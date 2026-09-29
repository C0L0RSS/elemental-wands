#!/usr/bin/env python3
"""Install the approved Hollow undead candidates as GeckoLib runtime assets.

The review viewer poses cubes in unmirrored Bedrock space with GeckoLib's rotation
signs. GeckoLib itself mirrors X on geometry, pivots and positions and swaps each
face's U. The runtime files pre-mirror those so the game draws exactly what the
viewer showed; `--check` re-proves that with sampled poses of every clip.

Runtime-only changes: walk/crawl root drift is removed (the entity's movement
drives travel), the archer's model arrow hides at release (the real arrow takes
over), and the generated Java timing/socket constants come from these clips.
"""
import argparse
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CANDIDATE = HERE / 'candidate'
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
JAVA = ROOT / 'src/main/java/com/anton/elementalwands/entity/undead/HollowUndeadClips.java'
CREATURES = ('crawler', 'archer', 'brute')
CLIPS = ('idle', 'walk', 'rise', 'attack', 'death')
LOOP = {'idle': True, 'walk': True, 'rise': 'hold_on_last_frame', 'attack': False, 'death': 'hold_on_last_frame'}
# Gameplay moments authored in build_preview.py / upright.py (seconds).
HIT = {'crawler': .58, 'archer': 2.6, 'brute': 2.15}
# Bone whose cube centroid marks the hit: crawler rake claw, archer bow hand, brute maul head.
HIT_SOCKET = {'crawler': ('right_hand', None), 'archer': ('left_hand', None), 'brute': ('club', 11.5)}
ARROW_HIDE = 2.6


# ---------- Shared matrix helpers ----------
def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(v):
    return [[1, 0, 0, v[0]], [0, 1, 0, v[1]], [0, 0, 1, v[2]], [0, 0, 0, 1]]


def rotate(axis, angle):
    c, s = math.cos(angle), math.sin(angle)
    if axis == 0: return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
    if axis == 1: return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def scale(v):
    return [[v[0], 0, 0, 0], [0, v[1], 0, 0], [0, 0, v[2], 0], [0, 0, 0, 1]]


def apply(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def sample(channel, time, fallback):
    if channel is None: return fallback
    keys = sorted((float(t), v) for t, v in channel.items())
    if time <= keys[0][0]: return keys[0][1]
    for (a, av), (b, bv) in zip(keys, keys[1:]):
        if time <= b:
            f = (time - a) / (b - a)
            return [x + (y - x) * f for x, y in zip(av, bv)]
    return keys[-1][1]


def rotation(r):
    # GeckoLib: X and Y negated at load, applied Z, Y, X.
    return mul(mul(rotate(2, math.radians(r[2])), rotate(1, math.radians(-r[1]))), rotate(0, math.radians(-r[0])))


def bones_of(geo):
    return geo['minecraft:geometry'][0]['bones']


def pose(bones, clip, time, gecko):
    """World matrix per bone; `gecko` applies GeckoLib's pivot/position X mirror."""
    flip = -1 if gecko else 1
    out = {}
    for bone in bones:
        ch = clip['bones'].get(bone['name'], {})
        r = sample(ch.get('rotation'), time, [0, 0, 0])
        p = sample(ch.get('position'), time, [0, 0, 0])
        s = sample(ch.get('scale'), time, [1, 1, 1])
        pv = bone['pivot']
        pv = [flip * pv[0], pv[1], pv[2]]
        local = translate([flip * p[0], p[1], p[2]])
        for m in (translate(pv), rotation(r), scale(s), translate([-v for v in pv])):
            local = mul(local, m)
        out[bone['name']] = mul(out[bone['parent']], local) if 'parent' in bone else local
    return out


# ---------- Textured quads as each renderer builds them ----------
def viewer_quads(cube, W, H):
    x, y, z = cube['origin']; w, h, d = cube['size']; X, Y, Z = x + w, y + h, z + d
    corners = {
        'north': [[x, y, z], [X, y, z], [X, Y, z], [x, Y, z]],
        'south': [[X, y, Z], [x, y, Z], [x, Y, Z], [X, Y, Z]],
        'east': [[X, y, z], [X, y, Z], [X, Y, Z], [X, Y, z]],
        'west': [[x, y, Z], [x, y, z], [x, Y, z], [x, Y, Z]],
        'up': [[x, Y, z], [X, Y, z], [X, Y, Z], [x, Y, Z]],
        'down': [[x, y, Z], [X, y, Z], [X, y, z], [x, y, z]]}
    for face, pts in corners.items():
        (u, v), (fw, fh) = cube['uv'][face]['uv'], cube['uv'][face]['uv_size']
        # viewer.js texture coordinates, converted back from its flipped upload to image space.
        uvs = [(u, v + fh), (u + fw, v + fh), (u + fw, v), (u, v)]
        yield face, pts, [(a / W, b / H) for a, b in uvs]


def gecko_quads(cube, W, H):
    (ox, oy, oz), (w, h, d) = cube['origin'], cube['size']
    ox = -(ox + w)
    blb, brb = [ox, oy, oz], [ox, oy, oz + d]
    tlb, trb = [ox, oy + h, oz], [ox, oy + h, oz + d]
    tlf, trf = [ox + w, oy + h, oz], [ox + w, oy + h, oz + d]
    blf, brf = [ox + w, oy, oz], [ox + w, oy, oz + d]
    quads = {'west': [trb, tlb, blb, brb], 'east': [tlf, trf, brf, blf], 'north': [tlb, tlf, blf, blb],
             'south': [trf, trb, brb, brf], 'up': [trb, trf, tlf, tlb], 'down': [blb, blf, brf, brb]}
    for face, pts in quads.items():
        (u, v), (us, vs) = cube['uv'][face]['uv'], cube['uv'][face]['uv_size']
        # GeoQuad.build for an unmirrored face with no UV rotation.
        u0, u1, v0, v1 = (u + us) / W, u / W, v / H, (v + vs) / H
        yield face, pts, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]


# ---------- Runtime conversion ----------
def mirror_geo(geo):
    geo = json.loads(json.dumps(geo))
    for bone in bones_of(geo):
        bone['pivot'] = [r(-bone['pivot'][0]), r(bone['pivot'][1]), r(bone['pivot'][2])]
        for cube in bone.get('cubes', []):
            (x, y, z), (w, h, d) = cube['origin'], cube['size']
            cube['origin'] = [r(-(x + w)), r(y), r(z)]
            cube['size'] = [r(w), r(h), r(d)]
            for face in cube['uv'].values():
                (u, v), (fw, fh) = face['uv'], face['uv_size']
                face['uv'], face['uv_size'] = [u + fw, v], [-fw, fh]
    return geo


def r(value):
    return round(value, 4) + 0.0


def game_clips(name, animations):
    """Viewer-space clips with the runtime-only changes applied (still unmirrored)."""
    clips = {}
    for clip in CLIPS:
        data = json.loads(json.dumps(animations[f'animation.hollow_{name}.{clip}']))
        if clip == 'walk':
            root = data['bones']['root']['position']
            length = data['animation_length']
            drift = sorted((float(t), v) for t, v in root.items())[-1][1][2]
            for t, v in root.items():
                v[2] -= drift * float(t) / length
        if clip == 'attack' and name == 'archer':
            arrow = data['bones']['arrow']['scale']
            for t in list(arrow):
                if float(t) >= ARROW_HIDE: arrow[t] = [0, 0, 0]
            if not any(abs(float(t) - ARROW_HIDE) < 1e-6 for t in arrow): arrow[str(ARROW_HIDE)] = [0, 0, 0]
        data['loop'] = LOOP[clip]
        clips[clip] = data
    return clips


def mirror_clip(clip):
    clip = json.loads(json.dumps(clip))
    for channels in clip['bones'].values():
        for kind, keys in channels.items():
            for t, v in keys.items():
                keys[t] = [r(-v[0]), r(v[1]), r(v[2])] if kind == 'position' else [r(x) for x in v]
    return clip


def centroid(bones, matrices, bone_name, min_x):
    points = []
    for bone in bones:
        if bone['name'] != bone_name: continue
        for cube in bone['cubes']:
            (x, y, z), (w, h, d) = cube['origin'], cube['size']
            if min_x is not None and x + w / 2 - bone['pivot'][0] < min_x: continue
            points.append(apply(matrices[bone_name], [x + w / 2, y + h / 2, z + d / 2]))
    return [sum(p[i] for p in points) / len(points) for i in range(3)]


def verify(name, bones, clips, runtime_bones, runtime_clips, W, H):
    """Every textured quad GeckoLib builds must equal the one the viewer drew."""
    worst = 0
    viewer_cubes = [(b['name'], c) for b in bones for c in b.get('cubes', [])]
    gecko_cubes = [(b['name'], c) for b in runtime_bones for c in b.get('cubes', [])]
    for clip in CLIPS:
        length = clips[clip]['animation_length']
        for time in [length * i / 7 for i in range(8)]:
            a, b = pose(bones, clips[clip], time, False), pose(runtime_bones, runtime_clips[clip], time, True)
            for (bone, vc), (_, gc) in zip(viewer_cubes, gecko_cubes):
                expected = {f: [(apply(a[bone], p), q) for p, q in zip(pts, uv)] for f, pts, uv in viewer_quads(vc, W, H)}
                for face, pts, uv in gecko_quads(gc, W, H):
                    # Each GeckoLib corner needs a viewer corner with the same place and texel
                    # (blocks and texture pixels).
                    for p, q in zip(pts, uv):
                        gp = apply(b[bone], p)
                        worst = max(worst, min(max(max(abs(i - j) for i, j in zip(gp, wp)) / 16,
                                                   max(abs(q[0] - wq[0]) * W, abs(q[1] - wq[1]) * H)) for wp, wq in expected[face]))
    if worst > 2e-3:
        raise SystemExit(f'{name}: GeckoLib pose differs from the reviewed viewer by {worst:.4f}')
    return worst


def exports():
    files, constants = {}, []
    for name in CREATURES:
        stem = f'hollow_{name}'
        geo = json.loads((CANDIDATE / f'{stem}.geo.json').read_text())
        animations = json.loads((CANDIDATE / f'{stem}.animation.json').read_text())['animations']
        description = geo['minecraft:geometry'][0]['description']
        W, H = description['texture_width'], description['texture_height']
        clips = game_clips(name, animations)
        runtime_geo = mirror_geo(geo)
        runtime_clips = {clip: mirror_clip(data) for clip, data in clips.items()}
        worst = verify(name, bones_of(geo), clips, bones_of(runtime_geo), runtime_clips, W, H)
        print(f'  {name}: GeckoLib quads match the viewer (max error {worst:.5f})')

        files[ASSETS / f'geckolib/models/{stem}.geo.json'] = (json.dumps(runtime_geo, indent=1) + '\n').encode()
        files[ASSETS / f'geckolib/animations/{stem}.animation.json'] = (json.dumps({'format_version': '1.8.0', 'animations': {
            f'animation.{stem}.{clip}': data for clip, data in runtime_clips.items()}}, separators=(',', ':')) + '\n').encode()
        for suffix in ('', '_glowmask'):
            files[ASSETS / f'textures/entity/{stem}{suffix}.png'] = (CANDIDATE / f'{stem}{suffix}.png').read_bytes()

        # Gameplay constants sampled from the same clips (viewer space: +X right, -Z forward, px).
        walk = animations[f'animation.hollow_{name}.walk']
        drift = sorted((float(t), v) for t, v in walk['bones']['root']['position'].items())[-1][1][2]
        stride = abs(drift) / 16 / walk['animation_length']
        bone, min_x = HIT_SOCKET[name]
        hit = centroid(bones_of(geo), pose(bones_of(geo), clips['attack'], HIT[name], False), bone, min_x)
        ticks = {clip: round(clips[clip]['animation_length'] * 20) for clip in CLIPS}
        up = name.upper()
        constants.append(
            f'    /** {name.capitalize()}: clip lengths in ticks, 1x walk speed in blocks/s, hit tick and socket (blocks: right, up, forward). */\n'
            f'    public static final int {up}_RISE = {ticks["rise"]}, {up}_ATTACK = {ticks["attack"]}, {up}_DEATH = {ticks["death"]}, {up}_HIT = {round(HIT[name] * 20)};\n'
            f'    public static final double {up}_STRIDE = {stride:.4f};\n'
            f'    public static final double[] {up}_SOCKET = {{{hit[0] / 16:.3f}, {hit[1] / 16:.3f}, {-hit[2] / 16:.3f}}};\n')
    files[JAVA] = ('package com.anton.elementalwands.entity.undead;\n\n'
                   '/** Generated by art/hollow_necromancer/undead/build_runtime.py from the installed clips; do not edit. */\n'
                   'public final class HollowUndeadClips {\n' + '\n'.join(constants) +
                   '\n    private HollowUndeadClips() {}\n}\n').encode()
    return files


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    for path, data in exports().items():
        if args.check:
            if not path.exists() or path.read_bytes() != data: raise SystemExit(f'Stale Hollow undead export: {path}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    print('Hollow undead runtime assets ' + ('verified' if args.check else 'installed'))
