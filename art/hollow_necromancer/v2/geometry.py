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
# Pelvis stops beneath the belt; the old tall block protruded through the bending torso.
cube(robe, [0, 15, 1], [5.4, 6, 4.4], 'cloth', folds_only=True)
for side, name in ((-1, 'right'), (1, 'left')):
    leg = bone('mage_leg_' + name, [side * 2, 14, 1], 'robe')
    # Separate thigh, shin and boot so the lower body can bend instead of hinging as one stick.
    link(leg, [side * 2, 8, 1], [side * 2, 14, 1], 2, material='bone')
    shin = bone('mage_shin_' + name, [side * 2, 8, 1], leg['name'])
    link(shin, [side * 2, 2, 1], [side * 2, 8, 1], 2, material='bone')
    cube(shin, [side * 2, 1, -.5], [2, 2, 4], 'leather')
    panel = bone('skirt_' + name, [side * 2.8, 18, 1], 'robe')
    hem = bone('skirt_hem_' + name, [side * 2.8, 9, 1], panel['name'])
    # Split the cloth at the knees, keeping the original rest silhouette and rotated cube pivots.
    for owner, cy, height in ((panel, 13.5, 9), (hem, 5.5, 7)):
        cube(owner, [side * 3.1, cy, 1], [2, height, 7], 'cloth',
             rotation=[0, 0, side * -5], pivot=[side * 3.1, 10, 1], hem=4 if owner is hem else 0, hollow=True, runes=True)
    for owner, cy, height in ((panel, 13.25, 8.5), (hem, 5.75, 6.5)):
        cube(owner, [side * 1.8, cy, -2.5], [3, height, 1], 'cloth',
             rotation=[-3, 0, side * -3], pivot=[side * 1.8, 10, -2.5], hem=3 if owner is hem else 0, runes=True)
back = bone('robe_tail', [0, 20, 3], 'robe')
cube(back, [0, 10, 4], [6, 18, 1], 'cloth', rotation=[7, 0, 0], hem=5, runes=True)
body = bone('body', [0, 18, 1], 'robe', [9, 0, 0])
cube(body, [0, 22, 1], [6, 8, 5], 'cloth', folds_only=True)
cube(body, [0, 26.5, 1], [8, 3, 6], 'cloth', mantle=True, hem=1)
cube(body, [0, 17.3, 1], [6.2, 2.2, 5], 'cloth', folds_only=True)
cube(body, [0, 18.5, .5], [6.4, 1, 5.4], 'leather', belt=True)
cube(body, [0, 23, -2.5], [2, 8, 1], 'cloth', runes=True)
cube(body, [-2.1, 18, -1.9], [1.5, 1.5, 1.2], 'bone', front='charm_skull')
cube(body, [2.2, 18, -1.8], [1, 2.4, 1], 'soul_vial')
hood = bone('hood', [0, 28, 0], 'body', [-9, 0, 0])
# Unrendered attachment survives the old hood being replaced by its pulled-open cloth.
bone('hood_breach_anchor', [0, 28, 0], 'body', [-9, 0, 0])
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
# The whole shaft and the head sit on their own bones so the transformation can snap the staff in
# two at STAFF_BREAK; every other clip leaves them at rest, so the staff stays whole.
STAFF_BREAK = 19
shaft = bone('staff_shaft', [-5, STAFF_BREAK, 1], 'staff')
link(shaft, [-5, 1, 1], [-5, 31, 1], 1, material='wood')
cube(staff, [-5, 11, 1], [1, 5, 1], 'leather', wrap=True)
staff_head = bone('staff_head', [-5, STAFF_BREAK, 1], 'staff')
cube(staff_head, [-5, 30.5, 1], [3, 3, 3], 'bone', front='staff_skull')
for side in (-1, 1):
    link(staff_head, [-5 + side * 2, 31, 1], [-5 + side * 1.5, 35, 1], 1, material='bone')
flame = bone('staff_flame', [-5, 33.5, 1], 'staff_head')
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


# ---------------- Transformation cinematic ----------------
# Parts that only the transformation shows. NecromancerModel hides them in every other clip (the
# workshop and the animatic do the same). New painted cubes are packed after the rest of the atlas
# (late), so every existing texel stays where it was; copies (uv_of) reuse their source's texels.
TRANSFORM_ONLY = []


def transform_bone(name, pivot, parent):
    TRANSFORM_ONLY.append(name)
    return bone(name, pivot, parent)


def free_copy(name, sources, pivot):
    """A root-level stand-in for cubes that must outlive their own bone: the robe's shreds once it
    bursts and the dropped staff halves. The copies keep their sources' model coordinates, so at
    rest they coincide; the clip bakes where the stand-in is in the world."""
    owner = transform_bone(name, pivot, 'root')
    for source in sources:
        raw = source['raw']
        copy = {'origin': list(raw['origin']), 'size': list(raw['size'])}
        if 'rotation' in raw:
            copy.update(rotation=list(raw['rotation']), pivot=list(raw['pivot']))
        owner['cubes'].append(copy)
        cubes.append({'raw': copy, 'material': source['material'], 'opts': {**source['opts'], 'uv_of': raw}, 'bone': name})
    return owner


def centre(entry):
    raw = entry['raw']
    return [o + s / 2 for o, s in zip(raw['origin'], raw['size'])]


# The snapped staff: halves with glowing broken ends, shown from the moment it breaks.
staff_lower = transform_bone('staff_lower', [-5, STAFF_BREAK, 1], 'staff')
link(staff_lower, [-5, 1, 1], [-5, STAFF_BREAK - .5, 1], 1, material='wood', late=True)
cube(staff_lower, [-5, STAFF_BREAK - .2, 1], [1, 1, 1], 'soul', ember=True, late=True)
cube(staff_lower, [-4.7, STAFF_BREAK - .6, .8], [1, 2, 1], 'wood', rotation=[8, 0, -14], late=True)  # A splinter.
staff_upper = transform_bone('staff_upper', [-5, STAFF_BREAK, 1], 'staff_head')
link(staff_upper, [-5, STAFF_BREAK + .5, 1], [-5, 31, 1], 1, material='wood', late=True)
cube(staff_upper, [-5, STAFF_BREAK + .2, 1], [1, 1, 1], 'soul', ember=True, late=True)

# The broken ends flare as the souls trapped in the staff break loose.
for half, sign in (('lower', -1), ('upper', 1)):
    flare = transform_bone(f'staff_glow_{half}', [-5, STAFF_BREAK + sign * .5, 1], f'staff_{half}')
    cube(flare, [-5, STAFF_BREAK + sign * .5, 1], [2, 2, 2], 'soul', late=True)

# The trapped souls: wailing heads trailing a wisp (behind, +Z), free at the root so the clip can
# fly each one anywhere. The first is painted; the rest reuse its texels.
SOULS = 40
SOUL_SHAPE = (([0, 0, 0], [5, 5, 4], dict(front='soul_face')), ([0, .5, 4], [3, 3, 4], dict(wisp=1)),
              ([0, 1, 7.5], [2, 2, 3], dict(wisp=2)), ([0, 1.5, 10], [1, 1, 2], dict(wisp=3)))
soul_cubes = []
for i in range(SOULS):
    soul = transform_bone(f'soul_{i}', [0, 0, 0], 'root')
    for k, (centre_, size, opts) in enumerate(SOUL_SHAPE):
        if i == 0:
            cube(soul, centre_, size, 'soul', late=True, **opts)
            soul_cubes.append(cubes[-1]['raw'])
        else:
            cube(soul, centre_, size, 'soul', uv_of=soul_cubes[k], **opts)

# A non-emissive lid covers the painted eye texels as the light is swallowed.
# The separate eye-flare bones also retract; scaling those alone leaves painted eyes visible.
eye_void = transform_bone('mage_eye_void', [0, 32, -1.72], 'mage_face')
cube(eye_void, [0, 32, -1.72], [4, 3, .12], 'void', late=True)

# Black smoke pouring out of his face: lumpy puffs, free at the root, sharing one painted puff.
SMOKES = 32
puff_cubes = []
for i in range(SMOKES):
    puff = transform_bone(f'smoke_{i}', [0, 0, 0], 'root')
    for k, (centre_, size) in enumerate((([0, 0, 0], [5, 5, 5]), ([1.5, 1.5, -1], [3, 3, 3]))):
        if i == 0:
            cube(puff, centre_, size, 'smoke', late=True)
            puff_cubes.append(cubes[-1]['raw'])
        else:
            cube(puff, centre_, size, 'smoke', uv_of=puff_cubes[k])

# The torn hood left on the giant skull until the roar blows it off.
skull_rag = transform_bone('skull_hood', [0, 70, -9], 'skull')
cube(skull_rag, [0, 74.2, -9.5], [15, 2, 12], 'cloth', hood=True, late=True)
cube(skull_rag, [0, 75.8, -8.5], [11, 2, 10], 'cloth', hood=True, late=True)  # Tapering to a point,
cube(skull_rag, [0, 77.2, -7], [6, 2, 7], 'cloth', rotation=[-12, 0, 0], hood=True, late=True)  # like his own.
cube(skull_rag, [0, 72.6, -15.6], [15, 2, 3], 'cloth', brow=True, late=True)
for side in (-1, 1):
    cube(skull_rag, [side * 7.5, 69.5, -9.5], [1, 11, 12], 'cloth', hem=4, late=True)
cube(skull_rag, [0, 68, -3.2], [15, 14, 1], 'cloth', hem=5, runes=True, late=True)
for side in (-1, 1):
    cube(skull_rag, [side * 7, 68.5, -15.4], [2, 11, 2], 'cloth', hem=3, late=True)  # The opening's rim.

# Claws that grow out of his own fingers when he grows: three jointed segments per finger, on
# the hand so the clip can chain them in the world without shearing. They copy the finger's
# texels, so the atlas is unchanged.
MAGE_CLAWS = {}
for side, name in ((-1, 'right'), (1, 'left')):
    for k in range(3):
        finger = next(c['raw'] for c in cubes if c['bone'] == f'mage_finger_{name}_{k}')
        chain = []
        for j in range(3):
            segment = transform_bone(f'mage_claw_{name}_{k}_{j}', [side * 4.8 + (k - 1) * .8, 10.8, 1], 'mage_hand_' + name)
            cube(segment, [side * 4.8 + (k - 1) * .8, 9.8, 1], [1, 2, 1], 'bone', uv_of=finger)
            chain.append(segment['name'])
        MAGE_CLAWS[name, k] = chain

# Dark lids over the sockets until they ignite, and the soul fire that bursts into them.
lids = transform_bone('colossus_lids', [0, 66.5, -14.1], 'skull')
cube(lids, [0, 66.5, -14.1], [10, 4, 1], 'void', late=True)
for x, name in ((-3, 'right'), (3, 'left')):
    flare = transform_bone('colossus_eye_' + name, [x, 66.2, -14.6], 'skull')
    cube(flare, [x, 66.7, -14.7], [2, 3, 1], 'soul', late=True)

# The robe becomes an empty shell at the breach.  Keeping the original torso on its own
# identity child preserves every earlier pose, texel and silhouette; hiding that child does not
# hide the head, belt, arms or waist.  The replacement halves keep only exterior surfaces, so
# opening them exposes thin cloth and a hollow cavity rather than two solid torso blocks.
SHELL_CLOSED = 'mage_chest_closed'
SHELL_CHEST = ('shell_chest_right', 'shell_chest_left')
SHELL_SHOULDERS = ('shell_shoulder_right', 'shell_shoulder_left')
SHELL_PANELS = (*SHELL_CHEST, *SHELL_SHOULDERS)


def copied_surface(owner, source, origin, size, faces, x_fraction=(0, 1)):
    """A cropped exterior surface using an existing atlas region, never painted or packed.

    The atlas is assigned later by build_art.pack; resolve_shell_uvs translates this metadata
    into ordinary GeckoLib per-face UVs after all source cube UVs are known.
    """
    raw = {'origin': list(origin), 'size': list(size)}
    if 'rotation' in source['raw']:
        raw.update(rotation=list(source['raw']['rotation']), pivot=list(source['raw']['pivot']))
    owner['cubes'].append(raw)
    cubes.append({'raw': raw, 'material': source['material'], 'bone': owner['name'],
                  'opts': {'uv_crop_of': source['raw'], 'uv_faces': dict(faces),
                           'uv_x_fraction': list(x_fraction)}})


def source_face_uv(raw):
    """GeckoLib box UV rectangles, including the bottom face's reversed V direction."""
    u, v = raw['uv']
    w, h, d = (math.floor(s) for s in raw['size'])
    return {'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
            'up': (u + d, v, w, d), 'down': (u + d + w, v + d, w, -d)}


def resolve_shell_uvs():
    """Call once after the atlas and ordinary uv_of copies have received their UVs.

    Cropping, rather than squeezing the whole texture onto each half, keeps the centre seam,
    rune placket and mantle aligned at the closed/open handoff.  No new atlas pixels are used.
    """
    for entry in cubes:
        opts = entry['opts']
        if 'uv_crop_of' not in opts:
            continue
        source = source_face_uv(opts['uv_crop_of'])
        x0, x1 = opts['uv_x_fraction']
        uv = {}
        for face, source_face in opts['uv_faces'].items():
            u, v, w, h = source[source_face]
            if source_face in ('north', 'south', 'up', 'down'):
                a, b = (1 - x1, 1 - x0) if source_face == 'south' else (x0, x1)
                u, w = u + w * a, w * (b - a)
            if w and h:
                uv[face] = {'uv': [round(u, 4), round(v, 4)],
                            'uv_size': [round(w, 4), round(h, 4)]}
        entry['raw']['uv'] = uv


# Select only the upper torso, mantle and rune strip.  The recently repaired lower waist,
# belt, charm and vial remain on body and keep their exact pre-breach articulation.
chest_sources = [entry for entry in cubes if entry['bone'] == 'body'
                 and centre(entry)[1] in (22, 26.5, 23)]
closed_chest = bone(SHELL_CLOSED, [0, 18, 1], 'body')
for entry in chest_sources:
    raw = entry['raw']
    body['cubes'].remove(raw)
    closed_chest['cubes'].append(raw)
    entry['bone'] = SHELL_CLOSED
for side, label in ((-1, 'right'), (1, 'left')):
    chest_panel = transform_bone('shell_chest_' + label, [side * 3, 22, 3.5], 'body')
    shoulder_panel = transform_bone('shell_shoulder_' + label, [side * 4, 26.5, 4], 'body')
    for entry in chest_sources:
        raw = entry['raw']
        origin, size = list(raw['origin']), list(raw['size'])
        # All three originals straddle X=0.  Their unchanged outer faces align perfectly
        # before peeling; the newly cut middle face is omitted to leave an open shell.
        assert origin[0] < 0 < origin[0] + size[0]
        size[0] *= .5
        if side > 0:
            origin[0] += size[0]
        directions = ('north', 'south', 'up', 'down', 'east' if side < 0 else 'west')
        owner = shoulder_panel if centre(entry)[1] == 26.5 else chest_panel
        copied_surface(owner, entry, origin, size, {face: face for face in directions},
                       (0, .5) if side < 0 else (.5, 1))

# A dark inner lining lies inside the torso, just in front of its back wall.  It can conceal
# the first tucked bones, but cannot turn the floor or the space behind him into a portal.
lining = transform_bone('shell_lining', [0, 22.5, 3.3], 'body')
void_source = next(entry for entry in cubes if entry['bone'] == 'mage_eye_void')
copied_surface(lining, void_source, [-2.9, 18, 3.24], [5.8, 9, .12],
               {'north': 'north', 'south': 'north'})


# Independent inner-bone visibility leaves the boots and cloth behind as an empty shell.
# These identity children do not change any earlier joint or cube placement.
MAGE_INNER_LEGS = []
for label in ('right', 'left'):
    for part in ('mage_leg_', 'mage_shin_'):
        source = next(b for b in bones if b['name'] == part + label)
        inner = bone(part + 'inner_' + label, source['pivot'], source['name'])
        MAGE_INNER_LEGS.append(inner['name'])
        for entry in [c for c in cubes if c['bone'] == source['name'] and c['material'] == 'bone']:
            source['cubes'].remove(entry['raw'])
            inner['cubes'].append(entry['raw'])
            entry['bone'] = inner['name']

# Stand-ins for the staff halves once they leave his hands, and for every piece of the empty robe
# (and the bones left in it) as it crumbles behind the colossus.
by_bone = lambda *names: [c for c in cubes if c['bone'] in names and 'uv_of' not in c['opts']]
free_copy('staff_drop_lower', by_bone('staff_lower') + [c for c in by_bone('staff') if c['material'] == 'leather'],
          [-5, 10, 1])
free_copy('staff_drop_upper', by_bone('staff_upper', 'staff_head'), [-5, 26, 1])
HOOD_PARTS = ('hood', 'hood_crown', 'hood_right', 'hood_left', 'mage_face')
ROBE_PARTS = ('robe', 'skirt_right', 'skirt_left', 'robe_tail', 'body', SHELL_CLOSED, 'right_arm', 'left_arm', 'mage_forearm_right',
              'mage_forearm_left', 'mage_leg_right', 'mage_leg_left', 'mage_shin_right', 'mage_shin_left',
              'skirt_hem_right', 'skirt_hem_left', 'mage_hand_right', 'mage_hand_left',
              *(f'mage_finger_{side}_{k}' for side in ('right', 'left') for k in range(3)))
SHREDS = []
for i, entry in enumerate(by_bone(*ROBE_PARTS)):
    SHREDS.append((f'shred_{i}', entry['bone']))
    free_copy(f'shred_{i}', [entry], centre(entry))
# The hood's cloth, torn off the skull as he outgrows it.
HOOD_RAGS = []
for i, entry in enumerate(e for e in by_bone(*HOOD_PARTS) if e['material'] == 'cloth'):
    HOOD_RAGS.append((f'hood_rag_{i}', entry['bone']))
    free_copy(f'hood_rag_{i}', [entry], centre(entry))
