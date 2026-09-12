# AGENT.md

This file gives coding agents the current working map for the Elemental Wands
repo. It is based on the older `CLAUDE.md`, but cleaned up for the current
Universal Wand / affinity architecture.

## Current session checkpoint — September 12, 2026

Latest approved combat balance (September 12): Fire primary/Pyre deal 6; meteor
uses a capped, distance/cover-aware explosion without falling-block damage. Guardian
fan becomes three quick aimed bursts, and a ready laser has protected priority after
two other attacks within its existing range. Read `docs/fire-guardian-balance-2026-09-12.md`
for exact tuning, validation and installation status. Human playtest is pending.

Latest Fire redesign (September 12): approved workshop 05 is implemented with
original four-frame art, an eight-block round fireball with half-block steps and
its full flame shell, an oriented 2.8-high traveling Pyre wall, and Pyre-only
0.075-high lingering flames. Primary/ultimate ground fire looks vanilla. Combat
is preserved. Read `docs/fire-redesign-2026-09-12.md` for validation/install status.

Latest Nature balance (September 11): approved flower-focused tuning makes seeds
flat 3 damage without Entangle; flower/secondary thorns ramp 3–5 with shared
one-second caster/target contact limits. Nature charge is capped per caster at
one seed point plus three thorn points per second; ultimate damage gives none.
The tree grants Regeneration II; Fire remains I. See
[the balance report](docs/nature-balance-2026-09-11.md) for checks and installation.

September 11 audit follow-up: [fix report](docs/audit-fixes-2026-09-11.md) covers
non-destructive meteor spawning, Titan/Nature temporary-block ownership, durable
equipment recovery, admission-before-vegetation mutation, and the remaining Aegis
cleanup. The user approved the standing slam remaining 360 degrees for multiplayer.
Hollow Purple now commits for its full 70-tick charge: no slot-switch cancellation
or refund, movement/item-use lock with free aiming, then release. Death, world exit
and encounter teardown clean up without a refund. See the same fix report.

Latest full audit (September 11): fixed the unguarded `/ew admin` unlock commands,
the Fire ultimate deleting the block 35 above its target, temporary spell blocks
becoming permanent when a wall/dome/tree covered another temporary block, wand NBT
that stored server uptime ticks instead of world time (Pyre buff, dash chaining),
non-persistent scheduler/projectile entities that could be saved, queued ultimate
key presses, the beam cancelling after lock when its target died, the `reviewing`
flag surviving `/ew guardian stop`, Tendril Bloom targeting passives/teammates/own
tree, missing lang keys, and church/arena journal parsing that could throw out of
the tick loop. Dead code listed under Cleanup Candidates was removed. Human playtest
of these fixes is pending.

Nature playtest (September 11): user completed a full Guardian fight and liked the
redesign. Reported entangle wraps on the ultimate's living hitbox are now excluded
on client and server; Nature slows, stacks, root tracking and thorn damage skip
AwakenedTreeEntity. Ordinary attack damage remains. See the Nature report for checks.

Latest Nature redesign (September 10): approved workshop revision 04 is ported to
custom flower/root/raft blocks, a 3D seed, attached angular entangle meshes and the
9-high/11-wide oak ultimate with heartwood and flowering leaves. Guardian recovery
cues use the real extra-opening timer. See [the Nature report](docs/nature-redesign-2026-09-10.md)
for release verification and installation. Full-fight Nature feedback was positive;
the tree entangle fix awaits client confirmation.

Latest spell visibility update (September 10): primary Fire/Space/Wind decorative
bursts start forward/down with cover checks. All affinities' custom particles,
Fire ribbons/front, Wind blades and Nature seeds fade near the first-person camera.
Projectile origins/combat and third-person opacity are preserved. See
[the clearance report](docs/spell-view-clearance-2026-09-10.md) for checks/install.
Human first-person feedback pending.

Latest Wind/Guardian pass (September 10): Wind primary is a centered three-blade
fan with a strict 12-block range and one hit per enemy/cast; dash recharge is five
seconds per charge. Guardian external push accumulation is blocked. New telegraphed
floating-stone volleys, sustained-hover pressure, mixed phase-two sequences and
closer pursuit are implemented. Read [the pressure report](docs/wind-guardian-pressure-2026-09-10.md)
for validation and current installation. Human fight/appearance feedback pending.

Latest Stone/Guardian balance: a contacted player Stone Wall absorbs one Guardian hit, then breaks. Beam/wave cover lasts for that hit only; later combo hits pass. See `docs/guardian-stone-wall-2026-09-10.md` for passing real-server checks and installed build. Human combat confirmation pending.

Earlier combat fix: the user approved the full two-phase fight. Guardian shockwaves
now pass through Nature flowers/growth and Stone primary spikes, clear contacted
tracked Nature plants, and remain blocked by Stone walls and Nature ultimate trees.
Client and server share floor/cover rules. See [the cover report](docs/guardian-wave-cover-2026-09-09.md)
for current checks and installation. Human confirmation of this specific fix is pending.
Latest HUD: carved wooden frames with brass/rune details, inset labels, and a text-free Stone reserve bar directly above the ability row. See `docs/wand-hud-2026-09-10.md`; user approved the design.
Latest shared build/installed SHA-256: `135618b30c832e0251a2cae0b434401571ed0352b2086352c152bb1b93b717a0` (September 12 approved Fire/Guardian balance; prior Fire artwork, Nature, audit and Hollow Purple changes preserved). See `docs/stone-gathered-mass-2026-09-10.md` for the custom model, 1.5s/2.5s recovery, stagger and validation. The user approved the new ability/model; the vegetation collision follow-up ignores low plants and retains solid cover. Confirmation of that fix is pending.

Latest church locator fix: the reported vanilla command blocked the server for
113 seconds. Church commands now schedule incremental structure-start searches,
with cancellation and temporary ticket cleanup; terrain scoring avoids redundant
work without changing placement rules. Read [the locator report](docs/guardian-locate-2026-09-09.md)
for validation and installation status before using older build hashes below.

Latest church placement update: statue/socket/offering moved six blocks forward,
24-chunk spacing with 14-chunk separation, least-earthwork nearby placement, preserved
natural side courtyards, short stone foundations and local soil/entrance steps.
Unfinished legacy sites move only the ritual group using a durable inventory receipt;
completed sites remain unchanged. See [the placement report](docs/guardian-placement-2026-09-09.md)
for validation and installation. The site anchor remains the original building origin;
use `Site.socket()` and `Site.offering()` for interaction positions.


Latest combat pass: two phases at 60% health, with a 64-tick unstable-magic
transition. Wider jumpable waves (32/44 blocks), phase-two triple slams and rock
volleys, and a leap follow-up slam. Original laser logic/animation, guard opening
and health tuning are preserved. Exact slam/leap destination markers are removed;
the leap uses a moving body shadow. Waves finish independently of attack recovery.
See [the phase report](docs/guardian-phases-2026-09-09.md). Preview at port 8330 now
includes transition/fast attacks and the approved guard pose. Human playtest pending.

Latest visual revision: guard wear follows dark joints/carved grooves in the original
stone palette. The exposed stance now has a strong backward arch, bent knees,
head tilted back and hanging arms and sustained trembling; planted-foot IK and hand clearance
are baked into the separate guard clip. Preview refreshed at port 8330. See
[the visual follow-up](docs/guardian-guard-visuals-2026-09-09.md). Human approval pending.

Latest balance pass: separate stone guard with three crack textures, opening ribs,
projected/shaking core and visible recovery. Main health is 600 + 450 per additional
player; guard is 120 + 90, through 64 players. Both retain their encounter peak.
Guarded/exposed main damage is 40%/150% after softening the portion of a hit above
40 to 35%. Opening/full exposure/closing last 24/140/30 ticks. Teammates have separate
hurt cooldowns; guard breaks wait for active leaps to land. One health bar only.
Read [the guard/balance report](docs/guardian-guard-2026-09-09.md) for tuning,
implementation, previews and checks. Human balance and animation feedback remain open.

Latest wand/boss follow-up: [Nature and arena spells](docs/guardian-nature-2026-09-09.md).
Nature earns a one-second post-attack opening with shared ten-second immunity;
Guardian impacts crush local growth and sustained thorns bias its next slam.
Fire coals may temporarily reskin the protected combat floor, seedlings survive
that floor, and Fire/tree regeneration now reaches actual healing ticks.
Human balance and appearance confirmation remain open.

Read [docs/MOD_HANDOFF.md](docs/MOD_HANDOFF.md) before resuming Guardian work.
The latest addition is the command-triggered, roofless 128-block Guardian arena:
[docs/guardian-arena.md](docs/guardian-arena.md) records its design, commands,
lifecycle, persistence, and validation. Death/disconnect eliminate players from
combat; death now automatically respawns them in spectator mode inside the arena.
They fly and see the boss bar but cannot fight/cast; all fighters dead closes the
arena. Return coordinates and original modes persist across disconnect/restart.
No exterior waiting-ground search is required to summon. Read
[the spectator update](docs/guardian-spectators-2026-09-09.md). The latest user playtest approved the corrected arena/floor. The survival church
encounter is now implemented; read [docs/guardian-church.md](docs/guardian-church.md)
for natural generation, placement/locator commands, heart recall, victory restoration,
loot, persistence, and offline build preview. Current Lunar build source/installed SHA-256 is
`467cf5a41bd7100802b27e7d1a846a70f44200090375cc9012d5369d7cfb9fbc`.
Latest follow-up: a vanilla-block Guardian effigy replaces the visible dormant
entity. Use the existing socket at its feet. No living Guardian is present before
the ritual; the ritual actor stays hidden during the player-only ascent. After
formation it falls 48 blocks in 36 ticks, plays a 32-tick impact/recovery clip, then
begins the existing awakening. Added arrival assets are separate from the approved
attack clips. Old unfinished sites refresh only the effigy footprint on revisit.
The real vanilla respawn lifecycle exposed an early teleport: Fabric AFTER_RESPAWN
fires before the connection swaps away from the dead player. Returns now wait for
the next server tick and the correct handler player. Safe-ground searches load
chunks before heightmap reads and never use an unchecked world-spawn fallback.
See [the arrival/respawn follow-up](docs/guardian-arrival-2026-09-09.md).
The earlier deep audit remains recorded in [its report](docs/guardian-audit-2026-09-09.md).
The user approved the ornate geometry, colorful glass and teal roof, then asked for
stone instead of quartz and gold. No crosses or real-world religious symbols.
Latest reward update: each chest independently rolls modest/better/rich treasure,
ordinary supplies, and shuffled slots. Books choose varied enchantments up to II,
respecting natural level-I caps. Stable per-site seeds prevent rerolls; completed
chests do not refill. Magical rewards are deferred. See the church guide for odds.
Latest playtest fixes: unfinished ruins blend exposed platform edges into nearby
soil with a saved, replayable slope plan. Existing RUINED sites also receive this
pass on revisit. Natural trees/foliage are breakable inside the ward, and valid heart
activation clears vegetation in the Guardian/player lift shafts before admission.
Server fixtures reproduced both the four-block ledge and tree-blocked ritual and
passed. Human feedback on these two fixes remains open.
The first human arena playtest exposed overlapping wall meshes and choppy carry
teleports. The follow-up uses invisible backing blocks, synchronized passenger
carriers/floor samples, and a church-style perimeter with ornate columns/arches.
The user liked the revised arena, then requested regular-sized floor blocks.
The latest floor/rim uses one-block vanilla texture repetitions in a single batched
surface (`GuardianArenaFloor`), preserving room size, architecture and lift behavior.
The first one-block floor playtest hit an invalid-atlas-ID crash. The fix uses
`Atlases.BLOCKS` for `AtlasManager.getAtlasTexture`, reserving the texture-file ID
for the render layer. A real-client asset/mesh regression is available through
`tools/guardian_floor_client_smoke.init.gradle`; it passed with local Lunar assets.
The user liked the corrected floor in a fresh Lunar playtest; broader fight balance is open. The earlier takeoff fix also still needs human confirmation; do not
interpret server checks as confirmation of client visuals or overall fight balance.

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
python3 tools/prepare_fire_assets.py --check
python3 tools/prepare_guardian_assets.py --check
python3 tools/prepare_guardian_throw_socket.py --check
node tools/prepare_nature_models.mjs --check
unzip -t build/libs/elementalwands-2.2.0.jar
```

The validator covers JSON and texture references, RGBA/power-of-two rules,
material depth, unique animation frames, model references, the registered
Fire/Wind particle additions, approved Fire resource references, and
exact production counts. Asset generators
must only be run with `--replace` when intentionally replacing the families
owned by that generator; never use a Fire/Wind pass to regenerate Arcane.
The exact package contract is Fire 45, Wind 53, Stone 41, Nature 44, Space 81
(264 affinity PNGs), plus two shared gear/HUD PNGs and 40 particle definitions.
Fire uses the approved workshop animation frames, vanilla netherrack for the Pyre
base, vanilla-looking ordinary ground fire, and a custom all-fire meteor renderer.

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
|-- church/                     # persistent church sites, socket, ritual and restoration
`-- mixin/                      # player, arena, and church protection hooks
```

Important resources:

- `src/main/resources/fabric.mod.json` - mod metadata and entrypoints
- `src/main/resources/assets/elementalwands/lang/en_us.json` - translatable strings
- `src/main/resources/assets/elementalwands/items/*.json` - 1.21.10 item model definitions
- `src/main/resources/assets/elementalwands/models/item/*.json` - item model JSONs
- `src/main/resources/assets/elementalwands/geckolib/**` - GeckoLib models and animations
- `tools/prepare_fire_assets.py` - approved workshop Fire exporter (`generate_fire_vfx_assets.py` is a compatibility entry point)
- `tools/generate_wind_vfx_assets.py` - deterministic Sky Shear Wind assets
- `tools/generate_shared_vfx_assets.py` - neutral universal wand and HUD frame only
- `tools/validate_remaining_vfx_assets.py` - all-affinity/shared VFX audit
- `docs/vfx-style-guide.md` - palettes, family ownership, exclusions, and counts

Crystal ore blocks, crystal crafting recipes, and ore worldgen were removed in
the Universal Wand refactor. `ModWorldGen.registerAll()` remains a no-op hook.
`ModBlocks` registers the permanent church socket plus three internal Guardian arena foundation/wall blocks
with no items or recipes. `GuardianArenaManager` owns the moving arena, membership,
containment, temporary sky structure, return, and world-save recovery receipt.

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

`AbstractWandItem.onWandDamageDealt(...)` grants Arcane Flux and normally adds +5
ultimate charge to the player's main-hand wand. Nature uses its explicit-charge
overload and player-owned `NatureCombat` windows (seed +1/s, thorns +3/s, ultimate 0).
A successful ultimate requires 100 charge
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
- Primary: `InfernoWaveEntity`, drawn by `FireWaveRenderer` using the approved
  four-frame stream on three velocity-aligned planes and a perpendicular curl.
  Speed, range, damage, piercing, collision, burning and temporary ground behavior
  are unchanged. `INFERNO_FLAME` uses vanilla fire models/textures without adding
  vanilla fire spread to the existing temporary trail.
- Secondary: Dragon's Pyre retains its 40-block scheduler, damage and self-buffs.
  A nonpersistent `PyreFrontEntity` follows the sampled surface as an oriented
  2.8-high custom fire wall; it disappears after propagation. Only `PYRE_FLAME`
  uses the 0.075-high original animated floor carpet on vanilla netherrack.
- Ultimate: `MeteorManager` retains the falling-block collision/explosion logic.
  `FireMeteorRenderer` delegates normal falling blocks to vanilla, replacing only
  `METEOR_CORE` with the eight-block sphere of half-block steps, 57 curved flame
  strips and 78 animated embers. `FireSpellMeshes` loads the approved 1,248-face
  model. Full-bright animation and near-camera fades preserve the visual contract.
  The explosion's ordinary ground fires remain vanilla.
- Original HUD icons remain; Fire Spirit is outside this pass.

### Wind

- Primary: three `VacuumBladeEntity` crescents in a narrow fan, centered on the
  crosshair, 12-block range, 7-to-4 damage falloff, one hit per enemy/cast and the
  existing 20-tick firing cooldown. Short wisps use the existing six-frame art.
- Secondary: Waylay Dash retains two charges, strength and chaining; each missing
  charge takes 100 ticks (five seconds) to recharge. `WaylayDashVfxManager` remains
  a five-tick visual tracer with its existing cleanup lifecycle.
- Ultimate: Zephyr Strike. It equips a temporary Elytra, launches the player, and
  creates a landing/impact explosion before restoring the old chest item. Its
  pearl vane wings, ascent streams, descent compression, shear feathers, and
  denser landing sequence are visual-only and retain vanilla Elytra geometry.
  Its post-impact visual burst self-removes after eight ticks and is cleared on
  server stop along with the existing active-strike state.
- Calamity Tornado, ores/crystals, and unused `wind_wand.png` are excluded from
  the second-generation VFX pass.

### Stone

- Primary: Gathered Mass via `StoneClusterManager` and `StoneClusterEntity`.
  Downward right-click on nearby solid ground gathers 25/100 material every
  12 ticks without changing terrain. Aim elsewhere to throw: small rock 5 damage /
  30 ticks; stored mass 14–32 damage at normal 25/50/75/100 stages / 50 ticks.
  Weight scales to -25% movement speed; actual damage chips the reserve but keeps
  its final point. Hits at 75+ briefly stagger ordinary targets. Water flight and
  elevated aiming work; solid cover blocks projectiles. The old spike scheduler
  is removed. See `docs/stone-gathered-mass-2026-09-10.md` for controls and checks.
- Secondary: Stone Wall. Guardian hits consume the wall after it absorbs that hit; subsequent attacks are no longer blocked. Recasting near the active wall shatters it forward for
  damage and knockback.
- Ultimate: Titan Dome via `TitanDomeManager`.
- Natural gray Stone textures: all 41 production PNGs use gray rock and restrained
  mineral accents, with rebuilt block surfaces, armor plates, sword and Titan debris.
  September 10 follow-up adds finer irregular Minecraft-style grain and worn bevels
  while preserving the approved shapes, alpha, dimensions and primary buff.
  See `docs/stone-redesign-2026-09-10.md` for preview and verification status.

### Nature

- Passive: Verdant Step places custom temporary leaf rafts over water.
- Primary: `SeedProjectileEntity`, rendered as a 3D winged seed, plants a four-stage
  custom flower or deals flat 3 direct damage without Entangle. Its thickets use
  custom roots/rafts and NatureCombat for 3–5 damage and Entangle each second.
- Secondary: Tendril Bloom sends vines from seedlings to targets and spreads
  brambles without another flower. Attached angular vines show Entangle state.
- Ultimate: `OvergrowthManager` builds the approved nine-block oak tree using
  `NatureTreeLayout`, including custom heartwood/flowering leaves. See the Nature
  balance report for Regeneration II, amplification, damage and Guardian behavior.
- `tools/prepare_nature_models.mjs` exports the workshop's models/mesh/layout;
  `--check` detects drift. No PNG counts changed in this model-only pass.

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
- Ultimate: Hollow Purple charge sequence via `HollowPurpleChargeManager`. Once
  started it commits for 70 ticks with no voluntary cancellation/refund. The caster
  rises in place, can aim, and cannot walk, blink or use items during the charge.
  Switching/dropping the wand does not stop release. Death/world exit cleans up.

## Manager Singletons

Complex timed or world-state effects live in `util/` managers and are usually
registered from `ElementalWandsMod.onInitialize()`.

| Manager | Used by |
| --- | --- |
| `TemporaryBlockManager` | temporary blocks for stone, fire, and nature effects |
| `GuardianArenaManager` | sealed Guardian party, tower formation/return, protected sky structure, restart recovery |
| `EntangleTracker` | nature entangle/root stacks and cooldown slowing |
| `OvergrowthManager` | Nature ultimate |
| `SeedlingManager` | Nature primary seedlings |
| `TendrilBloomManager` | Nature secondary tendrils and blooms |
| `MeteorManager` | Fire ultimate |
| `TitanDomeManager` | Stone ultimate |
| `WaylayDashVfxManager` | five-tick visual-only Wind dash tracer |
| `BlinkRiftManager` | Space secondary rift tracking |
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

Primary casts never go through networking; they run from item use on the server.

## GeckoLib Entity Notes

### Fractured Guardian cooperative boss

`/ew guardian arena start|status|stop` controls the new optional arena. It gathers
eligible players within 20 blocks, carries them through walls/floor/ascent, then
starts the existing fight on a protected 128-by-128 floor above the terrain. The
arena's boss-bar/participant range is 192 and movement/leap bounds cover that room;
normal summon encounters below retain their old bounds. See `docs/guardian-arena.md`
for the admission rules, safe abort, permanent elimination, and restart recovery.


Spawn with `/summon elementalwands:fractured_guardian ~ ~ ~10`. It remains MISC,
no natural spawning, persistent, with a 3.2 × 5.2 body hitbox. It automatically
awakens over 84 ticks when a visible Survival/Adventure player is within 24 blocks.
Creative/Spectator and teammates are excluded; automatic combat is off in Peaceful.

`GuardianBossCombat` owns the encounter: one action at a time, ability cooldowns,
12-tick recovery gap, least-recently-targeted eligible player selection, cluster
shockwaves, close circular slams, ranged throws/beams, and restrained repositioning.
There are no competing vanilla target goals. The boss holds a useful firing position
and only paths when the group is out of reach or behind cover, within 14 blocks of
its encounter origin. A blue boss bar is shared by eligible players within 48 blocks
of the boss and encounter origin. Health is 600 + 450 per extra player (up to 64 players);
maximum/current rise together for late joiners and never shrink during the fight.
After 200 ticks with no eligible participants, reset to full solo health and guard.

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
within 3 blocks with visibility checks. No explosions or block changes. It expires
on stop/death/unload or after 80 flight ticks. `GuardianThrowSocket` is generated by
`tools/prepare_guardian_throw_socket.py`; its `--check` detects animation drift.

The full-body slam hits at tick 26 (21 when unstable). Both the close slam and the
shockwave currently emit the same full ring from `GuardianPhaseRules` (radius 32 at
.85 blocks/tick, or 44 at .95 in phase two), 0.75 high, damage 6 once per eligible
player; there is no separate frontal-arc contact hit in the code. `GuardianWaveVisual`
draws one row of temporary stone geometry and a bright cyan crest, with denser dust. These are render-only blocks,
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
without impact damage. Landing at tick 50 deals 16 within radius 3, with cover/vertical
checks; outside that radius only the follow-up wave applies. Six ticks later a second 18-block wave begins.
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
bone references, keyframe ranges, and loop endpoints. The base/glow and six crack-variant creature textures
are separate from the 266-PNG spell/HUD contract; do not change affinity counts.

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
- The September 11 audit removed the dead Calamity Tornado / Boulder entities,
  `BlazeTrailManager`, `EventHorizonManager`, `MovementDisruptManager`, the Titan
  "Aegis" wall path, `CastPrimaryPayload`, `SpellBillboardRenderer`, the azalea
  support mixin, and the crystal-era item/ore textures. `textures/entity/winged_seed.png`
  is no longer rendered (the seed is a mesh) but stays because the Nature PNG
  contract in `tools/validate_remaining_vfx_assets.py` counts it.
