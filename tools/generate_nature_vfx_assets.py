#!/usr/bin/env python3
"""Generate the Fairy Bloom Nature VFX texture package.

Every production image is drawn directly on its final pixel grid. The artwork uses clustered
shading, broken material edges, and small value shifts inspired by modern Minecraft textures;
there is no random state, source image, resizing, blur, or antialiasing. Repeated runs therefore
produce the same hard-edged RGBA pixels.
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
BARK_INK = (31, 30, 24, 255)
BARK_DARK = (57, 45, 33, 255)
BARK = (91, 64, 42, 255)
BARK_LIGHT = (132, 91, 52, 255)
FOREST_INK = (20, 54, 36, 255)
FOREST = (36, 104, 56, 255)
EMERALD = (53, 145, 69, 255)
LEAF = (83, 171, 77, 255)
LEAF_LIGHT = (135, 205, 100, 255)
MINT = (184, 231, 145, 255)
IVORY_SHADOW = (211, 205, 151, 255)
IVORY = (248, 241, 190, 255)
CREAM = (255, 250, 221, 255)
POLLEN_DARK = (174, 125, 34, 255)
POLLEN = (226, 177, 58, 255)
POLLEN_LIGHT = (251, 218, 103, 255)
SUN = (255, 242, 164, 255)
PETAL_SHADOW = (211, 167, 135, 245)
PETAL = (244, 215, 173, 250)
PETAL_LIGHT = (255, 242, 207, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def alpha(color: tuple[int, int, int, int], value: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], value


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 41 + y * 67 + seed * 109 + x * y * 11) & 255


def bezier(points, samples: int = 40) -> list[tuple[int, int]]:
    p0, p1, p2, p3 = points
    result: list[tuple[int, int]] = []
    for index in range(samples + 1):
        t = index / samples
        u = 1.0 - t
        point = (
            round(u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]),
            round(u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]),
        )
        if not result or point != result[-1]:
            result.append(point)
    return result


def oriented_leaf(draw: ImageDraw.ImageDraw, cx: float, cy: float, angle: float,
                  length: float, width: float, highlight: bool = True,
                  opacity: int = 255) -> None:
    ux, uy = math.cos(angle), math.sin(angle)
    vx, vy = -uy, ux
    tip = (cx + ux * length * 0.55, cy + uy * length * 0.55)
    base = (cx - ux * length * 0.45, cy - uy * length * 0.45)
    left = (cx + vx * width, cy + vy * width)
    right = (cx - vx * width, cy - vy * width)
    poly(draw, [base, left, tip, right], alpha(FOREST_INK, opacity))
    poly(draw, [
        (base[0] + ux, base[1] + uy),
        (cx + vx * max(1.0, width - 1), cy + vy * max(1.0, width - 1)),
        (tip[0] - ux, tip[1] - uy),
        (cx - vx * max(1.0, width - 1), cy - vy * max(1.0, width - 1)),
    ], alpha(LEAF, opacity))
    draw.line((round(base[0]), round(base[1]), round(tip[0]), round(tip[1])),
              fill=alpha(FOREST, opacity), width=1)
    if highlight:
        hx = round(cx + vx * 0.8 + ux * length * 0.12)
        hy = round(cy + vy * 0.8 + uy * length * 0.12)
        draw.point((hx, hy), fill=alpha(MINT, opacity))


def oriented_petal(draw: ImageDraw.ImageDraw, cx: float, cy: float, angle: float,
                   length: float, width: float, opacity: int = 255) -> None:
    ux, uy = math.cos(angle), math.sin(angle)
    vx, vy = -uy, ux
    tip = (cx + ux * length * 0.62, cy + uy * length * 0.62)
    base = (cx - ux * length * 0.38, cy - uy * length * 0.38)
    shoulder = length * 0.12
    poly(draw, [
        base,
        (cx - ux * shoulder + vx * width, cy - uy * shoulder + vy * width),
        tip,
        (cx - ux * shoulder - vx * width, cy - uy * shoulder - vy * width),
    ], alpha(PETAL_SHADOW, opacity))
    poly(draw, [
        (base[0] + ux, base[1] + uy),
        (cx + vx * max(0.7, width - 1), cy + vy * max(0.7, width - 1)),
        (tip[0] - ux, tip[1] - uy),
        (cx - vx * max(0.7, width - 1), cy - vy * max(0.7, width - 1)),
    ], alpha(PETAL, opacity))
    draw.line((round(base[0]), round(base[1]), round(tip[0]), round(tip[1])),
              fill=alpha(PETAL_LIGHT, opacity), width=1)


def make_pollen(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    grains = (
        ((5, 11, 2), (10, 6, 1), (7, 3, 1), (13, 12, 1)),
        ((7, 9, 2), (11, 4, 1), (3, 6, 1), (12, 12, 1)),
        ((9, 7, 2), (5, 4, 1), (3, 11, 1), (13, 9, 1)),
        ((8, 5, 2), (4, 8, 1), (7, 13, 1), (12, 10, 1)),
    )[frame]
    for index, (x, y, size) in enumerate(grains):
        halo = alpha(POLLEN, 75 + index * 18)
        draw.rectangle((x - 1, y - 1, x + size, y + size), fill=halo)
        draw.rectangle((x, y, x + size - 1, y + size - 1),
                       fill=POLLEN_LIGHT if index % 2 == 0 else POLLEN)
        if size > 1:
            draw.point((x, y), fill=CREAM)
            draw.point((x + 1, y + 1), fill=POLLEN_DARK)
    draw.point((2 + frame * 3, 13 - frame * 2), fill=alpha(SUN, 145))
    return image


def make_petal(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    angle = -1.2 + frame * 0.78
    cx = 8 + round(math.sin(frame * 1.4) * 2)
    cy = 7 + (frame % 3)
    oriented_petal(draw, cx, cy, angle, 8, 2.6, 245 - frame * 10)
    draw.point((3 + frame * 2, 13 - frame), fill=alpha(POLLEN_LIGHT, 120))
    if frame in (1, 4):
        draw.point((12, 3 + frame), fill=alpha(PETAL_LIGHT, 150))
    return image


def make_leaf(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    angle = -2.35 + frame * 0.92
    cx = 8 + ((frame + 1) % 3) - 1
    cy = 8 + (frame % 2)
    oriented_leaf(draw, cx, cy, angle, 9, 3.1, True, 250 - frame * 8)
    # Small torn edge and secondary material cluster keep the leaf from reading as a flat icon.
    draw.point((cx + (2 if frame % 2 else -3), cy + 2), fill=alpha(FOREST, 220))
    draw.point((12 - frame * 2, 3 + frame), fill=alpha(MINT, 115 + frame * 15))
    return image


def make_vine(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    sway = (frame - 2.5) * 0.55
    full_path = bezier(((4, 27), (7 + sway, 12), (23 - sway, 25), (27, 6)), 54)
    reveal = min(len(full_path), max(10, round(len(full_path) * (0.35 + frame * 0.13))))
    path = full_path[:reveal]
    draw.line(path, fill=FOREST_INK, width=5, joint="curve")
    draw.line(path, fill=FOREST, width=3, joint="curve")
    draw.line(path[3:-2], fill=LEAF_LIGHT, width=1)

    for index in range(5):
        p_index = min(len(path) - 1, 5 + index * max(2, len(path) // 6))
        x, y = path[p_index]
        side = -1 if (index + frame) % 2 else 1
        if index < 3 + frame // 2:
            oriented_leaf(draw, x + side * 2.5, y, -0.8 + side * 1.55,
                          5 + index % 2, 1.8, index % 2 == 0, 240)
        thorn_x = x - side * 2
        draw.line((x, y, thorn_x, y - 2), fill=alpha(POLLEN_DARK, 220), width=1)

    ex, ey = path[-1]
    if frame >= 3:
        oriented_petal(draw, ex, ey, -1.2, 5 + frame // 2, 1.7, 245)
        draw.point((ex, ey), fill=SUN)
    return image


def make_bloom(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    cx, cy = 15.5, 16.5
    opening = min(1.0, frame / 5.0)
    fade = 255 if frame < 6 else 220 - (frame - 6) * 55

    # Sepals and low leaves establish a botanical silhouette before ivory petals open.
    for index in range(4):
        angle = index * math.pi / 2 + math.pi / 4
        oriented_leaf(draw,
                      cx + math.cos(angle) * (2 + opening * 2),
                      cy + math.sin(angle) * (2 + opening * 2),
                      angle, 7 + opening * 3, 2.5, index % 2 == 0, fade)

    petal_count = max(1, min(8, 1 + frame * 2))
    for index in range(petal_count):
        angle = index * math.tau / 8.0 - math.pi / 2
        distance = 1.0 + opening * 5.2
        length = 5.0 + opening * 5.0
        width = 1.6 + opening * 1.45
        oriented_petal(draw,
                       cx + math.cos(angle) * distance,
                       cy + math.sin(angle) * distance,
                       angle, length, width, fade)

    core_radius = 1 if frame < 2 else 2
    draw.rectangle((round(cx) - core_radius, round(cy) - core_radius,
                    round(cx) + core_radius, round(cy) + core_radius),
                   fill=alpha(POLLEN_DARK, fade))
    draw.rectangle((round(cx) - 1, round(cy) - 1, round(cx) + 1, round(cy) + 1),
                   fill=alpha(POLLEN_LIGHT, fade))
    draw.point((round(cx), round(cy)), fill=alpha(CREAM, fade))
    for offset in (-1, 1):
        draw.point((round(cx + offset * (8 + opening * 3)), round(cy - 7 + frame % 3)),
                   fill=alpha(SUN, max(80, fade - 55)))
    return image


def make_heart(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx, cy = 31.5, 32.0
    pulse = (0, 1, 2, 1, 0, -1, -2, -1)[frame]
    fade = 255 if frame < 6 else 230 - (frame - 6) * 35

    # A broken, rotating petal corona prevents the core from reading as a cartoon heart icon.
    for ray in range(10):
        angle = ray * math.tau / 10.0 + frame * 0.075
        inner = 18 + pulse * 0.4
        outer = 24 + (ray % 3) * 2 + pulse
        if (ray + frame) % 7 == 0:
            continue
        p1 = (cx + math.cos(angle - 0.09) * inner, cy + math.sin(angle - 0.09) * inner)
        p2 = (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer)
        p3 = (cx + math.cos(angle + 0.09) * inner, cy + math.sin(angle + 0.09) * inner)
        poly(draw, [p1, p2, p3], alpha(FOREST_INK, fade))
        mid = (cx + math.cos(angle) * (outer - 3), cy + math.sin(angle) * (outer - 3))
        poly(draw, [
            (cx + math.cos(angle - 0.05) * (inner + 1), cy + math.sin(angle - 0.05) * (inner + 1)),
            mid,
            (cx + math.cos(angle + 0.05) * (inner + 1), cy + math.sin(angle + 0.05) * (inner + 1)),
        ], alpha(LEAF_LIGHT if ray % 2 else MINT, fade))

    pod = [
        (31, 11 - pulse), (41, 17 - pulse), (46 + pulse, 29),
        (41, 43 + pulse), (32, 53 + pulse), (22, 43 + pulse),
        (17 - pulse, 29), (22, 17 - pulse),
    ]
    poly(draw, pod, alpha(BARK_INK, fade))
    poly(draw, [(31, 14 - pulse), (39, 19), (43, 29), (38, 41),
                (32, 49 + pulse), (25, 41), (21, 29), (25, 19)], alpha(POLLEN_DARK, fade))
    poly(draw, [(31, 17), (37, 21), (40, 29), (36, 39),
                (32, 45), (27, 38), (24, 29), (27, 21)], alpha(POLLEN, fade))
    poly(draw, [(31, 20), (35, 23), (37, 29), (34, 36),
                (31, 40), (29, 35), (27, 29), (29, 23)], alpha(POLLEN_LIGHT, fade))
    draw.rectangle((30, 24, 33, 34), fill=alpha(SUN, fade))
    draw.rectangle((31, 26, 32, 31), fill=alpha(CREAM, fade))

    # Bark-like seams and seed facets supply modern material variation.
    draw.line(((22, 29), (27, 31), (30, 40)), fill=alpha(BARK_LIGHT, fade), width=2)
    draw.line(((41, 24), (36, 28), (34, 37)), fill=alpha(IVORY_SHADOW, fade), width=1)
    draw.line(((26, 19), (31, 23), (36, 19)), fill=alpha(IVORY, fade), width=1)
    for index in range(6):
        angle = frame * 0.23 + index * math.tau / 6
        x = round(cx + math.cos(angle) * (28 + index % 2))
        y = round(cy + math.sin(angle) * (25 + index % 3))
        draw.point((x, y), fill=alpha(SUN, max(90, fade - 50)))
    return image


def make_winged_seed() -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)

    # Translucent leaf-wings are outlined and internally segmented like Minecraft foliage.
    left = [(29, 29), (22, 15), (7, 8), (9, 22), (18, 33), (29, 36)]
    right = [(35, 29), (43, 14), (57, 10), (55, 24), (46, 34), (35, 36)]
    poly(draw, left, FOREST_INK)
    poly(draw, right, FOREST_INK)
    poly(draw, [(28, 30), (21, 18), (10, 11), (12, 21), (20, 30), (28, 34)], alpha(LEAF, 230))
    poly(draw, [(36, 30), (43, 17), (54, 13), (52, 23), (45, 31), (36, 34)], alpha(LEAF_LIGHT, 230))
    draw.line(((12, 12), (27, 32)), fill=MINT, width=2)
    draw.line(((53, 14), (37, 32)), fill=MINT, width=2)
    draw.line(((16, 18), (10, 20)), fill=FOREST, width=1)
    draw.line(((20, 24), (13, 27)), fill=FOREST, width=1)
    draw.line(((48, 19), (55, 18)), fill=FOREST, width=1)
    draw.line(((44, 25), (51, 28)), fill=FOREST, width=1)

    pod = [(32, 18), (39, 24), (40, 38), (35, 48), (31, 53),
           (25, 46), (22, 36), (24, 25)]
    poly(draw, pod, BARK_INK)
    poly(draw, [(32, 21), (36, 26), (37, 37), (33, 47),
                (29, 48), (25, 37), (27, 26)], BARK)
    poly(draw, [(31, 23), (34, 27), (34, 38), (31, 44), (28, 37), (29, 27)], POLLEN_DARK)
    draw.rectangle((30, 27, 33, 38), fill=POLLEN)
    draw.rectangle((31, 29, 32, 35), fill=SUN)
    draw.point((31, 30), fill=CREAM)
    draw.line(((26, 35), (31, 39), (35, 35)), fill=BARK_LIGHT, width=1)
    draw.line(((28, 26), (32, 24), (35, 27)), fill=IVORY_SHADOW, width=1)

    for x, y, size in ((17, 42, 2), (10, 48, 1), (22, 53, 1), (5, 54, 1)):
        draw.rectangle((x, y, x + size - 1, y + size - 1), fill=alpha(POLLEN_LIGHT, 215 - x * 3))
        if size > 1:
            draw.point((x, y), fill=CREAM)
    return image


def icon_canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = canvas(32)
    return image, ImageDraw.Draw(image)


def make_primary_icon() -> Image.Image:
    image, draw = icon_canvas()
    left = [(14, 14), (9, 6), (3, 5), (5, 13), (12, 18)]
    right = [(18, 14), (23, 6), (29, 5), (27, 13), (20, 18)]
    poly(draw, left, FOREST_INK); poly(draw, right, FOREST_INK)
    poly(draw, [(13, 14), (9, 8), (5, 7), (7, 12), (12, 16)], LEAF)
    poly(draw, [(19, 14), (23, 8), (27, 7), (25, 12), (20, 16)], LEAF_LIGHT)
    draw.line((6, 7, 13, 15), fill=MINT, width=1)
    draw.line((26, 7, 19, 15), fill=MINT, width=1)
    poly(draw, [(16, 10), (20, 15), (19, 23), (16, 28), (12, 23), (12, 15)], BARK_INK)
    poly(draw, [(16, 13), (18, 16), (17, 22), (16, 25), (14, 22), (14, 16)], POLLEN)
    draw.rectangle((15, 16, 16, 21), fill=SUN)
    for x, y in ((4, 23), (8, 27), (24, 24), (28, 20)):
        draw.point((x, y), fill=POLLEN_LIGHT)
    return image


def make_secondary_icon() -> Image.Image:
    image, draw = icon_canvas()
    left = bezier(((3, 27), (4, 8), (13, 26), (16, 14)), 30)
    right = bezier(((29, 27), (28, 8), (19, 26), (16, 14)), 30)
    for path in (left, right):
        draw.line(path, fill=FOREST_INK, width=4)
        draw.line(path[2:-2], fill=FOREST, width=2)
        draw.line(path[6:-6], fill=LEAF_LIGHT, width=1)
    for cx, cy, angle in ((7, 15, -2.2), (10, 23, -0.8), (25, 15, -0.9), (22, 23, -2.3)):
        oriented_leaf(draw, cx, cy, angle, 5, 1.5, True)
    for index in range(6):
        angle = index * math.tau / 6
        oriented_petal(draw, 16 + math.cos(angle) * 4, 12 + math.sin(angle) * 4,
                       angle, 6, 1.8)
    draw.rectangle((14, 10, 17, 13), fill=POLLEN_DARK)
    draw.rectangle((15, 11, 16, 12), fill=SUN)
    draw.point((3, 29), fill=BARK_LIGHT); draw.point((28, 29), fill=BARK_LIGHT)
    return image


def make_ultimate_icon() -> Image.Image:
    image, draw = icon_canvas()
    # Root spokes and a layered canopy remain readable when the 32px glyph is drawn at 28px.
    for x2, y2 in ((2, 28), (7, 27), (11, 30), (21, 30), (25, 27), (30, 28)):
        draw.line((16, 25, x2, y2), fill=FOREST_INK, width=2)
        draw.point((x2, y2), fill=LEAF_LIGHT)
    draw.rectangle((13, 12, 18, 26), fill=BARK_INK)
    draw.rectangle((14, 13, 17, 25), fill=BARK)
    draw.line((15, 14, 15, 24), fill=BARK_LIGHT, width=1)
    canopy = [(4, 14), (6, 7), (11, 5), (15, 2), (20, 4), (27, 7),
              (29, 14), (25, 19), (18, 18), (11, 20), (6, 18)]
    poly(draw, canopy, FOREST_INK)
    poly(draw, [(6, 13), (8, 8), (13, 7), (16, 4), (20, 6), (25, 8),
                (27, 13), (23, 17), (17, 16), (11, 18), (7, 16)], FOREST)
    # Irregular two- and three-pixel clusters imitate the value breakup in modern leaf textures.
    dark_clusters = ((7, 11, 3, 2), (12, 6, 2, 2), (19, 6, 3, 2),
                     (23, 10, 3, 3), (8, 15, 3, 2), (17, 14, 3, 2))
    for x, y, width, height in dark_clusters:
        draw.rectangle((x, y, x + width - 1, y + height - 1), fill=FOREST_INK)
        draw.point((x + width - 1, y), fill=EMERALD)
    light_clusters = ((9, 9), (14, 7), (20, 9), (24, 14), (11, 15), (18, 12), (14, 15))
    for index, (x, y) in enumerate(light_clusters):
        draw.rectangle((x, y, x + 2, y + (1 if index % 2 else 2)),
                       fill=LEAF if index % 2 else LEAF_LIGHT)
        draw.point((x + 1, y), fill=MINT)
        if index % 3 == 0:
            draw.point((x + 2, y + 1), fill=IVORY)
            draw.point((x + 1, y + 2), fill=POLLEN_LIGHT)
    # Small negative-space notches keep the canopy from becoming a single round blob.
    draw.point((5, 13), fill=T); draw.point((27, 11), fill=T)
    draw.point((8, 7), fill=T); draw.point((23, 17), fill=T)
    draw.rectangle((14, 16, 17, 20), fill=POLLEN_DARK)
    draw.rectangle((15, 16, 16, 19), fill=SUN)
    draw.point((15, 17), fill=CREAM)
    draw.line((13, 14, 15, 17), fill=BARK_LIGHT, width=1)
    draw.line((18, 14, 16, 17), fill=IVORY_SHADOW, width=1)
    draw.line((14, 21, 17, 24), fill=BARK_LIGHT, width=1)
    for x, y in ((7, 11), (13, 5), (21, 8), (24, 13), (10, 16)):
        draw.point((x, y), fill=PETAL_LIGHT)
        draw.point((x + 1, y), fill=POLLEN_LIGHT)
    # Broken sunlit seams travel through different roots rather than outlining them uniformly.
    draw.line((15, 25, 8, 28), fill=BARK_LIGHT, width=1)
    draw.line((17, 25, 24, 28), fill=LEAF_LIGHT, width=1)
    draw.point((3, 28), fill=POLLEN_LIGHT); draw.point((29, 28), fill=POLLEN_LIGHT)
    return image


def make_bud(kind: str) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    if kind == "empty":
        stem, dark, light, core = alpha(BARK, 120), alpha(FOREST_INK, 125), alpha(FOREST, 130), T
    else:
        stem, dark, light, core = BARK, FOREST_INK, LEAF_LIGHT, POLLEN_LIGHT
    draw.line((8, 15, 8, 7), fill=alpha(BARK_INK, stem[3]), width=3)
    draw.line((8, 15, 8, 7), fill=stem, width=1)
    poly(draw, [(7, 11), (2, 7), (1, 11), (4, 14), (7, 13)], dark)
    poly(draw, [(9, 11), (14, 6), (15, 10), (12, 14), (9, 13)], light)
    draw.line((3, 9, 7, 12), fill=alpha(LEAF_LIGHT, light[3]), width=1)
    draw.line((14, 8, 9, 12), fill=alpha(MINT, light[3]), width=1)
    draw.point((3, 10), fill=alpha(FOREST, dark[3]))
    draw.point((12, 10), fill=alpha(FOREST, light[3]))
    if kind == "bloom":
        for index in range(5):
            angle = index * math.tau / 5.0 - math.pi / 2
            oriented_petal(draw, 8 + math.cos(angle) * 3.0,
                           5 + math.sin(angle) * 2.5,
                           angle, 6, 2.0)
        # Shadow clusters at petal bases, a faceted pollen core, and offset highlights survive
        # the 16-to-12 HUD downscale while avoiding a flat four-square flower.
        for x, y in ((6, 5), (8, 3), (10, 5), (7, 7), (9, 7)):
            draw.point((x, y), fill=PETAL_SHADOW)
        draw.rectangle((6, 4, 10, 7), fill=POLLEN_DARK)
        draw.rectangle((7, 4, 9, 6), fill=POLLEN_LIGHT)
        draw.point((8, 5), fill=CREAM)
        draw.point((4, 3), fill=PETAL_LIGHT)
        draw.point((12, 4), fill=PETAL_LIGHT)
    else:
        poly(draw, [(8, 1), (12, 4), (11, 8), (8, 10), (5, 8), (4, 4)], dark)
        poly(draw, [(8, 3), (10, 5), (9, 8), (7, 8), (6, 5)],
             alpha(FOREST if kind == "empty" else LEAF, dark[3]))
        draw.line((6, 5, 9, 8), fill=alpha(LEAF_LIGHT, dark[3]), width=1)
        if kind == "filled":
            draw.rectangle((7, 4, 9, 6), fill=core)
            draw.point((8, 4), fill=CREAM)
            draw.point((10, 5), fill=MINT)
    return image


def make_entangle_vignette() -> Image.Image:
    image = canvas((320, 180))
    draw = ImageDraw.Draw(image)

    paths = [
        bezier(((-4, 150), (38, 129), (18, 62), (75, -5)), 120),
        bezier(((324, 145), (280, 126), (305, 57), (246, -5)), 120),
        bezier(((18, 184), (62, 155), (108, 184), (151, 166)), 90),
        bezier(((302, 184), (257, 155), (213, 184), (169, 166)), 90),
    ]
    for path_index, path in enumerate(paths):
        draw.line(path, fill=alpha(FOREST_INK, 205), width=7, joint="curve")
        draw.line(path[2:-2], fill=alpha(FOREST, 188), width=4, joint="curve")
        draw.line(path[6:-6], fill=alpha(LEAF_LIGHT, 155), width=1)
        for index in range(9):
            point_index = min(len(path) - 2, 8 + index * max(4, len(path) // 11))
            x, y = path[point_index]
            if 10 < x < 310 and 4 < y < 176:
                side = -1 if (index + path_index) % 2 else 1
                oriented_leaf(draw, x + side * 5, y, -0.65 + side * 1.45,
                              10 + index % 3, 3.2, index % 2 == 0, 175)
                if index in (2, 6):
                    for petal_index in range(4):
                        angle = petal_index * math.pi / 2
                        oriented_petal(draw, x + math.cos(angle) * 3,
                                       y + math.sin(angle) * 3, angle, 6, 1.8, 190)
                    draw.point((x, y), fill=alpha(SUN, 220))

    # Fine thorn branches provide detail at modern display scales without covering the center.
    for side in (-1, 1):
        x = 18 if side < 0 else 301
        for y in range(22, 162, 19):
            draw.line((x, y, x + side * -10, y - 8), fill=alpha(BARK_LIGHT, 150), width=2)
            draw.point((x + side * -11, y - 9), fill=alpha(POLLEN_LIGHT, 175))
    return image


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for frame in range(4):
        generated[TEXTURES / f"particle/nature/pollen_{frame}.png"] = make_pollen(frame)
        generated[TEXTURES / f"particle/nature/leaf_{frame}.png"] = make_leaf(frame)
    for frame in range(6):
        generated[TEXTURES / f"particle/nature/petal_{frame}.png"] = make_petal(frame)
        generated[TEXTURES / f"particle/nature/vine_{frame}.png"] = make_vine(frame)
    for frame in range(8):
        generated[TEXTURES / f"particle/nature/bloom_{frame}.png"] = make_bloom(frame)
        generated[TEXTURES / f"particle/nature/heart_{frame}.png"] = make_heart(frame)
    generated.update({
        TEXTURES / "entity/winged_seed.png": make_winged_seed(),
        TEXTURES / "gui/ability/nature_primary.png": make_primary_icon(),
        TEXTURES / "gui/ability/nature_secondary.png": make_secondary_icon(),
        TEXTURES / "gui/ability/nature_ultimate.png": make_ultimate_icon(),
        TEXTURES / "gui/entangle_bud_empty.png": make_bud("empty"),
        TEXTURES / "gui/entangle_bud_filled.png": make_bud("filled"),
        TEXTURES / "gui/entangle_bud_bloom.png": make_bud("bloom"),
        TEXTURES / "gui/sprites/hud/entangle_vignette_v2.png": make_entangle_vignette(),
    })
    return generated


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: pixels differ from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        alpha_min, alpha_max = reopened.getchannel("A").getextrema()
        assert alpha_min == 0 and alpha_max >= 100, f"{path}: expected transparency and visible pixels"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        visible_colors = {rgba for _, rgba in colors if rgba[3] > 0}
        assert len(visible_colors) >= 3, f"{path}: insufficient material variation"
        if "ability" in path.parts or path.name == "winged_seed.png":
            assert len(visible_colors) >= 8, f"{path}: icon/entity texture is too flat"
        bbox = reopened.getbbox()
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
    return len(colors), bbox, digest


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    families = (("pollen", 4), ("petal", 6), ("leaf", 4), ("vine", 6),
                ("bloom", 8), ("heart", 8))
    for family, count in families:
        frames = [outputs[TEXTURES / f"particle/nature/{family}_{frame}.png"].tobytes()
                  for frame in range(count)]
        assert len(set(frames)) == count, f"{family}: duplicate animation frames"


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb = 112
    label_h = 28
    columns = 6
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (28, 39, 29, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        col = index % columns
        row = index // columns
        x = col * thumb
        y = row * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (224, 224, 206, 255))
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 14):
            for cx in range(0, thumb, 14):
                if (cx // 14 + cy // 14) % 2:
                    checker_draw.rectangle((cx, cy, cx + 13, cy + 13),
                                           fill=(172, 182, 163, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2,
                                          (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), str(relative.parent)[-17:],
                  fill=(171, 207, 144, 255), font=font)
        draw.text((x + 3, y + thumb + 14), relative.stem[:19],
                  fill=(255, 244, 204, 255), font=font)
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
                    raise SystemExit(f"Refusing to overwrite differing Nature texture: {path.relative_to(ROOT)}")
                image.save(path, format="PNG", optimize=False, compress_level=9)
        else:
            image.save(path, format="PNG", optimize=False, compress_level=9)

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_nature_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
