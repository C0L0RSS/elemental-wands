#!/usr/bin/env python3
"""Placeholder Hollow Necromancer: stepped cuboids, box UVs, a 64x64 texture and six clips.

Units are Minecraft model pixels (16 per block); front is negative Z; the right hand is -X.
Bone rotations follow Bedrock/GeckoLib: positive X leans a bone forward, negative X raises
an arm forward. This stands in until the authored phase-one art replaces it.

    python3 art/hollow_necromancer/v0-placeholder/build_placeholder.py [--check]
"""
import io
import json
import random
import sys
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parents[3]
ASSETS = REPO / 'src/main/resources/assets/elementalwands'
OUTPUTS = {
    'model': ASSETS / 'geckolib/models/hollow_necromancer.geo.json',
    'animation': ASSETS / 'geckolib/animations/hollow_necromancer.animation.json',
    'texture': ASSETS / 'textures/entity/hollow_necromancer.png',
    'glowmask': ASSETS / 'textures/entity/hollow_necromancer_glowmask.png',
}
SIZE = 64

# Palette: three value steps per material, chosen clusters rather than noise.
ROBE = [(29, 24, 36), (43, 36, 54), (60, 50, 74)]
HOOD = [(36, 30, 45), (52, 44, 65), (70, 60, 88)]
VOID = [(8, 7, 11), (12, 10, 16), (16, 13, 21)]
BONE = [(150, 140, 112), (190, 180, 150), (222, 214, 188)]
WOOD = [(46, 32, 22), (64, 46, 31), (84, 62, 42)]
EYE = (140, 245, 255)

bones = []
paint = []  # (u, v, size, material, front_override)


def bone(name, pivot, parent=None, rotation=None):
    entry = {'name': name, 'pivot': pivot, 'cubes': []}
    if parent:
        entry['parent'] = parent
    if rotation:
        entry['rotation'] = rotation
    bones.append(entry)
    return entry


def cube(owner, center, size, uv, material, front=None):
    origin = [c - s / 2 for c, s in zip(center, size)]
    owner['cubes'].append({'origin': origin, 'size': list(size), 'uv': list(uv)})
    paint.append((uv, size, material, front))


root = bone('root', [0, 0, 0])
robe = bone('robe', [0, 0, 0], 'root')
cube(robe, [0, 4, 0.5], [10, 8, 9], [0, 16], ROBE)
body = bone('body', [0, 8, 1], 'robe', [28, 0, 0])
cube(body, [0, 12.5, 1], [8, 9, 6], [0, 33], ROBE)
hood = bone('hood', [0, 17, 0.5], 'body')
cube(hood, [0, 20.5, 0], [8, 8, 8], [0, 0], HOOD, front='face')
cube(hood, [0, 24.5, 1.5], [4, 2, 3], [38, 24], HOOD)
right_arm = bone('right_arm', [-5, 16, 1], 'body', [-40, 0, 0])
cube(right_arm, [-5.5, 12, 1], [3, 8, 3], [32, 0], ROBE)
cube(right_arm, [-5.5, 7, 1], [2, 2, 2], [32, 11], BONE)
staff = bone('staff', [-5.5, 7, 1], 'right_arm', [40, 0, 0])
cube(staff, [-5.5, 11, 1], [1, 26, 1], [56, 0], WOOD)
cube(staff, [-5.5, 25.5, 1], [3, 3, 3], [38, 17], BONE, front='focus')
left_arm = bone('left_arm', [5, 16, 1], 'body', [-55, 0, 0])
cube(left_arm, [5.5, 12, 1], [3, 8, 3], [44, 0], ROBE)
cube(left_arm, [5.5, 7, 1], [2, 2, 2], [40, 11], BONE)

geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': 'geometry.hollow_necromancer', 'texture_width': SIZE,
                    'texture_height': SIZE, 'visible_bounds_width': 3,
                    'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.2, 0]},
    'bones': bones}]}


def texture():
    image = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    glow = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px, gx = image.load(), glow.load()
    for (u, v), (w, h, d), material, front in paint:
        for x in range(u, u + 2 * d + 2 * w):
            for y in range(v, v + d + h):
                # Two-pixel clusters keep the dither readable at native scale.
                roll = random.Random((x // 2) * 7919 + (y // 2) * 104729 + u * 31 + v).random()
                shade = 2 if roll > .82 else 0 if roll < .3 else 1
                if y >= v + d and y - v - d >= h - 1:
                    shade = 0  # Hem and cuff shadow.
                px[x, y] = material[shade] + (255,)
        if front:
            # North face: x from u+d to u+d+w, y from v+d to v+d+h.
            fx, fy = u + d, v + d
            if front == 'face':
                for x in range(fx + 1, fx + w - 1):
                    for y in range(fy + 2, fy + h):
                        px[x, y] = VOID[0 if (x + y) % 3 else 1] + (255,)
                for x in (fx + 2, fx + w - 3):
                    px[x, fy + 4] = EYE + (255,)
                    gx[x, fy + 4] = EYE + (255,)
            else:
                cx, cy = fx + w // 2, fy + h // 2
                px[cx, cy] = EYE + (255,)
                gx[cx, cy] = EYE + (255,)
    return image, glow


def key(values):
    return {f'{time:g}': list(value) for time, value in values}


def animations():
    clips = {}

    def clip(name, length, loop, channels):
        clips[f'animation.hollow_necromancer.{name}'] = {
            'loop': loop, 'animation_length': length,
            'bones': {bone_name: {channel: key(frames) for channel, frames in chans.items()}
                      for bone_name, chans in channels.items()}}

    clip('idle', 2.4, True, {
        'body': {'rotation': [(0, (0, 0, 0)), (1.2, (3, 0, 0)), (2.4, (0, 0, 0))]},
        'hood': {'rotation': [(0, (0, 0, 0)), (1.2, (-4, 3, 0)), (2.4, (0, 0, 0))]},
        'left_arm': {'rotation': [(0, (0, 0, 0)), (1.2, (-6, 0, -4)), (2.4, (0, 0, 0))]},
        'robe': {'position': [(0, (0, 0, 0)), (1.2, (0, -0.3, 0)), (2.4, (0, 0, 0))]},
    })
    clip('walk', 1.0, True, {
        'robe': {'rotation': [(0, (0, 0, -3)), (0.5, (0, 0, 3)), (1.0, (0, 0, -3))],
                 'position': [(0, (0, 0, 0)), (0.25, (0, 0.4, 0)), (0.5, (0, 0, 0)), (0.75, (0, 0.4, 0)), (1.0, (0, 0, 0))]},
        'left_arm': {'rotation': [(0, (-12, 0, 0)), (0.5, (12, 0, 0)), (1.0, (-12, 0, 0))]},
    })
    clip('cast_bolt', 2.0, False, {
        'right_arm': {'rotation': [(0, (0, 0, 0)), (0.45, (-45, 0, 0)), (0.6, (-25, 0, 0)), (0.9, (-40, 0, 0)),
                                   (1.2, (-25, 0, 0)), (1.5, (-40, 0, 0)), (2.0, (0, 0, 0))]},
        'body': {'rotation': [(0, (0, 0, 0)), (0.45, (-8, 0, 0)), (0.6, (6, 0, 0)), (2.0, (0, 0, 0))]},
    })
    clip('cast_hands', 2.4, False, {
        'left_arm': {'rotation': [(0, (0, 0, 0)), (0.6, (-100, 0, -15)), (1.5, (-100, 0, -15)), (1.6, (20, 0, 0)), (2.4, (0, 0, 0))]},
        'right_arm': {'rotation': [(0, (0, 0, 0)), (0.6, (-60, 0, 10)), (1.5, (-60, 0, 10)), (1.6, (10, 0, 0)), (2.4, (0, 0, 0))]},
        'body': {'rotation': [(0, (0, 0, 0)), (0.6, (-18, 0, 0)), (1.5, (-18, 0, 0)), (1.6, (14, 0, 0)), (2.4, (0, 0, 0))]},
    })
    clip('drain', 4.2, False, {
        'left_arm': {'rotation': [(0, (0, 0, 0)), (0.5, (-40, 0, 0)), (1.0, (-37, 2, 0)), (1.5, (-41, -2, 0)),
                                  (2.0, (-38, 2, 0)), (2.5, (-41, -2, 0)), (3.0, (-38, 2, 0)), (3.6, (-40, 0, 0)), (4.2, (0, 0, 0))]},
        'hood': {'rotation': [(0, (0, 0, 0)), (0.5, (-10, 0, 0)), (3.6, (-10, 0, 0)), (4.2, (0, 0, 0))]},
    })
    clip('raise', 2.2, False, {
        'left_arm': {'rotation': [(0, (0, 0, 0)), (0.8, (-150, 0, -20)), (1.1, (-150, 0, -20)), (1.3, (-30, 0, 0)), (2.2, (0, 0, 0))]},
        'right_arm': {'rotation': [(0, (0, 0, 0)), (0.8, (-80, 0, 20)), (1.1, (-80, 0, 20)), (1.3, (0, 0, 0)), (2.2, (0, 0, 0))]},
        'body': {'rotation': [(0, (0, 0, 0)), (0.8, (-25, 0, 0)), (1.1, (-25, 0, 0)), (1.3, (10, 0, 0)), (2.2, (0, 0, 0))]},
    })
    clip('blink', 0.8, False, {
        'root': {'scale': [(0, (1, 1, 1)), (0.3, (0.15, 1.3, 0.15)), (0.35, (0.15, 1.3, 0.15)), (0.8, (1, 1, 1))]},
    })
    return {'format_version': '1.8.0', 'animations': clips}


def outputs():
    image, glow = texture()
    files = {}
    for name, img in (('texture', image), ('glowmask', glow)):
        buffer = io.BytesIO()
        img.save(buffer, format='PNG', optimize=False)
        files[OUTPUTS[name]] = buffer.getvalue()
    files[OUTPUTS['model']] = (json.dumps(geo, indent=2) + '\n').encode()
    files[OUTPUTS['animation']] = (json.dumps(animations(), indent=2) + '\n').encode()
    return files


def main():
    check = '--check' in sys.argv
    stale = []
    for path, data in outputs().items():
        if check:
            if not path.exists() or path.read_bytes() != data:
                stale.append(path.relative_to(REPO))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        sys.exit('Stale Hollow Necromancer placeholder outputs: ' + ', '.join(map(str, stale)))
    print('Hollow Necromancer placeholder ' + ('matches' if check else 'written') + '.')


if __name__ == '__main__':
    main()
