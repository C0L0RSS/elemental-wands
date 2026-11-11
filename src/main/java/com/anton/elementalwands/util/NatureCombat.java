package com.anton.elementalwands.util;

import com.anton.elementalwands.item.AbstractWandItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Nature's hits: the caster's own mark a target for their flowers, and charge is paced per caster. */
public final class NatureCombat {
    public static final float SEED_DAMAGE = 4.0f;
    /** Ultimate charge for a Seed or Thornbite hit, at most once a second across both. */
    public static final int HIT_CHARGE = 3;
    /** Ultimate charge for each flower seed that lands. Three flowers land at most 1.5 a second. */
    public static final int FLOWER_CHARGE = 1;
    private static final int INTERVAL = 20;
    private static final Map<UUID, Integer> HIT_CHARGE_WINDOW = new HashMap<>();

    private NatureCombat() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTicks();
            HIT_CHARGE_WINDOW.values().removeIf(until -> until <= now);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HIT_CHARGE_WINDOW.clear());
    }

    public static void lashDamageDealt(Entity owner, Entity target, float healthDamage) {
        if (healthDamage > 0) reward(owner, healthDamage, HIT_CHARGE);
        FlowerTurretManager.mark(owner, target);
    }

    public static void seedDamageDealt(Entity owner, Entity target) {
        reward(owner, SEED_DAMAGE, HIT_CHARGE);
        FlowerTurretManager.mark(owner, target);
    }

    /**
     * A flower's seed landed. It earns its own small charge (flowers are already limited by their
     * number and pace, so they need no window) and returns a share of the health it took to the owner.
     */
    public static void flowerDamageDealt(Entity owner, float healthDamage) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        // Grown flowers are Full Bloom's own doing, and an ultimate does not refill itself.
        AbstractWandItem.onWandDamageDealt(player, FlowerTurretManager.DAMAGE,
                player.getMainHandStack().getItem() instanceof AbstractWandItem && !FullBloomManager.blooming(player) ? FLOWER_CHARGE : 0,
                com.anton.elementalwands.data.WizardAffinity.NATURE);
        float heal = healthDamage * FlowerTurretManager.HEAL_SHARE;
        if (heal <= 0 || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) return;
        SpellCombat.heal(player, heal, player, com.anton.elementalwands.data.WizardAffinity.NATURE);
        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)
            BlockNatureEvents.send(world, BlockNatureEvents.HEAL, player.getId(), player.getEntityPos(), net.minecraft.util.math.Vec3d.ZERO, 0);
    }

    private static void reward(Entity owner, float damage, int points) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        int now = player.getEntityWorld().getServer().getTicks();
        int charge = 0;
        if (player.getMainHandStack().getItem() instanceof AbstractWandItem
                && HIT_CHARGE_WINDOW.getOrDefault(player.getUuid(), Integer.MIN_VALUE) <= now) {
            HIT_CHARGE_WINDOW.put(player.getUuid(), now + INTERVAL);
            charge = points;
        }
        // Keep Arcane Flux for every accepted hit even when the charge window is closed.
        AbstractWandItem.onWandDamageDealt(player, damage, charge, com.anton.elementalwands.data.WizardAffinity.NATURE);
    }
}
