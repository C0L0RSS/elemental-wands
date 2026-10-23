#!/usr/bin/env python3
"""Offline pose sheets at 3/4 view and a perspective eye 1.8 blocks above ground.

Reuses the approved textured cuboid rasterizer. The perspective variant applies
reciprocal-depth interpolation to UVs, matching the browser's camera projection.
"""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'.local-previews/guardian-combat-review'


def render():
    p=runpy.run_path(str(HERE.parent/'v4-expressive/preview_motion.py'))
    clips=json.loads((ROOT/'src/main/resources/assets/elementalwands/geckolib/animations/fractured_guardian.animation.json').read_text())['animations']
    code=(p['SOURCE']/'render_preview.py').read_text().split('views=[')[0]
    code=code.replace('scale=size/112','scale=size/170').replace('tri-[0,40,0]','tri-[0,60,0]')
    perspective=code.replace('v=(tri-[0,60,0])@cam.T',
        'v=(tri-[0,28.8,-160])@cam.T\n        distances=-v[:,2].copy()\n        if distances.min()<=1:continue\n        v[:,:2]/=distances[:,None]\n        v[:,2]=1/distances\n        scale=size/(2*.70)')
    perspective=perspective.replace('texcoords=w1[...,None]*uv[0]+w2[...,None]*uv[1]+w3[...,None]*uv[2]',
        'texcoords=(w1[...,None]*uv[0]/distances[0]+w2[...,None]*uv[1]/distances[1]+w3[...,None]*uv[2]/distances[2])/z[...,None]')
    engines=[]
    for text in (code,perspective):
        engine={'__file__':str(p['SOURCE']/'render_preview.py')}
        exec(text,engine);engines.append(engine)
    frames={'idle':[0,.9,2.2,3.2,4.2,5.1,6],
            'walk':[0,.12,.5,.9,1.12,1.5,1.9],
            'leap_launch':[0,.18,.35,.42,.55,.63,.7],
            'leap_air':[0,.3,.65,.9,1.2,1.55,1.8],
            'leap_land':[0,.12,.3,.45,.8,1.35,2.2],
            'phase_change':[0,.65,1.48,1.6,1.9,2.7,3.2],
            'fan':[.4,.75,.9,1.3,1.7,1.9,2.25],
            'core_pulse':[.5,1.3,2.4,2.5,2.62,2.95,3.4],
            'slam':[.32,.78,1.075,1.24,1.3,1.7,2.3],
            'beam':[.4,1.2,1.6,1.72,2.2,2.8,3.3],
            'awaken':[0,.6,1.3,1.65,2.4,3.2,3.8]}
    OUT.mkdir(parents=True,exist_ok=True)
    for name,times in frames.items():
        size=360;sheet=Image.new('RGB',(size*len(times),2*(size+50)),(239,237,232))
        draw=ImageDraw.Draw(sheet)
        for row,(r,view) in enumerate(zip(engines,['3/4','Eye 1.8 blocks'])):
            for col,t in enumerate(times):
                r['mesh']=p['moved'](clips['animation.fractured_guardian.'+name],t)
                yaw,pitch=(-32,14) if row==0 else (0,-12.4)
                sheet.paste(r['render'](yaw,pitch,size),(col*size,row*(size+50)))
                draw.text((col*size+12,row*(size+50)+size+10),f'{name}  {t:.3f}s | {view}',font=r['font'](17),fill=(32,48,38))
        sheet.save(OUT/f'{name}-review.png')
    print('Guardian 3/4 and 1.8-block perspective sheets:',OUT)


if __name__=='__main__':render()
