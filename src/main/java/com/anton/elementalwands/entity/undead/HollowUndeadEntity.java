package com.anton.elementalwands.entity.undead;

import com.anton.elementalwands.entity.necromancer.NecromancerMinion;
import java.util.EnumSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shared body of the Hollow undead family. Wild copies spawn at night, burn in daylight and
 * drop vanilla-style loot; copies raised by a Necromancer follow {@link NecromancerMinion}.
 * Both claw out of the ground with their rise clip and strike on their attack clip's hit frame.
 */
public abstract class HollowUndeadEntity extends HostileEntity implements GeoEntity, NecromancerMinion {
    private static final TrackedData<Byte> ACTION = DataTracker.registerData(HollowUndeadEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> ACTION_ID = DataTracker.registerData(HollowUndeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final byte NONE = 0, RISE = 1, ATTACK = 2;
    /**
     * Ticks the client blends into a clip before its clock starts. Attack hits and aim are
     * delayed by this much so damage lands on the frame players see.
     */
    public static final int BLEND = 4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final State minion = new State();
    private final RawAnimation idle, walk, rise, attack, death;
    private int actionTicks, riseTick, seenAction = -1;

    protected HollowUndeadEntity(EntityType<? extends HollowUndeadEntity> type, World world, String name) {
        super(type, world);
        String clip = "animation.hollow_" + name + ".";
        idle = RawAnimation.begin().thenLoop(clip + "idle");
        walk = RawAnimation.begin().thenLoop(clip + "walk");
        rise = RawAnimation.begin().thenPlayAndHold(clip + "rise");
        attack = RawAnimation.begin().thenPlay(clip + "attack");
        death = RawAnimation.begin().thenPlayAndHold(clip + "death");
    }

    /** Rise clip length; the body is inert until it ends. */
    protected abstract int riseTicks();
    /** Death clip length; the corpse stays until it has settled. */
    protected abstract int deathTicks();
    /** Travel speed (blocks/s) the walk clip was authored at. */
    protected abstract double stride();

    // ── Combat hooks for the strike goal ────────────────────────────────────

    /** Close enough (and otherwise able) to start the attack clip now. */
    protected abstract boolean canBegin(LivingEntity target, boolean seen);
    /** Should keep walking toward the target instead of holding position. */
    protected boolean wantsCloser(LivingEntity target, boolean seen) { return true; }
    /** Turns toward the target while the attack clip plays. */
    protected abstract void aim(LivingEntity target, int tick);
    /** The clip's hit frame. */
    protected abstract void strike(ServerWorld world, LivingEntity target);
    protected void onBegin(ServerWorld world, LivingEntity target) {}

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, strikeGoal());
        goalSelector.add(5, new WanderAroundFarGoal(this, .8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8));
        goalSelector.add(6, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    protected abstract StrikeGoal strikeGoal();

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ACTION, NONE).add(ACTION_ID, 0);
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, EntityData data) {
        EntityData result = super.initialize(world, difficulty, reason, data);
        if (reason != SpawnReason.CHUNK_GENERATION) startRise();
        return result;
    }

    // ── Actions shared with the client animation controller ─────────────────

    private void play(byte action, int ticks) {
        dataTracker.set(ACTION, action);
        dataTracker.set(ACTION_ID, dataTracker.get(ACTION_ID) + 1);
        actionTicks = ticks;
    }

    private void endAction() {
        dataTracker.set(ACTION, NONE);
        actionTicks = 0;
    }

    public boolean isBound() { return minion.bound(); }
    public boolean isStriking() { return dataTracker.get(ACTION) == ATTACK; }
    @Override public State minionState() { return minion; }
    @Override public boolean isRising() { return dataTracker.get(ACTION) == RISE; }

    @Override
    public void startRise() {
        play(RISE, riseTicks());
        riseTick = 0;
        // Goal controls rather than NoAI, so a body saved mid-rise never reloads frozen.
        for (Goal.Control control : Goal.Control.values()) { goalSelector.disableControl(control); targetSelector.disableControl(control); }
        getNavigation().stop();
    }

    private void finishRise(ServerWorld world) {
        for (Goal.Control control : Goal.Control.values()) { goalSelector.enableControl(control); targetSelector.enableControl(control); }
        NecromancerMinion.finishRise(this, world);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        byte action = dataTracker.get(ACTION);
        if (action == RISE && isAlive()) riseEffects(world, riseTick++);
        if (action != NONE && --actionTicks <= 0) {
            endAction();
            if (action == RISE) finishRise(world);
        }
        NecromancerMinion.tick(this);
    }

    /** Soil bursts while the body claws free; the Necromancer's summons also shed soul wisps. */
    private void riseEffects(ServerWorld world, int tick) {
        if (tick == 0) world.playSound(null, getBlockPos(), isBound() ? SoundEvents.PARTICLE_SOUL_ESCAPE.value() : SoundEvents.BLOCK_ROOTED_DIRT_BREAK,
                SoundCategory.HOSTILE, 1.2f, .7f);
        if (tick > riseTicks() * .6) return;
        BlockState ground = world.getBlockState(BlockPos.ofFloored(getX(), getY() - .5, getZ()));
        if (tick % 2 == 0 && !ground.isAir())
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), getX(), getY() + .05, getZ(),
                    6, getWidth() * .5, .02, getWidth() * .5, .05);
        if (isBound() && tick % 3 == 0)
            world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + .2, getZ(), 2, .3, .1, .3, .02);
    }

    @Override
    public void tickMovement() {
        if (isAlive() && !isBound() && isAffectedByDaylight()) setOnFireFor(8);
        super.tickMovement();
    }

    /** Lets the death clip finish before the body puffs away. */
    @Override
    protected void updatePostDeath() {
        if (++deathTime >= deathTicks() + 6 && getEntityWorld() instanceof ServerWorld world && !isRemoved()) {
            world.sendEntityStatus(this, EntityStatuses.ADD_DEATH_PARTICLES);
            remove(RemovalReason.KILLED);
        }
    }

    // ── Geometry helpers for strikes ────────────────────────────────────────

    /** A point on the body in blocks (right, up, forward), following the body yaw. */
    protected Vec3d socket(double[] offset) {
        double yaw = Math.toRadians(bodyYaw), sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new Vec3d(getX() - cos * offset[0] - sin * offset[2], getY() + offset[1], getZ() - sin * offset[0] + cos * offset[2]);
    }

    protected static double distance(Box box, Vec3d point) {
        double x = point.x - MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = point.y - MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = point.z - MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Gap between the two bodies on the ground plane. */
    protected double reachTo(LivingEntity target) {
        return Math.max(0, Math.hypot(target.getX() - getX(), target.getZ() - getZ()) - (getWidth() + target.getWidth()) / 2);
    }

    /** Turns the whole body toward the target, offset clockwise by the given degrees. */
    protected void face(LivingEntity target, float offset, float maxTurn) {
        float want = (float)Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90 + offset;
        float yaw = MathHelper.stepUnwrappedAngleTowards(getYaw(), want, maxTurn);
        setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
    }

    /** Who a swing may hurt: anything this body could target, never the caster's side or its own kind. */
    protected boolean hittable(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || entity instanceof HollowUndeadEntity || isTeammate(entity)) return false;
        if (entity instanceof PlayerEntity player) return !player.isCreative() && !player.isSpectator();
        return entity == getTarget() || entity instanceof IronGolemEntity;
    }

    // ── Minion rules (no effect on wild bodies) ─────────────────────────────

    @Override protected boolean isInSameTeam(Entity other) { return NecromancerMinion.sameSide(this, other) || super.isInSameTeam(other); }
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        return NecromancerMinion.ignores(this, source) || super.isInvulnerableTo(world, source);
    }
    @Override public boolean isPushable() { return !isRising() && super.isPushable(); }
    @Override protected boolean shouldDropLoot(ServerWorld world) { return !isBound() && super.shouldDropLoot(world); }
    @Override protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        if (!isBound()) super.dropEquipment(world, source, causedByPlayer);
    }
    @Override protected int getExperienceToDrop(ServerWorld world) { return isBound() ? 0 : super.getExperienceToDrop(world); }
    @Override public boolean shouldSave() { return !isBound() && super.shouldSave(); }
    @Override public boolean canImmediatelyDespawn(double distance) { return !isBound() && super.canImmediatelyDespawn(distance); }

    // ── GeckoLib ────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new PacedController(test -> {
            test.controller().transitionLength(BLEND);
            test.setControllerSpeed(1);
            if (isDead()) return test.setAndContinue(death);
            byte action = dataTracker.get(ACTION);
            if (action != NONE) {
                int id = dataTracker.get(ACTION_ID);
                if (id != seenAction) { seenAction = id; test.controller().forceAnimationReset(); }
                // A rise starts underground at once; blending from the bind pose would flash the body above it.
                if (action == RISE) test.controller().transitionLength(0);
                return test.setAndContinue(action == RISE ? rise : attack);
            }
            if (!test.isMoving()) return test.setAndContinue(idle);
            // Limb speed is 4x blocks/tick moved. Walks are authored at chase speed, so playing them at
            // travel speed / authored speed keeps planted feet still from a wander (~0.65x) to a chase (1x).
            test.setControllerSpeed((float)MathHelper.clamp(limbAnimator.getSpeed() * 5 / stride(), .2, 2));
            return test.setAndContinue(walk);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /**
     * GeckoLib multiplies the whole elapsed time by the current speed, so a changing walk speed
     * would jump the pose. This controller accumulates time at each frame's speed instead.
     */
    private static final class PacedController extends AnimationController<HollowUndeadEntity> {
        private double lastTick, elapsed;

        PacedController(AnimationStateHandler<HollowUndeadEntity> handler) { super("body", BLEND, handler); }

        @Override
        protected double adjustTick(double tick) {
            if (shouldResetTick) {
                if (getAnimationState() != State.STOPPED) tickOffset = tick;
                shouldResetTick = false;
                lastTick = tick;
                elapsed = 0;
                return 0;
            }
            elapsed += animationSpeed * Math.max(tick - lastTick, 0);
            lastTick = tick;
            return elapsed;
        }
    }

    // ── Strike goal ─────────────────────────────────────────────────────────

    /** Chases the target, then plants and plays the attack clip; the creature strikes on its hit frame. */
    protected final class StrikeGoal extends Goal {
        private final double speed;
        private final int hitTick, busyTicks, cooldown;
        private int tick = -1, repath, readyAge;

        public StrikeGoal(double speed, int hitTick, int busyTicks, int cooldown) {
            this.speed = speed; this.hitTick = hitTick; this.busyTicks = busyTicks; this.cooldown = cooldown;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private boolean valid(LivingEntity target) { return target != null && target.isAlive() && canTarget(target); }

        @Override public boolean canStart() { return valid(getTarget()); }
        @Override public boolean shouldContinue() { return tick >= 0 || valid(getTarget()); }
        @Override public boolean shouldRunEveryTick() { return true; }
        @Override public void start() { tick = -1; repath = 0; setAttacking(true); }

        @Override
        public void stop() {
            if (tick >= 0 && isStriking()) endAction();
            tick = -1;
            setAttacking(false);
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (!(getEntityWorld() instanceof ServerWorld world)) return;
            LivingEntity target = getTarget();
            if (tick >= 0) {
                // Clip time as the client sees it, after the blend into the clip.
                int clip = ++tick - BLEND;
                if (clip >= 0 && valid(target)) aim(target, clip);
                if (clip == hitTick) strike(world, valid(target) ? target : null);
                if (clip >= busyTicks) { tick = -1; readyAge = age + cooldown; }
                return;
            }
            if (!valid(target)) return;
            boolean seen = getVisibilityCache().canSee(target);
            getLookControl().lookAt(target, 30, 30);
            if (seen && age >= readyAge && canBegin(target, seen)) {
                getNavigation().stop();
                tick = 0;
                play(ATTACK, busyTicks + BLEND);
                onBegin(world, target);
                return;
            }
            if (--repath <= 0 || getNavigation().isIdle()) {
                repath = 4 + getRandom().nextInt(7);
                if (wantsCloser(target, seen)) getNavigation().startMovingTo(target, speed);
                else getNavigation().stop();
            }
        }
    }
}
