package com.anton.elementalwands.client;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.util.FireLeapRules;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Flies the local caster along the server-committed arc with ordinary collision, so the camera
 * and body move like normal player movement. Remote leaps are kept only to draw their landing mark.
 */
public final class FireLeapClient {
    /** Fraction of the final horizontal speed carried out of the landing as a short skid. */
    private static final double CARRY=.35;
    /** Deviation from the planned step that counts as hitting something new; the leap then drops into normal falling. */
    private static final double BLOCKED=.08, TOUCHDOWN=.05, STANDING=.0784;
    public record Landing(Vec3d to,long until) {}
    private static final Map<Integer,Landing> LANDINGS=new HashMap<>();
    private static Vec3d from,to;
    private static PlayerEntity flyer;
    private static int step,duration;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.FireLeapPayload.ID,(payload,context) -> {
            var client=context.client();
            if(client.world==null || client.player==null) return;
            boolean own=payload.entityId()==client.player.getId();
            if(!payload.active()) { LANDINGS.remove(payload.entityId());if(own) from=null;return; }
            int ticks=FireLeapRules.duration(payload.from(),payload.to());
            LANDINGS.put(payload.entityId(),new Landing(payload.to(),client.world.getTime()+ticks+10));
            if(own) { from=payload.from();to=payload.to();duration=ticks;step=0;flyer=client.player; }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.world==null) { LANDINGS.clear();from=null;return; }
            // Respawn or a dimension change replaces the player object; never carry a leap across it.
            if(client.player!=flyer || !client.player.isAlive()) from=null;
            long now=client.world.getTime();LANDINGS.values().removeIf(landing -> landing.until<now);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { LANDINGS.clear();from=null; });
    }
    public static boolean active() { return from!=null; }
    private static Vec3d aimPos,aimTarget;
    private static float aimYaw,aimPitch;
    private static long aimTick=Long.MIN_VALUE;
    /** The current validated landing, or null. Recomputed only when the body, view or tick changes. */
    public static Vec3d aim(PlayerEntity player) {
        long tick=player.getEntityWorld().getTime();
        if(tick!=aimTick || !player.getEntityPos().equals(aimPos) || player.getYaw()!=aimYaw || player.getPitch()!=aimPitch) {
            aimTick=tick;aimPos=player.getEntityPos();aimYaw=player.getYaw();aimPitch=player.getPitch();
            Vec3d target=FireLeapRules.target(player);
            aimTarget=target!=null && FireLeapRules.supported(player.getEntityWorld(),player,aimPos,1.5) ? target : null;
        }
        return aimTarget;
    }
    public static Collection<Landing> landings() { return LANDINGS.values(); }
    /** Replaces one movement tick of the local player; false leaves vanilla travel in charge. */
    public static boolean step(PlayerEntity player) {
        if(from==null || player!=flyer || player!=MinecraftClient.getInstance().player) return false;
        if(!player.isAlive() || player.hasVehicle() || player.isTouchingWater() || player.isInLava()) { from=null;return false; }
        step++;
        Vec3d planned=FireLeapRules.position(from,to,step/(double)duration),delta=planned.subtract(player.getEntityPos());
        // Press the last step slightly into the floor so collision reports the touchdown this tick.
        player.move(MovementType.SELF,step>=duration ? delta.add(0,-TOUCHDOWN,0) : delta);
        Vec3d moved=player.getEntityPos();player.fallDistance=0;
        if(step>=duration || moved.distanceTo(planned)>BLOCKED) {
            // Landing keeps a little of the leap's run; an interrupted leap simply falls from where it stopped.
            Vec3d last=planned.subtract(FireLeapRules.position(from,to,(step-1)/(double)duration));
            player.setVelocity(step>=duration ? new Vec3d(last.x*CARRY,-STANDING,last.z*CARRY) : new Vec3d(0,Math.min(0,last.y),0));
            from=null;
        } else player.setVelocity(delta);
        return true;
    }
    private FireLeapClient() {}
}
