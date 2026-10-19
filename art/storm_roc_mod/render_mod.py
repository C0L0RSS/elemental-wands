#!/usr/bin/env python3
"""Review renders of the chunky Roc from every side, posed by the study's clips.

Uses the study viewer's own draw code in a Node VM and the NumPy rasterizer: no browser.
Writes .local-previews/storm-roc/mod/<view>.png and a contact sheet.
"""
import argparse,base64,io,json,os,subprocess,sys
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1];STUDY=ROOT/'art/storm_roc'
OUT=ROOT/'.local-previews/storm-roc/mod'
sys.path.insert(0,str(STUDY))
from render_review import render

def url(p):return 'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()

def case(name,clip,angles,extent,at,time=0):
    return [name,clip,'hero',False,True,False,False,{'angles':angles,'extent':extent,'at':at,'time':time,'modelOnly':True}]

CASES=[case('hunched-side','hunched',[90,0],15,[0,3.2,0]),
       case('hunched-three-quarter','hunched',[-38,16],15,[0,3.2,-1]),
       case('hunched-front','hunched',[0,6],13,[0,3.2,0]),
       case('hunched-back','hunched',[180,14],13,[0,3.2,0]),
       case('perched-three-quarter','perched',[-32,12],13,[0,7.4,1]),
       case('perched-front','perched',[0,6],13,[0,7.4,0]),
       case('flight-front-below','flight',[0,-24],21,[0,4.8,0]),
       case('flight-top','flight',[180,89.99],23,[0,5,0]),
       case('flight-under','flight',[0,-89.99],23,[0,5,0]),
       case('head-front','perched',[0,4],4.2,None),
       case('head-three-quarter','perched',[-35,8],4.2,None),
       case('head-side','perched',[90,2],4.2,None)]

def game_clips():
    """The installed runtime clips, back in the study's unreflected basis."""
    from validate_runtime import unreflect
    raw=json.loads((ROOT/'src/main/resources/assets/elementalwands/geckolib/animations/storm_roc.animation.json').read_text())['animations']
    clips={k.removeprefix('animation.storm_roc.'):v for k,v in raw.items()}
    for clip in clips.values():
        for c in clip['bones'].values():unreflect(c)
    return clips

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--width',type=int,default=900);p.add_argument('--only',nargs='*')
    p.add_argument('--game',nargs='*',help='installed clips: name=clip@seconds:bone:yaw:pitch:extent')
    args=p.parse_args();OUT.mkdir(parents=True,exist_ok=True)
    geo=json.loads((HERE/'candidate/storm_roc.geo.json').read_text())
    clips={k.removeprefix('animation.storm_roc.'):v for k,v in json.loads((STUDY/'candidate/storm_roc.animation.json').read_text())['animations'].items()}
    from validate_review import matrix
    bones=geo['minecraft:geometry'][0]['bones']
    def focus(clip,bone,t):
        m={}
        for b in bones:m[b['name']]=m.get(b.get('parent'),np.eye(4))@matrix(b,clip['bones'].get(b['name'],{}),t)
        return (m[bone]@np.r_[next(b['pivot'] for b in bones if b['name']==bone),1])[:3]/16
    head=focus(clips['perched'],'head',0)+[0,.6,-1.2]
    cases=[c[:7]+[{**c[7],'at':c[7]['at'] or head.tolist()}] for c in CASES if not args.only or c[0] in args.only]
    if args.game is not None:
        # Each game case gets its own clip key, so study and runtime clips never share a name.
        runtime=game_clips();cases=[]
        for spec in args.game:
            name,rest=spec.split('=');clip,rest=rest.split('@');t,bone,yaw,pitch,extent=rest.split(':')
            clips['game_'+name]=runtime[clip]
            at=focus(runtime[clip],bone,float(t))+([0,.6,-1.2] if bone=='head' else [0,0,0])
            cases.append(case(name,'game_'+name,[float(yaw),float(pitch)],float(extent),at.tolist(),float(t)))
    payload={'geo':geo,'clips':clips,'texture':url(HERE/'candidate/storm_roc.png'),'glow':url(HERE/'candidate/storm_roc_glowmask.png'),
        'sparks':url(STUDY/'candidate/storm_roc_sparks.png'),'spark_metadata':{'normal':{},'charged':{},'anchors':[]},
        'stats':{'bones':len(bones),'cubes':sum(len(b['cubes']) for b in bones)},'concept':'','face_concept':''}
    (OUT/'payload.json').write_text(json.dumps(payload))
    code=(STUDY/'review_scene.cjs').read_text();a=code.index('const here=');b=code.index('const width=',a)
    code=code[:a]+f"const here={json.dumps(str(STUDY))},root={json.dumps(str(ROOT))};\nconst payload=JSON.parse(fs.readFileSync(path.join(__dirname,'payload.json'),'utf8'));\n"+code[b:]
    (OUT/'record.cjs').write_text(code)
    (OUT/'cases.json').write_text(json.dumps(cases))
    subprocess.run(['node',str(OUT/'record.cjs'),str(OUT/'draws.json'),str(args.width)],check=True,env={**os.environ,'ROC_REVIEW_CASES':str(OUT/'cases.json')})
    r=json.loads((OUT/'draws.json').read_text());textures=[]
    for tex in r['textures']:
        im=Image.open(io.BytesIO(base64.b64decode(tex['src'].split(',')[1]))).convert('RGBA') if tex.get('src') else Image.new('RGBA',(1,1),tuple(tex['solid']))
        textures.append(np.array(im))
    for c in r['cases']:
        model=render(c,r,textures,transparent=True)
        im=Image.new('RGBA',model.size,(178,196,204,255));im.alpha_composite(model)
        ImageDraw.Draw(im).text((10,8),c['name'],fill=(20,30,40))
        im.convert('RGB').save(OUT/(c['name']+'.png'));print(c['name'],flush=True)
    names=[c['name'] for c in r['cases']];cols=3;tw=args.width//2;th=round(tw*820/1200)
    sheet=Image.new('RGB',(cols*tw,((len(names)+cols-1)//cols)*th),(30,30,30))
    for i,n in enumerate(names):
        im=Image.open(OUT/(n+'.png'));im.thumbnail((tw,th));sheet.paste(im,((i%cols)*tw,(i//cols)*th))
    sheet.save(OUT/'contact.png')
    for n in ('record.cjs','payload.json','cases.json','draws.json'):(OUT/n).unlink()
if __name__=='__main__':main()
