package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

/** Client-only presentation of the server's tracked drain; no gameplay decisions here. */
public final class NecromancerDrainEffects {
    private static final double TAU = Math.PI * 2;
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static ClientWorld world;
    private static final RenderLayer LAYER = RenderLayer.getEntityTranslucent(
            Identifier.of("elementalwands", "textures/misc/life_drain_white.png"));
    private record Cube(Vec3d position, double size, int color) {}
    private static final RenderStateDataKey<List<Cube>> CUBES = RenderStateDataKey.create(() -> "Necromancer soul streams");
    private static final class Channel {
        final NecromancerEntity boss;
        final LivingEntity target;
        final long start;
        double ended = -1;
        Vec3d hand, source;
        Channel(NecromancerEntity boss, LivingEntity target) {
            this.boss=boss;this.target=target;this.start=boss.getDrainStart();
        }
    }
    public record Veil(float time, float fade) {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NecromancerDrainEffects::tick);
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            List<Cube> cubes = new ArrayList<>();
            float delta = context.tickCounter().getTickProgress(false);
            Vec3d camera = context.camera().getPos();
            if (world != context.world()) { context.worldState().setData(CUBES, List.of()); return; }
            for (Channel channel : CHANNELS.values()) {
                float fade=fade(channel,delta);
                if (fade>=1 || !channel.boss.isAlive() || !channel.target.isAlive()) continue;
                if (channel.ended<0 || channel.hand==null) {
                    channel.source=channel.target.getLerpedPos(delta).add(0,channel.target.getHeight()*.68,0);
                    channel.hand=hand(channel.boss,delta);
                }
                Vec3d dst=channel.hand.add(0,.4,0);
                if (channel.source.distanceTo(camera)>64 && dst.distanceTo(camera)>64) continue;
                if (!context.frustum().isVisible(new Box(channel.source,dst).expand(1))) continue;
                boolean first=!context.camera().isThirdPerson() && channel.target==MinecraftClient.getInstance().getCameraEntity();
                float time=(float)((world.getTime()-channel.start+delta)/20.0);
                stream(cubes,channel.source.subtract(camera),dst.subtract(camera),channel.hand.subtract(camera),time,fade,first);
            }
            context.worldState().setData(CUBES,List.copyOf(cubes));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            List<Cube> cubes=context.worldState().getDataOrDefault(CUBES,List.of());
            if (cubes.isEmpty()) return;
            var out=context.consumers().getBuffer(LAYER);
            for (Cube cube:cubes) drawCube(out,context.matrices().peek(),cube);
        });
    }
    private static void tick(MinecraftClient client) {
        if (world!=client.world) { CHANNELS.clear();world=client.world; }
        if (world==null || client.player==null) { CHANNELS.clear();return; }
        for (var entity:world.getEntities()) {
            if (!(entity instanceof NecromancerEntity boss) || !boss.isAlive() || boss.getDrainTarget()<0 || boss.getDrainStart()<0) continue;
            if (!(world.getEntityById(boss.getDrainTarget()) instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) continue;
            Channel old=CHANNELS.get(boss.getUuid());
            if (old==null || old.start!=boss.getDrainStart() || old.target!=target)
                CHANNELS.put(boss.getUuid(),new Channel(boss,target));
        }
        CHANNELS.values().removeIf(c -> {
            if (c.boss.isRemoved() || !c.boss.isAlive() || c.target.isRemoved() || !c.target.isAlive() || c.target.isSpectator()) return true;
            if (c.boss.getDrainTarget()!=c.target.getId() || c.boss.getDrainStart()!=c.start) {
                if(c.ended<0)c.ended=world.getTime();
            }
            return c.ended>=0 && world.getTime()-c.ended>=15;
        });
    }
    private static float fade(Channel c,float delta) {
        return c.ended<0?0:Math.clamp((float)(world.getTime()+delta-c.ended)/15,0,1);
    }
    /** Only the actual local victim in first person receives a screen effect. */
    public static Veil localVeil(float delta) {
        var client=MinecraftClient.getInstance();
        if (world==null || client.world!=world || client.player==null || !client.player.isAlive()
                || client.player.isSpectator() || !client.options.getPerspective().isFirstPerson()
                || client.getCameraEntity()!=client.player) return null;
        Channel best=null;
        for(Channel c:CHANNELS.values()) if(c.target==client.player && c.boss.isAlive() && !c.boss.isRemoved()
                && (best==null || fade(c,delta)<fade(best,delta))) best=c;
        return best==null?null:new Veil((world.getTime()-best.start+delta)/20f,fade(best,delta));
    }
    private static Vec3d hand(NecromancerEntity boss,float delta) {
        Vec3d p=boss.isColossus()?new Vec3d(0,3.4,-2.4):NecromancerDrainSocket.local(boss.getDrainTime(delta));
        double yaw=Math.toRadians(MathHelper.lerpAngleDegrees(delta,boss.lastBodyYaw,boss.bodyYaw));
        return boss.getLerpedPos(delta).add(p.x*Math.cos(yaw)+p.z*Math.sin(yaw),p.y,p.x*Math.sin(yaw)-p.z*Math.cos(yaw));
    }
    private static Vec3d curve(Vec3d src,Vec3d dst,double u) {
        return src.lerp(dst,u).add(0,.30*Math.sin(Math.PI*u),.14*Math.sin(Math.PI*u));
    }
    private static double hash(int n) { double x=Math.sin(n*127.1+311.7)*43758.5453123;return x-Math.floor(x); }
    private static float visibility(double u,boolean first) { return first?(float)Math.clamp((u-.35)/.5,0,1):1; }
    private static void cube(List<Cube> cubes,Vec3d p,double size,int rgb,double alpha) {
        int a=(int)Math.clamp(alpha*255,0,255);
        if(a>2)cubes.add(new Cube(p,size,(a<<24)|rgb));
    }
    private static void ring(List<Cube> cubes,Vec3d p,Vec3d side,Vec3d up,double radius,double alpha) {
        for(int i=0;i<18;i++)if(i%7!=3) cube(cubes,p.add(side.multiply(Math.cos(i*TAU/18)*radius)).add(up.multiply(Math.sin(i*TAU/18)*radius)),.055,0x59d4d6,.7*alpha);
    }
    private static void stream(List<Cube> cubes,Vec3d src,Vec3d dst,Vec3d hand,float t,float breaking,boolean first) {
        double windup=Math.clamp(t/.7,0,1),amount=1-breaking;
        Vec3d axis=dst.subtract(src).normalize(),side=axis.crossProduct(new Vec3d(0,1,0));
        if(side.lengthSquared()<.001)side=new Vec3d(1,0,0);else side=side.normalize();
        Vec3d up=side.crossProduct(axis).normalize();
        if(!first)ring(cubes,src,side,up,.30,(t<.7?windup:.72)*amount);
        ring(cubes,dst,side,up,.17,(t<.7?windup:.7)*amount);
        cube(cubes,dst,.12,0xabfaff,.7*amount);
        for(int i=1;i<=5;i++)cube(cubes,dst.lerp(hand,i/6.0),.038,0x4fc2c9,.5*amount);
        if(t<.7) {
            for(int i=0;i<20;i++)cube(cubes,curve(src,dst,i/20.0),.035,0x1a6e75,.2*windup*amount*visibility(i/20.0,first));
            return;
        }
        int count=(int)(82*Math.clamp((t-.7)/.24,0,1));
        for(int i=0;i<=count;i++) {
            double u=i/82.0,visible=visibility(u,first)*amount;
            if(visible<.02 || (breaking>0 && hash(i*19)<breaking*.55))continue;
            Vec3d p=curve(src,dst,u).add((hash(i*17)-.5)*breaking*.32,(hash(i*23+3)-.5)*breaking*.32,(hash(i*31+7)-.5)*breaking*.32);
            cube(cubes,p,.10,0x09242b,.42*visible);
            for(int strand=0;strand<2;strand++) {
                double a=u*TAU*3.1-t*3.5+strand*Math.PI;
                Vec3d pos=p.add(side.multiply(Math.cos(a)*.14)).add(up.multiply(Math.sin(a)*.14));
                cube(cubes,pos,.105,strand==0?0x319da4:0x1b6971,.80*visible);
                if(i%4<2)cube(cubes,pos.add(0,.018,0),.055,strand==0?0x59d4d6:0x30a3ab,.65*visible);
                if(i%17==4)cube(cubes,pos.add(side.multiply(.045)),.045,0xabfaff,.7*visible);
            }
        }
        if(breaking==0) {
            for(int i=0;i<16;i++) {
                double u=((t-.7)*.43+i/16.0)%1;
                cube(cubes,curve(src,dst,u).add(side.multiply((hash(i*13)-.5)*.3)).add(up.multiply((hash(i*31)-.5)*.3)),.045,i%4==0?0xabfaff:0x55cdd0,.85*visibility(u,first));
            }
            double u=((t-.7)%.5)/.5;
            cube(cubes,curve(src,dst,u),.20,0x30a3ab,.55*visibility(u,first));
            cube(cubes,curve(src,dst,u),.13,0xabfaff,.9*visibility(u,first));
        } else for(int i=0;i<28;i++) {
            double u=hash(i*8+2);
            cube(cubes,curve(src,dst,u).add((hash(i*17)-.5)*breaking*.55,(hash(i*19+8)-.5)*breaking*.55,(hash(i*23)-.5)*breaking*.55),.05,0x59d4d6,.8*amount*visibility(u,first));
        }
    }
    private static final int[][] FACES={{0,1,3,2},{5,4,6,7},{4,0,2,6},{1,5,7,3},{2,3,7,6},{4,5,1,0}};
    private static final int[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
    private static void drawCube(VertexConsumer out,MatrixStack.Entry entry,Cube cube) {
        for(int f=0;f<6;f++) for(int i=0;i<4;i++) {
            int v=FACES[f][3-i];double r=cube.size/2;
            out.vertex(entry,(float)(cube.position.x+((v&1)==0?-r:r)),(float)(cube.position.y+((v&2)==0?-r:r)),(float)(cube.position.z+((v&4)==0?-r:r)))
                    .color(cube.color).texture(i==0||i==3?0:1,i<2?0:1).overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry,NORMALS[f][0],NORMALS[f][1],NORMALS[f][2]);
        }
    }
    private NecromancerDrainEffects() {}
}
