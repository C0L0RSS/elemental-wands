package com.anton.elementalwands.item;

import java.util.Locale;

import com.anton.elementalwands.util.WandUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class AbstractWandItem extends Item {

    public static final double DEFAULT_RANGE = 25.0;

    public static final int DEFAULT_PRIMARY_COOLDOWN_TICKS = 20;
    public static final int DEFAULT_SECONDARY_COOLDOWN_TICKS = 120;
    public static final int DEFAULT_ULTIMATE_COOLDOWN_TICKS = 800;

    private static final int GLOBAL_COOLDOWN_TICKS = 6;

    private static final String NBT_LAST_GLOBAL = "ew_last_global";
    private static final String NBT_LAST_PRIMARY = "ew_last_primary";
    private static final String NBT_LAST_SECONDARY = "ew_last_secondary";
    private static final String NBT_LAST_ULTIMATE = "ew_last_ultimate";

    protected AbstractWandItem(Settings settings) {
        super(settings);
    }

    public abstract void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack);

    public abstract void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack);

    public abstract void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack);

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient())
            return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld serverWorld))
            return ActionResult.SUCCESS;
        if (user.isSpectator())
            return ActionResult.PASS;

        if (user.isSneaking()) {
            castSecondary(serverWorld, user, stack);
        } else {
            castPrimary(serverWorld, user, stack);
        }
        return ActionResult.SUCCESS;
    }

    protected final HitResult raycast(ServerWorld world, Entity caster, double range) {
        return WandUtils.raycast(world, caster, range);
    }

    protected final void spawnParticleLine(ServerWorld world, Vec3d start, Vec3d end, ParticleEffect particle) {
        WandUtils.spawnBeam(world, start, end, particle);
    }

    protected final boolean tryStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            Ability ability, int abilityCooldownTicks) {
        long now = world.getTime();

        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        // Using orElse because getLong returns an Optional<Long> in this mapping
        long lastGlobal = nbt.getLong(NBT_LAST_GLOBAL).orElse(-1_000_000_000L);
        long globalRemaining = GLOBAL_COOLDOWN_TICKS - (now - lastGlobal);
        if (globalRemaining > 0) {
            sendCooldownActionbar(player, Ability.GLOBAL, (int) globalRemaining);
            return false;
        }

        String key = switch (ability) {
            case PRIMARY -> NBT_LAST_PRIMARY;
            case SECONDARY -> NBT_LAST_SECONDARY;
            case ULTIMATE -> NBT_LAST_ULTIMATE;
            case GLOBAL -> NBT_LAST_GLOBAL;
        };

        long last = nbt.getLong(key).orElse(-1_000_000_000L);
        long elapsed = now - last;

        // If player has Frost stacks, time passes 2x slower for cooldowns (elapsed is
        // halved)
        if (com.anton.elementalwands.util.ChillTracker.getStacks(player) > 0) {
            elapsed /= 2;
        }

        long remaining = abilityCooldownTicks - elapsed;
        if (remaining > 0) {
            sendCooldownActionbar(player, ability, (int) remaining);
            return false;
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong(key, now);
            data.putLong(NBT_LAST_GLOBAL, now);
        });
        return true;
    }

    protected final void sendCooldownActionbar(PlayerEntity player, Ability ability, int remainingTicks) {
        double seconds = remainingTicks / 20.0;
        String label = ability.displayName;
        String msg = String.format(Locale.ROOT, "%s cooldown: %.1fs", label, seconds);
        player.sendMessage(Text.literal(msg), true);
    }

    protected final boolean applyDamage(ServerWorld world, PlayerEntity caster, Entity target, float amount) {
        if (!(target instanceof LivingEntity living))
            return false;

        DamageSource source = world.getDamageSources().playerAttack(caster);
        return living.damage(world, source, amount);
    }

    public enum Ability {
        GLOBAL("GLOBAL"),
        PRIMARY("PRIMARY"),
        SECONDARY("SECONDARY"),
        ULTIMATE("ULTIMATE");

        private final String displayName;

        Ability(String displayName) {
            this.displayName = displayName;
        }
    }
}
