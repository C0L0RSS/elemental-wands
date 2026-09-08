# Fractured Guardian animation study 03

Six clips: idle, walk, awaken, slam, throw, beam. The beam selection previews
the jaw motion; synchronized aiming, pulse geometry, and damage run in Minecraft. The approved texture and geometry
remain in `../v2-textured/`. This directory adds an editable animated Blockbench
project and the GeckoLib animation JSON used by the runtime asset compiler.

Open `preview.html` to orbit, switch clips, pause, replay, and scrub their timeline.
`animation-review.png` is a pose sheet from the actual mesh; `motion-validation.json`
records floor-clearance samples. These previews are separate from Lunar testing.

Run `python3 build_animations.py` to regenerate animation data and the Blockbench
project. Run `python3 preview_motion.py` to verify motion and regenerate previews.
The Blockbench keyframe signs match the installed GeckoLib plugin's JSON importer.

See `../../../docs/fractured-guardian.md` for commands, lore, and the next checkpoint.
