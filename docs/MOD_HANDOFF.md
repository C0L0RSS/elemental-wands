# Elemental Wands (Fabric) - Gameplay + Technical Handoff

This document is meant to be handed to a developer who is helping build the mod. It describes:

- How the mod works at a system level (cooldowns, networking, HUD, tick managers).
- How each wand/ability works (gameplay behavior + the exact code paths/state involved).
- Where to edit things (class/file pointers, constants, NBT keys).

Mod details (from `src/main/resources/fabric.mod.json` + Gradle):

- Mod ID: `elementalwands`
- Version: `2.1.0` (see `gradle.properties`)
- Loader/API: Fabric Loader + Fabric API
- Minecraft target: `1.21.10`
- Java: `21`

## TL;DR Player Controls

The core control scheme is consistent across wands:

- Primary: Right click (no sneak)
- Secondary: Shift + right click (sneaking)
- Ultimate: `X` keybind (client sends a C2S packet)

Important technical note:

- Primary/Secondary are executed on the server via `Item#use()` (`AbstractWandItem.use`).
- Ultimate is executed on the server via `CastUltimatePayload` (`ElementalWandsClient` -> `ModNetworking`).

## How Players Obtain the Items (Current State)

There are no recipes or loot tables in this repo (no `src/main/resources/data/.../recipes`).

- Items are added to the Creative `Tools` tab via `ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)` in
  `src/main/java/com/anton/elementalwands/registry/ModItems.java`.
- They can also be spawned via commands:
  - `/give @p elementalwands:fire_wand`
  - `/give @p elementalwands:wind_wand`
  - `/give @p elementalwands:stone_wand`
  - `/give @p elementalwands:ice_wand`
  - `/give @p elementalwands:space_wand`
  - `/give @p elementalwands:titan_sword`

## Project Layout (Where Things Live)

- Entrypoints
  - `src/main/java/com/anton/elementalwands/ElementalWandsMod.java` (server/common init)
  - `src/main/java/com/anton/elementalwands/ElementalWandsClient.java` (client init: keybind, renderers, HUD)
- Registries
  - `src/main/java/com/anton/elementalwands/registry/ModItems.java`
  - `src/main/java/com/anton/elementalwands/registry/ModEntities.java`
- Wands/items
  - `src/main/java/com/anton/elementalwands/item/AbstractWandItem.java`
  - `src/main/java/com/anton/elementalwands/item/FireWandItem.java`
  - `src/main/java/com/anton/elementalwands/item/WindWandItem.java`
  - `src/main/java/com/anton/elementalwands/item/StoneWandItem.java`
  - `src/main/java/com/anton/elementalwands/item/IceWandItem.java`
  - `src/main/java/com/anton/elementalwands/item/SpaceWandItem.java`
- Projectile / spell entities
  - `src/main/java/com/anton/elementalwands/entity/InfernoWaveEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/VacuumBladeEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/CalamityTornadoEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/ChillSnowballEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/SingularityBoltEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/HollowPurpleOrbEntity.java`
  - `src/main/java/com/anton/elementalwands/entity/BoulderProjectileEntity.java` (currently not used by wands)
- Tick-driven managers (longer lived effects / zones)
  - `src/main/java/com/anton/elementalwands/util/*Manager.java`
- Client-only visuals
  - `src/main/java/com/anton/elementalwands/client/overlay/WandHudOverlay.java`
  - `src/main/java/com/anton/elementalwands/client/renderer/EmptyEntityRenderer.java`
- Networking
  - `src/main/java/com/anton/elementalwands/network/ModNetworking.java`
- Assets (textures/models/lang)
  - `src/main/resources/assets/elementalwands/lang/en_us.json`
  - `src/main/resources/assets/elementalwands/textures/item/*.png`
  - `src/main/resources/assets/elementalwands/textures/gui/wand_hud.png`
  - `src/main/resources/assets/elementalwands/items/*.json` (1.21 item model indirection)
  - `src/main/resources/assets/elementalwands/models/item/*.json` (handheld model)

## Core System: Wand Casting + Cooldowns

All wands extend `AbstractWandItem` and implement:

- `castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack)`
- `castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack)`
- `castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack)`

### Right-click entrypoint (Primary/Secondary)

`AbstractWandItem.use(World world, PlayerEntity user, Hand hand)`:

- Ignores client-side call (`world.isClient()` -> `SUCCESS`).
- Only executes on server (`ServerWorld`).
- If player is sneaking -> calls `castSecondary`.
- Otherwise -> calls `castPrimary`.

### Ultimate entrypoint (keybind -> network -> server)

Client keybind is registered in `ElementalWandsClient`:

- Default key: `X`
- Only sends while:
  - player exists
  - no screen open
  - main-hand item is an `AbstractWandItem`

Packet path:

- `ElementalWandsClient.tickClient()` -> `ClientPlayNetworking.send(ModNetworking.CastUltimatePayload.INSTANCE)`
- `ModNetworking.registerC2SReceivers()` -> server calls `handleCastUltimate(ServerPlayerEntity)`
- `handleCastUltimate`:
  - spectator check
  - requires `ServerWorld`
  - requires main-hand item is `AbstractWandItem`
  - calls `wand.castUltimate(world, player, stack)`

### Cooldown storage (ItemStack NBT in CUSTOM_DATA)

Cooldowns are tracked per *item stack* (not per player global state) using `DataComponentTypes.CUSTOM_DATA`.

In `AbstractWandItem`:

- Global cooldown: `GLOBAL_COOLDOWN_TICKS = 6` (0.30s)
- Default ability cooldowns (unless overridden):
  - Primary: `20` ticks (1.0s)
  - Secondary: `120` ticks (6.0s)
  - Ultimate: `800` ticks (40.0s)

NBT keys (inside `CUSTOM_DATA`):

- `ew_last_global`
- `ew_last_primary`
- `ew_last_secondary`
- `ew_last_ultimate`

Cooldown check + start:

- `tryStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack, Ability ability, int abilityCooldownTicks)`
  - Uses `world.getTime()` (world time ticks) as the clock.
  - Enforces global cooldown first (if remaining > 0 -> actionbar message and returns false).
  - Enforces ability cooldown next (if remaining > 0 -> actionbar message and returns false).
  - On success: writes both the ability timestamp and global timestamp to `CUSTOM_DATA`.

Actionbar messages:

- Uses `player.sendMessage(Text.literal(String.format("%s cooldown: %.1fs", label, seconds)), true)`
- `label` is one of `GLOBAL`, `PRIMARY`, `SECONDARY`, `ULTIMATE`

### Frost/Chill interaction: cooldowns tick 2x slower

`AbstractWandItem.tryStartCooldown` has a debuff interaction:

- If the caster has any Frost stacks (`ChillTracker.getStacks(player) > 0`), cooldown elapsed time is halved:
  - `elapsed /= 2`
  - Result: remaining time is effectively doubled

This means being chilled does not just slow movement; it also slows wand cooldown recovery.

## Core Utility: Raycasts (Blocks + Entities) and Beams

Several abilities need a "smart" raycast that can hit entities as well as blocks (vanilla `PlayerEntity#raycast` is block-only).

- `AbstractWandItem.raycast(...)` delegates to `WandUtils.raycast(...)`.
- `src/main/java/com/anton/elementalwands/util/WandUtils.java`:
  - Block raycast:
    - Uses a `RaycastContext` from `caster.getEyePos()` to `eyePos + (direction * range)`.
  - Entity raycast:
    - Builds a stretched/expanded search box along the ray.
    - Iterates `world.getOtherEntities(...)` and raycasts against each entity's bounding box.
  - Chooses the closer of the entity hit vs block hit (or returns block MISS if nothing is hit).

Default range used by most "targeted" abilities:

- `AbstractWandItem.DEFAULT_RANGE = 25.0` blocks

There is also a shared particle "beam" helper:

- `AbstractWandItem.spawnParticleLine(...)` -> `WandUtils.spawnBeam(...)`
  - Spawns one particle approximately every 0.35 blocks along a line.

## Core System: Frost (Chill) Stacks

Frost stacks are implemented in `src/main/java/com/anton/elementalwands/util/ChillTracker.java`.

State model:

- Stored server-side per world key: `Map<RegistryKey<World>, Map<UUID, ChillData>>`
- `ChillData`:
  - `stacks` (1 to 6)
  - `lastHitTick` (server ticks)
- Stacks decay by removal if no hit for `CLEAR_DELAY = 100` ticks (5 seconds)

Applying stacks:

- `ChillTracker.addStack(ServerWorld world, LivingEntity target)`
  - If last hit within 100 ticks -> increments stacks, otherwise resets to 1
  - Caps at 6
  - Immediately applies effects

Effects:

- 1-5 stacks:
  - Slowness for 60 ticks (3s)
  - Amplifier is `stacks - 1` (stack 1 -> Slowness I, stack 2 -> Slowness II, etc.)
  - Frozen ticks increased by +40, capped at 300
- 6 stacks ("Deep Frozen"):
  - Slowness amplifier 255 for 30 ticks (~1.5s) which effectively immobilizes movement
  - Frozen ticks set into damage range: `target.setFrozenTicks(target.getMinFreezeDamageTicks() + 100)`

Clearing stacks:

- `ChillTracker.clearFrostStacks(ServerWorld world, LivingEntity entity)` removes the entry

Where stacks are applied:

- `ChillSnowballEntity` (on entity hit)
- `IceWandItem` shatter AoE
- `BlizzardManager` phase 2 (every 20 ticks)

## Core System: Temporary Block Placement

Temporary block placement is the backbone of "spikes", "ice cages", "fire trails", etc.

`src/main/java/com/anton/elementalwands/util/TemporaryBlockManager.java`:

- `placeTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions, BlockState placedState, int durationTicks, Predicate<BlockState> canReplace)`
  - Saves original block states at each position into a `Long2ObjectMap<BlockState>`
  - Sets the block to `placedState`
  - Schedules restoration at `expiryTick = now + durationTicks` (server ticks)

Restoration logic:

- Restores a position only if the current block is still the placed block type:
  - `if (current.isOf(blocks.placedState.getBlock())) restore`
- If players replace the block with something else, the manager will not overwrite that.

Used heavily by:

- Fire trails (fire blocks)
- Stone spikes + Titan Dome + Aegis
- Ice spikes + Blizzard cages
- Tornado "soft block destruction" (temporary air)

## Core System: Temporary Snow Fields (Blizzard)

`src/main/java/com/anton/elementalwands/util/TemporarySnowManager.java`:

- Places single-layer `Blocks.SNOW` around a center by scanning down up to 6 blocks to find ground.
- Each tick while active:
  - Applies Slowness I for 25 ticks to entities whose feet position is on one of the placed snow blocks.
- Removes the snow blocks at expiry.

## Core System: Movement Disruption

`src/main/java/com/anton/elementalwands/util/MovementDisruptManager.java` provides reusable "anti-mobility" effects used by Space spells.

Sprint lock:

- `applySprintLock(ServerWorld world, LivingEntity target, int durationTicks)`
  - Forces sprint off each tick (players)
  - Applies horizontal drag multiplier `0.82`
  - Strips Slow Falling
  - Removes Speed II-style surf (removes SPEED if amplifier >= 1)

Instant mobility disruption:

- `disruptMobility(LivingEntity target)`
  - Multiplies X/Z velocity by 0.55
  - Clamps Y
  - Also strips dash glide + speed surf

## Client: HUD Overlay (Cooldown UI)

`src/main/java/com/anton/elementalwands/client/overlay/WandHudOverlay.java` draws a 3-slot HUD above the hotbar when holding any wand.

Behavior:

- Hidden when debug HUD is visible (`F3`).
- Reads the item stack `CUSTOM_DATA` NBT timestamps (`ew_last_*`) and compares against `client.world.getTime()`.
- Applies the same chill slowdown rule as server cooldown logic: `elapsed /= 2` if the player has Frost stacks.
- Shows a numeric second countdown bubble if remaining > 20 ticks (1s).

Special-case behavior for Wind secondary (dash charges):

- If wand is `WindWandItem` and ability is SECONDARY:
  - Ignores standard cooldown timestamps
  - Uses `WindWandItem.getDashCharges(stack)` and `getDashRechargeTicks(stack)`
  - Draws charge pips and a pulsing "recharging pip" for the next charge

Theme:

- Theme is derived by wand class (fire/ice/wind/stone/space/arcane fallback)
- Accent RGB:
  - Fire: `0xE0842C`
  - Ice: `0x8EDCF8`
  - Wind: `0xCFEBAE`
  - Stone: `0xC6B79A`
  - Space: `0xB29DFF`
  - Arcane: `0xD9D2AF`

## Entities + Rendering Strategy

Entities are registered in `ModEntities` using `FabricEntityTypeBuilder` with configured dimensions and tracking.

Client renderer registration in `ElementalWandsClient`:

- Uses `FlyingItemEntityRenderer` for:
  - `BOULDER_PROJECTILE`
  - `CHILL_SNOWBALL`
- Uses `EmptyEntityRenderer` for particle-only visuals:
  - `VACUUM_BLADE`
  - `CALAMITY_TORNADO`
  - `INFERNO_WAVE`
  - `SINGULARITY_BOLT`
  - `HOLLOW_PURPLE_ORB`

This means most spell visuals are done via server-spawned particles, not custom models.

### Entity Registry Reference (IDs, Dimensions, Tracking)

Values from `src/main/java/com/anton/elementalwands/registry/ModEntities.java` (all are `SpawnGroup.MISC`).

| Entity ID | Class | Dimensions (W x H) | Track range | Update rate | Client renderer |
|---|---|---:|---:|---:|---|
| `elementalwands:boulder_projectile` | `BoulderProjectileEntity` | 0.50 x 0.50 | 64 | 10 | `FlyingItemEntityRenderer` |
| `elementalwands:chill_snowball` | `ChillSnowballEntity` | 0.25 x 0.25 | 64 | 10 | `FlyingItemEntityRenderer` |
| `elementalwands:inferno_wave` | `InfernoWaveEntity` | 3.00 x 2.00 | 64 | 1 | `EmptyEntityRenderer` |
| `elementalwands:vacuum_blade` | `VacuumBladeEntity` | 0.50 x 0.50 | 64 | 1 | `EmptyEntityRenderer` |
| `elementalwands:calamity_tornado` | `CalamityTornadoEntity` | 6.00 x 12.00 | 128 | 1 | `EmptyEntityRenderer` |
| `elementalwands:singularity_bolt` | `SingularityBoltEntity` | 0.35 x 0.35 | 64 | 1 | `EmptyEntityRenderer` |
| `elementalwands:hollow_purple_orb` | `HollowPurpleOrbEntity` | 6.00 x 6.00 | 128 | 1 | `EmptyEntityRenderer` |

## Wands: Full Behavior Spec

All numbers below are taken directly from source constants as of `2.1.0`.

### Shared Defaults (unless overridden)

- Global cooldown: 6 ticks (0.30s) after a successful cast (only for abilities that call `tryStartCooldown`)
- Primary cooldown: 20 ticks (1.0s)
- Secondary cooldown: 120 ticks (6.0s)
- Ultimate cooldown: 800 ticks (40.0s)

Also: If the caster has any Frost stacks, cooldowns recover at half speed (effectively double cooldown).

### Balance Cheat Sheet (Key Numbers)

This section is a fast "at a glance" summary. Full technical details are in each wand section.

Cooldowns (ticks):

| Wand | Primary | Secondary | Ultimate | Notes |
|---|---:|---:|---:|---|
| Fire | 20 | 120 | 800 | uses standard cooldown system |
| Wind | 20 | charge-based | 800 | secondary uses charges (no `tryStartCooldown`) |
| Stone | 60 | 120 | 800 | primary overrides to 60 |
| Ice | 20 | 120 | 800 | uses standard cooldown system |
| Space | 20 | 120 | 800 | ultimate starts charge -> orb |
| Global | 6 | - | - | applied on successful `tryStartCooldown` casts |

Damage and durations (selected highlights):

- Fire primary (Inferno Wave): 6 damage, 15-block range, 2s fire trail (40 ticks), 3s ignite.
- Fire secondary (Magma Surf): Speed II for 3s (60 ticks), leaves 2s fire blocks behind you.
- Fire ultimate (Meteor): spawn height +35, explosion power 15, warns players within 60 blocks.
- Wind primary (Vacuum Blades): 2 blades, 5 damage each, 20-block range, knockback on hit.
- Wind secondary (Dash): 3 charges, 80 ticks per recharge, 30-tick chain window, strength 2.0 (+0.5 per chain).
- Wind ultimate (Tornado): 10s lifetime, radius 6 soft-block destruction (hardness <= 0.5), constant lift.
- Stone primary (Tectonic Spikes): 15 length, 6 damage, +0.5Y knock-up, 2s stone blocks (40 ticks).
- Stone secondary (Aegis): 4s moving 3x3 glass wall refreshed every tick.
- Stone ultimate (Titan Dome): 12s radius-16 shell, repair every 10 ticks, pullback speed 1.5, Resistance II.
- Ice primary (Volley): 3 projectiles, base damage 2, shatter damage 4, shatter triggers when pre-hit stacks >= 3.
- Ice secondary (Spikes): 10 length, 5 damage, +1.0Y knock-up, 3s packed ice (60 ticks).
- Ice ultimate (Blizzard): radius 25, 6s storm + 4s packed-ice cage finale.
- Space primary (Singularity): 4 direct damage, radius-3 pull + sprint lock (15 ticks).
- Space secondary (Blink Rift): 10-block blink, rift lasts 120 ticks, swap-back is cooldown-free.
- Space ultimate (Hollow Purple): 3.5s charge, orb radius 3, speed 1.3, 40 damage once per entity, erases terrain.

---

## Fire Wand (`FireWandItem`)

Files:

- `src/main/java/com/anton/elementalwands/item/FireWandItem.java`
- `src/main/java/com/anton/elementalwands/entity/InfernoWaveEntity.java`
- `src/main/java/com/anton/elementalwands/util/MeteorManager.java`

Passive (while held):

- Grants Fire Resistance continuously while in main hand or offhand:
  - `inventoryTick`: applies `FIRE_RESISTANCE` for 2 ticks every tick (no particles).

Primary: Inferno Wave

- Input: Right click
- Cooldown: Primary default (20 ticks) + global (6 ticks)
- Implementation:
  - `FireWandItem.castPrimary` -> `tryStartCooldown` -> spawns `InfernoWaveEntity`
- Projectile behavior (`InfernoWaveEntity`):
  - Speed: 1.5 blocks/tick
  - Max travel distance: 15 blocks
  - Damage: 6.0
  - Piercing: Yes (damages each entity at most once; tracked by entity id set)
  - On-hit:
    - Damage source: thrown projectile (owner is caster)
    - Sets target on fire for 3 seconds
  - Terrain interaction:
    - Leaves a 1-block fire trail if current position is air and block below is solid:
      - Uses `TemporaryBlockManager.placeTemporaryBlocks(... Blocks.FIRE ..., 40 ticks)`
  - Block collision:
    - Stops and despawns on first block hit
- Audio/visual:
  - Cast sounds: `ITEM_FIRECHARGE_USE`, `ENTITY_BLAZE_SHOOT`
  - Particles per tick: `FLAME`, `SMALL_FLAME`

Secondary: Magma Surf

- Input: Shift + right click
- Cooldown: Secondary default (120 ticks) + global (6 ticks)
- Buffs:
  - Speed II for 60 ticks (3 seconds)
  - Fire Resistance for 60 ticks
- Fire trail behavior:
  - Implemented in `FireWandItem.inventoryTick`, not in the cast itself.
  - While the player has SPEED with amplifier == 1 (Speed II), it:
    - Places fire at the player's feet (if air, and ground below is solid), for 40 ticks.
    - Spawns flame/lava particles around the player each tick.
- Edge case:
  - The fire trail is keyed only on "player is holding fire wand" AND "player currently has Speed II".
  - Any Speed II source (not just Magma Surf) will create a fire trail while holding the wand.

Ultimate: Maximum Meteor

- Input: Ultimate key (`X`)
- Cooldown: Ultimate default (800 ticks) + global (6 ticks)
- Targeting:
  - Raycast (blocks or entities) using `WandUtils.raycast` at range 25
  - Target position is `HitResult.getPos()`
- Implementation:
  - `FireWandItem.castUltimate` -> `MeteorManager.spawnMeteor(world, caster, target, 35, 15.0f)`
- Meteor behavior (`MeteorManager`):
  - Spawns a `FallingBlockEntity` at target X/Z and height +35
  - Falling block state: random obsidian or magma block
  - Initial velocity: Y = -0.2 (gravity still applies)
  - Tracks meteor by entity id; tick-managed for up to 240 ticks
  - While falling: spawns flame/smoke particles
  - On landing (or if entity disappears): triggers an explosion:
    - `world.createExplosion(caster, x, y, z, power=15.0, createFire=true, sourceType=MOB)`
  - Sends an actionbar warning to players within 60 blocks (message contains warning symbols and uses red/bold formatting)
  - Audio: wither spawn + ghast shoot at spawn; explosion sound on impact

---

## Wind Wand (`WindWandItem`)

Files:

- `src/main/java/com/anton/elementalwands/item/WindWandItem.java`
- `src/main/java/com/anton/elementalwands/entity/VacuumBladeEntity.java`
- `src/main/java/com/anton/elementalwands/entity/CalamityTornadoEntity.java`

Primary: Vacuum Blades (dual projectiles)

- Input: Right click
- Cooldown: Primary default (20 ticks) + global (6 ticks)
- Implementation:
  - `WindWandItem.castPrimary`:
    - Computes forward rotation vector
    - Computes a perpendicular "right" vector (horizontal)
    - Spawns two `VacuumBladeEntity` with lateral offsets of +/- 0.5
- Projectile behavior (`VacuumBladeEntity`):
  - Speed: 2.5 blocks/tick
  - Max travel distance: 20 blocks
  - Damage: 5.0
  - On-hit:
    - Damage source: thrown projectile
    - Adds knockback along projectile direction:
      - horizontal 0.5, vertical 0.2
    - Despawns on first entity hit (no piercing)
  - Block collision: despawns on first block hit
  - Particles: cloud + gust

Secondary: Waylay Dash (charge-based mobility)

- Input: Shift + right click
- IMPORTANT TECHNICAL NOTE:
  - This ability does NOT call `tryStartCooldown`.
  - It bypasses:
    - Global cooldown enforcement
    - Secondary cooldown timestamps (`ew_last_secondary`)
    - Chill slowdown on cooldown recovery (because it is not a cooldown, it is charges)
- Charge system (stored on the item stack):
  - Max charges: 3
  - Recharge time per charge: 80 ticks (4 seconds)
  - NBT keys in `CUSTOM_DATA`:
    - `DashCharges`
    - `RechargeTicks`
    - `LastDashTick` (server tick count)
    - `ChainCount`
  - Recharge is handled in `WindWandItem.inventoryTick`:
    - Only happens while the wand is equipped (main-hand/offhand tick)
    - Increments `RechargeTicks` and awards a charge when it reaches 80
- Dash execution (`castSecondary`):
  - If charges == 0: returns with no action
  - Chain mechanic:
    - If dash is used within 30 ticks (1.5s) of the last dash, chainCount++
    - Otherwise chainCount = 0
  - Strength scaling:
    - `dashStrength = 2.0 + (chainCount * 0.5)`
    - chain 0 -> 2.0
    - chain 1 -> 2.5
    - chain 2 -> 3.0
  - Velocity:
    - Applies velocity in look direction
    - Vertical component is reduced (`look.y * dashStrength * 0.5`)
  - Safety:
    - Sets `caster.setOnGround(false)` and `caster.fallDistance = 0`
    - Adds Slow Falling for 40 ticks
    - Sends `EntityVelocityUpdateS2CPacket` to force client sync
  - UI:
    - Sends an actionbar message using Minecraft color codes (section-sign formatting).

Ultimate: Calamity Tornado

- Input: Ultimate key (`X`)
- Cooldown: Ultimate default (800 ticks) + global (6 ticks)
- Implementation:
  - `WindWandItem.castUltimate` spawns `CalamityTornadoEntity`
- Tornado behavior (`CalamityTornadoEntity`):
  - Lifetime: 200 ticks (10 seconds)
  - Horizontal movement: 0.2 blocks/tick in the owner's facing direction at cast time
  - Terrain interaction:
    - Destroys "soft" blocks (hardness <= 0.5) within radius 6
    - Implemented as placing temporary air for the remaining tornado lifetime using `TemporaryBlockManager`
  - Entity interaction:
    - Lifts living entities in a helix (spiral) inside the destruction radius
    - Owner is ignored for the first 60 ticks (3 seconds) as a grace period
  - Particles: dense cloud + gust
  - Sounds: breeze wind burst periodically

---

## Stone Wand (`StoneWandItem`)

Files:

- `src/main/java/com/anton/elementalwands/item/StoneWandItem.java`
- `src/main/java/com/anton/elementalwands/util/TitanDomeManager.java`
- `src/main/java/com/anton/elementalwands/util/TemporaryBlockManager.java`

Primary: Tectonic Spikes

- Input: Right click
- Cooldown: 60 ticks (overrides default) + global (6 ticks)
- Terrain targeting:
  - Builds a 15-block path forward from the caster using a horizontal forward vector.
  - For each step, it finds a nearby "ground Y" by scanning within +/- 3 blocks around a reference Y.
  - A "ground candidate" is:
    - solid block
    - with air/replaceable above
    - no fluid in the above block
- Block placement:
  - Places `Blocks.STONE` at each step position for 40 ticks (2 seconds)
  - Replacement predicate: `(air or replaceable) && no fluid`
- Damage model:
  - For each spike position, creates a hitbox:
    - X/Z expanded by 0.7 blocks
    - Y expanded down by 5 blocks and up by 5 blocks
  - Any living entity inside any spike hitbox:
    - takes 6.0 damage (playerAttack damage source)
    - gets +0.5 Y velocity (knock-up)
  - Entities are deduped by UUID so you can't be hit multiple times by overlapping spike hitboxes in the same cast.

Secondary: Aegis (moving glass shield)

- Input: Shift + right click
- Cooldown: Secondary default (120 ticks) + global (6 ticks)
- Implementation:
  - `StoneWandItem.castSecondary` -> `TitanDomeManager.startAegis(world, caster)`
- Aegis behavior (`TitanDomeManager`):
  - Duration: 80 ticks (4 seconds)
  - Each world tick while active:
    - Builds a wall 4 blocks in front of the player in their horizontal facing direction.
    - Wall dimensions: 3 wide (lateral -1..+1) by 3 tall (y 0..2).
    - Places `Blocks.GLASS` with a duration of 1 tick (effectively re-placed each tick).
    - Replacement predicate: air/replaceable and no fluid.
  - The result is a "moving shield" that tracks the player every tick.

Ultimate: Titan Dome (domain / juggernaut form)

- Input: Ultimate key (`X`)
- Cooldown: Ultimate default (800 ticks) + global (6 ticks)
- Implementation:
  - `StoneWandItem.castUltimate` -> `TitanDomeManager.startDome(world, caster)`
- Dome behavior (`TitanDomeManager`):
  - Duration: 240 ticks (12 seconds)
  - Geometry:
    - Places a spherical shell of `Blocks.POLISHED_DEEPSLATE` around the caster's block position.
    - Radius: 16
    - Inner radius: 15 (so it is a shell, not a filled ball)
    - Only replaces blocks that are:
      - air, or
      - replaceable (tall grass etc.),
      - and not fluid-filled
    - It will not carve through solid terrain.
  - Repair:
    - Every 10 ticks, for all dome shell positions:
      - if the block is currently air, it restores it to polished deepslate
    - If a player replaces the dome block with another block type, the repair does not overwrite it.
  - "Inescapable Domain":
    - Tracks living entities that were ever inside the dome radius.
    - If a tracked entity is outside the radius, it is pulled back:
      - sets velocity toward center at magnitude 1.5 (DOMAIN_PULL_SPEED)
      - resets fall distance
  - Caster buffs:
    - Resistance II (amplifier 1), refreshed every 20 ticks (duration 40 ticks)
    - +1.0 knockback resistance via attribute modifier (temporary modifier)
  - Juggernaut loadout:
    - Equips full netherite armor pieces (standard items) but marks them in `CUSTOM_DATA`:
      - `ew_titan_gear=true`
      - `ew_titan_armor=true`
    - Replaces main hand with a Titan Sword stack, marked:
      - `ew_titan_gear=true`
      - `ew_titan_sword=true`
      - plus an embedded copy of the original armor NBT for recovery/debug
    - Stores the caster's original armor + main-hand in the Dome state so it can restore on expiry.
  - Cleanup:
    - On dome end:
      - restores blocks to original states (only if still polished deepslate)
      - removes knockback resistance modifier
      - restores original armor + main-hand
      - strips any marked Titan gear from inventory/equipment
    - Every 20 ticks, it also cleans up stale modifiers/gear from players who are not active dome casters.

---

## Ice Wand (`IceWandItem`)

Files:

- `src/main/java/com/anton/elementalwands/item/IceWandItem.java`
- `src/main/java/com/anton/elementalwands/entity/ChillSnowballEntity.java`
- `src/main/java/com/anton/elementalwands/util/ChillTracker.java`
- `src/main/java/com/anton/elementalwands/util/BlizzardManager.java`
- `src/main/java/com/anton/elementalwands/util/TemporarySnowManager.java`

Primary: Frost-Bite Volley (3-shot spread + Shatter)

- Input: Right click
- Cooldown: Primary default (20 ticks) + global (6 ticks)
- Fires 3 snowballs with spread:
  - Speed: 1.5
  - Divergence: 4.0 (wide spread)
- Each projectile is a custom subclass `ChillSnowballEntityWithShatter` that overrides `onEntityHit`.

Hit behavior:

- On entity hit:
  - Looks up current Frost stacks BEFORE applying the new stack.
  - Applies 1 Frost stack via `ChillTracker.addStack`.
  - If the target already had >= 3 stacks before this hit:
    - triggers Shatter
  - Otherwise:
    - deals base damage 2.0 (thrown damage source)
  - Always spawns snowflake particles and discards the projectile (it does not pierce).

Shatter behavior:

- Direct damage: 4.0 (thrown damage source)
- Clears Frost stacks on the target.
- AoE burst:
  - Radius: 3 blocks
  - Damage: 2.0 (magic damage)
  - Applies +1 Frost stack to each nearby living entity
- Particles: heavy snowflake burst + small explosions
- Sound: glass break

Block-hit behavior:

- Inherited from `ChillSnowballEntity`:
  - Places a 1-layer snow block on top of the hit block if possible.
  - Spawns snowflake particles.

Secondary: Permafrost Spikes (temporary packed ice eruptions)

- Input: Shift + right click
- Cooldown: Secondary default (120 ticks) + global (6 ticks)
- Spawns a line of spike positions 10 blocks forward:
  - For each step:
    - checks if the block below is solid
    - adds spike blocks at position and position+1 (2 blocks tall)
    - checks for entities in a hitbox around that position:
      - deals 5.0 magic damage
      - sets Y velocity to 1.0 (knock-up)
    - spawns snowflake particles at each step
- Places packed ice blocks temporarily:
  - Duration: 60 ticks (3 seconds)
  - Replacement predicate: current block is air
- Sounds: glass break + glass place

Ultimate: Blizzard (multi-phase zone + finale cage)

- Input: Ultimate key (`X`)
- Cooldown: Ultimate default (800 ticks) + global (6 ticks)
- Targeting:
  - Raycast range 25
  - If miss: center = caster position
  - Otherwise: center = hit position
- Implementation: `BlizzardManager.startBlizzard(world, caster, center)`

Blizzard details (`BlizzardManager`):

- Radius: 25 blocks
- Phase 1 (Build-up): 60 ticks (3 seconds)
  - Applies Slowness I continuously to targets in radius (excludes caster)
  - Light snowflake particles
  - Wind-burst sound every 40 ticks
- Phase 2 (Storm): 60 ticks (3 seconds)
  - Applies Blindness (whiteout) continuously
  - Deals freeze damage:
    - every 10 ticks, damage = 1.0 (2.0 damage/second)
  - Applies Frost stacks:
    - every 20 ticks, `ChillTracker.addStack`
  - Aggressive freezing:
    - adds frozen ticks (+5 each tick) up to damage threshold
  - Heavy snow particles
  - Freeze-hurt sound every 30 ticks
- Phase 3 (Finale): triggered once when phase 2 ends
  - Creates a 3x3x3 packed ice cage around each living entity still in radius
    - Duration: 80 ticks (4 seconds)
    - Center block is skipped so the entity's space remains open
    - Uses `TemporaryBlockManager` and the "air or replaceable" predicate
  - Applies Slowness amplifier 255 for the cage duration
  - Big finale particle burst + glass break sound

Extra: Snow field floor (`TemporarySnowManager`)

- Blizzard also creates a snow field at the start.
- The snow field lasts longer than the phases:
  - duration is `TOTAL_DURATION (120)` + `CAGE_DURATION (80)` + 60 more ticks
  - total ~260 ticks (13 seconds)
- Entities standing on the placed snow get Slowness I.

---

## Space Wand (`SpaceWandItem`)

Files:

- `src/main/java/com/anton/elementalwands/item/SpaceWandItem.java`
- `src/main/java/com/anton/elementalwands/entity/SingularityBoltEntity.java`
- `src/main/java/com/anton/elementalwands/util/BlinkRiftManager.java`
- `src/main/java/com/anton/elementalwands/util/HollowPurpleChargeManager.java`
- `src/main/java/com/anton/elementalwands/entity/HollowPurpleOrbEntity.java`

Global rule: spell lockout while charging ultimate

- Space wand checks `HollowPurpleChargeManager.isCharging(world, caster)`:
  - Primary does nothing if charging
  - Secondary does nothing if charging
  - Ultimate refuses to start if already charging

Primary: Singularity Bolt (short-range gravity impact)

- Input: Right click
- Cooldown: Primary default (20 ticks) + global (6 ticks)
- Spawns `SingularityBoltEntity`

Projectile behavior (`SingularityBoltEntity`):

- Speed: 0.9 blocks/tick
- Max travel distance: 24 blocks
- Direct hit damage: 4.0 (thrown damage source)
- Impact AoE:
  - Radius: 3 blocks
  - For each living entity in radius (excluding caster):
    - pulls them toward impact (prefers teleport step up to 2.5 blocks if space is empty; otherwise uses velocity)
    - applies a sprint lock for 15 ticks
    - applies additional mobility disruption (drag, removes Slow Falling, suppresses Speed II surf)
- Particles: portal + witch in flight and on hit
- Sound: enderman teleport on impact

Secondary: Blink Rift (blink forward, then optionally swap back)

- Input: Shift + right click
- Cooldown: Secondary default (120 ticks) + global (6 ticks), BUT read the swap path below.
- Two-step behavior:

Step A: Swap with existing rift (if present)

- The code always tries swap first:
  - `BlinkRiftManager.trySwapWithRift(world, caster)`
- If swap is successful:
  - returns immediately
  - does NOT check cooldown
  - does NOT start global cooldown
- If blocked:
  - sends an actionbar message: "Rift swap blocked."
  - returns

This makes swap-back effectively "free" and usable even while the blink is on cooldown, which is intentional for the feel of a rift tether.

Step B: Blink forward (if no rift exists)

- Finds a safe destination within range 10:
  - Raycasts for a blocking block
  - Chooses the farthest safe point in steps of 0.5 blocks from max distance down to 1.0
  - Checks both the feet position and a 1-block-raised feet position
  - Safety check uses:
    - world height bounds
    - `world.isSpaceEmpty` with the player's bounding box offset
    - finite coordinates
- If no safe location:
  - sends actionbar "No safe blink destination."
  - returns
- If a location is found:
  - starts cooldown via `tryStartCooldown` (global + secondary)
  - teleports player with `requestTeleport`
  - spawns a rift at the origin position for 120 ticks

Rift behavior (`BlinkRiftManager`):

- Stores one rift per caster per dimension:
  - `Map<RegistryKey<World>, Map<UUID, Rift(position, expiryTick)>>`
- Each tick:
  - spawns portal + witch particles at the rift (every other tick)
- Swap safety:
  - swap checks for space empty (prevents suffocation)
- Note:
  - A swap does not remove the rift, so multiple swaps are possible until expiry.

Ultimate: "Hollow Purple" charge -> orb (current implementation)

- Input: Ultimate key (`X`)
- Cooldown: Ultimate default (800 ticks) + global (6 ticks)
- Implementation:
  - `SpaceWandItem.castUltimate` -> `HollowPurpleChargeManager.startCharge(world, caster)`

Charge phase (`HollowPurpleChargeManager`):

- Duration:
  - Ascent: 60 ticks (3 seconds)
  - Hold: 10 ticks (0.5 seconds)
  - Total: 70 ticks (3.5 seconds)
- Ascent height: +10 blocks (clamped to world min/max)
- Movement lock:
  - Applies short Levitation repeatedly
  - Sets velocity to (0, controlled Y, 0) each tick
  - Prevents sprinting and resets fall distance
- Visuals:
  - Spawns a red/blue dust spiral ("snake lines") around the caster up to an anchor point.
  - Anchor point is caster position +3Y.
- At completion:
  - Spawns `HollowPurpleOrbEntity` at the anchor point
  - Gives the caster Slow Falling for 80 ticks (4 seconds)

Orb phase (`HollowPurpleOrbEntity`):

- Speed: 1.3 blocks/tick
- Radius: 3.0 blocks (approximately a 6-block diameter sphere)
- Lifetime:
  - 65 ticks (~3.25 seconds), OR
  - 90 blocks traveled, whichever comes first
- Terrain interaction:
  - Each tick, deletes all breakable blocks inside the sphere:
    - skips air
    - skips unbreakable blocks (hardness < 0.0)
    - sets remaining blocks to air
  - This is destructive and will permanently remove terrain, by design.
- Entity interaction:
  - Deals 40.0 damage to each living entity at most once (tracked by UUID set)
  - Non-living entities (items, projectiles, etc.) are discarded if touched
- Collision:
  - onBlockHit is a no-op; the orb tunnels through blocks and uses AoE checks instead.
- Visuals:
  - Dense portal/witch shell particles; reverse portal core
  - Plays respawn anchor charge sound every 6 ticks

Note on naming:

- The README currently labels Space ultimate as "Event Horizon", but code currently starts the Hollow Purple charge/orb flow.

---

## Titan Sword (`ModItems.TITAN_SWORD`)

Registration (`ModItems`):

- Created as a vanilla `Item` configured as:
  - netherite sword material
  - attack damage bonus: +3.0f (in addition to netherite base)
  - attack speed: -2.4f
  - fireproof
  - maxCount 1

Used by:

- Creative tools tab (always available)
- Titan Dome ultimate equips it temporarily and marks it as Titan gear in `CUSTOM_DATA`.

## Systems That Exist in Code But Are Not Wired Up (As of 2.1.0)

These are useful for a contributor to know about because they affect future work and reduce confusion:

- `EventHorizonManager` (`src/main/java/com/anton/elementalwands/util/EventHorizonManager.java`)
  - Fully implemented gravity zone + implosion logic
  - Not called by any wand currently
- `CycloneManager` (`src/main/java/com/anton/elementalwands/util/CycloneManager.java`)
  - Deprecated and not called
  - Still initialized in `ElementalWandsMod.onInitialize`
- `BlazeTrailManager` (`src/main/java/com/anton/elementalwands/util/BlazeTrailManager.java`)
  - Initialized, but no wand calls `addTrail`
  - Fire trail is currently implemented directly in `FireWandItem.inventoryTick`
- `FrostZoneManager` (`src/main/java/com/anton/elementalwands/util/FrostZoneManager.java`)
  - Not initialized in `ElementalWandsMod`
  - Not referenced by any wand
- `ModNetworking.CastPrimaryPayload`
  - Registered on server, but client never sends it
  - Primary casting currently works via server-side `Item#use()`

## Contributor Quickstart (Dev Workflow)

- Build: `./gradlew build`
- Run dev client: `./gradlew runClient`

Where to tweak balance:

- Per-wand constants: each `*WandItem.java` file
- Projectile constants: entity classes in `src/main/java/com/anton/elementalwands/entity`
- Zone/ultimate behavior: util managers in `src/main/java/com/anton/elementalwands/util`

How to add a new wand (high level):

1. Add a new `Item` extending `AbstractWandItem` in `src/main/java/com/anton/elementalwands/item`
2. Register it in `src/main/java/com/anton/elementalwands/registry/ModItems.java`
3. Add assets:
   - `src/main/resources/assets/elementalwands/lang/en_us.json`
   - `src/main/resources/assets/elementalwands/textures/item/<new>.png`
   - `src/main/resources/assets/elementalwands/items/<new>.json`
   - `src/main/resources/assets/elementalwands/models/item/<new>.json`
4. If the wand spawns a new entity:
   - register entity type in `ModEntities`
   - register renderer in `ElementalWandsClient`
5. If you need additional keybinds beyond ultimate:
   - register keybind in `ElementalWandsClient`
   - add C2S payload in `ModNetworking` and server handler that calls the wand method
