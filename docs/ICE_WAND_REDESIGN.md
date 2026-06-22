# Ice Wand Redesign — Design Spec

**Status:** Locked, pending implementation
**Mod version target:** 2.2.0 (tentative)
**Primary reference:** Existing wand infrastructure in `com.anton.elementalwands.item` and `com.anton.elementalwands.util`

---

## 1. Design Intent & Identity

### The Problem
The current Ice Wand (`IceAbilityHandler`) has the strongest CC of any wand but no distinct identity. Its abilities each *touch* CC mechanics but don't combo with each other — stacks build toward Shatter, but Shatter's AoE feeds back into more stacks in ways that feel either oppressive (group fights) or hollow (1v1). The ultimate (Blizzard) layers Blindness + Frost stacks + freeze damage simultaneously, which is the "enemy has no options" trap PvP design should avoid.

### The Fantasy
Ice is now a **patient territorial controller with a trickster finisher**. The Ice player:
- Plants slow-burn frost anchors across the battlefield (primary)
- Triggers them as a dramatic trap-spring when enemies are caught out (secondary)
- Commands a zone of atmospheric dominance that amplifies their setup (ultimate)

### The Principle
**Debuffs must create decisions, not remove them.** Every Ice debuff leaves the enemy *some* path to respond:
- No hard roots. Slowness IV is the ceiling; Slowness V only at max Frost stacks.
- No Blindness. Darkness only.
- All placed shards are destructible.
- Tendrils have travel time; enemies can dodge or destroy source shards.
- Enemies always retain agency — they must choose *how* to respond, not whether they can.

### Distinct From Other Wands
- **Fire** owns zone damage + self-buff. Ice zones are debuff-only; no damage from zones themselves.
- **Wind** owns burst mobility. Ice has passive Frostwalk but no dash.
- **Stone** owns instant terrain-reshape. Ice reshapes terrain *slowly and organically* — the opposite rhythm.
- **Space** owns teleportation. Ice stays put and makes the world hostile to others.

---

## 2. Passive — Held Effects

Applied while the Ice wand is in main or off hand (mirrors Fire wand's Fire Resistance pattern).

| Effect | Details |
|---|---|
| Frostwalk | Water freezes under the caster to temporary frosted ice (vanilla `FROST_WALKER` behavior, e.g. via status effect or equivalent attribute). |
| Caster self-slow | Slowness I while standing inside own Brinicle zones, Tendril Blooms, or Whiteout fog. |
| Self-immunity | Caster exempt from own Frost stack accumulation. |
| Self-immunity | Caster exempt from own Whiteout Darkness effect. |

---

## 3. Primary — Brinicle Shards

### Cast Behavior
- **Cooldown:** 1.25 seconds (25 ticks)
- **Cast action:** right-click, fires a single fast ice projectile in the caster's facing direction

### Projectile Behavior
Upon collision, the projectile resolves in one of two modes based on what it hits:

**Mode A — Direct enemy hit:**
- Damage = `4 + (1 × current Frost stacks on target)`, capped at 10
- **No Frost stack applied** on direct hit (stacks only come from zones)
- Projectile shatters on impact; no shard planted
- Damage source: `world.getDamageSources().playerAttack(caster)`

**Mode B — Surface hit:**
- Scan vertically from impact point (±3 to ±4 blocks) for the nearest walkable floor block
- If a valid floor is found: plant a shard anchor at that floor position
- If no valid floor found within scan range: projectile poofs with small particle, no plant
- If impact was on a wall or ceiling (not floor-facing): optional polish — spawn a particle line from impact point down to the floor anchor to visually connect "the cold descended"

### Shard Anchor Behavior
- **Lifespan:** 30 seconds after plant, then self-expires and cleans up
- **Visual:** packed ice block at anchor position (temporary via `TemporaryBlockManager`)
- **Max active shards per caster:** 5 — planting a 6th replaces the oldest
- **Shards persist if the caster dies or switches items** — they are world-anchored, not caster-bound

### Zone Growth (Pulsing)
- First pulse begins immediately after plant
- Every 1.5 seconds, the zone grows by 1 block of radius outward
- Maximum radius: 2-3 blocks (tune in testing)
- Snow layer placement is **terrain-following**: for each (x, z) column in the current pulse radius, scan vertically (±3 to ±4 blocks) to find the highest walkable floor in that column, place snow layer above it
- Columns with no valid floor in scan range are skipped (handles cliffs, pits)
- Water columns in zone radius are converted to packed ice (temporary)
- Lava columns are skipped

### Zone Effects (on enemies standing in snow)
- Slowness IV for as long as they stand on a snow block from this zone, plus 2 seconds residual after leaving
- 1 Frost stack per second of contact
- No direct damage from the zone itself

### Destruction
Shards can be destroyed through three vectors:
- **Any projectile or direct wand hit on the ice anchor block:** ~0.5 seconds to destroy (the block has low effective HP when attacked)
- **Manual mining:** ~3 seconds (wood-tier hardness, mineable with bare hands)
- **Automatic expiry** at the 30-second lifespan
- When a shard is destroyed (any vector), all its associated snow layers melt, the packed-ice anchor is restored, and any converted water blocks revert

---

## 4. Secondary — Tendril Bloom

### Cast Behavior
- **Cooldown:** 15 seconds
- **Requirement:** at least 1 active shard must exist in the world (from this caster)
- **Cast action:** shift + right-click

### Tendril Spawn
On cast, every currently-active shard from this caster **simultaneously** fires one tendril. Each tendril targets the nearest enemy entity within 15 blocks of its shard (LOS not required — tendrils can reach through minor obstacles, though terrain scan limits apply).

If a shard has no enemies within 15 blocks, it does not fire a tendril — but is still **consumed** once the secondary completes.

### Tendril Travel
- Travel time: 1.2 seconds to cover maximum range (~12.5 blocks/sec traversal speed)
- **Continuous target tracking (Model A):** the tendril's destination updates every tick to the target's current position. If the target moves, the tendril follows.
- As the tendril travels, it places snow layers along its path using the same terrain-following logic as primary shard zones
- **Tendrils can be interrupted:** if the source shard is destroyed during tendril travel, the tendril immediately dissipates and its snow stops growing (but persists its final state)

### Arrival (Bloom)
Upon the tendril reaching its target's position:
- Bloom zone begins at 1-block radius around arrival point
- Expands 1 block per second over 3 seconds to a maximum 3-block radius
- Uses same terrain-following snow placement logic as primary zones

### Bloom Effects (on enemies in bloom zone)
- Slowness IV + 1 Frost stack per second — **identical** to primary shard zone effects
- **No direct damage from the bloom** — this is pure debuff

### Post-Bloom
- Bloom stops expanding after 3 seconds
- Snow persists for 10 additional seconds as lingering hazard terrain
- After total 13 seconds from bloom start, all snow melts and the triggered shard is fully consumed
- **Consumption is per-tendril:** only the shards that actually fired tendrils are consumed. Shards that had no nearby target are also consumed if they were within the 15-block range check, but if the secondary triggers only some shards, untriggered ones remain (for future secondary casts or natural expiry).

---

## 5. Ultimate — Whiteout

### Cast Behavior
- **Requires:** 100 ultimate charge (uses existing charge system from `AbstractWandItem`)
- **Cast action:** X key (client keybind → `CastUltimatePayload`, existing path)

### Fog Zone
- **Position:** stationary at the caster's position at cast time; does not move with caster
- **Radius:** 12 blocks (spherical or cylindrical — tune in testing; cylindrical is likely simpler and matches expected play patterns)
- **Duration:** 12 seconds
- **Visual:** dense particle fog using existing particle types (likely `ITEM_SNOWBALL`, `SNOWFLAKE`, and/or `CLOUD` at high density)

### Asymmetric Vision
The caster has **normal vision** inside the fog. Enemies do not:
- Enemies inside the fog zone: **Darkness II** (not Blindness) for as long as they remain inside, refreshed every tick
- Enemies who leave the fog: **Darkness I** for an additional 3 seconds after leaving
- Caster is explicitly exempt from the Darkness status via the caster-exemption rules in section 2

### Debuff Zone
- Enemies inside the fog accumulate **0.5 Frost stacks per second** (rounds up over ticks — implementation can track as 1 stack per 2 seconds or use fractional accumulation)
- No direct damage from the fog itself

### Shard Amplification
Any Brinicle Shard (active at cast time *or* planted after the fog is up) that falls within the fog radius gets amplified for as long as both the shard and fog are active:
- Pulse interval: 0.5 seconds instead of 1.5 seconds (3x faster growth)
- Maximum radius: 4-6 blocks instead of 2-3 blocks (roughly doubled)
- When the shard leaves the fog (because the fog expires, or the shard's anchor position is outside the fog), it reverts to normal behavior and its zone naturally stops growing past its current state
- Amplified shards remain destructible with the same rules

---

## 6. Frost Stack System Changes (ChillTracker)

### Retained Behavior
- Per-entity stack tracking with 100-tick clear delay
- Caster-on-self Frost stacks continue to halve cooldown recovery speed (existing behavior in `AbstractWandItem.tryStartCooldown`)

### Changed Behavior
- **Maximum stacks capped at 5** (previously 6)
- **5-stack effect:** Slowness V for 60 ticks (refreshed each hit)
- **Remove the 6-stack behavior entirely:** no more Slowness 255 immobilization, no more `setFrozenTicks(...)` application, no more freeze damage tick from high stacks
- Stack progression: 1→Slowness I, 2→Slowness II, 3→Slowness III, 4→Slowness IV, 5→Slowness V (each for 60 ticks, refreshed)

---

## 7. Friendly Fire & PvP Rules

- **Non-caster players (including allies):** fully affected by all Ice effects — Slowness IV, Frost stacks, Darkness — identical to enemy treatment
- **Caster self:** Slowness I only, applied when standing in own Brinicle zones, Tendril Blooms, or Whiteout fog. No Frost stack accumulation. No Darkness.

This matches vanilla Minecraft friendly-fire conventions. No team/faction logic; all non-caster entities are treated equivalently.

---

## 8. Existing Code to Retire / Remove

Removed wholesale (these abilities are fully replaced):
- `IceAbilityHandler.castPrimary` (Frost-Bite Volley) — replaced by Brinicle Shards primary
- `IceAbilityHandler.castSecondary` (Glacial Gust) — replaced by Tendril Bloom secondary
- `IceAbilityHandler.castUltimate` (Blizzard) — replaced by Whiteout ultimate
- Inner class `ChillSnowballEntityWithShatter` in `IceAbilityHandler` — no longer used
- Inner class `ColdWaveProjectile` in `IceAbilityHandler` — no longer used
- All Shatter mechanic logic (AoE burst at 3+ stacks, stack-clear on shatter) — removed
- `BlizzardManager` — replaced by new `WhiteoutManager`
- 6-stack freeze behavior in `ChillTracker` — removed (see section 6)

Preserved and reused:
- `ChillTracker` (with stack cap modification)
- `TemporaryBlockManager` — used for all shard/zone block placements
- `TemporarySnowManager` — may be reused or superseded; reuse if it already handles terrain-following snow placement cleanly, otherwise supersede with new zone growth logic
- `ChillSnowballEntity` — may be repurposed as the Brinicle Shard projectile with modifications, or replaced entirely with a new entity (implementer's call based on cleanest diff)

---

## 9. New Code to Add

### New Classes (Suggested)
- `BrinicleShardEntity` — the ice block anchor entity (or alternative: a pure data structure tracked in a new `BrinicleShardManager` keyed by position + caster UUID)
- `BrinicleShardProjectileEntity` — the flying projectile (may extend or replace `ChillSnowballEntity`)
- `TendrilBloomManager` — static manager for tracking active tendrils, their paths, their target updates, and their eventual bloom behavior
- `WhiteoutManager` — static manager for tracking active fog zones, applying Darkness to enemies inside, applying Frost stacks, and propagating shard amplification

### Modified Classes
- `IceAbilityHandler` — full rewrite of its three cast methods; retain class structure
- `ChillTracker` — update cap to 5, remove 6-stack freeze behavior
- `ElementalWandsMod` — register any new managers in `onInitialize()`
- `ElementalWandsClient` — register any new entity renderers (likely reuse `EmptyEntityRenderer` if Brinicle projectile is added as a new entity)
- `ModEntities` — register new entity types if added

### Configuration / Registry
- Any new entity types must be registered in `ModEntities`
- Any new status effect needs via existing `StatusEffects` — the design only uses vanilla effects (Slowness, Darkness), no new effect registration needed

---

## 10. Ability Interaction Matrix

| Interaction | Behavior |
|---|---|
| Primary direct hit on enemy in own fog | Base damage + 1 per Frost stack, no additional fog interaction |
| Primary plant inside own fog | Shard pulses at 0.5s intervals, max radius 4-6 blocks |
| Shard planted outside fog, then fog cast over it | Shard amplifies as long as it's inside the fog; reverts when fog expires |
| Secondary cast with shards both inside and outside fog | Both tendrils fire normally; any bloom that lands inside fog uses normal bloom parameters (no ult amplification on blooms) |
| Enemy hit by direct primary then enters zone | Stack count resets accumulation timer; no double-stack from single projectile |
| Secondary cast during ult fog | All tendrils fire; any snow placed from tendrils inside the fog does not get ult-amplified (tendril snow is a separate system from shard pulse growth) |
| Caster standing in own bloom while secondary is firing | Caster gets Slowness I, immune to stacks, tendrils do not target caster |
| Caster inside own Whiteout while someone else (teammate or enemy) breaks a shard | Shard destruction proceeds normally; fog continues independently |

---

## 11. Balance Numbers — Summary Table

| Parameter | Value | Notes |
|---|---|---|
| Primary cooldown | 25 ticks (1.25s) | |
| Primary base damage | 4 | |
| Primary damage per Frost stack | +1 | Capped at 10 total |
| Primary max damage | 10 | |
| Shard lifespan | 600 ticks (30s) | |
| Shard max active per caster | 5 | Oldest replaced on overflow |
| Shard pulse interval | 30 ticks (1.5s) | Amplifies to 10 ticks (0.5s) inside fog |
| Shard max radius | 2-3 blocks | Amplifies to 4-6 blocks inside fog |
| Shard terrain scan vertical range | ±3 to ±4 blocks | Tune in testing |
| Shard zone Slowness | IV | |
| Shard zone Frost stack rate | 1 per 20 ticks (1/sec) | |
| Shard mining hardness | ~2.0 (wood tier) | |
| Secondary cooldown | 300 ticks (15s) | |
| Secondary min shard requirement | 1 | |
| Tendril range | 15 blocks | |
| Tendril travel time | 24 ticks (~1.2s) across max range | |
| Tendril target retarget frequency | Every tick (continuous) | |
| Bloom starting radius | 1 block | |
| Bloom max radius | 3 blocks | |
| Bloom growth time | 60 ticks (3s) | |
| Bloom persistence after growth | 200 ticks (10s) | |
| Ultimate charge required | 100 | Existing system |
| Whiteout radius | 12 blocks | |
| Whiteout duration | 240 ticks (12s) | |
| Whiteout Darkness amplifier | Darkness II in fog, Darkness I for 60 ticks (3s) after leaving | |
| Whiteout Frost stack rate | 0.5/sec (1 stack per 40 ticks) | |
| Frost stack maximum | 5 | |
| Frost stack max effect | Slowness V | |

---

## 12. Known Open Questions (for Implementation / Testing)

These are intentionally deferred — they should be clarified during or after initial implementation:

1. **Shard projectile speed / trajectory:** not specified; default to a fast-but-visible projectile similar to `ChillSnowballEntity`'s current behavior. Tune after testing.
2. **Fog shape (spherical vs cylindrical):** cylindrical is likely simpler (x-z circle, full y-axis within a range). Default to cylindrical unless spherical has clean implementation reasons.
3. **Particle density for fog visual:** balance between "visible fog" and "performance." Start conservative, increase if the effect feels too subtle in testing.
4. **Tendril visual:** snow layers along path is the minimum; may want additional particle trail (frost/snowflakes) for clarity. Tune after seeing implementation.
5. **Whether shard destruction from projectile should use entity HP model or a dedicated counter:** implementer's call. The behavior spec is ~0.5s to destroy; the mechanism can be whichever fits the codebase cleanly.

---

## 13. Testing Priorities (Post-Implementation)

When testing on a test server, focus on these in order:

1. **Compile success** — run `./gradlew build` before any in-game testing
2. **Basic primary functionality** — does direct hit deal correct damage, does surface hit plant shards, does terrain scanning work on flat/hilly/cave/wall surfaces
3. **Shard destruction** — can shards be shot, mined, and auto-expire without leaving behind permanent blocks
4. **Secondary trigger** — does the secondary require shards, does it consume correct shards, do tendrils track moving targets
5. **Tendril dodge viability** — can a sprinting player escape a tendril, or is travel time too fast
6. **Ultimate fog visuals** — is the fog visible, is the asymmetric vision working, does the caster see through it correctly
7. **Shard amplification in fog** — do shards visibly grow faster inside the fog zone
8. **Edge cases** — caster death with active shards, caster dimension swap, multiple casters' shards coexisting, shard placement on lava / water / void

---

## 14. Future Design Considerations (Out of Scope)

Notes for post-testing iteration, explicitly not to be implemented now:

- **Shatter mechanic revival:** if the kit feels "missing a burst moment" in testing, Shatter could return as a charged secondary variant or a conditional primary effect at max stacks. Design space retained.
- **Mob interactions:** clone mobs as a future ability (parked earlier in design conversation). Could come back as a replacement ultimate or as a new ability tier.
- **Crystal integration:** existing `memory` notes suggest crystals may return in some form. If they do, Ice may want a crystal-interactive ability (e.g., shards gain a bonus from holding a crystal in offhand).

These are intentionally left out of this spec. The current design is a complete shippable kit on its own merits.
