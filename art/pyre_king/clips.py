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


def cloth(t, swing=0., lift=0., period=None):
    """Cape and tabard: a slow irregular sway plus a reaction to the body's motion.
    swing > 0 throws the cloth back (the body lunged forward): the cape's hem flies out
    behind him and the tabard falls back against his legs; lift raises the hems.
    With a period, every sway frequency snaps to whole cycles so the loop has no seam."""
    def w(rate):
        return rate if not period else 2*math.pi*max(1, round(rate*period/(2*math.pi)))/period
    out = {}
    for i, name in enumerate(CAPE):
        s = math.sin(t*w(2.1)+i*1.3)*1.6+math.sin(t*w(3.7)+i*.7)*.8
        out[name] = ((swing*10+lift*6+s, 0, math.sin(t*w(1.7)+i)*1.2), (0, 0, 0))
    out['tabard'] = ((swing*9-lift*6+math.sin(t*w(2.3))*1.5, 0, 0), (0, 0, 0))
    out['mantle_scrap_front_right'] = ((math.sin(t*w(2.4))*1.5, 0, 0), (0, 0, 0))
    out['mantle_scrap_front_left'] = ((math.sin(t*w(2.4)+1)*1.5, 0, 0), (0, 0, 0))
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
            'feet': planted(), 'extra': cloth(t, period=6.0)}


# ---------------------------------------------------------------- walk / run
# Treadmill loops: he strides on the spot while each planted foot slides back at exactly the
# ground speed, so the game should move him at `speed` blocks/s (GROUND_SPEED). The feet
# track under the hips and the knees drive straight ahead. The heel strikes, the weight drops
# into that leg, then the heel peels and the ball pushes off a nearly straight leg. Above them
# the pelvis drops and turns over every step, the chest counter-turns about its own spine and
# drops into each footfall, the head holds on the target, and the sword rides part of the
# chest's motion a beat late and settles after each footfall.
from rig import fk, REST, REST_ANG, RAD, rx, ry, rz, euler_of, gecko_from, rot_between, Solver


def _corners(c):
    """A cube's eight corners in baked bone-rest space (blocks), placed as the baker does."""
    o, s = c['origin'], c['size']
    P = np.array([[-(o[0]+s[0]*i)/16, (o[1]+s[1]*j)/16, (o[2]+s[2]*k)/16]
                  for i in (0, 1) for j in (0, 1) for k in (0, 1)])
    r = c.get('rotation')
    if r and any(r):
        pv = baked_point(c['pivot']); R = rz(r[2]*RAD)@ry(-r[1]*RAD)@rx(-r[0]*RAD)
        P = (P-pv)@R.T+pv
    return P


def _sole(side):
    """Sole depth below the ankle, and the heel and toe edges along Z from it (blocks)."""
    a = ANKLE[side]; P = np.vstack([_corners(c) for c in BY[side+'_foot']['cubes']])
    return a[1]-P[:, 1].min(), P[:, 2].max()-a[2], P[:, 2].min()-a[2]


def _girth(bone, a, b):
    """How far a limb bone's cubes reach from its axis a->b: the radius of a capsule round it."""
    P = np.vstack([_corners(c) for c in BY[bone]['cubes']]); ab = b-a
    f = np.clip((P-a)@ab/(ab@ab), 0, 1)
    return float(np.linalg.norm(P-(a+f[:, None]*ab), axis=1).max())


SOLE = {side: _sole(side) for side in ('right', 'left')}
HIP = {side: baked_point(BY[side+'_thigh']['pivot']) for side in ('right', 'left')}
KNEE = {side: baked_point(BY[side+'_shin']['pivot']) for side in ('right', 'left')}
LEG = {side: (np.linalg.norm(KNEE[side]-HIP[side]), np.linalg.norm(ANKLE[side]-KNEE[side])) for side in ('right', 'left')}
GIRTH = {side: (_girth(side+'_thigh', HIP[side], KNEE[side])+.02, _girth(side+'_shin', KNEE[side], ANKLE[side])+.02)
         for side in ('right', 'left')}
SOLE_AXIS = {side: (ANKLE[side]+np.array([0, -SOLE[side][0]/2, SOLE[side][1]]),
                    ANKLE[side]+np.array([0, -SOLE[side][0]/2, SOLE[side][2]])) for side in ('right', 'left')}
FOOT_GIRTH = {side: _girth(side+'_foot', *SOLE_AXIS[side])+.02 for side in ('right', 'left')}
# The guard's staggered knees rig his right (sword-side) leg longer than his left (LEG): with
# both feet flat and the hips level, the right knee would bend more in every stance. Rolling
# the pelvis by HITCH degrees lifts the right hip and drops the left until both legs have the
# same spare length; a gait's hitch takes a share of it and the chest rolls it back out.
HITCH = math.degrees(math.asin((sum(LEG['right'])-sum(LEG['left']))/(HIP['right'][0]-HIP['left'][0])))


def _edges(bone):
    """A cloth bone's long cube edges sampled down their length, in rest space. The end at the
    pivot rides the body and cannot swing clear of a leg, so only the hanging part is kept."""
    O = baked_point(BY[bone]['pivot'])
    P = np.vstack([P[[i*4+k, i*4+2+k]].T@np.array([1-f, f]) for P in map(_corners, BY[bone]['cubes'])
                   for i in (0, 1) for k in (0, 1) for f in np.linspace(0, 1, 9)])
    return O, P[np.linalg.norm(P-O, axis=1) > .35]


CLOTH_EDGES = {name: _edges(name) for name in ['tabard']+CAPE}
LEG_CUBES = {side: [[_corners(c) for c in BY[side+'_thigh']['cubes']], [_corners(c) for c in BY[side+'_shin']['cubes']]]
             for side in ('right', 'left')}


_JOLT = {}


def _jolt(f, decay=5., freq=1.2):
    """Damped settle after a footfall at step phase 0; periodic over the step, peak 1."""
    h = lambda x: math.exp(-decay*x)*math.sin(2*math.pi*freq*x)
    g = lambda x: sum(h(x+n) for n in range(4))
    if (decay, freq) not in _JOLT: _JOLT[decay, freq] = max(g(i/400) for i in range(400))
    return g(f % 1)/_JOLT[decay, freq]


def _seg_dist(P, a, b):
    ab = b-a; f = np.clip((P-a)@ab/max(ab@ab, 1e-12), 0, 1)
    return np.linalg.norm(P-(a+f[:, None]*ab), axis=1)


def _box(P, margin=.02):
    """A posed cube's eight corners (ordered as _corners gives them) as a box: centre, unit
    axes (rows) and half sizes grown by margin."""
    E = np.array([P[4]-P[0], P[2]-P[0], P[1]-P[0]]); n = np.linalg.norm(E, axis=1)
    return P.mean(0), E/n[:, None], n/2+margin


def _push(bone, legs, x=0., z=0., sense=1.):
    """How far (deg) a leg pushes a cloth bone: the least extra GeckoLib x, toward `sense`
    (+1 swings the hem back, -1 forward), from x that keeps its edges off the legs: capsules
    (a, b, radius) or boxes (_box). They are in the bone's parent's rest space; z is its
    sideways swing."""
    O, P = CLOTH_EDGES[bone]
    def hit(Q, s):
        if np.ndim(s[2]) == 0: return (_seg_dist(Q, s[0], s[1]) < s[2]).any()
        return (np.abs((Q-s[0])@s[1].T) < s[2]).all(axis=1).any()
    def hits(d):
        Q = O+(P-O)@(rz(z*RAD)@rx(-(x+sense*d)*RAD)).T
        return any(hit(Q, s) for s in legs)
    if not hits(0): return 0.
    lo, hi = 0., 4.
    while hits(hi) and hi < 80: lo, hi = hi, hi+4
    for _ in range(8):
        mid = (lo+hi)/2; lo, hi = (mid, hi) if hits(mid) else (lo, mid)
    return hi


def gait(p):
    """A walk or run cycle from the parameters below (blocks, seconds, degrees)."""
    T, beta = p['period'], p['duty']
    span = p['speed']*beta*T                     # how far a planted foot slides back
    swing_t = (1-beta)*T
    bob = curve(p['bob_keys'], 1)
    load = curve([(0, 0), (p['drop'], 1), (.7, 0), (1, 0)], 1)     # the chest settles into each footfall
    hitch = p['hitch']*HITCH
    pitch_swing = curve([(0, -p['push']), (.3, -p['push']-p['hang']), (.78, p['strike']+4), (1, p['strike'])])
    # The swing foot's lift f(1-f)(1+k f), peak 1: skew k < 0 lifts it early (a walk's heel
    # flick), k > 0 late (a run's knee drives the foot high and forward before it reaches).
    k = p['skew']; top = max(i/200*(1-i/200)*(1+k*i/200) for i in range(201))

    def toe_in(side): return ry(-(1 if side == 'right' else -1)*p['toe_out']*RAD)

    def stance(side, s):
        """The planted foot at stance progress s: ankle and world rotation. It rolls over
        the heel's edge after the strike and over the toe's edge as it pushes off."""
        sgn = 1 if side == 'right' else -1
        sole, heel, toe = SOLE[side]; Ry = toe_in(side)
        if s < p['heel']: pitch, pivot = p['strike']*(1-(s/p['heel'])**2), (0, -sole, heel)
        elif s > p['peel']: pitch, pivot = -p['push']*((s-p['peel'])/(1-p['peel']))**2, (0, -sole, toe)
        else: pitch, pivot = 0., (0, -sole, heel)
        R = Ry@rx(pitch*RAD); pivot = np.array(pivot)
        flat = np.array([sgn*p['track'], sole, HIP[side][2]+p['center']+span*(s-.5)])
        return flat+Ry@pivot-R@pivot, R

    def swing(side, f):
        """The free foot: it peels away still drifting back, the knee drives it forward and
        up, then the shin reaches and the heel comes down, already moving with the ground."""
        sgn = 1 if side == 'right' else -1
        (a0, _), (a1, _) = stance(side, 1.), stance(side, 0.)
        m0, m1 = p['speed']*swing_t*p['leave'], p['speed']*swing_t*p['land']
        z = (2*f**3-3*f**2+1)*a0[2]+(f**3-2*f**2+f)*m0+(-2*f**3+3*f**2)*a1[2]+(f**3-f**2)*m1
        y = a0[1]+(a1[1]-a0[1])*SHAPES['ease'](f)+p['lift']*f*(1-f)*(1+k*f)/top
        x = sgn*(p['track']+p['arc']*math.sin(math.pi*f))
        return np.array([x, y, z]), toe_in(side)@rx(pitch_swing(f)*RAD)

    def body(t, follow=1.):
        """Pelvis position and rotation, chest rotation (GeckoLib degrees). follow scales the
        chest's own turn and footfall drop: the sword's frame takes only part of them."""
        u = (t % T)/T; f = (2*u) % 1
        over = lambda lag: math.sin(2*math.pi*(u-beta/2+.25-lag))   # +1: weight over the right foot
        pos = (p['sway']*over(0), float(p['bob']*bob(f))-p['sink'], p['surge']*math.sin(2*math.pi*f))
        prot = (p['tilt'], -p['yaw']*math.cos(2*math.pi*u), hitch+p['roll']*over(0))
        # The chest, in the world: it leans, counter-turns against the hips about an axis that
        # leans `spine` of the way with it (so the turn swings the shoulders, not the head),
        # and only rolls with the hips' list a beat late; the torso's own channels undo the
        # pelvis's yaw, list and hitch.
        chest = follow*p['chest']*math.cos(2*math.pi*(u-p['lagc']))
        lean = REST_ANG['torso'][0]-(p['tilt']+p['lean']+follow*p['nod']*load(f))*RAD
        roll = p['roll']*(over(0)-over(p['lagc']))-p['trunk']*over(p['lagc'])
        Tw = rx(p['spine']*lean)@ry(-chest*RAD)@rx((1-p['spine'])*lean)@rz(roll*RAD)
        Rp = rz(prot[2]*RAD)@ry(-prot[1]*RAD)@rx(-prot[0]*RAD)
        return pos, prot, tuple(gecko_from('torso', euler_of(Rp.T@Tw))[0])

    def pose(pos, prot, trot, neck=(0, 0, 0)):
        return fk({'pelvis': (prot, np.array(pos)*[-16, 16, 16]), 'torso': (trot, (0, 0, 0)), 'neck': (neck, (0, 0, 0))})

    def clip(t):
        u = (t % T)/T; f = (2*u) % 1
        pos, prot, trot = body(t)
        feet, foot_r, knee_hint = {}, {}, {}
        for side, phase in (('right', u), ('left', (u+.5) % 1)):
            sgn = 1 if side == 'right' else -1
            feet[side], foot_r[side] = stance(side, phase/beta) if phase < beta else swing(side, (phase-beta)/(1-beta))
            knee_hint[side] = np.array([sgn*p['knee_out'], 0, -1])
        neck = (-p['neck']*(p['tilt']+p['lean']+p['nod']*load(f)), 0, 0)
        W = pose(pos, prot, trot, neck)
        # The head holds the guard's gaze in the world; it only nods into each footfall.
        goal = rx(-p['look']*load(f)*RAD)@REST['head'][:3, :3]
        head = gecko_from('head', euler_of(W['neck'][:3, :3].T@goal))[0]
        # The sword rides the chest a beat late, and its weight dips after every footfall.
        S = pose(*body(t-p['lag'], p['follow']))['torso']@np.linalg.inv(REST['torso'])
        lag_f = (2*((t-p['lag']*.5) % T)/T) % 1
        j = _jolt(lag_f)
        cp, cd, cn = p['carry']
        D = unit(S[:3, :3]@unit(cd)+np.array([0, -math.tan(p['tip']*RAD*j), 0]))
        sword_p = S[:3, :3]@np.array(cp, float)+S[:3, 3]+np.array([0, -p['dip']*j, 0])
        # Cloth: the cape lies along his back, so his lean already swings its hem out behind
        # him; every footfall sends a ripple across it and it sways against his weight shift.
        # The tabard hangs plumb from the tilted pelvis. Both are pushed off the legs: the
        # driving knee kicks the tabard forward and aside, the leg behind lifts the cape.
        # The cape keeps clear of capsules round the legs; the tabard hangs between the
        # thighs, inside those capsules, so it is kept off the leg cubes themselves, posed
        # as rig.Solver will pose them.
        legs, cubes = {}, {}
        Rp, Pi = W['pelvis'][:3, :3], np.linalg.inv(W['pelvis'])
        for side in ('right', 'left'):
            hip = (W['pelvis']@np.r_[HIP[side], 1])[:3]
            knee = Solver.two_bone(hip, feet[side], *LEG[side], knee_hint[side])
            heel, toe = (feet[side]+foot_r[side]@(e-ANKLE[side]) for e in SOLE_AXIS[side])
            legs[side] = [(hip, knee, GIRTH[side][0]), (knee, feet[side], GIRTH[side][1]), (heel, toe, FOOT_GIRTH[side])]
            Rt = rot_between(Rp@(KNEE[side]-HIP[side]), knee-hip)@Rp
            Rk = rot_between(Rt@(ANKLE[side]-KNEE[side]), feet[side]-knee)@Rt
            posed = [P@Rt.T+hip-Rt@HIP[side] for P in LEG_CUBES[side][0]]+[P@Rk.T+knee-Rk@KNEE[side] for P in LEG_CUBES[side][1]]
            cubes[side] = [_box(P@Pi[:3, :3].T+Pi[:3, 3]) for P in posed]

        def into(bone, caps):        # leg capsules into a bone's rest space
            Mi = np.linalg.inv(W[bone])
            return [(Mi[:3, :3]@a+Mi[:3, 3], Mi[:3, :3]@b+Mi[:3, 3], r) for a, b, r in caps]
        extra = cloth(t, period=T)
        for i, name in enumerate(CAPE):
            side = (i-3)/3                       # -1 at his right edge, +1 at his left
            ripple = _jolt((f-.06-.03*abs(side)) % 1, 4, 1.1)
            (x, y, z), px = extra[name]
            x += p['cape']+p['flutter']*ripple+p['yaw_drag']*side*math.sin(2*math.pi*(u-.1))
            z -= p['cape_sway']*math.sin(2*math.pi*(u-beta/2-.05))
            extra[name] = ((x+_push(name, into('torso', legs['right']+legs['left']), x, z), y, z), px)
        R, L = cubes['right'], cubes['left']
        roll = max(-22, min(22, .5*(_push('tabard', L, sense=-1)-_push('tabard', R, sense=-1))))
        (x, y, z), px = extra['tabard']
        x += p['tabard']-prot[0]; z += roll-prot[2]
        extra['tabard'] = ((x-_push('tabard', R+L, x, z, -1), y, z), px)
        return {'pelvis_pos': pos, 'pelvis_rot': prot, 'torso_rot': trot, 'neck_rot': neck, 'head_rot': head,
                'sword_p': sword_p, 'sword_d': D, 'sword_n': S[:3, :3]@np.array(cn, float),
                'feet': feet, 'foot_r': foot_r, 'knee_hint': knee_hint, 'extra': extra}
    return clip


# speed (blocks/s), period (s, a multiple of the 20 Hz bake step), duty (stance share of the
# cycle), track (ankle off the centre line; the hips are at 0.375), toe_out, strike (toe-up at
# heel strike), push (heel-up at toe-off), hang (toe drop in early swing), heel/peel (stance
# share before the foot is flat / when the heel starts to rise), lift/skew (swing clearance and
# where in the swing it peaks), arc (sideways bow of the swing; < 0 brings the foot in under
# the hip), center (stance placed behind the hip), leave/land (share of ground speed the
# swinging foot keeps at lift-off and has at touch-down), knee_out (sideways knee aim),
# hitch (share of HITCH), sink/bob (pelvis height; bob_keys shape one step from heel strike),
# sway/surge, roll/yaw/tilt (pelvis), lean/chest/spine/trunk/nod/drop/lagc (chest: lean,
# counter-turn, how far the turn's axis leans with it, roll over the stance leg, the drop into
# each footfall and where in the step it bottoms out, lag behind the pelvis), neck (share of
# the lean the neck takes back), look (head nod per footfall), lag/dip/tip/follow (sword:
# delay, drop and point dip after a footfall, share of the chest's turn and drop it follows),
# carry (pommel, blade and flat in the rest pose's frame; the chest carries it), and
# cape/flutter/yaw_drag/cape_sway and tabard (cloth, deg).
# The walk is a long, straight-legged stride: the weight drops into each heel strike, the
# trailing leg drives off a nearly straight knee, and the energy above the hips goes into the chest's
# counter-turn and footfall drop, not into side-to-side rocking. He carries the blade low and
# out to his right, following only part of the chest's turn, so his mask stays clear from the
# front and three-quarter views and the fists pass over the driving knee. The charge leans
# from the hips, drives the knee high, spends over a third of each step in the air and pumps
# the shoulders harder. It carries the blade cocked out to his right, set back against his
# lean, so his face and chest stay clear and the point is ready to come round; the fists ride
# high enough that the driving right knee passes under the grip.
def _aimed(d, out, down):
    """Direction d turned out degrees further to his right about the upright and down degrees lower."""
    h, e = math.atan2(d[0], -d[2])+out*RAD, math.asin(d[1])-down*RAD
    return np.array([math.cos(e)*math.sin(h), math.sin(e), -math.cos(e)*math.cos(h)])


_OUT = rot_between(GUARD_D, _aimed(GUARD_D, 22, 20))
GUARD_CARRY = (GUARD_P+np.array([.15, .14, 0]), _OUT@GUARD_D, _OUT@SWORD_REST_N)
_COCK = rx(14*RAD)@unit((.82, .52, -.2)); _OUT2 = rot_between(_COCK, _aimed(_COCK, 4, 2))
READY = (GUARD_P+np.array([.29, .54, .22]), _OUT2@_COCK, _OUT2@rx(14*RAD)@SWORD_REST_N)
WALK = dict(speed=3.0, period=1., duty=.6, track=.45, toe_out=5, strike=14, push=30, hang=12, heel=.16, peel=.65,
            lift=.2, skew=-2/3, arc=-.04, center=.15, leave=.4, land=.8, knee_out=0, hitch=1,
            sink=-.065, bob=.05, bob_keys=[(0, 0), (.2, -.6, 'out'), (.5, .7), (.88, -.3), (1, 0)], sway=.03, surge=.025,
            roll=2, yaw=8, tilt=2, lean=5, chest=12, spine=.75, trunk=0, nod=5, drop=.25, lagc=.04, neck=.4, look=1.5,
            lag=.06, dip=.03, tip=1.5, follow=.3, carry=GUARD_CARRY,
            cape=-2, flutter=3, yaw_drag=3, cape_sway=3, tabard=3)
RUN = dict(speed=4.5, period=.9, duty=.35, track=.44, toe_out=3, strike=6, push=40, hang=16, heel=.1, peel=.42,
           lift=.8, skew=1.2, arc=-.05, center=.3, leave=.3, land=.4, knee_out=0, hitch=1,
           sink=0, bob=.2, bob_keys=[(0, .4), (.3, 0, 'out'), (.65, .85), (.82, 1, 'out'), (1, .4, 'in')], sway=.02, surge=.03,
           roll=2, yaw=10, tilt=13, lean=9, chest=14, spine=.75, trunk=0, nod=6, drop=.3, lagc=.05, neck=.5, look=2,
           lag=.04, dip=.05, tip=2, follow=.45, carry=READY,
           cape=-4, flutter=5, yaw_drag=4, cape_sway=4, tabard=8)
# The ground speed (blocks/s) each treadmill loop is authored at: the game moves him at this
# speed (and scales the loop's playback by actual speed / this), the workshop scrolls its floor.
GROUND_SPEED = {'walk': WALK['speed'], 'run': RUN['speed']}
walk = gait(WALK)
run = gait(RUN)


# ---------------------------------------------------------------- the edge leads
# sword_n is the way the blade's flat faces (rig.sword_frame): the blade's local X runs edge to
# edge and Z faces off the flat. For an edge to lead, the flat must face across the arc it cuts.
SWORD_TIP = 72/16           # ring bottom to tip, blocks
GUARD_N = SWORD_REST_N


def slerp(a, b, f):
    a, b = unit(a), unit(b); w = math.acos(max(-1., min(1., float(np.dot(a, b)))))
    return a if w < 1e-6 else unit((math.sin((1-f)*w)*a+math.sin(f*w)*b)/math.sin(w))


def rslerp(A, B, f):
    """The rotation f of the way from A to B (3x3), along the shortest turn."""
    R = A.T@B; c = max(-1., min(1., (np.trace(R)-1)/2)); w = math.acos(c)
    if w < 1e-9: return A.copy()
    axis = np.array([R[2, 1]-R[1, 2], R[0, 2]-R[2, 0], R[1, 0]-R[0, 1]])
    if np.linalg.norm(axis) < 1e-6: axis = (R+np.eye(3))[:, np.argmax(np.linalg.norm(R+np.eye(3), axis=0))]
    x, y, z = unit(axis); K = np.array([[0, -z, y], [z, 0, -x], [-y, x, 0]]); a = f*w
    return A@(np.eye(3)+math.sin(a)*K+(1-math.cos(a))*K@K)


def ncurve(keys):
    """A facing curve: unit vectors slerped with the named shapes (keep keys under 180 deg apart)."""
    pts = [(float(k[0]), unit(k[1]), k[2] if len(k) > 2 else 'ease') for k in keys]

    def at(t):
        if t <= pts[0][0]: return pts[0][1]
        for (a, av, _), (b, bv, shape) in zip(pts, pts[1:]):
            if t <= b:
                return slerp(av, bv, SHAPES[shape](min(1, max(0, (t-a)/(b-a) if b > a else 1))))
        return pts[-1][1]
    return at


def sweep(P, D, t, h=.05):
    """How the blade sweeps at t, as the 20 Hz bake shows it: the flat facing that puts its -X
    edge first, unit(D x tip velocity), and the tip's speed across the blade (blocks/s)."""
    def tip(s): return P(s)+SWORD_TIP*unit(D(s))
    d = unit(D(t)); v = (tip(t+h)-tip(t-h))/(2*h); v = v-np.dot(v, d)*d
    return unit(np.cross(d, v)), float(np.linalg.norm(v))


def planes(P, D, times):
    """The blade's sweep planes (sweep's facing) at the given times, each signed like the last."""
    out = []
    for t in times:
        n = sweep(P, D, t)[0]; out.append(-n if out and np.dot(n, out[-1]) < 0 else n)
    return out


def edge_lead(P, D, rest, lead, t):
    """sword_n and how firmly the edge must lead (0-1). While lead(t) is 1 and the blade moves
    fast, the flat faces the plane it sweeps, so an edge cuts first; slower, it follows `rest`,
    keyed to the blows' planes through tells and recoveries. The sweep takes rest's sign."""
    n, speed = sweep(P, D, t); r = rest(t)
    if np.dot(n, r) < 0: n = -n
    lock = float(lead(t))*SHAPES['ease'](min(1, max(0, (speed-2)/6)))
    return {'sword_n': slerp(r, n, lock), 'edge_lock': lock}


# ---------------------------------------------------------------- wrists that can roll
# Rolling the blade rolls both fists with it, and rig.Solver lays each forearm along the
# shortest turn from the sculpt. So a clip that rolls the blade is planned over the bake's
# 20 Hz samples: where the edge need not lead, the blade may roll further about its axis,
# and each gripping elbow moves round its IK circle, to keep the wrists comfortable.
from rig import fk, pivot_world, sword_frame, SWORD_LOCAL_R, GRIP   # noqa: E402
ARM = {side: tuple(baked_point(BY[side+b]['pivot']) for b in ('_upper_arm', '_forearm', '_hand'))
       for side in ('right', 'left')}
ELBOWS = np.radians(np.arange(-180, 180, 10))     # elbow round its circle; 0 = rig.Solver's own
ROLLS = np.radians(np.arange(-180, 180, 15))      # extra roll about the blade; 0 = sword_n
RELAX = .15                                       # s a fist takes to ease into or out of its grip
EDGE_SLACK = 15                                   # deg the roll may still lack as a blow starts


def rows(v): return v/np.linalg.norm(v, axis=-1)[..., None]


def turn(a, b):
    """Batched rig.rot_between: the shortest turns taking each a onto b, (n, 3, 3)."""
    a = rows(np.broadcast_to(a, b.shape)); b = rows(b)
    v = np.cross(a, b); c = np.einsum('ij,ij->i', a, b)
    K = np.zeros(b.shape+(3,)); K[:, 0, 1], K[:, 0, 2], K[:, 1, 2] = -v[:, 2], v[:, 1], -v[:, 0]
    K -= K.transpose(0, 2, 1)
    return np.eye(3)+K+K@K/np.maximum(1+c, 1e-6)[:, None, None]


def wrist_angles(Q, side):
    """The fist's turn against its forearm (Q = forearm^T hand, (..., 3, 3)): twist about the
    forearm and bend off its axis, in degrees; both zero in the sculpted guard."""
    a = unit(ARM[side][2]-ARM[side][1])
    def d(i, j): return Q[..., i, j]
    w = np.sqrt(np.maximum(0, 1+d(0, 0)+d(1, 1)+d(2, 2)))/2
    v = np.stack([np.copysign(np.sqrt(np.maximum(0, 1+d(0, 0)-d(1, 1)-d(2, 2)))/2, d(2, 1)-d(1, 2)),
                  np.copysign(np.sqrt(np.maximum(0, 1-d(0, 0)+d(1, 1)-d(2, 2)))/2, d(0, 2)-d(2, 0)),
                  np.copysign(np.sqrt(np.maximum(0, 1-d(0, 0)-d(1, 1)+d(2, 2)))/2, d(1, 0)-d(0, 1))], -1)
    twist = (np.degrees(2*np.arctan2(v@a, w))+180) % 360-180
    return twist, np.degrees(np.arccos(np.clip((Q@a)@a, -1, 1)))


def grips(spec, side):
    return spec.get('hands', 'both') == 'both' or (side == 'right' and spec.get('hands') == 'right')


def elbow_circle(spec, side):
    """rig.Solver's arm for this frame: shoulder, torso turn, hand target, the elbow circle's
    centre offset and radius (ca, sa along n, basis u, w) with angle 0 at its unhinted elbow."""
    W = fk({'pelvis': (tuple(spec.get('pelvis_rot', (0, 0, 0))),
                       tuple(np.array(spec.get('pelvis_pos', (0, 0, 0)))*[-16, 16, 16])),
            'torso': (tuple(spec.get('torso_rot', (0, 0, 0))), (0, 0, 0))})
    s0, e0, h0 = ARM[side]; l1, l2 = np.linalg.norm(e0-s0), np.linalg.norm(h0-e0)
    D = unit(spec['sword_d']); point = np.asarray(spec['sword_p'], float)+GRIP[side]*D
    if not grips(spec, side): point = np.asarray(spec['free_hands'][side], float)
    S = pivot_world(W, side+'_upper_arm'); Rt = W['torso'][:3, :3]; n = unit(point-S)
    d = min(max(np.linalg.norm(point-S), abs(l1-l2)+1e-4), l1+l2-1e-4)
    ca = (l1*l1+d*d-l2*l2)/(2*l1*d); sa = math.sqrt(max(0, 1-ca*ca))
    u = Rt@(e0-s0); u = unit(u-np.dot(u, n)*n)
    return W, S, Rt, point, n, ca, sa, u, np.cross(n, u)


def _boxes(bone):
    """A bone's cubes as boxes in its rest space: centres (k, 3), axes (k, 3, 3) as rows, half sizes (k, 3)."""
    P = np.array([_corners(c) for c in BY[bone]['cubes']])
    A = np.stack([P[:, 4]-P[:, 0], P[:, 2]-P[:, 0], P[:, 1]-P[:, 0]], 1)
    h = np.linalg.norm(A, axis=-1)
    return P.mean(1), A/np.maximum(h, 1e-9)[..., None], h/2


# What a gripping arm must not pass through: the forearm keeps out of the chest, shoulders,
# neck and head; the upper arm, which grows out of the torso and collar, out of the rest.
# Each limb is a row of balls (fractions along it, radius in blocks) about as thick as it.
TRUNK = ['torso', 'chest_shard', 'mantle_collar', 'neck', 'head', 'death_mask', 'ash_beard', 'crown']
HEAD = ['chest_shard', 'neck', 'head', 'death_mask', 'ash_beard', 'crown']
BODY_BOXES = {bone: _boxes(bone) for bone in TRUNK}
UPPER_BALLS, FORE_BALLS, LIMB_R = np.array([.55, .8, 1.]), np.array([-.25, 0., .25, .5, .7]), .24


def _sunk(X, boxes):
    """How deep balls of radius LIMB_R at points X (..., 3) sink into world boxes (centres,
    axes, halves): the deepest per point, in blocks."""
    C, A, H = boxes
    q = np.abs(np.einsum('kij,...kj->...ki', A, X[..., None, :]-C))-H
    sd = np.linalg.norm(np.maximum(q, 0), axis=-1)+np.minimum(q.max(-1), 0)
    return np.maximum(0, LIMB_R-sd).max(-1)


def body_boxes(spec, names):
    """The trunk's boxes in the world for this spec's pelvis, chest, neck and head."""
    W = fk({'pelvis': (tuple(spec.get('pelvis_rot', (0, 0, 0))), tuple(np.array(spec.get('pelvis_pos', (0, 0, 0)))*[-16, 16, 16])),
            'torso': (tuple(spec.get('torso_rot', (0, 0, 0))), (0, 0, 0)),
            'neck': (tuple(spec.get('neck_rot', (0, 0, 0))), (0, 0, 0)), 'head': (tuple(spec.get('head_rot', (0, 0, 0))), (0, 0, 0))})
    C, A, H = [], [], []
    for bone in names:
        c, a, h = BODY_BOXES[bone]; R, p = W[bone][:3, :3], W[bone][:3, 3]
        C.append(c@R.T+p); A.append(np.einsum('ij,kmj->kmi', R, a)); H.append(h)
    return np.vstack(C), np.vstack(A), np.vstack(H)


def arm_options(spec, side):
    """Every elbow place x extra blade roll for one gripping arm: the wrist turns Q
    (elbows, rolls, 3, 3) and their costs, and the upper arm's and forearm's local turns
    (elbows, 3, 3) for pricing how far they swing between samples."""
    W, S, Rt, point, n, ca, sa, u, w = elbow_circle(spec, side)
    s0, e0, h0 = ARM[side]; l1 = np.linalg.norm(e0-s0)
    E = S+l1*(ca*n+sa*(np.cos(ELBOWS)[:, None]*u+np.sin(ELBOWS)[:, None]*w))
    Ru = turn(Rt@(e0-s0), E-S)@Rt; Rf = turn(np.einsum('nij,j->ni', Ru, h0-e0), point-E)@Ru
    if spec.get('hinge'):                                       # as rig.Solver.hinged poses them
        N = rows(np.cross(np.cos(ELBOWS)[:, None]*u+np.sin(ELBOWS)[:, None]*w, n))
        def frames(x, y): return np.stack([x, y, np.cross(x, y)], -1)
        a0, b0 = unit(e0-s0), unit(h0-e0); n0 = unit(np.cross(a0, b0))
        Ru = frames(rows(E-S), N)@frames(a0, n0).T; Rf = frames(rows(point-E), N)@frames(b0, n0).T
    D = unit(spec['sword_d']); r = sword_frame(D, spec.get('sword_n'))[:, 2]; q = np.cross(D, r)
    Rh = np.array([sword_frame(D, math.cos(f)*r+math.sin(f)*q)@SWORD_LOCAL_R.T for f in ROLLS])
    Q = np.einsum('eji,rjk->erik', Rf, Rh)
    twist, bend = wrist_angles(Q, side)
    elbow = (E-pivot_world(W, 'torso'))@Rt                      # in the torso's frame
    sgn = 1 if side == 'right' else -1                          # an elbow inside the chest:
    inside = (elbow[:, 0]*sgn < .62) & (abs(elbow[:, 2]+.05) < .45) & (abs(elbow[:, 1]-.6) < .8)
    raised = np.degrees(np.arccos(np.clip(rows(E-S)@unit(Rt@(e0-s0)), -1, 1)))   # off its rest line
    # The limbs sunk into the trunk, neck and head (the old box test only knew the chest).
    upper = S+UPPER_BALLS[:, None, None]*(E-S)                  # (balls, elbows, 3)
    fore = E+FORE_BALLS[:, None, None]*(point-E)
    sunk = (_sunk(upper, body_boxes(spec, HEAD))**2).sum(0)+(_sunk(fore, body_boxes(spec, TRUNK))**2).sum(0)
    cost = (twist**2+bend**2+60*np.maximum(0, abs(twist)-80)**2+30*np.maximum(0, bend-75)**2
            + (.3*np.degrees(ELBOWS)**2+2e5*inside+2e5*sunk
               + 50*np.maximum(0, raised-(130 if side == 'right' else 100))**2)[:, None])
    return Q, cost, (np.einsum('ji,njk->nik', Rt, Ru), np.einsum('nji,njk->nik', Ru, Rf))


def geodesic(A, B):
    """Angles (deg) between every rotation in A (a, 3, 3) and in B (b, 3, 3), as (b, a)."""
    return np.degrees(np.arccos(np.clip((np.einsum('ajk,bjk->ba', A, B)-1)/2, -1, 1)))


def apart(angles): return np.degrees(np.abs((angles[:, None]-angles[None, :]+math.pi) % (2*math.pi)-math.pi))


def viterbi(costs, steps):
    """The cheapest path: costs[i] per state, steps[i-1][new, old] to move from sample i-1 to i."""
    best, back = costs[0], []
    for c, st in zip(costs[1:], steps):
        total = best[None, :]+st; back.append(np.argmin(total, axis=1)); best = c+total.min(axis=1)
    k = int(np.argmin(best)); path = [k]
    for b in reversed(back): k = int(b[k]); path.append(k)
    return path[::-1]


def easy_wrists(length, rate=20):
    """Clip decorator for clips that roll the blade. A spec may carry 'edge_lock' (0-1): from
    0.5 the blade keeps sword_n's roll or its half turn, so one edge or the other still leads.
    Planned once by dynamic programming over the bake's samples: first the extra roll, then
    each gripping elbow, trading wrist twist and bend against elbow travel and sudden turns.
    The first and last samples keep the guard's roll and elbows."""
    def wrap(pose):
        plan = {}

        def build():
            times = [round(i/rate, 4) for i in range(int(round(length*rate))+1)]
            specs = [pose(t) for t in times]; last = len(times)-1
            opts = [{side: arm_options(sp, side) for side in ('right', 'left') if grips(sp, side)} for sp in specs]
            rolls = np.degrees(ROLLS); spin = apart(ROLLS)
            costs = []
            # A sample whose next half-interval locks the edge must be nearly rolled into it
            # already (within EDGE_SLACK): the roll is lerped between samples, so what is left
            # over shows in the blow's first frames. Exactly would cost the cocked wrist too much.
            soon = [pose(min(t+.5/rate, length)).get('edge_lock', 0) >= .5 for t in times]
            for i, (sp, o) in enumerate(zip(specs, opts)):
                c = sum((o[side][1].min(axis=0) for side in o), np.zeros(len(ROLLS)))
                if sp.get('edge_lock', 0) >= .5: c = c+1e7*(abs(abs(rolls)-90) < 89)
                elif soon[i]: c = c+1e7*(abs(abs(rolls)-90) < 90-EDGE_SLACK)
                costs.append(c+1e8*(rolls != 0)*(i in (0, last)))
            # The roll turns smoothly, and never while no fist holds the sword (it flies or sticks).
            roll = viterbi(costs, [.5*spin**2+50*np.maximum(0, spin-40)**2+1e9*(spin > 0)*(not opts[i] and not opts[i+1])
                                   for i in range(last)])
            for side in ('right', 'left'):
                run = []
                for i in range(len(times)+1):
                    if i < len(times) and side in opts[i]:
                        run.append(i); continue
                    if run:
                        Qs = [opts[j][side][0][:, roll[j]] for j in run]
                        cs = [opts[j][side][1][:, roll[j]]+1e8*(ELBOWS != 0)*(j in (0, last)) for j in run]
                        # Sudden turns of the wrist, the upper arm or the forearm cost the
                        # most: GeckoLib lerps them between samples, and a fast one throws
                        # the fist off the grip in the in-between frames. So does an elbow
                        # swinging more than 60 deg round its circle in one sample (none of
                        # the clips needs one; the Turning Cut's planner took 150 deg).
                        limbs = [opts[j][side][2] for j in run]
                        st = [2*g**2+40*np.maximum(0, g-40)**2+apart(ELBOWS)**2+50*np.maximum(0, apart(ELBOWS)-60)**2
                              + sum(a**2+20*np.maximum(0, a-45)**2 for a in (geodesic(u0, u1), geodesic(f0, f1)))
                              for g, (u0, f0), (u1, f1) in zip((geodesic(a, b) for a, b in zip(Qs, Qs[1:])), limbs, limbs[1:])]
                        for j, k in zip(run, viterbi(cs, st)): plan[(side, times[j])] = float(ELBOWS[k]); plan[('held', side, times[j])] = 1.
                    run = []
                # A fist that lets go or takes hold eases its elbow over 0.2 s, without a snap.
                for i, t in enumerate(times):
                    if (side, t) in plan: continue
                    near = [(abs(j-i), plan[(side, times[j])]) for j in range(max(0, i-4), min(len(times), i+5))
                            if (side, times[j]) in plan]
                    if near: d, th = min(near); plan[(side, t)] = th*SHAPES['ease'](1-d/5); plan[('held', side, t)] = SHAPES['ease'](1-d/5)
                # When the fist lets go or takes hold: (switch time, a time it grips, takes?).
                switches = []
                for i in range(last):
                    held = grips(specs[i], side)
                    if held == grips(specs[i+1], side): continue
                    lo, hi = times[i], times[i+1]
                    for _ in range(24):
                        mid = (lo+hi)/2; lo, hi = (mid, hi) if grips(pose(mid), side) == held else (lo, mid)
                    switches.append((hi, hi if not held else lo, not held))
                plan[('switch', side)] = switches
            for j, t in enumerate(times): plan[('roll', t)] = float(ROLLS[roll[j]])

        def grip_pose(t, side):
            """Where the gripping fist is at t, and its turn against the forearm."""
            if ('grip', side, t) not in plan:
                W = fk(Solver().solve(clip(t, settle=False)))
                plan[('grip', side, t)] = (pivot_world(W, side+'_hand'), W[side+'_forearm'][:3, :3].T@W[side+'_hand'][:3, :3])
            return plan[('grip', side, t)]

        def clip(t, settle=True):
            spec = pose(t); spec.pop('edge_lock', None)
            if not plan: build()
            i = t*rate; a, b = math.floor(i), math.ceil(i); ta, tb = round(a/rate, 4), round(b/rate, 4)

            def planned(key):
                if (key, ta) not in plan or (key, tb) not in plan: return None
                x, y = plan[(key, ta)], plan[(key, tb)]
                return x+((y-x+math.pi) % (2*math.pi)-math.pi)*(i-a)
            f = planned('roll')
            if f:
                D = unit(spec['sword_d']); r = sword_frame(D, spec.get('sword_n'))[:, 2]
                spec['sword_n'] = math.cos(f)*r+math.sin(f)*np.cross(D, r)
            # A fist that lets go of the sword keeps its grip's turn and eases out of it over
            # RELAX s; one about to take hold reaches for the grip and turns into it ahead of
            # time. So neither the fist nor the sword it lets go of snaps.
            wrists = {}
            for side in ('right', 'left') if settle else ():
                if grips(spec, side): continue
                Q = np.eye(3)
                for ts, tq, takes in plan[('switch', side)]:
                    d = ts-t if takes else t-ts
                    if not -1e-6 <= d < RELAX: continue
                    k = SHAPES['ease'](min(1, 1-d/RELAX)); at, Qg = grip_pose(tq, side)
                    spec['free_hands'] = {**spec['free_hands'], side: (1-k)*np.asarray(spec['free_hands'][side], float)+k*at}
                    Q = rslerp(Q, Qg, k)
                wrists[side] = Q
            if wrists: spec['wrist'] = wrists
            # A clip may say where a free arm's elbow points (free_elbow {side: vec}); near a
            # grip change it blends into the planned elbow.
            hints, free = {}, spec.pop('free_elbow', {})
            for side in ('right', 'left'):
                th = planned(side); g = None if grips(spec, side) else free.get(side)
                if th is not None:
                    u, w = elbow_circle(spec, side)[-2:]; h = math.cos(th)*u+math.sin(th)*w
                    if g is not None:
                        k = plan.get(('held', side, ta), 0.)*(1-(i-a))+plan.get(('held', side, tb), 0.)*(i-a)
                        h = unit(k*h+(1-k)*unit(g))
                    hints[side] = h
                elif g is not None: hints[side] = unit(g)
            if hints: spec['elbow_hint'] = hints
            return spec
        clip.__name__, clip.__doc__, clip.pose = pose.__name__, pose.__doc__, pose
        return clip
    return wrap


# ---------------------------------------------------------------- Royal Cut
@easy_wrists(3.8)
def royal_cut(t):
    """Two cuts across a 150-degree arc, then a 1.2 s pause: the punish window.
    An edge leads both blows: the blade rolls into each blow's plane before it starts."""
    P = curve([(0, GUARD_P),
               (.5, (.78, 3.25, -.25)), (.9, (.82, 3.32, -.2), 'ease'),            # cock high right, tremble
               (.97, (.3, 2.85, -1.0), 'hit'), (1.05, (-.28, 2.5, -.92), 'linear'),   # the cut
               (1.3, (-.38, 2.44, -.86), 'snap'),                                     # follow-through, in reach
               (1.6, (-.64, 2.79, -.62)), (1.75, (-.69, 2.86, -.59)),                    # re-cock left
               (1.82, (-.1, 2.75, -1.05), 'hit'), (1.9, (.55, 2.5, -.75), 'linear'),  # backhand
               (2.2, (.74, 2.32, -.46), 'snap'),
               (2.6, (.48, 2.2, -.42)), (3.4, (.46, 2.18, -.44)),                    # the pause
               (3.8, GUARD_P)])
    D = ncurve([(0, GUARD_D),
                (.5, (.62, .48, .62)), (.9, (.6, .51, .62)),
                (.97, (.25, .2, -1.0), 'hit'), (1.05, (-.95, -.12, -.45), 'linear'),
                (1.3, (-.72, -.35, .6), 'snap'),
                (1.6, (-.55, .3, .78)), (1.75, (-.52, .34, .78)),
                (1.82, (-.1, .1, -1.0), 'hit'), (1.9, (.95, -.05, -.45), 'linear'),
                (2.2, (.7, -.3, .62), 'snap'),
                (2.6, (.55, -.32, -.77)), (3.4, (.55, -.33, -.77)),
                (3.8, GUARD_D)])
    torso = curve([(0, (0, 0, 0)), (.5, (-4, 26, 3)), (.9, (-5, 28, 3)),
                   (1.05, (8, -34, -4), 'hit'), (1.3, (9, -42, -5), 'snap'),
                   (1.75, (5, -54, -3)), (1.9, (10, 30, 4), 'hit'), (2.2, (11, 36, 5), 'snap'),
                   (2.6, (6, 16, 2)), (3.4, (5, 14, 2)), (3.8, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.5, (0, 12, 0)), (.9, (0, 13, 0)), (1.05, (0, -16, 0), 'hit'),
                    (1.75, (0, -20, 0)), (1.9, (0, 14, 0), 'hit'), (2.6, (0, 8, 0)), (3.8, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.5, (.08, .02, .12)), (.9, (.09, .02, .13)),
                   (1.05, (-.05, -.12, -.28), 'hit'), (1.75, (-.08, -.1, -.2)),
                   (1.9, (.06, -.14, -.3), 'hit'), (2.6, (.02, -.08, -.18)), (3.4, (.02, -.07, -.16)),
                   (3.8, (0, 0, 0))])
    step = curve([(0, (0, 0, 0)), (.9, (0, 0, 0)), (1.05, (0, 0, -.32), 'hit'), (3.4, (0, 0, -.32)), (3.8, (0, 0, 0))])
    lift = curve([(0, 0), (.9, 0), (.98, .18, 'out'), (1.05, 0, 'in'), (3.4, 0), (3.6, .12), (3.8, 0)])
    head = curve([(0, (0, 0, 0)), (.5, (-3, -10, 0)), (1.05, (2, 12, 0)), (1.75, (0, 24, 0)),
                  (1.9, (2, -10, 0)), (2.6, (-2, -4, 0)), (3.8, (0, 0, 0))])
    swing = curve([(0, 0), (.9, -.3), (1.05, 1, 'hit'), (1.6, .2), (1.9, 1, 'hit'), (2.6, 0)])
    # The edge leads: the flat turns into each blow's plane by the end of its tell and holds it
    # through the follow-through. easy_wrists picks which edge, and rolls the blade between.
    cut0, cut1, back0, back1 = planes(P, D, (.9, 1.2, 1.75, 2.1))
    rest = ncurve([(0, GUARD_N), (.5, cut0), (.9, cut0), (1.05, cut1, 'hit'), (1.3, cut1),
                   (1.6, back0), (1.75, back0), (1.9, back1, 'hit'), (2.2, back1),
                   (2.6, back1), (3.4, back1), (3.8, GUARD_N)])
    lead = curve([(0, 0), (.9, 0), (.93, 1, 'linear'), (1.3, 1), (1.4, 0), (1.75, 0), (1.78, 1, 'linear'),
                  (2.2, 1), (2.3, 0)])
    st = step(t)
    return {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': P(t), 'sword_d': D(t), **edge_lead(P, D, rest, lead, t),
            'feet': planted(sx=(st[0], float(lift(t)), st[2])),
            'extra': cloth(t, swing=float(swing(t)))}


# ---------------------------------------------------------------- Kingsguard Thrust
# A longsword thrust from the high guard beside the head, the point on the opponent's face
# (Ochs). He coils hard to his right with the fists beside his right shoulder and the point on
# a player's face 7.5 blocks out; the thrust drives the fists down and across to his belly as
# the chest unwinds, the point dropping onto a player's chest (THRUST_AIM). The poses were
# searched for comfortable wrists: a blade held level out in front strains this grip, so the
# fists end low and inside, as a two-handed thrust does. The game moves him the rest of the
# 2-block step.
THRUST_AIM = np.array([-.1, 1.3, -7.5])            # a player's chest, 7.5 blocks out
CHAMBER_FIST = np.array([1., 3.3, -.6])             # right fist in the tell, by his right shoulder
CHAMBER_D = unit(np.array([-.1, 2.0, -7.5])-CHAMBER_FIST)    # the point on a player's face
THRUST_FIST = np.array([-.4, 2.4, -2.2])            # right fist at full extension
THRUST_D = unit(THRUST_AIM-THRUST_FIST)
REST_FIST = np.array([-.4, 2.0, -1.75])             # the pause: fists low, point dropped ahead
REST_D = unit(np.array([.2, .8, -5.0])-REST_FIST)


def _turned_n(d):
    """The guard's flat, carried by the shortest turn that takes the guard's blade onto d."""
    return rot_between(GUARD_D, unit(d))@GUARD_N


@easy_wrists(2.9)
def kingsguard_thrust(t):
    """He coils into the high guard by his right shoulder with the point on your face (0.8 s
    tell; the aim freezes at the snap, 0.35 s). Then the left foot lunges in, the hips drive and
    the chest unwinds as both arms drive the point down onto you. He holds the extension, sags
    into the 1.2 s pause with the point dropped, and steps back to his guard."""
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home),
                  (.35, CHAMBER_FIST, 'out'), (.8, CHAMBER_FIST-.1*CHAMBER_D),        # draw, then coil tighter
                  # The drive: the arms ease through it while the hips and chest hit, which
                  # keeps both wrists comfortable as the fists cross his body.
                  (.93, THRUST_FIST, 'ease'), (1.0, THRUST_FIST+.08*THRUST_D, 'out'),  # overshoot
                  (1.2, THRUST_FIST, 'settle'),
                  (1.6, REST_FIST), (2.4, REST_FIST+(0, .01, .03)),                 # the pause
                  (2.9, home)])
    D = ncurve([(0, GUARD_D), (.35, CHAMBER_D, 'out'), (.8, CHAMBER_D), (.93, THRUST_D, 'ease'), (1.2, THRUST_D),
                (1.6, REST_D), (2.4, REST_D), (2.9, GUARD_D)])
    torso = curve([(0, (0, 0, 0)), (.35, (-4, 38, 2), 'out'), (.8, (-5, 41, 2)),
                   (.93, (12, 6, -2), 'hit'), (1.2, (13, 5, -2), 'settle'),
                   (1.6, (15, 2, -1)), (2.4, (14, 2, -1)), (2.9, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.35, (0, 18, 0), 'out'), (.8, (0, 19, 0)), (.93, (6, -4, 0), 'hit'),
                    (1.6, (5, 0, 0)), (2.4, (4, 0, 0)), (2.9, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.35, (.06, -.06, .18), 'out'), (.8, (.07, -.08, .2)),
                   (.93, (-.04, -.22, -.8), 'ease'), (1.2, (-.04, -.21, -.78), 'settle'),
                   (1.6, (-.02, -.14, -.7)), (2.4, (-.02, -.13, -.68)), (2.9, (0, 0, 0))])
    # The gaze stays on you: the neck and head undo most of the chest's turn.
    neck = curve([(0, (0, 0, 0)), (.35, (4, -20, 0), 'out'), (.8, (4, -21, 0)), (.93, (-4, 0, 0), 'hit'),
                  (1.6, (-2, 0, 0)), (2.9, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.35, (3, -34, -2), 'out'), (.8, (3, -37, -2)), (.93, (-6, -2, 0), 'hit'),
                  (1.6, (-4, -2, 0)), (2.4, (-3, -2, 0)), (2.9, (0, 0, 0))])
    # Feet: the right steps back to blade him in the tell; the left lunges in with the thrust;
    # both come home at the end.
    back = curve([(0, (0, 0, 0)), (.05, (0, 0, 0)), (.3, (.06, 0, .26)), (2.45, (.06, 0, .26)), (2.85, (0, 0, 0))])
    back_lift = curve([(0, 0), (.05, 0), (.17, .12, 'out'), (.3, 0, 'in'), (2.45, 0), (2.62, .1, 'out'), (2.85, 0, 'in')])
    lunge = curve([(0, (0, 0, 0)), (.78, (0, 0, -.12)), (.92, (-.05, 0, -1.05), 'hit'), (2.45, (-.05, 0, -1.05)),
                   (2.85, (0, 0, 0))])
    lunge_lift = curve([(0, 0), (.78, 0), (.84, .22, 'out'), (.92, 0, 'in'), (2.45, 0), (2.64, .14, 'out'), (2.85, 0, 'in')])
    swing = curve([(0, 0), (.35, -.4), (.8, -.5), (.93, 1, 'hit'), (1.4, .3), (2.4, 0)])
    # No edge leads a thrust, so easy_wrists rolls the blade freely for the wrists. It rolls
    # from the guard's flat, carried smoothly onto each direction (a level blade would make
    # the guard's own flat a poor reference: it nearly faces along the blade).
    rest = ncurve([(0, GUARD_N), (.35, _turned_n(CHAMBER_D), 'out'), (.8, _turned_n(CHAMBER_D)),
                   (.93, _turned_n(THRUST_D), 'ease'), (1.2, _turned_n(THRUST_D)),
                   (1.6, _turned_n(REST_D)), (2.4, _turned_n(REST_D)), (2.9, GUARD_N)])
    d = D(t); b = back(t); s = lunge(t)
    return {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'neck_rot': neck(t),
            'head_rot': head(t), 'sword_p': fist(t)-GRIP['right']*d, 'sword_d': d, 'sword_n': rest(t),
            'feet': planted(dx=(b[0], float(back_lift(t)), b[2]), sx=(s[0], float(lunge_lift(t)), s[2])),
            'extra': cloth(t, swing=float(swing(t)))}


# ---------------------------------------------------------------- Turning Cut
# He spins to his left and cuts the half behind him. The clip is authored in his own frame,
# then the whole pose turns by the root yaw SPIN(t) (degrees, + turns him left) about his
# centre, so he ends in his guard facing back the way he came: the game turns his body yaw by
# TURN_END degrees on the clip's last frame. Through the turn his feet pivot on their balls
# round his centre, each lifting a little in turn; a 180-degree turn about the midpoint of his
# stance swaps the two feet's places exactly.
TURN_END = 180.
PELVIS_PIVOT = pivot_world(REST, 'pelvis')


def _yaw(a): return ry(a*RAD)


def _turn_spec(spec, psi):
    """A pose authored in his own frame, turned psi degrees to his left about his centre."""
    R = _yaw(psi); out = dict(spec)
    out['pelvis_pos'] = R@(PELVIS_PIVOT+np.asarray(spec.get('pelvis_pos', (0, 0, 0)), float))-PELVIS_PIVOT
    x, y, z = spec.get('pelvis_rot', (0, 0, 0)); assert abs(z) < 1e-9   # no list: the yaw must compose exactly
    out['pelvis_rot'] = (x, y-psi, 0)
    for k in ('sword_p', 'sword_d', 'sword_n'):
        if k in spec: out[k] = R@np.asarray(spec[k], float)
    return out


def _spin():
    # One smooth push, fastest (about 700 deg/s) just before the arms bring the blade down.
    return curve([(0, 0), (.4, 0), (.8, 185, 'ease'), (.95, 188, 'ease'), (1.2, 182),
                  (2.2, 180), (2.6, TURN_END)])


@easy_wrists(2.6)
def turning_cut(t):
    """He glances back over his left shoulder and lays the blade back over his right shoulder,
    the point on whoever stands behind him, gathering his feet (0.7 s tell; snap at 0.25 s).
    Then the hips spin him left, the head leading and the blade carried high, and the cut
    whips low through the half behind him (the clang at 0.75 s) as the chest catches up. He
    ends facing back the way he came: a 1.2 s pause, and he settles into his guard."""
    spin = _spin(); psi = float(spin(t))
    # In his own frame: the blade raised high on his right, then a flat cut with the fists
    # low (below his belt) and the blade just under level, so it sweeps a player's body
    # height at reach 5. Searched for comfortable wrists with the edge leading.
    P = curve([(0, GUARD_P), (.25, (.8, 2.95, .35), 'out'), (.52, (.82, 3.0, .3)),
               (.645, (.3, 2.29, .03), 'in'),                                          # coming down past his right
               (.7, (-.03, 1.65, -.33), 'linear'), (.75, (-.35, 1.6, -.3), 'linear'), (.8, (-.37, 1.65, -.05), 'linear'),
               (.95, (-.53, 1.93, -.69), 'snap'),                                    # rising away to his left
               (1.2, (-.46, 1.93, -.29)), (2.2, (-.44, 1.91, -.3)),                   # low guard, point on you
               (2.6, GUARD_P)])
    D = ncurve([(0, GUARD_D), (.25, (.6, .7, .38), 'out'), (.52, (.6, .72, .35)), (.645, (.85, .4, -.3), 'in'),
                (.7, (.66, -.1, -.75), 'linear'), (.75, (0, -.1, -.99), 'linear'), (.8, (-.39, .03, -.92), 'linear'),
                (.95, (-.85, .53, .04), 'snap'), (1.2, (.34, -.17, -.93)), (2.2, (.34, -.18, -.92)), (2.6, GUARD_D)])
    torso = curve([(0, (0, 0, 0)), (.25, (-4, -15, 2), 'out'), (.45, (-5, -10, 2)), (.62, (-6, 22, 3), 'in'),
                   (.75, (8, -30, -4), 'hit'), (.95, (9, -40, -5), 'snap'), (1.2, (6, -18, -2)), (2.2, (5, -14, -2)),
                   (2.6, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.25, (0, -6, 0), 'out'), (.45, (2, -8, 0)), (.75, (6, -14, 0), 'hit'),
                    (1.2, (4, -8, 0)), (2.2, (3, -6, 0)), (2.6, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.25, (0, -.1, 0), 'out'), (.45, (0, -.15, .02)), (.62, (0, -.14, 0)),
                   (.75, (0, -.24, 0), 'hit'), (1.2, (0, -.17, 0)), (2.2, (0, -.15, 0)), (2.6, (0, 0, 0))])
    # The gaze: a glance back over the left shoulder, leading the spin onto whoever was behind
    # him (world yaw, + left), whatever the chest is doing; the neck takes 40% of the turn.
    gaze = curve([(0, 0), (.25, 85, 'out'), (.45, 90), (.62, 165), (.75, 180), (2.6, 180)])
    chest = psi-pelvis(t)[1]-torso(t)[1]
    look = max(-80., min(80., float(gaze(t))-chest))
    lean = curve([(0, 0), (.25, 6), (.62, 4), (.75, -6, 'hit'), (1.2, -3), (2.6, 0)])
    # Feet: gathered under him in the tell, then on their balls about his centre, each lifting
    # a little in turn; they land in his stance turned half round.
    gather = curve([(0, 0), (.08, 0), (.25, 1, 'out'), (.62, 1), (.85, 0), (2.6, 0)])
    feet, foot_r = {}, {}
    for side, peak in (('right', .52), ('left', .64)):
        home = ANKLE[side]; inner = home*np.array([.55, 1, .7])
        g = float(gather(t)); local = home+(inner-home)*g
        up = curve([(0, 0), (peak-.07, 0), (peak, .06, 'out'), (peak+.07, 0, 'in'), (2.6, 0)])
        tip = curve([(0, 0), (.42, 0), (.5, 15, 'out'), (.78, 15), (.9, 0), (2.6, 0)])
        y = float(up(t))
        if side == 'right':
            y += float(curve([(0, 0), (.08, 0), (.16, .1, 'out'), (.25, 0, 'in'), (2.6, 0)])(t))   # the gathering step
        feet[side] = _yaw(psi)@(local+np.array([0, y, 0]))
        # On the ball: heel up, rolling about the toe's edge.
        sole, heel, toe = SOLE[side]; R = _yaw(psi)@rx(-float(tip(t))*RAD)
        pivot = np.array([0, -sole, toe])
        feet[side] = feet[side]+_yaw(psi)@pivot-R@pivot
        foot_r[side] = R
    swing = curve([(0, 0), (.45, -.2), (.62, .6), (.75, 1, 'hit'), (1.2, .2), (2.2, 0)])
    lift = curve([(0, 0), (.45, 0), (.62, 1.2), (.8, 1.6), (1.1, .3), (1.6, 0)])
    body = {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t),
            'neck_rot': (float(lean(t))*.4, -.4*look, 0), 'head_rot': (float(lean(t))*.6, -.6*look, 0),
            'sword_p': P(t), 'sword_d': D(t)}
    spec = _turn_spec(body, psi)

    # The edge leads through the cut, judged on the blade's real (turning) motion.
    def Pw(s): return _yaw(float(spin(s)))@P(s)
    def Dw(s): return _yaw(float(spin(s)))@D(s)
    if 'planes' not in _TURN_CACHE:
        w0, w1 = planes(Pw, Dw, (.64, .8))
        _TURN_CACHE['planes'] = [_yaw(-float(spin(s)))@w for s, w in ((.64, w0), (.8, w1))]
    cut0, cut1 = _TURN_CACHE['planes']
    rest_b = ncurve([(0, GUARD_N), (.25, cut0), (.62, cut0), (.8, cut1, 'hit'), (2.2, cut1), (2.6, GUARD_N)])
    lead = curve([(0, 0), (.65, 0), (.68, 1, 'linear'), (.85, 1), (.92, 0)])
    spec.update(edge_lead(Pw, Dw, lambda s: _yaw(float(spin(s)))@rest_b(s), lead, t))
    spec.update({'feet': feet, 'foot_r': foot_r, 'extra': cloth(t, swing=float(swing(t)), lift=float(lift(t)))})
    return spec


_TURN_CACHE = {}


# ---------------------------------------------------------------- Cast, wait, Reel
LAND = np.array([.35, .55, -11.5])          # where the thrown sword drives into the floor
LAND_D = unit((.05, -.75, -.66))
RELEASE, STICK = .87, 1.22                  # the sword leaves his hand, and drives in
RELEASE_FIST = np.array([.4, 4.2, -2.3])   # his right fist at release: high, in front of his face
# The flight is a quadratic arc that leaves along the blade and arrives along LAND_D, so the
# point leads all the way and the sword sticks at the angle it flies in.
ARC = LAND-4.6*LAND_D
RELEASE_D = unit(ARC-RELEASE_FIST)
for _ in range(3): RELEASE_D = unit(ARC-(RELEASE_FIST-GRIP['right']*RELEASE_D))
RELEASE_P = RELEASE_FIST-GRIP['right']*RELEASE_D


def flight(t):
    """Pommel and blade direction in flight, RELEASE <= t <= STICK."""
    s = (t-RELEASE)/(STICK-RELEASE)
    p = (1-s)**2*RELEASE_P+2*(1-s)*s*ARC+s*s*LAND
    return p, unit((1-s)*(ARC-RELEASE_P)+s*(LAND-ARC))


@easy_wrists(4.7)
def cast_reel(t):
    """Throw (0.8 s tell), the sword sticks, a 1.5 s unarmed wait, the Reel tell (0.7 s),
    the sword rips back along a bowed path, he catches it two-handed and recovers.
    The throw is an overhand knife or axe throw: he steps his right foot back and sinks onto
    it, coils away and leans back, lays the blade back over his shoulder with the point up and
    behind him, and reaches at you with his left hand. Then the hips open, the left foot
    strides in, and the arm whips over the shoulder, releasing as the point comes onto you."""
    # The right fist and the blade while held: raised, laid back, held, then the whip.
    fist = curve([(0, GUARD_P+GRIP['right']*GUARD_D), (.22, (.85, 3.25, -.55)),
                  (.55, (.86, 4.1, .3)), (.74, (.9, 4.0, .45)),                     # laid back, tremble
                  (.8, (.78, 4.55, -.45), 'in'), (.85, (.5, 4.4, -1.8), 'linear'),   # over the shoulder
                  (RELEASE, RELEASE_FIST, 'linear')])
    held = dcurve([(0, GUARD_D), (.22, (.3, .95, .05)), (.55, (.55, .5, .67)), (.74, (.58, .4, .71)),
                   (.8, (.06, .98, .1), 'in'), (.85, (-.02, .6, -.8), 'linear'), (RELEASE, RELEASE_D, 'linear')])
    # After the stick: the Reel, as before - the sword rips back along a bowed path.
    reel = curve([(3.5, LAND), (3.62, (2.1, 1.6, -6.4), 'out'), (3.76, (.25, 2.55, -1.15), 'in'),
                  (4.0, (.2, 2.3, -.95), 'snap'), (4.7, GUARD_P)])
    reel_d = dcurve([(3.5, LAND_D), (3.62, (.6, .2, .78), 'out'), (3.76, (.35, .55, -.75), 'in'),
                     (4.0, (.45, .55, -.7), 'snap'), (4.7, GUARD_D)])

    def sword(s):
        if s <= RELEASE: d = held(s); return fist(s)-GRIP['right']*d, d
        if s <= STICK: return flight(s)
        if s <= 3.5: return LAND, LAND_D
        return reel(s), reel_d(s)
    P, D = (lambda s: sword(s)[0]), (lambda s: sword(s)[1])
    hands = 'both' if t < .22 else 'right' if t < RELEASE else 'none' if t < 3.76 else 'right' if t < 3.86 else 'both'
    # Free hands: the left reaches at you, then tucks to his hip as he throws; the right follows
    # through down across his body. Then both haul on the chain, as before.
    grip22 = P(.22)+GRIP['left']*D(.22)
    left = curve([(.22, grip22), (.4, (-.3, 3.3, -1.3)), (.55, (-.45, 3.5, -1.6)), (.74, (-.42, 3.55, -1.66)),
                  (.8, (-.55, 3.3, -1.35), 'in'), (RELEASE, (-.9, 3.0, -1.1)), (.97, (-1.1, 2.7, -.7), 'snap'),
                  (1.4, (-.75, 2.5, -.65)), (2.8, (-.6, 2.5, -.95)),
                  (3.15, (.05, 2.45, -.9)), (3.5, (.0, 2.4, -.75)), (3.7, (.05, 2.5, -.9)),
                  (3.86, (-.05, 2.55, -1.0))])
    right = curve([(RELEASE, RELEASE_FIST), (.95, (-.05, 3.25, -1.95), 'snap'), (1.15, (-.2, 2.6, -1.35)),
                   (1.6, (.3, 2.45, -1.0)), (2.8, (.6, 2.4, -.85)),
                   (3.15, (.32, 2.35, -.8)), (3.5, (.38, 2.3, -.62)), (3.7, (.3, 2.45, -.95))])
    # Coiled away and leaning back, then the hips open before the chest whips through.
    torso = curve([(0, (0, 0, 0)), (.22, (-4, 12, 2)), (.55, (-12, 26, 6)), (.74, (-14, 30, 7)),
                   (.8, (0, 10, 2), 'in'), (RELEASE, (16, -18, -4), 'out'), (1.0, (20, -26, -5), 'snap'),
                   (1.4, (10, -12, -2)),
                   (2.8, (4, -4, 0)), (3.15, (-6, 6, 0)), (3.5, (-10, 8, 0)),
                   (3.76, (6, -6, 0), 'hit'), (4.0, (4, 0, 0)), (4.7, (0, 0, 0))])
    pelvis_rot = curve([(0, (0, 0, 0)), (.55, (0, 14, 0)), (.68, (0, 16, 0)), (.78, (0, -4, 0)),
                        (.88, (0, -14, 0), 'snap'), (2.8, (0, -6, 0)), (3.5, (0, 4, 0)), (4.7, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.22, (.03, 0, .06)), (.55, (.1, -.08, .24)), (.68, (.1, -.1, .26)),
                   (.8, (.02, -.14, -.05), 'in'), (RELEASE, (-.04, -.16, -.28), 'out'), (1.0, (-.06, -.2, -.36), 'snap'),
                   (1.4, (-.04, -.1, -.24)), (2.8, (-.02, -.06, -.16)), (3.5, (0, -.12, .05)),
                   (3.76, (0, -.06, -.16), 'hit'), (4.7, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.55, (8, -30, -4)), (.74, (9, -34, -5)), (.8, (2, -10, 0), 'in'), (RELEASE, (-4, 12, 3)),
                  (1.0, (-6, 18, 3)), (1.4, (0, 8, 0)), (2.8, (2, 4, 0)), (3.5, (-2, -4, 0)), (4.0, (0, 0, 0))])
    swing = curve([(0, 0), (.74, -.5), (.88, 1, 'hit'), (1.6, .1), (3.5, -.5), (3.76, .8, 'hit'), (4.4, 0)])
    # Feet: the right steps back and takes his weight; the left strides in as he throws, and
    # both return to the guard after the catch.
    back = curve([(0, (0, 0, 0)), (.12, (0, 0, 0)), (.38, (.08, 0, .4)), (4.0, (.08, 0, .4)), (4.32, (0, 0, 0))])
    back_lift = curve([(0, 0), (.12, 0), (.25, .14, 'out'), (.38, 0, 'in'), (4.0, 0), (4.16, .12, 'out'), (4.32, 0, 'in')])
    stride = curve([(0, 0), (.68, 0), (.8, -.6, 'hit'), (3.4, -.6), (3.55, -.38), (4.34, -.38), (4.66, 0)])
    stride_lift = curve([(0, 0), (.68, 0), (.74, .22, 'out'), (.8, 0, 'in'), (4.34, 0), (4.5, .1, 'out'), (4.66, 0, 'in')])
    lead = curve([(0, 0), (.74, 0), (.75, 1, 'linear'), (RELEASE, 1), (RELEASE+.01, 0, 'linear')])
    whip, = planes(P, D, (.8,))
    rest = ncurve([(0, GUARD_N), (.55, whip), (3.5, whip), (3.62, GUARD_N)])
    b = back(t)
    spec = {'pelvis_pos': shift(t), 'pelvis_rot': pelvis_rot(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': P(t), 'sword_d': D(t), **edge_lead(P, D, rest, lead, t), 'hands': hands,
            'free_hands': {'left': left(t), 'right': right(t)},
            'feet': planted(dx=(b[0], float(back_lift(t)), b[2]), sx=(0, float(stride_lift(t)), float(stride(t)))),
            'extra': cloth(t, swing=float(swing(t)))}
    if RELEASE <= t < 3.86: spec['hidden'] = ['hanging_wrist_chain']
    return spec


# ---------------------------------------------------------------- chain moves: shared pieces
# Every chain move ends on the Reel's catch: the sword whips in to CATCH, his right fist takes
# it, the left follows 0.1 s later, and it settles through SETTLE into the guard.
CATCH_P, CATCH_D = np.array([.25, 2.55, -1.15]), unit((.35, .55, -.75))
SETTLE_P, SETTLE_D = np.array([.2, 2.3, -.95]), unit((.45, .55, -.7))
CATCH_AZ = math.degrees(math.atan2(-CATCH_P[0], -CATCH_P[2]))     # where the catch is round him
CATCH_R = float(np.hypot(CATCH_P[0], CATCH_P[2]))
UP = np.array([0., 1., 0.])


def _az(a):
    """The level direction at azimuth a degrees: 0 is his front, + turns to his left."""
    return np.array([-math.sin(a*RAD), 0., -math.cos(a*RAD)])


def _ramp(t, a, b):
    """0 before a, 1 after b, eased between."""
    return SHAPES['ease'](min(1., max(0., (t-a)/(b-a))))


def _placed(spec, psi=0., offset=(0, 0, 0)):
    """A pose authored in his own frame (pelvis, sword, free hands, feet), turned psi degrees
    to his left about his origin and then moved by offset (blocks)."""
    R = _yaw(psi); o = np.asarray(offset, float); out = dict(spec)
    out['pelvis_pos'] = R@(PELVIS_PIVOT+np.asarray(spec.get('pelvis_pos', (0, 0, 0)), float))-PELVIS_PIVOT+o
    x, y, z = spec.get('pelvis_rot', (0, 0, 0)); assert psi == 0 or abs(z) < 1e-9   # the yaw must compose exactly
    out['pelvis_rot'] = (x, y-psi, z)
    if 'sword_p' in spec: out['sword_p'] = R@np.asarray(spec['sword_p'], float)+o
    for k in ('sword_d', 'sword_n'):
        if k in spec: out[k] = R@np.asarray(spec[k], float)
    if 'free_elbow' in spec: out['free_elbow'] = {s: R@np.asarray(v, float) for s, v in spec['free_elbow'].items()}
    for k in ('free_hands', 'feet'):
        if k in spec: out[k] = {s: R@np.asarray(v, float)+o for s, v in spec[k].items()}
    if 'foot_r' in spec: out['foot_r'] = {s: R@np.asarray(v, float) for s, v in spec['foot_r'].items()}
    return out


def _pivot_feet(gather=0., tip=0., lift=(0., 0.), local=None):
    """Feet in his own frame (pass the result through _placed): his stance, or `local` ankles,
    drawn in under him by gather (0-1), each raised by its lift (right, left), and up on
    their balls by tip degrees, rolling about the toe's edge."""
    feet, foot_r = {}, {}
    for side, up in zip(('right', 'left'), lift):
        home = ANKLE[side] if local is None else np.asarray(local[side], float)
        at = home+(home*np.array([.55, 1, .7])-home)*gather+np.array([0, up, 0])
        sole, heel, toe = SOLE[side]; R = rx(-tip*RAD); pivot = np.array([0, -sole, toe])
        feet[side] = at+pivot-R@pivot; foot_r[side] = R
    return feet, foot_r


def _hands(t, let_go, release, catch):
    """Which fists grip: both; the right alone from let_go; none from release until the catch,
    when the right takes the sword and the left follows 0.1 s later."""
    return 'both' if t < let_go else 'right' if t < release else 'none' if t < catch else 'right' if t < catch+.1 else 'both'


# ---------------------------------------------------------------- Chain Scythe
# The sword whirls round him on the chain, its point SCYTHE_REACH blocks out.
# Low: he casts the sword out along the floor, drops into a crouch and spins once, dragging it
# round at ankle height like a hammer thrower. High: he stands tall and whirls it over his
# head like a lasso, the blade at a player's head height. Both then haul it in and catch it.
SCYTHE_REACH = 14.
SCYTHE_LAG = 25.         # deg the sword trails behind its chain's line at full swing, as the bowed chain's end does


@easy_wrists(2.9)
def chain_scythe_low(t):
    """0.9 s tell: he casts the sword out low to his right (the chain snaps taut at 0.45 s),
    sinks into a crouch and winds up against it. Then he spins once to his left, the blade
    flat on the floor at full chain (the clang at 0.9 s, mid-spin). He stops, hauls the chain,
    and the sword whips round his right side into his hand."""
    tr, tc, R = .3, 1.5, SCYTHE_REACH-SWORD_TIP*math.cos(SCYTHE_LAG*RAD)
    spin = curve([(0, 0), (.3, 0), (.62, -28), (.72, 10, 'in'), (1.06, 320, 'linear'), (1.2, 362, 'out'), (1.4, 360)])
    psi = float(spin(t))
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.12, (.85, 2.3, -.6)), (tr, (1.25, 1.9, .1))])
    held = ncurve([(0, GUARD_D), (.12, (.75, .2, -.6)), (tr, (.95, -.3, .1))])
    P0 = fist(tr)-GRIP['right']*held(tr)
    # After the release, in his own frame: where the pommel is round him, how far out, how high.
    az = curve([(tr, math.degrees(math.atan2(-P0[0], -P0[2]))), (.45, -108), (.62, -100), (.72, -118), (1.06, -128),
                (1.2, -80), (tc, CATCH_AZ)])
    r = curve([(tr, float(np.hypot(P0[0], P0[2]))), (.45, R, 'linear'), (1.06, R), (tc, CATCH_R)])
    y = curve([(tr, P0[1]), (.4, .24, 'in'), (1.1, .24), (tc, CATCH_P[1])])
    lag = curve([(tr, 0), (.45, 0), (.72, SCYTHE_LAG), (1.06, SCYTHE_LAG), (1.3, 0)])
    tail_p = curve([(tc, CATCH_P), (tc+.24, SETTLE_P, 'snap'), (2.9, GUARD_P)])
    tail_d = dcurve([(tc, CATCH_D), (tc+.24, SETTLE_D, 'snap'), (2.9, GUARD_D)])

    def P(s):
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        if s >= tc: return tail_p(s)
        return float(r(s))*_az(float(az(s)))+np.array([0, float(y(s)), 0])

    def D(s):
        if s <= tr: return held(s)
        if s >= tc: return tail_d(s)
        d = slerp(held(tr), _az(float(az(s))-float(lag(s))), _ramp(s, tr, tr+.1))   # flat on the floor, point out
        return slerp(d, CATCH_D, _ramp(s, tc-.2, tc))

    def N(s):
        n = slerp(_turned_n(held(min(s, tr))), UP, _ramp(s, tr, tr+.12))
        return slerp(n, GUARD_N, _ramp(s, tc-.2, tc))
    left = curve([(.12, P(.12)+GRIP['left']*D(.12)), (.3, (-.9, 2.6, -.6)), (.62, (-1.1, 2.1, -.5)), (.9, (-1.5, 2.6, -.1)),
                  (1.2, (-1.1, 2.6, -.5)), (tc, (-.3, 2.6, -.9)), (tc+.1, (-.05, 2.55, -1.0))])
    right = curve([(tr, fist(tr)), (.45, (1.5, 1.75, .4)), (.62, (1.45, 1.3, .65)), (1.06, (1.45, 1.35, .7)),
                   (1.2, (1.3, 1.7, .2)), (1.36, (.8, 2.4, -.7)), (tc-.06, (.3, 2.45, -.95))])
    shift = curve([(0, (0, 0, 0)), (.3, (.05, -.1, .05)), (.62, (.1, -.5, .1)), (1.06, (.05, -.45, 0)), (1.25, (0, -.25, -.05)),
                   (tc, (0, -.06, -.16)), (tc+.24, (0, -.05, -.1)), (2.9, (0, 0, 0))])
    torso = curve([(0, (0, 0, 0)), (.3, (8, 20, 0)), (.62, (22, 38, 0)), (.76, (20, 8, 0), 'in'), (1.06, (18, -12, 0)),
                   (1.25, (10, -8, 0)), (tc, (6, -6, 0), 'hit'), (tc+.24, (4, 0, 0)), (2.9, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.62, (6, 0, 0)), (1.06, (6, 0, 0)), (1.3, (2, 0, 0)), (2.9, (0, 0, 0))])
    # He keeps his eyes on you through the wind-up, then looks into the spin.
    look = float(curve([(0, 0), (.3, -30), (.62, -45), (.72, 30, 'in'), (1.06, 35), (1.25, 0)])(t))
    gather = curve([(0, 0), (.3, 0), (.6, .8), (1.1, .8), (1.38, 0)])
    tip = curve([(0, 0), (.58, 0), (.7, 14, 'out'), (1.1, 14), (1.25, 0)])
    step_r = curve([(0, 0), (.4, 0), (.5, .08, 'out'), (.6, 0, 'in')])
    step_l = curve([(0, 0), (1.2, 0), (1.29, .08, 'out'), (1.38, 0, 'in')])
    feet, foot_r = _pivot_feet(float(gather(t)), float(tip(t)), (float(step_r(t)), float(step_l(t))))
    swing = curve([(0, 0), (.62, -.3), (.9, .8), (1.3, .3), (2., 0)])
    flare = curve([(0, 0), (.62, 0), (.8, 1.6), (1.1, 1.4), (1.5, 0)])
    spec = _placed({'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t),
                    'neck_rot': (0, -.4*look, 0), 'head_rot': (3, -.6*look, 0),
                    'sword_p': P(t), 'sword_d': D(t), 'sword_n': N(t),
                    'free_hands': {'left': left(t), 'right': right(t)}, 'free_elbow': {'left': (-.6, -.2, 1)}, 'feet': feet, 'foot_r': foot_r}, psi)
    spec.update({'hands': _hands(t, .12, tr, tc), 'extra': cloth(t, swing=float(swing(t)), lift=float(flare(t)))})
    spec['hinge'] = True
    if tr <= t < tc+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


@easy_wrists(2.9)
def chain_scythe_high(t):
    """0.9 s tell: he swings the sword up behind his right shoulder and lets it out into a
    whirl over his head (the chain snaps out at 0.45 s). One turn at full chain follows, the
    blade at a player's head height (the clang at 0.9 s, mid-turn). He hauls it down and in,
    and it whips round into his hand."""
    tr, tc, top = .3, 1.5, 5.15
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.1, (.9, 2.7, -.5)), (.2, (1.0, 3.7, -.2), 'linear'), (tr, (.95, 4.6, .25), 'out')])
    held = ncurve([(0, GUARD_D), (.1, (.8, .45, -.4)), (.2, (.7, .6, .2), 'linear'), (tr, (.45, .55, .7), 'out')])
    P0 = fist(tr)-GRIP['right']*held(tr)
    c = curve([(tr, (.75, 0, 0)), (1.1, (.75, 0, 0)), (tc, (0, 0, 0))])                # the whirl's axis
    az = curve([(tr, -147), (.45, -70, 'in'), (.7, 155, 'linear'), (1.1, 515, 'linear'), (tc, 720+CATCH_AZ, 'out')])
    level = 2.6                                          # the pommel at full chain; the point hangs lower
    lag = curve([(tr, 10), (.45, 10), (.7, SCYTHE_LAG), (1.1, SCYTHE_LAG), (1.3, 10)])
    slope = math.atan2(top-level, 9.6); R = SCYTHE_REACH-SWORD_TIP*math.cos(slope)
    rho = curve([(tr, -.3), (.45, 2.5, 'in'), (.7, R, 'linear'), (1.1, R), (tc, CATCH_R)])
    y = curve([(tr, P0[1]), (.45, top+.15), (.7, level), (1.1, level), (1.3, 3.0), (tc, CATCH_P[1])])
    tail_p = curve([(tc, CATCH_P), (tc+.24, SETTLE_P, 'snap'), (2.9, GUARD_P)])
    tail_d = dcurve([(tc, CATCH_D), (tc+.24, SETTLE_D, 'snap'), (2.9, GUARD_D)])

    def polar(s): return c(s)+float(rho(s))*_az(float(az(s)))+np.array([0, float(y(s)), 0])
    slip = P0-polar(tr)

    def P(s):
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        if s >= tc: return tail_p(s)
        return polar(s)+slip*(1-_ramp(s, tr, tr+.1))

    def D(s):
        if s <= tr: return held(s)
        if s >= tc: return tail_d(s)
        e = math.atan2(float(y(s))-top, max(float(rho(s)), 1.5))            # the chain's line, carried on
        line = math.cos(e)*_az(float(az(s))-float(lag(s)))+math.sin(e)*UP
        return slerp(slerp(held(tr), line, _ramp(s, tr, .45)), CATCH_D, _ramp(s, tc-.2, tc))

    def N(s):
        n = slerp(_turned_n(held(min(s, tr))), UP, _ramp(s, tr, tr+.12))
        return slerp(n, GUARD_N, _ramp(s, tc-.2, tc))
    whirl = _ramp(t, tr, .45)*(1-_ramp(t, 1.1, 1.2))
    hand = curve([(tr, fist(tr)), (.42, (.75, top, 0)), (1.1, (.75, top, 0)), (1.28, (.6, 3.7, -1.3), 'in'),
                  (tc-.06, (.3, 2.45, -.95))])
    left = curve([(.1, P(.1)+GRIP['left']*D(.1)), (.4, (-1.2, 3.0, -.4)), (1.1, (-1.3, 2.9, -.3)), (1.3, (-.9, 2.8, -.7)),
                  (tc, (-.3, 2.6, -.9)), (tc+.1, (-.05, 2.55, -1.0))])
    a = float(az(t))
    shift = curve([(0, (0, 0, 0)), (.3, (0, 0, .05)), (.7, (0, .03, 0)), (1.1, (0, .03, 0)), (1.3, (0, -.15, -.1)),
                   (tc, (0, -.06, -.16)), (tc+.24, (0, -.05, -.1)), (2.9, (0, 0, 0))])
    torso = curve([(0, (0, 0, 0)), (.15, (4, 14, 0)), (.3, (-6, 10, 0)), (1.1, (-6, 0, 0)), (1.3, (12, -6, 0)),
                   (tc, (6, -6, 0), 'hit'), (tc+.24, (4, 0, 0)), (2.9, (0, 0, 0))])
    brace = curve([(0, 0), (.08, 0), (.28, 1), (2.3, 1), (2.6, 0)])
    lift_r = curve([(0, 0), (.08, 0), (.18, .1, 'out'), (.28, 0, 'in'), (2.3, 0), (2.45, .08, 'out'), (2.6, 0, 'in')])
    b = float(brace(t))
    swing = curve([(0, 0), (.3, -.3), (1.1, 0), (1.3, .6), (2., 0)])
    spec = {'pelvis_pos': shift(t)-.06*whirl*_az(a), 'torso_rot': torso(t)+np.array([0, 5*whirl*math.sin(a*RAD), 0]),
            'neck_rot': (4*whirl, 0, 0), 'head_rot': (6*whirl, 0, 0),
            'sword_p': P(t), 'sword_d': D(t), 'sword_n': N(t), 'hands': _hands(t, .1, tr, tc),
            'free_hands': {'left': left(t), 'right': hand(t)+.2*whirl*_az(a+25)}, 'free_elbow': {'left': (-.6, -.2, 1)},
            'feet': planted(dx=(.1*b, float(lift_r(t)), .22*b), sx=(-.06*b, 0, -.08*b)),
            'extra': cloth(t, swing=float(swing(t)), lift=.5*whirl)}
    spec['hinge'] = True
    if tr <= t < tc+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


# ---------------------------------------------------------------- the overhead cut
_CACHE = {}
# One vertical cut, shared by the Overhead Sentence and by phase two's third Royal Cut: the
# blade high over his head, pointing up and back, then slammed down in front of him as his
# left foot lunges in and he drops into the blow, the point on the floor about 5.5 blocks out.
# Both ends were searched for comfortable wrists.
HIGH_FIST, HIGH_D = np.array([.45, 5.1, -.1]), unit((.2, .39, .9))
SLAM_FIST, SLAM_D = np.array([-.4, 1.0, -1.75]), unit((.24, -.23, -.94))
CHOP = .14                                  # s from the blade high to the blade down


def _chop(a, rest, end):
    """Key lists for the overhead cut: the blade is high at a, down by a+CHOP, lifted a little
    and held until rest, and back in the guard at end."""
    b, home = a+CHOP, GUARD_P+GRIP['right']*GUARD_D
    up = np.array([0, .15, 0]); a1, a2, a3 = a+CHOP/3, a+CHOP*2/3, a+CHOP*5/6
    return {'fist': [(a, HIGH_FIST), (a1, (.3, 4.65, -1.15), 'in'), (a2, (0, 3.35, -2.15), 'linear'),
                     (a3, (-.3, 2.0, -1.95), 'linear'), (b, SLAM_FIST, 'linear'),
                     (b+.5, SLAM_FIST+up), (rest, SLAM_FIST+up), (end, home)],
            'D': [(a, HIGH_D), (a1, (.16, .98, .15), 'in'), (a2, (.07, .71, -.7), 'linear'),
                  (a3, (.27, .51, -.82), 'linear'), (b, SLAM_D, 'linear'),   # the fists lead, the blade whips down last
                  (rest, SLAM_D), (end, GUARD_D)],
            'torso': [(a, (-10, 6, 0)), (b, (30, 0, 0), 'hit'), (rest, (30, 0, 0)), (end, (0, 0, 0))],
            'pelvis': [(a, (-2, 4, 0)), (b, (10, 0, 0), 'hit'), (rest, (10, 0, 0)), (end, (0, 0, 0))],
            'shift': [(a, (0, .04, .1)), (b, (0, -.9, -.5), 'hit'), (b+.5, (0, -.75, -.5)), (rest, (0, -.75, -.5)),
                      (end, (0, 0, 0))],
            'head': [(a, (-6, 0, 0)), (b, (-14, 0, 0)), (rest, (-10, 0, 0)), (end, (0, 0, 0))],
            'lunge': [(a, 0), (a+.02, 0), (b, 1, 'hit'), (rest, 1), (end-.05, 0)],
            'lunge_lift': [(a, 0), (a+.02, 0), (a+.07, .2, 'out'), (b, 0, 'in'), (rest, 0), ((rest+end)/2, .12, 'out'), (end-.05, 0, 'in')],
            'swing': [(a, -.4), (b, 1, 'hit'), (b+.5, .3), (rest, 0)]}


@easy_wrists(2.6)
def overhead_sentence(t):
    """The cut that follows a Hook. 0.7 s tell: he raises the sword high over his head, his
    right foot stepping back. Then his left foot lunges in and the blade comes straight down
    the line in front of him, the edge leading, and bites the floor. A 1.2 s pause follows."""
    a, rest, end = .7, 2.2, 2.6
    home = GUARD_P+GRIP['right']*GUARD_D; k = _chop(a, rest, end)
    fist = curve([(0, home), (.3, (.5, 4.6, -.3), 'out')]+k['fist'])
    D = ncurve([(0, GUARD_D), (.3, (.3, .75, .6), 'out')]+k['D'])
    torso = curve([(0, (0, 0, 0)), (.3, (-6, 8, 0), 'out')]+k['torso'])
    pelvis = curve([(0, (0, 0, 0))]+k['pelvis'])
    shift = curve([(0, (0, 0, 0)), (.3, (0, .02, .08), 'out')]+k['shift'])
    head = curve([(0, (0, 0, 0))]+k['head'])
    lunge, lunge_lift = curve([(0, 0)]+k['lunge']), curve([(0, 0)]+k['lunge_lift'])
    back = curve([(0, 0), (.05, 0), (.3, 1), (rest, 1), (end-.05, 0)])
    back_lift = curve([(0, 0), (.05, 0), (.17, .12, 'out'), (.3, 0, 'in'), (rest, 0), ((rest+end)/2, .1, 'out'), (end-.05, 0, 'in')])
    swing = curve([(0, 0), (.3, -.3)]+k['swing'])

    def P(s): return fist(s)-GRIP['right']*D(s)
    chop0, chop1 = planes(P, D, (a+CHOP/3, a+CHOP*5/6))
    rest_n = ncurve([(0, GUARD_N), (.3, chop0), (a, chop0), (a+CHOP, chop1, 'hit'), (rest, chop1), (end, GUARD_N)])
    lead = curve([(0, 0), (a, 0), (a+.03, 1, 'linear'), (a+CHOP+.04, 1), (a+CHOP+.13, 0)])
    bk = float(back(t))
    return {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': P(t), 'sword_d': D(t), **edge_lead(P, D, rest_n, lead, t),
            'feet': planted(dx=(.06*bk, float(back_lift(t)), .3*bk), sx=(0, float(lunge_lift(t)), -1.*float(lunge(t)))),
            'extra': cloth(t, swing=float(swing(t)))}


@easy_wrists(4.4)
def royal_cut_three(t):
    """Phase two's Royal Cut: the same two cuts, then a third for anyone who dodged them. From
    the backhand's follow-through the blade circles up over his right shoulder (0.45 s tell)
    and comes down in the overhead cut, which leaves the fire line."""
    if t <= 2.2: return royal_cut.pose(t)
    if 'cut22' not in _CACHE: _CACHE['cut22'] = royal_cut.pose(2.2)
    base = _CACHE['cut22']; a, rest, end = 2.62, 3.95, 4.4
    k = _chop(a, rest, end)
    fist = curve([(2.2, base['sword_p']+GRIP['right']*base['sword_d']), (2.42, (1.2, 4.0, -.1))]+k['fist'])
    D = ncurve([(2.2, base['sword_d']), (2.42, (.3, .44, .85))]+k['D'])
    torso = curve([(2.2, base['torso_rot']), (2.42, (0, 20, 2))]+k['torso'])
    pelvis = curve([(2.2, base['pelvis_rot'])]+k['pelvis'])
    shift = curve([(2.2, base['pelvis_pos'])]+k['shift'])
    head = curve([(2.2, base['head_rot'])]+k['head'])
    step = base['feet']['left']-ANKLE['left']                    # the first cut's step, still planted
    lunge, lunge_lift = curve([(2.2, 0)]+k['lunge']), curve([(2.2, 0)]+k['lunge_lift'])
    swing = curve([(2.2, .6)]+k['swing'])

    def P(s): return fist(s)-GRIP['right']*D(s)
    chop0, chop1 = planes(P, D, (a+CHOP/3, a+CHOP*5/6))
    n22 = unit(base['sword_n'])
    if np.dot(chop0, n22) < 0: chop0, chop1 = -chop0, -chop1
    rest_n = ncurve([(2.2, n22), (2.42, chop0), (a, chop0), (a+CHOP, chop1, 'hit'), (rest, chop1), (end, GUARD_N)])
    lead = curve([(2.2, 0), (a, 0), (a+.03, 1, 'linear'), (a+CHOP+.04, 1), (a+CHOP+.13, 0)])
    f = float(lunge(t)); z = step[2]+(-1.-step[2])*f if t < rest else -1.*f
    return {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'head_rot': head(t),
            'sword_p': P(t), 'sword_d': D(t), **edge_lead(P, D, rest_n, lead, t),
            'feet': planted(sx=(step[0]*(1-f) if t < rest else 0, float(lunge_lift(t)), z)),
            'extra': cloth(t, swing=float(swing(t)))}


# ---------------------------------------------------------------- Drag Down
DRAG_TARGET = np.array([0., 8.5, -7.5])     # a player on a pillar top or hovering


@easy_wrists(2.4)
def drag_down(t):
    """0.8 s tell: he looks up, points at you with his left hand and swings the sword back
    low behind his right hip. A rising sidearm sling sends it up at you on the chain. As it
    strikes he takes the chain in both hands and hauls down with his whole weight; the sword
    slams to the floor in front of him and whips back into his hand."""
    tr, th, tc, end = .8, 1.0, 1.5, 2.4
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.15, (1.0, 2.3, -.2)), (.45, (1.25, 1.95, .55), 'out'), (.6, (1.27, 1.9, .6)),
                  (.68, (1.05, 2.35, -.25), 'in'), (.74, (.75, 3.0, -.35), 'linear'), (tr, (.25, 4.35, -.85), 'linear')])
    held = ncurve([(0, GUARD_D), (.15, (.8, .4, -.2)), (.45, (.59, -.08, .8), 'out'), (.6, (.59, -.08, .8)),
                   (.68, (.9, .42, .09), 'in'), (.74, (.69, .63, -.37), 'linear'), (tr, (.34, .66, -.66), 'linear')])
    P0 = fist(tr)-GRIP['right']*held(tr); aim = unit(DRAG_TARGET-P0); hit = DRAG_TARGET-SWORD_TIP*aim
    back = curve([(th+.08, hit), (1.3, (.2, .8, -4.2), 'in'), (tc, CATCH_P)])
    back_d = dcurve([(th+.08, aim), (1.3, (0, -.6, -.8), 'in'), (tc, CATCH_D)])
    tail_p = curve([(tc, CATCH_P), (tc+.24, SETTLE_P, 'snap'), (end, GUARD_P)])
    tail_d = dcurve([(tc, CATCH_D), (tc+.24, SETTLE_D, 'snap'), (end, GUARD_D)])

    def P(s):
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        if s <= th: return P0+(hit-P0)*(s-tr)/(th-tr)
        return back(s) if s < tc else tail_p(s)

    def D(s):
        if s <= tr: return held(s)
        if s <= th: return slerp(held(tr), aim, _ramp(s, tr, tr+.08))
        return back_d(s) if s < tc else tail_d(s)

    def N(s):
        n = _turned_n(held(min(s, tr)))
        return slerp(n, GUARD_N, _ramp(s, tc-.2, tc))
    left = curve([(.15, P(.15)+GRIP['left']*D(.15)), (.45, (-.5, 3.9, -1.1), 'out'), (.7, (-.5, 3.95, -1.1)), (tr, (-.8, 3.0, -.5)),
                  (th+.08, (.15, 4.6, -1.0)), (1.3, (.1, 2.0, -.9), 'hit'), (tc, (-.3, 2.6, -.9)), (tc+.1, (-.05, 2.55, -1.0))])
    right = curve([(tr, fist(tr)), (.9, (.3, 4.7, -1.1)), (th+.08, (.35, 4.9, -.9)), (1.3, (.4, 2.2, -.8), 'hit'),
                   (tc-.06, (.3, 2.45, -.95))])
    torso = curve([(0, (0, 0, 0)), (.15, (4, 15, 0)), (.45, (10, 35, 0), 'out'), (.6, (11, 37, 0)), (tr, (-12, -10, 0), 'hit'),
                   (th+.08, (-16, -12, 0)), (1.3, (26, -4, 0), 'hit'), (tc, (6, -6, 0)), (tc+.24, (4, 0, 0)), (end, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.45, (0, 15, 0), 'out'), (.6, (0, 15, 0)), (tr, (0, -5, 0), 'hit'), (1.3, (6, 0, 0)),
                    (tc, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.45, (0, -.3, .1), 'out'), (.6, (0, -.32, .1)), (tr, (0, .03, -.1), 'hit'),
                   (th+.08, (0, .04, -.1)), (1.3, (0, -.45, .05), 'hit'), (tc, (0, -.06, -.16)), (tc+.24, (0, -.05, -.1)),
                   (end, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.3, (-26, -14, 0)), (.6, (-28, -16, 0)), (tr, (-30, 6, 0)), (th+.08, (-30, 6, 0)),
                  (1.3, (8, 0, 0)), (tc+.24, (0, 0, 0))])
    step = curve([(0, 0), (.1, 0), (.35, 1), (2.0, 1), (2.3, 0)])
    step_lift = curve([(0, 0), (.1, 0), (.22, .12, 'out'), (.35, 0, 'in'), (2.0, 0), (2.15, .1, 'out'), (2.3, 0, 'in')])
    swing = curve([(0, 0), (.6, -.4), (tr, .6, 'hit'), (th+.08, -.3), (1.3, 1, 'hit'), (2., 0)])
    s = float(step(t))
    spec = {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'neck_rot': head(t)*.3, 'head_rot': head(t)*.7,
            'sword_p': P(t), 'sword_d': D(t), 'sword_n': N(t), 'hands': _hands(t, .15, tr, tc),
            'free_hands': {'left': left(t), 'right': right(t)}, 'free_elbow': {'left': (-.6, -.2, 1)},
            'feet': planted(dx=(.08*s, float(step_lift(t)), .28*s)), 'extra': cloth(t, swing=float(swing(t)))}
    spec['hinge'] = True
    if tr <= t < tc+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


# ---------------------------------------------------------------- Hook
HOOK_HIT = np.array([0., 1.6, -27.])        # the point strikes a player 27 blocks out


@easy_wrists(3.3)
def hook(t):
    """1.0 s tell: he draws the sword back beside his head like a javelin, the point on you
    and his left hand marking the line, and hurls it flat and hard. It strikes 27 blocks out;
    he drags it back hand over hand, three hauls in a second, then whips it up into his
    hand. The Overhead Sentence follows."""
    tr, th, tc, end = 1.0, 1.3, 2.5, 3.3
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.2, (.95, 2.95, -.05)), (.35, (1.15, 3.95, .5)), (.5, (1.25, 4.1, .45), 'out'), (.85, (1.27, 4.12, .5)), (tr, (.25, 3.7, -1.7), 'in')])
    held = ncurve([(0, GUARD_D), (.2, (.66, .55, -.52)), (.35, (.71, -.1, -.69)), (.5, (.1, -.2, -.97), 'out'), (.85, (.1, -.2, -.97)), (tr, (.03, -.08, -1), 'in')])
    P0 = fist(tr)-GRIP['right']*held(tr); aim = unit(HOOK_HIT-P0); hit = HOOK_HIT-SWORD_TIP*aim
    # The drag: three hauls bring it back along the floor, then the last flick to the catch.
    drag = curve([(th, hit), (1.42, hit+np.array([0, -.1, .2])), (1.62, (0, 1.4, -15.)), (1.72, (0, 1.4, -14.6)),
                  (1.95, (0, 1.3, -9.)), (2.05, (0, 1.3, -8.7)), (2.3, (.1, 1.2, -3.5)), (tc, CATCH_P, 'in')])
    drag_d = dcurve([(th, aim), (2.3, (0, -.03, -1)), (tc, CATCH_D, 'in')])
    tail_p = curve([(tc, CATCH_P), (tc+.24, SETTLE_P, 'snap'), (end, GUARD_P)])
    tail_d = dcurve([(tc, CATCH_D), (tc+.24, SETTLE_D, 'snap'), (end, GUARD_D)])

    def P(s):
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        if s <= th: return P0+(hit-P0)*(s-tr)/(th-tr)
        return drag(s) if s < tc else tail_p(s)

    def D(s):
        if s <= tr: return held(s)
        if s <= th: return slerp(held(tr), aim, _ramp(s, tr, tr+.06))
        return drag_d(s) if s < tc else tail_d(s)

    def N(s):
        n = _turned_n(held(min(s, tr)))
        return slerp(n, GUARD_N, _ramp(s, tc-.2, tc))
    far, near = (.25, 2.9, -1.75), (.5, 2.45, -.45)            # a hand reaching down the chain, and hauled in
    lfar, lnear = (-.15, 2.8, -1.7), (-.4, 2.4, -.5)
    left = curve([(.1, P(.1)+GRIP['left']*D(.1)), (.5, (-.35, 3.5, -1.6), 'out'), (.85, (-.36, 3.52, -1.62)), (tr, (-.7, 3.0, -.9)),
                  (th, (-.8, 2.7, -.7)), (1.42, lnear), (1.62, lfar), (1.84, lnear, 'hit'), (1.95, lfar), (2.17, lnear, 'hit'),
                  (2.3, lfar), (tc, (-.3, 2.6, -.9)), (tc+.1, (-.05, 2.55, -1.0))])
    right = curve([(tr, fist(tr)), (1.1, (.1, 3.3, -1.9), 'snap'), (th, far), (1.42, far), (1.62, near, 'hit'), (1.72, far),
                   (1.95, near, 'hit'), (2.05, far), (2.3, near, 'hit'), (tc-.06, (.3, 2.45, -.95))])
    pull = curve([(th, 0), (1.42, 0), (1.62, 1, 'hit'), (1.72, .2), (1.95, 1, 'hit'), (2.05, .2), (2.3, 1, 'hit'), (tc, 0)])
    torso = curve([(0, (0, 0, 0)), (.2, (-2, 16, 1)), (.5, (-4, 38, 2), 'out'), (.85, (-5, 41, 2)), (tr, (10, -20, 0), 'hit'),
                   (1.1, (14, -28, 0), 'snap'), (th, (10, -8, 0)), (tc, (6, -6, 0)), (tc+.24, (4, 0, 0)), (end, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.5, (0, 18, 0), 'out'), (.85, (0, 19, 0)), (tr, (0, -10, 0), 'hit'), (th, (0, -4, 0)),
                    (tc, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.5, (.06, -.06, .18), 'out'), (.85, (.07, -.08, .2)), (tr, (0, -.1, -.5), 'hit'),
                   (th, (0, -.12, -.4)), (tc, (0, -.06, -.3)), (tc+.24, (0, -.05, -.26)), (end, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.5, (3, -34, -2), 'out'), (.85, (3, -37, -2)), (tr, (-4, 14, 0), 'hit'), (th, (0, 4, 0)),
                  (tc, (0, 0, 0))])
    back = curve([(0, 0), (.1, 0), (.4, 1), (2.75, 1), (3.1, 0)])
    back_lift = curve([(0, 0), (.1, 0), (.25, .12, 'out'), (.4, 0, 'in'), (2.75, 0), (2.92, .1, 'out'), (3.1, 0, 'in')])
    stride = curve([(0, 0), (.86, 0), (tr, 1, 'hit'), (2.7, 1), (3.05, 0)])
    stride_lift = curve([(0, 0), (.86, 0), (.93, .2, 'out'), (tr, 0, 'in'), (2.7, 0), (2.88, .12, 'out'), (3.05, 0, 'in')])
    swing = curve([(0, 0), (.85, -.5), (tr, 1, 'hit'), (th, .2), (tc, .5, 'hit'), (3., 0)])
    b, f, pl = float(back(t)), float(stride(t)), float(pull(t))
    spec = {'pelvis_pos': shift(t)+np.array([0, -.08*pl, .22*pl]), 'pelvis_rot': pelvis(t),
            'torso_rot': torso(t)+np.array([-14*pl, 0, 0]), 'neck_rot': head(t)*.4, 'head_rot': head(t)*.6+np.array([8*pl, 0, 0]),
            'sword_p': P(t), 'sword_d': D(t), 'sword_n': N(t), 'hands': _hands(t, .1, tr, tc),
            'free_hands': {'left': left(t), 'right': right(t)}, 'free_elbow': {'left': (-.6, -.2, 1)},
            'feet': planted(dx=(.06*b, float(back_lift(t)), .3*b), sx=(0, float(stride_lift(t)), -.6*f)),
            'extra': cloth(t, swing=float(swing(t)))}
    spec['hinge'] = True
    if tr <= t < tc+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


# ---------------------------------------------------------------- Zip and Lunge
# Both end the same way: he flies to the stuck sword on the chain, lands in a crouch, rips it
# free with a haul on the chain, and takes it out of the air into one spinning cut round the
# spot he landed on (the ember ring), finishing across his front as the Turning Cut does.
# The clips move him: at the end he stands END_OFFSET blocks from where he started, and the
# game moves him there on the last frame. The flight is authored for one distance; the game
# will have to stretch it to the real one.
SPIN_P, SPIN_D = np.array([-.03, 1.65, -.33]), unit((.66, -.1, -.75))    # carried through the spin
ZIP_OFFSET = np.array([0., 0., -10.4])
LUNGE_LAND = np.array([.3, .55, -8.])
LUNGE_OFFSET = np.array([0., 0., -6.9])
# Where the chain has hauled him to at each moment, in the frame he started in. The game reads
# these (through the tracks build_art.py writes) to stretch each flight to the real distance.
TRAVEL = {'zip': curve([(0, (0, 0, 0)), (.62, (0, 0, 0)), (.98, ZIP_OFFSET+np.array([0, 0, .3]), 'linear'), (1.03, ZIP_OFFSET, 'out')]),
          'lunge': curve([(0, (0, 0, 0)), (.9, (0, 0, 0)), (1.28, LUNGE_OFFSET+np.array([0, 0, .3]), 'linear'), (1.33, LUNGE_OFFSET, 'out')])}


def _arrival(tl, hold, end, stuck):
    """Key lists, in his own frame, from the landing at tl: the sword (stuck, where it stands
    in his frame as he lands) torn free and caught at tl+0.17, the spin, the cut across his
    front, the low guard held until hold, and the guard at end."""
    tk = tl+.17
    return {'tk': tk,
            'P': [(tl, stuck), (tl+.09, (.6, 1.5, -.9)), (tk, SPIN_P), (tk+.3, SPIN_P), (tk+.36, (-.35, 1.6, -.3), 'linear'),
                  (tk+.42, (-.37, 1.65, -.05), 'linear'), (tk+.58, (-.53, 1.93, -.69), 'snap'), (tk+.82, (-.46, 1.93, -.29)),
                  (hold, (-.44, 1.91, -.3)), (end, GUARD_P)],
            'D': [(tl, LAND_D), (tl+.09, (.5, -.5, -.7)), (tk, SPIN_D), (tk+.3, SPIN_D), (tk+.36, (0, -.1, -.99), 'linear'),
                  (tk+.42, (-.39, .03, -.92), 'linear'), (tk+.58, (-.85, .53, .04), 'snap'), (tk+.82, (.34, -.17, -.93)),
                  (hold, (.34, -.18, -.92)), (end, GUARD_D)],
            'torso': [(tl, (25, 0, 0)), (tk, (1, -5, -.7)), (tk+.3, (1, -5, -.7)), (tk+.36, (8, -30, -4), 'hit'),
                      (tk+.58, (9, -40, -5), 'snap'), (tk+.82, (6, -18, -2)), (hold, (5, -14, -2)), (end, (0, 0, 0))],
            'pelvis': [(tl, (8, 0, 0)), (tk, (5, -13, 0)), (tk+.36, (6, -14, 0)), (tk+.82, (4, -8, 0)), (hold, (3, -6, 0)),
                       (end, (0, 0, 0))],
            'shift': [(tl, (0, -.45, 0)), (tk, (0, -.2, 0)), (tk+.36, (0, -.24, 0)), (tk+.82, (0, -.17, 0)), (hold, (0, -.15, 0)),
                      (end, (0, 0, 0))],
            'spin': [(tk-.02, 0), (tk+.06, 40, 'in'), (tk+.3, 300, 'linear'), (tk+.42, 362, 'out'), (tk+.6, 360)],
            'tip': [(tl, 0), (tk, 14), (tk+.42, 14), (tk+.55, 0)],
            'gather': [(tl, 0), (tk, .5), (tk+.42, .5), (tk+.7, 0)],
            'right': [(tl+.05, (.5, 2.2, -.9)), (tk-.06, (.5, 1.7, -.8))],
            'left': [(tl+.1, (-1.0, 1.9, -.7)), (tk+.1, (.19, 1.62, -.58))],
            'look': [(tl, 0), (tk, 30), (tk+.3, 35), (tk+.5, 0)],
            'flare': [(tl, .6), (tk+.2, 1.6), (tk+.5, 1.2), (tk+.9, 0)]}


def _arrive(t, k, pre, offset, stuck_world, stuck_d, tfree, spin=True):
    """The spec for a clip that ends on _arrival's keys k. pre holds each channel's keys before
    the landing; offset(t) is where he is; the sword stands at stuck_world until tfree. With
    spin False the keys are _landed's: he takes the sword and rises, with no cut."""
    tk = k['tk']
    def ch(name, make=curve): return make(pre[name]+k[name])
    psi = float(ch('spin')(t)); off = offset(t); look = float(ch('look')(t))
    feet, foot_r = _pivot_feet(float(ch('gather')(t)), float(ch('tip')(t)), local=pre['feet'](t))
    P, D = ch('P'), ch('D', ncurve)
    n = slerp(UP, _turned_n(D(t)), _ramp(t, tk+.44, tk+.82)) if spin else _turned_n(D(t))
    spec = _placed({'pelvis_pos': ch('shift')(t), 'pelvis_rot': ch('pelvis')(t), 'torso_rot': ch('torso')(t),
                    'neck_rot': (pre['head'](t)*.4)+np.array([0, -.4*look, 0]), 'head_rot': (pre['head'](t)*.6)+np.array([0, -.6*look, 0]),
                    'sword_p': P(t), 'sword_d': D(t), 'sword_n': n,
                    'free_hands': {'left': ch('left')(t)+pre['left_add'](t), 'right': ch('right')(t)}, 'free_elbow': {'left': (-.6, -.2, 1)},
                    'feet': feet, 'foot_r': foot_r}, psi, off)
    if t < tfree:                                 # the sword still stands where it stuck
        spec.update({'sword_p': stuck_world(t), 'sword_d': stuck_d(t), 'sword_n': _turned_n(stuck_d(t))})
    spec.update({'hands': pre['hands'](t) if t < tfree else 'none' if t < tk else 'right' if t < tk+.1 else 'both',
                 'extra': cloth(t, swing=float(pre['swing'](t)), lift=float(ch('flare')(t)))})
    spec['hinge'] = True
    if spin and tk+.04 <= t <= tk+.42: spec['edge_lock'] = 1.      # flat to the floor: an edge leads the whole spin
    if pre['thrown'](t) and t < tk+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


@easy_wrists(3.2)
def zip_in(t):
    """After a throw, with the sword stuck far off: he takes the chain in both hands and
    sinks back against it (the ember ring marks his landing from the start), then it hauls
    him off his feet. He flies to the sword, lands, tears it free and spins: the cut lands
    1.4 s in. A long recovery follows."""
    tl, hold, end = 1.03, 2.9, 3.2
    k = _arrival(tl, hold, end, LAND-ZIP_OFFSET)
    if 'wait' not in _CACHE: _CACHE['wait'] = cast_reel.pose(2.8)
    wait = _CACHE['wait']                         # the unarmed wait he starts from
    pre = {'P': [], 'D': [],
           'torso': [(0, wait['torso_rot']), (.5, (-4, 6, 0)), (.62, (35, 0, 0), 'in'), (.8, (50, 0, 0))],
           'pelvis': [(0, wait['pelvis_rot']), (.5, (0, 0, 0)), (.62, (15, 0, 0), 'in'), (.8, (20, 0, 0))],
           'shift': [(0, wait['pelvis_pos']), (.5, (0, -.5, .25)), (.62, (0, -.2, -.6), 'in'), (.78, (0, .5, 0), 'out')],
           'spin': [(0, 0)], 'tip': [(0, 0), (.56, 0), (.66, 35), (tl-.08, 35)], 'gather': [(0, 0)],
           'right': [(0, wait['free_hands']['right']), (.25, (.3, 2.7, -1.6)), (.5, (.4, 2.5, -.9)), (.62, (.35, 3.0, -1.7), 'in'),
                     (.98, (.4, 2.6, -1.5))],
           'left': [(0, wait['free_hands']['left']), (.25, (0, 2.5, -2.0)), (.5, (.1, 2.4, -1.3)), (.64, (-1.5, 2.5, -.6)), (.8, (-1.9, 2.7, -.3)),
                    (.98, (-1.8, 2.5, -.2))],
           'look': [(0, 0)], 'flare': [(0, 0), (.62, 0), (.8, 1.4)],
           'head': curve([(0, wait['head_rot']), (.5, (4, 0, 0)), (.62, (-18, 0, 0)), (.8, (-26, 0, 0)), (tl, (-8, 0, 0)), (tl+.17, (3, 0, 0))]),
           'swing': curve([(0, 0), (.5, -.4), (.7, 1, 'hit'), (tl, .6), (2., 0)]),
           'hands': lambda s: 'none', 'thrown': lambda s: True, 'left_add': lambda s: 0.}
    w = {s: wait['feet'][s] for s in ('right', 'left')}
    air = {'right': np.array([.45, 1.3, 1.2]), 'left': np.array([-.45, 1.6, .8])}
    lift = curve([(0, 0), (.62, 0), (.74, 1, 'out'), (tl-.1, 1), (tl-.02, 0, 'in')])
    pre['feet'] = lambda s: {side: (w[side] if s < .8 else ANKLE[side])*(1-float(lift(s)))+air[side]*float(lift(s))
                             for side in ('right', 'left')}
    return _arrive(t, k, pre, TRAVEL['zip'], lambda s: LAND, lambda s: LAND_D, tl)


@easy_wrists(3.4)
def lunge(t):
    """His answer to a third chain move in a row. He drops low and winds the chain round his
    forearm (the aim freezes at 0.45 s), slings the sword low and short, and lets the chain
    haul him after it. He lands, tears it free and spins: the cut lands 1.25 s after the
    freeze."""
    tr, ts, tl, hold, end = .62, .8, 1.33, 3.1, 3.4
    k = _arrival(tl, hold, end, LUNGE_LAND-LUNGE_OFFSET)
    home = GUARD_P+GRIP['right']*GUARD_D
    wind = lambda s: _ramp(s, .12, .2)*(1-_ramp(s, .45, .52))          # two turns of the forearm
    circle = lambda s, r: r*wind(s)*np.array([math.cos(4*math.pi*(s-.12)/.36), math.sin(4*math.pi*(s-.12)/.36), 0])
    base = curve([(0, home), (.1, (.7, 2.2, -.7)), (.3, (.65, 1.35, -.15), 'out'), (.5, (.62, 1.33, -.1)), (tr, (.3, 1.4, -1.7), 'in')])
    fist = lambda s: base(s)+circle(s, .1)
    held = ncurve([(0, GUARD_D), (.1, (.6, .3, -.7)), (.3, (.43, -.12, -.89), 'out'), (.5, (.43, -.12, -.89)), (tr, (.13, .1, -.98), 'in')])
    P0 = fist(tr)-GRIP['right']*held(tr)

    def stuck(s):                                # held, then flying low to where it sticks
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        return P0+(LUNGE_LAND-P0)*min(1., (s-tr)/(ts-tr))

    def stuck_d(s):
        return held(s) if s <= tr else slerp(held(tr), LAND_D, _ramp(s, tr, ts))
    pre = {'P': [], 'D': [],
           'torso': [(0, (0, 0, 0)), (.3, (25, 25, 0), 'out'), (.5, (26, 27, 0)), (tr, (30, -25, 0), 'hit'), (.9, (34, -10, 0)),
                     (1.05, (55, 0, 0))],
           'pelvis': [(0, (0, 0, 0)), (.3, (8, 10, 0), 'out'), (.5, (8, 10, 0)), (tr, (8, -10, 0), 'hit'), (.9, (10, 0, 0)),
                      (1.05, (18, 0, 0))],
           'shift': [(0, (0, 0, 0)), (.3, (0, -.6, 0), 'out'), (.5, (0, -.62, .02)), (tr, (0, -.6, -.5), 'hit'), (.9, (0, -.5, -.7)),
                     (1.05, (0, -.1, 0), 'out')],
           'spin': [(0, 0)], 'tip': [(0, 0), (.86, 0), (.96, 35), (tl-.08, 35)], 'gather': [(0, 0)],
           'right': [(tr, fist(tr)), (.72, (.3, 1.9, -2.0), 'snap'), (.9, (.35, 2.1, -1.9)), (1.28, (.4, 2.4, -1.5))],
           'left': [(.1, stuck(.1)+GRIP['left']*stuck_d(.1)), (.2, (.45, 1.75, -.45)), (.5, (.45, 1.72, -.42)), (tr, (-1.4, 1.9, -.5), 'hit'),
                    (.9, (-1.6, 2.2, -.3)), (1.28, (-1.8, 2.4, -.2))],
           'look': [(0, 0)], 'flare': [(0, 0), (.9, 0), (1.1, 1.4)],
           'head': curve([(0, (0, 0, 0)), (.3, (-14, -20, 0)), (.5, (-14, -22, 0)), (tr, (-18, 14, 0)), (1.05, (-30, 0, 0)), (tl, (-8, 0, 0)),
                          (tl+.17, (3, 0, 0))]),
           'swing': curve([(0, 0), (.5, -.3), (tr, .6, 'hit'), (.9, 0), (1.05, 1, 'hit'), (tl, .6), (2.4, 0)]),
           'hands': lambda s: _hands(s, .1, tr, 9.), 'thrown': lambda s: s >= tr,
           'left_add': lambda s: circle(s+.045, .16)}     # the left hand winds round the right forearm with it
    air = {'right': np.array([.45, 1.0, 1.3]), 'left': np.array([-.45, 1.3, .9])}
    lift = curve([(0, 0), (.9, 0), (1.02, 1, 'out'), (tl-.1, 1), (tl-.02, 0, 'in')])
    pre['feet'] = lambda s: {side: ANKLE[side]*(1-float(lift(s)))+air[side]*float(lift(s)) for side in ('right', 'left')}
    return _arrive(t, k, pre, TRAVEL['lunge'], stuck, stuck_d, tl)


# ---------------------------------------------------------------- Haul and Snare
# His own way round a pillar (the developer, Nov 11: the hall never helps him). The Haul is the
# Lunge with nothing wasted and twice the reach: a short low sling sends the sword far out past
# the pillar, and the chain hauls him to it at once. He lands in a crouch and tears it free,
# and there is no cut, because the Snare follows: the Hook with the Haul's quick low sling in
# place of its javelin draw, made as he turns on whoever was hiding.
HAUL_LAND = np.array([.3, .55, -14.])
HAUL_OFFSET = np.array([0., 0., -12.9])
TRAVEL['haul'] = curve([(0, (0, 0, 0)), (.5, (0, 0, 0)), (.77, HAUL_OFFSET+np.array([0, 0, .3]), 'linear'), (.8, HAUL_OFFSET, 'out')])


def _landed(tl, end, stuck):
    """_arrival without the spin: the sword torn free and caught at tl+0.17, carried low at
    his hip for a moment, and the guard at end."""
    tk = tl+.17
    return {'tk': tk,
            'P': [(tl, stuck), (tl+.09, (.6, 1.5, -.9)), (tk, SPIN_P), (tk+.1, SPIN_P), (end, GUARD_P)],
            'D': [(tl, LAND_D), (tl+.09, (.5, -.5, -.7)), (tk, SPIN_D), (tk+.1, SPIN_D), (end, GUARD_D)],
            'torso': [(tl, (25, 0, 0)), (tk, (8, -5, -.7)), (end, (0, 0, 0))],
            'pelvis': [(tl, (8, 0, 0)), (tk, (5, -8, 0)), (end, (0, 0, 0))],
            'shift': [(tl, (0, -.45, 0)), (tk, (0, -.3, 0)), (end, (0, 0, 0))],
            'spin': [(tl, 0)], 'tip': [(tl, 0)], 'gather': [(tl, 0)],
            'right': [(tl+.05, (.5, 2.2, -.9)), (tk-.06, (.5, 1.7, -.8))],
            'left': [(tl+.1, (-1.0, 1.9, -.7)), (tk+.1, (.19, 1.62, -.58))],
            'look': [(tl, 0)], 'flare': [(tl, .6), (tk+.2, 1.), (end, 0)]}


@easy_wrists(1.5)
def haul(t):
    """He drops low with the blade back at his right hip (the aim freezes at 0.2 s) and slings
    it flat at 0.3 s. It sticks 14 blocks out at 0.45 s and the chain hauls him after it from
    0.5 s: he lands 12.9 blocks away at 0.8 s, tears the sword free and has it in his hand at
    0.97 s. Left alone he rises into his guard; in the fight the Snare takes over there."""
    tr, ts, tl, end = .3, .45, .8, 1.5
    k = _landed(tl, end, HAUL_LAND-HAUL_OFFSET)
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.08, (.7, 2.2, -.7)), (.2, (.65, 1.35, -.15), 'out'), (tr, (.3, 1.4, -1.7), 'in')])
    held = ncurve([(0, GUARD_D), (.08, (.6, .3, -.7)), (.2, (.43, -.12, -.89), 'out'), (tr, (.13, .1, -.98), 'in')])
    P0 = fist(tr)-GRIP['right']*held(tr)

    def stuck(s):                                # held, then flying low to where it sticks
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        return P0+(HAUL_LAND-P0)*min(1., (s-tr)/(ts-tr))

    def stuck_d(s):
        return held(s) if s <= tr else slerp(held(tr), LAND_D, _ramp(s, tr, ts))
    pre = {'P': [], 'D': [],
           'torso': [(0, (0, 0, 0)), (.2, (25, 25, 0), 'out'), (tr, (30, -25, 0), 'hit'), (.5, (34, -10, 0)), (.6, (55, 0, 0))],
           'pelvis': [(0, (0, 0, 0)), (.2, (8, 10, 0), 'out'), (tr, (8, -10, 0), 'hit'), (.5, (10, 0, 0)), (.6, (18, 0, 0))],
           'shift': [(0, (0, 0, 0)), (.2, (0, -.6, 0), 'out'), (tr, (0, -.6, -.5), 'hit'), (.5, (0, -.5, -.7)), (.6, (0, -.1, 0), 'out')],
           'spin': [(0, 0)], 'tip': [(0, 0), (.45, 0), (.55, 35), (tl-.08, 35)], 'gather': [(0, 0)],
           'right': [(tr, fist(tr)), (.38, (.3, 1.9, -2.0), 'snap'), (.5, (.35, 2.1, -1.9)), (.76, (.4, 2.4, -1.5))],
           'left': [(.08, stuck(.08)+GRIP['left']*stuck_d(.08)), (.2, (.45, 1.75, -.45)), (tr, (-1.4, 1.9, -.5), 'hit'),
                    (.5, (-1.6, 2.2, -.3)), (.76, (-1.8, 2.4, -.2))],
           'look': [(0, 0)], 'flare': [(0, 0), (.5, 0), (.62, 1.4)],
           'head': curve([(0, (0, 0, 0)), (.2, (-14, -20, 0)), (tr, (-18, 14, 0)), (.6, (-30, 0, 0)), (tl, (-8, 0, 0)), (tl+.17, (3, 0, 0))]),
           'swing': curve([(0, 0), (.2, -.3), (tr, .6, 'hit'), (.5, 0), (.6, 1, 'hit'), (tl, .6), (1.4, 0)]),
           'hands': lambda s: _hands(s, .08, tr, 9.), 'thrown': lambda s: s >= tr, 'left_add': lambda s: 0.}
    air = {'right': np.array([.45, 1.0, 1.3]), 'left': np.array([-.45, 1.3, .9])}
    lift = curve([(0, 0), (.5, 0), (.58, 1, 'out'), (tl-.1, 1), (tl-.02, 0, 'in')])
    pre['feet'] = lambda s: {side: ANKLE[side]*(1-float(lift(s)))+air[side]*float(lift(s)) for side in ('right', 'left')}
    return _arrive(t, k, pre, TRAVEL['haul'], stuck, stuck_d, tl, spin=False)


@easy_wrists(2.7)
def snare(t):
    """The Hook made in a hurry, straight after a Haul. No javelin draw: he drops the blade
    back to his right hip (the aim freezes at 0.25 s) and slings it flat and low at 0.4 s, the
    Haul's own sling. From there it is the Hook 0.6 s early: the strike 27 blocks out at
    0.7 s, three hauls hand over hand, the catch at 1.9 s. The Overhead Sentence follows."""
    tr, th, tc, end = .4, .7, 1.9, 2.7
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.1, (.7, 2.2, -.7)), (.25, (.65, 1.35, -.15), 'out'), (tr, (.3, 1.4, -1.7), 'in')])
    held = ncurve([(0, GUARD_D), (.1, (.6, .3, -.7)), (.25, (.43, -.12, -.89), 'out'), (tr, (.13, .1, -.98), 'in')])
    P0 = fist(tr)-GRIP['right']*held(tr); aim = unit(HOOK_HIT-P0); hit = HOOK_HIT-SWORD_TIP*aim
    drag = curve([(th, hit), (th+.12, hit+np.array([0, -.1, .2])), (th+.32, (0, 1.4, -15.)), (th+.42, (0, 1.4, -14.6)),
                  (th+.65, (0, 1.3, -9.)), (th+.75, (0, 1.3, -8.7)), (th+1., (.1, 1.2, -3.5)), (tc, CATCH_P, 'in')])
    drag_d = dcurve([(th, aim), (th+1., (0, -.03, -1)), (tc, CATCH_D, 'in')])
    tail_p = curve([(tc, CATCH_P), (tc+.24, SETTLE_P, 'snap'), (end, GUARD_P)])
    tail_d = dcurve([(tc, CATCH_D), (tc+.24, SETTLE_D, 'snap'), (end, GUARD_D)])

    def P(s):
        if s <= tr: return fist(s)-GRIP['right']*held(s)
        if s <= th: return P0+(hit-P0)*(s-tr)/(th-tr)
        return drag(s) if s < tc else tail_p(s)

    def D(s):
        if s <= tr: return held(s)
        if s <= th: return slerp(held(tr), aim, _ramp(s, tr, tr+.06))
        return drag_d(s) if s < tc else tail_d(s)

    def N(s):
        n = _turned_n(held(min(s, tr)))
        return slerp(n, GUARD_N, _ramp(s, tc-.2, tc))
    far, near = (.25, 2.9, -1.75), (.5, 2.45, -.45)            # a hand reaching down the chain, and hauled in
    lfar, lnear = (-.15, 2.8, -1.7), (-.4, 2.4, -.5)
    left = curve([(.1, P(.1)+GRIP['left']*D(.1)), (.25, (.45, 1.75, -.45)), (tr, (-1.2, 2.2, -.6), 'hit'),
                  (th, (-.8, 2.7, -.7)), (th+.12, lnear), (th+.32, lfar), (th+.54, lnear, 'hit'), (th+.65, lfar), (th+.87, lnear, 'hit'),
                  (th+1., lfar), (tc, (-.3, 2.6, -.9)), (tc+.1, (-.05, 2.55, -1.0))])
    right = curve([(tr, fist(tr)), (tr+.1, (.3, 2.1, -2.0), 'snap'), (th, far), (th+.12, far), (th+.32, near, 'hit'), (th+.42, far),
                   (th+.65, near, 'hit'), (th+.75, far), (th+1., near, 'hit'), (tc-.06, (.3, 2.45, -.95))])
    pull = curve([(th, 0), (th+.12, 0), (th+.32, 1, 'hit'), (th+.42, .2), (th+.65, 1, 'hit'), (th+.75, .2), (th+1., 1, 'hit'), (tc, 0)])
    torso = curve([(0, (0, 0, 0)), (.25, (22, 25, 0), 'out'), (tr, (26, -25, 0), 'hit'), (tr+.1, (20, -28, 0), 'snap'),
                   (th, (10, -8, 0)), (tc, (6, -6, 0)), (tc+.24, (4, 0, 0)), (end, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (.25, (8, 10, 0), 'out'), (tr, (8, -10, 0), 'hit'), (th, (0, -4, 0)), (tc, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.25, (0, -.5, 0), 'out'), (tr, (0, -.5, -.5), 'hit'), (th, (0, -.12, -.4)),
                   (tc, (0, -.06, -.3)), (tc+.24, (0, -.05, -.26)), (end, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (.25, (-12, -18, 0)), (tr, (-14, 12, 0), 'hit'), (th, (0, 4, 0)), (tc, (0, 0, 0))])
    back = curve([(0, 0), (.05, 0), (.25, 1), (end-.55, 1), (end-.2, 0)])
    back_lift = curve([(0, 0), (.05, 0), (.15, .12, 'out'), (.25, 0, 'in'), (end-.55, 0), (end-.38, .1, 'out'), (end-.2, 0, 'in')])
    stride = curve([(0, 0), (tr-.14, 0), (tr, 1, 'hit'), (end-.6, 1), (end-.25, 0)])
    stride_lift = curve([(0, 0), (tr-.14, 0), (tr-.07, .2, 'out'), (tr, 0, 'in'), (end-.6, 0), (end-.42, .12, 'out'), (end-.25, 0, 'in')])
    swing = curve([(0, 0), (.25, -.5), (tr, 1, 'hit'), (th, .2), (tc, .5, 'hit'), (end-.3, 0)])
    b, f, pl = float(back(t)), float(stride(t)), float(pull(t))
    spec = {'pelvis_pos': shift(t)+np.array([0, -.08*pl, .22*pl]), 'pelvis_rot': pelvis(t),
            'torso_rot': torso(t)+np.array([-14*pl, 0, 0]), 'neck_rot': head(t)*.4, 'head_rot': head(t)*.6+np.array([8*pl, 0, 0]),
            'sword_p': P(t), 'sword_d': D(t), 'sword_n': N(t), 'hands': _hands(t, .1, tr, tc),
            'free_hands': {'left': left(t), 'right': right(t)}, 'free_elbow': {'left': (-.6, -.2, 1)},
            'feet': planted(dx=(.06*b, float(back_lift(t)), .3*b), sx=(0, float(stride_lift(t)), -.6*f)),
            'extra': cloth(t, swing=float(swing(t)))}
    spec['hinge'] = True
    if tr <= t < tc+.1: spec['hidden'] = ['hanging_wrist_chain']
    return spec


# ---------------------------------------------------------------- Unbound
# The phase change. He drives the sword into the floor with the overhead cut and lets go; the
# chains of his curse tear loose from his chest, sweep the hall low, hook on, and reel back in.
# The six chains are the marker bones UNBOUND_CHAINS: the clip sends each to its chain's far
# end, and whatever draws him draws a chain from his chest to every marker that has left it.
from geometry import UNBOUND_CHAINS    # noqa: E402
STAKE_FIST, STAKE_D = np.array([-.25, 1.1, -1.7]), unit((.2, -.46, -.87))   # the blade driven in at an angle
UNBOUND_REACH = 12.


def _swept(keys):
    """keys: (time, rate). Returns t -> how far a point has gone whose speed runs straight from
    each key's rate to the next: the integral of the rate from the first key."""
    ts, ws = [k[0] for k in keys], [k[1] for k in keys]; total = [0.]
    for i in range(1, len(ts)): total.append(total[-1]+(ws[i-1]+ws[i])/2*(ts[i]-ts[i-1]))

    def at(t):
        if t <= ts[0]: return 0.
        if t >= ts[-1]: return total[-1]
        i = max(j for j in range(len(ts)) if ts[j] <= t); f = t-ts[i]
        return total[i]+(ws[i]+(ws[i+1]-ws[i])*f/(ts[i+1]-ts[i])/2)*f
    return at


UNBOUND_SWING = [(1.2, 60), (1.3, 120), (1.5, 170), (1.75, 260), (2.0, 180), (2.3, 100), (2.6, 50), (2.72, 15),
                 (2.85, 120), (3.1, 300), (3.3, 220)]
_UNBOUND_TURN = _swept(UNBOUND_SWING)


@easy_wrists(4.3)
def unbound(t):
    """He raises the sword, cuts down into the floor (0.64 s) and leans on the hilt to drive
    the blade in, kneeling behind it. He lets go at 0.95 s and rises. At 1.2 s he throws his
    arms and head back and six chains burst from his chest, already whirling to his left.
    They never hang still: they run out along spirals, swing fastest as they drop to ankle
    height (the clang at 1.75 s), and keep swinging round him, slowing and rising, until the
    hooks catch at about 2.7 s. From 2.75 s he hauls them in, still turning, hunching over
    the sword; at 3.4 s he takes the hilt, tears the blade free and rises into his guard."""
    a, b, free, take, end = .5, .5+CHOP, .95, 3.4, 4.3; sunk = b+.2; a1, a2, a3 = a+CHOP/3, a+CHOP*2/3, a+CHOP*5/6
    home = GUARD_P+GRIP['right']*GUARD_D
    fist = curve([(0, home), (.25, (.5, 4.6, -.3), 'out'), (a, HIGH_FIST), (a1, (.3, 4.65, -1.15), 'in'),
                  (a2, (0, 3.35, -2.15), 'linear'), (a3, (-.3, 2.0, -1.95), 'linear'), (b, SLAM_FIST, 'linear'),
                  (sunk, STAKE_FIST, 'out'), (take+.1, STAKE_FIST), (3.75, REST_FIST), (end, home)])
    D = ncurve([(0, GUARD_D), (.25, (.3, .75, .6), 'out'), (a, HIGH_D), (a1, (.16, .98, .15), 'in'),
                (a2, (.07, .71, -.7), 'linear'), (a3, (.27, .51, -.82), 'linear'), (b, SLAM_D, 'linear'),
                (sunk, STAKE_D, 'out'), (take+.1, STAKE_D), (3.75, REST_D), (end, GUARD_D)])

    def P(s): return fist(s)-GRIP['right']*D(s)
    torso = curve([(0, (0, 0, 0)), (.25, (-6, 8, 0), 'out'), (a, (-10, 6, 0)), (b, (30, 0, 0), 'hit'), (free, (32, 0, 0)),
                   (1.1, (-4, 0, 0)), (1.2, (-20, 0, 0), 'hit'), (1.45, (-14, 20, 0)), (1.9, (-6, -30, 0)),
                   (2.6, (0, -18, 0)), (2.72, (6, 0, 0), 'hit'), (2.85, (4, -6, 0)), (3.3, (26, 0, 0), 'hit'), (take, (30, 0, 0)), (take+.1, (30, 0, 0)), (3.75, (15, 2, -1)),
                   (end, (0, 0, 0))])
    pelvis = curve([(0, (0, 0, 0)), (a, (-2, 4, 0)), (b, (10, 0, 0), 'hit'), (free, (10, 0, 0)), (1.1, (0, 0, 0)), (1.2, (-4, 0, 0)),
                    (2.75, (0, 0, 0)), (3.3, (8, 0, 0)), (take, (10, 0, 0)), (take+.1, (10, 0, 0)), (3.75, (5, 0, 0)), (end, (0, 0, 0))])
    shift = curve([(0, (0, 0, 0)), (.25, (0, .02, .08), 'out'), (a, (0, .04, .1)), (b, (0, -.9, -.5), 'hit'), (free, (0, -.9, -.5)),
                   (1.1, (0, -.25, -.25)), (1.2, (0, 0, -.15), 'hit'), (1.9, (0, -.1, -.15)), (2.75, (0, -.15, -.15)),
                   (3.3, (0, -.8, -.45)), (take, (0, -.9, -.5)), (take+.1, (0, -.9, -.5)), (3.75, (-.02, -.14, -.7)), (end, (0, 0, 0))])
    head = curve([(0, (0, 0, 0)), (a, (-6, 0, 0)), (b, (-14, 0, 0)), (1.1, (0, 0, 0)), (1.2, (-32, 0, 0), 'hit'), (1.45, (-20, -12, 0)),
                  (1.9, (-8, 14, 0)), (2.75, (0, 0, 0)), (3.3, (8, 0, 0)), (3.75, (-4, 0, 0)), (end, (0, 0, 0))])
    grip_r, grip_l = STAKE_FIST, P(free)+GRIP['left']*D(free)
    # Free arms: flung wide at the burst, thrown round with the swing, hauling in at the end.
    right = curve([(free, grip_r), (1.1, (1.0, 2.2, -1.0)), (1.3, (2.3, 3.8, .1)), (1.5, (2.2, 3.6, .2)),
                   (1.9, (1.6, 3.5, -1.3)), (2.75, (1.4, 3.0, -1.0)), (3.3, (.3, 1.7, -1.6), 'hit'), (take-.04, grip_r+(0, .15, .1))])
    left = curve([(free, grip_l), (1.1, (-1.0, 2.2, -1.0)), (1.25, (-2.3, 3.8, .1), 'out'), (1.5, (-1.7, 3.5, -1.2)),
                  (1.9, (-2.2, 3.6, .2)), (2.75, (-1.4, 3.0, -1.0)), (3.3, (-.6, 1.7, -1.5), 'hit'), (take+.1, grip_l)])
    lunge = curve([(0, 0), (a+.02, 0), (b, 1, 'hit'), (3.8, 1), (end-.05, 0)])
    lunge_lift = curve([(0, 0), (a+.02, 0), (a+.07, .2, 'out'), (b, 0, 'in'), (3.8, 0), (4.02, .12, 'out'), (end-.05, 0, 'in')])
    back = curve([(0, 0), (.05, 0), (.25, 1), (3.8, 1), (end-.05, 0)])
    back_lift = curve([(0, 0), (.05, 0), (.15, .12, 'out'), (.25, 0, 'in'), (3.8, 0), (4.02, .1, 'out'), (end-.05, 0, 'in')])
    chop0, chop1 = planes(P, D, (a+CHOP/3, a+CHOP*5/6))
    rest_n = ncurve([(0, GUARD_N), (.25, chop0), (a, chop0), (b, chop1, 'hit'), (take+.1, chop1), (end, GUARD_N)])
    lead = curve([(0, 0), (a, 0), (a+.03, 1, 'linear'), (b, 1), (b+.02, 0)])      # then he twists it in
    hands = 'both' if t < free else 'none' if t < take else 'right' if t < take+.1 else 'both'
    bk = float(back(t))
    swing = curve([(0, 0), (a, -.4), (b, 1, 'hit'), (1.1, 0), (1.2, -1, 'hit'), (1.9, .6), (2.75, 0), (3.3, .6), (end, 0)])
    flare = curve([(0, 0), (1.1, 0), (1.2, 2.2, 'hit'), (1.9, 1.4), (2.75, .5), (3.3, 0)])
    spec = {'pelvis_pos': shift(t), 'pelvis_rot': pelvis(t), 'torso_rot': torso(t), 'neck_rot': head(t)*.4, 'head_rot': head(t)*.6,
            'sword_p': P(t), 'sword_d': D(t), **edge_lead(P, D, rest_n, lead, t), 'hands': hands,
            'free_hands': {'left': left(t), 'right': right(t)},
            'feet': planted(dx=(.06*bk, float(back_lift(t)), .3*bk), sx=(0, float(lunge_lift(t)), -1.*float(lunge(t)))),
            'extra': cloth(t, swing=float(swing(t)), lift=float(flare(t)))}
    # The chains whirl to his left for as long as they are out. UNBOUND_SWING is how fast
    # (deg/s): rising through the burst, fastest at the clang, slowing as they climb, checked
    # when the hooks catch, and whipping round faster and faster as he hauls them in.
    reach = float(curve([(1.2, 0), (1.5, 1, 'out'), (2.75, 1), (3.3, 0, 'in')])(t))
    turn = _UNBOUND_TURN(t)
    height = float(curve([(1.2, 2.9), (1.45, 2.4), (1.65, .35), (1.9, .35), (2.2, 1.2), (2.6, 1.5), (2.75, 1.5), (3.3, 2.9)])(t))
    W = fk({'pelvis': (tuple(spec['pelvis_rot']), tuple(np.array(spec['pelvis_pos'])*[-16, 16, 16])),
            'torso': (tuple(spec['torso_rot']), (0, 0, 0))})
    chest = pivot_world(W, UNBOUND_CHAINS[0])                       # where the markers rest
    spec['free_elbow'] = {'right': (.6, -.2, 1), 'left': (-.6, -.2, 1)}      # back and out, away from his chest
    spec['hinge'] = True
    spec['chain_ends'] = {name: chest+reach*(UNBOUND_REACH*_az(30+60*i+turn)+np.array([0, height, 0])-chest)
                          for i, name in enumerate(UNBOUND_CHAINS)}
    if 1.2 <= t < 3.3: spec['hidden'] = ['curse_chain_chest']
    return spec


# ---------------------------------------------------------------- re-verify the edge and wrists
# The blows whose edge must lead: the Royal Cut's cut and backhand, the Turning Cut's sweep,
# the throw's whip. A thrust has no edge to lead; its wrists, grip and bake are still checked.
BLOWS = {'royal_cut': ((.9, 1.3), (1.75, 2.2)), 'turning_cut': ((.68, .85),), 'cast_reel': ((.74, RELEASE),),
         'overhead_sentence': ((.7, .88),), 'royal_cut_three': ((.9, 1.3), (1.75, 2.2), (2.62, 2.8)),
         'zip': ((1.24, 1.62),), 'lunge': ((1.54, 1.92),), 'unbound': ((.5, .66),)}
ATTACKS = ('royal_cut', 'royal_cut_three', 'kingsguard_thrust', 'turning_cut', 'cast_reel', 'chain_scythe_low',
           'chain_scythe_high', 'zip', 'hook', 'overhead_sentence', 'lunge', 'drag_down', 'unbound', 'haul', 'snare')
# While a fist holds the sword it must stay above the floor, except where the clip means it
# to be in the floor: Unbound drives it in and later tears it out.
BURIED = {'unbound': ((.6, 3.8),)}


# What check_edges holds each clip to (summary key: limit; 'lowest' keys are minimums).
LIMITS = {'max edge deg': 25, 'half-sample max edge deg': 25, 'baked max edge deg': 25,
          'max grip gap': .01, 'half-sample max grip gap': .01, 'baked max grip gap': .01,
          'max |twist| deg': 90, 'half-sample max |twist| deg': 90, 'max bend deg': 90, 'half-sample max bend deg': 90,
          'max arm turn per sample deg': 90, 'max free arm turn per sample deg': 150, 'baked max blade error deg': 5, 'baked max blade step excess deg': 5,
          'baked max arm error deg': 10, 'baked lowest held sword y': 0}


def check_edges(names=ATTACKS, clips=None, rate=20, table=True, baked=True, fps=60):
    """Re-verify the edge lead, the wrists and the grip: on the bake's 20 Hz samples, half way
    between them, and as GeckoLib plays the baked keys. From art/pyre_king:
        py -3 -c "import clips; clips.check_edges()"
    Samples: in each blow's fast samples (tip moving > 8 blocks/s across the blade) the blade's
    edge axis (its local X) must lie within 25 deg of the tip's motion across the blade, taken
    between the neighbouring 20 Hz samples. Every gripping fist must sit on the grip (< 0.01
    blocks), twist and bend under 90 deg against its forearm, and no upper arm or forearm may
    turn more than 90 deg between samples (an elbow flipping round its circle); an arm whose
    fist is free may turn up to 150 deg, as when it is flung wide.
    Half samples (t + 0.025 s, solved): the same edge, wrist and grip tests, the edge against
    the motion across that interval.
    Baked (fps frames of rig.bake's keys, lerped as core.js plays them, against the solved
    pose): fists on the grip, the blade within 5 deg of the true one and never stepping more
    than 5 deg further per frame than it truly turns, arms within 10 deg, the edge leading in
    fast blow frames, and the sword above the floor while a fist holds it.
    Prints each blow's samples and a summary per clip, with 'problems' naming every limit
    (LIMITS) it breaks; returns {clip: summary}. `clips` may be another CLIPS-like table,
    e.g. an older clips.py's."""
    from rig import Solver, bake, player
    pv = np.r_[baked_point(BY['sword']['pivot']), 1]
    SWORD_CORNERS = np.vstack([_corners(c) for c in BY['sword']['cubes']])

    def tip(W): return (W['sword']@pv)[:3]+SWORD_TIP*W['sword'][:3, 1]

    def world(fn, t, solver=None): spec = fn(t); return spec, fk((solver or Solver()).solve(spec))

    def edge_off(W, v):
        y, x = W['sword'][:3, 1], W['sword'][:3, 0]; v = v-np.dot(v, y)*y
        return math.degrees(math.acos(min(1, abs(np.dot(x, v))/max(np.linalg.norm(v), 1e-9)))), float(np.linalg.norm(v))

    def gaps(spec, W):
        y = W['sword'][:3, 1]
        return [float(np.linalg.norm(pivot_world(W, s+'_hand')-(W['sword']@pv)[:3]-GRIP[s]*y))
                for s in ('right', 'left') if grips(spec, s)]

    def wrists_of(spec, W):
        return {s: wrist_angles(W[s+'_forearm'][:3, :3].T@W[s+'_hand'][:3, :3], s) for s in ('right', 'left') if grips(spec, s)}

    def limbs(W, sides=('right', 'left')):
        return [W[a][:3, :3].T@W[b][:3, :3] for s in sides
                for a, b in (('torso', s+'_upper_arm'), (s+'_upper_arm', s+'_forearm'))]

    def turn_deg(A, B): return math.degrees(math.acos(np.clip((np.trace(A.T@B)-1)/2, -1, 1)))

    def in_blow(name, t): return any(lo <= t <= hi for lo, hi in BLOWS.get(name, ()))

    def peak(xs, nd=1): return round(float(max(xs, default=0)), nd)
    out = {}
    for name in names:
        fn, length, loop = (clips or CLIPS)[name]; solver = Solver(); n = int(round(length*rate))
        frames = [world(fn, round(i/rate, 4), solver) for i in range(n+1)]
        tips = [tip(W) for _, W in frames]
        edge, twist, bend, jump, gap, turns, loose, prev = [], [], [], [], [0.], [0.], [0.], {}
        if table: print(f'{name}:   t  speed  edge | R twist bend | L twist bend | grip gap')
        for i, (spec, W) in enumerate(frames):
            t = round(i/rate, 4)
            a, b = max(i-1, 0), min(i+1, n); ang, speed = edge_off(W, (tips[b]-tips[a])*rate/(b-a))
            inst = edge_off(W, (tip(world(fn, min(t+.005, length))[1])-tip(world(fn, max(t-.005, 0))[1]))/.01)[1]
            fast = inst > 8 and in_blow(name, t)
            wrists = wrists_of(spec, W)
            for side in ('right', 'left'):
                if side not in wrists: prev.pop(side, None); continue
                Q = W[side+'_forearm'][:3, :3].T@W[side+'_hand'][:3, :3]
                if side in prev: jump.append(geodesic(prev[side][None], Q[None])[0, 0])
                prev[side] = Q
            gap += gaps(spec, W)
            if i:           # an arm that grips at both samples, or one swinging free
                for side in ('right', 'left'):
                    held = grips(spec, side) and grips(frames[i-1][0], side)
                    (turns if held else loose).extend(turn_deg(A, B) for A, B in zip(limbs(frames[i-1][1], (side,)), limbs(W, (side,))))
            twist += [abs(w[0]) for w in wrists.values()]; bend += [w[1] for w in wrists.values()]
            if fast: edge.append(ang)
            if table and any(lo-.05 <= t <= hi+.05 for lo, hi in BLOWS.get(name, ())):
                cols = ' | '.join(f'{wrists[s][0]:+6.1f} {wrists[s][1]:5.1f}' if s in wrists else '     -     -'
                                  for s in ('right', 'left'))
                print(f'  {t:5.2f} {speed:6.1f} {ang:5.1f}{"*" if fast else " "}| {cols} | {max(gaps(spec, W), default=0):.4f}')
        summary = {'fast samples': len(edge), 'max edge deg': peak(edge),
                   'mean edge deg': round(float(np.mean(edge)) if edge else 0., 1),
                   'max |twist| deg': peak(twist), 'max bend deg': peak(bend),
                   'max wrist turn per sample deg': peak(jump), 'max arm turn per sample deg': peak(turns),
                   'max free arm turn per sample deg': peak(loose),
                   'max grip gap': peak(gap, 4)}
        # Half way between samples, where GeckoLib shows its in-betweens.
        h_edge, h_twist, h_bend, h_gap = [], [], [], [0.]
        for i in range(n):
            t = (i+.5)/rate; spec, W = world(fn, t)
            ang, _ = edge_off(W, (tips[i+1]-tips[i])*rate)
            inst = edge_off(W, (tip(world(fn, t+.005)[1])-tip(world(fn, t-.005)[1]))/.01)[1]
            if inst > 8 and in_blow(name, t): h_edge.append(ang)
            ws = wrists_of(spec, W); h_twist += [abs(w[0]) for w in ws.values()]; h_bend += [w[1] for w in ws.values()]
            h_gap += gaps(spec, W)
        summary.update({'half-sample max edge deg': peak(h_edge), 'half-sample max |twist| deg': peak(h_twist),
                        'half-sample max bend deg': peak(h_bend), 'half-sample max grip gap': peak(h_gap, 4)})
        if baked:
            play = player(bake(fn, length, loop, rate)); m = int(round(length*fps))
            ts = [min(j/fps, length) for j in range(m+1)]
            B = [fk(play(t)) for t in ts]; T = [world(fn, t) for t in ts]
            btips = [tip(W) for W in B]
            b_gap, b_err, b_step, b_edge, b_arm, low = [0.], [], [0.], [], [], [math.inf]
            for j, (t, Wb, (spec, Wt)) in enumerate(zip(ts, B, T)):
                yb, yt = Wb['sword'][:3, 1], Wt['sword'][:3, 1]
                b_gap += gaps(spec, Wb); b_err.append(math.degrees(math.acos(np.clip(yb@yt, -1, 1))))
                b_arm.append(max(turn_deg(Wb[s][:3, :3], Wt[s][:3, :3]) for s in
                                 [s+b for s in ('right', 'left') for b in ('_upper_arm', '_forearm', '_hand')]))
                if j:
                    yp, ytp = B[j-1]['sword'][:3, 1], T[j-1][1]['sword'][:3, 1]
                    b_step.append(math.degrees(math.acos(np.clip(yp@yb, -1, 1)))-math.degrees(math.acos(np.clip(ytp@yt, -1, 1))))
                if 0 < j < m:
                    ang, speed = edge_off(Wb, (btips[j+1]-btips[j-1])*fps/2)
                    if speed > 8 and in_blow(name, t): b_edge.append(ang)
                if gaps(spec, Wb) and not any(lo <= t <= hi for lo, hi in BURIED.get(name, ())):
                    low.append(float((SWORD_CORNERS@Wb['sword'][:3, :3].T+Wb['sword'][:3, 3])[:, 1].min()))
            summary.update({'baked fps': fps, 'baked max grip gap': peak(b_gap, 4), 'baked max blade error deg': peak(b_err),
                            'baked max blade step excess deg': peak(b_step), 'baked max edge deg': peak(b_edge),
                            'baked max arm error deg': peak(b_arm), 'baked lowest held sword y': round(min(low), 3)})
        summary['problems'] = [k for k, lim in LIMITS.items() if k in summary and
                               (summary[k] < lim if 'lowest' in k else summary[k] > lim)]
        out[name] = summary
        print(name, summary)
    return out


CLIPS = {
    'idle': (idle, 6.0, True),
    'walk': (walk, WALK['period'], True),
    'run': (run, RUN['period'], True),
    'royal_cut': (royal_cut, 3.8, False),
    'kingsguard_thrust': (kingsguard_thrust, 2.9, False),
    'turning_cut': (turning_cut, 2.6, False),
    'cast_reel': (cast_reel, 4.7, False),
    'chain_scythe_low': (chain_scythe_low, 2.9, False),
    'chain_scythe_high': (chain_scythe_high, 2.9, False),
    'overhead_sentence': (overhead_sentence, 2.6, False),
    'royal_cut_three': (royal_cut_three, 4.4, False),
    'drag_down': (drag_down, 2.4, False),
    'hook': (hook, 3.3, False),
    'zip': (zip_in, 3.2, False),
    'lunge': (lunge, 3.4, False),
    'haul': (haul, 1.5, False),
    'snare': (snare, 2.7, False),
    'unbound': (unbound, 4.3, False),
}
# Clips that end with him turned: degrees to his left, about his origin. The game turns his
# body yaw by this much on the clip's last frame, as the next clip starts in his own frame.
END_TURN = {'turning_cut': TURN_END}
# Clips that end with him somewhere else: his offset in blocks, in the frame he started in.
# The game moves him there on the clip's last frame, and must stretch the flight to the real
# distance to the sword.
END_OFFSET = {'zip': ZIP_OFFSET, 'lunge': LUNGE_OFFSET, 'haul': HAUL_OFFSET}
