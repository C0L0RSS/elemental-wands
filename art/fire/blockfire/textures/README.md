# Block fire atlas

`blockfire.png` is 64 x 128, RGBA, with alpha 255 everywhere. Each row is
four complete 16 x 16 cube-face tiles. Rows are core, yellow, orange, deep,
ember, smoke, char and magma. No gutters, transparency, edge outlines or glow.

Rebuild from the original index grids and palettes in `atlas_source.json`:

```sh
python3 art/fire/blockfire/textures/build_atlas.py
python3 art/fire/blockfire/textures/build_atlas.py --check
```

Requires Python 3, Pillow and NumPy. Paths resolve relative to the script.
`--check` writes nothing and verifies both PNGs byte-for-byte. A missing or
different PNG exits nonzero. The builder also checks tile dimensions, row order,
opacity, four distinct frames, loop closure, fixed palette counts, exactly equal
per-frame RGB averages, and the core-to-ember brightness steps.
For byte-identical preview rebuilds, use the same Pillow version: its bundled
font rasterization or PNG encoding can change between releases.

The source is authored on a native 16-pixel grid, with no randomness or image
resampling. The vanilla references are not inputs. Orange `#ed7518`, tangerine
`#ff971d`, gold `#ffc23d`, yellow `#ffe766`, pale yellow `#fff0ae` and burnt
orange `#b94411` anchor the Fire palette. The yellow row's `#ffb832` bridge
keeps the five heat levels approximately evenly spaced in encoded RGB luma.
There is no pure white. Ember uses red-brown coal with sparse orange flecks;
smoke uses neutral charcoal; char uses warm black rock with narrow orange seams;
magma uses thicker orange channels with restrained yellow heat pockets.

The first six rows rise four pixels per frame, with a one-pixel contour shear.
The fourth-to-first step wraps through the same 16-pixel field. Row offsets
reduce alignment between heat levels. Both operations preserve exact palette
counts, so average RGB remains identical in all four frames. Char and magma
keep their rock plates and crack masks fixed while heat travels through the
seams. Playback timing belongs to the renderer; this atlas does not prescribe it.

`blockfire_preview.png` shows the unmodified atlas enlarged 8x with nearest
neighbor sampling, row palettes, and one cube per row using frame 1. The entire
tile appears once on each face. Top/front/right multiply RGB by 1.0/0.8/0.6,
respectively. This is an offline material review, not Minecraft rendering proof.

Second pass: judge animation cadence and the core's pale-yellow coverage on
actual spell cubes at gameplay distance; adjust the rising pattern's displacement
if it scrolls too strongly, and the char seam coverage if small ember bombs lose
their crust. Keep the exact per-frame histogram constraint when editing.
