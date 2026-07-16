package com.anton.elementalwands.item;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.registry.ModParticles;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class UniversalWandItem extends AbstractWandItem {

    private static final float FRACTURED_PRIMARY_DAMAGE = 3.0f;
    private static final double FRACTURED_PRIMARY_RANGE = 20.0;

    public UniversalWandItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        switch (EWAttachments.getAffinity(caster)) {
            case FIRE  -> FireAbilityHandler.castPrimary(world, caster, stack);
            case WIND  -> WindAbilityHandler.castPrimary(world, caster, stack);
            case STONE -> StoneAbilityHandler.castPrimary(world, caster, stack);
            case NATURE -> NatureAbilityHandler.castPrimary(world, caster, stack);
            case SPACE -> SpaceAbilityHandler.castPrimary(world, caster, stack);
            case NONE  -> castFracturedPrimary(world, caster, stack);
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        switch (EWAttachments.getAffinity(caster)) {
            case FIRE  -> FireAbilityHandler.castSecondary(world, caster, stack);
            case WIND  -> WindAbilityHandler.castSecondary(world, caster, stack);
            case STONE -> StoneAbilityHandler.castSecondary(world, caster, stack);
            case NATURE -> NatureAbilityHandler.castSecondary(world, caster, stack);
            case SPACE -> SpaceAbilityHandler.castSecondary(world, caster, stack);
            case NONE  -> sendInertMessage(caster);
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // The unlock gate used to live inside trySpendUltimateCharge; now that the
        // helper is static we enforce it here before dispatching to a handler.
        if (!isAbilityUnlocked(caster, Ability.ULTIMATE)) {
            caster.sendMessage(Text.translatable("hud.elementalwands.locked"), true);
            return;
        }

        switch (EWAttachments.getAffinity(caster)) {
            case FIRE  -> FireAbilityHandler.castUltimate(world, caster, stack);
            case WIND  -> WindAbilityHandler.castUltimate(world, caster, stack);
            case STONE -> StoneAbilityHandler.castUltimate(world, caster, stack);
            case NATURE -> NatureAbilityHandler.castUltimate(world, caster, stack);
            case SPACE -> SpaceAbilityHandler.castUltimate(world, caster, stack);
            case NONE  -> sendInertMessage(caster);
        }
    }

    private static void sendInertMessage(PlayerEntity caster) {
        caster.sendMessage(
                Text.literal("The wand is inert... choose your path.")
                        .formatted(Formatting.GRAY),
                true);
    }

    private void castFracturedPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        HitResult hit = raycast(world, caster, FRACTURED_PRIMARY_RANGE);
        Vec3d start = caster.getEyePos();
        Vec3d end;

        if (hit.getType() == HitResult.Type.MISS) {
            Vec3d look = caster.getRotationVec(1.0F);
            end = start.add(look.multiply(FRACTURED_PRIMARY_RANGE));
        } else {
            end = hit.getPos();
        }

        // Draw the fractured beam with the shared original arcane texture family.
        spawnParticleLine(world, start, end, ModParticles.ARCANE_THREAD);

        // Apply damage if we hit an entity
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            applyDamage(world, caster, target, FRACTURED_PRIMARY_DAMAGE);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, world, entity, slot);
        if (!(entity instanceof PlayerEntity player)) return;
        switch (EWAttachments.getAffinity(player)) {
            case FIRE  -> FireAbilityHandler.inventoryTick(stack, world, entity, slot);
            case WIND  -> WindAbilityHandler.inventoryTick(stack, world, entity, slot);
            case STONE -> StoneAbilityHandler.inventoryTick(stack, world, entity, slot);
            case NATURE -> NatureAbilityHandler.inventoryTick(stack, world, entity, slot);
            default    -> {}
        }
    }

    @Override
    public int getPrimaryCooldownTicks() {
        // Per-affinity primary cooldowns are static on the handler classes; read the
        // current holder's affinity so the HUD cooldown pie shows the right value.
        // Note: HUD calls this via AbstractWandItem so `this` is the item, not a
        // handler — we can't read affinity here without a player reference. Return
        // default, then handlers override internally with their own getPrimaryCooldownTicks
        // when tryStartCooldown is invoked.
        return DEFAULT_PRIMARY_COOLDOWN_TICKS;
    }

    @Override
    public int getSecondaryCooldownTicks() {
        return DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }
}
