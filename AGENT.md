# AGENT.md

This file gives coding agents the current working map for the Elemental Wands
repo. It is based on the older `CLAUDE.md`, but cleaned up for the current
Universal Wand / affinity architecture.

## Current session checkpoint — September 8, 2026

Read [docs/MOD_HANDOFF.md](docs/MOD_HANDOFF.md) before resuming Guardian work. It
records the user's latest feedback, installed build/backup, source files, preview,
and next playtest. The latest takeoff-cancellation fix is built and installed in
Lunar, **but has not been confirmed in-game by the user**. Start with a leap test
and `/ew guardian status` if it cancels; do not interpret the stopping point as
confirmation that the bug or overall fight balance is resolved. Preserve the
uncommitted Guardian and pre-existing Fire work.

## Build And Run

```bash
./gradlew build        # compile + produce jars in build/libs/
./gradlew runClient    # launch a dev client
```

After any code change, run `./gradlew build` to verify compilation and packaging.
The build also runs `checkGuardianBeam`, a small executable geometry/timing
regression check in `src/guardianTest/java/`; it uses Minecraft's real Box/Vec3d
math without launching a world. It is not a live gameplay test.

For VFX work, the required repository checks are:

```bash
./gradlew clean build
git diff --check
python3 tools/validate_remaining_vfx_assets.py
python3 tools/prepare_guardian_assets.py --check
python3 tools/prepare_guardian_throw_socket.py --check
unzip -t build/libs/elementalwands-2.2.0.jar
```

The validator covers JSON and texture references, RGBA/power-of-two rules,
material depth, unique animation frames, model references, the registered
Fire/Wind particle additions, approved Fire resource references, and
exact production counts. Asset generators
must only be run with `--replace` when intentionally replacing the families
owned by that generator; never use a Fire/Wind pass to regenerate Arcane.
The exact package contract is Fire 77, Wind 53, Stone 41, Nature 44, Space 81
(296 affinity PNGs), plus two shared gear/HUD PNGs and 40 particle definitions.
Fire uses custom Kinetic Inferno animation frames while retaining vanilla
netherrack for the Pyre base and vanilla magma for the meteor core.

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
- `tools/generate_fire_vfx_assets.py` - deterministic Kinetic Inferno Fire package
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
- Primary: `InfernoWaveEntity`, presented by `FireWaveRenderer` as a ten-frame
  velocity-aligned stream plus an expanding perpendicular front, with an
  interpolated animated ribbon/ember wake. Projectile speed, range, damage,
  piercing, block collision, burning, and temporary ground-fire behavior are
  unchanged.
- Secondary: Dragon's Pyre, a 40-block propagating magma/fire runway. Standing on
  the pyre shortly after casting grants regeneration and speed. Its internal
  magma-backed blocks render as vanilla netherrack, while custom animated flame
  blocks fan outward from each slice's center over four visual-only ticks behind
  an animated advancing front.
- Ultimate: Maximum Meteor via `MeteorManager`. A surface point projected from
  the target X/Z drives a rotating, closing animated warning ring; the irregular
  magma-skinned core gains a flowing shell and hero impact without changing
  explosion behavior.
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

### Fractured Guardian cooperative boss

Spawn with `/summon elementalwands:fractured_guardian ~ ~ ~10`. It remains MISC,
no natural spawning, persistent, with a 3.2 × 5.2 body hitbox. It automatically
awakens over 84 ticks when a visible Survival/Adventure player is within 24 blocks.
Creative/Spectator and teammates are excluded; automatic combat is off in Peaceful.

`GuardianBossCombat` owns the encounter: one action at a time, ability cooldowns,
12-tick recovery gap, least-recently-targeted eligible player selection, cluster
shockwaves, close frontal slams, ranged throws/beams, and restrained repositioning.
There are no competing vanilla target goals. The boss holds a useful firing position
and only paths when the group is out of reach or behind cover, within 14 blocks of
its encounter origin. A blue boss bar is shared by eligible players within 48 blocks
of the boss and encounter origin. Health is 200 + 100 per extra player, capped 600;
maximum/current rise together for late joiners and never shrink during the fight.
After 200 ticks with no eligible participants, reset to full solo health.

`/ew guardian fight` enables automatic group combat. `/ew guardian stop` cancels all
attacks and held/flying rocks and persists passive mode with the command tag
`ew_guardian_passive`. Reload clears attack sessions; an aggressive living Guardian
can engage again with saved health. Death/removal clear bossbar/projectiles.
Read-only `/ew guardian status` reports the last leap outcome/cancellation reason.
One-shot real attacks: `beam`, `rock`, `shockwave`, `melee`, `leap`. These aim at the issuer
and leave the Guardian passive. Cosmetic controls remain `awaken`, `slam`, `throw`,
`follow`; they also pause aggression. Commands affect the nearest living Guardian
within 32 blocks and require operator permission. See `docs/fractured-guardian.md`.

`GuardianBeamAttack`: tracks predicted movement through tick 23, locks, fires on tick
32, and keeps a twelve-tick cyan pulse hazardous until tick 44; ends tick 72.
Damage is 8 at most once per victim per cast, including late entrants. A shielded
attempt is also consumed once. Radius .42, range 24; cover is rechecked each pulse tick.
The beam retains living-entity collateral; all other boss attacks damage eligible
players only. `GuardianHeldRockLayer` renders a 1.4-block stone using the current animated hand's
actual GeckoLib transforms. Held projectile rendering is hidden; `GuardianRockRenderer`
renders the same full-sized block in flight. The server release socket mirrors X and
applies the renderer's 180-bodyYaw rotation (the previous socket was mirrored to the
wrong side). `GuardianRockEntity` locks a predicted landing position at tick 36,
releases at tick 44, then flies ballistically at roughly 1.05 blocks/tick. Swept corner/
center rays stop the rock at cover; impact gives 8 direct or 4 splash (not both),
within 2.25 blocks with visibility checks. No explosions or block changes. It expires
on stop/death/unload or after 80 flight ticks. `GuardianThrowSocket` is generated by
`tools/prepare_guardian_throw_socket.py`; its `--check` detects animation drift.

The full-body slam hits at tick 26: close mode deals 6 in a frontal arc within 4.5
blocks; shockwave mode sends a 0.75-high ring to radius 18 at .65 blocks/tick, damage
6 once per eligible player. `GuardianWaveVisual` draws two rows of temporary stone
geometry and a bright cyan crest, with denser dust. These are render-only blocks,
not world edits or physical obstacles; the server ring is authoritative. Shared
`GuardianWaveSurface` checks keep visual and damage cover rules aligned. Client
terrain sampling is capped at 80 Hz and wave state clears on cancellation. Ground sampling uses collision surfaces at -3..+2 height,
with cover checks; jumping above the band avoids it. Terrain is unchanged.
After a throw, the director favors a ready wave even against a solo player; following
that wave it favors a beam. Shorter cooldowns and the 12-tick gap maintain pressure.
`GuardianMotionSample` uses server-observed position deltas, rejects teleport jumps,
and `GuardianCombatRules` caps movement prediction at 6 blocks. Reversing after
commitment, sprinting/dashing, jumping the ring, and cover remain counterplay.
`GuardianCombatRules` exposes selection, scaling, prediction, arc, and wave math for regression
checks in `src/guardianTest`. `checkGuardianBeam` runs beam/boss suites and compares 180 socket poses/yaws against
actual GeckoLib bone transforms. These do not constitute a live Minecraft playtest.
Space primary aim assist recognizes aggressive Guardians without a vanilla target.

`GuardianLeapAttack` adds a fist-powered double-wave leap against eligible visible
players 12–30 blocks away. It favors a ready leap with a 180-tick cooldown after its
94-tick action. The marker tracks/predicts until tick 10, commits, and the fists
hit the ground at tick 14, launching the first wave. Flight lasts 36 ticks along an
11-block-high arc with real `Entity.move` collision, not teleports. The full body
route is checked before takeoff and again per movement segment; obstructions cancel
without impact damage. Landing at tick 50 deals 16 within radius 3 or 10 within
radius 6, with cover/vertical checks. Six ticks later a second 18-block wave begins.
Both waves use separate world origins, tracked slots, and hit sets. The current
longer flight separates them; the renderer also supports simultaneous waves. A victim of the heavy landing is excluded from
its follow-up wave to avoid automatic double damage. Launch-wave victims can still
be hit by the later landing/wave. Both waves retain normal 6 damage and jump rules.
The landing recovery ends at tick 94, then the normal 12-tick inter-attack gap.

Frozen windups may clear Minecraft's cached `onGround` flag. The leap therefore
checks actual block support beneath the feet at start/takeoff/landing. Horizontal
travel eases in after the first 10% of flight and finishes before the last 10%,
clearing nearby steps during vertical departure/arrival. Candidate landings must
have a clear full route before showing the marker, and predicted updates retain
the last valid route if a new candidate is blocked. Regression fixtures reproduce
the prior diagonal-path rejection beside a low step and retain wall/ceiling checks.

The leap searches for a supported, clear landing within the existing arena and
avoids unloaded chunks, fluid-filled destinations, passengers, vehicles, and world
border crossings. Stop/death/removal/empty arena abort it and restore gravity.
`ew_guardian_leap_gravity` is an internal reload marker: loading an interrupted
flight restores gravity without resuming damage. The Guardian now tracks movement
every tick for the fast leap. Orange/red landing rings remain at world positions,
as do both waves while the boss moves. Live collision and multiplayer timing still
need playtesting; regression checks cover arcs, AoE bounds, both wave durations,
selection/cooldowns, and source/asset transforms.

`FracturedGuardianEntity`, `FracturedGuardianModel`, `FracturedGuardianRenderer`,
and `FracturedGuardianRenderState` follow the same GeckoLib architecture as the
other custom mobs. `AutoGlowingGeoLayer` reads `fractured_guardian_glowmask.png`.

The approved geometry/texture source is `art/fractured_guardian/v2-textured/`.
The original six animation clips remain authored in `art/fractured_guardian/v4-expressive/`.
`art/fractured_guardian/v5-leap/build_leap.py` copies those clips unchanged and adds
`leap_launch` (0.7s), `leap_air` (1.8s), and `leap_land` (2.2s). Current merged JSON,
editable Blockbench project, pose review, and preview are in `v5-leap/`; regenerate
that folder before packaging after any V4 changes. The generator preserves all 132 cubes / 38
bones. The full-body pass uses eased anticipation/recovery, braced legs with
planted-foot IK, chest plate separation, shoulder/arm spreading, and finger/thumb
follow-through. Head counter-transforms hold the mouth pivot steady during torso
expansion; client target pitch is added to that authored counter-rotation.
Triggered attack transitions use zero extra controller blending so authored impacts
stay aligned with the server timeline. Earlier art versions remain available. `preview_motion.py` samples floor clearance/support feet and renders a
review page; it does not prove in-game pathfinding or animation timing.

`python3 tools/prepare_guardian_assets.py` compiles the original atlas into
1024x1024 RGBA base/glow textures, scales UVs without changing geometry, and
packages nine authored clips (six V4 clips plus three leap phases). `--check` validates runtime drift,
bone references, keyframe ranges, and loop endpoints. The two creature textures
are separate from the 298-PNG spell/HUD contract; do not change affinity counts.

Remove nearby test Guardians with
`/kill @e[type=elementalwands:fractured_guardian,distance=..32]`.
See `docs/fractured-guardian.md` for the playtest checklist and draft sanctuary lore.

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

- `docs/PLAYER_GUIDE.txt` and `docs/ICE_WAND_REDESIGN.md` contain historical
  crystal/Ice-era information. `docs/MOD_HANDOFF.md` is the current session checkpoint;
  `CLAUDE.md` is a pointer to this authoritative guide.
- `CastPrimaryPayload` remains as dead or future-facing networking code.
- Some removed content assets remain in `src/main/resources/assets/elementalwands/textures`
  even though the related registered items/blocks no longer exist.
