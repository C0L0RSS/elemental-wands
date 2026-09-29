"""Authored Necromancer anatomy. Model pixels, front -Z, 16 pixels per block.

Rest pose is a hunched biped. Joints belong to anatomical chains so animations
can lower the torso and plant the hands without stretching solid body blocks.
"""
import math

bones, cubes = [], []


def bone(name, pivot, parent=None, rotation=None):
    out = {'name': name, 'pivot': list(pivot), 'cubes': []}
    if parent:
        out['parent'] = parent
    if rotation:
        out['rotation'] = list(rotation)
    bones.append(out)
    return out


def cube(owner, center, size, material='bone', rotation=None, pivot=None, **opts):
    raw = {'origin': [round(c - s / 2, 4) for c, s in zip(center, size)], 'size': list(size)}
    if rotation:
        raw.update(rotation=list(rotation), pivot=list(pivot or center))
    owner['cubes'].append(raw)
    cubes.append({'raw': raw, 'material': material, 'opts': opts, 'bone': owner['name']})


def link(owner, a, b, width=2, depth=None, **opts):
    """A slender cuboid along a bone, with integer UV dimensions and rotated joints."""
    dx, dy, dz = (b[i] - a[i] for i in range(3))
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    center = [(a[i] + b[i]) / 2 for i in range(3)]
    rotation = [-math.degrees(math.atan2(dz, math.hypot(dx, dy))), 0, math.degrees(math.atan2(dx, dy))]
    cube(owner, center, [width, round(length, 3), depth or width], rotation=rotation, **opts)


root = bone('root', [0, 0, 0])
robe = bone('robe', [0, 0, 0], 'root')
# A narrow waist, parted skirt panels and actual legs replace the bell-shaped body.
cube(robe, [0, 14, 1], [6, 12, 5], 'cloth', folds_only=True)
for side, name in ((-1, 'right'), (1, 'left')):
    leg = bone('mage_leg_' + name, [side * 2, 14, 1], 'robe')
    link(leg, [side * 2, 2, 1], [side * 2, 14, 1], 2, material='bone')
    cube(leg, [side * 2, 1, -.5], [2, 2, 4], 'leather')
    panel = bone('skirt_' + name, [side * 2.8, 18, 1], 'robe')
    # Glowing soul-thread hem bands (runes) on every panel keep the dark robe findable from any side.
    cube(panel, [side * 3.1, 10, 1], [2, 16, 7], 'cloth', rotation=[0, 0, side * -5], hem=4, hollow=True, runes=True)
    cube(panel, [side * 1.8, 10, -2.5], [3, 15, 1], 'cloth', rotation=[-3, 0, side * -3], hem=3, runes=True)
back = bone('robe_tail', [0, 20, 3], 'robe')
cube(back, [0, 10, 4], [6, 18, 1], 'cloth', rotation=[7, 0, 0], hem=5, runes=True)
body = bone('body', [0, 18, 1], 'robe', [9, 0, 0])
cube(body, [0, 22, 1], [6, 8, 5], 'cloth', folds_only=True)
cube(body, [0, 26.5, 1], [8, 3, 6], 'cloth', mantle=True, hem=1)
cube(body, [0, 18.5, .5], [7, 1, 6], 'leather', belt=True)
cube(body, [0, 23, -2.5], [2, 8, 1], 'cloth', runes=True)
cube(body, [-2.5, 17, -2.5], [2, 2, 2], 'bone', front='charm_skull')
cube(body, [2.6, 17, -2], [1, 3, 1], 'soul_vial')
hood = bone('hood', [0, 28, 0], 'body', [-9, 0, 0])
# Separate rim, brow, crown and rear taper, with a recessed face rather than a cube head.
cube(hood, [0, 31, 2.5], [5, 7, 1], 'cloth', hood=True)
hood_crown = bone('hood_crown', [0, 33, 2], 'hood')
cube(hood_crown, [0, 34, -.5], [5, 2, 6], 'cloth', rotation=[-8, 0, 0], hood=True)
cube(hood_crown, [0, 35.2, 1], [3, 1, 4], 'cloth', hood=True)
cube(hood_crown, [0, 33.4, -3.4], [6, 1, 1], 'cloth', rotation=[-8, 0, 0], brow=True)
face = bone('mage_face', [0, 31, .4], 'hood')
cube(face, [0, 31, .4], [4, 5, 4], 'void', front='hollow_face')
for side in (-1, 1):
    flap = bone('hood_' + ('right' if side < 0 else 'left'), [side * 2.8, 32, 2], 'hood')
    cube(flap, [side * 2.8, 30.5, -.5], [1, 7, 6], 'cloth', rotation=[8, 0, side * 8], hem=2)
for side, name in ((-1, 'right'), (1, 'left')):
    upper = bone(name + '_arm', [side * 4.5, 26, 1], 'body', [-8, 0, side * -5])
    cube(upper, [side * 4.8, 22.5, 1], [2, 7, 3], 'cloth', sleeve=True)
    fore = bone('mage_forearm_' + name, [side * 4.8, 19, 1], upper['name'], [-20, 0, 0])
    cube(fore, [side * 4.8, 16, 1], [2, 6, 3], 'cloth', sleeve=True)
    cube(fore, [side * 4.8, 13.2, 1], [3, 2, 4], 'cloth', hem=1, hollow=True)
    hand = bone('mage_hand_' + name, [side * 4.8, 12.5, 1], fore['name'])
    cube(hand, [side * 4.8, 11.5, 1], [2, 2, 1], 'bone', knuckles=True)
    for k in range(3):
        finger = bone(f'mage_finger_{name}_{k}', [side * 4.8 + (k - 1) * .8, 10.8, 1], hand['name'])
        cube(finger, [side * 4.8 + (k - 1) * .8, 9.8, 1], [1, 2, 1], 'bone')
staff = bone('staff', [-4.8, 11.5, 1], 'mage_hand_right', [19, 0, 0])
link(staff, [-5, 1, 1], [-5, 31, 1], 1, material='wood')
cube(staff, [-5, 11, 1], [1, 5, 1], 'leather', wrap=True)
cube(staff, [-5, 30.5, 1], [3, 3, 3], 'bone', front='staff_skull')
for side in (-1, 1):
    link(staff, [-5 + side * 2, 31, 1], [-5 + side * 1.5, 35, 1], 1, material='bone')
flame = bone('staff_flame', [-5, 33.5, 1], 'staff')
cube(flame, [-5, 33.5, 1], [1, 3, 1], 'soul', rotation=[0, 0, 12])

# Giant anatomy. Pelvis remains over the feet; the thoracic spine bows forward.
colossus = bone('colossus', [0, 0, 0], 'root')
pelvis = bone('pelvis', [0, 30, 3], 'colossus')
cube(pelvis, [0, 31, 4], [4, 6, 3], 'bone', vertebra=True)
for side in (-1, 1):
    # Iliac wings and an open obturator ring, not a solid block hip.
    link(pelvis, [side * 2, 31, 4], [side * 8, 34, 3], 3, depth=2, cracks=True)
    link(pelvis, [side * 8, 34, 3], [side * 9, 28, 1], 2, depth=3)
    link(pelvis, [side * 9, 28, 1], [side * 3, 26, -1], 2)
    link(pelvis, [side * 3, 26, -1], [0, 28, -2], 2)
spine = bone('spine', [0, 32, 3], 'pelvis', [22, 0, 0])
for i in range(8):
    y = 34 + 3.1 * i
    z = 4 + math.sin(i / 7 * math.pi) * 3 - i * .35
    cube(spine, [0, y, z], [4, 2, 3], 'bone', vertebra=True)
    cube(spine, [0, y + .3, z + 2], [1, 1, 3], 'bone')
    if i % 2 == 0:
        cube(spine, [0, y, z], [6, 1, 2], 'bone')
ribcage = bone('ribcage', [0, 52, 2], 'spine')
# Each rib wraps around a real empty cavity. Upper chest broadens, lower ribs float.
for i in range(6):
    y = 39 + i * 3
    width = [7, 9, 11, 12, 12, 10][i]
    for side in (-1, 1):
        path = [[side * 2, y + 1, 4], [side * width * .65, y + 1, 3],
                [side * width, y, -.5], [side * (width - 1), y - 1, -6],
                [side * 3, y - 2, -9]]
        if i == 0:
            path = path[:-1]
        for a, b in zip(path, path[1:]):
            link(ribcage, a, b, 2, rib=True)
link(ribcage, [0, 40, -9], [0, 54, -9], 2, depth=2)
for side in (-1, 1):
    link(ribcage, [0, 55, -7], [side * 12, 55, -4], 2)
    cube(ribcage, [side * 7, 52, 5], [6, 8, 1], 'bone', rotation=[0, side * 12, side * -18], cracks=True)
soul_core = bone('soul_core', [0, 47, -2], 'ribcage')
cube(soul_core, [0, 47, -2], [2, 5, 2], 'soul', rotation=[0, 0, 12])
neck = bone('neck', [0, 56, 1], 'ribcage')
for i in range(4):
    cube(neck, [0, 57 + i * 1.7, -i * 2], [3, 2, 3], 'bone', vertebra=True)
skull = bone('skull', [0, 62, -7], 'neck')
# A stepped cranium, real recessed sockets, nasal gap, cheek arches and separate teeth.
cube(skull, [0, 68, -9], [11, 8, 8], 'bone', cracks=True)
cube(skull, [0, 72, -9], [9, 2, 7], 'bone', cracks=True)
cube(skull, [0, 70, -10], [13, 4, 7], 'bone', cracks=True)
cube(skull, [0, 66, -13.3], [10, 7, 1], 'void', front='sockets')
for side in (-1, 1):
    cube(skull, [side * 3.5, 68.6, -14.3], [6, 2, 2], 'bone', rotation=[0, 0, side * -10], brow=True)
    cube(skull, [side * 5.8, 65.5, -13.6], [2, 5, 2], 'bone', rotation=[0, 0, side * -10])
    cube(skull, [side * 3.8, 63.5, -14], [4, 2, 2], 'bone', rotation=[0, 0, side * 8])
    cube(skull, [side * 3, 66.2, -14], [1, 1, 1], 'soul')
    link(skull, [side * 5.7, 63.5, -13], [side * 5, 63, -8], 1)
cube(skull, [0, 67.4, -14.5], [2, 3, 2], 'bone')
cube(skull, [0, 61.7, -13.6], [7, 2, 2], 'bone')
for i in range(7):
    if i != 1:
        cube(skull, [-3 + i, 60.1 + (.3 if i % 3 == 0 else 0), -14], [1, 2, 1], 'bone')
jaw = bone('jaw', [0, 63, -7], 'skull', [9, 0, 0])
for side in (-1, 1):
    link(jaw, [side * 5, 63, -7], [side * 4, 57.5, -12], 2)
    link(jaw, [side * 4, 57.5, -12], [side * 2, 57, -14], 2)
cube(jaw, [0, 57, -14], [5, 2, 2], 'bone')
for i in range(5):
    cube(jaw, [-2 + i, 58.5, -14], [1, 2, 1], 'bone')
for side, name in ((-1, 'right'), (1, 'left')):
    arm = bone('arm_' + name, [side * 12, 54, -4], 'ribcage', [-15, 0, 0])
    cube(arm, [side * 12, 54, -4], [4, 4, 4], 'bone', knob=True)
    # The four long limb bones carry glowing soul seams (etch) so the colossus reads in the dark.
    link(arm, [side * 12, 53, -4], [side * 16, 33, -5], 3, cracks=True, etch=True)
    cube(arm, [side * 16, 33, -5], [4, 3, 4], 'bone', knob=True)
    fore = bone('forearm_' + name, [side * 16, 33, -5], arm['name'], [-35, 0, 0])
    link(fore, [side * 15, 32, -5], [side * 16, 13, -10], 2, cracks=True, etch=True)
    link(fore, [side * 17.5, 32, -5], [side * 18, 13, -10], 1)
    hand = bone('hand_' + name, [side * 17, 13, -10], fore['name'])
    # Four separate metacarpals fan out from a narrow wrist. Each finger has
    # three angled phalanges, so the silhouette is a cupped human claw rather
    # than a flat rake. The thumb opposes the index finger across the hollow.
    cube(hand, [side * 17, 11.6, -10.7], [4, 3, 3], 'bone', knuckles=True)
    for k, length in enumerate((6.8, 9, 8, 6.2)):
        spread = (k - 1.35) * 1.75
        base = [side * (17 + spread), 9.8, -11.5]
        outward = side * (k - 1.25) * .8
        knuckle = [base[0] + outward * .35, 11.4, -11.6]
        link(hand, [side * (17 + spread * .55), 12.4, -10.5], knuckle, 1, knob=True)
        cube(hand, knuckle, [1.5, 1.5, 1.5], 'bone', knob=True)
        middle = [base[0] + outward, base[1] - length * .48, -14.4]
        distal = [middle[0] + outward * .5, middle[1] - length * .33, -17]
        end = [distal[0] + outward * .3, distal[1] - 1.4, -15.4]
        finger = bone(f'finger_{name}_{k}', base, hand['name'])
        link(finger, base, middle, 1, cracks=True)
        cube(finger, middle, [1.2, 1.2, 1.2], 'bone', knob=True)
        tip = bone(f'tip_{name}_{k}', middle, finger['name'])
        link(tip, middle, distal, 1)
        last = bone(f'end_{name}_{k}', distal, tip['name'])
        link(last, distal, end, 1)
    thumb_base = [side * 14.4, 11, -10.5]
    thumb = bone('thumb_' + name, thumb_base, hand['name'])
    thumb_joint = [side * 10.8, 8.2, -12.4]
    link(thumb, thumb_base, thumb_joint, 2)
    thumb_tip = bone('thumb_tip_' + name, thumb_joint, thumb['name'])
    link(thumb_tip, thumb_joint, [side * 10.2, 7, -15.2], 1)
    leg = bone('leg_' + name, [side * 7, 29, 2], 'pelvis')
    cube(leg, [side * 7, 29, 2], [4, 4, 4], 'bone', knob=True)
    link(leg, [side * 7, 28, 2], [side * 8, 16, -3], 3, cracks=True, etch=True)
    cube(leg, [side * 8, 16, -4], [3, 3, 2], 'bone', knob=True)
    shin = bone('shin_' + name, [side * 8, 16, -3], leg['name'])
    link(shin, [side * 7.5, 15, -2], [side * 8, 3, 1], 2, cracks=True, etch=True)
    link(shin, [side * 10, 15, -2], [side * 10, 3, 1], 1)
    foot = bone('foot_' + name, [side * 8, 3, 1], shin['name'])
    cube(foot, [side * 8, 2, 0], [4, 2, 4], 'bone', knuckles=True)
    for k in range(4):
        x = side * 8 + (k - 1.5)
        link(foot, [x, 1.3, 0], [x, 1.3, -4 - (k % 2)], 1)


# ---------------- Intro eye flares ----------------
# Buried inside the robed face, so they stay unseen until an animation pushes them forward and
# swells them (the intro's awakening). Set symmetrically over the eye texels painted on the
# face, so a flare covers them. They reuse the colossus eyes' texture region (uv_of), so the
# atlas never changes.
colossus_eyes = [c['raw'] for c in cubes if c['bone'] == 'skull' and c['material'] == 'soul']
for (x, name), source in zip(((-1, 'right'), (1, 'left')), colossus_eyes):
    eye = bone('mage_eye_' + name, [x, 32, 0], 'mage_face')
    cube(eye, [x, 32, 0], [1, 1, 1], 'soul', uv_of=source)
