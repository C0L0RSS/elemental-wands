"""Author the two real block layouts, worldgen NBT, and offline review geometry.
No spell textures are generated or modified. Run --check to detect asset drift.
"""
import gzip, json, struct, argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
B={}
def put(x,y,z,b):
    if b=='air': B.pop((x,y,z),None)
    else: B[x,y,z]=b if ':' in b else 'minecraft:'+b
def box(x1,y1,z1,x2,y2,z2,b):
    for x in range(x1,x2+1):
        for y in range(y1,y2+1):
            for z in range(z1,z2+1): put(x,y,z,b)
def build(restored):
    B.clear()
    # Raised masonry terrace; the forecourt is deliberately open to the sky.
    box(-16,-3,-10,16,-1,42,'stone_bricks')
    box(-16,0,-10,16,0,42,'smooth_stone')
    for x in range(-15,16):
        for z in range(-9,42):
            put(x,0,z,'polished_andesite' if abs(x)>3 else 'polished_deepslate')
    for z in [-10,42]: box(-16,1,z,16,1,z,'stone_brick_slab')
    for x in [-16,16]: box(x,1,-10,x,1,42,'stone_brick_slab')
    box(-4,1,-10,4,1,-10,'air')
    # Nave, apse, buttresses, and pointed window tracery.
    stone='stone_bricks' if restored else 'mossy_stone_bricks'
    box(-11,1,12,11,10,38,stone)
    box(-9,1,14,9,10,36,'air')
    for z in [16,23,30,36]:
        for side in [-1,1]:
            x=side*12
            box(x,1,z-1,x,6,z+1,'stone_bricks')
            box(side*11,7,z,side*11,12,z,'chiseled_stone_bricks')
            # Lancets sit in the side walls and remain true one-block geometry.
            for y in range(3,9):
                for dz in [-1,0,1]:
                    if y<7 or dz==0:
                        put(side*11,y,z+dz,'cyan_stained_glass' if dz==0 else 'light_blue_stained_glass')
    # Broad entrance with stepped pointed arch.
    box(-3,1,12,3,5,13,'air');box(-2,6,12,2,6,13,'air');box(-1,7,12,1,7,13,'air')
    for x in [-4,4]: box(x,1,11,x,6,11,'polished_diorite')
    for x,y in [(-3,7),(3,7),(-2,8),(2,8),(-1,9),(1,9),(0,10)]: put(x,y,11,'polished_diorite')
    # Steep tiled roof, stone ridge, raised gable ends.
    for x in range(-12,13):
        roof=19-abs(x)//2
        box(x,roof,11,x,roof,39,'deepslate_tiles')
        for z in [12,38]: box(x,11,z,x,roof-1,z,'stone_bricks')
    box(0,20,11,0,20,39,'polished_deepslate')
    # Rose window in rear gable, cyan/gold glass.
    for x in range(-3,4):
        for y in range(11,18):
            if abs(x)+abs(y-14)<=3: put(x,y,38,'yellow_stained_glass' if x==0 or y==14 else 'cyan_stained_glass')
    # Two front bell towers make the silhouette readable above surrounding trees.
    for c in [-9,9]:
        box(c-3,1,9,c+3,23,15,'stone_bricks')
        box(c-2,1,10,c+2,22,14,'air')
        for y in [7,15,23]: box(c-4,y,8,c+4,y,16,'chiseled_stone_bricks')
        for x in [c-3,c+3]:
            for z in [9,15]: box(x,2,z,x,24,z,'polished_diorite')
        box(c-1,17,9,c+1,21,9,'air');box(c-1,17,15,c+1,21,15,'air')
        box(c-3,17,11,c-3,21,13,'air');box(c+3,17,11,c+3,21,13,'air')
        put(c,21,12,'gold_block');put(c,20,12,'bell')
        for level in range(5):
            r=4-level
            box(c-r,24+level,12-r,c+r,24+level,12+r,'deepslate_tiles')
        box(c,29,12,c,32,12,'polished_diorite');box(c-1,31,12,c+1,31,12,'polished_diorite')
    # Interior columns, timber pews, ceremonial aisle, and altar.
    box(-9,0,14,9,0,36,'polished_diorite');box(-2,0,12,2,0,36,'polished_deepslate')
    for x in [-8,8]:
        for z in [18,25,32]:
            box(x,1,z,x,9,z,'quartz_pillar');box(x-1,10,z,x+1,10,z,'chiseled_quartz_block')
    for z in [19,23,27,31]:
        for side in [-1,1]:
            a,b=sorted([side*4,side*7]);box(a,1,z,b,1,z,'spruce_planks');box(a,2,z+1,b,2,z+1,'spruce_trapdoor')
    box(-4,1,34,4,1,36,'quartz_block');box(-2,2,35,2,2,36,'chiseled_quartz_block')
    put(0,3,36,'sea_lantern')
    # Two reward chests are empty in the ruins; inventories are assigned only on victory.
    put(-5,1,35,'chest');put(5,1,35,'chest')
    # Broken keeper, leaning slabs and fallen arm; socket remains exposed at the front.
    box(-3,1,1,3,1,5,'chiseled_stone_bricks')
    box(-2,2,2,-1,3,3,'mossy_stone_bricks');box(1,2,2,2,3,3,'cracked_stone_bricks')
    box(-2,4,2,2,5,3,'chiseled_stone_bricks');box(-1,6,2,1,6,3,'cracked_stone_bricks')
    box(-4,2,4,-4,3,6,'mossy_stone_bricks');put(3,2,5,'stone_brick_slab')
    put(0,2,0,'elementalwands:guardian_socket');put(-5,1,0,'chest')
    if not restored:
        # Deliberate collapsed right tower/roof, recognizable silhouette and ragged masonry.
        for p,b in list(B.items()):
            x,y,z=p
            if (x>=5 and y>12+(x+z)%4 and z>=8 and z<=16) or (y>=13 and 19<=z<=34 and x>=-3): B.pop(p,None)
            elif 14<=z<=38 and y>=4 and (x*17+y*31+z*7)%29<3 and abs(x)>=9: B.pop(p,None)
            elif b=='minecraft:cyan_stained_glass' and (x+y+z)%3: B.pop(p,None)
            elif b=='minecraft:stone_bricks' and y>=0:
                B[p]='minecraft:cracked_stone_bricks' if (x+y+z)%4==0 else 'minecraft:mossy_stone_bricks'
        for x,z,h in [(6,21,3),(4,26,2),(7,32,3),(-6,30,2)]:
            box(x,1,z,x+2,h,z+2,'mossy_cobblestone')
        # Vegetation belongs to the ruin template and disappears in restoration.
        for x,z in [(-14,19),(14,28),(-12,36),(8,24),(6,30)]: box(x,1,z,x+1,2,z+1,'oak_leaves')
    return {(x,y-2,z):b for (x,y,z),b in B.items()}
RUIN=build(False);WHOLE=build(True)

def string(s):
    b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
    if t==3:return struct.pack('>i',v)
    if t==8:return string(v)
    if t==9:
        kind,vs=v;return bytes([kind])+struct.pack('>i',len(vs))+b''.join(payload(kind,a) for a in vs)
    if t==10:return b''.join(bytes([k])+string(n)+payload(k,vv) for n,(k,vv) in v.items())+b'\0'
    raise ValueError(t)
def compound(**kw):return {k:v for k,v in kw.items()}
def nbt(layout):
    palette=[];indices={};blocks=[]
    for x in range(-16,17):
        for y in range(-5,31):
            for z in range(-10,43):
                b=layout.get((x,y,z),'minecraft:air')
                if b not in indices:indices[b]=len(palette);palette.append(compound(Name=(8,b),**({'Properties':(10,{'facing':(8,'north')})} if b.endswith('guardian_socket') else {})))
                d=compound(pos=(9,(3,[x+16,y+5,z+10])),state=(3,indices[b]))
                if b.endswith('guardian_socket'):d['nbt']=(10,{'id':(8,'elementalwands:guardian_socket')})
                blocks.append(d)
    raw=b'\x0a\0\0'+payload(10,compound(DataVersion=(3,4556),size=(9,(3,[33,36,53])),palette=(9,(10,palette)),blocks=(9,(10,blocks)),entities=(9,(10,[]))))
    return gzip.compress(raw,mtime=0)
def encoded(layout):return json.dumps([[*p,b] for p,b in sorted(layout.items())],separators=(',',':')).encode()+b'\n'
files={
 'src/main/resources/data/elementalwands/structure/guardian_church.nbt':nbt(RUIN),
 'src/main/resources/data/elementalwands/church/ruined.json':encoded(RUIN),
 'src/main/resources/data/elementalwands/church/restored.json':encoded(WHOLE),
 'art/guardian_church/blocks.json':json.dumps({'ruined':[[*p,b] for p,b in RUIN.items()],'restored':[[*p,b] for p,b in WHOLE.items()]},separators=(',',':')).encode()+b'\n'
}
check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');args=check.parse_args()
for name,data in files.items():
    p=ROOT/name
    if args.check:
        assert p.exists() and p.read_bytes()==data, f'Drift: {name}'
    else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
print(f'Church layouts verified: {len(RUIN)} ruined / {len(WHOLE)} restored blocks' if args.check else 'Church layouts generated')
