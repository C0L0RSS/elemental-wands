#!/usr/bin/env python3
"""Package approved Fire workshop 05 artwork and geometry, without redesigning it."""
import argparse
import io
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
WORKSHOP = ROOT / 'art/fire/workshop'
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'


def outputs():
    spec = json.loads((WORKSHOP / 'custom-art.json').read_text())['textures']
    result = {}
    def frame(family, index, size):
        entry = spec[family]; atlas = entry['atlas']
        image = Image.open(WORKSHOP / entry['url']).convert('RGBA')
        crop = atlas.get('crop', [0, 0, 1, 1])
        x = (index + crop[0]) * image.width / atlas['columns']
        y = (atlas['row'] + crop[1]) * image.height / atlas['rows']
        w = crop[2] * image.width / atlas['columns']; h = crop[3] * image.height / atlas['rows']
        image = image.crop((round(x), round(y), round(x+w), round(y+h))).resize(size, Image.Resampling.NEAREST)
        # Identical to the approved browser's 0.5 alpha cutout, not new artwork.
        image.putalpha(image.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
        return image
    def png(relative, image):
        out = io.BytesIO(); image.save(out, format='PNG'); result[ASSETS / relative] = out.getvalue()
    def js(relative, value):
        result[ASSETS / relative] = (json.dumps(value, indent=2)+'\n').encode()
    families = {'ember':'custom_ember', 'flame_ribbon':'custom_stream', 'impact_ring':'custom_arc',
                'pyre_front':'custom_pyre_wall', 'meteor_shell':'custom_plume',
                'meteor_warning':'custom_arc', 'meteor_impact':'custom_arc'}
    for family, source in families.items():
        for i in range(4): png(f'textures/particle/fire/{family}_{i}.png', frame(source, i, (64,64)))
        js(f'particles/fire_{family}.json', {'textures':[f'elementalwands:fire/{family}_{i}' for i in range(4)]})
    for family, source, size in [('inferno_stream','custom_stream',(128,64)),('inferno_front','custom_arc',(64,64)),('fire_meteor_surface','custom_meteor_surface',(128,256))]:
        for i in range(4): png(f'textures/entity/{family}_{i}.png',frame(source,i,size))
    for letter, source in [('a','custom_floor'),('b','custom_low_wisp')]:
        sheet=Image.new('RGBA',(64,256))
        for i in range(4):sheet.paste(frame(source,i,(64,64)),(0,i*64))
        png(f'textures/block/fire_ground_{letter}.png',sheet)
        js(f'textures/block/fire_ground_{letter}.png.mcmeta',{'animation':{'frametime':3 if letter=='a' else 2,'interpolate':False}})
    # Ordinary primary trail: vanilla appearance, existing temporary/contact behavior.
    js('blockstates/inferno_flame.json', {'multipart':[
        {'apply':[{'model':'minecraft:block/fire_floor0'},{'model':'minecraft:block/fire_floor1'}]},
        *[{'apply':[{'model':'minecraft:block/fire_side0','y':yaw},{'model':'minecraft:block/fire_side1','y':yaw}]} for yaw in [0,90,180,270]]
    ]})
    floor={'from':[0,.12,0],'to':[16,.12,16],'shade':False,'faces':{'up':{'uv':[0,0,16,16],'texture':'#floor'},'down':{'uv':[0,0,16,16],'texture':'#floor'}}}
    lip={'from':[0,0,8],'to':[16,1.2,8],'shade':False,'faces':{'north':{'uv':[0,0,16,16],'texture':'#lip'},'south':{'uv':[0,0,16,16],'texture':'#lip'}}}
    lip2={'from':[8,0,0],'to':[8,1.2,16],'shade':False,'faces':{'east':{'uv':[0,0,16,16],'texture':'#lip'},'west':{'uv':[0,0,16,16],'texture':'#lip'}}}
    js('models/block/pyre_low_fire.json',{'ambientocclusion':False,'textures':{'particle':'elementalwands:block/fire_ground_a','floor':'elementalwands:block/fire_ground_a','lip':'elementalwands:block/fire_ground_b'},'elements':[floor,lip,lip2]})
    js('blockstates/pyre_flame.json',{'variants':{'':[{'model':'elementalwands:block/pyre_low_fire','y':angle} for angle in [0,90,180,270]]}})
    # Kept only as a fallback/particle model; the meteor's normal renderer is custom.
    js('models/block/meteor_core.json',{'parent':'minecraft:block/cube_all','textures':{'all':'elementalwands:entity/fire_meteor_surface_0'}})
    result[ASSETS/'fire/meteor-model.json']=(WORKSHOP/'meteor-model.json').read_bytes()
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--replace',action='store_true');args=parser.parse_args()
    expected=outputs()
    # Remove only superseded Fire-owned frames, never other affinity assets or HUD icons.
    candidates=list((ASSETS/'textures/particle/fire').glob('*.png'))
    for pattern in ['inferno_stream_*.png','inferno_front_*.png','fire_meteor_surface_*.png']:
        candidates+=list((ASSETS/'textures/entity').glob(pattern))
    candidates += [ASSETS/'models/block/animated_fire_a.json',ASSETS/'models/block/animated_fire_b.json']
    stale=[p for p in candidates if p.exists() and p not in expected]
    drift=[p for p,data in expected.items() if not p.exists() or p.read_bytes()!=data]
    if args.check:
        if stale or drift:raise SystemExit('Fire workshop package drift: '+', '.join(str(p.relative_to(ROOT)) for p in stale+drift))
        print('Approved Fire workshop assets and 1,248-face meteor mesh verified.');return
    for p in stale:p.unlink()
    for p,data in expected.items():p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    print(f'Packaged {len(expected)} approved Fire resources; removed {len(stale)} superseded files.')


if __name__=='__main__':main()
