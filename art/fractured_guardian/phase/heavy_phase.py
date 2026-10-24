"""Shell rupture: strain until tick32, a single heave, then a heavy recovery."""
import numpy as np


def pose(t,rig):
    p={};rot,pos,ease=rig['rot'],rig['pos'],rig['ease']
    def curve(times,values):return float(ease(t,times,values))
    ts=[0,.65,1.25,1.48,1.6,1.78,2.2,2.7,3.2]
    pos(p,'pelvis',[0,curve(ts,[0,-3.5,-5,-5,-3.5,-3.8,-3.8,-2,0]),
                   curve(ts,[0,-.6,-1,-1,1.2,1.4,1.2,.5,0])])
    rot(p,'torso',[curve(ts,[0,10,18,18,-22,-23,-21,-8,0]),0,0])
    rot(p,'head',[curve([0,.65,1.48,1.6,1.73,2.2,2.7,3.2],
                              [0,5,9,0,-16,-16,-7,0]),0,0])
    rot(p,'jaw',[curve([0,.8,1.48,1.6,1.8,2.25,2.8,3.2],
                             [0,0,5,18,32,32,12,0]),0,0])
    for side,sign in [('right',-1),('left',1)]:
        pos(p,side+'_shoulder',[0,curve(ts,[.8,2,3,3,3.8,4,3.5,2,.8]),0])
        arm=curve([0,.65,1.25,1.48,1.7,1.88,2.2,2.8,3.2],[0,-12,-25,-25,30,32,30,12,0])
        spread=curve([0,.65,1.48,1.7,1.88,2.2,2.8,3.2],[0,4,7,33,35,34,16,0])
        rot(p,side+'_upper_arm',[arm,0,-sign*spread])
        rot(p,side+'_forearm',[curve(ts,[0,-20,-32,-32,-24,-12,-12,-8,0]),0,0])
        rot(p,side+'_hand',[curve([0,.65,1.48,1.7,1.9,2.4,3.2],[0,8,13,-8,-12,-9,0]),0,0])
        grip=curve([0,.65,1.25,1.48,1.6,1.85,2.4,3.2],[0,22,48,48,40,8,8,0])
        rig['fingers'](p,side,grip,curve([0,1.48,1.85,2.4,3.2],[0,0,8,8,0]))
    # The chest stays shut; only the core throbs, posed in code (FracturedGuardianModel).
    rig['legs'](p,t,False)
    for side in ('right','left'):
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ('thigh','shin','foot')):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p
