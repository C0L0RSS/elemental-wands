# Elemental Wands VFX Style Guide

## Production rules

- Author gameplay particles at 16x16 or 32x32 and hero/entity sprites at
  64x64. Keep hard pixel edges and use nearest-neighbor scaling only.
- Build silhouettes from deliberate color clusters. Do not use blur, smooth
  gradients, photographic smoke, or baked bloom.
- Match the material richness of modern Minecraft textures: use several
  clustered value steps, small irregular surface families, selective edge
  highlights, and restrained texture noise. Avoid flat emblem-like drawings,
  thick cartoon outlines, single-color fills, or enlarged low-detail shapes.
- Reserve ivory-white cores for the hottest or most powerful parts of an
  effect. Standard combat effects must leave targets readable; ultimates may
  briefly dominate the scene.
- Particle animation frames live as individual PNGs and are ordered by each
  `assets/elementalwands/particles/*.json` file.
- Custom spell blocks keep Minecraft's stepped geometry and pixel density.
  Stone spikes and the Fire meteor use compact low-poly models because their
  silhouettes carry gameplay readability; temporary ground surfaces remain
  ordinary blocks with weighted texture variants where repetition is visible.

## Shared palettes and shape language

| Affinity | Palette | Primary shapes |
| --- | --- | --- |
| Fractured | ivory, pale cyan, muted blue-gray | unstable motes and broken threads |
| Fire | charcoal, oxblood, molten orange, rare ivory-hot cores | clinker, fractured seams, ash, furnace fronts |
| Wind | pearl, silver, storm slate, tiny blue-gray shadows | pressure planes, shear edges, torn wakes, vanes |
| Stone | ochre, slate, warm gray, pale rune light | shards, dust, cracks, angular runes |
| Nature | chartreuse, emerald, bark brown, pollen gold | leaves, thorns, roots, blossoms |
| Space | violet, magenta, cyan, near-black | stars, orbit rings, streaks, void cores |

## Shared wand and HUD

- The universal 16x16 wand is deliberately affinity-neutral: dark wood, aged
  iron bindings, and fractured bone-white crystal prongs. It is the one held
  wand texture for every affinity; unused legacy per-affinity wand textures do
  not define the in-game appearance.
- `wand_hud_v2` uses neutral charcoal, iron, and bone. Affinity-colored ability
  overlays, padlocks, cooldown masks, ultimate charge, fractured-state cues,
  and Wind dash pips remain separate and retain their behavior.
- Shared gear is generated independently from affinity assets so Fire/Wind
  replacement passes cannot touch Arcane, Stone, Nature, or Space artwork.

## Fire — Cinderforge Cataclysm

- Fire is a material effect first: charcoal crust, oxblood recesses, molten
  seams, irregular clinker, hanging ash, and very sparse ivory-hot fractures.
  Avoid clean flame emblems, smooth orange gradients, and perfect circles.
- `fire_ember`, `fire_ash`, `fire_flame_ribbon`, and `fire_impact_ring` use
  clustered shading and broken silhouettes. `fire_meteor` is directional crust
  debris rather than a collection of miniature meteor balls.
- Inferno Wave is a six-frame 64x64 Cinder Maw. Layered clinker teeth close
  around a molten throat seam while rare white fracture points spall outward.
  Its particle wake is interpolated between ticks so it reads as one advancing
  front at full projectile speed.
- Dragon's Pyre uses six `fire_pyre_fissure` frames below an eight-frame
  `fire_pyre_front`. Four weighted coal variants carry branching molten seams
  across the runway without repeated ribbon tiling.
- Maximum Meteor uses an irregular multi-cuboid basalt core, an eight-frame
  landing brand, and a twelve-frame impact sequence: ivory contact, molten
  shockwave, basalt debris, embers, then ash. The stored target is visual-only;
  its brand is projected to the landing surface, and the existing explosion
  remains the sole source of gameplay behavior.
- `inferno_flame` is a four-frame vertical block animation. Fire Spirit and all
  of its assets are explicitly outside this redesign.

The approved concept boards establish visual direction only. Production PNGs
are rebuilt at exact game resolutions and validated for RGBA transparency.
The Fire/Wind reference lives at
`docs/vfx-concepts/fire-wind-second-generation-concept.png`; the Stone/Nature/
Space reference lives at `docs/vfx-concepts/stone-nature-space-concept.png`.
Neither concept image is ever used directly as a game texture.

## Wind — Sky Shear

- Wind reads pearl-white first. Silver edges and storm-slate seams separate its
  layered pressure planes; saturated cyan is excluded so it cannot resemble Ice.
- `wind_mote` is a pressure chip, `wind_crescent` a faceted cut,
  `wind_air_ribbon` a broken directional plane, and `wind_burst_ring` an
  asymmetric pressure tear rather than a perfect ring.
- Vacuum Blades are six-frame 64x64 shear assemblies with layered plates and
  serrated edges. One tracked mirror flag makes the pair oppose each other.
  Their torn wake is interpolated between ticks and impacts originate at the
  collision point, while speed, damage, range, and targeting stay unchanged.
- Waylay Dash emits a five-tick `wind_slipstream` tracer from the player's real
  movement. A chained dash adds a second outer shear lane; recharge, charge
  consumption, and movement remain unchanged.
- Zephyr's item and worn wings retain vanilla Elytra geometry but become layered
  pearl shear vanes. `wind_shear_feather` fragments, ascent streams, descent
  compression circles, and the dense eight-frame landing disc/strike column
  are presentation only; launch, fall detection, impact, and armor restoration
  remain the existing gameplay sequence.
- Calamity Tornado and its assets are explicitly outside this redesign.

## Production ownership and counts

- `tools/generate_fire_vfx_assets.py` owns only the 77 Fire production PNGs:
  62 particle frames, six Cinder Maw frames, five block textures (including the
  four-frame flame sheet as one PNG), the meteor core, and three HUD icons.
- `tools/generate_wind_vfx_assets.py` owns only the 53 Wind production PNGs:
  42 particle frames, six Vacuum Blade frames, item/worn wings, and three icons.
- The shared generator owns only `wizard_wand.png` and `wand_hud_v2.png`.
  Safe replacement never regenerates Arcane, Fire Spirit, Calamity Tornado,
  Stone, Nature, or Space assets.
- The validated affinity package is Fire 77, Wind 53, Stone 41, Nature 44,
  Space 81: 296 affinity PNGs plus two shared presentation PNGs and 42 particle
  definitions.

## Stone vertical slice

- Stone is Raw Seismic rather than arcane runework: charcoal slate, warm-gray
  strata, muted ochre fracture seams, mineral highlights, and pale weighted dust.
- `stone_fault` is the readable ground telegraph; it races ahead of the damage
  while remaining low enough not to hide targets.
- `stone_shard` and `stone_dust` separate heavy angular debris from lingering
  atmosphere. Shards fall quickly; dust stays low and never behaves like smoke.
- Earthen Maw grows low-poly layered teeth through multiple block states, then
  cracks and sinks instead of popping between ordinary stone cubes.
- Stone Wall assembles from stratified slabs, shows a fractured ready state, and
  shatters into a forward wedge that follows its real damage volume.
- Titan Dome is the Stone hero effect: mountain ribs close around the arena,
  basalt armor assembles on the caster, and the shell visibly pulls, repairs,
  fractures, and collapses over its lifetime.

## Nature vertical slice

- Nature is Fairy Bloom: emerald and mint foliage, dark bark, ivory flowers,
  pollen gold, paired stems, unfurling buds, petals, and luminous root spokes.
- `nature_pollen`, `nature_petal`, and `nature_leaf` provide small material
  detail without falling back to generic Happy Villager or spore particles.
- The primary seed is a visible winged pod. Planted seedlings advance from bud
  to crown and make each radius pulse readable as an unfolding growth ring.
- Tendril Bloom uses two intertwined stems with intermittent flowers; its final
  fairy ring keeps low thorn silhouettes so enemies do not mistake it for a
  harmless healing zone.
- Entangle retains its synchronized border vignette. World cues add one coil and
  bud per stack, culminating in a rooted five-stack flower crown.
- The Awakened Tree is the Nature hero effect: consumed seedlings stream into a
  pollen-gold heart, ten roots establish the true radius, and a dense ivory
  canopy reflects healthy, damaged, destroyed, and naturally expired states.

## Space vertical slice

- Space is Eldritch Horizon rather than generic purple magic: near-black cores,
  bruised violet, cold cyan, magenta, sparse bone-white stars, warped rings, and
  light visibly moving inward.
- Singularity Bolt is a compact black star whose wake bends only inside a shallow
  launch cone. Its impact breaks gravity outward through a ragged expansion ring
  and radial starlight, with no suction tether or displaced targets.
- Blink Rift leaves a standing eclipse slit whose six orbit fragments disappear
  over its six-second return window. Return, blocked return, and expiry each have
  distinct closure beats.
- Hollow Purple's cyan and magenta dying stars darken as they converge into an
  ivory pinpoint. Release inverts that light into the Devouring Eclipse.
- The Devouring Eclipse is the Space hero effect: a huge void core with a ragged
  corona, gravity-lens rings, backward-dragged starlight, terrain-consumption
  fragments, and a line-to-pinpoint final collapse instead of a simple despawn.
