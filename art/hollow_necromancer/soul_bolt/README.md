# Soul Bolt model and runtime assets

The Hollow Necromancer's `SoulBoltEntity` uses this authored skull through
`SoulBoltRenderer`. Its jaw continuously chomps during flight; a synchronized
final snap plays at contact. The eyes and mouth fissures glow, with the prepared
cyan wisp and shard particles accompanying flight and impact.

```sh
python3 art/hollow_necromancer/soul_bolt/build_preview.py
python3 art/hollow_necromancer/soul_bolt/serve.py
# Export or verify runtime resources (does not install into Lunar):
python3 art/hollow_necromancer/soul_bolt/build_preview.py --install
python3 art/hollow_necromancer/soul_bolt/build_preview.py --check
```

Open <http://127.0.0.1:8351/.local-previews/soul-bolt/>. Drag to orbit, scroll to
zoom, or use the preset views, including **Underside** for the closed lower jaw.
`build_preview.py` owns the candidate GeckoLib
geometry, texture, glowmask, and bite animation. The browser preview is an approximation of
Minecraft entity lighting, not an in-game proof or an installed Lunar build.

The face uses separate orbital rims, small recessed soul eyes, a textured nasal
opening, and a continuous upper jaw. The palate and lower jaw have solid caps.
The lower jaw stays within the cheekbones and extends along a long mandible,
with a slender chin and ordinary teeth. Its deep gape gives the bite its scale.
The authored 3.2-second cycle opens to 84 degrees, closes in 0.16 seconds, holds
the bite, then resets for review. **Open jaws**, **Closed bite**, and the timeline
let the reviewer inspect the poses. The viewer reads the exported animation
keyframes, including the small forward flight. In Minecraft, actual entity motion
controls position; a separate 0.8-second jaw loop chomps while airborne and the
server triggers the final bite on contact. The review timeline retains the longer
authored cycle for inspecting the model poses.
Each cube exports explicit per-face UV rectangles, with two texels per model
pixel and padded edges. The viewer reads those rectangles directly; never derive
UV sizes from the fractional geometric dimensions.

The source particle artwork is in `candidate/particles/`. The exporter preserves
those frames and verifies the runtime copies and 16×16 RGBA dimensions.
