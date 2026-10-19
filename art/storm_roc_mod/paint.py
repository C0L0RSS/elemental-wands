"""Pixel-art atlas for the chunky Roc: one painted tile per block face.

Face orientation follows the viewer and GeckoLib: on side faces the tile's top is +y;
on a slab's `up` face the tile's top is its tip (+z), on its `down` face the base.
Patterns are drawn per material and block kind, with a limited palette and no noise.
"""
import math,zlib
import numpy as np
from PIL import Image

def hexes(*cs):return [tuple(bytes.fromhex(c)) for c in cs]
PAL={
 'rust':hexes('5a2414','7a3218','9e431e','c05a27','da7838'),
 'rust_dark':hexes('461b0f','612617','7c3220','974228','b0552f'),
 'sienna':hexes('3a1a10','50241a','683022','82402a','9c5233'),
 'ruff':hexes('7a3312','a2471a','c4621f','de7f2c','f0a24c'),
 # Rusty orange like the neck ruff, a shade lighter, so the head belongs to the body.
 'crown':hexes('8a3a16','ae5020','c8682c','dc8440','eca462'),
 'mouth':hexes('2a0c0a','4a1410','6a1e18','8a2c22','a83c2c'),
 'cream':hexes('9a7a58','bd9a72','dcbf94','eed8b0','faf0d8'),
 'navy':hexes('141c2e','1e2a44','2a3c5e','3a5280','4e6aa0'),
 'mask':hexes('121010','1c1816','28221e','362d27','463a31'),
 'horn':hexes('8a7a64','a8977c','c7b698','e0d2b4','f2e8d0'),
 'horn_dark':hexes('4a4038','5e5248','74685a','8a7e6c','a09480'),
 'horn_tip':hexes('6e6152','8a7c6a','a89a84','c4b8a0','ddd2bc'),
 'skin':hexes('5a1e10','7c2c16','a03c1e','c0542a','d8703c'),
 'talon':hexes('101010','1e1c1c','2e2b2a','403c3a','55504c'),
 'gold':hexes('9a4e0a','df9416','fccf36','fdf494','ffffe0'),
 'eye':hexes('121010','8a1414','c42a22','e8c860','fff8d8'),
}
PAL['crest']=PAL['ruff']
FACES=('north','south','east','west','up','down')

def dims(part,face):
    w,h,d=part['cube']['size'];k=part['density']
    fw,fh={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}[face]
    return max(1,round(fw*k)),max(1,round(fh*k))

def scallops(a,b,size=4,span=6):
    """Offset rows of small feather scallops whose rounded tips point toward +a."""
    row=a//size;sx=(b+(row%2)*(span//2))%span;sy=a%size
    t=np.full(a.shape,2);t[sy==(size-1-np.abs(sx-(span-1)/2).astype(int))]=1
    t[(sy==0)&(sx>1)&(sx<span-2)]=3
    return t

def tip_zone(part,face,tw,th):
    """Where a feather's painted tip colour goes: a V deepest along the shaft, on every face."""
    tip=part.get('tip',0)
    if not tip or face=='north':return np.zeros((th,tw),dtype=bool)
    if face=='south':return np.ones((th,tw),dtype=bool)
    v,u=np.mgrid[0:th,0:tw]
    if face in ('up','down'):
        tipv=v if face=='up' else th-1-v
        return tipv+.45*np.abs(u-(tw-1)/2)<th*tip
    w,_,d=part['cube']['size'];edge=max(0,tip-.45*w/2/d)
    along=u if face=='east' else tw-1-u
    return along>=tw*(1-edge)

def tile(part,face,tw,th,flat):
    mat,kind=part['mat'],part['kind'];pal=PAL[mat]
    t=np.zeros((th,tw),dtype=int)+2;glow=np.zeros((th,tw),dtype=bool)
    alt=tip_zone(part,face,tw,th);altpal=PAL[part['tip_mat']] if part.get('tip_mat') else pal
    if flat:
        t[:]=2;t[0,:]=t[-1,:]=t[:,0]=t[:,-1]=1
        return t,glow,pal,alt,altpal
    v,u=np.mgrid[0:th,0:tw]
    long=face in ('up','down')
    trail=part.get('trail',1) or 1
    if kind=='arm':
        # The wing's arm: small marginal coverts all over, rows running back on top and below.
        if long:t=scallops(u if trail>0 else tw-1-u,v)
        elif face in ('east','west'):t=scallops(v,u)
        else:t[:]=1;t[0,:]=2
    elif kind=='covert':
        if long:
            # Lesser coverts under the arm, then a row of long greater coverts with dark tips.
            c=u if trail>0 else tw-1-u;split=int(tw*.45)
            t=scallops(c,v)
            back=c>=split;col=v%5;ends=(tw-1-c)<(np.abs(col-2)>=2)
            t[back]=np.where((v//5)%2==0,2,3)[back]
            t[back&(col==0)]=1;t[back&ends]=1;t[c==split]=1
            alt=back&(c>=tw*.8)&~ends;altpal=PAL['rust_dark']
            if face=='down':t[t==3]=2
        elif face==('east' if trail>0 else 'west'):
            t[:]=1;t[:,u[0]%5==2]=2                                  # the ragged trailing edge
        else:t[:]=1;t[0,:]=2
    elif kind=='tip':
        # A feather's point, a square turned 45°: barbs slant off a shaft down the diagonal it
        # points along, inside a dark outline.
        if face=='up':t[:]=2;t[(u+v)%5==0]=1;t[np.abs(u-v)<1]=3
        else:t[:]=3 if face=='down' else 1
        if long:t[:,0]=t[:,-1]=t[0,:]=t[-1,:]=1
        else:t[0,:]=2
    elif kind in ('feather','edge') and long:
        # Rows of barbs toward the tip, a pale shaft, dark vane edges, a darker tip.
        tipv=v if face=='up' else th-1-v                     # 0 at the tip end
        shaft=tw//2
        t[:]=2 if face=='up' else 3
        band=(tipv+np.abs(u-shaft)//2)%5==0
        t[band]=1 if face=='up' else 2
        if tw>=5:t[(np.abs(u-shaft)<1)&(tipv>1)]=3 if face=='up' else 4
        t[:,0]=1;t[:,-1]=1
        t[tipv<2]=1
        if kind=='edge':
            # Small covert scallops along the arm instead of a shaft.
            t[:]=2;row=v//4;sx=(u+(row%2)*3)%6;edge=(v%4)==(3-np.abs(sx-2.5).astype(int)//1)
            t[edge]=1;t[(v%4==0)&(sx>1)&(sx<4)]=3
    elif kind in ('feather','edge','hackle') and not long:
        t[:]=1;t[0,:]=2
    elif kind=='hackle' or (kind=='plate' and mat in ('ruff','sienna','rust')):
        if long:
            # Lanceolate hackle: a dark midline, light flanks, a dark point at the tip.
            tipv=v if face=='up' else th-1-v;mid=tw/2
            t[:]=3;t[np.abs(u-mid+.5)<1]=1
            t[(np.abs(u-mid+.5)>tw/2-1.5)]=2
            taper=np.abs(u-mid+.5)>(tipv+1)*.8
            t[taper]=1
        else:t[:]=1;t[0,:]=2
    elif mat=='cream' and face!='up' and face!='down':
        # Cream with rust chevrons pointing down.
        t[:]=3;off=np.abs(u-(tw-1)/2)//2;band=(v-off)%7
        t[band==0]=-1;t[band==1]=-2
    elif mat=='cream':
        t[:]=3;t[(v%6==0)&((u//3)%2==0)]=2
    elif kind=='bill':
        t[:]=2;t[:max(1,th//6)]=3;t[th-max(1,th//6):]=1
        t[(u%5==0)&(v>th//6)&(v<th-th//6)]=1 if mat=='horn_dark' else 2
        if face in ('east','west') and mat=='horn' and tw>=10:
            # Nostril slit near the base: east runs base to tip, west tip to base.
            u0=int(tw*.18) if face=='east' else tw-int(tw*.18)-3
            t[th//3:th//3+1,u0:u0+3]=0
    elif kind=='eye':
        if face=='south':
            # Small forward eye: red ring, pale gold iris, black pupil and a glint.
            cy,cx=(th-1)/2,(tw-1)/2;r=np.hypot((u-cx)/max(1,tw/2),(v-cy)/max(1,th/2))
            t[:]=0;t[r<1.05]=2;t[r<.72]=3;t[r<.34]=0
            t[int(cy)-1,int(cx)+1]=4
            pal=PAL['eye']
        else:t[:]=1;pal=PAL['mask']
    elif kind=='brow':
        t[:]=3 if face=='up' else 2;t[-1:,:]=1
        if face=='down':t[:]=0;pal=PAL['mask']
    elif kind=='scales':
        span=6;row=v//4;sx=(u+(row%2)*3)%span;sy=v%4
        crown=np.round(1.5*((sx-2.5)/3)**2)
        t[:]=2;t[sy==crown]=3;t[(sy==crown+1)&(sx>1)&(sx<5)]=4;t[sy==3]=1
    elif kind=='scutes':
        t[:]=3;t[v%4==3]=1
    elif kind=='talon':
        t[:]=2;t[:,0]=1;t[:max(1,th//3)]=3
    elif mat in ('mask',):
        t[:]=1;t[(u%3==0)&(v%4<2)]=0;t[(u%4==2)&(v%5==1)]=2
    elif mat=='crown':
        # Fine crown feathers: small offset scallops with a light tip on each.
        rows=v if face!='up' else th-1-v
        row=rows//4;sx=(u+(row%2)*3)%6;sy=rows%4
        t[:]=3;t[(sy==3)&(sx>0)&(sx<5)]=2;t[(sy==0)&((sx==2)|(sx==3))]=4
        if face=='down':t[:]=2
    else:
        # Contour feathers: offset rows of scallops, tips down on the sides, back on the top.
        rows=v if face!='up' else th-1-v
        row=rows//5;sx=(u+(row%2)*3)%6;sy=rows%5
        edge=sy==(4-np.round(np.abs(sx-2.5)*.8)).astype(int)
        t[:]=2;t[edge]=1;t[(sy==0)&(sx>1)&(sx<5)]=3
    if part.get('vein') and face in ('up','east','west','north','south') and tw>=8 and th>=6:
        # A branching zig-zag lightning crack, gold, lit in the glow mask.
        rng=np.random.default_rng(zlib.crc32(repr((part['bone'],part['cube']['origin'],face)).encode()))
        x=int(tw*rng.uniform(.25,.4));path=[]
        for y in range(th):
            if y%3==0:x=int(np.clip(x+rng.choice([-1,1,1]),1,tw-2))
            path.append((y,x))
        for y,x in path:
            t[y,x]=-3;glow[y,x]=True
            if 0<x<tw-1:t[y,x+1]=-4;glow[y,x+1]=True
        y0,x0=path[th//2]
        for k in range(min(tw-x0-2,6)):
            y=y0-k//2
            if 0<=y<th:t[y,x0+2+k]=-3;glow[y,x0+2+k]=True
    return t,glow,pal,alt,altpal

def colour(t,pal,mat):
    rust=PAL['rust'];gold=PAL['gold']
    out=np.zeros(t.shape+(4,),dtype=np.uint8)
    for k in range(5):out[t==k]=(*pal[k],255)
    out[t==-1]=(*rust[2],255);out[t==-2]=(*rust[1],255)
    out[t==-3]=(*gold[2],255);out[t==-4]=(*gold[3],255)
    return out

def paint(parts,flat=False,width=1024):
    tiles=[]
    for part in parts:
        part['cube']['uv']={}
        for face in FACES:
            tw,th=dims(part,face);tiles.append((part,face,tw,th))
    # Shelf packing, tallest first, with a one-texel extruded gutter.
    order=sorted(range(len(tiles)),key=lambda i:(-tiles[i][3],-tiles[i][2],i))
    x=y=shelf=0;spots={}
    for i in order:
        _,_,tw,th=tiles[i]
        if x+tw+2>width:x=0;y+=shelf;shelf=0
        spots[i]=(x+1,y+1);x+=tw+2;shelf=max(shelf,th+2)
    height=1
    while height<y+shelf:height*=2
    tex=np.zeros((height,width,4),dtype=np.uint8);glow=np.zeros((height,width,4),dtype=np.uint8)
    for i,(part,face,tw,th) in enumerate(tiles):
        px,py=spots[i]
        t,g,pal,alt,altpal=tile(part,face,tw,th,flat)
        rgba=colour(t,pal,part['mat']);rgba[alt]=colour(t,altpal,part['mat'])[alt]
        padded=np.pad(rgba,((1,1),(1,1),(0,0)),mode='edge')
        tex[py-1:py+th+1,px-1:px+tw+1]=padded
        lit=np.zeros((th,tw,4),dtype=np.uint8);lit[g]=rgba[g]
        glow[py-1:py+th+1,px-1:px+tw+1]=np.pad(lit,((1,1),(1,1),(0,0)),mode='edge')
        part['cube']['uv'][face]={'uv':[px,py],'uv_size':[tw,th]}
    return Image.fromarray(tex),Image.fromarray(glow),(width,height)
