#!/usr/bin/env python3
"""Generate the second-generation Fire production texture package.

All art is authored on its final pixel grid.  There is no random state,
resampling, antialiasing, blur, or external source image.  The deterministic
cluster masks deliberately resemble modern Minecraft material textures rather
than smooth vector marks or flat cartoon emblems.
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
SOOT = (20, 15, 16, 255)
CHAR = (35, 25, 25, 255)
COAL = (54, 37, 34, 255)
ROCK = (76, 47, 39, 255)
ROCK_LIGHT = (105, 64, 46, 255)
ASH_DARK = (66, 58, 56, 225)
ASH = (111, 98, 91, 215)
ASH_LIGHT = (169, 150, 132, 195)
OXBLOOD = (82, 15, 17, 255)
CRIMSON = (137, 25, 21, 255)
RED = (203, 48, 22, 255)
ORANGE = (239, 92, 17, 255)
GOLD = (250, 158, 30, 255)
PALE_GOLD = (255, 211, 83, 255)
IVORY = (255, 242, 177, 255)
WHITE_HOT = (255, 253, 222, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def alpha(color: tuple[int, int, int, int], value: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], value


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 43 + y * 67 + seed * 109 + x * y * 11 + x * x * 3) & 255


def material_fill(image: Image.Image, mask: Image.Image, seed: int,
                  palette: tuple[tuple[int, int, int, int], ...], cluster: int = 2) -> None:
    pixels = image.load()
    mask_pixels = mask.load()
    for y in range(image.height):
        for x in range(image.width):
            if not mask_pixels[x, y]:
                continue
            noise = pixel_hash(x // cluster, y // cluster, seed)
            pixels[x, y] = palette[min(len(palette) - 1, noise * len(palette) // 256)]


def draw_spark(draw: ImageDraw.ImageDraw, x: int, y: int, seed: int, scale: int = 1) -> None:
    shadow = alpha(CRIMSON, 185)
    draw.rectangle((x, y - 2 * scale, x + scale - 1, y + 2 * scale), fill=shadow)
    draw.rectangle((x - 2 * scale, y, x + 2 * scale, y + scale - 1), fill=shadow)
    draw.rectangle((x, y - scale, x + scale - 1, y + scale - 1), fill=PALE_GOLD)
    if seed % 2 == 0:
        draw.point((x, y), fill=WHITE_HOT)


def draw_broken_ring(image: Image.Image, cx: float, cy: float, radius: float,
                     thickness: float, frame: int, vertical_scale: float = 1.0,
                     fade: int = 255) -> None:
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            dx = x - cx
            dy = (y - cy) * vertical_scale
            distance = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 32)
            gap = ((segment + frame * 3) % 11 == 0
                   or (frame > 2 and (segment * 3 + frame) % 17 == 0))
            jitter = ((pixel_hash(x // 2, y // 2, frame) % 5) - 2) * 0.18
            delta = abs(distance - radius - jitter)
            if gap or delta > thickness:
                continue
            if delta < 0.45 and frame < 8:
                color = alpha(IVORY, fade)
            elif delta < 1.05:
                color = alpha(PALE_GOLD, fade)
            elif delta < 1.55:
                color = alpha(ORANGE, max(80, fade - 22))
            else:
                color = alpha(CRIMSON, max(65, fade - 55))
            pixels[x, y] = color


def make_ember(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    cx = (6, 8, 9, 7)[frame]
    cy = (10, 8, 6, 7)[frame]
    outline = [
        (cx - 2, cy - 2), (cx + 1, cy - 3), (cx + 3, cy - 1),
        (cx + 2, cy + 2), (cx, cy + 3), (cx - 3, cy + 1),
    ]
    poly(draw, outline, SOOT)
    poly(draw, [(cx - 1, cy - 2), (cx + 1, cy - 1), (cx + 2, cy + 1),
                (cx, cy + 2), (cx - 2, cy + 1)], RED)
    draw.rectangle((cx - 1, cy - 1, cx + 1, cy), fill=GOLD)
    draw.point((cx, cy - 1), fill=WHITE_HOT)
    tail = ((cx - 2, cy + 3), (cx - 4 - frame, cy + 5), (cx - 5, cy + 6))
    draw.line(tail, fill=alpha(CRIMSON, 160), width=1)
    draw.point((2 + frame * 3, 3 + (frame * 5) % 10), fill=alpha(ORANGE, 155))
    return image


def make_ash(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    flakes = (
        ((3 + frame, 4 + frame), (2, 2), ASH),
        ((11 - frame, 3 + frame // 2), (2, 1), ASH_LIGHT),
        ((5 + frame // 2, 11 - frame), (2, 2), ASH_DARK),
        ((12 - frame // 2, 11 + frame % 2), (1, 2), ASH),
    )
    for index, ((x, y), (w, h), color) in enumerate(flakes):
        points = [(x, y), (x + w, y + (index & 1)), (x + w - 1, y + h),
                  (x - 1, y + max(0, h - 1))]
        poly(draw, points, color)
        if w > 1:
            draw.point((x, y), fill=ASH_LIGHT)
    draw.point((2 + frame * 3, 13 - frame), fill=alpha(RED, 105))
    return image


def make_flame_ribbon(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    shift = frame % 3 - 1
    silhouette = [
        (2, 23), (6, 19 + shift), (9, 20), (11, 14 - shift), (15, 16),
        (18, 8 + shift), (21, 14), (25, 5 + (frame & 1)), (24, 16),
        (30, 12 + shift), (27, 20), (22, 23), (15, 22), (8, 26),
    ]
    poly(draw, silhouette, SOOT)
    poly(draw, [(4, 23), (8, 20), (11, 21), (13, 16), (16, 18),
                (19, 11), (21, 17), (26, 9), (24, 19), (28, 16),
                (25, 21), (18, 21), (11, 24)], CRIMSON)
    poly(draw, [(7, 22), (12, 20), (15, 19), (18, 14), (20, 19),
                (24, 14), (23, 21), (16, 22)], ORANGE)
    poly(draw, [(13, 21), (17, 18), (19, 16), (20, 21), (17, 22)], PALE_GOLD)
    draw.point((18, 20), fill=WHITE_HOT)
    # Chunked soot facets and detached cinders keep each frame materially rich.
    for index in range(5):
        x = 4 + ((index * 7 + frame * 4) % 25)
        y = 4 + ((index * 5 + frame * 3) % 24)
        if index % 2:
            draw.rectangle((x, y, x + 1, y + 1), fill=alpha(COAL, 185))
        else:
            draw.point((x, y), fill=alpha(GOLD, 175))
    return image


def make_impact_ring(frame: int) -> Image.Image:
    image = canvas(32)
    radius = 4.2 + frame * 2.0
    draw_broken_ring(image, 15.5, 15.5, radius, 2.0 if frame < 3 else 1.45,
                     frame, 1.08, 250 - frame * 20)
    draw = ImageDraw.Draw(image)
    for index in range(5):
        angle = index * math.tau / 5 + frame * 0.27
        inner = radius + 1
        outer = min(15, radius + 3 + (index + frame) % 3)
        x1 = round(15.5 + math.cos(angle) * inner)
        y1 = round(15.5 + math.sin(angle) * inner / 1.08)
        x2 = round(15.5 + math.cos(angle) * outer)
        y2 = round(15.5 + math.sin(angle) * outer / 1.08)
        draw.line((x1, y1, x2, y2), fill=alpha(ORANGE, 185), width=1)
    for index in range(4):
        x = 4 + ((index * 9 + frame * 5) % 24)
        y = 5 + ((index * 7 + frame * 3) % 22)
        draw.rectangle((x, y, x + (index & 1), y + 1),
                       fill=alpha((COAL, RED)[(index + frame) & 1], 165))
    draw.rectangle((15, 15, 16, 16), fill=alpha(WHITE_HOT, max(95, 235 - frame * 24)))
    draw.point((14, 16), fill=alpha(SOOT, max(75, 185 - frame * 15)))
    return image


def make_meteor(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx, cy = 32 + (frame % 3 - 1), 34
    radius = 18 + (1 if frame in (2, 5) else 0)
    # Angular corona tongues, deliberately asymmetric and hard edged.
    for tongue in range(12):
        angle = tongue * math.tau / 12 + frame * 0.11
        inner = radius - 1
        outer = radius + 4 + ((tongue * 3 + frame) % 7)
        spread = 0.08 + (tongue % 3) * 0.018
        poly(draw, [
            (cx + math.cos(angle - spread) * inner, cy + math.sin(angle - spread) * inner),
            (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer),
            (cx + math.cos(angle + spread) * inner, cy + math.sin(angle + spread) * inner),
        ], CRIMSON if tongue % 3 else ORANGE)
    mask = Image.new("1", image.size, 0)
    md = ImageDraw.Draw(mask)
    poly(md, [(cx - 15, cy - 16), (cx - 5, cy - 20), (cx + 9, cy - 18),
              (cx + 19, cy - 7), (cx + 18, cy + 9), (cx + 9, cy + 18),
              (cx - 7, cy + 19), (cx - 18, cy + 8), (cx - 19, cy - 7)], 1)
    material_fill(image, mask, frame + 3, (CHAR, COAL, ROCK, ROCK_LIGHT), 3)
    draw = ImageDraw.Draw(image)
    # Blocky molten fracture network.
    cracks = (
        [(cx - 10, cy - 13), (cx - 5, cy - 7), (cx - 6, cy), (cx, cy + 4), (cx - 2, cy + 13)],
        [(cx + 13, cy - 8), (cx + 6, cy - 4), (cx + 4, cy + 3), (cx - 3, cy + 5), (cx - 10, cy + 12)],
        [(cx - 15, cy + 1), (cx - 7, cy + 2), (cx, cy + 7), (cx + 10, cy + 8)],
    )
    for index, crack in enumerate(cracks):
        shifted = [(x + ((frame + index) % 3) - 1, y) for x, y in crack]
        draw.line(shifted, fill=OXBLOOD, width=4)
        draw.line(shifted[1:-1], fill=ORANGE, width=2)
        draw.point(shifted[len(shifted) // 2], fill=IVORY)
    for index in range(7):
        x = 7 + ((index * 11 + frame * 5) % 50)
        y = 5 + ((index * 7 + frame * 3) % 51)
        draw.rectangle((x, y, x + (index & 1), y + 1), fill=alpha(GOLD, 160))
    return image


def make_pyre_fissure(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    extent = 7 + frame * 3
    center = 16
    # Charred ground plates build around the growing molten branch.
    for index in range(9):
        x = max(1, center - extent + index * max(2, extent * 2 // 8))
        y = center + ((index * 5 + frame * 3) % 7) - 3
        w = 2 + (index + frame) % 4
        color = (CHAR, COAL, ROCK)[(index + frame) % 3]
        poly(draw, [(x, y - 2), (x + w, y - 1), (x + w - 1, y + 2), (x - 1, y + 1)], color)
    main = [(max(1, center - extent), 18), (7, 16), (11, 17), (15, 14),
            (19, 16), (23, 13), (min(30, center + extent), 15)]
    draw.line(main, fill=OXBLOOD, width=5)
    draw.line(main[1:-1], fill=ORANGE, width=3)
    draw.line(main[2:-2], fill=PALE_GOLD, width=1)
    branches = (
        [(10, 17), (8, 12), (6, 10)],
        [(19, 16), (21, 21), (25, 23)],
        [(15, 15), (14, 10), (17, 7)],
    )
    for index, branch in enumerate(branches):
        if frame >= index + 1:
            draw.line(branch, fill=CRIMSON, width=2)
            draw.line(branch[:-1], fill=GOLD, width=1)
    for index in range(frame + 2):
        draw.point((3 + (index * 7 + frame * 2) % 27, 5 + (index * 11) % 22), fill=alpha(GOLD, 170))
    return image


def make_pyre_front(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    ground = 48
    if frame <= 4:
        half_width = 7 + frame * 6
        plate_count = 3 + frame * 3
        flame_count = 1 + frame * 2
    else:
        half_width = (30, 26, 20)[frame - 5]
        plate_count = (13, 9, 6)[frame - 5]
        flame_count = (7, 5, 3)[frame - 5]
    left = 32 - half_width
    span = half_width * 2
    fade = 255 if frame < 6 else 215 if frame == 6 else 170

    # Dense lifted charcoal plates grow outward, then visibly spall apart.
    for index in range(plate_count):
        x = left + round(index * span / max(1, plate_count - 1))
        if frame >= 5 and (index + frame) % 4 == 0:
            continue
        y = ground - ((index * 5 + frame * 3) % 6)
        lift = max(0, 5 - abs(index - plate_count // 2)) + ((index + frame) % 3)
        poly(draw, [(x - 2, y), (x, y - 2 - lift), (x + 3, y - 1 - lift),
                    (x + 4, y + 2), (x, y + 3)], alpha(SOOT, fade))
        poly(draw, [(x - 1, y), (x, y - 1 - lift), (x + 2, y - lift),
                    (x + 3, y + 1), (x, y + 2)],
             alpha((COAL, ROCK, ROCK_LIGHT)[(index + frame) % 3], fade))
        if (index + frame) % 2 == 0:
            draw.line((x, y, x + 2, y - 1 - lift), fill=alpha(CRIMSON, fade), width=1)

    # The furnace crest advances from a single ignition tooth to a full front,
    # then breaks into separated remnants instead of looping as static flicker.
    for index in range(flame_count):
        x = 32 if flame_count == 1 else left + 3 + round(index * (span - 6) / (flame_count - 1))
        base = ground - 4 + ((index + frame) % 3)
        height = 9 + ((index * 7 + frame * 5) % 16)
        if frame <= 3:
            height += frame * 2
        sway = ((frame + index) % 3) - 1
        poly(draw, [(x - 4, base), (x - 3, base - height // 2), (x + sway, base - height),
                    (x + 2, base - height // 2 - 2), (x + 4, base)], alpha(CRIMSON, fade))
        poly(draw, [(x - 2, base), (x - 1, base - height // 2), (x + sway, base - height + 5),
                    (x + 2, base - height // 3), (x + 2, base)], alpha(ORANGE, fade))
        if height > 18:
            draw.rectangle((x, base - height // 2, x + 1, base - height // 2 + 4),
                           fill=alpha(PALE_GOLD, fade))
    draw.line((max(1, left - 2), 50, min(62, left + span + 2), 50), fill=alpha(OXBLOOD, fade), width=4)
    draw.line((left + 2, 49, min(62, left + span - 2), 49), fill=alpha(GOLD, fade), width=1)
    debris_count = 3 + frame if frame <= 5 else 12 - frame
    for index in range(debris_count):
        x = max(1, left - 4) + ((index * 13 + frame * 5) % min(61, span + 8))
        y = 8 + ((index * 9 + frame * 7) % 35)
        color = GOLD if index % 3 else ROCK_LIGHT
        draw.rectangle((x, y, x + (index & 1), y + (index % 3 == 0)),
                       fill=alpha(color, min(fade, 145 + (index % 3) * 25)))
    draw.point((min(62, left + span), 8 + frame), fill=alpha(WHITE_HOT, fade))
    return image


def make_meteor_warning(frame: int) -> Image.Image:
    image = canvas(64)
    fade = min(255, 145 + frame * 15)
    radius = 8 + frame * 3
    draw_broken_ring(image, 31.5, 42.5, radius, 2.2, frame, 1.9, fade)
    draw = ImageDraw.Draw(image)
    # Branching molten target cracks.
    for branch in range(8):
        angle = branch * math.tau / 8 + frame * 0.07
        inner = 3 + (branch + frame) % 3
        outer = 12 + frame * 2 + (branch * 3) % 7
        mid = (round(31.5 + math.cos(angle) * (inner + outer) * 0.45),
               round(42.5 + math.sin(angle) * (inner + outer) * 0.45 / 1.9))
        start = (round(31.5 + math.cos(angle) * inner), round(42.5 + math.sin(angle) * inner / 1.9))
        end = (round(31.5 + math.cos(angle + 0.09) * outer), round(42.5 + math.sin(angle + 0.09) * outer / 1.9))
        draw.line((start, mid, end), fill=alpha(CRIMSON, fade), width=2)
        draw.line((mid, end), fill=alpha(GOLD, fade), width=1)
    # Increasing downward streak, not a soft glow.
    streak_len = 7 + frame * 4
    draw.line((38, max(2, 36 - streak_len), 34, 37), fill=alpha(CRIMSON, fade), width=5)
    draw.line((38, max(3, 36 - streak_len), 34, 37), fill=alpha(ORANGE, fade), width=3)
    draw.line((37, max(4, 36 - streak_len), 34, 36), fill=alpha(IVORY, fade), width=1)
    for index in range(8):
        x = 5 + ((index * 17 + frame * 4) % 54)
        y = 8 + ((index * 11 + frame * 5) % 43)
        draw.rectangle((x, y, x + 1, y + 1), fill=alpha((COAL, ORANGE, GOLD)[index % 3], fade - 25))
    return image


def make_meteor_impact(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    cx, ground = 32, 46
    if frame < 4:
        height = 15 + frame * 10
        draw.line((cx + 7, max(1, ground - height - 8), cx, ground), fill=CRIMSON, width=9)
        draw.line((cx + 6, max(2, ground - height - 7), cx, ground), fill=ORANGE, width=5)
        draw.line((cx + 5, max(3, ground - height - 6), cx, ground), fill=WHITE_HOT, width=2)
    else:
        fade = max(75, 255 - (frame - 4) * 24)
        column_half = max(1, 8 - (frame - 4))
        poly(draw, [(cx - column_half, ground), (cx - 3, 17 + (frame - 4) * 2),
                    (cx, 5 + (frame - 4) * 3), (cx + 4, 18 + (frame - 4) * 2),
                    (cx + column_half, ground)], alpha(CRIMSON, fade))
        poly(draw, [(cx - max(1, column_half - 3), ground), (cx - 1, 20),
                    (cx + 1, 10 + (frame - 4) * 3), (cx + max(1, column_half - 3), ground)], alpha(ORANGE, fade))
        draw.line((cx, 16 + (frame - 4) * 2, cx, ground), fill=alpha(WHITE_HOT, fade), width=2)
    radius = min(28, 5 + frame * 2.5)
    draw_broken_ring(image, cx - 0.5, ground, radius, 2.5 if frame < 7 else 1.4,
                     frame, 2.25, max(75, 255 - frame * 13))
    draw = ImageDraw.Draw(image)
    for index in range(18):
        angle = math.pi + index * math.pi / 17
        distance = 6 + frame * 2 + (index * 7) % 13
        x = round(cx + math.cos(angle) * distance)
        y = round(ground + math.sin(angle) * distance * 0.72)
        size = 1 + ((index + frame) % 3)
        color = (SOOT, COAL, ROCK, ORANGE)[(index + frame) % 4]
        poly(draw, [(x - size, y), (x, y - size), (x + size, y), (x, y + size)], alpha(color, max(90, 240 - frame * 11)))
        if color in (COAL, ROCK):
            draw.point((x, y - 1), fill=alpha(ROCK_LIGHT, max(90, 220 - frame * 11)))
    return image


def make_inferno_wave(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    open_amount = (1, 3, 5, 4, 2, 4)[frame]
    upper_shift = -open_amount // 2
    lower_shift = open_amount // 2

    # Two independent clinker jaws give the Cinder Maw a readable open throat.
    upper = [(2, 35 + upper_shift), (6, 26 + upper_shift), (13, 23 + upper_shift),
             (13, 16 + upper_shift), (20, 20 + upper_shift), (25, 9 + upper_shift),
             (31, 17 + upper_shift), (38, 6 + upper_shift), (42, 19 + upper_shift),
             (51, 12 + upper_shift), (50, 23 + upper_shift), (61, 20 + upper_shift),
             (59, 29 + upper_shift), (48, 31 + upper_shift), (35, 30 + upper_shift),
             (21, 35 + upper_shift)]
    lower = [(5, 41 + lower_shift), (16, 37 + lower_shift), (28, 40 + lower_shift),
             (39, 36 + lower_shift), (51, 39 + lower_shift), (61, 35 + lower_shift),
             (59, 47 + lower_shift), (52, 53 + lower_shift), (14, 54 + lower_shift)]
    poly(draw, upper, SOOT)
    poly(draw, lower, SOOT)
    poly(draw, [(5, 34 + upper_shift), (10, 28 + upper_shift), (18, 26 + upper_shift),
                (17, 20 + upper_shift), (24, 23 + upper_shift), (27, 14 + upper_shift),
                (33, 21 + upper_shift), (38, 12 + upper_shift), (40, 23 + upper_shift),
                (48, 17 + upper_shift), (47, 27 + upper_shift), (57, 24 + upper_shift),
                (55, 28 + upper_shift), (43, 29 + upper_shift), (31, 28 + upper_shift),
                (19, 33 + upper_shift)], COAL)
    poly(draw, [(9, 43 + lower_shift), (18, 40 + lower_shift), (29, 43 + lower_shift),
                (40, 39 + lower_shift), (50, 42 + lower_shift), (56, 39 + lower_shift),
                (56, 46 + lower_shift), (49, 50 + lower_shift), (16, 51 + lower_shift)], COAL)

    # Overlapping clinker scales on both jaws, with frame-to-frame spalling.
    for jaw_index, (base_y, count) in enumerate(((27 + upper_shift, 13), (45 + lower_shift, 11))):
        for index in range(count):
            x = 5 + index * 4 + jaw_index * 2
            if (index + frame * 2 + jaw_index) % 11 == 0:
                continue
            y = base_y + ((index * 5 + frame * 3 + jaw_index) % 7) - 3
            w = 3 + (index + frame) % 3
            poly(draw, [(x, y), (x + 2, y - 3), (x + w, y - 2),
                        (x + w + 1, y + 1), (x + 1, y + 2)],
                 (CHAR, COAL, ROCK)[(index + frame + jaw_index) % 3])
            draw.line((x + 1, y - 1, x + w - 1, y - 2), fill=ROCK_LIGHT, width=1)

    # Molten throat seam is separated from the armor so it cannot read as a low campfire.
    throat = [(7, 39), (16, 35), (25, 38), (34, 33), (44, 37), (53, 32), (60, 34)]
    throat = [(x, y + (lower_shift if index < 2 else 0)) for index, (x, y) in enumerate(throat)]
    draw.line(throat, fill=OXBLOOD, width=8)
    draw.line(throat[1:-1], fill=RED, width=6)
    draw.line(throat[1:-1], fill=ORANGE, width=4)
    draw.line(throat[2:-2], fill=PALE_GOLD, width=2)
    draw.point((34, 34), fill=WHITE_HOT)

    # Opposed clinker teeth frame the open throat.
    for index, x in enumerate(range(12, 59, 7)):
        upper_y = 31 + upper_shift + ((index + frame) % 2)
        lower_y = 42 + lower_shift - ((index + frame) % 2)
        tooth_color = IVORY if (index + frame) % 5 == 0 else PALE_GOLD
        poly(draw, [(x - 2, upper_y), (x + 2, upper_y), (x, upper_y + 5)], tooth_color)
        if index % 2 == 0:
            poly(draw, [(x - 1, lower_y), (x + 2, lower_y), (x, lower_y - 4)], GOLD)

    draw.rectangle((47, 23 + upper_shift, 50, 26 + upper_shift), fill=OXBLOOD)
    draw.point((49, 24 + upper_shift), fill=WHITE_HOT)
    # Detached clinker chips visibly spall farther as the maw opens.
    spall_count = 4 + open_amount * 2
    for index in range(spall_count):
        x = 3 + ((index * 13 + frame * 7) % 59)
        y = 3 + ((index * 9 + frame * 5) % 49)
        size = 1 + ((index + frame) % 3 == 0)
        color = (COAL, ROCK, RED, GOLD)[(index + frame) % 4]
        draw.rectangle((x, y, min(63, x + size), y + (index & 1)), fill=alpha(color, 165))
        if color == ROCK and index % 4 == 0:
            draw.point((x, y), fill=IVORY)
    return image


def make_inferno_flame_frame(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    sway = (-1, 1, 0, -1)[frame]
    poly(draw, [(1, 15), (2, 10), (5, 8), (4 + sway, 4), (7, 6),
                (8 + sway, 0), (11, 6), (14, 4 + (frame & 1)), (13, 10),
                (15, 12), (14, 15)], SOOT)
    poly(draw, [(3, 15), (4, 10), (7, 8), (6 + sway, 4), (9, 8),
                (12, 6), (11, 11), (14, 12), (13, 15)], CRIMSON)
    poly(draw, [(5, 15), (6, 11), (9, 7), (10, 12), (12, 10), (11, 15)], ORANGE)
    draw.rectangle((8, 11, 9, 14), fill=PALE_GOLD)
    draw.point((9, 13), fill=WHITE_HOT)
    draw.point((2 + frame * 3, 3 + (frame * 5) % 9), fill=alpha(GOLD, 170))
    return image


def make_inferno_flame_sheet() -> Image.Image:
    image = canvas((16, 64))
    for frame in range(4):
        image.alpha_composite(make_inferno_flame_frame(frame), (0, frame * 16))
    return image


def make_pyre_coals(frame: int) -> Image.Image:
    image = Image.new("RGBA", (16, 16), CHAR)
    draw = ImageDraw.Draw(image)
    # Tileable 4x4 material clusters, with four distinct seam networks.
    for cy in range(0, 16, 4):
        for cx in range(0, 16, 4):
            noise = pixel_hash(cx // 4, cy // 4, frame)
            color = (CHAR, COAL, ROCK, SOOT)[noise % 4]
            draw.rectangle((cx, cy, cx + 3, cy + 3), fill=color)
            if noise > 150:
                draw.line((cx, cy, cx + 2, cy), fill=ROCK_LIGHT, width=1)
    seams = (
        [[(0, 5), (5, 6), (7, 4), (11, 6), (15, 5)], [(5, 15), (6, 11), (10, 9), (12, 5)]],
        [[(0, 12), (4, 10), (7, 12), (10, 8), (15, 9)], [(3, 0), (5, 4), (4, 8), (8, 11)]],
        [[(0, 3), (4, 4), (8, 2), (11, 5), (15, 4)], [(13, 15), (11, 11), (7, 10), (5, 6)]],
        [[(0, 8), (4, 7), (7, 9), (11, 7), (15, 10)], [(9, 0), (8, 4), (11, 7), (10, 13)]],
    )[frame]
    for path in seams:
        draw.line(path, fill=OXBLOOD, width=3)
        draw.line(path[1:-1], fill=ORANGE, width=1)
        draw.point(path[len(path) // 2], fill=PALE_GOLD)
    return image


def make_meteor_core() -> Image.Image:
    image = Image.new("RGBA", (16, 16), SOOT)
    draw = ImageDraw.Draw(image)
    for cy in range(1, 15, 3):
        for cx in range(1, 15, 3):
            color = (CHAR, COAL, ROCK, ROCK_LIGHT)[pixel_hash(cx, cy, 8) % 4]
            draw.rectangle((cx, cy, min(14, cx + 2), min(14, cy + 2)), fill=color)
    for crack in ([(1, 4), (6, 5), (7, 8), (12, 9), (15, 13)],
                  [(11, 0), (10, 4), (7, 6), (5, 11), (1, 13)]):
        draw.line(crack, fill=OXBLOOD, width=3)
        draw.line(crack[1:-1], fill=GOLD, width=1)
    draw.rectangle((7, 6, 8, 8), fill=WHITE_HOT)
    return image


def make_fire_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Compact furnace-drake crest with clustered armor and a white-hot jaw.
    poly(draw, [(2, 22), (6, 14), (11, 15), (12, 8), (17, 13), (21, 5),
                (23, 14), (29, 11), (27, 22), (22, 27), (7, 27)], SOOT)
    poly(draw, [(5, 22), (9, 17), (14, 18), (14, 12), (18, 16), (21, 10),
                (21, 19), (26, 15), (24, 22), (20, 24), (8, 24)], CRIMSON)
    for x, y in ((7, 18), (12, 15), (17, 16), (22, 13)):
        draw.rectangle((x, y, x + 3, y + 2), fill=COAL)
        draw.line((x + 1, y, x + 3, y), fill=ROCK_LIGHT, width=1)
    draw.line((7, 23, 23, 22), fill=ORANGE, width=4)
    draw.line((10, 22, 21, 21), fill=IVORY, width=1)
    draw.point((22, 17), fill=WHITE_HOT)
    return image


def make_fire_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for cy in range(17, 28, 4):
        for cx in range(4, 29, 5):
            color = (CHAR, COAL, ROCK)[pixel_hash(cx, cy, 5) % 3]
            draw.rectangle((cx, cy, cx + 4, cy + 3), fill=color)
    crack = [(3, 23), (8, 21), (12, 23), (16, 18), (20, 21), (28, 17)]
    draw.line(crack, fill=OXBLOOD, width=5)
    draw.line(crack[1:-1], fill=ORANGE, width=3)
    draw.line(crack[2:-2], fill=IVORY, width=1)
    for x, top in ((8, 8), (15, 4), (23, 10)):
        poly(draw, [(x - 3, 19), (x - 2, 13), (x, top), (x + 2, 14), (x + 3, 19)], CRIMSON)
        poly(draw, [(x - 1, 18), (x, top + 5), (x + 1, 18)], GOLD)
    return image


def make_fire_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    draw.line((3, 2, 13, 13), fill=CRIMSON, width=8)
    draw.line((4, 3, 14, 14), fill=ORANGE, width=4)
    draw.line((5, 3, 13, 12), fill=WHITE_HOT, width=1)
    poly(draw, [(10, 14), (16, 9), (25, 11), (30, 18), (27, 27),
                (20, 31), (11, 27), (7, 20)], SOOT)
    for box, color in (((12, 15, 18, 20), ROCK), ((19, 12, 25, 18), COAL),
                       ((18, 21, 27, 27), CHAR), ((10, 21, 17, 27), ROCK_LIGHT)):
        draw.rectangle(box, fill=color)
    draw.line((11, 18, 18, 19, 21, 26), fill=OXBLOOD, width=4)
    draw.line((12, 18, 18, 19, 21, 25), fill=GOLD, width=1)
    draw.point((18, 19), fill=WHITE_HOT)
    return image


FIRE_FAMILIES = {
    "ember": (4, 16, make_ember),
    "ash": (4, 16, make_ash),
    "flame_ribbon": (6, 32, make_flame_ribbon),
    "impact_ring": (6, 32, make_impact_ring),
    "meteor": (8, 64, make_meteor),
    "pyre_fissure": (6, 32, make_pyre_fissure),
    "pyre_front": (8, 64, make_pyre_front),
    "meteor_warning": (8, 64, make_meteor_warning),
    "meteor_impact": (12, 64, make_meteor_impact),
}


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for family, (count, _size, maker) in FIRE_FAMILIES.items():
        for frame in range(count):
            generated[TEXTURES / f"particle/fire/{family}_{frame}.png"] = maker(frame)
    for frame in range(6):
        generated[TEXTURES / f"entity/inferno_wave_{frame}.png"] = make_inferno_wave(frame)
    generated[TEXTURES / "block/inferno_flame.png"] = make_inferno_flame_sheet()
    for frame in range(4):
        generated[TEXTURES / f"block/pyre_coals_{frame}.png"] = make_pyre_coals(frame)
    generated.update({
        TEXTURES / "block/meteor_core.png": make_meteor_core(),
        TEXTURES / "gui/ability/fire_primary.png": make_fire_primary_icon(),
        TEXTURES / "gui/ability/fire_secondary.png": make_fire_secondary_icon(),
        TEXTURES / "gui/ability/fire_ultimate.png": make_fire_ultimate_icon(),
    })
    return generated


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    for family, (count, size, _maker) in FIRE_FAMILIES.items():
        frames = [outputs[TEXTURES / f"particle/fire/{family}_{frame}.png"] for frame in range(count)]
        assert all(image.size == (size, size) for image in frames), f"{family}: wrong frame dimensions"
        assert len({image.tobytes() for image in frames}) == count, f"{family}: duplicate frames"
    waves = [outputs[TEXTURES / f"entity/inferno_wave_{frame}.png"] for frame in range(6)]
    assert len({image.tobytes() for image in waves}) == 6, "inferno_wave: duplicate frames"
    sheet = outputs[TEXTURES / "block/inferno_flame.png"]
    cells = [sheet.crop((0, index * 16, 16, (index + 1) * 16)).tobytes() for index in range(4)]
    assert len(set(cells)) == 4, "inferno_flame: duplicate animation cells"


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: differs from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        visible = {rgba for _count, rgba in colors if rgba[3] > 0}
        minimum = 6 if max(expected.size) >= 32 else 4
        assert len(visible) >= minimum, f"{path}: only {len(visible)} visible colors"
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
        bbox = reopened.getbbox()
    return len(visible), bbox, digest


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb, label_h, columns = 104, 25, 8
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (30, 22, 22, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        x = (index % columns) * thumb
        y = (index // columns) * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (94, 84, 80, 255))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 13):
            for cx in range(0, thumb, 13):
                if (cx // 13 + cy // 13) % 2:
                    cd.rectangle((cx, cy, cx + 12, cy + 12), fill=(48, 40, 40, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), relative.stem[:19], fill=(255, 232, 190, 255), font=font)
        draw.text((x + 3, y + thumb + 13), str(relative.parent)[-15:], fill=(197, 143, 105, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="replace only this script's known outputs and remove its known legacy files")
    args = parser.parse_args()
    outputs = output_map()
    validate_frame_families(outputs)
    legacy = (TEXTURES / "entity/inferno_wave.png", TEXTURES / "block/pyre_coals.png")
    if any(path.exists() for path in legacy) and not args.replace:
        names = ", ".join(str(path.relative_to(ROOT)) for path in legacy if path.exists())
        raise SystemExit(f"Refusing to remove legacy Fire textures without --replace: {names}")

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        differs = True
        if path.exists():
            with Image.open(path) as existing:
                existing.load()
                differs = existing.mode != "RGBA" or existing.size != image.size or existing.tobytes() != image.tobytes()
        if differs:
            if path.exists() and not args.replace:
                raise SystemExit(f"Refusing to overwrite differing Fire texture: {path.relative_to(ROOT)}")
            image.save(path, format="PNG", optimize=False, compress_level=9)

    if args.replace:
        for path in legacy:
            if path.exists():
                path.unlink()

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_fire_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"FIRE_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
