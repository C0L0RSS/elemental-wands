package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.SeedProjectileEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class NatureSeedRenderer extends EntityRenderer<SeedProjectileEntity,NatureSeedRenderer.State> {
    public static final class State extends EntityRenderState {float spin;}
    public NatureSeedRenderer(EntityRendererFactory.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void updateRenderState(SeedProjectileEntity entity,State state,float delta){
        super.updateRenderState(entity,state,delta);state.spin=(entity.age+delta)*.19f;
    }
    @Override public void render(State state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera){
        float alpha=SpellViewClearance.opacity(!MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson(),
                camera.pos.distanceTo(new net.minecraft.util.math.Vec3d(state.x,state.y,state.z)),.44);
        if(alpha<=0 || state.invisible)return;
        int light=state.light;
        matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.spin));
        queue.submitCustom(matrices,NatureMeshes.LAYER,(entry,out)->NatureMeshes.draw("seed",out,entry,light,alpha,-1,null,false));
        matrices.pop();super.render(state,matrices,queue,camera);
    }
}
