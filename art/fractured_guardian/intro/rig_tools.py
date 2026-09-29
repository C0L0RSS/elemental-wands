"""Pose helpers for the intro: two-bone arm and leg IK on the approved rig, and a pose renderer."""
import math
import runpy
from pathlib import Path
import numpy as np

HERE = Path(__file__).resolve().parent
rig = runpy.run_path(str(HERE.parent/'v4-expressive/build_animations.py'))
bones, mesh, transforms, fingers = rig['bones'], rig['mesh'], rig['transforms'], rig['fingers']


def rot(p, bone, xyz): p.setdefault(bone, {})['rotation'] = np.array(xyz, dtype=float)
def pos(p, bone, xyz): p.setdefault(bone, {})['position'] = np.array(xyz, dtype=float)
def pivot(bone): return np.array(bones[bone]['pivot'], dtype=float)


def euler(r):
    """JSON rotation for a bone matrix; the rig builds Rz(-z) Ry(y) Rx(-x)."""
    y = -math.asin(max(-1, min(1, r[2, 0])))
    x = math.atan2(r[2, 1], r[2, 2])
    z = math.atan2(r[1, 0], r[0, 0])
    return [-math.degrees(x), math.degrees(y), -math.degrees(z)]


def arc(a, b):
    """Shortest rotation taking direction a onto direction b."""
    a = a/np.linalg.norm(a); b = b/np.linalg.norm(b)
    v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -.9999:
        axis = np.cross(a, [1, 0, 0]) if abs(a[0]) < .9 else np.cross(a, [0, 1, 0])
        axis /= np.linalg.norm(axis)
        return 2*np.outer(axis, axis) - np.eye(3)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k@k/(1 + c)


def fist_point(p, side):
    """Centre of the closed hand in the forearm's rest frame, for the hand pose already in p."""
    q = {k: {c: np.array(v) for c, v in ch.items()} for k, ch in p.items()
         if k.startswith(side+'_hand') or k.startswith(side+'_finger') or k.startswith(side+'_thumb')}
    mats = transforms(q)
    pts = [mats[c['bone']][:3, :3]@np.array(v) + mats[c['bone']][:3, 3] for c in mesh
           if c['bone'].startswith(side+'_hand') or c['bone'].startswith(side+'_finger') for v in c['vertices']]
    return np.mean(pts, axis=0)


def reach(p, side, target, pole):
    """Bends the arm so the closed hand's centre lands on target (model space), elbow toward pole."""
    rot(p, side+'_upper_arm', [0, 0, 0]); rot(p, side+'_forearm', [0, 0, 0])
    mats = transforms(p)
    sh = mats[side+'_shoulder']; inv = np.linalg.inv(sh)
    t = inv[:3, :3]@np.array(target, dtype=float) + inv[:3, 3]
    pole = inv[:3, :3]@np.array(pole, dtype=float)
    s, e0, w0 = pivot(side+'_upper_arm'), pivot(side+'_forearm'), fist_point(p, side)
    a, b = np.linalg.norm(e0 - s), np.linalg.norm(w0 - e0)
    d = min(np.linalg.norm(t - s), a + b - .01)
    direction = (t - s)/np.linalg.norm(t - s)
    bend = pole - direction*np.dot(pole, direction); bend /= np.linalg.norm(bend)
    cos_a = (a*a + d*d - b*b)/(2*a*d)
    elbow = s + a*(cos_a*direction + math.sqrt(max(0, 1 - cos_a*cos_a))*bend)
    r1 = arc(e0 - s, elbow - s)
    r2 = arc(w0 - e0, r1.T@(s + direction*d - elbow))
    rot(p, side+'_upper_arm', euler(r1)); rot(p, side+'_forearm', euler(r2))


def leg(p, side, foot, toe=0):
    """Sagittal two-bone leg: the ankle (foot pivot) at foot=(z, y) in model space, knee forward.
    toe tips the foot about the ankle (positive lifts the heel)."""
    py, pz = p['pelvis']['position'][1:] if 'pelvis' in p and 'position' in p['pelvis'] else (0, 0)
    hip = pivot(side+'_thigh') + [0, py, pz]
    l1, l2 = math.hypot(14, 3), math.hypot(13, 3)
    a0 = math.atan2(14, -3); a20 = math.atan2(13, 3) - a0
    z, down = foot[0] - hip[2], hip[1] - foot[1]
    c = (z*z + down*down - l1*l1 - l2*l2)/(2*l1*l2)
    assert -1 <= c <= 1, ('unreachable', side, foot, c)
    a2 = -math.acos(c)
    a1 = math.atan2(down, z) - math.atan2(l2*math.sin(a2), l1 + l2*math.cos(a2)) - a0
    a2 -= a20
    x, y = -math.degrees(a1), -math.degrees(a2)
    rot(p, side+'_thigh', [x, 0, 0]); rot(p, side+'_shin', [y, 0, 0]); rot(p, side+'_foot', [-x - y + toe, 0, 0])


def moved(p, only=None):
    mats = transforms(p)
    out = []
    for c in mesh:
        if only and not any(c['bone'].startswith(o) for o in only): continue
        m = mats[c['bone']]
        out.append(dict(c, vertices=(np.array(c['vertices'])@m[:3, :3].T + m[:3, 3]).tolist()))
    return out


def lowest(p, prefix=''):
    mats = transforms(p)
    return min(float((np.array(c['vertices'])@mats[c['bone']][:3, :3].T + mats[c['bone']][:3, 3])[:, 1].min())
               for c in mesh if c['bone'].startswith(prefix))


def point(p, bone, local):
    m = transforms(p)[bone]
    return m[:3, :3]@np.array(local, dtype=float) + m[:3, 3]


_renderer = None
def render(p, yaw, pitch, size=420, centre=40, span=110, only=None):
    global _renderer
    if _renderer is None:
        source = HERE.parent/'v2-textured'
        code = (source/'render_preview.py').read_text().split('views=[')[0].replace('scale=size/112', 'scale=size/SCALE')
        _renderer = {'__file__': str(source/'render_preview.py')}
        exec(code, _renderer)
    _renderer['SCALE'] = span
    code_centre = _renderer
    code_centre['mesh'] = [dict(c, vertices=(np.array(c['vertices']) - [0, centre - 40, 0]).tolist()) for c in moved(p, only)]
    return _renderer['render'](yaw, pitch, size)


def sheet(frames, path, views=((-30, 10), (0, 4), (90, 2), (150, 10)), size=380, centre=34, span=100, only=None):
    from PIL import Image, ImageDraw
    img = Image.new('RGB', (size*len(views), (size + 24)*len(frames)), (239, 237, 232))
    draw = ImageDraw.Draw(img)
    for r, (label, p) in enumerate(frames):
        for c, (yaw, pitch) in enumerate(views):
            img.paste(render(p, yaw, pitch, size, centre, span, only), (c*size, r*(size + 24)))
        draw.text((8, r*(size + 24) + size + 4), label, fill=(40, 40, 40))
    img.save(path)
