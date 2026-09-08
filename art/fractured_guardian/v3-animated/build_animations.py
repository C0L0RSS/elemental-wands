#!/usr/bin/env python3
"""Author Guardian keyframes and an editable Blockbench project; approved art stays intact."""
import copy
import json
import math
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent/'v2-textured'
PREFIX = 'animation.fractured_guardian.'


def channel(times, values):
    return {str(t): v for t, v in zip(times, values)}


def pose(bones, bone, times, values, kind='rotation'):
    bones.setdefault(bone, {})[kind] = channel(times, values)


def build():
    animations = {}
    def clip(name, length, loop=False):
        bones = {}
        animations[PREFIX+name] = {'loop': loop, 'animation_length': length, 'bones': bones}
        return bones

    # A held-together stone construct: quiet settling, not fleshy breathing.
    b = clip('idle', 6, True)
    ts = [0, 1.5, 3, 4.5, 6]
    pose(b, 'torso', ts, [[0,0,0],[-.45,.5,.25],[0,0,0],[.35,-.5,-.25],[0,0,0]])
    pose(b, 'head', ts, [[0,0,0],[.6,-1,0],[0,0,0],[-.4,1,0],[0,0,0]])
    for side, sign in [('right',-1),('left',1)]:
        pose(b, side+'_upper_arm', ts, [[0,0,0],[.7,0,sign*.3],[0,0,0],[-.4,0,-sign*.2],[0,0,0]])
        pose(b, side+'_forearm', ts, [[0,0,0],[-1,0,0],[0,0,0],[.5,0,0],[0,0,0]])
        # Slightly lift hanging hands to avoid knuckles dipping below the floor.
        pose(b, side+'_shoulder', ts, [[0,.8,0]]*5, 'position')

    # Feet follow a flat stance path and a raised return arc. Two-link IK keeps
    # thigh/shin joints connected and counter-rotates feet to keep soles level.
    b = clip('walk', 2, True)
    ts = [round(i/20, 2) for i in range(41)]
    for side, offset in [('right',0),('left',.5)]:
        thighs, shins, feet, arms, elbows = [], [], [], [], []
        l1, l2 = math.hypot(14,3), math.hypot(13,3)
        base1 = math.atan2(14,-3)
        base2 = math.atan2(13,3)-base1
        for t in ts:
            p = (t/2+offset)%1
            if p < .6:
                dz, lift = -4+8*p/.6, 0
            else:
                q = (p-.6)/.4
                dz, lift = 4-8*q, 3*math.sin(math.pi*q)**2
            z, down = dz, 27-lift
            a2 = -math.acos(max(-1,min(1,(z*z+down*down-l1*l1-l2*l2)/(2*l1*l2))))
            a1 = math.atan2(down,z)-math.atan2(l2*math.sin(a2),l1+l2*math.cos(a2))-base1
            a2 -= base2
            x, y = -math.degrees(a1), -math.degrees(a2)
            thighs.append([round(x,4),0,0]);shins.append([round(y,4),0,0]);feet.append([round(-x-y,4),0,0])
            arms.append([round(-4*math.cos(p*2*math.pi),3),0,0])
            elbows.append([-5,0,0])
        for name, values in [('thigh',thighs),('shin',shins),('foot',feet),('upper_arm',arms),('forearm',elbows)]:
            pose(b, side+'_'+name, ts, values)
        pose(b, side+'_shoulder', [0,2], [[0,1.5,0]]*2, 'position')
    pose(b, 'torso', ts, [[0,round(1.2*math.sin(t*math.pi),3),0] for t in ts])
    pose(b, 'head', ts, [[0,round(-1.2*math.sin(t*math.pi),3),0] for t in ts])

    b = clip('awaken', 4.2)
    ts = [0,.7,1.25,1.65,2.25,2.65,3.2,4.2]
    pose(b,'head',ts,[[16,0,0],[16,0,0],[10,-3,0],[5,0,0],[-5,0,0],[-3,0,0],[2,0,0],[0,0,0]])
    pose(b,'torso',ts,[[3,0,0],[3,0,0],[2,0,0],[0,0,0],[-3,0,0],[-2,0,0],[.8,0,0],[0,0,0]])
    for side, sign in [('right',-1),('left',1)]:
        pose(b,side+'_shoulder',ts,[[0,2,0],[0,2,0],[0,3,0],[0,3,0],[0,2,0],[0,1.5,0],[0,1,0],[0,.8,0]],'position')
        pose(b,side+'_upper_arm',ts,[[0,0,0],[0,0,0],[-3,0,sign*2],[-8,0,sign*3],[-15,0,sign*5],[-10,0,sign*3],[-3,0,0],[0,0,0]])
        pose(b,side+'_forearm',ts,[[0,0,0],[0,0,0],[-3,0,0],[-8,0,0],[-15,0,0],[-9,0,0],[-2,0,0],[0,0,0]])
        for finger in range(1,4):
            pose(b,f'{side}_finger_{finger}',ts,[[0,0,0],[0,0,0],[-6,0,0],[-18,0,0],[-35,0,0],[-20,0,0],[-5,0,0],[0,0,0]])

    # Distinct anticipation, fast downstroke, held impact, slow heavy recovery.
    b = clip('slam', 2.8)
    ts = [0,.4,.95,1.15,1.3,1.5,2,2.8]
    pose(b,'torso',ts,[[0,0,0],[-3,0,0],[-8,0,0],[-8,0,0],[8,0,0],[8,0,0],[3,0,0],[0,0,0]])
    pose(b,'head',ts,[[0,0,0],[3,0,0],[8,0,0],[8,0,0],[-5,0,0],[-5,0,0],[-2,0,0],[0,0,0]])
    for side, sign in [('right',-1),('left',1)]:
        pose(b,side+'_shoulder',ts,[[0,.8,0],[0,2,0],[0,3,0],[0,3,0],[0,2,0],[0,2,0],[0,2,0],[0,.8,0]],'position')
        pose(b,side+'_upper_arm',ts,[[0,0,0],[-28,0,sign*3],[-125,0,sign*4],[-125,0,sign*4],[-5,0,0],[-5,0,0],[-12,0,0],[0,0,0]])
        pose(b,side+'_forearm',ts,[[0,0,0],[-25,0,0],[-30,0,0],[-30,0,0],[12,0,0],[12,0,0],[0,0,0],[0,0,0]])
        for finger in range(1,4):
            pose(b,f'{side}_finger_{finger}',ts,[[0,0,0],[-45,0,0],[-65,0,0],[-65,0,0],[-65,0,0],[-65,0,0],[-30,0,0],[0,0,0]])
            pose(b,f'{side}_finger_{finger}_tip',ts,[[0,0,0],[-35,0,0],[-65,0,0],[-65,0,0],[-65,0,0],[-65,0,0],[-25,0,0],[0,0,0]])

    b = clip('throw', 3.6)
    ts=[0,.55,1,1.65,2,2.2,2.4,2.85,3.6]
    pose(b,'torso',ts,[[0,0,0],[5,-5,0],[5,-5,0],[-4,-12,0],[-5,-15,0],[3,12,0],[3,12,0],[1,5,0],[0,0,0]])
    pose(b,'head',ts,[[0,0,0],[8,5,0],[8,5,0],[2,12,0],[0,15,0],[-3,-12,0],[-3,-12,0],[0,-5,0],[0,0,0]])
    for side in ['right','left']:
        pose(b,side+'_shoulder',ts,[[0,5.5,0]]*8+[[0,.8,0]],'position')
    pose(b,'right_upper_arm',ts,[[0,0,0],[5,0,-4],[5,0,-4],[-145,-10,-10],[-155,-10,-10],[-55,5,0],[-50,5,0],[-15,0,0],[0,0,0]])
    pose(b,'right_forearm',ts,[[0,0,0],[0,0,0],[-8,0,0],[-30,0,0],[-40,0,0],[0,0,0],[0,0,0],[-5,0,0],[0,0,0]])
    pose(b,'left_upper_arm',ts,[[0,0,0],[-10,0,4],[-10,0,4],[-12,0,8],[-12,0,8],[-18,0,5],[-18,0,5],[-5,0,0],[0,0,0]])
    for finger in range(1,4):
        pose(b,f'right_finger_{finger}',ts,[[0,0,0],[0,0,0],[-55,0,0],[-55,0,0],[-55,0,0],[5,0,0],[5,0,0],[0,0,0],[0,0,0]])
        pose(b,f'right_finger_{finger}_tip',ts,[[0,0,0],[0,0,0],[-45,0,0],[-45,0,0],[-45,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]])
    # Mouth opens during the short tell, holds through the pulse, then closes slowly.
    b = clip('beam', 2.05)
    ts = [0,.2,.55,.8,1.05,1.4,2.05]
    pose(b,'head',ts,[[0,0,0]]*7)
    pose(b,'jaw',ts,[[0,0,0],[12,0,0],[32,0,0],[36,0,0],[32,0,0],[16,0,0],[0,0,0]])
    # Neck and torso remain planted so the server muzzle and rendered jaw stay aligned.
    pose(b,'torso',ts,[[0,0,0]]*7)
    for side in ['right','left']:
        pose(b,side+'_shoulder',ts,[[0,.8,0]]*7,'position')
        pose(b,side+'_upper_arm',ts,[[0,0,0],[-2,0,0],[-4,0,0],[-4,0,0],[-3,0,0],[-1,0,0],[0,0,0]])
    return {'format_version':'1.8.0','animations':animations}


def blockbench(data):
    project = json.loads((SOURCE/'fractured_guardian.bbmodel').read_text())
    project['name'] = 'Fractured Guardian — Animation study 03'
    groups = {g['name']:g['uuid'] for g in project['groups']}
    def uid(s): return str(uuid.uuid5(uuid.NAMESPACE_URL,'elementalwands/guardian/'+s))
    project['animations']=[]
    for name, clip in data['animations'].items():
        anim={'uuid':uid(name),'name':name,'loop':'loop' if clip['loop'] else 'once','length':clip['animation_length'],'snapping':20,'animators':{}}
        for bone, channels in clip['bones'].items():
            frames=[]
            for ch, keys in channels.items():
                for t, value in keys.items():
                    # Match the installed GeckoLib Blockbench plugin's import/export inversion.
                    value = [-value[0], -value[1], value[2]] if ch == 'rotation' else [-value[0], value[1], value[2]]
                    frames.append({'uuid':uid(name+bone+ch+t),'channel':ch,'time':float(t),'interpolation':'linear','data_points':[dict(zip(['x','y','z'],map(str,value)))]})
            anim['animators'][groups[bone]]={'name':bone,'type':'bone','keyframes':frames}
        project['animations'].append(anim)
    return project

if __name__=='__main__':
    data=build()
    (HERE/'fractured_guardian.animation.json').write_text(json.dumps(data,indent=2)+'\n')
    (HERE/'fractured_guardian.bbmodel').write_text(json.dumps(blockbench(data),indent=2)+'\n')
    print('Authored 6 animation clips and editable Blockbench project.')
