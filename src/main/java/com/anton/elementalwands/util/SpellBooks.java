package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import java.util.List;

/** Credits and claim receipts share the player's inventory save and survive death. */
public final class SpellBooks {
    public static final int LIMIT=1000000;
    public static List<Integer> credits(PlayerEntity p) {
        var n=p.getAttachedOrElse(EWAttachments.SPELL_BOOKS,new NbtCompound());
        return List.of(Math.clamp(n.getInt("tier0",0),0,LIMIT),Math.clamp(n.getInt("tier1",0),0,LIMIT),Math.clamp(n.getInt("tier2",0),0,LIMIT));
    }
    public static int availableTier(PlayerEntity p,WandSpells.Spell s) {
        return s==null || WandProgression.owns(p,s) ? -1 : availableTier(credits(p),s.category());
    }
    public static int availableTier(List<Integer> credits,WandSpells.Category category) {
        for(int i=category.ordinal();i<3;i++) if(i<credits.size() && credits.get(i)>0) return i;
        return -1;
    }
    public static boolean use(ServerPlayerEntity p,int tier,ItemStack stack) {
        if(tier<0 || tier>2 || stack.isEmpty() || !p.isAlive() || p.isSpectator()) return false;
        var n=p.getAttachedOrElse(EWAttachments.SPELL_BOOKS,new NbtCompound()).copy();
        int count=credits(p).get(tier);
        if(count>=LIMIT) { p.sendMessage(Text.literal("Use a free spell choice before opening another book."),false); return false; }
        n.putInt("tier"+tier,count+1);p.setAttached(EWAttachments.SPELL_BOOKS,n);stack.decrement(1);
        p.sendMessage(Text.literal("One free "+switch(tier){case 0->"Basic";case 1->"Basic or Technique";default->"Basic, Technique or Ultimate";}+" spell is available in the Spell Store."),false);
        com.anton.elementalwands.network.ModNetworking.syncPlayerData(p); return true;
    }
    static void spend(ServerPlayerEntity p,int tier) {
        var n=p.getAttachedOrElse(EWAttachments.SPELL_BOOKS,new NbtCompound()).copy();
        n.putInt("tier"+tier,Math.max(0,credits(p).get(tier)-1));p.setAttached(EWAttachments.SPELL_BOOKS,n);
    }
    public static boolean claim(ServerPlayerEntity p,String site) {
        var n=p.getAttachedOrElse(EWAttachments.SPELL_BOOKS,new NbtCompound()).copy();
        String key="church:"+site;
        if(n.getBoolean(key,false)) return false;
        // One-slot item: require actual space before recording a receipt; never drop a personal reward.
        if(p.getInventory().getEmptySlot()<0) {p.sendMessage(Text.literal("Make room, then open this chest to claim your spell book."),true);return false;}
        var book=new ItemStack(ModItems.SECONDARY_SPELL_BOOK);
        if(!p.getInventory().insertStack(book)) return false;
        n.putBoolean(key,true);p.setAttached(EWAttachments.SPELL_BOOKS,n);
        p.sendMessage(Text.literal("Your Guardian spell book is in your inventory. Right-click it for a free Basic or Technique spell."),false);
        return true;
    }
    private SpellBooks() {}
}
