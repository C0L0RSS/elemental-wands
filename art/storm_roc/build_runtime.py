#!/usr/bin/env python3
"""Install the approved Roc study and synchronized combat pose tracks.

The study stays untouched. Runtime poses and server sockets share the same sampled
transforms; even a client joining mid-attack draws the server's exact combat frame.
"""
import argparse, copy, json, math
from pathlib import Path
import numpy as np
from PIL import Image
from validate_review import sample, matrix
from poses import Pose, HEAD_SOCKET
from legs import solve_leg
from types import SimpleNamespace
from fold import rotation
from motion import Spring, envelope, keys, lag, limit_rate, noise, rotmat, shake, thin, twitch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
# The chunky block model and its pixel atlas ride the study's skeleton and clips unchanged.
MODEL = ROOT / 'art/storm_roc_mod/candidate'
SCALE = 1.25 / 16
KEY_FPS = 80  # max pose rate; ordinary motion is 40Hz, server sockets stay 20Hz.
# The brawl guard's talon kneads, (foot, lifts at, back down by): only then may a foot leave the rock.
KNEADS = (('right',2.65,3.3),('left',6.25,6.9))
# Its head twitches, (time, (pitch, yaw, roll)): held dead still, snapped, shivering as each lands.
# Two a few hundredths apart stutter. It faces the front for the hiss and before each loop ends.
GUARD_GLANCES = ((.3,(2,18,6)),(1.05,(-3,-14,-4)),(1.62,(-4,-20,-8)),(1.74,(-5,-31,-15)),
    (2.4,(0,4,0)),(3.15,(3,13,10)),(3.6,(0,0,0)),(5.35,(-2,-6,-24),4),(5.95,(4,22,8)),
    (6.5,(0,9,-3)),(7.2,(0,0,0)),(8.1,(-3,-16,-6)),(9.05,(0,0,0)))
# Thunder Bite: the bill snaps shut on the air at player height at 2.30 (tick 46); the standing
# bite's impact is the peck's strike pose, so it reaches as far. The server's aim follows its prey
# until 1.20. When its prey is out of reach, it plays bite_leap instead from the lock on, slower as
# befits its size: it pushes off at 2.15 (tick 43), the server carries it through the air, and it
# lands at 2.75 (tick 55) and bites at 2.80 (tick 56).
BITE_IMPACT, BITE_TAKEOFF, BITE_LANDING, BITE_LEAP_IMPACT = 2.3, 2.15, 2.75, 2.8
# Storm Sweep: the bill scythes the front at chest height from 1.25 to 1.65 (ticks 25-33); the
# crackle wave rolls out from its arc as it ends.
SWEEP_START, SWEEP_END = 1.25, 1.65
# Storm Scream: the blast leaves its open bill straight ahead at 1.60 (tick 32).
SCREAM_IMPACT = 1.6
# Thunderclap: it leaps off the rock at 0.85 (tick 17); the server throws it 9 blocks up to its apex
# at 1.75, it hangs there to 2.05, then smashes down and lands at 2.60 (tick 52).
WING_LIFT, WING_APEX, WING_DROP, WING_IMPACT = .85, 1.75, 2.05, 2.6
# Storm Spiral, an experimental Thunderclap after How to Train Your Dragon's flights: it launches at
# 0.85 into a corkscrew climb, barrel-rolling twice around its own spine to 2.15; levels out and
# stalls at the top to 2.55; dives talons first, flares and lands at 3.25. Parked (2026-10-19): it
# needs more work, so it is not in DEFINITIONS and doesn't ship; add it back to try it again.
SPIRAL_LIFT, SPIRAL_TOP, SPIRAL_DIVE, SPIRAL_IMPACT = .85, 2.15, 2.55, 3.25
# Raptor Strike: it stalks low and pushes off at 1.60 (tick 32); the server carries it flat and
# fast on a low arc, and its talons strike its prey at 2.60 (tick 52).
POUNCE_LAUNCH, POUNCE_IMPACT = 1.6, 2.6
FAST_KEYS = {'scream':[(1.45,2.2)], 'swing_left':[(1.2,1.95)], 'swing_right':[(1.2,1.95)], 'bite':[(1.95,2.7)], 'bite_leap':[(1.8,3.25)], 'wing':[(.45,1.1),(2.4,3.0)], 'wing_spiral':[(.5,3.6)], 'takeoff':[(1.1,1.2)],
    'pounce':[(1.3,1.75),(2.35,2.8)], 'call':[(1.9,2.1)],
    'hunched':[(.5,1.),(8.5,9.)]+[(g[0],g[0]+.35) for g in GUARD_GLANCES]}
# Block-space clearances at 1.25x; preserve the original model-space wing solve.
WING_REST, WING_SLAP = -.125, .7
# The idle and flight loops play their authored motion this much slower: a giant moves slowly.
SLOW = 1.5
DEFINITIONS = {'perched':(round(7.2*SLOW,3),True), 'call':(3,False), 'land':(.8,False),
    'flight':(round(4.8*SLOW,3),True), 'hunched':(9.6,True), 'dive':(3,False), 'stuck':(6,False),
    'bite':(3.9,False), 'bite_leap':(4.4,False), 'swing_left':(2.6,False), 'swing_right':(2.6,False),
    'scream':(2.9,False), 'wing':(4.0,False), 'pounce':(4.0,False),
    'stampede':(4.2,False), 'takeoff':(2.5,False), 'hop':(1.2,False)}

def packed(value):
    return (json.dumps(value, separators=(',', ':')) + '\n').encode()

def shape(bone):
    """Homogeneous model-space corners of a bone's cubes."""
    corners = []
    for c in bone.get('cubes', []):
        C = matrix({'pivot':c.get('pivot',[0,0,0])}, {'rotation':c.get('rotation',[0,0,0])}, 0)
        for dx in (0,1):
            for dy in (0,1):
                for dz in (0,1): corners.append(C@np.r_[np.array(c['origin'])+np.array(c['size'])*[dx,dy,dz],1])
    return np.array(corners).reshape(-1, 4)

def bill(beak):
    """The bill tip: the upper beak's frontmost point. The study's cube beak used its front face, 20% up."""
    if any('rotation' in c for c in beak['cubes']):
        # The centre of the frontmost edge: angled blocks reach it with a corner pair.
        p = shape(beak)[:,:3]; front = p[p[:,2] <= p[:,2].min() + .01]; return front.mean(axis=0)
    c = min(beak['cubes'], key=lambda c: c['origin'][2])
    return np.array(c['origin']) + np.array(c['size'])*[.5,.2,0]

# The Thunder Bite's windup crackles at the corners of its open mouth and along the sides of its
# lower jaw, beside the approved head's six anchors (which the head lock keeps unchanged).
MOUTH_SPARKS = [
    {'bone':'head','pivot_model_pixels':[7.5,162.5,-37.5],'sequence':'arc','size_blocks':.8,'phase':.05},
    {'bone':'head','pivot_model_pixels':[-7.5,162.5,-37.5],'sequence':'arc','size_blocks':.8,'phase':.27},
    {'bone':'lower_beak','pivot_model_pixels':[6.5,158.5,-45],'sequence':'spark','size_blocks':.55,'phase':.16},
    {'bone':'lower_beak','pivot_model_pixels':[-6.5,158.5,-45],'sequence':'spark','size_blocks':.55,'phase':.34}]

# The Storm Scream's breath crackles in its flared crest, at the tips of its plumes, so it reads
# apart from the bite's mouth crackles before it blasts.
CREST_SPARKS = [
    {'bone':'crest_plume_0_0','pivot_model_pixels':[-8.5,199.5,-2.7],'sequence':'arc','size_blocks':.7,'phase':.08},
    {'bone':'crest_plume_0_1','pivot_model_pixels':[-1,200.5,-8.3],'sequence':'arc','size_blocks':.75,'phase':.31},
    {'bone':'crest_plume_0_2','pivot_model_pixels':[6.6,198.4,-1.4],'sequence':'arc','size_blocks':.7,'phase':.19},
    {'bone':'crest_plume_1_0','pivot_model_pixels':[-6.5,196.6,10.6],'sequence':'spark','size_blocks':.5,'phase':.42},
    {'bone':'crest_plume_1_2','pivot_model_pixels':[6.5,196.6,10.6],'sequence':'spark','size_blocks':.5,'phase':.12},
    {'bone':'crest_plume_2_1','pivot_model_pixels':[1,191.6,18.5],'sequence':'arc','size_blocks':.6,'phase':.26}]

# The Thunderclap's wings crackle along their edges as they rise: the wrist's leading edge and the
# tips of the outer primaries, mirrored on the right wing.
WING_SPARKS = [{'bone':f'{side}_{bone}','pivot_model_pixels':[x*(1 if side=='left' else -1),y,z],
    'sequence':seq,'size_blocks':size,'phase':phase+(.2 if side=='right' else 0)}
    for side in ('left','right') for bone,(x,y,z),seq,size,phase in (
        ('wing_wrist',(171.8,90.5,-34.0),'arc',.9,.05),('primary_09',(228.2,88.1,13.7),'arc',1.0,.33),
        ('primary_06',(204.8,85.9,37.9),'spark',.7,.17),('primary_03',(177.2,80.5,60.1),'arc',.8,.41))]

# The Raptor Strike's talons crackle from its coil until they strike, at the tips of the three
# front toes of each foot.
TALON_SPARKS = [{'bone':f'{side}_toe_{i}_tip','pivot_model_pixels':[x*(1 if side=='left' else -1),1.2,z],
    'sequence':seq,'size_blocks':size,'phase':phase+(.2 if side=='right' else 0)}
    for side in ('left','right') for i,(x,z),seq,size,phase in (
        (0,(-.7,-51.6),'spark',.55,.07),(1,(14.2,-53.3),'arc',.75,.29),(2,(27.7,-51.6),'spark',.55,.18))]

def sparks():
    data = json.loads((HERE/'candidate/storm_roc_sparks.json').read_text())
    data['mouth_anchors'] = MOUTH_SPARKS
    data['crest_anchors'] = CREST_SPARKS
    data['wing_anchors'] = WING_SPARKS
    data['talon_anchors'] = TALON_SPARKS
    return (json.dumps(data, indent=2) + '\n').encode()

def glowing(texture, mask):
    """GeckoLib's glow layer draws the mask's own colours: the painted veins wherever the mask is lit."""
    t = np.array(Image.open(texture).convert('RGBA')); m = np.array(Image.open(mask).convert('RGBA'))
    out = np.zeros_like(t); lit = m[...,3] > 0
    out[lit] = t[lit]; out[...,3] = m[...,3]
    import io; buf = io.BytesIO(); Image.fromarray(out).save(buf, format='PNG'); return buf.getvalue()

def main():
    check = argparse.ArgumentParser()
    check.add_argument('--check', action='store_true')
    check.add_argument('--only', nargs='+', choices=list(DEFINITIONS), metavar='CLIP',
        help='rebuild just these clips and keep the installed rest, for quick iteration')
    args = check.parse_args()
    assert not (args.check and args.only), '--check rebuilds every clip'
    geo = json.loads((MODEL/'storm_roc.geo.json').read_text())
    bones = geo['minecraft:geometry'][0]['bones']
    study = json.loads((HERE/'candidate/storm_roc.geo.json').read_text())['minecraft:geometry'][0]['bones']
    rig = lambda bs: [[b['name'], b.get('parent'), b['pivot'], b.get('rotation')] for b in bs]
    assert rig(bones) == rig(study), 'The block model must keep the study skeleton the clips are authored on'
    source = json.loads((HERE/'candidate/storm_roc.animation.json').read_text())['animations']
    by = {b['name']: b for b in bones}
    # The study folded thin feathers; a model's own feathers may need their own fold in those clips.
    fold = json.loads((MODEL/'fold.json').read_text()) if (MODEL/'fold.json').exists() else {}
    # The study twists each thin tail feather on its own; the block fan moves only as a whole,
    # so its mirrored pairs stay mirrored.
    tail_feathers = {b['name'] for b in bones if b.get('parent') == 'tail_fan'}
    def pose(name, time):
        c = source['animation.storm_roc.'+name]; fix = fold.get(name, {})
        p = {b['name']: {k: np.array(fix[b['name']][k] if k in fix.get(b['name'], {}) else
                d if b['name'] in tail_feathers else sample(c['bones'].get(b['name'], {}).get(k), time, d), dtype=float)
                for k,d in [('rotation',[0,0,0]), ('position',[0,0,0]), ('scale',[1,1,1])]} for b in bones}
        # The study lifts its thin hooked toe tips 47 deg off the rock in the brawl poses. The
        # block feet stand flat with their talons on the ground, so a toe tip only ever curls
        # down (gripping a spire or closing on prey), never up: front toes curl with +X, the
        # back toe with -X.
        for label in ('left','right'):
            for i in range(4):
                r=p[f'{label}_toe_{i}_tip']['rotation']
                r[0]=min(r[0],0) if i==3 else max(r[0],0)
        if name in TUCKED: tuck(p)
        return p
    # The study's flying neck shrinks to 0.40 of its length, which crushed the block neck into
    # the chest. In the air it keeps 0.62, slimmer, and each joint rides out along its longer
    # section, so the head reaches further forward. Ground attacks keep the study's neck.
    NECK = (('neck_base_skin','neck_lower'), ('neck_lower_skin','neck_mid'), ('neck_mid_skin','neck_upper'),
        ('neck_upper_skin','neck_tip'), ('neck_tip_skin','head'))
    AIRBORNE = {'flight', 'land', 'dive', 'takeoff', 'hop'}
    def lengthen_neck(p):
        for skin, joint in NECK:
            f = float(np.clip((1-p[skin]['scale'][1])/.6, 0, 1))
            p[skin]['scale'] = p[skin]['scale']+np.array([-.17,.22,-.20])*f
            p[joint]['position'][1] += 17*.22*f
    # The study's folded wings cross the primaries under the drooped tail, so the wingtips hang
    # below the body. In these folded poses each primary tilts up about its root, just enough
    # for its tip half to clear the top of the tail.
    TUCKED = {'hunched', 'perched', 'peck'}
    def tuck(p, clearance=2):
        m = matrices(p)
        tail = (shape(by['tail_08'])@m['tail_08'].T)[:,:3]
        z0, z1 = tail[:,2].min(), tail[:,2].max()
        ends = [tail[tail[:,2] < z0+.3*(z1-z0)], tail[tail[:,2] > z1-.3*(z1-z0)]]
        (zb, yb), (zt, yt) = [(e[:,2].mean(), e[:,1].max()) for e in ends]
        top = lambda z: yb+(yt-yb)*(z-zb)/(zt-zb)
        rig = Pose(SimpleNamespace(bones=bones)); rig.world = m
        for b in bones:
            n = b['name']
            if '_primary_' not in n or not b['cubes']: continue
            pivot = point(m, n, b['pivot']); v = (shape(b)@m[n].T)[:,:3]
            d = v[v[:,2].argmax()]-pivot; d /= np.linalg.norm(d)
            k = np.cross(d, [0,1,0]); k /= np.linalg.norm(k)
            back = v[v[:,2] > np.median(v[:,2])]-pivot
            def turn(a):
                K = np.array([[0,-k[2],k[1]],[k[2],0,-k[0]],[-k[1],k[0],0]])
                return np.eye(3)+math.sin(a)*K+(1-math.cos(a))*K@K
            def clear(a):
                w = back@turn(a).T+pivot; return np.min(w[:,1]-top(w[:,2]))
            if clear(0) >= clearance: continue
            lo, hi = 0., math.radians(80)
            for _ in range(24):
                mid = (lo+hi)/2
                if clear(mid) >= clearance: hi = mid
                else: lo = mid
            rig.put(n, turn(hi)@m[n][:3,:3], pivot)
            for key, value in rig.channels[n].items(): p[n][key] = np.array(value, dtype=float)
    def mix(a,b,f):
        out = copy.deepcopy(a)
        for n in out:
            for k in out[n]:
                delta=b[n][k]-a[n][k]
                if k=='rotation': delta=(delta+180)%360-180
                out[n][k]=a[n][k]+delta*f
        return out
    def matrices(p):
        m={}
        for b in bones:m[b['name']]=m.get(b.get('parent'),np.eye(4))@matrix(b,{k:v.tolist() for k,v in p[b['name']].items()},0)
        return m
    def point(m,n,v): return (m[n]@np.r_[v,1])[:3]
    tip=bill(by['upper_beak'])
    # Head, bill tip, neck, chest, feet.
    landmarks={'head':('head',by['head']['pivot']), 'beak':('upper_beak',tip),
        'neck':('neck_upper',by['neck_upper']['pivot']), 'body':('body',[0,65,5]),
        'left_foot':('left_foot',by['left_foot']['pivot']), 'right_foot':('right_foot',by['right_foot']['pivot'])}
    def ease(t,a,b):
        f=float(np.clip((t-a)/(b-a),0,1));return f*f*(3-2*f)
    def quaternion(R):
        # Unit quaternion, w first. Stable also for the folded wing's near-180
        # rotations, where interpolating Euler components scatters the vanes.
        if np.trace(R)>0:
            s=math.sqrt(1+np.trace(R))*2
            return np.array([s/4,(R[2,1]-R[1,2])/s,(R[0,2]-R[2,0])/s,(R[1,0]-R[0,1])/s])
        i=int(np.argmax(np.diag(R)));j=(i+1)%3;k=(i+2)%3
        s=math.sqrt(1+R[i,i]-R[j,j]-R[k,k])*2;q=np.zeros(4)
        q[0]=(R[k,j]-R[j,k])/s;q[i+1]=s/4
        q[j+1]=(R[j,i]+R[i,j])/s;q[k+1]=(R[k,i]+R[i,k])/s
        return q
    def orient(a,b,f):
        qa,qb=quaternion(a),quaternion(b)
        if qa@qb<0:qb=-qb
        q=qa*(1-f)+qb*f;q/=np.linalg.norm(q);w,x,y,z=q
        return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
            [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
            [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
    def blend_wings(p, flight, weight=1):
        if weight<=0:return
        names=[n for n in p if any(s in n for s in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries'))]
        if weight>=1:
            for n in names:p[n]=copy.deepcopy(flight[n])
            return
        target=copy.deepcopy(p)
        for n in names:target[n]=flight[n]
        a,b=matrices(p),matrices(target)
        rig=Pose(SimpleNamespace(bones=bones));rig.world=a.copy()
        # The arm bones turn toward their targets in world space (a local blend swings the
        # half-open hand forward around the chest); feathers turn against their arm bone, so they
        # open as one sheet with it. Every root is carried by its parent, parents first: the
        # forearm starts where the upper arm ends and each feather root stays on its arm.
        arm=('wing_shoulder','wing_elbow','wing_wrist')
        for n in [bone['name'] for bone in bones if bone['name'] in names]:
            pivot=np.array(by[n]['pivot']);parent=by[n].get('parent')
            la,lb=np.linalg.inv(a[parent])@a[n],np.linalg.inv(b[parent])@b[n]
            at=(la[:3,:3]@pivot+la[:3,3])*(1-weight)+(lb[:3,:3]@pivot+lb[:3,3])*weight
            W=rig.world[parent]
            R=orient(a[n][:3,:3],b[n][:3,:3],weight) if n.endswith(arm) else W[:3,:3]@orient(la[:3,:3],lb[:3,:3],weight)
            rig.put(n,R,W[:3,:3]@at+W[:3,3])
        for n,cs in rig.channels.items():
            for k,v in cs.items():p[n][k]=np.array(v,dtype=float)
    def wings(p,t,weight=1):blend_wings(p,pose('flight',t%2.4),weight)
    def blend_local(p,other,names,weight):
        """Turns these bones toward other's local orientations by slerp, parents first, each carried
        by its already blended parent. Unlike mixing Euler channels, it never spins a bone between
        two differently stored versions of nearly the same angle."""
        if weight<=0:return
        if weight>=1:
            for n in names:p[n]=copy.deepcopy(other[n])
            return
        a,b=matrices(p),matrices(other)
        rig=Pose(SimpleNamespace(bones=bones));rig.world=dict(a)
        for n in [bone['name'] for bone in bones if bone['name'] in names]:
            pivot=np.array(by[n]['pivot']);parent=by[n].get('parent')
            la,lb=np.linalg.inv(a[parent])@a[n],np.linalg.inv(b[parent])@b[n]
            at=(la[:3,:3]@pivot+la[:3,3])*(1-weight)+(lb[:3,:3]@pivot+lb[:3,3])*weight
            W=rig.world[parent]
            rig.put(n,W[:3,:3]@orient(la[:3,:3],lb[:3,:3],weight),W[:3,:3]@at+W[:3,3])
        for n,cs in rig.channels.items():
            for k,v in cs.items():p[n][k]=np.array(v,dtype=float)
    LEG_BONES=[f'{side}_{part}' for side in ('left','right') for part in ['thigh','shin','tarsus','foot']+
        [f'toe_{i}_{end}' for i in range(4) for end in ('base','tip')]]
    def plant(p, targets):
        rig=Pose(SimpleNamespace(bones=bones));rig.world=matrices(p)
        for label,foot in targets.items():
            hip=point(rig.world,label+'_thigh',by[label+'_thigh']['pivot'])
            foot=np.array(foot,dtype=float)
            # Rotate the sagittal solve into the hip-to-foot plane. Unlike moving
            # the foot sideways after IK, this preserves all three rigid lengths
            # during weight shifts and body twists, with exact planted targets.
            reach=foot-hip;assert np.linalg.norm(reach)<67.9,(label,'unreachable foot',reach)
            # Tilt laterally rather than yawing the knee plane. Yaw is singular
            # when a planted foot is directly under its hip in Z.
            roll=math.degrees(math.atan2(reach[0],-reach[1]))
            R=rotation([0,0,roll]);local=R.T@reach
            class LegSpace:
                def put(self,name,orientation,target):rig.put(name,R@orientation,hip+R@target)
            theta=-75
            for candidate in [-75]+[-75+d for i in range(1,181) for d in (i,-i)]:
                knee=rotation([candidate,0,0])@np.array([0,-14,0])
                if 2.001<np.linalg.norm((local-knee)[1:])<53.999:
                    theta=candidate;break
            solve_leg(LegSpace(),label,np.zeros(3),local,theta)
            rig.put(label+'_foot',np.eye(3),foot)
        for n,cs in rig.channels.items():
            for k,v in cs.items():p[n][k]=np.array(v,dtype=float)
    def locomotion_mix(a,b,f):
        if f<=0:return copy.deepcopy(a)
        if f>=1:return copy.deepcopy(b)
        p=mix(a,b,f)
        ma,mb=matrices(a),matrices(b)
        plant(p,{label:(1-f)*point(ma,label+'_foot',by[label+'_foot']['pivot'])+
            f*point(mb,label+'_foot',by[label+'_foot']['pivot']) for label in ('left','right')})
        return p
    neutral=pose('hunched',0)
    neutral_m=matrices(neutral)
    feet={label:point(neutral_m,label+'_foot',by[label+'_foot']['pivot']) for label in ('left','right')}
    # 11.52 model pixels is .9 blocks of buried bill at the new scale.
    pin=point(matrices(pose('peck',.5)),'upper_beak',tip);pin[1]=-.9/SCALE
    wing_keys=('wing','primary','secondary','covert','leading','root_','primaries','secondaries')
    wing_cubes={label:{} for label in ('left','right')}
    for label in wing_cubes:
        for b in bones:
            if not b['name'].startswith(label+'_') or not any(k in b['name'] for k in wing_keys):continue
            corners=shape(b)
            if len(corners):wing_cubes[label][b['name']]=corners
    children={}
    for b in bones:children.setdefault(b.get('parent'),[]).append(b['name'])
    def subtree(n):return {n}.union(*[subtree(c) for c in children.get(n,[])])
    def splay(p,t,contact_only=False,joint='wing_shoulder'):
        # Roll each shoulder down until that wing's lowest feather meets the rock, then let
        # it lift and slap down again: the wings splay out and beat weakly against the ground.
        # With another joint, only the wing beyond it bends up off the rock.
        parts={label:{n:v for n,v in wing_cubes[label].items() if n in subtree(f'{label}_{joint}')} for label in ('left','right')}
        base={label:p[f'{label}_{joint}']['rotation'][2] for label in ('left','right')}
        lo={'left':-60.,'right':-60.};hi={'left':65.,'right':65.}
        targets={label:(WING_REST+(0 if contact_only else WING_SLAP*max(0,math.sin(t*7+side*.9))**2))/SCALE
            for label,side in (('left',1),('right',-1))}
        if contact_only:
            m=matrices(p)
            for label in list(lo):
                low=min(np.min(v@m[n][1]) for n,v in parts[label].items())
                if low>=targets[label]:del lo[label];del hi[label]
                else:lo[label]=0
            if not lo:return
        for _ in range(14):
            mid={label:(lo[label]+hi[label])/2 for label in lo}
            for label,side in (('left',1),('right',-1)):
                if label in mid:p[f'{label}_{joint}']['rotation'][2]=base[label]+side*mid[label]
            m=matrices(p)
            for label in lo:
                low=min(np.min(v@m[n][1]) for n,v in parts[label].items())
                if low>targets[label]:hi[label]=mid[label]
                else:lo[label]=mid[label]
        for label,side in (('left',1),('right',-1)):
            if label in lo:p[f'{label}_{joint}']['rotation'][2]=base[label]+side*(lo[label]+hi[label])/2
    def curve(t, keys):
        """Held keys with short eased transitions make avian snaps, not sine sweeps."""
        if t<=keys[0][0]:return keys[0][1]
        for (a,av),(b,bv) in zip(keys,keys[1:]):
            if t<=b:return av+(bv-av)*ease(t,a,b)
        return keys[-1][1]
    def pulse(t,a,b,c,d):return ease(t,a,b)*(1-ease(t,c,d))
    def head(p,pitch=0,yaw=0,roll=0):
        # Rotate about the rear skull socket, not the distant head pivot. This
        # keeps the locked head attached while allowing a strong one-eye cock.
        m=matrices(p);anchor=point(m,'head',np.array(by['head']['pivot'])+HEAD_SOCKET)
        R=rotation([pitch,yaw,roll]);rig=Pose(SimpleNamespace(bones=bones));rig.world=m
        rig.put('head',R,anchor-R@HEAD_SOCKET)
        for k,v in rig.channels['head'].items():p['head'][k]=np.array(v)
    def fan(p,t,amount=1):
        p['tail_fan']['rotation'][1]+=amount*18*math.sin(t*5)
        p['tail_fan']['rotation'][2]+=amount*7*math.sin(t*5+.5)
    def spread(p,beat=0,weight=1,sweep=0):
        # beat +1 is the raised recovery, -1 the committed downstroke.
        wings(p,0,weight)
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_shoulder']['rotation']+=np.array([sweep,0,side*48*beat])*weight
            p[label+'_wing_elbow']['rotation'][2]-=side*15*beat*weight
            p[label+'_wing_wrist']['rotation'][2]-=side*11*beat*weight
            for n in p:
                if n.startswith(label+'_root_'):p[n]['rotation'][2]+=side*22*beat*weight
                if n.startswith(label+'_primary_'):p[n]['rotation'][0]+=7*beat*weight
    FINGERS=(0,3,6,9)  # the primaries the chunky model builds
    def flap(p,t,stroke,weight=1,lag=.07,sweep=0):
        """A wingbeat that travels out along the wing. stroke(t) is +1 raised, -1 down.
        Each part follows the stroke a moment after the part inside it, so the hand trails up
        through the downstroke and whips down after it: the wing curves instead of swinging
        stiffly from the shoulder. On the way up the hand folds back and the fingers part;
        they close and twist into the downstroke."""
        wings(p,0,weight)
        s=lambda k:stroke(t-k*lag)
        h=.02;v=(stroke(t+h)-stroke(t-h))/(2*h)
        up=min(1,max(0,v/4));down=min(1,max(0,-v/4))
        shoulder,elbow,wrist=48*s(0),56*s(1),64*s(2)
        for label,side in (('left',1),('right',-1)):
            r=lambda n:p[f'{label}_{n}']['rotation']
            r('wing_shoulder')[:]+=np.array([sweep,0,side*shoulder])*weight
            r('wing_elbow')[:]+=np.array([0,side*8*up,side*(elbow-shoulder)])*weight
            r('wing_wrist')[:]+=np.array([0,side*24*up,side*(wrist-elbow)])*weight
            for i,f in enumerate(FINGERS):
                finger=72*s(3+i*.25)
                r(f'primary_{f:02}')[:]+=np.array([10*(down-up),side*(6+(i-2)*5)*up,side*(finger-wrist)])*weight
            for n in p:
                if n.startswith(label+'_root_'):p[n]['rotation'][2]+=side*.6*shoulder*weight
        return v
    perch=pose('perched',0);perch['root']['position'][1]-=65.25
    perch_m=matrices(perch)
    perch_feet={label:point(perch_m,label+'_foot',by[label+'_foot']['pivot']) for label in ('left','right')}
    plant(perch,perch_feet)
    def perched(time):
        t=time/SLOW;p=copy.deepcopy(perch);phase=math.tau*t/7.2
        breathe=math.sin(phase*2)
        shake=pulse(t,3.75,3.85,4.12,4.25)*math.sin((t-3.75)*math.tau*9)
        stretch=pulse(t,4.55,5.05,5.65,6.3)
        p['body']['position'][1]+=1.5*breathe
        p['body']['position'][0]+=2.5*math.sin(phase)
        p['body']['rotation'][2]+=2*math.sin(phase)+5*shake
        p['body']['rotation'][0]+=2*breathe+3*shake
        yaw=curve(t,[(0,0),(.45,0),(.60,38),(1.65,38),(1.8,-42),(2.95,-42),(3.1,-28),(3.55,-28),(3.7,0),(6.3,0),(6.45,24),(6.9,24),(7.2,0)])
        cock=curve(t,[(0,0),(2.05,0),(2.2,28),(2.9,28),(3.1,0),(7.2,0)])
        bob=pulse(t,3.15,3.25,3.35,3.5)
        p['neck_base']['rotation'][0]+=5*bob+2*breathe
        head(p,-3*breathe+10*bob,yaw,cock+3*shake)
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_shoulder']['rotation'][2]+=side*5*shake
        spread(p,.75,.48*stretch)
        p['tail_fan']['rotation'][1]+=22*pulse(t,.85,.95,1.05,1.3)-28*pulse(t,4.1,4.2,4.35,4.65)
        p['tail_fan']['rotation'][0]+=5*breathe+9*shake
        plant(p,perch_feet)
        return p
    def flight(time,slow=SLOW):
        t=(time/slow)%4.8
        # Two weighty, asymmetric beats; rapid power stroke, slow recovery, glide.
        # The downstroke ends shallower than the raise: a giant's wings don't point at the ground.
        beats=[(0,0),(.35,1),(.7,-.72),(1.15,.1),(1.55,1),(1.9,-.72),(2.45,0),(4.8,0)]
        p=pose('flight',0);flap(p,t,lambda x:curve(x%4.8,beats))
        bob=curve(t,[(0,0),(.35,-2),(.85,5),(1.55,-3),(2.05,5),(2.8,0),(4.8,0)])
        pitch=curve(t,[(0,0),(.35,-7),(.8,5),(1.55,-7),(2,5),(2.8,0),(4.8,0)])
        p['body']['position'][1]+=bob;p['body']['rotation'][0]+=pitch
        head(p,-4)
        p['tail_fan']['rotation'][0]-=pitch*.7
        p['tail_fan']['rotation'][1]+=4*math.sin(math.tau*t/4.8)
        glide=pulse(t,2.4,2.8,4.4,4.8)
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_wrist']['rotation'][2]+=side*2*math.sin(t*7)*glide
            for i,f in enumerate(FINGERS):
                p[f'{label}_primary_{f:02}']['rotation'][0]+=3*math.sin(t*7-i*.35)*glide
                p[f'{label}_primary_{f:02}']['rotation'][2]+=side*2.5*math.sin(t*5-i*.5)*glide
        return p
    HUNCHED=DEFINITIONS['hunched'][0]
    def hunched_breath(t):
        # Uneven breaths: a slow draw, a short catch and a quicker sigh out, each its own depth.
        return sum(depth*envelope(t,start,1.1,.15,.85,'ease','ease') for start,depth in
            ((.2,1),(2.3,.7),(5.35,1.1),(7.2,.85)))
    def hunched_hiss(t):
        # The threat display: it draws back, then hisses with its bill wide and its wings mantled.
        return envelope(t,4.05,.18,.5,.55)
    def hunched_thrust(t):
        # The neck goes out low under the hiss, quick but heavy: a whole neck can't snap like a bill.
        return envelope(t,3.9,.45,.35,.65,'ease','ease')
    def hunched(t):
        """The brawl guard, between blows. Breathing, slow weight shifts and a weaving neck never
        stop; over them come short beats spaced so any second of it shows one. The game resumes
        this loop where it left off, so each pause between attacks shows a different beat."""
        p=copy.deepcopy(neutral)
        breath=hunched_breath(t)
        # Weight rocks onto the left foot, then the right, holding each a while.
        weight=keys(t,[(0,0),(.9,0),(2.1,1),(3.5,1),(4.9,-1),(7.2,-1),(8.4,0),(HUNCHED,0)])
        sway=noise(t,HUNCHED,7,(1,2,3))-noise(0,HUNCHED,7,(1,2,3))
        drift=noise(t,HUNCHED,11,(2,3,5))-noise(0,HUNCHED,11,(2,3,5))
        draw=envelope(t,3.7,.35,0,.3,'ease','ease');hiss=hunched_hiss(t);thrust=hunched_thrust(t)
        tremble=shake(t,4.1,.75,13,1)
        rouse=envelope(t,7.3,.12,.08,.5,'snap','settle');flutter=shake(t,7.4,.55,8,2.5)
        flick=envelope(t,1.35,.1,.12,.8,'snap','settle')
        jaw=9*envelope(t,.55,.07,.03,.05,'snap','whip')+6*envelope(t,.78,.06,.02,.05,'snap','whip')\
            +7*envelope(t,8.55,.06,.03,.05,'snap','whip')
        body=p['body']
        body['position']+=np.array([3*weight,1.4*breath-.8*weight*weight+2.5*draw-4*thrust,0])
        body['rotation']+=np.array([-1.8*breath-1.5*draw+3*thrust,0,2*weight+1.2*flutter])
        p['neck_base']['rotation']+=np.array([-1.2*breath-4*draw+6.5*thrust+1.5*drift,5*sway,0])
        p['neck_lower']['rotation'][1]-=2.5*sway
        # The head keeps its own level in the world while the neck weaves under it.
        glance=twitch(t,GUARD_GLANCES,HUNCHED)
        head(p,jaw/3-6*hiss+1.5*tremble+glance[0],4*drift+glance[1],glance[2]+4*shake(t,8.62,.35,7,2))
        p['lower_beak']['rotation'][0]=jaw+36*hiss+2*tremble
        # The wings rise into the mantle with the neck, not the bill: they are too big to snap open.
        spread(p,.5,.45*thrust)
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_shoulder']['rotation'][2]+=side*(1.8*breath+1.5*tremble+6*rouse+2.5*flutter)
        p['tail_fan']['rotation']+=np.array([0,16*flick-6*weight,5*flick])
        targets={k:v.copy() for k,v in feet.items()}
        for label,start,end in KNEADS:
            # Lift a little and set down hard; the talons close while it is up and dig in on landing.
            lift=envelope(t,start,.22,.1,end-start-.32,'ease','drop')
            grip=envelope(t,start,.15,.17,end-start-.32)+.6*envelope(t,end,.06,.25,.45)
            targets[label]+=np.array([0,2.5*lift,-1.5*lift])
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*30*grip
        plant(p,targets);return p
    def hunched_crest(t):
        # Up a little on each breath, flared by the hiss, ruffled by the rouse.
        return REST+.12*hunched_breath(t)+(1-REST)*hunched_hiss(t)+.25*envelope(t,7.3,.12,.1,.5)
    def stuck(t):
        p=pose('peck',.5);spread(p,-.1)
        effort=pulse(t,0,.15,4.55,4.85)
        heave=ease(t,4.85,5.65)
        p['root']['rotation'][0]=10+3*math.sin(t*9)*effort-3*heave
        p['body']['rotation'][0]+=6*math.sin(t*7)*effort-7*heave
        p['body']['rotation'][2]+=7*math.sin(t*10)*effort
        p['neck_base']['rotation'][0]+=2*math.sin(t*8)*effort+6*heave
        p['tail_fan']['rotation'][1]+=32*math.sin(t*11)*effort
        p['tail_fan']['rotation'][0]+=18*math.sin(t*8)*effort
        p['lower_beak']['rotation'][0]=12+12*math.sin(t*16)*effort
        # Anchor the BILL after the whole-body heave. Release only after 5.65.
        p['root']['position']+=pin-point(matrices(p),'upper_beak',tip)
        splay(p,t)
        m=matrices(p);targets={}
        for label,side in (('left',1),('right',-1)):
            foot=point(m,label+'_foot',by[label+'_foot']['pivot'])
            hip=point(m,label+'_thigh',by[label+'_thigh']['pivot'])
            foot[1]=max(hip[1]-54,3.75+11*max(0,math.sin(t*13+side*math.pi/2))*effort)
            foot[2]+=8*math.sin(t*13+side*math.pi/2)*effort
            targets[label]=foot
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=30*math.sin(t*17+i+side)*effort
        plant(p,targets)
        return locomotion_mix(p,neutral,ease(t,5.65,6))
    buried=stuck(0)
    def bite(t,leap=False):
        """Thunder Bite. It rears up slowly with its bill opening wide and its wings mantled, its
        head on its prey, holds there charged while it aims, then lunges and snaps its bill shut
        in the air at player height at 2.30. It holds the bite, recoils and pulls back heavily into
        its guard. With leap, its prey is out of reach: from the aim lock it sinks into a deep
        crouch with its wings raised slowly, drives off the rock with straightening legs and a
        heavy downbeat, sails through the air with its legs trailing then swinging forward, talons
        spread, as its wings recover and flare broadly to brake, lands hard and soaks the weight
        up, its momentum carrying it into the bite at 2.80."""
        impact=BITE_LEAP_IMPACT if leap else BITE_IMPACT
        # The leap's body and head follow the standing bite's timeline, stretched from the lock to
        # the bite across its slower crouch, push and flight; after the bite they match again.
        u=t
        if leap and t>1.25:u=1.25+(t-1.25)*(BITE_IMPACT-1.25)/(impact-1.25) if t<=impact else t-(impact-BITE_IMPACT)
        rear=keys(u,[(0,0),(1.3,1),(1.95,1.12),(BITE_IMPACT,0,'hit')])
        strike=keys(u,[(0,0),(2.0,0),(BITE_IMPACT,1,'hit'),(2.6,1),(3.35,0)])
        source_t=keys(u,[(0,0),(1.3,.3),(2.0,.3),(BITE_IMPACT,.5,'hit'),(2.6,.5),(3.35,.85),(3.9,1.6)])
        p=pose('peck',source_t)
        # The bite's kick back through the body as the bill snaps shut.
        jolt=envelope(u,BITE_IMPACT,.04,.06,.4,'snap','ease')
        body=p['body']
        body['position']+=np.array([0,9*rear,8*rear-5*strike+2*jolt])
        body['rotation'][0]+=-14*rear-1.5*jolt
        p['neck_base']['rotation'][0]-=30*rear+3*jolt
        if leap:
            # Load, push, land: +y is up and -z forward.
            load=keys(t,[(0,0),(1.25,0),(1.85,1),(BITE_TAKEOFF,0,'snap')])
            push=keys(t,[(0,0),(1.85,0),(BITE_TAKEOFF,1,'snap'),(2.45,.35),(BITE_LANDING,0)])
            absorb=envelope(t,BITE_LANDING,.06,.1,.7,'snap','ease')
            body['position']+=np.array([0,-14*load+5*push-10*absorb,6*load-9*push+3*absorb])
            body['rotation'][0]+=10*load-8*push+9*absorb
            p['neck_base']['rotation'][0]+=-8*load+5*absorb
            p['tail_fan']['rotation'][0]+=-10*load+14*push-8*absorb
        # The head keeps its bite angle until the neck has pulled back, so the bill only retreats.
        pitch=14*keys(u,[(0,0),(2.0,0),(BITE_IMPACT,1,'hit'),(2.9,1),(3.45,0)])
        # As it rears up and leans back, its head stays on its prey: it looks down along the line to
        # where it will bite, then hands over to the lunge. (In the game its gaze then follows the
        # actual player until the aim locks.)
        aim=keys(u,[(0,0),(.45,1),(2.0,1),(BITE_IMPACT,0,'hit')])
        if aim>0:
            eye=point(matrices(p),'head',np.array(by['head']['pivot'])+HEAD_SOCKET)
            look=math.degrees(math.atan2(eye[1]-bite_at[1],eye[2]-bite_at[2]))
            pitch=aim*look+(1-aim)*pitch
        head(p,pitch)
        if leap:
            # Landing hard drops its head; the neck lifts the bill back to player height for the bite.
            hold=envelope(t,impact-.18,.12,.25,.45,'ease','ease')
            if hold>0:
                base=p['neck_base']['rotation'][0]
                def bill_y(bend):
                    p['neck_base']['rotation'][0]=base+bend;head(p,pitch)
                    return point(matrices(p),'upper_beak',tip)[1]
                goal=bill_y(0)*(1-hold)+bite_at[1]*hold
                lo,hi=-60.,40.
                for _ in range(24):
                    mid=(lo+hi)/2
                    if bill_y(mid)>goal:lo=mid
                    else:hi=mid
                bill_y((lo+hi)/2)
            # Raised slowly for the push, one heavy downbeat to drive off, a slow recovery stroke
            # through the air, then a broad flare to brake as it comes down.
            flap(p,t,lambda x:keys(x,[(0,0),(1.3,.9),(1.85,1.2),(BITE_TAKEOFF,-1.1,'hit'),(2.45,.35),
                (2.65,.85),(BITE_LANDING,.9),(3.0,-.2),(3.45,.2),(4.1,0)]),
                keys(t,[(0,0),(1.3,.55),(1.85,.75),(BITE_TAKEOFF,.9),(BITE_LANDING,.9),(3.2,.5),(3.8,.2),(4.3,0)]))
        else:
            # Wings mantled high through the windup, swept forward with the lunge, folded as it settles.
            flap(p,t,lambda x:keys(x,[(0,0),(1.3,.9),(2.0,.95),(BITE_IMPACT,-.4,'hit'),(2.55,-.1),(3.0,.2),(3.6,0)]),
                keys(t,[(0,0),(1.3,.55),(2.0,.6),(BITE_IMPACT,.6),(3.1,.3),(3.7,0)]))
        p['tail_fan']['rotation'][0]+=14*rear-9*strike
        # Bill opening wide as it rises, wider still in the lunge, snapped shut on the bite.
        p['lower_beak']['rotation'][0]=keys(u,[(0,0),(.5,0),(1.2,45),(2.0,48),(2.18,52),(BITE_IMPACT,0,'whip'),
            (2.65,0),(2.85,8),(3.1,0)])
        # The talons clench into the rock for the push, open wide in the air, and clench again as
        # it lands.
        grip=envelope(t,2.0,.15,.6,.5) if not leap else envelope(t,1.7,.15,.25,.1)+envelope(t,BITE_LANDING,.05,.4,.45)
        for label in ('left','right'):
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*25*grip
        plant(p,feet)
        if not leap or not BITE_TAKEOFF-.03<t<BITE_LANDING+.05:return p
        # In the air its legs trail back from the push, then swing forward under it with the talons
        # spread to land, as a raptor comes in: the study's grab legs, from tucked back to reaching.
        air=copy.deepcopy(p);grab=pose('grab',keys(t,[(BITE_TAKEOFF,0),(2.45,.5),(BITE_LANDING,1.2)]))
        for n in LEG_BONES:air[n]=copy.deepcopy(grab[n])
        blend_local(p,air,LEG_BONES,keys(t,[(BITE_TAKEOFF-.03,0),(BITE_TAKEOFF+.15,1),
            (BITE_LANDING-.08,1),(BITE_LANDING+.05,0,'drop')]))
        return p
    # Where the bite closes: the head looks here while it rears up.
    bite_at=point(matrices(bite(BITE_IMPACT)),'upper_beak',tip)
    # The guard's bill height, where the Raptor Strike's neck starts and ends.
    pounce_bill=point(matrices(neutral),'upper_beak',tip)[1];pounce_lift={}
    def pounce(t):
        """Raptor Strike. It sinks into a low stalking crouch with its wings half open and low and
        its head dead still on its prey, rocking its weight from foot to foot, its tail lashing.
        When its aim locks (1.20) it draws back and coils, gripping the rock, then drives off at
        1.60 with one huge downbeat and flies flat and fast at its prey, wings wide and level, legs
        tucked back, head down on its prey. At the last moment it pitches up, flares its wings to
        brake and swings its talons forward, screeching, onto its prey at 2.60. It soaks up the
        landing and mantles over the spot, wings arched down around it, crest raised and head low,
        glances about over its kill, then slowly settles back into its guard."""
        L,I=POUNCE_LAUNCH,POUNCE_IMPACT
        p=copy.deepcopy(neutral);body=p['body']
        stalk=keys(t,[(0,0),(.6,1),(1.0,1),(1.35,1.15),(L,0,'hit')])
        coil=keys(t,[(0,0),(1.0,0),(1.35,1),(L,0,'hit')])
        push=keys(t,[(0,0),(1.35,0),(L,1,'hit'),(1.95,.35),(2.25,0)])
        glide=keys(t,[(0,0),(L,0),(1.85,1),(2.25,1),(2.45,0)])
        flare=keys(t,[(0,0),(2.25,0),(2.48,1),(I,.5,'hit'),(I+.2,0)])
        absorb=envelope(t,I-.02,.08,.2,1.0,'snap','ease')
        mantle=keys(t,[(0,0),(I,0),(2.9,1),(3.35,1),(4.0,0)])
        # Its weight rocks from foot to foot as it stalks, and stills when its aim locks.
        shift=keys(t,[(0,0),(.4,0),(.7,1),(.95,-.7),(1.2,0)])
        body['position']+=np.array([3.5*shift,-14*stalk-4*coil+3.5*push-14*absorb-5*mantle,9*coil-7*push+3*absorb])
        body['rotation'][0]+=7*stalk+3*coil-5*push+8*glide-15*flare+10*absorb+7*mantle
        body['rotation'][2]-=4*shift
        # Its tail lashes as it stalks: flicked to one side, held, flicked to the other.
        p['tail_fan']['rotation'][1]+=keys(t,[(0,0),(.55,0),(.66,26,'snap'),(.9,18),(.98,-28,'snap'),(1.15,-20),
            (1.22,14,'snap'),(1.4,4),(L,0)])
        # In the flare its tail swings up with its body's pitch, clear of the rock coming up under it.
        p['tail_fan']['rotation'][0]+=-6*stalk+14*push+6*glide+LIFT*18*flare-8*absorb-6*mantle
        # Its head stays on its prey all the way in: level as it stalks (in the game its gaze
        # follows its prey until the aim locks), down along its flight and at the strike. Over its
        # kill it glances about, then settles.
        pitch=keys(t,[(0,0),(.6,6),(1.5,6),(L,10),(2.2,20),(I,28),(2.9,34),(3.4,32),(4.0,0)])
        look=twitch(t,[(3.0,(-14,26,8)),(3.04,(-16,30,10)),(3.3,(-10,-22,-6)),(3.55,(0,0,0))])
        # Its long neck carries the bill at these heights above the rock (blocks): low and level as
        # it stalks, lower still as it coils, thrust out with the push, raised as it flares, then
        # down over its kill.
        goal=pounce_bill+keys(t,[(0,0),(.6,-.65),(1.0,-.65),(1.35,-.9),(L,-.15,'hit'),(2.0,-.4),(2.3,-.5),
            (2.5,.3),(I,-.1),(2.75,-.8),(2.95,-1.2),(3.4,-1.15),(4.0,0)])/SCALE
        base=p['neck_base']['rotation'][0]
        def bill_y(bend):
            p['neck_base']['rotation'][0]=base+bend;head(p,pitch+look[0],look[1],look[2])
            return point(matrices(p),'upper_beak',tip)[1]
        lo,hi=-60.,40.
        for _ in range(24):
            mid=(lo+hi)/2
            if bill_y(mid)>goal:lo=mid
            else:hi=mid
        bill_y((lo+hi)/2)
        # A screech as it strikes, and a hiss over its kill.
        p['lower_beak']['rotation'][0]=keys(t,[(0,0),(2.25,0),(2.45,30),(I,38,'snap'),(2.85,34),(3.1,10),(3.3,14),(3.6,0)])
        # Half open and low as it stalks; spread wide for the push (not raised high like the
        # Thunderclap's), one huge downbeat to drive off, wide and level in the glide, a broad flare
        # to brake, then arched down around its prey.
        flap(p,t,lambda x:keys(x,[(0,0),(.6,-.25),(1.0,-.2),(1.4,.5),(L,-1.05,'hit'),(1.85,-.1),(2.05,.08),
            (2.25,.12),(2.48,.9),(I,-.35,'hit'),(2.85,-.48),(3.35,-.44),(4.0,0)]),
            keys(t,[(0,0),(.6,.5),(1.0,.55),(1.4,.95),(L,1),(I,1),(2.85,.9),(3.35,.85),(4.0,0)]))
        # The talons clench into the rock for the push, and clench on its prey as it lands.
        grip=envelope(t,1.1,.15,.3,.1)+keys(t,[(0,0),(I-.02,0),(I+.06,1,'snap'),(3.4,1),(4.0,0)])
        for label in ('left','right'):
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*28*grip
        plant(p,feet)
        # Mantling, its arched wings settle toward the rock without passing into it.
        if t>=I-.05:
            for joint in ('wing_wrist','wing_elbow','wing_shoulder'):splay(p,0,True,joint)
        if not L-.03<t<I+.05:return p
        # In the air its legs stream back from the push and tuck, then swing forward under it as
        # it flares, talons spread, and slam down onto its prey: the study's grab legs.
        # They leave the rock as they were at the push, not solved to a rock they no longer reach.
        if 'legs' not in pounce_lift:pounce_lift['legs']=pounce(L-.03)
        if t<I-.08:
            for n in LEG_BONES:p[n]=copy.deepcopy(pounce_lift['legs'][n])
        air=copy.deepcopy(p);grab=pose('grab',keys(t,[(L,.8),(1.8,.3),(2.0,.2),(2.18,.3),(I-.08,1.2)]))
        for n in LEG_BONES:air[n]=copy.deepcopy(grab[n])
        w=keys(t,[(L-.03,0),(L+.15,1),(I-.08,1),(I+.05,0,'drop')])
        # The hip, knee and ankle are hinges: they swing straight through between the bend they
        # push off with and the flying one. A slerp would fold the knee through itself, as the
        # two bend opposite ways.
        hinge=lambda n:n.split('_',1)[1] in ('thigh','shin','tarsus')
        for n in filter(hinge,LEG_BONES):
            for k in ('rotation','position'):p[n][k]=p[n][k]*(1-w)+grab[n][k]*w
        blend_local(p,air,[n for n in LEG_BONES if not hinge(n)],w)
        return p
    def spiral_body(t):
        """Storm Spiral (experimental), after the flights in How to Train Your Dragon. The
        Thunderclap's crouch, then it launches into a corkscrew climb: nose up steeply along a tight
        helix, barrel-rolling twice around its own spine with its wings swept back and its tail
        streaming. At the top it rolls level, spreads its wings wide and stalls with a screech, head
        turning down to find its prey; then it tucks and dives talons first, flares at the last
        moment and slams into the rock at 3.25, soaks it up and slowly folds its wings."""
        p=copy.deepcopy(neutral)
        load=keys(t,[(0,0),(.6,1),(SPIRAL_LIFT,0,'snap')])
        climb=keys(t,[(0,0),(.7,0),(1.0,1),(SPIRAL_TOP-.1,1),(SPIRAL_TOP+.2,0)])
        # The helix it climbs, and the two rolls around its own spine as it goes.
        heading=keys(t,[(0,0),(SPIRAL_LIFT,0),(SPIRAL_TOP,360)])
        roll=keys(t,[(0,0),(SPIRAL_LIFT+.05,0),(SPIRAL_TOP,720)])
        dive=keys(t,[(0,0),(SPIRAL_DIVE,0),(2.85,1),(3.05,1),(SPIRAL_IMPACT,.1)])
        air=keys(t,[(0,0),(SPIRAL_LIFT-.05,0),(SPIRAL_LIFT+.15,1),(SPIRAL_IMPACT-.1,1),(SPIRAL_IMPACT,0)])
        absorb=envelope(t,SPIRAL_IMPACT-.02,.08,.15,1.0,'snap','ease')
        strokes=[(0,0),(.6,.95),(SPIRAL_LIFT,-1.0,'hit'),(1.1,-.3),(SPIRAL_TOP,-.3),(SPIRAL_TOP+.25,.35),(SPIRAL_DIVE,.3),
            (2.85,.8),(3.1,-.2),(SPIRAL_IMPACT,-.1),(3.7,-.35),(5.0,0)]
        weight=keys(t,[(0,0),(.45,.85),(.6,1),(4.3,1),(5.0,0)])
        flap(p,t,lambda x:keys(x,strokes),weight)
        # Swept back like a drill for the climb, open wide at the top, tucked for the dive.
        fold=keys(t,[(0,0),(SPIRAL_LIFT,0),(1.1,.75),(SPIRAL_TOP-.1,.75),(SPIRAL_TOP+.2,0),(SPIRAL_DIVE,0),(2.8,.7),
            (3.05,.7),(SPIRAL_IMPACT-.05,0)])
        blend_wings(p,pose('hunched',0),fold)
        for label in ('left','right'):p[label+'_wing_shoulder']['rotation'][0]-=22*fold
        # The root carries the climb's heading and pitch; the body rolls around its own spine.
        p['root']['rotation']+=np.array([-70*climb+55*dive,heading,0])
        p['body']['rotation'][2]+=roll
        p['body']['position'][1]+=-13*load-12*absorb
        p['body']['rotation'][0]+=4*load+6*absorb
        p['neck_base']['rotation'][0]+=-14*load+8*climb-18*absorb
        p['tail_fan']['rotation'][0]+=-8*load+12*air
        fan(p,t,.3*air)
        # In the climb the head rides along its neck through the rolls; from the top it looks down
        # on the arena, then for the landing.
        look=keys(t,[(0,6),(.6,6),(SPIRAL_TOP,10),(SPIRAL_TOP+.25,45),(SPIRAL_DIVE,40),(3.05,30),(SPIRAL_IMPACT,10),(3.7,6),(5.0,0)])
        own=keys(t,[(0,1),(.6,1),(SPIRAL_LIFT,0),(SPIRAL_TOP-.05,0),(SPIRAL_TOP+.25,1)])
        if own>=1:head(p,look,heading%360)
        elif own>0:
            m=matrices(p);anchor=point(m,'head',np.array(by['head']['pivot'])+HEAD_SOCKET)
            R=orient(m['head'][:3,:3],rotation([look,heading%360,0]),own)
            rig=Pose(SimpleNamespace(bones=bones));rig.world=m;rig.put('head',R,anchor-R@HEAD_SOCKET)
            for k,v in rig.channels['head'].items():p['head'][k]=np.array(v)
        p['lower_beak']['rotation'][0]=34*envelope(t,SPIRAL_TOP+.1,.12,.25,.4)+32*envelope(t,SPIRAL_IMPACT-.1,.1,.4,.6)
        return p
    # Its legs as they leave the rock: in the air they blend from there, never solved against it.
    lift_legs={}
    def spiral(t):
        p=spiral_body(t)
        if t<=SPIRAL_LIFT:plant(p,feet);return p
        if not lift_legs:
            q=spiral_body(SPIRAL_LIFT);plant(q,feet);lift_legs.update({n:copy.deepcopy(q[n]) for n in LEG_BONES})
        # Legs trail straight down off the rock, then thrust out with the talons open from the top.
        air=copy.deepcopy(p);legs=copy.deepcopy(p)
        grab=pose('grab',keys(t,[(SPIRAL_LIFT,.3),(SPIRAL_TOP,.3),(SPIRAL_DIVE,.8),(2.85,1.2),(SPIRAL_IMPACT-.08,1.2)]))
        for n in LEG_BONES:air[n]=copy.deepcopy(grab[n]);legs[n]=copy.deepcopy(lift_legs[n])
        blend_local(legs,air,LEG_BONES,keys(t,[(SPIRAL_LIFT,0),(SPIRAL_LIFT+.3,1)]))
        if t<SPIRAL_IMPACT-.12:return legs
        # Landing: solved onto the rock again, blending out of the reaching legs.
        plant(p,feet);blend_local(p,legs,LEG_BONES,keys(t,[(SPIRAL_IMPACT-.12,1),(SPIRAL_IMPACT+.04,0,'drop')]))
        if t>=SPIRAL_IMPACT-.05:
            for joint in ('wing_wrist','wing_elbow','wing_shoulder'):splay(p,0,True,joint)
        return p
    # The crest rests half raised. It flares when the Roc screams, calls or winds up a blow,
    # ruffles in the perched shake and lies sleek in the air. +1 is full flare, -1 sleek.
    def crest(p,a):
        for r in range(3):
            for c in range(3):
                n=f'crest_plume_{r}_{c}'
                p[n]['rotation'][0]+=(40 if a>0 else 34)*a*(1-.15*r)   # +X raises the plume
                p[n]['rotation'][2]+=(1-c)*16*max(a,0)                  # outer plumes fan out
        # The side fans on the nape only flare: turned back they stick out from the neck.
        for label,side in (('left',1),('right',-1)):
            for row in range(2):
                for col in (0,2,3):
                    n=f'{label}_cheek_blue_{row}_{col}'
                    p[n]['rotation'][0]+=22*max(a,0);p[n]['rotation'][2]-=side*16*max(a,0)
    REST=.3   # on the ground the brawler keeps it up
    def stuck_crest(t):
        effort=pulse(t,0,.15,4.55,4.85)
        return (.5+.35*math.sin(t*9)*effort)*(1-ease(t,5.65,6))+REST*ease(t,5.65,6)
    def crest_amount(name,t):
        if name=='perched':
            u=t/SLOW;return .8*pulse(u,3.6,3.75,4.25,4.6)+.25*pulse(u,2.05,2.2,2.9,3.1)
        if name=='call':return pulse(t,0,.45,2,2.8)
        if name=='flight':return -1
        if name=='land':return -1+ease(t,.2,.8)
        if name=='hunched':return hunched_crest(t)
        if name=='stuck':return stuck_crest(t)
        if name=='dive':return -ease(t,.1,.6)*(1-ease(t,2.65,3))+stuck_crest(0)*ease(t,2.65,3)
        if name=='hop':return stuck_crest(0)-(1+stuck_crest(0))*pulse(t,0,.3,.85,1.2)
        if name=='bite':return REST+(1-REST)*envelope(t,0,1.1,1.5,.9,'ease','ease')
        if name=='bite_leap':return REST+(1-REST)*envelope(t,0,1.1,2.0,.9,'ease','ease')
        if name.startswith('swing'):return REST+(1-REST)*envelope(t,0,.8,1.0,.8,'ease','ease')
        if name=='scream':return REST+(1-REST)*envelope(t,0,1.0,1.3,.6,'ease','ease')
        if name=='wing':return REST+(1-REST)*envelope(t,0,.6,2.2,1.0,'ease','ease')
        if name=='wing_spiral':return keys(t,[(0,REST),(.6,1),(SPIRAL_LIFT,1),(1.1,-1),(SPIRAL_TOP-.1,-1),(SPIRAL_TOP+.15,1),
            (SPIRAL_DIVE,1),(2.75,-1),(3.1,-1),(SPIRAL_IMPACT,1),(5.0,REST)])
        if name=='stampede':return REST+(1-REST)*pulse(t,0,.4,3.65,4.2)
        if name=='takeoff':return (REST+.5*pulse(t,0,.4,.9,1.2))*(1-ease(t,1.2,2.5))-ease(t,1.2,2.5)
        # Pressed flat as it stalks, swept back in the air, raised over its kill.
        if name=='pounce':return keys(t,[(0,REST),(.6,0),(1.5,0),(POUNCE_LAUNCH,-.6),(2.3,-.6),(2.5,1),(3.4,1),(4.0,REST)])
        raise ValueError(name)
    # A bird holds its tail clear of the rock. While its feet are down, the fan lifts about its
    # root just enough that its lowest feather stays above the soles: the long wedge tail would
    # otherwise dig into the ground in the brawl. The weight follows each clip's mix to the air.
    tail_parts={n:shape(by[n]) for n in tail_feathers if by[n]['cubes']}
    def tail_low(p,m,reach=None):
        """The fan's lowest corner; with reach (centre, radius), only corners over that disc."""
        F=m[by['tail_fan']['parent']]@matrix(by['tail_fan'],{k:v.tolist() for k,v in p['tail_fan'].items()},0)
        low=math.inf
        for n,v in tail_parts.items():
            w=v@(F@matrix(by[n],{k:x.tolist() for k,x in p[n].items()},0)).T
            if reach is not None:w=w[np.hypot(w[:,0]-reach[0][0],w[:,2]-reach[0][2])<reach[1]]
            if len(w):low=min(low,w[:,1].min())
        return low
    def sole(p):
        m=matrices(p);return min((shape(by[n])@m[n].T)[:,1].min() for n in ('left_foot','right_foot'))
    ground,perch_ground=sole(neutral),sole(perch)
    probe=copy.deepcopy(neutral);m0=matrices(probe);low=tail_low(probe,m0);probe['tail_fan']['rotation'][0]+=10
    LIFT=1 if tail_low(probe,m0)>low else -1   # the X direction that raises the fan
    def lift_tail(p,floor,weight,reach=None,clearance=4):
        if weight<=0:return
        m=matrices(p);base=p['tail_fan']['rotation'][0]
        if tail_low(p,m,reach)>=floor+clearance:return
        lo,hi=0.,80.
        for _ in range(16):
            mid=(lo+hi)/2;p['tail_fan']['rotation'][0]=base+LIFT*mid
            if tail_low(p,m,reach)>=floor+clearance:hi=mid
            else:lo=mid
        p['tail_fan']['rotation'][0]=base+LIFT*hi*weight
    # A spire's capstone reaches about 7 blocks out from the feet; past it the tail may
    # hang down the drop as the approved perch has it.
    capstone=(np.mean(list(perch_feet.values()),axis=0),7/SCALE)
    def grounded(name,t):
        """(floor, weight, reach) while the feet are on the summit or a spire."""
        if name in ('perched','call'):return [(perch_ground,1,capstone)]
        if name=='flight':return []
        if name=='land':return [(perch_ground,ease(t,0,.3),capstone)]
        if name=='dive':return [(perch_ground,1-ease(t,0,.25),capstone),(ground,ease(t,2.65,3))]
        if name=='hop':return [(ground,1-ease(t,0,.3)),(ground,ease(t,.85,1.2))]
        if name=='takeoff':return [(ground,1-ease(t,1.2,2.5))]
        if name=='pounce':return [(ground,1-ease(t,POUNCE_LAUNCH-.05,POUNCE_LAUNCH+.05)*(1-ease(t,POUNCE_IMPACT-.05,POUNCE_IMPACT+.05)))]
        if name=='wing':return [(ground,1-ease(t,WING_LIFT-.05,WING_LIFT+.1)*(1-ease(t,WING_IMPACT-.1,WING_IMPACT)))]
        if name=='wing_spiral':return [(ground,1-ease(t,SPIRAL_LIFT-.05,SPIRAL_LIFT+.1)*(1-ease(t,SPIRAL_IMPACT-.1,SPIRAL_IMPACT)))]
        if name=='bite_leap':return [(ground,1-ease(t,BITE_TAKEOFF-.05,BITE_TAKEOFF+.05)*(1-ease(t,BITE_LANDING-.05,BITE_LANDING+.05)))]
        return [(ground,1)]
    def motion(name,t):
        if name=='perched':return perched(t)
        if name=='flight':return flight(t)
        if name=='hunched':return hunched(t)
        if name=='stuck':return stuck(t)
        if name=='call':
            p=copy.deepcopy(perch)
            rear=pulse(t,0,.45,1.9,2);posture=pulse(t,0,.45,1.9,2.1);command=pulse(t,1.9,2,2.6,3)
            tremble=math.sin(t*math.tau*11)*pulse(t,.45,.55,1.8,1.9)
            p['body']['position'][1]+=10*posture-3*command
            p['body']['rotation'][0]-=13*posture-7*command
            p['neck_base']['rotation'][0]-=15*posture
            head(p,-78*rear+12*command+2*tremble,0,2*tremble)
            spread(p,.8*rear+.15*command,.6*rear+command)
            p['lower_beak']['rotation'][0]=50*pulse(t,.15,.45,2.4,2.85)
            p['tail_fan']['rotation'][0]+=14*rear
            for label,side in (('left',1),('right',-1)):
                p[label+'_wing_wrist']['rotation'][2]+=side*3*tremble
            plant(p,perch_feet);return p
        if name=='land':
            if t==0:return flight(0)
            p=flight(0);spread(p,.85*ease(t,0,.2),1,-12*ease(t,0,.2))
            m=matrices(p)
            targets={label:point(m,label+'_foot',by[label+'_foot']['pivot']) for label in ('left','right')}
            for v in targets.values():v[2]-=18*ease(t,0,.2);v[1]-=10*ease(t,0,.2)
            plant(p,targets)
            landing=copy.deepcopy(perch)
            landing['body']['position'][1]-=7*pulse(t,.25,.4,.45,.8)
            spread(landing,.6,(1-ease(t,.4,.8))*.9)
            plant(landing,perch_feet)
            return locomotion_mix(p,landing,ease(t,0,.3))
        if name in ('dive','hop'):
            if name=='dive':
                p=flight((t*1.2)%4.8,1)
                # Climb first; body tips at the apex, then a tight falcon stoop.
                p['root']['rotation'][0]=-22*pulse(t,0,.3,1.4,1.8)+78*ease(t,1.5,2.05)
                fold=pulse(t,1.65,2.05,2.55,2.8)
                folded=pose('hunched',0)
                # Fold using the approved articulated wing, swept aft as a unit.
                blend_wings(p,folded,fold)
                for label,side in (('left',1),('right',-1)):
                    p[label+'_wing_shoulder']['rotation'][0]-=22*fold
                fan(p,t,.3*(1-fold))
                p=locomotion_mix(perch,p,ease(t,0,.25))
                return locomotion_mix(p,buried,ease(t,2.65,3))
            p=flight(0)
            p['root']['rotation'][0]=curve(t,[(0,-25),(.3,-32),(.55,-10),(.85,75),(1.2,75)])
            flap(p,t,lambda x:curve(x,[(0,0),(.3,1),(.55,-1),(.85,.2),(1.2,0)]))
            head(p,curve(t,[(0,-45),(.35,-50),(.7,35),(1.2,65)]))
            p=locomotion_mix(buried,p,ease(t,0,.3))
            return locomotion_mix(p,buried,ease(t,.85,1.2))
        if name=='bite':return bite(t)
        if name=='bite_leap':return bite(t,True)
        if name.startswith('swing'):
            # Storm Sweep. It coils its neck far round to one side, its body twisting and its weight
            # shifting under it, wings half open for balance and its bill crackling; holds a beat,
            # then scythes its open bill across its whole front at chest height, overshoots with
            # the tail counter-swinging and settles heavily into its guard.
            p=copy.deepcopy(neutral);side=1 if name.endswith('left') else -1
            a=keys(t,[(0,0),(1.0,-1),(SWEEP_START,-1.08),(SWEEP_END,1),(1.85,1.14),(2.6,0)])*side
            coil=envelope(t,0,1.0,.25,.4,'ease','ease')
            p['body']['rotation']+=np.array([0,15*a,4*a])
            p['body']['position']+=np.array([-3*a,-4*coil+2*envelope(t,SWEEP_START,.2,.2,.6,'ease','ease'),0])
            p['neck_base']['rotation'][1]+=58*a
            p['neck_lower']['rotation'][1]+=24*a
            p['tail_fan']['rotation'][1]-=30*a
            spread(p,.45,keys(t,[(0,0),(1.0,.45),(SWEEP_START,.5),(SWEEP_END,.75),(1.9,.6),(2.6,0)]))
            p['lower_beak']['rotation'][0]=keys(t,[(0,0),(.8,10),(SWEEP_START,22),(SWEEP_END,30),(1.9,12),(2.3,0)])
            low=envelope(t,.95,.3,.6,.5,'ease','ease')
            start=p['neck_base']['rotation'][0]
            current=point(matrices(p),'upper_beak',tip)[1]
            target=current*(1-low)+(1.05/SCALE)*low
            lo,hi=-45.,55.
            for _ in range(18):
                angle=(lo+hi)/2;p['neck_base']['rotation'][0]=start+angle
                if point(matrices(p),'upper_beak',tip)[1]>target:lo=angle
                else:hi=angle
            p['neck_base']['rotation'][0]=start+(lo+hi)/2
            plant(p,feet);return p
        if name=='scream':
            # Storm Scream. Unlike the bite's tall rear-up with its head on its prey, it sinks low
            # and draws its head back in an S with its bill to the sky, chest up, wings spread wide
            # out to the sides and tail fanned: a huge, slow breath with crackles gathering in its
            # flared crest. It draws in a little more, then whips forward and blasts straight ahead
            # at 1.60. Its whole body shudders behind the scream, wings thrown back, until it runs
            # out of breath and settles.
            p=copy.deepcopy(neutral)
            breath=keys(t,[(0,0),(1.3,1),(1.5,1.08),(SCREAM_IMPACT,0,'hit')])
            blast=keys(t,[(0,0),(1.5,0),(SCREAM_IMPACT,1,'hit'),(2.25,.85),(2.9,0)])
            shudder=math.sin((t-SCREAM_IMPACT)*math.tau*14)*envelope(t,SCREAM_IMPACT,.06,.55,.4,'ease','ease')
            p['body']['position']+=np.array([0,-7*breath-4*blast,5*breath-3*blast])
            p['body']['rotation']+=np.array([-9*breath+8*blast+1.5*shudder,0,.8*shudder])
            p['neck_base']['rotation'][0]-=30*breath+4*blast
            p['neck_upper']['rotation'][0]+=14*breath
            # Wings spread wide and level through the breath, thrown back just after the blast.
            spread(p,keys(t,[(0,0),(1.3,.12),(1.5,.15),(1.8,-.35),(2.6,0)]),keys(t,[(0,0),(1.3,.95),(1.65,.95),(1.95,.8),(2.9,0)]))
            head(p,-68*breath+2*shudder)
            p['lower_beak']['rotation'][0]=keys(t,[(0,0),(.5,6),(1.3,14),(1.5,18),(SCREAM_IMPACT,48,'snap'),
                (2.25,46),(2.6,18),(2.9,0)])+2*shudder
            p['tail_fan']['rotation'][0]+=16*breath-10*blast
            plant(p,feet);return p
        if name=='wing':
            # Thunderclap. It sinks into a deep crouch as its wings rise, then leaps: its legs drive
            # it off the rock with one huge downbeat. Rising, its legs tuck and its wings sweep up
            # and back; at the top it hangs a moment, wings raised high and head down on the players
            # below; then it smashes down, legs reaching, and lands hard at 2.60 with its wings
            # thrown out for balance and a roar, soaks up the landing and slowly folds its wings.
            p=copy.deepcopy(neutral)
            strokes=[(0,0),(.6,.95),(WING_LIFT,-1.0,'hit'),(1.35,.45),(WING_APEX,1.0),(WING_DROP,1.05),
                (WING_IMPACT,-.1,'hit'),(3.0,-.35),(4.0,0)]
            weight=keys(t,[(0,0),(.45,.85),(.6,1),(2.9,1),(4.0,0)])
            load=keys(t,[(0,0),(.6,1),(WING_LIFT,0,'snap')])
            push=keys(t,[(0,0),(.6,0),(WING_LIFT,1,'snap'),(1.2,.4),(WING_APEX,0)])
            # Its body tips: nose up as it rises, forward over the apex, head down into the smash.
            tilt=keys(t,[(0,0),(WING_LIFT,0),(1.3,-1),(WING_APEX,.2),(WING_DROP,.7),(2.45,.5),(WING_IMPACT,.15),(3.2,0)])
            air=keys(t,[(0,0),(WING_LIFT-.05,0),(WING_LIFT+.15,1),(WING_IMPACT-.1,1),(WING_IMPACT,0)])
            absorb=envelope(t,WING_IMPACT-.02,.08,.15,1.0,'snap','ease')
            flap(p,t,lambda x:keys(x,strokes),weight)
            p['body']['position'][1]+=-13*load+6*push-12*absorb
            p['body']['rotation'][0]+=4*load+12*tilt+6*absorb
            p['neck_base']['rotation'][0]+=-14*load+6*air*max(0,tilt)-18*absorb
            p['tail_fan']['rotation'][0]+=-8*load+12*push+6*air
            head(p,6*load+30*air*max(0,tilt)+6*absorb)
            p['lower_beak']['rotation'][0]=14*envelope(t,WING_APEX-.1,.15,.2,.2)+32*envelope(t,WING_IMPACT-.1,.1,.4,.6)
            fan(p,t,.35*weight)
            plant(p,feet)
            # Landing, the wings thrown out for balance settle toward the rock without passing into it.
            if t>=WING_IMPACT-.05:
                for joint in ('wing_wrist','wing_elbow','wing_shoulder'):splay(p,0,True,joint)
            if not WING_LIFT-.05<t<WING_IMPACT+.04:return p
            # In the air its legs tuck as it rises and reach down for the landing, talons spread.
            hang=copy.deepcopy(p);grab=pose('grab',keys(t,[(WING_LIFT,.3),(WING_APEX,.45),(WING_DROP,.65),(WING_IMPACT-.08,1.2)]))
            for n in LEG_BONES:hang[n]=copy.deepcopy(grab[n])
            blend_local(p,hang,LEG_BONES,keys(t,[(WING_LIFT-.05,0),(WING_LIFT+.3,1),(WING_IMPACT-.12,1),(WING_IMPACT+.04,0,'drop')]))
            return p
        if name=='wing_spiral':return spiral(t)
        if name=='takeoff':
            p=copy.deepcopy(neutral);hit=1.2
            # A crouch with the wings opening, the huge downbeat at tick 24, then it beats away.
            strokes=[(0,0),(.45,.3),(1.1,1.15),(1.2,-1.1),(1.65,.85),(2.1,-.8),(2.5,0)]
            weight=ease(t,.15,.6)
            crouch=pulse(t,0,.3,.45,.7)
            raise_=pulse(t,.2,hit-.15,hit-.1,hit)
            slam=pulse(t,hit-.1,hit,hit+.15,hit+.5)
            v=flap(p,t,lambda x:curve(x,strokes),weight,sweep=18*slam)
            # Each downstroke heaves the body up a little.
            heave=2.5*min(1,max(0,-v/4))*weight
            p['body']['position'][1]+=-7*crouch+7*raise_-9*slam+heave
            p['body']['rotation'][0]+=-8*raise_+10*slam
            p['neck_base']['rotation'][0]-=8*raise_+9*slam
            fan(p,t,.35*weight)
            plant(p,feet)
            # The slam curls the hand up off the rock first; only then does the shoulder give.
            if slam>0:
                for joint in ('wing_wrist','wing_elbow','wing_shoulder'):splay(p,0,True,joint)
            return locomotion_mix(p,flight(0),ease(t,1.2,2.5))
        if name=='pounce':return pounce(t)
        if name=='stampede':
            p=copy.deepcopy(neutral);targets={k:v.copy() for k,v in feet.items()}
            if t<1.2:
                scrape=math.sin(math.pi*(t%.6)/.6)**2
                targets['right'][1]+=7*scrape;targets['right'][2]+=24*scrape
                p['body']['position'][1]-=4*scrape
                head(p,-24*scrape,0,-8*scrape)
                spread(p,.3,.35*scrape);fan(p,t,.5*scrape)
            elif t<=2.7:
                run=ease(t,1.2,1.4);cycle=(t-1.2)*math.tau*2.6
                p['body']['position'][1]+=3*math.sin(cycle*2)*run-3*run
                p['body']['rotation'][0]+=9*run
                p['neck_base']['rotation'][0]-=4*run
                for label,phase in (('left',0),('right',math.pi)):
                    step=cycle+phase
                    targets[label][2]+=19*math.sin(step)*run
                    targets[label][1]+=10*max(0,math.cos(step))*run
                spread(p,-.1,.55*run);fan(p,t,.7*run)
            else:
                skid=pulse(t,2.7,2.85,3.65,4.2)
                p['body']['rotation'][0]-=20*skid;p['body']['position'][1]-=7*skid
                for label in targets:
                    targets[label][2]-=15*skid
                p['tail_fan']['rotation'][0]+=24*skid
                spread(p,.65,.95*skid,-12*skid)
                head(p,-30*pulse(t,3.65,3.85,3.95,4.2),0,0)
            plant(p,targets)
            if 2.7<t<2.9:return locomotion_mix(motion(name,2.7),p,ease(t,2.7,2.9))
            return p
        raise ValueError(name)
    # Follow-through: in these clips the loose feathers trail the bones that carry them, swinging
    # on after a snap or a shove instead of moving rigidly with the head and body.
    loose=lambda part:[b['name'] for b in bones if part(b['name']) and b['cubes']]
    FEATHERS=[Spring(loose(lambda n:n.startswith('crest_plume_')),2.8,.25,turn=.7,limit=14),
        Spring(loose(lambda n:'_cheek_blue_' in n),3.4,.3,turn=.6,limit=10),
        Spring(loose(lambda n:'_cheek_' in n and '_blue_' not in n),3.8,.35,turn=.6,limit=8),
        Spring(['tail_fan'],1.6,.32,turn=.9,shove=.7,limit=9)]
    FOLLOW={'hunched':FEATHERS,'bite':FEATHERS,'bite_leap':FEATHERS,'swing_left':FEATHERS,'swing_right':FEATHERS,'scream':FEATHERS,'wing':FEATHERS,'wing_spiral':FEATHERS,'pounce':FEATHERS}
    shapes={b['name']:shape(b) for b in bones if b.get('cubes')}
    def follow(frames,times,springs,loop):
        worlds=[matrices(p) for p in frames]
        # A loop's swing fades out over its last moments, so the seam meets its start at rest.
        fade=[1-ease(t,times[-1]-.6,times[-1]) if loop else 1 for t in times]
        for spring in springs:
            for n in spring.names:
                parent=by[n]['parent'];parts=[c for c in subtree(n) if c in shapes]
                carrier=np.array([w[parent][:3,:3] for w in worlds])
                assert all(np.allclose(R.T@R,np.eye(3),atol=1e-6) for R in carrier),(n,'carrier must not be scaled')
                pivots=np.array([point(w,n,by[n]['pivot']) for w in worlds])
                centres=[np.concatenate([(shapes[c]@w[c].T)[:,:3] for c in parts]).mean(axis=0) for w in worlds]
                lever=np.array([R.T@(c-x) for R,c,x in zip(carrier,centres,pivots)])
                swing=lag(times,carrier,pivots,lever,spring)
                for k,p in enumerate(frames):
                    # Turn the part's orientation; its own scale channel stays as it is.
                    R=carrier[k]@rotmat(swing[k]*fade[k])@carrier[k].T@worlds[k][n][:3,:3]@np.diag(1/p[n]['scale'])
                    rig=Pose(SimpleNamespace(bones=bones));rig.world=worlds[k];rig.put(n,R,pivots[k])
                    for key,v in rig.channels[n].items():p[n][key]=np.array(v,dtype=float)
    # Reworked clips drop the keys a straight line between their neighbours already gives, within
    # these tolerances (degrees, model pixels, scale). The bones the server's sockets ride on keep
    # every key, so hits land exactly where the export says.
    THIN={'hunched','bite','bite_leap','swing_left','swing_right','scream','wing','wing_spiral','pounce'};TOLERANCE={'rotation':.02,'position':.005,'scale':.0002}
    exact=set()
    for n,_ in landmarks.values():
        while n:exact.add(n);n=by[n].get('parent')
    animations={}; sockets={}
    for name,(duration,loop) in DEFINITIONS.items():
        if args.only and name not in args.only:continue
        frames=[]; points=[]
        frame_ticks=[i for i in range(round(duration*KEY_FPS)+1)
            if i%2==0 or any(a<=i/KEY_FPS<=b for a,b in FAST_KEYS.get(name,[]))]
        posed=[]
        for frame in frame_ticks:
            t=frame/KEY_FPS;p=motion(name,t)
            if name in AIRBORNE:lengthen_neck(p)
            crest(p,crest_amount(name,t));posed.append(p)
        if name in FOLLOW:follow(posed,[i/KEY_FPS for i in frame_ticks],FOLLOW[name],loop)
        # The Thunderclap's and Raptor Strike's hands and feathers meet the rock over a few
        # frames, not in one contact-solve jump; the shoulders still drive the clap down on its tick.
        if name in ('wing','wing_spiral','pounce'):limit_rate(posed,[i/KEY_FPS for i in frame_ticks],
            [f'{side}_wing_{joint}' for side in ('left','right') for joint in ('elbow','wrist')]+
            [f'{side}_primary_{i:02}' for side in ('left','right') for i in range(10)]+
            ([b['name'] for b in bones if '_root_' in b['name'] or '_secondary_' in b['name']] if name=='wing_spiral' else []),
            # The mantle's wings settle onto the rock slowly, as a giant's would.
            350 if name=='pounce' else 600)
        for frame,p in zip(frame_ticks,posed):
            t=frame/KEY_FPS
            for spot in grounded(name,t):lift_tail(p,*spot)
            # Java linearly interpolates Euler channels. Unwrap every bone so
            # the IK's equivalent +/-180 representations never make a full spin.
            if frames:
                for n in p:
                    a=frames[-1][n]['rotation'];p[n]['rotation']=a+(p[n]['rotation']-a+180)%360-180
            frames.append(p)
            m=matrices(p)
            if frame%(KEY_FPS//20)==0:
                points.append({key:[round(float(v),5) for v in point(m,n,v)*SCALE] for key,(n,v) in landmarks.items()})
        channels={}
        for b in bones:
            ch={}
            for k,default in [('rotation',[0,0,0]),('position',[0,0,0]),('scale',[1,1,1])]:
                values=[[round(float(v),4) for v in f[b['name']][k]] for f in frames]
                if all(v==default for v in values):continue
                if all(v==values[0] for v in values):ch[k]=values[0];continue
                kept=range(len(values)) if name not in THIN or b['name'] in exact else thin(frame_ticks,values,TOLERANCE[k])
                ch[k]={f'{frame_ticks[i]/KEY_FPS:.4f}':values[i] for i in kept}
            channels[b['name']]=ch
        animations[name]={'loop':loop,'animation_length':duration,'bones':channels}
        sockets[name]=points
        print(f'Authored {name}: {duration}s',flush=True)
    # The offline study uses unreflected Cartesian X. Gecko mirrors geometry,
    # pivots and position X while keeping its rotation parser signs unchanged.
    # Conjugate each authored rotation by that reflection (Y/Z sign reversal)
    # so the runtime mesh is exactly the reflected approved study, including
    # cube rotations. Source records, dimensions, pivots, UVs and atlas stay intact.
    runtime_geo=copy.deepcopy(geo)
    for bone in runtime_geo['minecraft:geometry'][0]['bones']:
        for item in [bone]+bone.get('cubes',[]):
            if 'rotation' in item:item['rotation']=[item['rotation'][0],-item['rotation'][1],-item['rotation'][2]]
        bone.setdefault('cubes',[])
    for animation in animations.values():
        for channel in animation['bones'].values():
            if 'rotation' not in channel:continue
            values=channel['rotation']
            if isinstance(values,list):channel['rotation']=[values[0],-values[1],-values[2]]
            else:
                for key,v in values.items():values[key]=[v[0],-v[1],-v[2]]
    if args.only:
        # The other clips stay exactly as installed, already in Gecko's basis.
        installed=json.loads((ASSETS/'geckolib/animations/storm_roc.animation.json').read_text())['animations']
        installed_sockets=json.loads((ASSETS/'storm_roc_sockets.json').read_text())
        animations={k:animations.get(k) or installed['animation.storm_roc.'+k] for k in DEFINITIONS}
        sockets={k:sockets.get(k) or installed_sockets[k] for k in DEFINITIONS}
    outputs={
        ASSETS/'geckolib/models/storm_roc.geo.json':packed(runtime_geo),
        ASSETS/'geckolib/animations/storm_roc.animation.json':packed({'format_version':'1.8.0','animations':{'animation.storm_roc.'+k:v for k,v in animations.items()}}),
        ASSETS/'storm_roc_sockets.json':packed(sockets),
        ASSETS/'storm_roc_sparks.json':sparks(),
        ASSETS/'textures/entity/storm_roc_sparks.png':(HERE/'candidate/storm_roc_sparks.png').read_bytes(),
        ASSETS/'textures/entity/storm_roc.png':(MODEL/'storm_roc.png').read_bytes(),
        ASSETS/'textures/entity/storm_roc_glowmask.png':glowing(MODEL/'storm_roc.png',MODEL/'storm_roc_glowmask.png'),
    }
    # A white texel lets the custom lightning/tornado meshes carry their own palette.
    import io
    white=Image.new('RGBA',(1,1),(255,255,255,255));buf=io.BytesIO();white.save(buf,format='PNG')
    outputs[ASSETS/'textures/misc/roc_white.png']=buf.getvalue()
    for path,data in outputs.items():
        if args.check:
            assert path.exists() and path.read_bytes()==data, f'Runtime asset drift: {path}'
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print(('Checked' if args.check else 'Installed')+f' {len(outputs)} Storm Roc assets; {len(animations)} poses, preserved study with Gecko basis conversion, unchanged atlas, 1.25x sockets.')
if __name__=='__main__':main()
