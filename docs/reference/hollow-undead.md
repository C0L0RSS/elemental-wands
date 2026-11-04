# Hollow undead

Three skeletal night mobs that also form the Hollow Necromancer's siege waves.
Code lives in `entity/undead/`; the Necromancer's binding rules are in
[Necromancer combat](necromancer-combat.md).

| Mob | Role | Health / damage | Night spawn (weight, group) | Drops |
| --- | --- | --- | --- | --- |
| Hollow Crawler | Legless fodder; drags itself along, rears and rakes with a claw | 14 / 3 | 45, 2–4 | Rotten flesh 0–2, bone 0–1 |
| Risen Archer | Holds at about 12 blocks and fires a real arrow | 18 / arrow | 25, 1–2 | Bone 0–2, arrow 0–2 |
| Hunched Brute | Rare heavy with a spiked maul; a charging slam: strides in heaving it overhead, then bounds and slams | 40 / 10, armor 4 | 8, 1 | Bone 1–3, iron nugget 0–3, rare iron ingot |

Drops scale with Looting. Wild mobs give normal experience (the brute gives 10).

## Spawning and daylight

They join the Overworld monster pool wherever vanilla zombies spawn, under the
standard darkness rule, so they share the monster cap rather than adding to it.
Like zombies, that includes dark caves. Wild bodies burn in daylight; Necromancer-bound
bodies do not. All three are tagged `#minecraft:undead` and `#minecraft:skeletons`
(Smite, inverted healing/harm, wolves).

## Clips and combat timing

Each mob has `idle`, `walk`, `rise`, `attack` and `death` clips. A rise plays when a
body spawns (except chunk generation) or rises in a Necromancer siege wave: it claws
its way out of the ground and ends standing in its idle pose. The body
stands on the floor and cannot act until the clip ends. Goal controls are disabled
rather than NoAI, so a body saved mid-rise reloads active.

The attack goal chases, then plants and plays the attack clip. The effect lands on
the clip's hit frame, taken from `HollowUndeadClips`:

- Crawler: tick 12, when the rake meets the target, with a small hop forward (tick 9).
  The clip shoves off into the hop and its claws rake back through the dirt while the
  hop slides the body, so they stay put.
- Archer: tick 52, when the string releases. The model draws side-on, so the body
  turns its left shoulder toward the target while drawing; it pulls the arrow from its
  quiver and draws to the jaw. The model's own arrow hides at release and a real arrow
  leaves the bow hand.
- Brute: tick 28 (1.4 s), a charging slam that starts within 4.5 blocks of its target.
  It stomps in on two strides while heaving the maul overhead and turning to track the
  target (ticks 0–15, about 1.2 blocks), crouches and commits on tick 15, then bounds and
  lands on tick 25 with the maul coming down. The bound is stretched or shortened (up to
  3 blocks) so the maul head lands where the target stood at the commit, so a late
  sidestep or step back beats it; the strides never carry the maul head past the target.
  The blow passes the hit point on tick 28 and buries the maul head in the ground just
  after. It stays hunched over the maul, its opening, and can act again on tick 55.
  Everything near the maul head is hit. The clip's travel is stripped at install and
  exported per tick (`BRUTE_CARRY`), which the server replays, so the feet stay planted.
  It only starts the slam with floor under the way (at most a one-block drop) and room
  for its body (a normal step up); otherwise it keeps walking. A hit, push or spell that
  moves it during the charge knocks it out of the charge, and the maul comes down
  wherever it ends up.

The death clip holds the corpse until it settles, instead of vanilla's one-second
sideways tip: the crawler scrabbles and goes limp, the archer crumples and drops its bow,
the brute falls forward like a tree. Hurt feedback is the standard red flash.

Bodies the Necromancer raises in a siege are quickened (a speed modifier added when
they are bound): crawlers +68% (about 4.4 blocks a second), brutes +15%, archers +10%.
The walk still plays at travel speed, inside the playback clamp; a siege crawler's crawl
runs at about 2.9x.

Walk clips are authored at about chase speed (crawler 1.5, archer 1.8, brute
1.67 blocks/s; `*_STRIDE` in `HollowUndeadClips`). The controller plays the walk at
travel speed divided by that stride, clamped to 0.2–3×. A wander plays at about 0.65×,
so planted feet and claws stay put at both speeds. It accumulates time per frame, so
speed changes never make the pose jump.

## Assets

`art/hollow_necromancer/undead/build_runtime.py` installs the approved review
candidates. GeckoLib mirrors X on geometry and positions and swaps each face's U;
the exporter pre-mirrors both so the game draws exactly what the review viewer showed.
`--check` re-proves that by comparing every textured quad across sampled poses of
every clip. The same script removes walk root drift and generates the timing and
socket constants in `HollowUndeadClips.java`.
