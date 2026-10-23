#!/usr/bin/env python3
"""Validate baked Euler curves, planted soles, contact clocks and preserved clips.

Samples the exported linear channels, not the author's smooth curves. This is
offline rig validation, not a Minecraft collision or appearance test.
"""
import argparse
import json
from pathlib import Path
import runpy
import numpy as np

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
P='animation.fractured_guardian.'
LENGTHS=dict(idle=6,walk=2,awaken=4.2,slam=2.8,throw=3.6,beam=3.6,
            leap_launch=.7,leap_air=1.8,leap_land=2.2,intro=23.8,
            phase_change=3.2,slam_fast=2.25,throw_fast=2.35,fan=2.4,
            slam_hold_10=3.3,slam_hold_16=3.6,slam_hold_22=3.9,core_pulse=3.8)


def validate(baseline=None):
    rig=runpy.run_path(str(HERE.parent/'v4-expressive/preview_motion.py'))
    review=runpy.run_path(str(HERE/'build_preview.py'))['REWORKED']
    clips=json.loads((ROOT/'src/main/resources/assets/elementalwands/geckolib/animations/fractured_guardian.animation.json').read_text())['animations']
    assert {k.removeprefix(P):v['animation_length'] for k,v in clips.items()}==LENGTHS
    if baseline:
        before=json.loads(Path(baseline).read_text())['animations']
        changed={k.removeprefix(P) for k in clips if clips[k]!=before[k]}
        assert changed==set(review),changed
        print('Baseline: only marked clips changed; intro, throws and leap chain preserved exactly.')
    mesh=[(c['bone'],np.array(c['vertices']),c['name']) for c in rig['mesh']]
    report={}
    for name in review:
        clip=clips[P+name]
        for bone,chs in clip['bones'].items():
            for kind,keys in chs.items():
                ordered=sorted((float(t),v) for t,v in keys.items())
                values=np.array([v for _,v in ordered])
                assert np.isfinite(values).all(),(name,bone,kind)
                assert ordered[0][0]>=0 and ordered[-1][0]<=clip['animation_length']
                if kind=='rotation':
                    assert abs(values).max()<180,(name,bone,'Euler branch')
                    assert abs(np.diff(values,axis=0)).max(initial=0)<90,(name,bone,'Euler jump')
        initial=rig['matrices'](clip,0)
        feet={n:v@initial[b][:3,:3].T+initial[b][:3,3] for b,v,n in mesh if b.endswith('_foot')}
        lowest=999;drift=0;head_error=0
        for t in np.linspace(0,clip['animation_length'],round(clip['animation_length']*120)+1):
            mats=rig['matrices'](clip,float(t))
            for bone,vertices,cube_name in mesh:
                m=mats[bone];world=vertices@m[:3,:3].T+m[:3,3]
                lowest=min(lowest,float(world[:,1].min()))
                if cube_name in feet:drift=max(drift,float(abs(world-feet[cube_name]).max()))
            if name=='beam':
                hp=np.array(rig['bones']['head']['pivot']);m=mats['head']
                head_error=max(head_error,float(np.linalg.norm(m[:3,:3]@hp+m[:3,3]-hp)))
        assert lowest>-.05,(name,'floor penetration',lowest)
        assert drift<.03,(name,'planted foot drift',drift)
        assert head_error<.02,(name,'beam socket',head_error)
        report[name]={'minimum_vertex':round(lowest,6),'maximum_foot_drift':round(drift,6),
                      'beam_pivot_error':round(head_error,6),'samples_per_second':120}
    slam=clips[P+'slam'];fast=clips[P+'slam_fast']
    for bone,chs in slam['bones'].items():
        for kind,keys in chs.items():
            remap=lambda t:t*1.05/1.3 if t<=1.3 else 1.05+(t-1.3)*1.2/1.5
            expected={str(round(remap(float(t)),5)):v for t,v in keys.items()}
            assert fast['bones'][bone][kind]==expected,(bone,kind,'fast remap')
    for hold in (10,16,22):
        clip=clips[P+f'slam_hold_{hold}'];end=1.075+hold/20
        for bone,chs in clip['bones'].items():
            for kind,keys in chs.items():
                reference=rig['sample'](slam['bones'][bone][kind],1.075)
                for t in np.linspace(1.075,end,25):
                    assert np.max(abs(rig['sample'](keys,t)-reference))<.00002
    for name,impact in [('slam',1.3),('slam_fast',1.05),('slam_hold_10',1.8),('slam_hold_16',2.1),('slam_hold_22',2.4)]:
        clip=clips[P+name];mats=rig['matrices'](clip,impact)
        for side in ('right','left'):
            lows=[]
            for bone,v,_ in mesh:
                if bone.startswith(side) and any(k in bone for k in ('finger','thumb','hand')):
                    m=mats[bone];lows.append((v@m[:3,:3].T+m[:3,3])[:,1].min())
            assert -.05<min(lows)<.3,(name,side,'fist contact',min(lows))
    out=ROOT/'.local-previews/guardian-combat-review'
    out.mkdir(parents=True,exist_ok=True)
    (out/'motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    print('PASS: fixed lengths; continuous Euler branches; stationary soles; beam pivot; slam contacts, holds and fast remap.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline',type=Path)
    args=parser.parse_args()
    validate(args.baseline)
