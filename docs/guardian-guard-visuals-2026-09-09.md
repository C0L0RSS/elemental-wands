# Guard appearance revision — September 9, 2026

The user rejected the thin cyan crack network and requested an unmistakable
vulnerable stance. The revised guard materials follow existing dark joints in
the stone atlas on its coarse pixel grid. Carved grooves deepen as guard falls;
original moss, eye/core light and magical fractures retain their artwork. No
additional emissive crack network is painted across the body.

The separate guard animation now begins with a backwards impact jolt, collapses
forward roughly 35 degrees into a lowered pelvis/bent knees, drops the head and
arms, and sustains a tired trembling motion while the core is exposed. It rises
again during the existing closure. Planted-foot IK and per-pose hand clearance
keep the long fingers and soles above the combat floor.

Sources: `art/fractured_guardian/guard/build_guard.py` and
`tools/prepare_guardian_assets.py`. Preview generation and a repeatable motion
check live in `art/fractured_guardian/guard/preview_guard.py`.
The preview at http://127.0.0.1:8330/preview.html was refreshed and visually inspected
at the exposed pose; `guard-review.png` shows the new stages and poses.

Validation passed: 389 animation samples, minimum vertex -0.0031 model units
(about 0.0002 blocks), planted sole height within 0.0031 model units; actual client
GeckoLib animation/material loading and existing bone-transform checks; clean
build/contracts; all VFX/Guardian/throw export checks; whitespace and JAR integrity.
All eleven earlier attack/arrival clips compare equal to their original sources.
Health, guard, damage, exposure timing, and server combat logic were not changed.
Full human fight appearance still needs a Lunar playtest.

Installed SHA-256: `f111af7d09e54fdfbe90b9c459ae15ad8fc81a703cde4b55958a19b172416e22`.
Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-guard-visuals-20260909-185555.jar`.
Evidence: `/private/tmp/guardian-guard-visuals-verification-20260909/`.
Restart Lunar to use this build.

## Backward-lean follow-up

The user approved the visual direction and requested that the hunch bend backwards
to show the floating core more clearly. The final stance arches the torso about
30 degrees backwards, tilts the head back and lets the arms hang outside the chest
opening. Hip position and arm clearance preserve planted feet and keep stone
pieces above the floor. Cracks, balance and exposure timing are unchanged.

The 389-sample clearance check passes (lowest vertex -0.00305 model units), along
with the final clean build, real-client loading, required asset validators,
throw-socket check, whitespace and JAR integrity. The earlier preview clearance
failure was a shin slab, corrected by moving the hips closer to the foot support.
Preview refreshed and inspected at two seconds. Human in-game visual review pending.

Current source/installed SHA-256: `33ff92a0a836b0efb55137401855578f80b1172063abc2dede627d4c6ea294b6`.
Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-backward-guard-20260909-190245.jar`.
