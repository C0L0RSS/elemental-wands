# Progress

[Home](Home.md) · [Vision](Vision.md) · [Progress](Progress.md) · [Updates](Updates.md) · [Ideas](Ideas.md)


Last updated: September 16, 2026

## Current focus

**Next:** finish the rules for the first element-leveling implementation.
**Current stage:** planning. No gameplay implementation for the new vision has
started in this planning session.

[Vision.md](Vision.md) records what we want to build and why. This file
records what we are working on, what is finished, and what comes next.
[AGENT.md](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2FAGENT.md) owns codebase guidance and required checks. Detailed feature
reports remain in `docs/`; link them here instead of copying their release logs.

## Status meanings

| Status | Meaning |
| --- | --- |
| Backlog | An idea or planned outcome; work and detailed design have not started |
| Planning | Requirements or decisions are being worked through |
| Ready | The next bounded change and its completion checks are clear |
| In progress | Implementation or other concrete work has started |
| Testing | Implementation exists, but required checks or scoped playtests remain |
| Done | The item's agreed scope and relevant verification are complete |
| Paused | Deliberately set aside; record the reason and condition for resuming |

Record a concrete blocker in the item's notes when one exists. An undecided future
idea belongs in Backlog or Planning; it does not block unrelated work.

Completion is scoped to the item. A finished XP implementation does not mean the
whole progression system is finished. For a gameplay change whose acceptance
includes combat feel, unfinished playtesting keeps it in Testing. Documentation
can be Done after review and documentation checks.

Installation/publication is tracked separately. A feature can be Done but not
installed or released. Neither a successful build nor a copied JAR proves gameplay
quality. Record verified build/install evidence in the relevant feature report.

## Before the first gameplay change

These are the next planning decisions, not additional mechanics to add:

- [x] **Pacing direction:** quick, effort-driven leveling that supports trying
  builds early; use the feel of Minecraft gear progression as the reference.
  One focused session is the working target for a useful build, with exact
  timings and maximum-strength pacing still open.
- [ ] **XP earning:** define which actions count, how support and repeated damage
  are treated, and numerical targets for the first few meaningful upgrades.
- [ ] **Damage growth:** choose a starting balance target, increment/formula, and
  whether growth has a cap. Consider ordinary Minecraft armor and enchanted gear.
- [ ] **First implementation scope:** decide whether the first change introduces
  levels with the current three-slot system or also changes slot capacity. Set
  slot milestones and recovery rules before implementing any slot expansion.
- [ ] **Saved progress:** define new XP/level behavior on death/reset and migrate
  existing saves while preserving purchases, Flux, and per-element separation.
- [ ] **Acceptance examples:** describe a new player's first upgrade, switching
  Fire -> Stone -> Fire, and saving/rejoining. Define the minimum UI information.

Complete the decisions needed by the chosen implementation slice. Exact later
bosses, every spell, full crystal catalogs, and final lore can remain open. Revisit
PvP, reward, discovery, and End lifecycle details before implementing those areas.

## Milestones

These group the long-term work. They are not dates, a percentage complete, or a
requirement to finish all content before asking friends for feedback.

| Milestone | Status | Observable outcome |
| --- | --- | --- |
| M0 — Living plan and tracking | Done | Vision, task status, and updates are linked and usable by future sessions |
| M1 — Element growth | Planning | A player earns element XP, levels automatically, sees the benefit, and retains separate progress when switching elements or rejoining |
| M2 — Builds and useful rewards | Backlog | Settled slot progression and UI work together; Flux and books teach spells; every eligible Guardian participant receives their book |
| M3 — First group playtest | Backlog | New players can start, progress, find the Guardian without commands, and provide recorded feedback through a straightforward installation flow |
| M4 — More reasons to explore | Backlog | New encounters, spells, maps, and crystals each address an identified player need |
| M5 — The End creature | Backlog | The giant participates in a complete, retryable multiplayer encounter that is accessible at any progression stage |

Small tests should happen during M1 and M2 as soon as a coherent slice is playable.
M3 is a broader onboarding/progression check, not the first opportunity to test.

## Work board

IDs remain stable so future sessions and reports can refer to the same work.
Large Backlog items should be split into smaller tasks when selected. Dependencies
below are planning guidance; refine them when the scope becomes concrete.

| ID | Work item | Status | Next action / dependency |
| --- | --- | --- | --- |
| DOC-01 | Record the mod vision | Done | [Living vision](Vision.md); created and documentation-checked September 16 |
| DOC-02 | Establish progress and update tracking | Done | Tracker and guide/vision links verified September 16; update as future work occurs |
| DOC-03 | Create the planning vault | Done | Files verified; user opened and liked the vault September 16 |
| DOC-04 | Keep the planning vault inside the repository | Done | Links and Git ignore rules verified; new vault registered and Home opened in Obsidian September 16 |
| PLAN-01 | Define XP, damage growth, persistence, and first slice | Planning | Work through the decision checklist above; put agreed rules in the vision or a linked focused spec |
| PROG-01 | Implement per-player, per-element XP and levels | Backlog | PLAN-01; preserve existing progression and attribute earnings to the actual source element |
| PROG-02 | Apply and balance level-based spell damage | Backlog | PLAN-01; choose combat benchmarks and account for multi-hit/lasting effects |
| BUILD-01 | Settle and implement progressive ability slots | Backlog | Resolve the proposed five-slot layout, unlock order, inputs, and cooldown interactions |
| UI-01 | Present progression and builds clearly | Backlog | PLAN-01; plan/review the interface before a redesign; coordinate with BUILD-01 |
| LOOT-01 | Add spell unlock books alongside Flux purchases | Backlog | Define book pools, selection, consumption, duplicates, and eligibility |
| COOP-01 | Personal Guardian unlock-book rewards | Backlog | LOOT-01; define participation, full inventory, disconnect, and repeat-reward behavior |
| WORLD-01 | Wand guidance and practical encounter discovery | Backlog | Define target selection, controls, travel distance, and completed/unavailable destinations |
| SMP-01 | Define and test SMP combat behavior | Backlog | Set PvP/party, terrain/base damage, death, and late-joiner expectations; preserve current protections until changed deliberately |
| QA-01 | Investigate reported Thorn Lash lethality | Backlog | User deferred this to a separate combat session; reproduce the report before choosing a fix |
| RELEASE-01 | Make a reproducible playtest package and setup guide | Backlog | Choose client/server delivery and test setup on an unfamiliar player's machine |
| PLAY-01 | Run and record a friends' progression playtest | Backlog | Use a coherent available slice; record confusion, enjoyment, balance issues, and follow-up tasks |
| CONTENT-01 | Next boss, reward map, and summoning setup | Backlog | Establish a distinct encounter/reward purpose and an actual destination before shipping its map |
| CONTENT-02 | Consumable crystals and further build options | Backlog | Select a small set from playtest needs; effects and economy are still concepts |
| END-01 | Design and prototype the giant End encounter | Backlog | Set limits, player counterplay, dragon/portal interactions, retreat, shared state, retries, and rematches |

### Existing foundation

These systems predate this tracker. They are available starting points, not claims
that all balance, client feel, or release work is complete. References describe
past checks; reverify the relevant source/build when resuming work.

| Foundation | Evidence / remaining scope |
| --- | --- |
| Five elements and current spells | [AGENT.md](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2FAGENT.md); some human combat and visual follow-ups remain in feature reports |
| Per-element Flux, spell ownership, and loadouts | [Store report](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-store-2026-09-12.md); planned XP/levels are not implemented here |
| Three categorized slots and native hub | [Categories](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-guide-2026-09-12.md), [short welcome](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fwand-guide-simple-2026-09-12.md); five slots remain proposed |
| Parties and allied-wand protection | [Party report](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fparties-2026-09-12.md); broader SMP policy and human co-op follow-up remain |
| Guardian church, ritual, arena, and restoration | [Church report](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fguardian-church.md), [pedestal report](obsidian://open?path=%2FUsers%2Fantonlabas%2FDesktop%2Felementalwands%2Fdocs%2Fguardian-pedestal-2026-09-14.md); personal book rewards remain planned |

## Completion and release notes

| Item / release | Completion evidence | Installation / publication |
| --- | --- | --- |
| DOC-01 — vision | All 10 local document links resolved and whitespace checks passed when created September 16 | Repository documentation; not a game release |
| DOC-02 — tracking | 44 local links across tracker, vision, and guide resolved; 17 unique task IDs; Markdown whitespace checks passed September 16 | Repository documentation; not a game release |
| New progression features | No implementation in this planning session | Not released |

For future gameplay releases, add the release/report link and separately record
whether it was built, locally installed, or publicly released. Link exact version,
checksum, verification date, and remaining playtest notes from that report. Do not
copy historical installation hashes forward as if freshly verified.

## Session updates

Read [Updates](Updates.md) for dated changes, checks, and next steps. Add new
session entries there; maintain task statuses in this note.

## Updating this tracker during future work

1. At the start, read the vision and this board. Select an existing ID or add a
   bounded task. Set the status according to work actually underway.
2. Before coding, state the intended outcome and relevant completion checks in
   the task's notes or a linked implementation plan. Resolve only its dependencies.
3. When requirements change, update Vision.md for design decisions and this
   file for scope/status. Keep one status here rather than separate competing boards.
4. At session end, update status, add a short entry in [Updates](Updates.md), link verification,
   and record the next action and any remaining blocker or playtest.
5. Mark Done only when the defined scope and its required checks are complete.
   Keep installation/publication separate. Reopen an item if new evidence reveals
   unfinished scope; use a new ID for genuinely new work.

Updates happen as work occurs in the project and planning vault. This file does not schedule
background work or monitor progress automatically.
