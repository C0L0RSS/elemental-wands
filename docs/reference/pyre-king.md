# Pyre King

The Fire shardbearer. His design, kit and proposed fight are in the
[roadmap](../ROADMAP.md#fire-the-pyre-king). This page covers what is in the game: his
body and his arena, the [Burned Throne Hall](#the-burned-throne-hall). He has no fight.

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

## The Burned Throne Hall

His arena is its own dimension, `elementalwands:pyre_hall`: the throne hall he set alight,
on a crag in a lava sea. It is a first pass (November 10, 2026). The developer liked it from
screenshots and the preview, and approved it the same day once the braziers were in. They have
not walked it in Lunar, and only operators can reach it.

**The hall.** Offsets are from the centre block; the fight floor is y=0.

- **Fight floor:** round, flat, 81 blocks across (every cell within 40.5 of the centre). It
  is patterned in blackstone and nether brick, charred at the centre where the pyre stood,
  with hardened runs of melted gold and long cold cracks. Nothing on the floor does damage:
  the developer ruled out magma (Nov 10), and the builder refuses any inside the wall.
- **Pillars:** six broken columns 20 blocks from the centre, at azimuths 0, 60, ... 300
  degrees (east, then clockwise through south), so none stands between the gate and the
  throne. They are about 7 across and 5, 8, 12, 17, 24 and 34 blocks tall. They are the
  only things on the floor.
- **Edge:** a one-block kerb carries an invisible barrier wall, with a barrier lid 33 up.
  Beyond it a raised gallery holds the rubble, fallen drums and fires.
- **Great braziers:** twelve torch-pillars stand on the gallery about 45.5 from the centre,
  roughly every 30 degrees, with a pair each side of the gate and of the throne. Each is a
  shaft 3 across under a bowl 7 across, its rim 15 above the floor, heaped with fire. Eleven
  burn; the one beside the east breach is snapped and cold. Block light only reaches 15
  blocks, so each lit brazier also has seven invisible `light` blocks outside the fight
  volume that carry its glow to the floor's rim (level 10 at most there, none by 30 from
  the centre).
- **Wall:** a round drum about 30 high with sixteen bays, pointed windows and three siege
  breaches. The roof is gone; its ribs still arc toward a broken ring 62 up, hung with
  chains and lanterns.
- **Gate and throne:** players arrive at (0, 1, 33), just inside the south gate, facing
  north. The king stands at the centre facing them. Behind him, in an apse in the north
  wall, a gold-edged throne stands on a dais under a broken round window. Two braziers and
  two hanging lanterns flank it, and fifteen invisible `light` blocks just in front of its
  back light it from top to bottom. The builder refuses a light block inside the fight
  volume.
- **Outside:** a causeway runs south from the gate over the lava; two more leave the crag
  lower down. Basalt spires stand in the sea. The dimension's flat generator supplies the
  lava; the layout has none.

The dimension borrows the Nether's thick fog and has no sky. `NaveFogMixin` ends the haze
170 blocks out (sooner with a short render distance). Ambient light is 0.4, raised from
0.25 with the braziers so the middle of the floor, which no block light reaches, stays
readable. The fog is a dark red, and ash drifts.

Minecraft's fog is linear and stops abruptly at its end, which showed as a hard line where
the bright sea met the sky. `client/PyreSeaHaze` hides it: each frame it draws a ring of
fog-coloured haze just above the lava around the camera, clear out to 30% of the fog's end
and opaque from 90%. Spires and the crag rise through it. It uses the frame's own fog
colour (`PyreSeaFogColorMixin`) and the render distance the server allows, so it also
covers the sea where far chunks are missing.

**Commands.**

| Command | Who | Effect |
| --- | --- | --- |
| `/ew hall enter [slot]` | Operators | Lays the slot out if needed, records where the player stood, and puts them at the arrival |
| `/ew hall summon` | Operators | Replaces any Pyre King in the slot with a new one at the centre, in his guard |
| `/ew hall reset` | Operators | Clears the slot's entities and lays it out again |
| `/ew hall status` | Operators | Layout generation, built slots, return points held |
| `/ew hall leave` | Anyone in the hall | Returns them to where they entered |

**Rules in the hall.** They reuse the other realms' hooks.

- Survival players can't break or place blocks. Explosions and Hollow Purple destroy
  nothing and start no fires.
- A Survival player who leaves the walled floor is set back at the arrival. Teleports can't
  cross into or out of the hall, and must land on the floor.
- Nothing drops on death; inventory and experience carry over to the respawn.
- The nave's per-block protection (spell blocks can't open the floor) is not applied yet.

**Slots and storage.** There are eight slots, 1024 blocks apart. A slot is laid out one
48x48 tile per server tick the first time it is used, then reused. When the installed
tiles change, every slot moves to untouched ground. `elementalwands/pyre-hall.json` holds
the generation, the built slots and each player's return point, saved before anyone moves.
Owners are `hall/PyreHall` (constants and geometry), `hall/PyreHallManager` and
`command/HallCommands`.

**Authoring.** `art/pyre_hall/build_layout.py` authors the hall from one seed.

- The default run writes the browser preview to `.local-previews/pyre-hall/`; `--serve`
  serves it on port 8420. The preview draws the king in his guard for scale, takes
  `?view=`, `&look=` and `&bare=1`, and is laid out for a phone.
- `--install` writes 22 structure tiles to `structure/pyre_hall/`; `--check` detects drift.
- The builder refuses a layout whose fight floor is not flat and empty apart from the
  pillars, or that contains lava, gravity blocks or anything outside the tile grid. It also
  requires twelve braziers with at least ten burning and the gate and throne pairs whole.
- The floor radius, ceiling, arrival, king's mark, lava height and tile grid are duplicated
  in `PyreHall` and must stay in step.

**Verification.** `pyre_hall_client_smoke.init.gradle` runs a hidden, muted client. It
enters by command, checks the floor, the barrier, the lava's height and the summoned king,
counts the braziers' flames, checks there is no magma inside the wall, measures the light
on the floor, saves fifteen screenshots to `build/pyre-hall-client-smoke/screenshots/`, and
leaves. Four show the sea at render distances of 8, 12 and 16 chunks, and at 16 capped to 8
as a server would; each waits until the client has built its terrain. Nobody has walked the
hall in Lunar.

**Known weak spots.** Where the sea gives way to haze is now a soft ramp, but a sharp eye
can still find it, most easily from the low causeway. The throne's lanterns are small from
across the hall; the invisible lights do most of the work. The braziers' pools of light on
the floor's rim are faint: most of the hall's brightness is the ambient light.

## Not built yet

The fight: targets, attack choice, damage, real movement, the thrown sword as its own
object, phase two, a death, and sounds. The effects have no ember rings, ember lines, floor
sparks or fire walls. Survival players have no way into the hall.
