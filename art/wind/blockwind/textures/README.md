# Block wind atlas

`blockwind.png` is 64 x 128 RGBA, alpha 255 everywhere: four complete 16 x 16
cube-face tiles across, eight material bands down, no gutters, outlines or glow.
Band order: gale, cloud, mist, streak, swirl, feather, pearl, silver.
Transparency for see-through cloud cubes belongs to the renderer.

```sh
python3 art/wind/blockwind/textures/build_atlas.py
python3 art/wind/blockwind/textures/build_atlas.py --check
```

Requires Python 3, Pillow and NumPy. Paths resolve beside the script.
`--check` writes nothing and compares both PNGs byte-for-byte; missing or changed
outputs exit nonzero. Rebuild with the same Pillow version for identical font
rasterization and PNG encoding (created with Pillow 12.1.1, NumPy 2.4.1).

`source/wind-materials.png` is the untouched source painting. Its 2-column, 4-row
panels feed the
bands in reading order: gale/cloud, mist/streak, swirl/feather, pearl/silver.
Normalized rounded panel bounds accommodate its 884 x 1779 dimensions. Each panel
is sampled nearest-neighbor to 16 x 16, luminance-normalized between its 2nd/98th
percentiles, and mapped to five fixed shades without dithering. The builder's
`PALETTES` lists every hex color. Streak's two boundary columns are averaged in
index space to join the horizontal seam. No randomness is used.

Gale/cloud/mist/streak scroll +4 pixels in x per frame; swirl turns clockwise
90 degrees; feather/silver stay fixed. Pearl keeps its surface anchored while a
two-pixel diagonal glint sweeps +4 pixels in x. Phase 4 equals phase 0, including
the final-to-first step. Pearl has at most 1.74/255 mean-channel variation; other
bands have zero mean RGB drift. Brightness of gale/cloud/mist is 240.0/222.1/195.1
in encoded RGB luma. Broader pale-blue shade ranges retain the source flow lines,
puff edges and speed ribbons at smaller face sizes. Bands 0-3 have luminance
standard deviation at least 20 and shade separation at least 50; their source
patterns are unchanged. Mist is deliberately blue-grey, with a larger step below
cloud. Swirl, feather, pearl and silver remain unchanged.

The Fire-style preview shows the unmodified atlas at exactly 8x nearest-neighbor,
five palette swatches and one frame-1 cube per band. Each face maps a complete
tile; top/front/right shade RGB by 1.0/0.8/0.6. Checks cover dimensions, opacity,
palettes, loop closure, frame distinctness (except static bands), brightness and
streak motion. This is an offline review; game-distance readability, alpha and
animation cadence still need gameplay review. Existing white/grass/dirt PNGs
are preserved and are not builder inputs or outputs.
