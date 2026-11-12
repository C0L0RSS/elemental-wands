package com.anton.elementalwands.entity.pyreking;

import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.network.ModNetworking;
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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens a Pyre King fight (the developer's idea, Nov 11). He sits slumped on his
 * throne like a statue, bound by a web of chains strung through the dark hall, his greatsword
 * hanging point down on a chain high above him. The chains burn through from the gate to the
 * throne, the sword falls and drives into the dais at his feet, his hand reaches and closes on
 * the hilt, the blade and then the whole hall take fire, and he rises. Then his chain hauls him to
 * the centre of the hall and the fight begins.
 * <p>
 * The server runs the timeline, holds everyone still and unhurt, puts the hall's fires out and
 * lights them again, and plays the sounds; each watcher's client flies its camera and draws the
 * chains on the same clock ({@link PyreKingIntroTrack}), so a party sees it together. Everyone can
 * skip; the fight starts early only once all of them have.
 */
public final class PyreKingIntro {
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    /** Before the Haul that ends the scene he is stood this far up the hall from his mark, behind a cut. */
    static final double RUNWAY = 26;
    private static final int QUIET = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final Map<UUID, PyreKingIntro> WATCHING = new HashMap<>();
    private static final Set<PyreKingIntro> RUNNING = new HashSet<>();

    private final PyreKingEntity king;
    private final ServerWorld world;
    private final BlockPos centre;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final PyreKingIntroTrack track = PyreKingIntroTrack.get();
    private final int length = track.length(), awaken = track.beat("awaken"), haul = track.beat("haul");
    private long start;
    private int clock = -1, lit;
    private boolean dark;

    PyreKingIntro(PyreKingEntity king, ServerWorld world, BlockPos centre, List<ServerPlayerEntity> players, long start) {
        this.king = king;
        this.world = world;
        this.centre = centre;
        this.start = start;
        Vec3d mark = at(track.mark());
        for (ServerPlayerEntity player : players) {
            PyreKingIntro previous = WATCHING.put(player.getUuid(), this);
            if (previous != null && previous != this) previous.watchers.remove(player);
            Vec3d at = player.getEntityPos();
            // Everyone faces his mark, so the camera's return lands on a view of him.
            player.teleport(world, at.x, at.y, at.z, Set.of(), facing(at, mark), 0, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            watchers.put(player, at);
        }
        Vec3d seat = at(track.seat());
        king.refreshPositionAndAngles(seat.x, seat.y, seat.z, PyreHall.KING_YAW, 0);
        king.setBodyYaw(PyreHall.KING_YAW);
        king.setHeadYaw(PyreHall.KING_YAW);
        king.play("throned", 0);
        quench();
        RUNNING.add(this);
    }

    public static void register() {
        // Watchers can't be hurt while their camera is somewhere else; only /kill and the void still reach them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof PlayerEntity
                && WATCHING.containsKey(entity.getUuid()) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)));
        // A hall must not be saved dark: whatever was out is lit again.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { for (PyreKingIntro intro : Set.copyOf(RUNNING)) intro.relight(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { WATCHING.clear(); RUNNING.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PyreKingIntro intro = WATCHING.remove(handler.getPlayer().getUuid());
            if (intro != null) intro.watchers.remove(handler.getPlayer());
        });
    }

    /** A player held in the intro: frozen, unhurt and unable to cast. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        PyreKingIntro intro = WATCHING.get(player.getUuid());
        if (intro != null) intro.skipped.add(player.getUuid());
    }

    /** A point of the track, in the world. */
    private Vec3d at(Vec3d cell) { return new Vec3d(centre.getX() + cell.x, PyreHall.SURFACE_Y + cell.y, centre.getZ() + cell.z); }
    private BlockPos block(PyreKingIntroTrack.Flame flame) { return PyreHall.cell(centre, flame.x(), flame.y(), flame.z()); }
    private static float facing(Vec3d from, Vec3d to) { return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)); }

    // ------------------------------------------------------------------ running the scene

    /** Runs one tick; false once the scene has handed over to the fight. */
    boolean tick() {
        int t = ++clock;
        if (t == 0) begin();
        holdWatchers(false);
        if (t >= length || t >= SKIP_AFTER && everyoneSkipped()) {
            finish();
            return false;
        }
        stage(t);
        return true;
    }

    private void begin() {
        start = world.getTime();
        for (ServerPlayerEntity player : watchers.keySet())
            if (ServerPlayNetworking.canSend(player, ModNetworking.PyreKingIntroPayload.ID)) ServerPlayNetworking.send(player, payload(true));
    }

    private ModNetworking.PyreKingIntroPayload payload(boolean active) {
        return new ModNetworking.PyreKingIntroPayload(king.getId(), start, centre, active);
    }

    /** Keeps every watcher where the scene found them, or with {@code release} lets them all go. Leavers stop counting toward the skip. */
    private void holdWatchers(boolean release) {
        for (var it = watchers.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayerEntity player = entry.getKey();
            if (release || player.isRemoved() || !player.isAlive() || player.getEntityWorld() != world) {
                WATCHING.remove(player.getUuid(), this);
                player.fallDistance = 0;
                if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.PyreKingIntroPayload.ID))
                    ServerPlayNetworking.send(player, payload(false));
                it.remove();
                continue;
            }
            Vec3d lock = entry.getValue();
            if (player.getEntityPos().squaredDistanceTo(lock) > .01)
                player.teleport(world, lock.x, lock.y, lock.z, Set.of(), player.getYaw(), player.getPitch(), false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
        }
    }

    private boolean everyoneSkipped() {
        for (ServerPlayerEntity player : watchers.keySet()) if (!skipped.contains(player.getUuid())) return false;
        return true;
    }

    private void stage(int t) {
        Vec3d seat = at(track.seat()), sword = at(track.sword(t));
        if (t == awaken) king.play("awaken", 0);
        // The dark hall: iron ticking as it cools, far off and near.
        if (t < track.beat("burn") && t % 46 == 20) sound(at(new Vec3d((t * 7 % 41) - 20, 9, 30 - t * .14)), SoundEvents.BLOCK_CHAIN_STEP, 1.4f, .5f);
        if (t == track.beat("pulse")) {
            sound(seat.add(0, 2.2, -1.7), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.2f, .5f);
            sound(seat.add(0, 2.2, -1.7), SoundEvents.ENTITY_WARDEN_HEARTBEAT, 3f, .7f);
        }
        // Each chain hisses as heat takes it and rings as it parts.
        for (PyreKingIntroTrack.Chain chain : track.chains()) {
            if (t == chain.heat()) sound(at(chain.a()), SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.6f, .6f);
            if (t == chain.parts() && !chain.sword()) {
                Vec3d where = at(chain.a().lerp(chain.b(), chain.part()).add(0, -chain.sag() * 4 * chain.part() * (1 - chain.part()), 0));
                sound(where, SoundEvents.BLOCK_CHAIN_BREAK, 2.6f, .6f + world.getRandom().nextFloat() * .3f);
                world.spawnParticles(ParticleTypes.LAVA, true, false, where.x, where.y, where.z, 6, .2, .2, .2, 0);
            }
        }
        int fall = track.beat("fall"), struck = track.beat("struck");
        if (t == fall) sound(sword, SoundEvents.BLOCK_CHAIN_BREAK, 3f, .45f);
        if (t == fall + 8) sound(sword, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, .5f);
        if (t == struck) {
            Vec3d floor = new Vec3d(sword.x, seat.y, sword.z);
            sound(floor, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .6f);
            sound(floor, SoundEvents.BLOCK_ANVIL_LAND, 2.2f, .5f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.POLISHED_BLACKSTONE.getDefaultState()), true, false,
                    floor.x, floor.y + .15, floor.z, 50, .5, .1, .5, .12);
            world.spawnParticles(ParticleTypes.POOF, true, false, floor.x, floor.y + .2, floor.z, 14, .4, .1, .4, .02);
        }
        if (t == struck + 4 || t == struck + 9) sound(sword, SoundEvents.BLOCK_ANVIL_PLACE, 1f, 1.6f);       // the blade still ringing
        if (t == awaken + 90 || t == awaken + 102) sound(seat.add(-1.85, 2.2, -.9), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.4f, .7f);
        if (t == awaken + 130) sound(seat.add(0, 2.5, -1), SoundEvents.BLOCK_GRINDSTONE_USE, 1.6f, .4f);     // he leans out of the throne
        if (t == track.beat("grasp")) sound(sword, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 2.4f, .5f);
        int ignite = track.beat("ignite");
        if (t == ignite) {
            sound(sword, SoundEvents.ITEM_FIRECHARGE_USE, 3f, .5f);
            sound(sword, SoundEvents.ENTITY_BLAZE_SHOOT, 2.4f, .5f);
        }
        if (t == ignite + 14) sound(seat.add(0, 3, 0), SoundEvents.ENTITY_RAVAGER_ROAR, 2.2f, .45f);
        // The hall's fires catch, throne to gate.
        PyreKingIntroTrack.Flame[] flames = track.flames();
        int caught = 0;
        while (lit < flames.length && flames[lit].tick() <= t) { light(flames[lit]); lit++; caught++; }
        if (caught > 0 && t % 3 == 0) {
            PyreKingIntroTrack.Flame last = flames[lit - 1];
            Vec3d where = Vec3d.ofCenter(block(last));
            sound(where, SoundEvents.ITEM_FIRECHARGE_USE, 3f, .6f + world.getRandom().nextFloat() * .2f);
            world.spawnParticles(ParticleTypes.LAVA, true, false, where.x, where.y, where.z, 8, .6, .4, .6, 0);
        }
        int rise = track.beat("rise");
        if (t == rise + 8) sound(seat, SoundEvents.BLOCK_GRINDSTONE_USE, 1.8f, .35f);
        if (t == awaken + 386) sound(sword, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.4f, .5f);      // the blade torn out of the stone
        if (t == awaken + 412) sound(seat, SoundEvents.ENTITY_RAVAGER_ROAR, 3f, .55f);
        if (t == haul) {
            // Behind the cut he is down off the dais; the chain brings him the rest of the way, to his mark.
            Vec3d mark = at(track.mark()), runway = mark.add(0, 0, -RUNWAY);
            king.refreshPositionAndAngles(runway.x, runway.y, runway.z, PyreHall.KING_YAW, 0);
            king.setBodyYaw(PyreHall.KING_YAW);
            king.setHeadYaw(PyreHall.KING_YAW);
            king.play("idle", 0);
            king.combat().introHaul(world, mark);
        }
    }

    // ------------------------------------------------------------------ the hall's fires

    /** Every fire in the hall goes out, and the light blocks that carry them with it. */
    private void quench() {
        for (PyreKingIntroTrack.Flame flame : track.flames()) {
            BlockPos pos = block(flame);
            BlockState there = world.getBlockState(pos);
            if (there.isOf(Blocks.FIRE) || there.isOf(Blocks.LIGHT)) world.setBlockState(pos, Blocks.AIR.getDefaultState(), QUIET);
        }
        dark = true;
        lit = 0;
    }

    private void light(PyreKingIntroTrack.Flame flame) {
        BlockPos pos = block(flame);
        if (!world.getBlockState(pos).isAir()) return;
        world.setBlockState(pos, flame.level() > 0 ? Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, flame.level()) : Blocks.FIRE.getDefaultState(), QUIET);
    }

    /** Whatever is still out is lit: the scene ended early, was called off, or the server is stopping. */
    private void relight() {
        if (!dark) return;
        PyreKingIntroTrack.Flame[] flames = track.flames();
        for (; lit < flames.length; lit++) light(flames[lit]);
        dark = false;
    }

    // ------------------------------------------------------------------ ending it

    private void finish() {
        relight();
        RUNNING.remove(this);
        holdWatchers(true);
        // Skipped, he is simply on his mark. Otherwise the Haul has just set him down there.
        Vec3d mark = at(track.mark());
        if (king.getEntityPos().squaredDistanceTo(mark) > .25) {
            king.refreshPositionAndAngles(mark.x, mark.y, mark.z, PyreHall.KING_YAW, 0);
            king.setBodyYaw(PyreHall.KING_YAW);
            king.setHeadYaw(PyreHall.KING_YAW);
        }
        king.endIntro();
    }

    /** He is gone or an operator took over mid-scene: light the hall and let everyone go without starting a fight. */
    void cancel() {
        relight();
        RUNNING.remove(this);
        holdWatchers(true);
    }

    int watcherCount() { return watchers.size(); }
    int clock() { return clock; }

    private void sound(Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
