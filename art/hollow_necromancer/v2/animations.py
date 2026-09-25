"""Hand-authored animation beats, baked to smooth numeric GeckoLib keyframes.

The eight-second emergence shares impact beats with NecromancerRules. The
skeleton settles into a four-limbed crawl; its lunge extends that gait into a
full-body spring toward the player.
"""
import math
import copy
from rig import plant, sample, matrix
from rig import bake_contacts


def keys(frames):
    """Bake ease-in/out to numeric keys; preview and Minecraft share interpolation."""
    out = {}
    for (t0, a), (t1, b) in zip(frames, frames[1:]):
        steps = max(1, math.ceil((t1 - t0) * 20))
        for i in range(steps):
            f = i / steps
            f = f * f * (3 - 2 * f)
            out[f'{t0 + (t1 - t0) * i / steps:.4f}'] = [round(x + (y - x) * f, 4) for x, y in zip(a, b)]
    out[f'{frames[-1][0]:.4f}'] = list(frames[-1][1])
    return out


def animations():
    clips = {}

    def clip(name, length, loop=False):
        clips[name] = {'loop': loop, 'animation_length': length, 'bones': {}}

    def track(name, part, channel, frames):
        clips[name]['bones'].setdefault(part, {})[channel] = keys(frames)

    def rot(name, part, frames):
        track(name, part, 'rotation', frames)

    def pos(name, part, frames):
        track(name, part, 'position', frames)

    def scale(name, part, frames):
        track(name, part, 'scale', [(t, (v, v, v) if isinstance(v, (int, float)) else v) for t, v in frames])

    def claw(name, side, frames):
        """An uneven grasp: index and middle lead, pinky lags, thumb opposes."""
        duration = clips[name]['animation_length']
        for i, (strength, lag) in enumerate(((.82, 0), (1.08, .045), (.95, .085), (.72, .13))):
            poses = [(min(duration, t + (lag if 0 < t < duration else 0)), v * strength) for t, v in frames]
            rot(name, f'finger_{side}_{i}', [(t, (v, (i - 1.5) * 2.5, (i - 1.5) * 1.5)) for t, v in poses])
            rot(name, f'tip_{side}_{i}', [(t, (v * .72, 0, 0)) for t, v in poses])
            rot(name, f'end_{side}_{i}', [(t, (v * .42, 0, 0)) for t, v in poses])
        rot(name, 'thumb_' + side, [(t, (v * .65, (1 if side == 'right' else -1) * v * .65, 0)) for t, v in frames])
        rot(name, 'thumb_tip_' + side, [(t, (v * .55, 0, 0)) for t, v in frames])

    zero = (0, 0, 0)
    clip('idle', 4, True)
    rot('idle', 'body', [(0, zero), (2, (1.8, 0, -.7)), (4, zero)])
    rot('idle', 'hood', [(0, (-3, -3, 0)), (1.8, (-5, 2, -2)), (3, (-2, 4, 0)), (4, (-3, -3, 0))])
    rot('idle', 'mage_forearm_left', [(0, zero), (2, (-5, 0, 2)), (4, zero)])
    scale('idle', 'staff_flame', [(0, 1), (1, (.85, 1.2, .85)), (2.2, 1), (3, (.9, 1.15, .9)), (4, 1)])
    clip('walk', 1.5, True)
    pos('walk', 'robe', [(0, zero), (.375, (0, .5, 0)), (.75, zero), (1.125, (0, .5, 0)), (1.5, zero)])
    for side, sign in [('right', 1), ('left', -1)]:
        rot('walk', 'mage_leg_' + side, [(0, (sign * 16, 0, 0)), (.75, (-sign * 16, 0, 0)), (1.5, (sign * 16, 0, 0))])
        rot('walk', 'skirt_' + side, [(0, (sign * 7, 0, 0)), (.75, (-sign * 7, 0, 0)), (1.5, (sign * 7, 0, 0))])
    rot('walk', 'left_arm', [(0, (-8, 0, 0)), (.75, (8, 0, 0)), (1.5, (-8, 0, 0))])
    rot('walk', 'robe_tail', [(0, (6, 0, -2)), (.75, (10, 0, 2)), (1.5, (6, 0, -2))])
    for name, length in [('cast_bolt', 2), ('cast_hands', 2.4), ('drain', 4.2), ('raise', 2.2)]:
        clip(name, length)
        rot(name, 'hood', [(0, zero), (.45, (-8, 0, -2)), (length - .4, (-8, 0, -2)), (length, zero)])
    rot('cast_bolt', 'right_arm', [(0, zero), (.45, (-40, 5, -4)), (.6, (-28, 5, -4)), (.85, (-38, 5, -4)), (.9, (-28, 5, -4)), (1.15, (-38, 5, -4)), (1.2, (-28, 5, -4)), (2, zero)])
    rot('cast_bolt', 'mage_forearm_right', [(0, zero), (.4, (-15, 0, 0)), (1.25, (-15, 0, 0)), (2, zero)])
    rot('cast_bolt', 'body', [(0, zero), (.5, (-6, 8, 0)), (1.3, (2, 5, 0)), (2, zero)])
    rot('cast_hands', 'left_arm', [(0, zero), (.7, (-85, 0, -16)), (1.45, (-85, 0, -16)), (1.6, (10, 0, -5)), (2.4, zero)])
    rot('cast_hands', 'mage_hand_left', [(0, zero), (.7, (-45, 0, 0)), (1.45, (-45, 0, 0)), (1.6, (30, 0, 0)), (2.4, zero)])
    rot('cast_hands', 'body', [(0, zero), (1.4, (-6, -12, 0)), (1.6, (12, 0, 0)), (2.4, zero)])
    rot('drain', 'left_arm', [(0, zero), (.65, (-55, 0, -12)), (1.5, (-58, 0, -12)), (2.5, (-55, 0, -12)), (3.5, (-58, 0, -12)), (4.2, zero)])
    rot('drain', 'mage_forearm_left', [(0, zero), (.7, (-15, 0, 0)), (3.7, (-35, 0, 0)), (4.2, zero)])
    rot('raise', 'left_arm', [(0, zero), (.45, (10, 0, -10)), (1.1, (-125, 0, -18)), (1.5, (-90, 0, -15)), (2.2, zero)])
    rot('raise', 'body', [(0, zero), (.45, (12, 0, 0)), (1.1, (-15, 0, 0)), (2.2, zero)])
    clip('blink', .8)
    scale('blink', 'robe', [(0, 1), (.3, (.1, 1.15, .1)), (.35, (.1, 1.15, .1)), (.8, 1)])

    clip('colossus_idle', 4, True)
    pos('colossus_idle', 'colossus', [(0, (0, -10, 0)), (1.3, (0, -9.5, 0)), (2.15, (0, -10.5, 0)), (3.25, (0, -9.7, 0)), (4, (0, -10, 0))])
    rot('colossus_idle', 'spine', [(0, (43, -3, 1)), (.55, (46, -2, 1)), (1.3, (42, 1, -2)), (2.15, (49, 4, -1)), (2.45, (45, 4, 1)), (3.25, (46, -3, 2)), (4, (43, -3, 1))])
    rot('colossus_idle', 'skull', [(0, (-58, 5, -3)), (.55, (-62, 4, -3)), (1.3, (-57, -1, 2)), (2.15, (-65, -8, 4)), (2.45, (-57, -7, 1)), (3.25, (-60, 3, -2)), (4, (-58, 5, -3))])
    rot('colossus_idle', 'jaw', [(0, (4, 0, 0)), (1.3, (6, 0, 0)), (2.15, (14, 0, 0)), (2.45, (3, 0, 0)), (4, (4, 0, 0))])
    scale('colossus_idle', 'soul_core', [(0, 1), (2, (.85, 1.15, .85)), (4, 1)])
    rot('colossus_idle', 'arm_right', [(0, (-3, -4, -2)), (1.35, (-8, -6, -6)), (2.15, (-17, 4, -9)), (2.4, (-6, 2, -3)), (4, (-3, -4, -2))])
    rot('colossus_idle', 'arm_left', [(0, (-8, 3, 3)), (1.5, (-4, 7, 2)), (2.15, (-12, -3, 7)), (3, (-6, 2, 4)), (4, (-8, 3, 3))])
    for side in ['right', 'left']:
        rot('colossus_idle', 'leg_' + side, [(0, (-32, 0, 0)), (2, (-37, 0, 0)), (4, (-32, 0, 0))])
        rot('colossus_idle', 'shin_' + side, [(0, (82, 0, 0)), (2, (86, 0, 0)), (4, (82, 0, 0))])
    for side, offset in [('right', 0), ('left', .35)]:
        claw('colossus_idle', side, [(0, 12 + offset * 5), (1.25, 21), (2.1, -14), (2.45, 34), (3.3, 16), (4, 12 + offset * 5)])

    clip('colossus_walk', 1.8, True)
    # Diagonal crawl: each planted hand drags back while its opposite hind leg
    # pushes. The free hand reaches past the skull before its claws set down.
    for side, sign in [('right', 1), ('left', -1)]:
        rot('colossus_walk', 'leg_' + side, [(0, (-25 + sign * 18, 0, sign * 5)), (.45, (-29, 0, sign * 4)), (.9, (-25 - sign * 18, 0, -sign * 5)), (1.35, (-29, 0, -sign * 4)), (1.8, (-25 + sign * 18, 0, sign * 5))])
        rot('colossus_walk', 'shin_' + side, [(0, (86 - sign * 5, 0, 0)), (.45, (89, 0, 0)), (.9, (86 + sign * 5, 0, 0)), (1.35, (89, 0, 0)), (1.8, (86 - sign * 5, 0, 0))])
        rot('colossus_walk', 'arm_' + side, [(0, (-52, 0, sign * 7)), (.45, (-58, 0, sign * 8)), (.9, (-49, 0, sign * 7)), (1.35, (-61, 0, sign * 8)), (1.8, (-52, 0, sign * 7))])
        rot('colossus_walk', 'forearm_' + side, [(0, (-13, 0, 0)), (.9, (-21, 0, 0)), (1.8, (-13, 0, 0))])
        claw('colossus_walk', side, [(0, 27 if sign > 0 else -19), (.45, 37 if sign > 0 else 32), (.9, -22 if sign > 0 else 27), (1.35, 32 if sign > 0 else 38), (1.8, 27 if sign > 0 else -19)])
    pos('colossus_walk', 'colossus', [(0, (0, -10, 0)), (.42, (0, -9.2, -.8)), (.9, (0, -10.7, 0)), (1.32, (0, -9.2, -.8)), (1.8, (0, -10, 0))])
    rot('colossus_walk', 'spine', [(0, (43, -5, 3)), (.43, (48, -7, 5)), (.9, (41, 4, -3)), (1.36, (48, 6, -5)), (1.8, (43, -5, 3))])
    rot('colossus_walk', 'skull', [(0, (-58, 7, -2)), (.43, (-63, 9, -3)), (.9, (-56, -3, 2)), (1.36, (-63, -8, 3)), (1.8, (-58, 7, -2))])

    clip('transform', 8)
    # Mage braces, leans back and parts the hood. The face disappears into the opening.
    rot('transform', 'body', [(0, zero), (.5, (8, 0, 0)), (1.2, (-55, 0, 0)), (2.3, (-62, 0, 0)), (3.8, (-45, 0, 0)), (4.9, (-65, 0, 0)), (5.4, zero)])
    rot('transform', 'hood', [(0, zero), (1.1, (-20, 0, 0)), (2.5, (-30, 0, 0)), (5.4, zero)])
    rot('transform', 'hood_crown', [(0, zero), (.8, zero), (1.5, (-65, 0, 0)), (5.8, (-65, 0, 0)), (6.2, (-12, 0, 0))])
    for side, sign in [('right', -1), ('left', 1)]:
        rot('transform', 'hood_' + side, [(0, zero), (.8, zero), (1.5, (0, sign * 55, sign * 22)), (5.8, (0, sign * 55, sign * 22)), (6.2, (0, sign * 25, sign * 10))])
        rot('transform', side + '_arm', [(0, zero), (.6, (-35, 0, sign * 20)), (1.15, (-110, 0, sign * 15)), (2, (-90, 0, sign * 30)), (3.8, (10, 0, sign * 32)), (5.3, (0, 0, sign * 65))])
        rot('transform', 'mage_forearm_' + side, [(0, zero), (1.1, (-80, 0, 0)), (2, (-40, 0, 0)), (3.8, zero)])
    scale('transform', 'mage_face', [(0, 1), (1, 1), (1.4, 0)])
    for side in ['right', 'left']:
        # Only the empty cloth remains after the creature has escaped its disguise.
        scale('transform', 'mage_hand_' + side, [(0, 1), (2.8, 1), (4.2, 0)])
        scale('transform', 'mage_leg_' + side, [(0, 1), (3.2, 1), (4.3, 0)])
    scale('transform', 'staff_flame', [(0, 1), (1.3, 1.5), (2.3, 0)])
    rot('transform', 'staff', [(0, zero), (1, (0, 0, 20)), (3, (0, 0, 65)), (5.2, (0, 0, 80))])
    # The robe crumples behind the emerging body, lying visibly on the floor before burning.
    pos('transform', 'robe', [(0, zero), (3.8, zero), (4.8, (0, 0, 5)), (5.5, (0, 2, 12)), (6.5, (0, 1, 12)), (7.2, (0, .3, 12))])
    rot('transform', 'robe', [(0, zero), (4, zero), (5.5, (-82, 0, 8)), (6.5, (-88, 0, 8))])
    scale('transform', 'robe', [(0, 1), (4.5, 1), (5.5, (1.2, .8, .3)), (6.4, (1.2, .8, .3)), (7.25, (1.1, .55, .1)), (7.7, 0)])
    # Arms emerge first. The small folded body follows through the hood, then unfolds on the ground.
    scale('transform', 'colossus', [(0, 0), (1.25, 0), (1.45, .25), (2.4, .42), (3.3, .7), (4.5, 1), (8, 1)])
    pos('transform', 'colossus', [(0, (0, 17, 8)), (1.45, (0, 17, 8)), (2.4, (0, 8, 4)), (3.3, (0, -3, 0)), (4.5, (0, -11, 0)), (5.5, (0, -11, 0)), (6.65, zero), (7.25, zero), (8, (0, -10, 0))])
    rot('transform', 'spine', [(0, (55, 0, 0)), (2.4, (55, 0, 0)), (3.3, (72, 0, -5)), (4.5, (65, 0, 0)), (5.5, (65, 0, 0)), (6.65, (-3, 0, 0)), (7.1, (-9, 0, 0)), (7.5, (12, 0, 0)), (8, (43, -3, 1))])
    rot('transform', 'skull', [(0, (-50, 0, 0)), (2.2, (-50, -10, -10)), (3.2, (-65, 10, 5)), (4.5, (-60, 0, 0)), (5.5, (-60, 0, 0)), (6.7, (-15, 0, 0)), (7.1, (-25, 0, 0)), (8, (-58, 5, -3))])
    rot('transform', 'jaw', [(0, zero), (2.2, (10, 0, 0)), (3.3, (22, 0, 0)), (5.5, (8, 0, 0)), (6.7, (12, 0, 0)), (7.1, (38, 0, 0)), (7.5, (32, 0, 0)), (8, (3, 0, 0))])
    for side, sign in [('right', 1), ('left', -1)]:
        delay = 0 if side == 'right' else .25
        rot('transform', 'arm_' + side, [(0, (-110, 0, sign * 10)), (1.6 + delay, (-120, sign * 12, sign * 20)), (2.6 + delay, (-25, 0, sign * 12)), (3.5 + delay, (-45, 0, sign * 8)), (4.5, (-55, 0, sign * 10)), (5.5, (-55, 0, sign * 10)), (6.6, (-5, 0, sign * 6)), (7.1, (-35, 0, sign * 15)), (8, zero)])
        scale('transform', 'arm_' + side, [(0, 1), (1.45, (1.6, 2.3, 1.6)), (2.5, (1.3, 1.7, 1.3)), (4.5, 1)])
        rot('transform', 'forearm_' + side, [(0, (-35, 0, 0)), (2, (-70, 0, 0)), (2.8 + delay, (-15, 0, 0)), (4.5, (-10, 0, 0)), (5.5, (-10, 0, 0)), (6.65, zero)])
        rot('transform', 'hand_' + side, [(0, (20, 0, 0)), (2, (60, 0, 0)), (3, (55, 0, 0)), (5.5, (55, 0, 0)), (6.65, zero)])
        claw('transform', side, [(0, 35), (1.8 + delay, -27), (2.8 + delay, 38), (3.5, -12), (4.25, 45), (5.5, 38), (6.65, -18), (7.1, 38), (8, 12)])
        rot('transform', 'leg_' + side, [(0, (-60, 0, sign * 18)), (3.6, (-60, 0, sign * 18)), (4.5, (-45, 0, sign * 10)), (5.5, (-45, 0, sign * 10)), (6.65, zero), (7.25, zero), (8, (-32, 0, 0))])
        rot('transform', 'shin_' + side, [(0, (100, 0, 0)), (3.6, (100, 0, 0)), (4.5, (70, 0, 0)), (5.5, (70, 0, 0)), (6.65, zero), (7.25, zero), (8, (82, 0, 0))])

    clip('colossus_roar', 2)
    pos('colossus_roar', 'colossus', [(0, (0, -10, 0)), (.4, zero), (1.4, zero), (2, (0, -10, 0))])
    rot('colossus_roar', 'spine', [(0, (43, -3, 1)), (.4, (-12, 0, 0)), (1.4, (-10, 0, 0)), (2, (43, -3, 1))])
    rot('colossus_roar', 'skull', [(0, (-58, 5, -3)), (.4, (-18, 0, 0)), (1.4, (-18, 0, 0)), (2, (-58, 5, -3))])
    rot('colossus_roar', 'jaw', [(0, zero), (.4, (35, 0, 0)), (1.4, (30, 0, 0)), (2, zero)])
    for side, sign in [('right', 1), ('left', -1)]:
        rot('colossus_roar', 'arm_' + side, [(0, zero), (.25, (-12, sign * 8, sign * 8)), (.4, (-42, sign * 20, sign * 24)), (.8, (-48, sign * 24, sign * 27)), (1.4, (-30, sign * 18, sign * 18)), (2, zero)])
        rot('colossus_roar', 'leg_' + side, [(0, (-32, 0, 0)), (.4, (-5, 0, 0)), (1.4, (-5, 0, 0)), (2, (-32, 0, 0))])
        rot('colossus_roar', 'shin_' + side, [(0, (82, 0, 0)), (.4, (12, 0, 0)), (1.4, (12, 0, 0)), (2, (82, 0, 0))])
        claw('colossus_roar', side, [(0, 12), (.25, 39), (.4, -24), (.8, -28), (1.4, 28), (2, 12)])
    clip('colossus_swipe', 1.8)
    pos('colossus_swipe', 'colossus', [(0, (0, -10, 0)), (.6, (0, -4, 0)), (.9, zero), (1.25, (0, -3, 0)), (1.8, (0, -10, 0))])
    rot('colossus_swipe', 'spine', [(0, (65, -3, 1)), (.25, (54, 14, -6)), (.6, (36, 32, -12)), (.9, (46, -34, 12)), (1.08, (40, -40, 15)), (1.28, (47, -17, 4)), (1.8, (65, -3, 1))])
    rot('colossus_swipe', 'skull', [(0, (-58, 5, -3)), (.6, (-18, -20, -5)), (.9, (-24, 12, 8)), (1.28, (-40, 5, 3)), (1.8, (-58, 5, -3))])
    rot('colossus_swipe', 'arm_right', [(0, zero), (.25, (-15, 22, -10)), (.6, (-46, 62, -34)), (.9, (-66, -72, 22)), (1.08, (-72, -79, 23)), (1.28, (-37, -23, 5)), (1.8, zero)])
    rot('colossus_swipe', 'forearm_right', [(0, zero), (.6, (-35, 0, 0)), (.9, (12, 0, 0)), (1.12, (4, 0, 0)), (1.8, zero)])
    rot('colossus_swipe', 'hand_right', [(0, zero), (.6, (-30, 0, -15)), (.9, (22, 0, 8)), (1.13, (35, 0, 11)), (1.8, zero)])
    rot('colossus_swipe', 'arm_left', [(0, zero), (.6, (20, -14, -25)), (.9, (-34, 12, -20)), (1.23, (-56, -17, -10)), (1.8, zero)])
    rot('colossus_swipe', 'hand_left', [(0, zero), (.6, (24, 0, 0)), (1.23, (-20, 0, 0)), (1.8, zero)])
    for side in ['right', 'left']:
        rot('colossus_swipe', 'leg_' + side, [(0, (-32, 0, 0)), (.9, (-5, 0, 0)), (1.8, (-32, 0, 0))])
        rot('colossus_swipe', 'shin_' + side, [(0, (82, 0, 0)), (.9, (18, 0, 0)), (1.8, (82, 0, 0))])
    claw('colossus_swipe', 'right', [(0, 12), (.6, -33), (.9, 24), (1.12, 48), (1.38, -8), (1.8, 12)])
    claw('colossus_swipe', 'left', [(0, 12), (.6, 41), (.9, -24), (1.23, -35), (1.42, 42), (1.8, 12)])
    clip('colossus_grab', 3.2)
    pos('colossus_grab', 'colossus', [(0, (0, -10, 0)), (.8, (0, -3, 0)), (1.8, zero), (2.2, (0, -2, 0)), (3.2, (0, -10, 0))])
    rot('colossus_grab', 'spine', [(0, (65, -3, 1)), (.7, (35, 0, 0)), (.8, (35, 0, 0)), (1.8, (-15, 0, 0)), (2.2, (55, 0, 0)), (3.2, (65, -3, 1))])
    rot('colossus_grab', 'skull', [(0, (-58, 5, -3)), (.8, (-20, 0, 0)), (1.8, (-10, 0, 0)), (2.2, (-45, 0, 0)), (3.2, (-58, 5, -3))])
    rot('colossus_grab', 'arm_right', [(0, zero), (.65, (-80, -12, 0)), (.8, (-70, -15, 0)), (1.8, (-130, -15, 0)), (2.2, (-30, -15, 0)), (3.2, zero)])
    rot('colossus_grab', 'forearm_right', [(0, zero), (.8, (-15, 0, 0)), (1.8, (-30, 0, 0)), (2.2, (-5, 0, 0)), (3.2, zero)])
    rot('colossus_grab', 'hand_right', [(0, zero), (.65, (35, 0, 0)), (.8, (-10, 0, 0)), (1.8, (-30, 0, 0)), (2.2, (20, 0, 0)), (3.2, zero)])
    claw('colossus_grab', 'right', [(0, 12), (.45, 42), (.65, -36), (.8, 65), (1.8, 67), (2.2, 72), (2.35, -30), (2.58, 34), (3.2, 12)])
    rot('colossus_grab', 'arm_left', [(0, zero), (.65, (-26, -15, -12)), (.8, (-52, -22, -18)), (1.8, (-66, 10, -15)), (2.2, (-28, 18, -10)), (3.2, zero)])
    for side in ['right', 'left']:
        rot('colossus_grab', 'leg_' + side, [(0, (-32, 0, 0)), (.8, (-18, 0, 0)), (1.8, zero), (2.2, (-22, 0, 0)), (3.2, (-32, 0, 0))])
        rot('colossus_grab', 'shin_' + side, [(0, (82, 0, 0)), (.8, (42, 0, 0)), (1.8, zero), (2.2, (48, 0, 0)), (3.2, (82, 0, 0))])
    claw('colossus_grab', 'left', [(0, 12), (.65, -22), (.8, 38), (1.8, 44), (2.2, -18), (3.2, 12)])
    clip('colossus_lunge', 2.5)
    pos('colossus_lunge', 'colossus', [(0, (0, -10, 0)), (.28, (0, -9, 1)), (.55, (0, -10, -1)), (.8, (0, -11, -2)), (1, (0, -12, -3)), (1.3, (0, -5, -4)), (1.7, (0, -11, -4)), (2.1, (0, -10, -2)), (2.5, (0, -10, 0))])
    rot('colossus_lunge', 'spine', [(0, (65, -3, 1)), (.28, (62, -7, -5)), (.55, (65, 7, -7)), (.8, (70, -8, 7)), (1, (72, 5, 4)), (1.35, (58, -4, 0)), (1.7, (76, 9, -5)), (1.9, (67, -5, 4)), (2.1, (65, 0, 0)), (2.5, (65, -3, 1))])
    rot('colossus_lunge', 'skull', [(0, (-58, 5, -3)), (.28, (-60, 11, 8)), (.55, (-60, -10, 8)), (.8, (-66, 10, -8)), (1, (-64, -8, -8)), (1.3, (-70, 0, 0)), (1.7, (-72, -8, 5)), (1.9, (-58, 9, -5)), (2.1, (-60, 0, 0)), (2.5, (-58, 5, -3))])
    rot('colossus_lunge', 'jaw', [(0, zero), (.55, (18, 0, 0)), (1, (32, 0, 0)), (1.3, (13, 0, 0)), (1.7, (37, 0, 0)), (2.1, (15, 0, 0)), (2.5, zero)])
    for side, sign in [('right', 1), ('left', -1)]:
        rot('colossus_lunge', 'arm_' + side, [(0, (-55, 0, sign * 8)), (.4, (-60 + sign * 15, 0, sign * 15)), (.8, (-60 - sign * 15, 0, sign * 15)), (1, (-78 + sign * 8, 0, sign * 9)), (1.3, (-115, 0, sign * 9)), (1.7, (-85, 0, sign * 13)), (2.1, (-60, 0, sign * 15)), (2.5, (-55, 0, sign * 8))])
        rot('colossus_lunge', 'forearm_' + side, [(0, (-13, 0, 0)), (.55, (-15, 0, 0)), (1, (-2, 0, 0)), (1.3, (4, 0, 0)), (1.7, (-8, 0, 0)), (2.5, (-13, 0, 0))])
        rot('colossus_lunge', 'hand_' + side, [(0, (40, 0, 0)), (.55, (55, 0, 0)), (1, (-12, 0, 0)), (1.3, (-20, 0, 0)), (1.7, (60, 0, 0)), (2.5, (40, 0, 0))])
        rot('colossus_lunge', 'leg_' + side, [(0, (-32, 0, 0)), (.28, (-42 - sign * 8, 0, sign * 11)), (.55, (-45 + sign * 12, 0, sign * 13)), (.8, (-47 - sign * 10, 0, sign * 12)), (1, (-52, 0, sign * 12)), (1.3, (28 + sign * 7, 0, sign * 7)), (1.5, (36 + sign * 5, 0, sign * 7)), (1.7, (-38 + sign * 11, 0, sign * 12)), (2.1, (-34, 0, sign * 8)), (2.5, (-32, 0, 0))])
        rot('colossus_lunge', 'shin_' + side, [(0, (82, 0, 0)), (.28, (82 + sign * 6, 0, 0)), (.55, (84 - sign * 5, 0, 0)), (.8, (87 + sign * 5, 0, 0)), (1, (92, 0, 0)), (1.3, (12 + sign * 5, 0, 0)), (1.5, (4 + sign * 5, 0, 0)), (1.7, (93 - sign * 4, 0, 0)), (2.1, (86, 0, 0)), (2.5, (82, 0, 0))])
        claw('colossus_lunge', side, [(0, 12), (.25, -22 if sign > 0 else 42), (.55, 42 if sign > 0 else -24), (.8, -25 if sign > 0 else 43), (1.0, 38), (1.3, -38), (1.7, 58), (2.1, 26), (2.5, 12)])
    # These poses are authored as absolute spine angles; the mesh already has
    # a 22-degree hunch. Transformation starts hidden and keeps its old ramp.
    for name in ['colossus_lunge', 'colossus_grab', 'colossus_swipe']:
        for value in clips[name]['bones']['spine']['rotation'].values():
            value[0] -= 22
    for time, value in clips['transform']['bones']['spine']['rotation'].items():
        t = float(time)
        weight = max(0, min(1, t / .3, (8 - t) / .5))
        value[0] -= 22 * weight
    clip('colossus_bite_throw', 2.2)
    pos('colossus_bite_throw', 'colossus', [(0, (0, -10, 0)), (.65, (0, -4, 0)), (1.05, (0, -4, 0)), (1.4, (0, -7, -2)), (2.2, (0, -10, 0))])
    rot('colossus_bite_throw', 'spine', [(0, (43, 0, 0)), (.65, (20, 0, 0)), (.9, (30, 0, 0)), (1.15, (18, -12, 0)), (1.4, (40, 14, 0)), (2.2, (43, 0, 0))])
    rot('colossus_bite_throw', 'skull', [(0, (-58, 0, 0)), (.6, (-44, 0, -7)), (.8, (-44, 0, -7)), (.9, (-35, 0, 4)), (1.1, (-44, 0, 0)), (1.4, (-57, 0, 0)), (2.2, (-58, 0, 0))])
    rot('colossus_bite_throw', 'jaw', [(0, (15, 0, 0)), (.6, (66, 0, 0)), (.8, (70, 0, 0)), (.9, (0, 0, 0)), (1.06, (0, 0, 0)), (1.25, (28, 0, 0)), (1.6, (8, 0, 0)), (2.2, (4, 0, 0))])
    for side, sign in [('right', 1), ('left', -1)]:
        rot('colossus_bite_throw', 'arm_' + side, [(0, (-50, 0, sign * 12)), (2.2, (-50, 0, sign * 12))])
        rot('colossus_bite_throw', 'forearm_' + side, [(0, (-20, 0, 0)), (2.2, (-20, 0, 0))])
        rot('colossus_bite_throw', 'leg_' + side, [(0, (-32, 0, 0)), (.65, (-22, 0, 0)), (1.4, (-37, 0, 0)), (2.2, (-32, 0, 0))])
        rot('colossus_bite_throw', 'shin_' + side, [(0, (82, 0, 0)), (.65, (65, 0, 0)), (1.4, (86, 0, 0)), (2.2, (82, 0, 0))])
        claw('colossus_bite_throw', side, [(0, -22 if sign > 0 else 28), (.18, 62 if sign > 0 else 32), (1.25, 66 if sign > 0 else 32), (1.4, -35 if sign > 0 else 32), (2.2, 12)])
    bake_contacts(clips)
    # A fast diagonal gait, with uneven torso sway and open jaws throughout pursuit.
    rush = copy.deepcopy(clips['colossus_walk'])
    rush['animation_length'] = .65
    for channels in rush['bones'].values():
        for name, frames in channels.items():
            channels[name] = {f'{float(t) * .65 / 1.8:.5f}': v for t, v in frames.items()}
    clips['colossus_rush'] = rush
    rot('colossus_rush', 'jaw', [(0, (28, 0, 0)), (.22, (42, 0, 0)), (.4, (22, 0, 0)), (.65, (28, 0, 0))])
    # Hold the torso at the mouth for the snap, then extend the wrist on release.
    bite = clips['colossus_bite_throw']
    mouth = (matrix('skull', bite, .8) @ [0, 59, -16, 1])[:3]
    path = {'0': [10, 20, -51], '.6': list(mouth), '1.05': list(mouth),
            '1.2': [18, 43, -29], '1.4': [12, 28, -56], '1.75': [20, 8, -47], '2.2': [20, 5, -43]}
    plant(bite, 'right', [(t / 20, sample(path, t / 20, [10, 20, -51]), 1, -20 if t < 29 else -65) for t in range(45)])
    plant(bite, 'left', [(t / 20, [-20, 5, -43], 1, -65) for t in range(45)])

    return {'format_version': '1.8.0', 'animations': {'animation.hollow_necromancer.' + k: v for k, v in clips.items()}}
