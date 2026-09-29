#!/usr/bin/env python3
"""Soul Fire Rain fireball and Soul Harvest soul: authored GeckoLib models.

Default output is review-only (candidate/ plus a browser page); --install exports the models,
animations, textures and glow masks into mod resources; --check verifies them.

    python3 art/hollow_necromancer/soul_fire/build_art.py
    python3 art/hollow_necromancer/soul_fire/build_art.py --install
    python3 art/hollow_necromancer/soul_fire/build_art.py --check

The page is served by the Soul Bolt server (port 8351) at /.local-previews/soul-fire/.
Units are model pixels (16 per block); front is -Z. Two texels per model pixel, like the
Soul Bolt, with explicit per-face UVs and a one-texel gutter.
"""
import argparse
import base64
import io
import json
import math
import random
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CANDIDATE = HERE / 'candidate'
PREVIEW = ROOT / '.local-previews' / 'soul-fire'
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'

# Ramps run dark to light. The fireball burns opaque and fully lit; the soul is a pale, cold,
# slightly see-through ghost whose eyes and mouth are hollow.
FLAME = [(16, 34, 96), (26, 66, 168), (40, 120, 220), (72, 206, 236), (170, 248, 255), (240, 255, 255)]
GHOST = [(22, 92, 112), (60, 170, 186), (120, 222, 220), (190, 246, 242), (236, 255, 252)]
HOLLOW = (6, 22, 30)
# Per-material ramp window, glow and opacity.
MATERIALS = {
    'core': (FLAME, 3, 5, True, 255),
    'flame': (FLAME, 2, 4, True, 255),
    'flame_dark': (FLAME, 1, 3, True, 255),
    'flame_tip': (FLAME, 0, 2, True, 255),
    'spark': (FLAME, 4, 5, True, 255),
    'ghost': (GHOST, 2, 4, True, 215),
    'ghost_dim': (GHOST, 1, 3, True, 190),
    'ghost_tip': (GHOST, 0, 2, True, 150),
    'hollow': ([HOLLOW], 0, 0, False, 255),
}


class Model:
    def __init__(self, name):
        self.name, self.bones, self.cubes = name, [], []

    def bone(self, name, pivot, parent=None):
        out = {'name': name, 'pivot': list(pivot), 'cubes': []}
        if parent:
            out['parent'] = parent
        self.bones.append(out)
        return out

    def cube(self, bone, center, size, material, rotation=None):
        raw = {'origin': [round(c - s / 2, 3) for c, s in zip(center, size)], 'size': list(size)}
        if rotation:
            raw.update(rotation=list(rotation), pivot=list(center))
        bone['cubes'].append(raw)
        self.cubes.append((raw, material))


# ---------------- Fireball: a white-hot core inside a cloud of flickering flame blocks ----------------
fire = Model('soul_fireball')
ball = fire.bone('ball', [0, 0, 0])
# A rounded core: a cube and three crossed slabs.
fire.cube(ball, (0, 0, 0), (7, 7, 7), 'core')
for size in ((9, 5, 5), (5, 9, 5), (5, 5, 9)):
    fire.cube(ball, (0, 0, 0), size, 'core')
# Flame blocks spread over the core on a Fibonacci sphere, stretched backward (+Z) so the
# fire streams behind it; the core shows between them. Each block flickers on its own bone.
rng = random.Random(41)
CLOUD = 16
cloud = []
for i in range(CLOUD):
    y = 1 - 2 * (i + .5) / CLOUD
    r, a = math.sqrt(1 - y * y), i * 2.39996
    x, z = math.cos(a) * r, math.sin(a) * r
    center = (round(x * 4.7, 2), round(y * 4.7, 2), round(z * 5.4 + 1.4, 2))
    size = round(2.5 + rng.random() * 1.4, 2)
    flame = fire.bone(f'flame_{i}', center, 'ball')
    fire.cube(flame, center, (size, size, size), 'flame' if center[2] < 3 else 'flame_dark',
              (rng.uniform(-30, 30), rng.uniform(-30, 30), rng.uniform(-30, 30)))
    cloud.append((center, rng.random() * 2 * math.pi))
# Short flame licks around the rear rim: a thick base and a small tip, pointing back and a
# little outward. They flicker in length rather than sway, so they read as fire, not limbs.
LICKS = 7
for i in range(LICKS):
    a = i * 2 * math.pi / LICKS + .3
    x, y = 3 * math.cos(a), 3 * math.sin(a)
    base = fire.bone(f'lick_{i}', [x, y, 3.5], 'ball')
    fire.cube(base, (x * 1.15, y * 1.15, 5.3), (3.4, 3.4, 3.6), 'flame', (rng.uniform(-15, 15), rng.uniform(-15, 15), rng.uniform(-30, 30)))
    tip = fire.bone(f'lick_{i}_tip', [x * 1.3, y * 1.3, 7], f'lick_{i}')
    fire.cube(tip, (x * 1.45, y * 1.45, 8.5), (2, 2, 3), 'flame_dark', (rng.uniform(-15, 15), rng.uniform(-15, 15), rng.uniform(-30, 30)))
plume = fire.bone('plume', [0, 0, 5], 'ball')
fire.cube(plume, (0, 0, 7.2), (4.4, 4.4, 4.4), 'flame', (0, 0, 20))
plume_tip = fire.bone('plume_tip', [0, 0, 9.4], 'plume')
fire.cube(plume_tip, (0, 0, 11.2), (2.8, 2.8, 3.6), 'flame_dark', (0, 0, 55))
# Pieces of flame tear off the back and shrink away as they fall behind.
SHEDS = 6
for i in range(SHEDS):
    shed = fire.bone(f'shed_{i}', [0, 0, 0], 'ball')
    fire.cube(shed, (0, 0, 0), (2, 2, 2), 'flame_dark' if i % 2 else 'flame', (30, 45, 0))
SPARKS = 5
for i in range(SPARKS):
    spark = fire.bone(f'spark_{i}', [0, 0, 0], 'ball')
    fire.cube(spark, (0, 0, 0), (1, 1, 1), 'spark')

# ---------------- Harvest soul: a rounded, hollow-eyed head streaming a waving tail ----------------
soul = Model('harvest_soul')
body = soul.bone('soul', [0, 0, 0])
head = soul.bone('head', [0, 11, 0], 'soul')
soul.cube(head, (0, 11, 0), (7, 7, 6), 'ghost')
for size in ((8.4, 5, 5), (5, 8.4, 5), (5, 5, 7.2)):
    soul.cube(head, (0, 11, 0), size, 'ghost')
for side in (-1, 1):
    # Hollow eyes, set just proud of the face so they never flicker against it.
    soul.cube(head, (side * 1.6, 12, -3.7), (1.6, 2.2, .2), 'hollow')
    # Wisps curling back from the sides of the head.
    wisp = soul.bone(f'wisp_{"left" if side > 0 else "right"}', [side * 4, 10, 1], 'head')
    soul.cube(wisp, (side * 4.4, 9.2, 3), (1.4, 2, 4), 'ghost_dim', (15, side * 20, 0))
mouth = soul.bone('mouth', [0, 9.6, -3.7], 'head')
soul.cube(mouth, (0, 8.9, -3.7), (2, 1.6, .2), 'hollow')
tail = [((0, 9, 2.5), (0, 8.3, 5), (5, 4.5, 5), 'ghost_dim'), ((0, 8, 7.2), (0, 7.4, 9.2), (3.6, 3.4, 4.4), 'ghost_dim'),
        ((0, 7.2, 11.2), (0, 7, 13), (2.6, 2.4, 4), 'ghost_tip'), ((0, 7, 14.8), (0, 7.3, 16.5), (1.6, 1.6, 3.6), 'ghost_tip')]
parent = 'soul'
for i, (pivot, center, size, material) in enumerate(tail):
    segment = soul.bone(f'tail_{i}', pivot, parent)
    soul.cube(segment, center, size, material)
    parent = f'tail_{i}'


# ---------------- Animations ----------------
def keys(frames):
    return {f'{t:.4f}': [round(v, 3) for v in value] for t, value in frames}


def wave(length, amplitude, phase, steps=8, base=(0, 0, 0), axis=1):
    out = []
    for k in range(steps + 1):
        t = length * k / steps
        value = list(base)
        value[axis] += amplitude * math.sin(2 * math.pi * k / steps + phase)
        out.append((t, value))
    return keys(out)


FLIGHT = .6
fire_bones = {'ball': {'scale': keys([(0, (1, 1, 1)), (.15, (1.05, 1.05, 1.05)), (.3, (.97, .97, .97)), (.45, (1.04, 1.04, 1.04)), (.6, (1, 1, 1))])}}
for i, (center, phase) in enumerate(cloud):
    # Each flame block swells and gutters on its own beat, rocking a little as it burns.
    fire_bones[f'flame_{i}'] = {
        'scale': keys([(FLIGHT * k / 6, (1 + .22 * math.sin(2 * math.pi * k / 6 + phase),) * 3) for k in range(7)]),
        'rotation': wave(FLIGHT, 12, phase, steps=6, axis=i % 3)}
for i in range(LICKS):
    phase = rng.random() * 2 * math.pi
    # Length flickers fast and unevenly; the tip flickers on its own beat.
    fire_bones[f'lick_{i}'] = {'scale': keys([(FLIGHT * k / 6, (1, 1, .7 + .35 * (1 + math.sin(2 * math.pi * k / 6 + phase)) * .9)) for k in range(7)]),
                               'rotation': wave(FLIGHT, 5, phase, steps=6, axis=i % 2)}
    fire_bones[f'lick_{i}_tip'] = {'scale': keys([(FLIGHT * k / 6, (.9 + .2 * math.sin(4 * math.pi * k / 6 + phase),) * 2
                                                  + (.5 + .45 * (1 + math.sin(4 * math.pi * k / 6 + phase + 1.3)),)) for k in range(7)])}
fire_bones['plume'] = {'scale': keys([(0, (1, 1, 1)), (.15, (1.05, 1.05, 1.25)), (.3, (.95, .95, .85)), (.45, (1.05, 1.05, 1.2)), (.6, (1, 1, 1))])}
fire_bones['plume_tip'] = {'scale': keys([(0, (1, 1, 1)), (.2, (.8, .8, 1.4)), (.4, (1, 1, .7)), (.6, (1, 1, 1))])}
def torn_off(begin, finish, offset, peak):
    """A flame piece that pops out at the rim, drifts back while shrinking to nothing, and only
    returns to the rim while invisible: every piece burns out instead of sliding back."""
    grow, eps, o = .1, .002, offset % FLIGHT
    phase = lambda t: ((t - o) / FLIGHT) % 1
    times = {0, FLIGHT, (o + grow * FLIGHT) % FLIGHT} | ({o - eps, o} if o > 0 else set())
    position, scale = [], []
    for t in sorted(times):
        f = 1.0 if o == 0 and t == FLIGHT else phase(t)
        size = peak * (f / grow if f < grow else (1 - f) / (1 - grow))
        position.append((t, tuple(p + (q - p) * f for p, q in zip(begin, finish))))
        scale.append((t, (size,) * 3))
    return {'position': keys(position), 'scale': keys(scale)}


for i in range(SHEDS):
    # Torn-off flame drifts back and up, burning out; each piece starts a sixth of a cycle later.
    a = i * 2 * math.pi / SHEDS + .7
    fire_bones[f'shed_{i}'] = torn_off((2.5 * math.cos(a), 2.5 * math.sin(a), 6), (5 * math.cos(a), 5 * math.sin(a) + 3, 17), FLIGHT * i / SHEDS, 1.3)
for i in range(SPARKS):
    # Embers peel off the rim and fall behind, burning out; each starts a fifth of a cycle later.
    a = i * 2 * math.pi / SPARKS + .4
    fire_bones[f'spark_{i}'] = torn_off((4.5 * math.cos(a), 4.5 * math.sin(a), 2), (7 * math.cos(a), 7 * math.sin(a) + 1.5, 18), FLIGHT * (i + .5) / SPARKS, 1.2)

DRIFT = 1.6
soul_bones = {
    'head': {'position': keys([(0, (0, 0, 0)), (.8, (0, .8, 0)), (1.6, (0, 0, 0))]),
             'rotation': wave(DRIFT, 4, 0, axis=2)},
    'mouth': {'scale': keys([(0, (1, 1, 1)), (.8, (1, 1.8, 1)), (1.6, (1, 1, 1))])},
    'wisp_left': {'rotation': wave(DRIFT, 14, 0)},
    'wisp_right': {'rotation': wave(DRIFT, 14, math.pi)},
}
for i in range(4):
    soul_bones[f'tail_{i}'] = {'rotation': keys([(DRIFT * k / 8, (6 * math.sin(2 * math.pi * k / 8 - i * .7),
                                                               (10 + 6 * i) * math.sin(2 * math.pi * k / 8 - i * .9), 0)) for k in range(9)])}

def cycle(length, pose, steps=16):
    """One loop of `pose(angle)`, sampled evenly; angle runs 0 to 2 pi over the loop."""
    return keys([(length * k / steps, pose(2 * math.pi * k / steps)) for k in range(steps + 1)])


# Dragged (the intro): torn from a body and hauled backward by the chest into the staff. The
# entity faces back the way it came (model -Z toward its origin), so the pull runs along +Z: the
# tail leads in, stretched thin along the path, while the head strains back toward where it
# came from, jaw wide, wisps clawing after it.
DRAG = .8
dragged_bones = {
    'soul': {'rotation': cycle(DRAG, lambda a: (3 * math.sin(2 * a), 6 * math.sin(a), 5 * math.sin(a + 1))),
             'scale': cycle(DRAG, lambda a: (.86 - .04 * math.sin(2 * a), .9, 1.28 + .08 * math.sin(2 * a)))},
    'head': {'position': cycle(DRAG, lambda a: (.3 * math.sin(3 * a), .4 * math.sin(2 * a), -1 - .4 * math.sin(2 * a))),
             'rotation': cycle(DRAG, lambda a: (-18 + 5 * math.sin(2 * a), 9 * math.sin(4 * a), 7 * math.sin(3 * a + .5)))},
    'mouth': {'scale': cycle(DRAG, lambda a: (1.25, 2.3 + .35 * math.sin(4 * a), 1))},
}
for side, sign in (('left', 1), ('right', -1)):
    # Swung round from trailing to reaching forward, flailing out of step with each other.
    shift = 0 if sign > 0 else math.pi
    dragged_bones[f'wisp_{side}'] = {'rotation': cycle(DRAG, lambda a, s=sign, p=shift: (
        25 * math.sin(2 * a + p), s * (135 + 30 * math.sin(2 * a + 1 + p)), s * 20 * math.sin(2 * a + p)))}
for i in range(4):
    # Each segment stretches along the pull and whips faster than in the drift, the tip widest.
    dragged_bones[f'tail_{i}'] = {
        'rotation': cycle(DRAG, lambda a, i=i: (8 * math.sin(2 * a - i * .9), (14 + 8 * i) * math.sin(2 * a - i * 1.1), 0)),
        'scale': keys([(0, (.85, .85, 1.35 if i == 0 else 1.1)), (DRAG, (.85, .85, 1.35 if i == 0 else 1.1))])}


def eased(frames):
    """Tick-timed poses, eased in and out between them and baked to one key per tick."""
    out = []
    for (t0, a), (t1, b) in zip(frames, frames[1:]):
        for t in range(t0, t1):
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            out.append((t / 20, [x + (y - x) * f for x, y in zip(a, b)]))
    return keys(out + [(frames[-1][0] / 20, list(frames[-1][1]))])


def shudder(start, stop, base, grow_from, grow_to, step=2):
    """A slow, heavy tremble around `base` whose swing grows from one amplitude to another."""
    out = []
    for t in range(start, stop, step):
        f = (t - start) / (stop - start)
        sign = 1 if (t - start) // step % 2 else -1
        out.append((t, [b + sign * (lo + (hi - lo) * f) for b, lo, hi in zip(base, grow_from, grow_to)]))
    return out


# Torn out (the intro, before `dragged`): 32 ticks from the moment the soul starts leaving the
# zombie's chest, played once and held. The entity faces forward, away from the body, and the
# renderer grows it from 0.35 to full size over the first 12 ticks. The head pushes out face
# first and opens into a scream (0-10); it strains against the body with its wisps clawing back
# toward the chest and its tail stretched taut behind it, shuddering harder and harder (10-26);
# the tail draws out to its longest and snaps, the tip whipping forward (26-32). The last pose
# is the first of `dragged`, apart from the tail's recoil, which the loop's blend carries on.
TORN = 32
start_of = lambda bone, channel: dragged_bones[bone][channel]['0.0000']
torn_bones = {
    'soul': {'rotation': eased([(0, (0, 0, 0)), (10, (0, 0, 0))] + shudder(12, 28, (0, 0, 0), (.4, 0, .8), (1.5, 0, 3)) +
                               [(28, (-2, 0, 0)), (30, (-3, 0, 1)), (TORN, start_of('soul', 'rotation'))]),
             'scale': eased([(0, (1, 1, 1)), (10, (.97, .97, 1.03)), (26, (.94, .95, 1.07)), (30, (.9, .92, 1.1)),
                             (TORN, start_of('soul', 'scale'))])},
    # Face down as it breaches, then lifted and a little tipped so the face reads.
    'head': {'position': eased([(0, (0, -.5, -1)), (10, (0, 0, -1.2)), (26, (0, .2, -1.8)), (30, (0, .3, -2.6)),
                                (31, (0, .2, -3.2)), (TORN, start_of('head', 'position'))]),
             'rotation': eased([(0, (22, 0, 0)), (6, (6, 0, 2)), (10, (-6, 0, 6))] +
                               shudder(12, 26, (-8, 0, 7), (1.5, 1.5, 1), (4, 5, 3.5), 3) +
                               [(26, (-10, 0, 8)), (28, (-12, 4, 5)), (30, (-14, -4, 8)), (31, (-26, 0, 4)),
                                (TORN, start_of('head', 'rotation'))])},
    'mouth': {'scale': eased([(0, (1, .8, 1)), (4, (1.1, 1.3, 1)), (10, (1.25, 2.2, 1))] +
                             shudder(12, 26, (1.25, 2.25, 1), (0, .08, 0), (0, .2, 0), 3) +
                             [(26, (1.3, 2.4, 1)), (30, (1.35, 2.6, 1)), (TORN, start_of('mouth', 'scale'))])},
}
for side, sign in (('left', 1), ('right', -1)):
    # Tucked back as it breaches, then clawing back toward the chest in turn, harder each time;
    # flung forward by the snap into the loop's reaching pose.
    lag = 0 if sign > 0 else 2
    claws = [(10 + lag + 4 * k, (-18 - 4 * k, sign * (18 + 4 * k), 0) if k % 2 == 0 else (16 + 3 * k, -sign * (10 + 2 * k), 0))
             for k in range(4)]
    torn_bones[f'wisp_{side}'] = {'rotation': eased([(0, (0, -sign * 20, 0)), (8, (0, -sign * 12, 0))] + claws +
                                                    [(28, (-34, sign * 34, 0)), (30, (-10, sign * 20, 0)), (31, (0, sign * 90, 0)),
                                                     (TORN, start_of(f'wisp_{side}', 'rotation'))])}
for i in range(4):
    # Straight and taut behind it, humming rather than flailing, thinned and drawn a little
    # longer (its tip stays about inside the chest it comes from); at the snap the tip whips
    # up and forward. Segments inherit tail_0's stretch, so only it lengthens.
    stretch = [(0, (.9, .9, 1)), (10, (.85, .85, 1.06 if i == 0 else 1)), (26, (.78, .78, 1.12 if i == 0 else 1)),
               (30, (.7, .7, 1.18 if i == 0 else 1.02)), (31, (.8, .8, 1.25 if i == 0 else 1.05)), (TORN, start_of(f'tail_{i}', 'scale'))]
    hum = shudder(10, 28, (0, 0, 0), (0, .3 * i, 0), (0, 1.2 * i, 0)) if i else [(10, (0, 0, 0))]
    recoil = {0: 0, 1: 12, 2: 35, 3: 70}[i]
    torn_bones[f'tail_{i}'] = {'scale': eased(stretch),
                               'rotation': eased([(0, (0, 0, 0))] + hum + [(28, (0, 0, 0)), (30, (0, 0, 0)), (31, (recoil, 0, 0)),
                                                                          (TORN, (recoil / 2, 0, 0))])}

ANIMATIONS = {
    'soul_fireball': {'animation.soul_fireball.flight': {'animation_length': FLIGHT, 'loop': True, 'bones': fire_bones}},
    'harvest_soul': {
        'animation.harvest_soul.drift': {'animation_length': DRIFT, 'loop': True, 'bones': soul_bones},
        # Clawing out of the soil: it swells from a wisp to full size with its tail hanging straight.
        'animation.harvest_soul.rise': {'animation_length': 1, 'loop': False, 'bones': {
            'soul': {'scale': keys([(0, (.25, .25, .25)), (.6, (1.08, 1.08, 1.08)), (.8, (1, 1, 1)), (1, (1, 1, 1))])},
            'head': {'rotation': keys([(0, (-25, 0, 0)), (.7, (5, 0, 0)), (1, (0, 0, 0))])},
            'tail_0': {'rotation': keys([(0, (30, 0, 0)), (1, (0, 0, 0))])},
            'mouth': {'scale': keys([(0, (1, 2, 1)), (1, (1, 1, 1))])}}},
        'animation.harvest_soul.dragged': {'animation_length': DRAG, 'loop': True, 'bones': dragged_bones},
        'animation.harvest_soul.torn_out': {'animation_length': TORN / 20, 'loop': 'hold_on_last_frame', 'bones': torn_bones},
    },
}


# ---------------- Texture ----------------
def paint(model):
    x = y = row = 0
    width = 128
    for raw, _ in model.cubes:
        w, h, d = [max(1, math.ceil(v * 2)) for v in raw['size']]
        raw['uv'] = {}
        for face, (fw, fh) in {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}.items():
            if x + fw + 2 > width:
                x, y, row = 0, y + row, 0
            raw['uv'][face] = {'uv': [x + 1, y + 1], 'uv_size': [fw, fh]}
            x += fw + 2
            row = max(row, fh + 2)
    height = 32
    while height < y + row:
        height *= 2
    texture, glow = Image.new('RGBA', (width, height)), Image.new('RGBA', (width, height))
    ink, light = texture.load(), glow.load()
    for index, (raw, material) in enumerate(model.cubes):
        ramp, low, high, glows, alpha = MATERIALS[material]
        for face, spec in raw['uv'].items():
            fx, fy = spec['uv']
            fw, fh = spec['uv_size']
            rng = random.Random(9100 + 131 * index + sum(map(ord, face)))
            for v in range(fh):
                for u in range(fw):
                    cu, cv = (u + .5) / fw - .5, (v + .5) / fh - .5
                    centre = 1 - min(1, 2 * math.hypot(cu, cv))
                    if material == 'core':
                        # White-hot in the middle of each face.
                        value = centre + (rng.random() - .5) * .45
                    else:
                        # Flame and ghost matter: two-texel clusters of light and shade, lighter on top.
                        cluster = random.Random(index * 977 + (u // 2) * 31 + (v // 2) * 17 + sum(map(ord, face))).random()
                        value = .35 + .3 * centre + .55 * (cluster - .5) + (rng.random() - .5) * .15
                    if face == 'up':
                        value += .15
                    if face == 'down':
                        value -= .15
                    step = low + max(0, min(high - low, int(round(value * (high - low)))))
                    color = (*ramp[min(step, len(ramp) - 1)], alpha)
                    ink[fx + u, fy + v] = color
                    if glows:
                        light[fx + u, fy + v] = color
            for image in (texture, glow):
                px = image.load()
                for gy in range(-1, fh + 1):
                    for gx in range(-1, fw + 1):
                        if 0 <= gx < fw and 0 <= gy < fh:
                            continue
                        px[fx + gx, fy + gy] = px[fx + min(fw - 1, max(0, gx)), fy + min(fh - 1, max(0, gy))]
    return texture, glow, width, height


def export(model):
    texture, glow, width, height = paint(model)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{model.name}', 'texture_width': width, 'texture_height': height,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, .5, 0]},
        'bones': model.bones}]}
    outputs = {
        f'geckolib/models/{model.name}.geo.json': (json.dumps(geo, indent=2) + '\n').encode(),
        f'geckolib/animations/{model.name}.animation.json': (json.dumps({'format_version': '1.8.0', 'animations': ANIMATIONS[model.name]}, indent=2) + '\n').encode(),
    }
    for suffix, image in (('', texture), ('_glowmask', glow)):
        stream = io.BytesIO()
        image.save(stream, format='PNG')
        outputs[f'textures/entity/{model.name}{suffix}.png'] = stream.getvalue()
    return outputs


def build(install=False, check=False):
    outputs = {}
    for model in (fire, soul):
        outputs.update(export(model))
    if check:
        stale = [name for name, data in outputs.items() if not (ASSETS / name).exists() or (ASSETS / name).read_bytes() != data]
        if stale:
            raise SystemExit('Soul fire export mismatch: ' + ', '.join(stale))
        print(f'Soul fire models match: {len(outputs)} files.')
        return
    CANDIDATE.mkdir(exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for name, data in outputs.items():
        (CANDIDATE / Path(name).name).write_bytes(data)
        if install:
            (ASSETS / name).parent.mkdir(parents=True, exist_ok=True)
            (ASSETS / name).write_bytes(data)
    payload = {}
    for model in (fire, soul):
        payload[model.name] = {
            'geo': json.loads(outputs[f'geckolib/models/{model.name}.geo.json']),
            'animation': ANIMATIONS[model.name],
            'texture': 'data:image/png;base64,' + base64.b64encode(outputs[f'textures/entity/{model.name}.png']).decode(),
            'glowmask': 'data:image/png;base64,' + base64.b64encode(outputs[f'textures/entity/{model.name}_glowmask.png']).decode(),
            'translucent': model is soul,
        }
    page = (HERE / 'viewer.html').read_text().replace('/*MODELS*/null', json.dumps(payload).replace('</', '<\\/'))
    (PREVIEW / 'index.html').write_text(page)
    print(f'Candidate: fireball {len(fire.cubes)} cubes, soul {len(soul.cubes)} cubes'
          + ('; installed into mod resources' if install else ''))
    print('Preview: http://127.0.0.1:8351/.local-previews/soul-fire/')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--install', action='store_true')
    modes.add_argument('--check', action='store_true')
    args = parser.parse_args()
    build(args.install, args.check)
