package com.anton.elementalwands.client;

import java.nio.file.*;
import java.util.Properties;
import com.anton.elementalwands.client.screen.WandHubScreen;
import com.anton.elementalwands.data.WandSpells;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Slot bindings are separate from vanilla's single-key lookup, so mouse bindings
 * do not steal Attack/Use from tools when the wand is put away. */
public final class WandControls {
    private static final InputUtil.Key[] KEYS = defaults();
    private static final boolean[] HELD = new boolean[6];
    private static int heldHotbar = -1, repeatTick;
    private static String heldAffinity = "";
    private static net.minecraft.item.ItemStack leapWand;
    private static boolean leapAim;
    private static KeyBinding hubKey;
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("elementalwands-controls.properties");
    private static InputUtil.Key[] defaults() { return new InputUtil.Key[]{InputUtil.Type.MOUSE.createFromCode(0),
            InputUtil.Type.MOUSE.createFromCode(1), InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_X),InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_R),InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_Z),InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_V)}; }
    public static void init() {
        hubKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.elementalwands.hub", GLFW.GLFW_KEY_H,
                new KeyBinding.Category(Identifier.of("elementalwands", "general"))));
        try {
            if (Files.exists(CONFIG)) {
                var properties = new Properties();
                try (var reader = Files.newBufferedReader(CONFIG)) { properties.load(reader); }
                for (int i = 0; i < KEYS.length; i++) {
                    var key = InputUtil.fromTranslationKey(properties.getProperty("slot." + i, KEYS[i].getTranslationKey()));
                    boolean duplicate=false;
                    for(int j=0;j<i;j++)if(KEYS[j].equals(key))duplicate=true;
                    if(valid(key) && !duplicate)KEYS[i]=key;
                    else {
                        for(int code:new int[]{GLFW.GLFW_KEY_R,GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_G,GLFW.GLFW_KEY_B,GLFW.GLFW_KEY_N}) {
                            var fallback=InputUtil.Type.KEYSYM.createFromCode(code);
                            boolean used=false;for(int j=0;j<i;j++)if(fallback.equals(KEYS[j]))used=true;
                            if(!used) { KEYS[i]=fallback;break; }
                        }
                    }
                }
            } else {
                // Carry forward an existing rebind of the old registered ultimate.
                var options = MinecraftClient.getInstance().runDirectory.toPath().resolve("options.txt");
                if (Files.exists(options)) for (String line : Files.readAllLines(options)) {
                    if (line.startsWith("key_key.elementalwands.ultimate:")) {
                        var key = InputUtil.fromTranslationKey(line.substring(line.indexOf(':') + 1));
                        if (valid(key) && !key.equals(KEYS[0]) && !key.equals(KEYS[1])) KEYS[2] = key;
                    }
                }
                save();
            }
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger("elementalwands").warn("Could not load wand controls; using available defaults", e); }
    }
    private static boolean valid(InputUtil.Key key) {
        return key.getCode() >= 0 && !(key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() == GLFW.GLFW_KEY_ESCAPE);
    }
    public static String hubLabel() { return hubKey.getBoundKeyLocalizedText().getString(); }
    public static int inputForSlot(int slot){return slot<3?slot:slot+1;}
    public static int slotForInput(int input){return input<3?input:input-1;}
    public static Text label(int slot) { return KEYS[slot].getLocalizedText(); }
    public static String slotKey(int slot){return shortLabel(inputForSlot(slot));}
    public static String shortLabel(int slot) {
        var key = KEYS[slot];
        if (key.getCategory() == InputUtil.Type.MOUSE) return switch (key.getCode()) {
            case 0 -> "LMB"; case 1 -> "RMB"; case 2 -> "MMB"; default -> "M" + (key.getCode()+1);
        };
        return MinecraftClient.getInstance().textRenderer.trimToWidth(key.getLocalizedText().getString(), 38);
    }
    public static String bind(int slot, InputUtil.Key key) {
        if (!valid(key)) return "That key cannot be bound.";
        if (key.getTranslationKey().equals(hubKey.getBoundKeyTranslationKey())) return "That key opens the wand hub. Choose another.";
        for (int i = 0; i < KEYS.length; i++) if (i != slot && KEYS[i].equals(key)) { KEYS[i] = KEYS[slot]; break; }
        KEYS[slot] = key;
        clear();
        return save() ? "Control saved." : "Control changed, but could not save the settings file.";
    }
    public static String reset() { System.arraycopy(defaults(), 0, KEYS, 0, KEYS.length); clear(); return save() ? "Default controls restored." : "Could not save controls."; }
    private static boolean save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            var p = new Properties();
            for (int i = 0; i < KEYS.length; i++) p.setProperty("slot." + i, KEYS[i].getTranslationKey());
            Path temp = CONFIG.resolveSibling(CONFIG.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) { p.store(writer, "Elemental Wands slot bindings"); }
            Files.move(temp, CONFIG, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger("elementalwands").warn("Could not save wand controls", e); return false; }
    }
    private static void release() {
        var client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null && ClientPlayNetworking.canSend(ModNetworking.ReleaseSpellPayload.ID))
            ClientPlayNetworking.send(ModNetworking.ReleaseSpellPayload.INSTANCE);
    }
    public static void clear() { leapAim=false;leapWand=null; if (java.util.stream.IntStream.range(0,HELD.length).anyMatch(i->HELD[i])) release(); java.util.Arrays.fill(HELD, false); heldHotbar = -1; repeatTick = 0; }
    public static boolean aimingLeap() { return leapAim && validLeapContext(); }
    private static boolean validLeapContext() {
        var c=MinecraftClient.getInstance();
        return c.player!=null && c.world!=null && c.currentScreen==null && c.isWindowFocused() && c.player.isAlive()
                && !c.player.isSpectator() && !c.player.hasVehicle() && !c.player.isGliding() && !c.player.isTouchingWater()
                && c.player.getMainHandStack()==leapWand && heldHotbar==c.player.getInventory().getSelectedSlot()
                && heldAffinity.equals(ClientPlayerData.getAffinity().name()) && ClientPlayerData.loadout().contains("fire_hop");
    }
    private static void releaseLeap() {
        var c=MinecraftClient.getInstance();
        if(aimingLeap()) {
            var target=com.anton.elementalwands.util.FireLeapRules.target(c.player);
            if(target!=null && com.anton.elementalwands.util.FireLeapRules.validTarget(c.player,target))
                ClientPlayNetworking.send(new ModNetworking.FireLeapCommitPayload(target.x,target.y,target.z));
            else c.player.sendMessage(Text.literal("No clear leap path. Aim toward open ground or a reachable ledge."),true);
        }
        leapAim=false;leapWand=null;
    }
    public static boolean input(boolean mouse, int code, int action) {
        var client = MinecraftClient.getInstance();
        var key = (mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM).createFromCode(code);
        if (action == GLFW.GLFW_RELEASE) {
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) { if (HELD[i] && (spellAtInput(i,"flamethrower") || spellAtInput(i,"stone_charge"))) release(); if(HELD[i] && spellAtInput(i,"fire_hop") && leapAim) releaseLeap(); HELD[i] = false; }
            return false;
        }
        if (client.currentScreen != null || client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !client.isWindowFocused() || !(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)) return false;
        if (key.getTranslationKey().equals(hubKey.getBoundKeyTranslationKey())) return false;
        // A deliberate vanilla interaction gesture; never casts as well as interacting.
        if (mouse && code == GLFW.GLFW_MOUSE_BUTTON_RIGHT && (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT))) return false;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) {
            if (action == GLFW.GLFW_PRESS) {
                HELD[i] = true; heldHotbar = client.player.getInventory().getSelectedSlot();
                heldAffinity = ClientPlayerData.getAffinity().name(); repeatTick = 0;
                if(i==3) {
                    if(ClientPlayerData.loadout().contains("flashover"))ClientPlayNetworking.send(ModNetworking.AlternateSpellPayload.INSTANCE);
                } else if(spellAtInput(i,"fire_hop")) {
                    leapWand=client.player.getMainHandStack();leapAim=true;
                } else ClientPlayNetworking.send(new ModNetworking.CastSlotPayload(slotForInput(i)));
            }
            return true;
        }
        return false;
    }
    public static void tick(MinecraftClient client) {
        if(leapAim && !validLeapContext()) { leapAim=false;leapWand=null; }
        boolean open = false;
        while (hubKey.wasPressed()) open = true;
        if (open && client.currentScreen == null && client.player != null && client.player.isAlive() && !client.player.isSpectator()) {
            open(); return;
        }
        if (client.player == null || client.currentScreen != null || !client.isWindowFocused() || !client.player.isAlive()
                || client.player.isSpectator() || !(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)
                || heldHotbar != client.player.getInventory().getSelectedSlot() || !heldAffinity.equals(ClientPlayerData.getAffinity().name())) { clear(); return; }
        if (++repeatTick % 2 != 0) return;
        for (int i = 0; i < KEYS.length; i++) {
            if(i==3 || slotForInput(i)>=ClientPlayerData.loadout().size())continue;
            var spell = WandSpells.find(ClientPlayerData.loadout().get(slotForInput(i)));
            if (HELD[i] && spell != null && spell.id().equals("stone_charge"))
                ClientPlayNetworking.send(ModNetworking.StoneChargeHoldPayload.INSTANCE);
            if (HELD[i] && spell != null && spell.ability() == AbstractWandItem.Ability.PRIMARY && (spell.id().equals("flamethrower") || spellReady(client, spell)))
                ClientPlayNetworking.send(new ModNetworking.CastSlotPayload(slotForInput(i)));
        }
    }
    private static boolean spellAtInput(int input,String id){int slot=slotForInput(input);return input!=3 && slot>=0 && slot<ClientPlayerData.loadout().size() && ClientPlayerData.loadout().get(slot).equals(id);}
    /** Client-side repeat gate mirroring the server's per-spell cooldown check. */
    private static boolean spellReady(MinecraftClient client, WandSpells.Spell spell) {
        long now = client.world.getTime();
        var data = client.player.getMainHandStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
        if (now - data.getLong("ew_last_global").orElse(-1000000000L) < 6) return false;
        if (spell.id().equals("gathered_mass")) return ClientPlayerData.stoneRemaining(now) <= 0;
        int duration = data.getInt(AbstractWandItem.durationKey(spell.id()), 0);
        long elapsed = now - data.getLong(AbstractWandItem.cooldownKey(spell.id())).orElse(-1000000000L);
        boolean entangled = ClientPlayerData.getEntangleStacks(client.player.getId()) > 0;
        if (entangled) elapsed /= 2;
        if (spell.category() == WandSpells.Category.BASIC && AbstractWandItem.basicSharedRemaining(data, now, entangled) > 0) return false;
        return elapsed >= duration;
    }
    public static void open() {
        clear();
        MinecraftClient.getInstance().setScreen(new WandHubScreen());
        ClientPlayNetworking.send(new ModNetworking.HubActionPayload("refresh", "", 0, ""));
    }
    private WandControls() {}
}
