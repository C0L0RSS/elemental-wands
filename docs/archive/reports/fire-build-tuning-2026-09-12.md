> Historical record. Describes its original change, not necessarily today’s behavior.
> Use [current documentation](../../README.md) for the maintained guides.
> Installation hashes and test results below are historical snapshots.

# Fire playtest tuning — September 12, 2026

The approved flame visuals are preserved. Flamethrower now ignites touched enemies
for two seconds and ramps its direct damage from 0.5 to 2.5 per half-second pulse
over three seconds of uninterrupted spraying. Ordinary Minecraft burning supplies
lingering damage. Releasing, losing the input lease, changing item/world/slot or
overheating ends the channel and resets damage buildup; stored heat remains. This
keeps the early pulses much gentler than the former flat 5 damage. Range, heat gain,
overheat threshold/recovery, store price and the no-ground-fire rule are unchanged.

Fire Hop now works in the air as well as on the ground. It retains directional
movement input, forward fallback, light takeoff damage and a six-second cooldown.
The new impulse is 0.62 horizontally and 0.91 upward. A dedicated client lift
notification clears its grounded flag before the velocity update so ground friction
does not swallow the first part of the leap. Ordinary air steering can alter travel.

A temporary five-block safe-fall allowance covers the height supplied by the hop,
without removing damage from longer cliff falls. The allowance is removed on landing,
death, world change, disconnect or expiry; it is a non-persistent attribute modifier.
No invulnerability, landing damage or ground fire is added. Riding, gliding and
water restrictions remain. The store description now describes the airborne leap
and damage buildup.

## Verification and installation

The real server fixture confirms a pig survives the opening pulses and is ignited,
cover and range remain effective, release stops direct damage while burning can
linger, damage-ramp endpoints are bounded, overheat and item-switch behavior persist,
and airborne Hop respects its cooldown and saved progression. Using actual
`PlayerEntity.travel(Vec3d.ZERO)` over flat stone, the measured rise was 5.003 blocks
and horizontal travel was 6.102 blocks. The normal landing did not hurt, while an
explicit 12-block fall still dealt damage. Ordinary steering and obstacles can change
travel distance. Human sprint-jump feel remains a playtest item.

Clean build, all required asset checks, existing build regressions, JAR integrity
and native client UI/control checks passed. The updated store text was visually
reviewed. Evidence lives in `docs/archive/evidence/fire-tuning-verification/`. Flame artwork remains
unchanged; native store screenshots use fixture data over the title panorama.

Built and installed Lunar JAR SHA-256:
`e840821728bf8c53731cdb25a2f01529511c11c9695aa586d1d0e15999f51f57`.
The installed target already matched this build when the final copy was made;
`.local-backups/lunar/20260912-210704/elementalwands-2.2.0.jar` is therefore a snapshot
of the same release. Earlier release backups remain in `.local-backups/lunar/`.
Restart Lunar to load the update. Clients and server should use the same JAR.
