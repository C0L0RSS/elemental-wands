#!/usr/bin/env python3
"""Generate the Vanilla Inferno Fire HUD package.

In-world fire intentionally references Minecraft's own animated fire models and
``minecraft:flame`` particle sprite. This generator owns only the three 32x32
ability icons and the exact retired legacy Fire files removed by ``--replace``.
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
SHADOW = (74, 20, 5, 255)
DEEP_ORANGE = (166, 45, 6, 255)
ORANGE = (242, 92, 8, 255)
GOLD = (255, 171, 18, 255)
YELLOW = (255, 225, 74, 255)
PALE = (255, 250, 188, 255)
WHITE_HOT = (255, 255, 232, 255)
NETHERRACK_DARK = (73, 31, 31, 255)
NETHERRACK = (111, 49, 49, 255)
NETHERRACK_LIGHT = (145, 61, 52, 255)
MAGMA_DARK = (51, 25, 19, 255)
MAGMA = (105, 35, 15, 255)


def canvas() -> Image.Image:
    return Image.new("RGBA", (32, 32), T)


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon(points, fill=fill)


def draw_flame(draw: ImageDraw.ImageDraw, x: int, base: int, height: int, width: int, phase: int) -> None:
    """Draw one clustered Minecraft-like fire tongue on the final pixel grid."""
    half = width // 2
    sway = (-1, 0, 1)[phase % 3]
    outer = [
        (x - half, base),
        (x - half, base - max(3, height // 3)),
        (x - max(1, half - 2), base - height // 2),
        (x - 1 + sway, base - height),
        (x + 2 + sway, base - height + max(3, height // 4)),
        (x + half, base - height // 2),
        (x + half, base),
    ]
    poly(draw, outer, SHADOW)
    poly(draw, [
        (x - half + 1, base),
        (x - max(1, half - 2), base - height // 2),
        (x + sway, base - height + 2),
        (x + max(1, half - 2), base - height // 2 + 1),
        (x + half - 1, base),
    ], DEEP_ORANGE)
    poly(draw, [
        (x - max(1, half - 3), base),
        (x - 1, base - height // 2),
        (x + sway, base - height + 5),
        (x + 2, base - height // 3),
        (x + max(1, half - 3), base),
    ], ORANGE)
    poly(draw, [
        (x - 1, base),
        (x, base - max(4, height // 2)),
        (x + 2, base - max(2, height // 3)),
        (x + 2, base),
    ], GOLD)
    if height >= 11:
        draw.rectangle((x, base - 4, x + 1, base - 2), fill=YELLOW)
        draw.point((x, base - 3), fill=WHITE_HOT)


def make_primary_icon() -> Image.Image:
    image = canvas()
    draw = ImageDraw.Draw(image)
    draw_flame(draw, 7, 27, 14, 9, 0)
    draw_flame(draw, 16, 27, 23, 12, 1)
    draw_flame(draw, 25, 27, 16, 9, 2)
    draw.rectangle((3, 27, 28, 29), fill=DEEP_ORANGE)
    draw.rectangle((6, 27, 26, 27), fill=GOLD)
    for x, y in ((3, 18), (10, 7), (23, 8), (29, 20)):
        draw.point((x, y), fill=YELLOW)
    return image


def make_secondary_icon() -> Image.Image:
    image = canvas()
    draw = ImageDraw.Draw(image)

    for y in range(23, 30, 3):
        for x in range(2, 30, 4):
            selector = (x * 5 + y * 3) % 3
            color = (NETHERRACK_DARK, NETHERRACK, NETHERRACK_LIGHT)[selector]
            draw.rectangle((x, y, min(30, x + 3), min(30, y + 2)), fill=color)

    draw_flame(draw, 16, 24, 20, 10, 1)
    draw_flame(draw, 10, 25, 13, 8, 0)
    draw_flame(draw, 22, 25, 13, 8, 2)
    draw_flame(draw, 5, 26, 8, 6, 1)
    draw_flame(draw, 27, 26, 8, 6, 0)
    draw.line((2, 24, 8, 23), fill=ORANGE, width=1)
    draw.line((24, 23, 30, 24), fill=ORANGE, width=1)
    return image


def make_ultimate_icon() -> Image.Image:
    image = canvas()
    draw = ImageDraw.Draw(image)

    # Long descending flame shell.
    poly(draw, [(3, 1), (11, 7), (14, 12), (20, 15), (16, 21), (8, 16)], SHADOW)
    poly(draw, [(5, 2), (12, 8), (14, 13), (18, 15), (15, 18), (10, 14)], ORANGE)
    poly(draw, [(7, 3), (12, 9), (13, 13), (16, 15), (14, 16), (11, 12)], YELLOW)
    draw.line((8, 3, 14, 14), fill=PALE, width=2)
    draw.point((9, 4), fill=WHITE_HOT)

    # Irregular magma-rock core, kept smaller than the surrounding fire.
    poly(draw, [(11, 14), (16, 10), (24, 12), (29, 18), (27, 26),
                (21, 30), (13, 27), (8, 21)], MAGMA_DARK)
    poly(draw, [(13, 15), (17, 12), (23, 14), (27, 18), (25, 23),
                (21, 27), (15, 25), (11, 21)], MAGMA)
    draw.line((12, 19, 17, 18, 19, 24, 24, 25), fill=ORANGE, width=3)
    draw.line((13, 19, 17, 18, 19, 23, 24, 24), fill=YELLOW, width=1)
    draw.line((21, 13, 20, 18, 25, 20), fill=GOLD, width=2)
    draw.point((18, 19), fill=WHITE_HOT)
    return image


def output_map() -> dict[Path, Image.Image]:
    return {
        TEXTURES / "gui/ability/fire_primary.png": make_primary_icon(),
        TEXTURES / "gui/ability/fire_secondary.png": make_secondary_icon(),
        TEXTURES / "gui/ability/fire_ultimate.png": make_ultimate_icon(),
    }


def retired_outputs() -> tuple[Path, ...]:
    families = {
        "ember": 4,
        "ash": 4,
        "flame_ribbon": 6,
        "impact_ring": 6,
        "meteor": 8,
        "pyre_fissure": 6,
        "pyre_front": 8,
        "meteor_warning": 8,
        "meteor_impact": 12,
    }
    paths = [
        TEXTURES / f"particle/fire/{family}_{frame}.png"
        for family, count in families.items()
        for frame in range(count)
    ]
    paths.extend(TEXTURES / f"entity/inferno_wave_{frame}.png" for frame in range(6))
    paths.extend([
        TEXTURES / "block/inferno_flame.png",
        TEXTURES / "block/inferno_flame.png.mcmeta",
        *(TEXTURES / f"block/pyre_coals_{frame}.png" for frame in range(4)),
        TEXTURES / "block/meteor_core.png",
    ])
    return tuple(paths)


def validate(path: Path, expected: Image.Image) -> tuple[int, tuple[int, int, int, int], str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == (32, 32), f"{path}: expected 32x32, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: differs from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        colors = reopened.getcolors(maxcolors=1024) or []
        visible = {rgba for _count, rgba in colors if rgba[3] > 0}
        assert len(visible) >= 6, f"{path}: only {len(visible)} visible colors"
        assert any(rgba[3] == 0 for _count, rgba in colors), f"{path}: missing transparency"
        digest = hashlib.sha256(reopened.tobytes()).hexdigest()[:12]
        bbox = reopened.getbbox()
    return len(visible), bbox, digest


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    scale = 8
    cell_width = 32 * scale
    label_height = 24
    sheet = Image.new("RGBA", (cell_width * 3, 32 * scale + label_height), (32, 26, 24, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        preview = sprite.resize((32 * scale, 32 * scale), Image.Resampling.NEAREST)
        sheet.alpha_composite(preview, (index * cell_width, 0))
        draw.text((index * cell_width + 6, 32 * scale + 5),
                  asset_path.stem, fill=(255, 235, 190, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--replace",
        action="store_true",
        help="replace only the three Fire HUD icons and remove the retired legacy Fire outputs",
    )
    args = parser.parse_args()
    outputs = output_map()

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        differs = True
        if path.exists():
            with Image.open(path) as existing:
                existing.load()
                differs = (
                    existing.mode != "RGBA"
                    or existing.size != image.size
                    or existing.tobytes() != image.tobytes()
                )
        if differs:
            if path.exists() and not args.replace:
                raise SystemExit(f"Refusing to overwrite differing Fire icon: {path.relative_to(ROOT)}")
            image.save(path, format="PNG", optimize=False, compress_level=9)

    if args.replace:
        for path in retired_outputs():
            if path.exists():
                path.unlink()

    for path, image in outputs.items():
        colors, bbox, digest = validate(path, image)
        print(
            f"OK {path.relative_to(ROOT)} 32x32 RGBA "
            f"colors={colors} bbox={bbox} sha256={digest}"
        )

    contact_sheet = Path("/tmp/elementalwands_fire_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"FIRE_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
