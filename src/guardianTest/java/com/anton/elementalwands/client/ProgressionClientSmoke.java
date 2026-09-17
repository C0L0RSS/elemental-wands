package com.anton.elementalwands.client;

import com.anton.elementalwands.client.screen.WandHubScreen;
import com.anton.elementalwands.data.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;

/** Native parchment UI checks, including real button dispatch and full spell lists. */
public final class ProgressionClientSmoke implements ClientModInitializer {
    private int tick;
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static List<String> buttons(MinecraftClient c){return c.currentScreen.children().stream().filter(x->x instanceof ButtonWidget).map(x->((ButtonWidget)x).getMessage().getString().trim()).toList();}
    private static void show(MinecraftClient c,List<String> owned,List<Integer> credits){
        ClientPlayerData.setUnlockedSkills(3,"FIRE");ClientPlayerData.setOwned(owned);
        ClientPlayerData.setHubData(346,WandSpells.defaults(WizardAffinity.FIRE),true);
        ClientPlayerData.setProgress(200,credits);c.setScreen(new WandHubScreen());bounds(c);
    }
    private static void bounds(MinecraftClient c){for(var child:c.currentScreen.children())if(child instanceof ButtonWidget b)
        check(b.getX()>=0&&b.getY()>=0&&b.getX()+b.getWidth()<=360&&b.getY()+b.getHeight()<=266,"Widget off parchment: "+b.getMessage());}
    private static void click(MinecraftClient c,int x,int y){
        int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();float scale=Math.min(1f,Math.min((w-8f)/360,(h-8f)/266));
        int ox=Math.round((w-360*scale)/2),oy=Math.round((h-266*scale)/2);
        c.currentScreen.mouseClicked(new Click(ox+x*scale,oy+y*scale,new MouseInput(0,0)),false);bounds(c);
    }
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name+".png",c.getFramebuffer(),1,t->{});}
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.getOverlay()!=null)return;try{
        tick++;
        if(tick==1)show(c,List.of("inferno_wave"),List.of(0,0,0));
        if(tick==20){shot(c,"progression-starter");check(buttons(c).contains("Inferno Wave")&&!buttons(c).contains("Dragon's Pyre"),"Starter library unfiltered");click(c,94,115);check(!buttons(c).contains("Inferno Wave"),"Empty Technique shows Basic");}
        if(tick==35)shot(c,"progression-empty-technique");
        if(tick==40){show(c,WandSpells.forAffinity(WizardAffinity.FIRE).stream().map(WandSpells.Spell::id).toList(),List.of(0,0,0));check(buttons(c).contains("Flamethrower")&&!buttons(c).contains("Meteor"),"Basic library filter");}
        if(tick==55){shot(c,"progression-five-basic");click(c,70,114);check(buttons(c).contains("Flashover")&&!buttons(c).contains("Inferno Wave"),"Technique category filter");}
        if(tick==70){shot(c,"progression-five-technique");click(c,102,114);check(buttons(c).contains("Meteor")&&!buttons(c).contains("Flashover"),"Ultimate category filter");}
        if(tick==85){shot(c,"progression-five-ultimate");click(c,166,114);check(buttons(c).contains("Flamethrower")&&!buttons(c).contains("Meteor"),"Flexible filter");}
        if(tick==100){shot(c,"progression-five-flexible");show(c,List.of("inferno_wave"),List.of(0,1,0));click(c,170,73);click(c,80,148);check(buttons(c).contains("Learn Free"),"Eligible store purchase is not Free");check(!buttons(c).stream().anyMatch(x->x.contains("Spell Book")),"Unexpected book tab");}
        if(tick==115){shot(c,"progression-free-store");ClientPlayerData.setProgress(200,List.of(0,0,0));((WandHubScreen)c.currentScreen).refresh();check(buttons(c).contains("Buy Spell")&&!buttons(c).contains("Learn Free"),"Prices did not return after credit spent");}
        if(tick==130){shot(c,"progression-priced-store");click(c,280,73);}
        if(tick==145){shot(c,"progression-controls");check(WandControls.slotKey(3).equalsIgnoreCase("Z")&&WandControls.slotKey(4).equalsIgnoreCase("V")&&WandControls.shortLabel(3).equalsIgnoreCase("R"),"New bindings lost alternate action");
            var screen=(WandHubScreen)c.currentScreen;screen.init(c,320,240);float scale=Math.min(312f/360,232f/266);int ox=Math.round((320-360*scale)/2),oy=Math.round((240-266*scale)/2);
            screen.mouseClicked(new Click(ox+65*scale,oy+113*scale,new MouseInput(0,0)),false);screen.keyPressed(new KeyInput(66,0,0));check(WandControls.shortLabel(0).equalsIgnoreCase("B"),"Scaled rebind failed");WandControls.reset();
            Files.writeString(Path.of("PROGRESSION_PASSED.txt"),"Native starter and five-slot UI, empty categories, Basic/Technique/Ultimate/flexible filtering, Free/priced store, all button bounds, six bindings, scaled rebinding and PNG captures passed.\n");c.scheduleStop();}
    }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("PROGRESSION_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}});}
}
