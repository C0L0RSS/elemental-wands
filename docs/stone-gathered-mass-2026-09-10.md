# Stone primary — Gathered Mass

The user approved replacing ground spikes with a gathered, overhead rock cluster,
keeping Stone Wall and Titan Dome unchanged. Small shots recover in 1.5 seconds;
charged throws recover in 2.5 seconds. Large hits briefly stagger ordinary targets.

## Controls and first-playtest tuning

Right-click the top of nearby solid ground while aiming downward to gather.
The ray must point down by at least about 20 degrees and hit a top face within
4.5 blocks of the eye. Each accepted pull adds 25 material, at most once every
12 ticks. Gathering respects the shared global cooldown and entangle slowdown.
An enemy under the crosshair takes priority over the floor behind it, so downward
aiming in close combat still fires. The ground is sampled but never broken or
replaced, including in the Guardian arena.
Nearby reachable submerged solid ground is usable; water itself is not material.

Right-click elsewhere to throw the current reserve. With no reserve, fire a
small custom rock from eye height. Charged throws release from overhead toward
the point under the crosshair. They travel with a slight ballistic drop, do not
home, penetrate neither walls nor ceilings, and work through water. Range is
36 blocks. Collision sweeps the centre and corners, tests initial solid overlap,
and selects the first target before cover. Low vegetation (grass, flowers, ferns,
azalea bushes, moss carpet and lily pads) is ignored by aiming, swept flight and
initial overlap checks. Plants are not destroyed; logs, leaves, fences, slabs and
Stone Wall remain cover. Hits are direct only, with no splash.

| Stored mass | Base damage | Recovery | Weight | Stagger |
| --- | --- | --- | --- | --- |
| 0, small shot | 5 | 1.5s | none | no |
| 25 | 14 | 2.5s | 6.25% slower | no |
| 50 | 20 | 2.5s | 12.5% slower | no |
| 75 | 26 | 2.5s | 18.75% slower | 0.5s |
| 100 | 32 | 2.5s | 25% slower | 0.5s |

Post-mitigation health/absorption damage sheds three material per damage point,
rounded upward, minimum 5 and maximum 30 per accepted erosion event. Ten ticks
of chip grace prevent rapid ticks from stripping multiple batches immediately.
Damage cannot remove the final material point; death still clears the reserve.
The orb does not intercept or absorb damage. Weight is its own temporary movement
attribute modifier and is removed immediately on throw or cleanup. Reserve and
weight remain while switching items, allowing defensive equipment use without
letting a slot switch remove the weight. The reserve is not saved across sessions.

Heavy hits stop sprinting for ten ticks on both server and tracking clients,
knock the victim away, and push airborne targets downward. Player gliding stops
on impact. This is not a casting/input stun and does not strip potion effects.
Knockback resistance affects the impulse. The Guardian retains authored movement
and attacks, while taking normal guarded/exposed damage from the projectile.
Teammates, caster-owned pets, creative players and spectators are not targets.

The HUD displays stored mass, stagger readiness, and the actual last shot's
cooldown. Emptying the cluster or swapping wands cannot switch a heavy shot's
remaining recovery to the shorter small-shot cooldown. Gathering is unavailable
during shot recovery. Shift-right-click and X retain the existing secondary/ultimate.

## Model and code

- `art/stone/cluster/preview.html`: interactive preview using the production mesh
  and texture; served only from this preview folder at localhost port 8341.
- `art/stone/cluster/stone_cluster.obj`: editable standard mesh export.
- `tools/build_stone_cluster_model.py`: authored 18-fragment, 468-face beveled mesh.
- `models/entity/stone_cluster.json` under the mod assets is the runtime source.
- `StoneClusterRenderer`: one batched submission, gathering motion, smooth growth/
  shrink and tumbling flight. The small projectile reuses the core piece. All
  settled visual geometry is inside the authoritative collision radius.
- `StoneClusterManager` owns player reserve, shot recovery, weight and cleanup.
- `StoneClusterEntity` owns tracked presentation and authoritative flight/impact.
- `StoneDamageMixin` measures actual lost health/absorption; `StoneStaggerMixin`
  blocks sprint reactivation until its transient per-entity expiry.
- The primary icon now depicts the actual cluster mesh. Existing 41-Stone/298-total
  PNG counts remain unchanged; the cluster reuses the approved Stone material.

The old Tectonic scheduler is removed. Its spell block/assets remain registered
for resource compatibility and shared Stone presentation; Stone primary no longer
places those blocks.

## September 10 vegetation follow-up

The user approved the new ability/model after playing, but reported shots snagging
on plants at the cluster edges. A real-server regression reproduced premature
impact on azalea with the original collision code. One vegetation policy now
filters the aim ray, travel rays and initial overlap, including plants with
non-empty vanilla collision shapes. Damage, cooldowns, size and visuals are unchanged.

The updated fixture passes 40 vegetation cases: ten plant types, small/full shots,
and travel/initial-overlap setups; full shots graze plants with their outer edge.
Six cover controls retain stone, logs, leaves, slabs, fences and Stone Wall.
The complete existing Stone gameplay suite also passed. In-world confirmation of
this specific vegetation fix remains pending.

## Verification

Real dedicated-server fixture passed: gathering, spam rejection, actual health
erosion, damage grace, minimum reserve, full-charge weight, terrain preservation,
spending reserve, heavy recovery after emptying, full damage, airborne knockdown,
sprint suppression/expiry, small-shot recovery, water flight and exact damage,
elevated targets, solid walls, embedded overhead release, cleanup and actual death.
The optional runner is `tools/stone_cluster_smoke.init.gradle`; it uses an isolated
build-directory world and the already accepted local Minecraft EULA.

The actual Minecraft client asset fixture passed cluster submissions into real
vertex buffers at six charge levels and three gathering ages, checking finite
coordinates, bounded vertex counts, one submission and collision-radius bounds.
It opens no world. Its runner is the existing `guardian_floor_client_smoke` with
the new Stone mesh check, using the existing Lunar asset cache.

The final clean build, VFX/Guardian/socket validators, whitespace and JAR integrity
checks passed. The packaged mesh, icon, mixin configuration and new runtime classes
were verified; smoke fixtures and art previews are excluded from the release JAR.
These checks are not a human PvP/boss balance test or in-world visual approval.

## Installed build

Vegetation fix installed September 10, 2026 at 11:33 local time. Source and installed SHA-256:
`bd2b6bb6d98f4808ce95079f1d5989fe9971c5b93ea281877dea77f12bb1305b`.

- Pre-vegetation-fix backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-vegetation-fix-20260910-113334.jar`
- Lunar path: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup of pre-Gathered-Mass gameplay: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-gathered-mass-20260910-110629.jar`
- Intermediate build backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-gather-aim-fix-20260910-110858.jar`

Restart Minecraft through Lunar. Suggested first playtest: four floor pulls,
move at full weight, take a hit, rebuild, throw at a grounded/elevated/swimming
target, then try small shots. Check the overhead silhouette in third person and
confirm that the wall/ultimate still provide useful gathering space. Numerical
damage and stagger values are an initial tuning pass, not final PvP balance.
