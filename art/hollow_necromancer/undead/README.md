# Hollow undead art preview

Run `python3 art/hollow_necromancer/undead/build_preview.py` to export the
three models, pixel textures, glow masks and animations into `candidate/`, plus
`.local-previews/undead/index.html`. Serve through the existing Soul Bolt server
(`python3 art/hollow_necromancer/soul_bolt/serve.py`, port 8351) and open
`http://127.0.0.1:8351/.local-previews/undead/`. The viewer reuses the Grasping Hands
WebGL rendering conventions.

The crawler study has a human-proportioned rib cage, stepped cranial vault,
recessed eye sockets and a tapered jaw. There are no legs: the pelvis trails
along the ground. Slender upper arms and paired forearm bones use a three-dimensional
two-bone solve with an outward elbow pole. Planted hands stay fixed in world
space while the torso advances; the returning hand lifts and eases into its
next contact. The crawl was first authored as a four-second cycle of two slow pulls.
It is now a lunging scramble: each claw plants 13 pixels ahead and hauls the chest up
and forward, covering 20 pixels in 0.83 seconds (1.5 blocks/s, about chase speed).
The camera follows
the body and the dragging portion loops after emergence.
Drag to orbit, scroll to zoom, and use the timeline or pose buttons to inspect it.
The preview is an approximate art reference, not a native gameplay test.

The requested family consists of a common crawler, a less common skeletal archer
and a rare hunched brute, naturally spawning throughout the Overworld at night.
Natural creatures will burn in daylight and drop loot; Necromancer-bound copies
will keep sunlight immunity, no loot, and encounter cleanup. Those runtime
changes are not implemented by this preview exporter.


## Archer and brute studies

`upright.py` owns the Risen Archer and Hunched Brute. Both reuse the approved
crawler skull geometry and copy its actual face texture pixels into their atlases.
The archer has a torn burial hood, quiver, wooden bow, animated bowstring and arrow.
The brute has a wider hunched rib cage, asymmetric arms, and old iron bindings.

`upright.py` builds their geometry and walks; their idle, rise, attack and death
clips are authored in `archer.py` and `brute.py` (see Reworked clips below). Walks use
planted contacts and sampled three-dimensional two-bone arm/leg poses.
Walks are authored at about chase speed on bent knees: the hips ride lowest as each
foot lands and the upper body leans in about the hips. Each arm swings forward as the
opposite foot lands. The archer covers 22 pixels in 0.77 seconds (1.8 blocks/s). The
brute covers 24 pixels in 0.9 seconds (1.67 blocks/s) and carries its maul level with
a bent elbow, taking a lower, dragging step on the club side.
The creature buttons retain the crawler for comparison; Compare all shows the three
creatures at one shared scale.


## Articulated hands and weapon contacts

All four fingers now have three joint bones; thumbs have two. The crawler
staggers finger curls during its planted pull and opens the fingers during the
lifted reach. The brute's free hand flexes independently; the weapon hand keeps
its fingers wrapped around a wood-and-iron club with a small grip-tightening
motion. Its club follows the wrist through windup, strike, and recovery.

The archer pulls its arrow from the quiver, extends the bow arm, anchors the drawing
hand beside the jaw with the elbow forward of the shoulder, and releases. The bow
handle crosses the curled palm and the bow follows the bow hand's wrist. The nock and
the bowstring share the drawing-hand contact; the released arrow continues from that
point. Hands close-up follows the hand positions for detailed review.

Archer and brute hands are built per side (thumb and index outermost, pinky
toward the body) so neither hand is mirrored. Relaxed hands turn their palms
toward the thighs; the brute's club head extends past its thumb. The club fist
stays in line with the forearm and angles the club only by wrist tilt (at most
45°). The club is a long spiked maul: wrapped grip, banded shaft, and an
iron-studded head with face and stepped diagonal spikes. The strike hand
travels an arc around the shoulder: cocked high with the club hanging behind
the back, then accelerating down while the wrist whips late, passing level in
front at the hit and burying its head in the ground. The pelvis and thighs stay joined when
the torso shifts. The bow palm
wraps the handle facing forward with its thumb above the grip. The
drawing wrist rolls into line with its forearm; its position is solved around
the unchanged string contact so correcting palm orientation does not detach
the arrow nock. Only the archer uses these wrist corrections.

The archer's bow elbow uses a downward/outward bend direction throughout the
cycle. The bow forearm and drawing upper arm use angle solutions that stay stable
through their reach planes. Both arms and wrists have continuous rotation keys
across the full cycle, including full draw and release.


## Game clip set

All three creatures export the same five clips, mapped onto one GeckoLib
controller: `idle` (loop), `walk` (loop; the crawler's crawl), `rise` (hold),
`attack` (once) and `death` (hold). The crawler's idle and crawl and both walks are
the approved studies; the rest were reworked (below). The crawler's hands are
mirrored per side like the archer's and brute's.

Walk and crawl clips still carry root motion for the preview; the runtime
install strips it so the game's movement drives travel. Hurts use the standard
red flash rather than a separate clip.


## Reworked clips

Reworked on the night of 22 October for weight, anticipation and follow-through:
heavy bodies move in few, slow beats with holds; only the blows are fast. Each clip
keeps its length and every server moment (hit and release frames, the brute's commit,
the crawler's hop, rise and death lengths). The preview's yellow banner and pink
"Reworked tonight" badges mark them; each has a note with its beats and the server
moments in seconds. `TONIGHT` in `build_preview.py` lists them.

- Brute attack (4.6 s): grips and sinks, then heaves the maul up and over in one long
  lift (0.3–1.2 s) and holds it high, breathing, while it tracks (to 1.5 s). It steps in
  on its right foot (1.58–1.95 s) as hips, chest, arm and maul uncoil in turn. The blow
  passes the hit point at 2.15 s and buries the head in the ground at about 2.22 s with a
  shudder. It stays hunched over the maul, braced on its thigh, looks up at 2.6 s, wrenches
  it free at 2.8 s and is back at rest by 3.4 s (the server releases it at 3.5 s).
- Crawler attack (1.4 s): gathers (the left claw steps in), rears with the right claw
  cocked high (0.2–0.47 s), shoves off into the server's hop at 0.45 s and rakes down at
  0.58 s. It crashes at 0.64 s and drags both claws back through the dirt in step with
  the hop's slide, so they stay put in the world, then walks them back to rest by 1.25 s.
- Archer attack (4.0 s): the skull locks on while the server turns the body side-on and
  the front foot steps out. It pulls an arrow from the quiver (0.3–1.08 s), nocks it at
  1.42 s, raises and draws to the jaw (1.55–2.25 s) and holds with a strained tremor. It
  looses at 2.6 s: the fingers fly open, the hand snaps back past the ear, the string
  snaps and shivers and the bow kicks. It holds the follow-through, then lowers by 3.5 s.
- Rises (crawler 1.9 s, archer and brute 3.6 s): the bodies lie in the grave. A claw
  punches up, flails and grips, and the skull and ribs come out in hauls (the crawler's
  with a strained stall). The archer keeps its bow clear of the dirt; the brute's maul
  head erupts in front and is shouldered. The upright pair kneel and push up off a knee
  into the idle pose; the brute swings the maul down off its shoulder with a thud at 3.32 s.
- Deaths: the crawler scrabbles in its death throes before its elbows buckle (chest down
  at 0.75 s). The archer crumples like a puppet, drops into a heap and folds forward
  while its bow clatters down. The brute drops onto its knees with a thud at 0.45 s,
  sways, and topples forward like a tree (face down at 1.18 s, one bounce).
- Archer and brute idles (4 s loops): breathing and weight shifts, slow head turns, a
  re-grip of the maul, the archer's bow hand tightening and drawing fingers twitching.

`motion.py` holds the timing tools (shaped keys, envelopes, shakes, tremors) and Euler
continuity. `rig.py` turns upright poses into bones: arms and legs are two-bone chains
turning about a shared elbow or knee hinge, blended from the original frames at the
clip ends so crossfades match. `crawler.py`, `archer.py` and `brute.py` author the
clips and hold their review notes. The hit frames are pinned to the approved poses, so
`HollowUndeadClips.java` (sockets, ticks) regenerates unchanged. Rotation keys stay
continuous, and at the ends that crossfade into other clips they use the walk's Euler
forms. `build_preview.py` fails if a reworked clip would end a full turn away, since
GeckoLib's crossfade would spin that part.

The viewer has ½× and ¼× speed and a Player eye camera (eyes 1.62 blocks up where the
attack's target stands; scroll to step closer or back). Links such as
`#brute/attack/2.15/player` open a clip paused at a moment from a chosen camera.


## Runtime install

Run `python3 art/hollow_necromancer/undead/build_runtime.py` after approving a
candidate, and pass `--check` to verify it. It writes the GeckoLib models, clips,
textures and glow masks, plus the generated `HollowUndeadClips.java`. It
pre-mirrors geometry and positions for GeckoLib and proves the result against this
viewer's pose. See [Hollow undead](../../../docs/reference/hollow-undead.md) for
gameplay.
