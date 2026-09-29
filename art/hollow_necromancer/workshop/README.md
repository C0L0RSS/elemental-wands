# Hollow Necromancer art workshop

A browser preview for reviewing the necromancer's model, textures and animations
before they go into the mod. It renders the real GeckoLib files the way GeckoLib 5 does:
box UVs, pivots, rotation signs, cutout transparency and the glowmask layer. Lighting
approximates Minecraft's entity shading; it is not an in-game test.

```bash
python3 art/hollow_necromancer/workshop/serve.py
```

Open <http://127.0.0.1:8350/>. The server binds to loopback and disables caching so a
rebuilt candidate shows on reload.

- **Candidate** is the art under review, written to `candidate/` by
  `python3 art/hollow_necromancer/v2/build_art.py` (Pillow and NumPy).
- **Mod resources** is `installed/`, a copy of the current source assets, not a Lunar installation check.
- Hover or click the model to find a part in the texture, or hover the texture to find
  the part it paints. Notes stay in this browser; **Copy** puts them on the clipboard.
- Links can set the view, for example
  `/?clip=colossus_idle&view=front&light=night&zoom=2&t=1.2&clean`.

`v2/build_art.py --install` copies the candidate into the mod resources and
`installed/` and samples the animated grab socket for the server; `--check` confirms
the runtime outputs still match. These are source resources, not the user's installed
Lunar JAR. V0/V1 remain historical source versions.
