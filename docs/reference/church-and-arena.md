# Church and arena

The survival encounter starts at a naturally generated ruined sanctuary. Each site
can be restored once by defeating its Guardian; failed attempts can be retried.
Java owners live under `src/main/java/com/anton/elementalwands/church/` and `arena/`.

## Discovery and ritual

Ruins generate in eligible new Overworld terrain; existing explored chunks are not
retrofitted. Biome/terrain filtering means structure spacing is not a guaranteed
distance between churches. Locating churches is incremental and cancellable so
large searches do not block the server. Keep ticket cleanup and search timeouts.

The courtyard holds a stone effigy, an offering chest, and a carved pedestal.
The chest supplies the site-specific Guardian Heart; the short interaction hint
replaces old lore-book onboarding. Use that heart on the bowl or column to begin.
The seated heart and cyan trail communicate acceptance. No living dormant boss
stands in the courtyard: players ascend first, then the Guardian arrives above.

Admission checks the group, ground, loaded area, build height, and open lift paths
before consuming a heart or changing vegetation. Recall a lost heart by sneaking
and using the socket with an empty main hand; old copies are invalidated. A wipe
or abort resets the offering. Only one arena may run on a server at a time.

The visual direction is ornate fictional stone architecture, colored glass and a
teal roof, with a broken-ring emblem. Preserve the approved geometry and stone
palette. Do not restore real-world religious symbols, gold/quartz masonry, or a
smooth non-Minecraft statue from earlier concepts.

## Arena lifecycle

The eligible nearby Survival/Adventure group is sealed at admission. Invisible
passenger carriers share motion with the visual floor during ascent and descent;
do not reintroduce per-tick teleport movement. Players retain camera control, and
casting is suspended without spending resources during cinematic stages.

The fight takes place on a roofless 128-block-square floor above the terrain.
A single batched visible surface uses ordinary one-block texture repetitions;
invisible backing supplies collision. Avoid duplicate visible meshes and their
z-fighting. Use `Atlases.BLOCKS` when requesting the block atlas from AtlasManager;
the render layer uses the texture-file ID. These identifiers are not interchangeable.

Spells work within the arena while world travel, blinks and other escape paths
respect its bounds. Ordinary player block building is disabled. Temporary spell
structures must not replace the protected foundation.

Death eliminates a fighter, who respawns as a spectator inside the arena. They can
watch but cannot fight or rejoin. Disconnect also eliminates; reconnecting permits
spectating rather than re-entry. A full wipe ends the encounter. Return restores
recorded positions and game modes, including offline players on reconnection.
Wait for the respawn connection to reference the replacement player before moving
it; validate loaded ground rather than trusting a raw world-spawn fallback.

A real boss death during combat commits victory. Removal, Peaceful, an operator
abort, or a wipe do not. Restoration proceeds beneath the returning group; the ward
ends afterward. Seeded treasure cannot be rerolled by recall/restart and collected
chests never refill. Eligible enrolled players also claim one personal spell book
per site when opening its restored reward chest; require inventory space before
recording the receipt. See [progression](progression-and-controls.md).

## Persistence and terrain constraints

`GuardianChurchManager` journals sites in `elementalwands/guardian-churches.json`.
`GuardianArenaManager` independently journals recovery in
`elementalwands/guardian-arena.json`, both beneath the world save.

- Commit recovery/return records before admission or destructive transitions.
- Replay interrupted builds/restorations idempotently. Restart aborts a running
  fight safely; committed victory must not become a fresh loot roll.
- Save world/player changes before retiring their recovery records.
- Release only chunk force flags owned by this encounter; preserve pre-existing ones.
- Sweep only the originally empty arena prism, not surrounding buildings/containers.
- Preserve temporary-block ownership, equipment recovery, and offline returns.
- Use site accessors such as `Site.socket()` and `Site.offering()` rather than
  assuming that the saved building anchor is the current interaction position.

Unfinished old sites upgrade with replayable inventory/layout receipts, preserving
heart identity and offering contents. Completed player-modified sites are left alone.
Terrain blending skips water, trees, containers, roads and artificial surfaces;
saved absolute target heights avoid repeatedly expanding earthwork on reload.
Ritual clearing permits natural vegetation in lift columns without clearing stone
roofs or arbitrary structures. Admission must precede those world mutations.

## Hollow Crypt realm

The Hollow Necromancer is fought in its own dimension, `elementalwands:hollow_crypt`: a flat
world with a fixed night, no weather or skylight, Nether-style thick fog and drifting ash.
Each fight slot is a clearing 88 blocks across. It sits inside a dead forest of about 200
cosmetic trees that reaches 124 blocks out, with some giants whose high boughs arch over the rim.
An invisible barrier shell and lid keep players and the boss within 44 blocks of the centre and
30 blocks above the ground. Owners are under `src/main/java/com/anton/elementalwands/crypt/`.

`art/hollow_crypt/build_layout.py` authors the clearing and forest. Its default run writes
the browser preview to `.local-previews/hollow-crypt/` (launch entry `hollow-crypt-preview`,
port 8360), which also shows the overworld graveyard. `--install` writes the realm as
48×48 structure tiles plus `hollow_graveyard.nbt`, and `--check` detects drift. The radius, ceiling and tile grid are
duplicated in `HollowCryptRealm` and must stay in step.

A slot is laid out the first time it is used, one tile per server tick, and is then reused.
`elementalwands/hollow-crypt.json` records the built slots and each player's return point.
When the installed tiles change, every slot moves to untouched ground instead of being
rebuilt over the old layout. Inside the realm, every player teleport (spells, pearls,
commands, portals) must stay in the clearing and cannot cross into or out of the realm, except
the crypt's own entry and exit. A player who escapes the clearing is pulled back. Survival
players cannot break or place blocks there. Temporary spell blocks still work and restore
themselves as usual. Death in the realm respawns the player normally and spends their return point.

Graveyards generate in plains, meadow, savanna, snowy plains, forest, birch forest,
dark forest, taiga and swamp biomes. They're found with
`/locate structure elementalwands:hollow_graveyard`, and new chunks only. Using the headstone
altar pulls every living player within 16 blocks into a free slot, blinded for a moment. It
records their return points first. The boss rises on the circle three seconds after they
arrive. When it dies, everyone in the slot is sent home ten seconds later. The altar is
recognised by its block pattern in any rotation, not by the structure record, so
`/place structure` copies work too. A player-built copy of the pattern would work as well.

Commands: `/ew crypt leave` is open to anyone in the realm, so a survival group is never
stranded. Operators also have `enter [slot]`, `summon` (clears the slot and raises a
fighting boss on the circle), `reset` (clears entities and restores the layout) and
`status`. No offering is needed yet. The sealed party, spectating after death, rewards and
restart recovery for an interrupted fight are not built. The graveyard's chest is empty.

## Authoring and tests

`tools/build_guardian_church.py` owns ruined/restored layouts and structure output;
`--check` detects drift. `tools/prepare_guardian_pedestal.py` owns the native
pedestal models. Offline building previews lack real surrounding terrain and
Minecraft lighting; use native checks for those.

Operator commands: `/ew guardian church place|locate|cancel`,
`/locate structure elementalwands:guardian_church`, and
`/ew guardian arena start|status|stop`. Placement mutates the world, so use a
suitable disposable area. `arena stop` is the emergency return operation.
See [testing](testing.md) for lifecycle, restart, worldgen, and client fixtures.
