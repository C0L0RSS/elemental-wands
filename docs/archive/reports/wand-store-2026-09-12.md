> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

> Category and welcome follow-up: [wand-guide-2026-09-12.md](wand-guide-2026-09-12.md) supersedes the two regular slots and adds the first-join Guide.

# Element progression and spell store — September 12, 2026

The native parchment hub now has Loadout, Spell Store and Controls pages. Change
Element opens a chooser, while the rest of the hub displays only the selected
element. Each element has a separate saved Flux balance, permanent spell ownership
and remembered loadout. Switching never refunds, deletes or transfers purchases.

## First-version rules

- Free basic spell for every element; existing secondaries cost 500 Flux and
  ultimates cost 1,500 Flux. Purchases no longer consume Minecraft XP.
- Flux comes from the existing wand damage rewards and is credited to the spell's
  source element. Lingering Fire damage still credits Fire after changing to Nature.
  Lingering damage also cannot charge another element's held ultimate.
- Free element changes and loadout edits outside combat. The existing ten-second
  combat lock and arena participation restrictions apply to purchasing and switching.
- H or `/ew hub` opens the hub. `/ew affinity <element>` now switches directly.
  `/ew affinity reset` only deselects; it preserves all progress. Admin unlocks grant
  permanent ownership to the selected element, and require one to be selected.
- New players see a free starter spell and store shortcuts in the two unowned slots.
  Existing three-spell defaults remain reserved; buying their corresponding spell
  makes it usable. Regular slots can be swapped, with two regular slots and one ultimate.
- Only the existing spells are sold in this version. New spells, upgrade mechanics,
  alternate ultimates and economy balancing are future work. No shrine or refund system.

## Persistence and migration

`ElementProgress` contains Flux and owned spell IDs. `EWAttachments.ELEMENT_PROGRESS`
is a map keyed by affinity, persisted and copied on death. Existing `WAND_LOADOUTS`
continues to store equipped spell IDs per affinity. `WandProgression` is the server
service for migration, balances, purchases, ownership and administrative grants.

A persistent migration marker imports the old Arcane Flux balance and secondary /
ultimate unlock flags exactly once, into the current element. The old fields remain
readable for this migration, but are never live balances again. Legacy players with
no element defer migration until their next choice. No XP is refunded or removed.
Migration only converts the original default spells, avoiding blanket grants of
future store additions. Other elements start with zero Flux and their free starter.

Purchases validate expected affinity, combat eligibility, spell membership, ownership
and funds on the server. Deduction and permanent ownership are saved together in one
record. Repeated or stale requests cannot double-charge or buy another element's spell.
The sync payload includes current-element ownership IDs; both hub and HUD use these.
Compatibility unlock flags remain derived for existing ability handlers.

The store supports three entries per page with pagination for larger future catalogs.
Spell metadata owns prices. The saved ownership format supports individual spell IDs;
adding a new spell still requires its combat implementation and catalog metadata.

## Verification

- `./gradlew build`: compilation, packaging and Guardian regression checks passed.
- Real isolated server: legacy Flux/unlock migration (including unselected legacy
  players), no migration replay after switches, per-element earnings and purchases,
  insufficient funds, duplicate/stale/cross-element requests, zero-XP purchasing,
  unchanged cooldown/charge on equip, player save/reload, actual death/respawn,
  combat restrictions, invalid equips and swapped spell dispatch passed.
- Isolated native Minecraft client: rendered all element themes, store purchasing
  and owned states, element chooser and Controls; scaled 320x240 hitboxes and
  persisted keyboard remapping passed. Screenshots use fixture progression over
  the title panorama; they are native rendering evidence, not a human combat playtest.
- Hub texture export drift check (11 textures), complete VFX asset validation,
  `git diff --check` and JAR integrity passed. Approved artwork was preserved.
- Evidence: `docs/archive/evidence/wand-store-verification/` (screenshots, server.txt, client.txt).

## Installation

Built and installed to the existing Lunar Fabric 1.21.10 mod path:
`~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.

Verified source and installed SHA-256:
`85a3557bc2fb1f1d434d8ce2eba73fb3ee31fb99e473ae2c6c5413110cf5ae9a`.

Previous JAR backup:
`.local-backups/lunar/20260912-171622/elementalwands-2.2.0.jar`
(previous hash `17d5282acdc3b92a9791c215ff576de7723bce6b55e5e81096df93558d8933aa`).

Restart Lunar and press H. Multiplayer clients and server must use this same build
because the progression sync payload changed. Human playtesting of store flow and
Flux earning pace remains pending. Rolling back the JAR does not reverse migrated
player saves; use a matching world/player backup if reverting progression versions.
