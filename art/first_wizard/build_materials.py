"""Bake the First Wizard's painted cube faces. Run with --install to write game textures,
or --check to compare the editable art exports and the installed textures without writing.
The 3-by-2 source painting is ordered ink, fire, wind / stone, nature, space.
build_ink.py and build_stone.py use the same painting for the body, wall and ink effects.
"""
import argparse
from functools import cache
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parent
ART = ROOT / "materials"
GAME = ROOT.parents[1] / "src/main/resources/assets/elementalwands/textures/misc"
PANELS = ("ink", "fire", "wind", "stone", "nature", "space")

# Row order matches the existing meshes. Large faces carry broad fields of colour;
# only a material's lightest value picks out the edge of the brushstroke.
PALETTES = {
    "fire": (
        "ffc65b ffda77 ffe79a fff0ac fff5c7",  # core
        "f59b32 ffaf35 ffc343 ffda5d ffe785",
        "c6521d de6222 f5822a ff9b32 ffb747",
        "812e22 9d3d21 bb5022 d96726 ef8131",
        "542925 6d2b22 8c3621 ae431e d96926",
        "353139 413c43 50494b 5d5654 6f6561",  # smoke
        "1b1720 26202a 352935 49343b 6f4039",
        "542329 823322 b64a20 df6821 ff9d33",
    ),
    "wind": (
        "a9d9e3 c0e6ec d6f0ed eff9ed f9fff4",  # gale edge
        "95c8d8 accfde c4e0e5 dcecee eaf5ec",
        "7294af 83a7bf 99bbcb b0cbd3 c5d8db",
        "68afb9 81c4ce 9adddc c6efea ebfbeb",
        "74b5be 89c6d1 a3d9dd c3e9e5 def2e9",
        "aebcca c1cdd7 d1dce2 e0e6e8 eff3eb",
        "b9c6ce cad6db d9e3e3 eaf1ed f5f7ee",
        "708097 8999ad 9eafbe b6c6cd d3dada",
    ),
    "nature": (
        "3c6436 4c7b3d 619247 81ab50 a8c96b",  # leaf
        "243b30 304a31 405f35 53763c 799347",
        "923c61 b05472 cf698c e98fab f3b3bc",
        "5a386b 6c447c 83599a a576ac c69cc8",
        "b27328 cd902d e8ae3e f9cc5e ffe497",
        "4a322b 624131 775334 927041 ad8d56",
        "78862f 929b32 abae3b c5c647 e5df71",
        "c3aa78 d1bc8d decc9c edddb2 f8ecd2",
    ),
    "space": (
        "121323 18182c 20203a 302943 443253",  # void
        "262037 302742 423057 56406d 715184",
        "412652 503269 67427f 855398 a66eac",
        "6c44a8 8151bd 965fd2 b782df d7aeed",
        "442e68 56387c 704999 8b5aae ab7bc6",
        "264a9c 3563bb 5082d3 70a5e6 a4d7f3",  # attraction
        "97374e b24453 cd5559 e57b70 f6ab92",  # repulsion
        "b3a8cf c5bbdf d8cce7 e7def0 f6eff4",
    ),
}


@cache
def levels(material):
    """Sample the painted pixel clusters on the native grid. Integer luma keeps the bake repeatable."""
    panel = PANELS.index(material)
    with Image.open(ART / "source.png") as source:
        width, height = source.size
        col, row = panel % 3, panel // 3
        tile = source.crop((col * width // 3, row * height // 2, (col + 1) * width // 3, (row + 1) * height // 2))
        rgb = np.asarray(tile.convert("RGB").resize((16, 16), Image.Resampling.NEAREST), dtype=np.int32)
    luma = (54 * rgb[:, :, 0] + 183 * rgb[:, :, 1] + 19 * rgb[:, :, 2]) // 256
    values = np.sort(luma.ravel())
    low, high = values[5], values[-6]
    if low >= high:
        raise ValueError(f"{material} is missing its painted value range")
    return ((np.clip(luma - low, 0, high - low) * 4 + (high - low) // 2) // (high - low)).astype(np.uint8)


def paint(indices, palette, alpha=255):
    colours = np.array([tuple(bytes.fromhex(colour)) for colour in palette.split()], dtype=np.uint8)
    tile = np.empty((16, 16, 4), dtype=np.uint8)
    tile[:, :, :3] = colours[indices]
    tile[:, :, 3] = alpha
    return tile


def atlas(material):
    result = Image.new("RGBA", (64, 128))
    for row, palette in enumerate(PALETTES[material]):
        # Fire rises, wind shears across the face. The turning geometry carries
        # stone-like astral facets and leaf veins, so their painted edges hold still.
        base = np.roll(levels(material), (row * 3, row * 5), axis=(0, 1))
        for frame in range(4):
            indices = base
            if material == "fire" and row < 6:
                indices = np.roll(base, -4 * frame, axis=0)
            elif material == "wind" and row < 5:
                indices = np.roll(base, 4 * frame, axis=1)
            elif material == "space" and row == 2:
                indices = np.roll(base, 4 * frame, axis=1)
            alpha = 148 + indices * 20 if material == "space" and row in (1, 2) else 255
            result.paste(Image.fromarray(paint(indices, palette, alpha)), (frame * 16, row * 16))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--install", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    images = {name: atlas(name) for name in PALETTES}
    # The planted flower already colours its vertices for stored charge, damage and wilt.
    images["bloom_grain"] = Image.fromarray(paint(levels("nature"), "afb3ae c3c8bd d5d9cd e8eadb f9f8e9"))
    for name, image in images.items():
        stream = BytesIO()
        image.save(stream, format="PNG", compress_level=9)
        data = stream.getvalue()
        for path in (ART / f"{name}.png", GAME / f"first_wizard_{name}.png"):
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    raise SystemExit(f"Stale {path.name}; run build_materials.py --install")
            elif path.parent == ART or args.install:
                path.write_bytes(data)
    print("First Wizard painted materials verified" if args.check else "Built First Wizard painted materials")


if __name__ == "__main__":
    main()
