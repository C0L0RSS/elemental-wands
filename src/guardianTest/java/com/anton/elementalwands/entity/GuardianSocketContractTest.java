package com.anton.elementalwands.entity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/** Compare the server release socket against GeckoLib's real mirrored bone transforms. */
final class GuardianSocketContractTest {
    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat());
    }
    private static Vector3f sample(JsonObject keys, float time) {
        if (keys == null) return new Vector3f();
        float previousTime = -1;
        Vector3f previous = null;
        var entries = new ArrayList<>(keys.entrySet());
        entries.sort(java.util.Comparator.comparingDouble(e -> Double.parseDouble(e.getKey())));
        for (var entry : entries) {
            float nextTime = Float.parseFloat(entry.getKey());
            Vector3f next = vector(entry.getValue().getAsJsonArray());
            if (nextTime >= time) return previous == null ? next : previous.lerp(next,(time-previousTime)/(nextTime-previousTime));
            previous = next; previousTime = nextTime;
        }
        return previous;
    }
    static void run() {
        try {
            Path assets = Path.of("src/main/resources/assets/elementalwands/geckolib");
            JsonArray geometry = JsonParser.parseString(Files.readString(assets.resolve("models/fractured_guardian.geo.json")))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            JsonObject animation = JsonParser.parseString(Files.readString(assets.resolve("animations/fractured_guardian.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("animation.fractured_guardian.throw").getAsJsonObject("bones");
            var bones = new LinkedHashMap<String,GeoBone>();
            for (var entry : geometry) {
                JsonObject json = entry.getAsJsonObject(); String name = json.get("name").getAsString();
                GeoBone parent = json.has("parent") ? bones.get(json.get("parent").getAsString()) : null;
                GeoBone bone = new GeoBone(parent,name,false,0.0,false,false);
                Vector3f pivot = vector(json.getAsJsonArray("pivot"));
                bone.updatePivot(-pivot.x,pivot.y,pivot.z); bones.put(name,bone);
            }
            var chain = new ArrayList<GeoBone>();
            for (GeoBone bone = bones.get("right_hand");bone != null;bone = bone.getParent()) chain.add(bone);
            Collections.reverse(chain);
            double largest = 0;
            for (int tick=0;tick<=44;tick++) {
                for (var entry : bones.entrySet()) {
                    JsonObject channels = animation.getAsJsonObject(entry.getKey());
                    Vector3f rotation = sample(channels == null ? null : channels.getAsJsonObject("rotation"),tick/20f);
                    Vector3f position = sample(channels == null ? null : channels.getAsJsonObject("position"),tick/20f);
                    entry.getValue().updateRotation((float)Math.toRadians(-rotation.x),(float)Math.toRadians(-rotation.y),(float)Math.toRadians(rotation.z));
                    entry.getValue().updatePosition(position.x,position.y,position.z);
                }
                for (float yaw : new float[]{0,90,-90,37}) {
                    MatrixStack matrices = new MatrixStack();
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));
                    for (GeoBone bone : chain) RenderUtil.prepMatrixForBone(matrices,bone);
                    Vector3f rendered = matrices.peek().getPositionMatrix().transformPosition(new Vector3f(34/16f,3/16f,-22/16f));
                    largest = Math.max(largest,GuardianThrowSocket.worldOffset(yaw,tick).distanceTo(new net.minecraft.util.math.Vec3d(rendered)));
                }
            }
            if (largest > .0001) throw new AssertionError("Server rock socket diverges from live GeckoLib transform by " + largest + " blocks");
            System.out.println("Guardian grip checks passed: 180 GeckoLib bone/yaw comparisons; maximum error="+largest+" blocks.");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
