package com.anton.elementalwands.client.wand;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exact union of the approved wooden boxes: only one exterior surface is drawn. */
public final class WandMesh {
    public record Data(float scale, float rotationZ, float headY, float headScale,
                       float[][] wood, float[][] guiWood, String[] elementOrder, int[] particleColors) {}
    public static final Data DATA = load();
    public static final int[] COLORS = DATA.particleColors();
    private static final float[][] NORMALS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final float[][] FACES = {
        {1,-1,-1, 1,1,-1, 1,1,1, 1,-1,1},
        {-1,-1,1, -1,1,1, -1,1,-1, -1,-1,-1},
        {-1,1,-1, -1,1,1, 1,1,1, 1,1,-1},
        {-1,-1,1, -1,-1,-1, 1,-1,-1, 1,-1,1},
        {1,-1,1, 1,1,1, -1,1,1, -1,-1,1},
        {-1,-1,-1, -1,1,-1, 1,1,-1, 1,-1,-1}
    };
    public static final Vector3f[] BOUNDS = bounds(false);
    public static final Vector3f[] GUI_BOUNDS = bounds(true);
    private static Data load() {
        var stream=WandMesh.class.getResourceAsStream("/assets/elementalwands/wand/mesh.json");
        if(stream==null)throw new IllegalStateException("Missing wand mesh");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader,Data.class);
        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }
    public static int element(String name) {
        for(int i=0;i<DATA.elementOrder.length;i++)if(DATA.elementOrder[i].equals(name))return i;
        return 0;
    }
    private static Matrix4f modelTransform(boolean gui) {
        var m=new Matrix4f().translation(.5f,.5f,.5f);
        if(gui)return m.rotateZ((float)Math.toRadians(-20)).rotateY((float)Math.toRadians(-20))
                .scale(.24f).translate(0,-4.3f,0);
        return m.rotateZ((float)Math.toRadians(DATA.rotationZ)).scale(DATA.scale);
    }
    public static void transform(MatrixStack matrices,boolean gui) {
        matrices.translate(.5,.5,.5);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(gui?-20:DATA.rotationZ));
        if(gui)matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-20));
        float scale=gui?.24f:DATA.scale;
        matrices.scale(scale,scale,scale);
        if(gui)matrices.translate(0,-4.3,0);
    }
    private static Vector3f[] bounds(boolean gui) {
        var m=modelTransform(gui);
        var result=new Vector3f[8];int i=0;
        for(float x:new float[]{-1.1f,1.1f})for(float y:new float[]{gui?3f:-6.04f,5.65f})for(float z:new float[]{-1.1f,1.1f})
            result[i++]=m.transformPosition(new Vector3f(x,y,z));
        return result;
    }
    public static void wood(VertexConsumer out,MatrixStack.Entry entry,int light,int overlay,boolean gui) {
        for(float[] q:gui?DATA.guiWood:DATA.wood) {
            float shade=q[14]>0?.86f:q[14]<0?.70f:q[13]>0?1f:q[13]<0?.62f:.82f;
            int color=tint(q[23]==0?0xffffff:0xe8cfa7,shade);
            for(int i=0;i<4;i++)vertex(out,entry,q[i*3],q[i*3+1],q[i*3+2],color,
                    q[15+i*2],q[16+i*2],light,overlay,q[12],q[13],q[14]);
        }
    }
    public static int tint(int rgb,float amount) {
        return 0xff000000 | Math.round(((rgb>>16)&255)*amount)<<16
                | Math.round(((rgb>>8)&255)*amount)<<8 | Math.round((rgb&255)*amount);
    }
    public static void cube(VertexConsumer out,MatrixStack.Entry entry,float half,int rgb,int light,int overlay,
                            float minU,float minV,float maxU,float maxV,boolean shade) {
        float[] shades={.82f,.65f,1f,.55f,.92f,.73f};
        float[] us={minU,minU,maxU,maxU},vs={maxV,minV,minV,maxV};
        for(int face=0;face<6;face++) {
            float[] n=NORMALS[face],p=FACES[face];int color=shade?tint(rgb,shades[face]):0xff000000|rgb;
            for(int i=0;i<4;i++)vertex(out,entry,p[i*3]*half,p[i*3+1]*half,p[i*3+2]*half,
                    color,us[i],vs[i],light,overlay,n[0],n[1],n[2]);
        }
    }
    private static void vertex(VertexConsumer out,MatrixStack.Entry e,float x,float y,float z,int color,
                               float u,float v,int light,int overlay,float nx,float ny,float nz) {
        out.vertex(e,x,y,z).color(color).texture(u,v).overlay(overlay).light(light).normal(e,nx,ny,nz);
    }
    private WandMesh() {}
}
