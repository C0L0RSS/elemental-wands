package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Harmless thrown acorn. Enemy/wall contacts drop it; only supported ground grows the tree. */
public final class OvergrowthSeedEntity extends Entity {
    private final PositionInterpolator interpolator = new PositionInterpolator(this, 2);
    private PlayerEntity caster;
    private ItemStack chargedWand;
    private boolean dropping, resolved;
    private SpellCastVisuals.Wake wake = SpellCastVisuals.Wake.NONE;

    public OvergrowthSeedEntity(EntityType<? extends OvergrowthSeedEntity> type, World world) {
        super(type, world); setNoGravity(true);
    }
    public void launch(PlayerEntity owner, ItemStack stack) {
        caster = owner; chargedWand = stack;
        setPosition(owner.getEyePos().add(0, -.12, 0));
        setVelocity(owner.getRotationVec(1).multiply(OvergrowthThrowRules.SPEED));
        wake = SpellCastVisuals.Wake.from(owner, getEntityPos(), getVelocity());
    }
    @Override public PositionInterpolator getInterpolator() { return interpolator; }
    @Override protected void initDataTracker(DataTracker.Builder builder) {}
    @Override public void tick() {
        super.tick();
        if (getEntityWorld().isClient()) { interpolator.tick(); return; }
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (caster == null || !caster.isAlive() || caster.isRemoved() || caster.getEntityWorld() != world
                || world.getPlayerByUuid(caster.getUuid()) != caster
                || EWAttachments.getAffinity(caster) != WizardAffinity.NATURE
                || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(caster)) { discard(); return; }
        Vec3d from = getEntityPos(), to = from.add(getVelocity());
        if (age > OvergrowthThrowRules.FLIGHT_LIMIT || !world.isChunkLoaded(BlockPos.ofFloored(to))
                || !world.getWorldBorder().contains(BlockPos.ofFloored(to))
                || to.y < world.getBottomY() || to.y > world.getTopYInclusive()) { fizzle(world); return; }
        var hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, this) {
            @Override public net.minecraft.util.shape.VoxelShape getBlockShape(net.minecraft.block.BlockState state,
                    BlockView view, BlockPos pos) {
                return ModSpellBlocks.isNatureGrowth(state) ? net.minecraft.util.shape.VoxelShapes.empty()
                        : super.getBlockShape(state, view, pos);
            }
        });
        Vec3d end = hit.getType() == HitResult.Type.MISS ? to : hit.getPos();
        if (!dropping) {
            var actor = net.minecraft.entity.projectile.ProjectileUtil.raycast(this, from, end,
                    getBoundingBox().stretch(getVelocity()).expand(1),
                    e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
                            && !WandAllies.protectedFrom(caster, e), from.squaredDistanceTo(end));
            if (actor != null) {
                Vec3d backwards = new Vec3d(-getVelocity().x, 0, -getVelocity().z);
                if (backwards.lengthSquared() < .001) backwards = Vec3d.fromPolar(0, caster.getYaw()).multiply(-1);
                backwards = backwards.normalize();
                Vec3d landing = actor.getPos().add(backwards.multiply(.6));
                // Leave the trunk's block outside the contacted body, including tall Guardians.
                // Otherwise the safe block-placement checks would leave a floating trunk above it.
                for (int i = 0; i < 6; i++) {
                    BlockPos cell = BlockPos.ofFloored(landing);
                    Box column = new Box(cell.getX(), actor.getEntity().getY(), cell.getZ(),
                            cell.getX()+1, actor.getEntity().getY()+actor.getEntity().getHeight(), cell.getZ()+1);
                    if (!column.intersects(actor.getEntity().getBoundingBox().expand(.05))) break;
                    landing = landing.add(backwards.multiply(.5));
                }
                setPosition(landing);
                drop(); return;
            }
        }
        if (hit.getType() != HitResult.Type.MISS) {
            if (!world.getFluidState(hit.getBlockPos()).isEmpty()) { fizzle(world); return; }
            if (hit.getSide() == Direction.UP) {
                BlockPos anchor = BlockPos.ofFloored(hit.getPos().add(0, .02, 0));
                if (!OvergrowthManager.startThrownOvergrowth(world, caster, anchor)) { fizzle(world); return; }
                resolved = true; discard(); return;
            }
            setPosition(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.08)));
            drop(); return;
        }
        setPosition(to);
        setVelocity(getVelocity().multiply(.99).add(0, -OvergrowthThrowRules.GRAVITY, 0));
        Vec3d shown = wake.at(getEntityPos());
        world.spawnParticles(ModParticles.NATURE_POLLEN, shown.x, shown.y, shown.z, 2, .06, .06, .06, .005);
    }
    private void drop() { dropping = true; setVelocity(0, -.1, 0); velocityDirty = true; }
    private void fizzle(ServerWorld world) {
        if (resolved) return;
        resolved = true;
        boolean retained = caster.getMainHandStack() == chargedWand || caster.getOffHandStack() == chargedWand;
        for (int i = 0; i < caster.getInventory().size(); i++) retained |= caster.getInventory().getStack(i) == chargedWand;
        if (retained && chargedWand.getItem() instanceof AbstractWandItem) {
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, chargedWand, data ->
                    data.putInt("elementalwands:ultimate_charge", 100));
        }
        caster.sendMessage(Text.literal(retained ? "The seed could not take root. Ultimate charge restored."
                : "The seed could not take root."), true);
        world.spawnParticles(ModParticles.NATURE_LEAF, getX(), getY(), getZ(), 12, .2, .2, .2, .02);
        discard();
    }
    @Override protected void readCustomData(ReadView view) { caster = null; }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
