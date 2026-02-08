package com.anton.elementalwands.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class WindWandItem extends AbstractWandItem {

    private static final double MOMENTUM_THRESHOLD = 0.4;
    private static final double LOW_MOMENTUM_RANGE = 6.0;
    private static final double HIGH_MOMENTUM_RANGE = 12.0;
    private static final float LOW_MOMENTUM_DAMAGE = 4.0f;
    private static final float HIGH_MOMENTUM_DAMAGE = 8.0f;
    private static final double DASH_SPEED = 1.7;

    private static final int OVERDRIVE_DURATION_TICKS = 120;
    private static final int OVERDRIVE_SPEED_AMPLIFIER = 4; // Speed V
    private static final int OVERDRIVE_CRASH_DURATION_TICKS = 60;
    private static final int OVERDRIVE_CRASH_SLOWNESS_AMPLIFIER = 3; // Slowness IV
    private static final int OVERDRIVE_CRASH_WEAKNESS_AMPLIFIER = 1; // Weakness II

    private static final String NBT_LAST_SECONDARY = "ew_last_secondary";

    private static final Identifier OVERDRIVE_VULNERABILITY_ID = Identifier.of("elementalwands",
            "zephyr_overdrive_vulnerability");
    private static final double OVERDRIVE_TARGET_ARMOR = -125.0;

    private static final Map<RegistryKey<World>, Map<UUID, Integer>> OVERDRIVE_EXPIRY_BY_WORLD = new HashMap<>();
    private static boolean overdriveTickRegistered = false;

    public WindWandItem(Settings settings) {
        super(settings);
        ensureOverdriveTickRegistered();
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!canUsePrimary(world, caster, stack)) return;

        double horizontalVelocity = getHorizontalVelocity(caster);
        boolean highMomentum = horizontalVelocity > MOMENTUM_THRESHOLD;
        double slashRange = highMomentum ? HIGH_MOMENTUM_RANGE : LOW_MOMENTUM_RANGE;
        float slashDamage = highMomentum ? HIGH_MOMENTUM_DAMAGE : LOW_MOMENTUM_DAMAGE;

        HitResult hit = raycast(world, caster, slashRange);
        Vec3d start = caster.getEyePos();
        Vec3d end = hit.getPos();

        spawnParticleLine(world, start, end, ParticleTypes.CLOUD);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();

            if (entity instanceof LivingEntity living) {
                boolean wasAliveBeforeHit = living.isAlive();
                float healthBeforeHit = living.getHealth();
                boolean didDamage = applyDamage(world, caster, living, slashDamage);
                if (didDamage && wasAliveBeforeHit && healthBeforeHit > 0.0f
                        && (!living.isAlive() || living.getHealth() <= 0.0f)) {
                    resetSecondaryCooldown(stack);
                }

                living.takeKnockback(1.8, caster.getX() - living.getX(), caster.getZ() - living.getZ());
                living.velocityModified = true;
            } else {
                applyDamage(world, caster, entity, slashDamage);
            }
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS)) return;

        Vec3d look = caster.getRotationVec(1.0f).normalize();
        caster.addVelocity(look.x * DASH_SPEED, look.y * DASH_SPEED, look.z * DASH_SPEED);
        caster.setOnGround(false);
        caster.fallDistance = 0;

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, true));

        if (caster instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            serverPlayer.networkHandler
                    .sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(caster));
        }
        caster.velocityModified = true;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.0f,
                1.1f);
        world.spawnParticles(ParticleTypes.CLOUD, caster.getX(), caster.getY(), caster.getZ(), 25, 0.4, 0.2, 0.4, 0.03);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS)) return;

        activateOverdrive(world, caster);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.2f,
                0.9f);
        world.spawnParticles(ParticleTypes.CLOUD, caster.getX(), caster.getBodyY(0.5), caster.getZ(), 32, 0.8, 0.4, 0.8,
                0.04);
    }

    private boolean canUsePrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (isOverdriveActive(world, caster)) {
            return true;
        }
        return tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS);
    }

    private static double getHorizontalVelocity(PlayerEntity caster) {
        Vec3d velocity = caster.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private static void resetSecondaryCooldown(ItemStack stack) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> data.remove(NBT_LAST_SECONDARY));
    }

    private static void activateOverdrive(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();
        OVERDRIVE_EXPIRY_BY_WORLD
                .computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>())
                .put(caster.getUuid(), now + OVERDRIVE_DURATION_TICKS);

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, OVERDRIVE_DURATION_TICKS,
                OVERDRIVE_SPEED_AMPLIFIER, false, true, true));

        applyOverdriveVulnerability(caster);
    }

    private static boolean isOverdriveActive(ServerWorld world, PlayerEntity caster) {
        Map<UUID, Integer> states = OVERDRIVE_EXPIRY_BY_WORLD.get(world.getRegistryKey());
        if (states == null) return false;

        Integer expiryTick = states.get(caster.getUuid());
        if (expiryTick == null) return false;

        int now = world.getServer().getTicks();
        if (now < expiryTick) return true;

        expireOverdrive(world, caster.getUuid(), true);
        return false;
    }

    private static void tickOverdrive(ServerWorld world) {
        Map<UUID, Integer> states = OVERDRIVE_EXPIRY_BY_WORLD.get(world.getRegistryKey());
        if (states == null || states.isEmpty()) return;

        int now = world.getServer().getTicks();
        Iterator<Map.Entry<UUID, Integer>> iterator = states.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (now < entry.getValue()) continue;

            UUID playerUuid = entry.getKey();
            iterator.remove();

            PlayerEntity player = world.getPlayerByUuid(playerUuid);
            if (player == null) continue;

            removeOverdriveVulnerability(player);
            if (player.isAlive() && !player.isSpectator()) {
                applyOverdriveCrash(player);
            }
        }

        if (states.isEmpty()) {
            OVERDRIVE_EXPIRY_BY_WORLD.remove(world.getRegistryKey());
        }
    }

    private static void expireOverdrive(ServerWorld world, UUID playerUuid, boolean applyCrash) {
        Map<UUID, Integer> states = OVERDRIVE_EXPIRY_BY_WORLD.get(world.getRegistryKey());
        if (states == null) return;

        if (states.remove(playerUuid) == null) return;
        if (states.isEmpty()) {
            OVERDRIVE_EXPIRY_BY_WORLD.remove(world.getRegistryKey());
        }

        PlayerEntity player = world.getPlayerByUuid(playerUuid);
        if (player == null) return;

        removeOverdriveVulnerability(player);
        if (applyCrash && player.isAlive() && !player.isSpectator()) {
            applyOverdriveCrash(player);
        }
    }

    private static void applyOverdriveCrash(PlayerEntity caster) {
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, OVERDRIVE_CRASH_DURATION_TICKS,
                OVERDRIVE_CRASH_SLOWNESS_AMPLIFIER, false, true, true));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, OVERDRIVE_CRASH_DURATION_TICKS,
                OVERDRIVE_CRASH_WEAKNESS_AMPLIFIER, false, true, true));
    }

    private static void applyOverdriveVulnerability(PlayerEntity caster) {
        EntityAttributeInstance armor = caster.getAttributeInstance(EntityAttributes.ARMOR);
        if (armor == null) return;

        armor.removeModifier(OVERDRIVE_VULNERABILITY_ID);

        double currentArmor = armor.getValue();
        double targetArmor = Math.min(currentArmor, OVERDRIVE_TARGET_ARMOR);
        double modifierValue = targetArmor - currentArmor;

        if (modifierValue == 0.0) return;
        armor.addTemporaryModifier(new EntityAttributeModifier(
                OVERDRIVE_VULNERABILITY_ID,
                modifierValue,
                EntityAttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeOverdriveVulnerability(PlayerEntity caster) {
        EntityAttributeInstance armor = caster.getAttributeInstance(EntityAttributes.ARMOR);
        if (armor == null) return;
        armor.removeModifier(OVERDRIVE_VULNERABILITY_ID);
    }

    private static void ensureOverdriveTickRegistered() {
        if (overdriveTickRegistered) return;
        overdriveTickRegistered = true;
        ServerTickEvents.END_WORLD_TICK.register(WindWandItem::tickOverdrive);
    }
}
