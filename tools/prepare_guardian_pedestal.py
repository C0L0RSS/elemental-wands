"""Export the approved September 14 pedestal geometry using existing vanilla textures.

Coordinates follow the reviewed 3D prototype. The bowl and support occupy separate
blocks so Minecraft's native raycast can select either half. No raster art changes.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
RUNE = [(-2,4),(-1,4),(0,4),(1,4),(-3,3),(2,3),(-4,2),(3,2),(-4,1),(3,1),
        (-4,0),(3,0),(-4,-1),(3,-1),(-3,-2),(2,-2),(-2,-3),(-1,-3),(0,-3)]
TEXTURES = {'particle':'minecraft:block/polished_andesite','stone':'minecraft:block/polished_andesite',
            'carved':'minecraft:block/chiseled_stone_bricks','dark':'minecraft:block/chiseled_deepslate',
            'white':'minecraft:block/white_concrete'}


def cube(elements, x, y, z, w, h, d, texture, tint=None, light=0):
    low = [(x-w/2)*16, (y-h/2)*16, (z-d/2)*16]
    high = [(x+w/2)*16, (y+h/2)*16, (z+d/2)*16]
    faces = {}
    # Actual texel density, never a whole block texture squeezed onto a small carving.
    for face in ['north','south','east','west','up','down']:
        a,b = (0,2) if face in ['up','down'] else (2,1) if face in ['east','west'] else (0,1)
        u,v = low[a] % 16, low[b] % 16
        du,dv = min(16,high[a]-low[a]), min(16,high[b]-low[b])
        u,v = min(u,16-du), min(v,16-dv)
        faces[face] = {'texture':'#'+texture, 'uv':[round(u,4),round(v,4),round(u+du,4),round(v+dv,4)]}
        if tint is not None: faces[face]['tintindex'] = tint
    elements.append({'from':[round(v,4) for v in low], 'to':[round(v,4) for v in high],
                     'faces':faces, 'light_emission':light})


def rune(elements,x,y,z,scale,lit=False,horizontal=False):
    for u,v in RUNE:
        if horizontal:
            cube(elements,x+u*scale,y,z+v*scale,.05,.008,.05,'white',0x62AFAC if not lit else 0x91ECE6,4 if not lit else 12)
        else:
            cube(elements,x+u*scale,y+v*scale,z,scale*.85,scale*.85,.012,'white',0x62AFAC if not lit else 0x91ECE6,3 if not lit else 12)


def support(lit):
    e=[]
    cube(e,.5,.09,.5,1.4,.18,1.4,'stone')
    cube(e,.5,.235,.5,1.18,.11,1.18,'carved')
    cube(e,.5,.615,.5,.64,.65,.64,'stone')
    for sx in [-1,1]:
        for sz in [-1,1]:cube(e,.5+sx*.32,.615,.5+sz*.32,.105,.65,.105,'dark')
    cube(e,.5,.985,.5,.85,.09,.85,'carved')
    rune(e,.535,.6,.168,.039,lit)
    return e


def bowl(lit):
    e=[]
    cube(e,.5,.085,.5,1.16,.11,1.16,'stone')
    cube(e,.5,.16,.5,.86,.055,.86,'dark')
    for row in range(-7,7):
        for col in range(-7,7):
            x,z=(col+.5)/16,(row+.5)/16
            radius=max(abs(x),abs(z))+min(abs(x),abs(z))*.45
            if .325<radius<.505:cube(e,.5+x,.275,.5+z,1/16,.22,1/16,'stone')
    rune(e,.535,.194,.46,.068,lit,True)
    if lit:
        # Seated faceted heart. Its lowest face sits just above the recess floor.
        for size in [(.31,.31,.31),(.4,.19,.19),(.19,.4,.19),(.19,.19,.4)]:
            cube(e,.5,.39,.5,*size,'white',0x53BDC2,10)
        cube(e,.43,.51,.339,.075,.075,.018,'white',0xC3F9DF,12)
    return e


def chest(lit):
    e=[]
    cube(e,.5,.5,.5,1,1,1,'dark')
    rune(e,.535,.44,-.009,.077,lit)
    return e


def model(elements):return {'ambientocclusion':True,'textures':TEXTURES,'elements':elements}


files={}
for name,builder in [('guardian_pedestal',support),('guardian_socket',bowl),('guardian_chest_rune',chest)]:
    for lit in [False,True]:
        files[f'models/block/{name}{"_lit" if lit else ""}.json']=model(builder(lit))
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        if name=='guardian_socket':
            for ritual in range(3):
                variants[f'facing={facing},pedestal=true,ritual={ritual}']={'model':f'elementalwands:block/{name}{"_lit" if ritual else ""}','y':rotation}
            variants[f'facing={facing},pedestal=false']={'model':'elementalwands:block/guardian_socket_legacy'}
        else:
            for lit in [False,True]:variants[f'facing={facing},lit={str(lit).lower()}']={'model':f'elementalwands:block/{name}{"_lit" if lit else ""}','y':rotation}
    files[f'blockstates/{name}.json']={'variants':variants}
files['models/block/guardian_socket_legacy.json']={'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/chiseled_deepslate'}}

parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
for name,data in files.items():
    path=ASSETS/name;encoded=json.dumps(data,separators=(',',':'))+'\n'
    if args.check:
        assert path.is_file() and path.read_text()==encoded, f'Pedestal asset drift: {path}'
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(encoded)
print(f'Guardian pedestal: {len(files)} native model/state files {"verified" if args.check else "exported"}; no PNGs changed.')
