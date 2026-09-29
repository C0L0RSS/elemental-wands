# Current status

Reviewed September 29, 2026. This page contains outstanding work, not release history.
It records known verification limits; it is not a fresh installation receipt.

- **Hollow Necromancer (in progress):** reworked after the first co-op playtest (duels and
  sieges, heavier telegraphed hits) and again after a solo playtest where phase two felt
  easier than phase one and the siege waves were easy. Sieges now bring larger waves,
  reinforcement crawlers, quickened undead and Soul Fire Rain from the perch. The colossus
  charge tells only through body language with a random windup, and phase two adds Grave
  Dive, Soul Harvest and the soul split at a quarter health. Server fixtures cover the
  full two-siege flow with rain, and every phase-two mechanic. A skippable intro cinematic
  now opens each crypt fight: a zombie's soul is torn out and a pan reveals the Necromancer
  hauling it into his staff, then his slam lights the rim braziers. The intro clip and the
  dragged-soul clip await workshop approval; the camera shots await review from the crypt
  client fixture's `crypt-intro-*.png` frames. Necromancer spells now cast soul light (real
  block light plus a blue ground pool); FPS on weaker machines is untested.
  After a third (solo) playtest, Life Drain only starts within 10 blocks, with a 1.5-second
  windup, and breaks at 14 blocks. The soul split now happens once, and the blind body swings at
  random headings instead of fighting alongside the soul (30 soul damage solo). Pending a playtest.
  Pending: user approval of the new clips (workshop) and the fireball and soul models
  (`/.local-previews/soul-fire/`), then install and client checks; a solo and co-op Lunar
  playtest with `/ew necromancer log on` to tune health, wave sizes, rain damage, dive and
  split timing (the Hands→ambush and Hands→bite combos cost an iron-armored player about
  three quarters of their health).
- **Nature support pass (September 29):** Nature is the support element. Seed and Thornbite
  deal 4, Thornbite heals 75%, thorns deal ×1.5 to bosses and test the whole body footprint,
  and flowers bank up to 8 stolen health that their owner collects by left-clicking the
  flower or popping it with their own Seed. Springbloom launches on a run into its side, lasts 8
  seconds with a 6-second cooldown and flies flatter and farther. Pending: a Lunar playtest
  of Nature against the Necromancer, the new launch arc's feel and whether banking feels worth it.
- **Hollow undead:** the crawler, archer and brute form the Necromancer's siege waves
  (quickened there) and spawn at night. The GeckoLib files are proven to match the approved
  preview and the server fixture covers their combat rules. The walks were re-authored
  at chase speed with long strides and play at travel speed, fixing the foot sliding.
  Pending: human Lunar review of how they look and move in game, spawn frequency and balance.
- **Hollow Crypt realm:** the dimension, the dead-forest clearing, the naturally
  generated graveyard and the full fight flow are in place. That covers free headstone entry,
  a sealed party, spectating after death, kept belongings, the wipe reset, victory chests and
  one spell book per graveyard. The native client fixture passed all of these for a solo player,
  plus the operator commands, containment and protection. Pending: multi-player spectating
  (not automatable in the solo fixture), human Lunar review of brightness (ambient light 0.2,
  lifted fog and seven clearing soul lanterns), the Necromancer's body glow, the Life Drain glow,
  fog density and whether non-explosive fire spells burn the clearing's logs. The graveyard
  design is a first pass; its level-ground placement was checked on a dedicated server
  (seed 12345) and `crypt_client_smoke` (now using the nearest natural graveyard) passed, but it
  still needs a fresh-world Lunar look beside hills. Forest-biome yards can have natural trees
  growing inside them. A fight's end now releases everyone the crypt brought into the slot, not
  only the sealed party; the co-op victory release needs a human multi-player check, as does a
  second party member using the headstone while the slot is laid out (the fixture covers one
  player's repeated use). Known gaps: graveyard rewards are keyed by the skull's coordinates,
  so moving the altar and winning again yields fresh chests and another spell book; and
  graveyards have no exclusion from villages (the structure set's one exclusion zone is the
  Guardian church), so a village can overlap one.
- **Gravity Well:** Space has five learnable spells; every current elemental
  catalog now meets the five-spell minimum. Dedicated-server checks passed the
  12.25-block level throw, immediate floor/wall/entity impact, pull, early/automatic
  collapse, damage/XP, cover/allies/double protection, Guardian resistance,
  Blink chaining, impact recovery and covered cleanup cases. Native client checks
  passed bound input, visible mob pull, both collapse modes, HUD and hub text.
  The continuous inward stream, 0.4-second accelerated buildup and outward burst
  were verified with fresh native client footage; server checks passed delayed
  single-hit damage and cosmetic cleanup.
  The native gameplay recording was visually reviewed. Human Lunar/multiplayer
  balance and feel remain pending; this is not an installation receipt.
- **Astral Double:** Space now has five spells with Gravity Well.
  Dedicated-server checks passed the short toss, mirrored/simultaneous damage,
  Blink Rift chaining, one-use return, delayed recovery, invalid placement,
  ally protection, Guardian attack destruction and covered lifecycle cases.
  Native client checks passed for bound input, the owner skin and wand, mirrored
  fire, teleport chaining and HUD synchronization. Human Lunar/multiplayer feel
  remains pending. This status is not an installation receipt.
- **Updraft:** Wind now has five spells, with no cap on future additions. Native
  client checks measured a 10.00-block rise and passed normal horizontal control,
  midair activation, safe landing, and prepared-dagger/Waylay Dash combos. The
  launch emits an expanding floor ring of white smoke puffs. The optional
  hidden-window fixture records gameplay for MP4 previews. Waylay Dash's base
  and chained boosts are now 75% of their previous strength. Human Lunar balance
  feedback remains pending. Server checks passed for the exact eight-second
  cooldown, collision, safe landing, movement combos, and lifecycle cleanup.
- **Gale Daggers:** Server checks passed for the
  three-shot burst, fresh aim, damage, deferred/player-owned cooldown, cover,
  allies and cleanup. Native client checks passed for prepared models, real
  key/mouse input, held-click suppression and cooldown/HUD synchronization.
  Daggers now leave a trail of small cloud-white rings. Human Lunar/multiplayer
  feel and balance remain pending.
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
- **Guardian/church:** the fight moved from the sky floor into its own dimension, the
  Shattered Nave, on September 29; the user approved the hall in the browser preview. The
  nave, church (with its restart pass), cover and Nature server fixtures passed, and the
  native client tour photographed the hall. Every fight now opens with the "effigy wakes" intro
  (the heart flies from the caller's hand into the kneeling Guardian, which wakes in lightning
  and slams its fists together); the nave fixture plays it in full and skipped, and the native
  client fixture recorded it. Pending: the user's look at the intro video and a Lunar playtest
  of realm and intro together, including the fog distance, brightness (ambient light 0.3) and
  light shafts, which read fainter in the client screenshots than in the preview; how the
  caller's raised arm reads in third person with real skins; and a co-op check that every
  watcher's camera stays in step and that a partial skip waits for the rest. Earlier: user
  approval of the overall two-phase fight is recorded; later attack pressure, party combat,
  pedestal discovery and varied-seed terrain integration still need broader human feedback.

Do not infer the installed Lunar version from this page or archived release hashes.
Verify source/installed files when deployment status matters. Remove resolved items
rather than accumulating a permanent checklist of every past playtest.
