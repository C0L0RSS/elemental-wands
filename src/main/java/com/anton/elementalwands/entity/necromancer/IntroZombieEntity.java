package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * The zombie whose soul opens the intro. The scene moves it; the client renderer arches it back
 * while its soul is torn out. Harmless, unhurt, never saved.
 */
public class IntroZombieEntity extends ZombieEntity {
    public IntroZombieEntity(EntityType<? extends IntroZombieEntity> type, World world) {
        super(type, world);
        setAiDisabled(true);
        setInvulnerable(true);
        setCanPickUpLoot(false);
    }

    @Override protected void initGoals() {}
    @Override protected boolean burnsInDaylight() { return false; }
    @Override protected boolean canConvertInWater() { return false; }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) { return true; }
    @Override public boolean canHit() { return false; }
}
