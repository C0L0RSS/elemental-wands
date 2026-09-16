package com.anton.elementalwands.party;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.BlockView;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import java.util.Optional;

/** Delegate existing explosion balance while suppressing allied damage AND knockback. */
public final class WandExplosionBehavior extends ExplosionBehavior {
    private final ServerWorld world;
    private final UUID caster;
    private final ExplosionBehavior delegate;
    public WandExplosionBehavior(ServerWorld world, UUID caster, ExplosionBehavior delegate) {
        this.world = world; this.caster = caster; this.delegate = delegate == null ? new ExplosionBehavior() : delegate;
    }
    public UUID caster() { return caster; }
    @Override public boolean shouldDamage(Explosion explosion, Entity target) {
        return !WandAllies.protectedFrom(world, caster, target) && delegate.shouldDamage(explosion, target);
    }
    @Override public float getKnockbackModifier(Entity target) {
        return WandAllies.protectedFrom(world, caster, target) ? 0 : delegate.getKnockbackModifier(target);
    }
    @Override public float calculateDamage(Explosion explosion, Entity target, float exposure) {
        return WandAllies.protectedFrom(world, caster, target) ? 0 : delegate.calculateDamage(explosion, target, exposure);
    }
    @Override public Optional<Float> getBlastResistance(Explosion explosion, BlockView world, BlockPos pos, BlockState block, FluidState fluid) {
        return delegate.getBlastResistance(explosion, world, pos, block, fluid);
    }
    @Override public boolean canDestroyBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState block, float power) {
        return delegate.canDestroyBlock(explosion, world, pos, block, power);
    }
}
