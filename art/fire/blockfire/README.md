# Block fire study

Browser study for rebuilding the six Fire wand spells as 3D "block fire": emissive cubes
that rise, tumble, cool and shrink away, replacing flat flame cards and most particles.
It is a review page, not runtime code. Approved scenes are ported to Java renderers
afterwards; nothing here enters the mod JAR directly.

```sh
python3 -m http.server 8347 --bind 127.0.0.1 --directory art/fire
# open http://127.0.0.1:8347/blockfire/
node art/fire/blockfire/check.mjs [scene ...]          # geometry + budget check
node art/fire/blockfire/shot.mjs <scene> <t> [cam] [out.png] ["dist=6&yaw=1&pitch=.3&dusk=1"]
```

Screenshots default to `.local-previews/blockfire/shots/` (ignored).

## Files

| File | Owns |
| --- | --- |
| `engine.js` | Math, deterministic noise, `Mesh.box` (oriented cuboids, baked face shade), WebGL renderer |
| `kit.js` | Shared primitives: `cube`, `flame`, `burst`, `smoke`, `puff`, `sparks`, `flameRing`, figures, terrain |
| `stage.js` | Builds a frame: terrain, then the scene; counts fire cubes |
| `scenes/*.js` | One file per spell plus the `kit` gallery |
| `textures/` | The `blockfire.png` atlas (8 bands × 4 frames) and its builder; preview-only scenery tiles |

## Scene contract

A scene file default-exports:

```js
{
  id, name, slot,            // slot: Primary | Secondary | Ultimate
  duration,                  // loop length in seconds (the timeline)
  summary, notes,            // plain-language text shown beside the view; notes may use \n
  phases: [[t, 'label'], …], // timeline labels
  area: {x0, x1, z0, z1},    // block terrain extent
  terrain: (x, z) => int,    // optional column heights (block centres); default flat 0
  budget,                    // optional peak fire-cube cap for check.mjs (default 600)
  cameras: { orbit: {target, yaw, pitch, distance},
             caster: {eye, look} | t => {eye, look},   // first person: cubes near the eye shrink
             target: {eye, look} | t => {eye, look} }, // what the enemy sees from the ground
  draw(m, t, ctx),           // ctx: {embers, dusk, ground(x, z) -> surface y, cam}
}
```

Conventions: y is up and ground level is y = 0 unless `terrain` says otherwise. Casters stand
at the origin facing +x (`yaw = 0`); `kit.wandTip(pos, yaw, pitch)` matches the mod's wand-tip
estimate. `ctx.embers` false must remove sparks and smoke but keep the spell's main body,
like Minecraft's reduced-particle setting.

## Rules

- Everything is a pure function of `t` and seeds: no `Math.random`, `Date`, or module state
  that changes between frames. The Java port must reproduce the same cubes.
- Fire flickers by length and size; it never whips or ripples (that reads as tentacles).
  Pieces leaving a flame shrink to nothing and restart hidden inside something larger.
- Fire cubes are opaque and fade by shrinking, not alpha. Smoke is the exception: see-through,
  evenly shaded, lit by the scene and drawn after everything else (`kit.SMOKE_ALPHA`, `SMOKE_SCALE`),
  because opaque grey cubes read as thrown stones. In game it can stay vanilla smoke particles.
- Show the real gameplay size, reach and timing from the Java code. The page has ¼× and ½×
  playback for inspection, so don't slow the scene itself.
- Each scene file starts with a short `Runtime port` comment: which entity or manager
  drives it in game, what client state it needs, which particles stay, and its cube budget.
