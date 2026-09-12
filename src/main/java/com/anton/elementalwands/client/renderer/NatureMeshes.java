package com.anton.elementalwands.client.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Approved square-beam/stepped-leaf meshes, with Minecraft pixel grain and world lighting. */
public final class NatureMeshes {
    public static final Identifier TEXTURE=Identifier.ofVanilla("textures/block/white_concrete_powder.png");
    public static final RenderLayer LAYER=RenderLayer.getEntityTranslucent(TEXTURE);
    private static final Map<String,float[]> MESHES=load();
    private static Map<String,float[]> load(){
        var stream=NatureMeshes.class.getResourceAsStream("/assets/elementalwands/nature/meshes.json");
        if(stream==null)throw new IllegalStateException("Missing Nature meshes");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return new Gson().fromJson(reader,new TypeToken<Map<String,float[]>>(){}.getType());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static void draw(String name,VertexConsumer out,MatrixStack.Entry entry,int light,
            float opacity,float reveal,Vec3d cameraLocal,boolean firstPerson){
        float[] data=MESHES.get(name);
        if(data==null)throw new IllegalArgumentException("Unknown Nature mesh "+name);
        for(int i=0;i<data.length;i+=30){
            if(reveal>=0 && Math.min(data[i+1],Math.min(data[i+11],data[i+21]))>reveal)continue;
            for(int corner=0;corner<4;corner++){
                int index=i+Math.min(corner,2)*10;
                float x=data[index],y=data[index+1],z=data[index+2];
                float nx=data[index+3],ny=data[index+4],nz=data[index+5];
                float shade=.82f+.18f*Math.max(0,ny);
                int r=Math.clamp(Math.round(data[index+6]*shade*255),0,255);
                int g=Math.clamp(Math.round(data[index+7]*shade*255),0,255);
                int b=Math.clamp(Math.round(data[index+8]*shade*255),0,255);
                float visible=opacity;
                if(cameraLocal!=null)visible*=SpellViewClearance.opacity(firstPerson,
                        cameraLocal.distanceTo(new Vec3d(x,y,z)),.08);
                int color=(Math.round(visible*255)<<24)|(r<<16)|(g<<8)|b;
                float u=Math.abs(ny)>.5?x:Math.abs(nx)>.5?z:x;
                float v=Math.abs(ny)>.5?z:y;
                out.vertex(entry,x,y,z).color(color).texture(u,v).overlay(OverlayTexture.DEFAULT_UV)
                        .light(data[index+9]==3?LightmapTextureManager.MAX_LIGHT_COORDINATE:light)
                        .normal(entry,nx,ny,nz);
            }
        }
    }
    private NatureMeshes(){}
}
