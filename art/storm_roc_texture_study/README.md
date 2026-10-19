# Bearded-vulture texture study

The approved texture (revision 2) and shape (revision 3) are the baseline for the Storm Roc,
refined by revision 3 of the texture below. `art/storm_roc_mod/build_mod.py` builds the
geometry and wears `storm-roc-vulture-v3.png` with `storm-roc-glow-v3.png` (which lights the
Demon face's eyes);
`art/storm_roc/build_runtime.py`
makes the tail and flight-neck changes in its poses, so the server sockets follow them. The
longer neck also covers the chain smash's hop and the pounce's leap.

## Vulture refinement: texture revision 3

- Cream forehead above a broad black eye-to-bill mask; the red-ringed eyes sit clear of it.
- A moderately raised rust crest of solid stepped feathers on all nine existing
  `crest_plume_*` bones, with lateral nape feathers on `*_cheek_blue_{0,1}_{0,2,3}`.
  Roots extend through the unchanged pivots inside the skull. In the study basis, positive
  local X raises the rearward feathers and negative X sleeks them; mirrored Z spreads the
  lateral fan. `build_runtime.py` animates it: half raised on the ground, flared to scream,
  call and wind up, ruffled in the perched shake, sleek in the air. It wears the neck-hackle
  region (`regions-v2/neck-hackles.png`), laid along each feather.
- Legs feathered rust-orange down to lead-grey toes; the back and rump wear the wings' covert
  scallops (panel 2 of `material-sheet-pixel-v2.png`), laid toward the tail with the same
  projection as `build_texture_v2.cjs`, so they continue into the wings; and a longer wedge
  tail (centre extended 22 model pixels, outer pair 7).
- 269 solid blocks. Every original UV island stays in place; added feather faces use
  reserved rows starting at 544. Wing and tail pixels stay byte-identical to v2.
- Later picked in the browser: the Demon face (`art/storm_roc_mod/heads.py`) and Huge legs
  (`art/storm_roc_mod/feet.py`), 307 blocks in all. Their new blocks get islands in the same
  reserved rows, and `build_texture_v3.py` paints them and writes the glow mask.

The runtime exporter recalculates folded primary-feather rotations against the longer tail,
and while the feet are down it lifts the fan just enough to keep it out of the rock (on a
spire, only over the capstone).

Reproduce the refinement with Pillow and NumPy, without regenerating the earlier sources:

```sh
python3 art/storm_roc_texture_study/build_texture_v3.py          # atlas and glow mask
python3 art/storm_roc_texture_study/build_texture_v3.py --check
python3 art/storm_roc_mod/build_mod.py
python3 art/storm_roc_mod/build_mod.py --check
python3 art/storm_roc/build_runtime.py
python3 art/storm_roc/validate_runtime.py
```

The v3 script starts from v2 and derives added UV islands directly from `refine()`. It
checks the locked layout, preserved wing/tail pixels and empty glow mask. `--check`
compares the regenerated PNG bytes. The geometry refinement runs after atlas painting.

## Shape revision 3

- The tail fan's feathers are mirrored pairs around the middle one, and the study's
  per-feather twist is gone; the fan still sways and pitches as a whole.
- In the air the neck keeps 0.62 of its length instead of 0.40, with matching joint offsets.
- The chest widens from 32 to 40 model pixels and the shoulder mantle from 34 to 42. Two
  overlapping collars on the first neck section close the neck join (212 blocks in all).
- Head and wing geometry are unchanged. The resized blocks keep their UV islands, so their
  tiles stretch a little rather than the atlas being repacked.

`approved-shape-v3/manifest.json` records the approved checkpoint's checks and hashes; the
geometry and clip JSON it describes are what the builders now produce.

## Texture revision 2

- `material-regions-v2.png`: the generated source, eight regional materials, cut into
  `regions-v2/` (32 × 64 each).
- `storm-roc-vulture-v2.png`: the 1024 × 1024 atlas on the block model's UV islands.
- `storm-roc-glow-v2.png`: the glow mask, with the body and shoulder lightning removed.
- `generation-prompt-v2.txt`: the prompt for the image generator.
- `storm-roc-vulture-v1.png`, `material-sheet-pixel-v2.png`, `material-sheet-v1.png` and
  `generation-prompt.txt`: the first pass and its sources.
- `layout.json`: each block's material labels and UV islands, as paint.py packs them.
- `mapping-v2.json`: the feather direction used on each face.

The packer projects each region's feather direction onto the turned block faces: away from
the beak across the head, down the neck and chest, and toward the tail across the back. The
lower jaw is horn apart from its beard. Wings, tail, eyes, mouth, beard and bare feet keep
their first-pass pixels.

To rebuild from the repository root (Sharp from npm, or set `SHARP_PATH` to another copy):

```sh
python3 art/storm_roc_texture_study/extract_layout.py
node art/storm_roc_texture_study/build_texture.cjs
node art/storm_roc_texture_study/build_texture_v2.cjs
```

The browser preview builders that compared these revisions with the previous Roc were
removed once the revisions were installed.
