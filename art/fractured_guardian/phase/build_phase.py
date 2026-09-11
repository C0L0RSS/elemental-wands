"""Separate phase transition and retimed physical attacks; original clips remain intact."""
import json,math,runpy,copy
from pathlib import Path
import numpy as np
HERE=Path(__file__).resolve().parent
rig=runpy.run_path(str(HERE.parent/'v4-expressive/build_animations.py'))
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
 a=smooth(t/.8)*(1-smooth((t-1.9)/1.0))
 sweep=smooth((t-1.25)/.35)*(1-smooth((t-1.9)/1.0))
 p={'pelvis':{'position':np.array([0,-3*a,0.])},
    'torso':{'rotation':[5*a,-12*a+24*sweep,0]},
    'head':{'rotation':[-5*a,8*a-16*sweep,0]}}
 for side,sign in [('right',-1),('left',1)]:
  p[side+'_shoulder']={'position':np.array([0,0.,0]),'rotation':[0,0,-sign*10*a]}
  p[side+'_upper_arm']={'rotation':[-65*a+25*sweep,sign*18*a,-sign*24*a]}
  p[side+'_forearm']={'rotation':[-35*a+25*sweep,0,sign*10*a]}
  p[side+'_hand']={'rotation':[-15*a,sign*15*sweep,0]}
  rig['fingers'](p,side,8*a,7*a,0)
 rig['legs'](p,t,False)
 for side in ['right','left']:
  mats=rig['transforms'](p);low=999
  for c in rig['mesh']:
   if c['bone'].startswith(side+'_') and not any(k in c['bone'] for k in ['thigh','shin','foot']):
    m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];low=min(low,float(v[:,1].min()))
  if low<.8:p[side+'_shoulder']['position'][1]+=(.8-low)/mats['torso'][1,1]
 return p
fan_bones={};fan_lowest=999
for i in range(117):
 t=round(i*.025,4);p=fan_pose(t);mats=rig['transforms'](p)
 for b,channels in p.items():
  for name,value in channels.items():fan_bones.setdefault(b,{}).setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
 for c in rig['mesh']:
  m=mats[c['bone']];v=np.array(c['vertices'])@m[:3,:3].T+m[:3,3];fan_lowest=min(fan_lowest,float(v[:,1].min()))
assert fan_lowest>-.08,fan_lowest
clips['animation.fractured_guardian.fan']={'loop':False,'animation_length':2.9,'bones':fan_bones}
print('Fan floor clearance:',fan_lowest)
(HERE/'phase.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':clips},indent=2)+'\n')
(HERE/'motion-validation.json').write_text(json.dumps({'samples':129,'minimum_vertex':lowest,'phase_seconds':3.2,'slam_impact_tick':21,'throw_release_tick':29},indent=2)+'\n')
print('Phase clips generated; minimum vertex',lowest)
