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
    # A walk covers `stride` pixels per cycle at about the body's chase speed (archer 1.8,
    # brute 1.65 blocks/s); the game plays it at the travel speed, so planted feet stay put.
    stride=24 if brute else 22
    walk_length=(27 if brute else 23)/30
    # Idle, rise, attack and death are authored in brute.py / archer.py on the shared rig
    # (rig.py); only the walk is built here.
    import brute as brute_clips, archer as archer_clips
    from rig import Upright
    rig=Upright(brute,hip_y,shoulder_y,half,arm_lengths,leg_lengths)
    reworked=(brute_clips if brute else archer_clips).REWORKED
    c=Clip(walk_length);samples=round(walk_length*30)
    for frame in range(samples+1):
        t=frame/30;p=t/walk_length;wave=math.sin(p*math.tau)
        # Bent knees carry the long stride: the hips ride lowest as each foot lands.
        bob=-(2 if brute else 1.6)-(.6 if brute else .5)*(1+math.cos(2*p*math.tau))/2
        shift=(.32 if brute else .16)*wave
        c.pose('root',t,[0,0,-stride*p])
        # The upper body leans into the walk about the hips.
        body_rot=[7 if brute else 4,0,0]
        body_pos=add(sub([0,hip_y,0],apply(mat(body_rot),[0,hip_y,0])),[shift,bob,0])
        torso_at=lambda q:add(apply(mat(body_rot),q),body_pos)
        c.pose('torso',t,body_pos,body_rot)
        # The skull levels its gaze against the lean and nods with each step.
        c.put('skull','rotation',t,[-(5 if brute else 3)+2*math.sin(2*p*math.tau),4*wave,-3 if brute else 0])
        c.put('jaw','rotation',t,[4+wave,0,0])
        hand_positions={}
        for side,label in ((-1,'left'),(1,'right')):
            shoulder=torso_at([side*half,shoulder_y+(.6 if brute and side==1 else 0),-2.7 if brute else -.3])
            if brute:
                # Hanging arms follow the lean and swing against the legs; the club arm less.
                # The club elbow bends to carry the maul above the lowered hips.
                hand=torso_at([side*(half+.3),shoulder_y+(2.2 if side==1 else 0)-sum(arm_lengths[label])+.7,-3.5])
                hand[2]-=(2.8 if side<0 else 1)*side*math.cos(p*math.tau)
            else:
                # Each arm swings forward as the opposite foot lands; the bow arm less.
                # A vertical bow handle lies across the curled fingers.
                grip=[-4.5,17.2,-3.5];right=[4.8,15,-1.5]
                grip[2]+=1.2*math.cos(p*math.tau);right[2]-=2.6*math.cos(p*math.tau)
                hand=torso_at(add(grip,[1,0,.6]) if side<0 else right)
            # The bow elbow stays below and outside the shoulder; a hanging drawing arm
            # bends its elbow back.
            pole=[side,.15,1] if brute else [-1,-1,0] if side<0 else [side,.2,1]
            elbow,ur,fr=chain(shoulder,hand,arm_lengths[label],pole)
            if not brute:
                # Use a stable axis solution for the bow forearm and drawing upper arm.
                axis=norm(sub(hand,elbow) if side<0 else sub(elbow,shoulder))
                stable=[math.degrees(math.acos(max(-1,min(1,-axis[1])))),math.degrees(math.atan2(-axis[0],axis[2])),0]
                if side<0:fr=stable
                else:ur=stable
            c.pose(label+'_upper',t,shoulder,ur);c.pose(label+'_fore',t,elbow,fr)
            # Relaxed hands turn their palms toward the thighs, thumbs forward.
            # The bow palm faces forward around the handle with the thumb up.
            wrist=[0,0,-90] if not brute and side<0 else [0,-90,0] if not brute else [0,-side*90,0]
            if brute and side==1:
                # The fist stays in line with the forearm, thumb side leading.
                # The club angle comes only from wrist tilt in the arm's swing
                # plane: 90 is square to the forearm, lower tips the head down.
                fore=norm(sub(hand,elbow));swing=norm(cross([1,0,0],fore))
                a=math.radians(66+4*wave)
                club=add(mul(fore,math.cos(a)),mul(swing,math.sin(a)))
                # A slight outward splay keeps the head clear of the body.
                club=norm(add(club,[.2,0,0]))
                fingers=sub(mul(fore,math.sin(a)),mul(swing,math.cos(a)))
                fingers=norm(sub(fingers,mul(club,dot(fingers,club))))
                wrist=euler(club,mul(fingers,-1))
            c.pose(label+'_hand',t,hand,wrist)
            hand_positions[label]=hand
            for f in range(4):
                ripple=.5+.5*math.sin(p*math.tau-f*.65)
                if brute and side==1:
                    # Keep the club enclosed with tiny sequential squeezes.
                    squeeze=.35*ripple
                    curls=[-64-8*squeeze,-76-8*squeeze,-43-6*squeeze]
                elif brute:
                    curls=[-12-32*ripple,-15-46*ripple,-10-38*ripple]
                elif side<0:
                    curls=[-60-5*ripple,-74-5*ripple,-40-5*ripple]
                else:
                    tension=.2*ripple
                    grip=tension if f<3 else tension*.35
                    curls=[-10-35*grip,-12-62*grip,-8-40*grip]
                for joint,angle in enumerate(curls):
                    c.put(label+'_finger_'+str(f)+'_'+str(joint),'rotation',t,[angle,0,side*(1.5-f)*(2 if (brute and side==1) or (not brute and side<0) else 6)*(1-.4*ripple) if joint==0 else 0])
            c.put(label+'_thumb_0','rotation',t,[-45 if side==1 and brute or side<0 and not brute else -20-15*wave,-side*30,-side*35])
            c.put(label+'_thumb_1','rotation',t,[-55 if side==1 and brute or side<0 and not brute else -25-15*wave,0,0])
            # Thighs stay attached to the pelvis when the torso shifts or tips.
            ankle=[side*(3.4 if brute else 2.5),.7,0]
            offset=0 if side<0 else .5
            n=math.floor(p+offset);ph=p+offset-n
            contact=n-offset
            # The foot lands well ahead of the hip and pushes off well behind it.
            old=-stride*contact-(stride*.31+.4);new=old-stride
            ankle[2]=old+stride*p if ph<.62 else old+(new-old)*ease((ph-.62)/.38)+stride*p
            # The brute favors its club side with a lower, dragging step.
            ankle[1]+=(1.2 if brute and side==1 else 2)*max(0,math.sin((ph-.62)/.38*math.pi)) if ph>=.62 else 0
            hip=torso_at([side*(2.6 if brute else 2),hip_y,0])
            knee,tr,sr=chain(hip,ankle,leg_lengths,[side*.1,0,-1])
            c.pose(label+'_thigh',t,hip,tr);c.pose(label+'_shin',t,knee,sr);c.pose(label+'_foot',t,ankle)
        if not brute:
            # Weapon transform is derived from the bow hand contact, not
            # inherited from a palm orientation that points the bow sideways.
            grip=add(hand_positions['left'],[-1,0,-.6])
            bow_origin=add(grip,[.6,1,0]);tip=0
            c.pose('bow',t,bow_origin,[0,-90,tip])
            spin=lambda v:add(bow_origin,[v[0]*math.cos(math.radians(tip))-v[1]*math.sin(math.radians(tip)),v[0]*math.sin(math.radians(tip))+v[1]*math.cos(math.radians(tip)),v[2]])
            relaxed=spin([1.2,-1,0])
            nock=add(hand_positions['right'],[-1.7*math.cos(0)-.6*math.sin(0),0,1.7*math.sin(0)-.6*math.cos(0)])
            center=lerp(relaxed,nock,0)
            for part,y in (('string_top',7.1),('string_bottom',-7.1)):
                endpoint=spin([1.2,y-1,0]);v=sub(center,endpoint)
                c.pose(part,t,endpoint,rot(v));c.put(part,'scale',t,[1,math.sqrt(dot(v,v)),1])
            c.pose('arrow',t,nock[:],rot(norm(sub(grip,nock))));c.put('arrow','scale',t,[0,0,0])
    # Euler-equivalent angles must stay on the same branch for the
    # viewer/GeckoLib linear keyframe interpolation.
    for name in ('left_hand','right_hand') if brute else ('left_upper','left_fore','right_upper','right_fore','left_hand','right_hand','string_top','string_bottom'):
        keys=c.data['bones'][name]['rotation'];previous=None
        for key in sorted(keys,key=float):
            value=keys[key]
            if previous is not None:
                value=[previous[i]+(value[i]-previous[i]+180)%360-180 for i in range(3)]
                keys[key]=value
            previous=value
    m.clips={'idle':None,'walk':c.data,'attack':None,'rise':None,'death':None}
    # Reworked clips keep the walk's Euler forms at the ends that blend into other clips.
    walk=m.clips['walk']['bones']
    rig.reference={bone:ch['rotation'][min(ch['rotation'],key=float)] for bone,ch in walk.items() if 'rotation' in ch}
    for kind in reworked:m.clips[kind]=reworked[kind](rig)
    return m.export(out)
