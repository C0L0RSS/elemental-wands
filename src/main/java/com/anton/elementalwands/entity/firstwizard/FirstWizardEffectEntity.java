package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * What clients need to draw one of the First Wizard's spells, and nothing more: hits are decided on the server by the
 * spell, never by this entity or by a client. A spell spawns one per drawn thing (the fireball, its burning ground,
 * a Blink Rift's pool) and the client's drawer for its {@link #kind} draws it from these clocks.
 *
 * <ul>
 *   <li>{@code kind}: which drawer draws it (a spell's own constant, such as {@code "fireball"}).</li>
 *   <li>{@code start}: the world tick it appeared; {@code form}: ticks it spends forming (growing in the open) from
 *       then; {@code life}: ticks after {@code start} when the server discards it.</li>
 *   <li>{@code stage}, {@code stageAt}: a spell-defined step (0 forming, then whatever the spell needs) and the tick
 *       it began, so a client can draw a release or a burst from the right moment.</li>
 *   <li>{@code size}, {@code dir}, {@code aux}: a spell-defined size (usually a radius), a direction or velocity in
 *       blocks per tick, and a second point (a launch point, an arrival point).</li>
 *   <li>{@code owner}: the copy's entity id; an anchored effect is drawn relative to it (a spell forming over its
 *       head) and the server keeps its position there.</li>
 * </ul>
 *
 * It is never saved, takes no damage and discards itself when its life runs out or its copy is gone.
 */
public final class FirstWizardEffectEntity extends Entity {
    private static final TrackedData<String> KIND = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Long> START = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> FORM = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> STAGE_AT = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Float> SIZE = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Vector3f> DIR = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> AUX = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ANCHORED = DataTracker.registerData(FirstWizardEffectEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Server: the copy that cast it. */
    private FirstWizardEntity owner;
    /** Server: where an anchored effect sits relative to the copy's feet. */
    private Vec3d anchor = Vec3d.ZERO;

    public FirstWizardEffectEntity(EntityType<? extends FirstWizardEffectEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, "");
        builder.add(START, 0L);
        builder.add(FORM, 0);
        builder.add(LIFE, 20);
        builder.add(STAGE, 0);
        builder.add(STAGE_AT, 0L);
        builder.add(SIZE, 1f);
        builder.add(DIR, new Vector3f());
        builder.add(AUX, new Vector3f());
        builder.add(OWNER, -1);
        builder.add(ANCHORED, false);
    }

    /** Spawns an effect of this kind at a point, starting now, owned by the copy. Set its other fields before the next tick. */
    static FirstWizardEffectEntity spawn(FirstWizardEntity owner, String kind, Vec3d pos, int form, int life) {
        var e = new FirstWizardEffectEntity(ModEntities.FIRST_WIZARD_EFFECT, owner.getEntityWorld());
        e.owner = owner;
        e.setPosition(pos);
        long now = owner.getEntityWorld().getTime();
        e.dataTracker.set(KIND, kind);
        e.dataTracker.set(START, now);
        e.dataTracker.set(STAGE_AT, now);
        e.dataTracker.set(FORM, form);
        e.dataTracker.set(LIFE, life);
        e.dataTracker.set(OWNER, owner.getId());
        owner.getEntityWorld().spawnEntity(e);
        return e;
    }

    // ── Server: set by the spell ─────────────────────────────────────────────

    public FirstWizardEffectEntity size(double size) { dataTracker.set(SIZE, (float) size); return this; }
    public FirstWizardEffectEntity dir(Vec3d dir) { dataTracker.set(DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)); return this; }
    /** The second point, in world coordinates (floats: fine on an arena near the world's origin, as the End's is). */
    public FirstWizardEffectEntity aux(Vec3d point) { dataTracker.set(AUX, new Vector3f((float) point.x, (float) point.y, (float) point.z)); return this; }
    /** Moves to the next stage, from now. */
    public FirstWizardEffectEntity stage(int stage) {
        dataTracker.set(STAGE, stage);
        dataTracker.set(STAGE_AT, getEntityWorld().getTime());
        return this;
    }
    /** Lives this many ticks from its start in all. */
    public FirstWizardEffectEntity life(int ticks) { dataTracker.set(LIFE, ticks); return this; }
    /** Keeps it at this offset from the copy's feet, and has clients draw it there. */
    public FirstWizardEffectEntity anchor(Vec3d offset) {
        anchor = offset;
        dataTracker.set(ANCHORED, true);
        if (owner != null) setPosition(owner.getEntityPos().add(offset));
        return this;
    }
    /** Stops following the copy, staying where it is now. */
    public FirstWizardEffectEntity unanchor() { dataTracker.set(ANCHORED, false); return this; }

    // ── Both sides ───────────────────────────────────────────────────────────

    public String kind() { return dataTracker.get(KIND); }
    public long start() { return dataTracker.get(START); }
    public int form() { return dataTracker.get(FORM); }
    public int life() { return dataTracker.get(LIFE); }
    public int stage() { return dataTracker.get(STAGE); }
    public long stageAt() { return dataTracker.get(STAGE_AT); }
    public float size() { return dataTracker.get(SIZE); }
    public Vec3d dir() { Vector3f v = dataTracker.get(DIR); return new Vec3d(v.x, v.y, v.z); }
    public Vec3d auxPoint() { Vector3f v = dataTracker.get(AUX); return new Vec3d(v.x, v.y, v.z); }
    public int ownerId() { return dataTracker.get(OWNER); }
    public boolean anchored() { return dataTracker.get(ANCHORED); }
    /** Ticks since it appeared, with the partial tick. */
    public double age(float partial) { return getEntityWorld().getTime() - start() + partial; }
    /** Ticks since its stage began, with the partial tick. */
    public double stageAge(float partial) { return getEntityWorld().getTime() - stageAt() + partial; }
    /** How far through its forming it is, 0 to 1. */
    public double formed(float partial) { return form() <= 0 ? 1 : Math.clamp(age(partial) / form(), 0, 1); }

    @Override public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld)) return;
        if (owner == null || owner.isRemoved() || age(0) > life()) { discard(); return; }
        if (anchored()) setPosition(owner.getEntityPos().add(anchor));
    }

    /** Effects are as big as the arena; a tiny box must not cull them. */
    @Override public boolean shouldRender(double squaredDistance) { return squaredDistance < 160.0 * 160.0; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override protected void readCustomData(ReadView view) {}
    @Override protected void writeCustomData(WriteView view) {}
}
