package com.anton.elementalwands.client.wand;

import java.util.Set;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Immutable extraction data keeps held, GUI, remote-player and dropped render calls independent. */
public final class WandRenderer implements SpecialModelRenderer<WandRenderer.State> {
    public record State(int element, double seconds) {}
    private static final RenderLayer WOOD=RenderLayer.getEntitySolid(Identifier.of("elementalwands","textures/wand/wood.png"));
    private static final RenderLayer CORE=RenderLayer.getEntityTranslucentEmissive(Identifier.of("elementalwands","textures/wand/cores.png"));
    private static final RenderLayer GLASS=RenderLayer.getItemEntityTranslucentCull(Identifier.of("elementalwands","textures/wand/glass.png"));
    @Override public State getData(ItemStack stack) {return new State(0,0);}
    @Override public void collectVertices(Set<Vector3f> vertices) {for(var v:WandMesh.BOUNDS)vertices.add(new Vector3f(v));}
    @Override public void render(State state,ItemDisplayContext context,MatrixStack matrices,
                                 OrderedRenderCommandQueue queue,int light,int overlay,boolean glint,int outline) {
        if(state==null)return;
        double t=state.seconds;int element=state.element;
        boolean gui=context==ItemDisplayContext.GUI;
        matrices.push();WandMesh.transform(matrices,gui);
        queue.submitCustom(matrices,WOOD,(entry,out)->WandMesh.wood(out,entry,light,overlay,gui));
        matrices.translate(0,WandMesh.DATA.headY(),0);
        matrices.scale(WandMesh.DATA.headScale(),WandMesh.DATA.headScale(),WandMesh.DATA.headScale());
        matrices.push();rotateCore(matrices,t);
        // Muted painted colors are self-lit, with authored face shading rather than neon emission.
        float u0=element*10/64f,u1=(element*10+8)/64f;
        queue.submitCustom(matrices,CORE,(entry,out)->WandMesh.cube(out,entry,.47f,0xffffff,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,u0,0,u1,.5f,true));
        matrices.pop();
        for(int i=0;i<8;i++) {
            matrices.push();var p=mote(i,t);matrices.translate(p.x,p.y,p.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)(t*.2+i)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(t*.27+i*.6)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float)(t*.13)));
            queue.submitCustom(matrices,CORE,(entry,out)->WandMesh.cube(out,entry,.0375f,WandMesh.COLORS[element],
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,.99f,.99f,.99f,.99f,true));
            matrices.pop();
        }
        // One outward-facing shell; texture contains the rim/reflections, so no coplanar overlays.
        // The later batch guarantees all opaque contents are written before the glass blends.
        queue.getBatchingQueue(1).submitCustom(matrices,GLASS,(entry,out)->WandMesh.cube(out,entry,.89f,
                0xffffff,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,0,0,1,1,false));
        matrices.pop();
    }
    public static void rotateCore(MatrixStack matrices,double t) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)(.08*Math.sin(t*.23))));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(.18+t*.32)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float)(.06*Math.sin(t*.19))));
    }
    public static Vector3f mote(int i,double t) {
        double a=i*2.399+t*(.38+i*.017);
        if(i<4)return new Vector3f((float)(Math.cos(a)*.79),(float)(.09*Math.sin(t*.5+i)),(float)(Math.sin(a)*.79));
        double r=.34+.035*Math.sin(t*.37+i);
        return new Vector3f((float)(Math.cos(a)*r),(float)((i%2==1?1:-1)*(.71+.02*Math.sin(t*.41+i))),(float)(Math.sin(a)*r));
    }
}
