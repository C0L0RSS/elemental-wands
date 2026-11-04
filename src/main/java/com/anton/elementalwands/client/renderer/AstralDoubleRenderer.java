package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.AstralDoubleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;

/**
 * Real owner skin (including slim arms and outer skin layers), with the Space wand, under a void-glass shell: the
 * player model drawn again a little larger in see-through glass (art/space/blockspace). The shell is bright as the
 * double forms and settles within a second; its orbiting plates and stars are BlockSpaceEffects'.
 */
public final class AstralDoubleRenderer extends EntityRenderer<AstralDoubleEntity,AstralDoubleRenderer.State> {
    /** PlayerEntityRenderState at the top level is rerouted to vanilla's player renderer. */
    public static final class State extends EntityRenderState {
        public final PlayerEntityRenderState appearance=new PlayerEntityRenderState();
    }
    private final SkinRenderer skin;
    public AstralDoubleRenderer(EntityRendererFactory.Context context) {
        super(context);shadowRadius=.35f;skin=new SkinRenderer(context);
    }
    @Override public State createRenderState(){return new State();}
    @Override public void updateRenderState(AstralDoubleEntity entity,State state,float delta) {
        super.updateRenderState(entity,state,delta);skin.updateRenderState(entity,state.appearance,delta);
    }
    @Override public void render(State state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera) {
        skin.render(state.appearance,matrices,queue,camera);
    }
    private static final class SkinRenderer extends LivingEntityRenderer<AstralDoubleEntity,PlayerEntityRenderState,PlayerEntityModel> {
        private final PlayerEntityModel wide,slim;
        private SkinRenderer(EntityRendererFactory.Context c){
            super(c,new PlayerEntityModel(c.getPart(EntityModelLayers.PLAYER),false),.35f);
            wide=model;slim=playerModel(Dilation.NONE,true);
            addFeature(new HeldItemFeatureRenderer<>(this));
            addFeature(new GlassShell(this));
        }
        @Override public PlayerEntityRenderState createRenderState(){return new PlayerEntityRenderState();}
        @Override public void updateRenderState(AstralDoubleEntity e,PlayerEntityRenderState s,float delta){
            super.updateRenderState(e,s,delta);
            var id=e.ownerUuid();var client=MinecraftClient.getInstance();
            var entry=id==null || client.getNetworkHandler()==null ? null : client.getNetworkHandler().getPlayerListEntry(id);
            s.skinTextures=entry!=null ? entry.getSkinTextures() : id!=null ? DefaultSkinHelper.getSkinTextures(id) : DefaultSkinHelper.getSteve();
            s.hatVisible=s.jacketVisible=s.leftPantsLegVisible=s.rightPantsLegVisible=s.leftSleeveVisible=s.rightSleeveVisible=true;
            s.spectator=false;s.capeVisible=false;s.playerName=null;s.displayName=null;
            s.limbSwingAmplitude=0;s.limbSwingAnimationProgress=0;s.limbAmplitudeInverse=1;
            s.handSwingProgress=e.getHandSwingProgress(delta);s.preferredArm=e.getMainArm();
            ArmedEntityRenderState.updateRenderState(e,s,itemModelResolver);
            s.rightArmPose=e.getMainArm()==net.minecraft.util.Arm.RIGHT ? BipedEntityModel.ArmPose.ITEM : BipedEntityModel.ArmPose.EMPTY;
            s.leftArmPose=e.getMainArm()==net.minecraft.util.Arm.LEFT ? BipedEntityModel.ArmPose.ITEM : BipedEntityModel.ArmPose.EMPTY;
        }
        @Override public Identifier getTexture(PlayerEntityRenderState s){return s.skinTextures.body().texturePath();}
        @Override public void render(PlayerEntityRenderState s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState camera){
            model=s.skinTextures.model()==PlayerSkinType.SLIM ? slim : wide;
            super.render(s,m,q,camera);
        }
    }
    private static PlayerEntityModel playerModel(Dilation dilation,boolean slim) {
        return new PlayerEntityModel(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(dilation,slim),64,64).createModel(),slim);
    }

    /**
     * The void-glass shell: the same pose on a slightly inflated player model, textured so every face of every limb is
     * dark see-through glass with a bright frame (built by art/space/blockspace/textures/build_astral_glass.py). The
     * outer skin-layer regions are transparent, so only one shell draws.
     */
    private static final class GlassShell extends FeatureRenderer<PlayerEntityRenderState,PlayerEntityModel> {
        private static final Identifier WIDE=Identifier.of("elementalwands","textures/entity/astral_glass_wide.png");
        private static final Identifier SLIM=Identifier.of("elementalwands","textures/entity/astral_glass_slim.png");
        private final PlayerEntityModel wide=playerModel(new Dilation(.7f),false),slim=playerModel(new Dilation(.7f),true);
        private GlassShell(FeatureRendererContext<PlayerEntityRenderState,PlayerEntityModel> context){super(context);}
        @Override public void render(MatrixStack m,OrderedRenderCommandQueue q,int light,PlayerEntityRenderState s,float limbAngle,float limbDistance){
            boolean thin=s.skinTextures.model()==PlayerSkinType.SLIM;
            // Bright as the double forms, then settled so the owner's skin shows through.
            double age=s.age/20.0,k=Math.clamp((age-.3)/.4,0,1);
            int alpha=(int)Math.round(255*(1-.58*k*k*(3-2*k)));
            q.getBatchingQueue(1).submitModel(thin?slim:wide,s,m,RenderLayer.getEntityTranslucent(thin?SLIM:WIDE),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,(alpha<<24)|0xFFFFFF,null,s.outlineColor,null);
        }
    }
}
