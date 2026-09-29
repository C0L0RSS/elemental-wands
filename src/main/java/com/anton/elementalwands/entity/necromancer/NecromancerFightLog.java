package com.anton.elementalwands.entity.necromancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Operator tuning aid ({@code /ew necromancer log on}). While enabled, each fight records its
 * casts, stages and waves, every hit on a player from the boss or its army (before and after
 * armor, or swallowed by hit immunity) and the damage each player deals; at the end it tells
 * operators a summary and writes the whole record under {@code logs/}.
 */
public final class NecromancerFightLog {
    private static final Logger LOG = LoggerFactory.getLogger("elementalwands-necromancer");
    private static boolean enabled;

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Records successful hits on players; lost hits are reported by the boss's own attacks. */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!enabled || !(entity instanceof ServerPlayerEntity player) || !(player.getEntityWorld() instanceof ServerWorld world)) return;
            NecromancerEntity boss = owner(world, source);
            if (boss != null) boss.combat().log().playerHit(world.getTime(), player, source, base, taken);
        });
    }

    private static NecromancerEntity owner(ServerWorld world, DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof NecromancerEntity boss) return boss;
        if (attacker instanceof NecromancerMinion minion && minion.minionState().boss != null
                && world.getEntity(minion.minionState().boss) instanceof NecromancerEntity boss) return boss;
        return null;
    }

    private record Taken(float raw, float afterArmor, int hits, int lost) {
        Taken add(float r, float a, boolean hit) { return new Taken(raw + r, afterArmor + a, hits + (hit ? 1 : 0), lost + (hit ? 0 : 1)); }
    }

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Taken> taken = new LinkedHashMap<>();
    private final Map<String, Float> dealt = new LinkedHashMap<>();
    private final Map<String, Integer> casts = new TreeMap<>();
    private long started = -1, stageStarted;
    private String stage = "duel A";

    boolean active() { return started >= 0; }

    void begin(long now, int players) {
        if (!enabled) return;
        lines.clear(); taken.clear(); dealt.clear(); casts.clear();
        started = stageStarted = now; stage = "duel A";
        line(now, "Fight begins with " + players + " player(s)");
    }

    void cast(long now, String what, String target) {
        if (!active()) return;
        casts.merge(what, 1, Integer::sum);
        line(now, "cast " + what + (target == null ? "" : " → " + target));
    }

    void stage(long now, String name) {
        if (!active()) return;
        line(now, "stage " + name + " (previous stage " + stage + " lasted " + seconds(now - stageStarted) + ")");
        stage = name; stageStarted = now;
    }

    void note(long now, String text) { if (active()) line(now, text); }

    void playerHit(long now, ServerPlayerEntity player, DamageSource source, float raw, float afterArmor) {
        if (!active()) return;
        String name = player.getName().getString();
        taken.merge(name, new Taken(0, 0, 0, 0).add(raw, afterArmor, true), (a, b) -> a.add(raw, afterArmor, true));
        line(now, name + " took " + round(afterArmor) + " (" + round(raw) + " before armor) from " + source.getName()
                + (source.getAttacker() instanceof NecromancerEntity ? "" : " [" + source.getAttacker().getName().getString() + "]"));
    }

    /** A boss attack that connected but dealt nothing: the player was still immune from a previous hit. */
    void lostHit(long now, ServerPlayerEntity player, String what, float amount) {
        if (!active()) return;
        String name = player.getName().getString();
        taken.merge(name, new Taken(0, 0, 0, 1), (a, b) -> a.add(0, 0, false));
        line(now, name + " ignored " + what + " (" + round(amount) + "): hit immunity");
    }

    void bossHit(long now, Entity attacker, float lost) {
        if (!active() || lost <= 0) return;
        String name = attacker == null ? "environment" : attacker.getName().getString();
        dealt.merge(name, lost, Float::sum);
    }

    /** Ends the record: tells operators in the boss's world and writes the file. Safe to call twice. */
    void finish(NecromancerEntity boss, String outcome) {
        if (!active() || !(boss.getEntityWorld() instanceof ServerWorld world)) return;
        long now = world.getTime();
        line(now, "Fight ends: " + outcome + " (last stage " + stage + " lasted " + seconds(now - stageStarted) + ")");
        List<String> summary = new ArrayList<>();
        summary.add("Necromancer fight: " + outcome + " after " + seconds(now - started) + ", stage " + stage);
        dealt.forEach((name, amount) -> summary.add("  " + name + " dealt " + round(amount)));
        taken.forEach((name, t) -> summary.add("  " + name + " took " + round(t.afterArmor()) + " (" + round(t.raw()) + " before armor) over "
                + t.hits() + " hits; " + t.lost() + " hits lost to immunity"));
        summary.add("  casts " + casts);
        started = -1;
        String file = "necromancer-fight-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
        Path path = world.getServer().getRunDirectory().resolve("logs").resolve(file);
        try {
            Files.createDirectories(path.getParent());
            List<String> out = new ArrayList<>(summary);
            out.add("");
            out.addAll(lines);
            Files.write(path, out);
            summary.add("  full log: logs/" + file);
        } catch (IOException e) {
            LOG.warn("Could not write the Necromancer fight log", e);
        }
        for (ServerPlayerEntity op : world.getPlayers(p -> world.getServer().getPlayerManager().isOperator(p.getPlayerConfigEntry())))
            summary.forEach(s -> op.sendMessage(Text.literal(s), false));
        summary.forEach(LOG::info);
    }

    private void line(long now, String text) { lines.add("[" + seconds(now - started) + "] " + text); }
    private static String seconds(long ticks) { return String.format("%.1fs", ticks / 20.0); }
    private static String round(float value) { return String.format("%.1f", value); }
}
