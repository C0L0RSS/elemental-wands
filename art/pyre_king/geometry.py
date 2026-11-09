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


def beam_frame(a, z):
    """The beam's local X (width), Y (length) and Z (depth) axes in source space."""
    d = [z[i]-a[i] for i in range(3)]; L = math.sqrt(sum(v*v for v in d)); h = math.hypot(d[0], d[1])
    return ([d[1]/h, -d[0]/h, 0], [v/L for v in d], [-d[2]/L*d[0]/h, -d[2]/L*d[1]/h, h/L])


def limb(b, a, z, t0, t1, width, depth, material='armor', out=0, back=0):
    """A beam over the stretch t0..t1 of the axis a->z, shifted `out` along its width axis
    and `back` along its depth axis, so plates can sit on a limb's faces."""
    ex, _, ez = beam_frame(a, z)
    def at(t): return [a[i]+(z[i]-a[i])*t+ex[i]*out+ez[i]*back for i in range(3)]
    return beam(b, at(t0), at(t1), width, depth, material)


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

# No coat: a close fauld of two lames hugs the hips under the belt and stops where the
# thighs begin, so the legs read in full. Each lame wraps the pelvis as one band; the upper
# one sits on top of the wider lower one, so its gold edge shows above the step. The lower
# lame is shallow, so more of the thigh shows, and broad enough to cover the top of the
# full-width cuisses.
FAULD = [  # half width, bottom, top, front z, back z
    (10.4, 32.8, 35.9, -6.0, 5.5),
    (14.2, 30.4, 32.8, -6.6, 6.1)]
for i, (hw, y0, y1, zf, zb) in enumerate(FAULD):
    cube('pelvis', (-hw, y0, zf), (2*hw, y1-y0, zb-zf))
    cube('pelvis', (-hw-.1, y0-.05, zf-.1), (2*hw+.2, .7, zb-zf+.2), 'hem' if i else 'trim')

# A crimson tabard hangs from a gold clasp on the belt, lies on the fauld's front and ends
# at mid-thigh, well above the knees.
bone('tabard', 'pelvis', (0, 36, -6))
cube('tabard', (-4, 35, -8.6), (8, 1.7, 3.9), 'gold')
beam('tabard', (0, 25, -8.0), (0, 35.4, -7.3), 6.6, .9, 'tabard')
beam('tabard', (.3, 23.2, -8.15), (.1, 25.6, -8.0), 4.6, .9, 'burnt_cloth')

# Heavy plate legs stand as straight columns under the broad cuirass, and broad staggered
# sabatons anchor them. Seen from the front, each knee sits on the line from hip to ankle,
# so thigh and shin run on as one leg; the stance bends the knees only forward. The knees
# are set so both legs are the same length, and the gaits need no pelvis hitch.
for side, sgn, zoff, kz in [('right', -1, -2.5, -4.5), ('left', 1, 3, -1.47)]:
    hip = (sgn*6, 33, 1); ankle = (sgn*13.5, 5, zoff+.5)
    knee = (hip[0]+(ankle[0]-hip[0])*(hip[1]-17)/(hip[1]-ankle[1]), 17, kz)
    thigh = bone(side+'_thigh', 'pelvis', hip)
    # The cuisse is full width from the knee up under the fauld, its outer side running on
    # in one line with the greave's, and its inner face where the gaits need it. Above the
    # knee its front and back corners round off in steps toward the fauld, so a thigh lifted
    # into a stride, or trailing, slides under the fauld's corners instead of cutting out
    # through them, and its front clears the belt clasp at the top of a run stride. Its
    # hidden top is narrower outside, so it stays under the fauld when a gait swings the
    # slanted thigh upright.
    def plate(t0, t1, inner, outer, front, back, material='armor'):
        limb(thigh, knee, hip, t0, t1, outer-inner, front+back, material,
             out=sgn*(outer+inner)/2, back=(back-front)/2)
    plate(0, .24, -5.2, 5.6, 4.9, 4.9)           # a little narrower at the knee
    plate(.2, .4, -5.7, 6.3, 5.2, 5.2)           # the heavy cuisse
    plate(.36, .76, -5.7, 6.3, 1, 5.2)           # its full width, up under the fauld
    plate(.72, .97, -5.4, 2.8, 3.4, 3.4)         # its hidden top
    plate(.36, .9, -5.5, 4, 3.4, 0)              # the rounded front, in two steps,
    plate(.36, .6, -2.6, 2.8, 5.2, 0)            # lower at the top
    plate(.24, .5, -4.4, 2.4, 5.9, -5)           # its raised front plate
    shin = bone(side+'_shin', thigh, knee)
    # The greave tapers gently in steps to the ankle; the calf fills out behind it.
    limb(shin, ankle, knee, 0, .32, 8.6, 8.6)                     # the ankle
    limb(shin, ankle, knee, .28, .6, 9.6, 9.2)                    # lower greave
    limb(shin, ankle, knee, .55, 1, 10.4, 9.8)                    # broad greave under the knee
    limb(shin, ankle, knee, .38, .86, 10.4, 5, back=3.4)          # the calf behind
    # The knee cop follows the shin: a broad plate with a boss on it, so it reads rounded,
    # and a thin gold edge along its lower rim. It stays inside the leg's line, and no gold
    # faces up when the shin swings back.
    limb(shin, ankle, knee, .8, 1.16, 10.6, 3.4, 'joint', back=-4.8)    # knee cop
    limb(shin, ankle, knee, .86, 1.1, 6.4, 1.2, 'joint', back=-6.9)     # its boss
    limb(shin, ankle, knee, .795, .842, 10.8, 1, 'trim', back=-6.05)    # its gold lower edge
    foot = bone(side+'_foot', shin, ankle)
    x = ankle[0]+sgn*.6          # the sabaton sits a little outward of the ankle
    cube(foot, (x-5.6, 0, zoff-8), (11.2, 4.4, 13))
    cube(foot, (x-5.2, 3.6, zoff-4.6), (10.4, 3.8, 9.2))
    cube(foot, (x-5.7, 3.6, zoff-8.15), (11.4, .9, 1.5), 'trim')   # a thin gold toe edge

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
# Locks rest on the gorget and breastplate and stop above the shard socket. Each lock
# narrows in three steps to a curled point, so the beard reads as hair, not slabs.
for x, y, w, h, ztop, zbottom, lean in [
    (-4.4, 56.8, 2.6, 6.6, -6, -7.4, -.5), (-1.6, 53.4, 3, 10.2, -6.1, -8.2, -.25),
    (1.4, 54.6, 2.8, 9, -6.1, -8, .3), (4.2, 57.6, 2.4, 5.8, -6, -7.3, .55),
    (-2.9, 54.8, 2.2, 4.7, -7.4, -8.4, -.4), (0, 50.6, 2.4, 7.4, -7.9, -8.7, 0),
    (2.6, 53.6, 2, 5.4, -7.4, -8.3, .4), (-.9, 52.2, 1.6, 3.3, -8.4, -8.8, -.3)]:
    curl = .55 if lean >= 0 else -.55
    mid = (x+lean*.45, y+h*.5, (ztop+zbottom)/2)
    beam('ash_beard', mid, (x, y+h, ztop), w, 1.8, 'ash')
    beam('ash_beard', (x+lean, y+.2, zbottom), (mid[0], mid[1]+.4, mid[2]), w*.74, 1.55, 'ash')
    beam('ash_beard', (x+lean*1.5+curl, y-1.9, zbottom-.2), (x+lean, y+.6, zbottom), w*.42, 1.2, 'ash')

# Five openwork points rise from the band. Two gold bars lean together into each point
# over a solid foot, and a small diamond finial caps it. One point has sheared off.
bone('crown', 'head', (0, 72, 0))
cube('crown', (-5.9, 70.9, -5.35), (11.8, .6, 10.7), 'char')
for o, s in [((-6, 71.4, -5.4), (12, 2, 1.4)), ((-6, 71.4, 4), (12, 2, 1.4)),
             ((-6, 71.4, -4), (1.3, 2, 8)), ((4.7, 71.4, -4), (1.3, 2, 8))]:
    cube('crown', o, s, 'gold')


def crown_point(x, z, width, top, side, broken, base=73.1, t=1.25, d=1.5):
    def at(u, y): return (x, y, z+u) if side else (x+u, y, z)

    def bar(a, b): beam('crown', at(*a), at(*b), *((d, t) if side else (t, d)), 'gold')

    def block(u0, y0, wu, hy, depth):
        o = at(u0, y0)
        if side: cube('crown', (o[0]-depth/2, o[1], o[2]), (depth, hy, wu), 'gold')
        else: cube('crown', (o[0], o[1], o[2]-depth/2), (wu, hy, depth), 'gold')
    half = width/2-t*.45; h = top-base
    block(-width/2, base-.3, width, h*.24+.3, d+.2)          # the solid foot
    if broken:
        # Both bars snapped at different heights; the finial is gone.
        bar((-half, base), (-half*.42, base+h*.6))
        bar((half, base), (half*.72, base+h*.27))
        return
    bar((-half, base), (0, top-1.05)); bar((half, base), (0, top-1.05))
    s = 1.2; c = at(0, top-s/math.sqrt(2))
    if side: cube('crown', (c[0]-d/2, c[1]-s/2, c[2]-s/2), (d, s, s), 'gold', [45, 0, 0], c)
    else: cube('crown', (c[0]-s/2, c[1]-s/2, c[2]-d/2), (s, s, d), 'gold', [0, 0, 45], c)


for args in [(0, -4.7, 5.4, 80, False, False), (-4.1, -4.4, 4.2, 77.4, False, False),
             (4.1, -4.4, 4.2, 77.4, False, True), (-5.3, 1.2, 4.4, 77, True, False),
             (5.3, 1.2, 4.4, 77, True, False)]:
    crown_point(*args)
cube('crown', (-2.4, 71.5, -6.8), (4.8, 4.5, 1.5), 'gold')
cube('crown', (-1.5, 72.4, -7.2), (3, 2.8, .6), 'ruby')

# ---------------------------------------------------------------- royal mantle
# A high standing collar frames the head from behind; lapels lie on the chest.
bone('mantle_collar', 'torso', (0, 59, 0))
beam('mantle_collar', (0, 58.4, 5.6), (0, 66.2, 7), 12, 1.5, 'crimson')
beam('mantle_collar', (0, 64.8, 6.9), (0, 66.8, 7.4), 13.6, 1.4, 'crimson')
for sgn in [-1, 1]:
    beam('mantle_collar', (sgn*6.9, 58.4, 1.6), (sgn*7.3, 62.8, 2.8), 1.4, 6, 'crimson')
# Seven unequal, overlapping cape panels hug the back and fold at staggered heights. Below
# the fold each falls a little behind plumb in the world (the torso leans 6 degrees, so in
# its frame that is a backward slope), clear of the fauld and the thighs, and the burnt
# hem ends low on the thigh, well above the knee, so the legs show from behind. The hem
# rises toward the sides, and the outer panels draw in, so no cloth frames the hips from
# the front. Each panel's pivot stays at the top of its shoulder (x).
PLUMB = math.tan(math.radians(6))
for i, (x, xb, w, fold, yb, zo) in enumerate([
        (-11.6, -9.8, 3.6, 43, 29.6, 0), (-8.2, -7.5, 6.4, 37.4, 26.6, .9),
        (-3.6, -3.6, 4.8, 44, 28, 0), (.6, .6, 7.4, 38.6, 25, .9),
        (5.2, 5.2, 5, 42.6, 27.6, 0), (8.9, 8.1, 6.2, 36.8, 26.2, .9),
        (11.8, 10, 3.4, 44.4, 30, 0)]):
    n = bone('mantle_scrap_%02d' % (i+1), 'torso', (x, 59.6, 5.5))
    xf = math.copysign(min(abs(x*.95), abs(xb)), x); zb = 5.75+PLUMB*(fold-yb)+.6+zo+2
    beam(n, (xf, fold, 5.75+zo), (x, 59.6, 5.45+zo), w, 1.1, 'crimson')
    beam(n, (xb, yb, zb), (xf, fold+.3, 5.75+zo), w, 1.1, 'crimson')
    beam(n, (xb+x*.01, yb-2.6, zb+.3+PLUMB*2.6), (xb, yb+.5, zb-.05), w*.62, 1.1, 'burnt_cloth')
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
    # Heavy arms: a thick upper arm and a broad couter at the elbow. The upper arm starts a
    # little below the shoulder joint, so its corners stay inside the shoulder plates.
    beam(upper, mid, add(shoulder, unit([elbow[i]-shoulder[i] for i in range(3)]), 1.2), 7.6, 7.2)
    beam(upper, elbow, mid, 7, 6.8)
    # The shoulder plates hang on their own bone, from the chest at the shoulder joint, not
    # on the arm: rig.Solver turns the plates only part of the way the upper arm turns (a
    # little for the swings of a stride, so they stay steady with the chest; most of the way
    # for a big raise, so the arm never leaves them). The mantle on them rides along.
    plate = bone(side+'_pauldron', 'torso', shoulder)
    # Two small stepped lames cap the shoulder; the lower one sits further forward, following
    # the arm down to the elbow. Its lower outer corner reaches just past the arm, so the
    # arm comes out from under it rather than through its end; it slopes about as gently
    # as the upper one, so it adds almost nothing to the shoulders' width. The sword arm
    # angles out from the body, so its lames sit a little further out. The arm moves a
    # little under its plate, so the lower lame leaves it some room: its inner end runs into
    # the chest, and its outer end, front and back stand a little clear of the arm.
    (up_x, up_w), (lo_x, lo_w) = ((14.3, 9), (15.7, 8.6)) if sgn < 0 else ((14, 8.4), (14.5, 7.6))
    LAMES = [(sgn*up_x, 56.8, up_w, 3.2, 9.2, -1.8, 16), (sgn*lo_x, 54.2, lo_w, 2.8, 10.6, -2.05, 14)]
    for cx, y, w, h, d, zc, tilt in LAMES:
        rot = [0, 0, sgn*tilt]; pv = (cx, y+h/2, zc)
        cube(plate, (cx-w/2, y, zc-d/2), (w, h, d), 'armor', rot, pv)
        cube(plate, (cx-w/2-.05, y-.02, zc-d/2-.05), (w+.1, .6, d+.1), 'trim', rot, pv)
    # The burned royal mantle lies over the inner part of the shoulder, from the collar to
    # the middle of the upper lame and from behind its front edge back to the cape, so the
    # iron plate and its gold edge show on top and in front; then it hangs down behind the
    # arm, where it joins the cape panels on the back.
    cx, y, w, h, d, zc, tilt = LAMES[0]; a = math.radians(tilt)
    top = (cx+sgn*math.sin(a)*h/2, y+h/2+math.cos(a)*h/2)
    u = (sgn*math.cos(a), -math.sin(a)); nrm = (sgn*math.sin(a), math.cos(a))
    s_in = (7.8-abs(top[0]))/math.cos(a)
    def on_top(s, lift=.3): return (top[0]+u[0]*s+nrm[0]*lift, top[1]+u[1]*s+nrm[1]*lift)
    outer, inner = on_top(1.0), on_top(s_in)
    beam(plate, (outer[0], outer[1], 1.5), (inner[0], inner[1], 1.5), 1.1, 9.4, 'crimson')
    beam(plate, (sgn*15, 45, 7.2), (sgn*14.4, inner[1]-1.6, 6.55), 5.6, 1.1, 'crimson')
    beam(plate, (sgn*15.2, 42.4, 7.35), (sgn*15, 45.5, 7.2), 3.8, 1.1, 'burnt_cloth')
    lower = bone(side+'_forearm', upper, elbow)
    axis = unit([hand[i]-elbow[i] for i in range(3)])
    wrist = add(hand, axis, -3.2); cuff = add(wrist, axis, -3.6)
    WRISTS[side] = (wrist, axis)
    fmid = add(elbow, axis, math.dist(elbow, wrist)*.5)
    # Thick vambraces taper toward the gauntlet; they end inside its cuff, so the wrist
    # itself stays slim.
    beam(lower, fmid, elbow, 7, 7.2, 'joint'); beam(lower, add(cuff, axis, .2), fmid, 6.2, 6.4, 'joint')
    cube(lower, (elbow[0]-3.7, elbow[1]-2.4, elbow[2]-3.7), (7.4, 4.8, 7.4), 'armor')
    cube(lower, (elbow[0]-3.75, elbow[1]-.3, elbow[2]-3.75), (7.5, .6, 7.5), 'trim')
    # The gauntlet cuff flares toward the elbow and slims at the wrist, so the fist reads
    # as its own shape instead of running on from a cuff of the same thickness.
    beam(lower, cuff, wrist, 5.0, 5.2, 'gauntlet')
    beam(lower, add(cuff, axis, -.5), add(cuff, axis, 1.3), 6.8, 7, 'gauntlet')
    beam(lower, add(cuff, axis, -.6), add(cuff, axis, 0), 7.1, 7.3, 'trim')
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
    if y >= FAULD[0][2]: return 7.8, -5, 5.2
    hw, _, _, zf, zb = FAULD[0]          # at the right hip it rides over the fauld
    return hw, zf, zb


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
chain = chain_loop(n, 41.4, .68, 12)
# The padlock hangs beside the shard: his death is locked in.
front = min((p for p in chain if p[2] < 0), key=lambda p: abs(p[0]-6))
lock(n, front[0], front[1]-1.7, front[2]-1.3)

# ---------------------------------------------------------------- the sword
bone('sword', 'right_hand', P, SWORD_ROT)


def weapon_cube(o, s, mat): return cube('sword', tuple(P[i]+o[i] for i in range(3)), s, mat)


for y, w in [(1.3, 3), (1.9, 4.4), (2.5, 3)]: weapon_cube((-w/2, y, -1.3), (w, .6, 2.6), 'gold')
weapon_cube((-1.1, 3, -1), (2.2, 11.6, 2), 'grip')
weapon_cube((-8.5, 14.6, -1.5), (17, 1.4, 3), 'gold')
BLADE = [((-3.5, 16, -.8), (7, 42, 1.6)), ((-2.5, 58, -.75), (5, 8, 1.5)),
         ((-1.6, 66, -.6), (3.2, 4, 1.2)), ((-.65, 70, -.45), (1.3, 2, .9))]
for o, s in BLADE: weapon_cube(o, s, 'steel')
weapon_cube((-.6, 16, -.95), (1.2, 46, .3), 'fuller')
bone('pommel_ring', 'sword', P); ring('pommel_ring', (P[0], P[1]+1.3, P[2]), 2.7, 2.6, .55, 'dark_iron')
# Rig compatibility only. Flames are world-space effects, never baked cubes.
bone('sword_fire', 'sword', P)
BLADE_SOCKETS = [[0, 16+i*3.5, 0] for i in range(17)]
# The blade's half width and half thickness at each socket, in model pixels. Flames root
# on whichever edge of this cross-section is uppermost in the world.
BLADE_HALF_EXTENTS = [next([-o[0], -o[2]] for o, s in BLADE if o[1] <= y < o[1]+s[1] or (o, s) == BLADE[-1])
                      for _, y, _ in BLADE_SOCKETS]
GUARD_DIRECTION = [round(v, 6) for v in D]

# The shackle closes snugly round the slim wrist of the right gauntlet; an eye under it
# takes the tether, whose five small links hang in a shallow loop behind the grip to
# the pommel ring.
wrist, axis = WRISTS['right']
shackle = add(wrist, axis, -1.3)
bone('right_wrist_shackle', 'right_hand', shackle)
up = unit(cross(axis, [1, 0, 0])); side_v = cross(axis, up)
corners = [add(add(shackle, up, a*3.1), side_v, b*3.1) for a, b in [(-1, -1), (1, -1), (1, 1), (-1, 1)]]
for j in range(4): beam('right_wrist_shackle', corners[j], corners[(j+1) % 4], .95, .95, 'dark_iron')
low = min(corners, key=lambda p: p[1])
cube('right_wrist_shackle', (low[0]-.7, low[1]-1.2, low[2]-.7), (1.4, 1.2, 1.4), 'dark_iron')
bone('hanging_wrist_chain', 'right_wrist_shackle', shackle)
ringpos = sword_point(1.3)
S = (low[0], low[1]-1.1, low[2]); E = (ringpos[0], ringpos[1]-.9, ringpos[2])
links = 5; sag = 2.2


def tether_point(u):
    k = 4*u*(1-u)
    return [S[i]+(E[i]-S[i])*u+(0, -sag*k, 1.1*k)[i] for i in range(3)]


for i in range(links):
    a, z = tether_point(i/links), tether_point((i+1)/links)
    tangent = unit([z[k]-a[k] for k in range(3)])
    mid = [(a[k]+z[k])/2 for k in range(3)]
    length = math.dist(a, z)*1.25
    if i % 2 == 0:
        across = unit(cross(tangent, [0, 0, 1]))
        open_link('hanging_wrist_chain', mid, tangent, across, length, 2.2, .8, 'iron')
    else:
        beam('hanging_wrist_chain', add(mid, tangent, -length/2), add(mid, tangent, length/2), .8, .8, 'iron')


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
