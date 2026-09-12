package com.anton.elementalwands.entity;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** Real damage acceptance, party scaling, stagger cancellation, and live meteors. Disposable world only. */
public final class GuardianGuardSmokeMod implements ModInitializer {
    private int tick,ground,opened=-1;
    private FracturedGuardianEntity boss;
    private final List<ServerPlayerEntity> players=new ArrayList<>();
    private final StringBuilder report=new StringBuilder();
    private float beforeMeteor;
    private net.minecraft.entity.mob.ZombieEntity fireTarget;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("GUARD_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private void run(MinecraftServer server) throws Exception {
        tick++;ServerWorld world=server.getOverworld();
        if(tick==25) {
            for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            for(int x=-40;x<=40;x++) for(int z=-40;z<=40;z++)
                world.setBlockState(new net.minecraft.util.math.BlockPos(x,ground-1,z),net.minecraft.block.Blocks.BEDROCK.getDefaultState(),2);
            addPlayer(server,0);
            var caster=players.getFirst();
            fireTarget=net.minecraft.entity.EntityType.ZOMBIE.create(world,net.minecraft.entity.SpawnReason.COMMAND);
            fireTarget.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ARMOR).setBaseValue(0);
            fireTarget.setAiDisabled(true);fireTarget.setPosition(-18.5,ground,4.5);
            fireTarget.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,new net.minecraft.item.ItemStack(net.minecraft.item.Items.CARVED_PUMPKIN));
            fireTarget.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE,200,0));
            var primary=new InfernoWaveEntity(world,caster);
            primary.onEntityHit(new net.minecraft.util.hit.EntityHitResult(fireTarget));
            // Remove armor so this fixture measures the spell itself.
            near(20-fireTarget.getHealth(),6,"Fire primary damage");
            fireTarget.setHealth(20);fireTarget.timeUntilRegen=0;fireTarget.setFireTicks(0);world.spawnEntity(fireTarget);
            caster.setPosition(-18.5,ground,.5);caster.setYaw(0);caster.setPitch(0);
            com.anton.elementalwands.item.FireAbilityHandler.castSecondary(world,caster,new net.minecraft.item.ItemStack(com.anton.elementalwands.registry.ModItems.FRACTURED_WAND));
            caster.setPosition(.5,ground,18.5);
            near(com.anton.elementalwands.util.MeteorManager.impactDamage(0,1),60,"Meteor center cap");
            near(com.anton.elementalwands.util.MeteorManager.impactDamage(6.5,1),30,"Meteor distance falloff");
            near(com.anton.elementalwands.util.MeteorManager.impactDamage(0,.5f),30,"Meteor partial cover");
            near(com.anton.elementalwands.util.MeteorManager.impactDamage(0,0),0,"Meteor full cover");
            near(com.anton.elementalwands.util.MeteorManager.impactDamage(10,1),0,"Meteor outer edge");
            boss=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            boss.setPosition(.5,ground,.5);boss.startFight();world.spawnEntity(boss);
        }
        if(boss==null)return;
        if(tick==35) {
            near(20-fireTarget.getHealth(),6,"Pyre damage");fireTarget.discard();
            report.append("Real Fire primary and traveling Pyre both deal six raw damage; meteor cap, distance falloff and exposure math pass.\n");
        }
        if(tick==125) {
            near(boss.getMaxHealth(),600,"Solo health");
            float health=boss.getHealth();hit(world,10);near(health-boss.getHealth(),4,"Guarded main damage");near(boss.getGuard(),110,"Guard loss");
            float guard=boss.getGuard();health=boss.getHealth();
            require(!boss.damage(world,world.getDamageSources().playerAttack(players.getFirst()),10),"Duplicate hurt-frame hit accepted");
            near(boss.getGuard(),guard,"Rejected hit damaged guard");near(boss.getHealth(),health,"Rejected hit damaged health");
            for(int i=1;i<5;i++)addPlayer(server,i);
        }
        if(tick==128) {
            near(boss.getMaxHealth(),2400,"Five-player health bypasses vanilla 1024 cap");
            near(boss.getHealth(),2396,"Joining healed prior main damage");
            near(boss.getMaxGuard(),480,"Five-player guard");near(boss.getGuard(),440,"Joining repaired cracks");
            players.getLast().changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
        }
        if(tick==131) {
            near(boss.getMaxHealth(),2400,"Elimination shrank health");near(boss.getMaxGuard(),480,"Elimination shrank guard");
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);
            var combat=(GuardianBossCombat)field.get(boss);
            var interrupt=GuardianBossCombat.class.getDeclaredMethod("interruptAction");interrupt.setAccessible(true);interrupt.invoke(combat);
            combat.testAttack(players.getFirst(),GuardianCombatRules.Attack.BEAM);
            for(int i=0;i<5;i++)hit(world,211); // All accepted: pessimistic burst, independent of hurt cooldown.
            require(boss.isAlive(),"Five-player initial volley killed boss");
            near(boss.getGuard(),0,"Burst did not break guard");
            report.append("Five-player fully accepted burst leaves ").append(boss.getHealth()).append(" / 2400 HP.\n");
        }
        if(tick==133) {
            require(boss.isGuardOpening(),"Guard did not stagger grounded boss");
            require(boss.getBeamTime(0)<0 && !boss.isHoldingRock(),"Guard break left active attack");
            opened=tick-(int)boss.getGuardTime(0);
        }
        if(opened>=0 && tick==opened+30) {
            float health=boss.getHealth();hit(world,10);near(health-boss.getHealth(),15,"Exposed damage");near(boss.getGuard(),0,"Exposure refilled guard");
            // The player's actual inventory charge is irrelevant to the boss damage path:
            // this launches the production falling meteor, impact and explosion.
            beforeMeteor=boss.getHealth();
            com.anton.elementalwands.util.MeteorManager.spawnMeteor(world,players.getFirst(),boss.getEntityPos(),35,5);
        }
        if(opened>=0 && tick==opened+70) {
            var output=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager());
            boss.writeCustomData(output);
            var copy=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            copy.readCustomData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager(),output.getNbt()));
            near(copy.getHealth(),boss.getHealth(),"Reload lost health");near(copy.getMaxHealth(),2400,"Reload lost party maximum");
            near(copy.getGuard(),0,"Reload repaired broken guard");near(copy.getGuardTime(0),boss.getGuardTime(0),"Reload lost exposure time");
            report.append("Save/load preserves main health, five-player maximum and remaining opening.\n");
        }
        if(opened>=0 && tick==opened+90) {
            float loss=beforeMeteor-boss.getHealth();require(loss>=60 && loss<=80,"Nerfed production meteor outside exposed damage target: "+loss);
            report.append("Actual falling meteor into exposed boss: ").append(loss).append(" HP.\n");
        }
        if(opened>=0 && tick==opened+180) {
            require(GuardianGuardRules.openness(boss.getGuardTime(0))<1,"Closing did not reduce exposure");
            float health=boss.getHealth();hit(world,10);float loss=health-boss.getHealth();require(loss>4 && loss<15,"Closing damage did not match visual openness");
        }
        if(opened>=0 && tick==opened+196) {
            require(!boss.isGuardOpening(),"Guard cycle never ended");near(boss.getGuard(),480,"Guard failed to regenerate");require(!boss.isAiDisabled(),"Guard left AI disabled");
            report.append("Guarded/exposed/reforming damage, guard reset and attack cancellation passed.\n");
            boss.stopReview();boss.setPosition(.5,ground,.5);boss.setNoGravity(true);boss.setAiDisabled(true);
            players.getLast().changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            beforeMeteor=boss.getHealth();
            for(var player:players)com.anton.elementalwands.util.MeteorManager.spawnMeteor(world,player,boss.getEntityPos(),35,5);
        }
        if(opened>=0 && tick==opened+265) {
            require(boss.isAlive(),"Five simultaneous meteors killed boss");
            float groupLoss=beforeMeteor-boss.getHealth();
            require(groupLoss>=90 && groupLoss<=100,"Five capped teammate meteors outside guarded target: "+groupLoss);
            report.append("Five simultaneous actual meteors: ").append(beforeMeteor-boss.getHealth()).append(" HP; boss survives.\n");
            boss.setNoGravity(false);boss.setPosition(.5,ground,.5);boss.setVelocity(Vec3d.ZERO);boss.startFight();
            for(int i=0;i<players.size();i++){players.get(i).setPosition(.5+i*3,ground,18.5);players.get(i).setVelocity(Vec3d.ZERO);}
        }
        if(opened>=0 && tick==opened+355) {
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);
            var combat=(GuardianBossCombat)field.get(boss);
            var interrupt=GuardianBossCombat.class.getDeclaredMethod("interruptAction");interrupt.setAccessible(true);interrupt.invoke(combat);
            boss.setPosition(.5,ground,.5);boss.setVelocity(Vec3d.ZERO);
            combat.testAttack(players.getFirst(),GuardianCombatRules.Attack.LEAP);
        }
        if(opened>=0 && tick==opened+375) {
            require(boss.getY()>ground+1,"Leap fixture did not take off: "+boss.leapStatus());
            for(int i=0;i<5;i++)hit(world,211);
            near(boss.getGuard(),0,"Airborne guard did not break");
        }
        if(opened>=0 && tick==opened+378) require(!boss.isGuardOpening(),"Guard froze an airborne leap");
        if(opened>=0 && tick==opened+455) {
            require(boss.isGuardOpening(),"Pending guard break did not open after landing: "+boss.leapStatus());
            require(!boss.hasNoGravity() && boss.getY()<ground+.1,"Guard left leap gravity/position unsafe");
            report.append("Airborne guard break waits for safe leap completion, then opens.\n");
            var repeated=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            repeated.damage(world,world.getDamageSources().playerAttack(players.getFirst()),40);
            repeated.damage(world,world.getDamageSources().playerAttack(players.getFirst()),211);
            near(600-repeated.getHealth(),GuardianGuardRules.impact(211)*.4f,"Increasing hits bypassed burst softening");
            repeated.damage(world,world.getDamageSources().playerAttack(players.get(1)),40);
            near(600-repeated.getHealth(),GuardianGuardRules.impact(211)*.4f+16,"Different attacker lost damage to hurt frames");
            report.append("Increasing same-attacker hits preserve the burst curve; a teammate still contributes.\n");
            // Verify successful damage remains possible after all mitigations, and /kill is never softened.
            boss.damage(world,world.getDamageSources().genericKill(),Float.MAX_VALUE);
            require(!boss.isAlive(),"Administrative kill was softened");
            Files.writeString(Path.of("GUARD_PASSED.txt"),report);System.out.println("GUARDIAN GUARD PASSED\n"+report);server.stop(false);
        }
        if(tick>800)throw new AssertionError("Guard test timed out");
    }
    private void addPlayer(MinecraftServer server,int index) throws Exception {
        var method=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);method.setAccessible(true);
        var player=(ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),"GuardTest"+index, .5+index*3,(double)ground,18.5);
        player.onTeleportationDone();player.setInvulnerable(true);players.add(player);
    }
    private void hit(ServerWorld world,float raw) {
        boss.timeUntilRegen=0;boss.clearGuardHurtWindows();
        require(boss.damage(world,world.getDamageSources().playerAttack(players.getFirst()),raw),"Controlled hit was rejected");
    }
    private static void near(float value,float expected,String reason){require(Math.abs(value-expected)<.02,reason+": "+value+" != "+expected);}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
