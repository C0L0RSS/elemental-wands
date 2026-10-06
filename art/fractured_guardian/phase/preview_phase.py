"""Review the shell's crack stages and the phase clips on the real rig and textures; no Minecraft needed.

Writes a pose sheet and an offline viewer to .local-previews/guardian-phase/. In game the ribs and
core are posed in code (FracturedGuardianModel); the preview bakes the same pose into its copy.
"""
import base64,copy,json,runpy,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'.local-previews/guardian-phase'
p=runpy.run_path(str(HERE.parent/'v4-expressive/preview_motion.py'))
source=p['SOURCE']
phase=json.loads((HERE/'phase.animation.json').read_text())['animations']
NAME='animation.fractured_guardian.'

def smooth(u):
    u=max(0,min(1,u));return u*u*(3-2*u)
def ribs(seconds):
    """GuardianShellRules.openness over the shell break's clock (burst at tick 32, open by 56)."""
    return smooth((seconds*20-32)/24)
def charge(seconds):
    """GuardianPulseRules.charge: plant to tick 10, pull to the blast at 50, fade over 12 ticks."""
    tick=seconds*20
    if tick<10:return .25*tick/10
    if tick<50:return .25+.75*(tick-10)/40
    return max(0,1-(tick-50)/12)
def shell(clip,openness,pulse):
    out=copy.deepcopy(clip);bones=out['bones']
    for name in ['chest_plate_-1','chest_plate_1','core']:bones[name]={}
    for i in range(int(out['animation_length']/.05)+1):
        t=round(i*.05,3);c=charge(t) if pulse else 0;a=openness(t)*(1+.3*c);key=str(t)
        jitter=a*(1 if not pulse and openness(t)<1 else .35+.65*c)
        size=(1.04)*(1+.35*c)
        bones['chest_plate_-1'].setdefault('rotation',{})[key]=[0,68*a,0]
        bones['chest_plate_-1'].setdefault('position',{})[key]=[-3*a,0,-2*a]
        bones['chest_plate_1'].setdefault('rotation',{})[key]=[0,-68*a,0]
        bones['chest_plate_1'].setdefault('position',{})[key]=[3*a,0,-2*a]
        bones['core'].setdefault('rotation',{})[key]=[0,math.sin(t*17)*8*jitter,math.sin(t*23)*7*jitter]
        bones['core'].setdefault('position',{})[key]=[math.sin(t*31)*.5*jitter,math.sin(t*21)*.4*jitter,-12*a]
        bones['core'].setdefault('scale',{})[key]=[size*(1+.65*a),size*(1+.3*a),size*(1+1.4*a)]
    return out
clips=dict(phase)
clips[NAME+'phase_change']=shell(phase[NAME+'phase_change'],ribs,False)
clips[NAME+'core_pulse']=shell(phase[NAME+'core_pulse'],lambda t:1,True)

# Extend the reference sampler to include the core's scale channel.
def matrices(clip,t):
    result={}
    for name,b in p['bones'].items():
        ch=clip['bones'].get(name,{})
        x,y,z=np.radians(p['sample'](ch.get('rotation'),t)*[-1,1,-1])
        cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        r=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
        scale=p['sample'](ch.get('scale'),t) if 'scale' in ch else np.ones(3)
        r=r@np.diag(scale);pivot=np.array(b['pivot']);m=np.eye(4);m[:3,:3]=r;m[:3,3]=pivot-r@pivot+p['sample'](ch.get('position'),t)
        result[name]=result[b['parent']]@m if b.get('parent') else m
    return result
def moved(clip,t):
    mats=matrices(clip,t);out=copy.deepcopy(p['mesh'])
    for c in out:
        v=np.array(c['vertices']);m=mats[c['bone']];c['vertices']=(v@m[:3,:3].T+m[:3,3]).tolist()
    return out
code=(source/'render_preview.py').read_text().split('views=[')[0].replace('scale=size/112','scale=size/125').replace('tri-[0,40,0]','tri-[0,43,0]')
r={'__file__':str(source/'render_preview.py')};exec(code,r)
assets=ROOT/'src/main/resources/assets/elementalwands/textures/entity'
OUT.mkdir(parents=True,exist_ok=True)
sheet=Image.new('RGB',(2400,1260),(239,237,232));draw=ImageDraw.Draw(sheet)
frames=[('Fresh shell',NAME+'slam_hold_22',0,0),('Worn: fists held overhead',NAME+'slam_hold_22',1.6,2),
        ('Delayed slam lands',NAME+'slam_hold_22',2.45,2),('Shell break: ribs swing open',NAME+'phase_change',2.6,3),
        ('Core pulse: braced',NAME+'core_pulse',.6,3),('Core pulse: drawing in',NAME+'core_pulse',2.2,3),
        ('Core pulse: blast',NAME+'core_pulse',2.55,3),('Core pulse: recovering',NAME+'core_pulse',3.2,3)]
for i,(label,clip,t,stage) in enumerate(frames):
    texture=assets/('fractured_guardian.png' if stage==0 else f'fractured_guardian_cracks_{stage}.png')
    r['atlas']=np.asarray(Image.open(texture).convert('RGB'));r['atlas_h'],r['atlas_w']=r['atlas'].shape[:2]
    r['mesh']=moved(clips[clip],t)
    x,y=i%4*600,i//4*630;sheet.paste(r['render'](-20,8,600),(x,y));draw.text((x+25,y+598),label,fill=(40,55,53),font=r['font'](21))
sheet.save(OUT/'phase-review.png')
# Fully offline viewer, reusing the approved rig's browser renderer.
template=(HERE.parent/'v4-expressive/viewer.template.html').read_text()
start=template.index('<select id="clip">');end=template.index('</select>',start)
options=[('core_pulse','Core pulse'),('slam_hold_22','Delayed slam (longest hold)'),('slam_hold_10','Delayed slam (shortest hold)'),
         ('phase_change','Shell break'),('fan','Aimed stone barrage'),('slam_fast','Fast slam'),('throw_fast','Fast rock throw')]
template=template[:start]+'<select id="clip">'+''.join(f'<option value="{v}">{l}</option>' for v,l in options)+template[end:]
template=template.replace("let clipName='idle'","let clipName='core_pulse'")
template=template.replace('max="6"','max="3.9"').replace('zoom=1','zoom=1.45').replace('pos-vec3(0.,60.,0.)','pos-vec3(0.,42.,0.)')
template=template.replace('href="fractured_guardian.bbmodel" download>Download Blockbench source','href="phase-review.png">Pose sheet')
template=template.replace('href="../v1/approved-concept.png">Approved concept','href="phase-review.png">View crack stages')
template=template.replace('FULL-BODY PERFORMANCE 04','FRACTURED GUARDIAN · PHASE TWO').replace('Braced legs, opening chest, expressive hands.','The shell breaks open; the core pulls and detonates.').replace('Actual animated rig · Beam effects run in Minecraft.','Actual phase clips · Particles, pull and damage run in Minecraft.')
# Viewer matrices already support rotation/translation; scale core vertices explicitly before its transform.
needle='return mul(m.r,v).map((x,i)=>x+m.p[i])'
replacement="if(name==='core'){const ch=selectedClip().bones.core||{};const s=ch.scale?sample(ch.scale,motionTime):[1,1,1],pivot=bones.core.pivot;v=v.map((x,i)=>pivot[i]+(x-pivot[i])*s[i]);}return mul(m.r,v).map((x,i)=>x+m.p[i])"
if needle in template: template=template.replace(needle,replacement)
for a,b in {'__MESH_DATA__':json.dumps(p['mesh'],separators=(',',':')),'__BONES_DATA__':json.dumps(p['bones'],separators=(',',':')),'__ANIMATION_DATA__':json.dumps(clips,separators=(',',':')),'__CUBE_COUNT__':'132','__ATLAS_DATA__':base64.b64encode((assets/'fractured_guardian_cracks_3.png').read_bytes()).decode()}.items():template=template.replace(a,b)
(OUT/'index.html').write_text(template)
print('Phase pose sheet and offline viewer written to',OUT.relative_to(ROOT))
