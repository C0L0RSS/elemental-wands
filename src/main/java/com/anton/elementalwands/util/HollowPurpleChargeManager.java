package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.registry.ModSounds;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class HollowPurpleChargeManager {

    private record ChargeState(int startTick, double startY, double targetY, double x, double z) {
    }

    private static final Map<RegistryKey<World>, Map<UUID, ChargeState>> CHARGES = new HashMap<>();

    private static final int ASCENT_TICKS = 60;
    private static final int HOLD_TICKS = 10;
    private static final int TOTAL_TICKS = ASCENT_TICKS + HOLD_TICKS;
    private static final double ASCENT_HEIGHT = 10.0;
    private static final double ORB_ANCHOR_OFFSET_Y = 3.0;
    private static final Identifier MOVEMENT_LOCK = Identifier.of("elementalwands", "hollow_purple_charge");
    private static final EntityAttributeModifier MOVEMENT_MODIFIER = new EntityAttributeModifier(
            MOVEMENT_LOCK, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private HollowPurpleChargeManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(HollowPurpleChargeManager::tickWorld);
        ServerPlayerEvents.LEAVE.register(HollowPurpleChargeManager::cancel);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive) -> {
            cancel(oldPlayer);
            releaseMovementLock(newPlayer);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination) -> cancel(player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) cancel(player);
            CHARGES.clear();
        });
        // Committed casting cannot be bypassed with a pearl, food, shield or block interaction.
        UseItemCallback.EVENT.register((player,world,hand) -> world instanceof ServerWorld serverWorld
                && isCharging(serverWorld,player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> world instanceof ServerWorld serverWorld
                && isCharging(serverWorld,player) ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean startCharge(ServerWorld world, PlayerEntity caster) {
        if (!caster.isAlive() || caster.isSpectator()) {
            return false;
        }

        Map<UUID, ChargeState> byCaster = CHARGES.computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>());
        if (byCaster.containsKey(caster.getUuid())) {
            return false;
        }

        double bottomLimit = world.getBottomY() + 1.0;
        double topLimit = world.getTopYInclusive() - 3.0;
        double startY = MathHelper.clamp(caster.getY(), bottomLimit, topLimit);
        double targetY = MathHelper.clamp(startY + ASCENT_HEIGHT, bottomLimit, topLimit);

        int now = world.getServer().getTicks();
        byCaster.put(caster.getUuid(), new ChargeState(now, startY, targetY, caster.getX(), caster.getZ()));

        caster.stopUsingItem();
        caster.stopRiding();
        lockMovementSpeed(caster);
        caster.setSprinting(false);
        caster.fallDistance = 0.0f;
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 8, 0, false, false, false));

        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_CHARGE, SoundCategory.PLAYERS,
                1.0f, 0.75f);
        // Clients draw the whole charge (two stars closing in above the caster) from this one message.
        BlockSpaceEvents.send(world, BlockSpaceEvents.CHARGE, caster.getId(), caster.getEntityPos());
        return true;
    }

    /** Lifecycle/encounter cleanup only; voluntary item changes never interrupt a committed cast. */
    public static void cancel(PlayerEntity caster) {
        boolean removed=false;
        for (var charges:CHARGES.values()) removed|=charges.remove(caster.getUuid())!=null;
        CHARGES.entrySet().removeIf(e -> e.getValue().isEmpty());
        releaseMovementLock(caster);
        if(removed) {
            caster.setVelocity(Vec3d.ZERO);caster.fallDistance=0;
            if (caster.getEntityWorld() instanceof ServerWorld world)
                BlockSpaceEvents.send(world, BlockSpaceEvents.CHARGE_END, caster.getId(), caster.getEntityPos());
        }
    }

    public static boolean isCharging(ServerWorld world, PlayerEntity caster) {
        Map<UUID, ChargeState> byCaster = CHARGES.get(world.getRegistryKey());
        return byCaster != null && byCaster.containsKey(caster.getUuid());
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, ChargeState> byCaster = CHARGES.get(world.getRegistryKey());
        if (byCaster == null || byCaster.isEmpty()) {
            return;
        }

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, ChargeState>> it = byCaster.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChargeState> entry = it.next();
            UUID casterUuid = entry.getKey();
            ChargeState state = entry.getValue();

            PlayerEntity caster = world.getPlayerByUuid(casterUuid);
            if (caster == null || !caster.isAlive() || caster.isSpectator()) {
                if (caster != null) {
                    releaseMovementLock(caster);
                    BlockSpaceEvents.send(world, BlockSpaceEvents.CHARGE_END, caster.getId(), caster.getEntityPos());
                }
                it.remove();
                continue;
            }

            int age = now - state.startTick;
            if (age >= TOTAL_TICKS) {
                releaseMovementLock(caster);
                launchOrb(world, caster);
                it.remove();
                continue;
            }

            applyChargeMovementLock(world, caster, state, age);

            if (age > 0 && age < ASCENT_TICKS && age % 20 == 0) {
                world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_CHARGE_CHIME,
                        SoundCategory.PLAYERS, 0.32f, 0.72f + age * 0.006f);
            }
            if (age == ASCENT_TICKS) {
                world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_CHARGE_PEAK, SoundCategory.PLAYERS,
                        0.48f, 1.75f);
            }
        }

        if (byCaster.isEmpty()) {
            CHARGES.remove(world.getRegistryKey());
        }
    }

    private static void lockMovementSpeed(PlayerEntity caster) {
        var speed = caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(MOVEMENT_LOCK)) speed.addTemporaryModifier(MOVEMENT_MODIFIER);
    }

    private static void releaseMovementLock(PlayerEntity caster) {
        var speed = caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        boolean locked = speed != null && speed.hasModifier(MOVEMENT_LOCK);
        if (speed != null) speed.removeModifier(MOVEMENT_LOCK);
        var effect = caster.getStatusEffect(StatusEffects.LEVITATION);
        if (locked && effect != null && effect.getDuration() <= 8 && effect.getAmplifier() == 0)
            caster.removeStatusEffect(StatusEffects.LEVITATION);
    }

    private static void applyChargeMovementLock(ServerWorld world, PlayerEntity caster, ChargeState state, int age) {
        lockMovementSpeed(caster);
        caster.stopGliding();
        caster.stopRiding();
        if (Math.abs(caster.getX()-state.x) > .001 || Math.abs(caster.getZ()-state.z) > .001)
            caster.requestTeleport(state.x, caster.getY(), state.z);
        caster.setSprinting(false);
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 6, 0, false, false, false));

        double progress = age < ASCENT_TICKS
                ? MathHelper.clamp((age + 1) / (double) ASCENT_TICKS, 0.0, 1.0)
                : 1.0;
        double desiredY = MathHelper.lerp(progress, state.startY, state.targetY);
        double yError = desiredY - caster.getY();

        Vec3d currentVelocity = caster.getVelocity();
        double correction = age < ASCENT_TICKS
                ? MathHelper.clamp(yError * 0.25, -0.06, 0.16)
                : MathHelper.clamp(yError * 0.35, -0.08, 0.08);
        double yVelocity = MathHelper.clamp(currentVelocity.y + correction, -0.12, 0.24);

        caster.setVelocity(0.0, yVelocity, 0.0);
        caster.velocityModified = true;
        caster.fallDistance = 0.0f;
    }

    private static Vec3d orbAnchor(PlayerEntity caster) {
        return new Vec3d(caster.getX(), caster.getY() + ORB_ANCHOR_OFFSET_Y, caster.getZ());
    }

    private static void launchOrb(ServerWorld world, PlayerEntity caster) {
        Vec3d anchor = orbAnchor(caster);
        Vec3d direction = caster.getRotationVec(1.0f);
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vec3d(0.0, 0.0, 1.0);
        }

        HollowPurpleOrbEntity orb = new HollowPurpleOrbEntity(world, caster, anchor, direction.normalize());
        world.spawnEntity(orb);

        caster.fallDistance = 0.0f;
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false, true));

        // The orb's renderer draws the launch flash when it first appears.
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_LAUNCH_BOOM, SoundCategory.PLAYERS, 1.5f,
                0.75f);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_LAUNCH_RELEASE,
                SoundCategory.PLAYERS, 1.3f, 0.7f);
    }
}
