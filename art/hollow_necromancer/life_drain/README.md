# Life Drain braided soul stream

Orbitable, review-only 3D concept for the Hollow Necromancer's Life Drain. The
page renders the mod's actual GeckoLib Necromancer model, texture, glow mask and
4.2-second drain animation, plus a player-scale block figure. Two voxel-like
soul strands carry fragments from the target's chest into a soul focus above the
animated hand. The first-person view fades nearby fragments for visibility.
Its soul vignette uses smooth dark mist and faint curling wisps. It
builds during windup, swells when damage lands, and drifts away as the channel ends; it appears only in Player eye view.
It includes the 0.7-second windup, 0.5-second damage pulse spacing, and an
illustrative line-of-sight break after the cast. The break sequence is a visual
study, not a change to the spell's server timing.

```sh
python3 art/hollow_necromancer/life_drain/build_preview.py
python3 art/hollow_necromancer/life_drain/serve.py
```

Open <http://127.0.0.1:8352/.local-previews/life-drain/>. Drag to orbit, scroll to
zoom, choose camera presets, pause, replay, scrub, or select a phase. The distance
control tests the stream's readability across different target spacing.
`build_preview.py --check` verifies the generated page.

`viewer.template.html` and `viewer.js` are the browser art reference. The
generated page under `.local-previews/` is ignored. Browser lighting and the
stream are approximations, not evidence of in-game appearance.

## In-game implementation

`NecromancerDrainEffects` renders the braided cubes, incoming fragments and
hand focus from the server-tracked target and cast clock. `NecromancerDrainOverlay`
uses smooth filtered mist/wisp textures and a clear center for the first-person
victim. Other views receive no vignette. The gameplay timing, damage and healing
remain server-controlled. Broken channels fade for 0.75 seconds; death, entity
removal and world changes clear the client presentation.

```sh
python3 art/hollow_necromancer/life_drain/build_runtime.py
python3 art/hollow_necromancer/life_drain/build_runtime.py --check
```

This exporter deterministically builds the procedural fog, curl and shadow PNGs,
a white stream texture, and a hand socket sampled from the current runtime drain
animation and V2 rig. It requires Pillow and NumPy. It does not install into Lunar.
See `docs/reference/testing.md` for the `-PdrainRecord` native test and video.

