package com.anton.elementalwands.util;

import com.anton.elementalwands.block.NatureSeedlingBlock;
import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.entity.SentryBloomEntity;
import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.registry.ModSpellBlocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The Nature wand's flowers. Sentry Bloom throws a bud that takes root where it lands; each flower spits a
 * small seed at an enemy every two seconds until it wilts. A flower looks at whatever its owner's
 * own Seed or Thornbite last hit (the "seed mark"), and otherwise at the nearest hostile mob,
 * and its head turns to show it.
 *
 * <p>While its owner's Full Bloom lasts (see {@link FullBloomManager}) a flower is grown: its
 * main stem rises and a smaller second head opens on a branch; the two fire in turn, each as fast as the one head did, and each at
 * its own target when there are two. A grown flower does not age.
 *
 * <p>A flower keeps a share of the health its seeds take. Its owner collects that by using the
 * flower from close up, which heals them and uses the flower up; anything else that ends the
 * flower wastes it.
 *
 * <p>A flower is never in the way. Its base is a block with no collision and its heads are
 * {@link SentryBloomEntity}s nothing can touch: nothing can stand on a flower or be lifted by it,
 * and no mob targets it. A player can aim at it and break it with one hit. It treats a boss as
 * it treats any enemy.
 */
public final class FlowerTurretManager {
    public static final int COOLDOWN = 120;
    /** Sentry Bloom's cooldown while its caster is in Full Bloom. */
    public static final int BLOOM_COOLDOWN = 60;
    public static final int MAX_FLOWERS = 3;
    public static final int LIFETIME = 480;
    public static final int FIRE_INTERVAL = 40;
    public static final double RANGE = 16;
    public static final float DAMAGE = 2.5f;
    /** The share of the health a flower's seed takes that the flower keeps for its owner. */
    public static final float BANK_SHARE = 0.2f;
    /** The most health one flower holds. */
    public static final float BANK_CAP = 8;
    /** How close a player's eyes must be to a flower to collect from it or break it with a wand click. */
    public static final double CLICK_REACH = 3;
    /** A flower spends its last two seconds wilting: head down, no more seeds. */
    public static final int WILT_TICKS = 40;
    public static final double SEED_SPEED = 1.4;
    /** How long a Seed or Thornbite hit keeps the owner's flowers on that target. */
    public static final int MARK_TICKS = 100;
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("elementalwands", "flower_seed"));

    private static final int FIRST_SHOT_DELAY = 12;
    private static final int UNFURL_TICKS = 12;
    private static final int RESCAN_TICKS = 5;
    private static final int FLOOR_SEARCH_DEPTH = 12;
    /** A flower holding health shows hearts this often. */
    private static final int HEART_TICKS = 30;
    /** The manager ends a flower itself; the block tracker's own expiry is only a safety net. */
    private static final int TRACKED_TICKS = LIFETIME + 2400;

    private static final class Flower {
        final UUID owner;
        final BlockPos pos;
        final TemporaryBlockManager.TemporaryPlacement placement;
        final SentryBloomEntity head;
        /** The second head, while the flower is grown. */
        SentryBloomEntity twin;
        int plantTick;
        int nextShot, nextTwinShot;
        /** Health its seeds have taken for its owner, until they collect it. */
        float banked;
        Flower(UUID owner, BlockPos pos, TemporaryBlockManager.TemporaryPlacement placement, SentryBloomEntity head, int now) {
            this.owner = owner; this.pos = pos; this.placement = placement; this.head = head;
            this.plantTick = now; this.nextShot = now + FIRST_SHOT_DELAY;
        }
    }

    private record Mark(UUID target, int until) {}

    private static final Map<RegistryKey<World>, List<Flower>> FLOWERS = new HashMap<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    private FlowerTurretManager() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(FlowerTurretManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { FLOWERS.clear(); MARKS.clear(); });
    }

    /**
     * Where a bud that came down in this cell takes root: on the ground there, or on the first
     * ground below it. Null when there is none in reach or something already grows on it.
     */
    public static BlockPos rootSpot(ServerWorld world, BlockPos from) {
        for (int down = 0; down <= FLOOR_SEARCH_DEPTH; down++) {
            BlockPos floor = from.down(down);
            if (!hasFloorTop(world, floor)) continue;
            return canHold(world.getBlockState(floor.up())) ? floor.up() : null;
        }
        return null;
    }

    /** Plants a flower for the caster, replacing their oldest when they already keep the limit. */
    public static boolean plant(ServerWorld world, PlayerEntity caster, BlockPos pos) {
        List<Flower> all = FLOWERS.computeIfAbsent(world.getRegistryKey(), k -> new ArrayList<>());
        var placement = TemporaryBlockManager.placeTrackedTemporaryBlocks(world, List.of(pos),
                ModSpellBlocks.NATURE_SEEDLING.getDefaultState(), TRACKED_TICKS,
                FlowerTurretManager::canHold, caster.getUuid());
        if (placement.placedCount() == 0) return false;
        List<Flower> own = all.stream().filter(f -> f.owner.equals(caster.getUuid())).toList();
        if (own.size() >= MAX_FLOWERS) {
            Flower oldest = own.stream().min(Comparator.comparingInt(f -> f.plantTick)).orElseThrow();
            wilt(world, oldest);
            all.remove(oldest);
        }
        Vec3d at = Vec3d.ofBottomCenter(pos);
        all.add(new Flower(caster.getUuid(), pos.toImmutable(), placement, spawnHead(world, at), world.getServer().getTicks()));
        world.playSound(null, pos, ModSounds.SPELL_NATURE_SENTRY_BLOOM_PLANT, SoundCategory.PLAYERS, 0.8f, 1.0f);
        BlockNatureEvents.send(world, BlockNatureEvents.PLANT, at);
        return true;
    }

    private static SentryBloomEntity spawnHead(ServerWorld world, Vec3d at) {
        SentryBloomEntity head = new SentryBloomEntity(com.anton.elementalwands.registry.ModEntities.SENTRY_BLOOM, world);
        head.setPosition(at);
        world.spawnEntity(head);
        return head;
    }

    public static int count(ServerWorld world, UUID owner) {
        return (int) FLOWERS.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(f -> f.owner.equals(owner)).count();
    }

    private static Flower at(ServerWorld world, BlockPos pos) {
        for (Flower flower : FLOWERS.getOrDefault(world.getRegistryKey(), List.of())) if (flower.pos.equals(pos)) return flower;
        return null;
    }

    /** The turning head of the flower planted here, or null. */
    public static SentryBloomEntity headAt(ServerWorld world, BlockPos pos) {
        Flower flower = at(world, pos);
        return flower == null ? null : flower.head;
    }

    /** The second head of a grown flower planted here, or null. */
    public static SentryBloomEntity twinAt(ServerWorld world, BlockPos pos) {
        Flower flower = at(world, pos);
        return flower == null ? null : flower.twin;
    }

    /** The health the flower planted here holds for its owner. */
    public static float banked(ServerWorld world, BlockPos pos) {
        Flower flower = at(world, pos);
        return flower == null ? 0 : flower.banked;
    }

    /** A seed this flower spat took health: the flower keeps a share of it for its owner, up to its cap. */
    public static void bank(ServerWorld world, BlockPos pos, Entity owner, float healthDamage) {
        Flower flower = pos == null ? null : at(world, pos);
        if (flower == null || owner == null || !flower.owner.equals(owner.getUuid()) || !(healthDamage > 0)) return;
        flower.banked = Math.min(BANK_CAP, flower.banked + healthDamage * BANK_SHARE);
    }

    /** The flower this player is aiming at from within reach, or null. */
    private static Flower aimed(ServerWorld world, PlayerEntity player, double reach) {
        Vec3d eye = player.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(player.getRotationVec(1.0f).multiply(reach)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && world.getBlockState(hit.getBlockPos()).isOf(ModSpellBlocks.NATURE_SEEDLING)
                ? at(world, hit.getBlockPos()) : null;
    }

    /**
     * A wand click on a flower from close up breaks it, whoever planted it. What it held is
     * wasted. Returns whether the click was spent on a flower.
     */
    public static boolean tryBreakAimed(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Flower flower = aimed(world, player, CLICK_REACH);
        if (flower == null) return false;
        end(world, flower);
        return true;
    }

    /**
     * A wand click on the player's own flower from close up collects what it holds. Returns
     * whether the click was spent on the flower, which it is even when there was nothing to take.
     */
    public static boolean tryCollectAimed(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Flower flower = aimed(world, player, CLICK_REACH);
        if (flower == null || !flower.owner.equals(player.getUuid())) return false;
        collect(world, player, flower);
        return true;
    }

    /** The same collection for an owner who uses the flower's block with something other than a wand in hand. */
    public static boolean collectAt(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        Flower flower = at(world, pos);
        if (flower == null || !flower.owner.equals(player.getUuid()) || player.isSpectator()
                || player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > (CLICK_REACH + 0.5) * (CLICK_REACH + 0.5)) return false;
        collect(world, player, flower);
        return true;
    }

    /** The owner takes what the flower holds: it heals them and uses the flower up. */
    private static void collect(ServerWorld world, ServerPlayerEntity player, Flower flower) {
        if (flower.banked <= 0) { player.sendMessage(Text.literal("This flower holds no health yet."), true); return; }
        if (player.getHealth() >= player.getMaxHealth()) { player.sendMessage(Text.literal("You are at full health."), true); return; }
        SpellCombat.heal(player, flower.banked, player, com.anton.elementalwands.data.WizardAffinity.NATURE);
        BlockNatureEvents.send(world, BlockNatureEvents.HEAL, player.getId(), player.getEntityPos(), Vec3d.ZERO, 0);
        Vec3d at = Vec3d.ofBottomCenter(flower.pos);
        world.spawnParticles(ParticleTypes.HEART, at.x, at.y + 0.9, at.z, hearts(flower), 0.25, 0.2, 0.25, 0.02);
        end(world, flower);
    }

    private static int hearts(Flower flower) {
        return Math.max(1, Math.round(flower.banked / 2));
    }

    /** Ends a flower from outside the world tick. */
    private static void end(ServerWorld world, Flower flower) {
        List<Flower> flowers = FLOWERS.get(world.getRegistryKey());
        if (flowers == null || !flowers.remove(flower)) return;
        wilt(world, flower);
        if (flowers.isEmpty()) FLOWERS.remove(world.getRegistryKey());
    }

    public static List<BlockPos> positions(ServerWorld world, UUID owner) {
        return FLOWERS.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(f -> f.owner.equals(owner)).map(f -> f.pos).toList();
    }

    /** The owner's own hit marks its target: their flowers turn to it for the next few seconds. */
    public static void mark(Entity owner, Entity hit) {
        Entity target = SpellCombat.damageTarget(hit);
        if (!(owner instanceof ServerPlayerEntity player) || !(target instanceof LivingEntity living)
                || !living.isAlive() || living instanceof AwakenedTreeEntity) return;
        MARKS.put(player.getUuid(), new Mark(living.getUuid(), player.getEntityWorld().getServer().getTicks() + MARK_TICKS));
        ModNetworking.syncSeedMark(player, living.getId(), MARK_TICKS);
    }

    /** The entity this owner's flowers are told to shoot, or null. */
    public static LivingEntity marked(ServerWorld world, UUID owner) {
        Mark mark = MARKS.get(owner);
        if (mark == null || mark.until <= world.getServer().getTicks()) return null;
        return world.getEntity(mark.target) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** What a flower shoots unprompted, and what its seeds may strike on the way to a target. */
    public static boolean hostile(Entity entity, Entity owner) {
        if (!(SpellCombat.damageTarget(entity) instanceof LivingEntity living) || !living.isAlive()
                || living.isSpectator() || living.isInvulnerable() || living instanceof AwakenedTreeEntity
                || WandAllies.protectedFrom(owner, living)) return false;
        if (living instanceof WandBoss boss) return !(owner instanceof ServerPlayerEntity player) || boss.eligible(player);
        if (!(living instanceof Monster)) return false;
        // A neutral monster (an enderman, a zombified piglin) is left alone until it picks a fight.
        return !(living instanceof Angerable) || living instanceof MobEntity mob && mob.getTarget() != null;
    }

    private static void tickWorld(ServerWorld world) {
        int now = world.getServer().getTicks();
        MARKS.values().removeIf(mark -> mark.until <= now);
        List<Flower> flowers = FLOWERS.get(world.getRegistryKey());
        if (flowers == null) return;
        for (Iterator<Flower> it = flowers.iterator(); it.hasNext();) {
            Flower flower = it.next();
            if (!world.isChunkLoaded(flower.pos)) continue;
            BlockState state = world.getBlockState(flower.pos);
            PlayerEntity owner = world.getPlayerByUuid(flower.owner);
            boolean blooming = owner != null && FullBloomManager.blooming(owner);
            // A flower in Full Bloom does not age once it has unfurled.
            if (blooming && now - flower.plantTick > UNFURL_TICKS) flower.plantTick++;
            int age = now - flower.plantTick;
            if (!state.isOf(ModSpellBlocks.NATURE_SEEDLING) || age >= LIFETIME || flower.head.isRemoved()
                    || owner == null || !owner.isAlive() || owner.isSpectator()) {
                wilt(world, flower);
                it.remove();
                continue;
            }
            // It unfurls over its first moments, then holds full bloom.
            int stage = Math.min(3, age / 4);
            boolean grown = blooming && stage == 3;
            if (grown != (flower.twin != null)) { if (grown) grow(world, flower, now); else shrink(world, flower); }
            if (state.get(NatureSeedlingBlock.STAGE) != stage || state.get(NatureSeedlingBlock.GROWN) != grown)
                com.anton.elementalwands.arena.GuardianArenaManager.setTemporarySpellBlock(world, flower.pos,
                        state.with(NatureSeedlingBlock.STAGE, stage).with(NatureSeedlingBlock.GROWN, grown));
            // A flower holding health shows it: more hearts as it fills.
            if (flower.banked > 0 && Math.floorMod(now + flower.pos.hashCode(), HEART_TICKS) == 0)
                world.spawnParticles(ParticleTypes.HEART, flower.pos.getX() + 0.5, flower.pos.getY() + (grown ? 2.0 : 1.2),
                        flower.pos.getZ() + 0.5, hearts(flower), 0.2, 0.1, 0.2, 0);
            if (age >= LIFETIME - WILT_TICKS) {
                if (!flower.head.wilting()) { flower.head.aim(null); flower.head.wilt(); }
                continue;
            }
            // The heads follow their targets between shots, so anyone can see what they are about to shoot.
            boolean ready = now >= flower.nextShot, twinReady = flower.twin != null && now >= flower.nextTwinShot;
            if (!ready && !twinReady && Math.floorMod(now + flower.pos.hashCode(), RESCAN_TICKS) != 0) continue;
            List<LivingEntity> targets = targetsFrom(world, owner, flower.pos);
            LivingEntity first = targets.isEmpty() ? null : targets.get(0);
            flower.head.aim(first);
            if (ready) {
                if (first == null) flower.nextShot = now + RESCAN_TICKS;
                else { fire(world, flower.pos, flower.head, owner, first); flower.nextShot = now + FIRE_INTERVAL; }
            }
            if (flower.twin == null) continue;
            // The second head takes a second enemy when there is one, and shares the first when there is not.
            LivingEntity second = targets.size() > 1 ? targets.get(1) : first;
            flower.twin.aim(second);
            if (twinReady) {
                if (second == null) flower.nextTwinShot = now + RESCAN_TICKS;
                else { fire(world, flower.pos, flower.twin, owner, second); flower.nextTwinShot = now + FIRE_INTERVAL; }
            }
        }
        if (flowers.isEmpty()) FLOWERS.remove(world.getRegistryKey());
    }

    /** The flower grows: the first head rises on a taller stem, and a smaller second head opens on a branch off it, firing half a beat later. */
    private static void grow(ServerWorld world, Flower flower, int now) {
        Vec3d at = Vec3d.ofBottomCenter(flower.pos);
        flower.head.mount(SentryBloomEntity.MAIN_STEM_TOP, SentryBloomEntity.MAIN_SCALE);
        flower.twin = spawnHead(world, at);
        flower.twin.mount(SentryBloomEntity.BRANCH_TOP, SentryBloomEntity.BRANCH_SCALE);
        flower.nextTwinShot = Math.max(now, flower.nextShot) + FIRE_INTERVAL / 2;
        BlockNatureEvents.send(world, BlockNatureEvents.GROW, at);
        world.playSound(null, flower.pos, ModSounds.SPELL_NATURE_SENTRY_BLOOM_GROW, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private static void shrink(ServerWorld world, Flower flower) {
        flower.twin.discard();
        flower.twin = null;
        flower.head.mountSingle();
        Vec3d at = Vec3d.ofBottomCenter(flower.pos);
        BlockNatureEvents.send(world, BlockNatureEvents.SHRINK, at);
    }

    /** What a flower of this owner planted here would shoot now: the marked enemy if it can, else the nearest monster. */
    public static LivingEntity targetFrom(ServerWorld world, PlayerEntity owner, BlockPos pos) {
        List<LivingEntity> targets = targetsFrom(world, owner, pos);
        return targets.isEmpty() ? null : targets.get(0);
    }

    /**
     * Every enemy a flower planted here could shoot, in the order it would choose them: the
     * owner's marked enemy first, then monsters from nearest to farthest. Sight lines and range
     * are measured from the top of the stem.
     */
    public static List<LivingEntity> targetsFrom(ServerWorld world, PlayerEntity owner, BlockPos pos) {
        Vec3d eye = Vec3d.ofBottomCenter(pos).add(0, SentryBloomEntity.PIVOT_HEIGHT, 0);
        List<LivingEntity> targets = new ArrayList<>(world.getEntitiesByClass(LivingEntity.class,
                new Box(pos).expand(RANGE), e -> hostile(e, owner) && reaches(world, owner, eye, e)));
        targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(eye)));
        LivingEntity marked = marked(world, owner.getUuid());
        if (marked != null && !WandAllies.protectedFrom(owner, marked) && reaches(world, owner, eye, marked)) {
            targets.remove(marked);
            targets.add(0, marked);
        }
        return targets;
    }

    private static Vec3d aimPoint(Entity target) {
        return target.getBoundingBox().getCenter();
    }

    private static boolean reaches(ServerWorld world, PlayerEntity owner, Vec3d eye, LivingEntity target) {
        Vec3d aim = aimPoint(target);
        if (aim.squaredDistanceTo(eye) > RANGE * RANGE) return false;
        return world.raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, owner)).getType() == HitResult.Type.MISS;
    }

    private static void fire(ServerWorld world, BlockPos pos, SentryBloomEntity head, PlayerEntity owner, LivingEntity target) {
        Vec3d pivot = head.pivot();
        Vec3d aim = aimPoint(target);
        // Lead a moving target by its travel time; a seed flies straight. Measured from where the
        // target actually went this tick: a held or knocked-back mob keeps a velocity it is not moving at.
        Vec3d drift = new Vec3d(target.getX() - target.lastX, 0, target.getZ() - target.lastZ)
                .multiply(aim.distanceTo(pivot) / SEED_SPEED);
        Vec3d direction = aim.add(drift).subtract(pivot);
        if (direction.lengthSquared() < 1.0e-4) direction = new Vec3d(0, 1, 0);
        direction = direction.normalize();
        // The seed leaves the mouth of the turned head, not the middle of the stem.
        Vec3d muzzle = pivot.add(direction.multiply(SentryBloomEntity.MUZZLE_OFFSET * head.scale()));
        head.fired();
        world.spawnEntity(new SeedProjectileEntity(world, owner, muzzle, direction.multiply(SEED_SPEED), target, pos));
        // The spit: pollen and a petal or two thrown up from the mouth.
        BlockNatureEvents.send(world, BlockNatureEvents.SHOT, head.getId(), muzzle, direction, head.scale());
        world.playSound(null, pos, ModSounds.SPELL_NATURE_SENTRY_BLOOM_SHOT, SoundCategory.PLAYERS, 0.35f, 1.0f);
    }

    private static void wilt(ServerWorld world, Flower flower) {
        TemporaryBlockManager.restoreTemporaryBlocks(world, flower.placement);
        flower.head.discard();
        if (flower.twin != null) flower.twin.discard();
        Vec3d at = Vec3d.ofBottomCenter(flower.pos);
        BlockNatureEvents.send(world, BlockNatureEvents.WILT, at);
        world.playSound(null, flower.pos, ModSounds.SPELL_NATURE_SENTRY_BLOOM_WILT, SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    private static boolean canHold(BlockState state) {
        return (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty()
                && !ModSpellBlocks.isNatureGrowth(state);
    }

    /** A full cube or any block with a flat solid top (a path, farmland, a slab). */
    private static boolean hasFloorTop(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || ModSpellBlocks.isNatureGrowth(state)) return false;
        return state.isSolidBlock(world, pos) || state.isSideSolidFullSquare(world, pos, Direction.UP);
    }
}
