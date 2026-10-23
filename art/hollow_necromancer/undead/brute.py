"""Reworked Hunched Brute clips. See README "Reworked clips" for what each does."""
import math
from motion import keys, shaped, envelope, shake, tremor
from rig import Baker, bump, solve, within, correction, rotate_about
from upright import add, sub, mul, dot, norm, lerp, cross, mat, apply, euler

# The approved strike's maul at the server's hit frame (2.15 s): the generated BRUTE_SOCKET
# in HollowUndeadClips.java comes from this pose, so the hand is pinned to it exactly.
HIT = 2.15
HIT_HAND = [7.225925925925926, 14.893226210499753, -14.264588309225822]
HIT_WRIST = [-41.8392173307979, -72.40497455155301, 37.60013434032274]
REST_TILT = 66


def club_frame(fore, axis, tilt, splay=.2):
    """Maul direction and finger direction for a fist in line with the forearm, tipped by the
    wrist only, in the plane the arm swings in (90 = square to the forearm)."""
    swing = norm(cross(axis, fore)); a = math.radians(tilt)
    club = add(mul(fore, math.cos(a)), mul(swing, math.sin(a)))
    club = norm(add(club, mul(axis, splay)))
    fingers = sub(mul(fore, math.sin(a)), mul(swing, math.cos(a)))
    return club, norm(sub(fingers, mul(club, dot(fingers, club))))


def off_axis(club, fingers, limit=.955):
    """Keeps the maul from pointing straight forward or back: there its Euler keys hit gimbal
    lock and wind a full turn. The head swings a touch wide (outward) there instead."""
    if abs(club[2]) <= limit:
        return club, fingers
    side = math.sqrt(max(0, 1-limit*limit-club[1]*club[1]))
    club = norm([side, club[1], math.copysign(limit, club[2])])
    return club, norm(sub(fingers, mul(club, dot(fingers, club))))


def grip_curls(squeeze):
    return [[-64-8*squeeze, -76-8*squeeze, -43-6*squeeze] for _ in range(4)]


def free_curls(close, spread=0.):
    return [[-12-32*close+6*spread*(f-1.5), -15-46*close, -10-38*close] for f in range(4)]


def attack(rig):
    """Heave the maul overhead, hold it there while tracking, step in and bring it down."""
    hy, length = rig.hip_y, 4.6
    sl = rig.shoulder_local(1)

    # ---- Body: pelvis position and torso rotation through the beats ----
    hit_body = [16, -14, -5]
    hit_hips = rig.hips_for_shoulder(hit_body, 1, [6.6, 26.0, -5.6])
    ground_hips = add(hit_hips, [.4, -2.4, -.8])
    hips_keys = [
        (0, [0, hy, 0]),
        (.30, [.2, hy-1.4, .2]),                     # dip: the knees give as it grips
        (1.20, [-.7, hy-.4, 1.4], 'slow'),           # legs drive the heave, weight goes back
        (1.45, [-.8, hy-.8, 1.6], 'out'),            # the maul's weight settles behind
        (1.60, [-.7, hy-.6, 1.5]),
        (1.95, [-.1, hy-1.8, -.8], 'ease'),          # steps in; the weight follows the foot
        (HIT, hit_hips, 'in'),                       # drops through the blow
        (2.21, ground_hips, 'linear'),
        (2.40, add(ground_hips, [0, .5, 0]), 'settle'),
        (2.80, add(ground_hips, [0, .3, 0])),
        (3.40, [0, hy, 0], 'slow'),
    ]
    body_keys = [
        (0, [0, 0, 0]),
        (.30, [7, -3, 0]),                           # leans in over the grip
        (1.20, [-10, 19, 6], 'slow'),                # leans back, right shoulder back, bends left
        (1.45, [-12, 22, 6], 'out'),
        (1.60, [-12, 22, 6]),
        (1.86, [-13, 23, 5], 'ease'),                # last coil as the foot comes down
        (2.10, [12, -10, -4], 'ease'),               # hips and chest lead the blow
        (HIT, hit_body, 'linear'),
        (2.21, [22, -18, -7], 'linear'),
        (2.40, [21, -17, -6], 'settle'),
        (2.80, [19, -14, -5]),
        (3.40, [0, 0, 0], 'slow'),
    ]

    def base(t):
        hips = keys(t, hips_keys); body = keys(t, body_keys)
        # The maul bites the ground at 2.21 s and the whole frame shudders.
        jolt = shake(t, 2.21, .32, 7, 2.5)
        hips = add(hips, [0, -.5*jolt, 0])
        body = add(body, [1.5*jolt, 0, .8*jolt])
        # Heavy breaths: one held high, two hunched over the buried maul.
        breath = math.sin(math.tau*(t-1.2)/.8)*envelope(t, 1.2, .1, .25, .1)*.8 \
            + math.sin(math.tau*(t-2.4)/.5)*envelope(t, 2.4, .1, .3, .1)*1.4
        return hips, add(body, [-breath, 0, 0])

    # ---- Maul arm: hand angle about the shoulder (0 down, 90 forward, 180 up) ----
    # The hit key is solved so the approved hand position falls out of the arc exactly.
    hit_at = rig.body({'hips': base(HIT)[0], 'body': base(HIT)[1]})[2]
    local = apply(mat([0, -hit_body[1], 0]), sub(HIT_HAND, hit_at(sl)))
    hit_phi = math.degrees(math.atan2(-local[2], -local[1]))
    hit_reach, hit_wide = math.hypot(local[1], local[2]), local[0]
    phi_keys = [
        (0, 3), (.30, -4),                           # drags the head back to get under it
        (1.20, 204, 'slow'),                         # heaves it up and over in one long lift
        (1.45, 216, 'out'), (1.60, 213),             # its weight drops behind and settles
        (1.90, 222, 'ease'),                         # loads further back with the step
        (HIT, hit_phi, 'in'), (2.21, 18, 'linear'),  # the blow, then on into the ground
        (2.80, 17), (2.95, 26, 'ease'), (3.40, 3, 'slow')]
    reach_keys = [(0, 15.6), (.30, 15.2), (1.20, 11.4, 'slow'), (1.45, 11.0), (1.90, 10.8),
                  (HIT, hit_reach, 'out'), (2.21, 15.4), (2.80, 15.4), (3.40, 15.6, 'slow')]
    wide_keys = [(0, .3), (1.20, 2.3, 'slow'), (1.45, 2.5), (1.90, 2.6), (HIT, hit_wide, 'in'),
                 (2.21, .9), (3.40, .3, 'slow')]
    tilt_keys = [(0, REST_TILT), (.30, 58), (1.20, 120, 'slow'), (1.45, 127, 'out'), (1.60, 125),
                 (1.90, 131, 'ease'), (2.0, 128, 'in'), (HIT, 48, 'in'), (2.21, 45, 'linear'),
                 (2.80, 46), (2.95, 70, 'ease'), (3.40, REST_TILT, 'slow')]
    pin_m = mat(HIT_WRIST)

    # ---- Feet: the right foot steps in for the blow and back again in the recovery ----
    def right_ankle(t):
        z = keys(t, [(0, 0), (1.58, 0), (1.95, -4.6, 'ease'), (2.92, -4.6), (3.30, 0, 'ease')])
        lift = envelope(t, 1.58, .2, .02, .15, 'out', 'in')*3.2+envelope(t, 2.92, .16, 0, .22, 'out', 'in')*2.2
        return [3.4, .7+lift, z]

    def right_foot(t):
        return [keys(t, [(0, 0), (1.58, 0), (1.72, 14, 'out'), (1.95, -4, 'in'), (2.0, 0),
                         (2.92, 0), (3.04, 12, 'out'), (3.30, 0, 'in')]), 0, 0]

    def pose(t, pinned=True):
        hips, body = base(t)
        P = {'hips': hips, 'body': body}
        _, _, at = rig.body(P)
        twist = body[1]; turn = mat([0, twist, 0])
        phi = math.radians(keys(t, phi_keys)); reach = keys(t, reach_keys); wide = keys(t, wide_keys)
        shoulder = at(sl)
        hand = add(shoulder, apply(turn, [wide, -reach*math.cos(phi), -reach*math.sin(phi)]))
        axis = apply(turn, [1, 0, 0])
        pole = apply(turn, [.6, -math.sin(phi), math.cos(phi)])
        elbow, _ = solve(shoulder, hand, rig.arm_lengths['right'], pole)
        strain = tremor(t, 11, 1.3)*envelope(t, 1.2, .15, .45, .15)*1.2
        club, fingers = club_frame(norm(sub(hand, elbow)), axis, keys(t, tilt_keys)+strain)
        # Through the hit the fist turns onto the approved strike's exact grip (a small, constant
        # correction faded in and out around the hit frame).
        k = bump(t, HIT, .07, .07)
        if pinned and k > 0:
            club = rotate_about(club, pin_axis, pin_angle*k); fingers = rotate_about(fingers, pin_axis, pin_angle*k)
        club, fingers = off_axis(club, fingers)
        wrist = euler(club, mul(fingers, -1))
        if pinned and abs(t-HIT) < 1e-6:
            wrist, hand = list(HIT_WRIST), list(HIT_HAND)
        squeeze = keys(t, [(0, .35), (.3, 1), (1.2, .8), (1.85, 1), (2.4, .7), (3.4, .35, 'slow')])
        P['right'] = {'hand': hand, 'pole': pole, 'wrist': wrist, 'curls': grip_curls(squeeze),
                      'splay': [(1.5-f)*2*.8 for f in range(4)],
                      'thumb': ([-45, -30, -35], [-55, 0, 0]),
                      'ankle': right_ankle(t), 'knee': [.1, 0, -1], 'foot': right_foot(t)}
        # ---- Free arm: claws out at the prey while the maul goes up, is flung back by the
        # blow, then braces on the left thigh and pushes off it to stand ----
        lshoulder = at(rig.shoulder_local(-1))
        rest = add(lshoulder, [-.3, -13.3, -.8])
        hip = at(rig.hip_local(-1))
        knee, _ = solve(hip, [-3.4, .7, 0], rig.leg_lengths, [-.1, 0, -1])
        hand_l = rest
        for a, b, target, shape in ((.15, .95, at([-8.4, 20.5, -10]), 'ease'),
                                    (1.92, HIT+.04, add(lshoulder, [-2.4, -12.4, 5.2]), 'ease'),
                                    (2.22, 2.42, add(lerp(hip, knee, .8), [-.9, .9, -1.2]), 'in'),
                                    (3.0, 3.40, rest, 'slow')):
            hand_l = lerp(hand_l, target, shaped(t, a, b, shape))
        hand_l = add(hand_l, [0, .4*math.sin(math.tau*t*.9)*envelope(t, .95, .1, .7, .2), 0])
        hand_l = within(lshoulder, hand_l, rig.arm_lengths['left'])
        close = keys(t, [(0, .4), (.25, .1), (.9, .25), (1.25, .6), (1.45, .25), (1.75, .7), (2.1, .4),
                         (2.42, .85, 'out'), (3.05, .85), (3.4, .4, 'slow')])
        lpole = lerp([-1, .15, 1], [-1, .4, .6], envelope(t, .15, .8, 1.0, .3))
        P['left'] = {'hand': hand_l, 'pole': lpole, 'wrist_local': [0, 90, 0], 'wrist_fixed': [0, 90, 0],
                     'curls': free_curls(close, 1-close), 'splay': [-(1.5-f)*6*.8 for f in range(4)],
                     'thumb': ([-20-15*close, 30, 35], [-25-15*close, 0, 0]),
                     'ankle': [-3.4, .7, 0], 'knee': [-.1, 0, -1]}
        # ---- Head: glares at the prey, counter-turned against the coil; drops with the blow,
        # lags behind the jolt, then lifts to look at the player in the hold ----
        look = keys(t, [(0, [0, 0, -3]), (.30, [10, 2, -3]), (1.20, [-4, -17, -1], 'slow'),
                        (1.55, [-6, -20, -1]), (1.90, [-4, -22, 0]), (HIT, [16, 12, -4], 'in'),
                        (2.30, [24, 16, -6], 'out'), (2.60, [-2, 10, -4], 'ease'),
                        (2.80, [-4, 9, -4]), (3.40, [0, 0, -3], 'slow')])
        lag = shake(t, 2.23, .4, 5, 2)*5
        P['skull'] = add(look, [lag+tremor(t, 7)*envelope(t, 1.2, .1, .6, .2)*.8, 0, 0])
        jaw = keys(t, [(0, 4), (.30, 8), (1.0, 22, 'ease'), (1.25, 24), (1.9, 27), (2.05, 31),
                       (2.22, 3, 'snap'), (2.34, 14, 'out'), (2.6, 10), (3.4, 4, 'slow')])
        P['jaw'] = [jaw+tremor(t, 13, 2)*envelope(t, 1.0, .1, .8, .1)*1.5, 0, 0]
        P['w'] = envelope(t, .1, .5, 2.55, .25, 'ease', 'ease')
        return P

    mine = pose(HIT, pinned=False)['right']   # the designed grip at the hit
    mm = mat(mine['wrist'])
    pin_axis, pin_angle = correction([mm[i][0] for i in range(3)], [mm[i][1] for i in range(3)],
                                     [pin_m[i][0] for i in range(3)], [pin_m[i][1] for i in range(3)])
    # Denser keys through the blow, so the fast whip of the maul interpolates cleanly.
    return Baker(rig, length, False, extra=[HIT]+[2.0+i/60 for i in range(16)]).bake(pose)


def rise(rig):
    """Claws out of the grave: the free hand punches up and grips, two slow heaves haul the
    skull and ribs out while the maul comes up over its shoulder, it kneels, pushes up off its
    thigh, then swings the maul down off its shoulder so the head thuds down in front."""
    hy = rig.hip_y
    sl = rig.shoulder_local(1)

    def pose(t):
        P = {}
        P['hips'] = keys(t, [(0, [.5, -9.5, 6]), (.85, [.5, -9, 5.6]),                 # lying in the grave
                             (1.35, [.2, -2.4, 3.5], 'ease'), (1.5, [.2, -2.1, 3.5]),      # heave: skull out
                             (2.2, [0, 4.2, 3], 'ease'), (2.45, [0, 4.6, 3]),              # heave: ribs out
                             (2.8, [-.4, 8.4, 2.6], 'ease'),                               # kneels up
                             (3.3, [0, hy-.8, .3], 'ease'), (3.6, [0, hy, 0], 'slow')])
        P['body'] = keys(t, [(0, [86, 8, -6]), (.85, [84, 4, -4]),
                             (1.35, [58, -6, 5], 'ease'), (2.2, [58, 4, -3], 'ease'),
                             (2.8, [30, -3, 3], 'ease'), (3.3, [6, 0, 0], 'ease'),
                             (3.6, [0, 0, 0], 'slow')])
        strain = envelope(t, .9, .3, 1.3, .3)
        P['body'] = add(P['body'], [tremor(t, 6)*strain*.8, 0, tremor(t, 5, 1)*strain*.6])
        thud = shake(t, 3.32, .3, 7, 2.5)
        P['hips'] = add(P['hips'], [0, -.5*thud, 0])
        P['skull'] = keys(t, [(0, [-18, 0, -3]), (.85, [-22, 6, -3]),
                              (1.35, [-46, -14, 4], 'ease'), (1.8, [-44, 12, -6], 'ease'), (2.3, [-40, -6, 3], 'ease'),
                              (2.8, [-24, -8, -3], 'ease'), (3.3, [-4, 10, -3], 'ease'),
                              (3.6, [0, 0, -3], 'slow')])
        P['jaw'] = [keys(t, [(0, 4), (.85, 6), (1.3, 30, 'ease'), (1.6, 10), (2.1, 28, 'ease'),
                             (2.5, 8), (3.3, 12, 'ease'), (3.6, 4, 'slow')]), 0, 0]
        _, _, at = rig.body(P)
        # ---- Free hand: punches up through the soil, claws, slams down and grips; then braces
        # on the left thigh and pushes off it ----
        plant = [-8.2, .7, -10.5]
        left = keys(t, [(0, [-5.5, -6, -5]), (.15, [-5.5, -6, -5]), (.45, [-8.4, 7.5, -10.5], 'out'),
                        (.55, [-8.6, 8, -10.8]), (.68, plant, 'in'), (2.45, plant)])
        hip = at(rig.hip_local(-1))
        lankle = keys(t, [(0, [-3.4, -22, 9]), (1.35, [-3.4, -15, 9.5], 'ease'), (2.2, [-3.4, -4, 10], 'ease'),
                          (2.65, [-3.4, .7, 10.4], 'ease'), (3.05, [-3.4, .7, 10.4]),
                          (3.2, [-3.4, 2.6, 4], 'out'), (3.38, [-3.4, .7, 0], 'in')])
        knee, _ = solve(hip, within(hip, lankle, rig.leg_lengths, .995), rig.leg_lengths, [-.1, -.5, -1])
        lshoulder = at(rig.shoulder_local(-1))
        brace = within(lshoulder, add(lerp(hip, knee, .6), [-1.2, 1.4, -1.6]), rig.arm_lengths['left'], .96)
        left = lerp(left, brace, shaped(t, 2.45, 2.8))
        left = lerp(left, add(lshoulder, [-.3, -13.3, -.8]), shaped(t, 3.2, 3.6, 'slow'))
        burst = envelope(t, .15, .3, .1, .13, 'out', 'in')
        grasp = envelope(t, .66, .05, 1.75, .35)
        P['left'] = {'hand': left, 'pole': [-1, .2, .4], 'wrist_local': [0, 90, 0], 'wrist_fixed': [0, 90, 0],
                     'wrist_w': 1-shaped(t, 3.2, 3.6),
                     'curls': free_curls(.5*grasp+.3*(1-burst), burst), 'splay': [-(1.5-f)*(6+10*burst)*.8 for f in range(4)],
                     'thumb': ([-20-15*grasp, 30, 35], [-25-15*grasp, 0, 0]),
                     'ankle': lankle, 'knee': [-.1, -.5, -1]}
        # ---- Maul arm: once the skull is out, the maul head erupts from the soil in front and
        # swings up onto its shoulder; it stays shouldered while the brute hauls and stands, then
        # swings down off the shoulder so the head drops in front (a thud at 3.32 s) ----
        shoulder = at(sl)
        phi = math.radians(keys(t, [(0, 20), (1.4, 20), (1.85, 100, 'ease'), (2.9, 96),
                                    (3.32, 3, 'in'), (3.6, 3)]))
        reach = keys(t, [(0, 15), (1.4, 15), (1.85, 8.4, 'ease'), (2.9, 8.6), (3.32, 15.6, 'out')])
        wide = keys(t, [(0, 1.5), (2.9, 1.2), (3.32, .3, 'ease')])
        hand = add(shoulder, [wide, -reach*math.cos(phi), -reach*math.sin(phi)])
        pole = lerp([.6, -math.sin(phi), math.cos(phi)], [1, .15, 1], shaped(t, 3.2, 3.6))
        held = within(shoulder, hand, rig.arm_lengths['right'], .995)
        elbow, _ = solve(shoulder, held, rig.arm_lengths['right'], pole)
        tilt = keys(t, [(0, 66), (1.4, 66), (1.85, 122, 'ease'), (2.9, 124), (3.1, 112, 'in'),
                        (3.32, 62, 'in'), (3.6, REST_TILT, 'ease')])
        club, fingers = club_frame(norm(sub(held, elbow)), [1, 0, 0], tilt+4*thud)
        squeeze = keys(t, [(0, .2), (1.4, 1), (1.9, .8), (3.6, .35, 'slow')])
        P['right'] = {'hand': hand, 'pole': pole, 'club': club, 'fingers': fingers,
                      'curls': grip_curls(squeeze), 'splay': [(1.5-f)*2*.8 for f in range(4)],
                      'thumb': ([-45, -30, -35], [-55, 0, 0]),
                      'ankle': keys(t, [(0, [3.4, -22, 9]), (1.35, [3.4, -15, 9.5], 'ease'), (2.2, [3.4, -4, 10], 'ease'),
                                        (2.65, [3.4, .7, 10.4], 'ease'), (2.8, [3.4, .7, 10.4]),
                                        (2.95, [3.4, 3, 2.6], 'out'), (3.1, [3.4, .7, 0], 'in')]),
                      'knee': [.1, -.5, -1]}
        kneel_r = envelope(t, 2.35, .3, .15, .2); kneel_l = envelope(t, 2.35, .3, .5, .3)
        P['right']['foot'] = [95*kneel_r, 0, 0]; P['left']['foot'] = [95*kneel_l, 0, 0]
        P['w'] = 1-shaped(t, 3.1, 3.6)
        return P

    baker = Baker(rig, 3.6, False, align='end')
    clip = baker.bake(pose)
    rise.clamped = baker.clamped
    return clip


def death(rig):
    """Rocked back by the blow, the knees give and it drops onto them with a thud; it sways
    there a beat with the maul head grounded, then topples forward like a felled tree, bounces
    once on its face and settles with the skull rolled aside."""
    hy = rig.hip_y
    sl = rig.shoulder_local(1)

    def pose(t):
        P = {}
        P['hips'] = keys(t, [(0, [0, hy, 0]), (.12, [0, hy-.2, 1.6], 'out'),
                             (.45, [.2, 8.2, 2.4], 'in'),                                  # knees hit
                             (.52, [.2, 8.7, 2.4], 'out'), (.62, [.2, 8.2, 2.4], 'in'),
                             (.8, [.4, 8.0, 2.0], 'ease'),
                             (1.18, [.6, 3.4, -1.8], 'in'),                                 # face hits
                             (1.26, [.6, 4.1, -1.9], 'out'), (1.38, [.6, 3.5, -1.9], 'in'),
                             (1.7, [.6, 3.5, -1.9])])
        P['body'] = keys(t, [(0, [0, 0, 0]), (.12, [-12, 2, 2], 'out'),
                             (.45, [14, 4, -3], 'ease'), (.62, [18, 2, -2]),
                             (.8, [24, -3, 4], 'ease'),
                             (1.18, [84, 6, 6], 'in'), (1.26, [80, 6, 6], 'out'), (1.38, [84, 6, 7], 'in'),
                             (1.7, [84, 6, 7])])
        P['skull'] = keys(t, [(0, [0, 0, -3]), (.1, [-24, 4, -3], 'snap'), (.45, [8, -6, -6], 'ease'),
                              (.8, [22, 8, 6], 'ease'), (1.12, [-10, 20, 8], 'ease'),       # lifts as it falls
                              (1.24, [16, 46, 16], 'out'), (1.5, [12, 56, 20], 'ease'), (1.7, [12, 56, 20])])
        P['jaw'] = [keys(t, [(0, 4), (.1, 24, 'snap'), (.45, 10, 'ease'), (.52, 18, 'out'), (.8, 8),
                             (1.18, 6), (1.26, 30, 'out'), (1.5, 24), (1.7, 24)]), 0, 0]
        _, _, at = rig.body(P)
        fall = shaped(t, .8, 1.18, 'in')
        # ---- Legs: the feet drag back as the knees drop, then the legs lie out straight ----
        for side, label in rig.labels:
            ankle = keys(t, [(0, [side*3.4, .7, 0]), (.12, [side*3.4, .7, 0]), (.45, [side*3.4, .7, 8.4], 'ease'),
                             (.8, [side*3.4, .7, 8.4]), (1.18, [side*(3.4+.8), .7, 13.2], 'ease'),
                             (1.7, [side*(3.4+.8), .7, 13.2])])
            P.setdefault(label, {})
            P[label]['ankle'] = ankle
            P[label]['knee'] = lerp([side*.1, -.5, -1], [side*.4, -1, .3], fall)
            P[label]['foot'] = [keys(t, [(0, 0), (.12, 0), (.45, 95, 'ease'), (1.7, 95)]), 0, 0]
        # ---- Maul arm: flinches, drops so the head thuds down beside it, then is flung out
        # ahead as it topples; the maul ends lying flat out to the side ----
        shoulder = at(sl)
        rest = [6.7, 14, -3.5]
        hand = keys(t, [(0, rest), (.12, [8.2, 17.5, -2.5], 'out'), (.45, [9.2, 6.5, -6.5], 'in'),
                        (.8, [9.6, 5.2, -9.4], 'ease'), (1.18, [8.8, 3.6, -24], 'in'),
                        (1.3, [8.8, 3.9, -25], 'out'), (1.5, [8.8, 3.8, -25.2]), (1.7, [8.8, 3.8, -25.2])])
        hand = within(shoulder, hand, rig.arm_lengths['right'], .98)
        pole = lerp([1, .15, 1], [.6, -1, .3], fall)
        elbow, _ = solve(shoulder, hand, rig.arm_lengths['right'], pole)
        fore = norm(sub(hand, elbow))
        club, fingers = club_frame(fore, [1, 0, 0], keys(t, [(0, REST_TILT), (.12, 80), (.45, 60, 'in'), (.8, 64)]))
        lying = norm([1, 0, -.35])
        club = norm(lerp(club, lying, shaped(t, .8, 1.22, 'in')))
        fingers = norm(sub(lerp(fingers, fore, shaped(t, .8, 1.22)), mul(club, dot(lerp(fingers, fore, shaped(t, .8, 1.22)), club))))
        P['right'].update({'hand': hand, 'pole': pole, 'club': club, 'fingers': fingers,
                           'curls': grip_curls(keys(t, [(0, .35), (.12, 1), (.8, .6), (1.7, .2)])),
                           'splay': [(1.5-f)*2*.8 for f in range(4)], 'thumb': ([-45, -30, -35], [-55, 0, 0])})
        # ---- Free arm: flung up by the blow, reaches for the ground as it kneels, folds out
        # beside it as it falls ----
        lshoulder = at(rig.shoulder_local(-1))
        lhand = keys(t, [(0, [-6.7, 15.2, -3.5]), (.12, [-9.5, 20, -5], 'out'), (.45, [-8.5, 4.5, -7], 'in'),
                         (.8, [-8.2, 2.4, -8.5], 'ease'), (1.18, [-8.4, 1.2, -12.5], 'in'),
                         (1.3, [-8.4, 1.6, -12.8], 'out'), (1.5, [-8.4, 1.2, -12.8]), (1.7, [-8.4, 1.2, -12.8])])
        lhand = within(lshoulder, lhand, rig.arm_lengths['left'], .98)
        spread = envelope(t, .05, .08, .1, .3)+envelope(t, 1.16, .04, .05, .3)
        P['left'].update({'hand': lhand, 'pole': lerp([-1, .15, 1], [-1, .8, -.2], fall),
                          'wrist_local': [0, 90, 0], 'wrist_fixed': [0, 90, 0],
                          'curls': free_curls(keys(t, [(0, .5), (.12, 0), (.5, .3), (1.18, .2), (1.4, .55), (1.7, .5)]), spread),
                          'splay': [-(1.5-f)*(5+8*spread) for f in range(4)],
                          'thumb': ([-20, 30, 35], [-25, 0, 0])})
        P['w'] = shaped(t, 0, .3)
        return P

    baker = Baker(rig, 1.7, False, align='start')
    clip = baker.bake(pose)
    death.clamped = baker.clamped
    return clip


def idle(rig):
    """A heavy standing loop: two slow, deep breaths that lift the shoulders and sink the hips,
    a slow shift of weight from foot to foot, the maul hanging heavy in a fist that re-grips
    once, the free claw flexing, and one slow turn of the skull to the side and back."""
    hy = rig.hip_y
    sl = rig.shoulder_local(1)
    length = 4.0

    def pose(t):
        p = t/length; tau = math.tau
        # Two breaths per loop: in (shoulders up, hips sink a little later) and out.
        breath = math.sin(tau*2*p)
        sway = math.sin(tau*p)                      # weight from the left foot to the right
        P = {'hips': [.5*sway, hy-.35*(1-math.cos(tau*2*p))/2-.15*(1-math.cos(tau*p))/2, 0],
             'body': [-1.6*breath, -2*sway, -1.8*sway]}
        _, _, at = rig.body(P)
        look = keys(t % length, [(0, 0), (1.1, 0), (1.9, 20, 'slow'), (2.6, 20), (3.5, 0, 'slow'), (4, 0)])
        P['skull'] = [-2*breath+1.5*math.sin(tau*3*p), look+2*math.sin(tau*p), -3+2*sway]
        grind = envelope(t, 2.9, .12, .1, .25)
        P['jaw'] = [4+2*math.sin(tau*2*p+1.2)+5*grind, 0, 0]
        # Maul arm: the hand hangs under the shoulder and swings a touch with the breathing.
        shoulder = at(sl)
        hand = add(shoulder, [.3, -15.6+.3*breath, -.8+.4*sway])
        hand = add(hand, [0, 0, -.3*math.sin(tau*2*p+.6)])
        pole = [1, .15, 1]
        elbow, _ = solve(shoulder, hand, rig.arm_lengths['right'], pole)
        regrip = envelope(t, 2.2, .3, .2, .5, 'ease', 'ease')
        tilt = REST_TILT+3*math.sin(tau*2*p+.4)-6*regrip
        club, fingers = club_frame(norm(sub(hand, elbow)), [1, 0, 0], tilt)
        ripple = [.5+.5*math.sin(tau*p-f*.65) for f in range(4)]
        P['right'] = {'hand': hand, 'pole': pole, 'club': club, 'fingers': fingers,
                      'curls': [[-64-8*s, -76-8*s, -43-6*s] for s in [.35*r+.5*regrip for r in ripple]],
                      'splay': [(1.5-f)*2*(1-.4*ripple[f]) for f in range(4)],
                      'thumb': ([-45, -30, -35], [-55, 0, 0]),
                      'ankle': [3.4, .7, 0], 'knee': [.1, 0, -1]}
        lshoulder = at(rig.shoulder_local(-1))
        lhand = add(lshoulder, [-.3, -13.8+.4*breath, -.8-.5*sway])
        flex = .5+.5*math.sin(tau*p-1.1)
        P['left'] = {'hand': lhand, 'pole': [-1, .15, 1], 'wrist_local': [0, 90, 0], 'wrist_fixed': [0, 90, 0],
                     'curls': [[-12-32*c, -15-46*c, -10-38*c] for c in [flex*(.6+.4*r) for r in ripple]],
                     'splay': [-(1.5-f)*6*(1-.4*ripple[f]) for f in range(4)],
                     'thumb': ([-20-15*math.sin(tau*p), 30, 35], [-25-15*math.sin(tau*p), 0, 0]),
                     'ankle': [-3.4, .7, 0], 'knee': [-.1, 0, -1]}
        P['w'] = 0
        return P

    return Baker(rig, length, True, align='start').bake(pose)


REWORKED = {'attack': attack, 'rise': rise, 'death': death, 'idle': idle}

# Review notes shown in the preview (times in seconds of clip time).
NOTES = {
    'attack': {
        'summary': 'One huge, committed overhead blow. A slow heave puts the maul high behind its head, '
                   'it holds there breathing while it tracks you, steps in, and the hips, chest, arm '
                   'and maul uncoil in turn into a fast chop that buries the head in the ground. It '
                   'stays hunched over the maul, braced on its thigh, before wrenching it free.',
        'beats': [(0, 'Grips the haft, knees give, drags the head back'),
                  (.30, 'Heaves the maul up and over in one long lift'),
                  (1.20, 'Weight settles behind; holds it high, breathing'),
                  (1.58, 'Lifts its right foot and steps in, loading further back'),
                  (1.86, 'Hips and chest uncoil; arm and maul follow'),
                  (2.15, 'The blow passes through you'),
                  (2.22, 'Maul bites the ground; the frame shudders'),
                  (2.30, 'Hunched over it, braced on its thigh, two heavy breaths'),
                  (2.60, 'Lifts its skull to look at you'),
                  (2.80, 'Wrenches the maul free and steps back'),
                  (3.40, 'Back at rest')],
        'server': [(1.50, 'stops turning to track (tick 30)'), (2.15, 'hit (tick 43)'), (3.50, 'busy ends (tick 70)')]},
    'rise': {
        'summary': 'Claws its way out of the grave. The free hand punches up and grips, two slow heaves '
                   'haul the skull and ribs out while the maul head erupts from the soil and swings onto '
                   'its shoulder; it kneels, pushes up off its thigh, and drops the maul in front.',
        'beats': [(0, 'Buried'),
                  (.15, 'Free hand punches up through the soil'),
                  (.55, 'Slams down and grips the ground'),
                  (.85, 'First heave: the skull breaks the surface'),
                  (1.40, 'Maul head erupts in front and swings onto its shoulder'),
                  (1.50, 'Second heave: chest and pelvis come out'),
                  (2.20, 'Knees out; kneels up, bracing on its thigh'),
                  (2.80, 'Pushes up off the thigh: right foot, then left'),
                  (3.10, 'Swings the maul down off its shoulder'),
                  (3.32, 'Maul head thuds down in front'),
                  (3.45, 'Settles at rest')],
        'server': [(2.16, 'soil stops bursting'), (3.60, 'can act (tick 72)')]},
    'death': {
        'summary': 'Falls like a felled tree. Rocked back by the hit, its knees give and it drops onto '
                   'them with a thud, the maul head grounding beside it; it sways a beat, then topples '
                   'forward, bounces once on its face and settles with the skull rolled aside and the '
                   'maul lying flat.',
        'beats': [(0, 'Rocked back; maul flung up'),
                  (.12, 'Knees give'),
                  (.45, 'Drops onto its knees with a thud'),
                  (.62, 'Sways on its knees, hands on the ground'),
                  (.80, 'Topples forward, gathering speed'),
                  (1.18, 'Face hits; one bounce'),
                  (1.38, 'Settles, skull rolled aside, jaw open')],
        'server': [(2.0, 'body removed (tick 40)')]},
    'idle': {
        'summary': 'A heavy standing loop: two slow, deep breaths that lift the shoulders and sink the '
                   'hips, a slow shift of weight from foot to foot, the maul hanging heavy in a fist that '
                   're-grips once, the free claw flexing, and one slow turn of the skull to the side.',
        'beats': [(0, 'Breathes in; weight on the left foot'),
                  (1.0, 'Breathes out; weight shifts right'),
                  (1.1, 'Skull turns slowly to the side'),
                  (2.2, 'Re-grips the maul'),
                  (2.6, 'Skull turns back'),
                  (2.9, 'Grinds its jaw')],
        'server': []},
}
