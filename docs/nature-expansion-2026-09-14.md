# Nature expansion — September 14, 2026

Thorn Lash adds a close-range, lifestealing Basic. Tendril Bloom can now operate
without planted flowers: it grows one stationary root knot that launches three
tendrils. All completed bramble patches now remain linked to their original
flower or knot, so destroying that source ends its effects.

## Player behavior and provisional tuning

- Buy **Thorn Lash** for 500 Nature Flux in H → Spell Store, then equip it in Basic.
  Seed remains the free alternative. Existing spell purchases and loadouts remain.
- Lash sweeps a textured, tapered 3D thorn vine through a 110-degree arc over eight
  ticks. Range is 4.5 blocks, damage 6 per enemy per cast, and cooldown one second.
  Aiming includes pitch. Solid cover blocks contact; allies, pets protected by
  party/team rules, decorative armor stands, and awakened trees are excluded.
- Lifesteal is 25% of actual health removed, capped at 2 health (one heart) across
  the entire sweep. Misses, fully blocked/invulnerable hits, absorption-only hits
  and overkill grant no excess healing. Armor and Guardian reductions apply first.
  Green return particles and a heart cue accompany successful lifesteal. No armor,
  regeneration buff, seed planting, or extra Entangle comes from Lash.
- Lash shares the existing Nature Basic charge window: at most one charge point
  per second across targets. Existing thorn contacts retain their shared window
  and three-point charge limit. Lifesteal does not generate additional charge.
- Tendril Bloom retains its existing purchase and 15-second cooldown. With flowers,
  each flower sends its usual tendril. Without flowers, a custom root knot grows
  at the caster's position or an immediately adjacent clear position with solid
  support (up to two blocks below). Invalid ground reports a message before spending
  cooldown. The knot occupies air only and has no movement collision.
- The knot sends exactly three tendrils, preferring different nearby combat targets
  within 15 blocks. With fewer enemies it reuses targets; with none it fans forward.
  Shared caster/target thorn limits prevent overlapping patches multiplying damage
  or Entangle stacks. It does not follow the caster; caster movement and incoming
  damage do not interrupt it.
- The knot has a visible, targetable outline and low mining hardness. An enemy's
  close-range wand Basic can also break it. Guardian growth-crushing attacks remove
  it through the existing growth-contact path. Breaking its support also ends it.
- Breaking a knot ends all three tendrils and their patches. Breaking a flower ends
  only effects owned by that flower. The source's temporary trails are also restored.
  Removing/replacing bramble cells stops their damage. Source loss is processed by
  the next server tick; explicit flower destruction and Guardian crush invalidate
  the effects immediately.
- A source lives for at most 284 ticks (14.2 seconds); completed blooms retain their
  260-tick maximum, bounded by source lifetime. Sources clear on caster death,
  disconnect/world exit, affinity change, or loss of the equipped Technique.
  Scheduled block checks remove orphan knots after reload. Existing temporary-block
  restoration handles normal server shutdown and preserves underlying terrain.

## Follow-up

Throwable Overgrowth is now implemented in the same day's follow-up; see
[the Overgrowth report](overgrowth-throw-2026-09-14.md). The following paragraph
records the scope of the earlier primary/secondary-only release.

Overgrowth is unchanged in this release and **still requires a seedling**. The
approved next concept is a throwable ultimate seed with consistent placement for
all loadouts; consuming a nearby flower would extend its duration. That change and
the possible draining Technique were not included in the request to implement the
new Basic and updated Tendril Bloom. Human balance and multiplayer feel remain open.

## Implementation

- `ThornLashRules` supplies tuning and shared client/server sweep geometry.
- `ThornLashEntity` owns the short-lived sweep, covered hit checks, single contact
  per target, actual-damage healing and cleanup. `ThornLashRenderer` renders the
  same curve with textured vine segments and stepped wooden thorns.
- `NatureAbilityHandler`, `WandSpells`, `WandLoadouts`, `NatureCombat`, `SpellIcons`
  and the Nature HUD provide casting, purchase/equip, charge and exact spell glyphs.
- `TendrilBloomManager.Source` owns each flower/knot cast's trails and blooms.
  Seedling removal invalidates its linked sources through `SeedlingManager`.
- `NatureRootKnotBlock` provides the outline, mining behavior and orphan check.
  `tools/prepare_nature_expansion.py` deterministically exports its native model:
  78 textured cubes, an exposed green core and three prominent branch sockets.
  Existing production PNGs remain untouched; the standard asset counts still pass.

## Verification

Passed the clean release build, Guardian contract tests, party checks, whitespace,
all required asset exporters/validators, the new knot exporter and JAR integrity.

The disposable server fixture checks purchase/ownership, duplicate-cast cooldown,
front/rear/covered contacts, allied protection, failed-hit and overkill healing,
per-sweep group cap, absence of planting, three-target selection, moving/damaged
caster, early and completed knot destruction, Guardian crush, support loss,
source-specific flower cleanup, affinity exit, expiry and orphan removal.
The existing real Guardian/Nature arena fixture also passed; its direct spell
setup now declares Nature affinity to satisfy source lifecycle validation.

```sh
./gradlew -I tools/nature_expansion_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_nature_smoke.init.gradle runServer --args nogui
./gradlew -I tools/nature_expansion_client_smoke.init.gradle \
  -PhubClientAssets="$HOME/.lunarclient/shared/assets" runClient
python3 tools/prepare_nature_expansion.py --check
```

The native client fixture checks purchase/equip synchronization, mouse-input casts,
Lash entity synchronization and healing, the knot's native block model and networked
removal. Screenshots are inspected separately from automated assertions. Evidence
is retained under `docs/nature-expansion-verification/`. These checks do not replace
human combat/latency playtesting.

## Installation

Installed the verified JAR into the local Lunar Fabric 1.21.10 profile. Source and
installed SHA-256 both equal `3f798e617b4ed5fb386d8c784b4dfe1e0ff4b99bfafed823500a74eae4e1b468`.
Previous JAR backup: `.local-backups/lunar/20260914-145937/elementalwands-2.2.0.jar`.
Restart Lunar to load it. Native visual/input checks passed in the disposable dev
client; the installed Lunar build still needs the user's combat playtest.
