#!/usr/bin/env python3
"""Build the Soul Bolt model, pixel atlas, animation, and local browser page.

Default output is review-only; --install exports resources, --check verifies them. Front is -Z; dimensions use 16 model
pixels per block, matching the Hollow Necromancer's GeckoLib geometry.
"""

import argparse
import base64
import io
import json
import math
import random
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CANDIDATE = HERE / "candidate"
PREVIEW = ROOT / ".local-previews" / "soul-bolt"

BONE = [(62, 53, 40), (104, 92, 70), (146, 133, 104),
        (184, 170, 136), (214, 202, 168), (236, 228, 202)]
SOUL = [(12, 58, 66), (26, 128, 138), (72, 214, 222),
        (170, 250, 255), (240, 255, 255)]
VOID = [(4, 3, 6), (9, 7, 12), (15, 12, 19)]

BONES = [
    {"name": "skull", "pivot": [0, 0, 0], "cubes": []},
    {"name": "jaw", "parent": "skull", "pivot": [0, -1, 2.4], "cubes": []},
]
CUBES = []


def cube(name, bone, center, size, material="bone", *, cracks=False):
    raw = {"origin": [round(c - s / 2, 3) for c, s in zip(center, size)],
           "size": list(size)}
    BONES[bone]["cubes"].append(raw)
    CUBES.append((name, raw, material, cracks))


# The cranium is built from stepped plates rather than a single skull cube.
cube("rear cranium", 0, (0, 1, 1.1), (6, 5, 5), cracks=True)
cube("crown", 0, (0, 3.8, .7), (6, 1, 4), cracks=True)
cube("forehead plate", 0, (0, 2.55, -1.7), (6, 1.3, 2), cracks=True)
cube("facial bone", 0, (0, .1, -1.85), (5.8, 3.5, .8))
cube("upper jaw", 0, (0, -1.2, -2.25), (4.9, 1.35, 1.15))
for side, label in ((-1, "right"), (1, "left")):
    cube(f"{label} temple", 0, (side * 3.25, 1.1, .5), (2, 4, 4), cracks=True)
    cube(f"{label} brow ridge", 0, (side * 1.75, 1.65, -2.55), (2.65, .75, 1.15), cracks=True)
    cube(f"{label} eye socket", 0, (side * 1.7, .65, -2.3), (2.05, 1.55, .24), "void")
    cube(f"{label} eye", 0, (side * 1.7, .65, -2.46), (.8, .65, .18), "soul")
    cube(f"{label} outer orbit", 0, (side * 2.95, .5, -2.45), (.75, 1.9, 1.15))
    cube(f"{label} cheekbone", 0, (side * 2.2, -.5, -2.65), (2.25, .7, 1.1), cracks=True)
    cube(f"{label} cheek arch", 0, (side * 3, -.75, -1.6), (.8, .75, 2.4))
    cube(f"{label} rear shard", 0, (side * 3.6, 1.35, 2.3), (1.2, 2.1, 1.35), cracks=True)
    cube(f"{label} jaw hinge", 1, (side * 2.7, -1.9, 1.8), (1.05, 2.7, 1.8))
    cube(f"{label} mandible upright", 1, (side * 2.65, -3.1, 1.35), (1.05, 2.7, 2.4), cracks=True)
    cube(f"{label} long jaw arm", 1, (side * 2.45, -4.15, -.65), (1.0, 1.35, 6.9), cracks=True)

cube("nasal bridge", 0, (0, .35, -2.5), (1.4, 1.9, .65))
cube("upper teeth bar", 0, (0, -1.9, -2.9), (4.8, .7, 1.1))
cube("upper palate", 0, (0, -1.7, -1.55), (4.8, .8, 3.75))
for i in range(5):
    cube(f"upper tooth {i+1}", 0, ((i - 2) * .92, -2.5, -3.1), (.64, .75, .75), "tooth")
cube("jaw chin", 1, (0, -4.5, -3.95), (5.9, 1.1, 1.1), cracks=True)
cube("jaw underside", 1, (0, -4.55, -.5), (4.9, .7, 7.3))
for i in range(6):
    cube(f"lower tooth {i+1}", 1, ((i - 2.5) * .85, -3.5, -3.75), (.65, 1.1, .8), "tooth")
for side, label in ((-1, "right"), (1, "left")):
    for i in range(2):
        cube(f"{label} side tooth {i+1}", 1, (side * 2.4, -3.35, -2.4+i*1.3), (.65, .9, .65), "tooth")
cube("mouth soul fissure", 1, (0, -4.16, -1.1), (.3, .1, 2.5), "soul")
cube("mouth soul branch front", 1, (-.35, -4.16, -1.9), (.9, .1, .3), "soul")
cube("mouth soul branch rear", 1, (.35, -4.16, -.2), (.9, .1, .3), "soul")

# The preview and exported animation share this authored bite cycle. Positive
# GeckoLib X rotation opens the jaw downwards in the viewer's coordinates.
BITE = {"animation_length": 3.2, "loop": True, "bones": {
    "jaw": {"rotation": {
        "0.0": [12, 0, 0], "0.55": [78, 0, 0], "1.5": [78, 0, 0],
        "1.7": [84, 0, 0], "1.86": [0, 0, 0], "2.12": [0, 0, 0],
        "2.5": [12, 0, 0], "3.2": [12, 0, 0]}},
    "skull": {
        "position": {"0.0": [0, 0, 3], "0.55": [0, 0, 3],
                     "1.5": [0, 0, -2.5], "1.7": [0, 0, -3.5],
                     "1.86": [0, 0, -5], "2.12": [0, 0, -5],
                     "3.2": [0, 0, 3]},
        "rotation": {"0.0": [0, 0, 0], "0.55": [-8, 0, 0],
                     "1.7": [-8, 0, 0], "1.86": [3, 0, 0],
                     "2.12": [3, 0, 0], "3.2": [0, 0, 0]}}
}}


def face_rects(raw):
    return {name: (*face["uv"], *face["uv_size"])
            for name, face in raw["uv"].items()}


def build(install=False, check=False):
    # Explicit per-face UVs keep subpixel geometry independent of atlas size.
    # Two texels per model pixel give the small teeth proper material coverage.
    # A one-pixel gutter prevents neighbouring atlas islands bleeding at edges.
    width = 256
    x = y = row = 0
    for _, raw, _, _ in CUBES:
        w, h, d = [max(1, math.ceil(v * 2)) for v in raw["size"]]
        raw["uv"] = {}
        for face, (fw, fh) in {"west": (d, h), "east": (d, h),
                               "north": (w, h), "south": (w, h),
                               "up": (w, d), "down": (w, d)}.items():
            if x + fw + 2 > width:
                x, y, row = 0, y + row, 0
            raw["uv"][face] = {"uv": [x + 1, y + 1], "uv_size": [fw, fh]}
            x += fw + 2
            row = max(row, fh + 2)
    used = y + row
    height = 64
    while height < used:
        height *= 2

    texture = Image.new("RGBA", (width, height))
    glow = Image.new("RGBA", (width, height))
    ink, light = texture.load(), glow.load()
    for index, (name, raw, material, cracks) in enumerate(CUBES):
        for face, (fx, fy, fw, fh) in face_rects(raw).items():
            rng = random.Random(6100 + 103 * index + sum(map(ord, face)))
            for v in range(fh):
                for u in range(fw):
                    if material == "soul":
                        tone = 1 + int(rng.random() > .28) + int((u + v) % 5 == 0)
                        color = SOUL[min(4, tone)]
                        light[fx + u, fy + v] = (*color, 255)
                    elif material == "void":
                        color = VOID[1 if rng.random() > .75 else 0]
                    else:
                        base = 4 if material == "tooth" else 3
                        if face in ("down", "west"):
                            base -= 1
                        if face == "up":
                            base += 1
                        # Two-pixel material clusters, sparse wear, and a few
                        # narrow fissures keep the tiny skull legible in flight.
                        cluster = ((u // 2) * 11 + (v // 2) * 17 + index * 7) % 13
                        tone = max(0, min(5, base + (1 if cluster == 2 else -1 if cluster == 10 else 0)))
                        if cracks and face in ("north", "east", "west") and fw >= 4 and fh >= 4:
                            seam = int(fw * .45) + v // 3 - (v // 5)
                            # Short, shaded fissures rather than broad black strips.
                            if u == seam and v < max(2, fh * .7):
                                tone = 1
                            elif u == seam + 1 and v % 4 == 0:
                                tone = 4
                        if name == "jaw underside" and face == "up":
                            # The floor is the shaded interior of the mouth;
                            # retain the pale rim without a bright bone panel.
                            tone = 1 if cluster < 10 else 2
                        color = BONE[tone]
                        if name == "nasal bridge" and face == "north":
                            # A small tapered nasal opening in the facial plane,
                            # with the upper bridge retained as bone.
                            if v >= 1 and abs(u - (fw - 1) / 2) <= (.1 if v < fh - 1 else 1):
                                color = VOID[1]
                    ink[fx + u, fy + v] = (*color, 255)
            for image in (texture, glow):
                pixels = image.load()
                for gy in range(-1, fh + 1):
                    for gx in range(-1, fw + 1):
                        if 0 <= gx < fw and 0 <= gy < fh:
                            continue
                        pixels[fx + gx, fy + gy] = pixels[
                            fx + min(fw - 1, max(0, gx)),
                            fy + min(fh - 1, max(0, gy))]

    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.soul_bolt_preview",
                        "texture_width": width, "texture_height": height,
                        "visible_bounds_width": 1.5, "visible_bounds_height": 1.8,
                        "visible_bounds_offset": [0, -.2, 0]},
        "bones": BONES,
    }]}
    animation = {"format_version": "1.8.0", "animations": {
        "animation.soul_bolt.bite": BITE,
        # Chomp throughout flight; real contact finishes with one final snap.
        "animation.soul_bolt.flight": {"animation_length": .8, "loop": True, "bones": {
            "jaw": {"rotation": {"0.0": [12, 0, 0], "0.28": [78, 0, 0],
                                 "0.42": [84, 0, 0], "0.54": [0, 0, 0],
                                 "0.66": [0, 0, 0], "0.8": [12, 0, 0]}}}},
        "animation.soul_bolt.impact": {"animation_length": .3, "loop": False, "bones": {
            "jaw": {"rotation": {"0.0": [78, 0, 0], "0.16": [0, 0, 0], "0.30": [0, 0, 0]}}}},
    }}
    outputs = {
        "geckolib/models/soul_bolt.geo.json": (json.dumps(geo, indent=2) + "\n").encode(),
        "geckolib/animations/soul_bolt.animation.json": (json.dumps(animation, indent=2) + "\n").encode(),
    }
    for name, image in (("soul_bolt", texture), ("soul_bolt_glowmask", glow)):
        stream = io.BytesIO()
        image.save(stream, format="PNG")
        outputs[f"textures/entity/{name}.png"] = stream.getvalue()
    for name in ("necromancer_soul_wisp.json", "necromancer_bite_shard.json"):
        outputs[f"particles/{name}"] = (CANDIDATE / "particles" / name).read_bytes()
    for family in ("wisp", "shard"):
        for frame in range(4):
            name = f"{family}_{frame}.png"
            data = (CANDIDATE / "particles" / name).read_bytes()
            with Image.open(io.BytesIO(data)) as sprite:
                if sprite.size != (16, 16) or sprite.mode != "RGBA":
                    raise ValueError(f"Invalid Soul Bolt particle: {name}")
            outputs[f"textures/particle/necromancer/{name}"] = data
    assets = ROOT / "src/main/resources/assets/elementalwands"
    if check:
        mismatches = [name for name, data in outputs.items()
                      if not (assets / name).exists() or (assets / name).read_bytes() != data]
        if mismatches:
            raise SystemExit("Soul Bolt export mismatch: " + ", ".join(mismatches))
        print("Soul Bolt: model, animations, textures and ten particle assets match the authored source")
        return
    CANDIDATE.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for name, data in outputs.items():
        if not name.startswith(("particles/", "textures/particle/")):
            (CANDIDATE / Path(name).name).write_bytes(data)
        if install:
            target = assets / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    (CANDIDATE / "manifest.json").write_text(json.dumps({
        "status": "review candidate only", "cubes": len(CUBES),
        "dimensions": [width, height], "bite_cycle_seconds": 3.2,
        "jaw_open_degrees": 84,
        "features": "long narrow mandible within the skull width, closed jaw floor, launch and snapping bite"
    }, indent=2) + "\n")
    images = {name: "data:image/png;base64," + base64.b64encode((CANDIDATE / name).read_bytes()).decode()
              for name in ("soul_bolt.png", "soul_bolt_glowmask.png")}
    payload = json.dumps({"geo": geo, "animation": animation, "images": images}).replace("</", "<\\/")
    page = (HERE / "viewer.template.html").read_text()
    page = page.replace(
        '<script type="module" src="/art/hollow_necromancer/soul_bolt/viewer.js"></script>',
        f'<script type="application/json" id="model-data">{payload}</script>\n'
        f'<script type="module">\n{(HERE / "viewer.js").read_text()}\n</script>')
    (PREVIEW / "index.html").write_text(page)
    print(f"Soul Bolt candidate: {len(CUBES)} cubes, {width}x{height} atlas")
    print(f"Preview: {PREVIEW / 'index.html'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--install", action="store_true")
    modes.add_argument("--check", action="store_true")
    args = parser.parse_args()
    build(args.install, args.check)
