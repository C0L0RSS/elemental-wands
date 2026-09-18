# Elemental Wands documentation

Start with [AGENT.md](../AGENT.md) for repository working rules. Read the relevant
current guide below; there is no requirement to read this whole folder.

| I want to understand… | Current reference |
| --- | --- |
| What still needs attention | [Current status](STATUS.md) |
| Slots, controls, store, levels, spell books, parties | [Progression and controls](reference/progression-and-controls.md) |
| What each element does and which code owns it | [Spells](reference/spells.md) |
| The 3D wand and its animated elemental glass | [Wand rendering](reference/wand-rendering.md) |
| Visual style and which assets are safe to regenerate | [Art and assets](reference/art-and-assets.md) |
| Guardian combat and animation constraints | [Guardian combat](reference/guardian-combat.md) |
| Churches, rituals, arena returns and saved-world recovery | [Church and arena](reference/church-and-arena.md) |
| How automated Minecraft previews and checks work | [Testing](reference/testing.md) |
| Short summaries of player-facing changes | [Releases](releases/README.md) |
| Why an old decision was made or how a bug was verified | [Historical archive](archive/README.md) |

## Keeping this useful

Current references describe present behavior and the constraints that are hard to
infer from code. Exact implementation details and tuning belong in code; each
reference points to the relevant owners. Update these guides in place rather than
stacking dated corrections above obsolete descriptions.

Release summaries are a few player-facing bullets per date. Avoid class inventories,
mesh counts, hashes, and copied test output. Mention a verification limitation only
when it matters to understanding that release.

The archive records history, not current instructions. Consult it selectively.
Old reports may intentionally describe superseded mechanics; their dates and
historical banners are part of their meaning. Preserve reusable art sources;
prune duplicate or superseded evidence after checking references.
