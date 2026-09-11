#!/usr/bin/env python3
"""Compile the approved Guardian art into runtime assets, or verify with --check.

This is a deterministic game-asset export: normalize PNG format/dimensions,
scale UV coordinates, and extract the already-approved magic pixels into the
GeckoLib glowmask. Original artwork, geometry, and editor projects are preserved.
"""
import argparse
import copy
import io
import json
from pathlib import Path

from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'art/fractured_guardian/v2-textured'
ASSETS = ROOT/'src/main/resources/assets/elementalwands'
SIZE = 1024


def cracked_texture(texture, glow, stage):
    # Material wear follows the atlas's existing dark stone joints. Work on its
    # native coarse pixel grid, rather than painting thin cyan lines across it.
    base = texture.copy()
    for tile in (0, 1, 5, 7, 8, 9, 10, 12):
        at=((tile%4)*256,(tile//4)*256)
        patch=texture.crop((*at,at[0]+256,at[1]+256))
        coarse=np.asarray(patch.resize((24,24),Image.Resampling.BOX)).astype(float)
        lum=coarse[:,:,:3].mean(axis=2)
        mask=np.zeros((24,24),dtype=bool)
        if tile in (8,9,10):
            # Recess the already-carved spiral/knot grooves, keeping their motifs intact.
            mask=lum < np.quantile(lum,.12+stage*.035)
        else:
            horizontal=tile in (1,5)
            cost=lum.T.copy() if horizontal else lum.copy()
            path_cost=np.full((24,24),1e9);prev=np.zeros((24,24),dtype=int)
            start=5+(tile*7)%14
            path_cost[0]=cost[0]+np.abs(np.arange(24)-start)*13
            for y in range(1,24):
                for x in range(2,22):
                    candidates=range(max(2,x-1),min(22,x+2))
                    last=min(candidates,key=lambda k:path_cost[y-1,k]+abs(k-x)*6)
                    prev[y,x]=last
                    path_cost[y,x]=cost[y,x]+path_cost[y-1,last]+abs(last-x)*6
            x=int(np.argmin(path_cost[-1]));path=[]
            for y in range(23,-1,-1):path.append((y,x));x=prev[y,x]
            # Cracks extend along one existing joint as the shell wears down.
            for y,x in path:
                if abs(y-12)<=3+stage*3:mask[y,x]=True
            if horizontal:mask=mask.T
        # Dark recessed stone with a restrained chipped edge, no new emissive veins.
        cut=Image.fromarray((mask*255).astype('uint8')).resize((256,256),Image.Resampling.NEAREST)
        pixels=np.asarray(patch).copy();selected=np.asarray(cut)>0
        pixels[selected,:3]=(pixels[selected,:3]*(.78-stage*.13)).astype('uint8')
        base.paste(Image.fromarray(pixels),at)
    return base, glow.copy()

def compile_assets():
    original = Image.open(SOURCE/'fractured_guardian.png').convert('RGBA')
    texture = original.resize((SIZE, SIZE), Image.Resampling.NEAREST)
    model = json.loads((SOURCE/'fractured_guardian.geo.json').read_text())
    source_model = copy.deepcopy(model)
    geometry = model['minecraft:geometry'][0]
    description = geometry['description']
    sx, sy = SIZE/description['texture_width'], SIZE/description['texture_height']
    assert original.size == (description['texture_width'], description['texture_height'])
    description['texture_width'] = description['texture_height'] = SIZE
    # Visibility bounds include the raised arms; physical collision dimensions stay separate.
    description.update(visible_bounds_width=12, visible_bounds_height=10, visible_bounds_offset=[0,5,0])
    names = {b['name'] for b in geometry['bones']}
    assert len(names) == len(geometry['bones']) == 38
    cube_count = 0
    for bone in geometry['bones']:
        assert 'parent' not in bone or bone['parent'] in names
        for cube in bone.get('cubes', []):
            cube_count += 1
            for face in cube['uv'].values():
                for key in ['uv', 'uv_size']:
                    face[key] = [face[key][0]*sx, face[key][1]*sy]
                u,v = face['uv']; w,h = face['uv_size']
                assert 0 <= min(u,u+w) < max(u,u+w) <= SIZE
                assert 0 <= min(v,v+h) < max(v,v+h) <= SIZE
    assert cube_count == 132
    for old,new in zip(source_model['minecraft:geometry'][0]['bones'], geometry['bones']):
        assert {k:v for k,v in old.items() if k!='cubes'} == {k:v for k,v in new.items() if k!='cubes'}
        for a,b in zip(old['cubes'],new['cubes']):
            assert {k:v for k,v in a.items() if k!='uv'} == {k:v for k,v in b.items() if k!='uv'}

    # Match the approved preview's cyan/ivory classifier and restrict it to the
    # three explicitly authored magic tiles. Moss and stone never emit light.
    glow = Image.new('RGBA', texture.size, (0,0,0,0))
    tile_size = SIZE//4
    for tile in [11,13,14]:
        tx,ty = (tile%4)*tile_size,(tile//4)*tile_size
        for y in range(ty,ty+tile_size):
            for x in range(tx,tx+tile_size):
                r,g,b,a = texture.getpixel((x,y))
                if g > r+8 and b > r-5 and g > 155:
                    glow.putpixel((x,y),(r,g,b,a))
    assert glow.getbbox() is not None
    assert 0 < SIZE*SIZE-glow.getchannel('A').histogram()[0] < SIZE*SIZE*.03

    outputs = {}
    for name,img in [('fractured_guardian.png',texture),('fractured_guardian_glowmask.png',glow)]:
        buf = io.BytesIO(); img.save(buf,format='PNG')
        outputs[ASSETS/'textures/entity'/name] = buf.getvalue()
    for stage in range(1,4):
        cracked, light = cracked_texture(texture, glow, stage)
        for suffix,img in [('',cracked),('_glowmask',light)]:
            buf=io.BytesIO(); img.save(buf,format='PNG')
            outputs[ASSETS/'textures/entity'/f'fractured_guardian_cracks_{stage}{suffix}.png']=buf.getvalue()
    outputs[ASSETS/'geckolib/models/fractured_guardian.geo.json'] = (json.dumps(model,indent=2)+'\n').encode()
    animations = json.loads((SOURCE.parent/'v5-leap/fractured_guardian.animation.json').read_text())
    assert set(animations['animations']) == {f'animation.fractured_guardian.{n}' for n in ['idle','walk','awaken','slam','throw','beam','leap_launch','leap_air','leap_land']}
    animations['animations'].update(json.loads((SOURCE.parent/'arrival/arrival.animation.json').read_text())['animations'])
    animations['animations'].update(json.loads((SOURCE.parent/'guard/guard.animation.json').read_text())['animations'])
    animations['animations'].update(json.loads((SOURCE.parent/'phase/phase.animation.json').read_text())['animations'])
    for clip in animations['animations'].values():
        assert set(clip['bones']) <= names
        assert clip['animation_length'] > 0
        for channels in clip['bones'].values():
            for keys in channels.values():
                assert all(0 <= float(t) <= clip['animation_length'] for t in keys)
                assert all(len(v) == 3 and all(isinstance(n, (int,float)) for n in v) for v in keys.values())
                if clip['loop']:
                    assert next(iter(keys.values())) == list(keys.values())[-1]
    outputs[ASSETS/'geckolib/animations/fractured_guardian.animation.json'] = (json.dumps(animations,indent=2)+'\n').encode()
    return outputs


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='Verify packaged files without writing anything')
    args=parser.parse_args()
    outputs=compile_assets()
    for path,data in outputs.items():
        if args.check:
            if not path.exists() or path.read_bytes()!=data:
                raise SystemExit(f'Guardian asset missing or stale: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(data)
    print(f'Guardian assets {"validated" if args.check else "compiled"}: 132 cubes, 38 bones, 1024x1024 RGBA, isolated glowmask; approved geometry unchanged.')


if __name__=='__main__':
    main()
