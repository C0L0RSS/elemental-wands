package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.item.FireAbilityHandler;
import com.anton.elementalwands.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;

/**
 * Actual nave floor/spell writes and attacks; isolated from player saves by the optional init script.
 * Nature must treat the Guardian as every wand does: same damage, no hold, and nothing of its own
 * in the Guardian's behaviour.
 */
public final class GuardianNatureSmokeMod implements ModInitializer {
    private int tick, phase, started, floor;
    private long attackStarted;
    private ServerPlayerEntity player;
    private FracturedGuardianEntity guardian;
    private GuardianBossCombat combat;
    private ItemStack wand;
    private BlockPos seed, coals;
    private net.minecraft.block.BlockState coalsFloor;
    private AwakenedTreeEntity tree;
    private float treeHealth, flowerHealth;
    private BlockPos bloom;
    private double guardianY;
    private static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch(Throwable e) { e.printStackTrace(); try { Files.writeString(Path.of("NATURE_FAILED.txt"),e.toString()); } catch(Exception ignored) {} server.stop(false); }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++; ServerWorld world=player!=null && GuardianArenaManager.inRealm(player)?(ServerWorld)player.getEntityWorld():server.getOverworld();
        require(tick<2600,"Nature fixture timed out: "+phase+" "+GuardianArenaManager.status());
        if(player!=null && player.isAlive() && phase!=8)player.setHealth(player.getMaxHealth());
        if(tick==30) {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"NatureTester",8.5,(double)ground,.5);
            player.onTeleportationDone(); player.setOnGround(true);
            wand=new ItemStack(ModItems.FRACTURED_WAND);player.equipStack(EquipmentSlot.MAINHAND,wand);
            // Slot 0 of a fresh nave is centred on x=0, z=0, like the fixture's authored positions.
            GuardianArenaManager.enter(player,0); phase=10;
        }
        if(phase==10 && GuardianArenaManager.inRealm(player)) {
            // A real client confirms the move into the nave; until then a player cannot be hurt.
            player.onTeleportationDone();
            require(GuardianArenaManager.summon(player).startsWith("The Guardian wakes"),"Could not summon the nave's Guardian");
            // Simulated players do not keep chunks ticking as a real client does; hold the floor loaded like the Overworld's.
            for(int x=-5;x<=4;x++)for(int z=-5;z<=4;z++)((ServerWorld)player.getEntityWorld()).setChunkForced(x,z,true); phase=11;
        }
        if(phase==11) {
            guardian=world.getEntitiesByClass(FracturedGuardianEntity.class,player.getBoundingBox().expand(80),e->e.isAlive()).stream().findFirst().orElse(null);
            // The nave fixture plays the intro; here it is skipped.
            if(guardian!=null && guardian.inIntro()) GuardianIntro.skip(player);
            if(guardian!=null && GuardianArenaManager.isFighting(guardian)) {
                var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(guardian);phase=1;
            }
        }
        if(phase==1) {
            guardian.stopReview();floor=player.getBlockY()-1;
            // Casts aim with the head, which the arrival teleport turned toward the seat; a client would turn it back.
            player.setPosition(-20.5,floor+1,-20.5); player.setYaw(0);player.setHeadYaw(0);player.setPitch(0);
            player.equipStack(EquipmentSlot.MAINHAND,wand);
            testRootVisualOwnership(world);
            coalsFloor=world.getBlockState(new BlockPos(-21,floor,-15));
            FireAbilityHandler.castSecondary(world,player,wand);
            require(!world.getEntitiesByClass(FireAbilityHandler.PyreSchedulerEntity.class,player.getBoundingBox().expand(4),e->true).isEmpty(),
                    "Fire secondary did not start its Pyre in the nave");
            var original=world.getBlockState(new BlockPos(25,floor,25));
            var placement=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(new BlockPos(25,floor,25)),ModSpellBlocks.PYRE_COALS.getDefaultState(),20,s->true);
            require(placement.placedCount()==1,"Tracked floor surface rejected");
            require(!world.setBlockState(new BlockPos(25,floor,25),Blocks.AIR.getDefaultState()),"Reskinned floor became breakable");
            TemporaryBlockManager.restoreTemporaryBlocks(world,placement);
            require(world.getBlockState(new BlockPos(25,floor,25)).equals(original),"Floor failed exact restoration");
            require(TemporaryBlockManager.placeTemporaryBlocks(world,List.of(new BlockPos(25,floor,25)),Blocks.AIR.getDefaultState(),20,s->true)==0,"Spell API can punch a floor hole");
            coals=new BlockPos(-21,floor,-15);started=tick;phase=2;
        }
        if(phase==2 && tick-started==50) {
            require(world.getBlockState(coals).isOf(ModSpellBlocks.PYRE_COALS),"Real Fire secondary has no coals on the nave floor");
            require(world.getBlockState(coals.up()).isOf(ModSpellBlocks.PYRE_FLAME),"Real Fire secondary has no flames");
            player.setPosition(coals.getX()+.5,floor+1,coals.getZ()+.5);
            FireAbilityHandler.inventoryTick(wand,world,player,EquipmentSlot.MAINHAND);
            require(player.hasStatusEffect(StatusEffects.SPEED)&&player.hasStatusEffect(StatusEffects.REGENERATION),"Pyre did not grant both buffs");
            player.setHealth(10);player.getHungerManager().setFoodLevel(10);started=tick;phase=8;
        }
        if(phase==8) {
            // This fixture has no real network connection to call playerTick; advance status timers explicitly.
            player.playerTick();
            FireAbilityHandler.inventoryTick(wand,world,player,EquipmentSlot.MAINHAND);
        }
        if(phase==8 && tick-started==30) {
            require(player.getHealth()>10,"Pyre shows Regeneration but never heals");
            player.getHungerManager().setFoodLevel(20);
            guardian.setPosition(10.5,floor+1,7.5);guardian.setOnGround(true);
            player.setPosition(10.5,floor+1,18.5);
            player.setAttached(com.anton.elementalwands.data.EWAttachments.AFFINITY,"NATURE");
            WandProgression.grant(player,3);
            require(WandLoadouts.get(player).contains("tendril_bloom"),"Fixture could not equip Sentry Bloom");
            guardian.testAttack(player,GuardianCombatRules.Attack.SHOCKWAVE);attackStarted=world.getTime();
            // A Seed damages and marks the Guardian like anything else; a flower's seed lands inside
            // the Seed's own hurt window; nothing slows or holds it.
            float health=guardian.getHealth();
            new SeedProjectileEntity(world,player).onEntityHit(new net.minecraft.util.hit.EntityHitResult(guardian));
            require(guardian.getHealth()<health && FlowerTurretManager.marked(world,player.getUuid())==guardian,"A Seed did not damage and mark the Guardian");
            require(FlowerTurretManager.hostile(guardian,player),"Flowers would not shoot the Guardian unprompted");
            health=guardian.getHealth();
            new SeedProjectileEntity(world,player,guardian.getEntityPos().add(0,8,0),new Vec3d(0,-1,0),guardian)
                    .onEntityHit(new net.minecraft.util.hit.EntityHitResult(guardian));
            require(guardian.getHealth()<health,"The Seed's hurt window swallowed a flower's seed on the Guardian");
            require(!guardian.hasStatusEffect(StatusEffects.SLOWNESS) && EntangleTracker.getStacks(guardian)==0,"Nature slowed the Guardian or wound vines on it");
            // A flower thrown onto the fight floor through the real cast.
            player.setPitch(45);
            com.anton.elementalwands.item.NatureAbilityHandler.castSecondary(world,player,wand);
            flowerHealth=guardian.getHealth();
            started=tick;phase=3;
        }
        if(phase==3 && tick-started==8) {
            require(FlowerTurretManager.count(world,player.getUuid())==1,"Sentry Bloom's bud could not plant on the fight floor");
            bloom=FlowerTurretManager.positions(world,player.getUuid()).getFirst();
        }
        if(phase==3 && tick-started==60) {
            require(FlowerTurretManager.count(world,player.getUuid())==1 && world.getBlockState(bloom).isOf(ModSpellBlocks.NATURE_SEEDLING),
                    "The flower did not outlast the Guardian's attack: block "+world.getBlockState(bloom)+" at "+bloom+", owner "+world.getPlayerByUuid(player.getUuid())
                            +", alive "+player.isAlive()+", spectator "+player.isSpectator()+", loaded "+world.isChunkLoaded(bloom));
            require(guardian.getHealth()<flowerHealth,"The flower did not shoot the Guardian");
            var next=GuardianBossCombat.class.getDeclaredField("nextAction");next.setAccessible(true);
            require(next.getLong(combat)==attackStarted+GuardianCombatRules.Attack.SHOCKWAVE.duration+GuardianCombatRules.RECOVERY_GAP,
                    "Nature changed the Guardian's attack timing or recovery");
            guardian.stopReview();
            // The oak grows right where the Guardian stands. It must not lift, push or block it.
            guardian.setPosition(10.5,floor+1,10.5);guardian.setVelocity(Vec3d.ZERO);guardian.setOnGround(true);
            seed=guardian.getBlockPos();guardianY=guardian.getY();
            float health=guardian.getHealth();
            OvergrowthManager.startOvergrowth(world,player,seed);
            require(guardian.getHealth()<health,"Root crush did not damage the Guardian");
            require(!guardian.hasStatusEffect(StatusEffects.SLOWNESS) && EntangleTracker.getStacks(guardian)==0,
                    "Root crush held the Guardian or wound vines on it");
            tree=world.getEntitiesByClass(AwakenedTreeEntity.class,new Box(seed).expand(10),e->e.isAlive()).getFirst();
            testTreeIsUntouchable(world);
            treeHealth=tree.getHealth();started=tick;phase=4;
        }
        if(phase==4 && tick-started==50) {
            require(world.getBlockState(seed.west(4).up(4)).isOf(ModSpellBlocks.NATURE_OAK_LOG),"Tree lost the approved outstretched branch");
            // Every solid piece inside the Guardian's body is missing, the heartwood included.
            for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(guardian.getBoundingBox().getMinPos()),BlockPos.ofFloored(guardian.getBoundingBox().getMaxPos().subtract(.001,.001,.001))))
                require(world.getBlockState(cell).getCollisionShape(world,cell).isEmpty(),"The oak grew a solid block inside the Guardian at "+cell+": "+world.getBlockState(cell));
            require(world.getBlockState(seed.up(8)).isOf(ModSpellBlocks.NATURE_OAK_LEAVES),"Tree is not nine blocks tall");
            require(com.anton.elementalwands.util.NatureTreeLayout.cells().size()==169,"Approved tree layout changed");
            // The trunk is solid, so it never grows through the Guardian: nothing lifts or traps it.
            require(Math.abs(guardian.getY()-guardianY)<.01,"The oak lifted the Guardian: "+guardian.getY()+" from "+guardianY);
            require(!guardian.isInsideWall(),"The Guardian is suffocating inside the oak");
            require(!tree.damage(world,world.getDamageSources().mobAttack(guardian),10) && tree.getHealth()==treeHealth,"The Guardian can damage the oak");
            OvergrowthManager.destroyTree(world,tree);
            require(world.getBlockState(seed.up(2)).isAir() && world.getBlockState(seed.west(4).up(4)).isAir(),"Tree custom heart/branches did not restore");
            guardian.stopReview();guardian.setPosition(-30.5,floor+1,-10.5);guardian.setVelocity(Vec3d.ZERO);guardian.setOnGround(true);
            player.setPosition(-30.5,floor+1,9.5);
            guardian.testAttack(player,GuardianCombatRules.Attack.LEAP);started=tick;phase=5;
        }
        if(phase==5 && tick-started==25) {
            var crush=OvergrowthManager.class.getDeclaredMethod("applyRootCrush",ServerWorld.class,net.minecraft.entity.player.PlayerEntity.class,BlockPos.class,float.class);crush.setAccessible(true);
            var before=guardian.getVelocity();crush.invoke(null,world,player,guardian.getBlockPos(),1f);
            require(guardian.getVelocity().equals(before),"Nature ultimate changed committed leap velocity");
            new SeedProjectileEntity(world,player).onEntityHit(new net.minecraft.util.hit.EntityHitResult(guardian));
            new SeedProjectileEntity(world,player,guardian.getEntityPos().add(0,8,0),new Vec3d(0,-1,0),guardian)
                    .onEntityHit(new net.minecraft.util.hit.EntityHitResult(guardian));
            require(guardian.getVelocity().equals(before),"A Seed or a flower's seed changed committed leap velocity");
        }
        if(phase==5 && tick-started==110) {
            require(guardian.getZ()>0 && !guardian.hasNoGravity(),"Leap under Nature fire did not finish: "+guardian.leapStatus());
            guardian.stopReview();GuardianArenaManager.leave(player);phase=6;
        }
        // The Pyre's coals expire on their own; the nave floor comes back exactly as it was.
        if(phase==6 && !GuardianArenaManager.status().contains("fight:")
                && server.getWorld(com.anton.elementalwands.arena.ShatteredNave.WORLD).getBlockState(coals).equals(coalsFloor)) {
            Files.writeString(Path.of("NATURE_PASSED.txt"),"Real nave floor: Fire coals/flames/buffs; protected tracked floor restoration; a Seed damages and marks the Guardian without slowing it; a flower's seed lands inside the Seed's hurt window; a flower planted on the fight floor by the real cast shoots the Guardian and outlasts its attack; attack timing and recovery unchanged by Nature; root crush damages without holding; the oak's solid trunk never grows through the Guardian, so it is neither lifted nor trapped, and the oak cannot be damaged; a leap uninterrupted by Seeds, flower seeds or root crush; floor restored after the fight.\n");
            System.out.println("GUARDIAN NATURE ARENA CHECK PASSED");server.stop(false);
        }
    }
    private void testRootVisualOwnership(ServerWorld world) throws Exception {
        var remaining=EntangleTracker.class.getDeclaredMethod("getRootVisualTicksRemaining",net.minecraft.entity.LivingEntity.class);
        remaining.setAccessible(true);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.SLOWNESS,30,6));
        require((int)remaining.invoke(null,player)==0,"Unrelated Slowness falsely creates Nature vines");
        EntangleTracker.syncUltimateRoot(world,player,30);
        require((int)remaining.invoke(null,player)==30 && EntangleTracker.getStacks(player)==0,"Ultimate root lost its visual or creates gameplay stacks");
        EntangleTracker.clearStacks(world,player);player.removeStatusEffect(StatusEffects.SLOWNESS);
        require((int)remaining.invoke(null,player)==0,"Root-only state did not clear");
    }
    private void testTreeIsUntouchable(ServerWorld world) throws Exception {
        float health=tree.getHealth();
        for(int i=0;i<5;i++)EntangleTracker.addStack(world,tree);
        EntangleTracker.applyNatureSlow(tree,40,6);
        EntangleTracker.syncUltimateRoot(world,tree,30);
        require(!tree.damage(world,world.getDamageSources().playerAttack(player),10),"A player can damage the oak");
        require(EntangleTracker.getStacks(tree)==0,"Ultimate tree received Entangle stacks");
        require(!tree.hasStatusEffect(StatusEffects.SLOWNESS),"Ultimate tree received Nature Slowness");
        require(tree.getHealth()==health,"The ultimate tree took damage");
        var roots=EntangleTracker.class.getDeclaredField("ULTIMATE_ROOT_UNTIL");roots.setAccessible(true);
        require(!((Map<?,?>)roots.get(null)).containsKey(tree.getUuid()),"Ultimate tree received root visual tracking");
    }
}
