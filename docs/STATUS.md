# Current status

Open work only: what is waiting on a review, a playtest, a check or a fix. Last full
review: November 12, 2026.

Rules for this page:

- One bullet per open item, saying what to look at or decide. Delete the bullet when it is
  settled; do not mark it done.
- No test receipts. Which fixture covers what is in [testing](reference/testing.md) and
  the fixture itself.
- No playtest diaries or change history. Feedback and the decisions it led to go in the
  feature's reference or its design notes; player-facing changes go in
  [releases](releases/README.md).
- Nothing here says which build is installed in Lunar. Compare file hashes when that matters.

Most items wait on the developer playing in Lunar. Agents can clear
[checks still owed](#checks-still-owed) and [known gaps](#known-gaps) without a playtest.

## Review outside the game

Videos, browser previews and workshop clips waiting for the developer's approval.

- **Storm Roc opening summon:** the `roc_intro_client_smoke` video, which runs on through
  the summon.
- **Storm Roc intro (the snatch):** the in-game video.
- **Guardian intro (the zombie):** the in-game video of the retimed intro.
- **Pyre King:** the Haul and Snare clips, the intro's `throned` and `awaken` clips, and the
  Chain Scythe's bowed chain in the workshop.
- **First Wizard intro:** the stills (and, with `-PwizardIntroVideo`, the video) from
  `first_wizard_intro_smoke`: whether the dragon's death reads as the pool killing it with
  its own breath, whether the pour reads as the sky becoming the copy, the pool's look and
  height, the 82.5 s pace and every camera move. A first pass nobody has watched in Lunar.
- **Necromancer:** the remaining workshop clips, including the standalone dragged-soul
  clip, and the fireball and soul models (`.local-previews/soul-fire/`). Once approved they
  need installing and a client check.

## Solo Lunar session

Looks and balance that only a person playing can judge.

### Pyre King

- His health is a first guess; damage is set for plain diamond to netherite armor.

### First Wizard and the End

Nobody has played any of it in Lunar. Everything below has only been seen in fixtures.

- **The copy's look:** the plain skin with ink motes floating round it (tried at the
  developer's request after the first coat of ink); whether the motes read as ink and not as
  a Space spell, and are noticed at fight distance; whether it stays findable in the End's
  light. The fixtures used the default skin on a dark test floor.
- **Each of the ten spells:** whether its forming reads as a warning, its size (about two to
  three times the player's), its colours cooling to purple and ink, and how its dodge feels:
  the Fireball's sidestep, leaving the Meteor's circle, jumping Sky Shear's three crescents,
  the Waylay Dash's line and wake, the Stone Wall behind you, the Faultline's sidestep and
  throw, Thornbite's sidestep, breaking the Sentry Bloom before it delivers, Blink Rift's
  pool, running or dashing out of the Gravity Well.
- **The pace of the fight:** health (1000), the gaps between spells, how often each spell
  and combination comes up, and damage against diamond to netherite armor. All first guesses.
- **The change's cinematic** (built Nov 19, seen only in the fixture's stills and GIFs, by
  day on a test floor): whether the arm's wrench, tear, fall and melt read with weight and
  not as slapstick; whether the swelling reads as her flank bulging under the skin (boxes of
  the torso's own skin with ink veins) rather than as an ink patch; whether the head's bend
  reads as wrong in a good way while the face stays readable in phase two; whether 17 s is
  the right length mid-fight; and how its shots frame her in the End's light with other
  players nearby. Its sounds are vanilla placeholders.
- **Phase two:** whether the torn body reads as a wounded copy floating with three huge
  ink tentacles growing out of her left side (since Nov 20 spread apart, one low, one at
  shoulder height, one rearing over her, as the developer asked on Nov 19; seen so far only
  in the fixture's clips, and from close behind her left one still coils over another; at
  some points of their slow crawl the shoulder-height one's and the weapon's hooks close into
  rings seen from her front left), and
  not as rods, a cage, glass or a Space spell; whether her body stays readable beside them;
  whether the tentacles' part in each move (since Nov 20: throwing her into the Leap and
  slapping down round its landing, gripping the floor like an octopus's arms and heaving,
  weaving and feeding the orb through the Flame's and Hollow Purple's long holds,
  counterweight and clearance for the Slam and Sweep, flaring for the spells; seen only in
  the fixture's clips) reads as one body at fight speed and distance; whether the gripping
  arms' curled tips read from a player's height across the floor, and whether the three arms
  crossing close by her flank in those holds look like a knot from her left or front left;
  whether the
  textured floor marks read from across the arena at night; how it drifts, and each move's
  tell and dodge: jumping the change's full sweep and the Sweep, sidestepping the Slam,
  leaving the Leap's circle, getting behind the Flame, leaving Hollow Purple's lane. Its
  numbers (the flame's 8 twice a second, the finisher's 28 and 12-block orb, its 25 s wait)
  are first guesses, and bottling the flame's cloud has not been tried by hand.
- **The island:** the arena's floor, kerb and broken pillars, the arrival spot, the way
  home's basin at the centre and the four gateways after a win.
- **The portal:** arriving from a stronghold, being refused (and set beside the portal, clear
  of its lava) while another party fights, and where a fallen player wakes beside the portal.
- **An existing world's End:** a survival world with builds on the main island being held
  and then forced, the dragon egg set down beside the arrival, and a player living on the
  outer islands left alone. The fixture used a fresh world with planted blocks.

### Storm Roc

- The opening summon and the first tornado forming.
- The spire hop, now that its gaze no longer flips its head (fixed November 3).
- Balance and appearance of both phases: its reach at half size, the hunting tornadoes,
  phase two's density (three big tornadoes, strikes during the brawl, chain-smash damage)
  and the storm's darkness.
- The new look, including how far the talons bite into the rock (about a third of a block
  standing, up to a block while it scrabbles when stuck).
- The reworked animations: the spire hop and its banked turn (do the 1.4 s longer spire
  visits make the air stretch drag?), the landing and call, the slower 5.6 s takeoff, the
  dive chain (crouch, stoop, flare warning, the stuck heaves and the rip free at 5 s, the
  chain hop), the dive that waits for a landing to settle, and whether the slams' last-tick
  drop reads as a hit or a pop.
- The phase-two "fewer perches" idea.
- The aerie: the gong, the call and the rewards; the gong's swing and lighting; how the
  levelled summit reads on more peaks; that stony peaks get no snow from the build; how
  findable aeries are without the hub map page; whether one Ultimate book per aerie is the
  right pace.
- The intro in play, start to landing.

### Hollow Necromancer and the crypt

- A full fight with `/ew necromancer log on` to tune health, wave sizes, rain damage, and
  the dive and split timing. The Hands→ambush and Hands→bite combos cost an iron-armored
  player about three quarters of their health.
- The November 4 siege tuning: horde sizes, how the faster crawl and the brute's charging
  slam look, and frame rate with a full horde.
- Life Drain (starts within 10 blocks, 1.5 s windup, breaks at 14), the single soul split
  and the blind body.
- The transformation cinematic's length.
- Astral Double against him: the 70% echo bolt and the extra skull per visible double. Is
  the double surviving too long, and is Space's pace right?
- Crypt brightness (ambient light 0.2, lifted fog, seven soul lanterns), his body glow, the
  Life Drain glow and fog density. Do non-explosive fire spells burn the clearing's logs?
- The mausoleum in a fresh world: by day and night, beside hills and in a forest; whether
  the motes are noticeable but quiet; the veil and void animation speed; how far the dead
  woods should reach (18 blocks bare, fading out to 34).

### Hollow undead

- How they look and move in game, how often they spawn at night, and their balance.

### Fractured Guardian and the nave

- A full fight with `/ew guardian log on` to tune health (1,400 plus 1,000 per extra player
  is a starting estimate) and fight length, aiming at about ten tries and four minutes.
- The core pulse clip, the held slams, the pull's feel and the ring's readability. The pull
  is applied by each client, so only a real client shows it.
- The nave and intro together: fog distance, brightness (ambient light 0.3), the light
  shafts (fainter in client screenshots than in the preview), and whether the Guardian
  reads in the dark vaults during the zombie's-eye shot.
- Broader feedback on later attack pressure, pedestal discovery and how churches sit in
  varied terrain.

### Heavier animation pass (October 22)

- The reworked Guardian, Necromancer and undead clips were approved in the browser and
  have not been judged in Lunar.

### Spells

- **Nature flowers:** the bud's arc, reach and look; the vanilla hearts over a flower
  holding health; whether a fifth of the damage, up to 8, is worth walking over for; the
  three-block collect reach; whether a caster breaks their own flower by accident when
  firing past it.
- **Nature against each boss:** flower damage and pace, the 16-block range, how many
  flowers and for how long, heal sizes, ultimate charge rate, how the heads read while they
  turn, and whether Full Bloom's twelve seconds and buffs are too much or too little.
- **Nature looks:** the grown two-headed flower, the petal wave, the seed marker, the
  recoil and wilt poses, and the block-nature effects.
- **Springbloom:** the September 29 run-in launch, 8-second pad, 6-second cooldown and
  flatter arc.
- **Thornbite:** attack feel, silhouette and balance.
- **Space:** escape options after the Blink nerf; Gravity Well and Astral Double balance
  and feel.
- **Wind:** Updraft and Gale Daggers balance and feel, with Waylay Dash at 75% strength.
  Confirm the wand no longer dips while a dash recharges (fixed November 3).
- **Stone:** Faultline's steering, interruption feel, spike appearance, behavior on varied
  terrain and destruction balance. Its launch was reported as not noticeable on Hollow
  undead; check how the launch and the 1.5-block shove read, including on a struck player.
- **Fire:** recent Fire Leap and Flashover changes. Open looks from the block-fire study:
  the Pyre carpet reads like lava; Meteor's warning ring is 5.4 blocks against a 10-block
  danger; the Fireball's head size; the Leap's wall burns longer than it hurts; the
  Flamethrower thins in first person.
- **Loadouts:** free-slot combinations and progression pace.
- **Locate links:** the teleport link from `/locate` to a church, a graveyard, an aerie
  and a Burned Gate should each land on clear ground.
- **Wand-tip cast particles:** whether casts leave the wand tip convincingly in either
  hand, in first and third person.
- **3D wand:** the larger held pose and the cropped hotbar icon.
- **Wand sounds:** volume balance between spells, overlap in a fight, the retriggered
  Flamethrower and Hollow Purple loops, and the two explosion sounds Minecraft plays slower.
  Nobody has heard the Seed block hit, Sentry Bloom or Full Bloom sounds. Wind was accepted
  as good enough for now and wants another pass.

## Co-op or two-client session

Things one player cannot show.

- **First Wizard:** a party member arriving through the portal mid-fight and joining (the
  boss grows and keeps its share of health), a non-member being refused, how it shares its
  attention between players, the Sentry Bloom taking turns, and the Stone Wall against two
  players. The change's cinematic with two watchers: one skipping alone (view back, still
  held, until the other skips or it ends), one arriving through the portal mid-way, and one
  logging out mid-way (the fixture covers these with one player made to leave or join the
  fight, not a second client).

- **Other players' spell effects:** Fire, Wind, Space and Nature effects cast by someone
  else arrive over the block-fire, block-wind, block-space and block-nature events. None has
  been seen from a second client.
- **3D wand:** another player's affinity colors on their wand.
- **Pyre King:** a co-op fight.
- **Spells with a second player:** how Gravity Well, Astral Double, Gale Daggers and
  Thornbite feel in multiplayer, and chaining Springbloom launches between players.
- **Storm Roc:** a co-op fight, the aerie call with a party, and the intro with several
  watchers.
- **Necromancer:** a co-op fight with `/ew necromancer log on`, including the November 4
  siege hordes at duo size; the transformation cinematic's skip with several watchers; spectating
  after death; the victory release for everyone the crypt brought in; a second party member
  walking into the veil while the slot is being laid out.
- **Guardian:** co-op pressure up to four players; every watcher's intro camera staying in
  step; the zombie staying in the Guardian's fists on a remote server; a partial skip
  waiting for the rest.
- **Parties:** allied protection and party combat in real fights, and multiplayer timing in
  general.

## Weaker-machine performance

- Storm Roc: the model, cloud sea, cap cloud, storm sheets and textured tornadoes.
- Necromancer: soul light (real block light), and a full siege horde.

## Checks still owed

Automated work an agent can do without the developer.

- **First Wizard fight fixture:** `shear-jump` failed once in three complete runs (one of
  three timed jumps landed late) and passes when the Sky Shear cases run alone. The
  spell did not change between runs; the case's jump is timed through the client and
  slips when the whole fixture is running. Make the case independent of that delay.

- No fixture has run on a dedicated server since the Nature rework; the November 10–11 runs
  were hosted in a singleplayer world on the Windows machine.
- The party fixture's restart half was not run after the Nature rework.
- The Necromancer and Storm Roc fixtures last passed before the flowers' turning head,
  banked healing and Full Bloom were added; rerun them.
- The Thornbite heal buff inside Full Bloom has no fixture.
- Springbloom's launch and flight leaves are in no client fixture, and its revised arc has
  no fresh native-client measurement (the server fixture checks about a 15-block apex and
  21-block travel).
- The client fixtures do not press collect or break on a flower.
- The hub and progression regression checks have not been rerun since the free-slot and
  cooldown changes.
- Wand-tip cast particles have had no native-client check (right and left hand, first and
  third person, wide FOV).
- The Astral Double tuning against the Necromancer has had no client check.
- `tools/check_assets.py` had its first full run on the Windows machine on November 18,
  with the working files converted to LF. Thirteen checks failed there, none of them for
  files changed that day, so each still depends on the machine and needs looking into:
  `tools/validate_remaining_vfx_assets.py`, `tools/prepare_fire_assets.py`,
  `tools/prepare_wand_assets.py`, `tools/prepare_guardian_assets.py`,
  `art/fractured_guardian/phase/build_phase.py`, `art/fractured_guardian/intro/build_intro.py`,
  `art/hollow_necromancer/v2/build_art.py`, `art/hollow_necromancer/transform/build_scene.py`,
  `art/hollow_necromancer/grasping_hands/build_preview.py` (its `--check` also rewrites its six
  candidate files with CRLF line endings), `art/storm_summit/build_layout.py`,
  `art/storm_roc_mod/build_mod.py`, `art/storm_roc/build_runtime.py` and
  `art/pyre_king/build_art.py`. Do not regenerate that art on the PC to make them pass.

## Known gaps

Accepted for now; fix when the feature is next touched.

- **The End is open to everyone.** The five-shard key (`EndGate.mayEnter`) lets every player
  through because Wand Shards are not in the game. Any Survival player who enters the End
  starts the final fight.
- **Buckets in the boss realms:** the nave, crypt, hall and summit refuse a bucket through
  the block use, which a normal client also refuses, so it never pours. Unlike the End they
  have no check on the item use itself, so a modified client that sends only that could pour.
- **First Wizard:** a Gravity Well's burst does nothing to a player the Meteor of the same
  combination has just hit (the hurt cooldown swallows it). A raised shield that stops one
  of the Sentry Bloom's light seeds is knocked aside like one that stopped a heavy spell.
  The Stone Wall always goes behind its target, never between two players.

- **Pyre King:** a player who only circles a pillar is hooked off it but can run clear of
  the Overhead that follows.
- **Storm aerie:** an operator `/ew summit start` on a slot a gong call has reserved takes
  the called players into an operator fight with no rewards. A broken reward chest only
  comes back after the next win there. On jagged peaks an aerie can sit on a shoulder below
  taller spires that are too narrow to level.
- **Storm Roc intro:** sounds are vanilla stand-ins; the wand is not drawn in the puppet's
  hand; lightning bolts in chunks the client has not loaded do not draw; at short view
  distances the landing shot may build its terrain just after the cut.
- **Storm Roc `stuck` clip:** the head shake after it rips free (about 5.4 s) has keys that
  overlap by up to 0.04 s, so a few tiny moves are skipped. Left as it is.
- **Graveyards:** ones generated before the mausoleum keep the old headstone, whose rewards
  are keyed by the skull's position, so moving that altar and winning again yields fresh
  chests and another spell book. A graveyard only partly generated before the mausoleum
  update may finish with the new, larger layout. `/place structure` needs the chunks about
  40 blocks around the yard loaded. Nothing keeps graveyards away from villages.
- **Astral Double against the Necromancer:** the extra skull counts a double that is visible
  and at least eight seconds old; it does not measure continuous time in sight.
- **Nature sounds:** the flower throw plays Seed's cast audio, and collecting a flower has
  no sound of its own.
- **Sounds elsewhere:** bosses, mobs, cutscenes and world cues still use vanilla sounds.

## Not built yet

- **First Wizard:** spectating for a fallen player, a hint at the portal while a fight runs,
  a death, rewards and its own sounds. The full lists are in
  [First Wizard](reference/first-wizard.md).

- **Pyre King:** a death clip, a hit reaction, a reward, spectating for a fallen player and
  his own sounds; his warnings are particles. The full list, with the planned moves that
  were left out, is in [Pyre King](reference/pyre-king.md#not-built-yet).
- **Storm Roc:** the phase-change cinematic (a short in-engine stand-in plays) and a death
  (it vanishes).

## Parked ideas and clean-up

- **Animation ideas that need gameplay changes:** the Guardian's throws (their held-rock
  pose is sampled by the server through tick 44), the archer's 83° snap turn before it
  shoots, the Necromancer's walk playback speed, an earlier `perch_cast` trigger, his drain
  arm (needs the drain socket regenerated) and the colossus bolt origin.
- **Nature clean-up, once the kit is approved:** `EntangleTracker`'s vine stacks, the wrap
  art, the entangled HUD buds and the halved-recovery penalty have no source in play. Remove
  them or give them a use. The Overgrowth oak is kept on purpose and is not part of this.
- **Space particles** are unused since the void-glass redesign.
