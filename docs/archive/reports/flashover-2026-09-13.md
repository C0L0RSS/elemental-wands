> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

# Flashover and Fire Leap sigil — September 13, 2026

Fire Leap's plain destination circles are replaced with a custom ground sigil:
eight broken rim sections, inward flame runes, a central angular flame glyph,
and rising ember flecks. The faint trajectory remains caster-only. After launch,
nearby clients see the committed sigil. Its outer edge stays at the existing
five-block damage radius; leap movement, targeting, damage and cooldown are unchanged.
The sigil is native geometry that follows terrain/cover and uses the Fire palette.

## Flashover

Flashover is a new Fire Technique, purchased for 500 Fire Flux. It competes with
Fire Leap and Dragon's Pyre for the Technique slot; the player still has one Basic,
one Technique and one Ultimate. Existing purchases and loadouts are preserved.

Use Technique (right mouse by default) to toss an ember. It travels in a gravity
arc and sticks to a solid surface, without dealing contact damage. A small faceted
ember inside charred fragments marks its position; it brightens when armed.

Initial values:

- Maximum three active embers, including airborne/unarmed embers.
- One second between throws; a half-second arming delay after landing.
- Embers expire 30 seconds after being thrown. Water extinguishes them; removing
  the supporting block dismisses them. They do not attach to mobs or players.
- Spell alternate (R by default) detonates all armed embers within 32 blocks.
  Out-of-range and unarmed embers remain. Empty detonation spends no cooldown.
- Detonating starts a six-second recovery before placing/detonating again.
  Recovery lives on the player and survives item swaps, save/reload and death.
- Each explosion has a three-block radius and cover checks. A victim takes one
  combined hit: 6 damage for one blast, 8 for two overlaps, 10 for three overlaps.
  Blasts cause no terrain damage, fire placement, automatic chain reactions or
  extra ignition damage. Damage awards Fire Flux through the shared progression path.

Enemies can disarm an ember with a melee hit or player-fired projectile. A wand's
Basic action also disarms an aimed ember within 3.5 blocks and clear sight.
Disarming never detonates it. Owners can disarm their own embers; allies are
protected against accidentally destroying each other's traps. Party/scoreboard/
PvP protection applies to blast victims and allied pets.

Death, disconnect, world changes or unequipping Flashover clear traps. Putting
away the wand alone retains them, but detonation requires holding the wand with
Flashover still equipped. The entity type cannot save, so traps cannot resume
from old world data. No chunk tickets are created for traps.

## UI and implementation

The Controls page now has a fourth rebindable row, Spell alternate. Existing three
bindings are retained; conflicts swap through the existing binding flow. The HUD
shows active/armed counts and the current detonation binding. The store supplies
price, placement/recovery timing and blast radius. A dedicated pixel icon extends
the approved Fire glyphs.

- `FlashoverRules` owns tuning and the overlap cap.
- `FlashoverEmberEntity` owns flight, anchoring, arming, disarming and expiry.
- `FlashoverManager` owns eligibility, cap, persistent cooldown, grouped damage,
  cleanup and HUD synchronization.
- `FlashoverEmberRenderer` builds the small charred/ember model from native geometry
  and vanilla blackstone, with approved Fire particles.
- `FireLeapPreview` builds the custom terrain-following sigil.

No new or replaced PNGs. The approved Fire, other wand, Guardian and hub raster
assets remain unchanged.

## Verification

The disposable server fixture covers purchase/equip, harmless toss/arming, cap,
melee and wand disarming, cover/allies, one grouped damage hit, persisted recovery,
range rejection, unequip cleanup and expiry. The integrated client fixture covers
Fire Leap hold/cancel/flight/sigil plus Flashover casting, synchronized counts,
alternate detonation and saved Controls-page rebinding. Native screenshots are
in `docs/archive/evidence/fire-expansion-verification/`.

```sh
./gradlew -I tools/flashover_server_smoke.init.gradle runServer
./gradlew -I tools/fire_expansion_client_smoke.init.gradle -PhubClientAssets=/Users/antonlabas/.lunarclient/shared/assets runClient
```

All listed server/client fixtures passed. Native screenshots were inspected;
four binding rows and loadout labels were adjusted to avoid overlap. Final
`./gradlew clean build`, required asset/export validators, `git diff --check`
and JAR integrity passed.

Installed at `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
Source/installed SHA-256: `eb5943d9ac74c10eea9946ee7f0e26983f52c1449e4b0a91f4df52df2f64632c`.
Previous version backed up in `.local-backups/lunar/20260913-122226/`.
Restart Lunar before testing. Human combat balance and multiplayer latency testing
remain pending.
