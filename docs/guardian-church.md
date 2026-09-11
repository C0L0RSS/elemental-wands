# The Keeper's Sanctuary

## September 9 update — courtyard clearance and more frequent ruins

The statue, pedestal, socket and offering chest move six blocks away from the facade,
leaving ten clear blocks between the rear of the pedestal and the doorway. The church
building and saved site origin stay fixed; the socket is now at local Z -6. The hidden
ritual actor follows the new plinth. Locate/place messages print the actual socket.

Natural structure spacing is now 24 chunks with 14-chunk separation: one candidate per
384-by-384-block region, or about 2.78 times the previous candidate density. Biome and
terrain acceptance still apply; this is not a guarantee of a ruin every 384 blocks.
Only new terrain receives naturally generated ruins.

The placement filter compares 17 nearby positions using a median floor elevation and
selects the acceptable position with the least estimated earthwork. High-side cutting
costs more than low-side support. Surface samples every two blocks enforce the existing
six-block variation / three-block floor deviation limit, including a four-block margin.
Dry solid support remains required; large slopes and cliffs are rejected.

For new builds, the side courtyard retains natural terrain outside the central route
and plinth, using omitted columns in the vanilla template and matching runtime
exclusions. The level hall has short stone foundations. A smaller local blend joins
natural courtyard terraces to the path, trims at most three blocks of natural soil on
high sides, and adds stone stairs at a lower front approach. It skips water, trees,
containers, artificial surfaces and larger drops. Completed sites are not graded.
The offline preview shows the architecture on illustrative flat ground; it cannot
show the actual terrain integration in a generated Minecraft world.

Existing unfinished ruins upgrade on revisit once their footprint is loaded. A durable
receipt stores every offering-chest slot before the statue/socket/chest move. Replay
preserves item components, heart tokens, the original site identity, and the building.
Completed legacy churches are left alone; their original layouts remain packaged for
an already-committed restoration to finish after an upgrade. The migration does not
attempt to reconstruct terrain that an older generator already replaced.

Verification and installation: see [the placement report](guardian-placement-2026-09-09.md).

## Current death flow — arena spectators

Fallen players automatically respawn as spectators inside the arena. They can fly
and see the boss health bar but cannot fight or cast. The arena closes when all
fighters fall. At the end, players return to their recorded gathering points and
original game modes. Disconnect/restart recovery preserves this return. The old
outside waiting-ground requirement has been removed. See
[the spectator update](guardian-spectators-2026-09-09.md); older outside-waiting
notes below are historical.
Current installed SHA-256: `96236d784118311681c98818ee70d073d726cf46831df357f9df830d2ce661ed`. Restart Lunar.


## Latest follow-up — stone statue, sky arrival, and safe respawn

The visible dormant entity is superseded by a custom vanilla-block effigy. Players
use its pedestal socket and rise alone; the living guardian drops from the sky
after the floor is complete, lands, then awakens. Respawn returns wait until the
connection has switched to the new player, with loaded and validated ground.
Installed in Lunar; source/installed SHA-256: `e89c65b88612b5244cfa3dffb2485fce6b7154aafb6045ed4dfeba975c24a239`. Restart Lunar.
Read [the follow-up report](guardian-arrival-2026-09-09.md). Earlier dormant-entity
notes below are historical. The church architecture and loot are unchanged.


The survival encounter connects a naturally generated ruined church, a dormant
Guardian on its plinth and its Guardian Heart, the existing sky arena, and a permanent restoration.
Each church can be completed once. Failed or interrupted attempts can be retried.

## Find and test it

- `/ew guardian church place` validates and builds a ruined church 40 blocks in
  the +Z direction from the caller. It prints the socket coordinates. Use an open,
  undeveloped 33-by-53 clearing with no more than six blocks of terrain variation.
  It rejects containers, other churches, non-natural blocks, water, and living
  entities in the footprint before editing. The build is spread across ticks.
- `/ew guardian church locate` prints the nearest recorded church's socket
  coordinates and state. With none recorded, it starts an incremental natural search.
- `/locate structure elementalwands:guardian_church` starts an incremental search
  of naturally scheduled sites, including unexplored areas. The church-only tag
  form also uses this path. Coordinates arrive later; mobs and commands keep ticking.
- `/ew guardian church cancel` stops the current church search. Only one search
  runs at a time, with a two-minute timeout and cleanup on disconnect/server stop.
- Other vanilla structure commands retain their normal behavior. Church searches
  acknowledge scheduling with result 1; delayed coordinates are feedback, not a
  synchronous distance value for command blocks or `execute store`.
- These testing/search commands require operator permission. Using the heart in
  Survival/Adventure requires no command or permission.
- `/ew guardian arena stop` remains the emergency return command.

Natural churches generate in new Overworld chunks in plains, sunflower plains,
forest, birch forest, and meadow biomes. Structure spacing is 24 chunks with a
14-chunk separation; a biome match is required, so this is not a guaranteed church
at every grid position. Existing explored terrain does not receive retroactive
buildings. Tall carved piers and an asymmetric facade remnant make the ruin visible.

## Survival interaction

The chest beside the statue contains a glowing Guardian Heart and *The Keeper's
Promise*. Gather the group on the open courtyard, then right-click the dormant Guardian or the dark socket
in front of its plinth with that church's heart. Existing arena admission checks
still apply, including loaded surrounding chunks, supported feet, open sky,
Survival/Adventure, non-Peaceful, and sufficient build-height clearance.

Admission consumes the heart only after the arena accepts the party. There is one
active arena per server. Death/disconnect means waiting outside until the fight
ends; there is no re-entry. An abort or wipe resets the church and replenishes a
heart in the offering chest. A lost heart can be recalled by sneak-using the socket
with an empty main hand. Recall invalidates all earlier copies for that site.

Natural logs, leaves, vines, bamboo, roots, and bee nests can be broken inside a
ruin. Valid heart activation clears natural logs/leaves/vines from the Guardian's
spawn column and the gathered players' lift columns; it leaves stonework and block
entities intact, so real roofs/structures still require an open gathering position.
This handles trees that worldgen places after the sanctuary structure.

The ward prevents changing the ruined church's other reserved blocks, including mining,
ordinary world block mutations, piston movement, and explosion block destruction.
Containers other than the offering chest remain inaccessible until restoration.
The reward chests contain nothing before victory. The ward ends upon restoration,
so the completed building can subsequently be used and modified; its permanent
socket remains unbreakable and reports completion.

A real Guardian death during its arena combat stage commits victory. Removal,
Peaceful, an operator abort, and a party wipe do not grant victory. Restoration
runs beneath the arena while it descends. On return the bells sound and the room
contains two treasure chests. Each chest rolls independently: 50% modest, 35% better, 15% rich. Modest chests
have 1–4 iron ingots and a 45% book chance; better chests have 3–6 iron, 1–3 gold,
a 30% chance of one diamond, a 65% book chance and 15% iron-armor chance. Rich
chests have 4–8 iron, 2–5 gold, 1–2 diamonds, a book and 45% iron-armor chance.
Armor is a random iron piece. Every chest also has 2–5 small stacks of mundane
supplies such as sticks, string, bones, paper, feathers, seeds and rotten flesh.
Everything is scattered among shuffled inventory slots, with empty spaces.

Books choose among the server's registered enchantments (including treasure and
curse enchantments), with a level from I to the lower of II or the enchantment's
natural maximum. They contain actual stored enchantments usable at an anvil.
World seed, sanctuary coordinates and chest side determine each repeatable roll;
recovery, recall and restarts cannot reroll it. RESTORED sites are never refilled.
Magical items are reserved for a future content pass.
Rewards and fight difficulty are initial values for playtesting.

## Visual direction

The user rejected the initial simple twin-tower church, its crosses, and the mostly
intact ruin. The current sanctuary uses no crosses or real-world religious symbols.
Its culture is fictional; a broken-ring emblem, faceted pinnacles, layered pointed
arches, and carved buttresses establish the restored identity. The user then asked
for a maximal ornate, colorful restoration: the current version has quartz masonry
and columns, cyan/blue/violet/magenta/amber glass, teal roof ribs and skylights, gold
accents, ring lantern crowns, a radial glass rear window, and lapis/gold floor inlays.
The ruin has no roof or glazing: isolated pillars, torn wall remnants, broken arches,
fallen column fragments, moss, and grass reclaiming the floor. Foundations below
the terrain are dirt, not the earlier raised rectangular stone plinth. The original
preview is retained only as historical art under `art/guardian_church/v1/`.

## Authoring and preview

`tools/build_guardian_church.py` owns both layouts. It writes the compressed vanilla
jigsaw template, runtime ruined/restored block coordinate JSON, and preview geometry.
`python3 tools/build_guardian_church.py --check` checks for drift.

`python3 tools/preview_guardian_church.py` creates the standalone WebGL comparison,
comparison PNG, and interior cutaway in `art/guardian_church/`. It reads the same
coordinates and vanilla textures from the local Minecraft client JAR. The HTML has
no CDN dependency, and supports synchronized orbit/zoom and preset views. Slabs,
wall blocks, chests, and end rods use full block envelopes in the preview; flowers
are omitted; their final
Minecraft models and lighting require a game playtest.

## Persistence and implementation

`GuardianSocketBlock` / `GuardianSocketEntity` anchor naturally generated sites.
Their horizontal facing rotates with the jigsaw piece; all runtime coordinate
transforms use that facing. Native generation uses a depth-one single pool piece and a -3 base height offset.
A sanctuary-specific jigsaw filter samples the entire rotated footprint plus a
margin against unmodified terrain. It accepts surface variation up to six blocks
with no point more than three blocks from the floor, requires solid dry support,
and tries nearby positions before rejecting the site. Noise-terrain extrusion is
disabled. Accepted pieces are reused rather than regenerated with changed randomness.
A zero-depth jigsaw does not emit its initial piece in this Minecraft version.

`GuardianChurchManager` records sites in the world's
`elementalwands/guardian-churches.json`, using atomic file replacement and a forced
write before admission or victory changes. Phases are BUILDING, RUINED, ACTIVE,
RESTORING, and RESTORED. Restarts abort ACTIVE sites and replay unfinished builds
or committed restorations. Restoration writes at most 1,024 positions per tick,
preserves the offering inventory/socket, and saves the world before retiring the
restoration state. Reward inventories are inaccessible during this operation.
Collected treasure is not recreated when a RESTORED church reloads.

The manager records and releases only chunk force flags it added. Arena recovery
continues to own its existing separate journal and player-return lifecycle.
Church integration adds an actual-combat death hook and an arena-completion hook;
the approved floor, architecture, carrier motion, and Guardian attacks are unchanged.

## Verification

Optional fixtures use disposable local server worlds, copied accepted test EULA,
loopback-only ephemeral ports, and no production player data:

```sh
./gradlew -I tools/guardian_church_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle -PchurchRecovery runServer --args nogui
./gradlew -I tools/guardian_church_worldgen_smoke.init.gradle runServer --args nogui
```

The lifecycle fixture checks the actual built socket and book, heart recall and
invalidation, sealed admission, abort/retry, real boss death, restoration, reward
availability, and permanent completion. The recovery run verifies that completion
and previously collected diamonds survive restart without duplication. The normal
world fixture exercises vanilla locate, actual structure generation, the rotated
socket and stocked offering chest. Test entrypoints never enter the release JAR.

These are server/asset tests and an offline build preview. They do not substitute
for a human Lunar test of the approach, statue interaction, restoration reveal,
client lighting, multiplayer balance, or natural placement across many seeds.

The latest visual feedback approved the ornate shape, details, stained glass and teal
roof, then requested ordinary stone instead of most quartz and all gold. The final
palette replaces quartz with stone bricks/polished andesite/smooth stone, gold with
carved stone, lapis with blue terracotta, and copper inlays/ornaments with colored
terracotta. Geometry and glass colors are preserved; rewards remain in the chests.

## Terrain integration

Unfinished ruins now blend their flat footprint into nearby lower soil with a broad,
irregular slope (roughly 8–14 blocks wide). This runs for new sites and for existing
RUINED sites on revisit, after their surrounding chunks are loaded. The process
works outside the authored footprint, does not move the sanctuary, and skips trees,
water, roads, containers and non-soil surfaces. Very large drops over 12 blocks are
left alone rather than creating enormous artificial embankments. Higher surrounding
terrain is preserved. Completed player-modified sites are not automatically graded.

Absolute target heights are journaled before editing and replayed in batches of
48 columns per tick. A saved plan is idempotent across restarts; terrain is saved
before retiring it. Rituals can proceed while a remaining terrain plan is paused;
blending resumes only when no arena is active. Architecture, loot and party rules
are unchanged. This is a server-side terrain pass and is not shown by the standalone
building preview, which has no surrounding world terrain.

## Dormant keeper and audit follow-up

The approved Guardian itself now stands passively and invulnerably on the former
statue plinth. A valid heart awakens that same entity. A failed attempt returns it;
victory removes it. Existing unfinished sites migrate only the old block statue's
known masonry coordinates, retaining the plinth and offering chest. Saved sites
reuse the same keeper UUID with a short load grace period before replacement.

At admission, cinematic seats start at least ten blocks from the boss and three
blocks from one another; their vertical columns are validated before starting.
The ritual clears natural growth from the original and planned lift columns.
Temporary Stone/Wind/Space effects are ended on return/elimination, late drops and
XP are collected during descent, and failed block writes are not recorded as
successful temporary placements. See `guardian-audit-2026-09-09.md` for scope,
verification and limits, including the unchanged terrain at old cliff sites.
