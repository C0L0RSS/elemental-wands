"""The robed caster's reworked clips: an elusive, theatrical mage who teleports, ambushes and
channels. Every clip starts and ends on the idle's first pose, so the controller's short
crossfades land on matching poses; his soles stay where they stand unless he steps, and the
staff's iron butt stays on the floor while it is planted.

Directions (model, as GeckoLib applies them): rotation +X tips a part's top forward (a hanging
arm's hand swings back, so raising an arm forward is negative X); +Y turns toward his right (his
right shoulder goes back); +Z on a hanging part swings its lower end toward his right. Position
+X moves toward his left, -Z forward. Times are seconds; the server's keyed ticks are noted with
each clip.
"""
import math
import numpy as np
from motion import Clip, curve, noise, follow, shaped, envelope

SOLE = {'right': (-2, 0, -.5), 'left': (2, 0, -.5)}  # boot soles, on the shins
STANCE = {'right': (2, 0, -.5), 'left': (-2, 0, -.5)}  # where the idle stands them (rig space)
BUTT = (-5, 1, 1)                                      # the staff's iron butt, on the staff
HEAD = (-5, 31, 1)                                     # the staff's skull
FINGERS = [f'mage_finger_{side}_{k}' for side in ('right', 'left') for k in range(3)]


def breath(t, period, phase=0.):
    """0 → 1 → 0 once a period: a slow inhale and a slower exhale."""
    f = ((t / period + phase) % 1)
    return math.sin(math.pi / 2 * f / .42) ** 2 if f < .42 else math.cos(math.pi / 2 * (f - .42) / .58) ** 2


def plant_feet(c, start=0., times=None, extra=(), spots=None):
    """Keep both soles flat on the floor where they stand at `start` (or at `spots`); the knees
    and hips bend to take the body."""
    goals, dofs, limits = [], [], {}
    for side in ('right', 'left'):
        sole = c.point('mage_shin_' + side, SOLE[side], start) * [1, 0, 1] if not spots else np.array(spots[side])
        goals.append(('mage_shin_' + side, SOLE[side], lambda t, s=sole: s, 1.))
        dofs += [('mage_leg_' + side, 0), ('mage_leg_' + side, 2), ('mage_shin_' + side, 0)]
        limits.update({('mage_leg_' + side, 0): (-120, 70), ('mage_leg_' + side, 2): (-30, 30),
                       ('mage_shin_' + side, 0): (0, 150)})
    goals += list(extra)
    return c.solve(goals, dofs, times, limits)


def plant_staff(c, where=None, times=None, weight=lambda t: 1., start=0., reach_back=60):
    """Keep the staff's butt where it stands at `start` (or at `where`) while the body moves
    around it (right arm solve). At full stretch his arm holds the butt a hand's breadth off the
    floor, as the rest pose does."""
    if where is None:
        where = c.point('staff', BUTT, start)
    goal = [('staff', BUTT, where if callable(where) else (lambda t: where), weight)]
    dofs = [('right_arm', 0), ('right_arm', 2), ('mage_forearm_right', 0)]
    limits = {('right_arm', 0): (-175, reach_back), ('right_arm', 2): (-20, 60), ('mage_forearm_right', 0): (-130, 18)}
    return c.solve(goal, dofs, times, limits)


class Robed(Clip):
    """A clip that leaves from the idle's first pose and returns to it. A key value of None
    stands for that bone's idle value; bones the clip leaves alone hold their idle values."""

    def __init__(self, clips, name, length, loop=False):
        super().__init__(clips, name, length, loop)
        self.idle = idle_pose(clips)

    def rest(self, bone, channel='rotation'):
        return np.array(self.idle.get((bone, channel), (1, 1, 1) if channel == 'scale' else (0, 0, 0)), dtype=float)

    def _fill(self, bone, channel, points):
        return [(p[0], self.rest(bone, channel) if p[1] is None else p[1], *p[2:]) for p in points]

    def rot(self, bone, points, lag=0.):
        return super().rot(bone, self._fill(bone, 'rotation', points), lag)

    def pos(self, bone, points, lag=0.):
        return super().pos(bone, self._fill(bone, 'position', points), lag)

    def scale(self, bone, points, lag=0.):
        return super().scale(bone, self._fill(bone, 'scale', points), lag)

    def hold_idle(self):
        """Every bone the idle moves and this clip has not keyed holds its idle value."""
        for (bone_, channel), v in self.idle.items():
            if (bone_, channel) not in self.fns and (bone_, channel) not in self.solved:
                self.add(bone_, channel, lambda t, v=np.array(v, dtype=float): v.copy())
        return self


def cloth(c, body_gain=1., tail_gain=1., period=None, hz=1.6, damping=.38, settle=None):
    """Overlap for the robe: the tail, skirt panels and hood flaps trail the body that carries
    them, driven by how the hips travel and the torso turns (a damped spring per panel). With
    `settle` the swing fades out over the clip's last that-many seconds, so it ends at rest."""
    times = c.ticks()
    robe = np.array([c.value('robe', 'position', t) for t in times])
    body = np.array([c.value('body', 'rotation', t) for t in times])
    hood = np.array([c.value('hood', 'rotation', t) for t in times])
    root = np.array([c.value('robe', 'rotation', t) for t in times])
    # Forward travel (-Z) and a forward pitch both fling the tail back; a turn swings it round.
    tail_drive = np.column_stack([-robe[:, 2] * 3 + body[:, 0] * .8 + root[:, 0], body[:, 1] + root[:, 1], robe[:, 0] * 2.5 - root[:, 2]])
    tail = follow(times, tail_drive, hz * .8, damping, period) * tail_gain
    skirt_drive = np.column_stack([-robe[:, 2] * 4 + robe[:, 1] * 1.5 + root[:, 0], root[:, 1] + body[:, 1] * .4, robe[:, 0] * 3 - root[:, 2]])
    skirt = follow(times, skirt_drive, hz, damping, period) * body_gain
    flap = follow(times, hood[:, 1:2], hz * 1.6, .32, period)
    crown = follow(times, hood[:, 0:1], hz * 1.4, .4, period)
    fade = (lambda t: 1 - shaped(t, c.length - settle, c.length)) if settle else (lambda t: 1.)
    table = lambda rows, f: (lambda t: f(rows[min(len(rows) - 1, max(0, round(t * 20)))]) * fade(t))
    c.add('robe_tail', 'rotation', table(tail, lambda r: np.array([-r[0] * .9, -r[1] * .5, r[2] * .7])))
    sink = lambda t: max(0., c.value('robe', 'position', 0)[1] - c.value('robe', 'position', t)[1])
    c.add('robe_tail', 'rotation', lambda t: np.array([sink(t) * 2.2 * fade(t), 0, 0]))  # A crouch spreads the tail.
    for side, sign in (('right', -1), ('left', 1)):
        c.add('skirt_' + side, 'rotation', table(skirt, lambda r: np.array([-r[0] * .8, -r[1] * .4, r[2] * .6])))
        c.add('skirt_hem_' + side, 'rotation', table(skirt, lambda r: np.array([-r[0] * .5, 0, r[2] * .4])), lag=.08)
        c.add('hood_' + side, 'rotation', table(flap, lambda r, s=sign: np.array([0, -r[0] * .6, s * abs(r[0]) * .25])))
    c.add('hood_crown', 'rotation', table(crown, lambda r: np.array([-r[0] * .5, 0, 0])))


def knees_push_skirts(c):
    """Bent knees push the skirt panels forward; the hems keep hanging."""
    def skirt(side):
        return lambda t: np.array([min(0., c.value('mage_leg_' + side, 'rotation', t)[0]) * .55, 0, 0])

    def hem(side):
        return lambda t: np.array([-min(0., c.value('mage_leg_' + side, 'rotation', t)[0]) * .35, 0, 0])
    for side in ('right', 'left'):
        c.add('skirt_' + side, 'rotation', skirt(side))
        c.add('skirt_hem_' + side, 'rotation', hem(side))


def staff_tilt(c, points):
    """Drive the staff by its world tilt from vertical (forward positive): the staff channel
    takes whatever the body, arm and forearm leave over, so the skull follows the authored arc
    whatever the arm does. None stands for the idle's tilt."""
    chain = lambda t: c.value('body', 'rotation', t)[0] + c.value('right_arm', 'rotation', t)[0] + c.value('mage_forearm_right', 'rotation', t)[0]
    rest = c.rest('body')[0] + c.rest('right_arm')[0] + c.rest('mage_forearm_right')[0] + c.rest('staff')[0]
    tilt = curve([(p[0], rest if p[1] is None else p[1], *p[2:]) for p in points], c.length if c.loop else None)
    c.add('staff', 'rotation', lambda t: np.array([tilt(t)[0] - chain(t), 0, 0]))
    for k in tilt.times:
        c.extra.add(round(k, 4))


def flame(c, base, period=None, seed=3):
    """The staff's soul flame: a base size curve with a ragged flicker on top."""
    size = curve(base, period)
    loop = period or c.length

    def f(t):
        s = size(t)
        flick = noise(t, loop, seed, (7, 11, 13, 17)) * .12
        return np.array([s[0] * (1 - flick * .5), s[1] * (1 + flick), s[2] * (1 - flick * .5)])
    c.add('staff_flame', 'scale', f)


# ---------------- Idle: the pose every clip leaves from and returns to ----------------

IDLE_LENGTH = 4.


def idle(clips):
    """A watchful caster, never quite still: weight on the staff leg, two slow breaths, one long
    look over his right shoulder and back, the free hand's fingers rolling once."""
    c = Clip(clips, 'idle', IDLE_LENGTH, loop=True)
    L = IDLE_LENGTH
    # The hips drift onto the staff leg and back, never exactly the same way twice in a loop.
    c.add('robe', 'position', lambda t: np.array([-.45 - .25 * noise(t, L, 11, (1, 2)), -.35 - .2 * breath(t, L / 2), .1 * noise(t, L, 12, (1,))]))
    for side in ('right', 'left'):  # Soft knees, so the solve below keeps them bending forward.
        c.rot('mage_leg_' + side, [(0, (-6, 0, 0)), (L, (-6, 0, 0))])
        c.rot('mage_shin_' + side, [(0, (11, 0, 0)), (L, (11, 0, 0))])
    c.add('body', 'rotation', lambda t: np.array([2 + 1.6 * breath(t, L / 2, .06), .8 * noise(t, L, 13, (1, 2)), 1.2]))
    look = curve([(0, (-3, 0, 0)), (1.25, (-3.5, -2, .5), 'slow'), (2.05, (1, 15, -4), 'slow'), (2.75, (0, 17, -5), 'slow'),
                  (3.55, (-4, -2, 1), 'slow'), (4, (-3, 0, 0), 'slow')], L)
    c.add('hood', 'rotation', look)
    c.add('hood', 'rotation', lambda t: np.array([-1.4 * breath(t, L / 2, .02), 0, 0]))  # The head stays level as the chest lifts.
    # The shoulders join the look a little late; the body never turns as far as the head.
    c.add('body', 'rotation', lambda t: np.array([0, .3 * look(t - .3)[1], 0]))
    # The free hand rides near the belt, fingers loosely curled; they roll shut and open once.
    c.rot('left_arm', [(0, (-14, 0, -3)), (1.9, (-16, 2, -4), 'slow'), (2.8, (-13, 0, -3), 'slow'), (4, (-14, 0, -3), 'slow')])
    c.rot('mage_forearm_left', [(0, (-32, 0, 0)), (2.0, (-38, 0, 0), 'slow'), (3.0, (-31, 0, 0), 'slow'), (4, (-32, 0, 0), 'slow')])
    c.rot('mage_hand_left', [(0, (14, 0, -4)), (2.1, (6, 0, -6), 'slow'), (3.1, (16, 0, -3), 'slow'), (4, (14, 0, -4), 'slow')])
    for k in range(3):
        c.rot(f'mage_finger_left_{k}', [(0, (28, 0, (k - 1) * 5)), (2.0, (30, 0, (k - 1) * 5)), (2.35, (78, 0, (k - 1) * 2), 'out'),
                                        (2.75, (70, 0, (k - 1) * 2)), (3.3, (24, 0, (k - 1) * 7), 'ease'), (4, (28, 0, (k - 1) * 5))],
              lag=.09 * k)
        c.rot(f'mage_finger_right_{k}', [(0, (55, 0, 0)), (4, (55, 0, 0))])  # Wrapped round the staff.
    c.rot('right_arm', [(0, (-4, 0, 1)), (4, (-4, 0, 1))])
    c.rot('mage_forearm_right', [(0, (-6, 0, 0)), (4, (-6, 0, 0))])
    flame(c, [(0, 1), (1, (.9, 1.15, .9)), (2.2, 1.05), (3, (.92, 1.12, .92)), (4, 1)], L)
    cloth(c, period=L)
    # Cloth also drifts on its own, as if a cold draught moves through the crypt.
    c.add('robe_tail', 'rotation', lambda t: np.array([3 + 2 * noise(t, L, 21), 1.5 * noise(t, L, 22), 2 * noise(t, L, 23)]))
    for side, seed in (('right', 31), ('left', 37)):
        c.add('skirt_' + side, 'rotation', lambda t, s=seed: np.array([1.2 * noise(t, L, s), 0, 1.2 * noise(t, L, s + 1)]))
        c.add('skirt_hem_' + side, 'rotation', lambda t, s=seed: np.array([1.5 * noise(t, L, s + 2), 0, 1.4 * noise(t, L, s + 3)]))
    plant_feet(c)
    plant_staff(c)
    return c.bake()


def idle_pose(clips):
    """Every channel's value on the idle's first frame, so other clips can start and end there."""
    clip = clips['idle']
    pose = {}
    for bone_, channels in clip['bones'].items():
        for channel, frames in channels.items():
            first = min(frames, key=float)
            pose[(bone_, channel)] = tuple(frames[first])
    return pose


# ---------------- Duel spells ----------------

def cast_bolt(clips):
    """Soul Bolt, 2 s. The server looses a skull at 0.6, 1.0 and 1.4 s (BOLT impact 12, every 8
    ticks) and one more at 1.8 s for a noticed Astral Double; he turns with his target all the
    while. Telegraph: he sinks, hoists the staff up and back over his shoulder while his free hand
    takes aim, and hangs there a beat. Each skull leaves on a whipped thrust that points the
    skull of the staff at the target; the third is the heaviest, his weight lunging onto the
    front foot. He follows through low, then straightens slowly."""
    c = Robed(clips, 'cast_bolt', 2)
    I = None
    # Body: sink and wind back, unwind into each thrust, the last one deepest.
    c.rot('body', [(0, I), (.16, (-1, -4, 1), 'ease'), (.4, (-9, -22, 3), 'out'), (.48, (-10, -24, 3), 'ease'),
                   (.6, (13, 9, -1), 'whip'), (.72, (9, 6, 0), 'out'), (.88, (2, -9, 2), 'ease'),
                   (1.0, (14, 8, -1), 'whip'), (1.12, (10, 5, 0), 'out'), (1.28, (-6, -18, 3), 'ease'),
                   (1.4, (21, 12, -2), 'whip'), (1.6, (25, 13, -2), 'out'), (1.72, (23, 11, -1), 'ease'), (2, I, 'slow')])
    # Hips: settle onto the back foot to hoist, rock forward at each thrust, lunge at the last.
    c.pos('robe', [(0, I), (.16, (-.5, -.9, .3), 'ease'), (.46, (-.3, -.6, 1.1), 'ease'), (.6, (-.6, -1.0, -.7), 'whip'),
                   (.9, (-.5, -.8, .4), 'ease'), (1.0, (-.6, -1.1, -.8), 'whip'), (1.28, (-.4, -.8, .8), 'ease'),
                   (1.4, (-.7, -1.9, -2.2), 'whip'), (1.65, (-.7, -2.1, -2.5), 'out'), (2, I, 'slow')])
    # The face stays on the target while the shoulders wind and unwind beneath it.
    c.rot('hood', [(0, I), (.16, (2, 3, 0)), (.4, (8, 19, -3), 'out'), (.48, (9, 20, -3)), (.6, (-10, -8, 1), 'whip'),
                   (.88, (-3, 8, -1)), (1.0, (-11, -7, 1), 'whip'), (1.28, (2, 16, -2)), (1.4, (-16, -10, 2), 'whip'),
                   (1.65, (-20, -11, 2), 'out'), (2, I, 'slow')], lag=.03)
    # Staff arm: hoisted up and back, then whipped down and forward; the staff itself leads the
    # head through each arc (its tilt is body + arm + forearm + staff, forward positive).
    c.rot('right_arm', [(0, I), (.16, (-28, 0, 6)), (.4, (-158, -6, 24), 'out'), (.48, (-162, -6, 25)),
                        (.6, (-78, 8, 8), 'whip'), (.72, (-84, 6, 9), 'out'), (.88, (-118, 0, 14)),
                        (1.0, (-80, 8, 8), 'whip'), (1.12, (-86, 6, 9), 'out'), (1.28, (-150, -4, 22)),
                        (1.4, (-72, 10, 6), 'whip'), (1.65, (-64, 10, 5), 'out'), (1.72, (-66, 9, 5)), (2, I, 'slow')], lag=-.015)
    c.rot('mage_forearm_right', [(0, I), (.16, (-22, 0, 0)), (.4, (-40, 0, 0), 'out'), (.48, (-42, 0, 0)),
                                 (.6, (-6, 0, 0), 'whip'), (.72, (-14, 0, 0), 'out'), (.88, (-30, 0, 0)),
                                 (1.0, (-6, 0, 0), 'whip'), (1.12, (-14, 0, 0), 'out'), (1.28, (-38, 0, 0)),
                                 (1.4, (-2, 0, 0), 'whip'), (1.65, (0, 0, 0), 'out'), (2, I, 'slow')])
    staff_tilt(c, [(0, None), (.16, 4), (.4, -36, 'out'), (.48, -40), (.6, 66, 'whip'), (.72, 54, 'out'), (.88, 4),
                   (1.0, 66, 'whip'), (1.12, 54, 'out'), (1.28, -22), (1.4, 72, 'whip'), (1.65, 102, 'out'), (1.72, 98),
                   (2, None, 'slow')])
    for k in range(3):
        c.rot(f'mage_finger_right_{k}', [(0, I), (.4, (75, 0, 0)), (1.65, (75, 0, 0)), (2, I)])  # A hard grip.
    # Aiming hand: rises to point at the target, fingers splayed; each skull flicks it open.
    c.rot('left_arm', [(0, I), (.2, (-40, 6, -10)), (.46, (-78, -14, -8), 'out'), (.6, (-74, -16, -10), 'ease'),
                       (1.31, (-70, -10, -12), 'ease'), (1.4, (-84, -18, -8), 'whip'), (1.7, (-64, -12, -10), 'ease'),
                       (2, I, 'slow')], lag=.03)
    c.rot('mage_forearm_left', [(0, I), (.46, (-12, 0, 0), 'out'), (.6, (-6, 0, 0)), (1.31, (-24, 0, 0)), (1.4, (-2, 0, 0), 'whip'),
                                (1.75, (-14, 0, 0)), (2, I, 'slow')], lag=.05)
    c.rot('mage_hand_left', [(0, I), (.46, (-24, 0, -6), 'out'), (.6, (-34, 0, -6), 'whip'), (.75, (-22, 0, -6)),
                             (1.0, (-34, 0, -6), 'whip'), (1.15, (-22, 0, -6)), (1.4, (-40, 0, -6), 'whip'),
                             (1.75, (-18, 0, -4)), (2, I, 'slow')], lag=.06)
    for k in range(3):
        spread = (k - 1) * 18
        c.rot(f'mage_finger_left_{k}', [(0, I), (.46, (-10, 0, spread), 'out'), (.56, (12, 0, spread * .6)), (.6, (-30, 0, spread * 1.3), 'snap'),
                                        (.9, (10, 0, spread * .7)), (1.0, (-30, 0, spread * 1.3), 'snap'), (1.3, (16, 0, spread * .5)),
                                        (1.4, (-38, 0, spread * 1.4), 'snap'), (1.75, (0, 0, spread * .8)), (2, I, 'slow')], lag=.07 + .025 * k)
    # The flame swells through the hoist, flares as each skull leaves and gutters after.
    flame(c, [(0, 1), (.44, 1.7), (.52, (1.8, 2.3, 1.8)), (.6, 2.8, 'snap'), (.74, 1.5), (.9, 1.8), (1.0, 2.6, 'snap'),
              (1.14, 1.5), (1.31, 2.0), (1.4, 3.1, 'snap'), (1.6, 1.4), (2, 1)])
    c.hold_idle()
    cloth(c, settle=.3)
    plant_feet(c)
    return c.bake()


def cast_hands(clips):
    """Grasping Hands, 2.4 s. The rings mark the players at once and close at 1.6 s (HANDS impact
    32), so the whole clip reads as a slow haul: he drives the staff into the floor, sweeps his
    free hand low over the ground, palm down, and lifts it against a great weight, the fingers
    hooking into claws and trembling, then wrenches the fist shut and down as the rings close.
    He holds the clench, then lets go and lifts the staff."""
    c = Robed(clips, 'cast_hands', 2.4)
    I = None
    c.rot('body', [(0, I), (.1, (-3, -4, 1)), (.24, (9, 0, 0), 'whip'), (.36, (7, 0, 0), 'out'), (.75, (27, 14, -6), 'ease'),
                   (1.42, (5, -3, 1), 'slow'), (1.6, (18, 9, -2), 'whip'), (1.85, (16, 8, -2), 'out'), (2.4, I, 'slow')])
    c.pos('robe', [(0, I), (.1, (-.5, .2, .2)), (.24, (-.6, -1.3, -.2), 'whip'), (.4, (-.6, -1.0, -.2), 'out'),
                   (.75, (-.2, -2.2, -.8)), (1.42, (-.4, -1.3, .2), 'slow'), (1.6, (-.4, -1.8, -.7), 'whip'),
                   (1.85, (-.4, -1.6, -.6), 'out'), (2.4, I, 'slow')])
    c.rot('hood', [(0, I), (.24, (6, 0, 0), 'whip'), (.75, (8, 2, 2)), (1.2, (-6, 0, 0), 'slow'), (1.42, (-10, 0, 0)),
                   (1.6, (8, 0, 0), 'whip'), (1.85, (6, 0, 0)), (2.4, I, 'slow')], lag=.04)
    # Staff: a short lift, then driven butt first into the floor beside his right foot.
    c.rot('right_arm', [(0, I), (.1, (-26, 0, 6)), (.24, (-18, 0, 8), 'whip'), (1.9, (-18, 0, 8)), (2.4, I, 'slow')])
    c.rot('mage_forearm_right', [(0, I), (.1, (-24, 0, 0)), (.24, (-10, 0, 0), 'whip'), (1.9, (-10, 0, 0)), (2.4, I, 'slow')])
    staff_tilt(c, [(0, None), (.1, -4), (.24, 8, 'whip'), (1.9, 8), (2.4, None, 'slow')])
    # Free hand: out low over the ground, palm down and spread; lifted slowly against the
    # weight; snapped shut and wrenched down at the close.
    c.rot('left_arm', [(0, I), (.3, (-16, 4, -20)), (.75, (-16, -10, -32), 'out'), (1.42, (-122, -6, -20), 'slow'),
                       (1.6, (-58, -6, -18), 'whip'), (1.85, (-60, -6, -18), 'out'), (2.4, I, 'slow')], lag=.03)
    c.rot('mage_forearm_left', [(0, I), (.75, (-14, 0, 0), 'out'), (1.42, (-36, 0, 0), 'slow'), (1.6, (-8, 0, 0), 'whip'),
                                (1.85, (-12, 0, 0)), (2.4, I, 'slow')], lag=.05)
    c.rot('mage_hand_left', [(0, I), (.75, (-62, 0, -10), 'out'), (1.42, (-20, 0, -6), 'slow'), (1.6, (-18, 0, -4), 'whip'),
                             (1.85, (-20, 0, -4)), (2.4, I, 'slow')], lag=.07)
    for k in range(3):
        spread = (k - 1) * 20
        c.rot(f'mage_finger_left_{k}', [(0, I), (.4, (-25, 0, spread)), (.75, (-30, 0, spread * 1.2), 'out'),
                                        (1.42, (58, 0, spread * .5), 'slow'), (1.54, (66, 0, spread * .3)), (1.6, (100, 0, 0), 'snap'),
                                        (1.9, (96, 0, 0)), (2.25, (20, 0, spread * .4), 'ease'), (2.4, I)], lag=.03 * k)
        c.rot(f'mage_finger_right_{k}', [(0, I), (.2, (80, 0, 0)), (1.9, (80, 0, 0)), (2.4, I)])
    # The strain: a tremble that grows as the hand climbs and peaks just before the clench.
    def tremble(t):
        size = shaped(t, .8, 1.5) * (1 - shaped(t, 1.6, 1.68)) + .5 * (shaped(t, 1.6, 1.62) - shaped(t, 1.75, 1.95))
        return np.array([math.sin(t * math.tau * 9.5) * 2.2, math.sin(t * math.tau * 7) * 1.2, math.sin(t * math.tau * 11.5) * 1.4]) * size
    c.add('left_arm', 'rotation', tremble)
    c.add('mage_hand_left', 'rotation', lambda t: tremble(t + .03) * 1.6)
    c.add('body', 'rotation', lambda t: tremble(t + .05) * .25)
    flame(c, [(0, 1), (.24, 2.2, 'snap'), (.5, 1.3), (1.42, 1.9), (1.6, 2.7, 'snap'), (1.85, 1.6), (2.4, 1)])
    c.hold_idle()
    cloth(c, settle=.35)
    plant_feet(c)
    # Held where it struck, at the height his reach allows standing (as in the idle).
    butt = c.point('staff', BUTT, .24) * [1, 0, 1] + [0, 1.6, 0]
    plant_staff(c, butt, weight=lambda t: shaped(t, .14, .24) * (1 - shaped(t, 1.9, 2.05)))
    return c.bake()


def blink(clips):
    """Blink, 0.8 s. The server teleports him at 0.35 s (7 ticks after the clip starts, for the
    blink, the shift and the ambush, which cuts to ambush_burst there). He gathers himself in,
    then whirls a quarter turn as he is drawn up into a thread of shadow; on arrival he spins out
    of the thread with the robe flung wide, lands low and rises."""
    c = Robed(clips, 'blink', .8)
    I = None
    gone = (.05, 1.3, .05)
    c.scale('robe', [(0, I), (.12, (1.04, .95, 1.04)), (.24, (.6, 1.1, .6), 'in'), (.32, gone, 'in'), (.35, gone),
                     (.36, gone), (.5, (1.1, .96, 1.1), 'out'), (.6, (.97, 1.02, .97)), (.8, I)])
    c.rot('robe', [(0, I), (.12, (0, -8, 0)), (.33, (0, 95, 0), 'in'), (.3499, (0, 100, 0), 'linear'),
                   (.35, (0, -80, 0), 'linear'), (.52, (0, 8, 0), 'out'), (.66, (0, -2, 0)), (.8, I)])
    c.pos('robe', [(0, I), (.12, (-.4, -1.4, .2), 'ease'), (.3, (-.4, 1.2, 0), 'in'), (.35, (-.4, 1.4, 0)),
                   (.36, (-.4, .6, 0)), (.48, (-.4, -1.8, 0), 'in'), (.6, (-.4, -1.0, 0), 'out'), (.8, I, 'slow')])
    c.rot('body', [(0, I), (.12, (12, 0, 0)), (.3, (-6, 0, 0)), (.35, (-8, 0, 0)), (.48, (14, 0, 0), 'in'),
                   (.6, (8, 0, 0), 'out'), (.8, I, 'slow')])
    c.rot('hood', [(0, I), (.12, (10, 0, 0)), (.3, (-10, 0, 0)), (.36, (-10, 0, 0)), (.5, (8, 0, 0)), (.8, I, 'slow')], lag=.03)
    # Arms: wrapped in for the vanish, flung out on arrival.
    c.rot('left_arm', [(0, I), (.12, (-38, 34, 6)), (.3, (-30, 40, 10)), (.36, (-30, 40, 10)), (.5, (-30, -10, -48), 'out'),
                       (.66, (-20, 0, -14)), (.8, I, 'slow')], lag=.02)
    c.rot('mage_forearm_left', [(0, I), (.12, (-70, 0, 0)), (.36, (-70, 0, 0)), (.5, (-10, 0, 0), 'out'), (.8, I, 'slow')], lag=.04)
    c.rot('right_arm', [(0, I), (.12, (-16, -8, -4)), (.36, (-16, -8, -4)), (.5, (-30, 0, 28), 'out'), (.66, (-12, 0, 6)), (.8, I, 'slow')], lag=.02)
    c.rot('mage_forearm_right', [(0, I), (.12, (-30, 0, 0)), (.36, (-30, 0, 0)), (.5, (-8, 0, 0), 'out'), (.8, I, 'slow')], lag=.04)
    staff_tilt(c, [(0, None), (.12, -2), (.36, -2), (.5, 14, 'out'), (.66, -4), (.8, None, 'slow')])
    for k in range(3):
        spread = (k - 1) * 22
        c.rot(f'mage_finger_left_{k}', [(0, I), (.12, (70, 0, 0)), (.36, (70, 0, 0)), (.5, (-30, 0, spread), 'snap'),
                                        (.8, I, 'slow')], lag=.02 * k)
    # The robe hem flares as he spins out of the thread.
    for side, sign in (('right', -1), ('left', 1)):
        c.rot('skirt_' + side, [(0, I), (.3, (-6, 0, -sign * 4)), (.36, (-6, 0, -sign * 4)), (.48, (-14, 0, -sign * 26), 'out'),
                                (.62, (-4, 0, -sign * 8)), (.8, I, 'slow')])
        c.rot('skirt_hem_' + side, [(0, I), (.36, (0, 0, 0)), (.5, (-10, 0, -sign * 18), 'out'), (.66, (4, 0, sign * 4)), (.8, I, 'slow')])
    c.rot('robe_tail', [(0, I), (.3, (2, 0, 0)), (.36, (2, 0, 0)), (.5, (38, 0, -12), 'out'), (.66, (10, 0, 4)), (.8, I, 'slow')])
    flame(c, [(0, 1), (.12, 1.4), (.32, .4, 'in'), (.36, .4), (.48, 2.2, 'snap'), (.62, 1.2), (.8, 1)])
    c.hold_idle()
    # Soles stay down until the thread lifts him (he is stretched off the floor while thin),
    # and again from the landing.
    plant_feet(c, times=[t for t in c.ticks() if t <= .18 or t >= .44])
    return c.bake()


def ambush_burst(clips):
    """Ambush burst, 1.7 s, from the moment he appears behind his victim. The soul ring at his
    feet marks it and the burst lands at 0.7 s (AMBUSH impact 26, 14 ticks after he appears); he
    then stands still for a second. He spins out of the blink's thread low, rises tall with the
    staff hoisted overhead in both hands, hangs at the top from 0.52 s, and drives the butt into the floor
    in front of him with his whole weight; he holds the crouch over the planted staff while the
    hem settles, then hauls himself up."""
    c = Robed(clips, 'ambush_burst', 1.7)
    I = None
    gone = (.05, 1.3, .05)
    c.scale('robe', [(0, gone), (.14, (1.08, .96, 1.08), 'out'), (.26, I)])
    c.rot('robe', [(0, (0, -80, 0)), (.16, (0, 6, 0), 'out'), (.28, I)])
    c.pos('robe', [(0, (-.4, .6, 0)), (.16, (-.5, -2.4, .3), 'in'), (.3, (-.5, -1.8, .3), 'out'), (.52, (-.4, -.1, .8), 'slow'),
                   (.6, (-.4, 0, .9)), (.7, (-.6, -4.2, -1.4), 'hit'), (.82, (-.6, -3.8, -1.3), 'out'),
                   (1.1, (-.6, -3.9, -1.3)), (1.7, I, 'slow')])
    c.rot('body', [(0, (6, 0, 0)), (.16, (20, 0, 0), 'out'), (.3, (14, 0, 0)), (.52, (-13, 4, 0), 'slow'), (.6, (-14, 4, 0)),
                   (.7, (33, -4, 0), 'hit'), (.82, (29, -3, 0), 'out'), (1.1, (30, -3, 0)), (1.7, I, 'slow')])
    c.rot('hood', [(0, (6, 0, 0)), (.16, (-6, 0, 0)), (.52, (-18, -4, 0), 'slow'), (.6, (-20, -4, 0)), (.7, (-2, 4, 0), 'hit'),
                   (.85, (4, 2, 0), 'out'), (1.1, (2, 2, 0)), (1.7, I, 'slow')], lag=.03)
    # Staff overhead in both hands: hoisted up, skull high, then driven butt first ahead of him.
    c.rot('right_arm', [(0, (-20, 0, 10)), (.16, (-40, 0, 18), 'out'), (.52, (-168, 6, 4), 'slow'), (.6, (-170, 6, 4)),
                        (.7, (-62, 4, 4), 'hit'), (1.1, (-60, 4, 4)), (1.7, I, 'slow')], lag=-.01)
    c.rot('mage_forearm_right', [(0, (-30, 0, 0)), (.16, (-30, 0, 0)), (.52, (-16, 0, 0), 'slow'), (.6, (-16, 0, 0)),
                                 (.7, (-22, 0, 0), 'hit'), (1.1, (-22, 0, 0)), (1.7, I, 'slow')])
    staff_tilt(c, [(0, -4), (.16, 6), (.52, -18, 'slow'), (.6, -20), (.7, -4, 'hit'), (1.1, -4), (1.7, None, 'slow')])
    # The free hand grips the shaft above the right hand while he hoists and drives it.
    c.rot('left_arm', [(0, (-20, 20, 0)), (.16, (-40, 20, -10)), (.52, (-160, -10, -6), 'slow'), (.6, (-162, -10, -6)), (.7, (-60, -12, -4), 'hit'),
                       (1.1, (-60, -12, -4)), (1.4, (-30, 0, -10)), (1.7, I, 'slow')])
    c.rot('mage_forearm_left', [(0, (-40, 0, 0)), (.52, (-20, 0, 0)), (.6, (-20, 0, 0)), (.7, (-30, 0, 0)), (1.1, (-30, 0, 0)), (1.7, I, 'slow')])
    for k in range(3):
        c.rot(f'mage_finger_left_{k}', [(0, (40, 0, 0)), (.3, (85, 0, 0)), (1.12, (85, 0, 0)), (1.4, (20, 0, (k - 1) * 8)), (1.7, I, 'slow')])
        c.rot(f'mage_finger_right_{k}', [(0, (70, 0, 0)), (.2, (85, 0, 0)), (1.12, (85, 0, 0)), (1.7, I, 'slow')])
    # The hem is blown out by the burst and settles while he holds the crouch.
    for side, sign in (('right', -1), ('left', 1)):
        c.rot('skirt_' + side, [(0, (-10, 0, -sign * 18)), (.2, (-4, 0, -sign * 6)), (.6, (2, 0, -sign * 2)),
                                (.74, (-22, 0, -sign * 24), 'snap'), (1.0, (-10, 0, -sign * 10)), (1.25, (-12, 0, -sign * 12)), (1.7, I, 'slow')])
        c.rot('skirt_hem_' + side, [(0, (-8, 0, -sign * 14)), (.2, (0, 0, 0)), (.6, (0, 0, 0)), (.76, (-16, 0, -sign * 20), 'snap'),
                                    (1.05, (4, 0, sign * 2)), (1.3, (-2, 0, -sign * 3)), (1.7, I, 'slow')])
    c.rot('robe_tail', [(0, (22, 0, 0)), (.2, (6, 0, 0)), (.6, (2, 0, 0)), (.76, (32, 0, 0), 'snap'), (1.05, (14, 0, 0)),
                        (1.3, (18, 0, 0)), (1.7, I, 'slow')])
    flame(c, [(0, 1.4), (.3, 1.6), (.52, 2.6, 'slow'), (.6, 2.7), (.7, 3.4, 'snap'), (.9, 1.0), (1.2, .7), (1.7, 1)])
    c.hold_idle()
    plant_feet(c, times=[t for t in c.ticks() if t >= .1], spots=STANCE)
    # Two hands on the shaft from the hoist to the rise; the butt bites the floor at 0.7 s.
    slam = c.point('staff', BUTT, .7) * [1, 0, 1] + [0, .3, 0]
    plant_staff(c, slam, weight=lambda t: shaped(t, .67, .7) * (1 - shaped(t, 1.12, 1.4)))
    grip = lambda t: c.point('staff', (-5, 21, 1), t)
    c.solve([('mage_hand_left', (4.8, 11.2, 1), grip, lambda t: shaped(t, .14, .34) * (1 - shaped(t, 1.12, 1.32)))],
            [('left_arm', 0), ('left_arm', 1), ('left_arm', 2), ('mage_forearm_left', 0)],
            limits={('left_arm', 0): (-185, 40), ('left_arm', 1): (-60, 60), ('left_arm', 2): (-80, 20), ('mage_forearm_left', 0): (-130, 20)})
    return c.bake()


def walk(clips):
    """Walk, a 1.5 s loop of two strides, carrying the idle's upper body so the two blend. A
    long, deliberate stalk: the hips drop onto each heel and rise over the planted leg, sway over
    it, and the shoulders counter-turn; the head stays level and fixed ahead; the staff swings
    with the far leg like a carried stick; the robe tail trails in the draught of the walk and
    the skirt panels follow the knees. (In game he covers about 3 blocks a second, more than
    two strides of this length can, so the soles cannot stay planted; see the rework notes.)"""
    c = Robed(clips, 'walk', 1.5, loop=True)
    L = 1.5
    ph = lambda t: math.tau * t / L
    rest = {k: c.rest(*k) for k in [('robe', 'position'), ('body', 'rotation'), ('hood', 'rotation'), ('left_arm', 'rotation'),
                                     ('right_arm', 'rotation'), ('mage_forearm_left', 'rotation'), ('mage_forearm_right', 'rotation')]}
    c.add('robe', 'position', lambda t: rest[('robe', 'position')] + np.array([.45 - .4 * math.sin(ph(t)), -.65 * math.cos(ph(t)) ** 2 + .55, 0]))
    c.add('body', 'rotation', lambda t: rest[('body', 'rotation')] + np.array([3 + .8 * math.cos(2 * ph(t)), 3.5 * math.cos(ph(t)), -1.2 * math.sin(ph(t)) - 1.2]))
    # The head rides level and looks ahead, undoing the shoulders' turn and the bob.
    c.add('hood', 'rotation', lambda t: rest[('hood', 'rotation')] + np.array([-3 - .8 * math.cos(2 * ph(t) - .4), -3.2 * math.cos(ph(t) - .2), 1.1 * math.sin(ph(t))]))
    for side, sign in (('right', 1), ('left', -1)):
        # Right leg forward at 0 s, left at 0.75 s; the knee folds as each leg swings through.
        swing = lambda t, s=sign: max(0., math.sin(ph(t) + (0 if s > 0 else math.pi) - math.pi)) ** 1.5
        c.add('mage_leg_' + side, 'rotation', lambda t, s=sign: np.array([-24 * s * math.cos(ph(t)) - 8 - 10 * swing(t, s), 0, 0]))
        c.add('mage_shin_' + side, 'rotation', lambda t, s=sign: np.array([10 + 34 * swing(t, s), 0, 0]))
        c.add('skirt_' + side, 'rotation', lambda t, s=sign: np.array([-9 * s * math.cos(ph(t) - .35) - 4, 0, -sign * 1.5]))
        c.add('skirt_hem_' + side, 'rotation', lambda t, s=sign: np.array([6 * s * math.cos(ph(t) - .9) + 3 * swing(t - .1, s), 0, 0]))
    # Arms swing against the legs: the free hand forward with the right foot, the staff with the left.
    c.add('left_arm', 'rotation', lambda t: rest[('left_arm', 'rotation')] + np.array([-7 * math.cos(ph(t) - .25), 0, -1]))
    c.add('mage_forearm_left', 'rotation', lambda t: rest[('mage_forearm_left', 'rotation')] + np.array([-4 * math.cos(ph(t) - .5), 0, 0]))
    c.add('right_arm', 'rotation', lambda t: rest[('right_arm', 'rotation')] + np.array([7 * math.cos(ph(t) - .25) - 5, 0, 2]))
    c.add('mage_forearm_right', 'rotation', lambda t: rest[('mage_forearm_right', 'rotation')] + np.array([3 * math.cos(ph(t) - .5) - 6, 0, 0]))
    # The staff hangs back a little behind the arm's swing, like a carried stick.
    c.add('staff', 'rotation', lambda t: np.array([-4 * math.cos(ph(t) - .6) + 6, 0, 0]))
    c.add('robe_tail', 'rotation', lambda t: np.array([14 + 3 * math.cos(2 * ph(t) - 1.2), 4 * math.sin(ph(t) - .6), 3 * math.sin(ph(t) - .9)]))
    c.add('mage_hand_left', 'rotation', lambda t: c.rest('mage_hand_left') + np.array([5 * math.cos(ph(t) - .8), 0, 0]))
    flame(c, [(0, 1), (.4, (.9, 1.15, .9)), (.75, 1), (1.15, (.88, 1.18, .88)), (1.5, 1)], L, seed=5)
    c.hold_idle()
    return c.bake()


# ---------------- Siege: the perch ----------------

PERCH = {('body', 'rotation'): (7, 0, -1), ('hood', 'rotation'): (14, -4, 2), ('left_arm', 'rotation'): (-74, -6, -26),
         ('mage_forearm_left', 'rotation'): (-10, 0, 0), ('mage_hand_left', 'rotation'): (-42, 0, -6),
         ('right_arm', 'rotation'): (-62, 0, 18), ('mage_forearm_right', 'rotation'): (-14, 0, 0), ('robe', 'position'): (-.3, -.5, 0)}
PERCH_TILT = 112  # the staff's skull aimed down at the clearing


def perch_channel(clips):
    """Siege channel, a 3 s loop while the waves fight. He leans out over the edge of his bough,
    looking down at the clearing; the staff hangs out over it, skull down, flame roaring; his free
    hand is spread over his army and its fingers work in a slow ripple like a puppeteer's. The
    robe billows in the soul fire's updraft and his chest rises and falls; his soles stay put."""
    c = Robed(clips, 'perch_channel', 3, loop=True)
    L = 3.
    for (bone_, channel), v in PERCH.items():
        c.add(bone_, channel, lambda t, v=np.array(v, dtype=float): v.copy())
    c.add('body', 'rotation', lambda t: np.array([1.8 * breath(t, L), 1.2 * noise(t, L, 41, (1,)), .8 * noise(t, L, 42, (1,))]))
    c.add('robe', 'position', lambda t: np.array([0, -.25 * breath(t, L), 0]))
    c.add('hood', 'rotation', lambda t: np.array([2 * noise(t, L, 43, (1, 2)), 7 * noise(t, L, 44, (1,)), 2 * noise(t, L, 45, (1,))]))
    c.add('left_arm', 'rotation', lambda t: np.array([3 * noise(t, L, 46, (1, 2)), 2 * noise(t, L, 47, (1,)), 2 * noise(t, L, 48, (1,))]))
    c.add('mage_hand_left', 'rotation', lambda t: np.array([8 * math.sin(math.tau * t / 1.5), 0, 4 * math.sin(math.tau * t / 1.5 + 1)]))
    for k in range(3):
        c.add(f'mage_finger_left_{k}', 'rotation',
              lambda t, k=k: np.array([25 + 35 * math.sin(math.tau * (t / 1.5 - k * .12)) ** 2, 0, (k - 1) * 14]))
        c.rot(f'mage_finger_right_{k}', [(0, (55, 0, 0)), (L, (55, 0, 0))])
    staff_tilt(c, [(0, PERCH_TILT), (L, PERCH_TILT)])
    c.add('staff', 'rotation', lambda t: np.array([3 * noise(t, L, 49, (1, 2)), 0, 0]))
    flame(c, [(0, 1.6), (.75, (1.35, 2, 1.35)), (1.5, 1.7), (2.25, (1.4, 2.1, 1.4)), (3, 1.6)], L, seed=8)
    # The updraft: the hem and tail billow up and out, irregularly.
    c.add('robe_tail', 'rotation', lambda t: np.array([14 + 7 * noise(t, L, 51), 4 * noise(t, L, 52), 5 * noise(t, L, 53)]))
    for side, sign, seed in (('right', -1, 61), ('left', 1, 67)):
        c.add('skirt_' + side, 'rotation', lambda t, s=seed, g=sign: np.array([-5 + 4 * noise(t, L, s), 0, -g * (6 + 4 * noise(t, L, s + 1))]))
        c.add('skirt_hem_' + side, 'rotation', lambda t, s=seed, g=sign: np.array([-4 + 6 * noise(t, L, s + 2), 0, -g * (5 + 5 * noise(t, L, s + 3))]))
    for side in ('right', 'left'):
        c.add('hood_' + side, 'rotation', lambda t, s=side: np.array([0, 4 * noise(t, L, 71 + len(s)), 0]))
    c.hold_idle()
    plant_feet(c, spots=STANCE)
    return c.bake()


def perch_cast(clips):
    """Soul Fire Rain, 0.8 s, chained back into the channel. The fireballs leave the staff the
    moment the clip starts, so the release is the first beat: he jabs the staff down at the
    clearing with a lurch of his whole body, the flame flaring and his free hand flung open; the
    staff kicks back up as the volley goes, the hem flicks, and he settles into the channel pose."""
    c = Robed(clips, 'perch_cast', .8)
    channel = {}
    for bone_, chans in clips['perch_channel']['bones'].items():
        for ch, frames in chans.items():
            channel[(bone_, ch)] = np.array(frames[min(frames, key=float)], dtype=float)
    # Offsets from the channel's first frame, back to nothing by the end.
    hit = lambda keys: curve([(0, (0, 0, 0))] + keys + [(.8, (0, 0, 0), 'slow')])
    for (bone_, ch), v in channel.items():
        if bone_ in ('mage_leg_right', 'mage_leg_left', 'mage_shin_right', 'mage_shin_left'):
            continue
        c.add(bone_, ch, lambda t, v=v: v.copy())
    c.add('body', 'rotation', hit([(.1, (12, 3, 0), 'snap'), (.3, (-3, 0, 0), 'out'), (.5, (1, 0, 0))]))
    c.add('robe', 'position', hit([(.1, (0, -.8, -.6), 'snap'), (.3, (0, .2, .2), 'out')]))
    c.add('hood', 'rotation', hit([(.1, (8, 0, 0), 'snap'), (.32, (-6, 0, 0), 'out'), (.5, (2, 0, 0))]))
    c.add('right_arm', 'rotation', hit([(.1, (-16, 0, -2), 'snap'), (.3, (8, 0, 2), 'out'), (.5, (-2, 0, 0))]))
    c.add('staff', 'rotation', hit([(.1, (22, 0, 0), 'snap'), (.28, (-14, 0, 0), 'out'), (.48, (4, 0, 0))]))
    c.add('left_arm', 'rotation', hit([(.12, (-14, 6, -8), 'snap'), (.4, (3, 0, 2), 'out')]))
    for k in range(3):
        c.add(f'mage_finger_left_{k}', 'rotation', hit([(.1, (-45, 0, (k - 1) * 10), 'snap'), (.45, (10, 0, 0), 'out')]))
    c.add('robe_tail', 'rotation', hit([(.12, (-12, 0, 0), 'snap'), (.35, (16, 0, 0), 'out'), (.55, (-4, 0, 0))]))
    for side, sign in (('right', -1), ('left', 1)):
        c.add('skirt_hem_' + side, 'rotation', hit([(.14, (-14, 0, -sign * 8), 'snap'), (.4, (6, 0, sign * 3), 'out')]))
    c.scale('staff_flame', [(0, tuple(channel[('staff_flame', 'scale')])), (.08, 3.0, 'snap'), (.3, 1.3), (.8, tuple(channel[('staff_flame', 'scale')]), 'slow')])
    c.hold_idle()
    plant_feet(c, spots=STANCE)
    return c.bake()


def crash(clips):
    """Crash, 6 s: the exposed window after the first siege (EXPOSED_TICKS), from the moment he
    hits the floor. He lands hard, his legs buckling onto one knee as he drives the staff in to
    catch himself, head thrown down; he heaves for breath, sagging on the staff, makes one failed
    push to rise at 2.5 s and sinks back, lifts his head to glare at 3.6 s, and only at 5 s hauls
    himself up the staff, steps his front foot back under him and stands."""
    c = Robed(clips, 'crash', 6)
    I = None
    heave = lambda t: math.sin(math.tau * (t - .5) / 1.5) * shaped(t, .5, 1.0) * (1 - shaped(t, 4.8, 5.1))
    c.pos('robe', [(0, (-.3, -2, 0)), (.14, (-.4, -7.4, .4), 'in'), (.5, (-.4, -6.6, .4), 'out'), (2.3, (-.4, -6.7, .4)),
                   (2.7, (-.4, -5.4, .2), 'ease'), (3.2, (-.4, -6.9, .4), 'in'), (5.0, (-.4, -6.6, .4)),
                   (5.65, (-.5, -1.6, .2), 'slow'), (6, I, 'slow')])
    c.add('robe', 'position', lambda t: np.array([0, .35 * heave(t), 0]))
    c.rot('body', [(0, (18, 0, 0)), (.14, (46, 4, 3), 'in'), (.5, (36, 3, 2), 'out'), (2.3, (37, 3, 2)), (2.7, (28, 2, 1)),
                   (3.2, (39, 4, 3), 'in'), (5.0, (36, 3, 2)), (5.65, (8, 0, 1), 'slow'), (6, I, 'slow')])
    c.add('body', 'rotation', lambda t: np.array([-4 * heave(t), 0, 0]))
    c.rot('hood', [(0, (0, 0, 0)), (.14, (30, 0, -4), 'in'), (.5, (22, -6, -4), 'out'), (3.5, (24, -4, -3)), (3.9, (-6, 8, 2), 'slow'),
                   (4.5, (-4, 9, 2)), (4.9, (20, 2, -2), 'slow'), (5.65, (4, 0, 0), 'slow'), (6, I, 'slow')], lag=.04)
    c.add('hood', 'rotation', lambda t: np.array([3 * heave(t - .2), 0, 0]))
    # The staff is driven in beside him at the landing and carries his weight until he stands.
    c.rot('right_arm', [(0, (-60, 0, 14)), (.14, (-58, 0, 12)), (5.65, (-58, 0, 12)), (6, I, 'slow')])
    c.rot('mage_forearm_right', [(0, (-14, 0, 0)), (.14, (-20, 0, 0)), (5.65, (-20, 0, 0)), (6, I, 'slow')])
    # Planted ahead of him and leaning back toward his shoulder, a pilgrim's staff.
    staff_tilt(c, [(0, 70), (.14, -16, 'in'), (5.4, -16), (5.8, None, 'slow'), (6, None)])
    for k in range(3):
        c.rot(f'mage_finger_right_{k}', [(0, (55, 0, 0)), (.14, (80, 0, 0)), (5.65, (80, 0, 0)), (6, I)])
    # The free hand braces on his raised knee.
    c.rot('left_arm', [(0, (-74, -6, -26)), (.14, (-20, 6, -12), 'in'), (5.0, (-20, 6, -12)), (5.6, (-24, 0, -8), 'slow'), (6, I, 'slow')])
    c.rot('mage_forearm_left', [(0, (-10, 0, 0)), (.14, (-46, 0, 0)), (5.0, (-46, 0, 0)), (6, I, 'slow')])
    c.rot('mage_hand_left', [(0, (-42, 0, -6)), (.14, (20, 0, 0)), (5.0, (20, 0, 0)), (6, I, 'slow')])
    for k in range(3):
        c.rot(f'mage_finger_left_{k}', [(0, (30, 0, 0)), (.14, (70, 0, 0)), (5.0, (70, 0, 0)), (5.6, (30, 0, (k - 1) * 5)), (6, I)])
    # The flame gutters while he is down and kindles again as he rises.
    flame(c, [(0, 1.5), (.14, .35, 'in'), (1.5, .55), (2.7, .4), (3.9, .7), (4.6, .5), (5.65, 1.2, 'slow'), (6, 1)], seed=9)
    # The hem is flung out by the landing and settles around the kneeling legs.
    for side, sign in (('right', -1), ('left', 1)):
        c.rot('skirt_' + side, [(0, (-6, 0, -sign * 6)), (.14, (-24, 0, -sign * 22), 'snap'), (.6, (-16, 0, -sign * 14), 'out'),
                                (5.0, (-15, 0, -sign * 13)), (5.65, (-2, 0, -sign * 2), 'slow'), (6, I, 'slow')])
        c.rot('skirt_hem_' + side, [(0, (0, 0, 0)), (.16, (-14, 0, -sign * 16), 'snap'), (.7, (6, 0, sign * 4), 'out'),
                                    (5.0, (4, 0, sign * 3)), (6, I, 'slow')])
    c.rot('robe_tail', [(0, (20, 0, 0)), (.16, (44, 0, 0), 'snap'), (.7, (26, 0, 0), 'out'), (5.0, (28, 0, 0)), (6, I, 'slow')])
    for side in ('right', 'left'):
        c.rot('mage_leg_' + side, [(0, (-30, 0, 0)), (6, I)])
        c.rot('mage_shin_' + side, [(0, (40, 0, 0)), (6, I)])
    c.hold_idle()
    # Legs: the right foot lands, then the knee goes down to the floor behind him with the shin
    # laid back on its toe; the left foot plants ahead. At the rise the right foot swings
    # through to stand under him and the left steps back beside it.
    leg_r = [('mage_leg_right', 0), ('mage_leg_right', 2), ('mage_shin_right', 0)]
    limits = {('mage_leg_right', 0): (-120, 70), ('mage_leg_right', 2): (-30, 30), ('mage_shin_right', 0): (0, 150),
              ('mage_leg_left', 0): (-120, 70), ('mage_leg_left', 2): (-30, 30), ('mage_shin_left', 0): (0, 150)}
    ticks = c.ticks()
    c.solve([('mage_shin_right', SOLE['right'], lambda t: np.array([2.3, 0, 1]), 1.)], leg_r,
            times=[t for t in ticks if t <= .02], limits=limits)
    knee, toe = np.array([2.6, 1.2, 3.2]), np.array([2.6, 1.8, 10.5])
    down = lambda t: shaped(t, .02, .14)
    c.solve([('mage_shin_right', (-2, 8, 1), lambda t: knee, down),
             ('mage_shin_right', SOLE['right'], lambda t: toe, down)], leg_r,
            times=[t for t in ticks if .02 < t <= 5.05], limits=limits)
    rise = curve([(5.05, c.point('mage_shin_right', SOLE['right'], 5.05)), (5.3, (2.5, 2.6, 4), 'out'), (5.55, STANCE['right'], 'in'),
                  (6, STANCE['right'])])
    c.solve([('mage_shin_right', SOLE['right'], rise, 1.)], leg_r, times=[t for t in ticks if t > 5.05], limits=limits)
    left_sole = curve([(0, (-2.6, 0, -5)), (5.2, (-2.6, 0, -5)), (5.4, (-2.3, 1.6, -2.8)), (5.6, STANCE['left'], 'in'), (6, STANCE['left'])])
    c.solve([('mage_shin_left', SOLE['left'], left_sole, 1.)], [('mage_leg_left', 0), ('mage_leg_left', 2), ('mage_shin_left', 0)],
            limits=limits)
    butt = np.array([7, .3, -14])
    plant_staff(c, butt, weight=lambda t: shaped(t, .04, .16) * (1 - shaped(t, 5.45, 5.8)), reach_back=15)
    knee_left = lambda t: c.point('mage_shin_left', (2, 8, 1), t) + [0, 1.2, -.6]
    c.solve([('mage_hand_left', (4.8, 11.5, 1), knee_left, lambda t: shaped(t, .06, .16) * (1 - shaped(t, 4.9, 5.2)))],
            [('left_arm', 0), ('left_arm', 1), ('left_arm', 2), ('mage_forearm_left', 0)],
            limits={('left_arm', 0): (-185, 40), ('left_arm', 1): (-60, 60), ('left_arm', 2): (-80, 20), ('mage_forearm_left', 0): (-130, 20)})
    return c.bake()


def drain(clips):
    """Life Drain, 4.2 s, dressed around its fixed arm. The braid's focus is sampled from the
    raised hand (life_drain/build_runtime.py bakes it into NecromancerDrainSocket), so the body,
    the raised arm and the forearm keep their keys exactly and only what hangs off them moves:
    the first pulse is at 1.5 s and one every 0.5 s after (DRAIN impact 30, DRAIN_INTERVAL 10).
    He plants the staff and glares down his arm; the raised hand's fingers spread and tremble
    while the motes gather, and on every pulse they clench as if hauling on the strands, his head
    jerking and the flame flaring with them; after the last he lets go."""
    base = clips['drain']
    c = Robed(clips, 'drain', 4.2)
    pulses = [1.5 + .5 * k for k in range(6)]
    tug = lambda t: sum(envelope(t, p - .04, .08, .05, .28, up='snap') for p in pulses) * (1 - shaped(t, 4.06, 4.2))
    I = None
    c.rot('hood', [(0, I), (.45, (6, -4, -3)), (1.45, (10, -6, -4)), (3.8, (10, -6, -4)), (4.2, I, 'slow')], lag=.03)
    c.add('hood', 'rotation', lambda t: np.array([4 * tug(t), 0, 0]))
    c.rot('mage_hand_left', [(0, I), (.7, (-30, 0, -6)), (1.45, (-34, 0, -6)), (3.8, (-30, 0, -6)), (4.2, I, 'slow')])
    c.add('mage_hand_left', 'rotation', lambda t: np.array([14 * tug(t), 0, 0]))
    tremble = lambda t: math.sin(t * math.tau * 9) * shaped(t, .6, 1.3) * (1 - shaped(t, 1.42, 1.5))
    c.add('mage_hand_left', 'rotation', lambda t: np.array([3 * tremble(t), 0, 2 * tremble(t + .05)]))
    for k in range(3):
        spread = (k - 1) * 20
        c.rot(f'mage_finger_left_{k}', [(0, I), (.7, (-28, 0, spread), 'out'), (1.45, (-32, 0, spread * 1.2)), (3.8, (-24, 0, spread)),
                                        (4.2, I, 'slow')], lag=.03 * k)
        c.add(f'mage_finger_left_{k}', 'rotation', lambda t, k=k: np.array([80 * tug(t - .025 * k), 0, 0]))
        c.rot(f'mage_finger_right_{k}', [(0, I), (.3, (80, 0, 0)), (3.9, (80, 0, 0)), (4.2, I)])
    # The staff is planted beside him for the whole channel.
    c.rot('right_arm', [(0, I), (.3, (-12, 0, 8)), (3.9, (-12, 0, 8)), (4.2, I, 'slow')])
    c.rot('mage_forearm_right', [(0, I), (.3, (-14, 0, 0)), (3.9, (-14, 0, 0)), (4.2, I, 'slow')])
    flame(c, [(0, 1), (.6, 1.4), (1.45, 1.6), (3.9, 1.5), (4.2, 1)])
    c.add('staff_flame', 'scale', lambda t: np.ones(3) * (1 + .5 * tug(t)))
    # The hem stirs toward the victim with each pulse.
    for side, sign in (('right', -1), ('left', 1)):
        c.add('skirt_hem_' + side, 'rotation', lambda t: np.array([-5 * tug(t - .06), 0, 0]))
    c.add('robe_tail', 'rotation', lambda t: np.array([-6 * tug(t - .08), 0, 0]))
    # Everything the socket reads stays exactly as it was; the legs keep the idle's stance.
    fixed = {bone_: chans for bone_, chans in base['bones'].items() if bone_ in ('left_arm', 'mage_forearm_left')}
    c.hold_idle()
    for bone_ in ('body', 'robe'):
        for channel in ('rotation', 'position', 'scale'):
            c.fns.pop((bone_, channel), None)
    plant_feet(c, spots=STANCE)
    clip = c.bake()
    clip['bones'].pop('left_arm', None)
    clip['bones'].pop('mage_forearm_left', None)
    clip['bones'].update(fixed)
    return clip
