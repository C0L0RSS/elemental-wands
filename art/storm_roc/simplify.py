"""Chunky body plates and broad wings; the approved head is never touched."""
import math
from proportions import region
from fold import rotation


def simplify(r):
    old={b['name']:{**b,'cubes':list(b['cubes'])} for b in r.bones};materials={id(c):m for c,m,s,f,d in r.cubes}
    head={'head'}
    for b in r.bones:
        if b.get('parent') in head:head.add(b['name'])
    feathers={n:dict(f) for n,f in r.feathers.items()}
    # Keep only the articulated skeleton and head; decorative plates share owners.
    rig={n for n in old if n in ('root','body','tail_fan') or n.startswith('neck_') and (n.endswith('_skin') or n in ('neck_base','neck_lower','neck_mid','neck_upper','neck_tip')) or '_wing_' in n or n.endswith(('_thigh','_shin','_tarsus','_foot')) or '_toe_' in n}
    r.bones=[b for b in r.bones if b['name'] in head|rig]
    for b in r.bones:
        if b['name'] not in head:b['cubes']=[]
    ids={id(c) for b in r.bones for c in b['cubes']};r.cubes=[v for v in r.cubes if id(v[0]) in ids];r.feathers={}
    def box(owner,center,size,mat,rot=None,feather=False,scar=False):
        r.cube(owner,center,[max(.5,round(v*2)/2) for v in size],mat,rot,scar=scar,feather=feather)
    def plate(name,owner,start,end,width,mat,tip=None,normal=None,animated=False,steps=3):
        import numpy as np
        from fold import frame,euler
        v=np.array(end)-start;length=float(np.linalg.norm(v));d=v/length
        R=frame(d,normal or [0,1,0]);angles=euler(R)
        if animated:
            r.bone(name,start,owner);r.feathers[name]={'start':list(start),'end':list(end),'width':width,'parent':owner};dest=name
        else:dest=owner
        rows=[(0,.66,1),(.60,.90,.78),(.85,1,.50)] if steps==3 else [(0,.75,1),(.68,1,.65)]
        shafted=name.startswith(('left_','right_')) and ('_leading_' in name or '_covert_' in name)
        if shafted:rows=[(0,.62,1),(.57,.86,.64),(.81,1,.25)]
        for i,(a,b,w) in enumerate(rows):
            c=(np.array(start)+v*(a+b)/2).tolist()
            box(dest,c,[width*w,1.5,length*(b-a)],tip if tip and i==len(rows)-1 else mat,angles,'shaft' if shafted else True,scar='wing' if name.endswith('_leading_02') and i==0 else False)
    # A handful of broad keel courses replace the fine ellipsoid lanes.
    for z in range(-26,55,10):
        f=math.sqrt(max(.12,1-((z-10)/48)**2));y=68-(z+8)*.18
        box('body',[0,y,z],[28*f,30*f,11],'white')
        box('body',[0,y+15*f,z],[25*f,4,12],'navy')
    for side,label in ((-1,'right'),(1,'left')):
        for j in range(6):
            z=-27+j*14;y=82-max(0,z+10)*.20
            plate('', 'body',[side*10,y,z],[side*16,y-5,z+23],16,'navy',normal=[side*.65,.76,0],steps=2)
        for j in range(4):
            z=-22+j*20;y=65-(z+8)*.16
            plate('','body',[side*13,y,z],[side*13,y-5,z+15],14,'white',normal=[side,0,0],steps=2)
    # Five broad overlapping collars retain the long neck's silhouette.
    for i,name in enumerate(('neck_base','neck_lower','neck_mid','neck_upper','neck_tip')):
        owner=name+'_skin';y=80+i*17;radius=10-i*.7
        for dy in (1,9,16):
            box(owner,[0,y+dy,-22],[radius*2,10,radius*2],'navy')
            box(owner,[0,y+dy,-22-radius*.66],[radius*2+.5,10,radius*.85],'white')
        for side in (-1,1):
            plate('',owner,[side*6,y+15,-23-radius],[side*7,y+1,-23-radius],10,'white',normal=[side*.5,0,-1],steps=2)
            plate('',owner,[side*(radius-.5),y+15,-17],[side*(radius+1),y+1,-10],10,'navy',normal=[side,0,0],steps=2)
        for side in (-1,1):
            plate('',owner,[side*3,y+14,-14],[side*4,y+3,-8],7.5,'navy',normal=[side*.7,.7,0],steps=3)
    # Scale the arm and hand span by 1.5, and the chord by 1.30.
    def wing(p):return [p[0]*1.5,p[1],-20+(p[2]+20)*1.30]
    for b in r.bones:
        if '_wing_' in b['name'] or b['name'].endswith(('_primaries','_secondaries')):b['pivot']=wing(b['pivot'])
    for side,label in ((-1,'right'),(1,'left')):
        choices=[]
        for name,f in feathers.items():
            if not name.startswith(label+'_') or region(name)!='wing':continue
            nums=[int(v) for v in name.split('_') if v.isdigit()];i=nums[-1] if nums else 0
            if '_primary_' in name:keep=True;steps=3;width=f['width']*1.5
            elif '_secondary_' in name:keep=i%2==0;steps=3;width=f['width']*2.8
            elif '_covert_' in name:keep=True;steps=3;width=f['width']*1.4
            elif '_leading_' in name:keep=True;steps=3;width=f['width']*1.2
            else:continue # Alula rods are removed.
            if not keep:continue
            cubes=old[name]['cubes'];mat=materials[id(cubes[0])];tip=materials[id(cubes[-1])]
            parent=f['parent']
            if parent.endswith(('_primaries','_secondaries')):parent=old[parent]['parent']
            start=wing(f['start']);end=wing(f['end'])
            stretch=1.6 if '_leading_' in name else 1.2 if '_covert_' in name else 1
            end=[a+(b-a)*stretch for a,b in zip(start,end)]
            plate(name,parent,start,end,width,mat,tip if tip!=mat else None,animated=True,steps=steps)
    # Fifteen broad red vanes, including the short overlap at the tail root.
    for name,f in feathers.items():
        if not name.startswith('tail_'):continue
        i=int(name.split('_')[-1])
        keep=('_middle_' in name and i in (0,5,10)) or ('_covert_' in name and i in (0,4,8)) or (name.count('_')==1 and i%2==0)
        if not keep:continue
        cubes=old[name]['cubes'];mat=materials[id(cubes[0])]
        plate(name,'tail_fan',f['start'],f['end'],f['width']*(2 if name.count('_')==1 else 2.5),mat,normal=[.72,.69,0],animated=True)
    # Short femur, bulky feathered drumstick, high backward hock and long tarsus.
    # Bind lengths are 14 / 28 / 26 pixels; the pose solver plants the original feet.
    for label in ('left','right'):
        foot=old[label+'_foot']['pivot'];x,z=foot[0],foot[2]
        for suffix,y in (('thigh',75),('shin',61),('tarsus',33)):
            next(b for b in r.bones if b['name']==label+'_'+suffix)['pivot']=[x,y,z]
        box(label+'_thigh',[x,68,z],[12,16,14],'white')
        for y,w,d,h in ((56,17,18,13),(46,15,17,12),(37,11,12,9)):
            box(label+'_shin',[x,y,z],[w,h,d],'white')
        for side in (-1,1):
            plate('',label+'_shin',[x+side*6,60,z-5],[x+side*7,36,z+2],10,'white',normal=[side,0,0],steps=3)
            plate('',label+'_shin',[x+side*7,60,z+3],[x+side*8,46,z+7],9,'navy',normal=[side,0,0],steps=2)
        box(label+'_tarsus',[x,33,z],[10,7,10],'scale')
        box(label+'_tarsus',[x,23,z],[8,18,8],'scale')
        box(label+'_tarsus',[x,11,z],[7,12,7],'scale')
        box(label+'_foot',[foot[0],6.3,foot[2]-1.35],[11,6,10],'scale')
        for i in range(4):
            base=label+f'_toe_{i}_base';tip=label+f'_toe_{i}_tip'
            # Merge adjacent claw courses into three hooked steps.
            for name in (base,tip):
                cs=old[name]['cubes'];scales=[c for c in cs if materials[id(c)]=='scale'];talons=[c for c in cs if materials[id(c)]=='talon']
                for group,mat in ([(scales,'scale')] if scales else [])+[(talons[k:k+2],'talon') for k in range(0,len(talons),2)]:
                    lo=[min(c['origin'][j] for c in group) for j in range(3)];hi=[max(c['origin'][j]+c['size'][j] for c in group) for j in range(3)]
                    box(name,[(a+b)/2 for a,b in zip(lo,hi)],[b-a for a,b in zip(lo,hi)],mat)
    # Parent order and count remain deterministic.
    pending=r.bones;ordered=[];seen=set()
    while pending:
        ready=[b for b in pending if not b.get('parent') or b['parent'] in seen];assert ready
        ordered.extend(ready);seen.update(b['name'] for b in ready);pending=[b for b in pending if b['name'] not in seen]
    r.bones=ordered
    return r
