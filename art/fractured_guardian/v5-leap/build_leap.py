"""Add the double-shockwave leap without changing any approved V4 clip or model geometry."""
from pathlib import Path
import copy,json,runpy,math
import numpy as np
HERE=Path(__file__).resolve().parent
BASE=HERE.parent/'v4-expressive'
h=runpy.run_path(str(BASE/'build_animations.py'))
rot,pos,ease=h['rot'],h['pos'],h['ease']
slam=h['legacy']['build']()['animations']['animation.fractured_guardian.slam']

def lift_hands(p):
    for side in ['right','left']:
        mats=h['transforms'](p);low=999
        for cube in h['mesh']:
            if cube['bone'].startswith(side+'_') and not any(w in cube['bone'] for w in ['thigh','shin','foot']):
                m=mats[cube['bone']];vertices=np.array(cube['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(vertices[:,1].min()))
        if low<.12:
            p.setdefault(side+'_shoulder',{}).setdefault('position',np.zeros(3))[1]+=(.12-low)/mats['torso'][1,1]
    return p

def pose(name,t):
    if name=='leap_launch':
        # Existing full-body slam compressed into a vigorous push-off, with planted feet.
        return h['performance']('slam',min(1.3,t/.7*1.3),slam)
    p=copy.deepcopy(h['performance']('slam',1.3,slam))
    if name=='leap_air':
        q=float(ease(t,[0,.22,1],[0,1,1]))
        tuck=float(ease(t,[0,.25,.55,.9,1],[0,1,1,0,0]))
        pos(p,'pelvis',[0,-4.5*(1-q),-1*(1-q)])
        rot(p,'torso',[14*(1-q)-10*q,0,0]);rot(p,'head',[-4*(1-q)+10*q,0,0])
        for side,sign in [('right',-1),('left',1)]:
            # Momentum from pushing off becomes an overhead, chest-open airborne pose.
            a=p[side+'_upper_arm']['rotation']
            rot(p,side+'_upper_arm',a*(1-q)+np.array([-105,0,-sign*18])*q)
            rot(p,side+'_forearm',[-50*q,0,0]);rot(p,side+'_hand',[-10*q,0,-sign*4*q])
            pos(p,side+'_shoulder',[0,2+2*q,0])
            h['fingers'](p,side,30+15*q,1)
            pos(p,f'chest_plate_{sign}',[sign*1.5*q,.3*q,-.7*q])
        h['legs'](p,0,False)
        for side in ['right','left']:
            p[side+'_thigh']['rotation'][0]-=45*tuck
            p[side+'_shin']['rotation'][0]+=75*tuck
            p[side+'_foot']['rotation'][0]-=30*tuck
        start=h['performance']('slam',1.3,slam)
        for bone,channels in p.items():
            for kind,value in channels.items():
                channels[kind]=start.get(bone,{}).get(kind,np.zeros(3))*(1-q)+value*q
        return p
    # The descent ends with feet extended; then knees, torso, shoulders and hands absorb impact.
    ts=[0,.10,.25,.5,1.0,1.6,2.2]
    crouch=float(ease(t,ts,[0,6,5,4,2,.5,0]))
    pos(p,'pelvis',[0,-crouch,-.15*crouch])
    rot(p,'torso',[float(ease(t,ts,[-10,18,14,10,5,1,0])),0,0])
    rot(p,'head',[float(ease(t,ts,[10,-10,-8,-5,-2,0,0])),0,0])
    for side,sign in [('right',-1),('left',1)]:
        rot(p,side+'_upper_arm',[float(ease(t,ts,[-105,-22,-16,-12,-8,-4,0])),0,-sign*float(ease(t,ts,[18,7,6,5,3,1,0]))])
        rot(p,side+'_forearm',[float(ease(t,ts,[-50,-6,-4,-3,-2,-1,0])),0,0])
        rot(p,side+'_hand',[float(ease(t,ts,[-10,9,6,4,2,0,0])),0,0])
        pos(p,side+'_shoulder',[0,float(ease(t,ts,[4,1,1,1.5,2,1,0])),0])
        h['fingers'](p,side,float(ease(t,ts,[45,55,48,38,20,8,0])),0)
        pos(p,f'chest_plate_{sign}',[sign*float(ease(t,ts,[1.5,2.2,1.5,1,.5,.2,0])),0,0])
    blend=min(1,t/.1)
    start=pose('leap_air',1.8)
    for bone,channels in p.items():
        for kind,value in channels.items():
            channels[kind]=start.get(bone,{}).get(kind,np.zeros(3))*(1-blend)+value*blend
    h['legs'](p,0,False)
    return lift_hands(p)

def build():
    data=json.loads((BASE/'fractured_guardian.animation.json').read_text())
    for name,duration in [('leap_launch',.7),('leap_air',1.8),('leap_land',2.2)]:
        clip={'loop':False,'animation_length':duration,'bones':{}}
        for i in range(round(duration*80)+1):
            t=round(i/80,4)
            for bone,channels in pose(name,t/1.8 if name=='leap_air' else t).items():
                for kind,value in channels.items():
                    clip['bones'].setdefault(bone,{}).setdefault(kind,{})[str(t)]=[round(float(x),5) for x in value]
        for channels in clip['bones'].values():
            for kind,keys in list(channels.items()):channels[kind]=h['reduce_keys'](keys,.0015)
        data['animations']['animation.fractured_guardian.'+name]=clip
    return data

if __name__=='__main__':
    data=build()
    (HERE/'fractured_guardian.animation.json').write_text(json.dumps(data,indent=2)+'\n')
    project=h['legacy']['blockbench'](data);project['name']='Fractured Guardian — Double shockwave leap 05'
    (HERE/'fractured_guardian.bbmodel').write_text(json.dumps(project,indent=2)+'\n')
    base=json.loads((BASE/'fractured_guardian.animation.json').read_text())
    assert all(data['animations'][name]==clip for name,clip in base['animations'].items())
    print('Added launch, airborne and landing clips; all six V4 animations preserved exactly.')
