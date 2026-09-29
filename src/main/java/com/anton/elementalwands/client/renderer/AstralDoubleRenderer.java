package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.AstralDoubleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;

/** Real owner skin (including slim arms and outer skin layers), with the Space wand. */
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
        private static final Identifier SHIMMER=Identifier.ofVanilla("textures/block/white_concrete.png");
        private final PlayerEntityModel wide,slim;
        private SkinRenderer(EntityRendererFactory.Context c){
            super(c,new PlayerEntityModel(c.getPart(EntityModelLayers.PLAYER),false),.35f);
            wide=model;slim=new PlayerEntityModel(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(net.minecraft.client.model.Dilation.NONE,true),64,64).createModel(),true);
            addFeature(new HeldItemFeatureRenderer<>(this));
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
            // Short curved wisps taper into separated pixel steps. They orbit and
            // drift upward without cross/star silhouettes or covering the owner's skin.
            q.submitCustom(m,RenderLayer.getEntityTranslucent(SHIMMER),(entry,out)->{
                for(int i=0;i<10;i++) {
                    double angle=s.age*.035+i*2.39996;
                    float y=(float)((i/10.0+s.age*.007)%1)*1.9f;
                    float radius=.48f+.035f*(float)Math.sin(angle*1.7);
                    int alpha=100+(int)(65*(.5+.5*Math.sin(s.age*.13+i)));
                    wisp(out,entry,angle,y,radius,alpha,i);
                }
            });
        }
        private static void wisp(VertexConsumer out,MatrixStack.Entry m,double angle,float y,float radius,int alpha,int index) {
            for(int step=0;step<5;step++) {
                double a=angle-step*.085,b=a-.075;
                float x=(float)Math.cos(a)*radius,z=(float)Math.sin(a)*radius;
                float X=(float)Math.cos(b)*radius,Z=(float)Math.sin(b)*radius;
                float h=.026f-step*.004f,Y=y-step*.018f;
                // Alternating step sizes keep each tail asymmetric and faceted.
                if((step+index)%3==0)h*=.72f;
                int opacity=(int)(alpha*(1-step*.17));
                int color=(opacity<<24)|(step==0?0xE2C3FF:0xB88AEF);
                quad(out,m,color,new float[]{x,Y-h,z,X,Y-h-.014f,Z,X,Y+h-.014f,Z,x,Y+h,z},
                        (float)Math.cos(a),0,(float)Math.sin(a));
            }
        }
        private static void quad(VertexConsumer out,MatrixStack.Entry m,int color,float[] v,float nx,float ny,float nz) {
            for(int i=0;i<4;i++)out.vertex(m,v[i*3],v[i*3+1],v[i*3+2]).color(color)
                    .texture(i<2?0:1,i%3==0?0:1).overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,nx,ny,nz);
        }
    }
}
