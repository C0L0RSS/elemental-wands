# Art and assets

## Shared direction

Use Minecraft-like stepped geometry and deliberate pixel clusters. Gameplay
particles usually use 16×16 or 32×32 frames and hero sprites 64×64; preserve an
approved asset's native resolution and pixel density. Use nearest-neighbor scaling,
material value steps and selective highlights. Avoid photographic grain, blur,
smooth gradients, baked bloom, thick cartoon outlines, and flat single-color fills.

Combat visuals must leave targets readable. Use `SpellViewClearance` for near-camera
fades and `SpellCastVisuals` for decorative muzzle offsets; never move the damage
origin merely to improve appearance. Restore lifetime alpha after submission and
preserve third-person opacity. Cover checks still apply to visual spawn offsets.

| Element | Palette and silhouette |
| --- | --- |
| Neutral | Ivory, pale cyan, muted gray; fractured threads and motes |
| Fire | Minecraft flame colors; tongues, rolling fronts, burning runways |
| Wind | Cloud white/pearl/light gray; curling gusts, rounded cloud puffs and flowing wakes; restrained pale blue shading |
| Stone | Natural gray with restrained mineral flecks; chipped slabs and worn plates |
| Nature | Green foliage, bark, pink/violet flowers, golden pollen; angular living growth |
| Space | Near-black, violet, magenta, cold cyan; dark see-through void glass with stars inside, eclipse cores and slits; Hollow Purple's wind-up is blue and red |

The [3D wand](wand-rendering.md) uses muted patterned cores in glass and coarse
wood grain. The old neutral flat sprite is now a fallback/particle asset.
The HUD uses carved wood, brass and rune accents; Stone reserve sits above it.
The parchment hub uses element-specific paper damage and layered corner artwork.

## Source ownership

Keep reusable art, editor projects and generators tracked. Browser previews are
local ignored artifacts; they are not runtime assets or proof of Minecraft output.
Runtime resources live under `src/main/resources/assets/elementalwands/`.

| Source / exporter | Owns |
| --- | --- |
| `art/fire/blockfire/` | Block-fire browser study, atlas source and builder for all six Fire spells |
| `art/fire/workshop/`, `tools/prepare_fire_assets.py` | Earlier workshop 05 Fire frames and meshes (Fire particles still use them) |
| `art/wind/blockwind/` | Block-wind browser study, painted atlas source and builder for all five Wind spells |
| `art/space/blockspace/` | Void-glass browser study, painted atlas source and builder for all five Space spells, and the Astral Double's glass-shell textures (`build_astral_glass.py`) |
| `art/wind/cloud-white/`, `tools/generate_wind_vfx_assets.py` | Cloud-white Wind atlas, native-grid frames, wing item icon and ability icons (the Wind particles and the worn-wing texture are unused now) |
| `tools/generate_shared_vfx_assets.py` | Legacy neutral wand sprite and wooden HUD frame only |
| `tools/generate_stone_vfx_assets.py` | Stone spell, block, equipment and HUD textures (41-image family) |
| `tools/generate_nature_vfx_assets.py` | Nature particle, entity and HUD textures (44-image family); separate from model/layout exporters |
| `tools/generate_space_vfx_assets.py` | Space particle, entity and HUD textures (81-image family; the Space particles are unused now) |
| `tools/prepare_nature_models.mjs` | Approved Nature pod, flowers, roots, rafts and tree layout |
| `art/nature/springbloom/model.js`, `tools/prepare_springbloom.mjs` | Approved Springbloom pad, wilt and pod meshes, with capped pieces for partial flowers; no PNGs |
| `tools/prepare_nature_expansion.py` | Root-knot native model |
| `art/wand/design.json`, `tools/prepare_wand_assets.py` | Dynamic wand meshes and textures |
| `tools/prepare_wand_hub_assets.py`, `tools/wand_hub_corner_art.py` | Parchment menu and physical corner layers |
| `tools/prepare_guardian_assets.py` | Guardian body, glow/crack textures and merged animation |
| `tools/prepare_guardian_throw_socket.py` | Server socket derived from authored animation |
| `tools/prepare_guardian_pedestal.py` | Native offering pedestal models |
| `art/hollow_necromancer/v2/build_art.py` | Authored mage/skeleton geometry, textures, animation and sampled server grab/slam and bite/throw wrist sockets; Pillow and NumPy |
| `art/hollow_necromancer/soul_bolt/build_preview.py` | Authored skull, hinged jaw, flight/impact animations, glow mask and prepared particle frames; `--install` exports mod resources, `--check` verifies them |
| `art/hollow_necromancer/grasping_hands/build_preview.py` | Three skeletal hand proportions, articulated claw animation and cyan glow masks; `--install` exports, `--check` verifies |
| `art/hollow_necromancer/undead/build_preview.py`, `upright.py`, `build_runtime.py` | Hollow Crawler, Risen Archer and Hunched Brute models, textures, glow masks and clips; `build_preview.py` writes the review candidate, `build_runtime.py` installs GeckoLib files and the generated `HollowUndeadClips` timings (`--check` verifies and re-proves the GeckoLib pose matches the review viewer) |
| `tools/prepare_soul_light.py` | Stepped soul-cyan ground pool under the Necromancer's glowing spells; `--check` verifies it |
| `tools/build_guardian_church.py` | Ruined/restored church layouts and structure resources |
| `art/storm_summit/build_layout.py` | Storm Summit realm tiles, generated `StormSummitLayout`, remains' blocks and cloud textures; `--check` verifies |
| `art/storm_roc/build_preview.py` | Storm Roc art study: the skeleton and clips the game model rides, and the head crackles; review-only, `--check` verifies |
| `art/storm_roc_texture_study/build_texture_v3.py`, `art/storm_roc_mod/build_mod.py` | Storm Roc bearded-vulture atlas, eye glow mask and 307-block model; rebuild texture, then model, then runtime (see that folder's README) |
| `art/storm_roc/build_runtime.py` | Storm Roc GeckoLib model, atlas, twenty-one clips and server sockets; `--check` verifies, `validate_runtime.py` checks anatomy and hit timing |
| `art/roc_effects/build_tornado_texture.py` | Roc tornado atlas; `--check` verifies |
| `art/pyre_king/build_art.py` | Pyre King review model, texture, glow mask, standalone thrown sword, animation clips including all three sword strings (`clips.py`, solved by `rig.py`), world-up blade-fire preview that streams behind a swung blade, and the phone-friendly local workshop (port 8410; also publishable as a static page). Review-only, not installed; `--check` verifies, `render_clips.py` renders clip strips |

Do not run generators with replacement options unless intentionally changing their
owned families. A Fire/Wind pass must not regenerate Space/Arcane, Stone, Nature,
or unrelated creature artwork. `generate_fire_vfx_assets.py` delegates to the
approved Fire exporter; its old name does not mean it owns every Fire-like asset.
Inspect a generator’s output map and replacement behavior before regeneration.
Prefer available `--check` modes for validation; not every generator supports
that flag, so check its arguments before running it.

## Element-specific constraints

Fire spells are block fire: cubes textured from `textures/misc/blockfire.png`
(8 heat bands × 4 frames, built by `art/fire/blockfire/textures/build_atlas.py`),
reviewed first in the `art/fire/blockfire/` browser study. The traveling Pyre wall
and very low lingering carpet are distinct. Ordinary ground fire uses
vanilla-looking flames; the meteor is a stepped all-fire sphere. Preserve
ordinary falling-block rendering.

Wind spells are block wind: cubes textured from `textures/misc/blockwind.png`
(8 bands × 4 frames: gale, cloud, mist, streak, swirl, feather, pearl, silver),
built from a painted source by `art/wind/blockwind/textures/build_atlas.py`;
the runtime copy must match its output byte for byte. Air is see-through and fades
by shrinking; only blade rims, wings, motes and the dagger are solid. Thin streaks
read as wires, loose solid cubes as debris and lines meeting at angles as
lightning, so trails stay puffy or curved. The Gale Dagger keeps its approved
flat-coloured box model; only its forging swirl, trail rings and hit puff are block wind.

The older cloud-white sprites use bright clusters with light pearl/blue-gray shading, broad
curling gusts, open pressure rings and cloudburst impacts. Avoid dark outlines,
metallic plates and torn shard silhouettes. The source atlas is exported to the
existing native grids with nearest-neighbor sampling and a fixed eight-color
palette. White particle vertex tints preserve the authored colors; lifetime and
near-camera fades remain active. `generate_wind_vfx_assets.py --check` verifies
all 53 outputs against the source without rewriting assets.

Gale Daggers uses the approved code-native stepped blade, raised ridge, compact
crossguard and wrapped grip in `GaleDaggerRenderer`. The model points along local
negative Z; the renderer rotates it to face the caster or projectile direction.
Pearl-white/light-gray faces use the vanilla white-concrete texture. Three models
hover overhead, with the center raised; the hub icon is composed in `SpellIcons`.
The `gale_dagger_trail` particle reuses the approved burst-ring frames. Small
cloud-white rings sit perpendicular to flight every 1.5 blocks, expand slightly
and fade over five ticks. Rings stay where they spawn, stop at collision, and
respect near-camera clearance; idle daggers emit none. `GaleRingParticle` owns
the orientation and fade, independently of the larger Wind impact rings.

Updraft emits twenty vanilla cloud particles radially at the launch point to
form an expanding smoke ring close to the floor. Existing Wind air ribbons form
a brief eight-tick rising spiral. Its upward-gust icon is composed in `SpellIcons`;
no new bitmap assets are generated.

Wind wings keep vanilla Elytra geometry; dash tracers and landing effects are
presentation, not movement authority. The held wand has a separate palette and
is outside this texture pass. Calamity Tornado is retired.

Faultline uses the code-native stepped spike mesh in `FaultlineSpikeRenderer`,
with the existing approved `stone_spike.png` material. Its visual entities have
no solid collision and expire immediately after the moving crest. The two new
Technique icons use native pixel compositions in `SpellIcons`, preserving the
existing spell-texture inventory.

Stone's approved material sources are `art/stone/natural-gray/` and the subsequent
`art/stone/weathered-detail/` revision. Gathered Mass replaced the old ground-spike
primary. Keep the chipped wall/dome/equipment materials and gray ability art.

Nature uses a winged pod, native cuboid flowers, low roots/rafts, angular vines,
and a block-built oak. Keep ordinary Minecraft logs/leaves dominant; do not restore
the superseded smooth tree model. The root knot and throwable acorn extend this style.

Space spells are void glass: pieces textured from `textures/misc/blockspace.png`
(8 bands × 4 frames: void, glass, nebula, rim, amethyst, blue, red, starlight), built
from a painted source by `art/space/blockspace/textures/build_atlas.py`; the runtime copy
must match its output byte for byte. Glass and nebula carry their own alpha (frame and stars
opaque, interior see-through); the other bands are solid. Space falls inward: streams
accelerate into a core, stretch along the fall and shrink away; only bursts move outward.
Cores are opaque void with a corona that faces the camera, and the Blink slit turns to face
it too. Keep pieces few and large. The Singularity Bolt's hit and miss have no flat rings
along the ground (the user removed them). Visual terrain fragments do not authorize world edits.

## Asset contracts and resource conventions

The validator counts Fire 45, Wind 53, Stone 41, Nature 44, Space 81 PNGs:
264 affinity images plus two shared images, and 43 particle definitions (including two Necromancer effects).
Guardian creature textures, church textures, menu textures, and the three wand
textures are separate from that spell/HUD contract. Do not change counts to hide
an accidentally removed or regenerated asset. The unused rendered seed sprite
`textures/entity/winged_seed.png` remains in the Nature contract intentionally.

GeckoLib 5.3-alpha-3 uses `geckolib/models/*.geo.json` and
`geckolib/animations/*.animation.json`. Model format is `1.12.0` with a
`minecraft:geometry` array; declared texture dimensions must match the PNG.
Minecraft 1.21.10 items need both `items/*.json` and `models/item/*.json`.
Spawn eggs use `settings.spawnEgg(entityType)`.

See [AGENT.md](../../AGENT.md) for the required validation commands and
[Guardian combat](guardian-combat.md) for the multi-source animation pipeline.

Astral Double uses the owner's native player skin (wide or slim arms and outer
skin layers) through `AstralDoubleRenderer`, with the existing Space wand model. The top-level
state wraps the player appearance so Minecraft does not route it to its vanilla
player renderer. A `GlassShell` feature draws the same pose again on a slightly inflated
player model, textured by `textures/entity/astral_glass_{wide,slim}.png` so every limb face is
void glass with a bright frame; it is bright as the double forms and settles within a second.
The orb, the formation stream, the circling plates, the stars and the poof are void glass from
`client/blockspace/`. The ability icon is a code-native pair of figures in `SpellIcons`.

Gravity Well is void glass: the bomb is a void cube inside six glass plates that part with
each pulse, with three rim satellites. On impact the plates blow open into the field: a
hovering eclipse core with two broken orbits, a dark stepped floor disk, a segmented rim
boundary at the four-block pull radius, and a floor vortex of large glass plates that spiral
in from the boundary to the core in about 2.3 seconds. The buildup speeds the vortex, swells
the core and tightens the boundary; the burst blows a rim ring and glass shards outward. The
entity's tracked phase timestamps drive the sequence; no per-piece network traffic is needed.
The boundary, vortex and core remain visible with reduced particle settings. `SpellIcons` owns
the code-native core-and-orbit glyph.
