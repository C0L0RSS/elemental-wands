package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.NatureMeshes;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Extract immutable snapshots, then render attached angular vines for every tracked target. */
public final class EntangleClientEffects {
    private record Wrap(String mesh,Vec3d relative,float sx,float sy,int light,float reveal,
                        Vec3d cameraLocal,boolean firstPerson){}
    private static final RenderStateDataKey<List<Wrap>> WRAPS=RenderStateDataKey.create(()->"Nature wraps");
    public static void register(){
        WorldRenderEvents.END_EXTRACTION.register(context->{
            List<Wrap> draws=new ArrayList<>();
            var client=MinecraftClient.getInstance();
            float delta=context.tickCounter().getTickProgress(false);
            Vec3d camera=context.camera().getPos();
            boolean firstPerson=!context.camera().isThirdPerson();
            long time=context.world().getTime();
            var visibleStates=new java.util.HashMap<>(ClientPlayerData.getEntangledEntities());
            // Ultimate recovery may be earned without regular Entangle stacks.
            for(var entity:context.world().getEntities())if(entity instanceof FracturedGuardianEntity guardian
                    && guardian.natureOpening() && !visibleStates.containsKey(entity.getId()))
                visibleStates.put(entity.getId(),new ClientPlayerData.EntangleState(5,time,0,time-10));
            for(var item:visibleStates.entrySet()){
                var entity=context.world().getEntityById(item.getKey());
                if(!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()
                        || living instanceof com.anton.elementalwands.entity.AwakenedTreeEntity
                        || living.isSpectator() || (entity==client.getCameraEntity() && firstPerson))continue;
                if(entity.squaredDistanceTo(camera)>48*48 || !context.frustum().isVisible(entity.getBoundingBox().expand(.7)))continue;
                var state=item.getValue();int stacks=time<state.rootVisualUntilTick()?5:state.stacks();
                if(stacks==0)continue;
                boolean boss=entity instanceof FracturedGuardianEntity;
                boolean wide=entity.getWidth()>entity.getHeight();
                String type=boss?"boss":wide?"wide":"human";
                float w=boss?3.2f:wide?1.4f:.6f,h=boss?5.2f:wide?.9f:1.8f;
                float sx=(entity.getWidth()*.55f+.08f)/(w*.55f+.08f),sy=entity.getHeight()/h;
                String mode="lingering";
                if(stacks>=5){
                    if(boss)mode=((FracturedGuardianEntity)entity).natureOpening()?"opening":"resisting";
                    else if(time<state.rootVisualUntilTick())mode="rooted";
                }
                Vec3d relative=entity.getLerpedPos(delta).subtract(camera);
                Vec3d local=new Vec3d(-relative.x/sx,-relative.y/sy,-relative.z/sx);
                float growth=Math.clamp((time+delta-state.visualStartedAtTick())/10f,0,1);
                draws.add(new Wrap(type+"_"+stacks+"_"+mode,relative,sx,sy,
                        WorldRenderer.getLightmapCoordinates(context.world(),entity.getBlockPos()),
                        growth>=1?-1:growth*h,local,firstPerson));
            }
            context.worldState().setData(WRAPS,List.copyOf(draws));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context->{
            var draws=context.worldState().getDataOrDefault(WRAPS,List.of());
            if(draws.isEmpty())return;
            var out=context.consumers().getBuffer(NatureMeshes.LAYER);
            var matrices=context.matrices();
            for(Wrap draw:draws){
                matrices.push();matrices.translate(draw.relative.x,draw.relative.y,draw.relative.z);
                matrices.scale(draw.sx,draw.sy,draw.sx);
                NatureMeshes.draw(draw.mesh,out,matrices.peek(),draw.light,1,draw.reveal,draw.cameraLocal,draw.firstPerson);
                matrices.pop();
            }
        });
    }
    public static void tick(MinecraftClient client){
        if(client.world!=null)ClientPlayerData.expireEntangles(client.world.getTime());
    }
    private EntangleClientEffects(){}
}
