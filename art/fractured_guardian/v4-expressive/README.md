# Fractured Guardian — full-body performance 04

September 8 revision of all six animations, based on feedback that the first pass
left too much of the body stationary. The approved mesh and atlas are preserved.

- Beam: 1.6-second loading pose with bent knees, chest opening, spread arms,
  opening jaw, and articulated hands; recoil and 1.4-second recovery. The runtime
  cyan pulse lasts 0.6 seconds. The preview displays the rig, not Minecraft VFX.
- Idle/walk: pelvis weight shifts, coordinated planted-foot IK, torso and shoulder
  movement, counter-moving head, and wrists/fingers that follow the arm motion.
- Awaken/slam/throw: stronger whole-body anticipation, compression, opposite-arm
  counterbalance, chest response, and hand/shoulder settling.

`build_animations.py` starts from the retained v3 authoring definitions, adds the
performance poses, solves planted feet and the beam head pivot, and bakes eased
tracks. It then removes redundant keys with bounded interpolation error.
`preview_motion.py` samples the exported curves at 60 Hz for floor clearance and
at 120 Hz for beam head stabilization, and builds the contact sheet and viewer.
The browser computes each bone transform once per frame to keep dense clips smooth.

```bash
python3 art/fractured_guardian/v4-expressive/build_animations.py
python3 art/fractured_guardian/v4-expressive/preview_motion.py
python3 tools/prepare_guardian_assets.py
```

These commands regenerate the authored outputs. Preserve any manual Blockbench
edits before rerunning them. The editable `.bbmodel` includes the unchanged atlas
and all animations. Earlier versions are retained for comparison.

See `../../../docs/fractured-guardian.md` for Lunar commands and playtest notes.
