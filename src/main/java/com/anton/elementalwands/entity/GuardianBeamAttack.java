package com.anton.elementalwands.entity;

import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** One mouth pulse, with an immutable aim after the tell and at most one hit per victim during its visible pulse. */
final class GuardianBeamAttack {
    private final FracturedGuardianEntity guardian;
    private final GuardianMotionSample motion = new GuardianMotionSample();
    private final java.util.Set<UUID> struck = new java.util.HashSet<>();
    private UUID target;
    private long started;
    private boolean active;
    private boolean previousNoAi;
    private Vec3d direction = new Vec3d(0,0,1);
    private Vec3d origin = Vec3d.ZERO;
    private boolean fired;
    private GuardianWallImpact walls = new GuardianWallImpact();
    private Vec3d anchor = Vec3d.ZERO;

    GuardianBeamAttack(FracturedGuardianEntity guardian) { this.guardian = guardian; }
    boolean isActive() { return active; }

    void begin(ServerPlayerEntity player) {
        cancel();
        target = player.getUuid();
        started = guardian.getEntityWorld().getTime();
        active = true;
        fired = false;
        struck.clear();
        walls = new GuardianWallImpact();
        motion.reset(player.getEntityPos());
        anchor = guardian.getEntityPos();
        previousNoAi = guardian.isAiDisabled();
        guardian.getNavigation().stop();
        guardian.setAiDisabled(true); // Prevent movement/body controllers fighting a committed cast.
        aimAt(player, GuardianBeamTiming.FIRE);
        guardian.syncBeam(started, origin, origin, pitch());
        guardian.triggerAnim("guardian", "beam");
        ServerWorld world = (ServerWorld)guardian.getEntityWorld();
        world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
                SoundCategory.HOSTILE, 1.1f, .85f);
    }

    void cancel() {
        if (active) {
            active = false;
            guardian.setAiDisabled(previousNoAi);
            guardian.clearBeam();
            guardian.stopTriggeredAnim("guardian", "beam");
        }
        target = null;
        walls = new GuardianWallImpact();
    }

    void tick(ServerWorld world) {
        if (!active) return;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
        if (!guardian.isAlive() || player == null || !player.isAlive() || player.isSpectator()
                || player.getEntityWorld() != world || guardian.squaredDistanceTo(player) > 32*32
                || guardian.getEntityPos().squaredDistanceTo(anchor) > .25*.25) {
            cancel();
            return;
        }
        int tick = (int)(world.getTime()-started);
        if (tick >= GuardianBeamTiming.END) { cancel(); return; }
        guardian.setVelocity(0, guardian.getVelocity().y, 0);
        if (GuardianBeamTiming.isTracking(tick)) aimAt(player, GuardianBeamTiming.FIRE-tick);
        float yaw = (float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        guardian.setYaw(yaw);
        guardian.setBodyYaw(yaw);
        guardian.setHeadYaw(yaw);
        if (tick < GuardianBeamTiming.FIRE) {
            // Converging, short-lived sparks; no line on the ground reveals the final aim early.
            double phase = tick * .65;
            Vec3d side = direction.crossProduct(new Vec3d(0,1,0)).normalize();
            Vec3d up = side.crossProduct(direction).normalize();
            double radius = 1.0 - .65*tick/GuardianBeamTiming.FIRE;
            for (int i=0; i<3; i++) {
                double angle=phase+i*Math.PI*2/3;
                Vec3d p=origin.add(side.multiply(Math.cos(angle)*radius)).add(up.multiply(Math.sin(angle)*radius));
                Vec3d inward=origin.subtract(p).multiply(.12);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,0,inward.x,inward.y,inward.z,1);
            }
        }
        if (GuardianBeamTiming.fires(tick) && !fired) {
            fired = true;
            fire(world);
        }
        if (GuardianBeamTiming.isPulse(tick)) damagePulse(world);
    }

    private float pitch() {
        return (float)Math.toDegrees(-Math.asin(direction.y));
    }

    private void aimAt(LivingEntity player, int remaining) {
        Vec3d base = guardian.getEntityPos().add(0,4.25,0);
        Vec3d targetPos = GuardianCombatRules.lead(player.getBoundingBox().getCenter(),
                motion.observe(player.getEntityPos()), remaining);
        Vec3d from = base.add(0,-.2,0);
        // Solve the small muzzle offset together with pitch so the pulse emerges from the mouth.
        for (int i=0; i<3; i++) {
            Vec3d delta = targetPos.subtract(from);
            float yaw = (float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            float pitch = MathHelper.clamp((float)Math.toDegrees(-Math.atan2(delta.y,delta.horizontalLength())), -50,50);
            double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);
            direction=new Vec3d(-Math.sin(yr)*Math.cos(pr),-Math.sin(pr),Math.cos(yr)*Math.cos(pr));
            Vec3d up=new Vec3d(-Math.sin(yr)*Math.sin(pr),Math.cos(pr),Math.cos(yr)*Math.sin(pr));
            Vec3d pivot = base.add(-Math.sin(yr)*.4375,0,Math.cos(yr)*.4375);
            from=pivot.add(direction.multiply(.8)).subtract(up.multiply(.2));
            guardian.setYaw(yaw);
            guardian.setBodyYaw(yaw);
            guardian.setHeadYaw(yaw);
        }
        origin = from;
        if (active) guardian.syncBeam(started,origin,origin,pitch());
    }

    private Vec3d pulseEnd(ServerWorld world) {
        Vec3d end = origin.add(direction.multiply(GuardianBeamTiming.RANGE));
        return walls.clip(world,guardian,origin,end,false,true);
    }

    private void fire(ServerWorld world) {
        Vec3d end = pulseEnd(world);
        world.playSound(null, origin.x,origin.y,origin.z,SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.HOSTILE,1.4f,1.2f);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,end.x,end.y,end.z,10,.18,.18,.18,.08);
    }

    private void damagePulse(ServerWorld world) {
        Vec3d end = pulseEnd(world);
        guardian.syncBeam(started,origin,end,pitch());
        Box search = new Box(origin,end).expand(GuardianBeamTiming.RADIUS);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class,search,
                e -> e != guardian && !struck.contains(e.getUuid()) && e.isAlive() && !e.isSpectator() && !guardian.isTeammate(e)
                        && !(e instanceof PlayerEntity p && p.isCreative())
                        && !(e instanceof ServerPlayerEntity p && !com.anton.elementalwands.arena.GuardianArenaManager.eligible(guardian,p)))) {
            var hit = GuardianBeamGeometry.contact(origin,end,victim.getBoundingBox());
            if (hit.isEmpty()) continue;
            Vec3d contact = hit.get();
            if (!walls.clear(world,guardian,origin,contact,false)) continue;
            struck.add(victim.getUuid()); // A blocked hit also consumes the attempt; no repeated shield spam.
            if (victim.damage(world,world.getDamageSources().mobAttack(guardian),GuardianBeamTiming.DAMAGE)) {
                victim.takeKnockback(.45,-direction.x,-direction.z);
            }
        }
    }
}
