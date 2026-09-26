# Hollow undead

Three skeletal night mobs that also form the Hollow Necromancer's raised army.
Code lives in `entity/undead/`; the Necromancer's binding rules are in
[Necromancer combat](necromancer-combat.md).

| Mob | Role | Health / damage | Night spawn (weight, group) | Drops |
| --- | --- | --- | --- | --- |
| Hollow Crawler | Legless fodder; drags itself along, rears and rakes with a claw | 14 / 3 | 45, 2–4 | Rotten flesh 0–2, bone 0–1 |
| Risen Archer | Holds at about 12 blocks and fires a real arrow | 18 / arrow | 25, 1–2 | Bone 0–2, arrow 0–2, rare bow |
| Hunched Brute | Rare heavy with a spiked maul; long windup, one crushing swing | 40 / 10, armor 4 | 8, 1 | Bone 1–3, iron nugget 0–3, rare iron ingot |

Drops scale with Looting. Wild mobs give normal experience (the brute gives 10).

## Spawning and daylight

They join the Overworld monster pool wherever vanilla zombies spawn, under the
standard darkness rule, so they share the monster cap rather than adding to it.
Like zombies, that includes dark caves. Wild bodies burn in daylight; Necromancer-bound
bodies do not. All three are tagged `#minecraft:undead` and `#minecraft:skeletons`
(Smite, inverted healing/harm, wolves).

## Clips and combat timing

Each mob has `idle`, `walk`, `rise`, `attack` and `death` clips. A rise plays when a
body spawns (except chunk generation) or is raised by the Necromancer. The body
stands on the floor and cannot act until the clip ends. Goal controls are disabled
rather than NoAI, so a body saved mid-rise reloads active.

The attack goal chases, then plants and plays the attack clip. The effect lands on
the clip's hit frame, taken from `HollowUndeadClips`:

- Crawler: tick 12, when the rake meets the target, with a small hop forward.
- Archer: tick 52, when the string releases. The model draws side-on, so the body
  turns its left shoulder toward the target while drawing. The model's own arrow
  hides at release and a real arrow leaves the bow hand.
- Brute: tick 43. It tracks the target for the first 30 ticks, then commits. Everything
  near the maul head is hit.

The death clip holds the corpse until it settles, instead of vanilla's one-second
sideways tip. Hurt feedback is the standard red flash.

Walk clips were authored at slow shuffling speeds (about 0.13–0.16 blocks/s). The
controller plays the walk at the body's travel speed, clamped to 0.8–4×. It
accumulates time per frame so speed changes never jump the pose. At full chase
speed the feet still slide somewhat.

## Assets

`art/hollow_necromancer/undead/build_runtime.py` installs the approved review
candidates. GeckoLib mirrors X on geometry and positions and swaps each face's U;
the exporter pre-mirrors both so the game draws exactly what the review viewer showed.
`--check` re-proves that by comparing every textured quad across sampled poses of
every clip. The same script removes walk root drift and generates the timing and
socket constants in `HollowUndeadClips.java`.
