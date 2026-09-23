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
| Space | Near-black, violet, magenta, cold cyan; stars, eclipse slits and warped rings |

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
| `art/fire/workshop/`, `tools/prepare_fire_assets.py` | Approved workshop 05 Fire frames and meshes |
| `art/wind/cloud-white/`, `tools/generate_wind_vfx_assets.py` | Cloud-white Wind atlas, native-grid frames, item/worn wings and icons |
| `tools/generate_shared_vfx_assets.py` | Legacy neutral wand sprite and wooden HUD frame only |
| `tools/generate_stone_vfx_assets.py` | Stone spell, block, equipment and HUD textures (41-image family) |
| `tools/generate_nature_vfx_assets.py` | Nature particle, entity and HUD textures (44-image family); separate from model/layout exporters |
| `tools/generate_space_vfx_assets.py` | Space particle, entity and HUD textures (81-image family) |
| `tools/prepare_nature_models.mjs` | Approved Nature pod, flowers, roots, rafts and tree layout |
| `art/nature/springbloom/model.js`, `tools/prepare_springbloom.mjs` | Approved Springbloom pad, wilt and pod meshes, with capped pieces for partial flowers; no PNGs |
| `tools/prepare_nature_expansion.py` | Root-knot native model |
| `art/wand/design.json`, `tools/prepare_wand_assets.py` | Dynamic wand meshes and textures |
| `tools/prepare_wand_hub_assets.py`, `tools/wand_hub_corner_art.py` | Parchment menu and physical corner layers |
| `tools/prepare_guardian_assets.py` | Guardian body, glow/crack textures and merged animation |
| `tools/prepare_guardian_throw_socket.py` | Server socket derived from authored animation |
| `tools/prepare_guardian_pedestal.py` | Native offering pedestal models |
| `tools/build_guardian_church.py` | Ruined/restored church layouts and structure resources |

Do not run generators with replacement options unless intentionally changing their
owned families. A Fire/Wind pass must not regenerate Space/Arcane, Stone, Nature,
or unrelated creature artwork. `generate_fire_vfx_assets.py` delegates to the
approved Fire exporter; its old name does not mean it owns every Fire-like asset.
Inspect a generator’s output map and replacement behavior before regeneration.
Prefer available `--check` modes for validation; not every generator supports
that flag, so check its arguments before running it.

## Element-specific constraints

Fire uses original four-frame workshop art. The traveling Pyre wall and very low
lingering carpet are distinct. Ordinary ground fire uses vanilla-looking flames;
the meteor is a stepped all-fire sphere. Preserve ordinary falling-block rendering.

Wind uses bright cloud-white clusters with light pearl/blue-gray shading, broad
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

Space keeps distinct inward/outward motion: the bolt impact uses an outward ring,
Blink Rift a standing eclipse slit, and Hollow Purple converging stars followed
by the Devouring Eclipse. Visual terrain fragments do not authorize world edits.

## Asset contracts and resource conventions

The validator counts Fire 45, Wind 53, Stone 41, Nature 44, Space 81 PNGs:
264 affinity images plus two shared images, and 41 particle definitions.
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
