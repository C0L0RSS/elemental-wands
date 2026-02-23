package com.anton.elementalwands.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.anton.elementalwands.entity.ChillSnowballEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.BlizzardManager;
import com.anton.elementalwands.util.ChillTracker;
import com.anton.elementalwands.util.TemporaryBlockManager;

public class IceWandItem extends AbstractWandItem {

    // Primary: Frost-Bite Volley with Shatter
    private static final float SHARD_BASE_DAMAGE = 2.0f;
    private static final float SHARD_SHATTER_DAMAGE = 4.0f;
    private static final int SHATTER_THRESHOLD_STACKS = 3;
    private static final int SHATTER_AOE_RADIUS = 3; // blocks
    private static final float SHATTER_AOE_DAMAGE = 2.0f;
    private static final int SHATTER_AOE_FROST_STACKS = 1; // Apply 1 Frost stack to AoE targets

    // Secondary: Glacial Gust
    private static final float GUST_DAMAGE = 4.0f;
    private static final int GUST_LIFETIME = 15; // 15 ticks ~ 0.75s

    public IceWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5F,
                0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

        // Fire 3 snowballs as volley
        for (int i = 0; i < 3; i++) {
            // Create custom snowball that checks for shatter
            ChillSnowballEntityWithShatter snowball = new ChillSnowballEntityWithShatter(world, caster);
            snowball.setPosition(caster.getEyePos().x, caster.getEyePos().y, caster.getEyePos().z);
            snowball.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0.0F, 1.5F, 4.0F); // 4.0F divergence for
                                                                                                // volley spread
            world.spawnEntity(snowball);
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, SoundCategory.PLAYERS, 1.0F,
                0.5F);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F,
                1.2F);

        // Fire 5 horizontal projectiles for Glacial Gust
        for (int i = 0; i < 5; i++) {
            float yawOffset = (i - 2) * 15.0f; // -30, -15, 0, 15, 30 spread
            ColdWaveProjectile projectile = new ColdWaveProjectile(world, caster, new Vec3d(0, 0, 0), yawOffset);
            world.spawnEntity(projectile);
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, getUltimateCooldownTicks()))
            return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d center = hit.getType() == HitResult.Type.MISS ? caster.getEntityPos() : hit.getPos();

        BlizzardManager.startBlizzard(world, caster, center);
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS,
                1.0f, 0.6f);
    }

    // Custom snowball that implements shatter mechanic
    private static class ChillSnowballEntityWithShatter extends ChillSnowballEntity {
        public ChillSnowballEntityWithShatter(ServerWorld world, LivingEntity owner) {
            super(ModEntities.CHILL_SNOWBALL, world);
            setOwner(owner);
        }

        @Override
        protected void onEntityHit(EntityHitResult entityHitResult) {
            if (!(getEntityWorld() instanceof ServerWorld sw)) {
                super.onEntityHit(entityHitResult);
                return;
            }

            Entity entity = entityHitResult.getEntity();
            if (entity instanceof LivingEntity living) {
                // Check Frost stacks before applying new stack
                int currentStacks = ChillTracker.getStacks(living);

                // Apply frost stack first (from parent class)
                ChillTracker.addStack(sw, living);

                // Check for shatter (3+ stacks)
                if (currentStacks >= SHATTER_THRESHOLD_STACKS) {
                    // SHATTER!
                    triggerShatter(sw, living);
                } else {
                    // Normal hit - deal base damage
                    living.damage(sw, sw.getDamageSources().thrown(this, getOwner()), SHARD_BASE_DAMAGE);
                }

                // Particles
                sw.spawnParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getBodyY(0.5), living.getZ(), 12, 0.35,
                        0.35, 0.35, 0.02);
            }

            // Don't call super - we handle everything here
            discard();
        }

        private void triggerShatter(ServerWorld world, LivingEntity target) {
            // Deal increased shatter damage
            target.damage(world, world.getDamageSources().thrown(this, getOwner()), SHARD_SHATTER_DAMAGE);

            // Clear frost stacks
            ChillTracker.clearFrostStacks(world, target);

            // AoE ice burst - damage and apply frost to nearby enemies
            Box aoeBox = Box.of(target.getEntityPos(), SHATTER_AOE_RADIUS * 2, SHATTER_AOE_RADIUS * 2,
                    SHATTER_AOE_RADIUS * 2);
            List<LivingEntity> nearbyEntities = world.getEntitiesByClass(LivingEntity.class, aoeBox,
                    e -> e != target && e.squaredDistanceTo(target) <= SHATTER_AOE_RADIUS * SHATTER_AOE_RADIUS);

            for (LivingEntity nearby : nearbyEntities) {
                // Deal AoE damage
                nearby.damage(world, world.getDamageSources().magic(), SHATTER_AOE_DAMAGE);

                // Apply Frost stack
                for (int i = 0; i < SHATTER_AOE_FROST_STACKS; i++) {
                    ChillTracker.addStack(world, nearby);
                }
            }

            // Dramatic shatter particles and sound
            world.spawnParticles(
                    ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getBodyY(0.5), target.getZ(),
                    50, 1.0, 1.0, 1.0, 0.15);
            world.spawnParticles(
                    ParticleTypes.EXPLOSION,
                    target.getX(), target.getBodyY(0.5), target.getZ(),
                    3, 0.2, 0.2, 0.2, 0.0);
            world.playSound(
                    null,
                    target.getBlockPos(),
                    SoundEvents.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS,
                    1.5f, 0.8f);
        }
    }

    // Custom projectile for Glacial Gust
    private static class ColdWaveProjectile extends ProjectileEntity {
        private Vec3d startPos;
        private final Set<Entity> hitEntities = new HashSet<>();
        private int ticksAlive = 0;

        public ColdWaveProjectile(ServerWorld world, LivingEntity owner, Vec3d offset, float yawOffset) {
            super(ModEntities.VACUUM_BLADE, world);
            setOwner(owner);
            setNoGravity(true);

            Vec3d spawnPos = owner.getEyePos().add(offset);
            setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            this.startPos = spawnPos;

            float yaw = owner.getYaw() + yawOffset;
            Vec3d direction = Vec3d.fromPolar(0, yaw).normalize(); // Horizontal plane using pitch=0
            setVelocity(direction.multiply(1.5));
        }

        @Override
        protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        }

        @Override
        public void tick() {
            super.tick();
            if (!(getEntityWorld() instanceof ServerWorld sw))
                return;

            ticksAlive++;
            if (ticksAlive > GUST_LIFETIME) {
                discard();
                return;
            }

            // Particles
            sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.3, 0.3, 0.3, 0.05);
            sw.spawnParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 3, 0.3, 0.3, 0.3, 0.05);

            // Piercing collision check
            Box hitBox = getBoundingBox().expand(0.5);
            List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, hitBox,
                    e -> e != getOwner() && e.isAlive() && !hitEntities.contains(e));

            for (LivingEntity target : targets) {
                hitEntities.add(target);

                target.damage(sw, sw.getDamageSources().magic(), GUST_DAMAGE);

                int stacks = ChillTracker.getStacks(target);
                if (stacks >= 2) {
                    target.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, true, true));
                } else {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 4, false, true, true));
                }
            }

            // Block collision - ignore small blocks
            BlockPos pos = getBlockPos();
            BlockState state = sw.getBlockState(pos);
            if (state.isSolidBlock(sw, pos) && state.isFullCube(sw, pos)) {
                discard();
            }

            Vec3d vel = getVelocity();
            setPosition(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
        }
    }
}
