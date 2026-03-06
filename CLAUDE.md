# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build        # compile + produce jars in build/libs/
./gradlew runClient    # launch dev client
```

No test suite exists. After any code change, run `./gradlew build` to verify compilation.

## Package Structure

```
com.anton.elementalwands
├── ElementalWandsMod.java      # common init: registries, events, onboarding
├── ElementalWandsClient.java   # client init: keybindings, HUD, C2S ultimate packet
├── registry/                   # ModItems, ModBlocks, ModEntities
├── item/                       # AbstractWandItem + 6 wand implementations
├── entity/                     # projectile/zone entities
├── util/                       # stateful manager singletons (zone effects, charges)
├── data/                       # EWAttachments (persistent player data)
├── network/                    # ModNetworking (C2S/S2C payloads)
├── client/
│   ├── overlay/WandHudOverlay  # 3-slot ability HUD above hotbar
│   └── ClientPlayerData        # client-side cache of synced unlock bitmask
├── world/ModWorldGen.java      # biome modification for crystal ore worldgen
└── mixin/                      # PlayerEntityMixin
```

Data/assets live under `src/main/resources/`:
- `assets/elementalwands/lang/en_us.json` — all translatable strings
- `data/elementalwands/recipe/` — crafting/smelting recipes
- `data/elementalwands/loot_table/blocks/` — ore drop loot tables
- `data/elementalwands/worldgen/` — configured/placed feature JSONs

## Core Architecture

### Ability dispatch (`AbstractWandItem`)

All wands extend `AbstractWandItem`. Abilities are dispatched via:
- `use(...)` → `castPrimary` / `castSecondary` (right-click / shift+right-click, server-side)
- `CastUltimatePayload` C2S → `ModNetworking.handleCastUltimate` → `castUltimate` (X key)

Each wand overrides `castPrimary`, `castSecondary`, `castUltimate`, and can override cooldown getters (`getPrimaryCooldownTicks`, etc.).

**Cooldowns** are stored in ItemStack `CUSTOM_DATA` under NBT keys `ew_last_global`, `ew_last_primary`, `ew_last_secondary`. Frost stacks on the caster halve elapsed cooldown time (2× slower recovery). Always guard server-only logic with `world.isClient()` check.

**Ability locking**: `isAbilityUnlocked(player, ability)` checks the `UNLOCKED_SKILLS` bitmask attachment. Override this in a wand to restrict abilities (e.g., `FracturedWandItem` locks secondary and ultimate).

**Ultimate charge**: Stored in ItemStack NBT under `elementalwands:ultimate_charge` (0–100). Charged by `onWandDamageDealt()` (+5 per hit). Cast requires full 100 charge, which resets to 0 on success.

**Arcane Flux**: Persistent player attachment (`EWAttachments.ARCANE_FLUX`, Long, survives death). Gained at 1 flux per damage point dealt. Used to gate skill unlocks via `/ew unlock secondary|ultimate` command.

### Manager singletons (`util/`)

Complex zone/state effects use static manager classes that tick via world tick events. Each manager holds per-player or per-entity state in `HashMap`s keyed by UUID or `BlockPos`. Key managers:

| Manager | Used by |
|---|---|
| `MeteorManager` | Fire ultimate |
| `BlazeTrailManager` | Fire secondary runway |
| `BlinkRiftManager` | Space secondary rift tracking |
| `HollowPurpleChargeManager` | Space ultimate charge visuals |
| `BlizzardManager` | Ice ultimate zone |
| `ChillTracker` | Frost stack state per entity |
| `TitanDomeManager` | Stone ultimate dome |
| `TemporaryBlockManager` | Placed-block lifecycle (spikes, walls) |
| `CycloneManager` | Wind ultimate |
| `EventHorizonManager` | Space ultimate lingering effects |

### Network sync

Three payload types in `ModNetworking`:
- `CastPrimaryPayload` (C2S) — registered but currently unused client-side
- `CastUltimatePayload` (C2S) — sent by `ElementalWandsClient` on X key press
- `SyncPlayerDataPayload` (S2C) — carries `unlockedSkills` bitmask to client; received by `ClientPlayerData`; call `ModNetworking.syncPlayerData(player)` after any skill unlock

### Progression loop

1. First join → player receives `fractured_wand` + `The Wizard's Path` book (guarded by command tag `ew_starter_received`)
2. Mine crystal ore → smelt raw crystal → craft with `fractured_wand` → elemental wand
3. Deal wand damage → accumulate `ARCANE_FLUX` → `/ew unlock secondary|ultimate` (costs flux + XP levels)
4. `reset_rune` + elemental wand → reverts to `fractured_wand`

## Adding a New Wand Ability / Wand

1. Add item class extending `AbstractWandItem`, override `castPrimary/Secondary/Ultimate` and cooldown getters.
2. Register in `ModItems.java`, add model JSON + texture PNG + lang key in `en_us.json`.
3. If the ability needs a zone effect, create a manager in `util/` and register its tick callback.
4. If a new entity is needed, register in `ModEntities.java`.

## Known Issues (as of v2.1.0)

- `CastPrimaryPayload` is registered server-side but never sent by the client.
- `StoneWandItem.PRIMARY_COOLDOWN_TICKS = 60` is unused; `getPrimaryCooldownTicks()` returns `40`.
- `ElementalWandsMod.giveStarterKit(...)` has an unused local variable (`persistent`).
