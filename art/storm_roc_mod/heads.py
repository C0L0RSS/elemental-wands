"""Head variants for the Storm Roc, so the face can be picked side by side.

`current` is the installed face. The others reshape blocks on the head bone after refine()
(every existing block keeps its UV island; new blocks get new islands) and repaint the face:
angled brows that dip toward the bill and hang over the eye, a lid across the top of the eye,
smaller or slit pupils, and in the less realistic ones glowing eyes, horns and war paint.

    build_mod.refine(model, head='scowl')
    build_texture_v3.build(head='scowl')
"""
import numpy as np

# brow: a ridge laid along each eye. Its lower edge crosses the eye `h0` above the eye's centre
#   and rises `slope` per pixel toward the back (so it dips toward the bill), over `span`
#   pixels in front of and behind the eye; `width` is its depth out from the head, `thick` its
#   height, `over` how far its outer face stands proud of the eye, `roll` its tilt over the eye.
# eye: squash (vertical size of the eye block), pupil, lid (dark texels across the top of the
#   9-texel eye, more at the front), glow colour, and recess: how far the eye sits back into a
#   dark socket of blocks (behind, below, in front of and behind it) instead of standing out.
# cream: 'brow' (on and above the angled brows), 'streak' (two streaks sweeping back above
#   them) or None. mask: 'socket' (dark up to the brow, so the eye sits in shadow) or
#   'warpaint' (socket plus a jagged streak rising back over the temple).
SCOWL = dict(h0=.9, slope=.3, span=(-8, 9), width=7, thick=4, over=1.2, roll=.25)
HOOD = dict(h0=.6, slope=.32, span=(-9, 10), width=9, thick=5.5, over=1.6, roll=.35)
VARIANTS = {
    'current': dict(label='Installed', note='The installed face, for comparison.'),
    'scowl': dict(label='Scowl', note='Angled brows dip toward the bill and cover the top of each eye; the cream follows them.',
        brow=SCOWL, eye=dict(squash=1, pupil='small', lid=1, recess=1.7), cream='brow', mask='socket'),
    'hooded': dict(label='Hooded', note='A heavier, darker brow shelf over a narrowed eye; cream only sweeps back above it.',
        brow=HOOD, eye=dict(squash=.85, pupil='small', lid=1.5, recess=1.7), cream='streak', mask='socket'),
    'storm': dict(label='Storm eyes', note='The hooded brow with glowing gold eyes and slit pupils.',
        brow=HOOD, eye=dict(squash=.85, pupil='slit', lid=1.5, glow='gold', recess=1.7), cream='streak', mask='socket'),
    'demon': dict(label='Demon', note='Glowing red slit eyes under steep, slim brows that cut across their tops, brow spikes and war paint.',
        brow=dict(h0=1.3, slope=.42, span=(-5, 9), width=6, thick=3.5, over=.8, roll=.3),
        eye=dict(squash=1, pupil='slit', lid=1, glow='red', recess=1.7),
        cream=None, mask='warpaint', horns=True),
}

def mirror(p, s): return np.array([p[0]*s, p[1], p[2]], dtype=float)

def _eye_frame(c):
    """An eye block's centre, outward direction and the horizontal direction back along the head."""
    from fold import rotation
    centre = np.array(c['pivot'], dtype=float); out = rotation(c['rotation'])[:, 2]
    back = np.cross([0, 1, 0], out); back *= np.sign(back[2]) or 1
    return centre, out, back/np.linalg.norm(back)

def shape(m, head):
    """Reshape the head's blocks for a variant; additions are tagged for the painter."""
    v = VARIANTS[head]
    if 'brow' not in v: return
    b = v['brow']
    side = lambda c: 1 if c['origin'][0] + c['size'][0]/2 > 0 else -1
    eyes = {side(p['cube']): p['cube'] for p in m.parts if p['bone'] == 'head' and p['kind'] == 'eye'}
    frames = {s: _eye_frame(c) for s, c in eyes.items()}
    recess = v['eye'].get('recess', 0)
    for s, c in eyes.items():
        # A narrower eye: squash the block about its centre (its pivot), keeping its island.
        h = c['size'][1]*v['eye']['squash']
        c['origin'][1] += (c['size'][1]-h)/2; c['size'][1] = h
        if recess:
            # Back into the head: the eye's face ends up about half a pixel behind the socket's rim.
            centre, out, back = frames[s]
            for key in ('origin', 'pivot'): c[key] = (np.array(c[key]) - out*recess).tolist()
    if recess:
        for p in [p for p in m.parts if p['bone'] == 'head' and p['mat'] == 'mask']:
            # The front mask plate stops at the eye's front edge instead of running over it.
            c = p['cube']; centre, out, back = frames[side(c)]
            from fold import rotation
            d = rotation(c['rotation'])[:, 2]; mid = np.array(c['pivot'], dtype=float)
            if (mid - centre)@back > 0: continue
            if d@back < 0: d = -d
            end = mid + d*c['size'][2]/2
            cut = max(0., ((end - centre)@back + 3.2)/(d@back))
            if cut <= 0: continue
            c['size'][2] -= cut; mid -= d*cut/2
            c['pivot'] = mid.tolist(); c['origin'] = (mid - np.array(c['size'])/2).tolist()
        for s, (centre, out, back) in frames.items():
            # The socket: a floor behind the eye and a rim below, in front and behind, all dark.
            # Each runs out from deep in the head; (back, up) is its middle, then its size.
            for b_mid, u_mid, b_size, u_size, face in ((0, 0, 7.2, 5.6, -.9), (0, -3.55, 8.4, 2.5, .3),
                    (-5.4, .25, 4.2, 6.5, -.4), (4.9, 0, 3.2, 5, 0)):
                at = centre + back*b_mid + np.array([0, u_mid, 0])
                m.slab('head', at - out*6, at + out*face, b_size, u_size, 'mask', up=(0, 1, 0), kind='block', head='socket')
    for p in [p for p in m.parts if p['bone'] == 'head' and p['kind'] == 'brow']:
        c = p['cube']; s = side(c); centre, out, back = frames[s]
        # Laid along the eye: its lower edge crosses h0 above the eye centre and rises toward the
        # back; its outer face stands `over` proud of the eye's face (1.3 out from the centre).
        o = 1.3 + b['over'] - b['width']/2
        def point(t): return centre + back*t + out*o + np.array([0, b['h0'] + b['slope']*t + b['thick']/2, 0])
        uv = c['uv']
        new = m.slab('head', point(b['span'][0]), point(b['span'][1]), b['width'], b['thick'], 'crown',
            up=np.array([0, 1, 0]) + out*b['roll'], kind='brow')
        m.by['head']['cubes'].pop(); m.parts.pop()
        c.clear(); c.update(new); c['uv'] = uv
    if v.get('horns'):
        # A tapering spike sweeps back and up from the end of each brow.
        for s, (centre, out, back) in frames.items():
            root = centre + back*(b['span'][1] - 2) + np.array([0, b['h0'] + b['slope']*b['span'][1] + b['thick']*.6, 0])
            tip = root + back*15 + np.array([0, 8, 0]) + out*2.5
            for k, (f0, f1, w) in enumerate(((0, .55, 4), (.45, .85, 3), (.75, 1, 1.5))):
                m.slab('head', root + (tip-root)*f0, root + (tip-root)*f1, w, w*.8, 'mask', up=out, kind='horn', head='horn')

def _brow_line(v, px, pz, edge):
    """Roughly the y of the brow's lower (edge=-1), middle (0) or upper (1) line at depth pz,
    for the left eye's frame mirrored by |px| (the brow rises toward the back)."""
    b = v['brow']; eye_y, eye_z = 176.6, -37.9    # the installed eye's centre
    t = (pz - eye_z)/.62                          # the brow's back direction is about 52 deg off z
    return eye_y + b['h0'] + b['slope']*t + b['thick']/2 + edge*b['thick']/2

def paint_crown(head, points, old, ramp):
    """Repaint a crown-material head face: rust, with the variant's cream and dark mask.
    ramp(colours) remaps the old tile's shading onto a palette ('rust', 'cream', 'mask')."""
    v = VARIANTS[head]; px, py, pz = np.moveaxis(points, -1, 0); ax = np.abs(px)
    tile = ramp('rust')
    if v.get('cream') == 'band' or 'brow' not in v: return None
    top, mid = _brow_line(v, ax, pz, 1), _brow_line(v, ax, pz, 0)
    if v['cream'] == 'brow':
        cream = (pz < -22) & (py > top - .5)
    elif v['cream'] == 'streak':
        cream = (ax > 6.5) & (pz < -16) & (pz > -42) & (py > top - .5) & (py < top + 4.5)
    else:
        cream = np.zeros_like(py, dtype=bool)
    tile[cream] = ramp('cream')[cream]
    lower = 165.5 + np.maximum(0, pz + 36)*.48
    black = ((ax > 6) | (pz < -38)) & (pz < -17) & (py > lower) & (py < mid + .5)
    if v['mask'] == 'warpaint':
        # A jagged streak of the mask rising back from the eye over the temple.
        streak = 179.5 + (pz + 34)*.42 + np.where((np.floor(pz) % 4) < 2, .8, -.8)
        black |= (ax > 8) & (pz > -34) & (pz < -10) & (np.abs(py - streak) < 1.6)
    tile[black] = ramp('mask')[black]
    return tile

def paint_brow(head, face, ramp):
    """The brow ridge: dark below and on its faces, the forehead colour on top."""
    v = VARIANTS[head]
    if face == 'up' and v.get('cream') in ('brow', 'streak'): return ramp('cream')
    if face == 'up': return ramp('rust')
    return ramp('mask')

EYE = {'gold': [(70, 12, 10), (196, 42, 34), (255, 214, 92), (255, 246, 196)],
       'red': [(40, 6, 8), (120, 10, 12), (255, 70, 38), (255, 196, 120)]}

def paint_eye(head, points, w, h, old):
    """The outward eye face: ring, iris, a small round or slit pupil, and a lid that slants down
    toward the bill. Returns (tile, lit) where lit marks texels for the glow mask."""
    v = VARIANTS[head]; e = v['eye']
    px, py, pz = np.moveaxis(points, -1, 0)
    cy, cz = py.mean(), pz.mean()
    yy, xx = np.mgrid[:h, :w]; cyx, cxx = (h-1)/2, (w-1)/2
    r = np.hypot((xx-cxx)/(w/2), (yy-cyx)/(h/2))
    ring, iris_hi, iris, pupil_c = EYE.get(e.get('glow'), [(18, 16, 16), (196, 42, 34), (232, 200, 96), (255, 248, 216)])
    tile = np.zeros((h, w, 4), np.uint8); tile[...] = (*ring, 255)
    tile[r < 1.05] = (*iris_hi, 255)
    tile[r < .72] = (*iris, 255)
    if e['pupil'] == 'slit':
        pupil = (np.abs(xx-cxx) < .9) & (r < .66)
    else:
        pupil = r < .22
    tile[pupil] = (14, 10, 10, 255)
    lit = (r < .72) & ~pupil if e.get('glow') else None
    if e.get('glow'): tile[(r < .45) & ~pupil] = (*pupil_c, 255)
    # The lid: dark from the top down to a line that drops toward the bill (lower at the front).
    span = max(1e-6, np.ptp(pz))
    front = (pz - pz.min())/span                      # 0 at the front of the eye, 1 at the back
    lid = py.max() - (e['lid'] + 1.5*(1 - front))*(np.ptp(py)/h)
    shut = py > lid
    tile[shut] = (24, 20, 22, 255)
    if lit is not None: lit &= ~shut
    return tile, lit
