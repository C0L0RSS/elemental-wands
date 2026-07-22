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
- Custom spell blocks use ordinary Minecraft block geometry in this pass so a
  later Blockbench model can replace the model without changing gameplay code.
  Stone is the exception: its gameplay depends on a jagged eruption silhouette,
  so its spike uses a compact low-poly block model while retaining Minecraft's
  stepped geometry and pixel density.

## Shared palettes and shape language

| Affinity | Palette | Primary shapes |
| --- | --- | --- |
| Fractured | ivory, pale cyan, muted blue-gray | unstable motes and broken threads |
| Fire | ivory, gold, orange, crimson, charcoal | embers, forked ribbons, ash, molten rings |
| Wind | pearl white, silver, cool gray, tiny blue-gray shadows | crescents, streamlines, feathers, burst rings |
| Stone | ochre, slate, warm gray, pale rune light | shards, dust, cracks, angular runes |
| Nature | chartreuse, emerald, bark brown, pollen gold | leaves, thorns, roots, blossoms |
| Space | violet, magenta, cyan, near-black | stars, orbit rings, streaks, void cores |

## Fire vertical slice

- `fire_ember`: tiny angular hot fragments with a short upward drift.
- `fire_flame_ribbon`: stepped forked flame used for travel and ground fronts.
- `fire_ash`: dark cooling fragments with low gravity and a soft alpha fade.
- `fire_impact_ring`: age-animated broken circular ring used at hits and impacts.
- `fire_meteor`: age-animated hot fragments used around the custom meteor core.
- Inferno Wave uses a full-bright billboard crest plus restrained motes.
- Dragon's Pyre uses `pyre_coals` for its surface and custom flame/ember fronts.
- Maximum Meteor uses `meteor_core`, meteor fragments, ash, and impact rings.

The approved concept board establishes visual direction only. Production PNGs
must be rebuilt at exact game resolutions and validated for RGBA transparency.
The combined reference for the second phase lives at
`docs/vfx-concepts/stone-nature-space-concept.png`; it is likewise never used
directly as a game texture.

## Wind vertical slice

- Wind must read pearl-white first. Desaturated blue-gray is limited to edge
  separation and shadow pixels so the affinity cannot be mistaken for Ice.
- `wind_mote`: tiny pressure glints used as restrained travel and impact detail.
- `wind_crescent`: thin compressed-air arcs for blade and landing accents.
- `wind_air_ribbon`: stepped streamlines used for projectiles, dashes, and dives.
- `wind_burst_ring`: age-animated pressure rings for casts and compact impacts.
- `wind_zephyr_impact`: 64px hero sequence reserved for Zephyr launch/landing.
- Vacuum Blades use a full-bright crescent billboard with short ribbon trails.
- Waylay Dash keeps its two-charge HUD pips and gains a directional ribbon burst.
- Zephyr Strike uses hidden custom wings, descending pressure rings, and a
  custom landing impact while leaving its launch and explosion mechanics intact.

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
- Singularity Bolt is a compact black star. Its travel wake and contracting
  impact ring point toward the pull center instead of reading as an explosion.
- Blink Rift leaves a standing eclipse slit whose six orbit fragments disappear
  over its six-second return window. Return, blocked return, and expiry each have
  distinct closure beats.
- Hollow Purple's cyan and magenta dying stars darken as they converge into an
  ivory pinpoint. Release inverts that light into the Devouring Eclipse.
- The Devouring Eclipse is the Space hero effect: a huge void core with a ragged
  corona, gravity-lens rings, backward-dragged starlight, terrain-consumption
  fragments, and a line-to-pinpoint final collapse instead of a simple despawn.
