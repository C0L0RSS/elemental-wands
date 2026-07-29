package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.entity.CalamityTornadoEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class EventHorizonManager {

    private static final class Horizon {
        private final Vec3d center;
        private final int radius;
        private final UUID casterUuid;
        private final int expiryTick;

        private Horizon(Vec3d center, int radius, UUID casterUuid, int expiryTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Horizon>> HORIZONS = new HashMap<>();

    private static final int DURATION_TICKS = 160;
    private static final int RADIUS = 13;
    private static final int INNER_RADIUS = 3;
    private static final float IMPLOSION_DAMAGE = 8.0f;
    private static final double TRACKING_HEIGHT = 64.0;
    private static final double IMPLOSION_TRACKING_HEIGHT = 96.0;

    private EventHorizonManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(EventHorizonManager::tickWorld);
        // Dropping these on shutdown keeps a pending implosion from firing at the
        // previous save's coordinates once another world reuses the same world key.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HORIZONS.clear());
    }

    public static void startEventHorizon(ServerWorld world, PlayerEntity caster, Vec3d center) {
        int now = world.getServer().getTicks();

        HORIZONS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Horizon(center, RADIUS, caster.getUuid(), now + DURATION_TICKS));

        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, 1.2f, 0.65f);
        world.spawnParticles(ParticleTypes.PORTAL, center.x, center.y + 0.7, center.z, 120, RADIUS * 0.5, 1.2,
                RADIUS * 0.5, 0.15);
        world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y + 0.5, center.z, 60, RADIUS * 0.45, 1.0,
                RADIUS * 0.45, 0.02);
    }

    private static void tickWorld(ServerWorld world) {
        List<Horizon> horizons = HORIZONS.get(world.getRegistryKey());
        if (horizons == null || horizons.isEmpty()) {
            return;
        }

        int now = world.getServer().getTicks();

        Iterator<Horizon> it = horizons.iterator();
        while (it.hasNext()) {
            Horizon horizon = it.next();

            if (now >= horizon.expiryTick) {
                triggerImplosion(world, horizon);
                it.remove();
                continue;
            }

            tickHorizon(world, horizon, now);
        }

        if (horizons.isEmpty()) {
            HORIZONS.remove(world.getRegistryKey());
        }
    }

    private static void tickHorizon(ServerWorld world, Horizon horizon, int now) {
        Box box = Box.of(horizon.center, horizon.radius * 2.0 + 2.0, TRACKING_HEIGHT, horizon.radius * 2.0 + 2.0);
        double radiusSq = horizon.radius * horizon.radius;
        double innerRadiusSq = INNER_RADIUS * INNER_RADIUS;

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(horizon.casterUuid));

        for (LivingEntity living : entities) {
            double dx = horizon.center.x - living.getX();
            double dz = horizon.center.z - living.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq > radiusSq) {
                continue;
            }

            double horizontalDist = Math.sqrt(Math.max(horizontalDistSq, 0.0001));
            double strength = (horizon.radius - horizontalDist) / horizon.radius;

            double nx = dx / horizontalDist;
            double nz = dz / horizontalDist;

            double radialStrength = 0.14 + strength * 0.26;
            double swirlStrength = 0.06 + strength * 0.12;
            double liftStrength = 0.10 + strength * 0.22;

            double radialX = nx * radialStrength;
            double radialZ = nz * radialStrength;
            double swirlX = -nz * swirlStrength;
            double swirlZ = nx * swirlStrength;

            living.addVelocity(radialX + swirlX, liftStrength, radialZ + swirlZ);
            living.velocityModified = true;
            living.fallDistance = 0.0f;

            MovementDisruptManager.suppressFireSurf(living);

            if (horizontalDistSq <= innerRadiusSq) {
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4, false, true, true));
                MovementDisruptManager.stripDashGlide(living);
            }
        }

        curveProjectiles(world, horizon, box, radiusSq);
        suppressTornadoes(world, horizon, box, radiusSq);

        if (now % 2 == 0) {
            spawnAmbientParticles(world, horizon, now);
        }

        if (now % 20 == 0) {
            world.playSound(null, BlockPos.ofFloored(horizon.center), SoundEvents.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS, 0.6f, 0.5f);
        }
    }

    private static void curveProjectiles(ServerWorld world, Horizon horizon, Box box, double radiusSq) {
        List<ProjectileEntity> projectiles = world.getEntitiesByClass(ProjectileEntity.class, box,
                projectile -> projectile.isAlive() && !projectile.isRemoved());

        for (ProjectileEntity projectile : projectiles) {
            double dx = horizon.center.x - projectile.getX();
            double dz = horizon.center.z - projectile.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq > radiusSq) {
                continue;
            }

            double horizontalDist = Math.sqrt(Math.max(horizontalDistSq, 0.0001));
            double strength = (horizon.radius - horizontalDist) / horizon.radius;
            Vec3d correction = new Vec3d(dx / horizontalDist, 0.0, dz / horizontalDist)
                    .multiply(0.03 + strength * 0.07);

            Vec3d velocity = projectile.getVelocity().add(correction);
            double maxSpeed = 2.8;
            if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
                velocity = velocity.normalize().multiply(maxSpeed);
            }

            projectile.setVelocity(velocity);
            projectile.velocityModified = true;
        }
    }

    private static void suppressTornadoes(ServerWorld world, Horizon horizon, Box box, double radiusSq) {
        List<CalamityTornadoEntity> tornadoes = world.getEntitiesByClass(CalamityTornadoEntity.class, box,
                tornado -> tornado.isAlive() && !tornado.isRemoved());

        for (CalamityTornadoEntity tornado : tornadoes) {
            double dx = horizon.center.x - tornado.getX();
            double dz = horizon.center.z - tornado.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq > radiusSq) {
                continue;
            }

            if (horizontalDistSq > 0.0001) {
                double horizontalDist = Math.sqrt(horizontalDistSq);
                Vec3d step = new Vec3d(dx / horizontalDist, 0.0, dz / horizontalDist).multiply(0.35);
                tornado.setPosition(tornado.getX() + step.x, tornado.getY(), tornado.getZ() + step.z);
            }

            tornado.setVelocity(tornado.getVelocity().multiply(0.1, 0.0, 0.1));
            tornado.velocityModified = true;
        }
    }

    private static void triggerImplosion(ServerWorld world, Horizon horizon) {
        Box box = Box.of(horizon.center, horizon.radius * 2.0, IMPLOSION_TRACKING_HEIGHT, horizon.radius * 2.0);
        double radiusSq = horizon.radius * horizon.radius;

        PlayerEntity caster = world.getPlayerByUuid(horizon.casterUuid);
        DamageSource source = caster == null ? world.getDamageSources().magic() : world.getDamageSources().playerAttack(caster);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(horizon.casterUuid));

        for (LivingEntity living : entities) {
            double dx = horizon.center.x - living.getX();
            double dz = horizon.center.z - living.getZ();
            if ((dx * dx + dz * dz) > radiusSq) {
                continue;
            }

            living.damage(world, source, IMPLOSION_DAMAGE);
        }

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, horizon.center.x, horizon.center.y + 0.6, horizon.center.z,
                3, 0.3, 0.3, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.PORTAL, horizon.center.x, horizon.center.y + 0.6, horizon.center.z, 160,
                horizon.radius * 0.45, 1.0, horizon.radius * 0.45, 0.18);
        world.playSound(null, BlockPos.ofFloored(horizon.center), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS, 1.1f, 0.7f);
    }

    private static void spawnAmbientParticles(ServerWorld world, Horizon horizon, int now) {
        double angle = now * 0.2;
        for (int i = 0; i < 16; i++) {
            double spin = angle + (i * (Math.PI * 2.0 / 16.0));
            double radius = horizon.radius * (0.25 + (i % 5) * 0.16);

            double x = horizon.center.x + Math.cos(spin) * radius;
            double z = horizon.center.z + Math.sin(spin) * radius;
            double y = horizon.center.y + 0.5 + (i % 6) * 0.35;

            world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
