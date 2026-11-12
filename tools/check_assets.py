#!/usr/bin/env python3
"""Run every asset and export check in one go.

    python3 tools/check_assets.py              run them all (Windows: py -3 tools/check_assets.py)
    python3 tools/check_assets.py --only roc   run the checks whose path contains "roc"
    python3 tools/check_assets.py --list       print the checks without running them
    python3 tools/check_assets.py --no-jar     skip the built-JAR test

Each check compares generated game files with their art sources. Some generators
rebuild their preview or candidate files on the way, so this script also compares
`git status` before and after and fails if any tracked file changed.

This is the one list of the active checks: when a generator gains a --check, or a new
atlas is installed, add a line to CHECKS below. Preview-only builders and the retired
v0/v1 generators are left out on purpose. Run `./gradlew build` first so the JAR test
has something to open; that test only proves the JAR is a readable archive holding the
mod's metadata, not that it is up to date.

Exit status is 0 only if every check that ran passed.
"""

from __future__ import annotations

import argparse
import filecmp
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MISC = "src/main/resources/assets/elementalwands/textures/misc/"

# ("py", script, *args)     run with this Python
# ("node", script, *args)   run with Node
# ("same", a, b)            the two files must be byte-identical
CHECKS = [
    # Spell effects
    ("py", "tools/validate_remaining_vfx_assets.py"),
    ("py", "tools/prepare_fire_assets.py", "--check"),
    ("py", "art/fire/blockfire/textures/build_atlas.py", "--check"),
    ("node", "art/fire/blockfire/check.mjs"),
    ("same", "art/fire/blockfire/textures/blockfire.png", MISC + "blockfire.png"),
    ("py", "art/wind/blockwind/textures/build_atlas.py", "--check"),
    ("node", "art/wind/blockwind/check.mjs"),
    ("same", "art/wind/blockwind/textures/blockwind.png", MISC + "blockwind.png"),
    ("py", "art/space/blockspace/textures/build_atlas.py", "--check"),
    ("py", "art/space/blockspace/textures/build_astral_glass.py", "--check"),
    ("node", "art/space/blockspace/check.mjs"),
    ("same", "art/space/blockspace/textures/blockspace.png", MISC + "blockspace.png"),
    ("py", "art/nature/blocknature/textures/build_atlas.py", "--check"),
    ("node", "art/nature/blocknature/check.mjs"),
    ("same", "art/nature/blocknature/textures/blocknature.png", MISC + "blocknature.png"),
    ("py", "tools/generate_wind_vfx_assets.py", "--check"),
    ("py", "tools/prepare_nature_expansion.py", "--check"),
    ("node", "tools/prepare_nature_models.mjs", "--check"),
    ("node", "tools/prepare_springbloom.mjs", "--check"),
    # Wand and hub
    ("py", "tools/prepare_wand_hub_assets.py", "--check"),
    ("py", "tools/prepare_wand_assets.py", "--check"),
    # Fractured Guardian, church and nave
    ("py", "tools/prepare_guardian_assets.py", "--check"),
    ("py", "tools/prepare_guardian_throw_socket.py", "--check"),
    ("py", "tools/prepare_guardian_pedestal.py", "--check"),
    ("py", "tools/build_guardian_church.py", "--check"),
    ("py", "art/guardian_nave/build_layout.py", "--check"),
    ("py", "art/fractured_guardian/v4-expressive/build_animations.py", "--check"),
    ("py", "art/fractured_guardian/v5-leap/build_leap.py", "--check"),
    ("py", "art/fractured_guardian/phase/build_phase.py", "--check"),
    ("py", "art/fractured_guardian/intro/build_intro.py", "--check"),
    # Hollow Necromancer, undead and crypt
    ("py", "art/hollow_necromancer/v2/build_art.py", "--check"),
    ("py", "art/hollow_necromancer/transform/build_scene.py", "--check"),
    ("py", "art/hollow_necromancer/soul_fire/build_art.py", "--check"),
    ("py", "tools/prepare_soul_light.py", "--check"),
    ("py", "art/hollow_necromancer/soul_bolt/build_preview.py", "--check"),
    ("py", "art/hollow_necromancer/grasping_hands/build_preview.py", "--check"),
    ("py", "art/hollow_necromancer/life_drain/build_runtime.py", "--check"),
    ("py", "art/hollow_necromancer/undead/build_runtime.py", "--check"),
    ("py", "art/hollow_crypt/build_layout.py", "--check"),
    # Storm Roc, summit and aerie
    ("py", "art/storm_summit/build_layout.py", "--check"),
    ("py", "art/storm_aerie/build_layout.py", "--check"),
    ("py", "art/storm_roc_texture_study/build_texture_v3.py", "--check"),
    ("py", "art/storm_roc_mod/build_mod.py", "--check"),
    ("py", "art/storm_roc/build_runtime.py", "--check"),
    ("py", "art/storm_roc/validate_runtime.py"),
    ("py", "art/roc_effects/build_tornado_texture.py", "--check"),
    ("py", "art/storm_roc/intro/build_intro.py", "--check"),
    # Pyre King, hall and gate
    ("py", "art/pyre_king/build_art.py", "--check"),
    ("py", "art/pyre_king/intro/build_intro.py", "--check"),
    ("py", "art/pyre_hall/build_layout.py", "--check"),
    ("py", "art/pyre_gate/build_layout.py", "--check"),
    # Sound
    ("py", "tools/build_sounds.py", "--check"),
]


def label(check: tuple) -> str:
    kind, *rest = check
    if kind == "same":
        return f"same  {rest[0]} == {rest[1]}"
    return f"{kind:<5} {' '.join(rest)}"


def run(check: tuple) -> tuple[bool, str]:
    kind, *rest = check
    if kind == "same":
        a, b = (ROOT / p for p in rest)
        missing = [str(p.relative_to(ROOT)) for p in (a, b) if not p.is_file()]
        if missing:
            return False, "missing: " + ", ".join(missing)
        return (True, "") if filecmp.cmp(a, b, shallow=False) else (False, "the files differ")
    if not (ROOT / rest[0]).is_file():
        return False, f"missing: {rest[0]}"
    if kind == "py":
        command = [sys.executable, *rest]
    else:
        node = shutil.which("node")
        if node is None:
            return False, "node is not installed or not on PATH"
        command = [node, *rest]
    done = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    output = (done.stdout + done.stderr).strip()
    return done.returncode == 0, output


def jar_check() -> tuple[str, bool, str]:
    version = None
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if line.startswith("mod_version="):
            version = line.split("=", 1)[1].strip()
    name = f"build/libs/elementalwands-{version}.jar"
    jar = ROOT / name
    if not jar.is_file():
        return f"jar   {name}", False, "not built; run ./gradlew build first, or pass --no-jar"
    with zipfile.ZipFile(jar) as archive:
        bad = archive.testzip()
        has_metadata = "fabric.mod.json" in archive.namelist()
    if bad is not None:
        return f"jar   {name}", False, f"corrupt entry: {bad}"
    if not has_metadata:
        return f"jar   {name}", False, "no fabric.mod.json inside; not a mod JAR"
    return f"jar   {name} (readable, has mod metadata)", True, ""


def tracked_state() -> str | None:
    """What git says has changed, or None where git is unavailable."""
    try:
        done = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True)
    except OSError:
        return None
    return done.stdout if done.returncode == 0 else None


def main() -> int:
    parser = argparse.ArgumentParser(description="Run every asset and export check.")
    parser.add_argument("--only", metavar="TEXT", help="run only checks whose path contains TEXT")
    parser.add_argument("--list", action="store_true", help="print the checks and exit")
    parser.add_argument("--no-jar", action="store_true", help="skip testing the built JAR")
    args = parser.parse_args()

    checks = [c for c in CHECKS if not args.only or args.only in label(c)]
    if args.list:
        for check in checks:
            print(label(check))
        return 0
    if not checks and args.only:
        print(f'No check matches "{args.only}".')
        return 1

    failures = []
    started = time.time()
    before = tracked_state()
    for check in checks:
        began = time.time()
        ok, output = run(check)
        print(f"{'PASS' if ok else 'FAIL'}  {time.time() - began:4.0f}s  {label(check)}", flush=True)
        if not ok:
            failures.append(label(check))
            for line in output.splitlines()[-15:]:
                print(f"      {line}")
    if not args.no_jar and not args.only:
        name, ok, output = jar_check()
        print(f"{'PASS' if ok else 'FAIL'}     0s  {name}")
        if not ok:
            failures.append(name)
            print(f"      {output}")

    after = tracked_state()
    if before is not None and after is not None and before != after:
        changed = sorted(set(after.splitlines()) ^ set(before.splitlines()))
        failures.append("a check changed tracked files")
        print("FAIL        a check changed tracked files:")
        for line in changed:
            print(f"      {line}")

    ran = len(checks) + (0 if args.no_jar or args.only else 1)
    passed = ran - len([f for f in failures if f != "a check changed tracked files"])
    print(f"\n{passed} of {ran} checks passed in {time.time() - started:.0f}s.")
    if failures:
        print("Failed:")
        for name in failures:
            print(f"  {name}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
