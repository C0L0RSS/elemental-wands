# Nature versus the Guardian, September 9, 2026

The Guardian keeps its committed attacks. Nature earns extra recovery time and
maintains destructible growth instead of pinning the boss indefinitely.

## Combat rules

- Nature slows on the Guardian are capped at Slowness I. Other targets retain
  their existing Nature slows/roots and player wand-cooldown penalty.
- Five Entangle stacks queue one extra second after the current attack completes.
  Multiple casters share that opening. It cannot stack or refresh; after it is
  consumed, the boss resists another opening for ten seconds. Overgrowth's root
  crush can earn the same opening and does not pull an airborne Guardian down.
- Two seconds of sustained thorn damage make the director prefer an available
  slam/shockwave at its next action opportunity. The current attack and earned
  recovery finish first. This preference has an eight-second cooldown and expires
  when thorn pressure stops. Distant visible players do not prevent local clearing.
- Close slams clear growth in their forward 4.5-block arc. Shockwave fist impacts
  and leap takeoff clear within four blocks; leap landings clear within six.
  Height and cover checks apply. The later [wave-cover fix](guardian-wave-cover-2026-09-09.md)
  makes the outward traveling band clear contacted growth while respecting defensive walls/trees.
- Destroying a seedling clears the patch it owns. Hitting a patch away from its
  seedling clears only contacted growth. Destroyed cells stop applying effects;
  distant plants survive. Blooms are independently crushable, and tendrils can be
  broken in transit. Temporary placement receipts retire with cleared Nature cells
  so an old timer cannot erase a replacement plant later.
- Local impacts deal ordinary damage to the ultimate tree (6 for fist impacts,
  10 for landings), respecting external cover and its health. Its own trunk shell
  does not protect its damageable core. Deliberately targeting the tree is deferred;
  this pass adds tree damage to these local impacts, not a new attack/target system.

## Arena spell placement and Fire fix

The arena still rejects normal block-item placement and protects its structure.
Tracked temporary Fire coals can reskin the intact combat floor and restore the
original block. This narrowly scoped write cannot remove floor blocks, modify walls,
or permit spell casts during the cinematic. Protection remains attached to floor
coordinates even while they contain coals. Other spell blocks can occupy the
combat room above its floor.

The batched floor renderer omits tiles occupied by real Pyre coals so the netherrack
surface is visible without two coplanar surfaces. Azalea seedlings accept the arena
floor and Pyre coals as support, surviving subsequent neighbor updates. Secondary
blooms locate their ground surface even under a tall target or on an existing moss
trail.

Fire's buff check recognizes both coals underfoot and actual spell flames at the
player's feet. Fire regeneration and the Nature tree's healing beacon now let the
vanilla Regeneration I timer reach its healing tick. Previously, refreshing a
20/40-tick effect every tick prevented it from reaching the required 50-tick boundary.
The new 60-tick effect refreshes only with ten ticks remaining, retaining the normal
half-heart per 2.5-second cadence; it may linger for up to three seconds after leaving.
Stronger or longer existing regeneration is preserved.

## Verification and playtest

`./gradlew clean build` includes the existing boss/arena/beam/leap/socket checks and
new recovery, immunity, pressure, cooldown, and reset checks.

The optional real-server fixture is:

```sh
./gradlew -I tools/guardian_nature_smoke.init.gradle runServer --args nogui
```

It creates an isolated arena and checks actual Fire placement/buffs/healing, safe
floor restoration/protection, seedling survival/growth, secondary bloom creation,
local clearing/distant preservation, exact extra recovery, tree damage, an
uninterrupted entangled leap, automatic clearing against a distant player, and
arena cleanup. No user save is opened.

The existing client fixture also checks a floor mesh with an occupied spell tile
omitted. Asset/reference validators and JAR integrity remain required.

Human Lunar checks remain open: visible coals without flicker, Nature's recovery
opening readability, thorn placement/clearing tactics, and solo/co-op balance.

## Verified installation

Build/regression checks, the real Nature/Fire arena fixture (including actual
health recovery), client asset/mesh checks, all required asset validators,
whitespace checks, and JAR integrity passed.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-nature-boss-20260909-161152.jar`
- Source/installed SHA-256: `80265ecc4d23218ce93b89b831ab41519d73350bdad770a6044292b487f6f1f1`
- Evidence: `/private/tmp/guardian-nature-verification-20260909/`.

Restart Lunar before testing. These automated results do not establish human
visual approval or final encounter balance.
