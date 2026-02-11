package com.anton.elementalwands.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

    // Secondary: Permafrost Spikes
    private static final int SPIKE_LINE_LENGTH = 10;
    private static final float SPIKE_DAMAGE = 5.0f;
    private static final double SPIKE_KNOCKUP_VELOCITY = 1.0;
    private static final int SPIKE_DURATION_TICKS = 60; // 3 seconds

    public IceWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS))
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
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS))
            return;

        // Permafrost Spikes: Erupting line of ice spikes
        Vec3d direction = caster.getRotationVec(1.0f).normalize();
        Vec3d start = caster.getEntityPos();

        Set<BlockPos> spikePositions = new HashSet<>();
        Set<Entity> hitEntities = new HashSet<>();

        // Create line of spikes
        for (int i = 1; i <= SPIKE_LINE_LENGTH; i++) {
            Vec3d pos = start.add(direction.multiply(i));
            BlockPos blockPos = BlockPos.ofFloored(pos);
            BlockPos groundPos = blockPos.down();

            // Check if there's solid ground
            if (world.getBlockState(groundPos).isSolidBlock(world, groundPos)) {
                // Place spike blocks (ice spikes)
                spikePositions.add(blockPos);
                spikePositions.add(blockPos.up()); // 2 blocks tall spike

                // Check for entities at this position and damage/knock them up
                Box hitBox = Box.of(Vec3d.ofCenter(blockPos), 1.5, 2.5, 1.5);
                List<Entity> entities = world.getOtherEntities(caster, hitBox);

                for (Entity entity : entities) {
                    if (hitEntities.contains(entity))
                        continue;
                    hitEntities.add(entity);

                    // Deal damage
                    entity.damage(world, world.getDamageSources().magic(), SPIKE_DAMAGE);

                    // Knock upward
                    entity.setVelocity(entity.getVelocity().x, SPIKE_KNOCKUP_VELOCITY, entity.getVelocity().z);
                    entity.velocityModified = true;
                }

                // Spawn particles
                world.spawnParticles(
                        ParticleTypes.SNOWFLAKE,
                        blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                        15, 0.3, 0.5, 0.3, 0.05);
            }
        }

        // Place temporary ice spike blocks
        TemporaryBlockManager.placeTemporaryBlocks(
                world,
                spikePositions,
                Blocks.PACKED_ICE.getDefaultState(),
                SPIKE_DURATION_TICKS,
                state -> state.isAir());

        // Sound effects
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.2f, 0.8f);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS))
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
}
