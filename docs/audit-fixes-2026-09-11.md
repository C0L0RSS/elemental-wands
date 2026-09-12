# Audit follow-up — September 11, 2026

The second review found incomplete fixes in the earlier audit. This follow-up
preserves the full-circle standing slam, which the user approved for multiplayer
pressure. The subsequent approved Hollow Purple change is documented below.

## Changes

- Meteor creation invokes the falling-block constructor directly. It no longer
  removes the spawn cell, even when the entire search column is occupied. This
  preserves block entities, inventories and neighbor state at spawn. Meteor
  impact/explosion behavior is unchanged.
- Titan Dome inherits the underlying terrain when covering a tracked temporary
  spell block, so an expired flame is not restored after collapse.
- Nature seedlings, roots and blooms retain placement identities instead of raw
  block snapshots. Cleanup only restores cells still owned by that placement;
  older thickets cannot erase newer seedlings or resurrect expired spell blocks.
- Zephyr and Titan save original equipment in persistent player attachments in
  the same save as inventory. Join/orphan cleanup restores the receipt first,
  and successful restoration clears it. Final death restores equipment before
  vanilla drops and soulbound collection. Receipts are not copied on death.
  Recovery applies to saves made with this version; previously lost items cannot
  be reconstructed without a backup.
- Titan equipment restoration is idempotent during collapse/cancellation. A
  second dome cannot overwrite an active dome's recovery receipt or spend charge.
- Church admission checks shaft clearance while treating removable vegetation
  as planned clearance. Statue refresh and vegetation removal happen only after
  successful admission and persistence. Failed admission preserves foliage and
  the offered heart.
- Corrected the Controls category translation key and removed the unused Aegis
  implementation that the earlier audit had reported as removed.

## Verification

All checks below passed on September 11. Runtime fixtures use disposable worlds
under `build/` and never open a user's Minecraft save. The enhanced equipment
fixture verified named, damaged, Protection III armor; serialization uses world
registry context so enchantments are retained.

- `./gradlew build` runs all eight Guardian contract suites.
- `./gradlew -I tools/audit_fixes_smoke.init.gradle runServer --args nogui`
  exercises occupied-chest meteor spawning, Nature ownership transfer, Titan
  expired-flame restoration and cancellation during collapse, and equipment
  save/reload recovery with named, damaged, enchanted equipment.
- `./gradlew -I tools/guardian_church_smoke.init.gradle runServer --args nogui`
  includes rejected-Peaceful vegetation/heart preservation and the existing
  successful ritual, return, retry and restoration checks.
- `./gradlew -I tools/guardian_nature_smoke.init.gradle runServer --args nogui`
  checks real arena Nature/Fire behavior and cleanup.
- Required asset/model validators, `git diff --check`, and JAR integrity checks.

These server checks do not replace a human client playtest of fight balance,
animation, or the Controls screen.

## Installation

Installed `build/libs/elementalwands-2.2.0.jar` into
`~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
Source and installed SHA-256 both verified:
`d4a128ca40716562cbaa3dc37d1f591b970591c6aee615a390b5206774637a1f`.

Previous installed JAR backed up as
`build/backups/elementalwands-before-audit-fixes-2026-09-11-0934.jar`.
Restart Lunar/Minecraft to load the changes. Human playtest is pending.

## Approved Hollow Purple commitment

The standing ground slam stays 360 degrees. It is not the jumping attack or the
floating-stone fan. The user explicitly prefers pressure on the surrounding party.

Hollow Purple now commits for the entire existing 70-tick (3.5-second) charge.
Switching or dropping the wand cannot cancel it and never refunds charge. The
caster rises at the existing rate, stays anchored horizontally, cannot walk/blink
or use items or blocks, and may aim freely until the single orb releases. No
invulnerability is granted. Release restores movement and retains the existing
Slow Falling effect. Death, disconnect, dimension change, server shutdown and
forced arena teardown clear the charge/lock without a refund or delayed shot.

The disposable audit fixture now also checks slot switching, item/blink rejection,
horizontal correction, late aim, the exact 70-tick release, no refund, movement
unlock and death cleanup. The expanded fixture, all eight Guardian build suites,
four asset/model checks, whitespace and JAR integrity checks passed.

Installed follow-up SHA-256 (source and Lunar copy both verified):
`0214a7540cbacf9ee0c429002c41af58b1609a65a6eb337338376b98fca4dbac`.
This supersedes the earlier audit JAR above. Its backup is
`build/backups/elementalwands-before-committed-ultimate-2026-09-11-1048.jar`.
Restart Lunar/Minecraft to load the change; human playtest is pending.
