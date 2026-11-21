#!/usr/bin/env python3
"""Build the First Wizard's ink: the ink atlas its spells cool into, the veins under its swelling flank in the change,
and the ink over its wound in phase two.

    py -3 art/first_wizard/build_ink.py             write the art copies here (ink.png, veins.png, wound_*.png)
    py -3 art/first_wizard/build_ink.py --install   also write the game's copies under src/main/resources
    py -3 art/first_wizard/build_ink.py --check     write nothing; fail if any art or game copy differs

The ink is liquid, not Space's void glass: matte black with a violet rim, wet highlights, and the purples a spell
cools through between its element's colour and black. No stars, no glass frames.

Atlas (64 x 128, the block-fire kit's layout: eight 16-pixel rows, four frames each, read by client/blockink):
  0 ink      matte black ink, a slow downward run
  1 rim      ink with a violet rim (the outline that keeps the copy findable)
  2 violet   the hottest purple: where an element's colour first cools
  3 purple   the middle purple
  4 deep     deep purple, just before black
  5 gloss    wet ink with lilac highlights (drips, pools)
  6 mist     see-through ink (the afterimage, thin sheets); the only row with alpha below 255
  7 magenta  the bridge from fire's deep red into violet

Veins (64 x 64, the vanilla skin layout): drawn over the copy while its left flank swells in the change's cinematic,
on the player model a little larger than the skin, fading in as it swells. Branching veins of ink with violet cores
and a bruise under the skin, on the torso's left half (its side face and the left halves of the front and back, rows
the swelling's lumps share: FirstWizardRenderer's lumps take the torso's texture there); everything else transparent.

Wounds (64 x 64, the vanilla skin layout, one per arm width): drawn over the torn copy once the swelling has burst
(and through phase two), on the player model a little larger than the skin. The torso's left half is matte ink, wet
in places, ragged with violet where it meets the skin; everything else is transparent, so the rest of the skin is the
player's own. The wound is always her left, whichever hand the skin's owner uses (the developer, Nov 19). (The
developer asked for the skin left alone, Nov 18: there is no coat over the whole copy any more.)

Everything is integer arithmetic on a fixed hash, so both machines write the same bytes. Requires Pillow and NumPy.
"""

import argparse
import io
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
GAME = REPO / "src/main/resources/assets/elementalwands/textures"
OUTPUTS = {
    "ink.png": GAME / "misc/first_wizard_ink.png",
    "veins.png": GAME / "entity/first_wizard_veins.png",
}
for _model in ("wide", "slim"):
    OUTPUTS[f"wound_{_model}.png"] = GAME / f"entity/first_wizard_wound_{_model}.png"

ROWS = [
    # name, palette (dark to light), motion
    ("ink", ["#09070d", "#0e0b14", "#130f1b", "#1a1424", "#2a1d3d"], "run"),
    ("rim", ["#0a080f", "#110d18", "#4d2a8a", "#7a43d6", "#a273f2"], "rim"),
    ("violet", ["#8e4ae6", "#a462f2", "#b77cf8", "#cb9cfc", "#e4c8ff"], "rise"),
    ("purple", ["#4f1d8c", "#5f27a4", "#6f32b8", "#7f40c8", "#9558dc"], "rise"),
    ("deep", ["#1e0b3a", "#271047", "#311556", "#3c1b66", "#4b2580"], "rise"),
    ("gloss", ["#07060a", "#0d0a12", "#15101d", "#5b4682", "#c4b0ec"], "run"),
    ("mist", ["#120c1c", "#170f24", "#1d132e", "#3a2366", "#6a42b8"], "mist"),
    ("magenta", ["#7a1f52", "#922a66", "#a93a7c", "#bf4d93", "#d76bb0"], "rise"),
]
MIST_ALPHA = [120, 135, 150, 190, 215]


def rnd(*values):
    """A fixed integer hash in 0..65535."""
    h = 2166136261
    for v in values:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def pattern(row, name):
    """A 16 x 16 grid of palette indices 0..4 for one row."""
    grid = np.zeros((16, 16), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            # Two octaves of blocky value noise from the hash: 4-pixel cells and 2-pixel cells.
            coarse = rnd(row, x // 4, y // 4) % 5
            fine = rnd(row + 50, x // 2, y // 2) % 3
            grain = rnd(row + 99, x, y) % 7
            value = coarse + fine - 1 + (1 if grain == 0 else 0) - (1 if grain == 1 else 0)
            grid[y, x] = max(0, min(4, value))
    if name in ("ink", "gloss", "rim", "mist"):
        # Liquid, never glass: a dark body with the light shades only in runs down the face, no frames, no specks.
        grid = np.minimum(grid, 2 if name in ("ink", "gloss") else 1)
        for x in range(16):
            if rnd(row, x, 3) % 3:
                continue
            top, length = rnd(row, x, 4) % 16, 2 + rnd(row, x, 5) % 4
            for k in range(length):
                grid[(top + k) % 16, x] = 3 if name in ("ink", "gloss") else 2
            if name in ("ink", "gloss") and rnd(row, x, 6) % 2 == 0:
                grid[(top + length) % 16, x] = 4 if name == "gloss" else 3
    if name == "gloss":
        # Wet highlights: a few short level streaks, as light lies on a liquid surface.
        for i in range(3):
            y, x0 = 2 + rnd(row, i, 8) % 12, rnd(row, i, 9) % 13
            grid[y, x0:x0 + 3] = 3
            grid[y, x0 + 1] = 4
    if name == "rim":
        # The violet rim is a meniscus along the top of each face, running down in drips of varied length.
        grid[0, :] = 4
        grid[1, :] = 3
        for x in range(16):
            for k in range(rnd(row, x, 10) % 5):
                grid[2 + k, x] = 3 if k < 2 else 2
    if name == "mist":
        grid[0, :] = np.maximum(grid[0, :], 3)
        grid[0, ::5] = 4
    return grid


def frame(base, motion, phase):
    """Permutations only, so a row's colours never pulse; every loop closes after four frames."""
    if motion in ("rise", "run"):
        step = -4 if motion == "rise" else 4
        out = base.copy()
        for x in range(16):
            sway = (0, 1, 0, -1)[phase] * (0, 0, 1, 1, 0, 0, -1, -1)[x % 8]
            out[:, x] = np.roll(base[:, x], step * phase + sway)
        return out
    # The rim keeps its meniscus (the top two rows) and the mist its top line; what is below runs down the face.
    top = 2 if motion == "rim" else 1
    out = base.copy()
    out[top:, :] = np.roll(base[top:, :], (0, 4, 7, 11)[phase], axis=0)
    return out


def build_atlas():
    atlas = np.zeros((128, 64, 4), dtype=np.uint8)
    for row, (name, palette, motion) in enumerate(ROWS):
        colours = np.array([tuple(bytes.fromhex(c.lstrip("#"))) for c in palette], dtype=np.uint8)
        base = pattern(row, name)
        counts = np.bincount(base.ravel(), minlength=5)
        frames = []
        for phase in range(4):
            indices = frame(base, motion, phase)
            assert np.array_equal(np.bincount(indices.ravel(), minlength=5), counts), name
            pixels = colours[indices]
            alpha = np.array(MIST_ALPHA, dtype=np.uint8)[indices] if name == "mist" else np.full((16, 16), 255, np.uint8)
            atlas[row * 16:(row + 1) * 16, phase * 16:(phase + 1) * 16, :3] = pixels
            atlas[row * 16:(row + 1) * 16, phase * 16:(phase + 1) * 16, 3] = alpha
            frames.append(indices.tobytes())
        assert len(set(frames)) == 4, f"{name}: all four frames must differ"
    return Image.fromarray(atlas, "RGBA")


INK = (0x0d, 0x0a, 0x12)
WET = (0x1c, 0x15, 0x29)
TIP = (0x6a, 0x3c, 0xc0)
RIM = (0x8a, 0x52, 0xe6)


def build_wound(slim, side="left"):
    """The ink over the torn copy's wound (phase two): its torso's cut side, `side` the player's "left" (always, now).

    The body is drawn narrowed toward the wand arm, so the whole torso texture lands on what is left of it; the half on
    the wound's side becomes matte ink, wet in places, ragged with violet where it meets the skin, so the missing side
    reads as ink and not as shirt. Everything else is transparent. Slim and wide differ only in name: the torso is the
    same in both layouts."""
    coat = np.zeros((64, 64, 4), np.uint8)
    u, v, w, h, d = 16, 16, 8, 12, 4
    # Columns of the body's faces that lie on that side of the player: the side face itself and half the front and back
    # (the front is seen from before, so the player's left is its right half; the back, from behind, the other way).
    if side == "left":
        whole, front, back = (u + d + w, d), (u + d + w // 2, w // 2), (u + 2 * d + w, w // 2)
        edge_front, edge_back = u + d + w // 2, u + 2 * d + w + w // 2 - 1
    else:
        whole, front, back = (u, d), (u + d, w // 2), (u + 2 * d + w + w // 2, w // 2)
        edge_front, edge_back = u + d + w // 2 - 1, u + 2 * d + w + w // 2
    for x0, width in (whole, front, back):
        for x in range(x0, x0 + width):
            for y in range(v + d, v + d + h):
                wet = rnd(7, x, y) % 9 == 0
                coat[y, x, :3] = WET if wet else INK
                coat[y, x, 3] = 252
    # Top and bottom of the torso on that half: ink too.
    for y in range(v, v + d):
        for x in range(u + d, u + d + 2 * w):
            if (side == "left") == ((x - (u + d)) % w >= w // 2):
                coat[y, x, :3] = INK
                coat[y, x, 3] = 252
    # The cut's ragged violet edge, down the front and back where the ink meets the skin.
    for x in (edge_front, edge_back):
        for y in range(v + d, v + d + h):
            if rnd(11, x, y) % 3:
                coat[y, x, :3] = RIM if rnd(12, x, y) % 2 else TIP
                coat[y, x, 3] = 235
    return Image.fromarray(coat, "RGBA")


VEIN = (0x12, 0x0d, 0x1b)
VEIN_CORE = (0x6a, 0x3c, 0xc0)
BRUISE = (0x2c, 0x14, 0x46)
# The 8 neighbours, in turn order, for a vein's walk.
STEPS = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]


def on_left_flank(x, y):
    """Whether a texel of the skin layout is on the torso's left half: the rows the swelling shows and the torso round it."""
    return (24 <= x < 36 and 20 <= y < 32) or (20 <= x < 24 and 22 <= y < 28) or (24 <= x < 32 and 16 <= y < 22)


def build_veins():
    """Veins of ink under the skin of the swelling flank: a bruise round its middle (the side face, at rib height) and
    branching veins walking out from it onto the front and back, dark with violet cores near the middle."""
    coat = np.zeros((64, 64, 4), np.uint8)
    cx, cy = 30, 24
    for y in range(64):
        for x in range(64):
            if not on_left_flank(x, y):
                continue
            d2 = (x - cx) ** 2 + 2 * (y - cy) ** 2
            if d2 <= 10:
                coat[y, x, :3] = BRUISE
                coat[y, x, 3] = 36 + (10 - d2) * 6
    for branch in range(8):
        x, y = cx - 1 + rnd(31, branch, 0) % 3, cy - 1 + rnd(31, branch, 1) % 3
        heading = rnd(31, branch, 2) % 8
        length = 5 + rnd(31, branch, 3) % 8
        for step in range(length):
            if on_left_flank(x, y):
                core = step < 3 and rnd(32, branch, step) % 3 != 0
                coat[y, x, :3] = VEIN_CORE if core else VEIN
                coat[y, x, 3] = 240 if core else 225 - step * 6
            turn = rnd(33, branch, step) % 6
            heading = (heading + (1 if turn == 0 else -1 if turn == 1 else 0)) % 8
            dx, dy = STEPS[heading]
            x, y = x + dx, y + dy
    return Image.fromarray(coat, "RGBA")


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--install", action="store_true", help="also write the game's copies")
    group.add_argument("--check", action="store_true", help="write nothing; fail if any copy differs")
    args = parser.parse_args()
    built = {"ink.png": png_bytes(build_atlas()), "veins.png": png_bytes(build_veins())}
    for model in ("wide", "slim"):
        built[f"wound_{model}.png"] = png_bytes(build_wound(model == "slim"))
    failures = []
    for name, data in built.items():
        for path in (ROOT / name, OUTPUTS[name]):
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    failures.append(str(path.relative_to(REPO)))
            elif path.parent == ROOT or args.install:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    if failures:
        raise SystemExit("MISMATCH: " + ", ".join(failures) + "; run build_ink.py --install")
    print(("CHECK PASSED" if args.check else "INSTALLED" if args.install else "BUILT") + ": " + ", ".join(built))


if __name__ == "__main__":
    main()
