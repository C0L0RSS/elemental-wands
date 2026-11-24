package com.anton.elementalwands.util;

import com.anton.elementalwands.data.WizardAffinity;
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

    public static void directHit(Entity owner, Entity target, SpellCombat.DamageResult hit) {
        if (!hit.accepted()) return;
        if (hit.healthLost() > 0) rewardDirectHit(owner, hit);
        FlowerTurretManager.mark(owner, target);
    }

    /**
     * A flower's seed landed. It earns its own small charge (flowers are already limited by their
     * number and pace, so they need no window). The health it took stays with the flower, for its
     * owner to collect (see {@link FlowerTurretManager#bank}).
     */
    public static void flowerDamageDealt(Entity owner, SpellCombat.DamageResult hit) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        // Grown flowers are Full Bloom's own doing, and an ultimate does not refill itself.
        SpellCombat.rewardDamage(hit, player, WizardAffinity.NATURE,
                FullBloomManager.blooming(player) ? 0 : FLOWER_CHARGE);
    }

    private static void rewardDirectHit(Entity owner, SpellCombat.DamageResult hit) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        int now = player.getEntityWorld().getServer().getTicks();
        int charge = 0;
        if (player.getMainHandStack().getItem() instanceof AbstractWandItem
                && HIT_CHARGE_WINDOW.getOrDefault(player.getUuid(), Integer.MIN_VALUE) <= now) {
            HIT_CHARGE_WINDOW.put(player.getUuid(), now + INTERVAL);
            charge = HIT_CHARGE;
        }
        SpellCombat.rewardDamage(hit, player, WizardAffinity.NATURE, charge);
    }
}
