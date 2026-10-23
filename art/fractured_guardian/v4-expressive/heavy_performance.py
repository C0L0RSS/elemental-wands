"""Stone brawler poses, in one continuous Euler branch; runtime clocks stay fixed.

No decomposed matrices or equivalent Euler representations enter these tracks.
Leg IK targets stationary soles; head compensation preserves the beam socket.
"""
import numpy as np

REWORKED = ('slam', 'awaken', 'beam')


def pose(name, t, rig):
    ease, rot, pos = rig['ease'], rig['rot'], rig['pos']
    p = {}

    def curve(times, values, delay=0):
        return float(ease(max(0, t-delay), times, values))

    if name == 'slam':
        # Settle first, haul the fists up, bear their weight at the exact hold
        # socket, then drop the body into the blow. No elastic impact oscillation.
        ts = [0, .32, .72, 1.075, 1.17, 1.30, 1.40, 1.70, 2.14, 2.8]
        pos(p, 'pelvis', [0, curve(ts, [0,-3.4,-2,-.6,-.6,-5.8,-6,-5.8,-3.5,0]),
                         curve(ts, [0,1.6,1.2,.7,.7,-1.8,-2,-2,-.9,0])])
        rot(p, 'torso', [curve(ts, [0,7,-5,-14,-14,18,20,19,9,0]), 0, 0])
        rot(p, 'head', [curve([0,.4,.85,1.075,1.20,1.38,1.72,2.3,2.8],
                                    [0,-2,4,10,10,-9,-10,-3,0]),0,0])
        for side, sign in [('right',-1),('left',1)]:
            # A small difference in the early haul reads as mass, but both fists
            # reach the same apex and strike together at the server impact.
            haul = .045 if side == 'left' else 0
            arm_times = [0,.32+haul,.78+haul,1.075,1.17,1.30,1.70,2.18,2.8]
            rot(p, side+'_upper_arm', [curve(arm_times,[0,-16,-88,-132,-132,-7,-7,-15,0]),
                                      0, -sign*curve(ts,[0,7,13,13,13,8,8,8,5,0])])
            rot(p, side+'_forearm', [curve([0,.4,.85,1.075,1.19,1.30,1.70,2.22,2.8],
                                                   [0,-24,-34,-29,-29,12,12,-6,0]),0,0])
            rot(p, side+'_hand', [curve([0,.48,1.075,1.2,1.3,1.42,1.75,2.3,2.8],
                                                [0,9,-8,-8,3,7,5,-2,0]),0,0])
            pos(p, side+'_shoulder', [0,curve(ts,[.8,1.4,3.5,4,4,1.2,.8,.8,1.5,.8]),0])
            grip=curve([0,.25,.68,1.075,1.75,2.3,2.8],[0,12,58,64,64,32,0])
            rig['fingers'](p,side,grip,0)
            # Loose plates react after the stone torso, then settle once.
            lag=curve([0,1.17,1.30,1.44,1.65,2.1,2.8],[0,0,.3,1,.6,.15,0])
            pos(p,f'chest_plate_{sign}',[sign*.7*lag,-.45*lag,-.8*lag])
            rot(p,f'chest_plate_{sign}',[2*lag,0,-sign*1.5*lag])
    elif name == 'awaken':
        # Dormant weight, a bracing compression on the existing 1.65s sound cue,
        # then one sustained rise. The approved cinematic intro is independent.
        ts=[0,.6,1.3,1.65,1.95,2.8,3.45,4.2]
        pos(p,'pelvis',[0,curve(ts,[-4,-4,-4.5,-5.3,-5,-2,-.4,0]),
                       curve(ts,[1.2,1.2,1.5,1.8,1.6,.7,.1,0])])
        rot(p,'torso',[curve(ts,[12,12,10,13,11,1,-3,0]),0,0])
        rot(p,'head',[curve([0,.6,1.3,1.65,2.3,3.1,3.65,4.2],
                                  [18,18,9,7,2,-4,-2,0]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            lag=.10 if side=='left' else 0
            arm=curve([0,.7,1.65,2.15,3,3.5,4.1],[0,0,9,12,18,10,0],lag)
            rot(p,side+'_upper_arm',[-arm,0,-sign*arm*.65])
            rot(p,side+'_forearm',[-curve([0,.8,1.65,2.5,3.3,4.1],[0,0,19,23,12,0],lag),0,0])
            rot(p,side+'_hand',[curve([0,1.1,1.8,2.7,3.6,4.1],[0,0,7,10,3,0],lag),0,0])
            pos(p,side+'_shoulder',[0,curve(ts,[2,2,2.5,3,3.1,2.8,1.4,.8]),0])
            grip=curve([0,.6,1.4,1.65,2.6,3.3,4.1],[0,0,28,40,40,15,0],lag)
            rig['fingers'](p,side,grip,0)
            settle=curve([0,1.65,2,2.8,3.4,4.2],[0,0,.4,1,.5,0])
            pos(p,f'chest_plate_{sign}',[sign*.7*settle,.3*settle,-.4*settle])
    else:  # Beam: planted charge, single recoil, sustained strain, earned release.
        ts=[0,.45,1.2,1.6,1.72,2.2,2.45,2.95,3.6]
        load=curve(ts,[0,.25,.9,1,1,1,.95,.55,0])
        recoil=curve(ts,[0,0,0,0,1,.85,.55,.12,0])
        pos(p,'pelvis',[0,-4*load-1.6*recoil,1.5*load+.5*recoil])
        rot(p,'torso',[-10*load-5*recoil,0,0])
        pos(p,'torso',[0,1.8*load,.8*recoil])
        rot(p,'jaw',[curve([0,.5,1.2,1.6,2.2,2.5,3,3.6],[0,4,27,42,42,35,15,0]),0,0])
        for side,sign in [('right',-1),('left',1)]:
            arm=curve([0,.55,1.25,1.6,1.8,2.2,2.6,3.1,3.6],[0,.15,.85,1,1,1,.92,.45,0])
            pos(p,side+'_shoulder',[sign*.6*load,.8+2.4*load,.7*load])
            rot(p,side+'_shoulder',[-3*load,0,-sign*4*load])
            rot(p,side+'_upper_arm',[-25*arm-8*recoil,0,-sign*38*arm])
            rot(p,side+'_forearm',[-27*arm-6*recoil,sign*3*arm,0])
            rot(p,side+'_hand',[7*arm+5*recoil,sign*5*arm,0])
            fingers=curve([0,.6,1.2,1.6,1.83,2.2,2.6,3.15,3.6],[0,3,9,13,18,16,12,5,0])
            rig['fingers'](p,side,fingers,5*arm)
            pos(p,f'chest_plate_{sign}',[sign*3.8*load,1.1*load,-1.8*load])
            rot(p,f'chest_plate_{sign}',[0,-sign*10*load,-sign*3*load])
        pos(p,'core',[0,.4*load,-1.2*load])
        parent=rig['transforms'](p)['torso']; hp=np.array(rig['bones']['head']['pivot'])
        pos(p,'head',np.linalg.solve(parent[:3,:3],hp-parent[:3,3])-hp)
        rot(p,'head',[-p['torso']['rotation'][0],0,0])

    rig['legs'](p,t,False)
    # Keep knuckles above the floor without moving either support foot.
    for side in ['right','left']:
        mats=rig['transforms'](p); low=999
        for c in rig['mesh']:
            if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ['thigh','shin','foot']):
                m=mats[c['bone']]; v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3]
                low=min(low,float(v[:,1].min()))
        if low<.12:
            p[side+'_shoulder']['position'][1]+=(.12-low)/mats['torso'][1,1]
    return p
