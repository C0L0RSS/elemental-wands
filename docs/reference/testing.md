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

## How the fixtures work

The project has one production mod and separate test helper entrypoints under
`src/guardianTest/java/`. A matching runner, `tools/<name>.init.gradle`, packages test
metadata and loads the helper beside the production mod in a development run. The helper
can create a disposable world, equip players, move the camera, exercise inputs, capture
native screenshots, write a pass/fail receipt, and exit. Helpers share the test source
set and never enter the released JAR.

Each runner defines its own directory under `build/` and clears its previous receipts
during setup. Fresh runs create or preserve previous disposable worlds; recovery options
deliberately reuse the prior fixture state. Read the runner's setup before launching.
Server fixtures bind localhost on an ephemeral port and require existing EULA acceptance
in `run/eula.txt`; do not silently accept terms or point a fixture at the user's real world.

**What a fixture asserts is written in its helper class, not here.** The runner names the
class (`fixtureMain` or the entrypoint it packages). Read it when behavior changes instead
of trusting a name or an old description.

## Required checks

The standard build and asset commands are in [AGENTS.md](../../AGENTS.md#build-and-required-checks).
`./gradlew build` includes `checkGuardianBeam`, `checkNecromancer`, `checkPyreKing`,
`checkParties`, `checkStormRocGaze` and `checkDocs`; none is a live gameplay test.
Use `--offline` only when dependencies and assets are already cached.

## Selecting a fixture

Each name below is a runner: `tools/<name>.init.gradle`. Server runners use `runServer`,
client runners use `runClient`. Run only the suites relevant to the change.

| Area | Server runner | Client runner | Flags and notes |
| --- | --- | --- | --- |
| Wand mesh, affinities, GUI | — | `wand_model_client_smoke` | |
| Hub, loadouts | `wand_hub_server_smoke` | `wand_hub_client_smoke` | |
| Purchases, progression | `progression_server_smoke` | `progression_client_smoke`, `progression_input_client_smoke` | |
| Fire heat and movement | `fire_build_server_smoke`, `fire_tuning_server_smoke` | `fire_leap_client_smoke` | |
| Fire block-fire visuals | — | `fire_blockfire_client_smoke` | |
| Flashover | `flashover_server_smoke`, `sticky_flashover_server_smoke` | `fire_expansion_client_smoke`, `sticky_flashover_client_smoke` | |
| Wind | `wind_pressure_smoke` | `wind_visual_client_smoke` | The client runner is a guided visual review and leaves its disposable world open |
| Wind block-wind visuals | — | `wind_blockwind_client_smoke` | |
| Wind Updraft, dash combos | `updraft_server_smoke` | `updraft_client_smoke` | `-PupdraftRecord` |
| Wind Gale Daggers | `gale_daggers_server_smoke` | `gale_daggers_client_smoke` | |
| Stone | `stone_cluster_smoke`, `stone_technique_smoke` | `stone_technique_client_smoke` | The Guardian floor client also checks the Stone cluster mesh |
| Nature Springbloom | `springbloom_server_smoke` | `springbloom_client_smoke` | |
| Nature Seed, Thornbite, Sentry Bloom, Full Bloom | `nature_expansion_server_smoke`, `full_bloom_server_smoke` | `nature_expansion_client_smoke`, `nature_overgrowth_client_smoke` | `nature_overgrowth_client_smoke` also covers the block-nature effects |
| Retired Overgrowth oak | `overgrowth_throw_server_smoke` | — | Runs the oak through its kept entry point |
| Space void-glass visuals | — | `space_blockspace_client_smoke` | |
| Space Gravity Well | `gravity_well_server_smoke` | `gravity_well_client_smoke` | `-PgravityRecord` |
| Space Astral Double | `astral_double_server_smoke` | `astral_double_client_smoke` | `-PastralRecord` |
| Guardian combat | `guardian_combat_smoke`, `guardian_shell_smoke`, `guardian_phase_smoke` | `guardian_floor_client_smoke` | The floor client takes `-PfloorClientAssets` and the visual switches `-PguardianPedestalVisual`, `-PguardianBurnVisual`, `-PnatureVisual` |
| Guardian cover, Nature, walls | `guardian_cover_smoke`, `guardian_nature_smoke`, `guardian_wall_smoke` | — | `guardian_nature_smoke -PnatureBalance` runs the Nature damage, mark and flower fixture on a flat world |
| Church, Shattered Nave | `guardian_church_smoke`, `guardian_nave_smoke` | `nave_client_smoke` | Recovery pairs: `-PchurchRecovery`, `-PnaveRecovery`. `-PintroVideo` records the Guardian intro |
| Hollow Necromancer | `necromancer_server_smoke` (robed fight, sieges), `necromancer_phase_smoke` (phase two) | `necromancer_client_smoke` | All flags are for the client runner: `-PnecroMechanics` for the phase-two mechanics against a scripted player; recording flags `-PnecroRecord`, `-PrushRecord`, `-PdrainRecord`, `-PsoulBoltRecord` |
| Hollow undead | `hollow_undead_server_smoke` | `hollow_undead_client_smoke` | |
| Hollow Crypt realm | — | `crypt_client_smoke` | Also photographs the siege perch views. `-PintroVideo` records his intro; `-PtransformVideo` records the transformation and skips the other crypt checks |
| Storm Roc | `roc_server_smoke` | `roc_client_smoke` | Run the server fixture first. The client saves screenshots, not video |
| Storm Summit realm | — | `summit_client_smoke` | |
| Storm aerie and intro | `aerie_server_smoke` | `aerie_client_smoke`, `roc_intro_client_smoke` | `-PaerieSeed` (default 12345), `-PaerieGongs="label@x,y,z;…"`; for the intro `-ProcIntroGong=x,y,z`, `-ProcIntroVideo`, `-ProcIntroEvery`, `-ProcIntroViewDistance`, `-ProcIntroSkipAt=<beat>` |
| Pyre King | `checkPyreKing` (in the build) | `pyre_king_client_smoke`, `pyre_king_fight_smoke`, `pyre_king_intro_smoke` | `-PpyreFightRecord`, `-PpyreFightRecordCase=<name>[,<name>]`, `-PpyreIntroVideo`, `-PpyreIntroEvery=<ticks>`. See [Pyre King](pyre-king.md#verification) |
| Burned Gate | — | `pyre_gate_smoke` | Uses a fresh normal world |
| Burned Throne Hall | — | `pyre_hall_client_smoke` | |
| Worldgen, locate | `guardian_church_worldgen_smoke`, `guardian_church_locate_smoke`, `aerie_worldgen_smoke` | — | `guardian_church_locate_smoke` also checks where the locate teleport links land at a church, a graveyard and an aerie. `-PaerieSeed` for the aerie runner. Terrain fit still needs a human look |
| Parties, audit regressions | `party_server_smoke`, `audit_fixes_smoke` | — | Restart pair: `-PpartyResume` |

Not every client fixture hides its window or mutes itself: about half of the helpers
set the volume. Before launching one, read its runner and helper, and make sure the test
client is muted as [AGENTS.md](../../AGENTS.md#working-rules) requires. Any new fixture must
mute its client before loading a world.

### Running them

Examples from the repository root, in the Mac's form. On the Windows PC translate the
Gradle command, `$HOME` and the assets folder with the table under
[two machines](../../AGENTS.md#two-machines).

```sh
./gradlew -I tools/progression_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/wand_model_client_smoke.init.gradle runClient -PhubClientAssets="$HOME/.lunarclient/shared/assets" --offline
./gradlew -I tools/guardian_floor_client_smoke.init.gradle runClient -PfloorClientAssets="$HOME/.lunarclient/shared/assets" -PguardianPedestalVisual --offline
```

- Client runners take `-PhubClientAssets` to reuse the local Lunar asset cache; add
  `-x downloadAssets` so Loom does not also fetch its own copy. The floor runner uses
  `-PfloorClientAssets` instead.
- The first client or server run on a machine needs one online run to fetch Loom's
  launcher libraries.
- A fresh run directory's first-launch accessibility screen turns the narrator on; set
  `narrator:0` in that runner's `options.txt` (the crypt runner switches it off itself).
- Each runner writes a `*_PASSED.txt` or `*_FAILED.txt` receipt under its `build/`
  directory. A process exiting successfully is not a pass: require a fresh passed receipt
  and no failed one. The runner file names its receipt, its `build/` directory and the
  asset index it needs.

Recovery pairs must run in sequence against their intended saved fixture:

```sh
./gradlew -I tools/guardian_nave_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_nave_smoke.init.gradle -PnaveRecovery runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle runServer --args nogui
./gradlew -I tools/guardian_church_smoke.init.gradle -PchurchRecovery runServer --args nogui
./gradlew -I tools/party_server_smoke.init.gradle runServer --args nogui
./gradlew -I tools/party_server_smoke.init.gradle -PpartyResume runServer --args nogui
```

### Writing fixtures: traps that pass silently

- Simulated players moved into a realm (the crypt or the nave) must call
  `onTeleportationDone()` afterwards, as a client's confirmation would. Until then they
  cannot be hurt, which silently passes damage checks.
- A boss intro teleports its watchers too, so confirm again once it ends. Fixtures that
  are not about the intro skip it with `GuardianIntro.skip` for each watcher.
- Simulated players drop every packet and declare no channels.
  `GuardianNaveSmokeMod.listen` declares one as a client would and records the payloads
  sent on it.
- Casts aim with the head, so set the head yaw as well as the yaw before an aimed cast.
- Fixtures that need realm entities to tick force-load the slot's chunks, as they do in
  the Overworld.

### Server fixtures on a machine with no accepted EULA

A dedicated-server runner refuses to start without `run/eula.txt`. Where none is on file,
`integrated_fixture_host.init.gradle` loads the same fixture class beside a singleplayer
world instead, started muted, with the real player parked far away as a spectator:

```sh
./gradlew -I tools/integrated_fixture_host.init.gradle runClient -x downloadAssets \
  -PfixtureMain=com.anton.elementalwands.entity.NatureBalanceSmokeMod -PfixtureReceipt=NATURE \
  -PhubClientAssets="$HOME/.lunarclient/shared/assets"
```

`fixtureReceipt` is the prefix of the fixture's `*_PASSED.txt`; receipts land in
`build/integrated-fixture-host/`. It creates a new ordinary world each run, in Survival,
and clears the air around y=100 where flat-world fixtures stage their scenes. It cannot
serve a restart pair or a fixture that depends on dedicated-server settings, and a pass is
reported as a hosted run, not a dedicated-server run. A fixture hosted this way must not
query the world for entities in the same tick it force-loads their chunk.

## Test and recording handoff

The delegation policy and standing user preference live in
[AGENTS.md](../../AGENTS.md#delegating-tests-and-gameplay-recordings). Use one worker
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

Use `gpt-6.1-sol` with medium reasoning and a fresh context (`fork_turns="none"`
when supported). Supply the following information rather than the full history:

```text
Run the established tests and gameplay recording for [feature] in [absolute
checkout path]. Read AGENTS.md and docs/reference/testing.md first. The current
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

## Recording gameplay

Recording modes capture the actual game framebuffer in a hidden window and send scripted
cast packets, so they need no keyboard or mouse focus; the ordinary fixture mode is what
verifies real input. Not every feature has one: for a new feature, the main agent adds or
adapts a recording mode to its fixture before delegating routine runs.

### The recipe

Shown for Updraft. Other modes follow the same steps with the values from the table
below: the frame file pattern, the frame count and the rate differ. Run from the
repository root, after the build and checks for the change:

```sh
./gradlew -I tools/updraft_server_smoke.init.gradle runServer --args nogui --offline
./gradlew -I tools/updraft_client_smoke.init.gradle runClient -PupdraftRecord \
  -PhubClientAssets="$HOME/.lunarclient/shared/assets" --offline
```

Require fresh passed receipts from both runs and no failed ones; a video by itself is not
a passing test. Verify every frame exists, then encode with the installed `ffmpeg` and
`ffprobe`:

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

For another mode, change three things in the `ffmpeg` line:

- `-i`: the frame files from the table. If the first frame is not numbered 0, add
  `-start_number <n>` before `-i` (the Guardian intro starts at 001).
- `-framerate`: 20 for a frame every tick. Modes that save a frame every other tick play
  at real speed at 10; at 20 the video runs double speed.
- `-frames:v`: the frame count, or leave it out to encode every frame present.

Run each step only after the preceding one succeeds. Confirm H.264, 1280×720, the frame
rate, the frame count, the duration and successful decoding. Inspect frames near each key
moment for missing content, pause menus or unwanted desktop capture. Save the MP4 under
ignored `.local-previews/<feature>/` before any later `clean`; do not overwrite a useful
earlier clip. Raw frames need not be kept once the video is verified.

### Recording modes

| Client runner and flag | Frames, rate | Frame files | Passed receipt |
| --- | --- | --- | --- |
| `updraft_client_smoke -PupdraftRecord` | 200 at 20 fps (10 s) | `frame-%04d.png` in the directory named by `build/updraft-client-smoke/RECORDING_DIR.txt` | `build/updraft-client-smoke/HUB_PASSED.txt`, server `build/updraft-smoke-run/PRESSURE_PASSED.txt` |
| `astral_double_client_smoke -PastralRecord` | 300 at 20 fps (15 s) | `frame-%04d.png` in `build/astral-double-client-smoke/RECORDING_DIR.txt`'s directory | `build/astral-double-client-smoke/HUB_PASSED.txt`, server `build/astral-double-smoke-run/PRESSURE_PASSED.txt` |
| `gravity_well_client_smoke -PgravityRecord` | 240 at 20 fps (12 s) | `frame-%04d.png` in `build/gravity-well-client-smoke/RECORDING_DIR.txt`'s directory | `build/gravity-well-client-smoke/HUB_PASSED.txt`, server `build/gravity-well-smoke-run/PRESSURE_PASSED.txt` |
| `necromancer_client_smoke -PnecroRecord` (colossus, swipe, casting, rush, walking) | 450 at 20 fps (22.5 s) | the directory named by `build/necromancer-client-smoke/RECORDING_DIR.txt` | `build/necromancer-client-smoke/NECRO_PASSED.txt` |
| `necromancer_client_smoke -PrushRecord` (rush and Grasping Hands) | 300 at 20 fps (15 s) | same | same |
| `necromancer_client_smoke -PdrainRecord` (Life Drain) | 300 at 20 fps (15 s) | same | same |
| `necromancer_client_smoke -PsoulBoltRecord` (one slowed skull) | 160 at 20 fps (8 s) | same | same |
| `nave_client_smoke -PintroVideo` (Guardian intro) | 482 at 20 fps (24.1 s) | `build/nave-client-smoke/screenshots/nave-intro-video-%03d.png`, from 001 | `build/nave-client-smoke/NAVE_CLIENT_PASSED.txt` |
| `crypt_client_smoke -PintroVideo` (Necromancer intro) | 383 at 20 fps (19.15 s) | `build/crypt-client-smoke/screenshots/intro-video-%03d.png`, from 000 | `build/crypt-client-smoke/CRYPT_PASSED.txt` |
| `crypt_client_smoke -PtransformVideo` (transformation) | about 692 at 20 fps; the receipt gives the count | `build/crypt-client-smoke/screenshots/transform-video-%03d.png`, from 000 | `build/crypt-client-smoke/CRYPT_PASSED.txt` |
| `roc_intro_client_smoke -ProcIntroVideo` (Roc intro and opening summon) | one frame every `-ProcIntroEvery` ticks (default 2, so 10 fps) | `build/roc-intro-client-smoke/screenshots/roc-intro-video-%04d.png` | `build/roc-intro-client-smoke/ROC_INTRO_CLIENT_PASSED.txt` |
| `pyre_king_intro_smoke -PpyreIntroVideo` | one frame every other tick (10 fps); every tick (20 fps) with `-PpyreIntroEvery=1` | `build/pyre-king-intro-smoke/screenshots/intro-%05d.png`; check the first number | `build/pyre-king-intro-smoke/PYRE_KING_INTRO_PASSED.txt` |
| `pyre_king_fight_smoke -PpyreFightRecord` | one frame every other tick of the free fight (10 fps) | `build/pyre-king-fight-smoke/screenshots/record-%05d.png`; check the first number | `build/pyre-king-fight-smoke/PYRE_KING_FIGHT_PASSED.txt` |
| `pyre_king_fight_smoke -PpyreFightRecordCase=<name>[,<name>]` | one frame every other tick of those cases (10 fps) | `build/pyre-king-fight-smoke/screenshots/case-<name>-%05d.png` | same |

Output names used so far, to keep clips findable: `.local-previews/updraft/updraft-gameplay.mp4`,
`.local-previews/astral-double/astral-double-gameplay.mp4`,
`.local-previews/gravity-well/gravity-well-gameplay.mp4`,
`.local-previews/grasping-hands/necromancer-rush-gameplay.mp4`,
`.local-previews/life-drain/life-drain-gameplay.mp4`.

Limits that apply to every recording: the player is scripted, so nothing here verifies
human input, multiplayer latency, balance or what is installed in Lunar. The Necromancer
recordings show appearance only; run the Necromancer server fixtures for real damage,
healing caps and cover rules. The rush recording's victim has iron-armor points so the
combo is survivable, and the soul-bolt recording slows the projectile on purpose.

### Storm Roc screenshots

Run `roc_server_smoke`, then `roc_client_smoke` with `-PhubClientAssets`. Require
`build/roc-smoke-run/ROC_SERVER_PASSED.txt` and `build/roc-client-smoke/ROC_CLIENT_PASSED.txt`.
The client runs a scripted rehearsal in the authored summit and saves individual
screenshots under `build/roc-client-smoke/screenshots/`; it records no video. Inspect the
perch, spire hop and landing for rock contact, then lightning, the dive warning and
impact, the stuck opening, every ground move and the takeoff, then phase two: the charge
bolt, the storm from the sky and the ground, the tornadoes, the chain smash and live
ground. The camera and challenger are scripted.

## Evidence retention

Keep useful final native views, a concise result and limitation summary, and critical
reproduction steps. Raw successful build and client logs are regenerable and usually
need not be retained in docs. Keep failure evidence when it explains an unresolved
or subtle regression. Historical receipts may include checksums but do not define
the current installation. Verify source and installed JARs when that matters.

Do not delete `art/` sources, runtime assets, test fixtures, or the only explanation
of a recovery edge case while pruning screenshots. Check links and generator
references first. `docs/archive/evidence/` holds selected historical evidence;
`.local-previews/` and `build/` hold local outputs rather than required starting context.
