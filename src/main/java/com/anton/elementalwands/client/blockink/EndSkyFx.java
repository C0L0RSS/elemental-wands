package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.FirstWizardIntroClient;
import com.anton.elementalwands.end.EndArena;
import com.anton.elementalwands.end.EndIntroTrack;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;

/**
 * The sky over the End's island in the mod's block style, from the ink atlas: the pool of living ink, and while the
 * intro plays everything the scene adds round it (the dragon itself is vanilla's, {@code FirstWizardIntroDragon}).
 *
 * <ul>
 *   <li><b>The pool</b>: a mass of ink columns over a disc as wide as two-thirds of the floor, 38 blocks up. Its belly
 *       sags in the middle and ripples slowly; a crest of dark purple runs across it now and then; its outline wavers,
 *       ringed by a violet meniscus. Blobs bulge out of its belly and drops of ink stretch, fall and splat on the floor.
 *       Matte black and liquid: no stars, no glass panes (that is Space). About seven hundred cubes, one mesh a frame,
 *       drawn from anywhere in the End's main island and skipped beyond 400 blocks.</li>
 *   <li><b>The breath</b> into the pool, the <b>bloom</b> of violet under its surface, the <b>stream back</b> (the same
 *       puffs, stretched, with an ink sheath), its breath swirling round the caught dragon, and what is left of the
 *       dragon drawn up into the ink.</li>
 *   <li><b>The condensing</b>: the edge coming down as a hanging lobe, the pour from the pool onto the forming body,
 *       a pool at its feet, a violet meniscus rising up it, and the last strand into its wand hand.</li>
 * </ul>
 * Stateless: each frame is drawn from the scene's clock ({@link FirstWizardIntroClient}) or the waiting pool's share.
 */
public final class EndSkyFx {
    private record Frame(AtlasMesh ink) {}
    private static final RenderStateDataKey<Frame> MESH = RenderStateDataKey.create(() -> "First Wizard sky");
    private static final Frame NONE = new Frame(null);
    private static final double FAR = 400;

    private EndSkyFx() {}

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            context.worldState().setData(MESH, NONE);
            ClientWorld world = context.world();
            if (world == null || world.getRegistryKey() != EndArena.WORLD) return;
            float delta = context.tickCounter().getTickProgress(false);
            EndIntroTrack.Scene scene = FirstWizardIntroClient.scene();
            double waiting = FirstWizardIntroClient.waitingPool(delta);
            if (scene == null && waiting <= 0) return;
            Vec3d camera = context.camera().getPos();
            if (camera.horizontalLengthSquared() > FAR * FAR) return;
            double now = (world.getTime() + delta) / 20.0;
            var ink = new AtlasMesh(InkKit.ATLAS, camera, !context.camera().isThirdPerson(), now, world);
            boolean embers = MinecraftClient.getInstance().options.getParticles().getValue() == ParticlesMode.ALL;
            EndIntroTrack track = EndIntroTrack.get();
            if (scene == null) pool(ink, track, track.poolRadius() * smoothstep(waiting), 0, 0, embers);
            else {
                double t = FirstWizardIntroClient.sceneTime(delta);
                pool(ink, track, scene.value("pool_r", t), scene.value("pool_cz", t), scene.value("bloom", t), embers);
                scene(ink, track, scene, t, world, delta, embers);
            }
            context.worldState().setData(MESH, new Frame(ink));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            AtlasMesh mesh = context.worldState().getDataOrDefault(MESH, NONE).ink();
            if (mesh == null) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (mesh.hasSolid()) { mesh.emitSolid(consumers.getBuffer(mesh.solidLayer), entry); flush(consumers, mesh); }
            if (mesh.hasSee()) { mesh.emitSee(consumers.getBuffer(mesh.seeLayer), entry); flush(consumers, mesh); }
        });
    }

    private static void flush(VertexConsumerProvider consumers, AtlasMesh mesh) {
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) { immediate.draw(mesh.solidLayer); immediate.draw(mesh.seeLayer); }
    }

    private static Vec3d at(double x, double y, double z) { return EndIntroTrack.world(new Vec3d(x, y, z)); }

    // ------------------------------------------------------------------ the pool

    /** The pool at radius r with its middle drifted cz south (it drains toward the edge that comes down); bloom 0 to 1.4. */
    static void pool(AtlasMesh m, EndIntroTrack track, double r, double cz, double bloom, boolean embers) {
        if (r < .2) return;
        double s = m.time, level = track.poolLevel(), full = track.poolRadius();
        double scale = Math.sqrt(r / full);
        double cell = r > 9 ? 2 : Math.max(.45, r / 4.5);
        int n = (int) Math.ceil(r / cell) + 1;
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
            double x = i * cell, z = j * cell, d = Math.hypot(x, z);
            double edge = r * (.9 + .1 * noise(Math.atan2(z, x) * 2.6 + s * .12 + 7));
            if (d > edge) continue;
            double k = d / edge, seed = i * 7.31 + j * 13.17;
            double wave = Math.sin(d * .5 - s * 1.1 + Math.atan2(z, x) * 1.0);
            double ripple = (.35 * wave + .22 * Math.sin(x * .31 + s * .8) * Math.cos(z * .27 - s * .6)) * scale * (1 - k * .4);
            double bottom = track.underside(d, r) + ripple;
            // Domed on top as well as below, so from the side it is a mass and not a plate.
            double top = level + (.8 + 3.6 * Math.sqrt(Math.max(0, 1 - k * k))) * scale + .25 * Math.sin(d * .4 + s);
            boolean rim = d > edge - cell * 1.15;
            int row = rnd(seed + Math.floor(s * .25 + rnd(seed) * 4)) < .07 ? InkKit.GLOSS : InkKit.INK;
            double w = cell * 1.04;
            InkKit.cube(m, at(x, (top + bottom) / 2, cz + z), w, top - bottom, w, rim ? InkKit.RIM : row, seed, AXES, 1);
            // A crest of wet purple running across its belly: the ink moving.
            if (wave > .965 && k < .88) InkKit.cube(m, at(x, bottom - .07, cz + z), w * .96, .14, w * .96, InkKit.DEEP, seed + 1, AXES, 1);
            // The violet meniscus round its edge, a lip just below the rim.
            if (rim) InkKit.cube(m, at(x * 1.015, bottom - .1, cz + z * 1.015), w * .92, .22, w * .92, InkKit.VIOLET, seed + 2, AXES, 1);
        }
        // Blobs bulging out of its belly, breathing slowly.
        for (int i = 0; i < 7; i++) {
            double a = rnd(i * 3.7 + 1) * TAU + s * .03 * (i % 2 == 0 ? 1 : -1), d = r * .62 * Math.sqrt(rnd(i * 5.1 + 2));
            double x = Math.cos(a) * d, z = Math.sin(a) * d;
            double b = (1.3 + .7 * Math.sin(s * .55 + i * 1.9)) * scale;
            InkKit.blob(m, at(x, track.underside(d, r) - b * .45, cz + z), b, InkKit.INK, i % 3 == 0 ? InkKit.GLOSS : InkKit.INK, i * 11.3, s * .4);
        }
        // Drops: each gathers under the belly, stretches, falls and splats on the floor below.
        int drops = embers ? 9 : 4;
        for (int i = 0; i < drops; i++) {
            double life = 4.6 + rnd(i * 2.9) * 1.8, phase = s / life + rnd(i * 1.3), loop = Math.floor(phase), u = (phase - loop) * life;
            double a = rnd(i * 4.3 + loop * 7.7) * TAU, d = r * .75 * Math.sqrt(rnd(i * 6.1 + loop * 3.3));
            double x = Math.cos(a) * d, z = Math.sin(a) * d, under = track.underside(d, r);
            double hang = 1.4;
            if (u < hang) {
                double g = u / hang;
                InkKit.cube(m, at(x, under - .25 - .5 * g, cz + z), .3 + .15 * g, .45 + .9 * g, .3 + .15 * g, g > .8 ? InkKit.GLOSS : InkKit.INK, i, AXES, 1);
            } else InkKit.drip(m, at(x, under - 1.1, cz + z), EndArena.CENTRE.getY() + 1, u - hang, life - hang, .42 * scale + .12, i * 9.1 + loop);
        }
        // The bloom: violet spreading just under the surface where the breath went in.
        if (bloom > 0) {
            Vec3d b = track.bloom();
            double reach = 5.5 * Math.min(1, bloom), c2 = .8;
            int bn = (int) Math.ceil(reach / c2);
            for (int i = -bn; i <= bn; i++) for (int j = -bn; j <= bn; j++) {
                double x = b.x + i * c2, z = b.z + j * c2, d = Math.hypot(i * c2, j * c2);
                double edge = reach * (.82 + .18 * noise(Math.atan2(j, i) * 3 + s * .5));
                if (d > edge) continue;
                double k = d / Math.max(.1, edge), pulse = 1 + .12 * Math.sin(s * 5 - d * 1.3);
                int row = k < .35 ? InkKit.MAGENTA : k < .75 ? InkKit.VIOLET : InkKit.PURPLE;
                double under = track.underside(Math.hypot(x, z - cz), r);
                InkKit.cube(m, at(x, under - .1 - .25 * (1 - k) * bloom, z), c2 * 1.02, .22 + .3 * (1 - k) * Math.min(1, bloom) * pulse, c2 * 1.02, row, i * 3.3 + j, AXES, 1);
            }
            // It swells as it fills, and more when it sends the breath back.
            double swell = Math.max(0, bloom - .6);
            InkKit.blob(m, at(b.x, b.y - .5 - swell * 1.2, b.z), (.7 + swell * 1.3) * (1 + .08 * Math.sin(s * 4)), InkKit.MAGENTA, InkKit.VIOLET, 77, s);
        }
    }

    // ------------------------------------------------------------------ the scene

    private static void scene(AtlasMesh m, EndIntroTrack track, EndIntroTrack.Scene scene, double t, ClientWorld world, float delta, boolean embers) {
        double s = m.time;
        Vec3d bloomAt = EndIntroTrack.world(track.bloom());
        double breath = scene.value("breath", t);
        if (breath > 0) breath(m, EndIntroTrack.world(scene.point("mouth", t)), bloomAt, breath, s);
        double back = scene.value("back", t);
        Vec3d target = scene.has("target") ? EndIntroTrack.world(scene.point("target", t)) : null;
        if (back > 0 && target != null) stream(m, bloomAt, target, back, s, t >= scene.beat("catch") && t < scene.beat("burst"));
        double remains = scene.value("remains", t);
        if (remains > 0 && remains < 1 && target != null) remains(m, target, bloomAt, remains);
        condensing(m, track, scene, t, world, embers);
    }

    /** The dragon's breath: puffs widening from its jaws to the pool, hot magenta cooling through violet. */
    private static void breath(AtlasMesh m, Vec3d mouth, Vec3d to, double amount, double s) {
        Vec3d d = to.subtract(mouth);
        double len = d.length();
        if (len < .5) return;
        Vec3d[] b = basis(d);
        int n = 40;
        for (int i = 0; i < n; i++) {
            double u = fract(s * 1.5 + i / (double) n + rnd(i * 1.7) * .02), spread = .25 + 1.9 * u;
            double a = rnd(i * 2.3 + Math.floor(s * 1.5 + i / (double) n)) * TAU, rr = Math.sqrt(rnd(i * 3.1)) * spread;
            Vec3d p = mouth.add(d.multiply(u)).add(b[0].multiply(Math.cos(a) * rr)).add(b[2].multiply(Math.sin(a) * rr));
            double size = (.35 + 1.15 * u) * amount * (.75 + .5 * rnd(i * 4.9));
            InkKit.cube(m, p, size, u < .3 ? InkKit.MAGENTA : u < .7 ? InkKit.VIOLET : InkKit.PURPLE, i, tumble(i, s, 3));
        }
        // Where it strikes the ink: a ring of violet spreading over the belly.
        for (int i = 0; i < 12; i++) {
            double u = fract(s * 1.2 + i / 12.0), a = i * TAU / 12 + s * .4;
            InkKit.cube(m, to.add(Math.cos(a) * (1 + 3 * u), -.15, Math.sin(a) * (1 + 3 * u)), .5 * (1 - u) * amount, InkKit.VIOLET, i + 40, tumble(i + 40, s, 2));
        }
    }

    /** The breath sent back: the same puffs stretched into a stream along an ink sheath, the head of it brightest. */
    private static void stream(AtlasMesh m, Vec3d from, Vec3d target, double back, double s, boolean holding) {
        Vec3d end = from.lerp(target, back), d = end.subtract(from);
        double len = d.length();
        if (len < .3) return;
        Vec3d dir = d.normalize();
        Vec3d[] b = basis(dir);
        int n = (int) Math.ceil(len / .5);
        for (int i = 0; i < n; i++) {
            double u = fract(i / (double) n - s * 2.4 * 2 / Math.max(4, len)), along = u * len;
            double wob = .18 * Math.sin(along * .9 - s * 9);
            Vec3d p = from.add(dir.multiply(along)).add(b[0].multiply(wob));
            double w = .85 + .25 * Math.sin(along * 1.7 + s * 12);
            InkKit.cube(m, p, w, 1.2, w, i % 3 == 0 ? InkKit.VIOLET : InkKit.MAGENTA, i, basis(dir, s * 3 + i), 1);
            // The dragon's own puffs, swept along it.
            if (i % 2 == 0) {
                double pa = rnd(i * 5.3) * TAU + s * 3, pr = .7 + .5 * rnd(i * 6.1);
                InkKit.cube(m, p.add(b[0].multiply(Math.cos(pa) * pr)).add(b[2].multiply(Math.sin(pa) * pr)), .45 + .3 * rnd(i * 2.2),
                        i % 4 == 0 ? InkKit.PURPLE : InkKit.MAGENTA, i + 200, tumble(i + 200, s, 3));
            }
            // The sheath: ink winding round the breath, so it reads as the pool's.
            double a = along * 1.4 - s * 7;
            Vec3d around = b[0].multiply(Math.cos(a) * .9).add(b[2].multiply(Math.sin(a) * .9));
            InkKit.cube(m, p.add(around), .3, i % 4 == 0 ? InkKit.DEEP : InkKit.INK, i + 300, tumble(i + 300, s, 4));
        }
        if (back < .999) InkKit.blob(m, end, 1.1, InkKit.MAGENTA, InkKit.VIOLET, 91, s * 3);
        // Its own breath wrapped round it while it is held: puffs circling and climbing.
        if (holding) for (int i = 0; i < 26; i++) {
            double a = rnd(i * 1.9) * TAU + s * (2.2 + rnd(i) * 1.2), rr = 3.5 + 2.2 * Math.sin(s * .9 + i), y = 3 * Math.sin(s * 1.3 + i * .7);
            Vec3d p = target.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            InkKit.cube(m, p, .55 + .45 * rnd(i * 2.7), i % 3 == 0 ? InkKit.INK : i % 3 == 1 ? InkKit.MAGENTA : InkKit.VIOLET, i + 500, tumble(i + 500, s, 3));
        }
    }

    /** What is left of the dragon: a flash, then its pieces flung out and drawn up into the bloom, cooling to ink. */
    private static void remains(AtlasMesh m, Vec3d from, Vec3d into, double k) {
        double s = m.time;
        if (k < .1) {
            double f = k / .1;
            for (int i = 0; i < 20; i++) InkKit.cube(m, from.add(sphereDir(i, 20).multiply(4 * f)), 2.2 * (1 - f) + .3, i % 2 == 0 ? InkKit.MAGENTA : InkKit.VIOLET, i + 700, tumble(i + 700, s, 5));
        }
        for (int i = 0; i < 64; i++) {
            Vec3d dir = sphereDir(i, 64);
            double reach = 9 + 6 * rnd(i * 3.3);
            Vec3d out = from.add(dir.multiply(reach * (1 - Math.pow(1 - Math.min(1, k / .25), 2))));
            Vec3d p;
            int row;
            double size = .55 + .35 * rnd(i * 1.1);
            if (k < .25) { p = out; row = i % 2 == 0 ? InkKit.MAGENTA : InkKit.VIOLET; }
            else {
                double g = Math.pow((k - .25) / .75, 1.6);
                Vec3d mid = from.add(dir.multiply(reach)).lerp(into, g);
                p = mid.add(dir.crossProduct(UP).multiply(Math.sin(g * Math.PI) * 2.5));
                size *= 1 - .6 * g;
                row = InkKit.cool(g);
            }
            InkKit.cube(m, p, size, row, i + 800, tumble(i + 800, s, 4));
        }
    }

    // ------------------------------------------------------------------ the condensing

    /** How high up the forming body the ink has risen, in blocks from the floor, for how far it has formed. */
    static double formedHeight(double f) {
        if (f < .3) return .75 * f / .3;
        if (f < .55) return .75 + .75 * (f - .3) / .25;
        if (f < .75) return 1.5 + .05 * (f - .55) / .2;
        return 1.55 + .45 * Math.min(1, (f - .75) / .2);
    }

    private static void condensing(AtlasMesh m, EndIntroTrack track, EndIntroTrack.Scene scene, double t, ClientWorld world, boolean embers) {
        double drape = scene.value("drape", t), flow = scene.value("flow", t), top = scene.value("top", t), formed = scene.value("formed", t);
        if (drape <= 0 && formed <= 0) return;
        double s = m.time, r = scene.value("pool_r", t), cz = scene.value("pool_cz", t);
        Vec3d form = track.form();
        Vec3d feet = EndIntroTrack.world(form);
        // Where the pour leaves the pool: its belly over the forming spot (the pool has drained toward it).
        double yTop = r > .3 ? track.underside(Math.hypot(form.x, form.z - cz), r) + .3 : track.poolLevel() - 1;
        double rise = formedHeight(formed);
        Vec3d hand = hand(world, feet);
        if (flow <= 0 && formed <= 0) {
            // The edge comes down: a lobe hanging from the belly, lengthening, a drop's bulb at its tip.
            double tip = yTop - drape * (yTop - .1);
            int n = (int) Math.ceil((yTop - tip) / .7) + 1;
            for (int i = 0; i <= n; i++) {
                double u = i / (double) n, y = yTop - (yTop - tip) * u;
                double w = (2.8 - 1.6 * u) * (1 + .08 * Math.sin(s * 3 + u * 6)), sway = .35 * Math.sin(s * .8 + u * 2.2) * u;
                InkKit.cube(m, at(form.x + sway, y, form.z), w, .9, w, i % 5 == 2 ? InkKit.RIM : InkKit.INK, i + 900, yawAxes(u * 1.3 + s * .2), 1);
            }
            InkKit.blob(m, at(form.x, tip - .3, form.z), 1.1 + .2 * Math.sin(s * 4), InkKit.INK, InkKit.GLOSS, 911, s);
            return;
        }
        // The pour: from the pool onto the top of what has formed, or, at the last, into its wand hand.
        if (flow > 0) {
            Vec3d upper = at(form.x, yTop - top * (yTop - (hand.y - feet.y)), form.z);
            Vec3d lower = top > 0 ? hand.add(0, .12, 0) : feet.add(0, rise + .1, 0);
            if (top > 0) upper = upper.lerp(new Vec3d(hand.x, upper.y, hand.z), top);
            Vec3d d = lower.subtract(upper);
            double len = d.length(), width = (.18 + .5 * flow) * (top > 0 ? .6 : 1);
            if (len > .2) {
                int n = (int) Math.ceil(len / .8);
                for (int i = 0; i < n; i++) {
                    double u = (i + .5) / n;
                    double sway = .12 * Math.sin(s * 2.1 + u * 7) * (1 - u);
                    double wu = width * (1.5 - .8 * u);
                    InkKit.cube(m, upper.add(d.multiply(u)).add(sway, 0, 0), wu, .95, wu, i % 6 == 3 ? InkKit.GLOSS : InkKit.INK, i + 1000, yawAxes(i * .7 + s * .3), 1);
                }
                // Lumps of ink racing down it.
                for (int i = 0; i < 12; i++) {
                    double u = fract(s * 16 / Math.max(4, len) + i / 12.0);
                    InkKit.cube(m, upper.add(d.multiply(u)), width * (1.6 - .7 * u), i % 3 == 0 ? InkKit.RIM : InkKit.GLOSS, i + 1100, tumble(i + 1100, s, 3));
                }
            }
        }
        if (formed >= 1) return;
        // A pool at its feet, shrinking as it rises out of it; the violet meniscus of the ink climbing its body.
        InkKit.pool(m, feet.add(0, .01, 0), 1.5 - .7 * formed, 1, s, 1203);
        if (formed > 0) {
            for (int i = 0; i < 14; i++) {
                double a = i * TAU / 14 + s * .6, y = rise + .05 * Math.sin(s * 5 + i);
                Vec3d p = feet.add(Math.cos(a) * .46, y, Math.sin(a) * .3);
                InkKit.cube(m, p, .13, i % 2 == 0 ? InkKit.VIOLET : InkKit.RIM, i + 1300, tumble(i + 1300, s, 2));
            }
            int drips = embers ? 6 : 3;
            for (int i = 0; i < drips; i++) {
                double life = .9 + rnd(i * 3.7) * .5, u = fract(s / life + rnd(i * 1.9)), a = rnd(i * 4.1 + Math.floor(s / life)) * TAU;
                InkKit.drip(m, feet.add(Math.cos(a) * .4, rise, Math.sin(a) * .25), feet.y, u * life, life, .1, i * 13.7 + Math.floor(s / life));
            }
            // Splashes where the pour lands.
            if (flow > 0 && top <= 0) InkKit.splash(m, feet.add(0, rise + .1, 0), feet.y, fract(s * 1.4), 6, 2.2, .14, Math.floor(s * 1.4) * 7.3);
        }
    }

    /** The copy's wand hand at its side (it faces the arrival, south), as CopyFx places it. */
    private static Vec3d hand(ClientWorld world, Vec3d feet) {
        boolean left = world.getEntityById(FirstWizardIntroClient.sceneCopy()) instanceof FirstWizardEntity copy && copy.getMainArm() == Arm.LEFT;
        // Facing south, its right hand is to the west (-x).
        return feet.add(left ? .36 : -.36, .75, .15);
    }
}
