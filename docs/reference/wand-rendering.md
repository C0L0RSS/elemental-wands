# Wand rendering

The approved wand has coarse-pixel twisted wood, a tapered joined bottom, and a
compact glass cube. A muted patterned elemental core rotates inside stationary
glass while small particles drift within it. Stone follows the gray ability art.
Keep the stepped Minecraft silhouette and jagged wrapping branch; avoid smooth
curves, fine photographic wood grain, or strongly saturated featureless cores.

Held views use the enlarged pose approved during iteration. The hotbar/inventory
shows the upper wand and glass at a readable size, with animation. Dropped items
and item frames have independent display transforms. Do not solve GUI cropping
by changing the actual held mesh or shortening the wand.

## Sources and owners

- `art/wand/design.json`: reusable geometry, material and palette source.
- `tools/prepare_wand_assets.py`: exports the wood meshes and three textures;
  `--check` verifies generated assets without replacing them.
- `client/wand/WandMesh`: mesh loading, full/GUI transforms, bounds and face output.
- `client/wand/WandRenderer`: core rotation, contained motes and glass submission.
- `client/wand/WandItemModel`: affinity selection and Fabric model registration.
- `models/item/fractured_wand.json`: display poses and legacy sprite fallback.

Java paths are relative to `src/main/java/com/anton/elementalwands/`; model and
texture paths are inside `src/main/resources/assets/elementalwands/`.

## Constraints worth preserving

The exporter unions intersecting wood boxes into an exterior shell to avoid
coplanar face flicker. The GUI mesh is clipped and capped separately. Keep shell
winding, bounds, and volume verification when changing geometry.

Glass uses one outward-facing shell, drawn after its contents. Do not add
coincident transparent shells to simulate reflections. Keep the animated core
and motes entirely inside the glass across their full movement range.

Held wands use the holder's affinity. The affinity attachment synchronizes to
tracking clients; do not color every remote wand using the local player's state.
Unheld world items are neutral quartz. GUI holder fallback and per-draw immutable
state must not leak colors between successive items.

The old flat wand sprite remains a particle/fallback asset. The three new wand
textures are separate from the established spell/HUD PNG count contract.

Run the exporter check and the native wand fixture in [testing](testing.md).
Native screenshots are stronger evidence than the browser concept, but simulated
remote holders do not replace an actual two-client appearance test.
