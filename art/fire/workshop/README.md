# Fire workshop — revision 05

**Implemented and installed September 12.** This workshop remains the approved
visual reference. Runtime files, package validation, backup and installation are
recorded in `docs/fire-redesign-2026-09-12.md`. Earlier preview-only scope notes
below are historical. Restart Minecraft through Lunar to test the implementation.

**September 12 spectacle update:** the approved round model is preserved. A full
shell of 46 curved fire strips, 11 longer crown flames, and up to 78 drifting
embers make the meteor visibly more fiery. Broad original flame-sheet artwork
is mixed with the existing narrow plumes; the camera includes the taller wake.
Particle disabling removes the embers while retaining the main flame shell.
606 sampled time/camera/particle combinations passed finite-geometry and texture
checks. Browser review confirmed the fuller flames. This remains preview-only.

**September 12 roundness update:** the meteor now uses a symmetric sphere with
half-block steps (2,176 voxels / 1,248 exposed faces), retaining its eight-block
diameter and existing textures. The mesh export was refreshed and its bounds,
UVs, three-axis symmetry and browser appearance were verified.

**Current behavior:** [Revision 03](revision-03.md) supersedes the historical revision 02
notes below. Primary/ultimate ground fire is vanilla. Pyre sends a tall custom
wall and leaves short custom floor fire. The meteor is now a massive blocky
fireball made entirely of fire. The preview opens on Maximum Meteor.

## Historical revision 02

September 11, 2026. The user clarified that the spell textures should be original,
with Minecraft fire and lava used as color/style references, not pasted whole
fire tiles. The user also requested Pyre flames only a couple of inches high.
This supersedes revision 01's direct vanilla-fire texture/model proposal.

## Preview

<http://127.0.0.1:8346/>

```sh
python3 -m http.server 8346 --bind 127.0.0.1 --directory art/fire/workshop
```

Preserve an existing listener and other local previews. The page is local and
uses no external network dependencies. It opens on the proposed Pyre; Compare
shows a reconstruction using the current mod's artwork.

## Revision 02

- Original 1024 × 1536 transparent pixel-art atlas, six rows × four frames:
  stream, floor fire, low flame curls, embers, impact arcs, meteor plumes.
- Atlas created with the built-in Image Generation tool. Its prompt and art
  direction are in `art-direction.md`; the unmodified returned PNG is
  `textures/custom/fire-atlas-v2.png`.
- `custom-art.json` defines the original sprite families and their frame rates.
  The renderer selects cells using UVs; no original sprite PNG is cut up, resized,
  recolored or overwritten. A cutout threshold removes faint edge halos at render
  time. White emissive tint and nearest sampling preserve the source artwork.
- Original fire geometry uses only these custom sprites. Vanilla fire remains
  in the labeled reference panels. Existing netherrack, stone, magma core, and
  neutral smoke retain their vanilla materials.
- Pyre uses animated surface patches and flame curls capped at 0.075 block above
  the placement surface, about three inches. Adjacent patches overlap slightly,
  rotate and animate out of phase to reduce visible tile repetition. The advancing
  front stays at the same low height. Sparse tiny embers rise at most about 0.2
  block, without runway smoke columns.
- Player eye sets a 1.62-block camera height at the approach to the runway.
  Orbit, zoom, comparison, pause, timeline, speed and daylight/dusk remain.
- The preview uses an 11-row sample of the 40-block Pyre; the final row now fully
  ignites before the front ends. Primary and meteor timing remain illustrative.
- The larger custom meteor geometry is still a separate future step. Its existing
  five-part magma model is retained in this preview.

## Scope

Only the browser workshop was revised. Runtime Java, production textures, combat,
block lifetimes and the installed Lunar mod are unchanged. This is a design
preview, not in-game verification. Wait for review of the browser design before
porting it into Minecraft. Preserve the existing first-person clearance work.

`tools/prepare_fire_preview.py` retains the 89 byte-identical vanilla/current-art
reference snapshots, with sources and hashes in `references.json`. Custom artwork
is intentionally separate so refreshing references cannot overwrite it.

## Validation

Revision 02 checks passed: 510 scene/time samples have finite geometry and valid
texture references. Proposed spells reference no vanilla fire or lava sprites.
Pyre flame geometry stays at or below 0.075 block above its placement surface;
all 33 sample tiles fully ignite. All six original families contain four distinct
frames with real transparency. Browser inspection covered all effect families
and the player-eye camera. Clean build, existing regressions, required asset
checks, whitespace and JAR integrity passed. The resulting JAR was not installed.
Static checks are not a Minecraft playtest.
