#!/usr/bin/env python3
"""Render animation clips as review strips, using the same offline renderer as render_review.py.

With no arguments, renders the standard strips into .local-previews/pyre-king/renders/clips/.
To render one strip:  render_clips.py OUT CLIP t1 t2 ... [--cam yaw,pitch,extent,atx,aty,atz] [--player]
--player looks from a player's eye, 1.6 blocks up and 8 blocks in front of him.
"""
import json
import math
import subprocess
import sys
from pathlib import Path
import numpy as np
from PIL import Image
import render_review as rr

HERE = Path(__file__).resolve().parent
OUT = rr.OUT/'clips'
DEFAULT_CAM = (28, 10, 9.5, .5, 2.6, -.8)
SIDE = (90, 4, 7, 0, 2.4, 0)
# Most times sit on the bake's 20 Hz samples. The in-between strips also show frames between
# them (0.867, 3.767, ...): GeckoLib and the workshop lerp the keys there, which is where a
# fist leaving the grip or a sword snapping round would show.
FRONT = (0, 8, 7, 0, 2.6, 0)
ABOVE = (20, 70, 7, 0, 2.6, -.5)
THROW_SIDE = (90, 4, 8, 0, 2.9, -.6)
THRUST_SIDE = (90, 4, 9, 0, 2.4, -1.8)
THRUST_34 = (-35, 10, 9, 0, 2.6, -1.2)
TURN_BEHIND = (150, 14, 11, 0, 2.4, 1)
TURN_ABOVE = (20, 60, 12, 0, 1, 0)
WHIRL_WIDE, WHIRL_NEAR = (30, 22, 22, 0, 2, 0), (30, 12, 8, 0, 2.6, 0)
UNBOUND_WIDE, UNBOUND_NEAR = (25, 24, 22, 0, 2, 0), (25, 10, 9, 0, 2.8, -.8)
STRIPS = {
    # Tell, cock, the cut at mid-blow, follow-through, re-cock, the backhand at mid-blow, the pause.
    'royal-cut.png': [('royal_cut', [0, .5, .9, .95, 1.0, 1.3, 1.75, 1.85, 1.9, 2.8], None, False)],
    # The edge leads: mid-blow from the front and from above.
    'royal-cut-edge.png': [('royal_cut', [.95, 1.0, 1.05, 1.85, 1.9], FRONT, False),
                           ('royal_cut', [.95, 1.0, 1.05, 1.85, 1.9], ABOVE, False)],
    'royal-cut-player-view.png': [('royal_cut', [.5, .9, .95, 1.75, 1.85], None, True)],
    # Both blows from the player's eye, frame by frame through their fastest part, with the
    # in-between frames GeckoLib blends.
    'royal-cut-player-blows.png': [('royal_cut', [.9, .917, .93, .95, .97], None, True),
                                   ('royal_cut', [1.75, 1.767, 1.783, 1.8, 1.82], None, True)],
    # Raise, laid back, deepest layback, overhead, release, flight, stuck, Reel, catch.
    'cast-reel.png': [('cast_reel', [0, .4, .74, .8, .85, .9, 1.05, 1.25, 3.65, 3.8], (35, 12, 13, .4, 2.4, -4.5), False)],
    # The throw close up: three-quarter, side and the player's eye.
    'cast-reel-throw.png': [('cast_reel', [.22, .55, .74, .8, .85], None, False),
                            ('cast_reel', [.22, .55, .74, .8, .85], THROW_SIDE, False),
                            ('cast_reel', [.55, .74, .8, .85, .9], None, True)],
    # Where the sword changes hands, with the in-between frames: the release (three-quarter
    # and the player's eye), the catch, and the left fist taking hold again.
    'cast-reel-release-catch.png': [('cast_reel', [.833, .85, .867, .883, .9], None, False),
                                    ('cast_reel', [.833, .85, .867, .883, .9], None, True),
                                    ('cast_reel', [3.733, 3.75, 3.767, 3.783, 3.8], None, False),
                                    ('cast_reel', [3.733, 3.75, 3.767, 3.783, 3.8], None, True),
                                    ('cast_reel', [3.833, 3.85, 3.867, 3.883, 3.9], None, False)],
    # Kingsguard Thrust: guard, the high guard (snap), coiled, the drive, full reach, overshoot,
    # settled, the pause, stepping back; from the side and three-quarter, then the player's eye.
    'kingsguard-thrust.png': [('kingsguard_thrust', [0, .35, .8, .87, .93, 1.0, 1.2, 1.8, 2.6, 2.9], THRUST_SIDE, False),
                              ('kingsguard_thrust', [0, .35, .8, .87, .93, 1.0, 1.2, 1.8, 2.6, 2.9], THRUST_34, False)],
    'kingsguard-thrust-player.png': [('kingsguard_thrust', [.35, .8, .87, .93, 1.0], None, True)],
    # Turning Cut: guard, the glance (snap), the spin, the sweep through the half behind him,
    # the follow-through and the guard facing back; from behind him and from above, then the
    # eye of a player standing 4.5 blocks behind him.
    'turning-cut.png': [('turning_cut', [0, .25, .45, .6, .68, .72, .76, .8, .95, 2.6], TURN_BEHIND, False),
                        ('turning_cut', [0, .25, .45, .6, .68, .72, .76, .8, .95, 2.6], TURN_ABOVE, False)],
    'turning-cut-player.png': [('turning_cut', [.25, .6, .68, .72, .76], None, 'behind')],
    # Chain Scythe, low then high: the cast or the raise, the chain snapping out, the turn at
    # full chain, the haul and the catch; each from far enough to see the chain, then close.
    'chain-scythe-low.png': [('chain_scythe_low', [.2, .45, .65, .8, .9, 1.0, 1.2, 1.45, 1.6, 2.9], WHIRL_WIDE, False),
                             ('chain_scythe_low', [.2, .45, .65, .8, .9, 1.0, 1.2, 1.45, 1.6, 2.9], WHIRL_NEAR, False)],
    'chain-scythe-high.png': [('chain_scythe_high', [.2, .3, .45, .7, .9, 1.0, 1.3, 1.45, 1.6, 2.9], WHIRL_WIDE, False),
                              ('chain_scythe_high', [.2, .3, .45, .7, .9, 1.0, 1.3, 1.45, 1.6, 2.9], WHIRL_NEAR, False)],
    # Zip and Lunge from the side: the haul or the wind-up, the flight, the landing, the spin.
    'zip.png': [('zip', [.25, .5, .7, .95, 1.1, 1.25, 1.4, 1.6, 1.8, 3.2], (70, 10, 18, 0, 2.5, -5.5), False)],
    'lunge.png': [('lunge', [.3, .45, .62, .8, 1.1, 1.4, 1.55, 1.7, 1.9, 3.4], (70, 10, 14, 0, 2.5, -3.8), False)],
    # Hook: the javelin draw and throw close up, then the drag and the catch from further off.
    'hook.png': [('hook', [.2, .35, .5, .85, 1.0], (60, 8, 10, 0, 2.8, -1), False),
                 ('hook', [1.3, 1.62, 1.95, 2.3, 2.5], (75, 10, 24, 0, 2, -9), False)],
    # The overhead cut: the raise, the blade high, three frames of the cut, the hold; from the
    # side and from the player's eye. Then phase two's third Royal Cut ending on the same cut.
    'overhead-sentence.png': [('overhead_sentence', [.3, .7, .75, .79, .82, .84, 1.4, 2.6], (90, 4, 10, 0, 3, -2), False),
                              ('overhead_sentence', [.7, .79, .84], None, True)],
    'royal-cut-three.png': [('royal_cut_three', [2.2, 2.42, 2.62, 2.71, 2.76, 3.3], (40, 8, 10, 0, 3, -2), False)],
    'drag-down.png': [('drag_down', [.45, .68, .74, .8, .95, 1.08, 1.3, 1.5], (60, 8, 16, 0, 4.5, -3), False)],
    # Unbound: the cut into the floor, the blade driven in, the burst, the chains running out,
    # the sweep, hooked and taut, the reel-in, the hilt taken and the guard.
    'unbound.png': [('unbound', [.5, .64, .9, 1.25, 1.5, 1.75, 1.9, 2.4, 3.0, 3.5], UNBOUND_WIDE, False),
                    ('unbound', [.5, .64, .9, 1.25, 1.5, 1.75, 1.9, 2.4, 3.0, 3.5], UNBOUND_NEAR, False)],
    # Quarter steps of each loop (walk 1.0 s, run 0.9 s), on or next to the 20 Hz keys.
    'walk-run.png': [('walk', [0, .25, .5, .75], SIDE, False), ('run', [0, .25, .45, .7], SIDE, False),
                     ('walk', [0, .25, .5, .75], FRONT, False), ('run', [0, .25, .45, .7], FRONT, False)],
}


def strip(clip, times, cam=None, player=False):
    tex = np.array(Image.open(HERE/'candidate/pyre_king.png'))
    glow = np.array(Image.open(HERE/'candidate/pyre_king_glowmask.png'))
    fire = np.array(Image.open(HERE/'candidate/bladefire.png'))
    yaw, pitch, extent, ax, ay, az = cam or DEFAULT_CAM
    frames = []
    # In-place walk/run: the fire feels the body's travel, as in the workshop and in game.
    speed = json.loads((HERE/'candidate/manifest.json').read_text())['clips'].get(clip, {}).get('ground_speed', 0)
    travel = ['--travel', f'0,0,{-speed}'] if speed else []
    for t in times:
        scene = json.loads(subprocess.check_output(['node', str(HERE/'export_scene.cjs'), str(HERE/'candidate/pyre_king.geo.json'),
                                                    '--effects', '--clip', clip, '--time', str(t)]+travel))
        at = np.array([ax, ay, az]); y, p = map(math.radians, (yaw, pitch))
        eye = at+[math.sin(y)*math.cos(p)*20, math.sin(p)*20, -math.cos(y)*math.cos(p)*20]
        if player:      # True: 8 blocks in front of him; 'behind': 4.5 blocks behind him
            eye, at = (np.array([0, 1.6, 4.5]), np.array([0, 3.0, 0.])) if player == 'behind' else (np.array([0, 1.6, -8.]), np.array([0, 3.0, 0.]))
        title = f'{clip}  t={t:.2f}s' if abs(t*20-round(t*20)) < 1e-6 else f'{clip}  t={t:.3f}s (in-between)'
        title += ('  · player eye, 4.5 blocks behind' if player == 'behind' else '  · player eye, 8 blocks') if player else ''
        frames.append(rr.render(scene['faces']+scene['flames'], tex, glow, eye, at, 520, 620, extent=extent,
                                fov=58 if player else None, title=title, subtitle='', scale=not player,
                                fire_texture=fire, revision='CLIP'))
    cols = min(5, len(frames)); rows = math.ceil(len(frames)/cols); w, h = frames[0].size
    sheet = Image.new('RGB', (w*cols, h*rows))
    for i, frame in enumerate(frames):
        sheet.paste(frame, ((i % cols)*w, (i//cols)*h))
    return sheet


def stack(sheets):
    out = Image.new('RGB', (max(s.width for s in sheets), sum(s.height for s in sheets)))
    y = 0
    for s in sheets:
        out.paste(s, (0, y)); y += s.height
    return out


def main(args):
    if not args:
        OUT.mkdir(parents=True, exist_ok=True)
        for name, rows in STRIPS.items():
            stack([strip(*row) for row in rows]).save(OUT/name); print(OUT/name)
        return
    player = '--player' in args; args = [a for a in args if a != '--player']; cam = None
    if '--cam' in args:
        i = args.index('--cam'); cam = [float(v) for v in args[i+1].split(',')]; args = args[:i]+args[i+2:]
    out, clip, times = args[0], args[1], [float(v) for v in args[2:]]
    strip(clip, times, cam, player).save(out); print(out)


if __name__ == '__main__':
    main(sys.argv[1:])
