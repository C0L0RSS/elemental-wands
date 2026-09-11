# Guardian phases — September 9, 2026

The user approved two phases, keeping the laser and guard system, with phase two
looking like unstable magic escaping the shell. This pass changes physical attack
pressure rather than raising health again. Human Lunar balance/visual feedback is pending.

## Encounter behavior

- At 60% main health, phase two is latched. Finish the current action and any earned
  guard opening before a 64-tick transition. No healing, invulnerability or guard
  refill is granted by the transition. Guard can break during the transition;
  that interrupts the pose, grants the opening, and retains phase two.
- Phase state, a pending transition, and remaining transition time are saved. Late
  party scaling/healing cannot revert a triggered phase. Empty-encounter reset
  returns to phase one. Stop cancels pending hazards and the transition animation.
- New full-body transition: knees brace, shoulders wrench backward, hands open and
  the torso shakes. Erratic cyan discharges and soul-fire wisps escape the shell.
  Core light pulses during phase two. Discharges fade during guard openings and
  are suppressed during the laser so its existing telegraph remains readable.
- Main/guard health and mitigation remain 600/+450 and 120/+90 per extra player,
  40% guarded / 150% exposed, with the existing burst curve. One health bar.

| Attack | Phase one | Phase two |
| --- | --- | --- |
| Slam/shockwave | One low wave, radius 32, speed .85 blocks/tick, height .75; 6 damage | Three slams: impacts at ticks 21, 66 and 116; final animation finishes tick 146. Radius 44, speed .95, same height/damage |
| Rock | Aim commits tick 40, release 44; speed target 1.5 blocks/tick, flight clamped 8–36 ticks; visible 3-block burst | Three throws, releases at sequence ticks 29, 76 and 123; each retargets/re-aims independently; sequence ends tick 141 |
| Leap | Original 36-tick flight and 11-block arc. Hard impact confined to radius 3; outside that, jump the low landing wave | Same committed flight, then a separate follow-up slam after landing recovery; third wave at sequence tick 120 |
| Laser | Existing charge, lock, pulse, damage, cooldown and recovery | Same |

The first two phase-two slam clips take 45 ticks, the final clip takes 56. This
creates two steady beats followed by a slightly delayed final impact. Rock hits
remain 8 direct or 4 splash, never both. No homing after release. Throws can select
targets through 48 blocks. Movement attributes are .19/.23 for phases one/two, and
the boss repositions during cooldown when no visible player is within 12 blocks.
Physical cooldowns shorten 20% in phase two; the between-action gap becomes 10
instead of 12 ticks. Laser cooldown and its following 12-tick gap are unchanged.

Exact slam/leap destination markers are removed. Leap shadows track the body's
actual airborne position, not its committed destination. The hard landing is not
jumpable directly beneath the Guardian; the outer waves are. Normal timed jumps
avoid those waves without a wand ability. Cover and arena/spectator eligibility
still apply.

## Hazard lifetime and performance

Waves have their own immutable origin, phase tuning, hit set, timestamp and visual
slot. They finish after their originating animation, so the last expanding wave
is not silently truncated by recovery. Authored combo timing needs at most two
simultaneous slots. Expiration clears each tracked slot; stop, guard stagger,
death and leap interruption cancel hazards. Landing-impact victims are excluded
from that landing's wave, retaining the previous protection against double hits.

Wide waves use one continuous client-rendered ridge. Server dust is bounded to
24 samples every three ticks rather than hundreds of samples every tick. Phase
magic is one bounded custom draw with at most 400 vertices. Neither creates world
blocks, damaging lightning entities, nor persistent additional entities.

## Assets and preview

`art/fractured_guardian/phase/build_phase.py` authors `phase_change`, `slam_fast`
and `throw_fast`. Attack retiming maps the impact/release exactly to server ticks;
all 12 previously installed animation clips remain unchanged. Total: 15 clips.
The original model, textures, beam animation and approved backward guard pose are
preserved. The generator samples 129 transition poses and checks floor clearance.

The existing preview at http://127.0.0.1:8330/preview.html now offers the transition,
fast physical attacks and guard opening. It uses the actual rig/animations; the
page explicitly notes that magic effects and combat run in Minecraft. Browser
inspection confirmed the new transition pose renders. It is not live gameplay proof.

## Verification

- Clean Gradle build, Guardian/arena/beam/guard/Nature/leap/socket contracts.
- New phase contract checks the party-relative threshold, ordinary vanilla jump
  trajectories, hard landing boundary and simultaneous wave capacity.
- Isolated real-server phase fixture: full earned opening before transition;
  phase/save clock preserved without healing; guard break during transition;
  three waves hitting a grounded player 40 blocks away while an elevated player
  avoids them; final wave continues after recovery; three physical projectiles;
  successful leap/follow-up and complete hazard cleanup.
- Worldless real client: all three added GeckoLib clips bake; actual magic mesh
  buffers are finite and bounded through six states and suppressed during beam;
  existing core/rib exposure and arena-floor checks pass.
- Required VFX/Guardian/socket validators, whitespace and JAR integrity checks.
- Compared against the prior installed JAR: beam attack/timing classes are byte
  identical, and all 12 old animation clips are structurally identical.

Fixtures are excluded from the release JAR. Run the phase server fixture with
`./gradlew -I tools/guardian_phase_smoke.init.gradle runServer --args nogui`.
It uses only its disposable build directory and a loopback ephemeral port.

A human playtest is still needed for difficulty, animation feel, readability,
multiplayer latency and frame rate. In particular, tune triple-slam rhythm and
rock splash after playing; server success is not a claim that balance is final.

## Installed build

- SHA-256: `c32b47e990184c6a0fe1ff267c85c1c5473ee2e94afeec2433a5b8b93799089a`. Build and installed files match.
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-phases-20260909-193806.jar`.
- Evidence: `/private/tmp/guardian-phase-verification-20260909/`.
- Restart Lunar to load the new classes. No user world was opened or edited.
