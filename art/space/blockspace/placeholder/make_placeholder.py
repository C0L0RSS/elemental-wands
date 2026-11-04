#!/usr/bin/env python3
"""Draw the stand-in Space atlas the study uses until the painted textures/blockspace.png exists.

Same layout as the painted atlas: 64 x 128 RGBA, four 16 x 16 frames across, eight bands down
(void, glass, nebula, rim, amethyst, blue, red, starlight). Glass and nebula carry per-pixel
alpha (bright frame and stars opaque, interior see-through); every other band is opaque.
Deterministic; python3 make_placeholder.py rewrites placeholder/blockspace.png beside it.
"""
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent / 'blockspace.png'
T = 16


def hexes(*cs):
    return np.array([tuple(bytes.fromhex(c[1:])) for c in cs], dtype=np.uint8)


def hash2(x, y, s):
    v = np.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453
    return v - np.floor(v)


def value_noise(seed, scale):
    """Tileable 16 x 16 value noise in [0, 1] from a coarse wrapped lattice."""
    n = T // scale
    lat = hash2(*np.meshgrid(np.arange(n), np.arange(n), indexing='xy'), seed)
    ys, xs = np.mgrid[0:T, 0:T] / scale
    x0, y0 = np.floor(xs).astype(int), np.floor(ys).astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = lat[y0 % n, x0 % n], lat[y0 % n, (x0 + 1) % n]
    c, d = lat[(y0 + 1) % n, x0 % n], lat[(y0 + 1) % n, (x0 + 1) % n]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def shade(values, pal):
    idx = np.clip((values * len(pal)).astype(int), 0, len(pal) - 1)
    return pal[idx]


def frame_mask(width=1):
    m = np.zeros((T, T), bool)
    m[:width, :] = m[-width:, :] = m[:, :width] = m[:, -width:] = True
    return m


STARS = [(3, 4), (11, 2), (7, 10), (13, 12), (2, 13)]


def tile(band, f):
    rgb = np.zeros((T, T, 3), np.uint8)
    a = np.full((T, T), 255, np.uint8)
    yy, xx = np.mgrid[0:T, 0:T]
    if band == 0:  # void: near-black, a few twinkling stars
        rgb[:] = shade(value_noise(1, 4) * .9, hexes('#05040a', '#080611', '#0c0918', '#110c20'))
        for i, (x, y) in enumerate(STARS[:4]):
            on = (i + f) % 4
            if on < 3:
                rgb[y, x] = hexes('#e9e0ff', '#a98cff', '#5fd8ff')[on]
    elif band == 1:  # glass: void stained glass, bright frame, stars drifting +4 px a frame
        rgb[:] = shade(value_noise(2, 8) * .99, hexes('#130a24', '#1b0f33', '#241443'))
        a[:] = 160
        for i, (x, y) in enumerate(STARS[:3]):
            sx = (x + 4 * f) % T
            rgb[y, sx] = hexes('#f2ecff', '#c7a6ff', '#7fe3ff')[i]
            a[y, sx] = 255
        fm = frame_mask()
        rgb[fm] = hexes('#9b5cff')[0]
        rgb[0, 0] = rgb[0, -1] = rgb[-1, 0] = rgb[-1, -1] = hexes('#d9b8ff')[0]
        a[fm] = 255
    elif band == 2:  # nebula: mottled plum cloud glass, scrolling, faint magenta frame
        n = np.roll(value_noise(3, 4) * .6 + value_noise(4, 8) * .4, 4 * f, axis=1)
        rgb[:] = shade(n, hexes('#1e0c31', '#331350', '#4c1b6e', '#6a278c', '#8c3aa8'))
        a[:] = (130 + n * 70).astype(np.uint8)
        fm = frame_mask()
        rgb[fm] = hexes('#c04fd8')[0]
        a[fm] = 220
    elif band == 3:  # rim: bright violet-to-magenta edge with a sweeping glint
        v = (yy + xx * .5) / (T * 1.5)
        rgb[:] = shade(v, hexes('#8f4dff', '#a866ff', '#c27bff', '#d98cf2', '#f08ee0'))
        g = ((xx + yy - 4 * f) % T) < 2
        rgb[g] = hexes('#fbe8ff')[0]
    elif band == 4:  # amethyst: faceted crystal, mostly static
        n = (np.abs(((xx * 3 + yy * 5) % 11) - 5) / 5) * .7 + value_noise(5, 4) * .3
        rgb[:] = shade(n, hexes('#4f2f86', '#6b42a8', '#8a5cc7', '#b08ae6', '#d9bfff'))
        rgb[(xx + yy) % 8 == 0] = hexes('#efe2ff')[0]
    elif band in (5, 6):  # blue / red star energy, swirling: a quarter turn a frame
        pal = (hexes('#1f47b8', '#2f6cf0', '#4f95ff', '#8cc0ff', '#e0efff') if band == 5
               else hexes('#9a1420', '#d6262a', '#ff4a35', '#ff8a5c', '#ffe0cc'))
        cx, cy = xx - 7.5, yy - 7.5
        r, ang = np.hypot(cx, cy), np.arctan2(cy, cx)
        n = .55 + .45 * np.sin(ang * 2 + r * .9) * np.exp(-r / 9)
        base = shade(np.clip(n - r / 22, 0, .999), pal)
        rgb[:] = np.rot90(base, -f)
    else:  # starlight: pale stepped star core
        d = np.abs(xx - 7.5) + np.abs(yy - 7.5)
        rgb[:] = shade(np.clip(1 - d / 15, 0, .999), hexes('#8c7bd6', '#b9a6ff', '#e6d8ff', '#fff8ff'))
        tw = (f % 2) * .5
        rgb[(d < 3 + tw * 2)] = hexes('#ffffff')[0]
    return rgb, a


def main():
    img = np.zeros((T * 8, T * 4, 4), np.uint8)
    for band in range(8):
        for f in range(4):
            rgb, a = tile(band, f)
            img[band * T:(band + 1) * T, f * T:(f + 1) * T, :3] = rgb
            img[band * T:(band + 1) * T, f * T:(f + 1) * T, 3] = a
    Image.fromarray(img, 'RGBA').save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()
