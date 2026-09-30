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
stands in the courtyard: the ritual takes the group to the Shattered Nave, where
the Guardian drops onto the effigy seat.

The ritual seals every living, non-spectator player within 20 blocks of the socket
into a fight, records where each stood, and only then consumes the heart; Creative
players come along but are not sealed in. Peaceful refuses it. Nothing at the church
is cleared or built for the fight. Recall a lost heart by sneaking and using the
socket with an empty main hand; old copies are invalidated. A wipe, an abort or a
restart resets the offering with a fresh heart. Each church runs its own fight, and
up to eight run at once, one per nave slot.

The visual direction is ornate fictional stone architecture, colored glass and a
teal roof, with a broken-ring emblem. Preserve the approved geometry and stone
palette. Do not restore real-world religious symbols, gold/quartz masonry, or a
smooth non-Minecraft statue from earlier concepts.

## The Shattered Nave

The Guardian is fought in its own dimension, `elementalwands:shattered_nave`: an endless
ruined cathedral. The fight floor is a flat 128-block square (blocks −64 to 63 around the
centre) in the crossing of a grid of pillars about 20 blocks thick, 44 apart and 84 tall
to their capitals, whose rib vaults and a broken-ring keystone fade into darkness overhead.
Many pillars are snapped, bitten, stripped of an outer column or collapsed, with fallen drums
across the aisles and masonry hanging in the air, all outside the fight floor. An invisible
barrier wall and lid 40 blocks up enclose the floor. The dimension borrows the Nether's thick
fog so it has no sky; `NaveFogMixin` sets the haze to end about 150 blocks out (or sooner
with a shorter render distance), so the grid reads as endless. Ambient light is 0.3, and the
floor is lit by sea-lantern inlays in the seat, the border band and the glowing cracks.
Coloured light shafts fall from unseen windows; Minecraft light has no colour, so
`client/NaveShaftEffects` draws them (additive, fading with distance) and invisible light
blocks light their floor pools. Owners are `arena/ShatteredNave` (geometry) and
`arena/GuardianArenaManager` (fights).

`art/guardian_nave/build_layout.py` authors the hall. Its default run writes the browser
preview to `.local-previews/guardian-nave/` (launch entry `guardian-nave-preview`, port 8370).
`--install` writes the hall as 121 structure tiles of 48×48 plus the generated light shafts
(`arena/ShatteredNaveShafts.java`), and `--check` detects drift. The half-width, ceiling and
tile grid are duplicated in `ShatteredNave` and must stay in step. The builder only draws the
hall out to about 220 blocks; the fog hides the edge, and the dimension's flat floor continues.

A slot is laid out the first time it is used, one tile per server tick (about six seconds),
and is then reused. When the installed tiles change, every slot moves to untouched ground.
The group arrives side by side south of the seat, facing it. Two seconds later the Guardian
appears kneeling on the seat as a stone statue and its intro cinematic plays (see below); the
fight starts when it ends. Until then nobody in the fight casts or takes damage, and the
Guardian cannot be hurt. Only the sealed players still standing can damage it, and it only
targets them.

### The intro: "the effigy wakes"

`entity/GuardianIntro` runs a 16.1-second scene (322 ticks) on the server's clock; each watcher's
client (`client/GuardianIntroClient`) flies its camera along the same timeline, so a party sees
it together. The camera frames the caller's outstretched arm with the heart on it (the player who
offered it; `IntroHeartArmMixin` raises their main arm and empties their hands). The heart
(`IntroHeartEntity`, a scripted path both sides compute from world time) trembles harder and
harder, floats up and flies down the nave, the camera chasing it until it strikes the kneeling
Guardian's core. Its eyes and veins stutter alight, lightning crawls over the stone and jumps to
the floor (`GuardianAwakeningVisual`), and it lifts its head, rises, spreads its arms and slams
its fists together; the slam sends a ring of sparks across the floor and the title appears.
The name slides up on a padded black card, matching the Necromancer's reveal: visible over
ticks 222–298 (3.8 seconds), with half-second entrance and exit fades. The camera returns over
ticks 298–322, after the card finishes. The rise and slam retain their original timing; the
extra time holds the final standing pose for the title.

Watchers are held still, unhurt and unable to cast. Anyone can hold Sneak to skip; the fight
starts early only once every watcher has. It plays every fight. An operator control that stops
the Guardian mid-scene releases everyone without starting the fight, and a restart comes back
fighting. `/ew guardian intro` replays it on the nearest Guardian anywhere, with the operator
holding the heart. The Guardian's clip (`animation.fractured_guardian.intro`) is authored by
`art/fractured_guardian/intro/build_intro.py` on the approved rig, sharing the scene's beats; it
also measures the points the scene aims at (`intro-points.json`), which the contract test checks
against `GuardianIntro`. Rebuild with that script, then `python3 tools/prepare_guardian_assets.py`.
The shared camera, HUD and input hooks (`IntroCameraMixin` and friends) ask `BossIntroCamera`,
which defers to whichever boss's scene is playing.

Every player teleport (spells, pearls, commands, portals) stays on the walled floor and cannot
cross into or out of the nave, except the nave's own moves; a player who escapes is pulled back.
A Guardian that strays off the floor or falls beneath it returns to the seat, but its high leap
is left alone. Survival players cannot break or place blocks. The floor and everything outside
the walled volume are protected against any block write: spells may place and clear their own
blocks above the floor, and temporary spell blocks may cover the floor and restore it, but never
open a hole in it or reach outside the walls. Explosions and Hollow Purple destroy no blocks.

- **Death:** nothing drops in the nave; inventory and experience carry over to the respawn.
  A fallen fighter respawns as a spectator above the floor, sees the Guardian's health bar,
  cannot leave the slot, and gets their game mode back when the fight ends. Disconnecting
  also counts as falling; rejoining mid-fight puts the player back as a spectator.
- **Wipe:** when nobody is left standing, the Guardian vanishes and everyone is sent back to
  where they stood at the church three seconds later. The church offers a fresh heart.
- **Victory:** the Guardian's death commits the win before any block changes, then the church
  is restored in the Overworld while the party waits ten seconds in the nave, so they return
  to the finished church. Seeded treasure cannot be rerolled by recall/restart and collected
  chests never refill. Each enrolled player may claim one personal spell book per site from
  its restored reward chest; require inventory space before recording the receipt. See
  [progression](progression-and-controls.md). Removal, Peaceful, an operator reset and a
  wipe are not victories.
- **Restart:** a fight is never resumed. Players still in the nave go home with their game
  mode restored when they join, and the church offers its heart again.

Worlds from the retired sky arena are upgraded on start: players it still owed a return or a
game mode are sent home when they next join, its record is renamed
`guardian-arena.retired.json`, and a hidden keeper left at a church is discarded.

## Persistence and terrain constraints

`GuardianChurchManager` journals sites in `elementalwands/guardian-churches.json`.
`GuardianArenaManager` independently journals `elementalwands/guardian-nave.json`: built
slots, return points, game modes to restore and fights in progress, beneath the world save.

- Commit return records before admission, and victory before restoration or rewards.
- Replay interrupted builds/restorations idempotently. Restart aborts a running
  fight safely; committed victory must not become a fresh loot roll.
- Save world/player changes before retiring their recovery records.
- Release only chunk force flags owned by the church; preserve pre-existing ones.
- Preserve temporary-block ownership, equipment recovery, and offline returns.
- Use site accessors such as `Site.socket()` and `Site.offering()` rather than
  assuming that the saved building anchor is the current interaction position.
  A site's fight is keyed by `Site.key()` (its anchor), not by the heart's token.

Unfinished old sites upgrade with replayable inventory/layout receipts, preserving
heart identity and offering contents. Completed player-modified sites are left alone.
Terrain blending skips water, trees, containers, roads and artificial surfaces;
saved absolute target heights avoid repeatedly expanding earthwork on reload.

## Hollow Crypt realm

The Hollow Necromancer is fought in its own dimension, `elementalwands:hollow_crypt`: a flat
world with a fixed night, no weather or skylight, Nether-style thick fog and drifting ash.
Ambient light is 0.2 and the fog is a dim grey-teal, so unlit ground stays a readable night.
Each fight slot is a clearing 88 blocks across. Ten soul-fire braziers ring its rim, dark until
the Necromancer's intro slam lights them (see [Necromancer combat](necromancer-combat.md#intro-cinematic)), and seven
soul lanterns on existing cover (one per grave-marker group, one per stump) light the interior.
The intro temporarily dims the ambient fill and fog, then restores this lighting as the
soul-fire wave ignites the braziers. It sits inside a dead forest of about 200
cosmetic trees that reaches 124 blocks out, with some giants whose high boughs arch over the rim.
An invisible barrier shell and lid keep players and the boss within 44 blocks of the centre and
30 blocks above the ground. The one exception is the boss's siege perch: eight bough tops above
the lid, seen from the clearing but never reached (see [Necromancer combat](necromancer-combat.md#sieges)). Owners are under `src/main/java/com/anton/elementalwands/crypt/`.

`art/hollow_crypt/build_layout.py` authors the clearing and forest. Its default run writes
the browser preview to `.local-previews/hollow-crypt/` (launch entry `hollow-crypt-preview`,
port 8360), which also shows the overworld graveyard with its animated veil and motes.
`--install` writes the realm as 48×48 structure tiles plus `hollow_graveyard.nbt` and the
generated siege perches (`crypt/HollowCryptPerches.java`). It also writes the mausoleum's block
models, block states, veil/void/glow textures, wither and dragon immunity tags and
`crypt/MausoleumModel.java` (the doorway pieces' cells and collision boxes). `--check`
detects drift in all of them. The radius, ceiling and tile grid are duplicated in
`HollowCryptRealm` and must stay in step. The sealed-stone list (`SEALED`) is duplicated in
`ModBlocks.SEALED`, in the same order.

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
`/place structure` fails on unsuitable ground. `/locate` can take several seconds.

The graveyard generates in the last feature step, after trees, so its template clears any
tree that grew in the yard. Its post-placement (`world/GraveyardBlight`) then kills the woods
around it: within 18 blocks of the footprint every tree is bare and the ground is scarred with
coarse dirt, podzol, dry grass, dead bushes and leaf litter; out to 34 blocks the canopy thins
and the ground recovers. The edge wanders by a few blocks. Every choice hashes the block
position, and each chunk also treats an 8-block margin of its neighbours, so the result doesn't
depend on chunk order. To reach the blight, the structure's box extends 40 blocks past the
footprint; the terrain beard still follows the piece alone. `/place structure` therefore needs
the chunks around the yard loaded too.

A gothic mausoleum stands at the north end of the yard, with the path from the lychgate leading
to its steps. It is solid and unbreakable: its stone is `sealed_*` look-alikes of the vanilla
blocks (hardness −1, immovable by pistons, immune to withers and the dragon). Its carved
pale-marble doorway (`mausoleum_arch`, 46 pieces of one sliced model) frames the veil
(`mausoleum_veil`): a slowly turning dark vortex with no collision. Its windows
(`mausoleum_window`) show the same dark void, set back in the wall. The soul lanterns, the
braziers' soul campfires, the moss and the spire's skull are ordinary blocks. Faint motes drift into the veil (client-side, from its
random display ticks), and aiming at the doorway from up to 12 blocks shows
"Walk through the veil to challenge the Hollow Necromancer" and "Everyone within 16 blocks is
drawn in" (`client/MausoleumHint`).

Entry is free: walking into the veil seals every living non-Creative player within 16 blocks
into a fight in a free slot. It records their return points first and blinds them for a moment
on the way in. Anyone taken from the doorway (within 3.5 blocks of the veil's bottom-centre
cell) returns to the court six blocks in front of it, facing the door, so they don't walk
straight back in. Anyone already sealed into a fight or waiting for a slot to open stays with
it, so standing in the veil or a second party member walking in while the slot is laid out
starts no second fight. A refusal (such as a full crypt) repeats only after the player has
stepped out of the veil for two seconds. A player who dies or disconnects before the slot opens
drops out of that fight, and their return point is forgotten. The boss rises on the circle
three seconds after they arrive. The graveyard is identified by the veil's bottom-centre cell,
not by the structure record, so `/place structure` copies work too.

Graveyards generated before the mausoleum keep their headstone altar: right-clicking it
starts the same ritual. It is recognised by its block pattern in any rotation, and its
rewards go beside its open grave.

- **Death:** nothing drops in the realm; inventory and experience carry over to the respawn,
  also after quitting from the death screen or a restart before respawning. The crypt handles
  only a death in the realm or in a fight; a return point alone never pulls a later death
  elsewhere back to the graveyard.
  A fallen fighter respawns as a spectator above their own clearing. They cannot leave it or
  teleport away, and get their game mode back when the fight ends. Disconnecting also counts
  as falling; rejoining mid-fight puts the player back as a spectator.
- **Wipe:** when nobody is left standing (dead, disconnected or gone), the boss and its army
  vanish. Everyone is sent back to where they entered and the slot's layout is rebuilt. The
  group may walk into the veil again straight away; a fight's end releases only the players in
  its own slot, so the lost fight never pulls them out of the new one.
- **Victory:** ten seconds after the boss dies, everyone is sent back. As with a wipe, that
  means everyone in the clearing, not only the sealed party: Creative players the ritual
  brought along and players who entered by command are released and messaged too. Anyone
  without a recorded return point (such as an operator who teleported in) is left alone. The
  first win at a graveyard places two chests on the ground either side of the mausoleum steps,
  facing them. They hold the church's seeded treasure roll plus bones and never refill. Every
  player who has won at that graveyard may claim one personal spell book there (free Basic or
  Technique spell) by opening a chest; the receipt key is `graveyard:<site>`. The graveyard is
  otherwise unchanged.
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
`/locate structure elementalwands:guardian_church`, and `/ew nave enter [slot]|summon|reset|status`
(`summon` drops a Guardian for the survival players already in the slot; `reset` ends the slot's
fight, clears its entities and lays it out again). `/ew nave leave` is open to anyone in the nave
and returns them to where they entered. Placement mutates the world, so use a suitable
disposable area.
See [testing](testing.md) for lifecycle, restart, worldgen, and client fixtures.
