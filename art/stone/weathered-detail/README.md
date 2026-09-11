# Weathered stone detail revision

The earlier grain-only refinement did not look different enough to the user.
This revision authors broken stone layers, chipped slab edges, recessed crevices,
directional armor shading and blade fractures, with stronger clustered grain.

`before-after.png` compares the actual initial gray assets with this revision;
`contact-sheet.png` shows all 41 current production textures. Armor is a flat
vanilla UV projection, not an in-game screenshot. The gray design's geometry,
texture sizes and alpha silhouettes are preserved.

Reproduce from the repository root using the existing native art generator:

```sh
python3 tools/generate_stone_vfx_assets.py --replace
python3 tools/preview_stone_assets.py --before-textures art/stone/minecraft-detail/before --output art/stone/weathered-detail
```
