package com.anton.elementalwands.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StoneZombieEntity extends ZombieEntity implements GeoEntity {

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.stone_zombie.walk");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.stone_zombie.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StoneZombieEntity(EntityType<? extends StoneZombieEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.MAX_HEALTH, 40.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        // Swim so the zombie can exit water bodies.
        this.goalSelector.add(0, new SwimGoal(this));
        // Doors: native vanilla goal at priority 1.
        this.goalSelector.add(1, new BreakDoorGoal(this, difficulty -> difficulty != Difficulty.PEACEFUL));
        // General obstacles: breaks pickaxe-mineable blocks in the direct path to the target.
        this.goalSelector.add(2, new BreakObstacleGoal(this));
        // The vanilla ActiveTargetGoal (added by super) requires line-of-sight, so the zombie
        // ignores players it cannot directly see.  This second goal uses mustSee=false so the
        // stone zombie aggroes through walls within follow range.
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    // --- GeoEntity ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("movement", 0, state ->
                state.setAndContinue(state.isMoving() ? WALK_ANIM : IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // --- Block-breaking AI ---

    /**
     * Scans the straight-line corridor between the zombie and its target for breakable
     * blocks and removes them one at a time.  Fires whenever a target exists and there
     * is a block in the way — it does not wait for the navigator to fully give up.
     *
     * <p>Key improvements over the previous BreakBlockGoal:
     * <ul>
     *   <li>Looks up to three blocks deep so multi-block-thick walls are cleared fully.</li>
     *   <li>Aims along the vector to the target, not the entity's own facing direction.</li>
     *   <li>Fires even when the navigator has a live (detour) path, so the zombie digs
     *       straight through rather than taking a long way around.</li>
     * </ul>
     */
    private static class BreakObstacleGoal extends Goal {
        private static final float MAX_BLAST_RESISTANCE = 1200.0f;
        /** How many blocks deep toward the target to scan for obstacles. */
        private static final int SCAN_DEPTH = 3;
        /** Heights to check at each depth (zombie body = 2 blocks; +1 for blocks to climb). */
        private static final int SCAN_HEIGHT = 3;
        /** 8-block activation radius (squared). */
        private static final double BREAK_RANGE_SQ = 64.0;
        /** Minimum distance squared — don't break when already touching the target. */
        private static final double MIN_DIST_SQ = 4.0;
        /** Ticks to break one block (20 = 1 second). */
        private static final int BREAK_TICKS = 20;

        private final StoneZombieEntity entity;
        private BlockPos targetPos = null;
        private int progress = 0;

        BreakObstacleGoal(StoneZombieEntity entity) {
            this.entity = entity;
        }

        @Override
        public boolean canStart() {
            LivingEntity target = entity.getTarget();
            if (target == null) return false;
            double distSq = entity.squaredDistanceTo(target);
            if (distSq > BREAK_RANGE_SQ || distSq < MIN_DIST_SQ) return false;
            targetPos = findBreakableBlock(target);
            return targetPos != null;
        }

        @Override
        public boolean shouldContinue() {
            if (entity.getTarget() == null || targetPos == null) return false;
            BlockState state = entity.getEntityWorld().getBlockState(targetPos);
            return !state.isAir() && isBreakable(state);
        }

        @Override
        public void start() {
            progress = 0;
        }

        @Override
        public void stop() {
            targetPos = null;
            progress = 0;
        }

        @Override
        public void tick() {
            if (targetPos == null) return;
            World world = entity.getEntityWorld();
            BlockState state = world.getBlockState(targetPos);
            if (state.isAir() || !isBreakable(state)) {
                // Block is gone — immediately find the next one in the corridor
                targetPos = findBreakableBlock(entity.getTarget());
                progress = 0;
                return;
            }
            if (++progress >= BREAK_TICKS) {
                if (world instanceof ServerWorld sw) {
                    sw.syncWorldEvent(WorldEvents.BLOCK_BROKEN, targetPos, Block.getRawIdFromState(state));
                }
                world.breakBlock(targetPos, true, entity);
                targetPos = findBreakableBlock(entity.getTarget());
                progress = 0;
            }
        }

        /**
         * Sweeps up to {@code SCAN_DEPTH} blocks in the horizontal direction toward
         * {@code target} and returns the nearest breakable block (at any of
         * {@code SCAN_HEIGHT} vertical offsets), or {@code null} if the corridor is clear.
         *
         * <p>Scanning from depth 1 outward means we always target the block closest to
         * the zombie, clearing the wall layer by layer.
         */
        private BlockPos findBreakableBlock(LivingEntity target) {
            if (target == null) return null;
            World world = entity.getEntityWorld();

            Vec3d diff = new Vec3d(target.getX() - entity.getX(), 0, target.getZ() - entity.getZ());
            Direction facing = diff.lengthSquared() > 0.001
                    ? Direction.getFacing(diff.x, 0, diff.z)
                    : entity.getHorizontalFacing();

            BlockPos origin = entity.getBlockPos();
            for (int depth = 1; depth <= SCAN_DEPTH; depth++) {
                BlockPos column = origin.offset(facing, depth);
                for (int h = 0; h < SCAN_HEIGHT; h++) {
                    BlockPos check = column.up(h);
                    BlockState bs = world.getBlockState(check);
                    if (!bs.isAir() && isBreakable(bs)) return check;
                }
            }
            return null;
        }

        private boolean isBreakable(BlockState state) {
            Block block = state.getBlock();
            if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) return false;
            if (block.getBlastResistance() >= MAX_BLAST_RESISTANCE) return false;
            // Cover stone/ore (pickaxe), dirt/gravel/sand (shovel), and wood/logs (axe).
            return state.isIn(BlockTags.PICKAXE_MINEABLE)
                    || state.isIn(BlockTags.SHOVEL_MINEABLE)
                    || state.isIn(BlockTags.AXE_MINEABLE);
        }
    }
}
