"""Soul light pool: the stepped soul-fire glow drawn on the ground under the Hollow
Necromancer's glowing spells (SoulLightPool). Concentric value steps rather than a smooth
gradient, at 16 px per block across the rain marker's 2.5-block radius.

python3 tools/prepare_soul_light.py          writes the texture
python3 tools/prepare_soul_light.py --check  verifies it
"""
import argparse
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/elementalwands/textures/misc/soul_light_pool.png'
SIZE = 80
# Outer edge of each step as a fraction of the radius, its colour and its opacity: a pale
# core fading out through the soul-fire cyans of the bolts and flames.
STEPS = [
    (.14, (0xA6, 0xF7, 0xFF), 210),
    (.30, (0x7A, 0xF0, 0xFF), 180),
    (.46, (0x5A, 0xE6, 0xFF), 150),
    (.62, (0x46, 0xD8, 0xFF), 114),
    (.79, (0x38, 0xC4, 0xF6), 76),
    (.96, (0x2C, 0xAC, 0xEA), 38),
]
EDGE = STEPS[-1][1]


def build():
    image = Image.new('RGBA', (SIZE, SIZE), (*EDGE, 0))
    centre = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            distance = ((x + .5 - centre) ** 2 + (y + .5 - centre) ** 2) ** .5 / centre
            for edge, rgb, alpha in STEPS:
                if distance < edge:
                    image.putpixel((x, y), (*rgb, alpha))
                    break
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    image = build()
    if args.check:
        if not OUT.exists() or Image.open(OUT).convert('RGBA').tobytes() != image.tobytes():
            sys.exit(f'{OUT.relative_to(ROOT)} differs from tools/prepare_soul_light.py')
        print('Soul light pool texture verified')
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f'Wrote {OUT.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
