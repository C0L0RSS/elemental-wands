# Hollow Necromancer combat

The Hollow Necromancer is a second cooperative boss in progress, fought in the Hollow
Crypt realm after walking into the veil of the overworld graveyard's mausoleum (see
[Church and arena](church-and-arena.md#hollow-crypt-realm)). Operators also test it with
`/summon` or `/ew crypt`.
Java owners are under `src/main/java/com/anton/elementalwands/entity/necromancer/`.

## Intro cinematic

A crypt fight opens with a 18.9-second scene (`NecromancerIntro`, 378 ticks) that replaces the old
pop-in rise. The Necromancer is already standing in the circle but stays out of frame until the
reveal, and his skeleton never shows, so the transformation stays a surprise:

| Ticks | Shot |
| --- | --- |
| 0–64 | Low in front of a zombie shuffling between the graves west of the circle; the circle is behind the camera |
| 64–108 | The zombie is lifted and arched back as its soul is torn out toward the lens; the body crumbles to bone dust |
| 108–150 | The camera pans after the soul and follows it in, revealing him hauling it into his raised staff |
| 150–220 | Low hero angle: the staff flares, his eyes light, he lowers the staff |
| 220–378 | From the players’ side, drawing back as the ring spreads: he turns on the players, levels the staff and slams it; a dense crest of soul fire, trailing souls and pale sparks lights the rim braziers; the title slides up on a padded black card, then the camera returns to the player |

The server runs the timeline (`NecromancerIntro`, ticked by the boss). It turns him to face the
zombie while he hauls and to the players over ticks 205–235. During the scene, the renderer follows
the synchronized entity yaw directly so the stationary mob’s usual body-turn delay cannot leave
him facing away during the staff slam. The server holds every watcher in place: they
cannot move, cast or take damage (except `/kill` and the void), and the boss is passive and
untouchable. A watcher caught mid-jump builds no fall distance while held. A watcher who disconnects
is let go at once, and a boss that unloads, changes dimension or is removed mid-scene releases
everyone. Operator `fight`, `stop`, `intro` and the one-shot rehearsals (casts, `wave`, `rain`)
end a running scene on the spot: its watchers go free and it never starts the fight later. The
scene itself never saves: a boss saved mid-scene reloads fighting, as the scene would have
ended, rather than passive, frozen and stuck at its first gate. The zombie (`IntroZombieEntity`) and its soul (`IntroSoulEntity`, drawn with the Soul
Harvest model and its `dragged` loop) never save; the soul's path is a function of world time,
so it stays in step with the camera and lands in the raised staff's flame (`STAFF_RAISED`, from
the intro clip's pose). Each client (`NecromancerIntroClient` with the `Intro*Mixin` classes) flies
the camera through the shots on the server's clock, letterboxes the view, hides the HUD, hand and
block outline, and narrows the lens. Holding Sneak skips: the camera hands back at once, and the
fight starts early only when every watcher has skipped (never before tick 20). A skip lands on
the finished scene with the braziers lit. The rim braziers are authored unlit, and in the crypt
the scene lights every soul campfire in its ring's reach, putting out any lit ones on its first
frame. Elsewhere it only borrows soul campfires that are burning and relights just those, and a
cancelled scene relights what it put out, so a replay outside the crypt leaves them as it found
them. The boss clip `animation.hollow_necromancer.intro`
shares the timeline. In the crypt cinematic, ambient fill falls from 0.2 to 0.025 and the fog
colour dims to 22% of normal while the soul effects and lantern pools remain visible. The slam’s
expanding fire front restores both to their normal level over ticks 274–294, as the braziers
ignite in larger bursts. This is a client cinematic effect: a skip, cancellation or dimension
change restores normal lighting, and other dimensions keep their existing lighting.
After the soul arrives at tick 148, his staff lowering and turn take 50% longer than the
original clip. He holds the levelled staff from ticks 242–262 before hoisting and slamming it
at tick 274. The name card is visible from ticks 278–354 (3.8 seconds, with half-second entrance
and exit fades), and the camera returns over ticks 354–378. The fire front keeps its one-second
expansion, so the blast remains forceful even with the caster’s slower movements.
`/ew necromancer intro` replays it for players within 64 blocks.

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
COLOSSUS  swipe / grab / rush / grave dive / soul harvest and its larger spells; no army
          → at 25% its soul tears free once while the blind body swings at random
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
| Soul bolt | Three slow homing skulls, 0.4 seconds apart, with glowing eyes and chomping jaws. They turn a little each tick, so cover and a late sidestep both beat them. Every skull that bites deals its 5 damage: the damage type bypasses hit immunity. When a player's Astral Double is at least eight seconds old and currently visible within bolt range, the volley ends with one more skull that homes on the nearest such double while the double hums; one bite destroys it. |
| Grasping hands | About twelve articulated skeletal claws rise around a 4-block ring under each of up to three players. Walking out before it closes (1.6 seconds) avoids its 10 damage and 2-second root. A caught player is followed at once by an ambush behind them, whatever its cooldown. Overlapping rings punish players who stand together. |
| Life drain | Only starts on a player within 10 blocks. The hand rises, a heartbeat sounds and motes gather at the focus for 1.5 seconds before the first pulse, and it stands still while it channels, so walking out to 14 blocks (or behind cover) breaks it. Otherwise two braided soul strands flow from the target into a focus above the mage’s animated hand (the colossus uses its jaw focus): six pulses of 2 damage, half a second apart, healing it by twice the damage up to 3% of its maximum health per channel. The drained player sees smooth mist and curling wisps in first person. |
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
   | 1 | 6 crawlers | 4 crawlers |
   | 2 | 5 crawlers, 2 archers | 4 crawlers, 1 archer |
   | 3 | 5 crawlers, 2 archers, 1 brute | 4 crawlers, 1 archer, 1 brute |
   | 4 | 6 crawlers, 2 archers, 2 brutes | 5 crawlers, 2 archers, 1 brute |

   Each player beyond two adds 40%, capped at 12 bodies per wave. While a wave still
   stands, a reinforcement crawler claws out 8–14 blocks from a random player every
   five seconds (two per wave solo, one more per extra player, at most five). A cleared
   wave rests 1.5 seconds; a wave that survives 25 seconds is joined by the next.
   Siege bodies are quickened by the caster's soul fire: crawlers move 30% faster (about
   2.6 blocks a second, faster than a zombie), brutes 15% and archers 10%. Wild night
   spawns keep their pace.
3. **Soul Fire Rain.** Three seconds after it perches, and then every four seconds (three
   in the second siege), it drives its staff down and lobs a volley of blue fireballs:
   one marker on every player plus one spare (two in the second siege) 3–6 blocks from a
   random player. Each marker is a soul-fire ring the size of the blast with an inner
   ring that fills it; the fireball leaves the staff with the marker and lands exactly as
   it fills, 1.5 seconds later, along a scripted lob that passes the crypt's lid and
   boughs. The blast deals 10 within 2.5 blocks with knockback and leaves a patch of
   soul fire for two seconds that sets anyone inside alight. It hits the caster's own
   army too, so crawlers can be led under a marker.
4. Crawlers and brutes cannot reach a hovering player, so archers prefer anyone more
   than four blocks above the floor, and after three seconds of hovering the perch
   fires a soul bolt (formed just below the crypt's lid).
5. When the last wave dies a flare marks the landing near home and it drops from the
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

The colossus moves on all fours in a low diagonal crawl (steering, not pathfinding) at
about 4.4 blocks a second, just faster than a walking player, never blinks and raises
no army. Its separate knuckles and three-part fingers form spread,
curled claws. One hand plants while the other reaches ahead and the opposite hind
leg pushes; even at rest the hands bear weight. It rears for its spells, the rush windup or a grab, then
returns to the crawl. Its rush accelerates that four-limbed gait, then a contact
grab pulls the victim to its jaws for a bite and forward throw:

| Attack | Behavior |
| --- | --- |
| Swipe | A marked frontal arc for 13 damage; stepping out or jumping clears it. |
| Grab | An articulated hand lifts one player, then slams them for 45% of their health (at most 14). The server follows a wrist socket sampled from the animation and holds the player without mounting, so the camera stays free. Team damage breaks the grip and staggers it. The same player is not grabbed again for 15 seconds. |
| Rush | Only its body tells the charge: no lane and no roar until it goes. It sinks its chest and coils its hind legs, then rocks its weight from side to side while each forefoot digs into the dirt and its skull stays fixed on the target. The rocking lasts a random 1.2–2 seconds in total, so it cannot be counted. For the last 0.3 seconds it goes still with its jaws clamped as the aim locks, then roars and runs, steering 1.2° a tick. Contact grabs one player, bites once for 14 damage after 0.9 seconds, throws them forward at 1.4 seconds, and recovers by 2.2 seconds. Walls stop the run; sidestepping after the lock makes it miss. Teammate damage does not interrupt this sequence. |
| Grave Dive | Used when every player stands at least 9 blocks off; it needs no sight. It rears and plunges head first into the ground (shielded from 0.8 seconds, gone by 1.2), then tunnels toward its target at 7.2 blocks a second, faster than a sprint, leaving cracked earth and soul fire along the surface. Beneath the target, or after four seconds, it stops: the ground cracks in a 3.5-block ring for one second, then it bursts out jaws first for 16 damage and throws everyone in the ring into the air. A player who keeps moving through the warning leaves the ring. If nobody is caught it is stuck half out of the ground for 3.5 seconds, taking ×1.5 damage, before it hauls itself free. While underground it cannot be hit or targeted. |
| Soul Harvest | Every 20 seconds or so (never while its soul is out) it kneels up, draws a breath and at 0.8 seconds screams at the sky, arms swept behind its back (a sculk shriek, a ghast scream and a warden roar; a sonic boom above its skull and a ring of souls rolling out over the ground). The spots it calls, 16–28 blocks away and at least 6 from every player (three souls solo, one more per extra player, at most six), glow with a ring and a low column of soul fire for one second and crack; then the souls claw out of them and drift toward its ribcage at 1.8 blocks a second. Each one that arrives heals 3% of its maximum health. Any hit destroys a soul; interrupting the call raises none. |
| Spells | Four-skull bolt volleys (jaws kindling first, plus the extra skull for a noticed Astral Double), 5-block hands (both claws strike the floor) and the drain (a sniff and heartbeat; the same 10-block range and 1.5-second windup). When Hands catches a player, the skeleton rushes the nearest caught player after a 0.5-second windup, then uses the same bite and throw. Only one victim is pursued per cast. Escape the ring before it closes to avoid the combo. |

In full iron armor (15 points) the Hands + bite combo costs about 15.5 of 20 health.

### The caster inside

Once, when the colossus reaches a quarter of its health, it holds there, rears onto
its hind legs with its arms thrown wide and convulses; 1.1 seconds later the Necromancer's
soul tears out of the ribcage (the core there goes dark). The soul is the Soul Bolt skull
at 2.4× with a crown of soul fire. It hovers 3–6 blocks above the ground and 8–14 blocks
from the players, glides between vantage points, blinks away (a flare marks the spot half
a second ahead) every four seconds or when a player comes within 4 blocks, and every three
seconds alternates a three-bolt volley with a 4-block ring of grasping hands. A faint
tether of souls runs back to the ribcage.

While the soul is out the body is shielded (hits flash off it) and blind: it stays where
it is and does not rush, dive or grab. Two seconds after the soul leaves, and then every
2.3–3.3 seconds, it throws a swipe at a random heading, aimed at no one. The marked arc
appears at the new heading at once and the body turns into it (20° a tick), so anyone
standing beside it has the swipe's full windup to step out. Hits on the soul wound the boss.
Once the soul has taken 30 damage solo (6% of the boss's maximum health, at least 40, with
more players) it is dragged back along the tether and the body collapses onto its chest for
five seconds, taking ×1.5 damage, before it pushes itself up. The split never repeats: the
colossus fights on with its whole moveset until it dies. A save while the soul is out
forgets the split, and it tears free again on the next hit.

One entity and one combined model carry both forms, so health, the boss bar and saving
stay continuous; the model hides whichever body is inactive. So the boss stays findable in the
dark crypt, both forms render with at least block light 9, and the glow mask lights the hood
eyes, staff skull and soul vial, the rune hems around the whole robe, the colossus eye
sockets and broken soul seams along its four long limb bones. Vanilla's burning overlay is
sized to the hitbox and would hide the colossus, so that form skips it; while burning (it
still takes fire damage) it shows small flames and smoke along its frame instead.

The transformed colossus is the fight's only elite; the sieges use the ordinary
[Hollow undead](hollow-undead.md), where the Hunched Brute is the heavy.

Each tell has its own clip, authored in `v2/animations.py`. `siege_and_windup_clips`: the
robed `perch_channel` loop, `crash` (kneeling through the exposed window) and `ambush_burst`,
and the colossus `colossus_cast_bolt` (jaws spit with each skull), `colossus_cast_hands`
(claws strike the floor at 0.25 seconds, clench as the rings close) and `colossus_cast_drain`.
`second_playtest_clips`: the robed `perch_cast` (the staff hoisted and driven down at each
volley, chained back into the channel), the charge's `colossus_rush_crouch`, looping
`colossus_rush_coil` and held `colossus_rush_set`, the dive's `colossus_dive`, `colossus_erupt`,
looping `colossus_stuck` and `colossus_haul`, and `colossus_split`, `colossus_collapse` and
`colossus_harvest`. The older standalone `roar` and `raise` clips are no longer used in combat.

The Soul Fire Rain fireball and the harvested souls are GeckoLib models from
`art/hollow_necromancer/soul_fire/build_art.py`. The fireball is a white-hot core inside a
cloud of flickering flame blocks with tongues streaming behind; it flies nose first along its
arc, fully lit. The soul is a rounded, hollow-eyed head with side wisps and a waving tail; it
swells out of the ground (`rise`), then drifts (`drift`) slightly see-through toward the
ribcage. The script writes an orbitable review page served by the Soul Bolt server at
`/.local-previews/soul-fire/`; `--install` and `--check` as elsewhere.

### Soul light

The soul spells light the crypt around them in two layers, since Minecraft light has no
colour:

- **Real light (server).** `SoulGlow.light` places `elementalwands:soul_glow`, an invisible,
  replaceable, collisionless block with no item or drops, at the spell each tick: fireball 14,
  its rain marker 6 → 9 → 12 → 15 in four steps as the ball closes in, a 15 flash for eight
  ticks at impact, Soul Bolt skulls 11, harvested souls 9 and the freed soul 13
  (`NecromancerRules.GLOW_*`, `rainGlow`). It only takes `air` or `cave_air` (else the block
  above), never water or any other block, and does nothing in unloaded chunks. A refresh
  that changes nothing writes nothing; two spells on one block keep the brighter level. Each
  glow goes dark three ticks after its last refresh: placing one schedules a block tick that
  clears it unless an in-memory expiry says it is still wanted. Scheduled ticks save with the
  chunk and the expiry map starts empty, so a glow left by a crash, reload or `/setblock`
  clears on its first check; a random tick is the fallback.
- **Blue colour (client).** `SoulLightPool` draws a stepped soul-cyan pool on the block tops
  under each glowing spell (and under a fireball's marker, stepping with its light). Each
  patch sits 0.02 above its own surface, so it follows steps and graves; it is translucent,
  emissive and does not write depth, so walls hide it. It fades with height (none beyond
  seven blocks, none for a light buried in the ground) and is weaker in daylight.
  `tools/prepare_soul_light.py` writes its texture; `--check` verifies it.

Every light change makes clients rebuild nearby chunk sections. A fireball crosses about
one block every two ticks, so an eight-ball volley costs roughly ten light changes a tick
for a second and a half; keep new glowing spells to this pattern and modest counts.

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
client follows the 1.5-second windup (`Action.DRAIN.impact`) and 0.5-second pulse spacing.
The robed and colossus drain clips were authored for the earlier 0.7-second windup, so the
raised hand holds for a moment before the first pulse and the colossus claw curls do not
land exactly on the pulses. The braid is
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
`/ew necromancer fight|intro|stop|status|transform|siege`, one-shot robed
`bolt|hands|drain|blink|shift|ambush`, `wave <1-4>`, `rain` (one Soul Fire Rain volley at
the nearby party) and, after transforming, colossus `swipe|grab|rush|dive|harvest` (spells
also work) and `split`. One-shot casts, volleys and waves leave the boss passive; `siege`
and `split` start the fight and the current duel's siege, or the soul split, at once. Each of
these commands, `fight` and `stop` ends a running intro first; `stop` stays passive after a
reload.

`/ew necromancer log on|off` is a tuning aid. Fights that start while it is on record
every cast, stage and wave, each hit on a player from the boss or its army (before and
after armor, or lost to hit immunity) and the damage each player deals. At the end the
operators see a summary and the full record is written to
`logs/necromancer-fight-<time>.txt` in the server directory. See [testing](testing.md)
for the fixtures.
