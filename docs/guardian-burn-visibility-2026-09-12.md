# Guardian burning visibility — September 12, 2026

The Guardian's standard Minecraft fire overlay scaled to its large render dimensions,
covering the body and making the fight hard to read. It now uses one short animated
vanilla flame near its legs, leaving the chest, head and arms visible.

This change is client-only. Fire damage, burn duration, ignition/extinguishing,
health, hitboxes, spells, attacks and all other combat behavior are unchanged.
Other entities retain their normal fire overlays. No textures or models changed.

## Implementation

`FracturedGuardianRenderer.updateRenderState` captures the existing on-fire render
flag into a Guardian-specific `burning` field, then disables its automatic full-size
fire submission. After rendering the model, `GuardianBurnVisual` submits the same
vanilla fire renderer with a separate small render state. The Guardian's original
width/height and the camera orientation are never modified.

Vanilla's fire mesh multiplies width and quad height; proxy dimensions of 1.1 by
0.65 produce a single quad about 1.54 blocks wide and 2.16 blocks high. The texture's
transparent flame tips preserve the familiar animated Minecraft look. Hidden ritual
actors, invisible Guardians, dying Guardians and extinguished Guardians omit the
cue. No global fire-rendering mixin is required.

## Verification and installation

Passed:

- Required clean build and final build, Guardian and party contracts.
- Every required VFX/Fire/Guardian/socket/Nature/hub asset check, whitespace check,
  JAR integrity and test-fixture exclusion.
- Loaded-client execution of Minecraft's actual fire mesher: exactly four vertices,
  bounded dimensions, distinct deferred state, unchanged camera and proper visibility
  suppression/cleanup.
- Disposable integrated world: real Guardian burn synchronization, renderer state,
  extinguishing and three screenshots. Front and angled screenshots were inspected;
  the body remains clearly visible. This is not a human full-fight playtest.

Evidence is in `docs/guardian-burn-verification/`.
Reproduce the client checks and screenshots with:

```sh
./gradlew -I tools/guardian_floor_client_smoke.init.gradle \
  -PfloorClientAssets="$HOME/.lunarclient/shared/assets" -PguardianBurnVisual runClient
```

Installed in the documented local Lunar Fabric 1.21.10 mods folder. Source,
shareable release and installed JAR have matching SHA-256:
`771c52bf1abc9ab4a9ec8222bd20903fbe0fc7af3ed978265fa9671354adba01`.

Shareable release: `build/releases/elementalwands-2.2.0-guardian-burn-2026-09-12.jar`.
The preceding installed party build is preserved at
`build/releases/elementalwands-2.2.0-parties-2026-09-12.jar`.
Restart Lunar to load the visual change.
