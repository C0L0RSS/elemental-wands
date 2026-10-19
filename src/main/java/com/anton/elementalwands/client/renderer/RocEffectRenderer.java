package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.SummitStormClient;
import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.entity.roc.RocEffectEntity;
import com.anton.elementalwands.entity.roc.StormRocRules;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Low-poly storm meshes. Telegraph geometry shares the authoritative effect radius and clock. */
public final class RocEffectRenderer extends EntityRenderer<RocEffectEntity, RocEffectRenderer.State> {
    private static final RenderLayer LIGHT = RenderLayer.getEntityTranslucentEmissive(
            Identifier.of("elementalwands", "textures/misc/roc_white.png"));
    private static final RenderLayer SMOKE = RenderLayer.getEntityTranslucent(
            Identifier.of("elementalwands", "textures/misc/roc_white.png"));
    private static final RenderLayer TWISTER = RenderLayer.getEntityTranslucent(
            Identifier.of("elementalwands", "textures/misc/roc_tornado.png"));
    /** First tile of each row in the tornado's 4x4 atlas: funnel wall, dusty foot, torn-up debris, wind bands. */
    private static final int WALL = 0, DUST = 4, DEBRIS = 8, BAND = 12;
    public static final class State extends EntityRenderState {
        RocEffectEntity.Kind kind;
        float tick, yaw, radius;
        int duration, warning;
        float[] ground = new float[0];
        int groundX, groundZ, groundSize;
    }
    public RocEffectRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius=0; }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(RocEffectEntity e, State s, float delta) {
        super.updateRenderState(e,s,delta);
        s.kind=e.kind(); s.tick=e.effectTicks(delta); s.duration=e.duration();
        s.radius=(float)e.radius(); s.warning=e.warningTicks(); s.yaw=e.getYaw();
        sampleGround(e,s);
    }
    /**
     * The rock under each effect, sampled once per world tick and shared by every frame in it.
     * Render states are rebuilt each frame, so the samples live here, keyed by entity.
     */
    private record Ground(long tick,double x,double z,int originX,int originZ,int size,float[] heights) {}
    private static final java.util.Map<Integer,Ground> GROUND=new java.util.HashMap<>();
    private static net.minecraft.world.World groundWorld;
    private static void sampleGround(RocEffectEntity e, State s) {
        if(s.kind==RocEffectEntity.Kind.SCREAM||s.kind==RocEffectEntity.Kind.DISTANT_BOLT||s.kind==RocEffectEntity.Kind.STORM
                ||s.kind==RocEffectEntity.Kind.STORM_BOLT||s.kind==RocEffectEntity.Kind.CHARGE_BOLT||s.kind==RocEffectEntity.Kind.CALL_BOLT) { s.groundSize=0;return; }
        // Samples belong to one world: a new world, or a runaway cache, starts afresh.
        if(e.getEntityWorld()!=groundWorld||GROUND.size()>512) { GROUND.clear(); groundWorld=e.getEntityWorld(); }
        long tick=e.getEntityWorld().getTime();
        Ground g=GROUND.get(e.getId());
        if(g==null||g.tick!=tick||g.x!=e.getX()||g.z!=e.getZ()) {
            g=sample(e,s,tick);
            if(GROUND.size()>48)GROUND.values().removeIf(old->old.tick<tick-40);
            GROUND.put(e.getId(),g);
        }
        s.ground=g.heights;s.groundX=g.originX;s.groundZ=g.originZ;s.groundSize=g.size;
    }
    private static Ground sample(RocEffectEntity e,State s,long tick) {
        boolean wave=s.kind==RocEffectEntity.Kind.SHOCKWAVE;
        double radius=wave?Math.min(s.radius,(Math.floor(s.tick)+1)*1.25):s.radius;
        int extent=(int)Math.ceil(Math.min(72,radius)+3);
        int originX=(int)Math.floor(e.getX())-extent,originZ=(int)Math.floor(e.getZ())-extent,size=extent*2+1;
        float[] heights=new float[size*size];
        BlockPos.Mutable pos=new BlockPos.Mutable();
        int floor=(int)Math.floor(e.getY());
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            double dx=originX+x+.5-e.getX(),dz=originZ+z+.5-e.getZ();
            double distance=Math.sqrt(dx*dx+dz*dz);
            if(distance>radius+2.5 || wave && distance<Math.max(0,radius-4))continue;
            double height=e.getY();
            // Snow's visual top is above its collision top by one layer.
            // Search only the summit surface band: distant cliffs/spires never
            // pull a huge decal up into the sky. At most five block reads/cell.
            for(int y=floor-1;y<=floor+3;y++) {
                pos.set(originX+x,y,originZ+z);
                var block=e.getEntityWorld().getBlockState(pos);
                var shape=block.isOf(Blocks.SNOW)?block.getOutlineShape(e.getEntityWorld(),pos):block.getCollisionShape(e.getEntityWorld(),pos);
                if(!shape.isEmpty())height=Math.max(height,y+shape.getMax(Direction.Axis.Y));
            }
            heights[z*size+x]=(float)Math.min(4,height-e.getY());
        }
        return new Ground(tick,e.getX(),e.getZ(),originX,originZ,size,heights);
    }
    private static double surface(State s,double worldX,double worldZ) {
        int x=(int)Math.floor(worldX)-s.groundX,z=(int)Math.floor(worldZ)-s.groundZ;
        return x>=0&&z>=0&&x<s.groundSize&&z<s.groundSize?s.ground[z*s.groundSize+x]:0;
    }
    private static Vec3d ground(State s,Vec3d p) {
        double a=Math.toRadians(s.yaw),c=Math.cos(a),sn=Math.sin(a);
        return p.add(0,surface(s,s.x+c*p.x-sn*p.z,s.z+sn*p.x+c*p.z),0);
    }
    @Override protected Box getBoundingBox(RocEffectEntity e) { return super.getBoundingBox(e).expand(115,100,115); }
    // Effects sit on the rock, a tornado's foot often inside a boulder: light them from the open air above.
    @Override protected int getSkyLight(RocEffectEntity e,BlockPos pos) { return e.getEntityWorld().getLightLevel(net.minecraft.world.LightType.SKY,pos.up(4)); }
    @Override protected int getBlockLight(RocEffectEntity e,BlockPos pos) { return e.getEntityWorld().getLightLevel(net.minecraft.world.LightType.BLOCK,pos.up(4)); }
    @Override public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        matrices.push(); matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));
        queue.submitCustom(matrices, SMOKE, (entry,v)->draw(s,entry,v,false));
        queue.submitCustom(matrices, LIGHT, (entry,v)->draw(s,entry,v,true));
        if(s.kind==RocEffectEntity.Kind.TORNADO)queue.submitCustom(matrices, TWISTER, (entry,v)->twister(s,entry,v));
        matrices.pop(); super.render(s,matrices,queue,camera);
    }
    private static void draw(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        switch(s.kind) {
            case SMALL_STRIKE, LARGE_STRIKE -> strike(s,m,v,light);
            case DISTANT_BOLT, STORM_BOLT, CHARGE_BOLT, CALL_BOLT -> skyBolt(s,m,v,light);
            case TORNADO -> tornado(s,m,v,light);
            case DIVE_MARKER -> marker(s,m,v,light);
            case SHOCKWAVE -> shockwave(s,m,v,light);
            case SCREAM -> scream(s,m,v,light);
            case LIVE_GROUND -> live(s,m,v,light);
            case STORM -> {}
        }
    }
    /** Seeds that differ per effect, so neighbouring bolts never share a shape. */
    private static int seed(State s) { return (int)Math.floor(s.x*7.31+s.z*13.7); }
    private static void strike(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        float progress=Math.min(1,s.tick/Math.max(1,s.warning));
        if(light) {
            ring(s,v,m,s.radius,.055,.16,0xE6FFE4A0,64);
            ring(s,v,m,s.radius*.88,.065,.07,0x80FFF3CE,48);
            int count=(int)(progress*48);
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/48;
                line(v,m,ground(s,polar(s.radius-.3,.07,a)),ground(s,polar(s.radius,.07,a)),.08,0xEDFFF9DD);
            }
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4+s.tick*.018;
                Vec3d p=ground(s,polar(s.radius*.93,.10,a));
                line(v,m,p,p.add(.14*Math.sin(i*7+s.tick),.16+.08*Math.sin(s.tick+i),.15),.035,0xCCFFFBE8);
            }
        } else disk(s,v,m,s.radius,.02,0x183E354B,48);
        float hit=s.tick-s.warning;
        if(!light||hit<0)return;
        int pulse=(int)(hit/10); float age=hit%10;
        boolean large=s.kind==RocEffectEntity.Kind.LARGE_STRIKE;
        if(pulse>=(large?3:2)||age>=4.5)return;
        float fade=1-age/5;
        bolt(v,m,Vec3d.ZERO,sky(s),seed(s)+pulse*71,(large?.3:.2)*(1-age/7),fade);
        ring(s,v,m,s.radius*(.3+age*.14),.09,.2,alpha(0xFFFFFADD,.8f*fade),48);
        // The rock flares white where it lands.
        disk(s,v,m,large?1.6:1.1,.06,alpha(0xFFFFF6DE,.7f*fade),16);
    }
    /** From the effect up into the clouds: phase one's cap cloud, or the storm's ceiling as it rolls in. */
    private static double sky(State s) {
        float storm=SummitStormClient.level(0);
        return StormSummit.CAP_Y+2+(StormSummit.STORM_CEILING-StormSummit.CAP_Y)*storm-s.y;
    }
    /**
     * Bolts that touch nobody: phase one's distant flashes from the cap cloud, phase two's storm
     * bolts from the cloud ceiling to the sea (radius is their length), the Roc's call into the
     * clouds, and the huge bolt that charges it.
     */
    private static void skyBolt(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        if(!light)return;
        switch(s.kind) {
            case DISTANT_BOLT -> { if(s.tick<4.5)bolt(v,m,Vec3d.ZERO,s.radius,seed(s),.18,1-s.tick/5); }
            case CALL_BOLT -> {
                // Its call: from the raised bill up into the clouds, flickering twice.
                double top=sky(s)-11*SummitStormClient.level(0);
                if(top>2&&(s.tick<3||s.tick>=5&&s.tick<9))bolt(v,m,Vec3d.ZERO,top,seed(s)+(s.tick<4?0:31),.34,1-s.tick/12);
            }
            case STORM_BOLT -> {
                // A double flicker, re-forking for its second flash.
                if(s.tick<2.5)bolt(v,m,Vec3d.ZERO,s.radius,seed(s),.38,1);
                else if(s.tick>=3.5&&s.tick<6.5)bolt(v,m,Vec3d.ZERO,s.radius,seed(s)+17,.32,1-(s.tick-3.5f)/4);
            }
            default -> {
                float fade=Math.min(1,(s.duration-s.tick)/6);
                int flicker=(int)(s.tick/3);
                // Brief dark gaps between strikes, so it reads as one huge flickering bolt.
                if(flicker%4==3&&s.tick<18)return;
                // Every solid core first, then the soft glows over them (see bolt).
                for(int i=0;i<3;i++)bolt(v,m,chargeBase(i,flicker),130,seed(s)+flicker*29+i*113,i==0?.9:.4,fade*(i==0?1:.75f),false,true);
                // Lightning crackles out across the Roc.
                for(int arc=0;arc<10;arc++) {
                    Vec3d p=new Vec3d(0,1,0);
                    double a=arc*.628+noise(arc+flicker*7),pitch=noise(arc*3+flicker)*.6;
                    for(int i=0;i<4;i++) {
                        Vec3d q=p.add(Math.cos(a)*2.2+noise(arc*17+i+flicker),Math.sin(pitch)*2+noise(arc*9+i)*.8,Math.sin(a)*2.2+noise(arc*23+i+flicker));
                        beam(v,m,p,q,.2,alpha(0xFFFFD45A,fade));
                        p=q;
                    }
                }
                for(int i=0;i<3;i++)bolt(v,m,chargeBase(i,flicker),130,seed(s)+flicker*29+i*113,i==0?.9:.4,fade*(i==0?1:.75f),true,true);
                // Its body glows at the strike.
                beam(v,m,new Vec3d(0,-1.5,0),new Vec3d(0,3,0),2.6,alpha(0xFFFFE7A6,.22f*fade));
            }
        }
    }
    private static Vec3d chargeBase(int i,int flicker) { return new Vec3d(noise(i*5+flicker)*.8,0,noise(i*11+flicker)*.8); }
    /** Short arcs crawl over a live circle's rock and re-form every two ticks; jumping clears its pulses. */
    private static void live(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        float fade=Math.min(1,(s.duration-s.tick)/10)*Math.min(1,s.tick/3);
        if(fade<=0)return;
        if(!light) { disk(s,v,m,s.radius,.025,alpha(0xFF2B2F48,.32f*fade),48);return; }
        ring(s,v,m,s.radius,.05,.07,alpha(0xFFFFE4A0,.6f*fade),64);
        int frame=(int)(s.tick/2);
        // Brighter just before each half-second pulse, so it can be timed.
        float charge=.55f+.45f*(float)Math.pow(((s.tick%10)/10),2);
        for(int arc=0;arc<14;arc++) {
            double a=noise(arc*13+frame*7)*Math.PI,r=Math.sqrt(Math.abs(noise(arc*31+frame*3)))*s.radius*.92;
            Vec3d p=polar(r,.09,a);
            double heading=noise(arc*5+frame)*Math.PI;
            for(int i=0;i<4;i++) {
                Vec3d q=p.add(Math.cos(heading)*.9+noise(arc*17+i+frame)*.55,0,Math.sin(heading)*.9+noise(arc*23+i+frame)*.55);
                if(q.horizontalLength()>s.radius)break;
                beam(v,m,ground(s,p),ground(s,q),.05,alpha(0xFFFFF6D8,fade*charge));
                p=q;
            }
        }
    }
    /** On the rock, faintly: where its winds start to pull, and where they slow you. */
    private static void tornado(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        if(!light)return;
        ring(s,v,m,s.radius*StormRocRules.TORNADO_REACH,.06,.05,0x30DBE8F7,64);
        ring(s,v,m,s.radius,.065,.06,0x50DBE8F7,48);
    }
    /**
     * A blocky tornado built like the game's own clouds: textured cubes of cloud spun in rings,
     * from a thick, dusty foot as wide as its catch to a broad cap overhead, wrapped in bands of
     * wind that whip around faster than the funnel turns. Rock, snow and ice it has torn up fly
     * around it, and dust swirls across the rock as far as its winds pull. Its size follows the
     * gameplay radius, and everything turns the way its winds drag you.
     */
    private static void twister(State s, MatrixStack.Entry m, VertexConsumer v) {
        double k=s.radius/5,ground=surface(s,s.x,s.z);
        float storm=SummitStormClient.level(0);
        // Under a clear sky its top frays out; in the storm it climbs into the cloud ceiling.
        double free=26+8*k,height=free+(StormSummit.STORM_CEILING+4-(s.y+ground)-free)*storm;
        int layers=Math.min(34,(int)(height/1.6));
        double gap=height/layers,lean=.9*k*height/32;
        // The world's light, not full bright: the storm darkens its tornadoes with everything else.
        int lit=s.light;
        for(int i=0;i<layers;i++) {
            double f=i/(double)(layers-1),y=ground+.4+f*height;
            double fray=Math.max(0,(f-.8)/.2)*(1-storm);
            double r=k*(1.9+5.2*Math.pow(f,1.5))+fray*1.5;
            double size=Math.sqrt(k)*(.8+1.25*f)*(1-.35*fray);
            int puffs=(int)Math.round(Math.min(20,Math.max(8,r*2.1))*(1-.5*fray));
            double spin=s.tick*(.22-.12*f)+i*.55;
            // A slow, sinuous lean keeps the column from looking rigid.
            double lx=Math.sin(s.tick*.02+f*2)*lean*f,lz=Math.cos(s.tick*.017+f*1.7)*lean*f;
            // Dusty at the rock, cloud-white overhead, slate in the storm.
            int rgb=mix(mix(0xD9D3CA,0xF4F6F8,(float)Math.min(1,f*1.4)),mix(0x8D8A86,0xA9AFB9,(float)f),storm*.75f);
            int row=f<.18?DUST:WALL;
            for(int j=0;j<puffs;j++) {
                double a=spin+j*Math.PI*2/puffs,wobble=noise(i*31+j*7);
                double rr=r*(.82+.26*wobble),w=size*(.8+.3*noise(i*13+j));
                // Solid, like the game's clouds, so no puff shows the sky through another.
                tile(v,m,Math.cos(a)*rr+lx,y+wobble*.35,Math.sin(a)*rr+lz,w,Math.max(w*.62,gap*.62),w,rgb,1,lit,row+((i+j)&3));
            }
            if(i%4==2)band(v,m,lx,y,lz,r*1.22+.6,Math.max(1.2,gap*1.6),s.tick*(.38-.1*f)+i,rgb,lit,i);
        }
        // Rock, snow and ice it has torn loose, flung up the lower funnel and dropped again.
        for(int d=0;d<22;d++) {
            double phase=s.tick*(.13+.03*noise(d))+d*2.399,lift=Math.min(height,free)*.6,rise=(s.tick*.09+d*1.9)%lift;
            double r=k*(2.4+2.6*rise/free)+.8,w=.22+.18*Math.abs(noise(d*3));
            tile(v,m,Math.cos(phase)*r,ground+.4+rise,Math.sin(phase)*r,w,w,w,0xFFFFFF,1,lit,DEBRIS+(d&3));
        }
        // Dust swirled across the rock, out to where its winds start to pull.
        double reach=s.radius*StormRocRules.TORNADO_REACH;
        for(int j=0;j<28;j++) {
            double a=s.tick*(.16+.06*noise(j))+j*2.399,rr=k*2+(reach-k*2)*(.15+.85*Math.abs(noise(j*7)));
            double w=(.5+.3*Math.abs(noise(j*5)))*Math.sqrt(k);
            Vec3d at=ground(s,new Vec3d(Math.cos(a)*rr,.3,Math.sin(a)*rr));
            tile(v,m,at.x,at.y,at.z,w,.2,w,mix(0xE2D9CC,0x8C8A88,storm),.75f*(float)(1-.6*rr/reach),lit,DUST+(j&3));
        }
    }
    /** One ring of wind around the funnel: streak tiles, open wherever the tile is clear. */
    private static void band(VertexConsumer v,MatrixStack.Entry m,double cx,double y,double cz,double r,double h,double turn,int rgb,int light,int seed) {
        int segments=Math.max(10,(int)(r*2.2)),color=shade(rgb,1,.85f);
        float y0=(float)(y-h/2),y1=(float)(y+h/2);
        for(int i=0;i<segments;i++) {
            double a=turn+i*Math.PI*2/segments,b=turn+(i+1)*Math.PI*2/segments;
            float x0=(float)(cx+Math.cos(a)*r),z0=(float)(cz+Math.sin(a)*r),x1=(float)(cx+Math.cos(b)*r),z1=(float)(cz+Math.sin(b)*r);
            face(v,m,x0,y0,z0,x1,y0,z1,x1,y1,z1,x0,y1,z0,color,light,BAND+((i+seed)&3));
        }
    }
    private static int mix(int a,int b,float f) {
        f=Math.max(0,Math.min(1,f));
        int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),bl=(int)((a&255)*(1-f)+(b&255)*f);
        return r<<16|g<<8|bl;
    }
    private static void marker(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        float f=Math.min(1,s.tick/Math.max(1,s.warning>0?s.warning:s.duration));
        if(!light) { disk(s,v,m,s.radius,.015,0x25273048,64);disk(s,v,m,s.radius*(.15+.85*f),.025,0x58313B54,64); }
        else {
            ring(s,v,m,s.radius,.05,.18,0xEDFFF0CB,96);
            ring(s,v,m,s.radius*(.15+.85*f),.06,.10,0xAFFFDC9B,72);
            for(int i=0;i<16;i++) {
                double a=i*Math.PI/8;
                line(v,m,ground(s,polar(s.radius-.9,.08,a)),ground(s,polar(s.radius+.45,.08,a)),.11,0xE6FFF4D1);
            }
        }
    }
    private static void shockwave(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        double r=Math.min(s.radius,s.tick*1.25);
        float fade=Math.min(1,(s.duration-s.tick)/8);
        if(r<.1)return;
        if(light) {
            ring(s,v,m,r,.22,.14,alpha(0xFFFFEFC8,fade*.9f),96);
            for(int i=0;i<48;i++) {
                double a=i*Math.PI/24;
                Vec3d p=ground(s,polar(r-.35,.12,a)),q=ground(s,polar(r,.35+.25*Math.abs(noise(i+(int)s.tick/2)),a+.025));
                line(v,m,p,q,.06,alpha(0xFFF3F8FF,fade));
                line(v,m,q,ground(s,polar(r+.4,.10,a+.04)),.04,alpha(0xFFDFE9FF,fade));
            }
        } else {
            int segments=Math.max(72,(int)Math.ceil(r*Math.PI*2/.65));
            for(int i=0;i<segments;i++) {
                double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments,h=.35+.3*Math.abs(noise(i*17));
                quad(v,m,ground(s,polar(r-.65,.03,a)),ground(s,polar(r-.65,.03,b)),ground(s,polar(r+.45,h,b)),ground(s,polar(r+.45,h,a)),alpha(0xFF69718B,fade*.40f));
            }
        }
    }
    private static void scream(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        if(!light)return;
        double length=110, width=Math.max(2.5,s.radius);
        float fade=Math.min(1,(s.duration-s.tick)/5);
        for(int j=0;j<18;j++) {
            double z=(j*6.5+s.tick*2.4)%length;
            double r=width*(.80+.15*Math.sin(j+s.tick*.3));
            for(int i=0;i<10;i++) {
                double a=i*Math.PI/5,b=(i+1)*Math.PI/5;
                line(v,m,new Vec3d(Math.cos(a)*r,Math.sin(a)*r,z),new Vec3d(Math.cos(b)*r,Math.sin(b)*r,z),.045,alpha(0xFFE0ECFF,fade*.48f));
            }
        }
        for(int i=0;i<3;i++)for(int j=0;j<22;j++) {
            double phase=(int)(s.tick/2)+i*33;
            Vec3d a=new Vec3d(noise(j*19+phase)*width*.6,noise(j*11+phase)*width*.6,j*5);
            Vec3d b=new Vec3d(noise((j+1)*19+phase)*width*.6,noise((j+1)*11+phase)*width*.6,(j+1)*5);
            line(v,m,a,b,.055,alpha(0xFFFFF1C8,fade*.85f));
        }
    }
    private static double noise(double seed) { double x=Math.sin(seed*127.1+311.7)*43758.5453;return (x-Math.floor(x))*2-1; }
    private static int alpha(int color,float a) { return (color&0xFFFFFF)|((Math.max(0,Math.min(255,(int)(255*a))))<<24); }
    private static Vec3d polar(double r,double y,double a) { return new Vec3d(Math.cos(a)*r,y,Math.sin(a)*r); }
    private static void ring(State s,VertexConsumer v,MatrixStack.Entry m,double r,double y,double w,int color,int segments) {
        segments=Math.max(segments,(int)Math.ceil(r*Math.PI*2/.65));
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            quad(v,m,ground(s,polar(r-w,y,a)),ground(s,polar(r-w,y,b)),ground(s,polar(r+w,y,b)),ground(s,polar(r+w,y,a)),color);
        }
    }
    private static void disk(State s,VertexConsumer v,MatrixStack.Entry m,double r,double y,int color,int segments) {
        // Independent world-cell patches conform to snow/low rocks rather than
        // spanning a single enormous triangle through the terrain.
        double angle=Math.toRadians(s.yaw),c=Math.cos(angle),sn=Math.sin(angle);
        int minX=(int)Math.floor(s.x-r),maxX=(int)Math.ceil(s.x+r);
        int minZ=(int)Math.floor(s.z-r),maxZ=(int)Math.ceil(s.z+r);
        for(int z=minZ;z<maxZ;z++)for(int x=minX;x<maxX;x++) {
            double dx=x+.5-s.x,dz=z+.5-s.z;
            if(dx*dx+dz*dz>(r+.71)*(r+.71))continue;
            Vec3d[] corners=new Vec3d[4];
            for(int i=0;i<4;i++) {
                double wx=x+((i==1||i==2)?1:0)-s.x,wz=z+(i>=2?1:0)-s.z;
                double length=Math.sqrt(wx*wx+wz*wz);
                if(length>r){wx*=r/length;wz*=r/length;}
                corners[i]=new Vec3d(c*wx+sn*wz,y+surface(s,x+.5,z+.5),-sn*wx+c*wz);
            }
            quad(v,m,corners[0],corners[3],corners[2],corners[1],color);
        }
    }
    private static void line(VertexConsumer v,MatrixStack.Entry m,Vec3d a,Vec3d b,double w,int color) {
        Vec3d d=b.subtract(a).normalize();
        Vec3d side=d.crossProduct(Math.abs(d.y)<.9?new Vec3d(0,1,0):new Vec3d(1,0,0)).normalize().multiply(w);
        Vec3d up=d.crossProduct(side).normalize().multiply(w);
        quad(v,m,a.subtract(side),b.subtract(side),b.add(side),a.add(side),color);
        quad(v,m,a.subtract(up),b.subtract(up),b.add(up),a.add(up),color);
    }
    /**
     * A jagged, blocky bolt from base straight up: square segments with a hot core inside a soft
     * gold glow, and forks that drop from every third joint.
     */
    private static void bolt(VertexConsumer v,MatrixStack.Entry m,Vec3d base,double height,int seed,double core,float fade) {
        bolt(v,m,base,height,seed,core,fade,false,false);
        bolt(v,m,base,height,seed,core,fade,true,false);
    }
    /**
     * One pass of a bolt: the core and forks, or the glow. Overlapping bolts draw all cores first.
     * A gold bolt keeps its contrast against storm clouds lit by its own flash.
     */
    private static void bolt(VertexConsumer v,MatrixStack.Entry m,Vec3d base,double height,int seed,double core,float fade,boolean glow,boolean gold) {
        if(fade<=0||core<=0)return;
        int steps=Math.max(6,(int)(height/5));
        double step=height/steps;
        Vec3d[] joints=new Vec3d[steps+1];
        joints[0]=base;
        for(int i=1;i<=steps;i++)joints[i]=base.add(noise(i*19+seed*71)*1.8,i*step,noise(i*43+seed*33)*1.8);
        // The hot core and forks go first: the glow writes depth, and drawn earlier it would hide
        // the core inside it, leaving only a faint gold haze.
        if(glow) {
            for(int i=1;i<=steps;i++)beam(v,m,joints[i-1],joints[i],core*2.6,alpha(gold?0xFFFFA928:0xFFFFD27A,(gold?.5f:.34f)*fade));
            return;
        }
        for(int i=1;i<=steps;i++) {
            beam(v,m,joints[i-1],joints[i],core,alpha(gold?0xFFFFE070:0xFFFFFFEE,fade));
            if(i%3==0&&i<steps) {
                Vec3d fork=joints[i].add(noise(i*9+seed)*5,-6,noise(i*21+seed)*5);
                beam(v,m,joints[i],fork,core*.55,alpha(0xFFFFF3CA,.9f*fade));
                beam(v,m,fork,fork.add(noise(i+5+seed)*3,-4,noise(i+9+seed)*3),core*.3,alpha(0xFFFFF7DA,.75f*fade));
            }
        }
    }
    /** A square prism along a segment; chunkier than crossed quads, and solid from every side. */
    private static void beam(VertexConsumer v,MatrixStack.Entry m,Vec3d a,Vec3d b,double w,int color) {
        Vec3d d=b.subtract(a);
        if(d.lengthSquared()<1e-6)return;
        d=d.normalize();
        Vec3d side=d.crossProduct(Math.abs(d.y)<.9?new Vec3d(0,1,0):new Vec3d(1,0,0)).normalize().multiply(w);
        Vec3d up=d.crossProduct(side).normalize().multiply(w);
        Vec3d[] c={side.add(up),side.subtract(up),side.negate().subtract(up),side.negate().add(up)};
        for(int i=0;i<4;i++) {
            Vec3d p=c[i],q=c[(i+1)%4];
            quad(v,m,a.add(p),b.add(p),b.add(q),a.add(q),color);
        }
    }
    /**
     * An axis-aligned box shaded per face like the game's clouds: lit top, darker sides, dark base.
     * Each face shows a tile from tile's row of the atlas, varied per face so neighbours differ.
     * Written straight to the buffer, since a storm's tornadoes draw several hundred a frame.
     */
    private static void tile(VertexConsumer v,MatrixStack.Entry m,double cx,double cy,double cz,double hx,double hy,double hz,int rgb,float a,int light,int tile) {
        float x0=(float)(cx-hx),x1=(float)(cx+hx),y0=(float)(cy-hy),y1=(float)(cy+hy),z0=(float)(cz-hz),z1=(float)(cz+hz);
        int top=shade(rgb,1,a),bottom=shade(rgb,.7f,a),ns=shade(rgb,.8f,a),ew=shade(rgb,.9f,a),row=tile&~3;
        face(v,m,x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0,top,light,tile);
        face(v,m,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,bottom,light,row+((tile+1)&3));
        face(v,m,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,ns,light,row+((tile+2)&3));
        face(v,m,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,ns,light,row+((tile+3)&3));
        face(v,m,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,ew,light,tile);
        face(v,m,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,ew,light,row+((tile+1)&3));
    }
    /** A quad from a to d, with the tile's bottom edge along a-b and its top along d-c. */
    private static void face(VertexConsumer v,MatrixStack.Entry m,float ax,float ay,float az,float bx,float by,float bz,
            float cx,float cy,float cz,float dx,float dy,float dz,int color,int light,int tile) {
        float u0=(tile&3)/4f,v0=(tile>>2)/4f,u1=u0+.25f,v1=v0+.25f;
        corner(v,m,ax,ay,az,u0,v1,color,light);corner(v,m,bx,by,bz,u1,v1,color,light);
        corner(v,m,cx,cy,cz,u1,v0,color,light);corner(v,m,dx,dy,dz,u0,v0,color,light);
    }
    private static void corner(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float u,float t,int color,int light) {
        v.vertex(m,x,y,z).color(color).texture(u,t).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m,0,1,0);
    }
    private static int shade(int rgb,float f,float a) {
        int r=(int)(((rgb>>16)&255)*f),g=(int)(((rgb>>8)&255)*f),b=(int)((rgb&255)*f);
        return alpha(r<<16|g<<8|b,a);
    }
    private static void quad(VertexConsumer v,MatrixStack.Entry m,Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color) {
        vertex(v,m,a,0,0,color);vertex(v,m,b,1,0,color);vertex(v,m,c,1,1,color);vertex(v,m,d,0,1,color);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,Vec3d p,float u,float t,int color) {
        v.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color).texture(u,t)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(m,0,1,0);
    }
}
