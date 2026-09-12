# Nature flower-focused balance — September 11, 2026

The user found direct seed spam more effective than planting during a full Guardian
fight, and thorn ticks charged the ultimate too quickly. They approved moving damage
and Entangle into flowers, limiting charge across patches/targets/wand swaps, and
doubling the tree's healing.

## Current tuning

| Source | Damage / effect | Ultimate charge |
| --- | --- | --- |
| Direct seed | Flat 3 damage; does not add or refresh Entangle | 1 per successful hit, at most once per 20 ticks per caster |
| Flower or secondary brambles | 3 / 3.5 / 4 / 4.5 / 5 damage at Entangle stacks 1–5 | 3 on successful damage, at most once per 20 ticks per caster across all targets |
| Tree awakening | Existing 14–18 burst and 30-tick ordinary root | 0; Arcane Flux remains |
| Tree healing | Regeneration II: 1 health every 25 ticks, about 12 health / 6 hearts during 15 seconds in range | 0 |

`NatureCombat` shares a 20-tick contact window keyed by caster and target UUID.
Flower and secondary contact use the same window for both damage and new Entangle
stacks. Overlap cannot multiply damage or instantly stack a target. Other enemies
still take their own damage. Normal Minecraft damage protection is retained.

Charge windows are separately keyed by caster UUID for seeds and thorns, independent
of wand identity, target, patch, or dimension. Swapping wands cannot reset them.
Only accepted damage gives rewards; a held wand is needed to receive charge. Arcane
Flux still accrues on every accepted damage event, even if its charge window is shut.
Old windows expire after 20 ticks, and all state clears on server shutdown. Other
affinities retain the existing five-charge reward through the default overload.

At ideal sustained contact, flowers provide three charge per second and seeds can
add one. A 100-point ultimate requires 34 flower awards, 25 combined awards, or 100
seed awards. These are award counts; travel, initial growth, tick phase and misses
affect elapsed combat time. No planting or healing action awards charge by itself.

The following are retained: primary and secondary cooldowns, five-flower limit,
30-second flower lifetime, growth radii, secondary targeting/travel/lifetime,
ultimate cost/radius/health/lifetime, regeneration range, caster self-slow, ordinary
root duration and Guardian resistance/earned recovery. Fire remains Regeneration I.
The tree continues to reject thorn damage and Entangle visuals. No assets changed.

## Verification

The disposable-world balance fixture is available with:

```sh
./gradlew clean build -I tools/guardian_nature_smoke.init.gradle -PnatureBalance runServer --args nogui
```

It checks actual seed damage with and without stacks, flower damage progression,
overlap independent of vanilla immunity, multiple enemies, swapped wands, separate
casters, 20-tick boundaries, rejected hits, Arcane Flux retention, unchanged ultimate
burst with no refund, the tree exclusion and actual health restored over 15 seconds.
The regular Nature/Guardian fixture remains available without `-PnatureBalance`.

Passed September 11: clean build and all eight Guardian contract suites; the new
real-server balance fixture; the real Guardian/Nature arena fixture; all required
VFX, Guardian asset/socket and Nature model validators; diff whitespace and JAR
integrity. The packaged JAR contains NatureCombat and excludes the test fixture.
Logs and pass markers are in `build/nature-balance-evidence/`.

The fixture selector now declares its mode as a build input: an initially cached
fixture package reran the balance test when the arena test was requested. The
corrected arena package was inspected and then passed the arena test independently.

## Installation

Installed `build/libs/elementalwands-2.2.0.jar` to the Lunar Fabric 1.21.10 mods
folder. Source and installed SHA-256 both verified as
`3fea911b49cc56325faf07cacc0f24651c82682aad53281f2618a8c173b4e490`.
Restart Lunar to load it. Human fight balance remains to be tested by the user.
