#!/usr/bin/env python3
"""Bake the installed runtime Roc into one JSON for the browser preview page.

The page draws each bone's blocks with that bone's world matrix, baked here at 20 Hz
with the same pose math the validators use, so the page cannot drift from the game's clips.
Writes .local-previews/storm-roc/preview/roc.json beside copies of the atlas and glow mask.
"""
import base64,json,shutil
from pathlib import Path
import numpy as np
from validate_review import matrix
from fold import rotation
from validate_runtime import read,unreflect
from build_runtime import SCALE,DEFINITIONS
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'
OUT=ROOT/'.local-previews/storm-roc/preview'
FPS=20

def bake_parts(bones,W,H):
    """Each meshed bone's triangles in the study basis, in bone order, as the page draws them."""
    parts=[]
    for b in [b for b in bones if b.get('cubes')]:
        P,N,U=[],[],[]
        for c in b['cubes']:
            # The study viewer's own faces, corner order and per-face UVs (PNG top-left).
            (x,y,z),(w,h,d)=c['origin'],c['size'];X,Y,Z=x+w,y+h,z+d
            R=rotation(c.get('rotation',[0,0,0]));pv=np.array(c.get('pivot',[0,0,0]),dtype=float)
            faces={'north':([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z]],[0,0,-1]),'south':([[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z]],[0,0,1]),
                'east':([[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z]],[1,0,0]),'west':([[x,y,Z],[x,y,z],[x,Y,z],[x,Y,Z]],[-1,0,0]),
                'up':([[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],[0,1,0]),'down':([[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],[0,-1,0])}
            for name,(corners,n) in faces.items():
                (u,v),(fw,fh)=c['uv'][name]['uv'],c['uv'][name]['uv_size']
                uv=[[u/W,(v+fh)/H],[(u+fw)/W,(v+fh)/H],[(u+fw)/W,v/H],[u/W,v/H]]
                for k in (0,1,2,0,2,3):P.append(R@(np.array(corners[k])-pv)+pv);N.append(R@np.array(n,dtype=float));U.append(uv[k])
        parts.append({'bone':b['name'],'positions':np.round(np.array(P)*SCALE,4).ravel().tolist(),
            'normals':np.round(np.array(N),4).ravel().tolist(),'uvs':np.round(np.array(U),5).ravel().tolist()})
    return parts

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    geo=read(ASSETS/'geckolib/models/storm_roc.geo.json')['minecraft:geometry'][0]
    bones=geo['bones']
    for b in bones:
        for item in [b]+b.get('cubes',[]):unreflect(item)
    clips={k.removeprefix('animation.storm_roc.'):v for k,v in read(ASSETS/'geckolib/animations/storm_roc.animation.json')['animations'].items()}
    for clip in clips.values():
        for c in clip['bones'].values():unreflect(c)
    meshes=[b for b in bones if b.get('cubes')]
    parts=bake_parts(bones,geo['description']['texture_width'],geo['description']['texture_height'])
    baked={};sockets=read(ASSETS/'storm_roc_sockets.json')
    for name,(duration,loop) in DEFINITIONS.items():
        first=sockets[name][0]
        frames=[]
        for i in range(round(duration*FPS)+1):
            t=i/FPS;world={}
            for b in bones:world[b['name']]=world.get(b.get('parent'),np.eye(4))@matrix(b,clips[name]['bones'].get(b['name'],{}),t)
            for b in meshes:
                M=world[b['name']].copy();M[:3,3]*=SCALE
                frames.append(M[:3,:4].T.ravel())
        data=np.array(frames,dtype='<f4')
        # Where it stands at the start (a spire top for the perched clips), and what to frame.
        baked[name]={'duration':duration,'loop':loop,'frames':round(duration*FPS)+1,
            'feet':min(first['left_foot'][1],first['right_foot'][1]),'body':first['body'],'head':first['head'],
            'matrices':base64.b64encode(data.tobytes()).decode()}
        print('baked',name,flush=True)
    (OUT/'roc.json').write_text(json.dumps({'fps':FPS,'scale':SCALE,'parts':parts,'clips':baked},separators=(',',':')))
    shutil.copy(ASSETS/'textures/entity/storm_roc.png',OUT/'storm_roc.png')
    shutil.copy(ASSETS/'textures/entity/storm_roc_glowmask.png',OUT/'storm_roc_glowmask.png')
    print(OUT/'roc.json',(OUT/'roc.json').stat().st_size//1024,'KB')
if __name__=='__main__':main()
