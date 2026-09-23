package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.SpringbloomEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class SpringbloomRenderer extends EntityRenderer<SpringbloomEntity,SpringbloomRenderer.State> {
    public static final class State extends EntityRenderState {boolean open;float elapsed;int cells;}
    public SpringbloomRenderer(EntityRendererFactory.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void updateRenderState(SpringbloomEntity e,State s,float delta){super.updateRenderState(e,s,delta);s.open=e.open();s.cells=e.openCells();s.elapsed=e.open()?e.elapsed(delta):e.age+delta;}
    @Override public void render(State s,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera){
        if(s.invisible)return;
        matrices.push();
        if(!s.open)matrices.multiply(RotationAxis.POSITIVE_Y.rotation(s.elapsed*.18f));
        // Ready instantly; only the final second changes visually. Collision lasts exactly 80 ticks.
        String mesh=s.open?(s.elapsed>=70?"springbloom_wilt":"springbloom_pad"):"springbloom_pod";
        queue.submitCustom(matrices,NatureMeshes.LAYER,(entry,out)->{
            if(!s.open || s.cells==511)NatureMeshes.draw(mesh,out,entry,s.light,1,-1,null,false);
            else for(int cell=0;cell<9;cell++)if((s.cells&(1<<cell))!=0)
                NatureMeshes.draw(mesh+"_cell_"+cell,out,entry,s.light,1,-1,null,false);
        });
        matrices.pop();super.render(s,matrices,queue,camera);
    }
}
