# Pyre King

The Fire shardbearer: a swordsman with a chained sword. His design and the reasons behind
it are in the [roadmap](../ROADMAP.md#fire-the-pyre-king). This page covers what is in the
game: his fight, his body, and his arena, the
[Burned Throne Hall](#the-burned-throne-hall).

Since November 10, 2026 he has a first-pass fight. It has been tested by fixtures and read
by three reviewing models, whose findings were fixed the same day. Nobody has fought him in
Lunar. Its damage is set for diamond to netherite armor; the rest is untuned.

## Commands

Summon him with `/summon elementalwands:pyre_king`, or in his hall with `/ew hall summon`.
He fights any Survival or Adventure player who comes within 34 blocks.

| Command (operators only) | Effect |
| --- | --- |
| `/ew pyreking fight` | The nearest Pyre King within 48 blocks fights. This is his default. |
| `/ew pyreking calm` | He stands passive in his guard. |
| `/ew pyreking move <name>` | He uses that one move on you, then stands passive. It only hurts in Survival or Adventure. |
| `/ew pyreking unbound` | The phase change starts as soon as his move in hand allows. He must be fighting. |
| `/ew pyreking status` | His phase, move, health and fires. |
| `/ew pyreking <clip>` | He plays that clip from the start and stands passive. `walk` and `run` loop. |
| `/ew pyreking stop` | He returns to his guard. |

The moves are `royal_cut`, `royal_cut_three`, `thrust`, `turning_cut`, `overhead`,
`scythe_low`, `scythe_high`, `cast`, `zip`, `hook`, `lunge`, `haul`, `snare`, `drag_down`
and `unbound`. The clips are `idle`, `walk`, `run` and one per move; the
[art README](../../art/pyre_king/README.md#clips) describes each.

## The fight

He is a hand-written rule system, as souls-like bosses are. `PyreKingRules` holds a table
of moves and a chooser; `PyreKingCombat` runs one move at a time.

**Hits come from the animation.** `art/pyre_king/build_art.py` samples every attack clip
120 times a second and writes where his wrist, the sword's pommel and its tip are
(`pyre_king_tracks.json`, read by `PyreKingTracks`). Each tick the server sweeps the blade,
or the chain and blade, between those samples and strikes whoever the sweep crosses. So a
blow lands where and when it is drawn.

- Each move names the moments its weapon can hit (its windows), how thick the weapon is, and
  a height rule:
  - a **cut** reaches down to the floor under the blade, and no deeper;
  - a **low sweep** misses feet 0.6 or more above his floor (jump it);
  - a **high sweep** misses a body under 1.55 tall (crouch);
  - a **thrown sword** hits only where it really is.
- **One mistake, one hit.** A combo is a run of blows less than 1.2 s apart. A player takes
  at most one heavy hit (24 or more) per combo.
- **Shields.** A raised shield stops one blow and is knocked aside for 5 s, as by an axe.
  The next blow of the string can still land. Fire on the floor ignores shields.
- **Stone stops him** (the developer's choice, Nov 11). A pillar between him and a player
  shelters them from his blade in hand, the Chain Scythe, the Cast's pull-back, the Hook
  and the Snare. Unbound's chains and his lines of fire already stopped at stone.

**Moves.** Times are seconds into the clip. "Free" is when he may act again.

| Move | Warning | Can hit | Free | Notes |
| --- | --- | --- | --- | --- |
| Royal Cut | 0.55, 1.38 | 0.88–1.17, 1.73–2.07 | 2.95 | Tracks until 0.75, steps in up to 1.5; the backhand may turn 45° toward the dodge |
| Royal Cut (three) | + 2.25 | + 2.6–2.8 | 3.65 | Phase two. If an earlier blow landed he calls the third off and is free at 2.95; it leaves a 12-block line of fire |
| Thrust | 0.35 | 0.8–0.97 | 1.85 | Aim freezes at 0.35; he drives 2.4 forward, so it reaches about 8 |
| Turning Cut | 0.25 | 0.68–0.86 | 1.75 | Cuts the half behind him and ends turned about |
| Overhead | 0.25 | 0.7–0.87 | 1.75 | Aim freezes at 0.5; a line straight ahead, about 7.5 long |
| Chain Scythe, low | 0.2 | 0.7–1.17 | 2.1 | Chain and blade round him to 14; jump |
| Chain Scythe, high | 0.2 | 0.64–1.15 | 2.1 | The same at head height; crouch |
| Cast | 0.47 | 0.87–1.26 (throw), 3.48–3.8 (pull-back) | 4.25 | Aim freezes at 0.47 and leads a moving target. Two separate tests |
| Zip | ring | 1.22–1.64 | 2.55 | Follows a Cast: the chain hauls him to the sword and he spins into a cut |
| Hook | 0.6 | 1.0–1.37 | 2.9 | Catches and drags; the Overhead follows a catch |
| Lunge | 0.45 | 1.52–1.94 | 2.85 | He slings the sword short and flies after it into the spinning cut |
| Haul | 0.05, ring | none | 0.85 | Only round a pillar. A low sling at 0.3 s; the chain sets him down at 0.8 s, 10 to 28 blocks away. The Snare follows |
| Snare | 0.05 | 0.4–0.77 | 2.3 | Only after a Haul. The Hook with a 0.4 s tell: catches and drags; the Overhead follows a catch. A miss frees him at 1.2 |
| Drag Down | 0.2 | 0.8–1.07 | 1.75 | For a player overhead. Aim freezes at 0.7 and leads a moving target; yanks them to the floor at his feet |
| Unbound | 1.2 | 1.62–1.95 | 4.3 | The phase change |

- **Damage** (raw, Normal; armor and Protection reduce it): sword in hand 21, chain and
  thrown sword 16, floor fire 8, Unbound's sweep 19, Drag Down's yank 5, the Hook's tug 4.
  - It is tuned for plain diamond to netherite armor (the developer's target, Nov 11).
    There a sword blow costs 8.6 or 7.7 of 20 health, so two leave a player alive and the
    third kills. The chain costs 5.8 or 5.2, and a second in his fire about 2.
  - An unarmored player dies to one sword blow. The contract check holds the armored sums.
- **Openings are tight** (the developer's choice, Nov 10): he is free 0.6 to 1 s after his
  last blow. The contract check holds every move but the Cast, Hook, Snare and Haul to that.
- **No stagger** (the developer's choice, Nov 10): nothing interrupts him.

**How he chooses.** While free he runs at his target (4.5 blocks/s, 5.2 in phase two),
turning at most 9° a tick, and holds at 3.4 blocks. When his gap runs out he weighs the
moves for where the target stands and picks one by chance, so the same spot does not
always draw the same move.

| Target is | He weighs |
| --- | --- |
| More than 3 blocks up for 1.5 s | Drag Down, once within 18 blocks; until then he closes in |
| Within 6.5 | Royal Cut (most), Overhead, Thrust, a rare low Scythe |
| 6.5–14 | Low Scythe (most), high Scythe, Lunge, Thrust and Overhead where they reach |
| 14–26 | Cast (most), Lunge, a rare Hook |
| Beyond 26 | Hook (most), Cast, Lunge |

What the target just did tilts the weights:

- **Behind him** for 0.4 s: the Turning Cut, heavily.
- **Backed straight away** from his last sword string: the Thrust.
- **Jumped** in the last 2 s: the high Scythe instead of the low.
- **Eating or drinking** more than 5 blocks away: he stops waiting out his gap, and the
  Cast and Lunge are weighted up (the developer's choice, Nov 10).
- **Out of sight** behind a pillar: he starts no move at them. He runs round it.
- **Hiding** (out of his sight for 1.5 s): he flanks. He gets round the pillar himself; the
  hall never helps him, and nothing of his passes through the stone (the developer's
  choices, Nov 11). He picks one of two by chance, from those he has a clear flight for:
  - **The Haul, then the Snare** (most often). He slings his sword out to open ground 8 to
    16 blocks from his target and the chain sets him down there 0.8 s after he starts, a
    flight of 10 blocks or more. Of the spots he can fly straight to, he takes the one
    that leaves them furthest from cover. The line he flew burns for 4 s (the developer's
    choice, Nov 11): past a pillar, that is a wall across the way round it. If he can see
    them he whips round and throws the Snare 0.55 s after landing. A catch is dragged to
    his feet under the Overhead.
  - The Snare leads its target by what they did in the last 0.2 s, and its point goes 2.5
    blocks past where they will be. Thrown only as far as them, it fell a block short of
    a player running away from him, every time.
  - **A Lunge round the pillar.** He lands 3 to 5.5 blocks from them with nothing between,
    and spins into its cut. It is open to him only when they are near the pillar's edge.
  - A Cast and a Zip were the first flank (Nov 11) and were dropped the same day: they
    cost him 4 s, which a player circling the pillar outlasts. The Haul replaced them.
  - A Haul or a Lunge is drawn from where it began, so he first turns onto the line of
    his throw, up to 30° a tick.
  - The Snare wastes no time on a target who is behind the stone again. If he loses
    sight of them before the sword leaves his hand he keeps it, and is free 0.3 s later.
    A throw that catches nobody is whipped straight back: he is free 1.2 s in, not 2.3.
  - A target still unseen when a flank ends draws the next after 0.3 s, not 1.5.
  - With no flight open he keeps running round and looks again every 0.5 s.
- **Eating or drinking while hidden:** he flanks after 0.4 s, and the Lunge is the likely
  one, because it lands a blow soonest.
- He never uses a move three times running, and seldom twice. Drag Down is the exception:
  it is all he has for a player overhead.

**Combos.**

- After a sword string he sometimes goes straight into another with no gap: a 30% chance
  and at most two strings in phase one, 55% and three in phase two.
- After the Royal Cut's first blow he breaks off into the Turning Cut if his target is
  behind him, or often the Thrust if it has backed out to 6.5–9.5 blocks.
- A Cast ends in a Zip when the target is more than 16 blocks from him at 2.75 s (and
  sometimes in phase two). In phase two a second Cast follows the first.
- Otherwise his gap between strings is 0.3 to 0.8 s in phase one and 0.1 to 0.5 s in two.
- A move called off part-way (the third cut, a Drag Down whose target has landed) eases him
  back to his guard, so the clip does not go on to show a blow that cannot hit.

**Throws are aimed.** The clips throw the sword at one authored point. At the aim freeze
the server picks the real one and syncs the difference. `PyreKingAim` says how much of that
difference the sword, and for Zip and Lunge his body, has taken at each moment of the clip.
The server's sweep and the client's drawn sword (`PyreKingModel`) read the same numbers.

- A Cast lands where a moving target will be, short of any pillar in the way.
- A Lunge lands him about 3.6 blocks short of that point. Should something still stand in
  his way, he stops against it. A Haul with nothing to get round does the same.
- A hooked player comes in on the sword's hilt and stops 4 blocks in front of him. Moving
  3.5 blocks off the chain (a movement spell) or a pillar in the way snaps it. A pillar
  between him and the player stops the Hook outright.
- Drag Down pulls its catch off a pillar's top sideways, then down: round stone, never
  through it.
- He stands still while the sword is out. The roadmap's walk during the Cast's wait is not
  built.

**Zip, Lunge, Haul and the Turning Cut move or turn him inside the clip.** The server makes that
real as it happens, so his hitbox is where he is. Clients draw those clips from where the
clip began, so the two do not add up: `PyreKingRenderer` faces him as he faced then and
hands the model's root the shift back to where he stood. His shadow stays under his real
place.

**He is drawn where he is swept.** Three things keep a client's picture on the server's
blade:

- His facing is synced exactly and drawn from that (`PyreKingEntity.facing`). Vanilla would
  let each client swing his body round up to 75° behind his head.
- His position eases over one tick, not vanilla's three, so a fast step is not drawn a
  block behind.
- A clip's time is set from the tick the server named (see below), not from when the
  client first drew it.

**Fire.** Both phases leave fire (the developer's choice, Nov 10). It shows as low flames
for 0.5 s, then burns on contact and once a second. It uses his own damage type, so Fire
Resistance does not stop it. A burn within 1 s of a weapon hit deals only what exceeds it.

A line of fire stops at a pillar.

| Fire | Height | Lasts |
| --- | --- | --- |
| Where a Cast lands | 1 block | 3 s |
| The line a Haul flew | 1 block, 2 in phase two | 4 s |
| The third Royal Cut's line (phase two) | 1 block | 3 s |
| A Cast's pull-back path and a Zip's line (phase two) | 2 blocks | 4 s |
| Unbound's six lines | 2 blocks | 4 s |

**Unbound.** At half health he holds there until the move in hand ends, then plays Unbound
and cannot be hurt. Six burning chains sweep the floor to 12 blocks (jump; a pillar between
you and him shelters you). At 2.7 s six lines of fire are lit where the chains lay. Phase
two starts when the clip ends, or at once if the clip is cut short. A reload while he is
held at half health starts Unbound again.

**Warnings.** Each blow has a sound and a ring of embers at his feet. A throw's line is
marked on the floor from the aim freeze, and a ring marks where a Zip or Lunge will land.
All of these, and the fire, are particles sent by the server.

**Around the fight.**

- A boss bar shows his health. Health is 600, plus 60% for each player after the first.
- In co-op he does not pick the same player twice running, and a flame marks his new target.
  A target he has chased for 6 s without starting a move loses its turn.
- With nobody eligible nearby for 10 s he resets: full health, phase one, back on his mark.
- He stays within 36 blocks of where he started.
- When he dies he burns away where he stands. He has no death clip and gives no reward.

## What a rehearsed clip does not do

`/ew pyreking <clip>` plays the clip as authored. Nothing is aimed, nothing hits, and Zip,
Lunge and the Turning Cut leave him back where he stood when the clip ends.

## Ownership

| Concern | Owner |
| --- | --- |
| Model, texture, glow mask, clips, effect numbers, weapon tracks | `art/pyre_king/build_art.py` (`--install` writes the game's copies; `--check` verifies them) |
| Moves, chooser, tuning | `entity/pyreking/PyreKingRules` |
| The fight loop, hits, fire, warnings | `entity/pyreking/PyreKingCombat` |
| State, synced clip, damage rules | `entity/pyreking/PyreKingEntity` |
| Weapon tracks and thrown-sword aim | `entity/pyreking/PyreKingTracks`, `PyreKingAim` |
| Clip table | `entity/pyreking/PyreKingClips` (generated) |
| Command | `command/PyreKingCommands` |
| Model and renderer | `client/model/PyreKingModel`, `client/renderer/PyreKingRenderer`, `PyreKingRenderState` |
| Blade fire and chains | `client/renderer/PyreKingEffectsLayer` |

The model, its 46 bones and its clips are the approved review candidate, installed
unchanged. The Haul and Snare clips (Nov 11) are the exception: the developer has not yet
reviewed them. Do not edit the installed files, `PyreKingClips.java` or
`pyre_king_tracks.json`; change the art sources and run
`py -3 art/pyre_king/build_art.py --install`.

**How clients follow his clip.** The server names the clip in synced data
(`PyreKingEntity.play`), and every client starts it when that arrives.

- A clip may start with a blend of a few ticks; the clip's first frame then plays after the
  blend, and the server counts from there.
- Once a one-shot clip is running, its time is set from the tick the server said its first
  frame plays on. A client that was looking away, or has only just come near, joins the
  clip where the server is.
- A finished clip holds its last frame, his guard, until the next is named.
- Some clips end a whole turn round, three end with him moved or turned, and two end with a
  hand on the far spelling of its angles. Before a blend, `PyreKingEntity.rebase` takes
  that out of the pose being blended from. Without it the blend would unwind the turn,
  slide him back, or spin the hand.
- A clip that carries him, cut off before its travel or turn was made real, starts the next
  with no blend.

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
  installed files, including `PyreKingClips.java` and `pyre_king_tracks.json`.
- `./gradlew build` runs `checkPyreKing` (`PyreKingContractTest`): every move fits its clip
  and warns before it lands, the openings are tight, the thrown sword's aim, the chooser's
  reads, and the sweep's geometry.
- `pyre_king_fight_smoke.init.gradle` runs a hidden, muted client against a Survival player.
  - Twenty-seven cases check each move: its damage, its reach, a sidestep after the aim
    freezes, the jump, the crouch, a raised shield, where a thrown sword lands, the drags,
    the Haul, Unbound, and a pillar in the way of a Cast, a Hook, the Scythe and Unbound's
    chains.
  - Three of them are for hiding, behind a round pillar 7 across like the hall's.
    - A player standing behind it draws a flank within 3 s, is seen within 3 s more, and
      is struck. In the last run he Hauled 17.5 blocks, then Snare, then Overhead.
    - A player who sprints round it for 30 s, acting on where he was a quarter of a second
      ago, and eats for the second half (which slows them to a crawl, as the game does),
      must be flanked six times and struck twice, and never go unseen for 6 s. In the
      last run: seven flanks, hooked once in the first 15 s, struck four times while
      eating, burned once, 79 raw in all.
    - A player who circles perfectly, with no reaction time, must be flanked three times
      in 15 s. In the last run: three Hauls, two of them ending in a catch, and both
      Overheads missed as they ran on, so 8 raw in all.
  - Four cases check that the drawn sword is where the server sweeps it (within 0.01 of
    the pommel). They measure a sword at rest; no case yet measures a blow in mid-swing.
  - A free fight then runs through Unbound to his death. The player stands, backs away,
    circles and jumps, runs far off, eats, climbs out of reach and gets behind him.
  - `-PpyreFightRecord` also saves a frame of the free fight every other tick, and
    `-PpyreFightRecordCase=<name>[,<name>]` a frame of those cases.
- `pyre_king_client_smoke.init.gradle` runs a hidden, muted client. It checks that he uses
  his GeckoLib renderer and that every clip in `PyreKingClips` is in the baked animation
  file. It then rehearses every clip and saves 56 screenshots to
  `build/pyre-king-client-smoke/screenshots/`.
- The screenshots show how he draws in the fixture. Nobody has yet looked at him, or
  fought him, in Lunar.

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
| `/ew hall summon` | Operators | Replaces any Pyre King in the slot with a new one at the centre. He fights Survival and Adventure players |
| `/ew hall reset` | Operators | Clears the slot's entities and lays it out again |
| `/ew hall status` | Operators | Layout generation, built slots, return points held |
| `/ew hall leave` | Anyone in the hall | Returns them to where they entered |

**Rules in the hall.** They reuse the other realms' hooks.

- Survival players can't break or place blocks. Explosions and Hollow Purple destroy
  nothing and start no fires.
- A Survival player who leaves the walled floor is set back at the arrival. Teleports can't
  cross into or out of the hall, and must land on the floor.
- The pillars are scenery (the developer's choice, Nov 11). A Survival player who lands on
  one slides off it, away from its middle, and the drop does not hurt. Updraft and
  Springbloom would otherwise put a player on the shorter ones with one cast.
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
- The floor radius, ceiling, arrival, king's mark, lava height, tile grid and the pillars'
  ring (20 out, every 60 degrees) are duplicated in `PyreHall` and must stay in step.

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

- **Tuning.** Damage is set for plain diamond to netherite armor. His health (600), his
  speeds and every timing are first guesses, untested against real players.
- **Art:** a death, a hit reaction, the mask cracking away, and the Double Cast as its own
  clip. Phase two's run plays the 4.5 blocks/s clip at 5.2.
- **Effects:** the warnings and the fire are server particles and vanilla sounds. He has no
  sounds of his own.
- **From the roadmap's plan:** walking during the Cast's wait, a sword that sticks in a
  pillar (he does not throw at a target behind one), pull-backs that bow toward the dodge
  or run through a player, the memory of a reversal, a Cast answering a movement spell,
  and Stone Wall as cover.
- **Open to play:** a player who stays in the air (a hover, a string of launches) draws
  only Drag Down. Nobody can stand on a pillar in the hall.
- **Open to play:** a player who only sprints round a pillar is now hooked off it on about
  every second Haul, but in the fixture they run clear of the Overhead that follows, so it
  costs them little health. Eating, hesitating or standing still costs far more. Nobody
  has tried this in the hall or against a person.
- **The Snare's turn and its recall are the game's, not the clips'.** He turns on his
  target up to 36° a tick while the clip plays straight ahead, and a throw that catches
  nobody is blended back to his guard in a quarter of a second.
- **Around the fight:** a reward, an intro, a way into the hall for Survival players, and
  the hall's per-block floor protection.
