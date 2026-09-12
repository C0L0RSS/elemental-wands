package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Real damage, status ticking and player-owned charge limits in a disposable world. */
public final class NatureBalanceSmokeMod implements ModInitializer {
    private int tick;
    private ServerPlayerEntity player, other;
    private CowEntity direct, ramp, crowd, fireHealing;
    private ItemStack wand;
    private AwakenedTreeEntity tree;

    private static void require(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static void close(float actual, float expected, String why) {
        require(Math.abs(actual - expected) < .001f, why + ": " + actual + " expected " + expected);
    }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("NATURE_FAILED.txt"), error.toString()); }
                catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++;
        ServerWorld world = server.getOverworld();
        if (tick == 30) {
            world.getChunk(0, 0); world.setChunkForced(0, 0, true);
            player = player(server, "GroveTester", 2.5);
            other = player(server, "OtherGrove", 4.5);
            wand = new ItemStack(ModItems.FRACTURED_WAND);
            player.equipStack(EquipmentSlot.MAINHAND, wand);
            other.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.FRACTURED_WAND));
            direct = cow(world, 8); ramp = cow(world, 10); crowd = cow(world, 12);

            hitSeed(world, direct);
            close(direct.getHealth(), 197, "Unstacked direct seed damage");
            require(EntangleTracker.getStacks(direct) == 0, "Direct seed creates Entangle");
            require(AbstractWandItem.getUltimateCharge(wand) == 1, "Seed must give one charge");
            for (int i=0; i<5; i++) EntangleTracker.addStack(world, direct);
            direct.timeUntilRegen = 0;
            hitSeed(world, direct);
            close(direct.getHealth(), 194, "Stacked seed gained bonus damage");
            require(AbstractWandItem.getUltimateCharge(wand) == 1, "Rapid seeds bypass charge cadence");

            NatureCombat.thornContact(world, ramp, player.getUuid());
            close(ramp.getHealth(), 197, "First flower tick");
            for (int i=0; i<10; i++) {
                ramp.timeUntilRegen = 0; // Prove our shared limit, independently of vanilla immunity.
                NatureCombat.thornContact(world, ramp, player.getUuid());
            }
            close(ramp.getHealth(), 197, "Overlapping patches multiply damage");
            require(EntangleTracker.getStacks(ramp) == 1, "Overlap accelerates Entangle");
            NatureCombat.thornContact(world, crowd, player.getUuid());
            close(crowd.getHealth(), 197, "Another enemy should still take damage");
            require(AbstractWandItem.getUltimateCharge(wand) == 4, "Crowd bypasses shared charge limit");
            long flux = player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
            wand = new ItemStack(ModItems.FRACTURED_WAND);
            player.equipStack(EquipmentSlot.MAINHAND, wand);
            NatureCombat.thornContact(world, cow(world, 14), player.getUuid());
            direct.timeUntilRegen = 0; hitSeed(world, direct);
            require(AbstractWandItem.getUltimateCharge(wand) == 0, "Wand swapping resets charge limits");
            require(player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L) > flux,
                    "Charge limiting removed Arcane Flux progression");
        }
        if (tick == 49) {
            ramp.timeUntilRegen = 0;
            NatureCombat.thornContact(world, ramp, player.getUuid());
            close(ramp.getHealth(), 197, "Contact window ended before 20 ticks");
        }
        if (tick >= 50 && tick <= 110 && (tick - 30) % 20 == 0) {
            int stacks = (tick - 30) / 20 + 1;
            float before = ramp.getHealth();
            NatureCombat.thornContact(world, ramp, player.getUuid());
            close(before - ramp.getHealth(), 3 + .5f * (stacks - 1), "Flower damage ramp");
            require(EntangleTracker.getStacks(ramp) == stacks, "Flower stack cadence");
            if (tick == 50) {
                require(AbstractWandItem.getUltimateCharge(wand) == 3, "Charge did not reopen at 20 ticks");
                direct.timeUntilRegen = 0; hitSeed(world, direct);
                require(AbstractWandItem.getUltimateCharge(wand) == 4, "Combined seed/thorn charge");
                crowd.timeUntilRegen = 0;
                NatureCombat.thornContact(world, crowd, other.getUuid());
                require(AbstractWandItem.getUltimateCharge(other.getMainHandStack()) == 3,
                        "Different players incorrectly share charge windows");
            }
        }
        if (tick == 130) {
            CowEntity immune = cow(world, 14); immune.setInvulnerable(true);
            int beforeCharge = AbstractWandItem.getUltimateCharge(wand);
            long beforeFlux = player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
            NatureCombat.thornContact(world, immune, player.getUuid()); hitSeed(world, immune);
            require(AbstractWandItem.getUltimateCharge(wand) == beforeCharge
                    && player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L) == beforeFlux,
                    "Rejected damage earned rewards");
            require(ramp.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() == 6,
                    "Flowers lost ordinary full root");
            // Ultimate damages a nearby real target, but must not refill its own reservoir.
            BlockPos center = new BlockPos(32, 100, 0); world.getChunk(center);
            world.setChunkForced(2, 0, true);
            player.setPosition(35.5, 100, .5);
            CowEntity crushed = cow(world, 38);
            OvergrowthManager.startOvergrowth(world, player, center, 5);
            close(crushed.getHealth(), 182, "Ultimate burst changed");
            require(AbstractWandItem.getUltimateCharge(wand) == beforeCharge, "Ultimate refunds charge");
            tree = world.getEntitiesByClass(AwakenedTreeEntity.class, new Box(center).expand(10), e->true).getFirst();
            NatureCombat.thornContact(world, tree, player.getUuid());
            close(tree.getHealth(), 60, "Thorns damage the tree");
            require(EntangleTracker.getStacks(tree) == 0, "Thorns wrap the tree");
            player.removeStatusEffect(StatusEffects.REGENERATION); player.setHealth(2);
            player.getHungerManager().setFoodLevel(10);
            fireHealing = cow(world, 14); fireHealing.setHealth(2);
        }
        if (tick > 130 && tick <= 430) {
            // Fake players have no connection advancing playerTick, unlike the real client.
            player.playerTick();
            SpellBuffs.regeneration(fireHealing);
        }
        if (tick == 429) {
            require(player.getStatusEffect(StatusEffects.REGENERATION).getAmplifier() == 1,
                    "Tree is not Regeneration II");
            require(fireHealing.getStatusEffect(StatusEffects.REGENERATION).getAmplifier() == 0,
                    "Fire regeneration was changed");
        }
        if (tick == 430) {
            close(player.getHealth(), 14, "Tree must heal twelve health over its lifetime");
            close(fireHealing.getHealth(), 8, "Fire must keep its original six health per 15s");
            require(!tree.isAlive() || tree.isRemoved(), "Tree did not expire after 15 seconds");
            Files.writeString(Path.of("NATURE_PASSED.txt"), "Nature balance: real direct seed damage; no direct Entangle; thorn ramp 3..5; shared contact and charge windows; crowd and swapped-wand limits; independent casters; rejected-hit rewards; preserved Flux; zero ultimate refund; tree exclusion; actual Regeneration II healing and unchanged Regeneration I.\n");
            System.out.println("NATURE BALANCE CHECK PASSED"); server.stop(false);
        }
    }
    private static void hitSeed(ServerWorld world, CowEntity target) {
        // Owner is supplied by the caller's fixture player below.
        var caster = world.getPlayers().stream().filter(p->p.getName().getString().equals("GroveTester")).findFirst().orElseThrow();
        new SeedProjectileEntity(world, caster).onEntityHit(new EntityHitResult(target));
    }
    private static CowEntity cow(ServerWorld world, double x) {
        CowEntity entity = new CowEntity(EntityType.COW, world);
        entity.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);
        entity.setHealth(200); entity.setAiDisabled(true); entity.setNoGravity(true);
        entity.setPosition(x, 100, .5); world.spawnEntity(entity); return entity;
    }
    private static ServerPlayerEntity player(MinecraftServer server, String name, double x) throws Exception {
        var factory = com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",
                MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        factory.setAccessible(true);
        var player = (ServerPlayerEntity) factory.invoke(null, server, UUID.randomUUID(), name, x, 100.0, .5);
        player.onTeleportationDone(); player.setNoGravity(true); return player;
    }
}
