package com.anton.elementalwands.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

/** Player-chosen wand HUD position. A custom position is stored as an offset from the
 * bottom-centre of the screen so the row keeps its place beside the hotbar across window
 * sizes and GUI scales. Unset means the default spot: level with the hotbar, left of the
 * offhand slot, clear of the armor bar, item name and action bar. */
public final class WandHudLayout {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("elementalwands-hud.properties");
    private static final int HOTBAR_HALF_WIDTH = 91, OFFHAND_WIDTH = 29, HOTBAR_GAP = 4, MARGIN = 2;
    /** Room kept above the row for the heat / stone / Flashover readouts. */
    private static final int TOP_ROOM = 40;
    private static boolean loaded, custom;
    private static int dx, dy;

    public static boolean custom() { load(); return custom; }

    /** Centre X of the ability row in real screen pixels, clamped on screen. */
    public static int centerX(int width, int rowWidth) {
        load();
        int x = custom ? width / 2 + dx : width / 2 - HOTBAR_HALF_WIDTH - OFFHAND_WIDTH - HOTBAR_GAP - rowWidth / 2;
        return MathHelper.clamp(x, rowWidth / 2 + MARGIN, Math.max(rowWidth / 2 + MARGIN, width - rowWidth / 2 - MARGIN));
    }

    /** Centre Y of the ability row in real screen pixels, clamped on screen. */
    public static int centerY(int height, int rowHeight) {
        load();
        int y = custom ? height + dy : height - 11;
        return MathHelper.clamp(y, rowHeight / 2 + TOP_ROOM, Math.max(rowHeight / 2 + TOP_ROOM, height - rowHeight / 2 - MARGIN));
    }

    public static void move(int width, int height, int centerX, int centerY) {
        load(); custom = true; dx = centerX - width / 2; dy = centerY - height;
    }

    public static void reset() { load(); custom = false; dx = 0; dy = 0; }

    public static boolean save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            var p = new Properties();
            p.setProperty("custom", Boolean.toString(custom));
            p.setProperty("dx", Integer.toString(dx));
            p.setProperty("dy", Integer.toString(dy));
            Path temp = CONFIG.resolveSibling(CONFIG.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) { p.store(writer, "Elemental Wands HUD position (offset from bottom-centre)"); }
            Files.move(temp, CONFIG, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("elementalwands").warn("Could not save wand HUD position", e);
            return false;
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(CONFIG)) return;
            var p = new Properties();
            try (var reader = Files.newBufferedReader(CONFIG)) { p.load(reader); }
            custom = Boolean.parseBoolean(p.getProperty("custom", "false"));
            dx = Integer.parseInt(p.getProperty("dx", "0").trim());
            dy = Integer.parseInt(p.getProperty("dy", "0").trim());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("elementalwands").warn("Could not load wand HUD position; using the default", e);
            custom = false; dx = 0; dy = 0;
        }
    }

    private WandHudLayout() {}
}
