> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

# Sticky Flashover — September 13, 2026

Flashover now sticks to hostile mobs and players as well as solid surfaces.
Its blast radius increases from three to four blocks, as approved. The existing
three-charge cap, 500 Fire Flux price, half-second arming delay, one-second throw
recovery and six-second detonation recovery remain.

## Behavior

An airborne ember checks the closest living contact before the first solid block.
The caster, allies, allied pets and spectators are skipped. A wall still blocks
attachment to a target behind it. Attaching does not deal direct damage.

Attached embers follow the target's position, body rotation and current dimensions.
They use a synchronized target ID and a small body-relative offset; the renderer
samples the target's interpolated pose so movement does not separate the model
from its host. This is a generic body attachment, not a per-mob animated bone socket.

Embers expire **30 seconds after being thrown**. Attachment, moving and priming do
not restart the timer. Losing the host through death, removal or dimension change
removes the ember. Water extinguishes it. Existing caster death/disconnect/unequip
and block-support cleanup remain. Targets keep ordinary invulnerability protection.

Press Spell alternate (R by default) to prime armed charges within 32 blocks in
placement order. The first explodes immediately, the second five ticks later and
the third after ten ticks (0 / .25 / .5 seconds at normal tick rate). Each has its
own sound and visual burst. Pending charges flash and remain disarmable. Destroying
one skips its explosion without making the following charge fire early.

The sequence samples each charge's actual current position and checks cover and
allies at that blast. A moving host can carry later explosions to different enemies.
The range check happens on activation; moving afterward does not cancel a committed
sequence. Caster lifecycle/affinity/encounter changes still cancel it safely.

A per-sequence victim ledger keeps base damage at **6 + 2 + 2**, at most ten per
victim, even when all charges overlap. A victim newly entering a later blast receives
its own first six-damage hit. Each blast still goes through armor, shields and other
ordinary damage handling. The custom `elementalwands:flashover` damage type is tagged
as explosion damage and bypasses only hurt cooldown, allowing the quarter-second
follow-ups to register without bypassing armor or invulnerability. No fire damage,
block destruction, automatic chain reaction or knockback explosion API was added.

## Implementation

`FlashoverEmberEntity` owns living contact, body-relative tracking, priming and
attachment cleanup. `FlashoverManager` owns the short committed sequence and its
per-victim damage ledger. `FlashoverRules.POP_INTERVAL` is five ticks and `RADIUS`
is four. `FlashoverEmberRenderer` follows the interpolated host and pulses when
primed. The store now describes sticking, staggered activation and expiry.

The existing Fire model, particles, sigil and raster assets are preserved.

## Verification

`tools/sticky_flashover_server_smoke.init.gradle` checks player and mob attachment,
ignoring allies, movement/turn following, wall occlusion, actual-position blasts,
four-block splash, the 6+2+2 cap, queued disarming/cancellation, host removal and
water cleanup. Fixtures explicitly mark simulated players as loaded and inspect
scheduled results after the due tick, avoiding Minecraft login protection and
mod callback-order assumptions.

The updated original Flashover server fixture covers expiry, arming/cap, cover,
allies, disarming and cooldown persistence with delayed damage. The focused native
client fixture checks synchronized attachments, renders a moved/turned cow,
triggers the delayed sequence and verifies the HUD and Controls page.

```sh
./gradlew -I tools/sticky_flashover_server_smoke.init.gradle runServer
./gradlew -I tools/flashover_server_smoke.init.gradle runServer
./gradlew -I tools/sticky_flashover_client_smoke.init.gradle -PhubClientAssets=/Users/antonlabas/.lunarclient/shared/assets runClient
```

All three fixtures passed. Native screenshots were inspected and captured in
`docs/archive/evidence/sticky-flashover-verification/` with server/client result markers. Final
`./gradlew clean build`, required asset/export validators, `git diff --check`
and JAR integrity passed.

Installed at `/Users/antonlabas/.lunarclient/profiles/1.21/mods/fabric-1.21.10/elementalwands-2.2.0.jar`.
Source/installed SHA-256: `9a64c9661a7d3d4cfcf6e73e22a69cee99c4be10d06f81b896935f2cd4c21f9b`.
Previous JAR backed up in `.local-backups/lunar/20260913-143924/`.
Restart Lunar before testing.
Human combat balance, different mob silhouettes and multiplayer latency remain
playtest checks.
