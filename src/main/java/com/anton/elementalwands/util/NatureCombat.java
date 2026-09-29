package com.anton.elementalwands.util;

import com.anton.elementalwands.party.WandAllies;

import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.entity.WandBoss;
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
    public static final float SEED_DAMAGE = 4.0f;
    /** Bosses shrug off the root, so thorns bite them harder instead; ordinary targets are unchanged. */
    public static final float BOSS_THORN_MULTIPLIER = 1.5f;
    /** A flower banks the health its thorns take, up to this much, until its owner pops it. */
    public static final float FLOWER_STORE_CAP = 8;
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

    /** The first contact deals 1; successive Entangle levels reach 2 at five stacks. */
    public static float thornDamage(int stacks) {
        return 1.0f + 0.25f * Math.clamp(stacks - 1, 0, 4);
    }

    /** Returns the health the thorns actually took, which the patch's flower banks. */
    public static float thornContact(ServerWorld world, LivingEntity target, UUID casterUuid) {
        if (!target.isAlive() || target.isSpectator() || target instanceof AwakenedTreeEntity
                || WandAllies.protectedFrom(world, casterUuid, target)) return 0;
        int now = world.getServer().getTicks();
        Contact key = new Contact(casterUuid, target.getUuid());
        if (CONTACTS.getOrDefault(key, Integer.MIN_VALUE) > now) return 0;
        // Claim before applying effects: overlapping patches cannot accelerate stacks either.
        CONTACTS.put(key, now + INTERVAL);
        EntangleTracker.addStack(world, target);
        float damage = thornDamage(EntangleTracker.getStacks(target)) * (target instanceof WandBoss ? BOSS_THORN_MULTIPLIER : 1);
        float before = target.getHealth();
        if (com.anton.elementalwands.util.SpellCombat.damage(target,world,world.getDamageSources().sweetBerryBush(),damage,world.getPlayerByUuid(casterUuid),com.anton.elementalwands.data.WizardAffinity.NATURE)) {
            if (target instanceof WandBoss boss) boss.onNatureThorns();
            reward(world.getPlayerByUuid(casterUuid), damage, THORN_CHARGE, 3);
            return Math.max(0, before - target.getHealth());
        }
        return 0;
    }

    /**
     * Standing in a patch: every block column under the body counts (at the feet and just below),
     * so a wide body such as the colossus is not tested by its centre alone.
     */
    public static boolean standsIn(LivingEntity e, java.util.Set<net.minecraft.util.math.BlockPos> cells) {
        var box = e.getBoundingBox();
        int y = e.getBlockPos().getY();
        for (int x = net.minecraft.util.math.MathHelper.floor(box.minX); x <= net.minecraft.util.math.MathHelper.floor(box.maxX - 1e-7); x++)
            for (int z = net.minecraft.util.math.MathHelper.floor(box.minZ); z <= net.minecraft.util.math.MathHelper.floor(box.maxZ - 1e-7); z++)
                if (cells.contains(new net.minecraft.util.math.BlockPos(x, y, z)) || cells.contains(new net.minecraft.util.math.BlockPos(x, y - 1, z)))
                    return true;
        return false;
    }

    public static void lashDamageDealt(Entity owner, float healthDamage) {
        if (healthDamage > 0) reward(owner, healthDamage, SEED_CHARGE, 1);
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
        AbstractWandItem.onWandDamageDealt(player, damage, charge, com.anton.elementalwands.data.WizardAffinity.NATURE);
    }
}
