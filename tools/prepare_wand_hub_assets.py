#!/usr/bin/env python3
"""Export the approved preview at one texel per GUI pixel; --check detects drift."""
import argparse
import io
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw
from wand_hub_corner_art import build_corner

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/wand_hub'
OUT = ROOT / 'src/main/resources/assets/elementalwands/textures/gui/hub'
ELEMENTS = ('Fire', 'Wind', 'Stone', 'Nature', 'Space', 'None')


def assets():
    corner = (ART / 'corner-source.js').read_text()
    script = '''const fs=require('fs'),vm=require('vm');
const fn=fs.readFileSync(0,'utf8');const all={};
for(const element of ['Fire','Wind','Stone','Nature','Space']) {
 const ops=[];const ctx={clearRect(){},fillRect(x,y,w,h){ops.push([x,y,w,h,this.fillStyle]);}};
 const root={querySelector(){return {getContext(){return ctx;}};}};
 vm.runInNewContext(fn+';paintCorner();',{root,element,paintPaper(){}});all[element]=ops;
}process.stdout.write(JSON.stringify(all));'''
    operations = json.loads(subprocess.check_output(['node', '-e', script], input=corner.encode()))
    paper = Image.open(ART / 'parchment-source.png').convert('RGBA').resize((360, 266), Image.Resampling.NEAREST)
    for y in range(266):
        for x in range(360):
            rgba = paper.getpixel((x, y))
            if (x < 13 or x > 346 or y < 10 or y > 256) and min(rgba[:3]) > 243:
                paper.putpixel((x, y), (0, 0, 0, 0))
    def notch(n, salt):
        p = ((n // 2) * 17 + salt) % 59
        return 2 if p < 5 else 1 if p < 16 else 0
    for x in range(360):
        for y in range(notch(x, 3)): paper.putpixel((x, y), (0, 0, 0, 0))
        for y in range(notch(x, 19)): paper.putpixel((x, 265-y), (0, 0, 0, 0))
    for y in range(266):
        for x in range(notch(y, 9)): paper.putpixel((x, y), (0, 0, 0, 0))
        for x in range(notch(y, 27)): paper.putpixel((359-x, y), (0, 0, 0, 0))
    normal = Image.new('RGBA', (512, 512)); normal.paste(paper, (0, 0))
    yield 'parchment.png', normal
    for element, rects in operations.items():
        im = Image.new('RGBA', (64, 64))
        for x, y, w, h, color in rects:
            layer = Image.new('RGBA', im.size)
            ImageDraw.Draw(layer).rectangle((x, y, x+w-1, y+h-1), fill=color)
            im = Image.alpha_composite(im, layer)
        sheet, decoration = build_corner(element, normal, im)
        # Content and all other paper edges are invariant; damage stays clear of text.
        assert sheet.crop((64, 0, 512, 512)).tobytes() == normal.crop((64, 0, 512, 512)).tobytes()
        assert sheet.crop((0, 64, 512, 512)).tobytes() == normal.crop((0, 64, 512, 512)).tobytes()
        if element in ('Fire', 'Nature', 'Space', 'Wind'):
            assert any(normal.getpixel((x,y))[3] and sheet.getpixel((x,y))[3] == 0
                       for y in range(48) for x in range(48)), 'Damage must remove real paper pixels'
        else:
            assert sheet.tobytes() == normal.tobytes(), 'Stone must leave the sheet intact'
        yield 'parchment_' + element.lower() + '.png', sheet
        yield 'corner_' + element.lower() + '.png', decoration


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    count = 0
    for name, image in assets():
        output = io.BytesIO(); image.save(output, format='PNG', optimize=True); data = output.getvalue()
        path = OUT / name
        if args.check:
            if not path.exists() or path.read_bytes() != data: raise SystemExit('Hub asset drift: ' + name)
        else:
            OUT.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        count += 1
    print(f'Wand hub: {count} approved assets ' + ('verified' if args.check else 'exported'))


if __name__ == '__main__': main()
