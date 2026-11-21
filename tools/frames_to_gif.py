#!/usr/bin/env python3
"""Assemble a fixture's recorded frames into an animated GIF, for reviewing motion (no ffmpeg needed).

    py -3 tools/frames_to_gif.py <screenshots dir> <prefix> <out.gif> [--fps 20] [--every 1] [--from N] [--to N]
                                 [--width 640] [--max-mb 15] [--crop X Y W H]

Frames are the files <prefix>-<tick>.png in the directory, in tick order (the First Wizard fight fixture writes
rec-<case>-<tick>.png with -PwizardRecord=case[:every]). --every keeps every n-th of them; --fps is the playback
rate (20 is the game's own speed for frames recorded every tick). Each frame is downscaled to --width and quantized
to one shared palette. --crop first cuts each frame to that box (source pixels), to frame a subject
more closely. If the result is over --max-mb, it is written again at a smaller width until it fits.
Requires Pillow.
"""

import argparse
import re
from pathlib import Path

from PIL import Image


def frames(directory, prefix, every, first, last):
    pattern = re.compile(re.escape(prefix) + r"-(\d+)\.png$")
    found = []
    for path in Path(directory).iterdir():
        match = pattern.match(path.name)
        if match and first <= int(match.group(1)) <= last:
            found.append((int(match.group(1)), path))
    found.sort()
    return [path for _, path in found[::every]]


def build(paths, out, fps, width, crop=None):
    images = []
    for path in paths:
        image = Image.open(path).convert("RGB")
        if crop:
            x, y, w, h = crop
            image = image.crop((x, y, x + w, y + h))
        height = round(image.height * width / image.width)
        images.append(image.resize((width, height), Image.LANCZOS))
    # One palette for the whole clip, from a strip of sampled frames, so colours do not flicker frame to frame.
    sample = images[:: max(1, len(images) // 8)]
    strip = Image.new("RGB", (images[0].width, images[0].height * len(sample)))
    for i, image in enumerate(sample):
        strip.paste(image, (0, i * images[0].height))
    palette = strip.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    quantized = [image.quantize(palette=palette, dither=Image.Dither.NONE) for image in images]
    quantized[0].save(out, save_all=True, append_images=quantized[1:], duration=round(1000 / fps), loop=0, optimize=False)
    return Path(out).stat().st_size


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory")
    parser.add_argument("prefix")
    parser.add_argument("out")
    parser.add_argument("--fps", type=float, default=20)
    parser.add_argument("--every", type=int, default=1)
    parser.add_argument("--from", dest="first", type=int, default=0)
    parser.add_argument("--to", dest="last", type=int, default=10 ** 9)
    parser.add_argument("--width", type=int, default=640)
    parser.add_argument("--max-mb", type=float, default=15)
    parser.add_argument("--crop", type=int, nargs=4, metavar=("X", "Y", "W", "H"))
    args = parser.parse_args()
    paths = frames(args.directory, args.prefix, args.every, args.first, args.last)
    if not paths:
        raise SystemExit(f"no frames {args.prefix}-<tick>.png in {args.directory}")
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    width = args.width
    while True:
        size = build(paths, args.out, args.fps, width, args.crop)
        if size <= args.max_mb * 1024 * 1024 or width <= 240:
            break
        width = int(width * .85)
    print(f"{args.out}: {len(paths)} frames at {width} wide, {size / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
