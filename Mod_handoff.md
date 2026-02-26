# Elemental Wands (Fabric) - Current Handoff

Last verified against code at commit `48d3dcc` (`gui fixes`).

## What Changed Recently (Since Previous Handoff Baseline `12e6998`)

Major additions and behavior changes:

- Added a full progression loop:
  - New starter item: `fractured_wand`
  - New resources: raw + refined elemental crystals (fire/wind/stone/ice/space)
  - New block set: crystal ores + deepslate variants
  - New worldgen for all 5 crystal ore families
  - New crafting/smelting/reset recipes
- Added first-join onboarding:
  - Players receive `fractured_wand` + a written guide book (`The Wizard's Path`)
  - Guarded by command tag `ew_starter_received`
- Added `ModBlocks` + `ModWorldGen` registration in common init.
- Added ability lock support in `AbstractWandItem` and HUD lock rendering.
  - Fractured Wand only unlocks PRIMARY.
- HUD updates:
  - HUD moved higher above hotbar
  - Fractured Wand uses a single centered slot and gray "MANA" theme
  - Locked slots draw a padlock overlay
- Fire wand secondary reworked into a long runway-style "Dragon's Pyre".
- Wind wand Zephyr Strike updated:
  - Dash disabled while Zephyr is active
  - Elytra equipment restore now preserves/re-equips old chestplate
  - Added descent ring particles
  - Removed old max-duration timeout logic
- Stone wand pacing and wall behavior updated:
  - Primary eruptions execute much faster
  - Secondary wall changed to 4x4, 70-tick duration
- Ice secondary gust now respects player pitch for vertical aiming.
- Space ultimate visuals and orb tuning updated:
  - Charge visuals now red/blue orb convergence
  - Hollow Purple orb increased to radius 5 and 60 damage

---

## Mod Metadata

- Mod ID: `elementalwands`
- Version: `2.1.0`
- Minecraft: `1.21.10`
- Java: `21`
- Loader/API: Fabric Loader + Fabric API

---

## Player Controls

- Primary: right click (not sneaking)
- Secondary: shift + right click (sneaking)
- Ultimate: `X` (client keybind -> C2S payload -> server cast)

Entrypoints:

- Primary/Secondary: `AbstractWandItem.use(...)`
- Ultimate: `ElementalWandsClient` sends `CastUltimatePayload` -> `ModNetworking.handleCastUltimate(...)`

---

## Progression and Content Loop (Current)

### First join starter flow

Implemented in `ElementalWandsMod` via `ServerPlayConnectionEvents.JOIN`.

On first join per player (command tag check):

- Gives `elementalwands:fractured_wand`
- Gives written book (`The Wizard's Path`)
- Sends welcome message
- Adds command tag `ew_starter_received`

### Crystal ore mining pipeline

1. Mine elemental crystal ore blocks (normal/deepslate variants).
2. Loot table behavior:
   - Silk Touch -> ore block drops
   - Otherwise -> corresponding raw crystal (fortune-aware)
3. Smelt raw crystal -> refined crystal (`minecraft:smelting`, 200 ticks, 0.7 xp).
4. Craft `fractured_wand + refined crystal` (shapeless) to awaken into elemental wand.
5. Use `reset_rune` + elemental wand (shapeless) to revert back to `fractured_wand`.

### New blocks/items

Registered in:

- `src/main/java/com/anton/elementalwands/registry/ModBlocks.java`
- `src/main/java/com/anton/elementalwands/registry/ModItems.java`

Blocks:

- `fire_crystal_ore`, `wind_crystal_ore`, `stone_crystal_ore`, `ice_crystal_ore`, `space_crystal_ore`
- `deepslate_*` variants for each above

Items:

- `fractured_wand`
- `raw_*_crystal` (5)
- `*_crystal` refined (5)
- `reset_rune`

Creative tabs:

- Tools: all wands + fractured wand + titan sword + reset rune
- Ingredients: all raw + refined crystals
- Building blocks: all ore blocks

---

## World Generation

Registration path:

- `ElementalWandsMod.onInitialize()` -> `ModWorldGen.registerAll()`
- `ModWorldGen` adds placed feature keys to overworld biome modifications.

Data-driven feature files:

- Configured features: `data/elementalwands/worldgen/configured_feature/*.json`
- Placed features: `data/elementalwands/worldgen/placed_feature/*.json`

Current ore settings (all five ore types):

- Vein size: `6`
- Count per placement: `8`
- Height range: trapezoid `-60` to `70`
- Targets:
  - `minecraft:stone_ore_replaceables` -> normal ore
  - `minecraft:deepslate_ore_replaceables` -> deepslate ore

Tool tags:

- `data/minecraft/tags/block/mineable/pickaxe.json`
- `data/minecraft/tags/block/needs_stone_tool.json`

---

## Core Wand System

Base class: `AbstractWandItem`

- Default cooldowns:
  - Primary: 20 ticks
  - Secondary: 120 ticks
  - Ultimate: 800 ticks
  - Global: 6 ticks
- Cooldown storage keys in `CUSTOM_DATA`:
  - `ew_last_global`, `ew_last_primary`, `ew_last_secondary`, `ew_last_ultimate`
- Chill interaction:
  - If caster has frost stacks, cooldown elapsed time is halved (cooldowns recover 2x slower).

Ability lock support:

- `AbstractWandItem.isAbilityUnlocked(...)` introduced.
- Default: all unlocked.
- Fractured Wand: PRIMARY only.
- Locked casts show `hud.elementalwands.locked` actionbar text.

---

## Wand-by-Wand Current Behavior

### Fractured Wand (`FracturedWandItem`)

- Primary:
  - 20-range beam style hit via raycast
  - Soul fire particle line
  - 3.0 damage on entity hit
- Secondary/Ultimate:
  - Locked behavior message: "The core is fractured... find a Crystal to awaken it."

### Fire Wand (`FireWandItem`)

- Passive while held:
  - Constant fire resistance refresh
  - If player stands on pyre ground shortly after cast, gains regen + speed boost
- Primary:
  - Spawns `InfernoWaveEntity`
- Secondary (reworked Dragon's Pyre runway):
  - Creates ~40-block forward runway, ~5-block width
  - Applies fire damage and ignite in the runway lane
  - Places temporary magma below and fire above for 100 ticks
- Ultimate:
  - Meteor strike (`MeteorManager`) from +35 height, explosion power 15

### Wind Wand (`WindWandItem`)

- Primary:
  - Dual `VacuumBladeEntity` projectiles with side offsets
- Secondary (Waylay Dash):
  - Charge system: 3 max, 80 ticks recharge each, chain window 30 ticks
  - Not using `tryStartCooldown` (charge-based logic)
  - Disabled while Zephyr Strike active
- Ultimate (Zephyr Strike):
  - Activates airborne state, equips temporary Elytra, launches player
  - On impact after short grace, triggers explosion scaled by impact velocity
  - Stores/restores previous chest armor in memory map (`ZEPHYR_CHESTPLATES`)

### Stone Wand (`StoneWandItem`)

- Primary (Earthen Maw / Tectonic wave):
  - Cooldown override currently returns 40 ticks
  - Builds 15-block spike path
  - Scheduler now processes 3 logic steps per tick (faster eruption)
  - Spike damage: 6.0 magic + vertical knockback
- Secondary:
  - 4x4 stone wall 2 blocks in front of caster
  - Temporary duration 70 ticks
  - Active wall grants nearby resistance buff while tracked
- Ultimate:
  - Titan Dome via `TitanDomeManager.startDome(...)`

### Ice Wand (`IceWandItem`)

- Primary:
  - 3-shot frost volley with shatter logic (`ChillSnowballEntityWithShatter`)
- Secondary (Glacial Gust):
  - Fires 5 piercing short-lived wave projectiles
  - Now uses player pitch in directional calculation (vertical aiming supported)
- Ultimate:
  - Starts Blizzard zone (`BlizzardManager`)

### Space Wand (`SpaceWandItem`)

- Primary:
  - `SingularityBoltEntity`
- Secondary:
  - Blink forward + persistent rift swapback flow (`BlinkRiftManager`)
- Ultimate:
  - Hollow Purple charge sequence (`HollowPurpleChargeManager`)
  - Charge: 60 ascent + 10 hold ticks
  - Visuals: converging red/blue orb system
  - Orb (`HollowPurpleOrbEntity`) currently tuned to:
    - Radius: 5.0
    - Damage: 60.0
    - Speed: 1.3
    - Lifetime: 65 ticks or 90 blocks travel
    - Terrain erasure in spherical volume each tick

---

## HUD and UX

File: `src/main/java/com/anton/elementalwands/client/overlay/WandHudOverlay.java`

Current behavior:

- HUD vertical offset increased (`SLOT_Y_OFFSET_FROM_HOTBAR_TOP = 50`)
- Fractured wand renders one centered ability slot (primary only)
- Locked abilities show dark overlay + padlock icon
- Added `MANA` theme/accent for fractured state

Localization updates in `assets/elementalwands/lang/en_us.json` include:

- Names for fractured wand/crystals/ores/reset rune
- `hud.elementalwands.locked`

---

## Key File Map

Core init and registration:

- `src/main/java/com/anton/elementalwands/ElementalWandsMod.java`
- `src/main/java/com/anton/elementalwands/registry/ModItems.java`
- `src/main/java/com/anton/elementalwands/registry/ModBlocks.java`
- `src/main/java/com/anton/elementalwands/world/ModWorldGen.java`

Wands:

- `src/main/java/com/anton/elementalwands/item/AbstractWandItem.java`
- `src/main/java/com/anton/elementalwands/item/FracturedWandItem.java`
- `src/main/java/com/anton/elementalwands/item/FireWandItem.java`
- `src/main/java/com/anton/elementalwands/item/WindWandItem.java`
- `src/main/java/com/anton/elementalwands/item/StoneWandItem.java`
- `src/main/java/com/anton/elementalwands/item/IceWandItem.java`
- `src/main/java/com/anton/elementalwands/item/SpaceWandItem.java`

Space ultimate internals:

- `src/main/java/com/anton/elementalwands/util/HollowPurpleChargeManager.java`
- `src/main/java/com/anton/elementalwands/entity/HollowPurpleOrbEntity.java`

Data and assets:

- `src/main/resources/data/elementalwands/recipe/*.json`
- `src/main/resources/data/elementalwands/loot_table/blocks/*.json`
- `src/main/resources/data/elementalwands/worldgen/**/*`
- `src/main/resources/assets/elementalwands/lang/en_us.json`

---

## Known Technical Notes

- `ModNetworking.CastPrimaryPayload` is still registered server-side, but client currently only sends `CastUltimatePayload`.
- `StoneWandItem` has an unused constant `PRIMARY_COOLDOWN_TICKS = 60`, while `getPrimaryCooldownTicks()` returns `40`.
- `ElementalWandsMod.giveStarterKit(...)` includes an unused local variable (`persistent`).

---

## Dev Quickstart

- Build: `./gradlew build`
- Run dev client: `./gradlew runClient`

Recommended balance edit points:

- Ability logic: each `*WandItem.java`
- Projectile tuning: `entity/*`
- Zone/ultimate managers: `util/*Manager.java`
- Progression economy: `data/elementalwands/recipe/*` + loot tables + worldgen json
