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
    from build_runtime import SCALE,DEFINITIONS,WING_REST,WING_SLAP,KEY_FPS,MODEL,shape,bill,glowing
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
        'land':[(.3,.8,'perched')],'hunched':[(0,DEFINITIONS['hunched'][0],'hunched')],
        'peck':[(0,1.8,'hunched')],'swing_left':[(0,1.8,'hunched')],
        'swing_right':[(0,1.8,'hunched')],'scream':[(0,2.2,'hunched')],
        'wing':[(0,2,'hunched')],'takeoff':[(0,1.2,'hunched')],
        'pounce':[(0,1.2,'hunched'),(2.2,3.2,'hunched')]}
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
                for a,b,ref in planted.get(name,[]):
                    if a-.001<=t<=b+.001:
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
    def endpoint(a,ta,b,tb):
        ma,mb=world(a,ta),world(b,tb)
        for n in by:assert np.allclose(ma[n],mb[n],atol=.003),(a,b,n,'endpoint')
    for a,ta,b,tb in [('dive',3,'stuck',0),('hop',0,'stuck',0),('hop',1.2,'stuck',0),
        ('stuck',6,'hunched',0),('land',0,'flight',0),('land',.8,'perched',0),
        ('call',0,'perched',0),('call',3,'perched',0),('takeoff',2.5,'flight',0)]:endpoint(a,ta,b,tb)
    for name in ('swing_left','swing_right'):
        points=np.array([frame['beak'] for frame in sockets[name][16:24]])
        assert np.all((points[:,1]>=1.0)&(points[:,1]<=1.1)),(name,'chest-height sweep',points[:,1])
        assert points[0,0]*points[-1,0]<0 and np.ptp(points[:,0])>12,(name,'sweep must cross the arena front')
        assert np.all(points[:,2]<-5),(name,'sweep behind body')
    pin=np.array([frame['beak'] for frame in sockets['stuck'][:114]])
    assert np.max(np.ptp(pin,axis=0))<.0001,'Beak moved before release at 5.65s'
    assert -.99<pin[0,1]<-.8,'Bill must be visibly buried at 1.25x'
    # At subframes, linear Euler interpolation may orbit the pin very slightly.
    pin_error=max(np.linalg.norm(pt(m,'upper_beak',tip)*SCALE-pin[0]) for m in snapshots['stuck'][:round(5.65*KEY_FPS*2)+1])
    assert pin_error<.06,('interpolated beak pin',pin_error)
    peck=np.array([f['beak'] for f in sockets['peck']])
    assert peck[:,2].argmin()==12,'Peck first reaches maximum extension at t=.60'
    assert peck[8,1]-peck[12,1]>3 and peck[8,2]-peck[12,2]>2,'Peck needs a large readable coil/strike'
    assert np.allclose(peck[12],peck[14],atol=.0001),'Peck must stick briefly after impact'
    jaw=clips['scream']['bones']['lower_beak']['rotation']
    assert abs(sample(jaw,1.2,[0,0,0])[0]-48)<.0001,'Scream jaw misses server impact tick'
    direction=world('scream',1.2)['head'][:3,:3]@np.array([0,0,-1])
    assert np.dot(direction,[0,0,-1])>.999,'Scream must aim straight forward at 1.20s'
    def shoulder(name,t):return sample(clips[name]['bones']['left_wing_shoulder'].get('rotation'),t,[0,0,0])[2]
    assert shoulder('wing',.9)>35 and shoulder('wing',1)<-35,'Wing slam misses tick 20'
    assert shoulder('takeoff',1.1)>35 and shoulder('takeoff',1.2)<-35,'Takeoff downbeat misses tick 24'
    for t in [.45,1.2,1.9]:
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
    corners=[]
    for b in bones:
        if not any(k in b['name'] for k in ('wing','primary','secondary','covert','leading','root_','primaries','secondaries')):continue
        vs=shape(b)
        if len(vs):corners.append((b['name'],vs))
    spans=[np.ptp(np.concatenate([(v@m[n].T)[:,0] for n,v in corners]))*SCALE for m in flight]
    assert 32<max(spans)<36.5,('flight wingspan',max(spans))
    wing_error=0
    for i in range(114):
        t=i/20;m=snapshots['stuck'][i*(KEY_FPS//10)]
        for label,side in (('left',1),('right',-1)):
            low=min(np.min(v@m[n][1]) for n,v in corners if n.startswith(label+'_'))*SCALE
            target=WING_REST+WING_SLAP*max(0,math.sin(t*7+side*.9))**2
            wing_error=max(wing_error,abs(low-target))
    assert wing_error<.02,('wing/rock contact solve',wing_error)
    # The clips are still authored on the cube study, so keep its head lock intact too.
    verify(SimpleNamespace(bones=read(HERE/'candidate/storm_roc.geo.json')['minecraft:geometry'][0]['bones']),Image.open(HERE/'candidate/storm_roc.png'),Image.open(HERE/'candidate/storm_roc_glowmask.png'))
    print(f'PASS: {len(clips)} clips at {KEY_FPS*2}fps; study head lock; model cube/UV basis reversal; model atlas and glow; max leg error {worst:.6f}px; foot drift {foot_error:.6f} blocks; socket error {socket_error:.6f} blocks; pin interpolation {pin_error:.5f} blocks; wing contact {wing_error:.5f} blocks; flight span {max(spans):.3f}, wrist excursion {excursion:.3f} blocks; endpoints and server impact times.')
if __name__=='__main__':main()
