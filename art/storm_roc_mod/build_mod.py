#!/usr/bin/env python3
"""Storm Roc, chunky mod-boss model.

Solid blocks, many of them angled, on the study's skeleton, so every combat clip, socket
and hitbox carries over. Wing and tail feathers ride the study's own feather bones and
follow its feather lines, so they fold the way the approved study's did, only thicker
and fewer. A pixel-art atlas is painted per block face (see paint.py).

    python3 art/storm_roc_mod/build_mod.py          # write candidate/ with the approved atlas
    python3 art/storm_roc_mod/build_mod.py --flat   # shape blockout: flat colours
    python3 art/storm_roc_mod/build_mod.py --check  # regenerate in memory and compare
"""
import argparse,io,json,math,sys
from pathlib import Path
import numpy as np
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
STUDY=ROOT/'art/storm_roc'
sys.path.insert(0,str(STUDY));sys.path.insert(0,str(HERE))
from fold import rotation,euler,frame
OUT=HERE/'candidate'
SIDES=(('left',1),('right',-1))
# Model pixels get 1 texel; the head, beak and crest get 2 so the face can carry detail.
FINE=('head','upper_beak','lower_beak','crest','crest_plume','left_cheek','right_cheek','left_cere','right_cere')

class Model:
    def __init__(self,study):
        self.bones=[{**{k:b[k] for k in ('name','parent','pivot','rotation') if k in b},'cubes':[]} for b in study]
        self.by={b['name']:b for b in self.bones}
        self.parts=[]
    def pivot(self,name):return np.array(self.by[name]['pivot'],dtype=float)
    def box(self,bone,center,size,mat,rot=None,kind='block',**extra):
        size=[max(.5,round(float(v)*2)/2) for v in size]
        c={'origin':[round(float(a)-s/2,4) for a,s in zip(center,size)],'size':size}
        if rot is not None and any(abs(r)>1e-4 for r in rot):
            c['pivot']=[round(float(v),4) for v in center];c['rotation']=[round(float(r),4) for r in rot]
        self.by[bone]['cubes'].append(c)
        self.parts.append({'bone':bone,'cube':c,'mat':mat,'kind':kind,
            'density':2 if bone.startswith(FINE) else 1,**extra})
        return c
    def slab(self,bone,a,b,width,thick,mat,up=(0,1,0),kind='feather',**extra):
        """A block from a to b: its length runs a->b (local +z, the tip end), thickness along up."""
        a,b=np.array(a,dtype=float),np.array(b,dtype=float);d=b-a;R=frame(d,np.array(up,dtype=float))
        # Which way its width runs toward the back (+z): the painter turns covert rows that way.
        extra.setdefault('trail',float(np.sign(round(R[2,0],3))))
        return self.box(bone,(a+b)/2,[width,thick,np.linalg.norm(d)],mat,euler(R),kind,**extra)
    def quill(self,bone,a,b,width,thick,mat,tip_mat,up,tip=.15,root=8):
        """A flight feather from a to b with a painted tip and a pointed end; its root starts
        `root` further in, buried in whatever it grows from, so its front never shows."""
        a,b,up=np.array(a,dtype=float),np.array(b,dtype=float),np.array(up,dtype=float)
        d=(b-a)/np.linalg.norm(b-a);up=up/np.linalg.norm(up)
        self.slab(bone,a-d*root,b,width,thick,mat,up=up,tip=tip,tip_mat=tip_mat)
        # The point: a square turned 45° in the vane, centred on the end, so its far corner
        # leads and its side corners meet the feather's edges. A hair thinner, so no faces fight.
        q=(d+np.cross(up,d))/math.sqrt(2);side=width/math.sqrt(2)
        self.slab(bone,b-q*side/2,b+q*side/2,side,thick-.5,tip_mat,up=up,kind='tip')

def mirror(p,side):return np.array([p[0]*side,p[1],p[2]],dtype=float)

def feather_line(study_bone):
    """The study feather's centre line, width and vane normal, read from its cubes."""
    cubes=study_bone['cubes'];best=max(cubes,key=lambda c:c['size'][0]*c['size'][2])
    R=rotation(best.get('rotation',[0,0,0]));d=R[:,2];up=R[:,1]
    centres=np.array([np.array(c['origin'])+np.array(c['size'])/2 for c in cubes])
    ends=np.concatenate([[c0-d*c['size'][2]/2,c0+d*c['size'][2]/2] for c0,c in zip(centres,cubes)])
    proj=ends@d;a=ends[np.argmin(proj)];b=ends[np.argmax(proj)]
    return a,b,max(c['size'][0] for c in cubes),up

def sculpt(study):
    m=Model(study);old={b['name']:b for b in study}
    P=m.pivot

    # ---------------------------------------------------------------- body
    # A deep barrel: core, keel and chest, mantle, rump and belly, plus hanging breast plates.
    m.box('body',[0,67,18],[36,32,62],'rust')                       # core
    m.box('body',[0,64,-14],[32,30,24],'cream',[14,0,0])           # chest, tipped forward
    m.box('body',[0,52,-2],[20,12,40],'cream',[6,0,0])             # keel
    m.box('body',[0,50,26],[28,8,40],'cream')                      # belly
    m.box('body',[0,83,8],[34,9,46],'rust',[-4,0,0],vein=True)     # mantle across the shoulders
    m.box('body',[0,74,50],[28,24,22],'rust',[-12,0,0])            # rump
    for label,s in SIDES:
        m.box('body',mirror([18,64,16],s),[6,24,50],'rust')        # flank
        m.box('body',mirror([12,80,-8],s),[14,12,22],'sienna',[8,0,s*-10],vein=True)  # shoulder
    # Shaggy breast plates: three rows, cream with rust chevrons, hanging forward and down.
    for row,(y,z,w) in enumerate([(78,-28,26),(68,-31,28),(57,-28,24)]):
        for col in (-1,0,1):
            x=col*w/3
            m.slab('body',[x,y+4,z+5],[x*1.08,y-8,z-1],w/3+2,3.5,'cream',up=(0,.35,-1),kind='plate')
    # ---------------------------------------------------------------- neck
    # Each skin bone carries a chamfered column, a cream throat plate and shaggy hackles.
    segments=[('neck_base_skin',88,26,25),('neck_lower_skin',105,24,22),('neck_mid_skin',122,23,22),
              ('neck_upper_skin',139,22,22),('neck_tip_skin',156,22,22)]
    for i,(bone,y,w,d) in enumerate(segments):
        m.box(bone,[0,y,-21],[w,26,d-6],'ruff')
        m.box(bone,[0,y,-21],[w-6,26,d],'ruff')
        m.box(bone,[0,y-1,-21-d/2],[w-8,24,3],'cream',[4,0,0])     # throat
        hackles=4 if i<3 else 2
        for k in range(hackles):
            a=math.radians(-60+k*(240/(hackles-1)) if hackles>1 else 0)   # around the sides and back
            for s in (1,-1):
                if s==-1 and abs(math.degrees(a))<1e-6:continue
                ux,uz=math.sin(a)*s,math.cos(a)
                top=[ux*(w/2-1),y+11,-21+uz*(d/2-1)];bot=[ux*(w/2+3),y-12,-21+uz*(d/2+3)]
                m.slab(bone,top,bot,9,2.5,'ruff',up=(ux,0,uz),kind='hackle')

    # ---------------------------------------------------------------- head
    # Bearded-vulture head, bigger and wider than the neck: a wedge skull that narrows to the
    # bill, a sleek sloping crown, heavy brows, small forward eyes in a dark mask band, a long
    # hooked bill with a bristle beard.
    m.box('head',[0,172,-13],[32,24,16],'crown')                      # back of the skull
    m.box('head',[0,172.5,-26.5],[28,23,13],'crown')                  # mid skull
    m.box('head',[0,170.5,-38],[17,13,10],'crown')                    # face, narrowing to the bill
    m.slab('head',[0,186,-7],[0,185,-25],25,5,'crown',up=(0,1,0),kind='block')     # domed crown
    m.slab('head',[0,185,-23],[0,179.5,-41],18,5,'crown',up=(0,1,.25),kind='block') # sloping to the bill
    m.box('head',[0,169,-3.5],[26,18,7],'ruff',[10,0,0])              # nape into the neck
    for label,s in SIDES:
        # Jowls: thick wedges, widest behind the eyes, tapering toward the bill.
        m.slab('head',mirror([13.5,168.5,-6],s),mirror([10.5,166,-31],s),12,6,'crown',up=mirror([1,0,.12],s),kind='block')
        # Dark mask band: flush plates from the bill, through the eye, tapering back across the cheek.
        m.slab('head',mirror([7.4,175.4,-44.5],s),mirror([12.2,176.6,-35],s),3.5,1.5,'mask',up=mirror([1,0,.4],s),kind='block')
        m.slab('head',mirror([13.5,176.6,-32],s),mirror([16.6,175.6,-19],s),3,1.5,'mask',up=mirror([1,0,.1],s),kind='block')
        # Brow: a ridge over the eye, scowling down toward the bill.
        m.slab('head',mirror([6,181.5,-42],s),mirror([14.5,180.5,-29],s),6,4.5,'crown',up=mirror([.15,1,0],s),kind='brow')
        # Small forward eye: red ring, pale iris, black pupil, painted on its outward face.
        out=mirror([math.sin(math.radians(38)),0,-math.cos(math.radians(38))],s)
        c=mirror([11,176.6,-36.6],s)
        m.slab('head',c-out*1.4,c+out*1.3,6,4.5,'eye',up=(0,1,0),kind='eye')
    for k,label in enumerate(['left_cheek_','right_cheek_']):
        s=1 if k==0 else -1
        for i in range(0,6,2):
            p=P(f'{label}{i}')+mirror([4,0,6],s)
            m.slab(f'{label}{i}',p,p+mirror([3,-11,7],s),8,2.5,'ruff',up=mirror([1,0,0],s),kind='hackle')

    # Long upper bill: a dark base under bristles, a fairly straight culmen, a sharp hook.
    ub='upper_beak'
    m.box(ub,[0,168.5,-44],[12,12,8],'horn_dark')
    m.slab(ub,[0,168.5,-46],[0,167,-53],10,10,'horn',up=(0,1,0),kind='bill')
    m.slab(ub,[0,168,-52],[0,166,-58],8,9,'horn',up=(0,1,0),kind='bill')
    m.slab(ub,[0,173.5,-46],[0,171,-57],5,3,'horn',up=(0,1,0),kind='bill')        # culmen ridge
    m.slab(ub,[0,168,-55.5],[0,159.5,-59.6],7,6.5,'horn',up=(0,.55,-1),kind='bill')   # hook
    m.slab(ub,[0,161.5,-59.4],[0,155.5,-57.4],4,4,'horn_tip',up=(0,0,-1),kind='bill')  # point
    m.slab(ub,[0,173.5,-42],[0,171,-47],11,3,'mask',up=(0,1,0),kind='block')       # bristles over the base
    # Lower jaw: built back to its hinge, so it swings open from the head instead of dropping
    # away from it, with a dark gape behind the bill that shows when it opens.
    lb='lower_beak'
    m.box(lb,[0,160.75,-37],[13,6.5,14],'crown')                      # jaw, from the hinge
    m.slab(lb,[0,161.5,-42],[0,161,-55],8,4,'horn',up=(0,1,0),kind='bill')
    m.box(lb,[0,160,-44],[10,5,4],'horn_dark')
    m.box('head',[0,162,-38.5],[11,3,15],'mouth')                     # gape
    # Beard: a dark bristle tuft under the bill.
    for x,dz in ((-3,0),(3,0),(0,-2.5)):
        m.slab(lb,[x,158.5,-40+dz],[x*1.3,147,-44+dz],4.5,3,'mask',up=(0,0,-1),kind='hackle')
    # Swept-back crest: three prongs with navy tips.
    for name,end in (('crest_plume_0_1',[0,188,0]),('crest_plume_0_0',[-7,186,-2]),('crest_plume_0_2',[7,186,-2])):
        p=P(name)+[0,4,0];tip=np.array(end,dtype=float)
        mid=p+(tip-p)*.68
        m.slab(name,p,mid,6,3,'crest',up=(0,1,.3),kind='feather')
        m.slab(name,mid-(tip-p)*.06,tip,4,2.5,'navy',up=(0,1,.3),kind='feather')

    # ---------------------------------------------------------------- wings
    # A thick arm along the leading edge carries the wing: big blocks for the upper arm, forearm
    # and hand, with knuckles over the elbow and wrist so the bends stay closed. Behind it, one
    # broad covert panel per arm bone covers the roots of a few broad flight feathers, each with
    # a pointed tip. Covert rows and tip colours are painted, not built.
    for label,s in SIDES:
        n=lambda name:f'{label}_{name}'
        sh,el,wr=P(n('wing_shoulder')),P(n('wing_elbow')),P(n('wing_wrist'))
        hand=wr+(wr-el)/np.linalg.norm(wr-el)*38
        lift=np.array([0,2.5,-4.5])
        m.slab(n('wing_shoulder'),sh+[s*-7,0,0]+lift,el+lift,20,12,'rust',kind='arm')
        m.slab(n('wing_elbow'),el+lift,wr+lift,18,11,'rust',kind='arm')
        m.slab(n('wing_wrist'),wr+lift,hand+[0,1.5,-4.5],14,9,'rust',kind='arm')
        m.box(n('wing_elbow'),el+lift,[12,12.5,20.5],'rust')                # elbow knuckle
        m.box(n('wing_wrist'),wr+lift,[10,10.5,17],'rust')                  # wrist knuckle, inside the forearm
        # Covert panels: lesser coverts tucked under the arm, greater coverts over the feathers.
        # Deep enough to swallow the feather roots from above and below.
        m.slab(n('wing_shoulder'),[sh[0]+s*2,80.5,1],[el[0]+s*3,81,1],30,8,'rust',kind='covert')
        m.slab(n('wing_elbow'),[el[0]-s*3,81,2],[wr[0]+s*2,82,2],32,8.5,'rust',kind='covert')
        m.slab(n('wing_wrist'),[wr[0]-s*2,82.5,-4],[wr[0]+s*28,83.5,-4],24,7.5,'rust',kind='covert')
        def feather(name,widen,thick,mat,tip_mat,tip=.15):
            a,b,w,up=feather_line(old[name])
            m.quill(name,a,b,w*widen,thick,mat,tip_mat,up,tip)        # root buried in the coverts and arm
        # Three broad secondaries along the back edge, four slotted primaries at the hand.
        for i in (2,6,10):feather(n(f'secondary_{i:02}'),1.3,3.5,'rust','rust_dark')
        for f in (0,3,6,9):
            feather(n(f'primary_{f:02}'),1.4,3.5,'rust','navy' if f>=6 else 'rust_dark',.22 if f>=6 else .15)
        feather(n('root_tertial_1'),1.5,3.5,'rust','rust_dark')

    # ---------------------------------------------------------------- tail
    # A bearded vulture's wedge tail: seven feathers fanned from the rump, the middle ones
    # longest. The middle feather lies on top and each outer pair a little lower, banked out, so
    # the fan overlaps in layers over a gentle dome instead of tilting all one way.
    for k in range(-3,4):
        name=f'tail_{8+2*k:02}';a=P(name);step=abs(k)
        yaw=math.radians(9*k);pitch=math.radians(-5-step)
        d=np.array([math.sin(yaw)*math.cos(pitch),math.sin(pitch),math.cos(yaw)*math.cos(pitch)])
        bank=math.radians(7*step)*np.sign(k)
        a=a+[0,1.2*(3-step)-(a[1]-57),0]
        m.quill(name,a,a+d*(70-6*step),20,3,'rust','rust_dark',[math.sin(bank),math.cos(bank),0],.14)
    # Upper tail coverts: a short layered cap over the feather roots.
    for name,yaw in (('tail_covert_0',-14),('tail_covert_4',0),('tail_covert_8',14)):
        a=P(name)+[0,4.5 if yaw==0 else 3.5,0];r=math.radians(yaw)
        d=np.array([math.sin(r),-.06,math.cos(r)])
        m.quill(name,a,a+d*(34 if yaw==0 else 28),24,4,'rust','rust_dark',[math.sin(r)*.15,1,0],.12)

    # ---------------------------------------------------------------- legs
    for label,s in SIDES:
        x=13.5*s
        m.box(f'{label}_thigh',[x,70,-10.7],[13,16,15],'rust')
        sh=f'{label}_shin'
        m.box(sh,[x,48,-10.7],[14,28,15],'rust')                      # feathered trousers
        for k,(ax,az) in enumerate(((1,0),(0,-1),(-1,0),(0,1))):
            top=[x+ax*6,58,-10.7+az*6];bot=[x+ax*9,34,-10.7+az*9]
            m.slab(sh,top,bot,10,2.5,'rust' if k!=1 else 'cream',up=(ax,0,az),kind='hackle')
        ta=f'{label}_tarsus'
        m.box(ta,[x,20,-10.7],[7,30,7],'skin',kind='scales')
        m.box(ta,[x,20,-14.6],[5,30,1.5],'skin',kind='scutes')
        m.box(f'{label}_foot',[x,6,-11.5],[11,5,10],'skin',kind='scales')
        for i in range(4):
            base=f'{label}_toe_{i}_base';tip=f'{label}_toe_{i}_tip'
            pb,pt=P(base),P(tip)
            m.slab(base,pb,pt,4.5,4,'skin',up=(0,1,0),kind='scales')
            a,b,w,up=feather_line(old[tip]) if old[tip]['cubes'] else (pt,pt+[0,-4,-6],3,np.array([0,1,0]))
            m.slab(tip,pt,b,3.5,3.5,'talon',up=up,kind='talon')
    return m

# The approved bearded-vulture atlas (art/storm_roc_texture_study) was painted onto the UV
# islands paint.py packs for the sculpt above, so the sculpt must keep packing exactly that.
TEXTURES=ROOT/'art/storm_roc_texture_study'
# The face and feet in use; heads.py and feet.py hold the variants to pick from.
HEAD='demon'
FEET='sickle_huge'

def refine(m,head=None,feet=None):
    """The approved shape revision, made after painting: every face keeps the island the atlas
    was painted on, so the widened blocks stretch their tiles a little instead of repacking."""
    by=m.by
    def resize(c,size):
        centre=np.array(c['origin'])+np.array(c['size'])/2
        c['size']=[float(v) for v in size];c['origin']=(centre-np.array(c['size'])/2).tolist()
    # The tail fan in exact mirrored pairs around its middle feather.
    for left,right in (('tail_02','tail_14'),('tail_04','tail_12'),('tail_06','tail_10'),('tail_covert_0','tail_covert_8')):
        for c,mirrored in zip(by[left]['cubes'],by[right]['cubes'],strict=True):
            uv=mirrored['uv'];mirrored.clear()
            mirrored.update(origin=[-c['origin'][0]-c['size'][0],*c['origin'][1:]],size=list(c['size']))
            if 'pivot' in c:mirrored['pivot']=[-c['pivot'][0],*c['pivot'][1:]]
            if 'rotation' in c:mirrored['rotation']=[c['rotation'][0],-c['rotation'][1],-c['rotation'][2]]
            mirrored['uv']=uv
    # A broader chest and shoulder mantle, with heavier sloped shoulders.
    body=by['body']['cubes']
    resize(body[1],[40,32,30]);resize(body[4],[42,11,52])
    for i in (7,9):resize(body[i],[18,14,26])
    # A smaller rump, tucked under the back line instead of standing out behind it, that still
    # covers the tail's roots.
    rump=body[5];rump['pivot']=[0,71,46];rump['size']=[24.,16.,17.]
    rump['origin']=(np.array(rump['pivot'])-np.array(rump['size'])/2).tolist()
    # The neck's two lowest sections flare out to meet them, about each section's own pivot.
    for name,k in (('neck_base_skin',1.16),('neck_lower_skin',1.07)):
        pivot=np.array(by[name]['pivot'],dtype=float);s=np.array([k,1,k])
        for c in by[name]['cubes']:
            centre=pivot+(np.array(c['origin'])+np.array(c['size'])/2-pivot)*s
            c['size']=(np.array(c['size'])*s).tolist();c['origin']=(centre-np.array(c['size'])/2).tolist()
            if 'pivot' in c:c['pivot']=(pivot+(np.array(c['pivot'])-pivot)*s).tolist()
    # Two overlapping collars sink into the chest and taper into the neck. They ride the first
    # neck section, so the join stays closed as the neck folds; they reuse its column's tiles.
    column=next(p for p in m.parts if p['bone']=='neck_base_skin')
    for centre,size in (([0,75,-21],[36,12,28]),([0,82,-21],[33,12,26])):
        c={'origin':(np.array(centre)-np.array(size)/2).tolist(),'size':[float(v) for v in size],'uv':column['cube']['uv']}
        by['neck_base_skin']['cubes'].append(c);m.parts.append({**column,'cube':c})

    # A broad facial mask, still using the original islands. Width is the vertical
    # axis of these side-facing slabs; leave the eye and cream brow in front of it.
    for p in m.parts:
        if p['bone']=='head' and p['mat']=='mask':
            c=p['cube'];resize(c,[8 if c['size'][2]<12 else 7,1.5,c['size'][2]])
            for key in ('origin','pivot'):c[key][1]-=2
        if p['bone']=='head' and p['kind']=='eye':
            c=p['cube'];out=rotation(c['rotation'])[:,2]*1.6
            for key in ('origin','pivot'):c[key]=(np.array(c[key])+out).tolist()

    # Extend the wedge from its buried roots, keeping each diamond point attached.
    for k in range(-3,4):
        shaft,point=by[f'tail_{8+2*k:02}']['cubes']
        d=rotation(shaft['rotation'])[:,2];extra=22-5*abs(k)
        centre=np.array(shaft['pivot'])+d*extra/2
        shaft['size'][2]+=extra;shaft['pivot']=centre.tolist()
        shaft['origin']=(centre-np.array(shaft['size'])/2).tolist()
        for key in ('origin','pivot'):point[key]=(np.array(point[key])+d*extra).tolist()

    # Three crown/nape tiers, rooted at the LOCKED plume pivots inside the skull.
    # Each feather is three overlapping solid blocks, stepping down to a small tip.
    # Positive local X rotation raises these rearward feathers; negative X sleeks
    # them. No rest rotation is added to the skeleton or animation channels.
    def crest_feather(name,end,width,up):
        root=m.pivot(name);end=np.array(end,dtype=float);d=end-root
        previous=list(by[name]['cubes'])
        for i,(a,b,w,t) in enumerate(((-.13,.64,width,3.5),(.57,.88,width*.65,2.5),(.83,1,width*.28,1.5))):
            c=m.slab(name,root+d*a,root+d*b,w,t,'crest',up=up,kind='feather',v3='crest')
            if i<len(previous):
                # Reuse the six original crest islands, never move/repack them.
                old=previous[i];uv=old['uv'];old.clear();old.update(c);old['uv']=uv
                by[name]['cubes'].pop();m.parts.pop()
                next(p for p in m.parts if p['cube'] is old)['v3']='crest'
    for row in range(3):
        for col in range(3):
            x=(col-1)*7.5
            edge=abs(col-1)
            y,z=((201-edge*2,-9+edge*7),(198-edge*2,6+edge*5),(191-edge*3,19+edge*4))[row]
            crest_feather(f'crest_plume_{row}_{col}',[x,y,z],8,(0,1,0))
    # The lateral nape fan uses the existing cheek bones, with roots inside the
    # skull. These can flare with local X plus mirrored Z rotation when animated.
    for label,s in SIDES:
        for row in range(2):
            for col in (0,2,3):
                name=f'{label}_cheek_blue_{row}_{col}'
                end=mirror([20+col*.6,193-row*17-col*1.4,1+col*3],s)
                crest_feather(name,end,7,mirror([1,.35,0],s))

    import heads,feet as talons
    heads.shape(m,head or HEAD)
    talons.shape(m,feet or FEET)

    # Only additions receive new UV islands, below the approved atlas's last row.
    # This same deterministic packing is used by build_texture_v3.py.
    from paint import FACES,dims
    x,y,shelf=0,544,0
    for p in m.parts:
        if 'uv' in p['cube']:continue
        p['cube']['uv']={}
        for face in FACES:
            w,h=dims(p,face)
            if x+w+2>1024:x=0;y+=shelf;shelf=0
            assert y+h+2<=1024,'crest exceeds unused atlas space'
            p['cube']['uv'][face]={'uv':[x+1,y+1],'uv_size':[w,h]}
            x+=w+2;shelf=max(shelf,h+2)

def approved_atlas(m):
    """The approved atlas and glow mask PNGs, after checking the sculpt still packs the islands
    they were painted on."""
    layout=json.loads((TEXTURES/'layout.json').read_text())['parts']
    assert len(layout)==len(m.parts),'the sculpt no longer matches the approved atlas'
    for i,(a,b) in enumerate(zip(layout,m.parts)):
        assert a['bone']==b['bone'] and a['cube']==json.loads(json.dumps(b['cube'])),('approved atlas layout',i,b['bone'])
    return (TEXTURES/'storm-roc-vulture-v3.png').read_bytes(),(TEXTURES/'storm-roc-glow-v3.png').read_bytes()

def build(flat=False):
    study=json.loads((STUDY/'candidate/storm_roc.geo.json').read_text())['minecraft:geometry'][0]['bones']
    m=sculpt(study)
    from paint import paint
    texture,glow,atlas=paint(m.parts,flat)
    def png(im):buf=io.BytesIO();im.save(buf,format='PNG');return buf.getvalue()
    texture,glow=png(texture),png(glow)
    if not flat:texture,glow=approved_atlas(m)
    refine(m)
    if flat:
        # The shape-only mode must also show additions in the reserved rows.
        from PIL import Image
        from paint import PAL
        im=Image.open(io.BytesIO(texture)).convert('RGBA')
        for p in m.parts:
            if p.get('v3')!='crest':continue
            for uv in p['cube']['uv'].values():
                x,y=uv['uv'];w,h=uv['uv_size']
                im.paste((*PAL['crest'][2],255),(x-1,y-1,x+w+1,y+h+1))
        texture=png(im)
    geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.storm_roc',
        'texture_width':atlas[0],'texture_height':atlas[1],'visible_bounds_width':32,'visible_bounds_height':20,
        'visible_bounds_offset':[0,5,0]},'bones':m.bones}]}
    counts={}
    for p in m.parts:counts[p['bone']]=counts.get(p['bone'],0)+1
    manifest={'pieces':len(m.parts),'atlas':list(atlas),'flat':flat,'pieces_per_bone':counts}
    return {'storm_roc.geo.json':(json.dumps(geo,separators=(',',':'))+'\n').encode(),
        'storm_roc.png':texture,'storm_roc_glowmask.png':glow,
        'manifest.json':(json.dumps(manifest,indent=1)+'\n').encode()}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');p.add_argument('--flat',action='store_true')
    args=p.parse_args();files=build(args.flat);OUT.mkdir(exist_ok=True)
    for name,data in files.items():
        if args.check:assert (OUT/name).read_bytes()==data,f'stale {name}'
        else:(OUT/name).write_bytes(data)
    print(('Checked' if args.check else 'Wrote'),json.loads(files['manifest.json'])['pieces'],'pieces')
if __name__=='__main__':main()
