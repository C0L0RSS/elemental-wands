> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

# Fire secondary tuning — September 14, 2026

Player feedback requested stronger Flashover, explosion particles, smoother thrown
bombs, 0.15-second staggered blasts, independent bomb recovery, and a 60-block leap.

## Behavior

Flashover now deals 12 damage on the first blast contacting a victim, 8 on the
second, and 4 on the third (24 maximum). A full-health unarmored creeper survives
one bomb and dies to two. This follows player feedback that the initial 20+4+4
tuning was too strong. The existing four-block radius, cover, allied protection,
armor/shields and invulnerability rules remain. Each blast uses vanilla explosion
emitter particles plus a small ember burst. It does not destroy terrain or set fires.
Blasts occur at 0 / 3 / 6 ticks (0 / 0.15 / 0.30 seconds at normal tick rate).

There are three independent bomb slots. Throwing reserves one until that bomb
leaves play. An exploded bomb recovers six seconds after its own explosion;
disarming, expiry, water, lost hosts/support, unloading or lifecycle dismissal
recover that slot after two seconds. A ready spare can be thrown while others
recover, subject to the existing one-second spacing between throws. A new armed
bomb can detonate during other slots' recovery. An ongoing three-bomb sequence
still finishes before another sequence starts.

Each slot persists its entity reservation and recovery timestamp/duration on the
player, independently of the wand stack. Unsaved bomb entities become two-second
lost recoveries when an orphaned reservation is encountered after reload. An old
six-second global recovery migrates onto one slot. Entangle retains its existing
cooldown slowing. The HUD shows ready/placed/armed/countdown state for each slot.

The entity now uses Minecraft's position interpolator across two ticks and the
existing per-frame model animation. Server updates already occurred each tick;
previously the client snapped between updates. Host attachments continue sampling
the host's interpolated pose. Network jitter and overall frame rate still depend
on the client/server environment.

Fire Leap's horizontal cap increases from 12 to 60 blocks, and its targeting ray
now reaches that distance. Its one-second flight, 4.5-block arc, six-second cooldown,
five-block landing wave, damage, elevation limits and full-body collision checks
remain. It requires a visible supported destination and loaded, clear route.

## Verification

Initial release checks (before the damage-only follow-up):

- `fire_tuning_server_smoke`: lethal direct creeper hit; independent slot recovery,
  spare throws/detonations, loss penalty, save/load and orphan handling, replacement
  wand, 60-block aim/full route/flight/landing and existing leap safety cases.
- `sticky_flashover_server_smoke`: attachments to mobs/players, host movement,
  allies, cover, 0/3/6-tick timing, 20+4+4 per-victim damage, queued disarm and water.
- `flashover_server_smoke`: purchase/equip, arming/cap, cover/allies, disarming,
  recovery persistence, remote range, unequip cleanup and 30-second expiry.
- `sticky_flashover_client_smoke`: actual client interpolation samples, synchronized
  attachments, detonation and individual HUD timers, key rebinding and screenshots.
- `fire_leap_client_smoke`: near-60-block targeting/preview, cancel/release input,
  synchronized flight, landing and no fall damage.

Native screenshots in `docs/archive/evidence/fire-secondary-tuning-verification/` were inspected.
The spell description was shortened after visual review found overlapping text;
its second client run passed and the corrected screenshot was inspected.
The exact 60-block server flight and near-limit client flight both passed. Client
angles use Minecraft's approximate trigonometry, so the aim fixture targets just
inside the cap; the server fixture also validates an exact 60-block destination.
Human combat feel and multiplayer latency still need playtesting.

All seven required asset/export validators and `git diff --check` passed.
No raster assets changed; production count remains 266 PNGs and 40 particles.


`./gradlew clean build --offline`, its Guardian geometry/timing checks and JAR
integrity passed. The release was installed into Lunar Fabric 1.21.10.
Source and installed SHA-256:
`62f64882f6e4e2797eb415cdee6817bfd4f38361dd091fa57fd82e0d3d831292`.
Previous installation backed up in `.local-backups/lunar/20260914-130449/`.
Restart Lunar to load this build.

## Damage-only follow-up

Reduced damage to 12 / 20 / 24 cumulative. The focused real-server regression
passed: one bomb leaves a full-health creeper with 8 health; the second kills it.
Attachment, sequence timing, cover, allies and disarm checks also passed.
`./gradlew build --offline`, `git diff --check` and JAR integrity passed.
Installed in Lunar; matching SHA-256: `2505ea4443ec9c6c03dcda06faae0ffe88396e904b5ead6820a19601d5b2583f`.
Backup: `.local-backups/lunar/20260914-131051/`. Restart Lunar.
