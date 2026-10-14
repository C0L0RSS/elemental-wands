package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.roc.RocEffectEntity;
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
    public static final class State extends EntityRenderState {
        RocEffectEntity.Kind kind;
        float tick, yaw, radius;
        int duration, warning;
        float[] ground = new float[0];
        int groundX, groundZ, groundSize;
        long groundTick = Long.MIN_VALUE;
        double groundOriginX, groundOriginZ, groundOriginY;

    }
    public RocEffectRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius=0; }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(RocEffectEntity e, State s, float delta) {
        super.updateRenderState(e,s,delta);
        s.kind=e.kind(); s.tick=e.effectTicks(delta); s.duration=e.duration();
        s.radius=(float)e.radius(); s.warning=e.warningTicks(); s.yaw=e.getYaw();
        sampleGround(e,s);
    }
    private static void sampleGround(RocEffectEntity e, State s) {
        if(s.kind==RocEffectEntity.Kind.SCREAM||s.kind==RocEffectEntity.Kind.DISTANT_BOLT) { s.groundSize=0;return; }
        long tick=e.getEntityWorld().getTime();
        if(s.groundTick==tick && s.groundOriginX==e.getX() && s.groundOriginZ==e.getZ())return;
        s.groundTick=tick;s.groundOriginX=e.getX();s.groundOriginZ=e.getZ();s.groundOriginY=e.getY();
        boolean wave=s.kind==RocEffectEntity.Kind.SHOCKWAVE;
        double radius=wave?Math.min(s.radius,(Math.floor(s.tick)+1)*1.25):s.radius;
        int extent=(int)Math.ceil(Math.min(72,radius)+3);
        s.groundX=(int)Math.floor(e.getX())-extent;s.groundZ=(int)Math.floor(e.getZ())-extent;
        s.groundSize=extent*2+1;
        int cells=s.groundSize*s.groundSize;
        if(s.ground.length!=cells)s.ground=new float[cells];
        java.util.Arrays.fill(s.ground,0);
        BlockPos.Mutable pos=new BlockPos.Mutable();
        int floor=(int)Math.floor(e.getY());
        for(int z=0;z<s.groundSize;z++)for(int x=0;x<s.groundSize;x++) {
            double dx=s.groundX+x+.5-e.getX(),dz=s.groundZ+z+.5-e.getZ();
            double distance=Math.sqrt(dx*dx+dz*dz);
            if(distance>radius+2.5 || wave && distance<Math.max(0,radius-4))continue;
            double height=e.getY();
            // Snow's visual top is above its collision top by one layer.
            // Search only the summit surface band: distant cliffs/spires never
            // pull a huge decal up into the sky. At most five block reads/cell.
            for(int y=floor-1;y<=floor+3;y++) {
                pos.set(s.groundX+x,y,s.groundZ+z);
                var block=e.getEntityWorld().getBlockState(pos);
                var shape=block.isOf(Blocks.SNOW)?block.getOutlineShape(e.getEntityWorld(),pos):block.getCollisionShape(e.getEntityWorld(),pos);
                if(!shape.isEmpty())height=Math.max(height,y+shape.getMax(Direction.Axis.Y));
            }
            s.ground[z*s.groundSize+x]=(float)Math.min(4,height-e.getY());
        }
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
    @Override public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        matrices.push(); matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));
        queue.submitCustom(matrices, SMOKE, (entry,v)->draw(s,entry,v,false));
        queue.submitCustom(matrices, LIGHT, (entry,v)->draw(s,entry,v,true));
        matrices.pop(); super.render(s,matrices,queue,camera);
    }
    private static void draw(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        switch(s.kind) {
            case SMALL_STRIKE, LARGE_STRIKE, DISTANT_BOLT -> strike(s,m,v,light);
            case TORNADO -> tornado(s,m,v,light);
            case DIVE_MARKER -> marker(s,m,v,light);
            case SHOCKWAVE -> shockwave(s,m,v,light);
            case SCREAM -> scream(s,m,v,light);
        }
    }
    private static void strike(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        float progress=Math.min(1,s.tick/Math.max(1,s.warning));
        boolean distant=s.kind==RocEffectEntity.Kind.DISTANT_BOLT;
        if(light && !distant) {
            ring(s,v,m,s.radius,.055,.11,0xE6FFE4A0,64);
            ring(s,v,m,s.radius*.88,.065,.055,0x80FFF3CE,48);
            int count=(int)(progress*48);
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/48;
                line(v,m,ground(s,polar(s.radius-.23,.07,a)),ground(s,polar(s.radius,.07,a)),.065,0xEDFFF9DD);
            }
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4+s.tick*.018;
                Vec3d p=ground(s,polar(s.radius*.93,.10,a));
                line(v,m,p,p.add(.14*Math.sin(i*7+s.tick),.16+.08*Math.sin(s.tick+i),.15),.027,0xCCFFFBE8);
            }
        } else if(!light&&!distant) disk(s,v,m,s.radius,.02,0x183E354B,48);
        float hit=s.tick-s.warning;
        if(!light||hit<0)return;
        int pulse=(int)(hit/10); float age=hit%10;
        int bolts=s.kind==RocEffectEntity.Kind.LARGE_STRIKE?3:2;
        if(!distant&&pulse>=bolts)return;
        if(age<4.5) {
            double width=(s.kind==RocEffectEntity.Kind.LARGE_STRIKE?.22:.13)*(1-age/6);
            Vec3d previous=Vec3d.ZERO;
            for(int i=1;i<=13;i++) {
                double y=i*5;
                Vec3d next=new Vec3d(noise(i*19+pulse*71)*1.7,y,noise(i*43+pulse*33)*1.7);
                line(v,m,previous,next,width*2.7,0x65FFD27A);
                line(v,m,previous,next,width,0xFFFFFFEB);
                if(i%3==0) {
                    Vec3d branch=next.add(noise(i*9+pulse)*5,-7,noise(i*21+pulse)*5);
                    line(v,m,next,branch,width*.6,0xDFFFF3CA);
                    line(v,m,branch,branch.add(noise(i+5)*3,-5,noise(i+9)*3),width*.3,0xAFFFF7DA);
                }
                previous=next;
            }
            if(!distant)ring(s,v,m,s.radius*(.3+age*.14),.09,.18,0xCCFFFADD,48);
        }
    }
    private static void tornado(State s, MatrixStack.Entry m, VertexConsumer v, boolean light) {
        double ground=surface(s,s.x,s.z);
        if(!light) {
            // Continuous spirals, with shared segment endpoints, replace the
            // disconnected tiles that read as a checkerboard in native views.
            for(int strand=0;strand<3;strand++) {
                double phase=s.tick*(.080+strand*.004)+strand*Math.PI*2/3;
                int segments=100;
                for(int i=0;i<segments;i++) {
                    double f=i/(double)segments,g=(i+1)/(double)segments;
                    double y=.16+17.6*f,z=.16+17.6*g;
                    double wa=.34+.43*f+.08*Math.sin(f*21+strand),wb=.34+.43*g+.08*Math.sin(g*21+strand);
                    Vec3d a=funnel(y,phase,ground),b=funnel(z,phase,ground);
                    float fade=(float)(Math.min(1,f*12)*Math.min(1,(1-g)*10));
                    int rgb=strand==1?0xFF90A2B4:0xFF70839B;
                    float opacity=(float)(.27+.06*Math.sin(f*18+strand));
                    quad(v,m,a.add(0,-wa,0),b.add(0,-wb,0),b.add(0,wb,0),a.add(0,wa,0),alpha(rgb,fade*opacity));
                    // Feathered borders keep the funnel airy without breaking
                    // the silhouette into separate geometric cells.
                    quad(v,m,a.add(0,-wa-.23,0),b.add(0,-wb-.23,0),b.add(0,-wb,0),a.add(0,-wa,0),alpha(rgb,fade*opacity*.30f));
                    quad(v,m,a.add(0,wa,0),b.add(0,wb,0),b.add(0,wb+.23,0),a.add(0,wa+.23,0),alpha(rgb,fade*opacity*.30f));
                }
            }
            // Unequal cloud swaths interrupt the regular helix rhythm. Their
            // tapered ends and different speeds prevent a stacked-ring look.
            for(int cloud=0;cloud<6;cloud++)for(int i=0;i<20;i++) {
                double f=i/20.,g=(i+1)/20.;
                double centerY=2.0+cloud*2.45+.4*Math.sin(s.tick*.018+cloud*2);
                double start=s.tick*(.047+cloud*.003)+cloud*2.399;
                double span=1.1+.7*Math.abs(noise(cloud*7));
                double radius=.9+3.25*Math.pow(centerY/18,1.35);
                Vec3d a=polar(radius,ground+centerY+f*.7,start+f*span);
                Vec3d b=polar(radius,ground+centerY+g*.7,start+g*span);
                double wa=.48*Math.sin(f*Math.PI),wb=.48*Math.sin(g*Math.PI);
                quad(v,m,a.add(0,-wa,0),b.add(0,-wb,0),b.add(0,wb,0),a.add(0,wa,0),0x287F91A8);
            }
            // Dust at the base makes the vortex feel connected to the rock.
            for(int arm=0;arm<3;arm++)for(int i=0;i<18;i++) {
                double f=i/18.,g=(i+1)/18.,a=s.tick*.085+arm*2.094+f*2.2,b=s.tick*.085+arm*2.094+g*2.2;
                double r=1.0+f*2.6,q=1.0+g*2.6;
                Vec3d p=ground(s,polar(r,.13+f*.12,a)),n=ground(s,polar(q,.13+g*.12,b));
                float fade=(float)(Math.sin(f*Math.PI)*.22);
                quad(v,m,p.add(0,-.10,0),n.add(0,-.10,0),n.add(0,.24,0),p.add(0,.24,0),alpha(0xFFAAA99F,fade));
            }
            disk(s,v,m,1.4,.03,0x49333B56,24);
        } else {
            ring(s,v,m,s.radius,.065,.045,0x45DBE8F7,48);
            // A few moving wind streaks, not a full bright wire cage.
            for(int streak=0;streak<4;streak++)for(int i=0;i<15;i++) {
                double y=.4+((s.tick*.075+streak*4.1)%12)+i*.24;
                double phase=s.tick*.11+streak*2.399;
                float fade=(float)(Math.sin(i/15.*Math.PI)*.23);
                line(v,m,funnel(y,phase,ground),funnel(y+.24,phase,ground),.018,alpha(0xFFDDEAF4,fade));
            }
            for(int i=0;i<12;i++) {
                double y=.25+(s.tick*.12+i*1.37)%13;
                double r=1.15+2.0*Math.pow(y/18,1.2),a=s.tick*.14+i*2.399;
                Vec3d p=polar(r,ground+y,a);
                line(v,m,p,p.add(.10*Math.cos(a),.10,.10*Math.sin(a)),i%3==0?.10:.06,i%3==0?0x8F9B9C99:0x65CFD5D7);
            }
        }
    }
    private static Vec3d funnel(double y,double phase,double ground) {
        double f=y/18,angle=phase-y*.76;
        double radius=.82+3.25*Math.pow(f,1.35)+.09*Math.sin(y*1.7+phase*.35);
        // Small coherent lean; there is no particle noise or random frame jitter.
        return polar(radius,ground+y,angle).add(.18*f*Math.sin(phase*.22+y*.16),0,.16*f*Math.cos(phase*.19+y*.12));
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
    private static void quad(VertexConsumer v,MatrixStack.Entry m,Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color) {
        vertex(v,m,a,0,0,color);vertex(v,m,b,1,0,color);vertex(v,m,c,1,1,color);vertex(v,m,d,0,1,color);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,Vec3d p,float u,float t,int color) {
        v.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color).texture(u,t)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(m,0,1,0);
    }
}
