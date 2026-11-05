"""The Pyre King's clips, authored as keyposes on a timeline (baked space: +X his right,
-Z his front, +Y up, blocks). rig.Solver turns each sampled pose into GeckoLib channels.

Timing follows the fight plan in docs/ROADMAP.md: an opening blow's tell is 0.8-0.9 s, a
follow-up's 0.45 s; blows themselves are fast (0.12-0.18 s), recoveries readable.
"""
import math
import numpy as np
from rig import SWORD_REST_P, SWORD_REST_D, SWORD_REST_N, baked_point, BY, unit

SHAPES = {
    'ease': lambda f: f*f*(3-2*f),
    'linear': lambda f: f,
    'out': lambda f: 1-(1-f)**2,
    'snap': lambda f: 1-(1-f)**3,
    'in': lambda f: f*f,
    'hit': lambda f: 2*f*f-f**3,
    'settle': lambda f: 1-math.exp(-5*f)*(math.cos(9*f)+5/9*math.sin(9*f)) - (1-math.exp(-5)*(math.cos(9)+5/9*math.sin(9))-1)*f*f,
    'hold': lambda f: 0.,
}


def curve(keys, period=None):
    """keys: (time, value[, shape]) - the shape names how the move arrives at that key."""
    pts = [(float(k[0]), np.array(k[1], float), k[2] if len(k) > 2 else 'ease') for k in keys]

    def at(t):
        if period: t %= period
        if t <= pts[0][0]: return pts[0][1]
        for (a, av, _), (b, bv, shape) in zip(pts, pts[1:]):
            if t <= b:
                f = (t-a)/(b-a) if b > a else 1
                return av+(bv-av)*SHAPES[shape](min(1, max(0, f)))
        return pts[-1][1]
    return at


def dcurve(keys, period=None):
    """A direction curve: values are interpolated then normalised (keep keys under 90 deg apart)."""
    c = curve([(k[0], unit(k[1]))+tuple(k[2:]) for k in keys], period)
    return lambda t: unit(c(t))


ANKLE = {side: baked_point(BY[side+'_foot']['pivot']) for side in ('right', 'left')}
GUARD_P, GUARD_D = SWORD_REST_P, SWORD_REST_D
CAPE = ['mantle_scrap_%02d' % i for i in range(1, 8)]
COAT = ['coat_flap_front_right', 'coat_flap_front_left', 'coat_flap_back_right',
        'coat_flap_back_left', 'coat_flap_side_right', 'coat_flap_side_left']


def cloth(t, swing=0., lift=0., period=None):
    """Cape, coat and tabard: a slow irregular sway plus a reaction to the body's motion.
    swing > 0 throws the cloth back (the body lunged forward); lift raises the hems."""
    out = {}
    for i, name in enumerate(CAPE):
        w = math.sin(t*2.1+i*1.3)*1.6+math.sin(t*3.7+i*.7)*.8
        out[name] = ((-(swing*10+lift*6)-w, 0, math.sin(t*1.7+i)*1.2), (0, 0, 0))
    for i, name in enumerate(COAT):
        back = 'back' in name
        w = math.sin(t*2.6+i*1.1)*1.1
        out[name] = (((-1 if back else 1)*(swing*6+lift*8)+w, 0, 0), (0, 0, 0))
    out['tabard'] = ((swing*9+lift*6+math.sin(t*2.3)*1.5, 0, 0), (0, 0, 0))
    out['mantle_scrap_front_right'] = ((math.sin(t*2.4)*1.5, 0, 0), (0, 0, 0))
    out['mantle_scrap_front_left'] = ((math.sin(t*2.4+1)*1.5, 0, 0), (0, 0, 0))
    return out


def planted(dx=(0, 0, 0), sx=(0, 0, 0)):
    return {'right': ANKLE['right']+np.array(dx), 'left': ANKLE['left']+np.array(sx)}


# ---------------------------------------------------------------- idle
def idle(t):
    """A coiled guard: breathing, the blade's point drifting, the head never leaving you."""
    b = math.sin(t*2*math.pi/3)
    return {'pelvis_pos': (0, -.025*(1+b)/2, 0), 'torso_rot': (1.5*b, .8*math.sin(t*2*math.pi/6), 0),
            'head_rot': (-1.2*b, 0, 0),
            'sword_p': GUARD_P+np.array([.02*math.sin(t*2*math.pi/3+.6), -.025*(1+b)/2, 0]),
            'sword_d': unit(GUARD_D+np.array([.03*math.sin(t*2*math.pi/6), .02*b, 0])),
            'feet': planted(), 'extra': cloth(t)}


# ---------------------------------------------------------------- walk / run
def gait(speed, period, stride, lift, bob, lean):
    def clip(t):
        u = (t % period)/period
        feet = {}
        for side, phase in (('right', 0), ('left', .5)):
            v = (u+phase) % 1
            if v < .5:       # stance: the foot slides back under him at the ground speed
                z = -stride/2+stride*(v/.5); y = 0
            else:            # swing: lifts and reaches forward
                f = (v-.5)/.5; z = stride/2-stride*SHAPES['ease'](f); y = lift*math.sin(math.pi*f)
            base = ANKLE[side]*np.array([.72, 1, 0])
            feet[side] = base+np.array([0, y, z])
        down = bob*(1-abs(math.cos(2*math.pi*u)))  # lowest at each contact
        sway = math.sin(2*math.pi*u)
        return {'pelvis_pos': (.04*sway, -down, 0), 'pelvis_rot': (0, 6*sway, 0),
                'torso_rot': (lean, -8*sway, 0), 'head_rot': (-lean*.6, 6*sway, 0),
                'sword_p': GUARD_P+np.array([.04*sway, -down, -.05*lean/10]),
                'sword_d': GUARD_D, 'feet': feet, 'extra': cloth(t, swing=speed/10)}
    return clip


walk = gait(3, .8, 1.15, .32, .06, 2)
run = gait(4.5, .62, 1.55, .45, .1, 9)


# ---------------------------------------------------------------- Royal Cut
def royal_cut(t):
    """Two cuts across a 150-degree arc, then a 1.2 s pause: the punish window."""
    P = curve([(0, GUARD_P),
               (.5, (.78, 3.25, -.25)), (.9, (.82, 3.32, -.2), 'ease'),            # cock high right, tremble
               (.97, (.3, 2.85, -1.0), 'hit'), (1.05, (-.28, 2.5, -.92), 'linear'),   # the cut
               (1.3, (-.28, 2.48, -.55), 'snap'),                                     # follow-through
               (1.6, (-.42, 2.8, -.3)), (1.75, (-.44, 2.84, -.26)),                   # re-cock left
               (1.82, (-.1, 2.75, -1.05), 'hit'), (1.9, (.55, 2.5, -.75), 'linear'),  # backhand
               (2.2, (.72, 2.25, -.3), 'snap'),
               (2.6, (.48, 2.2, -.42)), (3.4, (.46, 2.18, -.44)),                    # the pause
               (3.8, GUARD_P)])
    D = dcurve([(0, GUARD_D),
                (.5, (.3, .62, .72)), (.9, (.28, .66, .7)),
                (.97, (.25, .2, -1.0), 'hit'), (1.05, (-.95, -.12, -.45), 'linear'),
                (1.3, (-.72, -.35, .6), 'snap'),
                (1.6, (-.55, .3, .78)), (1.75, (-.52, .34, .78)),
                (1.82, (-.1, .1, -1.0), 'hit'), (1.9, (.95, -.05, -.45), 'linear'),
                (2.2, (.7, -.3, .62), 'snap'),
                (2.6, (.55, -.32, -.77)), (3.4, (.55, -.33, -.77)),
                (3.8, GUARD_D)])
    torso = curve([(0, (0, 0, 0)), (.5, (-4, 26, 3)), (.9, (-5, 28, 3)),
                   (1.05, (8, -34, -4), 'hit'), (1.3, (9, -42, -5), 'snap'),
                   (1.75, (5, -46, -3)), (1.9, (10, 30, 4), 'hit'), (2.2, (11, 36, 5), 'snap'),
                   (2.6, (6, 16, 2)), (3.4, (5, 14, 2)), (3.8, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.5, (0, 12, 0)), (.9, (0, 13, 0)), (1.05, (0, -16, 0), 'hit'),
                    (1.75, (0, -20, 0)), (1.9, (0, 14, 0), 'hit'), (2.6, (0, 8, 0)), (3.8, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.5, (.08, .02, .12)), (.9, (.09, .02, .13)),
                   (1.05, (-.05, -.12, -.28), 'hit'), (1.75, (-.08, -.1, -.2)),
                   (1.9, (.06, -.14, -.3), 'hit'), (2.6, (.02, -.08, -.18)), (3.4, (.02, -.07, -.16)),
                   (3.8, (0, 0, 0))])
    step = curve([(0, (0, 0, 0)), (.9, (0, 0, 0)), (1.05, (0, 0, -.32), 'hit'), (3.4, (0, 0, -.32)), (3.8, (0, 0, 0))])
    lift = curve([(0, 0), (.9, 0), (.98, .18, 'out'), (1.05, 0, 'in'), (3.4, 0), (3.6, .12), (3.8, 0)])
    head = curve([(0, (0, 0, 0)), (.5, (-3, -10, 0)), (1.05, (2, 12, 0)), (1.75, (0, 16, 0)),
                  (1.9, (2, -10, 0)), (2.6, (-2, -4, 0)), (3.8, (0, 0, 0))])
    swing = curve([(0, 0), (.9, -.3), (1.05, 1, 'hit'), (1.6, .2), (1.9, 1, 'hit'), (2.6, 0)])
    st = step(t)
    return {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': P(t), 'sword_d': D(t),
            'feet': planted(sx=(st[0], float(lift(t)), st[2])),
            'extra': cloth(t, swing=float(swing(t)))}


# ---------------------------------------------------------------- Cast, wait, Reel
LAND = np.array([.35, .55, -11.5])          # where the thrown sword drives into the floor
LAND_D = unit((.05, -.75, -.66))


def cast_reel(t):
    """Throw (0.8 s tell), the sword sticks, a 1.5 s unarmed wait, the Reel tell (0.7 s),
    the sword rips back along a bowed path, he catches it two-handed and recovers."""
    # Pommel path: in hand -> cocked behind the shoulder -> flight -> stuck -> reeled back.
    flight = curve([(0, GUARD_P), (.55, (.92, 3.55, .55)), (.8, (.96, 3.6, .62), 'ease'),
                    (.9, (.55, 3.35, -1.6), 'hit'),                             # released
                    (1.05, (.45, 2.6, -6.2), 'linear'), (1.22, LAND, 'in'),     # flies and sticks
                    (3.5, LAND, 'hold'),
                    (3.62, (2.1, 1.6, -6.4), 'out'), (3.76, (.25, 2.55, -1.15), 'in'),  # reeled, bowed out right
                    (4.0, (.2, 2.3, -.95), 'snap'), (4.7, GUARD_P)])
    Dc = dcurve([(0, GUARD_D), (.55, (-.08, .12, -1)), (.8, (-.1, .14, -1)),
                 (.9, (0, .05, -1), 'hit'), (1.05, (0, -.3, -1)), (1.22, LAND_D, 'in'),
                 (3.5, LAND_D, 'hold'), (3.62, (.6, .2, .78), 'out'), (3.76, (.35, .55, -.75), 'in'),
                 (4.0, (.45, .55, -.7), 'snap'), (4.7, GUARD_D)])
    hands = 'both' if t < .22 else 'right' if t < .86 else 'none' if t < 3.76 else 'right' if t < 3.86 else 'both'
    # Free hands: the left points at the target, then both haul on the chain.
    left = curve([(0, (-.2, 2.55, -1.05)), (.22, (-.2, 2.55, -1.05)), (.55, (-.55, 3.25, -1.55)),
                  (.9, (-.5, 3.1, -1.6)), (1.4, (-.62, 2.55, -.9)), (2.8, (-.6, 2.5, -.95)),
                  (3.15, (.05, 2.45, -.9)), (3.5, (.0, 2.4, -.75)), (3.7, (.05, 2.5, -.9)),
                  (3.86, (-.05, 2.55, -1.0))])
    right = curve([(.86, (.4, 3.0, -1.55)), (1.3, (.62, 2.45, -.9)), (2.8, (.6, 2.4, -.85)),
                   (3.15, (.32, 2.35, -.8)), (3.5, (.38, 2.3, -.62)), (3.7, (.3, 2.45, -.95))])
    torso = curve([(0, (0, 0, 0)), (.55, (-8, 30, 4)), (.8, (-10, 32, 5)), (.9, (12, -18, -3), 'hit'),
                   (1.3, (8, -10, -2), 'snap'), (2.8, (4, -4, 0)), (3.15, (-6, 6, 0)), (3.5, (-10, 8, 0)),
                   (3.76, (6, -6, 0), 'hit'), (4.0, (4, 0, 0)), (4.7, (0, 0, 0))])
    pelvis_rot = curve([(0, (0, 0, 0)), (.8, (0, 14, 0)), (.9, (0, -10, 0), 'hit'), (2.8, (0, -4, 0)),
                        (3.5, (0, 4, 0)), (4.7, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.8, (.06, .02, .18)), (.9, (-.04, -.1, -.3), 'hit'),
                   (1.3, (-.02, -.06, -.2)), (2.8, (0, -.04, -.12)), (3.5, (0, -.12, .12)),
                   (3.76, (0, -.06, -.12), 'hit'), (4.7, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.8, (-4, -14, 0)), (.9, (2, 8, 0)), (2.8, (2, 4, 0)), (3.5, (-2, -4, 0)),
                  (4.0, (0, 0, 0))])
    swing = curve([(0, 0), (.8, -.4), (.9, 1, 'hit'), (1.6, .1), (3.5, -.5), (3.76, .8, 'hit'), (4.4, 0)])
    step = curve([(0, 0), (.85, 0), (.95, -.35, 'hit'), (3.4, -.35), (3.55, -.1), (4.7, 0)])
    lift = curve([(0, 0), (.86, 0), (.9, .16, 'out'), (.97, 0, 'in')])
    spec = {'pelvis_pos': shift(t), 'pelvis_rot': pelvis_rot(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': flight(t), 'sword_d': Dc(t), 'hands': hands,
            'free_hands': {'left': left(t), 'right': right(t)},
            'feet': planted(sx=(0, float(lift(t)), float(step(t)))),
            'extra': cloth(t, swing=float(swing(t)))}
    if .86 <= t < 3.86: spec['hidden'] = ['hanging_wrist_chain']
    return spec


CLIPS = {
    'idle': (idle, 6.0, True),
    'walk': (walk, .8, True),
    'run': (run, .62, True),
    'royal_cut': (royal_cut, 3.8, False),
    'cast_reel': (cast_reel, 4.7, False),
}
