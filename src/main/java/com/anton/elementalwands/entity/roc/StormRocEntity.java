package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SpellCombat;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Giant, summon-only Roc. The server owns state, damage, admission and authored movement. */
public final class StormRocEntity extends PathAwareEntity implements GeoEntity, WandBoss {
    private static final TrackedData<String> POSE = DataTracker.registerData(StormRocEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Long> POSE_AT = DataTracker.registerData(StormRocEntity.class, TrackedDataHandlerRegistry.LONG);
    /** Charged by the storm: phase two's sky, clouds and brighter crackle follow this on clients. */
    private static final TrackedData<Boolean> CHARGED = DataTracker.registerData(StormRocEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** The player it is striking at or attacking, whom clients turn its head toward; -1 for none. */
    private static final TrackedData<Integer> LOOK = DataTracker.registerData(StormRocEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private record Hurt(long until, float amount) {}
    private record Hit(UUID attacker, int projectile, String type, long tick) {}
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final StormRocCombat combat = new StormRocCombat(this);
    private final List<RocBodyPartEntity> parts = new ArrayList<>();
    private final Map<UUID, Hurt> hurt = new HashMap<>();
    private final Map<Hit, Float> sameTickHits = new HashMap<>();

    public StormRocEntity(EntityType<? extends StormRocEntity> type, World world) {
        super(type, world);
        setPersistent();
        setNoGravity(true);
        noClip = true;
        experiencePoints = 0;
        // Its poses follow the server clock exactly, so its position must too. The default
        // three-tick blend drew a charging Roc up to ten blocks behind where it hit.
        getInterpolator().setLerpDuration(1);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, StormRocRules.health(1))
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.MOVEMENT_SPEED, 0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0)
                .add(EntityAttributes.FOLLOW_RANGE, 256);
    }

    @Override protected void initGoals() {}

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, "perched");
        builder.add(POSE_AT, 0L);
        builder.add(CHARGED, false);
        builder.add(LOOK, -1);
    }

    public String poseName() { return dataTracker.get(POSE); }
    public float poseTicks(float partial) { return getEntityWorld().getTime() - dataTracker.get(POSE_AT) + partial; }
    public long poseStart() { return dataTracker.get(POSE_AT); }
    public int lookTarget() { return dataTracker.get(LOOK); }
    void lookAt(Entity target) { dataTracker.set(LOOK, target == null ? -1 : target.getId()); }

    public boolean charged() { return dataTracker.get(CHARGED); }
    void charged(boolean charged) { dataTracker.set(CHARGED, charged); }

    void pose(String name) { pose(name, getEntityWorld().getTime()); }

    /** A clip that started at start: a clip can resume from where it left off. */
    void pose(String name, long start) {
        dataTracker.set(POSE, name);
        dataTracker.set(POSE_AT, start);
    }

    public void startFight(BlockPos centre, List<ServerPlayerEntity> players) { hurt.clear(); combat.start(centre, players); }
    public void stopFight() { combat.cancel(); clearParts(); }
    /** The fight is over: for now that means it was defeated in phase two. */
    public boolean fightComplete() { return combat.stage() == StormRocRules.Stage.COMPLETE; }
    public boolean defeated() { return combat.defeated(); }
    public boolean phaseTwo() { return combat.phaseTwo(); }
    public StormRocRules.Stage stage() { return combat.stage(); }
    /** Operators skip a fight straight to the phase change for playtests. */
    public boolean skipToPhaseTwo() { return combat.skipToPhaseTwo(); }
    public boolean fighting() { return combat.active(); }
    public boolean participant(UUID id) { return combat.participant(id); }
    public void removeParticipant(UUID id) { combat.removeParticipant(id); }
    public String status() { return combat.status(); }
    StormRocCombat combat() { return combat; }

    @Override public boolean eligible(ServerPlayerEntity p) {
        return participant(p.getUuid()) && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.getEntityWorld() == getEntityWorld();
    }

    @Override public boolean isBossAggressive() { return fighting(); }

    /** Operator/fixture rehearsals still run the real server timelines and damage tests. */
    public void testAction(ServerPlayerEntity player, StormRocRules.Action action) { combat.rehearse(player, action); }
    public RocEffectEntity testStrike(Vec3d position, boolean large) { return combat.strike(position, large); }
    public void testDive(Vec3d mark) { combat.dive(mark); }
    public RocEffectEntity testTornado(Vec3d point) { return combat.tornado(point); }
    public void testChain() { combat.chain(); }

    @Override public double getAttributeValue(RegistryEntry<EntityAttribute> attribute) {
        // Vanilla caps max health at 1024; larger parties need more, like the other bosses.
        double value = super.getAttributeValue(attribute);
        if (attribute.equals(EntityAttributes.MAX_HEALTH) && getAttributeInstance(attribute).getBaseValue() > 1024)
            return Math.max(value, getAttributeInstance(attribute).getBaseValue());
        return value;
    }

    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(world, source, amount);
        if (!fighting() || fightComplete()) return false;
        // No health can be lost at this landing gate. Reject the hit rather than report
        // accepted damage to spell callbacks, which would otherwise grant free charge.
        if (getHealth() <= combat.healthFloor() + .001f) return false;
        Entity attacker = source.getAttacker();
        if (attacker == null) attacker = SpellCombat.burnOwner(this, source);
        if (!(attacker instanceof ServerPlayerEntity player) || !eligible(player)) return false;
        // Shared across body/head/neck. A single AoE cannot multiply its damage by hitting parts,
        // while different teammates retain independent vanilla-like hurt windows.
        long now = world.getTime();
        sameTickHits.keySet().removeIf(h -> h.tick != now);
        Hit key = new Hit(player.getUuid(), source.getSource() == null ? -1 : source.getSource().getId(), source.getName(), now);
        Float already = sameTickHits.get(key);
        if (already != null && already >= amount) return false;
        float requested = amount;
        // Cooldown-bypassing effects may encounter overlapping parts with different prior
        // contact totals. Only the increase belongs to this attack, never a second full hit.
        if (already != null && source.isIn(DamageTypeTags.BYPASSES_COOLDOWN)) amount -= already;
        hurt.values().removeIf(h -> h.until <= now);
        Hurt previous = hurt.get(player.getUuid());
        timeUntilRegen = previous == null ? 0 : 10 + (int) (previous.until - now);
        lastDamageTaken = previous == null ? 0 : previous.amount;
        float before = getHealth();
        boolean accepted = super.damage(world, source, amount);
        if (accepted) {
            sameTickHits.put(key, requested);
            hurt.put(player.getUuid(), new Hurt(previous == null ? now + 10 : previous.until, lastDamageTaken));
            combat.damaged(player, before - getHealth());
        }
        return accepted;
    }

    @Override protected void applyDamage(ServerWorld world, DamageSource source, float amount) {
        float before = getHealth();
        super.applyDamage(world, source, amount);
        if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && fighting())
            setHealth(Math.max(getHealth(), Math.min(before, combat.healthFloor())));
    }

    /** Model-space X is mirrored by Gecko; the same conversion positions all authored sockets. */
    public Vec3d socket(String key) { return modelToWorld(StormRocPose.socket(poseName(), poseTicks(0), key)); }

    public Vec3d modelToWorld(Vec3d point) {
        double a = Math.toRadians(getYaw()), c = Math.cos(a), s = Math.sin(a);
        return getEntityPos().add(point.x * c + point.z * s, point.y, point.x * s - point.z * c);
    }

    void face(Vec3d target) {
        Vec3d d = target.subtract(getEntityPos());
        if (d.horizontalLengthSquared() > 1e-4) yaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
    }

    void yaw(float yaw) {
        setYaw(MathHelper.wrapDegrees(yaw));
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
    }

    @Override public void tick() {
        setVelocity(Vec3d.ZERO);
        super.tick();
        setNoGravity(true);
        noClip = true;
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (!isAlive()) { stopFight(); return; }
        combat.tick(world);
        if (fighting()) updateParts(world);
    }

    private void updateParts(ServerWorld world) {
        if (parts.isEmpty()) for (String name : List.of("head", "neck")) {
            var part = new RocBodyPartEntity(ModEntities.ROC_BODY_PART, world);
            part.bind(this, name);
            world.spawnEntity(part);
            parts.add(part);
        }
        // The torso includes its ground footprint so planted thorns can find a grounded Roc;
        // the enormous wings remain outside this hurt volume.
        Vec3d chest = socket("body");
        setBoundingBox(new Box(chest.x - 2.6, Math.min(getY(), chest.y - 1.8), chest.z - 2.8, chest.x + 2.6, chest.y + 2.2, chest.z + 2.8));
        for (var p : parts) p.follow();
    }

    private void clearParts() {
        for (var p : parts) p.discard();
        parts.clear();
    }

    @Override public void onRemove(Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) stopFight();
        super.onRemove(reason);
    }

    @Override public boolean isPushable() { return false; }
    @Override public void addVelocity(double x, double y, double z) {}
    @Override public boolean doesRenderOnFire() { return false; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
