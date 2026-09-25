#!/usr/bin/env python3
"""Hollow Necromancer revision 02: authored geometry and hand-designed pixel textures.

Writes the candidate into the browser workshop for review; nothing reaches the mod until
`--install`. `--check` confirms the installed assets still match this script.

    python3 art/hollow_necromancer/v2/build_art.py            # refresh the workshop candidate
    python3 art/hollow_necromancer/v2/build_art.py --install  # copy art and grip socket into the mod
    python3 art/hollow_necromancer/v2/build_art.py --check    # installed assets match the build

Units are model pixels (16 per block); front is -Z and the right hand is -X.
Geometry and animation are authored in the sibling geometry.py and animations.py.

Texture orientation (GeckoLib box UV): on each side face column 0 is the viewer's left and
row 0 is the top; on top/bottom faces row 0 is the back edge.
"""
import io
import json
import math
import random
import re
import sys
from pathlib import Path

from PIL import Image
from geometry import bones, cubes
from animations import animations
from rig import hand, sample

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
WORKSHOP = HERE.parent / 'workshop'
ASSETS = REPO / 'src/main/resources/assets/elementalwands'
INSTALL = {
    'geo': ASSETS / 'geckolib/models/hollow_necromancer.geo.json',
    'animation': ASSETS / 'geckolib/animations/hollow_necromancer.animation.json',
    'texture': ASSETS / 'textures/entity/hollow_necromancer.png',
    'glowmask': ASSETS / 'textures/entity/hollow_necromancer_glowmask.png',
    'rush_socket': REPO / 'src/main/java/com/anton/elementalwands/entity/necromancer/NecromancerRushSocket.java',
    'grab_socket': REPO / 'src/main/java/com/anton/elementalwands/entity/necromancer/NecromancerGrabSocket.java',
}
REVISION = {'revision': 'Revision 02', 'date': '2026-09-24',
            'summary': 'Lean hooded mage; hunched skeleton with jointed claws, a four-limbed crawl and an outstretched lunge.'}

# ---------------- Palette: ramps run dark to light ----------------
CLOTH = [(18, 14, 25), (30, 24, 41), (43, 35, 57), (58, 48, 76), (78, 65, 99)]
LINING = [(34, 18, 34), (52, 28, 50)]
CHAR = [(10, 8, 10), (20, 16, 20), (31, 25, 32), (42, 35, 44)]
VOID = [(4, 3, 6), (9, 7, 12), (15, 12, 19)]
BONE = [(62, 53, 40), (104, 92, 70), (146, 133, 104), (184, 170, 136), (214, 202, 168), (236, 228, 202)]
WOOD = [(26, 17, 11), (40, 28, 18), (56, 40, 26), (77, 56, 37)]
LEATHER = [(38, 23, 17), (56, 36, 26), (78, 52, 36)]
METAL = [(70, 74, 84), (118, 124, 136), (170, 176, 188)]
SOUL = [(12, 58, 66), (26, 128, 138), (72, 214, 222), (170, 250, 255), (240, 255, 255)]


# ---------------- UV packing ----------------
def box_uv_size(size):
    w, h, d = (math.floor(v) for v in size)
    return 2 * d + 2 * w, d + h


def pack(width):
    order = sorted(range(len(cubes)), key=lambda i: (-box_uv_size(cubes[i]['raw']['size'])[1], -box_uv_size(cubes[i]['raw']['size'])[0], i))
    x = y = row = 0
    for i in order:
        w, h = box_uv_size(cubes[i]['raw']['size'])
        if x + w > width:
            x, y, row = 0, y + row, 0
        cubes[i]['raw']['uv'] = [x, y]
        x += w
        row = max(row, h)
    used = y + row
    height = 64
    while height < used:
        height *= 2
    return height


TEX_W = 256
TEX_H = pack(TEX_W)


# ---------------- Painting ----------------
class Face:
    def __init__(self, image, glow, x, y, w, h, direction, cube, rng):
        self.image, self.glow, self.x, self.y, self.w, self.h = image, glow, x, y, w, h
        self.dir, self.cube, self.opts, self.rng = direction, cube, cube['opts'], rng
        self.side = direction in ('north', 'south', 'east', 'west')

    def put(self, c, r, color, alpha=255, glow=None):
        if 0 <= c < self.w and 0 <= r < self.h:
            self.image[self.x + c, self.y + r] = tuple(color) + (alpha,)
            if glow is not None:
                self.glow[self.x + c, self.y + r] = tuple(glow) + (255,)

    def clear(self, c, r):
        if 0 <= c < self.w and 0 <= r < self.h:
            self.image[self.x + c, self.y + r] = (0, 0, 0, 0)
            self.glow[self.x + c, self.y + r] = (0, 0, 0, 0)


def faces_of(entry):
    u, v = entry['raw']['uv']
    w, h, d = (math.floor(s) for s in entry['raw']['size'])
    return {'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h),
            'south': (u + 2 * d + w, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}


def ramp(colors, index):
    return colors[max(0, min(len(colors) - 1, index))]


def hash2(a, b, seed=0):
    return random.Random(a * 73856093 ^ b * 19349663 ^ seed * 83492791).random()


def tatters(f, depth_max, seed):
    """Per-column hem cut depths in strips of 1-3 columns."""
    rng, depths, c = random.Random(seed), [], 0
    while len(depths) < f.w:
        strip, depth = rng.choice((1, 2, 2, 3)), rng.choice(range(depth_max + 1))
        depths.extend([depth] * strip)
    return depths[:f.w]


def paint_cloth(f, palette=CLOTH, burnt=False):
    o = f.opts
    if f.dir == 'down' and o.get('hollow'):
        return
    if f.dir == 'south' and o.get('rag'):
        return  # Single-sided plane: the back shows the front through the cutout layer.
    if not f.side and not o.get('rag'):
        base = 3 if f.dir == 'up' else 1
        for c in range(f.w):
            for r in range(f.h):
                roll = hash2(f.x + c // 2, f.y + r, 5)
                f.put(c, r, ramp(palette, base + (1 if roll > .86 else -1 if roll < .14 else 0)))
        return
    rng = f.rng
    # Folds: a lit ridge, then a shadowed trough, every two to four columns.
    fold = [0] * f.w
    c = rng.randrange(1, 3)
    while c < f.w:
        fold[c] = -1
        if c > 0 and rng.random() < .5:
            fold[c - 1] = 1
        c += rng.choice((3, 4, 4, 5))
    hem = tatters(f, o.get('hem', 0), f.x * 31 + f.y) if o.get('hem') else [0] * f.w
    if o.get('rag'):
        hem = tatters(f, max(3, f.h // 3), f.x * 17 + f.y)
    for c in range(f.w):
        for r in range(f.h):
            cut = f.h - hem[c]
            if r >= cut:
                continue
            tone = 2 + fold[c]
            if o.get('shade_low') and r >= f.h * .6:
                tone -= 1
            if o.get('hood') and r == 0:
                tone += 1
            if o.get('brow') and r == f.h - 1:
                tone += 1  # Lit lower edge of the overhang.
            roll = hash2(f.x + c, f.y + r // 2, 11)
            if roll > .93:
                tone += 1
            elif roll < .05:
                tone -= 1
            color = ramp(palette, tone)
            glow = None
            if r == cut - 1 and hem[c] > 0:
                color = ramp(palette, 0)  # Frayed edge.
                if burnt:
                    color, glow = SOUL[1], SOUL[1] if roll > .35 else SOUL[0]
            f.put(c, r, color, glow=glow)
    if burnt:
        for _ in range(max(1, f.w * f.h // 60)):  # Burn-through holes with glowing rims.
            hx, hy = rng.randrange(1, max(2, f.w - 1)), rng.randrange(1, max(2, f.h - 3))
            f.clear(hx, hy)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c, r = hx + dx, hy + dy
                if 0 <= c < f.w and 0 <= r < f.h and f.image[f.x + c, f.y + r][3] and rng.random() < .6:
                    f.put(c, r, SOUL[1], glow=SOUL[0])
    if o.get('runes') and f.side:
        band = f.h - max(hem) - 1  # Embroidered soul-thread band just above the hem.
        for c in range(f.w):
            f.put(c, band, SOUL[0], glow=(7, 34, 38))
            if (c + f.x) % 5 == 2:
                f.put(c, band - 1, SOUL[0], glow=(7, 34, 38))
    if o.get('belt'):
        pass
    if o.get('mantle') and f.side:
        for c in range(f.w):  # Stitched seam along the top.
            if c % 2 == 0:
                f.put(c, 1, ramp(palette, 1))


def paint_hood_opening(f):
    """Front of the hood shell: rim, lining, and an open face showing the void head behind."""
    for c in range(f.w):
        for r in range(f.h):
            rim = c in (0, f.w - 1) or r == 0 or r == f.h - 1
            lining = c in (1, f.w - 2) or r in (1, f.h - 2)
            if rim:
                f.put(c, r, CLOTH[4] if r == 0 or c == 0 else CLOTH[3])
            elif lining:
                f.put(c, r, LINING[1] if r == 1 else LINING[0])
            else:
                f.clear(c, r)


def paint_void(f):
    for c in range(f.w):
        for r in range(f.h):
            f.put(c, r, VOID[0] if hash2(f.x + c, f.y + r, 3) < .6 else VOID[1])
    if f.dir == 'north' and f.opts.get('front') == 'hollow_face':
        eye_r = f.h // 2 - 1
        for c in (max(0, f.w // 2 - 1), min(f.w - 1, f.w // 2 + 1)):
            f.put(c, eye_r, SOUL[2], glow=SOUL[3])
        for c in range(f.w // 2 - 2, f.w // 2 + 3):  # The skull within, barely there.
            f.put(c, eye_r + 3, BONE[0] if c % 2 == 0 else VOID[0])


def paint_bone(f):
    o = f.opts
    base = {'up': 4, 'down': 2}.get(f.dir, 3)
    for c in range(f.w):
        for r in range(f.h):
            tone = base
            mottle = hash2((f.x + c) // 4, (f.y + r) // 3, 7)
            if mottle > .9:
                tone += 1
            elif mottle < .12:
                tone -= 1
            if f.side and f.h >= 3:
                if r == 0:
                    tone += 1
                elif r >= f.h - max(1, f.h // 5):
                    tone -= 1  # Shadowed underside.
                if f.w >= 5 and c in (0, f.w - 1):
                    tone -= 1
            if hash2(f.x + c, f.y + r, 17) < .015:
                tone -= 1  # Pores.
            f.put(c, r, ramp(BONE, tone))
    if o.get('cracks') and f.w * f.h >= 24:
        rng = f.rng
        for _ in range(max(1, f.w * f.h // 150)):
            c, r = rng.randrange(f.w), rng.randrange(f.h)
            for _ in range(rng.randrange(3, 9)):
                f.put(c, r, BONE[1])
                if c + 1 < f.w:
                    f.put(c + 1, r, BONE[4])
                r += 1
                c += rng.choice((-1, 0, 0, 1))
                if not (0 <= c < f.w and 0 <= r < f.h):
                    break
    if o.get('knuckles') and f.dir == 'up':
        for c in range(0, f.w, 2):
            f.put(c, f.h - 1, BONE[1])
    if o.get('claw'):
        for c in range(f.w):
            for r in range(f.h):
                f.put(c, r, ramp(BONE, 1 + (r == 0)))
    if o.get('etch') and f.dir == 'east' and f.w >= 5 and f.h >= 12:
        c0, rng = f.w // 2, random.Random(f.x * 7 + f.y)
        for r in range(2, f.h - 2):  # A faint soul-etched line with glyph notches.
            f.put(c0, r, SOUL[0], glow=(10, 50, 56))
            if r % 4 == 0:
                f.put(c0 + rng.choice((-1, 1)), r, SOUL[0], glow=(10, 50, 56))
    front = o.get('front')
    if f.dir != 'north' or not front:
        return
    if front == 'charm_skull':
        f.put(0, 0, VOID[0]); f.put(1, 0, VOID[0]); f.put(0, 1, BONE[2]); f.put(1, 1, BONE[3])
    elif front == 'staff_skull':
        f.put(0, 1, VOID[0], glow=SOUL[1]); f.put(2, 1, VOID[0], glow=SOUL[1])
        f.put(1, 1, BONE[2])
        for c in range(3):
            f.put(c, 2, BONE[4] if c % 2 == 0 else VOID[0])
    elif front == 'skull_face':
        paint_skull_face(f)
    elif front == 'upper_teeth':
        for c in range(f.w):
            for r in range(f.h - 2, f.h):
                f.put(c, r, (BONE[5] if r == f.h - 2 else BONE[3]) if c % 2 == 0 else VOID[0])
            f.put(c, 0, BONE[2])
        for c in range(f.w // 2 - 1, f.w // 2 + 1):  # Nasal cavity continues down.
            f.put(c, 0, VOID[0])
    elif front == 'lower_teeth':
        for c in range(f.w):
            f.put(c, 0, BONE[5] if c % 2 else VOID[0])


def paint_skull_face(f):
    """Cranium front (18x14): deep sockets with small soul pupils, nasal cavity, cheek shadow."""
    w, h = f.w, f.h
    for x0 in (2, w - 8):
        for c in range(6):
            for r in range(5):
                if c in (0, 5) and r in (0, 4):
                    continue
                edge = c in (0, 5) or r in (0, 4)
                f.put(x0 + c, 4 + r, BONE[1] if edge and r == 0 else BONE[2] if edge else VOID[0])
        for c in (2, 3):
            f.put(x0 + c, 6, SOUL[2], glow=SOUL[3])
            f.glow[f.x + x0 + c, f.y + 7] = SOUL[0] + (255,)
    for c in range(w):
        f.put(c, 3, BONE[2])  # Shadow under the brow ridge.
    for c, r in ((w // 2 - 1, h - 5), (w // 2, h - 5), (w // 2 - 1, h - 4), (w // 2, h - 4),
                 (w // 2 - 2, h - 3), (w // 2 + 1, h - 3), (w // 2 - 1, h - 3), (w // 2, h - 3)):
        f.put(c, r, VOID[0])
    for c in range(w):
        if not w // 2 - 3 < c < w // 2 + 2:
            f.put(c, h - 2, BONE[2])
            if c < 2 or c > w - 3:
                f.put(c, h - 3, BONE[2])


def paint_wood(f):
    o = f.opts
    for c in range(f.w):
        for r in range(f.h):
            grain = hash2(f.x + c, (f.y + r) // 3, 19)
            tone = 1 + (1 if grain > .6 else 0) + (1 if grain > .9 else 0)
            if hash2(f.x + c, f.y + r, 23) < .06:
                tone = 0
            if o.get('knot'):
                tone = 0 if (c + r) % 3 == 0 else 2
            f.put(c, r, ramp(WOOD, tone))
    if f.side and f.h >= 8:
        for r in range(3, f.h - 2, 5):
            f.put(0, r, WOOD[0]); f.put(0, r - 1, WOOD[3])


def paint_leather(f):
    o = f.opts
    for c in range(f.w):
        for r in range(f.h):
            tone = 1
            if o.get('wrap') and (c + r) % 3 == 0:
                tone = 0
            elif o.get('belt'):
                tone = 0 if c % 3 else 1
            f.put(c, r, ramp(LEATHER, tone))
    if o.get('belt') and f.dir == 'north':
        mid = f.w // 2
        for c in (mid - 1, mid):
            for r in range(f.h):
                f.put(c, r, METAL[1] if r == 0 else METAL[0])
        f.put(mid - 1, 0, METAL[2])


def paint_soul(f):
    cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
    for c in range(f.w):
        for r in range(f.h):
            dist = math.hypot(c - cx, r - cy) / max(1, max(cx, cy))
            swirl = hash2(f.x + c, f.y + r, 29)
            tone = 4 - int(dist * 3.2) - (1 if swirl < .25 else 0)
            color = ramp(SOUL, max(1, tone))
            f.put(c, r, color, glow=color)


def paint_vial(f):
    for c in range(f.w):
        for r in range(f.h):
            if r == 0:
                f.put(c, r, LEATHER[2])
            else:
                f.put(c, r, SOUL[2] if r > 0 else SOUL[1], glow=SOUL[1])


PAINTERS = {'cloth': paint_cloth, 'char': lambda f: paint_cloth(f, CHAR, burnt=True), 'void': paint_void,
            'bone': paint_bone, 'wood': paint_wood, 'leather': paint_leather, 'soul': paint_soul, 'soul_vial': paint_vial}


def texture():
    image = Image.new('RGBA', (TEX_W, TEX_H), (0, 0, 0, 0))
    glow = Image.new('RGBA', (TEX_W, TEX_H), (0, 0, 0, 0))
    px, gx = image.load(), glow.load()
    for index, entry in enumerate(cubes):
        for direction, (x, y, w, h) in faces_of(entry).items():
            if w <= 0 or h <= 0:
                continue
            face = Face(px, gx, x, y, w, h, direction, entry, random.Random(index * 1009 + len(direction) * 7 + x))
            if direction == 'north' and entry['opts'].get('front') == 'hood_opening':
                paint_hood_opening(face)
            elif direction == 'north' and entry['opts'].get('front') == 'open':
                continue
            else:
                PAINTERS[entry['material']](face)
    return image, glow


def grab_socket(data, rush=False):
    clip = data['animations']['animation.hollow_necromancer.colossus_bite_throw' if rush else 'animation.hollow_necromancer.colossus_grab']
    rows = []
    for tick in range(45 if rush else 65):
        p = hand(clip, tick / 20) / 16
        rows.append('        {' + ', '.join(f'{v:.6f}' for v in [-p[0], p[1], p[2]]) + '}')
    return ('''package com.anton.elementalwands.entity.necromancer;

import net.minecraft.util.math.Vec3d;

/** Generated from the V2 animated wrist by art/hollow_necromancer/v2/build_art.py. */
final class NecromancerGrabSocket {
    private static final double[][] POSES = {
''' + ',\n'.join(rows) + '''
    };

    static Vec3d worldPosition(NecromancerEntity boss, int tick) {
        double[] p = POSES[Math.clamp(tick, 0, POSES.length - 1)];
        double yaw = Math.toRadians(boss.getBodyYaw());
        return boss.getEntityPos().add(p[0] * Math.cos(yaw) + p[2] * Math.sin(yaw), p[1],
                p[0] * Math.sin(yaw) - p[2] * Math.cos(yaw));
    }
}
''').replace('NecromancerGrabSocket', 'NecromancerRushSocket' if rush else 'NecromancerGrabSocket').encode()


def validate(data):
    names = {b['name'] for b in bones}
    assert len(names) == len(bones), 'Duplicate rig joint'
    for b in bones:
        assert not b.get('parent') or b['parent'] in names, 'Missing parent'
    for name, clip in data['animations'].items():
        assert set(clip['bones']).issubset(names), f'Missing bone in {name}'
        for channels in clip['bones'].values():
            for frames in channels.values():
                assert all(0 <= float(t) <= clip['animation_length'] for t in frames), name
                assert all(math.isfinite(v) for frame in frames.values() for v in frame), name
    assert data['animations']['animation.hollow_necromancer.transform']['animation_length'] == 8
    assert 'shoulder_rag' not in names, 'Dark shoulder panel returned'
    for name, time in [('colossus_idle', 0), ('colossus_walk', 0),
                       ('transform', 5.5), ('colossus_lunge', 1.3)]:
        clip = data['animations']['animation.hollow_necromancer.' + name]
        right = sample(clip['bones']['arm_right']['rotation'], time, [0, 0, 0])[2]
        left = sample(clip['bones']['arm_left']['rotation'], time, [0, 0, 0])[2]
        assert right > 0 and left < 0, f'{name}: elbows folded in the same direction'
    for name in ('transform', 'colossus_idle', 'colossus_walk', 'colossus_roar',
                 'colossus_swipe', 'colossus_grab', 'colossus_lunge'):
        channel = data['animations']['animation.hollow_necromancer.' + name]['bones']['arm_left']['rotation']
        poses = sorted((float(t), v) for t, v in channel.items())
        assert all(math.dist(a, b) < 20 for (_, a), (_, b) in zip(poses, poses[1:])), f'{name}: left elbow snapped'
    for name, time in [('transform', 4.8), ('transform', 5.5), ('transform', 8),
                       ('colossus_idle', 0), ('colossus_walk', 0),
                       ('colossus_lunge', .8), ('colossus_lunge', 1.7), ('colossus_lunge', 2.5)]:
        for side in ['right', 'left']:
            height = hand(data['animations']['animation.hollow_necromancer.' + name], time, side)[1]
            assert 3 <= height <= 10, f'{name}: planted {side} wrist left the floor: {height}'
    for time in (1.3, 1.5):
        for side in ('right', 'left'):
            wrist = hand(data['animations']['animation.hollow_necromancer.colossus_lunge'], time, side)
            assert wrist[2] <= -55 and wrist[1] >= 15, f'Lunge {side} arm is tucked during flight: {wrist}'


def outputs():
    image, glow = texture()
    files = {}
    for name, img in (('texture', image), ('glowmask', glow)):
        buffer = io.BytesIO()
        img.save(buffer, format='PNG', optimize=False)
        files[name] = buffer.getvalue()
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.hollow_necromancer', 'texture_width': TEX_W, 'texture_height': TEX_H,
                        'visible_bounds_width': 10, 'visible_bounds_height': 9, 'visible_bounds_offset': [0, 4, 0]},
        'bones': bones}]}
    files['geo'] = (json.dumps(geo, indent=2) + '\n').encode()
    animation = animations()
    validate(animation)
    # Keep sampled vectors on one line so generated animation diffs remain readable.
    encoded = json.dumps(animation, indent=2)
    encoded = re.sub(r'\[\s*([-0-9.eE,\s]+)\]', lambda m: '[' + re.sub(r'\s+', '', m[1]).replace(',', ', ') + ']', encoded)
    files['animation'] = (encoded + '\n').encode()
    files['grab_socket'] = grab_socket(animation)
    files['rush_socket'] = grab_socket(animation, True)
    return files


def main():
    files = outputs()
    if '--check' in sys.argv:
        stale = [str(path.relative_to(REPO)) for name, path in INSTALL.items() if not path.exists() or path.read_bytes() != files[name]]
        if stale:
            sys.exit('Stale Hollow Necromancer art: ' + ', '.join(stale))
        print('Hollow Necromancer art matches.')
        return
    candidate = WORKSHOP / 'candidate'
    candidate.mkdir(parents=True, exist_ok=True)
    for name, path in INSTALL.items():
        if not name.endswith('_socket'):
            (candidate / path.name).write_bytes(files[name])
    (candidate / 'manifest.json').write_text(json.dumps({**REVISION, 'texture': [TEX_W, TEX_H], 'cubes': len(cubes), 'bones': len(bones)}, indent=2) + '\n')
    print(f'Candidate written: {len(cubes)} cubes, {TEX_W}x{TEX_H} texture.')
    if '--install' in sys.argv:
        installed = WORKSHOP / 'installed'
        installed.mkdir(parents=True, exist_ok=True)
        for name, path in INSTALL.items():
            path.write_bytes(files[name])
            if not name.endswith('_socket'):
                (installed / path.name).write_bytes(files[name])
        print('Installed into the mod resources.')


if __name__ == '__main__':
    main()
