#!/usr/bin/env python3
"""Numerical runtime contracts, including half-tick interpolation; no native claim."""
import json,math
from pathlib import Path
from types import SimpleNamespace
import numpy as np
from PIL import Image
from validate_review import matrix,sample
from head_lock import verify
HERE=Path(__file__).resolve().parent
ASSETS=HERE.parents[1]/'src/main/resources/assets/elementalwands'

def read(path):return json.loads(path.read_text())
def unreflect(item):
    if 'rotation' in item:
        r=item['rotation'];item['rotation']=[r[0],-r[1],-r[2]] if isinstance(r,list) else {k:[v[0],-v[1],-v[2]] for k,v in r.items()}

def main():
    from build_runtime import SCALE,DEFINITIONS,WING_REST,STUCK_FREE,SMASH_PAUSE,DIVE_APEXES,HOP_LOWEST,stuck_slap,KEY_FPS,KNEADS,BITE_IMPACT,BITE_TAKEOFF,BITE_LANDING,BITE_LEAP_IMPACT,SWEEP_START,SWEEP_END,SCREAM_IMPACT,WING_LIFT,WING_APEX,WING_IMPACT,POUNCE_LAUNCH,POUNCE_IMPACT,SPIRE_PUSH,SPIRE_FLIGHT,LAND_TOUCH,LAND,LEG_REACH,STAMPEDE_RUN,STAMPEDE_LOOP,MODEL,shape,bill,glowing
    approved=read(MODEL/'storm_roc.geo.json')
    geo=read(ASSETS/'geckolib/models/storm_roc.geo.json')
    for b in geo['minecraft:geometry'][0]['bones']:
        for item in [b]+b.get('cubes',[]):unreflect(item)
    for b,a in zip(geo['minecraft:geometry'][0]['bones'],approved['minecraft:geometry'][0]['bones'],strict=True):
        assert [b['name'],b.get('parent'),b['pivot'],b.get('rotation')]==[a['name'],a.get('parent'),a['pivot'],a.get('rotation')],b['name']
        assert b.get('cubes',[])==a.get('cubes',[]),('cubes',b['name'])
    assert (MODEL/'storm_roc.png').read_bytes()==(ASSETS/'textures/entity/storm_roc.png').read_bytes(),'atlas'
    assert glowing(MODEL/'storm_roc.png',MODEL/'storm_roc_glowmask.png')==(ASSETS/'textures/entity/storm_roc_glowmask.png').read_bytes(),'glow mask'
    clips={k.removeprefix('animation.storm_roc.'):v for k,v in read(ASSETS/'geckolib/animations/storm_roc.animation.json')['animations'].items()}
    sockets=read(ASSETS/'storm_roc_sockets.json')
    assert set(clips)==set(sockets)==set(DEFINITIONS)
    bones=geo['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones}
    for clip in clips.values():
        for c in clip['bones'].values():unreflect(c)
    def world(name,t):
        mats={}
        for b in bones:mats[b['name']]=mats.get(b.get('parent'),np.eye(4))@matrix(b,clips[name]['bones'].get(b['name'],{}),float(t))
        return mats
    def pt(m,n,v=None):return (m[n]@np.r_[by[n]['pivot'] if v is None else v,1])[:3]
    tip=bill(by['upper_beak'])
    landmarks={'head':('head',by['head']['pivot']),'beak':('upper_beak',tip),
        'neck':('neck_upper',by['neck_upper']['pivot']),'body':('body',[0,65,5]),
        'left_foot':('left_foot',by['left_foot']['pivot']),'right_foot':('right_foot',by['right_foot']['pivot'])}
    planted={'perched':[(0,DEFINITIONS['perched'][0],'perched')],'call':[(0,3,'perched')],
        'land':[(LAND_TOUCH+.08,LAND,'perched')],'hunched':[(0,DEFINITIONS['hunched'][0],'hunched')],
        'bite':[(0,DEFINITIONS['bite'][0],'hunched')],
        'bite_leap':[(0,BITE_TAKEOFF-.03,'hunched'),(BITE_LANDING+.05,DEFINITIONS['bite_leap'][0],'hunched')],
        'swing_left':[(0,DEFINITIONS['swing_left'][0],'hunched')],'swing_right':[(0,DEFINITIONS['swing_right'][0],'hunched')],'scream':[(0,DEFINITIONS['scream'][0],'hunched')],
        'wing':[(0,WING_LIFT-.06,'hunched'),(WING_IMPACT+.05,DEFINITIONS['wing'][0],'hunched')],'takeoff':[(0,1.2,'hunched')],
        # The Raptor Strike's feet leave the rock between its last planted key and the 1.575 one.
        'stampede':[(0,.2,'hunched')],'stampede_skid':[(1.45,DEFINITIONS['stampede_skid'][0],'hunched')],
        'pounce':[(0,POUNCE_LAUNCH-.04,'hunched'),(POUNCE_IMPACT+.05,DEFINITIONS['pounce'][0],'hunched')],
        # The spire hop's talons grip its spire through the crouch and the push.
        'spire_flight':[(0,SPIRE_PUSH+.1,'perched')]}
    reference={name:world(name,0) for name in ('perched','hunched')}
    worst=foot_error=socket_error=0.;snapshots={}
    for name,clip in clips.items():
        duration,loop=DEFINITIONS[name]
        assert clip['animation_length']==duration and clip['loop']==loop,name
        assert len(sockets[name])==round(duration*20)+1,(name,'socket frame count')
        authored={round(float(t),6) for c in clip['bones'].values() for keys in c.values() if isinstance(keys,dict) for t in keys}
        snapshots[name]=[]
        for index,t in enumerate(np.arange(0,duration+.001,1/(KEY_FPS*2))):
            mats=world(name,t);snapshots[name].append(mats)
            for n,m in mats.items():assert np.isfinite(m).all(),(name,t,n)
            head=mats['head'][:3,:3]
            assert np.allclose(head.T@head,np.eye(3),atol=.0001),(name,t,'head scaled or sheared')
            for side in ['left','right']:
                points=[pt(mats,side+'_'+joint) for joint in ['thigh','shin','tarsus','foot']]
                err=max(abs(np.linalg.norm(b-a)-length) for a,b,length in zip(points,points[1:],[14,28,26]))
                worst=max(worst,err);assert err<.025,(name,t,side,'leg length',err)
                # The brawl guard lifts each foot only for its own talon knead.
                kneading=name=='hunched' and any(side==foot and a-.001<=t<=b+.001 for foot,a,b in KNEADS)
                for a,b,ref in planted.get(name,[]):
                    if a-.001<=t<=b+.001 and not kneading:
                        drift=np.linalg.norm(points[-1]-pt(reference[ref],side+'_foot'))*SCALE
                        foot_error=max(foot_error,drift)
                        # Exported keys are pinned exactly. Between-key local Euler
                        # interpolation may deviate by at most 2 cm between keys.
                        assert drift<(.0001 if round(float(t),6) in authored else .02),(name,t,side,'planted foot',drift)
            if index%(KEY_FPS//10)==0:
                for key,(n,v) in landmarks.items():
                    err=np.linalg.norm(pt(mats,n,v)*SCALE-sockets[name][index//(KEY_FPS//10)][key])
                    socket_error=max(socket_error,err)
                    assert err<.0002,(name,t,key,'socket mismatch',err)
        if loop:
            for n in by:assert np.allclose(snapshots[name][0][n],snapshots[name][-1][n],atol=.003),(name,n,'loop seam')
        print(f'Checked {name}',flush=True)
    def endpoint(a,ta,b,tb,atol=.003):
        ma,mb=world(a,ta),world(b,tb)
        for n in by:assert np.allclose(ma[n],mb[n],atol=atol),(a,b,n,'endpoint')
    # The dive and each chain hop slam into stuck's first frame; the hop takes over from stuck where the
    # chain smash's pause ends.
    for a,ta,b,tb in [('dive',3,'stuck',0),('hop',0,'stuck',SMASH_PAUSE),('hop',1.2,'stuck',0),
        ('stuck',6,'hunched',0),('land',LAND,'perched',0),
        ('call',0,'perched',0),('call',3,'perched',0),('spire_flight',0,'perched',0)]:endpoint(a,ta,b,tb)
    # The spire hop hands over to the landing with no crossfade, its legs already reaching.
    endpoint('spire_flight',SPIRE_FLIGHT,'land',0)
    # So does the takeoff, to the landing or, bound for the phase change, to the flight loop
    # LEG_REACH sooner, before its legs reach for a spire.
    endpoint('takeoff',DEFINITIONS['takeoff'][0],'land',0)
    endpoint('takeoff',DEFINITIONS['takeoff'][0]-LEG_REACH,'flight',0)
    # The Thunder Charge's clips hand over with no crossfade: the windup into the run loop, and the
    # loop, on any stride boundary, into the skid.
    endpoint('stampede',STAMPEDE_LOOP,'stampede_run',0,.05)
    endpoint('stampede_run',DEFINITIONS['stampede_run'][0],'stampede_skid',0,.05)
    # The Storm Sweep scythes its whole front at chest height through the server's hit ticks.
    sweep=slice(round(SWEEP_START*20),round(SWEEP_END*20)+1)
    for name in ('swing_left','swing_right'):
        points=np.array([frame['beak'] for frame in sockets[name][sweep]])
        assert np.all((points[:,1]>=1.0)&(points[:,1]<=1.1)),(name,'chest-height sweep',points[:,1])
        assert points[0,0]*points[-1,0]<0 and np.ptp(points[:,0])>16,(name,'sweep must cross its whole front')
        assert np.all(points[:,2]<0),(name,'sweep must stay in front of its chest')
    # The server hurts anyone under a brawl foot that moves 0.1 blocks in a tick; a knead never may.
    for side in ('left','right'):
        steps=np.linalg.norm(np.diff([frame[side+'_foot'] for frame in sockets['hunched']],axis=0),axis=1)
        assert steps.max()<.075,('hunched knead would stomp',side,steps.max())
        for foot,a,b in KNEADS:
            if foot==side:assert np.allclose(sockets['hunched'][round(a*20)][side+'_foot'],sockets['hunched'][round(b*20)][side+'_foot'],atol=1e-4),('knead must set the foot back',side)
    pin=np.array([frame['beak'] for frame in sockets['stuck'][:round(STUCK_FREE*20)+1]])
    assert np.max(np.ptp(pin,axis=0))<.0001,('Beak moved before its release at',STUCK_FREE)
    assert -.99<pin[0,1]<-.8,'Bill must be visibly buried at 1.25x'
    # At subframes, linear Euler interpolation may orbit the pin very slightly.
    pin_error=max(np.linalg.norm(pt(m,'upper_beak',tip)*SCALE-pin[0]) for m in snapshots['stuck'][:round(STUCK_FREE*KEY_FPS*2)+1])
    assert pin_error<.06,('interpolated beak pin',pin_error)
    # Coming down with the server, its bill stays above the rock until it strikes: in the dive along
    # either phase's descent, and in the chain hop even on its lowest arc.
    smooth=lambda f:f*f*(3-2*f);dive=DEFINITIONS['dive'][0]
    def descent(t):return min(h*(1-smooth(min(1,max(0,(t-at)/(dive-at))))) for at,h in DIVE_APEXES)
    for k in range(round(min(at for at,_ in DIVE_APEXES)*20),round(dive*20)):
        y=sockets['dive'][k]['beak'][1]+descent(k/20)
        assert y>=0,('dive bill under the rock before its strike',k/20,y)
    hop=DEFINITIONS['hop'][0]
    for k in range(3,round(hop*20)):
        y=sockets['hop'][k]['beak'][1]+HOP_LOWEST*math.sin(math.pi*k/20/hop)
        assert y>=0,('hop bill under the rock before its slam',k/20,y)
    # Thunder Bite, standing and leaping: reared tall and looking down at its prey when its aim
    # locks (tick 24), then first at its furthest at the impact tick, where the server bursts its
    # electricity, at player height. Both clips match until the lock, where the server may switch.
    for name,at in (('bite',BITE_IMPACT),('bite_leap',BITE_LEAP_IMPACT)):
        impact=round(at*20)
        bite=np.array([f['beak'] for f in sockets[name]]);head=np.array([f['head'] for f in sockets[name]])
        assert bite[:impact,2].min()>bite[impact,2] and bite[impact,2]<bite[:,2].min()+.01,(name,'reaches its furthest (to a centimetre) at its impact tick')
        assert .6<bite[impact,1]<1.8,(name,'must close at player height',bite[impact])
        assert head[24,1]-head[impact,1]>4.5,(name,'needs a tall, slow rear-up while it aims')
        reared=head[24]-bite[24];assert math.degrees(math.atan2(reared[1],-reared[2]))>25,(name,'reared up, its head must look down at its prey')
    # Thinned feather keys may land up to their 0.02 degree tolerance apart, a few hundredths of a
    # model pixel: invisible, and the game crossfades the switch anyway.
    endpoint('bite',1.2,'bite_leap',1.2,.05)
    jaw=clips['scream']['bones']['lower_beak']['rotation']
    assert abs(sample(jaw,SCREAM_IMPACT,[0,0,0])[0]-48)<.0001,'Scream jaw misses server impact tick'
    direction=world('scream',SCREAM_IMPACT)['head'][:3,:3]@np.array([0,0,-1])
    assert np.dot(direction,[0,0,-1])>.999,'Scream must aim straight forward at its impact'
    def shoulder(name,t):return sample(clips[name]['bones']['left_wing_shoulder'].get('rotation'),t,[0,0,0])[2]
    # The Thunderclap is off the rock between its lift and its landing, and lands on its tick.
    for side in ('left','right'):
        feet_y=np.array([f[side+'_foot'][1] for f in sockets['wing']])
        assert feet_y[round(WING_APEX*20)]>feet_y[0]+1,('Thunderclap legs must tuck up in the air',side)
        assert abs(feet_y[round(WING_IMPACT*20)+1]-feet_y[0])<.02,('Thunderclap must land on its tick',side)
    # The Raptor Strike flies with its legs tucked up and back, swings its talons far forward to
    # strike, and is down on its impact tick.
    for side in ('left','right'):
        foot=np.array([f[side+'_foot'] for f in sockets['pounce']])
        assert foot[round(2.0*20),1]>foot[0,1]+3,('Raptor Strike legs must tuck up in flight',side)
        assert foot[round((POUNCE_IMPACT-.1)*20),2]<foot[round(2.0*20),2]-4,('Raptor Strike talons must swing forward to strike',side)
        assert abs(foot[round(POUNCE_IMPACT*20)+1,1]-foot[0,1])<.02,('Raptor Strike must land on its tick',side)
    # The Thunder Charge's feet stride off the rock in each loop of its run (the server cracks
    # lightning where each comes down, below 0.45 blocks) and are both down through its skid.
    rest=np.array([f[side+'_foot'][1] for f in sockets['stampede'][:1] for side in ('left','right')])
    for k,side in enumerate(('left','right')):
        y=np.array([f[side+'_foot'][1] for f in sockets['stampede_run']])
        falls=np.sum((y[:-1]>.45)&(y[1:]<=.45))
        assert y.max()>rest[k]+1 and falls==1,('Thunder Charge must stride once per loop on each foot',side,y.max(),falls)
        skid=np.array([f[side+'_foot'][1] for f in sockets['stampede_skid']])
        assert np.all(np.abs(skid[3:16]-rest[k])<.02),('Thunder Charge skids on both feet',side)
    # The takeoff's downbeat sweeps through level as its feet leave the rock on tick 24: its wrist is
    # high above its shoulder just before, level with it at the push and well below it just after.
    # Nothing touches the rock, and once its legs have tucked they ride as rigidly as the flight's
    # until they reach for its spire, LEG_REACH before the end.
    from build_runtime import TAKEOFF_PUSH
    def wrist(t):m=world('takeoff',t);return pt(m,'left_wing_wrist')[1]-pt(m,'left_wing_shoulder')[1]
    assert wrist(TAKEOFF_PUSH-.2)>40 and abs(wrist(TAKEOFF_PUSH))<12 and wrist(TAKEOFF_PUSH+.2)<-40,'Takeoff downbeat misses tick 24'
    feathers=[(b['name'],shape(b)) for b in bones if b.get('cubes') and any(k in b['name'] for k in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries'))]
    soles=min((shape(by[n])@snapshots['takeoff'][0][n].T)[:,1].min() for n in ('left_foot','right_foot'))
    clearance=min((v@m[n].T)[:,1].min() for m in snapshots['takeoff'] for n,v in feathers)-soles
    assert clearance*SCALE>.5,('Takeoff wings must clear the rock',clearance*SCALE)
    tuck={n:np.linalg.inv(snapshots['flight'][0]['body'])@snapshots['flight'][0][n] for n in by if n.split('_',1)[-1] in ('thigh','shin','tarsus','foot')}
    for index,m in enumerate(snapshots['takeoff']):
        if not 1.9<=index/(KEY_FPS*2)<=DEFINITIONS['takeoff'][0]-LEG_REACH:continue
        for n,rest in tuck.items():assert np.allclose(np.linalg.inv(m['body'])@m[n],rest,atol=.003),('takeoff tucked leg popped',index/(KEY_FPS*2),n)
    # Skyward from its stuttered snaps until it whips down onto its mark with the first strike.
    for t in [.45,1.2,1.75]:
        d=world('call',t)['head'][:3,:3]@np.array([0,0,-1])
        assert d[1]>.9,('call skyward hold',t,d)
    d=world('call',2)['head'][:3,:3]@np.array([0,0,-1])
    assert d[1]<-.15 and d[2]<-.9,('call command at first bolt',d)
    for m in snapshots['perched']:
        forward=m['head'][:3,:3]@np.array([0,0,-1]);yaw=math.degrees(math.atan2(forward[0],-forward[2]))
        assert abs(yaw)<=45.01,('perch yaw leaves look-at room',yaw)
    # A larger power stroke deliberately replaces the old shallow study flap.
    flight=snapshots['flight'];wrists=np.array([pt(m,'left_wing_wrist')*SCALE for m in flight])
    excursion=np.ptp(wrists[:,1]);assert 8<excursion<16,('flight wrist excursion',excursion)
    for m in flight:
        assert abs(m['root'][1,0])<.00001 and abs(m['body'][1,0])<.00001,'Flight banking belongs to Java'
    # Flight's legs are carried rigidly tucked under the body, including both seam
    # blends. A ground IK solve here used to kick them out for the first six ticks.
    tucked={side+'_'+joint:np.linalg.inv(flight[0]['body'])@flight[0][side+'_'+joint]
        for side in ('left','right') for joint in ('thigh','shin','tarsus','foot')}
    for index,m in enumerate(flight):
        body_inverse=np.linalg.inv(m['body'])
        for name,rest in tucked.items():
            assert np.allclose(body_inverse@m[name],rest,atol=.003),('flight tucked leg popped',index/(KEY_FPS*2),name)
    # Once the spire hop's legs have tucked, they ride as rigidly as the flight's until they reach
    # for the next spire.
    for index,m in enumerate(snapshots['spire_flight']):
        if not SPIRE_PUSH+.53<=index/(KEY_FPS*2)<=SPIRE_FLIGHT-LEG_REACH:continue
        body_inverse=np.linalg.inv(m['body'])
        for name,rest in tucked.items():
            assert np.allclose(body_inverse@m[name],rest,atol=.003),('spire hop tucked leg popped',index/(KEY_FPS*2),name)
    looks=np.array([m['head'][:3,:3]@np.array([0,0,-1]) for m in flight])
    assert looks[:,1].min()<-.3 and np.ptp(looks[:,0])>.5,'Flight must scan down and to both sides'
    corners=[]
    for b in bones:
        if not any(k in b['name'] for k in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries')):continue
        vs=shape(b)
        if len(vs):corners.append((b['name'],vs))
    spans=[np.ptp(np.concatenate([(v@m[n].T)[:,0] for n,v in corners]))*SCALE for m in flight]
    assert 32<max(spans)<36.5,('flight wingspan',max(spans))
    wing_error=0
    for i in range(round(STUCK_FREE*20)+1):
        t=i/20;m=snapshots['stuck'][i*(KEY_FPS//10)]
        for label in ('left','right'):
            low=min(np.min(v@m[n][1]) for n,v in corners if n.startswith(label+'_'))*SCALE
            target=WING_REST+stuck_slap(t,label)
            wing_error=max(wing_error,abs(low-target))
    assert wing_error<.02,('wing/rock contact solve',wing_error)
    # The clips are still authored on the cube study, so keep its head lock intact too.
    verify(SimpleNamespace(bones=read(HERE/'candidate/storm_roc.geo.json')['minecraft:geometry'][0]['bones']),Image.open(HERE/'candidate/storm_roc.png'),Image.open(HERE/'candidate/storm_roc_glowmask.png'))
    print(f'PASS: {len(clips)} clips at {KEY_FPS*2}fps; study head lock; model cube/UV basis reversal; model atlas and glow; max leg error {worst:.6f}px; foot drift {foot_error:.6f} blocks; socket error {socket_error:.6f} blocks; pin interpolation {pin_error:.5f} blocks; wing contact {wing_error:.5f} blocks; flight span {max(spans):.3f}, wrist excursion {excursion:.3f} blocks; endpoints and server impact times.')
if __name__=='__main__':main()
