package com.anton.elementalwands.church;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.sound.*;
import net.minecraft.particle.ParticleTypes;
import org.slf4j.*;

/** Durable site state; all world mutations run in bounded batches on the server thread. */
public final class GuardianChurchManager {
    public static final BlockEntityType<GuardianSocketEntity> SOCKET_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of("elementalwands","guardian_socket"),FabricBlockEntityTypeBuilder.create(GuardianSocketEntity::new,ModBlocks.GUARDIAN_SOCKET).build());
    private static final Logger LOG=LoggerFactory.getLogger("elementalwands-church");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public enum Phase { BUILDING, RUINED, ACTIVE, RESTORING, RESTORED }
    public static final class Site {
        public int x,y,z;
        public BlockRotation rotation=BlockRotation.NONE;
        public Phase phase=Phase.BUILDING;
        public transient boolean statuePrepared;
        public String token=UUID.randomUUID().toString(),guardian;
        public boolean stocked;
        public int layoutVersion;
        public JsonArray statueMoveInventory; // Durable original inventory until the moved blocks are saved.
        public BlockPos socket() { return at(0,0,layoutVersion>=2?-6:0); }
        public BlockPos offering() { return at(-5,-1,layoutVersion>=2?-6:0); }
        public boolean terrainBlended;
        public List<GuardianChurchTerrain.Column> terrainPlan;
        transient int terrainCursor;
        public List<Long> forced=new ArrayList<>();
        transient int cursor;
        public BlockPos anchor() { return new BlockPos(x,y,z); }
        public BlockPos at(int x,int y,int z) { return ChurchLayout.at(anchor(),rotation,new BlockPos(x,y,z)); }
        boolean contains(BlockPos p) {
            var q=p.subtract(anchor()).rotate(inverse(rotation));
            return q.getX()>=ChurchLayout.MIN_X && q.getX()<=ChurchLayout.MAX_X && q.getY()>=ChurchLayout.MIN_Y && q.getY()<=ChurchLayout.MAX_Y && q.getZ()>=ChurchLayout.MIN_Z && q.getZ()<=ChurchLayout.MAX_Z;
        }
    }
    private static final class State { int version=1; List<Site> sites=new ArrayList<>(); }
    private static State state;
    private static Path journal;
    private static boolean mutation;
    private static String error;
    private GuardianChurchManager() {}
    public static boolean isMutating() { return mutation; }
    public static boolean busy() { return state!=null && state.sites.stream().anyMatch(s -> s.phase==Phase.ACTIVE || s.phase==Phase.BUILDING || s.phase==Phase.RESTORING); }
    public static List<Site> sites() { return state==null?List.of():List.copyOf(state.sites); }
    public static BlockRotation inverse(BlockRotation r) { return r==BlockRotation.CLOCKWISE_90?BlockRotation.COUNTERCLOCKWISE_90:r==BlockRotation.COUNTERCLOCKWISE_90?BlockRotation.CLOCKWISE_90:r; }
    public static void init() {
        GuardianChurchLocator.init();
        ServerLifecycleEvents.SERVER_STARTED.register(GuardianChurchManager::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { state=null; journal=null; error=null; mutation=false; });
        ServerTickEvents.END_SERVER_TICK.register(GuardianChurchManager::tick);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,block,be) -> !protectedBlock(world,pos));
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> {
            if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            if (world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.GUARDIAN_SOCKET)) {
                discover(sw,hit.getBlockPos(),world.getBlockState(hit.getBlockPos()));
                if (hand==Hand.MAIN_HAND) p.sendMessage(Text.literal(interact(p,hit.getBlockPos())),false);
                return ActionResult.SUCCESS;
            }
            Site s=siteAt(world,hit.getBlockPos());
            if (s!=null && s.phase!=Phase.RESTORED && (s.phase!=Phase.RUINED || !hit.getBlockPos().equals(s.offering()))) {
                p.sendMessage(Text.literal("The church is held in a broken moment. Return the heart to its keeper."),true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (entity instanceof FracturedGuardianEntity guardian && GuardianArenaManager.isFighting(guardian)) {
                for (Site s:sites()) if (guardian.getUuidAsString().equals(s.guardian) && s.phase==Phase.ACTIVE) {
                    s.phase=Phase.RESTORING; s.cursor=0;
                    // Commit victory before either restoration or reward inventories are changed.
                    if (!save()) { s.phase=Phase.ACTIVE; return; }
                    force((ServerWorld)guardian.getEntityWorld(),s);
                }
            }
        });
    }
    private static void load(MinecraftServer server) {
        state=new State();error=null;
        journal=server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/guardian-churches.json");
        try {
            State loaded=Files.exists(journal)?JSON.fromJson(Files.readString(journal),State.class):new State();
            if (loaded==null || loaded.version!=1 || loaded.sites==null) throw new IOException("Invalid church journal");
            state=loaded;
            for (Site s:state.sites) {
                if (s.phase==null || s.rotation==null || s.token==null || s.forced==null) throw new IOException("Invalid church site");
                // A restart aborts combat, while an already committed victory finishes restoration.
                if (s.phase==Phase.ACTIVE) { s.phase=Phase.RUINED; s.stocked=false; s.token=UUID.randomUUID().toString(); }
                if (s.phase==Phase.BUILDING || s.phase==Phase.RESTORING) force(server.getOverworld(),s);
            }
            save();
        } catch (Exception e) { error=e.getMessage();LOG.error("Church journal unavailable; interactions disabled",e); }
    }
    private static boolean save() {
        if (error!=null || state==null || journal==null) return false;
        try {
            Files.createDirectories(journal.getParent());Path temp=journal.resolveSibling(journal.getFileName()+".tmp");
            try (var channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                var bytes=ByteBuffer.wrap(JSON.toJson(state).getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining()) channel.write(bytes);channel.force(true);
            }
            Files.move(temp,journal,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        } catch (IOException e) { error=e.getMessage();LOG.error("Could not save church state",e);return false; }
    }
    public static void discover(ServerWorld world,BlockPos pos,BlockState block) {
        if (error!=null || state==null || world.getRegistryKey()!=World.OVERWORLD || state.sites.stream().anyMatch(s -> s.contains(pos))) return;
        Site s=new Site();s.x=pos.getX();s.y=pos.getY();s.z=pos.getZ();s.phase=Phase.RUINED;
        s.rotation=switch(block.get(Properties.HORIZONTAL_FACING)) { case EAST -> BlockRotation.CLOCKWISE_90;case SOUTH -> BlockRotation.CLOCKWISE_180;case WEST -> BlockRotation.COUNTERCLOCKWISE_90;default -> BlockRotation.NONE; };
        if(world.getBlockEntity(pos) instanceof GuardianSocketEntity socket && socket.layoutVersion>=2) {
            s.layoutVersion=2;
            var origin=pos.add(new BlockPos(0,0,6).rotate(s.rotation));
            s.x=origin.getX();s.z=origin.getZ();
        }
        state.sites.add(s);save();
    }
    private static Site siteAt(World world,BlockPos pos) {
        if (state==null || world.getRegistryKey()!=World.OVERWORLD) return null;
        for (Site s:state.sites) if (s.contains(pos)) return s;
        return null;
    }
    public static boolean protectedBlock(World world,BlockPos pos) {
        if (mutation) return false;
        if (world.getBlockState(pos).isOf(ModBlocks.GUARDIAN_SOCKET)) return true;
        Site s=siteAt(world,pos);
        return s!=null && s.phase!=Phase.RESTORED && !vegetation(world.getBlockState(pos));
    }
    private static boolean vegetation(BlockState block) {
        return block.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || block.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                || block.isOf(Blocks.VINE) || block.isOf(Blocks.BAMBOO) || block.isOf(Blocks.BAMBOO_SAPLING)
                || block.isOf(Blocks.MANGROVE_ROOTS) || block.isOf(Blocks.BEE_NEST);
    }
    private static void clearRitualVegetation(ServerWorld world,FracturedGuardianEntity guardian) {
        List<Box> shafts=new ArrayList<>();
        var body=guardian.getBoundingBox();
        shafts.add(new Box(body.minX,body.minY,body.minZ,body.maxX,world.getTopYInclusive()+1,body.maxZ));
        var players=world.getPlayers(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                && !guardian.isTeammate(p) && p.squaredDistanceTo(guardian)<=20*20);
        var seats=com.anton.elementalwands.arena.GuardianArenaRules.liftSeats(players.stream().map(p -> p.getEntityPos()).toList(),guardian.getEntityPos());
        for (int i=0;i<players.size();i++) {
            var b=players.get(i).getBoundingBox();var seat=seats.get(i);
            shafts.add(new Box(b.minX,b.minY,b.minZ,b.maxX,world.getTopYInclusive()+1,b.maxZ));
            shafts.add(new Box(seat.x-.3,b.minY,seat.z-.3,seat.x+.3,world.getTopYInclusive()+1,seat.z+.3));
        }
        // Only clear the actual lift shafts, preserving the overgrowth elsewhere in the ruins.
        mutation=true;
        try {
            for (Box shaft:shafts) for (int x=(int)Math.floor(shaft.minX);x<(int)Math.ceil(shaft.maxX);x++)
                for (int z=(int)Math.floor(shaft.minZ);z<(int)Math.ceil(shaft.maxZ);z++) {
                    if (world.getChunkManager().getWorldChunk(x>>4,z>>4)==null) continue;
                    int top=world.getTopY(Heightmap.Type.WORLD_SURFACE,x,z);
                    for (int y=(int)Math.floor(shaft.minY);y<top;y++) {
                        var pos=new BlockPos(x,y,z);
                        if (vegetation(world.getBlockState(pos)) && world.getBlockEntity(pos)==null)
                            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    }
                }
        } finally { mutation=false; }
    }
    private static boolean ritualShaftClear(ServerWorld world, net.minecraft.entity.Entity entity, Box box) {
        if (!world.getWorldBorder().contains(box) || !world.getEntityCollisions(entity, box).isEmpty()) return false;
        var shape = net.minecraft.util.shape.VoxelShapes.cuboid(box);
        var context = net.minecraft.block.ShapeContext.of(entity);
        // Include adjacent cells because fences and other collision shapes can extend outside their cell.
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX-1,box.minY-1,box.minZ-1),
                BlockPos.ofFloored(box.maxX+1,box.maxY+1,box.maxZ+1))) {
            var state = world.getBlockState(pos);
            boolean insideShaft = pos.getX()>=Math.floor(box.minX) && pos.getX()<Math.ceil(box.maxX)
                    && pos.getZ()>=Math.floor(box.minZ) && pos.getZ()<Math.ceil(box.maxZ)
                    && pos.getY()>=Math.floor(box.minY);
            if (insideShaft && vegetation(state) && world.getBlockEntity(pos)==null) continue;
            if (net.minecraft.util.shape.VoxelShapes.matchesAnywhere(shape,
                    state.getCollisionShape(world,pos,context).offset(pos.getX(),pos.getY(),pos.getZ()),
                    net.minecraft.util.function.BooleanBiFunction.AND)) return false;
        }
        return true;
    }
    public static String interact(ServerPlayerEntity player,BlockPos socket) {
        if (error!=null || state==null) return "The ritual is unavailable; check the server's church recovery log.";
        Site s=siteAt(player.getEntityWorld(),socket);
        if (s==null) return "The keeper has not settled into this place yet.";
        if (s.phase==Phase.RESTORED) return "The keeper rests. This church has already been restored.";
        if (s.phase!=Phase.RUINED) return "The ritual is already underway. Wait for the encounter to finish.";
        if (player.isCreative() || player.isSpectator()) return "Enter Survival or Adventure to awaken the keeper.";
        if(s.layoutVersion<2 && !moveStatue((ServerWorld)player.getEntityWorld(),s))
            return "The courtyard is being prepared. Clear any objects beside the new plinth and try again.";
        var held=player.getMainHandStack();
        if (held.isEmpty() && player.isSneaking()) {
            String old=s.token;s.token=UUID.randomUUID().toString();
            if (!save()) { s.token=old; return "The heart could not be recalled safely."; }
            player.getInventory().offerOrDrop(heart(s));
            return "The heart returns to you. Earlier copies have lost their power.";
        }
        if (!held.isOf(ModItems.GUARDIAN_HEART) || !held.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString("church_token","").equals(s.token))
            return "Place this church's Guardian Heart in the socket. Lost it? Sneak-use the socket with an empty hand to recall it.";
        if (busy() || GuardianArenaManager.hasActiveArena()) return "Another ritual is underway. Wait until it ends.";
        ServerWorld world=(ServerWorld)player.getEntityWorld();
        var guardian=keeper(world,s);
        if (guardian==null) return "The keeper is taking shape on its plinth. Try again in a moment.";
        s.phase=Phase.ACTIVE;
        if (!save()) { s.phase=Phase.RUINED; return "The ritual could not be saved. Your heart was not consumed."; }
        String result=GuardianArenaManager.start(player,guardian,() -> {
            preparePlinth(world,s);
            clearRitualVegetation(world,guardian);
            held.decrement(1);
        }, (entity, box) -> ritualShaftClear(world,entity,box));
        if (!GuardianArenaManager.owns(guardian)) { s.phase=Phase.RUINED;save();return result; }
        return "The heart answers. The keeper will test everyone gathered in the courtyard.";
    }
    public static void arenaFinished(String guardianId) {
        if (state==null || error!=null) return;
        for (Site s:state.sites) if (guardianId.equals(s.guardian) && s.phase==Phase.ACTIVE) {
            s.phase=Phase.RUINED;s.stocked=false;s.token=UUID.randomUUID().toString();save();
        }
    }
    private static ItemStack heart(Site s) {
        var stack=new ItemStack(ModItems.GUARDIAN_HEART);var nbt=new NbtCompound();nbt.putString("church_token",s.token);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(nbt));stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);return stack;
    }
    private static ItemStack book() {
        var book=new ItemStack(Items.WRITTEN_BOOK);
        List<RawFilteredPair<Text>> pages=List.of(
            RawFilteredPair.of(Text.literal("The Keeper's Promise\n\nWhen the bells fell silent, our keeper held the church within its final unbroken moment. Its heart still remembers the way home.")),
            RawFilteredPair.of(Text.literal("Gather your companions in the open courtyard. Place the Guardian Heart into the dark socket on the empty plinth. Look to the sky when the arena is ready.\n\nOvercome the keeper, and the church will remember what it was.")),
            RawFilteredPair.of(Text.literal("The walls seal everyone nearby into the trial. Those who fall become silent witnesses, free to watch but unable to fight. If all fall, the heart returns here.\n\nA lost heart may be recalled: sneak-use the socket with an empty hand.")));
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of("The Keeper's Promise"),"The Last Bellkeeper",0,pages,true));return book;
    }
    private static boolean loaded(ServerWorld world,Site s) {
        BlockPos a=s.at(-16,0,-10),b=s.at(16,0,42);
        for (int x=Math.min(a.getX(),b.getX())>>4;x<=Math.max(a.getX(),b.getX())>>4;x++)
            for (int z=Math.min(a.getZ(),b.getZ())>>4;z<=Math.max(a.getZ(),b.getZ())>>4;z++)
                if (world.getChunkManager().getWorldChunk(x,z)==null) return false;
        return true;
    }
    private static void force(ServerWorld world,Site s) {
        var a=s.at(-16,0,-10);var b=s.at(16,0,42);
        for (int x=Math.min(a.getX(),b.getX())>>4;x<=Math.max(a.getX(),b.getX())>>4;x++) for (int z=Math.min(a.getZ(),b.getZ())>>4;z<=Math.max(a.getZ(),b.getZ())>>4;z++) {
            long key=ChunkPos.toLong(x,z);if (!world.getForcedChunks().contains(key) && !s.forced.contains(key)) s.forced.add(key);
        }
        if (!save()) return;
        for (long key:s.forced) { ChunkPos p=new ChunkPos(key);world.setChunkForced(p.x,p.z,true);world.getChunk(p.x,p.z); }
    }
    private static void release(ServerWorld world,Site s) { for(long key:s.forced) {var p=new ChunkPos(key);world.setChunkForced(p.x,p.z,false);}s.forced.clear();save(); }
    private static void tick(MinecraftServer server) {
        if (state==null || error!=null) return;
        ServerWorld world=server.getOverworld();
        for (Site s:state.sites) {
            if (!s.forced.isEmpty() && s.phase!=Phase.BUILDING && s.phase!=Phase.RESTORING) release(world,s);
            if (!loaded(world,s)) continue;
            if(s.phase==Phase.RUINED && s.layoutVersion<2 && !GuardianArenaManager.hasActiveArena() && !moveStatue(world,s)) continue;
            if (s.phase==Phase.BUILDING || s.phase==Phase.RESTORING) {
                boolean whole=s.phase==Phase.RESTORING;
                mutation=true;
                try {
                    for (int budget=1024;budget>0 && s.cursor<ChurchLayout.COUNT;budget--) {
                        var local=ChurchLayout.local(s.cursor++);var pos=ChurchLayout.at(s.anchor(),s.rotation,local);
                        // The offering inventory and socket are never overwritten during restoration.
                        if (whole && (pos.equals(s.offering()) || pos.equals(s.socket()))) continue;
                        if(s.layoutVersion>=2 && ChurchLayout.naturalCourtyard(local.getX(),local.getZ())) continue;
                        var target=ChurchLayout.state(whole,local,s.rotation,s.layoutVersion);
                        if (world.getBlockState(pos).isOf(Blocks.BEDROCK)) continue;
                        if (world.getBlockState(pos)!=target) world.setBlockState(pos,target,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                    }
                    if (s.cursor>=ChurchLayout.COUNT) {
                        if(world.getBlockEntity(s.socket()) instanceof GuardianSocketEntity socket) { socket.layoutVersion=s.layoutVersion;socket.markDirty(); }
                        if (whole) rewards(world,s);
                        else stock(world,s,true);
                        world.save(null,true,false);
                        s.phase=whole?Phase.RESTORED:Phase.RUINED;
                        if (!save()) return;
                        release(world,s);
                        if (whole) {
                            world.playSound(null,s.anchor(),SoundEvents.BLOCK_BELL_USE,SoundCategory.BLOCKS,4,.8f);
                            world.spawnParticles(ParticleTypes.END_ROD,s.x,s.y+10,s.z+20,140,10,8,15,.03);
                        }
                    }
                } finally { mutation=false; }
            } else if (s.phase==Phase.RUINED && !s.stocked) stock(world,s,false);
            if (s.phase==Phase.RUINED && !s.terrainBlended && !GuardianArenaManager.hasActiveArena()) blendTerrain(world,s);
            if(s.phase==Phase.RUINED && !s.statuePrepared) { preparePlinth(world,s);s.statuePrepared=true; }
            UUID keeperId=uuid(s.guardian);
            if ((s.phase==Phase.RUINED || s.phase==Phase.RESTORED) && keeperId!=null) {
                var entity=world.getEntity(keeperId);
                if (entity!=null && (!(entity instanceof FracturedGuardianEntity g) || !GuardianArenaManager.owns(g))) entity.discard();
            }
        }
    }
    /** Journal strings are user-editable; a malformed id must not throw out of the tick loop. */
    private static UUID uuid(String text) {
        if (text==null) return null;
        try { return UUID.fromString(text); } catch (IllegalArgumentException invalid) { return null; }
    }
    /** Create the hidden ritual actor only after a valid heart is offered. */
    private static FracturedGuardianEntity keeper(ServerWorld world,Site s) {
        FracturedGuardianEntity guardian=null;
        UUID keeperId=uuid(s.guardian);
        if (keeperId!=null && world.getEntity(keeperId) instanceof FracturedGuardianEntity g && g.isAlive()) guardian=g;
        if (guardian!=null && GuardianArenaManager.owns(guardian)) return guardian;
        
        if(guardian==null) {
            guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            s.guardian=guardian.getUuidAsString();
            if(!save())return null;
            var pos=s.at(0,-1,s.layoutVersion>=2?-3:3);guardian.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            guardian.stopReview();guardian.setAiDisabled(true);guardian.setNoGravity(true);guardian.setInvulnerable(true);
            guardian.addCommandTag("ew_church_keeper");guardian.setArenaHidden(true);world.spawnEntity(guardian);
        }
        var pos=s.at(0,-1,s.layoutVersion>=2?-3:3);
        boolean displaced=guardian.squaredDistanceTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>.000001;
        if(displaced)guardian.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        guardian.setArenaHidden(true);
        guardian.setVelocity(Vec3d.ZERO);
        float yaw=switch(s.rotation) {case NONE -> 180;case CLOCKWISE_90 -> 270;case CLOCKWISE_180 -> 0;default -> 90;};
        guardian.setYaw(yaw);guardian.setBodyYaw(yaw);guardian.setHeadYaw(yaw);
        if(guardian.isBossAggressive() || !guardian.isAiDisabled() || !guardian.hasNoGravity() || !guardian.isInvulnerable()) {
            guardian.stopReview();guardian.setAiDisabled(true);guardian.setNoGravity(true);guardian.setInvulnerable(true);
        }
        return guardian;
    }
    private static void preparePlinth(ServerWorld world,Site s) {
        // Refresh only the authored effigy on older unfinished sites; leave the socket and chest intact.
        mutation=true;
        try {
            for(int x=-4;x<=4;x++)for(int y=-1;y<=7;y++)for(int z=s.layoutVersion>=2?-5:1;z<=(s.layoutVersion>=2?0:6);z++) {
                var p=s.at(x,y,z);
                if(world.getBlockEntity(p)!=null) continue;
                var desired=ChurchLayout.state(false,new BlockPos(x,y,z),s.rotation,s.layoutVersion);
                if(!world.getBlockState(p).equals(desired)) world.setBlockState(p,desired,Block.NOTIFY_LISTENERS);
            }
        } finally {mutation=false;}
    }
    /** Replayable upgrade: save all chest slots before moving either interactive block. */
    static boolean moveStatue(ServerWorld world,Site s) {
        if(s.layoutVersion>=2) return true;
        if(s.phase!=Phase.RUINED || !loaded(world,s)) return false;
        var oldChest=s.at(-5,-1,0);var oldSocket=s.anchor();
        var newChest=s.at(-5,-1,-6);var newSocket=s.at(0,0,-6);
        var ops=world.getRegistryManager().getOps(com.mojang.serialization.JsonOps.INSTANCE);
        if(s.statueMoveInventory==null) {
            // No foreign inventories may be destroyed by the upgrade.
            for(int x=-5;x<=4;x++)for(int y=-1;y<=7;y++)for(int z=-6;z<=6;z++) {
                var pos=s.at(x,y,z);
                if(world.getBlockEntity(pos)!=null && !pos.equals(oldChest) && !pos.equals(oldSocket)) return false;
            }
            if(!(world.getBlockEntity(oldChest) instanceof ChestBlockEntity chest)) return false;
            s.statueMoveInventory=new JsonArray();
            for(int slot=0;slot<chest.size();slot++) {
                var stack=chest.getStack(slot);
                s.statueMoveInventory.add(stack.isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());
            }
            if(!save()) return false;
        }
        mutation=true;
        try {
            if(world.getBlockEntity(oldChest) instanceof ChestBlockEntity chest) chest.clear();
            for(int x=-5;x<=4;x++)for(int y=-1;y<=7;y++)for(int z=-6;z<=6;z++) {
                var pos=s.at(x,y,z);
                var be=world.getBlockEntity(pos);
                if(be!=null && !pos.equals(oldChest) && !pos.equals(oldSocket) && !pos.equals(newChest) && !pos.equals(newSocket)) return false;
                var desired=ChurchLayout.state(false,new BlockPos(x,y,z),s.rotation);
                if(!world.getBlockState(pos).equals(desired))world.setBlockState(pos,desired,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            }
            if(!(world.getBlockEntity(newChest) instanceof ChestBlockEntity chest)) return false;
            chest.clear();
            for(int i=0;i<s.statueMoveInventory.size();i++) {
                var value=s.statueMoveInventory.get(i);
                if(!value.isJsonNull())chest.setStack(i,ItemStack.CODEC.parse(ops,value).getOrThrow());
            }
            chest.markDirty();
            if(world.getBlockEntity(newSocket) instanceof GuardianSocketEntity socket) { socket.layoutVersion=2;socket.markDirty(); }
            world.save(null,true,false);
            s.layoutVersion=2;s.statueMoveInventory=null;s.statuePrepared=true;
            return save();
        } finally { mutation=false; }
    }
    private static void blendTerrain(ServerWorld world,Site s) {
        if (!GuardianChurchTerrain.loaded(world,s)) return;
        if (s.terrainPlan==null) {
            s.terrainPlan=GuardianChurchTerrain.plan(world,s);
            // Store absolute target heights before edits: a restart cannot repeatedly raise the slope.
            if (!save()) return;
        }
        mutation=true;
        try {
            for (int budget=48;budget>0 && s.terrainCursor<s.terrainPlan.size();budget--)
                GuardianChurchTerrain.apply(world,s.terrainPlan.get(s.terrainCursor++));
            if (s.terrainCursor>=s.terrainPlan.size()) {
                world.save(null,true,false);
                s.terrainBlended=true;s.terrainPlan=null;
                save();
            }
        } finally { mutation=false; }
    }
    private static void stock(ServerWorld world,Site s,boolean fresh) {
        if (!(world.getBlockEntity(s.offering()) instanceof ChestBlockEntity chest)) return;
        // Reserve no player storage: choose empty slots, leaving any stored belongings intact.
        boolean already=false;
        for(int i=0;i<chest.size();i++) if (chest.getStack(i).isOf(ModItems.GUARDIAN_HEART) && chest.getStack(i).getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString("church_token","").equals(s.token)) already=true;
        if (!already) for(int i=0;i<chest.size();i++) if(chest.getStack(i).isEmpty()) {chest.setStack(i,heart(s));break;}
        if (!s.stocked && (fresh || s.guardian==null)) for(int i=0;i<chest.size();i++) if(chest.getStack(i).isEmpty()) {chest.setStack(i,book());break;}
        chest.markDirty();s.stocked=true;save();
    }
    private static void rewards(ServerWorld world,Site s) {
        for (int x:new int[]{-5,5}) if(world.getBlockEntity(s.at(x,-1,35)) instanceof ChestBlockEntity chest) {
            var contents=GuardianChurchLoot.roll(world,s.anchor(),x);
            chest.clear();
            for (int slot=0;slot<contents.size();slot++) chest.setStack(slot,contents.get(slot));
            chest.markDirty();
        }
    }
    public static String locate(ServerCommandSource source) {
        ServerWorld world=source.getWorld();if(world.getRegistryKey()!=World.OVERWORLD) return "Search for churches in the Overworld.";
        Site nearest=sites().stream().min(Comparator.comparingDouble(s -> s.anchor().getSquaredDistance(source.getPosition()))).orElse(null);
        // Recorded sites include manually placed test builds; vanilla /locate also finds unexplored structures.
        if(nearest!=null) return "Nearest known church: "+coords(nearest.socket())+" ("+nearest.phase.toString().toLowerCase()+"). Use /locate structure elementalwands:guardian_church to search natural generation.";
        return GuardianChurchLocator.start(source,8);
    }
    private static String coords(BlockPos pos) { return "X "+pos.getX()+", Y "+pos.getY()+", Z "+pos.getZ(); }
    public static String place(ServerCommandSource source) {
        ServerWorld world=source.getWorld();
        if (error!=null || state==null) return "Church storage is unavailable.";
        if (world.getRegistryKey()!=World.OVERWORLD) return "Place the church in the Overworld.";
        if (busy() || GuardianArenaManager.hasActiveArena()) return "Wait for the current build or encounter to finish.";
        int x=BlockPos.ofFloored(source.getPosition()).getX(),z=BlockPos.ofFloored(source.getPosition()).getZ()+40;
        // Placement validates the entire footprint before reserving or modifying it.
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for (int dx=-16;dx<=16;dx++) for(int dz=-10;dz<=42;dz++) {
            int y=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz);low=Math.min(low,y);high=Math.max(high,y);
        }
        if(high-low>6 || high>180) return "Find a flatter, lower clearing (33 by 53 blocks) in the +Z direction, then try again.";
        Site s=new Site();s.x=x;s.y=(low+high)/2+1;s.z=z;s.layoutVersion=2;
        for (int i=0;i<ChurchLayout.COUNT;i++) {
            BlockPos p=ChurchLayout.at(s.anchor(),s.rotation,ChurchLayout.local(i));
            if (!world.getWorldBorder().contains(p) || world.getBlockEntity(p)!=null || siteAt(world,p)!=null) return "Placement would overlap a container, existing church, or world border. Move to a clear site.";
            var b=world.getBlockState(p);
            if (b.isOf(Blocks.BEDROCK) && p.getY()<s.y-2) continue;
            if (!natural(b)) return "Placement stopped at "+coords(p)+": choose an undeveloped clearing without buildings or water.";
        }
        if (!world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,new Box(Vec3d.of(s.at(-16,-5,-10)),Vec3d.of(s.at(17,31,43))),e -> true).isEmpty()) return "Move players and animals out of the proposed build area first.";
        state.sites.add(s);if(!save()) {state.sites.remove(s);return "Could not save placement; nothing was built.";}
        force(world,s);return "Building a ruined church nearby. Socket: "+coords(s.socket())+". Allow a few seconds; /ew guardian church locate reports this site.";
    }
    private static boolean natural(BlockState b) {
        return b.isAir() || b.isOf(Blocks.GRASS_BLOCK) || b.isOf(Blocks.DIRT) || b.isOf(Blocks.STONE) || b.isOf(Blocks.GRAVEL)
                || b.isOf(Blocks.COARSE_DIRT) || b.isOf(Blocks.ROOTED_DIRT) || b.isOf(Blocks.PODZOL) || b.isOf(Blocks.SNOW)
                || b.isOf(Blocks.SHORT_GRASS) || b.isOf(Blocks.TALL_GRASS) || b.isOf(Blocks.FERN) || b.isOf(Blocks.LARGE_FERN)
                || b.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) || b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES);
    }
}
