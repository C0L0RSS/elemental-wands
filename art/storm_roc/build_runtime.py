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

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
# The chunky block model and its pixel atlas ride the study's skeleton and clips unchanged.
MODEL = ROOT / 'art/storm_roc_mod/candidate'
SCALE = 1.25 / 16
KEY_FPS = 80  # max pose rate; ordinary motion is 40Hz, server sockets stay 20Hz.
FAST_KEYS = {'scream':[(1.1,1.2)], 'peck':[(.5,.6)], 'wing':[(.9,1.0)], 'takeoff':[(1.1,1.2)],
    'pounce':[(1.2,1.35),(2.05,2.2)], 'call':[(1.9,2.1)]}
# Block-space clearances at 1.25x; preserve the original model-space wing solve.
WING_REST, WING_SLAP = -.125, .7
# The idle and flight loops play their authored motion this much slower: a giant moves slowly.
SLOW = 1.5
DEFINITIONS = {'perched':(round(7.2*SLOW,3),True), 'call':(3,False), 'land':(.8,False),
    'flight':(round(4.8*SLOW,3),True), 'hunched':(round(4.8*SLOW,3),True), 'dive':(3,False), 'stuck':(6,False),
    'peck':(1.8,False), 'swing_left':(1.8,False), 'swing_right':(1.8,False),
    'scream':(2.2,False), 'wing':(2,False), 'pounce':(3.2,False),
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

def glowing(texture, mask):
    """GeckoLib's glow layer draws the mask's own colours: the painted veins wherever the mask is lit."""
    t = np.array(Image.open(texture).convert('RGBA')); m = np.array(Image.open(mask).convert('RGBA'))
    out = np.zeros_like(t); lit = m[...,3] > 0
    out[lit] = t[lit]; out[...,3] = m[...,3]
    import io; buf = io.BytesIO(); Image.fromarray(out).save(buf, format='PNG'); return buf.getvalue()

def main():
    check = argparse.ArgumentParser()
    check.add_argument('--check', action='store_true')
    args = check.parse_args()
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
    AIRBORNE = {'flight', 'land', 'dive', 'takeoff', 'hop', 'pounce'}
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
    def hunched(time):
        t=time/SLOW;p=copy.deepcopy(neutral);phase=math.tau*t/4.8
        p['body']['position']+=np.array([2*math.sin(phase),1.2*math.sin(2*phase),0])
        p['body']['rotation'][2]+=2*math.sin(phase)
        p['neck_base']['rotation'][1]+=7*math.sin(phase)
        p['neck_base']['rotation'][0]+=2.5*math.sin(2*phase)
        cock=curve(t,[(0,0),(1.3,0),(1.45,-23),(2.05,-23),(2.25,0),(4.8,0)])
        head(p,2*math.sin(phase),9*math.sin(phase),cock)
        p['tail_fan']['rotation'][1]-=10*math.sin(phase)
        plant(p,feet);return p
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
        if name=='hunched':return REST
        if name=='stuck':return stuck_crest(t)
        if name=='dive':return -ease(t,.1,.6)*(1-ease(t,2.65,3))+stuck_crest(0)*ease(t,2.65,3)
        if name=='hop':return stuck_crest(0)-(1+stuck_crest(0))*pulse(t,0,.3,.85,1.2)
        if name=='peck':return REST+(1-REST)*pulse(t,0,.4,.6,1)
        if name.startswith('swing'):return REST+.6*pulse(t,0,.5,1.3,1.8)
        if name=='scream':return REST+(1-REST)*pulse(t,0,.6,1.85,2.2)
        if name=='wing':return REST+(1-REST)*pulse(t,0,.5,1.2,1.8)
        if name=='stampede':return REST+(1-REST)*pulse(t,0,.4,3.65,4.2)
        if name=='takeoff':return (REST+.5*pulse(t,0,.4,.9,1.2))*(1-ease(t,1.2,2.5))-ease(t,1.2,2.5)
        if name=='pounce':
            air=ease(t,1.2,1.35)*(1-ease(t,2.05,2.2))
            return (REST+(1-REST)*pulse(t,.1,.4,1,1.2))*(1-air)-.6*air
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
        if name=='pounce':return [(ground,1-ease(t,1.2,1.35)*(1-ease(t,2.05,2.2)))]
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
        if name=='peck':
            # The first maximum extension is exactly the server's tick 12.
            source_t=curve(t,[(0,0),(.4,.3),(.5,.3),(.6,.5),(.72,.5),(.9,.85),(1.8,1.6)])
            p=pose('peck',source_t)
            coil=pulse(t,0,.4,.5,.6);strike=pulse(t,.5,.6,.72,.95)
            p['body']['position'][1]-=5*coil
            p['body']['position'][2]+=7*coil-5*strike
            p['neck_base']['rotation'][0]-=16*coil
            head(p,-26*coil+14*strike)
            spread(p,.65,.45*coil+.2*strike)
            p['tail_fan']['rotation'][0]+=12*coil-9*strike
            p['lower_beak']['rotation'][0]=12*pulse(t,.4,.5,.75,1)
            plant(p,feet);return p
        if name.startswith('swing'):
            p=copy.deepcopy(neutral);side=1 if name.endswith('left') else -1
            a=curve(t,[(0,0),(.55,-48),(.8,-48),(1.15,48),(1.3,55),(1.8,0)])*side
            twist=curve(t,[(0,0),(.6,-10),(.8,-10),(1.15,10),(1.35,13),(1.8,0)])*side
            p['body']['rotation'][1]+=twist
            p['neck_base']['rotation'][1]+=a*.75
            p['neck_lower']['rotation'][1]+=a*.25
            p['tail_fan']['rotation'][1]-=a*.55
            spread(p,.25,.6*pulse(t,0,.5,1.3,1.8))
            p['lower_beak']['rotation'][0]=15*pulse(t,.4,.65,1.25,1.65)
            low=pulse(t,.45,.8,1.15,1.65)
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
            p=copy.deepcopy(neutral)
            breath=pulse(t,0,.85,1.1,1.2);blast=pulse(t,1.1,1.2,1.85,2.2)
            p['body']['position'][1]+=8*breath-4*blast
            p['body']['rotation'][0]-=12*breath-8*blast
            p['neck_base']['rotation'][0]-=21*breath+4*blast
            spread(p,.5*breath-.25*blast,.8*breath+.85*blast)
            head(p,-43*breath+2*math.sin((t-1.2)*60)*blast)
            p['lower_beak']['rotation'][0]=12*breath+48*blast
            p['tail_fan']['rotation'][0]+=10*breath-8*blast
            plant(p,feet);return p
        if name in ('wing','takeoff'):
            p=copy.deepcopy(neutral);hit=1 if name=='wing' else 1.2
            if name=='wing':
                # One slow, heavy flap as it rises tall, wings high overhead, then the slam at
                # tick 20, a follow-through and a slow recovery stroke before they fold.
                strokes=[(0,0),(.32,.55),(.6,-.3),(.86,1.15),(.9,1.15),(1,-1.1),
                    (1.12,-1.2),(1.5,.35),(1.8,-.1),(2,0)]
                weight=ease(t,0,.18)*(1-ease(t,1.55,2))
            else:
                # A crouch with the wings opening, the huge downbeat at tick 24, then it beats away.
                strokes=[(0,0),(.45,.3),(1.1,1.15),(1.2,-1.1),(1.65,.85),(2.1,-.8),(2.5,0)]
                weight=ease(t,.15,.6)
            crouch=pulse(t,0,.2,.3,.5) if name=='wing' else pulse(t,0,.3,.45,.7)
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
            if name=='takeoff':return locomotion_mix(p,flight(0),ease(t,1.2,2.5))
            return p
        if name=='pounce':
            p=copy.deepcopy(neutral)
            crouch=ease(t,0,.45);wiggle=math.sin(t*math.tau*3)*pulse(t,.2,.4,1.05,1.2)
            p['body']['position'][1]-=12*crouch
            p['body']['position'][0]+=4*wiggle;p['body']['rotation'][2]+=5*wiggle
            p['tail_fan']['rotation'][1]+=32*wiggle
            p['tail_fan']['rotation'][0]+=12*crouch
            p['neck_base']['rotation'][0]-=6*crouch
            spread(p,.6,.35*crouch);plant(p,feet)
            if t<=1.2:return p
            air=pose('grab',1.2);flap(air,t,lambda x:curve(x,[(1.2,1),(1.6,-1),(2.2,.8)]))
            p=locomotion_mix(p,air,ease(t,1.2,1.35))
            land=copy.deepcopy(neutral)
            settle=1-ease(t,2.25,3.2)
            land['body']['position'][1]-=12*settle;land['body']['rotation'][0]+=9*settle
            land['neck_base']['rotation'][0]-=8*settle
            spread(land,.55,.9*settle);plant(land,feet)
            return locomotion_mix(p,land,ease(t,2.05,2.2))
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
    animations={}; sockets={}
    for name,(duration,loop) in DEFINITIONS.items():
        frames=[]; points=[]
        frame_ticks=[i for i in range(round(duration*KEY_FPS)+1)
            if i%2==0 or any(a<=i/KEY_FPS<=b for a,b in FAST_KEYS.get(name,[]))]
        for frame in frame_ticks:
            t=frame/KEY_FPS;p=motion(name,t)
            if name in AIRBORNE:lengthen_neck(p)
            crest(p,crest_amount(name,t))
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
                ch[k]=values[0] if all(v==values[0] for v in values) else {f'{i/KEY_FPS:.4f}':v for i,v in zip(frame_ticks,values)}
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
    outputs={
        ASSETS/'geckolib/models/storm_roc.geo.json':packed(runtime_geo),
        ASSETS/'geckolib/animations/storm_roc.animation.json':packed({'format_version':'1.8.0','animations':{'animation.storm_roc.'+k:v for k,v in animations.items()}}),
        ASSETS/'storm_roc_sockets.json':packed(sockets),
        ASSETS/'storm_roc_sparks.json':(HERE/'candidate/storm_roc_sparks.json').read_bytes(),
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
