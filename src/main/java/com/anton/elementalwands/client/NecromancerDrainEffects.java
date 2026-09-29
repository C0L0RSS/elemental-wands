package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.SpellViewClearance;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

/** Client-only presentation of the server's tracked drain; no gameplay decisions here. */
public final class NecromancerDrainEffects {
    private static final double TAU = Math.PI * 2;
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static ClientWorld world;
    private static final Identifier WHITE = Identifier.of("elementalwands", "textures/misc/life_drain_white.png");
    /** Eyes-style body: ignores world light and face shading, so the braid reads in the dark crypt. */
    private static final RenderLayer BODY = RenderLayer.getEyes(WHITE);
    /** Additive soft halo (src-alpha, one) without depth writes; overlapping glow accumulates gently. */
    private static final RenderLayer GLOW = RenderLayer.getDragonRays();
    // Soul palette shared with the soul bolt wisps: a deep core, two strand tones and pale highlights.
    private static final int CORE = 0x0e5a64, STRAND = 0x3fd2dc, STRAND_DIM = 0x21a2ae, BRIGHT = 0x7ff0f5,
            MOTE = 0x6ee6ec, PALE = 0xc6fcff, HOT = 0xf0ffff, HALO = 0x2fc8d6;
    private static final int IMPACT = NecromancerRules.Action.DRAIN.impact, INTERVAL = NecromancerRules.DRAIN_INTERVAL;
    /** Seconds from the cast to the first pulse: the braid forms over this windup. */
    public static final double WINDUP = IMPACT / 20.0;
    private record Cube(Vec3d position, double size, int color, boolean glow) {}
    /** One frame's cubes; those near a first-person lens fade instead of clipping it. */
    private record Emit(List<Cube> cubes, boolean lens, boolean victim) {
        void add(Vec3d p, double size, int rgb, double alpha) { put(p, size, rgb, alpha, false); }
        void glow(Vec3d p, double size, int rgb, double alpha) { put(p, size, rgb, alpha, true); }
        private void put(Vec3d p, double size, int rgb, double alpha, boolean glow) {
            // Positions are camera-relative. The victim's braid starts at their chest, just under
            // the lens, so it gets extra clearance and appears from about 1.5 blocks out.
            alpha *= SpellViewClearance.opacity(lens, p.length(), victim ? Math.max(.45, size / 2) : size / 2);
            int a = (int) Math.clamp(alpha * 255, 0, 255);
            if (a > 2) cubes.add(new Cube(p, size, (a << 24) | rgb, glow));
        }
    }
    private static final RenderStateDataKey<List<Cube>> CUBES = RenderStateDataKey.create(() -> "Necromancer soul streams");
    private static final class Channel {
        final NecromancerEntity boss;
        final LivingEntity target;
        final long start;
        double ended = -1;
        long sparked = Long.MIN_VALUE;
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
            boolean lens = !context.camera().isThirdPerson();
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
                boolean victim=lens && channel.target==MinecraftClient.getInstance().getCameraEntity();
                float time=(float)((world.getTime()-channel.start+delta)/20.0);
                stream(new Emit(cubes,lens,victim),channel.source.subtract(camera),dst.subtract(camera),channel.hand.subtract(camera),time,fade);
            }
            context.worldState().setData(CUBES,List.copyOf(cubes));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            List<Cube> cubes=context.worldState().getDataOrDefault(CUBES,List.of());
            if (cubes.isEmpty()) return;
            var consumers=context.consumers();var entry=context.matrices().peek();
            // Full-bright body first, then the additive halo over it; each is drawn in this pass.
            var body=consumers.getBuffer(BODY);
            for (Cube cube:cubes) if(!cube.glow()) drawCube(body,entry,cube);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BODY);
            var glow=consumers.getBuffer(GLOW);
            for (Cube cube:cubes) if(cube.glow()) drawHalo(glow,entry,cube);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(GLOW);
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
        for (Channel c:CHANNELS.values()) particles(c);
    }
    /**
     * Full-bright soul wisps ride the braid from victim to focus, so the flow direction reads
     * even at a glance. Wisps near a first-person lens fade through the shared particle clearance.
     */
    private static void particles(Channel c) {
        long now=world.getTime(),ticks=now-c.start;
        if (c.ended>=0 || ticks<0 || now==c.sparked) return;
        c.sparked=now;
        Vec3d src=c.target.getEntityPos().add(0,c.target.getHeight()*.68,0),dst=hand(c.boss,1).add(0,.4,0);
        if (src.squaredDistanceTo(dst)<.01) return;
        var random=world.getRandom();
        if (ticks<IMPACT) {
            // Windup: motes gather into the focus above the hand.
            if (ticks%2==0) {
                Vec3d o=new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).normalize().multiply(.5);
                spark(ModParticles.NECROMANCER_SOUL_WISP,dst.add(o),o.multiply(-.09));
            }
            return;
        }
        for (int i=0;i<2;i++) {
            double u=random.nextDouble();
            Vec3d jitter=new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).multiply(.16);
            spark(ModParticles.NECROMANCER_SOUL_WISP,curve(src,dst,u).add(jitter),tangent(src,dst,u).multiply(.10+.05*random.nextDouble()));
        }
        if ((ticks-IMPACT)%INTERVAL!=0) return;
        // Each damage pulse tears motes from the victim...
        for (int i=0;i<4;i++) {
            Vec3d jitter=new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).multiply(.3);
            spark(ModParticles.NECROMANCER_SOUL_WISP,src.add(jitter),tangent(src,dst,0).multiply(.14+.06*random.nextDouble()));
        }
        if (ticks<IMPACT+INTERVAL) return;
        // ...while the previous pulse lands in the focus and a stolen soul rises from it.
        spark(ParticleTypes.SCULK_SOUL,dst,new Vec3d((random.nextDouble()-.5)*.02,.035,(random.nextDouble()-.5)*.02));
        for (int i=0;i<3;i++)
            spark(ModParticles.NECROMANCER_SOUL_WISP,dst,new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).multiply(.12));
    }
    private static void spark(ParticleEffect type,Vec3d p,Vec3d v) {
        world.addParticleClient(type,p.x,p.y,p.z,v.x,v.y,v.z);
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
    /** Unit direction of travel along {@link #curve}, from victim toward focus. */
    private static Vec3d tangent(Vec3d src,Vec3d dst,double u) {
        double bend=Math.PI*Math.cos(Math.PI*u);
        return dst.subtract(src).add(0,.30*bend,.14*bend).normalize();
    }
    private static double hash(int n) { double x=Math.sin(n*127.1+311.7)*43758.5453123;return x-Math.floor(x); }
    private static void ring(Emit out,Vec3d p,Vec3d side,Vec3d up,double radius,double alpha) {
        for(int i=0;i<18;i++)if(i%7!=3) out.add(p.add(side.multiply(Math.cos(i*TAU/18)*radius)).add(up.multiply(Math.sin(i*TAU/18)*radius)),.055,MOTE,.78*alpha);
    }
    private static void stream(Emit out,Vec3d src,Vec3d dst,Vec3d hand,float t,float breaking) {
        double windup=Math.clamp(t/WINDUP,0,1),amount=1-breaking,settle=t<WINDUP?windup:1;
        Vec3d axis=dst.subtract(src).normalize(),side=axis.crossProduct(new Vec3d(0,1,0));
        if(side.lengthSquared()<.001)side=new Vec3d(1,0,0);else side=side.normalize();
        Vec3d up=side.crossProduct(axis).normalize();
        // A bright bead leaves the victim on each 0.5 s damage pulse and lands in the focus as the
        // next one leaves; both ends flare at that moment (the focus only once a bead has arrived).
        double bead=t<WINDUP?0:((t-WINDUP)%.5)/.5;
        double flare=t<WINDUP||breaking>0?0:Math.exp(-Math.pow(Math.min(bead,1-bead)/.1,2)),landed=t<WINDUP+.4?0:flare;
        if(!out.victim()) {
            ring(out,src,side,up,.30,(t<WINDUP?windup:.72)*amount);
            out.glow(src,.5,HALO,(.10+.14*flare)*settle*amount);
        }
        ring(out,dst,side,up,.17,(t<WINDUP?windup:.72)*amount);
        out.add(dst,.12,PALE,.85*amount);
        out.glow(dst,.55,HALO,(.14+.10*landed)*settle*amount);
        out.glow(dst,.28,BRIGHT,(.14+.24*landed)*settle*amount);
        for(int i=1;i<=5;i++)out.add(dst.lerp(hand,i/6.0),.038,0x5fdde4,.6*amount);
        if(t<WINDUP) {
            for(int i=0;i<20;i++)out.add(curve(src,dst,i/20.0),.035,STRAND_DIM,.3*windup*amount);
            return;
        }
        int count=(int)(82*Math.clamp((t-WINDUP)/.24,0,1));
        // About one soft halo every third of a block, whatever the stream length.
        int haloStep=Math.max(1,(int)Math.round(.34*82/Math.max(src.distanceTo(dst),.1)));
        for(int i=0;i<=count;i++) {
            double u=i/82.0;
            if(breaking>0 && hash(i*19)<breaking*.55)continue;
            Vec3d p=curve(src,dst,u).add((hash(i*17)-.5)*breaking*.32,(hash(i*23+3)-.5)*breaking*.32,(hash(i*31+7)-.5)*breaking*.32);
            out.add(p,.10,CORE,.45*amount);
            if(i%haloStep==0)out.glow(p,.40,HALO,.10*amount);
            for(int strand=0;strand<2;strand++) {
                double a=u*TAU*3.1-t*3.5+strand*Math.PI;
                Vec3d pos=p.add(side.multiply(Math.cos(a)*.14)).add(up.multiply(Math.sin(a)*.14));
                out.add(pos,.105,strand==0?STRAND:STRAND_DIM,.85*amount);
                if(i%4<2)out.add(pos.add(0,.018,0),.055,strand==0?BRIGHT:0x48d6de,.7*amount);
                if(i%17==4)out.add(pos.add(side.multiply(.045)),.045,HOT,.85*amount);
            }
        }
        if(breaking==0) {
            for(int i=0;i<16;i++) {
                double u=((t-WINDUP)*.43+i/16.0)%1;
                out.add(curve(src,dst,u).add(side.multiply((hash(i*13)-.5)*.3)).add(up.multiply((hash(i*31)-.5)*.3)),.045,i%4==0?HOT:MOTE,.9);
            }
            Vec3d p=curve(src,dst,bead);
            out.add(p,.20,STRAND,.6);
            out.add(p,.13,HOT,.95);
            out.glow(p,.5,BRIGHT,.22);
        } else for(int i=0;i<28;i++) {
            double u=hash(i*8+2);
            out.add(curve(src,dst,u).add((hash(i*17)-.5)*breaking*.55,(hash(i*19+8)-.5)*breaking*.55,(hash(i*23)-.5)*breaking*.55),.05,MOTE,.85*amount);
        }
    }
    private static final int[][] FACES={{0,1,3,2},{5,4,6,7},{4,0,2,6},{1,5,7,3},{2,3,7,6},{4,5,1,0}};
    private static final int[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
    /** Two triangles per face, keeping each quad's outward winding. */
    private static final int[] TRIANGLES={0,1,2,0,2,3};
    private static VertexConsumer corner(VertexConsumer out,MatrixStack.Entry entry,Cube cube,int v) {
        double r=cube.size()/2;
        return out.vertex(entry,(float)(cube.position().x+((v&1)==0?-r:r)),(float)(cube.position().y+((v&2)==0?-r:r)),(float)(cube.position().z+((v&4)==0?-r:r)))
                .color(cube.color());
    }
    private static void drawCube(VertexConsumer out,MatrixStack.Entry entry,Cube cube) {
        for(int f=0;f<6;f++) for(int i=0;i<4;i++)
            corner(out,entry,cube,FACES[f][3-i]).texture(i==0||i==3?0:1,i<2?0:1).overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry,NORMALS[f][0],NORMALS[f][1],NORMALS[f][2]);
    }
    private static void drawHalo(VertexConsumer out,MatrixStack.Entry entry,Cube cube) {
        for(int[] face:FACES) for(int k:TRIANGLES) corner(out,entry,cube,face[3-k]);
    }
    private NecromancerDrainEffects() {}
}
