"""Pyre King review sculpt, v6. Model pixels, front -Z, anatomical right -X.
Rest poses are authored directly; moving a bone later moves its attached details.
Every part touches the body or the part it hangs from: nothing floats.
"""
import math
from copy import deepcopy

BONES = []
PARTS = []


def bone(name, parent=None, pivot=(0, 0, 0), rotation=None):
    b = dict(name=name, pivot=list(pivot), cubes=[])
    if parent: b['parent'] = parent
    if rotation: b['rotation'] = list(rotation)
    BONES.append(b)
    return name


def cube(b, o, s, material='armor', rotation=None, pivot=None):
    c = dict(origin=[round(v, 5) for v in o], size=[round(v, 5) for v in s])
    if rotation: c.update(rotation=list(rotation), pivot=list(pivot or o))
    next(v for v in BONES if v['name'] == b)['cubes'].append(c)
    PARTS.append((c, material, b))
    return c


def beam(b, a, z, width, depth, material='armor'):
    d = [z[i]-a[i] for i in range(3)]
    L = math.sqrt(sum(v*v for v in d))
    rot = [-math.degrees(math.asin(d[2]/L)), 0, math.degrees(math.atan2(d[0], d[1]))]
    return cube(b, (a[0]-width/2, a[1], a[2]-depth/2), (width, L, depth), material, rot, a)


def ring(b, center, w, h, thickness, material='iron', plane='xy'):
    x, y, z = center; t = thickness
    if plane == 'xy':
        cube(b, (x-w/2, y-h/2, z-t/2), (w, t, t), material)
        cube(b, (x-w/2, y+h/2-t, z-t/2), (w, t, t), material)
        cube(b, (x-w/2, y-h/2+t, z-t/2), (t, h-2*t, t), material)
        cube(b, (x+w/2-t, y-h/2+t, z-t/2), (t, h-2*t, t), material)
    else:
        cube(b, (x-t/2, y-h/2, z-w/2), (t, t, w), material)
        cube(b, (x-t/2, y+h/2-t, z-w/2), (t, t, w), material)
        cube(b, (x-t/2, y-h/2+t, z-w/2), (t, h-2*t, t), material)
        cube(b, (x-t/2, y-h/2+t, z+w/2-t), (t, h-2*t, t), material)


def unit(v):
    length = math.sqrt(sum(x*x for x in v)); return [x/length for x in v]


def cross(a, b):
    return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]


def add(a, b, k=1): return [a[i]+k*b[i] for i in range(3)]


def open_link(b, center, tangent, across, length, width, t, material):
    corners = [add(add(center, tangent, s*length/2), across, d*width/2)
               for s, d in [(-1, -1), (1, -1), (1, 1), (-1, 1)]]
    for j in range(4): beam(b, corners[j], corners[(j+1) % 4], t, t, material)


def lock(b, x, y, z):
    ring(b, (x, y+2.1, z), 3.4, 3.8, .8, 'lock_iron')
    cube(b, (x-2.5, y-2.6, z-.95), (5, 4.6, 1.9), 'lock_iron')
    cube(b, (x-.45, y-1.3, z-1.25), (.9, 1.8, .35), 'hot')


# ---------------------------------------------------------------- body
bone('root')
bone('pelvis', 'root', (0, 32, 1))
cube('pelvis', (-7.5, 30, -4), (15, 9, 9), 'leather')
# A broad belt carries the tabard clasp; it wraps the waist where the old chain did.
cube('pelvis', (-7.8, 35.4, -5), (15.6, 2.6, 10.2), 'leather')
cube('pelvis', (-7.9, 37.1, -5.1), (15.8, 1, 10.4), 'trim')
# A slight forward lean and a lowered head: a duelist sizing up his challenger.
bone('torso', 'pelvis', (0, 36, 0), (6, 0, 0))
# Long cuirass: waist -> abdominal lames -> broad projecting breastplate.
cube('torso', (-7, 34, -4.6), (14, 10, 9.2))
cube('torso', (-9, 43, -5.8), (18, 9, 11))
cube('torso', (-12, 51, -6.8), (24, 8, 12))
for y, w, z in [(41, 15, -5.1)]:
    cube('torso', (-w/2, y, z), (w, .8, .8), 'trim')
for x in [-10.5, 8.5]: cube('torso', (x, 49, -6.9), (2, 8, 1.1))
# A stepped gorget seats the head in the armor; gold bands mark each step.
cube('torso', (-7.2, 58.2, -5.6), (14.4, 3.2, 10.8))
cube('torso', (-7.3, 61, -5.75), (14.6, .7, 11.1), 'trim')
cube('torso', (-6, 61.2, -4.8), (12, 3.4, 9.6))
cube('torso', (-6.1, 64, -4.95), (12.2, .6, 9.9), 'trim')
for sgn in [-1, 1]: cube('torso', (min(sgn*7, sgn*11.6), 58.8, -5.2), (4.6, 2.4, 10.2))

# The shard is set into a gold socket on the breastplate, not hung in front of it.
bone('chest_shard', 'torso', (0, 46, -6))
cube('chest_shard', (-3, 43, -7.2), (6, 6, 1.5), 'gold', [0, 0, 45], (0, 46, -6))
cube('chest_shard', (-2.1, 43.9, -8.1), (4.2, 4.2, 1.1), 'shard', [0, 0, 45], (0, 46, -6))
cube('chest_shard', (-.6, 45.1, -8.6), (1.2, 1.8, .6), 'hot')
# Heat cracks radiate from the socket over the breastplate; the rest stays dark.
for a, b in [((-3.3, 47.9, -5.85), (-7.2, 50.7, -5.85)), ((3.3, 47.9, -5.85), (7.4, 50.9, -5.85)),
             ((-3.8, 45.3, -5.85), (-8.3, 45.9, -5.85)), ((3.8, 45.3, -5.85), (8.3, 46, -5.85)),
             ((-7.2, 50.7, -5.85), (-8.4, 52.6, -6.85)), ((7.4, 50.9, -5.85), (8.6, 53.4, -6.85))]:
    beam('torso', a, b, .4, .25, 'ember')

# Six separated tassets: narrow waist, modest flare and daylight between panels.
for face in ['front', 'back']:
    for side, sgn in [('right', -1), ('left', 1)]:
        zsign = -1 if face == 'front' else 1
        n = bone('coat_flap_'+face+'_'+side, 'pelvis', (sgn*4.8, 35, zsign*4.8))
        for j in range(3):
            y = 29-j*5
            w = (9.4 if sgn < 0 else 6)+j*1.1
            cx = (-2.1 if sgn < 0 else 5.4)+sgn*j*.55
            z = zsign*(7.4+j*.65)
            beam(n, (cx, y, z), (cx, y+7, z-zsign*1.2), w, 1.25, 'coat')
            cube(n, (cx-w/2, y-.1, z-.7), (w, .95, 1.4), 'hem' if j == 2 else 'trim')
for side, sgn in [('right', -1), ('left', 1)]:
    n = bone('coat_flap_side_'+side, 'pelvis', (sgn*8.5, 35, 0))
    for j in range(3):
        y = 29-j*5; depth = 9.6+j*.9; x = sgn*(9.7+j*1.1)
        beam(n, (x, y, 0), (x-sgn*1.6, y+7, 0), 1.25, depth, 'coat')
        cube(n, (x-.7, y-.1, -depth/2), (1.4, .95, depth), 'hem' if j == 2 else 'trim')

# A crimson tabard hangs from a gold clasp on the belt, over the front tassets.
bone('tabard', 'pelvis', (0, 36, -6))
cube('tabard', (-4, 35, -8.6), (8, 1.7, 3.9), 'gold')
beam('tabard', (0, 18.4, -10.5), (0, 35.4, -7.7), 7.4, .9, 'tabard')
beam('tabard', (.35, 16.6, -10.75), (.1, 19, -10.45), 5.2, .9, 'burnt_cloth')

# Bent knees and staggered, broad sabatons anchor the forward-loaded cuirass.
for side, sgn, zoff in [('right', -1, -2.5), ('left', 1, 3)]:
    hip = (sgn*6, 33, 1); knee = (sgn*11, 17, zoff-3); ankle = (sgn*13.5, 5, zoff+.5)
    thigh = bone(side+'_thigh', 'pelvis', hip); beam(thigh, knee, hip, 7.4, 7.2)
    shin = bone(side+'_shin', thigh, knee); beam(shin, ankle, knee, 7, 7)
    cube(shin, (sgn*11-4.2, 15, zoff-7), (8.4, 5, 3), 'joint')
    cube(shin, (sgn*11-4.2, 18.8, zoff-7.3), (8.4, .8, 3.3), 'trim')
    foot = bone(side+'_foot', shin, ankle)
    cube(foot, (sgn*13.5-4.7, 0, zoff-8), (9.4, 4, 13))
    cube(foot, (sgn*13.5-4.2, 3, zoff-4), (8.4, 3.6, 8))
    cube(foot, (sgn*13.5-4.7, 3.2, zoff-8.1), (9.4, .8, 3.2), 'trim')
    cube(foot, (sgn*13.5-4, 5.2, zoff-3.6), (8, 1.2, 7.5), 'trim')

# ---------------------------------------------------------------- head
# A full skull fills the crown band and backs the mask; it sits in the gorget.
bone('neck', 'torso', (0, 59, -1)); cube('neck', (-2.8, 58, -2), (5.6, 6.6, 4.5), 'char')
bone('head', 'neck', (0, 64.5, -.5), (7, 0, 0))
cube('head', (-5.3, 64.5, -4.5), (10.6, 7.3, 8.8), 'char')
cube('head', (-4.6, 71.8, -3.9), (9.2, 1.2, 7.6), 'char')
cube('head', (-4.6, 66, 4.3), (9.2, 4.5, 1), 'char')
bone('death_mask', 'head', (0, 68, -5))
cube('death_mask', (-5.8, 68, -5.5), (11.6, 4, 1.5), 'mask')
for sgn in [-1, 1]:
    cube('death_mask', (sgn*5.1-.65, 65.5, -4.8), (1.3, 5.3, 3.5), 'mask',
         [0, sgn*14, 0], (sgn*5.1, 68, -4.8))
for s in [-1, 1]:
    cube('death_mask', (s*3-2, 63, -5.6), (4, 3.4, 1.5), 'mask')
    cube('death_mask', (s*4.8-1, 66, -5.5), (2, 3, 1.5), 'mask')
    cube('death_mask', (s*2.8-1.7, 67.45, -6), (3.4, .8, .5), 'eye')
    cube('death_mask', (s*2.8-2.1, 68.3, -6.25), (4.2, 1.1, 1.05), 'mask', [0, 0, -s*13], (s*2.8, 68.6, -6))
cube('death_mask', (-.85, 63.5, -7), (1.7, 6, 2), 'mask')
cube('death_mask', (-3.5, 63, -6), (7, 1.4, 1), 'mask')
# Molten gold weeps from the eye slits: the mask is slowly melting on him.
for x, low in [(-2.8, 65.3), (2.8, 64.3)]:
    cube('death_mask', (x-.4, low, -6.05), (.8, 67.45-low, .45), 'molten')

bone('ash_beard', 'head', (0, 64, -5))
# Locks rest on the gorget and breastplate and stop above the shard socket.
for x, y, w, h, ztop, zbottom, lean in [
    (-4.4, 56.8, 2.6, 6.6, -6, -7.4, -.5), (-1.6, 53.4, 3, 10.2, -6.1, -8.2, -.25),
    (1.4, 54.6, 2.8, 9, -6.1, -8, .3), (4.2, 57.6, 2.4, 5.8, -6, -7.3, .55),
    (-2.9, 54.8, 2.2, 4.7, -7.4, -8.4, -.4), (0, 50.6, 2.4, 7.4, -7.9, -8.7, 0),
    (2.6, 53.6, 2, 5.4, -7.4, -8.3, .4), (-.9, 52.2, 1.6, 3.3, -8.4, -8.8, -.3)]:
    beam('ash_beard', (x+lean, y, zbottom), (x, y+h, ztop), w, 1.8, 'ash')
    beam('ash_beard', (x+lean*1.6, y-1.6, zbottom-.15), (x+lean, y+.4, zbottom), w*.5, 1.4, 'ash')

# Sharp openwork crown points around a band that hugs the skull. One point is snapped.
bone('crown', 'head', (0, 72, 0))
cube('crown', (-5.9, 70.9, -5.35), (11.8, .6, 10.7), 'char')
for o, s in [((-6, 71.4, -5.4), (12, 2, 1.4)), ((-6, 71.4, 4), (12, 2, 1.4)),
             ((-6, 71.4, -4), (1.3, 2, 8)), ((4.7, 71.4, -4), (1.3, 2, 8))]:
    cube('crown', o, s, 'gold')
for x, z, width, top, side, broken in [(0, -4.7, 5.4, 80, False, False),
                                       (-4.1, -4.4, 4.2, 77.4, False, False),
                                       (4.1, -4.4, 4.2, 77.4, False, True),
                                       (-5.3, 1.2, 4.4, 77, True, False),
                                       (5.3, 1.2, 4.4, 77, True, False)]:
    base = 73; h = top-base
    tiers = [(base, h*.36, width, 1.7), (base+h*.36, h*.28, width*.62, 1.5),
             (base+h*.64, h*.22, width*.34, 1.3), (base+h*.86, h*.14, min(1, width*.16), 1.1)]
    if broken:
        # The tip has sheared off: an offset stub with a slanted break.
        tiers = [(base, h*.36, width, 1.7), (base+h*.36, h*.2, width*.55, 1.5)]
    for k, (y, th, w, d) in enumerate(tiers):
        off = width*.24 if broken and k == 1 else 0
        if side: cube('crown', (x-d/2, y, z-w/2+off), (d, th, w), 'gold')
        else: cube('crown', (x-w/2+off, y, z-d/2), (w, th, d), 'gold')
cube('crown', (-2.4, 71.5, -6.8), (4.8, 4.5, 1.5), 'gold')
cube('crown', (-1.5, 72.4, -7.2), (3, 2.8, .6), 'ruby')

# ---------------------------------------------------------------- royal mantle
# A high standing collar frames the head from behind; lapels lie on the chest.
bone('mantle_collar', 'torso', (0, 59, 0))
beam('mantle_collar', (0, 58.4, 5.6), (0, 66.2, 7), 12, 1.5, 'crimson')
beam('mantle_collar', (0, 64.8, 6.9), (0, 66.8, 7.4), 13.6, 1.4, 'crimson')
for sgn in [-1, 1]:
    beam('mantle_collar', (sgn*6.9, 58.4, 1.6), (sgn*7.3, 62.8, 2.8), 1.4, 6, 'crimson')
# Seven unequal, overlapping cape panels hug the back, fold at staggered heights,
# then fall over the back tassets to a ragged hem.
for i, (x, w, fold, yb, zo) in enumerate([(-11.6, 3.8, 43, 23, 0), (-8.2, 6.6, 37.4, 19.5, .9),
                                          (-3.6, 4.8, 44, 22, 0), (.6, 7.4, 38.6, 18.5, .9),
                                          (5.2, 5, 42.6, 21.5, 0), (8.9, 6.2, 36.8, 20, .9),
                                          (11.8, 3.6, 44.4, 24, 0)]):
    n = bone('mantle_scrap_%02d' % (i+1), 'torso', (x, 59.6, 5.5))
    drift = x*.08
    beam(n, (x*.95, fold, 5.75+zo), (x, 59.6, 5.45+zo), w, 1.1, 'crimson')
    beam(n, (x+drift, yb, 9.8+zo), (x*.95, fold+.3, 5.75+zo), w, 1.1, 'crimson')
    beam(n, (x+drift*1.2, yb-2.6, 10.1+zo), (x+drift, yb+.5, 9.75+zo), w*.62, 1.1, 'burnt_cloth')
for side, sgn in [('right', -1), ('left', 1)]:
    n = bone('mantle_scrap_front_'+side, 'torso', (sgn*6.5, 58.6, -5.8))
    beam(n, (sgn*8.6, 51.4, -7.3), (sgn*6.5, 58.6, -6.2), 2.6, 1, 'crimson')
    beam(n, (sgn*8.8, 49.6, -7.4), (sgn*8.6, 51.8, -7.3), 1.8, 1, 'burnt_cloth')

# ---------------------------------------------------------------- guard and arms
# Plough guard: both fists at the belly, the burning blade raised up and out at the
# challenger. The arms stay low, so the beard, shard and chain read from the front.
P = (-2, 32, -13)
D = unit((-.64, .72, -.27))
SWORD_ROT = (-math.degrees(math.asin(D[2])), 0, math.degrees(math.atan2(D[0], D[1])))


def sword_local(x, y, z):
    rx, _, rz = map(math.radians, SWORD_ROT)
    yy = math.cos(rx)*y+math.sin(rx)*z; zz = -math.sin(rx)*y+math.cos(rx)*z
    return (P[0]+math.cos(rz)*x+math.sin(rz)*yy,
            P[1]-math.sin(rz)*x+math.cos(rz)*yy, P[2]+zz)


def sword_point(y): return sword_local(0, y, 0)


WRISTS = {}
for side, sgn, station, elbow in [('right', -1, 12.3, (-18.5, 41, -6.5)),
                                  ('left', 1, 5.3, (9.5, 40.6, -8.6))]:
    shoulder = (sgn*14.2, 56, -1)
    hand = sword_point(station)
    upper = bone(side+'_upper_arm', 'torso', shoulder)
    mid = [shoulder[i]+(elbow[i]-shoulder[i])*.55 for i in range(3)]
    beam(upper, mid, shoulder, 6.2, 6.4); beam(upper, elbow, mid, 5.2, 5.4)
    # Three stepped lames wrap the shoulder cap; each lower one tilts further out.
    for cx, y, w, h, d, tilt in [(sgn*13.6, 56.2, 9.6, 4.2, 10.6, 24), (sgn*15.2, 52.4, 7.6, 3.8, 9.6, 34)]:
        rot = [0, 0, sgn*tilt]; pv = (cx, y+h/2, 0)
        cube(upper, (cx-w/2, y, -d/2), (w, h, d), 'armor', rot, pv)
        cube(upper, (cx-w/2-.05, y-.02, -d/2-.05), (w+.1, .6, d+.1), 'trim', rot, pv)
    # The burned royal mantle lies over the shoulder cap and hangs down the arm.
    # The cape comes over the whole shoulder: across the top, down the front, and down
    # behind the pauldron, where it joins the cape panels on the back.
    beam(upper, (sgn*18, 58.2, .3), (sgn*7.8, 62.5, .3), 1.1, 13.2, 'crimson')
    beam(upper, (sgn*16.2, 44.5, 7.5), (sgn*13.4, 61.6, 6.6), 8.4, 1.1, 'crimson')
    beam(upper, (sgn*16.6, 41.8, 7.7), (sgn*16.2, 45, 7.5), 5.2, 1.1, 'burnt_cloth')
    beam(upper, (sgn*14.6, 50.4, -6.2), (sgn*12.6, 61.6, -6.1), 5.6, .9, 'crimson')
    beam(upper, (sgn*14.9, 48.2, -6.25), (sgn*14.6, 50.8, -6.2), 3.6, .9, 'burnt_cloth')
    lower = bone(side+'_forearm', upper, elbow)
    axis = unit([hand[i]-elbow[i] for i in range(3)])
    wrist = add(hand, axis, -3.2); cuff = add(wrist, axis, -3.4)
    WRISTS[side] = (wrist, axis)
    fmid = add(elbow, axis, math.dist(elbow, wrist)*.5)
    beam(lower, fmid, elbow, 5.4, 5.6, 'joint'); beam(lower, wrist, fmid, 4.6, 4.8, 'joint')
    cube(lower, (elbow[0]-2.9, elbow[1]-2, elbow[2]-2.9), (5.8, 4, 5.8), 'armor')
    cube(lower, (elbow[0]-2.95, elbow[1]-.3, elbow[2]-2.95), (5.9, .6, 5.9), 'trim')
    beam(lower, cuff, wrist, 5.8, 6, 'gauntlet')
    beam(lower, add(wrist, axis, -.7), wrist, 6.2, 6.4, 'trim')
    n = bone(side+'_hand', lower, hand)

    # A real fist around the grip: knuckle shell, gold knuckle band and a thumb.
    def glove(o, size, mat):
        return cube(n, tuple(hand[k]+o[k] for k in range(3)), size, mat, SWORD_ROT, hand)
    glove((-3, -2.2, -3.1), (6, 4.4, 6.2), 'gauntlet')
    glove((-3.15, 1.6, -3.25), (6.3, .7, 6.5), 'trim')
    glove((-2.6, -1.9, 3.1), (5.2, 3.8, .9), 'gauntlet')
    glove((-2.1, -1.5, 4), (4.2, 3, .7), 'gauntlet')
    glove((2.9, -1.2, -2.4), (1.6, 2.6, 3.4), 'gauntlet')
    glove((1.4, 2.3, -2.4), (2, 1.2, 2.2), 'gauntlet')

# One heavy curse chain, from the left shoulder to the right hip, hugging the armor.
# Flat links lie on the body; edge-on links are single bars between them.
def body_extent(y):
    if y >= 51: return 12, -6.8, 5.2
    if y >= 43: return 9, -5.8, 5.2
    if y >= 35.4: return 7.8, -5, 5.2
    return 7.5, -4, 5


def chain_loop(b, y0, slope, count, t=1.55, width=4.8):
    pts = []
    for j in range(count):
        a = 2*math.pi*(j+.5)/count; co, si = math.cos(a), math.sin(a)
        y = y0
        for _ in range(4):
            hw, zf, zb = body_extent(y); zc = (zf+zb)/2; hd = (zb-zf)/2
            x = (hw+t/2)*math.copysign(abs(co)**.55, co)
            z = zc+(hd+t/2)*math.copysign(abs(si)**.55, si)
            y = y0+slope*x
        pts.append([x, y, z])
    for j, c in enumerate(pts):
        prev, nxt = pts[j-1], pts[(j+1) % count]
        tangent = unit([nxt[k]-prev[k] for k in range(3)])
        hw, zf, zb = body_extent(c[1]); zc = (zf+zb)/2
        radial = unit([c[0], 0, c[2]-zc])
        pitch = (math.dist(c, prev)+math.dist(c, nxt))/2
        if c[2] > 3.8 and abs(c[0]) < 12.5: continue
        if j % 2 == 0:
            across = unit(cross(radial, tangent))
            open_link(b, add(c, radial, .2), tangent, across, pitch*1.12, width, t, 'curse')
        else:
            mid = add(c, radial, .9)
            beam(b, add(mid, tangent, -pitch*.62), add(mid, tangent, pitch*.62), t, t, 'curse')
    return pts


n = bone('curse_chain_chest', 'torso', (0, 40.5, 0))
chain = chain_loop(n, 40.5, .85, 12)
# The padlock hangs beside the shard: his death is locked in.
front = min((p for p in chain if p[2] < 0), key=lambda p: abs(p[0]-6))
lock(n, front[0], front[1]-1.7, front[2]-1.3)

# ---------------------------------------------------------------- the sword
bone('sword', 'right_hand', P, SWORD_ROT)


def weapon_cube(o, s, mat): return cube('sword', tuple(P[i]+o[i] for i in range(3)), s, mat)


for y, w in [(1.3, 3), (1.9, 4.4), (2.5, 3)]: weapon_cube((-w/2, y, -1.3), (w, .6, 2.6), 'gold')
weapon_cube((-1.1, 3, -1), (2.2, 11.6, 2), 'grip')
weapon_cube((-8.5, 14.6, -1.5), (17, 1.4, 3), 'gold')
weapon_cube((-3.5, 16, -.8), (7, 42, 1.6), 'steel')
weapon_cube((-2.5, 58, -.75), (5, 8, 1.5), 'steel')
weapon_cube((-1.6, 66, -.6), (3.2, 4, 1.2), 'steel')
weapon_cube((-.65, 70, -.45), (1.3, 2, .9), 'steel')
weapon_cube((-.6, 16, -.95), (1.2, 46, .3), 'fuller')
bone('pommel_ring', 'sword', P); ring('pommel_ring', (P[0], P[1]+1.3, P[2]), 2.7, 2.6, .55, 'dark_iron')
# Rig compatibility only. Flames are world-space effects, never baked cubes.
bone('sword_fire', 'sword', P)
BLADE_SOCKETS = [[0, 16+i*3.5, 0] for i in range(17)]
GUARD_DIRECTION = [round(v, 6) for v in D]

# The shackle closes round the right gauntlet cuff; three slack links hang below
# the fists to the pommel ring.
wrist, axis = WRISTS['right']
shackle = add(wrist, axis, -1.5)
bone('right_wrist_shackle', 'right_hand', shackle)
up = unit(cross(axis, [1, 0, 0])); side_v = cross(axis, up)
corners = [add(add(shackle, up, a*4.3), side_v, b*4.2) for a, b in [(-1, -1), (1, -1), (1, 1), (-1, 1)]]
for j in range(4): beam('right_wrist_shackle', corners[j], corners[(j+1) % 4], 1.1, 1.1, 'dark_iron')
bone('hanging_wrist_chain', 'right_wrist_shackle', shackle)
ringpos = sword_point(1.3)
low = min(corners, key=lambda p: p[1])
S = (low[0], low[1]-.5, low[2]); E = (ringpos[0], ringpos[1]-.9, ringpos[2])
links = 5; sag = 3.6


def tether_point(u):
    k = 4*u*(1-u)
    return [S[i]+(E[i]-S[i])*u+(0, -sag*k, -1.2*k)[i] for i in range(3)]


for i in range(links):
    a, z = tether_point(i/links), tether_point((i+1)/links)
    tangent = unit([z[k]-a[k] for k in range(3)])
    mid = [(a[k]+z[k])/2 for k in range(3)]
    length = math.dist(a, z)*1.3
    if i % 2 == 0:
        across = unit(cross(tangent, [0, 0, 1]))
        open_link('hanging_wrist_chain', mid, tangent, across, length, 2.8, .95, 'iron')
    else:
        beam('hanging_wrist_chain', add(mid, tangent, -length/2), add(mid, tangent, length/2), .95, .95, 'iron')


def sword_bones():
    """Standalone projectile, tip +Y, pommel ring bottom at Y=0, identity root."""
    names = {'sword', 'pommel_ring', 'sword_fire'}
    out = deepcopy([b for b in BONES if b['name'] in names])
    for b in out:
        b['pivot'] = [round(b['pivot'][i]-P[i], 5) for i in range(3)]
        if b['name'] == 'sword': b.pop('parent', None); b.pop('rotation', None)
        for c in b['cubes']:
            c['origin'] = [round(c['origin'][i]-P[i], 5) for i in range(3)]
            if 'pivot' in c: c['pivot'] = [round(c['pivot'][i]-P[i], 5) for i in range(3)]
    return out
