"""Phase transition, retimed and delayed physical attacks, the fan and the core pulse; original clips remain intact."""
import argparse,json,math,runpy,copy
from pathlib import Path
import numpy as np
HERE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
rig=runpy.run_path(str(HERE.parent/'v4-expressive/build_animations.py'))
heavy=runpy.run_path(str(HERE/'heavy_casts.py'))
base=json.loads((HERE.parent/'v5-leap/fractured_guardian.animation.json').read_text())['animations']
def smooth(t):
 t=max(0,min(1,t));return t*t*(3-2*t)
def pose(t):
 # Brace low, wrench the shoulders back as the core erupts, then recover urgently.
 a=smooth(t/.65)*(1-smooth((t-2.45)/.75))
 burst=smooth((t-.65)/.65)*(1-smooth((t-2.3)/.9))
 shake=math.sin(t*35)*burst
 p={'pelvis':{'position':np.array([0,-5*a,-a])},
    'torso':{'rotation':[16*a-38*burst,shake,shake*.7]},
    'head':{'rotation':[-15*burst,shake*1.5,0]},
    'jaw':{'rotation':[22*burst,0,0]}}
 for side,sign in [('right',-1),('left',1)]:
  p[side+'_shoulder']={'position':np.array([0,0.,0]),'rotation':[0,0,-sign*9*burst]}
  p[side+'_upper_arm']={'rotation':[-12*a+44*burst,0,-sign*18*burst]}
  p[side+'_forearm']={'rotation':[-20*a,0,sign*8*burst]}
  p[side+'_hand']={'rotation':[18*a,0,sign*8*burst]}
  rig['fingers'](p,side,40*a-30*burst,8*burst,shake)
 rig['legs'](p,t,False)
 for side in ['right','left']:
  mats=rig['transforms'](p);low=999
  for c in rig['mesh']:
   if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ['thigh','shin','foot']):
    m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];low=min(low,float(v[:,1].min()))
  if low<.8:p[side+'_shoulder']['position'][1]+=(.8-low)/mats['torso'][1,1]
 return p
bones={};lowest=999
for i in range(129):
 t=round(i*.025,4);p=pose(t)
 for b,channels in p.items():
  for name,value in channels.items():bones.setdefault(b,{}).setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
 mats=rig['transforms'](p)
 for c in rig['mesh']:
  m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];lowest=min(lowest,float(v[:,1].min()))
assert lowest>-.08,lowest
clips={'animation.fractured_guardian.phase_change':{'loop':False,'animation_length':3.2,'bones':bones}}
for name,impact,new_impact,duration in [('slam',1.3,1.05,2.25),('throw',2.2,1.45,2.35)]:
 clip=copy.deepcopy(base['animation.fractured_guardian.'+name]);old_duration=clip['animation_length'];clip['animation_length']=duration
 def remap(t):
  return t*new_impact/impact if t<=impact else new_impact+(t-impact)*(duration-new_impact)/(old_duration-impact)
 for channels in clip['bones'].values():
  for channel,keys in channels.items():channels[channel]={str(round(remap(float(t)),5)):v for t,v in keys.items()}
 clips['animation.fractured_guardian.'+name+'_fast']=clip
# Separate full-body fan cast: brace, draw both hands out, then sweep to release.
def fan_pose(t):
 return heavy['pose']('fan',t,rig)
fan_bones={};fan_lowest=999
for i in range(193):
 t=round(i*.0125,4);p=fan_pose(t);mats=rig['transforms'](p)
 for b,channels in p.items():
  for name,value in channels.items():fan_bones.setdefault(b,{}).setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
 for c in rig['mesh']:
  m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];fan_lowest=min(fan_lowest,float(v[:,1].min()))
assert fan_lowest>-.08,fan_lowest
clips['animation.fractured_guardian.fan']={'loop':False,'animation_length':2.4,'bones':fan_bones}
print('Fan floor clearance:',fan_lowest)
# Delayed slams: the ordinary slam with a hold inserted at the top of the swing, fists overhead, so
# players cannot jump its wave on rhythm. APEX and HOLDS (ticks) match GuardianPhaseRules.
APEX,HOLDS=1.075,(10,16,22)
def sample(keys,t):
 times=sorted(float(k) for k in keys);lookup={float(k):v for k,v in keys.items()}
 if t<=times[0]:return list(lookup[times[0]])
 if t>=times[-1]:return list(lookup[times[-1]])
 for a,b in zip(times,times[1:]):
  if a<=t<=b:return [x+(y-x)*((t-a)/(b-a)) for x,y in zip(lookup[a],lookup[b])]
def held(clip,hold):
 seconds=hold/20;out=copy.deepcopy(clip);out['animation_length']=round(clip['animation_length']+seconds,5)
 for bone,channels in out['bones'].items():
  for channel,keys in channels.items():
   pose=[round(x,5) for x in sample(keys,APEX)];moved={}
   for t,v in keys.items():
    if float(t)<APEX:moved[t]=v
    elif float(t)>APEX:moved[str(round(float(t)+seconds,5))]=v
   moved[str(APEX)]=pose;moved[str(round(APEX+seconds,5))]=pose
   # Bear the stone at the exact apex. A rapid torso shiver made this massive
   # pose look busy and dragged the fists sideways throughout the hold.
   channels[channel]=dict(sorted(moved.items(),key=lambda kv:float(kv[0])))
 return out
for hold in HOLDS:clips[f'animation.fractured_guardian.slam_hold_{hold}']=held(base['animation.fractured_guardian.slam'],hold)
# Core pulse (phase two): it plants and arches back with its arms flung wide while the open core drags
# everyone in, then heaves forward as it detonates. Ticks match GuardianPulseRules: pull 10-50, blast 50, end 76.
PULSE_SECONDS=3.8
def pulse_pose(t):
 return heavy['pose']('core_pulse',t,rig)
pulse_bones={};pulse_lowest=999
for i in range(round(PULSE_SECONDS/.0125)+1):
 t=round(i*.0125,4);p=pulse_pose(t);mats=rig['transforms'](p)
 for b,channels in p.items():
  for name,value in channels.items():pulse_bones.setdefault(b,{}).setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
 for c in rig['mesh']:
  m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];pulse_lowest=min(pulse_lowest,float(v[:,1].min()))
assert pulse_lowest>-.08,pulse_lowest
clips['animation.fractured_guardian.core_pulse']={'loop':False,'animation_length':PULSE_SECONDS,'bones':pulse_bones}
print('Core pulse floor clearance:',pulse_lowest)
outputs={'phase.animation.json':{'format_version':'1.8.0','animations':clips},
 'motion-validation.json':{'samples':129,'minimum_vertex':lowest,'phase_seconds':3.2,'slam_impact_tick':21,'throw_release_tick':29,
 'slam_apex_seconds':APEX,'slam_holds':list(HOLDS),'core_pulse_seconds':PULSE_SECONDS,'core_pulse_minimum_vertex':pulse_lowest}}
for name,data in outputs.items():
 text=json.dumps(data,indent=2)+'\n';path=HERE/name
 if args.check:assert path.read_text()==text,f'Stale Guardian phase output: {path}'
 else:path.write_text(text)
print('Phase clips generated; minimum vertex',lowest)
