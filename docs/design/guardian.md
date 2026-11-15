# Fractured Guardian design notes

The decisions behind the Stone shardbearer's fight, with the developer's feedback and the
reasons for each choice. Read this before changing its design. For what the game does today,
read [Guardian combat](../reference/guardian-combat.md); for open work, [status](../STATUS.md).

Entries are marked **decided**, **proposed** (suggested, not yet agreed), **open** or
**built**, as they stood when written. Where an entry differs from the reference or the code,
those win. Record new design decisions here, not in the roadmap.

## The overhaul (November 12)

The developer liked how the Pyre King decides what to do (a weighted chooser: distance bands,
reads of what the player just did, strings) and asked for the other bosses to fight smarter,
each with its own identity, starting with the Guardian. After a first proposal that changed
only timings and the chooser, they said the Guardian "might need a bigger overhaul, not just
the timings and AI". Attacks may be added, removed or reworked.

What the old fight did, which is what this replaces:

- It chose attacks by cooldown: a forced beam every third attack, the leap and the core pulse
  whenever their cooldowns ended, otherwise a fixed order.
- It read three things: hovering, standing still and walking direction.
- It walked at the nearest player for about a second between attacks and stopped 6 blocks
  short, so against ranged players its fists rarely mattered.
- Heavy hits existed only up close. What reached a ranged player took about 15% of their
  health in iron, apart from the rock. Most attacks picked one player, so in co-op the other
  was free.
- Phase two tripled slams and throws. The held slam was a 35% roll.
- The fan was weak and missed players in the air: it waited 1.2 seconds to notice them and
  0.9 to release, aimed without their vertical movement, and threw stones a third of a block
  wide for 7 damage.

### Identity

- **Decided:** the Guardian is slow and never hurried. Every hit is an area: fists, waves,
  landings, blasts.
- **Decided:** it punishes habits by remembering how the player answered last time.
- **Decided:** it cannot catch a player, so it takes ground away until its fists or a landing
  can reach them. The Pyre King, by contrast, is fast, precise and tight.
- **Decided earlier, still true:** long wind-ups, heavy recoveries, only the blow is quick. It
  is tuned against full iron; a new duo should need about ten tries and a win takes about four
  minutes. Armor and Protection reduce its damage normally.

### Rules the developer set

- **Decided:** openings are 1.25 seconds solo after a string, not longer. Most fights will be
  multi-player, and the developer finds the current fight easy with two people. Openings
  already shrink with party size.
- **Decided:** healing is punished. Eating draws an attack at once.
- **Decided:** the fan is pointless and is replaced.
- **Decided:** attacks need a real, readable wind-up and must hurt. Chip damage means nothing
  against armor and golden apples.
- **Decided:** prefer area attacks and strikes that account for movement. Heavy single-target
  committed attacks were rejected: they either track until they hit or are trivial once
  committed. Players sprint, jump, dash and fight from 7 to 40 blocks, and "any players will be
  constantly moving".
- **Decided:** keep mechanics simple. Every mechanic is solvable solo. The big set piece
  happens once, at the phase change.

### The kit

The developer agreed this kit on November 12 ("yes it fits I think this is good").

| Move | Change | What it does | Answer |
| --- | --- | --- | --- |
| Slam | Kept; the Shockwave merged into it | Fists in front, a ring wave outward that now hurts; held when the player jumped the last one | Leave its front, jump the wave |
| Sweep | New, step two | A slow backhand through its sides and back, about 7 blocks, for players circling or behind | Back out of reach |
| Leap | Reworked | Gap closer and heaviest hit; aim locks late; lands on a marked ring | Run out of the ring |
| Rock | Kept | Thrown at once at anyone standing still or eating | Sidestep |
| Scar | Replaces the beam, step two | See below | See below |
| Airburst | Replaces the fan and the back volley | Stones burst round a launched player and knock them down; in co-op they burst on marked rings under players it isn't facing | Change direction in the air, or leave the ring |
| Core pulse, shell break | Kept as they were | | Sprint out |

On top sits the chooser, with its own table of distance bands, reads and strings. Phase two
strings attacks together where it used to triple them.

- **Decided:** build it in two playable steps. Step one is the chooser, the leap, the Airburst
  and the heavier damage, with no new art, so the new brain can be felt in a fight quickly.
  Step two is the Sweep and the Scar, which need a new clip and new effects.

### Step one (built November 12, not yet played)

See [Guardian combat](../reference/guardian-combat.md) for exactly what it does. These are
choices made while building it that the developer has not yet seen in a fight; each is a first
guess for the playtest to correct.

- **The opening.** The 1.25 seconds is the walk between strings, after each clip's own
  recovery has played, replacing a rest of about 1.6 seconds. It was read this way so that no
  recovery animation is cut short and the Guardian stays "never hurried". The other reading
  (1.25 seconds from the blow itself) would be much harsher and would cut the leap's recovery
  nearly in half.
- **Strings.** A string is attacks with no walk between them, each chosen fresh from where the
  target has gone, so the follow-up comes from the player's answer. Phase one strings two at
  most, about a third of the time; phase two three, most of the time.
- **The held slam is a memory, not a roll.** It holds only against a player who jumped the
  last wave that reached them, and a player the wave struck gets a plain slam next.
- **Standing still** is punished only where a rock can reach (6 blocks or more). Standing
  still beside it during its opening is what a close-range build is meant to do.
- **Damage.** The wave and the rock's splash went from 6 to 10 (about 30% of health in iron),
  and the leap hits for 18 across its whole ring. Nothing is left at the old 15%.
- **The leap** follows its target for the first 0.7 seconds of its flight, then marks a ring
  4.5 blocks in radius for 1.1 seconds. From the middle of the ring a sprint gets out and a
  walk does not.
- **The Airburst** notices a launched player in 0.15 seconds. Its stones burst within 4 blocks
  of where that player will be, counting their fall. On the ground the co-op ring is marked for
  1.3 seconds.
- **Rings are plain amber particles** and the bursts are vanilla explosion and stone particles.
  Drawn effects wait for the playtest.
- **The beam stayed** until step two's Scar replaced it, and nothing yet answered a player
  behind it.
- **Health is unchanged** at 1,400 plus 1,000 per extra player until a logged fight says
  otherwise.

### What to look for in the first playtest

- Whether the fight is now hard enough with two players, and still fair solo.
- Whether the leap's ring gives enough warning, and whether 4.5 blocks is the right size.
- Whether the wave at 10 damage is too much, given how often it comes.
- Whether the Airburst reaches a launched Wind player, and whether being knocked down feels
  fair.
- Whether 1.25 seconds of walking is an opening a close-range build can use.
- Whether strings read as strings, or as the Guardian simply not resting.

### Step two (built November 13, not yet played)

- **Decided:** the Sweep, as in the table.
- **Decided (November 13):** the Scar is a pen, not a hit. The developer had asked whether the
  Scar "would be too easy since I can confirm any players will be constantly moving". It would
  be: a sprinting player crosses a line a block and a half wide in about a sixth of a second, so
  a line through where they stand, or even ahead of them, almost never lands. The answer
  proposed was to stop judging it as a hit, and the developer agreed ("yes I think we can try
  that"):
  - It scores two cracks, one each side of its target, which erupt into spike walls too tall to
    jump that stand for six seconds and hurt to touch.
  - Ranged players survive by circling. Between two walls they can only move toward the
    Guardian or away from it, which is the direction its leap and slam wave punish.
  - It then leaps or slams into the pen. The Scar is the set-up; the landing is the hit.
  - The answer is to leave through an open end before the follow-up, or to stay in and run out
    of the landing ring along the pen. Going over the top needs a mobility spell, and that is
    what the Airburst is for.
  - In co-op other players get a wall near them, so the arena is cut into wedges and the party
    is split up.
  - The risk is that it is a two-part idea, a little more to learn than "dodge the thing". For
    the player it is still one rule: don't touch the spikes.
- **Open:** whether anything about the old Guardian should be kept exactly as it was. The
  developer was asked and has not named anything; the core pulse and the shell break are the
  only parts left untouched.

See [Guardian combat](../reference/guardian-combat.md#the-sweep-and-the-scar) for exactly what
both do. These are choices made while building them that the developer has not yet seen in a
fight; each is a first guess for the playtest to correct.

- **The Sweep does not turn.** Every other attack snaps round to face its target as it starts,
  which would put a player who got behind it back in front before the arm moved. So the Sweep
  keeps the facing it had, and "behind it" is read from that.
- **Front is safe from the Sweep, and the slam's fists only reach its front.** The two close
  attacks cover opposite ground, so each wind-up asks for a different answer. A melee player
  can stay on the boss by reading which one it is.
- **It sometimes sweeps at a player in front of it,** whom it cannot hit. That is on purpose:
  it breaks up the run of slams step one could not avoid, and a player who sidesteps on habit
  walks into it.
- **The pen is two parallel walls,** 7 blocks apart inside, starting 3 blocks from the Guardian
  and running 10 blocks past its target. Both ends are open. A third wall across the far end
  was considered and left out: the developer asked for mechanics that stay simple and solvable
  solo, and a closed box with the Guardian in its mouth may be neither.
- **Nobody on foot avoids the pen.** The cracks show for half a second, in which a sprint
  covers under 3 blocks, and each wall is 4 from the middle. This is what answers "players
  will be constantly moving". If it feels unfair, the half second is the number to raise.
- **The Scar's own recovery is short** (one second after the walls come up), because it always
  goes straight into the follow-up and that attack's recovery is the opening.
- **The follow-up is always the leap when the leap can reach.** A first version let it pick a
  plain slam one time in six, and a fixture caught it wasting the pen on a wave. Scar, then
  leap, is one thing to learn. The slam is the follow-up only inside 12 blocks, beyond 30, or
  when the leap has no landing. Somebody else standing behind it does not draw it off.
- **A leap into the pen lands about 3.5 seconds after the walls come up.** A player who runs
  for the far end at once gets out first and the leap follows them out. Whether the pen holds
  anyone long enough to matter is the first thing to judge in play.
- **The spikes are not blocks.** They are solid to players only: the Guardian walks and leaps
  through them, and nothing in the world is changed, so there is nothing to restore after a
  crash. The cost is that they are cover for nobody, including players hiding from a wave.
- **The spikes are drawn in code** on the Stone spells' existing spike texture, and the cracks
  and the Sweep's arc are the same amber particles as the leap's ring. No new image was made.
- **The beam is out of the fight but not deleted.** Nothing chooses it any more. Its code, clip
  and `/ew guardian beam` rehearsal stay until the Scar has been played, in case the Scar does
  not work out. Deleting them is listed in [status](../STATUS.md).
- **Co-op walls replace back stones on the Scar.** Both at once looked like too much.

### What to look for in the second playtest

- Whether the pen holds a player long enough for the follow-up to matter, or they are out of
  the far end every time.
- Whether being penned with no way to avoid it feels fair, given that the pen itself does not
  hurt.
- Whether the Sweep's wind-up reads as different from the slam's in time to act on it.
- Whether a Sweep aimed at nobody reads as a mix-up or as the Guardian being stupid.
- Whether the walls get in the way of melee players more than they trouble ranged ones.
- In co-op, whether the extra walls split the party or only add clutter.
- Whether the spikes and the amber marks are readable enough, or need drawn effects.
