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
| Wind | Storm Roc | Mountain peaks, a sky realm above the clouds | 2 | Concept chosen |
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
- **Decided:** the fight takes place in a large arena on a mountaintop above the clouds.
  Taller, thin rock spires stand around it, and the Roc perches on them between
  attacks.
- **Decided:** nothing in the fight knocks players toward the edge. The Roc's attacks
  hurt but don't shove. A player who falls off the edge dies.
- **Decided:** in the dodging stretches it uses three abilities:
  - **Small strikes:** a small circle shows on the ground for a moment before lightning
    hits it. They are spread all over the arena.
  - **Large strikes:** larger circles that are slightly slower but deal much more damage.
  - **Tornadoes:** one or more stay on the battlefield. They hurt anyone inside, slow
    players more the closer they get, and pull harder on players in the air.
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
- **Proposed:** small strikes fill the arena and land wherever players run. Large strikes
  mostly land on players, and their size means a player has to commit to getting out.
  No circle warns for less than about 0.7 seconds, and the small strikes always leave a
  path open.
- **Proposed:** a slowed player near a tornado can still escape a small circle, but not a
  large one. Tornadoes keep away from the edge, so their pull never drags anyone off.
  Their stronger pull on players in the air punishes constant jumping.
- **Proposed:** the strike circles are white-gold and crackle, and they come quick and
  many, unlike the Necromancer's few slow lobbed fireballs.
- **Proposed:** the dive lands on a large marked area, not on one player, and ends in a
  discharge. Players escape by leaving the area while strikes block some routes.
- **Decided:** once free, it fights on the ground with three kinds of attack:
  - **Scream blast:** it tucks in and takes a heavy breath, then screams a blast of air,
    sound and lightning in a straight horizontal line ahead. Everyone in the line is hit,
    so players lined up behind each other are all caught.
  - **Beak swings and pecks**, its basic melee.
  - **Wing wave:** a big wave from its wings that pushes players away.
- **Proposed:** every place to stand has an answer, so the brawl is about reading where
  its head points. The scream covers the long line in front, pecks hit a player close
  in front, beak swings sweep its sides, and the wing wave answers players hugging it
  or standing behind it.
- **Proposed:** the scream turns toward a player while it breathes in, locks its
  direction just before it fires, and is too wide to sidestep casually. It can't be
  jumped, and behind the Roc is safe from it.
- **Proposed:** the dive always lands far enough from the edge that the wing wave's push
  can't reach it, so the no-edge rule holds. Being pushed into a tornado is a deliberate
  danger.
- **Proposed:** it dives on a steady rhythm, not by chance.
  Starting guesses: about 15–20 seconds in the air, 3 for the dive, 3 stuck and 8 of
  brawling.
- **Proposed:** no ward while it perches. A long-range spell that reaches it still
  counts, but the fight is balanced as if nothing can.
- **Decided:** the phase change is the one big set piece: a cinematic in which the sky
  turns dark and stormy.
- **Decided:** in phase two its tornadoes are larger, and its lightning strikes are larger
  and hit harder.
- **Decided:** phase two adds a chain smash. Like the dive, it slams its beak into the
  ground, but several times in quick succession in different places, aimed at players.
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
- **Decided:** a raptor with a deep blue head and spiky crest, red skin around the eyes,
  white chest, wings that fade from red-pink to white, a red tail fan and red talons. Its
  oversized hooked beak is its main weapon and reads from a distance. Lightning crackles
  around its beak and eyes, and lightning marks run across its face and body.
- **Proposed:** its lightning is white-gold, unlike the Guardian's cyan core and the
  Necromancer's soul-blue fire. The marks glow brighter once it is charged in phase two.
- **Proposed:** an aerie shrine on a mountain peak holds a walk-in veil into the sky realm.
- **Proposed:** Windcallers are masked cultists who roam the mountain peaks. They dash,
  throw daggers and glide like wand players.
- **Proposed:** players of every element can fight it. Its damage window is on the
  ground, and no part of the fight relies on Wind's mobility spells.
- **Open:** whether Windcallers appear in the fight; the intro cinematic; fight length
  and the gear it is tuned against; whether hitting the base of the Roc's spire can
  force it off early (held in reserve in case the dodging stretches drag).

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
