# Fractured Guardian — cooperative boss playtest

The model and earlier movement pass were approved in Lunar. The fuller V4 animations
were approved in the HTML preview. The cooperative encounter and its visibility/
prediction pass were then playtested in Lunar; the user reported that solo Fire
primary still made the fight too easy. This version adds the requested fist-powered
leap, heavy landing, and two shockwaves. It has automated checks and pose review,
but needs a fresh live playtest. Ruins, rewards, and encounter phases remain future work.

## Stopping checkpoint — September 8, 2026

The user stopped after the takeoff-cancellation fix was installed. Their latest
live report was a yellow circle and half-second arm lift followed by cancellation.
The physical-floor probe and vertical departure/arrival fix passed regression
checks, **but a successful leap with this build has not yet been confirmed**.
Next session should test `/ew guardian leap`, capture `/ew guardian status` if it
stops, then return to full-fight balance. See [the session handoff](MOD_HANDOFF.md)
for the installed checksum/backup, evidence limits, and repository state.

## Start here

Restart Minecraft through Lunar to load the installed JAR. All clients and the
server need this mod build for cooperative testing. Use Normal difficulty and an
open area for the first test; Creative and Spectator players are not automatically
targeted or damaged. Peaceful disables the automatic encounter.

```mcfunction
/summon elementalwands:fractured_guardian ~ ~ ~10
/ew guardian fight
/ew guardian stop
```

A fresh summon automatically awakens when it sees a Survival/Adventure player
within 24 blocks. `/ew guardian fight` re-enables this behavior on a stopped Guardian.
`stop` cancels the current attack, removes its held/flying rocks, hides the boss bar,
and makes it passive **through world reloads** until `fight` is used. The summon is
still registered as MISC with no natural spawning. Existing saved Guardians become
aggressive unless they have the `ew_guardian_passive` command tag.

## How the encounter works

- A 4.2-second awakening gives the group time to recognize the encounter.
- A shared blue boss bar shows 200 health solo, plus 100 for each additional nearby
  eligible player, capped at 600. Joining a fight raises maximum/current health by
  the same amount, preserving damage already dealt. Departures never shrink it
  mid-fight. Damage per attack does not scale with group size.
- The boss considers all eligible players within 48 blocks of itself and the
  encounter origin. Targeted attacks favor the least recently targeted eligible
  player, with visibility/range checked before selection. A locked attack never
  switches to a different player partway through it.
- Close clusters favor a shockwave; players at range receive throws and beams;
  a close frontal slam guards its immediate space. Ability cooldowns and a
  0.6-second gap after each recovery prevent overlapping actions. A different
  available attack is preferred before repeating the previous one.
- It holds a firing position instead of continuously chasing one player. When
  everyone is out of reach or behind cover, it slowly repositions within 14 blocks
  of its encounter origin. Movement targets stay stable, with one path request per
  second. Wide-body pathfinding on rough terrain still needs playtesting.
- If no eligible players remain in the arena for 10 seconds, the encounter resets
  to full solo health. Death, removal, stop, and lost participants clear pending
  attacks/projectiles. Reloading clears attack timelines; an aggressive living
  Guardian can awaken again, retaining its saved health. There is no teleport reset.
- The original model, textures, and single 3.2 × 5.2 body hitbox are preserved.
  Multipart hitboxes and core weak points remain deferred.

## Attacks and counterplay

The visibility/difficulty pass corrects a mirrored server hand socket and replaces
the small item-style rock with full block geometry. The held stone is rendered on
the live hand, so it cannot drift on its own network interpolation path. The wave
uses temporary rendered stone geometry, denser dust, and a bright cyan crest; it
neither changes blocks nor physically blocks the player. It stays readable at low
particle settings because the ridge is rendered independently of particles.

Throws and beams predict steady horizontal travel before committing. Prediction
is capped at six blocks and rejects teleports. Waves now enter the solo rotation
after throws, with beams favored after waves, and cooldowns are shorter. Health and
damage values are unchanged; these changes need a fresh live difficulty assessment.

All listed damage is points before armor (two points = one heart), using normal
Minecraft damage/shield rules. Boss attacks do not place, remove, or explode blocks.

| Attack | Tell and behavior | Damage / counterplay |
| --- | --- | --- |
| Cyan mouth beam | V4 chest expansion and jaw charge; predicts movement for 1.2s, locks for 0.4s, fires at 1.6s; visible for 0.6s; 24-block range. | 8 at most once per victim throughout the visible pulse, including late entrants. It predicts steady walking before locking; reverse direction after commitment or use cover. |
| Rubble throw | A 1.4-block cobblestone chunk attaches to the actual animated hand. Predicted aim locks at 1.8s, release at 2.2s, then a faster gravity-driven arc to the committed landing point. | 8 direct or 4 nearby splash within 2.25 blocks, never both. Change direction or use mobility after aim locks; steady sideways walking is predicted. Rock stops at solid cover. |
| Ground shockwave | Cyan circular sparks during the overhead slam; impact at 1.3s sends a visible stone ridge with a cyan crest outward to 18 blocks at 0.65 blocks/tick. | 6, at most once per wave. Jump over the 0.75-block-high band or move beyond its range. It samples collision surfaces, stops its damage behind cover, and skips deep gaps. |
| Close slam | Stone-dust arc in front during the full-body slam; aim stops turning 0.6s before impact. | 6 in a frontal arc within 4.5 blocks, with knockback. Step behind it, retreat, or block. It is not a 360-degree contact attack. |

Shockwave terrain sampling supports surfaces from 3 blocks below to 2 blocks above
the starting floor. It does not climb cliffs or pass through walls. Beam collateral
retains its prior living-entity rules; rock, slam, and shockwave damage only eligible
players. Guardian teammates are excluded. Space primary aim assist now recognizes
an aggressive Guardian even though it has no vanilla single-player target goal.

## Fist-powered leap — new playtest

The Guardian favors a visible distant player (12–30 blocks) when its leap is ready.
Its fists rise and slam the ground over 0.7 seconds. At that strike, one stone wave
spreads from the takeoff spot and the body launches high into the air. It tucks its
legs and draws its arms up, then extends its feet and lands with a heavy smash.
The second wave begins 0.3 seconds after landing, from the actual landing position.
Both waves finish independently; the longer flight separates their arrival.

An orange radius-6 marker outlines the landing blast and a red radius-3 marker shows
the heavy center. The marker follows a predicted position for 0.5 seconds, then
stays fixed. Flight lasts 1.8 seconds, peaking 11 blocks above the straight line
between takeoff and landing. The player must leave the marked blast area; jumping
alone only avoids the waves, not the landing smash's three-block-high damage volume.

- Heavy center: 16 damage (8 hearts before armor).
- Outer landing area: 10 damage (5 hearts before armor).
- Each wave: 6 damage (3 hearts), once per player per wave.
- Players caught by the landing blast are excluded from that landing's follow-up
  wave. The first wave and landing still have independent hit checks.
- A 2.2-second landing recovery and ability cooldown prevent immediate leap spam.

The leap moves the actual boss with collision checks. It cancels when a ceiling,
wall, newly placed obstruction, unsafe landing, or unloaded chunk blocks the route.
It does not remove blocks, teleport through cover, or resume a saved midair attack.
Stopping it in the air restores gravity and cancels all pending damage and waves.
Co-op targeting still rotates between eligible players. Only players near takeoff
need to jump the first wave; the distant target must escape the landing area and
then jump the arriving second wave.

Test `/ew guardian leap` first on open level terrain, then `/ew guardian fight`.
Check both wave origins, airborne foot/arm poses, the fixed landing warning, blast
radius, cover, and stopping while airborne. Also test saving/reloading during a leap:
it should fall normally without an orphaned marker or delayed damage.

The takeoff fix checks physical floor support instead of the cached grounded flag
that can reset while the boss is frozen. It rises vertically before moving across
nearby steps, finishes horizontal travel before descending, and validates the whole
route before showing the marker. `/ew guardian status` reports the latest leap
outcome if it still cannot launch in a particular location.

## Isolated attack and animation controls

Operator-only commands select the nearest living Guardian within 32 blocks in the
current dimension. One-shot attack tests aim at the issuer, including Creative
for visual review; Creative/Spectator remain immune. They leave the boss passive.

| Command | Behavior |
| --- | --- |
| `/ew guardian status` | Read the last leap state/cancellation reason without changing combat. |
| `/ew guardian leap` | One real leap with both shockwaves; best reviewed 12–25 blocks away on open ground. |
| `/ew guardian beam` | One real mouth attack. |
| `/ew guardian rock` | One real held-rubble throw. |
| `/ew guardian shockwave` | One real traveling shockwave. |
| `/ew guardian melee` | One real frontal slam. |
| `/ew guardian awaken` | Cosmetic awakening rehearsal. |
| `/ew guardian slam` | Cosmetic slam rehearsal, no damage. |
| `/ew guardian throw` | Cosmetic throwing motion, no rock or damage. |
| `/ew guardian follow` | Movement review; follows issuer, stops about five blocks away. |
| `/ew guardian stop` | Cancel everything and persist passive mode. |

## Playtest checklist

1. Solo: verify awakening, blue bar, each isolated attack, and the full automatic
   fight. Test sideways beam/rock dodges, jumping the ring, stepping behind the
   slam, armor/shields, and cover. Check the visible stone is gripped and released
   by the hand. The HTML preview cannot prove these Minecraft effects.
2. Co-op: use two or three Survival players on different sides. Confirm a shared
   health bar, alternating targeted attacks, and no mid-cast target switch. Stand
   together near the boss to provoke the wave, then spread out for ranged pressure.
3. Cleanup: stop during charge, while holding rubble, and during flight. Kill the
   boss during a throw. Test a player dying, disconnecting, switching dimension,
   and changing to Creative. Have everyone leave for ten seconds and verify reset.
4. Reload: save during a charge/throw, then reload. There must be no stale beam,
   rock, or stuck NoAI pose. `stop` must stay passive across reloads; `fight` resumes.
5. Terrain: test slabs, slopes, walls, gaps, and rough navigation separately from
   the flat-ground test. Fine pathing and visual alignment are still review items.

```mcfunction
/kill @e[type=elementalwands:fractured_guardian,distance=..32]
```

## Lore proposal: the keeper that remembers

Before the sanctuary became a ruin, its builders entrusted its threshold to a
keeper carved from the same stone as its pillars. A binding in its chest gave it
one duty: hold the sanctuary until its people returned.

They never did. The roof fell, the wards faded, and the keeper knelt among the
stones. Moss filled the carvings. Its body broke around a core that never quite
went dark.

A wizard can repair the statue and feed it enough magic to wake. But mending the
stone does not mend its memory. The guardian recognizes power crossing the
threshold and answers with the only duty it still understands.

**Possible inscription:** “Until the last light fails, I keep the threshold.”

This is a working story, not newly established history for the rest of the mod.
The sanctuary's builders, the cause of its collapse, and its link to the other
affinities can remain mysteries until we develop them together.

## How the story can shape the encounter

- Its markings are worn binding runes. Pale seams show the old magic holding its
  separate stone pieces together; the moss marks its long dormancy.
- Awakening should begin with a few small signs: a finger moves, the head rises,
  stones scrape, and the larger body finally bears its own weight. The current
  animation rehearses that final body motion; repairing the statue and the full
  magical reveal come later.
- The current throw conjures rubble; a future version could use marked courtyard stones. That makes the
  pickup readable and gives us a way to keep the fight from dismantling a player's
  buildings. Destructive terrain interaction remains a future design choice.
- A future slam can reveal the core briefly during recovery. This gives an agile
  player an opening and connects the fractured chest to combat.
- Defeat could quiet the binding and leave a kneeling statue, with a fragment
  offered as a reward. We can decide whether the keeper is destroyed, repaired,
  or released when we design progression.

## Next implementation checkpoint

First confirm the installed takeoff fix in Minecraft. Then tune cooperative attack
cadence and damage from playtests before adding more moves. Then consider an exposed-core punish window, a second phase, rewards, and
the ruined-statue summoning sequence. The current close attack reuses the approved
two-handed slam; a dedicated one-arm swat could follow once combat feels right.

## Authoring and verification

Approved geometry/texture source: `art/fractured_guardian/v2-textured/`.
Current merged animation source and editable project: `art/fractured_guardian/v5-leap/`.
The original six clips remain unchanged in `v4-expressive/`.
The source atlas and 132 cubes / 38 bones are unchanged.

The September 8 performance pass revises all six clips. Idle has a slow weight
shift and hand settling; walking couples pelvis motion with foot lifts, shoulder
sway, arm swing, and head counter-motion. Awakening rises from a crouch and opens
the chest. Slam adds a loading dip, whole-body impact compression, and settling.
Throw adds a crouch, torso twist, opposite-arm counterbalance, wrist release, and
finger follow-through. Beam adds the braced expansion described above.

Poses are eased and baked, then redundant keys are removed within small position/
angle error bounds. The preview validates the resulting interpolated curves, not
just the original pose samples. Both support-foot contact and the beam's stabilized
head pivot are checked. Actual terrain and client aim alignment still need Lunar
review.

```bash
python3 art/fractured_guardian/v4-expressive/build_animations.py
python3 art/fractured_guardian/v4-expressive/preview_motion.py
python3 art/fractured_guardian/v5-leap/build_leap.py
python3 art/fractured_guardian/v5-leap/preview_leap.py
python3 tools/prepare_guardian_assets.py
python3 tools/prepare_guardian_throw_socket.py --check
./gradlew clean build
python3 tools/prepare_guardian_assets.py --check
python3 tools/validate_remaining_vfx_assets.py
git diff --check
unzip -t build/libs/elementalwands-2.2.0.jar
```

The build runs executable targeting, cooldown, party scaling, ballistic, wave-jump,
and beam geometry/timing regression checks. A further 180 pose/yaw comparisons use
GeckoLib itself to verify the server release socket against the actual hand transforms. These do not launch a multiplayer world.
`prepare_guardian_throw_socket.py --check` verifies the server grip path against the
approved animation, so later animation edits cannot silently leave a stale socket.

The motion preview samples all six clips at 60 Hz and checks floor clearance and
support-foot contact on level ground. It also produces a pose sheet and an
interactive page with clip selection, pause, replay, and a timeline. Those are
rig/asset checks, not proof of Minecraft pathfinding, rendering, sound sync, or
multiplayer timing. Lunar playtesting remains the next checkpoint.

One-off actions use GeckoLib's server-triggered animations, with idle/walk on the
same controller to avoid competing full-body poses. See the
[GeckoLib triggerable animation guide](https://github.com/bernie-g/geckolib/wiki/Triggerable-Animations-%28Geckolib5%29).
