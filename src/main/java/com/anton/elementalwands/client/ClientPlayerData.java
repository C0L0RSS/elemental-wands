package com.anton.elementalwands.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anton.elementalwands.data.WizardAffinity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class ClientPlayerData {

    private static int unlockedSkills = 0;
    private static String affinity = "NONE";
    private static long flux;
    private static double xp;
    private static List<Integer> credits=List.of(0,0,0);
    public static double xp(){return xp;}
    public static List<Integer> credits(){return credits;}
    public static boolean free(com.anton.elementalwands.data.WandSpells.Spell spell){return spell!=null&&!owns(spell)&&com.anton.elementalwands.util.SpellBooks.availableTier(credits,spell.category())>=0;}
    public static void setProgress(double value,List<Integer> counts){xp=com.anton.elementalwands.data.ElementLevels.clamp(value);credits=List.copyOf(counts);}

    private static float fireHeat;
    private static boolean fireOverheated;
    private static long hopReadyAt;
    private static int flashActive,flashArmed,flashDuration=20;
    private static long flashReadyAt;
    private static final long[] flashSlots=new long[3];
    public static void setFlashover(int active,int armed,int remaining,int duration,List<Integer> slots,long now) {
        flashActive=Math.clamp(active,0,3);flashArmed=Math.clamp(armed,0,flashActive);flashDuration=Math.max(1,duration);flashReadyAt=now+Math.max(0,remaining);
        for(int i=0;i<3;i++) { int value=i<slots.size()?slots.get(i):0;flashSlots[i]=value<0?value:now+value; }
    }
    public static String flashSlotLabel(int slot,long now) {
        long value=flashSlots[slot];
        return value==-2?"Armed":value==-1?"Placed":value<=now?"Ready":String.format(java.util.Locale.ROOT,"%.1fs",(value-now)/20.0);
    }
    public static int flashActive() { return flashActive; }
    public static int flashArmed() { return flashArmed; }
    public static int flashDuration() { return flashDuration; }
    public static long flashRemaining(long now) { return Math.max(0,flashReadyAt-now); }
    public static void setFireBuild(float heat, boolean overheated, int hopRemaining, long now) {
        fireHeat = Math.clamp(heat,0,100); fireOverheated=overheated; hopReadyAt=now+Math.max(0,hopRemaining);
    }
    public static float fireHeat() { return fireHeat; }
    public static boolean fireOverheated() { return fireOverheated; }
    public static long hopRemaining(long now) { return Math.max(0,hopReadyAt-now); }
    private static List<String> owned = List.of();
    public static boolean owns(com.anton.elementalwands.data.WandSpells.Spell spell) { return spell != null && (spell.price() == 0 || owned.contains(spell.id())); }
    public static List<String> owned() { return owned; }
    public static void setOwned(List<String> ids) { owned = List.copyOf(ids); }
    private static List<String> loadout = List.of("fractured_beam");
    private static boolean canEdit;
    public static long flux() { return flux; }
    public static boolean canEdit() { return canEdit; }
    public static int skills() { return unlockedSkills; }
    public static List<String> loadout() { return loadout; }
    public static void setHubData(long newFlux, List<String> ids, boolean editable) {
        flux = newFlux;
        loadout = com.anton.elementalwands.data.WandSpells.reconcile(getAffinity(), ids, owned);
        canEdit = editable;
    }
    private static List<BlockPos> natureSeedlings = List.of();
    private static int stoneMass, stoneDuration = 30;
    private static long stoneReadyAt;

    public static void setStoneCluster(int mass, int remaining, int duration, long now) {
        stoneMass = Math.clamp(mass,0,100); stoneDuration = Math.max(1,duration);
        stoneReadyAt = now + Math.max(0,remaining);
    }
    public static int stoneMass() { return stoneMass; }
    public static int stoneDuration() { return stoneDuration; }
    public static long stoneRemaining(long now) { return Math.max(0,stoneReadyAt-now); }
    public static void clearStoneCluster() { stoneMass=0; stoneDuration=30; stoneReadyAt=0; }
    private static final Map<Integer, EntangleState> entangledEntities = new HashMap<>();

    public static final int SKILL_SECONDARY = 1;
    public static final int SKILL_ULTIMATE  = 2;

    private ClientPlayerData() {}

    public static boolean isSecondaryUnlocked() {
        return (unlockedSkills & SKILL_SECONDARY) != 0;
    }

    public static boolean isUltimateUnlocked() {
        return (unlockedSkills & SKILL_ULTIMATE) != 0;
    }

    public static WizardAffinity getAffinity() {
        try {
            return WizardAffinity.valueOf(affinity);
        } catch (IllegalArgumentException e) {
            return WizardAffinity.NONE;
        }
    }

    public static void setUnlockedSkills(int skills, String affinityStr) {
        unlockedSkills = skills;
        affinity = affinityStr;
    }

    public static void setNatureSeedlings(List<BlockPos> positions) {
        natureSeedlings = List.copyOf(positions);
    }

    public static List<BlockPos> getNatureSeedlings() {
        return natureSeedlings;
    }

    public static void clearNatureSeedlings() {
        natureSeedlings = List.of();
    }

    public static void setEntangleStacks(int entityId, int stacks, long changedAtTick,
            int rootVisualTicks) {
        if (stacks <= 0 && rootVisualTicks<=0) {
            entangledEntities.remove(entityId);
            return;
        }

        int clampedStacks = Math.clamp(stacks,0,5);
        long rootVisualUntilTick = changedAtTick + Math.max(0, rootVisualTicks);
        EntangleState old=entangledEntities.get(entityId);
        long visualStart=old==null?changedAtTick:old.visualStartedAtTick();
        entangledEntities.put(entityId,
                new EntangleState(clampedStacks, changedAtTick, rootVisualUntilTick,visualStart));
    }

    public static EntangleState getEntangleState(int entityId) {
        return entangledEntities.get(entityId);
    }

    public static int getEntangleStacks(int entityId) {
        EntangleState state = entangledEntities.get(entityId);
        return state != null ? state.stacks() : 0;
    }

    public static Map<Integer, EntangleState> getEntangledEntities() {
        return Collections.unmodifiableMap(entangledEntities);
    }

    public static void clearEntangleState(int entityId) {
        entangledEntities.remove(entityId);
    }

    public static void clearEntangleStates() {
        entangledEntities.clear();
    }

    public static void reset() {
        flashActive=flashArmed=0;flashReadyAt=0;flashDuration=20;java.util.Arrays.fill(flashSlots,0);
        fireHeat=0; fireOverheated=false; hopReadyAt=0;
        clearStoneCluster();
        unlockedSkills = 0;
        affinity = "NONE";
        owned = List.of(); xp=0;credits=List.of(0,0,0); flux = 0; loadout = List.of("fractured_beam"); canEdit = false;
        natureSeedlings = List.of();
        entangledEntities.clear();
    }

    public static void expireEntangles(long now){entangledEntities.values().removeIf(s->now-s.changedAtTick()>105 || (s.stacks()==0 && now>=s.rootVisualUntilTick()));}
    public record EntangleState(int stacks, long changedAtTick, long rootVisualUntilTick,long visualStartedAtTick) {}
}
