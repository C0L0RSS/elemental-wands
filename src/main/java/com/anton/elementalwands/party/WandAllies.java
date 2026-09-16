package com.anton.elementalwands.party;

import com.anton.elementalwands.entity.AwakenedTreeEntity;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Server-authoritative protection, checked at impact/contact, including owner-backed summons and pets. */
public final class WandAllies {
    private WandAllies() {}
    private static UUID principal(Entity entity) {
        if (entity instanceof AwakenedTreeEntity tree && tree.getCasterUuid() != null) return tree.getCasterUuid();
        if (entity instanceof Tameable pet && pet.getOwnerReference() != null) return pet.getOwnerReference().getUuid();
        return entity.getUuid();
    }
    public static boolean protectedFrom(Entity caster, Entity target) {
        if (caster == null || target == null) return false;
        if (caster == target || caster.isTeammate(target)) return true;
        if (caster instanceof ServerPlayerEntity player) PartyManager.get(player.getEntityWorld().getServer()).remember(player);
        return target.getEntityWorld() instanceof ServerWorld world && protectedFrom(world, principal(caster), target);
    }
    public static boolean protectedFrom(ServerWorld world, UUID caster, Entity target) {
        if (caster == null || target == null) return false;
        UUID recipient = principal(target);
        if (caster.equals(recipient)) return true;
        var server = world.getServer();
        var parties = PartyManager.get(server);
        if (parties.allied(caster, recipient)) return true;
        var casterPlayer = server.getPlayerManager().getPlayer(caster);
        if (casterPlayer != null && casterPlayer.isTeammate(target)) return true;
        String casterName = parties.name(caster);
        String targetName = parties.name(recipient);
        var casterTeam = casterName == null ? null : server.getScoreboard().getScoreHolderTeam(casterName);
        var targetTeam = targetName == null ? target.getScoreboardTeam() : server.getScoreboard().getScoreHolderTeam(targetName);
        if (casterTeam != null && casterTeam == targetTeam) return true;
        // A PvP-disabled server must also block indirect thorns, crowd control and pet damage.
        return !server.isPvpEnabled() && casterName != null && (target instanceof PlayerEntity || targetName != null);
    }
}
