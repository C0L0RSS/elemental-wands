"""The colossus's reworked clips: a huge, heavy undead monster. Few, slow beats with long holds;
only the blow itself is fast. Every clip leaves from and returns to the crawl's rest pose (the
idle's first frame), the hind feet stay planted unless it steps, and planted claws stay where
they bite the ground.

Rig directions as in robed.py: +X tips a part's top forward, +Y turns it toward its right (a
forward-pointing right arm swings out to its right), -Z is forward. Spine values add to the mesh's 22-degree hunch. Wrist targets are rig space (X
mirrored: +X is its right), in model pixels.
"""
import math
import numpy as np
from motion import Clip, curve, shaped, envelope
from rig import sample, BY_NAME, matrix as rig_matrix, local as rig_local

CRAWL = {('colossus', 'position'): (0, -10, 0), ('spine', 'rotation'): (43, -3, 1), ('skull', 'rotation'): (-58, 5, -3),
         ('jaw', 'rotation'): (4, 0, 0), ('leg_right', 'rotation'): (-32, 0, 0), ('leg_left', 'rotation'): (-32, 0, 0),
         ('shin_right', 'rotation'): (82, 0, 0), ('shin_left', 'rotation'): (82, 0, 0)}
REST_WRIST = {'right': [20, 5, -43], 'left': [-20, 5, -43]}
ANKLE = {'right': (-8, 3, 1), 'left': (8, 3, 1)}  # each hind foot's pivot
CLAW_REST = 12


class Colossal(Clip):
    """A colossus clip: None in a key stands for the crawl's rest value of that channel."""

    def __init__(self, clips, name, length, loop=False, seed=None, seed_time=0.):
        super().__init__(clips, name, length, loop)
        idle = (seed or clips['colossus_idle'])['bones']
        # Each elbow starts its solve from the crawl's own solved arms (or the clip it chains
        # from), so it keeps its branch and the arms meet the next clip's.
        self.seed = {}
        for side in ('right', 'left'):
            for part in ('arm_', 'forearm_', 'hand_'):
                self.seed[part + side] = tuple(float(x) for x in sample(idle[part + side]['rotation'], seed_time, [0, 0, 0]))

    def rest(self, bone, channel='rotation'):
        return np.array(CRAWL.get((bone, channel), (1, 1, 1) if channel == 'scale' else (0, 0, 0)), dtype=float)

    def _fill(self, bone, channel, points):
        return [(p[0], self.rest(bone, channel) if p[1] is None else p[1], *p[2:]) for p in points]

    def rot(self, bone, points, lag=0.):
        return super().rot(bone, self._fill(bone, 'rotation', points), lag)

    def pos(self, bone, points, lag=0.):
        return super().pos(bone, self._fill(bone, 'position', points), lag)

    def hold_rest(self):
        for (bone_, channel), v in CRAWL.items():
            if (bone_, channel) not in self.fns:
                self.add(bone_, channel, lambda t, v=np.array(v, dtype=float): v.copy())
        for bone_, v in self.seed.items():
            if (bone_, 'rotation') not in self.fns:
                self.add(bone_, 'rotation', lambda t, v=np.array(v, dtype=float): v.copy())
        return self


def claw(c, side, frames):
    """An uneven grasp, as the crawl's claws do: index and middle lead, the little finger lags,
    the thumb opposes. frames: (time, curl degrees[, shape]); None is the resting curl."""
    frames = [(f[0], CLAW_REST if f[1] is None else f[1], *f[2:]) for f in frames]
    for i, (strength, lag) in enumerate(((.82, 0), (1.08, .045), (.95, .085), (.72, .13))):
        # A lagging finger still lands on the clip's last pose: keys its lag pushes past the end drop out.
        keys = [(t + (lag if 0 < t < c.length else 0), v * strength, *rest) for t, v, *rest in frames]
        keys = [k for k in keys if k[0] < c.length or k is keys[-1]]
        c.add(f'finger_{side}_{i}', 'rotation', curve([(t, (v, (i - 1.5) * 2.5, (i - 1.5) * 1.5), *r) for t, v, *r in keys]))
        c.add(f'tip_{side}_{i}', 'rotation', curve([(t, (v * .72, 0, 0), *r) for t, v, *r in keys]))
        c.add(f'end_{side}_{i}', 'rotation', curve([(t, (v * .42, 0, 0), *r) for t, v, *r in keys]))
    sign = 1 if side == 'right' else -1
    c.add('thumb_' + side, 'rotation', curve([(t, (v * .65, sign * v * .65, 0), *r) for t, v, *r in frames]))
    c.add('thumb_tip_' + side, 'rotation', curve([(t, (v * .55, 0, 0), *r) for t, v, *r in frames]))


def rest_ankle(side):
    """Where a hind ankle sits in the crawl's rest pose."""
    rest = Clip({}, 'rest', 1)
    for (bone_, channel), v in CRAWL.items():
        rest.add(bone_, channel, lambda t, v=np.array(v, dtype=float): v.copy())
    return rest.point('foot_' + side, ANKLE[side], 0)


def plant_hind(c, times=None, at_rest=False, weight=1.):
    """Hind feet stay where they stand at the start (or, with at_rest, where the crawl's rest
    pose stands them, for clips that start mid-crouch); hips and knees take the body, and each
    foot turns against its leg so it stays flat on the ground."""
    goals, dofs, limits = [], [], {}
    for side in ('right', 'left'):
        ankle = rest_ankle(side) if at_rest else c.point('foot_' + side, ANKLE[side], 0)
        goals.append(('foot_' + side, ANKLE[side], lambda t, s=ankle: s, weight))
        dofs += [('leg_' + side, 0), ('leg_' + side, 2), ('shin_' + side, 0)]
        limits.update({('leg_' + side, 0): (-140, 60), ('leg_' + side, 2): (-35, 35), ('shin_' + side, 0): (0, 160)})
    c.solve(goals, dofs, times, limits)
    start = {side: CRAWL[('leg_' + side, 'rotation')][0] + CRAWL[('shin_' + side, 'rotation')][0] if at_rest else
             c.value('leg_' + side, 'rotation', 0)[0] + c.value('shin_' + side, 'rotation', 0)[0] for side in ('right', 'left')}
    for side in ('right', 'left'):
        legs = c.solved[('leg_' + side, 'rotation')]
        shins = c.solved[('shin_' + side, 'rotation')]
        pitch = lambda t, s=side: c.value('colossus', 'rotation', t)[0] + c.value('pelvis', 'rotation', t)[0]
        w = (lambda t: weight(t)) if callable(weight) else (lambda t: weight)
        flat = lambda k, s=side: np.array([start[s] - legs[k][0] - shins[k][0] - pitch(k), 0, -legs[k][2]])
        c.solved[('foot_' + side, 'rotation')] = {k: flat(k) * w(k) + c.value('foot_' + side, 'rotation', k) * (1 - w(k)) for k in legs}


def reach(clip, side, targets, home=lambda t: 0.):
    """rig.plant's wrist solve, with one addition: home(t) (0..1) pulls the elbow's swivel, the
    turn that leaves the wrist where it is, back toward the authored arm. A clip that ends on
    the crawl's own arms can then plant its claw anywhere and still hand the same elbow back."""
    arm, fore, palm = 'arm_' + side, 'forearm_' + side, 'hand_' + side
    parent = BY_NAME[arm]['parent']
    wrist = np.array(BY_NAME[palm]['pivot']) * [-1, 1, 1]
    lo = np.array([-170, -75, -70, -140], dtype=float)
    hi = np.array([80, 75, 0 if side == 'left' else 70, 5], dtype=float)
    rows, fore_rows, palm_rows, previous = {}, {}, {}, None
    for t, target, weight, palm_pitch in targets:
        ar = sample(clip['bones'].get(arm, {}).get('rotation'), t, [0, 0, 0])
        fr = sample(clip['bones'].get(fore, {}).get('rotation'), t, [0, 0, 0])
        base = rig_matrix(parent, clip, t)
        authored = np.array([ar[0], ar[1], ar[2], fr[0]])
        q = authored.copy() if previous is None or weight < .5 else previous.copy()
        if side == 'left' and weight > 0:
            q[2] = min(q[2], 0)  # The mirrored arm flares toward its own side, as rig.plant keeps it.

        def point(v):
            return (base @ rig_local(arm, clip, t, v[:3]) @ rig_local(fore, clip, t, [v[3], 0, 0]) @ [*wrist, 1])[:3]
        goal = np.array(target) * weight + point(authored) * (1 - weight)
        if weight <= 1e-6:
            q = authored
        else:
            pull, prior = .02 + .5 * home(t), (q.copy() if previous is None else previous)
            for _ in range(60):
                error = goal - point(q)
                if np.linalg.norm(error) < .02 and home(t) <= 0:
                    break
                jac = np.column_stack([(point(q + np.eye(4)[i] * .1) - point(q)) / .1 for i in range(4)])
                # Least squares on the wrist, with soft pulls toward the last tick and toward home.
                lhs = jac.T @ jac + np.eye(4) * (.005 + .002 + pull * home(t))
                rhs = jac.T @ error - .002 * (q - prior) - pull * home(t) * (q - authored)
                q = np.clip(q + np.clip(np.linalg.solve(lhs, rhs), -10, 10), lo, hi)
            fade = weight * weight * (3 - 2 * weight)
            q = authored * (1 - fade) + q * fade
        previous = q
        key = f'{t:.4f}'
        rows[key] = [round(float(v), 4) for v in q[:3]]
        fore_rows[key] = [round(float(q[3]), 4), 0, 0]
        old = sample(clip['bones'].get(palm, {}).get('rotation'), t, [0, 0, 0])
        spine = sample(clip['bones'].get('spine', {}).get('rotation'), t, [0, 0, 0])[0] + BY_NAME['spine'].get('rotation', [0, 0, 0])[0]
        pitch = palm_pitch - spine - q[0] - q[3] - BY_NAME[arm].get('rotation', [0, 0, 0])[0] - BY_NAME[fore].get('rotation', [0, 0, 0])[0]
        fade = weight * weight * (3 - 2 * weight)
        palm_rows[key] = [round(float(old[0] * (1 - fade) + pitch * fade), 4), round(float(old[1] * (1 - fade)), 4), round(float(old[2] * (1 - fade)), 4)]
    for name, rows_ in ((arm, rows), (fore, fore_rows), (palm, palm_rows)):
        clip['bones'].setdefault(name, {})['rotation'] = rows_


def hands(clip, paths, pitch, home=lambda t: 0.):
    """Solve each wrist onto its authored path (rig space) per tick: planted claws hold their
    spot, free claws follow their arc. paths[side] = (keys [(time, point[, shape])], weight(t));
    pitch[side] = [(time, palm pitch)] as rig.plant takes it. home(t) eases each elbow back to
    the authored (crawl) arm, for clips that end on it."""
    n = round(clip['animation_length'] * 20)
    for side in ('right', 'left'):
        keys, weight = paths[side]
        path = curve(keys)
        palm = curve([(t, (v, v, v)) for t, v in pitch[side]])
        reach(clip, side, [(i / 20, list(path(i / 20)), weight(i / 20), palm(i / 20)[0]) for i in range(n + 1)], home)
        if clip['loop']:
            # The solve walks the loop once; close its seam exactly.
            for part in ('arm_', 'forearm_', 'hand_'):
                frames = clip['bones'][part + side]['rotation']
                keys = sorted(frames, key=float)
                frames[keys[-1]] = list(frames[keys[0]])


def swipe(clips):
    """Swipe, 1.8 s. The server turns it with its target until 0.6 s, marks the arc, and lands
    the blow across its whole front at 0.9 s (SWIPE impact 18). It sinks back onto its haunches
    and shifts its free hand in, then rears and twists away, the right arm cocked high and wide
    behind it with the jaws gaping; it hangs there, quivering, until 0.72 s. The arm whips round
    through the front of the arc at 0.86 s, the torso wrenching after it, and the claw smashes
    into the ground at its left front at 1.1 s; it holds over the claw, heaving, then drags the
    arm home and settles back onto all fours."""
    c = Colossal(clips, 'colossus_swipe', 1.8)
    c.pos('colossus', [(0, None), (.25, (0, -12.5, 2.5)), (.55, (0, -3.5, 1.5), 'slow'), (.72, (0, -3, 2)),
                       (.88, (0, -5.5, -2.5), 'whip'), (1.1, (0, -9.5, -3), 'in'), (1.22, (0, -8.6, -3), 'out'),
                       (1.45, (0, -9, -3)), (1.8, None, 'slow')])
    c.rot('spine', [(0, None), (.25, (49, -6, 3)), (.55, (14, 24, -8), 'slow'), (.72, (11, 27, -9)),
                    (.86, (24, -10, 4), 'whip'), (.98, (32, -28, 10), 'out'), (1.1, (40, -30, 10), 'in'),
                    (1.22, (37, -28, 9), 'out'), (1.45, (39, -26, 8)), (1.8, None, 'slow')])
    # The skull keeps its stare on the target while the torso winds, then is wrenched round.
    c.rot('skull', [(0, None), (.25, (-66, 8, -4)), (.55, (-40, -22, 8), 'slow'), (.72, (-36, -25, 9)),
                    (.88, (-46, 0, 0), 'whip'), (1.1, (-62, 14, -6), 'in'), (1.45, (-60, 12, -5)), (1.8, None, 'slow')], lag=.04)
    c.rot('jaw', [(0, None), (.25, (12, 0, 0)), (.55, (40, 0, 0), 'slow'), (.72, (46, 0, 0)), (.86, (8, 0, 0), 'snap'),
                  (1.1, (26, 0, 0)), (1.3, (14, 0, 0)), (1.45, (20, 0, 0)), (1.8, None, 'slow')])
    # The swinging arm, authored joint by joint while it is free of the ground: cocked high and
    # out behind its right shoulder, whipped round through the front, brought down at its left.
    seed_arm, seed_fore = c.seed['arm_right'], c.seed['forearm_right'][0]
    c.rot('arm_right', [(0, seed_arm), (.12, seed_arm), (.3, (-60, 70, 10)), (.55, (-105, 115, -10), 'slow'),
                        (.72, (-110, 118, -10)), (.8, (-96, 72, -8), 'in'), (.86, (-72, 8, -4), 'linear'),
                        (.92, (-66, -26, 0), 'out'), (1.0, (-56, -28, 0), 'out'), (1.1, (-46, -24, 0), 'in'),
                        (1.45, (-46, -24, 0)), (1.8, seed_arm, 'slow')])
    c.rot('forearm_right', [(0, (seed_fore, 0, 0)), (.12, (seed_fore, 0, 0)), (.3, (-50, 0, 0)), (.55, (-55, 0, 0), 'slow'),
                            (.72, (-58, 0, 0)), (.8, (-40, 0, 0), 'in'), (.86, (-12, 0, 0), 'linear'), (.92, (-8, 0, 0)),
                            (1.0, (-16, 0, 0)), (1.1, (-24, 0, 0)), (1.45, (-24, 0, 0)), (1.8, (seed_fore, 0, 0), 'slow')])
    c.rot('hand_right', [(0, c.seed['hand_right']), (.12, c.seed['hand_right']), (.3, (20, 0, 0)), (.55, (30, -10, 0)),
                         (.72, (34, -12, 0)), (.86, (-10, 20, 0), 'linear'), (.95, (-30, 24, 0)), (1.1, (-20, 0, 0)),
                         (1.8, c.seed['hand_right'], 'slow')])
    # The cocked claw shivers with strain while it waits.
    c.add('arm_right', 'rotation', lambda t: np.array([1.5, 1, 1.5]) * math.sin(t * math.tau * 11) * envelope(t, .5, .1, .12, .06))
    claw(c, 'right', [(0, None), (.12, 20), (.3, -10), (.55, -40), (.72, -44), (.84, -30, 'whip'), (.98, 30), (1.1, 62, 'in'),
                      (1.45, 58), (1.62, -6), (1.75, 20), (1.8, None)])
    claw(c, 'left', [(0, None), (.15, 10), (.28, -14), (.4, 40, 'in'), (.86, 55), (1.1, 62), (1.45, 50), (1.55, -10), (1.7, 30, 'in'), (1.8, None)])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    # Right: planted, then free from 0.3 to 1.0 s; it bites the ground at its left front and is
    # dragged home along the floor. Left: steps in under the rearing chest, braces, steps back.
    slam = (-8, 5, -52)
    right = [(0, REST_WRIST['right']), (.12, REST_WRIST['right']), (.3, (26, 16, -36)), (1.0, slam), (1.1, slam, 'in'),
             (1.45, slam), (1.6, (-2, 14, -42)), (1.74, REST_WRIST['right'], 'in'), (1.8, REST_WRIST['right'])]
    left = [(0, REST_WRIST['left']), (.15, REST_WRIST['left']), (.3, (-23, 14, -37)), (.46, (-27, 5, -37), 'ease'),
            (1.45, (-27, 5, -37)), (1.57, (-21, 15, -38)), (1.7, REST_WRIST['left'], 'in'), (1.8, REST_WRIST['left'])]
    hands(clip, {'right': (right, lambda t: 1 - shaped(t, .12, .3) + shaped(t, 1.0, 1.1)), 'left': (left, lambda t: 1.)},
          {'right': [(0, -65), (.12, -65), (.3, -30), (1.0, -50), (1.1, -65), (1.45, -65), (1.6, -40), (1.74, -65), (1.8, -65)],
           'left': [(0, -65), (.15, -65), (.3, -40), (.46, -65), (1.45, -65), (1.57, -35), (1.7, -65), (1.8, -65)]},
          home=lambda t: shaped(t, 1.4, 1.8))
    return clip


def cast_hands(clips):
    """Grasping Hands, 2.4 s. The rings mark the players at once; both claws strike the floor at
    0.25 s (CLAW_STRIKE, 5 ticks: dust and a crack at each claw) and the rings close at 1.6 s
    (HANDS impact 32). It heaves its chest up with both claws raised, drives them into the earth,
    then presses down on them, sinking and trembling, the claws slowly hooking deeper while a
    growl opens its jaws. At the close it wrenches both claws shut and rakes them back toward
    its chest, holds the grip, and lets go."""
    c = Colossal(clips, 'colossus_cast_hands', 2.4)
    c.pos('colossus', [(0, None), (.17, (0, -4.5, 1), 'out'), (.25, (0, -13, -1), 'whip'), (.34, (0, -12.2, -1), 'out'),
                       (.5, (0, -13, -1)), (1.45, (0, -14.2, -1), 'slow'), (1.6, (0, -11, 1), 'snap'), (1.85, (0, -11.5, 1)),
                       (2.4, None, 'slow')])
    c.rot('spine', [(0, None), (.17, (22, 0, 0), 'out'), (.25, (52, 0, 0), 'whip'), (.34, (49, 0, 0), 'out'), (.5, (52, 0, 0)),
                    (1.45, (56, 0, 0), 'slow'), (1.6, (40, 0, 0), 'snap'), (1.85, (42, 0, 0)), (2.4, None, 'slow')])
    c.rot('skull', [(0, None), (.17, (-30, 0, 0), 'out'), (.25, (-52, 0, 0), 'whip'), (.5, (-54, 0, 0)),
                    (1.45, (-58, 0, 0), 'slow'), (1.6, (-40, 0, 0), 'snap'), (1.85, (-46, 0, 0)), (2.4, None, 'slow')], lag=.03)
    c.rot('jaw', [(0, None), (.17, (34, 0, 0), 'out'), (.25, (6, 0, 0), 'snap'), (.5, (8, 0, 0)), (1.45, (30, 0, 0), 'slow'),
                  (1.6, (44, 0, 0), 'snap'), (1.85, (36, 0, 0)), (2.4, None, 'slow')])
    # Pressing down: a strain that grows until the clench, carried through the shoulders.
    strain = lambda t: shaped(t, .45, 1.4) * (1 - shaped(t, 1.5, 1.6))
    c.add('spine', 'rotation', lambda t: np.array([math.sin(t * math.tau * 8.5), math.sin(t * math.tau * 6.5) * .6, 0]) * 1.2 * strain(t))
    c.add('skull', 'rotation', lambda t: np.array([0, math.sin(t * math.tau * 7.5 + 1), math.sin(t * math.tau * 9 + 2)]) * 1.5 * strain(t))
    for side in ('right', 'left'):
        claw(c, side, [(0, None), (.12, -30), (.2, -38), (.25, 20, 'whip'), (.4, 24), (1.45, 40, 'slow'), (1.6, 70, 'snap'),
                       (1.85, 66), (2.05, 10), (2.4, None)])
    c.add('soul_core', 'scale', curve([(0, 1), (.25, 1.3, 'snap'), (.6, 1), (1.45, 1.4, 'slow'), (1.6, 1.8, 'snap'), (1.9, 1), (2.4, 1)]))
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    paths, pitch = {}, {}
    for side, sign in (('right', 1), ('left', -1)):
        strike = (sign * 23, 5, -38)
        paths[side] = ([(0, REST_WRIST[side]), (.17, (sign * 22, 32, -30), 'out'), (.25, strike, 'whip'), (1.45, strike),
                        (1.6, (sign * 20, 5, -30), 'out'), (1.85, (sign * 20, 5, -30)), (2.1, (sign * 20, 14, -38)),
                        (2.3, REST_WRIST[side], 'in'), (2.4, REST_WRIST[side])], lambda t: 1.)
        pitch[side] = [(0, -65), (.17, -42), (.25, -72), (1.45, -72), (1.6, -60), (1.85, -60), (2.1, -40), (2.3, -65), (2.4, -65)]
    hands(clip, paths, pitch, home=lambda t: shaped(t, 1.9, 2.4))
    return clip


def cast_bolt(clips):
    """Soul Bolt, 2 s. Soul fire kindles at the jaws until the first skull leaves at 0.6 s, then
    one every 0.3 s (0.9, 1.2, 1.5) and a fifth at 1.8 s for a noticed Astral Double (BOLT impact
    12, every 6 ticks for the colossus). It rears back onto its haunches, claws lifted before its
    chest and jaws prising open, and looks down its length at the target; each skull is spat
    with a lunge of the skull that jolts the chest back, the claws flinching. After the last it
    hangs there a moment, then drops back onto its claws with its whole weight."""
    c = Colossal(clips, 'colossus_cast_bolt', 2)
    spits = (.6, .9, 1.2, 1.5)
    body = [(0, None), (.42, (0, -3, 1.5), 'slow'), (.52, (0, -2.6, 1.8))]
    spine = [(0, None), (.42, (8, 0, 0), 'slow'), (.52, (6, 0, 0))]
    skull = [(0, None), (.42, (-12, 0, 0), 'slow'), (.52, (-10, 0, 0))]
    jaw = [(0, None), (.3, (14, 0, 0)), (.52, (28, 0, 0), 'slow')]
    for k, t in enumerate(spits):
        sway = (-1) ** k
        body += [(t, (0, -3.2, .6), 'whip'), (t + .1, (0, -2.5, 2.4), 'out'), (t + .26, (0, -2.7, 1.6))]
        spine += [(t, (13, 0, 0), 'whip'), (t + .1, (2, 3 * sway, 0), 'out'), (t + .26, (5, 1 * sway, 0))]
        skull += [(t, (-30, 0, 0), 'whip'), (t + .1, (-8, -5 * sway, 2 * sway), 'out'), (t + .26, (-12, -2 * sway, 0))]
        jaw += [(t - .04, (32, 0, 0)), (t, (60, 0, 0), 'snap'), (t + .1, (34, 0, 0), 'out'), (t + .24, (42, 0, 0))]
    # The drop: it falls forward onto its claws, the chest dipping below rest, and settles.
    c.pos('colossus', body[:-1] + [(1.75, (0, -3.4, 1.4), 'ease'), (1.9, (0, -11.5, -1), 'in'), (2, None, 'out')])
    c.rot('spine', spine[:-1] + [(1.75, (6, 0, 0), 'ease'), (1.9, (48, -2, 1), 'in'), (2, None, 'out')])
    c.rot('skull', skull[:-1] + [(1.75, (-14, 0, 0), 'ease'), (1.9, (-50, 3, -2), 'in'), (2, None, 'out')], lag=.03)
    c.rot('jaw', jaw[:-1] + [(1.75, (20, 0, 0), 'out'), (2, None, 'slow')])
    c.add('soul_core', 'scale', curve([(0, 1), (.52, 1.5, 'slow')] + [p for t in spits for p in ((t, 1.9, 'snap'), (t + .2, 1.4))] + [(2, 1)]))
    # The claws come up off the ground before its chest, flinching at each spit.
    for side, sign in (('right', 1), ('left', -1)):
        seed_arm, seed_fore = c.seed['arm_' + side], c.seed['forearm_' + side][0]
        flinch = [(t + d, (-8 - 10 * (d == .06), sign * 12, sign * (4 + 6 * (d == .06))), shape) for t in spits for d, shape in ((.06, 'out'), (.22, 'ease'))]
        c.rot('arm_' + side, [(0, seed_arm), (.1, seed_arm), (.42, (-8, sign * 12, sign * 4), 'slow')] + flinch
              + [(1.75, (-8, sign * 12, sign * 4)), (1.92, seed_arm, 'in'), (2, seed_arm)])
        c.rot('forearm_' + side, [(0, (seed_fore, 0, 0)), (.1, (seed_fore, 0, 0)), (.42, (-80, 0, 0), 'slow'),
                                  (1.75, (-80, 0, 0)), (1.92, (seed_fore, 0, 0), 'in'), (2, (seed_fore, 0, 0))])
        c.rot('hand_' + side, [(0, c.seed['hand_' + side]), (.1, c.seed['hand_' + side]), (.42, (30, 0, 0), 'slow'),
                               (1.75, (30, 0, 0)), (1.92, c.seed['hand_' + side], 'in'), (2, c.seed['hand_' + side])])
        claw(c, side, [(0, None), (.12, 22), (.42, -28, 'slow')] + [p for t in spits for p in ((t + .06, -40, 'snap'), (t + .22, -24))]
             + [(1.75, -20), (1.9, 30, 'in'), (2, None)])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    lift = lambda t: 1 - shaped(t, .08, .32) + shaped(t, 1.78, 1.92)
    hands(clip, {side: ([(0, REST_WRIST[side]), (2, REST_WRIST[side])], lift) for side in ('right', 'left')},
          {side: [(0, -65), (2, -65)] for side in ('right', 'left')}, home=lambda t: shaped(t, 1.88, 2))
    return clip


# ---------------- The charge: crouch → coil (loops) → set (held) ----------------
# The server crouches it for RUSH_CROUCH (0.6 s), loops the coil until the aim locks RUSH_LOCK
# (0.3 s) before launch, then holds the set. Dirt flies at its right forefoot when the coil loop
# starts and at its left 0.4 s in, so each claw rakes the ground right there.

COIL = {('colossus', 'position'): (0, -14, 2), ('spine', 'rotation'): (57, 0, 0), ('skull', 'rotation'): (-71, 0, 0),
        ('jaw', 'rotation'): (8, 0, 0)}
GRIP = {'right': (21, 5, -48), 'left': (-21, 5, -48)}


def rush_crouch(clips):
    """Charge, part one, 0.6 s: a small lift as it fixes its eyes on the target, then it sinks its
    chest low over the forefeet, haunches coiled beneath it, claws spreading to grip."""
    c = Colossal(clips, 'colossus_rush_crouch', .6)
    c.pos('colossus', [(0, None), (.14, (0, -9, -.5), 'out'), (.6, COIL[('colossus', 'position')], 'slow')])
    c.rot('spine', [(0, None), (.14, (40, 0, 0), 'out'), (.6, COIL[('spine', 'rotation')], 'slow')])
    c.rot('skull', [(0, None), (.14, (-52, 0, 0), 'out'), (.6, COIL[('skull', 'rotation')], 'slow')], lag=.03)
    c.rot('jaw', [(0, None), (.14, (2, 0, 0)), (.6, COIL[('jaw', 'rotation')], 'slow')])
    for side in ('right', 'left'):
        claw(c, side, [(0, None), (.2, 4), (.6, 44, 'slow')])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    # It ends as the coil begins: right claw set to rake, left already raked back.
    end = {'right': GRIP['right'], 'left': (GRIP['left'][0], 5, GRIP['left'][2] + 5)}
    hands(clip, {side: ([(0, REST_WRIST[side]), (.15, REST_WRIST[side]), (.32, (GRIP[side][0], 9, -46)), (.48, end[side], 'in'),
                         (.6, end[side])], lambda t: 1.) for side in ('right', 'left')},
          {side: [(0, -65), (.15, -65), (.32, -45), (.48, -80), (.6, -80)] for side in ('right', 'left')})
    return clip


def rush_coil(clips):
    """Charge, part two, a 0.8 s loop: its weight rolls onto one forefoot while the other lifts,
    sets down and rakes back through the dirt, then the other way. The shoulders roll with the
    load, the hind feet stay planted, and the skull counter-turns so the stare never leaves the
    target; a low rattle works its jaw."""
    c = Colossal(clips, 'colossus_rush_coil', .8, loop=True, seed=clips['colossus_rush_crouch'], seed_time=.6)
    base = np.array(COIL[('colossus', 'position')])
    # Load right (its right is -X in position) across 0.0-0.3, left across 0.4-0.7.
    side_shift = lambda t: -2.4 * math.sin(math.tau * t / .8)
    c.add('colossus', 'position', lambda t: base + np.array([side_shift(t), -.8 * abs(math.sin(math.tau * t / .8)), 0]))
    roll = lambda t: math.sin(math.tau * t / .8)
    c.add('spine', 'rotation', lambda t: np.array([57 + 2.5 * abs(roll(t)), 7 * roll(t), -6 * roll(t)]))
    c.add('skull', 'rotation', lambda t: np.array([-71 - 2 * abs(roll(t)), -7 * roll(t + .04), 6 * roll(t + .04)]))
    c.add('jaw', 'rotation', lambda t: np.array([8 + 3 * math.sin(math.tau * t / .8 * 6) * .5 + 2 * abs(roll(t)), 0, 0]))
    for side, phase in (('right', 0.), ('left', .4)):
        # Each claw: lifts at the end of its half, sets down at the dirt burst and rakes back.
        claw(c, side, [(0, 44), (phase, 44), (phase + .06, 66, 'snap'), (phase + .16, 70), (phase + .32, 40), (phase + .4, 22),
                       (.8, 44)] if phase else
             [(0, 44), (.06, 66, 'snap'), (.16, 70), (.32, 40), (.4, 44), (.7, 44), (.76, 22), (.8, 44)])
    c.hold_rest()
    plant_hind(c, at_rest=True)
    clip = c.bake()
    paths = {}
    for side, phase in (('right', 0.), ('left', .4)):
        g = GRIP[side]
        keys = [(0, g), (.14, (g[0], 5, g[2] + 5), 'out'), (.6, (g[0], 5, g[2] + 5)), (.7, (g[0], 10, g[2] - 1)), (.8, g, 'in')] if not phase else \
               [(0, (g[0], 5, g[2] + 5)), (.2, (g[0], 5, g[2] + 5)), (.3, (g[0], 10, g[2] - 1)), (.4, g, 'in'), (.54, (g[0], 5, g[2] + 5), 'out'),
                (.8, (g[0], 5, g[2] + 5))]
        paths[side] = (keys, lambda t: 1.)
    hands(clip, paths, {'right': [(0, -80), (.14, -82), (.6, -82), (.7, -55), (.8, -80)],
                        'left': [(0, -82), (.2, -82), (.3, -55), (.4, -80), (.54, -82), (.8, -82)]})
    return clip


def rush_set(clips):
    """Charge, part three, 0.3 s and held: it goes dead still and loads lower, claws biting,
    jaws clamped shut, as the aim locks."""
    c = Colossal(clips, 'colossus_rush_set', .3, seed=clips['colossus_rush_crouch'], seed_time=.6)
    c.pos('colossus', [(0, COIL[('colossus', 'position')]), (.3, (0, -15.5, 3), 'slow')])
    c.rot('spine', [(0, COIL[('spine', 'rotation')]), (.3, (61, 0, 0), 'slow')])
    c.rot('skull', [(0, COIL[('skull', 'rotation')]), (.3, (-76, 0, 0), 'slow')])
    c.rot('jaw', [(0, COIL[('jaw', 'rotation')]), (.08, (14, 0, 0)), (.3, (1, 0, 0), 'snap')])
    for side in ('right', 'left'):
        claw(c, side, [(0, 44), (.3, 68, 'slow')])
    c.hold_rest()
    plant_hind(c, at_rest=True)
    clip = c.bake()
    hands(clip, {side: ([(0, GRIP[side]), (.3, (GRIP[side][0], 4.5, GRIP[side][2] + 1), 'slow')], lambda t: 1.) for side in ('right', 'left')},
          {side: [(0, -80), (.3, -86)] for side in ('right', 'left')})
    return clip


def dive(clips):
    """Grave Dive, 1.2 s, held at its end. The server makes it untouchable at 0.8 s (DIVE_UNDER)
    and hides it under the ground at 1.2 s (DIVE_SINK). It rears as high as it can, claws
    reaching for the sky and jaws wide, hangs there, then pitches over and plunges head first:
    the claws bite into the ground ahead at 0.62 s and haul the body down after them, the hind
    legs kicking free and trailing it under."""
    c = Colossal(clips, 'colossus_dive', 1.2)
    c.pos('colossus', [(0, None), (.38, (0, -2, 1.5), 'slow'), (.48, (0, -1.6, 2)), (.62, (0, -6, -8), 'in'),
                       (.8, (0, -13, -11), 'ease'), (.96, (0, -32, -13), 'in'), (1.1, (0, -68, -14)), (1.2, (0, -100, -14), 'linear')])
    c.rot('colossus', [(0, (0, 0, 0)), (.38, (-4, 0, 0), 'slow'), (.48, (-6, 0, 0)), (.62, (28, 0, 0), 'in'),
                       (.8, (44, 0, 0)), (1.0, (58, 0, 0)), (1.2, (64, 0, 0))])
    c.rot('spine', [(0, None), (.38, (8, 0, 0), 'slow'), (.48, (6, 0, 0)), (.62, (48, 0, 0), 'in'), (.8, (58, 0, 0)), (1.2, (60, 0, 0))])
    c.rot('skull', [(0, None), (.38, (-22, 0, 0), 'slow'), (.48, (-16, 0, 0)), (.62, (-42, 0, 0), 'in'), (.8, (-30, 0, 0)),
                    (1.2, (-24, 0, 0))], lag=.03)
    c.rot('jaw', [(0, None), (.3, (32, 0, 0)), (.48, (42, 0, 0)), (.6, (10, 0, 0), 'snap'), (1.2, (8, 0, 0))])
    for side, sign in (('right', 1), ('left', -1)):
        seed_arm, seed_fore = c.seed['arm_' + side], c.seed['forearm_' + side][0]
        c.rot('arm_' + side, [(0, seed_arm), (.1, seed_arm), (.38, (-150, 0, sign * 15), 'slow'), (.48, (-154, 0, sign * 17)),
                              (.62, (-110, 0, sign * 15), 'in'), (.8, (-60, 0, sign * 15)), (1.2, (-40, 0, sign * 10))])
        c.rot('forearm_' + side, [(0, (seed_fore, 0, 0)), (.1, (seed_fore, 0, 0)), (.38, (-25, 0, 0), 'slow'), (.48, (-22, 0, 0)),
                                  (.62, (-10, 0, 0), 'in'), (.8, (-15, 0, 0)), (1.2, (-20, 0, 0))])
        c.rot('hand_' + side, [(0, c.seed['hand_' + side]), (.1, c.seed['hand_' + side]), (.38, (20, 0, 0), 'slow'),
                               (.62, (-30, 0, 0)), (1.2, (-40, 0, 0))])
        claw(c, side, [(0, None), (.1, 24), (.38, -42, 'slow'), (.5, -46), (.62, 62, 'in'), (.8, 70), (1.2, 70)])
        # The hind legs kick free as the body tips over, scrabbling in turn as it claws itself
        # under, and trail it down.
        d = 0 if sign > 0 else .09
        c.rot('leg_' + side, [(0, CRAWL[('leg_' + side, 'rotation')]), (.55, (-36, 0, 0)), (.66 + d, (10, 0, sign * 4), 'out'),
                              (.76 + d, (52, 0, sign * 8), 'snap'), (.86 + d, (14, 0, sign * 4)), (.96 + d, (56, 0, sign * 8), 'snap'),
                              (1.2, (40, 0, sign * 6))])
        c.rot('shin_' + side, [(0, CRAWL[('shin_' + side, 'rotation')]), (.55, (88, 0, 0)), (.66 + d, (70, 0, 0)), (.76 + d, (20, 0, 0), 'snap'),
                               (.86 + d, (64, 0, 0)), (.96 + d, (16, 0, 0), 'snap'), (1.2, (20, 0, 0))])
    c.add('soul_core', 'scale', curve([(0, 1), (.4, 1.4, 'slow'), (.62, 1.1), (1.2, 1)]))
    plant_hind(c, weight=lambda t: 1 - shaped(t, .5, .66))
    clip = c.bake()
    # The claws bite at 0.62 s and pull: they stay in their holes while the body slides down past
    # them, then follow it under.
    hands(clip, {side: ([(0, REST_WRIST[side]), (.1, REST_WRIST[side]), (.56, (sign * 17, 5, -58)), (.86, (sign * 17, 3, -56)),
                         (1.04, (sign * 16, -40, -50), 'in'), (1.2, (sign * 16, -90, -44))],
                        lambda t: 1 - shaped(t, .08, .26) + shaped(t, .56, .64))
                 for side, sign in (('right', 1), ('left', -1))},
          {side: [(0, -65), (.1, -65), (.3, 0), (.56, -70), (1.2, -90)] for side in ('right', 'left')})
    return clip


def harvest(clips):
    """Soul Harvest, 2.8 s. It screams at 0.8 s (HARVEST_SCREAM: the shriek and a sonic boom
    above its skull); the called graves glow for a second and the souls claw out at 1.8 s. It
    kneels up off its claws, head bowed, and draws a long breath that swells its chest and spreads
    its arms; at 0.8 s it throws its head back and screams at the sky, back arched and arms swept
    behind it, trembling with the effort while the core blazes; as the souls rise it slumps, and
    drops back onto all fours with its whole weight, hind feet planted throughout."""
    c = Colossal(clips, 'colossus_harvest', 2.8)
    c.pos('colossus', [(0, None), (.5, (0, -3.5, 1), 'slow'), (.75, (0, -4.2, 1.2)), (.84, (0, -1.2, 2), 'snap'),
                       (1.8, (0, -1.6, 2)), (2.25, (0, -4.5, 1), 'ease'), (2.55, (0, -11, -.5), 'in'), (2.8, None, 'out')])
    tremble = lambda t, a: a * math.sin(t * math.tau * 9) * shaped(t, .82, .95) * (1 - shaped(t, 1.7, 1.85))
    c.rot('spine', [(0, None), (.5, (8, 0, 0), 'slow'), (.75, (14, 0, 0)), (.84, (-16, 0, 0), 'snap'), (1.8, (-12, 0, 0)),
                    (2.25, (16, 0, 0), 'ease'), (2.55, (48, 0, 0), 'in'), (2.8, None, 'out')])
    c.add('spine', 'rotation', lambda t: np.array([tremble(t, 2), tremble(t + .05, 1.5), 0]))
    # Negative neck and skull pitch lift the face: at the scream the jaws point at the sky.
    c.rot('neck', [(0, (0, 0, 0)), (.5, (6, 0, 0), 'slow'), (.75, (10, 0, 0)), (.84, (-32, 0, 0), 'snap'), (1.8, (-28, 0, 0)),
                   (2.3, (0, 0, 0), 'ease'), (2.8, (0, 0, 0))])
    c.rot('skull', [(0, None), (.5, (-8, 0, 0), 'slow'), (.75, (-2, 0, 0)), (.84, (-76, 0, 0), 'snap'), (1.8, (-72, 0, 0)),
                    (2.3, (-40, 0, 0), 'ease'), (2.55, (-62, 4, -2), 'in'), (2.8, None, 'out')], lag=.03)
    c.add('skull', 'rotation', lambda t: np.array([tremble(t + .02, 3), 0, tremble(t + .07, 3)]))
    c.rot('jaw', [(0, None), (.5, (2, 0, 0)), (.75, (0, 0, 0)), (.84, (72, 0, 0), 'snap'), (1.8, (66, 0, 0)), (2.0, (40, 0, 0)),
                  (2.3, (12, 0, 0)), (2.8, None)])
    c.add('jaw', 'rotation', lambda t: np.array([tremble(t + .03, 4), 0, 0]))
    c.add('soul_core', 'scale', curve([(0, 1), (.5, 1.3, 'slow'), (.75, 1.5), (.84, 2.2, 'snap'), (1.05, 1.8), (1.3, 2.2), (1.55, 1.8),
                                       (1.8, 2.1), (2.4, 1), (2.8, 1)]))
    for side, sign in (('right', 1), ('left', -1)):
        seed_arm, seed_fore = c.seed['arm_' + side], c.seed['forearm_' + side][0]
        # Positive X swings an arm back: spread low for the breath, swept behind the back to scream.
        c.rot('arm_' + side, [(0, seed_arm), (.1, seed_arm), (.5, (-15, 0, sign * 32), 'slow'), (.75, (-6, sign * 6, sign * 40)),
                              (.84, (82, sign * 8, sign * 14), 'snap'), (1.8, (78, sign * 8, sign * 12)), (2.25, (-30, 0, sign * 22), 'ease'),
                              (2.45, seed_arm, 'in'), (2.8, seed_arm)])
        c.rot('forearm_' + side, [(0, (seed_fore, 0, 0)), (.1, (seed_fore, 0, 0)), (.5, (-25, 0, 0), 'slow'), (.75, (-10, 0, 0)),
                                  (.84, (28, 0, 0), 'snap'), (1.8, (24, 0, 0)), (2.25, (-30, 0, 0), 'ease'), (2.45, (seed_fore, 0, 0), 'in'),
                                  (2.8, (seed_fore, 0, 0))])
        c.add('arm_' + side, 'rotation', lambda t, s=sign: np.array([tremble(t + .04 * s, 2.5), 0, tremble(t, 1.5) * s]))
        claw(c, side, [(0, None), (.1, 24), (.5, -18, 'slow'), (.75, 10), (.84, -46, 'snap'), (1.75, -44), (1.85, 66, 'snap'),
                       (2.25, 50), (2.5, 30, 'in'), (2.8, None)])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    hands(clip, {side: ([(0, REST_WRIST[side]), (2.8, REST_WRIST[side])],
                        lambda t: 1 - shaped(t, .06, .34) + shaped(t, 2.38, 2.56))
                 for side in ('right', 'left')}, {side: [(0, -65), (2.8, -65)] for side in ('right', 'left')}, home=lambda t: shaped(t, 2.5, 2.8))
    return clip


def idle(clips):
    """The crawl at rest, a 4 s loop, and the pose every colossus clip leaves from. One slow, deep
    breath heaves the ribcage and swells the core; its weight drifts from one forefoot to the
    other; the skull turns to look, cocks once and turns back; the jaw creaks open on the breath
    out and clacks shut; each claw lifts and re-grips the ground in turn. The hind feet and the
    gripping claws stay where they are."""
    # The first frame is the crawl's rest pose itself, and its solved arms seed every other clip.
    seed = {'bones': {'arm_right': {'rotation': {'0': [-3, -4, -2]}}, 'forearm_right': {'rotation': {'0': [0, 0, 0]}},
                      'hand_right': {'rotation': {'0': [0, 0, 0]}}, 'arm_left': {'rotation': {'0': [-8, 3, 3]}},
                      'forearm_left': {'rotation': {'0': [0, 0, 0]}}, 'hand_left': {'rotation': {'0': [0, 0, 0]}}}}
    c = Colossal(clips, 'colossus_idle', 4, loop=True, seed=seed)
    L = 4.
    inhale = lambda t: math.sin(math.pi / 2 * min(1., (t % L) / 1.6)) ** 2 if (t % L) < 1.6 else math.cos(math.pi / 2 * ((t % L) - 1.6) / 2.4) ** 2
    sway = lambda t: math.sin(math.tau * t / L)
    rest_pos, rest_spine, rest_skull = (np.array(CRAWL[k]) for k in (('colossus', 'position'), ('spine', 'rotation'), ('skull', 'rotation')))
    c.add('colossus', 'position', lambda t: rest_pos + np.array([-.9 * sway(t), .9 * inhale(t), 0]))
    c.add('spine', 'rotation', lambda t: rest_spine + np.array([-3.5 * inhale(t), 1.5 * sway(t), -2 * sway(t)]))
    look = curve([(0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.8, (-4, -12, 4), 'slow'), (2.5, (-3, -13, 5)), (2.58, (-6, -10, -3), 'snap'),
                  (3.1, (-5, -10, -2)), (3.8, (0, 0, 0), 'slow'), (4, (0, 0, 0))], L)
    c.add('skull', 'rotation', lambda t: rest_skull + look(t) + np.array([2.5 * inhale(t - .15), 0, 0]))
    c.add('jaw', 'rotation', curve([(0, (4, 0, 0)), (1.6, (3, 0, 0)), (2.3, (13, 0, 0), 'slow'), (2.42, (2, 0, 0), 'snap'),
                                    (2.6, (5, 0, 0)), (4, (4, 0, 0))], L))
    c.add('soul_core', 'scale', lambda t: np.array([1 - .1 * inhale(t), 1 + .18 * inhale(t), 1 - .1 * inhale(t)]))
    claw(c, 'right', [(0, None), (.7, None), (.85, -6, 'out'), (1.15, 30, 'in'), (1.5, 18), (4, None)])
    claw(c, 'left', [(0, None), (2.7, None), (2.85, -6, 'out'), (3.15, 30, 'in'), (3.5, 18), (4, None)])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    rest = REST_WRIST
    hands(clip, {'right': ([(0, rest['right']), (.7, rest['right']), (.9, (20, 8, -45)), (1.15, (20, 5, -44), 'in'), (4, rest['right'])], lambda t: 1.),
                 'left': ([(0, rest['left']), (2.7, rest['left']), (2.9, (-20, 8, -45)), (3.15, (-20, 5, -44), 'in'), (4, rest['left'])], lambda t: 1.)},
          {'right': [(0, -65), (.7, -65), (.9, -40), (1.15, -65), (4, -65)], 'left': [(0, -65), (2.7, -65), (2.9, -40), (3.15, -65), (4, -65)]})
    return clip


def cast_drain(clips):
    """Life Drain, 4.2 s. A sniff and a heartbeat open it; the first pulse lands at 1.5 s and one
    every 0.5 s after (DRAIN impact 30, DRAIN_INTERVAL 10), six in all, the strands running into
    a focus before its jaws. It rears onto its haunches, sniffing, and reaches one claw out at its
    victim, fingers spread and trembling as the motes gather; on every pulse the claw clenches
    and drags back a little, the chest heaves, the core flares and the jaws gulp; after the last
    it drops back onto its claws."""
    c = Colossal(clips, 'colossus_cast_drain', 4.2)
    pulses = [1.5 + .5 * k for k in range(6)]
    beat = lambda t, a: a * sum(envelope(t, p - .02, .06, .04, .3, up='snap') for p in pulses) * (1 - shaped(t, 4.06, 4.2))
    c.pos('colossus', [(0, None), (.6, (0, -3, 1.5), 'slow'), (1.4, (0, -2.6, 1.6)), (3.9, (0, -2.8, 1.4)),
                       (4.12, (0, -11, -1), 'in'), (4.2, None, 'out')])
    c.add('colossus', 'position', lambda t: np.array([0, -.6 * beat(t, 1), .5 * beat(t, 1)]))
    c.rot('spine', [(0, None), (.6, (10, 4, 0), 'slow'), (1.4, (8, 5, 0)), (3.9, (9, 4, 0)), (4.12, (48, -2, 1), 'in'), (4.2, None, 'out')])
    c.add('spine', 'rotation', lambda t: np.array([-3 * beat(t, 1), 0, 0]))
    # Sniffing up the scent, then the head held on the victim, jaws prised wide for the focus.
    c.rot('skull', [(0, None), (.25, (-8, 0, 0)), (.35, (-14, 2, 0), 'snap'), (.45, (-8, 0, 0)), (.55, (-15, -2, 0), 'snap'),
                    (.8, (-18, -4, 0), 'slow'), (3.9, (-20, -4, 0)), (4.12, (-52, 3, -2), 'in'), (4.2, None, 'out')], lag=.03)
    c.rot('jaw', [(0, None), (.3, (10, 0, 0)), (.36, (4, 0, 0)), (.5, (12, 0, 0)), (.56, (5, 0, 0)), (1.45, (50, 0, 0), 'slow'),
                  (3.9, (54, 0, 0)), (4.2, None, 'in')])
    c.add('jaw', 'rotation', lambda t: np.array([-12 * beat(t, 1), 0, 0]))
    c.add('soul_core', 'scale', lambda t: np.ones(3) * (1 + .25 * shaped(t, .6, 1.5) * (1 - shaped(t, 3.9, 4.2)) + .45 * beat(t, 1)))
    # The reaching claw (right): out at the victim, opening as the motes gather, clenching on
    # each pulse. The left hangs before the chest.
    seed = {k: c.seed[k] for k in c.seed}
    c.rot('arm_right', [(0, seed['arm_right']), (.12, seed['arm_right']), (.7, (-62, 14, 14), 'slow'), (1.45, (-70, 12, 12)),
                        (3.9, (-68, 12, 12)), (4.08, seed['arm_right'], 'in'), (4.2, seed['arm_right'])])
    c.add('arm_right', 'rotation', lambda t: np.array([6 * beat(t, 1), 0, 0]))
    c.rot('forearm_right', [(0, (seed['forearm_right'][0], 0, 0)), (.12, (seed['forearm_right'][0], 0, 0)), (.7, (-20, 0, 0), 'slow'),
                            (3.9, (-18, 0, 0)), (4.08, (seed['forearm_right'][0], 0, 0), 'in'), (4.2, (seed['forearm_right'][0], 0, 0))])
    c.add('forearm_right', 'rotation', lambda t: np.array([-10 * beat(t, 1), 0, 0]))
    c.rot('hand_right', [(0, seed['hand_right']), (.12, seed['hand_right']), (.7, (6, 0, 0), 'slow'), (3.9, (6, 0, 0)),
                         (4.08, seed['hand_right'], 'in'), (4.2, seed['hand_right'])])
    tremble = lambda t: math.sin(t * math.tau * 10) * 1.5 * shaped(t, .7, 1.4) * (1 - shaped(t, 1.45, 1.5))
    c.add('arm_right', 'rotation', lambda t: np.array([tremble(t), tremble(t + .03), 0]))
    # Each pulse clenches the claw; it reopens between them, and after the last it plants.
    clench = lambda shut, open_: [k for t in pulses for k in ((t, shut, 'snap'), (t + .3, open_)) if k[0] < 4.0] + [(4.0, shut, 'snap')]
    claw(c, 'right', [(0, None), (.12, 20), (.7, -36, 'slow'), (1.45, -44)] + clench(46, -30) + [(4.1, 30, 'in'), (4.2, None)])
    c.rot('arm_left', [(0, seed['arm_left']), (.12, seed['arm_left']), (.6, (-8, -12, -4), 'slow'), (3.9, (-8, -12, -4)),
                       (4.08, seed['arm_left'], 'in'), (4.2, seed['arm_left'])])
    c.rot('forearm_left', [(0, (seed['forearm_left'][0], 0, 0)), (.12, (seed['forearm_left'][0], 0, 0)), (.6, (-80, 0, 0), 'slow'),
                           (3.9, (-80, 0, 0)), (4.08, (seed['forearm_left'][0], 0, 0), 'in'), (4.2, (seed['forearm_left'][0], 0, 0))])
    c.rot('hand_left', [(0, seed['hand_left']), (.12, seed['hand_left']), (.6, (30, 0, 0), 'slow'), (3.9, (30, 0, 0)),
                        (4.08, seed['hand_left'], 'in'), (4.2, seed['hand_left'])])
    claw(c, 'left', [(0, None), (.12, 22), (.6, -10, 'slow')] + clench(20, -6) + [(4.1, 30, 'in'), (4.2, None)])
    c.hold_rest()
    plant_hind(c)
    clip = c.bake()
    lift = lambda t: 1 - shaped(t, .06, .3) + shaped(t, 4.0, 4.12)
    hands(clip, {side: ([(0, REST_WRIST[side]), (4.2, REST_WRIST[side])], lift) for side in ('right', 'left')},
          {side: [(0, -65), (4.2, -65)] for side in ('right', 'left')}, home=lambda t: shaped(t, 4.1, 4.2))
    return clip


def plant_hind_in(clips, name):
    """Plant the hind feet of an older clip after the fact: its body keys stay exactly as they
    were (so the grab and bite sockets the server samples do not move), and only the legs and
    feet are re-solved to stand still."""
    baked = clips[name]
    c = Clip(clips, name, baked['animation_length'], baked['loop'])
    for bone_, channels in baked['bones'].items():
        for channel, frames in channels.items():
            c.add(bone_, channel, lambda t, f=frames, ch=channel: np.array(sample(f, t, [1, 1, 1] if ch == 'scale' else [0, 0, 0]), dtype=float))
    plant_hind(c)
    for side in ('right', 'left'):
        for part in ('leg_', 'shin_', 'foot_'):
            table = c.solved[(part + side, 'rotation')]
            baked['bones'].setdefault(part + side, {})['rotation'] = {f'{t:.4f}': [round(float(x), 4) for x in v] for t, v in sorted(table.items())}
