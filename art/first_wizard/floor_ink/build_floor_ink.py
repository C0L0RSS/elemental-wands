#!/usr/bin/env python3
"""Export the First Wizard's animated floor decals from the model-generated PNGs.

    py -3 art/first_wizard/floor_ink/build_floor_ink.py --install
    py -3 art/first_wizard/floor_ink/build_floor_ink.py --check
"""

import argparse
import struct
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
GAME = REPO / "src/main/resources/assets/elementalwands/textures/misc"
SIZES = {"strip": (64, 32), "cap": (32, 32), "pool": (128, 128)}
INK, WET, DEEP, SHEEN, TIP, RIM, GLINT = range(7)
PALETTE = np.array(
    [tuple(bytes.fromhex(hex_rgb)) for hex_rgb in (
        "0d0a12", "1c1529", "3d245c", "4e2e84", "6a3cc0", "8a52e6", "d991ee",
    )],
    dtype=np.int32,
)
# Violet pigment is dimmer in the base pass. The full-bright pass restores it.
BASE_SHADE = [INK, WET, DEEP, SHEEN, DEEP, TIP, RIM]
GLOW_OPACITY = [0, 0, 0, 0, 224, 255, 255]


def box_sum(pixels, width, height):
    """Integrate source pixels in integer texel bins."""
    rows = np.arange(height) * pixels.shape[0] // height
    cols = np.arange(width) * pixels.shape[1] // width
    return np.add.reduceat(np.add.reduceat(pixels, rows, axis=0), cols, axis=1)


def sample_source(shape, width, height):
    """Key green away before measuring ink coverage and pigment per texel."""
    with Image.open(ROOT / "source" / f"{shape}.png") as image:
        rgb = np.array(image.convert("RGB"), dtype=np.int32)
    red, green, blue = np.moveaxis(rgb, -1, 0)
    ink = green < np.maximum(red, blue) + 24
    violet = ink & (blue >= 95) & (red >= 58) & (blue - green >= 36)
    glint = violet & (red >= 170) & (blue >= 170)
    planes = np.dstack((ink, violet, glint, rgb * ink[..., None], rgb * violet[..., None]))
    bins = box_sum(planes, width, height)
    hits = bins[..., :1]
    accents = bins[..., 1:2]
    glints = bins[..., 2:3]
    body_rgb = bins[..., 3:6] // np.maximum(hits, 1)
    violet_rgb = bins[..., 6:9] // np.maximum(accents, 1)
    # Keep thin veins and the meniscus when they fill enough of a texel to read.
    violet_rgb = np.minimum(violet_rgb + [9, 2, 18], 255)
    pigment = np.where(accents * 8 >= hits, (violet_rgb * 7 + body_rgb) // 8, body_rgb)
    # Thin, bright glints disappear in a box average. Preserve the strongest ones
    # when the source paints them across a substantial part of the ink coverage.
    pigment = np.where((glints > 0) & (glints * 4 >= hits), PALETTE[GLINT], pigment)
    ones = np.ones(rgb.shape[:2], dtype=np.uint8)
    coverage = hits * 255 // box_sum(ones, width, height)[..., None]
    return np.concatenate((pigment, coverage), axis=-1).astype(np.int32)


def join_profiles(strip, cap):
    """Blend the high-resolution art's sampled profiles across the joins.

    Cap joins the first column of the strip. Both strip endpoints share a profile
    so repeating the strip, including around a ring, needs no transparent gutter.
    """
    seam = (strip[:, 0] + strip[:, -1] + 1) // 2
    for column, weight in enumerate((4, 2, 1)):
        for texels in (strip[:, column], strip[:, -1 - column], cap[:, -1 - column]):
            texels[:] = (texels * (4 - weight) + seam * weight + 2) // 4


def build_layers(texels, shape):
    base_frames, glow_frames = [], []
    y, x = np.indices(texels.shape[:2], dtype=np.int32)
    if shape == "strip":
        x[:, -1] = 0
    if shape == "cap":
        x -= texels.shape[1] - 1
    rgb, coverage = texels[..., :3], texels[..., 3]
    alpha = np.where(coverage >= 137, 255, np.where(coverage >= 64, 160, 0))
    # A traveling four-phase shimmer nudges the source pigment across palette
    # thresholds. Coverage stays fixed so the warning footprint does not jump.
    for phase in range(4):
        wave = (x + 2 * y - phase * 4) % 16
        lift = (4 - np.abs(wave - 8)) * (rgb[..., 2] >= 29)
        lit = rgb + lift[..., None] * np.array([2, 1, 3], dtype=np.int32)
        distance = ((lit[..., None, :] - PALETTE) ** 2).sum(axis=-1)
        shade = distance.argmin(axis=-1)
        base_rgb = PALETTE[np.take(BASE_SHADE, shade)]
        glow_alpha = alpha * np.take(GLOW_OPACITY, shade) // 255
        base_frames.append(np.dstack((base_rgb * (alpha > 0)[..., None], alpha)))
        glow_frames.append(np.dstack((PALETTE[shade] * (glow_alpha > 0)[..., None], glow_alpha)))
    return tuple(np.array(frames, dtype=np.uint8) for frames in (base_frames, glow_frames))


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def png_bytes(frames):
    """RGBA PNG with filter 0 and stored DEFLATE blocks.

    Fix the PNG framing and block boundaries so encoders and zlib versions on
    different machines cannot choose different filters or compression streams.
    """
    count, height, width, channels = frames.shape
    rows = frames.reshape(count * height, width * channels)
    scanlines = np.pad(rows, ((0, 0), (1, 0))).tobytes()
    stream = bytearray(b"\x78\x01")
    for offset in range(0, len(scanlines), 65535):
        block = scanlines[offset:offset + 65535]
        final = offset + len(block) == len(scanlines)
        stream.extend(struct.pack("<BHH", final, len(block), len(block) ^ 65535))
        stream.extend(block)
    stream.extend(struct.pack(">I", zlib.adler32(scanlines)))
    header = struct.pack(">IIBBBBB", width, count * height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header) + png_chunk(b"IDAT", stream) + png_chunk(b"IEND", b"")


def build():
    strip = sample_source("strip", *SIZES["strip"])
    cap = sample_source("cap", *SIZES["cap"])
    # A transparent safety border keeps peripheral droplets inside the pool quad.
    pool = np.pad(sample_source("pool", 124, 124), ((2, 2), (2, 2), (0, 0)))
    join_profiles(strip, cap)
    layers = {shape: build_layers(texels, shape) for shape, texels in (
        ("strip", strip), ("cap", cap), ("pool", pool),
    )}
    for strip_frames, cap_frames in zip(layers["strip"], layers["cap"]):
        if not (np.array_equal(strip_frames[:, :, 0], strip_frames[:, :, -1])
                and np.array_equal(strip_frames[:, :, 0], cap_frames[:, :, -1])):
            raise ValueError("Floor ink join changed between the strip and cap")

    for shape, pair in layers.items():
        for suffix, frames in zip(("", "_glow"), pair):
            if len({frame.tobytes() for frame in frames}) != 4:
                raise ValueError(f"{shape}{suffix} must have four distinct frames")
            yield f"first_wizard_floor_{shape}{suffix}.png", png_bytes(frames)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--install", action="store_true", help="write art exports and runtime copies")
    mode.add_argument("--check", action="store_true", help="verify exports and runtime copies without writing")
    args = parser.parse_args()

    mismatches = []
    outputs = list(build())
    for name, data in outputs:
        for path in (ROOT / name, GAME / name):
            if args.install:
                path.write_bytes(data)
            elif not path.is_file() or path.read_bytes() != data:
                mismatches.append(path.relative_to(REPO).as_posix())
    if mismatches:
        raise SystemExit("MISMATCH: " + ", ".join(mismatches) + "; run build_floor_ink.py --install")
    print(("CHECK PASSED" if args.check else "INSTALLED") + f": {len(outputs)} floor ink sheets")


if __name__ == "__main__":
    main()
