#!/usr/bin/env python3
"""Build an offline review of every packaged Guardian clip in .local-previews/."""
import argparse
import base64
import json
from pathlib import Path
import runpy

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'.local-previews/guardian-combat-review'
PREFIX='animation.fractured_guardian.'
REWORKED={
    'fan': {'description':'Plant and load over 0.75 s. Release three deliberate hand strikes at 0.90, 1.30 and 1.70 s while the body commits forward once; recover by 2.40 s.', 'events':[(.7,'Aim lock'),(.9,'Burst 1'),(1.3,'Burst 2'),(1.7,'Burst 3'),(2.4,'Recovered')]},
    'core_pulse': {'description':'Plant by 0.50 s and slowly open into a sustained brace through 2.40 s. Heave at the fixed 2.50 s blast; shoulders, hands and head follow, with low recovery through 3.80 s.', 'events':[(.5,'Pull starts'),(2.4,'Full brace'),(2.5,'Blast'),(2.7,'Follow-through'),(3.8,'Recovered')]},
    'slam': {'description':'0.00–1.075 s: crouch and haul both fists overhead. Bear the weight until 1.17 s; strike at 1.30 s. Stay compressed to 1.70 s, then recover through 2.80 s.',
             'events':[(.32,'Plant'),(1.075,'Apex'),(1.17,'Downstroke'),(1.3,'Impact'),(1.7,'Recovery begins')]},
    'slam_fast': {'description':'Same heavy performance within the fixed combo clock: overhead apex at 0.868 s, impact at 1.05 s, low through 1.37 s, recovered at 2.25 s.',
                  'events':[(1.075*1.05/1.3,'Apex'),(1.05,'Impact'),(1.37,'Recovery begins')]},
    'slam_hold_10': {'description':'Haul to the 1.075 s apex, hold the fists still for 0.50 s, strike at 1.80 s, and recover by 3.30 s.',
                     'events':[(1.075,'Hold starts'),(1.575,'Hold ends'),(1.8,'Impact')]},
    'slam_hold_16': {'description':'Haul to the 1.075 s apex, hold the fists still for 0.80 s, strike at 2.10 s, and recover by 3.60 s.',
                     'events':[(1.075,'Hold starts'),(1.875,'Hold ends'),(2.1,'Impact')]},
    'slam_hold_22': {'description':'Haul to the 1.075 s apex, hold the fists still for 1.10 s, strike at 2.40 s, and recover by 3.90 s.',
                     'events':[(1.075,'Hold starts'),(2.175,'Hold ends'),(2.4,'Impact')]},
    'beam': {'description':'Sink into a planted charge over 1.60 s. Recoil once as the beam fires at 1.60 s; bear the pulse through 2.20 s, then lower arms and unclench through 3.60 s. Mouth pivot stays fixed.',
             'events':[(1.2,'Aim lock'),(1.6,'Fire'),(1.72,'Recoil'),(2.2,'Pulse ends'),(3.6,'Recovered')]},
    'awaken': {'description':'Hold dormant weight for 0.60 s, brace at the existing 1.65 s impact cue, then slowly lift the torso. Hands and loose plates follow the rise; settle by 4.20 s. The cinematic intro is unchanged.',
               'events':[(.6,'Wake begins'),(1.65,'Brace / sound cue'),(2.8,'Heavy rise'),(4.2,'Ready')]},
}


def build():
    p=runpy.run_path(str(HERE.parent/'v4-expressive/preview_motion.py'))
    clips=json.loads((ROOT/'src/main/resources/assets/elementalwands/geckolib/animations/fractured_guardian.animation.json').read_text())['animations']
    assert len(clips)==18 and {PREFIX+n for n in REWORKED}<=clips.keys()
    script=(HERE/'viewer.js').read_text()
    replacements={
        '__MESH_DATA__':json.dumps(p['mesh'],separators=(',',':')),
        '__BONES_DATA__':json.dumps(p['bones'],separators=(',',':')),
        '__ANIMATION_DATA__':json.dumps(clips,separators=(',',':')),
        '__REWORKED_DATA__':json.dumps(REWORKED,separators=(',',':')),
        '__ATLAS_DATA__':base64.b64encode((p['SOURCE']/'fractured_guardian.png').read_bytes()).decode(),
    }
    for key,value in replacements.items():
        assert key in script,key
        script=script.replace(key,value)
    return (HERE/'viewer.template.html').read_text().replace('__SCRIPT__',script)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    content=build();target=OUT/'index.html'
    if args.check:
        assert target.read_text()==content,'Guardian combat preview is stale'
    else:
        OUT.mkdir(parents=True,exist_ok=True)
        target.write_text(content)
    print(f'Guardian preview {"verified" if args.check else "built"}: 18 clips, {len(REWORKED)} marked reworked; {target}')
