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
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;
import com.anton.elementalwands.client.wand.WandMesh;
import com.anton.elementalwands.client.wand.WandRenderer;
import com.anton.elementalwands.mixin.EquipmentModelLoaderAccessor;
import java.util.ArrayList;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Atlases;
import net.minecraft.util.Unit;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The intro's two actors, the Storm Roc and the player, drawn straight into the world each frame
 * rather than as entities: entities only draw once the chunk under them has been built, and the
 * scene goes on while the summit loads. Each is placed by the track and posed from its own baked
 * clip at the scene's time ({@link IntroClip}). The Roc is drawn nearly black, as dark as the track
 * says, with its eyes still glowing; the player wears their own skin, wide or slim, and the armour
 * they have on (the game's own armour models, textures, dyes, trims and glint, each part on its
 * bone), and holds their own wand in their element's colours, laid as the bake lays it so its tip
 * is where the spells start.
 */
public final class StormRocIntroPuppets {
    private static final String CLIPS = "/assets/elementalwands/geckolib/animations/";
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
    /** Each version's clips ("" the plain crash, else an element's landing), loaded once. */
    private static final Map<String, IntroClip[]> CLIP_SETS = new java.util.HashMap<>();
    private static RocRenderer rocRenderer;
    private static PlayerRenderer playerRenderer;
    private static Identifier skin = DefaultSkinHelper.getSteve().body().texturePath();
    private static boolean slim;
    private static int element = 0xEEEDE7;
    /** The player's wand core (WandMesh's element index). */
    private static int wandElement = 0;
    /** What the player wears, taken with their skin: one stack per {@link #ARMOUR} slot. */
    private static final ItemStack[] worn = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

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

    /** At the scene's start: this version's clips, the player's own skin, arms and armour, and their wand's colour. */
    static void capture(MinecraftClient client, String landing) {
        IntroClip[] clips = CLIP_SETS.computeIfAbsent(landing, l -> {
            String suffix = l.isEmpty() ? "" : "_" + l;
            return new IntroClip[]{IntroClip.load(CLIPS + "storm_roc_intro" + suffix + ".animation.json", "animation.storm_roc.intro"),
                    IntroClip.load(CLIPS + "storm_roc_intro_player" + suffix + ".animation.json", "animation.storm_roc_intro_player.intro")};
        });
        rocClip = clips[0];
        playerClip = clips[1];
        if (rocRenderer == null) {
            rocRenderer = new RocRenderer();
            playerRenderer = new PlayerRenderer();
        }
        if (client.player != null) {
            var textures = client.player.getSkin();
            skin = textures.body().texturePath();
            slim = textures.model() == PlayerSkinType.SLIM;
        }
        for (int i = 0; i < ARMOUR.length; i++)
            worn[i] = client.player == null ? ItemStack.EMPTY : client.player.getEquippedStack(ARMOUR[i]).copy();
        wandElement = WandMesh.element(ClientPlayerData.getAffinity().name());
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
        StormRocIntroTrack track = StormRocIntroClient.track();
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
            StormRocIntroTrack track = StormRocIntroClient.track();
            float dark = track.dark(time), lift = track.flash(time) * .5f;
            float v = Math.clamp(1 - dark + lift, .06f, 1);
            int c = Math.round(v * 255);
            return 0xFF000000 | c << 16 | c << 8 | Math.min(255, c + 6);
        }

        @Override public void adjustRenderPose(GeoRenderState state, MatrixStack matrices, BakedGeoModel model, CameraRenderState camera) {}
    }

    // ------------------------------------------------------------------ the player

    /**
     * The bake's wand (build_intro.py and every landing): from the wand locator's pivot, turned -70
     * degrees about X, pointing along +Z with its tip 0.72 out, where the spells' baked tips are.
     * The game's wand mesh runs along its own +Y; it's laid along that line with the top of its
     * glass on the tip.
     */
    private static final float WAND_TIP = .72f, WAND_SCALE = 1.1f;
    private static final WandRenderer WAND = new WandRenderer();

    /** Into a bone's frame as GeckoLib draws it this frame: its parents' moves, then its own (in model space, posed). */
    private static void toBone(MatrixStack matrices, GeoBone bone) {
        var chain = new ArrayList<GeoBone>();
        for (GeoBone b = bone; b != null; b = b.getParent()) chain.add(b);
        for (int i = chain.size() - 1; i >= 0; i--) RenderUtil.prepMatrixForBone(matrices, chain.get(i));
    }

    /** Not scaled away (the bones hidden while the camera is in the player's eyes are). */
    private static boolean shown(GeoBone bone) {
        for (GeoBone b = bone; b != null; b = b.getParent())
            if (b.getScaleX() == 0 || b.getScaleY() == 0 || b.getScaleZ() == 0) return false;
        return true;
    }

    private static void submitWand(BakedGeoModel model, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay) {
        GeoBone wand = model.getBone("wand").orElse(null);
        if (wand == null) return;
        matrices.push();
        toBone(matrices, wand);
        RenderUtil.translateToPivotPoint(matrices, wand);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70));
        // The glass's top (headY + the glass's half-size, scaled) on the tip.
        float top = (WandMesh.DATA.headY() + .89f * WandMesh.DATA.headScale()) * WandMesh.DATA.scale() * WAND_SCALE;
        matrices.translate(0, 0, WAND_TIP - top);
        matrices.scale(WAND_SCALE, WAND_SCALE, WAND_SCALE);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        // Undo the item's own placement (WandMesh.transform), leaving the mesh's +Y along the line.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-WandMesh.DATA.rotationZ()));
        matrices.translate(-.5, -.5, -.5);
        var client = MinecraftClient.getInstance();
        double seconds = client.world == null ? 0 : (client.world.getTime() + client.getRenderTickCounter().getTickProgress(false)) / 20.0;
        WAND.render(new WandRenderer.State(wandElement, seconds, null), ItemDisplayContext.NONE, matrices, queue, light, overlay, false, 0);
        matrices.pop();
    }

    // ------------------------------------------------------------------ the player's armour

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** The game's armour model for each slot, as the player renderer's own (wide or slim). */
    private static final String[] ARMOUR_LAYERS = {"helmet", "chestplate", "leggings", "boots"};
    /** Each model's parts; the puppet's bones are named and placed as they are. */
    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** One part of an armour model, drawn by itself on its bone. */
    private record Piece(String bone, Model<Object> model) {}
    private static List<List<Piece>> pieces;
    private static LoadedEntityModels piecesFrom;
    private static boolean piecesSlim;
    private static EquipmentRenderer equipment;

    /** Each slot's pieces, rebuilt when the game's models reload or the skin's arms change. */
    private static List<List<Piece>> pieces(MinecraftClient client) {
        LoadedEntityModels models = client.getLoadedEntityModels();
        if (pieces != null && piecesFrom == models && piecesSlim == slim) return pieces;
        pieces = new ArrayList<>();
        for (String layer : ARMOUR_LAYERS) {
            ModelPart root = models.getModelPart(new EntityModelLayer(Identifier.ofVanilla(slim ? "player_slim" : "player"), layer));
            List<Piece> list = new ArrayList<>();
            for (String name : PARTS) {
                if (!root.hasChild(name)) continue;
                ModelPart part = root.getChild(name);
                if (part.traverse().stream().allMatch(ModelPart::isEmpty)) continue;
                list.add(new Piece(name, new Model<>(part, RenderLayer::getArmorCutoutNoCull) {}));
            }
            pieces.add(list);
        }
        var loader = ((EquipmentModelLoaderAccessor) client.getEntityRenderDispatcher()).elementalwands$equipmentModelLoader();
        equipment = new EquipmentRenderer(loader, client.getAtlasManager().getAtlasTexture(Atlases.ARMOR_TRIMS));
        piecesFrom = models;
        piecesSlim = slim;
        return pieces;
    }

    /**
     * The player's armour, each part of each piece on its own bone. The game models armour y down
     * from the neck with x mirrored, so on a bone's frame it is drawn turned that way and raised to
     * the neck; its parts' own pivots are then the bones'.
     */
    private static void submitArmour(BakedGeoModel model, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        List<List<Piece>> all = null;
        for (int i = 0; i < ARMOUR.length; i++) {
            ItemStack stack = worn[i];
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null || equippable.assetId().isEmpty() || !ArmorFeatureRenderer.hasModel(stack, ARMOUR[i])) continue;
            if (all == null) all = pieces(MinecraftClient.getInstance());
            var layer = ARMOUR[i] == EquipmentSlot.LEGS ? EquipmentModel.LayerType.HUMANOID_LEGGINGS : EquipmentModel.LayerType.HUMANOID;
            for (Piece piece : all.get(i)) {
                GeoBone bone = model.getBone(piece.bone()).orElse(null);
                if (bone == null || !shown(bone)) continue;
                matrices.push();
                toBone(matrices, bone);
                matrices.translate(0, 1.5, 0);
                matrices.scale(-1, -1, 1);
                equipment.render(layer, equippable.assetId().get(), piece.model(), Unit.INSTANCE, stack, matrices, queue, light, 0);
                matrices.pop();
            }
        }
    }

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
                    if (t != null) pose(this, playerClip, t, inEyes != null && inEyes ? StormRocIntroClient.track().povHides() : List.of());
                }
            });
            // Their armour, and the wand in the right hand (not while the camera is in their eyes, where the arms are hidden).
            withRenderLayer(renderer -> new GeoRenderLayer<>(renderer) {
                @Override public void submitRenderTask(GeoRenderState state, MatrixStack matrices, BakedGeoModel model, OrderedRenderCommandQueue queue,
                        CameraRenderState camera, int light, int overlay, int colour, boolean rendered) {
                    if (!rendered) return;
                    // GeckoLib poses the mesh later, when it draws; pose it now to read this frame's bones.
                    getGeoModel().handleAnimations(renderer.createAnimationState(state));
                    submitArmour(model, matrices, queue, light);
                    Boolean inEyes = state.getGeckolibData(POV);
                    if (inEyes == null || !inEyes) submitWand(model, matrices, queue, light, overlay);
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
