# Storm Roc posture study

Review-only GeckoLib candidate for the Storm Roc. The current silhouette target is
`concepts/body-postures.png`: the hunched top figure and horizontal flying bottom
figure. The face and feather sheet (`concepts/face-and-feathers.png`) and body
turnaround (`concepts/body-turnaround.png`) still own the head design, palette and
wing pattern. Nothing here installs runtime assets.

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

Serve the preview with `python3 -m http.server 8380 --directory .local-previews/storm-roc`
and open http://localhost:8380. A build copies the concept sheets beside the page
and writes renders under `.local-previews/storm-roc/shots/`; that folder stays
untracked. Rebuilding updates its embedded geometry, clips,
textures, glow mask and spark sheet. `--check` regenerates in memory and compares
all seven candidate files and the generated HTML. It also verifies the head lock.

The CPU renderer executes the viewer's mesh/pose submissions in a Node VM and
rasterizes them with NumPy. It opens no browser, sockets or WebGL context. These
are offline renders, not in-game captures or proof of WebGL/GeckoLib execution.

## Current shape and rig

**657 body cubes and 184 body bones; 1,018 cubes and 237 bones total.**
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

`approved_head/` contains the protected head-family records and atlas source from
`.local-previews/storm-roc/snapshots/head-approved-1007/`. `head_lock.py` reserves
those exact UV rectangles, including gutters, before packing any new body island.

All **53 head-family bones and 361 cubes** retain their head-local pivots,
origins, sizes, cube rotations and UVs. Texture and glow-mask island pixels are
identical. The spark sheet is byte-identical, and all six anchor offsets,
sequence settings and staggered phases are preserved. The family was translated
rigidly in bind space to meet the longer neck. The existing jaw hinge opens in
the screech; the crest and cheek geometry have no new deformation.

`validate_review.py` compares against the supplied head snapshot, checks loop
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
