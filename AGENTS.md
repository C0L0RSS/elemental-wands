# Elemental Wands — agent guide

This is the starting guide for every agent working in this repository. `CLAUDE.md`
imports it, so keep shared instructions here and nowhere else. After this guide, read
only the reference your task needs; the archive is optional history.

## Finding code

**Before searching the source, look the feature up in the [feature index](docs/INDEX.md).**
It has one row per spell, boss, realm and system, naming the files that own it and the
reference that explains it. If the index is wrong or missing a row, fix it in the same
task; `./gradlew checkDocs` fails when it names something that does not exist.

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
- Do not create new planning documents or trackers unless asked. Keeping the existing
  ones true is part of every task: when behavior changes, update the affected reference
  and feature index row in place, add a status bullet for anything left open, and
  delete the bullets your work settles.
- Do not append session diaries, old release hashes, test dumps, or implementation
  inventories to this guide, the status page or the roadmap. The
  [documentation rules](docs/README.md) say where each kind of note goes.
- Distinguish build checks, native Minecraft tests, and human Lunar playtests.
  A successful build or browser preview does not establish in-game appearance.
- Mute automated Minecraft test clients before loading a world, including hidden
  screenshot/recording runs. Set master volume to zero in the disposable test
  client's options; do not change system audio or the user's Lunar settings.

## Where to read next

| Task | Read |
| --- | --- |
| Which files own a feature | [Feature index](docs/INDEX.md) |
| Open issues and outstanding playtests | [Current status](docs/STATUS.md) |
| Long-term direction and planned features | [Roadmap](docs/ROADMAP.md) |
| Controls, loadouts, progression, networking, parties | [Progression and controls](docs/reference/progression-and-controls.md) |
| Spell behavior and cross-element rules | [Spells](docs/reference/spells.md) |
| Wand geometry, palettes, animation | [Wand rendering](docs/reference/wand-rendering.md) |
| Pixel style, source assets, exporters | [Art and assets](docs/reference/art-and-assets.md) |
| Sound events, naming, replacing audio | [Sound](docs/reference/sound.md) |
| Guardian attacks and animation | [Guardian combat](docs/reference/guardian-combat.md) |
| Hollow Necromancer boss | [Necromancer combat](docs/reference/necromancer-combat.md) |
| Hollow undead night mobs | [Hollow undead](docs/reference/hollow-undead.md) |
| Churches, the Shattered Nave, the Hollow Crypt, recovery, rewards | [Church and arena](docs/reference/church-and-arena.md) |
| Storm Roc's fight, summit realm and aerie | [Storm Summit](docs/reference/storm-summit.md) |
| Pyre King's fight, body, clips, gate and hall | [Pyre King](docs/reference/pyre-king.md) |
| Why a built boss is designed the way it is | Design notes: [Storm Roc](docs/design/storm-roc.md), [Pyre King](docs/design/pyre-king.md) |
| Test runners and verification boundaries | [Testing](docs/reference/testing.md) |
| Viewing local review pages from another device | [Remote preview](docs/reference/remote-preview.md) |
| What changed for players | [Release summaries](docs/releases/README.md) |
| Editing any of these documents | [Documentation rules](docs/README.md) |

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

Java sources are under `src/main/java/com/anton/elementalwands/`; the
[feature index](docs/INDEX.md#packages) lists every package. Runtime assets are in
`src/main/resources/`. Editable artwork and generators live in `art/` and `tools/`.
Test helpers are in `src/guardianTest/`; they are separate from the shipped mod. See
the testing guide before launching a fixture.

## Build and required checks

```sh
./gradlew build
./gradlew runClient
```

Run `./gradlew build` after code changes. It includes the Guardian, Necromancer,
Pyre King, party and Roc gaze contract checks and `checkDocs`; none of them launches a
live gameplay session.

For changes to art, models, textures, effects, layouts or sounds, also run:

```sh
./gradlew clean build
git diff --check
python3 tools/check_assets.py
```

`tools/check_assets.py` runs every asset and export check, compares the installed
effect atlases with their sources, and tests the built JAR. It takes several minutes;
`--only <text>` runs just the checks whose path contains that text, and `--list` prints
them. It needs Node as well as Python (with Pillow and NumPy), and fails if a check
changes a tracked file. When a generator gains a `--check`, add it to the list in that
script, not to this guide.

Run the relevant server/client fixture when changing behavior it covers. Do not
claim a fixture passed from an old report.

Documentation-only changes need `./gradlew checkDocs` and `git diff --check`; they do
not require a gameplay build.

## Two machines

The project is worked on from a Mac and a Windows PC that share it through git. Commands
in these documents are written in one machine's form, usually the Mac's; translate them
with this table. The PC column was written from the Mac. A cell marked *unverified* has
not been confirmed on that machine: confirm it when your task needs it, then replace it.

| | Mac | Windows PC |
| --- | --- | --- |
| Shell | zsh | PowerShell |
| Gradle | `./gradlew` | `.\gradlew.bat` |
| Python | `python3` | `py -3` |
| Home folder in a command | `$HOME` | `$env:USERPROFILE` |
| SHA-256 of a file | `shasum -a 256 <file>` | `Get-FileHash <file>` |
| Lunar mods folder | `~/.lunarclient/profiles/1.21/mods/fabric-1.21.10/` | `%USERPROFILE%\.lunarclient\profiles\lunar\1.21\mods\fabric-1.21.10\` (*unverified*) |
| Minecraft assets for test clients | `$HOME/.lunarclient/shared/assets` | *unverified* |
| Viewing review pages from another device | not set up | [Remote preview](docs/reference/remote-preview.md) |

Git does not carry these between the machines, so never assume the other one has them:

- `.local-previews/` (workshops and review pages) and `build/`. Each exists only where it
  was built; rerun the generator or the build.
- The JAR installed in Lunar. Each machine has its own; compare hashes.
- An agent's own memory or notes. Anything the next agent needs goes in these documents.

Generated files must be identical on both machines, or the asset check fails on one of
them; see [art and assets](docs/reference/art-and-assets.md#source-ownership).

## Delegating tests and gameplay recordings

The user prefers one **GPT-6.1 Sol (`gpt-6.1-sol`) subagent** for established test and
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

The build writes `build/libs/elementalwands-<version>.jar`, where `<version>` is
`mod_version` in `gradle.properties` (2.2.0 at the time of writing).
It goes in the Lunar mods folder named under [two machines](#two-machines).

When installation is requested or already authorized, back up the previous JAR,
copy the verified build, and compare source and installed SHA-256 values (shown for the
Mac):

```sh
jar="elementalwands-$(grep '^mod_version=' gradle.properties | cut -d= -f2).jar"
shasum -a 256 "build/libs/$jar" "$HOME/.lunarclient/profiles/1.21/mods/fabric-1.21.10/$jar"
```

Require matching hashes before reporting installation. Restart Lunar to load the
replacement. Never infer the active build from an old documentation hash.

## Changing abilities safely

1. Find the spell's row in the [feature index](docs/INDEX.md#spells): its stable ID in
   `WandSpells`, its handler, and any owning manager.
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
7. Build and run the relevant checks, then update the reference and the feature
   index row in place.

For a new element, extend `WizardAffinity`, handler dispatch, spell metadata,
HUD/theme handling, language entries, and assets. The initial picker follows the
available affinities; inspect existing migration paths before changing saved IDs.

## Previews and documentation upkeep

Browser previews belong under ignored `.local-previews/`; do not commit generated
preview pages. Keep reusable model/texture sources and generator templates tracked.
Extract production art data from a preview before treating the page as disposable.
Runtime resources, not browser pages, enter the mod JAR.

Maintain one current explanation per topic, and update it in place when behavior
changes. Keep open work in `docs/STATUS.md` and delete it when resolved. Add a short
release summary for meaningful player changes. The [documentation rules](docs/README.md)
cover what each page is for and what does not belong in it.
