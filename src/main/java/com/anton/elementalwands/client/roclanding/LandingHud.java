package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import net.minecraft.client.gui.DrawContext;

/** The animatic's 2D overlays drawn with a DrawContext: a tint, a darkened edge, speed lines. */
public final class LandingHud {
    private LandingHud() {}

    public static int argb(double r, double g, double b, double a) {
        return (int) Math.round(clamp(a, 0, 1) * 255) << 24 | (int) Math.round(clamp(r, 0, 1) * 255) << 16
                | (int) Math.round(clamp(g, 0, 1) * 255) << 8 | (int) Math.round(clamp(b, 0, 1) * 255);
    }

    /** The whole screen tinted rgb at alpha a. */
    public static void tint(DrawContext context, int w, int h, double r, double g, double b, double a) {
        if (a > .003) context.fill(0, 0, w, h, argb(r, g, b, a));
    }

    /**
     * The screen's edges darkened toward rgb at alpha a, clear in the middle: the animatic's radial
     * vignette, made of a gradient down from each edge reaching depth (a fraction of the height) in.
     */
    public static void vignette(DrawContext context, int w, int h, double r, double g, double b, double a, double depth) {
        if (a <= .003) return;
        int edge = argb(r, g, b, a), clear = argb(r, g, b, 0), d = (int) Math.round(h * depth);
        context.fillGradient(0, 0, w, d, edge, clear);
        context.fillGradient(0, h - d, w, h, clear, edge);
        var m = context.getMatrices();
        // The sides: the same gradient turned a quarter.
        m.pushMatrix();
        m.translate(0, h);
        m.rotate((float) (-Math.PI / 2));
        context.fillGradient(0, 0, h, d, edge, clear);
        m.popMatrix();
        m.pushMatrix();
        m.translate(w, 0);
        m.rotate((float) (Math.PI / 2));
        context.fillGradient(0, 0, h, d, edge, clear);
        m.popMatrix();
    }

    /**
     * Speed lines streaming out from the middle (the animatic's): count of them at strength k,
     * moving with t (scene ticks), white at alpha a.
     */
    public static void speedLines(DrawContext context, int w, int h, double t, double k, double a, int count) {
        if (k <= .01 || a <= .003) return;
        double big = Math.hypot(w, h) / 2;
        float thick = Math.max(1, h * .0022f);
        int colour = argb(1, 1, 1, a);
        var m = context.getMatrices();
        for (int i = 0; i < count; i++) {
            double angle = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .08 * (.6 + hash(i * 3.1))) % 1;
            double r0 = big * (.4 + .6 * ph), len = big * (.08 + .18 * hash(i * 4.3)) * k;
            m.pushMatrix();
            m.translate((float) (w / 2.0 + Math.cos(angle) * r0), (float) (h / 2.0 + Math.sin(angle) * r0));
            m.rotate((float) angle);
            context.fill(0, 0, (int) Math.ceil(len), (int) Math.ceil(thick), colour);
            m.popMatrix();
        }
    }
}
