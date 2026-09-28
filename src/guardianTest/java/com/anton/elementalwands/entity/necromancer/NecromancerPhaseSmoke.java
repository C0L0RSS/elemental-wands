package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SpellCombat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Real-server transformation, colossus attack and grab-lifecycle regression. */
public final class NecromancerPhaseSmoke implements ModInitializer {
    private int tick, mark, stage;
    private ServerPlayerEntity target, second;
    private NecromancerEntity boss;
    private Vec3d origin, pinned;
    private boolean caughtRush, thrownRush;

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("NECROMANCER_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void run(MinecraftServer server) throws Exception {
        int t = ++tick;
        ServerWorld w = server.getOverworld();
        if (t == 25) {
            for (int x = -2; x <= 3; x++) for (int z = -2; z <= 3; z++) { w.getChunk(x, z); w.setChunkForced(x, z, true); }
            for (int x = -25; x <= 45; x++) for (int z = -25; z <= 45; z++) w.setBlockState(new BlockPos(x, 99, z), Blocks.STONE.getDefaultState());
            // A low ceiling over the caster: the colossus must find open ground before growing.
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) w.setBlockState(new BlockPos(x, 102, z), Blocks.STONE.getDefaultState());
            boss = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            w.spawnEntity(boss);
            boss.stopFight();
            boss.setStage(NecromancerRules.Stage.DONE); // Sieges are covered by the server smoke; burst straight to phase two.
            origin = boss.getEntityPos();
            target = player(server, "PhaseTarget", 12.5, 100, .5);
            second = player(server, "PhaseSecond", .5, 100, 16.5);
            for (var p : List.of(target, second)) { p.setLoaded(true); p.onTeleportationDone(); p.setNoGravity(true); p.getHungerManager().setFoodLevel(10); }
        }
        if (boss == null) return;
        // Burst damage stops at half health and schedules the transformation.
        if (t == 30) {
            SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(target), 5000, target, WizardAffinity.FIRE);
            require(boss.isAlive() && Math.abs(boss.getHealth() - boss.getMaxHealth() * .5f) < .01, "Burst skipped phase two: " + boss.getHealth());
            require(boss.phasePending() && !boss.isColossus(), "Transformation not pending");
        }
        if (t == 60) {
            require(boss.getEntityPos().distanceTo(origin) > 2.5, "Caster grew under a low ceiling instead of relocating");
            require(boss.isTransforming(), "Transformation did not begin after relocation: " + boss.status());
            mark = t - (int)boss.getTransformTime(0); // Transformation start.
        }
        if (stage == 0 && mark > 0 && t == mark + 40) {
            float health = boss.getHealth();
            require(!boss.damage(w, w.getDamageSources().playerAttack(target), 20) && boss.getHealth() == health, "Transformation took damage");
            // Saved mid-transformation, the boss reloads as the finished colossus.
            var copy = reload(w);
            require(copy.isColossus() && !copy.isTransforming() && copy.getWidth() == NecromancerRules.COLOSSUS_WIDTH, "Mid-transform save did not resume as colossus");
        }
        if (stage == 0 && mark > 0 && t == mark + NecromancerRules.TRANSFORM_GROW + 2)
            require(boss.isColossus() && boss.isTransforming() && boss.getWidth() == NecromancerRules.COLOSSUS_WIDTH, "Body did not grow mid-transformation");
        if (stage == 0 && mark > 0 && t == mark + NecromancerRules.TRANSFORM_TICKS + 3) {
            require(boss.isColossus() && !boss.isTransforming(), "Transformation did not finish: " + boss.status());
            require(boss.getHeight() == NecromancerRules.COLOSSUS_HEIGHT, "Colossus hitbox wrong");
            require(reload(w).isColossus(), "Colossus did not persist");
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) w.setBlockState(new BlockPos(x, 102, z), Blocks.AIR.getDefaultState());
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            place(target, 4.5); heal(target);
            boss.testAction(target, Action.GRAB);
            mark = t; stage = 1;
        }
        // Grab: lift, then a capped slam.
        int g = t - mark;
        if (stage == 1) {
            if (g == 24) {
                require(boss.getGrabbed() == target.getId(), "Grab missed a player in reach");
                require(target.getY() > 101.5, "Grabbed player was not lifted: " + target.getY());
            }
            if (g == 50) {
                require(boss.getGrabbed() == -1, "Slam did not release");
                require(Math.abs(target.getHealth() - (20 - NecromancerRules.grabDamage(20))) < .01, "Slam damage outside its cap: " + target.getHealth());
                place(target, 4.5); heal(target);
                boss.testAction(target, Action.GRAB);
                mark = t; stage = 2;
            }
        }
        // Escape: team damage during the hold breaks the grip before the slam.
        if (stage == 2) {
            if (g == 20) {
                require(boss.getGrabbed() == target.getId(), "Second grab missed");
                require(SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(second), 30, second, WizardAffinity.FIRE), "Escape damage rejected");
                require(boss.getGrabbed() == -1, "Grip survived escape damage");
            }
            if (g == 52) {
                require(target.getHealth() == 20, "Escaped player was still slammed");
                place(target, 4.5); heal(target);
                boss.testAction(target, Action.GRAB);
                mark = t; stage = 3;
            }
        }
        // Stopping the encounter mid-hold releases the player and stops pinning them.
        if (stage == 3) {
            if (g == 20) {
                require(boss.getGrabbed() == target.getId(), "Third grab missed");
                boss.stopFight();
                require(boss.getGrabbed() == -1, "Stop kept the player held");
                target.setPosition(20.5, 104, 20.5);
                pinned = target.getEntityPos();
            }
            if (g == 24) {
                require(target.getEntityPos().distanceTo(pinned) < .01, "Released player still pinned to the hand");
                place(target, 4); heal(target); heal(second);
                second.setPosition(boss.getX(), 100, boss.getZ() - 4); // Behind.
                boss.testAction(target, Action.SWIPE);
                mark = t; stage = 4;
            }
        }
        // Swipe: hits in front, spares behind and a jumping player.
        if (stage == 4) {
            if (g == NecromancerRules.Action.SWIPE.impact - 2) { second.setPosition(boss.getX() + 1.5, 101.3, boss.getZ() + 4); }
            if (g == NecromancerRules.Action.SWIPE.impact + 3) {
                require(target.getHealth() < 20, "Swipe missed the player in front");
                require(second.getHealth() == 20, "Swipe hit a jumping player");
            }
            if (g == 40) {
                heal(target); heal(second);
                second.setPosition(boss.getX(), 100, boss.getZ() - 4);
                boss.testAction(second, Action.SWIPE);
                boss.setYaw(0); boss.setBodyYaw(0);
            }
            if (g == 40 + NecromancerRules.Action.SWIPE.impact + 3) {
                require(second.getHealth() < 20, "Swipe did not turn to its target");
                heal(target); heal(second);
                boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
                target.setPosition(.5, 100, 14.5); second.setPosition(20.5, 100, 20.5);
                boss.testAction(target, Action.RUSH);
                mark = t; stage = 5;
            }
        }
        // Grounded rush: contact starts the grip, team damage cannot break it, bite then throw.
        if (stage == 5) {
            require(Math.abs(boss.getY() - 100) < .1, "Rush became airborne");
            if (boss.getGrabbed() == target.getId() && !caughtRush) {
                caughtRush = true;
                boss.damage(w, w.getDamageSources().playerAttack(second), 30);
                require(boss.getGrabbed() == target.getId(), "Team damage interrupted rush grip");
            }
            if (caughtRush && boss.getGrabbed() == -1 && target.getVelocity().horizontalLength() > 1) thrownRush = true;
            if (g == 95) {
                require(caughtRush && thrownRush, "Rush did not grab then throw");
                require(target.getHealth() == 20 - NecromancerRules.RUSH_DAMAGE, "Rush bite did not hit exactly once: " + target.getHealth());
                boss.stopFight(); boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
                target.setPosition(.5, 100, 14.5); heal(target);
                boss.testAction(target, Action.RUSH);
                mark = t; stage = 7;
            }
        }
        // Sidestep after commitment: limited steering cannot snap onto a player behind it.
        if (stage == 7) {
            if (g == NecromancerRules.RUSH_WARNING + 1) target.setPosition(10.5, 100, -4.5); // After the aim locks.
            require(boss.getGrabbed() == -1, "Dodged rush still grabbed");
            if (g == 60) {
                require(target.getHealth() == 20, "Missed rush dealt damage");
                boss.stopFight(); boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
                target.setPosition(.5, 100, 14.5); heal(target);
                for (int x = -5; x <= 5; x++) for (int y = 100; y < 107; y++) w.setBlockState(new BlockPos(x, y, 6), Blocks.STONE.getDefaultState());
                boss.testAction(target, Action.RUSH); mark = t; stage = 8;
            }
        }
        if (stage == 8 && g == 65) {
            require(boss.getZ() < 6 && boss.getGrabbed() == -1 && target.getHealth() == 20, "Rush crossed or grabbed through cover");
            for (int x = -5; x <= 5; x++) for (int y = 100; y < 107; y++) w.setBlockState(new BlockPos(x, y, 6), Blocks.AIR.getDefaultState());
            boss.stopFight(); boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            target.setPosition(.5, 100, 13.5); second.setPosition(7.5, 100, 15.5); heal(target); heal(second);
            armor(target, true); // The combo is sized for the playtest's iron armor.
            boss.testAction(target, Action.HANDS); mark = t; stage = 9;
            caughtRush = thrownRush = false;
        }
        if (stage == 9) {
            if (g == 20) require(w.getEntitiesByClass(GraspingHandEntity.class, boss.getBoundingBox().expand(30), e -> true).size()
                    == 2 * NecromancerRules.handsModels(NecromancerRules.handsRadius(true)), "Hands models missing");
            if (g == 33) require(boss.status().contains("rush"), "Hands catch did not immediately start rush");
            if (boss.getGrabbed() == target.getId()) caughtRush = true;
            require(boss.getGrabbed() != second.getId(), "Hands chose the farther trapped player");
            if (caughtRush && boss.getGrabbed() == -1 && target.getVelocity().horizontalLength() > 1) thrownRush = true;
            if (g == 120) {
                // Full iron (15 armor): hands 10 → 6.0, bite 14 → 9.52. It hurts a lot and leaves the player standing.
                require(caughtRush && thrownRush && Math.abs(target.getHealth() - (20 - 6f - 9.52f)) < .05, "Hands + bite combo failed: " + target.getHealth());
                require(second.getHealth() == 20 - NecromancerRules.HANDS_DAMAGE, "Secondary trapped player got bitten");
                armor(target, false);
                require(w.getEntitiesByClass(GraspingHandEntity.class, boss.getBoundingBox().expand(40), e -> true).isEmpty(), "Hands visuals did not expire");
                boss.stopFight(); boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
                target.setPosition(.5, 100, 13.5); heal(target); second.setPosition(30, 100, 30);
                boss.testAction(target, Action.HANDS); mark = t; stage = 10;
            }
        }
        if (stage == 10) {
            if (g == 15) target.setPosition(12.5, 100, -4.5);
            if (g == 40) {
                require(target.getHealth() == 20 && !boss.status().contains("rush"), "Escaped hands triggered combo");
                boss.stopFight(); boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
                target.setPosition(.5, 100, 10.5); heal(target);
                boss.testAction(target, Action.RUSH); mark = t; stage = 11;
            }
        }
        if (stage == 11 && boss.getGrabbed() == target.getId()) {
            boss.stopFight(); require(boss.getGrabbed() == -1, "Cancel retained rush victim");
            target.setPosition(20.5, 104, 20.5); pinned = target.getEntityPos(); mark = t; stage = 12;
        }
        if (stage == 11) require(g < 80, "Cancellation test never grabbed");
        if (stage == 12 && g == 5) {
            require(target.getEntityPos().distanceTo(pinned) < .01 && target.getHealth() == 20, "Cancelled rush still pinned or bit");
            Files.writeString(Path.of("NECROMANCER_PASSED.txt"), "Phase two passed: transformation and save, original grab/slam and teammate rescue, swipe; grounded rush, single bite, throw, no team interrupt, sidestep, wall collision, hands visuals and nearest caught target combo against iron armor, escape, expiry and cancellation.\n");
            server.stop(false);
        }
    }

    private NecromancerEntity reload(ServerWorld w) {
        var out = NbtWriteView.create(ErrorReporter.EMPTY, w.getRegistryManager());
        boss.writeData(out);
        var copy = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
        copy.readData(NbtReadView.create(ErrorReporter.EMPTY, w.getRegistryManager(), out.getNbt()));
        return copy;
    }

    /** Stands a player directly in front of the boss at the given distance. */
    private void place(ServerPlayerEntity player, double distance) {
        player.setPosition(boss.getX(), 100, boss.getZ() + distance);
        player.setVelocity(Vec3d.ZERO);
    }
    /** Full iron is 15 armor points. Fixture players never tick equipment, so the attribute is set directly. */
    private static void armor(ServerPlayerEntity player, boolean on) {
        player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ARMOR).setBaseValue(on ? 15 : 0);
    }
    private static void heal(ServerPlayerEntity player) { player.setHealth(20); player.timeUntilRegen = 0; player.clearStatusEffects(); player.setVelocity(Vec3d.ZERO); }
    private static ServerPlayerEntity player(MinecraftServer s, String name, double x, double y, double z) throws Exception {
        var f = com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player", MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        f.setAccessible(true);
        return (ServerPlayerEntity)f.invoke(null, s, UUID.randomUUID(), name, x, y, z);
    }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
