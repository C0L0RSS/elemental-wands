# Fractured Guardian — texture study 02

The approved 132-cube, 38-bone model with its first material pass. Geometry,
pivots, bone hierarchy, and dimensions are preserved exactly from `../v1`.

## Review artifacts

- `preview.html`: offline WebGL view of the actual textured model. Front, side,
  back, orbit, zoom, turntable, and a clay comparison toggle are available.
- `model-review.png`: four actual model views rendered from the same UV data.
- `fractured_guardian.bbmodel`: editable GeckoLib project with embedded texture.
- `fractured_guardian.geo.json`: matching geometry export with per-face UVs.
- `fractured_guardian.png`: original imagegen texture atlas, copied unchanged.
- `materials.json`: per-face material/UV assignments and intended magic regions.
- `imagegen-prompt.txt`: exact prompt used with the built-in imagegen tool and
  `../v1/approved-concept.png` as the material reference.
- `validation.json`: geometry preservation, UV, and texture verification results.

## Material direction

Gray mineral stone with varied clustered values; darker recesses and undersides;
muted olive moss on selected upper ledges and sheltered edges; worn spiral,
angular, and broken carvings on a few shoulder/forearm/back plates; pale magical
eye slits and two branching chest fractures. The 16 atlas tiles are reusable
material swatches. Most faces take different padded samples at a similar texel
density. Carvings and eye details use deliberate dedicated UV regions.

The atlas was made with the built-in imagegen tool. The original PNG is retained
without raster editing. The requested 1024×1024 atlas was returned at 1254×1254
RGB, so the project and geometry dimensions match the actual returned image.
This is a **texture review source**, not a finalized runtime texture package.

## Runtime integration status

The command-spawned static prototype is now implemented in the mod. Run
`/summon elementalwands:fractured_guardian ~ ~ ~5` in a cheats-enabled world.
`tools/prepare_guardian_assets.py` performs steps 1 and 2 below: it creates
1024×1024 RGBA runtime copies, rescales UVs, and extracts a glowmask for the
renderer. This original 1254×1254 editing project remains unchanged.

The live in-game scale/material check and animation work are distinct from
the historical texture review checks below.

### Integration sequence

1. Normalize a copy of the atlas to a power-of-two RGBA texture with crisp
   nearest-neighbor sampling, rescale the UVs, and retain the original source.
2. Create the runtime emissive layer for the chosen eye/chest pixels. The review
   shader simulates full-bright pale pixels only in marked magic regions;
   these screenshots do not prove emissive behavior in Minecraft or Blockbench.
3. Register the renderer/entity for a static in-game scale and material check.
   Check the hitbox, camera distance, and ground contact before attack animation.
4. Animate idle/walking, grip/lift/throw, and melee with server-side attack timing.

## Regeneration

```sh
python3 texture-model.py
python3 render_preview.py
```

Requires Python, Pillow, and NumPy. The first script changes model UV/material
data only; it does not regenerate/edit the atlas or geometry. Both scripts
overwrite outputs in this directory, so preserve manual source edits first.

## Checks

All 792 faces have nondegenerate UVs padded inside their assigned material tile.
Embedded PNG bytes match the atlas file; model/export dimensions match the PNG;
the approved geometry and hierarchy are unchanged. The actual textured model
was visually reviewed in the depth-buffered renders and WebGL preview.
Blockbench 5.1.3 with GeckoLib recognized the textured project, 132 cubes,
and the embedded 1254×1254 atlas after a temporary UI timeout.

The repository clean build, existing VFX validator, whitespace check, and JAR
integrity check passed. The existing VFX validator covers production assets,
not this design directory. That initial texture pass did not add runtime code. The subsequent summon
prototype was confirmed in Lunar; the current animation work is described below.

The next motion pass is in `../v3-animated/`; its generator preserves this approved art source. Runtime now includes idle/walk and commanded action rehearsals. See `../../../docs/fractured-guardian.md`.
