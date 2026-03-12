# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build        # compile + produce jars in build/libs/
./gradlew runClient    # launch dev client
```

No test suite exists. After any code change, run `./gradlew build` to verify compilation.

### Deploying after a build

After `./gradlew build` succeeds, copy `build/libs/elementalwands-2.1.0.jar` to all three locations:

```
~/Library/Application Support/feather/mods/elementalwands-2.1.0.jar
~/Library/Application Support/feather/user-mods/1.21.10-fabric/elementalwands-2.1.0.jar
~/Library/Application Support/feather/player-server/servers/cb0dcfe6-44d4-4c0d-a1de-e2f201ed69cd/mods/elementalwands-2.1.0.jar
```

- `feather/mods/` — what Feather actually loads into the game client
- `feather/user-mods/1.21.10-fabric/` — Feather's user mod list (keeps it in sync)
- `player-server/.../mods/` — the local Feather test server

Restart the game/server after replacing jars for changes to take effect. Use Python for the copy since the project directory name contains a Unicode narrow no-break space (U+202F) that breaks normal shell `cd`:

```python
import shutil, pathlib
jar = pathlib.Path('/Users/antonlabas/Desktop/elementalwands 9.13.44\u202fAM/build/libs/elementalwands-2.1.0.jar')
targets = [
    '~/Library/Application Support/feather/mods/elementalwands-2.1.0.jar',
    '~/Library/Application Support/feather/user-mods/1.21.10-fabric/elementalwands-2.1.0.jar',
    '~/Library/Application Support/feather/player-server/servers/cb0dcfe6-44d4-4c0d-a1de-e2f201ed69cd/mods/elementalwands-2.1.0.jar',
]
for t in targets:
    shutil.copy2(str(jar), str(pathlib.Path(t).expanduser()))
```

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

## Adding a Custom GeckoLib Entity

The Stone Zombie (`StoneZombieEntity`) is the reference implementation for GeckoLib entities.

Key files:
- `entity/StoneZombieEntity.java` — extends `ZombieEntity`, implements `GeoEntity`
- `client/model/StoneZombieModel.java` — extends `GeoModel`, points to geo/anim/texture
- `client/renderer/StoneZombieRenderer.java` — extends `GeoEntityRenderer`
- `client/renderer/StoneZombieRenderState.java` — implements `GeoRenderState`

**GeckoLib 5.3-alpha-3 resource paths** (different from GeckoLib 4):
- Models: `assets/<namespace>/geckolib/models/<name>.geo.json`
- Animations: `assets/<namespace>/geckolib/animations/<name>.animation.json`
- Identifiers in `GeoModel` use the full path e.g. `geckolib/models/stone_zombie.geo.json`

**Geo.json format**: must be `format_version: 1.12.0` with `minecraft:geometry` array.
`texture_width`/`texture_height` in the description **must match the actual PNG dimensions** exactly, or UV mapping will be wrong.

**1.21.10 SpawnEggItem API**: use `settings.spawnEgg(entityType)` — the old 4-arg constructor is gone.

**1.21.10 item model system**: every item needs both:
- `assets/<namespace>/models/item/<name>.json`
- `assets/<namespace>/items/<name>.json`

## Adding a New Wand Ability / Wand

1. Add item class extending `AbstractWandItem`, override `castPrimary/Secondary/Ultimate` and cooldown getters.
2. Register in `ModItems.java`, add model JSON + texture PNG + lang key in `en_us.json`.
3. If the ability needs a zone effect, create a manager in `util/` and register its tick callback.
4. If a new entity is needed, register in `ModEntities.java`.

## Known Issues (as of v2.1.0)

- `CastPrimaryPayload` is registered server-side but never sent by the client.
- `StoneWandItem.PRIMARY_COOLDOWN_TICKS = 60` is unused; `getPrimaryCooldownTicks()` returns `40`.
- `ElementalWandsMod.giveStarterKit(...)` has an unused local variable (`persistent`).
