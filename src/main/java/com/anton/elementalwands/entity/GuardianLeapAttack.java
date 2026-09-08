package com.anton.elementalwands.entity;

import java.util.UUID;
import net.minecraft.entity.MovementType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static com.anton.elementalwands.entity.GuardianLeapRules.*;

/** Committed, collision-respecting leap. Invalid/blocked routes cancel rather than teleport. */
final class GuardianLeapAttack {
    private final FracturedGuardianEntity guardian;
    private final GuardianBossCombat combat;
    private final java.util.Set<UUID> launchHits = new java.util.HashSet<>(), landingHits = new java.util.HashSet<>();
    private final GuardianMotionSample motion = new GuardianMotionSample();
    private Vec3d origin, landing, arena, expected;
    private UUID target;
    private long started;
    private boolean active, flying, previousGravity;
    private float yaw;
    private String status = "No leap attempted yet.";

    String status() { return status; }
    private boolean abort(String reason) { cancel(); status = "Cancelled: " + reason; return true; }
    private boolean supported(ServerWorld world) {
        return world.getBlockCollisions(guardian, supportBox(guardian.getEntityPos(),guardian.getWidth())).iterator().hasNext();
    }

    GuardianLeapAttack(FracturedGuardianEntity guardian, GuardianBossCombat combat) {
        this.guardian = guardian; this.combat = combat;
    }

    boolean begin(ServerWorld world, ServerPlayerEntity player, Vec3d arena) {
        cancel();
        if (!supported(world) || guardian.hasVehicle() || guardian.hasPassengers()) { status = "Cannot start: feet unsupported or carrying/riding an entity."; return false; }
        this.arena = arena; origin = guardian.getEntityPos(); expected = origin;
        landing = findLanding(world,player.getEntityPos());
        if (landing == null) { status = "Cannot start: no clear landing and route nearby."; return false; }
        status = "Windup: route checked.";
        target = player.getUuid(); started = world.getTime(); active = true;
        motion.reset(player.getEntityPos());
        launchHits.clear(); landingHits.clear();
        faceLanding();
        guardian.showLeapMarker(started,landing);
        guardian.triggerAnim("guardian","leap_launch");
        world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_ATTACK,SoundCategory.HOSTILE,1.5f,.45f);
        return true;
    }

    /** True once finished/cancelled, allowing the director to choose its next action. */
    boolean tick(ServerWorld world) {
        if (!active) return true;
        int tick = (int)(world.getTime()-started);
        if (!guardian.isAlive() || guardian.hasVehicle() || guardian.hasPassengers()
                || guardian.getEntityPos().squaredDistanceTo(expected) > 1) return abort("boss moved unexpectedly, died, or acquired a passenger.");
        if (tick < LOCK) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
            if (player == null || !player.isAlive() || player.isSpectator() || player.getEntityWorld() != world
                    || player.squaredDistanceTo(origin) > 36*36) return abort("target left during windup.");
            Vec3d aim = GuardianCombatRules.lead(player.getEntityPos(),motion.observe(player.getEntityPos()),LAND-tick);
            Vec3d proposed = findLanding(world,aim);
            if (proposed != null) landing = proposed;
            faceLanding(); guardian.showLeapMarker(started,landing);
        }
        if (tick == LOCK && !clearRoute(world,landing)) return abort("route obstructed before aim lock.");
        if (tick == TAKEOFF) {
            if (!supported(world)) return abort("ground beneath feet changed before takeoff.");
            if (!clearRoute(world,landing)) return abort("route obstructed before takeoff.");
            status = "Airborne.";
            previousGravity = guardian.hasNoGravity(); flying = true;
            guardian.setNoGravity(true);
            if (!previousGravity) guardian.addCommandTag("ew_guardian_leap_gravity");
            guardian.triggerAnim("guardian","leap_air");
            guardian.startWaveAt(world.getTime()-GuardianCombatRules.SLAM_IMPACT,origin,0);
            world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_ATTACK,SoundCategory.HOSTILE,1.8f,.65f);
        }
        guardian.setYaw(yaw); guardian.setBodyYaw(yaw); guardian.setHeadYaw(yaw);
        guardian.setVelocity(Vec3d.ZERO);
        if (tick > TAKEOFF && tick <= LAND && flying) {
            Vec3d next = position(origin,landing,(tick-TAKEOFF)/(double)FLIGHT);
            Vec3d delta = next.subtract(guardian.getEntityPos());
            // Check the volume immediately before moving too: players may build a wall during flight.
            if (!clearSegment(world,guardian.getEntityPos(),next)) return abort("new obstruction in flight.");
            guardian.move(MovementType.SELF,delta);
            if (guardian.getEntityPos().squaredDistanceTo(next) > .04) return abort("movement was blocked in flight.");
            expected = guardian.getEntityPos();
            if (tick == LAND) {
                guardian.move(MovementType.SELF,new Vec3d(0,-.08,0));
                expected = guardian.getEntityPos();
                restoreGravity();
                guardian.clearLeapMarker();
                if (!supported(world)) return abort("landing support disappeared.");
                status = "Landed; recovering.";
                guardian.triggerAnim("guardian","leap_land");
                landing = guardian.getEntityPos();
                combat.leapLanded(world,landingHits);
            }
        }
        if (launchWaveTick(tick) >= 0) {
            combat.leapWave(world,launchWaveTick(tick),origin,launchHits);
        }
        if (landingWaveTick(tick) >= 0) {
            int waveTick = landingWaveTick(tick);
            if (waveTick == 0) guardian.startWaveAt(world.getTime()-GuardianCombatRules.SLAM_IMPACT,landing,1);
            combat.leapWave(world,waveTick,landing,landingHits);
        }
        if (tick >= LAND+RECOVERY) { cancel(); status = "Last leap completed."; return true; }
        return false;
    }

    void cancel() {
        if (active) {
            status = "Stopped.";
            guardian.setVelocity(Vec3d.ZERO);
            guardian.clearLeapMarker(); guardian.clearWave();
            guardian.stopTriggeredAnim("guardian",null);
        }
        restoreGravity(); active = false;
    }

    private void restoreGravity() {
        if (flying) { guardian.setNoGravity(previousGravity); flying = false; }
        guardian.removeCommandTag("ew_guardian_leap_gravity");
    }

    private void faceLanding() {
        Vec3d delta = landing.subtract(origin);
        yaw = (float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        guardian.setYaw(yaw); guardian.setBodyYaw(yaw); guardian.setHeadYaw(yaw);
    }

    private Vec3d findLanding(ServerWorld world, Vec3d aim) {
        for (double[] offset : new double[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2}}) {
            double x=aim.x+offset[0],z=aim.z+offset[1];
            if (!world.isChunkLoaded(BlockPos.ofFloored(x,aim.y,z))) continue;
            var floor=world.raycast(new RaycastContext(new Vec3d(x,aim.y+3,z),new Vec3d(x,aim.y-8,z),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
            if (floor.getType() == HitResult.Type.MISS) continue;
            Vec3d point=floor.getPos();
            if (point.squaredDistanceTo(arena)>36*36 || point.subtract(origin).horizontalLength()>MAX_RANGE+2) continue;
            if (!world.getFluidState(BlockPos.ofFloored(point.add(0,.1,0))).isEmpty()) continue;
            if (space(world,point) && clearRoute(world,point)) return point;
        }
        return null;
    }

    private boolean space(ServerWorld world, Vec3d pos) {
        double radius=guardian.getWidth()/2.0;
        Box box=new Box(pos.x-radius+.01,pos.y+.01,pos.z-radius+.01,
                pos.x+radius-.01,pos.y+guardian.getHeight(),pos.z+radius-.01);
        if (!world.getWorldBorder().contains(box)) return false;
        for (int x=(int)Math.floor(box.minX); x<=Math.floor(box.maxX); x+=1)
            for (int z=(int)Math.floor(box.minZ); z<=Math.floor(box.maxZ); z+=1)
                if (!world.isChunkLoaded(BlockPos.ofFloored(x,pos.y,z))) return false;
        return !world.getBlockCollisions(guardian,box).iterator().hasNext();
    }

    private boolean clearSegment(ServerWorld world, Vec3d a, Vec3d b) {
        int samples=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.35));
        for (int i=1;i<=samples;i++) if (!space(world,a.lerp(b,i/(double)samples))) return false;
        return true;
    }

    private boolean clearRoute(ServerWorld world, Vec3d destination) {
        Vec3d previous=origin;
        for (int i=1;i<=FLIGHT;i++) {
            Vec3d next=position(origin,destination,i/(double)FLIGHT);
            if (!clearSegment(world,previous,next)) return false;
            previous=next;
        }
        return true;
    }
}
