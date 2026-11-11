# Nature atlas

Run `py -3 art/nature/blocknature/textures/build_atlas.py` to build; add `--check` to compare exact PNG bytes without writing. Requires the already available Pillow package.

64 x 128 RGBA: four 16px frames across, eight bands down. Rows: leaf, darkleaf, pink, violet, gold, bark, sap, cream. All texels opaque; geometry supplies the silhouette. The builder is the source of truth, with four explicit value steps per band. Deterministic coarse clusters, leaf veins, petal folds and bark grain avoid blur, gradients and photographic texture. Gold/sap highlights shift between frames; other bands remain stable. Runtime proposal uses nearest filtering and 8 fps with fixed seed frame offsets.

`white.png`, `grass.png` and `dirt.png` are copied Wind scenery tiles, excluded from the Nature atlas and later runtime port.
