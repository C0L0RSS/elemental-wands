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
Ambient light is 0.2 and the fog is a dim grey-teal, so unlit ground stays a readable night.
Each fight slot is a clearing 88 blocks across. Ten soul-fire braziers ring its rim, dark until
the Necromancer's intro slam lights them (see [Necromancer combat](necromancer-combat.md#intro-cinematic)), and seven
soul lanterns on existing cover (one per grave-marker group, one per stump) light the interior. It sits inside a dead forest of about 200
cosmetic trees that reaches 124 blocks out, with some giants whose high boughs arch over the rim.
An invisible barrier shell and lid keep players and the boss within 44 blocks of the centre and
30 blocks above the ground. The one exception is the boss's siege perch: eight bough tops above
the lid, seen from the clearing but never reached (see [Necromancer combat](necromancer-combat.md#sieges)). Owners are under `src/main/java/com/anton/elementalwands/crypt/`.

`art/hollow_crypt/build_layout.py` authors the clearing and forest. Its default run writes
the browser preview to `.local-previews/hollow-crypt/` (launch entry `hollow-crypt-preview`,
port 8360), which also shows the overworld graveyard. `--install` writes the realm as
48×48 structure tiles plus `hollow_graveyard.nbt` and the generated siege perches
(`crypt/HollowCryptPerches.java`), and `--check` detects drift in all of them. The radius, ceiling and tile grid are
duplicated in `HollowCryptRealm` and must stay in step.

A slot is laid out the first time it is used, one tile per server tick, and is then reused.
`elementalwands/hollow-crypt.json` records the built slots and each player's return point.
When the installed tiles change, every slot moves to untouched ground instead of being
rebuilt over the old layout. Inside the realm, every player teleport (spells, pearls,
commands, portals) must stay in the clearing and cannot cross into or out of the realm, except
the crypt's own entry and exit. A player who escapes the clearing is pulled back. Survival
players cannot break or place blocks there, and spells cause no world damage: Hollow Purple
passes through without erasing blocks, and explosions (such as Meteor) destroy no blocks and
start no fires. Temporary spell blocks still work and restore themselves as usual.

Graveyards generate in plains, meadow, savanna, snowy plains, forest, birch forest,
dark forest, taiga and swamp biomes (random spread 20/8 chunks, never within 4 chunks of a
Guardian church, so a yard's cleared air cannot cut into one). They're found with
`/locate structure elementalwands:hollow_graveyard`, and new chunks only.
Placement uses the custom structure type `elementalwands:hollow_graveyard`
(`world/HollowGraveyardStructure`) instead of vanilla jigsaw, which sampled one column and
stood the yard on a dirt slab at slopes and cliff edges. It surveys ground height on an
8-block grid over the footprint plus an 8-block rim, at up to 25 spots within 32 blocks of
the region's chunk. It refuses any spot where a sample is more than 3 blocks from the floor,
water or lava covers the footprint, or the biome is wrong, and seats the one-layer floor at
the median ground height. `beard_thin` levels the land to the floor, and cave gaps under it
are filled with dirt. A region with no suitable spot has no graveyard, and
`/place structure` fails on unsuitable ground. `/locate` can take several seconds. Entry is free: using
the headstone altar seals every living non-Creative player within 16 blocks into a fight in
a free slot. It records their return points first and blinds them for a moment on the way in.
Anyone already sealed into a fight or waiting for a slot to open stays with it, so a held
right-click or a second party member using the altar while the slot is laid out starts no
second fight. A player who dies or disconnects before the slot opens drops out of that fight,
and their return point is forgotten.
The boss rises on the circle three seconds after they arrive. The altar is recognised by its
block pattern in any rotation, not by the structure record, so `/place structure` copies work
too. A player-built copy of the pattern would work as well.

- **Death:** nothing drops in the realm; inventory and experience carry over to the respawn,
  also after quitting from the death screen or a restart before respawning. The crypt handles
  only a death in the realm or in a fight; a return point alone never pulls a later death
  elsewhere back to the graveyard.
  A fallen fighter respawns as a spectator above their own clearing. They cannot leave it or
  teleport away, and get their game mode back when the fight ends. Disconnecting also counts
  as falling; rejoining mid-fight puts the player back as a spectator.
- **Wipe:** when nobody is left standing (dead, disconnected or gone), the boss and its army
  vanish. Everyone is sent back to where they entered and the slot's layout is rebuilt. The
  group may use the headstone again straight away; a fight's end releases only the players in
  its own slot, so the lost fight never pulls them out of the new one.
- **Victory:** ten seconds after the boss dies, everyone is sent back. As with a wipe, that
  means everyone in the clearing, not only the sealed party: Creative players the headstone
  brought along and players who entered by command are released and messaged too. Anyone
  without a recorded return point (such as an operator who teleported in) is left alone. The first win at a
  graveyard places two chests beside the open grave. They hold the church's seeded treasure
  roll plus bones and never refill. Every player who has won at that graveyard may claim one
  personal spell book there (free Basic or Technique spell) by opening a chest; the
  receipt key is `graveyard:<site>`. The graveyard is otherwise unchanged.
- **Restart:** a fight is never resumed. On startup its slot is marked for rebuilding, and
  each player still in the realm goes home with their game mode restored when they join.

`elementalwands/hollow-crypt.json` records the built slots, return points, game modes to
restore, fights in progress and won graveyards.

Commands: `/ew crypt leave` is open to anyone in the realm and forfeits their place in a
fight. Operators also have `enter [slot]` and `summon`, which raises a fighting boss on the
circle; the non-Creative players already in the slot become its sealed party, and a summon
with none fights until reset. They also have `reset` (ends the slot's fight, clears
entities and restores the layout) and `status`. When `reset` or `summon` ends a fight, its
online watchers get their game mode back at the rim; an offline watcher keeps the record and
is sent home with their game mode on rejoining. The graveyard's gate chest is empty.

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
