#!/usr/bin/env python3
"""Build review-only GeckoLib art and local workshop. --check compares without writing.
Requires Pillow and NumPy; never writes runtime assets or installs a mod.
"""
import argparse
import io
import json
import math
import subprocess
from pathlib import Path
from PIL import Image
from geometry import BONES, PARTS, sword_bones, BLADE_SOCKETS, BLADE_HALF_EXTENTS, P, GUARD_DIRECTION

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / '.local-previews/pyre-king'
ATLAS_W = 1024
PALETTES = {
    'armor': ['17191b','232529','303236','3b3d40','494a4b'],
    'coat': ['151719','222428','303236','414044','505052'],
    'gauntlet':['222326','353639','494a4c','5b5c5b'],
    'char':['111213','202122','2d2e2f','393a38'],
    'ash':['3c3d3a','62655f','8d9089','b3b4aa','d2d0c4'],
    'leather':['1c1614','302320','46302a','654036'],
    'trim':['303433','655b38','aa8c45','dbc274','fff0b0'],
    'gold':['292c29','6b5d35','b1944b','e6b94e','ffedaa'],
    'mask':['272321','57402a','a37a3e','c79b52','e0b66b'],
    'iron':['272a2d','42484c','60686c','87908c'],
    'dark_iron':['131517','1f2225','2e3236','42474b'],
    'lock_iron':['1e1a14','3a3122','5a4b2e','7d6a3d'],
    'curse':['22171a','3b2724','5a3a30','7a5040','94604a'],
    'tabard':['1d1215','33171b','4f1f24','6e2a2d','8a3a37'],
    'molten':['5c3410','8a5418','b07a26','d49a38','f0bd5a'],
    'crimson':['21171a','3b2024','5c292d','803436','9b4844'],
    'burnt_cloth':['191718','2f2022','53292c','793631'],
    'ruby':['260f13','54151c','8b2028','b6383a','dc6250'],
    'steel':['5d6463','7e8985','a1aca1','c8cec0','e2dfc8'],
    'fuller':['414b4c','66736f','86948b'],
    'grip':['2e191a','4b2526','6a3531','915147'],
    'plate_edge':['131518','24272b','373a3d'], 'ember':['632511','a94412','d46c1c'],
    'void':['111214'], 'eye':['b8400e','f06a12','ff9a28','ffc457'],
    'shard':['c7360b','f2660d','ffb72e','ffe780'],
    'hot':['fff1aa'], 'lock':['813815','b95911','dd7f1d','f2ad3c'],
    'fire':['df3515','fb6518','ff9622','ffcf3e','fff09b'],
}
PALETTES['joint']=PALETTES['armor']
PALETTES['hem']=PALETTES['trim']
PALETTES['fire_mid']=PALETTES['fire']
PALETTES['fire_tip']=PALETTES['fire']
PALETTES={k:[tuple(bytes.fromhex(c)) for c in v] for k,v in PALETTES.items()}


def hashed(x,y,seed):
    v=(x*73856093 ^ y*19349663 ^ seed*83492791)&0xffffffff
    v=((v^(v>>16))*0x45d9f3b)&0xffffffff
    v=((v^(v>>16))*0x45d9f3b)&0xffffffff
    return ((v^(v>>16))&0xffff)/65535


def paint(w,h,mat,seed,face):
    im=Image.new('RGBA',(w,h)); glow=Image.new('RGBA',(w,h)); p=im.load(); g=glow.load()
    pal=PALETTES[mat]
    for y in range(h):
        for x in range(w):
            q=hashed(x//2,y//2,seed)
            v=2+(1 if q>.82 else -1 if q<.17 else 0)
            alpha=255; emit=False
            if mat in ('armor','coat','gauntlet','joint'):
                # Quiet broad plates: grain is low contrast and no random emissive dashes.
                v=2+(1 if q>.94 else -1 if q<.08 else 0)
                if x==0 or y==0:v=3
                if x==w-1 or y==h-1:v=0
                if (x,y) in ((2,3),(w-3,3)):v=3
                if hashed(x//7,y//9,seed)<.08:v=max(0,v-1)
                # Joint heat stays on one short seam, never through the plate field.
                if mat=='joint' and face in ('north','south') and y==h-3 and w*.3<x<w*.55:
                    p[x,y]=(142,54,18,255);g[x,y]=(154,57,14,145);continue
            elif mat in ('trim','gold','iron','curse','hem','dark_iron','lock_iron'):
                # A bevel and diagonal glint across irregular metal facets; no grain bands.
                v=2 if mat in ('iron','dark_iron','lock_iron') else 3
                if x==0 or y==0:v=4
                if x==w-1 or y==h-1:v=1
                glint=int(w*.28+y*.32)
                if abs(x-glint)<=max(0,w//16):v=4
                tarnish=hashed(x//3,y//4,seed)
                if tarnish<.10:v=1
                if tarnish<.045:v=0
                if mat=='curse':
                    v=2 if v>2 else v
                    if y==0 and (x+seed)%3==0:
                        color=(176,64,26)
                        p[x,y]=(*color,255);g[x,y]=(*color,90);continue
                if mat=='hem' and y>=h-2 and (x+seed)%7<4:
                    color=(196,91,21) if (x+seed)%7<2 else (144,47,13)
                    p[x,y]=(*color,255);g[x,y]=(*color,150);continue
            elif mat=='mask':
                v=3 if x<w*.7 else 2
                if hashed(x//4,y//5,seed)<.20: v=1
                # A single jagged crack per plate, with restrained molten pinholes.
                crack=(seed%max(1,w)+y//4+(y//9))%w if w>=20 else -9
                if x==crack and y>h*.2:
                    v=0
                    if y%9==6: p[x,y]=(224,86,17,255);g[x,y]=(162,43,6,180);continue
                if x==crack+1 and y%4==0: v=1
                if y==0: v=min(4,v+1)
            elif mat=='ash':
                # Hair grain: one-texel strands run down the lock, shadowed at the root and
                # pale toward the tip, with a dark parting every few strands.
                strand=hashed(x,0,seed+7)
                v=2+(1 if strand>.6 else 0)-(1 if strand<.2 else 0)
                if x%4==3 and w>5:v=1
                if y<h*.22:v-=1
                if y>h*.62:v+=1
                if hashed(x,y//4,seed)<.07:v-=1
                v=max(0,min(4,v))
                if hashed(x,y,seed)>.998:
                    p[x,y]=(166,85,32,255);g[x,y]=(120,42,10,110);continue
            elif mat in ('crimson','burnt_cloth'):
                q2=hashed(x//4,y//6,seed+3)
                v=2+(1 if q2>.86 else -1 if q2<.12 else 0)
                if y>h-2-(x//2*7+seed)%5 or hashed(x//3,y//3,seed)<(.04 if y<h*.8 else .2): v=0
                if mat=='burnt_cloth' and y>h-3 and (x+seed)%4==0:
                    p[x,y]=(176,59,17,255); g[x,y]=(100,24,4,130);continue
            elif mat=='tabard':
                v=2+(1 if (x//2+y//3+seed)%5==0 else 0)
                if hashed(x//2,y//2,seed)<.12:v=1
                if face in ('north','south'):
                    if x in (1,w-2) and 1<y<h-2: p[x,y]=(*PALETTES['gold'][2],255);continue
                    cx=w/2-.5; top=int(h*.12)+4
                    rel=y-top; dx=abs(x-cx)
                    crown=(0<=rel<=2 and dx<=4.5) or (-3<=rel<0 and (dx<=.6 or 3.4<=dx<=4.6)) or (rel==-4 and dx<=.6) or (-2<=rel<0 and 1.4<=dx<=2.6 and rel==-1)
                    if crown: p[x,y]=(*PALETTES['gold'][3 if rel!=2 else 2],255);continue
                    if 4<=rel<=5 and dx<=1.1: p[x,y]=(255,150,40,255);g[x,y]=(255,120,20,200);continue
            elif mat=='molten':
                v=2 if y<h-2 else 4
                if y>=h-2: p[x,y]=(*pal[4],255);g[x,y]=(*pal[4],150);continue
            elif mat=='steel':
                edge=min(x,w-1-x)
                v=4 if edge<2 else 2 if edge<4 else 1
                if y%17==5 and x%5<2:v=min(4,v+1)
                if face in ('east','west') or edge<1:
                    if hashed(0,y//5,seed+11)>.38:
                        p[x,y]=(255,178,62,255);g[x,y]=(255,140,30,215)
                    else:
                        p[x,y]=(120,46,24,255);g[x,y]=(150,40,10,70)
                    continue
                if edge<2:
                    p[x,y]=(176,78,34,255);g[x,y]=(190,60,14,110);continue
            elif mat=='grip': v=1 if (x+y)%5<2 else 3
            elif mat=='ruby': v=4 if x<2 and y<3 else 3 if y<h*.4 else 1
            elif mat.startswith('fire'):
                # Broad tapering tongues with transparent caps and a clear blade between edges.
                stage={'fire':0,'fire_mid':1,'fire_tip':2}[mat]
                if face in ('up','down'):alpha=0;v=0
                else:
                    rise=(h-1-y)/max(1,h-1)
                    mid=(w-1)*(.5+.13*rise)
                    radius=max(.45,w*.5*(1-rise*.86))
                    edge=abs(x-mid)/radius
                    if edge>1 or (rise>.88 and abs(x-mid)>.6):alpha=0;v=0
                    else:
                        heat=(1-rise)*.8+(1-edge)*.2-stage*.13
                        v=min(4,max(0,int(heat*5)))
                        alpha=int((225-stage*40)*(1-rise*.45))
                        if edge>.7:alpha=int(alpha*.65)
                emit=True
            elif mat in ('eye','shard','hot','lock','ember'):
                v=min(len(pal)-1,1+int((1-abs(x-w/2)/max(w/2,1))*(len(pal)-2)))
                emit=True
            color=pal[max(0,min(len(pal)-1,v))]
            p[x,y]=(*color,alpha)
            if emit:g[x,y]=(*color,int(alpha*{'lock':.9,'ember':.6}.get(mat,1)))
    return im,glow


def blade_fire_texture():
    """Pixel flames using only the existing block-fire atlas's exact colors.

    Frames 0-3 are tall tongues with opaque yellow-white roots, orange bodies and red
    tips. Frames 4-7 are low licks: broad, orange-bodied and red-tipped, with only a
    small yellow heart and no white core, so a row of them reads as one fire.
    Only the one-pixel perimeter and terminal tip are translucent.
    """
    source=Image.open(HERE.parent/'fire/blockfire/textures/blockfire.png').convert('RGBA')
    result=Image.new('RGBA',(128,32))
    # Every flame's root is rounded, not flat, so a row of them along a slanted blade
    # cannot stack into horizontal bands.
    def rounded(rise):return math.sqrt(min(1,(rise+.05)/.22))
    for frame in range(4):
        for y in range(32):
            rise=(31-y)/31
            center=7.5+round(2*rise*math.sin(rise*5+frame*math.pi/2))
            radius=max(.5,7*(1-rise)**.65*rounded(rise))
            for x in range(16):
                edge=abs(x-center)/radius
                if edge>1:continue
                # A hot core broadens into the blade; its shoulder is yellow,
                # then orange/red. Two-pixel contour steps keep a crisp flame.
                if rise<.32 and edge<.52*(1-rise):row=0
                elif rise<.55 and edge<.72:row=1
                elif rise<.82:row=2
                else:row=3
                alpha=255
                if edge>.83:alpha=175
                if rise>.93:alpha=min(alpha,135)
                color=source.getpixel((frame*16+x,row*16+y%16))[:3]
                result.putpixel((frame*16+x,y),(*color,alpha))
    for frame in range(4):
        for y in range(32):
            rise=(31-y)/31
            center=7.5+1.6*rise*math.sin(rise*4+frame*math.pi/2)
            radius=max(.5,7.6*(1-rise)**.5*rounded(rise))
            for x in range(16):
                edge=abs(x-center)/radius
                if edge>1:continue
                if rise<.38 and edge<.30:row=1
                elif rise<.74:row=2
                else:row=3
                alpha=255
                if edge>.8:alpha=170
                if rise>.9:alpha=min(alpha,130)
                color=source.getpixel((frame*16+x,row*16+y%16))[:3]
                result.putpixel((64+frame*16+x,y),(*color,alpha))
    assert any(p[3]==255 and min(p[:3])>170 for p in list(result.get_flattened_data()))
    return result


def build():
    faces=[]
    for seed,(c,mat,b) in enumerate(PARTS):
        w,h,d=c['size'];c['uv']={}
        for face,a,z in [('north',w,h),('south',w,h),('east',d,h),('west',d,h),('up',w,d),('down',w,d)]:
            faces.append((c,mat,seed,face,max(1,math.ceil(a*2)),max(1,math.ceil(z*2))))
    x=y=row=0
    for c,mat,seed,face,w,h in sorted(faces,key=lambda f:-f[-1]):
        if x+w+2>ATLAS_W:x=0;y+=row;row=0
        c['uv'][face]={'uv':[x+1,y+1],'uv_size':[w,h]}
        x+=w+2;row=max(row,h+2)
    height=2**math.ceil(math.log2(y+row))
    atlas=Image.new('RGBA',(ATLAS_W,height)); glow=Image.new('RGBA',atlas.size)
    for c,mat,seed,face,w,h in faces:
        im,gm=paint(w,h,mat,seed,face); pos=tuple(c['uv'][face]['uv']);atlas.paste(im,pos);glow.paste(gm,pos)
    def geo(bones,identifier):return {'format_version':'1.12.0','minecraft:geometry':[{'description':{
        'identifier':'geometry.'+identifier,'texture_width':ATLAS_W,'texture_height':height,
        'visible_bounds_width':9,'visible_bounds_height':9,'visible_bounds_offset':[0,3,0]},'bones':bones}]}
    model=geo(BONES,'pyre_king'); sword=geo(sword_bones(),'pyre_king_sword')
    def packed(o):return (json.dumps(o,indent=2)+'\n').encode()
    def png(im):b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
    # The exported guard stays static; effects.js owns the workshop-only sweep exercise.
    from rig import bake
    from clips import CLIPS
    anim={'format_version':'1.8.0','animations':{'animation.pyre_king.guard':{'loop':True,'animation_length':1,'bones':{}}}}
    for name,(fn,length,loop) in CLIPS.items():
        anim['animations']['animation.pyre_king.'+name]=bake(fn,length,loop)
    assets={
        'pyre_king.geo.json':packed(model),'pyre_king_sword.geo.json':packed(sword),
        'pyre_king.png':png(atlas),'pyre_king_glowmask.png':png(glow),'pyre_king.animation.json':packed(anim),
    }
    manifest={'revision':'Tenth sculpt / v10 · polish · first clips','date':'2026-11-05','summary':'Review only · 5-block masked duelist · 4.5-block longsword · plough guard',
        'cubes':len(PARTS),'bones':len(BONES),'sword_cubes':sum(len(b['cubes']) for b in sword_bones()),
        'sword_bones':len(sword_bones()),'texture_size':list(atlas.size),
        'translucent_bones':[], 'material_by_cube':[m for c,m,b in PARTS],
        'measurement_targets':{'body_height':80,'sword_length':72,'blade_length':56,'blade_width':7,'grip_length':11.6,'player_height':28.8,'player_eye':25.6},
        'sword_root_pivot':[0,0,0], 'sword_tip_axis':'+Y', 'source_front':'-Z', 'guard_direction':GUARD_DIRECTION,
        'blade_fire':{'bone':'sword','units':'model pixels','socket_space':'relative to sword pivot before bone rotation; reflect X and divide by 16 before applying baked bone matrix',
            'king_pivot':list(P),'sword_pivot':[0,0,0],'sockets':BLADE_SOCKETS,
            'socket_half_extents':BLADE_HALF_EXTENTS,'root_edge_fraction':1,
            'root':'each flame roots root_edge_fraction of the way from its socket toward the uppermost edge of the blade cross-section, on the top face (half_extents: local X half width, local Z half thickness, model pixels)',
            'world_up':[0,1,0],'atlas':'bladefire.png','atlas_grid':[8,1],'tongue_frames':[0,1,2,3],'lick_frames':[4,5,6,7],'atlas_fps':8,
            'active_socket_indices':list(range(17)), 'peak_sockets':[[2,.74],[4,.98],[10,1.18],[11,.8],[15,1.06]],
            'palette_source':'art/fire/blockfire/textures/blockfire.png',
            'trail_lifetime_seconds':.18,'trail_speed_threshold_blocks_per_second':1.2},
        'clips':{name:{'length':length,'loop':loop} for name,(fn,length,loop) in CLIPS.items()},
        'preview_rendering':'Shared effects.js samples posed sockets: a low overlapping flame roots on the blade\'s upper edge at every socket, making one continuous fringe, and five taller peaks break the silhouette. All upright in world space. No flame geometry in either sword export.'}
    # Measure the transformed corners with the same GeckoLib baker as the workshop.
    def baked(data):
        return json.loads(subprocess.check_output(['node',str(HERE/'export_scene.cjs'),'-'],input=packed(data)))
    body_scene, sword_scene = baked(model), baked(sword)
    body_y=[p[1] for f in body_scene['faces'] if f['bone'] not in ('sword','sword_fire','pommel_ring') for p in f['pts']]
    sword_y=[p[1] for f in sword_scene['faces'] if f['bone']!='sword_fire' for p in f['pts']]
    manifest['measured_body_height_blocks']=round(max(body_y)-min(body_y),6)
    manifest['measured_held_silhouette_height_blocks']=round(max(p[1] for f in body_scene['faces'] for p in f['pts'])-min(body_y),6)
    manifest['measured_sword_length_blocks']=round(max(sword_y)-min(sword_y),6)
    # Upright he is exactly 80 px; the guard's lean and lowered head cost a few hundredths.
    assert abs(manifest['measured_body_height_blocks']-5)<.1, manifest['measured_body_height_blocks']
    assert manifest['measured_sword_length_blocks']==4.5
    assert len(next(b for b in BONES if b['name']=='sword_fire')['cubes'])==0
    assert sum(b['name'].startswith('curse_chain_') for b in BONES)==1
    assert all(n in [b['name'] for b in BONES] for n in ['death_mask','crown','ash_beard','chest_shard','right_wrist_shackle','hanging_wrist_chain','tabard','mantle_collar'])
    assert sum(b['name'].startswith('coat_flap_') for b in BONES)==6
    assert sum(b['name'].startswith('mantle_scrap_') for b in BONES)==9
    assets['bladefire.png']=png(blade_fire_texture())
    assets['blockfire.png']=(HERE.parent/'fire/blockfire/textures/blockfire.png').read_bytes()
    subprocess.run(['node',str(HERE/'check_effects.cjs')],input=packed({'king':model,'sword':sword,'manifest':manifest}),check=True)
    assets['manifest.json']=packed(manifest)
    # Validate hierarchy, UVs and the requested structural contract before writing anything.
    names=[b['name'] for b in BONES];assert len(names)==len(set(names))
    assert 200<=len(PARTS)<=450, len(PARTS)
    for b in BONES:
        assert not b.get('parent') or names.index(b['parent'])<names.index(b['name'])
        for c in b['cubes']:
            assert all(v>0 for v in c['size'])
            for f in c['uv'].values():assert all(0<=a and a+s<=lim for a,s,lim in zip(f['uv'],f['uv_size'],atlas.size))
    assert next(b for b in BONES if b['name']=='sword')['parent']=='right_hand'
    files={HERE/'candidate'/n:v for n,v in assets.items()}
    for p in (HERE/'workshop').iterdir():
        if p.suffix in ('.js','.css') or p.name=='viewer.template.html':
            files[OUT/'workshop'/('index.html' if p.name=='viewer.template.html' else p.name)]=p.read_bytes()
    files.update({OUT/'workshop/candidate'/n:v for n,v in assets.items()})
    return files,manifest


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
    files,manifest=build();bad=[]
    for path,data in files.items():
        if args.check:
            if not path.exists() or path.read_bytes()!=data:bad.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if bad:raise SystemExit('Stale or missing outputs:\n'+'\n'.join(bad))
    print(('CHECK OK' if args.check else 'BUILT')+f": {manifest['cubes']} cubes / {manifest['bones']} bones; sword {manifest['sword_cubes']} cubes / {manifest['sword_bones']} bones; atlas {manifest['texture_size']}")
if __name__=='__main__':main()
