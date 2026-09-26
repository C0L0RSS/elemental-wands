"""Authored archer and brute candidates for the undead review workshop."""
import base64
import io
import json
import math
from PIL import Image

BONE=[(62,53,40),(104,92,70),(146,133,104),(177,165,136),(207,196,169),(231,222,195)]
PALETTES={'bone':BONE,'cloth':[(22,30,31),(35,46,45),(48,60,56),(66,77,69),(86,95,82),(108,114,97)],
          'wood':[(39,27,21),(60,41,28),(80,55,35),(105,75,45),(129,98,61),(156,123,79)],
          'metal':[(36,41,40),(57,65,61),(77,87,81),(97,108,98),(121,131,116),(144,154,137)],
          'string':[(105,101,80)]*3+[(171,167,138)]*3,'soul':[(72,214,222)]*6}

class Model:
    def __init__(self,stem): self.stem=stem;self.bones=[];self.parts=[];self.clips={}
    def bone(self,name,parent='root',pivot=(0,0,0)):
        b={'name':name,'pivot':list(pivot),'cubes':[]}
        if parent:b['parent']=parent
        self.bones.append(b);return name
    def cube(self,bone,center,size,material='bone',patches=None):
        c={'origin':[center[i]-size[i]/2 for i in range(3)],'size':list(size)}
        next(b for b in self.bones if b['name']==bone)['cubes'].append(c)
        self.parts.append((c,material,patches));return c
    def skull(self,source,offset,scale=1):
        # Preserve the crawler's actual approved geometry and every texture pixel.
        skin=Image.open(io.BytesIO(base64.b64decode(source['images']['hollow_crawler.png'].split(',')[1]))).convert('RGBA')
        glow=Image.open(io.BytesIO(base64.b64decode(source['images']['hollow_crawler_glowmask.png'].split(',')[1]))).convert('RGBA')
        for b in source['geo']['minecraft:geometry'][0]['bones']:
            if b['name'] not in ('skull','jaw'):continue
            pivot=[(b['pivot'][i]-[0,5.8,-4.8][i])*scale+offset[i] for i in range(3)]
            self.bone(b['name'],'torso' if b['name']=='skull' else 'skull',pivot)
            for c in b['cubes']:
                patches={}
                for f,r in c['uv'].items():
                    x,y=r['uv'];w,h=r['uv_size'];patches[f]=(skin.crop((x,y,x+w,y+h)),glow.crop((x,y,x+w,y+h)))
                center=[(c['origin'][i]+c['size'][i]/2-[0,5.8,-4.8][i])*scale+offset[i] for i in range(3)]
                self.cube(b['name'],center,[v*scale for v in c['size']],patches=patches)
    def export(self,out):
        width=512;x=y=row=0;regions=[]
        for n,(c,mat,patches) in enumerate(self.parts):
            w,h,d=[max(1,math.ceil(v*2)) for v in c['size']];c['uv']={}
            for face,(fw,fh) in {'west':(d,h),'east':(d,h),'north':(w,h),'south':(w,h),'up':(w,d),'down':(w,d)}.items():
                if patches:fw,fh=patches[face][0].size
                if x+fw+2>width:x=0;y+=row;row=0
                c['uv'][face]={'uv':[x+1,y+1],'uv_size':[fw,fh]}
                regions.append((n,mat,face,x+1,y+1,fw,fh,patches));x+=fw+2;row=max(row,fh+2)
        height=64
        while height<y+row:height*=2
        skin=Image.new('RGBA',(width,height));glow=Image.new('RGBA',(width,height))
        for n,mat,face,x,y,w,h,patches in regions:
            if patches:
                skin.paste(patches[face][0],(x,y));glow.paste(patches[face][1],(x,y))
            else:
                palette=PALETTES[mat]
                for v in range(h):
                    for u in range(w):
                        base=4 if face=='up' else 2 if face in ('down','west') else 3
                        cluster=(u//3*11+v//3*17+n*7)%29
                        tone=max(0,min(5,base+(1 if cluster==2 else -1 if cluster==10 else 0)))
                        if mat=='wood' and u%4==0:tone=max(0,tone-1)
                        if mat=='cloth' and v%5==0 and u%3:tone=max(0,tone-1)
                        color=(*palette[tone],255);skin.putpixel((x+u,y+v),color)
                        if mat=='soul':glow.putpixel((x+u,y+v),color)
            for im in (skin,glow):
                for v in range(-1,h+1):
                    for u in range(-1,w+1):
                        if not(0<=u<w and 0<=v<h):im.putpixel((x+u,y+v),im.getpixel((x+max(0,min(w-1,u)),y+max(0,min(h-1,v)))))
        geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+self.stem,'texture_width':width,'texture_height':height,'visible_bounds_width':5,'visible_bounds_height':5,'visible_bounds_offset':[0,1.5,0]},'bones':self.bones}]}
        animations={'format_version':'1.8.0','animations':{'animation.'+self.stem+'.'+k:v for k,v in self.clips.items()}}
        (out/(self.stem+'.geo.json')).write_text(json.dumps(geo,indent=2)+'\n')
        (out/(self.stem+'.animation.json')).write_text(json.dumps(animations,indent=2)+'\n')
        images={}
        for suffix,im in (('',skin),('_glowmask',glow)):
            name=self.stem+suffix+'.png';im.save(out/name);images[name]='data:image/png;base64,'+base64.b64encode((out/name).read_bytes()).decode()
        return {'stem':self.stem,'geo':geo,'images':images,'clips':self.clips}

def add(a,b):return [a[i]+b[i] for i in range(3)]
def sub(a,b):return [a[i]-b[i] for i in range(3)]
def mul(a,s):return [v*s for v in a]
def dot(a,b):return sum(a[i]*b[i] for i in range(3))
def norm(v):return mul(v,1/max(.00001,math.sqrt(dot(v,v))))
def lerp(a,b,t):return add(a,mul(sub(b,a),t))
def ease(t):t=max(0,min(1,t));return t*t*(3-2*t)
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def euler(x,y):
    # Z-Y-X rotation (GeckoLib/viewer signs) taking local X and Y onto x and y.
    z=cross(x,y)
    return [-math.degrees(math.atan2(y[2],z[2])),math.degrees(math.asin(max(-1,min(1,x[2])))),math.degrees(math.atan2(x[1],x[0]))]
def mat(r):
    # Matrix of a GeckoLib/viewer rotation: Z * Y * X with X and Y negated.
    ax,ay,az=-math.radians(r[0]),-math.radians(r[1]),math.radians(r[2])
    rx=[[1,0,0],[0,math.cos(ax),-math.sin(ax)],[0,math.sin(ax),math.cos(ax)]]
    ry=[[math.cos(ay),0,math.sin(ay)],[0,1,0],[-math.sin(ay),0,math.cos(ay)]]
    rz=[[math.cos(az),-math.sin(az),0],[math.sin(az),math.cos(az),0],[0,0,1]]
    mm=lambda a,b:[[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mm(rz,mm(ry,rx))
def apply(m,v):return [sum(m[i][k]*v[k] for k in range(3)) for i in range(3)]
def compose(outer,inner):
    # Rotation angles for applying `inner` first, then the world rotation `outer`.
    a,b=mat(outer),mat(inner)
    return euler(apply(a,[b[0][0],b[1][0],b[2][0]]),apply(a,[b[0][1],b[1][1],b[2][1]]))
def rot(v):
    d=norm(v);return [math.degrees(math.asin(max(-1,min(1,d[2])))),0,math.degrees(math.atan2(d[0],-d[1]))]

def chain(start,end,lengths,pole):
    v=sub(end,start);dist=math.sqrt(dot(v,v));a,b=lengths
    assert abs(a-b)+.01<dist<a+b, (start,end,lengths,dist)
    d=norm(v);along=(a*a-b*b+dist*dist)/(2*dist)
    side=norm(sub(pole,mul(d,dot(pole,d))))
    joint=add(start,add(mul(d,along),mul(side,math.sqrt(max(0,a*a-along*along)))))
    return joint,rot(sub(joint,start)),rot(sub(end,joint))

class Clip:
    def __init__(self,length,loop=True):self.data={'animation_length':length,'loop':loop,'bones':{}}
    def put(self,bone,kind,t,v):self.data['bones'].setdefault(bone,{}).setdefault(kind,{})[str(round(t,4))]=v
    def pose(self,bone,t,p,r=(0,0,0)):self.put(bone,'position',t,p);self.put(bone,'rotation',t,list(r))

def build(source,out,brute=False):
    m=Model('hollow_brute' if brute else 'hollow_archer');m.bone('root',None);m.bone('torso')
    hip_y=16 if brute else 14.3
    shoulder_y=29 if brute else 25
    half=6.4 if brute else 4.4
    neck=[.7,29,-3.8] if brute else [0,26,-.8]
    # An open, tapering rib cage, paired scapulae, sternum and exposed vertebrae.
    for i in range(9 if brute else 8):
        y=hip_y+i*1.4;z=1.1-(i/(8 if brute else 7))**2*(4 if brute else 1.7)
        m.cube('torso',[.3*math.sin(i*.8) if brute else 0,y,z],[1.7 if brute else 1.2,1.15,1.6])
        if brute:m.cube('torso',[.3*math.sin(i*.8),y,z+1],[.7,.8,.8])
    for i in range(5):
        y=shoulder_y-1.5-i*1.55;w=(half-.6)*(1-i*.065);z=-1.9 if brute else -.15
        for side in (-1,1):
            m.cube('torso',[side*w*.52,y,z+1],[w,.85,1.0])
            m.cube('torso',[side*w,y-.2,z-.1],[.85,1.1,2.7])
            m.cube('torso',[side*w*.62,y-.45,z-1.6],[w*.9,.75,.85])
    m.cube('torso',[0,shoulder_y-4,-3.9 if brute else -2.2],[1.1,6,.9])
    for side in (-1,1):
        m.cube('torso',[side*half*.7,shoulder_y-2,.3 if brute else 1.2],[2.2,3,.65])
        m.cube('torso',[side*2.1,hip_y,0],[2.9,2.2,2.5])
        m.cube('torso',[side*2.3,hip_y-1.3,-.3],[1,1.1,2])
        m.cube('torso',[side*half*.55,shoulder_y,-1.8 if brute else -.3],[half,1,1.2])
    m.cube('torso',[0,shoulder_y-4,-1],[.55,1.7,.55],'soul')
    m.skull(source,neck,1.12 if brute else 1)
    # Sparse burial cloth, keeping anatomy visible.
    if brute:
        for i in range(4):m.cube('torso',[-half+.5+i*.9,shoulder_y-.7-i*.25,-1.1],[.9,1.2,4],'cloth')
        for i in range(4):m.cube('torso',[-3+i*1.2,hip_y-2-i%2,-1.4],[1.1,3.5+i%2,.45],'cloth')
        for i in range(3):m.cube('torso',[2.8,hip_y-.5-i*.6,.3],[.5,.35,3.1],'metal')
    else:
        for side in (-1,1):
            m.cube('skull',[side*2.8,28.3,-.1],[.55,3.2,3.7],'cloth')
        m.cube('skull',[0,30.1,-.1],[5.5,.55,3.8],'cloth')
        m.cube('skull',[0,28.4,1.7],[5.4,3.7,.5],'cloth')
        for i in range(4):m.cube('torso',[2.2+i*.45,24.8-i*.35,.1],[.9,1,4.2],'cloth')
        for i in range(3):m.cube('torso',[1.8+i*.8,22.6-i*.55,.9],[.75,3+i*.4,.45],'cloth')
        for i in range(5):m.cube('torso',[-3+i*1.3,hip_y-.5,-1.1],[1.2,.8,1.1],'cloth')
        for i in range(3):m.cube('torso',[-1+i,hip_y-2.4-i%2,-1.5],[.85,3.4+i%2,.4],'cloth')
        # Quiver behind the right shoulder, with three visible arrow fletchings.
        m.cube('torso',[2.5,22,2.8],[2,7,1.6],'wood')
        for i in range(3):
            m.cube('torso',[1.9+i*.6,26.5,2.8],[.18,4,.18],'wood')
            m.cube('torso',[1.9+i*.6,28,2.8],[.55,1,.3],'cloth')
    arm_lengths={};leg_lengths=(8,8) if brute else (7,7)
    for side,label in ((-1,'left'),(1,'right')):
        heavy=brute and side==1
        arm_lengths[label]=(8.3,8) if heavy else ((7.5,7) if brute else (6,5.5))
        upper,lower=arm_lengths[label];width=2.1 if heavy else (1.5 if brute else 1.05)
        m.bone(label+'_upper');m.cube(label+'_upper',[0,-upper/2,0],[width,upper,width])
        m.cube(label+'_upper',[0,-upper+.3,0],[width*1.3,1.2,width*1.3])
        m.bone(label+'_fore')
        for x in (-width*.3,width*.3):m.cube(label+'_fore',[x,-lower/2,0],[width*.42,lower,width*.65])
        if heavy:
            for i in range(3):m.cube(label+'_fore',[0,-2-i*.7,0],[2.6,.4,2],'metal')
        m.bone(label+'_hand');hw=1.5 if heavy else (1.2 if brute else 1)
        # Rest pose is palm forward: thumb and index on the outer side, pinky
        # toward the body, mirrored per side so each hand has its own chirality.
        for f in range(4):
            x=side*(1.5-f)*.53*hw
            m.cube(label+'_hand',[x,-.6,0],[.4*hw,1.2,.6])
            y=-1.1;parent=label+'_hand'
            length=(1.7,2,1.85,1.4)[f]*(1.15 if heavy else 1)
            for joint,fraction in enumerate((.44,.33,.23)):
                name=label+'_finger_'+str(f)+'_'+str(joint)
                m.bone(name,parent,(x,y,0));seg=length*fraction
                m.cube(name,[x,y-seg/2,0],[.36*hw,seg,.5])
                m.cube(name,[x,y-.07,0],[.44*hw,.35,.6])
                y-=seg;parent=name
        m.bone(label+'_thumb_0',label+'_hand',(side*.9*hw,-.4,0))
        m.cube(label+'_thumb_0',[side*1.05*hw,-.85,-.2],[.5*hw,.9,.55])
        m.bone(label+'_thumb_1',label+'_thumb_0',(side*1.05*hw,-1.2,-.2))
        m.cube(label+'_thumb_1',[side*1.05*hw,-1.55,-.2],[.45*hw,.7,.55])
        m.bone(label+'_thigh');m.cube(label+'_thigh',[0,-leg_lengths[0]/2,0],[1.8 if brute else 1.35,leg_lengths[0],1.5])
        m.cube(label+'_thigh',[0,-leg_lengths[0]+.3,-.2],[2 if brute else 1.5,1.2,1.7])
        m.bone(label+'_shin')
        for x in (-.35,.35):m.cube(label+'_shin',[x,-leg_lengths[1]/2,0],[.6 if brute else .45,leg_lengths[1],.8])
        m.bone(label+'_foot');m.cube(label+'_foot',[0,.05,-1],[2.2 if brute else 1.8,1,3])
        for i in range(3):m.cube(label+'_foot',[(i-1)*.6,-.08,-2.7],[.45,.7,1])
    if brute:
        # Local X runs through the closed palm; wrist rotation carries the whole club.
        # A long spiked maul: wrapped grip, banded shaft, iron-studded head.
        m.bone('club','right_hand');cy,cz=-1,-.85
        def ring(x,length,width,material='wood'):m.cube('club',[x,cy,cz],[length,width,width],material)
        ring(-2.5,1.1,1.5,'metal')
        ring(0,4,.9)
        for i in range(6):ring(-1.6+i*.65,.35,1.15,'cloth')
        ring(6.2,8.4,1.35)
        for x in (4.4,8.2):ring(x,.5,1.7,'metal')
        ring(10.6,1,3.2);ring(15,8,4.6);ring(19.5,1.2,3.6)
        for x in (11.8,18.2):ring(x,.7,5.2,'metal')
        # Face spikes on the outer rows, stepped diagonal spikes between them.
        for n,x in enumerate((13,15,17)):
            if n%2==0:
                for axis in (1,2):
                    for sign in (-1,1):
                        for offset,length,width in ((2.9,1.2,.9),(3.95,.9,.45)):
                            center=[x,cy,cz];size=[width,width,width]
                            center[axis]+=sign*offset;size[axis]=length
                            m.cube('club',center,size,'metal')
            else:
                for dy in (-1,1):
                    for dz in (-1,1):
                        m.cube('club',[x,cy+dy*2.45,cz+dz*2.45],[.9,.9,.9],'metal')
                        m.cube('club',[x,cy+dy*3.05,cz+dz*3.05],[.5,.5,.5],'metal')
        m.cube('club',[20.7,cy,cz],[1.2,.9,.9],'metal');m.cube('club',[21.7,cy,cz],[.9,.45,.45],'metal')
    if not brute:
        m.bone('bow')
        m.cube('bow',[0,-1,-.6],[.8,2,.8],'cloth')
        for side in (-1,1):
            for i in range(6):m.cube('bow',[0,-1+side*(1.6+i*1.1),-.8+(i/5)**2*2],[.65,1.35,.65],'wood')
        for name in ('string_top','string_bottom','arrow'):
            m.bone(name)
        m.cube('string_top',[0,-.5,0],[.13,1,.13],'string');m.cube('string_bottom',[0,-.5,0],[.13,1,.13],'string')
        m.cube('arrow',[0,-6.2,0],[.18,12.4,.18],'wood')
        m.cube('arrow',[0,-12.7,0],[.5,.8,.4],'metal')
        m.cube('arrow',[0,-.7,0],[.75,1,.18],'cloth')
    lengths={'idle':4,'walk':3.6 if brute else 3.2,'attack':4.6 if brute else 4,'rise':3.6,'death':1.7}
    for kind,duration in lengths.items():
        c=Clip(duration,loop=kind not in ('rise','death'));samples=round(duration*30);bow_release=None
        for frame in range(samples+1):
            t=frame/30;p=t/duration;wave=math.sin(p*math.tau)
            bob=.1*wave if kind=='idle' else 0
            lean=0;shift=0
            if kind=='attack' and brute:
                # Rock back into the windup, then drop the weight forward into the hit.
                wind=ease(t/1.4);slam=ease((t-1.7)/.45);recover=ease((t-2.6)/2)
                lean=(1.4*wind-3.2*slam)*(1-recover);bob=-2.4*slam*(1-recover)
            if kind=='walk':bob=-.25*abs(math.sin(p*math.tau));shift=(.32 if brute else .16)*wave
            if kind=='rise':bob=-12*(1-ease((t-.65)/2.4));lean=-1.5*(1-ease((t-.65)/2.4))
            c.pose('root',t,[0,-24*(1-ease(t/.9)) if kind=='rise' else 0,-8*p if kind=='walk' else 0])
            body_pos=[shift,bob,lean];body_rot=[0,0,0]
            if kind=='death':
                # Recoil, buckle onto the knees, then topple face down about the hips.
                hit=ease(t/.15)*(1-ease((t-.15)/.25));kneel=ease((t-.12)/.45);fall=ease((t-.45)/.7)
                bounce=.5*math.sin(max(0,min(1,(t-1.15)/.3))*math.pi)
                body_rot=[-6*hit+80*fall,0,6*fall]
                hips=[0,hip_y+((7.5 if brute else 6.5)-hip_y)*kneel,0]
                hips=lerp(hips,[0,(3.5 if brute else 2.6)+bounce,-2],fall)
                pivot=apply(mat(body_rot),[0,hip_y,0]);body_pos=sub(hips,pivot)
            torso_at=lambda q:add(apply(mat(body_rot),q),body_pos)
            c.pose('torso',t,body_pos,body_rot)
            c.put('skull','rotation',t,[3*wave if brute else 2*wave,2*math.sin(p*math.tau),-3 if brute else 0])
            c.put('jaw','rotation',t,[4+(7*max(0,math.sin(p*math.pi)) if kind=='attack' and brute else wave),0,0])
            if kind=='death':
                # The skull snaps back on the hit, then turns its face aside on the ground.
                c.put('skull','rotation',t,[-18*hit+10*fall,55*fall,18*fall])
                c.put('jaw','rotation',t,[4+18*ease(t/.2)-6*fall,0,0])
            hand_positions={}
            for side,label in ((-1,'left'),(1,'right')):
                shoulder=torso_at([side*half,shoulder_y+(.6 if brute and side==1 else 0),-2.7 if brute else -.3])
                if brute:
                    hand=[side*(half+.3),shoulder[1]-sum(arm_lengths[label])+.7,-3.5+lean]
                    if kind=='walk':hand[2]+=(1.7 if side<0 else .8)*wave*side
                    if kind=='attack' and side==1:
                        # The hand travels an arc around the shoulder, just outside
                        # the skull: bent and cocked high behind the head, then
                        # accelerating down and extending into the hit.
                        strike=max(0,min(1,(t-1.7)/.45))
                        phi=3+202*wind+5*ease((t-1.4)/.3);phi+=(32-phi)*strike*strike
                        reach=15.8-4.3*wind;reach+=(15.9-reach)*strike
                        wide=.3+1.9*wind;wide+=(.8-wide)*strike
                        phi+=(3-phi)*recover;reach+=(15.8-reach)*recover;wide+=(.3-wide)*recover
                        a=math.radians(phi)
                        hand=add(shoulder,[wide,-reach*math.cos(a),-reach*math.sin(a)])
                else:
                    # Side-on archery stance: load close to the body, then extend
                    # the bow arm while the drawing hand anchors beside the cheek.
                    grip=[-4.5,17.2,-3.5]
                    right=[4.8,15,-1.5]
                    if kind=='walk':grip[2]-=.65*wave;right[2]+=.8*wave
                    if kind=='attack':
                        lift=ease(t/.65);draw=ease((t-.65)/1.35)
                        lower=ease((t-3.15)/.85);release=ease((t-2.6)/.13)
                        loaded=[-4.3,22,-6]
                        grip=lerp(lerp(grip,loaded,lift),[-14,25,-3.8],draw)
                        grip=lerp(grip,[-4.5,17.2,-3.5],lower)
                        if t<.3:right=lerp(right,[5.5,23,-5.4],ease(t/.3))
                        elif t<.65:right=lerp([5.5,23,-5.4],[-.8,22,-5.4],ease((t-.3)/.35))
                        else:right=lerp([-.8,22,-5.4],[-.1,25.3,-1.7],draw)
                        # Release follows the cheek backward, then moves outward
                        # before lowering, keeping the forearm outside the rib cage.
                        right=lerp(right,[1.4,25.3,-1.7],release)
                        right=lerp(right,[5.2,25,-3.8],ease((t-2.78)/.37))
                        right=lerp(right,[4.8,15,-1.5],lower)
                        c.put('skull','rotation',t,[1,-80*ease(t/1.1)*(1-lower),0])
                    # A vertical bow handle lies across the curled fingers.
                    hand=add(grip,[1,0,.6]) if side<0 else right
                if kind=='death':
                    # Arms flail up as the knees go, then land along the sides; the
                    # brute's club arm sprawls forward with the club across the ground.
                    final={'left':[-8,1.2,-12],'right':[8.5,3.8,-27]} if brute else {'left':[-5.5,1,-8],'right':[5.5,1,-8]}
                    if not brute:hand=torso_at(hand)
                    hand=lerp(hand,final[label],fall);hand[1]+=3*math.sin(kneel*math.pi)*(1-fall)
                    reach=sub(hand,shoulder);span=math.sqrt(dot(reach,reach));lo,hi=abs(arm_lengths[label][0]-arm_lengths[label][1])+.1,sum(arm_lengths[label])*.97
                    hand=add(shoulder,mul(reach,max(lo,min(hi,span))/span))
                if kind=='rise':
                    rest=[side*(half+.3),shoulder_y-sum(arm_lengths[label])+.7,-3.5] if brute else ([-4.5,18.2,-3.5] if side<0 else [4.5,15,-1.5])
                    hand=lerp([side*5,3.5,-5],rest,ease((t-.65)/2.4))
                draw_roll=0
                # A hanging drawing arm bends its elbow back. Lifting the arrow swings
                # the elbow out and forward, clear of the ribs and skull, for the draw;
                # it swings back out and behind as the bow lowers.
                draw_pole=[side,.2,1]
                if kind=='attack':
                    draw_pole=lerp(draw_pole,[side,0,-1],ease(t/.3))
                    draw_pole=lerp(draw_pole,[side,.2,1],ease((t-3.15)/.85))
                # The drawing palm continues the forearm instead of folding back
                # at the wrist. Retarget the wrist around the same string contact.
                if not brute and side>0 and kind=='attack' and .45<=t<=3.1:
                    anchor=add(hand,[-1.7,0,-.6])
                    for _ in range(16):
                        e,_,_=chain(shoulder,hand,arm_lengths[label],draw_pole)
                        direction=sub(hand,e)
                        draw_roll=math.atan2(direction[2],-direction[0])*ease((t-.45)/.3)*(1-ease((t-2.78)/.32))
                        off=[-1.7*math.cos(draw_roll)-.6*math.sin(draw_roll),0,1.7*math.sin(draw_roll)-.6*math.cos(draw_roll)]
                        hand=lerp(hand,sub(anchor,off),.65)
                # The bow elbow stays below and outside the shoulder. A pole in
                # the reach plane becomes nearly parallel halfway through drawing
                # and makes the elbow swivel over the shoulder.
                pole=[side,.15,1] if brute else [-1,-1,0] if side<0 else draw_pole
                # The swinging elbow trails the arc: behind when low, forward overhead.
                if brute and side==1 and kind=='attack':pole=[.6,-math.sin(a),math.cos(a)]
                if kind=='death':pole=lerp(pole,[side,-1,.3],fall)
                elbow,ur,fr=chain(shoulder,hand,arm_lengths[label],pole)
                if not brute:
                    # Use a stable axis solution for each segment's motion plane.
                    # The bow forearm and drawing upper arm pass through the old
                    # solver's Z-axis singularity during extension and release.
                    axis=norm(sub(hand,elbow) if side<0 else sub(elbow,shoulder))
                    stable=[math.degrees(math.acos(max(-1,min(1,-axis[1])))),math.degrees(math.atan2(-axis[0],axis[2])),0]
                    if side<0:fr=stable
                    else:ur=stable
                c.pose(label+'_upper',t,shoulder,ur);c.pose(label+'_fore',t,elbow,fr)
                # Relaxed hands turn their palms toward the thighs, thumbs forward.
                # The bow palm faces forward around the handle with the thumb up.
                if not brute and side<0:wrist=[0,0,-90]
                elif not brute:
                    aim=ease(t/.65)*(1-ease((t-3.15)/.85)) if kind=='attack' else 0
                    wrist=[0,-90*(1-aim),-90*aim]
                else:wrist=[0,-side*90,0]
                if not brute and side>0 and kind=='attack':wrist[0]=math.degrees(draw_roll)
                if brute and side==1:
                    # The fist stays in line with the forearm, thumb side leading.
                    # The club angle comes only from wrist tilt in the arm's swing
                    # plane: 90 is square to the forearm, lower tips the head down.
                    fore=norm(sub(hand,elbow));swing=norm(cross([1,0,0],fore))
                    tilt=66+(4*wave if kind=='walk' else 0)
                    if kind=='attack':
                        # The wrist stays cocked through the swing and whips late.
                        tilt=66+(125-66)*wind+5*ease((t-1.4)/.3);tilt+=(48-tilt)*strike**3;tilt+=(66-tilt)*recover
                    a=math.radians(tilt)
                    club=add(mul(fore,math.cos(a)),mul(swing,math.sin(a)))
                    # A slight outward splay keeps the head clear of the body.
                    club=norm(add(club,[.2,0,0]))
                    fingers=sub(mul(fore,math.sin(a)),mul(swing,math.cos(a)))
                    fingers=norm(sub(fingers,mul(club,dot(fingers,club))))
                    if kind=='death':
                        # The club rolls out flat to the side of the outstretched arm.
                        club=norm(lerp(club,norm([1,0,-.35]),fall))
                        fingers=norm(sub(fore,mul(club,dot(fore,club))))
                    wrist=euler(club,mul(fingers,-1))
                elif kind=='death':wrist=compose(body_rot,wrist)
                c.pose(label+'_hand',t,hand,wrist)
                hand_positions[label]=hand
                for f in range(4):
                    ripple=.5+.5*math.sin(p*math.tau-f*.65)
                    if brute and side==1:
                        # Keep the club enclosed; tiny sequential squeezes tighten at windup.
                        squeeze=(wind*(1-recover) if kind=='attack' else .35*ripple)
                        curls=[-64-8*squeeze,-76-8*squeeze,-43-6*squeeze]
                    elif brute:
                        close=(ease(t/1.6)*(1-ease((t-2.5)/2.1)) if kind=='attack' else ripple)
                        curls=[-12-32*close,-15-46*close,-10-38*close]
                    elif side<0:
                        curls=[-60-5*ripple,-74-5*ripple,-40-5*ripple]
                    else:
                        tension=ease((t-.42)/.22)*(1-ease((t-2.6)/.1)) if kind=='attack' else .2*ripple
                        # Index and middle fingers hook the string, then open on release.
                        grip=tension if f<3 else tension*.35
                        curls=[-10-35*grip,-12-62*grip,-8-40*grip]
                    if kind=='death' and not (brute and side==1):
                        curls=lerp(curls,[-15-3*f,-20-4*f,-12],ease((t-.3)/.5))
                    for joint,angle in enumerate(curls):
                        c.put(label+'_finger_'+str(f)+'_'+str(joint),'rotation',t,[angle,0,side*(1.5-f)*(2 if (brute and side==1) or (not brute and side<0) else 6)*(1-.4*ripple) if joint==0 else 0])
                c.put(label+'_thumb_0','rotation',t,[-45 if side==1 and brute or side<0 and not brute else -20-15*wave,-side*30,-side*35])
                c.put(label+'_thumb_1','rotation',t,[-55 if side==1 and brute or side<0 and not brute else -25-15*wave,0,0])
                # Thighs stay attached to the pelvis when the torso shifts or tips.
                ankle=[side*(3.4 if brute else 2.5),.7,0]
                if kind=='walk':
                    offset=0 if side<0 else .5
                    n=math.floor(p+offset);ph=p+offset-n
                    contact=n-offset
                    old=-8*contact-3;new=old-8
                    ankle[2]=old+8*p if ph<.62 else old+(new-old)*ease((ph-.62)/.38)+8*p
                    ankle[1]+=(.65 if brute and side==1 else 1.1)*max(0,math.sin((ph-.62)/.38*math.pi)) if ph>=.62 else 0
                if kind=='attack' and brute:ankle[2]=-1.5 if side>0 else 1
                leg_pole=[side*.1,0,-1]
                hip=torso_at([side*(2.6 if brute else 2),hip_y,0])
                if kind=='death':
                    # Feet slide back as the knees drop, then the legs lie out straight.
                    ankle[2]=4*kneel
                    ankle[2]+=((13.2 if brute else 11.4)+bounce*.3-ankle[2])*fall
                    leg_pole=[side*(.1+.3*fall),-fall,-(1-fall)]
                knee,tr,sr=chain(hip,ankle,leg_lengths,leg_pole)
                c.pose(label+'_thigh',t,hip,tr);c.pose(label+'_shin',t,knee,sr);c.pose(label+'_foot',t,ankle)
            if not brute:
                # Weapon transform is derived from the bow hand contact, not
                # inherited from a palm orientation that points the bow sideways.
                grip=add(hand_positions['left'],[-1,0,-.6])
                bow_origin=add(grip,[.6,1,0]);tip=0
                if kind=='death' and t>=.3:
                    # Released at the recoil, the bow drops and tips flat beside the body.
                    bow_release=bow_release or bow_origin
                    drop=max(0,min(1,(t-.3)/.55))
                    bow_origin=lerp(bow_release,[-12,.4,1.5],drop*drop);tip=90*ease(drop)
                c.pose('bow',t,bow_origin,[0,-90,tip])
                spin=lambda v:add(bow_origin,[v[0]*math.cos(math.radians(tip))-v[1]*math.sin(math.radians(tip)),v[0]*math.sin(math.radians(tip))+v[1]*math.cos(math.radians(tip)),v[2]])
                relaxed=spin([1.2,-1,0])
                nock=add(hand_positions['right'],[-1.7*math.cos(draw_roll)-.6*math.sin(draw_roll),0,1.7*math.sin(draw_roll)-.6*math.cos(draw_roll)])
                tension=ease((t-.5)/.15)*(1-ease((t-2.6)/.1)) if kind=='attack' else 0
                center=lerp(relaxed,nock,tension)
                for part,y in (('string_top',7.1),('string_bottom',-7.1)):
                    endpoint=spin([1.2,y-1,0]);v=sub(center,endpoint)
                    c.pose(part,t,endpoint,rot(v));c.put(part,'scale',t,[1,math.sqrt(dot(v,v)),1])
                direction=norm(sub(grip,nock));arrowpos=nock[:]
                visible=kind=='attack' and .6<t<2.92
                if t>=2.6 and kind=='attack':
                    direction=norm(sub([-14,25,-3.8],[-1.8,25.3,-2.3]))
                    arrowpos=add([-1.8,25.3,-2.3],mul(direction,(t-2.6)*65))
                c.pose('arrow',t,arrowpos,rot(direction));c.put('arrow','scale',t,[1,1,1] if visible else [0,0,0])
        # Euler-equivalent angles must stay on the same branch for the
        # viewer/GeckoLib linear keyframe interpolation, including lowering.
        for name in ('left_hand','right_hand') if brute else ('left_upper','left_fore','right_upper','right_fore','left_hand','right_hand','string_top','string_bottom'):
            keys=c.data['bones'][name]['rotation'];previous=None
            for key in sorted(keys,key=float):
                value=keys[key]
                if previous is not None:
                    value=[previous[i]+(value[i]-previous[i]+180)%360-180 for i in range(3)]
                    keys[key]=value
                previous=value
        m.clips[kind]=c.data
    return m.export(out)
