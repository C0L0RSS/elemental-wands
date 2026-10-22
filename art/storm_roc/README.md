# Storm Roc posture study

Review-only GeckoLib candidate for the Storm Roc. The current silhouette target is
`concepts/body-postures.png`: the hunched top figure and horizontal flying bottom
figure. The face and feather sheet (`concepts/face-and-feathers.png`) and body
turnaround (`concepts/body-turnaround.png`) still own the head design, palette and
wing pattern. The candidate builder remains review-only. The study is no longer the in-game
look: the runtime exporter below puts the block model from `art/storm_roc_mod/` on the
study's skeleton and combat motion (see the
[summit reference](../../docs/reference/storm-summit.md#authoring)).

## Build and review

Python 3, Pillow, NumPy and Node are required. No Gradle or browser automation:

```sh
python3 art/storm_roc/build_preview.py
python3 art/storm_roc/build_preview.py --check
python3 art/storm_roc/validate_review.py
# Fast silhouette iterations:
python3 art/storm_roc/render_review.py --width 640 --only hunched-side flight-side
# Full 1200px review set, overlays and contact sheet:
python3 art/storm_roc/render_review.py
```

The animation viewer is `animation_viewer/viewer.template.html`: `export_preview.py` copies it to
`.local-previews/storm-roc/preview/storm-roc-redesign.html` and bakes the installed clips into
`roc.json` beside it. Serve it with `python3 -m http.server 8380 --directory .local-previews/storm-roc`
and open http://localhost:8380/preview/storm-roc-redesign.html. Its Player camera views each clip
from a player's eyes on the ground.

The older posture study above is served at http://localhost:8380. A build copies the concept sheets beside the page
and writes renders under `.local-previews/storm-roc/shots/`; that folder stays
untracked. Rebuilding updates its embedded geometry, clips,
textures, glow mask and spark sheet. `--check` regenerates in memory and compares
all seven candidate files and the generated HTML. It also verifies the head lock.

The CPU renderer executes the viewer's mesh/pose submissions in a Node VM and
rasterizes them with NumPy. It opens no browser, sockets or WebGL context. These
are offline renders, not in-game captures or proof of WebGL/GeckoLib execution.

## Current shape and rig

**699 body cubes and 198 body bones; 1,060 cubes and 251 bones total.**
The approved head remains 361 cubes / 53 bones. Both builder and validator enforce
at most 700 non-head cubes, 200 non-head bones and 1,100 total cubes. The atlas is
1024 × 1024, with 4 texels per model pixel on the head and 2 elsewhere; 16 model
pixels equal one block. These are art budgets, not measured runtime costs.

Measured from exported geometry and animation channels at neutral time:

| Measurement | Blocks |
| --- | ---: |
| Standing shoulder landmark (screech at rest) | 5.285 |
| Hunched shoulder landmark | 4.599 |
| Standing-to-hunched shoulder drop | 0.686 |
| Hunched hip joints, mean height | 2.991 |
| Hip–shin–tarsus–foot joint chain, unfolded | 4.250 |
| Hunched head pivot height | 1.896 |
| Peck mid-strike head pivot height | 1.763 |
| Hunched belly clearance above the floor | 2.756 |
| Total hunched silhouette height | 5.480 |
| Hunched beak-to-tail extent, with low streamers | 18.352 |
| Neutral flight wingspan | 27.203 |
| Level flight beak-to-tail extent | 13.782 |
| Perched crown above the spire placement offset | 7.618 |
| Flight wrist vertical excursion over a full beat | 4.619 |

The torso keeps its size and long narrow keel. The leg rig now has a short,
mostly hidden femur, a bulky white-and-blue feathered drumstick, a backward hock,
and a long red scaled tarsus. Bind segment lengths are 14 / 28 / 26 model pixels.
The original feet, articulated toes and three-step black talons are retained.
A sagittal-plane IK solve preserves all three lengths and plants the feet.

Standing hocks sit at about 46% of hip height (1.54–1.57 blocks above the ground).
In the final hunch they move backward and down to 1.34–1.46 blocks. The body drops
and pitches forward, giving a shallow 0.69-block shoulder-height reduction, about
half the previous crouch. Both zigzags remain visible below the raised belly. The feet remain
staggered and fixed. Perched uses a crouched grip over the spire; screech stands
taller. Flight folds the hocks back under the tail, and grab extends the legs
forward with open talons.

The five neck links distribute the hunched arch with no adjacent bend greater
than 40 degrees. Their final link follows the skull's back-to-front axis and meets
the rear socket, 8 model pixels above and 15 behind the head pivot. The head stays
near player height. The raised hunch uses the full neck-segment lengths for extra
reach, while the wind-up and strike take up and extend the curve. Independent neck
skin bones do this without scaling the head. Flight retains its thicker, shorter forward neck.

The 1.6-second peck combines a deeper leg crouch and withdrawn neck during wind-up,
a forward body lunge and leg/neck extension at the strike, then recovery. The feet
stay planted throughout. Its mid-strike review frame remains at 0.50 seconds.

Wing arms and hands are 1.5× their previous span and the chord is 1.3× broader.
Ten primaries, six broad secondaries, thirty-two covert feathers and fifteen
leading-edge feathers per wing retain the slate/red/white pattern. The leading
feathers and coverts are longer and narrower, with three tapered steps and pointed
ends overlapping toward the tail. Painted longitudinal shafts, shaded edges and
small edge notches replace their former diagonal tile pattern. The extra narrow
feathers preserve coverage while remaining within the body cube and bone budgets. There are no alula rods or
solid wing spars. Folded leading plates, coverts and secondaries use staggered
ends and taper inward along the body; primaries separate into a few distinct tips.
The slate, red and white tiers remain ordered. Folded primaries cross the tail center, stay inside the fan's
width, and end before its tip, including the half-mantled hunch and screech.

Seven overlapping root feathers per wing join that flight surface to the flank:
three white tertials, two rose scapular coverts and two white axillaries. Their
inner edges overlap the body, while their outer edges overlap the existing wing.
In flight they hinge near the flank with 60% of the shoulder's roll and yaw, so
the inner vanes do not lift across the back on downstroke. Folded poses lay them
down the flank beneath the existing coverts. The original wing pivots, feather
geometry and motion are unchanged. The added root is deliberately layered and
chunky, with a broader rose shoulder patch and white underwing overlap.

The shoulders rise and fall while the elbows and wrists counter-bend. The distal
planes stay within 10.7 degrees of horizontal through the beat, with 4.62 blocks
of wrist excursion. Flight-side uses a shallow oblique view at a broad neutral
frame; the other flight cameras and spread view fit the complete 27-block span.

Fifteen broad tail vanes replace the previous 37. In hunch and peck the fan stays
23 degrees below horizontal, at 1.6× length to clear the floor below the lower
body. Its local pitch compensates for the torso lunge. In flight
the shorter, wider fan levels its vanes into overlapping red streamers. Knees and
tarsi fold under the rear belly with both sets of talons near the fan base.

## Approved head lock

`approved_head/` now locks the user-requested forward-eye revision. The former
`head-approved-1007` snapshot is historical. The current full snapshot is
`.local-previews/storm-roc/snapshots/head-approved-eyes-forward/candidate/`;
`approved_head/snapshot.json` also pins the committed source files with SHA-256,
so source integrity is checked even when local previews are absent.
`head_lock.py` reserves the exact head UV rectangles and gutters before packing
body islands.

The head remains **53 bones and 361 cubes**. Sixteen existing eye-region cubes
form front-facing sockets, bright gold irises, square dark pupils, and thick blue
supraorbital shelves with backing into the skull. Iris centers in exported bind
pixels are `[±8.5432, 177.04, -38.6041]`: closer to the bill than the former side
eyes. Eye planes have 35-degree outward yaw from straight ahead and mirrored
12-degree roll, lifting the outer corners and lowering the brow toward the bill.
Both eyes read beside and above the bill in the front and low player cameras.

Every bone name and parent is retained. The head pivot `[0,165,-22]` and jaw hinge
`[0,160.8,-30.4]`, hooked bill dimensions, skull courses, crest, plumes, cheek and
cere feathers, palette, animation clips and non-head geometry are unchanged.
Unchanged head islands retain their exact UVs and pixels; the revised eyes have
new atlas islands with fully emissive irises. Body UVs repack around those islands.
The spark sheet, sequencing and phases are unchanged; only four anchors move:

| Anchor | Absolute bind coordinates (model pixels) |
| --- | --- |
| `spark_eye_left` | `[9.0651, 177.04, -39.3496]` |
| `spark_eye_right` | `[-9.0651, 177.04, -39.3496]` |
| `spark_brow_left` | `[9.2257, 179.84, -39.579]` |
| `spark_brow_right` | `[-9.2257, 179.84, -39.579]` |

Matched before/after CPU renders and a comparison sheet live in
`.local-previews/storm-roc/eyes-forward/`. Its `cameras.json` records front,
three-quarter, side profile, face close-up and player-view cameras. The player
camera is 1.6 blocks high, 10 blocks horizontally in front of the hunched head
pivot, with 1.25x model scale and an assumed 70-degree vertical field of view.
The head pivot is 2.369 blocks high at that scale. Geometry-only captures omit
spark sprites so eye visibility comes from the irises themselves. Re-render:

```sh
python3 art/storm_roc/render_review.py --width 1200 \
  --cases .local-previews/storm-roc/eyes-forward/cameras.json \
  --output .local-previews/storm-roc/eyes-forward/after
```

`validate_review.py` compares against the current head snapshot, checks forward
eye orientation and iris emissivity, checks loop
endpoints, and verifies neck and leg attachment at every authored frame. It checks
fixed segment lengths and planted feet in all ground clips, standing hock height,
backward hock placement, the final 4.5–4.7-block shoulder height and 0.55–0.8-block
crouch depth, belly clearance, head/strike height, and the smooth neck bend. The peck remains
1.6 seconds. Wingbeat samples check distal wing angles, wrist excursion and the
rigid head transform. Additional checks cover atlas/budgets, the rear socket,
folded primary containment/crossover, 26–28.5-block span and 13–14.2-block flight length.

## Poses and images

The viewer provides Perched, Hunched, Low peck, Flight flap, Wings spread,
Screech and Talons / grab. Selecting a pose chooses a useful camera; orbit,
front/side/three-quarter/top views, face focus, orthographic projection, timeline,
Normal/Charged crackle, sky lighting and PNG capture remain available. The jagged
spire, one-block grid and 1.8-block player are preview scenery only.

The current full render set is under `.local-previews/storm-roc/shots/`:

- `perched-side.png`, `three-quarter.png`
- `hunched-side.png`, `hunched-front.png`, `peck.png` (0.50-second strike)
- `flight-side.png` (broad neutral frame, 60° yaw / 16° elevation)
- `flight-three-quarter.png` (above, from the opposite side)
- `flight-top.png` (1.50-second downstroke)
- `spread-top.png`, `face-profile.png`, `standing-side.png`, `screech.png`, `grab.png`
- `hunched-overlay.png`, `flight-overlay.png`
- `review-contact.png`

The overlays isolate the actual model submission, use a single uniform scale to
match the concept's beak-to-tail extent, using projected beak and tail landmarks.
They align the grounded feet or the projected flight head attachment height.
The model is drawn at 52% opacity. The overlay canvas expands to preserve the
new wings extending beyond the original concept span. They do not warp individual features or match
camera perspective, so silhouette differences remain visible. The older body
turnaround overlay control remains a separate reference. Earlier first-pass
blockout images are retained in `shots/blockout/`.

## Sources and remaining differences

- `sculpt.py`, `proportions.py`: approved head and original wing/leg foundations.
- `anatomy.py`: source proportions and neck skin rig.
- `simplify.py`: chunky plates, enlarged wings, consolidated bones and painted legs.
- `legs.py`: fixed-length avian leg solve and backward-hock selection.
- `approved_ground_pose.json`: historical upright-leg channels, superseded by the crouch rig.
- `fold.py`, `poses.py`: frame math, compact folds and exported local bone channels.
- `build_preview.py`, `head_lock.py`: pixel atlas, protected islands and export.
- `sparks.py`: unchanged flipbook artwork and explicitly ordered anchors.
- `viewer.js`, `viewer.template.html`: local interactive review.
- `render_review.py`, `review_scene.cjs`: CPU renders and concept overlays.
- `validate_review.py`: snapshot, rig, motion and scale checks.

Candidate exports remain Bedrock geometry 1.12.0 and GeckoLib animation 1.8.0 in
`candidate/`, alongside the RGBA atlas, glow mask, spark sheet, metadata and manifest.

The giant wings intentionally exceed the original concept's span. The final hunch
keeps a higher body and visible legs, while the neck reaches down to player height.
Primary tips and drumstick cuffs retain the approved chunky construction; their
edges are still angular up close. The shallow red tail remains longer than the
original concept, as established in the prior approved passes. Flight remains 13.78 blocks long. Intermediate pose blending,
runtime cost, native GeckoLib loading and live Minecraft appearance remain outside
this art review.

## Runtime combat export

`python3 art/storm_roc/build_runtime.py` installs sixteen runtime clips, the block model
and atlas from `art/storm_roc_mod/candidate/` (`MODEL`), the study's head crackles and
sampled server sockets. It preserves the model's shape and texture, converting the study
basis to GeckoLib by reversing Y/Z rotation signs.
The runtime scale is **1.25x** (`SCALE = 1.25 / 16`). Pose channels are sampled at
40 Hz, with 80 Hz keys around fast strikes to keep IK interpolation accurate; sockets remain
20 Hz, in blocks. Java's tick clock and clip durations are unchanged.

Ground poses use fixed 14/28/26-pixel avian legs. The solve tilts its sagittal plane
laterally for body weight shifts while retaining the exact planted foot targets.
Partial wing spreads interpolate world orientations and pivots, avoiding scattered
feathers from folded-wing Euler interpolation. Rotation channels are unwrapped
before export because Java interpolates their components directly.

Reworked clips share the tools in `motion.py`. Keys can say how a move arrives (`ease`,
`snap` for a bird's quick turn, `whip` for a landing strike, `drop` for a foot set down,
`settle` to overshoot and settle), envelopes rise fast and relax slowly, and seeded loop noise
replaces even sine sways. `twitch` is the Roc's signature head: held dead still, snapped to a
new angle (0.05 s plus 0.02 s per 10 degrees) and landing with a small electric shiver; reuse
it wherever the Roc looks around, above all in its lightning moves. `FOLLOW` lists each clip's loose parts (crest plumes, cheek fans,
cheek feathers, tail fan): a damped spring makes each trail and swing on behind the bone that carries
it, faded out at a loop's seam, and no faster than 450 degrees a second. Clips in `THIN` drop keys that a straight line between their
neighbours reproduces within 0.02 degrees; the bones that carry server sockets keep every key.

Folding wings: the study's perched wing stores its forearm and hand each turned about 177°
from the open wing, with its feathers rolled back over to match, so `blend_wings` from open to
perched carries the hand up and over its back and rolls every feather. Fold through
`flat_fold` instead, the way a bird folds: `joint_mix` bends the open wing's joints into a Z laid
flat at its side (arm swept back, forearm forward, hand back; the arm's and forearm's feathers
counter-turned so they keep pointing back, the hand's riding with it), then `fold_wings` turns
every part in world space the short way onto the perched fold (85–116°, so the folded wing
swings in as one). Opening a wing is the same in reverse, as the spire hop's push-off does.

Java blends Euler angles linearly between keys and across crossfades. A turn has two Euler
forms, (x, y, z) and (x+180, 180−y, z+180), and at gimbal lock more; the IK and the world-space
wing blends can return either from one key to the next. `continuous()` stores each key in the
form nearest the previous one, and `StormRocMotion` crossfades toward the nearer form.
`build_runtime.py --only hunched` and `export_preview.py --only hunched` rebuild one clip in
seconds and keep the rest as installed; the preview bakes reworked clips at 80 Hz.

The stuck bill is pinned **0.9 blocks below the rock** (11.52 model pixels), through
5.65 s. Wing contact uses the actual rotated cube corners. `WING_REST = -0.125`
and `WING_SLAP = 0.7` blocks preserve the old model-space clearances at half scale.
The wing attack also limits its downstroke at the rock surface; the takeoff's clears it. The
swing bill crosses the front at **1.05 blocks** throughout the damage window.

All times below are clip seconds; a second is 20 server ticks.

| Clip | Length | Loop | Key times and motion |
| --- | ---: | :---: | --- |
| perched | 7.2 | yes | Left snap 0.45–0.60, hold to 1.65; right snap 1.65–1.80; one-eye cock 2.05–3.10; bob 3.15–3.50; feather shake 3.75–4.25; half-open stretch 4.55–6.30; tail flicks, breathing and planted weight shifts. |
| call | 3.0 | no | Head quick and electric, body slow (`CALL_BOLT`, `CALL_STRIKES`). Three stuttered head snaps to the sky 0.08/0.22/0.34 as the bill gapes; the chest swells and rises to full height by 1.10. Bolt leaves the bill at **0.50** (`CALL_VOLLEY`), a jolt through body, wings and tail. Wings out into `flat_fold` 0.45–1.10, then open half raised and mantled forward by 1.60, trembling; skyward head tics 0.95/1.32/1.58. Command: head whips down 1.80–2.05 onto its mark as the first strike lands at **2.00**, chest thrust, wings flung wide; a shiver at the second strike **2.50**; wings fold back the natural way 2.15–2.95; settled for the third at **3.00**. In game the gaze locks on the struck player 1.9–2.6, crest and wing-edge crackles run 0.1–2.2 and the bill blazes at the bolt and the command. |
| land | 2.5 | no | Every move slow and heavy for its size. Starts `LAND_LEAD` (0.80) before the Roc arrives, from the flight's neutral glide (the hop and the takeoff chain in; the phase change's flight crossfades). Flare to 0.55: body pitched up 30°, wings raised and swept forward, legs hinge-swung into the study's grab 0.15–0.60, head held steady on the landing spot in world space; the root comes down from the flight's height to the perch's by **0.80**. Braking beat 0.40–0.68; touchdown at **0.80** (`LAND_TOUCH`): talons slam on and grip, legs sink deep to 0.95 and rise by 1.9, weight rolls on over the feet to 1.15. Wings high 1.3–1.45 with one slow sway; right talons re-grip 1.3. One slow look aside 1.25–1.95. The fold: the wings lower into `flat_fold` 1.42–2.04 (hands first), then swing in onto the perched fold 1.82–2.40, shoulders settling 1.95–2.5. Ends exactly at the call's first frame (chained); the client's gaze turns slowly, without snaps, to glare from 1.85. |
| spire_flight | 1.8 | no | The hop between spires until its landing's flare (StormRocRules `AIR_CYCLE - FLY_AT - LAND_LEAD`). Server holds it on the spire until **0.40** (`SPIRE_PUSH`) while it turns: crouch with the crest lifting, head locked forward; the wings swing out from its sides into `flat_fold` by 0.26 and open from the shoulder out, the hands last, raised by 0.50. Push-off 0.40–0.52 (talons planted to 0.50, then legs frozen and swung on their hinges into the flight tuck by 0.92); one heavy downbeat to 0.88; slow half recovery to 1.45; wings set to glide by 1.80. Glances down 1.00, forward 1.40. Ends exactly at Land's first frame (chained, no crossfade). |
| flight | 7.2 | yes | The long loop, played only in the phase change's stand-in. Two unequal heavy beats: gather to 0.95, shoulder power stroke 1.10–1.55, slow recovery to 3.00, lighter second push 3.14–3.60; hands and primaries trail the shoulder. Chest lift follows each push, peaking at 1.95 / 4.02. Glide from 4.65 with small hand/tail corrections and irregular primary flex. Head glances down and sideways at 0.25, 1.35, 2.65, 4.20 and 5.60, returning forward at 6.70; the client sometimes replaces the scan with a brief focus on a living survival player below. Tucked legs and spring follow-through in the crest, cheeks and tail. Client banking follows the actual turn. Exact existing flight-neutral seam retained for landing/takeoff; the dive retains its separate earlier beat. |
| hunched | 9.6 | yes | The brawl guard. Head twitches (`GUARD_GLANCES`) at 0.30, 1.05, a stutter 1.62/1.74, 2.40, 3.15, 3.60, a one-eye cock 5.35, 5.95, 6.50, 7.20, 8.10 and home at 9.05, each held still and landing with an electric shiver. Uneven breaths, weight held on the left 2.1–3.5 and the right 4.9–7.2, a weaving neck; clack clack 0.55–0.95; tail flick 1.35; right talons knead 2.65–3.30; hiss with neck thrust, wide bill, flared crest and mantled wings 3.70–5.30; left talons knead 6.25–6.90; rouse 7.30–8.00; clack and rattle 8.55. |
| dive | 3.0 | no | Perched start, climbing beats to 1.80; tip 1.50–2.05; tight stoop 2.05–2.55; wings reopen 2.55–2.80; spear to exact stuck(0) at **3.00**. |
| stuck | 6.0 | no | Pinned bill, rocking/heaving torso, wing slaps and scrabbling feet; big heave **4.85–5.65**; bill releases after 5.65, hunched neutral at 6.00. |
| bite | 3.9 | no | Thunder Bite, standing. Rears up slowly to 1.30 with the bill opening wide and wings mantled, its head looking down the line to the bite point the whole time (the game's gaze then follows the actual player until the aim locks at 1.20; `MOUTH_SPARKS` crackle at the corners of the mouth), holds charged, lunges from 2.00 and snaps the bill shut at **2.30** at player height (the peck's old strike pose, so the reach is the same) with a recoil; holds the bite to 2.60, pulls back by 3.90. No twitches: heavy, slow aiming. |
| bite_leap | 4.4 | no | The same until the 1.20 lock, where the server switches to it when its prey is 2+ blocks out of reach; its body and head follow the standing bite's timeline stretched from the lock to its bite. Deep crouch as the wings rise slowly 1.25–1.85; push-off and downbeat to **2.15**; 0.6 s in the air, the legs blending by slerp (`blend_local`) into the study's grab legs, trailing back then reaching forward, the wings recovering then flaring broadly; lands at **2.75** and absorbs, the neck lifting the bill back to player height for the bite at **2.80**. The server carries it up to 10 blocks between push-off and landing. |
| swing_left | 2.6 | no | Storm Sweep. Coils the neck far round to one side over 1.0 (neck base 58 degrees, lower neck 24, body twist 15, weight shifted, wings half open), holds to 1.25 with the bill crackling, scythes the open bill across the whole front at chest height **1.25–1.65** (about 170 degrees, 10–11.5 blocks out), overshoots to 1.85 with the tail counter-swinging, settles by 2.60. The server's crackle wave rolls out over the swept front as it ends. |
| swing_right | 2.6 | no | The same sweep the other way. |
| scream | 2.9 | no | Storm Scream. Unlike the bite's tall rear-up, a huge, slow breath low down to 1.30: it sinks, draws the neck back in an S with the bill to the sky (head pitched 68 degrees up), chest up, wings spread wide and level, tail fanned, `CREST_SPARKS` crackling on the plume tips; draws in to 1.50, whips forward and blasts straight ahead at **1.60** with the bill at 48 degrees; the body shudders at 14 Hz through the scream as the wings are thrown back just after it; settles by 2.90. |
| wing | 4.0 | no | Thunderclap. A deep crouch as the wings rise to 0.60; the leap with legs and one huge downbeat at **0.85** (the server throws it 9 blocks up, slowing to the apex at 1.75, a hang to 2.05, then gathering speed down); rising, the legs blend by slerp toward the grab legs and the body tips nose-up, wings sweeping up; at the top the wings are raised high and the head down on the players below, `WING_SPARKS` on the edges; it smashes down, legs reaching, and lands at **2.60** with the wings thrown out and a roar, the neck lifting as it absorbs; the wings settle (contact solve, rate-limited) and fold slowly by 4.00. |
| pounce | 4.0 | no | Raptor Strike. Sinks into a low stalking crouch by 0.60, wings half open and low, crest pressed flat, weight rocking foot to foot and the tail lashing in held flicks, the head level on its prey (the game's gaze follows the actual player until the aim locks at 1.20); draws back and coils 1.00–1.35, talons gripping, wings spread wide; pushes off with one huge downbeat at **1.60**, the legs leaving the rock from their push-off pose and swinging back through their hinges into the study's grab legs, tucked back; flies flat and fast (the server carries it on a low arc, at most 4 blocks up), wings wide and level, crest swept back; screeches, pitches up and flares 2.25–2.55 with the tail lifted clear, the talons swinging forward; strikes at **2.60** with `TALON_SPARKS` flaring; mantles to 3.40, wings arched down onto the rock (contact solve, rate-limited) and head low, glancing about; settles by 4.00. A neck solve holds the bill at set heights over the rock throughout. |
| stampede | 4.2 | no | Bull scrapes peak **0.30 / 0.90**; alternating-stride charge **1.20–2.70**; blend into lean-back skid after 2.70; air-brake wings; head toss 3.65–4.20. |
| takeoff | 2.9 | no | Every move of the body slow and heavy, the head quick (`TAKEOFF_PUSH`, `TAKEOFF`). Holds its guard to 0.30 while the game's gaze lets go; the head snaps up onto the spire it is leaving for at 0.35 and holds dead still through the launch while the server turns it toward the spire and the neck slowly rises. Wings out into `flat_fold` 0.15–0.55, then unfold shoulder first, hands last, 0.45–1.01, rising to fully raised by 0.94. Deep crouch 0.40–1.00, talons gripping, held to 1.03. Push-off 1.03–1.20: legs straighten, body driven up and forward, grunt. Feet leave the rock at **1.20** (tick 24, the gust) as the downbeat sweeps through level (wrist level with the shoulder); bottom of the stroke 1.30, at least 0.9 blocks clear of the rock before the server lifts it. Legs trail straight back 1.20–1.45, then fold on their hinges, hip and knee first and ankle last, into the flight's tuck by 1.90. Slow recovery to 2.00; head snaps down onto the players at 1.70; second, weaker beat 2.06–2.32 with a heave; head forward 2.45; exactly the flight's neutral glide at **2.90**, where `land` (or, for the phase change, `flight`) takes over with no crossfade. The server eases its first climb in (about 1 block by tick 28, 7 by tick 34) and the client banks and pitches it with its climb from the push. |
| hop | 1.2 | no | Exact stuck(0) start; bill yank/head throw to 0.30; huge downbeat about 0.55; tip and spear from 0.85 to exact stuck(0) at **1.20**. |

Server integration: select `call` and `land` explicitly. The concurrent Java
integration now does this, with a 60-tick call and 16-tick land. Call's warnings
belong at clip tick 10 and its bolts at ticks 40/50/60. Land starts from flight
neutral; its local feet grip at tick 6 and remain planted through tick 16. Keep
the root on the capstone while it absorbs the landing. The planned crossfade can
accommodate the preceding flight phase. Takeoff ends at flight(0).

The perched idle and the brawl guard each keep their own clock in Java, so every spire
visit or pause between attacks resumes the loop where it left off instead of restarting it.
Head yaw stays within +/-45 degrees for layered look-at. Java owns travel arcs, roll from turning, renderer scale and gameplay radii.

`python3 art/storm_roc/build_runtime.py --check` compares the installed exports.
`python3 art/storm_roc/validate_runtime.py` checks the head lock, exact geometry/UV
conversion, fixed leg lengths, planted feet, socket agreement, loop/end poses,
pinned bill, wing/ground contact and server impact timing. Hunched feet may leave the rock
only in their `KNEADS` windows, must return to the same spot and never move fast enough to
count as a stomp. The peck timing check
now measures its maximum forward extension at 0.60 s rather than requiring the
old study's entire strike pose. Flight sanity bounds allow the intentionally deeper
power strokes: maximum span must stay 32–36.5 blocks and wrist vertical excursion
must stay 8–16 blocks at 1.25x. These are new runtime envelopes; the unchanged
study validation still describes its original shallow flap. These are export checks,
not native gameplay verification.

`python3 art/storm_roc/render_runtime.py --gifs` creates a contact sheet for every
clip and 480px / 10fps GIFs for perched, call, flight, dive, bite, scream, wing,
pounce and stampede in `.local-previews/storm-roc/animations/`. It uses the CPU
renderer at 1.25x, without a browser or generated study HTML. Each clip keeps one
camera, a floor and a 1.8-block human reference. These show local authored poses;
server travel arcs are not applied, so airborne dive/hop frames can cross the
reference floor before their actual in-game landing. Native verification remains
the integrator's task; see the testing reference.
