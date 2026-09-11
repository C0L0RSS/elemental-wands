# Church locate server stalls — September 9, 2026

The Lunar log recorded `Locating element elementalwands:guardian_church took
113166 ms` at 22:12:47, followed by `Running 113152ms or 2263 ticks behind`.
The search eventually found `[-736, ~, 1680]`. It was a synchronous search stall,
not a permanent deadlock. The previous seed-12345 worldgen fixture also stalled
while locating, but only checked that the structure eventually existed.

## Fix

The vanilla church command and a tag containing only this church now start a
server-ticked search. `/ew guardian church locate` uses the same path when no
recorded site exists. The search enumerates the vanilla random-spread region
perimeters and asks Minecraft's normal chunk workers for `STRUCTURE_STARTS`.
It polls the future on later ticks instead of waiting on the server thread.
All world access, ticket management and command feedback remain on that thread.
No manual off-thread `world.locateStructure` calls are used.

Only one search runs at a time and only one structure-start request is pending.
A temporary loading-only ticket keeps that request alive; it is released on
completion, cancellation, failure, player disconnect/world change, timeout and
server shutdown. Searches stop after two minutes and can be cancelled with
`/ew guardian church cancel`. Vanilla commands for other structures are untouched.
Search acknowledgement returns 1 immediately; command-block distance capture is
no longer synchronous for this church. Unsupported custom placement types report
an explanation rather than falling back to a blocking search.

Placement avoids support-column work until the complete height grid passes,
caches overlapping support results, and stops scoring patches that cannot beat
the current best score. If all 17 possible shifted origins fail vanilla's biome
predicate at their exact proposed elevations, the expensive scoring is skipped.
The floor, slope/support rules, tie ordering, shifts, template and spacing remain
unchanged; the biome precheck does not reject a candidate merely because its
unshifted origin is in the wrong biome.

## Regression fixture

`tools/guardian_church_locate_smoke.init.gradle` creates a disposable normal world
under `build/church-locate-smoke-run/`. It invokes the real vanilla command,
cancels an in-flight search, checks the church alias and singleton-tag duplicate
handling, runs a second command during the search, and verifies continuing world
time and mob ticks. It checks the delayed coordinates against vanilla's result
on the same generated starts. A 10-second watchdog and a 1.5-second maximum
observed tick-gap assertion catch stalls. Empty-server pausing is disabled.

Run with `./gradlew -I tools/guardian_church_locate_smoke.init.gradle runServer
--offline --args nogui`; supply `-PchurchSeed=<seed>` for another fresh world.
The fixture stays out of the production JAR.

Human Lunar playtesting remains separate from these server checks.

## Measured results

- The initial incremental-search fixture on seed `12345` returned the same vanilla
  result `[-1104, ~, 144]`: 52.2 seconds elapsed, 1,052 ticking frames, maximum
  tick interval 57 ms. This was before the additional biome prefilter optimization.
- The final user-seed fixture (`-1173380564968105294`) returned `[-736, ~, 1680]`,
  exactly matching the reported Lunar search and vanilla's result on the generated
  starts: 90.3 seconds elapsed, 1,809 ticking frames, maximum tick interval 80 ms,
  and 1,793 mob ticks. The initial command returned in milliseconds and a subsequent
  `time query gametime` completed during the search.
- Cancellation/restart, singleton-tag duplicate suppression, church alias routing
  and continued world time passed in the same fixtures.
- The first user-seed fixture was stopped after Minecraft auto-paused its empty
  test server at 60 seconds. The fixture now disables that pause; the final run
  above completed normally. No player world was edited or used as a test world.

These timings measure different server/client conditions and are not a controlled
speed benchmark. The verified improvement is that the long search no longer owns
one server tick. Natural searches can still take over a minute.

Logs: `/private/tmp/church-locate-smoke-12345.log` and
`/private/tmp/church-locate-smoke-user-seed-final.log`.

The existing full native-generation fixture passed on the final source: same
seed-12345 origin `(-1082, 93, 128)`, clockwise-90 rotation, natural courtyard,
socket, heart chest, and terrain-blend checks. Its direct `world.locateStructure`
API call remains synchronous; only the player command is redirected. The fixture's
large single-tick locate/load operation is not a responsiveness test.

One run of that separate fixture hit `EntryMissingException: Missing Palette entry
for index 31` in Minecraft's lighting worker while bulk-loading chunks. A thread
dump showed the fixture waiting in `world.getChunk`; no incremental search was
active. The failed disposable process was stopped, and the unchanged source passed
on a fresh-world retry. The cause of that intermittent lighting failure has not
been established; it is not claimed fixed by the locator change. Evidence:
`/private/tmp/church-locate-native-final.log`,
`/private/tmp/church-native-thread-dump.txt`, and the passing
`/private/tmp/church-locate-native-retry.log`.

## Build and installation

Final `./gradlew build --offline`, `git diff --check`, church layout validation,
JAR integrity, locator-mixin packaging and test-fixture exclusion passed.
Build log: `/private/tmp/church-locate-build-final.log`.

Installed in Lunar with the previous JAR backed up. Restart the entire client to
load the new code; replacing the file does not update an already running game.

- Installed: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-church-locate-20260909-222957.jar`
- Matching built/installed SHA-256: `6ee62e6d44f47fac39d29877f54132f4a0b9e48dcd0ec933fbbdce4062ba9995`
- Receipt: `/private/tmp/church-locate-install.json`

No human playtest of this installed build has been confirmed yet.
