"""Shared motion tools for the Roc's clips: timing with character, loop-safe irregularity and
follow-through, the drag and swing of loose feathers behind the bone that carries them.

All curves are pure functions of clip time, so a clip can be sampled at any rate. Pass a
period to make a curve wrap, so an event near the end of a loop carries over its seam.
"""
import math
import numpy as np


def _unit(f):
    return min(1., max(0., f))


def _settle(f):
    # A damped spring's step response: it arrives early, overshoots by about a sixth and settles.
    r = lambda x: 1-math.exp(-5*x)*(math.cos(9*x)+5/9*math.sin(9*x))
    return r(f)-(r(1)-1)*f*f


# Progress 0..1 to value 0..1 along a segment. Each names how a move arrives at its key.
SHAPES = {
    'ease': lambda f: f*f*(3-2*f),       # soft start and stop: slow, heavy shifts
    'linear': lambda f: f,
    'snap': lambda f: 1-(1-f)**3,        # starts at full speed and brakes: a bird's head snap
    'whip': lambda f: f**3,              # starts slowly and arrives at full speed: a strike landing
    'drop': lambda f: f*f,               # falls into place, gathering speed: a foot set down
    'hit': lambda f: 2*f*f-f**3,         # builds speed and lands still moving: a heavy blow
    'settle': _settle,                   # arrives fast, overshoots and settles back
}


def shaped(t, a, b, shape='ease'):
    """0 before a, 1 after b, shaped in between."""
    return SHAPES[shape](_unit((t-a)/(b-a))) if b > a else float(t >= a)


def keys(t, points, period=None):
    """A value held at keys, moving between them. A key is (time, value) or (time, value, shape),
    where shape is how the move arrives at that key (default 'ease'). With a period the keys
    wrap: the move into the first key starts at the last one."""
    points = [(k[0], k[1], k[2] if len(k) > 2 else 'ease') for k in points]
    if period:
        t %= period
        if t < points[0][0]:
            (a, av, _), (b, bv, shape) = points[-1], points[0]
            a -= period
            return av+(bv-av)*SHAPES[shape](_unit((t-a)/(b-a)))
    if t <= points[0][0]:
        return points[0][1]
    for (a, av, _), (b, bv, shape) in zip(points, points[1:]):
        if t <= b:
            return av+(bv-av)*SHAPES[shape](_unit((t-a)/(b-a)))
    return points[-1][1]


def envelope(t, start, rise, hold, fall, up='snap', down='ease', period=None):
    """0 → 1 over rise, held, then 1 → 0 over fall. A snap up and a slower ease down is the
    default: animals act fast and relax slowly."""
    def one(u):
        if u < start or u > start+rise+hold+fall:
            return 0.
        if u < start+rise:
            return SHAPES[up](_unit((u-start)/rise))
        if u <= start+rise+hold:
            return 1.
        return 1-SHAPES[down](_unit((u-start-rise-hold)/fall))
    if period:
        return max(one(t % period), one(t % period+period), one(t % period-period))
    return one(t)


def shake(t, start, length, hz, decay=3.):
    """A shudder: an oscillation at hz that starts at full size and dies away over length."""
    u = t-start
    if u < 0 or u > length:
        return 0.
    return math.sin(math.tau*hz*u)*math.exp(-decay*u/length)*(1-shaped(u, .8*length, length))


def twitch(t, glances, period=None, spark=3, hz=18):
    """The Roc's signature head: held dead still, then snapped to a new angle in a few frames,
    landing with a small electric shiver, as if the storm in it jolts the muscles. glances are
    (time, (pitch, yaw, roll)) or (time, angles, spark size) in time order, each held until the
    next. Two glances a few hundredths apart make a stutter. A snap takes 0.05 s plus 0.02 s per
    10 degrees it turns, so small flicks are near instant and big swings still read. With a
    period the last glance is held over the seam into the first. Returns pitch, yaw, roll in
    degrees."""
    points, landings, before = [], [], glances[-1][1] if period else (0, 0, 0)
    for glance in glances:
        at, angles = glance[0], glance[1]
        quick = .05+np.linalg.norm(np.subtract(angles, before))/500
        points += [(at, before), (at+quick, angles, 'snap')]
        landings.append((at+quick, glance[2] if len(glance) > 2 else spark))
        before = angles
    out = np.array([keys(t, [(k[0], k[1][axis])+k[2:] for k in points], period) for axis in range(3)])
    for at, size in landings:
        out[1] += size*shake(t, at, .18, hz, 3)
        out[2] += .8*size*shake(t, at, .18, hz*1.3, 3)
    return out


def noise(t, period, seed, harmonics=(1, 2, 3, 5)):
    """Smooth irregular drift in -1..1 that repeats exactly every period. Harmonics of the loop
    with seeded phases and falling sizes, so a loop never settles into one even sway."""
    rng = np.random.default_rng(seed)
    phases = rng.uniform(0, math.tau, len(harmonics))
    weights = [1/h for h in harmonics]
    v = sum(w*math.sin(math.tau*h*t/period+p) for h, w, p in zip(harmonics, weights, phases))
    return v/sum(weights)


def rotvec(R):
    """The rotation vector (axis times angle, radians) of a rotation matrix."""
    v = .5*np.array([R[2, 1]-R[1, 2], R[0, 2]-R[2, 0], R[1, 0]-R[0, 1]])
    s = np.linalg.norm(v)
    c = (np.trace(R)-1)/2
    angle = math.atan2(s, c)
    return v*(angle/s) if s > 1e-9 else v


def rotmat(v):
    """Rodrigues: the rotation matrix of a rotation vector."""
    angle = np.linalg.norm(v)
    if angle < 1e-12:
        return np.eye(3)
    k = v/angle
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3)+math.sin(angle)*K+(1-math.cos(angle))*K@K


class Spring:
    """How a loose part trails the bone that carries it. hz is how fast it swings back (low is
    heavy and slow, high is light and quick); damping 1 never overshoots, lower wobbles longer.
    turn is how much it lags the carrier's turning and shove how much it is flung by the carrier
    being pushed around, both 0..1; limit caps the swing in degrees and speed its rate in degrees
    a second: air holds a feather back, so even a dead stop flings it over a few frames."""
    def __init__(self, names, hz, damping, turn=1., shove=1., limit=12., speed=450.):
        self.names, self.hz, self.damping = list(names), hz, damping
        self.turn, self.shove, self.limit, self.speed = turn, shove, limit, speed


def lag(times, carrier, pivot, lever, spring, substeps=8):
    """Integrates one part's lag from rest. carrier: (N,3,3) world rotations of the bone that
    carries it; pivot: (N,3) the part's pivot in the world; lever: (N,3) pivot to the part's
    centre of mass in the carrier's frame. Returns (N,3) rotation vectors in the carrier's frame.
    The part feels the carrier's angular acceleration as a twist the other way, and the pivot's
    acceleration as a push on its centre of mass; a damped spring pulls it back to rest."""
    n = len(times)
    t = np.asarray(times, dtype=float)
    dt = np.diff(t)
    # Angular velocity between frames, in the carrier's frame.
    w = np.array([rotvec(carrier[k].T@carrier[k+1])/dt[k] for k in range(n-1)])
    alpha = np.zeros((n, 3))
    accel = np.zeros((n, 3))
    for k in range(1, n-1):
        h = (dt[k-1]+dt[k])/2
        alpha[k] = (w[k]-w[k-1])/h
        a = ((pivot[k+1]-pivot[k])/dt[k]-(pivot[k]-pivot[k-1])/dt[k-1])/h
        accel[k] = carrier[k].T@a
    drive = np.zeros((n, 3))
    for k in range(n):
        L = np.linalg.norm(lever[k])
        push = np.cross(lever[k], -accel[k])/(L*L) if L > 1e-6 else 0
        drive[k] = -spring.turn*alpha[k]+spring.shove*push
    w0 = math.tau*spring.hz
    zeta = spring.damping
    limit, speed = math.radians(spring.limit), math.radians(spring.speed)
    phi, vel = np.zeros(3), np.zeros(3)
    out = np.zeros((n, 3))
    for k in range(n-1):
        for s in range(substeps):
            f = (s+.5)/substeps
            d = drive[k]*(1-f)+drive[k+1]*f
            h = dt[k]/substeps
            vel += (d-w0*w0*phi-2*zeta*w0*vel)*h
            if np.linalg.norm(vel) > speed:
                vel *= speed/np.linalg.norm(vel)
            phi += vel*h
            size = np.linalg.norm(phi)
            if size > limit:
                phi *= limit/size
                outward = phi/np.linalg.norm(phi)
                vel -= max(0., vel@outward)*outward
        out[k+1] = phi
    return out


def thin(times, values, tolerance):
    """The keys a linear interpolation needs to stay within tolerance of every sample: holds and
    straight runs collapse to their ends. Returns the kept indices, always the first and last."""
    t = np.asarray(times, dtype=float)
    v = np.asarray(values, dtype=float).reshape(len(t), -1)
    def fits(a, b):
        f = (t[a+1:b]-t[a])/(t[b]-t[a])
        return np.all(np.abs(v[a]+(v[b]-v[a])*f[:, None]-v[a+1:b]) <= tolerance)
    kept, a = [0], 0
    while a < len(t)-1:
        b = a+1
        while b+1 < len(t) and fits(a, b+1):
            b += 1
        kept.append(b)
        a = b
    return kept


def limit_rate(frames, times, names, rate):
    """Caps how fast these bones' rotation channels may turn, in degrees a second, carrying any
    excess into the next frames. A contact solve that lifts a wing off the rock in one jump then
    lands over a few frames instead of snapping. frames are pose dicts, changed in place."""
    for n in names:
        for k in range(1, len(frames)):
            step = rate*(times[k]-times[k-1])
            a, b = frames[k-1][n]['rotation'], frames[k][n]['rotation']
            d = (b-a+180) % 360-180
            frames[k][n]['rotation'] = a+np.clip(d, -step, step)
