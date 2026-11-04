# Block Space atlas

`blockspace.png`: 64 x 128 RGBA, four 16 x 16 frames across, eight bands down,
no gutters. Order: void, glass, nebula, rim, amethyst, blue, red, starlight.

```sh
python3 art/space/blockspace/textures/build_atlas.py
python3 art/space/blockspace/textures/build_atlas.py --check
```

Requires Python 3, Pillow and NumPy (created with Pillow 12.1.1 / NumPy 2.4.1).
Paths resolve beside the script. `--check` writes nothing and compares both PNGs
byte-for-byte, failing for missing or changed files. Identical PNG encoding and
preview font rasterization require the same Pillow version.

`source/space-materials.png` is the untouched source painting. Eight panels, two
columns by four rows, feed the bands in reading order. Runtime alpha is derived,
not painted.

Normalized panel crops are sampled nearest-neighbor to 16 x 16. Each panel's
2nd/98th-percentile luminance maps to five explicit fixed shades in `PALETTES`,
without dithering. For void/glass stars only, cell maximum luminance preserves
tiny source marks missed by center samples; three separated peaks are selected
with stable ties. Void keeps only two near-black background values and three
single-pixel stars. Glass keeps three dark interior values. No randomness.

Glass alpha is 140/160/180 inside; its one-pixel violet frame and three stars are
255. Nebula alpha is 140/152/164/176/188 inside, 255 on its faint magenta frame.
Every other band is alpha 255. RGB is straight, unpremultiplied RGBA.

Void stars twinkle through a four-phase shade cycle. Glass stars drift diagonally
three pixels per phase on a 12-pixel interior torus; its surface/frame stay fixed.
Nebula scrolls horizontally four pixels per phase beneath an anchored frame
(use a slower playback cadence for this band). Rim/blue/red sample painted
square shells two shells farther out per phase, making the pattern fall inward;
the eight-shell radial domain wraps. Amethyst is static. Starlight pulses by
palette offsets 0/+1/0/-1. Every phase is modulo four: phase 4 equals phase 0,
including the last-to-first transition. No timing metadata is embedded.

The preview shows the atlas at exactly 8x nearest-neighbor over a checker, five
palette swatches, and one complete-tile cube per band. Top/front/right RGB light
is 1.0/0.8/0.6; glass alpha composites over the checker. This is an offline
material preview, not a depth-sorted Minecraft transparency simulation.
Nebula is the weakest at 16 x 16: its subdued plum detail may merge against dark
terrain. Inward shells also deliberately reset at the outside edge. In-game
contrast, transparency sorting and animation cadence remain to be reviewed.
