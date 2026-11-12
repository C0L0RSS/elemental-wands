package com.anton.elementalwands.util;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Full Bloom, the Nature wand's ultimate. A wave of petals rolls out from the caster. The
 * caster and every ally inside its reach at that moment regenerate and gain absorption for
 * the bloom's twelve seconds, wherever they go afterwards. For the same twelve seconds the
 * caster's own spells are stronger: Seed and Sentry Bloom recover faster, Thornbite heals
 * more, and every flower of theirs grows a second head and stops ageing (see
 * {@link FlowerTurretManager}).
 *
 * <p>The wave itself is harmless: Full Bloom is a blessing, and Nature's damage comes from its
 * flowers and its other spells. On grass the wave leaves grass and wild flowers behind as bone
 * meal would. It places nothing else, so Full Bloom puts no block in a fight.
 */
public final class FullBloomManager {
    public static final int DURATION = 240;
    public static final int RADIUS = 10;
    /** The petal wave takes this long to reach its full radius. */
    public static final int WAVE_TICKS = 12;
    /** Regeneration II and Absorption I (two hearts). */
    public static final int REGENERATION_AMPLIFIER = 1, ABSORPTION_AMPLIFIER = 0;
    /** Seed's cooldown while its caster blooms (20 ticks otherwise). */
    public static final int SEED_COOLDOWN = 12;
    /** Thornbite's share of damage healed, and its cap, while its caster blooms. */
    public static final float BITE_LIFESTEAL = 1.0f, BITE_HEAL_CAP = 5.0f;

    private static final Block[] WILD_FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM};

    private static final class Bloom {
        final RegistryKey<World> world;
        final Vec3d origin;
        final int start;
        final Set<UUID> allies = new HashSet<>();
        double reached;
        Bloom(RegistryKey<World> world, Vec3d origin, int start) { this.world = world; this.origin = origin; this.start = start; }
    }

    private static final Map<UUID, Bloom> ACTIVE = new HashMap<>();

    private FullBloomManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FullBloomManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    /** Whether this player's own Full Bloom is running. */
    public static boolean blooming(Entity player) {
        return player != null && ACTIVE.containsKey(player.getUuid());
    }

    /** Whether this player is carried by someone's running Full Bloom (their own included). */
    public static boolean blessed(Entity player) {
        return player != null && ACTIVE.values().stream().anyMatch(bloom -> bloom.allies.contains(player.getUuid()));
    }

    public static void cast(ServerWorld world, PlayerEntity caster) {
        Bloom bloom = new Bloom(world.getRegistryKey(), caster.getEntityPos(), world.getServer().getTicks());
        // Whoever stands in the wave's reach now keeps the blessing for its whole length.
        for (PlayerEntity ally : world.getPlayers()) {
            if (!ally.isAlive() || ally.isSpectator() || ally.squaredDistanceTo(caster) > RADIUS * RADIUS
                    || ally != caster && !WandAllies.protectedFrom(caster, ally)) continue;
            bloom.allies.add(ally.getUuid());
            ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, DURATION, ABSORPTION_AMPLIFIER, false, false, true));
            SpellBuffs.regeneration(ally, caster, WizardAffinity.NATURE, REGENERATION_AMPLIFIER);
            BlockNatureEvents.send(world, BlockNatureEvents.HEAL, ally.getId(), ally.getEntityPos(), Vec3d.ZERO, 0);
        }
        ACTIVE.put(caster.getUuid(), bloom);
        Vec3d at = bloom.origin;
        // Clients draw the cast burst, the petal wave and the caster's drifting petals from this one event.
        BlockNatureEvents.send(world, BlockNatureEvents.FULL_BLOOM, caster.getId(), at, Vec3d.ZERO, RADIUS);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_NATURE_FULL_BLOOM_CAST, SoundCategory.PLAYERS, 1.3f, 1.0f);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTicks();
        for (Iterator<Map.Entry<UUID, Bloom>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Bloom bloom = entry.getValue();
            ServerWorld world = server.getWorld(bloom.world);
            PlayerEntity caster = world == null ? null : world.getPlayerByUuid(entry.getKey());
            int age = now - bloom.start;
            if (caster == null || !caster.isAlive() || caster.isSpectator() || age >= DURATION) { it.remove(); continue; }
            if (age <= WAVE_TICKS) wave(world, bloom, RADIUS * age / (double) WAVE_TICKS);
            for (UUID id : bloom.allies) {
                PlayerEntity ally = world.getPlayerByUuid(id);
                if (ally != null && ally.isAlive()) SpellBuffs.regeneration(ally, caster, WizardAffinity.NATURE, REGENERATION_AMPLIFIER);
            }
        }
    }

    /** The ring between the last radius and this one grows on grass. It hurts nothing; clients draw its petals. */
    private static void wave(ServerWorld world, Bloom bloom, double radius) {
        sprout(world, bloom, radius);
        bloom.reached = radius;
    }

    /** On grass the wave leaves short grass and wild flowers, as bone meal does. It touches nothing else. */
    private static void sprout(ServerWorld world, Bloom bloom, double radius) {
        BlockPos centre = BlockPos.ofFloored(bloom.origin);
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius || distance <= bloom.reached && bloom.reached > 0) continue;
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos ground = centre.add(dx, dy, dz);
                if (!world.isChunkLoaded(ground) || !world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK)) continue;
                if (world.getBlockState(ground.up()).isAir()) {
                    float roll = world.random.nextFloat();
                    if (roll < 0.14f) world.setBlockState(ground.up(), WILD_FLOWERS[world.random.nextInt(WILD_FLOWERS.length)].getDefaultState(), Block.NOTIFY_ALL);
                    else if (roll < 0.55f) world.setBlockState(ground.up(), Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_ALL);
                }
                break;
            }
        }
    }

    /** For fixtures: who a running bloom of this caster blesses. */
    public static List<UUID> allies(Entity caster) {
        Bloom bloom = caster == null ? null : ACTIVE.get(caster.getUuid());
        return bloom == null ? List.of() : List.copyOf(bloom.allies);
    }
}
