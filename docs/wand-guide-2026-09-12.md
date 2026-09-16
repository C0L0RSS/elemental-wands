> Simplified welcome: [wand-guide-simple-2026-09-12.md](wand-guide-simple-2026-09-12.md) replaces the five-page introduction below with one basic-functions page.

# Spell categories and welcome scroll — September 12, 2026

Each loadout now has one Basic, one Technique and one Ultimate slot. The server
rejects spells assigned to a different category. Category labels appear in the
loadout, spell details and Controls. The store has a cycling All / Basic /
Technique / Ultimate filter. Key and mouse bindings remain independently editable.

`WandSpells.Category` defines slot roles. Existing freely swapped loadouts are
normalized by category and saved in the new order, retaining their selected spells
where valid. Ownership, Flux and cooldown/charge data are preserved. Existing slot
bindings stay as configured; players can remap them in Controls.

## Welcome and Guide

A new server-persistent `WELCOME_SEEN` flag is independent of the retired starter
chat tag. First-time players, and existing players on their first join after this
update, receive a welcome request. The client waits for a live non-spectator player,
no loading overlay, no other open screen and permission to edit outside combat.
It shows the parchment Guide, then acknowledges it to the server. A connection
that ends before displaying the scroll does not consume the welcome. Disconnect
clears the pending client request. The flag persists through save/reload and death.

The scroll is dismissible and has Open Hub, Next and Back buttons. The hub's Guide
button reopens it at any time. Its five pages cover:

1. Welcome, Wizard: brief original lore, choosing an element and reopening the hub.
2. Basic / Technique / Ultimate roles, actual configured keys and mouse controls.
3. Element-specific Flux, permanent purchases, switching and ultimate charge.
4. The Keeper's Promise: sanctuary lore and activating the Guardian Heart.
5. Trial preparation, spectator behavior after defeat, retry and lost-heart recall.

The hub key hint reads the real current binding instead of always claiming H.
The screen does not pause multiplayer or combat. The first automatic popup waits
until the player is outside combat; manually reopened guidance is available normally.

## Retired inventory guides

The mod no longer generates either Wizard's Path or Keeper's Promise books.
Existing generated guides are identified by exact title AND original author.
They are removed from the player's inventory on join and on opening the hub, and
from offering chests on stocking or interaction. Their information is in the Guide.
User-authored books and ordinary enchanted-book loot are preserved. Books inside
other storage are not globally scanned; a retrieved legacy guide is retired on
opening the hub or the next join. Retired books are no longer soulbound.

The offering chest still provides its Guardian Heart and points players to the
hub Guide when opened. Ritual, encounter and rewards otherwise retain their behavior.

## Verification

- Build and Guardian geometry/combat regressions passed.
- Isolated real-server hub fixture passed category enforcement for all five elements,
  old loadout normalization, intact Flux/ownership, welcome acknowledgement persistence
  across save/reload and death, authored-book preservation and the existing store suite.
- Native client fixture rendered all five Guide pages, category filters, loadouts,
  store and Controls. Welcome gating checks cover loading, other screens, combat and
  repeated requests. Small-screen hitboxes and saved remapping passed.
- Screenshots were visually reviewed; a crowded category label and the last guide
  paragraph were corrected. These are actual Minecraft screens with fixture state
  over the title panorama, not a human multiplayer onboarding playtest.
- Hub texture drift, VFX reference/count validation and whitespace checks passed.

The final native client run passed after the spacing corrections. The real church
fixture passed placement, heart stocking without a book, recall invalidation,
sealed admission, abort/retry, boss death, restoration, loot and one-time completion.
Screenshots and result markers are in `docs/wand-guide-verification/`.

## Installation

Built and installed to the existing Lunar Fabric 1.21.10 mods directory. Source
and installed JAR SHA-256 both equal:
`a8ac0b6da202380b8c081cc32fa13c867feb1d78b6014d7275df2f02779440bc`.

Previous store-version JAR is backed up at
`.local-backups/lunar/20260912-173825/elementalwands-2.2.0.jar`.
Restart Lunar and join a world to see the welcome scroll. Reopen it through H → Guide.
Existing players also see it once on their first join after updating. Servers and
clients should use the same build. Human first-join and multiplayer playtesting remain
pending; tests establish persistence, dispatch/gating rules and native rendering.
