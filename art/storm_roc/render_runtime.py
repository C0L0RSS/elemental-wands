#!/usr/bin/env python3
"""CPU-only runtime contact sheets / GIFs, with a fixed camera per clip at 1.25x.

No browser, WebGL context, or generated study HTML is required. The Node VM only
records draw submissions; render_review rasterizes those triangles with NumPy.
"""
import argparse,base64,hashlib,io,json,os,subprocess
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from render_review import render
from build_runtime import SCALE
from validate_runtime import unreflect
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ASSETS=ROOT/'src/main/resources/assets/elementalwands'
OUT=ROOT/'.local-previews/storm-roc/animations'
KEYS={
 'perched':[0,.6,1.8,2.5,3.3,3.95,5.3,7.2],
 'call':[0,.25,.45,1.3,1.9,2,2.5,3],
 'land':[0,.1,.2,.3,.4,.5,.65,.8],
 'flight':[0,.35,.7,1.15,1.55,1.9,2.8,4.3],
 'hunched':[0,.6,1.45,1.9,2.5,3.2,4,4.8],
 'dive':[0,.4,1,1.5,1.8,2.3,2.75,3],
 'stuck':[0,.5,1.3,2.3,3.6,4.85,5.65,6],
 'peck':[0,.25,.45,.6,.7,.9,1.25,1.8],
 'swing_left':[0,.4,.65,.8,.95,1.15,1.4,1.8],
 'swing_right':[0,.4,.65,.8,.95,1.15,1.4,1.8],
 'scream':[0,.5,.85,1.1,1.2,1.5,1.9,2.2],
 'wing':[0,.3,.6,.85,1,1.15,1.5,2],
 'pounce':[0,.45,.85,1.2,1.45,1.8,2.2,3.2],
 'stampede':[0,.3,.9,1.2,1.65,2.4,3.15,4.2],
 'takeoff':[0,.3,.7,1.05,1.2,1.45,2,2.5],
 'hop':[0,.15,.3,.5,.7,.85,1,1.2]}
GIFS={'perched','call','flight','dive','peck','scream','wing','pounce','stampede'}

def raster(args):
    case,record,textures,path=args
    im=render(case,record,textures);im.save(path)
    return str(path)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only',nargs='+',choices=list(KEYS))
    # GIFs for the usual clips, or for every clip named with --only.
    parser.add_argument('--gifs',action='store_true')
    parser.add_argument('--width',type=int,default=480)
    parser.add_argument('--fps',type=int,default=10)
    parser.add_argument('--workers',type=int,default=4)
    args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True)
    geo=json.loads((ASSETS/'geckolib/models/storm_roc.geo.json').read_text())
    for b in geo['minecraft:geometry'][0]['bones']:
        for item in [b]+b.get('cubes',[]):unreflect(item)
    clips={k.removeprefix('animation.storm_roc.'):v for k,v in json.loads((ASSETS/'geckolib/animations/storm_roc.animation.json').read_text())['animations'].items()}
    for clip in clips.values():
        for ch in clip['bones'].values():unreflect(ch)
    def url(path):return 'data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode()
    payload={'geo':geo,'clips':clips,'texture':url(ASSETS/'textures/entity/storm_roc.png'),
        'glow':url(ASSETS/'textures/entity/storm_roc_glowmask.png'),'sparks':url(ASSETS/'textures/entity/storm_roc_sparks.png'),
        'spark_metadata':json.loads((ASSETS/'storm_roc_sparks.json').read_text()),'stats':{'bones':len(geo['minecraft:geometry'][0]['bones']),'cubes':sum(len(b.get('cubes',[])) for b in geo['minecraft:geometry'][0]['bones'])},'concept':'','face_concept':''}
    (OUT/'payload.json').write_text(json.dumps(payload))
    recorder=(HERE/'review_scene.cjs').read_text()
    a=recorder.index('const here=');b=recorder.index('const width=',a)
    recorder=recorder[:a]+f"const here={json.dumps(str(HERE))},root={json.dumps(str(ROOT))};\nconst payload=JSON.parse(fs.readFileSync(path.join(__dirname,'payload.json'),'utf8'));\n"+recorder[b:]
    # Scale only model vertices; the floor/grid and 1.8-block reference stay in blocks.
    marker='const cases=process.env.ROC_REVIEW_CASES?'
    recorder=recorder.replace(marker,f"source=source.replace('mesh[at+j]=(m[j]*d[i]+m[4+j]*d[i+1]+m[8+j]*d[i+2]+m[12+j])/16', 'mesh[at+j]=(m[j]*d[i]+m[4+j]*d[i+1]+m[8+j]*d[i+2]+m[12+j])*{SCALE}');\n"+marker)
    # Low-poly faces: GeckoLib's flat lists, triangles closed by a repeated corner. The viewer's
    # shader flips the PNG on upload, so the atlas's top-left V flips once here.
    recorder=recorder.replace(marker,"""source=source.replace('return {name:b.name,data:new Float32Array(data)};', `
 if(b.poly_mesh){const m=b.poly_mesh,P=m.positions,N=m.normals,U=m.uvs;
   for(const face of m.polys){const corners=face[3][0]===face[2][0]?[0,1,2]:[0,1,2,0,2,3];
     for(const k of corners){const [pi,ni,ui]=face[k];data.push(P[3*pi],P[3*pi+1],P[3*pi+2],N[3*ni],N[3*ni+1],N[3*ni+2],U[2*ui],1-U[2*ui+1]);}}}
 return {name:b.name,data:new Float32Array(data)};`);
"""+marker)
    # Add a floor grid and human scale marker to each otherwise isolated view.
    recorder=recorder.replace("if(options.extent){", "draws.push({vertices:floor,uniform:{...draws[0].uniform,tint:[.45,.52,.55,1]},texture:white.id,mask:clear.id}); for(const [key,tint] of Object.entries({skin:[.70,.48,.34,1],shirt:[.19,.49,.49,1],pants:[.23,.26,.38,1],boots:[.16,.19,.24,1],hair:[.22,.15,.12,1]}))draws.push({vertices:player[key],uniform:{...draws[0].uniform,tint},texture:white.id,mask:clear.id}); if(options.extent){")
    code=OUT/'record.cjs';code.write_text(recorder)
    manifest={'scale':SCALE*16,'fps':args.fps,'offline':True,'clips':{},'inputs':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [ASSETS/'geckolib/models/storm_roc.geo.json',ASSETS/'geckolib/animations/storm_roc.animation.json',ASSETS/'textures/entity/storm_roc.png',ASSETS/'textures/entity/storm_roc_glowmask.png']}}
    for name in args.only or KEYS:
        length=clips[name]['animation_length'];times=KEYS[name]
        gif_times=[round(i/args.fps,5) for i in range(round(length*args.fps)+1)] if args.gifs and (name in GIFS or args.only) else []
        # Same camera through the whole clip. A side-oblique view shows the neck
        # strike and both wing silhouettes; front-oblique reveals sideways looks.
        angles=[-52,13] if name not in ('swing_left','swing_right','perched','hunched','call') else [-28,12]
        alltimes=sorted(set(times+gif_times));frames={}
        # Keep Node's JSON and Python's triangle records bounded to eight frames.
        for start in range(0,len(alltimes),8):
            batch=alltimes[start:start+8]
            cases=[[f'{name}-{t:.2f}s',name,'hero',False,True,False,False,{'time':t,'modelOnly':True,'angles':angles,'extent':30 if name in ('flight','dive','wing','takeoff','hop','land','call','pounce') else 24,'at':[0,7,-2]}] for t in batch]
            casepath=OUT/'cases.json';casepath.write_text(json.dumps(cases));target=OUT/'draws.json'
            subprocess.run(['node',str(code),str(target),str(args.width)],check=True,env={**os.environ,'ROC_REVIEW_CASES':str(casepath)})
            record=json.loads(target.read_text());textures=[]
            for tex in record['textures']:
                im=Image.open(io.BytesIO(base64.b64decode(tex['src'].split(',')[1]))).convert('RGBA') if tex.get('src') else Image.new('RGBA',(1,1),tuple(tex['solid']))
                textures.append(np.array(im))
            meta={k:v for k,v in record.items() if k!='cases'}
            jobs=[(case,meta,textures,OUT/(case['name']+'.png')) for case in record['cases']]
            with ProcessPoolExecutor(max_workers=args.workers) as pool:list(pool.map(raster,jobs))
            frames.update({t:OUT/(case['name']+'.png') for t,case in zip(batch,record['cases'])})
            target.unlink()
        height=Image.open(frames[times[0]]).height
        sheet=Image.new('RGB',(args.width*4,(height+25)*2+30),(16,24,35));d=ImageDraw.Draw(sheet)
        d.text((12,8),f'{name} / {length}s / loop={clips[name]["loop"]} / 1.25x / CPU offline',fill='white')
        for i,t in enumerate(times):
            x=(i%4)*args.width;y=30+(i//4)*(height+25)
            sheet.paste(Image.open(frames[t]).convert('RGB'),(x,y));d.text((x+10,y+height+5),f't = {t:.2f} s',fill='white')
        sheet.save(OUT/(name+'-contact.png'))
        if gif_times:
            ims=[Image.open(frames[t]).convert('RGB') for t in gif_times]
            ims[0].save(OUT/(name+'.gif'),save_all=True,append_images=ims[1:],duration=round(1000/args.fps),loop=0,disposal=2)
        manifest['clips'][name]={'length':length,'loop':clips[name]['loop'],'keys':times,'gif':bool(gif_times)}
        print(f'Rendered {name}: contact sheet'+(' + GIF' if gif_times else ''),flush=True)
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for filename in ('payload.json','cases.json','record.cjs'):(OUT/filename).unlink(missing_ok=True)
    print(OUT,flush=True)
if __name__=='__main__':main()
