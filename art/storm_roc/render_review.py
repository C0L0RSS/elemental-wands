#!/usr/bin/env python3
"""Offline reference rasterizer of the viewer's draw calls (not browser screenshots)."""
import argparse,base64,io,json,subprocess
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
PREVIEW=HERE.parents[1]/'.local-previews/storm-roc'
CONCEPTS=HERE/'concepts'


def render(case,record,textures,transparent=False):
    if case['overlay']=='posture':return posture_overlay(case,record,textures)
    w,h=record['width'],record['height'];pixels=np.zeros((h,w,4),dtype=np.float64);pixels[:]=[0,0,0,0] if transparent else [133,172,192,255]
    depth=np.full((h,w),np.inf)
    for draw in case['draws']:
        data=np.array(draw['vertices'],dtype=float).reshape(-1,8)
        if len(data)==0:continue
        u=draw['uniform'];vp=np.array(u['vp']).reshape(4,4).T
        clip=np.column_stack((data[:,:3],np.ones(len(data))))@vp.T
        ww=clip[:,3];ndc=clip[:,:3]/ww[:,None];screen=np.column_stack(((ndc[:,0]+1)*w/2,(1-ndc[:,1])*h/2))
        texture=textures[draw['texture']];mask=textures[draw['mask']];th,tw=texture.shape[:2]
        sprite=u['emissiveSprite'];tint=np.array(u['tint']);charge=u['charge']
        for a in range(0,len(data),3):
            ids=slice(a,a+3);p=screen[ids]
            if np.any(ww[ids]<=0):continue
            lo=np.maximum(np.floor(p.min(axis=0)).astype(int),[0,0]);hi=np.minimum(np.ceil(p.max(axis=0)).astype(int),[w-1,h-1])
            if np.any(lo>hi):continue
            x0,y0=p[0];x1,y1=p[1];x2,y2=p[2];den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
            if abs(den)<1e-8:continue
            yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];xx=xx+.5;yy=yy+.5
            aa=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den
            bb=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den;cc=1-aa-bb
            zz=aa*ndc[a,2]+bb*ndc[a+1,2]+cc*ndc[a+2,2]
            dz=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            inside=(aa>=-1e-7)&(bb>=-1e-7)&(cc>=-1e-7)&(zz<=dz+1e-7)
            if not inside.any():continue
            coeff=np.stack([aa/ww[a],bb/ww[a+1],cc/ww[a+2]],axis=-1);coeff/=coeff.sum(axis=-1,keepdims=True)
            uv=coeff@data[ids,6:8]
            tx=np.clip((uv[:,:,0]*tw).astype(int),0,tw-1);ty=np.clip(((1-uv[:,:,1])*th).astype(int),0,th-1)
            tex=texture[ty,tx]/255.;tex*=tint
            inside &= tex[:,:,3]>(.01 if sprite else .5)
            if not inside.any():continue
            patch=pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            if sprite:
                rgb=tex[:,:,:3]*charge*255
                patch[:,:,:3][inside]=np.minimum(255,patch[:,:,:3][inside]+rgb[inside]*tex[:,:,3][inside,None])
            else:
                n=data[a,3:6];n=n/max(np.linalg.norm(n),1e-9)
                l=np.array([-.5,1,-.8]);l/=np.linalg.norm(l);l2=np.array([1,.2,.5]);l2/=np.linalg.norm(l2)
                shade=min(1,u['ambient']+.38*max(0,n@l)+.12*max(0,n@l2))
                mh,mw=mask.shape[:2];mx=np.clip((uv[:,:,0]*mw).astype(int),0,mw-1);my=np.clip(((1-uv[:,:,1])*mh).astype(int),0,mh-1)
                g=mask[my,mx]/255.;rgb=tex[:,:,:3]*shade*(1-g[:,:,3,None])+g[:,:,:3]*charge*g[:,:,3,None]
                patch[:,:,:3][inside]=np.clip(rgb[inside]*255,0,255);patch[:,:,3][inside]=255;dz[inside]=zz[inside]
    im=Image.fromarray(pixels.astype('uint8'))
    if case['overlay']:
        face=case['focus'];src=Image.open(CONCEPTS/('face-and-feathers.png' if face else 'body-turnaround.png')).convert('RGBA')
        x,y,cw,ch=(0,0,930,1024) if face else (0,0,705,1024) if case['clip']=='perched' else (710,0,826,510)
        rh=int(h*.94);rw=int(rh*cw/ch);layer=src.crop((x,y,x+cw,y+ch)).resize((rw,rh));layer.putalpha(97);im.alpha_composite(layer,((w-rw)//2,int(h*.03)))
    if transparent:return im
    d=ImageDraw.Draw(im);d.rectangle((8,h-27,408,h-5),fill=(13,24,37,230));d.text((16,h-23),'OFFLINE VIEWER RENDER / '+case['name'],fill=(218,230,238))
    return im


def posture_overlay(case,record,textures):
    """Uniform silhouette registration: common nose/tail extent, no shape warping."""
    isolated={**case,'overlay':False,'draws':[max(case['draws'],key=lambda d:len(d['vertices']))]}
    model=render(isolated,record,textures,transparent=True)
    bbox=model.getchannel('A').getbbox();model=model.crop(bbox)
    src=Image.open(CONCEPTS/'body-postures.png').convert('RGBA')
    hunch=case['clip']=='hunched'
    # Top figure excludes the scale mannequin; bottom uses the full flying figure.
    crop=(0,0,1536,545) if hunch else (0,535,1536,1024)
    concept=src.crop(crop);padding=220
    width=1320 if hunch else 1480
    factor=width/(case['anchors']['beakX']-case['anchors']['tailX'])
    left=round(20-(case['anchors']['tailX']-bbox[0])*factor)
    model=model.resize((round(model.width*factor),round(model.height*factor)),Image.Resampling.NEAREST)
    alpha=model.getchannel('A').point(lambda x:round(x*.52));model.putalpha(alpha)
    # Grounded talons align with the concept floor; flight aligns the rear head socket.
    if hunch:top=padding+527-model.height
    else:
        top=round(padding+310-(case['anchors']['headScreen'][1]-bbox[1])*factor)
    # Preserve intentional wing overhang beyond the old concept's span.
    pad_left=max(0,-left)+40;pad_right=max(0,left+model.width-1536)+40
    pad_top=max(0,-top)+20
    sw=1536+pad_left+pad_right;sh=max(concept.height+padding*2,top+model.height+75)+pad_top
    sheet=Image.new('RGBA',(sw,sh),(153,150,145,255))
    sheet.alpha_composite(concept,(pad_left,padding+pad_top))
    sheet.alpha_composite(model,(left+pad_left,top+pad_top))
    d=ImageDraw.Draw(sheet)
    d.rectangle((0,sheet.height-54,sheet.width,sheet.height),fill=(16,24,35,255))
    d.text((20,sheet.height-43),case['name']+' / model 52% over concept / uniform beak-to-tail scale; no silhouette warping',fill='white')
    d.text((20,sheet.height-24),'Registration: tail and beak extent; grounded feet or flight head height. Differences remain visible.',fill=(195,207,218))
    w=record['width'];return sheet.resize((w,round(sheet.height*w/sheet.width)),Image.Resampling.LANCZOS)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--only',nargs='*');p.add_argument('--width',type=int,default=1200);args=p.parse_args()
    (PREVIEW/'shots').mkdir(exist_ok=True)
    path=PREVIEW/'review-draws.json';subprocess.run(['node',str(HERE/'review_scene.cjs'),str(path),str(args.width),*(args.only or [])],check=True)
    record=json.loads(path.read_text());textures=[]
    for t in record['textures']:
        if t.get('src'):im=Image.open(io.BytesIO(base64.b64decode(t['src'].split(',')[1]))).convert('RGBA')
        else:im=Image.new('RGBA',(1,1),tuple(t['solid']))
        textures.append(np.array(im))
    for case in record['cases']:
        if args.only and case['name'] not in args.only:continue
        render(case,record,textures).save(PREVIEW/'shots'/(case['name']+'.png'));print(case['name'],flush=True)
    path.unlink()
    if not args.only:
        names=[case['name'] for case in record['cases']]
        sheet=Image.new('RGB',(1200,((len(names)+2)//3)*293),(16,24,35))
        draw=ImageDraw.Draw(sheet)
        for i,name in enumerate(names):
            thumb=Image.open(PREVIEW/'shots'/(name+'.png')).convert('RGB')
            thumb.thumbnail((400,273));x=(i%3)*400;y=(i//3)*293
            sheet.paste(thumb,(x,y));draw.text((x+8,y+276),name,fill='white')
        sheet.save(PREVIEW/'shots'/'review-contact.png')
        print('review-contact',flush=True)
if __name__=='__main__':main()
