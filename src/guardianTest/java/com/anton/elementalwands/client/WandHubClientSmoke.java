package com.anton.elementalwands.client;

import java.nio.file.*;
import com.anton.elementalwands.client.screen.WandHubScreen;
import com.anton.elementalwands.data.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

/** Opens the actual native screen with fixture data; no world or user save opened. */
public final class WandHubClientSmoke implements ClientModInitializer {
    private int ticks, index;
    private final WizardAffinity[] affinities = {WizardAffinity.FIRE, WizardAffinity.STONE, WizardAffinity.NATURE, WizardAffinity.SPACE, WizardAffinity.WIND, WizardAffinity.NONE};
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            try {
                if (++ticks == 1) {
                    for (String name : new String[]{"parchment", "parchment_fire", "parchment_stone", "parchment_nature", "parchment_space", "parchment_wind", "corner_fire", "corner_stone", "corner_nature", "corner_space", "corner_wind"}) {
                        var id = Identifier.of("elementalwands", "textures/gui/hub/"+name+".png");
                        try (var input = client.getResourceManager().getResourceOrThrow(id).getInputStream(); var image = NativeImage.read(input)) {
                            require(image.getWidth() == (name.startsWith("corner") ? 64 : 512), "Wrong texture dimensions");
                        }
                        client.getTextureManager().getTexture(id);
                    }
                    show(client, affinities[0]);
                }
                if (ticks % 25 == 0 && index < affinities.length) {
                    ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-"+affinities[index].name().toLowerCase()+".png", client.getFramebuffer(), 1, text -> {});
                    if (++index < affinities.length) show(client, affinities[index]);
                }
                if (ticks == 155) {
                    show(client, WizardAffinity.FIRE);
                    ClientPlayerData.setUnlockedSkills(0, "FIRE");
                    ClientPlayerData.setOwned(java.util.List.of("inferno_wave"));
                    ((WandHubScreen)client.currentScreen).refresh();
                    click(client, 175, 73);
                    click(client, 85, 181);
                }
                if (ticks == 165) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-locked.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 170) { show(client, WizardAffinity.FIRE); click(client, 275, 73); }
                if (ticks == 180) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-controls.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 185) { show(client, WizardAffinity.NATURE); click(client, 280, 50); }
                if (ticks == 190) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-elements.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 195) { show(client, WizardAffinity.SPACE); click(client, 175, 73); }
                if (ticks == 205) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-store-owned.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 210) { show(client, WizardAffinity.FIRE); click(client, 175, 73); click(client, 85, 92); }
                if (ticks == 220) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-category-basic.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 225) click(client, 85, 92);
                if (ticks == 235) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-category-technique.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 240) { show(client, WizardAffinity.NONE); client.setScreen(WandHubScreen.guide()); }
                if (ticks == 250) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-guide-simple.png", client.getFramebuffer(), 1, text -> {});
                if (ticks == 260) { click(client, 275, 229); }
                if (ticks == 270) {
                    show(client, WizardAffinity.FIRE);
                    ClientPlayerData.setHubData(150, WandSpells.defaults(WizardAffinity.FIRE), true);
                    ((WandHubScreen)client.currentScreen).refresh();
                    click(client,175,73);click(client,163,242);click(client,85,110);
                }
                if (ticks == 280) ScreenshotRecorder.saveScreenshot(client.runDirectory, "hub-flamethrower-price.png", client.getFramebuffer(),1,text -> {});
                if (ticks == 285) {
                    ClientPlayerData.setOwned(WandSpells.forAffinity(WizardAffinity.FIRE).stream().map(WandSpells.Spell::id).toList());
                    ((WandHubScreen)client.currentScreen).refresh();
                }
                if (ticks == 295) ScreenshotRecorder.saveScreenshot(client.runDirectory,"hub-flamethrower-owned.png",client.getFramebuffer(),1,text -> {});
                if (ticks == 300) click(client,85,145);
                if (ticks == 310) ScreenshotRecorder.saveScreenshot(client.runDirectory,"hub-fire-hop.png",client.getFramebuffer(),1,text -> {});
                if (ticks == 315) {
                    ClientPlayerData.setHubData(150,java.util.List.of("flamethrower","fire_hop","meteor"),true);
                    client.setScreen(new WandHubScreen());
                }
                if (ticks == 325) ScreenshotRecorder.saveScreenshot(client.runDirectory,"hub-fire-build.png",client.getFramebuffer(),1,text -> {});
                if (ticks == 330) client.setScreen(new net.minecraft.client.gui.screen.Screen(net.minecraft.text.Text.literal("Heat gauge component check")) {
                    @Override public void render(net.minecraft.client.gui.DrawContext ctx,int mx,int my,float delta) {
                        super.render(ctx,mx,my,delta);
                        ctx.getMatrices().pushMatrix();ctx.getMatrices().scale(2,2);
                        com.anton.elementalwands.client.overlay.WandHudOverlay.drawFireHeat(ctx,textRenderer,width/4,height/4-25,65,false);
                        com.anton.elementalwands.client.overlay.WandHudOverlay.drawFireHeat(ctx,textRenderer,width/4,height/4+15,100,true);
                        ctx.getMatrices().popMatrix();
                    }
                });
                if (ticks == 340) ScreenshotRecorder.saveScreenshot(client.runDirectory,"hub-fire-heat.png",client.getFramebuffer(),1,text -> {});
                if (ticks == 350) {
                    show(client, WizardAffinity.NONE);client.setScreen(WandHubScreen.guide());click(client,275,229);
                    require(client.currentScreen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.widget.ButtonWidget)
                            .map(c -> ((net.minecraft.client.gui.widget.ButtonWidget)c).getMessage().getString())
                            .anyMatch(t -> t.equals("Fire")), "Guide did not open the initial element chooser");
                    require(WandWelcome.canShow(true, true, true, false, false, false, true), "Ready welcome was blocked");
                    require(!WandWelcome.canShow(true, true, true, false, true, false, true), "Welcome overwrote an open screen");
                    require(!WandWelcome.canShow(true, true, true, false, false, true, true), "Welcome opened while loading");
                    require(!WandWelcome.canShow(true, true, true, false, false, false, false), "Welcome opened during combat");
                    require(!WandWelcome.canShow(false, true, true, false, false, false, true), "Welcome reopened without a request");
                    show(client, WizardAffinity.FIRE);
                    var screen = (WandHubScreen)client.currentScreen;
                    screen.init(client, 320, 240);
                    float scale = Math.min(1f, Math.min(312f/360, 232f/266));
                    int ox = Math.round((320-360*scale)/2), oy = Math.round((240-266*scale)/2);
                    // Native button dispatch through the scaled small-screen hit boxes.
                    screen.mouseClicked(new Click(ox+275*scale, oy+73*scale, new MouseInput(0,0)), false);
                    screen.mouseClicked(new Click(ox+65*scale, oy+120*scale, new MouseInput(0,0)), false);
                    screen.keyPressed(new KeyInput(82,0,0));
                    require(WandControls.shortLabel(0).equalsIgnoreCase("R"), "Native rebind controls did not accept keyboard input");
                    require(Files.readString(Path.of("config/elementalwands-controls.properties")).contains("slot.0=key.keyboard.r"), "Rebind was not persisted");
                    require(!WandControls.input(true,0,1), "Menu click leaked into a spell cast");
                    WandControls.reset();
                    Files.writeString(Path.of("HUB_PASSED.txt"), "Native screen rendered all affinities, production textures resolved, small-screen button hitboxes and keyboard rebinding/persistence passed. Screenshots saved.\n");
                    client.scheduleStop();
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("HUB_FAILED.txt"), failure.toString()); } catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
    private static void show(net.minecraft.client.MinecraftClient client, WizardAffinity affinity) {
        ClientPlayerData.setUnlockedSkills(3, affinity.name());
        ClientPlayerData.setOwned(WandSpells.defaults(affinity));
        ClientPlayerData.setHubData(1850, WandSpells.defaults(affinity), true);
        client.setScreen(new WandHubScreen());
    }
    private static void click(net.minecraft.client.MinecraftClient client, int x, int y) {
        int w = client.getWindow().getScaledWidth(), h = client.getWindow().getScaledHeight();
        float scale = Math.min(1f, Math.min((w-8f)/360, (h-8f)/266));
        int ox = Math.round((w-360*scale)/2), oy = Math.round((h-266*scale)/2);
        client.currentScreen.mouseClicked(new Click(ox+x*scale, oy+y*scale, new MouseInput(0,0)), false);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
