# Guardian guard and party balance — September 9, 2026

The Guardian now has separate main-health and stone-guard pools. Only main health
has a boss bar. Three material stages show guard cracks at 75%, 50%, and 25%.
Breaking guard interrupts grounded attacks, opens the chest plates, projects and
shakes the glowing core, then visibly closes/reforms the plates. A break during a
leap waits for the leap to finish safely. No ultimate is disabled.

## Initial tuning

| Players | Main health | Guard |
| --- | ---: | ---: |
| 1 | 600 | 120 |
| 2 | 1,050 | 210 |
| 3 | 1,500 | 300 |
| 4 | 1,950 | 390 |
| 5 | 2,400 | 480 |

Both continue scaling through 64 players rather than stopping at the former
five-player cap. Main health adds 450 per extra player and guard adds 90. Neither
shrinks after elimination or departure. Late joins preserve damage already dealt
and the remaining guard fraction; they cannot close an earned exposure window.
A Guardian-only attribute override allows health above vanilla's 1,024 ceiling.
Other entities retain their usual maximum-health behavior.

Accepted damage up to 40 retains its size; the portion above 40 contributes 35%.
That impact wears down guard and deals 40% main damage while guarded. Opening takes
24 ticks (1.2 s), full exposure lasts 140 ticks (7 s), and closing takes 30 ticks
(1.5 s). Main damage smoothly follows visible openness up to 150%, then returns to
40%. Guard refills only at the end; main health does not regenerate with guard.
The exposed body accepts damage throughout—no tiny core-only collision target.
All offensive hits contribute to guard; ordinary weapons can break it too.

Burst normalization precedes vanilla stronger-hit differences, so rapidly increasing
hits cannot bypass the damage curve. Pool changes happen only on the accepted
damage path, after Fabric admission vetoes and vanilla hurt-frame acceptance. /kill and out-of-world cleanup bypass
it. Duplicate-hit protection lasts ten ticks per attacker, so teammates can land
simultaneous ultimates without one person's hit swallowing everybody else's.
The same attacker's repeated hits retain vanilla stronger-hit/difference behavior.
Spells retain their ordinary damage against other entities.

These are starting balance values, not a claim of final difficulty. In a controlled
bedrock-floor server fixture, an actual falling Fire meteor into an exposed core
removed about 212 HP (including its impact/explosion/burning sequence). Five
simultaneous actual meteors into guarded armor removed about 282 HP from the
2,400-HP boss. Exact results depend on distance, exposure, cover, and timing.
Other ultimate combinations and human fight duration still need playtesting.

## Art and preview

- `art/fractured_guardian/guard/build_guard.py`: separately authored stagger clip.
- `guard.animation.json`: runtime body stagger; original eleven clips are preserved.
- Ribs/core use tracked server time in `FracturedGuardianModel`, so visual openness
  matches damage and late trackers/reloads can display the correct opening state.
- `tools/prepare_guardian_assets.py`: exports three branching crack material/glow
  variants while preserving original model geometry, texture, and magic tiles.
- `art/fractured_guardian/guard/preview_guard.py`: actual rig/material pose sheet and
  self-contained browser preview; includes the same chest/core transform curves.
- `art/fractured_guardian/guard/guard-review.png`: intact, cracked, opening, exposed,
  and closing poses. Preview textures show heavy cracks during the animation; in
  Minecraft those cracks fade in three stages during closing.
- Local preview: http://127.0.0.1:8330/preview.html (loopback only).

The 132-cube/38-bone source model and all approved attack/arrival animations remain
unchanged. No church architecture, rewards, arena dimensions, or player wand assets
were redesigned.

## Verification

- Build contracts: party scaling, direct-burst envelope, ordinary hits, fracture
  thresholds, exposure timing; existing beam/leap/socket/Nature checks remain.
- `tools/guardian_guard_smoke.init.gradle`: actual accepted/rejected hits, five-player
  health above 1,024, preserved damage on join, no shrink after spectator elimination,
  exposed and closing damage, beam cancellation, guard refill/AI recovery, actual
  solo and simultaneous five-caster meteors, saved health/guard/exposure roundtrip,
  pending airborne guard break and safe landing, administrative kill.
- Existing arena runtime fixture: formation/arrival, spectators, victory, full wipe,
  inventory mode/ground returns, and interrupted encounter. Recovery fixture verifies
  offline spectator return and cleanup after restart.
- Actual client asset fixture: GeckoLib bakes guard_break, loads six crack textures,
  and evaluates production rib rotation/core travel/scale at seven cycle times.
  Existing floor mesh, socket, heart, and arrival checks also pass. No world opened.
- VFX validators, church export drift, approved throw socket, clean build, whitespace
  check, and JAR integrity required before install.

The first guard fixture attempt incorrectly used the passive review command to
start a combat interruption; its setup was corrected to initiate the attack inside
an active encounter. It then passed. No live user save was opened or modified.

Evidence: `/private/tmp/guardian-guard-verification-20260909/`.
Installed in Lunar after the clean build and checks passed.

- Source/installed SHA-256: `1e9fd13cf400853e5fc14371f33030a5c0d6df13b43acd43ea9b2c3adc5893e2`.
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-guard-20260909-184628.jar`.
- Actual replaced JAR SHA-256: `80265ecc4d23218ce93b89b831ab41519d73350bdad770a6044292b487f6f1f1`.
- Restart Lunar to load this build. No user world was edited by the test fixtures.

