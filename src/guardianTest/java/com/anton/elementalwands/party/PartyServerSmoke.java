package com.anton.elementalwands.party;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.explosion.*;

/** Four real server players: caster, party ally, opponent, vanilla teammate. Disposable world only. */
public final class PartyServerSmoke implements ModInitializer {
    private static final UUID A=UUID.fromString("a1111111-1111-4111-8111-111111111111"), B=UUID.fromString("b1111111-1111-4111-8111-111111111111"),
            C=UUID.fromString("c1111111-1111-4111-8111-111111111111"), D=UUID.fromString("d1111111-1111-4111-8111-111111111111");
    private int ticks; private ServerPlayerEntity a,b,c,d; private MinecraftServer server; private ServerWorld world;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch(Throwable e) {
                e.printStackTrace();try { Files.writeString(Path.of("PARTY_FAILED.txt"),e.toString()); } catch(Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        int tick=++ticks;this.server=server;world=server.getOverworld();
        if (tick==20) {
            a=player(A,"PartyCaster",0,100,-4); b=player(B,"PartyAlly",-1,100,0);
            c=player(C,"PartyEnemy",0,100,0); d=player(D,"VanillaAlly",1,100,0);
        }
        if (tick==30) {
            var parties=PartyManager.get(server);
            if(Files.exists(Path.of("PARTY_RESTART_READY.txt"))) {
                require(parties.allied(A,B),"Party not restored after JVM restart");
                require(parties.party(A).leader().equals(A),"Leader not restored after JVM restart");
                require(command(c,"party accept PartyCaster")==0,"Invitation survived restart");
                require(WandAllies.protectedFrom(a,b) && WandAllies.protectedFrom(a,d),"Party/vanilla protection lost after restart");
                command(b,"party leave");require(!parties.allied(A,B),"Leave did not remove membership");
                command(a,"party disband");require(parties.party(A)==null,"Disband failed");
                Files.writeString(Path.of("PARTY_PASSED.txt"),"Real server passed: non-operator party commands, consent/leadership, invitation expiry, reconnect and JVM restart, vanilla teams, PvP-disabled safety, pets/tree, all projectile contacts, Nature zones/root, Pyre coals/fire trail, Fire cone, Stone domain, Meteor/Zephyr damage and knockback, spell fire ownership.\n");
                server.stop(false);return;
            }
            require(command(a,"party create")==1,"Non-op create failed");
            require(command(c,"party accept PartyCaster")==0,"Uninvited join accepted");
            require(command(a,"party invite PartyAlly")==1,"Invite failed");
            require(command(b,"party accept")==1,"Accept failed");
            require(command(b,"party invite PartyEnemy")==0,"Nonleader invited");
            require(command(b,"party kick PartyCaster")==0,"Nonleader kicked");
            require(command(b,"party disband")==0,"Nonleader disbanded");
            require(command(a,"ew party list")==1,"Namespaced alias failed");
            var team=server.getScoreboard().addTeam("party_smoke_vanilla"); team.setFriendlyFireAllowed(true);
            server.getScoreboard().addScoreHolderToTeam(a.getName().getString(),team);
            server.getScoreboard().addScoreHolderToTeam(d.getName().getString(),team);
            require(WandAllies.protectedFrom(a,b) && WandAllies.protectedFrom(a,d) && !WandAllies.protectedFrom(a,c),"Alliance selection wrong");
            require(server.getScoreboard().getScoreHolderTeam(b.getName().getString())==null,"Party overwrote vanilla teams");
            var pet=EntityType.WOLF.create(world,SpawnReason.COMMAND);pet.setOwner(b);pet.setTamed(true,false);
            require(WandAllies.protectedFrom(a,pet),"Ally pet not protected");
            var tree=new AwakenedTreeEntity(ModEntities.AWAKENED_TREE,world);tree.initialize(B);
            require(WandAllies.protectedFrom(a,tree),"Ally tree not protected");
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)world.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(ServerPlayerEntity target:List.of(b,c,d))NatureCombat.thornContact(world,target,A);
            require(b.getHealth()==200 && d.getHealth()==200 && EntangleTracker.getStacks(b)==0 && EntangleTracker.getStacks(d)==0,"Allied thorns/entangle");
            require(c.getHealth()<200 && EntangleTracker.getStacks(c)>0,"Enemy thorns suppressed");
            server.getGameRules().get(net.minecraft.world.GameRules.PVP).set(false,server);
            require(WandAllies.protectedFrom(a,c),"PvP-disabled server allowed harmful effects");
            server.getGameRules().get(net.minecraft.world.GameRules.PVP).set(true,server);
            projectiles();explosions();ground();natureUltimate();fireCone();domain();
            // Validate UUID resolution even when the caster is no longer in this world/player map.
            var mapField=net.minecraft.server.PlayerManager.class.getDeclaredField("playerMap");mapField.setAccessible(true);
            @SuppressWarnings("unchecked") var map=(Map<UUID,ServerPlayerEntity>)mapField.get(server.getPlayerManager());
            map.remove(A);server.getPlayerManager().getPlayerList().remove(a);
            require(WandAllies.protectedFrom(world,A,b) && WandAllies.protectedFrom(world,A,d),"Offline caster lost protections");
            map.put(A,a);server.getPlayerManager().getPlayerList().add(a);
            // Start real ticking Nature flowers with all three recipients in the growth.
            reset();a.setPosition(0,100,-5);b.setPosition(-1,100,0);c.setPosition(0,100,0);d.setPosition(1,100,0);
            require(SeedlingManager.tryPlantSeedling(world,a,new BlockHitResult(new Vec3d(.5,100,.5),Direction.UP,new BlockPos(0,99,0),false)),"Could not plant fixture seedling");
        }
        if(tick==70) {
            require(b.getHealth()==200 && d.getHealth()==200 && !b.hasStatusEffect(StatusEffects.SLOWNESS) && !d.hasStatusEffect(StatusEffects.SLOWNESS),"Allied Nature zone damage/slow");
            require(c.getHealth()<200 && c.hasStatusEffect(StatusEffects.SLOWNESS),"Enemy Nature zone failed");
            SeedlingManager.consumeAllSeedlingsForCaster(world,A);
            command(a,"party transfer PartyAlly");require(PartyManager.get(server).party(A).leader().equals(B),"Transfer failed");
            require(command(a,"party kick PartyAlly")==0,"Old leader retained permissions");
            command(b,"party transfer PartyCaster");
            b=server.getPlayerManager().respawnPlayer(b,false,Entity.RemovalReason.KILLED);
            b.setLoaded(true);b.onTeleportationDone();b.setNoGravity(true);b.getHungerManager().setFoodLevel(10);
            require(PartyManager.get(server).allied(A,b.getUuid()),"Respawn lost party");
            command(a,"party invite PartyEnemy");
        }
        if(tick==1280) {
            require(command(c,"party accept PartyCaster")==0,"Expired invitation accepted");
            command(a,"party invite PartyEnemy");command(c,"party decline");
            require(command(c,"party accept")==0,"Declined invitation accepted");
            command(b,"party leave");
            require(!WandAllies.protectedFrom(a,b),"Leaving retained protection");
        }
        if(tick==1350) {command(a,"party invite PartyAlly");command(b,"party accept");}
        if(tick==1420) {
            command(a,"party invite PartyEnemy"); // Must be discarded across restart.
            require(new PartyStore(server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/parties.json")).allied(A,B),"Committed membership missing");
            Files.writeString(Path.of("PARTY_RESTART_READY.txt"),"Initial server checks passed; restart required.\n");
            server.stop(false);
        }
    }
    private void projectiles() throws Exception {
        reset();
        for(ProjectileEntity projectile:List.of(new SeedProjectileEntity(world,a),new InfernoWaveEntity(world,a),
                new VacuumBladeEntity(world,a,new Vec3d(0,0,1),false,new HashSet<>()),new SingularityBoltEntity(world,a))) {
            Method can=projectile.getClass().getDeclaredMethod("canHit",Entity.class);can.setAccessible(true);
            require(!(boolean)can.invoke(projectile,b) && !(boolean)can.invoke(projectile,d),projectile.getType()+" collided with ally");
            Method hit=projectile.getClass().getDeclaredMethod("onEntityHit",EntityHitResult.class);hit.setAccessible(true);
            reset();hit.invoke(projectile,new EntityHitResult(b));require(b.getHealth()==200 && !b.isOnFire(),"Direct allied projectile effects");
            hit.invoke(projectile,new EntityHitResult(c));require(c.getHealth()<200,"Enemy projectile did no damage: "+projectile.getType());
            require(b.getHealth()==200 && d.getHealth()==200,"Projectile splash hit allies");
        }
        reset();var orb=new HollowPurpleOrbEntity(world,a,new Vec3d(0,100,0),new Vec3d(0,0,1));
        invoke(HollowPurpleOrbEntity.class,"damageTouchedTargets",new Class<?>[]{ServerWorld.class,Vec3d.class},orb,world,new Vec3d(0,100,0));
        require(b.getHealth()==200 && d.getHealth()==200 && c.getHealth()<200,"Purple allied touch filtering: " + b.getHealth()+"/"+c.getHealth()+"/"+d.getHealth());
        require(!StoneClusterEntity.eligible(a,b) && !StoneClusterEntity.eligible(a,d) && StoneClusterEntity.eligible(a,c),"Stone targeting wrong");
    }
    private void explosions() throws Exception {
        reset();Vec3d momentum=new Vec3d(.12,.07,.16);b.setVelocity(momentum);d.setVelocity(momentum);
        var damageField=MeteorManager.class.getDeclaredField("DAMAGE");damageField.setAccessible(true);
        var meteor=new ExplosionImpl(world,a,Explosion.createDamageSource(world,a),new WandExplosionBehavior(world,A,(ExplosionBehavior)damageField.get(null)),new Vec3d(0,101,0),5,false,Explosion.DestructionType.KEEP);
        meteor.explode();
        require(b.getHealth()==200 && d.getHealth()==200 && b.getVelocity().equals(momentum) && d.getVelocity().equals(momentum),"Meteor harmed/pushed allies");
        require(c.getHealth()<200,"Meteor suppressed opponent");
        reset();a.setPosition(0,100,0);b.setVelocity(momentum);d.setVelocity(momentum);
        invoke(ZephyrStrikeManager.class,"createWindImpact",new Class<?>[]{ServerWorld.class,ServerPlayerEntity.class,float.class},null,world,a,4f);
        require(b.getHealth()==200 && d.getHealth()==200 && b.getVelocity().equals(momentum) && d.getVelocity().equals(momentum) && c.getHealth()<200,"Zephyr protection or enemy damage failed");
        // Exercise the injected vanilla fire-creation call, with only disposable fixture blocks.
        List<BlockPos> positions=new ArrayList<>();for(int x=30;x<50;x++){
            var pos=new BlockPos(x,100,0);world.setBlockState(pos.down(),Blocks.STONE.getDefaultState());positions.add(pos);
        }
        invoke(ExplosionImpl.class,"createFire",new Class<?>[]{List.class},meteor,positions);
        int fires=0;for(BlockPos pos:positions)if(world.getBlockState(pos).isOf(ModSpellBlocks.INFERNO_FLAME)) {
            fires++;require(A.equals(TemporaryBlockManager.casterAt(world,pos)),"Meteor fire lost ownership");
        }
        require(fires>0,"Meteor fire hook did not place owned flames");
        a.setPosition(0,100,-4);
    }
    private void ground() throws Exception {
        reset();BlockPos fire=new BlockPos(20,100,0),coals=new BlockPos(22,99,0);
        TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(fire),ModSpellBlocks.INFERNO_FLAME.getDefaultState(),40,state->true,A);
        TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(coals),ModSpellBlocks.PYRE_COALS.getDefaultState(),40,state->true,A);
        class Handler implements EntityCollisionHandler {
            int events;List<java.util.function.Consumer<Entity>> callbacks=new ArrayList<>();
            public void addEvent(CollisionEvent event){events++;}
            public void addPreCallback(CollisionEvent event,java.util.function.Consumer<Entity> callback){callbacks.add(callback);}
            public void addPostCallback(CollisionEvent event,java.util.function.Consumer<Entity> callback){callbacks.add(callback);}
        }
        Method contact=com.anton.elementalwands.block.InfernoFlameBlock.class.getDeclaredMethod("onEntityCollision",BlockState.class,net.minecraft.world.World.class,BlockPos.class,Entity.class,EntityCollisionHandler.class,boolean.class);contact.setAccessible(true);
        Handler allied=new Handler();contact.invoke(ModSpellBlocks.INFERNO_FLAME,world.getBlockState(fire),world,fire,b,allied,true);
        require(allied.events==0 && allied.callbacks.isEmpty(),"Allied ground fire ignites");
        Handler enemy=new Handler();contact.invoke(ModSpellBlocks.INFERNO_FLAME,world.getBlockState(fire),world,fire,c,enemy,true);
        require(enemy.events>0 && !enemy.callbacks.isEmpty(),"Enemy fire inert");for(var callback:enemy.callbacks)callback.accept(c);
        require(c.getHealth()<200,"Enemy ground fire harmless");
        reset();ModSpellBlocks.PYRE_COALS.onSteppedOn(world,coals,world.getBlockState(coals),b);
        ModSpellBlocks.PYRE_COALS.onSteppedOn(world,coals,world.getBlockState(coals),c);
        require(b.getHealth()==200 && c.getHealth()<200,"Pyre coals protection failed");
    }
    private void natureUltimate() throws Exception {
        reset();invoke(OvergrowthManager.class,"applyRootCrush",new Class<?>[]{ServerWorld.class,PlayerEntity.class,BlockPos.class,float.class},null,world,a,new BlockPos(0,100,0),12f);
        require(b.getHealth()==200 && d.getHealth()==200 && !b.hasStatusEffect(StatusEffects.SLOWNESS) && b.getVelocity().equals(Vec3d.ZERO),"Allied root crush");
        require(c.getHealth()<200 && c.hasStatusEffect(StatusEffects.SLOWNESS),"Enemy root crush missing");
    }
    private void fireCone() throws Exception {
        reset();a.setYaw(0);a.setPitch(0);
        invoke(FireBuildManager.class,"flameDamage",new Class<?>[]{ServerPlayerEntity.class,float.class},null,a,3f);
        require(b.getHealth()==200 && d.getHealth()==200 && c.getHealth()<200,"Flamethrower party filtering");
    }
    private void domain() throws Exception {
        reset();TitanDomeManager.startDome(world,a);
        var field=TitanDomeManager.class.getDeclaredField("DOMES");field.setAccessible(true);
        Object dome=((Map<?,? extends List<?>>)field.get(null)).get(world.getRegistryKey()).getFirst();
        Method tick=TitanDomeManager.class.getDeclaredMethod("tickInescapableDomain",ServerWorld.class,dome.getClass(),int.class);tick.setAccessible(true);
        tick.invoke(null,world,dome,server.getTicks());
        b.setPosition(0,100,20);c.setPosition(0,100,20);d.setPosition(0,100,20);
        tick.invoke(null,world,dome,server.getTicks());
        require(b.getVelocity().equals(Vec3d.ZERO) && d.getVelocity().equals(Vec3d.ZERO) && c.getVelocity().length()>0,"Dome allied pull protection");
        TitanDomeManager.cancelForEncounter(a);
    }
    private void reset() {
        b.setPosition(-1,100,0);c.setPosition(0,100,0);d.setPosition(1,100,0);
        for(var player:List.of(a,b,c,d)) {player.setHealth(200);player.timeUntilRegen=0;player.setVelocity(Vec3d.ZERO);player.extinguish();player.clearStatusEffects();EntangleTracker.clearStacks(world,player);}
    }
    private ServerPlayerEntity player(UUID id,String name,double x,double y,double z)throws Exception {
        var factory=GuardianArenaSmokeModClass().getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);factory.setAccessible(true);
        var player=(ServerPlayerEntity)factory.invoke(null,server,id,name,x,y,z);
        player.setLoaded(true);player.onTeleportationDone();player.getHungerManager().setFoodLevel(10);
        player.setNoGravity(true);player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);player.setHealth(200);
        player.setStackInHand(Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModItems.FRACTURED_WAND));
        PartyManager.get(server).remember(player);return player;
    }
    private static Class<?> GuardianArenaSmokeModClass(){return com.anton.elementalwands.arena.GuardianArenaSmokeMod.class;}
    private int command(ServerPlayerEntity player,String command)throws Exception {return server.getCommandManager().getDispatcher().execute(command,player.getCommandSource());}
    private static Object invoke(Class<?> type,String name,Class<?>[] params,Object receiver,Object...args)throws Exception {
        var method=type.getDeclaredMethod(name,params);method.setAccessible(true);return method.invoke(receiver,args);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
