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

## First-person readability

Custom particles and spell billboards use `SpellViewClearance` to smoothly fade
near the first-person camera, accounting for sprite size and Fire ribbon length.
Restore lifetime alpha after particle submission. Third-person opacity stays
unchanged. Place decorative muzzle bursts through `SpellCastVisuals` so they start
forward/down and stay on the near side of cover. Keep damage/projectile origins
separate from these visual offsets. See `spell-view-clearance-2026-09-10.md`.

## Shared palettes and shape language

| Affinity | Palette | Primary shapes |
| --- | --- | --- |
| Fractured | ivory, pale cyan, muted blue-gray | unstable motes and broken threads |
| Fire | Minecraft flame white, yellow, orange, red | animated fire tongues, rolling fronts, burning runways |
| Wind | pearl, silver, storm slate, tiny blue-gray shadows | pressure planes, shear edges, torn wakes, vanes |
| Stone | natural gray, pale stone edges, subtle sage mineral flecks | carved slabs, fractured rock, dust, plated armor |
| Nature | chartreuse, emerald, bark brown, pollen gold | leaves, thorns, roots, blossoms |
| Space | violet, magenta, cyan, near-black | stars, orbit rings, streaks, void cores |

## Shared wand and HUD

- The universal 16x16 wand is deliberately affinity-neutral: dark wood, aged
  iron bindings, and fractured bone-white crystal prongs. It is the one held
  wand texture for every affinity; unused legacy per-affinity wand textures do
  not define the in-game appearance.
- `wand_hud_v2` uses carved walnut grain, brass corner inlays, and tiny ivory/sage
  rune crystals. Ability glyphs sit inside a six-pixel frame. Stone reserve is a
  matching text-free trough directly above the ability row, with four pull divisions
  and a pale mineral fill at 75+ mass. Cooldown/ultimate numbers sit within their
  slots. Locks use brass; Wind charge pips and all gameplay state retain their behavior.
- Shared gear is generated independently from affinity assets so Fire/Wind
  replacement passes cannot touch Arcane, Stone, Nature, or Space artwork.

## Fire — approved workshop 05

Original generated artwork in `art/fire/workshop/` is the source of truth for
this approved pass. `tools/prepare_fire_assets.py` extracts its four-frame families
with nearest-neighbor sizing and the preview's alpha cutoff. Vanilla fire/lava
inform the palette; whole vanilla fire tiles appear only on ordinary ground fire.

- Primary: three aligned stream planes with a perpendicular curl, using original
  orange/gold art. The existing camera-clearance behavior remains.
- Pyre: original oriented traveling wall (2.8 blocks high) and a distinct lingering
  carpet (0.075 block high) on the original netherrack runway. No standing full-height
  fire or per-block particle wall remains behind the passing front.
- Meteor: approved eight-block rounded sphere of half-block steps, all-fire surface,
  46 surrounding flame strips, 11 longer crown strips, and 78 local ember sprites.
  Ordinary falling blocks retain the vanilla renderer; meteor combat is unchanged.
- Seven existing particle IDs retain their role, with four frames each. HUD icons
  and Fire Spirit are unchanged.
- Browser approval is distinct from Minecraft asset/mesh checks and a human playtest.
  Read `fire-redesign-2026-09-12.md` for the current verification and installation.

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

- `tools/prepare_fire_assets.py` owns the approved 45-PNG Fire package except the
  three retained HUD icons. `generate_fire_vfx_assets.py` forwards to it. The
  exporter removes only superseded Fire-owned frames and models; `--check`
  validates byte-for-byte source/production agreement.
- `tools/generate_wind_vfx_assets.py` owns only the 53 Wind production PNGs:
  42 particle frames, six Vacuum Blade frames, item/worn wings, and three icons.
- The shared generator owns only `wizard_wand.png` and `wand_hud_v2.png`.
  Safe replacement never regenerates Arcane, Fire Spirit, Calamity Tornado,
  Stone, Nature, or Space assets.
- The validated affinity package is Fire 45, Wind 53, Stone 41, Nature 44,
  Space 81: 264 affinity PNGs plus two shared presentation PNGs and 40 particle
  definitions.

## Stone vertical slice

- Stone uses natural gray rock with restrained grain, chipped pale edges and sparse
  desaturated mineral flecks. The September 10 redesign replaces the dark ochre
  family across all 41 production PNGs; see `art/stone/natural-gray/before-after.png`.
- The accepted gray design has a finer Minecraft-style material pass:
  irregular clustered grain, tiny pits and varied bevel shading, with the same
  native resolution and alpha silhouettes. Latest comparison is
  `art/stone/weathered-detail/before-after.png`. The earlier grain-only pass was
  too subtle; the current pass authors broken layers, chipped courses and stronger
  plate shading in addition to grain.
- `stone_fault` is the readable ground telegraph; it races ahead of the damage
  while remaining low enough not to hide targets.
- `stone_shard` and `stone_dust` separate heavy angular debris from lingering
  atmosphere. Shards fall quickly; dust stays low and never behaves like smoke.
- Earthen Maw grows low-poly layered teeth through multiple block states, then
  cracks and sinks instead of popping between ordinary stone cubes.
- Stone Wall uses two broad courses per tile, preserves the same base material
  underneath its fractured ready state, and
  shatters into a forward wedge that follows its real damage volume.
- Titan Dome is the Stone hero effect: mountain ribs close around the arena,
  gray stone plate armor assembles on the caster, and the shell visibly pulls, repairs,
  fractures, and collapses over its lifetime.
- The dome has one dressed stone face per block; the sword has a honed chipped
  edge and a separate charcoal grip. Armor faces are authored per vanilla UV
  region with continuous plates and deliberate front visor openings. Titan
  particles show converging/crumbling stone fragments. Geometry is unchanged.

## Nature — the living grove

- Use vibrant green leaves/roots, bark brown, pink/violet petals and golden pollen.
  Geometry stays stepped and angular, with Minecraft-scale pixel detail.
- The primary is a 3D winged pod. Only planted seedling anchors carry flowers;
  four native cuboid stages unfold inside their selectable block footprint.
- Thickets and water walking use low custom roots and floating leaf rafts.
  Secondary travel/destination effects are vines and leaves, without another bloom.
- Entangle uses attached, chamfered rectangular vine strands that climb the body.
  Active root anchors expire independently of lingering stacks. Guardian resistance
  has no full-root timer; sap glints identify the actual extra recovery second.
- The ultimate is a real 9-high, 11-wide oak block structure, with outstretched
  branches, one heartwood block and a few flowering leaf blocks. Keep normal
  Minecraft logs/leaves dominant. Do not bring back a separate smooth tree model.
- Export with `tools/prepare_nature_models.mjs`; compare with `--check`. Existing
  textures provide pixel grain; exact face colors preserve the approved palette.
  The PNG/particle inventory is unchanged. See `nature-redesign-2026-09-10.md`.


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
