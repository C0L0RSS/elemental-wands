# Block Nature study

Browser study for Nature's opaque textured 3D effect pieces. It is ported to the game in `src/main/java/com/anton/elementalwands/client/blocknature/` (November 11), and `textures/blocknature.png` is copied to `textures/misc/blocknature.png`. Keep the two in step: change the study first, then the port.

## Open and reproduce

```powershell
py -3 art/nature/blocknature/make_page.py
py -3 art/nature/blocknature/serve.py
# http://127.0.0.1:8351/blocknature/
py -3 art/nature/blocknature/textures/build_atlas.py --check
node art/nature/blocknature/check.mjs
node art/nature/blocknature/shot.mjs flower .96 caster .local-previews/blocknature/flower-caster.png
node art/nature/blocknature/shot.mjs full-bloom .48 orbit .local-previews/blocknature/bloom-close.png "dist=8"
```

`index.html` is locally usable but ignored/untracked by `art/**/*.html`. `.gitignore` is unchanged. `make_page.py` reconstructs it from tracked `page-template.txt`. Take headless shots one at a time. `shot.mjs` detects Windows Chrome at `C:/Program Files/Google/Chrome/Application/chrome.exe`, then the existing macOS paths.

## Files and scene contract

The Wind engine is copied with atlas keys renamed. `engine.js` owns math, deterministic hash, oriented cuboids, face shade, camera clearance and WebGL; `shared.js` retains its figures and terrain. `kit.js` owns only Nature materials and pieces. `stage.js` assembles terrain, context and effects. `app.js` supplies play/pause, quarter speed, scrubbing, orbit drag/zoom, caster/target cameras and dusk. `check.mjs` samples all three cameras including event boundaries, checks finite vertices, texture keys, repeated-frame determinism and atlas cuboid budgets. Counts exclude context meshes and terrain.

Every scene exports `id, name, slot, duration, summary, notes, phases, area, budget, cameras, draw(m,t,ctx)`. World units are blocks, y is up, seconds are 20 ticks each. The caster faces +x. Draw output is a pure function of time and fixed seeds, with no accumulated simulation or random frame state. Piece orientation is world-relative; no billboards. All Nature pieces are opaque, lit and disappear by shrinking. `ctx.embers=false` removes optional Full Bloom gold kernels while preserving its wave. Near-eye clearance is inherited from Wind.

## Material and shape kit

Atlas: 64 x 128 RGBA, eight rows x four columns of 16 x 16 tiles. Nearest filtering, discrete value steps, clustered veins/folds/grain; no photographic noise or gradients. Foliage/bark/pink/violet/gold follow the approved mesh palette. Gold and sap shift selected highlights at 8 fps; structural materials hold steady.

| Row | Band | Role |
|---|---|---|
| 0 | leaf | Fresh leaf blades |
| 1 | darkleaf | Leaf undersides, removal, pad context |
| 2 | pink | Main petals, wave, impact |
| 3 | violet | Sparse petal variation |
| 4 | gold | Pollen kernels, mouth shot and launch |
| 5 | bark | Stem/ribbon material, available in gallery |
| 6 | sap | Shared healing leaves |
| 7 | cream | Pale spent petals / wilt |

Pieces: **petal** (two thick stepped slabs), **leaf** (three cuboids: blade, tip, raised vein), **pollen** (one kernel), **ribbon** (four bent-chain cuboids; gallery only), and raw **cube** material samples. Petals and leaves tumble with bounded deterministic orientation; no flat flash card. Healing uses six sap leaves spiralling up around the body with four gold kernels climbing between them, the face left open (strengthened by Claude before the port). Visual lifetimes (typically .3-.8 seconds) are design proposals; gameplay event times and reach come from the current Java.

## Scenes, budgets and runtime replacement map

Budgets below count individual atlas cuboids, not compound pieces. They are per displayed effect instance, not a global fight budget. A later Java port should merge repeated heal cues and cap simultaneous distant instances; the browser study does not implement a global runtime allocator.

| Scene | Cap | Replaces / event state |
|---|---:|---|
| full-bloom | 260 | FullBloomManager cast BLOOM/PETAL/POLLEN; wave PETAL/LEAF/POLLEN; immediate ally HEART; caster PETAL every 8 ticks for 240 ticks. Needs origin, cast tick, caster and blessed IDs. |
| flower | 72 | FlowerTurretManager planting BLOOM/LEAF/POLLEN, grow BLOOM/PETAL, shrink PETAL, muzzle POLLEN/PETAL, removal PETAL. SentryBloomEntity shot/aim/mount/wilt state; successful owner heal uses shared cue. |
| seed | 50 | SeedProjectileEntity flight POLLEN/LEAF, player entity-hit BLOOM/PETAL, flower entity-hit PETAL, block-hit POLLEN/LEAF. Needs projectile variant and collision event. |
| heals | 30 | ThornLashEntity contact POLLEN and successful lifesteal HEART; NatureCombat.flowerDamageDealt HEART. Needs healed entity/event tick; no cue when no health is gained. |
| springbloom | 70 | SpringbloomManager launch POLLEN, flight LEAF every four ticks, removal LEAF; SpringbloomEntity failed-pod-hit LEAF. Needs launch and removal events, player flight positions. |
| kit | 100 | Review only: all eight bands, all four frames, all four compound shapes. |

Full Bloom reaches radius 10 in .6 s; allies are blessed at cast, not when the ring passes. The proposed wave uses 48 petals plus 16 leaves and optional eight kernels rather than replicating every old particle emitted each tick. It is deliberately open and low, with no damage flash. The three drift petals repeat every .4 s until the 12-second blessing ends. Grass and flowers remain vanilla blocks; they are omitted from this isolated effect study.

Flower plant/unfurl is 12 ticks, shots every 40 ticks, secondary head offset 20 ticks, mouth socket .53125 along the head axis. Ordinary pivot is .6875 high; grown pivots (.3125,1.5,0) and (-.6875,1.125,.125), scales 1 and .75. The lifecycle timeline includes a 12 s age pause, so natural wilt is at 34.3 and removal at 36.3 after planting at .3. A few falling cream petals at wilt onset are a proposed addition; current runtime only emits its final removal burst. The scene is an event study with a nearby sample target, not a combat simulation.

Player Seed uses 1.5 blocks/tick, .03 gravity and .99 drag; flower Seed uses 1.4 straight and half model size. Both hit a sample target after .25 s; this is scene placement, not a range limit. Their runtime maximum lifetimes remain 100/30 ticks. Springbloom is an 8-second pad; the standing-drop example launches vertically at 1.7 blocks/tick with .08 gravity/.98 drag. Its default close camera judges the launch; use target/orbit zoom for the full trajectory. Horizontal movement is zero for this example (runtime can reach 1.9 blocks/tick).

## Approved models and scope

`context-data.js` contains a verbatim snapshot of approved `seed` and `sentry_head*` vertex arrays and `nature_seedling_*` block cuboids. `context.js` presents these without editing their source. Block context uses its face tint on a neutral material rather than reproducing the vanilla concrete-powder texture. Flower pose/stage presentation is illustrative. Springbloom's pad is a labeled stand-in matching its 1.5-wide/.6875-high collision envelope. The approved Thornbite flytrap and floating mark are not displayed or changed. Effects do not replace any of these models.

Audit: NatureVfx.rootSpokes/treeHeart/floweringCanopy have only retired OvergrowthManager callers. NatureVfx.line/ring are shared helpers; fairyRipple went with Verdant Step, which was removed. OvergrowthManager, OvergrowthSeedEntity and EntangleTracker are excluded as requested. NATURE_VINE is retired. NatureParticleFactories registration entries are not effect spawn sites. All other current ModParticles.NATURE_* spawn sites found by the audit are covered above.

Browser geometry checks and screenshots establish a review candidate only, not Minecraft appearance or runtime performance.


## Review evidence

Final sampled peaks (cuboids): Full Bloom 241/260; flower 32/72; seed 38/50; heals 23/30; Springbloom 60/70; kit 42/100. (A Verdant Step scene was dropped when that passive was removed.) Atlas byte check and geometry/determinism/camera checks pass. Windows headless `shot.mjs` was exercised successfully on the kit scene. No Java build or Minecraft test was run for this browser-only study.

Screenshots are in `../../../.local-previews/blocknature/`: `nature-contact.png` contains all 22 timeline/camera samples; `elements-contact.png` compares Fire Inferno, Wind Updraft, Space Bolt and Nature Full Bloom. `screenshots.txt` lists every PNG by absolute path. The contact sheet compares material language, not identical viewpoints, scale or performance. `comparison.html` is another ignored local page, regenerated by `make_page.py`, and loads the existing studies directly without modifying their source or pages.
