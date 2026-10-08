"""Body-independent planted legs, five-neck posing and articulated wing motion."""
import math
import numpy as np
from fold import rotation,euler,frame
from anatomy import NECK_NAMES,NECK_LENGTH
from legs import solve_leg
HEAD_SOCKET=np.array([0.,8.,15.])

class Pose:
    def __init__(self,r):self.bones={b['name']:b for b in r.bones};self.world={};self.channels={}
    def put(self,name,R,target):
        b=self.bones[name];pivot=np.array(b['pivot']);G=np.eye(4);G[:3,:3]=R;G[:3,3]=np.array(target)-R@pivot
        parent=self.world.get(b.get('parent'),np.eye(4));local=np.linalg.inv(parent)@G
        self.channels[name]={'rotation':euler(local[:3,:3]),'position':(local[:3,3]-pivot+local[:3,:3]@pivot).tolist()};self.world[name]=G
    def local(self,name,angles):
        b=self.bones[name];p=np.array(b['pivot']);R=rotation(angles);L=np.eye(4);L[:3,:3]=R;L[:3,3]=p-R@p
        self.world[name]=self.world.get(b.get('parent'),np.eye(4))@L;self.channels[name]={'rotation':angles}
    def point(self,name,p):return (self.world[name]@np.r_[p,1])[:3]

def fold_wings(r,mantle=0):
    p=Pose(r)
    for side,label in ((-1,'right'),(1,'left')):
        pivot=np.array([side*18,79,-22]);spread=rotation([0,-side*mantle*3,side*mantle])
        def put(name,R,target):
            if '_primary_' in name:p.put(name,R,target)
            else:p.put(name,spread@R,pivot+spread@(np.array(target)-pivot))
        for joint,target,direction in [('shoulder',[side*18,79,-22],[0,-.14,.99]),('elbow',[side*21,73,21],[0,-.12,-.993]),('wrist',[side*20,69,5],[0,-.28,.96])]:
            x=np.array(direction)*side;x/=np.linalg.norm(x);y=np.array([side if joint=='elbow' else -side,0,0])
            put(f'{label}_wing_{joint}',np.column_stack((x,y,np.cross(x,y))),target)
        for group in ('secondaries','primaries'):
            name=f'{label}_{group}'
            if name in p.bones:p.world[name]=p.world[p.bones[name]['parent']]
        for name,f in r.feathers.items():
            if not name.startswith(label+'_') or not any(k in name for k in ('primary','secondary','covert','leading','alula')):continue
            nums=[int(v) for v in name.split('_') if v.isdigit()];i=nums[-1] if nums else 0
            if '_primary_' in name:target=[side*(18-i*.4),67-i*1.5+(i%3-1)*3,14+i*2+(i%3-1)*4];drop=.36+i*.02;direction=[-side*.34,-drop,math.sqrt(1-drop*drop-.34*.34)]
            elif '_secondary_' in name:target=[side*(18-i*.35),70-i*.65+(i%3-1)*2,-22+i*4.5+(i%3-1)*4];direction=[side*.02,-.37,.929]
            elif '_covert_' in name:
                row=nums[0];target=[side*(24+row-i*.55),80-row*5-i*.48-(0 if i%4==0 else 3),-25+i*4.0+row*6+(i%3-1)*5];direction=[side*.02,-.40,.916]
            elif '_leading_' in name:target=[side*(26-i*.4),84-i*.7+(2 if i%4==0 else -1),-27+i*4.6+(i%3-1)*4];direction=[0,-.42,.907]
            else:target=[side*23,74,8];direction=[0,-.3,.954]
            angles=p.bones[name]['cubes'][0]['rotation']
            put(name,frame(direction,[side,.12,0])@rotation(angles).T,target)
    return p.channels

def animations(r):
    clips={};folded=fold_wings(r);mantled=fold_wings(r,5)
    for clip,duration in [('perched',4),('hunched',4),('peck',1.6),('flight',2.4),('spread',4),('screech',3.6),('grab',2.4)]:
        channels={}
        def key(name,channel,t,value):channels.setdefault(name,{}).setdefault(channel,{})[f'{t:.3f}']=[round(float(v),4) for v in value]
        for f in range(round(duration*20)+1):
            t=f/20;phase=t/duration*math.tau;breath=math.sin(phase)
            perch=clip=='perched';hunch=clip in ('hunched','peck');fly=clip in ('flight','grab');call=math.sin(math.pi*(t-.3)/2.8) if clip=='screech' and .3<t<3.1 else 0
            p=Pose(r)
            pitch=-20 if perch else 10 if hunch else -3 if clip=='screech' else 0
            body_y=51 if perch else 64.2 if hunch else 58 if fly else 67
            body_z=15;compression=.40 if fly else 1.0 if hunch else 1
            angles=[-45,20,90,125,90] if perch or clip=='screech' else [85,120,140,130,90] if hunch else [90,90,90,90,90]
            head_pitch=0
            if fly:angles=[85,75,75,85,90];head_pitch=-4
            if clip=='peck':
                times=[0,.3,.5,.65,.85,1.25,1.6]
                body_y=float(np.interp(t,times,[64.2,60.2,68.2,66.2,64.2,64.2,64.2]))
                body_z=float(np.interp(t,times,[15,19,4,7,11,15,15]))
                pitch=float(np.interp(t,times,[10,14,16,14,11,10,10]))
                compression=float(np.interp(t,times,[1.0,.93,1.05,1.03,1.01,1.0,1.0]))
                necks=[[85,120,140,130,90],[80,120,135,122,90],[90,110,125,120,90],[85,110,128,122,90],[85,120,140,130,90],[85,120,140,130,90],[85,120,140,130,90]]
                angles=[float(np.interp(t,times,[a[i] for a in necks])) for i in range(5)]
                head_pitch=float(np.interp(t,times,[0,-8,14,12,0,0,0]))
            p.put('body',rotation([pitch,0,0]),[0,body_y+(breath*.5 if fly else breath*.15),body_z])
            start=p.point('body',p.bones['neck_base']['pivot'])
            if call:
                angles=[a-call*d for a,d in zip(angles,[5,15,24,26,20])];head_pitch=-12*call
            angles[-1]=90+head_pitch
            for i,name in enumerate(NECK_NAMES):
                R=rotation([angles[i]+(.5*breath if i==2 else 0),0,0]);p.put(name,R,start)
                if fly or hunch:p.channels[name+'_skin']={'scale':[1.25 if fly else 1,compression,1.30 if fly else 1]}
                start=start+R@np.array([0,NECK_LENGTH*compression,0])
            p.put('head',rotation([head_pitch,0,0]),start-rotation([head_pitch,0,0])@HEAD_SOCKET)
            # The head family remains rigid; only the approved jaw hinge opens.
            p.local('lower_beak',[55*call,0,0])
            if perch or hunch or clip=='screech':p.channels.update(mantled if hunch else fold_wings(r,4*call) if call else folded)
            else:
                for side,label in ((-1,'right'),(1,'left')):
                    p.local(label+'_wing_shoulder',[0,side*(-10 if fly else -4),side*(24*breath if fly else 0)])
                    p.local(label+'_wing_elbow',[0,side*(3+3*breath if fly else 3),side*(-10*math.sin(phase-.3) if fly else 0)])
                    p.local(label+'_wing_wrist',[0,side*2,side*(-7*math.sin(phase-.5) if fly else 0)])
                    for i in range(10):p.channels[f'{label}_primary_{i:02}']={'rotation':[0,side*(i-4.5)*(.35*breath if fly else 0),0]}
            # Every leg shares the same fixed-length zigzag; ground feet never move.
            for side,label in ((-1,'right'),(1,'left')):
                if fly:
                    grasp=math.sin(math.pi*t/duration)**2 if clip=='grab' else 0
                    hip=np.array([side*13.5,55,30+side*4])
                    foot=np.array([side*13.5,54-32*grasp,45+side*4-70*grasp])
                    solve_leg(p,label,hip,foot,100-155*grasp,[0,180*(1-grasp),0])
                else:
                    hip=p.point('body',[side*13.5,54,(6+side*5) if perch else (2+side*10)])
                    foot=[side*13.5,7,(3+side*5) if perch else (-10+side*10)]
                    angle=-80 if perch else -75 if hunch else -65
                    if clip=='peck':angle=float(np.interp(t,times,[-75,-82,-62,-68,-75,-75,-75]))
                    solve_leg(p,label,hip,foot,angle)
                for i in range(4):
                    a=(-20 if i==3 else 24) if perch else 12 if fly else (47 if i==3 else -47)
                    if clip=='grab':a=30-55*grasp
                    p.channels[f'{label}_toe_{i}_tip']={'rotation':[a,0,0]}
                    if clip=='grab':p.channels[f'{label}_toe_{i}_base']={'rotation':[0,(i-1)*12*grasp if i<3 else 0,0]}
            p.local('tail_fan',[-25 if perch else -23-pitch if hunch else -25 if clip=='screech' else 0,0,breath*.3])
            if hunch:p.channels['tail_fan']['scale']=[1,1,1.6]
            if fly:p.channels['tail_fan']['scale']=[1.12,1,1]
            for i in range(17):
                name=f'tail_{i:02}'
                if name in p.bones:p.channels[name]={'rotation':[0,(i-8)*(.3 if perch else -.3 if fly else -.4),0]}
            if fly:
                for name,feather in r.feathers.items():
                    if not name.startswith('tail_'):continue
                    v=np.array(feather['end'])-feather['start']
                    bind=rotation(p.bones[name]['cubes'][0]['rotation'])
                    level=frame([v[0],-2,v[2]],[0,1,0])
                    p.channels[name]={'rotation':euler(level@bind.T)}
            for name,cs in p.channels.items():
                for ch,value in cs.items():key(name,ch,t,value)
            key('root','position',t,[0,62 if perch else 20 if fly else -3.25,0])
        clips[clip]={'loop':True,'animation_length':duration,'bones':channels}
    return clips
