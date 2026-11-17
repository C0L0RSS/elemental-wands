# Hollow Necromancer design notes

The decisions behind the Nature shardbearer's fight, with the developer's feedback and the
reasons for each choice. Read this before changing its design. For what the game does today,
read [Necromancer combat](../reference/necromancer-combat.md); for open work,
[status](../STATUS.md).

Entries are marked **decided**, **proposed** (suggested, not yet agreed), **open** or
**built**, as they stood when written. Where an entry differs from the reference or the code,
those win. Record new design decisions here, not in the roadmap.

## The smarter fight (November 16)

The developer had seen the Pyre King's and the Guardian's choosers and asked for the same for
the Necromancer, planned back and forth first. Their words on the fight as it was:

- "I really don't like the fight, especially when it takes its head off. In the flying head
  version, I really don't like that one or its movement."
- "It just seems like a random fight, like you can't really predict anything the boss is
  doing. You can just tell that's not very smart."
- "In two players it's kind of easy."
- "He just seems to be a little annoying. I think the rest of the fight is pretty good
  besides that", and it is "not really that bad of a boss fight, especially in 1v1".

What the old fight did, which is what this replaces:

- The robed caster worked down a fixed list (drain, hands, ambush, bolts), skipping whichever
  it cast last. It blinked whenever a player came close and shifted every two or three casts
  whatever was happening. It read nothing about the players, apart from following a Hands
  catch with an ambush. Between casts it stood still whenever the nearest player was 6 to 15
  blocks away.
- The colossus alternated swipe and grab up close, and otherwise took the first ready move
  from another fixed list. It harvested souls on a 20-second clock and crawled straight at
  the nearest player.
- At a quarter health the Necromancer's soul (a large Soul Bolt skull, the "flying head")
  tore out of the ribcage. It drifted between random points, teleported every four seconds
  and whenever a player came close, and cast on a fixed clock, while the shielded body swung
  at random headings.

### Identity

- **Decided:** each boss fights in its own way. The Pyre King is fast and precise and the
  Guardian takes ground. The Necromancer sets a trap and cashes it in: robed, it punishes
  where a player stands; as the colossus, it hunts them down. The developer was shown this
  with the proposal and answered its questions without objecting to it.

### What the developer decided

- **Decided:** the flying head is cut. The developer was offered a rework (the soul tethered
  above the body as a puppeteer), a cut with a frenzy at a quarter health, or something of
  their own, and chose the frenzy. Asked what bothered them about the head, they said "it is
  just really annoying and unenjoyable to fight".
- **Decided:** attacks may be added or removed on the builder's judgment, as long as the
  developer is told. One was added (the Soul Burst) and none removed apart from the soul
  split.
- **Decided:** the sieges stay as they are apart from "a slight buff" to the minions'
  damage: "They don't really do that much damage, so they're not very scary. There are just
  a lot of them."

### Built November 16, not yet played

See [Necromancer combat](../reference/necromancer-combat.md) for exactly what it does. These
are choices made while building it that the developer has not seen in a fight; each is a
first guess for the playtest to correct.

- **Two answers are memories, not rolls.** The complaint was that nothing could be
  predicted. So the first time a player gets on top of the robed caster he blinks, and if
  they chase him down again within ten seconds he bursts once they are inside the ring;
  the colossus follows a jumped swipe with the hand. None of these is a roll: each happens
  whenever that cast is off its cooldown and no higher claim comes first (a player left
  alone for ten seconds, the cast that follows a move, and for the hand a player it held
  in the last 15 seconds). The rest is weighted chance, as with the other two bosses, so
  the same spot does not always draw the same cast.
- **The Soul Burst is the new attack.** Before it, his only answer to a player beside him
  was to leave, which is likely part of what read as annoying. It reuses the ambush's staff
  slam and clip, so it needed no art. It is tighter than the ambush's burst (3.5 blocks, not
  4.5) because it starts with a player already next to him, and he stands still for a second
  after it: chasing him now buys an opening.
- **He shifts for a reason.** The shift on a count is gone. He moves to see round cover, to
  close on a player who has run far off, or to get out from under heavy fire, and it cannot
  repeat for ten seconds. This should mean fewer teleports than before; whether it is few
  enough is for the playtest.
- **A blink or a shift is followed by a cast at once.** He moves in order to cast, not to
  stall.
- **Hands lead a moving player.** Players are always moving, so a ring under where they
  stand almost never caught anyone. It now rises a second ahead of them, where they start
  just outside it. Running on is the wrong answer and turning away is the right one. The
  colossus's wider ring is led a little further (1.25 seconds, at most 6 blocks) so that
  the same holds for it.
- **He circles between casts.** A slow sidestep round the nearest player in place of
  standing still. It is small, about two blocks in a solo rest.
- **Feeding follows wounds.** He drains mostly when he has lost 8% of his health since his
  last drain, and the colossus harvests once it has lost a tenth since it last fed. Both
  used to come up on rotation or a clock.
- **Two players are pressed harder.** Strings are likelier and rests shorter with each
  extra player, and inside a string he turns on someone else, so both players are under
  attack at once. Health is unchanged (800 plus 500 for each extra player) until a logged
  fight says otherwise.
- **The frenzy.** The howl is the old `colossus_roar` clip, which was not in use. It pushes
  nearby players back and does no damage. From then on the colossus crawls at 5.0 blocks a
  second (it was 4.4; a sprint is 5.6), rests about half as long, strings up to three
  attacks, has its cooldowns cut to 70%, and no longer drains or harvests. A final quarter that still stopped to heal
  would have dragged, so the last stretch is a race with nothing to shoot down.
- **Nothing holds its health at the quarter.** The old gate existed so that burst damage
  could not skip the soul split. The frenzy changes how the colossus fights, not what the
  players must do, so skipping its howl loses nothing.
- **Siege damage.** Crawlers 3 to 4.5, brutes 10 to 12, archers' arrows about a third more.
  In full iron a crawler's hit goes from about 1.4 to about 2.2. Wild night spawns are
  unchanged.
- **Left as it was:** every other attack's timing and damage, the sieges' waves and the rain,
  the intro and the transformation.

### What to look for in the first playtest

- Whether his choices now read as answers to what the players did.
- Whether he is still annoying to chase: how often he teleports, and whether the burst and
  the second it leaves him standing feel like a fair trade.
- Whether the led hand rings are fair, and whether Hands into the ambush (or the bite)
  lands too often now.
- Whether two players still find it easy.
- Whether the frenzy is a good ending, or just a faster version of the same fight; whether
  a crawl of 5.0 blocks a second is too fast for a walking player in iron; and whether it
  still goes quiet against a player circling inside its arms.
- Whether crawlers at 4.5 are too much in the last duo wave (17 of them). A reviewer
  suggested 4 if so; the brute and arrow numbers looked right to them.
- Whether the siege hordes are now a threat.
- Whether his circling looks natural or makes him slide.
