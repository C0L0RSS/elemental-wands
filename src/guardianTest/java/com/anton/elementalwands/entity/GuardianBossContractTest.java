package com.anton.elementalwands.entity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import static com.anton.elementalwands.entity.GuardianCombatRules.*;

/** Regression cases for cooperative targeting, cooldowns, jump windows and rock trajectories. */
final class GuardianBossContractTest {
    private static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }

    static void run() {
        UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3);
        var players = List.of(new Candidate(a, 10, true), new Candidate(b, 12, true), new Candidate(c, 14, true));
        var history = new HashMap<UUID, Long>();
        require(target(Attack.THROW, players, history).id().equals(a), "Initial throw selection failed");
        history.put(a, 10L);
        require(target(Attack.BEAM, players, history).id().equals(b), "Beam tunneled on the prior throw target");
        history.put(b, 20L);
        require(target(Attack.THROW, players, history).id().equals(c), "Third co-op player was starved");
        history.put(c, 30L);
        require(target(Attack.BEAM, players, history).id().equals(a), "Target rotation did not wrap");
        var obscured = List.of(new Candidate(a, 10, false), new Candidate(b, 12, true));
        require(target(Attack.THROW, obscured, new HashMap<>()).id().equals(b), "Obstructed target was selected");
        require(target(Attack.THROW, List.of(), history) == null, "Empty party produced a target");
        require(target(Attack.BEAM, List.of(new Candidate(a, 30, true)), history) == null, "Beam exceeded range");
        var ready = new EnumMap<Attack, Long>(Attack.class);
        var crowd = List.of(new Candidate(a, 3, true), new Candidate(b, 7, true));
        require(choose(crowd, ready, 100, null) == Attack.SHOCKWAVE, "Cluster did not trigger area pressure");
        ready.put(Attack.SHOCKWAVE, 200L);
        require(choose(crowd, ready, 100, Attack.SHOCKWAVE) == Attack.SLAM, "Close defense did not obey cooldown");
        require(choose(players, new EnumMap<>(Attack.class), 100, Attack.THROW) == Attack.LEAP, "Distant players did not trigger a ready leap");
        var leapCooling = new EnumMap<Attack,Long>(Attack.class);
        leapCooling.put(Attack.LEAP,200L);
        require(choose(players,leapCooling,100,Attack.THROW)==Attack.SHOCKWAVE,"Wave was starved while leap cooled down");
        var far = List.of(new Candidate(a, 40, true));
        ready.put(Attack.THROW, 200L);
        ready.put(Attack.LEAP, 300L);
        require(choose(far, ready, 199, Attack.THROW) == null, "Distant throw skipped cooldown");
        require(choose(far, ready, 200, Attack.THROW) == Attack.THROW, "Distant solo encounter deadlocked");
        require(healthForParty(1)==600 && healthForParty(3)==1500 && healthForParty(5)==2400 && healthForParty(8)==3750, "Party scaling bounds changed");
        Vec3d center = Vec3d.ZERO;
        Box grounded = new Box(5.7,0,-.3,6.3,1.8,.3);
        require(waveContact(center, grounded, 5.5, 6, 0), "Wave missed standing player");
        require(!waveContact(center, grounded.offset(0,1,0), 5.5, 6, 0), "Successful jump was hit");
        require(!waveContact(center, grounded, 8, 8.45, 0), "Wave damaged a player behind the front");
        require(!waveContact(center, grounded, 2, 2.45, 0), "Wave hit ahead of its visible front");
        require(!waveContact(center, grounded.offset(0,-3,0), 5.5, 6, 0), "Wave hit through the floor");
        for (Vec3d aim : List.of(new Vec3d(0,1,10),new Vec3d(20,3,20),new Vec3d(0,-3,30))) {
            Vec3d pos = new Vec3d(2,3,0), velocity = launchVelocity(pos, aim);
            int ticks = flightTicks(pos, aim);
            for (int i=0;i<ticks;i++) { pos=pos.add(velocity); velocity=velocity.add(0,-ROCK_GRAVITY,0); }
            require(pos.distanceTo(aim)<1e-7, "Rock did not reach committed position with runtime integration");
        }
        Vec3d muzzle = new Vec3d(0,3,0), walking = new Vec3d(.215,0,0), person = new Vec3d(0,.9,14);
        Vec3d predicted = throwAim(muzzle,person,walking,THROW_RELEASE-THROW_LOCK);
        int arrival = flightTicks(muzzle,predicted) + THROW_RELEASE-THROW_LOCK;
        require(predicted.distanceTo(person.add(walking.multiply(arrival))) < .25,
                "Steady sideways walking still evades the predicted landing point");
        require(predicted.distanceTo(person.add(walking.multiply(-arrival))) > 3,
                "Reversing after commitment should beat prediction");
        require(lead(person,new Vec3d(100,40,0),40).distanceTo(person) <= 6.001,
                "A dash/teleport generated runaway aim");
        GuardianMotionSample sample = new GuardianMotionSample();
        sample.reset(person);
        Vec3d sampled = Vec3d.ZERO;
        for (int i=1;i<=12;i++) sampled = sample.observe(person.add(walking.multiply(i)));
        require(sampled.distanceTo(walking) < .001, "Server-position sampling missed steady player motion");
        require(sample.observe(person.add(100,0,0)).lengthSquared() == 0, "Teleport was treated as walking velocity");
        var solo = List.of(new Candidate(a,14,true));
        var cooldowns = new EnumMap<Attack,Long>(Attack.class);
        Attack prior = null;
        var attacks = new java.util.HashSet<Attack>();
        int decisions = 0;
        for (long now=0; now<600;) {
            Attack next = choose(solo,cooldowns,now,prior);
            if (next == null) { now++; continue; }
            require(now >= cooldowns.getOrDefault(next,0L), "Cadence bypassed per-attack cooldown");
            attacks.add(next); decisions++;
            cooldowns.put(next,now+next.duration+next.cooldown);
            now += next.duration+RECOVERY_GAP; prior=next;
        }
        require(attacks.containsAll(List.of(Attack.THROW,Attack.SHOCKWAVE,Attack.BEAM)), "Solo rotation starved an attack");
        require(decisions >= 6, "Long idle gaps returned to the encounter");
        require(THROW_RELEASE-THROW_LOCK>=4, "Throw dodge window shrank");
        require(Attack.THROW.duration-THROW_RELEASE>=20, "Throw lost recovery");
        require(GuardianPhaseRules.waveTicks(false)==38 && GuardianPhaseRules.waveTicks(true)==47, "Independent wave lifetime changed");
        System.out.println("Guardian boss checks passed: 3-player rotation, visibility/range, cluster selection, cooldowns, scaling, jumps, ballistic arcs.");
    }
}
