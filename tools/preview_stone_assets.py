#!/usr/bin/env python3
"""Review actual Stone PNGs and vanilla armor UV faces, without launching Minecraft."""
from pathlib import Path
import argparse
import io
import subprocess

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BASE = Path("src/main/resources/assets/elementalwands/textures")
OUT = ROOT / "art/stone/natural-gray"
BEFORE_REF = "09eb1e8a017264dec30b0084f86d04bc68be5e06"
BEFORE_TEXTURES = None


def asset(name, before=False):
    path = BASE / name
    if before:
        if BEFORE_TEXTURES is not None:
            return Image.open(BEFORE_TEXTURES / name).convert("RGBA")
        return Image.open(io.BytesIO(subprocess.check_output(
            ["git", "show", f"{BEFORE_REF}:{path}"], cwd=ROOT))).convert("RGBA")
    return Image.open(ROOT / path).convert("RGBA")


def mannequin(before=False, back=False):
    """Flat front/back face projection of standard armor UVs; not custom geometry."""
    body = asset("entity/equipment/humanoid/titan_armor.png", before)
    legs = asset("entity/equipment/humanoid_leggings/titan_armor.png", before)
    im = Image.new("RGBA", (20, 34))
    d = ImageDraw.Draw(im)
    for rect in ((6, 1, 13, 8), (6, 9, 13, 20), (2, 9, 5, 20),
                 (14, 9, 17, 20), (6, 21, 9, 32), (10, 21, 13, 32)):
        d.rectangle(rect, fill=(57, 64, 69, 255))
    def face(tex, box, dest):
        im.alpha_composite(tex.crop(box), dest)
    hx, cx, ax, lx = (24, 32, 52, 12) if back else (8, 20, 44, 4)
    face(body, (hx, 8, hx+8, 16), (6, 1))
    face(body, (cx, 20, cx+8, 32), (6, 9))
    for dest in ((2, 9), (14, 9)):
        face(body, (ax, 20, ax+4, 32), dest)
    for x in (6, 10):
        face(legs, (lx, 20, lx+4, 32), (x, 21))
        face(body, (lx, 27, lx+4, 32), (x, 28))
    return im


def main():
    global OUT, BEFORE_TEXTURES
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before-textures", type=Path)
    parser.add_argument("--output", type=Path, default=OUT)
    args = parser.parse_args()
    OUT, BEFORE_TEXTURES = args.output, args.before_textures
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (1280, 1020), (27, 32, 36))
    d = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 17)
    small = ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 13)
    d.text((30, 22), "STONE / NATURAL GRAY", font=font, fill=(226, 233, 231))
    d.text((30, 52), "Actual production textures - nearest-neighbor preview", font=small, fill=(170, 184, 188))
    names = [("block/stone_spike.png", "SPIKE"), ("block/stone_wall.png", "WALL"),
             ("block/stone_wall_ready.png", "CRACKED WALL"), ("block/titan_dome.png", "DOME"),
             ("item/titan_sword.png", "SWORD")]
    for row, before in enumerate((True, False)):
        y = 135 + row*218
        d.text((30, y-33), "BEFORE" if before else "AFTER", font=font, fill=(226, 233, 231))
        for col, (name, label) in enumerate(names):
            x = 30+col*246
            tex = asset(name, before).resize((160, 160), Image.Resampling.NEAREST)
            sheet.paste(tex, (x, y), tex)
            d.text((x, y+168), label, font=small, fill=(185, 200, 201))
    d.text((30, 584), "ARMOR / FRONT + BACK", font=font, fill=(226, 233, 231))
    d.text((30, 611), "Flat vanilla UV projection; no in-game lighting or 3D silhouette changes.",
           font=small, fill=(170, 184, 188))
    for i, (before, back) in enumerate(((True, False), (True, True), (False, False), (False, True))):
        x = 35+i*210
        tex = mannequin(before, back).resize((180, 306), Image.Resampling.NEAREST)
        sheet.paste(tex, (x, 650), tex)
        label = ("BEFORE" if before else "AFTER") + (" / BACK" if back else " / FRONT")
        d.text((x, 970), label, font=small, fill=(185, 200, 201))
    for i, ability in enumerate(("primary", "secondary", "ultimate")):
        tex = asset(f"gui/ability/stone_{ability}.png").resize((96, 96), Image.Resampling.NEAREST)
        sheet.paste(tex, (1070, 650+i*112), tex)
    sheet.save(OUT / "before-after.png")


if __name__ == "__main__":
    main()
