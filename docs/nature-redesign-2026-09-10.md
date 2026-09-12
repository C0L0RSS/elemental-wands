# Nature redesign — September 10, 2026

**Current balance:** the approved [September 11 flower-focused pass](nature-balance-2026-09-11.md)
supersedes the damage, direct-hit Entangle, charge and healing numbers below.
The sections below record the redesign and its original playtest tuning.

## September 11 playtest follow-up

The user completed a full Guardian fight with Nature and liked the result. The
reported bug was attached thorn/vine wraps appearing on the ultimate tree's
damageable living hitbox. AwakenedTreeEntity now rejects Nature Entangle stacks,
Nature slowing, ultimate root tracking and periodic thorn damage; the attached
vine renderer also excludes it. Normal attacks still damage the tree.

The Nature real-server fixture now tries all four effect paths on an actual
summoned tree and checks zero stacks, no slow, no root tracking and unchanged
health. Its existing Guardian impact check still verifies the tree is damageable.

Verification passed September 11: clean build and all Guardian contract checks;
real-server Nature fixture including the new tree regression; all required VFX,
Guardian asset/socket and Nature model checks; diff whitespace and JAR integrity.
The fix has not received a new human client playtest. Installed to Lunar Fabric
1.21.10 with source and installed SHA-256 both verified as
`546af8acd617e1ec950944fac2f6d08b027d835c8c52e17a8b2083e182d38628`.
Restart Lunar to load it. Earlier installation hashes below are historical.

### Ability reminder before the approved balance pass (September 11)

- Verdant Step: holding the wand grows temporary walkable leaf rafts over water.
- Primary (right click, one-second cooldown): ground hits plant flowers, up to
  five for 30 seconds. Direct living-target hits add Entangle before dealing
  6.75/8/9.25/10.5/11 damage at stacks 1–5; they do not plant a flower.
- Ground thickets spread to a three-block radius. Standing on tracked growth
  applies Slowness IV and adds one Entangle stack plus 1.5 thorn damage per second
  per patch, subject to normal damage immunity. The caster receives only a mild
  self-slow, without own thorn damage or stacks. Ground zones can affect other
  living mobs/players, including passives and teammates; secondary auto-target
  filtering is narrower than the zones' contact effects.
- Attached vines indicate Entangle, not a separate damage-over-time effect.
  Direct-hit stacks 1–4 apply increasing Slowness; five roots ordinary targets
  for two seconds, refreshed by further stacks. Stacks clear five seconds after
  the last application; slowing can expire earlier. Entangled players recover
  wand cooldowns at half speed.
- Secondary (sneak + right click, 15-second cooldown): each flower sends vines
  toward its nearest eligible target within 15 blocks. Arrival creates a vine-only
  bramble patch, radius three and lifetime 13 seconds. Source flowers have their
  remaining life capped at 15.5 seconds after the cast.
- Ultimate (X, full charge, aim at a flower): consumes all active flowers and
  summons a 60-health tree for 15 seconds. Its ten-block radius grants caster
  Regeneration I and makes flowers spread three times faster, up to radius six.
  The initial burst deals 14–18 damage depending on flowers consumed and roots
  ordinary targets for 1.5 seconds. The tree has no continuous damage aura by
  itself; plant fresh flowers for sustained damage.
- Guardian: Nature slowing is capped at Slowness I. Five stacks or the ultimate
  earn one extra recovery second after an attack, with a shared ten-second
  cooldown. Committed attacks/leaps continue. Sustained thorns encourage a
  growth-clearing slam. Damage figures above are base values before mitigation.

The user approved workshop revision 04 for implementation and in-game testing.
The tree shape remains open to later playtest feedback. Preview source is
`art/nature/workshop/`; it remains available at http://127.0.0.1:8342/ while its
local preview server is running.

## Implemented appearance

- Primary: a rotating 3D winged seed, exported from the approved preview, with
  size-aware first-person clearance. Projectile trajectory, collision origin,
  damage, entangle and cooldowns retain their existing rules.
- Planted anchor: a custom, vivid green plant with layered pink/violet petals and
  golden pollen. Four native block models unfold over 30 ticks. The finished
  flower head stays below one block, its small petal overhang stays within the
  existing targeting margin, and its physical collision is
  the previous azalea collision. Shooting the anchor and ultimate targeting
  continue through SeedlingManager.
- Thickets: low, stepped paired roots, wooden thorns and green leaf sprigs replace
  moss carpet. Water growth and Verdant Step use custom floating leaves.
  These are ordinary synchronized temporary blocks, with no block entities.
- Secondary: root blocks advance along the existing path; leaf/vine particles
  mark the traveling head. The destination spreads brambles and wraps affected
  targets. There is no second flower, flower crown or flower particle at the
  secondary destination. Its area damage and consumption timing are retained.
- Entangle: persistent, chamfered rectangular vine meshes attach to interpolated
  target positions. Human, wide-mob and Guardian templates scale to the target.
  Vines grow upward when first applied, add strands with stacks, and keep their
  growth progress through refresh packets. Active roots add foot anchors; these
  retract when only lingering stacks remain. The first-person local player uses
  the existing edge HUD instead of geometry through their camera.
- Guardian: never receives a full-root visual timer. Resisting vines have no sap
  glints; its actual extra recovery second lights the opening mesh. A tracked
  recovery start synchronizes this cue without altering attack timing or immunity.
- Ultimate: the exact 169-cell workshop tree, 9 blocks tall and 11 blocks across,
  with four outstretched limbs. It uses ordinary oriented oak logs and persistent
  oak leaves, one custom heartwood block and nine flowering leaf blocks. Existing
  four-stage growth and temporary restoration remain. Placement respects loaded
  chunks, build bounds, existing solid terrain and living occupants. The visible
  heart effects now align with the lower heartwood block.

The ultimate retains its 15-second lifetime, 10-block radius, 14–18 summon damage,
30-tick ordinary-mob root, 60-health damageable actor, caster regeneration and
seedling amplification. Its root-only visual does not create Entangle stacks or
slow a wizard's cooldowns. Unrelated high-level Slowness does not create Nature
vines. Guardian damage, recovery and shared immunity remain.

## Assets and integration

`node tools/prepare_nature_models.mjs` exports from the workshop. `--check` verifies
exact generated-file consistency. Block models use native Minecraft cuboids with
face colors and Minecraft's existing pixel-grain textures. No production PNG was
added or changed; the established 298-PNG / 40-particle package contract remains.
Nature's existing sprites remain available for restrained supporting particles.

Five internal blocks have no items, recipes or drops: `nature_seedling`,
`nature_roots`, `nature_raft`, `nature_heartwood`, `nature_flowering_leaves`.
Guardian waves and Stone throws treat the first three as low growth, while the
ultimate's wood/leaves remain cover. TemporaryBlockManager recognizes custom
Nature growth during cleanup. Managers retain server authority over damage areas.

Custom render data lives under `assets/elementalwands/nature/`, outside Minecraft's
block-model parser. The client loads 25 bounded mesh variants once. World rendering
extracts immutable snapshots and batches vines by material, with frustum/distance
culling and near-camera fading. Death, entity unload, dimension changes, disconnect,
root expiry and stale-state expiry clear the relevant visuals.

## Verification

Required clean build, existing combat regressions, asset inventory, Guardian asset
and throw-socket checks, Nature export consistency and JAR integrity all passed
for the release. The server fixture additionally verifies custom water growth,
walkable rafts, Verdant Step, exact water restoration, arena planting and thickets,
Guardian clearing and resistance, recovery timing, uninterrupted leap, damageable
tree, the approved branch/heart/top positions, and cleanup of the custom tree.

Client verification loads Minecraft's real assets, checks all eight block states
and their face palette, validates 25 meshes and partially grown buffers, and checks
root-only/refresh expiry. An isolated integrated-world fixture then renders the
actual blocks and networked pig, spider and Guardian wraps and saves four game
screenshots. It does not modify existing player worlds or Lunar settings.

Reproduce the integration checks:

```sh
./gradlew -I tools/guardian_nature_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_floor_client_smoke.init.gradle \
  -PfloorClientAssets=/Users/antonlabas/.lunarclient/shared/assets -PnatureVisual runClient
```

Screenshots are preserved in `art/nature/in-game-2026-09-10/`. Overlapping leaf
faces use a tiny stable depth separation to prevent water-raft shimmer.

The integrated-world shots verify appearance and rendering, not a human fight.
Human testing still needs to assess tree shape in real terrain, aiming and moving
through dense growth, first-/third-person comfort, multiplayer readability and
combat balance. The web preview is the design reference; the screenshots are
actual Minecraft views.

## Installation

Installed to `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
Source and installed SHA-256 both verified:

`77f30d95be560f25eacaff6fafb77eb46db0058557b1c86be844e2401b3c66b0`

The final clean build, all required asset checks and package parity passed.
Server and actual client evidence is preserved beside the in-game screenshots.
Restart Lunar Client to load the replacement mod.
