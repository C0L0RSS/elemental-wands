# Progression and controls

Current code map reviewed September 17, 2026. Java paths below are relative to
`src/main/java/com/anton/elementalwands/`.

## Player behavior

H or `/ew hub` opens the native parchment hub. A new player chooses an affinity
there; the server grants a wand. Each element keeps its own Flux, learned spells,
loadout, and element XP. Switching or resetting affinity preserves that progress.
The short Getting Started guide covers the basics; old generated lore books are
retired by exact title/author, without removing ordinary player books.

Loadouts have five free numbered slots. Any owned spell, including an ultimate,
can occupy any slot; the same spell cannot occupy two slots. Categories remain
store metadata, pricing, and spell-book eligibility, not slot restrictions.
Purchases fill available empty positions without replacing chosen spells.

Default slot inputs are left mouse, right mouse, X, Z, V; Spell alternate is R.
Bindings are editable in Controls and saved in
`config/elementalwands-controls.properties`. Existing saved bindings can differ.
Sneak + right mouse performs ordinary block interaction. Slot 1 also handles
close aimed Flashover disarming and Nature root-knot breaking.

The HUD sits beside the hotbar, shows owned equipped spells without key labels,
and hides empty slots. Controls > Move HUD permits dragging or arrow nudging;
`config/elementalwands-hud.properties` stores the position.

Purchases spend elemental Flux, not vanilla XP levels. Default Basics are free;
additional Basics and Techniques cost 500 Flux, ultimates 1,500. Spell books grant
credits for a free choice: Basic, Basic/Technique, or any category. The store uses
the lowest eligible credit before Flux. Credits and church claim receipts survive
death. A Guardian reward book is claimed once per eligible player/site, with
inventory space required before recording the claim.

Element XP is separate from spendable Flux and vanilla experience. Levels 1–6
increase spell damage by 10% per level above 1. `ElementLevels` owns thresholds;
`SpellCombat` awards XP from actual health damage/healing with target weighting.
Do not replace this with nominal damage accounting or credit a different element
when a delayed spell lands after an affinity change.

Loadout edits, purchases, and normal affinity changes require being outside
combat and the arena; casting/damage applies a ten-second combat lock. Hollow
Purple commitment also prevents editing. Admin unlock commands require operator
permission and must remain separate from normal progression.

## Implementation boundaries

| Owner | Responsibility |
| --- | --- |
| `data/WandSpells` | Stable IDs, catalog, categories, prices, five-slot normalization |
| `data/EWAttachments`, `data/ElementProgress` | Persisted per-player state and death copying |
| `util/WandProgression`, `util/SpellBooks`, `data/ElementLevels` | Purchases, legacy migration, credits, levels |
| `util/WandLoadouts` | Server validation, combat lock, equipment and cast dispatch |
| `client/WandControls`, input mixins | Bindings, held input, release/cancellation |
| `client/screen/WandHubScreen`, `client/overlay/WandHudOverlay` | Presentation of synchronized state |
| `network/ModNetworking` | Bounded requests and authoritative state sync |

`CastSlotPayload` sends a slot index, not a client-authorized spell. The server
resolves the actual loadout and validates life, held item, ownership, affinity,
arena rules, and committed actions. Fire Leap uses its dedicated aimed-release
request; hold/release channels also require server validation.
`HubActionPayload` includes expected affinity to reject stale-screen purchases.
Call `ModNetworking.syncPlayerData(player)` after progression/loadout changes.

Cooldowns are stored on the wand per spell ID (`ew_cd_*`, `ew_cdd_*`). The
`AbstractWandItem.beginCast`/`endCast` context preserves identity through handlers
that dispatch by ability category. Keep the six-tick global tap and ten-tick
shared Basic recovery as well as individual timers. Entangle slows recovery.
Swapping slots does not erase cooldowns or the shared 100-point ultimate reservoir.
`onWandDamageDealt` owns Flux/charge awards; Nature has explicit charge windows.

## Parties

`/party` and `/ew party` provide create, invite, accept, decline, list, leave,
kick, transfer, and disband. Membership persists per world; invitations expire
and are not persisted. Parties are independent of vanilla scoreboard teams.

`party/WandAllies` protects the caster, party members, vanilla teammates, their
pets, and Nature trees from harmful wand contacts. Delayed effects retain owner
identity and resolve current allies. Respect PvP-disabled rules too. Ordinary
sword/bow PvP and physical terrain obstruction remain Minecraft behavior.
`PartyStore` writes membership atomically before publishing changes; failed writes
must not partially change live membership, and corrupt data must be preserved.

For commands and historical verification, see the
[party report](../archive/reports/parties-2026-09-12.md). For applicable runners,
see [testing](testing.md).
