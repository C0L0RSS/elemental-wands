#!/usr/bin/env python3
"""Full-body performance pass. Bake eased poses, planted-foot IK, and head stabilization."""
import copy
import json
import math
import runpy
from pathlib import Path
import numpy as np

HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'v3-animated'
SOURCE=HERE.parent/'v2-textured'
legacy=runpy.run_path(str(OLD/'build_animations.py'))
reference=runpy.run_path(str(OLD/'preview_motion.py'))
bones=reference['bones']; mesh=reference['mesh']; PREFIX=legacy['PREFIX']


def ease(t, times, values):
    if t<=times[0]:return np.array(values[0],dtype=float)
    for i in range(1,len(times)):
        if t<=times[i]:
            q=(t-times[i-1])/(times[i]-times[i-1]);q=q*q*(3-2*q)
            return np.array(values[i-1])*(1-q)+np.array(values[i])*q
    return np.array(values[-1],dtype=float)


def original(clip,t):
    p={}
    for bone,chs in clip['bones'].items():
        p[bone]={}
        for kind,keys in chs.items():
            pairs=sorted((float(k),v) for k,v in keys.items())
            p[bone][kind]=ease(t,[x[0] for x in pairs],[x[1] for x in pairs])
    return p


def setp(p,bone,kind,xyz):p.setdefault(bone,{})[kind]=np.array(xyz,dtype=float)
def rot(p,bone,xyz):setp(p,bone,'rotation',xyz)
def pos(p,bone,xyz):setp(p,bone,'position',xyz)


def transforms(p):
    out={}
    for name,b in bones.items():
        c=p.get(name,{});x,y,z=np.radians(c.get('rotation',[0,0,0])*np.array([-1,1,-1]))
        cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        r=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
        pivot=np.array(b['pivot']);m=np.eye(4);m[:3,:3]=r;m[:3,3]=pivot-r@pivot+c.get('position',np.zeros(3))
        out[name]=out[b['parent']]@m if b.get('parent') else m
    return out


def legs(p, t, walking):
    # Targets are world/model-space sole positions; the pelvis can crouch without skating feet.
    py,pz=p['pelvis']['position'][1:]
    l1,l2=math.hypot(14,3),math.hypot(13,3)
    a0=math.atan2(14,-3);a20=math.atan2(13,3)-a0
    for side,offset in [('right',0),('left',.5)]:
        dz,lift=0,0
        if walking:
            phase=(t/2+offset)%1
            if phase<.6:dz=-5+10*phase/.6
            else:
                q=(phase-.6)/.4;dz=5-10*(q*q*(3-2*q));lift=4*math.sin(math.pi*q)**2
        z,down=dz-pz,27+py-lift
        c=(z*z+down*down-l1*l1-l2*l2)/(2*l1*l2)
        assert -1<=c<=1, ('unreachable foot',side,t,c)
        a2=-math.acos(c)
        a1=math.atan2(down,z)-math.atan2(l2*math.sin(a2),l1+l2*math.cos(a2))-a0
        a2-=a20
        x,y=-math.degrees(a1),-math.degrees(a2)
        rot(p,side+'_thigh',[x,0,0]);rot(p,side+'_shin',[y,0,0]);rot(p,side+'_foot',[-x-y,0,0])


def fingers(p,side,grip,spread=0,lag=0):
    sign=-1 if side=='right' else 1
    for i in range(1,4):
        rot(p,f'{side}_finger_{i}',[-grip*(.9+.08*i),0,sign*(i-2)*spread])
        rot(p,f'{side}_finger_{i}_tip',[-grip*.65+lag,0,0])
    rot(p,side+'_thumb',[-grip*.5,sign*grip*.25,-sign*grip*.18])
    rot(p,side+'_thumb_tip',[-grip*.65,0,0])


def performance(name,t,base):
    p=original(base,t)
    if name=='idle':
        a=t*math.pi/3; breath=(1-math.cos(a))*.5
        pos(p,'pelvis',[0,-.8*breath,.3*math.sin(a)])
        rot(p,'torso',[-1.5*breath,1.8*math.sin(a),.9*math.sin(a)])
        rot(p,'head',[1.0*breath,-2.8*math.sin(a-.2)+2.8*math.sin(-.2),-.5*math.sin(a)])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,side+'_shoulder',[0,1.2+.7*breath,0])
            rot(p,side+'_upper_arm',[-1.5-1.8*math.sin(a+sign*.4),0,-sign*(1+breath)])
            rot(p,side+'_forearm',[-2-1.5*math.sin(a+sign*.4-.4),0,0])
            rot(p,side+'_hand',[1.3*math.sin(a-.7),0,sign*.8*math.sin(a)])
            fingers(p,side,3+2*math.sin(a+sign*.5),.5)
            pos(p,f'chest_plate_{sign}',[sign*.65*breath,.15*breath,-.25*breath])
    elif name=='walk':
        a=t*math.pi
        pos(p,'pelvis',[0,-1.5+.65*math.cos(2*a),.45*math.sin(2*a)])
        rot(p,'torso',[2+1.7*math.sin(2*a),4*math.sin(a),2.4*math.cos(a)])
        rot(p,'head',[-1.5-1.2*math.sin(2*a-.2),-3*math.sin(a),-1.5*math.cos(a)])
        for side,sign in [('right',-1),('left',1)]:
            swing=math.cos(a+(0 if side=='right' else math.pi))
            rot(p,side+'_shoulder',[0,sign*2*swing,-sign*1.5])
            pos(p,side+'_shoulder',[0,2+.5*math.sin(2*a),0])
            rot(p,side+'_upper_arm',[-8-12*swing,0,-sign*5])
            rot(p,side+'_forearm',[-12+6*math.cos(a+(0 if side=='right' else math.pi)-.4),0,0])
            rot(p,side+'_hand',[5*math.sin(a+(0 if side=='right' else math.pi)-.55),0,0])
            fingers(p,side,12+4*math.sin(a+(0 if side=='right' else math.pi)-.5),1)
            pos(p,f'chest_plate_{sign}',[sign*.25,0,-.3*math.sin(2*a)])
    elif name=='beam':
        ts=[0,.3,.85,1.2,1.6,1.72,2.2,2.65,3.6]
        load=float(ease(t,ts,[0,.25,.75,1,1,1,.85,.35,0]))
        recoil=float(ease(t,ts,[0,0,0,0,0,1,.55,.12,0]))
        pos(p,'pelvis',[0,-3*load-1.3*recoil,.9*load])
        rot(p,'torso',[-7*load-4*recoil,0,0])
        pos(p,'torso',[0,2.6*load,1.1*recoil])
        jaw=float(ease(t,[0,.35,1,1.6,2.2,2.65,3.6],[0,10,28,42,40,18,0]))
        rot(p,'jaw',[jaw,0,0])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,f'chest_plate_{sign}',[sign*3.8*load,1.1*load,-1.8*load])
            rot(p,f'chest_plate_{sign}',[0,-sign*10*load,-sign*3*load])
            pos(p,side+'_shoulder',[sign*1.4*load,1.2+2.8*load,1.0*load])
            rot(p,side+'_shoulder',[-4*load,sign*5*load,-sign*5*load])
            rot(p,side+'_upper_arm',[-22*load-8*recoil,0,-sign*32*load])
            rot(p,side+'_forearm',[-23*load-8*recoil,sign*4*load,0])
            rot(p,side+'_hand',[8*load,sign*8*load,0])
            fingers(p,side,8*load,7*load,2*recoil)
        pos(p,'core',[0,.4*load,-1.2*load])
        # Head keeps a fixed world-space pivot while the torso loads around it.
        # Runtime head pitch is added to this counter-rotation, preserving muzzle alignment.
        parent=transforms(p)['torso'];hp=np.array(bones['head']['pivot'])
        pos(p,'head',np.linalg.solve(parent[:3,:3],hp-parent[:3,3])-hp)
        rot(p,'head',[-p['torso']['rotation'][0],0,0])
    else:
        if name=='awaken':
            ts=[0,.65,1.25,1.8,2.35,2.7,3.2,4.2]
            crouch=float(ease(t,ts,[4,4,3.2,2,0,.8,.3,0]))
            pos(p,'pelvis',[0,-crouch,.5*crouch])
            rot(p,'torso',ease(t,ts,[[9,0,0],[9,0,0],[5,-2,-1],[1,2,0],[-8,0,0],[-5,0,0],[2,0,0],[0,0,0]]))
            rot(p,'head',ease(t,ts,[[16,0,0],[16,0,0],[8,5,0],[0,-4,0],[-5,0,0],[0,0,0],[2,0,0],[0,0,0]]))
            spread=float(ease(t,ts,[0,0,4,10,18,12,5,0]))
            opening=float(ease(t,ts,[0,0,.3,.8,1,.7,.2,0]))
            for side,sign in [('right',-1),('left',1)]:
                rot(p,side+'_upper_arm',[-spread,0,-sign*spread])
                rot(p,side+'_hand',[spread*.2,sign*spread*.3,0])
                fingers(p,side,float(ease(t,[0,.7,1.3,1.9,2.4,3,4.2],[0,0,15,30,12,5,0])),opening*4)
                pos(p,side+'_shoulder',[0,2+opening*2,0])
                pos(p,f'chest_plate_{sign}',[sign*opening*2,opening*.3,-opening*.8])
        elif name=='slam':
            ts=[0,.35,.7,1.08,1.2,1.3,1.44,1.75,2.25,2.8]
            pos(p,'pelvis',[0,float(ease(t,ts,[0,-2.5,-1,0,-.2,-4.5,-4,-3,-1,0])),float(ease(t,ts,[0,1,0,-.5,-.5,-1,-1,-.5,0,0]))])
            rot(p,'torso',ease(t,ts,[[0,0,0],[4,-2,0],[-7,1,0],[-13,0,0],[-12,0,0],[14,0,0],[11,0,0],[8,0,0],[2,0,0],[0,0,0]]))
            recoil=0 if t<1.3 else math.sin((t-1.3)*20)*math.exp(-(t-1.3)*7)
            for side,sign in [('right',-1),('left',1)]:
                p[side+'_upper_arm']['rotation'][2]=-sign*float(ease(t,[0,.4,1.1,1.3,2,2.8],[0,8,12,7,4,0]))
                rot(p,side+'_hand',[recoil*4,0,sign*recoil*2])
                rot(p,side+'_shoulder',[recoil*2,0,0])
                pos(p,f'chest_plate_{sign}',[sign*.35*abs(recoil),0,-abs(recoil)*.5])
                rot(p,side+'_thumb',[-30*float(ease(t,[0,.5,1.5,2.8],[0,1,1,0])),0,0])
        else: # throw: crouch/pick, turn/load, release, opposite-arm counterweight.
            ts=[0,.55,1,1.65,2,2.2,2.4,2.85,3.6]
            pos(p,'pelvis',[0,float(ease(t,ts,[0,-3.5,-3,-1,-.5,-2,-1.5,-.5,0])),float(ease(t,ts,[0,-1,-1,1,1.5,-1,-1,-.3,0]))])
            rot(p,'torso',ease(t,ts,[[0,0,0],[9,-9,-3],[9,-9,-3],[-9,-24,4],[-11,-26,4],[8,20,-3],[6,17,-2],[2,6,0],[0,0,0]]))
            rot(p,'head',ease(t,ts,[[0,0,0],[5,9,1],[5,9,1],[6,20,-2],[7,23,-2],[-6,-18,2],[-4,-15,1],[0,-5,0],[0,0,0]]))
            rot(p,'left_upper_arm',ease(t,ts,[[0,0,0],[-20,0,-12],[-22,0,-15],[-30,8,-27],[-35,8,-30],[5,-8,-17],[2,-8,-14],[-4,0,-4],[0,0,0]]))
            rot(p,'left_forearm',ease(t,ts,[[0,0,0],[-14,0,0],[-14,0,0],[-25,0,0],[-30,0,0],[-12,0,0],[-12,0,0],[-6,0,0],[0,0,0]]))
            rot(p,'right_hand',ease(t,ts,[[0,0,0],[10,0,0],[5,0,0],[-14,-5,0],[-20,-8,0],[16,8,0],[10,5,0],[3,0,0],[0,0,0]]))
            fingers(p,'left',float(ease(t,ts,[0,6,10,15,15,5,4,2,0])),2)
            grip=float(ease(t,ts,[0,0,55,55,55,-5,0,0,0]));fingers(p,'right',grip,4 if 2.2<t<2.7 else 0)
            for sign in [-1,1]:pos(p,f'chest_plate_{sign}',[sign*.4*math.sin(t*math.pi/3.6),0,0])
    legs(p,t,name=='walk')
    # Protect the long knuckles through crouch/settle; only lift the relevant shoulder.
    for side in ['right','left']:
        mats=transforms(p);low=999
        for c in mesh:
            if c['bone'].startswith(side+'_') and not any(x in c['bone'] for x in ['thigh','shin','foot']):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];low=min(low,float(v[:,1].min()))
        if low<.12:
            par=mats['torso'];p.setdefault(side+'_shoulder',{}).setdefault('position',np.zeros(3))[1]+=(.12-low)/par[1,1]
    return p


def reduce_keys(keys,tolerance):
    # Remove samples only when straight interpolation stays within the authored error budget.
    entries=sorted((float(k),v) for k,v in keys.items());times=np.array([e[0] for e in entries]);values=np.array([e[1] for e in entries])
    keep={0,len(entries)-1};pending=[(0,len(entries)-1)]
    while pending:
        a,b=pending.pop()
        if b<=a+1:continue
        f=((times[a+1:b]-times[a])/(times[b]-times[a]))[:,None]
        approx=values[a]+(values[b]-values[a])*f
        errors=np.max(np.abs(values[a+1:b]-approx),axis=1);i=int(np.argmax(errors))+a+1
        if errors[i-a-1]>tolerance:keep.add(i);pending.extend([(a,i),(i,b)])
    return {str(entries[i][0]):entries[i][1] for i in sorted(keep)}


def build():
    old=legacy['build']();out={'format_version':'1.8.0','animations':{}}
    for key,base in old['animations'].items():
        name=key.removeprefix(PREFIX);duration=3.6 if name=='beam' else base['animation_length']
        clip={'loop':base['loop'],'animation_length':duration,'bones':{}}
        rate=80 if name=='slam' else 40
        for i in range(round(duration*rate)+1):
            t=round(i/rate,4);p=performance(name,t,base)
            for bone,chs in p.items():
                for kind,value in chs.items():
                    clip['bones'].setdefault(bone,{}).setdefault(kind,{})[str(t)]=[round(float(v),5) for v in value]
        if clip['loop']:
            for chs in clip['bones'].values():
                for keys in chs.values():keys[list(keys)[-1]]=next(iter(keys.values()))
        for bone,chs in clip['bones'].items():
            for kind,keys in list(chs.items()):
                precise=kind=='position' or bone in ['head','torso'] or any(s in bone for s in ['thigh','shin','foot'])
                chs[kind]=reduce_keys(keys,.0015 if precise else .012)
        out['animations'][key]=clip
    return out

if __name__=='__main__':
    data=build();(HERE/'fractured_guardian.animation.json').write_text(json.dumps(data,indent=2)+'\n')
    project=legacy['blockbench'](data);project['name']='Fractured Guardian — Full-body performance 04'
    (HERE/'fractured_guardian.bbmodel').write_text(json.dumps(project,indent=2)+'\n')
    print('Baked six full-body clips with planted-foot IK, eased timing and layered hand motion.')
