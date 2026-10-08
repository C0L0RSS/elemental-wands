#!/usr/bin/env python3
"""Deterministic, review-only Storm Roc geometry, pixels, rig and motion exporter."""
import argparse
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = HERE / 'candidate'
PREVIEW = ROOT / '.local-previews/storm-roc'
CONCEPTS = HERE / 'concepts'
DENSITY = 2
SIZE = 1024
PALETTES = {
    'navy': ['142239','213650','2c4865','395b7a','50728b'],
    'slate': ['25323e','354858','4a606f','617b89','8299a0'],
    'white': ['a3afb9','c9d1d2','e2e6df','eff0e5','fff5dc'],
    'rose': ['693447','943f51','b75668','d87785','e8a4a5'],
    'red': ['602837','8a3246','ae4255','cd5965','e57b7a'],
    'skin': ['65272e','92342e','bb4a3a','d86c4b','e79165'],
    'beak': ['823530','ae4534','d36543','e68b57','f3b878'],
    'ivory': ['aa9873','c5b38b','ded0a6','f3e9c7','fffae6'],
    'talon': ['111c29','202e3c','354655','536778','748894'],
    'mouth': ['241c2b','41212f','65313c','97434c','bc6666'],
    'gold': ['b48a40','d4ae59','ecd58d','fff1b8','fffdf0'],
}
PALETTES.update({
'crest_tip':['293f59','395771','4c7391','638ba3','85a8ba'],
'bill_ivory':['b9aa87','d0c29f','e6dcbc','f4ecd1','fff8e5'],
'beak_light':['ac653e','cd8750','e4b16d','f0cf96','fff0c5'],
'iris':['aa6520','d59728','f2c749','ffe479','fff4bc'],
'flight':['9dabb7','c6d0d5','e6e9e5','f5f4e8','fff9e9'],
 'tip':['283d52','3d5268','596d7c','80939e','a7b5bc'],
 'scale':['622d35','853c43','a94d4b','c3675a','da8d70'],
 'scute':['632c32','863b3c','ae4c43','c96952','de926b'],
 'tongue':['652c38','893b47','aa5060','bf6671','da8790'],
 'tail_red':['602837','8a3246','ae4255','cd5965','e57b7a'],'bill_shadow':['b7a27c','cbb991','e0d0a8','efe4c5','fffae5']})
PALETTES = {k:[tuple(bytes.fromhex(c)) for c in v] for k,v in PALETTES.items()}

class Roc:
    def __init__(self):
        self.bones = []
        self.cubes = []
        self.feathers = {}
    def bone(self, name, pivot, parent=None):
        b = {'name':name, 'pivot':list(pivot), 'cubes':[]}
        if parent: b['parent'] = parent
        self.bones.append(b)
        return name
    def cube(self, bone, center, size, material, rotation=None, scar=False, feather=False):
        # All dimensions are half-pixel multiples: UV lengths are exact at 2x.
        assert all(v > 0 and abs(v*4-round(v*4)) < 1e-6 for v in size), size
        c = {'origin':[round(a-b/2,4) for a,b in zip(center,size)], 'size':list(size)}
        if rotation:
            c.update(pivot=list(center), rotation=rotation)
        next(b for b in self.bones if b['name']==bone)['cubes'].append(c)
        density=4 if bone in ('head','upper_beak','lower_beak','crest') or bone.startswith('crest_') or '_cheek_' in bone else 2
        self.cubes.append((c,material,scar,feather,density))
    def feather(self, name, parent, start, end, width, material, scar=False, tip=None):
        self.bone(name,start,parent)
        self.feathers[name]={'start':start,'end':end,'parent':parent,'width':width}
        vec=[end[i]-start[i] for i in range(3)]
        length=math.hypot(*vec)
        yaw=-math.degrees(math.atan2(vec[0],vec[2]))
        pitch=math.degrees(math.atan2(vec[1],math.hypot(vec[0],vec[2])))
        rotation=[pitch,yaw,0]
        if any(key in name for key in ('cheek','ruff','crest_plume','flank','trouser','tail_','neck_mane','mantle')):
            # Roll the vanes toward the side-view camera, rather than showing thin edges.
            d=[v/length for v in vec]; normal=[.72,.69,0] if name.startswith('tail_') else [1,0,0]
            if 'neck_mane' in name or 'mantle' in name:normal=[.65,.76,0]
            if 'trouser' in name:
                parts=name.split('_');a=(int(parts[-1])/8+int(parts[-2])*.065)*math.tau;normal=[math.cos(a),0,math.sin(a)]
            w=[normal[1]*d[2]-normal[2]*d[1],normal[2]*d[0]-normal[0]*d[2],normal[0]*d[1]-normal[1]*d[0]];wl=math.hypot(*w);w=[v/wl for v in w]
            n=[d[1]*w[2]-d[2]*w[1],d[2]*w[0]-d[0]*w[2],d[0]*w[1]-d[1]*w[0]]
            ry=math.asin(-w[2]);rx=math.atan2(n[2],d[2]);rz=math.atan2(w[1],w[0])
            rotation=[-math.degrees(rx),-math.degrees(ry),math.degrees(rz)]
        segments=((0,.55,1),(.51,.85,.65),(.82,1,.2)) if 'crest' in name else ((0,.66,1),(.62,.90,.88),(.87,1,.57))
        if 'trouser' in name:segments=((0,.48,1),(.44,.74,.78),(.70,.91,.43),(.88,1,.12))
        if '_primary_' in name:segments=((0,.48,1),(.44,.70,.92),(.66,.89,.7),(.85,1,.42))
        for i,(a,b,w) in enumerate(segments):
            t=(a+b)/2;center=[round(start[j]+vec[j]*t,4) for j in range(3)]
            mat=tip if tip and i==len(segments)-1 else material
            self.cube(name,center,[max(1,round(width*w*2)/2),1.5 if any(k in name for k in ('crest','cheek','ruff','neck_mane','mantle')) else .5,max(1,round(length*(b-a)*2)/2)],mat,rotation,scar=False,feather=True)

from sculpt import sculpt as make_sculpt

def sculpt():return make_sculpt(Roc)


def paint(r):
    """Reusable per-face islands, exact 2x density, 1px extruded gutters."""
    from head_lock import reserve
    import numpy as np
    texture,glow,occupied,locked=reserve(r,SIZE)
    islands={}
    for number,(cube,mat,scar,feather,density) in enumerate(r.cubes):
        if id(cube) in locked:continue
        w,h,d=[round(v*density) for v in cube['size']]
        cube['uv']={}
        for face,(fw,fh) in {'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}.items():
            # Share repeated feather sides; scar-bearing patches retain distinct branches.
            show_scar=scar if ((scar=='face' and face in ('east','west')) or (scar in ('neck','chest') and face=='north') or (scar=='wing' and face=='up')) else False
            face_mat='white' if feather and mat in ('red','rose') and face=='down'  else mat
            key=(fw,fh,face_mat,str(show_scar),feather,density)
            islands.setdefault(key,[]).append((cube,face))
    max_y=0
    def allocate(w,h):
        for y in range(SIZE-h-1):
            free=~occupied[y:y+h+2].any(axis=0)
            edges=np.diff(np.r_[False,free,False].astype(int))
            starts=np.flatnonzero(edges==1);ends=np.flatnonzero(edges==-1)
            fits=starts[ends-starts>=w+2]
            if len(fits):
                x=int(fits[0]);occupied[y:y+h+2,x:x+w+2]=True
                return x,y
        raise ValueError('Atlas overflow')
    for key,users in sorted(islands.items(),key=lambda kv:(-kv[0][1],-kv[0][0],str(kv[0]))):
        w,h,mat,scar,feather,density=key
        x,y=allocate(w,h)
        px,py=x+1,y+1
        for cube,face in users:cube['uv'][face]={'uv':[px,py],'uv_size':[w,h]}
        pal=PALETTES[mat]
        tile=Image.new('RGBA',(w,h));mask=Image.new('RGBA',(w,h))
        for v in range(h):
            for u in range(w):
                # Diagonal barbs and broad value bands, never per-pixel noise.
                edge=min(u,w-1-u)
                tone=2
                if mat=='white' and not feather:
                    fx=(u+(v//12%2)*4)%8
                    edge_y=9-int(abs(fx-3.5))
                    tone=1 if v%12==edge_y and 0<fx<7 else 2
                elif feather=='shaft':
                    # Longitudinal quill, edge shadow and tiny edge notches; no tile diagonals.
                    tone=2
                    if w>3 and h>4:
                        shaft=w//2
                        if abs(u-shaft)<=1:tone=3
                        elif u==shaft+2:tone=1
                        if edge<density:tone=1
                        if edge<density+1 and v%13 in (5,6):tone=0
                        if v>h-3 and edge<3:tone=1
                elif feather and h>4 and w>3:
                    shaft=w//2
                    tone=3 if abs(u-shaft)<=1 else (2 if (v//4+abs(u-shaft)//3)%5 else 1)
                    if mat in ('red','rose') and ((v+abs(u-shaft))//3)%5==0:tone=3
                    if edge<1 and v%7<3:tone=1
                    if v>h*.8:tone=max(1,tone-1)
                elif mat=='scute':
                    # Curved plate crown; no dark rectangular border or seams.
                    arch=round(1.5*((u-(w-1)/2)/max(1,w/2))**2)
                    tone=3 if v==arch else 2
                    if v==arch+1 and w>5:tone=4
                elif mat=='scale':
                    # Interlocking crescent rows: highlights follow each crown.
                    span=8;row=v//5;sx=(u+(row%2)*4)%span;sy=v%5
                    crown=round(2*((sx-3.5)/4)**2)
                    tone=3 if sy==crown else 2
                    if sy==crown+1 and 2<=sx<=5:tone=4
                else:
                    tone=3 if u<max(1,w//6) else 2
                    if mat=='white':
                        scallop=(u+(v//6%2)*4)%8;edge=4+abs(scallop-4)//2
                        tone=2
                        if (v%10==6+int(abs(((u+(v//10%2)*5)%10)-5)/2)) and (u%10 not in (0,9)):tone=1
                        elif v%10==5:tone=3
                    elif mat in ('bill_ivory','bill_shadow'):
                        tone=3 if v==0 and w>12 else 2
                    elif (u//4+v//6)%9==2:tone=max(1,tone-1)
                col=pal[tone]
                tile.putpixel((u,v),(*col,255))
                if mat=='iris':mask.putpixel((u,v),(*pal[3],255))
        if scar != 'False' and w>=8 and h>=8:
            # Two connected, forked stair-step paths: warm border + bright narrow core.
            path=[(int(w*.3),0),(int(w*.4),int(h*.2)),(int(w*.32),int(h*.4)),(int(w*.56),int(h*.57)),(int(w*.48),int(h*.76)),(int(w*.65),h-1)]
            branch=[path[2],(int(w*.7),int(h*.3)),(int(w*.82),int(h*.08))]
            for target in (tile,mask):
                draw=ImageDraw.Draw(target)
                for p in (path,branch):
                    draw.line(p,fill=(*PALETTES['gold'][2],255),width=3 if density==2 else 5)
                    draw.line(p,fill=(*PALETTES['gold'][4],255),width=1 if density==2 else 2)
        for atlas,tile in ((texture,tile),(glow,mask)):
            atlas.paste(tile,(px,py))
            for v in range(-1,h+1):
                for u in (-1,w):atlas.putpixel((px+u,py+v),tile.getpixel((max(0,min(w-1,u)),max(0,min(h-1,v)))))
            for u in range(w):
                for v in (-1,h):atlas.putpixel((px+u,py+v),tile.getpixel((u,max(0,min(h-1,v)))))
        max_y=max(max_y,y+h+2)
    return texture,glow,{'body_islands':len(islands),'used_height':max(max_y,int(np.nonzero(occupied)[0].max())+1)}


def animations(r):
    from poses import animations as build_animations
    return build_animations(r)


def json_bytes(obj):return (json.dumps(obj,indent=2)+'\n').encode()
def png_bytes(im):
    buffer=io.BytesIO();im.save(buffer,format='PNG');return buffer.getvalue()


def build(check=False):
    r=sculpt();texture,glow,packing=paint(r);clips=animations(r)
    from sparks import build_sparks
    sparks,spark_meta=build_sparks(r)
    from head_lock import verify, verify_sparks, SOURCE
    head_status=verify(r,texture,glow)
    verify_sparks(r,spark_meta)
    assert png_bytes(sparks)==(SOURCE/'storm_roc_sparks.png').read_bytes()
    geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.storm_roc','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':32,'visible_bounds_height':20,'visible_bounds_offset':[0,5,0]},'bones':r.bones}]}
    anim={'format_version':'1.8.0','animations':{f'animation.storm_roc.{k}':v for k,v in clips.items()}}
    stats={'bones':len(r.bones),'cubes':len(r.cubes),'texels_per_model_pixel':{'head_beak_crest_cheeks':4,'body_wings_tail_legs':2},'texture_size':[SIZE,SIZE],'approved_head':head_status,'body_bones':len(r.bones)-head_status['bones'],'body_cubes':len(r.cubes)-head_status['cubes'],**packing}
    assert stats['body_cubes']<=700 and stats['body_bones']<=200 and len(r.cubes)<=1100,stats
    outputs={'storm_roc.geo.json':json_bytes(geo),'storm_roc.animation.json':json_bytes(anim),'storm_roc.png':png_bytes(texture),'storm_roc_glowmask.png':png_bytes(glow),'manifest.json':json_bytes(stats),'storm_roc_sparks.png':png_bytes(sparks),'storm_roc_sparks.json':json_bytes(spark_meta)}
    payload={'face_concept':'data:image/png;base64,'+base64.b64encode((CONCEPTS/'face-and-feathers.png').read_bytes()).decode(),'sparks':'data:image/png;base64,'+base64.b64encode(outputs['storm_roc_sparks.png']).decode(),'spark_metadata':spark_meta,'concept':'data:image/png;base64,'+base64.b64encode((CONCEPTS/'body-turnaround.png').read_bytes()).decode(),'geo':geo,'clips':clips,'stats':stats,'texture':'data:image/png;base64,'+base64.b64encode(outputs['storm_roc.png']).decode(),'glow':'data:image/png;base64,'+base64.b64encode(outputs['storm_roc_glowmask.png']).decode()}
    page=(HERE/'viewer.template.html').read_text().replace('<!-- MODEL_DATA -->','<script id="model-data" type="application/json">'+json.dumps(payload).replace('</','<\\/')+'</script>').replace('<!-- VIEWER -->','<script type="module">'+(HERE/'viewer.js').read_text()+'</script>')
    mismatches=[]
    for name,data in outputs.items():
        dest=OUT/name
        if check:
            if not dest.exists() or dest.read_bytes()!=data:mismatches.append(str(dest.relative_to(ROOT)))
        else:dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    if check:
        if not (PREVIEW/'index.html').exists() or (PREVIEW/'index.html').read_text()!=page:mismatches.append(str((PREVIEW/'index.html').relative_to(ROOT)))
        if mismatches:raise SystemExit('Stale or missing outputs:\n'+'\n'.join(mismatches))
        print('Candidate and review page match a fresh build.')
    else:
        PREVIEW.mkdir(parents=True,exist_ok=True);(PREVIEW/'index.html').write_text(page)
        # The viewer links the concept sheets relative to the page.
        (PREVIEW/'concepts').mkdir(exist_ok=True)
        for sheet in CONCEPTS.glob('*.png'):(PREVIEW/'concepts'/sheet.name).write_bytes(sheet.read_bytes())
    print(json.dumps(stats));return stats

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');build(p.parse_args().check)
