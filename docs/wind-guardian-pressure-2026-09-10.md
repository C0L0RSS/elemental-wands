# Close-range Wind and Guardian pressure — September 10, 2026

The user approved Wind as a close-range, mobile affinity and the Guardian changes
proposed after a Wind playtest: fix stored knockback, advance more deliberately,
add floating-stone fans, mix phase-two attacks, and respond to sustained hovering.
No health increase or laser retuning was requested.

## Wind

- Primary fires three short crescent blades: exactly along the crosshair and at
  eight degrees either side. All originate at the caster's eye, eliminating the
  old gap between two offset projectiles.
- Strict 12-block travel limit. The last movement/collision segment is clipped to
  the remaining distance. Damage falls linearly from 7 to 4 over that range.
- One enemy can take only one hit from each cast; the three transient projectiles
  share a hit set. Multiple different enemies may still be hit. Primary cooldown
  stays 20 ticks. Existing crescent textures are retained, drawn slightly smaller
  with shorter/sparser wisps instead of a long trail.
- Two dash charges, with each missing charge restored after 100 ticks (5 seconds),
  previously 80. Dash strength, chaining, slow falling and ultimate remain intact.
- Only accepted damage applies knockback, through the normal living-entity
  resistance path. Rejected duplicate hits cannot add extra upward velocity.

## Guardian

The boss rejects external additive velocity pushes. Its authored leap/lift use
explicit movement, so they are retained. Grounded attack freezes also discard
positive vertical velocity rather than preserving it for recovery.

The new fan attack raises five small stones above the shoulders/head, with cyan
charge edges and a separate full-body bracing/arm-sweep clip. The server and client
share the release sockets. Aim tracks during the windup, locks at tick 26 and
releases at tick 32. Phase one has one volley; phase two has another at tick 82,
with a fresh aiming/formation window starting at tick 50. Recovery ends at ticks
58/108 respectively. Stones fly straight at .9 blocks/tick, with radius .32 and
6 damage, once per player per volley; they have no splash or post-release homing.
Their bounded lifetime is 44 flight ticks. Solid cover between the body and a
floating socket prevents spawning a projectile beyond that obstruction.

A Stone Wall absorbs a volley before breaking. Its collision shadow is shared by
that volley's stones, so a later stone from the same volley cannot slip through
the newly removed wall. A later volley can hit. Ordinary cover and ultimate trees
still physically block these projectiles.

Attack selection sometimes uses the fan as a normal attack. Sustained airborne
players above 2.5 blocks for 24 ticks get priority when the fan is ready. This uses
position, not affinity or dash input; normal jumping never meets the threshold.
The same target rotation and cooldown rules apply. In phase two, eligible slams
sometimes become one slam followed by a separately telegraphed fan, while the
existing triple-slam pattern remains available. Guard breaks, phase transitions,
stop/death and lost targets cancel queued fans/projectiles.

Movement speeds are .24/.29 for phases one/two, previously .19/.23. Between normal
attacks, after earned recovery, the boss can spend up to 20 ticks approaching a
distant player, repathing every eight ticks and stopping within six blocks. It
also approaches during attack cooldowns. Arena bounds and ordinary-world leash
remain in force. Resuming an already-awakened encounter no longer freezes AI when
there is no awakening/guard/phase animation to play.

Laser mechanics, existing attack clips, main/guard health, damage mitigation and
the approved exposed-core opening remain unchanged. The new fan is a separate
16th animation. `/ew guardian fan` runs one fan attack for review (phase two uses
two volleys); existing arena start/stop and fight commands still work.

## Validation

- Live disposable-server Wind/pressure fixture: exact dash recharge; center-target
  hit and per-cast damage cap; strict maximum range; ordinary-mob knockback and
  rejected-hit behavior; repeated primary hits through slam/recovery without launch;
  5/10 actual fan projectiles; committed aim followed by fresh elevated aiming;
  guard-break cancellation; wall absorption across delayed shards; once-per-volley
  damage; actual pursuit; normal jump height versus sustained hovering.
- Existing real guard fixture: party scaling, actual meteors, damage acceptance,
  guard recovery and an airborne guard break followed by a safe landing.
- Worldless client fixture: new animation bakes, five charged stones generate
  finite/bounded geometry, stones disappear on release and reform for volley two;
  existing guard/core, Stone cluster and arena-floor checks remain included.
- Rig generator samples fan poses for floor clearance. Browser preview inspected
  at windup; it shows the actual body animation. Floating effects run in Minecraft,
  as indicated on the preview page. No human fight/visual confirmation yet.
- Required clean build, asset/socket validators, whitespace and JAR integrity.

The range fixture equips its zombie target with a helmet to exclude daylight
burning from health measurements. The movement fixture starts inside the ordinary
24-block awakening radius; the arena itself uses its existing larger admission
range. These fixtures do not edit player saves and are excluded from the release.

Run `./gradlew -I tools/wind_pressure_smoke.init.gradle runServer --args nogui`.
Preview: http://127.0.0.1:8330/preview.html (fan animation selected by default).

Human playtesting remains necessary for damage pressure, dodge rhythm, multiplayer
latency, and appearance. Automated checks establish mechanics, not final balance.

## Installed release

- Build and installed SHA-256: `9f189be74ae4e770409ee06c9aac06d6e8ab3430c3728bfb2e77a806c89dace2`.
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-wind-pressure-20260910-181442.jar`.
- Evidence and exact release copy: `/private/tmp/wind-pressure-verification-20260910/`.
- Final client check and clean build passed. All 15 pre-existing animation clips
  are preserved; beam implementation/timing classes are byte-identical to the
  prior installed build. Smoke fixtures are excluded from the release JAR.
- Restart Lunar to load the new classes. No user world was opened or modified.
