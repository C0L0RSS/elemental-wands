#!/usr/bin/env python3
"""Reduce the untouched Space painting to a fixed-palette RGBA atlas.

Python 3 + Pillow + NumPy. --check compares both PNGs without writing.
"""
import argparse
import io
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'source/space-materials.png'
NAMES = ('void', 'glass', 'nebula', 'rim', 'amethyst', 'blue', 'red', 'starlight')
# Ordered dark-to-light shades. No dithering, stochastic sampling or RGB tweening.
PALETTES = (
    ('#080710', '#100d20', '#1d1533', '#8673bc', '#c7eafa'),
    ('#100b1d', '#1b102d', '#2d1847', '#8950d5', '#cbb0ff'),
    ('#1c1028', '#30163f', '#482151', '#633063', '#a54a99'),
    ('#3c175d', '#692794', '#9b43df', '#d45af0', '#f5a0f5'),
    ('#492d6b', '#6d4993', '#9567b4', '#bc93d6', '#e0c3f0'),
    ('#1f47b8', '#2f6cf0', '#4f95ff', '#8cc0ff', '#e0efff'),
    ('#9a1420', '#d6262a', '#ff4a35', '#ff8a5c', '#ffe0cc'),
    ('#81729f', '#ac9bc6', '#cec0e6', '#e8def8', '#faf4ff'),
)
LUMA = np.array([.2126, .7152, .0722])
Y, X = np.indices((16, 16))
FRAME = (X == 0) | (X == 15) | (Y == 0) | (Y == 15)


def palette(row):
    return np.array([tuple(bytes.fromhex(c[1:])) for c in PALETTES[row]], dtype=np.uint8)


def source_grids():
    """Panel bounds normalized to the actual painting; nearest samples, five bins."""
    with Image.open(SOURCE) as image:
        rgb = image.convert('RGB')
        w, h = rgb.size
        if w < 32 or h < 64:
            raise ValueError('Source must have eight panels of at least 16 x 16')
        grids, anchors = [], []
        for row in range(8):
            x, y = row % 2, row // 2
            panel = rgb.crop((round(x*w/2), round(y*h/4), round((x+1)*w/2), round((y+1)*h/4)))
            values = np.asarray(panel.resize((16, 16), Image.Resampling.NEAREST), dtype=float) @ LUMA
            lo, hi = np.percentile(values, [2, 98])
            if hi-lo < 1:
                raise ValueError(f'Flat source panel: {NAMES[row]}')
            grids.append(np.clip(np.floor((values-lo)/(hi-lo)*5), 0, 4).astype(np.uint8))
            if row in (0, 1):
                # Tiny painted stars can fall between nearest-neighbor samples.
                # Max-pool only their locations, before luminance quantization.
                full = np.asarray(panel, dtype=float) @ LUMA
                peaks = np.array([[full[round(v*panel.height/16):round((v+1)*panel.height/16),
                                       round(u*panel.width/16):round((u+1)*panel.width/16)].max()
                                   for u in range(16)] for v in range(16)])
                anchors.append(stars(peaks, 3))
            else:
                anchors.append(())
        return zip(grids, anchors)


def stars(base, count):
    """Keep isolated brightest source samples, stable row-major tie ordering."""
    picked = []
    for flat in np.argsort(-base.astype(float).ravel(), kind='stable'):
        y, x = divmod(int(flat), 16)
        if 2 <= x <= 13 and 2 <= y <= 13 and all(max(abs(x-a), abs(y-b)) >= 4 for a, b in picked):
            picked.append((x, y))
            if len(picked) == count:
                return picked
    raise ValueError('Not enough separated source star samples')


def inward(base, phase):
    """Advect painted square shells inward two pixels per phase on an 8-shell torus.

    Sample the same ray on a larger source shell; after four phases the radial
    offset is eight shells, so frame 4 equals frame 0 exactly. No new noise.
    """
    dx, dy = 2*X-15, 2*Y-15
    radius = np.maximum(np.abs(dx), np.abs(dy))
    shell = (radius-1)//2
    sample_radius = 2*((shell+2*phase) % 8)+1
    sx = np.clip(np.rint((dx*sample_radius/radius+15)/2), 0, 15).astype(int)
    sy = np.clip(np.rint((dy*sample_radius/radius+15)/2), 0, 15).astype(int)
    return base[sy, sx]


def frame_rgba(base, row, frame, anchors=()):
    phase = frame % 4
    alpha = np.full((16, 16), 255, dtype=np.uint8)
    if row in (0, 1):
        # Source texture stays anchored; sparse selected stars twinkle or drift.
        indices = np.minimum(base, 2).copy()
        if row == 0:
            indices = (indices // 2).astype(np.uint8)
        else:
            alpha = (140+indices*20).astype(np.uint8)
            indices[FRAME] = 3
            alpha[FRAME] = 255
        for n, (x, y) in enumerate(anchors):
            if row == 1:
                x = 2+(x-2+3*phase) % 12
                y = 2+(y-2+3*phase) % 12
            indices[y, x] = (3, 4, 4, 3)[(phase+n) % 4]
            alpha[y, x] = 255
    elif row == 2:
        indices = np.roll(base, 4*phase, axis=1)
        alpha = (140+indices.astype(np.uint16)*12).astype(np.uint8)
        indices[FRAME] = 4
        alpha[FRAME] = 255
    elif row in (3, 5, 6):
        indices = inward(base, phase)
    elif row == 7:
        # One anchored core, restrained repeating value pulse.
        indices = np.minimum(base.astype(int)+(0, 1, 0, -1)[phase], 4).clip(0, 4)
    else:
        indices = base.copy()  # Crystal facets remain anchored.
    return np.dstack((palette(row)[indices], alpha))


def build_atlas():
    atlas = np.empty((128, 64, 4), dtype=np.uint8)
    for row, (base, anchors) in enumerate(source_grids()):
        frames = [frame_rgba(base, row, f, anchors) for f in range(4)]
        assert np.array_equal(frame_rgba(base, row, 4, anchors), frames[0]), 'Loop closure'
        assert len({f.tobytes() for f in frames}) >= (1 if row == 4 else 3)
        for f, pixels in enumerate(frames):
            if row in (1, 2):
                assert np.all(pixels[FRAME, 3] == 255)
                a = pixels[1:15, 1:15, 3]
                assert np.all(((140 <= a) & (a <= 190)) | (a == 255))
                if row == 1:
                    assert np.count_nonzero(a == 255) == 3
            else:
                assert np.all(pixels[:, :, 3] == 255)
            assert set(map(tuple, pixels[:, :, :3].reshape(-1, 3))) <= set(map(tuple, palette(row)))
            atlas[row*16:(row+1)*16, f*16:(f+1)*16] = pixels
    assert atlas.shape == (128, 64, 4)
    return Image.fromarray(atlas)


def checker(size, step=16):
    y, x = np.indices((size[1], size[0]))
    values = np.where((x//step+y//step) % 2, 44, 30).astype(np.uint8)
    return Image.fromarray(np.dstack((values, values, values, np.full_like(values, 255))))


def cube(sheet, tile, origin):
    """Wind's integer full-face projection, with real alpha over a checker."""
    ox, oy = origin
    layer = Image.new('RGBA', sheet.size)
    draw = ImageDraw.Draw(layer)
    pixels = np.asarray(tile)
    faces = (((ox, oy), (3, -2), (3, 2), 1.),
             ((ox, oy), (3, 2), (0, 4), .8),
             ((ox+48, oy+32), (3, -2), (0, 4), .6))
    for (x0, y0), u, v, light in faces:
        for y in range(16):
            for x in range(16):
                px, py = x0+x*u[0]+y*v[0], y0+x*u[1]+y*v[1]
                points = [(px, py), (px+u[0], py+u[1]),
                          (px+u[0]+v[0], py+u[1]+v[1]), (px+v[0], py+v[1])]
                color = tuple(np.rint(pixels[y, x, :3].astype(float)*light).astype(int))
                draw.polygon(points, fill=(*color, int(pixels[y, x, 3])))
    sheet.alpha_composite(layer)


def build_preview(atlas):
    sheet = Image.new('RGBA', (1040, 1184), '#13101d')
    draw = ImageDraw.Draw(sheet)
    title, label, small = [ImageFont.load_default(size=s) for s in (24, 16, 13)]
    draw.text((24, 18), 'BLOCK SPACE / VOID GLASS', font=title, fill='#eee2ff')
    draw.text((24, 51), '4-frame loops / per-pixel glass alpha / hard palette steps', font=label, fill='#b7a9c9')
    for f in range(4):
        draw.text((24+128*f, 90), f'FRAME {f+1:02d}', font=small, fill='#c6bfd2')
    enlarged = atlas.resize((512, 1024), Image.Resampling.NEAREST)
    background = checker(enlarged.size)
    background.alpha_composite(enlarged)
    sheet.paste(background, (24, 120))
    for row, name in enumerate(NAMES):
        y = 120+128*row
        draw.text((558, y+15), f'{row}  {name.upper()}', font=label, fill='#eee2ff')
        draw.text((558, y+46), 'Alpha 140-188 / 255' if row in (1, 2) else 'Alpha 255', font=small, fill='#b7a9c9')
        for i, color in enumerate(PALETTES[row]):
            draw.rectangle((558+29*i, y+87, 582+29*i, y+106), fill=color)
        sheet.paste(checker((116, 116), 8), (817, y+5))
        cube(sheet, atlas.crop((0, row*16, 16, (row+1)*16)), (830, y+38))
    draw.text((24, 1159), 'Atlas: exact 8x nearest-neighbor over checker / cube faces: full tile, RGB light 1.0 / 0.8 / 0.6', font=small, fill='#b7a9c9')
    return sheet.convert('RGB')


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format='PNG', optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Read-only byte comparison')
    args = parser.parse_args()
    atlas = build_atlas()
    outputs = {'blockspace.png': atlas, 'blockspace_preview.png': build_preview(atlas)}
    failures = []
    for name, image in outputs.items():
        path, data = ROOT/name, png_bytes(image)
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                failures.append(name)
        else:
            path.write_bytes(data)
    if failures:
        raise SystemExit('MISMATCH: '+', '.join(failures)+'; run build_atlas.py')
    print(('CHECK PASSED' if args.check else 'BUILT')+': '+', '.join(outputs))


if __name__ == '__main__':
    main()
