#!/usr/bin/env python3
"""Build original block-fire atlas and review sheet; --check is read-only.

Source: atlas_source.json (authored 16x16 index grids and finite palettes).
Requires Python 3, Pillow and NumPy. Run from any working directory.
"""

import argparse
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
NAMES = ("core", "yellow", "orange", "deep", "ember", "smoke", "char", "magma")
LUMA = np.array([0.2126, 0.7152, 0.0722])


def frame_indices(base, row, frame):
    """Only permutations: RGB sums and palette counts cannot pulse.

    Rising materials advance four pixels upward per frame on a 16-pixel torus.
    A gentle column shear changes the tongues' contour. Both transformations
    close after four frames, including the 3 -> 0 transition.
    Rock stays anchored: warm seam colors move along the existing crack mask.
    """
    phase = frame % 4
    if row["motion"] == "rise":
        result = np.roll(base, row["offset"], axis=1).copy()
        sway = (0, 1, 0, -1)[phase]
        for x in range(16):
            contour = (0, 0, 1, 1, 0, 0, -1, -1)[x % 8]
            result[:, x] = np.roll(result[:, x], -4 * phase + sway * contour)
        return result
    if row["motion"] == "seams":
        result = base.copy()
        threshold = 4 if row["name"] == "char" else 3
        # Bottom-to-top order makes hot packets travel up the branching seams.
        sites = sorted(zip(*np.where(base >= threshold)), key=lambda p: (-p[0], p[1]))
        values = np.array([base[y, x] for y, x in sites])
        values = np.roll(values, phase * len(values) // 4)
        for (y, x), value in zip(sites, values):
            result[y, x] = value
        return result
    raise ValueError(f"Unknown motion: {row['motion']}")


def build_atlas(source):
    assert tuple(row["name"] for row in source["rows"]) == NAMES
    atlas = np.full((128, 64, 4), 255, dtype=np.uint8)
    stats = []
    for row_number, row in enumerate(source["rows"]):
        grid = source["patterns"][row["pattern"]]
        assert len(grid) == 16 and all(len(line) == 16 for line in grid)
        base = np.array([[int(pixel) for pixel in line] for line in grid], dtype=np.uint8)
        palette = np.array([tuple(bytes.fromhex(color.lstrip("#"))) for color in row["palette"]], dtype=np.uint8)
        assert palette.shape[1] == 3 and int(base.max()) < len(palette)
        reference_counts = np.bincount(base.ravel(), minlength=len(palette))
        frames = []
        for frame in range(4):
            indices = frame_indices(base, row, frame)
            assert np.array_equal(np.bincount(indices.ravel(), minlength=len(palette)), reference_counts)
            pixels = palette[indices]
            frames.append(pixels)
            atlas[row_number * 16:(row_number + 1) * 16, frame * 16:(frame + 1) * 16, :3] = pixels
        assert len({pixels.tobytes() for pixels in frames}) == 4, "All four frames must differ"
        assert np.array_equal(frame_indices(base, row, 0), frame_indices(base, row, 4))
        means = np.array([frame.mean(axis=(0, 1)) for frame in frames])
        assert np.array_equal(means, np.repeat(means[:1], 4, axis=0))
        # Catch a bad wrap that changes far more pixels than ordinary steps.
        changes = [np.any(frames[i] != frames[(i + 1) % 4], axis=2).mean() for i in range(4)]
        assert max(changes) - min(changes) < 0.16
        stats.append((row["name"], means[0], float(means[0] @ LUMA), changes))
    heat_steps = -np.diff([stat[2] for stat in stats[:5]])
    assert np.all((heat_steps > 35) & (heat_steps < 60)), heat_steps
    assert np.all(atlas[:, :, 3] == 255)
    return Image.fromarray(atlas), stats


def cube(sheet, tile, origin):
    """Integer isometric pixel quads: top/front/right = 1.0 / 0.8 / 0.6.

    Each face maps the entire 16x16 tile once. No antialiasing or filtering.
    """
    ox, oy = origin
    draw = ImageDraw.Draw(sheet)
    pixels = np.asarray(tile)[:, :, :3]
    faces = (
        ((ox, oy), (3, -2), (3, 2), 1.0),
        ((ox, oy), (3, 2), (0, 4), 0.8),
        ((ox + 48, oy + 32), (3, -2), (0, 4), 0.6),
    )
    for (x0, y0), u, v, brightness in faces:
        for y in range(16):
            for x in range(16):
                px, py = x0 + x * u[0] + y * v[0], y0 + x * u[1] + y * v[1]
                points = [(px, py), (px + u[0], py + u[1]),
                          (px + u[0] + v[0], py + u[1] + v[1]), (px + v[0], py + v[1])]
                color = tuple(np.rint(pixels[y, x].astype(float) * brightness).astype(int))
                draw.polygon(points, fill=color)


def build_preview(atlas, source, stats):
    sheet = Image.new("RGB", (1040, 1184), "#13171e")
    draw = ImageDraw.Draw(sheet)
    # Pillow's bundled default font avoids machine-specific font paths.
    title = ImageFont.load_default(size=24)
    label = ImageFont.load_default(size=16)
    small = ImageFont.load_default(size=13)
    draw.text((24, 18), "BLOCK FIRE  /  MATERIAL ATLAS", font=title, fill="#f4ebd7")
    draw.text((24, 51), "16 px clusters  /  4-frame loop  /  opaque RGBA  /  no bloom", font=label, fill="#b4b8bf")
    for frame in range(4):
        draw.text((24 + frame * 128, 90), f"FRAME {frame + 1:02d}", font=small, fill="#c6cbd2")
    draw.text((558, 90), "ROW / PALETTE", font=small, fill="#c6cbd2")
    draw.text((813, 90), "1.0 / 0.8 / 0.6", font=small, fill="#c6cbd2")
    # Exactly 8x; no separators are inserted into the atlas.
    sheet.paste(atlas.resize((512, 1024), Image.Resampling.NEAREST).convert("RGB"), (24, 120))
    for row_number, row in enumerate(source["rows"]):
        y = 120 + row_number * 128
        draw.text((558, y + 15), f"{row_number}  {row['name'].upper()}", font=label, fill="#f4ebd7")
        draw.text((558, y + 42), f"Mean Y' {stats[row_number][2]:.1f}", font=small, fill="#a0a8b4")
        draw.text((558, y + 62), "Frame RGB drift: 0", font=small, fill="#a0a8b4")
        for i, color in enumerate(row["palette"]):
            draw.rectangle((558 + i * 29, y + 87, 582 + i * 29, y + 106), fill=color)
        tile = atlas.crop((0, row_number * 16, 16, (row_number + 1) * 16))
        cube(sheet, tile, (830, y + 32))
    draw.text((24, 1159), "Original authored patterns  |  Atlas shown at 8x nearest-neighbor  |  Cube faces use one complete tile", font=small, fill="#a0a8b4")
    return sheet


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify both PNG files without writing")
    args = parser.parse_args()
    source = json.loads((ROOT / "atlas_source.json").read_text())
    atlas, stats = build_atlas(source)
    outputs = {"blockfire.png": atlas, "blockfire_preview.png": build_preview(atlas, source, stats)}
    failures = []
    for name, output in outputs.items():
        path = ROOT / name
        data = png_bytes(output)
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                failures.append(name)
        else:
            path.write_bytes(data)
    for name, rgb, luma, changes in stats:
        print(f"{name:6s} mean RGB={tuple(round(float(v), 2) for v in rgb)} Y'={luma:.2f}; drift=0; loop changes="
              + "/".join(f"{change:.0%}" for change in changes))
    if failures:
        raise SystemExit("MISMATCH: " + ", ".join(failures) + "; run build_atlas.py to rebuild")
    print(("CHECK PASSED" if args.check else "BUILT") + ": blockfire.png (64x128 RGBA, alpha=255), blockfire_preview.png")


if __name__ == "__main__":
    main()
