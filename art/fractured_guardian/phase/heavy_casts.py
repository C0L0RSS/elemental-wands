"""Planted fan, core-pulse, sweep and scar performances; no changes to server sockets or clocks."""
import numpy as np


def track(t,keys):
    """Keys are (time,value[,shape]); shape is how the segment arriving at that key moves:
    'smooth' (eased at both ends), 'in' (accelerates into the key, for a blow that lands hard),
    'out' (leaves the previous key at speed and settles) or 'linear'."""
    if t<=keys[0][0]:return float(keys[0][1])
    for a,b in zip(keys,keys[1:]):
        if t<=b[0]:
            q=(t-a[0])/(b[0]-a[0]);shape=b[2] if len(b)>2 else 'smooth'
            if shape=='smooth':q=q*q*(3-2*q)
            elif shape=='in':q=q*q
            elif shape=='out':q=1-(1-q)*(1-q)
            return float(a[1]+(b[1]-a[1])*q)
    return float(keys[-1][1])


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
    elif name=='core_pulse':
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
    elif name=='sweep':
        sweep(p,t,rig)
    elif name=='scar':
        scar(p,t,rig)
    else:
        raise ValueError(name)
    rig['legs'](p,t,False)
    for side in ('right','left'):
        mats=rig['transforms'](p);low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ('thigh','shin','foot')):
                m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p


def level_arm(p,rig,side,heading,lift):
    """Hold an upper arm out from its shoulder: heading is degrees round from the torso's front
    toward that arm's own side (90 = straight out, 180 = straight back); lift 90 is level."""
    sign=-1 if side=='right' else 1
    rig['rot'](p,side+'_upper_arm',[heading-90,0,-sign*lift])


def sweep(p,t,rig):
    # Turning backhand for players beside or behind it. Server hit: tick 30 (1.5 s).
    # The torso turns like a turret on the planted pelvis; the right arm's world heading is
    # torso yaw + arm heading: 50 deg right of front at 1.4 s, its right side at 1.45 s,
    # straight behind at 1.5 s, 60 deg left of front at 1.6 s.
    rot,pos=rig['rot'],rig['pos']
    yaw=track(t,[(0,0),(.55,-16),(1.05,-34),(1.4,-38),(1.45,-2,'in'),(1.5,78,'linear'),(1.6,158,'out'),
                 (1.78,164),(2.25,138),(2.8,40),(3.2,0)])
    heading=track(t,[(0,90),(1.4,88),(1.45,114,'in'),(1.5,126,'linear'),(1.6,162,'out'),
                     (1.8,150),(2.3,128),(2.8,100),(3.2,90)])
    lift=track(t,[(0,0),(.45,30),(1.0,80),(1.4,80),(1.45,74),(1.5,70),(1.6,62),(1.8,64),(2.3,58),(2.8,18),(3.2,0)])
    fold=track(t,[(0,0),(.5,-55),(1.0,-122),(1.4,-126),(1.45,-24,'in'),(1.5,-10,'linear'),(1.6,-6,'out'),
                  (1.8,-14),(2.3,-30),(2.8,-14),(3.2,0)])
    pos(p,'pelvis',[0,track(t,[(0,0),(.6,-2.6),(1.05,-4),(1.4,-4.4),(1.5,-5.4),(1.65,-5.8),(1.85,-5.6),
                               (2.4,-3.8),(2.9,-1),(3.2,0)]),
                   track(t,[(0,0),(1.0,.8),(1.4,.9),(1.6,-.6),(2.2,-.5),(3.2,0)])])
    lean=track(t,[(0,0),(1.0,6),(1.4,7),(1.6,11),(1.85,12),(2.5,5),(3.2,0)])
    roll=track(t,[(0,0),(1.0,4),(1.4,5),(1.5,-6,'out'),(1.7,-7),(2.4,-2),(3.2,0)])
    rot(p,'torso',[lean,yaw,roll])
    # Looks back over its right shoulder from the start; leads into the turn, then lags home.
    look=track(t,[(0,0),(.35,0),(.8,96),(1.1,118),(1.4,122),(1.45,104,'in'),(1.5,62,'linear'),(1.6,12,'out'),
                  (1.8,-6),(2.4,-14),(2.9,-4),(3.2,0)])
    rot(p,'head',[track(t,[(0,0),(.8,6),(1.4,7),(1.6,-4),(2.2,-2),(3.2,0)]),look,0])
    rot(p,'jaw',[track(t,[(0,0),(1.3,4),(1.5,22),(1.75,20),(2.4,6),(3.2,0)]),0,0])
    level_arm(p,rig,'right',heading,lift)
    rot(p,'right_forearm',[fold,0,0])
    rot(p,'right_hand',[track(t,[(0,0),(1.0,-14),(1.4,-16),(1.5,6),(1.8,4),(3.2,0)]),0,0])
    pos(p,'right_shoulder',[0,track(t,[(0,.8),(1.0,2.4),(1.4,2.6),(1.6,1.4),(2.4,1.4),(3.2,.8)]),0])
    rig['fingers'](p,'right',track(t,[(0,0),(.6,40),(1.0,58),(1.6,62),(2.3,40),(3.2,0)]),0)
    # The left arm stays tucked against its flank and is dragged round by the torso.
    rot(p,'left_upper_arm',[track(t,[(0,0),(1.0,-22),(1.4,-24),(1.55,-10),(1.8,4),(2.5,-6),(3.2,0)]),0,
                            -track(t,[(0,0),(1.0,10),(1.4,12),(1.55,22),(1.8,24),(2.5,10),(3.2,0)])])
    rot(p,'left_forearm',[track(t,[(0,0),(1.0,-48),(1.4,-52),(1.6,-30),(2.4,-22),(3.2,0)]),0,0])
    pos(p,'left_shoulder',[0,track(t,[(0,.8),(1.0,1.8),(1.6,1.4),(3.2,.8)]),0])
    rig['fingers'](p,'left',track(t,[(0,0),(1.0,32),(1.6,24),(2.4,12),(3.2,0)]),2)
    for sign in (-1,1):
        lag=track(t,[(0,0),(1.45,0),(1.6,1),(1.85,.6),(2.4,.15),(3.2,0)])
        pos(p,f'chest_plate_{sign}',[sign*.6*lag,-.3*lag,-.7*lag])


def scar(p,t,rig):
    # Hands meet the floor at tick 20 (1.0 s); the walls are torn up at tick 30 (1.5 s).
    # Unlike the slam (fists hauled together overhead while crouching) it rears up with open hands spread wide.
    rot,pos=rig['rot'],rig['pos']
    pos(p,'pelvis',[0,track(t,[(0,0),(.4,-.6),(.75,-.8),(1.0,-5.5,'in'),(1.5,-5.8),(1.62,-4.4,'out'),
                               (1.85,-4.2),(2.2,-1.8),(2.5,0)]),
                   track(t,[(0,0),(.75,1.2),(1.0,-1.6,'in'),(1.5,-1.8),(1.62,.4,'out'),(2.5,0)])])
    rot(p,'torso',[track(t,[(0,0),(.4,-11),(.75,-17),(1.0,32,'in'),(1.5,33),(1.62,-9,'out'),(1.85,-7),
                            (2.2,3),(2.5,0)]),0,0])
    rot(p,'head',[track(t,[(0,0),(.4,-6),(.75,-9),(1.0,12,'in'),(1.5,18),(1.62,-12,'out'),(1.9,-10),
                           (2.25,-2),(2.5,0)]),0,0])
    rot(p,'jaw',[track(t,[(0,0),(.5,10),(.8,14),(1.0,6),(1.5,8),(1.62,30,'out'),(1.95,24),(2.5,0)]),0,0])
    for side,sign in [('right',-1),('left',1)]:
        # Wide and high, then out in front of and beside it at the floor, then wrenched up and out.
        pitch=track(t,[(0,0),(.4,-10),(.75,-16),(1.0,-37,'in'),(1.5,-38),(1.62,-22,'out'),(1.9,-20),
                       (2.25,-14),(2.5,0)])
        spread=track(t,[(0,0),(.4,70),(.75,104),(1.0,38,'in'),(1.5,37),(1.62,118,'out'),(1.9,110),
                        (2.25,22),(2.5,0)])
        rot(p,side+'_upper_arm',[pitch,0,-sign*spread])
        rot(p,side+'_forearm',[track(t,[(0,0),(.4,-14),(.75,-18),(1.0,-6,'in'),(1.5,-8),(1.62,-30,'out'),
                                        (1.9,-26),(2.25,-8),(2.5,0)]),0,0])
        rot(p,side+'_hand',[track(t,[(0,0),(.4,-12),(.75,-18),(1.0,16,'in'),(1.5,18),(1.62,-10,'out'),
                                     (2.25,-2),(2.5,0)]),0,0])
        pos(p,side+'_shoulder',[0,track(t,[(0,.8),(.4,2.6),(.75,3.4),(1.0,1.6,'in'),(1.5,1.4),
                                           (1.62,3.6,'out'),(1.9,3.2),(2.5,.8)]),0])
        # Splayed open as it rears, dug in on contact, clenched as it rips the walls up.
        grip=track(t,[(0,0),(.4,-10),(.75,-12),(1.0,30,'in'),(1.5,36),(1.62,62,'out'),(1.95,58),(2.5,0)])
        rig['fingers'](p,side,grip,track(t,[(0,0),(.4,14),(.75,16),(1.0,10),(1.5,8),(1.62,0),(2.5,0)]))
        lag=track(t,[(0,0),(1.0,0),(1.12,.7),(1.5,.4),(1.62,1),(1.9,.6),(2.5,0)])
        pos(p,f'chest_plate_{sign}',[sign*.6*lag,-.3*lag,-.6*lag])
