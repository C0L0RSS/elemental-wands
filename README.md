# Elemental Wands

A Fabric mod for Minecraft **1.21.10** built around elemental combat, learnable
spells, and customizable wand loadouts. Choose **Fire, Wind, Stone, Nature, or
Space**, unlock abilities, and arrange them across five spell slots.

The **Wizard's Wand** changes with your active affinity, including its animated
3D elemental core. Each element keeps its own learned spells, loadout, Flux, and
level progress when you switch affinities.

**Current version:** `2.2.0`

## Getting started

1. Press **H** or run **`/ew hub`** to open the wand hub.
2. Choose an elemental affinity. The server grants you a wand.
3. Use your starting Basic spell, earn elemental Flux through combat, and unlock
   more spells in the store.
4. Equip your learned spells in the hub and arrange your loadout to suit your playstyle.

The hub includes a Getting Started guide, spell store, loadout management, and
controls. Change your affinity, buy spells, and edit your loadout outside combat
and the arena.

## Five slots for every element

Each elemental loadout has **five freely assignable slots**. Any learned spell
from that element can occupy any slot, including ultimates. A spell can appear
only once in a loadout; unused slots may remain empty.

Basic, Technique, and Ultimate describe spell categories in the store. They do
not restrict where you equip a spell. Fire currently offers six spells to choose
from, Nature offers four, and Wind, Stone, and Space each offer three.

New purchases fill available empty slots without replacing your selected spells.
Switching elements restores that element's saved loadout. Moving a spell between
slots does not reset its cooldown or ultimate charge.

### Default controls

These bindings apply while using the wand and can be changed through the hub's
Controls page. Existing saved bindings may differ.

| Action | Default input |
| --- | --- |
| Cast slot 1 | Left mouse button |
| Cast slot 2 | Right mouse button |
| Cast slot 3 | X |
| Cast slot 4 | Z |
| Cast slot 5 | V |
| Spell alternate, such as detonating Flashover embers | R |
| Open wand hub | H or `/ew hub` |
| Ordinary block interaction | Sneak + right mouse button |

Some spells use held inputs: hold Flamethrower to channel it, or hold Fire Leap
to aim and release to leap. The HUD beside the hotbar shows your equipped spells
and their readiness; empty slots are hidden. Use **Controls > Move HUD** to
reposition it.

## Spell catalog

Unlock spells to build your own combination. Ultimates use a shared charge
reservoir and require full charge to cast.

| Element | Basic spells | Techniques | Ultimate |
| --- | --- | --- | --- |
| Fire | Inferno Wave, Flamethrower | Dragon's Pyre, Fire Leap, Flashover | Meteor |
| Wind | Sky Shear | Waylay Dash | Zephyr Strike |
| Stone | Gathered Mass | Stone Wall | Titan Dome |
| Nature | Seed, Thorn Lash | Tendril Bloom | Overgrowth |
| Space | Singularity Bolt | Blink Rift | Hollow Purple |

### Fire

Send an **Inferno Wave** forward or channel **Flamethrower**, managing its heat as
damage builds. **Dragon's Pyre** sends a ground-following wall of fire.
**Fire Leap** lets you aim a leap and land with a shockwave, while **Flashover**
plants sticky embers on enemies or surfaces for alternate-input detonation.
**Meteor** calls down a huge burning impact.

### Wind

**Sky Shear** releases three cutting crescents in a narrow fan. **Waylay Dash**
provides two independently recovering dash charges. **Zephyr Strike** temporarily
grants wings, launches you into the air, and delivers a landing impact.

### Stone

Aim toward nearby ground with **Gathered Mass** to build a stone reserve, then
throw it outward. **Stone Wall** raises cover that can be recast nearby to shatter
forward. **Titan Dome** creates a temporary stone enclosure with its own combat
and equipment effects.

### Nature

**Seed** strikes enemies or plants growing flowers. **Thorn Lash** sweeps nearby
enemies and restores health from damage dealt. **Tendril Bloom** sends vines from
your flowers, or creates a root knot when none are available. **Overgrowth** throws
an acorn that grows a healing oak; a nearby owned flower extends its duration.
The **Verdant Step** passive creates temporary rafts across water.

### Space

**Singularity Bolt** launches a black star with limited aim assistance.
**Blink Rift** teleports you and leaves a temporary return point.
**Hollow Purple** commits you to a charged release: you can keep aiming while
charging, but cannot move or use other items.

See the [spell reference](docs/reference/spells.md) for detailed behavior and
implementation owners.

## Progression and parties

- **Flux:** each element has its own spendable currency. Its starting Basic is
  free; additional Basics and Techniques cost 500 Flux, and ultimates cost 1,500.
- **Spell books:** grant credits for a free eligible spell choice. The store uses
  eligible credits before spending Flux.
- **Element levels:** separate from Flux and vanilla XP. Levels 1–6 increase spell
  damage by 10% per level above level 1.
- **Guardian encounters:** discover churches and take on their arena encounters
  for progression rewards.
- **Parties:** use `/party` or `/ew party` to group up. Allied protection applies
  to harmful wand effects.

For more, see [progression and controls](docs/reference/progression-and-controls.md)
and [churches and arenas](docs/reference/church-and-arena.md).

## Requirements and installation

- Minecraft `1.21.10`
- Fabric Loader `0.17.2` or newer
- Fabric API for Minecraft `1.21.10` (built against `0.138.4+1.21.10`)
- Java `21`

Place `elementalwands-2.2.0.jar` and the compatible Fabric API JAR in your
Minecraft instance's `mods` folder. Launcher-managed profiles may use their own
instance folder. GeckoLib is bundled with the mod.

## Development

Build the mod or launch a development client from the project root:

```sh
./gradlew build
./gradlew runClient
```

The installable build is `build/libs/elementalwands-2.2.0.jar`.
Dependency and release versions are maintained in `gradle.properties` and
`build.gradle`.

Start with [AGENT.md](AGENT.md) for repository working rules, required checks, and
local installation guidance. The [documentation index](docs/README.md) links to
current system references, [outstanding work](docs/STATUS.md), and
[release summaries](docs/releases/README.md). Historical reports live in the
[archive](docs/archive/README.md).

## License

MIT — see [LICENSE](LICENSE).
