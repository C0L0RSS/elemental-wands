package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.entity.WandBoss;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Reachable animated head/neck hurtboxes; every accepted hit belongs to the single Roc. */
public final class RocBodyPartEntity extends PathAwareEntity implements WandBoss {
    private StormRocEntity owner;
    private String socket = "head";

    public RocBodyPartEntity(EntityType<? extends RocBodyPartEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
        setInvisible(true);
        experiencePoints = 0;
        // Players aim melee at the client copy, so it must sit where the server's is each tick.
        getInterpolator().setLerpDuration(1);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 1024)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.MOVEMENT_SPEED, 0);
    }

    void bind(StormRocEntity owner, String socket) {
        this.owner = owner;
        this.socket = socket;
        follow();
    }

    void follow() {
        if (owner == null) return;
        Vec3d at = owner.socket(socket);
        setPosition(at.x, at.y - 1.6, at.z);
        setHealth(Math.min(getMaxHealth(), owner.getHealth()));
    }

    @Override public float getHealth() { return owner == null ? super.getHealth() : owner.getHealth(); }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return owner != null && owner.damage(world, source, amount); }

    @Override public void tick() {
        if (getEntityWorld() instanceof ServerWorld) {
            if (owner == null || owner.isRemoved() || !owner.fighting()) discard();
            else follow();
        } else if (isInterpolating()) {
            // Tracked positions reach a living entity through its interpolator, which only
            // LivingEntity.tick steps. This tick skips that, so step it here; otherwise the
            // client's copy stays where the part spawned and melee can never target it.
            getInterpolator().tick();
        }
    }

    @Override protected void initGoals() {}
    @Override public boolean eligible(ServerPlayerEntity p) { return owner != null && owner.eligible(p); }
    @Override public boolean isBossAggressive() { return owner != null && owner.isBossAggressive(); }
    @Override public boolean isPushable() { return false; }
    @Override public void addVelocity(double x, double y, double z) {}
    public StormRocEntity owner() { return owner; }
}
