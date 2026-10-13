# Storm Summit

The Storm Roc's realm. The map exists, but the fight, the Overworld aerie and the intro don't
yet; the planned design is the Storm Roc section of the [roadmap](../ROADMAP.md). Owners are
under `src/main/java/com/anton/elementalwands/summit/`.

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

The summit has no fight yet. Operators raise and visit a slot with `/ew summit enter [slot]`.
Anyone on the summit can use `/ew summit leave`, which returns them to where they entered.
`/ew summit status` reports the generation and built slots. A slot is laid out the first
time it is used, one 48×48 tile per server tick (70 tiles), and is then reused. When the
installed tiles change, every slot moves to untouched ground. `elementalwands/storm-summit.json`
records the built slots and return points.

A player outside the rim and more than 4 blocks below the summit, or anywhere below the
clouds, has fallen (`StormSummit.fallen`). For now, the wind sets them back at the arrival
point. The fight will decide what a fall costs. Survival players can't break the mountain
or place blocks on it. Spells and explosions destroy no blocks. Nothing drops on death: the
respawned player keeps their inventory and experience. Teleports aren't contained yet.

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
