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
- **Proposed:** an aerie shrine on a mountain peak holds a walk-in veil into a sky
  realm: a summit plateau over a sea of clouds, ringed by standing stones and wind
  chimes.
- **Proposed:** in phase one the Roc circles out of reach, screeches and dives at a
  marked shadow. A missed dive crashes it into the ground with its talons stuck, which
  is the main window to hit it. On the ground it uses a wing buffet, a heavy beak
  lunge and a feather sweep.
- **Decided:** nothing in the fight knocks players toward the edge. The Roc's attacks
  hurt but don't shove. A player who falls off the edge dies.
- **Proposed:** the chimes show which way the next gust of wind blades will sweep from.
  Standing stones block it, but the Roc smashes stones, so cover runs out as the fight
  goes on.
- **Proposed:** the one big set piece is the phase change, where the Roc tears the
  summit into floating islands linked by updraft vents.
- **Proposed:** phase two is a storm: lightning telegraphed on the ground, feather
  volleys, and a tornado that wanders across the islands and hurts anyone it touches.
- **Proposed:** Windcallers are masked cultists who roam the mountain peaks. They dash,
  throw daggers and glide like wand players.
- **Proposed:** players of every element can fight it. The Roc spends real time within
  reach of short-range spells, and the arena supplies its own lift instead of relying
  on Wind's mobility spells.
- **Open:** whether Windcallers appear in the fight; the intro cinematic; fight length
  and the gear it is tuned against.

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
