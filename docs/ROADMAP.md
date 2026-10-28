# Roadmap

Long-term direction, first set September 30, 2026. This page describes plans, not
current behavior. See the [current references](README.md) for what the mod does today
and [status](STATUS.md) for outstanding verification. Each item is marked **decided**,
**proposed** (suggested, not yet agreed) or **open**.

## Vision

Minecraft survival stays intact. Players still explore, build, settle and fight mobs
in the open world. What the mod adds is an Elden Ring-style layer of objectives:
bosses hidden across the world. Each one moves a light story forward and makes the
player's magic stronger. Beating the final boss is the mod's version of beating the
End, and afterwards players keep living on their SMP as fully powered wizards.

Pillars:

- **The open world stays the game.** Bosses are objectives within survival, not a
  separate mode.
- **Bosses unlock power.** Magical progression runs through boss victories.
- **PvP happens in the real world.** There is no duel mode. Wands are for fighting
  other players the way vanilla weapons are, so SMPs can have factions and wars.
- **Lore is optional.** Players who look find deep lore. Players who only want to
  fight lose nothing.
- **The mod is meant to be public.** The goal is a release on Modrinth.

## Progression: Wand Shards

- **Decided:** each of the five shardbearer bosses drops a Wand Shard. A shard both
  raises the player's power cap and counts toward the key to the final boss.
- **Proposed:** shards gate the higher element levels (levels run 1–6 today). XP still
  fills levels, but only boss victories raise the ceiling. "Fully upgraded" means all
  five shards and maximum levels.
- **Proposed:** every eligible player in a co-op win gets their own shard, the way
  spell-book claims already work.
- **Open:** the exact level cap per shard count, and whether spell books remain as a
  bonus boss reward.

### Boss order

- **Decided:** any player may attempt any boss, but there is an obvious intended order.
- **Proposed:** power sets the order instead of locked doors. Each boss is tuned for a
  certain number of shards, so an early attempt is possible but hard.
- **Proposed:** each boss entrance states its suggested shard count and how many shards
  the player carries, as a warning rather than a lock.
- **Proposed:** early losses stay cheap. The arenas already keep belongings and reset
  on a wipe.
- **Proposed:** placement reinforces the order. Later bosses live farther away or in
  harsher places (the Nether, the End), so vanilla progression lines up with boss
  progression.

## Boss roster

- **Decided:** five shardbearers, plus a sixth, final boss.
- **Proposed:** each shardbearer holds one element's shard (listed below).
- **Proposed:** each new boss brings its own mob faction that also roams the open
  world, as the Hollow undead do for the Necromancer.

| Shard | Boss | Where | Shards expected | State |
| --- | --- | --- | --- | --- |
| Stone | Fractured Guardian | Ruined churches, Shattered Nave | 0 | Built, rebalanced October 3; playtest pending |
| Nature (life turned to death) | Hollow Necromancer | Graveyards, Hollow Crypt | 1 | Built, in tuning |
| Wind | Storm Roc | Mountain peaks, a sky realm above the clouds | 2 | Built with its aerie and intro (Oct 27); phase cinematic and death to come; playtest pending |
| Fire | New | The Nether | 3 | Open |
| Space | New | The End or the deep dark | 4 | Open |
| — | Final: the wizard who tried to hold all five | Opened by all five shards | 5 | Open |

Early seeds to discuss (none chosen):

- **Fire:** a forge colossus whose lava floods cool into platforms, or a phoenix that
  rises from its ashes unless finished in time.
- **Space:** a creature that exists in two places at once, or an arena collapsing
  into a singularity.

### Boss design rules

These come from the Guardian and Necromancer playtests:

- Attacks have a real, readable windup, and landing them hurts. Chip damage means
  nothing against armor and golden apples.
- Use a telegraph → commit → punish structure rather than attacks chosen by cooldowns.
- Traps and zones need a consequence. Players are always moving.
- Summoned waves are separate sections, not mixed into the duel.
- Keep the boss findable. Darkness must not cost readability.
- Every mechanic is solvable solo. A teammate makes it easier but is never required.

### Difficulty and co-op

- **Decided:** bosses are meant to be hard, like Elden Ring. The Guardian, the first wall,
  should take a new duo about ten tries. Retries stay cheap (belongings are kept, the intro can
  be skipped).
- **Decided:** the world's difficulty (Easy / Normal / Hard) is the intensity setting. Normal is
  the intended fight. Easy and Hard change vanilla damage scaling and the boss's pace.
- **Decided:** boss damage is sized against the vanilla armor expected at that point in the
  order. Better armor makes a boss easier, as with the Wither or the dragon. The Guardian is
  tuned against full iron.
- **Decided:** fight length grows with the order. The Guardian aims at about four minutes for a
  winning run; later bosses run longer.
- **Decided:** co-op pressure, not only health, grows with the party: shorter rests, faster
  cooldowns, attacks on the players the boss isn't facing, and targeting that follows threat.
  Pressure stops growing at four players; larger parties add only health.
- **Decided:** a boss's big set piece happens once, at the phase change. The Guardian's
  repeating guard break became its shell bursting open at half health.

### Wind: the Storm Roc

- **Decided:** the Wind shardbearer is the Storm Roc, a giant storm bird fought above
  the clouds. Its faction is the Windcallers.
- **Decided:** the arena is a roughly round mountain summit about 110–120 blocks across:
  mostly open, weathered rock with a few low rocks, cracks and snow. Cliffs drop away at
  its edge into a sea of clouds.
- **Decided:** the rock is flat, with no one-block steps to trip a dodging player, and the
  middle is left bare. Below the rim a short sheer drop gives way to a steep, buttressed
  flank that meets the clouds about 36 blocks down. Snowy peaks and low rock needles rise
  out of the clouds in the distance.
- **Decided:** in phase one the sky is bright and clear: deep blue, a high sun and a white
  sea of clouds, so phase two's storm has the most to change.
- **Decided:** the summit carries signs of the fight: lightning scars burned into the rock,
  the remains of past challengers (bones, rusted helmets, a broken sword, the Roc's red and
  white feathers caught in the cracks) and Windcaller ruins. The ruins are a shrine on the
  west rim with a copper wind rose and a small bell, half of it fallen with the cliff, and
  pale, copper-capped standing stones with lightning rods along the rim. No nest.
- **Decided:** the Roc is drawn at 1.25 times the art study's size: a 34-block wingspan,
  11 blocks tall standing and 23 long hunched to brawl. It was first drawn at 2.5 times
  for spectacle, but in the first playtest (Oct 15) it was far too big in first person.
- **Decided:** the Roc perches on tall, thin rock spires that rise out of the clouds
  beyond the edge. They stand apart from the summit, so players can never reach their
  bases. About six spires stand in a loose ring 20–30 blocks out from the edge. Their
  tops are 25–35 blocks above the summit, at uneven heights. The tallest stands due north,
  facing the landing. Each spire is a leaning needle of dark rock ending in a capstone
  about 14 blocks across, sized for the Roc's grip. It perches on the far edge facing the
  summit, with its tail hanging over the back. The capstones are scorched black where
  lightning lands.
- **Decided:** a player who uses a movement spell to get close to a spire is met with a
  caw that deals heavy damage and knocks them back.
- **Proposed:** the caw always knocks toward the summit. The damage is the punishment,
  and a player who still falls short drops into the clouds. It only triggers near the
  spires, never on players dodging near the edge.
- **Decided:** nothing in the fight knocks players toward the edge. The Roc's attacks
  hurt but don't shove. A player who falls off the edge dies.
- **Proposed:** because the flank below the rim has ledges, falling counts once a player is
  outside the rim and more than about 4 blocks below the summit, not only on reaching the
  clouds.
- **Decided:** in the dodging stretches it uses three abilities:
  - **Small strikes:** a small circle shows on the ground for a moment before lightning
    hits it.
  - **Large strikes:** larger circles that are slightly slower but deal much more damage.
  - **Tornadoes:** one or more stay on the battlefield. They hurt anyone inside, slow
    players more the closer they get, and pull harder on players in the air.
- **Decided:** each strike hits two or three times in quick succession, so a player can't
  wait out the first bolt and then walk straight onto its circle. Every bolt deals the
  same full damage, like lightning striking the same spot again and again. Small strikes
  hit twice and large strikes three times, about half a second apart.
- **Decided:** strikes are really hard to dodge and hit really hard, rather than filling
  the screen. Against full diamond a small bolt deals about 4 hearts and a large bolt
  about 8.
- **Decided:** the strikes use custom lightning models instead of Minecraft's lightning:
  a small bolt for small strikes and a bigger one for large strikes. The repeat bolts
  look and hit the same as the first.
- **Decided:** for most of the fight the Roc flies and perches on the spires, calling
  strikes, and the players' job is to dodge and survive. Then it commits to a heavy
  attack and lands in the arena for a while, which is when players deal damage. None of
  its heavy attacks chase a player: against players who never stop moving, those are
  either impossible to dodge or trivial.
- **Decided:** most spells can't reach the spires, so the dodging stretches are pure
  survival. They are as hard as we can make them, with strikes that hit hard and a Roc
  that moves fast. This is the hardest fight so far.
- **Decided:** the heavy attack is a big dive that throws the Roc onto the battlefield.
  There it fights with melee attacks, and players deal most of their damage.
- **Decided:** the dive ends with its beak stuck in the floor. It is stunned for a moment
  and can't attack, which gives players free hits. Once it pulls free it attacks freely.
  It takes no extra damage on the ground; it is simply in reach. Tornadoes stay on the
  battlefield while it is down.
- **Decided:** the dive comes while the storm keeps going. Strikes and tornadoes stay
  active as the Roc leaves its perch and climbs high above the summit with a scream, and
  its shadow grows over the spot where it will land.
- **Decided:** it lands on a large marked area, not on one player, and players escape by
  running out of it while the strikes still falling block some routes. A direct hit
  kills a full-health player in full diamond: the dive is the biggest hit in the fight.
- **Decided:** the impact releases lightning and a shockwave. The shockwave is a visible
  ring that rolls out along the ground in a cloud of smoke, with the lightning arcing
  inside it, and players jump over it.
- **Decided:** it stays stuck for about 6 seconds, since players need 2–3 of them just to
  run back from outside the marked area.
- **Decided:** while stuck it panics, screeching and scrambling with its feet and talons
  to get up. The legs hurt anyone who gets too close, so players aim for the neck and
  head. The wings splay out and beat weakly against the rock, and they are harmless.
- **Proposed:** in phase one the strikes stop on impact while the tornadoes stay. About a
  second before it rips free the scrambling changes and its neck heaves, so players
  know to back off.
- **Decided:** strikes are few and aimed, not a screen full. Half land on a player and
  half where the player is running, so running in a straight line doesn't dodge them.
  Starting numbers, to tune in playtests:

  | | Small strike | Large strike |
  |---|---|---|
  | Circle radius | 2.5 blocks | 5 blocks |
  | Warning | 1.0 s | 1.5 s |
  | Escape from the centre (0.3 s reaction, sprinting) | 0.8 s | 1.25 s |
  | How often, per player | about every 2 s | about every 6 s |
  | Bolts | 2 × 4 hearts | 3 × 8 hearts |

- **Decided:** circles can partly overlap but never stack on the same spot, since every
  bolt does so much. No circle's centre lands inside another live circle; a strike aimed
  there moves to that circle's edge instead.
- **Decided:** random strikes also fall around the summit for atmosphere. They are real,
  so a player who uses an ability to escape has a small chance of landing in one. Both
  sizes fall at random and hit exactly like aimed strikes.
- **Proposed:** about one random strike every 1.5 seconds across the whole summit, one in
  four of them large. A blind dash then lands in a live circle roughly once in 200.
- **Decided:** distant bolts also hit the cloud sea and the spires around the summit. They
  only look and sound like a storm and can't hurt anyone.
- **Proposed:** distant bolts are rare in phase one's clear sky and come often in phase
  two's storm.
- **Proposed:** a slowed player near a tornado can still escape a small circle, but not a
  large one. Tornadoes keep away from the edge, so their pull never drags anyone off.
  Their stronger pull on players in the air punishes constant jumping.
- **Decided:** one tornado at the start of phase one, a second once the Roc has lost half
  of phase one's health, and a third in phase two. This may be too strong; tune it after
  playtests.
- **Decided:** tornadoes are permanent once they spawn. They stay through the dive, the
  stuck window and the brawl.
- **Decided:** a player caught in a tornado's core is lifted, spun and thrown out with a
  few hearts of damage, always toward the middle of the summit.
- **Decided:** tornadoes can't be destroyed; they are just wind. Movement abilities such
  as dashes, blinks and walls help players get out of them.
- **Decided:** tornadoes drift slower than walking and keep apart from each other and from
  the edge. The dive's marked area never lands on one. They were meant never to chase
  anyone; the second-playtest build tries a slow creep toward players (see below).
- **Decided:** a player who was just thrown can't be caught again for a couple of seconds,
  so tornadoes can't juggle anyone between them.
- **Decided:** the tornado count is the same in co-op. The aimed strikes already scale,
  since each player draws their own.
- **Proposed:** the strike circles are white-gold and crackle as they form, so players can
  hear one forming behind them. They come quick and hard, unlike the Necromancer's slow
  lobbed fireballs.
- **Decided:** once free, it fights on the ground with three kinds of attack:
  - **Scream blast:** it tucks in and takes a heavy breath, then screams a blast of air,
    sound and lightning in a straight horizontal line ahead. Everyone in the line is hit,
    so players lined up behind each other are all caught.
  - **Beak swings and the Thunder Bite**, its melee. (Decided 2026-10-19: the small peck
    became the Thunder Bite, a big area attack, because a giant shouldn't nip one player.)
  - **Wing wave:** a big wave from its wings that pushes players away.
- **Decided:** every place to stand has an answer, so the brawl is about reading where
  its head points. Long windups and heavy hits, with starting numbers against full
  diamond:

  | Attack | Where it hits | Warning | Damage |
  |---|---|---|---|
  | Storm Scream | A line ahead, 6 blocks wide, across the summit, in racing crackles | A huge breath ~1.5 s while turning to a player; aim locks 0.3 s before | ~8 hearts, shoved back along the line |
  | Thunder Bite | Everyone within 5 blocks of its bill as it snaps shut in the air where its prey stood; it leaps up to 10 blocks to get there | Rears up slowly and holds, crackling, ~2 s; aim locks 1 s before | ~8 hearts, big push away |
  | Storm Sweep | Its whole front at chest height, then a crescent of crackles rolling out to 22 blocks | Neck coils far to one side ~1.25 s, bill crackling | ~5 hearts and thrown hard sideways; the wave ~3 hearts unless jumped |
  | Thunderclap | Players hugging it or behind it: a crush under it, then a ring rolling out to 16 blocks | Deep crouch, then leaps 9 blocks up with a whoosh and dust blowing out, hangs at the top | ~8 hearts crushed, ~3 and a big push from the ring; jump the ring |

- **Proposed:** the scream is too wide to sidestep casually. It can't be jumped, and
  behind the Roc is safe from it.
- **Decided:** it hurts a player it steps on as it shifts its feet or moves, but it never
  attacks with its talons.
- **Decided:** it mostly stays where it landed, but it has a movement ability in the brawl
  so it can't be pinned against the edge or kited from range.
- **Decided:** the brawl lasts 10–20 seconds. Damage thresholds decide when it takes off
  and when phase two starts. It takes off with a huge wing spread whose gust blasts
  everyone away from it without damage, then flies up.
- **Decided:** damage alone decides when it takes off, with no timer: a team that deals
  less damage simply brawls longer. Phase two starts at half health. If its health
  crosses that line during a brawl, the takeoff gust leads straight into the cinematic.
- **Proposed:** each landing ends once it has lost about an eighth of its health, sized so
  a typical team needs 10–20 seconds. Phase one then takes at least four landings.
- **Decided:** the brawl's movement abilities are scary with a hint of cartoon:
  - **Pounce**, its normal way to move, reworked 2026-10-19 as the **Raptor Strike**: it
    stalks low with its tail lashing and its head locked on its prey, coils, then flies
    flat and fast at them and strikes talons first, with no floor circle. Anyone under it
    is crushed, and the landing sends out a jumpable ring like the dive's. It mantles over
    the spot before settling. It doesn't get stuck.
  - **Stampede**, its answer to a player hitting it from range: it scrapes the rock like
    a bull and thunders across the summit at that player. It can't stop in time, so it
    skids for blocks, feet scrabbling and sparks flying, then whips its head around.
- **Proposed:** starting numbers for the movement abilities:
  - Pounce: about 1.6 s of stalk and coil (aim locks at 1.2 s), then 1 s in the air at
    most 4 blocks up. No circle. About 10 hearts to anyone within 5 blocks of its landing
    and 3 from the ring. It mantles for about 0.8 s and settles by 4 s.
  - Stampede: two scrapes over about 1.2 s while it turns to the ranged player, then a
    straight run about 6 blocks wide. About 8 hearts to anyone trampled, who is tossed
    aside. The skid leaves about 1.5 s of opening. It always stops well short of the
    edge, and it waits at least 8 seconds before stampeding again.
- **Proposed:** the dive always lands far enough from the edge that neither the wing wave
  nor the takeoff gust can push anyone off, so the no-edge rule holds. Being pushed into
  a tornado is a deliberate danger.
- **Proposed:** it dives on a steady rhythm, not by chance.
  Starting guesses: about 15–20 seconds in the air, 3 for the dive, 6 stuck and 10–20 of
  brawling.
- **Proposed:** no ward while it perches. A long-range spell that reaches it still
  counts, but the fight is balanced as if nothing can.
- **Decided:** the phase change is the one big set piece: a cinematic in which the sky
  turns dark and stormy.
- **Decided:** in phase two its tornadoes are larger, and its lightning strikes are larger
  and hit harder.
- **Decided:** phase two's identity is "the storm is on its side". Nowhere is safe, the
  ground is always live, and the Roc comes down to the players more often.
- **Decided:** phase two keeps phase one's loop: the air stretch, a heavy attack, the stuck
  window, then the brawl. What changes is the attacks it uses, not the loop.
- **Decided:** phase two adds a chain smash. Like the dive, it slams its beak into the
  ground, but several times in quick succession in different places, aimed at players.
- **Decided:** the first smash lands on a spot picked at a player. Each later smash is also
  aimed at a player, but it can only move a limited distance from the previous landing.
  It never seems to teleport, yet the whole chain still covers a lot of ground. The hop
  distance is a sweet spot to find in playtests.
- **Decided:** starting numbers from the chain smash sandbox: each hop moves at most 25
  blocks. The rest are the sandbox's defaults until playtests: 4 smashes, circles of
  radius 8, a 2 s warning before the first and 1.2 s before each later one, and a 0.4 s
  pause after each smash.
- **Built for the first playtest (Oct 14), not yet decided:** phase two with these starting
  picks, chosen to test in game rather than in a sandbox:
  - random strikes only during the brawl;
  - live ground after each large strike, 2.5 s at half a heart every half second;
  - strikes of radius 3.5 and 7 (5 and 9 hearts), and tornadoes of radius 7 with a third one;
  - a 15-second air stretch;
  - a lethal first smash like the dive, about 6 hearts from each later one, and a jumpable
    ring from every smash.

  The cinematic is a short in-engine stand-in, the summit keeps all its spires, and the
  defeat simply ends the fight.
- **Decided (first playtest, Oct 15):** the user played phase one and asked for:
  - half the size (see above);
  - eagle eyes set forward under a heavy brow, so its eyes, not just its beak, look at you;
  - a revamp of every animation, flight included: it mostly seemed to sit still while
    damage happened anyway. Its head moves like a bird's on the perch, snapping round to
    look at players;
  - a tornado that does something, with textured cubes instead of plain grey ones;
  - a thin layer of cloud over the summit for phase one's lightning to fall from, instead of
    a clear blue sky.
- **Built for the second playtest (Oct 15), not yet decided:**
  - **Spires:** on each one it lands, rears up and calls a large volley down, a strike for
    each challenger (up to four), with a bolt from its raised bill into the clouds, glares,
    then flies on. It dives only from a
    spire, after its call.
  - **Its gaze:** it holds still, then snaps its head to the player it strikes at or attacks,
    or to you. It banks into its turns in flight.
  - **Tornadoes:** they catch anyone within 45% of their radius, whirl them about 10 blocks up
    for 1.8 s, then throw them toward the centre with no fall damage. Their winds drag
    players in and around from 1.8 times their radius. In the air stretch they creep toward
    the nearest player at a quarter of walking pace; in the brawl they only wander.
  - **Cap cloud:** phase one's cap cloud is a broken grey deck about 58 blocks over the summit
    that frays out about 125 blocks from the centre. It gathers while a Roc fights there, and
    phase two's storm replaces it.
- **Proposed:** in the cinematic it climbs into the clouds and the arena goes quiet. The
  sky darkens and thunder rolls in. A huge bolt strikes it and it starts to glow
  white-gold. It dives back through the clouds as bolts shatter several spires, then
  lands on the tallest one left with its wings spread. Its body doesn't change; the
  weather does.
- **Proposed:** each smash marks a large circle where a player is, and the circle stays
  put once marked. In co-op each smash turns to a different player, like the Guardian's
  triple slams. It rips free at once between smashes, and only the last one leaves it
  stuck. Smash circles keep away from the edge, and the last always lands where the wing
  wave can't push anyone off.
- **Proposed:** in phase two the chain smash replaces the single dive. The sky strikes on
  its own, there are fewer perches, and strikes keep falling while it brawls.
- **Decided (Oct 16–19):** it is built from solid, chunky blocks with a detailed pixel
  texture, like a big modded boss, and looks like a bearded vulture: a rust neck, breast and
  crest, slate wings and back, rust feathered legs and a long wedge tail. Its face is a
  demon's, with steep spiked brows over glowing red slit eyes set deep in a dark mask. The
  crest flares when it screams, calls or winds up and lies flat in flight. Huge black
  sickle talons grip the rock and the spires. Its long hooked beak is still its main weapon
  and reads from a distance. Lightning crackles around its head; its body has no lightning
  marks.
- **Proposed:** its lightning is white-gold, unlike the Guardian's cyan core and the
  Necromancer's soul-blue fire. Once the storm charges it in phase two, the crackle never
  settles.
- **Decided:** the entrance is an aerie at the top of a mountain in the Overworld.
  Ringing a big storm bell there calls the Roc. It attacks, snatches the player and throws
  them onto the arena.
- **Built (Oct 27):** the aerie is an abandoned Windcaller summoning gong on a levelled peak
  summit whose ground comes from the biome. Striking it twice takes the striker and their nearby
  party up through the intro below. Until shards exist, a win fills reward chests at the aerie and each
  victor claims one Ultimate spell book there, once per aerie. Like the other bosses, it has
  no death yet; deaths come with the polish pass.
- **Decided:** every player sees their own version of the intro, with their own skin as
  the one snatched. Only that player appears in it; teammates meet them on the summit.
  It plays on every attempt and can be skipped.
- **Decided:** high in the air, the player struggles in the Roc's talon in a chaotic
  fight before it throws them at the summit. How they survive the landing depends on
  their wand's element.
- **Decided (Oct 27):** the intro is about 15 seconds, held Sneak skips it, and it plays in
  ten shots. Shots 1–6 are first person, then it cuts to third person for the struggle, the
  throw and the landing:
  1. **Quiet (0–2 s):** the last toll fades; wind, sideways snow, the light dims. The sky is
     empty, because the Roc stays unseen until the snatch.
  2. **The caw (2–2.5 s):** a loud caw from above and behind.
  3. **The snatch (2.5–3 s):** black talons snap into the edges of the screen, a jolt, and the
     ground falls away.
  4. **Carried off (3–5 s):** looking down between the talons as the peak shrinks; its wing
     shadows sweep over with each beat, and each beat lurches the view.
  5. **The reveal (5–6.5 s):** you look up and see it for the first time. It screams down at
     you, head bent, red eyes, lightning round its head.
  6. **Storm clouds (6.5–8.5 s):** cloud fills the view and lightning lights it from inside;
     the Roc is a black shape between flashes. This shot hides the move into the sky realm, so
     it replaces the loading screen and stretches while the summit loads, with a short minimum.
  7. **The struggle (8.5–12 s), close third person:** above the sea of clouds, the camera cuts
     beside the talon. The player, in their own skin, is clamped in its claws, kicking and
     pulling at its toes. Its head looms above, glancing down and snapping. The camera is
     shaky and circling, lit by flashes. The player frees their wand arm and strikes the talon
     in a flash of their element's colour.
  8. **The throw (12–13 s):** it shrieks and flings them; the camera follows behind them as
     they tumble toward the summit.
  9. **The landing (13–14.5 s):** they crash into the snow, roll and push up to their feet
     facing the home spire, as the Roc sweeps overhead and lands on it.
  10. **Hand back (14.5–15.5 s):** the camera glides into the player's eyes, the letterbox
      bars slide away, and the fight begins.
- **Decided (Oct 27):** the Roc is a near-black silhouette with glowing red eyes in the
  clouds and against the sky; lightning flashes rim-light it for a moment. Above the clouds it
  is dark but readable. The camera moves a lot: wingbeat lurches, shake, tilts and whip pans,
  with cloud layers and wisps across the view.
- **Decided (Oct 27):** build it breadth first: one shared landing for everyone now, with
  the element only colouring the wand strike's flash. The six element landings below come in
  the polish pass. A custom Roc voice comes later; for now its existing screech.
- **Decided (Oct 27):** each player sees only their own scene. Teammates are hidden on that
  client until control returns, and the fight starts once everyone has landed or skipped. A
  player who skips waits on the summit.
- **Built (Oct 27):** a first version of the intro, played at half the speed first sketched
  (about 30 seconds), after an animatic was reviewed in the browser. It plays in place of the
  loading screen: the server moves the player into the sky realm while the storm cloud covers
  their view, and the cloud shot holds until the summit has loaded and drawn. The Roc and the
  player are drawn as GeckoLib puppets on the scene's own clock, with the player's own skin,
  and the talon has its own jointed toes for the grip. Sounds are vanilla stand-ins and the
  wand isn't drawn in the puppet's hand yet.
- **Built (Oct 28), after the user's review of the in-game video:**
  - The landing insert holds 2 seconds longer on the perched Roc.
  - Its name moved below its feet so it no longer covers its head.
  - The fight now opens with the Roc summoning the first tornado from its spire, and every
    tornado touches down from the cloud over 2.5 seconds instead of appearing whole.
- **Decided:** the landings go bigger than the spells can in normal play.
- **Proposed:** every landing takes the same time, about 3.5 seconds from the throw to
  control, and ends in the same standing pose facing the Roc's spire. Their effects fade
  within a few seconds, so the arena starts clean.
- **Proposed:** a first pass at the six landings:
  - **No element:** the wand sputters a beam straight down that barely slows the fall.
    They crash, roll and stagger up.
  - **Fire:** they fall wrapped in flame like a small Meteor and land in a crouch as a
    ring of fire bursts across the rock.
  - **Wind:** Zephyr Strike wings unfurl mid-fall. They glide a curve past the Roc's
    spire and land in a gust that blows the snow outward. (Alternative: an Updraft just
    before impact.)
  - **Stone:** rock gathers around them as they fall until they are a boulder. They
    crater into the summit, and the shell crumbles off as they rise.
  - **Nature:** a Springbloom flower thrown below them launches them in one high, gentle
    arc, and they land in a burst of petals.
  - **Space:** they throw their Astral Double down ahead of them. It hits the rock and
    cracks, and they swap into its place as it shatters. (Alternative: a Blink just
    before impact.)
- **Proposed:** Windcallers are masked cultists who roam the mountain peaks. They dash,
  throw daggers and glide like wand players.
- **Proposed:** players of every element can fight it. Its damage window is on the
  ground, and no part of the fight relies on Wind's mobility spells.
- **Decided:** build it in the same order as the other bosses: the map first, then the
  fight, then the cinematics.
- **Open:** whether Windcallers appear in the fight; the intro's exact shots and timings,
  and the details of each landing; fight length and the gear it is tuned against.

## Hub map page

- **Decided:** boss locations appear on a map page in the H hub, not on a physical map
  item that can be lost or take up inventory space.
- **Proposed:** a parchment chart in the hub's style, with markers and distances, not
  a rendered terrain map. Minimap mods already cover terrain.
- **Proposed:** choosing an element reveals the nearest church. Each shard reveals the
  next intended boss. Any boss site a player walks near is added as discovered, so
  players who go off-order still build up their map.
- **Proposed:** markers carry a line of optional lore.
- **Open:** whether players can pin a site to show a direction arrow on the HUD.

## Element crystals

- **Decided:** crystals are found, one-time-use items, like golden apples. Using one
  affects a single spell. This is a new design; the retired crystal ores and
  crafting stay removed.
- **Proposed:** crystals are loot only (structures, boss chests, rare drops). Crystals
  share a cooldown, like ender pearls, so they can't be chained in PvP.
- **Open:** what a crystal does. The options so far:

| Option | Effect | Cost |
| --- | --- | --- |
| Combo spells | Every crystal/wand pairing casts its own hand-made spell (e.g. Fire crystal + Stone wand throws a molten rock in a big arc) | 20–25 new spells |
| Empower | The next non-ultimate spell is twice as strong, with bigger effects | One mechanic, plus visual scaling |
| **Infusion (proposed)** | A crystal of your own element empowers. Another element's crystal adds its trait to your next spell | Six effects |

Proposed infusion traits:

| Crystal | Trait added to the next spell |
| --- | --- |
| Fire | Sets targets alight and leaves burning ground |
| Wind | Heavy knockback, faster travel |
| Stone | Staggering impact |
| Nature | Heals the caster for part of the damage |
| Space | Pulls targets in or casts a second time |

With infusion, a standout pairing can still become a hand-made signature spell later.

## PvP and servers

- **Decided:** PvP is open-world, with no duel mode.
- **Proposed:** add a server config to disable spell block damage or respect land
  claims, so public servers can install the mod.
- **Proposed:** add a separate PvP damage multiplier, so boss tuning and PvP tuning
  don't fight each other.
- **Proposed, later:** grow parties, which already have allied protection, into
  factions with names and colors.

## Lore

- **Decided:** lore is optional and never gates progression. Its depth, and possibly
  a lesson, rewards players who go looking.
- **Proposed premise (draft):** the broken-ring emblem was once a whole ring that
  bound the five elements. A wizard tried to hold all five alone and it shattered.
  The wizard's messenger, the Storm Roc, carried the pieces to the ends of the world
  and kept the Wind piece for itself. The Guardian still watches over the pieces. The Necromancer tried to fill the
  hollow it left with stolen souls. The final boss is that first wizard, now Hollow.
- **Proposed lesson:** no one wizard can hold everything. This echoes how the elements
  split into roles and how play encourages parties.
- **Proposed:** lore lives in item text, map markers, environments and hidden notes.

## Public release

- **Decided:** publish on Modrinth, or a similar platform, once the mod is at a good
  point.
- **Proposed:** release a public beta early, after the core loop is in place, rather
  than waiting for the final boss.
- Before release:
  - Players must install the exact GeckoLib alpha the mod requires.
  - Write the mod page and choose the public version numbering (local builds are 2.2.0).
  - Add server config (see above) and check dedicated-server behavior.
  - Keep developer commands operator-only.
  - Test performance on weaker machines.

## Phases

| Phase | Goal |
| --- | --- |
| 0 | Clear the playtest backlog in [status](STATUS.md) with a long co-op and PvP session |
| 1 | Core loop: shards, power cap and final-boss gate; retrofit the Guardian and Necromancer; hub map page; entrance warnings; lore skeleton; server config |
| 2 | Public beta |
| 3–5 | Wind, Fire and Space bosses, each with its faction. Crystals can land once the core loop exists |
| 6 | Final boss, then the 1.0 release |

## Open questions

- Boss concepts for the Fire and Space slots, and the final boss.
- Whether one element per shardbearer is the right frame.
- What crystals do.
- The exact level cap per shard.
- How faction mobs are split between boss waves and the open world for each new boss.
