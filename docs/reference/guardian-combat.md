# Guardian combat

The Fractured Guardian is an optional cooperative boss. Read
[church and arena](church-and-arena.md) for survival activation and recovery, and the
[design notes](../design/guardian.md) for why the fight is built this way and what is still
planned. Java owners below are under `src/main/java/com/anton/elementalwands/`.

## Encounter intent

Removing or unloading the Guardian closes its fight log and clears the boss bar
for everyone who was watching.

The Guardian is the first shardbearer: the slow, heavy one, meant to take a new duo in full
iron about ten tries on Normal (see the [roadmap](../ROADMAP.md)). A winning run should last
about four minutes. It is never hurried and it cannot catch a player, so every hit is an area
(fists, waves, landings, blasts), the leap is how it closes distance, and the Scar takes away
the ground a player runs on.
`entity/GuardianBossCombat` owns a single action director with earned recovery, threat-aware
targeting, cover checks, and bounded movement. Do not introduce competing vanilla target goals.
Creative/Spectator players and allies are excluded from normal combat; Peaceful does not start
an automatic fight.

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
3. It heaves through the burst, roars and settles back into its stance.

The chest stays shut and the core never comes out; it throbs inside the chest for the rest of
the fight and swells for the core pulse. Phase two adds unstable magic, faster throws, wider
waves, the core pulse, and longer and more frequent strings, in which a chained slam comes down
faster. It gives no damage bonus: phase two is where the Guardian gets meaner. Preserve
telegraphs when tuning difficulty. Change health only from fight-log evidence, not to fix rhythm.

### How it chooses

The Guardian is a rule system, like the Pyre King (`GuardianCombatRules.weights`). It first
picks who to turn on (see targeting below). Where that player stands decides which attacks it
weighs, what they are doing tilts the weights, and chance picks among them.

| Its target is | It weighs |
| --- | --- |
| Within 5 blocks, in front of it | The slam, and now and then the Sweep |
| Within 7 blocks, beside or behind it | The Sweep most, then the slam |
| 5 to 12 | The slam (for its wave) and, from 7 blocks, the Scar equally; the rock from 6 blocks |
| 12 to 30 | The leap most, then the Scar, the rock and the slam |
| Beyond 30 | The rock (to 48 blocks); phase two's wider wave reaches 44 |
| Between the Scar's walls | The leap from 12 blocks; nearer than that, the slam |
| Beyond every reach, or out of sight | Nothing: it walks at them |

What it reads off its target:

- **Launched** (off the ground and more than 2.5 blocks clear of the floor, so never a jump):
  the Airburst, at once.
- **Eating or drinking**: an attack at once, without finishing its walk; at range that is
  usually a rock.
- **Standing still** for 0.75 seconds, 6 to 48 blocks away: the same. Standing still beside
  it is not punished: that is the opening.
- **Kept away** (beyond 12 blocks for 5 seconds): more leaps and more Scars.
- **Beside or behind it** (more than 60 degrees round from where it faces, within 7 blocks):
  the Sweep. It is weighed the same way when that player is somebody other than its target.
- **Penned** (standing between the Scar's walls with more than 1.5 seconds of them left): the
  leap or the slam, and nothing else.
- **Jumped its last wave**: the next slam aimed at them is held (see below).

A player it has nothing for (beyond every reach, or with every attack locked out) is passed
over for the next one it can hit, so nobody can hold its attention from out of range.
It never uses the same attack three times running and seldom twice, unless it has only one.
Up close that means a third slam running is always a Sweep.
`GuardianBossCombat` can also lock an attack out for a while (`ready`): the core pulse and the
Airburst after each use, the Scar until its walls have fallen and five seconds more, and a
leap that found no landing.

**Strings.** A string is a run of attacks with no walk between them; each still plays its whole
recovery. After an attack it goes straight into another about a third of the time in phase one
(at most two) and most of the time in phase two (at most three). A leap that has just landed
goes on half the time in phase one and always in phase two, so the old follow-up slam is now
whatever suits where its target went. In co-op each attack of a string turns on a different
player when one is in sight. The Scar is the exception to both rules: it always goes straight
into another attack, even past the limit, and that attack is for whoever stands in the pen.

**The opening.** After a string it walks at its target for 1.25 seconds (solo, Normal) before
it picks again (`GuardianCombatRules.REST`). That, plus each clip's own recovery, is when to
hit it. It walks at the player it last turned on, not whoever is nearest, and stops 4 blocks
short. Wind-ups never get faster. Waking, the shell break and an interrupted attack are
followed by a short plain pause instead, which no punish cuts short.

### Damage

Raw values, before armor. Full iron on Normal is the reference. Nothing is a light hit: the
edge of an area takes about 30% of health, a direct hit about 45%, and the leap about two
thirds.

| Attack | Raw | Notes |
| --- | --- | --- |
| Slam's ground wave | 10 | Full circle, out to 32 blocks (44 in phase two); jumpable |
| Slam fists | 14 | Its front half within 5 blocks, plus anywhere under its body; those struck are spared the same slam's wave |
| Sweep | 14 | Everyone within 7 blocks outside the wedge in front of it; cannot be jumped |
| Scar's eruption | 14 | Whoever stands on a crack as its spike comes up; once per cast |
| Standing spikes | 10 | On touch, with a shove away; at most once a second per player |
| Rock, direct / splash | 13 / 10 | Splash within 3 blocks |
| Airburst | 10 | Everyone within 4 blocks of the burst; once per player per cast or cluster |
| Leap landing | 18 | Everyone inside the marked ring (4.5 blocks); outside it the landing wave is the hit |
| Shell burst | 10 | Once, at the break |
| Core pulse | 16 | Within 6 blocks |

All of these are mob or thrown damage, so vanilla's difficulty scaling applies on top: Easy
roughly halves them and Hard multiplies them by 1.5.

### Intensity, co-op and targeting

The world's difficulty is the intensity setting (`GuardianCombatRules`):

| Difficulty | Damage | Walk between strings | Lockouts | Strings | Held slam after a jumped wave |
| --- | --- | --- | --- | --- | --- |
| Easy | Vanilla's reduction | ×1.4 | ×1.15 | ×0.6 as often | 48% |
| Normal | As listed | ×1 | ×1 | As above | 80% |
| Hard | ×1.5 | ×0.75 | ×0.9 | ×1.3 as often | Always |

Co-op pressure grows with each player up to four (`PRESSURE_CAP`); larger parties add only
health. The walk between strings scales ×1, 0.7, 0.55 and 0.45 for one to four players, and
lockouts ×1, 0.85, 0.75 and 0.7.

- **Back stones.** In co-op, when it starts a slam or a throw, stones lift off its back
  for 0.8 seconds with sparks, three for each of up to three players it is not attacking.
  At a player on the ground an amber ring is marked where they are heading and the stones
  burst on it 1.3 seconds later; at a player in the air they fly straight to them. They have a
  five-second lockout (scaled), are blocked by cover, and never fire in a solo fight or a
  one-shot rehearsal (`GuardianVolleyRules`, `GuardianAirburstRules`).
- **Targeting.** No player goes ten seconds without being attacked. Apart from that, it picks
  the highest priority among the players it can see. Priority is recent damage dealt to it
  (fading over about five seconds), plus 15 for standing still for 15 ticks, plus 4 per second
  since that player was last attacked. Distance breaks ties, so with equal threat it still
  rotates through the party. A player it would punish comes before that, and one in the air
  before the rest.

### The slam, the leap and the Airburst

**The slam** is one attack at every range: fists in front of it and a ring wave outward. The
wave follows terrain and can be jumped; it is a full circle by explicit design choice. The
Guardian remembers, for each player, whether they jumped the last wave that reached them. Its
next slam aimed at a player who did holds its fists overhead for 10, 16 or 22 ticks before
smashing, so jumping in rhythm stops working; a player the wave struck gets an ordinary slam.
Each hold has its own clip (`slam_hold_<ticks>`), made by inserting the hold at the slam clip's
1.075-second apex. The server's impact moves by the same hold. In phase two a slam chained
inside a string uses the faster `slam_fast` clip unless it is held.

**The leap** (12 to 30 blocks) is its gap closer and its heaviest hit. It follows its target
through the wind-up and the first 0.7 seconds of the flight, leading their walk, then commits:
an amber ring 4.5 blocks in radius is marked on the floor for the last 1.1 seconds, and
everyone inside it when it lands takes the hit. Sprinting out of the ring works; walking from
its middle does not. A push-off wave leaves where it jumped from and a landing wave follows
the landing. The leap uses collision-aware movement and a checked full-body arc. Ground support
must be checked physically because frozen windups can clear cached `onGround`. In the air the
landing slides at most 0.7 blocks a tick, and only onto floor that passes the same landing and
route checks. Launch and landing waves have independent origins and victim accounting. Preserve
bounds, fluid/chunk checks, safe abort, and gravity restoration on reload. External accumulated
knockback must not float the boss; authored movement remains.

**The Airburst** replaces the old fan and back volley (`GuardianAirburstRules`). Its stones do
not need to strike a player: each flies to a point and bursts there, hurting everyone within 4
blocks who is not behind cover, or bursts early on whatever it touches. Stones released together
hurt a player once between them.

1. A launched player draws the cast at once. It plays the `fan` clip on `GuardianFanRules`
   timing: three bursts of three stones form over its head, the first released 0.9 seconds in
   and the others 0.4 seconds apart.
2. Each burst commits its aim 0.2 seconds before release, at where its target will be: their
   movement carried forward in all three axes, and their fall if they are falling. Changing
   direction in the air beats it.
3. A player caught in the air is knocked straight down.
4. Later bursts go to whoever is still in the air, spread across the party. With nobody up
   there, no more stones form and the clip plays out.

Ordinary jumping never draws it. The back stones above are the same stones.

### The Sweep and the Scar

**The Sweep** (`GuardianSweepRules`, the `sweep` clip, 3.2 seconds) is a turning backhand for
players beside or behind it. It is the only attack it does not turn to face first, so whoever
got behind it is still behind it when the arm comes round.

1. For 1.5 seconds it coils and looks back over its shoulder. An amber arc on the floor shows
   the reach, with the wedge in front of it left open.
2. Then one blow: 14 damage and a hard knock away for everyone within 7 blocks who is more than
   60 degrees round from straight ahead, or standing under its body. It reaches 3.5 blocks up,
   so jumping does not help. Cover blocks it.

The slam covers its front and the Sweep everything else, so the two wind-ups ask for opposite
answers: leave its front for a slam, get in front of it (or out of reach) for a Sweep. It is
weighed lightly even at a player in front of it, whom it cannot hit, because stepping aside
from what looks like another slam walks into it.

**The Scar** (`GuardianScarRules`, the `scar` clip, 2.5 seconds, 7 to 30 blocks) is not judged
as a hit. A sprinting player crosses any line on the floor before it can hurt them, so the Scar
pens them instead and the next attack is the hit.

1. For 1 second it rears up, turning with its target, then drives both hands into the floor.
2. That commits it. Two amber cracks race outward along the floor, 4 blocks either side of the
   line from it through where its target will be half a second later (their walk carried
   forward). They start 3 blocks out and run 10 blocks past the target, never further than 38.
3. Half a second later walls of stone spikes erupt along the cracks, from its hands outward over
   0.3 seconds: one spike per block, 4 blocks tall. Anyone standing on a crack takes 14.
4. The walls stand for 6 seconds, then crumble. Touching one costs 10 and a shove away, at most
   once a second.
5. It goes straight into another attack. For whoever stands in the pen that is the leap from 12
   to 30 blocks and the slam otherwise (also if the leap finds no landing), whoever else is
   near it. The leap's ring is 9 blocks across and the pen 7, so the way out of the ring is
   along the pen, not sideways. It counts as a pen only if both walls found floor to stand on
   to past its target; where one is cut short by the edge of the floor the walls still come
   up, but nobody is treated as penned.
6. In co-op, up to two players besides its target get one wall each, from the Guardian out past
   a point 3.5 blocks ahead of where they are heading (or beside them, on the side away from the
   pen, if they are standing still), so the party is cut apart. A one-shot rehearsal raises only
   the pen.

A sprint covers under 3 blocks in the half second between the cracks and the walls, so a
player on foot ends up in the pen whatever they do; leaving through an open end, or getting
over the top with a spell (which draws the Airburst), is the answer.

The spikes are entities (`GuardianSpikeEntity`), not blocks: nothing in the world is changed
and they are never saved. They are solid to players and to nothing else. The Guardian walks
and leaps through its own stone, and shots, spells, waves and lines of sight pass through, so
the walls are cover for nobody. They follow the floor, step over gaps and pillars, and end
where the floor does. An interrupted attack, the shell break and the end of the fight remove
them at once.

### The core pulse

The core pulse (`GuardianPulseRules`) is phase two only, first about eight seconds after the
break and then locked out for 16 seconds (scaled) after each one ends. It needs a visible
player within 12 blocks and, once ready, comes before everything except the Airburst.

1. For 0.5 seconds it plants and its core swells.
2. For 2 seconds its core drags every player within 12 blocks toward it. The pull is a per-tick
   impulse sent to each pulled client (`GuardianPullPayload`) and added to the player's own
   movement, so sprinting escapes, walking barely does, and standing still drags a player about
   four blocks a second. In the air the pull is cut to the same share as air control. Solid cover
   between a player and the core anchors them. Debris streams inward and a cyan ring marks the
   blast radius.
3. At tick 50 it detonates: 16 damage and heavy knockback within 6 blocks, blocked by cover.

Nothing is chained onto a core pulse.

### Cover, doubles and committed aim

Stone Wall and ordinary terrain provide cover; the Scar's spikes do not. (So would the trunk of
Nature's retired Overgrowth oak; no spell grows it now.) The Guardian has no behaviour written for one element.
A wall absorbs the current hit before breaking; preserve same-attack protection for later
stones of the same Airburst. Server damage and client surfaces use shared cover rules. Astral
Doubles are one-hit summons: local slams, traveling waves, beams, rocks and bursts destroy
them using the attack's cover rules. They do not stop Guardian rocks and do not change the
boss's player-target selection.

The beam is no longer chosen in a fight, since the Scar took its place, but it can still be
rehearsed: it tracks, commits, and pulses; each victim is consumed once per cast, including a
shielded attempt. Rock throws commit aim before release. Amber marks on the floor always mean
something is about to happen there: a ring for the leap or for bursting stones, an arc for the
Sweep, lines for the Scar's walls.

## Code and art ownership

| Owner | Responsibility |
| --- | --- |
| `entity/FracturedGuardianEntity`, `GuardianBossCombat` | Tracked state, lifecycle, action selection |
| `GuardianCombatRules`, `GuardianPhaseRules`, `GuardianShellRules` | The chooser, strings, damage, pacing, targeting and health |
| `GuardianPulseRules` | Core pulse timing and pull |
| `GuardianAirburstRules`, `GuardianFanRules`, `GuardianVolleyRules` | The Airburst: blast, aim and launch recognition; the cast's formation timing (the `fan` clip); back-stone sockets and pacing |
| `GuardianFightLog` (on the shared `BossFightLog`) | Operator fight log |
| `GuardianSweepRules` | The Sweep: timing, reach and the open wedge in front |
| `GuardianScarRules`, `GuardianSpikeEntity`, `client/renderer/GuardianSpikeRenderer` | The Scar: timing, where the pen and the co-op walls go, and each spike of a wall (drawn in code on the Stone spells' spike texture) |
| `GuardianBeamAttack`, `GuardianLeapAttack`, `GuardianLeapRules` | Committed beam (rehearsal only) and leap |
| `GuardianRockEntity` | The thrown rock and the Airburst's stones: flight, cover and hit contracts |
| `client/renderer/` and `client/model/` Guardian classes | Presentation driven by tracked state |
| `GuardianThrowSocket` and its exporter | Matching animated hand and server release socket |

All listed Guardian rule/attack classes above are in `entity/` unless a package
is shown. Inspect the actual owner before changing a timing constant.

Editable body geometry/texture are in `art/fractured_guardian/v2-textured/`.
The original six clips are in `v4-expressive/`; `v5-leap/build_leap.py` carries
those forward and adds leap phases. The runtime exporter also merges `arrival/`,
and `phase/` animation sources. `phase/build_phase.py` authors the phase change, fast
slam/throw, fan (the Airburst's cast), held slams, core pulse, sweep and scar;
`phase/preview_phase.py` renders pose sheets (`step-two-review.png` for the sweep and the scar)
and an offline viewer into `.local-previews/guardian-phase/`. The heavy performance pass
(`v4-expressive/heavy_performance.py` for awaken, slam and beam, which the fast and held slams
follow; `v4-expressive/heavy_locomotion.py` for idle and walk; `v5-leap/heavy_leap.py` for the
leap; `phase/heavy_casts.py` for fan, core pulse, sweep and scar; `phase/heavy_phase.py` for the phase change)
keeps every impact and release time, the walk's stride and the leap's arc, but gives each attack a
slower load, a planted brace and a long recovery; only the blow is quick. The throws are
unchanged because the server samples their held-rock pose through tick 44.
The sweep's arm points to its right at tick 29, straight behind at tick 30 and 60 degrees left
of front at tick 32; the scar's hands meet the floor at tick 20 and heave at tick 30
(`GuardianSweepRules`, `GuardianScarRules`; `combat-review/validate_motion.py` checks both).
`combat-review/build_preview.py` builds a review of all 20 clips into
`.local-previews/guardian-combat-review/`, with the reworked clips marked. Read
`tools/prepare_guardian_assets.py` before changing clips; do not assume an old report's clip
count is current.
Regenerate downstream sources when their inputs change, then use exporter checks.

Keep authored impact times aligned with server clocks and actual bone transforms.
The held rock and release socket must agree under body yaw and pose. Head pitch
layers over authored mouth-pivot compensation. The burning overlay stays short
near the legs to preserve attack visibility; it does not change fire damage.

## Rehearsal and verification

Operator commands include `/ew guardian fight`, `stop`, `status`, `intro` (the nave's opening
cinematic, played on the nearest Guardian turned to face you), and one-shot
`beam`, `rock`, `melee` (the slam), `sweep`, `leap`, `scar`, `airburst`, `pulse`. One-shot
rehearsals leave the boss passive, use standard timing (no held slam, no back stones, no
string or follow-up, and all three Airburst bursts whoever is in the air) and refuse a
Guardian in a
nave fight; use `/ew nave` for those (see
[church and arena](church-and-arena.md#the-shattered-nave)). Use disposable test worlds.
`/summon elementalwands:fractured_guardian ~ ~ ~10` creates a test boss. Vanilla
`/damage` reaches the half-health gate quickly to rehearse the break in a real fight. The
Sweep does not turn to whoever asked for it: stand beside or behind the boss to be hit.

`/ew guardian log on|off` is a tuning aid shared with the Necromancer (`BossFightLog`).
Fights that start while it is on record the party size, difficulty and health, every attack
and its target (including held and fast slams and back stones), the break and phase two, and
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
