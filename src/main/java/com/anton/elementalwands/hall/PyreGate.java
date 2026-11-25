package com.anton.elementalwands.hall;

import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.crypt.HollowCryptManager;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.summit.StormRocEncounter;
import com.anton.elementalwands.summit.StormSummitManager;
import com.anton.elementalwands.party.PartyLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

/**
 * The Pyre King's way in: the Burned Gate, at the end of a bridge to nowhere over a Nether lava lake. Its arch is
 * filled with banked fire and bound shut with chains, and on the chains hangs the king's seal. Touching the seal twice
 * burns the chains through: everyone standing by it is taken to a free slot of the Burned Throne Hall, where his intro
 * plays and the fight follows.
 *
 * <p>Fallen players watch their own fight. A wipe or victory returns the party to the gate.
 * The hall saves each player's way home and original game mode in case the fight is interrupted.
 */
public final class PyreGate {
    private PyreGate() {}

    /** Ticks: a second touch confirms within CONFIRM; a held right-click repeats faster than REPEAT and never counts as one. */
    private static final int CONFIRM = 100, REPEAT = 10;
    /** The chains burn this long before the party is taken. */
    private static final int DEPART = 50;
    /** Everyone this close to the seal goes with whoever touched it, and must still be this close (and a little) when the chains part. */
    private static final int RADIUS = 16, STRAY = 8;
    /**
     * A party's slot of the hall is kept loaded and ticking from the moment the chains catch: the king is seated on
     * his throne in the same tick the party arrives, and a king put into ground that is not loaded yet is put away
     * again at once. No SERIALIZE flag: a crash leaves no tickets.
     */
    private static final net.minecraft.server.world.ChunkTicketType PARTY_TICKET = net.minecraft.registry.Registry.register(
            net.minecraft.registry.Registries.TICKET_TYPE, net.minecraft.util.Identifier.of("elementalwands", "pyre_gate_party"),
            new net.minecraft.server.world.ChunkTicketType(0,
                    net.minecraft.server.world.ChunkTicketType.FOR_LOADING | net.minecraft.server.world.ChunkTicketType.FOR_SIMULATION));
    private static final int TICKET_RADIUS = 6 + net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.FULL)
            - net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.ENTITY_TICKING);
    /** The party is not taken before the hall is ready for it, however long that is; past this many ticks the gate gives up. */
    private static final int GIVE_UP = 600;

    private static final class Touch {
        final String site;
        final int first;
        int last;
        Touch(String site, int now) { this.site = site; first = last = now; }
    }

    private record Gate(String site, RegistryKey<World> world, BlockPos seal, int opened) {}

    /** One hall fight. A gate reserves its slot before departure; a command starts it in place. */
    private static final class Fight {
        final Gate gate;
        final int slot;
        final BlockPos centre;
        final Set<UUID> roster = new LinkedHashSet<>(), standing = new LinkedHashSet<>();
        /** Null until the party has been taken to the hall. */
        PyreKingEntity king;
        boolean won;
        Fight(Gate gate, int slot, BlockPos centre) {
            this.gate = gate; this.slot = slot; this.centre = centre;
        }
    }

    private static final Map<UUID, Touch> touches = new HashMap<>();
    private static final List<Fight> fights = new ArrayList<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PyreGate::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) fall(player, "You fell. Respawn to watch the rest of the fight.");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            touches.remove(handler.getPlayer().getUuid());
            fall(handler.getPlayer(), null);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { touches.clear(); fights.clear(); });
    }

    // ------------------------------------------------------------------ the seal

    /** A gate's identity: its world and its seal's block. */
    public static String siteKey(World world, BlockPos seal) {
        return world.getRegistryKey().getValue() + "|" + seal.getX() + "|" + seal.getY() + "|" + seal.getZ();
    }

    /** Where a party comes back to: on the gate's platform, facing the seal. */
    static Point stand(World world, BlockPos seal, Direction facing) {
        BlockPos cell = seal.offset(facing, PyreGateLayout.STAND[2] - PyreGateLayout.SEAL[2]).up(PyreGateLayout.STAND[1] - PyreGateLayout.SEAL[1]);
        var look = facing.getOpposite().getVector();
        // Minecraft yaw: 0 faces south (+z), 90 faces west (-x).
        float yaw = (float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
        return new Point(world.getRegistryKey().getValue().toString(), cell.getX() + .5, cell.getY(), cell.getZ() + .5, yaw, 0);
    }

    /**
     * A player touched the seal. The first touch names who would go; a second by the same player at the same seal
     * within five seconds burns the chains. Returns the reply, or null when nothing should be said (a held click, or
     * not a seal).
     */
    public static String touch(ServerPlayerEntity toucher, BlockPos pos) {
        ServerWorld world = toucher.getEntityWorld();
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(ModBlocks.PYRE_SEAL)) return null;
        int now = world.getServer().getTicks();
        String site = siteKey(world, pos);
        Touch last = touches.get(toucher.getUuid());
        // A held right-click repeats every few ticks: it keeps the first touch alive but never confirms it.
        if (last != null && last.site.equals(site) && now - last.last < REPEAT) { last.last = now; return null; }
        String refusal = refusal(toucher, world, site);
        if (refusal != null) { touches.remove(toucher.getUuid()); return refusal; }
        List<ServerPlayerEntity> group = group(toucher, world, pos);
        if (last != null && last.site.equals(site) && now - last.first <= CONFIRM) {
            touches.remove(toucher.getUuid());
            return open(toucher, world, pos, state.get(PyreSealBlock.FACING), site, group);
        }
        touches.put(toucher.getUuid(), new Touch(site, now));
        light(world, pos, true);
        sound(world, pos, SoundEvents.BLOCK_CHAIN_BREAK, 2.4f, .5f);
        sound(world, pos, SoundEvents.ITEM_FIRECHARGE_USE, 1.6f, .6f);
        return "The seal stirs. Touch it again within 5 seconds to wake the Pyre King for: " + names(group) + ".";
    }

    private static String refusal(ServerPlayerEntity toucher, ServerWorld world, String site) {
        if (PyreHallManager.enrolled() == null || world.getServer().getWorld(PyreHall.WORLD) == null)
            return "The seal is cold. (The Burned Throne Hall is unavailable; see the server log.)";
        if (world.getRegistryKey() == PyreHall.WORLD) return "The seal is cold here.";
        if (world.getDifficulty() == Difficulty.PEACEFUL) return "The seal is cold. (The Pyre King does not wake on Peaceful.)";
        if (!toucher.isAlive() || toucher.isCreative() || toucher.isSpectator()) return "Only a Survival or Adventure player can wake the Pyre King.";
        if (held(toucher.getUuid())) return "The gate is already answering you.";
        if (active(site)) return "The gate burns for another party. Wait for their fight to end.";
        if (elsewhere(toucher)) return "Finish your current fight first.";
        return null;
    }

    /** Sealed into, or inside, another boss's realm. */
    private static boolean elsewhere(ServerPlayerEntity player) {
        return GuardianArenaManager.isParticipant(player) || GuardianArenaManager.inRealm(player) || HollowCryptManager.inRealm(player)
                || StormSummitManager.inRealm(player) || StormRocEncounter.enrolled(player) || PyreHallManager.inRealm(player);
    }

    /**
     * Who goes: whoever touched the seal, and everyone else in this world within 16 blocks of it who is alive in
     * Survival or Adventure and not already held by a fight.
     */
    public static List<ServerPlayerEntity> group(ServerPlayerEntity toucher, ServerWorld world, BlockPos seal) {
        List<ServerPlayerEntity> group = new ArrayList<>(List.of(toucher));
        Vec3d at = Vec3d.ofCenter(seal);
        for (ServerPlayerEntity p : world.getPlayers())
            if (p != toucher && p.squaredDistanceTo(at) <= RADIUS * RADIUS && p.isAlive() && !p.isCreative() && !p.isSpectator()
                    && !held(p.getUuid()) && !elsewhere(p)) group.add(p);
        return group;
    }

    private static String names(List<ServerPlayerEntity> group) {
        return String.join(", ", group.stream().map(p -> p.getName().getString()).toList());
    }

    /**
     * The confirmed touch: reserves a free slot of the hall (laying it out if needed) and records everyone's way back
     * before anyone moves: side by side before the seal, so a party comes back in a line rather than on one spot
     * ({@link PartyLine}). The chains then burn for a moment; {@link #tick} takes the party.
     */
    private static String open(ServerPlayerEntity toucher, ServerWorld world, BlockPos seal, Direction facing, String site, List<ServerPlayerEntity> group) {
        MinecraftServer server = world.getServer();
        ServerWorld realm = server.getWorld(PyreHall.WORLD);
        int slot = freeSlot(realm);
        if (slot < 0) return "The Pyre King is fighting elsewhere. Try again soon.";
        Point home = stand(world, seal, facing);
        List<Point> homes = new ArrayList<>();
        for (Vec3d at : PartyLine.spots(world, new Vec3d(home.x(), home.y(), home.z()), home.yaw(), group.size()))
            homes.add(new Point(home.dimension(), at.x, at.y, at.z, home.yaw(), home.pitch()));
        if (!PyreHallManager.recordParty(group, homes))
            return "The seal is cold. (Could not record your way back.)";
        Gate gate = new Gate(site, world.getRegistryKey(), seal.toImmutable(), server.getTicks());
        Fight fight = new Fight(gate, slot, PyreHallManager.slotCentre(slot));
        group.forEach(p -> fight.roster.add(p.getUuid()));
        fights.add(fight);
        PyreHallManager.raise(realm, slot);
        realm.getChunkManager().addTicket(PARTY_TICKET, new net.minecraft.util.math.ChunkPos(fight.centre), TICKET_RADIUS);
        light(world, seal, true);
        sound(world, seal, SoundEvents.BLOCK_CHAIN_BREAK, 3f, .4f);
        sound(world, seal, SoundEvents.ENTITY_BLAZE_SHOOT, 2.4f, .5f);
        for (ServerPlayerEntity p : group)
            if (p != toucher) p.sendMessage(Text.literal(toucher.getName().getString() + " touched the king's seal. The chains burn."), false);
        return "The chains burn. The Burned Throne Hall takes: " + names(group) + ".";
    }

    /**
     * A slot with no fight and no victors waiting, not being laid out for someone else and with no living player
     * standing in it. Ready slots first; else one to lay out. -1 if every slot is busy.
     */
    private static int freeSlot(ServerWorld realm) {
        int unbuilt = -1;
        for (int slot = 0; slot < PyreHall.SLOTS; slot++) {
            BlockPos centre = PyreHallManager.slotCentre(slot);
            if (fights.stream().anyMatch(fight -> fight.centre.equals(centre)) || PyreHallManager.releasing(centre) || PyreHallManager.building(slot)) continue;
            var area = PyreHall.footprint(centre);
            if (!realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && area.contains(p.getEntityPos())).isEmpty()) continue;
            if (PyreHallManager.ready(slot)) return slot;
            if (unbuilt < 0) unbuilt = slot;
        }
        return unbuilt;
    }

    // ------------------------------------------------------------------ light

    /** Lights the seal and blazes the fire in its arch, or banks them again. */
    static void light(ServerWorld world, BlockPos seal, boolean lit) {
        BlockState state = world.getBlockState(seal);
        if (!state.isOf(ModBlocks.PYRE_SEAL)) return;
        Direction facing = state.get(PyreSealBlock.FACING), along = facing.rotateYClockwise();
        if (state.get(PyreSealBlock.LIT) != lit) world.setBlockState(seal, state.with(PyreSealBlock.LIT, lit), Block.NOTIFY_LISTENERS);
        BlockPos behind = seal.offset(facing.getOpposite(), PyreGateLayout.VEIL_BEHIND);
        for (int side = -PyreGateLayout.VEIL_SIDE; side <= PyreGateLayout.VEIL_SIDE; side++)
            for (int up = -PyreGateLayout.VEIL_DOWN; up <= PyreGateLayout.VEIL_UP; up++) {
                BlockPos pos = behind.offset(along, side).up(up);
                BlockState veil = world.getBlockState(pos);
                if (veil.isOf(ModBlocks.PYRE_GATE_VEIL) && veil.get(PyreGateVeilBlock.LIT) != lit)
                    world.setBlockState(pos, veil.with(PyreGateVeilBlock.LIT, lit), Block.NOTIFY_LISTENERS);
            }
        if (lit) world.scheduleBlockTick(seal, ModBlocks.PYRE_SEAL, PyreSealBlock.CHECK);
    }

    /** Whether something still holds this seal lit: a touch waiting for its second, or a party of this gate's. */
    static boolean holdsLit(ServerWorld world, BlockPos seal) {
        String site = siteKey(world, seal);
        int now = world.getServer().getTicks();
        return active(site)
                || touches.values().stream().anyMatch(touch -> touch.site.equals(site) && now - touch.first <= CONFIRM);
    }

    /** The chains across the arch burn: flames along every link in the seal's plane. */
    private static void burn(ServerWorld world, BlockPos seal, int strength) {
        BlockState state = world.getBlockState(seal);
        if (!state.isOf(ModBlocks.PYRE_SEAL)) return;
        Direction along = state.get(PyreSealBlock.FACING).rotateYClockwise();
        for (int side = -PyreGateLayout.VEIL_SIDE; side <= PyreGateLayout.VEIL_SIDE; side++)
            for (int up = -PyreGateLayout.VEIL_DOWN; up <= PyreGateLayout.VEIL_UP; up++) {
                BlockPos pos = seal.offset(along, side).up(up);
                if (!world.getBlockState(pos).isOf(Blocks.IRON_CHAIN) && !pos.equals(seal)) continue;
                world.spawnParticles(ParticleTypes.FLAME, true, false, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, strength, .25, .25, .25, .01);
                if (world.getRandom().nextInt(6) == 0)
                    world.spawnParticles(ParticleTypes.LAVA, true, false, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, .2, .2, .2, 0);
            }
    }

    // ------------------------------------------------------------------ parties

    /** Check before a command removes the current king or starts an intro in a reserved slot. */
    static String commandStartRefusal(BlockPos centre) {
        Fight fight = fightAt(centre);
        if (PyreHallManager.releasing(centre) || fight != null && fight.won) return "Wait for the hall to release the previous party.";
        if (fight != null && fight.king == null) return "The Burned Gate is bringing a party to this hall.";
        return null;
    }

    private static Fight fightAt(BlockPos centre) {
        for (Fight fight : fights) if (fight.centre.equals(centre)) return fight;
        return null;
    }

    /** Operators can start the same encounter in an occupied hall. An empty rehearsal has no party to release. */
    static boolean startInHall(ServerWorld realm, int slot, BlockPos centre, PyreKingEntity king) {
        Fight current = fightAt(centre);
        if (current != null) {
            // Replacing the king mid-fight keeps spectators and their surviving teammates together.
            current.king = king;
            return true;
        }
        var area = PyreHall.footprint(centre);
        var group = realm.getPlayers(p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && area.contains(p.getEntityPos()) && !held(p.getUuid()));
        if (group.isEmpty()) return true;
        if (!PyreHallManager.recordParty(group, null)) {
            king.discard();
            return false;
        }
        Fight fight = new Fight(null, slot, centre);
        fight.king = king;
        group.forEach(p -> fight.roster.add(p.getUuid()));
        fight.standing.addAll(fight.roster);
        fights.add(fight);
        realm.getChunkManager().addTicket(PARTY_TICKET, new net.minecraft.util.math.ChunkPos(centre), TICKET_RADIUS);
        return true;
    }

    private static Fight fightOf(UUID id) {
        for (Fight fight : fights)
            if (fight.roster.contains(id)) return fight;
        return null;
    }

    public static boolean held(UUID id) {
        var enrolled = PyreHallManager.enrolled();
        return enrolled != null && enrolled.contains(id.toString());
    }

    /** A fallen fighter's slot, including the interval before their respawn is moved into spectator mode. */
    public static BlockPos spectatorSlot(UUID id) {
        Fight fight = fightOf(id);
        return fight != null && fight.king != null && !fight.standing.contains(id) ? fight.centre : null;
    }

    public static boolean spectatorViewer(PyreKingEntity king, ServerPlayerEntity player) {
        Fight fight = fightOf(player.getUuid());
        return fight != null && fight.king == king && !fight.standing.contains(player.getUuid());
    }

    public static boolean eligible(PyreKingEntity king, ServerPlayerEntity player) {
        for (Fight fight : fights) {
            if (fight.king == king) return !fight.won && fight.standing.contains(player.getUuid())
                    && player.isAlive() && !player.isSpectator() && !player.isCreative() && player.getEntityWorld() == king.getEntityWorld();
        }
        return spectatorSlot(player.getUuid()) == null;
    }

    static void leave(UUID id) {
        Fight fight = fightOf(id);
        if (fight != null) {
            fight.standing.remove(id);
            fight.roster.remove(id);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerWorld realm = server.getWorld(PyreHall.WORLD);
        if (realm == null || PyreHallManager.enrolled() == null) return;
        int now = server.getTicks();
        touches.values().removeIf(touch -> now - touch.first > CONFIRM);
        for (Fight fight : List.copyOf(fights)) {
            if (fight.king == null) tickDeparting(server, realm, fight, now);
            else tickFighting(server, fight);
        }
        returnPlayers(server, realm);
    }

    /**
     * The chains burn: whoever has gone (dead, offline, wandered off) is left behind, and when they part the rest
     * are taken to the hall, where the king wakes.
     */
    private static void tickDeparting(MinecraftServer server, ServerWorld realm, Fight fight, int now) {
        Gate gate = fight.gate;
        ServerWorld world = server.getWorld(gate.world);
        Vec3d at = Vec3d.ofCenter(gate.seal);
        for (UUID id : List.copyOf(fight.roster)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.getEntityWorld() == world
                    && p.squaredDistanceTo(at) <= (RADIUS + STRAY) * (RADIUS + STRAY)) continue;
            fight.roster.remove(id);
            PyreHallManager.forgetEntry(id.toString());
            PyreHallManager.persist();
            if (p != null && p.isAlive()) p.sendMessage(Text.literal("You are too far from the gate. It leaves you behind."), false);
        }
        if (fight.roster.isEmpty() || world == null) { end(server, fight); return; }
        if (now % 4 == 0) burn(world, gate.seal, 2);
        if (now - gate.opened < DEPART) return;
        // Not before the slot is laid out and the ground from his throne to where they arrive is loaded and ticking.
        boolean ready = PyreHallManager.ready(fight.slot);
        if (!ready) PyreHallManager.raise(realm, fight.slot);
        if (!ready || !realm.shouldTickEntityAt(PyreHall.cell(fight.centre, 0, 7, -50)) || !realm.shouldTickEntityAt(fight.centre)
                || !realm.shouldTickEntityAt(BlockPos.ofFloored(PyreHall.arrival(fight.centre)))) {
            if (now - gate.opened < GIVE_UP) return;
            for (UUID id : List.copyOf(fight.roster)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                PyreHallManager.forgetEntry(id.toString());
                if (p != null) p.sendMessage(Text.literal("The fire dies back. The Burned Throne Hall did not answer; touch the seal again."), false);
            }
            PyreHallManager.persist();
            end(server, fight);
            return;
        }
        burn(world, gate.seal, 12);
        sound(world, gate.seal, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2f, .7f);
        List<ServerPlayerEntity> party = new ArrayList<>();
        for (UUID id : fight.roster) party.add(server.getPlayerManager().getPlayer(id));
        PyreHallManager.arriveAll(party, realm, fight.slot);
        fight.king = PyreHallManager.wake(realm, fight.centre, party);
        if (fight.king == null) {
            for (ServerPlayerEntity p : party) home(p, "The Pyre King does not wake. The gate lets you go.");
            end(server, fight);
            return;
        }
        fight.standing.addAll(fight.roster);
    }

    /** In the hall: the fight is won when he dies, and lost when nobody of the party is left standing in his slot. */
    private static void tickFighting(MinecraftServer server, Fight fight) {
        for (UUID id : List.copyOf(fight.roster)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (fight.gate == null && p != null && p.isAlive() && p.isCreative() && PyreHallManager.inRealm(p)) {
                // An operator switching to Creative steps out of the command-started party to inspect the hall.
                fight.roster.remove(id);
                PyreHallManager.withdraw(p);
            }
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator() || !PyreHallManager.inRealm(p)
                    || !PyreHall.nearestCentre(p.getEntityPos()).equals(fight.centre)) fight.standing.remove(id);
        }
        if (fight.won) {
            if (!PyreHallManager.releasing(fight.centre)) end(server, fight);
            return;
        }
        if (fight.king.isDead()) {
            // The hall sends the victors home ten seconds on; until then this party keeps its slot and its gate.
            fight.won = true;
            return;
        }
        if (fight.king.isRemoved() || fight.standing.isEmpty()) {
            if (!fight.king.isRemoved()) fight.king.discard();
            end(server, fight);
        }
    }

    /** A member who dies or disconnects is out of the fight; while the chains still burn they simply never went. */
    private static void fall(ServerPlayerEntity player, String message) {
        for (Fight fight : fights) {
            if (!fight.roster.contains(player.getUuid())) continue;
            if (fight.king == null) {
                fight.roster.remove(player.getUuid());
                PyreHallManager.forgetEntry(player.getUuidAsString());
                PyreHallManager.persist();
            } else if (fight.standing.remove(player.getUuid()) && message != null && !fight.won && !fight.standing.isEmpty()) {
                player.sendMessage(Text.literal(message), false);
            }
        }
    }

    private static void end(MinecraftServer server, Fight fight) {
        fights.remove(fight);
        ServerWorld realm = server.getWorld(PyreHall.WORLD);
        if (realm != null) realm.getChunkManager().removeTicket(PARTY_TICKET, new net.minecraft.util.math.ChunkPos(fight.centre), TICKET_RADIUS);
        if (fight.gate == null) return;
        ServerWorld world = server.getWorld(fight.gate.world);
        BlockPos seal = fight.gate.seal;
        // Only if the gate is loaded; otherwise the seal finds out for itself when it next ticks.
        if (world != null && world.isChunkLoaded(seal.getX() >> 4, seal.getZ() >> 4) && !holdsLit(world, seal)) light(world, seal, false);
    }

    /** Resolve respawns and rejoins after the connection points at the live player, using the current fight. */
    private static void returnPlayers(MinecraftServer server, ServerWorld realm) {
        var enrolled = PyreHallManager.enrolled();
        if (enrolled.isEmpty()) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!enrolled.contains(p.getUuidAsString()) || !p.isAlive() || p.networkHandler.player != p) continue;
            Fight fight = fightOf(p.getUuid());
            if (fight == null) {
                home(p, "The hall lets you go. You return to where you entered.");
            } else if (fight.king != null && !fight.standing.contains(p.getUuid())
                    && (!p.isSpectator() || p.getEntityWorld() != realm || !PyreHall.footprint(fight.centre).contains(p.getEntityPos()))) {
                PyreHallManager.spectate(p, realm, fight.centre);
            }
        }
    }

    private static void home(ServerPlayerEntity player, String message) {
        boolean gate = PyreHallManager.sendHome(player);
        player.sendMessage(Text.literal(gate ? message : "No way back was recorded; sent to world spawn."), false);
    }

    private static void sound(ServerWorld world, BlockPos at, SoundEvent event, float volume, float pitch) {
        world.playSound(null, at.getX() + .5, at.getY() + .5, at.getZ() + .5, event, SoundCategory.BLOCKS, volume, pitch);
    }

    static String status() {
        StringBuilder out = new StringBuilder();
        for (Fight fight : fights) out.append(" Hall party in slot ").append(fight.slot).append(": ")
                .append(fight.king == null ? "the chains are burning for " + fight.roster.size()
                        : fight.won ? "won, going home" : fight.standing.size() + "/" + fight.roster.size() + " standing").append('.');
        return out.toString();
    }

    /** For fixtures: whether a party from the gate with this seal is on its way or fighting. */
    public static boolean active(World world, BlockPos seal) {
        return active(siteKey(world, seal));
    }

    private static boolean active(String site) {
        return fights.stream().anyMatch(fight -> fight.gate != null && fight.gate.site.equals(site));
    }
}
