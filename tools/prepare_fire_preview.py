#!/usr/bin/env python3
"""Stage exact local Minecraft references and current Fire art for the web workshop.

This never modifies runtime assets. All PNGs are byte-for-byte source copies.
"""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "art/fire/workshop"
ASSETS = ROOT / "src/main/resources/assets/elementalwands"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", type=Path, default=Path.home() / ".gradle/caches/fabric-loom/1.21.10/minecraft-client.jar")
    args = parser.parse_args()
    manifest = {"minecraftVersion": "1.21.10", "scope": "Browser concept only; runtime and installed mod unchanged", "textures": {}, "models": {}}
    def write(key, data, source, metadata=None):
        target = OUT / "textures" / (key + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        manifest["textures"][key] = {"url": "textures/" + key + ".png", "source": source, "sha256": hashlib.sha256(data).hexdigest(), "animation": metadata}
    with ZipFile(args.client) as jar:
        refs = {"fire0": "block/fire_0", "fire1": "block/fire_1", "flame": "particle/flame", "lava": "particle/lava", "stone": "block/stone", "netherrack": "block/netherrack", "magma": "block/magma", "smoke0": "particle/generic_0", "smoke1": "particle/generic_1", "smoke2": "particle/generic_2", "smoke3": "particle/generic_3", "smoke4": "particle/generic_4", "smoke5": "particle/generic_5", "smoke6": "particle/generic_6", "smoke7": "particle/generic_7"}
        for key, ref in refs.items():
            source = "assets/minecraft/textures/" + ref + ".png"
            meta = json.loads(jar.read(source + ".mcmeta"))["animation"] if source + ".mcmeta" in jar.namelist() else None
            write(key, jar.read(source), source, meta)
        for key in ("template_fire_floor", "template_fire_side"):
            manifest["models"][key] = json.loads(jar.read("assets/minecraft/models/block/" + key + ".json"))
    current = {"old_ground0": "block/fire_ground_a", "old_ground1": "block/fire_ground_b"}
    for family, count in {"inferno_stream": 10, "inferno_front": 10}.items():
        current.update({f"old_{family}{i}": f"entity/{family}_{i}" for i in range(count)})
    for family, count in {"ember": 4, "flame_ribbon": 8, "impact_ring": 6, "pyre_front": 8, "meteor_shell": 8, "meteor_warning": 8, "meteor_impact": 10}.items():
        current.update({f"old_{family}{i}": f"particle/fire/{family}_{i}" for i in range(count)})
    for key, ref in current.items():
        source = ASSETS / "textures" / (ref + ".png")
        meta_path = source.with_suffix(".png.mcmeta")
        meta = json.loads(meta_path.read_text())["animation"] if meta_path.exists() else None
        write(key, source.read_bytes(), str(source.relative_to(ROOT)), meta)
    manifest["models"]["meteor_core"] = json.loads((ASSETS / "models/block/meteor_core.json").read_text())
    (OUT / "references.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Staged {len(manifest['textures'])} exact texture references in {OUT}")


if __name__ == "__main__":
    main()
