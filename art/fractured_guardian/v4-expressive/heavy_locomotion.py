"""Quiet stone weight and a loaded gait; retain the V4 foot trajectory exactly."""
import math
import numpy as np


def pose(name,t,rig):
    p={};ease,rot,pos=rig['ease'],rig['rot'],rig['pos']
    def curve(times,values,time=t):return float(ease(time,times,values))
    if name=='idle':
        # One deliberate adjustment, with long rests. No breathing chest or
        # constantly wriggling fingers. All local Euler channels close the loop.
        ts=[0,.9,2.2,3.2,4.8,6]
        load=curve(ts,[0,0,1,1,0,0])
        lag=curve([0,1.1,2.5,3.45,5.1,6],[0,0,1,1,0,0])
        pos(p,'pelvis',[0,-1.4-1.0*load,.4+.7*load])
        rot(p,'torso',[4+3*load,0,0])
        rot(p,'head',[-3-2*lag,curve([0,1.2,2.8,3.8,5.4,6],[0,0,-4,-4,0,0]),0])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,side+'_shoulder',[0,2.2,0])
            rot(p,side+'_upper_arm',[-3-2*lag,0,-sign*3])
            rot(p,side+'_forearm',[-6-lag,0,0])
            rot(p,side+'_hand',[2+lag,0,0])
            rig['fingers'](p,side,12,0)
            pos(p,f'chest_plate_{sign}',[0,-.3*lag,0])
    else:
        # Heel contacts remain at 0/1s. Retain the entire original sole path,
        # including its 10-unit stroke, 1.2s stance and 0.8s return per foot.
        step=t%1
        compression=curve([0,.12,.32,.7,1],[.65,1,.9,0,.65],step)
        pos(p,'pelvis',[0,-1.8-1.7*compression,0])
        a=t*math.pi
        rot(p,'torso',[5+2*compression,3*math.sin(a),1.6*math.cos(a)])
        rot(p,'head',[-3-compression,-2.5*math.sin(a-.16),-math.cos(a)])
        for side,sign in [('right',-1),('left',1)]:
            phase=a+(0 if side=='right' else math.pi)
            pos(p,side+'_shoulder',[0,2.8,0])
            rot(p,side+'_upper_arm',[-9-10*math.cos(phase-.28),0,-sign*5])
            rot(p,side+'_forearm',[-15+3*math.cos(phase-.48),0,0])
            rot(p,side+'_hand',[3+2*math.sin(phase-.65),0,0])
            rig['fingers'](p,side,18,0)
            # Loose plates settle just after each load transfers to a foot.
            lag=curve([0,.12,.3,.55,1],[0,.3,1,0,0],step)
            pos(p,f'chest_plate_{sign}',[0,-.35*lag,0])
    rig['legs'](p,t,name=='walk')
    for side in ('right','left'):
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ('thigh','shin','foot')):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p
