package com.anton.elementalwands.client.blockfire;

import com.anton.elementalwands.entity.FlashoverEmberEntity;
import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.BlockFireEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Client-only Fire spell visuals drawn as block fire. The server sends spell moments (BlockFireEvents) and
 * the spell entities carry their own state; nothing here decides a hit. Each frame every live effect adds
 * its cubes to one mesh, which is drawn in two passes: opaque full-bright fire, then see-through smoke.
 */
public final class BlockFireEffects {
    /** One frame's context for the effects. now is the world clock in seconds, including the partial tick. */
    record Frame(BlockFireMesh m, double now, float delta, ClientWorld world) {
        double t() { return m.time; }
    }
    /** A running effect; draw returns false once it has finished and can be dropped. */
    interface Fx { boolean draw(Frame f); }

    private static final RenderStateDataKey<BlockFireMesh> MESH = RenderStateDataKey.create(() -> "Block fire");
    private static final BlockFireMesh NONE = new BlockFireMesh(Vec3d.ZERO, false, false, 0, null);
    private static final int MAX_EFFECTS = 256;
    private static final List<Fx> EFFECTS = new ArrayList<>();
    /** Effects that follow a spell entity, by entity id, so each is started once. */
    private static final Map<Integer, Fx> ATTACHED = new HashMap<>(), RINGS = new HashMap<>();
    private static ClientWorld world;

    private BlockFireEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.BlockFirePayload.ID, (payload, context) -> {
            var w = context.client().world;
            if (w == null) return;
            sync(w);
            start(w, payload);
        });
        ClientTickEvents.END_CLIENT_TICK.register(BlockFireEffects::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            ClientWorld w = context.world();
            if (w != world || EFFECTS.isEmpty()) { context.worldState().setData(MESH, NONE); return; }
            var client = MinecraftClient.getInstance();
            float delta = context.tickCounter().getTickProgress(false);
            double now = (w.getTime() + delta) / 20.0;
            boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
            var mesh = new BlockFireMesh(context.camera().getPos(), !context.camera().isThirdPerson(), embers, now, w);
            Frame frame = new Frame(mesh, now, delta, w);
            EFFECTS.removeIf(fx -> !fx.draw(frame));
            ATTACHED.values().removeIf(fx -> !EFFECTS.contains(fx));
            RINGS.values().removeIf(fx -> !EFFECTS.contains(fx));
            context.worldState().setData(MESH, mesh);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            BlockFireMesh mesh = context.worldState().getDataOrDefault(MESH, NONE);
            if (mesh.isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (mesh.hasFire()) {
                mesh.emitFire(consumers.getBuffer(BlockFireMesh.FIRE), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockFireMesh.FIRE);
            }
            if (mesh.hasSmoke()) {
                mesh.emitSmoke(consumers.getBuffer(BlockFireMesh.SMOKE), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockFireMesh.SMOKE);
            }
        });
    }

    private static void clear() { EFFECTS.clear(); ATTACHED.clear(); RINGS.clear(); FlameFx.clear(); LeapFx.clear(); }
    private static void sync(ClientWorld w) { if (w != world) { clear(); world = w; } }

    static void add(Fx fx) {
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.removeFirst();
        EFFECTS.add(fx);
    }
    static double now(ClientWorld w) { return w.getTime() / 20.0; }

    private static void tick(MinecraftClient client) {
        if (client.world == null) { clear(); world = null; return; }
        sync(client.world);
        double now = now(client.world);
        // Spell entities start their own effects the first time this client sees them.
        for (Entity e : client.world.getEntities()) {
            if (e.isRemoved() || ATTACHED.containsKey(e.getId())) continue;
            Fx fx = null;
            if (e instanceof InfernoWaveEntity wave) fx = new InfernoFx(wave, now);
            else if (e instanceof FlashoverEmberEntity ember) fx = new EmberFx(ember, now);
            else if (e instanceof FallingBlockEntity block && block.getBlockState().isOf(ModSpellBlocks.METEOR_CORE)) fx = new MeteorFx.Ball(block, now);
            if (fx != null) { ATTACHED.put(e.getId(), fx); add(fx); }
        }
        FlameFx.tick(client.world, now);
    }

    /** Starts the take-off and flight of a leap; FireLeapClient forwards its leap packets here. */
    public static void leap(ClientWorld w, int entity, Vec3d from, Vec3d to, boolean active) {
        sync(w);
        if (active) add(new LeapFx.Flight(w, entity, from, to, now(w)));
        else LeapFx.ended(entity, now(w));
    }

    private static void start(ClientWorld w, ModNetworking.BlockFirePayload p) {
        double now = now(w);
        switch (p.kind()) {
            case BlockFireEvents.INFERNO_CAST -> add(new InfernoFx.Ignition(p.pos(), p.dir(), now));
            case BlockFireEvents.INFERNO_HIT -> add(new InfernoFx.Hit(p.pos(), p.dir(), now));
            case BlockFireEvents.INFERNO_BREAK -> add(new InfernoFx.Break(p.pos(), p.dir(), p.aux(), now));
            case BlockFireEvents.PYRE_CAST -> add(new PyreFx(w, p.entity(), p.pos(), p.dir(), now));
            case BlockFireEvents.PYRE_HIT -> add(new PyreFx.Hit(p.pos(), p.dir(), now));
            case BlockFireEvents.LEAP_LAND -> add(new LeapFx.Landing(w, p.entity(), p.pos(), now));
            case BlockFireEvents.FLASHOVER_POP -> add(new EmberFx.Blast(p.pos(), p.aux(), p.value(), now));
            case BlockFireEvents.METEOR_FALL -> {
                // Sent again every half second for late arrivals; one ring per meteor.
                if (!EFFECTS.contains(RINGS.get(p.entity()))) {
                    var ring = new MeteorFx.Ring(w, p.entity(), p.pos(), p.value(), now);
                    RINGS.put(p.entity(), ring);
                    add(ring);
                }
            }
            case BlockFireEvents.METEOR_IMPACT -> add(new MeteorFx.Impact(w, p.pos(), now));
            case BlockFireEvents.FLAME -> FlameFx.spray(w, p.entity(), p.value(), (int) p.aux().x, now);
            case BlockFireEvents.FLAME_OVERHEAT -> FlameFx.overheat(w, p.entity(), now);
            default -> {}
        }
    }

    // ---- Shared helpers ------------------------------------------------------------------------------

    /** The held wand tip as SpellCastVisuals estimates it: 0.75 forward, 0.38 toward the wand hand, 0.32 down. */
    static Vec3d tip(LivingEntity e, float delta) {
        Vec3d eye = e.getCameraPosVec(delta), look = e.getRotationVec(delta).normalize();
        float yaw = e.getYaw(delta) * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw)), up = right.crossProduct(look);
        boolean main = !(e.getOffHandStack().getItem() instanceof AbstractWandItem) || e.getMainHandStack().getItem() instanceof AbstractWandItem;
        Arm arm = main ? e.getMainArm() : e.getMainArm().getOpposite();
        return eye.add(look.multiply(.75)).add(right.multiply(.38 * (arm == Arm.RIGHT ? 1 : -1))).subtract(up.multiply(.32));
    }
    /** A stable per-event seed from where and when it happened, so two bursts never look identical. */
    static double seed(Vec3d p, double now) {
        return Math.floorMod((long) Math.floor(p.x * 31.7 + p.y * 7.3 + p.z * 17.9 + now * 20 * 3.1), 997) * 11.0;
    }
    /** True when the point is close enough to the camera to be worth drawing. */
    static boolean near(Frame f, Vec3d p, double reach) { return f.m().camera.squaredDistanceTo(p) < reach * reach; }
}
