# Fractured Guardian — geometry study 01

This is the first editable, untextured model based on the approved concept.
It is a design artifact, separate from the mod's registered/runtime content.

## Review

- Open `fractured_guardian.bbmodel` in Blockbench with the GeckoLib plugin.
- Open `preview.html` in a browser for a fully offline, rotatable view of the
  actual cuboids. Drag to orbit, scroll to zoom, or choose a camera preset.
- `model-review.png` shows the actual geometry from four angles.
- `approved-concept.png` is the image-generation reference approved in this
  conversation, not a render of this model.

The source has **132 cuboids and 38 named bone groups**. Geometry extends from
Y=0 to Y=82.999 model pixels, approximately **5.19 Minecraft blocks**. Front is
negative Z. Separate groups define the shoulders, arms, hands, finger segments,
thumbs, thighs, knees, feet, jaw, chest plates, and inset core.

The plain 16×16 `neutral-clay.png` swatch is embedded in the Blockbench project
to make its geometry readable. It is temporary presentation material: final
UV layouts, rock textures, carvings, moss, emissive masks, and animation are
intentionally deferred until the shape review. All faces currently share a
single neutral texel.

## Files and regeneration

- `fractured_guardian.bbmodel`: editable native GeckoLib project.
- `fractured_guardian.geo.json`: matching geometry export (format 1.12.0).
- `mesh.json`: transformed cuboids used by the review renderers.
- `build_model.py`: deterministic construction of this initial geometry.
- `render_preview.py`: depth-buffered renders and offline viewer assembly.
- `viewer.template.html`: dependency-free WebGL viewer template.

Regenerate from this directory with Python, Pillow, and NumPy:

```sh
python3 build_model.py
python3 render_preview.py
```

**Regeneration overwrites the model and export.** Save manual Blockbench edits
as a new version before rerunning the construction script. The approved concept
is preserved by both scripts.

## Validation and limits

The native project was opened in Blockbench 5.1.3 with the GeckoLib plugin, which
recognized all 132 cubes and the embedded neutral material. The JSON hierarchy,
cube counts, positive dimensions, unique IDs, finite coordinates, face material
references, and equality of source/export cube transforms were checked.

The review page is rendered from the same transformed cuboids and has camera
presets plus a turntable. These previews are geometry review, not in-game proof.
The model has not been registered, given AI/hitboxes, animated, or deployed to
Lunar. Existing runtime assets and existing working-tree edits are preserved.

Repository checks passed: `./gradlew clean build`, `git diff --check`,
`python3 tools/validate_remaining_vfx_assets.py`, and JAR `unzip -t`.
Gradle reported existing deprecation and GeckoLib version-format warnings.
