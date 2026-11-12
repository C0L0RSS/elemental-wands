package com.anton.elementalwands.entity;

import com.anton.elementalwands.block.NatureSeedlingBlock;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.item.NatureAbilityHandler;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.FlowerTurretManager;
import com.anton.elementalwands.util.FullBloomManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** Full Bloom in a disposable world: the wave, the blessing, the caster's quickened spells and two-headed flowers. */
public final class FullBloomSmokeMod implements ModInitializer {
    private static final BlockPos FLOWER = new BlockPos(9, 100, 10);
    private int tick;
    private ServerPlayerEntity caster, ally, stranger, farAlly;
    private ZombieEntity near, far;
    private CowEntity cow;
    private ItemStack wand;
    private float nearBefore, farBefore;

    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    // Mount points travel to clients as floats.
    private static boolean near(net.minecraft.util.math.Vec3d a, net.minecraft.util.math.Vec3d b) { return a.squaredDistanceTo(b) < 1.0e-6; }
    private static void close(float actual, float expected, String why) {
        require(Math.abs(actual - expected) < .001f, why + ": " + actual + " expected " + expected);
    }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("BLOOM_FAILED.txt"), error.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++;
        ServerWorld world = server.getOverworld();
        if (tick == 30) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { world.getChunk(x, z); world.setChunkForced(x, z, true); }
            // A lawn, with a stone patch in one corner of the wave's reach.
            for (int x = -4; x <= 22; x++) for (int z = -4; z <= 24; z++)
                world.setBlockState(new BlockPos(x, 99, z), (x <= 3 && z <= 3 ? Blocks.STONE : Blocks.GRASS_BLOCK).getDefaultState());
            caster = player(server, "BloomCaster", 8.5, 8.5); ally = player(server, "BloomAlly", 10.5, 8.5);
            stranger = player(server, "BloomStranger", 6.5, 8.5); farAlly = player(server, "BloomFarAlly", 8.5, 22.5);
            var team = server.getScoreboard().addTeam("bloom_allies"); team.setFriendlyFireAllowed(true);
            for (ServerPlayerEntity member : new ServerPlayerEntity[]{caster, ally, farAlly})
                server.getScoreboard().addScoreHolderToTeam(member.getName().getString(), team);
            wand = new ItemStack(ModItems.FRACTURED_WAND);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, wand, data -> data.putInt("elementalwands:ultimate_charge", 100));
            caster.equipStack(EquipmentSlot.MAINHAND, wand);
            near = zombie(world, 8.5, 12.5); far = zombie(world, 12.5, 8.5);
            cow = new CowEntity(EntityType.COW, world);
            cow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200);
            cow.setAiDisabled(true); cow.setNoGravity(true); cow.setPosition(5.5, 100, 5.5); world.spawnEntity(cow);
            require(FlowerTurretManager.plant(world, caster, FLOWER), "Could not plant the first flower");
        }
        if (tick == 110) { // Past the simulated players' sixty ticks of join protection.
            // Before the bloom: one head, on the nearer monster.
            require(FlowerTurretManager.twinAt(world, FLOWER) == null && !world.getBlockState(FLOWER).get(NatureSeedlingBlock.GROWN),
                    "A flower is grown without Full Bloom");
            ally.setHealth(4); nearBefore = near.getHealth(); farBefore = far.getHealth();
            NatureAbilityHandler.castUltimate(world, caster, wand);
            require(AbstractWandItem.getUltimateCharge(wand) == 0, "Full Bloom did not spend the charge");
            require(FullBloomManager.blooming(caster) && !FullBloomManager.blooming(ally), "Only the caster blooms");
            // The blessing goes to the caster and the allies in reach at the cast, and to nobody else.
            require(Set.copyOf(FullBloomManager.allies(caster)).equals(Set.of(caster.getUuid(), ally.getUuid())), "Wrong players blessed");
            for (ServerPlayerEntity blessed : new ServerPlayerEntity[]{caster, ally}) {
                require(blessed.getStatusEffect(StatusEffects.REGENERATION) != null
                        && blessed.getStatusEffect(StatusEffects.REGENERATION).getAmplifier() == FullBloomManager.REGENERATION_AMPLIFIER,
                        "No Regeneration II on " + blessed.getName().getString());
                close(blessed.getAbsorptionAmount(), 4, "Absorption on " + blessed.getName().getString());
            }
            for (ServerPlayerEntity other : new ServerPlayerEntity[]{stranger, farAlly})
                require(!other.hasStatusEffect(StatusEffects.REGENERATION) && other.getAbsorptionAmount() == 0,
                        "Full Bloom blessed " + other.getName().getString());
        }
        if (tick >= 110 && tick <= 420) { ally.playerTick(); stranger.playerTick(); } // Simulated players have no client driving their effects.
        if (tick == 126) {
            // The wave is harmless: an enemy player, the monster no flower has shot yet and an animal are untouched.
            close(stranger.getHealth(), 20, "The wave hurt an enemy player");
            close(far.getHealth(), farBefore, "The wave hurt a monster");
            close(cow.getHealth(), 200, "The wave hurt an animal");
            require(ally.getHealth() >= 4 && caster.getHealth() == caster.getMaxHealth(), "The wave hurt an ally");
            require(AbstractWandItem.getUltimateCharge(wand) == 0, "Full Bloom refilled itself");
            // On grass it left grass and wild flowers; on stone and beyond its reach, nothing.
            int grown = 0;
            for (int x = -4; x <= 22; x++) for (int z = -4; z <= 24; z++) {
                BlockPos pos = new BlockPos(x, 100, z);
                var state = world.getBlockState(pos);
                boolean plant = state.isOf(Blocks.SHORT_GRASS) || state.isIn(net.minecraft.registry.tag.BlockTags.SMALL_FLOWERS);
                double distance = Math.hypot(x - 8, z - 8);
                if (plant) grown++;
                require(!plant || distance <= FullBloomManager.RADIUS + .01 && !(x <= 3 && z <= 3),
                        "The wave planted " + state + " at " + pos + ", on stone or beyond its reach");
            }
            require(grown >= 20, "The wave left too little growth on grass: " + grown);
            // The flower has grown: a forked base, a second head, one head on each monster.
            require(world.getBlockState(FLOWER).get(NatureSeedlingBlock.GROWN), "The flower's base did not grow");
            var head = FlowerTurretManager.headAt(world, FLOWER); var twin = FlowerTurretManager.twinAt(world, FLOWER);
            require(twin != null && twin.isAlive() && near(head.mountPoint(), SentryBloomEntity.MAIN_STEM_TOP) && head.scale() == SentryBloomEntity.MAIN_SCALE
                    && near(twin.mountPoint(), SentryBloomEntity.BRANCH_TOP) && twin.scale() == SentryBloomEntity.BRANCH_SCALE,
                    "The flower has no second head on its branch");
            require(head.targetId() == near.getId() && twin.targetId() == far.getId(),
                    "Two heads did not take two monsters, nearest first: " + head.targetId() + " " + twin.targetId());
        }
        if (tick == 130) {
            // The caster's Seed and Sentry Bloom recover faster while they bloom.
            caster.setYaw(0); caster.setHeadYaw(0); caster.setPitch(45);
            NatureAbilityHandler.castPrimary(world, caster, wand);
            require(duration("seed") == FullBloomManager.SEED_COOLDOWN, "Seed cooldown in Full Bloom: " + duration("seed"));
        }
        if (tick == 138) {
            NatureAbilityHandler.castSecondary(world, caster, wand);
            // This simulated player has bought nothing, so the cast is stored under its slot, not the spell's name.
            String slot = AbstractWandItem.castingSpell(caster, AbstractWandItem.Ability.SECONDARY);
            require(duration(slot) == FlowerTurretManager.BLOOM_COOLDOWN, "Sentry Bloom cooldown in Full Bloom: " + duration(slot));
        }
        if (tick == 146) // The thrown bud has landed.
            require(FlowerTurretManager.count(world, caster.getUuid()) == 2, "Sentry Bloom did not plant during Full Bloom");
        if (tick == 150) FlowerTurretManager.mark(caster, far); // The marked enemy goes to the first head, the other monster to the second.
        if (tick == 160) {
            var head = FlowerTurretManager.headAt(world, FLOWER); var twin = FlowerTurretManager.twinAt(world, FLOWER);
            require(head.targetId() == far.getId() && twin.targetId() == near.getId(), "The mark did not go to the first head");
            // A flower planted during the bloom unfurls, then grows too.
            BlockPos second = FlowerTurretManager.positions(world, caster.getUuid()).stream().filter(p -> !p.equals(FLOWER)).findFirst().orElseThrow();
            require(FlowerTurretManager.twinAt(world, second) != null, "A flower planted in Full Bloom did not grow");
        }
        if (tick == 230) {
            // Each head keeps the single head's pace, half a beat apart: together twice the seeds.
            var head = FlowerTurretManager.headAt(world, FLOWER); var twin = FlowerTurretManager.twinAt(world, FLOWER);
            require(head.shots() >= 3 && twin.shots() >= 2 && Math.abs(head.shots() - 1 - twin.shots()) <= 1,
                    "The two heads are not firing in turn: " + head.shots() + " and " + twin.shots());
            require(near.getHealth() < nearBefore - 4 && far.getHealth() < farBefore - 4, "Both monsters were not shot");
            require(AbstractWandItem.getUltimateCharge(wand) == 0, "Flower hits refilled Full Bloom while it ran");
        }
        if (tick == 348) require(FullBloomManager.blooming(caster), "Full Bloom ended early");
        if (tick == 354) {
            require(!FullBloomManager.blooming(caster), "Full Bloom outlasted its twelve seconds");
            var head = FlowerTurretManager.headAt(world, FLOWER);
            require(FlowerTurretManager.twinAt(world, FLOWER) == null && !world.getBlockState(FLOWER).get(NatureSeedlingBlock.GROWN)
                    && near(head.mountPoint(), new net.minecraft.util.math.Vec3d(0, SentryBloomEntity.PIVOT_HEIGHT, 0)) && head.scale() == 1,
                    "The flower did not return to one head");
        }
        if (tick == 420) {
            // Regeneration II for the bloom and its last lingering seconds, as the old oak healed over its fifteen.
            require(ally.getHealth() >= 4 + 10 && ally.getHealth() <= 4 + 13, "Full Bloom healing on an ally: " + ally.getHealth());
            require(!stranger.hasStatusEffect(StatusEffects.REGENERATION) && stranger.getAbsorptionAmount() == 0, "A stranger was blessed later");
            close(stranger.getHealth(), 20, "A stranger was hurt");
        }
        if (tick == 580) {
            // Planted at tick 30, the flower would have wilted by 510. Its twelve seconds grown did not count.
            var head = FlowerTurretManager.headAt(world, FLOWER);
            require(head != null && head.isAlive() && !head.wilting() && world.getBlockState(FLOWER).isOf(ModSpellBlocks.NATURE_SEEDLING),
                    "A flower aged while it was grown");
            Files.writeString(Path.of("BLOOM_PASSED.txt"), "Full Bloom: charge spent and not refilled by the wave or by flowers; Regeneration II and two hearts of absorption for the caster and allies in reach at the cast, not for a stranger or a far ally; the wave hurts nobody and leaves grass and wild flowers on grass only, inside its reach; flowers grow a bigger base, a branch and a smaller second head, the heads take two monsters nearest first or the marked one first, fire in turn at the single head's pace, and a flower planted mid-bloom grows too; quicker Seed and Sentry Bloom cooldowns; twelve-second duration, one head again afterwards; about twelve health healed; no ageing while grown.\n");
            System.out.println("FULL BLOOM CHECK PASSED"); server.stop(false);
        }
    }
    private int duration(String spell) {
        return wand.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt(AbstractWandItem.durationKey(spell), 0);
    }
    private static ZombieEntity zombie(ServerWorld world, double x, double z) {
        ZombieEntity zombie = new ZombieEntity(EntityType.ZOMBIE, world);
        zombie.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200); zombie.setHealth(200);
        zombie.setAiDisabled(true); zombie.setNoGravity(true);
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        zombie.setPosition(x, 100, z); world.spawnEntity(zombie); return zombie;
    }
    private static ServerPlayerEntity player(MinecraftServer server, String name, double x, double z) throws Exception {
        var factory = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",
                MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        factory.setAccessible(true);
        var player = (ServerPlayerEntity) factory.invoke(null, server, UUID.randomUUID(), name, x, 100.0, z);
        player.setAttached(EWAttachments.AFFINITY, "NATURE");
        player.onTeleportationDone(); player.setNoGravity(true); player.getHungerManager().setFoodLevel(10); return player;
    }
}
