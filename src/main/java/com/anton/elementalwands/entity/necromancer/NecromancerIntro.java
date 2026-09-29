package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SoulGlow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens a crypt fight: the camera finds a zombie shuffling between the graves,
 * its soul is torn out toward something off-screen, and a pan after the soul reveals the
 * Necromancer already standing in the circle, hauling it into his raised staff. He turns on the
 * players and his staff slam sends a ring of soul fire out that lights the braziers. The skeleton
 * inside stays hidden for phase two. The server runs the timeline and holds everyone still and
 * unhurt; each watcher's client flies its camera along the same timeline, so a party sees it
 * together. Everyone can skip; the fight starts early only once all of them have.
 */
public final class NecromancerIntro {
    /** Timeline, in ticks from the first frame. The Necromancer's intro clip shares this clock. */
    public static final int LENGTH = 260, WALK_END = 64, ARCH = 66, PULL = 72, FREE = 104, CRUMBLE = 106, REVEAL = 108,
            SOUL_ARRIVE = 148, EYES = 166, HERO_END = 196, TURN = 186, TURN_END = 206, LEVEL = 208, SLAM = 216, RING_END = 236,
            TITLE = 220, TITLE_END = 252, RETURN = 244;
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    static final double RING_REACH = 44;
    /**
     * The scene around the circle, authored with the players to the south (+Z) as they arrive in
     * the crypt; {@link #place} turns it with the Necromancer's facing. The zombie shuffles south
     * along the row of graves west of the circle (their markers stand a block and a half to its
     * right), then turns toward him as the pull takes it.
     */
    public static final Vec3d VICTIM_FROM = new Vec3d(-19.5, 0, -11.5), VICTIM_TO = new Vec3d(-19.5, 0, -6.5);
    public static final double VICTIM_CHEST = 1.1;
    /** He faces the zombie while he hauls its soul in, then turns to the players. */
    public static final float PULL_FACING = (float)Math.toDegrees(Math.atan2(-VICTIM_TO.x, VICTIM_TO.z));
    /**
     * The raised staff's flame while he hauls, in his own frame (front +Z, +X to his left), measured
     * from the intro clip: it holds still from tick 72 to 148, so the soul dives straight in.
     */
    public static final Vec3d STAFF_RAISED = new Vec3d(-.578, 3.788, .095);
    /** The flame in the scene frame, where the torn soul lands. */
    public static final Vec3d STAFF_HEAD = place(Vec3d.ZERO, PULL_FACING, STAFF_RAISED);

    private static final Map<UUID, NecromancerIntro> WATCHING = new HashMap<>();

    private final NecromancerEntity boss;
    private final Vec3d centre;
    private final float yaw;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final List<BlockPos> braziers = new ArrayList<>();
    private IntroZombieEntity victim;
    private IntroSoulEntity torn;
    private long start;
    private int clock = -1;

    NecromancerIntro(NecromancerEntity boss, ServerWorld world, List<ServerPlayerEntity> players) {
        this.boss = boss;
        centre = boss.getEntityPos();
        yaw = boss.getYaw();
        for (ServerPlayerEntity player : players) {
            NecromancerIntro previous = WATCHING.put(player.getUuid(), this);
            if (previous != null && previous != this) previous.watchers.remove(player);
            // The ritual's blindness would black out the whole scene; the client fades in from black instead.
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            Vec3d at = player.getEntityPos();
            // Face the circle, so the camera's return lands on a view of the boss.
            float face = (float)Math.toDegrees(Math.atan2(-(centre.x - at.x), centre.z - at.z));
            player.teleport(world, at.x, at.y, at.z, Set.of(), face, 0, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            watchers.put(player, at);
        }
        findBraziers(world);
        turn(0);
    }

    public static void register() {
        // Watchers can't be hurt while their camera is somewhere else; only /kill and the void still reach them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof PlayerEntity
                && WATCHING.containsKey(entity.getUuid()) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
        // A leaver is let go at once: an unloaded boss can no longer tick them out, and they must not rejoin still held.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            NecromancerIntro intro = WATCHING.remove(handler.getPlayer().getUuid());
            if (intro != null) intro.watchers.remove(handler.getPlayer());
        });
    }

    /** A player held in an intro: frozen, unhurt and unable to cast. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        NecromancerIntro intro = WATCHING.get(player.getUuid());
        if (intro != null) intro.skipped.add(player.getUuid());
    }

    /** A point of the scene, authored around the circle with the players to the south, turned with the boss. */
    public static Vec3d place(Vec3d centre, float yaw, Vec3d local) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return centre.add(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    }

    /** The zombie's feet, relative to the circle: it walks, stops, then hangs lifted while its soul is torn. */
    public static Vec3d victimAt(double t) {
        if (t < WALK_END) return VICTIM_FROM.lerp(VICTIM_TO, Math.max(0, t) / WALK_END);
        return VICTIM_TO.add(0, lift(t), 0);
    }

    private static double lift(double t) { return .45 * smooth((t - ARCH) / 14); }

    /**
     * The torn soul {@code age} ticks after it starts to come out: it stretches from the chest,
     * fighting the pull, then is dragged in a rising arc into the staff, faster and faster.
     */
    public static Vec3d tornAt(Vec3d from, Vec3d to, double age) {
        double tear = FREE - PULL, flight = SOUL_ARRIVE - FREE;
        Vec3d dir = to.subtract(from).normalize(), side = new Vec3d(-dir.z, 0, dir.x);
        if (age < tear) {
            // Out of the chest a little at a time: it surges, is caught, surges again, trembling harder.
            double s = age / tear, out = TORN_REACH * smooth(s) + .1 * Math.sin(s * 5 * Math.PI) * s * (1 - s) * 4;
            double tremble = .02 + .04 * s;
            return from.add(dir.multiply(out)).add(side.multiply(tremble * Math.sin(age * 2.3))).add(0, tremble * Math.sin(age * 3.1), 0);
        }
        double s = MathHelper.clamp((age - tear) / flight, 0, 1), pulled = Math.pow(s, 1.7);
        return from.add(dir.multiply(TORN_REACH)).lerp(to, pulled).add(0, 1.2 * 4 * pulled * (1 - pulled), 0)
                .add(side.multiply(.5 * Math.sin(pulled * Math.PI) * Math.sin(age * .4)));
    }

    /** How far the soul stretches out of the chest before it rips free. */
    public static final double TORN_REACH = .9;

    /** Where the soul comes out: the zombie's chest, hanging fully lifted. */
    public static Vec3d soulOrigin() { return victimAt(FREE).add(0, VICTIM_CHEST, 0); }

    public static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    private Vec3d place(Vec3d local) { return place(centre, yaw, local); }

    /** His facing in the scene frame: toward the zombie, then round to the players. */
    public static float facing(double t) {
        return (float)MathHelper.lerp(smooth((t - TURN) / (TURN_END - TURN)), PULL_FACING, 0);
    }

    private void turn(int t) {
        float face = yaw + facing(t);
        boss.setYaw(face); boss.setBodyYaw(face); boss.setHeadYaw(face);
    }

    /** Runs one tick; false once the scene has handed over to the fight. */
    boolean tick(ServerWorld world) {
        int t = ++clock;
        if (t == 0) begin(world);
        hold(world);
        if (t >= LENGTH || t >= SKIP_AFTER && everyoneSkipped()) {
            finish(world, t < LENGTH);
            return false;
        }
        stage(world, t);
        return true;
    }

    private void begin(ServerWorld world) {
        start = world.getTime();
        for (ServerPlayerEntity player : watchers.keySet())
            if (ServerPlayNetworking.canSend(player, ModNetworking.NecromancerIntroPayload.ID))
                ServerPlayNetworking.send(player, new ModNetworking.NecromancerIntroPayload(boss.getId(), start, yaw, centre, true));
        sound(world, centre, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2f, .6f);
        IntroZombieEntity zombie = ModEntities.INTRO_ZOMBIE.create(world, SpawnReason.EVENT);
        if (zombie == null) return;
        Vec3d at = place(victimAt(0));
        float face = heading(new Vec3d(0, 0, 1));
        zombie.refreshPositionAndAngles(at.x, at.y, at.z, face, 0);
        zombie.setHeadYaw(face);
        zombie.setBodyYaw(face);
        world.spawnEntity(zombie);
        victim = zombie;
    }

    /** Keeps every watcher where the scene found them; leavers stop counting toward the skip. */
    private void hold(ServerWorld world) {
        for (var it = watchers.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayerEntity player = entry.getKey();
            if (player.isRemoved() || !player.isAlive() || player.getEntityWorld() != world) {
                WATCHING.remove(player.getUuid(), this);
                it.remove();
                continue;
            }
            Vec3d lock = entry.getValue();
            if (player.getEntityPos().squaredDistanceTo(lock) > .01)
                player.teleport(world, lock.x, lock.y, lock.z, Set.of(), player.getYaw(), player.getPitch(), false);
            player.setVelocity(Vec3d.ZERO);
            // A watcher caught mid-jump keeps sinking toward the ground and being put back; none of that is a fall.
            player.fallDistance = 0;
        }
    }

    private boolean everyoneSkipped() {
        for (ServerPlayerEntity player : watchers.keySet()) if (!skipped.contains(player.getUuid())) return false;
        return true;
    }

    private void stage(ServerWorld world, int t) {
        turn(t);
        if (victim != null && !victim.isRemoved()) walk(world, t);
        if (t == 12 || t == 44) sound(world, victimHead(t), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.4f, .8f);
        if (t == ARCH) {
            sound(world, victimHead(t), SoundEvents.ENTITY_ZOMBIE_HURT, 1.6f, .5f);
            sound(world, victimHead(t), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 1.6f, .6f);
        }
        if (t == PULL) {
            tear(world, t);
            sound(world, place(STAFF_HEAD), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 2f, .6f);
        }
        if (t > PULL && t < FREE && t % 2 == 0) {
            Vec3d chest = place(victimAt(t).add(0, VICTIM_CHEST, 0));
            world.spawnParticles(ParticleTypes.SOUL, true, false, chest.x, chest.y, chest.z, 1, .12, .15, .12, .01);
        }
        if (t == PULL + 16) sound(world, victimHead(t), SoundEvents.ENTITY_ZOMBIE_HURT, 1.4f, .4f);
        if (t >= PULL && t < SOUL_ARRIVE && t % 3 == 0) {
            // The raised staff draws soul light in while he hauls.
            Vec3d head = place(STAFF_HEAD);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, head.x, head.y, head.z, 2, .15, .15, .15, .01);
        }
        if (t == FREE) {
            // The rip: the tail tears out of the chest in a burst of soul fire.
            Vec3d chest = place(victimAt(t).add(0, VICTIM_CHEST, 0));
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, chest.x, chest.y, chest.z, 24, .15, .2, .15, .08);
            world.spawnParticles(ParticleTypes.SOUL, true, false, chest.x, chest.y, chest.z, 10, .2, .2, .2, .05);
            sound(world, victimHead(t), SoundEvents.ENTITY_ZOMBIE_DEATH, 1.6f, .6f);
            sound(world, victimHead(t), SoundEvents.ENTITY_VEX_CHARGE, 1.6f, .5f);
            sound(world, victimHead(t), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 2f, 1.2f);
        }
        if (t == CRUMBLE) crumble(world);
        if (t == SOUL_ARRIVE) {
            Vec3d head = place(STAFF_HEAD);
            if (torn != null) torn.discard();
            torn = null;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, head.x, head.y, head.z, 40, .2, .2, .2, .12);
            world.spawnParticles(ParticleTypes.SOUL, true, false, head.x, head.y, head.z, 12, .3, .3, .3, .05);
            sound(world, head, SoundEvents.ENTITY_BLAZE_SHOOT, 1.8f, .5f);
            sound(world, head, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, .7f);
            SoulGlow.light(world, head, NecromancerRules.GLOW_IMPACT, NecromancerRules.GLOW_IMPACT_TICKS);
        }
        if (t > SOUL_ARRIVE && t < TURN && t % 2 == 0) {
            // The staff burns brighter with the soul inside it.
            Vec3d head = place(STAFF_HEAD);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, head.x, head.y, head.z, 3, .12, .15, .12, .02);
        }
        if (t == EYES) {
            Vec3d head = centre.add(0, 2, 0);
            sound(world, head, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.5f, .5f);
            sound(world, head, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.5f, .6f);
        }
        if (t == TURN) sound(world, centre, SoundEvents.ENTITY_PHANTOM_FLAP, 2f, .5f);
        if (t == LEVEL) sound(world, centre, SoundEvents.ENTITY_WARDEN_ROAR, 2.5f, .7f);
        if (t == SLAM) slam(world);
        if (t > SLAM && t <= RING_END) ring(world, t);
    }

    private void walk(ServerWorld world, int t) {
        Vec3d at = place(victimAt(t));
        victim.setPosition(at.x, at.y, at.z);
        float walking = heading(new Vec3d(0, 0, 1)), pulled = heading(STAFF_HEAD.subtract(VICTIM_TO));
        float face = MathHelper.lerpAngleDegrees((float)smooth((t - WALK_END + 4) / 6.0), walking, pulled);
        victim.setYaw(face);
        victim.setBodyYaw(face);
        victim.setHeadYaw(face);
    }

    private void tear(ServerWorld world, int t) {
        IntroSoulEntity soul = ModEntities.INTRO_SOUL.create(world, SpawnReason.EVENT);
        if (soul == null) return;
        soul.script(place(soulOrigin()), place(STAFF_HEAD), start + PULL);
        world.spawnEntity(soul);
        torn = soul;
        sound(world, victimHead(t), SoundEvents.ENTITY_VEX_CHARGE, 1.4f, .45f);
    }

    /** The empty body falls apart into bone dust. */
    private void crumble(ServerWorld world) {
        if (victim == null) return;
        Vec3d at = victim.getEntityPos();
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE)), true, false,
                at.x, at.y + .9, at.z, 24, .25, .5, .25, .08);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.getDefaultState()), true, false,
                at.x, at.y + .6, at.z, 40, .3, .5, .3, .1);
        world.spawnParticles(ParticleTypes.ASH, true, false, at.x, at.y + 1, at.z, 30, .4, .6, .4, .02);
        sound(world, at, SoundEvents.ENTITY_SKELETON_DEATH, 1.4f, .5f);
        sound(world, at, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.5f, .7f);
        victim.discard();
        victim = null;
    }

    private void slam(ServerWorld world) {
        Vec3d at = centre.add(0, .1, 0);
        sound(world, at, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .6f);
        sound(world, at, SoundEvents.ENTITY_BLAZE_SHOOT, 2.5f, .5f);
        sound(world, at, SoundEvents.ITEM_FIRECHARGE_USE, 2.5f, .5f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor(world, at)), true, false,
                at.x, at.y + .2, at.z, 60, 1.2, .2, 1.2, .2);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, at.x, at.y + .3, at.z, 50, .6, .2, .6, .15);
    }

    /** The slam's soul-fire ring rolls out across the clearing and lights each brazier it reaches. */
    private void ring(ServerWorld world, int t) {
        double s = (t - SLAM) / (double)(RING_END - SLAM), eased = 1 - (1 - s) * (1 - s), radius = 1.5 + (RING_REACH - 1.5) * eased;
        int points = Math.min(160, (int)(radius * 3.5));
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points + t * .05;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, centre.x + Math.cos(angle) * radius, centre.y + .15,
                    centre.z + Math.sin(angle) * radius, 1, 0, .05, 0, .01);
        }
        for (var it = braziers.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            double dx = pos.getX() + .5 - centre.x, dz = pos.getZ() + .5 - centre.z;
            if (dx * dx + dz * dz > radius * radius) continue;
            ignite(world, pos, true);
            it.remove();
        }
    }

    private static void ignite(ServerWorld world, BlockPos pos, boolean loud) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.SOUL_CAMPFIRE)) return;
        world.setBlockState(pos, state.with(CampfireBlock.LIT, true));
        if (!loud) return;
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 20, .2, .3, .2, .08);
        world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.4f, .7f);
    }

    /** Puts out the clearing's soul-fire braziers, so the slam is what lights them. */
    private void findBraziers(ServerWorld world) {
        int reach = (int)RING_REACH + 1;
        BlockPos c = BlockPos.ofFloored(centre);
        for (int cx = ChunkSectionPos.getSectionCoord(c.getX() - reach); cx <= ChunkSectionPos.getSectionCoord(c.getX() + reach); cx++)
            for (int cz = ChunkSectionPos.getSectionCoord(c.getZ() - reach); cz <= ChunkSectionPos.getSectionCoord(c.getZ() + reach); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockPos pos : BlockPos.iterate(Math.max(cx << 4, c.getX() - reach), c.getY() - 1, Math.max(cz << 4, c.getZ() - reach),
                        Math.min((cx << 4) + 15, c.getX() + reach), c.getY() + 4, Math.min((cz << 4) + 15, c.getZ() + reach))) {
                    BlockState state = world.getBlockState(pos);
                    if (!state.isOf(Blocks.SOUL_CAMPFIRE)) continue;
                    braziers.add(pos.toImmutable());
                    if (state.get(CampfireBlock.LIT)) world.setBlockState(pos, state.with(CampfireBlock.LIT, false));
                }
            }
    }

    private void finish(ServerWorld world, boolean early) {
        clear();
        // A skip lands on the finished scene: braziers lit, body whole.
        if (early) for (BlockPos pos : braziers) ignite(world, pos, false);
        braziers.clear();
        release();
        boss.endIntro(early);
    }

    /** The boss is gone mid-scene: tidy up and let everyone go without starting a fight. */
    void cancel() {
        clear();
        release();
    }

    private void clear() {
        if (victim != null) victim.discard();
        if (torn != null) torn.discard();
        victim = null;
        torn = null;
    }

    private void release() {
        for (ServerPlayerEntity player : watchers.keySet()) {
            WATCHING.remove(player.getUuid(), this);
            player.fallDistance = 0;
            if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.NecromancerIntroPayload.ID))
                ServerPlayNetworking.send(player, new ModNetworking.NecromancerIntroPayload(boss.getId(), start, yaw, centre, false));
        }
        watchers.clear();
    }

    /** Watchers still held by this scene, for tests and status. */
    int watcherCount() { return watchers.size(); }
    boolean victimAlive() { return victim != null && !victim.isRemoved(); }
    boolean soulTorn() { return torn != null && !torn.isRemoved(); }
    int unlitBraziers() { return braziers.size(); }

    private Vec3d victimHead(double t) { return place(victimAt(t).add(0, 1.7, 0)); }

    private float heading(Vec3d local) {
        Vec3d dir = place(Vec3d.ZERO, yaw, local);
        return (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private static BlockState floor(ServerWorld world, Vec3d at) {
        BlockState state = world.getBlockState(BlockPos.ofFloored(at.x, at.y - .5, at.z));
        return state.isAir() ? Blocks.SOUL_SOIL.getDefaultState() : state;
    }

    private static void sound(ServerWorld world, Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private static void sound(ServerWorld world, Vec3d at, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch, world.getRandom().nextLong());
    }
}
