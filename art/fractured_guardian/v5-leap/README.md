# Double shockwave leap

Adds three full-body clips to the six approved V4 clips without changing their
animation values or the 132 cubes / 38 bones / textures.

- `leap_launch`, 0.7s: both fists rise and strike the ground to propel the body.
- `leap_air`, 1.8s: legs tuck, shoulders and arms draw overhead, then feet extend.
- `leap_land`, 2.2s: feet plant, torso/knees/hands compress and recover.

Minecraft moves the entity and emits the two independent shockwaves. The HTML
preview defaults to a full 16-block jump on a flat grid, with both shockwaves and
the runtime arc/timings. Individual poses remain selectable. This is a visual
simulation; damage and terrain collision still require Minecraft playtesting.

Regenerate with `python3 art/fractured_guardian/v5-leap/build_leap.py`, then run
`python3 art/fractured_guardian/v5-leap/preview_leap.py` for pose renders and floor
checks. Package with `python3 tools/prepare_guardian_assets.py`. Run the repository
build and asset checks in AGENT.md before installation.

## Session checkpoint

The user approved seeing the full preview and asked for a higher, longer jump:
1.8 seconds airborne and an 11-block arc. The subsequent in-game report was a
cancelled takeoff. A physical-floor check and vertical departure/arrival fix are
installed but await user confirmation. See [the session handoff](../../../docs/MOD_HANDOFF.md).

`sequence_preview.py` adds the default full-jump scene to the generated HTML.
Refresh V5 after regeneration; V4 has no full-jump option. Preview flight and wave
visuals are illustrative, not evidence that Minecraft's collision or damage works.
