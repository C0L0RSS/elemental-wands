package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.util.BlockNatureEvents;
import com.anton.elementalwands.util.FlowerTurretManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The bud Sentry Bloom throws. It is lobbed in an arc, as a Gravity Well is, and a flower takes
 * root where it comes down: on the ground it lands on, on the ground under a wall or ceiling it
 * strikes, or at the feet of an enemy it hits on the way. It is harmless and never saved.
 */
public final class SentryBloomPodEntity extends Entity {
    /** The throw: along the caster's aim with a little lift, then falling. A level throw lands about twelve blocks out. */
    public static final double SPEED = 1.05, LIFT = 0.22, GRAVITY = 0.06, DRAG = 0.99;
    /** A bud still in the air after this long is lost. */
    public static final int FLIGHT_LIMIT = 60;

    private final PositionInterpolator interpolator = new PositionInterpolator(this, 2);
    private PlayerEntity owner;

    public SentryBloomPodEntity(EntityType<? extends SentryBloomPodEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public void launch(PlayerEntity caster) {
        owner = caster;
        setPosition(caster.getEyePos().add(0, -0.12, 0));
        setVelocity(caster.getRotationVec(1.0f).multiply(SPEED).add(0, LIFT, 0));
    }

    @Override public PositionInterpolator getInterpolator() { return interpolator; }
    @Override protected void initDataTracker(DataTracker.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (getEntityWorld().isClient()) { interpolator.tick(); return; }
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.isSpectator() || owner.getEntityWorld() != world
                || EWAttachments.getAffinity(owner) != WizardAffinity.NATURE || !GuardianArenaManager.canCast(owner)) { discard(); return; }
        Vec3d from = getEntityPos(), to = from.add(getVelocity());
        BlockPos ahead = BlockPos.ofFloored(to);
        if (age > FLIGHT_LIMIT || !world.isChunkLoaded(ahead) || !world.getWorldBorder().contains(ahead)
                || to.y < world.getBottomY() || to.y > world.getTopYInclusive()) { discard(); return; }
        var block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
        Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
        var enemy = ProjectileUtil.raycast(this, from, end, getBoundingBox().stretch(to.subtract(from)).expand(1),
                e -> FlowerTurretManager.hostile(e, owner), from.squaredDistanceTo(end));
        BlockPos cell;
        if (enemy != null) { end = enemy.getPos(); cell = BlockPos.ofFloored(end); }
        else if (block.getType() == HitResult.Type.MISS) {
            setPosition(to);
            setVelocity(getVelocity().multiply(DRAG).add(0, -GRAVITY, 0));
            return;
        }
        // Water and lava swallow it.
        else if (!world.getFluidState(block.getBlockPos()).isEmpty()) cell = null;
        // A wall or ceiling: the flower takes the ground in front of or under it.
        else cell = block.getSide() == Direction.UP ? block.getBlockPos() : block.getBlockPos().offset(block.getSide());
        BlockPos spot = cell == null ? null : FlowerTurretManager.rootSpot(world, cell);
        if (spot == null || !FlowerTurretManager.plant(world, owner, spot)) {
            BlockNatureEvents.send(world, BlockNatureEvents.POD_HIT, end);
            owner.sendMessage(Text.literal("No ground there for a flower."), true);
        }
        discard();
    }

    @Override protected void readCustomData(ReadView view) { owner = null; }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
