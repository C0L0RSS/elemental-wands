#!/usr/bin/env python3
"""Author a beveled, fractured 18-piece rock model, shared by runtime and preview."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "art/stone/cluster"
RESOURCE = ROOT / "src/main/resources/assets/elementalwands/models/entity/stone_cluster.json"

# Centre, half extents, Euler rotation, material threshold. Deliberate uneven
# silhouette, interlocking core and broken satellite pieces; no perfect sphere.
LAYOUT = [
    ((0,0,0),(.39,.33,.36),(8,17,-12),0),
    ((-.28,.13,.09),(.29,.30,.28),(-14,-21,17),1),
    ((.30,-.13,-.12),(.28,.27,.31),(21,10,25),9),
    ((.12,.32,.17),(.31,.24,.26),(-12,26,-9),17),
    ((-.23,-.32,-.18),(.25,.25,.29),(14,-8,-24),26),
    ((.36,.19,-.27),(.25,.26,.26),(28,-17,13),32),
    ((-.38,.28,-.23),(.28,.22,.23),(-23,8,21),38),
    ((.05,-.25,.43),(.30,.25,.19),(12,29,-8),44),
    ((-.30,.09,.43),(.23,.27,.20),(-8,13,19),51),
    ((.38,.14,.34),(.24,.20,.24),(19,-31,12),56),
    ((.02,.52,-.21),(.26,.21,.23),(-17,11,9),61),
    ((-.06,-.48,-.41),(.22,.25,.20),(9,20,-22),67),
    ((-.58,-.13,.01),(.19,.27,.24),(16,-25,17),75),
    ((.62,-.19,.04),(.19,.24,.20),(-27,18,-19),79),
    ((-.39,.46,.23),(.22,.19,.24),(17,28,12),84),
    ((.35,-.46,.29),(.21,.21,.23),(-18,-16,26),89),
    ((.20,.32,-.54),(.23,.22,.18),(23,9,-14),93),
    ((-.32,-.13,-.61),(.25,.19,.16),(-9,31,18),96),
]


def rotate(v, angles):
    x,y,z=v
    a,b,c=map(math.radians,angles)
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    return [x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c),z]


def bevel_box(size):
    cut=min(size)*.40
    faces=[]
    # Six octagonal main planes.
    for axis in range(3):
        u,v=(axis+1)%3,(axis+2)%3
        a,b=size[u],size[v]
        ring=[(-a+cut,-b),(a-cut,-b),(a,-b+cut),(a,b-cut),
              (a-cut,b),(-a+cut,b),(-a,b-cut),(-a,-b+cut)]
        for sign in (-1,1):
            face=[]
            for x,y in ring:
                p=[0,0,0];p[axis]=sign*size[axis];p[u]=x;p[v]=y;face.append(p)
            faces.append(face)
    # Twelve bevel strips, then the eight clipped corners.
    for free in range(3):
        u,v=(free+1)%3,(free+2)%3
        for a in (-1,1):
            for b in (-1,1):
                face=[]
                for end,which in ((-1,0),(1,0),(1,1),(-1,1)):
                    p=[0,0,0];p[free]=end*(size[free]-cut)
                    p[u]=a*(size[u]-(cut if which else 0))
                    p[v]=b*(size[v]-(0 if which else cut));face.append(p)
                faces.append(face)
    for a in (-1,1):
        for b in (-1,1):
            for c in (-1,1):
                signs=(a,b,c);face=[]
                for axis in range(3):
                    face.append([signs[i]*(size[i]-(0 if i==axis else cut)) for i in range(3)])
                faces.append(face)
    # Orient every polygon outward, using its centroid and cross product.
    for face in faces:
        a,b,c=face[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
        if sum(normal[i]*sum(p[i] for p in face) for i in range(3))<0: face.reverse()
    return faces


def main():
    parts=[]
    for center,size,angles,threshold in LAYOUT:
        faces=[]
        for polygon in bevel_box(size):
            a,b,c=polygon[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
            normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            major=max(range(3),key=lambda i:abs(normal[i]));ux,uy=(major+1)%3,(major+2)%3
            uv=[[(p[ux]/size[ux]+1)/2,(p[uy]/size[uy]+1)/2] for p in polygon]
            positions=[[rotate(p,angles)[i]+center[i] for i in range(3)] for p in polygon]
            faces.append({"positions":positions,"uv":uv})
        parts.append({"threshold":threshold,"center":list(center),"faces":faces})
    bound=max(math.sqrt(sum(c*c for c in p)) for part in parts for f in part['faces'] for p in f['positions'])
    factor=.94/bound
    for part in parts:
        part['center']=[round(c*factor,6) for c in part['center']]
        for face in part['faces']:
            face['positions']=[[round(c*factor,6) for c in p] for p in face['positions']]
    data={"name":"Stone - Gathered Mass","texture":"elementalwands:textures/block/stone_spike.png",
          "bounds_radius":.94,"parts":parts}
    OUT.mkdir(parents=True,exist_ok=True);RESOURCE.parent.mkdir(parents=True,exist_ok=True)
    contents=json.dumps(data,separators=(',',':'))+'\n'
    RESOURCE.write_text(contents);(OUT/'stone_cluster.json').write_text(contents)
    # Standard editable mesh for external 3D tools.
    lines=['# Stone cluster, metres / Minecraft blocks at unit radius']
    index=1
    for n,part in enumerate(parts):
        lines.append(f'o fragment_{n:02}')
        for f in part['faces']:
            lines.extend('v '+' '.join(map(str,p)) for p in f['positions'])
            lines.append('f '+' '.join(str(index+i) for i in range(len(f['positions']))))
            index+=len(f['positions'])
    (OUT/'stone_cluster.obj').write_text('\n'.join(lines)+'\n')
    print(f'Stone cluster: {len(parts)} fragments, {sum(len(p["faces"]) for p in parts)} beveled faces, radius <= 0.94.')


if __name__=='__main__':main()
