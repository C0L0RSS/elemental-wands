# Elemental Wands — session handoff

Updated September 8, 2026, at the user's requested stopping point. Read
[AGENT.md](../AGENT.md) for authoritative architecture, build, and deployment rules;
[the Guardian guide](fractured-guardian.md) has combat details and draft lore.
This replaces the obsolete crystal/Ice/per-element-wand handoff.

## Resume here

**The latest Guardian takeoff fix is installed, but has not yet been confirmed by
the user in Minecraft.** Stopping the session is not confirmation that the leap
works in-game. Resume with that playtest before adding more boss features.

The user's last bug report: a yellow landing circle appeared, the Guardian raised
its arms for about half a second, then cancelled the leap and chose another attack.
Two potential cancellation causes were fixed:

1. A frozen windup can clear Minecraft's cached `onGround` flag. Leap start,
   takeoff, and landing now probe physical block support beneath the feet.
2. Immediate diagonal travel could make the full-body route check hit adjacent
   steps. Horizontal travel now eases in after the first 10% of flight and ends
   before the last 10%, allowing vertical departure and arrival. A complete route
   is validated before showing the marker; blocked predicted updates keep the
   previous valid landing instead of replacing it.

`/ew guardian status` now reports the last leap state/cancellation reason. If the
bug persists, capture that output after `/ew guardian leap`; investigate the actual
reason rather than removing wall/ceiling checks or blindly increasing clearances.

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
| Close slam | Full-body two-handed slam, frontal radius 4.5, 6 damage, cover/arc checks. A separate one-arm swat is not implemented. |
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

## Installed build and evidence

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

First confirm the takeoff fix. Then assess the complete solo fight with Fire primary,
co-op target rotation, rough terrain, and stop/death/reload cleanup. Tune from those
results before adding further attacks, phases, rewards, or weak points.

The planned lore is a broken sanctuary statue repaired/charged by players, awakening
an ancient keeper whose duty outlived its memory. Ruin generation, repair ritual,
full summoning reveal, rewards, and an exposed core are still proposals, not implemented.

Work is on `main` with substantial uncommitted/untracked changes. Guardian code and
art coexist with earlier Fire/Kinetic Inferno work. No commit or push was made in
this session. Preserve all of it; do not reset, clean, or overwrite unrelated changes.
The spell/HUD asset contract remains 298 PNGs and 40 particle definitions; the two
Guardian textures are separate. `docs/PLAYER_GUIDE.txt` and the Ice redesign document
remain historical and must not override AGENT.md's current architecture.
