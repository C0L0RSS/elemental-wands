#!/usr/bin/env python3
"""Compose a labelled native-asset HUD review, not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'art/hud/runewood'
TEX=ROOT/'src/main/resources/assets/elementalwands/textures'
font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',20)
small=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',15)
atlas=Image.open(TEX/'gui/wand_hud_v2.png').convert('RGBA')
before=Image.open(ART/'before.png').convert('RGBA')

def row(element='stone',mass=75,old=False,locked=False,cooldown=False):
    im=Image.new('RGBA',(120,56),(0,0,0,0))
    a=before if old else atlas
    d=ImageDraw.Draw(im)
    if not old and element=='stone':
        im.alpha_composite(a.crop((0,160,120,170)),(0,0))
        fill=round(106*mass/100)
        if fill:im.alpha_composite(a.crop((0,180 if mass>=75 else 176,fill,184 if mass>=75 else 180)),(7,3))
        for i in range(1,4):
            nx=7+round(106*i/4)
            d.point((nx,3),fill=(59,56,45,255));d.point((nx,6),fill=(59,56,45,255))
    for i,ability in enumerate(('primary','secondary','ultimate')):
        x=i*42;y=13;v=80 if (locked and i>0) or (cooldown and i==0) else 0
        im.alpha_composite(a.crop((i*85,v,i*85+36,v+36)),(x,y))
        icon=Image.open(TEX/f'gui/ability/{element}_{ability}.png').convert('RGBA')
        size=28 if old else 24;inset=4 if old else 6
        im.alpha_composite(icon.resize((size,size),Image.Resampling.NEAREST),(x+inset,y+inset))
        if locked and i>0:
            overlay=Image.new('RGBA',(24,24),(0,0,0,200));im.alpha_composite(overlay,(x+6,y+6))
            d.rectangle((x+14,y+18,x+21,y+23),fill='#af8145')
            d.line([(x+15,y+17),(x+15,y+14),(x+20,y+14),(x+20,y+17)],fill='#e2c18a')
            d.rectangle((x+17,y+20,x+18,y+21),fill='#30251e')
    return im

sheet=Image.new('RGB',(1140,730),'#1c2425');d=ImageDraw.Draw(sheet)
d.text((30,20),'CARVED WOOD WAND HUD',font=font,fill='#e6cea4')
d.text((30,52),'Production textures at 3x nearest-neighbour scale. Layout preview; not Minecraft gameplay.',font=small,fill='#a5b5b3')
for x,label,im in [(40,'Previous frames',row(old=True)),(410,'New frames + 75% Stone reserve',row()),(780,'Empty reserve / locked abilities',row(mass=0,locked=True))]:
 d.text((x,100),label,font=small,fill='#e5d6b8');sheet.paste(im.resize((360,168),Image.Resampling.NEAREST),(x,130),im.resize((360,168),Image.Resampling.NEAREST))
for i,element in enumerate(('fire','wind','nature','space')):
 x=45+(i%2)*550;y=335+(i//2)*175
 d.text((x,y),element.title(),font=font,fill='#d9c296')
 im=row(element).resize((360,168),Image.Resampling.NEAREST)
 sheet.paste(im,(x+135,y-15),im)
d.text((35,697),'Stone reserve: no text • four pull marks • pale mineral fill at heavy-hit strength',font=small,fill='#a5b5b3')
sheet.save(ART/'preview.png')
print(ART/'preview.png')
