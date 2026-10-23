"""Add the double-shockwave leap without changing any approved V4 clip or model geometry."""
from pathlib import Path
import argparse,copy,json,runpy,math
import numpy as np
HERE=Path(__file__).resolve().parent
BASE=HERE.parent/'v4-expressive'
h=runpy.run_path(str(BASE/'build_animations.py'))
choreography=runpy.run_path(str(HERE/'heavy_leap.py'))

def pose(name,t):
    return choreography['pose'](name,t,h)

def build():
    data=json.loads((BASE/'fractured_guardian.animation.json').read_text())
    for name,duration in [('leap_launch',.7),('leap_air',1.8),('leap_land',2.2)]:
        clip={'loop':False,'animation_length':duration,'bones':{}}
        for i in range(round(duration*160)+1):
            t=round(i/160,5)
            for bone,channels in pose(name,t).items():
                for kind,value in channels.items():
                    clip['bones'].setdefault(bone,{}).setdefault(kind,{})[str(t)]=[round(float(x),5) for x in value]
        for channels in clip['bones'].values():
            for kind,keys in list(channels.items()):channels[kind]=h['reduce_keys'](keys,.0015)
        data['animations']['animation.fractured_guardian.'+name]=clip
    return data

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    data=build()
    project=h['legacy']['blockbench'](data);project['name']='Fractured Guardian — Double shockwave leap 05'
    for name,value in [('fractured_guardian.animation.json',data),('fractured_guardian.bbmodel',project)]:
        text=json.dumps(value,indent=2)+'\n';path=HERE/name
        if args.check:assert path.read_text()==text,f'Stale Guardian V5 output: {path}'
        else:path.write_text(text)
    base=json.loads((BASE/'fractured_guardian.animation.json').read_text())
    assert all(data['animations'][name]==clip for name,clip in base['animations'].items())
    print('Added launch, airborne and landing clips; all six V4 animations preserved exactly.')
