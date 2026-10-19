# Storm Summit

The Storm Roc's realm, with a playable two-phase fight. The Overworld aerie, intro, the
phase-change cinematic, and the Roc's death and reward remain planned in the
[roadmap](../ROADMAP.md).
The realm and encounter lifecycle live under `summit/`; combat lives under `entity/roc/`.

## The realm

`elementalwands:storm_summit` is a void world (flat generator, one air layer) with
overworld sky effects, a fixed late-morning sun (time 4500), skylight and no mob spawns.
Its biome has no precipitation, so real weather never rains on a fight or douses fire spells,
and clients ignore the Overworld's weather there: only phase two's storm changes the sky. Its biome gives a deep blue sky and a pale horizon. The summit's surface is
y=100 (`StormSummit.SURFACE_Y`), so the camera never drops into the lower sky's darkness.
Vanilla clouds sit at y=54, hidden under the mod's own sea of clouds.

Each slot holds one copy of the mountaintop:

- **The summit:** a round summit of flat, weathered rock about 107–123 blocks across, with
  one narrow promontory. The middle 44 blocks are bare. Fissures, lightning scars and snow
  drifts vary the surface; low boulders and a few lip crags stand toward the rim.
- **The cliffs:** under the rim, a short sheer drop gives way to a buttressed flank that
  meets the clouds 36 blocks down (y=64, `StormSummit.CLOUD_Y`).
- **The spires:** six of them, 22–29 blocks off the rim at summit height. Their capstones
  are 25–35 blocks above the summit and about 14 blocks across, sized for the Roc when it was
  drawn at 2.5 times its art study. It is now drawn at 1.25 times (`StormSummitLayout.ROC_SCALE`,
  a 34-block wingspan), so they leave it room. `StormSummitLayout.PERCHES` lists where it
  stands on each, home spire (due north, 35 up) first. It faces the centre from the far edge, with its tail over
  the back.
- **Windcaller ruins:** a half-fallen shrine on the west rim, with a copper wind rose and a
  bell, plus copper-capped standing stones with lightning rods.
- **The remains:** bones, skulls, rusted helms, a broken sword and Roc feathers. They are
  walk-through, unbreakable decoration blocks (`ModBlocks.SUMMIT_RELICS`).
- **The far view:** snowy peaks and low needles rise out of the clouds up to about
  250 blocks away.

`client/SummitCloudEffects` draws the sea of clouds: an opaque, lit sheet at the cloud top
that hides everything below, and translucent wisps 3.5 blocks above it. Both are tiling
textures, one pixel per block, drifting east on the west wind and fading into the world's
fog. While a Roc fights on a summit, a thin, broken cap cloud gathers 58 blocks over it
(`StormSummit.CAP_Y`), solid out to 60 blocks from the centre and frayed away by 125, so
phase one's lightning falls from cloud rather than a clear sky. Phase two's storm replaces
it. During that storm it also draws a dark ceiling of storm cloud at y=176
(`StormSummit.STORM_CEILING`) with torn scud racing 11 blocks beneath it, darkens the sea,
and lights every layer during a lightning flash.

## Visiting

Operators raise and visit a slot with `/ew summit enter [slot]`.
Anyone on the summit can use `/ew summit leave`, which returns them to where they entered.
`/ew summit status` reports the generation and built slots and any fight's phase and stage. A slot is laid out the first
time it is used, one 48×48 tile per server tick (70 tiles), and is then reused. When the
installed tiles change, every slot moves to untouched ground. `elementalwands/storm-summit.json`
records the built slots and return points.

A player outside the rim and more than 4 blocks below the summit, or anywhere below the
clouds, has fallen (`StormSummit.fallen`). Outside a fight, the wind sets them back at the arrival
point. During a fight, a fall defeats the challenger and moves them into spectator mode. Survival players can't break the mountain
or place blocks on it. Spells and explosions destroy no blocks. Nothing drops on death: the
respawned player keeps their inventory and experience. During a fight, movement spells and
pearls may move within its slot (including toward spires), but cannot cross slots or dimensions.
The explicit leave command remains available.


## Phase one

After the slot is ready, `/ew summit start` begins a fight with the living Survival or
Adventure players on that summit. The roster seals at the start. `/ew summit reset` ends
it and permits another attempt; `/ew summit status` includes the current combat state.
Both start and reset require operator permission. For playtests, `/ew summit skip` jumps
a running fight to the phase change. There is no aerie entrance yet.

The Roc flies between the six spires for about 20 seconds on Normal (22.5 on Easy, 15 on
Hard). Each spire visit lasts 7.5 seconds:
- It lands, and its perched idle plays: a bird's head snaps, a feather shake, a wing stretch.
  The idle resumes on each spire, so every visit shows more of it.
- About 1.7 seconds in, it rears up and calls the lightning: a bolt shoots from its raised
  bill into the cap cloud, the sky flashes, and a large strike marks each challenger (up to
  four, like every aimed volley). It whips its head down as those bolts land, 1.5 seconds later.
- About 5.3 seconds in, it flies to the next spire.

It dives only from a spire, once its call has landed, and commits to a fixed dive marker for
three seconds. Small lightning circles have a one-second warning and two bolts; large circles
have a 1.5-second warning and three. Repeats arrive half a second apart, with full damage on
each bolt. Their centres cannot sit inside another live circle. Small aimed strikes keep
coming every two seconds, some leading movement; random damaging strikes and harmless distant
bolts from the cap cloud add atmosphere. Aimed volleys rotate through at most four
challengers at once.

The dive marker keeps clear of tornadoes. Its impact is lethal against full unenchanted
diamond and launches a jumpable smoke/lightning ring; it rolls over low rocks, so only being
in the air clears it. Sky strikes stop on impact. The Roc stays stuck for six seconds with
its bill driven into the rock, its wings splayed and beating weakly on the ground (harmless)
and its scrambling feet hurting anyone close. Then it brawls.

Where its target stands decides the answer, so every spot has one:

- **Head attacks** (peck, side sweep, straight scream) are offered only where the bill's
  authored path reaches. The reach is measured from the shipped sockets: sweeps about
  8.75–13.5 blocks ahead of its feet, pecks 10.75–16.5, the scream from 11.75 out.
- **Wing wave** for players beside or behind it, or under its neck and chest, out to 16 blocks.
- **Pounce** at a target over 26 blocks away, after four attacks in one spot, or at a player
  who stays underneath after a wing wave.
- **Stampede** at the player who last hit it from more than 22 blocks, at most every eight seconds.

It never repeats an attack back to back and picks among the rest at random. Its target is
chosen at random, weighted by each player's recent damage (counting half after five seconds),
so the main damage dealer draws most attacks but not all. It turns 15° a tick while winding up,
and each attack's aim stops following its target at a lock tick. The pounce locks 0.4 seconds
before launch and shows its circle at launch. A moving foot, from turning, scraping or
landing, hurts players under it; the pounce's flight and the stampede's charge use their own hits.
The brawl has no timeout: the next takeoff requires another eighth of total health lost.
Burst damage holds at each landing's gate. Reaching half health plays the takeoff, then the
phase change.

One tornado starts with the fight; a second appears at 75% total health. They persist through
the dive and brawl, stay within 32 blocks of the centre and keep apart. They move at a quarter
of walking pace: in the air stretch, the dive and the chain smash each turns slowly toward the
nearest challenger on the rock; in the brawl they only wander.
- **Winds:** from 1.8 times its radius they drag players inward and around it, harder the
  closer they are and much harder in the air. A walk escapes the edge of them; deep inside,
  only a sprint does. Inside its radius they also slow and chip.
- **Catch:** within 45% of its radius it catches a challenger, whirls them up the funnel to
  about 10 blocks (more for phase two's larger tornadoes) for 1.8 seconds, then throws them
  toward the summit centre. The throw's hit stands in for the fall, so the landing does no
  fall damage.

A shared three-second recatch immunity prevents juggling; an immune player is slowed but not
dragged. Dashes and teleports can escape the catch. Gusts and throws are constrained toward safe ground; normal strikes have no
knockback. Approaching a spire high above the summit triggers a damaging inward caw at any
point in the fight, so a spire is never a safe platform to shoot from.

Starting health is 2,400 solo plus 1,600 per additional challenger. Raw attack damage uses
vanilla armor and difficulty: on Normal, one small bolt costs about four hearts through
full diamond, a large bolt or scream about eight, and a peck/sweep about five. These are
initial tuning values, not human balance approval. Hard shortens the airborne stretch and
ground recovery; Easy lengthens them. Ground recovery pressure caps at four players.
Shields don't block the Roc. Its hits skip vanilla's hurt cooldown, so repeat bolts half a
second apart land in full. Within its own eight-tick window, though, a new hit only adds its
excess over the costliest recent one, so a crush and its ring, overlapping circles or a chip
and a bolt never stack. Costs are compared after armor, so a smaller hit first never
discounts a larger one. Values live in `StormRocRules`.

A defeated, disconnected or departed challenger cannot rejoin the current attempt. Defeated
players spectate with belongings kept; a wipe, reset or the Roc's defeat releases the group
for a retry. Original game modes are journalled before the fight and restored after an
interruption, server restart or later login. Runtime bosses, hurtboxes and effects do not
save, and ending the encounter removes hazards and tornado slowdown. Temporary chunk
tickets keep the summit and all perches ticking independently of player simulation distance;
they are removed when the fight ends and are never serialized.

On clients the Roc's clips crossfade over a quarter second whenever the server changes them.
Its gaze holds dead still, then snaps its head to the player it is striking at or attacking
(the server tracks that player), or else to you: fully while perched or between brawl attacks,
partly in flight, never mid-attack. In flight it banks into its turns and pitches with its
climb. All of this is presentation; hitboxes follow the authored clips.

The body, head and neck share the boss's health and damage admission. The latter two
hurtboxes follow authored sockets; their client copies step their position every tick, so melee
aims where the server has them. The Roc's own position reaches clients with a one-tick blend,
so a charge is drawn where it hits. The torso includes its grounded footprint for planted
thorns; authored feet also test owned coals and flame floors during grounded combat. Spell
damage and fire attribution resolve to the owning Roc so hitting multiple parts does not
multiply one attack's reward.

## Phase change and phase two

The phase change is a stand-in for its planned cinematic. After the half-health takeoff the
arena goes quiet: strikes, rings and live ground clear, and only the tornadoes remain. The Roc
climbs to about 36 blocks above the centre, well under where the storm clouds form. The
storm rolls in over about three and a half seconds. A huge bolt strikes the Roc, which then
lands on the tallest spire. It takes no damage during the eight seconds.

Phase two keeps phase one's loop, but the storm is on its side:

- **The storm:** a charged Roc, or the storm marker it leaves at the summit's centre, makes
  each client ease the summit into a thunderstorm. The marker keeps the storm going for
  players who can't track the Roc on a far spire at low render distance.
  - The sky, light and fog darken, rain falls and storm clouds close overhead.
  - The tornadoes darken with the light and reach up into the clouds.
  - Distant bolts fall from the ceiling into the sea of clouds with thunder, flashing the sky.
  - The Roc's crackle stays charged.

  Clients ignore all of this outside the summit.
- **Bigger strikes:** small circles have radius 3.5 and large ones 7, about 5 and 9 hearts a
  bolt through diamond. Random strikes fall every second instead of every one and a half.
- **Live ground:** a large strike's last bolt leaves its circle live for 2.5 seconds. Every
  half second it costs half a heart to anyone standing in it; being in the air at the pulse
  avoids it, and the arcs brighten just before each one.
- **Strikes during the brawl:** random strikes keep falling while it is stuck, brawling,
  taking off and between smashes. Aimed strikes stay in the air stretch.
- **Tornadoes:** they swell to radius 7 and a third joins them. The core, catch height and
  spacing grow with them.
- **More landings:** the air stretch is about 15 seconds on Normal (20 Easy, 12 Hard).
- **Chain smash** in place of the dive: four beak slams with radius-8 circles, each turned on
  the next player in turn and fixed once marked.
  - The first launches up from the perch, marks its player and falls on them two seconds
    later. Like the dive it is lethal.
  - After each slam it stays buried for 0.4 seconds, then rips free and hops at most 25
    blocks to the next circle, which lands 1.2 seconds after it appears and costs about six
    hearts.
  - Every slam sends a jumpable ring 26 blocks out.
  - Circles stay within 41 blocks of the centre (13 inside the narrowest rim) and off the
    tornadoes, close enough that the chain can always reach its last spot, within 24
    blocks of the centre, where no push reaches the edge.
  - It turns to face each circle from where it stands. Its bill reaches about 13 blocks past its feet,
    and facing the circle keeps the body's own hop no longer than the circle's. Near the rim it
    turns a little further to keep its body over the summit. Only the last slam leaves it stuck,
    then it brawls as before.

Its brawl moves, the caw and the landing gates are unchanged.
Phase two takes four more landings of an eighth of total health each. At the last gate
it is defeated. Its death and the shard reward are still to come, so for now the encounter
ends there and the group may fight again.

## Authoring

`art/storm_summit/build_layout.py` authors the whole map from one seed:

- **Default run:** writes the browser preview to `.local-previews/storm-summit/` (launch entry
  `storm-summit-preview`, port 8390). The preview poses the Roc art study's model at the
  layout's scale, perched, flying and landed, with a size slider, and draws the planned sea
  of clouds.
- **`--install`:** writes the realm tiles (`structure/storm_summit/summit_i_j.nbt`), the
  generated `summit/StormSummitLayout.java` (bounds, cloud height, perches and a rim table),
  the remains' block models, block states and textures, the two cloud textures and phase
  two's storm ceiling and scud.
- **`--check`:** detects drift in all of these.

`StormSummit` holds the realm's own numbers: surface height, slot spacing and arrival.

`tools/summit_client_smoke.init.gradle` is the native check. It enters a slot, photographs
seven viewpoints into `build/summit-client-smoke/screenshots/`, counts the remains, tries to
break the rock as a survival player, drops a player off the east rim to check the rescue,
and leaves. Screenshots are not a Lunar look.

The approved study stays under `art/storm_roc/candidate/`; its skeleton and clips drive the
game model. `art/storm_roc_mod/build_mod.py` builds that model: 307 blocks on the study's
bones, with a raised crest, wearing the bearded-vulture atlas (revision 3) from `art/storm_roc_texture_study/`
(its `--check` confirms the blocks still pack the islands that atlas was painted on). Its
`HEAD` and `FEET` pick the face and feet from `heads.py` and `feet.py`: the Demon face
(steep slim brows, glowing red slit eyes set in sockets, brow spikes) and Huge legs (thick legs
and big sickle talons on the same bones). `head_variants.py` bakes the other options for a
browser picker. `build_texture_v3.py` writes the atlas and the glow mask that lights the eyes.
`art/storm_roc/build_runtime.py` installs the model and atlas plus sixteen combat poses
(including the spire landing, the lightning call and the chain smash's hop) and matching
server sockets; it converts the preview's coordinate basis to GeckoLib without changing the
approved shape or texture.
Run `python3 art/storm_roc_mod/build_mod.py --check`, `python3 art/storm_roc/build_runtime.py --check` and
`python3 art/storm_roc/validate_runtime.py` to check export drift, anatomy and hit-frame
agreement. `render_runtime.py` produces offline review images.

`tools/roc_server_smoke.init.gradle` exercises real damage and encounter lifecycle in a
disposable server, through both phases. `tools/roc_client_smoke.init.gradle` runs a hidden
native summit rehearsal and captures individual screenshots of the Roc, its attacks, the phase
change and phase two's storm. Fresh receipts and inspection are required
before claiming either passed; neither establishes human Lunar/co-op balance.
