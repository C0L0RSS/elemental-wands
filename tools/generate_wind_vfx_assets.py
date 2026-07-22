#!/usr/bin/env python3
"""Generate the second-generation Stratospheric Raptor Wind texture package.

Sprites are drawn directly on their final pixel grids with deterministic hard
clusters.  No random state, blur, antialiasing, resampling, or source artwork is
used.  Wind reads as layered pearl pressure plates, torn flight vanes, broken
silver seams, and sparse cold refraction rather than smooth white line art.
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
INK = (48, 55, 61, 245)
SLATE = (70, 79, 86, 235)
BLUE_GRAY = (91, 102, 110, 225)
SILVER_DARK = (122, 130, 135, 240)
SILVER = (157, 165, 168, 245)
PEARL_SHADOW = (190, 199, 199, 245)
PEARL = (220, 227, 224, 250)
IVORY = (241, 242, 234, 255)
WHITE = (255, 255, 248, 255)
COLD = (196, 218, 224, 230)
METAL_DARK = (55, 58, 63, 255)
METAL = (117, 122, 128, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def alpha(color: tuple[int, int, int, int], value: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], value


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def pixel_hash(x: int, y: int, seed: int) -> int:
    return (x * 47 + y * 71 + seed * 113 + x * y * 7 + y * y * 5) & 255


def draw_plate(draw: ImageDraw.ImageDraw, points, seed: int, highlight: bool = True) -> None:
    poly(draw, points, INK)
    cx = sum(x for x, _y in points) / len(points)
    cy = sum(y for _x, y in points) / len(points)
    inset = []
    for x, y in points:
        inset.append((x + (1 if x < cx else -1), y + (1 if y < cy else -1)))
    poly(draw, inset, (SILVER_DARK, SILVER, PEARL_SHADOW)[seed % 3])
    if highlight and len(points) >= 4:
        p1, p2 = inset[0], inset[1]
        draw.line((p1, p2), fill=PEARL if seed % 2 else IVORY, width=1)


def draw_broken_pressure_ring(image: Image.Image, radius: float, frame: int,
                              vertical_scale: float = 1.0, fade: int = 245) -> None:
    pixels = image.load()
    cx = (image.width - 1) / 2
    cy = (image.height - 1) / 2
    thickness = 2.15 if frame < 3 else 1.45
    palette = (BLUE_GRAY, SILVER_DARK, SILVER, PEARL_SHADOW, PEARL, IVORY, WHITE)
    for y in range(image.height):
        for x in range(image.width):
            dx = x - cx
            dy = (y - cy) * vertical_scale
            distance = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 32)
            gap = ((segment + frame * 2) % 9 == 0
                   or (frame > 2 and (segment * 3 + frame) % 17 == 0))
            jitter = ((pixel_hash(x // 2, y // 2, frame) % 5) - 2) * 0.16
            delta = abs(distance - radius - jitter)
            if gap or delta > thickness:
                continue
            index = min(len(palette) - 1, max(0, round((thickness - delta) / thickness * 6)))
            color = palette[index]
            pixels[x, y] = alpha(color, fade if index >= 3 else max(80, fade - 35))


def make_mote(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    cx, cy = ((6, 10), (8, 8), (9, 6), (7, 7))[frame]
    shapes = (
        [(cx - 2, cy + 1), (cx - 1, cy - 2), (cx + 1, cy - 3), (cx + 3, cy), (cx + 1, cy + 2)],
        [(cx - 3, cy), (cx - 1, cy - 2), (cx + 2, cy - 1), (cx + 3, cy + 1), (cx, cy + 2)],
        [(cx - 2, cy + 2), (cx - 2, cy - 1), (cx, cy - 3), (cx + 3, cy - 1), (cx + 2, cy + 2)],
        [(cx - 3, cy + 1), (cx - 1, cy - 2), (cx + 1, cy - 2), (cx + 3, cy), (cx + 1, cy + 3)],
    )
    poly(draw, shapes[frame], alpha(INK, 205))
    poly(draw, [(cx - 1, cy), (cx, cy - 2), (cx + 2, cy), (cx, cy + 1)], PEARL)
    draw.point((cx, cy - 1), fill=WHITE)
    draw.point((cx + 1, cy), fill=COLD)
    draw.line((cx - 2, cy + 2, cx - 4 - frame, cy + 3), fill=alpha(SILVER, 135), width=1)
    draw.point((12 - frame * 2, 3 + frame), fill=alpha(IVORY, 135 + frame * 18))
    return image


def make_crescent(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Faceted talon assembled from overlapping pressure plates.
    plates = (
        [(3, 24), (5, 18), (10, 16), (12, 20), (8, 25)],
        [(7, 19), (9, 12), (15, 9), (17, 13), (12, 19)],
        [(14, 11), (19, 6), (25, 6), (26, 10), (20, 13)],
        [(23, 7), (29, 9), (28, 14), (24, 14), (26, 11)],
    )
    shift = ((frame + 1) % 3) - 1
    for index, plate in enumerate(plates):
        shifted = [(x + (frame // 4 if index > 1 else 0), y + shift * (index % 2)) for x, y in plate]
        draw_plate(draw, shifted, index + frame)
    # Quill seam and negative-space break stop it reading as a drawn crescent.
    draw.line((5, 23, 12, 17, 18, 11, 26, 9), fill=SLATE, width=2)
    draw.line((8, 21, 15, 14, 22, 9), fill=WHITE, width=1)
    cut_x = 12 + frame * 2
    draw.rectangle((cut_x, 13, min(28, cut_x + 2), 16), fill=T)
    for index in range(4):
        x = 3 + ((index * 8 + frame * 5) % 27)
        y = 4 + ((index * 6 + frame * 3) % 23)
        draw.rectangle((x, y, x + (index & 1), y + 1), fill=alpha((BLUE_GRAY, PEARL)[index & 1], 150))
    return image


def make_air_ribbon(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Two offset lanes of small stepped pressure facets, never a continuous curve.
    for lane in range(2):
        for index in range(6):
            x = 2 + index * 5 + lane * 2
            y = 10 + lane * 9 + ((index * 3 + frame * 2 + lane) % 5) - 2
            length = 3 + ((index + frame + lane) % 3)
            points = [(x, y + 1), (x + 1, y - 1), (x + length, y - 2),
                      (x + length + 2, y), (x + length, y + 2), (x + 1, y + 2)]
            draw_plate(draw, points, index + frame + lane, highlight=index % 2 == 0)
            if index in (1, 4):
                draw.point((x + length, y), fill=COLD)
    draw.rectangle((10 + frame * 3, 7, 11 + frame * 3, 24), fill=T)
    for x, y in ((3 + frame, 27 - frame), (27 - frame, 4 + frame)):
        draw.line((x, y, x + 3, y - 1), fill=alpha(PEARL, 145), width=1)
    return image


def make_burst_ring(frame: int) -> Image.Image:
    image = canvas(32)
    draw_broken_pressure_ring(image, 4.0 + frame * 2.2, frame, 1.10, 245 - frame * 20)
    draw = ImageDraw.Draw(image)
    for index in range(6):
        angle = index * math.tau / 6 + frame * 0.19
        inner = 5 + frame * 2
        outer = min(15, inner + 3 + (index + frame) % 3)
        x1 = round(15.5 + math.cos(angle) * inner)
        y1 = round(15.5 + math.sin(angle) * inner / 1.1)
        x2 = round(15.5 + math.cos(angle) * outer)
        y2 = round(15.5 + math.sin(angle) * outer / 1.1)
        draw.line((x1, y1, x2, y2), fill=alpha((SILVER, COLD)[index & 1], 160), width=1)
    draw.point((4 + frame * 4, 4 + (frame * 5) % 23), fill=alpha(WHITE, 165))
    return image


def make_zephyr_impact(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    fade = max(85, 255 - frame * 20)
    ground = 48
    # Ground pressure disc.
    draw_broken_pressure_ring(image, 7 + frame * 3.1, frame, 2.45, fade)
    # A stepped vertical sky-spear made of separated plates.
    spear_height = 12 + frame * 4
    for index in range(6):
        y = ground - index * max(3, spear_height // 6)
        spread = max(1, (5 - index) - frame // 3)
        x = 32 + ((index + frame) % 3) - 1
        points = [(x - spread - 2, y + 3), (x - spread, y - 2), (x, y - 5),
                  (x + spread + 2, y - 1), (x + spread, y + 3), (x, y + 5)]
        draw_plate(draw, points, index + frame)
        if index >= 3:
            draw.point((x, y - 2), fill=alpha(WHITE, fade))
    # Radial feather/shear chunks stay angular and materially shaded.
    for index in range(14):
        angle = math.pi + index * math.pi / 13
        distance = 9 + frame * 3 + (index * 5) % 9
        x = round(32 + math.cos(angle) * distance)
        y = round(ground + math.sin(angle) * distance * 0.65)
        size = 2 + ((index + frame) % 3)
        points = [(x - size, y + 1), (x, y - size), (x + size + 1, y - 1), (x + 1, y + 2)]
        draw_plate(draw, points, index + frame)
    for index in range(8):
        x = 5 + ((index * 17 + frame * 5) % 54)
        y = 8 + ((index * 11 + frame * 3) % 43)
        draw.rectangle((x, y, x + 1, y + (index & 1)), fill=alpha(COLD if index % 3 == 0 else PEARL, fade - 25))
    return image


def make_slipstream(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Torn, offset pressure planes form a tapered directional wake. Their
    # irregular overlap avoids both a centered ladder and a literal arrowhead.
    base_planes = (
        [(1, 12), (6, 7), (16, 9), (24, 5), (29, 8), (23, 13), (10, 15)],
        [(4, 21), (11, 15), (21, 17), (30, 15), (27, 21), (19, 24), (8, 25)],
        [(1, 28), (7, 24), (15, 25), (23, 23), (27, 27), (18, 30), (6, 31)],
    )
    for lane, plane in enumerate(base_planes):
        shift_x = frame // 2 + (lane if frame % 3 == 2 else 0)
        shift_y = ((frame + lane * 2) % 3) - 1
        shifted = [(min(31, x + shift_x), max(0, min(31, y + shift_y))) for x, y in plane]
        # Progressive trailing cuts make consecutive frames visibly flow.
        if frame >= 3 and lane == frame % 3:
            shifted = shifted[1:]
        draw_plate(draw, shifted, lane + frame)
        seam = shifted[1:4]
        if len(seam) >= 2:
            draw.line(seam, fill=SLATE, width=2)
            draw.line(seam[1:], fill=IVORY, width=1)
        hx, hy = shifted[3]
        draw.rectangle((max(0, hx - 1), hy, hx, min(31, hy + 1)), fill=COLD)

    # Detached shear planes trail behind and outside the main wake.
    for index in range(7):
        x = 1 + ((index * 9 + frame * 5) % 27)
        y = 2 + ((index * 13 + frame * 4) % 27)
        length = 2 + (index + frame) % 4
        color = (BLUE_GRAY, SILVER, PEARL, COLD)[index % 4]
        draw.line((x, y + 1, min(31, x + length), max(0, y - 1)),
                  fill=alpha(color, 145 + (index % 3) * 20), width=1)
        if index % 3 == 0:
            draw.point((x, y), fill=alpha(INK, 160))
    return image


def make_shear_feather(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # A broken flight vane rather than a flat feather emblem.
    axis = [(5 + frame // 2, 25), (10, 20), (15, 15), (21, 9), (27, 5 + frame % 2)]
    draw.line(axis, fill=INK, width=3)
    draw.line(axis[1:-1], fill=IVORY, width=1)
    for index in range(5):
        x = 8 + index * 4
        y = 22 - index * 4
        side = -1 if (index + frame) % 2 else 1
        reach = 4 + ((index + frame) % 3)
        points = [(x - 1, y + 1), (x + side * reach, y - 3),
                  (x + side * (reach + 2), y - 1), (x + 1, y + 2)]
        draw_plate(draw, points, index + frame)
        if index % 2 == 0:
            draw.point((x + side * reach, y - 2), fill=COLD)
    # Progressive edge erosion, while retaining unique readable silhouettes.
    for index in range(frame):
        x = 3 + ((index * 9 + frame * 5) % 27)
        y = 4 + ((index * 7 + frame * 3) % 24)
        draw.rectangle((x, y, min(31, x + 2), y + 1), fill=alpha(PEARL, 120 + frame * 12))
    return image


def make_vacuum_blade(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    # Six animated pressure plates form one forward-heavy talon.
    plates = (
        [(5, 50), (8, 39), (15, 34), (20, 39), (14, 50)],
        [(11, 39), (16, 27), (26, 21), (30, 27), (22, 39)],
        [(23, 25), (31, 15), (42, 10), (46, 16), (36, 25)],
        [(40, 13), (53, 9), (60, 15), (57, 22), (47, 21)],
        [(48, 21), (61, 18), (58, 28), (51, 31), (45, 27)],
        [(17, 45), (29, 34), (43, 27), (48, 31), (35, 39), (24, 50)],
    )
    wobble = (0, 1, 0, -1, 0, 1)[frame]
    for index, plate in enumerate(plates):
        shifted = [(x, y + wobble * (1 if index % 2 else -1)) for x, y in plate]
        draw_plate(draw, shifted, index + frame)
        # Authored interior facets make 64px sprites hold up at close camera range.
        cx = round(sum(x for x, _y in shifted) / len(shifted))
        cy = round(sum(y for _x, y in shifted) / len(shifted))
        draw.rectangle((cx - 1, cy - 1, cx + 2, cy + 1),
                       fill=(BLUE_GRAY, PEARL_SHADOW, PEARL)[(index + frame) % 3])
        draw.line((cx - 2, cy - 2, cx + 2, cy - 2), fill=IVORY, width=1)
    # Deep quill seam and cold compression edge.
    draw.line((7, 48, 18, 38, 29, 28, 43, 19, 56, 16), fill=INK, width=4)
    draw.line((10, 46, 20, 37, 31, 27, 45, 18, 57, 16), fill=COLD, width=2)
    draw.line((18, 43, 30, 34, 43, 28, 52, 27), fill=WHITE, width=1)
    # Negative-space cuts and wake shards prevent a single smooth silhouette.
    cut = 15 + frame * 5
    draw.rectangle((cut, 34, min(58, cut + 3), 39), fill=T)
    for index in range(10):
        x = 3 + ((index * 13 + frame * 7) % 58)
        y = 5 + ((index * 9 + frame * 4) % 52)
        length = 2 + (index + frame) % 4
        draw.line((x, y, min(63, x + length), max(0, y - 2)),
                  fill=alpha((SILVER, PEARL, COLD)[index % 3], 145 + (index % 3) * 20), width=1)
    draw.point((60, 15 + wobble), fill=WHITE)
    return image


def make_zephyr_wings_item() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    left = (
        [(15, 9), (11, 4), (4, 2), (6, 9), (14, 14)],
        [(14, 12), (7, 7), (1, 8), (5, 15), (14, 18)],
        [(14, 16), (6, 13), (2, 17), (8, 22), (15, 21)],
        [(15, 19), (8, 20), (6, 25), (13, 27), (16, 22)],
    )
    for index, feather in enumerate(left):
        draw_plate(draw, feather, index)
        mirrored = [(31 - x, y) for x, y in feather]
        draw_plate(draw, mirrored, index + 1)
        x1, y1 = feather[0]
        x2, y2 = feather[-2]
        draw.line((x1, y1, x2, y2), fill=IVORY, width=1)
        draw.line((31 - x1, y1, 31 - x2, y2), fill=IVORY, width=1)
    poly(draw, [(13, 8), (16, 5), (19, 8), (18, 22), (16, 27), (14, 22)], INK)
    poly(draw, [(15, 9), (16, 7), (17, 9), (17, 21), (16, 25), (15, 21)], SILVER)
    draw.line((16, 8, 16, 22), fill=WHITE, width=1)
    draw.point((4, 12), fill=alpha(COLD, 170))
    draw.point((27, 12), fill=alpha(COLD, 170))
    return image


def make_zephyr_wings_worn() -> Image.Image:
    image = canvas((64, 32))
    draw = ImageDraw.Draw(image)
    # Vanilla wings UV island; transparent steps customize its outer silhouette.
    silhouette = [(31, 0), (39, 0), (39, 2), (42, 2), (42, 4), (44, 4),
                  (44, 7), (46, 7), (46, 13), (45, 13), (45, 17), (43, 17),
                  (43, 20), (41, 20), (41, 23), (38, 23), (38, 20), (36, 20),
                  (36, 17), (34, 17), (34, 12), (33, 12), (33, 7), (32, 7)]
    poly(draw, silhouette, INK)
    inner = [(33, 1), (38, 1), (38, 3), (41, 3), (41, 5), (43, 5),
             (43, 8), (45, 8), (45, 12), (44, 12), (44, 16), (42, 16),
             (42, 19), (40, 19), (40, 21), (38, 19), (36, 16), (35, 11), (34, 7)]
    poly(draw, inner, PEARL_SHADOW)
    # Individually shaded vane plates, constrained to the equipment UV.
    vanes = (
        [(34, 3), (38, 2), (43, 6), (42, 8), (36, 6)],
        [(35, 7), (40, 7), (45, 10), (44, 12), (36, 10)],
        [(35, 11), (40, 11), (44, 14), (42, 17), (36, 15)],
        [(36, 16), (40, 16), (42, 19), (40, 21), (37, 19)],
    )
    for index, vane in enumerate(vanes):
        poly(draw, vane, (SILVER, PEARL, IVORY, PEARL_SHADOW)[index])
        draw.line((vane[0], vane[2]), fill=WHITE if index != 3 else COLD, width=1)
    draw.line((34, 4, 35, 15, 39, 21), fill=BLUE_GRAY, width=1)
    draw.point((37, 3), fill=WHITE)
    draw.point((43, 9), fill=COLD)
    # Narrow edge island used by the vanilla model.
    draw.line((22, 11, 22, 21), fill=INK, width=1)
    for y in range(12, 21, 2):
        draw.point((22, y), fill=PEARL)
    return image


def make_wind_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for vertical in (0, 6):
        plates = (
            [(2, 22 - vertical), (5, 16 - vertical), (11, 13 - vertical), (13, 17 - vertical), (8, 23 - vertical)],
            [(9, 16 - vertical), (16, 8 - vertical // 2), (24, 7 + vertical // 3),
             (28, 11 + vertical // 2), (21, 13 + vertical // 2), (14, 19 - vertical // 2)],
        )
        for index, plate in enumerate(plates):
            draw_plate(draw, plate, index + vertical)
    draw.line((4, 27, 14, 24), fill=alpha(COLD, 180), width=1)
    return image


def make_wind_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for index, y in enumerate((7, 13, 19, 25)):
        start = 2 + index
        points = [(start, y + 1), (start + 4, y - 2), (20 + index, y - 1),
                  (28, y), (20 + index, y + 2), (start + 4, y + 2)]
        draw_plate(draw, points, index)
        draw.line((start + 6, y, 25, y), fill=WHITE, width=1)
    draw.point((29, 13), fill=COLD)
    return image


def make_wind_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    left = (
        [(15, 8), (10, 3), (3, 5), (7, 11), (14, 14)],
        [(14, 12), (7, 9), (2, 13), (9, 17), (14, 18)],
        [(14, 17), (7, 16), (5, 22), (13, 23), (15, 20)],
    )
    for index, plate in enumerate(left):
        draw_plate(draw, plate, index)
        draw_plate(draw, [(31 - x, y) for x, y in plate], index + 1)
    poly(draw, [(14, 7), (16, 3), (18, 7), (18, 22), (16, 28), (14, 22)], INK)
    poly(draw, [(15, 8), (16, 5), (17, 8), (17, 21), (16, 26), (15, 21)], PEARL)
    draw.line((16, 6, 16, 21), fill=WHITE, width=1)
    draw_broken_pressure_ring(image, 11, 2, 2.2, 200)
    return image


WIND_FAMILIES = {
    "mote": (4, 16, make_mote),
    "crescent": (6, 32, make_crescent),
    "air_ribbon": (6, 32, make_air_ribbon),
    "burst_ring": (6, 32, make_burst_ring),
    "zephyr_impact": (8, 64, make_zephyr_impact),
    "slipstream": (6, 32, make_slipstream),
    "shear_feather": (6, 32, make_shear_feather),
}


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for family, (count, _size, maker) in WIND_FAMILIES.items():
        for frame in range(count):
            generated[TEXTURES / f"particle/wind/{family}_{frame}.png"] = maker(frame)
    for frame in range(6):
        generated[TEXTURES / f"entity/vacuum_blade_{frame}.png"] = make_vacuum_blade(frame)
    generated.update({
        TEXTURES / "item/zephyr_wings.png": make_zephyr_wings_item(),
        TEXTURES / "entity/equipment/wings/zephyr_wings.png": make_zephyr_wings_worn(),
        TEXTURES / "gui/ability/wind_primary.png": make_wind_primary_icon(),
        TEXTURES / "gui/ability/wind_secondary.png": make_wind_secondary_icon(),
        TEXTURES / "gui/ability/wind_ultimate.png": make_wind_ultimate_icon(),
    })
    return generated


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    for family, (count, size, _maker) in WIND_FAMILIES.items():
        frames = [outputs[TEXTURES / f"particle/wind/{family}_{frame}.png"] for frame in range(count)]
        assert all(image.size == (size, size) for image in frames), f"{family}: wrong frame dimensions"
        assert len({image.tobytes() for image in frames}) == count, f"{family}: duplicate frames"
    blades = [outputs[TEXTURES / f"entity/vacuum_blade_{frame}.png"] for frame in range(6)]
    assert len({image.tobytes() for image in blades}) == 6, "vacuum_blade: duplicate frames"


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
        visible = {rgba for _count, rgba in colors if rgba[3] > 0}
        minimum = 6 if max(expected.size) >= 32 else 4
        assert len(visible) >= minimum, f"{path}: only {len(visible)} visible colors"
        for rgba in visible:
            assert max(rgba[:3]) - min(rgba[:3]) <= 32, f"{path}: saturated non-Wind color {rgba}"
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
        bbox = reopened.getbbox()
    return len(visible), bbox, digest


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb, label_h, columns = 112, 28, 7
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (35, 39, 42, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        x = (index % columns) * thumb
        y = (index // columns) * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (223, 225, 222, 255))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 14):
            for cx in range(0, thumb, 14):
                if (cx // 14 + cy // 14) % 2:
                    cd.rectangle((cx, cy, cx + 13, cy + 13), fill=(173, 178, 179, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), relative.stem[:20], fill=(250, 250, 243, 255), font=font)
        draw.text((x + 3, y + thumb + 14), str(relative.parent)[-17:], fill=(176, 193, 199, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="replace only this script's known outputs and remove its known legacy file")
    args = parser.parse_args()
    outputs = output_map()
    validate_frame_families(outputs)
    legacy = TEXTURES / "entity/vacuum_blade.png"
    if legacy.exists() and not args.replace:
        raise SystemExit(f"Refusing to remove legacy Wind texture without --replace: {legacy.relative_to(ROOT)}")

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        differs = True
        if path.exists():
            with Image.open(path) as existing:
                existing.load()
                differs = existing.mode != "RGBA" or existing.size != image.size or existing.tobytes() != image.tobytes()
        if differs:
            if path.exists() and not args.replace:
                raise SystemExit(f"Refusing to overwrite differing Wind texture: {path.relative_to(ROOT)}")
            image.save(path, format="PNG", optimize=False, compress_level=9)

    if args.replace and legacy.exists():
        legacy.unlink()

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_wind_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"WIND_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
