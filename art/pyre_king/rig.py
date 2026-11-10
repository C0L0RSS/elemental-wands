"""Pose solver for the Pyre King's clips.

Forward kinematics mirrors workshop/core.js (GeckoLib 5 baking): baked space reflects
source X, uses blocks (16 model pixels), and composes T(pos) T(pivot) Rz Ry Rx T(-pivot).
Everything here works in baked space: +X is his right, -Z his front, +Y up.

A frame is described by intent (body angles, where the sword is, where the feet stand),
and solved into per-bone GeckoLib channels: two-bone IK keeps both fists on the grip, the
sword either rides the right hand or flies free on its chain, the feet stay planted, and
each shoulder plate takes a share of its upper arm's turn (pauldron_turn).
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


# The shoulder plates (side+'_pauldron') hang on the torso at the shoulder joint, so a plate
# does not rock with every swing of the arm under it. Each takes a share k of its upper
# arm's turn against the torso, about the same axis. The lames hug the top of the arm, so a
# plate has only a little room to let the arm move under it: the arm may turn up to
# PAULDRON_ROOM[2] deg further than its plate, but rise (lift from the chest's down line)
# only PAULDRON_ROOM[0] and swing out (away from the body's midline) only PAULDRON_ROOM[1]
# deg further; beyond that it carries the plate along. So k is the least share that keeps
# the arm within that room, never less than PAULDRON_FOLLOW[0] and never more than
# PAULDRON_FOLLOW[1], blended smoothly. The swings of a stride stay inside that room (the
# run's cocked sword arm turns 23-45 deg against the chest but lifts under 3 and swings out
# under 8), so the plates stay steady with the chest; only the walk's widest outward swing
# of the sword arm takes its plate a few degrees further. A big raise (the Royal Cut's cock and
# backhand, the throw's layback and whip, an arm swung out wide) carries its plate most of
# the way, so the arm never cuts out through it.
PAULDRON_FOLLOW = (.2, .9)
PAULDRON_ROOM = (14., 7., 47.)      # lift, outward swing, turn (deg)


def _smax(a, b, e=.04):
    return (a+b+math.sqrt((a-b)**2+e*e))/2


def pauldron_share(lift, out, turn):
    """k for an upper arm lifted, swung out and turned so many degrees against its rest pose."""
    k = PAULDRON_FOLLOW[0]
    for v, room in zip((lift, out, turn), PAULDRON_ROOM):
        k = _smax(k, 1-room/max(v, room))
    return -_smax(-k, -PAULDRON_FOLLOW[1])


def axis_angle(R):
    """The unit axis and angle (rad) of a rotation."""
    c = max(-1., min(1., (np.trace(R)-1)/2)); a = math.acos(c)
    v = np.array([R[2, 1]-R[1, 2], R[0, 2]-R[2, 0], R[1, 0]-R[0, 1]])
    if np.linalg.norm(v) > 1e-9 and a < math.pi-1e-3: return v/np.linalg.norm(v), a
    if a < 1e-9: return np.array([1., 0, 0]), 0.
    S = (R+np.eye(3))/2; i = int(np.argmax(np.diag(S)))    # near a half turn
    axis = unit(S[:, i]); return (axis if axis@v >= 0 else -axis), a


def axis_rot(axis, a):
    x, y, z = axis; c, s = math.cos(a), math.sin(a); C = 1-c
    return np.array([[c+x*x*C, x*y*C-z*s, x*z*C+y*s], [y*x*C+z*s, c+y*y*C, y*z*C-x*s], [z*x*C-y*s, z*y*C+x*s, c+z*z*C]])


def pauldron_turn(L, rest_dir, outward):
    """A pauldron's turn against the torso for its upper arm's turn L against the torso;
    rest_dir is the upper arm's rest direction (shoulder to elbow) and outward the unit
    vector away from the body's midline on that side, both in the torso's frame."""
    axis, a = axis_angle(L); u = L@rest_dir
    def deg(c): return math.degrees(math.acos(max(-1., min(1., c))))
    lift = deg(-u[1])-deg(-rest_dir[1])
    out = deg(rest_dir@outward)-deg(u@outward)
    return axis_rot(axis, pauldron_share(lift, out, math.degrees(a))*a)


class Solver:
    def __init__(self):
        self.prev = {}

    def ang(self, name, R):
        a = euler_of(R, self.prev.get(name, REST_ANG[name])); self.prev[name] = a; return a

    def solve(self, spec):
        """spec keys: pelvis_pos (blocks), pelvis_rot/torso_rot/head_rot/neck_rot (deg, GeckoLib
        sense: +x leans forward), sword_p (pommel, blocks), sword_d (direction), sword_n (flat),
        hands: 'both' | 'right' | 'none' (which fists grip), free_hands {side: point},
        wrist {side: 3x3} (a free fist's turn against its forearm; default straight),
        elbow_hint {side: vec}, hinge: True (pose the arms as hinges),
        feet {side: ankle point}, extra {bone: (deg, px)},
        chain_ends {marker bone: world point}.
        Optional leg controls (omit them for the guard stance's defaults): knee_hint {side: vec}
        replaces the forward-and-out knee direction, and foot_r {side: 3x3 world rotation}
        replaces the flat foot that follows the pelvis's heading (so a foot can roll heel to toe)."""
        ch = {}
        def put(name, deg=(0, 0, 0), px=(0, 0, 0)): ch[name] = (tuple(deg), tuple(px))
        put('pelvis', spec.get('pelvis_rot', (0, 0, 0)), np.array(spec.get('pelvis_pos', (0, 0, 0)))*np.array([-16, 16, 16]))
        put('torso', spec.get('torso_rot', (0, 0, 0)))
        put('neck', spec.get('neck_rot', (0, 0, 0)))
        put('head', spec.get('head_rot', (0, 0, 0)))
        for name, (deg, px) in spec.get('extra', {}).items(): put(name, deg, px)
        self.hidden = set(spec.get('hidden', ()))
        W = fk(ch)
        # Legs: two-bone IK from the hip to an ankle target, knee pointing forward-out unless
        # the spec aims it (a gait drives the knees straight ahead over narrow feet).
        for side in ('right', 'left'):
            target = spec.get('feet', {}).get(side)
            thigh, shin, foot = side+'_thigh', side+'_shin', side+'_foot'
            if target is None: continue
            hip = pivot_world(W, thigh)
            k0, a0 = baked_point(BY[shin]['pivot']), baked_point(BY[foot]['pivot'])
            h0 = baked_point(BY[thigh]['pivot'])
            sgn = 1 if side == 'right' else -1
            knee_hint = spec.get('knee_hint', {}).get(side)
            if knee_hint is None:
                # By default the knee bends the way the sculpted stance bends it, turned with the
                # pelvis: the rest knee's offset from its hip-ankle line. The knees sit on that
                # line seen from the front, so a planted leg stays a straight column.
                line = unit(a0-h0); rest = (k0-h0)-np.dot(k0-h0, line)*line
                knee_hint = W['pelvis'][:3, :3]@rest
            else:
                knee_hint = np.asarray(knee_hint, float)
            knee = self.two_bone(hip, np.asarray(target, float), np.linalg.norm(k0-h0), np.linalg.norm(a0-k0),
                                 knee_hint)
            Rp = W['pelvis'][:3, :3]
            Rt = rot_between(Rp@(k0-h0), knee-hip)@Rp
            put(thigh, *gecko_from(thigh, self.ang(thigh, Rp.T@Rt)))
            W = fk(ch)
            Rk = rot_between(Rt@(a0-k0), np.asarray(target)-knee)@Rt
            put(shin, *gecko_from(shin, self.ang(shin, Rt.T@Rk)))
            # Feet stay flat on the ground and follow the pelvis's heading, unless the spec
            # gives the foot's world rotation.
            Rfoot = spec.get('foot_r', {}).get(side)
            if Rfoot is None:
                yaw = math.atan2(-Rp[0, 2], Rp[2, 2]) if abs(Rp[1, 1]) > .5 else 0
                Rfoot = ry(yaw)
            put(foot, *gecko_from(foot, self.ang(foot, Rk.T@np.asarray(Rfoot, float))))
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
            Rf = rot_between(Ru@(h0-e0), point-E)@Ru
            # A clip may pose its arms as hinges instead (spec hinge: True): each bone's roll
            # then follows the plane the arm bends in, so an arm flung up or folded tight never
            # flips its roll the way a shortest turn from the sculpt does. A whole clip uses one
            # or the other; both agree in the guard.
            if spec.get('hinge'): Ru, Rf = self.hinged(S, E, point, hint, s0, e0, h0)
            put(up, *gecko_from(up, self.ang(up, Rt.T@Ru)))
            # The shoulder plate, on the torso at the same pivot, takes its share of that turn
            # (baked +X is his right, so outward is +X on the right, -X on the left).
            pd, outward = side+'_pauldron', np.array([1. if side == 'right' else -1., 0, 0])
            put(pd, *gecko_from(pd, self.ang(pd, pauldron_turn(Rt.T@Ru, unit(e0-s0), outward))))
            put(fo, *gecko_from(fo, self.ang(fo, Ru.T@Rf)))
            if Rh is None: Rh = Rf@np.asarray(spec.get('wrist', {}).get(side, np.eye(3)), float)
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
        # Marker bones sent to world points (the loose chains' far ends).
        for name, point in spec.get('chain_ends', {}).items():
            L = np.linalg.inv(W[BY[name]['parent']])@np.r_[np.asarray(point, float), 1]
            put(name, (0, 0, 0), gecko_from(name, REST_ANG[name], L[:3]-baked_point(BY[name]['pivot']))[1])
        return ch

    @staticmethod
    def hinged(S, E, point, hint, s0, e0, h0):
        """World rotations of an upper arm and forearm as a hinge: each lies along its bone
        and keeps the sculpt's bending plane on the plane this elbow bends in."""
        line = unit(point-S); m = np.asarray(hint, float)-np.dot(hint, line)*line
        m = unit(m) if np.linalg.norm(m) > 1e-6 else unit(np.cross(line, [0, 1, 0]))
        n = unit(np.cross(m, line)); a0, b0 = unit(e0-s0), unit(h0-e0); n0 = unit(np.cross(a0, b0))
        def frame(x, y): return np.column_stack([x, y, np.cross(x, y)])
        return frame(unit(E-S), n)@frame(a0, n0).T, frame(unit(point-E), n)@frame(b0, n0).T

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


# GeckoLib and the workshop play a clip by lerping each channel's keyed Euler angles, so between
# keys a bone takes the straight Euler path, not the real one. A 20 Hz key grid is fine for
# slow moves, but in a fast blow the fists drift off the grip between keys, and where the
# sword switches between riding the right hand and flying free its channel turns a long way
# in one step. So the bake keys every 1/rate s, then adds keys every 0.01 s over:
#   - the GRIP_WINDOW s either side of every change of grip (a fist takes or lets go of the
#     sword, so the sword switches between riding the right hand and flying free);
#   - any interval where, a third and two thirds of the way between its keys (where a 60 fps
#     viewer lands), the lerped pose strays from the solved one by more than FINE_TOL;
# and keys every 0.005 s in any 0.01 s interval that still strays (only the fastest flick of
# a blow). Key names keep two decimals, three for those half steps. Every rotation key is
# written as the Euler equivalent nearest the key before it, so no lerp takes the long way.
FINE = 200                  # the bake's clock: ticks per second
STEPS = (2, 1)              # the refinement steps, in ticks: 0.01 s, then 0.005 s
GRIP_WINDOW = .1
FINE_TOL = {'blade deg': 4., 'tip blocks': .2, 'grip gap blocks': .008, 'arm deg': 10.}
SWORD_LENGTH = 72/16
ARM_BONES = [s+b for s in ('right', 'left') for b in ('_upper_arm', '_forearm', '_hand')]


def player(clip):
    """t -> a baked clip's channels {bone: (deg, px)}, sampled the way workshop/core.js (and
    GeckoLib's linear keys) play them: each channel lerped between the keys either side of t."""
    import bisect
    def side(v, k): return v.get(k, v.get('post' if k == 'pre' else 'pre')) if isinstance(v, dict) else v
    tracks = {}
    for name, chans in clip['bones'].items():
        for kind in ('rotation', 'position'):
            if isinstance(chans.get(kind), dict):
                ks = sorted(((float(k), v) for k, v in chans[kind].items()), key=lambda kv: kv[0])
                tracks[name, kind] = ([k for k, _ in ks], [np.array(side(v, 'pre'), float) for _, v in ks],
                                      [np.array(side(v, 'post'), float) for _, v in ks])

    def at(t):
        out = {}
        for name in clip['bones']:
            ch = []
            for kind in ('rotation', 'position'):
                if (name, kind) not in tracks: ch.append((0, 0, 0)); continue
                ts, pre, post = tracks[name, kind]; i = bisect.bisect_left(ts, t)
                if i == 0: v = post[0]
                elif i == len(ts): v = post[-1]
                else: f = (t-ts[i-1])/((ts[i]-ts[i-1]) or 1); v = post[i-1]+(pre[i]-post[i-1])*f
                ch.append(tuple(v))
            out[name] = tuple(ch)
        return out
    return at


def unwrap(name, deg, near):
    """GeckoLib degrees for the same rotation as deg: the Euler equivalent nearest `near`."""
    deg, near = np.asarray(deg, float), np.asarray(near, float)
    rb = rest_angles(name)[1]/RAD               # total y = rest y - GeckoLib y
    best = None
    for cand in (deg, np.array([deg[0]-180, 2*rb-180-deg[1], deg[2]+180])):
        cand = cand+360*np.round((near-cand)/360)
        if best is None or np.sum((cand-near)**2) < np.sum((best-near)**2)-1e-9: best = cand
    return best


def gripping(spec):
    h = spec.get('hands', 'both')
    return frozenset(('right', 'left') if h == 'both' else ('right',) if h == 'right' else ())


def turned(A, B): return math.degrees(math.acos(np.clip((np.trace(A[:3, :3].T@B[:3, :3])-1)/2, -1, 1)))


def strays(W, truth, spec):
    """How far a lerped pose W misses the solved one, in FINE_TOL's terms."""
    pv = np.r_[baked_point(BY['sword']['pivot']), 1]
    y, yt = W['sword'][:3, 1], truth['sword'][:3, 1]
    p, pt = (W['sword']@pv)[:3], (truth['sword']@pv)[:3]
    return {'blade deg': math.degrees(math.acos(np.clip(y@yt, -1, 1))),
            'tip blocks': float(np.linalg.norm(p+SWORD_LENGTH*y-pt-SWORD_LENGTH*yt)),
            'grip gap blocks': max([float(np.linalg.norm(pivot_world(W, s+'_hand')-p-GRIP[s]*y))
                                    for s in gripping(spec)], default=0.),
            'arm deg': max(turned(W[b], truth[b]) for b in ARM_BONES)}


def bake(clip_fn, length, loop=False, rate=20):
    """Sample a clip function t -> spec into GeckoLib animation channels: keys every 1/rate s,
    and denser (STEPS) wherever the in-between lerp would miss the real motion."""
    solver, poses, bones = Solver(), {}, {}
    last = int(round(length*FINE))

    def solve(k):                                # k: time in 1/FINE s
        if k not in poses:
            spec = clip_fn(min(k/FINE, length)); poses[k] = (spec, solver.solve(spec), set(solver.hidden))
        return poses[k]

    def unwrapped(ticks):
        prev, out = {}, {}
        for k in ticks:
            ch = {}
            for name, (deg, px) in solve(k)[1].items():
                if name in prev:
                    near = unwrap(name, deg, prev[name])
                    if not np.array_equal(near, np.asarray(deg, float)): deg = tuple(near)
                prev[name] = np.asarray(deg, float); ch[name] = (deg, px)
            out[k] = ch
        return out

    def strays_between(keys, ka, kb):
        A, B = keys[ka], keys[kb]
        for f in (1/3, 1/2, 2/3):
            spec = clip_fn((ka+(kb-ka)*f)/FINE)
            lerped = {n: tuple(tuple(np.add(a, np.subtract(b, a)*f)) for a, b in zip(A[n], B[n])) for n in A if n in B}
            miss = strays(fk(lerped), fk(Solver().solve(spec)), spec)
            if any(miss[q] > FINE_TOL[q] for q in FINE_TOL): return True
        return False

    def split(ka, kb, step):
        sub = list(range(ka, kb, step))+[kb]; ticks.update(sub); return list(zip(sub, sub[1:]))

    base = list(range(0, last+1, FINE//rate))
    if base[-1] != last: base.append(last)
    ticks, w, check, forced = set(base), int(round(GRIP_WINDOW*FINE)), [], []
    for ka, kb in zip(base, base[1:]):
        (sa, _, ha), (sb, _, hb) = solve(ka), solve(kb)
        if gripping(sa) != gripping(sb) or ha != hb:
            lo, hi = max(0, ka-w), min(last, kb+w)
            forced += split(lo-lo % STEPS[0], hi, STEPS[0])
        else: check.append((ka, kb))
    for level, step in enumerate(STEPS):
        keys = unwrapped(sorted(ticks))
        flagged = [(ka, kb) for ka, kb in check if kb-ka > step and strays_between(keys, ka, kb)]
        check = [pair for ka, kb in flagged for pair in split(ka, kb, step)]
        if level == 0: check += forced
    ticks = sorted(ticks); keys = unwrapped(ticks)
    for k in ticks:
        t, hidden = f'{k/FINE:.2f}' if k % 2 == 0 else f'{k/FINE:.3f}', solve(k)[2]
        for name, (deg, px) in keys[k].items():
            b = bones.setdefault(name, {'rotation': {}, 'position': {}, 'scale': {}})
            b['rotation'][t] = [round(v, 3) for v in deg]
            b['position'][t] = [round(v, 3) for v in px]
        for name in set(bones) | hidden:
            b = bones.setdefault(name, {'rotation': {}, 'position': {}, 'scale': {}})
            b['scale'][t] = [0, 0, 0] if name in hidden else [1, 1, 1]
    for b in bones.values():
        if all(v == [1, 1, 1] for v in b['scale'].values()): b.pop('scale')
        for key in ('rotation', 'position'):
            if all(all(abs(v) < 1e-3 for v in vals) for vals in b[key].values()): b.pop(key)
    clip = {'animation_length': length, 'bones': {k: v for k, v in bones.items() if v}}
    if loop: clip['loop'] = True
    return clip
