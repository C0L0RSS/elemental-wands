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
includes `checkGuardianBeam`, `checkNecromancer` and `checkParties`; neither is a live gameplay test.
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
| Space Gravity Well | `gravity_well_server_smoke.init.gradle` | `gravity_well_client_smoke.init.gradle` |
| Space Astral Double | `astral_double_server_smoke.init.gradle` | `astral_double_client_smoke.init.gradle` |
| Wind Updraft / dash combos | `updraft_server_smoke.init.gradle` | `updraft_client_smoke.init.gradle` |
| Wind daggers | `gale_daggers_server_smoke.init.gradle` | `gale_daggers_client_smoke.init.gradle` |
| Wind | `wind_pressure_smoke.init.gradle` | `wind_visual_client_smoke.init.gradle` (guided visual review; leaves disposable world open) |
| Guardian combat | `guardian_combat_smoke.init.gradle`, `guardian_guard_smoke.init.gradle`, `guardian_phase_smoke.init.gradle` | `guardian_floor_client_smoke.init.gradle` |
| Guardian cover / Nature / walls | `guardian_cover_smoke.init.gradle`, `guardian_nature_smoke.init.gradle`, `guardian_wall_smoke.init.gradle` | Relevant native visual fixture |
| Hollow Necromancer | `necromancer_server_smoke.init.gradle` (spells, Hands→ambush, waves and their quickening, a Soul Fire Rain volley, blink/shift, the full two-siege fight flow with rain into the transformation), `necromancer_phase_smoke.init.gradle` (phase two; starts past the sieges: grab, swipe, the random-windup rush, Grave Dive hit and dodge, Soul Harvest, the soul split and collapse) | `necromancer_client_smoke.init.gradle` (screenshots; `-PnecroRecord` for hidden recording; `-PnecroMechanics` for the Soul Fire Rain fireball, charge windup, Grave Dive, soul split and Soul Harvest against a scripted player); siege perch views are in `crypt_client_smoke` |
| Hollow undead | `hollow_undead_server_smoke.init.gradle` (rise, chase, hit-frame damage and arrow timing, daylight burning, loot, held death clip, bound-minion rules) | `hollow_undead_client_smoke.init.gradle` (hidden window; rise, idle front/side, walk, attack key frames, death screenshots) |
| Hollow Crypt realm | — | `crypt_client_smoke.init.gradle` (realm build, enter, summon, a siege on an exported bough perch with near and far-rim screenshots, wall, reset, leave, `/locate`, headstone ritual, wipe with kept items, victory rewards and spell book; screenshots) |
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
A fresh run directory's first-launch accessibility screen turns the narrator on; set
`narrator:0` in that runner's `options.txt` (the crypt runner switches it off itself).
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

## Test and recording handoff

The delegation policy and standing user preference live in
[AGENT.md](../../AGENT.md#delegating-tests-and-gameplay-recordings). Use one worker
for the whole established test/record/export sequence, with the main agent doing
independent review or documentation work. Model selection alone does not guarantee
lower total usage: keep the handoff short and avoid duplicating the worker's work.

Before handing off, finish the implementation and identify the exact checkout,
changed behavior, required checks, and existing fixture. The worker must test
the current uncommitted implementation, not a fresh worktree missing those edits.
Pause edits to production code and fixtures until the worker finishes. Only one
agent owns Gradle runs and the Minecraft test client in that checkout; do not
overlap builds, `clean`, or test clients sharing its generated files. The main
agent can review source and edit unrelated documentation during verification.

Use `gpt-6-sol` with medium reasoning and a fresh context (`fork_turns="none"`
when supported). Supply the following information rather than the full history:

```text
Run the established tests and gameplay recording for [feature] in [absolute
checkout path]. Read AGENT.md and docs/reference/testing.md first. The current
uncommitted implementation is the test target; preserve it.

Expected behavior: [short description and acceptance criteria].
Run: [exact build/check/fixture commands; identify checks already completed].
Capture: [scenes, camera views, output path, expected duration/frame rate].
You own the Gradle/client runs while I review or update documentation. Do not
edit production code, change assertions, install into Lunar, or start other
agents. If an unexpected failure occurs, report the failing assertion, a short
log excerpt, and your diagnosis. Retry once only for a clearly identified
transient harness problem; otherwise return the failure to me.

Return: checks actually run and their outcomes, fresh receipt paths, MP4 path,
duration/frame rate, a few inspected frames, and any remaining limitations.
```

The main agent reviews failures and makes necessary changes before requesting
another run. When the worker succeeds, inspect the evidence and final video
metadata without rerunning passing suites unless new changes justify it. Deliver
the local MP4 inline or as an absolute-path link, distinguish scripted native
tests from human Lunar playtests, and state the actual installation status.

### Existing Updraft recording recipe

This is an implemented example, not a universal recording flag for every spell.
For another feature, select its existing fixture and have the main agent add or
adapt a recording mode if needed before delegating routine runs. No new agent or
extra confirmation is needed just to execute this recipe locally.

The Updraft client runner accepts `-PupdraftRecord` to capture a silent sequence
of the actual game framebuffer at 20 frames per second. Recording hides the test
window and sends scripted cast packets so it does not require keyboard/mouse
focus; the ordinary non-recording fixture verifies actual key/mouse input.
Prefer hidden-window recording to repeated attempts to steal desktop focus.
Recording helpers remain in the test source set and never enter the released mod.

Run from the repository root, after the required build/checks for the change.
Use `--offline` and this asset path only if the existing cache is available:

```sh
./gradlew -I tools/updraft_server_smoke.init.gradle runServer --args nogui --offline
./gradlew -I tools/updraft_client_smoke.init.gradle runClient -PupdraftRecord \
  -PhubClientAssets="$HOME/.lunarclient/shared/assets" --offline
```

Require fresh `build/updraft-smoke-run/PRESSURE_PASSED.txt` and
`build/updraft-client-smoke/HUB_PASSED.txt` receipts with no corresponding
`*_FAILED.txt` files. Both runners clear their prior receipts during setup.
Check the actual assertions/results; a video by itself is not a passing test.

`build/updraft-client-smoke/RECORDING_DIR.txt` names the current recording's
directory. The fixture captures 200 consecutive frames (`frame-0000.png` through
`frame-0199.png`) for ten seconds at 20 fps. Verify all frames exist, then encode
at that rate. This uses the installed `ffmpeg`/`ffprobe`; do not rely on a prior
session's temporary encoding script:

```sh
recording_frames="$(cat build/updraft-client-smoke/RECORDING_DIR.txt)"
recording_output=".local-previews/updraft/updraft-gameplay.mp4"
mkdir -p .local-previews/updraft
ffmpeg -hide_banner -loglevel error -y -framerate 20 \
  -i "$recording_frames/frame-%04d.png" -frames:v 200 \
  -vf 'scale=1280:720:flags=neighbor' -c:v libx264 -preset fast -crf 19 \
  -pix_fmt yuv420p -movflags +faststart "$recording_output"
ffprobe -v error -show_entries \
  stream=codec_name,width,height,avg_frame_rate,nb_frames:format=duration,size \
  -of json "$recording_output"
ffmpeg -v error -i "$recording_output" -f null -
```

Run each step only after the preceding one succeeds. Confirm H.264, 1280×720,
20 fps, 200 frames, ten seconds, and successful decoding. Inspect frames near
launch, apex, combo, and landing for missing content, pause menus, or unwanted
desktop capture. Preserve the final MP4 and concise receipts under ignored
`.local-previews/` before any later `clean` build. Preserve useful existing clips
when choosing an output filename; generated raw frames need not be retained once
the exported video is verified.

### Astral Double recording

`astral_double_client_smoke.init.gradle` accepts `-PastralRecord` and the same
`hubClientAssets` option. It records 300 consecutive frames at 20 fps (15 seconds)
from the game framebuffer into the directory named by
`build/astral-double-client-smoke/RECORDING_DIR.txt`. Use the encoding recipe above
with 300 frames and `.local-previews/astral-double/astral-double-gameplay.mp4`.
The ordinary mode exercises bound inputs; recording sends scripted cast packets.
Require fresh `build/astral-double-smoke-run/PRESSURE_PASSED.txt` and
`build/astral-double-client-smoke/HUB_PASSED.txt`, with no failure markers.
Scenes cover the orb toss, skin and wand, mirrored fire, consuming teleport,
Blink Rift return and destruction poof. A dev player's supplied skin is used;
this does not verify the user's authenticated Lunar skin or multiplayer feel.

## Evidence retention

The Necromancer client runner accepts `-PnecroRecord` with `hubClientAssets`.
It captures 450 framebuffer frames at 20 fps (22.5 seconds), covering the
eight-second emergence, the standing skeleton, swipe, casting, rush and walking.
`build/necromancer-client-smoke/RECORDING_DIR.txt` identifies the frame directory;
require a fresh `NECRO_PASSED.txt` and no `NECRO_FAILED.txt`. Screenshots also cover
the mage and transformation stages. This scripted recording does not verify
human input, multiplayer feel or installation into Lunar.

The same runner accepts `-PsoulBoltRecord` for an isolated skull view. It records
160 frames at 20 fps (eight seconds), checking the custom Soul Bolt renderer,
flight, synchronized stationary wall bite and removal. The deliberately slowed
test projectile makes the repeating jaw motion visible; normal combat uses the
existing faster homing volleys. Use the same recording directory and receipt paths
as above and encode with 160 frames. Run the Necromancer server fixture as well
to verify cover and player damage.

Keep useful final native views, a concise result/limitation summary, and critical
reproduction steps. Raw successful build/client logs are regenerable and usually
need not be retained in docs. Keep failure evidence when it explains an unresolved
or subtle regression. Historical receipts may include checksums but do not define
the current installation. Verify source and installed JARs when that matters.

Do not delete `art/` sources, runtime assets, test fixtures, or the only explanation
of a recovery edge case while pruning screenshots. Check links and generator
references first. `docs/archive/evidence/` holds selected historical evidence;
`.local-previews/` and `build/` hold local outputs rather than required starting context.

### Gravity Well recording

`gravity_well_client_smoke.init.gradle` accepts `-PgravityRecord` and
`-PhubClientAssets="$HOME/.lunarclient/shared/assets"`. Its hidden-window mode
captures 240 frames at 20 fps (12 seconds), covering the throw, impact, pulling
mobs, accelerating collapse buildup, outward burst, a second well and automatic
collapse. Use the encoding
recipe above with 240 frames and `.local-previews/gravity-well/gravity-well-gameplay.mp4`.
`build/gravity-well-client-smoke/RECORDING_DIR.txt` identifies the raw frames.
Require its fresh `HUB_PASSED.txt` and the dedicated fixture's
`build/gravity-well-smoke-run/PRESSURE_PASSED.txt`, with no failure markers.
The ordinary client mode exercises the bound fifth-slot input; recording sends
scripted packets. Neither mode establishes human Lunar balance approval.

### Necromancer rush and Grasping Hands recording

Use `-PrushRecord` on `necromancer_client_smoke.init.gradle` with the existing
`hubClientAssets` option. It captures 300 frames at 20 fps (15 seconds), showing
an ordinary rush (with its windup lane)/grab/bite/throw and the phase-two Hands
follow-up from a side camera; the scripted victim has iron-armor points so the combo
is survivable, as in the playtest. The server fixture `necromancer_phase_smoke.init.gradle`
checks the old slam and rescue, rush hit/miss after the aim locks, cover, single bite,
throw, no teammate interrupt, nearest trapped target, the combo's damage against iron
armor, Hands escape/visual expiry and cancellation. The native
fixture also checks that all three hand rigs lean and curl their fingers during
the grip, guarding against shared animation-processor state.

The native recording uses a scripted player with no input client; the fixture
integrates its released throw velocity. Actual combat owns the grip, bite and
throw impulse. Require fresh `NECRO_PASSED.txt` and no failure receipt, then encode
the current `RECORDING_DIR.txt` to `.local-previews/grasping-hands/necromancer-rush-gameplay.mp4`.
This does not verify human movement input or multiplayer latency.

### Life Drain recording

Use `-PdrainRecord` with `necromancer_client_smoke.init.gradle` and the existing
`hubClientAssets` option. It records 300 frames at 20 fps (15 seconds), showing
the braid from an observer camera, first-person mist, a cover break and boss
removal. Assertions check tracked cast timing, observer and third-person mist
exclusion, fade completion and removal cleanup. Require a fresh `NECRO_PASSED.txt`
and no failure receipt. Encode the current `RECORDING_DIR.txt` to
`.local-previews/life-drain/life-drain-gameplay.mp4`; run the Necromancer server
fixture for real damage, healing caps and cover rules. This does not establish
human Lunar appearance or installation.
