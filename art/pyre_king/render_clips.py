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
STRIPS = {
    'royal-cut.png': [('royal_cut', [0, .5, .9, .97, 1.05, 1.3, 1.75, 1.86, 2.2, 2.8], None, False)],
    'royal-cut-player-view.png': [('royal_cut', [.5, .9, 1.0, 1.75, 1.86], None, True)],
    'cast-reel.png': [('cast_reel', [0, .55, .8, .9, 1.05, 1.25, 2.2, 3.3, 3.65, 3.8], (35, 12, 13, .4, 2.4, -4.5), False)],
    'walk-run.png': [('walk', [0, .2, .4, .6], SIDE, False), ('run', [0, .155, .31, .465], SIDE, False)],
}


def strip(clip, times, cam=None, player=False):
    tex = np.array(Image.open(HERE/'candidate/pyre_king.png'))
    glow = np.array(Image.open(HERE/'candidate/pyre_king_glowmask.png'))
    fire = np.array(Image.open(HERE/'candidate/bladefire.png'))
    yaw, pitch, extent, ax, ay, az = cam or DEFAULT_CAM
    frames = []
    for t in times:
        scene = json.loads(subprocess.check_output(['node', str(HERE/'export_scene.cjs'), str(HERE/'candidate/pyre_king.geo.json'),
                                                    '--effects', '--clip', clip, '--time', str(t)]))
        at = np.array([ax, ay, az]); y, p = map(math.radians, (yaw, pitch))
        eye = at+[math.sin(y)*math.cos(p)*20, math.sin(p)*20, -math.cos(y)*math.cos(p)*20]
        if player:
            eye, at = np.array([0, 1.6, -8.]), np.array([0, 3.0, 0.])
        title = f'{clip}  t={t:.2f}s' + ('  · player eye, 8 blocks' if player else '')
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
