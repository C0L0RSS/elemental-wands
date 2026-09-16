#!/usr/bin/env python3
"""Deterministic native root-knot model; existing Minecraft textures, no raster changes."""
import json, math, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'
elements=[]
def cube(lo,hi,texture,tint=None):
    faces={}
    for side in ('up','down','north','south','east','west'):
        face={'texture':'#'+texture,'uv':[0,0,8,8]}
        if tint is not None: face['tintindex']=tint
        faces[side]=face
    elements.append({'from':[round(v,3) for v in lo],'to':[round(v,3) for v in hi],'faces':faces})
# Exposed angular heart, nested stepped highlights.
cube([5,3,5],[11,9,11],'heart',0x4cbd53)
cube([6,2,6],[10,11,10],'heart',0x90e878)
cube([6.8,5,4.8],[9.2,8,5.1],'heart',0xd1ff98)
# Six woven, rising roots form a loose cage. Three continue into major tendril sockets.
for branch in range(6):
    angle=branch*math.pi/3
    for segment in range(7):
        t=segment/6
        a=angle+math.sin(t*math.pi)*.55
        radius=6.1-3.0*math.sin(t*math.pi*.85)
        x,z=8+math.cos(a)*radius,8+math.sin(a)*radius
        y=.7+t*9.5
        width=2.5-1.1*t
        cube([x-width/2,y,z-width/2],[x+width/2,y+1.65,z+width/2],'bark')
        if segment in (1,3,5):
            cube([x-width/2-.06,y+.7,z-width/2-.06],[x+width/2+.06,y+1.05,z+width/2+.06],'grain')
    if branch%2==0:
        for segment in range(3):
            radius=4.5+segment*.95
            x,z=8+math.cos(angle)*radius,8+math.sin(angle)*radius
            y=1.3-segment*.35
            cube([x-.9,y,z-.9],[x+.9,y+1.5,z+.9],'bark')
# Small moss clusters, kept off the luminous target core.
for x,y,z in [(3,3,6),(11,2,9),(6,8,10),(10,9,5),(4,6,9),(9,1,3)]:
    cube([x,y,z],[x+1.6,y+.8,z+1.8],'moss')
model={'parent':'minecraft:block/block','ambientocclusion':True,
       'textures':{'particle':'minecraft:block/dark_oak_log','bark':'minecraft:block/dark_oak_log',
                   'grain':'minecraft:block/stripped_oak_log','heart':'minecraft:block/verdant_froglight_side','moss':'minecraft:block/moss_block'},
       'elements':elements}
outputs={ASSETS/'models/block/nature_root_knot.json':model,
         ASSETS/'blockstates/nature_root_knot.json':{'variants':{'':{'model':'elementalwands:block/nature_root_knot'}}}}
for path,data in outputs.items():
    content=json.dumps(data,indent=2)+'\n'
    if '--check' in sys.argv:
        if not path.exists() or path.read_text()!=content: raise SystemExit(f'Model drift: {path}')
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
print(f'Root knot: {len(elements)} textured cubes, 3 branch sockets; '+('verified' if '--check' in sys.argv else 'exported'))
