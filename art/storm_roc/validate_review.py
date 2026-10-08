#!/usr/bin/env python3
"""Check the actual exports, approved snapshot and posed scale without a browser."""
import json
from pathlib import Path
from types import SimpleNamespace
import numpy as np
from PIL import Image
from fold import rotation
from poses import HEAD_SOCKET
from head_lock import verify,approved
HERE=Path(__file__).resolve().parent
OUT=HERE/'candidate'
PREVIEW=HERE.parents[1]/'.local-previews/storm-roc'

def read(path):return json.loads(path.read_text())
def sample(ch,t,default):
    if ch is None:return default
    if isinstance(ch,list):return ch
    keys=sorted((float(k),v) for k,v in ch.items())
    if t<=keys[0][0]:return keys[0][1]
    for (a,av),(b,bv) in zip(keys,keys[1:]):
        if t<=b:return np.array(av)+(np.array(bv)-av)*(t-a)/(b-a)
    return keys[-1][1]
def matrix(b,c,t):
    R=rotation(sample(c.get('rotation'),t,[0,0,0]))@np.diag(sample(c.get('scale'),t,[1,1,1]));p=np.array(b['pivot']);m=np.eye(4);m[:3,:3]=R;m[:3,3]=p-R@p+sample(c.get('position'),t,[0,0,0]);return m

def main():
    geo=read(OUT/'storm_roc.geo.json')['minecraft:geometry'][0];bones=geo['bones'];by={b['name']:b for b in bones}
    clips=read(OUT/'storm_roc.animation.json')['animations'];seen=set()
    for b in bones:
        assert b['name'] not in seen
        assert not b.get('parent') or b['parent'] in seen
        seen.add(b['name'])
        for c in b['cubes']:
            assert min(c['size'])>0
            for f in c['uv'].values():
                assert all(0<=v and v+s<=1024 for v,s in zip(f['uv'],f['uv_size']))
    head_names={b['name'] for b in approved()}
    body_bones=[b for b in bones if b['name'] not in head_names]
    body_cubes=sum(len(b['cubes']) for b in body_bones)
    assert len(body_bones)<=200 and body_cubes<=700
    assert len(bones)<=253 and sum(len(b['cubes']) for b in bones)<=1100
    head=verify(SimpleNamespace(bones=bones),Image.open(OUT/'storm_roc.png'),Image.open(OUT/'storm_roc_glowmask.png'))
    snapshot=PREVIEW/'snapshots/head-approved-1007/candidate'
    # Verify that the locked source itself still matches the user-provided snapshot.
    if snapshot.exists():
        old=read(snapshot/'storm_roc.geo.json')['minecraft:geometry'][0]['bones'];names={b['name'] for b in approved()}
        assert [b for b in old if b['name'] in names]==approved()
        assert (OUT/'storm_roc_sparks.png').read_bytes()==(snapshot/'storm_roc_sparks.png').read_bytes()
        current=read(OUT/'storm_roc_sparks.json');old_sparks=read(snapshot/'storm_roc_sparks.json')
        old_head=np.array(next(b for b in old if b['name']=='head')['pivot']);new_head=np.array(by['head']['pivot'])
        for a,b in zip(current['anchors'],old_sparks['anchors']):
            assert np.allclose(np.array(a['pivot_model_pixels'])-new_head,np.array(b['pivot_model_pixels'])-old_head,atol=1e-7)
            assert {k:v for k,v in a.items() if k!='pivot_model_pixels'}=={k:v for k,v in b.items() if k!='pivot_model_pixels'}
        assert {k:v for k,v in current.items() if k!='anchors'}=={k:v for k,v in old_sparks.items() if k!='anchors'}
        for name in ('storm_roc.png','storm_roc_glowmask.png'):
            assert (HERE/'approved_head'/name).read_bytes()==(snapshot/name).read_bytes()
    def world(clip,t):
        mats={}
        for b in bones:mats[b['name']]=mats.get(b.get('parent'),np.eye(4))@matrix(b,clip['bones'].get(b['name'],{}),t)
        return mats
    def vertices(clip,t,select=lambda b:True):
        mats=world(clip,t);vertices=[]
        for b in bones:
            if not select(b):continue
            for c in b['cubes']:
                C=matrix({'pivot':c.get('pivot',[0,0,0])},{'rotation':c.get('rotation',[0,0,0])},0)
                for k in range(8):
                    p=np.array(c['origin'])+np.array(c['size'])*[(k>>j)&1 for j in range(3)]
                    vertices.append((mats[b['name']]@C@np.r_[p,1])[:3]/16)
        return np.array(vertices)
    def bounds(clip,t):
        a=vertices(clip,t);return a.min(axis=0),a.max(axis=0)
    for name,clip in clips.items():
        assert set(clip['bones'])<=seen
        for b,channels in clip['bones'].items():
            for channel,keys in channels.items():
                a=np.array(list(keys.values()));assert np.isfinite(a).all()
                # Rotations may wrap by 360 degrees, but every loop must close geometrically.
        first=world(clip,0);last=world(clip,clip['animation_length'])
        for name in first:assert np.allclose(first[name],last[name],atol=.002),(name,'loop seam')
        # Each neck endpoint stays attached to the next pivot throughout every loop.
        chain=['neck_base','neck_lower','neck_mid','neck_upper','neck_tip','head']
        for t in map(float,clip['bones']['body']['rotation']):
            mats=world(clip,t)
            for parent,child in zip(chain,chain[1:]):
                endpoint=mats.get(parent+'_skin',mats[parent])@np.r_[by[child]['pivot'],1]
                anchor=np.array(by[child]['pivot'])+(HEAD_SOCKET if child=='head' else 0)
                pivot=mats[child]@np.r_[anchor,1]
                assert np.linalg.norm(endpoint-pivot)<.003,(name,parent,'neck separation')
    get=lambda name:clips['animation.storm_roc.'+name]
    lo,hi=bounds(get('flight'),0);span=hi[0]-lo[0];length=hi[2]-lo[2]
    _,perch_hi=bounds(get('perched'),0);crown=perch_hi[1]-62/16
    m=world(get('hunched'),0);hunch=(m['head']@np.r_[by['head']['pivot'],1])[1]/16
    hlo,hhi=bounds(get('hunched'),0)
    hips=[(m[name]@np.r_[by[name]['pivot'],1])[1]/16 for name in ('left_thigh','right_thigh')]
    shoulder=(m['body']@np.array([0,84,-20,1]))[1]/16
    chain=['left_thigh','left_shin','left_tarsus','left_foot']
    leg_length=sum(np.linalg.norm(np.array(by[a]['pivot'])-by[b]['pivot']) for a,b in zip(chain,chain[1:]))/16
    standing=world(get('screech'),0)
    standing_shoulder=(standing['body']@np.array([0,84,-20,1]))[1]/16
    shoulder_drop=standing_shoulder-shoulder
    assert 26<span<28.5 and 4.5<shoulder<4.7
    assert .55<=shoulder_drop<=.8,(standing_shoulder,shoulder,'crouch depth')
    assert 13<=length<=14.2,(length,'flight length')
    assert 1.5<hunch<2.05,(hunch,'player-height head')
    assert get('peck')['animation_length']==1.6
    strike=world(get('peck'),.5)
    strike_head=(strike['head']@np.r_[by['head']['pivot'],1])[1]/16
    assert 1.5<strike_head<2.05,(strike_head,'strike height')
    belly=vertices(get('hunched'),0,lambda b:b['name']=='body')[:,1].min()
    assert belly>2.5,(belly,'belly must clear the floor')
    def joint(mats,label,suffix):return (mats[label+'_'+suffix]@np.r_[by[label+'_'+suffix]['pivot'],1])[:3]
    heel_standing=[];heel_crouched=[]
    for label in ('left','right'):
        hip=joint(standing,label,'thigh');knee=joint(standing,label,'shin');heel=joint(standing,label,'tarsus');foot=joint(standing,label,'foot')
        crouch_heel=joint(m,label,'tarsus')
        assert .4<heel[1]/hip[1]<.52,(label,'standing heel height')
        assert heel[2]>knee[2]+3 and heel[2]>foot[2]+3,(label,'backward heel')
        assert crouch_heel[1]<heel[1]-1 and crouch_heel[2]>heel[2]+1,(label,'heel must lower and move back')
        heel_standing.append(heel[1]/16);heel_crouched.append(crouch_heel[1]/16)
    # Fixed-length segments and planted feet, checked at every authored frame.
    lengths=[14,28,26];chain=['thigh','shin','tarsus','foot']
    for name,clip in clips.items():
        planted=not any(k in name for k in ('flight','grab'))
        reference=world(clip,0)
        for t in map(float,clip['bones']['body']['rotation']):
            mats=world(clip,t)
            for label in ('left','right'):
                points=[joint(mats,label,s) for s in chain]
                for i,(a,b) in enumerate(zip(points,points[1:])):
                    assert abs(np.linalg.norm(b-a)-lengths[i])<.005,(name,label,t,'leg length')
                    endpoint=mats[label+'_'+chain[i]]@np.r_[by[label+'_'+chain[i+1]]['pivot'],1]
                    assert np.linalg.norm(endpoint[:3]-b)<.005,(name,label,t,'leg separation')
                if planted:
                    assert np.linalg.norm(points[-1]-joint(reference,label,'foot'))<.005,(name,label,t,'foot sliding')
    # A smooth hunch distributes the curve across all five links.
    tangents=[m[n][:3,:3]@np.array([0,1,0]) for n in ['neck_base','neck_lower','neck_mid','neck_upper','neck_tip']]
    bends=[float(np.degrees(np.arccos(np.clip(a@b,-1,1)))) for a,b in zip(tangents,tangents[1:])]
    assert max(bends)<=40.01,(bends,'sharp neck corner')
    # Across the entire wingbeat, distal wing planes stay broad rather than folding upright.
    max_tilt=0;wrist_heights=[]
    for t in np.linspace(0,get('flight')['animation_length'],49):
        mats=world(get('flight'),t)
        for side in ('left','right'):
            name=side+'_wing_wrist';normal=mats[name][:3,:3]@np.array([0,1,0])
            tilt=float(np.degrees(np.arccos(np.clip(normal[1]/np.linalg.norm(normal),-1,1))))
            max_tilt=max(max_tilt,tilt)
            assert tilt<35,(t,side,'upright distal wing')
        wrist_heights.append((mats['left_wing_wrist']@np.r_[by['left_wing_wrist']['pivot'],1])[1]/16)
        H=mats['head'][:3,:3]
        assert np.allclose(H.T@H,np.eye(3),atol=.0001),'Head inherited skin scaling'
    excursion=max(wrist_heights)-min(wrist_heights)
    assert excursion>2,'Wingbeat too shallow'
    for name,t in [('perched',0),('hunched',0),('peck',.5),('screech',1.7)]:
        clip=get(name);mats=world(clip,t)
        tail=vertices(clip,t,lambda b:b['name'].startswith('tail_'))
        primaries=vertices(clip,t,lambda b:'_primary_' in b['name'])
        axis=mats['tail_fan'][:3,2];axis=axis/np.linalg.norm(axis)
        assert (primaries@axis).max()<=(tail@axis).max()+.05,(name,'primaries beyond tail tip')
        assert abs(primaries[:,0]).max()<=abs(tail[:,0]).max()+.15,(name,'primaries outside tail fan')
        left=vertices(clip,t,lambda b:b['name'].startswith('left_primary_'))
        right=vertices(clip,t,lambda b:b['name'].startswith('right_primary_'))
        assert left[:,0].min()<0 and right[:,0].max()>0,(name,'primaries do not cross tail center')
    # The final neck link always follows the skull's back-to-front axis.
    for name in clips:
        for t in np.linspace(0,clips[name]['animation_length'],9):
            mats=world(clips[name],t)
            neck=mats['neck_tip'][:3,:3]@np.array([0,1,0])
            forward=mats['head'][:3,:3]@np.array([0,0,-1])
            assert neck@forward>.999,'Crown-entry neck tangent'
    result={'body_bones':len(body_bones),'body_cubes':body_cubes,'total_bones':len(bones),'total_cubes':sum(len(b['cubes']) for b in bones),'head':head,'snapshot_checked':snapshot.exists(),'crown_above_perch_blocks':round(crown,3),'standing_shoulders_blocks':round(standing_shoulder,3),'hunched_shoulders_blocks':round(shoulder,3),'shoulder_drop_blocks':round(shoulder_drop,3),'standing_heel_heights_blocks':[round(v,3) for v in heel_standing],'crouched_heel_heights_blocks':[round(v,3) for v in heel_crouched],'hunched_hips_blocks':round(sum(hips)/2,3),'leg_joint_chain_blocks':round(leg_length,3),'hunched_beak_to_tail_blocks':round(hhi[2]-hlo[2],3),'neutral_flight_span_blocks':round(span,3),'level_flight_length_blocks':round(length,3),'hunched_head_pivot_blocks':round(hunch,3),'peck_strike_head_pivot_blocks':round(strike_head,3),'hunched_belly_clearance_blocks':round(float(belly),3),'hunched_total_height_blocks':round(hhi[1]-hlo[1],3),'flight_wrist_vertical_excursion_blocks':round(excursion,3),'max_distal_wing_tilt_degrees':round(max_tilt,3),'planted_feet':'verified in all ground clips','leg_chain':'14/28/26 pixels, connected at every frame','max_hunch_neck_bend_degrees':round(max(bends),3),'head_junction':'rear skull socket','folded_primaries':'contained by tail tip and fan','clips':len(clips),'neck_attachment_check':'every authored frame'}
    print(json.dumps(result,indent=2))
if __name__=='__main__':main()
