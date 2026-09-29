# Grasping Hands design preview

Skeletal hands with cyan soul seams for the Hollow Necromancer.
Default export refreshes the review page and candidates; `--install` copies the
models, textures and animations into the mod, and `--check` verifies agreement.

Run `python3 art/hollow_necromancer/grasping_hands/build_preview.py` to generate
three GeckoLib model/animation/texture candidates and the self-contained page at
`.local-previews/grasping-hands/index.html`. The existing Soul Bolt preview server
can serve that page at `http://127.0.0.1:8351/.local-previews/grasping-hands/`.
If it is not running, start `python3 art/hollow_necromancer/soul_bolt/serve.py`.

The balanced, broad and long hands share an articulated rig: exposed forearm
bones, four separate palm bones, three joints per finger and a two-joint thumb.
Raised knuckles, spread fingers and hooked fingertips keep a claw silhouette
during both the reach and the grip; the thumb curls in opposition.
Each finger flexes in sequence during emergence and repeatedly tightens during
the hold, with a separate opposing thumb motion.
The exporter reuses the approved Soul Bolt palette and cube authoring helper;
its default mode writes this directory's candidates and the ignored preview page.

Six alternating left/right hands surround a player-scale figure. Emergence is
staggered, but every hand closes at 1.6 seconds, matching the existing spell's
impact. The grip lasts 1.5 seconds before withdrawal. The figure can demonstrate
escaping the fixed warning ring; this is illustrative, not a gameplay simulation.

Drag to orbit using the other Necromancer previews' convention; scroll to zoom.
Use Rise, Grab, Hold, or the timeline to inspect poses. Single hand isolates the
rig, and the hand-name button cycles through all three proportions. The ring is
a preview marker; the game uses the server-owned spell ring and six visual entities.
