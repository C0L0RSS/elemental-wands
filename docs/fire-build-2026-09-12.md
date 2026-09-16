> Playtest tuning: [fire-build-tuning-2026-09-12.md](fire-build-tuning-2026-09-12.md) supersedes the original damage and grounded-hop values below.

# Fire build expansion — September 12, 2026

Fire now offers two additional purchasable spells alongside Inferno Wave,
Dragon's Pyre and Meteor. Defaults and prior purchases are preserved. Each new
spell costs 500 Fire Flux; neither is granted by migrating old unlock flags.
The existing operator unlock-all command grants the new options as well.

## Initial tuning

| Spell | Category | Behavior |
| --- | --- | --- |
| Flamethrower | Basic | Hold for a six-block widening cone; 5 damage per successful half-second pulse, subject to ordinary damage immunity. |
| Fire Hop | Technique | Grounded, short directional leap; 3 damage within 2.5 blocks at takeoff; six-second cooldown. |

Flamethrower gains 1.25 heat per tick, reaching 100 after four seconds of uninterrupted
firing. Releasing cools 1.5 heat per tick. At 100 it locks until heat reaches 25
(about 2.5 seconds). Continued held input can resume once cooled. There is no ground
fire, lingering burn, escalating damage or terrain mutation. Damage requires cone
membership and a clear block ray to a sampled target point. The caster, teammates
and their own tameable pets are excluded. Successful damage earns Fire Flux;
ultimate charge is limited to two per successful damage pulse across all targets.
The beam uses the existing approved animated Fire ribbons. Five visual rays stop at
solid cover; emitted particles inherit the established camera fade and short lifetime.

Fire Hop follows the server's vanilla movement input relative to yaw, normalizing
diagonals; neutral input uses forward. Initial horizontal/upward velocity is 0.8/0.45.
It requires the player to be grounded, not riding, gliding or touching water. It
has no invulnerability, airborne chains, landing damage or extra fall protection.
Normal player collision handles the leap. Landing produces only embers and a quiet
sound. The takeoff burst uses clear block rays, a radius check and modest Fire damage.
Entangle slows its cooldown using the same elapsed-time convention as existing spells.

Numbers live in `FireBuildRules`; these are starting playtest values. Human movement,
first-person flame appearance and fight balance still need testing in Lunar.

## Input, persistence and dispatch

`WandLoadouts` dispatches the new spells by stable ID, after the existing ownership,
category, held-wand, life, arena and charge-commitment checks. Legacy spells retain
normal affinity handler dispatch. The new IDs are `flamethrower` and `fire_hop`.

Held Basic input sends a short heartbeat every two client ticks and an explicit
release message on release/menu/input clearing. A server lease expires after six
ticks without refresh. The channel also stops after changing item, selected hotbar
slot, world, affinity, loadout, life state or encounter admission. Disconnect clears
transient channel/landing tracking; server shutdown clears both maps.

`FIRE_BUILD_STATE` persists heat, overheat and Hop readiness per player and copies
on death. Changing or obtaining another wand cannot clear heat or Hop cooldown.
Heat cools while online even with the wand put away; it is retained during logout.
Hop uses world-time readiness. New state sync supplies the heat gauge and Hop HUD
cooldown. Fire charge cannot be transferred by delayed damage after switching elements.

## UI

- Fire HUD icons follow the exact equipped spell, including the two new options.
- A labeled heat bar sits above the ability row while Flamethrower is equipped;
  its colors warm toward red and it explicitly says OVERHEATED while locked.
- New icons are native pixel compositions using the approved Fire glyphs.
  No approved textures or production PNG counts changed.
- Store details include spell timing and the new spells' range/burst information.
- Owned spells can be equipped directly from the store. The button's tooltip names
  the replaced spell; the loadout detail also displays the replacement.
- Unaffordable purchases state the exact additional Flux needed. Purchase/equip
  feedback has a highlighted footer strip.
- Fire's five-spell catalog exercises pagination and category filters. Paid Basic
  spells remain locked until purchased; saved spell IDs stay independent of slots.

## Verification

The isolated real-server Fire fixture covers paid purchase/equip, cone damage,
solid cover and range, held heat, release, heartbeat expiry, item changes, overheating
and recovery, save/reload, directional/diagonal grounded hopping, takeoff damage,
no subsequent damage, no ground fire, cooldown and death persistence.

The native client fixture rendered new spell price/owned states, direct-equipping
controls, both composed icons, the equipped build and the heat gauge at normal and
overheated values. Screenshots in `docs/fire-build-verification/` use fixture state
over the title panorama; the heat component screenshot is enlarged for inspection.
These do not claim a human combat or first-person VFX playtest.

Required clean build, Guardian regressions, asset validators/export drift checks,
whitespace and JAR integrity are checked for the final package. Existing hub/server
progression tests are run again with the expanded Fire catalog.

## Final validation and installation

Clean build and all required asset checks passed. The final Fire server run also
passed item-switch cancellation/heat retention, exact takeoff damage and no ground
fire. The existing hub progression server suite passed with the five-spell Fire
catalog. Native screenshots were reviewed for layout and icon correctness. Results
are saved beside the screenshots in `docs/fire-build-verification/`.

The built and installed Lunar Fabric 1.21.10 JARs have matching SHA-256:
`f19e147404844a8f82056e9bac5ce2dc28fdd13baa38f325fa6635b2234ed398`.
Previous JAR backup:
`.local-backups/lunar/20260912-203540/elementalwands-2.2.0.jar`.

Restart Lunar, press H, choose Fire and open Spell Store. Use category filters or
the next-page arrow to find Flamethrower and Fire Hop. Each costs 500 Fire Flux.
Operators can use the existing `/ew admin unlock all` flow for playtesting. Clients
and server must share the new build because held-input and Fire-state payloads were
added. Human combat, movement feel and first-person appearance remain open for tuning.
