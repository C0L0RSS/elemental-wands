"""Render and verify leap poses; actual collision, flight and effects require Minecraft."""
import base64,copy,json,runpy
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
base=HERE.parent/'v4-expressive'
p=runpy.run_path(str(base/'preview_motion.py'))
clips=json.loads((HERE/'fractured_guardian.animation.json').read_text())['animations']
source=p['SOURCE']
code=(source/'render_preview.py').read_text().split('views=[')[0].replace('scale=size/112','scale=size/165').replace('tri-[0,40,0]','tri-[0,60,0]')
r={'__file__':str(source/'render_preview.py')};exec(code,r)
sheet=Image.new('RGB',(1800,1320),(239,237,232));draw=ImageDraw.Draw(sheet)
frames=[('leap_launch',.34),('leap_launch',.7),('leap_air',.7),('leap_air',1.6),('leap_land',.12),('leap_land',1.2)]
for i,(name,t) in enumerate(frames):
 r['mesh']=p['moved'](clips['animation.fractured_guardian.'+name],t)
 x,y=(i%3)*600,(i//3)*660;sheet.paste(r['render'](-32,12,600),(x,y))
 draw.text((x+20,y+610),f'{name.upper()} / {t:.2f}s',fill=(50,60,50),font=r['font'](21))
sheet.save(HERE/'leap-review.png')
report={}
for name in ['leap_launch','leap_air','leap_land']:
 clip=clips['animation.fractured_guardian.'+name];lowest=999
 for t in np.linspace(0,clip['animation_length'],round(clip['animation_length']*80)+1):
  cubes=p['moved'](clip,float(t));u=t/clip['animation_length'];lift=16*4*11*u*(1-u) if name=='leap_air' else 0
  lowest=min(lowest,min(v[1]+lift for c in cubes for v in c['vertices']))
 report[name]={'lowest_vertex_with_flight':round(lowest,4)}
assert all(v['lowest_vertex_with_flight']>-.08 for v in report.values()),report
(HERE/'motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
template=(base/'viewer.template.html').read_text().replace('FULL-BODY PERFORMANCE 04','DOUBLE SHOCKWAVE LEAP 05').replace('Braced legs, opening chest, expressive hands.','Fist-powered launch, tucked flight, heavy landing.').replace('Actual animated rig · Beam effects run in Minecraft.','Animation poses · Flight and both shockwaves run in Minecraft.').replace('<option>beam</option>','<option>beam</option><option selected>leap_launch</option><option>leap_air</option><option>leap_land</option>')
template=template.replace("let clipName='idle'","let clipName='leap_launch'")
template=runpy.run_path(str(HERE/'sequence_preview.py'))['extend'](template)
for a,b in {'__MESH_DATA__':json.dumps(p['mesh'],separators=(',',':')),'__BONES_DATA__':json.dumps(p['bones'],separators=(',',':')),'__ANIMATION_DATA__':json.dumps(clips,separators=(',',':')),'__CUBE_COUNT__':'132','__ATLAS_DATA__':base64.b64encode((source/'fractured_guardian.png').read_bytes()).decode()}.items():template=template.replace(a,b)
(HERE/'preview.html').write_text(template)
print(json.dumps(report,indent=2))
