package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Native pixel compositions extend the approved Fire glyphs without replacing their artwork. */
public final class SpellIcons {
    public static void draw(DrawContext ctx, WandSpells.Spell spell, int x, int y, int size) {
        if (spell==null || spell.affinity()==WizardAffinity.NONE) return;
        ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(x,y);ctx.getMatrices().scale(size/32f,size/32f);
        if (spell.id().equals("thorn_lash")) {
            for (int i=0;i<23;i++) {
                int px=4+i, py=24-(int)(17*Math.sin(i/22.0*Math.PI*.8));
                ctx.fill(px,py,px+3,py+4,0xFF254D28);
                ctx.fill(px,py,px+2,py+2,0xFF8DBD5C);
                if(i%5==0) {ctx.fill(px,py-3,px+2,py,0xFFBFA97A);ctx.fill(px+1,py-4,px+2,py-2,0xFFE2D7A4);}
            }
            ctx.fill(3,25,8,29,0xFF75603E);ctx.fill(4,25,6,28,0xFFB69D68);
        } else if (spell.id().equals("flamethrower")) {
            ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(31,1);ctx.getMatrices().rotate((float)(Math.PI/2));
            glyph(ctx,spell,0,0,29);ctx.getMatrices().popMatrix();
            ctx.fill(1,13,10,21,0xFF634533);ctx.fill(1,14,8,19,0xFFD6B577);ctx.fill(8,12,11,22,0xFFA76B38);
            ctx.fill(2,14,7,16,0xFFF5D795);
        } else if (spell.id().equals("fire_hop")) {
            glyph(ctx,spell,0,11,24);
            ctx.fill(19,3,24,20,0xFFBC582C);ctx.fill(17,5,26,8,0xFFE9AB4C);
            ctx.fill(15,8,28,11,0xFFFFD980);ctx.fill(20,4,23,18,0xFFFFE7A0);
            ctx.fill(10,18,15,26,0xFF66513A);ctx.fill(10,24,22,28,0xFF876747);ctx.fill(11,24,20,26,0xFFCAA174);
        } else if(spell.id().equals("flashover")) {
            glyph(ctx,spell,5,1,24);
            for(int dx:new int[]{4,13,22}) {
                ctx.fill(dx,21,dx+7,29,0xFF47392F);ctx.fill(dx+1,22,dx+6,27,0xFFAD4B24);
                ctx.fill(dx+2,21,dx+5,25,0xFFFFBC4B);ctx.fill(dx+3,20,dx+4,23,0xFFFFE8AA);
            }
        } else glyph(ctx,spell,0,0,32);
        ctx.getMatrices().popMatrix();
    }
    private static void glyph(DrawContext ctx, WandSpells.Spell spell, int x,int y,int size) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("elementalwands",spell.iconPath()),x,y,0,0,size,size,32,32,32,32);
    }
    private SpellIcons() {}
}
