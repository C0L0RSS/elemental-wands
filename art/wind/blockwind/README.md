# Block wind study

Browser study for redrawing the five Wind wand spells as 3D "block wind" — see-through cloud cubes,
chunky speed streaks and stepped crescent blades textured from a painted atlas — in place of flat cloud
sprites. It follows the Fire block-fire study (`art/fire/blockfire/`), whose engine it copies. It is a review
page, not runtime code. The approved scenes are ported to `client/blockwind/` (one class per spell, `BlockWindKit`
mirroring `kit.js`); change the scene here first, then the port. `textures/blockwind.png` is copied unchanged to
`src/main/resources/assets/elementalwands/textures/misc/blockwind.png`.

```sh
python3 art/wind/blockwind/serve.py          # http://127.0.0.1:8348/blockwind/ (caching off)
node art/wind/blockwind/check.mjs [scene ...] # geometry + cube budget check
node art/wind/blockwind/shot.mjs <scene> <t> [cam] [out.png] ["dist=6&yaw=1&pitch=.3&dusk=1"]
```

| File | Owns |
| --- | --- |
| `engine.js` | Math, noise, `Mesh.box`, WebGL renderer; see-through air cubes sort back to front |
| `kit.js` | `cube`, `streak`, `puff`, `blade`, `ring`, `wisp`, `spiral`, `motes`, the `dagger` and `wings` models, figures, terrain |
| `scenes/*.js` | Sky Shear, Waylay Dash, Updraft, Gale Daggers, Zephyr Strike, and the kit gallery |
| `textures/` | The painted `blockwind.png` atlas (8 bands × 4 frames), its builder and source painting |
| `placeholder/` | Flat stand-in atlas, used only if `textures/blockwind.png` is missing |

Scene contract, determinism and camera conventions are the Fire study's (see its README). Wind-specific
rules: air is see-through and fades by shrinking and thinning; only blade rims and dagger metal are solid.
Fast air is stretched streaks, slow air is puffy clusters. Thin streaks under about 0.1 block read as wires
and loose solid cubes read as debris, so trails use chunky streaks or fainter echoes of the shape itself.
