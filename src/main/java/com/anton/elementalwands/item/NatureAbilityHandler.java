package com.anton.elementalwands.item;

import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.util.FlowerTurretManager;
import com.anton.elementalwands.util.FullBloomManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

public final class NatureAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 20;
    private static final int SECONDARY_COOLDOWN_TICKS = FlowerTurretManager.COOLDOWN;

    private NatureAbilityHandler() {
    }

    public static int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // In Full Bloom the caster's Seeds come faster.
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY,
                FullBloomManager.blooming(caster) ? FullBloomManager.SEED_COOLDOWN : getPrimaryCooldownTicks())) {
            return;
        }

        SeedProjectileEntity seed = new SeedProjectileEntity(world, caster);
        world.spawnEntity(seed);

        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_NATURE_SEED_CAST,
                SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    public static void castThornLash(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                "thorn_lash", com.anton.elementalwands.util.ThornLashRules.COOLDOWN)) return;
        var lash = new com.anton.elementalwands.entity.ThornLashEntity(
                com.anton.elementalwands.registry.ModEntities.THORN_LASH, world);
        lash.initialize(caster);
        world.spawnEntity(lash);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_NATURE_THORNBITE_CAST,
                SoundCategory.PLAYERS, .7f, 1f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // In Full Bloom a garden goes down twice as fast.
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY,
                FullBloomManager.blooming(caster) ? FlowerTurretManager.BLOOM_COOLDOWN : getSecondaryCooldownTicks())) return;
        // The bud is thrown; the flower takes root where it lands. A bud that finds no ground is
        // lost with its cooldown, as a missed Springbloom pod is.
        var pod = new com.anton.elementalwands.entity.SentryBloomPodEntity(
                com.anton.elementalwands.registry.ModEntities.SENTRY_BLOOM_POD, world);
        pod.launch(caster);
        world.spawnEntity(pod);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_NATURE_SENTRY_BLOOM_THROW,
                SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) return;
        FullBloomManager.cast(world, caster);
    }

    /**
     * The former ultimate, archived: throw an acorn that grows the Overgrowth oak. No spell
     * casts it now that Full Bloom is the ultimate; the oak and its rules are kept, with their
     * fixtures, for possible later use.
     */
    public static void castOvergrowth(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) return;
        var seed = new com.anton.elementalwands.entity.OvergrowthSeedEntity(
                com.anton.elementalwands.registry.ModEntities.OVERGROWTH_SEED, world);
        seed.launch(caster, stack);
        world.spawnEntity(seed);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_NATURE_OVERGROWTH_CAST,
                SoundCategory.PLAYERS, .9f, .75f);
    }
}
