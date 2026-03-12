package com.anton.elementalwands.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
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
        // MC 1.21.10 removed setCanDestroyBlocks() from MobNavigation.
        // Setting a positive penalty for BLOCKED nodes signals to the path node maker
        // that these positions are traversable (with cost), so paths can route through
        // breakable walls rather than always detouring around them.
        this.setPathfindingPenalty(PathNodeType.BLOCKED, 8.0f);
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
        // Doors: native vanilla goal — handles door-state changes, animations, and timing.
        // Runs on all non-peaceful difficulties; priority 1 keeps it above our break goal.
        this.goalSelector.add(1, new BreakDoorGoal(this, difficulty -> difficulty != Difficulty.PEACEFUL));
        // General obstacles: path-node-integrated block breaking for everything else.
        this.goalSelector.add(2, new BreakObstacleGoal(this));
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
     * Improves on the old forward-corridor BreakBlockGoal in two ways:
     * <ol>
     *   <li>Trigger condition — fires as soon as there is a target in range <em>and</em>
     *       a breakable block lies between the entity and that target, instead of waiting
     *       until the navigator has fully given up (path finished).</li>
     *   <li>Block detection — primary scan is along the vector toward the target
     *       (accurate regardless of the entity's facing), with the navigator's current
     *       path nodes checked as a secondary sweep to catch angled obstacles.</li>
     * </ol>
     */
    private static class BreakObstacleGoal extends Goal {
        private static final float MAX_BLAST_RESISTANCE = 1200.0f;
        private static final double BREAK_RANGE_SQ = 64.0;   // 8-block activation radius
        private static final double MIN_DIST_SQ     = 4.0;   // don't break while touching
        /** Ticks per block broken (20 = 1 second). */
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
            targetPos = findBlockedNode(target);
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
                // Block is already gone — find the next obstacle
                targetPos = findBlockedNode(entity.getTarget());
                progress = 0;
                return;
            }
            if (++progress >= BREAK_TICKS) {
                if (world instanceof ServerWorld sw) {
                    sw.syncWorldEvent(WorldEvents.BLOCK_BROKEN, targetPos, Block.getRawIdFromState(state));
                }
                world.breakBlock(targetPos, true, entity);
                targetPos = findBlockedNode(entity.getTarget());
                progress = 0;
            }
        }

        /**
         * Returns the nearest breakable block that is obstructing the path to {@code target}.
         *
         * <p>Primary scan: three block columns (entity foot + 1 + 2 above) in the four-way
         * direction that most closely faces the target.  This fires even when the navigator
         * has a live path, so the zombie breaks through walls it could only route around.
         *
         * <p>Secondary scan: the next four path nodes in the navigator's current path.
         * Catches angled obstacles that the directional scan might miss when the path
         * bends around a corner just before a wall.
         */
        private BlockPos findBlockedNode(LivingEntity target) {
            if (target == null) return null;
            World world = entity.getEntityWorld();

            // --- Primary: scan toward the target ---
            Vec3d myPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            Vec3d tgtPos = new Vec3d(target.getX(), target.getY(), target.getZ());
            Vec3d dir = tgtPos.subtract(myPos).multiply(1, 0, 1);
            Direction facing = dir.lengthSquared() > 0.001
                    ? Direction.getFacing(dir.x, 0, dir.z)
                    : entity.getHorizontalFacing();

            BlockPos front = entity.getBlockPos().offset(facing);
            for (int h = 0; h < 3; h++) {
                BlockPos check = front.up(h);
                BlockState bs = world.getBlockState(check);
                if (!bs.isAir() && isBreakable(bs)) return check;
            }

            // --- Secondary: sweep upcoming path nodes ---
            Path path = entity.getNavigation().getCurrentPath();
            if (path != null && !path.isFinished()) {
                int start = path.getCurrentNodeIndex();
                int end = Math.min(start + 4, path.getLength());
                for (int i = start; i < end; i++) {
                    PathNode node = path.getNode(i);
                    BlockPos nodePos = new BlockPos(node.x, node.y, node.z);
                    // Entity body spans two block heights
                    for (int h = 0; h <= 1; h++) {
                        BlockPos check = nodePos.up(h);
                        BlockState bs = world.getBlockState(check);
                        if (!bs.isAir() && isBreakable(bs)) return check;
                    }
                }
            }

            return null;
        }

        private boolean isBreakable(BlockState state) {
            Block block = state.getBlock();
            if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) return false;
            if (block.getBlastResistance() >= MAX_BLAST_RESISTANCE) return false;
            return state.isIn(BlockTags.PICKAXE_MINEABLE);
        }
    }
}
