# Elemental Wands VFX Style Guide

## Production rules

- Author gameplay particles at 16x16 or 32x32 and hero/entity sprites at
  64x64. Keep hard pixel edges and use nearest-neighbor scaling only.
- Build silhouettes from deliberate color clusters. Do not use blur, smooth
  gradients, photographic smoke, or baked bloom.
- Reserve ivory-white cores for the hottest or most powerful parts of an
  effect. Standard combat effects must leave targets readable; ultimates may
  briefly dominate the scene.
- Particle animation frames live as individual PNGs and are ordered by each
  `assets/elementalwands/particles/*.json` file.
- Custom spell blocks use ordinary Minecraft block geometry in this pass so a
  later Blockbench model can replace the model without changing gameplay code.

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
