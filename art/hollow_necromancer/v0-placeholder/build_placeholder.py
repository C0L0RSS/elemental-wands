#!/usr/bin/env python3
"""Placeholder Hollow Necromancer: robed caster and giant crawling skeleton in one GeckoLib model.

Both bodies share one geometry so the transformation can show them in the same frames; the
entity model hides whichever form is inactive. Box UVs, a 256x256 texture and all clips.

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
SIZE = 256

# Palette: three value steps per material, chosen clusters rather than noise.
ROBE = [(29, 24, 36), (43, 36, 54), (60, 50, 74)]
HOOD = [(36, 30, 45), (52, 44, 65), (70, 60, 88)]
VOID = [(8, 7, 11), (12, 10, 16), (16, 13, 21)]
BONE = [(150, 140, 112), (190, 180, 150), (222, 214, 188)]
WOOD = [(46, 32, 22), (64, 46, 31), (84, 62, 42)]
EYE = (140, 245, 255)
SKULL = [(168, 160, 132), (204, 196, 166), (232, 226, 204)]

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

# Colossus: a crawling giant with impossibly long front arms. It pivots at its back feet so
# the rear-up clips lift the skull to roughly seven blocks. UVs are shelf-packed below the robe.
shelf = {'x': 0, 'y': 64, 'row': 0}


def packed(size):
    w, h, d = size
    width, height = 2 * d + 2 * w, d + h
    if shelf['x'] + width > SIZE:
        shelf['x'], shelf['y'], shelf['row'] = 0, shelf['y'] + shelf['row'], 0
    uv = [shelf['x'], shelf['y']]
    shelf['x'] += width
    shelf['row'] = max(shelf['row'], height)
    assert shelf['y'] + height <= SIZE, 'Colossus UVs overflow the atlas'
    return uv


def bone_cube(owner, center, size, material=SKULL, front=None):
    cube(owner, center, size, packed(size), material, front)


colossus = bone('colossus', [0, 0, 30], 'root')
pelvis = bone('pelvis', [0, 40, 28], 'colossus')
bone_cube(pelvis, [0, 40, 30], [24, 10, 12])
spine = bone('spine', [0, 44, 26], 'pelvis')
for i in range(5):
    bone_cube(spine, [0, 44 + 2 * i, 22 - 7 * i], [6, 6, 7], BONE)
ribcage = bone('ribcage', [0, 52, -6], 'spine')
bone_cube(ribcage, [0, 40, -8], [4, 4, 22], BONE)
for i in range(4):
    z = -16 + i * 6
    for side in (-1, 1):
        bone_cube(ribcage, [side * 11, 46, z], [3, 16, 3])
        bone_cube(ribcage, [side * 6, 54, z], [8, 3, 3], BONE)
neck = bone('neck', [0, 54, -18], 'ribcage')
bone_cube(neck, [0, 56, -24], [6, 6, 10], BONE)
skull = bone('skull', [0, 58, -28], 'neck')
bone_cube(skull, [0, 64, -38], [20, 18, 20], front='skull')
jaw = bone('jaw', [0, 57, -30], 'skull')
bone_cube(jaw, [0, 55, -40], [16, 4, 16], front='teeth')
for side, name in ((-1, 'right'), (1, 'left')):
    arm = bone(f'arm_{name}', [side * 14, 52, -14], 'ribcage')
    bone_cube(arm, [side * 17, 37, -18], [5, 30, 5])
    fore = bone(f'forearm_{name}', [side * 17, 22, -18], f'arm_{name}')
    bone_cube(fore, [side * 17, 11, -20], [4, 22, 4])
    hand = bone(f'hand_{name}', [side * 17, 1, -20], f'forearm_{name}')
    bone_cube(hand, [side * 17, 1.5, -26], [9, 3, 10], BONE)
    for k in (-1, 0, 1):
        bone_cube(hand, [side * 17 + 3 * k, 1, -34], [2, 2, 7])
    leg = bone(f'leg_{name}', [side * 11, 38, 30], 'pelvis')
    bone_cube(leg, [side * 13, 29, 26], [6, 18, 6])
    shin = bone(f'shin_{name}', [side * 13, 20, 27], f'leg_{name}')
    bone_cube(shin, [side * 13, 10, 28], [5, 20, 5])
    bone_cube(shin, [side * 13, 1.5, 26], [6, 3, 10], BONE)

geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': 'geometry.hollow_necromancer', 'texture_width': SIZE,
                    'texture_height': SIZE, 'visible_bounds_width': 10,
                    'visible_bounds_height': 9, 'visible_bounds_offset': [0, 4, 0]},
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
            elif front == 'skull':
                for ex in (fx + 3, fx + w - 8):
                    for x in range(ex, ex + 5):
                        for y in range(fy + 5, fy + 10):
                            px[x, y] = VOID[0] + (255,)
                    for x in range(ex + 1, ex + 3):
                        for y in range(fy + 6, fy + 8):
                            px[x, y] = EYE + (255,)
                            gx[x, y] = EYE + (255,)
                for x in range(fx + w // 2 - 1, fx + w // 2 + 1):
                    for y in range(fy + 11, fy + 14):
                        px[x, y] = VOID[1] + (255,)
                for x in range(fx + 2, fx + w - 2):
                    px[x, fy + h - 2] = (SKULL[2] if x % 2 else VOID[0]) + (255,)
            elif front == 'teeth':
                for x in range(fx + 1, fx + w - 1):
                    px[x, fy] = (SKULL[2] if x % 2 else VOID[0]) + (255,)
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
    # Server clock: arms and skull by 2.4s, hitbox swap at 3.0s, roar at 4.25s, control at 5.0s.
    both_arms = lambda frames: {f'arm_{side}': {channel: [(t, (v[0], v[1] * (1 if side == 'right' else -1), v[2] * (1 if side == 'right' else -1)) if channel == 'rotation' else v)
                                                           for t, v in values] for channel, values in frames.items()}
                                for side in ('right', 'left')}
    clip('transform', 5.0, False, {
        'colossus': {'scale': [(0, (0, 0, 0)), (0.3, (0, 0, 0)), (0.35, (0.14, 0.14, 0.14)), (2.4, (0.14, 0.14, 0.14)),
                               (4.0, (1, 1, 1))],
                     'position': [(0, (0, 11, -26)), (2.4, (0, 11, -26)), (4.0, (0, 0, 0))],  # Tiny skeleton sits in the hood.
                     'rotation': [(0, (0, 0, 0)), (4.0, (0, 0, 0)), (4.25, (-32, 0, 0)), (4.6, (-32, 0, 0)), (5.0, (0, 0, 0))]},
        **both_arms({'scale': [(0, (1, 0.5, 1)), (0.35, (1, 0.5, 1)), (1.2, (1.6, 5, 1.6)), (1.8, (1.6, 3.5, 1.6)),
                               (2.4, (1.6, 3.5, 1.6)), (4.0, (1, 1, 1))],
                     'rotation': [(0, (-170, 0, 18)), (1.2, (-175, 0, 30)), (1.8, (-25, 0, 10)), (2.4, (-130, 0, 20)),
                                  (4.0, (0, 0, 0)), (4.25, (-60, 0, 25)), (4.6, (-60, 0, 25)), (5.0, (0, 0, 0))]}),
        'skull': {'scale': [(0, (1, 1, 1)), (1.8, (1, 1, 1)), (2.4, (3, 3, 3)), (4.0, (1, 1, 1))],
                  'position': [(0, (0, 0, 0)), (1.8, (0, 0, 0)), (2.4, (0, 70, -10)), (4.0, (0, 0, 0))]},
        'jaw': {'rotation': [(0, (0, 0, 0)), (4.0, (0, 0, 0)), (4.25, (28, 0, 0)), (4.6, (28, 0, 0)), (5.0, (0, 0, 0))]},
        'robe': {'scale': [(0, (1, 1, 1)), (0.2, (1.05, 0.95, 1.05)), (0.4, (0.95, 1.05, 0.95)), (2.4, (1, 1, 1)),
                           (3.6, (0.2, 0.2, 0.2)), (3.7, (0, 0, 0))],
                 'position': [(0, (0, 0, 0)), (2.4, (0, 0, 0)), (3.6, (0, -3, 0))]},
        'hood': {'rotation': [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (2.4, (-30, 0, 0)), (2.6, (20, 0, 0))]},
    })
    clip('colossus_idle', 3.0, True, {
        'ribcage': {'scale': [(0, (1, 1, 1)), (1.5, (1.04, 1.03, 1.04)), (3.0, (1, 1, 1))]},
        'skull': {'rotation': [(0, (0, 0, 0)), (1.5, (-4, 6, 0)), (3.0, (0, 0, 0))]},
        'jaw': {'rotation': [(0, (0, 0, 0)), (1.5, (6, 0, 0)), (3.0, (0, 0, 0))]},
    })
    clip('colossus_crawl', 1.2, True, {
        'arm_right': {'rotation': [(0, (-18, 0, 0)), (0.6, (16, 0, 0)), (1.2, (-18, 0, 0))]},
        'arm_left': {'rotation': [(0, (16, 0, 0)), (0.6, (-18, 0, 0)), (1.2, (16, 0, 0))]},
        'leg_right': {'rotation': [(0, (14, 0, 0)), (0.6, (-12, 0, 0)), (1.2, (14, 0, 0))]},
        'leg_left': {'rotation': [(0, (-12, 0, 0)), (0.6, (14, 0, 0)), (1.2, (-12, 0, 0))]},
        'colossus': {'position': [(0, (0, 0, 0)), (0.3, (0, 1.2, 0)), (0.6, (0, 0, 0)), (0.9, (0, 1.2, 0)), (1.2, (0, 0, 0))]},
        'skull': {'rotation': [(0, (0, -5, 0)), (0.6, (0, 5, 0)), (1.2, (0, -5, 0))]},
    })
    clip('colossus_roar', 2.0, False, {
        'colossus': {'rotation': [(0, (0, 0, 0)), (0.4, (-22, 0, 0)), (1.4, (-22, 0, 0)), (2.0, (0, 0, 0))]},
        'jaw': {'rotation': [(0, (0, 0, 0)), (0.4, (30, 0, 0)), (1.4, (30, 0, 0)), (2.0, (0, 0, 0))]},
        **both_arms({'rotation': [(0, (0, 0, 0)), (0.4, (-45, 0, 20)), (1.4, (-45, 0, 20)), (2.0, (0, 0, 0))]}),
    })
    clip('colossus_swipe', 1.8, False, {
        'arm_right': {'rotation': [(0, (0, 0, 0)), (0.7, (-75, 65, 0)), (0.9, (-75, -70, 0)), (1.3, (-75, -70, 0)), (1.8, (0, 0, 0))]},
        'colossus': {'rotation': [(0, (0, 0, 0)), (0.7, (0, 12, 0)), (0.9, (0, -12, 0)), (1.8, (0, 0, 0))]},
        'jaw': {'rotation': [(0, (0, 0, 0)), (0.7, (20, 0, 0)), (1.3, (0, 0, 0))]},
    })
    clip('colossus_grab', 3.2, False, {
        'arm_right': {'rotation': [(0, (0, 0, 0)), (0.6, (-100, 0, 0)), (0.8, (-85, 0, 0)), (1.8, (-155, 0, 0)),
                                   (2.2, (-40, 0, 0)), (3.2, (0, 0, 0))]},
        'hand_right': {'rotation': [(0, (0, 0, 0)), (0.7, (-30, 0, 0)), (0.8, (40, 0, 0)), (2.2, (40, 0, 0)), (2.4, (0, 0, 0))]},
        'colossus': {'rotation': [(0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.8, (-18, 0, 0)), (2.2, (6, 0, 0)), (3.2, (0, 0, 0))]},
    })
    clip('colossus_lunge', 2.5, False, {
        'colossus': {'position': [(0, (0, 0, 0)), (0.9, (0, -4, 0)), (1.0, (0, 0, 0)), (1.7, (0, -2, 0)), (2.5, (0, 0, 0))],
                     'rotation': [(0, (0, 0, 0)), (0.9, (10, 0, 0)), (1.2, (-15, 0, 0)), (1.7, (8, 0, 0)), (2.5, (0, 0, 0))]},
        **both_arms({'rotation': [(0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.3, (-110, 0, 10)), (1.7, (0, 0, 0))]}),
        'leg_right': {'rotation': [(0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.3, (40, 0, 0)), (1.7, (0, 0, 0))]},
        'leg_left': {'rotation': [(0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.3, (40, 0, 0)), (1.7, (0, 0, 0))]},
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
