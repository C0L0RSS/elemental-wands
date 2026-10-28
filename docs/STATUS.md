# Current status

Reviewed October 3, 2026. This page contains outstanding work, not release history.
It records known verification limits; it is not a fresh installation receipt.

- **Storm Roc (both phases):** the summit has a sealed, operator-started encounter.
  - **Opening (Oct 28):** on its home spire the Roc summons the first tornado. It screams a bolt
    into the clouds, the storm strikes the spot, and the funnel touches down over 2.5 seconds
    as it beats the wind at it, under a wall cloud it hangs from. Every new tornado now forms
    this way, harmless until formed.
    - **Verified:**
      - `roc_server_smoke` checks that no tornado appears before the call, that one forms
        harmlessly with a player at its core, and that the second comes only after the summon.
      - `roc_client_smoke` photographs the scream, the forming funnel and the formed tornado.
      - `roc_intro_client_smoke`'s video runs on through the summon.
    - **Pending:** the user's review of the video, then a Lunar look and a playtest.
    - **Note:** in the approved `stuck` clip, the head shake after it rips free (about 5.4
      seconds) has keys that overlap in time by up to 0.04 seconds. A few of its tiny moves
      are skipped. Found while guarding key order; left as it is.
  - **Phase one:** repeated lightning, permanent drifting tornadoes, a marked dive and jumpable
    shockwave, the pinned-beak opening, seven grounded actions and health-driven takeoffs.
  - **Phase change:** at half health a short in-engine stand-in for the planned cinematic.
  - **Phase two (Oct 14):** a client-side thunderstorm with storm clouds, rain and flashes;
    larger, harder strikes with live ground after the large ones; strikes during the brawl;
    swollen tornadoes plus a third; and the chain smash in place of the dive.
  - **Defeat:** it vanishes, with no death yet. Falls, death and disconnect eliminate a
    challenger; a wipe or reset releases the group for retry.
  - **Aerie (Oct 27):** survival players reach the fight from an abandoned Windcaller gong on
    Overworld peaks: a GeckoLib gong in a small dark oak frame on a summit that worldgen levels,
    surfaced by the biome. Striking it twice takes the striker's nearby party up, and a loss sends
    them back. A win fills a reward chest at its foot, and each victor claims one Ultimate spell
    book per aerie. It replaced a first bell-shrine design the user turned down.
    - **Verified:**
      - `aerie_server_smoke` passed 34 steps in all four rotations, including strikes through
        the part blocks, part shapes matching the round plate, and the swing reaching nearby
        players. The client fixture confirms a strike mid-swing restarts the swing.
      - On seed 12345 the worldgen fixture checked six aeries: two frozen, two jagged and two
        stony. In every one, all 195 columns of the box sit at the floor, the build makes no
        snow (stony ones are bare stone), the remains rest on the ground, and no bare wall
        nearby is taller than 10 blocks.
      - The native client photographed snowy and stony aeries, all four rotations, at rest
        and mid-swing.
      - The Roc and summit regressions still pass.
    - **Pending:**
      - a Lunar look and a solo and co-op playtest of the bell, the call and the rewards;
      - how the levelled summit reads on more peaks in Lunar, and that stony peaks never get
        snow from the build;
      - how findable aeries are without the hub map page;
      - whether an Ultimate book per aerie is the right pace.
    - **Known gaps:**
      - an operator `/ew summit start` on a slot a call has reserved takes the called players
        into an operator fight with no rewards;
      - a broken reward chest only comes back after the next win there.
      - the gong model's swing and lighting in Lunar.
      - on jagged peaks an aerie can sit on a shoulder below taller spires that are too narrow
        to level.
  - **Intro (Oct 27):** a first version of the snatch, about 30 seconds plus however long the
    summit takes to load. It plays in place of the loading screen: the server moves each called
    player into the realm only once their client's storm cloud covers the screen, and the
    cloud holds until the summit has loaded and drawn. The fight starts once everyone has
    landed or skipped (held Sneak).
    - **Verified:**
      - `aerie_server_smoke` passed 38 steps with simulated clients:
        - players are held at the gong's stand, unhurt, unmoving and unable to attack or use
          items, and carried up only after their cloud;
        - the fight starts as they land;
        - a disconnect at the gong, and a death on the summit, end that player's scene;
        - a client that never reports the cloud is left at the gong at its deadline, and can't
          be called again at once;
        - a scene that never lands is sent home after its length and grace, and the fight
          starts without it.
      - `roc_intro_client_smoke` played it in a real client on seed 12345:
        - no loading screen;
        - 33 ticks in the cloud after the move;
        - the camera handed back and the fight started.
      - The same test skipped it twice, each time with no loading screen and with the fight
        starting:
        - from the grip, in the aerie: the cloud covered the screen and cleared on the player's
          own view;
        - from the struggle: it cut straight back.
      - The frames were turned into a review video.
    - **Pending:** the user's in-game review of the video, then a Lunar look and a solo and
      co-op playtest.
    - **Known gaps:**
      - sounds are vanilla stand-ins;
      - the wand isn't drawn in the puppet's hand;
      - lightning bolts in chunks the client hasn't loaded don't draw;
      - the struggle opens about 200 blocks from the summit, so at short view distances the
        landing shot may build its terrain in the first moments after the cut.
  - **Not yet built:** the phase-change cinematic and a death.
  - **October 14 fixes:**
    - First pass: client hurtboxes, the safe spot under the neck, stampede targeting,
      permanent threat, stacking, shields, the pounce aim and the stuck pose.
    - Second pass:
      - a crash when a tornado throw kills a player;
      - spires were safe during the brawl;
      - hit stacking was compared before armor;
      - air chip could skip the last landing;
      - ground sampling repeated every frame;
      - the scream's and the rings' hit volumes went beyond what they draw.
    - The tornado was rebuilt from blocky cloud cubes and the bolts thickened.
  - **First playtest (Oct 15):** the user played phase one. These changes followed:
    - it is drawn at half the size;
    - its eyes face forward like an eagle's under heavy brows;
    - every clip was revamped, and there are new spire landing and lightning-call clips;
    - its gaze snaps to players and it banks in flight;
    - each spire visit ends in a call that brings the large volley down;
    - tornadoes are textured, catch from further out, drag players in, whirl them up and
      throw them, and creep toward players in the air stretch;
    - a cap cloud over the summit is what phase one's lightning falls from.
  - **New look (Oct 16–19):** a chunky block model with a bearded-vulture texture, the Demon
    face (glowing red slit eyes in sockets, brow spikes), an animated crest, thick legs with
    big sickle talons, and a tail that lifts off the rock while the feet are down. The
    server fixture passed with the new poses and the client fixture with the final model.
  - **Verified:** the server fixture covers both phases and the spire call (44 checks), and
    the native client fixture has 29 reviewed screenshots, including the spire hop's push-off
    and landing, the call bolt, bolts from the cap cloud, the textured tornado, the charge,
    the storm, the chain smash and live ground.
  - **Pending:**
    - human Lunar/co-op balance and appearance: the half-size reach, the hunting tornadoes,
      phase two's density (three big tornadoes, strikes during the brawl, chain-smash damage)
      and the storm's darkness;
    - weaker-machine performance with the model, cloud sea, cap and storm sheets and the
      textured tornadoes;
    - the phase-two "fewer perches";
    - the new look in Lunar, including how far the talons bite into the rock (about a third
      of a block standing, up to a block while it scrabbles when stuck);
    - the reworked animations in Lunar: the spire hop and its banked turn onto the spire (and
      whether the 1.4 s longer spire visits make the air stretch drag), landing and call (its
      crackles and command glare), the slower takeoff (5.6 s, climbing out past its spire and
      banking round onto it), and the dive
      chain (the dive's crouch, stoop and flare warning, the stuck opening's heaves and its rip
      free at 5 seconds, the chain hop); the dive that waits for a landing to settle; and whether
      the slams' last-tick drop reads as a hit or a pop.
  - See the [summit reference](reference/storm-summit.md) and [testing guide](reference/testing.md)
    for commands and verification boundaries.
- **Heavier animation pass (October 22):** 15 of the Guardian's 18 clips (all but its throws
  and intro), 20 Necromancer clips and 11 hollow undead clips were reworked for weight and
  readable windups, keeping every clip length and server timing. The Guardian's chest now stays
  shut after the shell break. Approved in the browser previews on October 23; pending in Lunar.
  Ideas that need gameplay changes: the Guardian throws (their held-rock pose is server-sampled
  through tick 44), the archer's 83° snap turn before it shoots, the Necromancer's walk
  playback speed, an earlier `perch_cast` trigger, its drain arm (needs the drain socket
  regenerated) and the colossus bolt origin.
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
  Phase two now opens with a 34.6-second transformation cinematic in which he grows into
  the colossus. Its browser animatic and its native crypt recording were approved October 6,
  and server fixtures cover it. It was installed into Lunar that day, built together with the
  Guardian rebalance. Pending: a playtest of its length and
  skip in co-op.
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
- **Blink Rift nerf (October 6):** Blink no longer leaves a return rift and its
  cooldown rose from 6 to 10 seconds. Astral Double and Gravity Well server
  fixtures cover the cooldown and chaining. Pending: a Lunar playtest of Space's
  escape options.
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
  Blink chaining, one-use return, delayed recovery, invalid placement,
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
  in-game look at the core pulse clip, the held slams, the pull's feel and the ring's readability. The pull is applied by each
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
