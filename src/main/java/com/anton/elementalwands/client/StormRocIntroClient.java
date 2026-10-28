package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.mixin.ClientChunkProgressAccessor;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.summit.StormRocIntroTrack;
import com.anton.elementalwands.summit.StormSummit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

/**
 * Plays the Storm Roc's intro on this client (the server side is summit.StormRocIntro). The scene
 * runs on its own clock, in game ticks, so it can wait: when it reaches the hold beat inside the
 * storm cloud it stays there until the summit realm has loaded, with an opaque storm cloud drawn
 * over everything. The move into the realm happens behind that cloud, and the vanilla loading
 * screen is never opened for it (mixin.StormRocIntroLoadingMixin). The first half is laid out from
 * where the player struck the gong, facing it; the second from the summit slot's centre. Both
 * actors are drawn without entities by {@link StormRocIntroPuppets}.
 */
public final class StormRocIntroClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 10, TITLE_FADE = 8;
    /** How long the cloud covers the screen before the client tells the server it may move the player. */
    private static final int COVER_IN = 6;
    /**
     * After this long in the realm the hold stops waiting for sections to build (a renderer that
     * reports them differently must not keep the player in the cloud until the server gives up);
     * the summit's chunks must still have arrived.
     */
    private static final int BUILD_PATIENCE = 200;
    /**
     * Once the summit is ready, the cloud holds this much longer with the camera where the summit's
     * first shot opens, so what that shot sees is built too before the cloud clears.
     */
    private static final int MIN_HOLD = 10;
    private static final double BARS = .11;
    private static final Identifier CLOUD_TEXTURE = Identifier.of("elementalwands", "textures/misc/storm_roc_intro_cloud.png");

    /** A skip hands back with a cut, not a glide, from a shot farther than this from the player's eyes. */
    private static final double CUT_BACK = 48;

    private static boolean active, finished, skipped, skippedOverSummit, cutBack, coveredSent, landedSent, holding;
    private static int hold, heldFor, readyFor, inRealmFor, sinceSkip, backFrom, ticks, nextCue, nextFlash;
    /**
     * Client ticks the scene's Roc stays on its perch after a finished scene ends, until the fight's
     * own Roc arrives: the server ends the scene a moment before that Roc reaches this client.
     */
    private static final int HANDOFF = 40;
    private static int handoff;
    /** Asked the server to move it into the realm: the loading screen for that one move is skipped. */
    private static boolean armed;
    private static double time, lastTime, coverAtSkip;
    private static Vec3d aerie = Vec3d.ZERO;
    private static float aerieYaw;
    private static BlockPos summit = BlockPos.ORIGIN;
    private static boolean hudWasHidden;
    private static final Random RANDOM = Random.create();

    private StormRocIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.StormRocIntroPayload.ID, (payload, context) -> {
            if (!payload.active()) { stop(context.client()); return; }
            start(context.client(), payload.aerie(), payload.aerieYaw(), payload.summit());
        });
        // Clicks during the scene would swing, use or cast at nothing: drained before vanilla handles input.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!active) return;
            while (client.options.attackKey.wasPressed()) {}
            while (client.options.useKey.wasPressed()) {}
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(StormRocIntroClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
        // The move into the realm stops every sound: the storm's bed comes back in the new world.
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            handoff = 0;
            if (active && !finished) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_ELYTRA_FLYING, .7f, .5f));
        });
        StormRocIntroPuppets.init();
    }

    private static void start(MinecraftClient client, Vec3d at, float yaw, BlockPos centre) {
        StormRocIntroTrack.get();
        active = true;
        finished = skipped = skippedOverSummit = cutBack = coveredSent = landedSent = holding = false;
        hold = heldFor = readyFor = inRealmFor = sinceSkip = ticks = nextCue = nextFlash = 0;
        backFrom = -1;
        time = lastTime = 0;
        aerie = at; aerieYaw = yaw; summit = centre;
        armed = false;
        handoff = 0;
        hudWasHidden = client.options.hudHidden;
        StormRocIntroPuppets.capture(client);
    }

    private static void stop(MinecraftClient client) {
        if (!active) return;
        active = false;
        armed = false;
        handoff = finished && inRealm(client) ? HANDOFF : 0;
        // A finished scene already gave the HUD back; the player may have toggled it since.
        if (!finished) client.options.hudHidden = hudWasHidden;
        StormRocIntroPuppets.release();
    }

    // ------------------------------------------------------------------ the clock

    private static StormRocIntroTrack track() { return StormRocIntroTrack.get(); }

    private static void tick(MinecraftClient client) {
        if (!active) { if (handoff > 0) handoff--; return; }
        if (client.world == null) return;
        ticks++;
        // The letterbox and cloud are drawn with the HUD, so it stays on while the scene owns the view.
        if (cinematic()) client.options.hudHidden = false;
        if (finished) return;
        StormRocIntroTrack track = track();
        lastTime = time;
        if (inRealm(client)) inRealmFor++;
        if (skipped) {
            sinceSkip++;
            if (backFrom < 0 && summitReady(client)) backFrom = sinceSkip;
        } else time = advance(client, track);
        if (!skipped && cinematicTime() > 10) {
            hold = client.options.sneakKey.isPressed() ? hold + 1 : Math.max(0, hold - 2);
            if (hold >= SKIP_HOLD) {
                coverAtSkip = cover(0);
                skipped = true;
                skippedOverSummit = time >= track.beat("summit");
                // Past the hold the summit is already loaded: the view glides back from a shot near
                // the player, and cuts from one out over the clouds.
                if (skippedOverSummit) {
                    backFrom = 0;
                    cutBack = client.player != null && shot(client, time).pos().distanceTo(client.player.getCameraPosVec(1)) > CUT_BACK;
                }
                ClientPlayNetworking.send(ModNetworking.StormRocIntroSkipPayload.INSTANCE);
            }
        }
        // Covered by the cloud (in the scene, or by a skip): the server may move the player now.
        if (!coveredSent && cover(0) >= 1) {
            coveredSent = true;
            armed = true;
            ClientPlayNetworking.send(ModNetworking.StormRocIntroCloudPayload.INSTANCE);
        }
        if (!skipped) { cues(client, track); effects(client, track); }
        // Landed: played through the hand-back, or skipped and safely on the loaded summit.
        boolean done = skipped ? backFrom >= 0 && (cutBack || sinceSkip >= backFrom + HAND_BACK) : time >= track.length();
        if (done && !landedSent) {
            landedSent = true;
            finished = true;
            ClientPlayNetworking.send(ModNetworking.StormRocIntroLandedPayload.INSTANCE);
            client.options.hudHidden = hudWasHidden;
        }
    }

    /**
     * The next tick of scene time: one on, except at the hold beat inside the cloud, where it waits
     * for the server to move the player and for the summit to load and draw, then MIN_HOLD more.
     * It never gives up on its own: a scene stuck here is ended by the server, which sends the
     * player home.
     */
    private static double advance(MinecraftClient client, StormRocIntroTrack track) {
        if (time >= track.beat("hold") && time < track.beat("summit")) {
            if (inRealm(client)) heldFor++;
            // Once ready it stays so: the camera then leaves for the summit's first shot, far enough
            // off that the world's grid of built sections, centred on the camera, lets the arrival go.
            if (readyFor > 0 || summitReady(client)) readyFor++;
            if (readyFor <= MIN_HOLD) { holding = true; return time; }
        }
        holding = false;
        return Math.min(time + 1, track.length());
    }

    /** The client is in the summit realm, as the player the server moved. */
    private static boolean inRealm(MinecraftClient client) {
        return client.world != null && client.world.getRegistryKey() == StormSummit.WORLD && client.player != null
                && client.player.getEntityWorld() == client.world;
    }

    /**
     * The summit has arrived and drawn: the player stands at the arrival in the new world, vanilla's
     * own loading is done, and the chunks the next shots look at are loaded and built.
     */
    private static boolean summitReady(MinecraftClient client) { return waitingFor(client) == null; }

    /** What the hold is still waiting for, or null once the summit is ready. */
    static String waitingFor(MinecraftClient client) {
        if (!inRealm(client) || client.getNetworkHandler() == null) return "the move into the realm";
        if (((ClientChunkProgressAccessor) client.getNetworkHandler()).elementalwands$chunkLoadProgress() != null) return "vanilla's chunk loading";
        Vec3d arrival = StormSummit.arrival(summit);
        if (client.player.getEntityPos().squaredDistanceTo(arrival) > 16) return "the player at the arrival (at " + client.player.getEntityPos() + ")";
        // What vanilla loads and builds: the client's render distance, capped by the server's.
        int view = client.options.getClampedViewDistance();
        int reach = Math.min(2, Math.max(0, view - 1));
        int cx = MathHelper.floor(arrival.x) >> 4, cz = MathHelper.floor(arrival.z) >> 4;
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++)
            if (!client.world.isChunkLoaded(cx + x, cz + z)) return "chunk " + (cx + x) + "," + (cz + z) + " near the arrival";
        // What the summit's shots look at: where you land, the middle of the summit, the home spire;
        // each only if the client's view distance reaches it at all.
        StormRocIntroTrack track = track();
        for (Vec3d at : new Vec3d[]{arrival, place(1, Vec3d.ZERO).add(.5, 1, .5), place(1, track.homePerch()).add(0, -1, 0)}) {
            BlockPos pos = BlockPos.ofFloored(at);
            if (Math.max(Math.abs((pos.getX() >> 4) - cx), Math.abs((pos.getZ() >> 4) - cz)) > view - 1) continue;
            if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return "the chunk at " + pos.toShortString();
            if (inRealmFor < BUILD_PATIENCE && !client.worldRenderer.isRenderingReady(pos)) return "the section at " + pos.toShortString() + " to build";
        }
        return null;
    }

    /** Scene time at this frame. */
    static double time(float tickDelta) { return MathHelper.lerp(tickDelta, lastTime, time); }

    /** Real ticks since the start, for the skip prompt. */
    private static int cinematicTime() { return ticks; }

    // ------------------------------------------------------------------ the camera

    /** The player is held by the scene: no walking, jumping or sneaking. */
    public static boolean frozen() { return active; }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return active && !finished && handBack(0) < 1; }

    /** Hiding the vanilla loading screen for the move into the summit realm this client asked for. */
    public static boolean coversLoading(net.minecraft.registry.RegistryKey<net.minecraft.world.World> into) {
        return active && armed && into == StormSummit.WORLD;
    }

    /** The loading screen this client skipped for its move into the realm: never again until asked. */
    public static void loadingSkipped() { armed = false; }

    /** Players are hidden while the scene plays (each sees only their own copy), and the fight's own Roc until the scene's has handed over. */
    public static boolean hides(Entity entity) {
        if (!active) return false;
        if (entity instanceof PlayerEntity) return !finished;
        return entity instanceof StormRocEntity && StormRocIntroPuppets.rocShown();
    }

    /** 0 while the scene owns the camera, rising to 1 as a skip hands the view back once the summit has loaded. */
    private static double handBack(float tickDelta) {
        if (!skipped) return time(tickDelta) >= track().length() ? 1 : 0;
        if (backFrom < 0) return 0;
        if (cutBack) return 1;
        return smooth((sinceSkip - backFrom + tickDelta) / HAND_BACK);
    }

    public static BossIntroCamera.Pose pose(float tickDelta) {
        var client = MinecraftClient.getInstance();
        if (!active || finished || client.player == null) return null;
        double back = handBack(tickDelta);
        if (back >= 1) return null;
        BossIntroCamera.Pose shot = shot(client, time(tickDelta));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(client.player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float)back, shot.yaw(), client.player.getYaw(tickDelta)),
                MathHelper.lerp((float)back, shot.pitch(), client.player.getPitch(tickDelta)), (float)(shot.roll() * (1 - back)));
    }

    /**
     * The shot at scene time t in this world. The world only builds what the camera sees, so while
     * the cloud covers the screen in the realm the camera waits in the player's eyes, where vanilla's
     * own loading and the summit's checks look, until the summit is ready (for good after a skip
     * from before the summit, so handing back moves nothing); then where the summit's first shot
     * opens, so that is built too.
     */
    private static BossIntroCamera.Pose shot(MinecraftClient client, double t) {
        StormRocIntroTrack track = track();
        boolean realm = client.world != null && client.world.getRegistryKey() == StormSummit.WORLD;
        if (realm && client.player != null && (skipped ? !skippedOverSummit : track.half(t) == 0 && readyFor == 0))
            return new BossIntroCamera.Pose(client.player.getCameraPosVec(1), client.player.getYaw(), client.player.getPitch(), 0);
        if (realm && (track.half(t) == 0 || skipped)) t = Math.max(t, track.beat("summit"));
        if (!realm && track.half(t) == 1) t = track.beat("summit") - .001;
        return new BossIntroCamera.Pose(place(track.half(t), track.eye(t)), track.yaw(t) + frameYaw(track.half(t)), track.pitch(t), track.roll(t));
    }

    public static float fov(float fov, float tickDelta) {
        if (!active || finished) return fov;
        return (float)MathHelper.lerp(handBack(tickDelta), track().fov(time(tickDelta)), fov);
    }

    /** A point in the scene's frame for this half, in the world. */
    static Vec3d place(int half, Vec3d local) {
        if (half == 1) return new Vec3d(summit.getX() + local.x, summit.getY() + local.y, summit.getZ() + local.z);
        double yaw = Math.toRadians(aerieYaw);
        Vec3d ahead = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), left = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        return aerie.add(left.multiply(local.x)).add(0, local.y, 0).add(ahead.multiply(local.z));
    }

    static float frameYaw(int half) { return half == 1 ? 0 : aerieYaw; }

    /** A rotation in the scene's frame for this half, in the world. */
    static Quaternionf turn(int half, Quaternionf local) {
        return new Quaternionf().rotationY((float)Math.toRadians(-frameYaw(half))).mul(local);
    }

    /** The half the actors are drawn in at time t: the world the client is in decides. */
    static int drawnHalf(double t) {
        var client = MinecraftClient.getInstance();
        boolean realm = client.world != null && client.world.getRegistryKey() == StormSummit.WORLD;
        return realm ? 1 : 0;
    }

    static boolean active() { return active; }
    /** The scene is over, but its Roc still holds its perch until the fight's own appears. */
    static boolean handingOver() { return !active && handoff > 0; }
    /** Ticks the scene has waited in the cloud since the move into the realm (for the client fixture). */
    static int heldFor() { return heldFor; }

    /** Paused in the cloud, waiting for the summit. */
    static boolean holding() { return holding; }
    static boolean finished() { return finished; }
    static boolean skipped() { return skipped; }

    // ------------------------------------------------------------------ the cloud, sounds, light

    /** How opaque the storm cloud over the screen is: building as the camera enters it, solid while the move and the load happen, gone at the cut. */
    static double cover(float tickDelta) {
        if (!active || finished) return 0;
        StormRocIntroTrack track = track();
        // A skip on the summit just hands back; one before it closes the cloud over the move.
        if (skipped) return skippedOverSummit ? 0 : Math.max(coverAtSkip, Math.min(1, (sinceSkip + tickDelta) / COVER_IN)) * (1 - handBack(tickDelta));
        double t = time(tickDelta), cloud = track.beat("cloud");
        if (t >= track.beat("summit")) return 0;
        return MathHelper.clamp((t - (cloud - 4)) / (4 + COVER_IN), 0, 1);
    }

    private static void cues(MinecraftClient client, StormRocIntroTrack track) {
        var cues = track.cues();
        while (nextCue < cues.size() && cues.get(nextCue).tick() <= time) {
            var cue = cues.get(nextCue++);
            if (cue.tick() < lastTime) continue;
            play(client, track, cue);
        }
    }

    private static void play(MinecraftClient client, StormRocIntroTrack track, StormRocIntroTrack.Cue cue) {
        double t = cue.tick();
        int half = drawnHalf(t);
        Vec3d at = switch (cue.from()) {
            case "roc_head" -> place(half, track.rocHead(t));
            case "roc" -> place(half, track.rocPos(t));
            case "player", "hand" -> place(half, track.half(t) == half ? track.playerPos(t) : track.eye(t));
            case "point" -> place(half, cue.at());
            case "gong" -> place(0, new Vec3d(0, 3, 4));
            default -> client.gameRenderer.getCamera().getPos();
        };
        record Sound(SoundEvent event, float volume, float pitch) {}
        Sound sound = switch (cue.cue()) {
            case "wind_rise", "wind_rush", "sky_open", "fall_wind" -> new Sound(SoundEvents.ITEM_ELYTRA_FLYING, .6f, cue.cue().equals("fall_wind") ? 1.3f : .8f);
            case "caw" -> new Sound(SoundEvents.ENTITY_PHANTOM_AMBIENT, 6f, .45f);
            case "wings_rush", "wingbeat" -> new Sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3f, .55f + RANDOM.nextFloat() * .1f);
            case "snatch" -> new Sound(SoundEvents.ENTITY_PLAYER_HURT, 1f, .8f);
            case "scream", "shriek" -> new Sound(SoundEvents.ENTITY_PHANTOM_DEATH, 6f, .4f);
            case "cloud_roar", "thunder" -> new Sound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, .7f + RANDOM.nextFloat() * .2f);
            case "crackle" -> new Sound(SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, .8f, 1.6f);
            case "struggle" -> new Sound(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 1f, 1f);
            case "wand_strike" -> new Sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, 1f);
            case "fling" -> new Sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, .6f);
            case "crash" -> new Sound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.2f, .7f);
            case "roc_lands" -> new Sound(SoundEvents.ENTITY_RAVAGER_STEP, 3f, .5f);
            default -> null;
        };
        if (sound == null) return;
        client.getSoundManager().play(new PositionedSoundInstance(sound.event(), SoundCategory.HOSTILE, sound.volume(), sound.pitch(), RANDOM, at.x, at.y, at.z));
    }

    /** Snow on the wind at the aerie, sparks round the Roc's head, and the summit's bolts. */
    private static void effects(MinecraftClient client, StormRocIntroTrack track) {
        int half = drawnHalf(time);
        if (half != track.half(time)) return;
        Vec3d eye = place(half, track.eye(time));
        if (half == 0 && time < track.beat("cloud")) {
            double yaw = Math.toRadians(aerieYaw);
            Vec3d wind = new Vec3d(Math.cos(yaw), -.05, Math.sin(yaw)).multiply(.9);
            for (int i = 0; i < 10; i++) {
                Vec3d at = eye.add((RANDOM.nextDouble() - .5) * 18, (RANDOM.nextDouble() - .5) * 10, (RANDOM.nextDouble() - .5) * 18).subtract(wind.multiply(6));
                client.world.addParticleClient(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, wind.x, wind.y, wind.z);
            }
        }
        var strikes = track.lightning();
        while (nextFlash < strikes.size() && strikes.get(nextFlash).tick() <= time) {
            var strike = strikes.get(nextFlash++);
            if (strike.tick() < lastTime || strike.half() != half) continue;
            switch (strike.kind()) {
                case "crackle" -> {
                    Vec3d head = place(half, track.rocHead(strike.tick()));
                    for (int i = 0; i < 24; i++)
                        client.world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, true, true, head.x + (RANDOM.nextDouble() - .5) * 4,
                                head.y + (RANDOM.nextDouble() - .5) * 3, head.z + (RANDOM.nextDouble() - .5) * 4, 0, 0, 0);
                }
                case "bolt" -> {
                    Vec3d at = place(half, strike.at());
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(client.world, SpawnReason.TRIGGERED);
                    if (bolt == null) break;
                    bolt.setCosmetic(true);
                    bolt.setId(-4200 - nextFlash);
                    bolt.refreshPositionAndAngles(at.x, StormSummit.CLOUD_Y, at.z, 0, 0);
                    client.world.addEntity(bolt);
                    client.world.setLightningTicksLeft(2);
                }
                default -> client.world.setLightningTicksLeft(2);
            }
        }
    }

    // ------------------------------------------------------------------ the screen

    /** Letterbox, the storm cloud, the wing shadow, lightning and the wand's flash, the title and skip prompt; in place of the HUD. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        StormRocIntroTrack track = track();
        float delta = counter.getTickProgress(false);
        double t = time(delta), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        boolean aerieShot = track.half(t) == 0 && drawnHalf(t) == 0;
        if (aerieShot && !skipped) {
            double dim = track.dim(t);
            if (dim > .01) context.fill(0, 0, w, h, alpha(dim * .6) | 0x05070C);
            double shade = track.shade(t);
            if (shade > .01) {
                int y = (int)((track.shadeAcross(t) * 1.8 - .5) * h), band = (int)(h * .45);
                context.fillGradient(0, y - band, w, y, 0x00000000, alpha(Math.min(.85, shade * 1.5)) | 0x04060A);
                context.fillGradient(0, y, w, y + band, alpha(Math.min(.85, shade * 1.5)) | 0x04060A, 0x00000000);
            }
        }
        double flash = track.flash(t) * (1 - back);
        if (flash > .01 && !skipped) context.fill(0, 0, w, h, alpha(.35 * flash) | 0xD6E4FF);
        double wand = track.wand(t) * (1 - back);
        if (wand > .01 && !skipped) context.fill(0, 0, w, h, alpha(.3 * wand) | StormRocIntroPuppets.elementColour());
        drawCover(context, delta);
        int bar = (int)Math.round(h * BARS * (skipped ? 1 - back : track.bars(t)));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        if (!skipped) title(context, client, t, w, h, bar);
        if (!skipped && cinematicTime() > 10 && back <= 0) {
            Text hint = Text.translatable("guardian.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
            int x = w - client.textRenderer.getWidth(hint) - 10, y = h - Math.max(bar, 0) - 16;
            // In at the start; out while the Roc's name shows below it, and with the letterbox as the
            // view glides back into the player's eyes.
            double shown = smooth((cinematicTime() - 10) / 10.0) * (1 - titleShown(t)) * (t >= track.beat("back") ? track.bars(t) : 1);
            context.drawText(client.textRenderer, hint, x, y, alpha(shown * .75) | 0xFFFFFF, true);
            if (hold > 0) context.fill(x, y + 10, x + client.textRenderer.getWidth(hint) * Math.min(hold, SKIP_HOLD) / SKIP_HOLD, y + 11, alpha(shown) | 0xFF6040);
        }
    }

    /** The inside of the storm cloud: grey billows streaming past, lit from within by lightning. */
    static void drawCover(DrawContext context, float delta) {
        double cover = cover(delta);
        if (cover <= .001) return;
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        double seconds = (ticks + delta) / 20.0, flash = Math.max(track().flash(time(delta)), lightning(delta));
        int base = (int)MathHelper.lerp(flash, 0x52, 0xB8);
        context.fill(0, 0, w, h, alpha(cover) | base << 16 | (base + 3) << 8 | (base + 10));
        // Two layers of billows at different sizes and speeds, the near one rushing past.
        layer(context, w, h, seconds * 34, seconds * -60, 1.6, cover * .75, flash);
        layer(context, w, h, seconds * -18, seconds * -26, 2.6, cover * .55, flash);
    }

    private static void layer(DrawContext context, int w, int h, double dx, double dy, double size, double opacity, double flash) {
        int tile = (int)(Math.max(w, h) / size);
        int ox = Math.floorMod((int)dx, tile), oy = Math.floorMod((int)dy, tile);
        int shade = (int)MathHelper.lerp(flash, 0xC8, 0xFF);
        int colour = alpha(opacity) | shade << 16 | shade << 8 | Math.min(255, shade + 12);
        for (int x = -tile + ox; x < w; x += tile)
            for (int y = -tile + oy; y < h; y += tile)
                context.drawTexture(RenderPipelines.GUI_TEXTURED, CLOUD_TEXTURE, x, y, 0, 0, tile, tile, 256, 256, 256, 256, colour);
    }

    /** Lightning inside the cloud while it waits for the summit: a flicker every second or so. */
    private static double lightning(float delta) {
        if (!holding) return 0;
        int phase = Math.floorMod(ticks * 7919, 23);
        return phase < 3 ? .6 * (1 - phase / 3.0) : 0;
    }

    /** How far the Roc's name has faded in, 0 to 1. */
    private static double titleShown(double t) {
        StormRocIntroTrack track = track();
        return smooth((t - track.beat("title")) / TITLE_FADE) * (1 - smooth((t - (track.beat("title_end") - TITLE_FADE)) / TITLE_FADE));
    }

    /** The Roc's name under it in the landing insert, just above the letterbox, clear of its head and wings. */
    private static void title(DrawContext context, MinecraftClient client, double t, int w, int h, int bar) {
        StormRocIntroTrack track = track();
        double enter = smooth((t - track.beat("title")) / TITLE_FADE), title = titleShown(t);
        if (title <= 0) return;
        Text name = Text.translatable("entity.elementalwands.storm_roc");
        String upper = name.getString().toUpperCase(java.util.Locale.ROOT);
        int width = client.textRenderer.getWidth(upper);
        float scale = Math.min(2.6f, (w - 48f) / Math.max(1, width));
        float centreY = h - bar - 18 - (client.textRenderer.fontHeight - 4) * scale - (float)(10 * (1 - enter));
        int halfCard = (int)Math.ceil(width * scale / 2) + 12;
        int cardTop = (int)Math.floor(centreY - 4 * scale) - 9;
        int cardBottom = (int)Math.ceil(centreY + (client.textRenderer.fontHeight - 4) * scale) + 10;
        context.fill(w / 2 - halfCard, cardTop, w / 2 + halfCard, cardBottom, alpha(title * .85));
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate(w / 2f, centreY);
        m.scale(scale, scale);
        context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xE8EEF6, true);
        m.popMatrix();
        int line = (int)(width * scale / 2 * smooth((t - track.beat("title")) / 14));
        context.fill(w / 2 - line, cardBottom - 6, w / 2 + line, cardBottom - 5, alpha(title * .85) | 0xFF6040);
    }

    /** After landing, the player has their view back while the others are still being carried in. */
    public static void drawWaiting(DrawContext context) {
        if (!active || !finished) return;
        var client = MinecraftClient.getInstance();
        Text text = Text.translatable("storm.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCCE8EEF6);
    }

    /** The storm darkening the aerie's sky while the scene is there: rain and thunder levels, or -1 to leave the weather alone. */
    public static float weather(boolean overworld, float tickDelta) {
        if (!active || finished || !overworld) return -1;
        double t = time(tickDelta);
        StormRocIntroTrack track = track();
        return track.half(t) == 0 ? (float)MathHelper.clamp(.4 + track.dim(t), 0, 1) : -1;
    }

    static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    private static int alpha(double a) { return (int)Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
