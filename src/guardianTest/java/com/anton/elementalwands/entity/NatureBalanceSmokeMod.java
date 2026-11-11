package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.item.NatureAbilityHandler;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModSpellBlocks;
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

/** Real damage, the seed mark, Sentry Bloom flowers and the solid-trunked oak in a disposable world. */
public final class NatureBalanceSmokeMod implements ModInitializer {
    private static final BlockPos CENTER = new BlockPos(32, 100, 0);
    private int tick;
    private ServerPlayerEntity player, other;
    private CowEntity direct, ramp, crowd, fireHealing, inTrunk;
    private net.minecraft.entity.mob.ZombieEntity zombie;
    private BlockPos flower;
    private float directBefore, zombieBefore;
    private int chargeBefore;
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
            direct = cow(world, 8); ramp = cow(world, 10); crowd = cow(world, 14);
            for (int x = 0; x <= 15; x++) for (int z = 0; z <= 6; z++)
                world.setBlockState(new BlockPos(x, 99, z), net.minecraft.block.Blocks.STONE.getDefaultState());
            zombie = new net.minecraft.entity.mob.ZombieEntity(EntityType.ZOMBIE, world);
            zombie.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);
            zombie.setHealth(200); zombie.setAiDisabled(true); zombie.setNoGravity(true);
            zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
            zombie.setPosition(12.5, 100, 4.5); world.spawnEntity(zombie);

            // A Seed deals its flat damage, marks its target for the caster's flowers, and nothing else.
            hitSeed(world, player, direct);
            close(direct.getHealth(), 200 - NatureCombat.SEED_DAMAGE, "Direct seed damage");
            require(FlowerTurretManager.marked(world, player.getUuid()) == direct, "A Seed hit must mark its target");
            require(EntangleTracker.getStacks(direct) == 0 && direct.getStatusEffect(StatusEffects.SLOWNESS) == null,
                    "A Seed must not wind vines or slow");
            require(AbstractWandItem.getUltimateCharge(wand) == NatureCombat.HIT_CHARGE, "Seed charge");
            direct.timeUntilRegen = 0;
            hitSeed(world, player, direct);
            close(direct.getHealth(), 200 - 2 * NatureCombat.SEED_DAMAGE, "Second Seed damage");
            require(AbstractWandItem.getUltimateCharge(wand) == NatureCombat.HIT_CHARGE, "Rapid seeds bypass charge cadence");
        }
        if (tick == 32) {
            long flux = WandProgression.flux(player);
            wand = new ItemStack(ModItems.FRACTURED_WAND);
            player.equipStack(EquipmentSlot.MAINHAND, wand);
            direct.timeUntilRegen = 0; hitSeed(world, player, direct);
            require(AbstractWandItem.getUltimateCharge(wand) == 0, "Wand swapping resets charge limits");
            require(WandProgression.flux(player) > flux, "Charge limiting removed Arcane Flux progression");
        }
        if (tick == 50) {
            direct.timeUntilRegen = 0; hitSeed(world, player, direct);
            require(AbstractWandItem.getUltimateCharge(wand) == NatureCombat.HIT_CHARGE, "Charge did not reopen at 20 ticks");
            crowd.timeUntilRegen = 0; hitSeed(world, other, crowd);
            require(AbstractWandItem.getUltimateCharge(other.getMainHandStack()) == NatureCombat.HIT_CHARGE,
                    "Different players incorrectly share charge windows");
            require(FlowerTurretManager.marked(world, player.getUuid()) == direct
                    && FlowerTurretManager.marked(world, other.getUuid()) == crowd, "Players share a seed mark");
        }
        if (tick == 52) {
            // Sentry Bloom through the real cast. Aimed at the sky it finds no ground and costs nothing.
            player.setYaw(0); player.setPitch(-90);
            NatureAbilityHandler.castSecondary(world, player, wand);
            require(FlowerTurretManager.count(world, player.getUuid()) == 0, "A flower was planted in the sky");
            player.setPitch(45);
            NatureAbilityHandler.castSecondary(world, player, wand);
            require(FlowerTurretManager.count(world, player.getUuid()) == 1, "A failed cast started the cooldown, or the cast planted nothing");
            flower = FlowerTurretManager.positions(world, player.getUuid()).getFirst();
            require(flower.equals(new BlockPos(2, 100, 2)), "The flower was not planted where the caster aimed: " + flower);
            var state = world.getBlockState(flower);
            require(state.isOf(ModSpellBlocks.NATURE_SEEDLING), "No flower block at " + flower + ": " + state);
            require(state.getCollisionShape(world, flower).isEmpty() && state.getOutlineShape(world, flower).isEmpty(),
                    "A flower is solid or can be aimed at");
            player.setPitch(60);
            NatureAbilityHandler.castSecondary(world, player, wand);
            require(FlowerTurretManager.count(world, player.getUuid()) == 1, "Sentry Bloom ignored its cooldown");
            directBefore = direct.getHealth(); chargeBefore = AbstractWandItem.getUltimateCharge(wand);
        }
        if (tick == 75) {
            // Its first seed went to the marked cow, not to the monster or the unmarked cow.
            close(direct.getHealth(), directBefore - FlowerTurretManager.DAMAGE, "The flower did not shoot the marked target once");
            close(zombie.getHealth(), 200, "The flower ignored its owner's mark");
            close(ramp.getHealth(), 200, "The flower shot an unmarked animal");
            require(AbstractWandItem.getUltimateCharge(wand) == chargeBefore + NatureCombat.FLOWER_CHARGE, "Flower hit charge");
            // Its head is looking at what it shot, and has fired once.
            var head = FlowerTurretManager.headAt(world, flower);
            require(head != null && head.isAlive() && head.targetId() == direct.getId() && head.shots() == 1 && !head.wilting(),
                    "The flower's head is not turned on its target after one shot");
            require(!head.canHit() && !head.canBeHitByProjectile() && !head.damage(world, world.getDamageSources().playerAttack(other), 20),
                    "The flower's head can be hit");
            // A flower's hit leaves no hurt cooldown behind, so the caster's next Seed lands in full...
            require(direct.timeUntilRegen <= 10, "A flower's hit opened a hurt cooldown: " + direct.timeUntilRegen);
            hitSeed(world, player, direct);
            close(direct.getHealth(), directBefore - FlowerTurretManager.DAMAGE - NatureCombat.SEED_DAMAGE, "A flower's hit shaved the next Seed");
            // ...and a flower's seed landing inside the Seed's own cooldown still counts.
            var spit = new SeedProjectileEntity(world, player, direct.getEntityPos().add(0, 3, 0), new net.minecraft.util.math.Vec3d(0, -1, 0), direct);
            require(spit.canHit(direct) && spit.canHit(zombie) && !spit.canHit(ramp) && !spit.canHit(other) && !spit.canHit(player),
                    "A flower's seed must strike only its target and monsters");
            // A flower's hit returns a fifth of the health it took to its owner.
            player.setHealth(10);
            spit.onEntityHit(new EntityHitResult(direct));
            close(direct.getHealth(), directBefore - 2 * FlowerTurretManager.DAMAGE - NatureCombat.SEED_DAMAGE, "The Seed's cooldown swallowed a flower's hit");
            close(player.getHealth(), 10 + FlowerTurretManager.DAMAGE * FlowerTurretManager.HEAL_SHARE, "Flower hit healing");
            close(other.getHealth(), other.getMaxHealth(), "A flower healed someone else");
            player.setHealth(player.getMaxHealth());
            // A flower's hit is not the caster's: it does not move the mark.
            new SeedProjectileEntity(world, player, zombie.getEntityPos().add(0, 3, 0), new net.minecraft.util.math.Vec3d(0, -1, 0), zombie)
                    .onEntityHit(new EntityHitResult(zombie));
            require(zombie.getHealth() < 200, "A flower seed did not damage a monster"); // Its helmet takes a share.
            require(FlowerTurretManager.marked(world, player.getUuid()) == direct, "A flower's hit moved the mark");
        }
        if (tick == 126) world.setBlockState(flower, net.minecraft.block.Blocks.AIR.getDefaultState()); // Whatever removes the block removes the flower.
        if (tick == 128) {
            require(FlowerTurretManager.count(world, player.getUuid()) == 0 && FlowerTurretManager.headAt(world, flower) == null,
                    "A flower outlived its block");
            // The rest is a second player's garden, so its healing stays out of the oak's healing check below.
            flower = new BlockPos(2, 100, 4);
            require(FlowerTurretManager.plant(world, other, flower), "Could not plant for the second player");
        }
        if (tick == 180) { // The marks from ticks 50 and 75 have run out.
            require(FlowerTurretManager.marked(world, player.getUuid()) == null && FlowerTurretManager.marked(world, other.getUuid()) == null,
                    "The seed mark never expired");
            directBefore = direct.getHealth(); zombieBefore = zombie.getHealth();
        }
        if (tick == 230) {
            // Unprompted, a flower shoots the nearest monster and leaves animals and players alone.
            require(zombie.getHealth() < zombieBefore, "An unmarked flower did not shoot the monster: " + zombie.getHealth() + " target " + FlowerTurretManager.targetFrom(world, other, flower));
            close(direct.getHealth(), directBefore, "The flower kept shooting after the mark expired");
            close(ramp.getHealth(), 200, "The flower shot an animal");
            require(FlowerTurretManager.headAt(world, flower).targetId() == zombie.getId(), "An unmarked flower is not looking at the monster");
            // A fourth flower replaces the oldest.
            for (BlockPos spot : new BlockPos[]{new BlockPos(4, 100, 5), new BlockPos(6, 100, 5), new BlockPos(8, 100, 5)})
                require(FlowerTurretManager.plant(world, other, spot), "Could not plant at " + spot);
            require(FlowerTurretManager.count(world, other.getUuid()) == FlowerTurretManager.MAX_FLOWERS, "Flower limit");
            require(world.getBlockState(flower).isAir() && FlowerTurretManager.headAt(world, flower) == null, "The oldest flower was not replaced");
            require(!FlowerTurretManager.plant(world, other, new BlockPos(4, 100, 5)), "Two flowers share one spot");
        }
        if (tick == 130) {
            CowEntity immune = cow(world, 6); immune.setInvulnerable(true);
            int beforeCharge = AbstractWandItem.getUltimateCharge(wand);
            long beforeFlux = WandProgression.flux(player);
            hitSeed(world, player, immune);
            require(AbstractWandItem.getUltimateCharge(wand) == beforeCharge
                    && WandProgression.flux(player) == beforeFlux, "Rejected damage earned rewards");
            require(FlowerTurretManager.marked(world, player.getUuid()) != immune, "A rejected Seed still marked its target");
            // Ultimate damages a nearby real target, but must not refill its own reservoir.
            world.getChunk(CENTER); world.setChunkForced(2, 0, true);
            // The caster stands beside the trunk; a cow stands exactly where the trunk will grow.
            player.setPosition(CENTER.getX() + 2.5, CENTER.getY(), CENTER.getZ() + .5);
            inTrunk = cow(world, CENTER.getX() + .5);
            CowEntity crushed = cow(world, 38);
            OvergrowthManager.startOvergrowth(world, player, CENTER);
            close(crushed.getHealth(), 194, "Ultimate burst changed");
            require(crushed.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() == 6, "Roots did not pin an ordinary enemy");
            require(EntangleTracker.getStacks(crushed) == 0, "Ultimate roots created vines");
            require(AbstractWandItem.getUltimateCharge(wand) == beforeCharge, "Ultimate refunds charge");
            tree = world.getEntitiesByClass(AwakenedTreeEntity.class, new Box(CENTER).expand(10), e->true).getFirst();
            require(!tree.damage(world, world.getDamageSources().playerAttack(other), 30), "The oak can be attacked");
            close(tree.getHealth(), 60, "The oak took damage");
            FlowerTurretManager.mark(player, tree);
            require(FlowerTurretManager.marked(world, player.getUuid()) != tree && !FlowerTurretManager.hostile(tree, player),
                    "Flowers can be turned on the oak");
            require(!tree.canHit() && !tree.canBeHitByProjectile(), "The oak's anchor can be aimed at");
            player.removeStatusEffect(StatusEffects.REGENERATION); player.setHealth(2);
            player.getHungerManager().setFoodLevel(10);
            fireHealing = cow(world, 14); fireHealing.setHealth(2);
        }
        if (tick == 200) {
            // A solid trunk under a walk-through canopy.
            for (BlockPos pos : new BlockPos[]{CENTER.up(2), CENTER.up(3), CENTER.west(4).up(4)}) {
                var state = world.getBlockState(pos);
                require((state.isOf(ModSpellBlocks.NATURE_OAK_LOG) || state.isOf(ModSpellBlocks.NATURE_HEARTWOOD))
                        && state.isFullCube(world, pos) && state.getHardness(world, pos) < 0, "Trunk is not a solid, unbreakable block at " + pos + ": " + state);
            }
            var leaves = world.getBlockState(CENTER.up(8));
            require(leaves.isOf(ModSpellBlocks.NATURE_OAK_LEAVES) && leaves.getCollisionShape(world, CENTER.up(8)).isEmpty()
                    && leaves.getOutlineShape(world, CENTER.up(8)).isEmpty(), "Canopy is solid or targetable: " + leaves);
            // The trunk never grows through an occupant: that piece is missing, and nobody is lifted or trapped.
            require(world.getBlockState(CENTER).isAir() && world.getBlockState(CENTER.up()).isAir(), "The trunk grew through an occupant: " + world.getBlockState(CENTER));
            require(Math.abs(inTrunk.getY() - 100) < .01 && !inTrunk.isInsideWall(), "The oak lifted or trapped an occupant");
            require(world.getBlockState(CENTER.up(2)).isOf(ModSpellBlocks.NATURE_HEARTWOOD), "Tree lost its custom heart");
            require(world.getBlockState(CENTER.west(4).up(4)).isOf(ModSpellBlocks.NATURE_OAK_LOG), "Tree lost its outstretched branch");
            require(world.getBlockState(CENTER.up(8)).isOf(ModSpellBlocks.NATURE_OAK_LEAVES), "Tree is not nine blocks tall");
            require(NatureTreeLayout.cells().size() == 169, "Approved tree layout changed");
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
            require(world.getBlockState(CENTER.up(2)).isAir() && world.getBlockState(CENTER.west(4).up(4)).isAir(),
                    "Expired tree left blocks behind");
        }
        if (tick == 690) // In their last two seconds flowers hang their heads and stop shooting.
            require(FlowerTurretManager.headAt(world, new BlockPos(4, 100, 5)).wilting(), "A flower in its last two seconds is not wilting");
        if (tick == 715) { // The flowers planted at tick 230 have lived their 24 seconds.
            require(FlowerTurretManager.count(world, other.getUuid()) == 0, "Flowers did not wilt after their lifetime");
            for (BlockPos spot : new BlockPos[]{new BlockPos(4, 100, 5), new BlockPos(6, 100, 5), new BlockPos(8, 100, 5)})
                require(world.getBlockState(spot).isAir(), "A wilted flower left its block at " + spot);
            require(zombie.isAlive() && zombie.getHealth() < zombieBefore, "Three flowers did not keep shooting the monster");
            Files.writeString(Path.of("NATURE_PASSED.txt"), "Nature balance: flat Seed damage with a per-player seed mark and no vines or slow; Sentry Bloom through the real cast with aimed placement, free failed cast, cooldown, a pass-through untargetable flower block, a limit of three with the oldest replaced; flowers shoot the marked target first, then the nearest monster, never an animal or bystander; flower hits bypass and leave no hurt cooldown, earn their own charge, heal their owner a fifth of the damage and do not move the mark; an untouchable head turned on the target, wilting in the last two seconds; a flower ends with its block; mark expiry; shared and per-player charge windows; swapped-wand limit; rejected-hit rewards; preserved Flux; zero ultimate refund and no vines from the ultimate; the oak cannot be marked; untouchable oak with a solid unbreakable trunk that never grows through an occupant, under a pass-through canopy; actual Regeneration II healing and unchanged Regeneration I; blocks restored on expiry; flowers wilt after 24 seconds and restore their ground.\n");
            System.out.println("NATURE BALANCE CHECK PASSED"); server.stop(false);
        }
    }
    private static void hitSeed(ServerWorld world, ServerPlayerEntity caster, CowEntity target) {
        new SeedProjectileEntity(world, caster).onEntityHit(new EntityHitResult(target));
    }
    private static CowEntity cow(ServerWorld world, double x) {
        CowEntity entity = new CowEntity(EntityType.COW, world);
        entity.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);
        entity.setHealth(200); entity.setAiDisabled(true); entity.setNoGravity(true);
        entity.setPosition(x, 100, .5); world.spawnEntity(entity); return entity;
    }
    private static ServerPlayerEntity player(MinecraftServer server, String name, double x) throws Exception {
        var factory = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",
                MinecraftServer.class, UUID.class, String.class, double.class, double.class, double.class);
        factory.setAccessible(true);
        var player = (ServerPlayerEntity) factory.invoke(null, server, UUID.randomUUID(), name, x, 100.0, .5);
        player.setAttached(EWAttachments.AFFINITY, "NATURE");
        player.onTeleportationDone(); player.setNoGravity(true); return player;
    }
}
