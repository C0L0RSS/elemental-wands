# AGENT.md

This file gives coding agents the current working map for the Elemental Wands
repo. It is based on the older `CLAUDE.md`, but cleaned up for the current
Universal Wand / affinity architecture.

## Build And Run

```bash
./gradlew build        # compile + produce jars in build/libs/
./gradlew runClient    # launch a dev client
```

There is no test suite. After any code change, run `./gradlew build` to verify
the mod still compiles and packages.

For VFX work, the required repository checks are:

```bash
./gradlew clean build
git diff --check
python3 tools/validate_remaining_vfx_assets.py
unzip -t build/libs/elementalwands-2.2.0.jar
```

The validator covers JSON and texture references, RGBA/power-of-two rules,
material depth, unique animation frames, model references, the six registered
Fire/Wind particle additions, and exact production counts. Asset generators
must only be run with `--replace` when intentionally replacing the families
owned by that generator; never use a Fire/Wind pass to regenerate Arcane.
The exact package contract is Fire 77, Wind 53, Stone 41, Nature 44, Space 81
(296 affinity PNGs), plus two shared gear/HUD PNGs and 42 particle definitions.

### Deploying After A Build

After `./gradlew build` succeeds, copy `build/libs/elementalwands-2.2.0.jar`
into the Lunar Client Fabric mods folder for 1.21:

- macOS (verified on this machine): `~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Windows: `%USERPROFILE%\.lunarclient\profiles\lunar\1.21\mods\fabric-1.21.10\elementalwands-2.2.0.jar`

Restart Lunar Client after replacing the jar.

macOS / bash:

```bash
cp build/libs/elementalwands-2.2.0.jar "$HOME/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar"
shasum -a 256 build/libs/elementalwands-2.2.0.jar "$HOME/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar"
```

The two SHA-256 values must match. Do not report deployment complete from a
filename match alone.

Windows / Git Bash:

```bash
cp build/libs/elementalwands-2.2.0.jar "$USERPROFILE/.lunarclient/profiles/lunar/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar"
```

## Package Structure

```text
com.anton.elementalwands
|-- ElementalWandsMod.java      # common init: registries, events, onboarding, commands
|-- ElementalWandsClient.java   # keybinds, HUD registration, client networking
|-- registry/                   # ModItems, ModBlocks, ModEntities
|-- item/                       # AbstractWandItem, UniversalWandItem, per-affinity handlers
|-- entity/                     # projectile, zone, and custom mob entities
|-- util/                       # stateful manager singletons and helpers
|-- data/                       # EWAttachments and WizardAffinity
|-- network/                    # ModNetworking payloads and sync helpers
|-- client/
|   |-- overlay/WandHudOverlay  # 3-slot ability HUD above hotbar
|   `-- ClientPlayerData        # client cache for synced unlocks and affinity
|-- world/ModWorldGen.java      # currently a no-op after crystal worldgen removal
`-- mixin/                      # PlayerEntityMixin
```

Important resources:

- `src/main/resources/fabric.mod.json` - mod metadata and entrypoints
- `src/main/resources/assets/elementalwands/lang/en_us.json` - translatable strings
- `src/main/resources/assets/elementalwands/items/*.json` - 1.21.10 item model definitions
- `src/main/resources/assets/elementalwands/models/item/*.json` - item model JSONs
- `src/main/resources/assets/elementalwands/geckolib/**` - GeckoLib models and animations
- `tools/generate_fire_vfx_assets.py` - deterministic Cinderforge Fire assets
- `tools/generate_wind_vfx_assets.py` - deterministic Sky Shear Wind assets
- `tools/generate_shared_vfx_assets.py` - neutral universal wand and HUD frame only
- `tools/validate_remaining_vfx_assets.py` - all-affinity/shared VFX audit
- `docs/vfx-style-guide.md` - palettes, family ownership, exclusions, and counts

Crystal ore blocks, crystal crafting recipes, and ore worldgen were removed in
the Universal Wand refactor. `ModBlocks.registerAll()` and `ModWorldGen.registerAll()`
are kept as no-op hooks.

## Core Architecture

### Universal Wand Dispatch

There is one wand item registered as `fractured_wand`, displayed to players as
`Wizard's Wand`. Its runtime behavior comes from the player's persisted
`WizardAffinity` attachment:

- `NONE`
- `FIRE`
- `WIND`
- `STONE`
- `NATURE`
- `SPACE`

`UniversalWandItem` dispatches ability calls to:

- `FireAbilityHandler`
- `WindAbilityHandler`
- `StoneAbilityHandler`
- `NatureAbilityHandler`
- `SpaceAbilityHandler`

`NONE` affinity uses the basic fractured primary beam and inert secondary /
ultimate behavior.

### Controls And Dispatch Flow

- Right click: primary ability through `AbstractWandItem.use(...)`
- Shift + right click: secondary ability through `AbstractWandItem.use(...)`
- `X`: ultimate ability through `ElementalWandsClient` -> `CastUltimatePayload`
  -> `ModNetworking.handleCastUltimate(...)`

Primary and secondary casts happen server-side from item use. The ultimate is
client-keybound and sent to the server through C2S networking.

### Cooldowns And Charge

Primary and secondary cooldowns are stored in ItemStack `CUSTOM_DATA`:

- `ew_last_global`
- `ew_last_primary`
- `ew_last_secondary`

Entangle stacks on the caster halve elapsed cooldown time, effectively slowing
cooldown recovery.

Ultimate charge is stored on the wand ItemStack under:

- `elementalwands:ultimate_charge`

`AbstractWandItem.onWandDamageDealt(...)` grants Arcane Flux and adds +5 ultimate
charge to the player's main-hand wand. A successful ultimate requires 100 charge
and resets the reservoir to 0.

### Progression And Affinity

First join gives the player `The Wizard's Path` written book and marks them with
the `ew_starter_received` command tag.

The book lets a new player choose an affinity with clickable commands:

- `/ew affinity fire`
- `/ew affinity wind`
- `/ew affinity stone`
- `/ew affinity nature`
- `/ew affinity space`

Choosing an affinity:

- sets `EWAttachments.AFFINITY`
- resets unlocked skills and Arcane Flux
- grants a `fractured_wand` / Wizard's Wand if the player does not already have one
- refreshes the book
- syncs player data to the client HUD

Ability unlocks use:

- `/ew unlock secondary`
- `/ew unlock ultimate`

Costs are defined in `EWAttachments`:

- Secondary: 500 Arcane Flux + 15 XP levels
- Ultimate: 1500 Arcane Flux + 30 XP levels

`/ew affinity reset` clears affinity, skills, and Arcane Flux so the player can
choose a different element.

Admin helpers:

- `/ew admin unlock secondary <player>`
- `/ew admin unlock ultimate <player>`
- `/ew admin unlockall <player>`

## Element Behaviors

### Fire

- Passive: fire resistance while the wand is held.
- Primary: `InfernoWaveEntity`, presented as the six-frame Cinder Maw with an
  interpolated clinker/ember wake. Projectile speed, range, damage, piercing,
  block collision, burning, and temporary ground-fire behavior are unchanged.
- Secondary: Dragon's Pyre, a 40-block propagating magma/fire runway. Standing on
  the pyre shortly after casting grants regeneration and speed. The visual front
  uses grounded `fire_pyre_fissure` seams and `fire_pyre_front` furnace frames;
  four weighted coal textures prevent obvious runway tiling.
- Ultimate: Maximum Meteor via `MeteorManager`. A surface point projected from
  the target X/Z is stored only for `fire_meteor_warning`; the irregular meteor
  core and `fire_meteor_impact` sequence do not change explosion behavior.
- Fire Spirit, Fire Spirit assets, ores/crystals, and unused `fire_wand.png` are
  excluded from the second-generation VFX pass.

### Wind

- Primary: two `VacuumBladeEntity` projectiles with side offsets. Their only new
  tracked state is a mirror boolean for opposing six-frame Sky Shear sprites;
  wakes are interpolated and collision VFX use the actual hit position.
- Secondary: Waylay Dash with 2 charges, passive recharge, and chain scaling.
  A short-lived visual tracer follows real player movement, with an outer lane
  on chained casts; charge and movement logic stay authoritative and unchanged.
  `WaylayDashVfxManager` expires each trace after five ticks and also clears it
  on death, leave, world change, server stop, or replacement by a newer dash.
- Ultimate: Zephyr Strike. It equips a temporary Elytra, launches the player, and
  creates a landing/impact explosion before restoring the old chest item. Its
  pearl vane wings, ascent streams, descent compression, shear feathers, and
  denser landing sequence are visual-only and retain vanilla Elytra geometry.
  Its post-impact visual burst self-removes after eight ticks and is cleared on
  server stop along with the existing active-strike state.
- Calamity Tornado, ores/crystals, and unused `wind_wand.png` are excluded from
  the second-generation VFX pass.

### Stone

- Primary: Tectonic Spikes, a terrain-following line of temporary stone spikes.
- Secondary: Stone Wall. Recasting near the active wall shatters it forward for
  damage and knockback.
- Ultimate: Titan Dome via `TitanDomeManager`.

### Nature

- Passive: Verdant Step places temporary lily pads over water near and ahead of
  the moving player.
- Primary: `SeedProjectileEntity`, which plants seedlings.
- Secondary: Tendril Bloom sends tendrils from active seedlings to nearby targets.
- Ultimate: Overgrowth via `OvergrowthManager`.

### Space

- Primary: `SingularityBoltEntity` launches a 0.9-block/tick black star with a
  24-block range. On its first tick it can acquire one combat target inside a
  16-block, 12-degree aim cone, then steer by at most 1 degree per tick and 16
  degrees total. It never retargets or U-turns, permanently drops guidance when
  the target is obstructed, behind, invalid, or outside the 30-degree leash, and
  ignores passive mobs, teammates, spectators, and caster-owned pets for aim
  assist. Impact deals 7 direct damage plus 2.5 splash damage within 3 blocks;
  it applies no pull, teleport, knockback, sprint lock, or mobility disruption.
  `space_expansion_ring` reuses the implosion-ring frames in reverse for the
  outward damage wave; range-expiry misses retain the inward implosion.
- Secondary: Blink Rift. The first cast blinks to a safe destination and leaves a
  rift; a later cast can swap back if the rift is usable.
- Ultimate: Hollow Purple charge sequence via `HollowPurpleChargeManager`.

## Manager Singletons

Complex timed or world-state effects live in `util/` managers and are usually
registered from `ElementalWandsMod.onInitialize()`.

| Manager | Used by |
| --- | --- |
| `TemporaryBlockManager` | temporary blocks for stone, fire, and nature effects |
| `EntangleTracker` | nature entangle/root stacks and cooldown slowing |
| `OvergrowthManager` | Nature ultimate |
| `SeedlingManager` | Nature primary seedlings |
| `TendrilBloomManager` | Nature secondary tendrils and blooms |
| `MeteorManager` | Fire ultimate |
| `TitanDomeManager` | Stone ultimate |
| `WaylayDashVfxManager` | five-tick visual-only Wind dash tracer |
| `BlazeTrailManager` | retained fire manager |
| `MovementDisruptManager` | movement disruption effects |
| `BlinkRiftManager` | Space secondary rift tracking |
| `EventHorizonManager` | retained space manager |
| `HollowPurpleChargeManager` | Space ultimate charge visuals and release |

## Networking

`ModNetworking` currently registers:

- `CastUltimatePayload` (C2S)
- `SyncPlayerDataPayload` (S2C)

`SyncPlayerDataPayload` carries:

- unlocked skill bitmask
- current affinity string

Call `ModNetworking.syncPlayerData(player)` after any server-side change to
affinity or unlocked skills so the HUD padlocks and theme update correctly.

`CastPrimaryPayload` and `handleCastPrimary(...)` still exist in the source but
are not part of the current client input flow.

## GeckoLib Entity Notes

The Stone Zombie and Fire Spirit are the reference custom GeckoLib mobs.

Useful files:

- `entity/StoneZombieEntity.java`
- `entity/FireSpiritEntity.java`
- `client/model/StoneZombieModel.java`
- `client/model/FireSpiritModel.java`
- `client/renderer/StoneZombieRenderer.java`
- `client/renderer/FireSpiritRenderer.java`

GeckoLib 5.3-alpha-3 resource paths:

- Models: `assets/<namespace>/geckolib/models/<name>.geo.json`
- Animations: `assets/<namespace>/geckolib/animations/<name>.animation.json`

Geo JSON must use `format_version: 1.12.0` with a `minecraft:geometry` array.
The `texture_width` and `texture_height` values must match the PNG dimensions.

Minecraft 1.21.10 notes:

- Spawn eggs use `settings.spawnEgg(entityType)`.
- Items need both `assets/<namespace>/models/item/<name>.json` and
  `assets/<namespace>/items/<name>.json`.

## Adding Or Changing Abilities

For an existing element:

1. Edit the matching `*AbilityHandler`.
2. Keep server-only logic on the server side.
3. Use `AbstractWandItem.tryStartCooldown(...)` for primary/secondary cooldowns.
4. Use `AbstractWandItem.trySpendUltimateCharge(...)` for ultimates.
5. Call `AbstractWandItem.onWandDamageDealt(...)` when wand damage lands.
6. Register any new ticking manager in `ElementalWandsMod.onInitialize()`.
7. Add entities to `ModEntities` and client renderers in `ElementalWandsClient`
   if the ability needs a new entity.
8. Run `./gradlew build`.

For a new element:

1. Add a new value to `WizardAffinity`.
2. Add a new ability handler in `item/`.
3. Dispatch it from `UniversalWandItem`.
4. Add HUD cooldown/theme handling in `WandHudOverlay`.
5. Add a clickable book button and command branch in `ElementalWandsMod`.
6. Add lang/assets/entities/managers as needed.
7. Run `./gradlew build`.

## Current Cleanup Candidates

- `CLAUDE.md`, `docs/MOD_HANDOFF.md`, and `docs/PLAYER_GUIDE.txt` still contain
  older crystal/Ice-era information.
- `CastPrimaryPayload` remains as dead or future-facing networking code.
- Some removed content assets remain in `src/main/resources/assets/elementalwands/textures`
  even though the related registered items/blocks no longer exist.
