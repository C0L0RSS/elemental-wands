#!/usr/bin/env python3
"""Validate the Stone, Nature, and Space production VFX package.

This complements Gradle's resource packaging check with constraints that matter
for authored pixel art: exact JSON references, alpha-capable PNGs, useful color
depth, power-of-two dimensions, and non-duplicated animation frames.
"""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/elementalwands"
ELEMENTS = ("stone", "nature", "space")
STATIC_TEXTURE_ELEMENTS = {
    "textures/block/stone_spike.png": "stone",
    "textures/block/stone_fault.png": "stone",
    "textures/block/stone_wall.png": "stone",
    "textures/block/stone_wall_ready.png": "stone",
    "textures/block/titan_dome.png": "stone",
    "textures/item/titan_sword.png": "stone",
    "textures/entity/equipment/humanoid/titan_armor.png": "stone",
    "textures/entity/equipment/humanoid_leggings/titan_armor.png": "stone",
    "textures/gui/ability/stone_primary.png": "stone",
    "textures/gui/ability/stone_secondary.png": "stone",
    "textures/gui/ability/stone_ultimate.png": "stone",
    "textures/entity/winged_seed.png": "nature",
    "textures/gui/ability/nature_primary.png": "nature",
    "textures/gui/ability/nature_secondary.png": "nature",
    "textures/gui/ability/nature_ultimate.png": "nature",
    "textures/gui/entangle_bud_empty.png": "nature",
    "textures/gui/entangle_bud_filled.png": "nature",
    "textures/gui/entangle_bud_bloom.png": "nature",
    "textures/gui/sprites/hud/entangle_vignette_v2.png": "nature",
    "textures/gui/ability/space_primary.png": "space",
    "textures/gui/ability/space_secondary.png": "space",
    "textures/gui/ability/space_ultimate.png": "space",
}


def particle_pngs() -> list[Path]:
    files: list[Path] = []
    for element in ELEMENTS:
        files.extend(sorted((ASSETS / "textures/particle" / element).glob("**/*.png")))
    return files


def production_pngs() -> list[Path]:
    files = set(particle_pngs())
    files.update(ASSETS / relative for relative in STATIC_TEXTURE_ELEMENTS)
    return sorted(files)


def validate_json() -> list[str]:
    errors: list[str] = []
    for path in sorted(ASSETS.rglob("*.json")):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"invalid JSON {path.relative_to(ROOT)}: {exc}")

    for element in ELEMENTS:
        definitions = sorted((ASSETS / "particles").glob(f"{element}_*.json"))
        if not definitions:
            errors.append(f"no particle definitions found for {element}")
            continue
        for definition in definitions:
            try:
                payload = json.loads(definition.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            textures = payload.get("textures")
            if not isinstance(textures, list) or not textures:
                errors.append(f"particle definition has no textures: {definition.relative_to(ROOT)}")
                continue
            for identifier in textures:
                if not isinstance(identifier, str) or ":" not in identifier:
                    errors.append(f"invalid particle texture id {identifier!r} in {definition.relative_to(ROOT)}")
                    continue
                namespace, texture_path = identifier.split(":", 1)
                if namespace != "elementalwands":
                    errors.append(f"foreign texture namespace {identifier} in {definition.relative_to(ROOT)}")
                    continue
                png = ASSETS / "textures/particle" / f"{texture_path}.png"
                if not png.is_file():
                    errors.append(f"missing particle texture {png.relative_to(ROOT)}")
    return errors


def validate_stone_model_references() -> list[str]:
    errors: list[str] = []
    blockstates = sorted((ASSETS / "blockstates").glob("stone_*.json"))
    blockstates.append(ASSETS / "blockstates/titan_dome.json")
    for path in blockstates:
        payload = json.loads(path.read_text(encoding="utf-8"))
        for variant in payload.get("variants", {}).values():
            variants = variant if isinstance(variant, list) else [variant]
            for entry in variants:
                identifier = entry.get("model", "")
                if identifier.startswith("elementalwands:"):
                    target = ASSETS / "models" / f"{identifier.split(':', 1)[1]}.json"
                    if not target.is_file():
                        errors.append(f"missing block model {target.relative_to(ROOT)}")

    models = sorted((ASSETS / "models/block").glob("stone_*.json"))
    models.extend((
        ASSETS / "models/block/titan_dome.json",
        ASSETS / "models/item/titan_sword.json",
    ))
    for path in models:
        payload = json.loads(path.read_text(encoding="utf-8"))
        for identifier in payload.get("textures", {}).values():
            if not identifier.startswith("elementalwands:"):
                continue
            target = ASSETS / "textures" / f"{identifier.split(':', 1)[1]}.png"
            if not target.is_file():
                errors.append(f"missing model texture {target.relative_to(ROOT)}")

    item_definition = json.loads((ASSETS / "items/titan_sword.json").read_text(encoding="utf-8"))
    item_model = item_definition["model"]["model"]
    item_target = ASSETS / "models" / f"{item_model.split(':', 1)[1]}.json"
    if not item_target.is_file():
        errors.append(f"missing item model {item_target.relative_to(ROOT)}")

    equipment = json.loads((ASSETS / "equipment/titan_armor.json").read_text(encoding="utf-8"))
    for layer, entries in equipment.get("layers", {}).items():
        for entry in entries:
            identifier = entry.get("texture", "")
            if not identifier.startswith("elementalwands:"):
                continue
            target = (ASSETS / "textures/entity/equipment" / layer
                      / f"{identifier.split(':', 1)[1]}.png")
            if not target.is_file():
                errors.append(f"missing equipment texture {target.relative_to(ROOT)}")
    return errors


def validate_pngs(paths: list[Path]) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    counts = {element: 0 for element in ELEMENTS}
    for path in paths:
        relative = path.relative_to(ROOT)
        asset_relative = path.relative_to(ASSETS).as_posix()
        element = STATIC_TEXTURE_ELEMENTS.get(asset_relative)
        if element is None:
            for candidate in ELEMENTS:
                if candidate in path.parts:
                    element = candidate
                    break
        if element is not None:
            counts[element] += 1
        try:
            with Image.open(path) as image:
                image.load()
                if image.mode != "RGBA":
                    errors.append(f"expected RGBA image: {relative} ({image.mode})")
                    rgba = image.convert("RGBA")
                else:
                    rgba = image
                width, height = rgba.size
                if width <= 0 or height <= 0 or width > 512 or height > 512:
                    errors.append(f"unexpected dimensions: {relative} ({width}x{height})")
                is_screen_overlay = path.name == "entangle_vignette_v2.png" and (width, height) == (320, 180)
                if not is_screen_overlay and (width & (width - 1) or height & (height - 1)):
                    errors.append(f"non-power-of-two dimensions: {relative} ({width}x{height})")

                pixels = list(rgba.get_flattened_data())
                visible = [pixel for pixel in pixels if pixel[3] > 0]
                if not visible:
                    errors.append(f"fully transparent image: {relative}")
                    continue
                distinct = {(r, g, b) for r, g, b, _a in visible}
                if "gui" in path.parts or path.name == "winged_seed.png":
                    minimum_colors = 6
                elif "block" in path.parts or "equipment" in path.parts or "item" in path.parts:
                    minimum_colors = 6
                else:
                    # A single dissipating animation frame may intentionally become
                    # sparse; family-level depth is checked separately below.
                    minimum_colors = 3
                if len(distinct) < minimum_colors:
                    errors.append(
                        f"insufficient material detail: {relative} "
                        f"({len(distinct)} colors, expected at least {minimum_colors})"
                    )
                requires_alpha = (
                    "particle" in path.parts
                    or "gui" in path.parts
                    or path.name == "winged_seed.png"
                )
                if requires_alpha and not any(pixel[3] == 0 for pixel in pixels):
                    errors.append(f"missing transparent background: {relative}")
        except OSError as exc:
            errors.append(f"cannot read PNG {relative}: {exc}")
    for element, count in counts.items():
        if count == 0:
            errors.append(f"no production PNGs found for {element}")
    return errors, counts


def particle_family(path: Path) -> tuple[Path, str]:
    stem, separator, suffix = path.stem.rpartition("_")
    return path.parent, stem if separator and suffix.isdigit() else path.stem


def validate_unique_particle_frames(paths: list[Path]) -> list[str]:
    errors: list[str] = []
    by_family: dict[tuple[Path, str], dict[str, list[Path]]] = defaultdict(lambda: defaultdict(list))
    for path in paths:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        by_family[particle_family(path)][digest].append(path)
    for (directory, family), digests in sorted(by_family.items()):
        for duplicate_paths in digests.values():
            if len(duplicate_paths) > 1:
                names = ", ".join(path.name for path in duplicate_paths)
                errors.append(f"duplicate frames in {(directory / family).relative_to(ROOT)}: {names}")
    return errors


def validate_particle_family_detail(paths: list[Path]) -> list[str]:
    errors: list[str] = []
    families: dict[tuple[Path, str], list[Path]] = defaultdict(list)
    for path in paths:
        families[particle_family(path)].append(path)

    for (directory, family), frames in sorted(families.items()):
        richest_frame = 0
        for path in frames:
            with Image.open(path) as image:
                rgba = image.convert("RGBA")
                visible_rgb = {
                    (r, g, b)
                    for r, g, b, alpha_value in rgba.get_flattened_data()
                    if alpha_value > 0
                }
                richest_frame = max(richest_frame, len(visible_rgb))
        minimum_colors = 4
        if richest_frame < minimum_colors:
            errors.append(
                f"insufficient family material detail: {(directory / family).relative_to(ROOT)} "
                f"({richest_frame} colors in richest frame, expected at least {minimum_colors})"
            )
    return errors


def main() -> int:
    pngs = production_pngs()
    errors = validate_json()
    errors.extend(validate_stone_model_references())
    png_errors, counts = validate_pngs(pngs)
    errors.extend(png_errors)
    errors.extend(validate_unique_particle_frames(particle_pngs()))
    errors.extend(validate_particle_family_detail(particle_pngs()))

    if errors:
        print("VFX validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        "VFX validation passed: "
        + ", ".join(f"{element}={counts[element]} PNGs" for element in ELEMENTS)
        + f", total={len(pngs)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
