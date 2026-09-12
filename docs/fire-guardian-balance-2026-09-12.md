# Fire damage and Guardian pressure — September 12, 2026

The user playtested Fire and Nature against the Guardian, approved a Fire nerf
and faster aimed stone barrage, and reported that the laser appeared only once
across two fights, during phase two.

## Fire

- Primary and Pyre impact: 8 → 6 raw damage. Firing cadence, burning, charge awards,
  Pyre ground duration, movement/healing benefits and approved artwork are retained.
- Meteor uses one explosion damage path. Falling-block impact damage is disabled.
  Damage is capped at 60 raw within three blocks, falls linearly to zero at ten
  blocks, and is multiplied by Minecraft's cover exposure. Distance is measured
  to the target's bounding box. The normal armor and Guardian guard rules follow.
- Explosion power is 5 instead of 15, limiting its blast reach to ten blocks.
  Ordinary explosion terrain/fire rules remain in use; existing arena protection
  still applies. The eight-block meteor artwork and falling trajectory are retained.
- In the real-server exposed-Guardian fixture, the falling meteor and subsequent
  burning removed **73.5 HP**, inside the approved 60–80 target. The earlier recorded
  fixture removed about 212 HP. Five simultaneous casts against guarded armor removed
  **96.8 HP** in this pass, with all five casters contributing.

## Guardian barrage and laser

- `/ew guardian fan` retains its command name but now casts three bursts of three
  stones, in both phases. Releases occur at ticks 18/26/34 (0.9/1.3/1.7 seconds),
  with 14 ticks of recovery; the full action is 48 ticks (2.4 seconds).
- Speed doubles from 0.9 to 1.8 blocks/tick; adjacent directions tighten from 12 to
  4 degrees. Each burst reacquires a target, predicts observed movement, and commits
  four ticks before release. A spark flash marks commitment. There is no homing.
- Damage remains 6 per burst at most, with the existing normal player hurt immunity.
  One burst cannot stack its three stones on one player. Stone Wall absorption is
  shared within a burst; subsequent bursts can pass the consumed wall. Solid cover,
  guard interruption, encounter teardown and bounded flight lifetime remain.
- Phase-two mixed slam/barrage sequences use the quicker barrage; the triple-slam
  alternative remains available. The separately authored barrage animation now
  contains three synchronized sweeps. All other packaged animation clips are identical
  to the working files before this balance pass.
- The director counts attacks started since its last laser. After two other attacks,
  a ready laser gets priority when a visible player is within its existing 5–24-block
  targeting range. Leap priority, random/hover/pending fans and Nature clearing cannot
  override that reserved choice. Cooldowns, earned guard/Nature recovery, phase
  transitions and committed attacks are respected. The counter resets with the fight.
- Laser damage, range, charge, aim lock, pulse and animation are unchanged. This is
  a rotation guarantee at eligible decision points, not a fixed real-time interval.

## Verification and installation

Passing checks:

- Clean build and executable Guardian contracts, including sustained laser rotations
  in both phases, cooldown/range/cover limits and exact burst timing.
- Disposable guard server: real six-damage primary/Pyre, live meteor measurements,
  multiplayer damage acceptance, guard lifecycle and safe interrupted leap landing.
- Disposable pressure server: real releases at ticks 18/26/34, all nine projectiles
  in both phases, committed aim followed by fresh elevated targeting, interruption,
  Stone Wall absorption, same-burst damage limits, hovering and pursuit. The actual
  director selects the reserved laser in both phases with competing overrides armed.
- Loaded isolated client: animation bake, three reforming stone sets and bounded
  glow geometry, plus the existing Fire/Nature/Guardian mesh checks. No user world
  was opened. Only the barrage animation changed; all other clips were compared.
- All affinity, Fire, Guardian, throw-socket and Nature asset validators; whitespace
  and JAR integrity. Smoke fixtures are excluded from the release JAR.
- Refreshed the existing offline animation preview to the 2.4-second barrage.

The first simultaneous test launches collided with shared Gradle build outputs;
subsequent server runs were serialized. Fixture corrections removed target armor
from raw spell measurements, allowed gravity to restore the cached grounded flag
after forcibly interrupting awakening, and set consistent prior-attack history for
phase-two laser priority. These corrections did not require weakening the combat
assertions or changing the production balance.

Evidence: `/private/tmp/fire-guardian-balance-20260912/`.

Installed in Lunar; source and installed SHA-256 both match:
`135618b30c832e0251a2cae0b434401571ed0352b2086352c152bb1b93b717a0`.

Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-fire-guardian-balance-20260912-104728.jar`.
Restart Lunar Client to load this build.
Human fight difficulty, dodge feel and visual confirmation require a new playtest.
