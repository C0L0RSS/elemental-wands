#!/usr/bin/env python3
"""Generate the deterministic Kinetic Inferno Fire VFX package.

Every production sprite is authored directly on its final pixel grid. The
generator never rescales, antialiases, blurs, or interpolates artwork. Flame
depth comes from irregular hue-shifted clusters rather than black outlines or
flat vector bands.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections.abc import Callable
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/elementalwands"
TEXTURES = ASSETS / "textures"
PARTICLES = ASSETS / "particles"
CONTACT_SHEET = Path("/tmp/elementalwands_kinetic_inferno_contact_sheet.png")

T = (0, 0, 0, 0)
EMBER_BROWN = (83, 30, 17, 255)
OXBLOOD = (116, 24, 16, 255)
CRIMSON = (166, 35, 14, 255)
VERMILION = (218, 55, 10, 255)
DEEP_ORANGE = (238, 77, 7, 255)
ORANGE = (255, 107, 7, 255)
AMBER = (255, 145, 10, 255)
GOLD = (255, 181, 20, 255)
YELLOW = (255, 218, 63, 255)
PALE_GOLD = (255, 238, 132, 255)
IVORY = (255, 250, 202, 255)
WHITE_HOT = (255, 255, 235, 255)
SMOKE_WARM = (100, 76, 68, 205)
SMOKE_LIGHT = (145, 119, 102, 175)

OUTER = (EMBER_BROWN, OXBLOOD, CRIMSON, VERMILION)
BODY = (CRIMSON, VERMILION, DEEP_ORANGE, ORANGE, AMBER)
HOT = (ORANGE, AMBER, GOLD, YELLOW, PALE_GOLD)
CORE = (GOLD, YELLOW, PALE_GOLD, IVORY, WHITE_HOT)

HUD_SHADOW = (74, 20, 5, 255)
HUD_DEEP_ORANGE = (166, 45, 6, 255)
HUD_ORANGE = (242, 92, 8, 255)
HUD_GOLD = (255, 171, 18, 255)
HUD_YELLOW = (255, 225, 74, 255)
HUD_PALE = (255, 250, 188, 255)
HUD_WHITE = (255, 255, 232, 255)
HUD_NETHERRACK_DARK = (73, 31, 31, 255)
HUD_NETHERRACK = (111, 49, 49, 255)
HUD_NETHERRACK_LIGHT = (145, 61, 52, 255)
HUD_MAGMA_DARK = (51, 25, 19, 255)
HUD_MAGMA = (105, 35, 15, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 61 + y * 89 + seed * 131 + x * y * 17 + x * x * 5 + y * y * 3) & 255


def material_fill(image: Image.Image, mask: Image.Image, seed: int,
                  palette: tuple[tuple[int, int, int, int], ...], cluster: int = 2) -> None:
    pixels = image.load()
    mask_pixels = mask.load()
    for y in range(image.height):
        for x in range(image.width):
            if not mask_pixels[x, y]:
                continue
            value = pixel_hash(x // cluster, y // cluster, seed)
            pixels[x, y] = palette[min(len(palette) - 1, value * len(palette) // 256)]


def draw_cluster(image: Image.Image, points, seed: int,
                 palette: tuple[tuple[int, int, int, int], ...], cluster: int = 2) -> None:
    mask = Image.new("1", image.size, 0)
    poly(ImageDraw.Draw(mask), points, 1)
    material_fill(image, mask, seed, palette, cluster)


def clear_cluster(image: Image.Image, points) -> None:
    poly(ImageDraw.Draw(image), points, T)


def draw_spark(draw: ImageDraw.ImageDraw, x: int, y: int, seed: int, scale: int = 1) -> None:
    colors = (VERMILION, ORANGE, GOLD, PALE_GOLD)
    edge = colors[seed % len(colors)]
    draw.rectangle((x, y - 2 * scale, x + scale - 1, y + 2 * scale), fill=edge)
    draw.rectangle((x - 2 * scale, y, x + 2 * scale, y + scale - 1), fill=edge)
    draw.rectangle((x, y - scale, x + scale - 1, y + scale - 1), fill=PALE_GOLD)
    draw.point((x, y), fill=WHITE_HOT)


def draw_vertical_flame(image: Image.Image, x: int, base: int, height: int,
                        width: int, phase: int, seed: int) -> None:
    half = max(2, width // 2)
    sway = (-2, -1, 1, 2, 0)[phase % 5]
    outer = [
        (x - half, base), (x - half - 1, base - height // 4),
        (x - half + 2, base - height // 2), (x - 2 + sway, base - height),
        (x + 1 + sway, base - height + max(3, height // 5)),
        (x + half - 1, base - height // 2), (x + half + 1, base - height // 4),
        (x + half, base),
    ]
    draw_cluster(image, outer, seed, OUTER, 2)
    inner = [
        (x - half + 2, base), (x - half + 3, base - height // 3),
        (x - 1 + sway, base - height + max(4, height // 4)),
        (x + 2 + sway, base - height + max(6, height // 3)),
        (x + half - 2, base - height // 3), (x + half - 2, base),
    ]
    draw_cluster(image, inner, seed + 19, HOT, 2)
    if height >= 12:
        core = [
            (x - 1, base), (x - 2, base - height // 4),
            (x + sway // 2, base - height * 3 // 5),
            (x + 2, base - height // 3), (x + 2, base),
        ]
        draw_cluster(image, core, seed + 41, CORE, 1)
    d = ImageDraw.Draw(image)
    d.point((x - half + 1, base - 1), fill=EMBER_BROWN)
    d.point((x + half - 1, base - 2), fill=CRIMSON)


def draw_hud_flame(draw: ImageDraw.ImageDraw, x: int, base: int,
                   height: int, width: int, phase: int) -> None:
    half = width // 2
    sway = (-1, 0, 1)[phase % 3]
    poly(draw, [
        (x - half, base), (x - half, base - max(3, height // 3)),
        (x - max(1, half - 2), base - height // 2), (x - 1 + sway, base - height),
        (x + 2 + sway, base - height + max(3, height // 4)),
        (x + half, base - height // 2), (x + half, base),
    ], HUD_SHADOW)
    poly(draw, [
        (x - half + 1, base), (x - max(1, half - 2), base - height // 2),
        (x + sway, base - height + 2), (x + max(1, half - 2), base - height // 2 + 1),
        (x + half - 1, base),
    ], HUD_DEEP_ORANGE)
    poly(draw, [
        (x - max(1, half - 3), base), (x - 1, base - height // 2),
        (x + sway, base - height + 5), (x + 2, base - height // 3),
        (x + max(1, half - 3), base),
    ], HUD_ORANGE)
    poly(draw, [
        (x - 1, base), (x, base - max(4, height // 2)),
        (x + 2, base - max(2, height // 3)), (x + 2, base),
    ], HUD_GOLD)
    if height >= 11:
        draw.rectangle((x, base - 4, x + 1, base - 2), fill=HUD_YELLOW)
        draw.point((x, base - 3), fill=HUD_WHITE)


def make_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    draw_hud_flame(draw, 7, 27, 14, 9, 0)
    draw_hud_flame(draw, 16, 27, 23, 12, 1)
    draw_hud_flame(draw, 25, 27, 16, 9, 2)
    draw.rectangle((3, 27, 28, 29), fill=HUD_DEEP_ORANGE)
    draw.rectangle((6, 27, 26, 27), fill=HUD_GOLD)
    for x, y in ((3, 18), (10, 7), (23, 8), (29, 20)):
        draw.point((x, y), fill=HUD_YELLOW)
    return image


def make_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for y in range(23, 30, 3):
        for x in range(2, 30, 4):
            draw.rectangle((x, y, min(30, x + 3), min(30, y + 2)),
                           fill=(HUD_NETHERRACK_DARK, HUD_NETHERRACK,
                                 HUD_NETHERRACK_LIGHT)[(x * 5 + y * 3) % 3])
    draw_hud_flame(draw, 16, 24, 20, 10, 1)
    draw_hud_flame(draw, 10, 25, 13, 8, 0)
    draw_hud_flame(draw, 22, 25, 13, 8, 2)
    draw_hud_flame(draw, 5, 26, 8, 6, 1)
    draw_hud_flame(draw, 27, 26, 8, 6, 0)
    draw.line((2, 24, 8, 23), fill=HUD_ORANGE, width=1)
    draw.line((24, 23, 30, 24), fill=HUD_ORANGE, width=1)
    return image


def make_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    poly(draw, [(3, 1), (11, 7), (14, 12), (20, 15), (16, 21), (8, 16)], HUD_SHADOW)
    poly(draw, [(5, 2), (12, 8), (14, 13), (18, 15), (15, 18), (10, 14)], HUD_ORANGE)
    poly(draw, [(7, 3), (12, 9), (13, 13), (16, 15), (14, 16), (11, 12)], HUD_YELLOW)
    draw.line((8, 3, 14, 14), fill=HUD_PALE, width=2)
    draw.point((9, 4), fill=HUD_WHITE)
    poly(draw, [(11, 14), (16, 10), (24, 12), (29, 18), (27, 26),
                (21, 30), (13, 27), (8, 21)], HUD_MAGMA_DARK)
    poly(draw, [(13, 15), (17, 12), (23, 14), (27, 18), (25, 23),
                (21, 27), (15, 25), (11, 21)], HUD_MAGMA)
    draw.line((12, 19, 17, 18, 19, 24, 24, 25), fill=HUD_ORANGE, width=3)
    draw.line((13, 19, 17, 18, 19, 23, 24, 24), fill=HUD_YELLOW, width=1)
    draw.line((21, 13, 20, 18, 25, 20), fill=HUD_GOLD, width=2)
    draw.point((18, 19), fill=HUD_WHITE)
    return image


def stream_length(frame: int) -> int:
    return (14, 21, 31, 42, 49, 52, 52, 49, 43, 34)[frame]


def make_inferno_stream(frame: int) -> Image.Image:
    image = canvas((64, 32))
    head = 54
    length = stream_length(frame)
    start = head - length
    samples = 10
    top: list[tuple[float, float]] = []
    bottom: list[tuple[float, float]] = []
    for index in range(samples + 1):
        t = index / samples
        x = start + length * t
        center = 16 + math.sin(t * math.tau * 1.35 + frame * 0.58) * (1.0 + 1.2 * t)
        envelope = math.sin(math.pi * t) ** 0.58
        width = 1.5 + envelope * (4.5 + min(frame, 5) * 0.45) + t * 1.8
        if frame >= 8:
            width *= 1.0 - (frame - 7) * 0.09
        top.append((x, center - width))
        bottom.append((x, center + width))
    draw_cluster(image, top + list(reversed(bottom)), 100 + frame, OUTER + BODY, 2)

    inner_top: list[tuple[float, float]] = []
    inner_bottom: list[tuple[float, float]] = []
    for index in range(samples + 1):
        t = index / samples
        x = start + 2 + max(1, length - 4) * t
        center = 16 + math.sin(t * math.tau * 1.2 + frame * 0.51) * (0.5 + t)
        width = 0.9 + (math.sin(math.pi * t) ** 0.7) * (2.6 + min(frame, 6) * 0.25)
        inner_top.append((x, center - width))
        inner_bottom.append((x, center + width))
    draw_cluster(image, inner_top + list(reversed(inner_bottom)), 140 + frame, HOT, 2)

    core_end = min(head, start + max(8, int(length * 0.72)))
    core = [
        (start + 2, 16), (start + 7, 13), (core_end - 5, 13 + (frame & 1)),
        (core_end, 16), (core_end - 6, 19 - (frame & 1)), (start + 6, 19),
    ]
    draw_cluster(image, core, 180 + frame, CORE, 1)
    if frame >= 2:
        gap_x = start + length * (0.38 + (frame % 3) * 0.09)
        clear_cluster(image, [(gap_x - 2, 10 + frame % 4), (gap_x + 5, 12 + frame % 3),
                              (gap_x + 2, 14 + frame % 2), (gap_x - 3, 13)])
    draw = ImageDraw.Draw(image)
    for index in range(7):
        x = max(2, min(62, start + ((index * 11 + frame * 7) % max(12, length + 8)) - 4))
        y = 4 + ((index * 7 + frame * 5) % 24)
        if (index + frame) % 3 == 0:
            draw_spark(draw, x, y, frame + index)
        else:
            draw.rectangle((x, y, min(63, x + 1), min(31, y + 1)),
                           fill=(VERMILION, ORANGE, GOLD)[(index + frame) % 3])
    return image


def make_inferno_front(frame: int) -> Image.Image:
    image = canvas(64)
    radii = (9, 12, 16, 21, 25, 27, 28, 27, 24, 19)
    radius = radii[frame]
    cx, cy = 32, 33
    points: list[tuple[float, float]] = []
    vertices = 24
    for index in range(vertices):
        angle = index * math.tau / vertices
        jitter = ((pixel_hash(index, frame, 31) % 9) - 4) * 0.65
        tongue = 0
        if index in ((frame * 3 + 2) % vertices, (frame * 5 + 9) % vertices,
                     (frame * 7 + 15) % vertices):
            tongue = 5 + frame % 4
        r = radius + jitter + tongue
        points.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r * 0.88))
    draw_cluster(image, points, 220 + frame, OUTER + BODY, 2)
    inner = []
    for index in range(18):
        angle = index * math.tau / 18 + frame * 0.07
        r = radius * (0.62 + ((index * 3 + frame) % 4) * 0.035)
        inner.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r * 0.84))
    draw_cluster(image, inner, 250 + frame, HOT, 2)
    core_radius = max(4, int(radius * 0.30))
    core = [
        (cx - core_radius, cy + 1), (cx - core_radius // 2, cy - core_radius),
        (cx + 2, cy - core_radius - 2 + frame % 3),
        (cx + core_radius, cy - 1), (cx + core_radius // 2, cy + core_radius),
        (cx - 2, cy + core_radius + 1),
    ]
    draw_cluster(image, core, 280 + frame, CORE, 1)
    for gap in range(3):
        angle = gap * math.tau / 3 + frame * 0.23
        inner_r = radius * 0.68
        outer_r = radius + 3
        width = 0.06 + gap * 0.012
        clear_cluster(image, [
            (cx + math.cos(angle - width) * inner_r, cy + math.sin(angle - width) * inner_r * 0.88),
            (cx + math.cos(angle - width) * outer_r, cy + math.sin(angle - width) * outer_r * 0.88),
            (cx + math.cos(angle + width) * outer_r, cy + math.sin(angle + width) * outer_r * 0.88),
            (cx + math.cos(angle + width) * inner_r, cy + math.sin(angle + width) * inner_r * 0.88),
        ])
    draw = ImageDraw.Draw(image)
    for index in range(8):
        angle = index * math.tau / 8 + frame * 0.31
        r = radius + 5 + (index + frame) % 5
        x = round(cx + math.cos(angle) * r)
        y = round(cy + math.sin(angle) * r * 0.86)
        if 3 <= x < 61 and 3 <= y < 61:
            draw_spark(draw, x, y, index + frame)
    return image


def make_ember(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    cx = (5, 8, 10, 7)[frame]
    cy = (11, 8, 5, 7)[frame]
    poly(draw, [(cx - 3, cy), (cx - 1, cy - 3), (cx + 2, cy - 2),
                (cx + 3, cy + 1), (cx + 1, cy + 3), (cx - 2, cy + 2)], OXBLOOD)
    poly(draw, [(cx - 1, cy - 2), (cx + 2, cy - 1), (cx + 1, cy + 2),
                (cx - 2, cy + 1)], ORANGE)
    draw.rectangle((cx - 1, cy - 1, cx + 1, cy), fill=YELLOW)
    draw.point((cx, cy - 1), fill=WHITE_HOT)
    draw.line((cx - 2, cy + 2, max(0, cx - 5 - frame), min(15, cy + 5)),
              fill=CRIMSON, width=1)
    draw.point((2 + frame * 3, 2 + (frame * 5) % 11), fill=GOLD)
    return image


def make_flame_ribbon(frame: int) -> Image.Image:
    image = canvas(32)
    start_x = 3
    end_x = 28 - (frame % 3)
    center = 17 + (frame % 3) - 1
    top = []
    bottom = []
    for index in range(8):
        t = index / 7
        x = start_x + (end_x - start_x) * t
        wave = math.sin(t * math.tau * 1.25 + frame * 0.72) * (1.2 + t)
        width = 1.5 + math.sin(math.pi * t) * 4.2 + (1.0 - t) * 1.5
        top.append((x, center + wave - width))
        bottom.append((x, center + wave + width))
    draw_cluster(image, top + list(reversed(bottom)), 320 + frame, OUTER + BODY, 2)
    inner = [(5, center), (10, center - 3 + frame % 2), (18, center - 2),
             (26, center + (frame % 3) - 1), (20, center + 3), (11, center + 2)]
    draw_cluster(image, inner, 350 + frame, HOT + CORE, 1)
    if frame % 2 == 0:
        clear_cluster(image, [(13, center - 5), (19, center - 4),
                              (17, center - 2), (12, center - 3)])
    draw = ImageDraw.Draw(image)
    draw_spark(draw, 27 - frame % 4, 6 + (frame * 3) % 7, frame)
    draw.rectangle((5 + frame * 2 % 11, 25 - frame % 5,
                    6 + frame * 2 % 11, 26 - frame % 5), fill=VERMILION)
    return image


def make_impact_ring(frame: int) -> Image.Image:
    image = canvas(32)
    cx = cy = 15.5
    radius = 4.0 + frame * 2.05
    pixels = image.load()
    for y in range(32):
        for x in range(32):
            dx = x - cx
            dy = (y - cy) * 1.07
            distance = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 36)
            if (segment + frame * 4) % 13 == 0:
                continue
            delta = abs(distance - radius - ((pixel_hash(x, y, frame) % 5) - 2) * 0.18)
            if delta > (2.0 if frame < 3 else 1.35):
                continue
            if delta < 0.45:
                palette = (WHITE_HOT, IVORY, PALE_GOLD)
            elif delta < 0.95:
                palette = (YELLOW, GOLD, AMBER)
            else:
                palette = (ORANGE, VERMILION, CRIMSON, OXBLOOD)
            pixels[x, y] = palette[pixel_hash(x // 2, y // 2, frame + 390) % len(palette)]
    draw = ImageDraw.Draw(image)
    for index in range(5):
        angle = index * math.tau / 5 + frame * 0.38
        x = round(cx + math.cos(angle) * min(15, radius + 3))
        y = round(cy + math.sin(angle) * min(15, radius + 3) / 1.07)
        if 2 <= x < 30 and 2 <= y < 30:
            draw_spark(draw, x, y, frame + index)
    draw.rectangle((15, 15, 16, 16), fill=(WHITE_HOT, PALE_GOLD, GOLD)[min(2, frame // 2)])
    return image


def make_pyre_front(frame: int) -> Image.Image:
    image = canvas(64)
    base = 54
    for index in range(9):
        x = 5 + index * 7
        height = 17 + ((index * 9 + frame * 7) % 28)
        width = 8 + (index + frame) % 5
        draw_vertical_flame(image, x, base, height, width, frame + index, 430 + frame * 17 + index)
    band = [(2, 54), (5, 48), (13, 50), (20, 46 + frame % 3), (29, 49),
            (37, 45 + (frame + 1) % 4), (47, 49), (58, 46), (62, 53), (62, 58), (2, 58)]
    draw_cluster(image, band, 470 + frame, BODY + HOT, 2)
    core = [(7, 54), (15, 51), (24, 53), (33, 49), (42, 52), (55, 50),
            (59, 55), (48, 57), (34, 55), (21, 57), (9, 56)]
    draw_cluster(image, core, 500 + frame, CORE, 1)
    draw = ImageDraw.Draw(image)
    for index in range(10):
        x = 4 + ((index * 13 + frame * 7) % 57)
        y = 5 + ((index * 11 + frame * 5) % 38)
        if (index + frame) % 3 == 0:
            draw_spark(draw, x, y, frame + index)
        else:
            draw.rectangle((x, y, min(63, x + 1), min(63, y + 2)),
                           fill=(CRIMSON, ORANGE, GOLD)[(index + frame) % 3])
    return image


def make_meteor_shell(frame: int) -> Image.Image:
    image = canvas(64)
    cx, cy = 32, 34
    outer_points = []
    inner_points = []
    for index in range(28):
        angle = index * math.tau / 28 + frame * 0.13
        r = 27 + ((index * 7 + frame * 5) % 7) - 3
        if index in ((frame * 3 + 4) % 28, (frame * 5 + 13) % 28):
            r += 7
        outer_points.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r))
        inner_r = 15 + ((index * 3 + frame) % 4)
        inner_points.append((cx + math.cos(angle) * inner_r, cy + math.sin(angle) * inner_r))
    draw_cluster(image, outer_points, 540 + frame, OUTER + BODY, 2)
    draw_cluster(image, [(cx + math.cos(i * math.tau / 20 + frame * 0.1) * 23,
                          cy + math.sin(i * math.tau / 20 + frame * 0.1) * 23)
                         for i in range(20)], 570 + frame, HOT, 2)
    clear_cluster(image, inner_points)
    draw = ImageDraw.Draw(image)
    for index in range(4):
        x = 17 + index * 10
        peak = 2 + ((index * 7 + frame * 3) % 8)
        draw.line((x, 19, x - 3 + frame % 3, peak), fill=CRIMSON, width=3)
        draw.line((x + 1, 19, x - 2 + frame % 3, peak + 3), fill=ORANGE, width=2)
        draw.point((x - 1 + frame % 3, peak + 4), fill=YELLOW)
    for index in range(8):
        angle = index * math.tau / 8 + frame * 0.41
        x = round(cx + math.cos(angle) * 30)
        y = round(cy + math.sin(angle) * 27)
        if 3 <= x < 61 and 3 <= y < 61:
            draw_spark(draw, x, y, frame + index)
    return image


def make_meteor_warning(frame: int) -> Image.Image:
    image = canvas(32)
    base = 27
    height = 14 + (frame * 5) % 10
    width = 10 + frame % 4
    draw_vertical_flame(image, 16, base, height, width, frame, 610 + frame)
    hook = [(15, 18), (20, 13 - frame % 3), (26, 14 + frame % 4),
            (23, 17 + frame % 2), (18, 20)]
    draw_cluster(image, hook, 630 + frame, BODY + HOT, 1)
    draw = ImageDraw.Draw(image)
    draw_spark(draw, 25 - frame % 4, 7 + frame % 5, frame)
    draw.rectangle((6 + frame % 5, 23, 8 + frame % 5, 24), fill=OXBLOOD)
    return image


def make_meteor_impact(frame: int) -> Image.Image:
    image = canvas(64)
    base = 55
    growth = (0.45, 0.62, 0.80, 0.96, 1.08, 1.15, 1.12, 1.02, 0.88, 0.72)[frame]
    for index in range(11):
        x = 3 + index * 6
        peak = 16 + ((index * 13 + frame * 9) % 29)
        height = max(8, round(peak * growth))
        width = 8 + (index * 2 + frame) % 6
        draw_vertical_flame(image, x, base, height, width, frame * 2 + index,
                            660 + frame * 23 + index)
    crown = [(1, 55), (4, 46), (11, 49), (18, 41 + frame % 4),
             (26, 46), (32, 36 + (frame * 3) % 8), (39, 45),
             (47, 40 + (frame + 2) % 6), (55, 48), (62, 44), (63, 57), (1, 59)]
    draw_cluster(image, crown, 710 + frame, OUTER + BODY + HOT, 2)
    hot_base = [(7, 55), (15, 50), (24, 53), (32, 46), (41, 52),
                (52, 49), (59, 55), (54, 59), (39, 57), (25, 60), (12, 58)]
    draw_cluster(image, hot_base, 750 + frame, CORE, 1)
    if frame >= 6:
        clear_cluster(image, [(25, 19 + frame), (34, 15 + frame),
                              (41, 22 + frame), (34, 27 + frame)])
    draw = ImageDraw.Draw(image)
    for index in range(13):
        angle = math.pi + index * math.pi / 12
        radius = 13 + frame * 2 + (index % 3) * 3
        x = round(32 + math.cos(angle) * radius)
        y = round(52 + math.sin(angle) * radius * 0.75)
        if 3 <= x < 61 and 3 <= y < 61:
            draw_spark(draw, x, y, frame + index)
    return image


def make_ground_frame(frame: int, variant: int) -> Image.Image:
    image = canvas(32)
    base = 31
    specs = ((4, 13, 8), (10, 22, 10), (16, 16, 9), (22, 25, 11), (28, 14, 7))
    if variant == 1:
        specs = ((3, 17, 8), (9, 25, 11), (15, 14, 8), (21, 21, 10), (27, 19, 9))
    for index, (x, nominal_height, width) in enumerate(specs):
        pulse = ((frame * (index + 2) + variant * 3) % 7) - 3
        height = max(8, nominal_height + pulse)
        draw_vertical_flame(image, x, base, height, width, frame + index + variant * 2,
                            800 + variant * 100 + frame * 17 + index)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 29, 30, 31), fill=OXBLOOD)
    draw.line((3, 29, 9, 27, 15, 30, 22, 26 + frame % 3, 29, 29), fill=ORANGE, width=2)
    draw.line((7, 29, 14, 28, 21, 28, 26, 27), fill=YELLOW, width=1)
    return image


def make_ground_sheet(variant: int) -> Image.Image:
    sheet = canvas((32, 32 * 8))
    for frame in range(8):
        sheet.alpha_composite(make_ground_frame(frame, variant), (0, frame * 32))
    return sheet


def output_map() -> dict[Path, Image.Image]:
    outputs: dict[Path, Image.Image] = {
        TEXTURES / "gui/ability/fire_primary.png": make_primary_icon(),
        TEXTURES / "gui/ability/fire_secondary.png": make_secondary_icon(),
        TEXTURES / "gui/ability/fire_ultimate.png": make_ultimate_icon(),
        TEXTURES / "block/fire_ground_a.png": make_ground_sheet(0),
        TEXTURES / "block/fire_ground_b.png": make_ground_sheet(1),
    }
    for frame in range(10):
        outputs[TEXTURES / f"entity/inferno_stream_{frame}.png"] = make_inferno_stream(frame)
        outputs[TEXTURES / f"entity/inferno_front_{frame}.png"] = make_inferno_front(frame)
    families: tuple[tuple[str, int, Callable[[int], Image.Image]], ...] = (
        ("ember", 4, make_ember),
        ("flame_ribbon", 8, make_flame_ribbon),
        ("impact_ring", 6, make_impact_ring),
        ("pyre_front", 8, make_pyre_front),
        ("meteor_shell", 8, make_meteor_shell),
        ("meteor_warning", 8, make_meteor_warning),
        ("meteor_impact", 10, make_meteor_impact),
    )
    for family, count, factory in families:
        for frame in range(count):
            outputs[TEXTURES / f"particle/fire/{family}_{frame}.png"] = factory(frame)
    return outputs


def metadata_outputs() -> dict[Path, str]:
    outputs: dict[Path, str] = {
        TEXTURES / "block/fire_ground_a.png.mcmeta": json.dumps(
            {"animation": {"frametime": 2, "interpolate": False}}, indent=2) + "\n",
        TEXTURES / "block/fire_ground_b.png.mcmeta": json.dumps(
            {"animation": {"frametime": 2, "interpolate": False,
                           "frames": [3, 4, 5, 6, 7, 0, 1, 2]}}, indent=2) + "\n",
    }
    families = {
        "fire_ember": ("ember", 4),
        "fire_flame_ribbon": ("flame_ribbon", 8),
        "fire_impact_ring": ("impact_ring", 6),
        "fire_pyre_front": ("pyre_front", 8),
        "fire_meteor_shell": ("meteor_shell", 8),
        "fire_meteor_warning": ("meteor_warning", 8),
        "fire_meteor_impact": ("meteor_impact", 10),
    }
    for definition, (family, count) in families.items():
        outputs[PARTICLES / f"{definition}.json"] = json.dumps(
            {"textures": [f"elementalwands:fire/{family}_{frame}" for frame in range(count)]},
            indent=2,
        ) + "\n"
    return outputs


def retired_outputs() -> tuple[Path, ...]:
    legacy_families = {"ash": 4, "meteor": 8, "pyre_fissure": 6}
    paths = [
        TEXTURES / f"particle/fire/{family}_{frame}.png"
        for family, count in legacy_families.items()
        for frame in range(count)
    ]
    paths.extend(TEXTURES / f"entity/inferno_wave_{frame}.png" for frame in range(6))
    paths.extend([
        TEXTURES / "block/inferno_flame.png",
        TEXTURES / "block/inferno_flame.png.mcmeta",
        PARTICLES / "fire_inferno_flame.json",
    ])
    return tuple(paths)


def visible_colors(image: Image.Image) -> set[tuple[int, int, int, int]]:
    colors = image.getcolors(maxcolors=image.width * image.height + 1) or []
    return {rgba for _count, rgba in colors if rgba[3] > 0}


def adjacent_difference(left: Image.Image, right: Image.Image) -> float:
    if left.size != right.size:
        return 1.0
    a = left.tobytes()
    b = right.tobytes()
    pixels = left.width * left.height
    changed = sum(a[index:index + 4] != b[index:index + 4] for index in range(0, len(a), 4))
    return changed / pixels


def validate_outputs(outputs: dict[Path, Image.Image]) -> None:
    if len(outputs) != 77:
        raise AssertionError(f"Fire output contract is {len(outputs)} PNGs, expected 77")
    for path, expected in outputs.items():
        with Image.open(path) as reopened:
            reopened.load()
            if reopened.mode != "RGBA":
                raise AssertionError(f"{path}: expected RGBA, got {reopened.mode}")
            if reopened.size != expected.size or reopened.tobytes() != expected.tobytes():
                raise AssertionError(f"{path}: differs from deterministic source")
            colors = visible_colors(reopened)
            if reopened.getbbox() is None or not colors:
                raise AssertionError(f"{path}: empty image")
            if any(r == 0 and g == 0 and b == 0 and a > 0 for r, g, b, a in colors):
                raise AssertionError(f"{path}: contains opaque pure black")
            if ("gui" not in path.parts and reopened.width >= 32
                    and reopened.height <= 64 and len(colors) < 7):
                raise AssertionError(f"{path}: only {len(colors)} visible colors, expected at least 7")
            if reopened.width >= 64 and len(colors) < 10:
                raise AssertionError(f"{path}: only {len(colors)} visible colors, expected at least 10")
            if not any(rgba[3] == 0 for _count, rgba in (reopened.getcolors(maxcolors=65537) or [])):
                raise AssertionError(f"{path}: missing transparency")

    sequences: list[list[Path]] = [
        [TEXTURES / f"entity/inferno_stream_{frame}.png" for frame in range(10)],
        [TEXTURES / f"entity/inferno_front_{frame}.png" for frame in range(10)],
    ]
    for family, count in (("ember", 4), ("flame_ribbon", 8), ("impact_ring", 6),
                          ("pyre_front", 8), ("meteor_shell", 8),
                          ("meteor_warning", 8), ("meteor_impact", 10)):
        sequences.append([TEXTURES / f"particle/fire/{family}_{frame}.png" for frame in range(count)])
    for sequence in sequences:
        images = [outputs[path] for path in sequence]
        hashes = {hashlib.sha256(image.tobytes()).digest() for image in images}
        if len(hashes) != len(images):
            raise AssertionError(f"{sequence[0].parent}: duplicate animation frame")
        for index, (left, right) in enumerate(zip(images, images[1:])):
            difference = adjacent_difference(left, right)
            if difference < 0.055:
                raise AssertionError(
                    f"{sequence[index + 1]}: adjacent-frame difference {difference:.3f} is too small"
                )


def make_contact_sheet(outputs: dict[Path, Image.Image]) -> None:
    rows: list[tuple[str, list[Path]]] = [
        ("INFERNO STREAM", [TEXTURES / f"entity/inferno_stream_{i}.png" for i in range(10)]),
        ("INFERNO FRONT", [TEXTURES / f"entity/inferno_front_{i}.png" for i in range(10)]),
        ("FLAME RIBBON", [TEXTURES / f"particle/fire/flame_ribbon_{i}.png" for i in range(8)]),
        ("PYRE FRONT", [TEXTURES / f"particle/fire/pyre_front_{i}.png" for i in range(8)]),
        ("METEOR SHELL", [TEXTURES / f"particle/fire/meteor_shell_{i}.png" for i in range(8)]),
        ("METEOR WARNING", [TEXTURES / f"particle/fire/meteor_warning_{i}.png" for i in range(8)]),
        ("METEOR IMPACT", [TEXTURES / f"particle/fire/meteor_impact_{i}.png" for i in range(10)]),
        ("IMPACT RING", [TEXTURES / f"particle/fire/impact_ring_{i}.png" for i in range(6)]),
        ("EMBER", [TEXTURES / f"particle/fire/ember_{i}.png" for i in range(4)]),
        ("GROUND FIRE", [TEXTURES / "block/fire_ground_a.png", TEXTURES / "block/fire_ground_b.png"]),
    ]
    columns = 5
    cell_size = 208
    preview_size = 192
    section_heights = [34 + math.ceil(len(paths) / columns) * cell_size for _label, paths in rows]
    sheet_width = 24 + columns * cell_size
    sheet_height = 20 + sum(section_heights)
    sheet = Image.new("RGBA", (sheet_width, sheet_height), (35, 27, 24, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    section_y = 10
    for row_index, ((label, paths), section_height) in enumerate(zip(rows, section_heights)):
        draw.rectangle((6, section_y, sheet_width - 6, section_y + section_height - 6),
                       fill=(45 + (row_index % 2) * 5, 32, 28, 255))
        draw.text((14, section_y + 10), label, fill=(255, 223, 160, 255), font=font)
        for frame_index, path in enumerate(paths):
            sprite = outputs[path]
            if sprite.height > 64:
                sprite = sprite.crop((0, 0, 32, 32))
            factor = max(1, preview_size // max(sprite.size))
            preview = sprite.resize((sprite.width * factor, sprite.height * factor), Image.Resampling.NEAREST)
            column = frame_index % columns
            line = frame_index // columns
            cell_x = 12 + column * cell_size
            cell_y = section_y + 30 + line * cell_size
            sheet.alpha_composite(preview, (
                cell_x + (preview_size - preview.width) // 2,
                cell_y + (preview_size - preview.height) // 2,
            ))
            draw.text((cell_x + 4, cell_y + preview_size - 12),
                      str(frame_index), fill=(255, 197, 96, 255), font=font)
        section_y += section_height
    sheet.convert("RGB").save(CONTACT_SHEET, quality=95)


def write_text_outputs(outputs: dict[Path, str], replace: bool) -> None:
    for path, text in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists() and path.read_text(encoding="utf-8") != text and not replace:
            raise SystemExit(f"Refusing to overwrite differing Fire metadata: {path.relative_to(ROOT)}")
        path.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="replace the complete Kinetic Inferno Fire-owned package")
    args = parser.parse_args()
    outputs = output_map()
    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        differs = True
        if path.exists():
            with Image.open(path) as existing:
                existing.load()
                differs = existing.mode != "RGBA" or existing.size != image.size or existing.tobytes() != image.tobytes()
        if differs:
            if path.exists() and not args.replace:
                raise SystemExit(f"Refusing to overwrite differing Fire asset: {path.relative_to(ROOT)}")
            image.save(path, format="PNG", optimize=False, compress_level=9)
    write_text_outputs(metadata_outputs(), args.replace)
    if args.replace:
        for path in retired_outputs():
            if path.exists() and path not in outputs:
                path.unlink()
    validate_outputs(outputs)
    make_contact_sheet(outputs)
    for path, image in outputs.items():
        digest = hashlib.sha256(image.tobytes()).hexdigest()[:12]
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={len(visible_colors(image))} sha256={digest}")
    print(f"CONTACT_SHEET {CONTACT_SHEET}")
    print(f"FIRE_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
