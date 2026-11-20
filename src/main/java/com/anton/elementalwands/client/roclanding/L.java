package com.anton.elementalwands.client.roclanding;

/**
 * The browser animatic's maths (art/storm_roc/intro/animatic.js), so a landing's effects port line
 * for line: vectors are double[3], matrices column-major double[16] as the animatic's M builds them,
 * angles in radians. Pure functions; nothing here keeps state.
 */
public final class L {
    private L() {}

    public static double clamp(double v, double a, double b) { return Math.min(b, Math.max(a, v)); }
    public static double smooth(double s) { s = clamp(s, 0, 1); return s * s * (3 - 2 * s); }
    public static double ramp(double t, double a, double b) { return smooth((t - a) / (b - a)); }
    public static double mix(double a, double b, double s) { return a + (b - a) * s; }
    /** The animatic's hash: a repeatable fraction for n. */
    public static double hash(double n) { double s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }
    /** Overshoots past 1 and settles back, for things springing up. */
    public static double backEase(double s) { return 1 + 2.70158 * Math.pow(s - 1, 3) + 1.70158 * Math.pow(s - 1, 2); }
    public static double rad(double degrees) { return Math.toRadians(degrees); }

    public static double[] v(double x, double y, double z) { return new double[]{x, y, z}; }
    public static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    public static double[] add(double[] a, double x, double y, double z) { return new double[]{a[0] + x, a[1] + y, a[2] + z}; }
    public static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    public static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    public static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    public static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    public static double dist(double[] a, double[] b) { return len(sub(a, b)); }
    public static double[] norm(double[] a) { double l = len(a); if (l == 0) l = 1; return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    public static double[] lerp(double[] a, double[] b, double s) {
        return new double[]{a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s};
    }
    public static double[] copy(double[] a) { return new double[]{a[0], a[1], a[2]}; }

    // ------------------------------------------------------------------ matrices (column-major, as M)

    public static double[] id() { return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
    public static double[] mul(double[] a, double[] b) {
        double[] o = new double[16];
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) {
            double s = 0;
            for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
            o[c * 4 + r] = s;
        }
        return o;
    }
    /** a × b × c × ...: the animatic's [..].reduce(M.mul). */
    public static double[] mul(double[]... ms) {
        double[] o = ms[0];
        for (int i = 1; i < ms.length; i++) o = mul(o, ms[i]);
        return o;
    }
    public static double[] t(double x, double y, double z) { double[] m = id(); m[12] = x; m[13] = y; m[14] = z; return m; }
    public static double[] t(double[] p) { return t(p[0], p[1], p[2]); }
    public static double[] s(double x, double y, double z) { double[] m = id(); m[0] = x; m[5] = y; m[10] = z; return m; }
    public static double[] s(double k) { return s(k, k, k); }
    public static double[] rx(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1}; }
    public static double[] ry(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1}; }
    public static double[] rz(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
    /** Translation p then rotation q (w, x, y, z), as M.quat. */
    public static double[] quat(double[] p, double w, double x, double y, double z) {
        return new double[]{1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0, 2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0, p[0], p[1], p[2], 1};
    }
    public static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12], m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13], m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14]};
    }
    public static double[] dir(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[4] * v[1] + m[8] * v[2], m[1] * v[0] + m[5] * v[1] + m[9] * v[2], m[2] * v[0] + m[6] * v[1] + m[10] * v[2]};
    }
    /** A frame at a whose +Z runs toward b. */
    public static double[] along(double[] a, double[] b) {
        double[] d = norm(sub(b, a)), x = cross(v(0, 1, 0), d);
        if (len(x) < .05) x = cross(v(1, 0, 0), d);
        x = norm(x);
        double[] y = cross(d, x);
        return new double[]{x[0], x[1], x[2], 0, y[0], y[1], y[2], 0, d[0], d[1], d[2], 0, a[0], a[1], a[2], 1};
    }
}
