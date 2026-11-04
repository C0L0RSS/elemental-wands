package com.anton.elementalwands.client.blockspace;

import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.AstralOrbEntity;
import com.anton.elementalwands.entity.GravityBombEntity;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.util.BlockSpaceEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Client-only Space spell visuals drawn as void glass (art/space/blockspace). The server sends spell moments
 * (BlockSpaceEvents) and the spell entities carry their own state; nothing here decides a hit or a teleport. Each frame
 * every live effect adds its pieces to one mesh, drawn in two passes: solid pieces, then see-through glass sorted back
 * to front.
 */
public final class BlockSpaceEffects {
    /** One frame's context. now is the world clock in seconds, including the partial tick. */
    record Frame(BlockSpaceMesh m, double now, float delta, ClientWorld world, Entity cameraEntity) {}
    /** A running effect; draw returns false once it has finished and can be dropped. */
    interface Fx { boolean draw(Frame f); }

    private static final RenderStateDataKey<BlockSpaceMesh> MESH = RenderStateDataKey.create(() -> "Block space");
    private static final BlockSpaceMesh NONE = new BlockSpaceMesh(Vec3d.ZERO, false, false, 0, null);
    private static final int MAX_EFFECTS = 256;
    private static final List<Fx> EFFECTS = new ArrayList<>();
    /** Effects that follow a spell entity, by entity id, so each is started once. */
    private static final Map<Integer, Fx> ATTACHED = new HashMap<>();
    /** Bolt hits reported by the server (BOLT_HIT), by entity id: {x, y, z, strength}. They arrive before its removal. */
    private static final Map<Integer, double[]> HITS = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, double[]> eldest) { return size() > 64; }
    };
    /** Hollow Purple collapses reported by the server, by orb id. */
    private static final Map<Integer, Vec3d> COLLAPSES = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Vec3d> eldest) { return size() > 16; }
    };
    /** Running Hollow Purple charges, by caster id, so CHARGE_END can stop one. */
    private static final Map<Integer, PurpleFx.Charge> CHARGES = new HashMap<>();
    private static ClientWorld world;

    private BlockSpaceEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.BlockSpacePayload.ID, (payload, context) -> {
            var w = context.client().world;
            if (w == null) return;
            sync(w);
            start(w, payload);
        });
        ClientTickEvents.END_CLIENT_TICK.register(BlockSpaceEffects::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            ClientWorld w = context.world();
            if (w != world || EFFECTS.isEmpty()) { context.worldState().setData(MESH, NONE); return; }
            var client = MinecraftClient.getInstance();
            float delta = context.tickCounter().getTickProgress(false);
            double now = (w.getTime() + delta) / 20.0;
            boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
            boolean firstPerson = !context.camera().isThirdPerson();
            var mesh = new BlockSpaceMesh(context.camera().getPos(), firstPerson, embers, now, w);
            Frame frame = new Frame(mesh, now, delta, w, firstPerson ? context.camera().getFocusedEntity() : null);
            EFFECTS.removeIf(fx -> !fx.draw(frame));
            ATTACHED.values().removeIf(fx -> !EFFECTS.contains(fx));
            CHARGES.values().removeIf(fx -> !EFFECTS.contains(fx));
            context.worldState().setData(MESH, mesh);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            BlockSpaceMesh mesh = context.worldState().getDataOrDefault(MESH, NONE);
            if (mesh.isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (mesh.hasSolid()) {
                mesh.emitSolid(consumers.getBuffer(BlockSpaceMesh.SOLID), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockSpaceMesh.SOLID);
            }
            if (mesh.hasGlass()) {
                mesh.emitGlass(consumers.getBuffer(BlockSpaceMesh.GLASS), entry);
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockSpaceMesh.GLASS);
            }
        });
    }

    private static void clear() { EFFECTS.clear(); ATTACHED.clear(); HITS.clear(); COLLAPSES.clear(); CHARGES.clear(); }
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
            if (e instanceof SingularityBoltEntity bolt) fx = new BoltFx(client.world, bolt, now);
            else if (e instanceof HollowPurpleOrbEntity orb) fx = new PurpleFx.Orb(orb, now);
            else if (e instanceof GravityBombEntity bomb) fx = new WellFx(bomb, now);
            else if (e instanceof AstralOrbEntity orb) fx = new AstralFx.Orb(orb);
            else if (e instanceof AstralDoubleEntity copy) fx = new AstralFx.Copy(copy, now);
            if (fx != null) { ATTACHED.put(e.getId(), fx); add(fx); }
        }
    }

    private static void start(ClientWorld w, ModNetworking.BlockSpacePayload p) {
        double now = now(w);
        switch (p.kind()) {
            case BlockSpaceEvents.BOLT_HIT -> HITS.put(p.entity(), new double[]{p.pos().x, p.pos().y, p.pos().z, p.value()});
            case BlockSpaceEvents.BLINK -> add(new BlinkFx(w, p.entity(), p.pos(), p.dir(), now));
            case BlockSpaceEvents.BLINK_FAIL -> add(new BlinkFx.Fail(p.pos(), p.dir(), now));
            case BlockSpaceEvents.POOF -> add(new AstralFx.Poof(p.pos(), now));
            case BlockSpaceEvents.CHARGE -> {
                var charge = new PurpleFx.Charge(w, p.entity(), now);
                PurpleFx.Charge old = CHARGES.put(p.entity(), charge);
                if (old != null) old.stop();
                add(charge);
            }
            case BlockSpaceEvents.CHARGE_END -> { PurpleFx.Charge c = CHARGES.remove(p.entity()); if (c != null) c.stop(); }
            case BlockSpaceEvents.COLLAPSE -> COLLAPSES.put(p.entity(), p.pos());
            default -> {}
        }
    }

    /** Where the server said this bolt struck ({x, y, z, strength}), or null if it simply ran out of range. */
    static double[] takeHit(int entity) { return HITS.remove(entity); }
    /** Where the server said this Hollow Purple orb began its collapse, or null. */
    static Vec3d takeCollapse(int entity) { return COLLAPSES.remove(entity); }
    /** True while this client draws a void-glass effect for the entity (client fixtures check it). */
    public static boolean isDrawing(int entity) { return ATTACHED.containsKey(entity); }
    /** True when the point is close enough to the camera to be worth drawing. */
    static boolean near(Frame f, Vec3d p, double reach) { return f.m().camera.squaredDistanceTo(p) < reach * reach; }
}
