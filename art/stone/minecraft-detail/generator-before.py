#!/usr/bin/env python3
"""Generate the Natural Gray Stone VFX package at final pixel resolution.

Every texture is authored directly on its production grid.  Clustered shading,
broken strata, mineral seams, chipped silhouettes, and sparse edge highlights
replace smooth gradients or flat icon fills.  There is no random state,
resampling, antialiasing, blur, or external source artwork.
"""

from __future__ import annotations

import argparse
import hashlib
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/elementalwands/textures"

T = (0, 0, 0, 0)
INK = (57, 62, 65, 255)
RIFT = (70, 76, 79, 255)
BEDROCK = (96, 103, 106, 255)
SLATE_DARK = (119, 127, 130, 255)
SLATE = (145, 153, 155, 255)
SLATE_LIGHT = (182, 189, 188, 255)
WARM_DARK = (112, 118, 117, 255)
WARM = (151, 155, 149, 255)
WARM_LIGHT = (192, 195, 186, 255)
MINERAL_DARK = (126, 141, 132, 255)
MINERAL = (164, 180, 167, 255)
MINERAL_LIGHT = (198, 210, 196, 255)
DUST_DARK = (128, 137, 139, 210)
DUST = (172, 181, 181, 205)
DUST_LIGHT = (210, 216, 212, 220)
PALE = (222, 228, 224, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def alpha(color: tuple[int, int, int, int], value: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], value


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    value = x * 73 + y * 151 + seed * 199 + x * y * 17 + x * x * 11 + y * y * 7
    return (value ^ (value >> 5) ^ (value << 3)) & 255


def clustered_material(size: tuple[int, int], seed: int, palette, base) -> Image.Image:
    """Build large deliberate clusters instead of noisy single-pixel static."""
    image = Image.new("RGBA", size, base)
    draw = ImageDraw.Draw(image)
    width, height = size
    for y in range(0, height, 2):
        for x in range(0, width, 2):
            value = pixel_hash(x // 2, y // 2, seed)
            if value < 42:
                color = palette[0]
            elif value < 113:
                color = palette[1]
            elif value < 205:
                color = palette[2]
            else:
                color = palette[3]
            cluster_width = 1 + ((value >> 3) & 1)
            cluster_height = 1 + ((value >> 5) & 1)
            draw.rectangle(
                (x, y, min(width - 1, x + cluster_width), min(height - 1, y + cluster_height)),
                fill=color,
            )
    return image


def make_spike_material() -> Image.Image:
    image = stone_grain(16, 16, 17)
    draw = ImageDraw.Draw(image)
    # Long fractured facets read along the teeth instead of a brick pattern.
    poly(draw, [(1, 0), (5, 0), (4, 5), (6, 10), (4, 15), (1, 15)], SLATE_DARK)
    draw.line([(6, 0), (5, 5), (7, 10), (5, 15)], fill=SLATE_LIGHT)
    draw.line([(12, 0), (11, 4), (13, 8), (11, 12), (12, 15)], fill=BEDROCK)
    draw.line([(12, 5), (14, 7), (15, 7)], fill=SLATE_LIGHT)
    draw.line([(1, 10), (3, 9), (5, 10)], fill=BEDROCK)
    for x, y in ((8, 3), (9, 3), (2, 13), (13, 11)):
        draw.point((x, y), fill=MINERAL if x % 2 else MINERAL_LIGHT)
    return image


def stone_grain(width: int, height: int, seed: int) -> Image.Image:
    """Quiet rock clusters: detail is subordinate to the large carved forms."""
    image = Image.new("RGBA", (width, height), SLATE)
    draw = ImageDraw.Draw(image)
    shades = ((135, 143, 145, 255), (153, 161, 162, 255),
              (160, 167, 166, 255), (141, 148, 147, 255))
    for y in range(height):
        for x in range(width):
            value = pixel_hash(x // 3, y // 2, seed)
            draw.point((x, y), fill=shades[value % len(shades)])
            if pixel_hash(x, y, seed + 7) < 12:
                draw.point((x, y), fill=SLATE_DARK)
    return image


def make_fault_block() -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    cracks = (
        [(0, 11), (3, 9), (6, 10), (8, 7), (11, 8), (15, 4)],
        [(6, 10), (5, 6), (3, 4), (4, 1)],
        [(9, 7), (10, 4), (13, 2)],
        [(11, 8), (13, 11), (15, 12)],
    )
    for crack in cracks:
        draw.line(crack, fill=alpha(INK, 225), width=3)
        draw.line(crack[1:-1] if len(crack) > 2 else crack, fill=alpha(MINERAL_DARK, 240), width=1)
    for x, y, color in ((8, 7, PALE), (5, 6, MINERAL), (10, 4, MINERAL_LIGHT), (3, 9, WARM_LIGHT)):
        draw.point((x, y), fill=alpha(color, 225))
    # Loose gravel clusters sit beside the crack instead of a flat glowing decal.
    for x, y in ((1, 8), (3, 12), (7, 12), (12, 10), (14, 6)):
        draw.rectangle((x, y, x + 1, y + (x & 1)), fill=alpha(WARM_DARK, 205))
        draw.point((x, y), fill=alpha(WARM_LIGHT, 210))
    return image


def make_wall_material(ready: bool) -> Image.Image:
    image = stone_grain(16, 16, 37)
    draw = ImageDraw.Draw(image)
    # Two broad courses; edges tile across adjacent full-sized wall blocks.
    for y in (0, 8):
        draw.line((0, y, 15, y), fill=BEDROCK)
        draw.line((0, y + 1, 15, y + 1), fill=SLATE_LIGHT)
        draw.line((0, y + 7, 15, y + 7), fill=SLATE_DARK)
    for x, y in ((4, 1), (12, 9)):
        draw.line((x, y, x, y + 6), fill=BEDROCK)
        draw.line((x + 1, y + 1, x + 1, y + 5), fill=SLATE_LIGHT)
        draw.point((x + 1, y), fill=SLATE_DARK)
    draw.line((8, 4, 10, 4), fill=MINERAL_DARK)
    draw.point((9, 3), fill=MINERAL_LIGHT)
    draw.line((3, 12, 4, 12), fill=WARM_LIGHT)
    if ready:
        cracks = (
            [(2, 0), (4, 3), (3, 6), (7, 8), (6, 12), (9, 15)],
            [(4, 3), (8, 3), (10, 5), (14, 4)],
            [(7, 8), (11, 9), (13, 12), (15, 12)],
        )
        for crack in cracks:
            draw.line([(x + 1, y) for x, y in crack], fill=SLATE_LIGHT, width=1)
            draw.line(crack, fill=RIFT, width=1)
        for x, y in ((4, 3), (7, 8), (11, 9), (6, 12)):
            draw.point((x, y), fill=MINERAL_LIGHT)
    return image


def make_titan_dome_material() -> Image.Image:
    image = stone_grain(16, 16, 83)
    draw = ImageDraw.Draw(image)
    # A single dressed stone per block avoids a noisy cobblestone cage.
    draw.line([(0, 15), (0, 0), (15, 0)], fill=SLATE_LIGHT)
    draw.line([(1, 15), (15, 15), (15, 1)], fill=BEDROCK)
    draw.line([(2, 12), (2, 3), (3, 2), (12, 2)], fill=(168, 176, 176, 255))
    draw.line([(3, 13), (12, 13), (13, 12), (13, 3)], fill=SLATE_DARK)
    draw.line([(10, 0), (10, 2), (9, 3), (9, 5)], fill=BEDROCK)
    draw.point((10, 4), fill=WARM_LIGHT)
    draw.line((5, 9, 7, 9), fill=MINERAL_DARK)
    draw.point((6, 8), fill=MINERAL_LIGHT)
    return image


def make_dust(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    clusters = (
        ((3, 10, 3, 2), (9, 7, 4, 3), (6, 4, 2, 2), (12, 12, 2, 1)),
        ((2, 8, 4, 3), (8, 10, 5, 2), (10, 4, 3, 2), (5, 13, 2, 1)),
        ((3, 5, 3, 2), (7, 8, 4, 3), (11, 11, 3, 2), (4, 13, 4, 1)),
        ((2, 11, 3, 2), (6, 6, 5, 3), (11, 3, 2, 2), (12, 12, 2, 1)),
    )[frame]
    for index, (x, y, width, height) in enumerate(clusters):
        shadow = alpha(DUST_DARK, 165 + index * 10)
        body = alpha(DUST, 180 + index * 8)
        light = alpha(DUST_LIGHT, 180 + index * 8)
        poly(draw, [(x, y), (x + width - 1, y - 1), (x + width, y + height - 1),
                    (x + 1, y + height)], shadow)
        draw.rectangle((x + 1, y, x + width - 1, y + max(0, height - 1)), fill=body)
        draw.line((x + 1, y, x + max(1, width - 2), y), fill=light, width=1)
        if width >= 4:
            draw.point((x + width - 1, y + height - 1), fill=alpha(WARM_DARK, 190))
    return image


def make_shard(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    shapes = (
        [(7, 1), (12, 5), (10, 13), (6, 15), (3, 9), (4, 3)],
        [(4, 3), (11, 1), (14, 7), (10, 14), (4, 12), (2, 7)],
        [(6, 1), (13, 4), (12, 11), (8, 15), (3, 11), (2, 5)],
        [(3, 5), (8, 1), (14, 6), (12, 13), (5, 14), (1, 10)],
        [(5, 2), (12, 2), (14, 9), (9, 15), (3, 12), (2, 6)],
        [(4, 1), (10, 3), (14, 8), (11, 14), (5, 15), (1, 8)],
    )[frame]
    poly(draw, shapes, alpha(SLATE_DARK, 245 - frame * 8))
    inset = [(round(8 + (x - 8) * 0.72), round(8 + (y - 8) * 0.72)) for x, y in shapes]
    poly(draw, inset, alpha(SLATE if frame % 2 else WARM, 250 - frame * 8))
    light_face = [inset[0], inset[1], inset[2], (8, 8)]
    poly(draw, light_face, alpha(SLATE_LIGHT, 235 - frame * 8))
    draw.line((inset[0], (8, 8), inset[3]), fill=alpha(BEDROCK, 245 - frame * 8), width=1)
    draw.line((inset[5], (8, 8), inset[2]), fill=alpha(SLATE_DARK, 225 - frame * 8), width=1)
    draw.point((8, 8), fill=alpha(MINERAL_LIGHT, 235 - frame * 8))
    for x, y in ((2 + frame, 13 - frame // 2), (13 - frame // 2, 2 + frame)):
        if 0 <= x < 16 and 0 <= y < 16:
            draw.point((x, y), fill=alpha(DUST_LIGHT, 160))
    return image


def radial_crack(draw: ImageDraw.ImageDraw, center, angle: float, length: float, frame: int, branch: int) -> None:
    cx, cy = center
    points = [(cx, cy)]
    steps = max(3, round(length / 3))
    for step in range(1, steps + 1):
        distance = length * step / steps
        jitter = (((step * 5 + branch * 7 + frame * 3) % 5) - 2) * 0.34
        local_angle = angle + jitter * 0.12
        points.append((round(cx + math.cos(local_angle) * distance),
                       round(cy + math.sin(local_angle) * distance)))
    draw.line(points, fill=alpha(INK, 230 - frame * 14), width=3 if frame < 3 else 2)
    draw.line(points[1:-1], fill=alpha(MINERAL_DARK, 230 - frame * 16), width=1)
    if len(points) > 3:
        bx, by = points[len(points) // 2]
        branch_angle = angle + (-1 if branch % 2 else 1) * 0.7
        end = (round(bx + math.cos(branch_angle) * (3 + frame // 2)),
               round(by + math.sin(branch_angle) * (3 + frame // 2)))
        draw.line((bx, by, end[0], end[1]), fill=alpha(MINERAL_DARK, 190 - frame * 10), width=1)


def make_fault(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    center = (15.5 + ((frame % 3) - 1) * 0.5, 16)
    length = 5.0 + frame * 2.25
    arms = 5 + frame // 2
    for branch in range(arms):
        angle = branch / arms * math.tau + frame * 0.19
        radial_crack(draw, center, angle, length + ((branch * 3 + frame) % 4), frame, branch)
    if frame < 4:
        poly(draw, [(13, 16), (16, 12), (19, 16), (16, 20)], alpha(RIFT, 230 - frame * 25))
        draw.rectangle((15, 14, 16, 17), fill=alpha(MINERAL_LIGHT, 225 - frame * 22))
        draw.point((15, 15), fill=alpha(PALE, 220 - frame * 22))
    for index in range(7):
        angle = index / 7 * math.tau + frame * 0.4
        radius = 6 + frame * 2 + (index % 3)
        x = round(15.5 + math.cos(angle) * radius)
        y = round(16 + math.sin(angle) * radius)
        if 1 <= x < 31 and 1 <= y < 31:
            draw.rectangle((x, y, x + (index % 2), y + ((index + 1) % 2)),
                           fill=alpha(WARM_LIGHT if index % 3 else MINERAL, 190 - frame * 15))
    return image


def make_shockwave(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    cx, cy = 15.5, 19.0
    rx = 4.5 + frame * 2.25
    ry = 2.0 + frame * 0.9
    fade = 235 - frame * 22
    segments: list[tuple[int, int]] = []
    for step in range(81):
        angle = step / 80.0 * math.tau
        segment = int(angle / math.tau * 24)
        gap = (segment + frame * 3) % 8 == 0 or (frame >= 4 and (segment + frame) % 5 == 0)
        point = (round(cx + math.cos(angle) * rx), round(cy + math.sin(angle) * ry))
        if gap:
            if len(segments) >= 2:
                draw.line(segments, fill=alpha(INK, fade), width=3 if frame < 3 else 2)
                draw.line(segments[1:-1], fill=alpha(DUST_LIGHT, fade), width=1)
            segments = []
        else:
            segments.append(point)
    if len(segments) >= 2:
        draw.line(segments, fill=alpha(INK, fade), width=3 if frame < 3 else 2)
        draw.line(segments[1:-1], fill=alpha(DUST_LIGHT, fade), width=1)
    # Forward-leaning slab splinters make the ring feel like ground, not a magic halo.
    for index in range(8):
        angle = index / 8 * math.tau + frame * 0.22
        x = round(cx + math.cos(angle) * (rx + 2))
        y = round(cy + math.sin(angle) * (ry + 1))
        if 2 <= x < 30 and 2 <= y < 30:
            poly(draw, [(x - 1, y + 1), (x, y - 2 - index % 2), (x + 2, y), (x + 1, y + 2)],
                 alpha(WARM if index % 2 else SLATE, fade - 20))
            draw.point((x, y - 1), fill=alpha(WARM_LIGHT, fade))
    return image


def make_titan(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    fade = 250 if frame < 4 else 250 - (frame - 3) * 43
    # Separate carved fragments converge, then crumble outward. No floating face icon.
    for index in range(9):
        angle = index / 9 * math.tau + frame * .13
        radius = 24 - frame * 3 if frame < 4 else 14 + (frame - 4) * 4
        x = round(31 + math.cos(angle) * radius)
        y = round(32 + math.sin(angle) * radius * .68 - (index % 3) * 3)
        w = 4 + index % 3
        h = 5 + (index * 3) % 5
        poly(draw, [(x-w,y-h), (x+w-2,y-h-2), (x+w,y-h), (x,y-h+2)], alpha(SLATE_LIGHT, fade))
        poly(draw, [(x-w,y-h), (x,y-h+2), (x,y+h), (x-w,y+h-2)], alpha(SLATE, fade))
        poly(draw, [(x,y-h+2), (x+w,y-h), (x+w-1,y+h-2), (x,y+h)], alpha(SLATE_DARK, fade))
        draw.line((x-w+1,y-h+2,x-w+1,y+h-3), fill=alpha(WARM_LIGHT, fade))
        draw.line([(x+1,y-1), (x+3,y), (x+2,y+2)], fill=alpha(BEDROCK, fade))
        if index % 3 == 0:
            draw.point((x-2,y-1), fill=alpha(MINERAL_LIGHT, fade))
        draw.point((max(0,x-w-2),min(63,y+h+3)), fill=alpha(DUST_LIGHT, max(0,fade-60)))
    return image


def make_titan_sword() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Honed broad stone blade, stepped chips, shaded ridge and a distinct grip.
    blade = [(10, 18), (24, 3), (30, 1), (29, 7), (24, 12),
             (23, 12), (23, 14), (16, 21), (13, 21)]
    poly(draw, blade, BEDROCK)
    poly(draw, [(11, 18), (24, 4), (29, 2), (27, 7), (14, 20)], SLATE)
    poly(draw, [(14, 20), (27, 7), (28, 6), (27, 9), (16, 21)], SLATE_DARK)
    draw.line([(11, 18), (24, 4), (29, 2)], fill=SLATE_LIGHT, width=1)
    draw.line([(13, 18), (26, 5)], fill=(166, 175, 175, 255), width=1)
    draw.line([(18, 13), (20, 13), (20, 15)], fill=BEDROCK)
    draw.point((19, 12), fill=MINERAL_LIGHT)
    draw.line((23, 7, 24, 7), fill=MINERAL_DARK)
    draw.line((25, 5, 27, 3), fill=PALE)
    poly(draw, [(7, 17), (9, 16), (18, 25), (17, 27), (14, 24), (12, 23), (9, 20)], BEDROCK)
    draw.line([(8, 17), (11, 19), (14, 22), (17, 25)], fill=SLATE_LIGHT, width=2)
    draw.line((5, 28, 11, 22), fill=INK, width=4)
    draw.line((5, 27, 10, 22), fill=SLATE_DARK, width=1)
    for x, y in ((6, 27), (8, 25), (10, 23)):
        draw.line((x - 1, y - 1, x + 1, y + 1), fill=BEDROCK)
    poly(draw, [(2, 28), (4, 26), (8, 30), (6, 31), (4, 31)], SLATE_DARK)
    draw.line((3, 28, 5, 30), fill=SLATE_LIGHT)
    return image


def make_armor(leggings: bool) -> Image.Image:
    """Author each vanilla armor UV face separately; no random holes at seams."""
    image = canvas((64, 32))

    def plate(x, y, w, h, seed, edge=True):
        face = stone_grain(w, h, seed)
        d = ImageDraw.Draw(face)
        if edge:
            d.line((0, 0, w - 1, 0), fill=SLATE_LIGHT)
            d.line((0, 1, 0, h - 1), fill=SLATE_DARK)
            d.line((1, h - 1, w - 1, h - 1), fill=BEDROCK)
            d.point((w - 1, 0), fill=WARM_LIGHT)
        if w >= 4 and h >= 5:
            d.line((w - 2, 2, w - 2, 3), fill=SLATE_DARK)
            d.point((w - 3, 3), fill=MINERAL)
        image.alpha_composite(face, (x, y))

    draw = ImageDraw.Draw(image)
    if leggings:
        # Full trouser faces with aligned knee plates and darker articulated joints.
        plate(4, 16, 4, 4, 11)
        plate(8, 16, 4, 4, 12)
        for i in range(4):
            plate(i * 4, 20, 4, 12, 20 + i)
            draw.line((i * 4, 24, i * 4 + 3, 24), fill=BEDROCK)
            draw.line((i * 4, 28, i * 4 + 3, 28), fill=BEDROCK)
            draw.line((i * 4 + 1, 25, i * 4 + 3, 25), fill=SLATE_LIGHT)
        for x, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
            plate(x, 28, w, 4, 40 + x)
        draw.line((16, 28, 39, 28), fill=RIFT)
        draw.rectangle((23, 28, 24, 30), fill=SLATE_LIGHT)
    else:
        # Helmet: complete top, underside and sides, with a deliberate front visor.
        plate(8, 0, 8, 8, 1)
        plate(16, 0, 8, 8, 2)
        for i in range(4):
            plate(i * 8, 8, 8, 8, 3 + i)
        draw.rectangle((9, 11, 14, 12), fill=T)
        draw.rectangle((11, 13, 12, 15), fill=T)
        draw.line((9, 10, 14, 10), fill=SLATE_LIGHT)
        draw.line((10, 13, 10, 15), fill=SLATE_LIGHT)
        draw.line((13, 13, 13, 15), fill=BEDROCK)
        # Chest: shoulder deck, front breastplate, side plates and back.
        plate(20, 16, 8, 4, 13)
        plate(28, 16, 8, 4, 14)
        for x, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
            plate(x, 20, w, 12, 50 + x)
            draw.line((x, 27, x + w - 1, 27), fill=BEDROCK)
            draw.line((x + 1, 28, x + w - 1, 28), fill=SLATE_LIGHT)
        draw.line([(20, 20), (22, 22), (25, 22), (27, 20)], fill=BEDROCK)
        draw.line((23, 23, 23, 26), fill=SLATE_LIGHT)
        draw.point((24, 24), fill=MINERAL_LIGHT)
        # Shoulder cap / elbow seam / gauntlet, continuous across all four faces.
        plate(44, 16, 4, 4, 18)
        plate(48, 16, 4, 4, 19)
        for i in range(4):
            x = 40 + i * 4
            plate(x, 20, 4, 12, 70 + i)
            draw.line((x, 24, x + 3, 24), fill=BEDROCK)
            draw.line((x, 26, x + 3, 26), fill=RIFT)
            draw.line((x, 27, x + 3, 27), fill=SLATE_LIGHT)
        # Boots occupy the bottom five pixels of each leg face.
        for i in range(4):
            plate(i * 4, 27, 4, 5, 80 + i)
            draw.line((i * 4, 31, i * 4 + 3, 31), fill=RIFT)
        plate(8, 16, 4, 4, 90)
    return image


def make_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Cross-section of a fault with three differently tiered teeth.
    poly(draw, [(2, 26), (5, 21), (9, 22), (12, 17), (17, 19), (21, 14),
                (26, 17), (30, 13), (30, 29), (2, 29)], INK)
    poly(draw, [(4, 26), (6, 23), (10, 24), (13, 20), (17, 21), (21, 17),
                (26, 19), (28, 17), (28, 27), (4, 27)], WARM_DARK)
    for points, face in (
        ([(5, 23), (8, 13), (11, 8), (13, 22)], SLATE),
        ([(13, 20), (16, 7), (19, 3), (21, 18)], WARM),
        ([(21, 17), (24, 10), (27, 7), (28, 18)], SLATE_DARK),
    ):
        poly(draw, points, INK)
        inset = [(x + (1 if x < sum(px for px, _ in points) / len(points) else -1), y + 1)
                 for x, y in points]
        poly(draw, inset, face)
    draw.line((3, 25, 8, 23, 13, 25, 18, 21, 24, 23, 29, 19), fill=MINERAL_DARK, width=2)
    draw.line((5, 25, 9, 24, 13, 25, 18, 22, 24, 23), fill=MINERAL_LIGHT, width=1)
    for x, y in ((9, 15), (11, 20), (17, 9), (18, 15), (25, 12), (26, 16)):
        draw.point((x, y), fill=SLATE_LIGHT if x % 2 else WARM_LIGHT)
    return image


def make_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    outline = [(3, 27), (3, 9), (7, 5), (25, 5), (29, 9), (29, 27)]
    poly(draw, outline, INK)
    draw.rectangle((5, 8, 27, 25), fill=BEDROCK)
    courses = ((6, 8, 15, 13), (16, 8, 26, 13), (5, 14, 12, 19),
               (13, 14, 22, 19), (23, 14, 27, 19), (6, 20, 16, 25), (17, 20, 26, 25))
    for index, (x0, y0, x1, y1) in enumerate(courses):
        draw.rectangle((x0, y0, x1, y1), fill=SLATE_DARK if index % 3 else WARM_DARK)
        draw.line((x0 + 1, y0 + 1, x1 - 1, y0 + 1), fill=SLATE_LIGHT, width=1)
        draw.line((x1, y0 + 1, x1, y1), fill=RIFT, width=1)
        if index % 2:
            draw.point((x0 + 2, y1 - 1), fill=WARM_LIGHT)
    crack = [(9, 5), (12, 11), (10, 16), (16, 19), (14, 27)]
    draw.line(crack, fill=INK, width=3)
    draw.line(crack[1:-1], fill=MINERAL_DARK, width=1)
    draw.line((12, 11, 18, 10, 23, 12), fill=MINERAL_DARK, width=1)
    for x, y in ((12, 11), (10, 16), (16, 19)):
        draw.point((x, y), fill=MINERAL_LIGHT)
    return image


def make_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Dome silhouette doubles as a mountain crown around a plated Titan helm.
    poly(draw, [(1, 25), (3, 16), (8, 10), (13, 6), (16, 2), (20, 7),
                (25, 10), (29, 17), (31, 25)], INK)
    poly(draw, [(4, 24), (5, 17), (10, 12), (14, 9), (16, 5), (19, 10),
                (24, 12), (27, 18), (28, 24)], BEDROCK)
    draw.line((5, 21, 10, 18, 14, 19, 18, 15, 23, 18, 27, 21), fill=WARM_DARK, width=2)
    draw.line((7, 20, 11, 18, 14, 19, 18, 16, 23, 18), fill=SLATE_LIGHT, width=1)
    poly(draw, [(10, 15), (16, 10), (23, 16), (21, 25), (16, 29), (11, 25)], RIFT)
    poly(draw, [(12, 16), (16, 13), (21, 17), (19, 23), (16, 26), (13, 23)], SLATE_DARK)
    poly(draw, [(10, 17), (7, 11), (8, 20)], WARM_DARK)
    poly(draw, [(22, 17), (26, 11), (24, 21)], WARM_DARK)
    draw.line((12, 19, 16, 18, 20, 20), fill=MINERAL_DARK, width=3)
    draw.line((14, 19, 16, 19, 19, 20), fill=MINERAL_LIGHT, width=1)
    for x, y in ((6, 17), (11, 12), (20, 11), (26, 17), (16, 7), (14, 24), (19, 23)):
        draw.point((x, y), fill=WARM_LIGHT if x % 2 else SLATE_LIGHT)
    return image


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for frame in range(4):
        generated[TEXTURES / f"particle/stone/dust_{frame}.png"] = make_dust(frame)
    for frame in range(6):
        generated[TEXTURES / f"particle/stone/shard_{frame}.png"] = make_shard(frame)
        generated[TEXTURES / f"particle/stone/fault_{frame}.png"] = make_fault(frame)
        generated[TEXTURES / f"particle/stone/shockwave_{frame}.png"] = make_shockwave(frame)
    for frame in range(8):
        generated[TEXTURES / f"particle/stone/titan_{frame}.png"] = make_titan(frame)
    generated.update({
        TEXTURES / "block/stone_spike.png": make_spike_material(),
        TEXTURES / "block/stone_fault.png": make_fault_block(),
        TEXTURES / "block/stone_wall.png": make_wall_material(False),
        TEXTURES / "block/stone_wall_ready.png": make_wall_material(True),
        TEXTURES / "block/titan_dome.png": make_titan_dome_material(),
        TEXTURES / "item/titan_sword.png": make_titan_sword(),
        TEXTURES / "entity/equipment/humanoid/titan_armor.png": make_armor(False),
        TEXTURES / "entity/equipment/humanoid_leggings/titan_armor.png": make_armor(True),
        TEXTURES / "gui/ability/stone_primary.png": make_primary_icon(),
        TEXTURES / "gui/ability/stone_secondary.png": make_secondary_icon(),
        TEXTURES / "gui/ability/stone_ultimate.png": make_ultimate_icon(),
    })
    return generated


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: differs from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        visible_colors = {rgba for _count, rgba in colors if rgba[3] > 0}
        assert len(visible_colors) >= 4, f"{path}: too few material colors ({len(visible_colors)})"
        bbox = reopened.getbbox()
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
    return len(colors), bbox, digest


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    for family, count in (("dust", 4), ("shard", 6), ("fault", 6), ("shockwave", 6), ("titan", 8)):
        frames = [outputs[TEXTURES / f"particle/stone/{family}_{frame}.png"].tobytes()
                  for frame in range(count)]
        assert len(set(frames)) == count, f"{family}: duplicate frames"


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb = 112
    label_height = 27
    columns = 6
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_height)), (34, 35, 34, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        column = index % columns
        row = index // columns
        x = column * thumb
        y = row * (thumb + label_height)
        checker = Image.new("RGBA", (thumb, thumb), (205, 199, 185, 255))
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 14):
            for cx in range(0, thumb, 14):
                if (cx // 14 + cy // 14) % 2:
                    checker_draw.rectangle((cx, cy, cx + 13, cy + 13), fill=(154, 153, 147, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), str(relative.parent)[-17:], fill=(185, 169, 132, 255), font=font)
        draw.text((x + 3, y + thumb + 13), relative.stem[:19], fill=(237, 225, 197, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="replace only this script's known generated output paths")
    args = parser.parse_args()
    outputs = output_map()
    validate_frame_families(outputs)

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists():
            with Image.open(path) as existing:
                existing.load()
                differs = (existing.mode != "RGBA" or existing.size != image.size
                           or existing.tobytes() != image.tobytes())
            if differs:
                if not args.replace:
                    raise SystemExit(f"Refusing to overwrite differing Stone texture: {path.relative_to(ROOT)}")
                image.save(path, format="PNG", optimize=False, compress_level=9)
        else:
            image.save(path, format="PNG", optimize=False, compress_level=9)

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_stone_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
