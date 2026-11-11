# Pyre King — eleventh review sculpt

Based on [the approved masked king](concepts/direction-d-mask.png). Passes 1–5 found the
form; passes 6–9 reworked it after the developer's review and four rounds of independent
grading. Pass 10 polished the crown, beard, grip and blade fire and added the first clips.
Pass 11 answers the developer's review of those clips:
- the coat is gone, the legs and arms are heavier, and the pauldrons are smaller;
- walk and run are rebuilt with power and no waddle;
- the edge leads in the Royal Cut, and the throw's wind-up lays the blade back;
- the fire streams behind a swung blade;
- the workshop is laid out for phones.

The developer approved pass 11 on November 9. Pass 12 added two more sword strings, the
Kingsguard Thrust and the Turning Cut, and the developer approved those the same day.
Pass 13 adds the rest of the kit's clips: both Chain Scythes, Zip, Hook, Overhead Sentence,
Lunge, Drag Down, phase two's third Royal Cut, and Unbound. The sculpt is unchanged, but
the model gains six empty marker bones for Unbound's chains.

Pass 14 (November 11) adds two clips for getting round a pillar, the Haul and the Snare.
The developer asked for the move; they have not yet seen these two clips.

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
- **Shoulders:** two small iron lames with gold lower edges, on their own `right_pauldron` /
  `left_pauldron` bones. Each bone is a child of the torso at the shoulder joint, so the
  plates sit on the chest, not on the arm. The upper lame slopes 16° and the lower 14°, and
  the arm comes out from under the lower lame. The sword-arm (right) lames sit slightly
  further out, because that arm angles away from the body. Front width at shoulder height
  is about 2.42 blocks.
- **Cape:** the crimson mantle lies on the inner part of each shoulder and runs into seven
  unequal panels on the back. Their burnt hem ends on the lower thigh, at 23.5–27.5 px,
  well above the knee, and rises toward the sides. The outer panels draw in, so no cloth
  frames the hips from the front.
- **Curse chain:** one rust-iron curse chain runs from the left shoulder to the right hip,
  riding over the fauld at the hip and hidden under the cape at the back. Its tarnished
  padlock hangs beside the shard.
- **Arms and guard:** heavy upper arms (7.6 × 7.2 px) and vambraces (7.0 tapering to 6.2)
  end in flared gauntlet cuffs that slim to the wrist, so each fist reads as its own shape.
  He holds a plough guard, with both fists on the grip and the blade raised up and out. A
  snug shackle closes round the right wrist; from an eye under it, a five-link iron tether
  hangs in a shallow loop behind the grip to the pommel ring.
- **Lower body:** there is no coat. A close fauld of two lames hugs the hips under the
  belt: a gold-edged upper lame and an ember-hemmed lower lame, ending at the top of the
  thighs. The crimson tabard with its gold crown hangs from the belt clasp to mid-thigh.
- **Legs:** straight, heavy columns.
  - Seen from the front, each knee sits on the line from hip to ankle, so thigh and shin
    form one line. Both legs are the same length (1.883 blocks).
  - The cuisse is 12 px wide and 10.4 px deep, full width right up under the fauld, with a
    raised front plate. Above the lower thigh its front and back corners step in, so a
    raised thigh slides under the fauld.
  - The knee cop is 10.6 px wide, with a boss and a thin gold lower edge. It has no side
    wing and stays within the leg's line.
  - The greaves taper gently: 10.4 px under the knee, 9.6, then 8.6 at the ankle, with the
    calf behind.
  - The sabatons are 11.2 × 13 px and 7.4 px tall, with a thin gold toe edge.
  - The lower fauld lame is 28.4 px wide and sits at 30.4–32.8 px, so it covers the thigh
    tops.

## Build and review

Requires Python 3, Pillow, NumPy, and Node.js. No package downloads are needed. On the
developer's Windows PC, `python3` is a broken Store alias, so use `py -3` there.

```sh
python3 art/pyre_king/build_art.py
python3 art/pyre_king/build_art.py --install
python3 art/pyre_king/build_art.py --check
python3 art/pyre_king/render_review.py
python3 art/pyre_king/render_clips.py
python3 art/pyre_king/workshop/serve.py
```

`--install` also writes the game's copies under `src/main/resources` and
`PyreKingClips.java`; `--check` verifies those too, so run `--install` after any change.

Previews are not in git. On a fresh checkout, run the build first, because the workshop is
served from `.local-previews/pyre-king/`. Then run the two render scripts to regenerate the
review images. The build takes about 20 s.

### The workshop

Open <http://127.0.0.1:8410/workshop/>. If it is already running, rebuild and reload. The
loopback server serves only `.local-previews/pyre-king/` and disables caching.

The workshop is a static page, so the built `workshop/` folder, with its `candidate/`
files, can also be published as a private claude.ai artifact and opened on a phone. See
[remote preview](../../docs/reference/remote-preview.md).

**Layout:**
- It is phone-first: the 3D view fills the screen, and no control covers it.
- A bottom bar holds Clip, Play/Pause, Speed (1×, ½×, ¼×), a scrubber with the clip clock,
  and a toggle for the sheet.
- The sheet holds:
  - camera presets (¾ view, Front, Side, Back, Head, Player · 8, Player · 16);
  - the Player, Fire, Glow and Clay toggles;
  - Test sweep, Freeze horizontal and High guard;
  - Model and Light;
  - a collapsed section with the texture atlas and rig notes.
- On a portrait phone the sheet sits between the view and the bar, at most 40% of the
  height. On landscape screens and desktop it is a side panel.
- Links to `../renders/` appear only when the page is served locally.

**Gestures:** one finger orbits, two fingers pinch-zoom and pan, and a double tap returns
to the preset. With a mouse, drag orbits, the wheel zooms, and right-drag or Shift-drag
pans. The page itself never scrolls or zooms.

**Camera:**
- Each preset fits what is on screen: the guard, the sweep, or a looping clip over its
  whole cycle.
- The one-shot clips (the attacks) use a follow camera that frames the pose and the next 0.3 s.
  It also keeps Unbound's six chains in view.
  It keeps the blade and the thrown sword in view. The king can shrink to 28% of the view
  height for this, or 22% while the sword is thrown.
- Head and the Player cameras follow clips too. Changing clip keeps your angle but
  reframes.

**Other behaviour:**
- **Scale player:** the 1.8-block scale player stands beside the king, never between him
  and the camera, and is hidden during clips.
- **Floor:** walk and run play in place, so the floor scrolls at each clip's
  `ground_speed` and planted feet stay on their tiles. The same speed reaches the fire as
  travel.
- **Links:** state lives in the `#fragment`, for example
  `index.html#clip=royal_cut&t=1.05&view=side`. A fragment with `t` opens paused at that
  time. A local `?query` also works.
- **Drawing:** the page draws only while something plays or changes. The still guard's
  flames flicker while playing, and Pause freezes pose and fire.

### Checks

`--check` rebuilds in memory and compares every generated candidate and workshop file byte
for byte. It also validates the hierarchy and UVs, measures transformed geometry with the
workshop's GeckoLib baker, and asserts the structural contract:
- no coat flaps;
- the cape's hem and the tabard's tip end at least 5 px above the knee;
- no deep gold lids on the shins;
- one curse chain;
- an empty `sword_fire` bone.

`check_effects.cjs` verifies the fire:
- Still flames are upright and axis-aligned, with no wake.
- Swung flames trail their travel. They are never shorter than when still, never point
  down, never hang below the blade and never fold.
- The outer blade streams more than the hilt, and the wake appears only when fast.
- Teleports and spins are never traced, and travel leans the flames calmly.
- Nothing pops, every face is well formed, and the output is deterministic.
- The socket spacing, the frozen level sweep, the sweep's fist IK, upper-edge roots and
  matching held/thrown sword geometry are also checked.

The build writes no files when checking.

`py -3 -c "import clips; clips.check_edges()"`, run from `art/pyre_king`, re-verifies the
attack clips. It checks the 20 Hz samples, the half-samples and the baked clip played at
60 fps:
- the edge leads in each blow;
- wrist twist and bend;
- arm turn per sample (90° for a gripping arm, 150° for one swinging free);
- the fists stay on the grip;
- the in-between frames follow the solved pose;
- the held sword stays above the floor.

It returns a `problems` list. All thirteen attack clips are checked by default.

Editable sources are `geometry.py`, `rig.py`, `clips.py`, `build_art.py`,
`render_review.py`, `render_clips.py`, `export_scene.cjs`, `check_effects.cjs` and
`workshop/`. All generated previews remain in the ignored `.local-previews/pyre-king/`
directory.

## Candidate and dimensions

- `candidate/pyre_king.geo.json`: **266 cubes / 46 bones**, including the sword.
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
  `clips.py`. The workshop's test sweep is preview code, not a clip.
- `candidate/manifest.json`: dimensions, materials, the clips with their `ground_speed` and
  `end_turn_degrees` and `end_offset_blocks`, and the blade fire contract.

Upright, ground to crown is **80 pixels / 5.00 blocks**. In the leaning guard the body
measures **4.913642 blocks**, and with the raised sword, excluding fire, **5.045612 blocks**.
The scale figure is exactly **1.80 blocks** tall. Source geometry uses 16 model pixels per
block, with front −Z and anatomical right −X; the baker applies GeckoLib 5's X reflection.

| Sword measurement | Model pixels | Blocks |
| --- | ---: | ---: |
| Ring bottom to tip | 72 | 4.50 |
| Blade | 56 | 3.50 |
| Blade root width | 7 | 0.4375 |
| Blade root thickness | 1.6 | 0.10 |
| Leather grip | 11.6 | 0.725 |
| Cross-guard width | 17 | 1.0625 |

Named bone groups:
- nine mantle scraps (seven cape panels and two lapels);
- two shoulder plates (`right_pauldron`, `left_pauldron`), carrying both lames, their trims,
  the crimson shoulder cloth and the drape behind the arm;
- the tabard and the mantle collar;
- head, mask, crown and beard;
- the chest shard;
- one curse chain;
- limb segments;
- the wrist shackle and the hanging tether;
- six empty markers on the chest, `unbound_chain_1` to `unbound_chain_6`. A clip moves a
  marker to where a loose chain's far end is. At rest they sit on the chest chain, and
  nothing is drawn. `effects.js` draws a burning chain from the chest to every marker that
  is more than 0.6 blocks from its rest place:
  - a chain that is being swung bows: its far end trails behind the rest, as on a
    pinwheel. The chain has weight. It takes a moment to bend when a swing starts, and
    when its far end stops, the rest swings on past, bows the other way a little and
    settles. The bow is the far end's sideways velocity over the last second, passed
    through a slightly springy filter (0.55 s period), times 0.09 s. It is largest two
    thirds of the way along and reaches at most 22% of the chain's length;
  - every third link glows hot, with the chest shard's texture;
  - the far end carries a barbed hook 1.25 blocks long. It lies flat, curves to the left
    (the way the chains sweep), and its barb points back toward him;
  - a low flame burns every 0.6 blocks or so, with a tall one on the hook. A flame stands
    upright where the chain is still. Where the chain moves, it lies back along the way it
    came, by up to 1.1 blocks, and loses up to 45% of its rise.

`sword` remains a child of `right_hand`; `pommel_ring` and the empty `sword_fire` remain
its children. Standalone sword geometry has its pivot at the ring bottom and its tip
along +Y.

Hips and ankles are as in pass 10. The knees sit on the hip–ankle line seen from the
front (x ±10.29), at z −4.5 (right) and −1.47 (left), so both legs are 1.883 blocks long.
Each cube's texture noise is seeded by its position in the cube list, so adding or removing
cubes upstream of a part changes that part's texture.

## Clips

`clips.py` authors each clip as keyposes on a timeline, with named easing (`hit` for blows,
`snap` for hard stops, `settle` for recoveries). `rig.py` solves each sample into GeckoLib
channels.

**How a pose is solved:**
- Forward kinematics match the workshop's GeckoLib baker exactly.
- Two-bone IK keeps both fists on the grip. Played back at 60 fps, the worst gap is under
  0.01 blocks.
- The legs use IK. By default each knee bends the way the sculpted stance bends it, turned
  with the pelvis, so planted legs stay straight columns. Gaits pass `knee_hint` and
  `foot_r` (a foot rolling heel to toe).
- Each shoulder plate takes a share k of its upper arm's turn against the chest, about the
  same axis. k is the smallest share that keeps the arm within the plate's room (14° more
  lift, 7° more outward swing, 47° more turn), smoothly clamped between 0.2 and 0.9
  (`rig.PAULDRON_FOLLOW` / `PAULDRON_ROOM`). Strides stay inside that room, so the plates
  stay steady with the chest; big raises carry them. The workshop's test sweep doesn't
  drive the plates.
- A free fist can keep a `wrist` turn instead of snapping straight to its forearm.
- A clip can pose its arms as hinges (`hinge: True`). Each arm bone's roll then follows the
  plane the elbow bends in. The default takes the shortest turn from the sculpt, which
  flips a bone's roll when an arm is flung up or folded tight. Both agree in the guard.
  The chain moves use the hinge; the sword strings keep the default, so their bakes are
  unchanged.
- A clip can say where a free arm's elbow points (`free_elbow`). The chain moves point it
  back and out. Near a grip change it blends into the planned elbow.
- `chain_ends` sends marker bones to world points.
- A thrown sword leaves the hand and flies free. Meanwhile the short tether is hidden, and
  `effects.js` draws the long chain from the wrist shackle to the pommel ring.

**How a clip is baked:**
- Keys are every 0.05 s.
- Extra 0.01 s keys are added within 0.1 s of every grip change, and over any interval
  whose in-between frames would miss the solved pose. Where 0.01 s still misses, 0.005 s
  keys are used; those key names have three decimals.
- Every rotation key is written as the Euler equivalent nearest the key before it, so no
  in-between takes the long way round.
- `rig.player(clip)` plays a baked clip exactly as the workshop does, for checks.

**Clips:**
- `idle`: a coiled guard with breathing and a drifting blade point.
- `walk`: 3.0 blocks/s on a 1.0 s loop.
  - The feet track under the hips and the knees drive straight ahead.
  - Each step strikes the heel, rolls flat, and pushes off a nearly straight trailing leg.
  - The pelvis drops into each footfall, with almost no side-to-side sway. The chest
    counter-turns ±12° about its own spine, and the head holds its gaze.
  - The blade is carried low and out to his right. It takes only 30% of the chest's turn,
    so its fire stays off his face.
- `run`: 4.5 blocks/s on a 0.9 s loop, the roadmap's phase-one pace.
  - He spends about 0.17 s in the air per 2-block step, and the knee drives to about 53°.
  - The lean is taken from the hips, the chest counter-turns ±14°, and the blade is cocked
    high to his right.
- `royal_cut`: a 0.9 s tell, the cut, a 0.45 s tell, the backhand, then the 1.2 s pause.
  - An edge leads both blows: the cut with one edge, the backhand with the other.
  - `easy_wrists` plans the blade's roll and both elbows over the whole clip. It keeps the
    wrists comfortable and the arms out of the trunk, neck, head and beard. It also eases
    each fist into and out of its grip over 0.15 s.
- `cast_reel`: an overhand throw, then the wait and the Reel.
  - Research found the historical sword throw is javelin-like (Fiore dei Liberi's
    "throwing guard"). The wind-up follows an overhand knife or axe throw instead, which
    reads better and lays the blade back.
  - 0–0.55 s: he steps his right foot back, coils away, leans back, and lays the blade
    back over his right shoulder with the point up, behind him and turned out. The left
    fist reaches toward the target.
  - From 0.68 s his hips open and the left foot strides in; from 0.74 s the arm whips
    over the shoulder.
  - Release is at 0.87 s. The sword flies point-first along an arc and sticks about 11.5
    blocks out at 1.22 s.
  - The 1.5 s wait, the 0.7 s Reel tell, the pull-back and the two-handed catch follow.
- `kingsguard_thrust`: a longsword thrust from the high guard by the head (Ochs), point
  first. Nothing is swung, so no edge leads.
  - 0–0.35 s: he coils hard to his right, chest turned about 56°, the right foot stepping
    back. The fists come up beside his right shoulder, with the point on the face of a
    player 7.5 blocks out. The aim freezes at the snap (0.35 s); he coils tighter until
    0.8 s.
  - 0.8–0.93 s: the left foot lunges in about a block, and the hips drive and the chest
    unwinds. Both arms drive the point down onto a player's chest (`clips.THRUST_AIM`).
    The arms ease through the drive while the body hits; that keeps both wrists
    comfortable as the fists cross his body. The fists end low and inside, as in a real
    two-handed thrust, because a blade held level out in front strains this grip. He
    overshoots slightly and settles.
  - 1.2–2.4 s: the pause, the point dropped ahead of him. Then he steps back to his guard.
  - The clip covers about 0.8 blocks of the 2-block step; the game moves him the rest.
- `turning_cut`: he spins left and cuts the half behind him.
  - 0–0.25 s: he glances back over his left shoulder. The blade rises high on his right,
    and his feet gather in under him. Snap at 0.25 s.
  - 0.4–0.8 s: his hips spin him 185° in one smooth push, fastest at about 700°/s. His
    head leads and the blade is carried high. His feet pivot on their balls about his
    centre, each lifting a little in turn.
  - 0.68–0.85 s: the edge leads as the cut whips flat and low through the half behind him.
    The fists are below his belt, so the blade sweeps a player's body height at reach 5.
    The follow-through rises away to his left.
  - 1.2–2.2 s: the pause, in a low guard with the point on whoever was behind him. Then he
    settles into his guard, facing back the way he came.
  - The clip is authored in his own frame and turned by a root yaw about his origin. A
    180° turn about the middle of his stance swaps his feet's places exactly. The manifest
    gives the turn as `end_turn_degrees` (180, counterclockwise seen from above). The game
    turns his body yaw by that much on the clip's last frame, as the next clip starts in
    his own frame.
- Both new clips were posed with searches for comfortable wrists.
  - The planner now also charges an elbow for swinging more than 60° round its circle in
    one sample; the Turning Cut's plan had swung one 150°.
  - No earlier clip swings more than 40°, so their bakes are byte-identical to before.

**Chain moves and the overhead cut (pass 13).** Every chain move ends on the Reel's catch
unless noted: the sword whips in, the right fist takes it, and the left follows 0.1 s later.
- While the sword is swung on its chain, `effects.js` bows that chain too, with the same
  springy filter as Unbound's chains but as an even arc of at most 12% of its length. The
  Scythes' sword trails 25° behind the chain's line at full swing (`clips.SCYTHE_LAG`), so
  it carries on from the bowed chain's end.
- `chain_scythe_low`: the sword's point sweeps a 14-block circle at ankle height.
  - 0–0.45 s: he casts the sword out low to his right. It skids to full chain, and the
    chain snaps taut at 0.45 s.
  - 0.45–0.7 s: he sinks into a deep crouch and winds up against the chain.
  - 0.7–1.2 s: he spins once to his left like a hammer thrower, dragging the blade flat on
    the floor. The clang is at 0.9 s, mid-spin. He ends facing the way he started.
  - 1.2–1.5 s: he hauls the chain, and the sword whips round his right side into his hand.
- `chain_scythe_high`: the same reach at a player's head height.
  - 0–0.3 s: he swings the sword up behind his right shoulder and lets it go.
  - 0.3–0.7 s: it whirls over his head like a lasso as the chain runs out.
  - 0.7–1.1 s: one turn at full chain, the clang at 0.9 s. The pommel rides 2.6 blocks up
    and the point hangs to about 1.5.
  - 1.1–1.5 s: he hauls it down and in, and catches it.
- `zip`: starts from the throw's unarmed wait, with the sword stuck 11.5 blocks out.
  - 0–0.62 s: he takes the chain in both hands, sinks back against it, and is hauled off
    his feet.
  - 0.62–1.03 s: he flies to the sword and lands 10.4 blocks from where he started.
  - 1.03–1.2 s: a haul on the chain tears the sword out of the floor into his hand.
  - 1.2–1.62 s: one spin to his left with the blade flat, an edge leading; the cut lands
    at 1.4 s. He finishes across his front as the Turning Cut does, then holds a low guard
    until 2.9 s.
- `lunge`:
  - 0–0.5 s: he drops low and winds the chain round his right forearm in two turns, his
    left hand winding with it. The aim freezes at 0.45 s.
  - 0.62 s: a low sling sends the sword short; it sticks 8 blocks out at 0.8 s.
  - 0.9–1.33 s: the chain hauls him 6.9 blocks after it.
  - 1.33–1.92 s: he tears the sword free and spins as in the Zip. The cut lands at 1.7 s,
    1.25 s after the freeze.
- `haul`: the Lunge with nothing wasted and nearly twice the reach. It has no blow.
  - 0–0.3 s: he drops low with the blade back at his right hip, with no winding of the
    chain, and slings it flat. The aim freezes at 0.2 s.
  - 0.45 s: the sword sticks 14 blocks out.
  - 0.5–0.8 s: the chain hauls him 12.9 blocks after it.
  - 0.8–0.97 s: he lands in a crouch and tears the sword free into his right hand.
  - Left alone he rises into his guard by 1.5 s. In the fight the Snare takes over at 1.0 s.
- `snare`: the Hook made in a hurry, straight after a Haul.
  - 0–0.4 s: no javelin draw. He drops the blade back to his right hip and slings it flat
    and low, the Haul's own sling. The aim freezes at 0.25 s.
  - 0.4–0.7 s: it flies 27 blocks.
  - 0.7–1.7 s: the Hook's three hauls, hand over hand.
  - 1.9 s: the last flick brings it up into his hand. He is in his guard at 2.7 s.
- `hook`:
  - 0–1.0 s: he draws the sword back beside his head like a javelin, the point on the
    target and his left hand marking the line, then hurls it flat. This is the historical
    throw (Fiore's "throwing guard") that the Cast does not use.
  - 1.0–1.3 s: it flies 27 blocks.
  - 1.3–2.3 s: he drags it back hand over hand, in three hauls.
  - 2.5 s: a last flick brings it up into his hand.
- `overhead_sentence`: the cut that follows a Hook.
  - 0–0.7 s: he raises the sword high over his head, the blade pointing up and back.
  - 0.7–0.84 s: his left foot lunges in and the blade comes straight down the line in
    front of him. The fists lead and the blade whips down last, an edge leading. The point
    stops just above the floor about 5.3 blocks out.
  - He holds it there until 2.2 s.
- `royal_cut_three`: phase two's Royal Cut. It is the Royal Cut until 2.2 s. Then the blade
  circles up over his right shoulder and comes down in the same overhead cut at 2.62–2.76 s.
  The Royal Cut itself is unchanged.
- `drag_down`:
  - 0–0.6 s: he looks up, points at the target with his left hand, and swings the sword
    back low behind his right hip.
  - 0.6–0.8 s: a rising sidearm sling sends it up at a player 8.5 blocks up and 7.5 out.
  - 1.0–1.3 s: he takes the chain in both hands and hauls down with his whole weight. The
    sword drives into the floor about 4 blocks in front of him.
  - 1.5 s: it whips back into his hand. He is back in his guard at 2.4 s, because a sword
    string follows.
- `unbound`: the phase change, 4.3 s.
  - 0–0.64 s: he raises the sword and cuts down into the floor with the overhead cut.
  - 0.64–0.95 s: he leans on the hilt and drives the blade in at an angle, kneeling behind
    it, then lets go. His wrist chain still runs to the pommel.
  - 1.2 s: he throws his arms and head back, and six burning chains burst from his chest,
    each ending in a barbed hook. The chest chain is hidden while they are out.
  - The chains whirl to his left for as long as they are out, about 330° in all
    (`clips.UNBOUND_SWING` is their turning speed), so they are never straight for long.
  - 1.2–1.5 s: they run out to 12 blocks along spirals.
  - 1.65–1.9 s: they skim at ankle height, turning fastest (260°/s) at the clang, 1.75 s.
  - 1.9–2.6 s: they keep swinging round him, slowing and rising to 1.5 blocks.
  - About 2.7 s: the hooks catch, which checks the swing and yanks his chest round.
  - 2.75–3.3 s: he hauls them in, hunching over the sword. They whip round faster as
    they shorten.
  - 3.4 s: he takes the hilt, tears the blade free and rises into his guard.

**What the game must do for these clips:**
- `zip`, `lunge` and `haul` move him. The manifest's `end_offset_blocks` is where he stands
  at the end, in the frame he started in: 10.4, 6.9 and 12.9 blocks forward. The game moves him there on
  the clip's last frame. The flight is authored for one distance, so the game will have to
  stretch it to the real one.
- The low Scythe, Zip and Lunge spin him a full turn, so their pelvis channel ends at 360°.
  As with the Turning Cut, the next clip must start with no blend.
- Throw distances and targets are fixed in the clips (27 blocks for the Hook and the Snare,
  8.5 up for Drag Down). The game will have to aim the real sword.
- The Snare is thrown at a player who may be anywhere round him when the Haul lands. The
  clip throws straight ahead; the game turns him as it plays.
- Hit zones, ember rings and lines, floor sparks and fire walls are game effects. The
  clips only show the body, the sword and the chains.

**What the build gives the game for that.** `--install` also writes
`data/elementalwands/pyre_king_tracks.json`: for every attack clip, where his wrist
shackle, the sword's pommel and its tip are, 120 times a second, in the frame he started the
clip in (blocks: right, up, forward). Zip, Lunge and Haul add how far the chain has hauled
him (`clips.TRAVEL`), and Unbound adds its six chains' ends. The fight sweeps these for hits
and aims thrown swords from them; see
[Pyre King](../../docs/reference/pyre-king.md#the-fight).

Walk and run are in-place loops. In the manifest, each clip's `ground_speed` (walk 3.0,
run 4.5, the others 0) is the speed the game must move him at, or else it must scale
playback by actual speed ÷ `ground_speed`. Phase two's 5.2 blocks/s run plays at about
1.16×. `clips.HITCH` would compensate unequal leg lengths with a pelvis roll; with the
legs now equal it is 0.

`render_clips.py` renders review strips into `.local-previews/pyre-king/renders/clips/`:
- royal-cut, royal-cut-edge (front and top at mid-blow), royal-cut-player-view and
  royal-cut-player-blows;
- cast-reel, cast-reel-throw and cast-reel-release-catch, including in-between frames;
- kingsguard-thrust (side and three-quarter) and kingsguard-thrust-player;
- turning-cut (from behind and above) and turning-cut-player, from the eye of a player
  4.5 blocks behind him;
- chain-scythe-low and chain-scythe-high, each from far enough to see the chain, then close;
- zip, lunge, hook, overhead-sentence (side and player's eye), royal-cut-three, drag-down;
- unbound, wide and close;
- walk-run, from the side and front.

Frames off the 20 Hz grid are labelled "(in-between)". With arguments it renders any clip
at chosen times: `render_clips.py OUT CLIP t1 t2 … [--cam yaw,pitch,extent,x,y,z]
[--player]`. Walk and run pass their travel to the fire automatically.

Every move in the proposed kit now has a clip. Not yet animated: a death, a hit reaction,
phase two's Double Cast as its own clip, and the mask cracking away at the phase change.

## Blade fire contract

`workshop/effects.js` is shared by WebGL and by the Node scene exporter used by the offline
rasterizer. Every frame it transforms **17 blade sockets**, from Y=16 at the blade root to
Y=72 at the tip in 3.5-pixel steps.

### Still fire

When the blade is still or moving slowly, the fire is exactly the approved world-up fire:
- **Licks:** every socket burns, because the developer wants the whole blade on fire. Each
  carries a narrow, upright lick of irregular height and width, nudged along the blade so
  the row has no rhythm. Licks use frames 4–7, which have no white core.
- **Roots:** every flame roots on the blade's uppermost edge at its socket, with its
  rounded base sunk just below the edge. The fire rises off the blade as one fringe
  instead of crossing its face in bands.
- **Peaks:** five taller tongues, in `peak_sockets`, sit at irregular positions with the
  tallest in the outer half. They use frames 0–3, which have white-hot roots, as do the
  two flames at the hilt.
- **Shape and motion:** each flame is three upright cutout sheets. Length and width
  flicker independently, and frames play at eight per second. The flames' axes stay in
  world space and never inherit the sword's rotation.

### Swing response

When the blade moves, the fire behaves like a burning blade whipped through the air.
`manifest.blade_fire.swing` holds every number and formula, so an in-game renderer can
reproduce it.

- **History:** the clip is re-posed at 8 earlier moments, 0.015 s apart. Only the sword's
  ancestor bones are posed. `opts.travel` (the body's world velocity) shifts older
  samples, so a walking or running king's fire leans slightly. A slice faster than 400
  blocks/s counts as a jump, such as a parent switch, and is never traced.
- **Flames:** each flame lies back along the path its root just travelled.
  - It stretches toward `height*1.3 + 0.25` blocks, keeps 60% of its rise and curls up.
  - It is never shorter than when still, never points down and never folds past 70°.
  - Lean ramps over 2–20 blocks/s and stream over 8–60 blocks/s.
- **Wake:** one swept sheet joins the roots across the arc the blade covered.
  - It fades in from the blade, dims with age over about 0.12 s, and fades where it would
    hang below the blade.
  - It ramps in over 20–90 blocks/s, so only real blows draw it.
  - Each quad is a normal tint plus an additive glow.
- **Settling:** everything settles upright soon after the blade stops, without popping.

**Face contract:** every flame face has `pts`, `n`, `uvs`, `alpha` in (0,1] and `trail`
(true on the wake).
- Optional `alphas` are per-vertex opacity used instead of `alpha`.
- Faces with `blend: 'add'` are drawn after all other flames, back to front, additively,
  with depth testing and no depth writes.
- Draw opaque geometry first, then sort normal flame faces back to front with alpha
  blending, depth testing and no depth writes.
- `scene(model, manifest, t, motion, planted, clip, opts)` also returns per-flame `fire`
  records for checks.

**Socket space:** `manifest.blade_fire.sockets` contains positions **relative to the sword
bone pivot, before rotation**, in model pixels. To place them:
1. Add the king pivot (`manifest.blade_fire.king_pivot`), or `[0,0,0]` for the standalone.
2. Reflect X and divide by 16.
3. Apply the posed bone matrix.

To find a flame's root, move from the socket toward whichever side of the blade's local X
and Z is higher in the world, by up to `socket_half_extents`. For gameplay, record the real
recent root positions and blade axis every render frame instead of re-posing the clip.

## Review images

Each pass's renders are archived in `.local-previews/pyre-king/v1/` through `v10/`. The
current `renders/` folder holds:
- front, three-quarter, side and back;
- the head and grip close-ups;
- the sword alone and the sword planted;
- the player cameras at 8 and 16 blocks;
- the frozen horizontal sweep and the fire close-ups;
- the contact sheet.

The `compare-v10-v11-*` images pair the tenth pass with this one. The archives and
comparisons exist only on the computer that made them.

**Pass 11** came out of the developer's review, one adversarial review round and a fix
round. **Known limits:**
- The right wrist bends about 94° at one fast sample of the cut and of the throw's whip
  (`check_edges` flags it). Going lower needs the cocked and whip poses reworked.
- During the attack tells, his chest turns 25–29° against his hips, and the cape grazes
  the top of the left thigh. The tabard can pass through a thigh in the attack clips,
  because those clips don't push cloth off the legs.
- In the run, the tabard's belt band meets a raised thigh by about 0.1 px, and a raised
  thigh shows up to about 1.8 px past the fauld's side.
- At a few fast attack frames the arm covers up to 0.56 px of a lower shoulder lame's gold
  edge, and over a run stride the right plate still turns about 5°.

**Pass 12 known limits:**
- In the Turning Cut's downswing (0.60–0.65 s) the right elbow swings about 100° round its
  circle while his body spins. At the one 60 fps frame at 0.633 s, the baked left fist
  sits 0.013 blocks (0.2 px) off the grip, over the 0.01 limit (`check_edges` flags it).
  Between frames, at 0.6375 s, it reaches 0.03. Keying finer than the bake's 0.005 s, or
  a downswing that keeps the elbow on one side, would fix it.
- The thrust's drive (0.82–0.9 s) and the Turning Cut's glance (0.15–0.23 s) bend or twist
  a wrist up to 75–80°, under the 90° limit.
- Neither new clip pushes cloth off the legs, and their cape and tabard clearances were
  not measured.
- The thrust ends about 6 blocks out in the clip; the game's step carries it the rest of
  the 8-block line.

**Pass 13 known limits:**
- These nine clips were checked with `check_edges` and looked at as still frames. The
  developer watched them in the workshop and approved them on November 10.
- The Overhead Sentence's point stops 0.14 blocks above the floor instead of biting
  it, and the third Royal Cut's does the same.
- A stuck sword is buried to the hilt, as in the throw. So the Zip and the Lunge tear it
  out with the chain instead of gripping it in the floor.
- The rig's grip cannot be reversed, so Unbound drives the blade in at about 27° from the
  floor instead of plunging it straight down.
- The six chains, their fire and their hooks are drawn by the workshop from the marker
  bones, like the blade fire; the game will have to draw them too. The chains are straight
  links with a little sag and the swing's bow, and they pass through his arms and through
  each other. Their
  fire follows the chain's motion over the last 0.03 s only, so it has no wake.
- Free arms swing fast in places: up to 136° in one 0.05 s sample (the high Scythe, the
  Zip and the Lunge).
- The third Royal Cut inherits the Royal Cut's wrist limit.
- None of these clips pushes cloth off the legs.
- Unbound's chains now spiral in as he hauls them. The proposed fight has them reel in
  along straight ember lines that become fire walls, so one of the two will have to change.

**Still open:** the phone layout was tested only in desktop browser emulation, not on a
real phone.

These are workshop and offline observations, not in-game validation. Since November 10
the candidate is also installed in the game as a summonable body with no fight; see
[Pyre King](../../docs/reference/pyre-king.md). The game draws his fire and chains with
its own copy of `effects.js` (`PyreKingEffectsLayer`), so a change to either must be made
in both.
