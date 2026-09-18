#!/usr/bin/env python3
"""Export the approved wand's joined wood shell and pixel textures. --check detects drift."""
import argparse, io, json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'

def rgba(h):return tuple(bytes.fromhex(h.removeprefix('#')))+(255,)
def png(image):
 b=io.BytesIO();image.save(b,format='PNG');return b.getvalue()
def joined_shell(boxes):
 # Coordinate compression is exact at every box boundary: no hidden faces,
 # coincident shells or voxel approximation of the approved silhouette.
 axes=[sorted({b[k][a] for b in boxes for k in ('min','max')}) for a in range(3)]
 lookups=[{v:i for i,v in enumerate(axis)} for axis in axes]
 solid=np.zeros(tuple(len(axis)-1 for axis in axes),dtype=np.uint8)
 for b in boxes:
  slices=tuple(slice(lookups[a][b['min'][a]],lookups[a][b['max'][a]]) for a in range(3))
  solid[slices]=b['material']+1
 quads=[]
 for axis in range(3):
  u,v=(axis+1)%3,(axis+2)%3
  for sign in (-1,1):
   for plane in range(len(axes[axis])):
    inside=plane-1 if sign==1 else plane;outside=plane if sign==1 else plane-1
    if not 0<=inside<solid.shape[axis]:continue
    slab=np.take(solid,inside,axis=axis)
    neighbour=np.take(solid,outside,axis=axis) if 0<=outside<solid.shape[axis] else np.zeros_like(slab)
    remaining=[a for a in range(3) if a!=axis]
    mask=np.where(neighbour==0,slab,0)
    if remaining!=[u,v]:mask=mask.T
    mask=mask.copy()
    # Merge adjacent exposed cells of the same material into nonoverlapping quads.
    for i in range(mask.shape[0]):
     for j in range(mask.shape[1]):
      mat=int(mask[i,j])
      if not mat:continue
      j2=j+1
      while j2<mask.shape[1] and mask[i,j2]==mat:j2+=1
      i2=i+1
      while i2<mask.shape[0] and np.all(mask[i2,j:j2]==mat):i2+=1
      mask[i:i2,j:j2]=0
      pts=[]
      for a,b in [(i,j),(i2,j),(i2,j2),(i,j2)]:
       p=[0.,0.,0.];p[axis]=axes[axis][plane];p[u]=axes[u][a];p[v]=axes[v][b];pts.append(p)
      if sign<0:pts.reverse()
      normal=[0,0,0];normal[axis]=sign
      uv=[]
      for p in pts:
       uv.extend([p[2 if axis==0 else 0]/.88,p[2 if axis==1 else 1]/1.76])
      quads.append([round(f,6) for p in pts for f in p]+normal+[round(f,6) for f in uv]+[mat-1])
 assert quads and len({tuple(q[:12]) for q in quads})==len(quads),'Duplicate wood faces'
 # Divergence theorem: the exterior shell must enclose exactly the occupied
 # volume, with outward winding. This catches missing/reversed interior faces.
 expected=float(np.sum((solid>0)*np.diff(axes[0])[:,None,None]*np.diff(axes[1])[None,:,None]*np.diff(axes[2])[None,None,:]))
 volume=0.0
 for q in quads:
  pts=np.array(q[:12]).reshape(4,3);n=np.array(q[12:15]);cross=np.cross(pts[1]-pts[0],pts[3]-pts[0])
  assert np.dot(cross,n)>0,'Inverted wood face'
  volume+=float(np.dot(pts[0],n)*np.linalg.norm(cross))/3
 assert abs(volume-expected)<1e-5,f'Non-closed wood shell: {volume} != {expected}'
 return quads

def outputs():
 d=json.loads((ROOT/'art/wand/design.json').read_text());q=joined_shell(d['woodBoxes'])
 wood=Image.new('RGBA',(4,8))
 for y,row in enumerate(d['woodRows']):
  for x,v in enumerate(row):wood.putpixel((x,y),rgba(d['woodPalette'][int(v)]))
 atlas=Image.new('RGBA',(64,16),(255,255,255,255))
 for i,(name,style) in enumerate(d['elements'].items()):
  for y,row in enumerate(style['rows']):
   for x,v in enumerate(row):atlas.putpixel((i*10+x,y),rgba(style['palette'][int(v)]))
 # A single front-facing glass skin. Reflections are in this texture, not stacked planes.
 glass=Image.new('RGBA',(16,16),(198,210,215,22));g=ImageDraw.Draw(glass)
 g.rectangle((0,0,15,15),outline=(201,216,222,125))
 g.rectangle((2,2,2,6),fill=(231,239,241,190));g.rectangle((2,2,5,2),fill=(231,239,241,190))
 g.rectangle((12,11,12,13),fill=(222,234,240,160))
 cropped=[]
 for b in d['woodBoxes']:
  if b['max'][1]<=d['guiMinY']:continue
  b=dict(b);b['min']=list(b['min']);b['min'][1]=max(b['min'][1],d['guiMinY']);cropped.append(b)
 gui=joined_shell(cropped)
 runtime={'guiWood':gui,'scale':d['scale'],'rotationZ':d['rotationZ'],'headY':d['headY'],'headScale':d['headScale'],'wood':q,'elementOrder':list(d['elements']),'particleColors':[int(e['particleColor'][1:],16) for e in d['elements'].values()]}
 return {'wand/mesh.json':(json.dumps(runtime,separators=(',',':'))+'\n').encode(),'textures/wand/wood.png':png(wood),'textures/wand/cores.png':png(atlas),'textures/wand/glass.png':png(glass)}

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
 result=outputs()
 for name,data in result.items():
  p=ASSETS/name
  if args.check:
   if not p.exists() or p.read_bytes()!=data:raise SystemExit(f'Wand asset drift: {name}')
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 count=len(json.loads(result['wand/mesh.json'])['wood'])
 print(f'Wand assets verified: {count} unique exterior wood quads; 6 core palettes; 3 textures.')
if __name__=='__main__':main()
