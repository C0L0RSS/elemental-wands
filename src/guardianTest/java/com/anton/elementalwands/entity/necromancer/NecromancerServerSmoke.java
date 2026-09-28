package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Stage;
import com.anton.elementalwands.entity.undead.HollowArcherEntity;
import com.anton.elementalwands.entity.undead.HollowBruteEntity;
import com.anton.elementalwands.entity.undead.HollowCrawlerEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SpellCombat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Real-server spell, wave, siege and lifecycle regression for the Hollow Necromancer. */
public final class NecromancerServerSmoke implements ModInitializer {
    private static final Box AREA = new Box(-30, 90, -30, 50, 130, 50);
    private int tick, siege, since;
    private ServerPlayerEntity target, second;
    private NecromancerEntity boss;
    private float bossBefore;
    private List<UUID> firstRaised = List.of();
    private boolean sniped;

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
            w.setTimeOfDay(6000); // Noon: the undead must not burn.
            boss = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            w.spawnEntity(boss);
            boss.stopFight();
            target = player(server, "NecroTarget", 10.5, 100, .5);
            second = player(server, "NecroSecond", .5, 100, 20.5);
            for (var p : List.of(target, second)) { p.setLoaded(true); p.onTeleportationDone(); p.setNoGravity(true); p.getHungerManager().setFoodLevel(10); }
            require(boss instanceof WandBoss, "Necromancer is not a WandBoss");
            wall(5, Blocks.STONE);
            boss.testAction(target, Action.BOLT);
        }
        if (boss == null) return;
        // Cover: every bolt of the volley stops at the wall.
        if (t == 90) {
            require(target.getHealth() == 20, "Soul bolt passed through cover: " + target.getHealth());
            require(count(w, SoulBoltEntity.class) == 0, "Blocked bolts lingered");
            wall(5, Blocks.AIR);
            boss.testAction(target, Action.BOLT);
        }
        // Every skull of an open volley counts: hit immunity no longer swallows the middle bite.
        if (t == 150) {
            require(Math.abs(target.getHealth() - (20 - NecromancerRules.BOLT_COUNT * NecromancerRules.BOLT_DAMAGE)) < .01,
                    "Open volley did not land every skull: " + target.getHealth());
            require(count(w, SoulBoltEntity.class) == 0, "Bolts outlived their volley");
            heal(target); boss.setHealth(400); bossBefore = boss.getHealth();
            boss.testAction(target, Action.DRAIN);
        }
        // Drain: damages and heals up to its cap, and a wall breaks it.
        if (t == 190) {
            require(target.getHealth() < 20, "Drain dealt no damage");
            require(boss.getHealth() > bossBefore, "Drain did not heal");
            require(boss.getHealth() - bossBefore <= boss.getMaxHealth() * NecromancerRules.DRAIN_HEAL_SHARE + .01, "Drain exceeded heal cap");
            require(boss.getDrainTarget() == target.getId(), "Drain tether not tracked for clients");
            wall(5, Blocks.STONE);
        }
        if (t == 200) {
            require(boss.getDrainTarget() == -1 && boss.status().contains("idle"), "Blocked drain kept channeling: " + boss.status());
            bossBefore = boss.getHealth();
        }
        if (t == 215) {
            require(boss.getHealth() == bossBefore, "Broken drain kept healing");
            wall(5, Blocks.AIR);
            heal(target); heal(second);
            boss.testAction(target, Action.HANDS);
        }
        // Hands: a wide ring under each player; stepping well out avoids it, standing still is rooted
        // and followed at once by an ambush behind the caught player.
        if (t == 225) second.setPosition(second.getX() + NecromancerRules.HANDS_RADIUS + 2, 100, second.getZ());
        if (t == 252) {
            require(Math.abs(target.getHealth() - (20 - NecromancerRules.HANDS_DAMAGE)) < .01, "Grasping hands missed a player standing still: " + target.getHealth());
            var root = target.getStatusEffect(StatusEffects.SLOWNESS);
            require(root != null && root.getAmplifier() >= 6, "Grasping hands did not root");
            require(second.getHealth() == 20, "Grasping hands hit a player who stepped out");
            require(boss.status().contains("ambush"), "A root was not followed by an ambush: " + boss.status());
            target.setHealth(20); target.timeUntilRegen = 0;
        }
        if (t == 276) {
            require(Math.abs(target.getHealth() - (20 - NecromancerRules.AMBUSH_DAMAGE)) < .01, "Ambush burst missed the rooted player: " + target.getHealth());
            require(boss.distanceTo(target) < NecromancerRules.AMBUSH_RADIUS + 2.5 && boss.getZ() < target.getZ(), "Ambush did not appear behind the player");
            require(second.getHealth() == 20, "Ambush burst reached a distant player");
            heal(target); heal(second);
            second.setPosition(.5, 100, 20.5);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            boss.testWave(target, 1);
        }
        // Siege wave 1 for two players: five crawlers claw out of the floor away from the players.
        if (t == 280) {
            var minions = minions(w);
            require(minions.size() == NecromancerRules.wave(1, 2).total() && minions.stream().allMatch(m -> m instanceof HollowCrawlerEntity),
                    "Wave 1 composition wrong: " + minions.size());
            require(minions.stream().allMatch(m -> Math.abs(m.getY() - 100) < .05 && NecromancerMinion.rising(m)), "Wave bodies did not rise on the floor");
            require(minions.stream().allMatch(m -> m.distanceTo(target) >= NecromancerRules.WAVE_CLEAR - .01 && m.distanceTo(second) >= NecromancerRules.WAVE_CLEAR - .01),
                    "A wave body rose beside a player");
            firstRaised = minions.stream().map(MobEntity::getUuid).toList();
        }
        if (t == 300) {
            var minions = minions(w);
            require(minions.stream().allMatch(m -> m.isTeammate(boss) && boss.isTeammate(m)), "Minions are not on the caster's side");
            float health = boss.getHealth();
            require(!boss.damage(w, w.getDamageSources().mobAttack(minions.getFirst()), 6) && boss.getHealth() == health, "Minion damaged its caster");
        }
        if (t == 330) require(firstRaised.stream().map(w::getEntity).allMatch(m -> m instanceof MobEntity mob && mob.isAlive()
                && !NecromancerMinion.rising(mob) && !mob.isAiDisabled() && Math.abs(mob.getY() - 100) < .05 && !mob.isOnFire()),
                "Wave bodies did not finish rising, or burned at noon");
        if (t == 335) boss.testWave(target, 3);
        if (t == 345) {
            var minions = minions(w);
            require(minions.stream().noneMatch(m -> firstRaised.contains(m.getUuid())), "A new rehearsal kept the previous wave");
            var expected = NecromancerRules.wave(3, 2);
            require(minions.stream().filter(m -> m instanceof HollowCrawlerEntity).count() == expected.crawlers()
                    && minions.stream().filter(m -> m instanceof HollowArcherEntity).count() == expected.archers()
                    && minions.stream().filter(m -> m instanceof HollowBruteEntity).count() == expected.brutes(), "Wave 3 composition wrong: " + minions.size());
            boss.stopFight();
        }
        if (t == 347) {
            require(minions(w).isEmpty(), "Stopping the encounter left minions behind");
            heal(target);
            target.setPosition(2.5, 100, .5);
            boss.testAction(target, Action.BLINK);
        }
        // Blink: escapes a close player within the leash and leaves a curse on the old spot.
        if (t == 360) {
            double distance = boss.distanceTo(target);
            require(distance >= NecromancerRules.BLINK_MIN - 1.5, "Blink did not escape: " + distance);
            require(boss.getEntityPos().subtract(.5, 100, .5).horizontalLength() <= NecromancerRules.BLINK_LEASH + NecromancerRules.BLINK_MAX, "Blink left the leash");
        }
        if (t == 372) require(target.hasStatusEffect(StatusEffects.WITHER) && target.hasStatusEffect(StatusEffects.SLOWNESS), "Curse patch missing");
        // Shift: a flare marks a new spot away from everyone, then it appears there.
        if (t == 375) {
            target.clearStatusEffects(); heal(target);
            target.setPosition(10.5, 100, .5);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            boss.testAction(target, Action.SHIFT);
        }
        if (t == 375 + Action.SHIFT.impact + 3) {
            Vec3d at = boss.getEntityPos();
            require(at.distanceTo(new Vec3d(.5, 100, .5)) > 5, "Shift did not move the caster");
            require(boss.distanceTo(target) >= NecromancerRules.SHIFT_CLEAR - .5 && boss.distanceTo(second) >= NecromancerRules.SHIFT_CLEAR - .5, "Shift landed beside a player");
            require(at.subtract(.5, 100, .5).horizontalLength() <= NecromancerRules.BLINK_LEASH + .01, "Shift left the leash");
        }
        // Player spells damage the boss as any WandBoss.
        if (t == 395) {
            boss.setHealth(boss.getMaxHealth()); // The drain check left it below the first duel's gate.
            float health = boss.getHealth();
            require(SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(target), 10, target, WizardAffinity.FIRE), "Wand damage rejected");
            require(boss.getHealth() < health, "Wand damage had no effect");
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            target.setPosition(10.5, 100, .5); second.setPosition(.5, 100, 14.5);
            heal(target); heal(second);
            boss.startFight();
            siege = 1; since = t;
        }
        if (siege > 0) siegeFlow(w, t, t - since);
    }

    /** The full robed fight in fight mode: two sieges with their waves, the exposed crash and the transformation. */
    private void siegeFlow(ServerWorld w, int t, int s) throws Exception {
        switch (siege) {
            case 1 -> { // Engage, then burst: the first duel holds at 75% and starts the siege.
                if (s == 25) {
                    require(boss.status().contains("fighting"), "Fight did not engage: " + boss.status());
                    SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(target), 5000, target, WizardAffinity.FIRE);
                    require(Math.abs(boss.getHealth() - boss.getMaxHealth() * .75f) < .01, "Burst skipped the first siege: " + boss.getHealth());
                }
                if (s == 30) {
                    require(boss.stage() == Stage.SIEGE_1, "Siege did not start: " + boss.status());
                    float health = boss.getHealth();
                    require(!boss.damage(w, w.getDamageSources().playerAttack(target), 20) && boss.getHealth() == health, "Sieging caster took damage");
                }
                if (s == 60) {
                    require(boss.getY() > 105 && boss.hasNoGravity(), "Caster did not take its perch: " + boss.getEntityPos());
                    second.setPosition(.5, 106, 14.5); // Hovers out of the army's reach.
                    next();
                }
            }
            case 2 -> { // Wave 1 (duo), a perch bolt for the hovering player, then clear it.
                var minions = minions(w);
                if (target.getHealth() < 12) heal(target); // Risen crawlers reach the grounded player meanwhile.
                if (!sniped && second.getHealth() < 20) sniped = true;
                if (s == 50) require(minions.size() == NecromancerRules.wave(1, 2).total(), "Siege wave 1 size wrong: " + minions.size());
                if (s == 50) require(minions.stream().allMatch(m -> m.squaredDistanceTo(boss) > 1), "Wave bodies rose on the perch");
                if (s >= 110 && sniped) { minions.forEach(m -> m.kill(w)); heal(second); second.setPosition(.5, 100, 14.5); next(); }
                require(s < 110 + 60, "Hovering player was never shot from the perch");
            }
            case 3 -> { // Wave 2 follows the breather: archers join; clear it and the caster crashes down.
                var minions = minions(w);
                if (minions.size() == NecromancerRules.wave(2, 2).total() && s > NecromancerRules.WAVE_BREATHER) {
                    require(minions.stream().filter(m -> m instanceof HollowArcherEntity).count() == NecromancerRules.wave(2, 2).archers(), "Wave 2 archers missing");
                    minions.forEach(m -> m.kill(w));
                    next();
                }
                require(s < NecromancerRules.WAVE_BREATHER + 120, "Siege wave 2 never filled: " + minions.size() + ", " + boss.status());
            }
            case 4 -> { // The crash: back on the ground in the second duel, exposed to extra damage.
                if (boss.stage() == Stage.DUEL_B && boss.isOnGround() && !boss.hasNoGravity()) {
                    require(Math.abs(boss.getY() - 100) < .1, "Caster did not land on the floor: " + boss.getY());
                    float health = boss.getHealth();
                    require(boss.damage(w, w.getDamageSources().playerAttack(target), 10), "Exposed damage rejected");
                    require(Math.abs(health - boss.getHealth() - 10 * NecromancerRules.EXPOSED_MULTIPLIER) < .01, "Exposed caster took no extra damage: " + (health - boss.getHealth()));
                    next();
                }
                require(s < 120, "Caster never crashed down: " + boss.status());
            }
            case 5 -> { // Burst: the second duel holds at half health and starts the second siege.
                if (s == 5) {
                    SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(second), 5000, second, WizardAffinity.FIRE);
                    require(Math.abs(boss.getHealth() - boss.getMaxHealth() * .5f) < .01 && !boss.phasePending(), "Burst skipped the second siege: " + boss.getHealth());
                }
                if (s == 10) { require(boss.stage() == Stage.SIEGE_2, "Second siege did not start: " + boss.status()); next(); }
            }
            case 6, 7 -> { // Waves 3 and 4 bring the brutes; clearing the last one ends the siege.
                int number = siege == 6 ? 3 : 4;
                var expected = NecromancerRules.wave(number, 2);
                var minions = minions(w);
                if (minions.size() == expected.total()) {
                    require(minions.stream().filter(m -> m instanceof HollowBruteEntity).count() == expected.brutes(), "Wave " + number + " brutes wrong");
                    minions.forEach(m -> m.kill(w));
                    next();
                }
                require(s < 200, "Siege wave " + number + " never filled: " + minions.size() + ", " + boss.status());
            }
            case 8 -> { // The second crash hands over to the transformation.
                if (boss.isTransforming()) {
                    require(boss.stage() == Stage.DONE && minions(w).isEmpty(), "Transformation began with the siege unfinished");
                    boss.stopFight();
                    require(!boss.hasNoGravity(), "Stopped caster kept its perch");
                    Files.writeString(Path.of("NECROMANCER_PASSED.txt"), "Hollow Necromancer passed: cover stops soul bolts; every skull of an open volley lands; drain damages, heals within its cap, is tracked for clients and breaks on lost sight; wide grasping hands root a player who stays, spare one who steps out and are followed by an ambush burst from behind; siege waves rise on the floor away from players with the duo compositions, share the caster's side, cannot hurt it, finish rising with AI, do not burn at noon and dissolve on stop; blink escapes within the leash and leaves a curse; shift repositions away from everyone; wand damage applies; the fight gates at 75% into a shielded perched siege whose hovering target draws a perch bolt, waves 1-2 advance and a cleared siege crashes the caster down exposed; half health starts the second siege whose waves 3-4 bring brutes, and clearing it begins the transformation.\n");
                    w.getServer().stop(false);
                }
                require(s < 200, "Second siege never ended in the transformation: " + boss.status());
            }
            default -> {}
        }
    }

    private void next() { siege++; since = tick; }

    private void wall(int x, net.minecraft.block.Block block) {
        var w = (ServerWorld)boss.getEntityWorld();
        for (int y = 100; y <= 103; y++) for (int z = -3; z <= 3; z++) w.setBlockState(new BlockPos(x, y, z), block.getDefaultState());
    }
    private static void heal(ServerPlayerEntity player) { player.setHealth(20); player.timeUntilRegen = 0; player.clearStatusEffects(); }
    private static List<MobEntity> minions(ServerWorld w) { return w.getEntitiesByClass(MobEntity.class, AREA, e -> e instanceof NecromancerMinion && e.isAlive()); }
    private static int count(ServerWorld w, Class<? extends net.minecraft.entity.Entity> type) { return w.getEntitiesByClass(type, AREA, e -> !e.isRemoved()).size(); }
    private static ServerPlayerEntity player(MinecraftServer s, String name, double x, double y, double z) throws Exception {
        var f = com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player", MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        f.setAccessible(true);
        return (ServerPlayerEntity)f.invoke(null, s, UUID.randomUUID(), name, x, y, z);
    }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
