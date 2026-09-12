# Fire workshop revision 03 — September 12, 2026

The user clarified the three distinct visuals and supplied a Jogo Maximum Meteor
image as a reference for a huge mass of fire. They then specified a Minecraft
blocky shape with a spherical overall silhouette. This revision supersedes the
revision 02 statements about all ground fire being short and the meteor model
being deferred.

## Current design

- Primary and ultimate ground fires: vanilla `fire_0` / `fire_1` animation and
  ordinary Minecraft floor/side fire models at regular size. Primary stream art
  remains the custom artwork the user liked.
- Dragon's Pyre: a traveling custom 2.8-block-high wall of flame. The wall ends
  when propagation ends. Only its lingering floor uses the custom 0.075-block
  (approximately three-inch) fire carpet. The 11-row sample fully ignites.
- Maximum Meteor: an eight-block-wide rounded shape constructed from 268
  connected one-block voxels, rendering only its 306 exposed faces. All faces
  use animated fire artwork. No magma or rock material is used on the proposed
  meteor. New surface UVs span large portions of the sphere rather than repeating
  a complete texture on each block. Edge flames, warning, impact and ordinary
  residual ground fires complete the browser sequence.

`meteor-model.js` is the editable source. `meteor-model.json` is a serialized
preview mesh export with voxel positions, exposed faces and UVs; it is not yet a
Minecraft runtime model. The slow rotation preserves the chunky stepped shape.

## New artwork

`textures/custom/meteor-wall-atlas-v3.png` was produced using the built-in Image
Generation tool. The returned file is 1254 × 1254 RGBA, with four frames across
each of two rows. The top row is filled fire surface; the lower row has custom
wall silhouettes and transparency. Atlas regions use normalized UVs with a small
inset. No generated source image was recolored or resized. Existing revision 02
art remains for the stream, short floor flames, embers, arcs and edge plumes.

The supplied anime image guided the large fiery mass, not rocky debris or a
smooth realistic sphere. The later explicit request for a blocky rounded model
is authoritative.

## Verification and scope

510 sampled scene/time combinations passed finite geometry and texture-reference
checks. Custom short floor fire appears only in Pyre, whose lingering flame
planes are at most 0.075 block above placement height. Its traveling wall reaches
2.8 blocks and disappears after propagation. Primary and meteor aftermath use
vanilla fire; proposed meteor geometry never references magma. The voxel model
is connected and all face UVs stay within their atlas frame.

Browser review verified the rounded stepped meteor, moving Pyre wall with low
trail, and restored regular fire. A transient initial image-decode failure
resolved on reload; loading errors now identify the texture URL. Required build,
asset and JAR checks are recorded in the task. The installed mod and all runtime
spell files remain unchanged. This browser preview is not in-game proof.

## Artwork prompt

Create an original pixel-art game VFX texture atlas, square 1024x1024 pixels, exactly FOUR columns and TWO rows, eight rectangular cells each 256 pixels wide x 512 pixels high. No text labels, no borders, no gutters. TOP ROW: FOUR animation frames of an OPAQUE FULLY FILLED fiery turbulent surface material for a MASSIVE FIREBALL model. Each 256x512 cell fully covered edge-to-edge by swirling, ragged, interlocking flame currents, luminous molten yellow interior and saturated rich orange margins, no solid rock, no stones, no charcoal, no brown, no lava crack network, no empty holes. Dense energetic FIRE turbulence over the whole area, not individual separated fire icons. Primary colors golden yellow #ffd34a, pale yellow #fff09a, bright orange #ff9a17, warm orange #ed6510. Limited brightest highlights. Texture must read as a dense blazing mass entirely made of fire. Designed to wrap onto a faceted roughly spherical meteor model, NOT a drawing of a sphere: only edge-to-edge surface texture. Four coherent moving frames left to right. BOTTOM ROW: FOUR animation frames of a CUSTOM MOVING WALL OF FIRE, transparent RGBA surrounding the silhouette. A broad sheet with a continuous irregular orange/yellow lower body and 4 to 6 tall curling forked flame tips along the top, gaps between tips. Each wall fits cell with 10px side margin, extends from near bottom to near top. Original shapes and irregular internal flame detail, not copied Minecraft block fire. Comparable registration across frames so it feels animated. Style throughout: CRISP MINECRAFT-COMPATIBLE PIXEL ART, hand-authored logical pixel grid of 4x4 pixel clusters, stepped silhouettes, irregular internal material clusters, no smoothing, no blur, no soft gradients, no bloom halo, no black outlines, no photorealism. Color inspired by regular Minecraft fire and the immense yellow-orange flaming mass in the user's Jogo Maximum Meteor reference. No characters, no debris, no rocky meteor. Final atlas exact structure: opaque top half of fiery surface tiles, transparent lower half containing four separate tall wall flame sprites. Return actual alpha for empty areas.
