# Springbloom design source

Approved source for Nature's final Technique, exported verbatim by
`tools/prepare_springbloom.mjs` into the runtime meshes. The browser page remains
a design viewer; use the native client/server fixtures for game verification.

`model.js` authors a stemless flower resting on the floor, a broad, shallow yellow
voxel bubble, spreading basal leaves, its compact thrown pod, and its
opening/wilt poses using the existing Nature workshop palette and
mesh primitives. One unit is one Minecraft block. The open golden landing center
is 0.6875 blocks high; low petals and leaves now sit directly on the ground.
The flower stays rigid during a bounce; a brief pollen burst marks the launch.
The flower spans roughly 2.25 blocks. Revision 03 flattens the bubble, thickens
and broadens its basal leaves, and removes bounce compression at the user's request.

The interactive review page lives in ignored `.local-previews/springbloom/`.
Serve that directory locally on port 8354 and open http://127.0.0.1:8354/.
The page imports this source and the existing workshop through local symlinks.
It includes orbit/side/top views, player scale, pod/open/bounce/lifetime/break
animations, scrubbing and dusk lighting. Bounce travel is cropped for model review.

The agreed gameplay targets are a 10-second cooldown,
a four-second breakable pad usable by any player, and an approximately 20-block
upward / up-to-15-block forward launch on level ground. Incoming horizontal motion
determines direction, with capped speed. Landing from above activates it; contact
with the side does not. The pad catches incoming falls safely; each launch grants
fall protection through the next landing. No caster-order chain tracking.

The user approved revision 03 for implementation. Preserve the authored model
here when incorporating revisions; preview UI is disposable.

`clipped-mesh.js` partitions each authored cuboid into nine world-block columns,
with closed cut faces. The exporter includes full pad/wilt meshes and the clipped
pieces. Runtime block-state masks select exactly the same columns for rendering
and collision. A blocked neighbor removes that section; it does not reject the
whole flower. Center support and headroom remain required.
