"""Long-keel body and five overlapping neck segments; approved head is rigid."""
import math
from proportions import region

NECK_NAMES=['neck_base','neck_lower','neck_mid','neck_upper','neck_tip']
NECK_LENGTH=17
HEAD_BIND=[0,165,-22]

def redesign(r,detail=True):
    family={'head'}
    for b in r.bones:
        if b.get('parent') in family:family.add(b['name'])
    keep=family|{b['name'] for b in r.bones if region(b['name']) in ('wing','tail','leg')}|{'root','body'}
    r.bones=[b for b in r.bones if b['name'] in keep]
    r.feathers={k:v for k,v in r.feathers.items() if k in keep}
    for b in r.bones:
        if b['name']=='body':b['cubes']=[];b['pivot']=[0,65,15]
    kept={id(c) for b in r.bones for c in b['cubes']}
    r.cubes=[v for v in r.cubes if id(v[0]) in kept]
    # Affine bind-space changes affect entire regions, never the head proportions.
    def change(name,p):
        x,y,z=p
        if name in family:return [x,y+74.2,z-6]
        kind=region(name)
        if kind=='wing':return [x*1.28,79+(y-66.75),-20+z*1.35]
        if kind=='tail':return [x,47+(y-42)*.12,37+(z-26)*.62]
        if kind=='leg':return [x*1.35,7+(y-7)*1.50,-8+(z-8)*1.35]
        return list(p)
    for b in r.bones:
        name=b['name'];kind=region(name);b['pivot']=[round(v,4) for v in change(name,b['pivot'])]
        if name=='head':b['parent']='neck_tip'
        for c in b['cubes']:
            center=change(name,[c['origin'][j]+c['size'][j]/2 for j in range(3)])
            # Keep feather cross sections; lengthen wings without stretching pixels.
            if kind=='wing':c['size']=[round(v*f*2)/2 for v,f in zip(c['size'],[1.28,1,1.35])]
            if kind=='leg':c['size']=[max(.5,round(v*f*2)/2) for v,f in zip(c['size'],[1.35,1.50,1.35])]
            c['origin']=[round(center[j]-c['size'][j]/2,4) for j in range(3)]
            if 'pivot' in c:c['pivot']=[round(v,4) for v in change(name,c['pivot'])]
    for name,f in r.feathers.items():
        f['start']=change(name,f['start']);f['end']=change(name,f['end'])
    # Rebuild wing vanes in their enlarged bind frame. Their normals and centers
    # must agree after the regional resize, or the fold exposes long edge-on bars.
    materials={id(c):(m,scar,feather) for c,m,scar,feather,d in r.cubes}
    wing_feathers=[(name,dict(f)) for name,f in r.feathers.items() if region(name)=='wing']
    for name,f in wing_feathers:
        b=next(b for b in r.bones if b['name']==name)
        mat=materials[id(b['cubes'][0])][0];tip=materials[id(b['cubes'][-1])][0]
        ids={id(c) for c in b['cubes']};r.cubes=[v for v in r.cubes if id(v[0]) not in ids];r.bones.remove(b)
        r.feather(name,f['parent'],f['start'],f['end'],round(f['width']*1.28*2)/2,mat,tip=tip if tip!=mat else None)
    # Wing spar volumes stay beneath the feather rows, with no exposed slate beam.
    for b in r.bones:
        if not any(b['name'].endswith('_wing_'+j) for j in ('shoulder','elbow','wrist')):continue
        ids={id(c) for c in b['cubes']};r.cubes=[v for v in r.cubes if id(v[0]) not in ids];b['cubes']=[]
    def box(b,c,s,m,rot=None,scar=False):
        r.cube(b,c,[max(.5,round(v*2)/2) for v in s],m,rot,scar)
    # Replace the old tail with broad, long, overlapping vanes, laid level in bind.
    tail_names={b['name'] for b in r.bones if b['name'].startswith('tail')}
    removed={id(c) for b in r.bones if b['name'] in tail_names for c in b['cubes']}
    r.bones=[b for b in r.bones if b['name'] not in tail_names]
    r.cubes=[v for v in r.cubes if id(v[0]) not in removed]
    r.feathers={k:v for k,v in r.feathers.items() if k not in tail_names}
    r.bone('tail_fan',[0,59,42],'body')
    for i in range(17):
        a=(i-8)/8
        r.feather(f'tail_{i:02}','tail_fan',[a*10,57+abs(a)*4,42],[a*33,55+abs(a)*24,109-abs(a)*22],11,'tail_red')
    for i in range(11):
        a=(i-5)/5
        r.feather(f'tail_middle_{i}','tail_fan',[a*9,59,42],[a*25,60+abs(a)*16,93-abs(a)*8],10,'tail_red')
    for i in range(9):
        a=(i-4)/4
        r.feather(f'tail_covert_{i}','tail_fan',[a*9,61,40],[a*18,62+abs(a)*10,72-abs(a)*6],10,'rose')
    # Long deep keel: high shoulders descend into narrow hips.
    for z in range(-30,59,4):
        q=(z-10)/48;f=math.sqrt(max(.04,1-q*q));waist=1-.22*max(0,(z-12)/46)
        y=68-(z+8)*.18
        for lane in range(-1,2):
            x=lane*9*f*waist;h=34*f*(1-.16*abs(lane))
            box('body',[x,y,z],[10*f*waist,h,4.5],'white')
        box('body',[0,y+17*f,z],[24*f*waist,4,6],'navy')
    for side in (-1,1):
        for j in range(6):
            z=-25+j*7;y=79+3*math.sin(j/6*math.pi)-j*.8
            box('body',[side*13,y,z],[13,8,10],'navy')
    if detail:
        for side,label in ((-1,'right'),(1,'left')):
            for row in range(3):
                for j in range(8):
                    z=-27+j*9;x=side*(4+row*5.5);y=82-row*2-max(0,z+10)*.20
                    r.feather(f'{label}_mantle_{row}_{j}','body',[x,y,z],[x+side*3,y-5,z+20],9,'navy',tip='slate' if row==2 else None)
            for row in range(3):
                for j in range(6):
                    z=-24+j*12;x=side*(12+row*1.3);y=66-row*6-(z+8)*.16
                    r.feather(f'{label}_flank_{row}_{j}','body',[x,y,z],[x*.9,y-8,z+12],8,'white')
    # A separate lower tarsus joint splits the scaled leg below the hock.
    for label in ('left','right'):
        shin=next(b for b in r.bones if b['name']==label+'_shin')
        foot=next(b for b in r.bones if b['name']==label+'_foot')
        name=label+'_tarsus';r.bone(name,[shin['pivot'][0],23,shin['pivot'][2]],shin['name']);foot['parent']=name
        lower=next(b for b in r.bones if b['name']==name)
        for c in list(shin['cubes']):
            if c['origin'][1]+c['size'][1]/2<23:
                shin['cubes'].remove(c);lower['cubes'].append(c)
    for i,name in enumerate(NECK_NAMES):
        y=80+i*NECK_LENGTH;radius=10-i*.7
        r.bone(name,[0,y,-22],'body' if i==0 else NECK_NAMES[i-1])
        # Overlap beyond both pivot ends; each joint stays buried during bending.
        for dy in (-2,2,6,10,14,18):
            w=radius*2-(1 if dy in (-2,18) else 0)
            box(name,[0,y+dy,-22],[w,5,w],'navy')
            box(name,[0,y+dy,-22-radius*.66],[w+.5,5,radius*.85],'white')
        if detail:
            for side,label in ((-1,'right'),(1,'left')):
                for row in range(2):
                    for j in range(2):
                        x=side*(3+j*5);yy=y+6+row*9
                        r.feather(f'{label}_throat_{i}_{row}_{j}',name,[x,yy,-23-radius],[x*1.12,yy-13,-22-radius],6,'white')
                    for j in range(2):
                        x=side*(radius-.5);z=-20+j*6;yy=y+6+row*9
                        r.feather(f'{label}_ruff_{i}_{row}_{j}',name,[x,yy,z],[x*1.08,yy-13,z+4],7,'navy')
    if detail:
        for i,name in enumerate(NECK_NAMES):
            y=80+i*NECK_LENGTH
            for j in range(3):
                r.feather(f'neck_mane_{i}_{j}',name,[(j-1)*4,y+14,-13],[(j-1)*5,y+2,-3],7,'navy',tip='slate')
    # Independent skin bones allow the flight neck to compress and thicken
    # without inheriting any scale into the approved head or the neck joint rig.
    for name in NECK_NAMES:
        b=next(b for b in r.bones if b['name']==name);skin=name+'_skin'
        children=[c for c in r.bones if c.get('parent')==name and c['name'] not in NECK_NAMES+['head']]
        r.bone(skin,b['pivot'],name);r.bones[-1]['cubes']=b['cubes'];b['cubes']=[]
        for child in children:child['parent']=skin
    # Stable parent-before-child order is required by both review and export.
    pending=r.bones;ordered=[];seen=set()
    while pending:
        ready=[b for b in pending if not b.get('parent') or b['parent'] in seen]
        assert ready,'Cyclic rig'
        ordered.extend(ready);seen.update(b['name'] for b in ready)
        pending=[b for b in pending if b['name'] not in seen]
    r.bones=ordered
    from simplify import simplify
    return simplify(r)
