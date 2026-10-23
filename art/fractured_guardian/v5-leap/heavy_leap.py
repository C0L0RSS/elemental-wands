"""Fixed-clock leap: loaded launch, restrained flight and a two-stage landing.

All times are seconds. Entity motion remains GuardianLeapRules' 11-block arc.
The launch/air and air/land endpoints are shared poses, not approximations.
"""
import numpy as np


def pose(name,t,rig):
    p={};rot,pos,ease=rig['rot'],rig['pos'],rig['ease']
    def curve(times,values):return float(ease(t,times,values))
    if name=='leap_launch':
        ts=[0,.18,.42,.55,.7]
        pos(p,'pelvis',[0,curve(ts,[0,-3.5,-1.5,-.8,-5.6]),curve(ts,[0,1.2,.6,.4,-1.6])])
        rot(p,'torso',[curve(ts,[0,7,-8,-12,16]),0,0])
        rot(p,'head',[curve(ts,[0,-2,4,8,-6]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,side+'_shoulder',[0,curve(ts,[.8,1.8,3.4,3.8,1.2]),0])
            rot(p,side+'_upper_arm',[curve(ts,[0,-20,-105,-122,-7]),0,-sign*curve(ts,[0,8,12,12,8])])
            rot(p,side+'_forearm',[curve(ts,[0,-20,-32,-30,12]),0,0])
            rot(p,side+'_hand',[curve(ts,[0,5,-8,-8,3]),0,0])
            rig['fingers'](p,side,curve(ts,[0,20,52,58,58]),0)
            pos(p,f'chest_plate_{sign}',[sign*curve(ts,[0,0,0,0,.2]),0,0])
    elif name=='leap_air':
        ts=[0,.32,.75,1.05,1.45,1.8]
        pos(p,'pelvis',[0,curve(ts,[-5.6,-1,0,0,0,0]),curve(ts,[-1.6,0,.4,.4,0,0])])
        rot(p,'torso',[curve(ts,[16,4,-6,-6,-3,0]),0,0])
        rot(p,'head',[curve(ts,[-6,-3,3,3,1,0]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,side+'_shoulder',[0,curve(ts,[1.2,3.4,4,4,3.5,3]),0])
            rot(p,side+'_upper_arm',[curve(ts,[-7,-55,-110,-110,-92,-65]),0,-sign*curve(ts,[8,14,16,16,12,10])])
            rot(p,side+'_forearm',[curve(ts,[12,-38,-48,-48,-35,-20]),0,0])
            rot(p,side+'_hand',[curve(ts,[3,-4,-10,-10,-8,-6]),0,0])
            rig['fingers'](p,side,curve(ts,[58,52,48,48,45,45]),0)
            pos(p,f'chest_plate_{sign}',[sign*curve(ts,[.2,.8,1,1,.6,.3]),0,0])
    else:
        # Feet absorb the arrival first. Fists follow at +0.30s, the existing
        # second-wave clock; then the body stays low before pushing upright.
        ts=[0,.12,.3,.45,.8,1.35,2.2]
        pos(p,'pelvis',[0,curve(ts,[0,-5.4,-5.8,-5.9,-5.6,-3,0]),curve(ts,[0,-1.3,-1.7,-1.7,-1.4,-.5,0])])
        rot(p,'torso',[curve(ts,[0,14,19,20,18,7,0]),0,0])
        rot(p,'head',[curve([0,.18,.38,.8,1.5,2.2],[0,-6,-10,-9,-3,0]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            pos(p,side+'_shoulder',[0,curve(ts,[3,2,1.1,.8,.8,1.6,.8]),0])
            rot(p,side+'_upper_arm',[curve(ts,[-65,-53,-7,-7,-7,-14,0]),0,-sign*curve(ts,[10,10,8,8,8,4,0])])
            rot(p,side+'_forearm',[curve(ts,[-20,-27,12,12,12,-5,0]),0,0])
            rot(p,side+'_hand',[curve(ts,[-6,-8,3,7,5,-2,0]),0,0])
            rig['fingers'](p,side,curve(ts,[45,52,60,60,60,28,0]),0)
            lag=curve([0,.12,.3,.45,.8,1.5,2.2],[.3,.3,.6,1,.7,.15,0])
            pos(p,f'chest_plate_{sign}',[sign*lag,0,0])
    rig['legs'](p,0,False)
    if name=='leap_air':
        tuck=curve([0,.3,.65,1.05,1.55,1.8],[0,.65,1,1,0,0])
        for side in ('right','left'):
            p[side+'_thigh']['rotation'][0]-=38*tuck
            p[side+'_shin']['rotation'][0]+=67*tuck
            p[side+'_foot']['rotation'][0]-=29*tuck
        # Launch floor correction is part of its actual endpoint. Blend any
        # resulting shoulder offset out over the first 0.20s of flight.
        start=pose('leap_launch',.7,rig)
        q=float(ease(t,[0,.2],[0,1]))
        for side in ('right','left'):
            raw=1.2
            p[side+'_shoulder']['position'][1]+=(start[side+'_shoulder']['position'][1]-raw)*(1-q)
        return p
    for side in ('right','left'):
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ('thigh','shin','foot')):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p
