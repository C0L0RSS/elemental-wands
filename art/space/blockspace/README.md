# Block space study

Browser study for redrawing the five Space wand spells as "void glass": dark see-through glass pieces with a bright
frame and stars inside, opaque void cores ringed by a corona that faces the viewer, and streams that fall inward.
It follows the Fire (`art/fire/blockfire/`) and Wind (`art/wind/blockwind/`) studies, whose engine it copies, so the
three elements read as one game. It is a review page, not runtime code; approved scenes are ported to Java afterwards.

```sh
python3 art/space/blockspace/serve.py          # http://127.0.0.1:8349/blockspace/ (caching off)
node art/space/blockspace/check.mjs [scene ...] # geometry + piece budget check
node art/space/blockspace/shot.mjs <scene> <t> [cam] [out.png] ["dist=6&yaw=1&pitch=.3&dusk=1"]
python3 art/space/blockspace/textures/build_atlas.py --check
```

| File | Owns |
| --- | --- |
| `engine.js` | Math, noise, `Mesh.box`, WebGL renderer; glass sorts back to front and keeps texels down to a 0.1 alpha cutoff |
| `kit.js` | `piece`, `plate`, `core`, `ring`, `inflow`, `spill`, `stars`, `chase`, `slit`, `shell`, `star`, `rift`, `poof`, `boltHead`, the Astral orb and Gravity Bomb models, figures, terrain |
| `scenes/*.js` | Singularity Bolt, Blink Rift, Astral Double, Gravity Well, Hollow Purple, and the kit gallery |
| `textures/` | The painted `blockspace.png` atlas (8 bands × 4 frames), its builder and source painting |
| `placeholder/` | A generated stand-in atlas, used only if `textures/blockspace.png` is missing |
| `scenery/` | Preview-only white, grass and dirt tiles |

Scene contract, determinism and camera conventions are the Fire study's (see its README). Space-specific rules:
glass and nebula pieces are always see-through and carry their own alpha (frame and stars opaque, interior about
60%); void, rim, amethyst, blue, red and starlight are solid. Space falls inward: streams accelerate toward a core,
stretch along the fall and shrink to nothing on arrival; only bursts move outward, and briefly. Keep pieces few and
large, because hundreds of small cubes read as noise. Coronas and the Blink slit turn to face the camera so they never
go edge-on.
