package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.GaleDaggerEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** One prepared volley per player; only launch commits the recovery. */
public final class GaleDaggers {
    public static final String ID="gale_daggers";
    public static final int COOLDOWN=240, SPACING=3;
    public static final double RANGE=40, SPEED=3;
    public static final float DAMAGE=5;
    private static final Map<UUID,Volley> VOLLEYS=new HashMap<>();
    private static final class Volley {
        final ServerPlayerEntity player; final ItemStack wand;
        final List<GaleDaggerEntity> daggers=new ArrayList<>();
        int next; long launch=-1;
        Volley(ServerPlayerEntity p){player=p;wand=p.getMainHandStack();}
    }
    public static void init(){
        ServerTickEvents.END_WORLD_TICK.register(world->{
            for(var v:List.copyOf(VOLLEYS.values())){
                if(v.player.getEntityWorld()!=world)continue;
                if(!valid(v)){cancel(v.player);continue;}
                if(v.launch>=0 && v.player.getEntityWorld().getTime()>=v.launch+v.next*SPACING) launchNext(v);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->cancel(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var v:List.copyOf(VOLLEYS.values()))cancel(v.player);});
    }
    private static boolean allowed(ServerPlayerEntity p){
        return p.isAlive()&&!p.isSpectator()&&!p.isRemoved() && EWAttachments.getAffinity(p)==WizardAffinity.WIND
                && com.anton.elementalwands.arena.GuardianArenaManager.canCast(p)
                && p.getMainHandStack().getItem() instanceof AbstractWandItem
                && WandProgression.owns(p,WandSpells.find(ID)) && WandLoadouts.get(p).contains(ID);
    }
    private static boolean valid(Volley v){
        return allowed(v.player)&&v.player.getMainHandStack()==v.wand
                && v.daggers.stream().skip(v.next).allMatch(d->!d.isRemoved()&&d.getEntityWorld()==v.player.getEntityWorld());
    }
    public static boolean active(ServerPlayerEntity p){
        var v=VOLLEYS.get(p.getUuid());if(v!=null&&!valid(v)){cancel(p);return false;}return v!=null;
    }
    public static boolean ownsPrepared(ServerPlayerEntity p,GaleDaggerEntity dagger){
        var v=VOLLEYS.get(p.getUuid());return v!=null&&valid(v)&&v.daggers.contains(dagger);
    }
    public static long remaining(net.minecraft.entity.player.PlayerEntity p){
        long elapsed=p.getEntityWorld().getTime()-p.getAttachedOrElse(EWAttachments.GALE_LAST_LAUNCH,-1_000_000_000L);
        if(EntangleTracker.getStacks(p)>0)elapsed/=2;
        return Math.max(0,COOLDOWN-elapsed);
    }
    public static boolean cast(ServerPlayerEntity p){
        if(!allowed(p))return false;
        if(active(p))return fire(p);
        long remaining=remaining(p);
        if(remaining>0){AbstractWandItem.sendCooldownActionbar(p,"Gale Daggers",(int)remaining);return false;}
        var world=p.getEntityWorld();var stack=p.getMainHandStack();
        if(!AbstractWandItem.canStartCooldown(world,p,stack,ID,COOLDOWN)
                || !AbstractWandItem.tryStartCooldown(world,p,stack,ID,0))return false;
        var v=new Volley(p);VOLLEYS.put(p.getUuid(),v);
        for(int i=0;i<3;i++){
            var dagger=new GaleDaggerEntity(world,p,i);v.daggers.add(dagger);
            if(!world.spawnEntity(dagger)){cancel(p);return false;}
        }
        p.setAttached(EWAttachments.GALE_PREPARED,3);WandLoadouts.markCombat(p);
        p.sendMessage(Text.literal("Gale Daggers ready — press again or use primary fire."),true);
        return true;
    }
    public static boolean fire(ServerPlayerEntity p){
        if(!active(p))return false;
        var v=VOLLEYS.get(p.getUuid());if(v.launch>=0)return false;
        if(!AbstractWandItem.tryStartCooldown(p.getEntityWorld(),p,v.wand,ID,COOLDOWN))return false;
        v.launch=p.getEntityWorld().getTime();p.setAttached(EWAttachments.GALE_LAST_LAUNCH,v.launch);
        WandLoadouts.markCombat(p);launchNext(v);return true;
    }
    private static void launchNext(Volley v){
        v.daggers.get(v.next).launch(v.player);v.next++;
        v.player.setAttached(EWAttachments.GALE_PREPARED,3-v.next);
        if(v.next==3)VOLLEYS.remove(v.player.getUuid());
    }
    public static void cancel(ServerPlayerEntity p){
        var v=VOLLEYS.remove(p.getUuid());if(v!=null)for(int i=v.next;i<v.daggers.size();i++)v.daggers.get(i).discard();
        p.setAttached(EWAttachments.GALE_PREPARED,0);
    }
    public static Vec3d anchor(net.minecraft.entity.player.PlayerEntity p,int index){
        double side=(index-1)*.61, yaw=Math.toRadians(p.getYaw());
        return p.getEntityPos().add(Math.cos(yaw)*side,p.getHeight()+(index==1?.57:.24),Math.sin(yaw)*side);
    }
    private GaleDaggers(){}
}
