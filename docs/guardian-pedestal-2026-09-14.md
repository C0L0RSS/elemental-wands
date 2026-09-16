# Guardian offering pedestal — September 14, 2026

The approved interactive prototype is implemented with native block models. The
floating chiseled-deepslate socket becomes a supported carved column with a broad
foot, a shallow octagonal bowl and an empty recess. A cyan broken-ring rune repeats
on the column and statue's chest. Existing vanilla stone textures retain their
pixel density; this adds no raster assets.

## Interaction

Use this church's Guardian Heart on either the bowl or column. Aiming at either
with a heart in the main hand shows the current vanilla Use binding and “Use
Guardian Heart.” The hint hides on empty hand, active/completed offerings, screens,
hidden HUD and Spectator. The offering chest points to the pedestal before the
statue. The server retains heart-token validation, recall, group admission and
consume-only-after-admission behavior.

A successful offering seats a small faceted heart, brightens the pedestal's rune,
plays a chime and sends a short cyan particle trail to the statue. Its chest rune
lights after 30 ticks. The cosmetic sequence ends after 64 ticks and does not delay
the existing arena. Rejected attempts leave the recess empty. Abort/wipe resets it;
victory retains the seated heart as a completion detail. Restart reconciles visual
state from the church journal without replaying the cosmetic sequence.

## Existing worlds and authoring

The socket remains at the same coordinates and retains its block entity, site ID,
heart token and offering inventory. New ruins use the pedestal directly. Unfinished
version-two churches upgrade three positions: the empty support cell, chest core
and socket model flag. The operation refuses foreign blocks or inventories and is
idempotent even if chunks saved only part of the upgrade. The previous version-one
statue migration now creates the complete pedestal. Completed old churches retain
their old cube model; packaged legacy layouts are unchanged.

`GuardianSocketBlock` owns `pedestal` and `ritual` (0 empty, 1 offered, 2 restored).
`GuardianRitualBlock` supplies the support and chest rune with facing/lit states.
`GuardianChurchManager` handles click routing, upgrade and visual state. The
client's `GuardianOfferingHint` never decides admission or consumes items.

`tools/prepare_guardian_pedestal.py` exports ten model/state files from the approved
geometry; run it with `--check` to detect drift. `tools/build_guardian_church.py`
owns generated layouts and the rotated worldgen template. Browser prototypes stay
under ignored `.local-previews/`; Minecraft screenshots are in
`docs/guardian-pedestal-verification/`.

## Verification

- Clean build, existing combat/party regressions, required asset exporters,
  pedestal and church layout checks, whitespace and JAR integrity.
- Isolated server: four rotated upgrades, foreign-inventory refusal, partial-save
  recovery, preserved token/block entity/chest contents, native column routing,
  rejected heart, accepted offering, abort/retry, restoration and one-time loot.
- Fresh server restart: restored pedestal and collected rewards remain intact.
- Natural world generation: rotated pedestal/support/rune, stocked heart and
  unchanged natural courtyard integration.
- Actual Minecraft client: all model variants load, empty-hand hint suppression,
  native heart-use packet, synchronized seated heart, consumed item, abort cleanup
  and inspected screenshots.

Human survival discovery/readability testing remains the next playtest.

Reproduce the runtime checks with:

```sh
./gradlew -I tools/guardian_church_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle -PchurchRecovery runServer --args nogui
./gradlew -I tools/guardian_church_worldgen_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_floor_client_smoke.init.gradle -PguardianPedestalVisual -PfloorClientAssets="$HOME/.lunarclient/shared/assets" runClient
```

The server fixtures bind only to localhost with a disposable random port. The client
fixture creates its own world under `build/guardian-floor-client-smoke/`.

## Installed release

Installed in the existing Lunar Fabric 1.21.10 profile. Source and installed SHA-256:

`2d0b17d9bec2b179b2216f9638ff73f806df17ca496fe369ce47ddf8d0cf1731`

Previous JAR backup: `.local-backups/lunar/20260914-093518/elementalwands-2.2.0.jar`.
The checksum receipt is `guardian-pedestal-verification/install.json`.
Restart Lunar, then revisit an unfinished church; no new world is required.
