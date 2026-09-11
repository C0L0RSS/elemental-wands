# Guardian arrival, stone effigy, and respawn follow-up — September 9

The user approved the recent arena and sanctuary, requested the living guardian
remain unseen until the arena finishes forming, and reported repeated void deaths
after respawning. They subsequently requested a custom statue made from ordinary
Minecraft blocks.

## Result

- A nine-block-wide stone effigy has broad carved shoulders, hanging arms/fists,
  short separated legs, a recessed face, and restrained cyan terracotta details.
  Stairs, slabs, walls, stone bricks, and andesite are vanilla. The existing custom
  heart socket remains at the front of the pedestal; no new blocks or valuable
  gold/quartz materials are required.
- Ruined and restored layouts include the effigy. Previously unfinished sites
  refresh only its authored footprint when loaded. The old dormant living entity
  is removed; no living guardian is created until a valid heart is offered.
- Players rise alone in their separated seats. The hidden actor waits above the
  arena, then appears 48 blocks above the finished floor, accelerates downward for
  36 ticks, and lands with cloud/end-rod particles and a heavy impact sound.
  A 32-tick crouch/compression clip leads into the existing 84-tick awakening.
- Players remain protected and casting stays locked through the arrival/landing.
  The introductory impact deals no damage and edits no terrain. Cancelling or
  wiping during formation/arrival uses the existing recovery lifecycle.
- Arrival animations live in `art/fractured_guardian/arrival/arrival.animation.json`;
  the asset exporter merges them without changing the nine approved attack/idle clips.
  Hidden state uses its own tracked flag: vanilla living-entity invisibility is
  recomputed by status effects, so a one-time `setInvisible` is insufficient.

## Respawn finding

Fabric's AFTER_RESPAWN callback runs at the tail of PlayerManager.respawnPlayer,
before ServerPlayNetworkHandler assigns its returned replacement player. Calling
teleport inside that callback can operate through a handler still attached to the
dead player. The original fixture manually made a replacement and called the
callback; switching it to the real respawnPlayer method reproduced the failure.

The callback now queues the UUID. A subsequent server tick checks that the live
replacement is also the handler's player before returning it outside. Eliminated
players remain excluded from the fight. Return searches load destination chunks
before reading heightmaps, require dry solid support and body clearance, and retain
recovery receipts rather than teleport to an unchecked fallback when no safe point
exists. This also closes a possible bottom-Y result from an unloaded heightmap.

## Verification

Real-server tests cover hidden ascent, visible falling arrival, landing, actual
vanilla death/respawn, supported waiting ground, victory/cleanup, whole-party wipe,
retry, restart recovery, socket interaction, old-site effigy refresh, church
restoration, and randomized loot. An unloaded column queried from below world
height must resolve to supported ground. Client asset and required package checks
are run before installation. Human Lunar confirmation of the entrance animation
and the reported respawn scenario remains the final visual/gameplay check.

Offline preview: `art/guardian_church/preview.html`, with an isolated effigy view
at `art/guardian_church/statue.png`. Slabs/walls/stairs use simplified envelopes in
the preview; Minecraft supplies the final block shapes and lighting.

## Installed build

- Installed in Lunar Fabric 1.21.10: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
- SHA-256 (source and installed): `e89c65b88612b5244cfa3dffb2485fce6b7154aafb6045ed4dfeba975c24a239`.
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-sky-arrival-20260909-140540.jar`.
- Passed: clean build, required VFX/Guardian/socket/layout validators, JAR integrity,
  no fixture classes in the release JAR, real-server arena/party-wipe/recovery,
  church ritual/restoration/loot, existing natural-site migration, and real-client
  floor/socket/heart plus GeckoLib arrival-clip baking.
- Evidence: `/private/tmp/guardian-arrival-verification-20260909/`.
- Restart Lunar to load the new Java code. Human motion/appearance confirmation
  remains open; server and asset checks do not prove the final client experience.
