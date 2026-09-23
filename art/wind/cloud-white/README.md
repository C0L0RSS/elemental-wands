# Cloud-white Wind source

`source-atlas.png` is original artwork generated with the built-in image generation
skill for the approved brighter, softer Minecraft-cloud Wind redesign. Preserve
this source: runtime textures are deterministic exports, not independent edits.

Art prompt: an 8 by 8 transparent sprite atlas of crisp Minecraft-style wind pixel
art, predominantly cloud white and pearl with light neutral gray and restrained
pale blue shading. Fuller rolling cloud puffs, curling gusts, continuous ribbons,
open expanding pressure rings, a low cloudburst impact, and broad crescent air
blades. No dark outlines, metallic plates, shards, blur, glow or baked background.
Each animation occupies a fixed row, with consistent isolated cell placement.

Rows and occupied columns (one based):

| Row | Columns | Content |
| --- | --- | --- |
| 1 | 1-4 | Drifting cloud motes |
| 1 | 5 | Wing item |
| 1 | 6-8 | Primary crescent, secondary gust, ultimate wing icons |
| 2 | 1-6 | Crescent gust |
| 3 | 1-6 | Air ribbon |
| 4 | 1-6 | Expanding burst ring |
| 5 | 1-8 | Cloudburst ground impact |
| 6 | 1-6 | Slipstream |
| 7 | 1-6 | Curled wisp (legacy shear_feather resource name) |
| 8 | 1-6 | Vacuum blade |

The exporter uses normalized eighths of the actual source dimensions, samples
fixed cells to the existing 16, 32 or 64 pixel grids with nearest-neighbor scaling,
with one or two pixels of transparent padding. The blade row has an 18-source-pixel
horizontal registration correction to exclude neighboring frame tips. It
removes nearly transparent background residue using an alpha threshold of 128,
and maps visible pixels to eight opaque cloud shades without dithering. Runtime
particle lifetime alpha supplies the fade. Fixed cell bounds retain relative
animation scale; frames are never individually cropped to their bounding boxes.

The worn 64 by 32 wing texture uses the existing code-native Elytra UV footprint
with rounded cloud strata and the same palette. The held wand is a separate asset.

```sh
python3 tools/generate_wind_vfx_assets.py --replace
python3 tools/generate_wind_vfx_assets.py --check
```

Export writes only the 53 owned Wind runtime PNGs and a local contact sheet in
`.local-previews/wind-redesign/`. Check mode writes nothing. No other element or
wand assets are regenerated. Texture previews do not prove in-game appearance.
