package com.anton.elementalwands.entity.roc;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Immutable authored channels and sockets shared by server hitboxes and client presentation. */
public final class StormRocPose {
    private record Channel(double[] time, float[][] value) {
        float[] at(double t) {
            int i=Arrays.binarySearch(time,t);
            if(i>=0)return value[i];
            i=-i-1;
            if(i==0)return value[0];
            if(i>=time.length)return value[value.length-1];
            float f=(float)((t-time[i-1])/(time[i]-time[i-1]));
            float[] v=new float[3];
            for(int a=0;a<3;a++)v[a]=value[i-1][a]+f*(value[i][a]-value[i-1][a]);
            return v;
        }
    }
    private record Clip(boolean loop,double ticks,Map<String,Channel[]> bones,Map<String,Vec3d[]> sockets) {
        double time(double tick) { return loop ? Math.max(0,tick)%ticks : Math.clamp(tick,0,ticks); }
    }
    private static class Data { static final Map<String,Clip> CLIPS=load(); }
    private StormRocPose() {}
    private static JsonObject read(String path) {
        try(var stream=StormRocPose.class.getResourceAsStream("/assets/elementalwands/"+path)) {
            if(stream==null)throw new IllegalStateException("Missing Roc track: "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static float[] vector(JsonElement e) {
        var a=e.getAsJsonArray();return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};
    }
    private static Channel channel(JsonElement e,float fallback) {
        if(e==null)return new Channel(new double[]{0},new float[][]{{fallback,fallback,fallback}});
        if(e.isJsonArray())return new Channel(new double[]{0},new float[][]{vector(e)});
        var sorted=new TreeMap<Double,float[]>();
        e.getAsJsonObject().entrySet().forEach(k->sorted.put(Double.parseDouble(k.getKey())*20,vector(k.getValue())));
        return new Channel(sorted.keySet().stream().mapToDouble(Double::doubleValue).toArray(),sorted.values().toArray(float[][]::new));
    }
    private static Map<String,Clip> load() {
        var out=new HashMap<String,Clip>();
        var sockets=read("storm_roc_sockets.json");
        read("geckolib/animations/storm_roc.animation.json").getAsJsonObject("animations").entrySet().forEach(entry->{
            String name=entry.getKey().substring("animation.storm_roc.".length());
            var clip=entry.getValue().getAsJsonObject();var bones=new LinkedHashMap<String,Channel[]>();
            clip.getAsJsonObject("bones").entrySet().forEach(b->{
                var channels=b.getValue().getAsJsonObject();
                bones.put(b.getKey(),new Channel[]{channel(channels.get("rotation"),0),channel(channels.get("position"),0),channel(channels.get("scale"),1)});
            });
            var frames=sockets.getAsJsonArray(name);var points=new HashMap<String,Vec3d[]>();
            for(String key:frames.get(0).getAsJsonObject().keySet()) {
                var samples=new Vec3d[frames.size()];
                for(int i=0;i<samples.length;i++){float[] v=vector(frames.get(i).getAsJsonObject().get(key));samples[i]=new Vec3d(v[0],v[1],v[2]);}
                points.put(key,samples);
            }
            out.put(name,new Clip(clip.get("loop").getAsBoolean(),clip.get("animation_length").getAsDouble()*20,Collections.unmodifiableMap(bones),Map.copyOf(points)));
        });
        return Map.copyOf(out);
    }
    public static Map<String,float[]> pose(String name,float tick) {
        Clip clip=Data.CLIPS.getOrDefault(name,Data.CLIPS.get("hunched"));double t=clip.time(tick);
        var out=new LinkedHashMap<String,float[]>();
        clip.bones.forEach((bone,channels)->{
            float[] values=new float[9];
            for(int i=0;i<3;i++)System.arraycopy(channels[i].at(t),0,values,i*3,3);
            out.put(bone,values);
        });
        return out;
    }
    public static Vec3d socket(String name,double tick,String key) {
        Clip clip=Data.CLIPS.getOrDefault(name,Data.CLIPS.get("hunched"));
        double t=clip.time(tick);Vec3d[] points=clip.sockets.get(key);
        int i=Math.min((int)t,points.length-1);return points[i].lerp(points[Math.min(i+1,points.length-1)],t-i);
    }
}
