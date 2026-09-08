"""Render the exported cuboids directly, with orthographic depth buffering.

These are views of the actual model, not generated concept illustrations.
Requires Pillow and NumPy. Also assembles an offline WebGL review page.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
mesh = json.loads((ROOT/'mesh.json').read_text())


def camera(yaw, elevation):
    a,b=map(math.radians,(yaw,elevation))
    return np.array([[math.cos(a),0,math.sin(a)],
                     [-math.sin(a)*math.sin(b),math.cos(b),math.cos(a)*math.sin(b)],
                     [math.sin(a)*math.cos(b),math.sin(b),-math.cos(a)*math.cos(b)]])


def triangles():
    for cube in mesh:
        v=np.array(cube['vertices'])
        for face in cube['faces']:
            q=v[face]
            n=np.cross(q[1]-q[0],q[2]-q[0]); n/=np.linalg.norm(n)
            for indices in [[0,1,2],[0,2,3]]:
                yield q[indices],n


def render(yaw,elevation,size=1000):
    cam=camera(yaw,elevation)
    light=np.array([-.55,.8,.65]);light/=np.linalg.norm(light)
    scale=size/112
    rgb=np.empty((size,size,3),dtype=np.uint8);rgb[:]=[239,237,232]
    depth=np.full((size,size),-1e9)
    for tri,n in triangles():
        if np.dot(n,cam[2])<=0:continue
        v=(tri-[0,40,0])@cam.T
        v[:,0]=v[:,0]*scale+size/2
        v[:,1]=-v[:,1]*scale+size/2
        xmin=max(0,int(np.floor(v[:,0].min())));xmax=min(size-1,int(np.ceil(v[:,0].max())))
        ymin=max(0,int(np.floor(v[:,1].min())));ymax=min(size-1,int(np.ceil(v[:,1].max())))
        if xmin>xmax or ymin>ymax:continue
        x,y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
        a,b,c=v
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:continue
        w1=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
        w2=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den
        w3=1-w1-w2;z=w1*a[2]+w2*b[2]+w3*c[2]
        region=depth[ymin:ymax+1,xmin:xmax+1]
        mask=(w1>=-1e-7)&(w2>=-1e-7)&(w3>=-1e-7)&(z>region)
        shade=.49+.46*max(0,np.dot(cam@n,light))+.07*max(0,n[1])
        color=np.clip(np.array([186,184,177])*shade,0,255).astype(np.uint8)
        region[mask]=z[mask]
        rgb[ymin:ymax+1,xmin:xmax+1][mask]=color
    # Restrained screen-space contact shading: geometry remains unaltered.
    valid=depth>-1e8;occ=np.zeros(depth.shape)
    for dy,dx in [(0,3),(0,-3),(3,0),(-3,0),(5,5),(-5,-5),(5,-5),(-5,5)]:
        near=np.roll(depth,(dy,dx),(0,1));delta=near-depth
        occ+=valid&(near>-1e8)&(delta>.7)&(delta<13)
    rgb[valid]=(rgb[valid]*(1-.035*occ[valid,None])).astype(np.uint8)
    return Image.fromarray(rgb)


font_path='/System/Library/Fonts/Supplemental/Arial.ttf'
bold_path='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
def font(size,bold=False):return ImageFont.truetype(bold_path if bold else font_path,size)


views=[('three-quarter',-32,14),('front',0,0),('side',90,0),('back',180,0)]
renders={}
for name,yaw,pitch in views:
    img=render(yaw,pitch,1400 if name=='three-quarter' else 900)
    img.save(ROOT/f'{name}.png');renders[name]=img
sheet=Image.new('RGB',(2200,1550),(239,237,232));d=ImageDraw.Draw(sheet)
d.text((70,45),'FRACTURED GUARDIAN',font=font(48,True),fill=(45,48,47))
d.text((73,110),'GEOMETRY STUDY 01  /  ACTUAL MODEL',font=font(21),fill=(107,109,105))
sheet.paste(renders['three-quarter'].resize((1350,1350),Image.Resampling.LANCZOS),(0,170))
for name,xy,label in [('front',(1300,190),'FRONT'),('side',(1760,190),'SIDE'),('back',(1440,800),'BACK')]:
    size=420 if name!='back' else 630
    sheet.paste(renders[name].resize((size,size),Image.Resampling.LANCZOS),xy)
    d.text((xy[0]+size//2,xy[1]+size+12),label,font=font(18),fill=(95,98,94),anchor='mt')
d.text((75,1490),'Neutral clay • Texture, glow and animation come after shape review',font=font(23),fill=(102,104,100))
sheet.save(ROOT/'model-review.png')
template=(ROOT/'viewer.template.html').read_text()
(ROOT/'preview.html').write_text(template.replace('__MESH_DATA__',json.dumps(mesh,separators=(',',':'))).replace('__CUBE_COUNT__',str(len(mesh))))
print('Rendered actual cuboids: four views, model-review.png, offline preview.html')
