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
- **Pyre King:** the Haul and Snare clips and the Chain Scythe's bowed chain in the workshop.
- **Necromancer:** the remaining workshop clips, including the standalone dragged-soul
  clip, and the fireball and soul models (`.local-previews/soul-fire/`). Once approved they
  need installing and a client check.

## Solo Lunar session

Looks and balance that only a person playing can judge.

### Pyre King

- His health is a first guess; damage is set for plain diamond to netherite armor.

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
- **The new brain (built November 17, never played):** everything in the
  [first-playtest list](design/storm-roc.md#what-to-look-for-in-the-first-playtest). Record
  the fight with `/ew summit log on`. The low pass has no clip of its own (it flies in the
  spire hop's clip and the flight loop) and its bolts end at the bill's authored place, not
  where its turned head shows it; the lock marks for the bite and the scream are plain
  spark particles.
- The aerie: the gong, the call and the rewards; the gong's swing and lighting; how the
  levelled summit reads on more peaks; that stony peaks get no snow from the build; how
  findable aeries are without the hub map page; whether one Ultimate book per aerie is the
  right pace.
- The intro in play, start to landing.

### Hollow Necromancer and the crypt

- **The smarter fight (November 16):** the first fight against the chooser, the Soul Burst,
  the led hand rings and the frenzy that replaced the flying head. What to judge is listed
  in the [design notes](design/necromancer.md#what-to-look-for-in-the-first-playtest).
- A full fight with `/ew necromancer log on` to tune health, wave sizes, rain damage, and
  the dive timing. The Hands→ambush and Hands→bite combos cost an iron-armored
  player about three quarters of their health, and the hands now lead a moving player.
- The November 4 siege tuning: horde sizes, how the faster crawl and the brute's charging
  slam look, and frame rate with a full horde; and whether the November 16 damage buff
  (crawlers 4.5, brutes 12, arrows a third more) makes the horde a threat.
- Life Drain (starts within 10 blocks, 1.5 s windup, breaks at 14).
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

- **The November overhaul has not been played.** Step one (the chooser, strings, the
  later-locking leap, the Airburst and the heavier damage) and step two (the Sweep and the
  Scar) are built and pass their checks, but nobody has fought them, and the two new clips have
  not been seen in a game. The design notes list what to judge in the
  [first](design/guardian.md#what-to-look-for-in-the-first-playtest) and
  [second](design/guardian.md#what-to-look-for-in-the-second-playtest) playtest. The amber
  marks and bursts are plain particles, and the spikes are drawn in code, until a playtest says
  they need drawn effects.
- **The beam is out of the fight but its code remains** (`GuardianBeamAttack` and its rules,
  visual, clip and rehearsal). Delete it once the Scar has been played and kept; put it back in
  `GuardianCombatRules.weights` if the Scar is dropped.
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

- **Meteor in the boss arenas:** it used to burst on the invisible barrier lid over the
  Hollow Crypt and the Burned Throne Hall (fixed November 15). Check it lands on the floor
  in the Crypt, the Hall, the Shattered Nave and the Storm Summit. Outside the arenas it is
  unchanged: roofs and trees still stop it.
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
- **Springbloom and Updraft fall damage:** a launch sometimes landed with full fall damage,
  seen in the Pyre King fight (fixed November 18: protection now lasts until the player has
  actually lifted off). Check in a heavy fight that no launch landing hurts, and that a later
  ordinary fall still does.
- **Thornbite:** attack feel, silhouette and balance.
- **Space:** escape options after the Blink nerf; Gravity Well and Astral Double balance
  and feel.
- **Wind:** Updraft and Gale Daggers balance and feel, with Waylay Dash at 75% strength.
  Confirm the wand no longer dips while a dash recharges (fixed November 3).
- **Stone:** Faultline's steering, interruption feel, spike appearance, behavior on varied
  terrain and destruction balance. Its launch was reported as not noticeable on Hollow
  undead; check how the launch and the 1.5-block shove read, including on a struck player.
- **Fire:** recent Fire Leap and Flashover changes. Dragon's Pyre now runs along the floor
  whatever the pitch (November 17): its full 40 blocks aimed up at a boss, over slopes,
  steps and pillars. Open looks from the block-fire study:
  the Pyre carpet reads like lava; Meteor's warning ring is 5.4 blocks against a 10-block
  danger; the Fireball's head size; the Leap's wall burns longer than it hurts; the
  Flamethrower thins in first person.
- **Loadouts:** free-slot combinations and progression pace.
- **Sneak + right click** (November 17): it should cast while crouched in a fight, also
  at a mob standing in grass, and still open doors and chests, work the church sockets,
  gong and seal, and trade with villagers or mount a horse when aimed at them in reach.
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
  siege hordes at duo size; whether two players are now pressed hard enough (likelier
  strings, shorter rests, and strings that turn on the other player); the transformation cinematic's skip with several watchers; spectating
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

- No fixture has run on a dedicated server since the Nature rework; the November 10–11 runs
  were hosted in a singleplayer world on the Windows machine.
- The party fixture's restart half was not run after the Nature rework.
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
- `tools/check_assets.py` has not had a full run on the Windows machine since the
  structure files and the Pyre King's model were made to come out the same on every
  machine (November 12). Run it there once; a failure means a generator still depends on
  the machine. A run of only its Guardian checks there on November 13 found two such cases:
  - `tools/prepare_guardian_assets.py --check` calls the four Guardian body textures stale.
    Regenerated there, their pixels match the committed files exactly, but the last seven
    bytes of the compressed data differ (Pillow 13.0.0). The committed files were kept.
  - `tools/build_guardian_church.py`, `art/guardian_nave/build_layout.py` and
    `art/fractured_guardian/intro/build_intro.py` fail their `--check` although nothing they
    write has changed. That checkout converts line endings (`core.autocrlf=true`), and those
    checks appear to compare raw bytes.
  - `art/hollow_necromancer/v2/build_art.py --check` (November 16) calls the model and the
    animation JSON stale there even with the line endings set aside; the two generated
    Java sockets match. Nothing was regenerated, and what differs was not looked into.

## Known gaps

Accepted for now; fix when the feature is next touched.

- **Pyre King:** a player who only circles a pillar is hooked off it but can run clear of
  the Overhead that follows.
- **Launch fall protection in water or on climbables:** an Updraft cast on a ladder, vines or
  scaffolding or in water, or a Springbloom run-in launch from water or vines at the pad's
  edge, ends its flight before the client applies the launch, so that flight lands
  unprotected.
- **Dragon's Pyre over slabs:** its flames sit in the block above a bottom slab or deep snow,
  so over them they float half a block up.
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
- **Soul split leftovers:** the split is out of the fight. Its `colossus_split` and
  `colossus_collapse` clips and eight of its sounds (the five `freed_soul` events,
  `split.release_souls` and the two `collapse` events) are no longer played. Remove them or
  give them a use in the next art and sound passes. The frenzy's howl borrows
  `split.charge` and `split.release_shriek`.
- **Space particles** are unused since the void-glass redesign.
