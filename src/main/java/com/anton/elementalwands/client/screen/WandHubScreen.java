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
    /** Controls page order: slots 1-5, then the spell alternate (input index 3). */
    private static final int[] CONTROL_ORDER = {0, 1, 2, 4, 5, 3};
    private String selected = "", stamp = "", message = "";
    private enum Page { LOADOUT, STORE, CONTROLS, ELEMENTS, GUIDE }
    private Page page = Page.LOADOUT;
    private int libraryPage, selectedSlot, categoryFilter = -1;
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
                + ClientPlayerData.loadout() + ":" + ClientPlayerData.canEdit() + ":" + page + ":" + selected + ":" + binding + ":" + libraryPage + ":" + categoryFilter + ":" + selectedSlot + ":" + ClientPlayerData.xp() + ":" + ClientPlayerData.credits();
        if (current.equals(stamp)) return;
        var chosen = WandSpells.find(selected);
        if (chosen == null || !library().contains(chosen)) selected=library().isEmpty()?"":library().getFirst().id();
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
            for (int i=0;i<6;i++) {
                int input=CONTROL_ORDER[i]; int x=i<3?22:194,y=105+(i%3)*35;
                button(binding==input?"Press a key...":WandControls.label(input).getString(),x,y,146,18,
                        ()->{binding=input;refresh();});
            }
            button("Reset controls", 22, 221, 146, 20, () -> feedback(WandControls.reset()));
            button("Minecraft controls...", 194, 203, 148, 16, () -> client.setScreen(new ControlsOptionsScreen(this, client.options)));
            button("Move HUD", 194, 222, 148, 16, () -> client.setScreen(new WandHudEditScreen(this)));
            return;
        }
        if (page == Page.STORE) button("Category: " + (categoryFilter < 0 ? "All" : WandSpells.Category.forSlot(categoryFilter).label()), 18, 86, 156, 13,
                () -> { categoryFilter = categoryFilter == 2 ? -1 : categoryFilter+1; libraryPage = 0;
                    selected = library().isEmpty() ? "" : library().getFirst().id(); refresh(); });
        if(page==Page.LOADOUT) {
            var slots=visibleSlots();int size=slotSize();
            for(int index=0;index<slots.size();index++) {
                int slot=slots.get(index);var spell=WandSpells.find(ClientPlayerData.loadout().get(slot));
                var b=button("",slotX(index),102,size,size,()->{
                    selectedSlot=slot;libraryPage=0;selected=ClientPlayerData.loadout().get(slot);stamp="";refresh();
                });
                b.setTooltip(Tooltip.of(Text.literal(WandSpells.slotLabel(slot)+(spell==null?"":": "+spell.name()))));
            }
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
        if(spell==null)return;
        boolean owned = ClientPlayerData.owns(spell);
        if (page == Page.STORE) {
            var b = button(owned ? "Owned" : ClientPlayerData.free(spell) ? "Learn Free" : "Buy Spell", 194, 202, 148, 20, () -> action("unlock", 0, spell.id()));
            b.active = !owned && ClientPlayerData.canEdit() && (ClientPlayerData.free(spell) || ClientPlayerData.flux() >= spell.price());
            if (owned) {
                boolean equipped = ClientPlayerData.loadout().contains(spell.id());
                var equipButton = button(equipped ? "Equipped" : "Equip now", 194, 225, 148, 16,
                        () -> action("equip", destination(spell), spell.id()));
                equipButton.setTooltip(Tooltip.of(Text.literal(
                        "Equip in " + WandSpells.slotLabel(destination(spell)))));
                equipButton.active = !equipped && ClientPlayerData.canEdit();
            }
        } else {
            int slot = selectedSlot;
            boolean equipped = ClientPlayerData.loadout().get(slot).equals(spell.id());
            button(equipped ? WandSpells.slotLabel(slot) + " equipped" : "Equip " + WandSpells.slotLabel(slot), 194, 202, 148, 20,
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
        if(affinity!=WizardAffinity.NONE){
            double xp=ClientPlayerData.xp();int level=ElementLevels.level(xp);
            context.drawText(textRenderer,"Lv "+level+" / 6  +"+((level-1)*10)+"%",194,22,MUTED,false);
            context.drawText(textRenderer,level==6?"Maximum level":"XP "+(int)ElementLevels.within(xp)+" / "+(int)ElementLevels.required(xp),194,34,MUTED,false);
            context.fill(194,40,292,41,0xFFC6B087);
            context.fill(194,40,194+(level==6?98:(int)(98*ElementLevels.within(xp)/ElementLevels.required(xp))),41,0xFF9B722B);
        }
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
            for(int i=0;i<6;i++){int input=CONTROL_ORDER[i];context.drawText(textRenderer,input==3?"Spell alternate":WandSpells.slotLabel(WandControls.slotForInput(input)),i<3?22:194,94+(i%3)*35,MUTED,false);}
            context.drawText(textRenderer,binding>=0?"Press key / mouse; Esc cancels":"Hold a Basic spell's key to repeat.",22,203,MUTED,false);
        } else {
            if(page==Page.LOADOUT)for(int i=0;i<visibleSlots().size();i++){
                int slot=visibleSlots().get(i);
                if(!WandSpells.slotOpen(ClientPlayerData.owned(),slot))continue;
                String label=WandSpells.slotLabel(slot);
                context.getMatrices().pushMatrix();context.getMatrices().translate(slotX(i)+slotSize()/2f,90);context.getMatrices().scale(.75f,.75f);
                context.drawText(textRenderer,label,-textRenderer.getWidth(label)/2,0,MUTED,false);context.getMatrices().popMatrix();
            }
            if (page == Page.LOADOUT) context.drawText(textRenderer, "Owned spells", 22, 153, MUTED, false);
            var spell = WandSpells.find(selected);
            if(spell!=null) {
            icon(context, spell, 302, 12, 28);
            context.drawText(textRenderer,textRenderer.trimToWidth(spell.name(),148),194,94,INK,false);
            context.drawText(textRenderer, spell.ultimate() ? "Ultimate / 100 charge" : spell.category().label() + " spell", 194, 110, MUTED, false);
            wrappedLimited(context, spell.description(), 194, 124, 148, INK, 3);
            context.drawText(textRenderer, textRenderer.trimToWidth(spell.timing(),148), 194, 168, MUTED, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(spell.reach(),148), 194, 179, MUTED, false);
            if (page == Page.STORE) {
                String cost = ClientPlayerData.owns(spell) ? (spell.price() == 0 ? "Free starter spell" : "Permanently owned") : ClientPlayerData.free(spell) ? "Free spell choice" : "Price: " + spell.price() + " Flux";
                context.drawText(textRenderer, cost, 194, 190, MUTED, false);
                if (!ClientPlayerData.owns(spell) && !ClientPlayerData.free(spell) && ClientPlayerData.flux() < spell.price())
                    context.drawText(textRenderer, "Need " + (spell.price()-ClientPlayerData.flux()) + " more Flux", 194, 226, 0xFF853E27, false);
                wrapped(context, "Earn " + title(affinity) + " Flux with " + title(affinity) + " spell damage.", 22, 211, 148, MUTED);
            }
            if (page == Page.LOADOUT && ClientPlayerData.owns(spell) && !ClientPlayerData.loadout().contains(spell.id())) {
                var replaced = WandSpells.find(ClientPlayerData.loadout().get(selectedSlot));
                String label = "Replaces " + (ClientPlayerData.owns(replaced) ? replaced.name() : "empty slot");
                context.drawText(textRenderer, textRenderer.trimToWidth(label,148), 194, 225, MUTED, false);
            }
            } else {
                wrapped(context,"No spells to place in "+WandSpells.slotLabel(selectedSlot)+".",194,94,148,INK);
                wrapped(context,"Learn spells in the Spell Store, then put them in any slot.",194,125,148,MUTED);
            }
            if (!ClientPlayerData.canEdit()) context.drawText(textRenderer, "Locked during combat", 194, 235, 0xFF853E27, false);
        }
        super.render(context, localX(mouseX), localY(mouseY), delta);
        if ((page == Page.LOADOUT || page == Page.STORE) && affinity != WizardAffinity.NONE) {
            if(page==Page.LOADOUT)for(int i=0;i<visibleSlots().size();i++){
                int slot=visibleSlots().get(i),size=slotSize(),x=slotX(i);var spell=WandSpells.find(ClientPlayerData.loadout().get(slot));
                if(ClientPlayerData.owns(spell))icon(context,spell,x+4,106,size-8);
                if(WandSpells.slotOpen(ClientPlayerData.owned(),slot)){
                    String key=WandControls.slotKey(slot);context.drawText(textRenderer,key,x+(size-textRenderer.getWidth(key))/2,141,MUTED,false);
                }
                if(selectedSlot==slot)context.fill(x,102,x+size,104,0xFFF4D176);
            }
            var spells = library();
            for (int i = libraryPage*3; i < Math.min(spells.size(), libraryPage*3+3); i++) {
                var spell = spells.get(i);
                int y = (page == Page.STORE ? 100 : 164) + (i%3)*(page == Page.STORE ? 36 : 24);
                icon(context, spell, 22, y+3, 16);
                if (spell.id().equals(selected)) context.fill(18, y, 20, y+22, 0xFFF4D176);
                if (page == Page.STORE) context.drawText(textRenderer, ClientPlayerData.owns(spell) ? "Owned" : ClientPlayerData.free(spell) ? "Free" : spell.price() + " Flux", 24, y+24, MUTED, false);
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
                + "Slots 1-3: " + WandControls.label(0).getString() + ", " + WandControls.label(1).getString() + ", " + WandControls.label(2).getString()
                + " | Slots 4-5: " + WandControls.label(4).getString() + ", " + WandControls.label(5).getString() + "\nChange your keys in Controls.\n\n"
                + "Deal spell damage to earn element XP and Flux.\nBuy spells in the Store and place them in any slot.\n"
                + "Your purchases stay saved when switching elements.\n\n"
                + "Explore the world and discover what awaits.";
    }
    private List<Integer> visibleSlots(){return WandSpells.visibleSlots(ClientPlayerData.owned());}
    private int slotSize(){return visibleSlots().size()==3?36:visibleSlots().size()==4?32:28;}
    private int slotX(int index){return 24+index*(visibleSlots().size()==3?52:visibleSlots().size()==4?40:32);}
    private int destination(WandSpells.Spell spell){
        for(int i=0;i<5;i++)if(WandSpells.fits(spell,i)&&WandSpells.slotOpen(ClientPlayerData.owned(),i)&&ClientPlayerData.loadout().get(i).isEmpty())return i;
        return WandSpells.fits(spell,selectedSlot)&&WandSpells.slotOpen(ClientPlayerData.owned(),selectedSlot)?selectedSlot:spell.category().slot();
    }
    private void wrappedLimited(DrawContext ctx,String text,int x,int y,int width,int color,int count){
        var lines=textRenderer.wrapLines(Text.literal(text),width);
        for(int i=0;i<Math.min(count,lines.size());i++)ctx.drawText(textRenderer,lines.get(i),x,y+i*11,color,false);
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
