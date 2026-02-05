package com.anton.elementalwands.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import com.anton.elementalwands.entity.ChillSnowballEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.FrostZoneManager;
import com.anton.elementalwands.util.BlizzardManager;

public class IceWandItem extends AbstractWandItem {

    public IceWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS))
            return;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5F,
                0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

        for (int i = 0; i < 3; i++) {
            ChillSnowballEntity snowball = new ChillSnowballEntity(ModEntities.CHILL_SNOWBALL, world);
            snowball.setOwner(caster);
            snowball.setPosition(caster.getEyePos());
            snowball.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0.0F, 1.5F, 4.0F); // 4.0F divergence for
                                                                                                // volley spread
            world.spawnEntity(snowball);
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS))
            return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        BlockPos center;
        if (hit.getType() == HitResult.Type.MISS) {
            center = BlockPos.ofFloored(hit.getPos());
        } else {
            center = BlockPos.ofFloored(hit.getPos());
        }

        FrostZoneManager.createZone(world, center, 3, 120);
        world.playSound(null, center, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS))
            return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d center = hit.getType() == HitResult.Type.MISS ? caster.getEntityPos() : hit.getPos();

        BlizzardManager.startBlizzard(world, caster, center);
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 1.0f,
                0.5f);
    }
}
