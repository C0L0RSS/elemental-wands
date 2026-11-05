"""Pose solver for the Pyre King's clips.

Forward kinematics mirrors workshop/core.js (GeckoLib 5 baking): baked space reflects
source X, uses blocks (16 model pixels), and composes T(pos) T(pivot) Rz Ry Rx T(-pivot).
Everything here works in baked space: +X is his right, -Z his front, +Y up.

A frame is described by intent (body angles, where the sword is, where the feet stand),
and solved into per-bone GeckoLib channels: two-bone IK keeps both fists on the grip, the
sword either rides the right hand or flies free on its chain, and the feet stay planted.
"""
import math
import numpy as np
from geometry import BONES, P as SWORD_PIVOT_SRC, sword_point

RAD = math.pi/180
BY = {b['name']: b for b in BONES}
ORDER = [b['name'] for b in BONES]
GRIP = {'right': 12.3/16, 'left': 5.3/16}


def baked_point(p): return np.array([-p[0]/16, p[1]/16, p[2]/16])


def rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a): c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def unit(v):
    v = np.asarray(v, float); n = np.linalg.norm(v); return v/n if n > 1e-12 else v


def rest_angles(name):
    r = BY[name].get('rotation', [0, 0, 0])
    return np.array([-r[0]*RAD, -r[1]*RAD, r[2]*RAD])


def mat4(R, t):
    M = np.eye(4); M[:3, :3] = R; M[:3, 3] = t; return M


def euler_of(R, near=None):
    """Angles (a, b, c) with R = Rz(c) Ry(b) Rx(a), continuous with `near` if given."""
    b = -math.asin(max(-1, min(1, R[2, 0])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(R[2, 1], R[2, 2]); c = math.atan2(R[1, 0], R[0, 0])
    else:
        a = math.atan2(-R[1, 2], R[1, 1]); c = 0
    sol = np.array([a, b, c])
    if near is not None:
        alt = np.array([a+math.pi, math.pi-b, c+math.pi])
        cands = []
        for base in (sol, alt):
            cands.append(base+2*math.pi*np.round((near-base)/(2*math.pi)))
        sol = min(cands, key=lambda v: np.sum((v-near)**2))
    return sol


def local_matrix(name, ang, pos):
    """ang: total baked angles; pos: baked translation (blocks)."""
    pv = baked_point(BY[name]['pivot'])
    R = rz(ang[2]) @ ry(ang[1]) @ rx(ang[0])
    return mat4(R, pos+pv-R@pv)


def fk(channels):
    """channels: {bone: (anim_deg_xyz, pos_px_xyz)} in GeckoLib units -> world 4x4 per bone."""
    out = {}
    for name in ORDER:
        r, p = channels.get(name, ((0, 0, 0), (0, 0, 0)))
        ang = rest_angles(name)+np.array([-r[0]*RAD, -r[1]*RAD, r[2]*RAD])
        pos = np.array([-p[0]/16, p[1]/16, p[2]/16])
        M = local_matrix(name, ang, pos)
        parent = BY[name].get('parent')
        out[name] = out[parent]@M if parent else M
    return out


def gecko_from(name, ang_total, pos_baked=(0, 0, 0)):
    rest = rest_angles(name); d = ang_total-rest
    return ((-d[0]/RAD, -d[1]/RAD, d[2]/RAD), (-pos_baked[0]*16, pos_baked[1]*16, pos_baked[2]*16))


REST = fk({})
REST_ANG = {n: rest_angles(n) for n in ORDER}


def pivot_world(W, name): return (W[name]@np.r_[baked_point(BY[name]['pivot']), 1])[:3]


def rot_between(a, b):
    a, b = unit(a), unit(b); v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -0.999999:
        axis = unit(np.cross(a, [1, 0, 0] if abs(a[0]) < .9 else [0, 1, 0]))
        return 2*np.outer(axis, axis)-np.eye(3)
    K = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3)+K+K@K/(1+c)


SWORD_REST_R = REST['sword'][:3, :3]
SWORD_REST_D = SWORD_REST_R@np.array([0, 1, 0])
SWORD_REST_N = SWORD_REST_R@np.array([0, 0, 1])
SWORD_REST_P = pivot_world(REST, 'sword')
# The sword's rotation relative to the hand that holds it; both fists share it.
SWORD_LOCAL_R = local_matrix('sword', REST_ANG['sword'], np.zeros(3))[:3, :3]


def sword_frame(D, N=None):
    """World rotation of the sword: blade along D, flat facing N (defaults to the guard's)."""
    y = unit(D); N = SWORD_REST_N if N is None else np.asarray(N, float)
    z = unit(N-np.dot(N, y)*y)
    if np.linalg.norm(N-np.dot(N, y)*y) < 1e-6: z = unit(np.cross([1, 0, 0], y))
    x = np.cross(y, z)
    return np.column_stack([x, y, z])


def Rt0(W): return W['torso'][:3, :3]


class Solver:
    def __init__(self):
        self.prev = {}

    def ang(self, name, R):
        a = euler_of(R, self.prev.get(name, REST_ANG[name])); self.prev[name] = a; return a

    def solve(self, spec):
        """spec keys: pelvis_pos (blocks), pelvis_rot/torso_rot/head_rot/neck_rot (deg, GeckoLib
        sense: +x leans forward), sword_p (pommel, blocks), sword_d (direction), sword_n (flat),
        hands: 'both' | 'right' | 'none' (which fists grip), free_hands {side: point},
        elbow_hint {side: vec}, feet {side: ankle point}, extra {bone: (deg, px)}."""
        ch = {}
        def put(name, deg=(0, 0, 0), px=(0, 0, 0)): ch[name] = (tuple(deg), tuple(px))
        put('pelvis', spec.get('pelvis_rot', (0, 0, 0)), np.array(spec.get('pelvis_pos', (0, 0, 0)))*np.array([-16, 16, 16]))
        put('torso', spec.get('torso_rot', (0, 0, 0)))
        put('neck', spec.get('neck_rot', (0, 0, 0)))
        put('head', spec.get('head_rot', (0, 0, 0)))
        for name, (deg, px) in spec.get('extra', {}).items(): put(name, deg, px)
        self.hidden = set(spec.get('hidden', ()))
        W = fk(ch)
        # Legs: two-bone IK from the hip to an ankle target, knee pointing forward-out.
        for side in ('right', 'left'):
            target = spec.get('feet', {}).get(side)
            thigh, shin, foot = side+'_thigh', side+'_shin', side+'_foot'
            if target is None: continue
            hip = pivot_world(W, thigh)
            k0, a0 = baked_point(BY[shin]['pivot']), baked_point(BY[foot]['pivot'])
            h0 = baked_point(BY[thigh]['pivot'])
            sgn = 1 if side == 'right' else -1
            knee = self.two_bone(hip, np.asarray(target, float), np.linalg.norm(k0-h0), np.linalg.norm(a0-k0),
                                 np.array([.35*sgn, 0, -1]))
            Rp = W['pelvis'][:3, :3]
            Rt = rot_between(Rp@(k0-h0), knee-hip)@Rp
            put(thigh, *gecko_from(thigh, self.ang(thigh, Rp.T@Rt)))
            W = fk(ch)
            Rk = rot_between(Rt@(a0-k0), np.asarray(target)-knee)@Rt
            put(shin, *gecko_from(shin, self.ang(shin, Rt.T@Rk)))
            # Feet stay flat on the ground and follow the pelvis's heading.
            yaw = math.atan2(-Rp[0, 2], Rp[2, 2]) if abs(Rp[1, 1]) > .5 else 0
            put(foot, *gecko_from(foot, self.ang(foot, Rk.T@ry(yaw))))
            W = fk(ch)
        # Sword and arms.
        D = unit(spec.get('sword_d', SWORD_REST_D)); Pw = np.asarray(spec.get('sword_p', SWORD_REST_P), float)
        Rs = sword_frame(D, spec.get('sword_n'))
        hands = spec.get('hands', 'both')
        targets = {}
        for side in ('right', 'left'):
            if hands == 'both' or (hands == 'right' and side == 'right'):
                targets[side] = (Pw+GRIP[side]*D, Rs@SWORD_LOCAL_R.T)
            else:
                targets[side] = (np.asarray(spec['free_hands'][side], float), None)
        for side in ('right', 'left'):
            point, Rh = targets[side]
            up, fo, hand = side+'_upper_arm', side+'_forearm', side+'_hand'
            S = pivot_world(W, up)
            e0, h0, s0 = baked_point(BY[fo]['pivot']), baked_point(BY[hand]['pivot']), baked_point(BY[up]['pivot'])
            # By default the elbow bends the way the sculpt's guard bends it.
            hint = np.asarray(spec.get('elbow_hint', {}).get(side, Rt0(W)@(e0-s0)), float)
            Rt = W['torso'][:3, :3]
            E = self.two_bone(S, point, np.linalg.norm(e0-s0), np.linalg.norm(h0-e0), hint)
            Ru = rot_between(Rt@(e0-s0), E-S)@Rt
            put(up, *gecko_from(up, self.ang(up, Rt.T@Ru)))
            Rf = rot_between(Ru@(h0-e0), point-E)@Ru
            put(fo, *gecko_from(fo, self.ang(fo, Ru.T@Rf)))
            if Rh is None: Rh = Rf
            put(hand, *gecko_from(hand, self.ang(hand, Rf.T@Rh)))
        W = fk(ch)
        # The sword rides the right hand unless it has been released.
        if hands in ('right', 'both'):
            put('sword')
        else:
            Hw = W['right_hand']
            pv = baked_point(BY['sword']['pivot'])
            desired = mat4(Rs, Pw-Rs@pv)
            L = np.linalg.inv(Hw)@desired
            Rl = L[:3, :3]
            ang = self.ang('sword', Rl)
            pos = L[:3, 3]+Rl@pv-pv
            put('sword', *gecko_from('sword', ang, pos))
        return ch

    @staticmethod
    def two_bone(root, target, l1, l2, hint):
        d_vec = target-root; d = np.linalg.norm(d_vec)
        d = min(max(d, abs(l1-l2)+1e-4), l1+l2-1e-4)
        n = unit(d_vec)
        m = np.asarray(hint, float)-np.dot(hint, n)*n
        m = unit(m) if np.linalg.norm(m) > 1e-6 else unit(np.cross(n, [0, 1, 0]))
        ca = (l1*l1+d*d-l2*l2)/(2*l1*d)
        sa = math.sqrt(max(0, 1-ca*ca))
        return root+l1*(ca*n+sa*m)


def bake(clip_fn, length, loop=False, rate=20):
    """Sample a clip function t -> spec at `rate` Hz into GeckoLib animation channels."""
    solver = Solver(); bones = {}
    n = int(round(length*rate))
    for i in range(n+1):
        t = round(i/rate, 4)
        ch = solver.solve(clip_fn(min(t, length)))
        for name, (deg, px) in ch.items():
            b = bones.setdefault(name, {'rotation': {}, 'position': {}, 'scale': {}})
            b['rotation'][f'{t:.2f}'] = [round(v, 3) for v in deg]
            b['position'][f'{t:.2f}'] = [round(v, 3) for v in px]
        for name in set(bones) | solver.hidden:
            b = bones.setdefault(name, {'rotation': {}, 'position': {}, 'scale': {}})
            b['scale'][f'{t:.2f}'] = [0, 0, 0] if name in solver.hidden else [1, 1, 1]
    for b in bones.values():
        if all(v == [1, 1, 1] for v in b['scale'].values()): b.pop('scale')
        for key in ('rotation', 'position'):
            if all(all(abs(v) < 1e-3 for v in vals) for vals in b[key].values()): b.pop(key)
    clip = {'animation_length': length, 'bones': {k: v for k, v in bones.items() if v}}
    if loop: clip['loop'] = True
    return clip
