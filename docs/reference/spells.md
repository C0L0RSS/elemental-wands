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
projectile wakes (Fireball, Vacuum Blade, Singularity Bolt, seeds) bend from
it onto the true path over the first three blocks. Aim, collision, damage and
travel distance still use the authoritative eye-line path. Client renderers additionally fade spell sprites and
meshes near the first-person camera through `client/renderer/SpellViewClearance`.

## Fire

Fireball is the traveling primary, with a 20-block configured range. It retains
the stable `inferno_wave` ID for saved loadouts. Dragon's Pyre sends a ground-following wall
and leaves low lingering flames. Meteor calls the large stepped fire sphere with
bounded distance/cover-aware explosion damage. Its spawn must not delete the
block above the target, and falling-block contact must not add unintended damage.
Spell ground fire is owned and temporary so it cannot bypass allied protection.

Flamethrower is a held close-range cone with gradual damage ramp, heat, overheat,
and release/timeout handling. Fire Leap is hold-to-aim/release-to-commit, with
terrain targeting, a validated full-body flight arc, and a jumpable landing wave.
Targeting accepts horizon aim and reachable ledge tops within a 22-block
horizontal cap. When the aimed arc is blocked, it pulls the landing back along
the aim to the farthest clear leap. It keeps the elevation, cover, fluid, chunk
and arena safety checks. The arc is a true parabola at constant horizontal speed.
Its peak is 2.5–6.5 blocks above the higher end and grows with distance.
Flight time comes from a fixed gravity, about 0.8 s for short hops and 1.2 s at
full range. The server validates the arc, spends the cooldown and owns fall
protection and the wave. The caster's client flies the committed arc with real
collision, so there is no carrier entity and no seated pose. The wave fires only
when the server sees the caster land on footing within 1.5 blocks of the mark.
Leaving the arc, hitting something midair or never arriving ends the leap
without a wave. Landing keeps a short skid. Keep the stable `fire_hop` ID for
saved purchases.

Flashover throws sticky embers onto surfaces or enemies and detonates them with
Spell alternate. Bomb slots recover independently after blast or loss; pending
staggered blasts remain disarmable. Shared hit accounting caps a sequence's
per-victim damage. Never add terrain destruction or unowned spreading fire.

All six Fire spells are drawn client-side as "block fire": emissive cubes from
one atlas that rise, tumble, cool through heat bands and shrink away, ported from
the approved browser study in `art/fire/blockfire/`. The server sends spell
moments (cast, hit, break, landing, pop, impact, flamethrower heat) as
`BlockFireEvents`; spell entities supply the rest. Visuals never decide a hit.
Burning targets keep vanilla's on-fire look. Sparks and smoke follow the
particle setting; near-lens cubes shrink in first person. The Pyre's
`PYRE_FLAME` block draws nothing itself; the client draws its carpet.

Owners: `FireAbilityHandler`, `FireBuildManager`/`FireBuildRules`,
`FireLeapManager`/`FireLeapRules`/`client/FireLeapClient`, `FlashoverManager`/
`FlashoverRules`/`FlashoverEmberEntity`, `MeteorManager`; visuals in
`client/blockfire/` (`BlockFireEffects`, `BlockFireKit`, one class per spell).

## Wind

Sky Shear fires a three-crescent fan centered on the crosshair, with a strict
20-block range limit and one hit per enemy per cast. Base damage falls linearly
from 7 up close to 4 at maximum range (5.5 at ten blocks), retaining pressure while
repositioning around a boss. Solid cover stops the blades. Waylay Dash has two separately
recovering charges (five seconds per charge). Its base and chained movement
impulses are 75% of their former strength: 1.5 base and 1.875 for the second
chained dash. Existing momentum, aim, collisions and terrain still affect total
travel. Zephyr Strike temporarily equips wings, launches the player,
and creates a landing impact before restoring their chest equipment.

Updraft (`updraft`, 500 Flux) applies a single vertical launch (~10 blocks of rise
in clear space), retaining current horizontal momentum and vanilla air control.
It can launch from the ground or midair. One charge recovers in eight seconds
from activation, with per-player recovery preserved across wand changes, death
and reconnects, plus the shared spell cooldown/Entangle rules. It requires a
fresh press, so holding its input does not automatically launch again.

The caster remains free to attack, fire prepared daggers and use Waylay Dash.
Updraft grants fall protection only until the first landing, without slowing the
fall or reducing combat damage. Walls and ceilings retain normal collision.
Water/lava contact, climbing, mounting, flight/gliding, death, affinity/world exit,
disconnect and encounter exclusion end its transient flight state. Putting away
the wand does not remove the current jump's safe landing. Twenty cloud puffs
roll out from the launch point near the floor (or at the feet for midair casts)
while a double coil winds up the launch column; a small soft puff marks the safe
landing. Owners: `UpdraftManager`,
`UpdraftFallMixin`, and the vertical-only `UpdraftLaunchPayload`; the owning client
preserves its live X/Z velocity rather than receiving stale server components.

Gale Daggers (`gale_daggers`, 500 Flux) prepares three pearl-white daggers above
and beside the head, with the center higher and all tips facing forward. Press
its slot again or press primary fire (slot 1) to launch the complete volley.
Primary fire replaces its usual spell while prepared; holding that launch click
does not add Sky Shear. Preparation has no spell cooldown. The first launch starts
a 12-second recovery on both the retained wand and the player; changing wands,
death, and reconnects cannot clear it. The shared global tap still applies.

Shots launch three ticks (0.15 seconds) apart, each using the current crosshair
rotation and converging on its raycast target. After release, each projectile flies
straight at three blocks per tick for at most 40 blocks, leaving crisp cloud rings
every 1.2 blocks that quickly expand and fade. Each deals 5 base damage
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

Zephyr Strike equips the transient wings in the chest slot and must restore the
original equipment through lifecycle interruptions. The wings keep the vanilla
glider component but name no equipment model, so the flat Elytra never draws:
clients draw 3D feathered wings on anyone wearing them instead.

All five Wind spells are drawn client-side as "block wind": see-through cloud
cubes, chunky streaks and stepped crescent blades from one atlas
(`textures/misc/blockwind.png`), ported from the approved browser study in
`art/wind/blockwind/`. Sky Shear blades face the caster and target and trail two
fainter echoes; Waylay Dash leaves a widening cloud wake with curled swooshes, and
a chained dash doubles its ring and tints the outer cloud pale blue; Zephyr Strike
streams off the wings, tightens rings in a dive and lands in a dome and a rolling
cloud wall out to its visual radius. Clients draw blades and daggers from the spell
entities; the server sends `BlockWindEvents` for dashes, Updraft, the Zephyr launch
and landing, and blade/dagger hits. Visuals never decide a hit. Motes and curls
follow the particle setting; near-lens cubes shrink in first person.

Owners: `WindAbilityHandler`, `VacuumBladeEntity`, and the Zephyr lifecycle code;
visuals in `client/blockwind/` (`BlockWindEffects`, `BlockWindKit`, one class per
spell). Calamity Tornado is retired.

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
can take one 4-damage hit per cast; non-boss enemies lose their own horizontal
momentum, receive an upward launch of roughly 1.4 blocks on open ground, are shoved
about 1.5 blocks along the wave's direction, and have movement interrupted for
twelve ticks (0.6 seconds). The shove replaces the hit's ordinary knockback and is
shortened by knockback resistance (a Hollow brute travels about 0.6 blocks); the
launch height is not. They can still aim and cast; this is
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

Nature is the support element: its damage stays below Wind and Stone, and its hits turn into
healing. Two rules keep it as simple to use as the other wands:

- **Nature leaves nothing in the way.** Its flowers have no collision, it
  plants no brambles or root knots, and its ultimate places no block in a fight. Nothing can
  be lifted, tripped, blocked, shielded or trapped by Nature growth, and no mob targets a
  flower. A player can aim at a flower's stem and break it (see Sentry Bloom). Springbloom (a
  jump pad the caster places on purpose) is the only Nature block that can be stood on.
- **A boss is an ordinary target.** Every Nature spell deals a boss the same damage as anything
  else. The one difference is the rule every wand shares: a boss is never slowed, rooted or
  moved. No boss has behaviour written for Nature.

Seed is a winged pod that deals 4 on a direct hit; a pod that lands on the ground only scatters
pollen. A Seed or Thornbite hit that deals damage **marks** its target for the caster's flowers
for five seconds (see Sentry Bloom). It winds no vine and slows nothing. Thornbite
(stable saved ID `thorn_lash`) is a separate Basic: a Venus flytrap on three
braided vines extends immediately with a slight upward arc and snaps at the first
non-allied target. Aim and damage origin commit on press; turning or moving does
not sweep the attack. Only the mouth deals damage, at most once per cast, and
solid cover stops it. An invulnerable first target also consumes the bite.
Base damage is 4, range 4.5 blocks, cooldown one second,
and healing is 75% of actual health damage, capped at one and a half hearts. Extension takes
three ticks, closure one tick, and retraction four ticks; an early contact shortens
the cast. Both the decorative mouth and stem use the held wand's actual render
transform: the flytrap grows out along the casting-hand side and retracts into
the moving wand tip. This includes left-handed and third-person poses and does
not change the committed damage origin or add hits along the visual side curve.

Sentry Bloom (stable saved ID `tendril_bloom`) throws a bud, and a flower takes root where it
lands. The bud is lobbed as Gravity Well's bomb is: along the aim at 1.05 blocks a tick with
0.22 of lift, then falling (a level throw lands about twelve blocks out, a raised one
farther). It plants on the ground it comes down on; striking a wall or ceiling it takes the
ground in front of or below that point, and striking an enemy (anything a flower would shoot
unprompted) it takes the ground at that enemy's feet. It passes through everything else, is
harmless, and is lost after three seconds in the air. The cooldown, six seconds, starts at the
throw: a bud that finds no ground within twelve blocks below, lands in water or lava, or comes
down where something already grows shows a message and is not refunded, as a missed Springbloom
pod is not. A caster keeps three flowers; a fourth replaces the oldest. Each lasts 24 seconds
(the last two wilting, with no more seeds) and ends early if its owner dies, leaves the world
or its block is removed. The bud is a `SentryBloomPodEntity`, drawn as the flower's own head at
half size, tumbling (`SentryBloomPodRenderer`); it leaves no trail.

A flower unfurls for about half a second, then spits a small seed every two seconds at one
enemy within 16 blocks that it has a clear line to: the owner's **marked** target if there is
one, otherwise the nearest monster. Unprompted it shoots monsters and bosses only. It leaves
animals, players and neutral monsters that have not picked a fight alone, and never shoots an
ally, their pet or their oak even when marked. A flower's seed deals 2.5 (scaled by Nature
level like every spell, 10% a level), flies straight, leads a moving target, and passes
through anything that is neither its target nor a monster. Each hit gives 1 ultimate charge
and does not move the mark.

A flower's hits heal nobody at once. The flower **banks** a fifth of the health each of its
seeds takes (`BANK_SHARE`), up to 8 (`BANK_CAP`), and puffs hearts every second and a half
while it holds any, more as it fills. Its owner collects by aiming at the flower from within
three blocks (`CLICK_REACH`) and pressing slot 2 (right mouse by default) with a wand, or by
using its block with anything else in hand: they are healed by what it holds and the flower is
used up. Nothing is taken, and the flower stays, when it holds nothing or its owner is at full
health; either way that press casts nothing. Only the owner can collect. Wilting, being
replaced as the oldest, being broken and the owner's death all waste what a flower holds.

A flower can be broken. Its block has no collision but has an outline around its stem and
head (not the spread of its leaves), so a player can aim at it. A fresh slot-1 press (left
mouse by default) with a wand, aimed at any flower from within three blocks, breaks it and
casts nothing; a held repeat does not. Without a wand an ordinary punch breaks it at once
where blocks can be broken. Either works on anyone's flower, the owner's included. Mobs never
attack a flower, and seeds (the caster's and other flowers') fly through one.

A flower's hit is not the caster's own, so it must not get in their way. Its damage type
(`flower_seed`) skips the hit cooldown, and it leaves an ordinary target's hit cooldown as it
found it. A boss keeps hurt windows per attacker: damage tagged `elementalwands:no_hurt_window`
neither is swallowed by nor opens that window, so a flower can never shave its owner's next
Seed. That tag is the general rule for anything a caster leaves behind to fight beside them.

The mark shows to the flowers' owner only, as one winged seed turning above the marked
enemy (`SeedMarkClientEffects`). Everyone sees where a flower is looking: its head turns to
follow its target between shots (re-chosen four times a second), kicks back when it fires and
hangs down while it wilts.

A flower is kept together by `FlowerTurretManager`. Its base (roots, leaves, stem)
is the `nature_seedling` block: no collision, an outline around the stem only, tracked by
`TemporaryBlockManager` so the ground under it is restored. Its head is a `SentryBloomEntity`
that nothing can hit, push or damage and that is never saved; it carries only the target, the
shot count, the wilting flag and where it is mounted (the stem top, or the main stem or
branch of a grown flower, with that head's size), and each client eases the head toward the target itself
(`SentryBloomRenderer`, head meshes authored mouth-up with the pivot at the stem top). Sight
lines are measured from that pivot and seeds leave from the mouth of the turned head.

Full Bloom is the ultimate (stable saved ID `overgrowth`). A wave of petals rolls out from the
caster to ten blocks in about half a second. The caster and every ally within ten blocks at
the cast are blessed for the bloom's twelve seconds, wherever they go afterwards:
Regeneration II (about twelve health with its last lingering seconds, as the oak used to
heal) and two hearts of Absorption. For the same twelve seconds the caster's own spells are
stronger: Seed recovers in 0.6 seconds, not one; Thornbite heals all the damage it deals, up to
two and a half hearts; Sentry Bloom recovers in three seconds, not six; and every flower the
caster owns is **grown**. A grown flower has a bigger leaf base, a taller main stem with its head, and a side branch
with a smaller second head. Each head fires at the single
head's pace, half a beat after the other, so the flower spits twice as many seeds, and the
second head takes a second enemy when one is in sight (the marked enemy always goes to the
first head). A grown flower does not age, and a flower planted mid-bloom unfurls and then
grows too. When the bloom ends each flower is one head again.

The wave is harmless: Full Bloom is a blessing for the party, and Nature's damage comes from
its flowers and its other spells. On grass blocks the wave leaves short grass and wild flowers
behind, as bone meal would; that is the only thing Full Bloom places, and it places nothing on
any other ground. Flower seeds give no ultimate charge while their owner blooms.
`FullBloomManager` owns it.

The earlier ultimate, Overgrowth (a thrown acorn that grows a healing oak with a solid trunk
and walk-through leaves), is archived: no spell casts it, but the developer wants it kept for possible
later use. Its code, blocks, art and fixtures (`OvergrowthManager`, `OvergrowthSeedEntity`,
`AwakenedTreeEntity`, `NatureAbilityHandler.castOvergrowth`) stay in the project and its
fixture keeps running through that kept entry point; do not delete them in a clean-up. Invalid
landings of that acorn restore
charge to the retained casting wand.

Nature has no passive. Verdant Step (lily-pad rafts that let the holder walk on water) was
removed on November 11 at the developer's request: walking on water did not fit the kit. Its
raft block, models and fixture went with it.

Springbloom is Nature's second Technique and fifth spell. It throws a harmless
pod that opens immediately on supported ground into the approved stemless
flower. Grass, ferns, leaf litter and other soft plants do not block placement;
nearby foliage stays in place. Plants covered by the center are restored on expiry,
including both halves of tall plants. The center needs support and clear headroom;
blocked neighboring columns trim the flower instead of rejecting it. A nine-cell
mask controls both the capped visual mesh and the smaller collision/landing area,
and updates when adjacent blocks change. Obstacles, fluids and other spell growth
are left intact; no launch or catch exists on a clipped section. Its shallow yellow cushion is 0.6875 blocks high, with thick basal leaves;
the cushion stays rigid on launch and emits pollen. The cushion is too tall to step
onto, so a grounded player who walks or runs into its side (any movement key held, not
sneaking, within 0.08 blocks of its collision edge) is launched along their facing, at
full strength when sprinting and about three quarters when walking. A player landing from
above is caught without incoming fall damage and launched upward, including hostile
players and last-second pod catches; incoming horizontal movement chooses that direction
and scales the launch up to its cap. The launch is 1.7 up and at most 1.9 across (blocks a
tick). During the flight, movement input steers with ordinary vanilla air control,
as in any fall; normal gravity and collision still apply. On level ground, flights
rise roughly 15 blocks, and a full-speed launch coasts about 21 blocks forward without
steering (the earlier 2.0/1.35 launch rose 20 and coasted 15). A drop to lower terrain can extend the travel.

Each pad is reusable, breakable in one hit, and lasts 160 ticks from opening. Slot 1
can also break an aimed pad with a wand. The 120-tick throw cooldown is keyed to the
player as well as the casting wand, survives death/reconnects, and is not refunded
for failed placement. Each caster can have one active pad. There is no caster-order
or ground-touch chain restriction. Fall protection ends after the next actual
landing; the server tolerates the client ground flag arriving before landing damage.
Falling leaves mark protected flight. Death, disconnect, world changes, flight,
vehicles, fluids and climbing end the flight state. Pad expiry, breaking, support
loss and owner exit restore only that pad's tracked placement. Scheduled checks
remove orphan pads after reload; transient visual entities are not saved.
Owners: `SpringbloomManager`, `SpringbloomRules`, `SpringbloomEntity`,
`SpringbloomBlock`, the Springbloom movement/fall mixins and `SpringbloomRenderer`.

`NatureCombat` sets the mark and owns the per-caster charge window: a Seed or Thornbite hit
gives 3 ultimate charge at most once a second across both, on whichever wand is held, and each
flower seed that lands gives 1. Owners: `NatureAbilityHandler`, `SeedProjectileEntity` (the
caster's Seed and the flowers' smaller one), `SentryBloomPodEntity`, `FlowerTurretManager`,
`FullBloomManager`, `ThornLashEntity`/`ThornLashRules`, `NatureCombat`.
Nature's spells are drawn client-side as "block nature": opaque, lit petals (two stepped slabs),
leaves (three boxes) and pollen kernels from one atlas (`textures/misc/blocknature.png`), ported
from the browser study in `art/nature/blocknature/`. Pieces face the world, not the camera, tumble a
little and vanish by shrinking. Planting throws leaves; a flower's shot puffs pollen and a petal
from the mouth; growth and shrinking shed petals; a wilting head drops pale petals and removal a
last fall of pink ones. A flower's seed trails two small gold kernels a tick; the caster's own
Seed leaves no trail (removed on November 11 at the developer's request, so only the winged pod
flies); hits open into petals and block hits into pollen and leaves. Thornbite's bite puffs pollen. One heal
cue (six sap leaves spiralling up with four gold kernels between them) plays on a player only when
their health rose: Thornbite lifesteal, collecting a flower's banked health, and each player Full
Bloom blesses. The hearts over a flower that holds health are vanilla heart particles, as the
first banking flower used.
Full Bloom bursts petals at the caster, rolls a low ring of 48 petals with leaves to its radius in
its twelve wave ticks (the gold kernels follow the particle setting), then drops three petals off the
caster every 0.4 seconds while it lasts. Springbloom bursts pollen and leaves at launch, sheds a
leaf pair every four ticks until the rider lands, and drops dark leaves when the pad goes. Clients
draw flower seed trails and wilting from the entities; the server sends `BlockNatureEvents` for the
rest. Visuals never decide a hit or a heal; near-lens pieces shrink in first person. Visuals:
`client/blocknature/` (`BlockNatureEffects`, `BlockNatureKit`, `NatureFx`). The old Nature particles
are still registered for the retired oak and `EntangleTracker`, but no live spell spawns them.

`EntangleTracker` still holds the vine-stack rules (slow, root at five, and an entangled
caster's halved spell recovery) and the wrap art, but no spell winds vines now; its live user
is the retired Overgrowth root. The bramble and root-knot blocks and their art remain registered but
no spell places them; a knot left in a world by an older version removes itself.

## Space

Singularity Bolt is a black star with limited initial aim assistance. It cannot
retarget or U-turn and loses guidance through cover or invalid angles. Impact
causes damage without restoring the retired pull/mobility-disruption mechanics.
Blink Rift teleports up to 10 blocks along the caster's aim to the farthest safe
spot, on a 10-second cooldown. It leaves no return point.

Astral Double (`astral_double`, 500 Flux) tosses a harmless, visible orb in a short
arc (about seven blocks on level ground). A supported landing with clear standing
room creates one stationary copy of the caster, using their skin and arm model,
holding a Space wand with violet motes around it. Wall contacts drop the orb to
the floor. Invalid, fluid, unloaded or unsupported landings fizzle and begin a
three-second recovery. A fresh press is required; holding the input cannot
consume the newly formed double.

The double lasts 45 seconds from landing. Every successful Singularity Bolt cast
also launches an echo bolt from the double toward the caster's crosshair hit point,
dealing 70% of the cast bolt's direct and splash damage. Each projectile retains its
own ordinary range, cover collision and limited guidance. The paired damage type lets
both bolts deal damage even on the same tick, while preserving armor, shields, party
protection and Space attribution. It does not copy Hollow Purple or fire autonomously.
The Hollow Necromancer sends an extra skull at a currently visible double at least
eight seconds old (see [Necromancer combat](necromancer-combat.md)).

Recasting within 64 blocks teleports to the double and consumes it. Walls between
the two positions do not prevent a return to a safe destination; arena containment
still applies. Blinking does not remove the double, and the double's return does
not touch Blink's cooldown, so the two can be chained.

A single hostile damaging hit, including Guardian beam, wave, slam, rock and shard
contact, destroys the double in a magical poof. Owner/allied attacks are protected.
Guardian projectiles destroy it without treating it as defensive cover. Destruction,
teleport consumption and expiry start 15 seconds of recovery; the orb's flight and
the live double do not start that timer. The player retains recovery across wand
replacement, death and reconnects. Putting away the wand keeps the beacon, while
death, affinity/loadout/world exit, disconnect, encounter exclusion, loss of safe
support and server shutdown remove it with recovery. The entities are transient;
a saved active marker recovers safely after an interrupted server run.
Owners: `AstralDoubleManager`, `AstralOrbEntity`, `AstralDoubleEntity`, and the
shared Singularity Bolt and Guardian collision paths.

Gravity Well (`gravity_well`, 500 Flux) throws a violet gravity bomb in an arc,
traveling about 12 blocks on a level throw. The first hostile living-entity,
solid-surface or fluid impact creates a stationary well; it neither bounces nor
follows a struck enemy. One bomb/well may exist per caster. A fresh press is
required for both throwing and early collapse; held repeats do neither.

The well lasts four seconds and pulls eligible living targets within four blocks
of its center. Solid cover blocks both pull and burst. It deals no ticking damage.
Recast to collapse early, or let the full duration expire: either begins a
0.4-second buildup with accelerating inward particles, then deals six base damage
with Space level/XP attribution and a stronger inward impulse. The visual burst
explodes outward for 0.6 seconds; that debris is cosmetic and deals no extra hits.
Pull continues during buildup. Repeated recasts cannot restart or duplicate it.
Early collapse trades sustained control time for an earlier burst; it does not
amplify damage.
The caster, allies, owned summons and protected pets are unaffected. Knockback
resistance reduces pull, and bosses take damage without displacement.
Movement, primary fire, Astral Double and Blink Rift remain independent; Hollow
Purple's existing charge lock still prevents manual casts during its commitment.

Recovery begins on impact and lasts 16 seconds, including time spent pulling;
collapse never restarts that timer. The live HUD shows TOSS / PULL plus time,
BURST during buildup, then remaining recovery. Recovery is retained on both the original wand and a
persistent player attachment, preventing replacement-wand resets. Losing a
flying bomb to timeout, unloaded terrain or cancellation costs full recovery.
Death, disconnect, affinity/loadout/world exit, encounter exclusion and server
stop remove the effect without a damaging collapse. Rejoin clears stale active
state and retains recovery; transient bombs are never saved. Putting away the
wand does not cancel a valid well. No blocks are modified.
Owners: `GravityWellManager`, `GravityBombEntity`.

Hollow Purple commits for its complete charge. The player can aim but cannot
walk, blink, or use items; switching/dropping the wand does not cancel or refund
it. Death, world exit, and encounter teardown clean it up without a refund.
The orb erases blocks along its path except in the Hollow Crypt realm, where it
leaves the terrain intact (as do explosions there; see `HollowCryptRealm.keepsTerrain`).
Owners: `SpaceAbilityHandler`, `SingularityBoltEntity`,
`HollowPurpleChargeManager`. Keep arena containment in all teleport paths.

All five Space spells are drawn client-side as "void glass": dark see-through glass pieces
with a bright frame and stars inside, opaque void cores ringed by a corona that faces the
viewer, and streams that fall inward, from one atlas (`textures/misc/blockspace.png`), ported
from the approved browser study in `art/space/blockspace/`. Clients draw bolts, Hollow Purple
orbs, Gravity Bombs and the Astral orb and double from the entities (the bomb's tracked phase
clocks drive the field, buildup and burst); the server sends `BlockSpaceEvents` for bolt hits,
both ends of a blink or swap, a failed blink, poofs, Hollow Purple's charge (and its early end)
and its collapse. Visuals never decide a hit or a teleport. The server moves bolts and Hollow Purple orbs with
`setPosition` once a tick and they have no interpolator, so clients glide the drawn head between
updates (`Glide`) rather than drawing the raw, uneven positions. Stars and spilled shards follow the
particle setting; near-lens pieces shrink in first person. The Astral Double keeps its owner's
skin under a void-glass shell drawn by `AstralDoubleRenderer`. Visuals live in
`client/blockspace/` (`BlockSpaceEffects`, `BlockSpaceKit`, one class per spell); asset ownership
is described in [art and assets](art-and-assets.md).
Targeted test runners are listed in [testing](testing.md).

Bosses implement `WandBoss` (the Guardian, Hollow Necromancer and Storm Roc). Wand spells
do not slow, root, knock back, stagger or interrupt them; they still take normal damage.
That is the whole interface: a boss has no hook for a particular element.
