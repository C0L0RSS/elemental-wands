"""Planted fan and core-pulse performances; no changes to server sockets or clocks."""
import numpy as np


def pose(name,t,rig):
    p={};rot,pos,ease=rig['rot'],rig['pos'],rig['ease']
    def curve(times,values,delay=0):
        return float(ease(max(0,t-delay),times,values))
    if name=='fan':
        ts=[0,.4,.75,.9,1.3,1.7,1.85,2.4]
        load=curve(ts,[0,.45,1,1,1,1,.9,0])
        pos(p,'pelvis',[0,curve(ts,[0,-2.7,-4,-4.2,-4.6,-5,-4.8,0]),
                       curve(ts,[0,1.1,1.5,.9,0,-.9,-1,0])])
        # The body commits forward over all three releases rather than winding
        # left/right three times. Individual hands carry the release accents.
        rot(p,'torso',[curve(ts,[0,4,-7,-4,1,8,9,0]),0,0])
        rot(p,'head',[curve(ts,[0,-2,5,4,1,-4,-5,0]),0,0])
        pulses=[]
        for release in (.9,1.3,1.7):
            pulses.append(curve([0,release-.10,release,release+.10,release+.29,2.4],
                                [0,0,1,.9,0,0]))
        for side,sign in [('right',-1),('left',1)]:
            accent=(pulses[0]*(1 if side=='right' else .45)+
                    pulses[1]*(1 if side=='left' else .45)+pulses[2])
            pos(p,side+'_shoulder',[0,.8+2.2*load,0])
            rot(p,side+'_shoulder',[0,0,-sign*6*load])
            rot(p,side+'_upper_arm',[-58*load+12*accent,0,-sign*24*load])
            rot(p,side+'_forearm',[-38*load+22*accent,0,0])
            rot(p,side+'_hand',[-9*load+13*accent,0,0])
            rig['fingers'](p,side,20*load-14*accent,5*load)
    else:
        ts=[0,.5,1.3,2.15,2.4,2.5,2.62,2.95,3.8]
        plant=curve(ts,[0,1,1,1,1,1,1,.9,0])
        draw=curve(ts,[0,0,.4,.9,1,.3,0,0,0])
        pos(p,'pelvis',[0,curve(ts,[0,-4,-4.6,-5,-5,-5.8,-6,-5.8,0]),
                       curve(ts,[0,1,1.3,1.6,1.6,-1.7,-2,-1.8,0])])
        rot(p,'torso',[curve(ts,[0,-8,-15,-23,-25,18,21,19,0]),0,0])
        rot(p,'head',[curve([0,.5,1.3,2.4,2.55,2.7,3.05,3.8],
                                  [0,-4,-9,-13,5,8,7,0]),0,0])
        rot(p,'jaw',[curve([0,.5,1.3,2.4,2.5,2.8,3.3,3.8],
                                 [0,8,16,28,36,32,14,0]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            # The shoulders lag the torso impulse, then carry the load down.
            arm=curve([0,.5,1.3,2.4,2.55,2.68,3.05,3.8],
                           [0,18,26,34,-24,-30,-25,0])
            spread=curve([0,.5,1.3,2.4,2.55,2.7,3.15,3.8],
                              [0,28,40,48,43,34,25,0])
            pos(p,side+'_shoulder',[0,.8+2*plant,0])
            rot(p,side+'_shoulder',[0,0,-sign*6*plant])
            rot(p,side+'_upper_arm',[arm,sign*4*draw,-sign*spread])
            rot(p,side+'_forearm',[-18*plant-8*draw,0,0])
            rot(p,side+'_hand',[curve([0,.5,2.4,2.6,2.8,3.8],[0,8,14,-15,-10,0]),0,0])
            grip=curve([0,.5,2.4,2.5,2.7,3.05,3.8],[0,8,4,4,38,32,0])
            rig['fingers'](p,side,grip,9*draw)
    rig['legs'](p,t,False)
    for side in ('right','left'):
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ('thigh','shin','foot')):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p
