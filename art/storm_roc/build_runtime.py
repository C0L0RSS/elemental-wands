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
# The perched idle's loop and its head's looks: aside, a slow turn the other way, a one-eyed cock down
# at the summit (crest up), forward through the rouse and the stretch, one look down after it. Each
# turn takes PERCH_TURN times a brawl twitch (a 76-degree turn about half a second), as befits so
# heavy a bird, and lands with only a small shiver. Its yaw stays within 45 degrees, so the game's
# gaze has room to turn it toward a challenger.
PERCHED, PERCH_TURN = 10.8, 2.6
PERCH_GLANCES = ((.55,(2,34,5)),(1.9,(-5,-40,-12)),(3.0,(10,-20,26),2),(3.75,(0,0,0)),
    (5.75,(-6,0,0)),(7.95,(0,0,0)),(8.95,(5,26,6)),(9.75,(0,0,0)))
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
# The hop between spires is the server's flight (StormRocRules: AIR_CYCLE - FLY_AT - LAND_LEAD
# ticks). The server holds it on its spire until SPIRE_PUSH (tick 8) while it turns onto its way out,
# then flies it out to the side of the next spire and banks it round onto it (RocApproach): the turn
# runs from about 2.3 s to 3.1 s. As the clip ends it flares into its landing, LAND_LEAD before arriving.
SPIRE_PUSH, SPIRE_FLIGHT = .4, 3.2
# The landing touches down at LAND_TOUCH (LAND_LEAD) and settles as its call begins (CALL_AT later).
LAND_TOUCH, LAND = .8, 2.5
# Its legs start reaching for the spire LEG_REACH before the landing begins (StormRocRules.LAND_REACH),
# in the last stretch of the hop or the takeoff, and are fully out 0.30 into the landing.
LEG_REACH = .45
# The call's bolt leaves its bill at CALL_VOLLEY (tick 10); the large strikes it calls land 1.5 s
# later (LARGE_WARNING), then every half second (BOLT_GAP).
CALL_BOLT, CALL_STRIKES = .5, (2.0, 2.5, 3.0)
# The fight opens with it summoning the summit's first tornado from its spire: it screams the storm
# down at SUMMON_CALL (tick 34, StormRocRules.SUMMON_CALL), when the bolt strikes the funnel's spot
# and the funnel starts touching down, drives the wind at it with three heavy downbeats, and is
# back on its perch's first frame at SUMMON (tick 88, StormRocRules.SUMMON_TICKS).
SUMMON, SUMMON_CALL, SUMMON_BEATS = 4.4, 1.7, (2.2, 2.8, 3.4)
# Raptor Strike: it stalks low and pushes off at 1.60 (tick 32); the server carries it flat and
# fast on a low arc, and its talons strike its prey at 2.60 (tick 52).
POUNCE_LAUNCH, POUNCE_IMPACT = 1.6, 2.6
# The takeoff from the summit floor as the brawl ends: its feet leave the rock at TAKEOFF_PUSH
# (tick 24, StormRocRules.TAKEOFF_PUSH) as its first downbeat sweeps through level and the gust
# blasts everyone back. The server lifts it slowly off the rock, then flies it out past its next spire
# and banks it round onto it (RocApproach; the turn runs from about 3.4 s to 4.65 s). The clip ends
# in the flight's neutral glide at TAKEOFF (TAKEOFF_TICKS - LAND_LEAD), where the landing's flare,
# or the phase change's flight, carries on with no crossfade.
TAKEOFF_PUSH, TAKEOFF = 1.2, 4.8
# Thunder Charge: it rakes the rock three times, slowly, as befits its size, and launches at 2.20
# (tick 44). Its run lasts as long as the server's charge does, so it is three clips: 'stampede'
# ends one stride into the run (STAMPEDE_LOOP, tick 53); 'stampede_run' is that one stride, 9 ticks
# at STRIDE_HZ, looped; 'stampede_skid' takes over on a stride boundary and skids. The server keeps
# these ticks in StormRocRules (STAMPEDE_RUN_AT, STAMPEDE_STRIDE, STAMPEDE_AFTER).
STAMPEDE_RUN, STRIDE_HZ = 2.2, 20/9
STAMPEDE_LOOP = round(STAMPEDE_RUN+1/STRIDE_HZ, 4)
# Where the skid clip starts in the charge's own time: any stride boundary after the loop.
STAMPEDE_SKID_AT = round(STAMPEDE_LOOP+1/STRIDE_HZ, 4)
STAMPEDE_RAKES = (.25, .85, 1.45)
# The dive: the server holds it on its spire until DIVE_PUSH (StormRocRules.DIVE_PUSH, tick 8) while
# it crouches, then carries it up to its apex over its mark (at 1.80 in phase one, 2.00 in the chain
# smash) and down onto it, braking over the last few tenths; its bill spears into the rock at 3.00
# (DIVE_TICKS, tick 60), where stuck begins.
DIVE_PUSH = .4
# The server's path, for the checks that its bill stays above the rock until it strikes: its apex
# over the mark (time, height in blocks) in phase one and the chain smash, from which it comes down
# along a smoothstep to land at 3.00; and the chain hop's lowest arc (5 blocks plus 0.3 per block
# hopped), a sine over the hop's 1.20.
DIVE_APEXES, HOP_LOWEST = ((1.8,64),(2.0,42)), 5
# Stuck: its bill stays pinned in the rock until STUCK_FREE, when it rips free; its guard follows
# at 6.00 (STUCK_TICKS). The chain smash leaves it buried SMASH_PAUSE (tick 8) after each slam but
# the last, then its hop takes over from that frame.
STUCK_FREE, SMASH_PAUSE = 5.0, .4
# While stuck its splayed wings rest on the rock; this is how far their lowest feathers lift off it
# (blocks above WING_REST), the right wing a moment behind the left: a bounce at the impact, one slow
# lift and heavy slap as it heaves, a shove as it wrenches, then raised high as it gathers itself to
# rip free. The validator checks the contact solve against it.
STUCK_SLAPS = ((0,0),(.1,.35,'snap'),(.32,0,'drop'),(1.5,0),(1.95,1.6),(2.02,1.65),(2.14,0,'hit'),
    (3.3,0),(3.6,1.0),(3.68,1.05),(3.8,0,'hit'),(4.4,0),(4.95,2.2),(STUCK_FREE,2.25))
def stuck_slap(t, label):
    return keys(t-(.06 if label=='right' else 0), STUCK_SLAPS)
FAST_KEYS = {'scream':[(1.45,2.2)], 'swing_left':[(1.2,1.95)], 'swing_right':[(1.2,1.95)], 'bite':[(1.95,2.7)], 'bite_leap':[(1.8,3.25)], 'wing':[(.45,1.1),(2.4,3.0)], 'wing_spiral':[(.5,3.6)], 'takeoff':[(1.1,1.2)],
    'pounce':[(1.3,1.75),(2.35,2.8)], 'spire_flight':[(.3,1.0),(1.3,SPIRE_FLIGHT)], 'land':[(.6,1.0)], 'call':[(0,.6),(1.75,2.1),(2.45,2.7),(2.85,3.0)], 'summon':[(1.0,1.9),(2.0,3.5)], 'stampede':[(.2,2.1),(2.05,2.65)], 'stampede_run':[(0,.45)], 'stampede_skid':[(0,.3)],
    'hunched':[(.5,1.),(8.5,9.)]+[(g[0],g[0]+.35) for g in GUARD_GLANCES]}
FAST_KEYS['takeoff']=[(.3,.6),(.95,3.6)]
FAST_KEYS['dive']=[(.05,.25),(.4,.6),(.95,1.15),(1.5,1.95),(2.5,3.0)]
FAST_KEYS['stuck']=[(0,.3),(2.0,2.2),(3.65,3.85),(4.9,5.75)]
FAST_KEYS['hop']=[(0,.45),(.85,1.2)]
# Block-space clearances at 1.25x; preserve the original model-space wing solve.
WING_REST = -.125
# The idle and flight loops play their authored motion this much slower: a giant moves slowly.
SLOW = 1.5
DEFINITIONS = {'perched':(PERCHED,True), 'call':(3,False), 'summon':(SUMMON,False), 'land':(LAND,False),
    'flight':(round(4.8*SLOW,3),True), 'spire_flight':(SPIRE_FLIGHT,False), 'hunched':(9.6,True), 'dive':(3,False), 'stuck':(6,False),
    'bite':(3.9,False), 'bite_leap':(4.4,False), 'swing_left':(2.6,False), 'swing_right':(2.6,False),
    'scream':(2.9,False), 'wing':(4.0,False), 'pounce':(4.0,False),
    'stampede':(STAMPEDE_LOOP,False), 'stampede_run':(round(1/STRIDE_HZ,4),True), 'stampede_skid':(1.9,False),
    'takeoff':(2.5,False), 'hop':(1.2,False)}
DEFINITIONS['takeoff']=(TAKEOFF,False)

def continuous(prev, cur):
    """cur's Euler angles in the form nearest prev's: wrapped by 360s, or the same turn's twin
    (x+180, 180-y, z+180), or at gimbal lock any split of the one angle left free. The IK and the
    world-space wing blends can return any of them from one key to the next."""
    options=[cur,np.array([cur[0]+180,180-cur[1],cur[2]+180])]
    if abs(math.cos(math.radians(cur[1])))<1e-3:
        R=rotation(cur)
        for s in (1,-1):
            e=np.array([prev[0],cur[1],cur[2]+s*(prev[0]-cur[0])])
            if np.allclose(rotation(e),R,atol=1e-6):options.append(e)
    wrap=lambda e:prev+(e-prev+180)%360-180
    return min((wrap(e) for e in options),key=lambda e:float(np.abs(e-prev).sum()))

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
    # The neck lengthens by how far its skin has shrunk toward the study's flying neck, so a clip
    # blending from a perch into flight grows its neck as it goes.
    AIRBORNE = {'flight', 'spire_flight', 'land', 'dive', 'takeoff', 'hop'}
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
    def fold_wings(p, spread, weight):
        """Opens folded wings toward spread ones (weight 1 open), or folds them, as a bird's do.
        weight(n) is each part's own, so the hands can lead. Every part, arm bone and feather alike,
        turns in world space the short way: the arm swings in and the feathers, which keep pointing
        back, tip onto their side (about 90 degrees; the primaries a little more). A blend in each
        feather's own frame instead carries it through its arm bone's near half-turn, as the study
        folds them, and the feathers and hand flip over on the way. Each part's root stays where
        its blended parent carries it."""
        names=[n for n in p if any(s in n for s in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries'))]
        target=copy.deepcopy(p)
        for n in names:target[n]=spread[n]
        a,b=matrices(p),matrices(target)
        rig=Pose(SimpleNamespace(bones=bones));rig.world=a.copy()
        for n in [bone['name'] for bone in bones if bone['name'] in names]:
            w=weight(n);pivot=np.array(by[n]['pivot']);parent=by[n].get('parent')
            # Unmoved parts keep their own channels, as stored, under unmoved parents.
            if w<=0 and np.allclose(rig.world[parent],a[parent]):continue
            la,lb=np.linalg.inv(a[parent])@a[n],np.linalg.inv(b[parent])@b[n]
            at=(la[:3,:3]@pivot+la[:3,3])*(1-w)+(lb[:3,:3]@pivot+lb[:3,3])*w
            W=rig.world[parent]
            rig.put(n,orient(a[n][:3,:3],b[n][:3,:3],w),W[:3,:3]@at+W[:3,3])
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
        # Roll each shoulder down until that wing's lowest feather meets the rock, then lift it
        # off and slap it down again as stuck's STUCK_SLAPS say: the wings splay out on the ground.
        # With another joint, only the wing beyond it bends up off the rock.
        parts={label:{n:v for n,v in wing_cubes[label].items() if n in subtree(f'{label}_{joint}')} for label in ('left','right')}
        base={label:p[f'{label}_{joint}']['rotation'][2] for label in ('left','right')}
        lo={'left':-60.,'right':-60.};hi={'left':65.,'right':65.}
        targets={label:(WING_REST+(0 if contact_only else stuck_slap(t,label)))/SCALE for label in ('left','right')}
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
    def perched(t):
        """Its idle on a spire. Slow, uneven breaths and its weight rocking from foot to foot, held
        a while on each, never stop. Over them its head holds dead still, then turns heavily from look
        to look, landing with a small shiver: aside, the other way, a one-eyed cock down at the summit.
        It rouses,
        crest flaring, shaking its whole plumage, then stretches slowly as a great eagle does: its wings
        swing out from its sides, unfold from the shoulder out and rise half open over its back, hold,
        and fold back in, the hands first. The game resumes this loop where it left off."""
        P=PERCHED
        p=copy.deepcopy(perch);body=p['body']
        breath=sum(d*envelope(t,a,1.0,.2,1.1,'ease','ease') for a,d in ((.2,1),(2.9,.75),(5.2,.6),(7.9,1.1)))
        # Its weight rolls onto its left foot, then its right, holding each a while.
        weight=keys(t,[(0,0),(1.0,0),(2.3,1),(4.0,1),(5.3,-1),(8.7,-1),(9.9,0),(P,0)])
        rouse=envelope(t,4.35,.12,.1,.6,'snap','settle');flutter=shake(t,4.4,.6,9,2.5)
        # The stretch: wings out and up over its back, held, and home; its neck reaches as it lifts.
        reach=keys(t,[(0,0),(5.6,0),(6.5,1),(7.1,1),(8.1,0),(P,0)])
        flick=envelope(t,1.25,.1,.12,.7,'snap','settle')+.8*envelope(t,5.0,.1,.1,.6,'snap','settle')
        body['position']+=np.array([3*weight,1.4*breath-.8*weight*weight-2*reach,1.5*reach])
        body['rotation']+=np.array([-1.6*breath+2*flutter+6*reach,0,2*weight+1.5*flutter])
        p['neck_base']['rotation'][0]+=-1.2*breath-5*reach
        # Its head holds its own level and snaps between looks; the rouse shivers through it.
        look=twitch(t,PERCH_GLANCES,P,spark=1.2,hz=12,duration_scale=PERCH_TURN)
        head(p,look[0]-2*breath,look[1],look[2]+3*flutter)
        # Wings out of the fold as a bird opens them: they swing out from its sides into a Z laid flat,
        # unfold from the shoulder out, the hands last, and rise; then back the same way, hands first.
        stroke=lambda x:keys(x,[(0,0),(5.75,0),(6.55,.7),(7.05,.7),(7.7,.15),(P,0)])
        air=pose('flight',0);flap(air,t,stroke,lag=.12)
        order=lambda n:2 if n in HANDS else 1 if n in FOREARMS else 0
        opening=copy.deepcopy(air)
        joint_mix(opening,flat_fold,lambda n:1-keys(t,[(5.85+.08*order(n),0),(6.5+.08*order(n),1),(7.05-.08*order(n),1),(7.7-.08*order(n),0)]))
        fold_wings(p,opening,lambda n:keys(t,[(5.45+.05*order(n),0),(6.0+.05*order(n),1),(7.6-.05*order(n),1),(8.3-.05*order(n),0)]))
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_shoulder']['rotation'][2]+=side*(1.5*breath+6*rouse+3*flutter)
        p['tail_fan']['rotation']+=np.array([5*breath+8*rouse-4*reach,18*flick-5*weight,4*flick])
        # A soft click of the bill as it settles from the stretch.
        p['lower_beak']['rotation'][0]=7*envelope(t,8.7,.06,.04,.08,'snap','whip')+5*envelope(t,8.9,.06,.03,.08,'snap','whip')
        plant(p,perch_feet)
        return p
    def flight_base(time,slow=SLOW):
        """Existing aerial motion used by the dive and the shared landing/takeoff handoff.
        Keep those clips independent while the cruising flight is reworked."""
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
    flight_rest=flight_base(0)
    def flight(time):
        """Two unequal, heavy strokes, then a living glide. Shoulders commit first, hands
        and primaries lag behind, and the chest rises only after the wings have pushed.
        Recovery takes longer than the push; the head searches the rock below."""
        period=DEFINITIONS['flight'][0];t=time%period
        stroke=lambda x:keys(x%period,[(0,0),(.25,.08),(.95,.96),(1.10,.92),
            (1.55,-.68,'hit'),(1.82,-.59),(2.35,.12),(3.0,.84),(3.14,.80),
            (3.60,-.60,'hit'),(3.90,-.50),(4.65,0),(period,0)])
        p=pose('flight',0)
        flap(p,t,stroke,lag=.13)
        # Sink as the wings unload, then lift after the push. The second effort is weaker.
        lift=keys(t,[(0,0),(.95,-3.5),(1.35,-2.5),(1.95,6.5),(2.55,1.0),
            (3.0,-3),(3.42,-2),(4.02,5.0),(4.85,.5),(5.8,-.7),(6.8,0),(period,0)])
        pitch=keys(t,[(0,0),(.95,-3.5),(1.35,-3),(1.92,2.8),(2.5,.4),
            (3.0,-2.8),(3.4,-2.4),(4.0,2.0),(4.8,0),(period,0)])
        p['body']['position'][1]+=lift
        p['body']['rotation'][0]+=pitch
        # The neck absorbs the chest's effort; head orientation is solved in world space.
        p['neck_base']['rotation'][0]-=.35*pitch
        p['tail_fan']['rotation'][0]+=-.65*pitch+keys(t,[(0,0),(1.55,0),(1.95,-2),
            (2.5,1),(3.6,0),(4.05,-1.6),(4.75,.7),(5.5,0),(period,0)])
        glide=envelope(t,4.25,.65,1.0,1.1,'ease','ease')
        trim=keys(t,[(0,0),(4.5,0),(5.0,1),(5.55,.4),(6.05,-.65),(6.8,0),(period,0)])
        p['tail_fan']['rotation'][1]+=3.5*trim
        # Small hand corrections and irregular feather flex keep the glide alive.
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_wrist']['rotation'][2]+=side*(1.4*trim+glide*noise(t,period,21+side,(2,3,5)))
            for i,f in enumerate(FINGERS):
                flex=glide*noise(t,period,31+i+side*7,(2,3,5))
                p[f'{label}_primary_{f:02}']['rotation'][0]+=1.8*flex
                p[f'{label}_primary_{f:02}']['rotation'][2]+=side*.9*flex
        # Watch the ground in held glances, with the quick turns and little settling
        # shiver used by the brawl. In game, occasional player focus takes over these looks.
        look=twitch(t,[(.25,(22,-24,-5)),(1.35,(34,14,4)),(2.65,(23,30,6)),
            (4.2,(38,-20,-6)),(5.6,(28,22,4)),(6.7,(0,0,0))],spark=1.0,hz=12,duration_scale=2.70)
        head(p,-4+look[0]+.35*trim,look[1]+1.5*trim,look[2]-.3*trim)
        # Airborne legs stay tucked. The grounded locomotion blend re-solves them
        # as planted feet and flips their knees at the beginning/end of this loop.
        return mix(flight_rest,p,ease(t,0,.3)*(1-ease(t,6.85,period)))
    SPIRE_OFF=SPIRE_PUSH+.12   # its talons leave the rock
    spire_lift={}
    def reaching(u):
        """How far its legs have reached for the spire u seconds after the landing begins (negative
        in the hop's or the takeoff's last stretch), and how far into the study's grab its talons are."""
        return keys(u,[(-LEG_REACH,0),(.3,1)]),keys(u,[(-LEG_REACH,.3),(.3,1.2)])
    def reach_legs(p,u,share=1):
        """Swings its tucked legs forward into the grab, talons spreading, as far as reaching(u) has
        them (times share): hinge joints swing straight through, as in the Raptor Strike."""
        w,at=reaching(u);w*=share
        if w<=0:return
        grab=pose('grab',at);reach=copy.deepcopy(p)
        for n in LEG_BONES:reach[n]=copy.deepcopy(grab[n])
        hinge=lambda n:n.split('_',1)[1] in ('thigh','shin','tarsus')
        for n in filter(hinge,LEG_BONES):
            for k in ('rotation','position'):p[n][k]=p[n][k]*(1-w)+grab[n][k]*w
        blend_local(p,reach,[n for n in LEG_BONES if not hinge(n)],w)
    # Where the hop and the takeoff hand over to the landing: the flight's neutral glide, its legs
    # already reaching for the spire.
    arrival=copy.deepcopy(flight_rest);reach_legs(arrival,0)
    def spire_flight(t):
        """The hop to the next spire, all of it as the game plays it. The server holds it on its
        spire until SPIRE_PUSH while it turns onto its way out: it sinks into a crouch, crest
        lifting and head locked on where it is going, and opens and raises its wings. Then it drives
        off with straightening legs into one huge downbeat, its legs streaming back and tucking. A
        slow recovery and a second, weaker beat carry it out to the side of the next spire, glancing
        down over the summit; then it sets its wings and glides through its banked turn round onto
        the spire (the game banks it, levels its head and turns its head into the turn), and comes
        in over the spire in the flight's neutral glide, where the landing's flare begins."""
        P,OFF,END=SPIRE_PUSH,SPIRE_OFF,SPIRE_FLIGHT
        load=keys(t,[(0,0),(.05,0),(P,1),(OFF,0,'snap')])
        push=keys(t,[(0,0),(P-.04,0),(OFF,1,'hit'),(.95,0)])
        # From the perch's body and legs into the flight's, over the push and the downbeat.
        airborne=keys(t,[(0,0),(P,0),(.9,1)])
        # Raised slowly through the crouch and the push, one heavy downbeat as the feet leave, a
        # slow recovery that only half raises them, a second, weaker beat, then set level to glide
        # before the turn.
        stroke=lambda x:keys(x,[(0,0),(.05,0),(.5,1.05),(.88,-1.0,'hit'),(1.05,-.9),(1.45,.4),
            (1.9,.75),(2.22,-.65,'hit'),(2.36,-.55),(2.65,0),(END,0)])
        air=pose('flight',0);flap(air,t,stroke,lag=.11)
        # The wings open as a bird opens them, the landing's fold in reverse: they swing out from
        # its sides into a Z laid flat, then unfold from the shoulder out, the hands last, rising.
        reach={n:.2 if n in HANDS else .17 if n in FOREARMS else .14 for n in by}
        opening=copy.deepcopy(air);joint_mix(opening,flat_fold,lambda n:1-keys(t,[(reach[n],0),(reach[n]+.28,1)]))
        ground=copy.deepcopy(perch);fold_wings(ground,opening,lambda n:keys(t,[(.02,0),(.26,1)]))
        p=mix(ground,air,airborne);body=p['body']
        # Down and back into the crouch, up and forward off the rock, and heaved up after each downbeat.
        lift=keys(t,[(0,0),(.95,0),(1.15,4.5),(1.5,1.2),(1.75,0),(2.2,0),(2.38,2.2),(2.65,0)])
        body['position']+=np.array([0,-9*load+7*push+lift,5*load-8*push])
        body['rotation'][0]+=9*load-10*push+keys(t,[(0,0),(.85,0),(1.2,3),(1.7,0),(2.15,0),(2.4,1.5),(2.7,0)])
        p['neck_base']['rotation'][0]+=6*load-4*push
        p['tail_fan']['rotation'][0]+=-12*load+14*push
        # A grunt with the push.
        p['lower_beak']['rotation'][0]=10*envelope(t,P-.05,.1,.12,.3)
        # Small hand and tail corrections keep the glide through the turn alive.
        glide=keys(t,[(0,0),(2.45,0),(2.6,1),(3.0,1),(3.15,0)])
        trim=keys(t,[(0,0),(2.5,0),(2.65,1),(2.85,-.5),(3.0,0)])
        p['tail_fan']['rotation'][1]+=3.5*trim
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_wrist']['rotation'][2]+=side*(1.4*trim+glide*noise(t,END,21+side,(2,3,5)))
            for i,f in enumerate(FINGERS):
                flex=glide*noise(t,END,31+i+side*7,(2,3,5))
                p[f'{label}_primary_{f:02}']['rotation'][0]+=1.8*flex
                p[f'{label}_primary_{f:02}']['rotation'][2]+=side*.9*flex
        # Talons gripping the rock until they leave it; then the legs stream back from the push
        # and tuck, swinging straight through on their hinges (see the Raptor Strike).
        if t<OFF:
            for label in ('left','right'):
                for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*20*envelope(t,P-.15,.1,.12,.05)
            plant(p,perch_feet)
        else:
            if 'legs' not in spire_lift:spire_lift['legs']=spire_flight(OFF-.01)
            for n in LEG_BONES:p[n]=copy.deepcopy(spire_lift['legs'][n])
            w=keys(t,[(OFF,0),(OFF+.4,1)])
            hinge=lambda n:n.split('_',1)[1] in ('thigh','shin','tarsus')
            for n in filter(hinge,LEG_BONES):
                for k in ('rotation','position'):p[n][k]=p[n][k]*(1-w)+air[n][k]*w
            blend_local(p,air,[n for n in LEG_BONES if not hinge(n)],w)
        # Locked on the next spire through the crouch and the launch, then held glances down over
        # the summit to either side, turned at the flight's slow pace, and forward again for the turn.
        look=twitch(t,[(.08,(12,0,0)),(1.0,(32,-22,-5)),(1.55,(0,0,0)),(1.95,(26,18,4)),(2.4,(0,0,0))],
            spark=1.0,hz=12,duration_scale=2.70)
        head(p,-4*airborne+look[0],look[1],look[2])
        # It hands over exactly in the pose the landing starts from, its legs already reaching for
        # the spire over the last stretch.
        p=mix(p,flight_rest,ease(t,2.95,END));reach_legs(p,t-END)
        return p
    # The wing's hands and forearms, each with the feathers they carry.
    HANDS={n for label in ('left','right') for n in subtree(f'{label}_wing_wrist')}
    FOREARMS={n for label in ('left','right') for n in subtree(f'{label}_wing_elbow')}-HANDS
    WING_PARTS=[n for label in ('left','right') for n in subtree(f'{label}_wing_shoulder')]
    # Wings folded flat out at its sides, as a bird folds them before tucking them in: the arm swept
    # back, the forearm forward and the hand back, all in the plane of the open wing. From here every
    # part turns about the same way into the perched fold, so the wing moves in as one.
    flat_fold=pose('flight',0)
    # How far each arm bone ends up turned in the plane of the wing.
    swept={'wing_shoulder':60,'wing_elbow':-60,'wing_wrist':60}
    for label,side in (('left',1),('right',-1)):
        flat_fold[label+'_wing_shoulder']['rotation'][1]+=side*60
        flat_fold[label+'_wing_elbow']['rotation'][1]-=side*120
        flat_fold[label+'_wing_wrist']['rotation'][1]+=side*120
        # The feathers of the arm and forearm keep pointing back as their bones fold under them,
        # closing like a fan; the hand's primaries and coverts and the leading edge ride with their bone.
        for n in subtree(label+'_wing_shoulder'):
            bone=by[n]['parent'].removeprefix(label+'_')
            if n.endswith(tuple(swept)) or '_leading_' in n or bone=='wing_wrist':continue
            flat_fold[n]['rotation'][1]-=side*swept[bone]
    def joint_mix(p,other,weight):
        """Moves the wing's joints toward other's angles, each part by weight(n): a bend at each
        joint, which, unlike a blend of orientations, never flips a part over."""
        for n in WING_PARTS:
            w=weight(n)
            for k in p[n]:
                delta=other[n][k]-p[n][k]
                if k=='rotation':delta=(delta+180)%360-180
                p[n][k]=p[n][k]+delta*w
    def land(t):
        """Its landing, from the flight's neutral glide as it comes in over its spire, every move
        slow and heavy for its size. It flares LAND_TOUCH before touching down: its body pitches
        slowly up, its wings rise and swing forward, cupped, and its legs swing forward, talons
        open, while its head holds steady on the rock. One slow, heavy braking beat, then its
        talons slam onto the capstone and grip, its legs fold deep under its weight and its body
        rolls on over its feet as its tail swings up. It holds its wings high while it finds its
        balance, takes one slow look aside, and folds its wings in stages, hands first, its
        shoulders settling as they close. It is still as its call begins; in game it turns to glare
        at its challenger from LAND_GLARE."""
        T=LAND_TOUCH
        # Riding above the server's position in flight, it comes down onto the rock as it brakes.
        drop=keys(t,[(0,0),(.3,.08),(T,1,'hit')])
        flare=keys(t,[(0,0),(.55,1),(T,.75),(T+.45,0)])
        airborne=keys(t,[(0,1),(T-.12,1),(T+.4,0)])
        absorb=envelope(t,T-.03,.18,.15,.8,'snap','ease')
        lurch=keys(t,[(0,0),(T,0),(T+.35,1),(T+1.1,0)])
        # Slowly cupped and raised as it flares, one slow braking beat forward and down, raised high
        # as it lands and held there for its balance. Then it folds them as a bird does: they come
        # down and fold flat out at its sides, the hands leading, and the folded wings swing in
        # against its body, every feather still pointing back.
        stroke=lambda x:keys(x,[(0,0),(.4,.9),(.68,-.4,'hit'),(T+.1,.45),(T+.5,1.0),(1.45,.9)])
        air=pose('flight',0);flap(air,t,stroke,lag=.12,sweep=25*flare)
        lower={n:1.42 if n in HANDS else 1.48 if n in FOREARMS else 1.54 for n in by}
        folding=copy.deepcopy(air);joint_mix(folding,flat_fold,lambda n:keys(t,[(lower[n],0),(lower[n]+.5,1)]))
        ground=copy.deepcopy(perch);fold_wings(ground,folding,lambda n:1-keys(t,[(1.82,0),(2.4,1)]))
        p=mix(ground,air,airborne);body=p['body']
        p['root']['position']=air['root']['position']*(1-drop)+perch['root']['position']*drop
        # It sinks deep into the impact and its weight rolls on over its feet; one slow sway as it
        # finds its balance; its shoulders settle as its wings close.
        sway=keys(t,[(0,0),(T+.2,0),(T+.65,2.5),(1.8,-1),(2.25,0)])
        settle=envelope(t,1.95,.25,.05,.25,'ease','ease')
        body['position']+=np.array([0,-12*absorb-2*settle,-3*lurch])
        body['rotation']+=np.array([-30*flare+8*lurch,0,sway])
        p['neck_base']['rotation'][0]+=-8*flare+3*lurch
        # Its tail stays in line with its pitched-up body as a brake, clear of the rock coming up
        # under it, then swings up to balance it as it lands.
        p['tail_fan']['rotation'][0]+=LIFT*(6*flare+16*lurch)
        p['lower_beak']['rotation'][0]=8*envelope(t,T-.03,.1,.12,.45)
        # Its head holds steady on the spot it lands on through the flare and the impact; once down,
        # it lifts and turns slowly to look aside, then back to the front.
        yaw=keys(t,[(0,0),(1.25,0),(1.7,-22),(1.95,-22),(2.35,0)])
        head(p,keys(t,[(0,-4),(.5,14),(T,16),(T+.18,20),(T+.6,8),(1.6,0)]),yaw,.25*yaw)
        # Its talons slam on and grip; the right foot slowly re-grips as it finds its balance.
        grip=keys(t,[(0,0),(T-.03,0),(T+.06,1,'snap'),(2.2,1),(LAND,0)])
        regrip=envelope(t,1.3,.18,.12,.3,'ease','ease')
        for label in ('left','right'):
            for i in range(4):
                p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*(26*grip-(14*regrip if label=='right' else 0))
        if t>=T-.1:plant(p,perch_feet)
        if t>=T+.07:return p
        # In the air its legs, already reaching as it comes in, swing on forward into the study's
        # grab, talons spread, and slam down onto the rock as the planted legs take over.
        reach_legs(p,t,keys(t,[(T-.06,1),(T+.07,0,'drop')]))
        # It starts exactly in the glide the hop hands over in, its legs already reaching.
        return mix(arrival,p,ease(t,0,.08))
    def call(t):
        """It calls the lightning down, its head quick and electric, its great body slow. Its head
        jerks up to the sky in three stuttered snaps as its bill opens, while its chest begins to
        swell and rise. The bolt leaves its bill at CALL_BOLT, and the discharge jolts through it.
        Still rising, it opens its wings, the fold in reverse, half raised and trembling with the
        storm, its head held to the sky with small electric tics. As the first strike lands it
        whips its head down onto its mark, thrusts its chest forward and spreads its wings; the
        second strike shivers through it; then it folds its wings slowly and settles."""
        B,(S1,S2,S3)=CALL_BOLT,CALL_STRIKES
        p=copy.deepcopy(perch);body=p['body']
        # The slow swell up to its full height, the command thrust forward, the jolts of the storm.
        rise=keys(t,[(0,0),(.15,0),(1.1,1),(S1-.1,1),(S1+.15,.55),(S3,0)])
        command=keys(t,[(0,0),(S1-.1,0),(S1+.05,1,'hit'),(S2-.05,.8),(S3,0)])
        jolt=shake(t,B,.4,13)+.6*shake(t,S2,.3,13)
        tremble=math.sin(t*math.tau*11)*envelope(t,B+.05,.2,1.15,.15,'ease','ease')
        body['position']+=np.array([0,11*rise-2*command,-3*command])
        body['rotation']+=np.array([-15*rise+9*command+1.5*jolt,0,2.5*jolt])
        p['neck_base']['rotation'][0]+=-16*rise+8*command
        p['tail_fan']['rotation'][0]+=14*rise
        p['tail_fan']['rotation'][1]+=8*jolt
        # Out into the flat Z and open, from the shoulder out, half raised as it holds; flung wide at
        # the command; then back in the same way, the hands first.
        stroke=lambda x:keys(x,[(0,0),(.6,0),(1.6,.55),(S1-.12,.55),(S1+.05,.12,'snap'),(S1+.3,.12),(S2+.1,0)])
        # Held, they mantle a little forward, toward the challengers it is calling down on.
        mantle=keys(t,[(0,0),(.9,0),(1.6,1),(S1-.12,1),(S1+.05,0,'snap')])
        air=pose('flight',0);flap(air,t,stroke,lag=.12,sweep=20*mantle)
        order=lambda n:2 if n in HANDS else 1 if n in FOREARMS else 0
        opening=copy.deepcopy(air)
        joint_mix(opening,flat_fold,lambda n:1-keys(t,[(.8+.06*order(n),0),(1.6+.06*order(n),1),(S1+.25-.06*order(n),1),(S1+.6-.06*order(n),0)]))
        fold_wings(p,opening,lambda n:keys(t,[(.45+.05*order(n),0),(1.0+.05*order(n),1),(S2-.05-.05*order(n),1),(S3-.05-.05*order(n),0)]))
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_wrist']['rotation'][2]+=side*3*tremble
            p[label+'_wing_shoulder']['rotation'][2]+=side*4*jolt
        # Its bill gapes to the sky for the bolt, hisses at its command, and closes.
        p['lower_beak']['rotation'][0]=keys(t,[(0,0),(.12,0),(.42,52,'snap'),(S1-.15,48),(S1,30,'snap'),(S2-.1,26),(S3-.15,0)])
        # Its head: three stuttered snaps up to the sky, small electric tics as it holds there, a
        # whip down onto its mark as the first strike lands, a jolt at the second, and home.
        look=twitch(t,[(.08,(-30,6,4)),(.22,(-54,-5,-3)),(.34,(-80,0,0),2.5),(.95,(-83,5,3)),(1.32,(-77,-4,-2)),
            (1.58,(-81,2,1)),(S1-.2,(20,0,0),4),(S2,(26,2,2),2),(S2+.12,(18,0,0)),(S3-.12,(0,0,0))],spark=1.5)
        head(p,look[0],look[1],look[2]+2*tremble)
        plant(p,perch_feet);return p
    def summon(t):
        """It summons the summit's first tornado. A beat still on its perch, then it draws back and
        low, gathering itself, its wings opening from the shoulder; rears up to its full height with
        its wings raised high as its head jerks up to the sky in stuttered electric snaps and its bill
        gapes. At SUMMON_CALL it screams the storm down: the bolt strikes where the funnel will touch
        down, and the discharge jolts through it. Then, its head whipped down onto the funnel, it
        drives the wind at it with three heavy downbeats, its body heaving forward with each; folds
        its wings slowly, the hands first, and settles back on its perch."""
        C,(B1,B2,B3)=SUMMON_CALL,SUMMON_BEATS
        p=copy.deepcopy(perch);body=p['body']
        gather=keys(t,[(0,0),(.3,0),(1.0,1),(1.15,1),(1.55,0)])
        rise=keys(t,[(0,0),(1.0,0),(1.55,1),(C+.15,1),(B1+.1,.6),(B3+.2,.45),(SUMMON-.3,0)])
        heave=sum(envelope(t,b-.08,.1,.05,.35,'snap','ease') for b in SUMMON_BEATS)
        jolt=shake(t,C,.45,13)
        tremble=math.sin(t*math.tau*11)*envelope(t,C-.3,.2,.35,.2,'ease','ease')
        body['position']+=np.array([0,-4*gather+12*rise-1.5*heave,2.5*gather-2.5*heave])
        body['rotation']+=np.array([8*gather-18*rise+7*heave+1.5*jolt,0,2.5*jolt])
        p['neck_base']['rotation'][0]+=6*gather-18*rise+5*heave
        p['tail_fan']['rotation'][0]+=-6*gather+16*rise
        p['tail_fan']['rotation'][1]+=8*jolt
        # Raised high for the scream, then three heavy strokes down at the funnel, each recovering
        # high, and down to rest.
        stroke=lambda x:keys(x,[(0,0),(.5,0),(1.2,.4),(1.6,.95),(C+.15,1.0),(B1-.25,1.0),(B1,-.55,'hit'),
            (B1+.32,.85),(B2,-.6,'hit'),(B2+.32,.85),(B3,-.65,'hit'),(B3+.4,.25),(SUMMON-.35,0)])
        air=pose('flight',0);flap(air,t,stroke,lag=.12)
        order=lambda n:2 if n in HANDS else 1 if n in FOREARMS else 0
        opening=copy.deepcopy(air)
        joint_mix(opening,flat_fold,lambda n:1-keys(t,[(.6+.06*order(n),0),(1.3+.06*order(n),1),(B3+.3-.06*order(n),1),(SUMMON-.55-.06*order(n),0)]))
        fold_wings(p,opening,lambda n:keys(t,[(.35+.05*order(n),0),(.9+.05*order(n),1),(B3+.35-.05*order(n),1),(SUMMON-.25-.05*order(n),0)]))
        for label,side in (('left',1),('right',-1)):
            p[label+'_wing_wrist']['rotation'][2]+=side*3*tremble
            p[label+'_wing_shoulder']['rotation'][2]+=side*4*jolt
        # Its bill gapes to the sky for the scream and closes; it hisses at each downbeat.
        p['lower_beak']['rotation'][0]=keys(t,[(0,0),(1.25,0),(1.45,52,'snap'),(C+.2,48),(B1-.2,12),(B1,30,'snap'),
            (B1+.3,8),(B2,26,'snap'),(B2+.3,8),(B3,30,'snap'),(B3+.4,0)])
        # Its head: lowered at the summit as it gathers itself; three stuttered snaps up to the sky,
        # electric tics as it screams; a whip down onto the funnel, a jolt at each beat, and home.
        look=twitch(t,[(.4,(14,0,0)),(1.15,(-28,6,4)),(1.3,(-55,-5,-3)),(1.45,(-82,0,0),2.5),(C,(-85,4,3)),
            (C+.15,(24,0,0),3),(B1,(28,2,2),2),(B2,(28,-2,-1),2),(B3,(24,1,1),2),(SUMMON-.45,(0,0,0))],spark=1.5)
        head(p,look[0],look[1],look[2]+2*tremble)
        plant(p,perch_feet);return p
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
    # Its bill driven into the rock at a slant, its neck bent down to it and its wings splayed out.
    STUCK_HEAD=42   # its head's pitch below level, degrees, while its bill is buried
    def stuck_pose():
        p=pose('peck',.5);spread(p,-.1)
        p['root']['rotation'][0]=10
        p['neck_base']['rotation'][0]-=16;p['neck_upper']['rotation'][0]+=10
        return p
    guard_pose=hunched(0);stuck_free={}
    def stuck(t):
        """Stuck by its bill: the players' opening, shaped so it reads. The impact crumples it onto its
        bill, its legs folding deep and its tail whipping up, its wings bouncing on the rock; it
        settles into a crouch (where the chain smash's hop takes over, at SMASH_PAUSE) and lies
        stunned, only breathing, a shiver of the storm running through it. Then it heaves, straining
        against its pinned bill, its hips rising as its legs straighten, lifts its wings slowly and
        slaps them down once, and slumps, panting. It wrenches its neck to its right, then its left, its body swinging
        about its pinned bill, rakes the rock with its right talons and shoves at it with its wings, and
        slumps again. From 4.40 it gathers itself: crest flared and crackling, tail up, its wings
        raised high off the rock, its whole body trembling. At STUCK_FREE its bill rips free."""
        F=STUCK_FREE
        if t>F:return stuck_release(t)
        p=stuck_pose();body=p['body']
        crumple=keys(t,[(0,0),(.12,1,'snap'),(.4,.7),(1.3,0)])
        heave=keys(t,[(0,0),(1.4,0),(1.95,.9),(2.05,.95),(2.5,0,'drop'),(4.4,0),(4.95,1.05),(F,1.1)])
        # Heavy yanks, each held a moment: a whole neck can't snap like a bill.
        wrench=keys(t,[(0,0),(3.2,0),(3.45,1),(3.55,1),(3.82,-1.1),(3.95,-1),(4.25,0)])
        breath=sum(d*envelope(t,a,.55,.1,.65,'ease','ease') for a,d in ((.5,.7),(2.55,1),(3.05,.8),(4.0,.6)))
        tremble=math.sin(t*math.tau*11)*envelope(t,4.55,.2,.25,0,'ease','ease')
        shiver=shake(t,.9,.3,14)+.7*shake(t,2.95,.3,14)+.6*tremble
        # Its bill is pinned, so its body moves about it as its neck bends: folding the neck sinks it
        # onto its bill, straining it out raises its hips, legs straightening under it.
        body['rotation']+=np.array([2*crumple-heave-1.2*breath,0,-3*wrench+shiver])
        p['neck_base']['rotation']+=np.array([-9*crumple+10*heave+1.5*breath,6*wrench,0])
        p['neck_upper']['rotation'][0]-=4*heave
        p['neck_lower']['rotation'][1]+=3*wrench
        p['tail_fan']['rotation'][0]+=LIFT*(18*crumple+12*heave)
        p['tail_fan']['rotation'][1]-=10*wrench
        p['lower_beak']['rotation'][0]=6+4*breath+3*heave
        # Its head is held by its bill: it only shivers, and turns as its neck wrenches.
        head(p,STUCK_HEAD+.8*shiver,9*wrench,3*wrench+2*shiver)
        # The bill stays pinned however its body moves.
        p['root']['position']+=pin-point(matrices(p),'upper_beak',tip)
        splay(p,t)
        # Its feet stay where its guard will stand; once, its right talons rake the rock: lifted,
        # reached forward, slammed down and dragged back, then set back in place.
        targets={k:v.copy() for k,v in feet.items()}
        targets['right']+=np.array([0,keys(t,[(0,0),(3.4,0),(3.52,12),(3.64,8),(3.7,0,'drop'),(3.95,0),(4.05,5),(4.15,0,'drop')]),
            keys(t,[(0,0),(3.4,0),(3.55,-10),(3.66,-10),(3.8,10,'hit'),(3.95,10),(4.15,0)])])
        grip=.5+.5*heave+.5*envelope(t,3.68,.05,.15,.2)
        for label in ('left','right'):
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*26*grip
        plant(p,targets)
        return p
    def stuck_release(t):
        """It rips its bill free and snaps its head up, rocks back onto its legs as it comes loose, shakes
        its head, and folds its raised wings as a bird does, hands first, settling into its guard."""
        F=STUCK_FREE
        if 'pose' not in stuck_free:stuck_free['pose']=stuck(F)
        start=stuck_free['pose']
        p=mix(start,guard_pose,keys(t,[(F,0),(F+.1,.04),(6.0,1)]))
        rock=keys(t,[(F,0),(F+.22,1,'snap'),(F+.6,-.25),(5.85,0)])
        p['body']['rotation'][0]-=9*rock
        p['body']['position']+=np.array([0,3*rock,2*rock])
        p['tail_fan']['rotation'][0]+=LIFT*6*rock
        look=twitch(t,[(F,(-STUCK_HEAD-14,0,0),4),(F+.34,(-STUCK_HEAD-5,-18,-9)),(F+.42,(-STUCK_HEAD-5,15,7)),
            (F+.5,(-STUCK_HEAD-3,-8,-3)),(F+.62,(-STUCK_HEAD,0,0))])
        head(p,STUCK_HEAD+look[0],look[1],look[2])
        p['lower_beak']['rotation'][0]=keys(t,[(F,start['lower_beak']['rotation'][0]),(F+.08,34,'snap'),(F+.3,30),(F+.6,4),(6.0,0)])
        # Its raised wings come down and fold flat at its sides, the hands leading, then swing in.
        for n in WING_PARTS:p[n]=copy.deepcopy(guard_pose[n])
        folding=copy.deepcopy(p)
        for n in WING_PARTS:folding[n]=copy.deepcopy(start[n])
        lower={n:F+.05 if n in HANDS else F+.1 if n in FOREARMS else F+.15 for n in by}
        joint_mix(folding,flat_fold,lambda n:keys(t,[(lower[n],0),(lower[n]+.45,1)]))
        fold_wings(p,folding,lambda n:1-keys(t,[(F+.45,0),(5.95,1)]))
        plant(p,feet)
        return p
    # Stuck's frames where the dive and each hop slam in (0) and where the hop takes over, crouched
    # after its slam (SMASH_PAUSE); built when first needed, once the tail's lift is known.
    stuck_frames={}
    def stuck_frame(t):
        if t not in stuck_frames:stuck_frames[t]=stuck(t)
        return stuck_frames[t]
    hinge=lambda n:n.split('_',1)[1] in ('thigh','shin','tarsus')
    def swing_legs(p,a,b,w):
        """Its legs, from a's toward b's. The hip, knee and ankle are hinges: they swing straight
        through (a slerp would fold a knee through itself, as two bends can go opposite ways); the
        feet and toes turn the short way, each carried by its blended parent."""
        target=copy.deepcopy(p)
        for n in LEG_BONES:p[n]=copy.deepcopy(a[n]);target[n]=copy.deepcopy(b[n])
        for n in filter(hinge,LEG_BONES):
            for k in ('rotation','position'):p[n][k]=a[n][k]*(1-w)+b[n][k]*w
        blend_local(p,target,[n for n in LEG_BONES if not hinge(n)],w)
    def strike(p,t,start,beat,end):
        """Drives a pose into stuck's first frame by end, arriving at speed: the body, neck and head from
        start, the wings beating down onto the rock from beat. The legs land on their own."""
        body,wings=keys(t,[(start,0),(end,1,'hit')]),keys(t,[(beat,0),(end,1,'hit')])
        if max(body,wings)<=0:return p
        q,w=mix(p,stuck_frame(0),body),mix(p,stuck_frame(0),wings)
        for n in WING_PARTS:q[n]=w[n]
        for n in LEG_BONES:q[n]=p[n]
        return q
    DIVE_OFF=DIVE_PUSH+.12   # its talons leave the rock
    dive_lift={}
    def dive(t):
        """The dive from its spire. The server holds it there until DIVE_PUSH: it sinks into a crouch,
        crest lifting, its head snapping down onto its mark and locking there, and opens and raises its
        wings, the spire hop's opening. It drives off with straightening legs into one huge downbeat,
        its legs streaming back and tucking; a slow recovery, a second, labored beat (where the chain
        smash picks its player its head snaps onto them), then its wings rise high and wide over the
        top. It tips slowly over into its stoop around a head held dead still on its mark, folds its
        wings in against its body, hands first, as a falcon stoops, and draws its neck back into an S.
        As the server brakes
        it flares, the warning seen from below: its body swings back toward level around its still
        head, its wings open high and wide, its tail fans and its legs swing down, screeching. Then
        its neck shoots out, its wings beat down onto the rock and its bill spears in at 3.00,
        stuck's first frame, the bill above the rock until then."""
        P,OFF=DIVE_PUSH,DIVE_OFF
        load=keys(t,[(0,0),(.05,0),(P,1),(OFF,0,'snap')])
        push=keys(t,[(0,0),(P-.04,0),(OFF,1,'hit'),(.95,0)])
        airborne=keys(t,[(0,0),(P,0),(.9,1)])
        flare=keys(t,[(0,0),(2.5,0),(2.75,1)])
        # Raised slowly through the crouch, one huge downbeat as its feet leave, a slow recovery and a
        # second, weaker beat; raised high over the top; flung up and forward in the flare.
        stroke=lambda x:keys(x,[(0,0),(.05,0),(.5,1.05),(.88,-1.0,'hit'),(1.32,.75),(1.6,-.7,'hit'),(1.95,1.05),(2.5,1.0),(2.72,.75),(3.0,-.5,'hit')])
        air=pose('flight',0);flap(air,t,stroke,lag=.11,sweep=22*flare)
        reach={n:.2 if n in HANDS else .17 if n in FOREARMS else .14 for n in by}
        opening=copy.deepcopy(air);joint_mix(opening,flat_fold,lambda n:1-keys(t,[(reach[n],0),(reach[n]+.28,1)]))
        ground=copy.deepcopy(perch);fold_wings(ground,opening,lambda n:keys(t,[(.02,0),(.26,1)]))
        p=mix(ground,air,airborne);body=p['body']
        # Folded flat at its sides for the stoop, the hands first; opened from the shoulder out to flare.
        tuck={n:1.92 if n in HANDS else 1.97 if n in FOREARMS else 2.02 for n in by}
        spread_={n:2.56 if n in HANDS else 2.53 if n in FOREARMS else 2.5 for n in by}
        joint_mix(p,flat_fold,lambda n:keys(t,[(tuck[n],0),(tuck[n]+.3,1),(spread_[n],1),(spread_[n]+.22,0)]))
        # Then swung in most of the way against its body, as a falcon stoops, and out again to flare.
        close=keys(t,[(0,0),(2.05,0),(2.32,.65),(2.47,.65),(2.6,0)])
        if close>0:
            closed=copy.deepcopy(p)
            for n in WING_PARTS:closed[n]=copy.deepcopy(perch[n])
            fold_wings(closed,p,lambda n:1-close)
            for n in WING_PARTS:p[n]=closed[n]
        # Down and back into the crouch, up and forward off the rock, heaved up after each downbeat;
        # nose up as it climbs, tipped slowly over into the stoop, swung back toward level to brake.
        lift=keys(t,[(0,0),(.95,0),(1.15,4.5),(1.5,1.2),(1.62,0),(1.8,3.5),(2.0,0)])
        pitch=keys(t,[(0,0),(.7,0),(1.05,-10),(1.6,-7),(1.78,0),(2.22,60),(2.5,62),(2.8,-6)])
        body['position']+=np.array([0,-9*load+7*push+lift,5*load-8*push])
        body['rotation'][0]+=9*load-10*push+pitch
        coil=keys(t,[(0,0),(2.0,0),(2.35,1),(2.8,1)])
        p['neck_base']['rotation'][0]+=6*load-4*push-20*coil
        p['neck_upper']['rotation'][0]+=12*coil
        p['tail_fan']['rotation'][0]+=-12*load+14*push-LIFT*12*flare
        # A grunt with the push, a screech in the flare.
        p['lower_beak']['rotation'][0]=10*envelope(t,P-.05,.1,.12,.3)+keys(t,[(0,0),(2.55,0),(2.72,30,'snap'),(2.82,26)])
        # Its head: down onto its mark as it crouches, then held there in the world, snapping lower as
        # it nears the top and fixed on its mark as its body tips over under it and swings back.
        look=twitch(t,[(.08,(22,0,0)),(1.0,(40,0,0),2),(1.55,(58,0,0),2),(1.78,(70,0,0),2),(2.58,(55,0,0),2)])
        head(p,look[0],look[1],look[2])
        # Talons gripping the rock until they leave it; then its legs stream back from the push and
        # tuck, swing down to reach as it flares, and land planted, swinging on their hinges.
        if t<OFF:
            for label in ('left','right'):
                for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*20*envelope(t,P-.15,.1,.12,.05)
            plant(p,perch_feet)
        else:
            if 'legs' not in dive_lift:dive_lift['legs']=dive(OFF-.01)
            grab=pose('grab',keys(t,[(OFF,.8),(1.0,.3),(1.6,.15),(2.5,.1),(2.82,1.2)]))
            swing_legs(p,dive_lift['legs'],grab,keys(t,[(OFF,0),(OFF+.35,1)]))
            if t>2.84:swing_legs(p,copy.deepcopy(p),stuck_frame(0),keys(t,[(2.84,0),(3.0,1,'drop')]))
        # The strike: its body, neck and head drive into stuck's first frame, arriving at speed, its
        # wings beating down onto the rock from a moment before.
        return strike(p,t,2.82,2.82,3.0)
    hop_lift={}
    def hop(t):
        """The chain smash's hop: one wingbeat. From its crouch after the slam, its legs drive, its
        wings shove down off the rock and its bill tears free, its head snapping up with a screech;
        its legs stream back and tuck as its wings rise slowly through one long upstroke. Its head
        snaps down onto the next circle and holds there while its body tips over under it, wings held
        high as the warning, legs swinging forward to reach. Then its neck spears down, its wings beat
        onto the rock and its bill drives in at 1.20, stuck's first frame, the bill above the rock
        until then even on the server's lowest arc."""
        OFF=.08
        stroke=lambda x:keys(x,[(0,-.2),(.12,-.95,'hit'),(.62,.9),(.92,.85),(1.2,-.5,'hit')])
        flare=keys(t,[(0,0),(.6,0),(.85,1)])
        air=pose('flight',0);flap(air,t,stroke,lag=.1,sweep=18*flare)
        p=mix(stuck_frame(SMASH_PAUSE),air,keys(t,[(0,0),(.25,1)]));body=p['body']
        # Reared up as it tears free and leaps, level over the top, tipped down onto its circle.
        body['rotation'][0]+=keys(t,[(0,0),(.12,-16),(.45,-6),(.62,0),(.88,16)])
        p['neck_base']['rotation'][0]+=keys(t,[(0,0),(.12,-12),(.5,-4),(.9,6)])
        p['tail_fan']['rotation'][0]+=LIFT*keys(t,[(0,0),(.12,10),(.5,0),(.88,-8)])
        p['lower_beak']['rotation'][0]=keys(t,[(0,stuck_frame(SMASH_PAUSE)['lower_beak']['rotation'][0]),(.06,34,'snap'),(.3,28),(.55,0),(.85,0),(.95,12)])
        look=twitch(t,[(.02,(-STUCK_HEAD-12,0,0),4),(.3,(6,0,0),2),(.85,(10,0,0),1)])
        head(p,STUCK_HEAD+look[0],look[1],look[2])
        if t<OFF:plant(p,feet)
        else:
            if 'legs' not in hop_lift:hop_lift['legs']=hop(OFF-.01)
            grab=pose('grab',keys(t,[(OFF,.8),(.4,.3),(.6,.25),(.92,1.1),(1.05,1.2)]))
            swing_legs(p,hop_lift['legs'],grab,keys(t,[(OFF,0),(OFF+.25,1)]))
            if t>1.04:swing_legs(p,copy.deepcopy(p),stuck_frame(0),keys(t,[(1.04,0),(1.2,1,'drop')]))
        return strike(p,t,.97,1.0,1.2)
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
    def stampede(t,S=1e6):
        """Thunder Charge, in its own time; S is when it stops running (never, for the windup and
        the run loop). A bull's challenge: it lowers its head and sways it, crest raised, wings
        half open and low, and rakes the rock back three times with its right talons, snorting,
        crackles flying; then it drops lower still and explodes into a charge at 2.20. It runs
        like a cassowary, head low and level as a battering ram, bill wide open in a screech,
        wings held out for balance and bouncing with each step, legs pumping, the tail streaming
        and swaying. At S it slams its feet forward and skids, its bill snapping shut, leaning
        back with its talons gouging the rock and its wings flared up as brakes; its head whips
        round to the player it missed, then it steps back into its guard."""
        R=STAMPEDE_RUN
        p=copy.deepcopy(neutral);body=p['body'];targets={k:v.copy() for k,v in feet.items()}
        low=keys(t,[(0,0),(.4,1),(1.95,1),(2.1,1.3),(R,1)])*keys(t,[(0,1),(R-.05,1),(R+.12,0)])
        # Every stop envelope starts exactly at S, a stride boundary, so the skid clip takes over
        # from the run loop without a seam.
        run=keys(t,[(0,0),(R-.08,0),(R+.12,1),(S,1),(S+.12,0,'snap')])
        skid=keys(t,[(0,0),(S,0),(S+.14,1,'snap'),(S+.75,1),(S+1.0,.3),(S+1.5,0)])
        whip=keys(t,[(0,0),(S+.75,0),(S+.9,1,'snap'),(S+1.35,1),(S+1.75,0)])
        # Its wings open slowly from their running hold into the skid's brake, as a giant's would.
        brake=keys(t,[(0,0),(S,0),(S+.4,1),(S+.75,1),(S+1.0,.3),(S+1.5,0)])
        hold=keys(t,[(0,0),(R-.08,0),(R+.12,1),(S,1),(S+.4,0)])
        # Three slow rakes with the right talons, 0.6 s each: lifted high and forward, set down,
        # dragged back hard. The whole body drives each one: it rocks back onto its left foot and
        # rears a little as the leg lifts, then lunges forward and down as it drags, head dipping
        # and tail flicking; the wings open a little after the lunge and settle slowly.
        rz=ry=wind=thrust=flick=0.
        for a in STAMPEDE_RAKES:
            rz+=keys(t,[(a,0),(a+.22,-18),(a+.28,-20),(a+.48,26,'hit'),(a+.6,0)])
            ry+=keys(t,[(a,0),(a+.13,14),(a+.28,0,'drop'),(a+.48,0),(a+.54,7),(a+.6,0)])
            wind+=keys(t,[(a-.05,0),(a+.22,1),(a+.3,1),(a+.48,0,'hit')])
            thrust+=envelope(t,a+.3,.2,.06,.4,'hit','ease')
            flick+=envelope(t,a+.36,.25,.05,.45,'ease','ease')
        targets['right']+=np.array([0,ry,rz])
        snort=sum(envelope(t,a+.38,.06,.08,.2) for a in STAMPEDE_RAKES)
        # The stride: each foot sweeps back on the rock under it for 45% of a cycle, then swings
        # forward through the air; the body dips as each foot takes its weight.
        phase=(t-R)*STRIDE_HZ
        bob=sway=0.
        for label,offset in (('left',0),('right',.5)):
            f=(phase+offset)%1
            if f<.45:z,y=-20+40*f/.45,0
            else:u=(f-.45)/.55;z,y=20-40*(u*u*(3-2*u)),16*math.sin(math.pi*u)
            targets[label]+=np.array([0,y,z])*run
        bob=-3*math.cos(4*math.pi*(phase-.225))*run
        sway=math.sin(2*math.pi*phase)*run
        # Braced at the skid: both feet slammed forward, then stepped back one at a time.
        for label,(a,b) in (('left',(S+.9,S+1.15)),('right',(S+1.15,S+1.4))):
            brace=keys(t,[(0,0),(S,0),(S+.14,1,'snap'),(a,1),(b,0)])
            targets[label]+=np.array([0,8*envelope(t,a,.12,0,.13,'ease','drop'),-18*brace])
        body['position']+=np.array([3*low-2*sway+3*wind,-8*low-6*run+bob-8*skid+2*whip+3*wind-4*thrust,-4*run+7*skid+6*wind-8*thrust])
        body['rotation'][0]+=8*low+12*run-18*skid-6*wind+9*thrust
        body['rotation'][2]+=3*sway*run+4*wind
        p['tail_fan']['rotation'][0]+=LIFT*(10*low+8*run+12*skid+14*thrust)
        p['tail_fan']['rotation'][1]+=10*sway-12*thrust
        fan(p,t,.6*low)
        # Its head: swaying low as it challenges, level and steady as it runs, thrown back as it
        # brakes, then whipped round (in the game its gaze finds the player it missed).
        pitch=18*low+4*run-22*skid*(1-whip)-8*whip-6*wind+10*thrust
        look=twitch(t,[(S+.7,(0,38,10)),(S+1.5,(0,0,0))])
        yaw=keys(t,[(0,0),(.3,0),(.7,10),(1.3,-12),(1.8,6),(2.05,0)])+look[1]
        goal=pounce_bill+(-.75*low-.1*run+.9*skid*(1-whip)+.5*whip)/SCALE
        base=p['neck_base']['rotation'][0]
        def bill_y(bend):
            p['neck_base']['rotation'][0]=base+bend;head(p,pitch+look[0],yaw,look[2])
            return point(matrices(p),'upper_beak',tip)[1]
        lo,hi=-60.,40.
        for _ in range(24):
            mid=(lo+hi)/2
            if bill_y(mid)>goal:lo=mid
            else:hi=mid
        bill_y((lo+hi)/2)
        # It screeches the whole way, bill gaping, the screech shaking with each stride; the skid
        # snaps it shut, and it only huffs as its head whips round.
        p['lower_beak']['rotation'][0]=14*snort+(44+5*math.sin(4*math.pi*phase))*run+8*whip
        # Wings: half open and low as it challenges, held out and bouncing with each step, flared up
        # and forward to brake, half mantled as its head whips round.
        spread(p,-.15*low+.15*flick+(-.3+.12*math.cos(4*math.pi*(phase-.225)))*hold+.65*brake*(1-whip)+.25*whip,
            .5*low+.2*flick+.75*hold+.95*brake*(1-whip)+.5*whip,-12*brake*(1-whip))
        grip=skid+.6*sum(envelope(t,a+.28,.06,.18,.15) for a in STAMPEDE_RAKES)
        for label in ('left','right'):
            for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*25*min(grip,1)
        plant(p,targets)
        return p
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
    takeoff_lift={}
    def takeoff(t):
        """Its takeoff from the summit floor as the brawl ends, every move of its body slow and heavy
        for its size. It holds its guard a moment while the game's gaze lets go, then its head snaps
        up onto the spire it is leaving for and holds there, dead still, through the launch, while
        the server turns it to face its way and its neck slowly rises. Its wings swing out from its
        sides into a Z laid flat and unfold from the shoulder out, the hands last, rising high, as it
        sinks into a deep crouch and grips the rock.
        Its legs drive it up and its feet leave the rock at TAKEOFF_PUSH as its first downbeat sweeps
        through level: the gust. Nothing touches the rock. Its legs trail straight back from the
        push, then fold up under it on their hinges as the server lifts it slowly off the rock. A
        slow recovery; its head snaps down onto the players it blasted; a second, weaker beat heaves
        it on up and a third carries it out past its spire; then it looks ahead and sets its wings to
        glide through its banked turn round onto the spire (the game banks it, levels its head and
        turns its head into the turn), in the flight's neutral glide, where the landing's flare begins."""
        P,END=TAKEOFF_PUSH,TAKEOFF
        # Down and back into the crouch, held, then driven up and forward off the rock.
        crouch=keys(t,[(0,0),(.4,0),(.9,1),(.98,1),(P,0,'hit')])
        push=keys(t,[(0,0),(.98,0),(P,1,'hit'),(1.6,.35),(2.0,0)])
        # From the guard's body and legs into the flight's once its feet leave the rock.
        airborne=keys(t,[(0,0),(P,0),(1.75,1)])
        # Nose up as it climbs off the rock, levelling as it gathers speed; heaved up by each beat.
        climb=keys(t,[(0,0),(P,0),(1.55,1),(2.2,.6),(3.3,.3),(3.8,0),(END,0)])
        heave=keys(t,[(0,0),(1.3,0),(1.6,1),(2.0,.2),(2.3,0),(2.55,.7),(2.9,0),(3.3,.5),(3.6,0),(END,0)])
        # Raised slowly as it opens them and crouches, one heavy downbeat through level as its feet
        # leave, a slow recovery, a second, weaker beat and a third, weaker still as it climbs out
        # past its spire, then set level to glide through the turn round onto it.
        stroke=lambda x:keys(x,[(0,0),(.2,0),(.94,1.05),(.98,1.05),(1.3,-.7),
            (2.0,.8),(2.06,.8),(2.32,-.65,'hit'),(2.65,.2),(2.95,.6),(3.25,-.45,'hit'),(3.55,0),(END,0)])
        air=pose('flight',0);flap(air,t,stroke,lag=.08)
        # The wings open as a bird opens them, the landing's fold in reverse: they swing out from
        # its sides into a Z laid flat, then unfold from the shoulder out, the hands last, rising.
        order=lambda n:2 if n in HANDS else 1 if n in FOREARMS else 0
        opening=copy.deepcopy(air);joint_mix(opening,flat_fold,lambda n:1-keys(t,[(.45+.08*order(n),0),(.85+.08*order(n),1)]))
        ground=copy.deepcopy(neutral);fold_wings(ground,opening,lambda n:keys(t,[(.15,0),(.55,1)]))
        p=mix(ground,air,airborne);body=p['body']
        body['position']+=np.array([0,-13*crouch+8*push+4*heave,6*crouch-8*push])
        body['rotation'][0]+=9*crouch-10*push-7*climb
        # Its neck rises slowly, lifting its head toward the spire, and stays up through the push.
        rear=keys(t,[(0,0),(.3,0),(.85,1),(1.03,1),(1.7,0)])
        p['neck_base']['rotation'][0]-=24*rear+14*crouch
        p['tail_fan']['rotation'][0]+=-12*crouch+14*push
        # A grunt with the push.
        p['lower_beak']['rotation'][0]=10*envelope(t,P-.12,.1,.12,.3)
        # Once the game's gaze has let go of its challenger, it snaps up onto the spire it is leaving
        # for and stays locked there through the launch; then a look down at the players it blasted,
        # ahead again, one more glance down over the summit as it climbs out, and ahead for the turn.
        look=twitch(t,[(.35,(-18,0,0)),(1.7,(34,-16,-6)),(2.45,(-4,0,0)),(3.05,(24,20,5)),(3.5,(0,0,0))],
            spark=1.0,duration_scale=1.5)
        head(p,look[0],look[1],look[2])
        if t<=P:
            # Its talons clench into the rock for the push.
            for label in ('left','right'):
                for i in range(4):p[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*24*envelope(t,.55,.35,.23,.07)
            plant(p,feet)
        else:
            # Its legs leave the rock straight from the push and trail back, talons open, then fold
            # up under it into the flight's tuck. The hip, knee and ankle swing straight through on
            # their hinges (see the Raptor Strike); the feet and toes turn the short way.
            if 'legs' not in takeoff_lift:
                pushed=takeoff(P);trail=copy.deepcopy(pushed)
                for label in ('left','right'):
                    trail[label+'_thigh']['rotation'][0]+=35
                    for i in range(4):trail[f'{label}_toe_{i}_tip']['rotation'][0]+=(-1 if i==3 else 1)*12
                takeoff_lift['legs'],takeoff_lift['trail']=pushed,trail
            pushed,trail=takeoff_lift['legs'],takeoff_lift['trail']
            for n in LEG_BONES:p[n]=copy.deepcopy(pushed[n])
            # The hip and knee fold first and the ankle last, so its feet rise steadily into the
            # tuck instead of swinging down through a dangling, straightened leg.
            back=keys(t,[(P,0),(1.45,1)])
            tuck={'thigh':keys(t,[(1.45,0),(1.75,1)]),'shin':keys(t,[(1.45,0),(1.8,1)]),'tarsus':keys(t,[(1.5,0),(1.9,1)])}
            hinge=lambda n:n.split('_',1)[1] in tuck
            for n in filter(hinge,LEG_BONES):
                w=tuck[n.split('_',1)[1]]
                for k in ('rotation','position'):
                    p[n][k]=(pushed[n][k]*(1-back)+trail[n][k]*back)*(1-w)+air[n][k]*w
            rest=[n for n in LEG_BONES if not hinge(n)]
            blend_local(p,trail,rest,back);blend_local(p,air,rest,tuck['tarsus'])
        # It hands over exactly in the pose the landing starts from.
        # It settles into the flight's neutral glide LEG_REACH before the end, where the phase change's
        # flight takes over; bound for a spire, its legs then reach for it, and it hands over exactly in
        # the pose the landing starts from.
        p=mix(p,flight_rest,ease(t,4.0,END-LEG_REACH));reach_legs(p,t-END)
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
        # Flared by the impact, drooping as it lies stunned, up with each effort, flared and crackling
        # as it gathers itself to rip free, and at rest in its guard.
        F=STUCK_FREE
        return keys(t,[(0,1),(.15,1),(.7,.15),(1.4,.15),(1.95,.6),(2.5,.2),(3.3,.2),(3.5,.55),(4.0,.5),(4.3,.25),
            (4.62,1,'snap'),(F+.1,1),(5.6,.6),(6.0,REST)])
    def crest_amount(name,t):
        # Raised a little for the one-eyed cock, flared for the rouse, a breath of lift in the stretch.
        if name=='perched':
            return .3*envelope(t,3.0,.1,.65,.3)+.85*envelope(t,4.25,.12,.35,.6,'snap','ease')+.2*keys(t,[(0,0),(5.6,0),(6.5,1),(7.1,1),(8.1,0),(PERCHED,0)])
        # Rising as it gathers the storm, flared as the bolt leaves its bill, ruffled at the second
        # strike, lowered as it settles.
        if name=='call':return keys(t,[(0,0),(.1,0),(.42,.75),(CALL_BOLT,1,'snap'),(2.2,1),(2.45,.85),(2.52,1,'snap'),(2.65,.8),(3.0,0)])
        # Rising as it gathers itself, flared at the scream, flaring again with each downbeat, lowered as it settles.
        if name=='summon':return keys(t,[(0,0),(.4,0),(1.3,.7),(SUMMON_CALL,1,'snap'),(SUMMON_BEATS[2]+.2,.9),(SUMMON-.3,0)])
        if name=='flight':return -1
        # Lifted as it gathers itself on the spire, swept flat as it launches.
        if name=='spire_flight':return keys(t,[(0,0),(.08,0),(SPIRE_PUSH-.05,.45),(SPIRE_PUSH+.22,-1),(SPIRE_FLIGHT,-1)])
        # Sleek as it comes in, slowly raised once it is down, at rest for the call.
        if name=='land':return keys(t,[(0,-1),(LAND_TOUCH-.05,-1),(LAND_TOUCH+.5,.35),(1.95,.35),(LAND,0)])
        if name=='hunched':return hunched_crest(t)
        if name=='stuck':return stuck_crest(t)
        # Lifted as it gathers itself on its spire, sleek from the launch, flared as it screeches in the
        # flare, still flared from the impact as stuck begins.
        if name=='dive':return keys(t,[(0,0),(.08,0),(DIVE_PUSH-.05,.45),(DIVE_PUSH+.22,-1),(2.55,-1),(2.72,1,'snap'),(3.0,stuck_crest(0))])
        # Flared as it tears free, swept back in the air, flared again as it slams.
        if name=='hop':return keys(t,[(0,stuck_crest(SMASH_PAUSE)),(.06,1,'snap'),(.35,-.8),(.8,-.8),(.95,.6),(1.2,stuck_crest(0))])
        if name=='bite':return REST+(1-REST)*envelope(t,0,1.1,1.5,.9,'ease','ease')
        if name=='bite_leap':return REST+(1-REST)*envelope(t,0,1.1,2.0,.9,'ease','ease')
        if name.startswith('swing'):return REST+(1-REST)*envelope(t,0,.8,1.0,.8,'ease','ease')
        if name=='scream':return REST+(1-REST)*envelope(t,0,1.0,1.3,.6,'ease','ease')
        if name=='wing':return REST+(1-REST)*envelope(t,0,.6,2.2,1.0,'ease','ease')
        if name=='wing_spiral':return keys(t,[(0,REST),(.6,1),(SPIRAL_LIFT,1),(1.1,-1),(SPIRAL_TOP-.1,-1),(SPIRAL_TOP+.15,1),
            (SPIRAL_DIVE,1),(2.75,-1),(3.1,-1),(SPIRAL_IMPACT,1),(5.0,REST)])
        # Raised as it challenges, swept back as it runs, flared as its head whips round.
        if name.startswith('stampede'):
            R=STAMPEDE_RUN;S={'stampede':1e6,'stampede_run':1e6,'stampede_skid':STAMPEDE_SKID_AT}[name]
            t+={'stampede':0,'stampede_run':STAMPEDE_LOOP,'stampede_skid':STAMPEDE_SKID_AT}[name]
            return keys(t,[(0,REST),(.4,1),(R-.1,1),(R+.1,-.6),(S,-.6),(S+.2,.3),(S+.75,.3),(S+.9,1),(S+1.5,1),(S+1.9,REST)])
        # Lifted as it crouches for the push, swept flat as it leaves the rock.
        if name=='takeoff':return keys(t,[(0,REST),(.1,REST),(.9,.6),(TAKEOFF_PUSH-.05,.6),(TAKEOFF_PUSH+.25,-1),(TAKEOFF,-1)])
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
        if name in ('perched','call','summon'):return [(perch_ground,1,capstone)]
        if name=='flight':return []
        if name=='spire_flight':return [(perch_ground,1-ease(t,SPIRE_PUSH+.07,SPIRE_PUSH+.22),capstone)]
        if name=='land':return [(perch_ground,ease(t,LAND_TOUCH-.05,LAND_TOUCH+.15),capstone)]
        if name=='dive':return [(perch_ground,1-ease(t,DIVE_PUSH+.07,DIVE_PUSH+.22),capstone),(ground,ease(t,2.88,3))]
        if name=='hop':return [(ground,1-ease(t,0,.15)),(ground,ease(t,1.08,1.2))]
        if name=='takeoff':return [(ground,1-ease(t,1.2,2.5))]
        if name=='pounce':return [(ground,1-ease(t,POUNCE_LAUNCH-.05,POUNCE_LAUNCH+.05)*(1-ease(t,POUNCE_IMPACT-.05,POUNCE_IMPACT+.05)))]
        if name=='wing':return [(ground,1-ease(t,WING_LIFT-.05,WING_LIFT+.1)*(1-ease(t,WING_IMPACT-.1,WING_IMPACT)))]
        if name=='wing_spiral':return [(ground,1-ease(t,SPIRAL_LIFT-.05,SPIRAL_LIFT+.1)*(1-ease(t,SPIRAL_IMPACT-.1,SPIRAL_IMPACT)))]
        if name=='bite_leap':return [(ground,1-ease(t,BITE_TAKEOFF-.05,BITE_TAKEOFF+.05)*(1-ease(t,BITE_LANDING-.05,BITE_LANDING+.05)))]
        return [(ground,1)]
    def motion(name,t):
        if name=='perched':return perched(t)
        if name=='flight':return flight(t)
        if name=='spire_flight':return spire_flight(t)
        if name=='hunched':return hunched(t)
        if name=='stuck':return stuck(t)
        if name=='call':return call(t)
        if name=='summon':return summon(t)
        if name=='land':return land(t)
        if name=='dive':return dive(t)
        if name=='hop':return hop(t)
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
        if name=='takeoff':return takeoff(t)
        if name=='pounce':return pounce(t)
        if name=='stampede':return stampede(t)
        if name=='stampede_run':return stampede(STAMPEDE_LOOP+t)
        if name=='stampede_skid':return stampede(STAMPEDE_SKID_AT+t,STAMPEDE_SKID_AT)
        raise ValueError(name)
    # Follow-through: in these clips the loose feathers trail the bones that carry them, swinging
    # on after a snap or a shove instead of moving rigidly with the head and body.
    loose=lambda part:[b['name'] for b in bones if part(b['name']) and b['cubes']]
    FEATHERS=[Spring(loose(lambda n:n.startswith('crest_plume_')),2.8,.25,turn=.7,limit=14),
        Spring(loose(lambda n:'_cheek_blue_' in n),3.4,.3,turn=.6,limit=10),
        Spring(loose(lambda n:'_cheek_' in n and '_blue_' not in n),3.8,.35,turn=.6,limit=8),
        Spring(['tail_fan'],1.6,.32,turn=.9,shove=.7,limit=9)]
    # The run loop has its own stride sway and no springs, so the chain's clips meet exactly.
    FOLLOW={'hunched':FEATHERS,'bite':FEATHERS,'bite_leap':FEATHERS,'swing_left':FEATHERS,'swing_right':FEATHERS,'scream':FEATHERS,'wing':FEATHERS,'wing_spiral':FEATHERS,'pounce':FEATHERS,'stampede':FEATHERS,'stampede_skid':FEATHERS}
    FOLLOW['flight']=FOLLOW['spire_flight']=FOLLOW['land']=FOLLOW['call']=FOLLOW['summon']=FEATHERS
    FOLLOW['takeoff']=FEATHERS
    FOLLOW['perched']=FEATHERS
    # Clips another clip continues without a crossfade, or that must end exactly at rest: their swing
    # settles by their end, as a loop's does.
    CHAINED={'stampede','spire_flight','land','call','summon'}
    CHAINED.add('takeoff')
    # The dive chain's clips cut into each other mid-motion, so their swing is at rest wherever another
    # clip takes over or hands over: the dive's and hop's at their slams, stuck's where the hop takes
    # over (it builds up from there, ringing on from the impact) and in its guard at the end.
    FOLLOW['dive']=FOLLOW['stuck']=FOLLOW['hop']=FEATHERS
    FADES={'dive':lambda t:1-ease(t,2.7,3.0),'hop':lambda t:1-ease(t,.85,1.2),
        'stuck':lambda t:ease(t,SMASH_PAUSE,1.0)*(1-ease(t,5.4,6.0))}
    # The phase change's flight takes over from the takeoff LEG_REACH before its end.
    FADES['takeoff']=lambda t:1-ease(t,TAKEOFF-LEG_REACH-.6,TAKEOFF-LEG_REACH)
    shapes={b['name']:shape(b) for b in bones if b.get('cubes')}
    def follow(frames,times,springs,loop,fade=None):
        worlds=[matrices(p) for p in frames]
        # A loop's or a chained clip's swing fades out over its last moments, so the seam meets
        # the next clip at rest; fade, a function of clip time, sets it instead.
        fade=[fade(t) for t in times] if fade else [1-ease(t,times[-1]-.6,times[-1]) if loop else 1 for t in times]
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
    THIN={'hunched','bite','bite_leap','swing_left','swing_right','scream','wing','wing_spiral','pounce','stampede','stampede_run','stampede_skid'};TOLERANCE={'rotation':.02,'position':.005,'scale':.0002}
    THIN.update(('flight','spire_flight','land','call','summon'))
    THIN.add('takeoff')
    THIN.update(('dive','stuck','hop'))
    # Frames another clip takes over from mid-clip keep their keys exactly.
    KEEP={'stuck':(SMASH_PAUSE,),'takeoff':(TAKEOFF-LEG_REACH,)}
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
        if name in FOLLOW:follow(posed,[i/KEY_FPS for i in frame_ticks],FOLLOW[name],loop or name in CHAINED,FADES.get(name))
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
            # Java linearly interpolates Euler channels. Store every bone's turn in the form nearest
            # its previous key, so equivalent forms never make it spin between keys.
            if frames:
                for n in p:p[n]['rotation']=continuous(frames[-1][n]['rotation'],p[n]['rotation'])
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
                kept=range(len(values)) if name not in THIN or b['name'] in exact else sorted(set(thin(frame_ticks,values,TOLERANCE[k]))
                    |{frame_ticks.index(round(at*KEY_FPS)) for at in KEEP.get(name,())})
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
