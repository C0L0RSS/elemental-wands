#!/usr/bin/env python3
"""Generate the Fire vertical-slice pixel-art texture set.

The artwork is drawn directly on its final pixel grid.  There is deliberately
no antialiasing, scaling, random state, or dependency on an external source
image, which keeps the output stable and Minecraft-native.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/elementalwands/textures"

T = (0, 0, 0, 0)
INK = (24, 13, 16, 255)
COAL = (43, 29, 29, 255)
ASH_DARK = (63, 52, 51, 230)
ASH = (111, 94, 86, 210)
ASH_LIGHT = (173, 150, 128, 185)
OXBLOOD = (78, 14, 18, 255)
CRIMSON = (137, 25, 22, 255)
RED = (205, 49, 25, 255)
ORANGE = (240, 99, 21, 255)
GOLD = (248, 174, 43, 255)
PALE_GOLD = (255, 218, 97, 255)
IVORY = (255, 243, 183, 255)
BLUE_INK = (17, 31, 57, 255)
ARCANE_DARK = (32, 80, 137, 230)
ARCANE = (78, 166, 221, 235)
ARCANE_PALE = (174, 231, 244, 245)
ARCANE_WHITE = (238, 255, 245, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(int(x), int(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 37 + y * 61 + seed * 101 + x * y * 7) & 255


def bezier(points, samples: int = 32):
    p0, p1, p2, p3 = points
    result = []
    for index in range(samples + 1):
        t = index / samples
        u = 1.0 - t
        result.append((
            round(u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]),
            round(u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]),
        ))
    compact = []
    for point in result:
        if not compact or point != compact[-1]:
            compact.append(point)
    return compact


def draw_spark(draw: ImageDraw.ImageDraw, x: int, y: int, color=GOLD, scale: int = 1) -> None:
    draw.rectangle((x, y - 2 * scale, x + scale - 1, y + 2 * scale), fill=color)
    draw.rectangle((x - 2 * scale, y, x + 2 * scale, y + scale - 1), fill=color)
    draw.rectangle((x, y, x + scale - 1, y + scale - 1), fill=IVORY if color != ARCANE else ARCANE_WHITE)


def draw_flame(draw: ImageDraw.ImageDraw, points, inner=True) -> None:
    poly(draw, points, INK)
    inset = [(x, y + 1) for x, y in points[1:-1]]
    if len(inset) >= 3:
        poly(draw, inset, CRIMSON)
    if inner:
        xs = [x for x, _ in points]
        ys = [y for _, y in points]
        cx = round(sum(xs) / len(xs))
        bottom = max(ys) - 2
        top = min(ys) + max(3, (max(ys) - min(ys)) // 2)
        poly(draw, [(cx - 2, bottom), (cx - 2, top + 2), (cx, top), (cx + 2, top + 3), (cx + 2, bottom)], ORANGE)
        draw.rectangle((cx - 1, bottom - 3, cx + 1, bottom), fill=GOLD)


def make_ember(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    # A tumbling coal with a short incandescent wake.
    x = 7 + (frame % 2)
    y = 8 - frame
    tails = [
        [(x - 1, y + 3), (x - 1, y + 5)],
        [(x + 1, y + 3), (x + 2, y + 5)],
        [(x - 2, y + 2), (x - 3, y + 4)],
        [(x + 2, y + 2), (x + 3, y + 4)],
    ]
    tail = tails[frame]
    draw.line(tail, fill=CRIMSON, width=1)
    draw.point(tail[-1], fill=(RED[0], RED[1], RED[2], 150))
    poly(draw, [(x, y - 3), (x + 2, y - 1), (x + 2, y + 2), (x, y + 3), (x - 2, y + 1), (x - 2, y - 1)], INK)
    draw.rectangle((x - 1, y - 2, x + 1, y + 1), fill=RED)
    draw.rectangle((x, y - 1, x + 1, y), fill=GOLD)
    draw.point((x, y - 1), fill=IVORY)
    draw.point((2 + frame * 3, 12 - frame * 2), fill=(GOLD[0], GOLD[1], GOLD[2], 185))
    return image


def make_flame_ribbon(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame / 6.0 * math.tau
    p0 = (5, 23 - (frame % 2))
    p1 = (8 + round(math.sin(phase) * 4), 8)
    p2 = (23 + round(math.cos(phase) * 3), 25)
    p3 = (27, 8 + (frame % 3))
    path = bezier((p0, p1, p2, p3), 38)
    draw.line(path, fill=INK, width=7, joint="curve")
    draw.line(path, fill=CRIMSON, width=5, joint="curve")
    draw.line(path, fill=ORANGE, width=3, joint="curve")
    draw.line(path, fill=PALE_GOLD, width=1)
    # Break the ribbon into a living, tapered end and add detached cinders.
    ex, ey = path[-1]
    draw.rectangle((ex - 2, ey - 2, ex + 2, ey + 2), fill=T)
    draw.line(path[-5:-2], fill=GOLD, width=2)
    draw.point(path[-3], fill=IVORY)
    motes = [
        (4 + frame * 3, 6 + (frame % 2) * 3),
        (25 - frame * 2, 27 - (frame % 3)),
        (13 + (frame % 3) * 5, 3 + frame // 2),
    ]
    for index, (x, y) in enumerate(motes):
        draw.rectangle((x, y, x + (index == 0), y + (index == 1)), fill=(GOLD[0], GOLD[1], GOLD[2], 180))
    return image


def make_ash(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    flakes = [
        (3 + frame, 4 + frame, 2, ASH),
        (10 - frame, 3 + frame // 2, 1, ASH_LIGHT),
        (5 + frame // 2, 11 - frame, 1, ASH_DARK),
        (12 - frame // 2, 10 + (frame % 2), 2, ASH),
    ]
    for index, (x, y, size, color) in enumerate(flakes):
        if (frame + index) % 2:
            poly(draw, [(x, y), (x + size, y + 1), (x + size - 1, y + size), (x - 1, y + size - 1)], color)
        else:
            draw.rectangle((x, y, x + size, y + max(0, size - 1)), fill=color)
        if size > 1:
            draw.point((x, y), fill=ASH_LIGHT)
    return image


def make_impact_ring(frame: int) -> Image.Image:
    image = canvas(32)
    pixels = image.load()
    cx = cy = 15.5
    radius = 4.5 + frame * 2.0
    thickness = 2.2 if frame < 3 else 1.7
    for y in range(32):
        for x in range(32):
            dx = x - cx
            dy = (y - cy) * 1.12
            dist = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 24)
            gap = ((segment + frame * 3) % 11 == 0) or (frame > 3 and (segment + frame) % 7 == 0)
            jitter = ((pixel_hash(x, y, frame) % 3) - 1) * 0.18
            delta = abs(dist - radius - jitter)
            if not gap and delta <= thickness:
                if delta < 0.55 and frame < 4:
                    pixels[x, y] = IVORY
                elif delta < 1.2:
                    pixels[x, y] = PALE_GOLD if frame < 5 else ORANGE
                else:
                    pixels[x, y] = RED if frame < 4 else (CRIMSON[0], CRIMSON[1], CRIMSON[2], 190)
    draw = ImageDraw.Draw(image)
    if frame < 4:
        for offset in (-1, 1):
            x = 16 + offset * (radius + 2)
            y = 15 + offset * ((frame % 2) * 2 - 1)
            if 1 <= x < 30:
                draw.rectangle((int(x), int(y), int(x) + 1, int(y) + 1), fill=GOLD)
    return image


def make_meteor(frame: int) -> Image.Image:
    image = canvas(64)
    pixels = image.load()
    cx = 31.5 + ((frame % 3) - 1)
    cy = 33.5
    radius = 18.5 + (1 if frame in (2, 5) else 0)

    # Angular corona tongues rotate around the rocky core.
    draw = ImageDraw.Draw(image)
    for tongue in range(8):
        angle = (tongue / 8.0) * math.tau + frame * math.tau / 32.0
        inner = radius - 2
        outer = radius + 5 + ((tongue + frame) % 3) * 2
        spread = 0.12 + (tongue % 2) * 0.025
        p1 = (cx + math.cos(angle - spread) * inner, cy + math.sin(angle - spread) * inner)
        p2 = (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer)
        p3 = (cx + math.cos(angle + spread) * inner, cy + math.sin(angle + spread) * inner)
        poly(draw, [p1, p2, p3], CRIMSON)
        mid = (cx + math.cos(angle) * (outer - 3), cy + math.sin(angle) * (outer - 3))
        poly(draw, [
            (cx + math.cos(angle - spread * 0.55) * inner, cy + math.sin(angle - spread * 0.55) * inner),
            mid,
            (cx + math.cos(angle + spread * 0.55) * inner, cy + math.sin(angle + spread * 0.55) * inner),
        ], ORANGE)

    for y in range(8, 58):
        for x in range(7, 58):
            dx = x - cx
            dy = y - cy
            dist = math.sqrt(dx * dx + dy * dy)
            ragged = ((pixel_hash(x // 2, y // 2, frame) % 5) - 2) * 0.35
            if dist <= radius + ragged:
                noise = pixel_hash(x // 3, y // 3, frame * 2)
                if dist > radius - 2.0:
                    color = INK
                elif noise < 48:
                    color = OXBLOOD
                elif noise < 125:
                    color = COAL
                elif noise < 190:
                    color = (77, 45, 34, 255)
                else:
                    color = CRIMSON
                pixels[x, y] = color

    draw = ImageDraw.Draw(image)
    cracks = [
        [(25, 18), (28, 23), (27, 28), (31, 31), (29, 38), (33, 44)],
        [(44, 25), (38, 27), (36, 33), (31, 34), (26, 39), (22, 45)],
        [(18, 31), (23, 32), (26, 35), (30, 34), (36, 38), (44, 39)],
    ]
    for index, crack in enumerate(cracks):
        shifted = [(x + ((frame + index) % 3) - 1, y) for x, y in crack]
        draw.line(shifted, fill=RED, width=3)
        draw.line(shifted[1:-1], fill=GOLD, width=1)
        if (frame + index) % 2 == 0:
            mx, my = shifted[len(shifted) // 2]
            draw.point((mx, my), fill=IVORY)

    # A tiny asymmetric wake makes animation direction readable.
    tail_x = 13 + frame % 4
    draw.line([(tail_x, 14), (9, 9), (12, 4)], fill=CRIMSON, width=3)
    draw.line([(tail_x + 2, 14), (12, 9)], fill=GOLD, width=1)
    return image


def make_arcane_mote(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    size = (2, 3, 4, 3)[frame]
    cx = 7 + (frame == 2)
    cy = 8 - (frame == 1)
    poly(draw, [(cx, cy - size), (cx + size, cy), (cx, cy + size), (cx - size, cy)], BLUE_INK)
    inner = max(1, size - 1)
    poly(draw, [(cx, cy - inner), (cx + inner, cy), (cx, cy + inner), (cx - inner, cy)], ARCANE)
    draw.rectangle((cx, cy - 1, cx, cy + 1), fill=ARCANE_WHITE)
    if frame in (1, 2):
        draw.point((2 + frame, 11), fill=(ARCANE_PALE[0], ARCANE_PALE[1], ARCANE_PALE[2], 170))
        draw.point((12, 3 + frame), fill=(ARCANE[0], ARCANE[1], ARCANE[2], 160))
    return image


def make_arcane_thread(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame / 6.0 * math.tau
    points = []
    for y in range(3, 29, 2):
        x = 16 + round(math.sin(y * 0.38 + phase) * 6) + ((y // 4 + frame) % 2)
        points.append((x, y))
    draw.line(points, fill=BLUE_INK, width=5)
    draw.line(points, fill=ARCANE_DARK, width=3)
    draw.line(points, fill=ARCANE_PALE, width=1)
    for index in (2, 6, 10):
        if index < len(points):
            x, y = points[index]
            direction = -1 if (index + frame) % 2 else 1
            branch = [(x, y), (x + direction * 3, y + 2), (x + direction * 5, y + 1)]
            draw.line(branch, fill=ARCANE, width=1)
    draw.point(points[(frame + 3) % len(points)], fill=ARCANE_WHITE)
    return image


def make_inferno_flame() -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    poly(draw, [(2, 15), (2, 10), (4, 7), (3, 4), (6, 6), (7, 1), (10, 5), (13, 3), (12, 9), (15, 11), (14, 15)], INK)
    poly(draw, [(3, 15), (4, 10), (6, 8), (6, 4), (9, 7), (12, 5), (11, 11), (14, 12), (13, 15)], CRIMSON)
    poly(draw, [(5, 15), (5, 11), (8, 8), (9, 5), (11, 9), (10, 12), (12, 15)], ORANGE)
    poly(draw, [(7, 15), (7, 12), (9, 9), (10, 13), (9, 15)], PALE_GOLD)
    draw.point((8, 13), fill=IVORY)
    draw.point((2, 7), fill=(GOLD[0], GOLD[1], GOLD[2], 180))
    draw.point((13, 2), fill=(RED[0], RED[1], RED[2], 175))
    return image


def make_pyre_coals() -> Image.Image:
    image = Image.new("RGBA", (16, 16), COAL)
    draw = ImageDraw.Draw(image)
    # Irregular coal chunks and ember seams, designed as a seamless tile.
    chunks = [
        ((0, 0, 6, 5), (51, 38, 35, 255)),
        ((7, 0, 12, 4), (72, 44, 34, 255)),
        ((13, 0, 15, 6), (45, 32, 31, 255)),
        ((1, 7, 5, 13), (69, 43, 35, 255)),
        ((7, 6, 13, 11), (49, 35, 34, 255)),
        ((9, 12, 15, 15), (76, 42, 31, 255)),
        ((0, 14, 7, 15), (47, 34, 33, 255)),
    ]
    for box, color in chunks:
        draw.rectangle(box, fill=color)
    seams = [[(0, 6), (4, 6), (6, 4), (9, 5), (11, 3)], [(5, 15), (6, 11), (9, 10), (11, 7), (15, 8)]]
    for seam in seams:
        draw.line(seam, fill=OXBLOOD, width=2)
        draw.line(seam[1:-1], fill=ORANGE, width=1)
    draw.point((8, 5), fill=PALE_GOLD)
    draw.point((10, 9), fill=GOLD)
    draw.point((6, 12), fill=RED)
    return image


def make_meteor_core() -> Image.Image:
    image = Image.new("RGBA", (16, 16), INK)
    draw = ImageDraw.Draw(image)
    rock = (70, 42, 34, 255)
    draw.rectangle((1, 1, 14, 14), fill=rock)
    draw.rectangle((0, 3, 3, 11), fill=(55, 37, 33, 255))
    draw.rectangle((12, 2, 15, 6), fill=(91, 45, 31, 255))
    draw.rectangle((3, 12, 13, 15), fill=(48, 34, 32, 255))
    cracks = [[(2, 4), (6, 5), (7, 8), (11, 9), (14, 13)], [(11, 0), (10, 4), (7, 6), (5, 10), (1, 12)]]
    for crack in cracks:
        draw.line(crack, fill=CRIMSON, width=3)
        draw.line(crack[1:-1], fill=GOLD, width=1)
    draw.rectangle((7, 6, 8, 8), fill=IVORY)
    draw.point((11, 9), fill=PALE_GOLD)
    draw.point((5, 10), fill=ORANGE)
    return image


def make_inferno_wave() -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    # Broad impact crescent with five distinct flame crests.
    outline = [(3, 45), (8, 36), (15, 31), (13, 23), (21, 27), (24, 14), (31, 25),
               (38, 10), (41, 26), (51, 18), (50, 31), (60, 27), (57, 39), (62, 46),
               (53, 52), (11, 52)]
    poly(draw, outline, INK)
    poly(draw, [(6, 45), (11, 38), (19, 34), (18, 28), (25, 32), (26, 20), (32, 30),
                (38, 17), (39, 32), (48, 25), (46, 36), (56, 32), (53, 42), (58, 46),
                (50, 49), (12, 49)], CRIMSON)
    poly(draw, [(10, 44), (17, 39), (25, 38), (27, 29), (32, 37), (38, 25), (39, 39),
                (48, 32), (47, 41), (54, 44), (49, 47), (15, 47)], ORANGE)
    poly(draw, [(16, 44), (24, 41), (29, 34), (32, 42), (38, 32), (41, 42), (48, 38),
                (47, 45), (20, 45)], GOLD)
    draw.line([(8, 52), (51, 52), (58, 48)], fill=(RED[0], RED[1], RED[2], 200), width=3)
    draw.line([(15, 48), (47, 48)], fill=IVORY, width=1)
    for x, y in ((9, 27), (17, 18), (32, 12), (53, 15), (59, 34), (5, 51)):
        draw.rectangle((x, y, x + 1, y + 2), fill=(GOLD[0], GOLD[1], GOLD[2], 190))
    return image


def make_wizard_wand() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # A neutral diagonal master wand. Affinity is communicated by the spell
    # effects and HUD, so the item itself carries all five elements as tiny
    # restrained inlays around a pale fractured arcane crystal.
    shaft = [(5, 27), (7, 29), (23, 12), (20, 9)]
    poly(draw, shaft, INK)
    poly(draw, [(7, 26), (8, 27), (22, 12), (21, 11)], (91, 52, 34, 255))
    draw.line([(8, 24), (19, 13)], fill=(137, 77, 39, 255), width=1)
    # Pommel and blackened-silver bindings.
    poly(draw, [(3, 27), (6, 25), (9, 28), (7, 31), (4, 30)], INK)
    draw.rectangle((5, 27, 7, 29), fill=(143, 151, 162, 255))
    draw.point((6, 28), fill=ARCANE_WHITE)
    draw.line([(10, 22), (13, 25)], fill=INK, width=3)
    draw.line([(10, 22), (12, 24)], fill=(152, 159, 168, 255), width=1)
    draw.line([(17, 15), (20, 18)], fill=INK, width=3)
    draw.line([(17, 15), (19, 17)], fill=(152, 159, 168, 255), width=1)

    # Five one-pixel affinity inlays: Fire, Wind, Stone, Nature, Space.
    for x, y, color in (
        (9, 24, RED),
        (12, 21, ARCANE_PALE),
        (15, 18, (190, 151, 83, 255)),
        (18, 15, (86, 177, 85, 255)),
        (20, 13, (153, 101, 214, 255)),
    ):
        draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=INK)
        draw.point((x, y), fill=color)

    # Forked blackened-silver crown and fractured ivory arcane crystal.
    poly(draw, [(18, 11), (18, 6), (21, 8), (22, 3), (25, 6), (29, 5), (27, 12), (24, 15)], INK)
    poly(draw, [(20, 11), (20, 8), (22, 9), (23, 5), (25, 8), (27, 7), (25, 13), (23, 14)],
         (104, 112, 124, 255))
    poly(draw, [(22, 10), (23, 6), (24, 4), (26, 7), (25, 11), (24, 13)], ARCANE_PALE)
    draw.rectangle((23, 7, 24, 10), fill=ARCANE_WHITE)
    draw.point((27, 3), fill=(ARCANE_PALE[0], ARCANE_PALE[1], ARCANE_PALE[2], 190))
    return image


def draw_slot(image: Image.Image, x: int, y: int, ready: bool, kind: int) -> None:
    draw = ImageDraw.Draw(image)
    frame = (104, 84, 72, 255) if not ready else GOLD
    light = (156, 138, 121, 255) if not ready else IVORY
    dark = (34, 30, 31, 255) if not ready else OXBLOOD
    fill = (20, 18, 20, 205)
    # 36x36 bevel with corner cuts.
    poly(draw, [(x + 4, y), (x + 31, y), (x + 35, y + 4), (x + 35, y + 31),
                (x + 31, y + 35), (x + 4, y + 35), (x, y + 31), (x, y + 4)], dark)
    poly(draw, [(x + 5, y + 2), (x + 30, y + 2), (x + 33, y + 5), (x + 33, y + 30),
                (x + 30, y + 33), (x + 5, y + 33), (x + 2, y + 30), (x + 2, y + 5)], frame)
    draw.rectangle((x + 5, y + 5, x + 30, y + 30), fill=fill)
    draw.line([(x + 5, y + 5), (x + 30, y + 5)], fill=light, width=1)
    draw.line([(x + 5, y + 5), (x + 5, y + 30)], fill=light, width=1)
    draw.line([(x + 5, y + 30), (x + 30, y + 30)], fill=dark, width=2)
    draw.line([(x + 30, y + 5), (x + 30, y + 30)], fill=dark, width=2)
    # A different notch marks primary, secondary, and ultimate at a glance.
    if kind == 0:
        draw.rectangle((x + 15, y + 1, x + 20, y + 3), fill=light)
    elif kind == 1:
        draw.rectangle((x + 9, y + 1, x + 13, y + 3), fill=light)
        draw.rectangle((x + 22, y + 1, x + 26, y + 3), fill=light)
    else:
        poly(draw, [(x + 12, y + 3), (x + 15, y), (x + 18, y + 3), (x + 21, y), (x + 24, y + 3)], light)
        draw.rectangle((x + 13, y + 3, x + 23, y + 4), fill=frame)


def make_wand_hud() -> Image.Image:
    image = canvas(256)
    for index, x in enumerate((0, 85, 170)):
        draw_slot(image, x, 0, True, index)
        draw_slot(image, x, 80, False, index)
    draw = ImageDraw.Draw(image)
    # Reserved ultimate-charge bar retained for compatibility with the legacy atlas.
    x, y, w, h = 29, 184, 201, 22
    poly(draw, [(x + 4, y), (x + w - 5, y), (x + w - 1, y + 4), (x + w - 1, y + h - 5),
                (x + w - 5, y + h - 1), (x + 4, y + h - 1), (x, y + h - 5), (x, y + 4)], INK)
    draw.rectangle((x + 3, y + 3, x + w - 4, y + h - 4), fill=(64, 34, 31, 255))
    draw.rectangle((x + 6, y + 6, x + w - 7, y + h - 7), fill=(18, 17, 19, 230))
    for pip in range(13):
        px = x + 10 + pip * 14
        draw.rectangle((px, y + 9, px + 2, y + 12), fill=PALE_GOLD if pip in (0, 12) else RED)
    return image


def make_fire_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    poly(draw, [(3, 21), (7, 15), (11, 14), (10, 9), (15, 13), (18, 5), (21, 14),
                (27, 10), (26, 18), (30, 21), (25, 25), (7, 25)], INK)
    poly(draw, [(6, 21), (10, 17), (14, 17), (13, 13), (18, 17), (19, 10), (22, 18),
                (26, 15), (24, 21), (27, 22), (23, 23), (8, 23)], RED)
    draw.line([(9, 21), (23, 21)], fill=PALE_GOLD, width=2)
    draw.point((17, 18), fill=IVORY)
    return image


def make_fire_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Brazier/pyre viewed as a compact, readable glyph.
    poly(draw, [(6, 21), (10, 18), (22, 18), (26, 21), (23, 27), (9, 27)], INK)
    poly(draw, [(9, 21), (13, 20), (20, 20), (23, 22), (21, 25), (11, 25)], (79, 45, 34, 255))
    draw.line([(11, 23), (21, 23)], fill=ORANGE, width=2)
    draw_flame(draw, [(11, 19), (10, 14), (13, 11), (12, 6), (16, 10), (19, 4), (21, 11), (24, 14), (21, 20)], inner=True)
    draw.point((16, 14), fill=IVORY)
    return image


def make_fire_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Falling meteor, angled to keep the silhouette distinct from the pyre.
    draw.line([(5, 4), (13, 12)], fill=CRIMSON, width=7)
    draw.line([(6, 5), (14, 13)], fill=ORANGE, width=3)
    draw.line([(7, 5), (13, 11)], fill=IVORY, width=1)
    poly(draw, [(12, 12), (21, 10), (28, 16), (27, 24), (21, 29), (13, 25), (10, 19)], INK)
    poly(draw, [(14, 14), (21, 12), (26, 16), (25, 23), (21, 26), (15, 24), (12, 19)], (79, 44, 34, 255))
    draw.line([(15, 17), (20, 19), (22, 24)], fill=RED, width=3)
    draw.line([(16, 17), (20, 19), (22, 23)], fill=PALE_GOLD, width=1)
    draw.point((20, 19), fill=IVORY)
    return image


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for frame in range(4):
        generated[TEXTURES / f"particle/fire/ember_{frame}.png"] = make_ember(frame)
        generated[TEXTURES / f"particle/fire/ash_{frame}.png"] = make_ash(frame)
        generated[TEXTURES / f"particle/arcane/mote_{frame}.png"] = make_arcane_mote(frame)
    for frame in range(6):
        generated[TEXTURES / f"particle/fire/flame_ribbon_{frame}.png"] = make_flame_ribbon(frame)
        generated[TEXTURES / f"particle/fire/impact_ring_{frame}.png"] = make_impact_ring(frame)
        generated[TEXTURES / f"particle/arcane/thread_{frame}.png"] = make_arcane_thread(frame)
    for frame in range(8):
        generated[TEXTURES / f"particle/fire/meteor_{frame}.png"] = make_meteor(frame)
    generated.update({
        TEXTURES / "block/inferno_flame.png": make_inferno_flame(),
        TEXTURES / "block/pyre_coals.png": make_pyre_coals(),
        TEXTURES / "block/meteor_core.png": make_meteor_core(),
        TEXTURES / "entity/inferno_wave.png": make_inferno_wave(),
        TEXTURES / "item/wizard_wand.png": make_wizard_wand(),
        TEXTURES / "gui/wand_hud_v2.png": make_wand_hud(),
        TEXTURES / "gui/ability/fire_primary.png": make_fire_primary_icon(),
        TEXTURES / "gui/ability/fire_secondary.png": make_fire_secondary_icon(),
        TEXTURES / "gui/ability/fire_ultimate.png": make_fire_ultimate_icon(),
    })
    return generated


def validate(path: Path, image: Image.Image) -> tuple[int, tuple[int, int, int, int]]:
    expected_size = image.size
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected_size, f"{path}: expected {expected_size}, got {reopened.size}"
        alpha = reopened.getchannel("A")
        assert alpha.getextrema()[1] > 0, f"{path}: empty alpha channel"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        colors = len(reopened.getcolors(maxcolors=reopened.width * reopened.height) or [])
        bbox = reopened.getbbox()
    return colors, bbox


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb = 96
    label_h = 18
    columns = 6
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (31, 27, 30, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, image) in enumerate(outputs.items()):
        col = index % columns
        row = index // columns
        x = col * thumb
        y = row * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (220, 220, 220, 255))
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 12):
            for cx in range(0, thumb, 12):
                if (cx // 12 + cy // 12) % 2:
                    checker_draw.rectangle((cx, cy, cx + 11, cy + 11), fill=(176, 176, 176, 255))
        scale = max(1, min(thumb // image.width, thumb // image.height))
        preview = image.resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        label = asset_path.stem[:15]
        draw.text((x + 3, y + thumb + 3), label, fill=(244, 233, 211, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    outputs = output_map()
    existing = [path for path in outputs if path.exists()]
    if existing:
        formatted = "\n".join(f"  {path.relative_to(ROOT)}" for path in existing)
        raise SystemExit(f"Refusing to overwrite existing production textures:\n{formatted}")

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path, format="PNG", optimize=False, compress_level=9)

    for path, image in outputs.items():
        colors, bbox = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA colors={colors} bbox={bbox}")

    # Preview is intentionally kept out of the repository.
    contact_sheet = Path("/tmp/elementalwands_fire_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
