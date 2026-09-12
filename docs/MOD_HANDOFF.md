Latest approved combat balance (September 12): Fire primary/Pyre deal 6; meteor
uses a capped, distance/cover-aware explosion without falling-block damage. Guardian
fan becomes three quick aimed bursts, and a ready laser has protected priority after
two other attacks within its existing range. Read `fire-guardian-balance-2026-09-12.md`
for exact tuning, validation and installation status. Human playtest is pending.

Installed/source SHA-256: `135618b30c832e0251a2cae0b434401571ed0352b2086352c152bb1b93b717a0`.
All build, server and client checks passed. Restart Lunar. Earlier release hashes below are historical.

# September 11 approved Nature balance

## Latest — approved Fire redesign, September 12

The approved workshop is implemented and installed. Fire now has original four-frame
art, an eight-block round all-fire meteor with its full flame shell, a traveling
Pyre wall, Pyre-only short lingering flames, and vanilla-looking primary/ultimate
ground fire. Existing combat and first-person clearance remain. The clean build,
asset/package checks, isolated real-server wall/cleanup checks and loaded-client
mesh checks passed. Human playtest is pending. See
[the Fire report](fire-redesign-2026-09-12.md) for details and backup.

Latest source/installed SHA-256: `308c31471783425146f3a271992086e74082e0ba0d49c3d9e153bb2ced6c803c`.
The current contract is 45 Fire PNGs / 266 spell-and-shared PNGs / 40 particle definitions.
Earlier build hashes and asset counts below are historical.


Flower-focused balance is implemented and installed: seeds deal flat 3 damage
without Entangle; flower/secondary contact ramps 3–5 damage with one shared
caster/target tick each second. Charge awards are player-owned (+1 seed and +3
thorns per second at most), and the ultimate burst gives no charge. The tree
provides Regeneration II; Fire remains I. See `docs/nature-balance-2026-09-11.md`.

Clean build, asset/package checks, real-server balance tests and Guardian arena
checks passed. The tree actually restored six hearts over 15 seconds in the
fixture; human fight balance is pending. Lunar source/install SHA-256 match:
`3fea911b49cc56325faf07cacc0f24651c82682aad53281f2618a8c173b4e490`.
Restart Lunar to load. Earlier hashes below are historical.

# Elemental Wands — session handoff

Latest approved behavior (September 11): standing slam stays 360 degrees for
multiplayer pressure; Hollow Purple commits for 70 ticks with free aim, locked
movement/item use, no voluntary cancellation or refund. Build, expanded audit
server fixture and assets pass. Installed/source SHA-256:
`0214a7540cbacf9ee0c429002c41af58b1609a65a6eb337338376b98fca4dbac`.
See [the follow-up](audit-fixes-2026-09-11.md#approved-hollow-purple-commitment).

Latest audit follow-up (September 11): [fixes and verification](audit-fixes-2026-09-11.md).
Meteor/temporary-block restoration, persistent gear recovery, rejected ritual
foliage preservation and cleanup fixes passed three real-server fixtures and the
build/asset checks. Installed SHA-256 is
`d4a128ca40716562cbaa3dc37d1f591b970591c6aee615a390b5206774637a1f`.
That build was superseded by the committed Hollow Purple follow-up in the same
report. The user approved the 360-degree standing slam; Hollow Purple now commits
without a refund or slot-switch cancellation. Human playtest is pending.

Latest Nature update: [approved redesign implementation](nature-redesign-2026-09-10.md).
Custom flowers, roots, rafts, a 3D seed, attached angular Entangle vines and the
nine-block oak ultimate are implemented. Tree shape can be revised after playtesting.
See the report for current verification and installation.
Current build and installed SHA-256: `77f30d95be560f25eacaff6fafb77eb46db0058557b1c86be844e2401b3c66b0`.
Actual client views and server checks passed; human Nature playtest is pending.

Latest spell visibility: [first-person clearance](spell-view-clearance-2026-09-10.md).
Fire/Space/Wind casting bursts now start forward/down; custom particles across all
affinities and Fire/Wind/Nature projectile visuals fade near the first-person
camera. Combat paths/ranges and third-person opacity are preserved. Required
build/assets/package checks passed and the update is installed; human playtest pending.

Latest combat update: [Wind/Guardian pressure](wind-guardian-pressure-2026-09-10.md).
Wind is now a 12-block three-blade fan with five-second dash recharge. The Guardian
resists stacked pushes, pursues more actively and has committed floating-stone
volleys for ground/air pressure, including mixed phase-two sequences. Laser and
earned guard openings are preserved. Read the report for checks/install status;
fresh human balance/visual feedback is pending.
Current verified build/installed SHA-256: `6e101d6bebcd6fe7f3a9b4a2b740c03874d7af0edcd9c4d16b5382a3fd9c04e5`. Restart Lunar.


Latest balance: Stone Wall absorbs one Guardian hit, then crumbles. The breaking
beam/wave stays blocked for its full duration; follow-up attacks are separate.
Real-server attack/ownership/terrain checks passed and build installed; user playtest
pending. See [wall balance report](guardian-stone-wall-2026-09-10.md).

Latest presentation update: shared wooden/rune wand HUD; Stone reserve is text-free
and anchored directly above the slots. Build/assets/preview checked and installed.
See [HUD report](wand-hud-2026-09-10.md); user approved the design.

Latest Stone update: Gathered Mass replaces the ground-spike primary with an
18-piece custom overhead rock cluster. Ground clicks gather; aimed clicks throw;
empty shots recover in 1.5s and charged throws in 2.5s. Weight, damage erosion,
water flight and a brief heavy-hit stagger are implemented. Secondary/ultimate
behavior is unchanged. The user approved the new ability/model. The follow-up
ignores low vegetation in aim, travel and overlap collision while retaining solid
cover; 40 plant cases, six cover controls and the full Stone server suite passed.
Human confirmation of the vegetation fix is pending. Read [the Stone report](stone-gathered-mass-2026-09-10.md).
Earlier Stone/Wall build SHA-256: `52912ac1ff76bc56fa393c5acdf7ca1bc4435a9ce17e92e027fd5f0632eb3879` (superseded by the Wind/Guardian update above).

Latest fight feedback/fix: user completed and approved the two-phase encounter.
Flowers and Stone primary spikes were unintended shockwave cover; the wave now
passes through them and clears contacted Nature growth. Stone walls and the actual
Nature ultimate tree remain defenses. Read [the cover report](guardian-wave-cover-2026-09-09.md)
for verification/install status. Confirmation of this fix is pending.
Earlier cover-fix build: `ef7c2b392c19e7444dae1cef99fdad98cd5f4cbc3de914598cb9e07307ac01cf` (superseded by the current Stone build above).


Latest church search fix: see [guardian-locate-2026-09-09.md](guardian-locate-2026-09-09.md).
The vanilla locate command caused a verified 113-second server stall. Church
searches now run incrementally with cancel/timeout cleanup, and placement skips
redundant terrain work. The report supersedes earlier installation status below.

Latest church placement pass: see [guardian-placement-2026-09-09.md](guardian-placement-2026-09-09.md).
The ritual group is six blocks farther from the door, candidate spacing is 24 chunks,
and new side courtyards preserve natural terrain with local slope transitions. Existing
unfinished sites have a journaled inventory-preserving statue upgrade. Use the report
for current validation/install status; older installation hashes below are historical.


## Latest — unstable Guardian phase two, September 9

Read [the phase report](guardian-phases-2026-09-09.md) first. Phase two triggers at
60% health after the current attack/earned guard opening, with unstable cyan magic
and a new full-body eruption. Physical attacks gain wider jumpable waves, three
slams/rocks, and a leap follow-up. Laser and guard/health tuning are preserved.
Preview port 8330 includes the added clips. Build/runtime checks passed; human
Lunar balance and visual confirmation remain open. Earlier sections are historical.
Current verified build/installed SHA-256: `c32b47e990184c6a0fe1ff267c85c1c5473ee2e94afeec2433a5b8b93799089a`. Restart Lunar to load it.


## Latest guard appearance

The first thin cyan crack network was rejected. The new materials deepen existing
stone joints and carved grooves. The user liked the revised cracks and stagger, then requested a backward lean.
Guard break now settles into a strong backward arch with bent knees, head tipped
back and slack arms, exposing the suspended core; it trembles, then returns upright. The updated preview is open at port 8330. See
[the visual follow-up](guardian-guard-visuals-2026-09-09.md). Gameplay values unchanged.

## Latest combat balance — stone guard

Current installed SHA-256: `c32b47e990184c6a0fe1ff267c85c1c5473ee2e94afeec2433a5b8b93799089a`. Restart Lunar.

Separate guard, visible cracks, opening ribs and exposed shaking core are implemented.
Main health scales from 600 solo to 2,400 with five players; guard scales from 120
to 480. Guarded hits still chip health; an earned seven-second exposure amplifies
damage. Ultimates stay usable, and teammates no longer swallow each other's
simultaneous hits through shared hurt immunity. Only one boss bar is shown.
See [the guard report](guardian-guard-2026-09-09.md) for initial tuning, actual meteor
results, preview and validation. Older health numbers below are historical.

## Latest follow-up — Nature boss interactions and arena spell placement

See [the Nature/arena spell report](guardian-nature-2026-09-09.md) for the approved
rules, implementation, and tests. Guardian attacks remain committed; Nature earns
limited extra recovery and local impacts clear growth. Fire coals now work on the
protected floor, Nature seedlings survive it, and regeneration actually heals.
The earlier installed hashes below are historical; use the verification note in
the new report for this build. Human Lunar appearance/balance checks remain open.

## Current death flow — arena spectators

Fallen players automatically respawn as spectators inside the arena. They can fly
and see the boss health bar but cannot fight or cast. The arena closes when all
fighters fall. At the end, players return to their recorded gathering points and
original game modes. Disconnect/restart recovery preserves this return. The old
outside waiting-ground requirement has been removed. See
[the spectator update](guardian-spectators-2026-09-09.md); older outside-waiting
notes below are historical.
Spectator-only build (superseded) SHA-256: `96236d784118311681c98818ee70d073d726cf46831df357f9df830d2ce661ed`.


## Latest follow-up — stone statue, sky arrival, and safe respawn

The visible dormant entity is superseded by a custom vanilla-block effigy. Players
use its pedestal socket and rise alone; the living guardian drops from the sky
after the floor is complete, lands, then awakens. Respawn returns wait until the
connection has switched to the new player, with loaded and validated ground.
Installed in Lunar; source/installed SHA-256: `e89c65b88612b5244cfa3dffb2485fce6b7154aafb6045ed4dfeba975c24a239`. Restart Lunar.
Read [the follow-up report](guardian-arrival-2026-09-09.md). Earlier dormant-entity
notes below are historical. The church architecture and loot are unchanged.


Updated September 8, 2026, at the user's requested stopping point. Read
[AGENT.md](../AGENT.md) for authoritative architecture, build, and deployment rules;
[the Guardian guide](fractured-guardian.md) has combat details and draft lore.
This replaces the obsolete crystal/Ice/per-element-wand handoff.

## Latest installed follow-up — dormant keeper and full audit, September 9

The user reported cliff-shaped terrain and a Guardian appearing inside their camera,
suggested replacing the statue with the Guardian, then authorized an unattended
deep bug audit. Work is complete and installed. Read
[guardian-audit-2026-09-09.md](guardian-audit-2026-09-09.md) for findings and limits.

- Actual dormant Guardian now occupies the former statue plinth. A valid heart can
  be used on that entity or its socket; the same UUID enters the fight. Existing
  unfinished sites migrate their old block statue, preserving the pedestal/chest.
- Lift seats start 10 blocks from the boss and 3 blocks apart. New columns are
  validated and natural vegetation can be cleared before admission.
- New worldgen samples raw terrain under the rotated footprint plus a margin,
  rejects cliffs/water/thin shelves, tries nearby flat patches, and disables the
  old terrain-noise extrusion. Old cliff-shaped terrain is not automatically removed.
- Audit fixes end Stone dome and Space charge effects on return/elimination, collect
  late drops and XP throughout descent, track only successful temporary placements,
  and consume the accepted heart before equipment restoration moves inventory stacks.
- The approved sanctuary shape/colors/common-stone palette and randomized loot remain.

Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-guardian-audit-20260909-131341.jar`
Source/installed SHA-256: `90c1ee6233c9d58750e98c1178135121cce0297e11de241a90ab91cbcbef6498`

Passed: clean build, asset and church-template drift checks, JAR integrity, client
floor/socket/heart model checks, actual damage from all five attacks (8/8/6/6/16),
multiplayer arena/death/respawn/return tests, active spell cleanup and late XP/items,
interrupted-fight recovery, dormant keeper identity after reload, accepted natural
placement plus a known-cliff rejection, direct keeper interaction, protected write
accounting, and existing randomized loot checks. Evidence is retained at
`/private/tmp/guardian-audit-verification-20260909/`.

The flat fixtures now disable vanilla structure generation to keep their controlled
terrain free of incidental villages. The natural-world fixture retains real worldgen.
The combat fixture waits out ordinary join protection and acknowledges teleportation;
an initial protected-target failure was a fixture setup issue, not a beam defect.
One automatic approval attempt timed out; the allowed retry succeeded. No actions
remain blocked. No production save was opened or edited by these tests.

Next human step: restart Lunar, revisit an unfinished church for the keeper/camera
changes, and use a newly generated sanctuary for the terrain-placement fix.

## Latest installed follow-up — terrain and tree obstruction, September 9

The user found a natural ruin with a sharp elevated dirt-platform edge, then could
not summon because trees occupied the courtyard and were protected by the ward.
Both fixes are now installed. A persisted, bounded terrain pass blends nearby lower
soil into the perimeter (roughly 8–14 blocks), including existing unfinished sites.
It skips water, trees, non-soil structures and very large drops; completed sites
are untouched. The authored structure is not moved or redesigned.

Logs/leaves/vines and other natural tree growth can now be broken inside the ward.
Valid heart activation clears vegetation from the boss and enrolled players' lift
shafts; roofs, sanctuary masonry and block entities remain intact. Stand in the
open courtyard. Old invalidated heart copies still cannot activate the ritual.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-terrain-tree-fix-20260909-115054.jar`
- Source/installed SHA-256: `a37dec6705a28679b9a6f06d207fad2b4d257ed19e75433d5b7d74cb3eddc2b3`
- Passed: build/regressions, whitespace/JAR checks, a reproduced four-block ledge
  graded into a walkable slope, preservation of a chest/road/tree, stable replay,
  manual tree breaking, successful ritual with injected spawn-tree/overhead leaves,
  full arena lifecycle/random loot, and terrain completion on a naturally generated
  rotated ruin. Human visual/playtest confirmation is still needed.
- Evidence: `/private/tmp/guardian-terrain-tree-verification-20260909/`.
- Next: restart Lunar, return to the same unfinished ruin, let nearby chunks load,
  and use its valid heart on the socket again. No new world or church is required.

## Latest installed follow-up — randomized rewards, September 9

The fixed reward list below is superseded. Each chest now rolls independently:
50% modest, 35% better, 15% rich, with 2–5 small stacks of mundane supplies and
randomized occupied slots. Rich finds can include diamonds, gold and iron ingots,
a random iron armor piece and an enchanted book; modest finds may just contain
a little iron and a book alongside clutter. Books use varied registered enchantments
(including treasure/curses), capped at II and the enchantment's natural maximum.
World seed/site position/chest side fix the roll, so reloads cannot reroll it.
Completed and collected chests do not refill. Magical items remain deferred.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-random-church-loot-20260909-113325.jar`
- Source/installed SHA-256: `7bb1151a78f95c72f1a7c87096ad8b11b5f35bcd9dadfb2cbe627e6ae7dba672`
- Passed: build/regressions, whitespace and JAR checks; 1,024 loot rolls covering
  all 27 slots and 42 enchantments with valid level caps and repeatability; actual
  restored chest contents compared to the rolls; restart preserved an emptied chest.
- Evidence: `/private/tmp/guardian-random-loot-verification-20260909/`.
- Restart Lunar to use this update. Existing completed sites are not refilled.

## Latest installed follow-up — initial victory loot, September 9

The user requested modest vanilla treasure now, with magical items deferred.
Both chests together now contain 4 diamonds, 8 gold ingots, 16 iron ingots,
an iron chestplate, iron leggings, a Protection II enchanted book and an Unbreaking II
book. These replace the earlier emeralds/apples/diamond gear/XP bottles. Existing
completed chests are not refilled. The sanctuary design is unchanged.

- Installed JAR: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-church-loot-20260909-112705.jar`
- Source/installed SHA-256: `aab5e92f98ff784bea0e4b35301bd270577a20abc1d00e077cad827447b7ef89`
- Passed: Gradle build and existing regression suite, diff whitespace check,
  JAR integrity, and the real-server church lifecycle fixture with exact checks
  of both chest inventories and both stored level-II enchantments.
- Evidence: `/private/tmp/guardian-church-loot-verification-20260909/`.
- Restart Lunar to load this update; future victories use the new contents.

## Latest installed build — survival sanctuary, September 9

The complete survival encounter and final ornate **stone** palette are installed in
Lunar. No crosses; roofless overgrown ruins; restored carved stone, colored glass,
teal roofs and inexpensive terracotta inlays. The user approved the ornate geometry
and requested removal of quartz/gold; the final layout has no gold, quartz, lapis or
copper resource blocks. Keep the approved shape and ornamentation when iterating.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-survival-church-20260909-111703.jar`
- SHA-256: `746fc48ca8363bcbe0a83491ecf084f2d8359f1743fc0324a63c6194956cf5f7` (source and installed JAR matched).
- Passed: clean build, required asset checks, church layout drift check, JAR integrity,
  real-server ritual/abort/retry/death/restoration/rewards checks, restart persistence,
  normal-world natural generation/rotated socket/heart chest, and actual client
  socket/heart model resolution plus the existing batched floor regression.
- Server lifecycle was verified on the same ornate geometry before the final
  material-only stone substitution. Natural generation and the clean/client build
  checks passed after the final palette change. Human Lunar feedback remains open.
- Evidence: `/private/tmp/guardian-church-verification-20260909/`.
- Preview: `art/guardian_church/preview.html` and `comparison.png`; the local preview
  server is `http://127.0.0.1:8321/preview.html` (serves only that art directory).
- Next user action: restart Lunar, `/ew guardian church place` in a flat clearing,
  then `/ew guardian church locate`. Take the heart from the courtyard chest and
  use the socket in Survival. `/ew guardian arena stop` remains emergency return.

## Resume here — survival church encounter, September 9

The user tried the corrected regular-size floor and said they really like it.
They approved implementing the survival church/heart/ritual/restoration loop,
with easy placement/location commands and an offline preview of both builds.
Read [guardian-church.md](guardian-church.md) for the complete implementation.
The user rejected the first simple design and requested no crosses or specific
real-world religion, plus much more destruction (roofless walls/pillars like their
references). The revised sanctuary has carved arches, a fictional broken-ring glyph,
fragmented walls/columns, fallen masonry and a grass-reclaimed floor. The user then
requested the restoration be as ornate and colorful as possible: quartz, extensive
multicolored glass, teal/gold roofs, ring crowns, and patterned floors are now authored.
V1 art is
historical only; the runtime templates and current preview use this revised layout.

`/ew guardian church place` builds a validated nearby test site and prints its
socket coordinates; `/ew guardian church locate` finds recorded sites or searches
natural generation. `/locate structure elementalwands:guardian_church` searches
natural sites. The heart is in the courtyard chest; use it on the socket in
Survival. Sneak-use an empty hand to recall a lost heart. Victory permanently
restores the church and grants loot; failed/interrupted attempts can retry.

Offline previews: `art/guardian_church/preview.html`, `comparison.png`, and
`interior.png`. They use the authored block layouts and vanilla textures, with
simplified full-block envelopes for non-cube blocks. They are not in-game evidence.
The latest church build needs a human Lunar playtest; keep Guardian fight-balance
and earlier takeoff-specific checks open.

The sections below preserve the earlier arena development history.

## Resume here — Guardian arena prototype

The user approved implementing the enormous roofless arena before the church or
summoning items. See [guardian-arena.md](guardian-arena.md) for the complete current
specification and playtest commands. Current sequence: immediate sealed membership,
0.7-second wall eruption, expanding floor, eight-second ascent, solid sky structure,
Guardian awakening/fight, descent, and withdrawing walls.

The arena is 128 blocks across with walls to build height. Death or disconnect
eliminates a participant until the encounter ends; players respawn outside and
cannot rejoin. Re-entry is explicitly deferred to a future session. The eventual
church/heart/statue/restoration/loot are agreed design direction, not implemented.

Run `/ew guardian arena start` near a Guardian in Survival/Adventure, outdoors,
with the party within 20 blocks. `/ew guardian arena stop` safely returns the group
from anywhere. `/ew guardian arena status` reports stage and survivors. One arena
per server for this prototype. The original terrain is retained; only initially
empty sky is modified. World restart aborts and cleans up the old encounter.

The user played the first arena prototype and reported: wall texture flicker when
combat starts, choppy ascent despite high FPS, the Guardian visually sinking into
the moving floor, and an awkward outside/death view. They then requested a massive
church interior with ornate pillars rather than the oversized-block look.

The follow-up uses one persistent visible church shell and invisible, non-luminous
collision backing. Construction starts during formation/ascent with smaller batches.
Invisible passenger carriers replace repeated player teleports. Carriers, camera,
Guardian, and floor share one curve and previous-frame sample; the floor caches the
entity-tick time to avoid the client's later world-time increment. The outside search
keeps clear of the wall and a solo wipe no longer asks the player to wait for survivors.

September 9 feedback: the user tried the church/lift build and said it was good,
but the oversized floor blocks gave movement an odd sense of scale. They asked to
keep the arena size and try normal block sizes. The floor and perimeter rim now use
one-block vanilla texture tiles, batched into one top-surface render submission.
The 128-block room, architecture, collision, lift timing, and encounter rules stay
as before. The new floor still needs a human playtest.

The first one-block floor build crashed immediately after the walls appeared.
The September 9 09:49:43 crash report identifies `IllegalArgumentException: Invalid
atlas id: minecraft:textures/atlas/blocks.png` at `GuardianArenaFloor.submit`.
The lookup now uses `Atlases.BLOCKS`; the GPU render layer still uses the atlas
texture path. The optional client regression loads real assets and invokes the
production floor code, unlike the older server-only fixture.

Next is a **fresh human Lunar playtest** of the corrected regular-size floor.
Prior server tests did not reveal the original rendering problem; do not claim the
new client motion or appearance is confirmed until played. The earlier takeoff fix
also remains unconfirmed by a human.

### Earlier leap fix

The prior bug report was a yellow landing circle followed by cancelled takeoff.
The existing fix probes actual foot support and uses vertical departure/arrival
clearance. `/ew guardian status` still reports the last leap cancellation reason.
Do not remove route/ceiling checks to bypass a real obstruction.

## User decisions and priorities

- This is intended as a cooperative spell-combat game. The boss must pressure a
  group, rotate targets, and commit visibly to attacks rather than chase one player.
- Approved look: hulking, hunched stone Guardian, oversized arms, shorter legs,
  weathered Minecraft-style cuboids, sparse moss, and pale cyan magic.
- Full-body animation matters: anticipation, torso/chest motion, knees, shoulders,
  hands, and recovery. Avoid returning to isolated limb swings.
- Earlier solo fights were too easy with only Fire primary and ordinary sideways
  walking. Prediction, a visible stone wave, and the leap were added in response.
  Overall solo and co-op balance remain open; do not claim they are solved.
- The leap must use both fists to strike the ground, create a first shockwave,
  propel the body upward, then land with a smash and a second shockwave.
- The user explicitly requested longer reaction time: **1.8 seconds airborne and
  an 11-block arc**, replacing the original 1 second / 7.5 blocks.
- Use Lunar for the user's playtests; the user normally plays and shares feedback.
  Prefer discussing substantial new designs before changing them.
- Preserve the approved model/texture. Multipart hitboxes and general jerky follow
  pathing were deferred. Do not expand either project merely during a handoff.

## Current implemented checkpoint

Fabric Minecraft 1.21.10, Java 21, mod 2.2.0, GeckoLib 5.3-alpha-3. The broader mod
uses one Wizard's Wand (`fractured_wand`) with FIRE/WIND/STONE/NATURE/SPACE affinities.
Crystal ore progression and separate elemental wand classes are retired.

The Guardian is summon-only, persistent, and automatically aggressive toward nearby
eligible Survival/Adventure players. It has a shared boss bar and health scaling
(200 solo, +100 per additional player, capped at 600). Creative/Spectator players
are excluded from automatic targeting/damage; automatic combat is off in Peaceful.

| Attack | Current behavior |
| --- | --- |
| Mouth beam | Cyan, 1.6s charge, 0.4s committed aim window, 0.6s hazardous pulse. Movement prediction before lock; 8 damage at most once per victim per cast, including late entrants; cover checked. |
| Rubble throw | A 1.4-block stone attaches to the live hand; corrected mirrored server socket. Predicted aim locks at tick 36; release at tick 44; faster ballistic travel. 8 direct or 4 splash, never both. |
| Shockwave | Visible rendered stone ridge, cyan crest, dust; radius 18, speed .65 blocks/tick, .75-block-high jumpable band; 6 damage once per victim. No world block placement. |
| Close slam | Standing full-body two-handed slam, full 360-degree wave, 6 damage and shared wave cover/jump rules. Separate from the leap and floating-stone fan; circular shape approved for multiplayer. |
| Leap | Targets visible players 12–30 blocks away. Lock tick 10; fist strike/takeoff/first wave tick 14; flight 36 ticks; landing tick 50; second wave tick 56; recovery ends tick 94. Center radius 3 does 16 damage, outer radius 6 does 10, before armor. Landing victims are excluded from that landing's follow-up wave. |

Leap movement respects collision and does not teleport or break blocks. Two wave
slots retain independent world-space origins/hit sets. With the current longer
flight the waves are separated in time, although the rendering supports overlap.
Stop/death/removal/empty encounter cancels pending effects and restores gravity.
The saved internal leap-gravity tag is cleared on reload so an interrupted airborne
Guardian can fall normally without resuming stale damage.

## Quick playtest commands

Cheats/operator permission required. Controls select the nearest living Guardian
within 32 blocks in the current dimension.

```mcfunction
/summon elementalwands:fractured_guardian ~ ~ ~10
```

| Command | Purpose |
| --- | --- |
| `/ew guardian leap` | One leap; test 12–25 blocks away on open ground first. |
| `/ew guardian status` | Read the last leap state or cancellation reason. |
| `/ew guardian fight` | Enable automatic group combat. |
| `/ew guardian stop` | Cancel combat/effects and persist passive mode across reloads. |
| `/ew guardian beam`, `rock`, `shockwave`, `melee` | Separate real-attack tests. |
| `/ew guardian awaken`, `slam`, `throw`, `follow` | Cosmetic/movement review; these pause automatic combat. |

A restart of Minecraft through Lunar is required to load an updated JAR. Do not
mistake a world reload or browser refresh for loading new Java classes.

## Latest installed follow-up — floor atlas crash fix, September 9

The floor renderer now uses `Atlases.BLOCKS` to retrieve the atlas definition,
while retaining `SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE` for the render layer.
The previous build crashed with `Invalid atlas id` at the first visible floor tile.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-atlas-fix-20260909-100703.jar`
- SHA-256: `2352f9ae9eaa35fb93d53984044e83ba5218708303111e232431e2d9e201d302`
- Passed: an actual Minecraft client loaded real textures, reproduced rejection of
  the old ID, resolved the corrected atlas and all three sprites, and ran production
  floor submission into real vertex buffers at hidden/partial/full sizes. The check
  verified one-block tiles, a constant surface height, and a single mesh submission.
- Runner: `tools/guardian_floor_client_smoke.init.gradle`; used the optional existing
  Lunar asset cache because the development asset-index download was unavailable.
  It opened no world and closed itself after the check. This is an asset/mesh
  regression, not a human Lunar gameplay or full-fight FPS test.
- Passed: final clean build, existing Guardian/arena checks, required asset/socket
  validators, whitespace check, JAR integrity and test-fixture exclusion.
- Evidence: `/private/tmp/guardian-floor-atlas-verification-20260909/`.
- Next: restart Lunar and start an arena near the Guardian; keep its 128-block size
  and one-block floor tiles. No human playtest of this repaired build is confirmed.

## Previous installed follow-up — regular-size floor, September 9

The user liked the revised arena overall but requested normal block-sized floor
textures. The floor and rim now repeat vanilla textures every 1 block instead of
stretching each texture across 8 blocks. A single batched top surface avoids
submitting 16,384 separate cube models. Palette/aisles, 128-block room size, church
architecture, collision, shared lift motion, and encounter rules are preserved.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-unit-floor-20260909-094005.jar`
- SHA-256: `3925f9fff09f61b1734c8375d8bd35f8402fcaa13deca3b4ac60af681e0f3844`
- Passed: clean build and existing Guardian/arena regressions, VFX/Guardian asset
  and socket checks, whitespace check, JAR integrity and packaging verification.
- No new server lifecycle run was needed for this client-only floor change. The
  previous runtime checks remain historical evidence for the unchanged lifecycle.
- The one-block floor appearance still needs a fresh human Lunar playtest.

## Previous installed follow-up — church arena and lift fix

Installed September 8 after the user's screenshots and church-interior request.
The build and installed JAR match. This revised client experience has not yet been
confirmed by a human playtest.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-arena-lift-fix-20260908-174827.jar`
- SHA-256: `59e3bf265cac90ecb698b1dd774487111b2ec1d07309da10528ea1ae32ca97a5`
- Passed: clean build, all Guardian/arena regressions, asset/socket validators,
  whitespace check, JAR integrity, dedicated-server lifecycle and restart recovery.
- New runtime assertions cover mounted lift transport, blocked cinematic dismount,
  identical camera/Guardian/floor partial-frame samples, bounded player teleport
  corrections, invisible collision surfaces, and zero backing light emission.
- The final visual pass uses one monumental lancet per bay to bound render work,
  and a continuous floor rim closes the space behind the perimeter columns.
- Temporary evidence: `/private/tmp/guardian-arena-lift-fix-verification-20260908/`.

## Initial arena build and verification — September 8, 2026

The initial arena prototype was installed and subsequently playtested by the user.
The screenshots exposed the motion/overlap problems described above. This record
is historical; the follow-up installation above is current.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-arena-20260908-162241.jar`
- Build/installed SHA-256: `b73c73c95b68d3b7138ccb90499f2e77ce4824f12f1eaf51e0962dce1ac0f6e1`
- Passed: clean Gradle build, arena/boss/leap/beam/socket regressions, all VFX and
  Guardian asset/socket validators, `git diff --check`, and JAR integrity.
- Passed: separate dedicated-server lifecycle fixture (real floor, roofless walls,
  teleport and foundation protection, actual death, respawn exclusion, victory
  return, original ground preservation, and chunk-force ownership).
- Passed: restart of a saved active fight, removing its floor/walls, restoring its
  Guardian, and releasing only its own chunk-force flag.
- Temporary test evidence: `/private/tmp/guardian-arena-verification-20260908/`.
  The optional smoke runner in `tools/guardian_arena_smoke.init.gradle` reproduces
  the checks. Fixtures are excluded from the release JAR.

## Earlier installed build and evidence (before the arena)

At this documentation checkpoint, the local build and Lunar JAR were checksum-
matched again:

- Build: `build/libs/elementalwands-2.2.0.jar`
- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- SHA-256: `595edfd15651e5b2c49215d0d4a7536eb5c9026a16bcfe30f410a7e9893e9357`
- Previous-build backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-takeoff-fix-20260908-104116.jar`
- Optional temporary receipt: `/private/tmp/guardian-takeoff-fix-install.json`

These are dated records, not a substitute for checking the current JAR after future
edits. Temporary files may disappear. No restart or successful in-game leap after
this installation was confirmed before stopping.

The last implementation pass passed clean Gradle build, Guardian regression suites,
asset and socket validators, `git diff --check`, and JAR integrity checks. Tests
cover targeting/cooldowns, trajectories, marked AoE bounds, wave timings, physical
support geometry, step-route rejection, retained walls/ceilings, animation duration,
and 180 GeckoLib hand/socket pose comparisons. **They do not launch a world or prove
live terrain collision, multiplayer behavior, or encounter balance.**

## Assets, preview, and code map

- Approved geometry/texture: `art/fractured_guardian/v2-textured/` (132 cubes, 38 bones).
- Six approved full-body clips: `art/fractured_guardian/v4-expressive/`.
- Current nine-clip package, editable Blockbench project, leap generators and pose
  review: `art/fractured_guardian/v5-leap/`. Its generator copies the six V4 clips
  unchanged and adds launch (.7s), air (1.8s), and landing (2.2s).
- Current preview: [V5 full jump](http://127.0.0.1:8767/v5-leap/preview.html), default
  **Full jump + both waves**. It illustrates a 16-block leap on a flat grid using
  the arc/timing and separate wave origins. It does not simulate live collision or
  damage. The V4 URL cannot show the new jump. Refresh stale tabs after regeneration.
- Preview generation: `preview_leap.py` plus `sequence_preview.py` in V5. Flight and
  preview timing/height currently appear in both Java and Python/JS; keep them in
  sync when tuning. The airborne pose generator stretches a normalized pose profile.
- If the preview server is absent, run from the repo:
  `python3 -m http.server 8767 --bind 127.0.0.1 --directory art/fractured_guardian`.
  Check existing listener ownership first; preserve an existing preview process.
- Runtime: `entity/GuardianBossCombat.java`, `GuardianLeapAttack.java`,
  `GuardianLeapRules.java`, `GuardianCombatRules.java`, `FracturedGuardianEntity.java`.
- Effects: `client/renderer/GuardianLeapVisual.java`, `GuardianWaveVisual.java`,
  `GuardianHeldRockLayer.java`, `GuardianRockRenderer.java`; ground/cover sampling
  is shared through `entity/GuardianWaveSurface.java`.
- Commands: `command/GuardianCommands.java`. Tests: `src/guardianTest/java/`.
- Packaging: `tools/prepare_guardian_assets.py`; socket source/check:
  `tools/prepare_guardian_throw_socket.py`. Regenerate V5 after changing V4 and before
  packaging. Do not accidentally package only the old six-clip file.

## Deferred work and repository caution

The arena is now implemented following the user's subsequent design approval.
Validate it with the existing leap and Fire/Wind/Space kits before tuning arena
scale or authoring the church reveal. Church generation, heart item/statue ritual,
restoration, rewards, additional attacks, and weak points remain deferred.

The planned lore is a broken sanctuary statue repaired/charged by players, awakening
an ancient keeper whose duty outlived its memory. Ruin generation, repair ritual,
full summoning reveal, rewards, and an exposed core are still proposals, not implemented.

The worktree was clean when arena implementation began. The arena changes and
updated guides are now local and uncommitted; no commit or push was made. Preserve
the existing Guardian and Fire/Kinetic Inferno baseline and the new arena work.
The spell/HUD asset contract remains 298 PNGs and 40 particle definitions; the two
Guardian textures are separate. `docs/PLAYER_GUIDE.txt` and the Ice redesign document
remain historical and must not override AGENT.md's current architecture.

The latest visual feedback approved the ornate shape, details, stained glass and teal
roof, then requested ordinary stone instead of most quartz and all gold. The final
palette replaces quartz with stone bricks/polished andesite/smooth stone, gold with
carved stone, lapis with blue terracotta, and copper inlays/ornaments with colored
terracotta. Geometry and glass colors are preserved; rewards remain in the chests.
# September 11 Nature playtest follow-up

The user liked a full Nature/Guardian fight and reported entangle wraps on the
ultimate tree. Fixed Nature stacks, slows, root tracking, thorn damage and attached
wrap rendering to skip its stationary hitbox. Normal damage remains. Clean build,
all required asset/package checks and the extended real-server Nature fixture pass.
Installed Lunar JAR SHA-256: `546af8acd617e1ec950944fac2f6d08b027d835c8c52e17a8b2083e182d38628`.
Source/install match verified; restart required. Client confirmation of this fix
is pending. See `docs/nature-redesign-2026-09-10.md` for the current ability reminder.
