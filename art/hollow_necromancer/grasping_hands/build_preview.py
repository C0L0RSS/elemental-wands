#!/usr/bin/env python3
"""Author Grasping Hands assets and preview; --install exports, --check verifies runtime assets."""
import base64
import importlib.util
import json
import math
import sys
import shutil
import random
from PIL import Image
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE / 'candidate'
PREVIEW = ROOT / '.local-previews/grasping-hands'


def make_variant(index):
    # Reuse the skull's approved pixel material/UV authoring functions, in an
    # isolated module instance; its output function is never invoked.
    source = HERE.parent / 'soul_bolt/build_preview.py'
    spec = importlib.util.spec_from_file_location('soul_palette', source)
    art = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(art)
    art.BONES.clear()
    art.CUBES.clear()

    def bone(name, pivot, parent=None):
        entry = {'name': name, 'pivot': pivot, 'cubes': []}
        if parent:
            entry['parent'] = parent
        art.BONES.append(entry)
        return len(art.BONES) - 1

    def cube(name, owner, center, size, material='bone'):
        art.cube(name, owner, center, size, material, cracks=material == 'bone')

    root = bone('root', [0, 0, 0])
    # Exposed radius and ulna, a narrow wrist, and four separate palm bones.
    length = (14, 12.5, 16)[index]
    width = (1, 1.18, .84)[index]
    for side in (-1, 1):
        cube('forearm shaft', root, [side * .9 * width, length / 2, 0], [1.15 * width, length, 1.15])
        cube('wrist process', root, [side * .9 * width, length - .4, 0], [1.65 * width, 1.8, 1.6])
        cube('broken base', root, [side * .9 * width, .6, 0], [1.6 * width, 1.8 + (side + 1) * .25, 1.5])
    palm = bone('palm', [0, length, 0], 'root')
    cube('wrist', palm, [0, length + .6, 0], [3.1 * width, 1.6, 1.8])
    finger_base = length + 5
    for finger in range(4):
        x = (finger - 1.5) * 1.6 * width
        cube('metacarpal', palm, [x, length + 3, 0], [1.05 * width, 3.8, 1.2])
        cube('knuckle', palm, [x, finger_base, 0], [1.5 * width, 1.5, 1.65])
        parent = 'palm'
        y = finger_base
        sizes = (2.65, 3.1, 2.85, 2.2)
        for joint in range(3):
            seg = sizes[finger] * (1, .78, .65)[joint] * (1, .9, 1.1)[index]
            name = f'finger_{finger}_{joint}'
            owner = bone(name, [x, y, 0], parent)
            cube('phalange', owner, [x, y + seg / 2, 0], [(.95 if joint < 2 else .8) * width, seg, 1])
            cube('finger joint', owner, [x, y + .35, 0], [1.3 * width, .9, 1.3])
            parent, y = name, y + seg
    thumb = bone('thumb_0', [-2.4 * width, length + 1.5, 0], 'palm')
    cube('thumb base', thumb, [-2.4 * width, length + 3, 0], [1.3, 3, 1.4])
    thumb_tip = bone('thumb_1', [-2.4 * width, length + 4.5, 0], 'thumb_0')
    cube('thumb tip', thumb_tip, [-2.4 * width, length + 5.7, 0], [1.15, 2.4, 1.25])
    # Sparse soul seams illuminate the anatomy without filling the hollow palm.
    cube('soul wrist seam', palm, [-.6, length + .6, -.93], [.3, 1.25, .08], 'soul')
    cube('soul wrist back', palm, [.6, length + .6, .93], [.3, 1.25, .08], 'soul')
    cube('soul forearm seam', root, [.9 * width, length * .65, -.59], [.22, 2.5, .08], 'soul')

    # Explicit per-face rectangles keep skinny finger geometry pixel sharp.
    tw, x, y, row = 256, 0, 0, 0
    for _, raw, _, _ in art.CUBES:
        w, h, d = [max(1, math.ceil(v * 2)) for v in raw['size']]
        raw['uv'] = {}
        for face, (fw, fh) in {'west': (d, h), 'east': (d, h), 'north': (w, h),
                               'south': (w, h), 'up': (w, d), 'down': (w, d)}.items():
            if x + fw + 2 > tw:
                x, y, row = 0, y + row, 0
            raw['uv'][face] = {'uv': [x + 1, y + 1], 'uv_size': [fw, fh]}
            x += fw + 2
            row = max(row, fh + 2)
    th = 64
    while th < y + row:
        th *= 2
    texture, glow = Image.new('RGBA', (tw, th)), Image.new('RGBA', (tw, th))
    for number, (_, raw, material, _) in enumerate(art.CUBES):
        for face, region in raw['uv'].items():
            fx, fy = region['uv']
            fw, fh = region['uv_size']
            rng = random.Random(number * 117 + index * 3 + sum(map(ord, face)))
            for v in range(fh):
                for u in range(fw):
                    if material == 'soul':
                        color = art.SOUL[2 + int(rng.random() > .7)]
                        glow.putpixel((fx + u, fy + v), (*color, 255))
                    else:
                        base = 4 if face == 'up' else 2 if face in ('down', 'west') else 3
                        cluster = (u // 2 * 11 + v // 2 * 17 + number * 7) % 13
                        tone = max(0, min(5, base + (1 if cluster == 2 else -1 if cluster == 10 else 0)))
                        if fw > 3 and u == fw // 2 + v // 4 and v < fh * .6:
                            tone = 1
                        color = art.BONE[tone]
                    texture.putpixel((fx + u, fy + v), (*color, 255))
            for im in (texture, glow):
                for v in range(-1, fh + 1):
                    for u in range(-1, fw + 1):
                        if not (0 <= u < fw and 0 <= v < fh):
                            im.putpixel((fx + u, fy + v), im.getpixel((fx + min(fw - 1, max(0, u)), fy + min(fh - 1, max(0, v)))))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.grasping_hand_{index}',
                        'texture_width': tw, 'texture_height': th,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4,
                        'visible_bounds_offset': [0, 1, 0]},
        'bones': art.BONES}]}
    animation = {'animation_length': 4.2, 'loop': False, 'bones': {}}
    def channel(name, channel_name, keys):
        animation['bones'].setdefault(name, {})[channel_name] = {str(t): v for t, v in keys}
    channel('root', 'position', [(0, [0, -34, 0]), (.35, [0, -34, 0]),
            (1.05, [0, -5, 0]), (1.6, [0, -1, 0]), (3.1, [0, -1, 0]), (3.85, [0, -34, 0]), (4.2, [0, -34, 0])])
    channel('root', 'rotation', [(0, [0, 0, 0]), (1.05, [8, 0, 0]),
            (1.6, [25 + index * 3, 0, 0]), (3.1, [25 + index * 3, 0, 0]), (3.85, [0, 0, 0])])
    channel('palm', 'rotation', [(0, [-12, 0, 0]), (1.15, [-12, 0, 0]),
            (1.6, [14, 0, 0]), (3.1, [14, 0, 0]), (3.85, [-12, 0, 0])])
    for finger in range(4):
        for joint in range(3):
            # Raised knuckles and hooked tips read as a claw, even before closing.
            # Keep the fingers spread through the grip instead of folding a fist.
            splay = (finger - 1.5) * -13 if joint == 0 else 0
            ready = (-12, 48, 38)[joint] + finger * 2
            curl = (12, 78, 58)[joint] + finger * 2
            delay = finger * .065 + joint * .025
            flex = (12, 24, 18)[joint]
            channel(f'finger_{finger}_{joint}', 'rotation', [(0, [ready, 0, splay]),
                    (.65 + delay, [ready - flex * .6, 0, splay]),
                    (.88 + delay, [ready + flex, 0, splay * .8]),
                    (1.2 + delay, [ready - flex * .4, 0, splay]),
                    (1.6, [curl, 0, splay * .7]),
                    (1.85 + delay, [curl - flex * .65, 0, splay * .85]),
                    (2.12 + delay, [curl + flex * .3, 0, splay * .6]),
                    (2.43 + delay, [curl - flex * .5, 0, splay * .8]),
                    (2.73 + delay, [curl + flex * .25, 0, splay * .65]),
                    (3.1, [curl, 0, splay * .7]), (3.7, [ready, 0, splay])])
    channel('thumb_0', 'rotation', [(0, [18, -12, 48]), (.8, [8, -8, 54]),
            (1.08, [30, -20, 30]), (1.3, [18, -12, 48]),
            (1.6, [38, -25, 12]), (2.05, [25, -18, 25]), (2.35, [43, -28, 8]),
            (2.7, [28, -20, 22]), (3.1, [38, -25, 12]), (3.7, [18, -12, 48])])
    channel('thumb_1', 'rotation', [(0, [48, 0, 0]), (.85, [30, 0, 0]),
            (1.13, [65, 0, 0]), (1.35, [42, 0, 0]),
            (1.6, [72, 0, 0]), (2.1, [52, 0, 0]), (2.4, [78, 0, 0]),
            (2.75, [56, 0, 0]), (3.1, [72, 0, 0]), (3.7, [48, 0, 0])])
    animations = {'format_version': '1.8.0', 'animations': {'animation.grasping_hand.grasp': animation}}
    stem = f'grasping_hand_{index}'
    (OUT / f'{stem}.geo.json').write_text(json.dumps(geo, indent=2) + '\n')
    (OUT / f'{stem}.animation.json').write_text(json.dumps(animations, indent=2) + '\n')
    texture.save(OUT / f'{stem}.png')
    glow.save(OUT / f'{stem}_glowmask.png')
    images = {name: 'data:image/png;base64,' + base64.b64encode((OUT / name).read_bytes()).decode()
              for name in (f'{stem}.png', f'{stem}_glowmask.png')}
    return {'geo': geo, 'animation': animation, 'images': images, 'stem': stem}


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    payload = {'variants': [make_variant(i) for i in range(3)]}
    page = (HERE / 'viewer.template.html').read_text()
    page = page.replace('<!-- MODEL_DATA -->', '<script id="model-data" type="application/json">'
                        + json.dumps(payload).replace('</', '<\\/') + '</script>')
    page = page.replace('<!-- VIEWER -->', '<script type="module">' + (HERE / 'viewer.js').read_text() + '</script>')
    (PREVIEW / 'index.html').write_text(page)
    print(f'Grasping Hands: three articulated candidates; preview {PREVIEW / "index.html"}')

    if '--install' in sys.argv or '--check' in sys.argv:
        assets = ROOT / 'src/main/resources/assets/elementalwands'
        for source in sorted(OUT.iterdir()):
            folder = 'geckolib/models' if source.name.endswith('.geo.json') else 'geckolib/animations' if source.name.endswith('.animation.json') else 'textures/entity'
            target = assets / folder / source.name
            if '--check' in sys.argv:
                assert target.exists() and target.read_bytes() == source.read_bytes(), f'Stale Grasping Hands asset: {target}'
            else:
                shutil.copyfile(source, target)
        print('Grasping Hands runtime assets match.' if '--check' in sys.argv else 'Installed Grasping Hands runtime assets.')
