package com.anton.elementalwands.entity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * The Scar and the Sweep in a real fight. The Scar pens a target who stays put without hurting them,
 * its spikes stop players and not the Guardian, touching one hurts once, the leap follows at once and
 * lands while the walls stand, and the walls fall on time. The Sweep strikes a player behind it
 * without turning to face them.
 */
public final class GuardianScarSmokeMod implements ModInitializer {
    private static final GuardianCombatRules.Attack SCAR = GuardianCombatRules.Attack.SCAR, LEAP = GuardianCombatRules.Attack.LEAP,
            SWEEP = GuardianCombatRules.Attack.SWEEP, SLAM = GuardianCombatRules.Attack.SLAM;
    private int tick, y, stage, raised;
    private long scarStart, leapStart, sweepStart;
    private float dealt;
    private double px = .5, pz = 15.5;
    private FracturedGuardianEntity boss;
    private ServerPlayerEntity player;
    private final StringBuilder report = new StringBuilder();

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("SCAR_FAILED.txt"), e + "\n" + report); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void run(MinecraftServer server) throws Exception {
        tick++;
        ServerWorld world = server.getOverworld();
        if (tick == 25) {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) { world.getChunk(x, z); world.setChunkForced(x, z, true); }
            y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0);
            var factory = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",
                    MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
            factory.setAccessible(true);
            player = (ServerPlayerEntity)factory.invoke(null, server, UUID.randomUUID(), "Penned", px, (double)y, pz);
            player.onTeleportationDone();
            boss = new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN, world);
            // Passive until the player's join protection has passed, so it has not walked anywhere.
            boss.setPosition(.5, y, .5); boss.stopReview(); world.spawnEntity(boss);
        }
        if (boss == null) return;
        if (tick == 125) boss.startFight(); // After the player's join protection.
        dealt += Math.max(0, player.getMaxHealth() - player.getHealth()); player.setHealth(player.getMaxHealth());
        player.setVelocity(Vec3d.ZERO); player.setPosition(px, y, pz);
        long now = world.getTime();
        Object active = get("active");
        int standing = spikes(world);
        raised = Math.max(raised, standing);
        switch (stage) {
            case 0 -> {
                if (tick != 160) break;
                require((boolean)get("engaged"), "The fight never started");
                // Awake, rested, and with only the Scar free: the chooser has to take it.
                open();
                for (var attack : GuardianCombatRules.Attack.values()) if (attack != SCAR) ready().put(attack, now + 100000);
                stage = 1;
            }
            case 1 -> {
                require(tick < 170, "It did not score the floor at a player 15 blocks away: active=" + active);
                if (active != SCAR) break;
                scarStart = (long)get("started");
                // Free everything for the follow-up; the pen must still narrow it to the leap.
                ready().keySet().removeIf(attack -> attack != SCAR);
                dealt = 0; stage = 2;
            }
            case 2 -> {
                int age = (int)(now - scarStart);
                if (age == GuardianScarRules.PLANT + 1) require(standing == 0, "Spikes came up with the cracks");
                if (age == GuardianScarRules.ERUPT + GuardianScarRules.RIPPLE + 2) {
                    // 23 a wall on a flat floor; a hosted run's ordinary terrain can cost a few.
                    require(standing >= 36, "Two walls past a target 15 blocks away raised only " + standing + " spikes");
                    require(dealt == 0, "The pen hurt a target standing in the middle of it: " + dealt);
                    var spike = world.getEntitiesByClass(GuardianSpikeEntity.class, area(), e -> !e.isRemoved()).get(0);
                    require(spike.isCollidable(player) && !spike.isCollidable(boss), "A spike is solid to the wrong bodies");
                    Box across = player.getBoundingBox().stretch(6, 0, 0);
                    require(!world.getEntityCollisions(player, across).isEmpty(), "Nothing stops a player walking out sideways");
                    require(world.getEntityCollisions(boss, across).isEmpty(), "Its own spikes would stop the Guardian");
                    require(world.getEntityCollisions(player, player.getBoundingBox().stretch(0, 0, 9)).isEmpty(), "The far end of the pen is closed");
                    report.append("The Scar raised ").append(standing).append(" spikes round a standing target without hurting them; they stop players and not the Guardian, and the pen's far end is open.\n");
                    px = .5 + GuardianScarRules.HALF_WIDTH; // Into the wall.
                }
                if (age == GuardianScarRules.ERUPT + GuardianScarRules.RIPPLE + 8) {
                    near(dealt, GuardianScarRules.TOUCH_DAMAGE, "Standing in a spike for six ticks");
                    report.append("Touching a standing spike costs ").append(dealt).append(" once, not every tick.\n");
                    px = .5; dealt = 0;
                }
                if (age < GuardianScarRules.DURATION) { require(active == SCAR, "The Scar ended early at tick " + age + ": " + active); break; }
                require(age <= GuardianScarRules.DURATION + 3, "Nothing followed the Scar: active=" + active);
                if (active == null) break;
                require(active == LEAP, "It followed the Scar into the pen with " + active + ", not the leap");
                require(player.getUuid().equals(get("target")), "The follow-up was not for the penned player");
                leapStart = (long)get("started");
                require(leapStart - scarStart <= GuardianScarRules.DURATION + 2, "It walked before following the Scar");
                stage = 3;
            }
            case 3 -> {
                if (now - leapStart != GuardianLeapRules.LAND + 2) break;
                require(dealt >= GuardianLeapRules.DAMAGE, "The leap into the pen missed a target standing in it: " + dealt);
                require(standing > 0 && get("pen") != null, "The walls fell before the leap landed");
                report.append("It leapt straight after the Scar and landed on the penned target for ").append(dealt)
                        .append(" with ").append(standing).append(" spikes still standing.\n");
                for (var attack : GuardianCombatRules.Attack.values()) ready().put(attack, now + 100000);
                stage = 4;
            }
            case 4 -> {
                long fall = scarStart + GuardianScarRules.ERUPT + GuardianScarRules.LIFE;
                if (now == fall - GuardianSpikeEntity.CRUMBLE - 2) require(standing > 0, "The walls fell early");
                if (now != fall + 2) break;
                require(standing == 0 && get("pen") == null, "Spikes outlived their six seconds: " + standing);
                report.append("The walls fell on time.\n");
                // The Sweep: the target stands 4 blocks behind it, and two slams running leave nothing else to weigh.
                open();
                ready().clear();
                set("last", SLAM); set("before", SLAM);
                boss.setPosition(.5, y, .5); boss.setVelocity(Vec3d.ZERO); boss.setOnGround(true);
                boss.setYaw(0); boss.setBodyYaw(0); boss.setHeadYaw(0);
                pz = -3.5; player.setPosition(px, y, pz); dealt = 0;
                stage = 5;
            }
            case 5 -> {
                if (active == null) { require(now < scarStart + GuardianScarRules.ERUPT + GuardianScarRules.LIFE + 12, "It ignored a player behind it"); break; }
                require(active == SWEEP, "A player behind it drew " + active + ", not the Sweep");
                sweepStart = (long)get("started");
                stage = 6;
            }
            default -> {
                int age = (int)(now - sweepStart);
                require(Math.abs(MathHelper.wrapDegrees(boss.getBodyYaw())) < 1, "It turned to face the player behind it: " + boss.getBodyYaw());
                if (age == GuardianSweepRules.IMPACT - 1) require(dealt == 0, "The Sweep landed before its blow");
                if (age != GuardianSweepRules.IMPACT + 1) break;
                near(dealt, GuardianSweepRules.DAMAGE, "The Sweep on a player behind it");
                report.append("The Sweep struck a player 4 blocks behind it for ").append(dealt).append(" without turning.\n");
                boss.stopReview();
                require(spikes(world) == 0, "Stopping left spikes behind");
                Files.writeString(Path.of("SCAR_PASSED.txt"), report.toString());
                server.stop(false);
            }
        }
    }

    private Box area() { return new Box(-80, y - 5, -80, 80, y + 80, 80); }
    private int spikes(ServerWorld world) { return world.getEntitiesByClass(GuardianSpikeEntity.class, area(), e -> !e.isRemoved()).size(); }
    /** No awakening, no rest and no string in progress: its next tick is a fresh pick. */
    private void open() throws Exception {
        var interrupt = GuardianBossCombat.class.getDeclaredMethod("interruptAction"); interrupt.setAccessible(true); interrupt.invoke(combat());
        set("waking", 0); set("nextAction", 0L); set("chain", 0);
    }
    private GuardianBossCombat combat() throws Exception {
        var f = FracturedGuardianEntity.class.getDeclaredField("combat"); f.setAccessible(true); return (GuardianBossCombat)f.get(boss);
    }
    private Object get(String name) throws Exception {
        var f = GuardianBossCombat.class.getDeclaredField(name); f.setAccessible(true); return f.get(combat());
    }
    private void set(String name, Object value) throws Exception {
        var f = GuardianBossCombat.class.getDeclaredField(name); f.setAccessible(true); f.set(combat(), value);
    }
    @SuppressWarnings("unchecked") private Map<GuardianCombatRules.Attack, Long> ready() throws Exception {
        return (Map<GuardianCombatRules.Attack, Long>)get("ready");
    }
    private static void near(float value, float expected, String what) { require(Math.abs(value - expected) < .02, what + " dealt " + value + ", not " + expected); }
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
