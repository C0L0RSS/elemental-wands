#!/usr/bin/env python3
"""Export all three Hollow undead art candidates and review page; no runtime installation."""
import base64
import importlib.util
import json
import math
import sys
import random
from PIL import Image
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE / 'candidate'
PREVIEW = ROOT / '.local-previews/undead'


def make_variant(index):
    # Reuse the skull's approved pixel material/UV authoring functions, in an
    # isolated module instance; its output function is never invoked.
    source = HERE.parent / 'soul_bolt/build_preview.py'
    spec = importlib.util.spec_from_file_location('soul_palette', source)
    art = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(art)
    art.BONES.clear()
    art.CUBES.clear()

    def bone(name, pivot, parent=None):
        entry = {'name': name, 'pivot': pivot, 'cubes': []}
        if parent:
            entry['parent'] = parent
        art.BONES.append(entry)
        return len(art.BONES) - 1

    def cube(name, owner, center, size, material='bone'):
        art.cube(name, owner, center, size, material, cracks=material == 'bone')

    root = bone('root', [0, 0, 0])
    torso = bone('torso', [0, 0, 0], 'root')
    # Adult skeleton proportions, laid prone: chest to pelvis is twelve pixels.
    for i in range(7):
        z = -3 + i*2
        y = 6.4 - i*.65
        cube('vertebra', torso, [0,y,z], [1.8,1.6,1.7])
    for i in range(4):
        z=-3+i*2
        y=6.3-i*.5
        for side in (-1,1):
            cube('rib back',torso,[side*2,y,z],[3.1,1,1])
            cube('rib side',torso,[side*3.5,y-1.3,z],[1,2.8,1])
            cube('rib underside',torso,[side*2.5,y-2.5,z],[2.5,.8,1])
    for side in (-1,1):
        cube('pelvic wing',torso,[side*2.1,2,8.5],[2.5,2,2.6])
        cube('hip socket',torso,[side*2.7,1.1,9.3],[1.2,1.4,1.4])
    cube('sacrum',torso,[0,2.2,9],[1.8,1.5,2.5])
    cube('soul heart',torso,[0,4.8,-1],[.8,1.2,.8],'soul')
    skull=bone('skull',[0,5.8,-4.8],'torso')
    # Stepped cranial vault, recessed orbits and a tapered jaw, rather than a cube head.
    cube('cranial dome',skull,[0,9,-5.8],[4.4,1.3,4.1])
    cube('cranial middle',skull,[0,8,-5.7],[5.5,1.5,4.8])
    cube('occiput',skull,[0,6.8,-4.5],[4.8,2,2.5])
    cube('orbital shadow',skull,[0,6.6,-7.3],[4.7,2,.2],'socket')
    for side in (-1,1):
        cube('brow arch',skull,[side*1.4,7.6,-8],[2.4,.7,.8])
        cube('orbital rim',skull,[side*2.4,6.5,-7.8],[.55,1.7,.65])
        cube('cheek arch',skull,[side*1.9,5.7,-7.7],[1.4,.65,.8])
        cube('temporal bone',skull,[side*2.3,6.6,-6.5],[.65,1.8,1.5])
        cube('eye ember',skull,[side*1.35,6.6,-7.5],[.32,.35,.12],'soul')
        cube('maxilla',skull,[side*.65,5.4,-7.9],[.9,.9,.8])
    cube('nasal bridge',skull,[0,6.9,-8],[.55,1,.7])
    jaw=bone('jaw',[0,5.7,-5.3],'skull')
    for side in (-1,1):
        cube('jaw ramus',jaw,[side*1.75,4.9,-6.2],[.6,1.7,1.9])
        cube('jaw angle',jaw,[side*1.3,4.2,-7],[1.2,.6,1.8])
    cube('chin',jaw,[0,4.2,-7.7],[2.1,.65,.8])
    for x in (-1.25,-.75,-.25,.25,.75,1.25):
        cube('upper tooth',skull,[x,4.9,-8],[.38,.45,.5])
        cube('lower tooth',jaw,[x,4.6,-7.9],[.36,.35,.5])
    for side,label in ((-1,'left'),(1,'right')):
        cube('clavicle',torso,[side*3.2,5.5,-3],[2.8,.8,1])
        # Independently positioned segments share IK endpoints. Elbows bend in an
        # outward-facing plane; the wrist stays flat during the planted pull.
        arm=bone(label+'_arm',[0,0,0],'root')
        cube('humerus',arm,[0,-3,0],[1,6,1])
        cube('elbow condyle',arm,[0,-5.7,0],[1.45,1,1.4])
        fore=bone(label+'_forearm',[0,0,0],'root')
        for offset in (-.34,.34):
            cube('radius and ulna',fore,[offset,-2.75,0],[.42,5.5,.65])
        hand=bone(label+'_hand',[0,0,0],'root')
        for f in range(4):
            # Index beside the thumb and pinky outermost, mirrored per side.
            fx=side*(f-1.5)*.55
            cube('metacarpal',hand,[fx,0,-.65],[.38,.55,1.3])
            length=(1.65,2,1.8,1.4)[f]
            z=-1.3;parent=label+'_hand'
            for joint,fraction in enumerate((.44,.33,.23)):
                name=label+'_finger_'+str(f)+'_'+str(joint)
                digit=bone(name,[fx,0,z],parent)
                seg=length*fraction
                cube('phalange',digit,[fx,0,z-seg/2],[.34,.42,seg])
                cube('knuckle',digit,[fx,0,z-.08],[.43,.5,.35])
                z-=seg;parent=name
        thumb=bone(label+'_thumb_0',[-side*.9,0,-.35],label+'_hand')
        cube('thumb base',thumb,[-side*1.15,0,-.7],[.45,.5,.8])
        tip=bone(label+'_thumb_1',[-side*1.15,0,-1.05],label+'_thumb_0')
        cube('thumb tip',tip,[-side*1.15,0,-1.4],[.4,.45,.7])
    # Explicit per-face rectangles keep skinny finger geometry pixel sharp.
    tw, x, y, row = 256, 0, 0, 0
    for _, raw, _, _ in art.CUBES:
        w, h, d = [max(1, math.ceil(v * 2)) for v in raw['size']]
        raw['uv'] = {}
        for face, (fw, fh) in {'west': (d, h), 'east': (d, h), 'north': (w, h),
                               'south': (w, h), 'up': (w, d), 'down': (w, d)}.items():
            if x + fw + 2 > tw:
                x, y, row = 0, y + row, 0
            raw['uv'][face] = {'uv': [x + 1, y + 1], 'uv_size': [fw, fh]}
            x += fw + 2
            row = max(row, fh + 2)
    th = 64
    while th < y + row:
        th *= 2
    texture, glow = Image.new('RGBA', (tw, th)), Image.new('RGBA', (tw, th))
    for number, (_, raw, material, _) in enumerate(art.CUBES):
        for face, region in raw['uv'].items():
            fx, fy = region['uv']
            fw, fh = region['uv_size']
            rng = random.Random(number * 117 + index * 3 + sum(map(ord, face)))
            for v in range(fh):
                for u in range(fw):
                    if material == 'soul':
                        color = art.SOUL[2 + int(rng.random() > .7)]
                        glow.putpixel((fx + u, fy + v), (*color, 255))
                    elif material == 'socket':
                        color = (35, 37, 34)
                    else:
                        base = 4 if face == 'up' else 2 if face in ('down', 'west') else 3
                        cluster = (u // 3 * 11 + v // 3 * 17 + number * 7) % 29
                        tone = max(0, min(5, base + (1 if cluster == 2 else -1 if cluster == 10 else 0)))
                        if fw > 3 and u == fw // 2 + v // 4 and v < fh * .6:
                            tone = 1
                        color = art.BONE[tone]
                    texture.putpixel((fx + u, fy + v), (*color, 255))
            for im in (texture, glow):
                for v in range(-1, fh + 1):
                    for u in range(-1, fw + 1):
                        if not (0 <= u < fw and 0 <= v < fh):
                            im.putpixel((fx + u, fy + v), im.getpixel((fx + min(fw - 1, max(0, u)), fy + min(fh - 1, max(0, v)))))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.hollow_crawler',
                        'texture_width': tw, 'texture_height': th,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4,
                        'visible_bounds_offset': [0, 1, 0]},
        'bones': art.BONES}]}
    # Every clip samples one pose function; limb targets are in root space and
    # the arm bones (children of root) are solved to reach them.
    def ease(v):
        v = max(0, min(1, v))
        return v*v*(3-2*v)
    def mix(a, b, t):
        return [a[i]+(b[i]-a[i])*t for i in range(len(a))] if isinstance(a, list) else a+(b-a)*t
    def norm(v):
        length=math.sqrt(sum(x*x for x in v))
        return [x/length for x in v]
    def rotation(v):
        d=norm(v)
        return [math.degrees(math.asin(max(-1,min(1,d[2])))),0,math.degrees(math.atan2(d[0],-d[1]))]
    def advance(q):
        half=math.floor(q*2)
        return (half+ease(q*2-half))*4
    def torso_point(q, pos, rot):
        # Same order as GeckoLib/viewer about the torso pivot: X, then Y, then Z (X/Y negated).
        ax, ay, az = -math.radians(rot[0]), -math.radians(rot[1]), math.radians(rot[2])
        x, y, z = q
        y, z = y*math.cos(ax)-z*math.sin(ax), y*math.sin(ax)+z*math.cos(ax)
        x, z = x*math.cos(ay)+z*math.sin(ay), -x*math.sin(ay)+z*math.cos(ay)
        x, y = x*math.cos(az)-y*math.sin(az), x*math.sin(az)+y*math.cos(az)
        return [x+pos[0], y+pos[1], z+pos[2]]
    def solve_arm(shoulder, hand, side, pole):
        delta=[hand[i]-shoulder[i] for i in range(3)]
        dist=math.sqrt(sum(v*v for v in delta));d=norm(delta)
        assert .55<dist<11.45, (shoulder, hand, dist)
        along=(6*6-5.5*5.5+dist*dist)/(2*dist)
        height=math.sqrt(max(0,36-along*along))
        projection=sum(pole[i]*d[i] for i in range(3))
        perpendicular=norm([pole[i]-projection*d[i] for i in range(3)])
        return [shoulder[i]+d[i]*along+perpendicular[i]*height for i in range(3)]
    def grip(flex, flight):
        return [-22-8*flex+26*flight, 34+13*flex-20*flight, 26+12*flex-16*flight]
    def walk_hand(q, side):
        offset=0 if side<0 else .5
        n=math.floor(q+offset);phase=q+offset-n
        contact=n-offset
        old_z=-advance(contact)-11.5
        next_z=-advance(contact+1)-11.5
        if phase<.68:
            return [side*4.5,.55,old_z+advance(q)],phase,0
        f=(phase-.68)/.32
        hand=[side*(4.5+.65*math.sin(f*math.pi)),.55+1.1*math.sin(f*math.pi)**2,old_z+(next_z-old_z)*ease(f)+advance(q)]
        return hand,phase,math.sin(f*math.pi)
    rest={side:walk_hand(0,side)[0] for side in (-1,1)}

    def base():
        return {'root':[0,0,0],'torso_pos':[0,0,0],'torso_rot':[0,0,0],'skull':[0,0,0],'jaw':[3,0,0],'hands':{}}
    def hand_state(pos, fingers, splay=5, thumb=(-12,20,28), rot=(0,0,0), side=1):
        return {'pos':pos,'fingers':fingers,'splay':splay,'thumb':list(thumb),'rot':[rot[0],side*(5+rot[1]),rot[2]]}

    def idle(t):
        w=t/4*math.tau;p=base()
        p['torso_pos']=[.08*math.sin(w),.12*math.sin(2*w),0]
        p['torso_rot']=[-1.2*math.sin(2*w),0,0]
        p['skull']=[-3+3*math.sin(2*w),14*math.sin(w),3*math.sin(w+1)]
        p['jaw']=[4+3*math.sin(3*w),0,0]
        for side in (-1,1):
            fingers=[grip(.5+.5*math.sin(w-f*.9),0) for f in range(4)]
            if side>0:
                # An impatient index-finger tap on the planted claw.
                fingers[0][0]-=14*max(0,math.sin(2*w))**6
            p['hands'][side]=hand_state(rest[side],fingers,side=side)
        return p

    def walk(t):
        q=t/4;p=base()
        p['root']=[0,0,-advance(q)]
        p['torso_pos']=[.22*math.sin(q*math.tau),0,0]
        look=[(0,[0,0,-2]),(1,[3,-3,0]),(2,[0,0,2]),(3,[3,3,0]),(4,[0,0,-2])]
        for (a,va),(b,vb) in zip(look,look[1:]):
            if a<=t<=b:p['skull']=mix(va,vb,ease((t-a)/(b-a)))
        p['jaw']=[2+4*math.sin(min(1,t/2.5)*math.pi),0,0]
        for side in (-1,1):
            pos,phase,flight=walk_hand(q,side)
            fingers=[grip(.5+.5*math.sin(phase*math.tau-f*.65),flight) for f in range(4)]
            p['hands'][side]=hand_state(pos,fingers,5+8*flight,(-12+18*flight,20+15*flight,28-15*flight),side=side)
        return p

    def rise(t):
        p=base();body=ease((t-.35)/1.35)
        p['root']=[0,-11*(1-body),0]
        p['torso_rot']=[-12*(1-body)*ease(t/.5),0,0]
        p['skull']=[-18*(1-body),6*math.sin(t*4),0]
        p['jaw']=[4+12*math.sin(min(1,t/1.3)*math.pi),0,0]
        for side in (-1,1):
            # Each claw bursts up out of the ground and slams down before the body hauls.
            planted=.5 if side>0 else .72
            home=[rest[side][0],.55,rest[side][2]]
            burst=ease((t-planted+.45)/.33)
            world=mix([side*4.6,-5,home[2]+3],[side*5,1.3,home[2]+1],burst)
            world=mix(world,home,ease((t-planted+.12)/.12))
            pos=[world[0],world[1]-p['root'][1],world[2]]
            flight=1-ease((t-planted)/.15)
            fingers=[grip(.5+.5*math.sin(t*5-f*.7),flight) for f in range(4)]
            p['hands'][side]=hand_state(pos,fingers,5+8*flight,side=side)
        return p

    def attack(t):
        # Rear up, rake the right claw down into the target (hit at ~0.58s), recover.
        p=base();rear=ease(t/.42);strike=ease((t-.44)/.16);rec=ease((t-.72)/.6)
        rx=mix(mix(0,-14,rear),7,strike);p['torso_rot']=[mix(rx,0,rec),0,0]
        p['torso_pos']=mix(mix(mix([0,0,0],[0,1.6,.4],rear),[0,-.3,-2.4],strike),[0,0,0],rec)
        p['skull']=[mix(mix(mix(0,-14,rear),10,strike),0,rec),0,0]
        p['jaw']=[mix(mix(mix(3,24,rear),2,ease((t-.56)/.06)),3,rec),0,0]
        left=mix(rest[-1],[-4.8,.55,-9],ease(t/.3));left[1]+=.8*math.sin(min(1,t/.3)*math.pi)
        left=mix(left,rest[-1],rec)
        p['hands'][-1]=hand_state(left,[grip(1,0) for f in range(4)],side=-1)
        struck=[3.6,.7,-15+1.2*ease((t-.6)/.12)]
        right=mix(mix(rest[1],[4.9,9.2,-6.5],rear),struck,strike)
        right=mix(right,rest[1],rec);right[1]+=2.2*math.sin(rec*math.pi)
        opened=[grip(.2,1) for f in range(4)]
        raked=[[-8,70,50] for f in range(4)]
        fingers=[mix(mix(mix(grip(.5,0),opened[f],rear),raked[f],strike),grip(.5,0),rec) for f in range(4)]
        pitch=mix(mix(mix(0,-45,rear),20,strike),0,rec)
        p['hands'][1]=hand_state(right,fingers,5+8*rear*(1-strike),rot=(pitch,0,0),side=1)
        return p

    def death(t):
        # A jolt, then the arms give out and the frame drops, rolls and settles.
        p=base();jolt=ease(t/.18);fall=ease((t-.2)/.6)
        bounce=.16*math.sin(max(0,min(1,(t-.8)/.35))*math.pi)
        p['torso_rot']=[mix(mix(0,-9,jolt),5,fall),0,9*fall]
        p['torso_pos']=[0,mix(mix(0,.6,jolt),-1,fall)+bounce,mix(0,.8,jolt)*(1-fall)]
        p['skull']=mix(mix([0,0,0],[-16,0,0],jolt),[16,24,26],fall)
        p['jaw']=[mix(mix(3,20,jolt),26,fall),0,0]
        for side in (-1,1):
            target=[-6.6,.5,-10.5] if side<0 else [6.8,.5,-6.8]
            pos=mix(rest[side],target,fall);pos[1]+=1.3*math.sin(min(1,t/.5)*math.pi)*(1-fall)
            fingers=[mix(grip(.5,.8*jolt),[-4,14+2*f,10],fall) for f in range(4)]
            p['hands'][side]=hand_state(pos,fingers,5+6*jolt,rot=(-15*jolt*(1-fall),12*fall,0),side=side)
        return p

    clips={}
    for name,length,loop,sampler in (('idle',4,True,idle),('walk',4,True,walk),('rise',1.9,False,rise),
                                      ('attack',1.4,False,attack),('death',1.6,False,death)):
        animation={'animation_length':length,'loop':loop,'bones':{}}
        def put(bone,kind,t,value):
            animation['bones'].setdefault(bone,{}).setdefault(kind,{})[str(round(t,4))]=[round(v,4) for v in value]
        for frame in range(round(length*30)+1):
            t=frame/30;p=sampler(t)
            put('root','position',t,p['root']);put('torso','position',t,p['torso_pos']);put('torso','rotation',t,p['torso_rot'])
            put('skull','rotation',t,p['skull']);put('jaw','rotation',t,p['jaw'])
            for side,label in ((-1,'left'),(1,'right')):
                h=p['hands'][side]
                shoulder=torso_point([side*4.4,5.5,-3],p['torso_pos'],p['torso_rot'])
                elbow=solve_arm(shoulder,h['pos'],side,[side,-.18,.1])
                put(label+'_arm','position',t,shoulder);put(label+'_arm','rotation',t,rotation([elbow[i]-shoulder[i] for i in range(3)]))
                put(label+'_forearm','position',t,elbow);put(label+'_forearm','rotation',t,rotation([h['pos'][i]-elbow[i] for i in range(3)]))
                put(label+'_hand','position',t,h['pos']);put(label+'_hand','rotation',t,h['rot'])
                for finger,angles in enumerate(h['fingers']):
                    for joint,angle in enumerate(angles):
                        put(label+'_finger_'+str(finger)+'_'+str(joint),'rotation',t,[angle,side*(finger-1.5)*h['splay'] if joint==0 else 0,0])
                thumb=h['thumb']
                put(label+'_thumb_0','rotation',t,[thumb[0],side*thumb[1],0]);put(label+'_thumb_1','rotation',t,[thumb[2],0,0])
        clips[name]=animation
    animations={'format_version':'1.8.0','animations':{'animation.hollow_crawler.'+k:v for k,v in clips.items()}}
    stem = 'hollow_crawler'
    (OUT / f'{stem}.geo.json').write_text(json.dumps(geo, indent=2) + '\n')
    (OUT / f'{stem}.animation.json').write_text(json.dumps(animations, indent=2) + '\n')
    texture.save(OUT / f'{stem}.png')
    glow.save(OUT / f'{stem}_glowmask.png')
    images = {name: 'data:image/png;base64,' + base64.b64encode((OUT / name).read_bytes()).decode()
              for name in (f'{stem}.png', f'{stem}_glowmask.png')}
    return {'geo': geo, 'clips': clips, 'images': images, 'stem': stem}


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    from upright import build
    crawler = make_variant(0)
    payload = {'variants': [crawler, build(crawler, OUT), build(crawler, OUT, brute=True)]}
    page = (HERE / 'viewer.template.html').read_text()
    page = page.replace('<!-- MODEL_DATA -->', '<script id="model-data" type="application/json">'
                        + json.dumps(payload).replace('</', '<\\/') + '</script>')
    page = page.replace('<!-- VIEWER -->', '<script type="module">' + (HERE / 'viewer.js').read_text() + '</script>')
    (PREVIEW / 'index.html').write_text(page)
    print(f'Hollow army: crawler, archer and brute candidates; preview {PREVIEW / "index.html"}')

