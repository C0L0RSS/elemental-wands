> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

# Throwable Overgrowth — September 14, 2026

Overgrowth now uses the same throwable acorn with either Nature Basic. A full
100-charge cast launches a harmless, arcing seed. Supported ground grows the
existing oak with its root awakening and Regeneration II healing area.

## Behavior

- Cast the equipped Ultimate (X by default). No flower targeting or planting is
  required. Ownership, charge, global recovery and arena admission remain enforced.
- Initial speed is 1.1 blocks/tick, gravity .05 and drag .99. Aim upward for a longer
  throw or downward for a close placement. The seed expires after six seconds.
- Wall/ceiling contacts stop horizontal travel and drop the seed. Enemy contacts
  drop it just outside the contacted body's trunk footprint, avoiding a permanent
  hole in the tree caused by placing its trunk through the enemy. The projectile
  itself deals no damage; the normal tree awakening supplies the attack.
- The tree lasts **15 seconds** without a flower. At landing, the nearest active
  owned flower within **three blocks** is optionally consumed for **five extra
  seconds**. Only one flower is consumed; other nearby, distant and foreign flowers
  remain. Flowers do not change the throw trajectory or move the chosen landing.
- This bonus changes duration only: the thrown tree's awakening deals the existing
  base 14 damage and retains its existing radius, root effects and Regeneration II.
  It does not add healing strength, tree health or burst damage. The old direct
  manager overloads remain compatible with existing commands/regression fixtures.
- The tree preserves other flowers and root knots when placing its blocks. Consuming
  a bonus flower also ends that flower's linked Tendril Bloom effects, as expected
  from the source ownership introduced earlier today.
- Placement requires loaded, in-bounds, dry ground with a full supporting top face
  and a clear central trunk column. Outer branches retain existing non-destructive
  placement and occupant checks. The seed does not grow a floating or underwater
  tree. Thin/partial support, cramped ceilings, water, border exits and failed
  flight expiry fizzle.
- A failed landing restores 100 charge only if the living caster still holds or
  carries the exact casting wand. It never credits a different/dropped wand.
  Caster death, world exit/disconnect, affinity change or lost arena admission
  cancels an in-flight seed without a refund. Projectile entities are not saved.

## Implementation and visual verification

`OvergrowthSeedEntity` owns synchronized flight, contacts, lifecycle and failed
landing refunds. `OvergrowthThrowRules` owns provisional tuning and placement
checks. `SeedlingManager.consumeNearestForOvergrowth` consumes one owned source
at impact. `OvergrowthManager.startThrownOvergrowth` applies the duration bonus.

`OvergrowthSeedRenderer` renders a stepped woody acorn with a cap and three green
leaves using existing Minecraft textures. Near-camera scaling keeps release clear.
The old flower-only target preview was removed. Store details now describe the
throw and the 15/20-second durations. No raster assets or approved tree model changed.

The disposable server fixture verifies seedless casting and charge spend, grounded
landings, nearest-only flower consumption, preservation of additional/foreign
flowers, identical base/enhanced awakening damage, actual 15/20-second expiry,
wall and mob impacts, harmless projectile contacts, water/ceiling rejection and
refunds, failed-placement flower preservation and affinity cleanup.

The combined native client fixture exercises Thorn Lash purchase/equip/input/heal,
the root knot's three targets and destruction, spell-panel text bounds, and an X-key
Overgrowth throw without seedlings. It captures the synchronized acorn and tree.
Screenshots are inspected separately from automated assertions. Evidence is under
`docs/archive/evidence/overgrowth-throw-verification/`. Human balance/latency playtesting remains open.

```sh
./gradlew -I tools/overgrowth_throw_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/nature_overgrowth_client_smoke.init.gradle \
  -PhubClientAssets="$HOME/.lunarclient/shared/assets" runClient
```

## Release verification and installation

Clean build, Guardian/party contract checks, required asset/export validators,
whitespace, JAR integrity, the Overgrowth server fixture and combined native client
fixture passed. Lunar source/installed SHA-256: `5dc8e17857342cbce9b56df4bc6824187dc81d6580bafd6b8506f65095ab11af`.
Backup: `.local-backups/lunar/20260914-152158/elementalwands-2.2.0.jar`.
Restart Lunar to load this build. Human combat/latency feedback remains pending.
