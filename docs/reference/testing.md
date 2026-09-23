# Testing and native Minecraft previews

## What the checks establish

| Check | Establishes | Does not establish |
| --- | --- | --- |
| Build and contract suites | Compilation, packaging, covered math/state contracts | A live world or approved appearance |
| Asset/export checks | References, dimensions, inventory and generated-source agreement | Gameplay feel |
| Dedicated-server fixture | Real server entities, damage, lifecycle and persistence under scripted cases | Actual client appearance or network feel |
| Native client fixture | Minecraft model/input/render behavior in the fixture | Human balance approval or all hardware/client combinations |
| Human Lunar/multiplayer playtest | Real player feedback in that configuration | Exhaustive correctness |

Report which checks actually ran and which remain pending. Archived passing logs
only establish results for that historical run. Screenshots and browser prototypes
must be labeled according to where they were produced.

## How the preview helpers work

The project has one production mod and separate test helper entrypoints under
`src/guardianTest/java/`. A matching `tools/*_smoke.init.gradle` packages test
metadata and loads the helper beside the production mod in a development run.
The helper can create a disposable world, equip players, move the camera, exercise
inputs, capture native screenshots, write a pass/fail receipt, and exit.

Repeated runs of a feature reuse that feature's helper. Different features have
different entrypoints. They share the test source set rather than being separate
production projects. The helper metadata/classes must not enter the released JAR.

Each runner defines its own directory under `build/`. Fresh runs create or preserve
previous disposable worlds; recovery options deliberately reuse the prior fixture
state. Read its setup before launching. Server fixtures generally bind localhost
on an ephemeral port and require existing EULA acceptance in `run/eula.txt`; do not
silently accept terms or point a fixture at the user's real world.

## Required checks

The complete standard build/VFX command list lives in [AGENT.md](../../AGENT.md).
Use `--offline` only when dependencies/assets are already cached. `./gradlew build`
includes `checkGuardianBeam` and `checkParties`; neither is a live gameplay test.
For church layout changes also run `python3 tools/build_guardian_church.py --check`.

## Selecting a fixture

Paths below are relative to `tools/`. Run only suites relevant to the change;
inspect their assertions when behavior changes instead of trusting an old name.

| Area | Server runner | Client runner |
| --- | --- | --- |
| Wand mesh / affinities / GUI | — | `wand_model_client_smoke.init.gradle` |
| Hub / loadouts | `wand_hub_server_smoke.init.gradle` | `wand_hub_client_smoke.init.gradle` |
| Purchases / progression | `progression_server_smoke.init.gradle` | `progression_client_smoke.init.gradle`, `progression_input_client_smoke.init.gradle` |
| Fire heat and movement | `fire_build_server_smoke.init.gradle`, `fire_tuning_server_smoke.init.gradle` | `fire_leap_client_smoke.init.gradle` |
| Flashover | `flashover_server_smoke.init.gradle`, `sticky_flashover_server_smoke.init.gradle` | `fire_expansion_client_smoke.init.gradle`, `sticky_flashover_client_smoke.init.gradle` |
| Nature Springbloom | `springbloom_server_smoke.init.gradle` | `springbloom_client_smoke.init.gradle` |
| Nature Lash / Bloom / Overgrowth | `nature_expansion_server_smoke.init.gradle`, `overgrowth_throw_server_smoke.init.gradle` | `nature_expansion_client_smoke.init.gradle`, `nature_overgrowth_client_smoke.init.gradle` |
| Stone | `stone_cluster_smoke.init.gradle`, `stone_technique_smoke.init.gradle` | `stone_technique_client_smoke.init.gradle`; Guardian floor client includes Stone cluster mesh checks |
| Wind daggers | `gale_daggers_server_smoke.init.gradle` | `gale_daggers_client_smoke.init.gradle` |
| Wind | `wind_pressure_smoke.init.gradle` | `wind_visual_client_smoke.init.gradle` (guided visual review; leaves disposable world open) |
| Guardian combat | `guardian_combat_smoke.init.gradle`, `guardian_guard_smoke.init.gradle`, `guardian_phase_smoke.init.gradle` | `guardian_floor_client_smoke.init.gradle` |
| Guardian cover / Nature / walls | `guardian_cover_smoke.init.gradle`, `guardian_nature_smoke.init.gradle`, `guardian_wall_smoke.init.gradle` | Relevant native visual fixture |
| Church / arena | `guardian_church_smoke.init.gradle`, `guardian_arena_smoke.init.gradle` | Guardian floor client with visual options |
| Worldgen / locate | `guardian_church_worldgen_smoke.init.gradle`, `guardian_church_locate_smoke.init.gradle` | Human terrain review |
| Parties / audit regressions | `party_server_smoke.init.gradle`, `audit_fixes_smoke.init.gradle` | Human co-op review |

Examples from the repository root:

```sh
./gradlew -I tools/progression_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/wand_model_client_smoke.init.gradle runClient   -PhubClientAssets="$HOME/.lunarclient/shared/assets" --offline
./gradlew -I tools/guardian_floor_client_smoke.init.gradle runClient   -PfloorClientAssets="$HOME/.lunarclient/shared/assets" -PguardianPedestalVisual --offline
```

Client runners using `hubClientAssets` can reuse the local Lunar asset cache.
The floor runner uses `floorClientAssets` instead; supported visual switches
include `guardianPedestalVisual`, `guardianBurnVisual`, and `natureVisual`.
Check the runner for the required asset index and completion marker. A process
exiting successfully is insufficient if the fixture did not produce its receipt.

Recovery pairs must run in sequence against their intended saved fixture:

```sh
./gradlew -I tools/guardian_arena_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_arena_smoke.init.gradle -ParenaRecovery runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle -PchurchRecovery runServer --args nogui
./gradlew -I tools/party_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/party_server_smoke.init.gradle -PpartyResume runServer --args nogui
```

## Evidence retention

Keep useful final native views, a concise result/limitation summary, and critical
reproduction steps. Raw successful build/client logs are regenerable and usually
need not be retained in docs. Keep failure evidence when it explains an unresolved
or subtle regression. Historical receipts may include checksums but do not define
the current installation. Verify source and installed JARs when that matters.

Do not delete `art/` sources, runtime assets, test fixtures, or the only explanation
of a recovery edge case while pruning screenshots. Check links and generator
references first. `docs/archive/evidence/` holds selected historical evidence;
`.local-previews/` and `build/` hold local outputs rather than required starting context.
