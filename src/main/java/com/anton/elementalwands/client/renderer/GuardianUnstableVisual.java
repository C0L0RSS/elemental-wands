package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;

/** Bounded, erratic cyan discharges around the shell, with a brighter transition eruption. */
final class GuardianUnstableVisual {
    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (!state.unstable || state.beamTime >= 0) return;
        float time = state.magicTime;
        float flare = state.phaseTime >= 0 ? (float)Math.sin(Math.PI * Math.clamp(state.phaseTime / 64,0,1)) : .18f;
        float fade = state.guardTime >= 0 ? .35f : 1;
        queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out) -> {
            for (int strand=0; strand<10; strand++) {
                // Change shape in discrete bursts rather than a smooth decorative orbit.
                double seed = Math.floor(time/3)*1.73 + strand*9.1;
                if (Math.sin(seed) < -.35 && flare < .5) continue;
                double angle = strand*Math.PI/5 + Math.sin(seed)*.45;
                double ca=Math.cos(angle), sa=Math.sin(angle);
                for (int segment=0;segment<5;segment++) {
                    double a=segment/5.0,b=(segment+1)/5.0;
                    double ra=1.05+a*(.55+flare*1.6),rb=1.05+b*(.55+flare*1.6);
                    float ax=(float)(ca*ra+Math.sin(seed+segment*7)*.16),az=(float)(sa*ra);
                    float bx=(float)(ca*rb+Math.sin(seed+(segment+1)*7)*.16),bz=(float)(sa*rb);
                    float ay=(float)(2.7+Math.sin(strand*3)*.7+a*(.8+flare));
                    float by=(float)(2.7+Math.sin(strand*3)*.7+b*(.8+flare));
                    float width=.025f+.035f*flare;
                    int alpha=(int)((170+70*flare)*(1-a*.6)*fade);
                    // Two crossed ribbons remain visible from every viewing direction.
                    for (int plane=0;plane<2;plane++) {
                        float dx=plane==0?width:0,dz=plane==1?width:0;
                        out.vertex(entry,ax-dx,ay,az-dz).color(60,225,255,alpha);
                        out.vertex(entry,bx-dx,by,bz-dz).color(165,255,255,alpha);
                        out.vertex(entry,bx+dx,by,bz+dz).color(165,255,255,alpha);
                        out.vertex(entry,ax+dx,ay,az+dz).color(60,225,255,alpha);
                    }
                }
            }
        });
    }
}
