# The First Wizard (final boss): design notes

The sixth and last boss: the wizard who tried to hold all five elements, now a pool of
living ink over the End that learns, copies and takes magic. This page records what the
developer decided on November 17–18, 2026 and why. What is in the game is described in the
[reference](../reference/first-wizard.md); what is still open is in [status](../STATUS.md).

"First Wizard" is a working name. The developer has not named the boss.

## The idea

- **A monster obsessed with learning, copying and taking magic** (the developer, Nov 17).
  It is a giant pool of space and darkness floating above the End island, and it fights as
  a clone of the player.
- **The pool and the clone are one thing.** The developer wants that to be clear all
  through the fight: the pool condenses into the copy in the intro, every spell leaves
  black and purple behind, and in phase two the copy fights with the ink itself.
- **It is the roadmap's first wizard.** The wizard who tried to hold all five elements has
  no shape of its own left, which is why it wears the player's. That is also why it casts
  from all five elements.
- **It kills the Ender Dragon to show what it is.** When players enter the End the dragon
  breathes into the pool; the pool sends the breath back and the dragon dies of its own
  magic.

## The End belongs to this boss

Decided November 18.

- **There is no vanilla dragon fight.** Two final bosses in one world "will be weird" (the
  developer). The End is rewritten: no dragon, no healing crystals, no vanilla exit portal
  or gateways. The dragon appears only as an actor in the intro, using the vanilla model.
- **Why not a separate dimension.** A lookalike realm was considered and dropped: owning
  the real End means there is no fake copy of the island and no "dragon already dead"
  state to fake afterwards. Mods that rework the End's main island or the dragon fight
  will clash; that is accepted and belongs on the mod page. The arena is a stamped
  layout, so it could move to its own dimension later without redesigning the boss.
- **The five shards are the key to the End.** The End portal refuses a player without all
  five. This locks elytra, shulker boxes and end cities behind the five shardbearers and
  this boss; the developer accepted that. Shards are not in the game yet, so the portal is
  open to everyone for now (see status).
- **One fight at a time.** There is one island. While a fight runs, other players wait at
  the portal, as at the other bosses' entrances. Members of the fighting party who arrive
  late join the fight; the boss grows to the larger party and keeps its share of health,
  so nobody gains by starting short-handed. This is new: the other bosses seal their
  roster at the start.
- **The island keeps belongings and resets**, like the other boss realms.
- **After the win** the way home and the gateways to the outer islands open, so the outer
  End is what the victory gives. Rewards beyond that are parked with the other bosses'.
- **Endermen** are kept out of the arena during a fight only. The biome is not changed,
  so enderman farms off the island still work.
- **Dragon's breath** needs a new source, since no dragon can be fought: players bottle
  the cloud left by the boss's phase-two flame (below).

## The copy

- **It wears the skin of the first player to enter** (the developer, Nov 17), for the
  whole fight and for everyone in the party, with effects around it that show it is a copy.
- **Ink, not void glass.** Space magic is already black and purple (Hollow Purple) and
  already makes a player-skin copy with violet motes (Astral Double). The boss must not
  read as a Space spell: its darkness is liquid, matte black with a violet rim, and it
  drips and pools. Space is hard glass with stars inside.
- **The skin is left alone** (the developer, Nov 18, on seeing the first stills: "I dont
  really like the extra stuff on the skin"). The first pass drew a coat of ink over the
  skin, with a violet rim, drips and a pool at its feet. The developer asked to try a
  regular skin with floating particles around it instead. Ink on the body is kept only
  where phase two needs it: over the wound.
- **It is the player's size in phase one.** Every other boss is a large monster; this one
  is a wizard duel. Its power shows in the size of its spells.

## Its spells

- **All five elements, not the player's own kit** (the developer, Nov 17): a boss that
  only copied would have one spell against a player who spams one. It opens with a spell
  from the first player's element, then shows the rest.
- **Bigger than the player's, about two to three times** (the developer, Nov 18). Five
  times was considered and rejected: "big ahh fire balls flying at you" would be too much
  without a clear warning.
- **Bigger means wider and slower, never faster**, so that size buys a longer warning.
- **Every spell forms in the open before it is released** (the developer's idea, Nov 18:
  "the fireball maybe forming above the caster"). Ink rises off the wand arm, gathers above
  the copy's head, takes the element's colour and becomes the spell. The longer it forms,
  the bigger it ends up. The growing spell also marks where the boss is. Spells that are
  not thrown form where they will appear: ink runs along the ground for Stone Wall and
  Faultline, pools at Blink Rift's arrival point, and Meteor's shadow grows under it.
- **Every spell cools into ink.** It starts in its element's colour and cools to purple,
  then black, where the player's version turns to smoke or fades. For fire this was the
  developer's suggestion (Nov 18): "a custom fire where the tips of the fire is purple".
  Vanilla dragon breath is only purple particles and was not used.
- **Scaling up changes what a spell is for.** The player's versions are precise tools; the
  boss's are area attacks that decide where a player may stand.

| Spell | The player's | The boss's |
| --- | --- | --- |
| Fireball | A shot with 20 blocks of range | A slow fireball several blocks across that leaves burning ground |
| Meteor | One marked blast | A very large marked circle with a long fall: leave the area |
| Sky Shear | Three crescents in a fan | Wide crescents at ground height: jump them |
| Waylay Dash | A short burst of movement | A dash across the arena that leaves a damaging wake |
| Stone Wall | A small piece of cover | A tall wall across the arena that cuts off a way out and crumbles after a few seconds |
| Faultline | A 20-block wave with almost no warning | A long wave with a real warning that throws a player high enough to be hit again |
| Thornbite | A 4.5-block bite that heals the caster | A giant flytrap that lunges far and heals the boss if it bites |
| Sentry Bloom | Small flowers that shoot and store healing | One towering flower that stores healing for the boss until it is broken |
| Blink Rift | 10 blocks | Anywhere on the arena, with ink pooling at the arrival point first |
| Gravity Well | A 4-block pull | A pull over a large part of the arena: run or dash out |

Three combinations to start with: Stone Wall then Fireball down the open lane; Gravity Well
with a Meteor already falling on it; Blink Rift behind the player then Thornbite.

Hollow Purple is kept out of phase one. It is phase two's finisher.

The boss rules from the [roadmap](../ROADMAP.md#boss-design-rules) apply: a readable
windup, telegraph then commit then punish, zones with a consequence, a boss that stays
findable, and nothing that needs a second player.

## Phase two

The developer's idea (Nov 18), "kind of scary": an arm and part of the body are cut off,
and two or three long tendrils of the ink come out where they were, like octopus or spider
legs. It uses them to move and as melee weapons.

- **The hit that starts phase two takes the arm off.** The off-hand arm and that side of
  the torso go; three tendrils grow from the wound. The wand arm and the face stay human,
  so the human side casts and the ink side moves and strikes.
- **It stops walking.** Two tendrils carry the body; the third is the weapon.
- **The tendrils are gangly spider legs** (the developer, Nov 18, on seeing the first
  stills). The first pass drew them thick and curved, ending in flat pads, and they read
  as tails. The developer asked for them longer and thinner, "like gangly spider legs with
  sharp points instead of feet at the end".
- **It floats, and the tentacles move like snakes** (the developer, Nov 18, on seeing the
  spider legs). They liked the thinness and asked to keep it, but "a little more octopus
  like": the copy floats instead of walking, and the three tentacles "wrap and move around
  her like snakes". So they no longer plant on the floor and carry the body; they are
  supple, wind slowly round the floating copy, and uncoil to strike.
- **The tentacles are huge and come from the left side** (the developer, Nov 19, on seeing
  the floating clips): "a lot bigger … huge", and "obviously from the left side of the
  body". The copy loses its left arm whichever hand the skin's owner uses, so the wand is
  always in the right hand from the change on.
- **Spread out, not jumbled** (the developer, Nov 19, on seeing the huge tentacles and the
  cinematic: "I like this one a lot"). The three should be "a little more spread out
  though instead of jumbled together", with one bent upward and/or downward, and "a little
  bit thinner". The developer asked for several variations from different agents and left
  the choice of a favourite, and its iteration, to the lead agent.
- **The change is a cinematic** (the developer, Nov 19): the arm falls off, a bulge swells
  on the left side, and the tentacles burst out of it. The bulge bends the head the other
  way, away from the tentacles, "to give a more uncanny vibe", and the head stays bent
  through phase two.
- **One style for the whole body** (the developer, Nov 19): the coils moved smoothly at
  rest but went "flat and jittery" when attacking, and the marks they left on the floor
  were blocky, with a texture that did not match the tentacles. Attacks must move with the
  same smoothness as the rest pose, and the floor marks must be continuous, painted ink in
  one texture. The developer asked for that texture to be generated with an image model.
- **Tendril attacks:** a slam along a marked line, a low sweep to jump, and a leap with a
  marked landing.
- **The tentacles take part in every attack** (the developer, Nov 20, on seeing the spread
  pose: "I like this a lot"). In the first attacks they drew in and held still for the
  Leap, the Flame and Hollow Purple, and the two not striking idled through a Slam or Sweep.
  The developer asked for the Leap "and other attacks" to be redesigned so "the tentacles
  [are] a part of its attacks not just freeze". The whole body works in every move: the
  tentacles throw her into the Leap and catch her landing, root her for the Flame, brace
  Hollow Purple, and answer the human hand's spells.
- **The developer's combo:** the leap lands for heavy damage, then it roots itself and
  turns a flame on anyone still close. While it breathes it cannot move, which is the
  player's opening.
- **The flame is the boss's own fire**, orange at the base with purple tips, cooling to
  ink. The cloud it leaves can be bottled as dragon's breath.
- **Hollow Purple is the finisher**, cast from the human hand while the tendrils brace it.

## The intro

One scene (the developer, Nov 18: the kill and the transformation belong in the same
intro). Boss intros are long and unhurried.

1. The players arrive. The dragon is circling under the pool.
2. The dragon breathes into the pool. The pool sends the breath back and kills it.
3. One edge of the pool touches down and condenses into the first player's skin, boots
   first, while the mass overhead shrinks. The last strand pulls into the wand hand.
4. The fight starts.

The dragon dies once per world. On later attempts the pool is already waiting overhead and
the intro is only the condensing, which can be skipped like the other bosses' intros.

## Build order

1. The End takeover: no dragon fight, the rebuilt island, the portal rules.
2. The copy in phase one: the first player's skin, the forming animation, the ten spells.
3. Phase two with the tendrils.
4. The intro with the dragon.

Sounds, a death animation and rewards are parked, as for the other bosses.
