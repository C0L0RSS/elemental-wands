#!/usr/bin/env python3
"""Offline contact sheet of shipped combat tracks; not native Minecraft proof."""
import base64,io,json,os,subprocess
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from render_review import render
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
OUT=ROOT/'.local-previews/storm-roc/runtime'

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    cases=[]
    for name,t in [('hunched',0),('dive',2),('stuck',0),('stuck',2.3),('stuck',5.4),('stuck',6),('peck',.6),('peck',.8),('swing_left',1),('scream',1.7),('wing',1.3),('pounce',.7),('pounce',1.65),('pounce',2.3),('stampede',.3),('stampede',1.65),('stampede',3.2),('takeoff',1.6)]:
        cases.append([name+'-'+str(t),name,'hero',False,True,False,False,{'time':t,'modelOnly':True,'extent':16,'at':[0,5,-2]}])
    casepath=OUT/'cases.json';casepath.write_text(json.dumps(cases))
    recorder=(HERE/'review_scene.cjs').read_text().replace('const here=__dirname,','const here='+json.dumps(str(HERE))+',')
    marker="const width=Number(process.argv[3]||1200)"
    recorder=recorder.replace(marker,"payload.clips=Object.fromEntries(Object.entries(JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/assets/elementalwands/geckolib/animations/storm_roc.animation.json'),'utf8')).animations).map(([k,v])=>[k.replace('animation.storm_roc.',''),v])); for(const clip of Object.values(payload.clips))for(const ch of Object.values(clip.bones)){if(ch.rotation){const flip=v=>[v[0],-v[1],-v[2]];ch.rotation=Array.isArray(ch.rotation)?flip(ch.rotation):Object.fromEntries(Object.entries(ch.rotation).map(([t,v])=>[t,flip(v)]));}} \n"+marker)
    code=OUT/'record.cjs';code.write_text(recorder)
    target=OUT/'draws.json'
    subprocess.run(['node',str(code),str(target),'640'],check=True,env={**os.environ,'ROC_REVIEW_CASES':str(casepath)})
    record=json.loads(target.read_text());textures=[]
    for t in record['textures']:
        im=Image.open(io.BytesIO(base64.b64decode(t['src'].split(',')[1]))).convert('RGBA') if t.get('src') else Image.new('RGBA',(1,1),tuple(t['solid']))
        textures.append(np.array(im))
    # Three columns keep all poses in one inspectable artifact.
    sheet=Image.new('RGB',(1920,6*460),(16,24,35));d=ImageDraw.Draw(sheet)
    for i,case in enumerate(record['cases']):
        im=render(case,record,textures);im.save(OUT/(case['name']+'.png'))
        x=(i%3)*640;y=(i//3)*460;sheet.paste(im,(x,y));d.text((x+10,y+440),case['name'],fill='white')
    sheet.save(OUT/'combat-contact.png')
    target.unlink();print(OUT/'combat-contact.png')
if __name__=='__main__':main()
