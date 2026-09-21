# Elemental spells

Use this guide for intent and ownership. Read the current handler/rules classes
for exact tuning; archived reports record values at their original release.
Java paths below are relative to `src/main/java/com/anton/elementalwands/`.

## Shared rules

`UniversalWandItem` dispatches affinity behavior to `item/*AbilityHandler`.
`data/WandSpells` owns stable spell IDs; category names do not identify slots.
`util/WandLoadouts`, `AbstractWandItem`, `SpellCombat`, and `party/WandAllies`
provide validation, cooldown/charge, attributed damage/XP, and allied protection.
Preserve all four when adding a spell. See [progression](progression-and-controls.md).

Timed effects live in owning managers registered at common initialization.
Temporary terrain uses `TemporaryBlockManager`: overlapping effects must restore
prior ownership correctly, never turn another temporary layer into permanent
terrain or overwrite unrelated later edits. Equipment loans must restore the
original item across death/reload without losing it or duplicating it.

## Fire

Inferno Wave is the traveling primary; Dragon's Pyre sends a ground-following wall
and leaves low lingering flames. Meteor calls the large stepped fire sphere with
bounded distance/cover-aware explosion damage. Its spawn must not delete the
block above the target, and falling-block contact must not add unintended damage.
Spell ground fire is owned and temporary so it cannot bypass allied protection.

Flamethrower is a held close-range cone with gradual damage ramp, heat, overheat,
and release/timeout handling. Fire Leap is hold-to-aim/release-to-commit, with
terrain targeting, a validated full-body flight arc, and a jumpable landing wave.
The current targeting accepts horizon aim and reachable ledge tops within its
60-block horizontal cap; it retains elevation, cover, fluid, chunk, and arena
safety checks. Keep the stable `fire_hop` ID for saved purchases.

Flashover throws sticky embers onto surfaces or enemies and detonates them with
Spell alternate. Bomb slots recover independently after blast or loss; pending
staggered blasts remain disarmable. Shared hit accounting caps a sequence's
per-victim damage. Never add terrain destruction or unowned spreading fire.

Owners: `FireAbilityHandler`, `FireBuildManager`/`FireBuildRules`,
`FireLeapManager`/`FireLeapRules`/`FireLeapEntity`, `FlashoverManager`/
`FlashoverRules`/`FlashoverEmberEntity`, `MeteorManager`.

## Wind

Sky Shear fires a short three-crescent fan centered on the crosshair, with a
strict range limit and one hit per enemy per cast. Waylay Dash has two separately
recovering charges. Zephyr Strike temporarily equips wings, launches the player,
and creates a landing impact before restoring their chest equipment.

Preserve vanilla Elytra geometry and restore original equipment through lifecycle
interruptions. `WaylayDashVfxManager` is a visual tracer, not movement authority.
Owners: `WindAbilityHandler`, `VacuumBladeEntity`, and the Zephyr lifecycle code.
Calamity Tornado is retired.

## Stone

Gathered Mass collects reserve while aiming at nearby ground and throws it when
aiming outward. Gathering does not alter terrain. Reserve affects weight and
charged-shot strength; damage can chip reserve while retaining its final point.
Low vegetation should not behave like solid projectile cover.

Stone Wall provides cover and can be recast nearby to shatter forward. A Guardian
hit consumes the wall after it absorbs that hit; shared beam/wave/volley cover
must protect against that same attack without shielding later attacks. Titan Dome
owns temporary terrain, confinement, and equipment effects through its lifecycle.
The retired primary spike attack and Titan Aegis wall path remain retired.
Faultline is a separately purchased Technique, not a replacement for Gathered Mass.

Faultline (`faultline`, 500 Flux) sends a ten-block widening wave after a short
warning. The custom non-solid spikes rise and immediately crumble. Each target
can take one 4-damage hit per cast; non-Guardian enemies lose horizontal momentum,
receive a low pop, and have movement interrupted for ten ticks. Forty ticks of
interrupt immunity prevent repeated Faultlines from maintaining a movement lock.
The wave follows nearby solid/leaf/water surfaces; tall cover or a gap stops that
lane. It never edits terrain. Recovery is nine seconds.

Juggernaut (`stone_charge`, 500 Flux) is held to run and released to brake. Grounded
forward motion ramps from a shoulder bump to full strength over approximately
2.5 seconds, with a five-second run limit and eight-second recovery after ending.
Steering is gradual and becomes tighter at speed; looking remains free. Vanilla
jump height, gravity and fall damage remain intact. At full speed, the running
jump covers about ten blocks on level ground. Airborne movement cannot build
charge. Landing has no attack and allows the run to continue; water ends it.
A voluntary grounded release gives a short non-damaging slide; airborne release
hands control back to normal movement.

The first body collision ends the run. Impact damage rises from 4 to 14 before
level scaling. Stronger impacts include a forward cone with weaker secondary
hits. A bounded block budget favors dirt/leaves/glass, then wood, with ordinary
stone costing much more and breaking only close to a full-speed impact. Intact
hard cover shields blocks behind it. No containers, ores, protected structures,
tracked temporary blocks, or floor excavation are allowed; normal block drops
apply. Server block-break vetoes are respected. Ordinary damage does not cancel
a run; ordinary knockback is heavily reduced and ignored at full speed.
Slowness reduces speed/power, while Faultline and hard roots interrupt the run.
Other spells cannot be cast during the run or its braking slide.

Owners: `StoneAbilityHandler`, `StoneClusterManager`/`StoneClusterEntity`,
`FaultlineManager`/`FaultlineSpikeEntity`/`FaultlineSpikeRenderer`,
`StoneChargeManager`/`StoneTechniqueRules`/`StoneMotionMixin`, `TitanDomeManager`,
and shared temporary-block ownership.

## Nature

Seed is a winged pod that damages enemies or plants staged flowers. Flowers and
Tendril Bloom brambles provide the sustained damage/Entangle loop. Thorn Lash is
a separate sweeping Basic with actual-health-damage lifesteal and a per-cast cap;
it does not plant seeds. Current `ThornLashRules` has base damage 3, 50% lifesteal,
and a one-heart cap, superseding the initial report's 6 damage/25% values.

Tendril Bloom sends vines from flowers; without flowers it grows a stationary
three-tendril root knot. Every trail and bramble patch belongs to its source.
Destroying that flower/knot, losing support, expiry, owner exit, or reload orphan
cleanup must end linked effects without removing another source's growth.

Overgrowth throws an acorn and grows the approved block-built healing oak on
supported impact. A nearby owned flower extends duration; it is not required to
cast, and only the nearest eligible flower is consumed. Invalid landings restore
charge to the retained casting wand. Verdant Step creates temporary water rafts.

`NatureCombat` owns per-caster damage/charge windows; prevent thorn spam or
multiple wands from bypassing them. Guardian resistance prevents permanent rooting
while preserving earned recovery openings. Owners: `NatureAbilityHandler`,
`SeedlingManager`, `TendrilBloomManager`, `ThornLashEntity`/`ThornLashRules`,
`OvergrowthSeedEntity`/`OvergrowthManager`, `EntangleTracker`, `NatureCombat`.

## Space

Singularity Bolt is a black star with limited initial aim assistance. It cannot
retarget or U-turn and loses guidance through cover or invalid angles. Impact
causes damage without restoring the retired pull/mobility-disruption mechanics.
Blink Rift makes a safe teleport and leaves a temporary return rift.

Hollow Purple commits for its complete charge. The player can aim but cannot
walk, blink, or use items; switching/dropping the wand does not cancel or refund
it. Death, world exit, and encounter teardown clean it up without a refund.
Owners: `SpaceAbilityHandler`, `SingularityBoltEntity`, `BlinkRiftManager`,
`HollowPurpleChargeManager`. Keep arena containment in all teleport paths.

Visual ownership is described in [art and assets](art-and-assets.md).
Targeted test runners are listed in [testing](testing.md).
