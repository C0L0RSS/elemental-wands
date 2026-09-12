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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Actual arena/spell writes and attacks; isolated from player saves by the optional init script. */
public final class GuardianNatureSmokeMod implements ModInitializer {
    private int tick, phase, started, floor;
    private long attackStarted;
    private ServerPlayerEntity player;
    private FracturedGuardianEntity guardian;
    private GuardianBossCombat combat;
    private ItemStack wand;
    private BlockPos seed, remote, distant, coals;
    private AwakenedTreeEntity tree;
    private float treeHealth;
    private boolean sawClearing;
    private static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch(Throwable e) { e.printStackTrace(); try { Files.writeString(Path.of("NATURE_FAILED.txt"),e.toString()); } catch(Exception ignored) {} server.stop(false); }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++; var world=server.getOverworld();
        require(tick<2000,"Nature fixture timed out: "+phase+" "+GuardianArenaManager.status());
        if(player!=null && player.isAlive() && phase!=8)player.setHealth(player.getMaxHealth());
        if(tick==30) {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"NatureTester",8.5,(double)ground,.5);
            player.onTeleportationDone(); player.setOnGround(true);
            guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            guardian.setPosition(.5,ground,.5);guardian.stopReview();world.spawnEntity(guardian);
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(guardian);
            require(GuardianArenaManager.start(player,guardian).startsWith("Arena sealed"),"Could not start arena"); phase=1;
        }
        if(phase==1 && GuardianArenaManager.isFighting(guardian)) {
            guardian.stopReview();floor=player.getBlockY()-1;
            player.setPosition(-20.5,floor+1,-20.5); player.setYaw(0);player.setPitch(0);
            wand=new ItemStack(ModItems.FRACTURED_WAND);player.equipStack(EquipmentSlot.MAINHAND,wand);
            testRootVisualOwnership(world);
            testWaterGrowth(world);
            FireAbilityHandler.castSecondary(world,player,wand);
            seed=new BlockPos(10,floor+1,10);remote=new BlockPos(20,floor+1,10);
            distant=new BlockPos(50,floor+1,10);
            plant(world,seed);plant(world,remote);plant(world,distant);
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
            require(world.getBlockState(coals).isOf(ModSpellBlocks.PYRE_COALS),"Real Fire secondary has no coals on arena floor");
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
            require(world.getBlockState(seed).isOf(ModSpellBlocks.NATURE_SEEDLING),"Seedling uprooted on artificial floor");
            require(world.getBlockState(seed.east()).isOf(ModSpellBlocks.NATURE_ROOTS),"Seedling failed to grow arena thorns");
            guardian.setPosition(10.5,floor+1,7.5);guardian.setOnGround(true);
            player.setPosition(10.5,floor+1,18.5);
            var source = SeedlingManager.getActiveSeedlingsForCaster(world,player.getUuid()).stream().filter(s -> s.anchorPos().equals(remote)).findFirst().orElseThrow();
            TendrilBloomManager.startTendril(world,player,source.seedlingId(),Vec3d.ofCenter(remote),guardian);
            guardian.testAttack(player,GuardianCombatRules.Attack.SHOCKWAVE);attackStarted=world.getTime();
            for(int i=0;i<5;i++)EntangleTracker.addStack(world,guardian);
            require(guardian.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier()==0,"Guardian got a full root");
            started=tick;phase=3;
        }
        if(phase==3 && tick-started==22) {
            var blooms=TendrilBloomManager.class.getDeclaredField("BLOOMS");blooms.setAccessible(true);
            require(!((Map<?,?>)blooms.get(null)).isEmpty(),"Nature secondary failed to produce a bloom against the tall boss");
        }
        if(phase==3 && tick-started==60) {
            require(!SeedlingManager.isSeedlingAlive(world,SeedlingManager.getActiveSeedlingsForCaster(world,player.getUuid()).stream().filter(s->s.anchorPos().equals(seed)).map(s->s.seedlingId()).findFirst().orElse(new UUID(0,0))),"Slam left its seedling active");
            require(world.getBlockState(seed).isAir()&&world.getBlockState(seed.east()).isAir(),"Slam did not clear seedling and owned thorns");
            require(world.getBlockState(remote).isAir(),"Traveling wave left the in-range plant intact");
            require(world.getBlockState(distant).isOf(ModSpellBlocks.NATURE_SEEDLING),"Wave erased plants outside its range");
            var next=GuardianBossCombat.class.getDeclaredField("nextAction");next.setAccessible(true);
            require(next.getLong(combat)==attackStarted+GuardianCombatRules.Attack.SHOCKWAVE.duration+GuardianCombatRules.RECOVERY_GAP+20,"Attack timing/recovery extension is wrong");
            guardian.stopReview();
            OvergrowthManager.startOvergrowth(world,player,seed,1);
            tree=world.getEntitiesByClass(AwakenedTreeEntity.class,new Box(seed).expand(10),e->e.isAlive()).getFirst();
            testTreeRejectsEntangle(world);
            treeHealth=tree.getHealth();started=tick;phase=4;
        }
        if(phase==4 && tick-started==50) {
            require(world.getBlockState(seed.up(2)).isOf(ModSpellBlocks.NATURE_HEARTWOOD),"Tree lost its custom heart");
            require(world.getBlockState(seed.west(4).up(4)).isOf(Blocks.OAK_LOG),"Tree lost the approved outstretched branch");
            require(world.getBlockState(seed.up(8)).isOf(Blocks.OAK_LEAVES),"Tree is not nine blocks tall");
            require(com.anton.elementalwands.util.NatureTreeLayout.cells().size()==169,"Approved tree layout changed");
            combat.crushGrowth(world,guardian.getEntityPos(),6,false,6);
            require(tree.isAlive() && tree.getHealth()<treeHealth,"Guardian impact cannot damage tree through its own shell, or instantly deletes it");
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
            for(int i=0;i<5;i++)EntangleTracker.addStack(world,guardian);
        }
        if(phase==5 && tick-started==110) {
            require(guardian.getZ()>0 && !guardian.hasNoGravity(),"Entangled leap did not finish: "+guardian.leapStatus());
            guardian.stopReview();guardian.setPosition(30.5,floor+1,30.5);guardian.setVelocity(Vec3d.ZERO);
            player.setPosition(30.5,floor+1,.5);
            plant(world,new BlockPos(30,floor+1,32));
            guardian.startFight();started=tick;phase=7;
        }
        if(phase==7) {
            var active=GuardianBossCombat.class.getDeclaredField("active");active.setAccessible(true);
            if(active.get(combat)==GuardianCombatRules.Attack.SHOCKWAVE) sawClearing=true;
            if(tick-started==180) {
                require(sawClearing,"Sustained real thorns did not make the Guardian clear while players were distant");
                require(world.getBlockState(new BlockPos(30,floor+1,32)).isAir(),"Automatic clearing left seedling intact");
                guardian.stopReview();GuardianArenaManager.stop();phase=6;
            }
        }
        if(phase==6 && !GuardianArenaManager.hasActiveArena()) {
            require(world.getBlockState(coals).isAir(),"Arena cleanup left spell floor behind");
            Files.writeString(Path.of("NATURE_PASSED.txt"),"Real arena: Fire coals/flames/buffs; protected tracked floor restoration; Nature planting/growth/survival; local slam destruction and distant preservation; exact extended recovery; damageable ultimate tree; uninterrupted rooted leap; arena cleanup.\n");
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
    private void testTreeRejectsEntangle(ServerWorld world) throws Exception {
        float health=tree.getHealth();
        for(int i=0;i<5;i++)EntangleTracker.addStack(world,tree);
        EntangleTracker.applyNatureSlow(tree,40,6);
        EntangleTracker.syncUltimateRoot(world,tree,30);
        var thorns=SeedlingManager.class.getDeclaredMethod("applyThorns",ServerWorld.class,net.minecraft.entity.LivingEntity.class,UUID.class);
        thorns.setAccessible(true);thorns.invoke(null,world,tree,player.getUuid());
        require(EntangleTracker.getStacks(tree)==0,"Ultimate tree received Entangle stacks");
        require(!tree.hasStatusEffect(StatusEffects.SLOWNESS),"Ultimate tree received Nature Slowness");
        require(tree.getHealth()==health,"Nature thorns damaged the ultimate tree");
        var roots=EntangleTracker.class.getDeclaredField("ULTIMATE_ROOT_UNTIL");roots.setAccessible(true);
        require(!((Map<?,?>)roots.get(null)).containsKey(tree.getUuid()),"Ultimate tree received root visual tracking");
    }
    private void testWaterGrowth(ServerWorld world) {
        BlockPos water=new BlockPos(200,-61,0),pad=water.up();world.getChunk(water);
        var oldWater=world.getBlockState(water);var oldPad=world.getBlockState(pad);
        world.setBlockState(water,Blocks.WATER.getDefaultState(),3);world.setBlockState(pad,Blocks.AIR.getDefaultState(),3);
        var growth=SeedlingManager.placeVerdantGrowth(world,List.of(water),java.util.Set.of(),20);
        require(growth.placed().contains(pad) && world.getBlockState(pad).isOf(ModSpellBlocks.NATURE_RAFT),"Water growth did not use custom raft");
        require(!world.getBlockState(pad).getCollisionShape(world,pad).isEmpty(),"Water raft is not walkable");
        SeedlingManager.restoreBlocks(world,growth.placements());
        require(world.getBlockState(pad).isAir() && world.getBlockState(water).isOf(Blocks.WATER),"Raft cleanup damaged water");
        Vec3d before=player.getEntityPos();player.setPosition(200.5,-60,.5);
        com.anton.elementalwands.item.NatureAbilityHandler.inventoryTick(wand,world,player,EquipmentSlot.MAINHAND);
        require(world.getBlockState(pad).isOf(ModSpellBlocks.NATURE_RAFT),"Verdant Step still uses vanilla pads");
        world.setBlockState(pad,Blocks.AIR.getDefaultState(),3);
        TemporaryBlockManager.forgetNaturePosition(world,pad);
        player.setPosition(before);world.setBlockState(water,oldWater,3);world.setBlockState(pad,oldPad,3);
    }
    private void plant(ServerWorld world,BlockPos pos) {
        require(SeedlingManager.tryPlantSeedling(world,player,new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0,.5,0),Direction.UP,pos.down(),false)),"Arena rejected Nature seedling");
    }
}
