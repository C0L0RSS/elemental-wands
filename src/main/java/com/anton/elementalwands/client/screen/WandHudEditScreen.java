package com.anton.elementalwands.client.screen;

import com.anton.elementalwands.client.WandHudLayout;
import com.anton.elementalwands.client.overlay.WandHudOverlay;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Drag the live wand HUD to a new spot. The overlay keeps drawing underneath this
 * screen (no blur, no dim) so the player sees exactly where the row will sit. */
public final class WandHudEditScreen extends Screen {
    private static final int HINT = 0xFFE8D6AF, MUTED = 0xFFB3A78E, OUTLINE = 0xB0E8D6AF, OUTLINE_ACTIVE = 0xFFF4D176;
    private final Screen parent;
    private boolean dragging;
    private double grabX, grabY;

    public WandHudEditScreen(Screen parent) { super(Text.literal("Wand HUD position")); this.parent = parent; }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset position"), b -> WandHudLayout.reset())
                .dimensions(width / 2 - 102, 40, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 2, 40, 100, 20).build());
    }

    /** No blur or dim: the HUD below must stay readable while it is moved. */
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, "Drag the wand HUD to where you want it", width / 2, 14, HINT);
        context.drawCenteredTextWithShadow(textRenderer, "Arrow keys nudge (Shift for 10). Esc or Done saves.", width / 2, 26, MUTED);
        if (WandHudOverlay.lastWidth <= 0) {
            context.drawCenteredTextWithShadow(textRenderer, "No spells to show yet. Equip a spell in the Loadout page first.", width / 2, height / 2, MUTED);
            return;
        }
        int x = WandHudOverlay.lastX - 3, y = WandHudOverlay.lastY - 3;
        int w = WandHudOverlay.lastWidth + 6, h = WandHudOverlay.lastHeight + 6;
        int color = dragging || inside(mouseX, mouseY) ? OUTLINE_ACTIVE : OUTLINE;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean inside(double mx, double my) {
        return WandHudOverlay.lastWidth > 0
                && mx >= WandHudOverlay.lastX - 3 && mx < WandHudOverlay.lastX + WandHudOverlay.lastWidth + 3
                && my >= WandHudOverlay.lastY - 3 && my < WandHudOverlay.lastY + WandHudOverlay.lastHeight + 3;
    }

    @Override public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - (WandHudOverlay.lastX + WandHudOverlay.lastWidth / 2.0);
            grabY = click.y() - (WandHudOverlay.lastY + WandHudOverlay.lastHeight / 2.0);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!dragging) return super.mouseDragged(click, offsetX, offsetY);
        WandHudLayout.move(width, height, (int) Math.round(click.x() - grabX), (int) Math.round(click.y() - grabY));
        return true;
    }

    @Override public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override public boolean keyPressed(KeyInput key) {
        int step = (key.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        int nx = 0, ny = 0;
        switch (key.key()) {
            case GLFW.GLFW_KEY_LEFT -> nx = -step;
            case GLFW.GLFW_KEY_RIGHT -> nx = step;
            case GLFW.GLFW_KEY_UP -> ny = -step;
            case GLFW.GLFW_KEY_DOWN -> ny = step;
            default -> { return super.keyPressed(key); }
        }
        if (WandHudOverlay.lastWidth > 0) WandHudLayout.move(width, height,
                WandHudOverlay.lastX + WandHudOverlay.lastWidth / 2 + nx, WandHudOverlay.lastY + WandHudOverlay.lastHeight / 2 + ny);
        return true;
    }

    @Override public void close() {
        WandHudLayout.save();
        if (client != null) client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }
}
