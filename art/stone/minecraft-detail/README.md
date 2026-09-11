# Minecraft-style detail refinement

Actual production previews, September 10, 2026:

- `before-after.png`: initial approved gray design versus refined pixel grain.
- `contact-sheet.png`: all 41 current Stone textures.
- `before/`: exact initial gray PNGs, retained for reproducible comparison.
- `generator-before.py`: archival source snapshot; run the active generator in
  `tools/`, not this snapshot (its relative paths are intentionally unmodified).

No new concept illustration was generated. This refinement edits the existing
native pixel-art generator, preserving all texture dimensions and alpha channels.
The front/back armor images are flat vanilla UV projections, not game captures.

Regenerate from the repository root:

```sh
python3 tools/generate_stone_vfx_assets.py --replace
python3 tools/preview_stone_assets.py --before-textures art/stone/minecraft-detail/before --output art/stone/minecraft-detail
```
