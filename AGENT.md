# Elemental Wands — agent guide

Read this guide at the start of repository work. Then read only the reference
relevant to the task; the docs archive is optional history, not required context.

## Working rules

- Prioritize the requested implementation and relevant verification. Do not
  substitute planning documents or trackers for the requested work.
- Preserve unrelated work already in the checkout. Inspect the current diff
  before editing; never discard changes just to make a build or cleanup easier.
- Keep gameplay decisions and validation on the server. Client previews and UI
  do not authorize casts, purchases, damage, or world edits.
- Current code determines implemented behavior. If a guide conflicts with it,
  inspect the implementation and correct the relevant guide within the task.
  Do not restore historical behavior merely because an old report describes it.
- Read, create, or update planning documents and trackers only when requested.
  Update affected current reference facts in place when behavior changes.
- Keep release notes brief and player-facing. Do not append session diaries,
  old release hashes, test dumps, or implementation inventories to this file.
- Distinguish build checks, native Minecraft tests, and human Lunar playtests.
  A successful build or browser preview does not establish in-game appearance.

## Where to read next

[Documentation index](docs/README.md) routes tasks to these maintained references:

| Task | Read |
| --- | --- |
| Open issues and outstanding playtests | [Current status](docs/STATUS.md) |
| Controls, loadouts, progression, networking | [Progression and controls](docs/reference/progression-and-controls.md) |
| Spell behavior and cross-element rules | [Spells](docs/reference/spells.md) |
| Wand geometry, palettes, animation | [Wand rendering](docs/reference/wand-rendering.md) |
| Pixel style, source assets, exporters | [Art and assets](docs/reference/art-and-assets.md) |
| Guardian attacks and animation | [Guardian combat](docs/reference/guardian-combat.md) |
| Church, arena, recovery, rewards | [Church and arena](docs/reference/church-and-arena.md) |
| Test runners and verification boundaries | [Testing](docs/reference/testing.md) |
| What changed for players | [Release summaries](docs/releases/README.md) |

Historical reports are indexed under [archive](docs/archive/README.md). Read them
only for a particular old decision, bug, migration, or test result. Old numbers,
controls, installation hashes, and “pending” statements are not current guidance.

## Project map

Fabric mod for Minecraft 1.21.10, Java 21, with GeckoLib 5.3-alpha-3.
`gradle.properties` and `build.gradle` own dependency and release versions.

One item, `fractured_wand` (Wizard's Wand), dispatches through `UniversalWandItem`
using the player's persisted `WizardAffinity`: NONE, FIRE, WIND, STONE, NATURE,
SPACE. NONE has the fractured beam. Crystal crafting/ore progression and Ice
are retired; do not use legacy player/design documents as current requirements.

Java sources are under `src/main/java/com/anton/elementalwands/`:

| Location | Responsibility |
| --- | --- |
| `ElementalWandsMod`, `ElementalWandsClient` | Common/client registration |
| `item/` | Shared wand and per-element ability handlers |
| `data/` | Attachments, affinities, stable spell IDs, levels |
| `util/` | Timed effects, progression, loadouts, temporary blocks |
| `entity/` | Projectiles, custom creatures, Guardian combat rules |
| `client/` | Controls, screens, HUD, renderers, wand models |
| `network/` | Validated C2S requests and S2C state |
| `arena/`, `church/` | Encounter lifecycle and saved-world recovery |
| `party/`, `command/` | Allied protection and party commands |
| `registry/`, `mixin/` | Registration and focused Minecraft integration |

Runtime assets are in `src/main/resources/`. Editable artwork and generators
live in `art/` and `tools/`. Test helpers are in `src/guardianTest/`; they are
separate from the shipped mod. See the testing guide before launching a fixture.

## Build and required checks

```sh
./gradlew build
./gradlew runClient
```

Run `./gradlew build` after code changes. It includes Guardian and party contract
checks; those do not launch a live gameplay session. For VFX changes, run:

```sh
./gradlew clean build
git diff --check
python3 tools/validate_remaining_vfx_assets.py
python3 tools/prepare_fire_assets.py --check
python3 tools/prepare_guardian_assets.py --check
python3 tools/prepare_guardian_throw_socket.py --check
python3 tools/prepare_guardian_pedestal.py --check
python3 tools/prepare_nature_expansion.py --check
node tools/prepare_nature_models.mjs --check
node tools/prepare_springbloom.mjs --check
python3 tools/prepare_wand_hub_assets.py --check
python3 tools/prepare_wand_assets.py --check
unzip -t build/libs/elementalwands-2.2.0.jar
```

Run the relevant server/client fixture when changing behavior it covers. Do not
claim a fixture passed from an old report. Documentation-only changes need link,
reference, and whitespace checks; they do not require a gameplay build.

## Delegating tests and gameplay recordings

The user prefers one **GPT-6 Sol (`gpt-6-sol`) subagent** for established test and
recording work together when the main agent has useful independent work, such as
reviewing the implementation or updating documentation. This is standing project
authorization for that bounded delegation; no new confirmation is needed.
Use medium reasoning and a short, fresh handoff instead of the full conversation.

The main agent owns design, production changes, unexpected gameplay bugs, and
final review. The subagent runs the agreed checks, verifies receipts, records
gameplay, encodes the MP4, and returns concise results. Escalate unexpected
failures rather than weakening assertions or repeatedly debugging independently.
Do not spawn an agent just to encode a video or wait on a command. If delegation
is unavailable or there is no useful parallel work, run the workflow locally.
This preference does not authorize installation into Lunar or concurrent edits
to the code being tested. Follow the [test and recording handoff](docs/reference/testing.md#test-and-recording-handoff)
for commands, shared-workspace coordination, and deliverables.

## Local installation

Build output: `build/libs/elementalwands-2.2.0.jar`.
Verified Mac destination:
`~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.

When installation is requested or already authorized, back up the previous JAR,
copy the verified build, and compare source and installed SHA-256 values:

```sh
shasum -a 256 build/libs/elementalwands-2.2.0.jar "$HOME/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar"
```

Require matching hashes before reporting installation. Restart Lunar to load the
replacement. Never infer the active build from an old documentation hash.
The previously recorded Windows location is
`%USERPROFILE%\.lunarclient\profiles\lunar\1.21\mods\fabric-1.21.10\elementalwands-2.2.0.jar`;
verify that machine's profile before using it.

## Changing abilities safely

1. Locate the stable spell in `WandSpells`, its handler, and any owning manager.
2. Preserve server-side ownership, affinity, cooldown, charge, combat-lock,
   party protection, and encounter-admission checks.
3. Use the shared cooldown and ultimate-charge helpers in `AbstractWandItem`.
   Preserve the per-spell cast context; categories do not identify equipped slots.
4. Preserve damage/Flux/charge attribution and `SpellCombat` level/XP handling.
   Delayed effects must retain their source element and owner.
5. Register new ticking managers in common initialization and any new entities,
   assets, payloads, and client renderers in their appropriate registries.
6. Cover cancellation, death, disconnect, world exit, server stop, and reload
   where applicable. Temporary world changes must preserve existing ownership
   and restoration; see the relevant lifecycle reference.
7. Build and run the relevant checks, then update current facts in place.

For a new element, extend `WizardAffinity`, handler dispatch, spell metadata,
HUD/theme handling, language entries, and assets. The initial picker follows the
available affinities; inspect existing migration paths before changing saved IDs.

## Previews and documentation upkeep

Browser previews belong under ignored `.local-previews/`; do not commit generated
preview pages. Keep reusable model/texture sources and generator templates tracked.
Extract production art data from a preview before treating the page as disposable.
Runtime resources, not browser pages, enter the mod JAR.

Maintain one current explanation per topic. Keep unresolved work in `docs/STATUS.md`
and remove it when resolved. Add a short release summary for meaningful player
changes; do not make a report for every routine tweak. Preserve detailed reports
only when they explain a significant decision, regression, migration, or test.
Keep evidence selectively: useful final views and bug proof, not duplicate images
or regenerable log dumps. Check links and asset ownership before deleting files.
