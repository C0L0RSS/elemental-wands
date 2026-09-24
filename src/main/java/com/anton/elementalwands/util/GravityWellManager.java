package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.GravityBombEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;

/** One transient bomb per owner. Recovery starts at impact, independently of collapse. */
public final class GravityWellManager {
    public static final String ID = "gravity_well";
    public static final int COOLDOWN = 320, DURATION = 80;
    public static final double RADIUS = 4;
    public static final float DAMAGE = 6;
    private record Cast(ServerPlayerEntity player, ServerWorld world, ItemStack wand, GravityBombEntity bomb, long launched) {}
    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Cast c : List.copyOf(CASTS.values())) {
                if (!valid(c) || c.bomb.isRemoved() || (!c.bomb.isWell() && c.world.getTime() - c.launched >= 60)) finish(c, false);
                else if (c.bomb.isCollapsing()) {
                    if (c.bomb.collapseAge() >= GravityBombEntity.COLLAPSE_TICKS) finish(c, true);
                } else if (c.bomb.isWell() && c.world.getTime() >= state(c.player).getLong("expires", Long.MAX_VALUE))
                    requestCollapse(c); // Loaded chunks can stop entity ticks when the owner teleports away.
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((e, source) -> { if (e instanceof ServerPlayerEntity p) cancel(p); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var p = handler.player;
            var data = state(p);
            if (data.getBoolean("active", false)) {
                data = data.copy();
                if (data.getLong("expires", 0) == 0) data.putLong("impact", p.getEntityWorld().getTime());
                data.putBoolean("active", false);
                p.setAttached(EWAttachments.GRAVITY_STATE, data);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Cast c : List.copyOf(CASTS.values())) finish(c, false);
        });
    }
    public static NbtCompound state(PlayerEntity p) { return p.getAttachedOrElse(EWAttachments.GRAVITY_STATE, new NbtCompound()); }
    public static long remaining(NbtCompound data, long now, boolean entangled) {
        long elapsed = Math.max(0, now - data.getLong("impact", -1_000_000_000L));
        return Math.max(0, COOLDOWN - (entangled ? elapsed / 2 : elapsed));
    }
    public static long remaining(PlayerEntity p) { return remaining(state(p), p.getEntityWorld().getTime(), EntangleTracker.getStacks(p) > 0); }
    private static boolean valid(Cast c) {
        var p = c.player;
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.getEntityWorld() == c.world
                && c.world.isChunkLoaded(c.bomb.getBlockPos())
                && c.world.getPlayerByUuid(p.getUuid()) == p && EWAttachments.getAffinity(p) == WizardAffinity.SPACE
                && GuardianArenaManager.canCast(p) && WandProgression.owns(p, WandSpells.find(ID)) && WandLoadouts.get(p).contains(ID);
    }
    public static boolean owns(ServerPlayerEntity p, GravityBombEntity bomb) {
        Cast c = CASTS.get(p.getUuid()); return c != null && c.bomb == bomb && valid(c);
    }
    public static GravityBombEntity active(ServerPlayerEntity p) {
        Cast c = CASTS.get(p.getUuid()); return c != null && valid(c) && !c.bomb.isRemoved() ? c.bomb : null;
    }
    public static boolean cast(ServerPlayerEntity p) {
        var w = p.getEntityWorld();
        if (!p.isAlive() || p.isSpectator() || !GuardianArenaManager.canCast(p)
                || EWAttachments.getAffinity(p) != WizardAffinity.SPACE || HollowPurpleChargeManager.isCharging(w, p)
                || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandProgression.owns(p, WandSpells.find(ID)) || !WandLoadouts.get(p).contains(ID)) return false;
        Cast c = CASTS.get(p.getUuid());
        if (c != null) {
            if (!valid(c) || c.bomb.isRemoved()) { finish(c, false); return false; }
            if (!c.bomb.isWell() || c.bomb.isCollapsing()) return false;
            // A deliberate recast only consumes the shared tap, never the impact recovery.
            if (!AbstractWandItem.tryStartCooldown(w, p, p.getMainHandStack(), ID, 0)) return false;
            requestCollapse(c); WandLoadouts.markCombat(p); return true;
        }
        long remaining = remaining(p);
        if (remaining > 0) { AbstractWandItem.sendCooldownActionbar(p, "Gravity Well", (int) remaining); return false; }
        if (!AbstractWandItem.canStartCooldown(w, p, p.getMainHandStack(), ID, COOLDOWN)
                || !AbstractWandItem.tryStartCooldown(w, p, p.getMainHandStack(), ID, 0)) return false;
        var bomb = new GravityBombEntity(w, p);
        c = new Cast(p, w, p.getMainHandStack(), bomb, w.getTime()); CASTS.put(p.getUuid(), c);
        var data = state(p).copy(); data.putBoolean("active", true); data.putBoolean("collapsing", false); data.putLong("expires", 0);
        p.setAttached(EWAttachments.GRAVITY_STATE, data);
        if (!w.spawnEntity(bomb)) { finish(c, false); return false; }
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.PLAYERS, .8f, .65f);
        WandLoadouts.markCombat(p); return true;
    }
    public static void impact(ServerPlayerEntity p, GravityBombEntity bomb) {
        Cast c = CASTS.get(p.getUuid());
        if (c == null || c.bomb != bomb || !valid(c)) return;
        beginRecovery(c);
        var data = state(p).copy(); data.putLong("expires", c.world.getTime() + DURATION);
        p.setAttached(EWAttachments.GRAVITY_STATE, data);
    }
    public static void expired(ServerPlayerEntity p, GravityBombEntity bomb) {
        Cast c = CASTS.get(p.getUuid());
        if (c != null && c.bomb == bomb) {
            if (valid(c) && bomb.isWell()) requestCollapse(c); else finish(c, false);
        }
    }
    private static void requestCollapse(Cast c) {
        if (c.bomb.isCollapsing()) return;
        c.bomb.beginCollapse();
        var data = state(c.player).copy(); data.putBoolean("collapsing", true);
        c.player.setAttached(EWAttachments.GRAVITY_STATE, data);
    }
    public static void cancel(ServerPlayerEntity p) { Cast c = CASTS.get(p.getUuid()); if (c != null) finish(c, false); }
    private static void beginRecovery(Cast c) {
        AbstractWandItem.startCooldown(c.world, c.wand, ID, COOLDOWN, false);
        var data = state(c.player).copy(); data.putLong("impact", c.world.getTime());
        c.player.setAttached(EWAttachments.GRAVITY_STATE, data);
    }
    private static void finish(Cast c, boolean collapse) {
        if (!CASTS.remove(c.player.getUuid(), c)) return;
        // A lost/unloaded flying bomb still costs recovery; cancellation never detonates.
        if (!c.bomb.isWell()) beginRecovery(c);
        if (collapse) c.bomb.collapse();
        else c.bomb.discard();
        var data = state(c.player).copy(); data.putBoolean("active", false);
        c.player.setAttached(EWAttachments.GRAVITY_STATE, data);
    }
    private GravityWellManager() {}
}
