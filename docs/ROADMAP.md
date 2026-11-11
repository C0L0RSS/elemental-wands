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
| Fire | Pyre King | The Nether | 3 | Redesign started Nov 4: a swordsman with a chained sword; every move animated; a first-pass fight and his hall are in the game for operators (Nov 10) |
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

### Fire: the Pyre King

The redesign started on November 4. The first version (look and kit) was shelved, and
only the concept carries over. The kit below went through seven reviewed drafts before
the developer's direction made it melee first.

#### Decisions

- **Decided:** the Fire shardbearer is the Pyre King, the cursed old king of a fallen
  Nether kingdom, held by a curse as an eternal fire thing. His weapon is a fire sword.
- **Decided (Nov 4):** he is a swordsman first. Close-range sword strings are his main
  attacks and his heaviest hits.
- **Decided (Nov 4):** the sword is chained to his wrist. The chain is how he deals with
  distance:
  - He throws the sword at players who are far away, waits a moment, then pulls it back.
  - He hooks players and drags them to him.
  - He pulls himself to the sword when players run too far.

  Every chain move exists to bring the fight back to sword range.
- **Decided (Nov 4):** he is fast and aggressive, but a little slower than a sprinting
  player. Playtests set the exact speed.
- **Decided (Nov 4):** he reads how players move.
- **Decided (Nov 4):** for now the fight is a pure duel, with no summoned waves.
- **Decided (Nov 4):** armor and Protection reduce his damage normally. Working up to a
  maxed Protection build is a fair way to make him easier, as in souls-likes.
- **Decided (Nov 4):** he is human-sized, about 5 blocks tall: a scorched, armored king.
  He is a suit of armor, not a skeleton; the Necromancer already has the bones.
  At his size a realistic human face looks wrong, so he wears a scorched, cracked gold
  death mask with ember eyes, above a long ash beard. The approved concept is
  `art/pyre_king/concepts/direction-d-mask.png`. His armor is charred black with tarnished
  gold trim, under a heavy gold crown with a red gem. The Fire shard glows in his chest,
  and a burned crimson cape covers his shoulders and back. One curse chain carries a
  padlock (three chains were too noisy).
- **Decided (Nov 6):** no knee-length coat. A close fauld hugs his hips so his legs and
  footwork read, and the legs and arms are heavy and strong under small pauldrons.
- **Decided (Nov 9):** the chains that tear loose in Unbound are on fire.
- **Proposed:** each of those chains ends in a barbed hook, which is what catches on the
  pillars.
- **Proposed:** the mask cracks and falls away at the phase change, revealing a burned
  slag face. The model keeps the mask as its own part so this stays possible.
- **Built (Nov 4):** a review model in `art/pyre_king/`.
  - Upright he is 5 blocks; he leans into a two-handed plough guard with the burning blade
    raised up and out.
  - The cape covers both shoulders and his back; the head sits in a gorget, the fists are
    full-size, and one curse chain carries the padlock beside the shard.
  - The blade fire is drawn upright along the whole blade by the preview, rising off the
    blade's upper edge.
  - A polish pass (Nov 5) gave the crown openwork points, made the beard's locks taper in
    strands, cleared the grip (slimmer cuffs, a snug shackle, a smaller tether), and ended
    the fire's ladder-rung look on a slanted blade.
  - Animation has started: idle, walk, run, the Royal Cut string and throw → wait → pull
    back, with both fists held on the grip by IK.
  - After the developer's review (Nov 6), pass 11 removed the coat, thickened the legs
    and arms and shrank the pauldrons. It rebuilt the walk and run with the feet under
    the hips and a working upper body, made the edge lead in the Royal Cut, and laid the
    blade back over the shoulder in the throw's wind-up, like an overhand axe throw. The
    fire now streams behind a swung blade. A follow-up straightened the legs (each knee
    on its hip–ankle line, full thighs, bigger boots) and hung the pauldrons from the
    chest, so the sword arm no longer rocks them in the run. The developer approved the
    result on Nov 9.
  - Pass 12 (Nov 9) added the other two sword strings, which the developer approved:
    - **Kingsguard Thrust:** he thrusts from a high guard beside his head, the point on
      your face, then lunges and drives the point down onto your chest.
    - **Turning Cut:** he glances back over his shoulder, spins left and cuts flat and low
      through the half behind him. He ends facing back the way he came; the manifest tells
      the game to turn him 180° when the clip ends.
  - Pass 13 (Nov 9) animated the rest of the kit. The developer approved it on Nov 10:
    - **Chain Scythe, low:** he casts the sword out along the floor, crouches, and spins
      once like a hammer thrower, dragging the blade round at ankle height.
    - **Chain Scythe, high:** he whirls it over his head like a lasso, the blade at head
      height.
    - **Zip:** the chain hauls him off his feet to the stuck sword. He tears it free and
      spins into one cut round the spot he landed on.
    - **Lunge:** he drops low, winds the chain round his forearm, slings the sword short
      and flies after it into the same spinning cut.
    - **Hook:** he throws the sword like a javelin, then drags it back hand over hand.
    - **Overhead Sentence:** he raises the sword over his head and brings it straight
      down, lunging in. Phase two's third Royal Cut ends on the same cut.
    - **Drag Down:** a rising sling sends the sword up at a player overhead, and he hauls
      it down with both hands.
    - **Unbound:** he drives the sword into the floor and lets go, and six chains burst
      from his chest. They sweep low, hook on, and reel back in before he tears the sword
      free. The model carries six marker bones that say where the chains' ends are.
      After the developer's review, the chains burn along their whole length and end in
      barbed hooks. They whirl round him for as long as they are out, curved like a
      pinwheel, and spiral in as he hauls them. The proposed reel-in along straight ember
      lines no longer matches that.
    - Zip and Lunge move him inside the clip; the manifest tells the game where he ends up.
  - The Chain Scythe's chain now bows with its swing as Unbound's chains do, awaiting
    the developer's review.
  - In the game (Nov 10): he can be summoned and made to play any clip, with his blade
    fire, the sword's chain and Unbound's chains drawn by game code.
  - A first-pass fight was built the same day. See [Pyre King](reference/pyre-king.md)
    for what it does; the sections below remain the plan it was built from.
  - Not yet animated: a death, a hit reaction, and the mask cracking away.
- **Decided (Nov 4):** his sword is a large two-handed English longsword. He fights with
  both hands on the grip, in readable longsword guards: high over the shoulder before an
  overhead cut, point forward before the Thrust. To throw, he lets go with his left hand
  and hurls the sword one-handed, paying out the chain from his right wrist to the
  pommel. He catches it two-handed on the pull-back.
- **Decided (Nov 4):** the sword is oversized for him, about 4.5 blocks overall with a
  blade of about 3.5, so his sweeps reach down to players much smaller than he is.
- **Decided (Nov 4):** fire covers the whole blade and burns upward, whatever angle the
  sword is at. It is drawn separately from the sword model each frame as block fire along
  the blade, like the Fire spells. The sword model itself carries only a hot, glowing blade.
- **Decided (Nov 6):** when the blade is swung or thrown, its flames lie back and stream
  behind it like a whipped torch, leaving a short fading wake, then settle upright again.
- **Decided (Nov 4):** the first version's story stays for now, and can change once the
  frame story is written (see [Lore](#lore)). Like Sardanapalus, he set his own palace
  alight rather than surrender. Like Koschei, his death is caught in the Fire shard, which
  was in that fire. He can't die while it holds him, and taking the shard lets him go.
  - **Proposed:** the Storm Roc brought him the Fire piece to keep. It made his kingdom
    grander than ever, and he came to think of it as his own.
  - **Proposed:** when the piglins besieged his palace for it, he shut his court in the
    throne hall, piled his treasure around the shard and set the hall alight. No one
    cursed him: the curse is his own grip.
  - **Proposed:** the Nether fortresses were his kingdom's roads. The piglins' love of
    gold began with the gold they picked from his ashes.

#### Decided for the fight (Nov 10)

The developer settled these before the first pass was built. Where they differ from the
proposals below, they win.

- **Weighted chance.** He is a rule system, like a souls-like boss: distance and what the
  player just did decide which moves he weighs, and chance picks among them. The proposal
  below used fixed thresholds and no rolls.
- **Fire in both phases.** His attacks leave fire as proposed below. The hall's floor
  itself never does damage.
- **Healing is punished.** Eating or drinking in the open draws a throw or a Lunge.
- **No stagger.** Nothing interrupts him; the openings are the ones he leaves.
- **Tight openings.** He is free again less than a second after his last blow, not the
  1.2 s pause proposed below.
- **Gear (Nov 11).** The fight is balanced for plain diamond to netherite armor. The
  damage table proposed below (34 for the sword) is replaced by the one on the
  [Pyre King](reference/pyre-king.md#the-fight) page (21).
- **Shields (Nov 11).** A shield stops one blow and is knocked aside for 5 s.
- **Pillars (Nov 11).** The hall's pillars are scenery and cover on the floor only. Nobody
  stands on one: a player who lands there slides off, unhurt.
- **Hiding (Nov 11).** The hall never helps him against a player behind a pillar ("the
  equivalent of plot armor"), and nothing of his goes through the stone: the developer did
  not want the Scythe to. He gets round it himself. The first try, a Cast past the pillar
  and a Zip to it, was "a really good start" but so short that "it might honestly look
  faster if he just walked it". The developer asked for a much faster pull to open ground
  where the player is visible, then at once a thrown sword that drags them to him: the
  Haul and the Snare. More than one answer is welcome; a Lunge round the pillar is the
  second. The developer also approved the line a Haul flies along burning, as his own
  doing, so that Hauls wall off the way round a pillar.

#### Core rules (proposed)

- **One mistake, one hit.** A combo is a run of blows less than 1.2 s apart. Each player
  takes at most one heavy hit per combo. A throw and its pull-back are separate tests,
  because the wait between them is longer.
- **Every read freezes visibly.**
  - Throws lock their aim 0.4 s before release; the ember line along the aim turns solid
    at that point, and a late turn beats it.
  - The direction of a follow-up is fixed as soon as its tell starts.
- **Two beats.** Every sweep and landing gives a warning (a chain snap or a ring of
  embers), then lands on a clang 0.45 s later.
  - The server judges a low sweep by the player's feet at the clang, with 2 ticks of
    latency forgiveness. Take off between the snap and 0.1 s before the clang.
  - For a high sweep, be crouching by the clang.
- **Pace.**
  - He runs at 4.5 blocks/s (5.2 in phase two).
  - He walks at 3 blocks/s through tells, while the sword is out, and in recoveries.
  - He runs at his target for 0.5 s before each pick.
  - He is slower than a sprinting player (5.6) but faster than a player backing away while
    facing him to shoot (about 4.3). Minecraft only sprints forward, so a player can shoot
    him or outrun him, but not both.
  - He never teleports.
- **Damage tiers.** Raw damage on Normal:

| Source | Damage |
| --- | --- |
| Sword in hand (sword strings, the Overhead Sentence, the landing cut after a Zip or Lunge) | 34 |
| Chain and thrown sword (Scythe, throw, pull-back) | 24 |
| Floor fire, on contact and then each second | 14 |
| Unbound set piece | 30 |
| Drag Down yank | 8 |
| Hook tug | 6 |

#### How he picks a string (proposed)

He picks from his target's position once the 0.5 s run ends, using fixed thresholds and
no random rolls.

| Target is | He uses |
| --- | --- |
| Above the floor (see Drag Down) | Drag Down |
| Within 6 blocks | A sword string |
| 6–14 blocks | Chain Scythe. (As proposed it ignored cover; since Nov 11 stone stops it, see Hiding above) |
| 14–26 blocks, or more than 14 blocks away behind cover | Cast |
| Beyond 26 blocks with a clear line | Hook |

**Repeats:** if he would pick the same string a third time in a row against the same
target, he changes it. A sword string becomes a low Scythe; a chain move becomes a Lunge,
which brings him to the player.

#### Sword strings (proposed)

| When | String | Tell | Answer |
| --- | --- | --- | --- |
| Default | **Royal Cut**: two cuts across a 150° arc in front of him. He tracks you until the snap, then freezes and steps up to 1.5 blocks; the reach is 4.5 from where he lands. The second cut has no step and turns up to 45° toward your dodge side | 0.9 s, then 0.45 s | Back out of reach. The closer you stand, the earlier you must go |
| You backed straight away from his last sword string | **Kingsguard Thrust**: he steps 2 blocks and thrusts straight along the frozen aim, 8 blocks long and 1.5 wide | 0.8 s; aim freezes at the snap | Sidestep |
| Someone has stood behind him for 1 s | **Turning Cut**: he spins and cuts the 180° behind him, with a reach of 5 | 0.7 s; snap, then clang | Step out of reach, or round to his front |

- After every sword string he pauses for 1.2 s, standing still. This is the punish
  window for close-range builds.
- If you escape a sword string with a movement spell, his next string is a Cast aimed at
  your landing spot.

#### Chain moves (proposed)

**Chain Scythe (6–14 blocks).** The sword whirls round him on 14 blocks of chain, with a
0.9 s tell.
- **Low:** sparks drag along the floor, and the hit zone is 0–0.6 blocks up. Jump.
- **High:** the sword is raised and whistles, and the hit zone is 1.55–3.2 blocks up.
  Crouch; a crouching player is 1.5 blocks tall.
- He swings high if the target jumped in the last 2 s.
- Being beyond 14 blocks is safe from both.

**Cast (14–26 blocks).** This is throw, wait, pull back.
1. **The throw:** an aimed throw with a 0.8 s tell. The sword sticks into the floor at
   the aimed point, or into the pillar or Stone Wall in the way.
2. **The wait:** 1.5 s. He can't attack, and he walks toward the target, paying out
   chain.
3. **Then one of two things:**
   - **Reel (the usual case):** after a 0.7 s tell, he pulls the sword back along an
     ember-marked arc that bows up to 6 blocks toward the target's dodge side. Every
     second pull-back runs through a player's position instead. He then recovers for
     1.4 s.
   - **Zip:** if the target is more than 20 blocks from him, or the sword is stuck in
     cover, he pulls himself to the sword instead. A 5-block ember ring marks the landing
     1.4 s before his spinning cut lands. He then recovers for 1.8 s.

**Hook (beyond 26 blocks).** He fires the sword like a harpoon, with a 1.0 s tell.
- **A hit** drags you over 1 s to 4 blocks in front of him, into the Overhead Sentence.
  The Sentence is a 2-wide, 8-long vertical cut with a 0.7 s tell; sidestep it.
- **Breaking free:** casting any movement spell during the drag snaps the chain.
  Springbloom counts when the pad is placed, Juggernaut when it starts.
- **A pillar** crossed mid-drag snaps the chain and frees you. A pillar or Stone Wall also
  blocks the Hook outright.
- **A miss** plays out like a Cast, usually ending in a Zip.

**Lunge (a third chain move in a row).** He throws the sword 2 blocks short of the frozen
lead point and pulls himself after it.
- It has its own windup: he drops low and wraps the chain round his forearm.
- A 5-block ring appears at the aim freeze, 1.25 s before the cut lands.

**Drag Down (players on pillar tops or hovering).** This triggers on a player standing
more than 3 blocks up for 1.5 s, or hanging more than 5 blocks up, not falling, for 1 s.
- It's an aimed upward chain strike with a 0.8 s tell.
- **A hit** yanks the player to the floor in front of him, with no fall damage, and a
  sword string follows.
- **A miss** leaves the player free. Landing during the tell cancels it.

#### Reading players (proposed)

- **Lead aim:** his throws intercept based on the target's last 0.5 s of velocity,
  covering both the windup and the flight, capped at 12 blocks. Teleports clear the
  history.
- **Reversal memory:** if you beat his last throw by reversing, his next throw aims where
  a reversal would take you. The ember line shows which kind of throw it is.
- **Dodge side** decides which way follow-ups and pull-back arcs bend.
- **Jumping** decides the Scythe's height.
- **Backing straight away** draws the Thrust.
- **Standing behind him** draws the Turning Cut.

#### Floor fire (proposed)

- It arms 0.5 s after appearing.
- Fire Resistance doesn't stop it, because it uses the curse's damage type.
- A fire hit within 1 s of a weapon hit deals only its excess.

| Fire | Height | Lasts |
| --- | --- | --- |
| End of a Cast | 1 block (jumpable) | 3 s |
| Royal Cut's third blow (phase two) | 1 block | 3 s |
| Phase-two pull-back paths and Zip lines | 2 blocks: detour, or use a movement spell | 4 s |
| Unbound chain lines | 2 blocks | 4 s |

#### Phase change: Unbound (proposed)

At half health he drives the sword into the floor, and the chains of his curse tear loose
from his body. This is the fight's one set piece.

1. **The sweep:** the chains sweep the whole hall low, snap then clang. Jump. Pillars
   shelter you.
2. **The reel-in:** 1.0 s later, the six chains have hooked into the pillars. They reel
   in to him along ember-marked lines. Step off the lines.
3. **The walls:** the six lines are left as 2-block fire walls for 4 s, cutting the hall
   into sectors.

The set piece counts as one combo, and he can't be damaged for its 4 s.

**Phase two:**
- He runs at 5.2 blocks/s.
- The wait after a throw is 1.2 s.
- The Royal Cut gains a third blow for anyone who dodged the first two: an overhead cut
  that leaves a 12-block fire line.
- Every Cast becomes a **Double Cast**: a second throw follows the first one's pull-back,
  aimed toward the side you dodged to.
- Pull-back paths and Zip lines burn as 2-block walls.

#### Arena, co-op and tuning (proposed)

- **Arena:** a prepared Nether realm, his burned throne hall. It is about 80 blocks
  across and flat, with six broken pillars about 20 blocks from the center.
  - A first pass was built on Nov 10 as an operator-only realm: a round hall on a crag in a
    lava sea, with a south gate, a throne in a north apse and a burned-out roof. The
    developer approved it from screenshots and the preview the same day, after asking for
    no magma on the floor and for great braziers to light it. See
    [Pyre King](reference/pyre-king.md#the-burned-throne-hall).
  - Pillars block Hooks, catch Casts (a Zip follows), shelter players from the low
    Unbound sweep, and anchor the Unbound chains.
  - Players can't place or break blocks.
  - A Stone Wall counts as cover for one Cast or Hook.
  - Titan Dome is a buff zone, not a barrier.
- **Co-op:**
  - He alternates targets, never picking the same player twice in a row. Each switch is
    announced by a sword point, a sound, and a flame mark over the player.
  - The Turning Cut covers players behind him.
  - Every second pull-back runs through another player.
  - In phase two the Double Cast's second throw goes to another player.
  - The chain hits everyone it crosses, and each player keeps their own one-hit limit.
  - Only one player can be hooked at a time.
  - Pressure grows up to four players through shorter recoveries, with floors of 1.2 s
    on the catch recovery and 1.4 s on the Zip landing recovery. Health grows with the
    party; damage per hit doesn't.
- **Fire:** he takes normal damage from Fire spells. His hits don't set players alight.
- **Length:** a winning duo run takes about 6 minutes. Start at 2,600 health plus 1,700
  per extra player, and tune it with Nature and Flamethrower runs to check the
  close-range openings.
- **Open:**
  - the arena's details;
  - a faction for later.

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

- **Decided (Nov 4):** write the frame story first: one line through every boss to the
  final boss. Side mobs, structures and items grow around it afterwards. For now the
  bosses are the skeleton of the game.
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
