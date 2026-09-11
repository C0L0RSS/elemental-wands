"""Author a separate guard stagger; established attack animations are untouched."""
import json, math, runpy
import numpy as np
from pathlib import Path
HERE=Path(__file__).resolve().parent
DURATION=9.7
rig=runpy.run_path(str(HERE.parent/"v4-expressive/build_animations.py"))

def openness(t):
    u=max(0,min(1,t/1.2 if t<1.2 else 1 if t<8.2 else (9.7-t)/1.5))
    return u*u*(3-2*u)

def pose(t, include_shell=False):
    a=openness(t)
    # A backwards jolt settles into an open-chested arch, exposing the suspended core.
    recoil=math.sin(min(1,t/.65)*math.pi)*16 if t<.65 else 0
    breath=math.sin((t-1.2)*3.2)*a
    slump=-30*a-2*breath-recoil
    p={'pelvis':{'position':np.array([0,-6*a,-a])},
       'torso':{'rotation':[slump,1.5*breath,2*a]},
       'head':{'rotation':[-12*a,0,3*a+1.5*breath]},
       'jaw':{'rotation':[20*a,0,0]}}
    for side,sign in [('right',-1),('left',1)]:
        p[side+'_shoulder']={'position':np.array([0,-a,.5*a]),'rotation':[0,0,-sign*7*a]}
        p[side+'_upper_arm']={'rotation':[25*a+breath,0,-sign*14*a]}
        p[side+'_forearm']={'rotation':[-12*a-2*breath,0,sign*3*a]}
        p[side+'_hand']={'rotation':[12*a,0,sign*5*a]}
        rig['fingers'](p,side,18*a,2*a,breath)
    rig['legs'](p,t,False)
    # Long hanging fingers remain above the floor throughout collapse and recovery.
    for side in ['right','left']:
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ['thigh','shin','foot']):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.8:p[side+'_shoulder']['position'][1]+=(.8-low)/mats['torso'][1,1]
    if include_shell:
        p.update({'chest_plate_-1':{'rotation':[0,68*a,0],'position':[-3*a,0,-2*a]},
                  'chest_plate_1':{'rotation':[0,-68*a,0],'position':[3*a,0,-2*a]},
                  'core':{'rotation':[0,math.sin(t*17)*8*a,math.sin(t*23)*7*a],
                          'position':[math.sin(t*31)*.5*a,math.sin(t*21)*.4*a,-12*a],
                          'scale':[1+.65*a,1+.3*a,1+1.4*a]}})
    return p

def clip(shell=False):
    bones={}
    for i in range(195):
        t=round(i*.05,3)
        for b,channels in pose(t,shell).items():
            for name,value in channels.items():
                bones.setdefault(b,{}).setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
    return {'loop':False,'animation_length':DURATION,'bones':bones}

if __name__=='__main__':
    (HERE/'guard.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':{'animation.fractured_guardian.guard_break':clip()}},indent=2)+'\n')
    (HERE/'preview.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':{'animation.fractured_guardian.guard_break':clip(True)}},indent=2)+'\n')
