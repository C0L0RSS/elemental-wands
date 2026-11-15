# Pyre King design notes

The decisions behind the Fire shardbearer, with the developer's playtest feedback and the
reasons for each choice. Read this before changing his design. For what the game does today,
read [Pyre King](../reference/pyre-king.md); for open work, [status](../STATUS.md).

Entries are marked **decided**, **proposed** or **open**, as they stood when written. The
sections titled "proposed" are the kit as drafted before the fight was built: the
[decisions for the fight](#decided-for-the-fight-nov-10) and the playtest sections override
them, and the reference records what was built. Phrases such as "not yet played" in the
playtest sections were true on the day they were written. Record new design decisions here,
not in the roadmap.

The redesign started on November 4. The first version (look and kit) was shelved, and
only the concept carries over. The kit below went through seven reviewed drafts before
the developer's direction made it melee first.

## Decisions

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
  - A first-pass fight was built the same day. See [Pyre King](../reference/pyre-king.md)
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
  frame story is written (see [Lore](../ROADMAP.md#lore)). Like Sardanapalus, he set his own palace
  alight rather than surrender. Like Koschei, his death is caught in the Fire shard, which
  was in that fire. He can't die while it holds him, and taking the shard lets him go.
  - **Proposed:** the Storm Roc brought him the Fire piece to keep. It made his kingdom
    grander than ever, and he came to think of it as his own.
  - **Proposed:** when the piglins besieged his palace for it, he shut his court in the
    throne hall, piled his treasure around the shard and set the hall alight. No one
    cursed him: the curse is his own grip.
  - **Proposed:** the Nether fortresses were his kingdom's roads. The piglins' love of
    gold began with the gold they picked from his ashes.

## The first playtest (Nov 11)

The developer fought him in Lunar with a level 1 Space wand, all abilities unlocked, and won
on the second try. "The fight is pretty clean, but it's really easy."

- **Too slow.** "His overall speed in terms of the speed between his attacks just has to be
  faster."
- **No melee against a ranged player.** "Most players aren't really playing close range, he
  wasn't really doing any melee attacks. It was just different variations of him throwing
  the sword, and the sword throw was very slow. So it wasn't hard to dash."
- **The spin is easy to leave.** "During the spin maneuver, the little pause that he does
  in between, it was really easy to get out. I don't think I got hit by it once."
- **The Haul is liked.** "Him pulling himself with a chain was a really good move."
- **Decided:** his fire on the floor is regular vanilla fire, not a custom flat texture.
  "The 2D textures don't really fit in that well in a 3D environment."
- **Built the same day, not yet played:** a tempo for every clip, shorter gaps, more
  chained strings, more Lunges and Hooks at range, and fire as blocks. See
  [Pyre King](../reference/pyre-king.md#the-fight). His health and damage are unchanged.

## The second playtest (Nov 11)

After the pace was raised the developer fought him again: "it was really good."

- **The Chain Scythe up close.** "It is impossible to dodge that at close range. So I think it
  has to either be removed or changed." Changed the same day, not yet played: it strikes only
  out where its blade swings (5 blocks or more from him), so a player close in is under the
  chain, and he no longer uses it on a player within 6.5 blocks. Jumping the low swing and
  crouching under the high one still work further out.
- **The hall's blocks** should be as indestructible as the other arenas'. The way they were
  not was the time after a win: nothing sent the player home. Survival players already could
  not break them; now mining one does not even start.
- **Decided:** a win sends everyone home, as in the other arenas. Built the same day: ten
  seconds after he dies.

## Summoning and the intro (Nov 11)

- **Decided:** players reach him at a burned gatehouse of his kingdom in the Nether, by
  right-clicking something there, as the Storm Roc's gong is struck.
- **Decided (Nov 11):** of three things to right-click (a chained gate with the king's seal, a
  chain winch, a war horn) the developer chose the chained gate.
- **Decided (Nov 11):** the place is the developer's idea: "a bridge leading off to nowhere
  over a lake of lava. But the gate looks like a portal."
- **Built (Nov 11):** the Burned Gate. See [Pyre King](../reference/pyre-king.md#the-burned-gate).
  Touching its seal twice is what sets the chains burning. The developer played it through the
  intro and the fight the same day: "really good".
- **Decided:** his intro is the developer's own idea. He sits slumped on his throne like a
  statue. Chains are webbed round the whole throne room and round him, and the greatsword
  hangs from the ceiling on one of them, high above and just in front of him. The chains
  burn and go loose, the sword falls and digs into the floor in front of him, a close-up
  shows his hand reaching and gripping the hilt, and everything takes fire: the sword and
  the hall. "This is my general overview idea, we can change it a lot later."
- **Decided:** it should be long. The other intros run over 30 seconds and there is no time
  limit: "I would rather have a long intro that is cool and feels the impact instead of an
  intro that looks super sped up."
- **Built (Nov 11) and approved (Nov 12):** 61 seconds. The developer had the arms reworked
  and the last pull given weight, then approved it from the in-game video. See
  [Pyre King](../reference/pyre-king.md#the-intro).
- **Open:** whether the player's touch at the gate should show in the intro itself (the
  scene opens in the hall as before); whether a fallen player should watch the rest of a
  co-op fight, as in the other realms; where the Pyre King comes in the order of bosses,
  since his gate needs a Nether portal.

## Decided for the fight (Nov 10)

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
  [Pyre King](../reference/pyre-king.md#the-fight) page (21).
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

## Core rules (proposed)

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

## How he picks a string (proposed)

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

## Sword strings (proposed)

| When | String | Tell | Answer |
| --- | --- | --- | --- |
| Default | **Royal Cut**: two cuts across a 150° arc in front of him. He tracks you until the snap, then freezes and steps up to 1.5 blocks; the reach is 4.5 from where he lands. The second cut has no step and turns up to 45° toward your dodge side | 0.9 s, then 0.45 s | Back out of reach. The closer you stand, the earlier you must go |
| You backed straight away from his last sword string | **Kingsguard Thrust**: he steps 2 blocks and thrusts straight along the frozen aim, 8 blocks long and 1.5 wide | 0.8 s; aim freezes at the snap | Sidestep |
| Someone has stood behind him for 1 s | **Turning Cut**: he spins and cuts the 180° behind him, with a reach of 5 | 0.7 s; snap, then clang | Step out of reach, or round to his front |

- After every sword string he pauses for 1.2 s, standing still. This is the punish
  window for close-range builds.
- If you escape a sword string with a movement spell, his next string is a Cast aimed at
  your landing spot.

## Chain moves (proposed)

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

## Reading players (proposed)

- **Lead aim:** his throws intercept based on the target's last 0.5 s of velocity,
  covering both the windup and the flight, capped at 12 blocks. Teleports clear the
  history.
- **Reversal memory:** if you beat his last throw by reversing, his next throw aims where
  a reversal would take you. The ember line shows which kind of throw it is.
- **Dodge side** decides which way follow-ups and pull-back arcs bend.
- **Jumping** decides the Scythe's height.
- **Backing straight away** draws the Thrust.
- **Standing behind him** draws the Turning Cut.

## Floor fire (proposed)

- It arms 0.5 s after appearing.
- Fire Resistance doesn't stop it, because it uses the curse's damage type.
- A fire hit within 1 s of a weapon hit deals only its excess.

| Fire | Height | Lasts |
| --- | --- | --- |
| End of a Cast | 1 block (jumpable) | 3 s |
| Royal Cut's third blow (phase two) | 1 block | 3 s |
| Phase-two pull-back paths and Zip lines | 2 blocks: detour, or use a movement spell | 4 s |
| Unbound chain lines | 2 blocks | 4 s |

## Phase change: Unbound (proposed)

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

## Arena, co-op and tuning (proposed)

- **Arena:** a prepared Nether realm, his burned throne hall. It is about 80 blocks
  across and flat, with six broken pillars about 20 blocks from the center.
  - A first pass was built on Nov 10 as an operator-only realm: a round hall on a crag in a
    lava sea, with a south gate, a throne in a north apse and a burned-out roof. The
    developer approved it from screenshots and the preview the same day, after asking for
    no magma on the floor and for great braziers to light it. See
    [Pyre King](../reference/pyre-king.md#the-burned-throne-hall).
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
