# First Wizard floor ink

The three PNGs under `source/` are the unmodified ImageGen outputs and the
source of truth for the silhouettes, sheen, rims and veins. Keep them with
the exporter so the same textures can be rebuilt on either machine.

Run `py -3 art/first_wizard/floor_ink/build_floor_ink.py --install` to regenerate
the six art exports and their runtime copies. Use `--check` to compare both
sets byte for byte without writing. The exporter keys green, samples coverage
and pigment at the native grid, matches the join profiles, and quantizes to
the fixed ink palette. A four-phase shimmer moves through the source pigment
while the outline stays fixed.

| Texture pair | Frame | RGBA sheet | World footprint |
| --- | --- | --- | --- |
| `first_wizard_floor_strip[_glow].png` | 64 × 32 | 64 × 128 | One repeat every 4 blocks along a 2-block-wide path |
| `first_wizard_floor_cap[_glow].png` | 32 × 32 | 32 × 128 | 2 × 2 blocks |
| `first_wizard_floor_pool[_glow].png` | 128 × 128 | 128 × 512 | 8 × 8 blocks |

Four equal frames are stacked top to bottom. Pair the same frame in the base
and glow passes. U follows the path and V runs across it. Both ends of the
strip share the right-edge profile of the cap. Flip the cap in U at the far
end and fit whole repeats between caps and around closed rings so the join
uses that profile. Sample with nearest-neighbor filtering.

## ImageGen prompts

### Strip

Generate a high-resolution game texture source for a Minecraft-style boss floor decal,
orthographic straight overhead with flat lighting. Wide landscape canvas. One horizontal
band of spilled liquid ink runs fully through the left and right image borders, centered
vertically. It must tile seamlessly horizontally; left and right boundaries should show
matching ink width, edge profiles and wet sheen. Ink fills roughly 65% of image height,
with flat perfectly solid chroma-key green #00ff00 negative space above and below. Matte
near-black #0d0a12 body, dark wet purple #1c1529, deep purple #3d245c, restrained
branching violet #6a3cc0 veins, a clear irregular meniscus of #8a52e6 on BOTH long sides.
Use broad, calm areas of near-black and a few smooth wet contours in large readable
clusters, with varied lumpy fingers and sparse detached droplets near the edges. Keep
veins thin, winding, and sparse, with small pale magenta glints near the meniscus. Bold
deliberate shapes suitable to downsample to a 64 by 32 pixel texture. A seamless texture
crop, no isolated pad, no point or cap, no band along the centerline, no neon aura, no
shadows, no perspective, no text, no stars, no glass facets.

### Cap

Generate a high-resolution square game texture source for a Minecraft-style boss floor
decal, orthographic straight overhead with flat lighting. On perfectly solid chroma-key
green #00ff00 background draw the rounded LEFT END of a horizontal trail of spilled black
ink. The ink extends completely through the RIGHT edge of the canvas, centered vertically
and about 65% of the image height at that right edge. Along the left, the trail curves
outward into a rounded ragged bulbous end at about 15% of canvas width, with a few short
liquid fingers and detached droplets. This is one asymmetric rounded end-cap, with green
negative space left, above and below. Matte near-black #0d0a12 body, dark wet purple
#1c1529, deep purple #3d245c, restrained branching violet #6a3cc0 wisps, a clear irregular
meniscus of #8a52e6 with a few pale magenta glints. Broad calm black center, large
readable wet-sheen clusters suitable for downsampling to 32 by 32 pixels. No exposure
gradients, shadows, neon aura, stars, glass facets, drips falling downward, text, or
perspective.

### Pool

Generate a high-resolution square game texture source for a Minecraft-style boss floor
decal, orthographic straight overhead with flat lighting. One large round, roughly
circular puddle of living ink centered on perfectly solid chroma-key green #00ff00, with
the main ink silhouette roughly 85% of canvas diameter. Use an irregular ragged sprawling
edge with a few bulbous tendrils and separate rounded droplets, keep all artwork in frame.
Matte near-black #0d0a12 body, dark wet purple #1c1529, deep purple #3d245c, a violet
meniscus #8a52e6 along much of the jagged edge and branching violet #6a3cc0 veins that run
inward from several places and peter out before the center. Keep the center filled with
black and dark ink and use wide calm areas between the veins, along with a few dark wet
contour shapes. Sparse pale magenta glints. The silhouette must read from far away, use
deliberate pixel-like clusters designed to survive downsampling to 128 by 128. The ink
surface is liquid with subdued wet sheen. No stars, sparks, crystal facets, concentric
rings, neon aura, grain, shadows, text, or perspective.
