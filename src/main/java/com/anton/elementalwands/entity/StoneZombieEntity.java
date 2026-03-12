package com.anton.elementalwands.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

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
        this.goalSelector.add(1, new BreakBlockGoal(this));
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

    private static class BreakBlockGoal extends Goal {
        private static final int BREAK_INTERVAL = 60;
        private static final double BREAK_RANGE_SQ = 64.0;
        private static final float MAX_BLAST_RESISTANCE = 1200.0f;

        private final StoneZombieEntity entity;
        private final List<BlockPos> queue = new ArrayList<>();
        private int timer = 0;

        BreakBlockGoal(StoneZombieEntity entity) {
            this.entity = entity;
        }

        @Override
        public boolean canStart() {
            LivingEntity target = entity.getTarget();
            if (target == null) return false;
            double distSq = entity.squaredDistanceTo(target);
            if (distSq > BREAK_RANGE_SQ || distSq < 2.25) return false;
            Path path = entity.getNavigation().getCurrentPath();
            if (path != null && !path.isFinished()) return false;
            queue.clear();
            queue.addAll(findBreakableBlocks(target));
            return !queue.isEmpty();
        }

        @Override
        public boolean shouldContinue() {
            return entity.getTarget() != null && !queue.isEmpty();
        }

        @Override
        public void start() {
            timer = 0;
        }

        @Override
        public void stop() {
            queue.clear();
            timer = 0;
        }

        @Override
        public void tick() {
            timer++;
            if (timer < BREAK_INTERVAL) return;
            timer = 0;
            if (queue.isEmpty()) return;

            BlockPos pos = queue.remove(0);
            World world = entity.getEntityWorld();
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !isBreakable(state)) return;

            if (world instanceof ServerWorld sw) {
                sw.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
            }
            world.breakBlock(pos, true, entity);
        }

        private List<BlockPos> findBreakableBlocks(LivingEntity target) {
            List<BlockPos> result = new ArrayList<>();
            Vec3d myPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            Vec3d tgtPos = new Vec3d(target.getX(), target.getY(), target.getZ());
            Vec3d dir = tgtPos.subtract(myPos).multiply(1, 0, 1);
            if (dir.lengthSquared() < 0.001) return result;
            dir = dir.normalize();
            Direction facing = Direction.getFacing(dir.x, 0, dir.z);
            Direction side = facing.rotateYClockwise();
            BlockPos front = entity.getBlockPos().offset(facing);
            for (int s = 0; s < 2; s++) {
                BlockPos base = front.offset(side, s);
                for (int h = 0; h < 3; h++) {
                    BlockPos check = base.up(h);
                    BlockState blockState = entity.getEntityWorld().getBlockState(check);
                    if (!blockState.isAir() && isBreakable(blockState)) {
                        result.add(check);
                    }
                }
            }
            return result;
        }

        private boolean isBreakable(BlockState state) {
            Block block = state.getBlock();
            if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) return false;
            if (block.getBlastResistance() >= MAX_BLAST_RESISTANCE) return false;
            return state.isIn(BlockTags.PICKAXE_MINEABLE);
        }
    }
}
