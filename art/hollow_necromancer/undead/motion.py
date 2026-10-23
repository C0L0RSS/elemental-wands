"""Timing and joint tools shared by the reworked Hollow undead clips.

Curves are pure functions of clip time. A key is (time, value) or (time, value, shape), where
shape says how the move arrives at that key; values may be numbers or lists. Heavy bodies use
few keys with long holds and slow eases; only blows use the fast shapes.
"""
import math


def _unit(f):
    return min(1., max(0., f))


def _settle(f):
    # A damped spring's step response: arrives early, overshoots by about a sixth, settles.
    r = lambda x: 1-math.exp(-5*x)*(math.cos(9*x)+5/9*math.sin(9*x))
    return r(f)-(r(1)-1)*f*f


SHAPES = {
    'ease': lambda f: f*f*(3-2*f),       # soft start and stop: heavy shifts of weight
    'linear': lambda f: f,
    'out': lambda f: 1-(1-f)**2,         # leaves fast, brakes into the key
    'snap': lambda f: 1-(1-f)**3,        # starts at full speed and brakes hard
    'in': lambda f: f*f,                 # gathers speed: a weight dropping
    'whip': lambda f: f**3,              # slow start, arrives at full speed: a blow landing
    'hit': lambda f: 2*f*f-f**3,         # builds speed and lands still moving
    'slow': lambda f: f*f*f*(f*(6*f-15)+10),  # smootherstep: very gentle ends
    'settle': _settle,                   # arrives fast, overshoots and settles back
}


def shaped(t, a, b, shape='ease'):
    """0 before a, 1 after b, shaped in between."""
    return SHAPES[shape](_unit((t-a)/(b-a))) if b > a else float(t >= a)


def _mix(a, b, f):
    if isinstance(a, (list, tuple)):
        return [x+(y-x)*f for x, y in zip(a, b)]
    return a+(b-a)*f


def keys(t, points):
    """The value at clip time t of a keyed curve: held at keys, shaped moves between them."""
    pts = [(k[0], k[1], k[2] if len(k) > 2 else 'ease') for k in points]
    if t <= pts[0][0]:
        return list(pts[0][1]) if isinstance(pts[0][1], (list, tuple)) else pts[0][1]
    for (a, av, _), (b, bv, shape) in zip(pts, pts[1:]):
        if t <= b:
            return _mix(av, bv, SHAPES[shape](_unit((t-a)/(b-a))) if b > a else 1)
    last = pts[-1][1]
    return list(last) if isinstance(last, (list, tuple)) else last


def envelope(t, start, rise, hold, fall, up='snap', down='ease'):
    """0 -> 1 over rise, held, then 1 -> 0 over fall."""
    if t < start or t > start+rise+hold+fall:
        return 0.
    if t < start+rise:
        return SHAPES[up](_unit((t-start)/rise))
    if t <= start+rise+hold:
        return 1.
    return 1-SHAPES[down](_unit((t-start-rise-hold)/fall))


def shake(t, start, length, hz, decay=3.):
    """A shudder at hz that starts at full size and dies away over length."""
    u = t-start
    if u < 0 or u > length:
        return 0.
    return math.sin(math.tau*hz*u)*math.exp(-decay*u/length)*(1-shaped(u, .8*length, length))


def tremor(t, hz=9., seed=0.):
    """Small, irregular strain shiver (a sum of incommensurate sines), roughly -1..1."""
    return (math.sin(math.tau*hz*t+seed)*.6+math.sin(math.tau*hz*1.73*t+2.1+seed)*.3
            + math.sin(math.tau*hz*.61*t+4.2+seed)*.25)


def lag(samples, dt, stiffness=60., damping=11.):
    """A damped spring that trails a sampled signal (lists of floats): overlapping action for
    parts that hang off a moving body. Starts at rest on the first sample."""
    out = []
    x = list(samples[0]) if isinstance(samples[0], (list, tuple)) else [samples[0]]
    v = [0.]*len(x)
    for s in samples:
        s = list(s) if isinstance(s, (list, tuple)) else [s]
        for _ in range(4):
            h = dt/4
            for i in range(len(x)):
                a = stiffness*(s[i]-x[i])-damping*v[i]
                v[i] += a*h
                x[i] += v[i]*h
        out.append(list(x))
    return out if isinstance(samples[0], (list, tuple)) else [o[0] for o in out]


# ---------- Rotation keys ----------
def twin(r):
    """The other Euler form of the same turn (X, Y negated; applied Z, Y, X)."""
    return [r[0]+180, 180-r[1], r[2]+180]


def nearest(r, previous):
    """The form of r (twin and whole turns per channel) closest to the previous key, so a
    linear Euler blend between them never spins a part the long way round."""
    best = None
    for form in (list(r), twin(r)):
        cand = [previous[i]+(form[i]-previous[i]+180) % 360-180 for i in range(3)]
        cost = sum(abs(cand[i]-previous[i]) for i in range(3))
        if best is None or cost < best[0]:
            best = (cost, cand)
    return best[1]


def continuous(channel):
    """Rewrite a rotation channel {time: [x, y, z]} in place so each key is the form nearest
    the previous one."""
    previous = None
    for key in sorted(channel, key=float):
        if previous is not None:
            channel[key] = nearest(channel[key], previous)
        previous = channel[key]
    return channel
