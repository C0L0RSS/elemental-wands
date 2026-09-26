#!/usr/bin/env python3
"""Export smooth procedural HUD layers and an animation-sampled drain hand socket."""
import argparse
import io
import json
import math
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'v2'))
from rig import matrix, BY_NAME
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'


def exports():
    files = {}
    def png(name, image, smooth=True):
        path = ASSETS / 'textures' / name
        buf = io.BytesIO(); image.save(buf, format='PNG')
        files[path] = buf.getvalue()
        if smooth:
            files[Path(str(path) + '.mcmeta')] = b'{"texture":{"blur":true,"clamp":true}}\n'
    n = 512
    y, x = np.mgrid[:n, :n] / (n - 1) * 2 - 1
    radius = np.hypot(x, y)
    rgba = np.zeros((n, n, 4), dtype=np.uint8)
    for c, stops in enumerate(([58,29,8], [126,76,22], [137,91,37])):
        rgba[:,:,c] = np.interp(radius, [0,.4,1], stops).astype(np.uint8)
    rgba[:,:,3] = (np.interp(radius,[0,.4,1],[255,178,0])).astype(np.uint8)
    png('gui/life_drain/fog.png', Image.fromarray(rgba))
    # Large, smooth curl; transparent margins prevent clipped ends under rotation.
    layer = Image.new('RGBA', (512,512)); draw = ImageDraw.Draw(layer)
    pts=[]
    for i in range(201):
        t=i/200; a,b,c,d=(470,250),(335,300),(115,160),(215,100)
        pts.append(tuple((1-t)**3*a[k]+3*(1-t)**2*t*b[k]+3*(1-t)*t*t*c[k]+t**3*d[k] for k in range(2)))
    draw.line(pts, fill=(113,164,169,255), width=14)
    png('gui/life_drain/wisp.png',layer.filter(ImageFilter.GaussianBlur(5)))
    rgba[:,:,:3] = [2,7,14]
    rgba[:,:,3] = (255*np.interp(radius,[.28,.68,1,1.28],[0,.10,.60,.94])).astype(np.uint8)
    png('gui/life_drain/shadow.png',Image.fromarray(rgba))
    png('misc/life_drain_white.png',Image.new('RGBA',(2,2),'white'),False)
    animation=json.loads((ASSETS/'geckolib/animations/hollow_necromancer.animation.json').read_text())['animations']
    clip=animation['animation.hollow_necromancer.drain']
    bone=BY_NAME['mage_hand_left']; pivot=np.array(bone['pivot'])*[-1,1,1]
    rows=[]
    for tick in range(85):
        p=(matrix('mage_hand_left',clip,tick/20) @ [*pivot,1])[:3]/16
        rows.append('        {'+', '.join(f'{v:.6f}' for v in [-p[0],p[1],p[2]])+'}')
    source='''package com.anton.elementalwands.client;

import net.minecraft.util.math.Vec3d;

/** Generated from the runtime drain rig by life_drain/build_runtime.py. */
final class NecromancerDrainSocket {
    private static final double[][] POSES = {
'''+',\n'.join(rows)+'''
    };
    static Vec3d local(float tick) {
        float frame = Math.clamp(tick, 0, POSES.length - 1);
        int index = (int)frame;
        double[] a = POSES[index], b = POSES[Math.min(index + 1, POSES.length - 1)];
        return new Vec3d(a[0],a[1],a[2]).lerp(new Vec3d(b[0],b[1],b[2]),frame-index);
    }
    private NecromancerDrainSocket() {}
}
'''
    files[ROOT/'src/main/java/com/anton/elementalwands/client/NecromancerDrainSocket.java']=source.encode()
    return files

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for path,data in exports().items():
        if args.check:
            if not path.exists() or path.read_bytes()!=data: raise SystemExit(f'Stale Life Drain export: {path}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print('Life Drain smooth HUD textures and hand socket '+('verified' if args.check else 'exported'))
