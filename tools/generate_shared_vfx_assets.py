#!/usr/bin/env python3
"""Generate the two shared Elemental Wands UI/item textures deterministically."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/elementalwands/textures"

T = (0, 0, 0, 0)
CHARCOAL = (18, 19, 20, 255)
WOOD_DARK = (38, 28, 24, 255)
WOOD = (74, 49, 35, 255)
WOOD_LIGHT = (116, 78, 50, 255)
IRON_DARK = (48, 49, 48, 255)
IRON = (94, 93, 87, 255)
IRON_LIGHT = (143, 139, 126, 255)
BONE_DARK = (154, 144, 122, 255)
BONE = (210, 201, 177, 255)
BONE_LIGHT = (245, 237, 211, 255)


def canvas(size: int | tuple[int, int]) -> Image.Image:
    if isinstance(size, int):
        size = (size, size)
    return Image.new("RGBA", size, T)


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)


def make_wizard_wand() -> Image.Image:
    image = canvas(16)
    draw = ImageDraw.Draw(image)
    poly(draw, [(2, 14), (4, 15), (12, 6), (10, 4)], CHARCOAL)
    poly(draw, [(3, 13), (4, 14), (11, 6), (10, 5)], WOOD)
    draw.line((4, 12, 9, 7), fill=WOOD_LIGHT, width=1)
    draw.point((7, 10), fill=WOOD_DARK)
    draw.rectangle((4, 11, 6, 13), fill=IRON_DARK)
    draw.point((5, 12), fill=IRON_LIGHT)
    poly(draw, [(9, 5), (9, 2), (11, 3), (12, 0), (13, 3), (15, 2),
                (14, 6), (12, 8)], CHARCOAL)
    poly(draw, [(10, 5), (10, 3), (11, 4), (12, 1), (13, 4), (14, 3),
                (13, 6), (12, 7)], IRON)
    # Fractured bone-white crystal prongs; there is no affinity-colored core.
    poly(draw, [(11, 5), (12, 1), (13, 4), (12, 7)], BONE_DARK)
    draw.line((12, 2, 12, 5), fill=BONE, width=1)
    draw.point((12, 3), fill=BONE_LIGHT)
    draw.point((10, 3), fill=IRON_LIGHT)
    draw.point((13, 5), fill=BONE)
    return image


def draw_slot(image: Image.Image, x: int, y: int, ready: bool, kind: int) -> None:
    draw = ImageDraw.Draw(image)
    frame = IRON if not ready else BONE_DARK
    light = IRON_LIGHT if not ready else BONE_LIGHT
    dark = CHARCOAL if not ready else IRON_DARK
    poly(draw, [(x + 4, y), (x + 31, y), (x + 35, y + 4), (x + 35, y + 31),
                (x + 31, y + 35), (x + 4, y + 35), (x, y + 31), (x, y + 4)], dark)
    poly(draw, [(x + 5, y + 2), (x + 30, y + 2), (x + 33, y + 5),
                (x + 33, y + 30), (x + 30, y + 33), (x + 5, y + 33),
                (x + 2, y + 30), (x + 2, y + 5)], frame)
    draw.rectangle((x + 5, y + 5, x + 30, y + 30), fill=(18, 19, 20, 215))
    draw.line((x + 5, y + 5, x + 30, y + 5), fill=light, width=1)
    draw.line((x + 5, y + 5, x + 5, y + 30), fill=light, width=1)
    draw.line((x + 5, y + 30, x + 30, y + 30), fill=dark, width=2)
    draw.line((x + 30, y + 5, x + 30, y + 30), fill=dark, width=2)
    if kind == 0:
        draw.rectangle((x + 15, y + 1, x + 20, y + 3), fill=light)
    elif kind == 1:
        draw.rectangle((x + 9, y + 1, x + 13, y + 3), fill=light)
        draw.rectangle((x + 22, y + 1, x + 26, y + 3), fill=light)
    else:
        poly(draw, [(x + 12, y + 3), (x + 15, y), (x + 18, y + 3),
                    (x + 21, y), (x + 24, y + 3)], light)


def make_wand_hud() -> Image.Image:
    # Existing atlas dimensions and UV layout are deliberately preserved.
    image = canvas(256)
    for index, x in enumerate((0, 85, 170)):
        draw_slot(image, x, 0, True, index)
        draw_slot(image, x, 80, False, index)
    draw = ImageDraw.Draw(image)
    x, y, w, h = 29, 184, 201, 22
    poly(draw, [(x + 4, y), (x + w - 5, y), (x + w - 1, y + 4),
                (x + w - 1, y + h - 5), (x + w - 5, y + h - 1),
                (x + 4, y + h - 1), (x, y + h - 5), (x, y + 4)], CHARCOAL)
    draw.rectangle((x + 3, y + 3, x + w - 4, y + h - 4), fill=IRON_DARK)
    draw.rectangle((x + 6, y + 6, x + w - 7, y + h - 7), fill=(18, 19, 20, 230))
    for pip in range(13):
        px = x + 10 + pip * 14
        draw.rectangle((px, y + 9, px + 2, y + 12),
                       fill=BONE if pip in (0, 12) else IRON_LIGHT)
    return image


def output_map() -> dict[Path, Image.Image]:
    return {
        TEXTURES / "item/wizard_wand.png": make_wizard_wand(),
        TEXTURES / "gui/wand_hud_v2.png": make_wand_hud(),
    }


def validate(path: Path, expected: Image.Image) -> tuple[int, str]:
    with Image.open(path) as reopened:
        reopened.load()
        assert reopened.mode == "RGBA", f"{path}: expected RGBA, got {reopened.mode}"
        assert reopened.size == expected.size, f"{path}: expected {expected.size}, got {reopened.size}"
        assert reopened.tobytes() == expected.tobytes(), f"{path}: differs from deterministic source"
        assert reopened.getbbox() is not None, f"{path}: empty image"
        colors = reopened.getcolors(maxcolors=reopened.width * reopened.height) or []
        visible = {rgba for _count, rgba in colors if rgba[3] > 0}
        assert len(visible) >= 8, f"{path}: insufficient shared material detail"
        return len(visible), hashlib.sha256(reopened.tobytes()).hexdigest()[:12]


def make_contact_sheet(outputs: dict[Path, Image.Image], path: Path) -> None:
    sheet = Image.new("RGBA", (320, 288), (31, 28, 30, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, (asset_path, sprite) in enumerate(outputs.items()):
        x = index * 160
        checker = Image.new("RGBA", (160, 256), (215, 212, 207, 255))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, 256, 16):
            for cx in range(0, 160, 16):
                if (cx // 16 + cy // 16) % 2:
                    cd.rectangle((cx, cy, cx + 15, cy + 15), fill=(164, 161, 160, 255))
        scale = max(1, min(160 // sprite.width, 256 // sprite.height))
        preview = sprite.resize((sprite.width * scale, sprite.height * scale), Image.Resampling.NEAREST)
        checker.alpha_composite(preview, ((160 - preview.width) // 2, (256 - preview.height) // 2))
        sheet.alpha_composite(checker, (x, 0))
        draw.text((x + 4, 264), asset_path.stem, fill=(247, 236, 218, 255), font=font)
    sheet.convert("RGB").save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="replace only this script's two known output paths")
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
                raise SystemExit(f"Refusing to overwrite differing shared texture: {path.relative_to(ROOT)}")
            image.save(path, format="PNG", optimize=False, compress_level=9)
    for path, image in outputs.items():
        colors, digest = validate(path, image)
        print(f"OK {path.relative_to(ROOT)} {image.width}x{image.height} RGBA "
              f"colors={colors} sha256={digest}")
    contact_sheet = Path("/tmp/elementalwands_shared_vfx_contact_sheet.png")
    make_contact_sheet(outputs, contact_sheet)
    print(f"CONTACT_SHEET {contact_sheet}")
    print(f"SHARED_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
