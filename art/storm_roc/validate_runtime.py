#!/usr/bin/env python3
"""Check runtime basis adaptation, rigid head, leg lengths, timing, and pinned beak.

This is numerical export validation, not native Minecraft appearance validation.
"""
import copy,json
from pathlib import Path
import numpy as np
from validate_review import matrix,sample
HERE=Path(__file__).resolve().parent
ASSETS=HERE.parents[1]/'src/main/resources/assets/elementalwands'

def read(path):return json.loads(path.read_text())
def unreflect(item):
    if 'rotation' in item:
        r=item['rotation'];item['rotation']=[r[0],-r[1],-r[2]] if isinstance(r,list) else {k:[v[0],-v[1],-v[2]] for k,v in r.items()}
def main():
    approved=read(HERE/'candidate/storm_roc.geo.json')
    geo=read(ASSETS/'geckolib/models/storm_roc.geo.json')
    for b in geo['minecraft:geometry'][0]['bones']:
        for item in [b]+b.get('cubes',[]):unreflect(item)
    assert geo==approved,'Gecko basis reversal must recover every approved geometry/UV record exactly'
    for name in ['storm_roc.png','storm_roc_glowmask.png']:
        assert (HERE/'candidate'/name).read_bytes()==(ASSETS/'textures/entity'/name).read_bytes(),name
    clips=read(ASSETS/'geckolib/animations/storm_roc.animation.json')['animations']
    sockets=read(ASSETS/'storm_roc_sockets.json')
    bones=geo['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones}
    worst=0
    for name,clip in clips.items():
        for c in clip['bones'].values():unreflect(c)
        # Half-tick samples also inspect the interpolation between exported keys.
        for t in np.arange(0,clip['animation_length']+.001,.025):
            mats={}
            for b in bones:
                mats[b['name']]=mats.get(b.get('parent'),np.eye(4))@matrix(b,clip['bones'].get(b['name'],{}),float(t))
                assert np.isfinite(mats[b['name']]).all(),(name,t,b['name'])
            head=mats['head'][:3,:3]
            assert np.allclose(head.T@head,np.eye(3),atol=.0001),(name,t,'head scaled or sheared')
            for side in ['left','right']:
                points=[(mats[side+'_'+joint]@np.r_[by[side+'_'+joint]['pivot'],1])[:3] for joint in ['thigh','shin','tarsus','foot']]
                err=max(abs(np.linalg.norm(b-a)-length) for a,b,length in zip(points,points[1:],[14,28,26]))
                worst=max(worst,err)
                assert err<.025,(name,t,side,err)
    for name in ('swing_left','swing_right'):
        heights=[frame['beak'][1] for frame in sockets[name][16:24]]
        assert all(1<=y<=2.3 for y in heights),(name,'grounded sweep height',heights)
    pin=np.array([frame['beak'] for frame in sockets['stuck'][:114]])
    assert np.max(np.ptp(pin,axis=0))<.0001,'Beak moved before final release'
    assert sockets['stuck'][-1]==sockets['hunched'][0],'Release endpoint differs from brawl pose'
    candidate=read(HERE/'candidate/storm_roc.animation.json')['animations']['animation.storm_roc.peck']
    peck=clips['animation.storm_roc.peck']
    for name,ch in candidate['bones'].items():
        for channel,keys in ch.items():
            default=[1,1,1] if channel=='scale' else [0,0,0]
            assert np.allclose(sample(keys,.5,default),sample(peck['bones'][name].get(channel),.6,default),atol=.0002),('peck impact',name,channel)
    jaw=clips['animation.storm_roc.scream']['bones']['lower_beak']['rotation']
    assert abs(sample(jaw,1.2,[0,0,0])[0]-48)<.0001,'Scream jaw misses server impact tick'
    print(f'PASS: {len(clips)} clips at 40fps; rigid head; max leg error {worst:.6f} model pixels; exact geometry/UV basis reversal; byte-identical atlases; pinned beak; server impact frames.')
if __name__=='__main__':main()
