package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.block.SpringbloomBlock;
import com.anton.elementalwands.entity.SpringbloomEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Server owns casts, pad lifetime, landing qualification and damage protection. */
public final class SpringbloomManager {
    private record Pad(ServerWorld world, BlockPos pos, UUID owner, long expires,
                       TemporaryBlockManager.TemporaryPlacement placement, SpringbloomEntity visual, BlockState covered, BlockState upper) {}
    private static final class Flight {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final long launched;
        long landed=-1;
        Flight(ServerPlayerEntity player,ServerWorld world,long launched){this.player=player;this.world=world;this.launched=launched;}
    }
    private static final Map<ServerPlayerEntity,Vec3d> MOTION = new WeakHashMap<>();
    public static Vec3d motion(ServerPlayerEntity p) { return MOTION.getOrDefault(p,p.getVelocity()); }
    private static final List<Pad> PADS = new ArrayList<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var iterator=PADS.iterator();iterator.hasNext();) {
                var pad=iterator.next(); var owner=server.getPlayerManager().getPlayer(pad.owner);
                int cells=openCells(pad.world,pad.pos);
                if (pad.world.getTime()>=pad.expires || owner==null || !owner.isAlive() || owner.getEntityWorld()!=pad.world
                        || EWAttachments.getAffinity(owner)!=WizardAffinity.NATURE || !GuardianArenaManager.canCast(owner)
                        || !pad.world.getBlockState(pad.pos).isOf(ModSpellBlocks.SPRINGBLOOM)
                        || !supported(pad.world,pad.pos) || (cells&SpringbloomFootprint.CENTER)==0) {
                    restore(pad);
                    pollen(pad.world,Vec3d.ofBottomCenter(pad.pos),true); iterator.remove();
                } else {
                    var state=pad.world.getBlockState(pad.pos);
                    if(state.get(SpringbloomBlock.OPEN_CELLS)!=cells)
                        pad.world.setBlockState(pad.pos,state.with(SpringbloomBlock.OPEN_CELLS,cells),Block.NOTIFY_LISTENERS);
                }
            }
            FLIGHTS.entrySet().removeIf(entry -> {
                var f=entry.getValue(); var p=f.player;
                boolean ended=p.isRemoved() || !p.isAlive() || server.getPlayerManager().getPlayer(p.getUuid())!=p
                        || p.getEntityWorld()!=f.world || p.isSpectator() || p.hasVehicle() || p.isGliding()
                        || p.getAbilities().flying || p.isTouchingWater() || p.isInLava() || p.isClimbing()
                        || (f.landed>=0 && f.world.getTime()>f.landed+2);
                // Client onGround can arrive before its landing position/fall-damage packet.
                // Consume real fall damage in the mixin; clear zero-distance landings only
                // after physical contact and a short packet-order grace period.
                if(!ended && f.landed<0 && f.world.getTime()>f.launched+2 && p.isOnGround()
                        && f.world.raycast(new RaycastContext(p.getEntityPos(),p.getEntityPos().add(0,-.06,0),
                        RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,p)).getType()!=HitResult.Type.MISS)
                    f.landed=f.world.getTime();
                if (!ended && f.world.getTime()%4==0) f.world.spawnParticles(ModParticles.NATURE_LEAF,
                        p.getX(),p.getY()+.15,p.getZ(),2,.18,.08,.18,.015);
                if(ended)p.setAttached(EWAttachments.SPRINGBLOOM_FLIGHT,false);
                return ended;
            });
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for(var pad:PADS)restore(pad);
            PADS.clear(); FLIGHTS.clear(); MOTION.clear();
        });
    }
    public static boolean cast(ServerPlayerEntity p) {
        var world=p.getEntityWorld(); var stack=p.getMainHandStack();
        if (!p.isAlive() || p.isSpectator() || EWAttachments.getAffinity(p)!=WizardAffinity.NATURE
                || !(stack.getItem() instanceof AbstractWandItem) || !GuardianArenaManager.canCast(p)
                || !WandProgression.owns(p,WandSpells.find("springbloom")) || !WandLoadouts.get(p).contains("springbloom")) return false;
        // Player-owned recovery survives switching/copying wands, death and reconnects.
        long now=world.getServer().getOverworld().getTime();
        long remaining=p.getAttachedOrElse(EWAttachments.SPRINGBLOOM_READY,0L)-now;
        if(remaining>0){p.sendMessage(Text.literal("Springbloom: "+((remaining+19)/20)+"s"),true);return false;}
        if(!AbstractWandItem.tryStartCooldown(world,p,stack,"springbloom",SpringbloomRules.COOLDOWN))return false;
        p.setAttached(EWAttachments.SPRINGBLOOM_READY,now+SpringbloomRules.COOLDOWN);
        var pod=new SpringbloomEntity(ModEntities.SPRINGBLOOM,world);pod.launch(p);world.spawnEntity(pod);
        world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_EGG_THROW,SoundCategory.PLAYERS,.65f,1.15f);
        return true;
    }
    private static boolean supported(ServerWorld world,BlockPos pos) {
        return world.getBlockState(pos.down()).isSideSolidFullSquare(world,pos.down(),Direction.UP)
                && world.getFluidState(pos.down()).isEmpty();
    }
    private static boolean softCover(ServerWorld world,BlockPos pos) {
        var state=world.getBlockState(pos);
        return state.isAir() || (state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !ModSpellBlocks.isNatureGrowth(state)
                && (state.isReplaceable() || state.getBlock() instanceof PlantBlock || state.isOf(Blocks.LEAF_LITTER))
                && state.getCollisionShape(world,pos).isEmpty());
    }
    private static int openCells(ServerWorld world,BlockPos center) {
        int cells=0;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            boolean clear=true;
            for(int y=0;y<3;y++) {
                var at=center.add(x,y,z);
                if(!world.isChunkLoaded(at) || !world.getWorldBorder().contains(at)
                        || at.getY()>world.getTopYInclusive()){clear=false;break;}
                // The existing center belongs to this pad; other tracked growth stays excluded.
                if(x==0&&z==0&&y==0&&world.getBlockState(at).isOf(ModSpellBlocks.SPRINGBLOOM))continue;
                if(!softCover(world,at) || TemporaryBlockManager.isTracked(world,at)){clear=false;break;}
            }
            if(clear)cells|=SpringbloomFootprint.bit(x,z);
        }
        return cells;
    }
    private static boolean overlaps(Pad pad,ServerPlayerEntity player) {
        var state=pad.world.getBlockState(pad.pos);
        return state.isOf(ModSpellBlocks.SPRINGBLOOM)
                && SpringbloomFootprint.overlaps(state.get(SpringbloomBlock.OPEN_CELLS),pad.pos,player.getBoundingBox());
    }
    private static void restore(Pad pad) {
        pad.visual.discard();
        TemporaryBlockManager.restoreTemporaryBlocks(pad.world,pad.placement);
        // The lower half is restored by the shared tracker first. Never overwrite later edits.
        if(pad.upper!=null && pad.world.getBlockState(pad.pos).equals(pad.covered)
                && pad.world.getBlockState(pad.pos.up()).isAir() && !TemporaryBlockManager.isTracked(pad.world,pad.pos.up())
                && pad.covered.canPlaceAt(pad.world,pad.pos))
            GuardianArenaManager.setTemporarySpellBlock(pad.world,pad.pos.up(),pad.upper);
    }
    public static boolean plant(ServerPlayerEntity owner,BlockPos pos) {
        var world=owner.getEntityWorld();
        if(!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos) || !supported(world,pos)
                || TemporaryBlockManager.isTracked(world,pos) || world.getBlockState(pos).isOf(ModSpellBlocks.SPRINGBLOOM))return false;
        int cells=openCells(world,pos);
        if((cells&SpringbloomFootprint.CENTER)==0)return false;
        BlockState covered=world.getBlockState(pos), upper=null;
        if(covered.getBlock() instanceof TallPlantBlock && covered.get(TallPlantBlock.HALF)==DoubleBlockHalf.LOWER) {
            var above=world.getBlockState(pos.up());
            if(!above.isOf(covered.getBlock()) || above.get(TallPlantBlock.HALF)!=DoubleBlockHalf.UPPER)return false;
            upper=above;
            // Remove both halves without drops; setting the lower half first would break the upper.
            world.setBlockState(pos.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
        }
        var placement=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(pos),ModSpellBlocks.SPRINGBLOOM.getDefaultState().with(SpringbloomBlock.OPEN_CELLS,cells),
                // Manager restores multi-part plants together at tick 80; tracker is a fallback.
                SpringbloomRules.LIFETIME+2,s->s.equals(covered),owner.getUuid());
        if(placement.isEmpty()) {
            if(upper!=null && world.getBlockState(pos).equals(covered) && world.getBlockState(pos.up()).isAir())
                GuardianArenaManager.setTemporarySpellBlock(world,pos.up(),upper);
            return false;
        }
        for(var it=PADS.iterator();it.hasNext();) {var old=it.next();if(old.owner.equals(owner.getUuid())) {
            restore(old);it.remove();
        }}
        var visual=new SpringbloomEntity(ModEntities.SPRINGBLOOM,world);visual.open(pos);world.spawnEntity(visual);
        PADS.add(new Pad(world,pos.toImmutable(),owner.getUuid(),world.getTime()+SpringbloomRules.LIFETIME,placement,visual,covered,upper));
        world.playSound(null,pos,SoundEvents.BLOCK_AZALEA_PLACE,SoundCategory.PLAYERS,.8f,.85f);
        return true;
    }
    public static boolean hasPad(ServerWorld world,BlockPos pos) {
        return PADS.stream().anyMatch(p->p.world==world && p.pos.equals(pos));
    }
    public static void afterMove(ServerPlayerEntity p,Vec3d before,Vec3d requested,boolean grounded) {
        if(requested.lengthSquared()>1E-8)MOTION.put(p,requested);
        if(grounded || requested.y>=0 || !p.isAlive() || p.isSpectator() || p.hasVehicle() || p.isGliding()
                || p.getAbilities().flying || HollowPurpleChargeManager.isCharging(p.getEntityWorld(),p)
                || !GuardianArenaManager.canCast(p))return;
        var world=p.getEntityWorld();var previous=FLIGHTS.get(p.getUuid());
        if(previous!=null && previous.launched==world.getTime())return;
        for(var pad:PADS) {
            double top=pad.pos.getY()+SpringbloomRules.HEIGHT;
            if(pad.world!=world || pad.expires<=world.getTime() || !world.getBlockState(pad.pos).isOf(ModSpellBlocks.SPRINGBLOOM)
                    || !supported(world,pad.pos) || before.y<top+.001 || Math.abs(p.getY()-top)>.035
                    || !overlaps(pad,p))continue;
            p.fallDistance=0; p.setOnGround(false);
            p.setAttached(EWAttachments.SPRINGBLOOM_FLIGHT,true);
            p.setVelocity(SpringbloomRules.launch(new Vec3d(p.getX()-before.x,0,p.getZ()-before.z)));
            p.velocityModified=true;
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            FLIGHTS.put(p.getUuid(),new Flight(p,world,world.getTime()));
            pollen(world,p.getEntityPos(),false);
            world.playSound(null,pad.pos,SoundEvents.BLOCK_SLIME_BLOCK_FALL,SoundCategory.PLAYERS,.85f,1.25f);
            return;
        }
    }
    public static boolean catching(ServerPlayerEntity p) {
        return PADS.stream().anyMatch(pad -> pad.world==p.getEntityWorld() && pad.expires>pad.world.getTime()
                && pad.world.getBlockState(pad.pos).isOf(ModSpellBlocks.SPRINGBLOOM) && supported(pad.world,pad.pos)
                && Math.abs(p.getY()-pad.pos.getY()-SpringbloomRules.HEIGHT)<.035
                && overlaps(pad,p));
    }
    public static boolean protectedFall(ServerPlayerEntity p) {
        var flight=FLIGHTS.get(p.getUuid());
        return flight!=null && flight.player==p && flight.world==p.getEntityWorld();
    }
    public static void finishLanding(ServerPlayerEntity p) { FLIGHTS.remove(p.getUuid()); p.setAttached(EWAttachments.SPRINGBLOOM_FLIGHT,false); p.fallDistance=0; }
    public static boolean tryBreakAimed(ServerPlayerEntity p) {
        var hit=p.getEntityWorld().raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(p.getRotationVec(1).multiply(4.5)),
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK && p.getEntityWorld().getBlockState(hit.getBlockPos()).isOf(ModSpellBlocks.SPRINGBLOOM)
                && p.getEntityWorld().breakBlock(hit.getBlockPos(),false,p);
    }
    private static void pollen(ServerWorld world,Vec3d pos,boolean broken) {
        world.spawnParticles(broken?ModParticles.NATURE_LEAF:ModParticles.NATURE_POLLEN,pos.x,pos.y+.15,pos.z,
                broken?14:22,.5,.14,.5,.055);
    }
    private SpringbloomManager() {}
}
