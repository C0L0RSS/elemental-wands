package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.NatureCombat;
import com.anton.elementalwands.util.ThornLashRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** One committed sweep. The visible vine and swept hit tests use identical geometry. */
public final class ThornLashEntity extends Entity {
    private UUID casterId;
    private final Set<UUID> contacted = new HashSet<>();
    private float healingUsed;

    public ThornLashEntity(EntityType<? extends ThornLashEntity> type, World world) {
        super(type, world); setNoGravity(true); noClip = true;
    }

    public void initialize(PlayerEntity caster) {
        casterId = caster.getUuid();
        Vec3d origin = caster.getEyePos().add(0, -.35, 0);
        refreshPositionAndAngles(origin.x, origin.y, origin.z, caster.getYaw(), caster.getPitch());
    }

    @Override public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        PlayerEntity caster = casterId == null ? null : world.getPlayerByUuid(casterId);
        if (caster == null || !caster.isAlive() || caster.isSpectator()
                || EWAttachments.getAffinity(caster) != WizardAffinity.NATURE
                || !(caster.getMainHandStack().getItem() instanceof AbstractWandItem)
                || age > ThornLashRules.LIFETIME) { discard(); return; }
        if (age > ThornLashRules.SWEEP_TICKS) return;
        var candidates = world.getEntitiesByClass(LivingEntity.class,
                new Box(getEntityPos(), getEntityPos()).expand(ThornLashRules.RANGE + 1),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity)
                        && !(e instanceof AwakenedTreeEntity) && !WandAllies.protectedFrom(caster, e)
                        && !contacted.contains(e.getUuid()));
        for (LivingEntity target : candidates) {
            Vec3d contact = contact(world, target);
            if (contact == null) continue;
            contacted.add(target.getUuid());
            float before = target.getHealth();
            if (!target.damage(world, world.getDamageSources().playerAttack(caster), ThornLashRules.DAMAGE)) continue;
            float lost = Math.max(0, before - target.getHealth());
            NatureCombat.lashDamageDealt(caster, lost);
            float heal = Math.min(Math.max(0, ThornLashRules.HEAL_CAP - healingUsed), lost * ThornLashRules.LIFESTEAL);
            healingUsed += heal;
            if (heal > 0) {
                caster.heal(heal);
                for (int i = 0; i < 8; i++) {
                    Vec3d p = contact.lerp(caster.getEntityPos().add(0, 1, 0), i / 7.0);
                    world.spawnParticles(ModParticles.NATURE_POLLEN, p.x, p.y, p.z, 1, .025, .025, .025, 0);
                }
                world.spawnParticles(ModParticles.NATURE_HEART, caster.getX(), caster.getBodyY(.6), caster.getZ(), 1, .1, .1, .1, 0);
            }
        }
    }

    private Vec3d contact(ServerWorld world, LivingEntity target) {
        Box box = target.getBoundingBox().expand(ThornLashRules.WIDTH);
        for (int time = 0; time <= 6; time++) {
            double t = age - 1 + time / 6.0;
            for (int segment = 0; segment <= 24; segment++) {
                Vec3d p = getEntityPos().add(ThornLashRules.point(getYaw(), getPitch(), t, segment / 24.0));
                if (!box.contains(p)) continue;
                if (world.raycast(new RaycastContext(getEntityPos(), p, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS) return p;
            }
        }
        return null;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {}
    @Override protected void readCustomData(ReadView view) { casterId = null; }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
