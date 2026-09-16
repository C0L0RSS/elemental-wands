package com.anton.elementalwands.client.screen;

import java.util.List;
import java.util.Locale;
import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.client.WandControls;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** The approved parchment design, with native Minecraft text/widgets and server state. */
public final class WandHubScreen extends Screen {
    public static final int PAPER_WIDTH = 360, PAPER_HEIGHT = 266;
    private static final int INK = 0xFF352C20, MUTED = 0xFF715A39;
    private String selected = "", stamp = "", message = "";
    private enum Page { LOADOUT, STORE, CONTROLS, ELEMENTS, GUIDE }
    private Page page = Page.LOADOUT;
    private int libraryPage, categoryFilter = -1;
    public static WandHubScreen guide() { var screen = new WandHubScreen(); screen.page = Page.GUIDE; return screen; }
    private int binding = -1, ticks;
    private float scale = 1;
    private int originX, originY;

    public WandHubScreen() { super(Text.literal("Wand Hub")); }
    @Override protected void init() {
        WandControls.clear();
        scale = Math.min(1.0f, Math.min((width - 8f) / PAPER_WIDTH, (height - 8f) / PAPER_HEIGHT));
        originX = Math.round((width - PAPER_WIDTH * scale) / 2);
        originY = Math.round((height - PAPER_HEIGHT * scale) / 2);
        stamp = ""; refresh();
    }
    public void feedback(String text) { message = text; stamp = ""; refresh(); }
    private List<WandSpells.Spell> library() {
        return WandSpells.forAffinity(ClientPlayerData.getAffinity()).stream()
                .filter(s -> page == Page.STORE || ClientPlayerData.owns(s))
                .filter(s -> page != Page.STORE || categoryFilter < 0 || s.category().slot() == categoryFilter).toList();
    }
    private void page(Page next) { page = next; categoryFilter = -1; binding = -1; libraryPage = 0; selected = ""; refresh(); }
    public void refresh() {
        if (client == null) return;
        var affinity = ClientPlayerData.getAffinity();
        String current = affinity + ":" + ClientPlayerData.owned() + ":" + ClientPlayerData.flux() + ":"
                + ClientPlayerData.loadout() + ":" + ClientPlayerData.canEdit() + ":" + page + ":" + selected + ":" + binding + ":" + libraryPage + ":" + categoryFilter;
        if (current.equals(stamp)) return;
        var chosen = WandSpells.find(selected);
        if (chosen == null || chosen.affinity() != affinity || (page == Page.LOADOUT && !ClientPlayerData.owns(chosen)))
            selected = WandSpells.forAffinity(affinity).getFirst().id();
        stamp = current;
        clearChildren();
        button("Done", 284, 244, 58, 16, this::close);
        if (page == Page.GUIDE) {
            button("Open hub", 225, 219, 117, 20, () -> page(Page.LOADOUT));
            return;
        }
        button("Guide", 174, 43, 56, 16, () -> page(Page.GUIDE));
        if (affinity == WizardAffinity.NONE || page == Page.ELEMENTS) {
            int y = 91;
            for (var choice : WizardAffinity.values()) if (choice != WizardAffinity.NONE) {
                var captured = choice;
                var b = button(title(choice) + (choice == affinity ? " (selected)" : ""), 22, y, 146, 24,
                        () -> { action("choose", 0, captured.name()); page(Page.LOADOUT); });
                b.active = choice != affinity && ClientPlayerData.canEdit();
                y += 28;
            }
            if (affinity != WizardAffinity.NONE) button("Back", 194, 209, 148, 20, () -> page(Page.LOADOUT));
            return;
        }
        button("Change element", 235, 43, 107, 16, () -> page(Page.ELEMENTS)).active = ClientPlayerData.canEdit();
        button("Loadout", 18, 65, 100, 18, () -> page(Page.LOADOUT)).active = page != Page.LOADOUT;
        button("Spell Store", 122, 65, 116, 18, () -> page(Page.STORE)).active = page != Page.STORE;
        button("Controls", 242, 65, 100, 18, () -> page(Page.CONTROLS)).active = page != Page.CONTROLS;
        if (page == Page.CONTROLS) {
            for (int i = 0; i < 4; i++) {
                int slot = i;
                button(binding == i ? "Press a key..." : WandControls.label(i).getString(), 22, 105 + i*31, 146, 18,
                        () -> { binding = slot; refresh(); });
            }
            button("Reset controls", 22, 221, 146, 20, () -> feedback(WandControls.reset()));
            button("Minecraft controls...", 194, 211, 148, 20, () -> client.setScreen(new ControlsOptionsScreen(this, client.options)));
            return;
        }
        if (page == Page.STORE) button("Category: " + (categoryFilter < 0 ? "All" : WandSpells.Category.forSlot(categoryFilter).label()), 18, 86, 156, 13,
                () -> { categoryFilter = categoryFilter == 2 ? -1 : categoryFilter+1; libraryPage = 0;
                    selected = library().isEmpty() ? "" : library().getFirst().id(); refresh(); });
        if (page == Page.LOADOUT) for (int slot = 0; slot < 3; slot++) {
            String id = ClientPlayerData.loadout().get(slot);
            var spell = WandSpells.find(id);
            var b = button("", 24 + slot * 52, 102, 36, 36, () -> {
                if (ClientPlayerData.owns(spell)) { selected = id; refresh(); }
                else { page(Page.STORE); selected = id; refresh(); }
            });
            b.setTooltip(Tooltip.of(Text.literal((ClientPlayerData.owns(spell) ? "" : "Visit the store: ") + spell.name())));
        }
        var spells = library();
        libraryPage = Math.clamp(libraryPage, 0, Math.max(0, (spells.size()-1)/3));
        for (int i = libraryPage*3; i < Math.min(spells.size(), libraryPage*3+3); i++) {
            var spell = spells.get(i);
            int y = (page == Page.STORE ? 100 : 164) + (i%3)*(page == Page.STORE ? 36 : 24);
            button("    " + spell.name(), 18, y, 156, 22, () -> { selected = spell.id(); refresh(); });
        }
        if (spells.size() > 3) {
            button("<", 18, 237, 20, 12, () -> { libraryPage--; refresh(); }).active = libraryPage > 0;
            button(">", 154, 237, 20, 12, () -> { libraryPage++; refresh(); }).active = (libraryPage+1)*3 < spells.size();
        }
        var spell = WandSpells.find(selected);
        boolean owned = ClientPlayerData.owns(spell);
        if (page == Page.STORE) {
            var b = button(owned ? "Owned" : "Buy Spell", 194, 202, 148, 20, () -> action("unlock", 0, spell.id()));
            b.active = !owned && ClientPlayerData.canEdit() && ClientPlayerData.flux() >= spell.price();
            if (owned) {
                boolean equipped = ClientPlayerData.loadout().get(spell.category().slot()).equals(spell.id());
                var equipButton = button(equipped ? "Equipped" : "Equip now", 194, 225, 148, 16,
                        () -> action("equip", spell.category().slot(), spell.id()));
                equipButton.setTooltip(Tooltip.of(Text.literal(
                        "Replaces " + WandSpells.find(ClientPlayerData.loadout().get(spell.category().slot())).name())));
                equipButton.active = !equipped && ClientPlayerData.canEdit();
            }
        } else {
            int slot = spell.category().slot();
            boolean equipped = ClientPlayerData.loadout().get(slot).equals(spell.id());
            button(equipped ? spell.category().label() + " equipped" : "Equip " + spell.category().label(), 194, 202, 148, 20,
                    () -> action("equip", slot, spell.id())).active = !equipped && ClientPlayerData.canEdit();
        }
    }

    private ButtonWidget button(String text, int x, int y, int w, int h, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> action.run()).dimensions(x, y, w, h).build());
    }
    private void action(String action, int slot, String value) {
        if (client.player == null) return;
        ClientPlayNetworking.send(new ModNetworking.HubActionPayload(action, ClientPlayerData.getAffinity().name(), slot, value));
    }
    @Override public void tick() {
        if (client.player == null) return;
        if (!client.player.isAlive() || client.player.isSpectator()) { close(); return; }
        if (++ticks % 20 == 0) action("refresh", 0, "");
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen.renderWithTooltip already draws/blurred the background in 1.21.10.
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(originX, originY);
        context.getMatrices().scale(scale, scale);
        var affinity = ClientPlayerData.getAffinity();
        texture(context, "parchment" + (affinity == WizardAffinity.NONE ? "" : "_" + affinity.name().toLowerCase(Locale.ROOT)), 0, 0, 360, 266, 512);
        if (affinity != WizardAffinity.NONE) texture(context, "corner_" + affinity.name().toLowerCase(Locale.ROOT), 0, 0, 64, 64, 64);
        context.drawText(textRenderer, "Wizard's Wand", 43, 22, INK, false);
        context.drawText(textRenderer, affinity == WizardAffinity.NONE ? "Choose an affinity" : title(affinity) + " affinity", 43, 36, MUTED, false);
        String balance = affinity == WizardAffinity.NONE ? "Choose your element" : title(affinity) + " Flux: " + String.format(Locale.ROOT, "%,d", ClientPlayerData.flux());
        context.drawText(textRenderer, textRenderer.trimToWidth(balance, 148), 22, 51, MUTED, false);
        if (page == Page.GUIDE) {
            context.drawText(textRenderer, "Getting started", 22, 72, INK, false);
            wrapped(context, guideText(), 22, 90, 316, INK);
        } else if (affinity == WizardAffinity.NONE || page == Page.ELEMENTS) {
            wrapped(context, "Choose an element", 194, 91, 145, INK);
            wrapped(context, "Every element has its own Flux and spell collection. Your purchases and equipped spells are saved when you switch.", 194, 117, 145, INK);
            wrapped(context, "New elements start with a free basic spell.", 194, 177, 145, MUTED);
        } else if (page == Page.CONTROLS) {
            for (int i = 0; i < 4; i++) context.drawText(textRenderer, i==3 ? "Spell alternate" : WandSpells.Category.forSlot(i).label(), 22, 94+i*31, MUTED, false);
            wrapped(context, binding >= 0 ? "Press a keyboard key or click a mouse button. Escape cancels." : "Bind spells and their alternate action. Flashover uses alternate to detonate.", 194, 94, 145, INK);
            wrapped(context, "Hold Basic to repeat. Sneak + right-click interacts with blocks. Put the wand away for normal tools.", 194, 150, 145, MUTED);
        } else {
            if (page == Page.LOADOUT) for (var category : WandSpells.Category.values()) {
                context.getMatrices().pushMatrix();context.getMatrices().translate(42+category.slot()*52,90);context.getMatrices().scale(.8f,.8f);
                context.drawText(textRenderer,category.label(),-textRenderer.getWidth(category.label())/2,0,MUTED,false);context.getMatrices().popMatrix();
            }
            if (page == Page.LOADOUT) context.drawText(textRenderer, "Owned spells", 22, 153, MUTED, false);
            var spell = WandSpells.find(selected);
            icon(context, spell, 302, 12, 28);
            wrapped(context, spell.name(), 194, 94, 148, INK);
            context.drawText(textRenderer, spell.ultimate() ? "Ultimate / 100 charge" : spell.category().label() + " spell", 194, 110, MUTED, false);
            wrapped(context, spell.description(), 194, 124, 148, INK);
            context.drawText(textRenderer, spell.timing(), 194, 168, MUTED, false);
            context.drawText(textRenderer, spell.reach(), 194, 179, MUTED, false);
            if (page == Page.STORE) {
                String cost = ClientPlayerData.owns(spell) ? (spell.price() == 0 ? "Free starter spell" : "Permanently owned") : "Price: " + spell.price() + " Flux";
                context.drawText(textRenderer, cost, 194, 190, MUTED, false);
                if (!ClientPlayerData.owns(spell) && ClientPlayerData.flux() < spell.price())
                    context.drawText(textRenderer, "Need " + (spell.price()-ClientPlayerData.flux()) + " more Flux", 194, 226, 0xFF853E27, false);
                wrapped(context, "Earn " + title(affinity) + " Flux with " + title(affinity) + " spell damage.", 22, 211, 148, MUTED);
            }
            if (page == Page.LOADOUT && ClientPlayerData.owns(spell) && !ClientPlayerData.loadout().get(spell.category().slot()).equals(spell.id())) {
                var replaced = WandSpells.find(ClientPlayerData.loadout().get(spell.category().slot()));
                String label = "Replaces " + (ClientPlayerData.owns(replaced) ? replaced.name() : "empty slot");
                context.drawText(textRenderer, textRenderer.trimToWidth(label,148), 194, 225, MUTED, false);
            }
            if (!ClientPlayerData.canEdit()) context.drawText(textRenderer, "Locked during combat", 194, 235, 0xFF853E27, false);
        }
        super.render(context, localX(mouseX), localY(mouseY), delta);
        if ((page == Page.LOADOUT || page == Page.STORE) && affinity != WizardAffinity.NONE) {
            if (page == Page.LOADOUT) for (int i = 0; i < 3; i++) {
                var spell = WandSpells.find(ClientPlayerData.loadout().get(i));
                if (ClientPlayerData.owns(spell)) icon(context, spell, 28+i*52, 106, 28);
                else context.drawText(textRenderer, "+", 39+i*52, 116, MUTED, false);
                String key = WandControls.shortLabel(i);
                context.drawText(textRenderer, key, 42+i*52-textRenderer.getWidth(key)/2, 141, MUTED, false);
            }
            var spells = library();
            for (int i = libraryPage*3; i < Math.min(spells.size(), libraryPage*3+3); i++) {
                var spell = spells.get(i);
                int y = (page == Page.STORE ? 100 : 164) + (i%3)*(page == Page.STORE ? 36 : 24);
                icon(context, spell, 22, y+3, 16);
                if (spell.id().equals(selected)) context.fill(18, y, 20, y+22, 0xFFF4D176);
                if (page == Page.STORE) context.drawText(textRenderer, ClientPlayerData.owns(spell) ? "Owned" : spell.price() + " Flux", 24, y+24, MUTED, false);
            }
        }
        if (page != Page.GUIDE && !message.isBlank()) {
            context.fill(20,248,278,261,0xBFE9D49D);
            context.drawText(textRenderer, textRenderer.trimToWidth(message,254),22,250,INK,false);
        }
        context.getMatrices().popMatrix();
    }
    private String guideText() {
        return "Press " + WandControls.hubLabel() + " to open your hub and choose an element.\n\n"
                + "Basic: " + WandControls.label(0).getString() + " | Technique: " + WandControls.label(1).getString()
                + " | Ultimate: " + WandControls.label(2).getString() + "\nChange your keys in Controls.\n\n"
                + "Deal spell damage to earn that element's Flux.\nBuy spells in the Store; equip one of each category.\n"
                + "Your purchases stay saved when switching elements.\n\n"
                + "Explore the world and discover what awaits.";
    }
    private static String title(WizardAffinity affinity) {
        String s = affinity.name().toLowerCase(Locale.ROOT); return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private void wrapped(DrawContext ctx, String text, int x, int y, int width, int color) {
        for (var line : textRenderer.wrapLines(Text.literal(text), width)) { ctx.drawText(textRenderer, line, x, y, color, false); y += 11; }
    }
    private static void texture(DrawContext ctx, String name, int x, int y, int w, int h, int size) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("elementalwands", "textures/gui/hub/" + name + ".png"), x, y, 0, 0, w, h, size, size);
    }
    private static void icon(DrawContext ctx, WandSpells.Spell spell, int x, int y, int size) {
        com.anton.elementalwands.client.SpellIcons.draw(ctx,spell,x,y,size);
    }
    private int localX(double x) { return (int)((x-originX)/scale); }
    private int localY(double y) { return (int)((y-originY)/scale); }
    @Override public boolean mouseClicked(Click click, boolean doubleClick) {
        if (binding >= 0) {
            int slot = binding; binding = -1;
            feedback(WandControls.bind(slot, InputUtil.Type.MOUSE.createFromCode(click.button()))); return true;
        }
        return super.mouseClicked(new Click(localX(click.x()), localY(click.y()), click.buttonInfo()), doubleClick);
    }
    @Override public boolean mouseReleased(Click click) {
        return super.mouseReleased(new Click(localX(click.x()), localY(click.y()), click.buttonInfo()));
    }
    @Override public boolean keyPressed(KeyInput key) {
        if (binding >= 0) {
            int slot = binding; binding = -1;
            if (key.key() == GLFW.GLFW_KEY_ESCAPE) { refresh(); return true; }
            feedback(WandControls.bind(slot, InputUtil.fromKeyCode(key))); return true;
        }
        return super.keyPressed(key);
    }
    @Override public void removed() { WandControls.clear(); net.minecraft.client.option.KeyBinding.unpressAll(); }
    @Override public boolean shouldPause() { return false; }
}
