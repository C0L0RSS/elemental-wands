# Fire Leap targeting — September 16, 2026

Requested behavior: aiming past the jump range or toward the horizon selects
ground within the existing 60-block horizontal cap. Aiming at a raised ledge's
side selects its top without requiring a visible top face. Use Omen's Shrouded
Step as an interaction reference, while retaining Fire Leap's actual flight.
Riot's [4.04 placement notes](https://playvalorant.com/en-us/news/game-updates/valorant-patch-notes-4-04/)
also describe improving valid ground placement for Shrouded Step; they do not
specify an algorithm to copy.

Keep the existing +4/-6 landing elevation limits, full standing-body arc clearance,
loaded chunks, world border, arena confinement, fluid restrictions and cooldown.
Preview and release share the destination resolver; the server independently
validates the complete route. Remove the redundant straight eye-to-feet ray,
which incorrectly rejects a reachable ledge even when the flight clears it.

The resolver preserves precise top-face aiming at nearby ground. For a side face,
it checks just inside that face for a supported collision surface within the
height limits. For a miss or distant ground, it projects down at the range cap.
If that column has no safe footing, it searches back toward the caster in half-block
steps. It does not automatically redirect around an obstructed flight arc.

## Verified

- `./gradlew build --offline`: compilation, packaging, Guardian regressions and
  party persistence checks passed.
- `./gradlew -I tools/fire_tuning_server_smoke.init.gradle runServer --offline --args nogui`:
  forward/horizon, shallow downward and upward aim; precise nearby aim; hidden
  three-block ledge; slab top; elevation bounds; nonfinite targets; wall/ceiling
  rejection; 60-block flight and existing wave/cooldown cases passed.
- `./gradlew -I tools/fire_leap_client_smoke.init.gradle runClient --offline -PhubClientAssets=/Users/antonlabas/.lunarclient/shared/assets -x downloadAssets`:
  actual hold/preview/cancel/release input, synchronized full-range flight from
  level aim, three-block hidden-ledge flight and both safe landings passed.
- All asset/export validators listed in AGENT.md passed. No production artwork
  changed. `git diff --check` and JAR archive integrity passed.

Inspected native-client screenshots:
[forward aim](fire-leap-targeting-verification/fire-leap-aim.png),
[hidden ledge aim](fire-leap-targeting-verification/fire-leap-ledge-aim.png), and
[ledge landing](fire-leap-targeting-verification/fire-leap-ledge-landed.png).
The landing sigil stays on the real terrain; a hidden top can occlude it while
the existing arc indicates the path. Human aiming feel and multiplayer latency
remain playtest items. The existing +4/-6 elevation limits still apply.

## Installed

Installed in Lunar Fabric 1.21.10 on September 16; source and installed SHA-256:
`364f908fe08894c7b7a4f77451f5f615beec2d6e8e84059e2651152a44ac190a`.
Previous JAR: `.local-backups/lunar/20260916-212822/elementalwands-2.2.0.jar`.
Restart Lunar to load this build. No public release was made.
