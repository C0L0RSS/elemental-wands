#!/usr/bin/env python3
"""Validate the exact five-affinity and shared production VFX package."""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/elementalwands"
ELEMENTS = ("fire", "wind", "stone", "nature", "space")
EXPECTED_ELEMENT_COUNTS = {"fire": 45, "wind": 53, "stone": 41, "nature": 44, "space": 81}

# family -> (frame count, exact square dimensions)
PARTICLE_FAMILIES: dict[str, dict[str, tuple[int, int]]] = {
    "fire": {
        "ember": (4,64), "flame_ribbon": (4,64), "impact_ring": (4,64),
        "pyre_front": (4,64), "meteor_shell": (4,64), "meteor_warning": (4,64), "meteor_impact": (4,64),
    },
    "wind": {
        "mote": (4, 16), "crescent": (6, 32), "air_ribbon": (6, 32),
        "burst_ring": (6, 32), "zephyr_impact": (8, 64),
        "slipstream": (6, 32), "shear_feather": (6, 32),
    },
    "stone": {
        "dust": (4, 16), "shard": (6, 16), "fault": (6, 32),
        "shockwave": (6, 32), "titan": (8, 64),
    },
    "nature": {
        "pollen": (4, 16), "leaf": (4, 16), "petal": (6, 16),
        "vine": (6, 32), "bloom": (8, 32), "heart": (8, 64),
    },
    "space": {
        "mote": (4, 16), "pinpoint": (4, 16), "singularity": (6, 32),
        "broken_orbit": (6, 32), "implosion_ring": (6, 32), "rift": (6, 32),
        "dying_star_cyan": (6, 32), "dying_star_magenta": (6, 32),
        "consumption": (6, 32), "eclipse": (8, 64), "gravity_lens": (8, 64),
        "final_collapse": (12, 64),
    },
}

# relative texture path -> (owner, exact dimensions)
STATIC_TEXTURES: dict[str, tuple[str, tuple[int, int]]] = {
    # Fire: 17 static/hero PNGs + 28 approved particle frames = 45.
    "textures/gui/ability/fire_primary.png": ("fire", (32, 32)),
    "textures/gui/ability/fire_secondary.png": ("fire", (32, 32)),
    "textures/gui/ability/fire_ultimate.png": ("fire", (32, 32)),
    "textures/block/fire_ground_a.png": ("fire", (64, 256)),
    "textures/block/fire_ground_b.png": ("fire", (64, 256)),
    **{f"textures/entity/inferno_stream_{frame}.png": ("fire", (128, 64)) for frame in range(4)},
    **{f"textures/entity/inferno_front_{frame}.png": ("fire", (64, 64)) for frame in range(4)},
    **{f"textures/entity/fire_meteor_surface_{frame}.png": ("fire", (128,256)) for frame in range(4)},
    # Wind: 11 static PNGs + 42 particles = 53.
    **{f"textures/entity/vacuum_blade_{frame}.png": ("wind", (64, 64)) for frame in range(6)},
    "textures/item/zephyr_wings.png": ("wind", (32, 32)),
    "textures/entity/equipment/wings/zephyr_wings.png": ("wind", (64, 32)),
    "textures/gui/ability/wind_primary.png": ("wind", (32, 32)),
    "textures/gui/ability/wind_secondary.png": ("wind", (32, 32)),
    "textures/gui/ability/wind_ultimate.png": ("wind", (32, 32)),
    # Stone.
    "textures/block/stone_spike.png": ("stone", (16, 16)),
    "textures/block/stone_fault.png": ("stone", (16, 16)),
    "textures/block/stone_wall.png": ("stone", (16, 16)),
    "textures/block/stone_wall_ready.png": ("stone", (16, 16)),
    "textures/block/titan_dome.png": ("stone", (16, 16)),
    "textures/item/titan_sword.png": ("stone", (32, 32)),
    "textures/entity/equipment/humanoid/titan_armor.png": ("stone", (64, 32)),
    "textures/entity/equipment/humanoid_leggings/titan_armor.png": ("stone", (64, 32)),
    "textures/gui/ability/stone_primary.png": ("stone", (32, 32)),
    "textures/gui/ability/stone_secondary.png": ("stone", (32, 32)),
    "textures/gui/ability/stone_ultimate.png": ("stone", (32, 32)),
    # Nature.
    "textures/entity/winged_seed.png": ("nature", (64, 64)),
    "textures/gui/ability/nature_primary.png": ("nature", (32, 32)),
    "textures/gui/ability/nature_secondary.png": ("nature", (32, 32)),
    "textures/gui/ability/nature_ultimate.png": ("nature", (32, 32)),
    "textures/gui/entangle_bud_empty.png": ("nature", (16, 16)),
    "textures/gui/entangle_bud_filled.png": ("nature", (16, 16)),
    "textures/gui/entangle_bud_bloom.png": ("nature", (16, 16)),
    "textures/gui/sprites/hud/entangle_vignette_v2.png": ("nature", (320, 180)),
    # Space.
    "textures/gui/ability/space_primary.png": ("space", (32, 32)),
    "textures/gui/ability/space_secondary.png": ("space", (32, 32)),
    "textures/gui/ability/space_ultimate.png": ("space", (32, 32)),
}

SHARED_TEXTURES = {
    "textures/item/wizard_wand.png": (16, 16),
    "textures/gui/wand_hud_v2.png": (256, 256),
}

OBSOLETE_TEXTURES = (
    "textures/entity/inferno_wave.png",
    "textures/entity/vacuum_blade.png",
    "textures/block/pyre_coals.png",
) + tuple(
    f"textures/entity/inferno_wave_{frame}.png" for frame in range(6)
) + (
    "textures/block/inferno_flame.png",
    "textures/block/inferno_flame.png.mcmeta",
    "textures/block/meteor_core.png",
    "models/block/inferno_flame.json",
    "models/block/pyre_coals.json",
    "models/block/pyre_coals_1.json",
    "models/block/pyre_coals_2.json",
    "models/block/pyre_coals_3.json",
) + tuple(
    f"textures/block/pyre_coals_{frame}.png" for frame in range(4)
)


def family_ids(element: str, family: str, count: int, reverse: bool = False) -> list[str]:
    frames = range(count - 1, -1, -1) if reverse else range(count)
    return [f"elementalwands:{element}/{family}_{frame}" for frame in frames]


EXPECTED_PARTICLE_DEFINITIONS: dict[str, list[str]] = {
    "arcane_mote": family_ids("arcane", "mote", 4),
    "arcane_thread": family_ids("arcane", "thread", 6),
}
for _element, _families in PARTICLE_FAMILIES.items():
    for _family, (_count, _size) in _families.items():
        EXPECTED_PARTICLE_DEFINITIONS[f"{_element}_{_family}"] = family_ids(_element, _family, _count)
EXPECTED_PARTICLE_DEFINITIONS["space_expansion_ring"] = family_ids(
    "space", "implosion_ring", 6, reverse=True
)

NEW_PARTICLE_CONSTANTS = (
    "FIRE_EMBER", "FIRE_FLAME_RIBBON", "FIRE_IMPACT_RING", "FIRE_PYRE_FRONT",
    "FIRE_METEOR_SHELL", "FIRE_METEOR_WARNING", "FIRE_METEOR_IMPACT",
    "WIND_SLIPSTREAM", "WIND_SHEAR_FEATHER",
)


def expected_particle_paths() -> dict[Path, tuple[str, tuple[int, int]]]:
    paths: dict[Path, tuple[str, tuple[int, int]]] = {}
    for element, families in PARTICLE_FAMILIES.items():
        for family, (count, size) in families.items():
            for frame in range(count):
                path = ASSETS / f"textures/particle/{element}/{family}_{frame}.png"
                paths[path] = (element, (size, size))
    return paths


def expected_production_paths() -> dict[Path, tuple[str, tuple[int, int]]]:
    paths = expected_particle_paths()
    paths.update({ASSETS / relative: spec for relative, spec in STATIC_TEXTURES.items()})
    return paths


def validate_json() -> list[str]:
    errors: list[str] = []
    json_paths = sorted(ASSETS.rglob("*.json"))
    json_paths.extend(sorted(ASSETS.rglob("*.png.mcmeta")))
    for path in json_paths:
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"invalid JSON {path.relative_to(ROOT)}: {exc}")

    definitions = {path.stem: path for path in sorted((ASSETS / "particles").glob("*.json"))}
    expected_names = set(EXPECTED_PARTICLE_DEFINITIONS)
    actual_names = set(definitions)
    for missing in sorted(expected_names - actual_names):
        errors.append(f"missing particle definition: assets/elementalwands/particles/{missing}.json")
    for extra in sorted(actual_names - expected_names):
        errors.append(f"unexpected particle definition: {definitions[extra].relative_to(ROOT)}")
    if len(definitions) != 40:
        errors.append(f"particle definition count {len(definitions)}, expected 40")

    for name, expected_textures in EXPECTED_PARTICLE_DEFINITIONS.items():
        path = definitions.get(name)
        if path is None:
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        textures = payload.get("textures")
        if textures != expected_textures:
            errors.append(
                f"particle texture order mismatch in {path.relative_to(ROOT)}: "
                f"expected {expected_textures}, got {textures}"
            )
            continue
        for identifier in textures:
            namespace, texture_path = identifier.split(":", 1)
            if namespace == "minecraft":
                errors.append(
                    f"unapproved external particle texture {identifier} in {path.relative_to(ROOT)}"
                )
                continue
            png = ASSETS / "textures/particle" / f"{texture_path}.png"
            if namespace != "elementalwands" or not png.is_file():
                errors.append(f"missing particle texture for {identifier} in {path.relative_to(ROOT)}")
    return errors


def validate_exact_inventory() -> list[str]:
    errors: list[str] = []
    expected = expected_production_paths()
    for path in expected:
        if not path.is_file():
            errors.append(f"missing production PNG: {path.relative_to(ROOT)}")

    expected_particles = set(expected_particle_paths())
    actual_particles: set[Path] = set()
    for element in ELEMENTS:
        actual_particles.update((ASSETS / "textures/particle" / element).glob("*.png"))
    for path in sorted(actual_particles - expected_particles):
        errors.append(f"unexpected affinity particle PNG: {path.relative_to(ROOT)}")

    for relative in SHARED_TEXTURES:
        if not (ASSETS / relative).is_file():
            errors.append(f"missing shared PNG: {(ASSETS / relative).relative_to(ROOT)}")
    if len(SHARED_TEXTURES) != 2:
        errors.append(f"shared texture contract contains {len(SHARED_TEXTURES)} entries, expected 2")

    for relative in OBSOLETE_TEXTURES:
        path = ASSETS / relative
        if path.exists():
            errors.append(f"obsolete replaced texture still present: {path.relative_to(ROOT)}")
    return errors


def validate_pngs() -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    counts = {element: 0 for element in ELEMENTS}
    paths = expected_production_paths()
    all_specs: dict[Path, tuple[str, tuple[int, int]]] = dict(paths)
    all_specs.update({ASSETS / relative: ("shared", size) for relative, size in SHARED_TEXTURES.items()})
    for path, (owner, expected_size) in sorted(all_specs.items()):
        if not path.is_file():
            continue
        if owner in counts:
            counts[owner] += 1
        relative = path.relative_to(ROOT)
        try:
            with Image.open(path) as image:
                image.load()
                if image.mode != "RGBA":
                    errors.append(f"expected RGBA image: {relative} ({image.mode})")
                    rgba = image.convert("RGBA")
                else:
                    rgba = image
                if rgba.size != expected_size:
                    errors.append(f"wrong dimensions: {relative} ({rgba.size}, expected {expected_size})")
                pixels = list(rgba.get_flattened_data())
                visible = [pixel for pixel in pixels if pixel[3] > 0]
                if not visible:
                    errors.append(f"fully transparent image: {relative}")
                    continue
                distinct_rgb = {(r, g, b) for r, g, b, _a in visible}
                is_particle = "particle" in path.parts
                minimum_colors = 3 if is_particle else 6
                if owner in ("fire", "wind") and max(expected_size) >= 32:
                    minimum_colors = max(minimum_colors, 6)
                if owner == "fire" and "gui" not in path.parts and expected_size[0] >= 32:
                    minimum_colors = max(minimum_colors, 7)
                if owner == "fire" and expected_size[0] >= 64:
                    minimum_colors = max(minimum_colors, 10)
                if len(distinct_rgb) < minimum_colors:
                    errors.append(
                        f"insufficient material detail: {relative} "
                        f"({len(distinct_rgb)} colors, expected at least {minimum_colors})"
                    )
                if owner == "fire" and any(r == 0 and g == 0 and b == 0 for r, g, b, _a in visible):
                    errors.append(f"opaque pure black is forbidden in Fire art: {relative}")
                requires_alpha = (is_particle
                                  or any(part in path.parts for part in ("gui", "entity", "item"))
                                  or (owner == "fire" and path.name.startswith("fire_ground_")))
                if path.name.startswith("fire_meteor_surface_"): requires_alpha = False
                if requires_alpha and not any(pixel[3] == 0 for pixel in pixels):
                    errors.append(f"missing transparent background: {relative}")
        except OSError as exc:
            errors.append(f"cannot read PNG {relative}: {exc}")

    for element, expected_count in EXPECTED_ELEMENT_COUNTS.items():
        if counts[element] != expected_count:
            errors.append(f"{element} production PNG count {counts[element]}, expected {expected_count}")
    if sum(counts.values()) != 264:
        errors.append(f"affinity production PNG total {sum(counts.values())}, expected 264")
    return errors, counts


def validate_unique_frames() -> list[str]:
    errors: list[str] = []
    for element, families in PARTICLE_FAMILIES.items():
        for family, (count, _size) in families.items():
            paths = [ASSETS / f"textures/particle/{element}/{family}_{frame}.png" for frame in range(count)]
            if any(not path.is_file() for path in paths):
                continue
            digests: dict[str, list[str]] = defaultdict(list)
            richest = 0
            for path in paths:
                digests[hashlib.sha256(path.read_bytes()).hexdigest()].append(path.name)
                with Image.open(path) as image:
                    richest = max(richest, len({(r, g, b) for r, g, b, a in image.convert("RGBA").get_flattened_data() if a > 0}))
            for names in digests.values():
                if len(names) > 1:
                    errors.append(f"duplicate frames in {element}/{family}: {', '.join(names)}")
            minimum = 6 if element in ("fire", "wind") and PARTICLE_FAMILIES[element][family][1] >= 32 else 4
            if richest < minimum:
                errors.append(f"insufficient family detail in {element}/{family}: {richest}, expected {minimum}")

    for stem, count in (("vacuum_blade", 6), ("inferno_stream", 4), ("inferno_front", 4), ("fire_meteor_surface", 4)):
        paths = [ASSETS / f"textures/entity/{stem}_{frame}.png" for frame in range(count)]
        if all(path.is_file() for path in paths) and len({path.read_bytes() for path in paths}) != count:
            errors.append(f"duplicate entity animation frames in {stem}")
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
    models.extend((ASSETS / "models/block/titan_dome.json", ASSETS / "models/item/titan_sword.json"))
    for path in models:
        payload = json.loads(path.read_text(encoding="utf-8"))
        for identifier in payload.get("textures", {}).values():
            if isinstance(identifier, str) and identifier.startswith("elementalwands:"):
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
            if identifier.startswith("elementalwands:"):
                target = ASSETS / "textures/entity/equipment" / layer / f"{identifier.split(':', 1)[1]}.png"
                if not target.is_file():
                    errors.append(f"missing equipment texture {target.relative_to(ROOT)}")
    return errors


def validate_fire_resource_references() -> list[str]:
    from prepare_fire_assets import outputs
    errors = []
    for path, expected in outputs().items():
        if not path.is_file() or path.read_bytes() != expected:
            errors.append(f"approved Fire workshop resource drift: {path.relative_to(ROOT)}")
    model = json.loads((ASSETS / "models/block/pyre_low_fire.json").read_text())
    if any(max(e['from'][1],e['to'][1]) > 1.2 for e in model['elements']):
        errors.append("Pyre lingering fire exceeds 0.075 block")
    mesh = json.loads((ASSETS / "fire/meteor-model.json").read_text())
    if mesh.get('widthBlocks') != 8 or mesh.get('voxelSize') != .5 or len(mesh.get('faces',[])) != 1248:
        errors.append("Meteor differs from the approved round half-block-step model")
    return errors


def validate_java_particle_registration() -> list[str]:
    errors: list[str] = []
    registry_path = ROOT / "src/main/java/com/anton/elementalwands/registry/ModParticles.java"
    factories_path = ROOT / "src/main/java/com/anton/elementalwands/client/particle/ModParticleFactories.java"
    for path in (registry_path, factories_path):
        if not path.is_file():
            errors.append(f"missing particle registration source: {path.relative_to(ROOT)}")
    if errors:
        return errors

    registry_source = registry_path.read_text(encoding="utf-8")
    factories_source = factories_path.read_text(encoding="utf-8")
    for constant in NEW_PARTICLE_CONSTANTS:
        registry_declaration = f"public static final SimpleParticleType {constant} = register("
        profile_declaration = f"private static final Profile {constant} = new Profile("
        factory_registration = f"factories.register(ModParticles.{constant},"
        if registry_declaration not in registry_source:
            errors.append(f"missing registry declaration for {constant}")
        if profile_declaration not in factories_source:
            errors.append(f"missing client profile for {constant}")
        if factory_registration not in factories_source:
            errors.append(f"missing client factory registration for {constant}")
    return errors


def main() -> int:
    errors = validate_json()
    errors.extend(validate_exact_inventory())
    png_errors, counts = validate_pngs()
    errors.extend(png_errors)
    errors.extend(validate_unique_frames())
    errors.extend(validate_stone_model_references())
    errors.extend(validate_fire_resource_references())
    errors.extend(validate_java_particle_registration())

    if errors:
        print("VFX validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        "VFX validation passed: "
        + ", ".join(f"{element}={counts[element]} PNGs" for element in ELEMENTS)
        + f", affinities={sum(counts.values())}, shared={len(SHARED_TEXTURES)}, "
          f"production_total={sum(counts.values()) + len(SHARED_TEXTURES)}, "
          f"particle_definitions={len(EXPECTED_PARTICLE_DEFINITIONS)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
