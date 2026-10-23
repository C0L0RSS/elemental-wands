# Hollow Necromancer art workshop

A browser preview for reviewing the necromancer's model, textures and animations
before they go into the mod. It renders the real GeckoLib files the way GeckoLib 5 does:
box UVs, pivots, rotation signs, cutout transparency and the glowmask layer. Lighting
approximates Minecraft's entity shading; it is not an in-game test. As in the game, the
transformation's own parts (snapped staff, claws, rags, smoke, ghosts) show only in the
`transform` clip; the candidate's `manifest.json` lists them.

```bash
python3 art/hollow_necromancer/workshop/serve.py
```

Open <http://127.0.0.1:8350/>. The server binds to loopback and disables caching so a
rebuilt candidate shows on reload.

- **Candidate** is the art under review, written to `candidate/` by
  `python3 art/hollow_necromancer/v2/build_art.py` (Pillow and NumPy).
- **Mod resources** is `installed/`, a copy of the current source assets, not a Lunar installation check.
- **Before tonight** appears when there is a local snapshot of the art from before a
  rework pass in the ignored `.local-previews/necromancer-workshop/before/`. To make one,
  copy the four files from `installed/` there before installing a new candidate.
- **Clips** lists every clip by form. The clips of the latest rework pass, named in
  `rework.json`, carry a yellow **REWORKED TONIGHT** badge and a chip in the banner at the
  top. Choosing one shows what it now does, beat by beat, in seconds. Clips only touched up
  in passing get a quieter badge.
- Hover or click the model to find a part in the texture, or hover the texture to find
  the part it paints. Notes stay in this browser; **Copy** puts them on the clipboard.
- Links can set the view, for example
  `/?clip=colossus_idle&view=front&light=night&zoom=2&t=1.2&clean`.

`v2/build_art.py --install` copies the candidate into the mod resources and
`installed/`, and samples the animated grab and rush sockets for the server. `--check`
confirms the runtime outputs still match. These are source resources, not the user's
installed Lunar JAR. V0/V1 remain historical source versions.

The reworked clips are authored in `v2/robed.py` and `v2/colossus.py` with the tools in
`v2/motion.py`. `build_art.py` checks that no rotation key flips to the other Euler form of
a turn, and that those clips start and end on their form's rest pose.
