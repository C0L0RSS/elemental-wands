package com.anton.elementalwands.entity;

import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Summon-only cooperative boss, with explicit passive animation review controls. */
public class FracturedGuardianEntity extends PathAwareEntity implements GeoEntity {
    private static final String CONTROLLER = "guardian";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.fractured_guardian.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.fractured_guardian.walk");
    private static final TrackedData<Long> BEAM_START = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Vector3f> BEAM_ORIGIN = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> BEAM_END = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Float> BEAM_PITCH = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> ROCK_HELD = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<BlockPos> WAVE_BLOCK = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Long> WAVE_START = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Vector3f> WAVE_ORIGIN = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Long> WAVE_TWO_START = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<BlockPos> WAVE_TWO_BLOCK = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Vector3f> WAVE_TWO_ORIGIN = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Long> LEAP_START = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<BlockPos> LEAP_BLOCK = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Vector3f> LEAP_ORIGIN = DataTracker.registerData(FracturedGuardianEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private final GuardianBeamAttack beam = new GuardianBeamAttack(this);
    private final GuardianBossCombat combat = new GuardianBossCombat(this);
    private static final String PASSIVE_TAG = "ew_guardian_passive";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // Test sessions deliberately expire on reload, disconnect, or leaving this dimension.
    private UUID followPlayer;
    private String rehearsal;
    private int rehearsalTick;

    public FracturedGuardianEntity(EntityType<? extends FracturedGuardianEntity> type, World world) {
        super(type, world);
        setPersistent();
        experiencePoints = 0;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BEAM_START, -1L);
        builder.add(WAVE_TWO_START, -1L);
        builder.add(WAVE_TWO_BLOCK, BlockPos.ORIGIN);
        builder.add(WAVE_TWO_ORIGIN, new Vector3f());
        builder.add(LEAP_START, -1L);
        builder.add(LEAP_BLOCK, BlockPos.ORIGIN);
        builder.add(LEAP_ORIGIN, new Vector3f());

        builder.add(ROCK_HELD, false);
        builder.add(WAVE_START, -1L);
        builder.add(WAVE_BLOCK, BlockPos.ORIGIN);
        builder.add(WAVE_ORIGIN, new Vector3f());
        builder.add(BEAM_ORIGIN, new Vector3f());
        builder.add(BEAM_END, new Vector3f());
        builder.add(BEAM_PITCH, 0f);
    }

    void syncBeam(long start, Vec3d origin, Vec3d end, float pitch) {
        dataTracker.set(BEAM_ORIGIN, origin.subtract(getEntityPos()).toVector3f());
        dataTracker.set(BEAM_END, end.subtract(getEntityPos()).toVector3f());
        dataTracker.set(BEAM_PITCH, pitch);
        dataTracker.set(BEAM_START, start);
    }

    void startWave(long start) { startWaveAt(start,getEntityPos(),0); }
    void startWaveAt(long start, Vec3d point, int slot) {
        BlockPos block = BlockPos.ofFloored(point);
        dataTracker.set(slot == 0 ? WAVE_BLOCK : WAVE_TWO_BLOCK,block);
        dataTracker.set(slot == 0 ? WAVE_ORIGIN : WAVE_TWO_ORIGIN,point.subtract(Vec3d.of(block)).toVector3f());
        dataTracker.set(slot == 0 ? WAVE_START : WAVE_TWO_START,start);
    }
    void clearWave() { dataTracker.set(WAVE_START,-1L); dataTracker.set(WAVE_TWO_START,-1L); }
    public float getWaveTime(float partialTick) { return getWaveTime(partialTick,0); }
    public float getWaveTime(float partialTick, int slot) {
        long start = dataTracker.get(slot == 0 ? WAVE_START : WAVE_TWO_START);
        return start < 0 ? -1000 : getEntityWorld().getTime()-start+partialTick;
    }
    public Vec3d getWaveOrigin() { return getWaveOrigin(0); }
    public Vec3d getWaveOrigin(int slot) {
        return Vec3d.of(dataTracker.get(slot == 0 ? WAVE_BLOCK : WAVE_TWO_BLOCK))
                .add(new Vec3d(dataTracker.get(slot == 0 ? WAVE_ORIGIN : WAVE_TWO_ORIGIN))).subtract(getEntityPos());
    }
    void showLeapMarker(long start, Vec3d point) {
        BlockPos block = BlockPos.ofFloored(point);
        dataTracker.set(LEAP_BLOCK,block); dataTracker.set(LEAP_ORIGIN,point.subtract(Vec3d.of(block)).toVector3f());
        dataTracker.set(LEAP_START,start);
    }
    void clearLeapMarker() { dataTracker.set(LEAP_START,-1L); }
    public float getLeapTime(float partialTick) {
        long start = dataTracker.get(LEAP_START);
        return start < 0 ? -1 : getEntityWorld().getTime()-start+partialTick;
    }
    public Vec3d getLeapTarget() { return Vec3d.of(dataTracker.get(LEAP_BLOCK)).add(new Vec3d(dataTracker.get(LEAP_ORIGIN))); }

    public boolean isHoldingRock() { return dataTracker.get(ROCK_HELD); }
    void setHoldingRock(boolean held) { dataTracker.set(ROCK_HELD, held); }

    void clearBeam() { dataTracker.set(BEAM_START, -1L); }
    public float getBeamTime(float partialTick) {
        long start = dataTracker.get(BEAM_START);
        return start < 0 ? -1 : getEntityWorld().getTime()-start+partialTick;
    }
    public Vec3d getBeamOrigin() { return new Vec3d(dataTracker.get(BEAM_ORIGIN)); }
    public Vec3d getBeamEnd() { return new Vec3d(dataTracker.get(BEAM_END)); }
    public float getBeamPitch() { return dataTracker.get(BEAM_PITCH); }

    public void fireBeamForReview(ServerPlayerEntity player) {
        stopReview();
        beam.begin(player);
    }

    void beginCombatBeam(ServerPlayerEntity player) { beam.begin(player); }
    void cancelCombatBeam() { beam.cancel(); }
    public boolean isBossAggressive() { return !getCommandTags().contains(PASSIVE_TAG); }

    public String leapStatus() { return combat.leapStatus(); }

    public void startFight() {
        stopReview();
        removeCommandTag(PASSIVE_TAG);
        combat.start();
    }

    public void testAttack(ServerPlayerEntity player, GuardianCombatRules.Attack attack) {
        stopReview();
        combat.testAttack(player, attack);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 200.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.16)
                .add(EntityAttributes.STEP_HEIGHT, 1.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    @Override
    protected void initGoals() {
        // The encounter controller chooses attacks and movement; no competing vanilla target goals.
    }

    public void followForReview(ServerPlayerEntity player) {
        stopReview();
        setAiDisabled(false); // Upgrade Guardians saved by the stationary prototype.
        getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.16);
        followPlayer = player.getUuid();
    }

    public void stopReview() {
        followPlayer = null;
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        beam.cancel();
        rehearsal = null;
        rehearsalTick = 0;
        getNavigation().stop();
        setMovementSpeed(0);
        setVelocity(0, getVelocity().y, 0);
        stopTriggeredAnim(CONTROLLER, null);
    }

    public void rehearse(String animation) {
        stopReview();
        rehearsal = animation;
        triggerAnim(CONTROLLER, animation);
    }

    @Override
    public void tick() {
        if (getEntityWorld() instanceof ServerWorld serverWorld) beam.tick(serverWorld);
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (!isAlive()) { combat.cancel(); return; }
        if (!getCommandTags().contains(PASSIVE_TAG)) combat.tick(world);
        else combat.tickReview(world);
        if (followPlayer != null) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(followPlayer);
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.getEntityWorld() != world || squaredDistanceTo(player) > 48 * 48) {
                stopReview();
            } else if (squaredDistanceTo(player) <= 5 * 5) {
                getNavigation().stop();
            } else if (age % 10 == 0) {
                getNavigation().startMovingTo(player, 1.0);
            }
        }
        if (rehearsal != null) {
            int tick = ++rehearsalTick;
            if ((rehearsal.equals("slam") && tick == 26)
                    || (rehearsal.equals("awaken") && tick == 33)) {
                stoneImpact(world);
            }
            if (rehearsal.equals("throw") && tick == 44) {
                world.playSound(null, getX(), getY()+3, getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                        SoundCategory.HOSTILE, .8f, .65f);
            }
            int duration = switch (rehearsal) {
                case "awaken" -> 84;
                case "slam" -> 56;
                case "throw" -> 72;
                default -> 0;
            };
            if (tick >= duration) rehearsal = null;
        }
    }

    @Override
    public void remove(net.minecraft.entity.Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) combat.cancel();
        super.remove(reason);
    }

    @Override
    protected void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        if (getCommandTags().contains("ew_guardian_leap_gravity")) {
            setNoGravity(false); removeCommandTag("ew_guardian_leap_gravity");
        }
        // A saved mid-cast NoAI flag must not immobilize the next encounter.
        if (!getCommandTags().contains(PASSIVE_TAG)) setAiDisabled(false);
    }

    private void stoneImpact(ServerWorld world) {
        if (!isOnGround()) return;
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_STONE_BREAK,
                SoundCategory.HOSTILE, 1.2f, .55f);
        // Sample actual support blocks; dust follows uneven ground without modifying it.
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            double x = getX() + Math.cos(angle) * 2;
            double z = getZ() + Math.sin(angle) * 2;
            BlockPos support = BlockPos.ofFloored(x, getY()-.1, z);
            BlockState state = world.getBlockState(support);
            if (!state.isAir() && !state.getCollisionShape(world, support).isEmpty()) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        x, support.getY()+1.05, z, 3, .18, .12, .18, .035);
            }
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.BLOCK_STONE_STEP, .65f, .6f);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<FracturedGuardianEntity>(CONTROLLER, 4,
                state -> {
                    if (state.controller().getTriggeredAnimation() != null) {
                        // Authored tells supply anticipation; extra blending would delay
                        // the grip, ground impact, and mouth pose behind server damage.
                        state.controller().transitionLength(0);
                        return PlayState.CONTINUE;
                    }
                    state.controller().transitionLength(4);
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }).receiveTriggeredAnimations()
                .triggerableAnim("awaken", RawAnimation.begin().thenPlay("animation.fractured_guardian.awaken"))
                .triggerableAnim("slam", RawAnimation.begin().thenPlay("animation.fractured_guardian.slam"))
                .triggerableAnim("throw", RawAnimation.begin().thenPlay("animation.fractured_guardian.throw"))
                .triggerableAnim("leap_launch", RawAnimation.begin().thenPlay("animation.fractured_guardian.leap_launch"))
                .triggerableAnim("leap_air", RawAnimation.begin().thenPlay("animation.fractured_guardian.leap_air"))
                .triggerableAnim("leap_land", RawAnimation.begin().thenPlay("animation.fractured_guardian.leap_land"))
                .triggerableAnim("beam", RawAnimation.begin().thenPlay("animation.fractured_guardian.beam")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
