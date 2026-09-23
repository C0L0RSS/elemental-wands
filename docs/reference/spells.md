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

Cast particles leave the wand, not the caster's face. `util/SpellCastVisuals`
estimates the held wand tip on the casting-hand side; cast bursts bloom just past
it, the fractured beam is drawn from it, and the Flamethrower stream and
projectile wakes (Inferno Wave, Vacuum Blade, Singularity Bolt, seeds) bend from
it onto the true path over the first three blocks. Aim, collision, damage and
travel distance still use the authoritative eye-line path. Client renderers additionally fade spell sprites and
meshes near the first-person camera through `client/renderer/SpellViewClearance`.

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
strict seven-block range limit, damage falling from 7 to 4, and one hit per enemy
per cast. Waylay Dash has two separately
recovering charges. Zephyr Strike temporarily equips wings, launches the player,
and creates a landing impact before restoring their chest equipment.

Gale Daggers (`gale_daggers`, 500 Flux) prepares three pearl-white daggers above
and beside the head, with the center higher and all tips facing forward. Press
its slot again or press primary fire (slot 1) to launch the complete volley.
Primary fire replaces its usual spell while prepared; holding that launch click
does not add Sky Shear. Preparation has no spell cooldown. The first launch starts
a 12-second recovery on both the retained wand and the player; changing wands,
death, and reconnects cannot clear it. The shared global tap still applies.

Shots launch three ticks (0.15 seconds) apart, each using the current crosshair
rotation and converging on its raycast target. After release, each projectile flies
straight at three blocks per tick for at most 40 blocks, leaving small, spaced
cloud-white rings that quickly expand and fade. Each deals 5 base damage
without range falloff; all three can damage one target for 15 before armor and
level scaling. The spell-specific damage type bypasses the ordinary hit cooldown,
but preserves armor, shields, invulnerability, party/team/PvP protection and Wind
XP/charge attribution. Solid cover stops shots, including cover between the eyes
and the overhead launch point. No terrain edits or homing occur.

Movement and dashing remain available. Putting the casting wand away, changing
affinity/loadout/world, death, disconnect, or encounter restrictions cancels any
unthrown daggers. Prepared entities are transient and never saved. Canceling an
unfired preparation costs no recovery; an interrupted fired volley keeps its
cooldown. Fired projectiles stop on owner death, exit or encounter exclusion.
Owners: `GaleDaggers`, `GaleDaggerEntity`, and `GaleDaggerRenderer`.

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

Faultline (`faultline`, 500 Flux) sends a twenty-block widening wave after a 0.1-second
warning. It advances two one-block rows per tick (40 blocks per second), reaching
its end about 0.6 seconds after casting. Its far-end width remains eight blocks.
The custom non-solid spikes rise and immediately crumble. Each target
can take one 4-damage hit per cast; non-Guardian enemies lose horizontal momentum,
receive an upward launch of roughly 1.4 blocks on open ground, and have movement
interrupted for twelve ticks (0.6 seconds). They can still aim and cast; this is
a movement interruption rather than an action lock. Forty ticks of interrupt
immunity prevent repeated Faultlines from maintaining a movement lock.
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
Tendril Bloom brambles provide the sustained damage/Entangle loop. Thornbite
(stable saved ID `thorn_lash`) is a separate Basic: a Venus flytrap on three
braided vines extends immediately with a slight upward arc and snaps at the first
non-allied target. Aim and damage origin commit on press; turning or moving does
not sweep the attack. Only the mouth deals damage, at most once per cast, and
solid cover stops it. An invulnerable first target also consumes the bite.
It does not plant seeds. Base damage is 3, range 4.5 blocks, cooldown one second,
and healing is 50% of actual health damage, capped at one heart. Extension takes
three ticks, closure one tick, and retraction four ticks; an early contact shortens
the cast. Both the decorative mouth and stem use the held wand's actual render
transform: the flytrap grows out along the casting-hand side and retracts into
the moving wand tip. This includes left-handed and third-person poses and does
not change the committed damage origin or add hits along the visual side curve.

Tendril Bloom sends vines from flowers; without flowers it grows a stationary
three-tendril root knot. Every trail and bramble patch belongs to its source.
Destroying that flower/knot, losing support, expiry, owner exit, or reload orphan
cleanup must end linked effects without removing another source's growth.

Overgrowth throws an acorn and grows the approved block-built healing oak on
supported impact. A nearby owned flower extends duration; it is not required to
cast, and only the nearest eligible flower is consumed. Invalid landings restore
charge to the retained casting wand. Verdant Step creates temporary water rafts.

Springbloom is Nature's second Technique and fifth spell. It throws a harmless
pod that opens immediately on supported ground into the approved stemless
flower. Grass, ferns, leaf litter and other soft plants do not block placement;
nearby foliage stays in place. Plants covered by the center are restored on expiry,
including both halves of tall plants. The center needs support and clear headroom;
blocked neighboring columns trim the flower instead of rejecting it. A nine-cell
mask controls both the capped visual mesh and the smaller collision/landing area,
and updates when adjacent blocks change. Obstacles, fluids and other spell growth
are left intact; no launch or catch exists on a clipped section. Its shallow yellow cushion is 0.6875 blocks high, with thick basal leaves;
the cushion stays rigid on launch and emits pollen. The raised solid center cannot
be walked onto. A player landing from above is caught without incoming fall damage
and launched upward, including hostile players and last-second pod catches.
Incoming horizontal movement chooses the direction and scales the launch up to its
cap. During the flight, movement input steers with ordinary vanilla air control,
as in any fall; normal gravity and collision still apply. On level ground, flights
rise roughly 20 blocks; steering alone covers about 8–10 blocks from a standing
launch, and holding forward after a full-speed launch reaches roughly 23–25.
A drop to lower terrain can extend the travel.

Each pad is reusable, breakable in one hit, and lasts 80 ticks from opening. Slot 1
can also break an aimed pad with a wand. The 200-tick throw cooldown is keyed to the
player as well as the casting wand, survives death/reconnects, and is not refunded
for failed placement. Each caster can have one active pad. There is no caster-order
or ground-touch chain restriction. Fall protection ends after the next actual
landing; the server tolerates the client ground flag arriving before landing damage.
Leaf particles mark protected flight. Death, disconnect, world changes, flight,
vehicles, fluids and climbing end the flight state. Pad expiry, breaking, support
loss and owner exit restore only that pad's tracked placement. Scheduled checks
remove orphan pads after reload; transient visual entities are not saved.
Owners: `SpringbloomManager`, `SpringbloomRules`, `SpringbloomEntity`,
`SpringbloomBlock`, the Springbloom movement/fall mixins and `SpringbloomRenderer`.

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
