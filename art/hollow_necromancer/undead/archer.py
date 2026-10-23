"""Reworked Risen Archer clips. See README "Reworked clips" for what each does."""
import math
from motion import keys, shaped, envelope, shake, tremor
from rig import Baker, within
from upright import add, sub, mul, dot, norm, lerp, mat, apply, euler, rot

# The approved draw at the server's release frame (2.6 s): ARCHER_SOCKET in the generated
# HollowUndeadClips.java comes from the bow hand here, so it is pinned exactly.
RELEASE = 2.6
BOW_HAND = [-13, 25, -3.2]
BOW_WRIST = [0, 0, -90]
# Where the drawing fingers anchor the nock beside the jaw, and the preview arrow's flight line.
ANCHOR_HAND, ANCHOR_ROLL = [-0.094, 25.3, -2.883], math.radians(38.301848990571216)
FLIGHT_FROM, FLIGHT_TO = [-1.8, 25.3, -2.3], [-14, 25, -3.8]
QUIVER = [2.5, 28.4, 2.5]          # the fletchings behind the right shoulder
REST_BOW = [-3.5, 17.2, -2.9]      # idle bow hand
REST_DRAW = [4.8, 15, -1.5]        # idle drawing hand


def nock_offset(roll):
    """Nock position relative to the drawing hand for a wrist roll (radians)."""
    return [-1.7*math.cos(roll)-.6*math.sin(roll), 0, 1.7*math.sin(roll)-.6*math.cos(roll)]


def _mm(a, b):
    return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _t(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def slerp(a, b, f):
    a, b = norm(a), norm(b)
    angle = math.acos(max(-1, min(1, dot(a, b))))
    if angle < 1e-6:
        return b
    s = math.sin(angle)
    return norm(add(mul(a, math.sin((1-f)*angle)/s), mul(b, math.sin(f*angle)/s)))


def bow_parts(clip, t, bow_hand, bow_wrist, nock, tension, wobble, arrow):
    """Bow, string and arrow keys. The bow rides the bow hand (its rest grip is the original
    hand pose), the string meets the nock while drawn, and the arrow is keyed as given."""
    carry = _mm(mat(bow_wrist), _t(mat(BOW_WRIST)))
    grip = add(bow_hand, apply(carry, [-1, 0, -.6]))
    origin = add(bow_hand, apply(carry, [-.4, 1, -.6]))
    bow = _mm(carry, mat([0, -90, 0]))
    clip.pose('bow', t, origin, euler([bow[i][0] for i in range(3)], [bow[i][1] for i in range(3)]))
    tip = lambda v: add(origin, apply(bow, v))
    relaxed = tip([0, -1, 1.2])
    # The string bows back to the nock while drawn and shivers along the shot after the loose.
    center = add(lerp(relaxed, nock, tension), mul(apply(bow, [0, 0, 1]), wobble))
    for part, ty in (('string_top', 7.1), ('string_bottom', -7.1)):
        end = tip([0, ty-1, 1.2]); v = sub(center, end)
        clip.pose(part, t, end, rot(v)); clip.put(part, 'scale', t, [1, math.sqrt(dot(v, v)), 1])
    position, direction, visible = arrow
    if direction is None:
        direction = norm(sub(grip, nock))
    clip.pose('arrow', t, position, rot(direction))
    clip.put('arrow', 'scale', t, [1, 1, 1] if visible else [0, 0, 0])


def attack(rig):
    """Lock eyes on the prey and plant a side-on stance, draw an arrow from the quiver, nock it,
    raise and draw to the jaw, hold, loose (the string snaps and shivers, the bow kicks), hold
    the follow-through, then lower."""
    hy = rig.hip_y
    shot = norm(sub(FLIGHT_TO, FLIGHT_FROM))

    def pose(t):
        P = {}
        # ---- Body: settles into the stance, turns to the quiver, sits into the draw ----
        P['hips'] = keys(t, [(0, [0, hy, 0]), (.32, [-.5, hy-.5, -.1], 'ease'), (.8, [-.2, hy-.4, 0]),
                             (1.7, [-.6, hy-.6, 0], 'ease'), (2.3, [-.7, hy-.7, .1]),
                             (3.0, [-.6, hy-.5, 0]), (3.5, [0, hy, 0], 'slow')])
        P['body'] = keys(t, [(0, [0, 0, 0]), (.32, [2, -4, 0]), (.78, [4, 12, -3], 'ease'),
                             (1.08, [3, 8, -2]), (1.5, [0, -2, 0], 'ease'),
                             (2.2, [-3, -3, 1], 'ease'), (RELEASE, [-3.5, -3, 1]),
                             (2.68, [-2, -2, 1], 'out'), (3.0, [-1, -2, 1]), (3.5, [0, 0, 0], 'slow')])
        # ---- Head: locks on the prey while the body turns under it (the server turns the body
        # side-on in about 0.3 s), glances at the quiver, dips to the arrow at anchor ----
        yaw = keys(t, [(0, 0), (.3, -78, 'out'), (3.05, -78), (3.55, 0, 'slow')])
        look = keys(t, [(0, [1, 0, 0]), (.3, [-2, 0, 0]), (.6, [4, 0, -2]), (.9, [6, 0, -3]),
                        (1.3, [2, 0, 0]), (2.15, [3, 0, 1], 'ease'), (RELEASE, [4, 0, 1]),
                        (2.7, [3, 0, 1], 'out'), (3.05, [1, 0, 0]), (3.55, [0, 0, 0], 'slow')])
        P['skull'] = [look[0], yaw+look[1], look[2]]
        P['jaw'] = [keys(t, [(0, 4), (.3, 6), (1.6, 3, 'ease'), (2.2, 0, 'ease'), (RELEASE, 0),
                             (2.66, 11, 'out'), (2.9, 6), (3.5, 4, 'slow')]), 0, 0]

        # ---- Bow hand: set before the chest, pushed out at the prey through the draw, held
        # with a strained tremor, kicked forward by the loose, then lowered ----
        set_ = [-6.6, 20.8, -5.2]
        # The bow swings out a little as the front foot steps, so the thigh passes inside it.
        bow = keys(t, [(0, REST_BOW), (.2, add(REST_BOW, [-1.6, .4, -1.0]), 'out'), (.95, add(REST_BOW, [-1.4, .4, -.9])),
                       (1.32, set_, 'ease'), (1.55, set_),
                       (2.0, [-12.4, 24.6, -3.4], 'ease'), (2.27, BOW_HAND, 'ease'), (RELEASE, BOW_HAND),
                       (2.68, add(BOW_HAND, [-1.2, -.7, -.3]), 'out'), (2.98, add(BOW_HAND, [-.9, -.8, -.2]), 'ease'),
                       (3.5, REST_BOW, 'slow')])
        strain = envelope(t, 2.05, .2, .33, .02)
        bow = add(bow, [0, tremor(t, 9)*.12*strain, tremor(t, 8, 2)*.1*strain])
        tip = keys(t, [(0, 0), (RELEASE, 0), (2.7, 16, 'out'), (2.95, 10, 'ease'), (3.5, 0, 'slow')])
        bow_wrist = [0, 0, -90+tip]
        if abs(t-RELEASE) < 1e-6:
            bow, bow_wrist = list(BOW_HAND), list(BOW_WRIST)

        # ---- Drawing hand: to the quiver, the arrow out tail-first, onto the string, drawn to
        # the jaw, loosed: the fingers fly open and the hand snaps back past the ear ----
        carry = _mm(mat(bow_wrist), _t(mat(BOW_WRIST)))
        string_rest = add(bow, apply(carry, [.8, 0, -.6]))      # the relaxed string's nocking point
        roll = 0.
        lift_off = add(string_rest, [1.7, 1.2, .6])
        if t < 1.42:
            # Up in front of the shoulder and over it to the quiver; the arrow comes out high
            # and is carried out in front of the shoulder, clear of the skull, to the string.
            hand = keys(t, [(0, REST_DRAW), (.3, REST_DRAW), (.55, [3.8, 26.6, -6.2], 'ease'),
                            (.75, add(QUIVER, [.4, .2, 0]), 'ease'), (.82, QUIVER),
                            (1.08, [3.6, 33.2, .6], 'ease'), (1.25, [4.6, 27.5, -6.8], 'ease'),
                            (1.42, lift_off, 'ease')])
        elif t <= RELEASE:
            draw = keys(t, [(1.55, 0), (1.75, .12, 'ease'), (2.25, 1, 'ease')])
            anchor_nock = add(ANCHOR_HAND, nock_offset(ANCHOR_ROLL))
            nock = lerp(string_rest, anchor_nock, draw)
            nock = lerp(add(lift_off, nock_offset(0)), nock, shaped(t, 1.42, 1.55))
            roll = ANCHOR_ROLL*shaped(t, 1.6, 2.25)
            hand = sub(nock, nock_offset(roll))
        else:
            # Snaps back past the ear, then comes down out at the side, clear of the ribs.
            back = keys(t, [(RELEASE, [0, 0, 0]), (2.66, [1.6, .5, -.3], 'snap'), (2.98, [1.8, .4, -.4]),
                            (3.24, sub([6.2, 21.5, -6.2], ANCHOR_HAND), 'ease'),
                            (3.5, sub(REST_DRAW, ANCHOR_HAND), 'ease')])
            hand = add(ANCHOR_HAND, back)
            roll = ANCHOR_ROLL*(1-shaped(t, 3.0, 3.45))
        hook = keys(t, [(0, .2), (.6, 0), (.82, .55, 'out'), (1.42, .55), (1.55, .9), (RELEASE, .9),
                        (2.63, 0, 'snap'), (3.5, .2, 'slow')])
        pinch = envelope(t, .77, .05, .55, .1)
        curls = [[-10-35*hook-25*pinch, -12-62*hook-20*pinch, -8-40*hook] if f < 2 else
                 [-10-35*hook, -12-62*hook, -8-40*hook] if f < 3 else
                 [-10-12*hook, -12-20*hook, -8-14*hook] for f in range(4)]
        aim = shaped(t, 1.3, 1.7)*(1-shaped(t, 3.0, 3.5))
        reach = envelope(t, .3, .35, .3, .2, 'ease', 'ease')
        # The drawing elbow sits forward of the shoulder at shoulder height through the draw
        # (as approved), up and out while reaching to the quiver, back by the side at rest.
        # The elbow goes up and out for the quiver, forward for the draw and stays forward as the
        # arm lowers, swinging back only at the end. A path that circles the arm instead winds
        # its keys a turn, which the crossfade into the next clip would spin out.
        pole = keys(t, [(0, [1, .2, 1]), (.3, [1, .2, 1]), (.6, [1, .7, -.4], 'ease'), (.95, [1, .7, -.4]),
                        (1.1, [.5, .05, -.87], 'ease'), (3.0, [.5, .05, -.87]), (3.22, [.3, .2, -.9], 'ease'), (3.5, [1, .2, 1], 'ease')])
        P['right'] = {'hand': hand, 'pole': pole,
                      'wrist': [math.degrees(roll), -90*(1-aim)+60*reach*(1-aim), -90*aim],
                      'curls': curls, 'splay': [(1.5-f)*6*.8*(1-.6*hook) for f in range(4)],
                      'thumb': ([-20-25*pinch, -30, -35], [-25-30*pinch, 0, 0]),
                      'ankle': keys(t, [(0, [2.5, .7, 0]), (.22, [2.5, .7, 0]), (.42, [2.8, .7, .9], 'ease'),
                                        (3.3, [2.8, .7, .9]), (3.55, [2.5, .7, 0], 'ease')]),
                      'knee': [.1, 0, -1]}
        P['right']['ankle'][1] += envelope(t, .22, .1, 0, .1, 'out', 'in')*1.2+envelope(t, 3.3, .1, 0, .15, 'out', 'in')*1.2
        # The front foot steps out toward the prey to open a side-on stance, and back at the end.
        step = envelope(t, .04, .12, 0, .14, 'out', 'in')
        P['left'] = {'hand': bow, 'pole': [-1, -1, 0], 'wrist': bow_wrist,
                     'curls': [[-62.5, -76.5, -42.5] for f in range(4)],
                     'splay': [-(1.5-f)*2*.8 for f in range(4)],
                     'thumb': ([-45, 30, 35], [-55, 0, 0]),
                     'ankle': keys(t, [(0, [-2.5, .7, 0]), (.04, [-2.5, .7, 0]), (.3, [-3.7, .7, -.9], 'ease'),
                                       (3.05, [-3.7, .7, -.9]), (3.35, [-2.5, .7, 0], 'ease')]),
                     'knee': [-.1, 0, -1], 'foot': [12*step, 0, 0]}
        P['left']['ankle'][1] += step*1.8+envelope(t, 3.05, .13, 0, .17, 'out', 'in')*1.5

        # ---- Bowstring and arrow ----
        tension = shaped(t, 1.42, 1.55) if 1.42 <= t <= RELEASE else 0.
        P['string'] = (tension, -2.2*shake(t, RELEASE+.02, .35, 9, 3.5))
        if t < .8:
            P['arrow'] = (QUIVER, [0, -1, 0], False)
        elif t < 1.42:
            # Drawn tail-first out of the quiver, then swung over the shoulder onto the string.
            # Tail-first out of the quiver, then the tip swings out to the side and round in
            # front of the chest onto the string (never through the body).
            held = lerp([-.4, -1.0, -.3], nock_offset(0), shaped(t, 1.2, 1.42))
            tip = keys(t, [(0, [.15, -1, .3]), (1.05, [.15, -1, .3]), (1.18, [.75, -.35, -.55], 'ease'),
                           (1.31, [-.2, -.45, -.87], 'ease'), (1.42, shot, 'ease')])
            P['arrow'] = (add(hand, held), norm(tip), True)
        elif t < RELEASE:
            P['arrow'] = (add(hand, nock_offset(roll)), None, True)
        else:
            P['arrow'] = (add(FLIGHT_FROM, mul(shot, (t-RELEASE)*65)), shot, t < 2.92)
        P['w'] = envelope(t, .1, .5, 2.7, .3, 'ease', 'ease')
        return P

    def parts(clip, t, P):
        nock = add(P['right']['hand'], nock_offset(math.radians(P['right']['wrist'][0])))
        bow_parts(clip, t, P['left']['hand'], P['left']['wrist'], nock, *P['string'], P['arrow'])

    # Denser keys through the loose, so the string's shiver and the snap read between frames.
    extra = [RELEASE+i/60 for i in range(-3, 21)]
    return Baker(rig, 4.0, False, extra=extra).bake(pose, parts)


def rise(rig):
    """Claws out of the grave: the free hand bursts up and grips the ground, the bow comes up
    clear of the dirt, the skull and shoulders haul out in two heaves, it kneels, then pushes
    up off its knee and looks about for prey."""
    hy = rig.hip_y

    def pose(t):
        P = {}
        P['hips'] = keys(t, [(0, [.5, -8.5, 5.5]), (.7, [.5, -8, 5.2]),                # lying in the grave
                             (1.0, [.3, -4, 2.5], 'ease'), (1.12, [.3, -3.6, 2.5]),       # heave: the skull is out
                             (1.45, [0, 1.6, 2], 'ease'), (1.58, [0, 1.9, 2]),            # heave: the chest is out
                             (2.0, [-.3, 6.2, 2.8], 'ease'),                              # up onto its knees
                             (2.35, [-.3, 7.2, 2], 'ease'),
                             (2.95, [0, hy-.6, .2], 'ease'),                               # stands
                             (3.2, [0, hy, 0], 'settle'), (3.6, [0, hy, 0])])
        P['body'] = keys(t, [(0, [86, 10, -8]), (.7, [84, 6, -6]),
                             (1.0, [58, -4, 4], 'ease'), (1.45, [56, 3, -3], 'ease'),
                             (2.0, [62, 0, 0], 'ease'), (2.35, [22, -4, 2], 'ease'),
                             (2.95, [4, 0, 0], 'ease'), (3.2, [-2, 0, 0], 'out'),
                             (3.6, [0, 0, 0], 'slow')])
        P['skull'] = keys(t, [(0, [-20, 0, 0]), (.7, [-24, 10, 0]),
                              (1.0, [-48, -18, 6], 'ease'), (1.45, [-44, 20, -6], 'ease'),
                              (2.0, [-50, 0, 0], 'ease'), (2.35, [-14, -30, 0], 'ease'),   # looks about
                              (2.75, [-6, 34, 0], 'ease'), (3.15, [2, -6, 0], 'ease'),
                              (3.6, [0, 0, 0], 'slow')])
        P['jaw'] = [keys(t, [(0, 4), (.8, 24, 'ease'), (1.1, 6), (1.45, 26, 'ease'), (1.7, 8),
                             (2.6, 14, 'ease'), (3.0, 4)])+abs(math.sin(math.tau*6*(t-1.6)))*envelope(t, 1.6, .02, .3, .1)*8, 0, 0]
        # ---- Free (drawing) hand: bursts up through the soil, slams down and grips; hauls;
        # then rests on its right knee and pushes off it to stand ----
        plant = [6.2, .6, -9]
        right = keys(t, [(0, [3.8, -6, -4]), (.1, [3.8, -6, -4]), (.3, [6.6, 6.2, -9.2], 'out'),
                         (.4, [6.9, 6.8, -9.6]), (.5, plant, 'in'), (2.0, plant),
                         (2.3, [4.6, 9.4, -3.8], 'ease'), (2.85, [4.8, 13.5, -2.2], 'ease'),
                         (3.3, REST_DRAW, 'ease')])
        burst = envelope(t, .1, .2, .12, .08, 'snap', 'in')
        grasp = envelope(t, .44, .12, 1.4, .35)
        P['right'] = {'hand': right, 'pole': lerp([1, .3, .6], [1, .2, 1], shaped(t, 2.85, 3.3)),
                      'wrist': [-30*burst+40*grasp, -90+90*grasp, 0],
                      'curls': [[-10-50*grasp+20*burst, -12-55*grasp+12*burst, -8-40*grasp] for f in range(4)],
                      'splay': [(1.5-f)*(6+10*burst)*.8 for f in range(4)],
                      'thumb': ([-20, -30, -35], [-25, 0, 0])}
        # ---- Bow hand: comes up out of the ground with the bow held clear of the dirt ----
        bow = keys(t, [(0, [-4, -7, -5]), (.5, [-4, -7, -5]), (.9, [-7.6, 7.8, -7.2], 'ease'),
                       (1.5, [-7.8, 9.8, -7.6], 'ease'), (2.0, [-7.4, 10.8, -7.0], 'ease'),
                       (2.35, [-6.6, 15.4, -5.6], 'ease'), (2.95, [-4.5, 17.4, -3.4], 'ease'),
                       (3.3, REST_BOW, 'ease')])
        P['left'] = {'hand': bow, 'pole': [-1, -1, 0], 'wrist': [0, 0, -90+keys(t, [(0, 30), (.95, 30), (1.5, 14, 'ease'), (2.4, 0, 'ease')])],
                     'curls': [[-62.5, -76.5, -42.5] for f in range(4)],
                     'splay': [-(1.5-f)*2*.8 for f in range(4)],
                     'thumb': ([-45, 30, 35], [-55, 0, 0])}
        # ---- Legs: buried, then kneeling with the shins along the ground, then the right foot
        # plants and it rises; the left foot comes forward under it ----
        P['right']['ankle'] = keys(t, [(0, [2.6, -23, 8]), (1.0, [2.6, -17, 8.5], 'ease'),
                                       (1.45, [2.6, -9, 9], 'ease'), (2.0, [2.6, .7, 9.4], 'ease'),
                                       (2.35, [2.6, .7, 9.4]), (2.5, [2.6, 1.9, 3], 'out'), (2.6, [2.6, .7, -1], 'in'),
                                       (3.6, [2.5, .7, 0], 'ease')])
        P['left']['ankle'] = keys(t, [(0, [-2.6, -23, 8]), (1.0, [-2.6, -17, 8.5], 'ease'),
                                      (1.45, [-2.6, -9, 9], 'ease'), (2.0, [-2.6, .7, 9.4], 'ease'),
                                      (2.75, [-2.6, .7, 9.4]), (2.95, [-2.6, 2.4, 3], 'out'), (3.1, [-2.5, .7, 0], 'in')])
        P['right']['knee'] = [.1, -.5, -1]; P['left']['knee'] = [-.1, -.5, -1]
        kneel_r = envelope(t, 1.6, .4, .35, .25); kneel_l = envelope(t, 1.6, .4, .8, .3)
        P['right']['foot'] = [95*kneel_r, 0, 0]; P['left']['foot'] = [95*kneel_l, 0, 0]
        P['w'] = 1-shaped(t, 3.0, 3.5)
        P['string'] = (0., 0.)
        P['arrow'] = (QUIVER, [0, -1, 0], False)
        return P

    def parts(clip, t, P):
        bow_parts(clip, t, P['left']['hand'], P['left']['wrist'], P['right']['hand'], 0., 0., P['arrow'])

    baker = Baker(rig, 3.6, False, align='end')
    clip = baker.bake(pose, parts)
    rise.clamped = baker.clamped
    return clip


def death(rig):
    """Rocked back by the hit, the bow slips from its fingers and the legs fold under it like
    cut strings; it drops into a slumped heap, sags, then folds forward onto its face with the
    knees still under it while the bow clatters down beside it."""
    hy = rig.hip_y

    def pose(t):
        P = {}
        drop = shaped(t, .1, .45, 'in')
        P['hips'] = keys(t, [(0, [0, hy, 0]), (.1, [0, hy-.3, 1.2], 'out'),
                             (.45, [.2, 5.4, 2.6], 'in'),                                   # drops into a heap
                             (.52, [.2, 5.9, 2.6], 'out'), (.62, [.2, 5.4, 2.6], 'in'),
                             (.75, [.2, 5.2, 2.9], 'ease'),
                             (1.12, [.4, 4.0, 4.2], 'in'), (1.2, [.4, 4.3, 4.2], 'out'),
                             (1.32, [.4, 4.0, 4.2], 'in'), (1.7, [.4, 4.0, 4.2])])
        P['body'] = keys(t, [(0, [0, 0, 0]), (.1, [-11, 2, -3], 'out'), (.45, [8, -3, 4], 'ease'),
                             (.75, [30, -4, 5], 'ease'),                                     # sags
                             (1.12, [78, -6, 6], 'in'), (1.2, [74, -6, 6], 'out'),
                             (1.32, [78, -6, 7], 'in'), (1.7, [78, -6, 7])])
        P['skull'] = keys(t, [(0, [0, 0, 0]), (.08, [-26, 6, 0], 'snap'), (.45, [14, -8, 4], 'ease'),
                              (.75, [34, -6, 8], 'ease'), (1.08, [-6, 10, 4], 'ease'),
                              (1.18, [14, 34, 18], 'out'), (1.45, [10, 44, 22], 'ease'), (1.7, [10, 44, 22])])
        P['jaw'] = [keys(t, [(0, 4), (.08, 22, 'snap'), (.45, 8), (.52, 16, 'out'), (.75, 6),
                             (1.12, 4), (1.2, 26, 'out'), (1.45, 20), (1.7, 20)]), 0, 0]
        _, _, at = rig.body(P)
        for side, label in rig.labels:
            P[label] = {'ankle': keys(t, [(0, [side*2.5, .7, 0]), (.1, [side*2.5, .7, 0]),
                                         (.45, [side*2.9, .7, 6.8], 'ease'), (1.12, [side*3.0, .7, 7.6], 'ease'),
                                         (1.7, [side*3.0, .7, 7.6])]),
                        'knee': [side*.25, -.4, -1],
                        'foot': [keys(t, [(0, 0), (.1, 0), (.45, 95, 'ease'), (1.7, 95)]), 0, 0]}
        # ---- Arms: the bow arm is flung out and lets the bow go; both arms hang as it sags and
        # end folded beside its skull ----
        bow = keys(t, [(0, REST_BOW), (.1, [-8.6, 19.5, -2.5], 'out'), (.45, [-6.5, 6.5, -5.5], 'in'),
                       (.75, [-6.0, 3.2, -6.5], 'ease'), (1.12, [-6.4, 1.1, -11.5], 'in'),
                       (1.25, [-6.8, 1.0, -12.2], 'out'), (1.7, [-6.8, 1.0, -12.2])])
        draw = keys(t, [(0, REST_DRAW), (.1, [6.5, 17.5, -1.5], 'out'), (.45, [6.2, 5.8, -4.5], 'in'),
                        (.75, [5.8, 2.6, -6.2], 'ease'), (1.12, [5.9, 1.0, -10.5], 'in'),
                        (1.25, [6.4, 1.0, -11.4], 'out'), (1.7, [6.4, 1.0, -11.4])])
        for label, hand, side in (('left', bow, -1), ('right', draw, 1)):
            P[label].update({'hand': within(at(rig.shoulder_local(side)), hand, rig.arm_lengths[label], .98)})
        loose = shaped(t, .08, .2)
        P['left'].update({'pole': [-1, -1, 0], 'wrist': [0, 0, -90+60*shaped(t, .1, .7)],
                          'curls': [[-62.5+50*loose, -76.5+60*loose, -42.5+30*loose] for f in range(4)],
                          'splay': [-(1.5-f)*(2+6*loose)*.8 for f in range(4)],
                          'thumb': ([-45+25*loose, 30, 35], [-55+30*loose, 0, 0])})
        P['right'].update({'pole': [1, .2, 1], 'wrist': [0, -90+40*shaped(t, .1, .8), 0],
                           'curls': [[-13-10*shaped(t, .9, 1.4), -18-15*shaped(t, .9, 1.4), -12] for f in range(4)],
                           'splay': [(1.5-f)*6*.8 for f in range(4)],
                           'thumb': ([-20, -30, -35], [-25, 0, 0])})
        P['w'] = shaped(t, 0, .3)
        return P

    def parts(clip, t, P):
        hand, wrist = P['left']['hand'], P['left']['wrist']
        if t > .2:
            # Let go at 0.2 s: the bow drops, tips over, bounces once and lies flat beside it.
            released = pose(.2)['left']
            start = released['hand']
            f = shaped(t, .2, .62, 'in')
            hand = lerp(start, [-12.6, 1.4, 1.8], f)
            hand[1] += 1.1*envelope(t, .62, .06, 0, .1, 'out', 'in')
            wrist = [0, 0, -90+60*shaped(.2, .1, .7)+(90-60*shaped(.2, .1, .7))*f]
        bow_parts(clip, t, hand, wrist, add(hand, [.8, 0, -.6]), 0., 0., (QUIVER, [0, -1, 0], False))

    baker = Baker(rig, 1.7, False, align='start')
    clip = baker.bake(pose, parts)
    death.clamped = baker.clamped
    return clip


def idle(rig):
    """A hunter at rest: weight settles onto one hip and back, the bow hand tightens and eases
    on the grip, the drawing fingers twitch as if feeling for a string, and the skull holds
    still between two slow, watchful turns."""
    hy = rig.hip_y
    length = 4.0

    def pose(t):
        p = t/length; tau = math.tau
        sway = math.sin(tau*p)
        P = {'hips': [-.4*sway, hy-.25*(1-math.cos(tau*p))/2, 0],
             'body': [-.8*math.sin(tau*2*p), 1.5*sway, 1.6*sway]}
        look = keys(t % length, [(0, 0), (.7, 0), (1.3, -16, 'slow'), (1.9, -16), (2.5, 9, 'slow'),
                                 (3.1, 9), (3.7, 0, 'slow'), (4, 0)])
        P['skull'] = [2*math.sin(tau*p)+1.5*math.sin(tau*2*p), look, -1.5*sway]
        P['jaw'] = [4+math.sin(tau*p)+3*envelope(t, 3.15, .08, .04, .12), 0, 0]
        ripple = [.5+.5*math.sin(tau*p-f*.65) for f in range(4)]
        squeeze = envelope(t, 1.0, .35, .3, .6, 'ease', 'ease')
        # The drawing fingers twitch twice, as if feeling for the string.
        itch = envelope(t, 2.0, .06, .05, .2, 'snap', 'ease')+envelope(t, 2.32, .06, .05, .25, 'snap', 'ease')
        grip = [(.2*r+.45*itch) if f < 3 else (.2*r+.45*itch)*.35 for f, r in enumerate(ripple)]
        P['left'] = {'hand': add(REST_BOW, [-.15*sway, .2*math.sin(tau*2*p+.5), 0]), 'pole': [-1, -1, 0],
                     'wrist': [0, 0, -90+2*sway],
                     'curls': [[-60-5*r-6*squeeze, -74-5*r-8*squeeze, -40-5*r-6*squeeze] for r in ripple],
                     'splay': [-(1.5-f)*2*(1-.4*ripple[f]) for f in range(4)],
                     'thumb': ([-45, 30, 35], [-55, 0, 0]),
                     'ankle': [-2.5, .7, 0], 'knee': [-.1, 0, -1]}
        P['right'] = {'hand': add(REST_DRAW, [-.15*sway, .2*math.sin(tau*2*p+.9), .2*sway]), 'pole': [1, .2, 1],
                      'wrist': [0, -90, 0],
                      'curls': [[-10-35*g, -12-62*g, -8-40*g] for g in grip],
                      'splay': [(1.5-f)*6*(1-.4*ripple[f]) for f in range(4)],
                      'thumb': ([-20-15*math.sin(tau*p), -30, -35], [-25-15*math.sin(tau*p), 0, 0]),
                      'ankle': [2.5, .7, 0], 'knee': [.1, 0, -1]}
        P['w'] = 0
        P['string'] = (0., 0.)
        P['arrow'] = (QUIVER, [0, -1, 0], False)
        return P

    def parts(clip, t, P):
        bow_parts(clip, t, P['left']['hand'], P['left']['wrist'], P['right']['hand'], 0., 0., P['arrow'])

    return Baker(rig, length, True, align='start').bake(pose, parts)


REWORKED = {'attack': attack, 'rise': rise, 'death': death, 'idle': idle}

# Review notes shown in the preview (times in seconds of clip time).
NOTES = {
    'attack': {
        'summary': 'A hunter\'s deliberate shot. Its skull locks on you while the body turns side-on and '
                   'the front foot steps into a stance; it pulls an arrow from the quiver, nocks it, raises '
                   'the bow and draws slowly to the jaw, holds, then looses: the string snaps and shivers, '
                   'the bow kicks, and it holds the follow-through before lowering.',
        'beats': [(0, 'Locks eyes on you; front foot steps out side-on'),
                  (.30, 'Reaches over its right shoulder to the quiver'),
                  (.82, 'Pulls an arrow out tail-first'),
                  (1.08, 'Swings it over and down onto the string'),
                  (1.42, 'Nocks it; bow set before the chest'),
                  (1.55, 'Raises the bow at you and draws'),
                  (2.25, 'Anchored at the jaw: holds, strained'),
                  (2.60, 'Looses: fingers fly open, string snaps'),
                  (2.66, 'Holds the follow-through, watching the arrow'),
                  (3.00, 'Lowers the bow, steps back into its stance')],
        'server': [(0, 'turns side-on (about 0.3 s)'), (2.60, 'arrow fires (tick 52)'), (3.60, 'busy ends (tick 72)')]},
    'rise': {
        'summary': 'Claws out of the grave. The drawing hand punches up and grips, the bow comes up clear '
                   'of the dirt, two heaves haul the skull and chest out, it kneels, then stands up off '
                   'its knee looking about for prey.',
        'beats': [(0, 'Buried'),
                  (.10, 'Hand punches up through the soil, clawing'),
                  (.40, 'Slams down and grips the ground'),
                  (.50, 'Bow comes up out of the dirt, held clear'),
                  (.70, 'First heave: the skull breaks the surface'),
                  (1.12, 'Second heave: shoulders and ribs out'),
                  (1.58, 'Hauls its knees out and kneels'),
                  (2.00, 'Lets go of the ground and kneels up'),
                  (2.35, 'Right foot plants; stands, looking about'),
                  (2.95, 'Settles, gaze back to the front')],
        'server': [(2.16, 'soil stops bursting'), (3.60, 'can act (tick 72)')]},
    'death': {
        'summary': 'Crumples like a puppet with cut strings. Rocked back, it lets the bow go and its legs '
                   'fold under it; it drops into a slumped heap, sags, then folds forward onto its face '
                   'with its knees still under it while the bow clatters down beside it.',
        'beats': [(0, 'Rocked back; bow arm flung out'),
                  (.20, 'Lets go of the bow; legs fold'),
                  (.45, 'Drops into a heap on its heels'),
                  (.62, 'Bow lands flat beside it'),
                  (.75, 'Sags, skull drooping'),
                  (1.12, 'Folds forward onto its face'),
                  (1.32, 'Settles, skull turned aside')],
        'server': [(2.0, 'body removed (tick 40)')]},
    'idle': {
        'summary': 'A hunter at rest (also what it plays between shots while it holds its range): its '
                   'weight settles onto one hip and back, the bow hand tightens and eases on the grip, '
                   'the drawing fingers twitch as if feeling for a string, and the skull holds still '
                   'between two slow, watchful turns.',
        'beats': [(0, 'Weight settles onto the right hip'),
                  (.7, 'Skull turns slowly to its left'),
                  (1.0, 'Bow hand tightens on the grip'),
                  (1.9, 'Turns slowly to its right'),
                  (2.0, 'Drawing fingers twitch twice'),
                  (3.1, 'Jaw clicks; skull comes back to the front')],
        'server': []},
}
