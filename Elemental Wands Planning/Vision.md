# Vision

[Home](Home.md) · [Vision](Vision.md) · [Progress](Progress.md) · [Updates](Updates.md) · [Ideas](Ideas.md)


Last updated: September 16, 2026  
Source: the user's September 15–16 planning discussion.  
Status: design direction; proposed features below are not necessarily implemented.

## 1. The goal

**Build an easy-to-pick-up Minecraft survival SMP mod where players develop their
own elemental magic, explore for useful discoveries, form parties, fight bosses,
and sometimes fight each other.**

Players should be able to build a home, gather supplies, trade, explore, and choose
their own goals. Magic should add interesting progression and combat to that
world. Ruins and artifacts hint at a lost magical civilization. A spectacular
creature in the End is a long-term challenge, accessible without completing a
mandatory story or boss sequence.

The first audience is the user's friends: they build and adventure together on an
SMP, but also play around and sometimes kill each other for fun. Organized faction
warfare is not a requirement. Broader player preferences can be informed by tests.

### What success means

- A newcomer can understand how to start casting and progressing with little help.
- Players can explain what they are working toward and why an expedition is useful.
- Different elements and loadouts offer worthwhile choices throughout progression.
- A small group actually tries the mod and enjoys it; some want to play again.
- Releases, technical decisions, and improvements from real feedback make this a
  substantial, honestly documented passion project and portfolio piece.

There is no required download count, campaign length, or release date yet. Actual
playtest participation and feedback matter more than assuming downloads equal
players. Seek feedback before building the entire endgame.

## 2. How to read and maintain this plan

- **Agreed direction:** the user has endorsed the underlying decision.
- **Working proposal:** a candidate to refine or playtest; its details are not final.
- **Open:** needs a decision before dependent implementation.
- **Existing:** supported by the local source/documents reviewed on September 16;
  this is not a claim about the currently running or installed client.

This file records product direction. [AGENT.md](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2FAGENT.md) remains the codebase
guide for architecture, required checks, and deployment. Consult both before a
substantial feature change. A roadmap entry is not an instruction to implement
every feature in the current session.

[Progress.md](Progress.md) tracks milestones, individual work items, completion
evidence, and session updates. Use it for current status; keep design decisions
here. Record session history in [Updates](Updates.md) and new uncommitted concepts
in [Ideas](Ideas.md). Future sessions should update the relevant progress item.

For future planning sessions, begin with the goal and the relevant open decisions.
Work through a small number of foundational questions at a time. The user prefers
simple explanations and has asked to slow down when discussion jumps ahead into
large story outlines, UI redesigns, or implementation.

When a decision changes, update its section and the short decision log at the end.
Keep recommendations visibly separate from user decisions. Link implementation
reports as work lands rather than copying release logs or checksums into this file.

## 3. World, exploration, and multiplayer

### Agreed direction

- Use open, generated Minecraft worlds with survival SMP freedom.
- Building and spending time at home are valid parts of the intended experience.
- Keep a wand guidance mechanic that feels like following a mystery. Players may
  seek a magical destination without being required to follow a quest line.
- Structures and bosses provide opportunities across the world. The Guardian's
  church is an encounter players can discover, not a guaranteed first chapter.
- Support cooperative parties and player-versus-player combat. Balance for the
  initial friends-and-rivalries audience while retaining the survival setting.
- Keep installation and multiplayer setup straightforward. Test onboarding with
  people unfamiliar with the project.

### Working proposals and open details

- On-demand wand guidance could produce a brief directional magical trace.
  Its input, appearance, range, target selection, and handling of already-completed
  sites remain open. It should not require operator locate commands to be useful.
- Discovery needs to work even when a church is far away. Structure distribution,
  travel time, target searches, and smaller discoveries between bosses need design
  and playtesting. The old assumption of a guaranteed nearby story church is not
  automatically part of this survival direction.
- A special starting location where each player obtains a wand was discussed
  before the SMP pivot. Revisit whether it fits generated worlds, late joiners,
  and simple onboarding; it is not a settled map requirement.
- Boss rewards can include maps or clues to another encounter, including a hint
  about a summoning block and the crystal/material it requires. These are intended
  as leads. Decide how independently discovering a later boss works and avoid
  accidentally making every useful reward require one fixed boss order.
- Party friendly fire, base damage, griefing, death/item loss, trading, and late
  joiner progression are not fully designed. Existing protections remain the
  implementation baseline until changes are explicitly planned.
- A bundled modpack is a possible distribution route; launcher, hosting, and
  packaging choices remain open. A mandatory authored world download is no longer
  assumed. Do not promise one-click hosting or platform acceptance without testing.

## 4. Progression: simple growth, interesting builds

### Agreed direction: progress belongs to the player and element

Each player has independent saved progress for Fire, Wind, Stone, Nature, and
Space. A strong Fire wizard switching to an undeveloped Stone element uses Stone's
own strength and learned abilities. Returning to Fire restores that Fire progress.
The physical wand is the casting tool, rather than the sole owner of magical
strength. Switching elements must not erase progress or transfer another element's
damage bonus.

### Agreed direction: automatic element levels

- Add an element-specific XP meter, separate in purpose from spendable Flux.
- Using that element's magic earns XP.
- Reaching the required XP automatically increases its level.
- Successive levels require more XP.
- Levels provide simple damage increases to that element's damaging abilities.
- Keep the leveling experience easy to understand. Build complexity should come
  primarily from choosing spells and combinations.

The intended roles are:

| System | Purpose |
| --- | --- |
| Element XP and level | Automatic damage growth and the planned expansion of equipped capacity |
| Element Flux | A grindable, spendable currency for choosing spells in the shop |
| Spell unlock books | Permanent spell learning earned through loot and encounters |
| Consumable crystals | Proposed temporary spell enhancements during play |

XP and Flux can potentially be earned from the same activity, but spending Flux
should not spend away an element's level progress. Exact earning rules are open.

### Agreed pacing direction: quick access to experimentation

Leveling an element should be fairly easy and quick for a player who actively
pursues it. The user's reference is the feel of Minecraft equipment progression:
accessible early equipment, worthwhile improvements through effort/exploration,
and harder-to-obtain top-end equipment and enchantments. This is a pacing analogy,
not a requirement to use those materials or copy their numerical stats.

The working interpretation is that useful build experimentation should be possible
within one focused play session. Exact session length, slot unlock timing, and
whether maximum strength is also reachable in that session remain open. Avoid
making trying another element require a long grind before it becomes enjoyable.

Potential implication to confirm: make the core build available relatively early,
with the strongest upgrades or optional refinements taking more effort. No exact
XP values, level count, damage cap, or extra refinement system is approved yet.

### Open rules to settle before implementation

1. What earns XP, including damage, kills, support spells, bosses, and PvP?
2. How do repeated hits, damage-over-time, harmless casting, and easy farms affect
   earnings? The assistant proposed meaningful combat rewards rather than rewarding
   air-casting; this still needs a concrete rule that also treats support fairly.
3. Starting level, XP curve, damage increment, and whether damage growth has a cap.
   A finite damage cap was recommended, not finalized.
4. How do bonuses apply across single hits, repeating zones, multi-hit spells, and
   ultimates? Damage growth does not itself specify healing, cooldown, root duration,
   range, or armor penetration upgrades.
5. How are XP, levels, and new slot capacity introduced for existing player saves?
   Preserve existing purchases, Flux, and remembered builds.

Balance needs early-, middle-, and late-game comparisons with similarly invested
Minecraft weapons and armor, including enchantments. Consider range, reliability,
burst, sustained damage, healing, control, and escape opportunities. Adding levels
alone will not fix an already overpowered spell. Exact numbers require tests.

The user reported that Nature's **Thorn Lash sweeping vine** kills an unarmored
player in roughly three hits. The user explicitly deferred diagnosis/tuning to a
separate session. This is reported feedback, not a reproduced result, and should
not turn a future planning session into an unrequested combat fix.

### Working proposal: grow from one equipped spell to five

Begin with one Basic/primary. The user's current candidate for the maximum is:

| Slot role | Count |
| --- | --- |
| Basic / primary | 1 |
| Technique / secondary | 2 |
| Ultimate | 1 |
| Flexible: Basic or Technique | 1 |

This permits either two Basics + two Techniques + one Ultimate, or one Basic +
three Techniques + one Ultimate. All refer to the active element's learned spells.
The user called this layout a hunch; prototype it before treating it as final.

Level milestones are the current proposed way to unlock slots. Special crystals
were also mentioned as an alternative. Exact requirements remain open. The
assistant suggested Basic -> Technique -> Ultimate -> second Technique -> flexible
as an unlock order; that sequence is not confirmed.

Also open: five-input usability, key bindings, duplicate spell rules, and how
multiple equipped spells interact with cooldowns. Shared recovery between Basics
was suggested to prevent alternating Basics from simply doubling attack rate.
Technique combinations need their own evaluation. Do not assume every extra slot
is only a convenience; more available attacks can substantially increase strength.

## 5. Spells, loot, and reasons to explore

### Agreed direction: two ways to learn spells

Keep the Flux shop and add spell unlock books from exploration and bosses. Players
can grind toward a desired purchase or earn a free unlock through an encounter.
Both should feed the same permanent spell ownership for the selected element.

The proposed Guardian reward is a book for one Basic or Technique spell. Later,
harder bosses can award better books with access to higher-value choices. The
exact book grades, spell pools, and possible Ultimate access remain to be designed.

The discussed simple redemption flow is a working proposal:

1. Use the book and select an element.
2. Choose an eligible, unlearned spell.
3. Consume the book and permanently learn that spell for that element.

Learning a spell should still use that element's power and an appropriate equipped
slot. A rare unlock should not silently transfer high Fire strength into a newly
chosen Stone build. Decide eligibility for any future advanced spells explicitly.

Use **category** for Basic, Technique, and Ultimate. If book grades or spell
rarities are introduced, distinguish them from categories and element levels.

### Agreed cooperative reward principle

**Each eligible participant gets one spell unlock book for a shared boss victory,
alongside shared chest treasure.** One friend should not receive the only meaningful
magic reward from the group's success. This is a planned reward rule, not the
current church loot behavior.

Eligibility, delivery when inventories are full, disconnects, repeat victories,
late joiners, and how personal rewards coexist with a shared chest remain open.
Current churches can each be completed once; account for that when designing
repeatable progression and distributing rewards.

### Candidate supporting content

- Smaller encounters and optional structures can contain loot, clues, or materials.
- One-use crystals could temporarily enhance a spell or wand. This is separate
  from spell unlock books and permanent element XP. Specific crystal effects,
  rarity, stacking, and consumption rules have not been approved.
- An Echo Crystal that repeats a spell was only a brainstorming example.
- Maps and short summoning instructions can make a boss reward lead to another
  expedition without requiring a full quest system.

The design goal is useful exploration rewards with a small number of understandable
systems. Avoid adding currencies, resource trees, or rarity layers merely to fill
out a progression chart.

## 6. Lore and the End encounter

### Agreed narrative direction

Lore should resemble discoverable references within the world. Mechanics, combat,
loot, and player freedom are the priorities. A player should be able to understand
how to use a reward or summon a boss without studying the full history.

The working background is a vanished magical civilization. Magic once enabled
great achievements, but the pursuit of more power led to selfishness, conflict,
and war. A great being associated with greed grew through that strife and corrupted
the land's magic. It was previously stopped somehow; magic is now returning.

These are themes, not a finalized canon. The earlier victory, disappearance of the
civilization, source of magic, and exact nature of the creature remain unresolved.
Do not assume the player's objective is to erase all magic or end survival play.

### Agreed encounter direction

- A spectacular, enormous creature is the long-term End challenge.
- Reaching the End makes the encounter accessible regardless of element level or
  completion of other bosses. Entering the End should be able to trigger it.
- Players who attempt it too early can fail, prepare elsewhere, and return.
- The giant must actively participate in the fight; its presence is central to
  the intended experience.

### Visual and combat concepts, still open

- A serpent- or eldritch-like being so large players cannot see all of it at once.
- The dragon is already dead or is killed in front of the players during the reveal.
  Neither the exact scene nor replacing vanilla dragon progression is finalized.
- Corrupted versions of players and fallen figures from the old civilization
  could appear at different points in the broader adventure.
- The creature represents greed and hunger for power. How that symbolism becomes
  a defeatable encounter, and the exact victory method, remain open.

An early technical assessment found the scene plausible, not proven by a prototype.
Keep this a later content milestone, after ordinary progression and combat work.

Before implementation, define retreat, wipes, resets, simultaneous arrivals,
late joiners, dragon/portal behavior, existing worlds, rewards, and rematches.
The assistant proposed one shared fight with deliberate rematches after victory;
this is not yet an agreed encounter lifecycle. "Enter the End to trigger it" must
not accidentally restart an ongoing fight on every arrival.

## 7. UI and onboarding goals

The user expects a UI revision if leveling and additional slots are adopted.
Settle progression rules first, then design and review the interface.

The proposed information hierarchy is:

- Active element, its level, and a clear XP meter to the next level.
- Spendable Flux shown separately from XP.
- Current damage bonus and spell values at the player's current element level.
- Equipped abilities, category labels, and understandable locked-slot requirements.
- Spell learning through either Flux or an eligible book.
- Short, contextual guidance for starting, exploration, and summoning.

Raw spell damage should be distinguished from actual health lost after a target's
defenses. Exact screen structure, art, layout, and five-slot controls are open.
Avoid a lengthy mandatory lore tutorial or a UI that reveals technical internals.

## 8. Existing foundation and planned work

Source/doc snapshot reviewed September 16, 2026; recheck before coding:

| Area | Existing foundation | Planned addition |
| --- | --- | --- |
| Magic | Universal wand, five affinities, distinct spells | Further build options as their purpose becomes clear |
| Progression | Per-element Flux, permanent spell ownership, remembered loadouts | Per-element XP, levels, and damage growth |
| Slots | One Basic, one Technique, one Ultimate | Progressive capacity and proposed five-slot layout |
| Interface | Loadout, Spell Store, Controls, short welcome guide | Level/XP presentation, new slots, book redemption |
| Multiplayer | Parties and shared allied-wand protections | Broader PvP balance and progression/reward rules |
| Exploration | Generated Guardian churches and offering ritual | Wand guidance, further encounters, maps and clues |
| Bosses | Guardian, cooperative arena, restoration and ordinary treasure | Personal unlock-book rewards and later bosses |
| Endgame | Concept only for the giant End creature | Prototype and eventual encounter |

Starting references:

- [ElementProgress](file:///Users/antonlabas/Desktop/elementalwands/src/main/java/com/anton/elementalwands/data/ElementProgress.java)
  currently stores Flux and owned spells, not the planned XP/level fields.
- [WandProgression](file:///Users/antonlabas/Desktop/elementalwands/src/main/java/com/anton/elementalwands/util/WandProgression.java),
  [WandSpells](file:///Users/antonlabas/Desktop/elementalwands/src/main/java/com/anton/elementalwands/data/WandSpells.java), and
  [WandLoadouts](file:///Users/antonlabas/Desktop/elementalwands/src/main/java/com/anton/elementalwands/util/WandLoadouts.java).
- [Current store](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-store-2026-09-12.md),
  [current categories](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-guide-2026-09-12.md), and
  [simplified welcome](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-guide-simple-2026-09-12.md).
- [Current parties](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fparties-2026-09-12.md) and
  [Guardian church](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fguardian-church.md).

This documentation session does not change gameplay or confirm live balance.

## 9. Suggested work order

Current task statuses and completion evidence are maintained in
[Progress.md](Progress.md). The sequence below describes the intended direction.

This is a proposed sequence for future sessions, not an approved implementation
specification or a promise to finish every feature before playtesting.

1. **Finish the progression rules.** Decide XP earning, growth curve, damage
   scaling/cap, slot milestones, and save migration. Write simple player examples.
2. **Establish combat benchmarks.** Compare starter and advanced magic against
   relevant mobs, bosses, and players with different armor/weapon investments.
   Handle the deferred Thorn Lash report in a combat-focused session.
3. **Build one complete progression loop.** Introduce per-element growth with an
   understandable UI, preserve existing saves, and verify switching elements.
   Plan the slot/UI expansion together once the candidate layout is settled.
4. **Connect exploration to rewards.** Add book redemption and personal Guardian
   rewards, then make discovering encounters practical through wand guidance.
   Maps pointing to another boss need an actual destination before shipping.
5. **Run a small-group playtest and package it clearly.** Learn whether newcomers
   understand the system and want another session. Iterate before expanding scope.
6. **Expand from evidence.** Add spells, crystals, ordinary encounters, and bosses
   that serve an identified build or exploration need. Develop the End creature
   after the supporting combat and progression are enjoyable.

### First playable milestone

A newcomer can choose an element, use it to earn understandable progress, learn
another spell through Flux or a Guardian reward book, find an encounter without
operator commands, and enjoy it with friends. Each eligible boss participant gets
their own progression reward. Switching elements retains independent progress.

Use the current five-element foundation and Guardian to test this loop. The
complete boss roster, every crystal idea, and the enormous End encounter are not
prerequisites for obtaining real player feedback. Define exact release scope after
the progression rules are settled.

### Filter for new ideas

Before adding a feature, explain which player need it serves: a useful build
choice, understandable progression, rewarding exploration, enjoyable SMP combat,
or easier onboarding. Identify how it interacts with existing systems and what
playtest would show that it works. Keep unrelated ideas in a backlog.

## 10. Decision log and superseded directions

- **September 15:** explored a campaign about a lost civilization, a fixed map,
  and a border. These were exploratory choices, not implemented changes.
- **September 16:** the user chose generated-world survival SMP play. This
  supersedes the mandatory authored campaign, fixed map/border requirement,
  rejection of home-building, and church-as-required-first-chapter assumptions.
- **September 16:** retained mysterious wand guidance and optional lore; proposed
  boss maps/summoning clues and an End encounter accessible at any progression stage.
- **September 16:** selected persistent progress per player and per element,
  automatic XP levels with damage growth, and simple progression supporting builds.
- **September 16:** proposed starting with one spell and growing toward a five-slot
  loadout; exact layout, milestones, and cooldown interactions remain provisional.
- **September 16:** retained Flux purchases alongside loot-based spell books;
  agreed one book per eligible participant for cooperative boss rewards.
- **September 16:** created this living document to continue planning in future
  sessions. Next focus: the open progression rules in Section 4.
- **September 16:** added [Progress.md](Progress.md) to track milestones, work
  status, evidence, and updates separately from the long-term design.

- **September 16:** planning moved into the separate Elemental Wands Planning
  Obsidian vault. Vision and Progress are authoritative here; session history is
  in Updates. Repository documents are navigation pointers.

- **September 16:** the user chose to keep the planning vault inside the mod
  repository for future sessions. The five notes now live in
  `Elemental Wands Planning/`; local Obsidian settings are excluded from Git.
