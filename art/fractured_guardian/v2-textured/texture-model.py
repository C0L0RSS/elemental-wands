"""Apply material UVs to the approved model without changing any geometry.

The image-generated PNG is used unchanged. All processing here is model data:
UV assignments, project embedding, material metadata, and preview mesh data.
"""
import base64
import copy
import hashlib
import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT.parent / 'v1'
atlas_path = ROOT / 'fractured_guardian.png'
atlas = Image.open(atlas_path)
W, H = atlas.size
assert W == H
T = W / 4
source = json.loads((SOURCE/'fractured_guardian.bbmodel').read_text())
project = copy.deepcopy(source)
geo = json.loads((SOURCE/'fractured_guardian.geo.json').read_text())
mesh = json.loads((SOURCE/'mesh.json').read_text())
material_names = ['stone', 'strata', 'shadow_stone', 'moss_sparse', 'moss_ledge',
                  'chipped_stone', 'deep_crevice', 'stone_variant', 'spiral_carving',
                  'knot_carving', 'broken_carving', 'magic_fracture', 'face_stone',
                  'eye_light', 'core_light', 'moss_crevice']
face_names = ['north', 'south', 'west', 'east', 'down', 'up']
assignments = []


def stable(name):
    return int(hashlib.sha256(name.encode()).hexdigest()[:8], 16)


def material(e, face):
    name=e['name']
    h=stable(name+face)
    # The small eye and core cubes already exist in the approved geometry.
    if 'eye_inset' in name:
        return 13 if face == 'north' else 6
    if name.startswith('core_'):
        return 14
    # Magic follows the two central chest plates, not every stone.
    if name.startswith('chest_plate') and name.endswith('_main') and face=='north':
        return 11
    if face=='north' and 'shoulder_front_plate_inner' in name:
        return 8
    if face=='north' and 'forearm_front_slab_lower' in name:
        return 9
    if face=='south' and ('back_scapula' in name or name=='torso_spine_3'):
        return 10
    if any(x in name for x in ['inner_mass', 'foundation', 'upper_arm_core']):
        return 2 if face!='down' else 6
    if 'head_' in name or 'jaw_' in name:
        return 12 if face != 'down' else 2
    if face=='down':
        return 6 if h%3==0 else 2
    if face=='up':
        if any(x in name for x in ['shoulder', 'hump', 'spine', 'back_scapula']):
            return 4 if h%3 else 3
        return 3 if h%4==0 else 5
    if any(x in name for x in ['shoulder','back_scapula','calf','foot_base','palm','wrist']):
        if h%4==0:return 3
    if any(x in name for x in ['elbow','wrist','knuckle']) and h%5==0:return 15
    return [0, 1, 7, 0, 1, 7, 5][h%7]


def uv_rect(e, face, tile):
    col,row=tile%4,tile//4
    x,y=col*T,row*T
    # Art boundaries use a one-texel inset, so adjacent material tiles cannot bleed.
    pad=T*.052
    dx,dy,dz=[b-a for a,b in zip(e['from'],e['to'])]
    width,height={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),
                  'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}[face]
    if tile==13:
        # Tight eye crop keeps the slit readable on a small recessed face.
        return [x+T*.29,y+T*.37,x+T*.73,y+T*.58]
    if tile==14:
        return [x+T*.22,y+T*.23,x+T*.78,y+T*.78]
    if tile in [8,9,10]:
        return [x+pad,y+pad,x+T-pad,y+T-pad]
    if tile==11:
        # The branch touches the inner edge on both chest plates.
        rect=[x+pad,y+T*.20,x+T-pad,y+T*.83]
        if '_-1_' in e['name']:rect[0],rect[2]=rect[2],rect[0]
        return rect
    scale=T/24  # generated artwork uses roughly 24 large pixels per tile
    scale=min(scale,(T-2*pad)/max(width,height))
    uw,uh=width*scale,height*scale
    key=stable(e['name']+face)
    ox=(T-2*pad-uw)*((key%997)/996)
    oy=(T-2*pad-uh)*(((key//997)%997)/996)
    # Moss sits near top/edge pockets; selected moss faces use the patch-bearing edge.
    if tile in [3,4,15]:ox=0;oy=0
    return [x+pad+ox,y+pad+oy,x+pad+ox+uw,y+pad+oy+uh]


by_uuid={e['uuid']:e for e in project['elements']}
by_name={e['name']:e for e in project['elements']}
for e in project['elements']:
    for face in face_names:
        tile=material(e,face)
        rect=[round(v,5) for v in uv_rect(e,face,tile)]
        e['faces'][face]={'uv':rect,'texture':0}
        assignments.append(dict(cube=e['name'],face=face,tile=tile,
                                material=material_names[tile],uv=rect,
                                magic=tile in [11,13,14]))
project['name']='Fractured Guardian — Textured study 02'
project['resolution']={'width':W,'height':H}
project['textures']=[dict(name='fractured_guardian.png', uuid=source['textures'][0]['uuid'],
    id='0',source='data:image/png;base64,'+base64.b64encode(atlas_path.read_bytes()).decode(),
    mode='bitmap',saved=True,internal=True,width=W,height=H,uv_width=W,uv_height=H)]
# Future bone registration and animations are preserved verbatim.
project.pop('editor_state',None)
(ROOT/'fractured_guardian.bbmodel').write_text(json.dumps(project,indent=2)+'\n')

group_by_uuid={g['uuid']:g for g in project['groups']}
bone_by_name={b['name']:b for b in geo['minecraft:geometry'][0]['bones']}
def export(node):
    group=group_by_uuid[node['uuid']]
    bone=bone_by_name[group['name']]
    cubes=[by_uuid[c] for c in node['children'] if isinstance(c,str)]
    assert len(cubes)==len(bone['cubes'])
    for src,dst in zip(cubes,bone['cubes']):
        for f in face_names:
            u0,v0,u1,v1=src['faces'][f]['uv']
            dst['uv'][f]={'uv':[u0,v0],'uv_size':[round(u1-u0,5),round(v1-v0,5)]}
    for child in node['children']:
        if isinstance(child,dict):export(child)
for node in project['outliner']:export(node)
desc=geo['minecraft:geometry'][0]['description'];desc['texture_width']=W;desc['texture_height']=H
(ROOT/'fractured_guardian.geo.json').write_text(json.dumps(geo,indent=2)+'\n')

# Per-vertex coordinates follow Blockbench/Bedrock face orientation.
# In the mesh's outward-wound face order, the two vertical dimensions begin at
# the bottom; top/bottom UV orientations match the native entity cube mapping.
for cube in mesh:
    e=by_name[cube['name']]
    cube['uvs']=[];cube['magic']=[]
    for f in face_names:
        u0,v0,u1,v1=e['faces'][f]['uv']
        corners={
            'north':[[u1,v1],[u1,v0],[u0,v0],[u0,v1]],
            'south':[[u0,v1],[u1,v1],[u1,v0],[u0,v0]],
            'west':[[u0,v1],[u1,v1],[u1,v0],[u0,v0]],
            'east':[[u1,v1],[u1,v0],[u0,v0],[u0,v1]],
            'down':[[u1,v0],[u0,v0],[u0,v1],[u1,v1]],
            'up':[[u1,v0],[u1,v1],[u0,v1],[u0,v0]],
        }[f]
        cube['uvs'].append([[u/W,v/H] for u,v in corners])
        cube['magic'].append(material(e,f) in [11,13,14])
(ROOT/'mesh.json').write_text(json.dumps(mesh,separators=(',',':'))+'\n')
(ROOT/'materials.json').write_text(json.dumps(dict(
    atlas='fractured_guardian.png',width=W,height=H,tiles=material_names,
    notes='Original imagegen PNG retained unchanged. Magic is a material-preview treatment; no runtime emissive layer yet.',
    faces=assignments),indent=2)+'\n')

# Ensure texture work never silently changes the approved silhouette or rig.
assert project['groups']==source['groups'] and project['outliner']==source['outliner']
for old,new in zip(source['elements'],project['elements']):
    assert {k:v for k,v in old.items() if k!='faces'}=={k:v for k,v in new.items() if k!='faces'}
    for f in new['faces'].values():
        assert all(math.isfinite(v) for v in f['uv'])
        assert all(0<=v<=W for v in f['uv'])
        assert abs(f['uv'][2]-f['uv'][0])>0 and abs(f['uv'][3]-f['uv'][1])>0
print(f'Textured {len(mesh)} unchanged cubes / {len(assignments)} faces; original atlas {W}x{H}.')
