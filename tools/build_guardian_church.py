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
def build(restored, legacy=False):
    B.clear()
    # A garden precinct, cut corners and buried foundations rather than a stone box.
    for x in range(-16,17):
        for z in range(-10,43):
            corner=(abs(x)>13 and (z<-7 or z>39))
            for y in range(-3,0): put(x,y,z,'dirt')
            put(x,0,z,'grass_block')
            if not corner and (abs(x)<=13 and z>=10 or abs(x)<=3):
                put(x,0,z,'polished_andesite' if abs(x)>2 else 'polished_deepslate')
    if restored:
        for z in range(-9,42):
            for x in [-15,15]: put(x,0,z,'stone_bricks')
        for z in [-9,41]: box(-13,0,z,13,0,z,'stone_bricks')
    # Long hall: two tall side arcades, layered sills, inset lancets, and flying ribs.
    for side in [-1,1]:
        x=side*11
        box(x,1,12,x,17,38,'stone_bricks')
        for y in [1,2,10,17]:box(x, y,12,x,y,38,'polished_andesite')
        for z in [15,22,29,36]:
            # Five-block pointed opening. A narrower high clerestory sits above it.
            for dz in range(-2,3):
                top=9-abs(dz)
                box(x,3,z+dz,x,top,z+dz,'cyan_stained_glass')
                put(x,top+1,z+dz,'chiseled_stone_bricks')
                put(x,12,z+dz,'light_blue_stained_glass')
                if abs(dz)<2:put(x,13,z+dz,'light_blue_stained_glass')
                if dz==0:put(x,14,z,'cyan_stained_glass')
            # Outer piers are fluted and carry a diagonal stone arch to the nave.
            pierz=z-3
            box(side*14,1,pierz,side*14,8,pierz,'stone_bricks')
            for y in [1,7]:box(min(side*15,side*13),y,pierz,max(side*15,side*13),y,pierz,'chiseled_stone_bricks')
            for distance,y in [(14,9),(13,10),(12,11),(11,12)]:put(side*distance,y,pierz,'polished_andesite')
            box(side*11,1,pierz,side*11,19,pierz,'polished_diorite')
            for y in [2,10,18]:box(min(side*12,side*10),y,pierz,max(side*12,side*10),y,pierz,'chiseled_stone_bricks')
            put(side*11,20,pierz,'stone_brick_wall')
    # Facade: deeply recessed entrance and three soaring window arches.
    box(-11,1,11,11,21,12,'stone_bricks')
    for x in range(-11,12):
        peak=28-abs(x)
        if peak>=22:box(x,22,11,x,peak,12,'stone_bricks')
        put(x,max(22,peak)+1,11,'polished_andesite')
    # Two concentric stepped entrance archivolts (void stays wide enough to walk).
    for x in range(-4,5):
        top=10-abs(x)//2
        box(x,1,10,x,top,12,'air')
        put(x,top+1,10,'polished_diorite');put(x,top+2,11,'chiseled_stone_bricks')
    for x in [-5,5]:
        box(x,1,10,x,9,10,'polished_diorite')
        box(x,1,9,x,1,11,'chiseled_stone_bricks')
    for cx in [-7,0,7]:
        for dx in range(-1,2):
            top=22-abs(dx)*2 if cx==0 else 19-abs(dx)*2
            box(cx+dx,13,11,cx+dx,top,12,'cyan_stained_glass' if dx==0 else 'light_blue_stained_glass')
            put(cx+dx,top+1,10,'polished_diorite')
        for dx in [-2,2]:box(cx+dx,12,10,cx+dx,18 if cx else 21,10,'polished_andesite')
    # Angular pinnacles replace crosses; glyphs are hollow, interrupted rings.
    for cx in [-11,11]:
        for y in range(1,25):
            for dx,dz in [(0,0),(-1,0),(1,0),(0,-1),(0,1)]:put(cx+dx,y,11+dz,'stone_bricks' if dx else 'polished_diorite')
        for y in [1,9,17,24]:box(cx-2,y,9,cx+2,y,13,'chiseled_stone_bricks')
        for y in range(25,29):
            r=1 if y<27 else 0;box(cx-r,y,11-r,cx+r,y,11+r,'polished_deepslate')
        put(cx,29,11,'sea_lantern');put(cx,30,11,'stone_brick_wall')
    # Large broken-ring emblem in the high facade. No real-world religious symbols.
    for dx in range(-3,4):
        for dy in range(-3,4):
            d=dx*dx+dy*dy
            if 5<=d<=10 and not (dx>0 and dy>0):put(dx,25+dy,10,'oxidized_copper')
    put(0,25,10,'sea_lantern')
    # Faceted roof and visible vault ribs: full form only exists after victory.
    for x in range(-11,12):
        y=25-abs(x)//2
        box(x,y,13,x,y,39,'deepslate_tiles')
        if x%3==0:box(x,y+1,13,x,y+1,39,'polished_deepslate')
    box(0,27,13,0,27,39,'cut_copper')
    # Rear sanctuary screen with three tall lancets and a radial ceilingless altar recess.
    box(-11,1,38,11,20,38,'stone_bricks')
    for x in range(-11,12):box(x,21,38,x,25-abs(x)//2,38,'stone_bricks')
    for cx in [-6,0,6]:
        for dx in [-1,0,1]:box(cx+dx,5,38,cx+dx,17-abs(dx)*2,38,'cyan_stained_glass')
        for dx in [-2,2]:box(cx+dx,3,37,cx+dx,17,37,'polished_diorite')
    # Freestanding pillars and pointed ribs frame a spacious nave.
    for z in [17,24,31]:
        for x in [-7,7]:
            box(x-1,1,z-1,x+1,1,z+1,'chiseled_stone_bricks')
            box(x,2,z,x,12,z,'quartz_pillar')
            for y in [3,11,13]:box(x-1,y,z,x+1,y,z,'chiseled_quartz_block')
        for x in range(-6,7):put(x,19-abs(x),z,'polished_diorite')
    # Low ceremonial stone seating and an original circular hearth motif.
    for z in [19,23,27,31]:
        for side in [-1,1]:
            a,b=sorted([side*3,side*5]);box(a,1,z,b,1,z,'smooth_stone_slab')
    box(-4,1,34,4,1,36,'polished_diorite')
    for x,z in [(-2,35),(-1,34),(0,33),(1,34),(2,35),(1,36),(-1,36)]:put(x,2,z,'chiseled_quartz_block')
    put(0,2,35,'sea_lantern');put(-5,1,35,'chest');put(5,1,35,'chest')
    # Lit hanging crystals along the restored hall, abstract and restrained.
    for x in [-9,9]:
        for z in [18,25,32]:put(x,10,z,'sea_lantern')
    # A carved effigy, built entirely from ordinary blocks. The living boss arrives later.
    box(-4,1,1,4,1,6,'stone_bricks')
    for side in [-1,1]:
        x=side*2
        box(x-1,2,2,x,2,5,'polished_andesite')  # broad feet
        box(x,3,3,x,4,4,'stone_bricks')
        put(x,3,2,'chiseled_stone_bricks')
        put(side*3,2,1,'stone_brick_stairs')
        box(side*4,3,2,side*4,4,3,'chiseled_stone_bricks') # low heavy fists
        box(side*4,5,3,side*4,6,3,'stone_brick_wall')
        box(min(side*4,side*2),7,3,max(side*4,side*2),7,5,'chiseled_stone_bricks')
        box(min(side*4,side*2),8,3,max(side*4,side*2),8,4,'stone_brick_slab')
        put(side*3,6,3,'mossy_stone_bricks')
    box(-2,5,3,2,6,5,'stone_bricks')
    box(-1,4,3,1,4,4,'polished_andesite')
    box(-1,6,2,1,6,2,'chiseled_stone_bricks')
    put(0,5,2,'chiseled_deepslate') # dormant core; the usable socket stays within reach below
    box(-1,7,3,1,8,4,'polished_andesite')
    box(-1,9,2,1,9,4,'stone_brick_slab') # overhanging brow
    put(-1,8,2,'cyan_terracotta');put(1,8,2,'cyan_terracotta')
    put(0,7,2,'chiseled_deepslate')
    if not restored:
        put(3,8,3,'air');put(4,8,4,'air');put(-2,2,2,'cracked_stone_bricks')
        put(2,6,3,'mossy_stone_bricks')
    put(0,2,0,'elementalwands:guardian_socket');put(-5,1,0,'chest')
    if not restored:
        # Centuries of collapse: no roof, no glass, mostly grass, disconnected masonry.
        for p,b in list(B.items()):
            x,y,z=p
            if z<9:continue # Preserve the readable ritual and statue courtyard.
            keep=True
            if y<0:continue
            if y==0:
                # Broken pavement islands suggest the old aisle without a pristine platform.
                if ((x*13+z*7)%19<12 and abs(x)>1) or (abs(x)<=1 and z%9<3):B[p]='minecraft:grass_block'
                continue
            if 'glass' in b or b in ['minecraft:deepslate_tiles','minecraft:polished_deepslate','minecraft:cut_copper','minecraft:sea_lantern','minecraft:oxidized_copper']:keep=False
            elif z<=13:
                # A narrow asymmetric facade remnant: central arch broken open to the sky.
                cap=18+(x*3+z)%5 if x<-6 else (9+(x+z)%4 if x>7 else 5+(x+2*z)%4)
                keep=y<=cap
            elif z>=37:
                # Two torn rear wall ends, large open middle with empty lancet outlines.
                cap=14+(x*7+z)%5 if x>5 else (7+(x+z)%4 if x<-6 else 2+(x+z)%3)
                keep=y<=cap
            elif abs(x)>=10:
                # Only the left first bays and far right end survive as high arches.
                cap=(13+(x+z)%5 if x<0 and z<24 else (10+(x+z)%4 if x>0 and z>29 else 1+(x+2*z)%4))
                keep=y<=cap
            else:
                # Pillars survive at different heights; vaults, roof and seating are gone.
                col=next(((cx,cz) for cx in [-7,7] for cz in [17,24,31] if abs(x-cx)<=1 and abs(z-cz)<=1),None)
                if col:
                    cx,cz=col;cap={(-7,17):11,(7,17):3,(-7,24):4,(7,24):8,(-7,31):6,(7,31):2}[col];keep=y<=cap
                else:keep=False
            if not keep:B.pop(p,None)
            elif b=='minecraft:stone_bricks':B[p]='minecraft:mossy_stone_bricks' if (x+y*3+z)%4 else 'minecraft:cracked_stone_bricks'
            elif b in ['minecraft:polished_diorite','minecraft:quartz_pillar','minecraft:chiseled_quartz_block']:B[p]='minecraft:andesite' if (x+y+z)%3 else 'minecraft:mossy_stone_bricks'
        # Fallen column sections, eroded rubble fans, and low vegetation, never a new roof.
        for x,z,length in [(-6,27,5),(5,20,4),(8,33,3)]:
            for k in range(length):put(x,1,z+k,'chiseled_stone_bricks' if k%3==0 else 'andesite')
        for x,z in [(-10,25),(10,18),(-4,15),(3,30),(7,36),(-12,34),(12,26)]:
            for dx,dz,h in [(0,0,2),(1,0,1),(-1,1,1),(0,2,1)]:box(x+dx,1,z+dz,x+dx,h,z+dz,'mossy_cobblestone')
        for x,z in [(-14,19),(14,28),(-12,36),(8,24),(6,30),(-5,34)]:put(x,1,z,'oak_leaves')
        # Empty reward receptacles still occupy the fixed runtime coordinates.
        put(-5,1,35,'chest');put(5,1,35,'chest')
    if restored:
        # Pearl-white structural stone, cool carved shadows, and warm gilded seams.
        for p,b in list(B.items()):
            x,y,z=p
            if z<9 or y<1:continue
            if b=='minecraft:stone_bricks':B[p]='minecraft:quartz_bricks'
            elif b=='minecraft:polished_diorite':B[p]='minecraft:quartz_pillar'
            elif b=='minecraft:chiseled_stone_bricks':B[p]='minecraft:chiseled_quartz_block'
            elif b=='minecraft:polished_andesite':B[p]='minecraft:smooth_quartz'
            elif b=='minecraft:polished_deepslate':B[p]='minecraft:dark_prismarine'
            elif b=='minecraft:deepslate_tiles':B[p]='minecraft:warped_planks'
            elif b=='minecraft:cut_copper':B[p]='minecraft:gold_block'
            elif 'stained_glass' in b:
                colors=['blue','light_blue','cyan','purple','magenta','orange','yellow']
                B[p]='minecraft:'+colors[min(6,max(0,(y-3)//3))]+'_stained_glass'
        # Inlaid floor: broad pearl nave, a lapis/teal aisle, and gold-edged geometric rosettes.
        box(-9,0,13,9,0,36,'smooth_quartz')
        box(-2,0,10,2,0,36,'dark_prismarine')
        for x in [-3,3]:box(x,0,13,x,0,33,'cut_copper')
        for z in [17,25,32]:
            for x in range(-3,4):
                for dz in range(-3,4):
                    d=abs(x)+abs(dz)
                    if d==3:put(x,0,z+dz,'gold_block')
                    elif d<3:put(x,0,z+dz,'lapis_block' if d>0 else 'sea_lantern')
        # Outer rhythm: doubled fluted pilasters, three levels of capitals and jewel bands.
        for side in [-1,1]:
            for z in [12,19,26,33,38]:
                x=side*12
                box(x,2,z,x,16,z,'quartz_pillar')
                for y in [2,8,16]:
                    box(min(x,side*13),y,z-1,max(x,side*13),y,z+1,'chiseled_quartz_block')
                    put(side*13,y+1,z,'gold_block')
                put(side*13,18,z,'sea_lantern');put(side*13,19,z,'dark_prismarine')
            # Gallery frieze: amber accents and small trefoil-like stone teeth.
            for z in range(13,38):
                put(side*12,18,z,'smooth_quartz')
                if z%2==0:put(side*12,19,z,'chiseled_quartz_block')
                if z%7==1:put(side*12,17,z,'gold_block')
            for z in [15,22,29,36]:
                # Thin mullions split each high colored window into three lancets.
                for dz in [-1,1]:box(side*11,3,z+dz,side*11,6,z+dz,'quartz_pillar')
                put(side*12,11,z,'sea_lantern')
        # Layered front archivolts reach forward, alternating carved pearl and turquoise.
        for x in range(-5,6):
            top=12-abs(x)//2
            put(x,top,9,'chiseled_quartz_block')
            put(x,top+1,10,'dark_prismarine')
            if x%2==0:put(x,top+2,10,'gold_block')
        for x in [-6,6]:
            box(x,1,9,x,10,9,'quartz_pillar')
            box(x-1,1,9,x+1,1,9,'chiseled_quartz_block')
            put(x,11,9,'sea_lantern')
        # Stained-glass panels in the facade: coherent cool-to-warm gradients with gilded borders.
        for cx in [-7,0,7]:
            for dx in [-1,0,1]:
                top=22-abs(dx)*2 if cx==0 else 19-abs(dx)*2
                for y in range(13,top+1):
                    color=['cyan','light_blue','blue','purple','magenta','orange','yellow'][min(6,(y-13)//2)]
                    put(cx+dx,y,11,color+'_stained_glass');put(cx+dx,y,12,color+'_stained_glass')
                put(cx+dx,top+1,10,'gold_block')
            for dx in [-2,2]:put(cx+dx,12,10,'sea_lantern')
        # Jewel rose at the back is radial rather than a cross: concentric octagonal bands.
        for x in range(-5,6):
            for dy in range(-5,6):
                d=max(abs(x),abs(dy))+min(abs(x),abs(dy))//2
                if d<=5:
                    palette={0:'sea_lantern',1:'yellow_stained_glass',2:'orange_stained_glass',3:'magenta_stained_glass',4:'purple_stained_glass',5:'cyan_stained_glass'}
                    put(x,17+dy,38,palette[d])
                    if d==5:put(x,17+dy,39,'chiseled_quartz_block')
        # Gold-tipped teal pinnacles; central glowing stones sit within broken rings.
        for cx in [-11,11]:
            for y in [9,17,24]:
                for dx,dz in [(-2,0),(2,0),(0,-2),(0,2)]:put(cx+dx,y,11+dz,'gold_block')
            put(cx,28,11,'gold_block');put(cx,29,11,'sea_lantern');put(cx,30,11,'end_rod')
        # Roof bays alternate turquoise ribs and colorful narrow skylights.
        for z in [17,24,31,37]:
            for x in range(-10,11):
                y=25-abs(x)//2
                put(x,y+1,z,'dark_prismarine' if abs(x)%3 else 'gold_block')
            for x in [-5,-4,4,5]:
                y=25-abs(x)//2
                for dz in [-1,0,1]:put(x,y,z+dz,'cyan_stained_glass' if dz else 'light_blue_stained_glass')
        # Roof lantern crowns mirror the culture's interrupted-circle emblem.
        for z in [18,30]:
            box(0,27,z,0,28,z,'gold_block')
            for x,y in [(-2,29),(-2,30),(-1,31),(0,32),(1,31),(2,29),(1,28),(-1,28)]:put(x,y,z,'oxidized_copper')
            put(0,30,z,'sea_lantern')
        # A richly colored rear sanctuary, framed with illuminated quartz steps.
        box(-4,1,34,4,1,36,'chiseled_quartz_block')
        for x in [-4,4]:
            box(x,2,36,x,5,36,'quartz_pillar');put(x,6,36,'sea_lantern')
        for x,z in [(-2,35),(-1,34),(0,33),(1,34),(2,35),(1,36),(-1,36)]:put(x,2,z,'gold_block')
        put(0,2,35,'sea_lantern')
        # Flower borders outside the ceremonial route add living color to the restored precinct.
        for x in [-14,14]:
            for z in range(1,38,3):put(x,1,z,['allium','azure_bluet','blue_orchid'][(z//3)%3])
    if restored:
        # Approved ornate geometry, revised to common stone and inexpensive colored inlays.
        economical={
            'quartz_bricks':'stone_bricks', 'quartz_pillar':'polished_andesite',
            'smooth_quartz':'smooth_stone', 'chiseled_quartz_block':'chiseled_stone_bricks',
            'quartz_block':'stone_bricks', 'gold_block':'chiseled_stone_bricks',
            'lapis_block':'blue_terracotta', 'cut_copper':'orange_terracotta',
            'oxidized_copper':'cyan_terracotta',
        }
        for pos,block in list(B.items()):
            name=block.removeprefix('minecraft:')
            if name in economical:B[pos]='minecraft:'+economical[name]
    if not legacy:
        # Short stone footings are exposed only where the surrounding slope falls away.
        for x in range(-13,14):
            for z in range(10,40):
                if abs(x)==13 or z in (10,39):
                    for y in range(-3,0): put(x,y,z,'mossy_stone_bricks' if not restored else 'stone_bricks')
        # Move only the ritual group; the church, footprint and saved origin do not move.
        group={p:b for p,b in B.items() if p[1]>=1 and -5<=p[0]<=4 and 0<=p[2]<=6}
        for p in group: B.pop(p)
        for (x,y,z),b in group.items(): B[x,y,z-6]=b
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
                # Keep the natural courtyard terraces outside the level route/plinth.
                if z<9 and abs(x)>3 and not (-5<=x<=4 and -6<=z<=0):
                    continue # Omit the column: jigsaw placement does not ignore structure-void blocks.
                if b not in indices:indices[b]=len(palette);palette.append(compound(Name=(8,b),**({'Properties':(10,{'facing':(8,'north')})} if b.endswith('guardian_socket') else {})))
                d=compound(pos=(9,(3,[x+16,y+5,z+10])),state=(3,indices[b]))
                if b.endswith('guardian_socket'):d['nbt']=(10,{'id':(8,'elementalwands:guardian_socket'),'layout_version':(3,2)})
                blocks.append(d)
    raw=b'\x0a\0\0'+payload(10,compound(DataVersion=(3,4556),size=(9,(3,[33,36,53])),palette=(9,(10,palette)),blocks=(9,(10,blocks)),entities=(9,(10,[]))))
    return gzip.compress(raw,mtime=0)
def encoded(layout):return json.dumps([[*p,b] for p,b in sorted(layout.items())],separators=(',',':')).encode()+b'\n'
files={
 'src/main/resources/data/elementalwands/structure/guardian_church.nbt':nbt(RUIN),
 'src/main/resources/data/elementalwands/church/ruined.json':encoded(RUIN),
 'src/main/resources/data/elementalwands/church/restored.json':encoded(WHOLE),
 'src/main/resources/data/elementalwands/church/ruined_legacy.json':encoded(build(False,True)),
 'src/main/resources/data/elementalwands/church/restored_legacy.json':encoded(build(True,True)),
 'art/guardian_church/blocks.json':json.dumps({'ruined':[[*p,b] for p,b in RUIN.items()],'restored':[[*p,b] for p,b in WHOLE.items()]},separators=(',',':')).encode()+b'\n'
}
check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');args=check.parse_args()
for name,data in files.items():
    p=ROOT/name
    if args.check:
        assert p.exists() and p.read_bytes()==data, f'Drift: {name}'
    else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
print(f'Church layouts verified: {len(RUIN)} ruined / {len(WHOLE)} restored blocks' if args.check else 'Church layouts generated')
