"""Reworked Hollow Crawler clips, sampled by build_preview.py's crawler emitter.

Each entry returns (sampler, extra key times). A sampler gives the crawler pose dict at a clip
time: root, torso position/rotation, skull, jaw and the two claws' hand states.
"""
import math
from motion import keys, shaped, envelope, shake, tremor

# The approved lunge's raking claw at the server's hit frame (0.58 s): CRAWLER_SOCKET in the
# generated HollowUndeadClips.java comes from this pose, so it is pinned exactly.
HIT = .58
HIT_HAND = [3.6875675675675677, 1.272432432432433, -14.427567567567568]
HIT_PITCH = 15.622822822822819
# Where the body slides after the server's hop at clip tick 9 (px along -Z): vanilla mob physics
# (one ground-friction tick, three airborne ticks, then friction brakes it), shown a tick late
# as the client interpolates. Claws that are down while the body slides rake backward by the
# same amount, so they stay put in the world instead of skating.
SLIDE = [(.50, 0), (.55, 4.8), (.60, 7.4), (.65, 9.8), (.70, 12.0), (.75, 14.0), (.80, 15.1),
         (.85, 15.6), (.90, 16.0), (.95, 16.1)]


def slide(t):
    return keys(t, [(a, v, 'linear') for a, v in SLIDE])


def _turn(q, rot):
    # Same order as GeckoLib/viewer about the torso origin: X, then Y, then Z (X/Y negated).
    ax, ay, az = -math.radians(rot[0]), -math.radians(rot[1]), math.radians(rot[2])
    x, y, z = q
    y, z = y*math.cos(ax)-z*math.sin(ax), y*math.sin(ax)+z*math.cos(ax)
    x, z = x*math.cos(ay)+z*math.sin(ay), -x*math.sin(ay)+z*math.cos(ay)
    x, y = x*math.cos(az)-y*math.sin(az), x*math.sin(az)+y*math.cos(az)
    return [x, y, z]


def body_pivot(rot, lift=0.):
    """Torso position for a rotation that pitches the body about its dragging pelvis (which
    stays on the ground) while it turns about the middle of the spine."""
    pelvis, middle = [0, 1.5, 9], [0, 3.5, 3]
    p = _turn(pelvis, rot); m = _turn(middle, rot)
    # Keep the pelvis height and the spine's middle in line, front to back and side to side.
    return [middle[0]-m[0], pelvis[1]-p[1]+lift, pelvis[2]-p[2]]


def attack(kit):
    """Press down, rear up on the left claw with the right one high, shove off into the hop
    and rake the right claw down onto the target, crash, drag the claws back while the body
    slides over them, then gather itself."""
    base, hand_state, grip, rest = kit['base'], kit['hand_state'], kit['grip'], kit['rest']

    def pose(t):
        p = base()
        rot = keys(t, [(0, [0, 0, 0]), (.18, [3, 0, 0]),                    # presses down
                       (.40, [-14, 8, -5], 'ease'),                        # rears, right shoulder back
                       (.47, [-12, 6, -4]),                                # shoves off the left claw
                       (HIT, [9, -9, 3], 'in'),                            # throws itself into the rake
                       (.66, [6, -7, 2], 'out'),                           # chest crashes down
                       (.82, [2, -3, 1], 'ease'), (1.2, [0, 0, 0], 'slow')])
        lift = keys(t, [(0, 0), (.18, -.35), (.40, .6, 'ease'), (.47, .5), (.55, .5),
                        (.64, -.7, 'in'), (.74, -.1, 'settle'), (1.2, 0, 'slow')])
        lift += shake(t, .64, .3, 9, 2.5)*.25
        p['torso_rot'] = rot
        p['torso_pos'] = body_pivot(rot, lift)
        p['skull'] = [v+d for v, d in zip(
            keys(t, [(0, [-3, 0, 2.5]), (.18, [7, 0, 1]), (.40, [-18, -9, 2], 'ease'),
                     (.47, [-16, -7, 2]), (HIT, [-4, 6, -2], 'in'), (.68, [5, 3, -5], 'out'),
                     (.85, [-8, -4, 0], 'ease'), (1.25, [-3, 0, 2.5], 'slow')]),
            [shake(t, .66, .35, 6, 2)*6, 0, tremor(t, 14)*envelope(t, .88, .05, .2, .1)*4])]
        jaw = keys(t, [(0, 4), (.18, 9), (.40, 30, 'out'), (.54, 32), (HIT, 2, 'snap'),
                       (.68, 18, 'out'), (.82, 8), (1.25, 4, 'slow')])
        jaw += abs(math.sin(math.tau*7*(t-.9)))*envelope(t, .9, .02, .16, .06)*10   # clacks
        p['jaw'] = [jaw, 0, 0]

        # ---- Right claw: up and back over the shoulder, raked down onto the target, dragged
        # back through the dirt as the body slides over it, then set back down at rest ----
        cocked, loaded = [5.2, 13.2, -6.4], [5.6, 14.0, -5.0]
        raked = [3.6, .55, HIT_HAND[2]+slide(.95)-slide(HIT)]
        if t < .20:
            right = list(rest[1])
        elif t < .40:
            f = shaped(t, .20, .40, 'out'); right = [a+(b-a)*f for a, b in zip(rest[1], cocked)]
        elif t < .47:
            f = shaped(t, .40, .47); right = [a+(b-a)*f for a, b in zip(cocked, loaded)]
        elif t <= HIT:
            f = shaped(t, .47, HIT, 'in'); right = [a+(b-a)*f for a, b in zip(loaded, HIT_HAND)]
            # The claw arcs over and comes down onto the target instead of cutting straight.
            right[1] += 3.5*math.sin(f*math.pi)*(1-f)
        elif t < .95:
            right = [HIT_HAND[0]+(raked[0]-HIT_HAND[0])*shaped(t, HIT, .9, 'out'),
                     HIT_HAND[1]+(raked[1]-HIT_HAND[1])*shaped(t, HIT, .66, 'out'),
                     HIT_HAND[2]+slide(t)-slide(HIT)]
        else:
            f = shaped(t, 1.0, 1.22); right = [a+(b-a)*f for a, b in zip(raked, rest[1])]
            right[1] += 1.6*math.sin(f*math.pi)
        open_ = envelope(t, .2, .18, .2, .1, 'out', 'in')
        dig = envelope(t, HIT-.04, .04, .3, .25, 'snap', 'ease')
        fingers = [[a*(1-dig)+b*dig for a, b in zip(grip(.5*(1-open_), open_), [-6, 72, 52])] for _ in range(4)]
        pitch = keys(t, [(0, 0), (.2, 0), (.40, -48, 'out'), (.47, -55), (HIT, HIT_PITCH, 'in'),
                         (.9, 32, 'ease'), (1.0, 30), (1.22, 0, 'ease')])
        splay = 5+9*open_
        p['hands'][1] = hand_state(right, fingers, splay, rot=(pitch, 0, 0), side=1)
        if abs(t-HIT) < 1e-6:
            p['hands'][1]['pos'] = list(HIT_HAND); p['hands'][1]['rot'] = [HIT_PITCH, 5.0, 0]

        # ---- Left claw: bears the weight, shoves off, flies with the hop, slaps down ahead
        # and brakes the slide, then walks back to rest ----
        landing, gather = [-5.3, .55, -13.4], [-5.0, .55, -9.4]
        if t < .17:
            # Gathers itself: the left claw steps in under the shoulder to bear the rear.
            f = shaped(t, .02, .17); left = [a+(b-a)*f for a, b in zip(rest[-1], gather)]
            left[1] += 1.2*math.sin(f*math.pi)
        elif t < .47:
            left = list(gather)
        elif t < .65:
            f = shaped(t, .47, .65, 'in')
            start = [gather[0], gather[1], gather[2]+slide(t)]   # the push point slides away
            left = [a+(b-a)*f for a, b in zip(start, landing)]
            left[1] += 2.2*math.sin(f*math.pi)
        elif t < 1.0:
            left = [landing[0], landing[1], landing[2]+slide(t)-slide(.65)]
        else:
            planted = [landing[0], landing[1], landing[2]+slide(1)-slide(.65)]
            f = shaped(t, 1.0, 1.25); left = [a+(b-a)*f for a, b in zip(planted, rest[-1])]
            left[1] += 1.4*math.sin(f*math.pi)
        flight = envelope(t, .02, .06, .02, .07, 'out', 'in')+envelope(t, .47, .08, .06, .04, 'out', 'in') \
            + envelope(t, 1.0, .1, .05, .1, 'out', 'in')
        press = envelope(t, .3, .12, .05, .05)
        fingers = [grip(.5+.4*press-.2*f, flight) for f in range(4)]
        p['hands'][-1] = hand_state(left, fingers, 5+8*flight, side=-1,
                                    rot=(keys(t, [(0, 0), (.47, 0), (.55, -20, 'out'), (.65, 6, 'in'),
                                                  (.75, 0)]), 0, 0))
        return p
    # Denser keys through the shove, rake and crash.
    return pose, [HIT]+[.46+i/60 for i in range(14)]


def rise(kit):
    """Scrabbles out of the ground: one claw then the other punches up, flails and digs in, two
    desperate hauls drag the skull and ribs out, a stall, then the pelvis is dragged clear and it
    flops flat, shakes off the dirt and looks about."""
    base, hand_state, grip, rest = kit['base'], kit['hand_state'], kit['grip'], kit['rest']
    torso_point = kit['torso_point']

    def claw(t, side, out, slam, shoulder):
        """Burst from under the ground at `out`, flail, slam down onto its rest spot at `slam`."""
        home = list(rest[side])
        under = [shoulder[0]+side*.6, shoulder[1]-3, shoulder[2]-5]
        up = [side*5.4, 4.6, home[2]+1.5]
        if t < out:
            return under, 0, 0.
        if t < slam-.1:
            f = shaped(t, out, out+.2, 'out')
            pos = [a+(b-a)*f for a, b in zip(under, up)]
            # Flails: a twitch up and to the side before it comes down.
            wob = envelope(t, out+.17, .04, .03, .05, 'snap', 'ease')
            pos = [pos[0]+side*.8*wob, pos[1]+.9*wob, pos[2]-.6*wob]
            return pos, 1., shaped(t, out, out+.12, 'snap')
        f = shaped(t, slam-.1, slam, 'in')
        pos = [a+(b-a)*f for a, b in zip(up, home)]
        return pos, 1-f, 1-shaped(t, slam, slam+.1)

    def pose(t):
        p = base()
        # Hauls in lurches: up and forward toward the claws, a stall, then again.
        p['torso_rot'] = keys(t, [(0, [-34, 0, 6]), (.56, [-32, 4, 4]), (.84, [-22, -6, -5], 'out'),
                                  (.98, [-21, -5, -4]), (1.30, [-8, 6, 4], 'out'), (1.42, [-7, 5, 3]),
                                  (1.62, [1.5, 0, 0], 'in'), (1.75, [0, 0, 0], 'out')])
        p['torso_pos'] = keys(t, [(0, [0, -14, 4.5]), (.56, [0, -13.4, 4.3]), (.84, [-.4, -5.2, 2.8], 'out'),
                                  (.98, [-.3, -4.2, 2.7]), (1.30, [.3, -1.2, 1.2], 'out'), (1.42, [.3, -.9, 1]),
                                  (1.62, [0, -.35, 0], 'in'), (1.75, [0, 0, 0], 'out')])
        stall = envelope(t, .86, .04, .1, .04)
        p['torso_rot'] = [p['torso_rot'][0]+tremor(t, 13)*2*stall, p['torso_rot'][1], p['torso_rot'][2]+tremor(t, 11, 1)*2*stall]
        p['skull'] = [v+d for v, d in zip(
            keys(t, [(0, [-30, 0, 0]), (.56, [-30, 10, 0]), (.84, [-38, -16, 8], 'out'), (.98, [-36, -12, 6]),
                     (1.30, [-24, 14, -8], 'out'), (1.62, [6, 0, 0], 'in'), (1.9, [-3, 0, 2.5], 'ease')]),
            [0, 26*shake(t, 1.62, .28, 7, 2.2), 0])]
        jaw = keys(t, [(0, 4), (.5, 6), (.8, 30, 'out'), (.98, 12), (1.25, 28, 'out'), (1.5, 6), (1.9, 4)])
        jaw += abs(math.sin(math.tau*7*(t-1.62)))*envelope(t, 1.62, .02, .2, .06)*10
        p['jaw'] = [jaw, 0, 0]
        for side, out, slam in ((1, .08, .4), (-1, .3, .58)):
            shoulder = torso_point([side*4.4, 5.5, -3], p['torso_pos'], p['torso_rot'])
            pos, flight, splay = claw(t, side, out, slam, shoulder)
            reach = [b-a for a, b in zip(shoulder, pos)]; dist = math.sqrt(sum(v*v for v in reach))
            if dist > 11.2:
                pos = [a+v*11.2/dist for a, v in zip(shoulder, reach)]
            dig = envelope(t, slam, .05, .9, .3) if t >= slam else 0
            fingers = [[a*(1-dig)+b*dig for a, b in zip(grip(.5+.3*math.sin(t*9-f*.8)*(1-dig), flight),
                                                          grip(1, 0))] for f in range(4)]
            p['hands'][side] = hand_state(pos, fingers, 5+9*splay, side=side,
                                          rot=(-30*flight*(t < slam), 0, 0))
        return p
    return pose, []


def death(kit):
    """Jolted up by the hit, it scrabbles in its death throes, claws raking the dirt and skull
    thrashing; then the elbows buckle and the chest slams down, the skull bounces, the body
    rolls a little onto its side and the claws slide open. One last twitch, then still."""
    base, hand_state, grip, rest = kit['base'], kit['hand_state'], kit['grip'], kit['rest']
    torso_point = kit['torso_point']

    def pose(t):
        p = base()
        rot = keys(t, [(0, [0, 0, 0]), (.1, [-12, 0, 2], 'snap'), (.45, [-8, 4, -3], 'ease'),
                       (.75, [7, -2, 10], 'in'), (.84, [4, -2, 11], 'out'), (.95, [6, -2, 13], 'in'),
                       (1.6, [6, -2, 14], 'slow')])
        throes = envelope(t, .1, .05, .25, .1)
        rot = [rot[0]+tremor(t, 12)*2.5*throes, rot[1]+tremor(t, 9, 1)*3*throes, rot[2]]
        lift = keys(t, [(0, 0), (.1, 1.1, 'snap'), (.45, .9), (.75, -1.1, 'in'), (.84, -.6, 'out'),
                        (.95, -1.1, 'in'), (1.6, -1.1)])
        p['torso_rot'] = rot
        p['torso_pos'] = body_pivot(rot, lift)
        p['skull'] = [v+d for v, d in zip(
            keys(t, [(0, [-3, 0, 2.5]), (.08, [-22, 0, 0], 'snap'), (.45, [-12, 0, 0]),
                     (.76, [18, 10, 12], 'in'), (.86, [6, 18, 20], 'out'), (1.0, [14, 26, 30], 'ease'),
                     (1.6, [14, 26, 30])]),
            [0, 22*math.sin(math.tau*4.5*(t-.12))*throes, 0])]
        jaw = keys(t, [(0, 4), (.08, 26, 'snap'), (.3, 12), (.45, 20), (.76, 6, 'in'), (.86, 24, 'out'),
                       (1.1, 28, 'ease'), (1.6, 28)])
        p['jaw'] = [jaw+abs(math.sin(math.tau*6*(t-.15)))*throes*8, 0, 0]
        for side in (-1, 1):
            home = list(rest[side])
            final = [-7.2, .5, -10.2] if side < 0 else [7.4, .5, -6.4]
            # Death throes: each claw rakes back through the dirt in turn.
            rakes = [(.12, .14), (.30, .13)] if side > 0 else [(.2, .14), (.38, .1)]
            pos = list(home)
            for start, length in rakes:
                f = envelope(t, start, length*.4, 0, length*.6, 'out', 'in')
                pos = [pos[0]+side*.6*f, pos[1]+1.6*f, pos[2]+2.2*shaped(t, start, start+length)]
            # The elbows buckle and the claws slide out to the sides as it drops.
            slide = shaped(t, .45, 1.05, 'ease')
            base_pos = [home[0], home[1], home[2]+2.2*len(rakes)*shaped(t, .1, .45)]
            pos = [p0+(fv-b0)*slide+(pp-b0)*(1-slide) for p0, b0, fv, pp in zip(base_pos, base_pos, final, pos)]
            shoulder = torso_point([side*4.4, 5.5, -3], p['torso_pos'], p['torso_rot'])
            reach = [b-a for a, b in zip(shoulder, pos)]; dist = math.sqrt(sum(v*v for v in reach))
            if dist > 11.2:
                pos = [a+v*11.2/dist for a, v in zip(shoulder, reach)]
            clench = keys(t, [(0, .5), (.08, 1, 'snap'), (.45, .9), (.8, .3), (1.6, 0, 'slow')])
            twitch = envelope(t, 1.08, .05, .02, .2, 'snap', 'ease') if side > 0 else 0
            opened = shaped(t, .8, 1.4)
            fingers = [[a*(1-opened)+b*opened for a, b in zip(grip(clench+.8*twitch, 0), [-4, 14+2*f, 10])]
                       for f in range(4)]
            p['hands'][side] = hand_state(pos, fingers, 5+6*throes, side=side,
                                          rot=(-15*throes, 12*shaped(t, .75, 1.2), 0))
        return p
    return pose, []


REWORKED = {'attack': attack, 'rise': rise, 'death': death}

# Review notes shown in the preview (times in seconds of clip time).
NOTES = {
    'attack': {
        'summary': 'A desperate lunge. It gathers itself, rears up on the left claw with the right one '
                   'cocked high, shoves off into the server\'s hop and rakes the claw down onto you, '
                   'crashes, then drags both claws back through the dirt as its body slides over them.',
        'beats': [(0, 'Gathers: left claw steps in, chest presses down'),
                  (.20, 'Rears up; right claw whips up high'),
                  (.40, 'Holds it there, jaw gaping'),
                  (.47, 'Shoves off the left claw'),
                  (.58, 'Rake lands, jaw snaps shut'),
                  (.64, 'Chest crashes; left claw slaps down ahead'),
                  (.70, 'Claws rake back while the body slides'),
                  (.88, 'Skull rattles, jaw clacks'),
                  (1.0, 'Walks its claws back to rest')],
        'server': [(.45, 'hop forward (tick 9)'), (.60, 'hit (tick 12)')]},
    'rise': {
        'summary': 'A desperate scramble out of the ground: one claw then the other punches up, flails '
                   'and digs in, two hauls drag the skull and ribs out with a strained stall between, then '
                   'it drags its pelvis clear, flops flat and rattles the dirt off its skull.',
        'beats': [(0, 'Buried'),
                  (.08, 'Right claw punches up and flails'),
                  (.30, 'Left claw punches up; right one digs in'),
                  (.56, 'First haul: the skull breaks out, jaw gaping'),
                  (.58, 'Left claw digs in'),
                  (.86, 'Stalls, straining'),
                  (.98, 'Second haul: the ribs come out'),
                  (1.42, 'Drags its pelvis clear and flops flat'),
                  (1.62, 'Shakes the dirt off its skull'),
                  (1.80, 'Settles, looking about')],
        'server': [(1.14, 'soil stops bursting'), (1.90, 'can act (tick 38)')]},
    'death': {
        'summary': 'Jolted up by the hit, it scrabbles in its death throes, claws raking the dirt and '
                   'skull thrashing, until the elbows buckle and the chest slams down. The skull bounces, '
                   'the body rolls a little onto its side, the claws slide open, one last twitch.',
        'beats': [(0, 'Jolted up by the hit, jaw gaping'),
                  (.12, 'Death throes: claws rake, skull thrashes'),
                  (.45, 'Elbows buckle'),
                  (.75, 'Chest slams down; skull bounces'),
                  (.95, 'Rolls a little onto its side; jaw falls open'),
                  (1.08, 'Last twitch of the right claw'),
                  (1.3, 'Still')],
        'server': [(1.90, 'body removed (tick 38)')]},
}
