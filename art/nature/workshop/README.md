# Nature workshop — design preview 04

Requested September 10, 2026: preview the complete Nature redesign in the browser
for live feedback **before** implementing it in Minecraft. The earlier request to
complete the full pass is now gated on this preview review. No Nature runtime code,
production resources, ability mechanics, build or Lunar install changed in this pass.

Open http://127.0.0.1:8342/ while the local server is running. To restart:

```
python3 -m http.server 8342 --bind 127.0.0.1 --directory art/nature/workshop
```

The server serves this preview folder only, binds to loopback, and has no external
library/font/CDN dependency. Browser notes stay in localStorage until copied.

Revision 02 follows the user's live feedback: vivid green vines/leaves, richer
pink/violet/gold flowers, blockier Minecraft-like silhouettes, and straight
segmented entangle wraps instead of circular coils. The secondary keeps the
original plant as its only floral anchor and climbs the target with vines.
This visual plan must not add another targetable flower in the later game pass.

Revision 03 follows the user's request for a real Minecraft-style ultimate tree.
The tree uses regular oak logs and leaves with only two custom block concepts:
one heartwood block in the trunk and occasional flowering oak leaves. Its canopy
is full leaf blocks rather than individual modeled leaves. A player beside the
tree provides scale. The custom block faces are preview geometry, not installed
Minecraft block definitions. No Nature runtime changes are authorized until review.

Five interactive scenes: winged seed and sprouting plant; land roots and water
rafts; traveling paired vines that climb a target, with no destination flower; staged block-built oak awakening;
entangle on player/spider/Guardian references, 0–5 stacks, active root, lingering
stacks, Guardian opening and resistance. Drag to orbit, scroll to zoom, use the
animation scrubber, pause/play/restart, speed, daylight/dusk and reset controls.

`models.js` authors all Nature geometry: square-section roots, angular thorns, stepped
leaf plates, thick stepped petals, seed pods and climbing vine wraps. The ultimate now uses `TREE_BLOCKS`: a
nine-block-high oak with grid-aligned full cubes; `tree-layout.json` records the
exact block coordinates and proposed types.
`engine.js` renders native WebGL 2 meshes with procedural pixel-grain materials.
The Nature plant shapes and materials are concepts. The ultimate uses actual
Minecraft 1.21.10 oak log, oak log top and oak leaf textures, copied unchanged from
the locally installed game JAR into `textures/` for this local preview.
`guardian.json` is a read-only copy of the existing production Guardian geometry;
its preview stone material and restrained opening pose are approximate, not the
production texture or animation. Player and spider are simple scale references.

Browser checks: shader/scene load, seed and tree visual inspection, Guardian
five-stack opening, land/water switching and UI state; no browser errors observed.
Revision 02 checked JavaScript syntax, visually reviewed the flower, verified
secondary target-climbing without another flower, and inspected the five-stack
angular player wraps. Browser error log remained empty.
This is not proof of live game lighting, collision, timing, performance or combat.

After feedback, preserve the Nature mechanics unless separately approved: seedling
ownership and destruction; physical growth/damage areas and restoration; Guardian
wave clearing/cover; the ultimate's damageable heart and healing; exact root expiry
and distinct Guardian recovery/immunity. Integrate first-person clearance without
obscuring the center of the affected player's screen.

Revision 03: JavaScript syntax, unique integer block positions, texture loading,
browser shader/error checks and tree visual inspection passed. Other preview
scenes retain the approved Revision 02 shapes and palette.

Revision 04: user approved the height and requested more outstretched branches.
Retains nine-block height; logs now extend four blocks in each main direction,
with staggered heights and separate crowns spanning eleven blocks. Heartwood and
flowering oak accents are preserved. This remains a preview-only shape revision.
