package com.anton.elementalwands.client.overlay;

import com.anton.elementalwands.client.NecromancerDrainEffects;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/** Smooth, drifting soul mist; the central view remains transparent. */
public final class NecromancerDrainOverlay implements HudRenderCallback {
    private static final Identifier SHADOW=texture("shadow"),FOG=texture("fog"),WISP=texture("wisp");
    private static Identifier texture(String name) { return Identifier.of("elementalwands","textures/gui/life_drain/"+name+".png"); }
    private static double hash(int n) { double x=Math.sin(n*127.1+311.7)*43758.5453123;return x-Math.floor(x); }
    @Override public void onHudRender(DrawContext context,RenderTickCounter counter) {
        var veil=NecromancerDrainEffects.localVeil(counter.getTickProgress(false));
        if(veil==null)return;
        double t=veil.time(),fade=veil.fade();
        double strength=(t<.7?.22+.50*Math.clamp(t/.7,0,1):.72)*(1-fade);
        if(strength<=0)return;
        double phase=t>=.7?(t-.7)%.5:.25;
        double pulse=t>=.7&&fade==0?Math.exp(-Math.pow(Math.min(phase,.5-phase)/.075,2)):0;
        int w=context.getScaledWindowWidth(),h=context.getScaledWindowHeight();
        draw(context,SHADOW,0,0,w,h,strength);
        for(int i=0;i<22;i++) {
            double a=i*Math.PI*2/22+Math.sin(t*.31+i*2.7)*.07;
            double reach=.96+Math.sin(t*.6+i*1.9)*.07+fade*.28;
            double size=.16+hash(i*37)*.12;
            double alpha=strength*(.32+.14*pulse)*(.6+.4*Math.pow(Math.sin(i*3+t*.7),2));
            var m=context.getMatrices();m.pushMatrix();
            m.translate((float)(w/2.0+Math.cos(a)*w*.51*reach),(float)(h/2.0+Math.sin(a)*h*.53*reach));
            m.rotate((float)(a+.7*Math.sin(i)));
            draw(context,FOG,(int)(-w*size),(int)(-h*size*.85),(int)(2*w*size),(int)(2*h*size*.85),alpha);
            m.popMatrix();
        }
        for(int i=0;i<12;i++) {
            double a=i*Math.PI*2/12+.15*Math.sin(i*7),sway=Math.sin(t*.8+i*2)*.10;
            double alpha=strength*(.15+.09*pulse)*(.5+.5*Math.pow(Math.sin(t*.9+i),2));
            var m=context.getMatrices();m.pushMatrix();
            m.translate((float)(w/2.0+Math.cos(a)*w*(.48+fade*.14)),(float)(h/2.0+Math.sin(a)*h*(.50+fade*.14)));
            m.rotate((float)(a+sway));
            draw(context,WISP,(int)(-w*.14),(int)(-h*.22),(int)(w*.28),(int)(h*.44),alpha);
            m.popMatrix();
        }
    }
    private static void draw(DrawContext context,Identifier texture,int x,int y,int w,int h,double alpha) {
        int color=((int)Math.clamp(alpha*255,0,255)<<24)|0xffffff;
        context.drawTexture(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,w,h,512,512,512,512,color);
    }
}
