# Guardian combat review

An offline review page for all 18 packaged Guardian clips, with the clips from the heavy
performance pass (every clip but the throws and the intro: 15 of 18) marked
"Reworked tonight", each with its timings and events. The 3D view uses the same pose math as
`v4-expressive/preview_motion.py`.

```bash
python3 art/fractured_guardian/combat-review/build_preview.py          # writes .local-previews/guardian-combat-review/
python3 art/fractured_guardian/combat-review/build_preview.py --check  # verifies the clips and marks
```

Serve the repository root (for example `python3 -m http.server`) and open
`/.local-previews/guardian-combat-review/index.html`. `render_review.py` renders the per-clip
review sheets, from a three-quarter view and from a player's eye height. The preview is an
art reference, not a native gameplay test.
