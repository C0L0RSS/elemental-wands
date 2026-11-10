# Pyre King

The Fire shardbearer. His design, kit and proposed fight are in the
[roadmap](../ROADMAP.md#fire-the-pyre-king). This page covers what is in the game.

## What exists

Since November 10, 2026 he is a summonable body with no fight:

- He stands in his guard and plays his idle clip.
- He has no targets, no attacks, no damage and no movement of his own.
- An operator can make him play any of his 16 clips.
- His health (600) is a placeholder.

Summon him with `/summon elementalwands:pyre_king`. He faces south by default; add
`{Rotation:[180f,0f]}` to face him north.

| Command (operators only) | Effect |
| --- | --- |
| `/ew pyreking <clip>` | The nearest Pyre King within 48 blocks plays that clip from the start. `walk` and `run` loop until stopped. |
| `/ew pyreking stop` | He returns to his guard. |

The clips are `idle`, `walk`, `run`, `royal_cut`, `royal_cut_three`, `kingsguard_thrust`,
`turning_cut`, `overhead_sentence`, `cast_reel`, `chain_scythe_low`, `chain_scythe_high`,
`zip`, `hook`, `lunge`, `drag_down` and `unbound`. The
[art README](../../art/pyre_king/README.md#clips) describes each one.

## What a rehearsed clip does not do

- **He does not really move or turn.** Turning Cut, Zip and Lunge turn or carry him inside
  the clip only. When the clip ends he is back where he stood, facing the way he faced.
  `PyreKingClips` records where each clip leaves him (`endTurnDegrees`, `endX/Y/Z`) for the
  fight to apply later.
- **The sword is not a separate thing.** A thrown sword is his own sword bone carried far
  from his hand by the clip, at the clip's fixed distance (27 blocks for the Hook).
- **Nothing hits anyone.** Blade, chains and fire are visuals only.

## Ownership

| Concern | Owner |
| --- | --- |
| Model, texture, glow mask, clips, effect numbers | `art/pyre_king/build_art.py` (`--install` writes the game's copies; `--check` verifies them) |
| Entity, clip table | `entity/pyreking/PyreKingEntity`, `PyreKingClips` (generated) |
| Command | `command/PyreKingCommands` |
| Model and renderer | `client/model/PyreKingModel`, `client/renderer/PyreKingRenderer`, `PyreKingRenderState` |
| Blade fire and chains | `client/renderer/PyreKingEffectsLayer` |

The model, its 46 bones and its clips are the approved review candidate, installed
unchanged. Do not edit the installed files or `PyreKingClips.java`; change the art sources
and run `py -3 art/pyre_king/build_art.py --install`.

Every clip starts with no blend (the controller's transition is 0). The clips start and
end in his guard, and the ones that spin him end a whole turn round, which a blend would
unwind.

## Blade fire and chains

`PyreKingEffectsLayer` is the game's copy of the workshop's `effects.js`, which the
developer approved in the workshop. It draws three things in the world each frame, from
his posed bones:

- **Blade fire.** Upright flames along the blade. They lie back along the path the blade
  just swung and leave a fading wake behind a fast blow.
- **The sword's chain,** from his wrist shackle to the pommel while the sword is out of
  his hand. It bows while the sword is swung on it.
- **Unbound's six chains,** from his chest to each marker bone the clip has moved. They
  burn, every third link glows, each ends in a barbed hook, and they bow with their swing.

Both copies read the same numbers. The workshop reads them from the candidate's
`manifest.json`; the game reads `pyre_king_effects.json`, which the installer writes from
that manifest. **A change to how the effects are built must be made in both
`effects.js` and `PyreKingEffectsLayer`.**

The two differ in one way. The workshop re-poses the clip to learn where the blade was a
moment ago. The game remembers where it really was, for up to 1.15 seconds per king. So in
the game the fire also leans when he is moved or turned, and it starts from rest when he
first comes into view.

The layer poses the model itself before reading any bone, because GeckoLib 5 poses the
mesh later, in a deferred callback.

## Verification

- `py -3 art/pyre_king/build_art.py --check` verifies the candidate, the workshop and the
  installed files, including `PyreKingClips.java`.
- `pyre_king_client_smoke.init.gradle` runs a hidden, muted client. It checks that he uses
  his GeckoLib renderer and that every clip in `PyreKingClips` is in the baked animation
  file. It then rehearses every clip and saves 48 screenshots to
  `build/pyre-king-client-smoke/screenshots/`.
- The screenshots show how he draws in the fixture. They do not show motion, and nobody
  has yet looked at him in Lunar.

## Not built yet

The fight: targets, attack choice, damage, real movement, the thrown sword as its own
object, the arena, phase two, a death, and sounds. The effects have no ember rings,
ember lines, floor sparks or fire walls.
