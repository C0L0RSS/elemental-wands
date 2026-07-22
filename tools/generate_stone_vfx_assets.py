#!/usr/bin/env python3
"""Generate the Raw Seismic Stone VFX package at final pixel resolution.

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
INK = (28, 30, 30, 255)
RIFT = (39, 38, 35, 255)
BEDROCK = (47, 51, 51, 255)
SLATE_DARK = (58, 63, 64, 255)
SLATE = (75, 81, 81, 255)
SLATE_LIGHT = (96, 101, 97, 255)
WARM_DARK = (77, 72, 65, 255)
WARM = (104, 96, 83, 255)
WARM_LIGHT = (132, 121, 99, 255)
OCHRE_DARK = (116, 82, 39, 255)
OCHRE = (164, 123, 58, 255)
OCHRE_LIGHT = (203, 164, 88, 255)
DUST_DARK = (121, 111, 94, 210)
DUST = (174, 157, 127, 205)
DUST_LIGHT = (213, 196, 157, 220)
PALE = (232, 216, 172, 255)


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
    image = clustered_material((16, 16), 17, (INK, SLATE_DARK, SLATE, WARM), BEDROCK)
    draw = ImageDraw.Draw(image)
    # Geological layers continue across model faces while chipped breaks keep it rough.
    for points in (
        [(0, 4), (4, 3), (8, 5), (12, 4), (15, 5)],
        [(0, 9), (3, 10), (7, 8), (11, 10), (15, 9)],
        [(0, 14), (5, 13), (10, 15), (15, 13)],
    ):
        draw.line(points, fill=WARM_DARK, width=1)
    draw.line([(2, 1), (5, 4), (4, 7), (8, 10), (7, 14)], fill=RIFT, width=2)
    draw.line([(3, 1), (6, 4), (5, 7), (9, 10)], fill=OCHRE_DARK, width=1)
    for x, y in ((1, 7), (11, 2), (13, 11), (4, 12), (9, 6)):
        draw.rectangle((x, y, x + 1, y + 1), fill=OCHRE if (x + y) % 2 else WARM_LIGHT)
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
        draw.line(crack[1:-1] if len(crack) > 2 else crack, fill=alpha(OCHRE_DARK, 240), width=1)
    for x, y, color in ((8, 7, PALE), (5, 6, OCHRE), (10, 4, OCHRE_LIGHT), (3, 9, WARM_LIGHT)):
        draw.point((x, y), fill=alpha(color, 225))
    # Loose gravel clusters sit beside the crack instead of a flat glowing decal.
    for x, y in ((1, 8), (3, 12), (7, 12), (12, 10), (14, 6)):
        draw.rectangle((x, y, x + 1, y + (x & 1)), fill=alpha(WARM_DARK, 205))
        draw.point((x, y), fill=alpha(WARM_LIGHT, 210))
    return image


def make_wall_material(ready: bool) -> Image.Image:
    image = clustered_material((16, 16), 41 if ready else 37,
                               (INK, SLATE_DARK, WARM_DARK, WARM), BEDROCK)
    draw = ImageDraw.Draw(image)
    # Three offset courses of broad interlocking slabs.
    seams = [4, 9, 13]
    for y in seams:
        wobble = [(0, y), (4, y + (y % 2)), (8, y - 1), (12, y), (15, y - (y % 3 == 0))]
        draw.line(wobble, fill=INK, width=1)
        if y != 13:
            draw.line([(x, min(15, py + 1)) for x, py in wobble[1:-1]], fill=SLATE_LIGHT, width=1)
    for x, y0, y1 in ((5, 0, 4), (11, 4, 9), (3, 9, 13), (12, 13, 15)):
        draw.line((x, y0, x + ((x + y0) % 2), y1), fill=RIFT, width=1)
    # Material inclusions and chipped corners give the 16px tile a newer-block density.
    for x, y, color in (
        (1, 2, WARM_LIGHT), (2, 2, WARM), (8, 1, SLATE_LIGHT), (13, 3, OCHRE_DARK),
        (7, 7, WARM_LIGHT), (14, 6, SLATE), (1, 11, SLATE_LIGHT), (8, 12, WARM),
        (5, 15, OCHRE_DARK), (14, 14, WARM_LIGHT),
    ):
        draw.point((x, y), fill=color)
    if ready:
        cracks = (
            [(2, 0), (4, 3), (3, 6), (7, 8), (6, 12), (9, 15)],
            [(4, 3), (8, 3), (10, 5), (14, 4)],
            [(7, 8), (11, 9), (13, 12), (15, 12)],
        )
        for crack in cracks:
            draw.line(crack, fill=INK, width=2)
            draw.line(crack[1:-1], fill=OCHRE_DARK, width=1)
        for x, y in ((4, 3), (7, 8), (11, 9), (6, 12)):
            draw.point((x, y), fill=OCHRE_LIGHT)
    return image


def make_titan_dome_material() -> Image.Image:
    image = clustered_material((16, 16), 83, (INK, BEDROCK, SLATE_DARK, WARM_DARK), RIFT)
    draw = ImageDraw.Draw(image)
    plates = (
        [(0, 0), (7, 0), (6, 5), (1, 6)],
        [(8, 0), (15, 0), (15, 5), (11, 6), (7, 4)],
        [(0, 7), (5, 5), (10, 7), (9, 12), (3, 11)],
        [(11, 7), (15, 6), (15, 14), (10, 12)],
        [(0, 12), (6, 11), (10, 15), (0, 15)],
    )
    for plate in plates:
        draw.line(plate + [plate[0]], fill=INK, width=1)
    draw.line([(0, 8), (4, 9), (7, 7), (10, 10), (15, 9)], fill=OCHRE_DARK, width=1)
    for x, y in ((1, 1), (5, 2), (10, 2), (13, 4), (3, 8), (7, 9), (12, 9), (5, 13), (13, 14)):
        draw.rectangle((x, y, min(15, x + 1), y), fill=SLATE_LIGHT if (x + y) % 3 else WARM_LIGHT)
    for x, y in ((4, 9), (7, 7), (10, 10)):
        draw.point((x, y), fill=OCHRE)
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
    poly(draw, shapes, alpha(INK, 245 - frame * 8))
    inset = [(round(8 + (x - 8) * 0.72), round(8 + (y - 8) * 0.72)) for x, y in shapes]
    poly(draw, inset, alpha(SLATE_DARK if frame % 2 else WARM_DARK, 250 - frame * 8))
    light_face = [inset[0], inset[1], inset[2], (8, 8)]
    poly(draw, light_face, alpha(SLATE_LIGHT, 235 - frame * 8))
    draw.line((inset[0], (8, 8), inset[3]), fill=alpha(RIFT, 245 - frame * 8), width=1)
    draw.line((inset[5], (8, 8), inset[2]), fill=alpha(OCHRE_DARK, 225 - frame * 8), width=1)
    draw.point((8, 8), fill=alpha(OCHRE_LIGHT, 235 - frame * 8))
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
    draw.line(points[1:-1], fill=alpha(OCHRE_DARK, 230 - frame * 16), width=1)
    if len(points) > 3:
        bx, by = points[len(points) // 2]
        branch_angle = angle + (-1 if branch % 2 else 1) * 0.7
        end = (round(bx + math.cos(branch_angle) * (3 + frame // 2)),
               round(by + math.sin(branch_angle) * (3 + frame // 2)))
        draw.line((bx, by, end[0], end[1]), fill=alpha(OCHRE_DARK, 190 - frame * 10), width=1)


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
        draw.rectangle((15, 14, 16, 17), fill=alpha(OCHRE_LIGHT, 225 - frame * 22))
        draw.point((15, 15), fill=alpha(PALE, 220 - frame * 22))
    for index in range(7):
        angle = index / 7 * math.tau + frame * 0.4
        radius = 6 + frame * 2 + (index % 3)
        x = round(15.5 + math.cos(angle) * radius)
        y = round(16 + math.sin(angle) * radius)
        if 1 <= x < 31 and 1 <= y < 31:
            draw.rectangle((x, y, x + (index % 2), y + ((index + 1) % 2)),
                           fill=alpha(WARM_LIGHT if index % 3 else OCHRE, 190 - frame * 15))
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
    fade = 255 if frame < 5 else 255 - (frame - 4) * 42
    cx = 31.5
    ground = 54
    # A broad mountain silhouette forms from separate strata and becomes a horned helm.
    silhouette = [
        (5, ground), (8, 45), (15, 42), (18, 31), (24, 34), (28, 17),
        (32, 9), (36, 18), (40, 31), (46, 27), (49, 40), (57, 44), (60, ground),
    ]
    assembly = min(1.0, (frame + 1) / 4.0)
    formed = []
    for index, (x, y) in enumerate(silhouette):
        drift = (1.0 - assembly) * (10 + (index * 7 % 12))
        direction = -1 if x < cx else 1
        formed.append((x + direction * drift, y + (1.0 - assembly) * (index % 4) * 3))
    poly(draw, formed + [(60, 58), (4, 58)], alpha(INK, fade))
    inner = [(9, 53), (13, 46), (20, 43), (22, 34), (28, 37), (31, 18),
             (34, 24), (38, 37), (45, 33), (47, 44), (55, 47), (57, 53)]
    inner_formed = []
    for index, (x, y) in enumerate(inner):
        drift = (1.0 - assembly) * (7 + (index * 5 % 9))
        inner_formed.append((x + (-drift if x < cx else drift), y))
    poly(draw, inner_formed + [(56, 56), (8, 56)], alpha(BEDROCK, fade))
    # Layered basalt armor plates.
    for y, left, right, offset in ((46, 10, 55, 0), (39, 17, 49, 2), (33, 22, 44, -1), (27, 26, 39, 1)):
        if assembly < 0.35 and y < 40:
            continue
        draw.line((left + offset, y, right + offset, y + (frame + y) % 2),
                  fill=alpha(WARM_DARK, fade), width=3)
        draw.line((left + offset + 2, y - 1, right + offset - 3, y),
                  fill=alpha(SLATE_LIGHT, fade), width=1)
    # Helm brow, cheek plates, and narrow ochre fault visor.
    if frame >= 2:
        poly(draw, [(24, 24), (31, 19), (40, 25), (37, 34), (32, 38), (26, 34)], alpha(RIFT, fade))
        poly(draw, [(26, 25), (31, 22), (38, 26), (35, 31), (32, 34), (28, 31)], alpha(SLATE_DARK, fade))
        draw.line((27, 28, 31, 27, 36, 29), fill=alpha(OCHRE_DARK, fade), width=3)
        draw.line((29, 28, 31, 28, 35, 29), fill=alpha(OCHRE_LIGHT, fade), width=1)
        poly(draw, [(24, 26), (19, 18), (20, 31)], alpha(WARM_DARK, fade))
        poly(draw, [(39, 27), (45, 18), (43, 32)], alpha(WARM_DARK, fade))
    # Fractures and mineral inclusions stay sparse so Stone never looks like a glowing spell.
    cracks = (
        [(14, 49), (21, 46), (24, 40), (30, 38)],
        [(50, 47), (45, 43), (42, 37), (36, 35)],
        [(31, 53), (29, 47), (33, 43), (31, 38)],
    )
    for index, crack in enumerate(cracks):
        shifted = [(x + ((frame + index) % 3) - 1, y) for x, y in crack]
        draw.line(shifted, fill=alpha(RIFT, fade), width=2)
        draw.line(shifted[1:-1], fill=alpha(OCHRE_DARK, max(0, fade - 20)), width=1)
    # Detached slabs move inward during assembly and outward during the final crumble.
    for index in range(12):
        angle = index / 12 * math.tau + frame * 0.23
        if frame < 4:
            radius = 25 - frame * 4 + index % 3
        else:
            radius = 9 + (frame - 4) * 5 + index % 4
        x = round(cx + math.cos(angle) * radius)
        y = round(35 + math.sin(angle) * radius * 0.65)
        size = 2 + (index + frame) % 3
        poly(draw, [(x - size, y), (x, y - size), (x + size, y - 1), (x + 1, y + size)],
             alpha(INK, max(45, fade - 20)))
        poly(draw, [(x - size + 1, y), (x, y - size + 1), (x + size - 1, y - 1), (x, y + size - 1)],
             alpha(WARM if index % 2 else SLATE, max(40, fade - 25)))
        if index % 3 == 0:
            draw.point((x, y - 1), fill=alpha(OCHRE, max(35, fade - 10)))
    return image


def make_titan_sword() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Massive asymmetrical slab blade, broad enough to read as a transformed weapon.
    outline = [(4, 28), (7, 31), (11, 26), (15, 25), (28, 9), (29, 3), (24, 2),
               (9, 17), (8, 22), (3, 26)]
    poly(draw, outline, INK)
    blade = [(9, 25), (14, 23), (27, 8), (27, 4), (24, 4), (11, 18), (10, 22)]
    poly(draw, blade, SLATE_DARK)
    poly(draw, [(12, 22), (16, 21), (26, 8), (26, 5), (23, 7), (13, 19)], SLATE)
    draw.line((14, 22, 25, 7), fill=SLATE_LIGHT, width=2)
    draw.line((12, 23, 24, 8), fill=WARM_LIGHT, width=1)
    draw.line((22, 5, 26, 6), fill=OCHRE_DARK, width=2)
    draw.line((17, 15, 20, 16, 22, 12), fill=RIFT, width=2)
    draw.line((18, 15, 20, 16, 22, 13), fill=OCHRE, width=1)
    # Tiered stone guard and wrapped grip use multiple clusters at item scale.
    poly(draw, [(6, 19), (10, 17), (16, 23), (14, 27)], INK)
    poly(draw, [(8, 20), (10, 19), (14, 23), (13, 25)], WARM)
    draw.line((7, 21, 13, 26), fill=OCHRE_DARK, width=1)
    draw.line((6, 24, 10, 28), fill=WARM_DARK, width=4)
    draw.line((6, 24, 9, 27), fill=WARM_LIGHT, width=1)
    poly(draw, [(3, 26), (6, 24), (10, 28), (8, 31), (5, 31)], INK)
    draw.rectangle((5, 27, 7, 29), fill=OCHRE_DARK)
    draw.point((6, 27), fill=OCHRE_LIGHT)
    for x, y in ((21, 9), (17, 18), (12, 21), (25, 6)):
        draw.point((x, y), fill=PALE if (x + y) % 2 else WARM_LIGHT)
    return image


HUMANOID_MASK = (
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "################################................................",
    "################################................................",
    "################################................................",
    "#########..##..#################................................",
    "####..###..##..###..############................................",
    ".......##......##.......########................................",
    ".......###....###.........####..................................",
    ".......###....###...............................................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "................######....##############################........",
    "................######....##############################........",
    "................#######..###############################........",
    "................########################################........",
    "................########################################........",
    "................#################################..#####........",
    "...######.......########################.##.#.#......#..........",
    "########################################........................",
    "########################################........................",
    "################....########....########........................",
    "################.....######......######.........................",
    "################......####........####..........................",
)

LEGGINGS_MASK = (
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "##################....####....##########........................",
    "########################################........................",
    "................########################........................",
    "................########################........................",
    "................########################........................",
)


def make_armor(mask: tuple[str, ...], seed: int) -> Image.Image:
    image = canvas((64, 32))
    pixels = image.load()
    palette = (INK, BEDROCK, SLATE_DARK, WARM_DARK, SLATE, WARM)
    for y, row in enumerate(mask):
        for x, marker in enumerate(row):
            if marker != "#":
                continue
            value = pixel_hash(x // 2, y // 2, seed)
            color = palette[min(len(palette) - 1, value * len(palette) // 256)]
            # Dark external edges give each UV island a carved plate silhouette.
            edge = any(
                yy < 0 or yy >= 32 or xx < 0 or xx >= 64 or mask[yy][xx] != "#"
                for xx, yy in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
            )
            if edge:
                color = INK if (x + y + seed) % 5 else OCHRE_DARK
            elif (x + y * 3 + seed) % 13 == 0:
                color = SLATE_LIGHT
            elif (x * 5 + y + seed) % 19 == 0:
                color = WARM_LIGHT
            pixels[x, y] = color
    draw = ImageDraw.Draw(image)
    # Sparse stratified seams and rivets are clipped back through the armor mask.
    overlay = canvas((64, 32))
    overlay_draw = ImageDraw.Draw(overlay)
    for y in (3, 10, 14, 22, 26, 29):
        overlay_draw.line((0, y, 55, y + (y % 2)), fill=OCHRE_DARK, width=1)
    for x, y in ((9, 2), (14, 5), (3, 9), (20, 10), (27, 13), (10, 21), (24, 24),
                 (35, 22), (49, 25), (5, 28), (18, 30), (34, 29)):
        overlay_draw.point((x, y), fill=OCHRE_LIGHT)
        if x + 1 < 64:
            overlay_draw.point((x + 1, y), fill=RIFT)
    mask_image = Image.new("L", (64, 32), 0)
    mask_pixels = mask_image.load()
    for y, row in enumerate(mask):
        for x, marker in enumerate(row):
            if marker == "#":
                mask_pixels[x, y] = 255
    image.alpha_composite(Image.composite(overlay, canvas((64, 32)), mask_image))
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
    draw.line((3, 25, 8, 23, 13, 25, 18, 21, 24, 23, 29, 19), fill=OCHRE_DARK, width=2)
    draw.line((5, 25, 9, 24, 13, 25, 18, 22, 24, 23), fill=OCHRE_LIGHT, width=1)
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
    draw.line(crack[1:-1], fill=OCHRE_DARK, width=1)
    draw.line((12, 11, 18, 10, 23, 12), fill=OCHRE_DARK, width=1)
    for x, y in ((12, 11), (10, 16), (16, 19)):
        draw.point((x, y), fill=OCHRE_LIGHT)
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
    draw.line((12, 19, 16, 18, 20, 20), fill=OCHRE_DARK, width=3)
    draw.line((14, 19, 16, 19, 19, 20), fill=OCHRE_LIGHT, width=1)
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
        TEXTURES / "entity/equipment/humanoid/titan_armor.png": make_armor(HUMANOID_MASK, 137),
        TEXTURES / "entity/equipment/humanoid_leggings/titan_armor.png": make_armor(LEGGINGS_MASK, 149),
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
