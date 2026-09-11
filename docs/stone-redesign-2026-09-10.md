# Stone natural-gray redesign

## Latest weathered revision — September 10, 08:54

The user reported that the detail pass looked identical. Its grain was too subtle
to materially change the visual read. This revision adds explicitly authored
overlapping spike facets, broken wall courses, irregular dome rims and stone
ledges, blade fractures and directional armor plate shading. It also strengthens
the grain contrast. Geometry, alpha silhouettes, sizes and combat remain unchanged.

Fresh comparison: `art/stone/weathered-detail/before-after.png`, using the exact
initial approved gray PNGs as the baseline. No preview file was reused. This is
an actual asset/flat-UV review; Minecraft lighting remains untested by a human.

Passed clean build, existing Guardian regressions, VFX/Guardian/socket validators,
whitespace and JAR integrity. All 41 PNGs match the deterministic generator and
packaged JAR; all sizes/alpha channels match the initial gray pass. No smoke fixture
classes are packaged. Installed with matching source/destination hashes:

- SHA-256: `610f226d20282c1260769590059ca5725ffa6bec6eca45d982ee102e7299826a`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-stone-weathered-20260910-085420.jar`
- Destination: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`

## Earlier subtle refinement — September 10

The user liked the new design and asked for less simple, more Minecraft-like
textures. The refinement retains its shapes and palette, replacing rectangular
flat patches with original irregular pixel clusters, small mineral pits and
varied bevel shading. Actual locally installed Minecraft stone, andesite and
stone-brick textures were inspected as density references; vanilla artwork was
not copied into the custom assets. The editable native generator remains the source.

All 41 Stone textures have refined detail with identical dimensions and alpha
channels. Wall/ready textures share the same grain seed. Particle families use
stable grain rather than random per-frame noise. The approved primary remains
10 damage / 30 ticks; gameplay and geometry were not changed by this follow-up.

Review: `art/stone/minecraft-detail/before-after.png` compares this refinement
with the approved initial gray pass. The accompanying contact sheet shows all
production assets. These are texture/UV previews, not in-game visual validation.

Passed clean build and existing Guardian checks, VFX audit, Guardian asset/socket
checks, whitespace and JAR integrity, deterministic texture regeneration, and
exact packaged-source equality for all 41 PNGs. Verified all alpha channels match
the initial gray design and no smoke fixture is packaged.

Installed September 10 after backup; restart Minecraft through Lunar to test:

- Source/installed SHA-256: `0af0b14dc6dabba838b739e6e20bef8edc67306d0d92431de0edeeb2f2bce2c2`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-stone-detail-20260910-084858.jar`
- Destination remains `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.

## Initial gray pass

Implemented September 9, 2026 (evening, America/New_York). The user selected
natural gray stone with subtle mineral highlights and approved a primary buff to
10 damage with a 1.5-second cooldown.

## Result

- All 41 active Stone PNGs updated: five block textures, the sword, two armor
  atlases, three ability icons, and 30 particle frames. Retired Stone wand/ore
  assets are unused and are not part of this runtime family.
- Spikes use fractured vertical rock facets. Wall blocks use broad offset slabs;
  their ready state adds cracks over the identical base pattern. Dome blocks have
  one dressed stone face each. Texture sizes and model geometry are unchanged.
- Armor UV faces have coherent plates, a deliberate visor, shoulder caps,
  articulated joints, knees and boots. Sword has a chipped honed edge and charcoal
  grip. Titan particles now show separate assembling/crumbling stone fragments.
- Runtime particle tints and Stone HUD accents use pale neutral/mineral colors.
  Old brown tints would have discolored the new gray artwork in-game.
- Primary damage changed from 6 to 10, cooldown from 55 to 30 ticks. Existing
  one-hit-per-target-per-cast tracking, range, hit volume and knockback remain.
  The HUD already reads the same handler cooldown. No boss-specific bypass added.

The old primary's ideal base throughput was 6 / 2.75 = 2.18 damage/second,
compared with Fire's 8 / 1 = 8 before burning. The new Stone figure is 10 / 1.5 =
6.67. These are uninterrupted-cast arithmetic, not measured fight DPS. Guardian
guard/exposure multipliers, hit admission, range and movement still affect combat.

## Review artifacts and editable sources

- `art/stone/natural-gray/before-after.png`: actual production textures and flat
  front/back vanilla armor UV projections. This is not a Minecraft screenshot.
- `art/stone/natural-gray/contact-sheet.png`: all 41 production textures.
- `art/stone/natural-gray/concept.png`: built-in imagegen art-direction reference.
  Its illustrated geometry is conceptual; runtime geometry was not changed.
- `tools/generate_stone_vfx_assets.py`: editable native pixel-art source. Use
  `--replace` only to intentionally replace this Stone family.
- `tools/preview_stone_assets.py`: rebuilds the before/after review using the
  recorded pre-redesign Git revision for its baseline.

## Verification and installation

Passed clean Gradle build (including existing Guardian regressions), whitespace
check, 298-PNG/40-particle VFX audit, Guardian asset and throw-socket checks, and
JAR integrity. All 41 Stone resources in the release JAR exactly match the source
PNGs; no smoke fixture classes are packaged.

Installed in Lunar after backing up the previous JAR:

- Destination: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-stone-redesign-20260909-230450.jar`
- Source and installed SHA-256: `ef7c2b392c19e7444dae1cef99fdad98cd5f4cbc3de914598cb9e07307ac01cf`

Restart Minecraft through Lunar to load the changes. Human visual and combat
validation remain pending: inspect all three spells and armor/sword in third
person, then compare primary responsiveness and Guardian guard-breaking. Static
UV review does not establish appearance under game lighting or full-fight balance.
