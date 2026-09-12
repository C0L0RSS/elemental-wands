package com.anton.elementalwands.util;

import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared flower/secondary contact and player-owned charge windows. */
public final class NatureCombat {
    public static final float SEED_DAMAGE = 3.0f;
    private static final int INTERVAL = 20;
    private record Contact(UUID caster, UUID target) {}
    private static final Map<Contact, Integer> CONTACTS = new HashMap<>();
    private static final Map<UUID, Integer> THORN_CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> SEED_CHARGE = new HashMap<>();

    private NatureCombat() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTicks();
            CONTACTS.values().removeIf(until -> until <= now);
            THORN_CHARGE.values().removeIf(until -> until <= now);
            SEED_CHARGE.values().removeIf(until -> until <= now);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CONTACTS.clear(); THORN_CHARGE.clear(); SEED_CHARGE.clear();
        });
    }

    /** The first contact deals 3; successive Entangle levels reach 5 at five stacks. */
    public static float thornDamage(int stacks) {
        return 3.0f + 0.5f * Math.clamp(stacks - 1, 0, 4);
    }

    public static void thornContact(ServerWorld world, LivingEntity target, UUID casterUuid) {
        if (!target.isAlive() || target.isSpectator() || target instanceof AwakenedTreeEntity
                || target.getUuid().equals(casterUuid)) return;
        int now = world.getServer().getTicks();
        Contact key = new Contact(casterUuid, target.getUuid());
        if (CONTACTS.getOrDefault(key, Integer.MIN_VALUE) > now) return;
        // Claim before applying effects: overlapping patches cannot accelerate stacks either.
        CONTACTS.put(key, now + INTERVAL);
        EntangleTracker.addStack(world, target);
        float damage = thornDamage(EntangleTracker.getStacks(target));
        if (target.damage(world, world.getDamageSources().sweetBerryBush(), damage)) {
            if (target instanceof FracturedGuardianEntity guardian) guardian.onNatureThorns();
            reward(world.getPlayerByUuid(casterUuid), damage, THORN_CHARGE, 3);
        }
    }

    public static void seedDamageDealt(Entity owner) {
        reward(owner, SEED_DAMAGE, SEED_CHARGE, 1);
    }

    private static void reward(Entity owner, float damage, Map<UUID, Integer> windows, int points) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        int now = player.getEntityWorld().getServer().getTicks();
        int charge = 0;
        if (player.getMainHandStack().getItem() instanceof AbstractWandItem
                && windows.getOrDefault(player.getUuid(), Integer.MIN_VALUE) <= now) {
            windows.put(player.getUuid(), now + INTERVAL);
            charge = points;
        }
        // Keep Arcane Flux for every accepted hit even when the charge window is closed.
        AbstractWandItem.onWandDamageDealt(player, damage, charge);
    }
}
