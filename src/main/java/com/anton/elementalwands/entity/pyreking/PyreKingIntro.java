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
 * the hilt, the blade and then the whole hall take fire, and he rises. Then he slings the sword
 * down the hall, takes its chain in both fists and heaves himself after it to the hall's centre,
 * and the fight begins.
 * <p>
 * The server runs the timeline, holds everyone still and unhurt, puts the hall's fires out and
 * lights them again, and plays the sounds; each watcher's client flies its camera and draws the
 * chains on the same clock ({@link PyreKingIntroTrack}), so a party sees it together. Everyone can
 * skip; the fight starts early only once all of them have.
 */
public final class PyreKingIntro {
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    /** The clip that ends the scene: it brings him down the hall to his mark, and its travel is made real here. */
    static final String HAUL = "intro_haul";
    /**
     * He is stood on the runway this many ticks before the cut that shows him there: a client draws a moved entity
     * that much after the scene's clock, which its camera cuts on.
     */
    private static final int MOVED_EARLY = 3;
    private static final int QUIET = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final Map<UUID, PyreKingIntro> WATCHING = new HashMap<>();
    private static final Set<PyreKingIntro> RUNNING = new HashSet<>();

    private final PyreKingEntity king;
    private final ServerWorld world;
    private final BlockPos centre;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final PyreKingIntroTrack track = PyreKingIntroTrack.get();
    private final PyreKingTracks.Track pull = PyreKingTracks.get(HAUL);
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
        if (t >= haul - MOVED_EARLY) haul(t);
    }

    /**
     * The scene's last move. Behind a cut he is stood down off the dais, a throw's length up the hall; he slings the
     * sword to his mark, takes the chain and heaves himself after it. The clip draws that from where it began, and
     * this moves him as it does, so he is where he is drawn.
     */
    private void haul(int t) {
        Vec3d mark = at(track.mark()), runway = at(track.runway()), stuck = at(track.stuck());
        if (t == haul - MOVED_EARLY) {                // awaken has him in his guard already, which idle holds
            king.refreshPositionAndAngles(runway.x, runway.y, runway.z, PyreHall.KING_YAW, 0);
            king.setBodyYaw(PyreHall.KING_YAW);
            king.setHeadYaw(PyreHall.KING_YAW);
            king.play("idle", 0);
        }
        if (t == haul) king.play(HAUL, 0);
        if (!HAUL.equals(king.clip())) return;        // not begun, or ended: he stands where he is
        Vec3d was = king.getEntityPos(), at = runway.lerp(mark, pull.travel((t - haul) / 20.0) / pull.travelEnd());
        king.setPosition(at.x, at.y, at.z);
        int thrown = track.beat("throw"), struck = track.beat("stuck"), grab = track.beat("grab"), strain = track.beat("strain"),
                heave = track.beat("heave"), launch = track.beat("launch"), landed = track.beat("landed"), stopped = track.beat("stopped"),
                tear = track.beat("tear");
        if (t == haul + 12) sound(at, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.6f, .55f);          // he sinks back over the blade
        if (t == thrown) {
            sound(at, SoundEvents.ITEM_TRIDENT_THROW.value(), 2.6f, .6f);
            sound(at, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.2f, .5f);
        }
        if (t > thrown && t < struck && (t - thrown) % 2 == 0) sound(at, SoundEvents.BLOCK_CHAIN_STEP, 1.8f, .9f + (t - thrown) * .04f);    // the chain pays out
        if (t == struck) {
            sound(stuck, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .7f);
            sound(stuck, SoundEvents.BLOCK_ANVIL_LAND, 2.2f, .55f);
            dust(stuck, 40, .4, .12);
            world.spawnParticles(ParticleTypes.LAVA, true, false, stuck.x, stuck.y + .2, stuck.z, 8, .3, .1, .3, 0);
        }
        if (t == struck + 4) sound(stuck, SoundEvents.BLOCK_ANVIL_PLACE, 1f, 1.5f);                           // the blade still ringing
        if (t == grab - 8 || t == grab - 4) sound(at, SoundEvents.BLOCK_CHAIN_PLACE, 1.8f, t == grab - 8 ? .7f : .6f);  // a turn round his forearm
        if (t == grab) sound(at, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 2f, .5f);
        if (t == strain - 3) {                                                                                 // the chain comes taut and holds him
            sound(at, SoundEvents.BLOCK_CHAIN_HIT, 2.6f, .5f);
            sound(at, SoundEvents.BLOCK_GRINDSTONE_USE, 1.6f, .35f);
        }
        if (t == heave) sound(at, SoundEvents.ENTITY_RAVAGER_ATTACK, 2.6f, .5f);
        if (t >= heave + 2 && t < launch) dust(at, 8, .4, .08);                                                // his feet dragged across the floor
        if (t == launch) {
            sound(at, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2.2f, .5f);
            dust(at, 30, .5, .15);
        }
        if (t > launch && t < landed && (t - launch) % 3 == 0) sound(at, SoundEvents.BLOCK_CHAIN_STEP, 1.8f, 1.3f);
        if (t == landed) {
            sound(at, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3.5f, .5f);
            sound(at, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.6f, .5f);
            sound(at, SoundEvents.BLOCK_ANVIL_LAND, 2.4f, .4f);
            dust(at, 70, .8, .2);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y + .2, at.z, 10, .7, .1, .7, .02);
            // The line he flew burns behind him, as a Haul's does in the fight.
            king.combat().scorch(world, runway.lerp(mark, pull.travel((launch - haul) / 20.0) / pull.travelEnd()), at);
        }
        if (t >= landed && t < stopped) {                                                                      // ploughing to a stop: stone and sparks along the way
            for (int i = 0; i < 3; i++) {
                Vec3d on = was.lerp(at, (i + .5) / 3);
                dust(on, 8, .5, .12);
                world.spawnParticles(ParticleTypes.LAVA, true, false, on.x, on.y + .15, on.z, 2, .5, .05, .2, 0);
            }
            if ((t - landed) % 2 == 0) sound(at, SoundEvents.BLOCK_BASALT_BREAK, 2.2f, .5f + (t - landed) * .01f);
            if (t == landed + 1) sound(at, SoundEvents.BLOCK_GRINDSTONE_USE, 2.4f, .4f);
        }
        if (t == stopped) {
            sound(at, SoundEvents.ENTITY_IRON_GOLEM_STEP, 2.4f, .5f);
            dust(at, 20, .6, .08);
        }
        if (t == track.beat("take")) sound(stuck, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 2.4f, .5f);
        if (t == tear - 5) sound(stuck, SoundEvents.BLOCK_GRINDSTONE_USE, 1.8f, .5f);
        if (t == tear) {
            sound(stuck, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.4f, .5f);
            dust(stuck, 24, .3, .15);
        }
    }

    /** Stone thrown up off the floor. */
    private void dust(Vec3d at, int count, double spread, double speed) {
        BlockState floor = world.getBlockState(BlockPos.ofFloored(at.x, at.y - .5, at.z));
        if (floor.isAir()) floor = Blocks.POLISHED_BLACKSTONE.getDefaultState();
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), true, false, at.x, at.y + .15, at.z, count, spread, .1, spread, speed);
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
        // Skipped, he is simply on his mark, in his guard. Otherwise his haul has just set him down there.
        Vec3d mark = at(track.mark());
        if (king.getEntityPos().squaredDistanceTo(mark) > .25) {
            king.refreshPositionAndAngles(mark.x, mark.y, mark.z, PyreHall.KING_YAW, 0);
            king.setBodyYaw(PyreHall.KING_YAW);
            king.setHeadYaw(PyreHall.KING_YAW);
        }
        if (!"idle".equals(king.clip())) king.play("idle", 0);
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
