# Guardian breaks Stone Wall after one absorbed hit

User requested a small Stone nerf: each Guardian attack should be blocked by a
player's Stone Wall as before, then break that entire wall so it cannot absorb
several heavy attacks. User also approved the wooden HUD from the previous build.

## Behavior

- Thrown rocks consume a contacted wall; its original cover still shields players
  from that rock's splash. Each later rock is a separate hit.
- The mouth beam breaks its contacted wall on firing. That wall's cover clips the
  remaining ticks of this same pulse, including its synchronized visual endpoint.
  The next beam can pass through the newly open space.
- Slam/melee shockwaves and the leap's traveling waves break walls when their
  advancing band actually reaches them. The wall shields its former covered area
  for the rest of that wave. A later wave, including a combo follow-up, is separate.
- Leap landing damage consumes walls inside its actual damage volume; all players
  shielded by that wall remain protected from this landing damage pass.
- Walls behind other solid cover are not consumed by an attack that cannot reach
  them. Only active, tracked player Stone Walls are removed. Titan Dome, trees,
  ordinary building blocks and untracked/admin-placed spell blocks are unchanged.

Breaking restores the wall's original cells through its temporary-placement
receipt, removes the active wall record and emits existing Stone dust/shards and
stone-break sound. It does not trigger the player's offensive shatter, grant flux,
drop blocks or reset cooldowns. Normal recast, resistance and cooldown rules are
otherwise unchanged; existing short resistance duration may naturally linger.

## Implementation

`GuardianWallImpact` is per discrete attack (one beam, rock, wave or landing).
It records the full block volumes of the wall consumed by that hit, so immediately
removing the blocks cannot let later ticks or later victims in the same hit leak
through. Nothing persists to future attacks or world saves. Waves discard this
state on expiry/interruption; beams reset it on begin/cancel.

`StoneAbilityHandler` owns wall teardown and queries. Wall positions now come from
`TemporaryBlockManager`'s placement receipt, rather than the requested footprint,
so partially overlapping casts cannot claim another player's existing wall cells.

The beam's endpoint and server wave particles respect consumed cover. The existing
continuous client wave ridge still samples current terrain; after destruction its
cosmetic ridge can traverse the former wall's shadow, which remains safe for that
wave. No networking or model/texture changes were needed.

## Verification

`tools/guardian_wall_smoke.init.gradle` runs real casts and production Guardian
attack code in an isolated server world. Passed: whole-wall teardown, first wave
protection for two players, subsequent-wave damage, full twelve-tick beam shielding,
subsequent-beam damage, rock splash shielding/subsequent-rock damage, leap landing
shielding/subsequent damage, natural cover shielding a wall, separate ownership of
partially overlapping casts, original floor preservation and no active-wall leaks.
The initial failure was a simulated player's stale head yaw placing the wall behind
the players; the fixture now explicitly asserts wall placement before testing hits.

Final build/contracts, VFX/Guardian/socket validation, whitespace and JAR integrity
checks passed. Human combat confirmation remains pending.

Installed September 10, 2026 at 13:07 local. Source/Lunar SHA-256 verified equal:
`52912ac1ff76bc56fa393c5acdf7ca1bc4435a9ce17e92e027fd5f0632eb3879`.
Backup: `/Users/antonlabas/.lunarclient/profiles/1.21/mod-backups/elementalwands-before-guardian-wall-nerf-20260910-130745.jar`.
