# Guardian combat

The Fractured Guardian is an optional cooperative boss. Read
[church and arena](church-and-arena.md) for survival activation and recovery.
Java owners below are under `src/main/java/com/anton/elementalwands/`.

## Encounter intent

`entity/GuardianBossCombat` owns a single action director with earned recovery,
target rotation, cover checks, and bounded movement. Do not introduce competing
vanilla target goals. Creative/Spectator players and allies are excluded from
normal combat; Peaceful does not start an automatic fight.

Health scales with participants and does not shrink when players leave. Guard is
a separate pool shown by worn stone materials, not another boss bar. Breaking
it opens the ribs and exposes the core, granting a vulnerability window. A break
during a leap waits for safe landing. `GuardianGuardRules` owns scaling, damage
multipliers, crack stages and opening duration.

At the health threshold, phase two latches after the current action and any earned
opening. It adds unstable magic, faster pressure, and mixed/repeated attacks.
`GuardianPhaseRules` owns the shared phase clock. Preserve earned recovery and
telegraphs when tuning difficulty; do not silently raise health to change rhythm.

The standing slam remains a full circular wave by explicit design choice.
Waves follow terrain and can be jumped. Small Nature growth does not provide
cover, while Stone Wall and Nature ultimate trees do. A wall absorbs the current
hit before breaking; preserve same-attack protection for delayed volley contacts.
Server damage and client surfaces use shared cover rules.

The beam tracks, commits, and pulses; each victim is consumed once per cast,
including a shielded attempt. Rock throws and floating-stone bursts commit aim
before release. The current fan uses quick successive bursts, not the original
five-stone historical volley. Sustained hovering can attract pressure; ordinary
jumping should remain counterplay. Ready beams receive selection priority after
other attacks so the move remains visible in a fight.

The leap uses collision-aware movement and a checked full-body arc. Ground support
must be checked physically because frozen windups can clear cached `onGround`.
Launch and landing waves have independent origins and victim accounting. Preserve
bounds, fluid/chunk checks, safe abort, and gravity restoration on reload.
External accumulated knockback must not float the boss; authored movement remains.

## Code and art ownership

| Owner | Responsibility |
| --- | --- |
| `entity/FracturedGuardianEntity`, `GuardianBossCombat` | Tracked state, lifecycle, action selection |
| `GuardianCombatRules`, `GuardianPhaseRules`, `GuardianGuardRules` | Shared mechanics and tuning |
| `GuardianBeamAttack`, `GuardianLeapAttack`, `GuardianLeapRules` | Committed beam and leap |
| `GuardianFanRules`, `GuardianRockEntity` | Projectile timing, cover and hit contracts |
| `client/renderer/` and `client/model/` Guardian classes | Presentation driven by tracked state |
| `GuardianThrowSocket` and its exporter | Matching animated hand and server release socket |

All listed Guardian rule/attack classes above are in `entity/` unless a package
is shown. Inspect the actual owner before changing a timing constant.

Editable body geometry/texture are in `art/fractured_guardian/v2-textured/`.
The original six clips are in `v4-expressive/`; `v5-leap/build_leap.py` carries
those forward and adds leap phases. The runtime exporter also merges `arrival/`,
`guard/`, and `phase/` animation sources. Read `tools/prepare_guardian_assets.py`
before changing clips; do not assume an old report's clip count is current.
Regenerate downstream sources when their inputs change, then use exporter checks.

Keep authored impact times aligned with server clocks and actual bone transforms.
The held rock and release socket must agree under body yaw and pose. Head pitch
layers over authored mouth-pivot compensation. The burning overlay stays short
near the legs to preserve attack visibility; it does not change fire damage.

## Rehearsal and verification

Operator commands include `/ew guardian fight`, `stop`, `status`, and one-shot
`beam`, `rock`, `shockwave`, `melee`, `leap`, `fan`. One-shot rehearsals leave the
boss passive. Arena restrictions still apply; use disposable test worlds.
`/summon elementalwands:fractured_guardian ~ ~ ~10` creates a test boss.

The build includes geometry/timing contracts and socket comparisons; real-server
fixtures exercise damage/lifecycle. Native screenshots test appearance separately.
Neither is a substitute for human multiplayer timing and balance feedback.
See [testing](testing.md) and [current status](../STATUS.md).
