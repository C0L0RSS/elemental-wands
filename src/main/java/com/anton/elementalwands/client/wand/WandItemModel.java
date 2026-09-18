package com.anton.elementalwands.client.wand;

import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.ModelSettings;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;

/** The holder's affinity supplies color; unheld wands are neutral quartz, never another player's color. */
public final class WandItemModel implements ItemModel {
    private final ModelSettings settings;
    private final WandRenderer renderer=new WandRenderer();
    public WandItemModel(ModelSettings settings) {this.settings=settings;}
    public static void register() {
        ModelLoadingPlugin.register(plugin->plugin.modifyItemModelAfterBake().register(ModelModifier.OVERRIDE_PHASE,(model,context)->{
            if(!context.itemId().equals(Identifier.of("elementalwands","fractured_wand")))return model;
            var baker=context.bakeContext().blockModelBaker();
            var base=baker.getModel(Identifier.of("elementalwands","item/fractured_wand"));
            return new WandItemModel(ModelSettings.resolveSettings(baker,base,base.getTextures()));
        }));
    }
    public static WizardAffinity affinity(HeldItemContext holder,ItemDisplayContext display) {
        var client=MinecraftClient.getInstance();
        if(holder!=null && holder.getEntity() instanceof PlayerEntity player)
            return player==client.player?ClientPlayerData.getAffinity():EWAttachments.getAffinity(player);
        if(display==ItemDisplayContext.GUI && holder==null && client.player!=null)return ClientPlayerData.getAffinity();
        return WizardAffinity.NONE;
    }
    @Override public void update(ItemRenderState state,ItemStack stack,ItemModelManager manager,
                                 ItemDisplayContext display,ClientWorld world,HeldItemContext holder,int seed) {
        var client=MinecraftClient.getInstance();
        double time=world==null?0:(world.getTime()+client.getRenderTickCounter().getTickProgress(false))/20.0;
        var data=new WandRenderer.State(WandMesh.element(affinity(holder,display).name()),time);
        state.addModelKey(this);state.addModelKey(data.element());state.markAnimated();
        var layer=state.newLayer();layer.setVertices(()->display==ItemDisplayContext.GUI?WandMesh.GUI_BOUNDS:WandMesh.BOUNDS);
        layer.setSpecialModel(renderer,data);settings.addSettings(layer,display);
    }
}
