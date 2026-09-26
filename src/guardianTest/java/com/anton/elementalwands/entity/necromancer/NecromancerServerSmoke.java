package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.WandBoss;
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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Real-server spell, army and lifecycle regression for the Hollow Necromancer. */
public final class NecromancerServerSmoke implements ModInitializer {
    private static final Box AREA = new Box(-30, 90, -30, 50, 120, 50);
    private int tick;
    private ServerPlayerEntity target, second;
    private NecromancerEntity boss;
    private float bossBefore;
    private List<UUID> firstRaised = List.of();

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
            w.setTimeOfDay(6000); // Noon: fodder must not burn.
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
        if (t == 150) {
            require(target.getHealth() < 20 && target.getHealth() > 0, "Open soul bolt missed: " + target.getHealth());
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
        // Hands: a ring under each player; stepping out avoids it, standing still is rooted.
        if (t == 225) second.setPosition(second.getX() + 4, 100, second.getZ());
        if (t == 252) {
            require(target.getHealth() < 20, "Grasping hands missed a player standing still");
            var root = target.getStatusEffect(StatusEffects.SLOWNESS);
            require(root != null && root.getAmplifier() >= 6, "Grasping hands did not root");
            require(second.getHealth() == 20, "Grasping hands hit a player who stepped out");
            heal(target); heal(second);
            boss.combat().testAction(target, Action.RAISE);
        }
        // Raise: Hollow undead claw out of the floor, then fight, without burning or hurting their caster.
        if (t == 276) {
            var minions = minions(w);
            require(!minions.isEmpty() && minions.size() <= NecromancerRules.RAISE_PER_CAST, "Raise count wrong: " + minions.size());
            require(minions.stream().anyMatch(m -> m instanceof com.anton.elementalwands.entity.undead.HollowCrawlerEntity)
                    && minions.stream().noneMatch(m -> m instanceof com.anton.elementalwands.entity.undead.HollowBruteEntity),
                    "A new army did not start with a crawler (and no brute)");
            require(minions.stream().allMatch(m -> Math.abs(m.getY() - 100) < .05 && NecromancerMinion.rising(m)), "Minions did not rise on the floor");
            firstRaised = minions.stream().map(MobEntity::getUuid).toList();
        }
        if (t == 305) {
            var minions = minions(w);
            require(minions.stream().allMatch(m -> m.isTeammate(boss) && boss.isTeammate(m)), "Minions are not on the caster's side");
            float health = boss.getHealth();
            require(!boss.damage(w, w.getDamageSources().mobAttack(minions.getFirst()), 6) && boss.getHealth() == health, "Minion damaged its caster");
            boss.combat().testAction(target, Action.RAISE);
        }
        if (t == 335) boss.combat().testAction(target, Action.RAISE);
        if (t == 352) require(firstRaised.stream().map(w::getEntity).allMatch(m -> m instanceof MobEntity mob && mob.isAlive()
                && !NecromancerMinion.rising(mob) && !mob.isAiDisabled() && Math.abs(mob.getY() - 100) < .05), "Minions did not finish rising");
        if (t == 385) {
            var minions = minions(w);
            require(minions.size() == NecromancerRules.minionCap(false, 2), "Army cap not respected: " + minions.size());
            require(minions.stream().noneMatch(MobEntity::isOnFire), "Fodder burned in daylight");
            boss.stopFight();
        }
        if (t == 387) {
            require(minions(w).isEmpty(), "Stopping the encounter left minions behind");
            heal(target);
            target.setPosition(2.5, 100, .5);
            boss.testAction(target, Action.BLINK);
        }
        // Blink: escapes a close player within the leash and leaves a curse on the old spot.
        if (t == 400) {
            double distance = boss.distanceTo(target);
            require(distance >= NecromancerRules.BLINK_MIN - 1.5, "Blink did not escape: " + distance);
            require(boss.getEntityPos().subtract(.5, 100, .5).horizontalLength() <= NecromancerRules.MOVEMENT_RANGE + 4 + NecromancerRules.BLINK_MAX, "Blink left the leash");
        }
        if (t == 412) require(target.hasStatusEffect(StatusEffects.WITHER) && target.hasStatusEffect(StatusEffects.SLOWNESS), "Curse patch missing");
        // Player spells damage the boss as any WandBoss.
        if (t == 415) {
            target.clearStatusEffects(); heal(target);
            float health = boss.getHealth();
            require(SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(target), 10, target, WizardAffinity.FIRE), "Wand damage rejected");
            require(boss.getHealth() < health, "Wand damage had no effect");
            boss.startFight();
        }
        if (t == 440) require(boss.status().contains("fighting"), "Fight did not engage: " + boss.status());
        if (t == 520) {
            boss.stopFight();
            require(minions(w).isEmpty() && count(w, SoulBoltEntity.class) == 0, "Stop left encounter entities");
            Files.writeString(Path.of("NECROMANCER_PASSED.txt"), "Hollow Necromancer passed: cover stops soul bolts; open volley hits; drain damages, heals within its cap, is tracked for clients and breaks on lost sight; grasping hands root players who stay and spare players who step out; crawlers claw out of the floor, finish rising with AI, share the caster's side, cannot hurt it, respect the army cap and do not burn at noon; stop dissolves the army; blink escapes within the leash and leaves a Wither/Slowness curse; wand damage applies; fight mode engages.\n");
            server.stop(false);
        }
    }

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
