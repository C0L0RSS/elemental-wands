"""Reserve the approved face atlas and verify its rigid head-local geometry."""
import json
from pathlib import Path
import numpy as np
from PIL import Image
HERE=Path(__file__).resolve().parent
SOURCE=HERE/'approved_head'

def approved():return json.loads((SOURCE/'bones.json').read_text())

def reserve(r,size):
    old={b['name']:b for b in approved()};current={b['name']:b for b in r.bones}
    occupied=np.zeros((size,size),dtype=bool)
    texture=Image.new('RGBA',(size,size));glow=Image.new('RGBA',(size,size))
    source=[Image.open(SOURCE/name).convert('RGBA') for name in ('storm_roc.png','storm_roc_glowmask.png')]
    for name,b in old.items():
        assert len(b['cubes'])==len(current[name]['cubes']),name
        for c,ref in zip(current[name]['cubes'],b['cubes']):
            c['uv']=ref['uv']
            for f in ref['uv'].values():
                x,y=f['uv'];w,h=f['uv_size'];rect=(x-1,y-1,x+w+1,y+h+1)
                occupied[y-1:y+h+1,x-1:x+w+1]=True
                for dst,src in zip((texture,glow),source):dst.paste(src.crop(rect),rect)
    locked={id(c) for name in old for c in current[name]['cubes']}
    return texture,glow,occupied,locked

def verify(r,texture,glow):
    ref=approved();current={b['name']:b for b in r.bones};origin=np.array(current['head']['pivot']);old_origin=np.array(ref[0]['pivot'])
    source=[Image.open(SOURCE/name).convert('RGBA') for name in ('storm_roc.png','storm_roc_glowmask.png')]
    for b in ref:
        now=current[b['name']]
        assert np.allclose(np.array(now['pivot'])-origin,np.array(b['pivot'])-old_origin,atol=1e-7),b['name']
        if b['name']!='head':assert now.get('parent')==b.get('parent')
        assert len(now['cubes'])==len(b['cubes'])
        for c,old in zip(now['cubes'],b['cubes']):
            for key in old:
                if key in ('origin','pivot'):assert np.allclose(np.array(c[key])-origin,np.array(old[key])-old_origin,atol=1e-7),(b['name'],key)
                else:assert c[key]==old[key],(b['name'],key)
            for f in old['uv'].values():
                x,y=f['uv'];w,h=f['uv_size'];rect=(x-1,y-1,x+w+1,y+h+1)
                for dst,src in zip((texture,glow),source):assert dst.crop(rect).tobytes()==src.crop(rect).tobytes(),(b['name'],'pixels')
    return {'bones':len(ref),'cubes':sum(len(b['cubes']) for b in ref),'geometry_uv_pixels':'unchanged relative to head'}


def verify_sparks(r,metadata):
    old=json.loads((SOURCE/'storm_roc_sparks.json').read_text())
    delta=np.array(next(b for b in r.bones if b['name']=='head')['pivot'])-np.array(approved()[0]['pivot'])
    assert len(metadata['anchors'])==len(old['anchors'])
    for a,b in zip(metadata['anchors'],old['anchors']):
        assert np.allclose(np.array(a['pivot_model_pixels'])-delta,b['pivot_model_pixels'],atol=1e-7)
        assert {k:v for k,v in a.items() if k!='pivot_model_pixels'}=={k:v for k,v in b.items() if k!='pivot_model_pixels'}
    assert {k:v for k,v in metadata.items() if k!='anchors'}=={k:v for k,v in old.items() if k!='anchors'}
