#!/usr/bin/env python3
"""Offline textured triangle renderer, adapted from storm_roc/render_review.py.
Uses the workshop's GeckoLib 5 baker in Node, then NumPy rasterization. No browser.
Transparent flame faces sort back to front, depth-test against opaque geometry,
and do not write depth; faces with blend 'add' are drawn after the rest, additively,
and per-vertex 'alphas' interpolate like the UVs. Perspective-correct
nearest-neighbor UV sampling.
"""
import argparse
import json
import math
import subprocess
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
OUT=ROOT/'.local-previews/pyre-king/renders'


def baked(name,*options):
    return json.loads(subprocess.check_output(['node',str(HERE/'export_scene.cjs'),str(HERE/'candidate'/name),*options]))


def normalize(v):return v/max(np.linalg.norm(v),1e-10)


def camera(eye,at,aspect,fov=None,extent=6):
    eye=np.array(eye);z=normalize(eye-at);x=normalize(np.cross([0,1,0],z));y=np.cross(z,x)
    view=np.eye(4);view[:3,:3]=[x,y,z];view[:3,3]=-view[:3,:3]@eye
    if fov:
        f=1/math.tan(math.radians(fov/2));n=.05;far=150
        p=np.array([[f/aspect,0,0,0],[0,f,0,0],[0,0,(far+n)/(n-far),2*far*n/(n-far)],[0,0,-1,0]])
    else:
        p=np.diag([2/(extent*aspect),2/extent,-2/150,1]);p[2,3]=-1
    return p@view


def solid_box(o,s,col):
    x,y,z=o;X,Y,Z=np.array(o)+s
    quads=[([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z]],[0,0,-1]),([[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z]],[0,0,1]),
           ([[x,y,Z],[x,y,z],[x,Y,z],[x,Y,Z]],[-1,0,0]),([[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z]],[1,0,0]),
           ([[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],[0,1,0]),([[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],[0,-1,0])]
    return [dict(pts=q,n=n,uvs=[[0,0]]*4,bone='scale_player',color=col) for q,n in quads]


def player():
    x=-2.35;skin=[161,115,82];shirt=[31,123,130];pants=[56,64,100];out=[]
    for o,s,c in [([x-.225,1.35,-.225],[.45,.45,.45],skin),([x-.225,.675,-.1125],[.45,.675,.225],shirt),
        ([x-.45,.675,-.1125],[.225,.675,.225],skin),([x+.225,.675,-.1125],[.225,.675,.225],skin),
        ([x-.225,0,-.1125],[.225,.675,.225],pants),([x,0,-.1125],[.225,.675,.225],pants)]:out+=solid_box(o,s,c)
    # Painted dark hair cap and two eyes keep the scale figure legible as a player.
    out+=solid_box([x-.226,1.69,-.226],[.452,.111,.452],[58,41,29])
    for dx in [-.13,.07]:out+=solid_box([x+dx,1.57,-.228],[.07,.055,.003],[196,205,215])
    return out


def font(size):
    for f in ['/System/Library/Fonts/Supplemental/Arial.ttf','/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']:
        if Path(f).exists():return ImageFont.truetype(f,size)
    return ImageFont.load_default()


def render(faces,texture,glow,eye,at,w,h,extent=6,fov=None,title='',subtitle='',scale=False,fire=True,fire_texture=None,revision='ELEVENTH'):
    vp=camera(eye,np.array(at),w/h,fov,extent);eye=np.array(eye)
    bg=Image.new('RGB',(w,h),(175,176,171));draw=ImageDraw.Draw(bg)
    def project(p):
        v=vp@np.r_[p,1]
        if v[3]<=0:return None
        return ((v[0]/v[3]+1)*w/2,(1-v[1]/v[3])*h/2)
    # One-block ground grid, rendered before the subject.
    for n in range(-12,13):
        for a,b in [([n,0,-8],[n,0,12]),([-12,0,n],[12,0,n])]:
            p,q=project(a),project(b)
            if p and q:draw.line([p,q],fill=(151,154,150),width=1)
    pixels=np.array(bg,dtype=float);depth=np.full((h,w),np.inf)
    fs=[f for f in faces if fire or f['bone'] not in ('sword_fire','world_fire')]+(player() if scale else [])
    # Opaque faces first, then normal flames back to front, then additive flames (blend 'add')
    # back to front, as the workshop draws them.
    fs.sort(key=lambda f:(f['bone'] in ('sword_fire','world_fire'),f.get('blend')=='add',-np.mean(np.sum((np.array(f['pts'])-eye)**2,axis=1)) if f['bone'] in ('sword_fire','world_fire') else 0))
    for face in fs:
        pts=np.array(face['pts']);clip=np.c_[pts,np.ones(4)]@vp.T;ww=clip[:,3];ndc=clip[:,:3]/ww[:,None]
        screen=np.c_[(ndc[:,0]+1)*w/2,(1-ndc[:,1])*h/2];uvs=np.array(face['uvs'])
        n=np.array(face['n']);n*=1 if n@(eye-pts.mean(axis=0))>=0 else -1
        shade=min(1,.48+.40*max(0,n@normalize(np.array([-.5,1,-.8])))+.12*max(0,n@normalize(np.array([1,.2,.5]))))
        transparent=face['bone'] in ('sword_fire','world_fire')
        for ids in ([0,1,2],[0,2,3]):
            p=screen[ids]
            if np.any(ww[ids]<=0):continue
            lo=np.maximum(np.floor(p.min(axis=0)).astype(int),[0,0]);hi=np.minimum(np.ceil(p.max(axis=0)).astype(int),[w-1,h-1])
            if np.any(lo>hi):continue
            x0,y0=p[0];x1,y1=p[1];x2,y2=p[2];den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
            if abs(den)<1e-8:continue
            yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];xx=xx+.5;yy=yy+.5
            aa=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den;bb=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den;cc=1-aa-bb
            zz=aa*ndc[ids[0],2]+bb*ndc[ids[1],2]+cc*ndc[ids[2],2]
            dz=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            inside=(aa>=0)&(bb>=0)&(cc>=0)&(zz<=dz+1e-8)
            if not inside.any():continue
            patch=pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            if 'color' in face:
                patch[inside]=np.array(face['color'])*shade;dz[inside]=zz[inside];continue
            coeff=np.stack([aa/ww[ids[0]],bb/ww[ids[1]],cc/ww[ids[2]]],axis=-1);coeff/=coeff.sum(axis=-1,keepdims=True)
            uv=coeff@uvs[ids];effect=face['bone']=='world_fire';skin=fire_texture if effect else texture;th,tw=skin.shape[:2]
            tx=np.clip((uv[:,:,0]*tw).astype(int),0,tw-1);ty=np.clip((uv[:,:,1]*th).astype(int),0,th-1)
            tex=skin[ty,tx]/255;g=tex if effect else glow[ty,tx]/255
            # Per-vertex opacity ('alphas') interpolates perspective-correctly like the UVs and
            # replaces the face's 'alpha' when present, as the workshop's opacity attribute does.
            fade=coeff@np.array(face['alphas'],dtype=float)[ids] if 'alphas' in face else face.get('alpha',1)
            alpha=tex[:,:,3]*fade
            inside&=(tex[:,:,3]>=.02)&(alpha>1e-4)
            rgb=tex[:,:,:3]*shade;rgb=rgb*(1-g[:,:,3,None])+np.maximum(rgb,g[:,:,:3])*g[:,:,3,None]
            if effect:rgb=tex[:,:,:3]
            # Additive flames (SRC_ALPHA, ONE) glow onto what is behind them; others blend over it.
            if transparent and face.get('blend')=='add':patch[inside]=np.minimum(255,patch+rgb*255*alpha[:,:,None])[inside]
            elif transparent:patch[inside]=(rgb*255*alpha[:,:,None]+patch*(1-alpha[:,:,None]))[inside]
            else:patch[inside]=rgb[inside]*255;dz[inside]=zz[inside]
    im=Image.fromarray(np.clip(pixels,0,255).astype('uint8'));d=ImageDraw.Draw(im)
    d.rectangle((0,0,w,88),fill=(30,32,33));d.text((26,19),title,fill=(233,215,179),font=font(25));d.text((27,54),subtitle,fill=(180,181,174),font=font(13))
    d.text((26,h-30),f'PYRE KING / {revision} SCULPT     •     OFFLINE GECKOLIB REVIEW',fill=(52,55,54),font=font(12))
    return im


def measurements(king,sword):
    body=np.array([p for f in king['faces'] if f['bone'] not in ('sword','sword_fire','pommel_ring') for p in f['pts']])
    weapon=np.array([p for f in sword['faces'] if f['bone'] not in ('sword_fire','world_fire') for p in f['pts']])
    full=np.array([p for f in king['faces'] for p in f['pts']])
    return dict(body_height_blocks=round(float(np.ptp(body[:,1])),6),body_bounds=[body.min(axis=0).tolist(),body.max(axis=0).tolist()],
                held_silhouette_height_blocks=round(float(np.ptp(full[:,1])),6),sword_length_blocks=round(float(np.ptp(weapon[:,1])),6),
                sword_blade_length_blocks=3.5,cubes=king['cubes'],bones=len(king['bones']),sword_cubes=sword['cubes'],sword_bones=len(sword['bones']))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--only',nargs='*');p.add_argument('--width',type=int,default=1200);args=p.parse_args()
    king=baked('pyre_king.geo.json','--effects');sword=baked('pyre_king_sword.geo.json','--effects');OUT.mkdir(parents=True,exist_ok=True)
    texture=np.array(Image.open(HERE/'candidate/pyre_king.png'));glow=np.array(Image.open(HERE/'candidate/pyre_king_glowmask.png'))
    fire_texture=np.array(Image.open(HERE/'candidate/bladefire.png'))
    metrics=measurements(king,sword);(OUT.parent/'measurements.json').write_text(json.dumps(metrics,indent=2)+'\n')
    assert abs(metrics['body_height_blocks']-5)<.1,metrics
    assert abs(metrics['sword_length_blocks']-4.5)<1e-5,metrics
    def orbit(yaw,pitch,at):
        y,p=map(math.radians,(yaw,pitch));return np.array(at)+[math.sin(y)*math.cos(p)*18,math.sin(p)*18,-math.cos(y)*math.cos(p)*18]
    cases=[]
    for name,yaw,pitch in [('front',0,0),('three-quarter',32,12),('side',90,4),('back',180,8)]:
        at=[.9,3.15,0];cases.append(dict(name=name,faces=king['faces']+king['flames'],eye=orbit(yaw,pitch,at),at=at,extent=7.8,scale=True,title=name.replace('-',' ').title(),subtitle='King 5.00 blocks · player 1.80 blocks · two-hand guard'))
    at=[0,4.22,-.15];cases.append(dict(name='head-close-up',faces=king['faces']+king['flames'],eye=orbit(16,3,at),at=at,extent=2.22,title='The scorched death mask',subtitle='Separate mask / crown / ash beard · ember eyes · tarnished and fractured gold'))
    at=[.42,2.3,-.98];cases.append(dict(name='grip-close-up',faces=king['faces']+king['flames'],eye=orbit(26,10,at),at=at,extent=2.5,title='The grip',subtitle='Both fists on the grip · wrist shackle · short tether to the pommel ring'))
    at=[0,2.6,0];cases.append(dict(name='sword-alone',faces=sword['faces']+sword['flames'],eye=orbit(12,6,at),at=at,extent=6.4,title='Longsword / standalone export',subtitle='4.50 blocks overall · 3.50-block broad blade · long grip / round pommel / tether ring'))
    planted=baked('pyre_king_sword.geo.json','--effects','--planted')
    cases.append(dict(name='sword-planted',faces=planted['faces']+planted['flames'],eye=orbit(12,6,at),at=at,extent=6.4,title='Longsword / planted fire check',subtitle='World-up fire also stays upright when the sword points down'))
    for d in (8,16):cases.append(dict(name=f'player-{d}-blocks',faces=king['faces']+king['flames'],eye=[0,1.6,-d],at=[0,3.3,0],fov=50,scale=True,title=f'Player view / {d} blocks',subtitle='Eye 1.60 blocks · look-at height 3.30 · vertical FOV 50° · matched workshop camera'))
    sweep=baked('pyre_king.geo.json','--effects','--sweep')
    at=[1.6,2.8,0]
    cases.append(dict(name='horizontal-sweep',faces=sweep['faces']+sweep['flames'],eye=orbit(20,12,at),at=at,extent=8.4,scale=True,title='Horizontal sweep / frozen at 1.80 s',subtitle='Blade level · every flame rises in world Y · flames settle upright as the blade stops'))
    # Detail cameras frame only the flame-bearing blade with room above the tips.
    for name,scene_data,yaw,pitch in [('high-guard',king,18,7),('horizontal-sweep',sweep,20,12)]:
        pts=np.array([p for f in scene_data['flames'] for p in f['pts']])
        at=(pts.min(axis=0)+pts.max(axis=0))/2
        cases.append(dict(name='fire-close-up-'+name,faces=scene_data['faces']+scene_data['flames'],
            eye=orbit(yaw,pitch,at),at=at,extent=4.6,title='Fire detail / '+name.replace('-',' '),
            subtitle='A flame at every socket, rooted on the upper edge · five tall peaks · block-fire colours · world-up'))
    for c in cases:
        if args.only and c['name'] not in args.only:continue
        opts={k:v for k,v in c.items() if k!='name'}
        im=render(texture=texture,glow=glow,fire_texture=fire_texture,w=args.width,h=args.width,**opts);im.save(OUT/(c['name']+'.png'));print(c['name'],flush=True)
    if all((OUT/(c['name']+'.png')).exists() for c in cases):
        tw=600;sheet=Image.new('RGB',(tw*3,tw*math.ceil(len(cases)/3)+100),(24,26,28));d=ImageDraw.Draw(sheet)
        d.text((28,20),'PYRE KING / 11',fill=(232,208,164),font=font(30));d.text((28,61),'5.00-block masked king  /  1.80-block scale player  /  4.50-block longsword  /  review only',fill=(180,180,174),font=font(17))
        for i,c in enumerate(cases):
            im=Image.open(OUT/(c['name']+'.png'));im=im.resize((tw,tw),Image.Resampling.LANCZOS);sheet.paste(im,((i%3)*tw,100+(i//3)*tw))
        sheet.save(OUT/'contact-sheet.png')
    # Exact v10 cameras are unchanged. Compare archived pixels, never rebake v10
    # with the new geometry or alter the preserved original review evidence.
    for name in ['three-quarter','front','side','back','head-close-up','grip-close-up','player-8-blocks',
                 'horizontal-sweep','fire-close-up-high-guard']:
        if args.only and name not in args.only:continue
        saved=OUT.parent/'v10'/(name+'.png')
        if not saved.exists():continue
        before=Image.open(saved).convert('RGB')
        after=Image.open(OUT/(name+'.png')).convert('RGB')
        if before.size!=after.size:
            print(f'skipped compare-v10-v11-{name}: the archived v10 image is {before.size}, not {after.size}');continue
        w,h=after.size;pair=Image.new('RGB',(2*w,h+60),(24,26,28));d=ImageDraw.Draw(pair)
        d.text((26,16),'V10 / TENTH SCULPT',fill=(232,208,164),font=font(25))
        d.text((w+26,16),'V11 / ELEVENTH SCULPT',fill=(232,208,164),font=font(25))
        pair.paste(before,(0,60));pair.paste(after,(w,60))
        pair.save(OUT/('compare-v10-v11-'+name+'.png'))
    print(json.dumps(metrics,indent=2))
if __name__=='__main__':main()
