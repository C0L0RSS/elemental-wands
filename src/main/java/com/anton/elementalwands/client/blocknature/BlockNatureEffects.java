package com.anton.elementalwands.client.blocknature;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blocknature.BlockNatureKit.*;

import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.entity.SentryBloomEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.util.BlockNatureEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Client-only Nature spell visuals drawn as block nature (art/nature/blocknature). The server sends spell moments
 * (BlockNatureEvents); flower seeds in flight and wilting flowers are read from their entities. Nothing here decides a hit or
 * a heal. Each frame every live effect adds its opaque pieces to one mesh, drawn in a single cutout pass.
 */
public final class BlockNatureEffects {
    /** One frame's context. now is the world clock in seconds, including the partial tick. */
    record Frame(BlockNatureMesh m, double now, float delta, ClientWorld world) {}
    /** A running effect; draw returns false once it has finished and can be dropped. */
    interface Fx { boolean draw(Frame f); }

    private static final RenderStateDataKey<BlockNatureMesh> MESH = RenderStateDataKey.create(() -> "Block nature");
    private static final BlockNatureMesh NONE = new BlockNatureMesh(Vec3d.ZERO, false, false, 0, null);
    private static final int MAX_EFFECTS = 256;
    /** Effects are drawn only this close to the camera. */
    static final double REACH = 96;
    private static final List<Fx> EFFECTS = new ArrayList<>();
    /** Flower seed trails by entity id, so each seed starts one. */
    private static final Map<Integer, Fx> ATTACHED = new HashMap<>();
    /** Flower heads already seen wilting, so each sheds its pale petals once. */
    private static final Set<Integer> WILTED = new HashSet<>();
    private static ClientWorld world;

    private BlockNatureEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.BlockNaturePayload.ID, (payload, context) -> {
            var w = context.client().world;
            if (w == null) return;
            sync(w);
            start(w, payload);
        });
        ClientTickEvents.END_CLIENT_TICK.register(BlockNatureEffects::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            ClientWorld w = context.world();
            if (w != world || EFFECTS.isEmpty()) { context.worldState().setData(MESH, NONE); return; }
            var client = MinecraftClient.getInstance();
            float delta = context.tickCounter().getTickProgress(false);
            double now = (w.getTime() + delta) / 20.0;
            boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
            var mesh = new BlockNatureMesh(context.camera().getPos(), !context.camera().isThirdPerson(), embers, now, w);
            Frame frame = new Frame(mesh, now, delta, w);
            EFFECTS.removeIf(fx -> !fx.draw(frame));
            ATTACHED.values().removeIf(fx -> !EFFECTS.contains(fx));
            context.worldState().setData(MESH, mesh);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            BlockNatureMesh mesh = context.worldState().getDataOrDefault(MESH, NONE);
            if (mesh.isEmpty()) return;
            var consumers = context.consumers();
            mesh.emit(consumers.getBuffer(BlockNatureMesh.SOLID), context.matrices().peek());
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockNatureMesh.SOLID);
        });
    }

    private static void clear() { EFFECTS.clear(); ATTACHED.clear(); WILTED.clear(); }
    private static void sync(ClientWorld w) { if (w != world) { clear(); world = w; } }

    static void add(Fx fx) {
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.removeFirst();
        EFFECTS.add(fx);
    }
    static double now(ClientWorld w) { return w.getTime() / 20.0; }
    static boolean near(Frame f, Vec3d p, double reach) { return f.m().camera.squaredDistanceTo(p) < reach * reach; }

    private static void tick(MinecraftClient client) {
        if (client.world == null) { clear(); world = null; return; }
        sync(client.world);
        double now = now(client.world);
        Set<Integer> seen = new HashSet<>();
        for (Entity e : client.world.getEntities()) {
            if (e.isRemoved()) continue;
            if (e instanceof SeedProjectileEntity seed) {
                // Only a flower's small seed leaves a trail; the caster's Seed flies clean.
                if (seed.fromFlower() && !ATTACHED.containsKey(e.getId())) {
                    Fx fx = new NatureFx.SeedTrail(seed);
                    ATTACHED.put(e.getId(), fx); add(fx);
                }
            } else if (e instanceof SentryBloomEntity head) {
                seen.add(e.getId());
                // A flower's last two seconds: a few pale petals fall from the drooping head.
                if (head.wilting() && WILTED.add(e.getId()))
                    add(new NatureFx.Pop(head.pivot(), now, seed(head.pivot()), new Burst(6, .3, .8, 2, PETAL, CREAM, true)));
            }
        }
        WILTED.retainAll(seen);
    }

    private static double seed(Vec3d p) { return Math.floor(rnd(p.x * 3.1 + p.y * 1.7 + p.z * 7.3) * 64); }

    private static void start(ClientWorld w, ModNetworking.BlockNaturePayload p) {
        double now = now(w), seed = seed(p.pos());
        Vec3d at = p.pos();
        switch (p.kind()) {
            case BlockNatureEvents.PLANT -> pop(at.add(0, .7, 0), now, seed, new Burst(8, .28, .65, .7, LEAF_PIECE, LEAF, false));
            case BlockNatureEvents.GROW -> pop(at.add(0, 1, 0), now, seed, Burst.petals(12, .32, .9));
            case BlockNatureEvents.SHRINK -> pop(at.add(0, .9, 0), now, seed, Burst.petals(8, .3, .8).falling());
            case BlockNatureEvents.WILT -> pop(at.add(0, .5, 0), now, seed, Burst.petals(10, .3, .8).falling());
            case BlockNatureEvents.SHOT -> {
                pop(at, now, seed, new Burst(4, .19, .18, .3, POLLEN, GOLD, false));
                pop(at.add(0, .1, 0), now, seed + 5, new Burst(2, .22, .2, .35, PETAL, PINK, false));
            }
            case BlockNatureEvents.SEED_HIT -> pop(at, now, seed, p.value() > 0 ? Burst.petals(5, .2, .3) : Burst.petals(12, .32, .65));
            case BlockNatureEvents.SEED_BLOCK -> {
                pop(at, now, seed, new Burst(6, .3, .4, .7, POLLEN, GOLD, false));
                pop(at, now, seed + 9, new Burst(4, .24, .4, .7, LEAF_PIECE, LEAF, false));
            }
            case BlockNatureEvents.HEAL -> add(new NatureFx.Heal(w.getEntityById(p.entity()), at, now));
            case BlockNatureEvents.BITE -> pop(at, now, seed, new Burst(5, .17, .2, .35, POLLEN, GOLD, false));
            case BlockNatureEvents.FULL_BLOOM -> add(new NatureFx.Bloom(at, p.value(), w.getEntityById(p.entity()), now));
            case BlockNatureEvents.SPRING_LAUNCH -> {
                pop(at.add(0, .11, 0), now, seed, new Burst(10, .25, .65, .7, POLLEN, GOLD, false));
                pop(at.add(0, .01, 0), now, seed + 13, new Burst(8, .34, .95, .7, LEAF_PIECE, LEAF, false));
                add(new NatureFx.Flight(w.getEntityById(p.entity()), now));
            }
            case BlockNatureEvents.SPRING_END -> pop(at.add(0, .15, 0), now, seed, new Burst(14, .3, .65, .7, LEAF_PIECE, DARKLEAF, false));
            case BlockNatureEvents.POD_HIT -> pop(at.add(0, .1, 0), now, seed, new Burst(6, .23, .2, .7, LEAF_PIECE, LEAF, false));
            default -> {}
        }
    }

    private static void pop(Vec3d at, double now, double seed, Burst look) { add(new NatureFx.Pop(at, now, seed, look)); }
}
