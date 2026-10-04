package com.anton.elementalwands.entity;

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
import java.util.function.BooleanSupplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Operator tuning aid shared by the bosses ({@code /ew <boss> log on}). While enabled, each fight
 * records its casts and stages, every hit on a player (before and after armor, or swallowed by hit
 * immunity) and the damage each player deals; at the end it tells operators a summary and writes
 * the whole record under {@code logs/}.
 */
public class BossFightLog {
    private static final Logger LOG = LoggerFactory.getLogger("elementalwands-boss-log");
    private final String title, file;
    private final String firstStage;
    private final BooleanSupplier enabled;

    private record Taken(float raw, float afterArmor, int hits, int lost) {
        Taken add(float r, float a, boolean hit) { return new Taken(raw + r, afterArmor + a, hits + (hit ? 1 : 0), lost + (hit ? 0 : 1)); }
    }

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Taken> taken = new LinkedHashMap<>();
    private final Map<String, Float> dealt = new LinkedHashMap<>();
    private final Map<String, Integer> casts = new TreeMap<>();
    private long started = -1, stageStarted;
    private String stage;

    /** {@code file} names the log, as in {@code logs/<file>-fight-<time>.txt}. */
    protected BossFightLog(String title, String file, String firstStage, BooleanSupplier enabled) {
        this.title = title; this.file = file; this.firstStage = firstStage; this.enabled = enabled;
        this.stage = firstStage;
    }

    public boolean active() { return started >= 0; }

    public void begin(long now, int players) { begin(now, players, ""); }
    public void begin(long now, int players, String details) {
        if (!enabled.getAsBoolean()) return;
        lines.clear(); taken.clear(); dealt.clear(); casts.clear();
        started = stageStarted = now; stage = firstStage;
        line(now, "Fight begins with " + players + " player(s)" + details);
    }

    public void cast(long now, String what, String target) {
        if (!active()) return;
        casts.merge(what, 1, Integer::sum);
        line(now, "cast " + what + (target == null ? "" : " → " + target));
    }

    public void stage(long now, String name) {
        if (!active()) return;
        line(now, "stage " + name + " (previous stage " + stage + " lasted " + seconds(now - stageStarted) + ")");
        stage = name; stageStarted = now;
    }

    public void note(long now, String text) { if (active()) line(now, text); }

    public void playerHit(long now, ServerPlayerEntity player, DamageSource source, float raw, float afterArmor) {
        if (!active()) return;
        String name = player.getName().getString();
        taken.merge(name, new Taken(0, 0, 0, 0).add(raw, afterArmor, true), (a, b) -> a.add(raw, afterArmor, true));
        Entity attacker = source.getAttacker();
        line(now, name + " took " + round(afterArmor) + " (" + round(raw) + " before armor) from " + source.getName()
                + (attacker == null || attacker instanceof WandBoss ? "" : " [" + attacker.getName().getString() + "]"));
    }

    /** A boss attack that connected but dealt nothing: the player was still immune from a previous hit. */
    public void lostHit(long now, ServerPlayerEntity player, String what, float amount) {
        if (!active()) return;
        String name = player.getName().getString();
        taken.merge(name, new Taken(0, 0, 0, 1), (a, b) -> a.add(0, 0, false));
        line(now, name + " ignored " + what + " (" + round(amount) + "): hit immunity");
    }

    public void bossHit(long now, Entity attacker, float lost) {
        if (!active() || lost <= 0) return;
        String name = attacker == null ? "environment" : attacker.getName().getString();
        dealt.merge(name, lost, Float::sum);
    }

    /** Ends the record: tells operators in the boss's world and writes the file. Safe to call twice. */
    public void finish(LivingEntity boss, String outcome) {
        if (!active() || !(boss.getEntityWorld() instanceof ServerWorld world)) return;
        long now = world.getTime();
        line(now, "Fight ends: " + outcome + " (last stage " + stage + " lasted " + seconds(now - stageStarted) + ")");
        List<String> summary = new ArrayList<>();
        summary.add(title + " fight: " + outcome + " after " + seconds(now - started) + ", stage " + stage);
        dealt.forEach((name, amount) -> summary.add("  " + name + " dealt " + round(amount)));
        taken.forEach((name, t) -> summary.add("  " + name + " took " + round(t.afterArmor()) + " (" + round(t.raw()) + " before armor) over "
                + t.hits() + " hits; " + t.lost() + " hits lost to immunity"));
        summary.add("  casts " + casts);
        started = -1;
        String name = file + "-fight-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
        Path path = world.getServer().getRunDirectory().resolve("logs").resolve(name);
        try {
            Files.createDirectories(path.getParent());
            List<String> out = new ArrayList<>(summary);
            out.add("");
            out.addAll(lines);
            Files.write(path, out);
            summary.add("  full log: logs/" + name);
        } catch (IOException e) {
            LOG.warn("Could not write the {} fight log", title, e);
        }
        for (ServerPlayerEntity op : world.getPlayers(p -> world.getServer().getPlayerManager().isOperator(p.getPlayerConfigEntry())))
            summary.forEach(s -> op.sendMessage(Text.literal(s), false));
        summary.forEach(LOG::info);
    }

    private void line(long now, String text) { lines.add("[" + seconds(now - started) + "] " + text); }
    private static String seconds(long ticks) { return String.format("%.1fs", ticks / 20.0); }
    private static String round(float value) { return String.format("%.1f", value); }
}
