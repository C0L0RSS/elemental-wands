package com.anton.elementalwands.arena;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianArenaEntity;
import com.anton.elementalwands.entity.GuardianLiftEntity;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModEntities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.anton.elementalwands.arena.GuardianArenaRules.*;

/** One prototype encounter per server. Only initially empty sky is ever edited. */
public final class GuardianArenaManager {
    private static final Logger LOG=LoggerFactory.getLogger("elementalwands-arena");
    private static GuardianArenaJournal journal;
    private static GuardianArenaJournal.State saved;
    private static Session active;
    private static boolean internalMutation, internalTeleport;
    private static String storageError;
    private static final Set<UUID> respawnReturns=new java.util.HashSet<>();

    private static final class Session {
        final GuardianArenaJournal.Arena receipt;
        final ServerWorld world;
        final GuardianArenaRoster roster;
        final Map<UUID,Vec3d> seats=new HashMap<>();
        final Map<UUID,Vec3d> safe=new HashMap<>();
        final Map<UUID,GuardianLiftEntity> lifts=new HashMap<>();
        long phaseStarted;
        FracturedGuardianEntity guardian;
        GuardianArenaEntity visual;
        Phase phase=Phase.WALLS;
        int tick, buildCursor, cleanupColumn, cleanupY=Integer.MIN_VALUE;
        boolean cleaned;
        double feet;
        String outcome="Encounter underway";
        Session(GuardianArenaJournal.Arena receipt, ServerWorld world, GuardianArenaRoster roster) {
            this.receipt=receipt; this.world=world; this.roster=roster; feet=receipt.base()+1; phaseStarted=world.getTime();
        }
        Box volume() { var a=receipt; return new Box(a.x()-HALF-1,a.floor(),a.z()-HALF-1,a.x()+HALF+1,a.top()+1,a.z()+HALF+1); }
    }

    private GuardianArenaManager() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(GuardianArenaManager::load);
        ServerTickEvents.END_SERVER_TICK.register(GuardianArenaManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active!=null) {
                releaseLifts(active);
                recoverGuardian(active);
                returnOnline(server);
                // Retain the receipt until a subsequent world save has persisted all cleanup.
                // Startup always repeats this idempotent sweep; no partial fight resumes.
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { active=null; saved=null; journal=null; storageError=null; respawnReturns.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> eliminate(handler.getPlayer(),"Disconnected"));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> server.execute(() -> {
            ServerPlayerEntity player=handler.getPlayer();
            if (active!=null && active.roster.enrolled(player.getUuid()) && active.phase.ordinal()<Phase.WITHDRAW.ordinal()) {
                active.roster.eliminate(player.getUuid());
                releaseLift(active,player.getUuid());
                spectate(player);
            } else returnPending(player);
        }));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (entity instanceof ServerPlayerEntity player && active!=null && active.roster.enrolled(player.getUuid())) {
                eliminate(player,"You fell. You can now watch the fight.");
                respawnReturns.add(player.getUuid());
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive) -> {
            // PlayerManager fires this before ServerPlayNetworkHandler replaces its dead player.
            // Teleport only on a later tick, once the connection points at the replacement entity.
            if (!alive) respawnReturns.add(newPlayer.getUuid());
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GuardianArenaManager::allowDamage);
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> {
            if (active!=null && world==active.world && active.volume().contains(Vec3d.ofCenter(hit.getBlockPos()))
                    && player.getStackInHand(hand).getItem() instanceof BlockItem) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
    }

    private static void load(MinecraftServer server) {
        active=null; storageError=null;
        journal=new GuardianArenaJournal(server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/guardian-arena.json"));
        try {
            saved=journal.read();
            if (saved.arena!=null) {
                ServerWorld world=world(server,saved.arena.dimension());
                if (world==null) throw new IOException("Arena dimension is unavailable");
                active=new Session(saved.arena,world,new GuardianArenaRoster(Set.of()));
                active.phase=Phase.CLEANUP;
                forceChunks(active,true);
                LOG.info("Recovering interrupted Guardian arena at {}, {}",saved.arena.x(),saved.arena.z());
            }
        } catch (IOException e) { storageError=e.getMessage(); LOG.error("Arena recovery requires attention; new encounters disabled",e); }
    }

    /** Validates the complete sky footprint before sealing the party or changing any block. */
    public static String start(ServerPlayerEntity caller, FracturedGuardianEntity guardian) {
        return start(caller,guardian,() -> {});
    }
    public static String start(ServerPlayerEntity caller, FracturedGuardianEntity guardian,Runnable accepted) {
        if (storageError!=null || saved==null) return "Arena unavailable: "+(storageError==null?"world is not ready":storageError);
        if (active!=null) return "An arena is already active or recovering. Use /ew guardian arena status.";
        ServerWorld world=(ServerWorld)caller.getEntityWorld();
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return "The prototype needs the Overworld's open sky.";
        if (world.getDifficulty()==Difficulty.PEACEFUL) return "Switch out of Peaceful before starting a Guardian encounter.";
        if (caller.isCreative() || caller.isSpectator()) return "Use Survival or Adventure to join the prototype.";
        int cx=guardian.getBlockX(), cz=guardian.getBlockZ();
        List<ServerPlayerEntity> players=world.getPlayers(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                && !guardian.isTeammate(p) && p.squaredDistanceTo(guardian)<=GATHER_RADIUS*GATHER_RADIUS);
        if (!players.contains(caller)) return "Gather within "+GATHER_RADIUS+" blocks of the Guardian first.";
        List<Entity> carried=new ArrayList<>(players); carried.add(guardian);
        var plannedSeats=liftSeats(players.stream().map(Entity::getEntityPos).toList(),guardian.getEntityPos());
        int base=(int)Math.ceil(carried.stream().mapToDouble(Entity::getY).max().orElse(guardian.getY()));
        int highest=base;
        Box footprint=new Box(cx-HALF-2,base,cz-HALF-2,cx+HALF+2,world.getTopYInclusive(),cz+HALF+2);
        if (!world.getWorldBorder().contains(footprint)) return "The arena would cross the world border. Move farther inside.";
        List<Long> forced=new ArrayList<>();
        for (int x=(cx-HALF-1)>>4;x<=(cx+HALF)>>4;x++) for (int z=(cz-HALF-1)>>4;z<=(cz+HALF)>>4;z++) {
            if (world.getChunkManager().getWorldChunk(x,z)==null) return "Load the surrounding area first (about five chunks in every direction).";
            long key=ChunkPos.toLong(x,z);
            if (!world.getForcedChunks().contains(key)) forced.add(key);
        }
        for (int x=cx-HALF-1;x<=cx+HALF;x++) for (int z=cz-HALF-1;z<=cz+HALF;z++)
            highest=Math.max(highest,world.getTopY(Heightmap.Type.WORLD_SURFACE,x,z));
        int floor=Math.max(base+40,highest+8), top=world.getTopYInclusive();
        if (floor>top-64) return "This location is too high for the tower. Find lower ground with at least 64 blocks of open sky above the arena.";
        for (var p:players) if (com.anton.elementalwands.util.HollowPurpleChargeManager.isCharging(world,p))
            return "Finish charging Hollow Purple before starting the encounter.";
        for (Entity entity:carried) {
            if (entity.hasVehicle() || entity.hasPassengers()) return "Everyone, including the Guardian, must dismount first.";
            Box box=entity.getBoundingBox();
            if (entity instanceof ServerPlayerEntity player) {
                Vec3d seat=plannedSeats.get(players.indexOf(player));
                if(!world.isSpaceEmpty(player,new Box(seat.x-.3,base+1,seat.z-.3,seat.x+.3,floor+7,seat.z+.3)))
                    return "The lift needs a clear starting position away from the Guardian. Gather in the open courtyard.";
            }
            if (!(entity==guardian && guardian.getCommandTags().contains("ew_church_keeper"))
                    && !world.isSpaceEmpty(entity,new Box(box.minX,box.maxY,box.minZ,box.maxX,floor+7,box.maxZ)))
                return "The gathering spot needs open sky above each player and the Guardian. Move out from beneath roofs or trees.";
            if (entity instanceof ServerPlayerEntity p && (p.isGliding() || world.isSpaceEmpty(p,p.getBoundingBox().offset(0,-.08,0)))) return "Everyone must stand on the ground before starting.";
        }
        // Admission already verified the caller is standing on ground. This is the drop-return
        // point only; eliminated players spectate, so no unrelated exterior terrain is required.
        Vec3d waiting=caller.getEntityPos();
        var receipt=new GuardianArenaJournal.Arena(world.getRegistryKey().getValue().toString(),guardian.getUuidAsString(),
                cx,cz,base,floor,top,point(guardian),point(world,waiting,0,0),guardian.hasNoGravity(),guardian.isInvulnerable(),guardian.isAiDisabled(),guardian.getAttributeBaseValue(net.minecraft.entity.attribute.EntityAttributes.FOLLOW_RANGE),forced);
        saved.arena=receipt;
        for (var p:players) {
            saved.returns.put(p.getUuidAsString(),point(p));
            saved.gameModes.put(p.getUuidAsString(),p.interactionManager.getGameMode().getId());
        }
        if (!persist()) { saved.arena=null; for (var p:players) { saved.returns.remove(p.getUuidAsString());saved.gameModes.remove(p.getUuidAsString()); } return "Could not save the recovery receipt. Nothing was spawned."; }
        Session s=new Session(receipt,world,new GuardianArenaRoster(players.stream().map(Entity::getUuid).toList()));
        accepted.run(); // Consume the ritual item before temporary spell equipment moves inventory stacks.
        for (var p:players) cancelEncounterEffects(p);
        active=s; s.guardian=guardian;
        forceChunks(s,true);
        guardian.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);
        guardian.setHealth(200);
        guardian.setPosition(guardian.getX(),floor+49,guardian.getZ());
        guardian.setArenaHidden(true);
        guardian.stopReview(); guardian.setAiDisabled(true); guardian.setNoGravity(true); guardian.setInvulnerable(true);
        for (int i=0;i<players.size();i++) s.seats.put(players.get(i).getUuid(),plannedSeats.get(i));
        s.visual=new GuardianArenaEntity(ModEntities.GUARDIAN_ARENA,world);
        s.visual.setPosition(cx,base,cz); world.spawnEntity(s.visual);
        sound(s,SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,1.5f,.5f);
        announce(s,"The threshold is sealed.");
        carry(s);
        return "Arena sealed for "+players.size()+" player(s). Fallen players spectate until the encounter ends. Emergency return: /ew guardian arena stop";
    }

    public static boolean hasActiveArena() { return active!=null; }
    public static boolean isFighting(FracturedGuardianEntity guardian) { return owns(guardian) && active.phase==Phase.FIGHT; }

    public static String status() {
        if (storageError!=null) return "Arena storage error: "+storageError;
        if (active==null) return "No active arena.";
        var s=active; var a=s.receipt;
        return "Arena: "+s.phase+" — "+s.roster.survivors().size()+" surviving; 128 × 128; floor Y="+a.floor()+", walls to Y="+a.top()+". "+s.outcome;
    }
    public static void stop() { if (active!=null && active.phase.ordinal()<Phase.DESCENT.ordinal()) descend(active,"Encounter stopped"); }
    public static boolean owns(FracturedGuardianEntity guardian) { return active!=null && guardian.getUuidAsString().equals(active.receipt.guardian()) && guardian.getEntityWorld()==active.world; }
    public static boolean eligible(FracturedGuardianEntity guardian, ServerPlayerEntity player) {
        return !owns(guardian) || (active.phase==Phase.FIGHT && active.roster.alive(player.getUuid()));
    }
    public static double encounterRange(FracturedGuardianEntity guardian) { return owns(guardian)?192:48; }
    public static double movementRange(FracturedGuardianEntity guardian) { return owns(guardian)?HALF-6:14; }
    public static boolean validLanding(FracturedGuardianEntity guardian, Vec3d home, Vec3d target) {
        if (!owns(guardian)) return target.squaredDistanceTo(home)<=36*36;
        var a=active.receipt;
        return contains(a.x(),a.z(),a.floor()+.8,a.top()-guardian.getHeight(),target,4);
    }

    public static boolean canCast(PlayerEntity player) {
        if(player.isSpectator()) return false;
        return active==null || !active.roster.enrolled(player.getUuid())
                || (active.roster.alive(player.getUuid()) && active.phase==Phase.FIGHT);
    }

    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (internalTeleport || active==null || !player.isAlive()) return true;
        Session s=active; var a=s.receipt;
        if(isWatching(player)) return destination==s.world && contains(a.x(),a.z(),a.floor()+2,a.top()-2,target,2);
        if (s.roster.alive(player.getUuid())) {
            if (s.phase!=Phase.FIGHT) return false;
            return destination==s.world && contains(a.x(),a.z(),a.floor()+1,a.top()-2,target,.4);
        }
        // Elimination and late arrival never enroll a new participant, even by portal or pearl.
        return destination!=s.world || !s.volume().expand(0,1,0).contains(target);
    }

    private record SpellWrite(World world, BlockPos pos, BlockState state) {}
    private static SpellWrite spellWrite;

    private static boolean combatFloor(World world, BlockPos pos) {
        if (active == null || world != active.world) return false;
        var a = active.receipt;
        return pos.getY() == a.floor() && pos.getX() >= a.x()-HALF && pos.getX() < a.x()+HALF
                && pos.getZ() >= a.z()-HALF && pos.getZ() < a.z()+HALF;
    }

    /** Only the tracked spell manager may reskin an intact combat floor; never make a hole. */
    public static boolean setTemporarySpellBlock(ServerWorld world, BlockPos pos, BlockState state) {
        BlockState current = world.getBlockState(pos);
        boolean coals = state.isOf(com.anton.elementalwands.registry.ModSpellBlocks.PYRE_COALS);
        boolean restoring = current.isOf(com.anton.elementalwands.registry.ModSpellBlocks.PYRE_COALS)
                && (state.isOf(ModBlocks.ARENA_STONE) || state.isOf(ModBlocks.ARENA_DARK) || state.isOf(ModBlocks.ARENA_LIGHT));
        if (!combatFloor(world,pos) || active.phase != Phase.FIGHT || (!coals && !restoring))
            return world.setBlockState(pos,state,3);
        SpellWrite previous = spellWrite;
        spellWrite = new SpellWrite(world,pos.toImmutable(),state);
        try { return world.setBlockState(pos,state,3); }
        finally { spellWrite = previous; }
    }

    public static boolean authorizedSpellWrite(World world, BlockPos pos, BlockState state) {
        return spellWrite != null && spellWrite.world == world && spellWrite.pos.equals(pos) && spellWrite.state.equals(state);
    }

    public static boolean protectedBlock(World world,BlockPos pos) {
        return !internalMutation && (combatFloor(world,pos) || world.getBlockState(pos).isOf(ModBlocks.ARENA_STONE)
                || world.getBlockState(pos).isOf(ModBlocks.ARENA_DARK) || world.getBlockState(pos).isOf(ModBlocks.ARENA_LIGHT));
    }
    public static boolean rejectPlacement(World world,BlockPos pos,BlockState state) {
        return !internalMutation && active!=null && world==active.world && active.phase!=Phase.FIGHT
                && !state.isAir() && pos.getY()>=active.receipt.base() && pos.getY()<=active.receipt.top()
                && pos.getX()>=active.receipt.x()-65 && pos.getX()<=active.receipt.x()+64
                && pos.getZ()>=active.receipt.z()-65 && pos.getZ()<=active.receipt.z()+64;
    }

    private static boolean allowDamage(LivingEntity entity,DamageSource source,float amount) {
        if (active==null) return true;
        Session s=active;
        if (entity instanceof ServerPlayerEntity p && (isWatching(p) || (s.roster.alive(p.getUuid()) && s.phase!=Phase.FIGHT))) return false;
        if (entity instanceof FracturedGuardianEntity g && owns(g)) {
            if (s.phase!=Phase.FIGHT) return false;
            if (source.getAttacker() instanceof ServerPlayerEntity p) return s.roster.alive(p.getUuid());
        }
        return true;
    }

    private static void tick(MinecraftServer server) {
        for(UUID id:List.copyOf(respawnReturns)) {
            var player=server.getPlayerManager().getPlayer(id);
            if(player==null) {respawnReturns.remove(id);continue;}
            if(player.networkHandler.player!=player) continue;
            if(!player.isAlive()) {
                if(saved==null || !saved.returns.containsKey(id.toString())) {respawnReturns.remove(id);continue;}
                var handler=player.networkHandler;
                player=server.getPlayerManager().respawnPlayer(player,false,Entity.RemovalReason.KILLED);
                handler.player=player; // Same ordering as vanilla's respawn packet handler.
            }
            if(active!=null && active.roster.enrolled(id) && active.phase.ordinal()<Phase.WITHDRAW.ordinal()) spectate(player); else returnPending(player);
            respawnReturns.remove(id);
        }
        Session s=active;
        if (s==null) {
            if(saved!=null && server.getOverworld().getTime()%20==0) returnOnline(server);
            return;
        }
        maintainSpectators(s);
        if (s.phase==Phase.CLEANUP) {
            recoverGuardian(s); returnOnline(server); moveDrops(s); clean(s);
            if (s.cleaned) complete(s);
            return;
        }
        for (UUID id:s.roster.survivors()) {
            ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
            if (p==null || !p.isAlive() || p.isSpectator() || p.isCreative()) {
                s.roster.eliminate(id); releaseLift(s,id);
                if(p!=null)cancelEncounterEffects(p);
            }
        }
        if (s.phase.ordinal()<Phase.DESCENT.ordinal()) {
            if (s.roster.wiped()) descend(s,"The party fell");
            else if (s.guardian==null || s.guardian.isRemoved() || !s.guardian.isAlive()) descend(s,"The Guardian is defeated");
            else if (s.world.getDifficulty()==Difficulty.PEACEFUL) descend(s,"Encounter ended: Peaceful difficulty");
        }
        var a=s.receipt;
        s.tick++;
        switch (s.phase) {
            case WALLS -> {
                carry(s);
                if (s.tick==1) sound(s,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),2,.55f);
                if (s.tick>=WALL_TICKS) phase(s,Phase.FLOOR);
            }
            case FLOOR -> { carry(s); build(s); if (s.phase==Phase.FLOOR && s.tick>=FLOOR_TICKS) phase(s,Phase.ASCENT); }
            case ASCENT -> {
                s.feet=a.base()+1+(a.floor()-a.base())*ease(s.tick/(double)LIFT_TICKS); carry(s); build(s);
                if (s.phase==Phase.ASCENT && s.tick>=LIFT_TICKS) phase(s,Phase.SETTLE);
            }
            case SETTLE -> {
                carry(s); build(s);
                if (s.phase==Phase.SETTLE && s.tick>=SETTLE_TICKS && s.buildCursor>=buildCount(s)) {
                    phase(s,Phase.ARRIVAL);
                    var lift=new GuardianLiftEntity(ModEntities.GUARDIAN_LIFT,s.world);
                    lift.setPosition(a.guardianHome().x(),a.floor()+49,a.guardianHome().z());
                    lift.drop(a.floor()+49,a.floor()+1,s.phaseStarted,ARRIVAL_TICKS);
                    s.world.spawnEntity(lift);s.lifts.put(s.guardian.getUuid(),lift);
                    s.guardian.setPosition(lift.getEntityPos());
                    s.guardian.startRiding(lift,true,true);
                    s.guardian.setArenaHidden(false);
                    s.guardian.triggerAnim("guardian","arrival_fall");
                    announce(s,"Something stirs above the sanctuary.");
                    sound(s,SoundEvents.ENTITY_PHANTOM_FLAP,2,.5f);
                }
            }
            case ARRIVAL -> {
                if(s.tick>=ARRIVAL_TICKS) {
                    releaseLift(s,s.guardian.getUuid());
                    s.guardian.setPosition(a.guardianHome().x(),a.floor()+1,a.guardianHome().z());
                    s.guardian.setVelocity(Vec3d.ZERO);s.guardian.fallDistance=0;
                    s.guardian.triggerAnim("guardian","arrival_land");
                    sound(s,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),2,.65f);
                    s.world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,s.guardian.getX(),s.feet+.2,s.guardian.getZ(),90,4,.15,4,.1);
                    s.world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,s.guardian.getX(),s.feet+1,s.guardian.getZ(),50,2,1,2,.08);
                    phase(s,Phase.LANDING);
                }
            }
            case LANDING -> {
                if(s.tick>=LANDING_TICKS) {
                    releaseLifts(s);
                    phase(s,Phase.FIGHT);
                    s.guardian.setNoGravity(false); s.guardian.setInvulnerable(false); s.guardian.setAiDisabled(false);
                    s.guardian.startFight();
                    announce(s,"The keeper awakens.");
                }
            }
            case FIGHT -> contain(s);
            case DESCENT -> {
                // Death-animation XP and late drops can appear after descent has already started.
                moveDrops(s); clean(s);
                s.feet=a.floor()+1-(a.floor()-a.base())*ease(s.tick/(double)DESCEND_TICKS); carry(s);
                if (s.world.getTime()-s.phaseStarted>=DESCEND_TICKS && s.cleaned) {
                    releaseLifts(s); recoverGuardian(s); returnOnline(server); phase(s,Phase.WITHDRAW);
                    sound(s,SoundEvents.BLOCK_BELL_USE,2,.6f);
                }
            }
            case WITHDRAW -> { if (s.tick>=WITHDRAW_TICKS) complete(s); }
            default -> {}
        }
        if (active==s) {
            updateVisual(s);
            if ((s.phase==Phase.ASCENT || s.phase==Phase.DESCENT || s.phase==Phase.FLOOR) && s.tick%20==0)
                sound(s,SoundEvents.BLOCK_STONE_BREAK,1.5f,.5f);
        }
    }

    private static void phase(Session s,Phase phase) {
        s.phase=phase; s.tick=0; s.phaseStarted=s.world.getTime();
        if (phase==Phase.ASCENT || phase==Phase.DESCENT) {
            boolean descending=phase==Phase.DESCENT;
            double from=descending?s.receipt.floor()+1:s.receipt.base()+1;
            double to=descending?s.receipt.base()+1:s.receipt.floor()+1;
            int duration=descending?DESCEND_TICKS:LIFT_TICKS;
            if (s.visual!=null) s.visual.animateFloor((float)(from-s.receipt.base()),(float)(to-s.receipt.base()),s.phaseStarted,duration);
            for (var lift:s.lifts.values()) lift.animate(from,to,s.phaseStarted,duration);
        }
    }
    private static void cancelEncounterEffects(ServerPlayerEntity player) {
        com.anton.elementalwands.util.ZephyrStrikeManager.cancel(player);
        com.anton.elementalwands.util.TitanDomeManager.cancelForEncounter(player);
        com.anton.elementalwands.util.HollowPurpleChargeManager.cancel(player);
    }
    private static void descend(Session s,String outcome) {
        if (s.phase.ordinal()>=Phase.DESCENT.ordinal()) return;
        s.outcome=outcome;
        for (UUID id:s.roster.survivors()) {
            var player=s.world.getServer().getPlayerManager().getPlayer(id);
            if(player!=null) cancelEncounterEffects(player);
        }
        // An interrupted formation returns from its actual height, never jumps to the top first.
        if (s.feet<s.receipt.floor()+.9) { recoverGuardian(s); returnOnline(s.world.getServer()); phase(s,Phase.CLEANUP); return; }
        phase(s,Phase.DESCENT);
        if (s.guardian!=null && s.guardian.isAlive()) { s.guardian.stopReview(); s.guardian.setArenaHidden(true); s.guardian.setInvulnerable(true); s.guardian.setNoGravity(true); }
        for (UUID id:s.roster.survivors()) {
            ServerPlayerEntity p=s.world.getServer().getPlayerManager().getPlayer(id);
            if (p!=null) { com.anton.elementalwands.util.ZephyrStrikeManager.cancel(p); s.seats.put(id,new Vec3d(p.getX(),0,p.getZ())); }
        }
        announce(s,outcome+". Returning to the sanctuary.");
        moveDrops(s);
        for (Entity e:s.world.getOtherEntities(null,s.volume(),e -> e instanceof net.minecraft.entity.projectile.ProjectileEntity)) e.discard();
    }

    private static void carry(Session s) {
        for (UUID id:s.roster.survivors()) {
            ServerPlayerEntity player=s.world.getServer().getPlayerManager().getPlayer(id);
            if (player!=null && player.isAlive()) carryMember(s,player);
        }
        if (s.phase==Phase.DESCENT && s.guardian!=null && s.guardian.isAlive()) carryMember(s,s.guardian);
    }

    private static void carryMember(Session s,Entity member) {
        GuardianLiftEntity lift=s.lifts.get(member.getUuid());
        if (lift==null || lift.isRemoved()) {
            lift=new GuardianLiftEntity(ModEntities.GUARDIAN_LIFT,s.world);
            Vec3d seat=s.seats.getOrDefault(member.getUuid(),new Vec3d(s.receipt.guardianHome().x(),0,s.receipt.guardianHome().z()));
            lift.setPosition(seat.x,s.feet,seat.z);
            if (s.phase==Phase.ASCENT) lift.animate(s.receipt.base()+1,s.receipt.floor()+1,s.phaseStarted,LIFT_TICKS);
            else if (s.phase==Phase.DESCENT) lift.animate(s.receipt.floor()+1,s.receipt.base()+1,s.phaseStarted,DESCEND_TICKS);
            else lift.animate(s.feet,s.feet,s.world.getTime(),0);
            s.world.spawnEntity(lift); s.lifts.put(member.getUuid(),lift);
        }
        if (member.getVehicle()!=lift) {
            if (member instanceof ServerPlayerEntity player) player.stopGliding();
            internalTeleport=true;
            try { member.startRiding(lift,true,true); }
            finally { internalTeleport=false; }
        }
        lift.advance();
        member.setVelocity(Vec3d.ZERO); member.fallDistance=0;
    }

    private static void releaseLift(Session s,UUID id) {
        GuardianLiftEntity lift=s.lifts.remove(id);
        if (lift==null) return;
        var riders=List.copyOf(lift.getPassengerList());
        Vec3d position=lift.getEntityPos();
        lift.release();
        for (Entity rider:riders) if (rider.isAlive()) {
            if (rider instanceof ServerPlayerEntity player) transfer(player,s.world,position,player.getYaw(),player.getPitch());
            else { rider.setPosition(position); rider.setVelocity(Vec3d.ZERO); rider.fallDistance=0; }
        }
    }
    private static void releaseLifts(Session s) {
        for (UUID id:List.copyOf(s.lifts.keySet())) releaseLift(s,id);
    }

    private static void contain(Session s) {
        var a=s.receipt;
        for (UUID id:s.roster.survivors()) {
            ServerPlayerEntity p=s.world.getServer().getPlayerManager().getPlayer(id);
            if (p==null || !p.isAlive()) continue;
            Vec3d position=p.getEntityPos();
            if (p.getEntityWorld()!=s.world || !contains(a.x(),a.z(),a.floor()+.9,a.top()-2,position,.4)) {
                Vec3d fallback=s.safe.getOrDefault(id,new Vec3d(a.x()+8.5,a.floor()+1,a.z()+.5));
                Vec3d proposed=clamp(a.x(),a.z(),a.floor()+1,a.top()-2,position,1);
                if (p.getEntityWorld()!=s.world || !s.world.isSpaceEmpty(p,p.getBoundingBox().offset(proposed.subtract(position)))) proposed=fallback;
                transfer(p,s.world,proposed,p.getYaw(),p.getPitch());
                p.sendMessage(Text.literal("The sanctuary's binding holds you inside."),true);
            } else if (p.isOnGround()) s.safe.put(id,position);
        }
        for (var p:s.world.getPlayers()) if (!s.roster.alive(p.getUuid()) && !p.isSpectator()
                && s.volume().expand(0,1,0).contains(p.getEntityPos())) sendOutside(p);
        if (s.guardian!=null && !validLanding(s.guardian,new Vec3d(a.x(),a.floor()+1,a.z()),s.guardian.getEntityPos())) {
            // Preserve the high leap; only horizontal escape or falling beneath the floor is corrected.
            if (Math.abs(s.guardian.getX()-a.x())>HALF-3 || Math.abs(s.guardian.getZ()-a.z())>HALF-3 || s.guardian.getY()<a.floor()) {
                s.guardian.stopReview(); s.guardian.setPosition(a.x(),a.floor()+1,a.z()); s.guardian.startFight();
            }
        }
    }

    private static void eliminate(ServerPlayerEntity player,String reason) {
        if (active!=null && active.roster.eliminate(player.getUuid())) { releaseLift(active,player.getUuid()); cancelEncounterEffects(player); player.sendMessage(Text.literal(reason),false); }
    }
    public static boolean spectatorViewer(FracturedGuardianEntity guardian,ServerPlayerEntity player) {
        return owns(guardian) && isWatching(player) && player.isAlive() && player.isSpectator() && player.getEntityWorld()==active.world;
    }
    private static boolean isWatching(PlayerEntity player) {
        return active!=null && active.roster.enrolled(player.getUuid()) && !active.roster.alive(player.getUuid())
                && saved!=null && saved.returns.containsKey(player.getUuidAsString());
    }
    private static void spectate(ServerPlayerEntity player) {
        if(active==null || !player.isAlive()) return;
        releaseLift(active,player.getUuid());
        cancelEncounterEffects(player);
        player.changeGameMode(GameMode.SPECTATOR);
        player.setCameraEntity(player);
        var a=active.receipt;
        transfer(player,active.world,new Vec3d(a.x()+18.5,Math.min(a.top()-3,a.floor()+10),a.z()+18.5),player.getYaw(),player.getPitch());
        player.sendMessage(Text.literal("You are watching the fight. Fly around to spectate; you will return when the encounter ends."),false);
    }
    private static void maintainSpectators(Session s) {
        if(s.phase==Phase.WITHDRAW || s.phase==Phase.CLEANUP) return;
        var a=s.receipt;
        for(var player:s.world.getServer().getPlayerManager().getPlayerList()) {
            if(!player.isAlive() || !isWatching(player) || player.networkHandler.player!=player) continue;
            if(!player.isSpectator()) {spectate(player);continue;}
            var pos=player.getEntityPos();
            if(player.getEntityWorld()!=s.world || !contains(a.x(),a.z(),a.floor()+2,a.top()-2,pos,2)) {
                player.setCameraEntity(player);
                transfer(player,s.world,clamp(a.x(),a.z(),a.floor()+2,a.top()-2,pos,3),player.getYaw(),player.getPitch());
            }
        }
    }
    private static void sendOutside(ServerPlayerEntity player) {
        if (active==null || !player.isAlive()) return;
        if(isWatching(player)) {spectate(player);return;}
        var p=active.receipt.waiting();
        Vec3d destination=findSafe(active.world,new Vec3d(p.x(),p.y(),p.z()),player,pos -> !active.volume().expand(2,2,2).contains(pos));
        if(destination==null) return;
        transfer(player,active.world,destination,player.getYaw(),player.getPitch());
        player.sendMessage(Text.literal(active.roster.wiped()?"The encounter has ended. The tower is withdrawing.":"You are outside the encounter. Surviving players must finish the fight."),false);
    }

    private static void updateVisual(Session s) {
        if (s.visual==null || s.visual.isRemoved()) return;
        var a=s.receipt;
        float wall=a.top()+1-a.base(), radius=HALF;
        if (s.phase==Phase.WALLS) { wall*=Math.min(1,s.tick/(float)WALL_TICKS); radius=0; }
        if (s.phase==Phase.FLOOR) radius=(float)(HALF*ease(s.tick/(double)FLOOR_TICKS));
        if (s.phase==Phase.WITHDRAW) { wall*=1-ease(s.tick/(double)WITHDRAW_TICKS); radius=0; }
        s.visual.update((float)(s.feet-a.base()),radius,wall,true);
    }

    private static int buildCount(Session s) { return constructionCount(s.receipt.floor(),s.receipt.top()); }
    private static BlockPos buildPosition(Session s,int index) {
        var a=s.receipt; return constructionPosition(a.x(),a.z(),a.floor(),a.top(),index);
    }
    private static void build(Session s) {
        internalMutation=true;
        try {
            for (int i=0;i<BUILD_BUDGET && s.buildCursor<buildCount(s);i++,s.buildCursor++) {
                BlockPos p=buildPosition(s,s.buildCursor);
                if (!s.world.getBlockState(p).isAir()) { descend(s,"Arena formation obstructed; returning safely"); return; }
                boolean floor=p.getY()==s.receipt.floor();
                Block material=(Math.floorMod(p.getX()-s.receipt.x(),16)==0 || Math.floorMod(p.getZ()-s.receipt.z(),16)==0)
                        ? ModBlocks.ARENA_DARK : ModBlocks.ARENA_STONE;
                if (floor && Math.floorMod(p.getX()-s.receipt.x(),16)==0 && Math.floorMod(p.getZ()-s.receipt.z(),16)==0) material=ModBlocks.ARENA_LIGHT;
                s.world.setBlockState(p,material.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS);
            }
        } finally { internalMutation=false; }
    }

    /** The entire final prism was air at admission; sweep it to remove interrupted temporary spells too. */
    private static void clean(Session s) {
        if (s.cleaned) return;
        internalMutation=true;
        try {
            int budget=CLEAN_BUDGET;
            while (budget-->0 && s.cleanupColumn<130*130) {
                int x=s.receipt.x()-65+s.cleanupColumn%130,z=s.receipt.z()-65+s.cleanupColumn/130;
                if (s.cleanupY==Integer.MIN_VALUE) s.cleanupY=Math.min(s.receipt.top(),s.world.getTopY(Heightmap.Type.WORLD_SURFACE,x,z));
                if (s.cleanupY<s.receipt.floor()) { s.cleanupColumn++; s.cleanupY=Integer.MIN_VALUE; continue; }
                BlockPos pos=new BlockPos(x,s.cleanupY--,z);
                if (!s.world.getBlockState(pos).isAir()) s.world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS);
            }
            s.cleaned=s.cleanupColumn>=130*130;
        } finally { internalMutation=false; }
    }

    private static void moveDrops(Session s) {
        for (Entity e:s.world.getOtherEntities(null,s.volume(),e -> e instanceof ItemEntity || e instanceof ExperienceOrbEntity)) {
            var p=s.receipt.waiting(); e.setPosition(p.x(),p.y()+.3,p.z()); e.setVelocity(Vec3d.ZERO);
        }
    }
    private static void recoverGuardian(Session s) {
        if (s.guardian==null) {
            Entity e=s.world.getEntity(UUID.fromString(s.receipt.guardian()));
            if (e instanceof FracturedGuardianEntity g) s.guardian=g;
        }
        if (s.guardian!=null && s.guardian.isAlive()) {
            releaseLift(s,s.guardian.getUuid());
            if (s.guardian.getVehicle() instanceof GuardianLiftEntity lift) lift.release();
            var a=s.receipt; var p=a.guardianHome();
            s.guardian.setArenaHidden(s.guardian.getCommandTags().contains("ew_church_keeper"));
            s.guardian.stopReview(); s.guardian.setPosition(p.x(),p.y(),p.z());
            s.guardian.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.FOLLOW_RANGE).setBaseValue(a.followRange()>0?a.followRange():16);
            s.guardian.setNoGravity(a.gravity()); s.guardian.setInvulnerable(a.invulnerable()); s.guardian.setAiDisabled(a.noAi());
            s.guardian.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);
            s.guardian.setHealth(200);
            s.guardian.setVelocity(Vec3d.ZERO); s.guardian.fallDistance=0;
        }
    }
    private static void complete(Session s) {
        releaseLifts(s); moveDrops(s);
        if (s.visual!=null) s.visual.discard();
        // Flush world changes before retiring the write-ahead receipt. A crash during this flush replays cleanup.
        forceChunks(s,false);
        s.world.save(null,true,false);
        saved.arena=null;
        persist(); active=null;
        com.anton.elementalwands.church.GuardianChurchManager.arenaFinished(s.receipt.guardian());
    }
    private static void returnOnline(MinecraftServer server) {
        if (saved==null) return;
        for (var p:server.getPlayerManager().getPlayerList()) returnPending(p);
    }
    private static void returnPending(ServerPlayerEntity player) {
        if (saved==null || !player.isAlive() || player.networkHandler.player!=player) return;
        var point=saved.returns.get(player.getUuidAsString());
        if (point==null) return;
        ServerWorld destination=world(player.getEntityWorld().getServer(),point.dimension());
        if (destination==null) return;
        Vec3d safe=findSafe(destination,new Vec3d(point.x(),point.y(),point.z()),player);
        if(safe==null) return; // Keep the recovery receipt until supported ground becomes available.
        transfer(player,destination,safe,point.yaw(),point.pitch());
        String originalMode=saved.gameModes.get(player.getUuidAsString());
        if(originalMode!=null) {
            player.setCameraEntity(player);
            player.changeGameMode(GameMode.byId(originalMode,GameMode.SURVIVAL));
        }
        // Player data must reach disk before removing its recovery point from the receipt.
        destination.getServer().getPlayerManager().saveAllPlayerData();
        saved.returns.remove(player.getUuidAsString());saved.gameModes.remove(player.getUuidAsString()); persist();
    }
    private static Vec3d findSafe(ServerWorld world,Vec3d preferred,Entity entity) {
        return findSafe(world,preferred,entity,pos -> true);
    }
    private static Vec3d findSafe(ServerWorld world,Vec3d preferred,Entity entity,java.util.function.Predicate<Vec3d> allowed) {
        BlockPos feet=BlockPos.ofFloored(preferred);
        world.getChunk(feet.getX()>>4,feet.getZ()>>4);
        if (allowed.test(preferred) && world.getBlockState(feet.down()).isSolidBlock(world,feet.down())
                && world.isSpaceEmpty(entity,new Box(preferred.x-.4,preferred.y,preferred.z-.4,preferred.x+.4,preferred.y+2,preferred.z+.4))) return preferred;
        for (int radius=0;radius<=24;radius++) for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
            if (Math.abs(dx)!=radius && Math.abs(dz)!=radius) continue;
            int x=feet.getX()+dx,z=feet.getZ()+dz;
            // Unloaded heightmaps can report bottom Y. Load the terrain BEFORE querying it.
            world.getChunk(x>>4,z>>4);
            int y=world.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z);
            BlockPos support=new BlockPos(x,y-1,z);
            Vec3d candidate=new Vec3d(x+.5,y,z+.5);
            if (allowed.test(candidate) && y>world.getBottomY() && y<world.getTopYInclusive()-2 && world.getFluidState(support).isEmpty() && world.getBlockState(support).isSolidBlock(world,support)
                    && world.getWorldBorder().contains(support) && world.isSpaceEmpty(entity,new Box(x+.1,y,z+.1,x+.9,y+2,z+.9))) return candidate;
        }
        // Never send a player to an unvalidated heightmap fallback (including world spawn).
        return null;
    }
    private static void transfer(ServerPlayerEntity player,ServerWorld world,Vec3d p,float yaw,float pitch) {
        if (player.getVehicle() instanceof GuardianLiftEntity lift) lift.release();
        player.stopRiding();
        internalTeleport=true;
        try { player.teleport(world,p.x,p.y,p.z,Set.of(),yaw,pitch,true); player.setVelocity(Vec3d.ZERO); player.fallDistance=0; player.velocityModified=true; }
        finally { internalTeleport=false; }
    }
    private static GuardianArenaJournal.Point point(Entity entity) { return point((ServerWorld)entity.getEntityWorld(),entity.getEntityPos(),entity.getYaw(),entity.getPitch()); }
    private static GuardianArenaJournal.Point point(ServerWorld world,Vec3d pos,float yaw,float pitch) { return new GuardianArenaJournal.Point(world.getRegistryKey().getValue().toString(),pos.x,pos.y,pos.z,yaw,pitch); }
    private static ServerWorld world(MinecraftServer server,String id) { return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(id))); }
    private static void forceChunks(Session s,boolean forced) {
        for (long key:s.receipt.forcedChunks()) { ChunkPos p=new ChunkPos(key); s.world.setChunkForced(p.x,p.z,forced); }
    }
    private static boolean persist() {
        try { journal.write(saved); return true; }
        catch (IOException e) { storageError=e.getMessage(); LOG.error("Could not save Guardian arena recovery receipt",e); return false; }
    }
    private static void sound(Session s,net.minecraft.sound.SoundEvent event,float volume,float pitch) {
        for (UUID id:s.roster.survivors()) {
            var p=s.world.getServer().getPlayerManager().getPlayer(id);
            if (p!=null) p.playSoundToPlayer(event,SoundCategory.BLOCKS,volume,pitch);
        }
    }
    private static void announce(Session s,String message) {
        for (UUID id:s.roster.survivors()) {
            var p=s.world.getServer().getPlayerManager().getPlayer(id);
            if (p!=null) p.sendMessage(Text.literal(message),false);
        }
    }
}
