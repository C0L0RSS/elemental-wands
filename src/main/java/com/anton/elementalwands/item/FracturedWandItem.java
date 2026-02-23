package com.anton.elementalwands.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class FracturedWandItem extends AbstractWandItem {

    private static final float PRIMARY_DAMAGE = 3.0f;
    private static final double PRIMARY_RANGE = 20.0;

    public FracturedWandItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        HitResult hit = raycast(world, caster, PRIMARY_RANGE);
        Vec3d start = caster.getEyePos();
        Vec3d end;

        if (hit.getType() == HitResult.Type.MISS) {
            Vec3d look = caster.getRotationVec(1.0F);
            end = start.add(look.multiply(PRIMARY_RANGE));
        } else {
            end = hit.getPos();
        }

        // Spawn soul fire flame particles along the beam
        spawnParticleLine(world, start, end, ParticleTypes.SOUL_FIRE_FLAME);

        // Apply damage if we hit an entity
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            applyDamage(world, caster, target, PRIMARY_DAMAGE);
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        caster.sendMessage(
                Text.literal("The core is fractured... find a Crystal to awaken it.")
                        .formatted(Formatting.GRAY),
                true);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        caster.sendMessage(
                Text.literal("The core is fractured... find a Crystal to awaken it.")
                        .formatted(Formatting.GRAY),
                true);
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("An empty vessel waiting for an elemental heart.")
                .formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
