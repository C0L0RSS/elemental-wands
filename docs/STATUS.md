# Current status

Reviewed September 23, 2026. This page contains outstanding work, not release history.
It records known verification limits; it is not a fresh installation receipt.

- **Gale Daggers:** Wind now has four spells. Server checks passed for the
  three-shot burst, fresh aim, damage, deferred/player-owned cooldown, cover,
  allies and cleanup. Native client checks passed for prepared models, real
  key/mouse input, held-click suppression and cooldown/HUD synchronization.
  Daggers now leave a trail of small cloud-white rings. Human Lunar/multiplayer
  feel and balance remain pending; the fifth Wind spell is still to be designed.
- **Wind artwork:** bright cloud-white gusts, impact puffs, wings and ability icons
  replace the dark fractured textures. Native client inspection passed for blade
  synchronization and target damage, equipped Zephyr wings and landing completion,
  with screenshots of the HUD, casts, dash trails and landing burst. Human Lunar
  appearance feedback remains pending. The Wind pressure server fixture now passes with the
  seven-block primary range, including ordinary-target hits and Guardian checks.
  Its simulated player now sets head direction explicitly for aimed casts.
- **Springbloom:** Nature now has five spells. The dedicated-server fixture covers
  purchases, cooldown, hostile/rim catches, collision, destruction and cleanup,
  plus planting through forest-floor foliage and restoring covered plants. Partial
  flowers beside obstacles share a synchronized visual/collision footprint.
  Native client checks passed for the approved model, real mouse casting, a
  last-second pod catch, protected landing, and approximately 20.7 blocks upward /
  14.8 forward with movement held. Human Lunar/co-op feedback on placement,
  timing and coordinated chaining remains pending.
- **Thornbite:** Thorn Lash is now a committed single-target flytrap bite. The
  Nature server fixture passed for combat. The revised visual launches and returns
  at the wand tip along the hand side; its native client fixture passed with
  right/left hands, third person, wide FOV and turning during return. Human
  Lunar/co-op feedback on attack feel, silhouette and balance remains pending.
- **Stone techniques:** Faultline now reaches 20 blocks at four times its initial
  speed, with a stronger upward launch and a 0.6-second movement interruption.
  Dedicated-server mechanics and native client movement/render checks cover the
  initial implementation. Human Lunar/co-op feedback is still needed for steering,
  interruption feel, spike appearance, varied terrain and destruction balance.
- **3D wand:** native client previews and model checks passed in the redesign
  session. The larger held pose and cropped hotbar icon still need the user's
  Lunar feedback. Actual two-client affinity appearance remains unverified;
  a simulated remote holder check is not equivalent.
- **Five free spell slots:** build/whitespace checks were recorded as passing.
  The updated hub/progression server fixtures were not rerun in that session.
  Shared Basic recovery, element leveling, spell-book rewards, and current Thorn
  Lash tuning are present in code; this documentation cleanup did not retest them.
- **Wand-tip cast particles:** cast bursts, the fractured beam, the Flamethrower
  stream and projectile wakes now start at an estimated wand tip instead of the
  caster's face. The build passed; the tip placement (right/left hand,
  first/third person, wide FOV) has not been checked in a native client or Lunar.
- **Human playtesting:** free-slot combinations, progression pace, recent Fire
  Leap/Flashover and Nature changes, and multiplayer timing remain balance/feel
  work. Automated mechanics checks do not settle these questions.
- **Guardian/church:** user approval of the arena floor and overall two-phase
  fight is recorded. Later attack pressure, party combat, pedestal discovery,
  and varied-seed terrain integration still need broader human feedback.

Do not infer the installed Lunar version from this page or archived release hashes.
Verify source/installed files when deployment status matters. Remove resolved items
rather than accumulating a permanent checklist of every past playtest.
