package com.anton.elementalwands.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.anton.elementalwands.util.BlazeTrailManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.WandUtils;
import net.minecraft.entity.EquipmentSlot;

public class FireWandItem extends AbstractWandItem {

    // Primary: Shotgun
    private static final int SHOTGUN_PELLET_COUNT = 6;
    private static final double SHOTGUN_RANGE = 10.0;
    private static final float SHOTGUN_PELLET_DAMAGE = 2.0f;
    private static final double SHOTGUN_SPREAD = 0.15; // Spread factor
    private static final int SHOTGUN_BURN_SECONDS = 3;

    // Secondary: Blazing Dash
    private static final int DASH_DURATION_TICKS = 60; // 3 seconds

    // Ultimate: Heavy Meteor
    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 10.0f;

    public FireWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, world, entity, slot);
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                && entity instanceof LivingEntity living) {
            // Permanent Fire Resistance while holding
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 2, 0, false, false, true));
        }
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS))
            return;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
                0.5f, 1.2f);

        Vec3d look = caster.getRotationVec(1.0f);
        Vec3d start = caster.getEyePos();

        for (int i = 0; i < SHOTGUN_PELLET_COUNT; i++) {
            // Apply randomized spread
            Vec3d spreadDir = look.add(
                    (world.random.nextDouble() - 0.5) * SHOTGUN_SPREAD,
                    (world.random.nextDouble() - 0.5) * SHOTGUN_SPREAD,
                    (world.random.nextDouble() - 0.5) * SHOTGUN_SPREAD).normalize();

            // Use our updated custom raycast
            HitResult hit = WandUtils.raycast(world, caster, spreadDir, SHOTGUN_RANGE);
            Vec3d end = hit.getPos();

            spawnParticleLine(world, start, end, ParticleTypes.FLAME);

            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) hit).getEntity();
                applyDamage(world, caster, entity, SHOTGUN_PELLET_DAMAGE);
                entity.setOnFireFor(SHOTGUN_BURN_SECONDS);

                if (entity instanceof LivingEntity living) {
                    living.takeKnockback(0.5, caster.getX() - living.getX(), caster.getZ() - living.getZ());
                }
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                // Small flame particle on block hit
                world.spawnParticles(ParticleTypes.SMALL_FLAME, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS))
            return;

        // Apply Speed and Fire Res
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DASH_DURATION_TICKS, 1)); // Level II = Amp
                                                                                                       // 1
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, DASH_DURATION_TICKS, 0));

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Register for Fire Trail
        if (caster instanceof ServerPlayerEntity serverPlayer) {
            BlazeTrailManager.addTrail(serverPlayer, DASH_DURATION_TICKS);
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS))
            return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d target = hit.getPos();

        MeteorManager.spawnMeteor(world, caster, target, METEOR_SPAWN_HEIGHT, METEOR_EXPLOSION_POWER);
    }
}
