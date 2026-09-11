# First-person spell clearance — September 10, 2026

The user reported that large Fire, Space and other wand effects briefly filled
the screen as they spawned at eye level. The approved approach preserves the
particle art and full effect size, moves decorative casting bursts forward/down,
and fades nearby effects in first person.

## Changes

- Fire, Space and Wind primary bursts use `SpellCastVisuals.burstOrigin`: 1.6
  blocks forward and 0.3 blocks below the eyes. A collision ray clamps the burst
  just before nearby cover. Actual projectile origins and collision paths remain
  unchanged, including point-blank hits, range, aim assist and damage.
- All five affinities' custom particles use `SpellViewClearance` when rendering.
  Opacity smoothly increases with camera distance and scales with the particle's
  current size. The original lifetime alpha is restored after submission, so
  fading does not accumulate or change animation/lifetime state. Camera and
  particle positions are sampled at the rendered frame.
- Fire's mesh front uses the same fade. Its long crossed ribbons measure distance
  to their full center segment, so a tail passing through the camera remains
  suppressed even when the projectile head is farther away.
- Wind blades and Nature seeds also fade in their entity billboard renderers.
- Third-person views bypass the fade. In first person the rule applies to nearby
  custom effects regardless of caster, including moving into an existing effect.
  Vanilla particles, block surfaces and Stone's overhead rock mesh are unchanged.

No textures, particle counts, projectile sizes, cooldowns, ranges, or combat
behavior were changed. Larger ultimate particles get a wider camera-clearance
zone; their appearance farther away stays intact.

## Verification

Passed `./gradlew clean build` (including the existing Guardian regression suites),
`git diff --check`, `python3 tools/validate_remaining_vfx_assets.py`,
`python3 tools/prepare_guardian_assets.py --check`,
`python3 tools/prepare_guardian_throw_socket.py --check`, and
`unzip -t build/libs/elementalwands-2.2.0.jar`. Production asset counts remain
298 PNGs and 40 particle definitions. No live first-person playtest has been
performed for this change.

Installed in `~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
Build and installed SHA-256 both match:
`6e101d6bebcd6fe7f3a9b4a2b740c03874d7af0edcd9c4d16b5382a3fd9c04e5`. Restart Lunar to load it.

After restarting Lunar, check Fire/Space/Wind primaries while standing still,
sprinting, looking up/down, and casting against nearby walls. Check Nature seeds
and large Stone/Nature/Space effects close to the camera. Compare first and third
person, and confirm point-blank hits still land. Human feedback may warrant
tuning the clearance distances.
