#!/usr/bin/env python3
"""Export the cloud-white Wind workshop atlas to Minecraft's native pixel grids.

The authored atlas is preserved under art/wind/cloud-white. Each fixed cell is
sampled with nearest-neighbor filtering, transparent generation residue is
removed, and shading is snapped to a small cloud palette. No blur or dithering.
The worn wing is code-native artwork on the vanilla Elytra UV islands.
"""

from __future__ import annotations

import argparse
import hashlib
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/elementalwands/textures"
SOURCE = ROOT / "art/wind/cloud-white/source-atlas.png"
PREVIEW = ROOT / ".local-previews/wind-redesign/contact-sheet.png"

# Cool neutral shadows, pearl body, cloud-white highlights. No dark outline.
PALETTE = (
    (188, 205, 213, 255),
    (200, 216, 222, 255),
    (212, 225, 231, 255),
    (222, 232, 236, 255),
    (232, 239, 242, 255),
    (240, 245, 248, 255),
    (248, 250, 252, 255),
    (255, 255, 255, 255),
)
T = (0, 0, 0, 0)


def atlas_sprite(atlas: Image.Image, row: int, column: int, size: int) -> Image.Image:
    """Fixed cell bounds preserve the authored relative scale through animation."""
    # The authored blade row is shifted slightly right of the nominal grid.
    # Move its whole strip consistently so adjacent frame tips cannot bleed in.
    x_offset = round(atlas.width * 18 / 1254) if row == 7 else 0
    bounds = (round(column * atlas.width / 8) + x_offset, round(row * atlas.height / 8),
              round((column + 1) * atlas.width / 8) + x_offset, round((row + 1) * atlas.height / 8))
    padding = 1 if size == 16 else 2
    sampled = atlas.crop(bounds).resize((size - padding * 2, size - padding * 2), Image.Resampling.NEAREST)
    pixels = []
    for r, g, b, a in sampled.get_flattened_data():
        if a < 128:
            pixels.append(T)
        else:
            # Palette conversion is deterministic; source art stays untouched.
            pixels.append(min(PALETTE, key=lambda color:
                              (r - color[0]) ** 2 + (g - color[1]) ** 2 + (b - color[2]) ** 2))
    sampled.putdata(pixels)
    sprite = Image.new("RGBA", (size, size), T)
    sprite.paste(sampled, (padding, padding))
    return sprite


def make_zephyr_wings_worn() -> Image.Image:
    """Pearl cloud bands within the existing vanilla wing UV footprint."""
    image = Image.new("RGBA", (64, 32), T)
    draw = ImageDraw.Draw(image)
    silhouette = [(31, 0), (39, 0), (39, 2), (42, 2), (42, 4), (44, 4),
                  (44, 7), (46, 7), (46, 13), (45, 13), (45, 17), (43, 17),
                  (43, 20), (41, 20), (41, 23), (38, 23), (38, 20), (36, 20),
                  (36, 17), (34, 17), (34, 12), (33, 12), (33, 7), (32, 7)]
    draw.polygon(silhouette, fill=PALETTE[2])
    mask = image.getchannel("A")
    for y in range(24):
        for x in range(31, 47):
            if not mask.getpixel((x, y)):
                continue
            # Broad rounded cloud strata, with light upper faces and gray undersides.
            band = (y + round(2 * math.sin((x - 32) * .55))) % 7
            shade = (6, 7, 6, 5, 4, 3, 2)[band]
            if x <= 33:
                shade = max(1, shade - 2)
            image.putpixel((x, y), PALETTE[shade])
    # Narrow edge island used by the vanilla model.
    draw.line((22, 11, 22, 21), fill=PALETTE[3], width=1)
    for y in range(12, 21, 2):
        draw.point((22, y), fill=PALETTE[6])
    return image


WIND_FAMILIES = {
    "mote": (4, 16, 0),
    "crescent": (6, 32, 1),
    "air_ribbon": (6, 32, 2),
    "burst_ring": (6, 32, 3),
    "zephyr_impact": (8, 64, 4),
    "slipstream": (6, 32, 5),
    "shear_feather": (6, 32, 6),
}


def output_map() -> dict[Path, Image.Image]:
    with Image.open(SOURCE) as source:
        atlas = source.convert("RGBA")
    assert atlas.width == atlas.height and atlas.width >= 512, "Expected square 8x8 source atlas"
    generated: dict[Path, Image.Image] = {}
    for family, (count, size, row) in WIND_FAMILIES.items():
        for frame in range(count):
            generated[TEXTURES / f"particle/wind/{family}_{frame}.png"] = atlas_sprite(atlas, row, frame, size)
    for frame in range(6):
        generated[TEXTURES / f"entity/vacuum_blade_{frame}.png"] = atlas_sprite(atlas, 7, frame, 64)
    generated.update({
        TEXTURES / "item/zephyr_wings.png": atlas_sprite(atlas, 0, 4, 32),
        TEXTURES / "entity/equipment/wings/zephyr_wings.png": make_zephyr_wings_worn(),
        TEXTURES / "gui/ability/wind_primary.png": atlas_sprite(atlas, 0, 5, 32),
        TEXTURES / "gui/ability/wind_secondary.png": atlas_sprite(atlas, 0, 6, 32),
        TEXTURES / "gui/ability/wind_ultimate.png": atlas_sprite(atlas, 0, 7, 32),
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
        checker = Image.new("RGBA", (thumb, thumb), (45, 57, 67, 255))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, thumb, 14):
            for cx in range(0, thumb, 14):
                if (cx // 14 + cy // 14) % 2:
                    cd.rectangle((cx, cy, cx + 13, cy + 13), fill=(59, 73, 84, 255))
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
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--replace", action="store_true", help="replace only known Wind outputs")
    mode.add_argument("--check", action="store_true", help="verify outputs without writing files")
    args = parser.parse_args()
    outputs = output_map()
    validate_frame_families(outputs)
    legacy = TEXTURES / "entity/vacuum_blade.png"
    if legacy.exists() and not args.replace:
        raise SystemExit(f"Unexpected legacy Wind texture: {legacy.relative_to(ROOT)}")

    for path, sprite in outputs.items():
        if not args.check:
            differs = True
            if path.exists():
                with Image.open(path) as existing:
                    differs = existing.mode != "RGBA" or existing.size != sprite.size or existing.tobytes() != sprite.tobytes()
            if differs:
                if path.exists() and not args.replace:
                    raise SystemExit(f"Refusing to overwrite differing Wind texture: {path.relative_to(ROOT)}")
                path.parent.mkdir(parents=True, exist_ok=True)
                sprite.save(path, format="PNG", optimize=False, compress_level=9)
        colors, bbox, digest = validate(path, sprite)
        print(f"OK {path.relative_to(ROOT)} {sprite.width}x{sprite.height} RGBA "
              f"colors={colors} bbox={bbox} sha256={digest}")

    if args.replace and legacy.exists():
        legacy.unlink()
    if not args.check:
        PREVIEW.parent.mkdir(parents=True, exist_ok=True)
        make_contact_sheet(outputs, PREVIEW)
        print(f"CONTACT_SHEET {PREVIEW}")
    print(f"WIND_TOTAL {len(outputs)}")


if __name__ == "__main__":
    main()
