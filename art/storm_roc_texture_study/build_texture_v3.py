#!/usr/bin/env python3
"""Refine the approved v2 atlas without moving any of its UV islands.

    python3 art/storm_roc_texture_study/build_texture_v3.py          # atlas and glow mask
    python3 art/storm_roc_texture_study/build_texture_v3.py --check

Pillow/NumPy only. The v2 PNG is the immutable starting image. Geometry and the
new crest islands come from build_mod.refine(); no candidate PNG is read. The crest and
the back are laid out from the study's generated feather art, as build_texture_v2.cjs did. Wing,
tail, bill, breast, neck and beard pixels remain unchanged; the face and feet variants
repaint their own (heads.py, feet.py).
"""
import argparse
import io
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / 'art/storm_roc_mod'))
from build_mod import sculpt, refine, rotation, HEAD, FEET
import heads, feet as talons
from paint import paint


def palette(*colours):
    return np.array([(*bytes.fromhex(c), 255) for c in colours], dtype=np.uint8)


RUST = palette('62291c', '82351f', 'a64b28', 'be6335', 'd57d47', 'e39a60')
CREAM = palette('a18c6d', 'c3b292', 'd9c8a6', 'e8dbbd', 'f1e6cd', 'f6edd9')
MASK = palette('101216', '16191d', '1c2025', '24292e', '30363b', '41474b')
SLATE = palette('161a21', '20262e', '2a313b', '343d47', '424b54', '515a62')
# Lead-grey bare toes; the legs are feathered down to them.
LEAD = palette('2a2c30', '3a3d42', '4b4f55', '5d6268', '70757b', '858a8f')


def remap(tile, colours):
    """Keep v2's deliberate pixel clusters, changing only their colour/value ramp."""
    lum = tile[..., :3] @ np.array([.2126, .7152, .0722])
    lo, hi = np.percentile(lum, [4, 96])
    levels = np.clip((lum-lo) / max(1, hi-lo) * 5, 0, 5).astype(int)
    return colours[levels]


def face_points(cube, face, w, h):
    """Texel centres in model space, in the viewer's top-left PNG convention."""
    x,y,z = cube['origin']; dx,dy,dz = cube['size']; X,Y,Z = x+dx,y+dy,z+dz
    origin,U,V,du,dv = {
        'north': ([x,Y,z],[1,0,0],[0,-1,0],dx,dy),
        'south': ([X,Y,Z],[-1,0,0],[0,-1,0],dx,dy),
        'east': ([X,Y,z],[0,0,1],[0,-1,0],dz,dy),
        'west': ([x,Y,Z],[0,0,-1],[0,-1,0],dz,dy),
        'up': ([x,Y,Z],[1,0,0],[0,0,-1],dx,dz),
        'down': ([x,y,z],[1,0,0],[0,0,1],dx,dz),
    }[face]
    v,u = np.mgrid[:h,:w]
    points = np.array(origin) + (u[...,None]+.5)/w*du*np.array(U) + (v[...,None]+.5)/h*dv*np.array(V)
    pivot = np.array(cube.get('pivot',[0,0,0]))
    return (points-pivot) @ rotation(cube.get('rotation',[0,0,0])).T + pivot


def face_normal(cube, face):
    """A face's outward normal in model space."""
    n = {'north': [0,0,-1], 'south': [0,0,1], 'east': [1,0,0], 'west': [-1,0,0], 'up': [0,1,0], 'down': [0,-1,0]}[face]
    return rotation(cube.get('rotation',[0,0,0])) @ np.array(n, dtype=float)


def project(source, cube, face, w, h, flow, density=1.):
    """The study's packing (build_texture_v2.cjs): lay a 32 x 64 feather tile across the face in
    model space, its feathers pointing along `flow` as seen on that face, so neighbouring blocks
    continue one another's feathering."""
    points = face_points(cube, face, w, h); n = face_normal(cube, face)
    flow = np.array(flow, dtype=float); D = flow - n*(flow@n)
    if np.linalg.norm(D) < .05:
        D = face_points(cube, face, 1, 2)[1, 0] - face_points(cube, face, 1, 2)[0, 0]
    D /= np.linalg.norm(D); S = np.cross(D, n)
    sx = np.floor(points@S*density).astype(int) % source.shape[1]
    sy = np.floor(points@D*density).astype(int) % source.shape[0]
    return source[sy, sx].copy()


def materials():
    """The study's generated feather art: the eight v2 regions, and the v1 sheet's panels (the
    wings' covert scallops among them), each as a 32 x 64 tile."""
    regions = {n: np.array(Image.open(HERE/'regions-v2'/f'{n}.png').convert('RGBA'))
        for n in ('crown','cheek-nape','neck-hackles','breast','back-mantle','belly','leg-plumes','beak-horn')}
    sheet = Image.open(HERE/'material-sheet-pixel-v2.png').convert('RGBA'); sw, sh = sheet.width//4, sheet.height//2
    panels = [np.array(sheet.crop((i%4*sw, i//4*sh, i%4*sw+sw, i//4*sh+sh)).resize((32, 64), Image.NEAREST)) for i in range(8)]
    return regions, panels


def build(head=None, glow=False, feet=None):
    """The atlas PNG for a head and feet variant (default: build_mod.HEAD and FEET); with glow,
    also its glow mask."""
    head = head or HEAD; feet = feet or FEET
    original = np.array(Image.open(HERE/'storm-roc-vulture-v2.png').convert('RGBA'))
    atlas = original.copy()
    layout = json.loads((HERE/'layout.json').read_text())['parts']
    regions, panels = materials()
    study = json.loads((ROOT/'art/storm_roc/candidate/storm_roc.geo.json').read_text())['minecraft:geometry'][0]['bones']
    model = sculpt(study)
    paint(model.parts)
    assert len(model.parts)==len(layout)
    for actual,expected in zip(model.parts,layout,strict=True):
        assert actual['bone']==expected['bone'] and actual['cube']==expected['cube'], 'v2 layout drift'
    refine(model, head, feet)
    head_name = head
    lit_mask = np.zeros(original.shape[:2], bool)
    # New face blocks have no old pixels; they borrow the mask's feathering.
    src = layout[next(i for i,q in enumerate(layout) if q['bone']=='head' and q['mat']=='mask')]['cube']['uv']['up']
    (sx,sy),(sw,sh) = src['uv'],src['uv_size']; sample = original[sy:sy+sh,sx:sx+sw]
    horn_sample = lambda w,h: np.tile(sample,(h//sh+1,w//sw+1,1))[:h,:w]
    # Rebuilt toes borrow the toe's own scales.
    src = next(q for q in layout if q['bone']=='left_toe_1_base')['cube']['uv']['up']
    (tx,ty),(tw,th) = src['uv'],src['uv_size']; toe = original[ty:ty+th,tx:tx+tw]
    toe_sample = lambda w,h: np.tile(toe,(h//th+1,w//tw+1,1))[:h,:w]
    for i,p in enumerate(model.parts):
        bone = p['bone']
        crest = p.get('v3')=='crest'
        face_part = bone=='head' and head_name!='current' and (p['kind'] in ('brow','eye') or p.get('head'))
        foot_part = feet!='current' and p.get('feet')
        head = bone=='head' and p['mat']=='crown'
        mask = bone=='head' and p['mat']=='mask'
        legs = bone.endswith(('_shin','_thigh','_tarsus'))
        toes = p['mat']=='skin' and (bone.endswith('_foot') or '_toe_' in bone)
        mantle = bone=='body' and i in (0,4,5,6,7,8,9)
        if not any((crest,head,mask,legs,toes,mantle,face_part,foot_part)):
            continue
        for face,uv in p['cube']['uv'].items():
            x,y = uv['uv']; w,h = uv['uv_size']
            old = original[y:y+h,x:x+w]
            ramp = lambda name: remap(old if old[...,3].any() else horn_sample(w,h), {'rust':RUST,'cream':CREAM,'mask':MASK}[name])
            if foot_part=='digit':
                tile = remap(toe_sample(w,h),LEAD)
            elif foot_part=='talon':
                tile = talons.paint_talon(face_points(p['cube'],face,w,h), face, p['tip_at'], p['talon_len'])
            elif face_part and p['kind']=='eye':
                if face!='south': continue
                tile,lit = heads.paint_eye(head_name, face_points(p['cube'],face,w,h), w, h, old)
                if lit is not None: lit_mask[y:y+h,x:x+w] |= lit
            elif face_part and p['kind']=='brow':
                tile = heads.paint_brow(head_name, face, ramp)
            elif face_part:
                tile = ramp('mask')
            elif crest:
                # The crest grows out of the neck's hackles: the same art, along each feather.
                tile = project(regions['neck-hackles'], p['cube'], face, w, h, rotation(p['cube']['rotation'])[:,2], 1.3)
            elif legs:
                tile = remap(old,RUST)
            elif toes:
                tile = remap(old,LEAD)
            elif mask:
                tile = remap(old,MASK)
            else:
                points = face_points(p['cube'],face,w,h)
                if mantle:
                    # The back wears the wings' covert scallops, running toward the tail, so it
                    # continues into the wings.
                    tile = project(panels[2], p['cube'], face, w, h, [0,-.15,1])
                    # The body's sides keep the rust breast below the wing line; its rear face,
                    # over the rump, stays dark like the back.
                    if i in (0,6,8) and face!='south':tile[points[...,1]<74]=old[points[...,1]<74]
                else:
                    varied = heads.paint_crown(head_name, points, old, ramp)
                    if varied is not None:
                        atlas[y-1:y+h+1,x-1:x+w+1] = np.pad(varied,((1,1),(1,1),(0,0)),mode='edge')
                        continue
                    px,py,pz = np.moveaxis(points,-1,0)
                    tile = remap(old,RUST)
                    forehead = (pz < -25) & (py > 177.5)
                    tile[forehead] = remap(old,CREAM)[forehead]
                    # A broad eye-to-bill mask with a descending front lobe;
                    # taper it over the rear cheek instead of a narrow strap.
                    lower = 165.5 + np.maximum(0,pz+36)*.48
                    black = ((np.abs(px)>6) | (pz < -38)) & (pz < -17) & (py>lower) & (py<179)
                    tile[black] = remap(old,MASK)[black]
            atlas[y-1:y+h+1,x-1:x+w+1] = np.pad(tile,((1,1),(1,1),(0,0)),mode='edge')

    # Existing islands retain exact positions, even on resized/replaced blocks.
    for p,old in zip(model.parts,layout):
        assert p['cube']['uv']==old['cube']['uv'], ('UV drift',p['bone'])
        if any(s in p['bone'] for s in ('wing_','primary_','secondary_','root_tertial','tail_')):
            for uv in old['cube']['uv'].values():
                x,y=uv['uv'];w,h=uv['uv_size']
                assert np.array_equal(atlas[y-1:y+h+1,x-1:x+w+1], original[y-1:y+h+1,x-1:x+w+1]),p['bone']
    assert not np.array(Image.open(HERE/'storm-roc-glow-v2.png').convert('RGBA'))[...,3].any(), 'glow must remain empty'
    out=io.BytesIO();Image.fromarray(atlas).save(out,format='PNG')
    if not glow: return out.getvalue()
    mask=np.zeros_like(atlas);mask[lit_mask]=atlas[lit_mask]
    buf=io.BytesIO();Image.fromarray(mask).save(buf,format='PNG')
    return out.getvalue(),buf.getvalue()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args();atlas,glow=build(glow=True)
    # The glow mask lights what the face's variant makes glow (the Demon's eyes).
    for name,data in (('storm-roc-vulture-v3.png',atlas),('storm-roc-glow-v3.png',glow)):
        path=HERE/name
        if args.check:assert path.read_bytes()==data,f'stale {name}'
        else:path.write_bytes(data)
        print(('Checked' if args.check else 'Wrote'),path.relative_to(ROOT))


if __name__=='__main__':
    main()
