package com.anton.elementalwands.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Encounter-owned rubble. Never places blocks, explodes, or damages terrain. */
public class GuardianRockEntity extends ProjectileEntity implements FlyingItemEntity {
    private static final TrackedData<Boolean> HELD = DataTracker.registerData(GuardianRockEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final ItemStack STACK = new ItemStack(Blocks.COBBLESTONE);
    private boolean loadedFromSave;
    private int flyingTicks;

    public GuardianRockEntity(EntityType<? extends GuardianRockEntity> type, World world) { super(type, world); }

    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(HELD, true); }
    @Override public ItemStack getStack() { return STACK; }
    public boolean isHeld() { return dataTracker.get(HELD); }
    @Override protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        loadedFromSave = true; // Transient projectiles must not resume a stale fight after chunk reload.
    }

    public void release(Vec3d aim) {
        dataTracker.set(HELD, false);
        setVelocity(GuardianCombatRules.launchVelocity(getEntityPos(), aim));
        velocityDirty = true;
    }

    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld world) {
            if (loadedFromSave || age > 160 || !(getOwner() instanceof FracturedGuardianEntity guardian)
                    || !guardian.isAlive() || guardian.isRemoved()) { discard(); return; }
            if (dataTracker.get(HELD)) return;
            if (++flyingTicks > 80) { discard(); return; }
            Vec3d start = getEntityPos(), end = start.add(getVelocity());
            // Sweep the center and the cube's corners to stop its visible volume at cover.
            double fraction = 1;
            boolean blocked = false;
            double r = GuardianCombatRules.ROCK_RADIUS;
            for (int i = 0; i < 9; i++) {
                Vec3d offset = i == 8 ? Vec3d.ZERO : new Vec3d((i&1)==0?-r:r, (i&2)==0?-r:r, (i&4)==0?-r:r);
                var hit = world.raycast(new RaycastContext(start.add(offset), end.add(offset),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (hit.getType() != HitResult.Type.MISS) {
                    blocked = true;
                    fraction = Math.min(fraction,
                            start.add(offset).distanceTo(hit.getPos()) / Math.max(1e-8, start.distanceTo(end)));
                }
            }
            ServerPlayerEntity direct = null;
            double entityFraction = fraction;
            for (ServerPlayerEntity player : world.getPlayers(p -> GuardianBossCombat.canDamage(guardian, p))) {
                Box box = player.getBoundingBox().expand(r);
                var hit = box.contains(start) ? java.util.Optional.of(start) : box.raycast(start, end);
                if (hit.isEmpty()) continue;
                double f = start.distanceTo(hit.get()) / Math.max(1e-8, start.distanceTo(end));
                if (f < entityFraction) { entityFraction = f; direct = player; }
            }
            if (direct != null || blocked) {
                setPosition(start.lerp(end, entityFraction));
                impact(world, guardian, direct);
                return;
            }
            if (age % 2 == 0) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    getX(), getY(), getZ(), 2, .15, .15, .15, .01);
        } else if (dataTracker.get(HELD)) return;
        // Same ballistic integration on both sides; server alone decides impacts and damage.
        setPosition(getEntityPos().add(getVelocity()));
        setVelocity(getVelocity().add(0, -GuardianCombatRules.ROCK_GRAVITY, 0));
    }

    private void impact(ServerWorld world, FracturedGuardianEntity guardian, ServerPlayerEntity direct) {
        Vec3d center = getEntityPos();
        for (ServerPlayerEntity player : world.getPlayers(p -> GuardianBossCombat.canDamage(guardian, p))) {
            if (player != direct && player.squaredDistanceTo(center) > 2.25*2.25) continue;
            Vec3d contact = player.getBoundingBox().getCenter();
            // Surface-offset origin avoids a block impact's exact boundary occluding the open side.
            Vec3d from = center.subtract(getVelocity().normalize().multiply(.08));
            if (!GuardianBossCombat.clearLine(world, guardian, from, contact)) continue;
            if (player.damage(world, world.getDamageSources().thrown(this, guardian), player == direct ? 8 : 4)) {
                Vec3d away = player.getEntityPos().subtract(center);
                player.takeKnockback(.6, -away.x, -away.z);
            }
        }
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                center.x, center.y, center.z, 35, .5, .4, .5, .12);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.4f, .6f);
        discard();
    }
}
