# Approved Fire redesign — September 12, 2026

Implemented the user's approved workshop 05. Original artwork uses Minecraft
fire/lava colors without making the spells out of repeated vanilla fire tiles.

## Result

- Primary: four-frame original stream art, three velocity-aligned planes and a
  perpendicular curl. Existing first-person fades and safe muzzle origins remain.
- Primary ground fire: ordinary Minecraft fire models, textures and height on
  the existing temporary fire block. Its contact damage/lifetime and lack of
  uncontrolled vanilla spread are preserved.
- Dragon's Pyre: a synchronized, oriented custom wall about 2.8 blocks tall moves
  with the existing 40-block scheduler. The remaining floor uses the approved
  original animated carpet and tiny flame curls, capped at 0.075 block. The wall
  does not add collision or damage. It is nonpersistent, noninteractive and
  discarded when propagation ends; a lifetime bound handles orphan cleanup.
- Maximum Meteor: the approved eight-block rounded half-block-step model, 1,248
  exposed faces, animated all-fire material, 46 surrounding fire strips, 11 crown
  strips and 78 local ember sprites. It renders only the internal meteor falling
  block; ordinary falling blocks delegate to the vanilla renderer. The bottom of
  the visible body aligns with the existing projectile's collision foot.
- Existing cooldowns, damage, range, explosion power, ground-fire/contact rules,
  Pyre buffs and temporary-block ownership are preserved. The meteor's visual
  size is larger than its unchanged falling-block collision volume. Its existing
  explosion creates regular vanilla ground fire.
- Fire Spirit, other affinities and the retained Fire HUD icons are unchanged.

## Asset ownership

`tools/prepare_fire_assets.py` exports the approved workshop atlas cells and exact
meteor mesh, using nearest-neighbor sizing and the browser's 0.5 alpha cutoff.
`generate_fire_vfx_assets.py` now forwards to that exporter, preventing accidental
regeneration of the old design. `--check` detects production drift.

Fire now owns 45 PNGs: 28 particle frames, eight primary frames, four meteor
surface frames, two animated ground sheets and three retained icons. There are
264 affinity PNGs, two shared presentation PNGs and 40 particle definitions.
The validator was updated to the approved contract; superseded Fire frames/models
were removed. Other resource families were preserved.

## Verification

Passed clean Gradle build and all existing Guardian regressions; exact Fire asset
export check; all-affinity VFX validator; Guardian asset/socket and Nature model
checks; whitespace and JAR integrity. All 56 exported Fire resources match the
packaged JAR byte-for-byte. No smoke-test classes are packaged.

The isolated real-server audit fixture also passed the new Fire checks: exactly
one wall, movement with the scheduler, correct floor height, no persistence or
interaction, removal at propagation end, and original runway restoration. Existing
meteor non-destructive spawning and the other audit checks passed alongside it.

The isolated Minecraft client loaded the resources and both new renderers. Its
checks verified baked vanilla/low-fire models and actual vertex buffers at six
animation times: 5,988 meteor vertices in at most 13 submissions, 24 Pyre wall
vertices, finite coordinates and bounded dimensions. It used the existing Lunar
asset cache without opening a saved world. Evidence excerpts are in
`art/fire/validation-2026-09-12/`.

These checks do not replace a human gameplay/visual playtest. Lunar shader packs,
camera feel, multiplayer appearance and fight balance still need player feedback.

## Installation

Backed up the previous JAR and atomically installed the verified build. Restart
Minecraft through Lunar to load it.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-fire-workshop-20260912-092700.jar`
- Source and installed SHA-256: `308c31471783425146f3a271992086e74082e0ba0d49c3d9e153bb2ced6c803c`

The browser at <http://127.0.0.1:8346/> remains the approved visual reference, with
previous-art comparison snapshots rather than live game footage.
