# Hollow Necromancer combat

The Hollow Necromancer is a second cooperative boss in progress, fought in the Hollow
Crypt realm after the overworld graveyard's headstone ritual (see
[Church and arena](church-and-arena.md#hollow-crypt-realm)). Operators also test it with
`/summon` or `/ew crypt`.
Java owners are under `src/main/java/com/anton/elementalwands/entity/necromancer/`.

## Encounter intent

A tall, lean, hooded caster that is hard to pin down. The robed fight alternates
**duels**, where it fights alone and blinks about the clearing, with **sieges**, where
it retreats out of reach and raises escalating waves of Hollow undead.
`NecromancerCombat` owns one committed cast at a time, target rotation, movement and
the siege; no vanilla goals compete with it. Creative/Spectator players are excluded
and Peaceful stops the fight. Rules and tuning live in `NecromancerRules`, which the
contract checks exercise without a world.

```
ROBED     Duel A (100→75%) → Siege 1 (waves 1–2) → crash, exposed → Duel B (75→50%)
          → Siege 2 (waves 3–4) → crash → transformation
COLOSSUS  swipe / grab / rush and its larger spells; no army
```

Health is 800 plus 500 per extra participant, grows when players join and never
shrinks mid-fight. Each duel holds at its gate (75%, then half health), so burst damage
cannot skip a siege or the second phase; reaching the gate interrupts the current cast
and starts the siege. The stage (`NecromancerRules.Stage`) and the encounter's home
save with the boss; a siege saved mid-way restarts from its first wave.

### Duels

Every attack has its own tell, then commits; heavy hits are sized to hurt through iron
armor. The caster keeps 6–15 blocks from the nearest player while walking (within 16
of home); its blinks, shifts and ambushes reach 30 blocks from home.

| Spell | Behavior |
| --- | --- |
| Soul bolt | Three slow homing skulls, 0.4 seconds apart, with glowing eyes and chomping jaws. They turn a little each tick, so cover and a late sidestep both beat them. Every skull that bites deals its 5 damage: the damage type bypasses hit immunity. |
| Grasping hands | About twelve articulated skeletal claws rise around a 4-block ring under each of up to three players. Walking out before it closes (1.6 seconds) avoids its 10 damage and 2-second root. A caught player is followed at once by an ambush behind them, whatever its cooldown. Overlapping rings punish players who stand together. |
| Life drain | Two braided soul strands flow from the target into a focus above the mage’s animated hand (the colossus uses its jaw focus). The drained player sees smooth mist and curling wisps in first person. Damage and boss healing remain capped per channel; losing line of sight or range breaks it. |
| Blink | When a player gets within 4.5 blocks it teleports away and leaves a Slowness/Wither curse patch. A soul-fire flare marks where it lands. |
| Shift | Every two or three casts it teleports to a new spot 10–24 blocks from the party and at least 8 from everyone. A flare and sound mark the spot 0.6 seconds early. |
| Ambush | A whisper and a flare follow the target’s back for 0.6 seconds, then it appears behind them. After a 0.7-second windup (a soul ring at its feet) a burst deals 14 plus knockback within 4.5 blocks. A player who keeps moving escapes; one standing still to cast is caught. It then stands still for a second. Teammate damage during the windup (the grab-escape amount) interrupts and staggers it. |

Targets rotate: each cast picks the least recently targeted eligible player, nearest
first. Bolts and drain need sight; hands and ambush do not.

### Sieges

1. A flare marks its perch, then it blinks there, loses gravity and channels. In the
   crypt the perch is the free bough nearest the party (never the previous siege's):
   eight bough tops above the barrier lid, exported by the layout builder as
   `crypt/HollowCryptPerches`. Players see it but cannot reach it. Elsewhere it hovers
   ten blocks above home. A soul-fire ward and a column of soul fire down to the floor
   lead the eye to it through the fog. (A vanilla glowing outline does not render: the
   GeckoLib 5.3 alpha renderer has no outline support.) It is immune; hits flash the ward
   and a shield sound.
2. Waves claw out of the floor 8–32 blocks from home and at least 6 from every player,
   staggered over two seconds, each with a soul stream from the staff. Waves grow:

   | Wave | Two players | One player |
   | --- | --- | --- |
   | 1 | 5 crawlers | 3 crawlers |
   | 2 | 4 crawlers, 2 archers | 3 crawlers, 1 archer |
   | 3 | 4 crawlers, 2 archers, 1 brute | 3 crawlers, 1 archer, 1 brute |
   | 4 | 5 crawlers, 2 archers, 2 brutes | 3 crawlers, 2 archers, 1 brute |

   Each player beyond two adds 40%, capped at 12 bodies per wave. A cleared wave
   rests three seconds; a wave that survives 45 seconds is joined by the next.
3. Crawlers and brutes cannot reach a hovering player, so archers prefer anyone more
   than four blocks above the floor, and after three seconds of hovering the perch
   fires a soul bolt (formed just below the crypt's lid).
4. When the last wave dies a flare marks the landing near home and it drops from the
   perch. After the first siege it lies **exposed** for six seconds: no casting and
   ×1.5 damage taken. After the second, the crash leads straight into the
   transformation; the landing is chosen with room for the colossus.

While a siege runs, the encounter and the army's leash are measured from home, not
from the perched caster.

Raised bodies are the same Hollow Crawler, Risen Archer and Hunched Brute that spawn
at night (see [Hollow undead](hollow-undead.md)). Once bound by `NecromancerMinion`
they do not burn, drop loot or experience, or save, are on the caster's side for
targeting and spell protection, cannot damage it, and dissolve when it stops or is
gone. Each plays its own rise clip standing on the floor and cannot act until it ends.

Player spells treat the boss as a `WandBoss`, the shared interface that also
covers the Guardian: no roots, knockback, stagger or interrupts from wand spells.

## Phase two: the colossus

The second siege's crash starts the transformation once the boss stands on ground
with headroom for the colossus. Under a low ceiling it blinks to open ground first,
and after ten seconds it transforms in place regardless. The operator `transform`
command skips any remaining siege.

During the eight-second, invulnerable transformation the mage arches backward and
opens its hood. The skeleton reaches through, plants its hands, and pulls its body
free. The discarded robe collapses onto the ground behind it and burns away while
the skeleton rears up, then settles onto all fours. Any bodies still standing freeze
while it happens. The hitbox grows at 4.5 seconds, nearby players are pushed clear,
and a roar at 7.1 seconds precedes the return to combat. A save during the
transformation reloads as the finished colossus. The colossus keeps its phase if
everyone leaves.

The colossus moves on all fours in a low diagonal crawl (steering, not pathfinding),
never blinks and raises no army. Its separate knuckles and three-part fingers form spread,
curled claws. One hand plants while the other reaches ahead and the opposite hind
leg pushes; even at rest the hands bear weight. It rears for its spells, the rush windup or a grab, then
returns to the crawl. Its rush accelerates that four-limbed gait, then a contact
grab pulls the victim to its jaws for a bite and forward throw:

| Attack | Behavior |
| --- | --- |
| Swipe | A marked frontal arc for 13 damage; stepping out or jumping clears it. |
| Grab | An articulated hand lifts one player, then slams them for 45% of their health (at most 14). The server follows a wrist socket sampled from the animation and holds the player without mounting, so the camera stays free. Team damage breaks the grip and staggers it. The same player is not grabbed again for 15 seconds. |
| Rush | A 1.5-second windup: it rears and scrapes the floor while two soul-fire edges mark its lane up to the first wall. Its aim follows the target, then locks for the last 0.4 seconds. The run then steers only 0.75° a tick. Contact grabs one player, bites once for 14 damage after 0.9 seconds, throws them forward at 1.4 seconds, and recovers by 2.2 seconds. Walls stop the run; sidestepping after the lock makes it miss. Teammate damage does not interrupt this sequence. |
| Spells | Four-skull bolt volleys (jaws kindling first), 5-block hands (both claws strike the floor) and the drain (a sniff and heartbeat). When Hands catches a player, the skeleton rushes the nearest caught player after a 0.5-second windup, then uses the same bite and throw. Only one victim is pursued per cast. Escape the ring before it closes to avoid the combo. |

In full iron armor (15 points) the Hands + bite combo costs about 15.5 of 20 health.

One entity and one combined model carry both forms, so health, the boss bar and saving
stay continuous; the model hides whichever body is inactive. So the boss stays findable in the
dark crypt, both forms render with at least block light 9, and the glow mask lights the hood
eyes, staff skull and soul vial, the rune hems around the whole robe, the colossus eye
sockets and broken soul seams along its four long limb bones. Vanilla's burning overlay is
sized to the hitbox and would hide the colossus, so that form skips it; while burning (it
still takes fire damage) it shows small flames and smoke along its frame instead.

The transformed colossus is the fight's only elite; the sieges use the ordinary
[Hollow undead](hollow-undead.md), where the Hunched Brute is the heavy.

Each tell has its own clip, authored in `v2/animations.py` (`siege_and_windup_clips`): the
robed `perch_channel` loop, `crash` (kneeling through the exposed window) and `ambush_burst`,
and the colossus `colossus_rush_windup`, `colossus_cast_bolt` (jaws spit with each skull),
`colossus_cast_hands` (claws strike the floor at 0.25 seconds, clench as the rings close) and
`colossus_cast_drain`. The older standalone `roar` clip is no longer used in combat.

## Art and verification

Both forms use the authored V2 model generated by
`art/hollow_necromancer/v2/build_art.py`. `geometry.py` owns the layered mage and
open skeletal anatomy; `animations.py` owns the timing and poses. `rig.py` bakes
hand contacts and the server grab socket from the same transforms. The exporter
requires Pillow and NumPy. Default invocation writes the workshop candidate;
`--install` writes mod resources and the Java grab socket; `--check` compares all
five runtime outputs. Neither option installs a JAR into Lunar.

The earlier V0/V1 generators are historical sources and must not overwrite V2
runtime assets. Minion art is owned by the Hollow undead exporter.

Soul bolts use the authored skull from `art/hollow_necromancer/soul_bolt/build_preview.py`.
Its default invocation builds the local review page; `--install` exports the model,
texture, glow mask, animations and prepared wisp/shard particles into mod resources;
`--check` verifies their agreement with the source assets. `SoulBoltRenderer` faces the skull along its flight, including vertical aim.
The jaw chomps every 0.8 seconds while flying. Contact deals damage once, stops the
projectile and synchronizes a final bite; the harmless skull disappears within
eight ticks. Cover, homing, damage and volley sizes retain their existing rules.

Life Drain rendering is owned by `NecromancerDrainEffects` and
`NecromancerDrainOverlay`. The server tracks the victim and cast start tick; the
client follows the 0.7-second windup and 0.5-second pulse spacing. The braid is
full-bright soul cyan with a soft additive halo, so it reads in the dark crypt; a
bright bead travels victim → focus each pulse and both ends flare as it lands.
Full-bright soul wisps gather at the focus during the windup, then flow along the
braid, burst from the victim on each pulse and rise from the focus as the pulse
lands. In first person, stream fragments fade only by distance from the camera
(`SpellViewClearance`), while observers see the full braid. Cover, range failure
or normal completion starts a 0.75-second visual fade; removal, death and world
changes clear the effect. The victim's HUD mist tints the screen edges soul cyan
and brightens on each pulse without darkening the view; it is restricted to the
living local victim in first person, including when several bosses cast.
`art/hollow_necromancer/life_drain/build_runtime.py` exports the smooth HUD
textures (edge glow, fog, wisp) and sampled mage hand socket; `--check` verifies
them. The browser preview remains an approximate art reference with the older
darker palette, not native gameplay evidence.

Operator commands: `/summon elementalwands:hollow_necromancer`, then
`/ew necromancer fight|stop|status|transform|siege`, one-shot robed
`bolt|hands|drain|blink|shift|ambush`, `wave <1-4>` and, after transforming, colossus
`swipe|grab|rush` (spells also work). One-shot casts and waves leave the boss passive;
`siege` starts the fight and the current duel's siege at once.

`/ew necromancer log on|off` is a tuning aid. Fights that start while it is on record
every cast, stage and wave, each hit on a player from the boss or its army (before and
after armor, or lost to hit immunity) and the damage each player deals. At the end the
operators see a summary and the full record is written to
`logs/necromancer-fight-<time>.txt` in the server directory. See [testing](testing.md)
for the fixtures.
