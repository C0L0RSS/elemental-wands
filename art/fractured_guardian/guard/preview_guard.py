"""Review the actual guard textures and sampled rig; no Minecraft needed."""
import base64,copy,json,runpy,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
p=runpy.run_path(str(HERE.parent/'v4-expressive/preview_motion.py'))
source=p['SOURCE']; clips=json.loads((HERE/'preview.animation.json').read_text())['animations']
clip=clips['animation.fractured_guardian.guard_break']
clips.update(json.loads((HERE.parent/'phase/phase.animation.json').read_text())['animations'])
# Extend the existing reference sampler to include the core's scale channel.
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

def moved(t):
    mats=matrices(clip,t);out=copy.deepcopy(p['mesh'])
    for c in out:
        v=np.array(c['vertices']);m=mats[c['bone']];c['vertices']=(v@m[:3,:3].T+m[:3,3]).tolist()
    return out
code=(source/'render_preview.py').read_text().split('views=[')[0].replace('scale=size/112','scale=size/125').replace('tri-[0,40,0]','tri-[0,43,0]')
r={'__file__':str(source/'render_preview.py')};exec(code,r)
assets=HERE.parents[2]/'src/main/resources/assets/elementalwands/textures/entity'
# HERE is art/fractured_guardian/guard; repository is parents[2].
sheet=Image.new('RGB',(1800,1260),(239,237,232));draw=ImageDraw.Draw(sheet)
frames=[('Intact',0,0),('Hairline fractures',0,1),('Guard nearly broken',0,3),('Ribs opening',.65,3),('Core exposed',2.0,3),('Plates returning',9.1,3)]
for i,(label,t,stage) in enumerate(frames):
    texture=assets/('fractured_guardian.png' if stage==0 else f'fractured_guardian_cracks_{stage}.png')
    r['atlas']=np.asarray(Image.open(texture).convert('RGB'));r['atlas_h'],r['atlas_w']=r['atlas'].shape[:2]
    r['mesh']=moved(t)
    x,y=i%3*600,i//3*630;sheet.paste(r['render'](-20,8,600),(x,y));draw.text((x+25,y+598),label,fill=(40,55,53),font=r['font'](21))
sheet.save(HERE/'guard-review.png')
# Fully offline viewer, reusing the approved rig's browser renderer.
template=(HERE.parent/'v4-expressive/viewer.template.html').read_text()
start=template.index('<select id="clip">');end=template.index('</select>',start)
template=template[:start]+'<select id="clip"><option value="fan">Aimed stone barrage</option><option value="phase_change">Phase two eruption</option><option value="slam_fast">Fast slam</option><option value="throw_fast">Fast rock throw</option><option value="guard_break">Guard break</option>'+template[end:]
template=template.replace("let clipName='idle'","let clipName='fan'")
template=template.replace('max="6"','max="2.4"').replace('zoom=1','zoom=1.45').replace('pos-vec3(0.,60.,0.)','pos-vec3(0.,42.,0.)')
template=template.replace('href="fractured_guardian.bbmodel" download>Download Blockbench source','href="guard.animation.json" download>Guard animation source')
template=template.replace('href="../v1/approved-concept.png">Approved concept','href="guard-review.png">View crack stages')
template=template.replace('FULL-BODY PERFORMANCE 04','FRACTURED GUARD').replace('Braced legs, opening chest, expressive hands.','Unstable magic, urgent attacks, and a suspended core.').replace('Actual animated rig · Beam effects run in Minecraft.','Actual phase and guard animations · Magic discharges and combat run in Minecraft.')
# Viewer matrices already support rotation/translation; scale core vertices explicitly before its transform.
template=template.replace('function buildPoses(){posedBones={};','function buildPoses(){posedBones={};')
# Embed sampled scale into the vertex transform using bone-local pivot prior to the posed hierarchy.
needle='return mul(m.r,v).map((x,i)=>x+m.p[i])'
replacement="if(name==='core'){const ch=selectedClip().bones.core||{};const s=ch.scale?sample(ch.scale,motionTime):[1,1,1],pivot=bones.core.pivot;v=v.map((x,i)=>pivot[i]+(x-pivot[i])*s[i]);}return mul(m.r,v).map((x,i)=>x+m.p[i])"
# Actual variable names verified below; keep preview honest if template changes.
if needle in template: template=template.replace(needle,replacement)
for a,b in {'__MESH_DATA__':json.dumps(p['mesh'],separators=(',',':')),'__BONES_DATA__':json.dumps(p['bones'],separators=(',',':')),'__ANIMATION_DATA__':json.dumps(clips,separators=(',',':')),'__CUBE_COUNT__':'132','__ATLAS_DATA__':base64.b64encode((assets/'fractured_guardian_cracks_3.png').read_bytes()).decode()}.items():template=template.replace(a,b)
(HERE/'preview.html').write_text(template)
print('Guard pose sheet and offline animation preview generated.')

# Sample between authored keys as well as on them: hands and soles must not sink.
lowest=100.0; soles=[]
for t in np.linspace(0,9.7,389):
    for cube in moved(float(t)):
        height=min(v[1] for v in cube['vertices'])
        lowest=min(lowest,height)
        if cube['bone'].endswith('_foot'):soles.append(height)
report={'samples':389,'lowest_vertex':lowest,'minimum_sole':min(soles),'maximum_sole':max(soles)}
assert lowest>-.08 and max(soles)<.08,report
(HERE/'motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('Guard motion clearance:',report)
