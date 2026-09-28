# Hollow undead art preview

Run `python3 art/hollow_necromancer/undead/build_preview.py` to export the
three models, pixel textures, glow masks and animations into `candidate/`, plus
`.local-previews/undead/index.html`. Serve through the existing Soul Bolt server
on port 8351. The viewer reuses the Grasping Hands WebGL rendering conventions.

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

Each has Idle, Walk, Rise, and attack clips. Walks use planted contacts and sampled
three-dimensional two-bone arm/leg poses; rise clips stop at their final pose.
Walks are authored at about chase speed on bent knees: the hips ride lowest as each
foot lands and the upper body leans in about the hips. Each arm swings forward as the
opposite foot lands. The archer covers 22 pixels in 0.77 seconds (1.8 blocks/s). The
brute covers 24 pixels in 0.9 seconds (1.67 blocks/s) and carries its maul level with
a bent elbow, taking a lower, dragging step on the club side.
Draw & fire includes a draw, brief aim, release, and lowering the bow. Heavy strike
includes a long windup, downward swing, and recovery. These are art studies, not
server combat timings. The creature buttons retain the crawler for comparison;
Compare all shows the three creatures at one shared scale. The archer and brute
have not been approved or installed in Minecraft.


## Articulated hands and weapon contacts

All four fingers now have three joint bones; thumbs have two. The crawler
staggers finger curls during its planted pull and opens the fingers during the
lifted reach. The brute's free hand flexes independently; the weapon hand keeps
its fingers wrapped around a wood-and-iron club with a small grip-tightening
motion. Its club follows the wrist through windup, strike, and recovery.

The archer loads close to its body, extends the bow arm, anchors the drawing
hand beside the cheek, releases its drawing fingers, moves the elbow outward,
and lowers the bow. The bow handle crosses the curled palm. The nock and the
bowstring share the drawing-hand contact; the released arrow continues from
that point. The elbow pole faces away from the torso, avoiding the previous
inward fold. Hands close-up follows the hand positions for detailed review.

Archer and brute hands are built per side (thumb and index outermost, pinky
toward the body) so neither hand is mirrored. Relaxed hands turn their palms
toward the thighs; the brute's club head extends past its thumb. The club fist
stays in line with the forearm and angles the club only by wrist tilt (at most
45°). The club is a long spiked maul: wrapped grip, banded shaft, and an
iron-studded head with face and stepped diagonal spikes. The strike hand
travels an arc around the shoulder: cocked high with the club hanging behind
the back, then accelerating down while the wrist whips late, landing level in
front in a forward-weighted crouch. The pelvis and thighs stay joined when
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

All three creatures now export the same five clips, ready to map onto
GeckoLib controllers: `idle` (loop), `walk` (loop; the crawler's crawl),
`rise` (hold), `attack` (once) and `death` (hold).

- Crawler: idle breathes, looks around and taps a claw; the crawl is the
  lunging scramble described above; rise bursts both claws out of the ground before the
  body hauls free; the claw lunge rears up and rakes forward, with the hit at
  about 0.58s; death jolts, collapses, rolls and settles. The crawler's hands
  are mirrored per side like the archer and brute.
- Archer and brute: death recoils, buckles onto the knees, then topples face
  down about the hips with the legs lying out straight. The archer drops its
  bow, which tips flat beside the body; the brute's club arm sprawls forward
  with the maul on the ground.
- The archer's drawing elbow bends back while the arm hangs, swings out and
  forward only while lifting and drawing, and returns behind as the bow lowers.

Walk and crawl clips still carry root motion for the preview; the runtime
install strips it so the game's movement drives travel. Hurts use the standard
red flash rather than a separate clip.


## Runtime install

Run `python3 art/hollow_necromancer/undead/build_runtime.py` after approving a
candidate, and pass `--check` to verify it. It writes the GeckoLib models, clips,
textures and glow masks, plus the generated `HollowUndeadClips.java`. It
pre-mirrors geometry and positions for GeckoLib and proves the result against this
viewer's pose. See [Hollow undead](../../../docs/reference/hollow-undead.md) for
gameplay.
