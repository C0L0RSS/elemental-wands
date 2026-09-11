# Guardian encounter audit — September 9, 2026

The audit covered the reported spawn/camera and terrain faults, then traced the
Guardian's attacks, party membership, temporary spell state, teardown, loot,
and saved-world recovery. It used code review plus isolated real-server and client
fixtures. It did not operate on the user's live world.

## Changes

| Issue | Change | Verification |
| --- | --- | --- |
| Guardian appeared where the activator stood and obscured the camera. | A single persistent, passive Guardian now occupies the statue plinth. The same entity enters combat. Players' lift positions start at least 10 blocks from it, with 3-block spacing between players. | Church lifecycle fixture verifies identity reuse and initial separation. Geometry checks exercise 40 players approaching on the same line. |
| Moving players to separated positions could put them under a new obstruction. | Planned lift columns are checked before admission; the ritual clears natural vegetation in those columns too. Real structures remain obstacles. | Existing tree-obstructed ritual test plus admission checks. |
| Sloping/cliff terrain could become a tall rectangular landmass. | Disabled the structure's noise-terrain extrusion. Worldgen now checks the rotated footprint and a margin against unmodified terrain, rejecting cliffs, water and thin overhangs. It searches a small nearby area for flat ground before declining a site. Manual placement sits one block lower. | Normal-world fixture rejects the previously observed unsuitable test footprint and finds a valid natural site. |
| Stone dome could persist after combat and continue rebuilding/confining in the abandoned sky arena. | End the participating caster's dome/aegis and restore real equipment when admission/return/elimination occurs. | Multiplayer arena fixture starts the dome before victory and verifies it is removed during descent. |
| A Space charge could continue applying lift motion or launch an orb during return. | Cancel active Hollow Purple charges on encounter transitions/elimination, removing only the short levitation effect owned by the charge. | Arena fixture starts a charge at victory and verifies it is no longer active during return. |
| Death-animation XP and other late drops could appear after the first cleanup sweep. | Collect drops/XP throughout descent and recovery, before removing more floor. | A late item and XP orb injected during descent both reach the outside waiting area. |
| Protected temporary-block writes were counted as successful placements. | Record a temporary placement only when the world actually accepts the block write. | Church fixture attempts a spell replacement of the protected socket and requires an empty placement result. |
| Restoring temporary spell equipment can move the held heart to another inventory slot. | Consume the heart at the successful-admission boundary, before equipment restoration moves inventory stacks. Rejected admission does not call the consumption callback. | Ritual inventory/consumption checks and unchanged failed-admission path. |

The old block statue is migrated idempotently within its known masonry coordinates;
the plinth, socket, offering inventory, sanctuary design, and randomized loot remain.
Players can use the heart on either the dormant Guardian or its socket. A failed
attempt returns that keeper to passive duty; a victory removes it permanently for
that completed site.

## Combat results

With stationary, unobstructed Survival targets, actual server health loss was:

| Attack | Damage observed |
| --- | --- |
| Beam | 8 |
| Rock throw | 8 |
| Close slam | 6 |
| Shockwave | 6 |
| Heavy leap landing | 16 |

The fixture waits for normal join protection to expire and completes the test
player's teleport handshake before measuring damage. An initial protected-target
run was corrected as a fixture issue, not reported as a production beam bug.
The leap restored gravity; stopping the Guardian left no rock projectiles behind.
Existing geometry checks cover committed aim, cover/volume checks, jumpable waves,
ballistic trajectories, landing bounds, cooldowns, health scaling and target rotation.

## Lifecycle and persistence coverage

- Real carrier mounting, camera/body motion samples, and no per-tick player teleport stream.
- Supported floor, roofless walls, immutable arena backing and bounded wand teleports.
- Actual player death and respawn exclusion; no survivor re-entry after elimination.
- Explicit abort, retry, victory descent, and permanent sanctuary completion.
- Same dormant keeper identity after loading a saved natural site; no duplicate keeper.
- Restart during an arena fight: players returned, sky structure removed, original
  guardian flags recovered, owned chunk tickets released, pre-existing tickets retained.
- 1,024 deterministic loot rolls, varied enchantments capped at II/their natural
  maximum, shuffled slots, ordinary clutter, and no refill of collected treasure.

## Limits and next human checks

The placement correction applies to newly generated sites. Existing cliff-shaped
terrain produced by earlier worldgen is not automatically carved away or relocated:
there is no original-terrain snapshot that could safely reconstruct it. Existing
unfinished sites do receive the new dormant Guardian and camera-spacing behavior.

The updated preview uses the real static Guardian mesh on the plinth. It does not
prove the animated in-game view. The next Lunar checks are the approach to the
keeper, interaction reach, facing, first/third-person lift visibility, new natural
terrain, and the restored reveal. Multiplayer latency and broad combat balance
still require real players; the audit is not a claim that every possible mod,
terrain seed or spell combination is bug-free.

## Reproduction tools

- `tools/guardian_combat_smoke.init.gradle`: all five attacks against live targets.
- `tools/guardian_arena_smoke.init.gradle`: multiplayer lifecycle, spells, late drops;
  rerun with `-ParenaRecovery` for interrupted-fight recovery.
- `tools/guardian_church_smoke.init.gradle`: dormant keeper, ritual, retry, restoration,
  loot and protected placements; `-PchurchRecovery` checks completed-site persistence.
- `tools/guardian_church_worldgen_smoke.init.gradle`: natural placement and loaded
  keeper identity; `-PchurchRecovery` retains and reloads the preceding world.
- `tools/guardian_floor_client_smoke.init.gradle`: actual client atlas, floor mesh,
  socket model and heart item model.

All fixtures use disposable worlds beneath `build/`, loopback-only ephemeral ports,
and the previously accepted test EULA. Test entrypoints are excluded from the release
JAR. Static assets retain the existing spell/Guardian package contract.

## Final package

Installed JAR SHA-256: `90c1ee6233c9d58750e98c1178135121cce0297e11de241a90ab91cbcbef6498`. Source and installed files matched.
Final clean build, required asset checks, JAR verification and client asset/mesh checks
passed. Evidence: `/private/tmp/guardian-audit-verification-20260909/`.
