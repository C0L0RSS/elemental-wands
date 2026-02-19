package com.anton.elementalwands;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.BlizzardManager;
import com.anton.elementalwands.util.BlinkRiftManager;
import com.anton.elementalwands.util.ChillTracker;
import com.anton.elementalwands.util.CycloneManager;
import com.anton.elementalwands.util.EventHorizonManager;
import com.anton.elementalwands.util.HollowPurpleChargeManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.MovementDisruptManager;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TemporarySnowManager;
import com.anton.elementalwands.util.TitanDomeManager;
import com.anton.elementalwands.util.BlazeTrailManager;
import net.fabricmc.api.ModInitializer;

public class ElementalWandsMod implements ModInitializer {
    public static final String MOD_ID = "elementalwands";

    @Override
    public void onInitialize() {
        TemporarySnowManager.init();
        TemporaryBlockManager.init();
        ChillTracker.init();
        BlizzardManager.init();
        CycloneManager.init();
        MeteorManager.init();
        TitanDomeManager.init();
        BlazeTrailManager.init();
        MovementDisruptManager.init();
        BlinkRiftManager.init();
        EventHorizonManager.init();
        HollowPurpleChargeManager.init();
        ModEntities.registerAll();
        ModItems.registerAll();
        ModNetworking.registerPayloads();
        ModNetworking.registerC2SReceivers();
    }
}
