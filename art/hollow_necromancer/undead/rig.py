"""Upright skeleton solver for the reworked archer and brute clips.

A clip is a sampler `pose(t) -> dict` in root space (px; +X right, +Y up, -Z forward). This
module turns each pose into the same bones and conventions `upright.py` uses: the torso turns
about the pelvis, arms and legs are two-bone chains solved toward hand and ankle targets,
and every bone is keyed at 30 fps.

Limb segments turn about a real hinge: the elbow or knee axis is shared by both segments of a
chain, so a forearm never rolls about its own length as it passes the solver's singular
directions. Each clip blends from the original direction-only frames (`w` = 0, the look the
walk and the idles keep) into the hinge frames (`w` = 1) while it acts, so clip boundaries
match. Rotation keys are kept continuous and, at the ends that crossfade into other clips, in
the walk's Euler forms (see `align`).
"""
import math
from upright import add, sub, mul, dot, norm, cross, euler, mat, apply, compose, Clip
import motion

FPS = 30


def _length(v):
    return math.sqrt(dot(v, v))


def _rot_x(d, ref='rot'):
    """Local X of the frame the original clips give a segment pointing along d: upright.rot()
    (no Y turn) or, for the archer's bow forearm and drawing upper arm, the 'stable' solution
    (no Z turn)."""
    if ref == 'stable':
        y = math.atan2(-d[0], d[2])
        return [math.cos(y), 0, math.sin(y)]
    z = math.atan2(d[0], -d[1])
    return [math.cos(z), math.sin(z), 0]


def _turn(v, axis, angle):
    """Rotate v (perpendicular to the unit axis) about axis."""
    return add(mul(v, math.cos(angle)), mul(cross(axis, v), math.sin(angle)))


def solve(start, end, lengths, pole):
    """Two-bone chain: joint position and the hinge axis (normal of the bend plane)."""
    v = sub(end, start); dist = _length(v); a, b = lengths
    assert abs(a-b)+.01 < dist < a+b, (start, end, lengths, dist)
    d = norm(v); along = (a*a-b*b+dist*dist)/(2*dist)
    side = norm(sub(pole, mul(d, dot(pole, d))))
    joint = add(start, add(mul(d, along), mul(side, math.sqrt(max(0, a*a-along*along)))))
    return joint, norm(cross(d, side))


def within(start, end, lengths, fraction=.97):
    """Pull a target toward the chain root so the chain can reach it."""
    v = sub(end, start); dist = _length(v); a, b = lengths
    lo, hi = abs(a-b)+.1, (a+b)*fraction
    return add(start, mul(v, max(lo, min(hi, dist))/dist)) if dist > 1e-6 else end


class Upright:
    def __init__(self, brute, hip_y, shoulder_y, half, arm_lengths, leg_lengths):
        self.brute, self.hip_y, self.shoulder_y, self.half = brute, hip_y, shoulder_y, half
        self.arm_lengths, self.leg_lengths = arm_lengths, leg_lengths
        self.labels = ((-1, 'left'), (1, 'right'))
        self.refs = {} if brute else {'left_fore': 'stable', 'right_upper': 'stable'}
        self.reference = {}          # bone -> rotation at the start of the unchanged walk

    def shoulder_local(self, side):
        return [side*self.half, self.shoulder_y+(.6 if self.brute and side == 1 else 0), -2.7 if self.brute else -.3]

    def hip_local(self, side):
        return [side*(2.6 if self.brute else 2), self.hip_y, 0]

    def body(self, P):
        """Torso rotation about the pelvis pivot; returns (position, rotation, point mapper)."""
        r = P['body']; m = mat(r)
        pos = sub(P['hips'], apply(m, [0, self.hip_y, 0]))
        return pos, r, (lambda q: add(apply(m, q), pos))

    def hips_for_shoulder(self, body_rot, side, shoulder):
        """Pelvis position that puts a shoulder at a given point for a given torso rotation."""
        return sub(shoulder, apply(mat(body_rot), sub(self.shoulder_local(side), [0, self.hip_y, 0])))


class Baker:
    """Keys one clip, carrying each limb segment's hinge and twist from frame to frame."""

    def __init__(self, rig, length, loop, extra=(), align='both'):
        self.rig, self.clip = rig, Clip(length, loop)
        self.times = sorted(set([round(i/FPS, 6) for i in range(round(length*FPS)+1)] + [round(t, 6) for t in extra]))
        self.twist = {}
        self.clamped = {}
        self.align = align

    def segment(self, name, start, end, hinge, w):
        """Euler rotation for a limb segment from start to end (cubes run along local -Y)."""
        d = norm(sub(end, start)); axis = mul(d, -1)
        xr = _rot_x(d, self.rig.refs.get(name, 'rot'))
        # The hinge keeps its sense from frame to frame (it never flips through the bone).
        prev = self.twist.get(name)
        h = hinge if dot(hinge, prev[0] if prev else xr) >= 0 else mul(hinge, -1)
        # Signed turn from the original frame's X to the hinge X about the bone, kept continuous;
        # it is folded back to the short way while fully on the hinge, where it has no effect.
        angle = math.atan2(dot(cross(xr, h), axis), dot(xr, h))
        if prev:
            angle = prev[1]+(angle-prev[1]+math.pi) % math.tau-math.pi
        if w >= .999:
            angle = (angle+math.pi) % math.tau-math.pi
        x = norm(_turn(xr, axis, angle*w))
        self.twist[name] = (h, angle)
        return euler(x, axis)

    def reach(self, name, t, start, end, lengths):
        """A chain target pulled within reach; how far (px) it had to move is logged, since a
        planted hand or foot that is pulled would slide."""
        held = within(start, end, lengths, .995)
        miss = math.sqrt(dot(sub(held, end), sub(held, end)))
        if miss > .05:
            self.clamped.setdefault(name, []).append((round(t, 3), round(miss, 2)))
        return held

    def check_pole(self, name, t, start, end, pole):
        """Logs a bend direction nearly along the limb, where the elbow or knee can pop over."""
        along = abs(dot(norm(pole), norm(sub(end, start))))
        if along > .9:
            self.clamped.setdefault(name+' pole', []).append((round(t, 3), round(along, 3)))

    def emit(self, t, P):
        rig, c = self.rig, self.clip
        w = P.get('w', 1)
        c.pose('root', t, P.get('root', [0, 0, 0]))
        body_pos, body_rot, at = rig.body(P)
        c.pose('torso', t, body_pos, body_rot)
        c.put('skull', 'rotation', t, list(P['skull']))
        c.put('jaw', 'rotation', t, list(P['jaw']))
        for side, label in rig.labels:
            L = P[label]
            shoulder = at(rig.shoulder_local(side))
            hand = self.reach(label+' hand', t, shoulder, L['hand'], rig.arm_lengths[label])
            self.check_pole(label+' arm', t, shoulder, hand, L['pole'])
            elbow, hinge = solve(shoulder, hand, rig.arm_lengths[label], L['pole'])
            ur = self.segment(label+'_upper', shoulder, elbow, hinge, w)
            fr = self.segment(label+'_fore', elbow, hand, hinge, w)
            c.pose(label+'_upper', t, shoulder, ur); c.pose(label+'_fore', t, elbow, fr)
            if 'wrist' in L:
                wrist = L['wrist']
            elif 'club' in L:
                wrist = euler(L['club'], mul(L['fingers'], -1))
            else:
                # Free hand: carried by the forearm, blended from the old world-fixed pose.
                carried = compose(fr, L['wrist_local'])
                fixed = L['wrist_fixed']
                wrist = [fixed[i]+(motion.nearest(carried, fixed)[i]-fixed[i])*L.get('wrist_w', w) for i in range(3)]
            c.pose(label+'_hand', t, hand, wrist)
            for f, curls in enumerate(L['curls']):
                for joint, angle in enumerate(curls):
                    c.put(label+'_finger_'+str(f)+'_'+str(joint), 'rotation', t,
                          [angle, 0, L['splay'][f] if joint == 0 else 0])
            c.put(label+'_thumb_0', 'rotation', t, list(L['thumb'][0]))
            c.put(label+'_thumb_1', 'rotation', t, list(L['thumb'][1]))
            hip = at(rig.hip_local(side))
            ankle = self.reach(label+' foot', t, hip, L['ankle'], rig.leg_lengths)
            self.check_pole(label+' leg', t, hip, ankle, L['knee'])
            knee, khinge = solve(hip, ankle, rig.leg_lengths, L['knee'])
            tr = self.segment(label+'_thigh', hip, knee, khinge, w)
            sr = self.segment(label+'_shin', knee, ankle, khinge, w)
            c.pose(label+'_thigh', t, hip, tr); c.pose(label+'_shin', t, knee, sr)
            c.pose(label+'_foot', t, ankle, list(L.get('foot', [0, 0, 0])))

    def bake(self, sampler, after=None):
        for t in self.times:
            P = sampler(t)
            self.emit(t, P)
            if after:
                after(self.clip, t, P)
        self.drift = align(self.clip.data, self.rig.reference, self.align)
        if self.drift:
            raise SystemExit(f'Rotation ends in a different Euler form than the walk (a crossfade would '
                             f'spin it): {self.drift}')
        return self.clip.data


def align(clip, reference, mode):
    """Keeps every rotation channel continuous and, at the clip ends GeckoLib blends across,
    in the same Euler form as the unchanged clips (the walk), so a crossfade between clips
    never spins a part through the other form of the same turn. `mode` names the ends that
    are blended into: 'start' (deaths), 'end' (rises start unblended underground) or 'both'.
    Returns the channels whose far end lands in another form (a full turn of winding)."""
    drifted = {}
    for bone, channels in clip['bones'].items():
        keys = channels.get('rotation')
        if not keys:
            continue
        order = sorted(keys, key=float)
        ref = reference.get(bone)
        if mode == 'end':
            order = order[::-1]
        if ref is not None:
            keys[order[0]] = motion.nearest(keys[order[0]], ref)
        previous = keys[order[0]]
        for key in order[1:]:
            keys[key] = motion.nearest(keys[key], previous)
            previous = keys[key]
        if mode == 'both' and ref is not None:
            end = keys[order[-1]]
            if max(abs(a-b) for a, b in zip(end, motion.nearest(end, ref))) > 1e-3:
                drifted[bone] = [round(a-b, 1) for a, b in zip(end, motion.nearest(end, ref))]
    return drifted


def correction(x, y, px, py):
    """Axis and angle of the turn taking the frame with local X = x, Y = y onto (px, py)."""
    a = [x, y, cross(x, y)]; b = [px, py, cross(px, py)]
    # R = B A^T with frames as columns.
    r = [[sum(b[k][i]*a[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    angle = math.acos(max(-1, min(1, (r[0][0]+r[1][1]+r[2][2]-1)/2)))
    if angle < 1e-9:
        return [1, 0, 0], 0.
    axis = norm([r[2][1]-r[1][2], r[0][2]-r[2][0], r[1][0]-r[0][1]])
    return axis, angle


def rotate_about(v, axis, angle):
    """Rodrigues rotation of any vector v about a unit axis."""
    c, s = math.cos(angle), math.sin(angle)
    return add(add(mul(v, c), mul(cross(axis, v), s)), mul(axis, dot(axis, v)*(1-c)))


def bump(t, at, before, after):
    """1 at `at`, easing to 0 `before` seconds earlier and `after` seconds later."""
    if t <= at:
        return motion.shaped(t, at-before, at, 'slow')
    return 1-motion.shaped(t, at, at+after, 'slow')
