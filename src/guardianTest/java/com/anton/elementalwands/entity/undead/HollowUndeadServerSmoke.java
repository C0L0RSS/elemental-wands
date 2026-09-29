package com.anton.elementalwands.entity.undead;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerMinion;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Real-server regression for the Hollow undead: rise, chase, hit-frame timing, the archer's
 * arrow, daylight burning, vanilla-style loot, the held death clip, and bound minions' rules.
 * Each creature fights its own player in a lane far from the others.
 */
public final class HollowUndeadServerSmoke implements ModInitializer {
    private static final int START = 25, NOON = 420, KILL = 640, END = 720;
    private final List<Lane> lanes = new ArrayList<>();
    private HollowUndeadEntity bound;
    private ServerPlayerEntity killer;
    private int tick, brutesDeath = -1, burned;

    /** One fighter and its victim, with the ticks its attack started and landed. */
    private static final class Lane {
        final HollowUndeadEntity mob; final ServerPlayerEntity victim; final int hit; final Box area;
        int strike = -1, landed = -1; float health = 20;
        Lane(HollowUndeadEntity mob, ServerPlayerEntity victim, int hit) {
            this.mob = mob; this.victim = victim; this.hit = hit;
            area = new Box(mob.getBlockPos()).expand(24, 6, 12);
        }
    }

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("UNDEAD_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void run(MinecraftServer server) throws Exception {
        int t = ++tick;
        ServerWorld w = server.getOverworld();
        if (t == START) {
            for (int x = -2; x <= 4; x++) for (int z = -2; z <= 8; z++) { w.getChunk(x, z); w.setChunkForced(x, z, true); }
            for (int x = -20; x <= 60; x++) for (int z = -20; z <= 125; z++) w.setBlockState(new BlockPos(x, 99, z), Blocks.STONE.getDefaultState());
            w.setTimeOfDay(18000);
            w.setWeather(24000, 0, false, false);
            w.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false, server); // Only the fighters under test.
            // The crawler must crawl to its victim first; the brute and archer can strike from where they rise.
            lane(server, w, ModEntities.HOLLOW_CRAWLER, 0, 5.5, HollowUndeadClips.CRAWLER_HIT);
            lane(server, w, ModEntities.HOLLOW_BRUTE, 40, 2.3, HollowUndeadClips.BRUTE_HIT);
            lane(server, w, ModEntities.HOLLOW_ARCHER, 80, 9, HollowUndeadClips.ARCHER_HIT);
            killer = player(server, "UndeadKiller", 40.5, 100, 118.5);
            var boss = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
            boss.refreshPositionAndAngles(30.5, 100, 110.5, 0, 0);
            w.spawnEntity(boss);
            boss.stopFight();
            bound = new HollowCrawlerEntity(ModEntities.HOLLOW_CRAWLER, w);
            bound.refreshPositionAndAngles(32.5, 100, 110.5, 0, 0);
            NecromancerMinion.bind(bound, boss);
            w.spawnEntity(bound);
            return;
        }
        if (t < START) return;
        if (t == START + 1) for (Lane lane : lanes)
            require(lane.mob.isRising() && Math.abs(lane.mob.getY() - 100) < .05, lane.mob.getType().getUntranslatedName() + " did not start rising on the floor");
        for (Lane lane : lanes) watch(w, lane, t);
        if (t == START + HollowUndeadClips.CRAWLER_RISE + 2)
            require(!lanes.getFirst().mob.isRising(), "Crawler rise did not end with its clip");
        if (t == START + HollowUndeadClips.CRAWLER_RISE + 30)
            require(lanes.getFirst().mob.getX() > 1, "Crawler did not crawl toward its victim after rising");
        if (t == NOON - 1) for (Lane lane : lanes) {
            String name = lane.mob.getType().getUntranslatedName();
            require(lane.strike >= 0 && lane.landed >= 0, name + " never landed an attack");
            int clip = lane.landed - lane.strike - HollowUndeadEntity.BLEND;
            require(clip == lane.hit, name + " landed " + clip + " ticks into its clip, not on frame " + lane.hit);
        }
        // Noon: wild bodies catch fire under the open sky, the Necromancer's does not.
        if (t == NOON) { w.setTimeOfDay(6000); for (Lane lane : lanes) { lane.mob.setHealth(lane.mob.getMaxHealth()); lane.victim.getAbilities().invulnerable = true; } }
        if (t > NOON && t < KILL) {
            for (Lane lane : lanes) if (lane.mob.isOnFire()) burned |= 1 << lanes.indexOf(lane);
            require(!bound.isOnFire(), "A bound minion burned in daylight");
        }
        if (t == KILL) {
            require(burned == 7, "Not every wild body burned at noon: mask " + burned);
            var brute = lanes.get(1).mob;
            brute.extinguish();
            require(brute.damage(w, w.getDamageSources().playerAttack(killer), 1000), "Brute could not be killed");
            require(bound.damage(w, w.getDamageSources().playerAttack(killer), 1000), "Bound minion could not be killed");
            brutesDeath = t;
        }
        if (t == KILL + 3) {
            Box brute = lanes.get(1).mob.getBoundingBox().expand(3);
            require(!w.getEntitiesByClass(ItemEntity.class, brute, e -> e.getStack().isOf(Items.BONE)).isEmpty(), "Brute dropped no bones");
            require(!w.getEntitiesByClass(ExperienceOrbEntity.class, brute, e -> true).isEmpty(), "Brute dropped no experience");
            Box minion = bound.getBoundingBox().expand(3);
            require(w.getEntitiesByClass(ItemEntity.class, minion, e -> true).isEmpty()
                    && w.getEntitiesByClass(ExperienceOrbEntity.class, minion, e -> true).isEmpty(), "A bound minion dropped loot");
        }
        if (brutesDeath >= 0 && t == brutesDeath + 25) require(!lanes.get(1).mob.isRemoved(), "Brute corpse vanished before its death clip finished");
        if (brutesDeath >= 0 && t == brutesDeath + HollowUndeadClips.BRUTE_DEATH + 9) require(lanes.get(1).mob.isRemoved(), "Brute corpse was never removed");
        if (t == END) {
            Files.writeString(Path.of("UNDEAD_PASSED.txt"), "Hollow undead passed: all three rise on the floor, the crawler crawls to its victim, "
                    + "crawler, brute and archer land damage/arrows exactly on their clip hit frames after the blend (" + HollowUndeadClips.CRAWLER_HIT + ", "
                    + HollowUndeadClips.BRUTE_HIT + ", " + HollowUndeadClips.ARCHER_HIT + " ticks), wild bodies burn at noon while a bound minion "
                    + "does not, the brute drops bones and experience and its corpse stays for the death clip, and a bound minion drops nothing.\n");
            server.stop(false);
        }
    }

    private void lane(MinecraftServer server, ServerWorld w, EntityType<? extends HollowUndeadEntity> type, double z, double gap, int hit) throws Exception {
        HollowUndeadEntity mob = type.spawn(w, BlockPos.ofFloored(.5, 100, z + .5), SpawnReason.COMMAND);
        require(mob != null, "Could not spawn " + type.getUntranslatedName());
        mob.refreshPositionAndAngles(.5, 100, z + .5, -90, 0);
        ServerPlayerEntity victim = player(server, "Victim" + lanes.size(), .5 + gap, 100, z + .5);
        victim.setLoaded(true); victim.onTeleportationDone(); victim.setNoGravity(true); victim.getHungerManager().setFoodLevel(10);
        lanes.add(new Lane(mob, victim, hit));
    }

    /** Records the first attack clip and the first damage/arrow; nothing may land while rising. */
    private void watch(ServerWorld w, Lane lane, int t) {
        if (lane.mob.isRemoved() || t >= NOON) return;
        if (lane.strike < 0 && lane.mob.isStriking()) lane.strike = t;
        boolean landed;
        if (lane.mob instanceof HollowArcherEntity) {
            List<ArrowEntity> arrows = w.getEntitiesByClass(ArrowEntity.class, lane.area, a -> a.getOwner() == lane.mob);
            landed = !arrows.isEmpty();
            if (landed && lane.landed < 0) {
                Vec3d toward = lane.victim.getEntityPos().subtract(arrows.getFirst().getEntityPos());
                require(arrows.getFirst().getVelocity().dotProduct(toward) > 0, "Archer's arrow did not fly toward its victim");
            }
        } else landed = lane.victim.getHealth() < lane.health;
        if (landed && lane.landed < 0) {
            require(lane.strike >= 0, lane.mob.getType().getUntranslatedName() + " hurt its victim outside an attack clip");
            lane.landed = t;
        }
        lane.health = lane.victim.getHealth();
        if (lane.landed >= 0 && t - lane.landed > 15) { lane.victim.setHealth(20); lane.health = 20; }
    }

    private static ServerPlayerEntity player(MinecraftServer s, String name, double x, double y, double z) throws Exception {
        var f = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player", MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        f.setAccessible(true);
        return (ServerPlayerEntity)f.invoke(null, s, UUID.randomUUID(), name, x, y, z);
    }

    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
