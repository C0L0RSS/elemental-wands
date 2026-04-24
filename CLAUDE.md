# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build        # compile + produce jars in build/libs/
./gradlew runClient    # launch dev client
```

No test suite exists. After any code change, run `./gradlew build` to verify compilation.

### Deploying after a build

After `./gradlew build` succeeds, copy `build/libs/elementalwands-2.2.0.jar` to two locations inside the Feather data directory:

1. **Feather client mod folder** — the jar Feather loads into the game:
   - Windows: `%APPDATA%\.feather\user-mods\1.21.10-fabric\elementalwands-2.2.0.jar`
   - macOS: `~/Library/Application Support/.feather/user-mods/1.21.10-fabric/elementalwands-2.2.0.jar`
2. **Feather player-server mods folder** — the jar the local test server loads:
   - Windows: `%APPDATA%\.feather\player-server\servers\<SERVER-UUID>\mods\elementalwands-2.2.0.jar`
   - macOS: `~/Library/Application Support/.feather/player-server/servers/<SERVER-UUID>/mods/elementalwands-2.2.0.jar`

**Current active player-server UUID:** `6dab8e0e-d0dd-40f4-8062-985f17cbf0ca` (Fabric 1.21.10, 7038 MB). If unsure, check `%APPDATA%\.feather\player-server\player-servers.json` — each entry's `id` is the folder name under `servers/`.

Note: `%APPDATA%\.feather\mods\` only contains `feather-mods.json` (manifest) — **do not drop the jar there**. The real client jar location is `user-mods/1.21.10-fabric/`.

Restart the game/server after replacing jars for changes to take effect.

**Windows (bash/Git Bash) one-liner:**

```bash
SRC="build/libs/elementalwands-2.2.0.jar"
cp "$SRC" "$APPDATA/.feather/user-mods/1.21.10-fabric/elementalwands-2.2.0.jar"
cp "$SRC" "$APPDATA/.feather/player-server/servers/6dab8e0e-d0dd-40f4-8062-985f17cbf0ca/mods/elementalwands-2.2.0.jar"
```

**Cross-platform Python fallback:**

```python
import shutil, pathlib, os, sys
jar = pathlib.Path('build/libs/elementalwands-2.2.0.jar').resolve()
server_uuid = '6dab8e0e-d0dd-40f4-8062-985f17cbf0ca'
feather = pathlib.Path(os.environ['APPDATA']) / '.feather' if sys.platform == 'win32' \
    else pathlib.Path('~/Library/Application Support/.feather').expanduser()
targets = [
    feather / 'user-mods' / '1.21.10-fabric' / jar.name,
    feather / 'player-server' / 'servers' / server_uuid / 'mods' / jar.name,
]
for t in targets:
    shutil.copy2(jar, t)
    print('wrote', t)
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
| `BrinicleShardManager` | Ice primary shard plants + pulsing zones |
| `TendrilBloomManager` | Ice secondary tendrils + blooms |
| `WhiteoutManager` | Ice ultimate fog + shard amplification |
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

## Known Issues (as of v2.2.0)

- `CastPrimaryPayload` is registered server-side but never sent by the client.
- `StoneWandItem.PRIMARY_COOLDOWN_TICKS = 60` is unused; `getPrimaryCooldownTicks()` returns `40`.
- `ElementalWandsMod.giveStarterKit(...)` has an unused local variable (`persistent`).
