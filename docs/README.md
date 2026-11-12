# Elemental Wands documentation

Start with [AGENTS.md](../AGENTS.md). Its ["Where to read next"](../AGENTS.md#where-to-read-next)
table is the one list of these documents; this page only says what each kind of document
is for and how to keep it useful. There is no requirement to read the whole folder.

## What each page is for

| Page | Holds | Does not hold |
| --- | --- | --- |
| [Feature index](INDEX.md) | One row per feature: owning files and the reference to read | Behavior, tuning, history |
| [reference/](reference/) | How a system behaves today and the constraints that are hard to infer from code | Dated corrections, playtest diaries, values that the code states exactly |
| [design/](design/) | Why a built boss is the way it is: decisions, reasons, the developer's playtest feedback | Current behavior (that is the reference) |
| [Status](STATUS.md) | Open work: one bullet per thing awaiting a review, playtest, check or fix | Anything resolved, test receipts, change history |
| [Roadmap](ROADMAP.md) | What is still ahead, marked decided, proposed or open | Design logs for things already being built |
| [releases/](releases/README.md) | A few player-facing bullets per change | Class inventories, mesh counts, hashes, copied test output |
| [archive/](archive/README.md) | History: old reports, evidence, retired designs | Current instructions |

## Rules

- **One current explanation per topic.** Update a reference in place when behavior
  changes; do not stack a dated correction above an obsolete description.
- **Code owns exact values.** A reference explains intent and names the owner. Point to
  the class for tuning numbers that change often.
- **Keep the index true.** When you add, rename, move or retire a feature, change its row
  in [INDEX.md](INDEX.md) in the same task. Use backticks there only for files, folders and
  classes: `./gradlew checkDocs` fails on any that does not exist.
- **Status shrinks as well as grows.** Add a bullet when something is left open; delete it
  when it is settled. Put the item in the section that says who can clear it (a review, a
  solo session, a co-op session, an automated check).
- **Playtest feedback has a home.** What the developer said and what was decided goes in
  the boss's design notes or the system's reference, not in status or the roadmap.
- **A plan leaves the roadmap when building starts.** Move a boss's section to `design/`
  and keep its row in the roster.
- **Fixture descriptions live in the fixture.** The testing guide lists which runner covers
  which area and its flags; it does not restate assertions.
- **Check before finishing.** Run `./gradlew checkDocs` and `git diff --check` after editing
  documents. It verifies the index, `package/Class` names and every relative link and
  heading anchor outside the archive.

The archive records history, not current instructions. Consult it selectively.
Old reports may intentionally describe superseded mechanics; their dates and
historical banners are part of their meaning. Preserve reusable art sources;
prune duplicate or superseded evidence after checking references.
