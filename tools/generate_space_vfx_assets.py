#!/usr/bin/env python3
"""Generate the Space / Starved Cosmos production texture package.

The artwork is authored directly at its final pixel resolution. There is no
source-image scaling, antialiasing, blur, random state, or external input. This
keeps every clustered highlight, void shadow, and stepped silhouette stable and
Minecraft-native across repeated runs.
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
VOID = (3, 4, 10, 255)
VOID_BLUE = (7, 9, 19, 255)
VOID_VIOLET = (15, 8, 27, 255)
DEEP_VIOLET = (34, 13, 55, 255)
BRUISED_VIOLET = (69, 25, 103, 255)
VIOLET = (112, 43, 161, 255)
MAGENTA_DARK = (134, 35, 126, 255)
MAGENTA = (221, 65, 187, 255)
MAGENTA_PALE = (255, 151, 225, 255)
CYAN_DARK = (23, 96, 116, 255)
CYAN = (55, 211, 224, 255)
CYAN_PALE = (163, 244, 240, 255)
BONE_SHADOW = (176, 172, 174, 255)
BONE = (231, 227, 214, 255)
STARLIGHT = (255, 250, 229, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def alpha(color: tuple[int, int, int, int], value: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], value


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 43 + y * 71 + seed * 109 + x * y * 11 + x * x * 3) & 255


def draw_star(draw: ImageDraw.ImageDraw, x: int, y: int, color=STARLIGHT, size: int = 2) -> None:
    shadow = DEEP_VIOLET if color in (STARLIGHT, BONE) else VOID_VIOLET
    draw.line((x, y - size, x, y + size), fill=shadow, width=1)
    draw.line((x - size, y, x + size, y), fill=shadow, width=1)
    if size > 1:
        draw.line((x - 1, y - 1, x + 1, y + 1), fill=color, width=1)
        draw.line((x + 1, y - 1, x - 1, y + 1), fill=color, width=1)
    draw.point((x, y), fill=STARLIGHT)


def sampled_ellipse(cx: float, cy: float, rx: float, ry: float, phase: float = 0.0,
                    samples: int = 96) -> list[tuple[int, int]]:
    points: list[tuple[int, int]] = []
    for step in range(samples):
        angle = phase + step / samples * math.tau
        point = (round(cx + math.cos(angle) * rx), round(cy + math.sin(angle) * ry))
        if not points or point != points[-1]:
            points.append(point)
    return points


def draw_broken_path(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], color, width: int,
                     seed: int, gap_mod: int = 11) -> None:
    segment: list[tuple[int, int]] = []
    for index, point in enumerate(points):
        gap = ((index // 3 + seed * 2) % gap_mod == 0) or ((index + seed * 5) % 37 == 0)
        if gap:
            if len(segment) >= 2:
                draw.line(segment, fill=color, width=width)
            segment = []
        else:
            segment.append(point)
    if len(segment) >= 2:
        draw.line(segment, fill=color, width=width)


def make_mote(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    centers = ((6, 10), (8, 8), (9, 6), (7, 7))
    cx, cy = centers[frame]
    radius = (2, 3, 3, 2)[frame]
    poly(draw, [(cx, cy - radius - 1), (cx + 1, cy - 1), (cx + radius, cy),
                (cx + 1, cy + 1), (cx, cy + radius), (cx - 1, cy + 1),
                (cx - radius, cy), (cx - 1, cy - 1)], DEEP_VIOLET)
    poly(draw, [(cx, cy - radius), (cx + radius - 1, cy), (cx, cy + radius - 1),
                (cx - radius + 1, cy)], CYAN_PALE if frame % 2 == 0 else MAGENTA_PALE)
    draw.rectangle((cx, cy - 1, cx + 1, cy), fill=STARLIGHT)
    tail = [(cx - 1, cy + 2), (cx - 3 - frame, cy + 4), (cx - 5 - frame // 2, cy + 4)]
    draw.line(tail, fill=alpha(VIOLET, 165), width=1)
    draw.point((2 + frame * 3, 3 + (frame * 5) % 9), fill=alpha(BONE, 155))
    return image


def make_singularity(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    cx = 15.5 + ((frame % 3) - 1) * 0.35
    cy = 15.5
    core_radius = 7.1 + (1 if frame in (2, 5) else 0)

    for ray in range(12):
        angle = ray / 12.0 * math.tau + frame * 0.13
        inner = core_radius + 0.5
        outer = inner + 2 + ((ray * 3 + frame) % 4)
        spread = 0.11 + (ray % 3) * 0.018
        color = CYAN_DARK if (ray + frame) % 3 == 0 else MAGENTA_DARK if ray % 2 else VIOLET
        poly(draw, [
            (cx + math.cos(angle - spread) * inner, cy + math.sin(angle - spread) * inner),
            (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer),
            (cx + math.cos(angle + spread) * inner, cy + math.sin(angle + spread) * inner),
        ], alpha(color, 205))

    pixels = image.load()
    for y in range(4, 28):
        for x in range(4, 28):
            dx = x - cx
            dy = y - cy
            distance = math.sqrt(dx * dx + dy * dy)
            noise = pixel_hash(x // 2, y // 2, frame)
            ragged = ((noise % 5) - 2) * 0.28
            if distance <= core_radius + ragged:
                if distance > core_radius - 1.25:
                    pixels[x, y] = DEEP_VIOLET if noise < 140 else VOID_VIOLET
                elif noise < 58:
                    pixels[x, y] = VOID_VIOLET
                elif noise > 225:
                    pixels[x, y] = VOID_BLUE
                else:
                    pixels[x, y] = VOID

    outer = sampled_ellipse(cx, cy, 11.2, 8.5, frame * 0.18, 88)
    draw_broken_path(draw, outer, alpha(VIOLET, 210), 2, frame, 9)
    inner = sampled_ellipse(cx, cy, 10.3, 7.7, frame * 0.18, 88)
    draw_broken_path(draw, inner, alpha(CYAN_PALE if frame % 2 else MAGENTA_PALE, 210), 1, frame + 2, 13)
    for x, y, color in ((5, 8, CYAN), (26, 21, MAGENTA), (7, 25, BONE), (24, 6, STARLIGHT)):
        if (x + frame) % 3:
            draw.point((x + ((frame + y) % 3) - 1, y), fill=alpha(color, 185))
    return image


def make_broken_orbit(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame * math.tau / 18.0
    outer = sampled_ellipse(15.5, 15.5, 12.8, 7.2, phase, 112)
    inner = sampled_ellipse(15.5, 15.5, 8.7, 12.2, -phase * 0.72, 112)
    draw_broken_path(draw, outer, alpha(DEEP_VIOLET, 225), 4, frame, 10)
    draw_broken_path(draw, outer, alpha(MAGENTA_PALE, 220), 1, frame + 1, 10)
    draw_broken_path(draw, inner, alpha(BRUISED_VIOLET, 220), 3, frame + 3, 12)
    draw_broken_path(draw, inner, alpha(CYAN_PALE, 215), 1, frame + 5, 12)
    for index in range(4):
        angle = phase + index * math.tau / 4.0
        x = round(15.5 + math.cos(angle) * (11.2 if index % 2 else 9.6))
        y = round(15.5 + math.sin(angle) * (7.0 if index % 2 else 10.0))
        draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=DEEP_VIOLET)
        draw.point((x, y), fill=STARLIGHT if index == frame % 4 else CYAN)
    draw.rectangle((14, 14, 17, 17), fill=alpha(VOID, 235))
    return image


def make_implosion_ring(frame: int) -> Image.Image:
    image = canvas(32)
    pixels = image.load()
    cx = cy = 15.5
    radius = 13.3 - frame * 1.72
    thickness = 1.8 if frame < 3 else 1.35
    for y in range(32):
        for x in range(32):
            dx = x - cx
            dy = (y - cy) * 1.08
            distance = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 30)
            gap = (segment + frame * 3) % 11 == 0 or (frame > 2 and (segment + frame) % 8 == 0)
            delta = abs(distance - radius)
            if not gap and delta <= thickness:
                if delta < 0.48:
                    pixels[x, y] = STARLIGHT if frame >= 4 else CYAN_PALE
                elif delta < 1.0:
                    pixels[x, y] = MAGENTA_PALE if segment % 2 else VIOLET
                else:
                    pixels[x, y] = alpha(DEEP_VIOLET, 190)
    draw = ImageDraw.Draw(image)
    for side in range(4):
        angle = side * math.pi / 2 + frame * 0.15
        start = (round(cx + math.cos(angle) * (radius + 4)), round(cy + math.sin(angle) * (radius + 4)))
        end = (round(cx + math.cos(angle) * max(1, radius - 2)), round(cy + math.sin(angle) * max(1, radius - 2)))
        draw.line((start, end), fill=alpha(VIOLET, 155), width=1)
    if frame >= 4:
        draw_star(draw, 16, 16, STARLIGHT, 1)
    return image


def make_rift(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    cx = 15 + (frame % 3 == 1)
    widths = (3, 4, 5, 4, 3, 2)
    half_width = widths[frame]
    left: list[tuple[int, int]] = []
    right: list[tuple[int, int]] = []
    for y in range(2, 30, 2):
        jitter = ((pixel_hash(y, frame, 4) % 3) - 1)
        taper = 1 if y < 6 or y > 25 else 0
        left.append((cx - half_width + taper + jitter, y))
        right.append((cx + half_width - taper + jitter, y))
    silhouette = left + list(reversed(right))
    expanded = [(x + (-2 if x < cx else 2), y) for x, y in silhouette]
    poly(draw, expanded, alpha(DEEP_VIOLET, 220))
    poly(draw, silhouette, VOID)

    # Restrained value clusters give the slit a stone-like modern-Minecraft
    # material read while preserving the uninterrupted near-black aperture.
    pixels = image.load()
    for y in range(4, 29):
        for x in range(max(0, cx - half_width + 1), min(32, cx + half_width)):
            if pixels[x, y] != VOID:
                continue
            noise = pixel_hash(x, y, frame + 17)
            if noise < 32:
                pixels[x, y] = VOID_VIOLET
            elif noise > 232:
                pixels[x, y] = VOID_BLUE
            elif 118 < noise < 132 and y % 3 == 0:
                pixels[x, y] = (9, 6, 18, 255)

    draw = ImageDraw.Draw(image)
    draw.line(left[1:-1], fill=MAGENTA_DARK, width=1)
    draw.line(right[1:-1], fill=CYAN_DARK, width=1)
    draw.line([(cx, 4), (cx - 1, 10), (cx + 1, 16), (cx, 22), (cx, 28)],
              fill=VOID_BLUE, width=1)

    for side in (-1, 1):
        for index in range(3):
            y = 7 + index * 8 + ((frame + index) % 3) - 1
            x0 = cx + side * (half_width + 3)
            x1 = cx + side * (half_width + 7 + index)
            draw.line((x1, y + side, x0, y), fill=alpha(VIOLET if index != 1 else CYAN, 170), width=1)

    # Edge knots deliberately break the two chromatic seams into uneven mineral
    # clusters instead of leaving clean neon bands.
    for index, y in enumerate((5 + frame % 3, 12, 19 + (frame + 1) % 3, 26)):
        left_x = cx - half_width + ((pixel_hash(index, y, frame) % 3) - 1)
        right_x = cx + half_width + ((pixel_hash(y, index, frame) % 3) - 1)
        draw.rectangle((left_x - 1, y, left_x, y + (index % 2)),
                       fill=CYAN_DARK if index % 3 == 0 else MAGENTA_DARK)
        draw.rectangle((right_x, y - (index % 2), right_x + 1, y),
                       fill=MAGENTA_DARK if index % 3 == 0 else CYAN_DARK)

    swallowed = ((cx - 1, 9 + frame % 4), (cx + 1, 21 - frame % 3), (cx, 15 + frame % 2))
    for index, (x, y) in enumerate(swallowed):
        draw.point((x, y), fill=alpha(BONE if index < 2 else VIOLET, 115 + index * 28))
    draw.point((cx, 3 + frame), fill=STARLIGHT)
    draw.point((cx - 9, 10 + frame % 4), fill=alpha(BONE, 150))
    draw.point((cx + 10, 23 - frame % 4), fill=alpha(CYAN_PALE, 145))
    return image


def make_dying_star(frame: int, cyan: bool) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    cx = cy = 15.5
    radius = 6.4 + (1 if frame in (1, 4) else 0)
    bright = CYAN if cyan else MAGENTA
    pale = CYAN_PALE if cyan else MAGENTA_PALE
    dark = CYAN_DARK if cyan else MAGENTA_DARK

    for ray in range(14):
        angle = ray / 14.0 * math.tau + frame * 0.11
        inner = radius - 0.5
        outer = radius + 3 + ((ray + frame * 2) % 5)
        spread = 0.09 + (ray % 2) * 0.03
        blackened = (ray + frame) % 4 == 0 or (frame >= 4 and ray % 3 == 0)
        color = DEEP_VIOLET if blackened else dark if ray % 2 else bright
        poly(draw, [
            (cx + math.cos(angle - spread) * inner, cy + math.sin(angle - spread) * inner),
            (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer),
            (cx + math.cos(angle + spread) * inner, cy + math.sin(angle + spread) * inner),
        ], color)

    pixels = image.load()
    for y in range(7, 25):
        for x in range(7, 25):
            dx = x - cx
            dy = y - cy
            distance = math.sqrt(dx * dx + dy * dy)
            noise = pixel_hash(x // 2, y // 2, frame + (2 if cyan else 7))
            if distance <= radius + ((noise % 5) - 2) * 0.22:
                if distance > radius - 1.3:
                    pixels[x, y] = dark if noise < 145 else DEEP_VIOLET
                elif noise < 72 + frame * 10:
                    pixels[x, y] = VOID
                elif noise < 170:
                    pixels[x, y] = VOID_VIOLET
                else:
                    pixels[x, y] = bright
    draw = ImageDraw.Draw(image)
    core_size = max(1, 3 - frame // 3)
    draw.rectangle((15 - core_size, 15 - core_size, 16 + core_size, 16 + core_size), fill=pale)
    draw.rectangle((15, 15, 16, 16), fill=STARLIGHT)
    draw.arc((7, 9, 25, 23), 202 + frame * 8, 336 + frame * 8, fill=alpha(pale, 190), width=1)
    return image


def make_pinpoint(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    sizes = (1, 2, 3, 1)
    size = sizes[frame]
    cx = 8 if frame == 3 else 7
    cy = 7
    draw.line((cx, cy - size - 2, cx, cy + size + 2), fill=alpha(BONE_SHADOW, 150), width=1)
    draw.line((cx - size - 2, cy, cx + size + 2, cy), fill=alpha(VIOLET, 155), width=1)
    poly(draw, [(cx, cy - size), (cx + size, cy), (cx, cy + size), (cx - size, cy)], BONE)
    draw.rectangle((cx, cy, cx + 1, cy + 1), fill=STARLIGHT)
    if frame == 2:
        draw.point((3, 4), fill=CYAN_PALE)
        draw.point((12, 10), fill=MAGENTA_PALE)
    elif frame == 3:
        draw.point((5, 11), fill=alpha(BONE, 165))
    return image


def make_eclipse(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx = 31.5 + ((frame % 3) - 1) * 0.45
    cy = 31.5
    core_radius = 19.0 + (1 if frame in (2, 6) else 0)

    for ray in range(24):
        angle = ray / 24.0 * math.tau + frame * 0.075
        inner = core_radius - 0.5
        outer = core_radius + 5 + ((ray * 5 + frame * 3) % 8)
        spread = 0.045 + (ray % 4) * 0.012
        if ray % 5 == 0:
            color = CYAN_DARK
        elif ray % 3 == 0:
            color = MAGENTA_DARK
        else:
            color = BRUISED_VIOLET
        poly(draw, [
            (cx + math.cos(angle - spread) * inner, cy + math.sin(angle - spread) * inner),
            (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer),
            (cx + math.cos(angle + spread) * inner, cy + math.sin(angle + spread) * inner),
        ], alpha(color, 230))

    pixels = image.load()
    for y in range(6, 58):
        for x in range(6, 58):
            dx = x - cx
            dy = y - cy
            distance = math.sqrt(dx * dx + dy * dy)
            noise = pixel_hash(x // 2, y // 2, frame * 3)
            ragged = ((noise % 7) - 3) * 0.24
            if distance <= core_radius + ragged:
                if distance > core_radius - 1.8:
                    pixels[x, y] = DEEP_VIOLET if noise < 160 else VOID_VIOLET
                elif noise < 45:
                    pixels[x, y] = VOID_VIOLET
                elif noise > 229:
                    pixels[x, y] = VOID_BLUE
                else:
                    pixels[x, y] = VOID

    outer = sampled_ellipse(cx, cy, 27.2, 22.8, frame * 0.06, 180)
    draw_broken_path(draw, outer, alpha(VIOLET, 225), 3, frame, 13)
    highlight = sampled_ellipse(cx, cy, 27.2, 22.8, frame * 0.06, 180)
    draw_broken_path(draw, highlight, alpha(CYAN_PALE if frame % 2 else MAGENTA_PALE, 220), 1, frame + 7, 17)
    inner = sampled_ellipse(cx, cy, 22.2, 19.3, -frame * 0.09, 156)
    draw_broken_path(draw, inner, alpha(MAGENTA if frame % 2 else CYAN, 185), 1, frame + 3, 19)

    consumed = ((5, 13), (57, 18), (8, 48), (54, 51), (31, 3), (61, 35))
    for index, (x, y) in enumerate(consumed):
        shift = (frame + index) % 4
        tx = x + (1 if x < cx else -1) * shift
        ty = y + (1 if y < cy else -1) * (shift // 2)
        color = CYAN_PALE if index % 3 == 0 else MAGENTA_PALE if index % 3 == 1 else BONE
        draw.line((x, y, tx + (2 if x < cx else -2), ty), fill=alpha(color, 170), width=1)
        draw.point((tx, ty), fill=STARLIGHT)
    return image


def make_gravity_lens(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx = cy = 31.5
    radius = 19.0 + frame * 1.65
    phase = frame * 0.11
    ring = sampled_ellipse(cx, cy, radius, radius * (0.82 + (frame % 2) * 0.035), phase, 192)
    draw_broken_path(draw, ring, alpha(DEEP_VIOLET, 205 - frame * 9), 5 if frame < 4 else 3, frame, 12)
    draw_broken_path(draw, ring, alpha(BONE, 225 - frame * 10), 1, frame + 4, 12)
    ring2 = sampled_ellipse(cx, cy, radius * 0.84, radius * 0.61, -phase * 1.3, 176)
    draw_broken_path(draw, ring2, alpha(VIOLET, 185 - frame * 8), 3, frame + 2, 15)
    draw_broken_path(draw, ring2, alpha(CYAN_PALE if frame % 2 else MAGENTA_PALE, 205 - frame * 8), 1,
                     frame + 8, 15)
    for index in range(8):
        angle = index / 8.0 * math.tau + phase
        start_r = radius + 7 + (index % 2) * 3
        end_r = radius - 2
        sx = round(cx + math.cos(angle) * start_r)
        sy = round(cy + math.sin(angle) * start_r * 0.86)
        ex = round(cx + math.cos(angle) * end_r)
        ey = round(cy + math.sin(angle) * end_r * 0.86)
        draw.line((sx, sy, ex, ey), fill=alpha(CYAN_DARK if index % 2 else MAGENTA_DARK, 135), width=1)
    return image


def make_consumption(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame * 0.7
    path: list[tuple[int, int]] = []
    for x in range(3, 29, 2):
        y = 16 + round(math.sin(x * 0.33 + phase) * (4 - frame * 0.35))
        path.append((x, y))
    draw.line(path, fill=alpha(DEEP_VIOLET, 195), width=5)
    draw.line(path[2:-1], fill=alpha(MAGENTA_DARK if frame % 2 else CYAN_DARK, 220), width=3)
    draw.line(path[5:-2], fill=alpha(BONE, 210), width=1)
    # Carve deliberate gaps so this reads as stretched matter, not a magic beam.
    gap_x = 8 + frame * 3
    draw.rectangle((gap_x, 9, gap_x + 2, 23), fill=T)
    for index in range(4):
        x = 4 + ((index * 7 + frame * 4) % 23)
        y = 7 + ((index * 9 + frame * 3) % 18)
        poly(draw, [(x, y - 1), (x + 2, y), (x, y + 1), (x - 1, y)],
             alpha(VIOLET if index % 2 else BONE, 155 + index * 18))
    end_x, end_y = path[-1]
    poly(draw, [(end_x - 2, end_y - 3), (end_x + 3, end_y), (end_x - 2, end_y + 3)], STARLIGHT)
    draw.point((end_x + 3, end_y), fill=STARLIGHT)
    return image


def make_final_collapse(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx = cy = 31.5

    if frame <= 2:
        rx = (20, 13, 7)[frame]
        ry = 20 + frame * 2
        outer = sampled_ellipse(cx, cy, rx + 5, ry + 4, frame * 0.08, 160)
        draw_broken_path(draw, outer, MAGENTA_DARK if frame % 2 else CYAN_DARK, 4, frame, 10)
        draw_broken_path(draw, outer, STARLIGHT, 1, frame + 4, 14)
        poly(draw, [(cx - rx, cy - ry + 3), (cx + rx, cy - ry),
                    (cx + rx - 1, cy + ry), (cx - rx + 1, cy + ry - 2)], VOID)
        draw.line((cx, cy - ry - 5, cx, cy + ry + 5), fill=DEEP_VIOLET, width=2)
    elif frame == 3:
        draw.line((32, 4, 32, 59), fill=DEEP_VIOLET, width=7)
        draw.line((32, 7, 32, 57), fill=VOID, width=3)
        draw.line((31, 12, 31, 51), fill=STARLIGHT, width=1)
        for y in (8, 19, 34, 48, 57):
            draw.line((24, y, 30, y, 34, y + 1, 41, y + 1), fill=alpha(VIOLET, 170), width=1)
    elif frame == 4:
        draw_star(draw, 32, 32, STARLIGHT, 4)
        draw.rectangle((30, 30, 33, 33), fill=STARLIGHT)
        draw.point((24, 32), fill=CYAN_PALE)
        draw.point((40, 32), fill=MAGENTA_PALE)
    else:
        ring_frame = frame - 5
        radius = 6 + ring_frame * 5.2
        ring = sampled_ellipse(cx, cy, radius, radius * (0.78 + ring_frame * 0.025), ring_frame * 0.13, 176)
        draw_broken_path(draw, ring, alpha(DEEP_VIOLET, 240 - ring_frame * 20), 6 if ring_frame < 3 else 4,
                         frame, 11)
        draw_broken_path(draw, ring, alpha(STARLIGHT, 255 - ring_frame * 22), 2 if ring_frame < 3 else 1,
                         frame + 5, 11)
        inner = sampled_ellipse(cx, cy, max(2, radius - 3), max(2, radius * 0.78 - 2), -ring_frame * 0.09, 160)
        draw_broken_path(draw, inner, alpha(CYAN_PALE if ring_frame % 2 else MAGENTA_PALE,
                                            220 - ring_frame * 17), 1, frame + 2, 15)
        if frame <= 7:
            draw_star(draw, 32, 32, STARLIGHT, max(1, 3 - ring_frame))
    return image


def make_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Layered black-star core with an asymmetric broken orbit and swallowed stars.
    for ray in range(10):
        angle = ray / 10.0 * math.tau + 0.16
        inner = 7
        outer = 10 + (ray * 3) % 5
        poly(draw, [
            (16 + math.cos(angle - 0.10) * inner, 16 + math.sin(angle - 0.10) * inner),
            (16 + math.cos(angle) * outer, 16 + math.sin(angle) * outer),
            (16 + math.cos(angle + 0.10) * inner, 16 + math.sin(angle + 0.10) * inner),
        ], CYAN_DARK if ray % 3 == 0 else MAGENTA_DARK if ray % 2 else VIOLET)
    draw.ellipse((9, 9, 23, 23), fill=DEEP_VIOLET)
    draw.ellipse((11, 11, 21, 21), fill=VOID)
    draw.rectangle((12, 12, 15, 14), fill=VOID_VIOLET)
    draw.rectangle((18, 17, 20, 20), fill=VOID_BLUE)
    orbit = sampled_ellipse(15.5, 15.5, 13, 8, 0.35, 96)
    draw_broken_path(draw, orbit, BONE_SHADOW, 2, 2, 10)
    draw_broken_path(draw, orbit, CYAN_PALE, 1, 5, 14)
    draw_star(draw, 27, 8, STARLIGHT, 1)
    draw.line((4, 24, 9, 20), fill=MAGENTA_PALE, width=1)
    return image


def make_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Tall aperture, layered edge distortion, and exactly six countdown fragments.
    poly(draw, [(13, 3), (19, 5), (20, 12), (18, 18), (20, 27), (13, 29),
                (11, 22), (13, 16), (11, 9)], VOID_VIOLET)
    poly(draw, [(13, 5), (18, 6), (19, 12), (17, 18), (19, 26), (14, 28),
                (12, 22), (14, 16), (12, 9)], DEEP_VIOLET)
    poly(draw, [(15, 6), (17, 8), (16, 14), (18, 20), (16, 26), (14, 26),
                (15, 20), (14, 14)], VOID)
    draw.line((13, 6, 12, 14, 13, 23), fill=BRUISED_VIOLET, width=1)
    draw.point((13, 8), fill=MAGENTA)
    draw.point((12, 17), fill=MAGENTA_DARK)
    draw.point((14, 25), fill=MAGENTA_PALE)
    draw.line((19, 7, 20, 15, 18, 25), fill=CYAN_DARK, width=1)
    draw.point((19, 9), fill=CYAN)
    draw.point((20, 17), fill=CYAN_PALE)
    draw.point((18, 23), fill=BONE_SHADOW)
    draw.rectangle((15, 11, 16, 13), fill=VOID_BLUE)
    draw.point((16, 17), fill=VOID_VIOLET)
    draw.point((15, 22), fill=BRUISED_VIOLET)
    for index in range(6):
        angle = index / 6.0 * math.tau - 0.3
        x = round(16 + math.cos(angle) * 12)
        y = round(16 + math.sin(angle) * 11)
        color = STARLIGHT if index == 0 else CYAN_PALE if index % 2 else MAGENTA_PALE
        shadow = CYAN_DARK if index % 2 else MAGENTA_DARK
        poly(draw, [(x, y - 2), (x + 2, y), (x, y + 2), (x - 1, y)], shadow)
        draw.line((x, y - 1, x + 1, y), fill=color, width=1)
        draw.point((x, y), fill=STARLIGHT)
    draw.point((4, 7), fill=alpha(BONE, 170))
    draw.point((27, 25), fill=alpha(VIOLET, 190))
    return image


def make_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for ray in range(18):
        angle = ray / 18.0 * math.tau + 0.08
        inner = 9
        outer = 12 + (ray * 5) % 4
        spread = 0.07
        color = CYAN_DARK if ray % 5 == 0 else MAGENTA_DARK if ray % 3 == 0 else VIOLET
        poly(draw, [
            (16 + math.cos(angle - spread) * inner, 16 + math.sin(angle - spread) * inner),
            (16 + math.cos(angle) * outer, 16 + math.sin(angle) * outer),
            (16 + math.cos(angle + spread) * inner, 16 + math.sin(angle + spread) * inner),
        ], color)
    draw.ellipse((7, 7, 25, 25), fill=DEEP_VIOLET)
    draw.ellipse((9, 9, 23, 23), fill=VOID)
    pixels = image.load()
    for y in range(9, 24):
        for x in range(9, 24):
            dx = x - 16
            dy = y - 16
            if dx * dx + dy * dy > 49 or pixels[x, y] != VOID:
                continue
            noise = pixel_hash(x // 2, y // 2, 31)
            if noise < 52:
                pixels[x, y] = VOID_VIOLET
            elif noise > 218:
                pixels[x, y] = VOID_BLUE
            elif 105 < noise < 126:
                pixels[x, y] = (10, 6, 19, 255)
    draw = ImageDraw.Draw(image)
    draw.rectangle((11, 11, 13, 12), fill=VOID_VIOLET)
    draw.rectangle((19, 18, 21, 20), fill=VOID_BLUE)
    draw.point((13, 18), fill=alpha(BONE, 170))
    draw.point((19, 13), fill=alpha(CYAN_PALE, 150))
    draw.point((17, 21), fill=alpha(MAGENTA_PALE, 135))
    ring = sampled_ellipse(15.5, 15.5, 14, 11.5, 0.18, 108)
    draw_broken_path(draw, ring, BONE, 2, 3, 12)
    draw_broken_path(draw, ring, MAGENTA_PALE, 1, 7, 15)
    for x, y, color in ((5, 10, CYAN), (10, 27, MAGENTA), (24, 5, MAGENTA), (27, 19, CYAN)):
        draw.rectangle((x, y, x + 1, y + 1), fill=DEEP_VIOLET)
        draw.point((x + (x % 2), y), fill=alpha(color, 215))
    draw.point((3, 9), fill=CYAN_PALE)
    draw.point((28, 23), fill=STARLIGHT)
    return image


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for frame in range(4):
        generated[TEXTURES / f"particle/space/mote_{frame}.png"] = make_mote(frame)
        generated[TEXTURES / f"particle/space/pinpoint_{frame}.png"] = make_pinpoint(frame)
    for frame in range(6):
        generated[TEXTURES / f"particle/space/singularity_{frame}.png"] = make_singularity(frame)
        generated[TEXTURES / f"particle/space/broken_orbit_{frame}.png"] = make_broken_orbit(frame)
        generated[TEXTURES / f"particle/space/implosion_ring_{frame}.png"] = make_implosion_ring(frame)
        generated[TEXTURES / f"particle/space/rift_{frame}.png"] = make_rift(frame)
        generated[TEXTURES / f"particle/space/dying_star_cyan_{frame}.png"] = make_dying_star(frame, True)
        generated[TEXTURES / f"particle/space/dying_star_magenta_{frame}.png"] = make_dying_star(frame, False)
        generated[TEXTURES / f"particle/space/consumption_{frame}.png"] = make_consumption(frame)
    for frame in range(8):
        generated[TEXTURES / f"particle/space/eclipse_{frame}.png"] = make_eclipse(frame)
        generated[TEXTURES / f"particle/space/gravity_lens_{frame}.png"] = make_gravity_lens(frame)
    for frame in range(12):
        generated[TEXTURES / f"particle/space/final_collapse_{frame}.png"] = make_final_collapse(frame)
    generated.update({
        TEXTURES / "gui/ability/space_primary.png": make_primary_icon(),
        TEXTURES / "gui/ability/space_secondary.png": make_secondary_icon(),
        TEXTURES / "gui/ability/space_ultimate.png": make_ultimate_icon(),
    })
    return generated


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: differs from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        alpha_min, alpha_max = reopened.getchannel("A").getextrema()
        assert alpha_min == 0 and alpha_max >= 120, f"{path}: expected transparency and visible detail"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        visible_colors = {rgba for _count, rgba in colors if rgba[3] > 0}
        assert len(visible_colors) >= 3, f"{path}: insufficient clustered material variation"
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
        bbox = reopened.getbbox()
    return len(visible_colors), bbox, digest


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    families = {
        "mote": 4,
        "pinpoint": 4,
        "singularity": 6,
        "broken_orbit": 6,
        "implosion_ring": 6,
        "rift": 6,
        "dying_star_cyan": 6,
        "dying_star_magenta": 6,
        "consumption": 6,
        "eclipse": 8,
        "gravity_lens": 8,
        "final_collapse": 12,
    }
    for family, count in families.items():
        frames = [outputs[TEXTURES / f"particle/space/{family}_{frame}.png"].tobytes()
                  for frame in range(count)]
        assert len(set(frames)) == count, f"{family}: duplicate animation frames"


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb = 104
    label_h = 25
    columns = 8
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (20, 18, 27, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        col = index % columns
        row = index // columns
        x = col * thumb
        y = row * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (91, 88, 99, 255))
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 13):
            for cx in range(0, thumb, 13):
                if (cx // 13 + cy // 13) % 2:
                    checker_draw.rectangle((cx, cy, cx + 12, cy + 12), fill=(47, 44, 54, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), relative.stem[:19], fill=(246, 239, 228, 255), font=font)
        draw.text((x + 3, y + thumb + 13), str(relative.parent)[-15:], fill=(174, 154, 190, 255), font=font)
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
                if existing.mode != "RGBA" or existing.size != image.size or existing.tobytes() != image.tobytes():
                    if not args.replace:
                        raise SystemExit(f"Refusing to overwrite differing Space texture: {path.relative_to(ROOT)}")
                    image.save(path, format="PNG", optimize=False, compress_level=9)
        else:
            image.save(path, format="PNG", optimize=False, compress_level=9)

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_space_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
