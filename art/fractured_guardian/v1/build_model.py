"""Rebuild the approved Guardian's geometry study; no runtime assets are changed.

Units are Minecraft model pixels (16 per block); front is negative Z.
The native project uses the installed GeckoLib plugin's model format.
UVs are deliberately temporary at this stage.
"""
import base64
import io
import json
import math
from pathlib import Path
import uuid

from PIL import Image

ROOT = Path(__file__).resolve().parent
elements, groups, bones = [], [], []
nodes = {}


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'elementalwands/guardian/v1/' + name))


def group(name, pivot, parent=None):
    node = {'uuid': uid(name), 'children': []}
    nodes[name] = node
    groups.append(dict(name=name, origin=pivot, rotation=[0, 0, 0], uuid=uid(name),
                       export=True, isOpen=False, visibility=True, color=0))
    bone = dict(name=name, pivot=pivot, cubes=[])
    if parent:
        nodes[parent]['children'].append(node)
        bone['parent'] = parent
    bones.append(bone)
    return name


def cube(bone, name, center, size, rotation=(0, 0, 0)):
    name = bone + '_' + name
    low = [round(c-s/2, 4) for c, s in zip(center, size)]
    high = [round(c+s/2, 4) for c, s in zip(center, size)]
    faces = {f: {'uv': [0, 0, 1, 1], 'texture': 0}
             for f in ['north', 'east', 'south', 'west', 'up', 'down']}
    e = dict(name=name, uuid=uid(name), type='cube', origin=list(center),
             rotation=list(rotation), **{'from': low, 'to': high},
             box_uv=False, rescale=False, autouv=0, color=0, faces=faces,
             visibility=True, export=True)
    elements.append(e)
    nodes[bone]['children'].append(e['uuid'])
    uv = {f: {'uv': [0, 0], 'uv_size': [1, 1]} for f in faces}
    next(b for b in bones if b['name'] == bone)['cubes'].append(
        dict(origin=low, size=list(size), pivot=list(center),
             rotation=list(rotation), uv=uv))


group('root', [0, 0, 0])
group('pelvis', [0, 31, 4], 'root')
cube('pelvis', 'foundation', [0, 32, 4], [24, 13, 17], [5, 0, 0])
for s in [-1, 1]:
    cube('pelvis', f'hip_{s}', [s*11, 32, 3], [9, 12, 17], [6, s*5, -s*9])
cube('pelvis', 'front_keystone', [0, 32, -6], [11, 10, 5], [8, 0, 0])

group('torso', [0, 37, 4], 'pelvis')
cube('torso', 'inner_mass', [0, 52, 3], [25, 28, 17], [10, 0, 0])
cube('torso', 'shoulder_bridge', [0, 68, 6], [38, 16, 20], [12, 0, 0])
cube('torso', 'hump_center', [0, 74, 8], [19, 12, 21], [15, 0, -3])
for s in [-1, 1]:
    # Tapering rib plates form the abdomen, leaving narrow irregular seams.
    for i in range(3):
        cube('torso', f'rib_{s}_{i}', [s*(7.4+i*1.4), 43+i*6.7, -7.5-i*.7],
             [12+i*1.7, 6.1, 7], [9, -s*6, s*(11-i*2)])
    chest = group(f'chest_plate_{s}', [s*14, 61, -6], 'torso')
    cube(chest, 'main', [s*9, 62, -9], [16, 11, 7], [7, -s*6, s*8])
    cube(chest, 'upper_lip', [s*10.5, 68, -8], [16, 4, 8], [3, -s*4, s*7])
    cube('torso', f'back_scapula_{s}', [s*13, 66, 16], [14, 19, 8], [14, s*10, -s*8])
    cube('torso', f'back_low_{s}', [s*10, 47, 12], [12, 12, 6], [12, s*7, s*7])
for i in range(5):
    cube('torso', f'spine_{i}', [0, 40+i*7.2, 14+i*.8], [8, 6.2, 5], [10, 0, (-1)**i*3])
group('core', [0, 60, -4], 'torso')
cube('core', 'unlit_inset', [0, 59, -6], [6, 8, 4], [0, 0, 20])

group('head', [0, 68, -7], 'torso')
cube('head', 'cranium', [0, 70, -11], [13, 12, 12], [5, 0, 0])
cube('head', 'crown', [0, 76, -10], [14, 3, 12], [7, 0, -4])
for s in [-1, 1]:
    cube('head', f'temple_{s}', [s*6, 69, -12], [4, 10, 9], [4, s*7, s*4])
    cube('head', f'brow_{s}', [s*3.7, 72, -17.5], [6.8, 3.3, 4], [0, 0, s*8])
    cube('head', f'cheek_{s}', [s*4.8, 65.8, -17], [3.9, 4.1, 4], [0, -s*6, s*8])
    # Recessed geometry only: texture and emissive eye treatment come later.
    cube('head', f'eye_inset_{s}', [s*3.3, 69.5, -17.2], [2.7, 1.6, .6])
cube('head', 'nose', [0, 68, -18.2], [3, 5.6, 3], [6, 0, 0])
group('jaw', [0, 64, -10], 'head')
cube('jaw', 'mandible', [0, 62.8, -14], [11, 3.8, 9], [-5, 0, 0])
cube('jaw', 'chin', [0, 62, -18], [7, 3, 3])

for s, side in [(-1, 'right'), (1, 'left')]:
    shoulder = group(side+'_shoulder', [s*21, 66, 3], 'torso')
    asym = 1 if s == 1 else 0
    cube(shoulder, 'mass', [s*24, 66, 3], [17+asym, 18, 21], [8, -s*8, -s*13])
    cube(shoulder, 'cap', [s*24, 75+asym, 4], [17+asym, 5, 21], [10, -s*7, -s*14])
    cube(shoulder, 'outer_plate', [s*32, 65, 2], [6, 15, 17], [5, -s*8, -s*14])
    cube(shoulder, 'front_plate_inner', [s*20.5, 66, -8.5], [7.5, 14, 5], [9, -s*11, -s*9])
    cube(shoulder, 'front_plate_outer', [s*28, 64.5, -8], [7, 12, 5], [5, -s*4, -s*16])
    cube(shoulder, 'cap_inner_step', [s*19.5, 78+asym, 4], [8, 4, 17], [10, -s*9, -s*8])
    cube(shoulder, 'cap_rear_step', [s*25, 75, 12], [11, 5, 8], [17, -s*8, -s*13])
    cube(shoulder, 'outer_lower_chip', [s*32, 58, -1], [7, 6, 11], [10, -s*9, -s*22])
    cube(shoulder, 'back_plate', [s*25, 66, 14], [12, 13, 5], [12, s*8, -s*10])
    upper = group(side+'_upper_arm', [s*25, 61, 1], shoulder)
    cube(upper, 'core', [s*28, 50, -1], [12, 19, 14], [12, 0, s*11])
    cube(upper, 'bicep', [s*28, 51, -8], [11, 13, 6], [10, -s*5, s*10])
    cube(upper, 'tricep', [s*30, 51, 6], [10, 13, 6], [13, 0, s*10])
    fore = group(side+'_forearm', [s*31, 41, -3], upper)
    cube(fore, 'elbow', [s*31, 41, -3], [13, 10, 14], [8, s*5, 0])
    cube(fore, 'mass', [s*33, 29, -8], [15, 21, 17], [15, -s*3, s*4])
    cube(fore, 'front_slab_upper', [s*32, 34, -15.8], [12, 9.5, 5], [16, -s*5, s*7])
    cube(fore, 'front_slab_lower', [s*33.5, 23.8, -18.3], [13, 9.5, 5], [12, s*2, -s*3])
    cube(fore, 'front_split_edge', [s*38.5, 32.5, -15.5], [5, 10, 5], [20, -s*12, -s*9])
    cube(fore, 'outer_slab_upper', [s*40, 34, -6], [4.5, 9.5, 13], [19, s*6, s*8])
    cube(fore, 'outer_slab_lower', [s*40, 24, -9], [5, 10, 14], [10, s*2, -s*5])
    cube(fore, 'rear_slab', [s*34, 31, 1], [12, 14, 4], [15, s*5, s*4])
    hand = group(side+'_hand', [s*34, 18, -11], fore)
    cube(hand, 'wrist', [s*34, 18, -11], [11, 7, 13], [12, 0, 0])
    cube(hand, 'palm', [s*34, 12, -14], [17, 12, 13], [8, 0, 0])
    cube(hand, 'back_plate', [s*34, 12, -7], [14, 10, 4], [8, 0, -s*4])
    cube(hand, 'outer_knuckle_cap', [s*40.5, 13, -15], [5, 9, 10], [10, s*6, -s*9])
    # Three broad independently grouped fingers and an inward opposed thumb.
    for i in range(3):
        x = s*(28.5+i*5.6)
        finger = group(side+f'_finger_{i+1}', [x, 11, -19], hand)
        cube(finger, 'knuckle', [x, 9, -20], [5, 6, 6], [8, 0, 0])
        cube(finger, 'proximal', [x, 5.3, -21], [4.8, 6.8, 5.8], [8, 0, 0])
        tip = group(side+f'_finger_{i+1}_tip', [x, 3, -21], finger)
        cube(tip, 'tip', [x, 2, -19.6], [4.8, 4, 7.6])
    thumb = group(side+'_thumb', [s*25.5, 14, -12], hand)
    cube(thumb, 'base', [s*24, 11, -12], [6, 8, 7], [0, s*10, -s*30])
    thumb_tip = group(side+'_thumb_tip', [s*21, 8, -12], thumb)
    cube(thumb_tip, 'tip', [s*21.5, 6.5, -14], [5, 6, 7], [20, -s*12, -s*10])

    thigh = group(side+'_thigh', [s*11, 32, 4], 'pelvis')
    cube(thigh, 'main', [s*12, 25, 3], [13, 16, 15], [-12, 0, -s*7])
    cube(thigh, 'front_plate', [s*12, 25, -5], [11, 12, 5], [-12, -s*4, -s*7])
    cube(thigh, 'outer_shard', [s*18.5, 27, 3], [5, 11, 11], [-8, s*10, -s*13])
    shin = group(side+'_shin', [s*13, 18, 1], thigh)
    cube(shin, 'knee', [s*13, 17, -3], [13, 8, 10], [-8, 0, -s*3])
    cube(shin, 'main', [s*13.5, 11, 4], [12, 13, 13], [12, 0, 0])
    cube(shin, 'front_slab', [s*13.5, 10, -3], [10, 9, 4], [12, 0, s*4])
    calf = [s*13.5, 12, 11]
    cube(shin, 'calf', calf, [10, 10, 4], [12, 0, -s*4])
    foot = group(side+'_foot', [s*13.5, 5, 4], shin)
    cube(foot, 'base', [s*13.5, 3, 0], [15, 6, 19])
    for i in range(3):
        cube(foot, f'toe_{i}', [s*(8.2+i*5.2), 2.5, -9.3], [4.8, 5, 7])

# Neutral swatch provides clay shading in Blockbench; it is not a skin or UV pass.
buffer = io.BytesIO()
Image.new('RGBA', (16, 16), (166, 164, 157, 255)).save(buffer, format='PNG')
swatch = buffer.getvalue()
(ROOT/'neutral-clay.png').write_bytes(swatch)
project = dict(meta=dict(format_version='5.0', model_format='geckolib_model', box_uv=False),
               name='Fractured Guardian — Geometry Study v1',
               geckolib_modid='elementalwands', geckolib_model_type='Entity',
               model_identifier='fractured_guardian', visible_box=[8, 7, 2.5],
               resolution=dict(width=16, height=16), elements=elements, groups=groups,
               outliner=[nodes['root']], textures=[dict(
                   name='neutral-clay.png', uuid=uid('clay'), id='0',
                   source='data:image/png;base64,'+base64.b64encode(swatch).decode(),
                   mode='bitmap', saved=True, internal=True, width=16, height=16,
                   uv_width=16, uv_height=16)], animations=[])
(ROOT/'fractured_guardian.bbmodel').write_text(json.dumps(project, indent=2)+'\n')
geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': dict(identifier='geometry.fractured_guardian', texture_width=16,
                        texture_height=16, visible_bounds_width=8,
                        visible_bounds_height=7, visible_bounds_offset=[0, 2.5, 0]),
    'bones': bones}]}
(ROOT/'fractured_guardian.geo.json').write_text(json.dumps(geo, indent=2)+'\n')

# Shared actual mesh for the offline preview and the deterministic renders.
# Blockbench uses ZYX Euler order: apply X, then Y, then Z to column vectors.
def rotate(p, angles):
    x,y,z=p
    a,b,c=map(math.radians,angles)
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
    return [x,y,z]


quads = [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[0,1,5,4],[3,7,6,2]]
mesh=[]
for e in elements:
    lo,hi=e['from'],e['to']
    raw=[[lo[0],lo[1],lo[2]],[hi[0],lo[1],lo[2]],
         [hi[0],hi[1],lo[2]],[lo[0],hi[1],lo[2]],
         [lo[0],lo[1],hi[2]],[hi[0],lo[1],hi[2]],
         [hi[0],hi[1],hi[2]],[lo[0],hi[1],hi[2]]]
    vertices=[]
    for p in raw:
        q=rotate([v-o for v,o in zip(p,e['origin'])],e['rotation'])
        vertices.append([round(v+o,5) for v,o in zip(q,e['origin'])])
    mesh.append(dict(name=e['name'], vertices=vertices, faces=quads))
(ROOT/'mesh.json').write_text(json.dumps(mesh,separators=(',',':'))+'\n')
all_v=[v for c in mesh for v in c['vertices']]
bounds={a:[round(min(v[i] for v in all_v),3),round(max(v[i] for v in all_v),3)]
        for i,a in enumerate('xyz')}
print(json.dumps(dict(cubes=len(elements),bones=len(bones),bounds=bounds),indent=2))
