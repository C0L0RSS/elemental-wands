package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Stage;
import com.anton.elementalwands.entity.undead.HollowArcherEntity;
import com.anton.elementalwands.entity.undead.HollowBruteEntity;
import com.anton.elementalwands.entity.undead.HollowCrawlerEntity;
import com.anton.elementalwands.block.SoulGlowBlock;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.SoulGlow;
import com.anton.elementalwands.util.SpellCombat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Real-server spell, wave, siege and lifecycle regression for the Hollow Necromancer. */
public final class NecromancerServerSmoke implements ModInitializer {
    private static final Box AREA = new Box(-30, 90, -30, 50, 130, 50);
    private static final List<BlockPos> BRAZIERS = List.of(new BlockPos(20, 100, 3), new BlockPos(-12, 100, -30));
    /** A soul campfire already out when a scene begins: no intro may light it. */
    private static final BlockPos UNLIT = new BlockPos(-6, 100, -16);
    private int tick, siege, since, intro = -1;
    private ServerPlayerEntity target, second;
    private NecromancerEntity boss, spare;
    private float bossBefore;
    private List<UUID> firstRaised = List.of();
    private boolean sniped, rained, litBolt, litBall, litMarker;

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
            // A glow nothing refreshes, as a crash or reload would leave one, clears itself.
            w.setBlockState(new BlockPos(-5, 100, -5), ModSpellBlocks.SOUL_GLOW.getDefaultState());
        }
        if (boss == null) return;
        if (t == 25 + SoulGlow.CHECK + 1) require(w.getBlockState(new BlockPos(-5, 100, -5)).isAir(), "An orphaned soul light did not clear itself");
        if (t > 25 && t < 90 && !litBolt) for (var bolt : w.getEntitiesByClass(SoulBoltEntity.class, AREA, e -> !e.isRemoved()))
            if (glows(w).stream().anyMatch(p -> p.toCenterPos().squaredDistanceTo(bolt.getBoundingBox().getCenter()) < 3)) litBolt = true;
        // Cover: every bolt of the volley stops at the wall.
        if (t == 90) {
            require(litBolt, "Soul bolts carried no light");
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
            require(minions.stream().allMatch(m -> m.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) > m.getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED) + 1e-4),
                    "Siege bodies were not quickened");
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
        // Soul Fire Rain: a marker on each player; the one who stays is blasted, the one who leaves is spared.
        if (t == 395) {
            require(glows(w).isEmpty(), "Soul light left behind by earlier spells: " + glows(w));
            w.setBlockState(new BlockPos(10, 100, 0), Blocks.COBWEB.getDefaultState()); // Soul light must work around it, never replace it.
            target.setPosition(10.5, 100, .5); second.setPosition(.5, 100, 20.5); heal(target); heal(second);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            require(boss.testRain(target) == NecromancerRules.rainMarkers(2, Stage.SIEGE_1), "Rain volley size wrong");
            require(count(w, SoulFireballEntity.class) == NecromancerRules.rainMarkers(2, Stage.SIEGE_1), "Rain fireballs missing");
        }
        if (t == 400) second.setPosition(40.5, 100, 40.5);
        if (t == 395 + NecromancerRules.RAIN_WARNING + 3) {
            require(target.getHealth() <= 20 - NecromancerRules.RAIN_DAMAGE + .01 && target.getHealth() >= 20 - NecromancerRules.RAIN_DAMAGE - 1.01,
                    "Soul fireball missed the player under its marker: " + target.getHealth());
            require(second.getHealth() == 20, "Soul fireball hit a player who left its marker");
            require(count(w, SoulFireballEntity.class) == 0, "Soul fireballs outlived their landing");
            var flash = w.getBlockState(new BlockPos(10, 101, 0));
            require(flash.isOf(ModSpellBlocks.SOUL_GLOW) && flash.get(SoulGlowBlock.LEVEL) == NecromancerRules.GLOW_IMPACT, "No soul light flash over the blast: " + flash);
        }
        // Soul light rides every fireball and its marker, only ever in air, and all of it clears after the landing.
        if (t > 395 && t < 395 + NecromancerRules.RAIN_WARNING) {
            var lit = glows(w);
            require(lit.stream().allMatch(p -> p.getY() >= 100), "Soul light replaced the floor: " + lit);
            for (var ball : w.getEntitiesByClass(SoulFireballEntity.class, AREA, e -> !e.isRemoved())) {
                Vec3d at = ball.getEntityPos().add(0, .3, 0);
                if (lit.stream().anyMatch(p -> p.toCenterPos().squaredDistanceTo(at) < 3)) litBall = true;
                if (lit.stream().anyMatch(p -> p.toCenterPos().squaredDistanceTo(ball.target()) < 3)) litMarker = true;
            }
        }
        if (t == 395 + NecromancerRules.RAIN_WARNING + NecromancerRules.GLOW_IMPACT_TICKS + 6) {
            require(litBall && litMarker, "Soul Fire Rain carried no light: ball " + litBall + ", marker " + litMarker);
            require(glows(w).isEmpty(), "Soul light outlived the rain: " + glows(w));
            require(w.getBlockState(new BlockPos(10, 100, 0)).isOf(Blocks.COBWEB), "Soul light replaced a block under the marker");
            w.setBlockState(new BlockPos(10, 100, 0), Blocks.AIR.getDefaultState());
        }
        // The intro cinematic holds both players still and unhurt; the fight starts early only when both skip.
        if (t == 430) {
            heal(target); heal(second);
            target.setPosition(10.5, 100, .5); second.setPosition(.5, 100, 14.5);
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            for (BlockPos pos : BRAZIERS) w.setBlockState(pos, Blocks.SOUL_CAMPFIRE.getDefaultState());
            boss.beginIntro(List.of(target, second));
        }
        if (t == 433) {
            require(boss.inIntro() && !boss.isBossAggressive() && boss.isAiDisabled(), "Intro did not hold the boss");
            require(NecromancerIntro.watching(target) && NecromancerIntro.watching(second), "Intro did not hold the players");
            require(count(w, IntroZombieEntity.class) == 1, "Intro zombie missing");
            require(BRAZIERS.stream().noneMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "Intro did not put out the braziers");
            float health = boss.getHealth();
            require(!boss.damage(w, w.getDamageSources().playerAttack(target), 20) && boss.getHealth() == health, "Boss took damage in its intro");
            require(!target.damage(w, w.getDamageSources().mobAttack(boss), 6) && target.getHealth() == 20, "A watcher took damage in the intro");
            target.setPosition(14.5, 100, 4.5);
            NecromancerIntro.skip(target);
            second.fallDistance = 12; // As if caught mid-jump and put back while sinking.
        }
        if (t == 435) {
            require(target.getEntityPos().squaredDistanceTo(10.5, 100, .5) < .01, "A watcher walked away during the intro: " + target.getEntityPos());
            require(second.fallDistance == 0, "The intro hold let a watcher build fall distance: " + second.fallDistance);
        }
        if (t == 445) {
            require(boss.inIntro(), "One player's skip ended the intro for everyone");
            NecromancerIntro.skip(second);
        }
        if (t == 453) {
            require(!boss.inIntro() && boss.isBossAggressive() && !NecromancerIntro.watching(target), "A unanimous skip did not start the fight");
            require(count(w, IntroZombieEntity.class) == 0 && count(w, IntroSoulEntity.class) == 0, "Skipped intro left its actors behind");
            require(BRAZIERS.stream().allMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "Skipped intro left the braziers dark");
            boss.stopFight();
        }
        // A chunk unload or a portal skips remove(); the unloaded boss must still let its watchers go.
        if (t == 455) {
            w.setBlockState(UNLIT, Blocks.SOUL_CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false));
            spare = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
            spare.refreshPositionAndAngles(.5, 100, -8.5, 0, 0);
            w.spawnEntity(spare);
            spare.stopFight();
            spare.beginIntro(List.of(target));
        }
        if (t == 457) {
            require(NecromancerIntro.watching(target) && count(w, IntroZombieEntity.class) == 1, "The spare boss's intro did not start");
            require(BRAZIERS.stream().noneMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "The spare boss's intro did not put out the braziers");
            // Saved mid-scene, the boss comes back fighting rather than passive, frozen and stuck at its first gate.
            NecromancerEntity saved = reload(w, spare);
            require(saved.isBossAggressive() && !saved.isAiDisabled(), "A boss saved mid-intro reloaded passive or frozen");
            spare.setRemoved(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
        if (t == 458) {
            require(!NecromancerIntro.watching(target) && count(w, IntroZombieEntity.class) == 0, "An unloaded boss left its watcher held");
            // A cancelled scene relights what it put out, and never lights a soul campfire that was already out.
            require(BRAZIERS.stream().allMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "A cancelled intro left the braziers dark");
            require(!w.getBlockState(UNLIT).get(CampfireBlock.LIT), "An intro lit a soul campfire that was out");
            boss.beginIntro(List.of(target, second));
        }
        // An operator stop mid-scene is final: the scene must not start the fight when it would have ended.
        if (t == 459) {
            require(boss.inIntro() && NecromancerIntro.watching(target), "The stop check's intro did not start");
            boss.stopFight();
            require(!boss.inIntro() && boss.intro() == null && !boss.isBossAggressive() && !boss.isAiDisabled(), "Stop did not end the intro: " + boss.status());
            require(!NecromancerIntro.watching(target) && !NecromancerIntro.watching(second) && count(w, IntroZombieEntity.class) == 0,
                    "Stop mid-intro left its watchers or actors");
            require(BRAZIERS.stream().allMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)) && !w.getBlockState(UNLIT).get(CampfireBlock.LIT),
                    "Stop mid-intro did not restore the braziers");
            require(!reload(w, boss).isBossAggressive(), "A stopped boss did not stay passive after reload");
            w.setBlockState(UNLIT, Blocks.AIR.getDefaultState());
        }
        // Player spells damage the boss as any WandBoss.
        if (t == 460) {
            boss.setHealth(boss.getMaxHealth()); // The drain check left it below the first duel's gate.
            float health = boss.getHealth();
            require(SpellCombat.damage(boss, w, w.getDamageSources().playerAttack(target), 10, target, WizardAffinity.FIRE), "Wand damage rejected");
            require(boss.getHealth() < health, "Wand damage had no effect");
            boss.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            target.setPosition(10.5, 100, .5); second.setPosition(.5, 100, 14.5);
            heal(target); heal(second);
            for (BlockPos pos : BRAZIERS) w.setBlockState(pos, Blocks.SOUL_CAMPFIRE.getDefaultState());
            boss.beginIntro(List.of(target, second));
            intro = t;
        }
        if (intro >= 0) introFlow(w, t - intro);
        if (siege > 0) siegeFlow(w, t, t - since);
    }

    /** The whole intro plays out: the zombie's soul, the grave souls, the ring lighting the braziers, then the fight. */
    private void introFlow(ServerWorld w, int i) {
        NecromancerIntro scene = boss.intro();
        if (i == 30) require(scene != null && scene.victimAlive() && scene.watcherCount() == 2, "Intro lost its zombie or watchers");
        if (i == 40) require(Math.abs(MathHelper.wrapDegrees(boss.getBodyYaw() - NecromancerIntro.PULL_FACING)) < 1, "He does not face the zombie: " + boss.getBodyYaw());
        if (i == NecromancerIntro.PULL + 3) require(scene.soulTorn() && count(w, IntroSoulEntity.class) == 1, "The zombie's soul was not torn out");
        if (i == NecromancerIntro.CRUMBLE + 2) require(!scene.victimAlive() && count(w, IntroZombieEntity.class) == 0, "The emptied zombie did not crumble");
        if (i == NecromancerIntro.SOUL_ARRIVE + 2) require(!scene.soulTorn() && count(w, IntroSoulEntity.class) == 0, "The soul did not reach the staff");
        if (i == NecromancerIntro.TURN_END + 2) require(Math.abs(MathHelper.wrapDegrees(boss.getBodyYaw())) < 1, "He did not turn to the players: " + boss.getBodyYaw());
        if (i == NecromancerIntro.SLAM) require(BRAZIERS.stream().noneMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "Braziers lit before the slam");
        if (i == NecromancerIntro.RING_END + 1) require(BRAZIERS.stream().allMatch(pos -> w.getBlockState(pos).get(CampfireBlock.LIT)), "The ring did not light the braziers");
        if (i == NecromancerIntro.LENGTH - 5) require(boss.inIntro() && target.getHealth() == 20 && second.getHealth() == 20, "Intro ended early or let a watcher be hurt");
        if (i == NecromancerIntro.LENGTH + 2) {
            require(!boss.inIntro() && boss.isBossAggressive() && !boss.isAiDisabled(), "The fight did not start after the intro");
            require(!NecromancerIntro.watching(target) && !NecromancerIntro.watching(second), "Players were still held after the intro");
            intro = -1;
            siege = 1; since = tick;
        }
    }

    /** The full robed fight in fight mode: two sieges with their waves, the exposed crash and the transformation. */
    private void siegeFlow(ServerWorld w, int t, int s) throws Exception {
        // The rain and the army keep hitting both players; this run checks flow, not survival.
        if (siege >= 2) for (var p : List.of(target, second)) if (p.getHealth() < 10) heal(p);
        if (count(w, SoulFireballEntity.class) > 0) rained = true;
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
                if (!sniped && count(w, SoulBoltEntity.class) > 0) sniped = true; // Only the perch shoots bolts in a siege.
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
                require(rained, "No Soul Fire Rain fell during the siege");
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
                    Files.writeString(Path.of("NECROMANCER_PASSED.txt"), "Hollow Necromancer passed: the intro holds the boss and both players still and unhurt with no fall distance, puts out the braziers, ends early only when everyone skips, releases its watchers and relights only the braziers it put out when its boss unloads or an operator stops it (a stop is final), comes back fighting when saved mid-scene, tears the zombie's soul out while he faces it, lands it in the staff, turns him to the players, and its ring relights the braziers before the fight starts; cover stops soul bolts; every skull of an open volley lands; drain damages, heals within its cap, is tracked for clients and breaks on lost sight; wide grasping hands root a player who stays, spare one who steps out and are followed by an ambush burst from behind; siege waves rise on the floor away from players with the duo compositions, share the caster's side, cannot hurt it, finish rising with AI, do not burn at noon and dissolve on stop; blink escapes within the leash and leaves a curse; shift repositions away from everyone; wand damage applies; siege bodies are quickened; a Soul Fire Rain volley blasts the player who stays under a marker and spares one who leaves; soul light rides the bolts, fireballs and markers, flashes on impact, never replaces a block and clears itself, orphans included; the fight gates at 75% into a shielded perched siege where fireballs rain and a hovering target draws a perch bolt, waves 1-2 advance and a cleared siege crashes the caster down exposed; half health starts the second siege whose waves 3-4 bring brutes, and clearing it begins the transformation.\n");
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
    private static void heal(ServerPlayerEntity player) { player.setHealth(20); player.timeUntilRegen = 0; player.clearStatusEffects(); player.extinguish(); }
    private static List<MobEntity> minions(ServerWorld w) { return w.getEntitiesByClass(MobEntity.class, AREA, e -> e instanceof NecromancerMinion && e.isAlive()); }
    /** The boss as a save and reload would bring it back; the copy is never spawned. */
    private static NecromancerEntity reload(ServerWorld w, NecromancerEntity boss) {
        var out = net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY, w.getRegistryManager());
        boss.writeData(out);
        var copy = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
        copy.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY, w.getRegistryManager(), out.getNbt()));
        return copy;
    }
    private static int count(ServerWorld w, Class<? extends net.minecraft.entity.Entity> type) { return w.getEntitiesByClass(type, AREA, e -> !e.isRemoved()).size(); }
    /** Soul lights anywhere a rain volley or bolt can reach from the fixture's positions. */
    private static List<BlockPos> glows(ServerWorld w) {
        List<BlockPos> lit = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(-10, 99, -10, 20, 115, 30))
            if (w.getBlockState(pos).isOf(ModSpellBlocks.SOUL_GLOW)) lit.add(pos.toImmutable());
        return lit;
    }
    private static ServerPlayerEntity player(MinecraftServer s, String name, double x, double y, double z) throws Exception {
        var f = com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player", MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        f.setAccessible(true);
        return (ServerPlayerEntity)f.invoke(null, s, UUID.randomUUID(), name, x, y, z);
    }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
