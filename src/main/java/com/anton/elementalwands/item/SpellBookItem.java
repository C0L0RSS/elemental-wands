package com.anton.elementalwands.item;

import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class SpellBookItem extends Item {
    private final int tier;
    public SpellBookItem(Settings settings,int tier) { super(settings.maxCount(1));this.tier=tier; }
    @Override public ActionResult use(World world,PlayerEntity user,Hand hand) {
        if(user instanceof ServerPlayerEntity p)
            return com.anton.elementalwands.util.SpellBooks.use(p,tier,user.getStackInHand(hand)) ? ActionResult.SUCCESS : ActionResult.FAIL;
        return ActionResult.SUCCESS;
    }
}
