# Storm Summit

The Storm Roc's realm, with a playable first combat phase. The Overworld aerie, intro,
storm transformation and second phase remain planned in the [roadmap](../ROADMAP.md).
The realm and encounter lifecycle live under `summit/`; combat lives under `entity/roc/`.

## The realm

`elementalwands:storm_summit` is a void world (flat generator, one air layer) with
overworld sky effects, a fixed late-morning sun (time 4500), skylight, no weather and no
mob spawns. Its biome gives a deep blue sky and a pale horizon. The summit's surface is
y=100 (`StormSummit.SURFACE_Y`), so the camera never drops into the lower sky's darkness.
Vanilla clouds sit at y=54, hidden under the mod's own sea of clouds.

Each slot holds one copy of the mountaintop:

- **The summit:** a round summit of flat, weathered rock about 107–123 blocks across, with
  one narrow promontory. The middle 44 blocks are bare. Fissures, lightning scars and snow
  drifts vary the surface; low boulders and a few lip crags stand toward the rim.
- **The cliffs:** under the rim, a short sheer drop gives way to a buttressed flank that
  meets the clouds 36 blocks down (y=64, `StormSummit.CLOUD_Y`).
- **The spires:** six of them, 22–29 blocks off the rim at summit height. Their capstones
  are 25–35 blocks above the summit and about 14 blocks across, sized for the Roc drawn at
  2.5 times its art study. `StormSummitLayout.PERCHES` lists where it stands on each, home
  spire (due north, 35 up) first. It faces the centre from the far edge, with its tail over
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
fog.

## Visiting

Operators raise and visit a slot with `/ew summit enter [slot]`.
Anyone on the summit can use `/ew summit leave`, which returns them to where they entered.
`/ew summit status` reports the generation and built slots. A slot is laid out the first
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
Both start and reset require operator permission. There is no aerie entrance yet.

The Roc perches and flies between the six spires for about 18 seconds on Normal, then
commits to a fixed dive marker for three seconds. Small lightning circles have a one-second
warning and two bolts; large circles have a 1.5-second warning and three. Repeats arrive
half a second apart, with full damage on each bolt. Their centres cannot sit inside another
live circle. Some aimed strikes lead movement; random damaging strikes and harmless distant
bolts add atmosphere. Aimed volleys rotate through at most four challengers at once.

The dive marker keeps clear of tornadoes. Its impact is lethal against full unenchanted
diamond and launches a jumpable smoke/lightning ring; it rolls over low rocks, so only being
in the air clears it. Sky strikes stop on impact. The Roc stays stuck for six seconds with
its bill driven into the rock, its wings splayed and beating weakly on the ground (harmless)
and its scrambling feet hurting anyone close. Then it brawls.

Where its target stands decides the answer, so every spot has one:

- **Head attacks** (peck, side sweep, straight scream) are offered only where the bill's
  authored path reaches. The reach is measured from the shipped sockets: sweeps about
  17.5–27.5 blocks ahead of its feet, pecks 21–31.5, the scream from 23.5 out.
- **Wing wave** for players beside or behind it, or under its neck and chest.
- **Pounce** at a target over 35 blocks away, after four attacks in one spot, or at a player
  who stays underneath after a wing wave.
- **Stampede** at the player who last hit it from more than 28 blocks, at most every eight seconds.

It never repeats an attack back to back and picks among the rest at random. Its target is
chosen at random, weighted by each player's recent damage (counting half after five seconds),
so the main damage dealer draws most attacks but not all. It turns 15° a tick while winding up,
and each attack's aim stops following its target at a lock tick. The pounce locks 0.4 seconds
before launch and shows its circle at launch. A moving foot, from turning, scraping or
landing, hurts players under it; the pounce's flight and the stampede's charge use their own hits.
The brawl has no timeout: the next takeoff requires another eighth of total health lost.
Burst damage holds at each landing's gate. Reaching half health plays the takeoff and ends
this phase-only encounter; it awards no victory loot or shard.

One tornado starts with the fight; a second appears at 75% total health. They persist through
the dive and brawl, drift slowly within the interior and keep apart. Their outer wind slows
and hurts; the core lifts/spins a challenger, then throws them toward the summit centre.
A shared three-second recatch immunity prevents juggling. Dashes and teleports can escape
the core. Gusts and throws are constrained toward safe ground; normal strikes have no
knockback. Approaching a spire high above the summit triggers a damaging inward caw.

Starting health is 2,400 solo plus 1,600 per additional challenger. Raw attack damage uses
vanilla armor and difficulty: on Normal, one small bolt costs about four hearts through
full diamond, a large bolt or scream about eight, and a peck/sweep about five. These are
initial tuning values, not human balance approval. Hard shortens the airborne stretch and
ground recovery; Easy lengthens them. Ground recovery pressure caps at four players.
Shields don't block the Roc. Its hits skip vanilla's hurt cooldown, so repeat bolts half a
second apart land in full. Within its own eight-tick window, though, a new hit only adds its
excess over the largest recent one, so a crush and its ring, overlapping circles or a chip
and a bolt never stack. Values live in `StormRocRules`.

A defeated, disconnected or departed challenger cannot rejoin the current attempt. Defeated
players spectate with belongings kept; a wipe, reset or phase completion releases the group
for a retry. Original game modes are journalled before the fight and restored after an
interruption, server restart or later login. Runtime bosses, hurtboxes and effects do not
save, and ending the encounter removes hazards and tornado slowdown. Temporary chunk
tickets keep the summit and all perches ticking independently of player simulation distance;
they are removed when the fight ends and are never serialized.

The body, head and neck share the boss's health and damage admission. The latter two
hurtboxes follow authored sockets; their client copies step their position every tick, so melee
aims where the server has them. The Roc's own position reaches clients with a one-tick blend,
so a charge is drawn where it hits. The torso includes its grounded footprint for planted
thorns; authored feet also test owned coals and flame floors during grounded combat. Spell
damage and fire attribution resolve to the owning Roc so hitting multiple parts does not
multiply one attack's reward.

## Authoring

`art/storm_summit/build_layout.py` authors the whole map from one seed:

- **Default run:** writes the browser preview to `.local-previews/storm-summit/` (launch entry
  `storm-summit-preview`, port 8390). The preview poses the Roc art study's model at the
  layout's scale, perched, flying and landed, with a size slider, and draws the planned sea
  of clouds.
- **`--install`:** writes the realm tiles (`structure/storm_summit/summit_i_j.nbt`), the
  generated `summit/StormSummitLayout.java` (bounds, cloud height, perches and a rim table),
  the remains' block models, block states and textures, and the two cloud textures.
- **`--check`:** detects drift in all of these.

`StormSummit` holds the realm's own numbers: surface height, slot spacing and arrival.

`tools/summit_client_smoke.init.gradle` is the native check. It enters a slot, photographs
seven viewpoints into `build/summit-client-smoke/screenshots/`, counts the remains, tries to
break the rock as a survival player, drops a player off the east rim to check the rescue,
and leaves. Screenshots are not a Lunar look.

The approved study stays under `art/storm_roc/candidate/`. `build_runtime.py` installs its
model/atlas plus thirteen combat poses and matching server sockets; it converts the
preview's coordinate basis to GeckoLib without changing the approved shape or texture.
Run `python3 art/storm_roc/build_runtime.py --check` and
`python3 art/storm_roc/validate_runtime.py` to check export drift, anatomy and hit-frame
agreement. `render_runtime.py` produces offline review images.

`tools/roc_server_smoke.init.gradle` exercises real damage and encounter lifecycle in a
disposable server. `tools/roc_client_smoke.init.gradle` runs a hidden native summit rehearsal
and captures individual screenshots of the Roc and attacks. Fresh receipts and inspection are required
before claiming either passed; neither establishes human Lunar/co-op balance.
