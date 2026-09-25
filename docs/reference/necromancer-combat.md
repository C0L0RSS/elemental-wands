# Hollow Necromancer combat

The Hollow Necromancer is a second cooperative boss in progress. Operators test it
with `/summon` or in the Hollow Crypt realm (`/ew crypt`; see [Church and arena](church-and-arena.md#hollow-crypt-realm)).
The survival graveyard ritual that leads there is not built yet.
Java owners are under `src/main/java/com/anton/elementalwands/entity/necromancer/`.

## Encounter intent

A tall, lean, hooded caster that keeps its distance and wins through telegraphed
spells and a raised army. `NecromancerCombat` owns one committed cast at a time,
target rotation and movement; no vanilla goals compete with it. It backs away from
close players and closes on distant ones inside a leash around its starting point.
Creative/Spectator players are excluded and Peaceful stops the fight.

| Spell | Behavior |
| --- | --- |
| Soul bolt | Three slow homing skulls with glowing eyes and continuously chomping jaws. They turn a little each tick, so cover and a late sidestep both beat them. |
| Grasping hands | Six articulated skeletal claws rise around each of up to three marked players, with cyan soul seams and flexing fingers. Walking out of the fixed ring before 1.6 seconds avoids its damage and 1.5-second root. |
| Life drain | A channeled tether that damages and heals the boss, capped per channel. Losing line of sight or range breaks it. |
| Blink | When a player gets close it teleports away within the leash and leaves a Slowness/Wither curse patch. |
| Raise | Spectral skeletons and zombies climb out of the floor, never under a player, up to an army cap. |

Health scales with participants and never shrinks mid-fight. Rules and tuning live
in `NecromancerRules`, which the contract checks exercise without a world.

Minions are custom subclasses of vanilla undead (`SpectralSkeletonEntity`,
`SpectralZombieEntity`). They do not burn, convert, drop loot or save, are on the
caster's side for targeting and spell protection, cannot damage it, and dissolve
when it stops or is gone. `NecromancerMinion` owns the rise-from-the-floor effect.

Player spells treat the boss as a `WandBoss`, the shared interface that also
covers the Guardian: no roots, knockback, stagger or interrupts from wand spells.

## Phase two: the colossus

The robed form cannot drop below half health; burst damage stops there and
schedules the transformation, so the second phase is never skipped. It starts once
the boss stands on ground with headroom for the colossus. Under a low ceiling it
blinks to open ground first, and after ten seconds it transforms in place regardless.

During the eight-second, invulnerable transformation the mage arches backward and
opens its hood. The skeleton reaches through, plants its hands, and pulls its body
free. The discarded robe collapses onto the ground behind it and burns away while
the skeleton rears up, then settles onto all fours. Its minions freeze while it happens, including
ones that finish rising during the sequence. The hitbox grows at 4.5 seconds, nearby
players are pushed clear, and a roar at 7.1 seconds precedes the return to combat.
A save during the transformation reloads as the finished colossus. The colossus
keeps its phase if everyone leaves.

The colossus moves on all fours in a low diagonal crawl (steering, not pathfinding)
and never blinks. Its separate knuckles and three-part fingers form spread,
curled claws. One hand plants while the other reaches ahead and the opposite hind
leg pushes; even at rest the hands bear weight. It rears for a roar or grab, then
returns to the crawl. Its rush accelerates that four-limbed gait, then a contact
grab pulls the victim to its jaws for a bite and forward throw:

| Attack | Behavior |
| --- | --- |
| Swipe | A marked frontal arc; stepping out or jumping clears it. |
| Grab | An articulated hand lifts one player, then slams them for capped damage. The server follows a wrist socket sampled from the animation and holds the player without mounting, so the camera stays free. Team damage breaks the grip and staggers it. The same player is not grabbed again for 15 seconds. |
| Rush | A 0.6-second warning precedes a fast grounded run with limited steering. Contact grabs one player, bites once for 8 damage after 0.9 seconds, throws them forward at 1.4 seconds, and recovers by 2.2 seconds. Walls stop the run; sidestepping can make it miss. Teammate damage does not interrupt this sequence. |
| Spells | Larger bolt volleys, wider hands, the drain and a bigger army. When Hands successfully catches a player, the skeleton immediately rushes the nearest caught player without the normal rush warning, then uses the same bite and throw. Only one victim is pursued per cast. Escape the ring before it closes to avoid the combo. |

One entity and one combined model carry both forms, so health, the boss bar and saving
stay continuous; the model hides whichever body is inactive.

## Planned

A custom spectral elite raised by the colossus, further human art/animation review, and the
overworld graveyard with the ritual and fight lifecycle that lead into the crypt realm.

## Art and verification

Both forms use the authored V2 model generated by
`art/hollow_necromancer/v2/build_art.py`. `geometry.py` owns the layered mage and
open skeletal anatomy; `animations.py` owns the timing and poses. `rig.py` bakes
hand contacts and the server grab socket from the same transforms. The exporter
requires Pillow and NumPy. Default invocation writes the workshop candidate;
`--install` writes mod resources and the Java grab socket; `--check` compares all
five runtime outputs. Neither option installs a JAR into Lunar.

The earlier V0/V1 generators are historical sources and must not overwrite V2
runtime assets. Minion bodies remain vanilla textures with a pale tint.

Soul bolts use the authored skull from `art/hollow_necromancer/soul_bolt/build_preview.py`.
Its default invocation builds the local review page; `--install` exports the model,
texture, glow mask, animations and prepared wisp/shard particles into mod resources;
`--check` verifies their agreement with the source assets. `SoulBoltRenderer` faces the skull along its flight, including vertical aim.
The jaw chomps every 0.8 seconds while flying. Contact deals damage once, stops the
projectile and synchronizes a final bite; the harmless skull disappears within
eight ticks. Cover, homing, damage and volley sizes retain their existing rules.

Operator commands: `/summon elementalwands:hollow_necromancer`, then
`/ew necromancer fight|stop|status|transform`, one-shot robed `bolt|hands|drain|raise|blink`
and, after transforming, colossus `swipe|grab|rush` (spells also work). One-shot casts
leave the boss passive. See [testing](testing.md) for its fixtures.
