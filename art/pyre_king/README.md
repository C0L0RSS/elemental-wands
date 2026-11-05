# Pyre King — tenth review sculpt

Based on [the approved masked king](concepts/direction-d-mask.png). Passes 1–5 found the
form; passes 6–9 reworked it after the developer's review and four rounds of independent
grading. A final touch brought the cape over both shoulders. Pass 10 polished the crown,
beard, grip and blade fire.

## The design

- **Height:** upright, he is exactly 80 px (5 blocks). In the guard pose the torso leans 6°
  forward and the head is lowered 7°, which measures 4.91 blocks.
- **Head:** the head sits in a stepped gorget under a lower crimson collar, so nothing
  floats. A full skull backs the gold death mask. The mask has angled brows, narrow amber
  eye slits, one dominant crack and molten gold drips. Five openwork crown points rise from
  the band: in each, two gold bars lean together over a solid foot, and a small diamond
  finial caps the point. One point has sheared off, its two bars snapped at different
  heights.
- **Beard and shard:** a long ash beard of staggered locks rests on the breastplate and
  ends above the Fire shard. Each lock narrows in three steps to a curled point, and its
  texture runs in strands down the lock. The shard sits in a gold socket.
- **Shoulders and cape:** sloped two-plate pauldrons, with a yoke filling the gap to the
  collar. The cape covers both shoulders: across the top, down their fronts, and down
  behind the pauldrons into seven unequal panels. Those hug the back, then flare over the
  back tassets to a burnt hem.
- **Curse chain:** one rust-iron curse chain runs from the left shoulder to the right hip,
  hidden under the cape at the back. Its tarnished padlock hangs beside the shard.
- **Guard and arms:** a plough guard with big gauntleted fists on the grip and the blade
  raised up and out. The forearms taper toward the wrists, and each gauntlet cuff flares
  toward the elbow and slims at the wrist, so the fist reads as its own shape. A snug
  shackle closes round the right wrist; from an eye under it, a five-link iron tether hangs
  in a shallow loop behind the grip to the pommel ring.
- **Lower body:** a flared, knee-length coat of six tasset bones. Each tier has a gold
  edge, and the bottom tier has an ember hem. A crimson tabard with a gold crown hangs
  from a gold clasp on the belt.

## Build and review

Requires Python 3, Pillow, NumPy, and Node.js. No package downloads are needed.

```sh
python3 art/pyre_king/build_art.py
python3 art/pyre_king/build_art.py --check
python3 art/pyre_king/render_review.py
python3 art/pyre_king/render_clips.py
python3 art/pyre_king/workshop/serve.py
```

Previews are not in git. On a fresh checkout, run the build first, because the workshop
is served from `.local-previews/pyre-king/`. Then run the two render scripts to regenerate
the review images.

Open <http://127.0.0.1:8410/workshop/>. If already running, rebuild and reload.
The loopback server serves only `.local-previews/pyre-king/` and disables caching.
The workshop offers **Clip** (with speed), **Test sweep**, **Pause / Play**,
**Freeze horizontal** and **High guard**. Flames flicker even in the static guard; Pause freezes both
pose and effects. Fire can be toggled off to inspect the hot blade itself. The test sweep
turns the blade about the middle of the grip and keeps both fists on it with the same
two-bone arm IK as the clips.

`--check` rebuilds in memory, compares every generated candidate and workshop
file byte for byte, validates hierarchy and UVs, and measures transformed
geometry with the workshop's GeckoLib baker. `check_effects.cjs` also verifies
that fire edges remain aligned to world axes across the swing, socket spacing
stays fixed, the frozen sweep is level, the trail fades after motion stops,
the sweep keeps both fists on the grip with rigid forearms, flames root on the blade's
upper edge, and held/thrown sword geometry and UVs match. It writes no files.

Editable sources are `geometry.py`, `rig.py`, `clips.py`, `build_art.py`,
`render_review.py`, `render_clips.py`, `export_scene.cjs`, `check_effects.cjs` and
`workshop/`. All generated previews
remain in the ignored `.local-previews/pyre-king/` directory.

## Candidate and dimensions

- `candidate/pyre_king.geo.json`: **284 cubes / 44 bones**, including the sword.
- `candidate/pyre_king_sword.geo.json`: **14 cubes / 3 bones**, derived from the
  held sword. The preserved `sword_fire` bone is empty in both exports.
- `candidate/pyre_king.png` and `pyre_king_glowmask.png`: 1024 × 512 RGBA atlases.
  Blade edges retain hot color and emission without geometry flames.
- `candidate/bladefire.png`: eight 16 × 32 cutout frames with colors sampled from the
  existing block-fire atlas. Frames 0–3 are tall tongues with white-hot roots; 4–7 are
  low licks with no white core. Every frame's root is rounded, not flat. Interiors are
  opaque; only the perimeter and tips fade.
- `candidate/blockfire.png`: byte-identical copy of the existing Fire atlas;
  its source in `art/fire/blockfire/` is unchanged.
- `candidate/pyre_king.animation.json`: the static guard, plus the clips baked from
  `clips.py` at 20 Hz. The workshop's test sweep is preview code, not a clip.
- `candidate/manifest.json`: dimensions, materials and blade fire socket contract.

Upright, ground to crown is **80 pixels / 5.00 blocks**. In the leaning guard the body
measures **4.913642 blocks**, and with the raised sword, excluding
fire, **5.045612 blocks**. The scale figure is exactly
**1.80 blocks** tall. Source geometry uses 16 model pixels per block, with front −Z and
anatomical right −X; the baker applies GeckoLib 5's X reflection.

| Sword measurement | Model pixels | Blocks |
| --- | ---: | ---: |
| Ring bottom to tip | 72 | 4.50 |
| Blade | 56 | 3.50 |
| Blade root width | 7 | 0.4375 |
| Blade root thickness | 1.6 | 0.10 |
| Leather grip | 11.6 | 0.725 |
| Cross-guard width | 17 | 1.0625 |

Named bone groups:
- six coat flaps;
- nine mantle scraps (seven cape panels and two lapels);
- the tabard and the mantle collar;
- head, mask, crown and beard;
- the chest shard;
- one curse chain;
- limb segments;
- the wrist shackle and the hanging tether.

 `sword` remains a child of
`right_hand`; `pommel_ring` and the empty `sword_fire` remain its children.
Standalone sword geometry has its pivot at the ring bottom and its tip along +Y.

## Clips (first pass)

`clips.py` authors each clip as keyposes on a timeline, with named easing (`hit` for blows,
`snap` for hard stops, `settle` for recoveries). `rig.py` solves each 20 Hz sample into
GeckoLib channels.

**How a pose is solved:**
- Forward kinematics match the workshop's GeckoLib baker exactly. The blade sockets agree
  to four decimals.
- Two-bone IK keeps both fists on the grip; the worst grip gap is 0.01 blocks.
- The elbows bend the way the sculpt bends them, and the feet stay planted.
- A thrown sword leaves the hand and flies free. Meanwhile the short tether is hidden, and
  `effects.js` draws the long chain from the wrist shackle to the pommel ring.

**Clips:**
- `idle`
- `walk` and `run`
- `royal_cut`: a 0.9 s tell, the cut, a 0.45 s tell, the backhand, then the 1.2 s pause.
- `cast_reel`: a 0.8 s throw tell, the sword sticks about 11 blocks out, a 1.5 s wait, a
  0.7 s Reel tell, then the pull-back and the two-handed catch.

The workshop's **Clip** menu plays them, with speed control. `render_clips.py` renders
review strips, including a player-eye view of the Royal Cut, into
`.local-previews/pyre-king/renders/clips/`. With arguments it renders any clip at chosen
times: `render_clips.py OUT CLIP t1 t2 … [--cam yaw,pitch,extent,x,y,z] [--player]`.

**Next:** first, the developer's feedback on how these clips move (speed, stiffness, and
whether the tells read). Then the rest of the kit.

The rest of the kit is still to come:
- Kingsguard Thrust and Turning Cut;
- the Chain Scythe and its release;
- Zip, Hook and Overhead Sentence, Lunge and Drag Down;
- Unbound.

## World-up fire contract

`workshop/effects.js` is shared by WebGL and by the Node scene exporter used by the
offline rasterizer. Every frame it transforms **17 blade sockets**, from Y=16 at the blade
root to Y=72 at the tip in 3.5-pixel steps.

**Licks:** every socket burns, because the developer wants the whole blade on fire. Each
carries a narrow, upright lick of irregular height and width, nudged along the blade so the
row has no rhythm. Licks use frames 4–7, which have no white core.

**Roots:** every flame roots on the blade's uppermost edge at its socket, with its rounded
base sunk just below the edge. On a slanted blade that is the upper edge, on a level blade
the middle of the top face, and on an upright blade the axis. The fire therefore rises
off the blade as one fringe instead of crossing its face in bands, which read as ladder
rungs on a diagonal blade.

**Peaks:** five taller tongues, in `peak_sockets`, sit at irregular positions with the
tallest in the outer half. They use frames 0–3, which have white-hot roots, as do the two
flames at the hilt.

**Shape:** each flame is a shallow group of three upright cutout sheets. Front and back
clear the blade's surface, and a central cross-sheet keeps it visible from the side.

**Motion:** length and width flicker independently, and frames play at eight per second.
The flames' axes stay in world space and never inherit the sword's rotation. Upright fire
also works on the standalone and upside-down planted sword.

`manifest.blade_fire.sockets` contains positions **relative to the sword bone
pivot, before rotation**, in model pixels. Add the king pivot (`manifest.blade_fire.king_pivot`)
(or `[0,0,0]` for the standalone), reflect X, divide by 16, then apply the posed
bone matrix used by this baker. To find a flame's root, take the blade's local X and Z
axes through the same matrix and move from the socket toward whichever side of each is
higher in the world, by up to `manifest.blade_fire.socket_half_extents` (half width along
X, half thickness along Z, model pixels). The X offset is scaled by `root_edge_fraction`,
and each offset fades smoothly to zero as its axis turns level. A future game renderer
should use its own corresponding model-to-world transform, then construct flame sheets
along world `[0,1,0]`. Do not rotate the sheets with that matrix.

The test motion reconstructs previous socket positions at 0.05, 0.10 and 0.15
seconds ago. Only positions moving faster than 1.2 blocks/second emit the
short trail. These prior world positions linger, rise slightly, and shrink and
fade against a 0.18-second lifetime. Startup never samples negative time.
For gameplay, retain actual recent posed socket positions instead of sampling
the test motion. Draw opaque geometry first, then sort flame faces back to
front with alpha blending, depth testing and no depth writes.

## Review images

Each pass's renders are archived in `.local-previews/pyre-king/v1/` through `v9/`. The
current `renders/` folder holds:
- front, three-quarter, side and back;
- the head and grip close-ups;
- the sword alone and the sword planted;
- the player cameras at 8 and 16 blocks;
- the frozen horizontal sweep and the fire close-ups;
- the contact sheet.

The `compare-v9-v10-*` images pair the ninth pass with this one. The archives and
comparisons exist only on the computer that made them; render the ninth pass from an
older commit to recreate `v9/`.

**Pass 10 resolved** the four problems the ninth pass left open:
- the fire's ladder-rung look on a diagonal blade;
- the crowding of fists, cuffs and tether at the grip;
- the blocky beard and the stepped crown points;
- the forearms stretching in the workshop's test sweep.

**Still open:** the developer's review of this pass and of the first clips.

These are workshop and offline observations, not in-game validation. Nothing here is in
the game yet: no Java uses it, and nothing is installed.
