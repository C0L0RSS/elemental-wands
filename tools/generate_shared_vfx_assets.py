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
    # Native 36px carved walnut frame: directional grain, worn bevels and brass inlay.
    tile = canvas(36)
    d = ImageDraw.Draw(tile)
    d.polygon([(6,0),(29,0),(35,6),(35,29),(29,35),(6,35),(0,29),(0,6)], fill=(37,26,24,255))
    d.polygon([(6,1),(29,1),(34,6),(34,29),(29,34),(6,34),(1,29),(1,6)], fill=(88,53,33,255))
    palette=[(87,51,31,255),(108,65,37,255),(128,80,43,255),(151,99,53,255),(114,68,38,255)]
    for py in range(2,34):
        for px in range(2,34):
            if 6<=px<=29 and 6<=py<=29: continue
            if tile.getpixel((px,py))[3]==0: continue
            along=px if py<6 or py>29 else py
            across=py if py<6 or py>29 else px
            grain=(across*3 + along//(3+(across%3)) + kind)%len(palette)
            tile.putpixel((px,py),palette[grain])
    d.line([(5,2),(29,2),(32,5)], fill=(182,125,69,255))
    d.line([(2,6),(2,28)],fill=(164,106,57,255))
    d.line([(6,33),(29,33),(33,29),(33,6)],fill=(60,36,28,255),width=1)
    d.rectangle((5,5,30,30),fill=(48,32,28,255))
    d.rectangle((6,6,29,29),fill=(24,29,29,235))
    d.line((6,6,29,6),fill=(17,22,23,255))
    d.line((6,7,6,29),fill=(17,22,23,255))
    d.line((7,30,29,30),fill=(168,111,58,255))
    # Hand-cut metal corner straps rather than a continuous bright outline.
    for cx,cy,sx,sy in [(4,4,1,1),(31,4,-1,1),(4,31,1,-1),(31,31,-1,-1)]:
        d.line((cx,cy,cx+sx*3,cy),fill=(194,153,83,255))
        d.line((cx,cy,cx,cy+sy*3),fill=(153,111,56,255))
        d.point((cx,cy),fill=(231,202,136,255))
    # A tiny inset crystal and engraved slot-specific marks supply the magic.
    d.polygon([(17,0),(20,3),(17,6),(14,3)],fill=(57,42,30,255))
    d.polygon([(17,1),(19,3),(17,5),(15,3)],fill=(171,190,167,255) if ready else (87,100,91,255))
    d.line((16,2,17,2),fill=(237,240,204,255) if ready else (127,134,112,255))
    for mark in range(kind+1):
        mx=17-kind*2+mark*4
        d.line((mx,32,mx+1,31),fill=(208,170,103,255) if ready else (139,102,59,255))
    for py in (12,22):
        d.line([(3,py-1),(4,py),(3,py+1)],fill=(206,164,91,255) if ready else (134,94,50,255))
        d.line([(32,py-1),(31,py),(32,py+1)],fill=(141,99,53,255))
    image.alpha_composite(tile,(x,y))


def make_wand_hud() -> Image.Image:
    image = canvas(256)
    for index, x in enumerate((0, 85, 170)):
        draw_slot(image, x, 0, True, index)
        draw_slot(image, x, 80, False, index)
    d=ImageDraw.Draw(image)
    # 120x10 charge trough, native HUD coordinates; unused atlas space keeps asset counts stable.
    d.polygon([(4,160),(115,160),(119,164),(119,165),(115,169),(4,169),(0,165),(0,164)],fill=(41,29,25,255))
    d.polygon([(4,161),(115,161),(118,164),(115,168),(4,168),(1,164)],fill=(117,72,40,255))
    d.line((6,161,113,161),fill=(185,128,68,255))
    for gx in range(9,111,9):
        d.line((gx,162,gx+4,162),fill=(86,48,30,255))
        d.line((gx+2,167,gx+6,167),fill=(154,99,51,255))
    d.rectangle((6,162,113,167),fill=(48,34,29,255))
    d.rectangle((7,163,112,166),fill=(24,30,30,245))
    for cx in (3,116):
        d.line((cx,163,cx,166),fill=(212,174,104,255))
        d.point((cx,163),fill=(241,214,151,255))
    # Layered mineral fill; two rows distinguish the heavy-hit threshold without text.
    for row,colors in [(176,[(177,188,179),(123,140,132),(139,153,141),(76,94,87)]),
                       (180,[(235,231,194),(184,194,155),(203,209,169),(123,143,109)])]:
        for fy,color in enumerate(colors):
            d.line((0,row+fy,105,row+fy),fill=(*color,255))
        for fx in range(5,104,13):
            d.point((fx,row+2),fill=(*colors[0],255))
            d.point((fx+1,row+3),fill=(*colors[1],255))
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
