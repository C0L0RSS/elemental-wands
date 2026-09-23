package com.anton.elementalwands.client.particle;

import com.anton.elementalwands.client.renderer.SpellViewClearance;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small stationary pressure ring perpendicular to the dagger's flight, expanding as it fades. */
final class GaleRingParticle extends BillboardParticle {
    private final SpriteProvider sprites;
    private final Quaternionf orientation;
    private final Vector3f normal;
    GaleRingParticle(ClientWorld world,double x,double y,double z,double dx,double dy,double dz,SpriteProvider sprites){
        super(world,x,y,z,sprites.getFirst());this.sprites=sprites;
        Vector3f axis=new Vector3f((float)dx,(float)dy,(float)dz);
        if(axis.lengthSquared()<1e-6f)axis.set(0,0,1);else axis.normalize();
        normal=axis;orientation=new Quaternionf().rotationTo(new Vector3f(0,0,1),axis);
        setVelocity(0,0,0);collidesWithWorld=false;gravityStrength=0;maxAge=5;
        scale=.12f;alpha=.65f;setColor(1,1,1);updateSprite(sprites);
    }
    @Override public void tick(){
        super.tick();if(dead)return;
        float progress=age/(float)maxAge;
        scale=.12f+.04f*progress;alpha=.65f*(1-progress);updateSprite(sprites);
    }
    @Override public void render(BillboardParticleSubmittable out,Camera camera,float delta){
        float savedAlpha=alpha;
        double dx=x-camera.getPos().x,dy=y-camera.getPos().y,dz=z-camera.getPos().z;
        alpha*=SpellViewClearance.opacity(!camera.isThirdPerson(),Math.sqrt(dx*dx+dy*dy+dz*dz),scale);
        // Particle quads cull their back face. Keep the ring in the flight plane,
        // but show its visible side to observers on either side of that plane.
        Quaternionf facing=new Quaternionf(orientation);
        if(normal.dot((float)-dx,(float)-dy,(float)-dz)<0)facing.rotateY((float)Math.PI);
        try{if(alpha>0)render(out,camera,facing,delta);}finally{alpha=savedAlpha;}
    }
    @Override protected RenderType getRenderType(){return RenderType.PARTICLE_ATLAS_TRANSLUCENT;}
    @Override protected int getBrightness(float delta){return 0xF000F0;}
}
