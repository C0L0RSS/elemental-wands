package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.blockink.WizardBody;
import com.anton.elementalwands.client.blockink.WizardCastPose;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The First Wizard: the player model in its skin owner's skin (slim arms and outer layers included), holding the
 * Wizard's Wand. The skin is the player's own, left alone (the developer, Nov 18); what shows it is a copy is the motes
 * of ink floating round it (client/blockink's {@code CopyFx}).
 *
 * <ul>
 *   <li>The skin comes from the profile the copy carries ({@link FirstWizardEntity#profile()}), resolved by the game's
 *       skin cache, so it stays when its owner dies or logs off. It is never lit below level 10, so it stays findable
 *       in the dark.</li>
 *   <li>Poses are a player's: it walks with its steps; while a spell forms its wand arm is raised overhead, and at the
 *       release it swings.</li>
 *   <li>The change (from {@link WizardBody}, on the change's baked track): it is thrown back by the blow; its left arm
 *       is wrenched and twitches and comes away from the shoulder; from the snap that arm is drawn on its own, in the
 *       skin with its sleeve, falling, tumbling and lying by her feet, darkening as it melts into the floor. Her left
 *       flank swells: lumps of the torso's own skin push out of its side in pulses, with veins of ink under them (the
 *       veins layer, art/first_wizard/build_ink.py). Her head bends to her right, too far, and stays bent through phase
 *       two. At the burst the swelling is gone, that side of the torso is narrowed and covered in ink (the wound layer),
 *       and from then the body is drawn floating ({@link WizardBody#lift}) with its legs hanging relaxed. The tentacles
 *       are client/blockink's {@code TendrilFx}; the ink at the shoulder, the strands and the melting arm's pool are
 *       {@code ChangeFx}'s.</li>
 *   <li>The wound is always her left, and the wand in her right hand from the change on, whichever hand the skin's
 *       owner uses (the developer, Nov 19).</li>
 * </ul>
 */
public final class FirstWizardRenderer extends EntityRenderer<FirstWizardEntity, FirstWizardRenderer.State> {
    public static final class State extends EntityRenderState {
        public final PlayerEntityRenderState appearance = new WizardState();
    }

    private final SkinRenderer skin;

    public FirstWizardRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = .4f;
        skin = new SkinRenderer(context);
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void updateRenderState(FirstWizardEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        skin.updateRenderState(entity, state.appearance, delta);
        com.anton.elementalwands.client.blockink.CopyForming.capture(entity, state, delta); // the intro's forming look (end/EndIntro)
    }

    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        skin.render(state.appearance, matrices, queue, camera);
    }

    // ── The model ────────────────────────────────────────────────────────────

    /**
     * Lumps of the swelling: where each sits on the torso's left side (body pixels), how big it grows (x out, y, z), and
     * how far out it is pushed, at a full swell. Each is a box of the torso's own skin (its texture is the torso's left
     * half: the side face, and the left halves of the front and back), so the shirt itself is what stretches.
     */
    private static final float[][] LUMPS = {
            {4, 4.6f, 0, 1.8f, 1.4f, 1.55f, 2.4f},
            {3.8f, 2.2f, -.9f, 1.25f, .9f, 1.1f, 1.8f},
            {3.8f, 7.6f, .8f, 1.15f, .95f, 1.05f, 1.5f}};
    /** The head's bend at its worst (the track's peak), for how far the neck slips with it. */
    private static final float BEND_PEAK = 1.117f;

    /** The player model with the swelling's lumps on its torso. */
    private static ModelData modelData(Dilation dilation, boolean slim) {
        ModelData data = PlayerEntityModel.getTexturedModelData(dilation, slim);
        ModelPartData body = data.getRoot().getChild("body");
        for (int i = 0; i < LUMPS.length; i++) {
            ModelPartData lump = body.addChild("bulge_" + i, ModelPartBuilder.create().uv(20, 18).cuboid(-2, -3, -2, 4, 6, 4, dilation),
                    ModelTransform.origin(LUMPS[i][0], LUMPS[i][1], LUMPS[i][2]));
            lump.addChild("jacket", ModelPartBuilder.create().uv(20, 34).cuboid(-2, -3, -2, 4, 6, 4, dilation.add(.25f)), ModelTransform.NONE);
        }
        return data;
    }

    private static WoundedModel playerModel(Dilation dilation, boolean slim) {
        return new WoundedModel(TexturedModelData.of(modelData(dilation, slim), 64, 64).createModel(), slim);
    }

    /** The left arm on its own, its sleeve with it, for drawing it torn off: its own transform is nothing. */
    private static ModelPart armAlone(boolean slim) {
        ModelPart arm = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim), 64, 64).createModel().getChild("left_arm");
        arm.setDefaultTransform(ModelTransform.NONE);
        arm.resetTransform();
        return arm;
    }

    /** The player model's pose and what the change and the torn body add to it. */
    public static final class WizardState extends PlayerEntityRenderState {
        /** The arm is off (from the snap); the flank has burst (the torso is the wound); the arm is drawn torn off. */
        public boolean armGone, burst, tornArm;
        public float wandWeight, wandPitch, wandRoll;
        public WizardCastPose casting = WizardCastPose.REST;
        public float facing;
        public float lift, back, sideways, trail, time;
        /** The arm while it is still on: the model's angles, and how far its pivot has come out of the socket (pixels). */
        public float wrenchPitch, wrenchYaw, wrenchRoll, gapX, gapY;
        public boolean wrenched;
        /** The torn arm: its middle in the change's frame (blocks), its angles and turn, how flat and how far melted. */
        public float armX, armY, armZ, armPitch, armTwist, armRoll, armTurn, flat, melt;
        /** The swelling and its veins, and the head's bend and twitch. */
        public float bulge, veins, bend, twitch;
    }

    /**
     * The player model, changed: the arm wrenched and then hidden, the swelling's lumps, the head bent; once burst, the
     * torso's off half narrowed toward the wand arm, the legs trailing behind the raised body, and the wand arm held out
     * while it breathes fire or forms the orb.
     */
    private static final class WoundedModel extends PlayerEntityModel {
        private final ModelPart[] lumps = new ModelPart[LUMPS.length];

        WoundedModel(ModelPart root, boolean slim) {
            super(root, slim);
            for (int i = 0; i < lumps.length; i++) lumps[i] = body.getChild("bulge_" + i);
        }

        @Override public void setAngles(PlayerEntityRenderState s) {
            super.setAngles(s);
            leftArm.visible = rightArm.visible = true;
            for (ModelPart lump : lumps) lump.visible = false;
            com.anton.elementalwands.client.blockink.CopyForming.shape(this, s); // the intro's forming look
            if (!(s instanceof WizardState w)) return;
            castPose(w);
            if (w.wrenched) {
                // Wrenched at the shoulder, twitching, then sagging out of the socket.
                leftArm.pitch = w.wrenchPitch; leftArm.yaw = w.wrenchYaw; leftArm.roll = w.wrenchRoll;
                leftArm.originX += w.gapX; leftArm.originY += w.gapY;
            }
            if (w.armGone) leftArm.visible = false;
            if (w.bulge > .02f && !w.burst) {
                // The torso swells toward its left; lumps of its own skin push out of that side, each writhing a little.
                body.xScale = 1 + .14f * w.bulge;
                body.originX += .56f * w.bulge;
                for (int i = 0; i < lumps.length; i++) {
                    float[] l = LUMPS[i];
                    float b = w.bulge * (1 + .07f * (float) Math.sin(w.time * 2.3 + i * 2.1));
                    ModelPart lump = lumps[i];
                    lump.visible = true;
                    lump.originX = l[0] + l[6] * b;
                    lump.xScale = Math.max(.05f, l[3] * b);
                    lump.yScale = Math.max(.05f, .45f + (l[4] - .45f) * b);
                    lump.zScale = Math.max(.05f, .55f + (l[5] - .55f) * b);
                }
            }
            if (w.bend != 0) {
                // Bent to her right, too far: rolled about the neck, the neck slipping toward that side.
                float k = w.bend / BEND_PEAK;
                head.roll -= w.bend;
                head.yaw += w.twitch;
                head.originX -= 1f * k;
                head.originY += .5f * k;
            }
            if (w.burst) {
                body.xScale = .62f;
                body.originX -= 1.52f;
                jacket.visible = false;
                float sway = (float) Math.sin(w.time * 1.6) * .07f;
                leftLeg.pitch = w.trail * (.16f + sway);
                rightLeg.pitch = w.trail * (.26f - sway);
                leftLeg.roll = -w.trail * .05f;
                rightLeg.roll = w.trail * .05f;
            }
            WizardCastPose cast = w.casting;
            if (cast == WizardCastPose.REST) return;
            float lean = (float) Math.toRadians(cast.pitch()), bank = (float) Math.toRadians(cast.bank());
            float turn = (float) Math.sin(Math.toRadians(cast.twist()));
            // The gaze resists the shoulder turn; the hips and feet trail it, even through a full revolution.
            head.yaw += .68f * turn;
            head.pitch -= .40f * lean;
            head.roll += .30f * bank;
            float up = (float) cast.skyward();
            head.pitch = MathHelper.lerp(up, head.pitch, -1.40f - lean);
            head.roll *= 1 - .84f * up;
            head.yaw *= 1 - .8f * up;
            float hipTurn = .42f * turn;
            step(leftLeg, (float) Math.toRadians(cast.leftLeg()), hipTurn, bank, -1);
            step(rightLeg, (float) Math.toRadians(cast.rightLeg()), hipTurn, bank, 1);
        }

        private static void step(ModelPart leg, float pitch, float turn, float bank, int side) {
            float x = leg.originX, z = leg.originZ;
            leg.originX = x * MathHelper.cos(turn) + z * MathHelper.sin(turn);
            leg.originZ = z * MathHelper.cos(turn) - x * MathHelper.sin(turn);
            leg.originY -= .9f * Math.abs(MathHelper.sin(pitch));
            leg.pitch += pitch;
            leg.yaw += turn;
            leg.roll += .42f * bank + side * .20f * Math.abs(MathHelper.sin(pitch));
        }

        private void castPose(WizardState w) {
            boolean right = w.preferredArm == Arm.RIGHT;
            ModelPart wand = right ? rightArm : leftArm;
            ModelPart palm = right ? leftArm : rightArm;
            float hand = right ? 1 : -1;
            wand.pitch = (1 - w.wandWeight) * wand.pitch + w.wandPitch;
            wand.roll = (1 - w.wandWeight) * wand.roll + w.wandRoll * hand;
            if (!w.armGone) {
                palm.pitch = (1 - w.wandWeight) * palm.pitch + w.wandPitch * .7f;
                palm.roll = (1 - w.wandWeight) * palm.roll - w.wandRoll * hand * .85f;
            }
        }
    }

    /** The wand follows the strike closely and takes longer to lower, including across two queued spells. */
    private static final class WandMotion {
        private static final Map<FirstWizardEntity, WandMotion> COPIES = new MapMaker().weakKeys().makeMap();
        private double lastTick = Double.NEGATIVE_INFINITY;
        private float weight, pitch, roll;

        void sample(WizardState w, FirstWizardRules.Move move, double tick, double age, double beat, boolean openingSweep) {
            float load = move == null ? 0 : smooth((float) (age / 6));
            float swing = smooth((float) ((age - beat + 3) / 5));
            if (move == FirstWizardRules.Move.SWEEP)
                swing = smooth((float) ((age - beat) / (openingSweep ? PhaseChange.SWEEP : SweepSpell.SWING)));
            if (move == FirstWizardRules.Move.SKY_SHEAR) {
                swing = 0;
                for (int arm = 0; arm < SkyShearSpell.COUNT; arm++) {
                    double stroke = age - SkyShearSpell.leaves(arm);
                    swing += smooth((float) ((stroke + 3) / 5)) * (1 - smooth((float) ((stroke - 3) / 7)));
                }
            }
            float aimPitch = move == null ? 0 : switch (move) {
                case METEOR -> -1.25f - 1.8f * swing;
                case STONE_WALL, SENTRY_BLOOM -> -.3f - 2.4f * swing;
                case HOLLOW_PURPLE -> -1.25f - 1.8f * smooth((float) (age / 24)) + 1.95f * smooth((float) ((age - beat + 12) / 14));
                case FIREBALL -> -1.15f - 1.9f * smooth((float) (age / 12)) + 2.35f * swing;
                case FAULTLINE, SLAM -> -2.95f + 2.1f * swing;
                case BLINK_RIFT -> -1.05f + .75f * swing;
                case LEAP, WAYLAY_DASH -> .75f - 2.35f * swing;
                case SWEEP, SKY_SHEAR, THORNBITE -> -1.5f + .55f * swing;
                case FLAME, GRAVITY_WELL -> -1.6f;
            };
            float aimRoll = move == null ? 0 : switch (move) {
                case SWEEP, SKY_SHEAR -> -1.35f + 2.15f * swing;
                case FIREBALL -> -.6f + .5f * swing;
                case HOLLOW_PURPLE -> -.7f - .3f * smooth((float) (age / 22)) + .8f * swing;
                case STONE_WALL -> -.85f + .3f * swing;
                case BLINK_RIFT -> .9f - 1.65f * swing;
                default -> -.18f;
            };
            double dt = tick - lastTick;
            float blend = dt < 0 || dt > 21 ? 1 : (float) -Math.expm1(-dt / (move == null ? 4.5 : 1.5));
            lastTick = tick;
            w.wandWeight = weight = MathHelper.lerp(blend, weight, load);
            w.wandPitch = pitch = MathHelper.lerp(blend, pitch, load * aimPitch);
            w.wandRoll = roll = MathHelper.lerp(blend, roll, load * aimRoll);
        }
    }

    /** Fills the change's and the torn body's part of the state. */
    private static void tear(FirstWizardEntity e, WizardState s, float delta) {
        s.armGone = WizardBody.armGone(e, delta);
        s.burst = WizardBody.burst(e, delta);
        s.lift = (float) WizardBody.lift(e, delta);
        s.casting = WizardCastPose.sample(e, delta);
        s.facing = (float) WizardBody.yaw(e, delta);
        s.back = (float) WizardBody.staggerBack(e, delta);
        s.sideways = (float) WizardBody.staggerSide(e, delta);
        s.trail = (float) Math.clamp(s.lift / FirstWizardRules.LIFT, 0, 1);
        s.time = (float) ((e.getEntityWorld().getTime() % 24000 + delta) / 20.0);
        s.bend = (float) WizardBody.bend(e, delta);
        s.twitch = (float) WizardBody.headTwitch(e, delta);
        double t = WizardBody.change(e, delta);
        s.wrenched = t >= 0 && t < PhaseChange.SNAP;
        s.tornArm = t >= PhaseChange.SNAP && t < PhaseChange.MELTED + 2;
        s.bulge = t >= 0 ? (float) WizardBody.value("bulge", t) : 0;
        s.veins = t >= 0 ? (float) WizardBody.value("veins", t) : 0;
        if (s.wrenched) {
            Vec3d w = WizardBody.point("wrench", t), gap = WizardBody.point("gap", t);
            s.wrenchPitch = (float) w.x; s.wrenchYaw = (float) w.y; s.wrenchRoll = (float) w.z;
            s.gapX = (float) gap.x; s.gapY = (float) gap.y;
        }
        if (s.tornArm) {
            Vec3d at = WizardBody.point("arm", t), rot = WizardBody.point("arm_rot", t);
            s.armX = (float) at.x; s.armY = (float) at.y; s.armZ = (float) at.z;
            s.armPitch = (float) rot.x; s.armTwist = (float) rot.y; s.armRoll = (float) rot.z;
            s.armTurn = (float) WizardBody.value("arm_turn", t);
            s.flat = (float) WizardBody.value("flat", t);
            s.melt = (float) WizardBody.value("melt", t);
        }
        boolean openingSweep = t >= PhaseChange.TELL;
        FirstWizardRules.Move move = openingSweep ? FirstWizardRules.Move.SWEEP : FirstWizardRules.Move.byId(e.castMove());
        double age = openingSweep ? t - PhaseChange.TELL : e.castAge(delta);
        double beat = openingSweep ? PhaseChange.SWEEP_FROM - PhaseChange.TELL : e.castRelease();
        if (move == FirstWizardRules.Move.METEOR) beat = MeteorSpell.TIMING.aimLock();
        WandMotion.COPIES.computeIfAbsent(e, unused -> new WandMotion())
                .sample(s, move, e.getEntityWorld().getTime() + (double) delta, age, beat, openingSweep);
    }

    /** Raises the drawn body and throws it back with the stagger, round its hips. */
    private static void raise(WizardState s, MatrixStack m) {
        m.translate(0, s.lift, 0);
        if (s.back != 0) {
            m.translate(0, .75, 0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.bodyYaw));
            m.multiply(RotationAxis.POSITIVE_X.rotation(-s.back));
            m.multiply(RotationAxis.POSITIVE_Z.rotation(s.sideways * .5f));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s.bodyYaw));
            m.translate(0, -.75, 0);
        }
        s.casting.apply(m, s.facing);
    }

    private static SkinTextures skinOf(FirstWizardEntity e) {
        try {
            return MinecraftClient.getInstance().getPlayerSkinCache().get(e.profile()).getTextures();
        } catch (RuntimeException ignored) {
            return DefaultSkinHelper.getSteve();
        }
    }

    /** Fills a player render state from the copy: its skin, its walk, its wand and the pose of the spell in hand. */
    private static void pose(FirstWizardEntity e, PlayerEntityRenderState s, float delta, net.minecraft.client.item.ItemModelManager items) {
        s.skinTextures = skinOf(e);
        s.hatVisible = s.jacketVisible = s.leftPantsLegVisible = s.rightPantsLegVisible = s.leftSleeveVisible = s.rightSleeveVisible = true;
        s.spectator = false; s.capeVisible = false; s.playerName = null; s.displayName = null;
        ArmedEntityRenderState.updateRenderState(e, s, items);
        Arm main = e.getMainArm();
        s.preferredArm = main;
        BipedEntityModel.ArmPose wand = BipedEntityModel.ArmPose.ITEM;
        s.handSwingProgress = 0;
        s.rightArmPose = main == Arm.RIGHT ? wand : BipedEntityModel.ArmPose.EMPTY;
        s.leftArmPose = main == Arm.LEFT ? wand : BipedEntityModel.ArmPose.EMPTY;
    }

    /** The torn arm's tint as it melts: the skin darkening through a bruise of violet into ink, then gone into the floor. */
    private static int meltColor(float melt) {
        float dark = smooth(melt / .6f), bruise = smooth(melt / .3f) * (1 - smooth((melt - .3f) / .4f));
        float r = MathHelper.lerp(dark, 1f, .07f) + .18f * bruise, g = MathHelper.lerp(dark, 1f, .05f), b = MathHelper.lerp(dark, 1f, .1f) + .3f * bruise;
        float a = 1 - smooth((melt - .72f) / .28f);
        return net.minecraft.util.math.ColorHelper.fromFloats(a, Math.min(1, r), Math.min(1, g), Math.min(1, b));
    }

    private static float smooth(float s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    private static final class SkinRenderer extends LivingEntityRenderer<FirstWizardEntity, PlayerEntityRenderState, PlayerEntityModel> {
        private final PlayerEntityModel wide, slim;
        private final ModelPart wideArm = armAlone(false), slimArm = armAlone(true);

        private SkinRenderer(EntityRendererFactory.Context c) {
            super(c, playerModel(Dilation.NONE, false), .4f);
            wide = model;
            slim = playerModel(Dilation.NONE, true);
            addFeature(new HeldItemFeatureRenderer<>(this));
            addFeature(new BulgeVeins(this));
            addFeature(new WoundInk(this));
        }
        @Override public PlayerEntityRenderState createRenderState() { return new WizardState(); }
        @Override public void updateRenderState(FirstWizardEntity e, PlayerEntityRenderState s, float delta) {
            super.updateRenderState(e, s, delta);
            pose(e, s, delta, itemModelResolver);
            if (s instanceof WizardState w) tear(e, w, delta);
        }
        @Override public Identifier getTexture(PlayerEntityRenderState s) { return s.skinTextures.body().texturePath(); }
        @Override public void render(PlayerEntityRenderState s, MatrixStack m, OrderedRenderCommandQueue q, CameraRenderState camera) {
            boolean thin = s.skinTextures.model() == PlayerSkinType.SLIM;
            model = thin ? slim : wide;
            m.push();
            if (s instanceof WizardState w) raise(w, m);
            super.render(s, m, q, camera);
            m.pop();
            if (s instanceof WizardState w && w.tornArm && !s.invisible) tornArm(w, thin, m, q);
        }

        /** The arm torn off, in the skin with its sleeve, where the change's track has it: falling, lying, melting. */
        private void tornArm(WizardState w, boolean thin, MatrixStack m, OrderedRenderCommandQueue q) {
            int color = meltColor(w.melt);
            if ((color >>> 24) < 4) return;
            m.push();
            // Into the model's space, as the living renderer goes there: x her left, y down, z back, in blocks.
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - w.bodyYaw));
            m.scale(-1, -1, 1);
            m.translate(0, -WizardBody.MODEL_TOP, 0);
            m.translate(w.armX, WizardBody.MODEL_TOP - w.armY, -w.armZ);
            // Flattening onto the floor as it melts; then turned as it lies, and as it tumbled.
            m.scale(1, Math.max(.05f, w.flat), 1);
            m.multiply(RotationAxis.POSITIVE_Y.rotation(w.armTurn));
            m.multiply(new Quaternionf().rotationZYX(w.armRoll, w.armTwist, w.armPitch));
            // From its middle to its pivot (the shoulder end of its box).
            m.translate(-(thin ? .5f : 1f) / 16f, -4f / 16f, 0);
            q.submitModelPart(thin ? slimArm : wideArm, m, RenderLayer.getEntityTranslucent(w.skinTextures.body().texturePath()), w.light,
                    OverlayTexture.DEFAULT_UV, null, false, false, color, null, 0);
            m.pop();
        }

        /** Findable in the dark: never lit below level 10 (the boss rule: darkness must not cost readability). */
        @Override protected int getBlockLight(FirstWizardEntity e, BlockPos pos) { return Math.max(10, super.getBlockLight(e, pos)); }
        @Override protected boolean hasLabel(FirstWizardEntity e, double distance) { return false; }
    }

    /**
     * While her flank swells: veins of ink under the skin of the torso's left side, on a slightly inflated model (lumps
     * included), textured (art/first_wizard/build_ink.py) so that only the veins show; they darken in as it swells.
     * Full-bright, so the violet in them glows. Gone at the burst.
     */
    private static final class BulgeVeins extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
        private static final Identifier VEINS = Identifier.of("elementalwands", "textures/entity/first_wizard_veins.png");
        private final PlayerEntityModel wide = playerModel(new Dilation(.3f), false), slim = playerModel(new Dilation(.3f), true);

        private BulgeVeins(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

        @Override public void render(MatrixStack m, OrderedRenderCommandQueue q, int light, PlayerEntityRenderState s, float limbAngle, float limbDistance) {
            if (!(s instanceof WizardState w) || w.burst || w.veins <= .01f) return;
            boolean thin = s.skinTextures.model() == PlayerSkinType.SLIM;
            int alpha = Math.round(Math.min(1, w.veins) * 255);
            q.getBatchingQueue(1).submitModel(thin ? slim : wide, s, m, RenderLayer.getEntityTranslucent(VEINS),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, alpha << 24 | 0xFFFFFF, null, s.outlineColor, null);
        }
    }

    /**
     * Once burst: ink over the cut side of the torso, on a slightly inflated model, textured (art/first_wizard/build_ink.py)
     * so that her left half of the torso is matte ink with a ragged violet edge where it meets the skin, and everything
     * else is transparent. Full-bright. The rest of the skin stays the player's own.
     */
    private static final class WoundInk extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
        private static final Identifier WIDE = Identifier.of("elementalwands", "textures/entity/first_wizard_wound_wide.png"),
                SLIM = Identifier.of("elementalwands", "textures/entity/first_wizard_wound_slim.png");
        private final PlayerEntityModel wide = playerModel(new Dilation(.35f), false), slim = playerModel(new Dilation(.35f), true);

        private WoundInk(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

        @Override public void render(MatrixStack m, OrderedRenderCommandQueue q, int light, PlayerEntityRenderState s, float limbAngle, float limbDistance) {
            if (!(s instanceof WizardState w) || !w.burst) return;
            boolean thin = s.skinTextures.model() == PlayerSkinType.SLIM;
            q.getBatchingQueue(1).submitModel(thin ? slim : wide, s, m, RenderLayer.getEntityTranslucent(thin ? SLIM : WIDE),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF, null, s.outlineColor, null);
        }
    }
}
