package com.anton.elementalwands.client.renderer;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;

/** Optional client fixture: uses fully loaded Minecraft assets; never opens a world. */
public final class GuardianFloorClientSmokeMod implements ClientModInitializer {
    private boolean done;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.getOverlay()!=null) return;
            done=true;
            try {
                var manager=client.getAtlasManager();
                boolean oldLookupRejected=false;
                try { manager.getAtlasTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE); }
                catch (IllegalArgumentException expected) { oldLookupRejected=true; }
                require(oldLookupRejected,"Fixture no longer reproduces the reported invalid atlas ID");
                var atlas=manager.getAtlasTexture(Atlases.BLOCKS);
                for (String texture:new String[]{"quartz_block_bottom","polished_deepslate","polished_andesite"}) {
                    var id=Identifier.ofVanilla("block/"+texture);
                    require(atlas.getSprite(id).getContents().getId().equals(id),"Missing floor texture: "+id);
                }
                var socketModel=client.getBlockRenderManager().getModel(com.anton.elementalwands.registry.ModBlocks.GUARDIAN_SOCKET.getDefaultState());
                require(socketModel.particleSprite().getContents().getId().equals(Identifier.ofVanilla("block/chiseled_deepslate")),"Church socket model is missing");
                var heartState=new net.minecraft.client.render.item.ItemRenderState();
                client.getItemModelManager().clearAndUpdate(heartState,new net.minecraft.item.ItemStack(com.anton.elementalwands.registry.ModItems.GUARDIAN_HEART),net.minecraft.item.ItemDisplayContext.GUI,null,null,0);
                require(!heartState.isEmpty(),"Guardian Heart model is empty");
                require(heartState.getParticleSprite(net.minecraft.util.math.random.Random.create()).getContents().getId().equals(Identifier.ofVanilla("item/heart_of_the_sea")),"Guardian Heart texture is missing");
                var animations=software.bernie.geckolib.cache.GeckoLibResources.getBakedAnimations().get(Identifier.of("elementalwands","fractured_guardian"));
                require(animations!=null,"Guardian animation package was not baked");
                for(String clip:new String[]{"arrival_fall","arrival_land","awaken","guard_break","phase_change","slam_fast","throw_fast","fan"}) require(animations.getAnimation("animation.fractured_guardian."+clip)!=null,"Missing baked entrance clip: "+clip);
                for (int stage=1;stage<=3;stage++) for(String suffix:new String[]{"","_glowmask"}) {
                    var id=Identifier.of("elementalwands","textures/entity/fractured_guardian_cracks_"+stage+suffix+".png");
                    require(client.getResourceManager().getResource(id).isPresent(),"Missing guard material: "+id);
                    client.getTextureManager().getTexture(id);
                }
                var guardModel=new com.anton.elementalwands.client.model.FracturedGuardianModel();
                var guardState=new FracturedGuardianRenderState();
                guardModel.getBakedModel(guardModel.getModelResource(guardState));
                var core=guardModel.getBone("core").orElseThrow();
                var leftPlate=guardModel.getBone("chest_plate_1").orElseThrow();
                for(float time:new float[]{0,12,24,80,164,180,194}) {
                    for(var bone:guardModel.getAnimationProcessor().getRegisteredBones()) {
                        bone.setPosX(0);bone.setPosY(0);bone.setPosZ(0);
                        bone.setRotX(0);bone.setRotY(0);bone.setRotZ(0);
                        bone.setScaleX(1);bone.setScaleY(1);bone.setScaleZ(1);
                    }
                    guardState.guardTime=time;
                    guardModel.setCustomAnimations(new software.bernie.geckolib.animatable.processing.AnimationState<>(guardState,null,0,new it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap<>(),null));
                    float open=com.anton.elementalwands.entity.GuardianGuardRules.openness(time);
                    require(Math.abs(core.getPosZ()+12*open)<.001,"Core visual travel disagrees with server exposure");
                    require(Math.abs(core.getScaleZ()-(1+1.4f*open))<.001,"Core scale disagrees with preview");
                    require(Math.abs(leftPlate.getRotY()-Math.toRadians(68*open))<.001,"Rib rotation disagrees with preview");
                    for(int stage=1;stage<=3;stage++) {
                        guardState.guardCracks=stage;
                        require(guardModel.getTextureResource(guardState).getPath().contains("cracks_"+stage),"Wrong fracture material");
                    }
                }
                checkFan();
                checkUnstable();
                checkStoneCluster();
                NatureClientChecks.check(client);
                FireClientChecks.check(client);
                for (float radius:new float[]{0,1.25f,4,64}) checkFloor(radius, java.util.Set.of());
                checkFloor(64,java.util.Set.of(net.minecraft.util.math.ChunkPos.toLong(0,0)));
                Files.writeString(Path.of("FLOOR_CLIENT_PASSED.txt"),
                        "Old atlas lookup rejected; production lookup and all three sprites resolved. Actual floor submission built valid entity vertex buffers at hidden, partial, small, and full 138x138 extents. Church socket and Guardian Heart models and the two arrival clips/awakening also resolved from actual client assets. No world opened.\n");
                System.out.println("GUARDIAN FLOOR CLIENT CHECK PASSED");
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("FLOOR_CLIENT_FAILED.txt"),failure.toString()); } catch (Exception ignored) {}
            } finally {
                if(Boolean.getBoolean("ew.nature.visualWorld") && !Files.exists(Path.of("FLOOR_CLIENT_FAILED.txt"))) NatureVisualClientSmoke.start(client);
                else client.scheduleStop();
            }
        });
    }
    private static void checkFan() {
        var state=new FracturedGuardianRenderState();int[] blocks={0},glows={0};
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),
                new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
            if(method.getName().equals("submitBlock")) {blocks[0]++;return null;}
            require(method.getName().equals("submitCustom"),"Unexpected fan render command");glows[0]++;
            try(var allocator=new BufferAllocator(8192)) {
                var vertices=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
                ((OrderedRenderCommandQueue.Custom)args[2]).render(((MatrixStack)args[0]).peek(),vertices);
                try(var buffer=vertices.end()) {
                    require(buffer.getDrawParameters().vertexCount()==8,"Fan glow is not bounded");
                    var data=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR.getVertexSize();
                    for(int i=0;i<8;i++)for(int axis=0;axis<3;axis++) {
                        float value=data.getFloat(i*stride+axis*4);
                        require(Float.isFinite(value) && Math.abs(value)<8,"Fan geometry invalid");
                    }
                }
            }
            return null;
        });
        state.fanTime=14;state.fanPitch=-35;GuardianFanVisual.submit(state,new MatrixStack(),queue);
        require(blocks[0]==3 && glows[0]==3,"Barrage does not display three release stones");
        state.fanTime=18;GuardianFanVisual.submit(state,new MatrixStack(),queue);
        require(blocks[0]==3,"Held fan stones remain after release");
        state.unstable=true;state.fanTime=22;GuardianFanVisual.submit(state,new MatrixStack(),queue);
        require(blocks[0]==6,"Second burst lacks a new telegraph");
        state.fanTime=30;GuardianFanVisual.submit(state,new MatrixStack(),queue);
        require(blocks[0]==9,"Third burst lacks a new telegraph");
        state.fanTime=34;GuardianFanVisual.submit(state,new MatrixStack(),queue);
        require(blocks[0]==9,"Stones linger after final release");
    }
    private static void checkUnstable() {
        var state=new FracturedGuardianRenderState();
        state.unstable=true;
        int[] submissions={0};
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),
            new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
                require(method.getName().equals("submitCustom"),"Unexpected unstable render submission");
                submissions[0]++;
                try(var allocator=new BufferAllocator(64000)) {
                    var vertices=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
                    ((OrderedRenderCommandQueue.Custom)args[2]).render(((MatrixStack)args[0]).peek(),vertices);
                    try(var buffer=vertices.end()) {
                        int count=buffer.getDrawParameters().vertexCount();
                        require(count>0 && count<=400,"Unbounded phase discharge mesh");
                        var data=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR.getVertexSize();
                        for(int i=0;i<count;i++)for(int axis=0;axis<3;axis++) {
                            float value=data.getFloat(i*stride+axis*4);
                            require(Float.isFinite(value) && Math.abs(value)<7,"Invalid phase discharge vertex");
                        }
                    }
                }
                return null;
            });
        for(float time:new float[]{0,16,32,48,63,100}) {
            state.magicTime=time;state.phaseTime=time<64?time:-1;
            GuardianUnstableVisual.submit(state,new MatrixStack(),queue);
        }
        require(submissions[0]==6,"Phase discharge submission count changed");
        state.beamTime=10;GuardianUnstableVisual.submit(state,new MatrixStack(),queue);
        require(submissions[0]==6,"Phase discharges obscure beam telegraph");
    }
    private static void checkFloor(float radius, java.util.Set<Long> hidden) {
        int[] submissions={0};
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),
                new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args) -> {
                    require(method.getName().equals("submitCustom") && args.length==3,"Unexpected floor submission");
                    submissions[0]++;
                    try (var allocator=new BufferAllocator(4*1024*1024)) {
                        var vertices=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                        ((OrderedRenderCommandQueue.Custom)args[2]).render(((MatrixStack)args[0]).peek(),vertices);
                        try (var buffer=vertices.end()) {
                            int side=radius>=64?138:2*(int)Math.ceil(radius);
                            require(buffer.getDrawParameters().vertexCount()==(side*side-hidden.size())*4,"Floor vertex count/extent changed unexpectedly");
                            var data=buffer.getBuffer();
                            int stride=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSize();
                            for (int quad=0;quad<side*side-hidden.size();quad++) {
                                float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minZ=minX,maxZ=maxX;
                                for (int corner=0;corner<4;corner++) {
                                    int offset=(quad*4+corner)*stride;
                                    float x=data.getFloat(offset),y=data.getFloat(offset+4),z=data.getFloat(offset+8);
                                    require(Float.isFinite(x) && Float.isFinite(z) && y==37,"Invalid floor vertex or floor-height drift");
                                    minX=Math.min(minX,x); maxX=Math.max(maxX,x); minZ=Math.min(minZ,z); maxZ=Math.max(maxZ,z);
                                }
                                require(maxX-minX<=1 && maxZ-minZ<=1,"A floor tile is larger than one Minecraft block");
                            }
                        }
                    }
                    return null;
                });
        GuardianArenaFloor.submit(new MatrixStack(),queue,37,radius,hidden);
        require(submissions[0]==(radius==0?0:1),"Floor stopped batching its surface into one submission");
    }
    private static void checkStoneCluster() {
        for(int mass:new int[]{0,1,25,50,75,100})for(float age:new float[]{0,5,20}) {
            var state=new StoneClusterRenderer.State();
            state.mass=mass;state.targetMass=mass;state.beforeMass=Math.max(0,mass-25);
            state.held=true;state.gatherAge=age;state.time=13;state.light=0x00F000F0;
            state.groundDelta=new net.minecraft.util.math.Vec3d(1,-3,1);
            int[] count={0};
            var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),
                new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
                    require(method.getName().equals("submitCustom"),"Unexpected Stone render command");count[0]++;
                    try(var allocator=new BufferAllocator(512000)) {
                        var vertices=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                        ((OrderedRenderCommandQueue.Custom)args[2]).render(((MatrixStack)args[0]).peek(),vertices);
                        try(var buffer=vertices.end()) {
                            int n=buffer.getDrawParameters().vertexCount();require(n>0 && n<=5000,"Unbounded or empty Stone mesh");
                            var data=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSize();
                            for(int i=0;i<n;i++) {
                                double radius=0;
                                for(int axis=0;axis<3;axis++) { float value=data.getFloat(i*stride+axis*4);require(Float.isFinite(value),"Nonfinite Stone vertex");radius+=value*value; }
                                if(age>=20)require(Math.sqrt(radius)<=com.anton.elementalwands.util.StoneClusterRules.radius(mass)+.001,"Stone mesh exceeds collision radius");
                            }
                        }
                    }return null;
                });
            StoneClusterRenderer.submitMesh(state,new MatrixStack(),queue);
            require(count[0]==1,"Stone mesh stopped batching");
        }
        System.out.println("STONE CLUSTER CLIENT MESH CHECKS PASSED");
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
