# Guardian shockwave cover — September 9, 2026

The user completed a full two-phase fight and approved its overall shape, then
reported that Nature flowers and Stone primary spikes blocked the rock wave.
Stone secondary walls and Nature ultimate trees should keep their protection.

## Fix

The wave previously used ordinary collision rays both to find the floor and to
check cover. A flowering azalea or raised spike could occlude the wave, or be
mistaken for the surface on which it should travel. The traveling wave also did
not invoke Nature cleanup; only the nearby fist/landing impacts did.

`GuardianWaveSurface` now applies the same narrow collision rule to client surface
rendering, server particle placement, and server damage. It ignores flowering
azaleas, moss carpet, lily pads and the internal Stone primary spike block. Stone
Wall, Titan Dome, logs/leaves and ordinary terrain retain their collision/cover.
This does not change general entity collisions or the laser/projectile raycasts.

Every traveling wave clears tracked Nature growth reached by its damage band,
respecting real cover. Destroying a seedling uses the existing cleanup path for
its owned patch and timer receipts. Plants behind a defensive wall or beyond the
wave survive. Local fist impacts use the same permissive cover check when clearing
small plants. The wave passes through primary spikes without deleting them or
changing their own ability timing/damage. Ultimate trees retain their existing
local-impact damage and continue shielding players from the wave.

The wave scans the existing tracked Nature placements, with a radial filter before
surface/cover checks. It does not scan the full arena or remove arbitrary terrain.
No new assets, animations, attack timings, phase behavior or health values changed.

## Verification

- Build and the existing Guardian/phase/guard/Nature/leap/beam/socket contracts.
- New isolated real-arena fixture reproduces old flower occlusion with the original
  collider ray, verifies floor height and visibility through all four spike stages,
  checks local smash and traveling-wave seedling removal, and verifies actual
  player damage behind primaries. Primary spikes and the protected arena floor survive.
- Actual wave damage is blocked by the Stone secondary wall; a live seedling
  behind that wall survives. A real grown Nature ultimate tree remains alive and
  blocks the wave. Ordinary stone also remains cover.
- Updated the existing Nature fixture to expect in-range plants to be cleared by
  the traveling wave and to test preservation with a separate out-of-range plant.
- Required asset validators, whitespace and JAR integrity checks.

Runner: `./gradlew -I tools/guardian_cover_smoke.init.gradle runServer --args nogui`.
It creates only a disposable build-directory world on a loopback ephemeral port.
Human confirmation of this specific fix remains pending; the user's approval of
the overall fight is preserved.

## Verified installation

Both live arena fixtures passed, including the broader Nature/Fire recovery,
growth, tree damage and cleanup checks. Clean build and required validators passed.
The concurrently completed Stone redesign installed the shared build before this
task's install step. This task verified its three compiled Guardian class entries
and preserved that newer release; no older texture package was installed.

- Build/installed SHA-256: `ef7c2b392c19e7444dae1cef99fdad98cd5f4cbc3de914598cb9e07307ac01cf`.
- Backup from the shared release: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-stone-redesign-20260909-230450.jar`.
- Evidence: `/private/tmp/guardian-cover-verification-20260909/`.
- Restart Lunar to load both updates. No user save was opened or modified.
