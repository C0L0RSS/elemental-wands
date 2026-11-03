#!/usr/bin/env python3
"""Build the block Wind material atlas from the untouched source painting.

Python 3, Pillow, NumPy. --check compares both PNGs byte-for-byte; never writes.
"""
import argparse
import io
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'source/wind-materials.png'
NAMES = ('gale', 'cloud', 'mist', 'streak', 'swirl', 'feather', 'pearl', 'silver')
PALETTES = (
    ('#93c9e1', '#bbdeed', '#e0f0f6', '#f3fafc', '#fbfdfc'),
    ('#95b6c7', '#b4ccd9', '#d4e1e7', '#e8f0f3', '#f9fbfa'),
    ('#7599af', '#91b0c2', '#aec5d2', '#cadce5', '#e2ecf0'),
    ('#a4cee2', '#c1e0ef', '#d9edf7', '#eff9fc', '#fcffff'),
    ('#cfdee6', '#dce8ef', '#e6f0f4', '#f2f8fa', '#fcfefd'),
    ('#bfdbe9', '#d4e6ee', '#e4eef2', '#f1f6f7', '#fdfefd'),
    ('#d5e7ec', '#e4e1ee', '#e7f0f2', '#f4f5f1', '#fffffd'),
    ('#b8c8d1', '#cbd6dd', '#dbe3e7', '#eaf0f2', '#fbfcfa'),
)
LUMA = np.array([0.2126, 0.7152, 0.0722])


def palette(row):
    return np.array([tuple(bytes.fromhex(c[1:])) for c in PALETTES[row]], dtype=np.uint8)


def source_grids():
    """Normalized 2x4 panel bounds, center samples, five bins, no dithering.

    Robust luminance normalization recovers the source's intentional clusters;
    the fixed palette supplies restrained material colors. No painted pixel
    grids or procedural texture replacements are used.
    """
    with Image.open(SOURCE) as image:
        rgb = image.convert('RGB')
        w, h = rgb.size
        if w < 32 or h < 64:
            raise ValueError('Source must contain at least 16 samples per panel axis')
        grids = []
        for row in range(8):
            x, y = row % 2, row // 2
            panel = rgb.crop((round(x*w/2), round(y*h/4), round((x+1)*w/2), round((y+1)*h/4)))
            samples = np.asarray(panel.resize((16, 16), Image.Resampling.NEAREST), dtype=float)
            values = samples @ LUMA
            low, high = np.percentile(values, [2, 98])
            if high - low < 1:
                raise ValueError(f'Flat source panel: {NAMES[row]}')
            indices = np.clip(np.floor((values-low)/(high-low)*5), 0, 4).astype(np.uint8)
            if row == 3:
                # Join the source's left/right boundaries before toroidal motion.
                # Only two edge columns change; interior speed-line paint stays.
                seam = np.rint((indices[:, 0].astype(float)+indices[:, -1])/2).astype(np.uint8)
                indices[:, 0] = seam
                indices[:, -1] = seam
                assert np.array_equal(indices[:, 0], indices[:, -1])
            grids.append(indices)
        return grids


def frame_indices(base, row, frame):
    phase = frame % 4
    if row in (0, 1, 2, 3):
        # The finite sixteen-pixel field is a torus; 4 -> 0 is the same +4 step.
        return np.roll(base, 4*phase, axis=1)
    if row == 4:
        return np.rot90(base, -phase).copy()
    if row == 6:
        y, x = np.indices((16, 16))
        glint = ((x-y-4*phase) % 16) < 2
        return np.where(glint, 4, base).astype(np.uint8)
    return base.copy()


def build_atlas():
    atlas = np.full((128, 64, 4), 255, dtype=np.uint8)
    stats = []
    for row, base in enumerate(source_grids()):
        colors = palette(row)
        frames = [colors[frame_indices(base, row, f)] for f in range(4)]
        if row < 4:
            # Preserve visible material structure rather than near-white plastic.
            luminance = frames[0] @ LUMA
            assert luminance.std() >= 20, 'Insufficient pattern contrast'
            assert np.ptp(luminance) >= 50, 'Insufficient shade separation'
        assert np.array_equal(frame_indices(base, row, 4), frame_indices(base, row, 0))
        assert 2 <= len(np.unique(np.concatenate(frames).reshape(-1, 3), axis=0)) <= 6
        assert np.all(colors > 0), 'No pure black or zero channels'
        assert len({f.tobytes() for f in frames}) == (1 if row in (5, 7) else 4)
        if row == 3:
            for phase in range(4):
                assert np.array_equal(np.roll(frames[phase], 4, axis=1), frames[(phase+1) % 4])
        means = np.array([f.mean(axis=(0, 1)) for f in frames])
        if row != 6:
            assert np.array_equal(means, np.repeat(means[:1], 4, axis=0))
        changes = [np.any(frames[f] != frames[(f+1) % 4], axis=2).mean() for f in range(4)]
        assert max(changes)-min(changes) < .16, 'Uneven loop wrap'
        for f, pixels in enumerate(frames):
            atlas[row*16:(row+1)*16, f*16:(f+1)*16, :3] = pixels
        stats.append((float(means[0] @ LUMA), float(np.abs(means-means[:1]).max()), changes))
    brightness = np.array([s[0] for s in stats[:3]])
    assert np.all(np.abs(brightness - [240, 222, 195]) <= 1), brightness
    steps = -np.diff(brightness)
    assert 16 <= steps[0] <= 20 and 25 <= steps[1] <= 29, steps
    assert stats[2][0] >= 194, 'Mist must stay airy, lighter than smoke'
    assert atlas.shape == (128, 64, 4) and np.all(atlas[:, :, 3] == 255)
    return Image.fromarray(atlas), stats


def cube(sheet, tile, origin):
    """Same complete-face integer projection and shading as Fire's preview."""
    ox, oy = origin
    draw = ImageDraw.Draw(sheet)
    pixels = np.asarray(tile)[:, :, :3]
    faces = (((ox, oy), (3, -2), (3, 2), 1.0),
             ((ox, oy), (3, 2), (0, 4), .8),
             ((ox+48, oy+32), (3, -2), (0, 4), .6))
    for (x0, y0), u, v, light in faces:
        for y in range(16):
            for x in range(16):
                px, py = x0+x*u[0]+y*v[0], y0+x*u[1]+y*v[1]
                points = [(px, py), (px+u[0], py+u[1]),
                          (px+u[0]+v[0], py+u[1]+v[1]), (px+v[0], py+v[1])]
                draw.polygon(points, fill=tuple(np.rint(pixels[y, x].astype(float)*light).astype(int)))


def build_preview(atlas, stats):
    sheet = Image.new('RGB', (1040, 1184), '#13171e')
    draw = ImageDraw.Draw(sheet)
    title, label, small = [ImageFont.load_default(size=s) for s in (24, 16, 13)]
    draw.text((24, 18), 'BLOCK WIND  /  MATERIAL ATLAS', font=title, fill='#edf7fc')
    draw.text((24, 51), '16 px clusters  /  4-frame loop  /  opaque RGBA  /  no bloom', font=label, fill='#b4b8bf')
    for f in range(4):
        draw.text((24+f*128, 90), f'FRAME {f+1:02d}', font=small, fill='#c6cbd2')
    draw.text((558, 90), 'ROW / PALETTE', font=small, fill='#c6cbd2')
    draw.text((813, 90), '1.0 / 0.8 / 0.6', font=small, fill='#c6cbd2')
    sheet.paste(atlas.resize((512, 1024), Image.Resampling.NEAREST).convert('RGB'), (24, 120))
    for row, name in enumerate(NAMES):
        y = 120+row*128
        draw.text((558, y+15), f'{row}  {name.upper()}', font=label, fill='#edf7fc')
        draw.text((558, y+42), f"Mean Y' {stats[row][0]:.1f}", font=small, fill='#a0a8b4')
        draw.text((558, y+62), f'Frame RGB drift: {stats[row][1]:.2f}', font=small, fill='#a0a8b4')
        for i, color in enumerate(PALETTES[row]):
            draw.rectangle((558+i*29, y+87, 582+i*29, y+106), fill=color)
        cube(sheet, atlas.crop((0, row*16, 16, (row+1)*16)), (830, y+32))
    draw.text((24, 1159), 'Generated source art  |  Atlas shown at 8x nearest-neighbor  |  Cube faces use one complete tile', font=small, fill='#a0a8b4')
    return sheet


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format='PNG', optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Read-only byte comparison of both outputs')
    args = parser.parse_args()
    atlas, stats = build_atlas()
    outputs = {'blockwind.png': atlas, 'blockwind_preview.png': build_preview(atlas, stats)}
    failures = []
    for name, image in outputs.items():
        path, data = ROOT/name, png_bytes(image)
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                failures.append(name)
        else:
            path.write_bytes(data)
    for name, (luma, drift, changes) in zip(NAMES, stats):
        print(f"{name:7s} Y'={luma:.2f} RGB drift={drift:.2f} loop=" + '/'.join(f'{c:.0%}' for c in changes))
    if failures:
        raise SystemExit('MISMATCH: '+', '.join(failures)+'; run build_atlas.py')
    print(('CHECK PASSED' if args.check else 'BUILT')+': blockwind.png, blockwind_preview.png')


if __name__ == '__main__':
    main()
