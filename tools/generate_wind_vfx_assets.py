#!/usr/bin/env python3
"""Generate the approved Wind pixel-art texture package.

Every production image is drawn directly on its final pixel grid.  The script
uses no random state, source image, resizing, blur, or antialiasing, so repeated
runs produce the same hard-edged Minecraft-native sprites.
"""

from __future__ import annotations

import hashlib
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/elementalwands/textures"

T = (0, 0, 0, 0)
BLUE_GRAY_DARK = (85, 94, 102, 225)
BLUE_GRAY = (109, 118, 125, 205)
SILVER_DARK = (137, 141, 141, 235)
SILVER = (171, 175, 173, 245)
PEARL_SHADOW = (204, 207, 203, 245)
PEARL = (231, 233, 228, 250)
WHITE = (248, 248, 242, 255)
BRIGHT = (255, 255, 251, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def with_alpha(color: tuple[int, int, int, int], alpha: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], alpha


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def bezier(points, samples: int = 32) -> list[tuple[int, int]]:
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


def clipped_line(draw: ImageDraw.ImageDraw, points, fill, width: int) -> None:
    if len(points) >= 2:
        draw.line(points, fill=fill, width=width, joint="curve")


def make_mote(frame: int) -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    centers = ((6, 10), (8, 8), (9, 6), (7, 7))
    x, y = centers[frame]
    shapes = (
        [(x - 1, y), (x, y - 2), (x + 1, y - 1), (x + 1, y + 1), (x, y + 2), (x - 1, y + 1)],
        [(x - 2, y), (x, y - 2), (x + 2, y), (x, y + 2)],
        [(x - 1, y - 2), (x + 1, y - 2), (x + 2, y), (x, y + 2), (x - 2, y + 1)],
        [(x - 2, y - 1), (x, y - 2), (x + 2, y), (x + 1, y + 2), (x - 1, y + 1)],
    )
    poly(draw, shapes[frame], with_alpha(BLUE_GRAY, 180))
    draw.rectangle((x - 1, y - 1, x, y), fill=PEARL)
    draw.point((x, y - 1), fill=BRIGHT)
    tails = (((4, 13), (3, 14)), ((5, 11), (3, 12)), ((7, 10), (4, 12)), ((9, 10), (11, 12)))
    clipped_line(draw, tails[frame], with_alpha(PEARL_SHADOW, 125), 1)
    draw.point((12 - frame * 2, 3 + frame), fill=with_alpha(WHITE, 115 + frame * 12))
    return image


def make_crescent(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame - 2.5
    path = bezier((
        (5 + frame // 2, 23 - frame % 2),
        (8 + round(phase * 0.7), 7 + frame // 3),
        (23 + round(phase * 0.6), 5 + (frame + 1) % 2),
        (27 - frame // 3, 13 + frame % 3),
    ), 38)
    clipped_line(draw, path, with_alpha(BLUE_GRAY, 190), 7)
    clipped_line(draw, path[2:-1], SILVER, 5)
    clipped_line(draw, path[5:-3], PEARL, 3)
    clipped_line(draw, path[9:-7], BRIGHT, 1)
    # The advancing tip is solid; the trailing end fragments into air dashes.
    tx, ty = path[0]
    draw.rectangle((tx - 3, ty - 3, tx + 2, ty + 3), fill=T)
    clipped_line(draw, path[4:9], with_alpha(PEARL_SHADOW, 205), 2)
    ex, ey = path[-1]
    draw.rectangle((ex - 1, ey - 1, ex + 1, ey + 1), fill=BRIGHT)
    for index, (x, y) in enumerate(((4 + frame, 8 + frame % 3), (11 + frame * 2, 27 - frame % 2))):
        draw.line((x, y, x + 2 + index, y - 1), fill=with_alpha(PEARL, 145 + frame * 8), width=1)
    return image


def make_air_ribbon(frame: int) -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    phase = frame / 6.0 * math.tau
    primary: list[tuple[int, int]] = []
    secondary: list[tuple[int, int]] = []
    for x in range(2, 30):
        y = 15 + round(math.sin(x * 0.36 + phase) * 5)
        primary.append((x, y))
        secondary.append((x, y + 5 + round(math.sin(x * 0.22 + phase) * 1.5)))
    clipped_line(draw, primary[2:-2], with_alpha(BLUE_GRAY, 145), 4)
    clipped_line(draw, primary[3:-3], with_alpha(PEARL_SHADOW, 225), 2)
    clipped_line(draw, primary[6:-6], WHITE, 1)
    clipped_line(draw, secondary[5:-5], with_alpha(SILVER, 165), 2)
    clipped_line(draw, secondary[9:-8], with_alpha(PEARL, 210), 1)
    # Intentional gaps keep the sprite airy instead of looking like water.
    gap_x = 8 + frame * 3
    draw.rectangle((gap_x, 6, gap_x + 1, 25), fill=T)
    for x, y in (primary[1], primary[-2], secondary[3], secondary[-3]):
        draw.point((x, y), fill=with_alpha(WHITE, 135))
    return image


def make_burst_ring(frame: int) -> Image.Image:
    image = canvas(32)
    pixels = image.load()
    cx = cy = 15.5
    radius = 4.0 + frame * 2.25
    thickness = 1.9 if frame < 3 else 1.4
    fade = 245 - frame * 19
    for y in range(32):
        for x in range(32):
            dx = x - cx
            dy = (y - cy) * 1.08
            distance = math.sqrt(dx * dx + dy * dy)
            angle = (math.atan2(dy, dx) + math.tau) % math.tau
            segment = int(angle / math.tau * 28)
            gap = (segment + frame * 2) % 9 == 0 or (frame >= 4 and (segment + frame) % 6 == 0)
            delta = abs(distance - radius)
            if not gap and delta <= thickness:
                if delta <= 0.45 and frame < 4:
                    pixels[x, y] = with_alpha(BRIGHT, fade)
                elif delta <= 1.0:
                    pixels[x, y] = with_alpha(PEARL, fade)
                else:
                    pixels[x, y] = with_alpha(SILVER, max(80, fade - 35))
    draw = ImageDraw.Draw(image)
    for side in (-1, 1):
        x = round(cx + side * (radius + 2))
        y = round(cy + side * ((frame % 3) - 1))
        if 1 <= x <= 30:
            draw.line((x - side, y, x + side * 2, y - side), fill=with_alpha(PEARL, max(100, fade - 30)), width=1)
    return image


def make_zephyr_impact(frame: int) -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    fade = 255 - frame * 19
    cx, ground = 31.5, 47

    # A broken ground-hugging pressure ring expands from the landing point.
    rx = 7 + frame * 3
    ry = max(2.0, rx * 0.34)
    ring: list[tuple[int, int]] = []
    for step in range(65):
        angle = step / 64.0 * math.tau
        segment = int(step / 4)
        if (segment + frame) % 7 == 0:
            if len(ring) > 1:
                clipped_line(draw, ring, with_alpha(PEARL, fade), 2 if frame < 5 else 1)
            ring = []
            continue
        ring.append((round(cx + math.cos(angle) * rx), round(ground + math.sin(angle) * ry)))
    clipped_line(draw, ring, with_alpha(PEARL, fade), 2 if frame < 5 else 1)

    # Three rising streamlines bend rather than radiating, avoiding a snowflake motif.
    height = 11 + frame * 3
    for lane in (-1, 0, 1):
        sway = lane * (6 + frame)
        path = bezier((
            (32 + lane * 2, ground - 1),
            (31 + sway, ground - height // 3),
            (35 - sway, ground - height * 2 // 3),
            (31 + lane * (4 + frame // 2), ground - height),
        ), 28)
        shadow_width = 4 if lane == 0 and frame < 4 else 3
        clipped_line(draw, path, with_alpha(BLUE_GRAY, max(80, fade - 60)), shadow_width)
        clipped_line(draw, path[2:-2], with_alpha(PEARL_SHADOW, max(100, fade - 20)), max(1, shadow_width - 1))
        clipped_line(draw, path[6:-6], with_alpha(WHITE, fade), 1)

    # Low lateral gusts make the landing direction readable.
    for side in (-1, 1):
        y = ground - 4 - (frame % 2)
        path = bezier(((32, y), (32 + side * 8, y - 7), (32 + side * (14 + frame), y - 3),
                       (32 + side * min(29, 12 + frame * 3), y - 9 - frame)), 24)
        clipped_line(draw, path, with_alpha(SILVER, max(80, fade - 20)), 3)
        clipped_line(draw, path[3:-3], with_alpha(BRIGHT, fade), 1)

    for index in range(5):
        x = 12 + ((index * 11 + frame * 5) % 42)
        y = 18 + ((index * 9 + frame * 3) % 28)
        draw.line((x, y, x + (1 if index % 2 else 2), y - 1), fill=with_alpha(PEARL, max(75, fade - 45)), width=1)
    return image


def make_vacuum_blade() -> Image.Image:
    image = canvas(64)
    draw = ImageDraw.Draw(image)
    main = bezier(((7, 47), (12, 14), (43, 5), (58, 19)), 64)
    clipped_line(draw, main, BLUE_GRAY_DARK, 13)
    clipped_line(draw, main[2:-1], SILVER_DARK, 11)
    clipped_line(draw, main[5:-2], PEARL, 7)
    clipped_line(draw, main[9:-4], BRIGHT, 3)

    # Carve the inner edge into a thin, forward-heavy vacuum slash.
    cut = bezier(((14, 47), (23, 26), (43, 17), (54, 20)), 52)
    clipped_line(draw, cut, T, 7)
    clipped_line(draw, cut[5:-5], with_alpha(PEARL_SHADOW, 205), 2)

    for index, offset in enumerate((0, 6, 11)):
        tail = bezier(((5, 49 + index * 2), (13, 47 + offset), (24, 43 + offset // 2), (34 + index * 5, 35 + offset // 3)), 24)
        clipped_line(draw, tail, with_alpha(PEARL if index == 0 else SILVER, 190 - index * 30), 2 if index == 0 else 1)
    draw.line((51, 13, 59, 17), fill=WHITE, width=2)
    draw.point((60, 18), fill=BRIGHT)
    return image


def draw_feather(draw: ImageDraw.ImageDraw, points, highlight) -> None:
    poly(draw, points, BLUE_GRAY_DARK)
    inset = [(x + (1 if x < 16 else -1), y) for x, y in points]
    poly(draw, inset, PEARL_SHADOW)
    if len(points) >= 4:
        x1, y1 = points[1]
        x2, y2 = points[-2]
        draw.line((x1, y1, x2, y2), fill=highlight, width=1)


def make_zephyr_wings_item() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    left = [
        [(15, 10), (11, 5), (5, 3), (7, 10), (14, 16)],
        [(14, 13), (7, 8), (2, 8), (6, 15), (14, 19)],
        [(14, 16), (7, 14), (3, 17), (9, 22), (15, 21)],
        [(15, 19), (9, 20), (7, 24), (13, 26), (16, 22)],
    ]
    for feather in left:
        draw_feather(draw, feather, WHITE)
    for feather in left:
        mirrored = [(31 - x, y) for x, y in feather]
        draw_feather(draw, mirrored, WHITE)
    poly(draw, [(13, 9), (16, 6), (19, 9), (18, 22), (16, 26), (14, 22)], BLUE_GRAY_DARK)
    poly(draw, [(15, 10), (16, 8), (17, 10), (17, 21), (16, 24), (15, 21)], SILVER)
    draw.line((16, 9, 16, 21), fill=BRIGHT, width=1)
    draw.point((4, 12), fill=with_alpha(WHITE, 160))
    draw.point((27, 12), fill=with_alpha(WHITE, 160))
    return image


def make_zephyr_wings_worn() -> Image.Image:
    image = canvas((64, 32))
    draw = ImageDraw.Draw(image)
    # This is the 1.21.10 `wings` equipment-layer UV island.  Minecraft mirrors
    # it for the opposite wing; transparent feather steps create a custom edge.
    silhouette = [
        (31, 0), (39, 0), (39, 2), (42, 2), (42, 4), (44, 4), (44, 7),
        (46, 7), (46, 13), (45, 13), (45, 17), (43, 17), (43, 20),
        (41, 20), (41, 22), (38, 22), (38, 20), (36, 20), (36, 17),
        (34, 17), (34, 12), (33, 12), (33, 7), (32, 7),
    ]
    poly(draw, silhouette, BLUE_GRAY_DARK)
    inner = [
        (33, 1), (38, 1), (38, 3), (41, 3), (41, 5), (43, 5), (43, 8),
        (45, 8), (45, 12), (44, 12), (44, 16), (42, 16), (42, 19),
        (40, 19), (40, 21), (38, 19), (36, 16), (35, 11), (34, 7),
    ]
    poly(draw, inner, PEARL_SHADOW)
    # Layered feather vanes sweep downward and outward.
    draw.line([(34, 5), (42, 8), (45, 10)], fill=WHITE, width=2)
    draw.line([(35, 9), (43, 12), (44, 14)], fill=PEARL, width=2)
    draw.line([(36, 13), (42, 16), (42, 18)], fill=WHITE, width=2)
    draw.line([(37, 17), (40, 20)], fill=PEARL, width=2)
    draw.line([(34, 4), (35, 15), (39, 20)], fill=SILVER, width=1)
    draw.point((36, 3), fill=BRIGHT)
    draw.point((42, 9), fill=BRIGHT)
    # Narrow edge island used by the vanilla Elytra model.
    draw.line((22, 11, 22, 21), fill=BLUE_GRAY_DARK, width=1)
    for y in range(12, 21, 2):
        draw.point((22, y), fill=PEARL)
    return image


def make_wind_primary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    for offset in (0, 5):
        path = bezier(((4, 23 - offset), (8, 9 - offset // 2), (23, 4 + offset), (28, 11 + offset)), 30)
        clipped_line(draw, path, BLUE_GRAY_DARK, 5)
        clipped_line(draw, path[3:-2], PEARL, 3)
        clipped_line(draw, path[7:-5], BRIGHT, 1)
    draw.line((4, 27, 13, 24), fill=with_alpha(SILVER, 190), width=1)
    return image


def make_wind_secondary_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    # Layered forward gusts communicate a dash without an ice-like arrowhead.
    for index, y in enumerate((9, 15, 21)):
        start = 3 + index * 2
        draw.line((start, y, 17 + index, y), fill=BLUE_GRAY_DARK, width=3)
        draw.line((start + 2, y, 18 + index, y), fill=WHITE, width=1)
        draw.line((16 + index, y - 4, 26 + index, y, 16 + index, y + 4), fill=PEARL, width=2)
    draw.point((28, 15), fill=BRIGHT)
    return image


def make_wind_ultimate_icon() -> Image.Image:
    image = canvas(32)
    draw = ImageDraw.Draw(image)
    left = [(15, 9), (10, 5), (4, 6), (7, 11), (3, 14), (10, 16), (6, 21), (14, 19)]
    poly(draw, left, BLUE_GRAY_DARK)
    poly(draw, [(14, 10), (10, 7), (6, 7), (9, 11), (6, 14), (11, 14), (9, 18), (14, 17)], PEARL)
    poly(draw, [(31 - x, y) for x, y in left], BLUE_GRAY_DARK)
    poly(draw, [(31 - x, y) for x, y in [(14, 10), (10, 7), (6, 7), (9, 11), (6, 14), (11, 14), (9, 18), (14, 17)]], PEARL)
    draw.line((16, 5, 16, 22), fill=WHITE, width=3)
    draw.line((16, 4, 16, 18), fill=BRIGHT, width=1)
    draw.arc((7, 19, 24, 27), 4, 176, fill=SILVER, width=2)
    draw.line((5, 25, 27, 25), fill=with_alpha(PEARL, 190), width=1)
    return image


def output_map() -> dict[Path, Image.Image]:
    generated: dict[Path, Image.Image] = {}
    for frame in range(4):
        generated[TEXTURES / f"particle/wind/mote_{frame}.png"] = make_mote(frame)
    for frame in range(6):
        generated[TEXTURES / f"particle/wind/crescent_{frame}.png"] = make_crescent(frame)
        generated[TEXTURES / f"particle/wind/air_ribbon_{frame}.png"] = make_air_ribbon(frame)
        generated[TEXTURES / f"particle/wind/burst_ring_{frame}.png"] = make_burst_ring(frame)
    for frame in range(8):
        generated[TEXTURES / f"particle/wind/zephyr_impact_{frame}.png"] = make_zephyr_impact(frame)
    generated.update({
        TEXTURES / "entity/vacuum_blade.png": make_vacuum_blade(),
        TEXTURES / "item/zephyr_wings.png": make_zephyr_wings_item(),
        TEXTURES / "entity/equipment/wings/zephyr_wings.png": make_zephyr_wings_worn(),
        TEXTURES / "gui/ability/wind_primary.png": make_wind_primary_icon(),
        TEXTURES / "gui/ability/wind_secondary.png": make_wind_secondary_icon(),
        TEXTURES / "gui/ability/wind_ultimate.png": make_wind_ultimate_icon(),
    })
    return generated


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: pixels differ from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        alpha = reopened.getchannel("A")
        alpha_min, alpha_max = alpha.getextrema()
        assert alpha_min == 0 and alpha_max >= 80, f"{path}: expected genuine transparency and visible pixels"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        for count, rgba in colors:
            if rgba[3] == 0:
                continue
            saturation_span = max(rgba[:3]) - min(rgba[:3])
            assert saturation_span <= 22, f"{path}: saturated non-Wind color {rgba} count={count}"
        bbox = reopened.getbbox()
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
    return len(colors), bbox, digest


def validate_frame_families(outputs: dict[Path, Image.Image]) -> None:
    for family, count in (("mote", 4), ("crescent", 6), ("air_ribbon", 6), ("burst_ring", 6), ("zephyr_impact", 8)):
        frames = [outputs[TEXTURES / f"particle/wind/{family}_{frame}.png"].tobytes() for frame in range(count)]
        assert len(set(frames)) == count, f"{family}: duplicate animation frames"


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    thumb = 112
    label_h = 28
    columns = 6
    rows = math.ceil(len(outputs) / columns)
    sheet = Image.new("RGBA", (columns * thumb, rows * (thumb + label_h)), (38, 41, 43, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        col = index % columns
        row = index // columns
        x = col * thumb
        y = row * (thumb + label_h)
        checker = Image.new("RGBA", (thumb, thumb), (225, 225, 222, 255))
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 14):
            for cx in range(0, thumb, 14):
                if (cx // 14 + cy // 14) % 2:
                    checker_draw.rectangle((cx, cy, cx + 13, cy + 13), fill=(181, 184, 184, 255))
        scale = max(1, min(thumb // sprite.width, thumb // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((thumb - preview.width) // 2, (thumb - preview.height) // 2))
        sheet.alpha_composite(checker, (x, y))
        relative = asset_path.relative_to(TEXTURES)
        draw.text((x + 3, y + thumb + 2), str(relative.parent)[-16:], fill=(185, 194, 197, 255), font=font)
        draw.text((x + 3, y + thumb + 13), relative.stem[:18], fill=(249, 249, 243, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    outputs = output_map()
    validate_frame_families(outputs)
    existing = [path for path in outputs if path.exists()]
    if existing:
        formatted = "\n".join(f"  {path.relative_to(ROOT)}" for path in existing)
        raise SystemExit(f"Refusing to overwrite existing Wind production textures:\n{formatted}")

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path, format="PNG", optimize=False, compress_level=9)

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA colors={colors} bbox={bbox} sha256={digest}")

    contact_sheet = Path("/tmp/elementalwands_wind_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
