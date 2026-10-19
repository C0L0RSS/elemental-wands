#!/usr/bin/env python3
"""Build the head variants in heads.py (or the talon variants in feet.py) side by side, to pick
the Roc's face and feet.

For each variant this writes, under .local-previews/storm-roc/heads/ (or talons/):
  <variant>.json   the model's parts for the browser page (same bones and order as roc.json)
  <variant>.png    its atlas, and <variant>_glow.png its glow layer as the game draws it
  renders/         offline views of every variant (render_mod.py), and contact.png
The page draws them on the installed clips from .local-previews/storm-roc/preview/roc.json
(made by art/storm_roc/export_preview.py).

    python3 art/storm_roc_mod/head_variants.py [variant ...]
    python3 art/storm_roc_mod/head_variants.py --feet [--head demon] [variant ...]
"""
import argparse,copy,importlib.util,io,json,shutil,sys,tempfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE));sys.path.insert(0,str(ROOT/'art/storm_roc'));sys.path.insert(0,str(ROOT/'art/storm_roc_texture_study'))
import build_mod,heads,feet as talons
from paint import paint
from build_texture_v3 import build as texture
from build_runtime import glowing
from export_preview import bake_parts
OUT=ROOT/'.local-previews/storm-roc/heads'
CLIPS=None
VIEWS=['front=hunched@0:head:0:4:4.5','three-quarter=hunched@0:head:-35:8:4.5','side=hunched@0:head:90:2:4.5',
    'from-below=hunched@0:head:-25:-14:5','scream=scream@1.3:head:-40:10:6','body=hunched@0:body:-38:12:15']
FOOT_VIEWS=['three-quarter=hunched@0:left_foot:-35:14:2.8','side=hunched@0:left_foot:80:6:2.8','front=hunched@0:left_foot:-10:4:2.8',
    'low=hunched@0:left_foot:-40:-6:2.6','perched=perched@0:left_foot:-40:15:3','pounce=pounce@1.6:left_foot:-60:5:4']

def model(head,feet=None):
    study=json.loads((build_mod.STUDY/'candidate/storm_roc.geo.json').read_text())['minecraft:geometry'][0]['bones']
    m=build_mod.sculpt(study);paint(m.parts);build_mod.refine(m,head,feet)
    return {'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.storm_roc',
        'texture_width':1024,'texture_height':1024,'visible_bounds_width':32,'visible_bounds_height':20,
        'visible_bounds_offset':[0,5,0]},'bones':m.bones}]}

def render(name,geo,atlas,mask,views=VIEWS):
    spec=importlib.util.spec_from_file_location('render_mod',HERE/'render_mod.py');rm=importlib.util.module_from_spec(spec);spec.loader.exec_module(rm)
    with tempfile.TemporaryDirectory() as tmp:
        c=Path(tmp)/'candidate';c.mkdir()
        (c/'storm_roc.geo.json').write_text(json.dumps(geo));(c/'storm_roc.png').write_bytes(atlas);(c/'storm_roc_glowmask.png').write_bytes(mask)
        rm.HERE=Path(tmp);rm.OUT=OUT/'renders'/name
        if CLIPS:
            # Clips from another runtime build (e.g. a pose change not installed yet).
            from validate_runtime import unreflect
            raw=json.loads(Path(CLIPS).read_text())['animations'];clips={k.removeprefix('animation.storm_roc.'):v for k,v in raw.items()}
            for clip in clips.values():
                for c in clip['bones'].values():unreflect(c)
            rm.game_clips=lambda:copy.deepcopy(clips)
        sys.argv=['render','--width','900','--game',*views];rm.main()

def main():
    global OUT,CLIPS
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('names',nargs='*')
    ap.add_argument('--feet',action='store_true');ap.add_argument('--head',default='demon')
    ap.add_argument('--clips',help='render with this runtime animation JSON instead of the installed one')
    args=ap.parse_args();picked=args.names;CLIPS=args.clips
    table=talons.FEET if args.feet else heads.VARIANTS;names=picked or list(table)
    views=FOOT_VIEWS if args.feet else VIEWS
    if args.feet:OUT=OUT.parent/'talons'
    look=(lambda name:(args.head,name)) if args.feet else (lambda name:(name,None))
    OUT.mkdir(parents=True,exist_ok=True)
    base=json.loads((ROOT/'.local-previews/storm-roc/preview/roc.json').read_text())
    order=[p['bone'] for p in base['parts']]
    index=[]
    for name in names:
        head,feet=look(name);geo=model(head,feet);atlas,mask=texture(head,glow=True,feet=feet)
        parts=bake_parts(geo['minecraft:geometry'][0]['bones'],1024,1024)
        assert [p['bone'] for p in parts]==order,'a variant must keep the meshed bones and their order'
        (OUT/f'{name}.json').write_text(json.dumps({'parts':parts},separators=(',',':')))
        (OUT/f'{name}.png').write_bytes(atlas)
        with tempfile.TemporaryDirectory() as tmp:
            a,g=Path(tmp)/'a.png',Path(tmp)/'g.png';a.write_bytes(atlas);g.write_bytes(mask)
            (OUT/f'{name}_glow.png').write_bytes(glowing(a,g))
        render(name,geo,atlas,mask,views)
        v=table[name];index.append({'id':name,'label':v['label'],'note':v['note']})
        print('built',name,flush=True)
    # One contact sheet: a row per variant, a column per view.
    views=[v.split('=')[0] for v in views];tw,th=450,307
    sheet=Image.new('RGB',(tw*len(views)+150,th*len(names)),(30,30,30));d=ImageDraw.Draw(sheet)
    for r,name in enumerate(names):
        d.text((10,r*th+th//2),table[name]['label'],fill=(240,230,220))
        for c,view in enumerate(views):
            im=Image.open(OUT/'renders'/name/f'{view}.png');im.thumbnail((tw,th));sheet.paste(im,(150+c*tw,r*th))
    sheet.save(OUT/'contact.png')
    if len(names)>1:(OUT/'variants.json').write_text(json.dumps(index,indent=1)+'\n')
    print(OUT/'contact.png')
if __name__=='__main__':main()
