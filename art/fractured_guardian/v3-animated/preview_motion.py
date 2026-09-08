"""Preview the runtime animation transforms on the approved textured mesh."""
import base64
import copy
import json
import math
from pathlib import Path
import numpy as np

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'v2-textured'
mesh=json.loads((SOURCE/'mesh.json').read_text())
geo=json.loads((SOURCE/'fractured_guardian.geo.json').read_text())['minecraft:geometry'][0]
bones={b['name']:b for b in geo['bones']}
project=json.loads((SOURCE/'fractured_guardian.bbmodel').read_text())
groups={g['uuid']:g['name'] for g in project['groups']}
owner={}
def visit(node,parent=None):
    if isinstance(node,str): owner[node]=parent;return
    name=groups[node['uuid']]
    for c in node['children']: visit(c,name)
for n in project['outliner']:visit(n)
by_name={e['name']:owner[e['uuid']] for e in project['elements']}
for cube in mesh:cube['bone']=by_name[cube['name']]
clips=json.loads((HERE/'fractured_guardian.animation.json').read_text())['animations']

def sample(keys,t):
    if not keys:return np.zeros(3)
    entries=sorted((float(k),np.array(v,dtype=float)) for k,v in keys.items())
    if t<=entries[0][0]:return entries[0][1]
    for (a,x),(b,y) in zip(entries,entries[1:]):
        if t<=b:return x+(y-x)*(t-a)/(b-a)
    return entries[-1][1]

def matrices(clip,t):
    result={}
    for name,b in bones.items():
        channels=clip['bones'].get(name,{})
        # GeckoLib negates JSON X/Y rotation then mirrors model X coordinates.
        x,y,z=np.radians(sample(channels.get('rotation'),t)*[-1,1,-1])
        cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
        rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
        ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])
        rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
        rot=rz@ry@rx;p=np.array(b['pivot'])
        mat=np.eye(4);mat[:3,:3]=rot;mat[:3,3]=p-rot@p+sample(channels.get('position'),t)
        result[name]=result[b['parent']]@mat if b.get('parent') else mat
    return result

def moved(clip,t):
    mats=matrices(clip,t);out=copy.deepcopy(mesh)
    for cube in out:
        v=np.array(cube['vertices']);m=mats[cube['bone']]
        cube['vertices']=(v@m[:3,:3].T+m[:3,3]).tolist()
    return out

if __name__=='__main__':
    # Reuse the existing deterministic rasterizer, without its file-writing entrypoint.
    code=(SOURCE/'render_preview.py').read_text().split("views=[")[0].replace('scale=size/112','scale=size/160').replace('tri-[0,40,0]','tri-[0,60,0]')
    renderer={'__file__':str(SOURCE/'render_preview.py')};exec(code,renderer)
    from PIL import Image,ImageDraw
    frames=[('idle',1.5),('walk',.3),('awaken',2.25),('slam',1.05),('slam',1.3),('throw',1.9)]
    sheet=Image.new('RGB',(1800,1320),(239,237,232));draw=ImageDraw.Draw(sheet)
    for i,(name,t) in enumerate(frames):
        renderer['mesh']=moved(clips['animation.fractured_guardian.'+name],t)
        img=renderer['render'](-32,12,600)
        x,y=(i%3)*600,(i//3)*660
        sheet.paste(img,(x,y));draw.text((x+30,y+610),f'{name.upper()} / {t:.2f}s',fill=(50,60,50),font=renderer['font'](23))
    sheet.save(HERE/'animation-review.png')
    report={}
    for name,clip in clips.items():
        lowest=1000;footlow=1000;foothigh=-1000
        for t in np.linspace(0,clip['animation_length'],int(clip['animation_length']*60)+1):
            cubes=moved(clip,float(t));low=min(v[1] for c in cubes for v in c['vertices']);lowest=min(lowest,low)
            feet=[min(v[1] for v in c['vertices']) for c in cubes if 'foot_base' in c['name']]
            footlow=min(footlow,min(feet));foothigh=max(foothigh,min(feet))
        report[name]={'lowest_vertex':round(lowest,4),'lowest_support_foot':round(footlow,4),'highest_support_foot':round(foothigh,4)}
        # Tolerance is 0.05 model units (0.0032 blocks), covering interpolation rounding.
    assert all(v['lowest_vertex'] > -.05 and abs(v['highest_support_foot']) < .05 for v in report.values()), report
    (HERE/'motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    template=(HERE/'viewer.template.html').read_text()
    replacements={'__MESH_DATA__':json.dumps(mesh,separators=(',',':')),'__BONES_DATA__':json.dumps(bones,separators=(',',':')),'__ANIMATION_DATA__':json.dumps(clips,separators=(',',':')),'__CUBE_COUNT__':'132','__ATLAS_DATA__':base64.b64encode((SOURCE/'fractured_guardian.png').read_bytes()).decode()}
    for a,b in replacements.items():template=template.replace(a,b)
    (HERE/'preview.html').write_text(template)
