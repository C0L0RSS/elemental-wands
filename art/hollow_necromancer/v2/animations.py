"""Hand-authored animation beats, baked to smooth numeric GeckoLib keyframes.

The transformation is its own cinematic (transform.py), sharing its beats with
NecromancerTransformScene. The skeleton settles into a four-limbed crawl; its
lunge extends that gait into a full-body spring toward the player. Reworked clips
are authored in robed.py and colossus.py with the tools in motion.py.
"""
import math
import copy
from rig import plant, sample, matrix
from rig import bake_contacts
from transform import transform_clip
import robed
import colossus


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


def siege_and_windup_clips(clip, rot, pos, scale, claw, clips):
    """Tells added after the first co-op playtest, all since reworked in robed.py and colossus.py:
    the perch channel, the crash, the ambush burst and the colossus bolt, hands and drain tells.
    These slots keep their place in the animation file."""
    clip('perch_channel', 3, True)
    clip('crash', 6)
    clip('ambush_burst', 1.7)
    clip('colossus_cast_bolt', 2)
    clip('colossus_cast_hands', 2.4)
    clip('colossus_cast_drain', 4.2)


def second_playtest_clips(clip, rot, pos, scale, claw, clips):
    """Clips added after the solo playtest. Timings match NecromancerRules: the eruption hits on
    its first frame and ends half-buried; the soul leaves the ribcage at SPLIT_RELEASE (1.1 s);
    the collapse spans COLLAPSE_TICKS (5 s). Spine values add to the mesh's 22-degree hunch, as in
    the idle. The charge windup, the dive and the harvest were reworked in colossus.py."""
    zero = (0, 0, 0)

    def ticks(name):
        return [t / 20 for t in range(round(clips[name]['animation_length'] * 20) + 1)]

    def hands(name, targets, pitch, weight=lambda t: 1):
        """Solve both wrists to per-side target keys; `targets(sign)` gives a key dict."""
        for side, sign in (('right', 1), ('left', -1)):
            keys_ = targets(sign)
            plant(clips[name], side, [(t, sample(keys_, t, keys_['0']), weight(t), sample(pitch, t, [-65])[0])
                                      for t in ticks(name)])

    def seed(name, source, t):
        """Start the wrist solve from another clip's solved arms, so each elbow keeps its branch."""
        for side in ('right', 'left'):
            for part in ('arm_', 'forearm_'):
                v = tuple(float(x) for x in sample(clips[source]['bones'][part + side]['rotation'], t, [0, 0, 0]))
                rot(name, part + side, [(0, v), (clips[name]['animation_length'], v)])

    def legs(name, leg, shin):
        for side, sign in (('right', 1), ('left', -1)):
            rot(name, 'leg_' + side, [(t, (x, 0, sign * z)) for t, (x, z) in leg])
            rot(name, 'shin_' + side, [(t, (x, 0, 0)) for t, x in shin])

    rest = lambda sign: [sign * 20, 5, -43]

    # The charge (crouch, coil, set), the Grave Dive and the Soul Harvest are reworked in
    # colossus.py; their slots keep the file order.
    clip('colossus_rush_crouch', .6)
    clip('colossus_rush_coil', .8, True)
    clip('colossus_rush_set', .3)
    clip('colossus_dive', 1.2)

    # Eruption: bursts up jaws first with its arms flung out of the grave, hangs, then pitches
    # forward onto its hands and ends half-buried (the stuck pose).
    clip('colossus_erupt', 1.2)
    pos('colossus_erupt', 'colossus', [(0, (0, -85, -2)), (.15, (0, -22, -2)), (.3, (0, -16, -2)), (.55, (0, -24, -2)), (.8, (0, -35, -2)), (1.2, (0, -34, -2))])
    rot('colossus_erupt', 'spine', [(0, (-15, 0, 0)), (.15, (-18, 0, 0)), (.3, (-12, 0, 0)), (.55, (8, 0, 0)), (.8, (14, 0, 0)), (1.2, (5, 0, 0))])
    rot('colossus_erupt', 'skull', [(0, (-20, 0, 0)), (.15, (-35, 0, 0)), (.3, (-30, 0, 0)), (.55, (-34, 0, 0)), (.8, (-40, 0, 0)), (1.2, (-30, 4, -2))])
    rot('colossus_erupt', 'jaw', [(0, (55, 0, 0)), (.3, (60, 0, 0)), (.55, (25, 0, 0)), (.8, (8, 0, 0)), (1.2, (14, 0, 0))])
    scale('colossus_erupt', 'soul_core', [(0, 1.4), (.3, 1.4), (.8, 1)])
    legs('colossus_erupt', [(0, (0, 0)), (1.2, (-10, 0))], [(0, 20), (1.2, 40)])
    for side, sign in (('right', 1), ('left', -1)):
        rot('colossus_erupt', 'arm_' + side, [(0, (-150, 0, sign * 20)), (.3, (-140, sign * 10, sign * 35)), (.55, (-90, 0, sign * 20)), (.8, (-55, 0, sign * 10)), (1.2, (-55, 0, sign * 10))])
        rot('colossus_erupt', 'forearm_' + side, [(0, (-10, 0, 0)), (.55, (-30, 0, 0)), (1.2, (-20, 0, 0))])
        claw('colossus_erupt', side, [(0, -35), (.3, -35), (.55, 20), (.8, 60), (1.2, 50)])
    hands('colossus_erupt', lambda sign: {'0': [sign * 22, 3, -32], '1.2': [sign * 22, 3, -32]}, {'0': [-65]},
          lambda t: max(0.0, min(1.0, (t - .55) / .25)))

    # Stuck (loops): half out of the ground, heaving and clawing at the earth, jaws snapping.
    clip('colossus_stuck', 1, True)
    pos('colossus_stuck', 'colossus', [(0, (0, -34, -2)), (.25, (0, -31.5, -1)), (.5, (0, -34, -2)), (.75, (0, -32, -3)), (1, (0, -34, -2))])
    rot('colossus_stuck', 'spine', [(0, (5, 0, 0)), (.25, (-4, -6, 4)), (.5, (5, 0, 0)), (.75, (-3, 6, -4)), (1, (5, 0, 0))])
    rot('colossus_stuck', 'skull', [(0, (-30, 4, -2)), (.25, (-18, -12, 6)), (.5, (-30, 4, -2)), (.75, (-20, 12, -6)), (1, (-30, 4, -2))])
    rot('colossus_stuck', 'jaw', [(0, (14, 0, 0)), (.2, (40, 0, 0)), (.3, (6, 0, 0)), (.7, (38, 0, 0)), (.8, (6, 0, 0)), (1, (14, 0, 0))])
    legs('colossus_stuck', [(0, (-10, 0)), (1, (-10, 0))], [(0, 40), (1, 40)])
    claw('colossus_stuck', 'right', [(0, 50), (.2, 70), (.35, -10), (.5, 50), (1, 50)])
    claw('colossus_stuck', 'left', [(0, 50), (.5, 50), (.7, 70), (.85, -10), (1, 50)])
    seed('colossus_stuck', 'colossus_erupt', 1.2)
    hands('colossus_stuck', lambda sign: (
        {'0': [22, 3, -32], '.2': [22, 3, -26], '.35': [22, 10, -34], '.5': [22, 3, -34], '1': [22, 3, -32]} if sign > 0 else
        {'0': [-22, 3, -32], '.5': [-22, 3, -32], '.7': [-22, 3, -26], '.85': [-22, 10, -34], '1': [-22, 3, -32]}),
        {'0': [-65], '.35': [-35], '.5': [-65], '.85': [-35], '1': [-65]})

    # Haul: drags its hips out of the earth and settles back into the crawl.
    clip('colossus_haul', 1)
    pos('colossus_haul', 'colossus', [(0, (0, -34, -2)), (.35, (0, -27, -1)), (.7, (0, -14, 0)), (1, (0, -10, 0))])
    rot('colossus_haul', 'spine', [(0, (5, 0, 0)), (.35, (24, 0, 0)), (.7, (44, 0, 0)), (1, (43, -3, 1))])
    rot('colossus_haul', 'skull', [(0, (-30, 4, -2)), (.35, (-44, 0, 0)), (.7, (-60, 0, 0)), (1, (-58, 5, -3))])
    rot('colossus_haul', 'jaw', [(0, (14, 0, 0)), (.35, (30, 0, 0)), (1, (4, 0, 0))])
    legs('colossus_haul', [(0, (-10, 0)), (.5, (-60, 10)), (.8, (-40, 5)), (1, (-32, 0))], [(0, 40), (.5, 110), (.8, 90), (1, 82)])
    for side in ('right', 'left'):
        claw('colossus_haul', side, [(0, 50), (.35, 70), (1, 12)])
    seed('colossus_haul', 'colossus_stuck', 0)
    hands('colossus_haul', lambda sign: {'0': [sign * 22, 3, -32], '.35': [sign * 22, 2, -28], '.7': [sign * 21, 4, -38], '1': rest(sign)},
          {'0': [-65]})

    # Soul split: rears onto its hind legs, arms thrown wide, and convulses until the soul core
    # flares and tears out of the ribcage at 1.1 s; then it drops back to all fours.
    clip('colossus_split', 1.5)
    pos('colossus_split', 'colossus', [(0, (0, -10, 0)), (.45, (0, -1, 1)), (.8, (0, 0, 1)), (1.1, (0, -2, 0)), (1.5, (0, -10, 0))])
    rot('colossus_split', 'spine', [(0, (43, -3, 1)), (.45, (-8, 0, 0)), (.7, (-14, 0, 0)), (.8, (-6, 0, 0)), (.9, (-15, 0, 0)),
                                   (1, (-4, 0, 0)), (1.1, (-18, 0, 0)), (1.5, (43, -3, 1))])
    rot('colossus_split', 'skull', [(0, (-58, 5, -3)), (.45, (-5, 0, 0)), (1.1, (-10, 0, 0)), (1.5, (-58, 5, -3))])
    rot('colossus_split', 'jaw', [(0, (4, 0, 0)), (.45, (45, 0, 0)), (1.1, (60, 0, 0)), (1.5, (4, 0, 0))])
    scale('colossus_split', 'soul_core', [(0, 1), (.8, 1.3), (1, 1.8), (1.1, 2.2), (1.15, 0), (1.5, 0)])
    legs('colossus_split', [(0, (-32, 0)), (.45, (-5, 0)), (1.1, (-5, 0)), (1.5, (-32, 0))], [(0, 82), (.45, 15), (1.1, 15), (1.5, 82)])
    for side, sign in (('right', 1), ('left', -1)):
        rot('colossus_split', 'arm_' + side, [(0, zero), (.45, (-40, sign * 15, sign * 40)), (1.1, (-50, sign * 20, sign * 45)), (1.5, zero)])
        rot('colossus_split', 'forearm_' + side, [(0, zero), (.45, (-20, 0, 0)), (1.5, zero)])
        claw('colossus_split', side, [(0, 12), (.45, -35), (.8, -40), (1.1, 70), (1.5, 12)])
    hands('colossus_split', lambda sign: {'0': rest(sign)}, {'0': [-65]}, lambda t: max(0.0, 1 - t / .4, (t - 1.1) / .4))

    # Collapse: the soul dragged home, the body folds onto its chest, splayed and twitching,
    # the core guttering, then pushes itself back up at the end of the exposed window.
    clip('colossus_collapse', 5)
    pos('colossus_collapse', 'colossus', [(0, (0, -10, 0)), (.3, (0, -15, -2)), (.45, (0, -13.5, -2)), (.6, (0, -15, -2)), (4.2, (0, -15, -2)), (4.7, (0, -12, -1)), (5, (0, -10, 0))])
    rot('colossus_collapse', 'spine', [(0, (43, -3, 1)), (.3, (68, 4, -6)), (.45, (65, 4, -6)), (.6, (68, 4, -6)), (2, (66, 4, -6)), (3.2, (68, 4, -6)),
                                      (4.2, (67, 4, -6)), (4.7, (55, 0, 0)), (5, (43, -3, 1))])
    rot('colossus_collapse', 'skull', [(0, (-58, 5, -3)), (.3, (-44, -18, 12)), (2, (-44, -18, 12)), (2.1, (-36, -10, 8)), (2.3, (-44, -18, 12)),
                                      (3.4, (-44, -18, 12)), (3.5, (-34, -6, 6)), (3.7, (-44, -18, 12)), (4.2, (-44, -18, 12)), (5, (-58, 5, -3))])
    rot('colossus_collapse', 'jaw', [(0, (4, 0, 0)), (.3, (22, 0, 0)), (4.2, (22, 0, 0)), (5, (4, 0, 0))])
    scale('colossus_collapse', 'soul_core', [(0, 1), (.3, .6), (1.2, .8), (2.1, .6), (3, .8), (3.9, .6), (4.6, 1), (5, 1)])
    legs('colossus_collapse', [(0, (-32, 0)), (.3, (-58, 12)), (4.2, (-58, 12)), (5, (-32, 0))], [(0, 82), (.3, 112), (4.2, 112), (5, 82)])
    for side in ('right', 'left'):
        claw('colossus_collapse', side, [(0, 12), (.3, -20), (4.2, -15), (4.5, 50), (5, 12)])
    hands('colossus_collapse', lambda sign: {'0': rest(sign), '.3': [sign * 30, 3, -34], '4.2': [sign * 30, 3, -34], '4.6': [sign * 24, 5, -40], '5': rest(sign)},
          {'0': [-65], '.3': [-40], '4.2': [-40], '4.6': [-65]})

    clip('colossus_harvest', 2.8)

    clip('perch_cast', .8)  # Reworked in robed.py.

def intro_clip(clips):
    """The opening cinematic, 378 ticks (18.9 s). The original keys below are authored in ticks,
    then retimed at the end to match NecromancerIntro. The soul absorption keeps its first
    148 ticks; the flare, lowering and turn run at two-thirds speed. The levelled staff holds
    for an extra second before the hoist and slam, and the title has a long, patient recovery. He stands robed in the circle throughout; the
    skeleton inside stays the phase-two surprise. The game turns the entity: toward the zombie
    for the pull (0-205), then about 107 degrees to the players (205-235). Everything here is
    local, with the model's front (-Z) forward.

    Original authored ticks before retiming: 0-60 menacing stance; 60-72 staff raised overhead, left claw reaching for the zombie;
    72-148 the pull (staff and body held still so the soul has a fixed target, the claw hauling
    the soul in on an invisible rope); 148 the soul strikes the flame; 166 eyes flare; 172-186
    staff lowered, head bowed at the low camera; 186-206 shuffling turn; 208 staff levelled at
    the players, 213 hoisted, 216 slammed down; eased into the idle's first pose by 260."""
    zero = (0, 0, 0)
    clip = clips['intro'] = {'loop': False, 'animation_length': 18.9, 'bones': {}}
    end = 260

    def put(part, channel, frames):
        """Eased like keys(), in ticks, without baking steps across a held value."""
        frames = [(t / 20, (v, v, v) if isinstance(v, (int, float)) else tuple(v)) for t, v in frames]
        out = {}
        for (t0, a), (t1, b) in zip(frames, frames[1:]):
            steps = 1 if a == b else max(1, math.ceil((t1 - t0) * 20 - 1e-9))
            for i in range(steps):
                f = i / steps
                f = f * f * (3 - 2 * f)
                out[f'{t0 + (t1 - t0) * i / steps:.4f}'] = [round(x + (y - x) * f, 4) for x, y in zip(a, b)]
        out[f'{frames[-1][0]:.4f}'] = [round(v, 4) for v in frames[-1][1]]
        clip['bones'].setdefault(part, {})[channel] = out

    def rot(part, frames):
        put(part, 'rotation', frames)

    def pos(part, frames):
        put(part, 'position', frames)

    def strain(start, stop, a, b, shake, step=2):
        """Ease from pose a to b over start-stop while shuddering every `step` ticks."""
        out = []
        for t in range(start + step, stop, step):
            f = (t - start) / (stop - start)
            f = f * f * (3 - 2 * f)
            sign = 1 if (t - start) // step % 2 else -1
            out.append((t, tuple(x + (y - x) * f + sign * s for x, y, s in zip(a, b, shake))))
        return out + [(stop, b)]

    def billow(start, stop, low, high, period):
        """Alternate between two poses every half period: cloth snapping in the soul wind."""
        return [(t, low if (t - start) // (period // 2) % 2 == 0 else high) for t in range(start, stop, period // 2)]

    # World tilt of the staff is body + arm + forearm + staff (forward positive; the rest pose
    # nets to zero): upright at his side, raised overhead leaning 15 degrees toward the zombie,
    # levelled at 90, upright again when hoisted and when planted ten pixels ahead.
    # With the arm pointing up, negative roll carries the staff out to his right side.
    raised = {'body': (-10, 0, 0), 'right_arm': (-168, 0, -12), 'mage_forearm_right': (-10, 0, 0)}
    staff_up = 15 + 10 + 168 + 10

    # ---- Body. Still from 72 to 148, so the staff head is a fixed target for the soul.
    rot('body', [(0, (3, 0, 0)), (30, (4.5, 0, 0)), (60, (3, 0, 0)), (66, (-2, 0, 0)), (72, raised['body']),
                 (148, raised['body']), (150, (-18, 0, 0)), (153, (-7, 0, 0)), (156, (-12, 0, 0)), (160, (-10, 0, 0)),
                 (172, (-10, 0, 0)), (186, (6, 0, 0)), (190, (5, 0, 2)), (196, (5, 0, -2)), (202, (5, 0, 1)),
                 (208, (7, 0, 0)), (210, (9, 0, 0)), (213, (-12, 0, 0)), (215, (22, 0, 0)), (216, (40, 0, 0)),
                 (218, (43, 0, 0)), (222, (38, 0, 0)), (234, (37, 0, 0)), (end, zero)])
    pos('robe', [(0, zero), (148, zero), (150, (0, .9, 0)), (154, zero), (186, zero), (190, (0, .5, 0)), (193, zero),
                 (196, (0, .5, 0)), (199, zero), (210, zero), (213, (0, 1.5, 0)), (215, (0, .3, 0)), (216, (0, -.5, 0)),
                 (218, (0, -.8, 0)), (222, (0, -.6, 0)), (234, (0, -.5, 0)), (end, zero)])
    # Hood: bowed and patient, lifted toward the zombie through the pull, snapped up by the
    # strike, bowed at the low camera, then glaring at the players.
    rot('hood', [(0, (8, 0, 0)), (60, (8, 0, 0)), (72, (-2, 0, 0))] + strain(72, 148, (-2, 0, 0), (-4, 0, 0), (1.2, 0, 1)) +
        [(150, (-32, 0, 0)), (154, (-24, 0, 0)), (160, (-20, 0, 0)), (172, (-18, 0, 0)), (186, (20, 0, 0)), (202, (16, 0, 0)),
         (208, (-2, 0, 0)), (210, (-2, 0, 0)), (213, (-18, 0, 0)), (216, (-22, 0, 0)), (222, (-26, 0, 0)), (234, (-26, 0, 0)),
         (end, (-3, -3, 0))])

    # ---- Right arm and staff: raised overhead and held steady, lowered to his side, levelled,
    # hoisted and driven down butt first.
    rot('right_arm', [(0, zero), (60, zero), (66, (-120, 0, -4)), (72, raised['right_arm']), (148, raised['right_arm']),
                      (150, (-172, 0, -16)), (154, raised['right_arm']), (172, raised['right_arm']), (186, zero),
                      (202, zero), (208, (-88, 0, 6)), (210, (-92, 0, 6)), (213, (-165, 0, 8)), (215, (-84, 0, 8)),
                      (216, (-40, 0, 8)), (218, (-38, 0, 8)), (234, (-40, 0, 8)), (end, zero)])
    rot('mage_forearm_right', [(0, zero), (60, zero), (66, (-5, 0, 0)), (72, raised['mage_forearm_right']),
                               (172, raised['mage_forearm_right']), (186, zero), (202, zero), (208, (15, 0, 0)),
                               (210, (15, 0, 0)), (213, (-10, 0, 0)), (215, (-16, 0, 0)), (216, (-20, 0, 0)),
                               (234, (-20, 0, 0)), (end, zero)])
    rot('staff', [(0, zero), (60, zero), (66, (130, 0, 0)), (72, (staff_up, 0, 0)), (172, (staff_up, 0, 0)), (186, zero),
                  (202, zero), (208, (156, 0, 0)), (210, (158, 0, 0)), (213, (182, 0, 0)), (215, (78, 0, 0)),
                  (216, (20, 0, 0)), (218, (15, 0, 0)), (234, (23, 0, 0)), (end, zero)])
    put('staff_flame', 'scale', [(0, 1), (20, (.85, 1.2, .85)), (40, 1), (60, 1.1), (72, 1.6)] +
        billow(76, 146, (1.4, 2, 1.4), 1.8, 12) + [(146, (1.9, 2.4, 1.9)), (148, 1.9), (150, 3.2), (152, 2), (154, 2.7),
                                                    (157, 1.8), (160, 1.6), (164, 1.6), (166, 2.1), (170, 1.6), (186, 1.3),
                                                    (206, 1.3), (208, 2.4), (212, 1.8), (216, 3), (219, 1.4), (226, 1.6),
                                                    (234, 1.3), (end, 1)])

    # ---- Left arm: reaches for the zombie, claw open; clenches on the soul and hauls it in
    # hand over fist toward the chest, shaking; flung open by the strike.
    reach, chest = (-95, 0, -6), (-55, 0, 12)
    rot('left_arm', [(0, zero), (60, zero), (66, (-50, 0, -8)), (72, reach), (80, reach)] +
        strain(80, 146, reach, chest, (1.8, 0, 1.5)) +
        [(148, chest), (150, (-20, 0, -45)), (156, (-16, 0, -34)), (172, (-10, 0, -24)), (186, (-4, 0, -6)), (202, (-4, 0, -6)),
         (208, (18, 0, -22)), (210, (18, 0, -22)), (213, (-140, 0, -18)), (215, (-60, 0, -25)), (216, (-12, 0, -32)),
         (218, (-8, 0, -36)), (234, (-10, 0, -30)), (end, zero)])
    rot('mage_forearm_left', [(0, zero), (60, zero), (72, (12, 0, 0)), (80, (12, 0, 0))] +
        strain(80, 146, (12, 0, 0), (-88, 0, 0), (1.5, 0, 0)) +
        [(148, (-88, 0, 0)), (150, (0, 0, 0)), (172, (-6, 0, 0)), (186, zero), (208, zero), (213, (-15, 0, 0)),
         (216, (-5, 0, 0)), (234, (-5, 0, 0)), (end, zero)])
    rot('mage_hand_left', [(0, zero), (60, zero), (72, (-15, 0, 0)), (80, (10, 0, 0)), (146, (25, 0, 0)), (150, (-20, 0, 0)),
                           (160, zero), (end, zero)])
    for k in range(3):
        spread = (k - 1) * 16
        rot(f'mage_finger_left_{k}', [(0, zero), (60, zero), (70, (-30, 0, spread)), (76, (-30, 0, spread)), (80, (75, 0, spread / 5)),
                                       (146, (80, 0, spread / 5)), (148, (80, 0, spread / 5)), (150, (-35, 0, spread)),
                                       (160, (-10, 0, spread / 2)), (186, (20, 0, 0)), (206, (20, 0, 0)), (213, (-25, 0, spread)),
                                       (218, (-25, 0, spread)), (end, zero)])

    # ---- Eyes: a low glow once his gaze lifts for the pull, a flare at 166, back inside by 252.
    for side in ('right', 'left'):
        eye = 'mage_eye_' + side
        pos(eye, [(0, zero), (70, zero), (74, (0, 0, -1.5)), (240, (0, 0, -1.5)), (252, zero), (end, zero)])
        put(eye, 'scale', [(0, 1), (70, 1), (74, 1.12), (148, 1.12), (151, 1.3), (164, 1.2), (166, 1.55), (169, 1.3),
                           (186, 1.3), (208, 1.35), (216, 1.45), (220, 1.25), (240, 1.15), (252, 1), (end, 1)])

    # ---- Robe: braced stance and cloth streaming in the soul wind through the pull, burst out
    # by the strike, swung by the turning steps, blown out by the slam.
    for side, sign, brace, stance in (('right', 1, -14, -24), ('left', -1, 10, -8)):
        rot('mage_leg_' + side, [(0, zero), (60, zero), (70, (brace, 0, 0)), (186, (brace, 0, 0)), (188, zero),
                                 (190 if sign > 0 else 196, (-14, 0, 0)), (193 if sign > 0 else 199, zero), (213, zero),
                                 (216, (stance, 0, 0)), (234, (stance, 0, 0)), (end, zero)])
        rot('skirt_' + side, [(0, zero), (60, zero), (72, (6, 0, sign * 4))] +
            billow(76, 146, (10, 0, sign * 7), (4, 0, sign * 3), 10 if sign > 0 else 14) +
            [(148, (6, 0, sign * 4)), (150, (14, 0, sign * 12)), (156, (6, 0, sign * 5)), (186, zero),
             (190, (4, 0, sign * 5)), (196, (4, 0, -sign * 3)), (202, (2, 0, sign * 2)), (206, zero), (213, zero),
             (216, (14, 0, sign * 12)), (220, (8, 0, sign * 7)), (234, (6, 0, sign * 5)), (end, zero)])
    rot('robe_tail', [(0, zero), (60, zero), (72, (12, 0, 0))] + billow(76, 146, (22, 0, 4), (12, 0, -4), 12) +
        [(148, (14, 0, 0)), (150, (28, 0, 0)), (156, (14, 0, 0)), (186, (6, 0, 0)), (192, (16, 0, 5)), (198, (10, 0, -5)),
         (204, (12, 0, 2)), (208, (6, 0, 0)), (213, (4, 0, 0)), (216, (24, 0, 0)), (220, (14, 0, 0)), (234, (10, 0, 0)),
         (end, zero)])
    for side, sign in (('right', -1), ('left', 1)):
        rot('hood_' + side, [(0, zero), (148, zero), (150, (0, sign * 16, sign * 6)), (160, (0, sign * 8, sign * 3)),
                             (186, zero), (end, zero)])

    # (Runtime tick, original authored tick). Preserve every pre-absorption key; sample the
    # ending at 20 Hz so all joints share the slower beats and the stationary pointing hold.
    timing = [(148, 148), (238, 208), (242, 210), (262, 210), (274, 216),
              (294, 236), (354, 244), (378, 260)]
    source_ticks = []
    for (start, a), (stop, b) in zip(timing, timing[1:]):
        source_ticks.extend(a + (b - a) * (t - start) / (stop - start) for t in range(start, stop))
    source_ticks.append(260)
    for channels in clip['bones'].values():
        for channel, frames in channels.items():
            poses = sorted((float(t), v) for t, v in frames.items())
            values, at = [], 0
            for tick in source_ticks:
                time = tick / 20
                while at + 1 < len(poses) - 1 and poses[at + 1][0] < time:
                    at += 1
                (a, x), (b, y) = poses[at:at + 2]
                f = max(0, min(1, (time - a) / (b - a)))
                values.append([round(v + (w - v) * f, 4) for v, w in zip(x, y)])
            retimed = {t: v for t, v in frames.items() if float(t) < 148 / 20}
            for i, value in enumerate(values):
                # Keep the endpoints of holds instead of hundreds of redundant keys.
                if i == 0 or i == len(values) - 1 or value != values[i - 1] or value != values[i + 1]:
                    retimed[f'{(148 + i) / 20:.4f}'] = value
            channels[channel] = retimed


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
    robed.idle(clips)  # The reworked robed clips (robed.py) start and end on the idle's first pose.
    robed.walk(clips)
    clip('cast_bolt', 2)    # Slots for reworked clips, filled at the end so the file keeps its order.
    clip('cast_hands', 2.4)
    for name, length in [('drain', 4.2), ('raise', 2.2)]:
        clip(name, length)
        rot(name, 'hood', [(0, zero), (.45, (-8, 0, -2)), (length - .4, (-8, 0, -2)), (length, zero)])
    for name, length in [('drain', 4.2), ('raise', 2.2)]:
        for k in range(3):  # The staff hand keeps its grip on the shaft, as in the idle.
            rot(name, f'mage_finger_right_{k}', [(0, (55, 0, 0)), (length, (55, 0, 0))])
    rot('drain', 'left_arm', [(0, zero), (.65, (-55, 0, -12)), (1.5, (-58, 0, -12)), (2.5, (-55, 0, -12)), (3.5, (-58, 0, -12)), (4.2, zero)])
    rot('drain', 'mage_forearm_left', [(0, zero), (.7, (-15, 0, 0)), (3.7, (-35, 0, 0)), (4.2, zero)])
    rot('raise', 'left_arm', [(0, zero), (.45, (10, 0, -10)), (1.1, (-125, 0, -18)), (1.5, (-90, 0, -15)), (2.2, zero)])
    rot('raise', 'body', [(0, zero), (.45, (12, 0, 0)), (1.1, (-15, 0, 0)), (2.2, zero)])
    clip('blink', .8)

    colossus.idle(clips)  # Reworked in colossus.py; its first frame seeds the colossus clips.

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

    transform_clip(clips)  # The transformation cinematic: transform.py.

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
    # a 22-degree hunch.
    for name in ['colossus_lunge', 'colossus_grab']:
        for value in clips[name]['bones']['spine']['rotation'].values():
            value[0] -= 22
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
    siege_and_windup_clips(clip, rot, pos, scale, claw, clips)
    second_playtest_clips(clip, rot, pos, scale, claw, clips)
    # The animation rework (robed.py, colossus.py): authored with motion.py, solved contacts.
    for build in (robed.drain, robed.cast_bolt, robed.cast_hands, robed.blink, robed.ambush_burst, robed.perch_channel, robed.perch_cast, robed.crash,
                  colossus.swipe, colossus.cast_bolt, colossus.cast_hands, colossus.cast_drain, colossus.rush_crouch, colossus.rush_coil,
                  colossus.rush_set, colossus.dive, colossus.harvest):
        build(clips)
    for name in ('colossus_grab', 'colossus_bite_throw', 'colossus_split', 'colossus_collapse'):
        colossus.plant_hind_in(clips, name)  # Their hind feet no longer slide; nothing else moves.
    intro_clip(clips)

    return {'format_version': '1.8.0', 'animations': {'animation.hollow_necromancer.' + k: v for k, v in clips.items()}}
