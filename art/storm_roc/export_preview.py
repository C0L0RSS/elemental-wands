#!/usr/bin/env python3
"""Bake the installed runtime Roc into one JSON for the browser preview page.

The page draws each bone's blocks with that bone's world matrix, baked here at 20 Hz
with the same pose math the validators use, so the page cannot drift from the game's clips.
Writes .local-previews/storm-roc/preview/roc.json beside copies of the atlas and glow mask.
"""
import argparse,base64,json,math,shutil
from pathlib import Path
import numpy as np
from validate_review import matrix
from fold import rotation
from validate_runtime import read,unreflect
from build_runtime import SCALE,DEFINITIONS,STAMPEDE_RUN
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'
OUT=ROOT/'.local-previews/storm-roc/preview'
FPS=20
# Reworked clips have snaps quicker than a 20 Hz bake can show; the page reads each clip's own rate.
CLIP_FPS={'hunched':80,'bite':80,'bite_leap':80,'swing_left':80,'swing_right':80,'scream':80,'wing':80,'pounce':80,'stampede':80}
# The Thunder Charge's run lasts as long as the server's charge does, so the page shows one whole
# charge: its windup, as many strides of the run loop as it needs, then its skid. Its path follows
# StormRocRules' charge (ticks and blocks): its prey stands 30 blocks ahead and sprints off to the
# side as it launches; it curves after them, locks its line 15 blocks short (about a second
# out, so a sprint sideways still clears its trample), runs on past and skids.
CHARGE=dict(impact=44,run_at=53,stride=9,speed=.77,turn=2,commit=15,past=8,max_run=90,skid=5,skid_ticks=16,after=38)
PREY_START,PREY_SPRINT=(0.,-30.),.28

def charge():
    """The charge's ticks: the Roc's [x, z, heading] (heading 0 faces -z, positive turns left), its
    prey's [x, z], and the tick its skid starts."""
    c=CHARGE;x=z=yaw=0.;px,pz=PREY_START;locked=ending=False;left=0.;stop=None;roc,prey=[],[]
    for t in range(c['impact']+c['max_run']+c['stride']+c['after']+1):
        if t>=c['impact'] and stop is None:
            px-=PREY_SPRINT
            if not locked:
                close=math.hypot(px-x,pz-z)
                if close<=c['commit']:locked,left=True,close+c['past']
                else:
                    want=math.degrees(math.atan2(-(px-x),-(pz-z)))
                    yaw+=max(-c['turn'],min(c['turn'],(want-yaw+180)%360-180))
            speed=c['speed']*min(1,(t-c['impact']+1)/5);h=math.radians(yaw)
            x,z=x-math.sin(h)*speed,z-math.cos(h)*speed
            if locked:left-=speed
            ending=ending or locked and left<=0 or t-c['impact']>=c['max_run']
            if ending and t>=c['run_at'] and (t-c['run_at'])%c['stride']==0:stop,skid=t,(x,z,yaw)
        elif stop is not None and t>stop:
            f=min(1,(t-stop)/c['skid_ticks']);slide=c['skid']*(1-(1-f)**2);h=math.radians(skid[2])
            x,z=skid[0]-math.sin(h)*slide,skid[1]-math.cos(h)*slide
        roc.append([round(x,4),round(z,4),round(yaw,4)]);prey.append([round(px,4),round(pz,4)])
        if stop is not None and t>=stop+c['after']:break
    return roc,prey,stop

def chained(T,stop):
    """Which clip, and when in it, the game is playing T seconds into the charge."""
    tick=T*20;run_at=CHARGE['run_at']
    if tick<run_at:return 'stampede',T
    if tick<stop:return 'stampede_run',((tick-run_at)/20)%DEFINITIONS['stampede_run'][0]
    return 'stampede_skid',min((tick-stop)/20,DEFINITIONS['stampede_skid'][0])

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
    parser=argparse.ArgumentParser()
    parser.add_argument('--only',nargs='+',choices=list(DEFINITIONS),metavar='CLIP',help='re-bake just these clips')
    args=parser.parse_args()
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
    previous=json.loads((OUT/'roc.json').read_text())['clips'] if args.only else {}
    for name,(duration,loop) in DEFINITIONS.items():
        if args.only and name not in args.only:
            baked[name]=previous[name];continue
        first=sockets[name][0];fps=CLIP_FPS.get(name,FPS);extra={}
        if name=='stampede':
            path,prey,stop=charge();duration=(stop+CHARGE['after'])/20
            extra={'path':path,'prey':prey,'launch':STAMPEDE_RUN,'stop':stop/20}
        frames=[]
        for i in range(round(duration*fps)+1):
            t=i/fps;world={};clip,at=chained(t,stop) if name=='stampede' else (name,t)
            for b in bones:world[b['name']]=world.get(b.get('parent'),np.eye(4))@matrix(b,clips[clip]['bones'].get(b['name'],{}),at)
            for b in meshes:
                M=world[b['name']].copy();M[:3,3]*=SCALE
                frames.append(M[:3,:4].T.ravel())
        data=np.array(frames,dtype='<f4')
        # Where it stands at the start (a spire top for the perched clips), and what to frame.
        baked[name]={'duration':duration,'loop':loop,'fps':fps,'frames':round(duration*fps)+1,
            'feet':min(first['left_foot'][1],first['right_foot'][1]),'body':first['body'],'head':first['head'],
            'matrices':base64.b64encode(data.tobytes()).decode(),**extra}
        print('baked',name,flush=True)
    (OUT/'roc.json').write_text(json.dumps({'fps':FPS,'scale':SCALE,'parts':parts,'clips':baked},separators=(',',':')))
    # The viewer page itself is tracked as a template; the clip data and textures beside it are not.
    shutil.copy(HERE/'animation_viewer/viewer.template.html',OUT/'storm-roc-redesign.html')
    shutil.copy(ASSETS/'textures/entity/storm_roc.png',OUT/'storm_roc.png')
    shutil.copy(ASSETS/'textures/entity/storm_roc_glowmask.png',OUT/'storm_roc_glowmask.png')
    print(OUT/'roc.json',(OUT/'roc.json').stat().st_size//1024,'KB')
if __name__=='__main__':main()
