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

/**
 * Real damage acceptance, party scaling, back volleys, the half-health gate, the shell break's
 * immunity and burst, the core pulse blast and live meteors. Disposable world only.
 */
public final class GuardianShellSmokeMod implements ModInitializer {
    private static final int VOLLEY_DEADLINE = 420;
    private int tick,ground,breakStart=-1,pulseStart=-1,meteorsAt=-1;
    private boolean volleySeen;
    private FracturedGuardianEntity boss;
    private final List<ServerPlayerEntity> players=new ArrayList<>();
    private final StringBuilder report=new StringBuilder();
    private float beforeMeteor;
    private net.minecraft.entity.mob.ZombieEntity fireTarget;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("SHELL_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
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
            near(boss.getMaxHealth(),1400,"Solo health");
            float health=boss.getHealth();hit(world,10);near(health-boss.getHealth(),10,"Phase-one damage was softened");
            require(boss.getCracks()==0,"A fresh shell showed cracks");
            health=boss.getHealth();
            require(!boss.damage(world,world.getDamageSources().playerAttack(players.getFirst()),10),"Duplicate hurt-frame hit accepted");
            near(boss.getHealth(),health,"Rejected hit damaged health");
            for(int i=1;i<5;i++)addPlayer(server,i);
        }
        if(tick==128) {
            near(boss.getMaxHealth(),5400,"Five-player health bypasses vanilla 1024 cap");
            near(boss.getHealth(),5390,"Joining healed prior damage");
            players.getLast().changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
        }
        if(tick==131) near(boss.getMaxHealth(),5400,"Elimination shrank health");
        // A real four-player fight: some committed attack must send stones at the players it is not attacking.
        if(tick>131 && tick<VOLLEY_DEADLINE && !volleySeen) {
            volleySeen=!world.getEntitiesByClass(GuardianRockEntity.class,boss.getBoundingBox().expand(12),r->r.isShard() && r.isHeld()).isEmpty();
            if(volleySeen) report.append("Four-player fight raised a back volley at tick ").append(tick).append(".\n");
        }
        if(tick==VOLLEY_DEADLINE) {
            require(volleySeen,"No back volley in a four-player fight");
            interrupt();
            boss.setPosition(.5,ground,.5);boss.setVelocity(Vec3d.ZERO);
            players.getLast().changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            float gate=GuardianShellRules.gate(boss.getMaxHealth());
            boss.setHealth(gate+50);
            hit(world,211);
            near(boss.getHealth(),gate,"Burst skipped the half-health gate");
            require(boss.getCracks()==3,"Shell was not fully cracked at the gate");
            hit(world,40);
            near(boss.getHealth(),gate,"Phase one lost health below the gate");
            report.append("Phase one holds at half health (").append(gate).append(") however hard it is hit.\n");
        }
        if(tick>VOLLEY_DEADLINE && breakStart<0) {
            // The break starts once the boss stands on the ground with no attack running.
            require(tick<VOLLEY_DEADLINE+40,"Shell break did not begin at the gate");
            if(!boss.isBreaking())return;
            require(boss.isUnstable(),"Shell broke without entering phase two");
            breakStart=tick-(int)boss.getPhaseTime(0);
            float health=boss.getHealth();
            boss.timeUntilRegen=0;boss.clearHurtWindows();
            require(!boss.damage(world,world.getDamageSources().playerAttack(players.get(1)),50),"The break accepted damage");
            near(boss.getHealth(),health,"The break lost health");
            // One player close enough for the burst, one well clear; the rest stay protected and far away.
            vulnerable(players.get(0),new Vec3d(.5,ground,4.5));
            vulnerable(players.get(1),new Vec3d(.5,ground,12.5));
        }
        if(breakStart>=0 && tick==breakStart+GuardianPhaseRules.BURST+2) {
            near(players.get(0).getHealth(),20-GuardianPhaseRules.BURST_DAMAGE,"The burst missed a player beside it");
            near(players.get(1).getHealth(),20,"The burst reached a player who backed off");
            players.get(0).setInvulnerable(true);players.get(1).setInvulnerable(true);
            report.append("Shell break: immune throughout, burst hits within six blocks only.\n");
        }
        if(breakStart>=0 && tick==breakStart+44) {
            float open=GuardianShellRules.openness(boss.getPhaseTime(0),boss.isUnstable());
            require(open>0 && open<1,"Ribs not swinging open after the burst: "+open);
        }
        if(breakStart>=0 && tick==breakStart+GuardianPhaseRules.TRANSITION_TICKS+4) {
            require(!boss.isBreaking() && boss.isUnstable() && !boss.isAiDisabled(),"Break did not hand back to phase two");
            require(GuardianShellRules.openness(boss.getPhaseTime(0),true)==1,"Ribs closed after the break");
            var output=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager());
            boss.writeCustomData(output);
            var copy=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            copy.readCustomData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager(),output.getNbt()));
            near(copy.getHealth(),boss.getHealth(),"Reload lost health");near(copy.getMaxHealth(),5400,"Reload lost party maximum");
            require(copy.isUnstable() && copy.getCracks()==3,"Reload mended the broken shell");
            report.append("Save/load keeps health, the five-player maximum and the broken shell.\n");
            boss.stopReview();
            boss.setPosition(.5,ground,.5);boss.setVelocity(Vec3d.ZERO);
            vulnerable(players.get(0),new Vec3d(.5,ground,4.5));
            vulnerable(players.get(1),new Vec3d(.5,ground,9.5));
            boss.testAttack(players.get(0),GuardianCombatRules.Attack.PULSE);
            pulseStart=tick;
        }
        if(pulseStart>=0 && tick==pulseStart+GuardianPulseRules.PULL_START+5)
            require(boss.getPulseTime(0)>=0 && boss.isAiDisabled(),"Core pulse did not plant");
        if(pulseStart>=0 && tick==pulseStart+GuardianPulseRules.BLAST+2) {
            near(players.get(0).getHealth(),20-GuardianPulseRules.DAMAGE,"The blast missed a player at the core");
            near(players.get(1).getHealth(),20,"The blast reached past its edge");
            players.get(0).setInvulnerable(true);players.get(1).setInvulnerable(true);
            report.append("Core pulse blast hits inside six blocks only.\n");
        }
        if(pulseStart>=0 && tick==pulseStart+GuardianPulseRules.DURATION+3) {
            require(boss.getPulseTime(0)<0 && !boss.isAiDisabled(),"Core pulse did not finish");
            boss.stopReview();boss.setPosition(.5,ground,.5);boss.setNoGravity(true);boss.setAiDisabled(true);
            for(int i=0;i<players.size();i++){players.get(i).setPosition(.5+i*3,ground,18.5);players.get(i).setVelocity(Vec3d.ZERO);}
            beforeMeteor=boss.getHealth();
            // The player's actual inventory charge is irrelevant to the boss damage path:
            // this launches the production falling meteor, impact and explosion.
            for(var player:players)com.anton.elementalwands.util.MeteorManager.spawnMeteor(world,player,boss.getEntityPos(),35,5);
            meteorsAt=tick;
        }
        if(meteorsAt>=0 && tick==meteorsAt+70) {
            require(boss.isAlive(),"Five simultaneous meteors killed boss");
            float groupLoss=beforeMeteor-boss.getHealth();
            require(groupLoss>=150 && groupLoss<=250,"Five capped teammate meteors outside target: "+groupLoss);
            report.append("Five simultaneous actual meteors: ").append(groupLoss).append(" HP of 5400; boss survives.\n");
            var repeated=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            repeated.damage(world,world.getDamageSources().playerAttack(players.getFirst()),40);
            repeated.damage(world,world.getDamageSources().playerAttack(players.getFirst()),211);
            near(1400-repeated.getHealth(),GuardianShellRules.impact(211),"Increasing hits bypassed burst softening");
            repeated.damage(world,world.getDamageSources().playerAttack(players.get(1)),40);
            near(1400-repeated.getHealth(),GuardianShellRules.impact(211)+40,"Different attacker lost damage to hurt frames");
            report.append("Increasing same-attacker hits preserve the burst curve; a teammate still contributes.\n");
            // /kill is never softened.
            boss.damage(world,world.getDamageSources().genericKill(),Float.MAX_VALUE);
            require(!boss.isAlive(),"Administrative kill was softened");
            Files.writeString(Path.of("SHELL_PASSED.txt"),report);System.out.println("GUARDIAN SHELL PASSED\n"+report);server.stop(false);
        }
        if(tick>1100)throw new AssertionError("Shell test timed out");
    }
    private void interrupt() throws Exception {
        var interrupt=GuardianBossCombat.class.getDeclaredMethod("interruptAction");interrupt.setAccessible(true);interrupt.invoke(boss.combat());
    }
    private void vulnerable(ServerPlayerEntity player,Vec3d position) {
        player.setPosition(position);player.setVelocity(Vec3d.ZERO);player.setInvulnerable(false);
        player.setHealth(20);player.timeUntilRegen=0;player.clearStatusEffects();
    }
    private void addPlayer(MinecraftServer server,int index) throws Exception {
        var method=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);method.setAccessible(true);
        var player=(ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),"ShellTest"+index, .5+index*3,(double)ground,18.5);
        player.onTeleportationDone();player.setInvulnerable(true);players.add(player);
    }
    private void hit(ServerWorld world,float raw) {
        boss.timeUntilRegen=0;boss.clearHurtWindows();
        require(boss.damage(world,world.getDamageSources().playerAttack(players.getFirst()),raw),"Controlled hit was rejected");
    }
    private static void near(float value,float expected,String reason){require(Math.abs(value-expected)<.02,reason+": "+value+" != "+expected);}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
