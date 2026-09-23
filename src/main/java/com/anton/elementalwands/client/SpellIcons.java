package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Native pixel compositions extend the approved elemental glyphs without replacing their artwork. */
public final class SpellIcons {
    public static void draw(DrawContext ctx, WandSpells.Spell spell, int x, int y, int size) {
        if (spell==null || spell.affinity()==WizardAffinity.NONE) return;
        ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(x,y);ctx.getMatrices().scale(size/32f,size/32f);
        if (spell.id().equals("gale_daggers")) {
            for(int i=0;i<3;i++) {
                int x0=6+i*10, tip=i==1?1:5;
                for(int y0=tip;y0<21;y0++) {
                    int half=Math.min(2,(y0-tip)/3);
                    ctx.fill(x0-half,y0,x0+half+1,y0+1,0xFFBCCBD0);
                    ctx.fill(x0,y0,x0+1,y0+1,0xFFF8FAF7);
                }
                ctx.fill(x0-3,21,x0+4,23,0xFFD8E2E4);
                ctx.fill(x0-1,23,x0+2,29,0xFF98AAB4);ctx.fill(x0-1,29,x0+2,31,0xFFE8EEED);
            }
        } else if (spell.id().equals("faultline")) {
            for (int i=0;i<3;i++) {
                int base=3+i*10, tip=i==1?3:10;
                for(int row=tip;row<27;row++) {
                    int half=Math.min(5,1+(row-tip)/4),mid=base+4;
                    ctx.fill(mid-half,row,mid+half+1,row+1,0xFF525C59);
                    ctx.fill(mid-half+1,row,mid+1,row+1,0xFFA4B5AC);
                    if(row%5<2)ctx.fill(mid,row,mid+half,row+1,0xFF788B82);
                }
                ctx.fill(base,27,base+8,29,0xFF68736B);
                ctx.fill(base+1,26,base+4,27,0xFFCFD4B8);
            }
        } else if (spell.id().equals("stone_charge")) {
            // A faceted shoulder of stone, with three separated motion streaks.
            ctx.fill(1,8,10,10,0xFF9CAFA5);ctx.fill(0,15,8,18,0xFFCED6C6);ctx.fill(2,24,12,26,0xFF788B82);
            ctx.fill(13,5,22,7,0xFF899A90);ctx.fill(10,7,25,11,0xFFB5C3B7);
            ctx.fill(8,11,28,23,0xFF68786F);ctx.fill(11,23,25,27,0xFF4C5954);
            ctx.fill(15,27,22,29,0xFF788B82);ctx.fill(12,10,19,17,0xFFCED6C6);
            ctx.fill(10,17,17,22,0xFF9CAD9E);ctx.fill(20,10,25,14,0xFF899A90);
            ctx.fill(18,18,25,23,0xFF899A90);ctx.fill(26,13,29,21,0xFFB5C3B7);
            ctx.fill(29,15,31,19,0xFFCED6C6);ctx.fill(15,8,17,10,0xFFE0E3CF);
        } else if (spell.id().equals("springbloom")) {
            for(int px=3;px<29;px+=5){ctx.fill(px,23,px+5,28,0xFF30B821);ctx.fill(px+1,21,px+4,26,0xFF78E330);}
            ctx.fill(3,20,29,24,0xFF9945DB);ctx.fill(5,18,27,23,0xFFF0338F);
            for(int row=0;row<10;row++){int half=6+row/2;ctx.fill(16-half,9+row,16+half,10+row,row<4?0xFFFFD64C:0xFFFFCC26);}
            ctx.fill(11,9,18,11,0xFFFFED82);
        } else if (spell.id().equals("thorn_lash")) {
            // Three twined stems beneath an open, toothed flytrap.
            for(int row=17;row<31;row++) for(int strand=0;strand<3;strand++) {
                int px=13+strand*2+(int)Math.round(Math.sin(row*.6+strand*2));
                ctx.fill(px,row,px+2,row+1,strand==1?0xFF78E330:0xFF2E851A);
            }
            for(int side:new int[]{-1,1}) for(int row=0;row<13;row++) {
                int width=3+(int)(5*Math.sin(row/12.0*Math.PI));
                int center=16+side*(7-row/3);
                ctx.fill(center-width/2,4+row,center+width/2+1,5+row,0xFF30B821);
                ctx.fill(center-width/2+1,5+row,center+width/2,6+row,0xFF78E330);
                int inner=center-side*(width/2-1);
                ctx.fill(inner-1,5+row,inner+2,6+row,row<3?0xFF9945DB:0xFFF0338F);
                if(row%3==0)ctx.fill(inner+(side<0?1:-3),4+row,inner+(side<0?4:0),5+row,0xFFFFCC26);
            }
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
