# Current status

Reviewed October 3, 2026. This page contains outstanding work, not release history.
It records known verification limits; it is not a fresh installation receipt.

- **Hollow Necromancer (in progress):** reworked after the first co-op playtest (duels and
  sieges, heavier telegraphed hits) and again after a solo playtest where phase two felt
  easier than phase one and the siege waves were easy. Sieges now bring larger waves,
  reinforcement crawlers, quickened undead and Soul Fire Rain from the perch. The colossus
  charge tells only through body language with a random windup, and phase two adds Grave
  Dive, Soul Harvest and the soul split at a quarter health. Server fixtures cover the
  full two-siege flow with rain, and every phase-two mechanic. A skippable intro cinematic
  now opens each crypt fight: a zombie's soul is torn out and a pan reveals the Necromancer
  hauling it into his staff, then his slam lights the rim braziers. The final slower intro
  and camera sequence were approved September 29; native verification and Lunar installation
  were recorded for that version. Separate workshop/model approvals remain below.
  Necromancer spells now cast soul light (real block light plus a blue ground pool);
  FPS on weaker machines is untested.
  After a third (solo) playtest, Life Drain only starts within 10 blocks, with a 1.5-second
  windup, and breaks at 14 blocks. The soul split now happens once, and the blind body swings at
  random headings instead of fighting alongside the soul (30 soul damage solo). Pending a playtest.
  Space was too strong here: Astral Double tuning makes its echo bolt
  deal 70%, and each skull volley can add one skull for a currently visible double at least
  eight seconds old (server fixture covered). This does not measure continuous time in sight.
  Pending: installation/client checks of that tuning and a playtest of double survival and
  Space's pace; separate approval of the remaining workshop clips, including the standalone
  dragged-soul clip, and fireball/soul models (`/.local-previews/soul-fire/`), followed by
  their installation/client checks; a solo and co-op Lunar
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
  generated graveyard and the full fight flow are in place. That covers free walk-in veil entry,
  a sealed party, spectating after death, kept belongings, the wipe reset, victory chests and
  one spell book per graveyard. The native client fixture passed all of these for a solo player,
  plus the operator commands, containment and protection. Pending: multi-player spectating
  (not automatable in the solo fixture), human Lunar review of brightness (ambient light 0.2,
  lifted fog and seven clearing soul lanterns), the Necromancer's body glow, the Life Drain glow,
  fog density and whether non-explosive fire spells burn the clearing's logs. The graveyard's
  level-ground placement was checked on a dedicated server (seed 12345). The mausoleum
  (unbreakable, pale marble doorway, vortex veil, void windows, door hint, motes, court return)
  and the withered woods around the yard passed `crypt_client_smoke` on the nearest natural
  graveyard, including no leaves within 14 blocks of the door and no second fight while
  standing in the veil. Pending: a fresh-world Lunar look at the mausoleum by day and night,
  beside hills and in a forest; whether the motes are noticeable but quiet enough; the veil and
  void animation speed; and how far the dead woods should reach (18 bare, fading out to 34
  blocks). A fight's end now releases everyone the crypt brought into the slot, not only the
  sealed party; the co-op victory release needs a human multi-player check, as does a second
  party member walking into the veil while the slot is laid out (the fixture covers one player
  standing in it). Known gaps: graveyards generated before the mausoleum keep the old headstone,
  whose rewards are keyed by the skull's coordinates, so moving that altar and winning again
  yields fresh chests and another spell book (the mausoleum can't be moved). A graveyard only
  partly generated before this update may finish its missing chunks with the new, larger
  layout. `/place structure` now needs the chunks about 40 blocks around the yard loaded. And
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
  Earlier native client checks passed for the approved model, real mouse casting,
  a last-second pod catch and protected landing. Their flight measurements predate
  the September 29 flatter arc and run-in launch. The current server fixture checks
  roughly a 15-block apex and 21-block travel; these are simulated server measurements.
  Fresh native-client measurement of the revised arc and human Lunar/co-op feedback
  on placement, timing and coordinated chaining remain pending.
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
  Faultline also shoves targets about 1.5 blocks along the wave. The server fixture
  covers the launch and shove on a zombie, a chasing Hollow crawler and a Hollow
  brute; the launch was reported as not noticeable on Hollow undead in Lunar, so how
  the launch and shove read on screen, and the shove on a struck player, need a playtest.
- **3D wand:** native client previews and model checks passed in the redesign
  session. The larger held pose and cropped hotbar icon still need the user's
  Lunar feedback. Actual two-client affinity appearance remains unverified;
  a simulated remote holder check is not equivalent.
- **Five free spell slots:** five unrestricted slots, per-spell cooldowns and shared
  Basic recovery are implemented, alongside element levels, tiered spell-book credits
  and personal rewards. Preserved September 17 server/native receipts cover the earlier
  progression, persistence, credits and UI implementation. They do not establish a fresh
  pass for later free-slot/cooldown changes or current tuning. Pending: relevant current
  hub/progression regression checks and human feedback on free-slot combinations and pace.
- **Wand-tip cast particles:** cast bursts, the fractured beam, the Flamethrower
  stream and projectile wakes now start at an estimated wand tip instead of the
  caster's face. The build passed; the tip placement (right/left hand,
  first/third person, wide FOV) has not been checked in a native client or Lunar.
- **Human playtesting:** free-slot combinations, progression pace, recent Fire
  Leap/Flashover and Nature changes, and multiplayer timing remain balance/feel
  work. Automated mechanics checks do not settle these questions.
- **Guardian difficulty (October 3):** the Guardian was rebalanced to be about a ten-try boss for a
  new duo in full iron on Normal, with a winning run of about four minutes. Its hits were raised so
  heavy ones take about 45% through iron. Slams sometimes hold before smashing and now hit with the
  fists. The repeating guard break is gone: at half health the shell bursts open as the phase
  change, and phase two adds a pull-then-blast core pulse. In co-op, pressure grows up to four
  players (shorter rests, faster cooldowns, back volleys at the players it isn't attacking, and
  threat-based targeting). World difficulty sets the intensity. Health is now 1,400 plus 1,000 per
  extra player, a starting estimate. Contract checks and the shell, phase, combat, cover,
  Nature, wall, Wind and Space server fixtures cover the mechanics. Pending: a solo and co-op
  Lunar playtest with `/ew guardian log on` to tune health and fight length. Also pending: an
  in-game look at the core pulse clip, the held slams, the ribs staying open (clipping against the
  slam and throw), the pull's feel and the ring's readability. The pull is applied by each
  client, so a fixture with fake players cannot show it; it needs a real client.
- **Guardian/church:** the fight moved from the sky floor into its own dimension, the
  Shattered Nave, on September 29; the user approved the hall in the browser preview. The
  nave, church (with its restart pass), cover and Nature server fixtures passed, and the
  native client tour photographed the hall. On September 30 the "effigy wakes" intro was
  replaced at the user's request: the Guardian now smashes a zombie, tears it in two and throws it
  over the players (approved in the browser animatic, then retimed slower on request). Pending: the
  user's look at the in-game intro video and a Lunar playtest of realm and intro together,
  including the fog distance, brightness (ambient light 0.3) and light shafts, which read fainter
  in the client screenshots than in the preview; whether the Guardian reads in the dark vaults
  during the zombie's-eye shot; and a co-op check that every watcher's camera stays in step, that
  the zombie stays in the Guardian's fists on a remote server, and that a partial skip waits for
  the rest. Earlier: user
  approval of the overall two-phase fight is recorded; later attack pressure, party combat,
  pedestal discovery and varied-seed terrain integration still need broader human feedback.

Do not infer the installed Lunar version from this page or archived release hashes.
Verify source/installed files when deployment status matters. Remove resolved items
rather than accumulating a permanent checklist of every past playtest.
