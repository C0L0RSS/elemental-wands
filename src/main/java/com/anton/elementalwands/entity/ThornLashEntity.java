package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.NatureCombat;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.util.ThornLashRules;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** One forward bite, committed on press. Stable entity/spell IDs preserve learned spells. */
public final class ThornLashEntity extends Entity {
    private static final TrackedData<Integer> CASTER = DataTracker.registerData(ThornLashEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> START = DataTracker.registerData(ThornLashEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Float> STOP = DataTracker.registerData(ThornLashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEAL = DataTracker.registerData(ThornLashEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private UUID casterId;

    public ThornLashEntity(EntityType<? extends ThornLashEntity> type, World world) {
        super(type, world); setNoGravity(true); noClip = true;
    }
    public void initialize(PlayerEntity caster) {
        casterId = caster.getUuid();
        dataTracker.set(CASTER, caster.getId());
        dataTracker.set(START, getEntityWorld().getTime());
        Vec3d origin = caster.getEyePos();
        refreshPositionAndAngles(origin.x, origin.y, origin.z, caster.getYaw(), caster.getPitch());
    }
    public int casterEntityId() { return dataTracker.get(CASTER); }
    public float elapsed(float delta) { return getEntityWorld().getTime() - dataTracker.get(START) + delta; }
    public float stopProgress() { return dataTracker.get(STOP); }
    public boolean healed() { return dataTracker.get(HEAL) > 0; }
    public float biteTime() { return Math.min(1, stopProgress()) * ThornLashRules.EXTEND_TICKS; }
    public Vec3d tipPosition(float time) {
        double progress = Math.min(Math.clamp(time / ThornLashRules.EXTEND_TICKS, 0, 1), stopProgress());
        double retract = Math.clamp((time - biteTime() - ThornLashRules.BITE_TICKS) / ThornLashRules.RETRACT_TICKS, 0, 1);
        return getEntityPos().add(ThornLashRules.tip(getYaw(), getPitch(), progress).multiply(1 - retract));
    }
    @Override public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        PlayerEntity caster = casterId == null ? null : world.getPlayerByUuid(casterId);
        float time = elapsed(0);
        if (caster == null || !caster.isAlive() || caster.isSpectator()
                || EWAttachments.getAffinity(caster) != WizardAffinity.NATURE
                || !(caster.getMainHandStack().getItem() instanceof AbstractWandItem)
                || caster.getEyePos().squaredDistanceTo(getEntityPos()) > 64
                || time > biteTime() + ThornLashRules.BITE_TICKS + ThornLashRules.RETRACT_TICKS) { discard(); return; }
        if (stopProgress() <= 1 || time > ThornLashRules.EXTEND_TICKS) return;
        // Sweep only the mouth, testing the nearest contact before any farther one.
        for (int sample = 1; sample <= 12; sample++) {
            double from = Math.clamp((time - 1 + (sample - 1) / 12.0) / ThornLashRules.EXTEND_TICKS, 0, 1);
            double to = Math.clamp((time - 1 + sample / 12.0) / ThornLashRules.EXTEND_TICKS, 0, 1);
            Vec3d a = getEntityPos().add(ThornLashRules.tip(getYaw(), getPitch(), from));
            Vec3d b = getEntityPos().add(ThornLashRules.tip(getYaw(), getPitch(), to));
            var wall = world.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            double nearest = wall.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : a.distanceTo(wall.getPos());
            LivingEntity victim = null;
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                    new Box(a, b).expand(ThornLashRules.WIDTH),
                    e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity)
                            && !(e instanceof AwakenedTreeEntity) && !WandAllies.protectedFrom(caster, e))) {
                Box box = target.getBoundingBox().expand(ThornLashRules.WIDTH);
                Vec3d contact = box.contains(a) ? a : box.raycast(a, b).orElse(null);
                if (contact == null || a.distanceTo(contact) >= nearest) continue;
                if (world.raycast(new RaycastContext(getEntityPos(), contact, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, this)).getType() != HitResult.Type.MISS) continue;
                nearest = a.distanceTo(contact); victim = target;
            }
            if (Double.isFinite(nearest)) {
                float progress = (float)(from + (to - from) * Math.clamp(nearest / Math.max(1E-8, a.distanceTo(b)), 0, 1));
                dataTracker.set(STOP, progress); // An invulnerable first target also consumes the bite.
                Vec3d contact = getEntityPos().add(ThornLashRules.tip(getYaw(), getPitch(), progress));
                if (victim != null) bite(world, caster, victim, contact);
                world.playSound(null, contact.x, contact.y, contact.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.PLAYERS, .45f, 1.6f);
                return;
            }
        }
        if (time >= ThornLashRules.EXTEND_TICKS) dataTracker.set(STOP, 1f);
    }
    private void bite(ServerWorld world, PlayerEntity caster, LivingEntity target, Vec3d contact) {
        float before = target.getHealth();
        if (!SpellCombat.damage(target, world, world.getDamageSources().playerAttack(caster), ThornLashRules.DAMAGE, caster, WizardAffinity.NATURE)) return;
        float lost = Math.max(0, before - target.getHealth());
        NatureCombat.lashDamageDealt(caster, lost);
        float heal = Math.min(ThornLashRules.HEAL_CAP, lost * ThornLashRules.LIFESTEAL);
        float health = caster.getHealth();
        if (heal > 0) SpellCombat.heal(caster, heal, caster, WizardAffinity.NATURE);
        dataTracker.set(HEAL, caster.getHealth() - health);
        world.spawnParticles(ModParticles.NATURE_POLLEN, contact.x, contact.y, contact.z, 5, .10, .10, .10, .015);
        if (healed()) world.spawnParticles(ModParticles.NATURE_HEART, caster.getX(), caster.getBodyY(.6), caster.getZ(), 1, .1, .1, .1, 0);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CASTER, -1); builder.add(START, 0L); builder.add(STOP, 2f); builder.add(HEAL, 0f);
    }
    @Override protected void readCustomData(ReadView view) { discard(); }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
