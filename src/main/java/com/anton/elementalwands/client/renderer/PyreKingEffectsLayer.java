package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.PyreKingIntroClient;
import com.anton.elementalwands.client.model.PyreKingModel;
import com.anton.elementalwands.entity.pyreking.PyreKingIntroTrack;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The Pyre King's fire and chains, drawn in the world each frame from his posed bones. This is
 * the game's copy of art/pyre_king/workshop/effects.js, which the developer approved in the
 * workshop, and it reads the same numbers (pyre_king_effects.json, written by build_art.py):
 * <ul>
 * <li>blade fire: upright flames along the blade that lie back along the path it just swung
 * and leave a fading wake behind a fast blow;</li>
 * <li>the sword's chain, from his wrist shackle to the pommel while the sword is out of his hand;</li>
 * <li>Unbound's six loose chains, from his chest to each marker bone the clip has moved: burning,
 * every third link hot, each ending in a barbed hook, bowed by their own swing.</li>
 * </ul>
 * The workshop re-poses the clip to learn where the blade was a moment ago. Here the layer
 * remembers where it really was, so walking and turning lean the fire too.
 */
final class PyreKingEffectsLayer extends GeoRenderLayer<PyreKingEntity, Void, PyreKingRenderState> {
    private static final Identifier FIRE = Identifier.of("elementalwands", "textures/entity/pyre_king_bladefire.png");
    private static final RenderLayer CORE = RenderLayer.getEntityCutoutNoCull(FIRE);
    private static final RenderLayer TINT = RenderLayer.getEntityTranslucentEmissive(FIRE);
    private static final RenderLayer GLOW = RenderLayer.getEyes(FIRE);
    private static final RenderLayer CHAIN = RenderLayer.getEntityCutoutNoCull(PyreKingModel.TEXTURE);
    private static final int BRIGHT = 0xF000F0;
    /** How long the layer remembers: the chains' bow looks back a second. */
    private static final double MEMORY = 1.15, GAP = .3;
    private static final double[] UP = {0, 1, 0};

    private static final JsonObject DATA = load();
    private static final JsonObject BF = DATA.getAsJsonObject("blade_fire"), SW = BF.getAsJsonObject("swing"),
            TG = SW.getAsJsonObject("tongue"), ST = SW.getAsJsonObject("stream"), LOOSE = DATA.getAsJsonObject("loose_chains"),
            WRIST = DATA.getAsJsonObject("chain");
    private static final double[][] SOCKETS = grid(BF.getAsJsonArray("sockets")), HALF = grid(BF.getAsJsonArray("socket_half_extents"));
    private static final double[][] PEAKS = grid(BF.getAsJsonArray("peak_sockets"));
    private static final double[] TONGUE_FRAMES = row(BF.getAsJsonArray("tongue_frames")), LICK_FRAMES = row(BF.getAsJsonArray("lick_frames"));
    private static final int FRAMES = BF.getAsJsonArray("atlas_grid").get(0).getAsInt();
    private static final double DU = 1.0 / FRAMES, EDGE = num(BF, "root_edge_fraction");
    private static final int SAMPLES = SW.get("history_samples").getAsInt(), SUBSTEPS = SW.get("path_substeps").getAsInt(),
            SPEED_SAMPLES = SW.get("speed_samples").getAsInt();
    private static final double STEP = num(SW, "history_step_seconds"), SPAN = SAMPLES * STEP, TELEPORT = num(SW, "teleport_speed_blocks_per_second");
    private static final double[] LEAN = row(SW.getAsJsonArray("lean_speed_blocks_per_second")),
            STREAM = row(SW.getAsJsonArray("stream_speed_blocks_per_second")), WAKE = row(SW.getAsJsonArray("wake_speed_blocks_per_second"));
    private static final double[] IRON = row(WRIST.getAsJsonArray("iron_uv")), HOT = row(WRIST.getAsJsonArray("hot_uv"));
    private static final String[] MARKERS = names(LOOSE.getAsJsonArray("markers"));
    private static final double LOOSE_MIN = num(LOOSE, "draw_beyond_blocks"), HOOK = num(LOOSE, "hook_blocks"),
            FLAME_SPACING = num(LOOSE, "flame_spacing_blocks"), BOW_SECONDS = num(LOOSE, "bow_seconds"), BOW_SHARE = num(LOOSE, "bow_share"),
            WRIST_MIN = num(WRIST, "draw_beyond_blocks"), WRIST_BOW_SHARE = num(WRIST, "bow_share");
    private static final double BOW_STEP = .05;
    private static final int BOW_SAMPLES = 20;
    private static final double[] BOW_WEIGHTS = bowWeights(num(LOOSE, "bow_period_seconds"), num(LOOSE, "bow_damping"));
    private static final double TANGENT_LIMIT = 2;

    private final Map<Integer, Memory> memories = new HashMap<>();
    /** For fixtures: the pommel and the blade's last flame root (its tip) as last drawn, in the world. */
    public static volatile double[][] drawnBlade;

    PyreKingEffectsLayer(GeoRenderer<PyreKingEntity, Void, PyreKingRenderState> renderer) { super(renderer); }

    // ---------------------------------------------------------------- what the layer remembers
    /** Where the blade's flame roots, the pommel and the chains' ends were at one moment (world space). */
    private record Sample(double time, double[][] roots, double[] pommel, double[][] chest, double[][] ends) {}

    private static final class Memory {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();   // newest first
        double seen;
    }

    /** A remembered point `age` seconds ago; before the memory begins, where it was first seen. */
    private static double[] past(Memory memory, double now, double age, java.util.function.Function<Sample, double[]> point) {
        double when = now - age;
        Sample newer = null;
        for (Sample s : memory.samples) {
            if (s.time <= when) {
                if (newer == null) return point.apply(s);
                double f = (when - s.time) / Math.max(1e-9, newer.time - s.time);
                return mix(point.apply(s), point.apply(newer), f);
            }
            newer = s;
        }
        return point.apply(newer);
    }

    // ---------------------------------------------------------------- the frame
    @Override
    public void submitRenderTask(PyreKingRenderState state, MatrixStack matrices, BakedGeoModel model, OrderedRenderCommandQueue queue,
            CameraRenderState camera, int light, int overlay, int color, boolean rendered) {
        if (!rendered) return;
        // GeckoLib 5 poses the mesh in a deferred callback. Pose this king now, before reading his bones.
        getGeoModel().handleAnimations(renderer.createAnimationState(state));
        Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
        double[] eye = {camera.pos.x, camera.pos.y, camera.pos.z};
        Matrix4f swordM = bone(model, BF.get("bone").getAsString()), wristM = bone(model, WRIST.get("wrist_bone").getAsString());
        if (swordM == null || wristM == null) return;
        GeoBone sword = model.getBone(BF.get("bone").getAsString()).orElseThrow();
        Matrix4f blade = new Matrix4f(base).mul(swordM);
        double[] pivot = pivot(sword);
        // The blade's sockets, then each flame's root: toward the blade's uppermost edge there.
        double[] ux = norm(dir(blade, 1, 0, 0)), uz = norm(dir(blade, 0, 0, 1));
        double[][] roots = new double[SOCKETS.length][];
        for (int i = 0; i < SOCKETS.length; i++) {
            double[] p = world(blade, eye, pivot[0] - SOCKETS[i][0] / 16, pivot[1] + SOCKETS[i][1] / 16, pivot[2] + SOCKETS[i][2] / 16);
            roots[i] = add(p, add(mul(ux, EDGE * HALF[i][0] / 16 * Math.tanh(ux[1] * 8)), mul(uz, HALF[i][1] / 16 * Math.tanh(uz[1] * 8))));
        }
        double[] pommel = world(blade, eye, pivot[0], pivot[1], pivot[2]);
        drawnBlade = new double[][]{pommel, roots[roots.length - 1]};
        GeoBone shackle = model.getBone(WRIST.get("wrist_bone").getAsString()).orElseThrow();
        double[] sp = pivot(shackle), wrist = world(new Matrix4f(base).mul(wristM), eye, sp[0], sp[1], sp[2]);
        double[][] chest = new double[MARKERS.length][], ends = new double[MARKERS.length][];
        for (int i = 0; i < MARKERS.length; i++) {
            GeoBone marker = model.getBone(MARKERS[i]).orElse(null);
            Matrix4f own = bone(model, MARKERS[i]), parent = marker == null || marker.getParent() == null ? null : bone(model, marker.getParent().getName());
            if (own == null || parent == null) { chest[i] = ends[i] = pommel; continue; }
            double[] mp = pivot(marker);
            chest[i] = world(new Matrix4f(base).mul(parent), eye, mp[0], mp[1], mp[2]);
            ends[i] = world(new Matrix4f(base).mul(own), eye, mp[0], mp[1], mp[2]);
        }

        double now = state.age / 20.0;
        Memory memory = memories.computeIfAbsent(state.id, id -> new Memory());
        Sample newest = memory.samples.peekFirst();
        if (newest != null && (now < newest.time || now - newest.time > GAP)) memory.samples.clear();   // rewound, or unseen for a while
        if (memory.samples.isEmpty() || now > memory.samples.peekFirst().time)
            memory.samples.addFirst(new Sample(now, roots, pommel, chest, ends));
        while (memory.samples.size() > 2 && now - memory.samples.peekLast().time > MEMORY) memory.samples.removeLast();
        memory.seen = now;
        memories.values().removeIf(m -> m != memory && Math.abs(now - m.seen) > 30);   // kings no longer drawn

        Batch core = new Batch(), tint = new Batch(), glow = new Batch(), iron = new Batch(), hot = new Batch();
        // In his intro the blade is cold until his fist lights it, and the fire runs up it from the hilt.
        int lit = (int) Math.round(Math.max(0, Math.min(1, state.blade)) * roots.length);
        if (lit > 0) fire(memory, now, roots, lit, core, tint, glow);
        // Nor does his wrist's chain run to a sword that hangs from the roof or stands in the floor.
        boolean tether = state.intro < 0 || state.intro >= PyreKingIntroTrack.get().beat("grasp");
        chains(memory, now, tether, wrist, pommel, chest, ends, iron, hot, core);
        if (state.intro >= 0) web(state.intro, now, iron, hot, core);
        float ex = (float) eye[0], ey = (float) eye[1], ez = (float) eye[2];
        if (!iron.isEmpty()) queue.submitCustom(matrices, CHAIN, (entry, out) -> iron.emit(out, ex, ey, ez, light, false));
        if (!hot.isEmpty()) queue.submitCustom(matrices, CHAIN, (entry, out) -> hot.emit(out, ex, ey, ez, BRIGHT, false));
        if (!core.isEmpty()) queue.submitCustom(matrices, CORE, (entry, out) -> core.emit(out, ex, ey, ez, BRIGHT, false));
        if (!tint.isEmpty()) queue.submitCustom(matrices, TINT, (entry, out) -> tint.emit(out, ex, ey, ez, BRIGHT, false));
        if (!glow.isEmpty()) queue.submitCustom(matrices, GLOW, (entry, out) -> glow.emit(out, ex, ey, ez, BRIGHT, true));
    }

    /** A bone's model-space matrix, parents applied before children as the mesh does. */
    private static Matrix4f bone(BakedGeoModel model, String name) {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null) return null;
        var chain = new ArrayList<GeoBone>();
        for (GeoBone b = bone; b != null; b = b.getParent()) chain.add(b);
        Collections.reverse(chain);
        MatrixStack stack = new MatrixStack();
        for (GeoBone b : chain) RenderUtil.prepMatrixForBone(stack, b);
        return new Matrix4f(stack.peek().getPositionMatrix());
    }

    private static double[] pivot(GeoBone bone) { return new double[]{bone.getPivotX() / 16, bone.getPivotY() / 16, bone.getPivotZ() / 16}; }

    private static double[] world(Matrix4f m, double[] eye, double x, double y, double z) {
        Vector3f p = m.transformPosition(new Vector3f((float) x, (float) y, (float) z));
        return new double[]{p.x + eye[0], p.y + eye[1], p.z + eye[2]};
    }

    private static double[] dir(Matrix4f m, float x, float y, float z) {
        Vector3f d = m.transformDirection(new Vector3f(x, y, z));
        return new double[]{d.x, d.y, d.z};
    }

    // ---------------------------------------------------------------- blade fire
    /** A root's recent path: pts[m] is where it was m*h seconds ago, len[m] how far it has come since. */
    private record Route(double[][] track, double[][] slopes, double[][] pts, double[] len, double h) {
        double[] at(double age) { return along(track, slopes, age, STEP); }
    }

    private static Route route(double[][] track) {
        double[][] m = slopes(track);
        int n = (track.length - 1) * SUBSTEPS;
        double h = STEP / SUBSTEPS;
        double[][] pts = new double[n + 1][];
        double[] len = new double[n + 1];
        for (int i = 0; i <= n; i++) pts[i] = along(track, m, i * h, STEP);
        for (int i = 1; i <= n; i++) len[i] = len[i - 1] + dist(pts[i], pts[i - 1]);
        return new Route(track, m, pts, len, h);
    }

    /** The route of a point w of the way from route a's point to route b's. */
    private static Route between(Route a, Route b, double w) {
        double[][] track = new double[a.track.length][];
        for (int j = 0; j < track.length; j++) track[j] = mix(a.track[j], b.track[j], w);
        double[][] slopes = new double[track.length][];
        for (int j = 0; j < track.length; j++) slopes[j] = mix(a.slopes[j], b.slopes[j], w);
        double[][] pts = new double[a.pts.length][];
        double[] len = new double[pts.length];
        for (int m = 0; m < pts.length; m++) pts[m] = mix(a.pts[m], b.pts[m], w);
        for (int m = 1; m < pts.length; m++) len[m] = len[m - 1] + dist(pts[m], pts[m - 1]);
        return new Route(track, slopes, pts, len, a.h);
    }

    private static double[][] slopes(double[][] track) {
        int last = track.length - 1;
        double[][] out = new double[track.length][];
        for (int j = 0; j <= last; j++) {
            double[] l = j > 0 ? sub(track[j], track[j - 1]) : null, r = j < last ? sub(track[j + 1], track[j]) : null;
            if (l == null) { out[j] = r == null ? new double[3] : r; continue; }
            if (r == null) { out[j] = l; continue; }
            double[] m = mul(add(l, r), .5);
            double lm = length(m), cap = TANGENT_LIMIT * Math.min(length(l), length(r));
            out[j] = lm > cap ? mul(m, cap / lm) : m;
        }
        return out;
    }

    private static double[] along(double[][] track, double[][] m, double age, double step) {
        int last = track.length - 1;
        if (last < 1) return track[0];
        double u = Math.max(0, Math.min(last, age / step));
        int j = Math.min(last - 1, (int) Math.floor(u));
        double f = u - j, f2 = f * f, f3 = f2 * f;
        double h00 = 2 * f3 - 3 * f2 + 1, h10 = f3 - 2 * f2 + f, h01 = 3 * f2 - 2 * f3, h11 = f3 - f2;
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) out[k] = h00 * track[j][k] + h10 * m[j][k] + h01 * track[j + 1][k] + h11 * m[j + 1][k];
        return out;
    }

    private static double lengthAt(Route r, double age) {
        int n = r.len.length - 1;
        if (n < 1) return 0;
        double u = Math.max(0, Math.min(n, age / r.h));
        int m = Math.min(n - 1, (int) Math.floor(u));
        return r.len[m] + (r.len[m + 1] - r.len[m]) * (u - m);
    }

    private static double ageAt(Route r, double d) {
        double[] len = r.len;
        int n = len.length - 1;
        if (d <= 0 || n < 1) return 0;
        if (d >= len[n]) return n * r.h;
        int m = 0;
        while (len[m + 1] < d) m++;
        double s = len[m + 1] - len[m];
        return (m + (s > 1e-12 ? (d - len[m]) / s : 0)) * r.h;
    }

    private static double ramp(double[] range, double v) {
        return v > range[0] ? smooth(Math.log(v / range[0]) / Math.log(range[1] / range[0])) : 0;
    }

    private record Motion(double stream) {}

    private static Motion motionOf(Route r) {
        int n = SPEED_SAMPLES;
        double h = SPAN / n, stream = 0, total = 0;
        for (int k = 1; k <= n; k++) {
            double v = (lengthAt(r, k * h) - lengthAt(r, (k - 1) * h)) / h, w = n + 1 - k;
            stream += w * ramp(STREAM, v);
            total += w;
        }
        return new Motion(stream / total);
    }

    private static void fire(Memory memory, double now, double[][] roots, int lit, Batch core, Batch tint, Batch glow) {
        int n = roots.length;
        // Where every root was at each sampled age, one extra sample past the window to catch a jump.
        double[][][] history = new double[SAMPLES + 2][][];
        history[0] = roots;
        for (int j = 1; j <= SAMPLES + 1; j++) {
            double age = j * STEP;
            history[j] = new double[n][];
            for (int i = 0; i < n; i++) { int root = i; history[j][i] = past(memory, now, age, s -> s.roots[root]); }
        }
        // A slice in which a root outruns teleport_speed is a jump, not a swing: the fire forgets
        // everything before it, so it never traces a teleport.
        double[] slice = new double[SAMPLES + 1];
        for (int j = 0; j <= SAMPLES; j++) for (int i = 0; i < n; i++) slice[j] = Math.max(slice[j], dist(history[j + 1][i], history[j][i]) / STEP);
        int cut = -1;
        for (int j = 0; j <= SAMPLES && cut < 0; j++) if (slice[j] > TELEPORT) cut = j;
        if (cut >= 0) {
            cut = Math.max(0, cut - 1);
            while (cut > 0 && slice[cut - 1] > TELEPORT / 2) cut--;
            for (int k = cut + 1; k <= SAMPLES; k++) history[k] = history[cut];
        }
        double[] bladeAxis = norm(sub(roots[n - 1], roots[0]));
        Route[] plain = new Route[n], licks = new Route[n];
        for (int i = 0; i < n; i++) {
            double[][] track = new double[SAMPLES + 1][];
            for (int j = 0; j <= SAMPLES; j++) track[j] = history[j][i];
            plain[i] = route(track);
        }
        // Licks are nudged 0..45% of the way to the next root so the row has no rhythm.
        for (int i = 0; i < n; i++) licks[i] = between(plain[i], plain[Math.min(i + 1, n - 1)], .45 * hash(i));
        Motion[] plainMotion = new Motion[n], lickMotion = new Motion[n];
        for (int i = 0; i < n; i++) { plainMotion[i] = motionOf(plain[i]); lickMotion[i] = motionOf(licks[i]); }
        for (int i = 0; i < Math.min(n, lit); i++) {
            tongue(core, now, i, 0, licks[i], lickMotion[i], bladeAxis);
            for (double[] peak : PEAKS) if ((int) peak[0] == i) tongue(core, now, i, peak[1], plain[i], plainMotion[i], bladeAxis);
        }
        if (lit >= n) wake(tint, glow, now, plain, plainMotion);
    }

    private static double noise(double t, double i) { return .5 + .27 * Math.sin(t * 7.3 + i * 2.71) + .23 * Math.sin(t * 11.9 + i * 7.13); }

    private static double hash(double i) { double x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); }

    private static double frame(double[] pool, double t, int offset) { return pool[Math.floorMod((int) Math.floor(t * 8) + offset, 4)] / FRAMES; }

    /** One flame: a low lick (tall 0) or a peak of that height, lying back along its root's path. */
    private static void tongue(Batch out, double t, int i, double tall, Route r, Motion m, double[] bladeAxis) {
        boolean peak = tall > 0;
        double n = noise(t, i + (peak ? 31 : 0));
        double base = .28 + .2 * hash(i + 17) - .04 * Math.abs(i - 8) / 8.0;
        double height = (peak ? tall : base) * (.84 + .28 * n), thin = 1 - num(TG, "thinning") * m.stream;
        double width = (peak ? .30 + .16 * hash(i + 9) : .30 + .12 * hash(i + 5)) * (.88 + .18 * noise(t + .3, i)) * thin, depth = .24 * thin;
        double u = frame(peak || i < 2 ? TONGUE_FRAMES : LICK_FRAMES, t, i + (peak ? 2 : 0));
        double life = Math.min(SPAN, num(TG, "seconds") * (.75 + .5 * hash(i + 23))), travelled = lengthAt(r, life);
        double lean = ramp(LEAN, travelled / life);
        double limit = height * num(TG, "stretch") + num(TG, "reach_blocks"), reach = limit * Math.tanh(travelled / limit);
        double rise = height * (1 - m.stream * (1 - num(TG, "rise_kept"))), bend = num(TG, "curl") * m.stream,
                flutter = num(TG, "flutter_blocks") * m.stream, phase = i * 2.3 + (peak ? 1.1 : 0);
        int segments = lean > 0 ? TG.get("segments").getAsInt() : 1;
        double[] o = r.pts[0];
        double sink = peak ? .06 : .07;
        double[][] points = new double[segments + 1][];
        for (int k = 0; k <= segments; k++) {
            double x = (double) k / segments;
            double[] d = mul(sub(r.at(ageAt(r, x * reach)), o), lean);
            if (d[1] < 0) d[1] *= num(TG, "sag");   // dragged downward, the flame stays near level instead of hanging
            double[] q = add(add(o, d), new double[]{0, rise * (x - bend * x * (1 - x)) - sink, 0});
            double a = flutter * x * x;
            if (a != 0) q = add(add(q, mul(bladeAxis, a * Math.sin(t * 9.1 + phase + x * 3.3))), new double[]{0, .6 * a * Math.sin(t * 13.7 + phase * 1.7 + x * 4.1), 0});
            points[k] = q;
        }
        // Never shorter than the same flame standing still, and never folded back over itself.
        floor(points, height);
        unfold(points, num(TG, "max_turn_degrees") * Math.PI / 180);
        floor(points, height);
        double[][] across = new double[segments + 1][], deep = new double[segments + 1][];
        for (int k = 0; k <= segments; k++) {
            double[] d = tangent(points, k);
            across[k] = fromUp(d, new double[]{1, 0, 0});
            deep[k] = fromUp(d, new double[]{0, 0, 1});
        }
        strip(out, points, across, width / 2, deep, -depth / 2, u, 1);
        strip(out, points, across, -width / 2, deep, depth / 2, u, 1);
        strip(out, points, deep, depth / 2, null, 0, u, 1);
    }

    private static void floor(double[][] points, double height) {
        int last = points.length - 1;
        double[] c = sub(points[last], points[0]);
        double flat = c[0] * c[0] + c[2] * c[2];
        if (c[1] * c[1] + flat >= height * height) return;
        double e = Math.sqrt(Math.max(0, height * height - flat)) - c[1];
        for (int k = 0; k <= last; k++) points[k][1] += e * k / last;
    }

    /** No joint of the spine turns more than maxTurn: the rest swings straighter about that joint. */
    private static void unfold(double[][] points, double maxTurn) {
        for (int k = 1; k + 1 < points.length; k++) {
            double[] a = sub(points[k], points[k - 1]), b = sub(points[k + 1], points[k]);
            double la = length(a), lb = length(b);
            if (la < 1e-6 || lb < 1e-6) continue;
            double[] unit = mul(a, 1 / la);
            double c = dot(unit, b) / lb;
            if (c >= Math.cos(maxTurn)) continue;
            double[] e = sub(b, mul(unit, dot(unit, b)));
            double el = length(e);
            e = el > 1e-9 ? mul(e, 1 / el) : norm(cross(unit, Math.abs(unit[1]) < .9 ? UP : new double[]{1, 0, 0}));
            double[] to = add(mul(unit, Math.cos(maxTurn)), mul(e, Math.sin(maxTurn)));
            for (int j = k + 1; j < points.length; j++) points[j] = add(points[k], rotate(sub(points[j], points[k]), norm(b), to));
        }
    }

    /** v turned by the shortest rotation that takes unit a onto unit b. */
    private static double[] rotate(double[] v, double[] a, double[] b) {
        double[] axis = cross(a, b);
        double s = length(axis), c = dot(a, b);
        if (s < 1e-9) return v;
        axis = mul(axis, 1 / s);
        return add(add(mul(v, c), mul(cross(axis, v), s)), mul(axis, dot(axis, v) * (1 - c)));
    }

    private static double[] tangent(double[][] points, int k) {
        double[] d = sub(points[Math.min(points.length - 1, k + 1)], points[Math.max(0, k - 1)]);
        double l = length(d);
        return l > 1e-7 ? mul(d, 1 / l) : new double[]{0, 1, 0};
    }

    /** The minimal rotation that turns world up onto the unit direction d, applied to v. */
    private static double[] fromUp(double[] d, double[] v) {
        if (d[1] < -.9999) return new double[]{v[0], -v[1], -v[2]};
        double[] w = {d[2], 0, -d[0]}, wv = cross(w, v);
        return add(add(v, wv), mul(cross(w, wv), 1 / (1 + d[1])));
    }

    /** One cutout sheet along a spine, root first: v runs from v0 at the root to 0 at the tip. */
    private static void strip(Batch out, double[][] points, double[][] side, double half, double[][] off, double shift, double u, double v0) {
        int last = points.length - 1;
        for (int k = 0; k < last; k++) {
            double[] a = off == null ? points[k] : add(points[k], mul(off[k], shift)), b = off == null ? points[k + 1] : add(points[k + 1], mul(off[k + 1], shift));
            double[] sa = mul(side[k], half), sb = mul(side[k + 1], half);
            double va = v0 * (1 - (double) k / last), vb = v0 * (1 - (double) (k + 1) / last);
            out.quad(sub(a, sa), add(a, sa), add(b, sb), sub(b, sb), u, va, u + DU, va, u + DU, vb, u, vb, 1, 1, 1, 1);
        }
    }

    /** The swing's wake: one sheet across the arc the blade just swept, fading in from the blade and out behind. */
    private static void wake(Batch tint, Batch glow, double t, Route[] plain, Motion[] motion) {
        int n = plain.length, rows = ST.get("rows").getAsInt();
        Route tip = plain[n - 1];
        double max = num(ST, "max_length_blocks"), drawn = lengthAt(tip, Math.min(SPAN, num(ST, "seconds")));
        if (drawn < 1e-4) return;
        double window = ageAt(tip, max * Math.tanh(drawn / max));
        if (window < 1e-5) return;
        double[][][] v = new double[n][rows + 1][];
        double[][] alpha = new double[n][rows + 1];
        double hold = num(ST, "hold");
        for (int s = 0; s < n; s++) for (int k = 0; k <= rows; k++) {
            double x = (double) k / rows, ph = s * 1.9 + .7, a = num(ST, "flutter_blocks") * motion[s].stream * x * x;
            v[s][k] = add(plain[s].at(window * x), new double[]{0, num(ST, "rise_blocks") * x * x + .6 * a * Math.sin(t * 13.7 + ph * 1.7 + x * 4.1), 0});
            double age = window * x, h = plain[s].h, from = Math.max(0, age - h);
            double speed = (lengthAt(plain[s], age + h) - lengthAt(plain[s], from)) / (age + h - from), drop = plain[s].pts[0][1] - v[s][k][1];
            alpha[s][k] = ramp(WAKE, speed) * (1 - smooth(age / SPAN)) * smooth(x / num(ST, "fade_in")) * (1 - smooth((x - hold) / (1 - hold)))
                    * (1 - smooth(drop / num(ST, "drop_fade_blocks")));
        }
        int columns = ST.get("columns_per_frame").getAsInt();
        double span = num(ST, "frame_span"), v0 = num(ST, "v_start"), v1 = num(ST, "v_end"), tintShare = num(ST, "tint"), glowShare = num(ST, "glow");
        for (int s = 0; s + 1 < n; s++) {
            int g = s / columns;
            double u0 = frame(LICK_FRAMES, t, g) + DU * (1 - span) / 2, f = DU * span / columns;
            double ua = u0 + f * (s - g * columns), ub = u0 + f * (s + 1 - g * columns);
            for (int k = 0; k < rows; k++) {
                double a0 = alpha[s][k], a1 = alpha[s + 1][k], a2 = alpha[s + 1][k + 1], a3 = alpha[s][k + 1];
                if (Math.max(Math.max(a0, a1), Math.max(a2, a3)) < .01) continue;
                double va = v0 + (v1 - v0) * k / rows, vb = v0 + (v1 - v0) * (k + 1) / rows;
                // Two layers on the same quad: a tint that turns what is behind it the colour of fire, then a glow.
                tint.quad(v[s][k], v[s + 1][k], v[s + 1][k + 1], v[s][k + 1], ua, va, ub, va, ub, vb, ua, vb, a0 * tintShare, a1 * tintShare, a2 * tintShare, a3 * tintShare);
                glow.quad(v[s][k], v[s + 1][k], v[s + 1][k + 1], v[s][k + 1], ua, va, ub, va, ub, vb, ua, vb, a0 * glowShare, a1 * glowShare, a2 * glowShare, a3 * glowShare);
            }
        }
    }

    // ---------------------------------------------------------------- chains
    private static double[] bowWeights(double period, double damping) {
        double w0 = 2 * Math.PI / period, wd = w0 * Math.sqrt(1 - damping * damping), sum = 0;
        double[] w = new double[BOW_SAMPLES];
        for (int k = 0; k < BOW_SAMPLES; k++) { double a = (k + .5) * BOW_STEP; w[k] = Math.exp(-damping * w0 * a) * Math.sin(wd * a); sum += w[k]; }
        for (int k = 0; k < BOW_SAMPLES; k++) w[k] /= sum;
        return w;
    }

    /**
     * How far a chain bows as it is swung: its far end's sideways velocity over the last second,
     * through a slightly springy filter, so the chain lags, swings on past a stop and settles.
     */
    private static double[] bow(Memory memory, double now, double[] from, double[] to, java.util.function.Function<Sample, double[]> end, double share) {
        double[] v = new double[3], newer = to;
        for (int k = 0; k < BOW_SAMPLES; k++) {
            double[] older = past(memory, now, (k + 1) * BOW_STEP, end);
            v = add(v, mul(sub(newer, older), BOW_WEIGHTS[k] / BOW_STEP));
            newer = older;
        }
        double[] d = norm(sub(to, from));
        v = sub(v, mul(d, dot(v, d)));
        v[1] = 0;
        double size = length(v) * BOW_SECONDS, cap = share * dist(from, to);
        return size > 1e-6 && cap > 1e-6 ? mul(v, cap * Math.tanh(size / cap) / length(v)) : new double[3];
    }

    /** A chain's line from a to b, sagging a little; bow swings it out sideways (an even arc, or tip-heavy). */
    private static double[] chainLine(double[] a, double[] b, double[] bow, boolean arc, double u) {
        double sag = Math.min(.6, .04 * dist(a, b));
        double[] p = add(add(a, mul(sub(b, a), u)), new double[]{0, -sag * 4 * u * (1 - u), 0});
        return add(p, mul(bow, arc ? 4 * u * (1 - u) : 6.75 * u * u * (1 - u)));
    }

    private static double[][] hookPoints(double[] a, double[] b, double[] bow) {
        double[] d = norm(sub(b, chainLine(a, b, bow, false, .97)));
        double[] s = Math.hypot(d[0], d[2]) > 1e-3 ? norm(cross(UP, d)) : new double[]{1, 0, 0};
        double[][] shape = {{0, 0}, {.55, 0}, {.82, .2}, {.86, .5}, {.66, .74}, {.34, .68}};
        double[][] out = new double[shape.length][];
        for (int k = 0; k < shape.length; k++) out[k] = add(b, add(mul(d, shape[k][0] * HOOK), mul(s, shape[k][1] * HOOK)));
        return out;
    }

    private static void chains(Memory memory, double now, boolean tether, double[] wrist, double[] pommel, double[][] chest, double[][] ends,
            Batch iron, Batch hot, Batch fire) {
        if (tether && dist(wrist, pommel) >= WRIST_MIN) links(iron, null, wrist, pommel, bow(memory, now, wrist, pommel, Sample::pommel, WRIST_BOW_SHARE), true);
        for (int i = 0; i < ends.length; i++) {
            if (dist(chest[i], ends[i]) < LOOSE_MIN) continue;
            int chain = i;
            double[] a = chest[i], b = ends[i], bow = bow(memory, now, a, b, s -> s.ends[chain], BOW_SHARE);
            links(iron, hot, a, b, bow, false);
            double[][] hook = hookPoints(a, b, bow);
            for (int k = 0; k + 1 < hook.length; k++) beam(k >= hook.length - 3 ? hot : iron, k >= hook.length - 3 ? HOT : IRON, hook[k], hook[k + 1], .11 - .012 * k);
            // Its fire: a low flame every flame_spacing blocks or so and a tall one on the hook.
            double dt = .03;
            double[] wasA = past(memory, now, dt, s -> s.chest[chain]), wasB = past(memory, now, dt, s -> s.ends[chain]);
            int count = Math.max(2, (int) Math.round(dist(a, b) / FLAME_SPACING));
            for (int j = 0; j < count; j++) {
                int id = i * 40 + j;
                double u = (j + .5 + .5 * (hash(id + 7) - .5)) / count;
                chainFlame(fire, now, chainLine(a, b, bow, false, u), chainLine(wasA, wasB, bow, false, u), dt, id, false);
            }
            double[][] was = hookPoints(wasA, wasB, bow);
            chainFlame(fire, now, hook[2], was[2], dt, i * 40 + 38, true);
            chainFlame(fire, now, hook[4], was[4], dt, i * 40 + 39, false);
        }
    }

    // ---------------------------------------------------------------- the intro's web
    /**
     * The web of chains his intro strings through the hall, at scene tick t ({@link PyreKingIntroTrack}): each hangs
     * between its two ends; heat runs along it and its links glow and burn; it parts, each half swings down from its
     * own end, and the fire eats both back from the break until nothing is left. The one the sword hangs on follows
     * the sword's pommel and parts there.
     */
    private static void web(double t, double now, Batch iron, Batch hot, Batch fire) {
        PyreKingIntroTrack track = PyreKingIntroTrack.get();
        int id = 400;
        for (PyreKingIntroTrack.Chain chain : track.chains()) {
            id += 97;
            if (t >= chain.gone()) continue;
            double[] a = vec(PyreKingIntroClient.place(chain.a()));
            double[] b = vec(PyreKingIntroClient.place(chain.sword() ? track.sword(Math.min(t, chain.parts())) : chain.b()));
            double span = dist(a, b), cut = chain.part();
            int n = Math.max(4, (int) Math.round(span / .34));
            double heat = Math.max(0, Math.min(1, (t - chain.heat()) / Math.max(1, chain.run())));
            boolean parted = t >= chain.parts();
            double swung = parted ? smooth((t - chain.parts()) / 26.0) : 0;
            double burned = parted ? (t - chain.parts()) / Math.max(1.0, chain.gone() - chain.parts()) : 0;
            for (int i = 0; i < n; i++) {
                double u = (i + .5) / n;
                boolean first = u < cut;
                // 0 at the break, 1 at the half's own end: the fire eats it back that way.
                double left = first ? (cut - u) / Math.max(cut, 1e-6) : (u - cut) / Math.max(1 - cut, 1e-6);
                if (parted && left < burned) continue;
                double[] p = hang(a, b, chain.sag(), span, (double) i / n, cut, swung), q = hang(a, b, chain.sag(), span, (double) (i + 1) / n, cut, swung);
                if (dist(p, q) < 1e-4) continue;        // heaped on the floor
                double[] d = norm(sub(q, p)), side = norm(cross(d, Math.abs(d[1]) > .9 ? new double[]{1, 0, 0} : UP)), up = cross(side, d);
                double w = i % 2 == 1 ? .06 : .13, h = i % 2 == 1 ? .13 : .06, half = dist(p, q) * .62;
                double[] c = mul(add(p, q), .5);
                boolean glowing = u <= heat;
                box(glowing ? hot : iron, glowing ? HOT : IRON, c, mul(d, half), mul(side, w), mul(up, h));
                // Fire along what glows, and a taller flame where the heat is arriving and where each half is burning back.
                boolean front = heat < 1 && Math.abs(u - heat) < .6 / n, eaten = parted && left - burned < 1.2 / (n * Math.max(first ? cut : 1 - cut, .05));
                if (glowing && (front || eaten || (i + id) % 6 == 2)) chainFlame(fire, now, c, c, .03, id + i, front || eaten);
            }
        }
    }

    /**
     * Where a chain's point a share u of the way from a to b hangs. Whole, it sags between its ends. Parted at `cut`,
     * each half swings down (swung 0 to 1) to hang straight from its own end, and what reaches the floor lies on it.
     */
    private static double[] hang(double[] a, double[] b, double sag, double span, double u, double cut, double swung) {
        double[] p = add(add(a, mul(sub(b, a), u)), new double[]{0, -sag * 4 * u * (1 - u), 0});
        if (swung <= 0) return p;
        boolean first = u < cut;
        double[] end = first ? a : b;
        double along = span * (first ? u : 1 - u);
        double floor = PyreKingIntroClient.floor(end[0], end[2]) + .07;
        double[] down = {end[0], Math.max(floor, end[1] - along), end[2]};
        double[] out = mix(p, down, swung);
        out[1] = Math.max(out[1], Math.min(p[1], floor));
        return out;
    }

    private static double[] vec(net.minecraft.util.math.Vec3d v) { return new double[]{v.x, v.y, v.z}; }

    /** Upright where its chain is still; where the chain moves it lies back along the way it came. */
    private static void chainFlame(Batch out, double t, double[] root, double[] prev, double dt, int i, boolean tall) {
        double[] v = mul(sub(root, prev), 1 / dt);
        double speed = length(v), k = Math.min(1, speed / 30);
        double h = (tall ? .95 : .34 + .3 * hash(i + 41)) * (.84 + .28 * noise(t, i + 60)), w = (tall ? .5 : .3) * (.88 + .18 * noise(t + .3, i));
        double[] tip = add(add(root, speed > 1e-6 ? mul(v, -Math.min(.05, 1.1 / speed)) : new double[3]), new double[]{0, h * (1 - .45 * k), 0});
        double[] d = norm(sub(tip, root));
        double u = frame(tall ? TONGUE_FRAMES : LICK_FRAMES, t, i);
        double[][] points = {add(root, new double[]{0, -.05, 0}), tip};
        double[] x = fromUp(d, new double[]{1, 0, 0}), z = fromUp(d, new double[]{0, 0, 1});
        strip(out, points, new double[][]{x, x}, w / 2, null, 0, u, 1);
        strip(out, points, new double[][]{z, z}, w / 2, null, 0, u, 1);
    }

    /** Heavy links from a to b, alternating flat and upright; with hot, every third link glows. */
    private static void links(Batch iron, Batch hot, double[] a, double[] b, double[] bow, boolean arc) {
        int n = Math.max(4, (int) Math.round(dist(a, b) / .32));
        for (int i = 0; i < n; i++) {
            double[] p = chainLine(a, b, bow, arc, (double) i / n), q = chainLine(a, b, bow, arc, (double) (i + 1) / n);
            double[] d = norm(sub(q, p)), side = norm(cross(d, Math.abs(d[1]) > .9 ? new double[]{1, 0, 0} : UP)), up = cross(side, d);
            double w = i % 2 == 1 ? .05 : .11, h = i % 2 == 1 ? .11 : .05, half = dist(p, q) * .62;
            double[] c = mul(add(p, q), .5);
            boolean glowing = hot != null && i % 3 == 1;
            box(glowing ? hot : iron, glowing ? HOT : IRON, c, mul(d, half), mul(side, w), mul(up, h));
        }
    }

    /** A square bar from p to q, half-width w. */
    private static void beam(Batch out, double[] uv, double[] p, double[] q, double w) {
        double[] d = sub(q, p), unit = norm(d), side = norm(cross(unit, Math.abs(unit[1]) > .9 ? new double[]{1, 0, 0} : UP)), up = cross(side, unit);
        box(out, uv, mul(add(p, q), .5), mul(d, .5), mul(side, w), mul(up, w));
    }

    /** The four long faces of a box: centre c, half-vectors along (x), across (y) and up (z). */
    private static void box(Batch out, double[] uv, double[] c, double[] x, double[] y, double[] z) {
        int[][] faces = {{-1, -1, 1, -1}, {1, -1, 1, 1}, {1, 1, -1, 1}, {-1, 1, -1, -1}};
        for (int[] f : faces) {
            double[] a = add(add(c, mul(y, f[0])), mul(z, f[1])), b = add(add(c, mul(y, f[2])), mul(z, f[3]));
            out.quad(sub(a, x), sub(b, x), add(b, x), add(a, x), uv[0], uv[1], uv[2], uv[1], uv[2], uv[3], uv[0], uv[3], 1, 1, 1, 1);
        }
    }

    // ---------------------------------------------------------------- drawing
    /** Quads in world space: four corners of x y z u v alpha each. */
    private static final class Batch {
        private final List<float[]> quads = new ArrayList<>();

        boolean isEmpty() { return quads.isEmpty(); }

        void quad(double[] a, double[] b, double[] c, double[] d, double ua, double va, double ub, double vb, double uc, double vc, double ud, double vd,
                double aa, double ab, double ac, double ad) {
            quads.add(new float[]{(float) a[0], (float) a[1], (float) a[2], (float) ua, (float) va, (float) aa,
                    (float) b[0], (float) b[1], (float) b[2], (float) ub, (float) vb, (float) ab,
                    (float) c[0], (float) c[1], (float) c[2], (float) uc, (float) vc, (float) ac,
                    (float) d[0], (float) d[1], (float) d[2], (float) ud, (float) vd, (float) ad});
        }

        /** additive: the layer adds colour and ignores alpha, so the colour itself carries the fade. */
        void emit(VertexConsumer out, float ex, float ey, float ez, int light, boolean additive) {
            for (float[] q : quads) {
                float nx = (q[7] - q[1]) * (q[20] - q[2]) - (q[8] - q[2]) * (q[19] - q[1]);
                float ny = (q[8] - q[2]) * (q[18] - q[0]) - (q[6] - q[0]) * (q[20] - q[2]);
                float nz = (q[6] - q[0]) * (q[19] - q[1]) - (q[7] - q[1]) * (q[18] - q[0]);
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-9f) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
                for (int k = 0; k < 24; k += 6) {
                    int alpha = Math.round(Math.max(0, Math.min(1, q[k + 5])) * 255), shade = additive ? alpha : 255;
                    out.vertex(q[k] - ex, q[k + 1] - ey, q[k + 2] - ez).color(shade, shade, shade, alpha).texture(q[k + 3], q[k + 4])
                            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
                }
            }
        }
    }

    // ---------------------------------------------------------------- small maths and data
    private static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double[] mul(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
    private static double[] mix(double[] a, double[] b, double f) { return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    private static double length(double[] a) { return Math.sqrt(dot(a, a)); }
    private static double dist(double[] a, double[] b) { return length(sub(a, b)); }
    private static double[] norm(double[] a) { double l = length(a); return l > 1e-12 ? mul(a, 1 / l) : new double[]{0, 1, 0}; }
    private static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    private static double num(JsonObject o, String key) { return o.get(key).getAsDouble(); }

    private static double[] row(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double[][] grid(JsonArray a) {
        double[][] out = new double[a.size()][];
        for (int i = 0; i < out.length; i++) out[i] = row(a.get(i).getAsJsonArray());
        return out;
    }

    private static String[] names(JsonArray a) {
        String[] out = new String[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsString();
        return out;
    }

    private static JsonObject load() {
        try (var stream = PyreKingEffectsLayer.class.getResourceAsStream("/assets/elementalwands/pyre_king_effects.json")) {
            if (stream == null) throw new IllegalStateException("Missing pyre_king_effects.json: run art/pyre_king/build_art.py --install");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
