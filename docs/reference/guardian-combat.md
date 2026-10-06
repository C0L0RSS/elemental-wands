# Guardian combat

The Fractured Guardian is an optional cooperative boss. Read
[church and arena](church-and-arena.md) for survival activation and recovery.
Java owners below are under `src/main/java/com/anton/elementalwands/`.

## Encounter intent

The Guardian is the first shardbearer: a slow, heavy brawler meant to take a new duo in full
iron about ten tries on Normal (see the [roadmap](../ROADMAP.md)). A winning run should last
about four minutes. `entity/GuardianBossCombat` owns a single action director with earned
recovery, threat-aware targeting, cover checks, and bounded movement. Do not introduce competing
vanilla target goals. Creative/Spectator players and allies are excluded from normal combat;
Peaceful does not start an automatic fight.

Health is 1,400 plus 1,000 per extra participant (`GuardianShellRules`), grows when players join
and does not shrink when they leave. There are no damage multipliers: every hit lands at full
strength, apart from the burst cap that softens single hits above 40.

### The shell and the break

Phase one wears down a stone shell. The three crack textures follow progress toward half
health (from 87.5%, 75% and 62.5%), so players can see the break coming. Health cannot fall
below half until the shell breaks, so burst cannot skip the break. Once at half health, the
break waits for the current action to finish and for the Guardian to stand on the ground. A leap
lands first.

The break is the phase change (`GuardianPhaseRules`, 64 ticks, using the `phase_change` clip).
The Guardian cannot be damaged at any point during it:

1. For 1.6 seconds it braces while the cracks spark and the stone grinds. This is the cue to back away.
2. At tick 32 the shell bursts: 10 damage and knockback within 6 blocks, blocked by cover.
3. The ribs swing open over 24 ticks and it roars.

The ribs and the exposed core then stay open for the rest of the fight. The core settles to a
tremor that the core pulse stirs up again. Phase two adds unstable magic, faster throws, triple
slams and throws, wider waves, the leap's follow-up slam and the core pulse. It gives no damage
bonus: phase two is where the Guardian gets meaner. Preserve telegraphs when tuning difficulty.
Change health only from fight-log evidence, not to fix rhythm.

### Damage

Raw values, before armor. Full iron on Normal is the reference: light hits take about 15% of
health and heavy hits about 45%.

| Attack | Raw | Notes |
| --- | --- | --- |
| Slam/shockwave ground wave | 6 | Full circle; jumpable |
| Slam fists | 14 | Its front half within 5 blocks, plus anywhere under its body; those struck are spared the same slam's wave |
| Beam | 14 | |
| Rock, direct / splash | 13 / 6 | Splash within 3 blocks |
| Fan or back-volley stone | 7 | One hit per player per volley |
| Leap landing | 18 | Within 3 blocks; outside that the landing wave (6, jumpable) is the hit |
| Shell burst | 10 | Once, at the break |
| Core pulse | 16 | Within 6 blocks |

All of these are mob or thrown damage, so vanilla's difficulty scaling applies on top: Easy
roughly halves them and Hard multiplies them by 1.5.

### Intensity, co-op and targeting

The world's difficulty is the intensity setting (`GuardianCombatRules`):

| Difficulty | Damage | Rest between attacks | Cooldowns | Delayed slam chance |
| --- | --- | --- | --- | --- |
| Easy | Vanilla's reduction | ×1.4 | ×1.15 | 21% |
| Normal | As listed | ×1 | ×1 | 35% |
| Hard | ×1.5 | ×0.75 | ×0.9 | 56% |

Co-op pressure grows with each player up to four (`PRESSURE_CAP`); larger parties add only
health. Rest between attacks scales ×1, 0.7, 0.55 and 0.45 for one to four players, and
cooldowns ×1, 0.85, 0.75 and 0.7. The rest is the recovery gap plus the short walk before the
next choice, about 1.6 seconds solo. Wind-ups never get faster.

- **Back volley.** In co-op, when it starts a slam, shockwave, throw or beam, stones lift off
  its back for 0.8 seconds with sparks. Then they fly at up to three of the players it is
  not attacking, three stones each, with a short lead on their walking. The volley has a
  five-second cooldown (scaled), is blocked by cover, and never fires in a solo fight or a
  one-shot rehearsal (`GuardianVolleyRules`).
- **Targeting.** No player goes ten seconds without being attacked. Apart from that, it picks
  the highest priority. Priority is recent damage dealt to it (fading over about five
  seconds), plus 15 for standing still for 15 ticks, plus 4 per second since that player was
  last attacked. Distance breaks ties, so with equal threat it still rotates through the party.
- Each repeat of a phase-two triple slam or throw, and each later burst of the fan, turns to a
  different eligible player when one exists.

### Delayed slams and the core pulse

An ordinary slam (not the fast first two of a triple) sometimes holds its fists overhead
for 10, 16 or 22 ticks before smashing, so jumping its wave in rhythm stops working. Each
hold has its own clip (`slam_hold_<ticks>`), made by inserting the hold at the slam clip's
1.075-second apex. The server's impact moves by the same hold.

The core pulse (`GuardianPulseRules`) is phase two only, first about eight seconds after the
break and then on a 20-second cooldown (scaled). It needs a visible player within 12 blocks
and takes priority over everything except a due beam.

1. For 0.5 seconds it plants and the ribs flare.
2. For 2 seconds its core drags every player within 12 blocks toward it. The pull is a per-tick
   impulse sent to each pulled client (`GuardianPullPayload`) and added to the player's own
   movement, so sprinting escapes, walking barely does, and standing still drags a player about
   four blocks a second. In the air the pull is cut to the same share as air control. Solid cover
   between a player and the core anchors them. Debris streams inward and a cyan ring marks the
   blast radius.
3. At tick 50 it detonates: 16 damage and heavy knockback within 6 blocks, blocked by cover.

The standing slam remains a full circular wave by explicit design choice.
Waves follow terrain and can be jumped. Small Nature growth does not provide
cover, while Stone Wall and Nature ultimate trees do. A wall absorbs the current
hit before breaking; preserve same-attack protection for delayed volley contacts.
Server damage and client surfaces use shared cover rules. Astral Doubles are
one-hit summons: local slams, traveling waves, beams, rocks and shards destroy
them on contact using the attack's cover rules. They do not stop Guardian rocks
or shards and do not change the boss's player-target selection.

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
| `GuardianCombatRules`, `GuardianPhaseRules`, `GuardianShellRules` | Shared mechanics, damage, pacing, targeting and health |
| `GuardianPulseRules`, `GuardianVolleyRules` | Core pulse timing and pull; back-volley sockets and pacing |
| `GuardianFightLog` (on the shared `BossFightLog`) | Operator fight log |
| `GuardianBeamAttack`, `GuardianLeapAttack`, `GuardianLeapRules` | Committed beam and leap |
| `GuardianFanRules`, `GuardianRockEntity` | Projectile timing, cover and hit contracts |
| `client/renderer/` and `client/model/` Guardian classes | Presentation driven by tracked state |
| `GuardianThrowSocket` and its exporter | Matching animated hand and server release socket |

All listed Guardian rule/attack classes above are in `entity/` unless a package
is shown. Inspect the actual owner before changing a timing constant.

Editable body geometry/texture are in `art/fractured_guardian/v2-textured/`.
The original six clips are in `v4-expressive/`; `v5-leap/build_leap.py` carries
those forward and adds leap phases. The runtime exporter also merges `arrival/`,
and `phase/` animation sources. `phase/build_phase.py` authors the phase change, fast
slam/throw, fan, delayed slams and core pulse; `phase/preview_phase.py` renders a pose sheet
and offline viewer into `.local-previews/guardian-phase/`. Read `tools/prepare_guardian_assets.py`
before changing clips; do not assume an old report's clip count is current.
Regenerate downstream sources when their inputs change, then use exporter checks.

Keep authored impact times aligned with server clocks and actual bone transforms.
The held rock and release socket must agree under body yaw and pose. Head pitch
layers over authored mouth-pivot compensation. The burning overlay stays short
near the legs to preserve attack visibility; it does not change fire damage.

## Rehearsal and verification

Operator commands include `/ew guardian fight`, `stop`, `status`, `intro` (the nave's opening
cinematic, played on the nearest Guardian turned to face you), and one-shot
`beam`, `rock`, `shockwave`, `melee`, `leap`, `fan`, `pulse`. One-shot rehearsals leave the
boss passive, use standard timing (no delayed slam, no back volley) and refuse a Guardian in a
nave fight; use `/ew nave` for those (see
[church and arena](church-and-arena.md#the-shattered-nave)). Use disposable test worlds.
`/summon elementalwands:fractured_guardian ~ ~ ~10` creates a test boss. Vanilla
`/damage` reaches the half-health gate quickly to rehearse the break in a real fight.

`/ew guardian log on|off` is a tuning aid shared with the Necromancer (`BossFightLog`).
Fights that start while it is on record the party size, difficulty and health, every attack
and its target (including delayed slam holds and back volleys), the break and phase two, and
each hit on a player before and after armor or lost to hit immunity. They also record the
damage each player deals and who fell to what. At the end, operators see a summary and the
full record is written to `logs/guardian-fight-<time>.txt`.

The build includes geometry/timing contracts and socket comparisons; real-server
fixtures exercise damage/lifecycle. Native screenshots test appearance separately.
Neither is a substitute for human multiplayer timing and balance feedback.
See [testing](testing.md) and [current status](../STATUS.md).

Gravity Well's pull and collapse impulse do not displace the Guardian. Its
collapse still deals normally attributed Space damage through the Guardian's
existing damage rules, with arena eligibility and solid cover checked first.
