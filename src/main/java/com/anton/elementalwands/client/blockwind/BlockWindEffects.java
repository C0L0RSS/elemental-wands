package com.anton.elementalwands.client.blockwind;

import com.anton.elementalwands.entity.GaleDaggerEntity;
import com.anton.elementalwands.entity.VacuumBladeEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.util.BlockWindEvents;
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
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;

/**
 * Client-only Wind spell visuals drawn as block wind (art/wind/blockwind). The server sends spell moments
 * (BlockWindEvents) and the spell entities carry their own state; nothing here decides a hit. Each frame every live
 * effect, plus the wings of anyone wearing Zephyr wings, adds its cubes to one mesh, which is drawn in two passes:
 * solid cubes, then see-through air sorted back to front.
 */
public final class BlockWindEffects {
    /** One frame's context. now is the world clock in seconds, including the partial tick. */
    record Frame(BlockWindMesh m, double now, float delta, ClientWorld world, Entity cameraEntity) {}
    /** A running effect; draw returns false once it has finished and can be dropped. */
    interface Fx { boolean draw(Frame f); }

    private static final RenderStateDataKey<BlockWindMesh> MESH = RenderStateDataKey.create(() -> "Block wind");
    private static final BlockWindMesh NONE = new BlockWindMesh(Vec3d.ZERO, false, false, 0, null);
    private static final int MAX_EFFECTS = 256;
    private static final List<Fx> EFFECTS = new ArrayList<>();
    /** Effects that follow a spell entity, by entity id, so each is started once. */
    private static final Map<Integer, Fx> ATTACHED = new HashMap<>();
    /** Projectile hits reported by the server (BlockWindEvents.HIT), by entity id; they arrive before its removal. */
    private static final Map<Integer, Vec3d> HITS = new java.util.LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Vec3d> eldest) { return size() > 64; }
    };
    private static ClientWorld world;

    private BlockWindEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.BlockWindPayload.ID, (payload, context) -> {
            var w = context.client().world;
            if (w == null) return;
            sync(w);
            start(w, payload);
        });
        ClientTickEvents.END_CLIENT_TICK.register(BlockWindEffects::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            ClientWorld w = context.world();
            if (w != world || (EFFECTS.isEmpty() && !ZephyrFx.anyWings(w))) { context.worldState().setData(MESH, NONE); return; }
            var client = MinecraftClient.getInstance();
            float delta = context.tickCounter().getTickProgress(false);
            double now = (w.getTime() + delta) / 20.0;
            boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
            boolean firstPerson = !context.camera().isThirdPerson();
            var mesh = new BlockWindMesh(context.camera().getPos(), firstPerson, embers, now, w);
            Frame frame = new Frame(mesh, now, delta, w, firstPerson ? context.camera().getFocusedEntity() : null);
            EFFECTS.removeIf(fx -> !fx.draw(frame));
            ATTACHED.values().removeIf(fx -> !EFFECTS.contains(fx));
            ZephyrFx.drawWings(frame);
            context.worldState().setData(MESH, mesh);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            BlockWindMesh mesh = context.worldState().getDataOrDefault(MESH, NONE);
            if (mesh.isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (mesh.hasSolid()) {
                mesh.emitSolid(consumers.getBuffer(BlockWindMesh.SOLID), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockWindMesh.SOLID);
            }
            if (mesh.hasAir()) {
                mesh.emitAir(consumers.getBuffer(BlockWindMesh.AIR), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockWindMesh.AIR);
            }
        });
    }

    private static void clear() { EFFECTS.clear(); ATTACHED.clear(); HITS.clear(); ZephyrFx.clear(); }
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
            if (e instanceof VacuumBladeEntity blade) fx = new ShearFx(blade, now);
            else if (e instanceof GaleDaggerEntity dagger) fx = new DaggerFx(dagger, now);
            if (fx != null) { ATTACHED.put(e.getId(), fx); add(fx); }
        }
    }

    private static void start(ClientWorld w, ModNetworking.BlockWindPayload p) {
        double now = now(w);
        switch (p.kind()) {
            case BlockWindEvents.DASH -> add(new DashFx(w, p.entity(), p.pos(), p.dir(), p.value() > 0, now));
            case BlockWindEvents.UPDRAFT -> add(new UpdraftFx(w, p.entity(), p.pos(), now));
            case BlockWindEvents.ZEPHYR_LAUNCH -> {
                add(new ZephyrFx.Launch(p.pos(), p.dir(), now));
                add(ZephyrFx.flight(w, p.entity(), now));
            }
            case BlockWindEvents.HIT -> HITS.put(p.entity(), p.pos());
            case BlockWindEvents.ZEPHYR_LAND -> {
                ZephyrFx.landed(p.entity());
                add(new ZephyrFx.Landing(p.pos(), p.value(), now));
            }
            default -> {}
        }
    }

    /** Where the server said this blade or dagger struck, or null if it simply ran out of range. */
    static Vec3d takeHit(int entity) { return HITS.remove(entity); }
    /** True when the point is close enough to the camera to be worth drawing. */
    static boolean near(Frame f, Vec3d p, double reach) { return f.m().camera.squaredDistanceTo(p) < reach * reach; }
}
