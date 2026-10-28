package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.summit.StormRocIntroTrack;
import com.anton.elementalwands.summit.StormSummitLayout;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderModelPositioner;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The intro's two actors, the Storm Roc and the player, drawn straight into the world each frame
 * rather than as entities: entities only draw once the chunk under them has been built, and the
 * scene goes on while the summit loads. Each is placed by the track and posed from its own baked
 * clip at the scene's time ({@link IntroClip}). The Roc is drawn nearly black, as dark as the track
 * says, with its eyes still glowing; the player wears their own skin, wide or slim.
 */
public final class StormRocIntroPuppets {
    private static final String ROC_CLIP = "/assets/elementalwands/geckolib/animations/storm_roc_intro.animation.json";
    private static final String PLAYER_CLIP = "/assets/elementalwands/geckolib/animations/storm_roc_intro_player.animation.json";
    private static final DataTicket<Double> TIME = DataTicket.create("elementalwands_intro_time", Double.class);
    private static final DataTicket<Boolean> POV = DataTicket.create("elementalwands_intro_pov", Boolean.class);
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    /** A puppet: GeckoLib needs something to animate; this one has no controllers and keeps no time of its own. */
    private static final class Puppet implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object object) { return 0; }
        @Override public double getBoneResetTime() { return 0; }
    }

    private static final Puppet ROC = new Puppet(), PLAYER = new Puppet();
    private static IntroClip rocClip, playerClip;
    private static RocRenderer rocRenderer;
    private static PlayerRenderer playerRenderer;
    private static Identifier skin = DefaultSkinHelper.getSteve().body().texturePath();
    private static boolean slim;
    private static int element = 0xEEEDE7;

    private StormRocIntroPuppets() {}

    static void init() {
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (!StormRocIntroClient.active() && !StormRocIntroClient.handingOver()) return;
            var client = MinecraftClient.getInstance();
            CameraRenderState camera = context.worldState().cameraRenderState;
            if (camera == null || camera.pos == null) return;
            float delta = client.getRenderTickCounter().getTickProgress(false);
            draw(context.matrices(), context.commandQueue(), camera, delta);
        });
    }

    /** At the scene's start: the player's own skin and arms, and their wand's colour. */
    static void capture(MinecraftClient client) {
        if (rocClip == null) {
            rocClip = IntroClip.load(ROC_CLIP, "animation.storm_roc.intro");
            playerClip = IntroClip.load(PLAYER_CLIP, "animation.storm_roc_intro_player.intro");
            rocRenderer = new RocRenderer();
            playerRenderer = new PlayerRenderer();
        }
        if (client.player != null) {
            var textures = client.player.getSkin();
            skin = textures.body().texturePath();
            slim = textures.model() == PlayerSkinType.SLIM;
        }
        element = switch (ClientPlayerData.getAffinity()) {
            case FIRE -> 0xE0842C;
            case NATURE -> 0x7FD36B;
            case STONE -> 0xBCC7C3;
            case SPACE -> 0xB29DFF;
            default -> 0xAAE1FF;
        };
    }

    static void release() {}

    /** The colour of the player's wand strike. */
    static int elementColour() { return element; }

    /** The scene's Roc is still standing in for the fight's: until the fight's own Roc has arrived. */
    static boolean rocShown() {
        if (!StormRocIntroClient.active() && !StormRocIntroClient.handingOver()) return false;
        if (!StormRocIntroClient.finished()) return true;
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return false;
        return client.world.getEntitiesByClass(StormRocEntity.class, client.player.getBoundingBox().expand(300), e -> true).isEmpty();
    }

    private static void draw(MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, float delta) {
        StormRocIntroTrack track = StormRocIntroTrack.get();
        double t = StormRocIntroClient.time(delta);
        // Once over, or skipped in the realm, the Roc waits on its perch where the fight's own will appear.
        if (StormRocIntroClient.finished() || StormRocIntroClient.skipped() && StormRocIntroClient.drawnHalf(t) == 1) t = track.length();
        int half = StormRocIntroClient.drawnHalf(t);
        // The client is still at the aerie but the scene has moved on to the summit (or the other way
        // round, briefly, behind the cloud): draw nothing rather than the wrong world's actors.
        if (track.half(t) != half) return;
        if (rocShown()) submit(matrices, queue, camera, rocRenderer, ROC, half, track.rocPos(t), track.rocTurn(t), t, false);
        if (!StormRocIntroClient.finished() && !StormRocIntroClient.skipped())
            submit(matrices, queue, camera, playerRenderer, PLAYER, half, track.playerPos(t), track.playerTurn(t), t, track.pov(t));
    }

    private static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera,
            GeoObjectRenderer<Puppet, Void, GeoRenderState> renderer, Puppet puppet, int half, Vec3d local, Quaternionf turn, double t, boolean pov) {
        Vec3d at = StormRocIntroClient.place(half, local);
        Quaternionf world = StormRocIntroClient.turn(half, turn);
        matrices.push();
        matrices.translate(at.x - camera.pos.x, at.y - camera.pos.y, at.z - camera.pos.z);
        matrices.multiply(world);
        // GeckoLib's models face -Z; the track's face +Z.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        if (puppet == ROC) rocRenderer.scene(t); else playerRenderer.scene(t, pov);
        renderer.submit(matrices, puppet, null, queue, camera, FULL_BRIGHT, 0,
                RenderModelPositioner.add(null, (state, model) -> {}));
        matrices.pop();
    }

    /** Poses every bone the clip moves, as the game's own Roc model does (GeckoLib negates X and Y). */
    private static void pose(GeoModel<Puppet> model, IntroClip clip, double t, List<String> hidden) {
        float radians = (float)(Math.PI / 180);
        for (Map.Entry<String, IntroClip.Pose> e : clip.pose(t).entrySet()) {
            model.getBone(e.getKey()).ifPresent(bone -> {
                var initial = bone.getInitialSnapshot();
                IntroClip.Pose p = e.getValue();
                bone.setRotX(initial.getRotX() - p.rotation()[0] * radians);
                bone.setRotY(initial.getRotY() - p.rotation()[1] * radians);
                bone.setRotZ(initial.getRotZ() + p.rotation()[2] * radians);
                bone.setPosX(p.position()[0]); bone.setPosY(p.position()[1]); bone.setPosZ(p.position()[2]);
                bone.setScaleX(p.scale()[0]); bone.setScaleY(p.scale()[1]); bone.setScaleZ(p.scale()[2]);
            });
        }
        for (String name : hidden) model.getBone(name).ifPresent(bone -> { bone.setScaleX(0); bone.setScaleY(0); bone.setScaleZ(0); });
    }

    // ------------------------------------------------------------------ the Roc

    private static final class RocRenderer extends GeoObjectRenderer<Puppet, Void, GeoRenderState> {
        private double time;

        RocRenderer() {
            super(new GeoModel<>() {
                private final Identifier model = Identifier.of("elementalwands", "geckolib/models/storm_roc_intro.geo.json");
                private final Identifier texture = Identifier.of("elementalwands", "textures/entity/storm_roc.png");
                private final Identifier animation = Identifier.of("elementalwands", "geckolib/animations/storm_roc_intro.animation.json");
                @Override public Identifier getModelResource(GeoRenderState state) { return model; }
                @Override public Identifier getTextureResource(GeoRenderState state) { return texture; }
                @Override public Identifier getAnimationResource(Puppet puppet) { return animation; }
                @Override public void setCustomAnimations(AnimationState<Puppet> state) {
                    Double t = state.renderState().getGeckolibData(TIME);
                    if (t != null) pose(this, rocClip, t, List.of());
                }
            });
            withScale((float)StormSummitLayout.ROC_SCALE);
            // Its eyes glow at full strength however dark the rest of it is drawn.
            withRenderLayer(renderer -> new AutoGlowingGeoLayer<>(renderer) {
                @Override public void submitRenderTask(GeoRenderState state, MatrixStack matrices, BakedGeoModel model, OrderedRenderCommandQueue queue,
                        CameraRenderState camera, int light, int overlay, int colour, boolean reRender) {
                    super.submitRenderTask(state, matrices, model, queue, camera, light, overlay, 0xFFFFFFFF, reRender);
                }
            });
        }

        void scene(double t) { time = t; }

        @Override public void addRenderData(Puppet puppet, Void related, GeoRenderState state, float delta) { state.addGeckolibData(TIME, time); }

        /** Near black in the storm, lightning lifting it for a moment. */
        @Override public int getRenderColor(Puppet puppet, Void related, float delta) {
            StormRocIntroTrack track = StormRocIntroTrack.get();
            float dark = track.dark(time), lift = track.flash(time) * .5f;
            float v = Math.clamp(1 - dark + lift, .06f, 1);
            int c = Math.round(v * 255);
            return 0xFF000000 | c << 16 | c << 8 | Math.min(255, c + 6);
        }

        @Override public void adjustRenderPose(GeoRenderState state, MatrixStack matrices, BakedGeoModel model, CameraRenderState camera) {}
    }

    // ------------------------------------------------------------------ the player

    private static final class PlayerRenderer extends GeoObjectRenderer<Puppet, Void, GeoRenderState> {
        private double time;
        private boolean pov;

        PlayerRenderer() {
            super(new GeoModel<>() {
                private final Identifier wide = Identifier.of("elementalwands", "geckolib/models/storm_roc_intro_player.geo.json");
                private final Identifier slimArms = Identifier.of("elementalwands", "geckolib/models/storm_roc_intro_player_slim.geo.json");
                private final Identifier animation = Identifier.of("elementalwands", "geckolib/animations/storm_roc_intro_player.animation.json");
                @Override public Identifier getModelResource(GeoRenderState state) { return slim ? slimArms : wide; }
                @Override public Identifier getTextureResource(GeoRenderState state) { return skin; }
                @Override public Identifier getAnimationResource(Puppet puppet) { return animation; }
                @Override public void setCustomAnimations(AnimationState<Puppet> state) {
                    Double t = state.renderState().getGeckolibData(TIME);
                    Boolean inEyes = state.renderState().getGeckolibData(POV);
                    if (t != null) pose(this, playerClip, t, inEyes != null && inEyes ? StormRocIntroTrack.get().povHides() : List.of());
                }
            });
        }

        void scene(double t, boolean inEyes) { time = t; pov = inEyes; }

        @Override public void addRenderData(Puppet puppet, Void related, GeoRenderState state, float delta) {
            state.addGeckolibData(TIME, time);
            state.addGeckolibData(POV, pov);
        }

        @Override public void adjustRenderPose(GeoRenderState state, MatrixStack matrices, BakedGeoModel model, CameraRenderState camera) {}
    }
}
