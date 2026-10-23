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
from build_runtime import SCALE,DEFINITIONS,STAMPEDE_RUN,SPIRE_FLIGHT,LAND
from build_runtime import TAKEOFF
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'
OUT=ROOT/'.local-previews/storm-roc/preview'
FPS=20
# Reworked clips have snaps quicker than a 20 Hz bake can show; the page reads each clip's own rate.
CLIP_FPS={'flight':80,'spire_flight':80,'land':80,'hunched':80,'bite':80,'bite_leap':80,'swing_left':80,'swing_right':80,'scream':80,'wing':80,'pounce':80,'stampede':80}
CLIP_FPS['takeoff']=80
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

# Its way onto a spire is StormRocCombat's RocApproach: out to one side of the spire, round in one
# banked turn of APPROACH_RADIUS, and in along a straight run-in of RUN_IN, so it comes down facing
# the summit; the shorter way round, the turn rolled in and out over ROLL blocks. Centre-relative.
APPROACH_RADIUS,RUN_IN,ROUTE_STEP,ROLL=20,20,.5,6
# The client banks it into its turns by BANK_RATE degrees per degree of turn a tick, up to BANK_MAX.
BANK_RATE,BANK_MAX=4.6,45

def approach(start,spire):
    """RocApproach.onto, from start onto spire with the centre at the origin: its points [x, z] every
    step blocks along the way and the heading (radians, from +x toward +z) at each."""
    a,s=np.array([start[0],start[2]],float),np.array([spire[0],spire[2]],float)
    n=-s/np.linalg.norm(s);e=s-n*RUN_IN;r=APPROACH_RADIUS;best=None
    for side in (1,-1):
        c=np.array([e[0]-side*n[1]*r,e[1]+side*n[0]*r]);d=a-c;D=np.linalg.norm(d)
        if D<=r*1.05:continue
        half,base=math.acos(r/D),math.atan2(d[1],d[0])
        for k in (1,-1):
            t=c+r*np.array([math.cos(base+k*half),math.sin(base+k*half)]);h=t-a;straight=np.linalg.norm(h)
            m=np.array([-side*(t[1]-c[1]),side*(t[0]-c[0])])/r
            if np.dot(m,h)/straight<.999:continue
            frm,to=math.atan2(t[1]-c[1],t[0]-c[0]),math.atan2(e[1]-c[1],e[0]-c[0])
            arc=r*(((to-frm)*side)%(2*math.pi))
            if best is None or straight+arc<best[0]:best=(straight+arc,straight,arc,math.atan2(h[1],h[0]),side)
            break
    _,straight,arc,start_heading,side=best;total=straight+arc+RUN_IN
    count=math.ceil(total/ROUTE_STEP);step=total/count;s=np.arange(count+1)*step
    heading=start_heading+side*np.clip(s-straight,0,arc)/r;half=round(ROLL/step)
    for _ in range(3):heading=np.array([heading[np.clip(np.arange(i-half,i+half+1),0,count)].mean() for i in range(count+1)])
    pts=np.zeros((count+1,2));pts[0]=a
    for i in range(1,count+1):pts[i]=pts[i-1]+np.array([math.cos(heading[i-1]),math.sin(heading[i-1])])*step
    # Rounding the turn moved its end a little: spread the difference along the way.
    pts+=np.outer(np.arange(count+1)/count,np.array([spire[0],spire[2]])-pts[-1])
    return pts,heading,step

def along(route,distance):
    """Where it is, and its yaw (degrees, as the game's), this far along its route."""
    pts,heading,step=route;k=min(max(distance/step,0),len(pts)-1);i=min(int(k),len(pts)-2);f=k-i
    h=heading[i]+(heading[i+1]-heading[i])*f
    return pts[i]+(pts[i+1]-pts[i])*f,math.degrees(math.atan2(-math.cos(h),math.sin(h)))

def flown(body,h,height_ease):
    """A flight onto its spire tick by tick, as StormRocCombat flies it: held for the push while it
    turns evenly onto its way out, then along its route over a rising and settling arc. With the
    client's bank, climb and look into the turn (StormRocMotion), aloft from the push until the
    landing's flare. body is the body socket height each tick of the chained clips."""
    start,end=np.array(h['start'],float),np.array(h['end'],float);route=approach(start,end)
    length=(len(route[0])-1)*route[2];travel=h['travel'];push=h['push'];arrive=push+travel+1
    def wanted(pos,at):return math.degrees(math.atan2(-(at[0]-pos[0]),at[2]-pos[2]))
    smooth=lambda f:f*f*(3-2*f);wrap=lambda a:(a+180)%360-180
    yaw=h['facing'];bank=climb=look=0.;last_yaw,last=yaw,start.copy();ticks=[];pos=start.copy()
    goal=along(route,0)[1]
    for tick in range(len(body)):
        if tick<push:yaw+=wrap(goal-yaw)/(push-tick)
        if tick<arrive:
            f=min(1,max(0,(tick-push)/travel));g=min(1,f/(1-h['lead']/2/travel))
            arc=math.sin(g*math.pi)**(2 if g>.5 else 1)*height_ease(g)
            across,heading=along(route,smooth(f)*length)
            pos=np.array([across[0],start[1]+(end[1]-start[1])*smooth(g)+arc*h['arc'],across[1]])
            if f>0:yaw=last_yaw+wrap(heading-last_yaw)
        else:pos=end.copy();yaw=last_yaw+wrap(wanted(end,(0,0,0))-last_yaw)
        turn,rise=yaw-last_yaw,pos[1]-last[1];last_yaw,last=yaw,pos.copy()
        aloft=push<=tick<arrive-h['lead']
        bank+=((max(-BANK_MAX,min(BANK_MAX,turn*BANK_RATE)) if aloft else 0)-bank)*.2
        climb+=((max(-25,min(25,rise*25)) if aloft else 0)-climb)*.2
        look+=((max(-35,min(35,turn*3)) if aloft else 0)-look)*.15
        ticks.append((pos.copy(),yaw,bank,climb,body[tick],look))
    return ticks

# The hop between spires follows StormRocCombat's tickAir: held on its spire for the push (SPIRE_PUSH)
# while it turns onto its way out, then flown along its route over a 12-block arc. It flares into its
# landing LAND_LEAD before it arrives, settling to the next spire's height halfway through the flare
# as it brakes; on arrival it faces the centre. Spires 1 to 2, centre-relative; it starts facing the centre.
HOP=dict(push=8,travel=71,arc=12,lead=16,start=(73,29,-54),end=(80,32,34))
HOP['facing']=math.degrees(math.atan2(73,54))

def hop(body):
    """The hop and its landing, tick by tick: the server's position and heading, and the client's
    bank, climb and look. body is the body socket height each tick of the two chained clips."""
    return flown(body,HOP,lambda g:1)

# The takeoff follows StormRocCombat's tickTakeoff: crouched on the summit floor until the push
# (TAKEOFF_PUSH) while it turns onto its way out, then flown along its route over a 12-block arc that
# eases in, its first beats lifting it slowly off the rock. It flares into its landing LAND_LEAD
# before it arrives. From a guard spot on the summit floor, facing the centre, to spire 1; centre-relative.
TAKEOFF_PATH=dict(push=24,travel=88,arc=12,lead=16,start=(-6,0,14),end=(73,29,-54))
TAKEOFF_PATH['facing']=math.degrees(math.atan2(-6,-14))

def took(body):
    """The takeoff and its landing, tick by tick, as hop() gives the hop's."""
    return flown(body,TAKEOFF_PATH,lambda g:(lambda x:x*x*(3-2*x))(min(1,g/.6)))

# In a banked turn the game holds its head near level and turns it into the turn (StormRocMotion's
# steady): a turn in the model's space that the top of the neck takes NECK_SHARE of.
LEVEL,NECK_SHARE=.85,.35

def axis_turn(axis,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a)
    return {'x':np.array([[1,0,0],[0,c,-s],[0,s,c]]),'y':np.array([[c,0,s],[0,1,0],[-s,0,c]]),'z':np.array([[c,-s,0],[s,c,0],[0,0,1]])}[axis]

def steady_turn(ticks,t):
    """The head's turn t seconds into a flight baked by flown(): back against the client's bank,
    about its climbing body's long axis, and into the turn."""
    k=min(t*20,len(ticks)-1);i=int(k);j=min(i+1,len(ticks)-1);f=k-i
    bank,climb,look=(ticks[i][n]+(ticks[j][n]-ticks[i][n])*f for n in (2,3,5))
    return axis_turn('x',-climb)@axis_turn('z',-bank*LEVEL)@axis_turn('x',climb)@axis_turn('y',look)

def part_of(turn,share):
    """The same turn, share as far."""
    angle=math.acos(float(np.clip((np.trace(turn)-1)/2,-1,1)))
    if angle<1e-6:return np.eye(3)
    axis=np.array([turn[2,1]-turn[1,2],turn[0,2]-turn[2,0],turn[1,0]-turn[0,1]])/(2*math.sin(angle))
    K=np.array([[0,-axis[2],axis[1]],[axis[2],0,-axis[0]],[-axis[1],axis[0],0]]);a=angle*share
    return np.eye(3)+math.sin(a)*K+(1-math.cos(a))*K@K

def turned(M,pivot,turn):
    """A bone's world matrix turned in the model's space about its own pivot."""
    p=M[:3,:3]@np.array(pivot,float)+M[:3,3];R=np.eye(4);R[:3,:3]=turn;R[:3,3]=p-turn@p
    return R@M

def framed(ticks,origin,heading):
    """Ticks in a page frame with the Roc at origin facing -z at heading: [x, y, z, heading, bank,
    climb, pivot] (heading positive turns left, as the charge's)."""
    a=math.radians(heading)
    forward,right=np.array([-math.sin(a),math.cos(a)]),np.array([-math.cos(a),-math.sin(a)])
    page=lambda v:[float(np.dot(v[[0,2]],right)),float(v[1]),-float(np.dot(v[[0,2]],forward))]
    path=[[round(v,4) for v in (*page(pos-origin),-(yaw-heading),bank,climb,pivot)] for pos,yaw,bank,climb,pivot,*_ in ticks]
    return path,page

def chained(T,stop):
    """Which clip, and when in it, the game is playing T seconds into the charge."""
    tick=T*20;run_at=CHARGE['run_at']
    if tick<run_at:return 'stampede',T
    if tick<stop:return 'stampede_run',((tick-run_at)/20)%DEFINITIONS['stampede_run'][0]
    return 'stampede_skid',min((tick-stop)/20,DEFINITIONS['stampede_skid'][0])

def taken(T):
    """Which clip, and when in it, the game is playing T seconds into the takeoff: it, then its landing."""
    return ('takeoff',T) if T<TAKEOFF else ('land',min(T-TAKEOFF,LAND))

def hopped(T):
    """Which clip, and when in it, the game is playing T seconds into the hop: the hop, then its landing."""
    return ('spire_flight',T) if T<SPIRE_FLIGHT else ('land',min(T-SPIRE_FLIGHT,LAND))

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
        first=sockets[name][0];fps=CLIP_FPS.get(name,FPS);extra={};flight=None
        if name=='stampede':
            path,prey,stop=charge();duration=(stop+CHARGE['after'])/20
            extra={'path':path,'prey':prey,'launch':STAMPEDE_RUN,'stop':stop/20}
        if name in ('spire_flight','land'):
            # The hop plays on into its landing, as in the game; the landing alone comes in over
            # its spire along the hop's last stretch.
            ticks=hop([frame['body'][1] for frame in sockets['spire_flight'][:-1]+sockets['land']])
            start,end=np.array(HOP['start'],float),np.array(HOP['end'],float)
            if name=='spire_flight':
                path,page=framed(ticks,start,ticks[0][1]);duration=SPIRE_FLIGHT+LAND;flight=ticks
                extra={'path':path,'to':page(end-start),'centre':page(-start),'land':SPIRE_FLIGHT}
            else:
                flight=ticks[len(sockets['spire_flight'])-1:]
                path,page=framed(flight,end,ticks[-1][1])
                extra={'path':path,'centre':page(-end)}
                # It stands on its spire, and is framed there, as it ends.
                first=sockets[name][-1]
        if name=='takeoff':
            # The takeoff, too, plays on into its landing on its next spire, along the server's path.
            ticks=took([frame['body'][1] for frame in sockets['takeoff'][:-1]+sockets['land']])
            start,end=np.array(TAKEOFF_PATH['start'],float),np.array(TAKEOFF_PATH['end'],float)
            path,page=framed(ticks,start,ticks[0][1]);duration=TAKEOFF+LAND;flight=ticks
            extra={'path':path,'to':page(end-start),'centre':page(-start),'land':TAKEOFF,
                'landFeet':min(sockets['land'][-1]['left_foot'][1],sockets['land'][-1]['right_foot'][1])}
        frames=[]
        for i in range(round(duration*fps)+1):
            t=i/fps;world={};clip,at=chained(t,stop) if name=='stampede' else hopped(t) if name=='spire_flight' else taken(t) if name=='takeoff' else (name,t)
            head=steady_turn(flight,t) if flight else None
            for b in bones:
                world[b['name']]=world.get(b.get('parent'),np.eye(4))@matrix(b,clips[clip]['bones'].get(b['name'],{}),at)
                # The top of the neck takes its share of the head's turn; the head, the rest.
                if head is not None and b['name']=='neck_tip':world['neck_tip']=turned(world['neck_tip'],b['pivot'],part_of(head,NECK_SHARE))
                if head is not None and b['name']=='head':world['head']=turned(world['head'],b['pivot'],head@part_of(head,NECK_SHARE).T)
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
