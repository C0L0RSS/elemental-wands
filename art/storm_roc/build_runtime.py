#!/usr/bin/env python3
"""Install the approved Roc study and a synchronized first-phase combat pose track.

The study stays untouched. Runtime poses and server sockets share the same sampled
transforms; even a client joining mid-attack draws the server's exact combat frame.
"""
import argparse, copy, json, math
from pathlib import Path
import numpy as np
from PIL import Image
from validate_review import sample, matrix
from poses import Pose
from legs import solve_leg
from types import SimpleNamespace
from fold import rotation

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
SCALE = 2.5 / 16
# While stuck, each wing's lowest feather rests this far into the rock (blocks at 2.5x)
# and lifts by up to WING_SLAP between weak beats.
WING_REST, WING_SLAP = -.25, 1.4

def packed(value):
    return (json.dumps(value, separators=(',', ':')) + '\n').encode()

def main():
    check = argparse.ArgumentParser()
    check.add_argument('--check', action='store_true')
    args = check.parse_args()
    geo = json.loads((HERE/'candidate/storm_roc.geo.json').read_text())
    bones = geo['minecraft:geometry'][0]['bones']
    source = json.loads((HERE/'candidate/storm_roc.animation.json').read_text())['animations']
    by = {b['name']: b for b in bones}
    def pose(name, time):
        c = source['animation.storm_roc.'+name]
        return {b['name']: {k: np.array(sample(c['bones'].get(b['name'], {}).get(k), time, d), dtype=float)
                for k,d in [('rotation',[0,0,0]), ('position',[0,0,0]), ('scale',[1,1,1])]} for b in bones}
    def mix(a,b,f):
        out = copy.deepcopy(a)
        for n in out:
            for k in out[n]:
                delta=b[n][k]-a[n][k]
                if k=='rotation': delta=(delta+180)%360-180
                out[n][k]=a[n][k]+delta*f
        return out
    def matrices(p):
        m={}
        for b in bones:m[b['name']]=m.get(b.get('parent'),np.eye(4))@matrix(b,{k:v.tolist() for k,v in p[b['name']].items()},0)
        return m
    def point(m,n,v): return (m[n]@np.r_[v,1])[:3]
    # Head, bill tip, neck, chest, feet. Head-local geometry remains locked.
    tipcube=min(by['upper_beak']['cubes'],key=lambda c:c['origin'][2])
    tip=np.array(tipcube['origin'])+np.array(tipcube['size'])*[.5,.2,0]
    landmarks={'head':('head',by['head']['pivot']), 'beak':('upper_beak',tip),
        'neck':('neck_upper',by['neck_upper']['pivot']), 'body':('body',[0,65,5]),
        'left_foot':('left_foot',by['left_foot']['pivot']), 'right_foot':('right_foot',by['right_foot']['pivot'])}
    def ease(t,a,b):
        f=float(np.clip((t-a)/(b-a),0,1));return f*f*(3-2*f)
    def wings(p, t, weight=1):
        flight=pose('flight',t%2.4)
        for n in p:
            if any(s in n for s in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries')):
                for k in p[n]:
                    delta=flight[n][k]-p[n][k]
                    if k=='rotation':delta=(delta+180)%360-180
                    p[n][k]+=delta*weight
    def plant(p, targets):
        rig=Pose(SimpleNamespace(bones=bones));rig.world=matrices(p)
        for label,foot in targets.items():
            hip=point(rig.world,label+'_thigh',by[label+'_thigh']['pivot'])
            # A true fixed-length avian solve, including backward hock, avoids
            # detached feet during the anticipation, run, and landing crouches.
            foot=np.array(foot,dtype=float)
            reach=foot-hip
            if np.linalg.norm(reach)>66:foot=hip+reach*66/np.linalg.norm(reach)
            theta=-75
            for candidate in [-75]+[-75+d for i in range(1,181) for d in (i,-i)]:
                knee=hip+rotation([candidate,0,0])@np.array([0,-14,0])
                if 2.001<np.linalg.norm((np.array(foot)-knee)[1:])<53.999:
                    theta=candidate;break
            solve_leg(rig,label,hip,foot,theta)
        for n,cs in rig.channels.items():
            for k,v in cs.items():p[n][k]=np.array(v,dtype=float)
    def locomotion_mix(a,b,f):
        p=mix(a,b,f)
        ma,mb=matrices(a),matrices(b)
        rig=Pose(SimpleNamespace(bones=bones));rig.world=matrices(p)
        for label in ('left','right'):
            hip=point(rig.world,label+'_thigh',by[label+'_thigh']['pivot'])
            foot=(1-f)*point(ma,label+'_foot',by[label+'_foot']['pivot'])+f*point(mb,label+'_foot',by[label+'_foot']['pivot'])
            def angle(m):
                d=m[label+'_thigh'][:3,:3]@np.array([0,-14,0])
                return math.degrees(math.atan2(d[2],-d[1]))
            desired=(1-f)*angle(ma)+f*angle(mb)
            # Find the nearest reachable femur orientation along the lift.
            candidates=[desired]+[desired+d for i in range(1,181) for d in (i,-i)]
            for theta in candidates:
                knee=hip+rotation([theta,0,0])@np.array([0,-14,0])
                distance=np.linalg.norm((foot-knee)[1:])
                if 2.001<distance<53.999:break
            solve_leg(rig,label,hip,foot,theta)
        for n,cs in rig.channels.items():
            for k,v in cs.items():p[n][k]=np.array(v,dtype=float)
        return p
    neutral=pose('hunched',0)
    neutral_m=matrices(neutral)
    feet={label:point(neutral_m,label+'_foot',by[label+'_foot']['pivot']) for label in ('left','right')}
    # The bill tip is driven about 1.5 blocks into the rock (at 2.5x), not resting on it.
    pin=point(matrices(pose('peck',.5)),'upper_beak',tip);pin[1]=-9.6
    wing_keys=('wing','primary','secondary','covert','leading','root_','primaries','secondaries')
    wing_cubes={label:[(b['name'],np.array(c['origin'])+np.array(c['size'])*[dx,dy,dz])
        for b in bones if b['name'].startswith(label+'_') and any(k in b['name'] for k in wing_keys)
        for c in b.get('cubes',[]) for dx in (0,1) for dy in (0,1) for dz in (0,1)] for label in ('left','right')}
    def splay(p,t):
        # Roll each shoulder down until that wing's lowest feather meets the rock, then let
        # it lift and slap down again: the wings splay out and beat weakly against the ground.
        base={label:p[label+'_wing_shoulder']['rotation'][2] for label in ('left','right')}
        lo={'left':-60.,'right':-60.};hi={'left':10.,'right':10.}
        targets={label:(WING_REST+WING_SLAP*max(0,math.sin(t*7+side*.9))**2)/SCALE
            for label,side in (('left',1),('right',-1))}
        for _ in range(14):
            mid={label:(lo[label]+hi[label])/2 for label in lo}
            for label,side in (('left',1),('right',-1)):
                p[label+'_wing_shoulder']['rotation'][2]=base[label]+side*mid[label]
            m=matrices(p)
            for label in lo:
                low=min((m[n]@np.r_[v,1])[1] for n,v in wing_cubes[label])
                if low>targets[label]:hi[label]=mid[label]
                else:lo[label]=mid[label]
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_shoulder']['rotation'][2]=base[label]+side*(lo[label]+hi[label])/2
    def stuck(t):
        p=pose('peck',.5)
        wings(p,1.8+.05*math.sin(t*7))
        heave=ease(t,4.85,5.65)
        p['root']['rotation'][0]=10-3*heave+1.3*math.sin(t*11)*(1-heave)
        p['body']['rotation'][0]+=3*math.sin(t*8)*(1-heave)-7*heave
        p['neck_base']['rotation'][0]+=6*heave
        for side,label in ((-1,'right'),(1,'left')):
            kick=math.sin(t*13+side*math.pi/2)
            p[label+'_thigh']['rotation'][0]+=16*kick*(1-.65*heave)
            p[label+'_shin']['rotation'][0]-=12*kick
            p[label+'_tarsus']['rotation'][0]+=18*math.sin(t*13+side*math.pi/2-.5)
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=20*math.sin(t*17+i+side)
        p['lower_beak']['rotation'][0]=18+10*math.sin(t*16)
        at=point(matrices(p),'upper_beak',tip)
        p['root']['position']+=pin-at
        splay(p,t)
        m=matrices(p);targets={}
        for side,label in ((-1,'right'),(1,'left')):
            foot=point(m,label+'_foot',by[label+'_foot']['pivot'])
            foot[1]=3.75+7*max(0,math.sin(t*13+side*math.pi/2))
            targets[label]=foot
        plant(p,targets)
        # Beak remains genuinely pinned through the anticipation heave.
        return mix(p,neutral,ease(t,5.65,6))
    definitions={'perched':(4,True),'flight':(2.4,True),'hunched':(4,True),
        'dive':(3,False),'stuck':(6,False),'peck':(1.8,False),
        'swing_left':(1.8,False),'swing_right':(1.8,False),'scream':(2.2,False),
        'wing':(2,False),'pounce':(3.2,False),'stampede':(4.2,False),'takeoff':(2.5,False)}
    animations={}; sockets={}
    for name,(duration,loop) in definitions.items():
        frames=[]; points=[]
        for frame in range(round(duration*20)+1):
            t=frame/20
            if name in ('perched','flight','hunched'):
                p=pose(name,t)
                if name=='perched':p['root']['position'][1]-=65.25
            elif name=='stuck':p=stuck(t)
            elif name=='dive':
                p=pose('flight',t%2.4)
                p['root']['rotation'][0]=58*ease(t,.5,1.8)
                wings(p,.8, .65*ease(t,.5,1.5))
                if t>2.35:p=mix(p,stuck(0),ease(t,2.35,3))
            elif name=='peck':
                # Long readable draw-back, then a hard beak strike at 0.6s.
                source_t=float(np.interp(t,[0,.45,.6,.8,1.8],[0,.3,.5,.65,1.6]))
                p=pose('peck',source_t)
            elif name.startswith('swing'):
                p=pose('hunched',0)
                side=1 if name.endswith('left') else -1
                a=float(np.interp(t,[0,.6,.8,1,1.15,1.8],[0,-28,-28,38,30,0]))*side
                p['neck_base']['rotation'][1]+=a
                p['neck_lower']['rotation'][1]+=a*.25
                p['head']['rotation'][2]+=a*.12
                p['lower_beak']['rotation'][0]=10*ease(t,.5,.8)*(1-ease(t,1.2,1.6))
                # Carry the actual bill through grounded players, rather than
                # stretching an invisible hitbox down from the standing head.
                low=ease(t,.45,.8)*(1-ease(t,1.15,1.65))
                start=p['neck_base']['rotation'][0]
                current=point(matrices(p),'upper_beak',tip)[1]
                target=current*(1-low)+(1.7/SCALE)*low
                lo,hi=-25.,35.
                for _ in range(16):
                    angle=(lo+hi)/2
                    p['neck_base']['rotation'][0]=start+angle
                    y=point(matrices(p),'upper_beak',tip)[1]
                    if y>target:lo=angle
                    else:hi=angle
                p['neck_base']['rotation'][0]=start+(lo+hi)/2
            elif name=='scream':
                breath=ease(t,0,.9)*(1-ease(t,1.15,1.2))
                blast=ease(t,1.15,1.2)*(1-ease(t,1.95,2.2))
                p=pose('hunched',0)
                p['body']['rotation'][0]-=1.8*breath
                p['body']['position'][1]+=.5*breath
                plant(p,feet)
                p['neck_base']['rotation'][0]-=7*breath
                p['head']['rotation'][0]-=8*breath
                p['lower_beak']['rotation'][0]=5*breath+48*blast
                p['neck_base']['rotation'][0]+=3*blast*math.sin(t*65)
            elif name in ('wing','takeoff'):
                p=pose('hunched',0)
                lift=ease(t,0,1)
                wings(p,.6,lift)
                if t>1:wings(p,.6+(t-1)*1.8,1)
                if name=='wing':p=mix(p,neutral,ease(t,1.4,2))
                else:p=locomotion_mix(p,pose('flight',(t-1.2)%2.4),ease(t,1.2,2))
            elif name=='pounce':
                p=pose('hunched',0)
                crouch=ease(t,0,.6)*(1-ease(t,1.1,1.2))
                p['body']['position'][1]-=5*crouch
                p['body']['rotation'][2]+=2.2*math.sin(t*22)*crouch
                p['tail_fan']['rotation'][1]+=14*math.sin(t*22)*crouch
                p['tail_fan']['rotation'][2]+=4*math.sin(t*22+.6)*crouch
                plant(p,feet)
                if t>=1.1:
                    air=pose('grab',min(1.2,(t-1.1)*1.3))
                    wings(air,.6+(t-1.2)*2)
                    p=locomotion_mix(p,air,ease(t,1.1,1.25))
                if t>=2.05:
                    land=pose('hunched',0)
                    land['body']['position'][1]-=7*(1-ease(t,2.25,3.2))
                    plant(land,feet)
                    p=locomotion_mix(p,land,ease(t,2.05,2.2))
            else:
                p=pose('hunched',0)
                targets={k:v.copy() for k,v in feet.items()}
                if t<1.2:
                    # Two unmistakable bull scrapes, with one supporting foot.
                    scrape=math.sin(math.pi*((t%.6)/.6))
                    targets['right'][1]+=4*scrape
                    targets['right'][2]+=15*scrape
                    p['head']['rotation'][0]+=7*scrape
                elif t<2.7:
                    cycle=(t-1.2)*18
                    p['body']['position'][1]+=2*abs(math.sin(cycle))
                    for side,label in ((-1,'right'),(1,'left')):
                        step=cycle+(0 if side==1 else math.pi)
                        targets[label][2]+=12*math.cos(step)
                        targets[label][1]+=7*max(0,math.sin(step))
                    p['neck_base']['rotation'][0]+=2*math.sin(cycle)
                else:
                    skid=1-ease(t,2.7,4.2)
                    p['body']['rotation'][0]-=7*skid
                    for side,label in ((-1,'right'),(1,'left')):
                        targets[label][2]-=9*skid
                        targets[label][1]+=2*abs(math.sin(t*22+side))*skid
                    p['neck_base']['rotation'][1]+=40*ease(t,3.25,3.75)*(1-ease(t,3.9,4.2))
                plant(p,targets)
            frames.append(p)
            m=matrices(p)
            points.append({key:[round(float(v),5) for v in point(m,n,v)*SCALE] for key,(n,v) in landmarks.items()})
        channels={}
        for b in bones:
            ch={}
            for k,default in [('rotation',[0,0,0]),('position',[0,0,0]),('scale',[1,1,1])]:
                values=[[round(float(v),4) for v in f[b['name']][k]] for f in frames]
                if all(v==default for v in values):continue
                ch[k]=values[0] if all(v==values[0] for v in values) else {f'{i/20:.2f}':v for i,v in enumerate(values)}
            channels[b['name']]=ch
        animations[name]={'loop':loop,'animation_length':duration,'bones':channels}
        sockets[name]=points
    # The offline study uses unreflected Cartesian X. Gecko mirrors geometry,
    # pivots and position X while keeping its rotation parser signs unchanged.
    # Conjugate each authored rotation by that reflection (Y/Z sign reversal)
    # so the runtime mesh is exactly the reflected approved study, including
    # cube rotations. Source records, dimensions, pivots, UVs and atlas stay intact.
    runtime_geo=copy.deepcopy(geo)
    for bone in runtime_geo['minecraft:geometry'][0]['bones']:
        for item in [bone]+bone.get('cubes',[]):
            if 'rotation' in item:item['rotation']=[item['rotation'][0],-item['rotation'][1],-item['rotation'][2]]
    for animation in animations.values():
        for channel in animation['bones'].values():
            if 'rotation' not in channel:continue
            values=channel['rotation']
            if isinstance(values,list):channel['rotation']=[values[0],-values[1],-values[2]]
            else:
                for key,v in values.items():values[key]=[v[0],-v[1],-v[2]]
    outputs={
        ASSETS/'geckolib/models/storm_roc.geo.json':packed(runtime_geo),
        ASSETS/'geckolib/animations/storm_roc.animation.json':packed({'format_version':'1.8.0','animations':{'animation.storm_roc.'+k:v for k,v in animations.items()}}),
        ASSETS/'storm_roc_sockets.json':packed(sockets),
        ASSETS/'storm_roc_sparks.json':(HERE/'candidate/storm_roc_sparks.json').read_bytes(),
        ASSETS/'textures/entity/storm_roc_sparks.png':(HERE/'candidate/storm_roc_sparks.png').read_bytes(),
        ASSETS/'textures/entity/storm_roc.png':(HERE/'candidate/storm_roc.png').read_bytes(),
        ASSETS/'textures/entity/storm_roc_glowmask.png':(HERE/'candidate/storm_roc_glowmask.png').read_bytes(),
    }
    # A white texel lets the custom lightning/tornado meshes carry their own palette.
    import io
    white=Image.new('RGBA',(1,1),(255,255,255,255));buf=io.BytesIO();white.save(buf,format='PNG')
    outputs[ASSETS/'textures/misc/roc_white.png']=buf.getvalue()
    for path,data in outputs.items():
        if args.check:
            assert path.exists() and path.read_bytes()==data, f'Runtime asset drift: {path}'
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print(('Checked' if args.check else 'Installed')+f' {len(outputs)} Storm Roc assets; {len(animations)} poses, preserved study with Gecko basis conversion, unchanged atlas, 2.5x sockets.')
if __name__=='__main__':main()
