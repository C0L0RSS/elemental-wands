#!/usr/bin/env python3
"""The First Wizard's change to phase two: the cinematic's script, baked for the game.

The developer's request (Nov 19, docs/design/first-wizard.md, "Phase two"): "a cinematic for the arm falling off then
the tentacles popping out of the body maybe having like a bulge that makes the head bend the other way from the
tentacles to give a more uncanny vibe". The tentacles come from her left side, whichever hand the skin's owner uses.
Boss cinematics are long, weighty and unhurried; this one is a mid-fight beat, so about 17 s.

At half health the fight stops for everyone in it. The copy staggers from the blow; its left arm wrenches back and out
and twitches; it tears away from the shoulder on strands of ink, drops, tumbles, and lands by her feet, lies there a
moment and melts into ink that soaks into the floor. Her left flank swells in slow pulses with ink pressing out under
the skin, and as it swells her head bends slowly, too far, the other way, with a twitch. The swelling bursts and the
tentacles erupt out of it (client/blockink's TendrilFx draws them from GROW_FROM), and she lifts off the floor into
phase two's float. Then the camera hands back and the fight resumes with phase two's tell and full sweep, which are
gameplay (PhaseChange), not part of this script.

This script owns the cinematic on the change's own clock (20 ticks a second, tick 0 the change's first):
  - the camera, one shot after another, with hard cuts;
  - the body: the stagger, the arm's wrench and twitches, the gap opening at its shoulder, the torn arm's fall (where
    its middle is and how it is turned), how far it has melted, the seam's ink, the bulge and the veins under the skin,
    the head's bend and twitch, and the lift.

Points are in the copy's frame at the change's start: +X on her left, +Z ahead of her (the way she faces), y up from
her feet; entity/firstwizard/FirstWizardChangeTrack.place puts them in the world. Angles for the arm and the head are
the player model's (radians; model x to her left, y down, z back, applied Z * Y * X as a ModelPart applies them). The
beats must match entity/firstwizard/spell/PhaseChange's constants: the contract check (checkFirstWizard) holds them
to each other.

  py -3 art/first_wizard/change/build_change.py        writes change-track.json beside this file
  ... --install                                        also writes the game's copy
  ... --check                                          fails if either copy is stale
"""
import argparse
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
TRACK = HERE/'change-track.json'
RUNTIME = ROOT/'src/main/resources/assets/elementalwands/cinematics/first_wizard_change.json'
RATE = 20

# ---------------------------------------------------------------- the clock (ticks), in step with PhaseChange
B = {
    'stagger': 28,                          # thrown back by the blow, and recovered
    'wrench': 18,                           # the left arm is wrenched back and out, and twitches
    'tear': 92,                             # it starts to come away from the shoulder on strands of ink
    'snap': 108,                            # the last strands snap: it drops
    'lands': 120,                           # about when it is down (it tumbles over and settles)
    'melt_from': 156, 'melted': 192,        # it melts into ink that soaks into the floor
    'bulge_from': 184,                      # her left flank starts to swell
    'bend_from': 194, 'bent': 256,          # her head bends the other way, to its peak
    'burst': 266, 'grow_from': 266,         # the swelling bursts and the tentacles erupt (TendrilFx)
    'lift_from': 284, 'lifted': 318,        # she lifts off the floor into the float
    'return': 320,                          # the camera starts back to the players' eyes
    'length': 340,                          # control returns; the tell and the full sweep follow (gameplay)
}

# ---------------------------------------------------------------- the player model (pixels, model axes)
PX = 1/16
MODEL_TOP = 1.501           # the model's origin over its feet, in blocks (LivingEntityRenderer's 1.501)
SHOULDER = (5., 2., 0.)     # the left arm's pivot
ARM_MID = (1., 4., 0.)      # the arm's middle from its pivot (wide arms; slim ones are half a pixel nearer)
ARM_HALF = 6.               # half the arm's length
ARM_THICK = 2.              # half its thickness (wide)
FACE = (0., -4., -4.)       # the middle of the face from the neck
BEND_PEAK = math.radians(64)    # the head's bend at its worst: further than a neck goes
BEND_TWO = math.radians(46)     # and where it stays through phase two: a little less, the face still readable

EASE = {'io': lambda f: f*f*(3-2*f), 'lin': lambda f: f, 'out': lambda f: 1-(1-f)**2, 'in': lambda f: f*f,
        'slow': lambda f: f*f*f*(f*(6*f-15)+10), 'in3': lambda f: f*f*f}


def clamp(v, lo=0., hi=1.): return max(lo, min(hi, v))
def smooth(v): return EASE['io'](clamp(v))
def add(a, b): return tuple(x+y for x, y in zip(a, b))
def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def mul(a, k): return tuple(x*k for x in a)
def mix(a, b, f): return tuple(x+(y-x)*f for x, y in zip(a, b))


def key(t, rows):
    """rows: (tick, value[, ease]); the ease names how the move arrives at that row. Values are numbers or tuples."""
    def lerp(a, b, f):
        if isinstance(a, tuple): return tuple(x+(y-x)*f for x, y in zip(a, b))
        return a+(b-a)*f
    if t <= rows[0][0]: return rows[0][1]
    for a, b in zip(rows, rows[1:]):
        if t <= b[0]:
            f = (t-a[0])/(b[0]-a[0])
            return lerp(a[1], b[1], EASE[b[2] if len(b) > 2 else 'io'](f))
    return rows[-1][1]


# ---------------------------------------------------------------- rotations (model axes, as ModelPart applies them)
def rx(a):
    c, s = math.cos(a), math.sin(a)
    return ((1, 0, 0), (0, c, -s), (0, s, c))


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, 0, s), (0, 1, 0), (-s, 0, c))


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def mm(a, b): return tuple(tuple(sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)) for i in range(3))
def mv(a, v): return tuple(sum(a[i][k]*v[k] for k in range(3)) for i in range(3))
def zyx(pitch, yaw, roll): return mm(rz(roll), mm(ry(yaw), rx(pitch)))


def frame_of(p):
    """A model point (pixels) in the copy's frame (blocks): left, up, ahead."""
    return (p[0]*PX, MODEL_TOP-p[1]*PX, -p[2]*PX)


# ---------------------------------------------------------------- the stagger
def stagger(t):
    """Thrown back by the blow (radians), and recovering by STAGGER."""
    span = B['stagger']
    if t >= span: return 0.
    return .42*math.sin(math.pi*t/span)*(smooth(t/4) if t < 4 else 1)


# ---------------------------------------------------------------- the arm, still on
# Each twitch: when, how hard, and which way it jerks (pitch, yaw, roll).
TWITCHES = [(31, .8, (1., -.6, -.5)), (42, .55, (-.6, .9, .4)), (50, 1., (.8, .4, -.9)), (59, .7, (-.9, -.7, .3)),
            (65, 1.1, (1., .6, -.8)), (74, .8, (-.5, -1., .6)), (80, 1.2, (.9, .5, -1.)), (87, 1., (-.8, .8, .7))]


def wrench(t):
    """The left arm while it is still on (model pitch, yaw, roll): yanked back and out at the shoulder, jerking in
    twitches, shivering as it strains; from TEAR it sags out of the socket, its top leaning away from her."""
    if t < B['wrench']: return (0., 0., 0.)
    u = EASE['out'](clamp((t-B['wrench'])/22))
    pitch, yaw, roll = .3*u, .05*u, -.36*u
    for tick, k, (dp, dy, dr) in TWITCHES:
        d = t-tick
        if d < 0: continue
        f = k*math.exp(-d/3.2)*math.cos(d*1.7)
        pitch += .34*f*dp; yaw += .45*f*dy; roll += .3*f*dr
    strain = clamp((t-(B['tear']-24))/24)
    pitch += .025*strain*math.sin(t*2.9); roll += .03*strain*math.sin(t*3.7+1)
    if t >= B['tear']:
        s = smooth((t-B['tear'])/(B['snap']-B['tear']))
        pitch, yaw, roll = pitch+(.08-pitch)*s, yaw+(.12-yaw)*s, roll+(.14-roll)*s
    return (pitch, yaw, roll)


def gap(t):
    """How far the arm's pivot has come away from the socket (model pixels: out, down)."""
    s = smooth((t-B['tear'])/(B['snap']-B['tear']))
    return (2.*s, 1.6*s, 0.)


def attached_centre(t):
    p, y, r = wrench(t)
    pivot = add(SHOULDER, gap(t))
    return frame_of(add(pivot, mv(zyx(p, y, r), ARM_MID)))


# ---------------------------------------------------------------- the arm, torn off
def fall():
    """The torn arm from SNAP until it has settled, a tick at a time: (middle in the frame, (pitch, twist, roll), turn).
    It drops slowly, drifting out and a little forward, tumbling about its length and leaning out; its hand hits the
    floor first and it topples out over it, slaps down along the floor and settles."""
    p0, y0, r0 = wrench(B['snap'])
    centre = attached_centre(B['snap'])
    v = (.026, -.014, .007)
    g = .019
    theta, omega, twist, turn = r0, .012, y0, 0.
    contact, pivot, slapped = None, None, None
    out = []
    for i in range(0, 60):
        if contact is None:
            v = (v[0], v[1]-g, v[2])
            centre = add(centre, v)
            theta += omega; omega += .005; twist += .085
            # The hand end's centre line, and how low its bottom corner reaches.
            end = centre[1]-ARM_HALF*PX*math.cos(theta)
            if end <= ARM_THICK*PX*math.sin(theta):
                contact = i
                pivot = (centre[0]-ARM_HALF*PX*math.sin(theta), 0., centre[2])
        elif slapped is None:
            omega += .028*math.sin(theta); theta += omega; twist += .05
            turn = .42*smooth((i-contact)/8)
            if theta >= math.pi/2:
                theta, slapped = math.pi/2, i
            centre = (pivot[0]+ARM_HALF*PX*math.sin(theta), ARM_THICK*PX*math.sin(theta)+ARM_HALF*PX*math.cos(theta), pivot[2])
        else:
            d = i-slapped
            bounce = math.exp(-d/2.2)*abs(math.sin(d*1.3))
            theta = math.pi/2-.09*bounce
            centre = (pivot[0]+ARM_HALF*PX*math.sin(theta), ARM_THICK*PX+.04*bounce, pivot[2])
            turn = .42
        out.append((centre, (p0*(1-smooth(i/10)), twist, theta), turn))
    return out, contact, slapped


FALL, CONTACT, SLAPPED = fall()
REST = FALL[-1][0]


def arm(t):
    """The torn arm: middle (frame), (pitch, twist, roll), turn, how flat it has melted."""
    if t < B['snap']:
        p, y, r = wrench(t)
        return attached_centre(t), (p, y, r), 0., 1.
    centre, rot, turn = FALL[min(len(FALL)-1, t-B['snap'])]
    m = melt(t)
    flat = 1-.8*smooth(m/.8)
    # Flattening onto the floor, then soaking into it.
    centre = (centre[0], centre[1]*flat-.07*smooth((m-.55)/.45), centre[2])
    return centre, rot, turn, flat


def melt(t): return EASE['io'](clamp((t-B['melt_from'])/(B['melted']-B['melt_from'])))


# ---------------------------------------------------------------- the seam, the strands and the socket
def seep(t):
    """Ink beading out of the seam at her shoulder while the arm strains."""
    return smooth((t-(B['wrench']+16))/(B['tear']-B['wrench']-16)) if t < B['snap'] else 0.


def strands(t):
    """Strands of ink holding the arm to the socket: how far they have stretched, 0 once they snap."""
    return smooth((t-B['tear'])/(B['snap']-B['tear'])) if B['tear'] <= t < B['snap'] else 0.


def socket(t):
    """Ink welling in the empty socket; it settles to a little as the flank starts to swell (the skin must read), and is
    gone when the burst takes the flank."""
    return smooth((t-B['tear'])/(B['snap']-B['tear']))*(1-.65*smooth((t-B['bulge_from'])/24))*(1-smooth((t-(B['burst']-6))/6))


# ---------------------------------------------------------------- the bulge and the veins
PULSES = [(198, .5), (213, .65), (228, .8), (243, .95), (258, 1.15)]     # PhaseChange.HEARTBEAT_FROM, every HEARTBEAT ticks


def bulge(t):
    """How far her left flank has swollen (0 none, about 1 at the burst): a slow rise in pulses, each surge bigger and
    only partly falling back; gone the moment it bursts."""
    if t < B['bulge_from'] or t >= B['burst']: return 0.
    u = (t-B['bulge_from'])/(B['burst']-B['bulge_from'])
    b = .62*EASE['in'](u)+.05*smooth((t-B['bulge_from'])/8)
    for tick, k in PULSES:
        d = t-tick
        if d < 0: continue
        b += .16*k*(smooth(d/5) if d < 5 else math.exp(-(d-5)/9)*.7+.3)
    # The last instant: it strains and splits.
    b += .12*smooth((t-(B['burst']-3))/3)
    return b


def veins(t):
    """The veins under the swelling's skin: they darken in with it, never so far that the skin is lost."""
    return .75*smooth((t-(B['bulge_from']+8))/60)*(1. if t < B['burst'] else 0.)


# ---------------------------------------------------------------- the head
HEAD_TWITCHES = [(236, .13, .1), (249, -.1, .08), (B['burst'], .09, .06)]     # tick, yaw jerk, extra bend


def bend(t):
    """Her head's bend away from the swelling, to her right (radians): slow and unhurried to further than a neck goes,
    a jerk now and then, then easing back a little after the burst to where it stays in phase two."""
    if t < B['bend_from']: return 0.
    b = BEND_PEAK*EASE['slow'](clamp((t-B['bend_from'])/(B['bent']-B['bend_from'])))
    if t >= B['burst']: b = BEND_PEAK+(BEND_TWO-BEND_PEAK)*smooth((t-B['burst']-6)/34)
    for tick, _, extra in HEAD_TWITCHES:
        d = t-tick
        if d >= 0: b += extra*math.exp(-d/2.4)*math.cos(d*1.4)
    return b


def head_yaw(t):
    y = 0.
    for tick, jerk, _ in HEAD_TWITCHES:
        d = t-tick
        if d >= 0: y += jerk*math.exp(-d/2.4)*math.cos(d*1.6)
    return y


def face(t):
    """The middle of her face in the frame, the head bent (as the renderer bends it: rolled to her right about the
    neck, the neck slipping toward that side)."""
    k = bend(t)/BEND_PEAK
    neck = (-1.*k, .5*k, 0.)
    return frame_of(add(neck, mv(zyx(0, head_yaw(t), -bend(t)), FACE)))


# ---------------------------------------------------------------- the lift
def lift(t): return EASE['slow'](clamp((t-B['lift_from'])/(B['lifted']-B['lift_from'])))


# ---------------------------------------------------------------- the camera
def look_at(eye, target):
    d = sub(target, eye)
    # The frame's yaw: 0 looks ahead of her (+Z), as a Minecraft yaw does along +Z; 90 looks to her right.
    return math.degrees(math.atan2(-d[0], d[2])), -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))


SHOULDER_AT = frame_of(SHOULDER)


def follow(start, end, target, rate):
    """A look that eases after a moving target, a tick at a time."""
    look, out = target(start), {}
    for t in range(start, end+1):
        look = add(look, mul(sub(target(t), look), rate))
        out[t] = look
    return out


FALL_LOOK = follow(92, 150, lambda t: add(arm(t)[0], (0, .08, 0)) if t >= B['snap'] else add(SHOULDER_AT, (.05, -.1, 0)), .22)


def shots():
    rest = REST
    return [
        # The blow: she is thrown back and her left arm is wrenched; medium, from her front left.
        (0, 'the blow', [(0, (2.3, 1.5, 3.4)), (40, (2.05, 1.5, 3.05), 'lin')], [(0, (.15, 1.15, 0.)), (40, (.2, 1.2, 0.), 'lin')], [(0, 56.)]),
        # Close on the arm, from her left: it twitches and strains against the sky, ink beading at the seam.
        (40, 'the arm', [(40, (2.05, 1.42, 1.05)), (92, (1.72, 1.36, .82), 'lin')], [(40, (.5, 1.08, 0.)), (92, (.52, 1.14, 0.), 'lin')], [(40, 50.)]),
        # Low by her feet: it tears away on strands of ink, drops and lands in front of the camera.
        (92, 'it falls', [(92, (2.05, .55, 1.9)), (150, (1.95, .5, 1.78), 'lin')], 'fall', [(92, 52.)]),
        # Over the arm on the floor: it lies there, darkens and melts into the floor.
        (150, 'it melts', [(150, add(rest, (.9, 1.18, 1.0))), (196, add(rest, (.78, 1.05, .86)), 'lin')],
         [(150, add(rest, (-.1, .05, 0))), (196, add(rest, (-.12, 0., 0)), 'lin')], [(150, 46.)]),
        # Her front: the left flank swells, the head starts to go the other way.
        (196, 'the bulge', [(196, (.55, 1.3, 2.55)), (232, (.45, 1.34, 2.2), 'lin')], [(196, (.2, 1.22, 0.)), (232, (.14, 1.3, 0.), 'lin')], [(196, 54.)]),
        # Her face while the head bends, too far: the camera keeps it in frame as it goes.
        (232, 'the face', [(232, (-.1, 1.5, 2.45)), (262, (-.16, 1.5, 2.15), 'lin')], 'face', [(232, 50.)]),
        # Her left side as the swelling bursts and the tentacles erupt; it draws back as they unfurl.
        (262, 'the burst', [(262, (2.3, 1.45, 2.2)), (292, (3.1, 1.6, 3.0), 'out')], [(262, (.35, 1.3, 0.)), (292, (.3, 1.75, 0.), 'io')], [(262, 64.)]),
        # Low in front of her: she lifts off the floor. The camera hands back from RETURN.
        (292, 'it rises', [(292, (2.4, .5, 2.7)), (B['length'], (2.75, .55, 3.15), 'lin')], [(292, (.1, 1.75, 0.)), (B['length'], (0., 2.0, 0.), 'lin')], [(292, 70.)]),
    ]


def camera(t, rows):
    shot = [s for s in rows if s[0] <= t][-1]
    eye = key(t, shot[2])
    if shot[3] == 'fall': look = FALL_LOOK[min(max(t, 92), 150)]
    elif shot[3] == 'face':
        neck = frame_of((0., -1., 0.))
        look = mix(neck, face(t), .6)
    else: look = key(t, shot[3])
    yaw, pitch = look_at(eye, look)
    return eye, yaw, pitch, float(key(t, shot[4]))


# ---------------------------------------------------------------- baking
def r3(v): return round(float(v), 3)
def r2(v): return round(float(v), 2)
def v3(p): return [r3(x) for x in p]


def unwrap(prev, cur):
    while cur-prev > 180: cur -= 360
    while cur-prev < -180: cur += 360
    return cur


CHANNELS = ('eye', 'yaw', 'pitch', 'fov', 'stagger', 'stagger_side', 'wrench', 'gap', 'seep', 'strands', 'socket',
            'arm', 'arm_rot', 'arm_turn', 'flat', 'melt', 'bulge', 'veins', 'bend', 'head_yaw', 'lift')


def bake_scene():
    rows = shots()
    n = B['length']
    ticks = {k: [] for k in CHANNELS}
    last_yaw = None
    for t in range(n+1):
        eye, yaw, pitch, fov = camera(t, rows)
        if last_yaw is not None: yaw = unwrap(last_yaw, yaw)
        last_yaw = yaw
        ticks['eye'].append(v3(eye)); ticks['yaw'].append(r2(yaw)); ticks['pitch'].append(r2(pitch)); ticks['fov'].append(r2(fov))
        s = stagger(t)
        ticks['stagger'].append(r3(s)); ticks['stagger_side'].append(r3(s*.7))
        ticks['wrench'].append(v3(wrench(t) if t < B['snap'] else (0, 0, 0))); ticks['gap'].append(v3(gap(t)))
        ticks['seep'].append(r3(seep(t))); ticks['strands'].append(r3(strands(t))); ticks['socket'].append(r3(socket(t)))
        centre, rot, turn, flat = arm(t)
        ticks['arm'].append(v3(centre)); ticks['arm_rot'].append(v3(rot)); ticks['arm_turn'].append(r3(turn)); ticks['flat'].append(r3(flat))
        ticks['melt'].append(r3(melt(t)))
        ticks['bulge'].append(r3(bulge(t))); ticks['veins'].append(r3(veins(t)))
        ticks['bend'].append(r3(bend(t))); ticks['head_yaw'].append(r3(head_yaw(t)))
        ticks['lift'].append(r3(lift(t)))
    cuts = sorted({s[0] for s in rows if s[0] > 0})
    return {'beats': B, 'cuts': cuts, 'shots': {s[1]: s[0] for s in rows}, 'hits': [], 'ticks': ticks}


def bake():
    return {
        'frame': 'the copy at the change start: +X her left, +Z ahead of her, y up from her feet; '
                 'arm and head angles are the player model\'s (radians, x left, y down, z back, applied Z * Y * X)',
        'bend_two': r3(BEND_TWO),
        'scene': bake_scene(),
    }


def packed(track):
    def scene_rows(s):
        head = ',\n'.join('    %s: %s' % (json.dumps(k), json.dumps(v, separators=(',', ':'))) for k, v in s.items() if k != 'ticks')
        ticks = ',\n'.join('      %s: %s' % (json.dumps(k), json.dumps(v, separators=(',', ':'))) for k, v in s['ticks'].items())
        return '{\n'+head+',\n    "ticks": {\n'+ticks+'\n    }\n  }'
    rows = []
    for k, v in track.items():
        rows.append('  %s: %s' % (json.dumps(k), scene_rows(v) if k == 'scene' else json.dumps(v, separators=(',', ':'))))
    return ('{\n'+',\n'.join(rows)+'\n}\n').encode()


def checks():
    """What the scene must do, whatever the tuning: the arm lands by her feet and clear of them, and lies on the floor
    before it melts; everything is over before the camera hands back."""
    assert CONTACT is not None and SLAPPED is not None, 'the torn arm never lands'
    assert B['snap']+SLAPPED < B['melt_from'], 'the arm is still falling when it starts to melt'
    lying = FALL[-1]
    assert abs(lying[0][1]-ARM_THICK*PX) < .01 and abs(lying[1][2]-math.pi/2) < .01, 'the arm does not lie on the floor'
    assert .5 < REST[0] < 1.6 and abs(REST[2]) < .6, f'the arm lands away from her feet: {REST}'
    assert bulge(B['burst']-1) > .9 and bulge(B['burst']) == 0, 'the bulge does not swell to the burst'
    assert abs(bend(B['length'])-BEND_TWO) < .005, 'the head does not settle where phase two keeps it'
    assert lift(B['length']) == 1, 'she is not lifted when control returns'
    assert B['tear'] < B['snap'] < B['lands'] < B['melt_from'] < B['melted'] < B['burst'] < B['lift_from'] < B['lifted'] <= B['return'] < B['length']


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--install', action='store_true'); ap.add_argument('--check', action='store_true')
    args = ap.parse_args()
    checks()
    track = bake(); data = packed(track)
    targets = [TRACK]+([RUNTIME] if args.install or args.check else [])
    if args.check:
        stale = [str(p.relative_to(ROOT)) for p in targets if not p.exists() or p.read_bytes() != data]
        if stale: raise SystemExit('Stale or missing: '+', '.join(stale))
    else:
        for p in targets: p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
    s = track['scene']
    print(('CHECK OK' if args.check else 'INSTALLED' if args.install else 'BUILT')
          + f": the change {s['beats']['length']} ticks ({s['beats']['length']/RATE:.1f} s, {len(s['cuts'])+1} shots);"
          + f" the arm lands at tick {B['snap']+SLAPPED}, at ({REST[0]:.2f}, {REST[1]:.3f}, {REST[2]:.2f})")


if __name__ == '__main__':
    main()
