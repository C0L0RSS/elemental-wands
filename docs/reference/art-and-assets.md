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
| `art/nature/blocknature/` | Block-nature browser study and painted atlas source for the Nature spell effects |
| `art/space/blockspace/` | Void-glass browser study, painted atlas source and builder for all five Space spells, and the Astral Double's glass-shell textures (`build_astral_glass.py`) |
| `art/wind/cloud-white/`, `tools/generate_wind_vfx_assets.py` | Cloud-white Wind atlas, native-grid frames, wing item icon and ability icons (the Wind particles and the worn-wing texture are unused now) |
| `tools/generate_shared_vfx_assets.py` | Legacy neutral wand sprite and wooden HUD frame only |
| `tools/generate_stone_vfx_assets.py` | Stone spell, block, equipment and HUD textures (41-image family) |
| `tools/generate_nature_vfx_assets.py` | Nature particle, entity and HUD textures (44-image family); separate from model/layout exporters |
| `tools/generate_space_vfx_assets.py` | Space particle, entity and HUD textures (81-image family; the Space particles are unused now) |
| `art/nature/workshop/`, `tools/prepare_nature_models.mjs` | Nature pod, the Sentry Bloom flower (`sentryBloom.js`), roots and tree layout |
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
| `art/pyre_king/build_art.py` | Pyre King model, texture, glow mask, standalone thrown sword, animation clips for every move in his kit (`clips.py`, solved by `rig.py`), world-up blade-fire preview that streams behind a swung blade, and the phone-friendly local workshop (port 8410; also publishable as a static page). `--install` writes the game's model, clips, textures, `pyre_king_effects.json` and `PyreKingClips.java`; `--check` verifies the candidate and the installed files; `render_clips.py` renders clip strips. See [Pyre King](pyre-king.md); `--install` also writes `pyre_king_tracks.json`, where his wrist, pommel and blade tip are 120 times a second in every attack clip, which the fight sweeps for hits |
| `art/pyre_hall/build_layout.py` | The Pyre King's Burned Throne Hall: floor, pillars, wall, throne, roof ribs, crag and causeways; phone-friendly browser preview (port 8420) with the king posed for scale; `--install` writes the realm tiles, `--check` verifies |

Do not run generators with replacement options unless intentionally changing their
owned families. A Fire/Wind pass must not regenerate Space/Arcane, Stone, Nature,
or unrelated creature artwork. `generate_fire_vfx_assets.py` delegates to the
approved Fire exporter; its old name does not mean it owns every Fire-like asset.
Inspect a generator’s output map and replacement behavior before regeneration.
Prefer available `--check` modes for validation; not every generator supports
that flag, so check its arguments before running it.

A `--check` compares bytes, and the project is built on both Windows and macOS, so a
generator must write the same bytes on each. Three things have broken that before:

- **Compressed files.** Python's gzip header carries an operating-system byte that varies by
  version and platform. Structure files go through `gzip_stable` in
  `art/hollow_crypt/build_layout.py`, which fixes it.
- **Unrounded decimals.** Trigonometry differs in the last digits between machines, and a
  rounded tiny negative becomes `-0.0`. Round before writing and add `0.` to the result, as
  `stable` does in `art/pyre_king/build_art.py`.
- **Ignored folders.** A check must not require files under `.local-previews/`, which exist
  only where they were built.

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

Nature uses a winged pod, a staged flower that spits seeds and a
block-built oak (retired from play with the Overgrowth ultimate, art kept). The oak's logs and leaves are look-alike blocks that reuse the vanilla
oak models and textures (their own blockstates; solid trunk, walk-through leaves); keep that ordinary
Minecraft look dominant and do not restore the superseded smooth tree model. The
throwable acorn extends this style. The Sentry Bloom flower is cut in two so its head
can turn (`art/nature/workshop/sentryBloom.js`). The base is the four-stage
`nature_seedling` block model: root crown, braced leaves and a stem that rises to 0.6875
blocks. The head is three render meshes in `nature/meshes.json` (`sentry_head`,
`sentry_head_fired`, `sentry_head_wilt`), authored mouth-up with the pivot at the origin: a
stem-coloured knuckle, a slim tapered green calyx and eight broad pink petals cupped
around a thin violet throat, with one small loaded gold seed at its centre 0.53125 blocks
along the axis, where the game fires from. Face-on it must read as a flower: the petals
take up far more of the view than the centre (the first head, a fat studded seed sac with
a large seed and four small petals, was rejected as too pod-like).
It clears its own leaves at any yaw up to 105 degrees from upright, the limit the game
keeps to. Upright it stands about 1.44 blocks tall. It has no face. While its owner's Full
Bloom lasts the base is `nature_seedling_grown` (block state `grown=true`), shaped like a
plant rather than a fork: a leaf rosette 2 blocks across (the normal one is a block), so a
boosted flower reads at a glance; one tall main stem that bends away from the branch to the
full-size head at (0.3125, 1.5, 0) from the bottom centre; and a thinner side branch that splits off 0.5625
blocks up (37.5% of the main stem) and rises outward to a three-quarter-size head at
(-0.6875, 1.125, 0.125). `SentryBloomEntity.MAIN_STEM_TOP`/`BRANCH_TOP` and their scales
must match the art. The base is 61 cuboids, 2 blocks wide and 1.5 tall (model bounds
(-8, 0, -8) to (24, 24, 24)); with upright heads the flower is about 2.14 blocks tall, and
its leaves reach half a block into neighbouring cells. In a 5-degree yaw/tilt sweep through
0-105 degrees two heads aimed the same way never touch, and neither sinks into a stem at
level aim; the small head's petals clip its own branch slightly at tilts of 95-105 degrees.
GPT Astra designed it on November 10 after the developer found the earlier symmetric fork
too unlike a flower, then raised the branch and widened the rosette at the developer's
request; the main stem's bend, which balances the two heads, was added afterwards on the same
request. The developer approved the stem-and-branch direction from previews and client
fixture screenshots; this final revision has been seen only in those, not in Lunar. The target-wrap vines, bramble carpet and root-knot models
are retired from play but kept, with their exporters, for possible reuse.

Nature's spell effects are block nature: opaque pieces textured from
`textures/misc/blocknature.png` (8 bands × 4 frames: leaf, dark leaf, pink, violet, gold, bark,
sap, cream), copied from `art/nature/blocknature/textures/blocknature.png`
(`build_atlas.py --check` verifies the source). Three shapes only: a petal of two stepped slabs, a
leaf of three boxes (blade, tip, raised vein) and a pollen kernel. Nothing is see-through or faces
the camera, and nothing glows like a card: pieces take the world's light, gold and sap a little
less so. Keep them chunky and few, and let them leave by shrinking, never by fading. Unlike Fire,
Wind and Space it is not cube-like by design: the developer asked for the same 3D style, not
pure cubes. GPT Astra built the study on November 11; Claude strengthened the heal cue and the
seed trail before the port.

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

See [AGENTS.md](../../AGENTS.md#build-and-required-checks) for the required validation commands and
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
