package com.anton.elementalwands.data;

/** Increasing level costs, slowed after Guardian playtesting; XP is independent of spendable Flux. */
public final class ElementLevels {
    private static final double[] TOTALS = {0, 240, 600, 1140, 1920, 3000};
    public static double clamp(double xp) { return Double.isFinite(xp) ? Math.clamp(xp, 0, TOTALS[TOTALS.length - 1]) : 0; }
    public static int level(double xp) { int level=1; while(level<6 && xp>=TOTALS[level]) level++; return level; }
    public static float multiplier(double xp) { return 1 + (level(xp)-1)*.1f; }
    public static double within(double xp) { return level(xp)==6 ? 0 : clamp(xp)-TOTALS[level(xp)-1]; }
    public static double required(double xp) { int l=level(xp); return l==6 ? 0 : TOTALS[l]-TOTALS[l-1]; }
    private ElementLevels() {}
}
