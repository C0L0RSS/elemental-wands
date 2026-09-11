# Guardian churches — placement and frequency, September 9, 2026

The statue, pedestal, socket and offering chest are six blocks farther from the
church entrance, leaving ten clear blocks behind the pedestal. The building and
saved site identity stay fixed. The actual socket is local Z -6; callers use
`Site.socket()` and `Site.offering()` instead of assuming the anchor is the socket.

Natural candidates now use 24-chunk spacing and 14-chunk separation (previously
40/24): one candidate per 384-by-384-block region, about 2.78 times as many candidate
sites. The existing five biomes and terrain checks still constrain actual occurrence.
This does not populate already-explored terrain or establish a measured spawn rate.

## Hills and foundations

The 17-position search compares acceptable candidates instead of accepting the first.
It chooses a median floor elevation and scores height deviations, with a larger
penalty for cutting high ground. Height samples are shared across attempts. A
2-block sample grid covers the rotated footprint plus a 4-block margin; the maximum
surface spread is still six blocks and no sample may be more than three blocks from
the floor. Dry, solid support checks retain cliff/water/cave-shelf rejection.

New native templates omit side-courtyard columns outside the main path and plinth.
The runtime placement/restoration loop preserves those same columns. This retains
natural courtyard terraces rather than replacing the entire rectangle with a slab.
Short stone footings support the hall. Local grading joins the path to nearby soil,
trims modest uphill banks, and adds stone stairs at a lower front approach. The new
pass skips differences over three blocks and preserves water, tree trunks, containers,
artificial surfaces and stone hills. Saved absolute column targets replay without
progressively raising or lowering terrain. Existing saved blend plans remain readable.

An initial native-world test exposed that placing `structure_void` blocks in this
jigsaw template replaced terrain. The final template omits the entries altogether;
fresh natural-world height comparisons passed after that correction.

## Existing worlds

Unfinished legacy ruins migrate once their footprint is loaded and no arena is active
in the normal tick path. The move journals every offering inventory slot before any
block changes, then saves the world before clearing the receipt. It preserves item
components, the existing heart token, the site's coordinates, and all church geometry.
Replaying an interrupted move restores the saved slots exactly once. Unexpected block
entities in the destination defer the upgrade instead of deleting another inventory.

Completed sites are not moved or regraded. Legacy layouts remain packaged so a build
or committed restoration already in progress can finish consistently. Old terrain
that was overwritten cannot be reconstructed from the church journal.

## Validation

Passed:

- `./gradlew build --offline`, including existing Guardian/arena/guard/phase/beam checks.
- `python3 tools/build_guardian_church.py --check`; legacy layout files matched the
  pre-change layouts byte for byte. Independent coordinate checks confirmed the clear
  approach, retained legacy socket, unchanged ruin architecture and valid 24/14 settings.
- Isolated server lifecycle fixture: placement, heart recall, vegetation clearance,
  ritual admission, abort/retry, real boss death, restoration and rewards.
- Migration fixture: all four orientations, inventory slots/components, preserved
  token/building, interrupted receipt replay, no duplicate sites/items, completed-site exclusion.
- Terrain fixtures: legacy four-block ledge, smaller uphill/downhill transitions,
  entrance stairs, preserved courtyard ground and idempotent replay.
- Fresh normal-world fixture (seed 12345): vanilla locate, actual native generation,
  versioned/rotated socket discovery, heart chest, floor and original side-courtyard
  height preservation. Recorded origin: (-1082, 93, 128), clockwise 90 degrees.
- Server restart: completed church and previously collected treasure remained intact.
- Asset/socket validators, `git diff --check`, JAR integrity and fixture exclusion.

Logs: `/private/tmp/church-placement-lifecycle.log`,
`/private/tmp/church-placement-worldgen.log`, and
`/private/tmp/church-placement-recovery.log`. Disposable test worlds remain under
`build/church-smoke-run/` and `build/church-worldgen-smoke-run/`.

The regenerated `art/guardian_church/preview.html` and comparison PNG show the new
statue spacing on illustrative flat ground. Actual terrain appearance and exploration
frequency still need a human Lunar playtest; the server checks are not visual approval
or a multi-seed density survey.

## Installation

Installed in Lunar after all checks passed. Restart the full Lunar client to load it.

- Installed JAR: `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`
- Previous-build backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-church-placement-20260909-200118.jar`
- Matching source/installed SHA-256: `467cf5a41bd7100802b27e7d1a846a70f44200090375cc9012d5369d7cfb9fbc`
- Receipt: `/private/tmp/church-placement-install.json`

No human playtest of this installed build has been confirmed yet.
