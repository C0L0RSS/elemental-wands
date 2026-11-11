#!/usr/bin/env python3
"""Generate the mod's synthesized sound effects, or check the sound wiring.

    python3 tools/build_sounds.py                 rebuild every generated sound
    python3 tools/build_sounds.py meteor leap     rebuild sounds whose name contains a word
    python3 tools/build_sounds.py --wav DIR ...   also write WAV copies for listening
    python3 tools/build_sounds.py --check         verify sounds.json, ModSounds and the files agree

Building needs numpy and soundfile. Ogg files differ byte-for-byte on every
encode, so rebuild only the sounds you changed. --check needs neither library.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/elementalwands"
MOD_SOUNDS = ROOT / "src/main/java/com/anton/elementalwands/registry/ModSounds.java"
sys.path.insert(0, str(Path(__file__).resolve().parent / "sound"))


def recipes() -> dict:
    import fire
    import nature
    import space
    import stone
    import wind

    return {**fire.SOUNDS, **nature.SOUNDS, **space.SOUNDS, **stone.SOUNDS, **wind.SOUNDS}


def outputs(name: str, variants: int) -> list[str]:
    return [name] if variants == 1 else [f"{name}_{i}" for i in range(1, variants + 1)]


def build(words: list[str], wav_dir: Path | None) -> None:
    import numpy as np
    from synth import SR, rng_for, write_ogg, write_wav

    built = 0
    for name, (recipe, variants) in recipes().items():
        if words and not any(word in name for word in words):
            continue
        for index, out in enumerate(outputs(name, variants)):
            audio = recipe(rng_for(name, index))
            write_ogg(ASSETS / "sounds" / f"{out}.ogg", audio)
            if wav_dir:
                write_wav(wav_dir / f"{out.replace('/', '_')}.wav", audio)
            rms = 20 * np.log10(np.sqrt(np.mean(audio ** 2)))
            print(f"{out}: {len(audio) / SR:.2f}s rms {rms:.1f} dBFS")
            built += 1
    print(f"built {built} files")


def check() -> int:
    problems = []
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    registered = set(re.findall(r'register\("([^"]+)"\)', MOD_SOUNDS.read_text(encoding="utf-8")))
    problems += [f"ModSounds event without a sounds.json entry: {e}" for e in sorted(registered - set(sounds))]
    problems += [f"sounds.json entry without a ModSounds event: {e}" for e in sorted(set(sounds) - registered)]

    referenced = set()
    for event, entry in sounds.items():
        if not entry.get("sounds"):
            problems.append(f"{event} lists no sounds")
        for sound in entry.get("sounds", []):
            name = sound if isinstance(sound, str) else sound["name"]
            is_event = not isinstance(sound, str) and sound.get("type") == "event"
            if name.startswith("elementalwands:") and not is_event:
                referenced.add(name.split(":", 1)[1])
    on_disk = {p.relative_to(ASSETS / "sounds").with_suffix("").as_posix()
               for p in (ASSETS / "sounds").rglob("*.ogg")} if (ASSETS / "sounds").exists() else set()
    problems += [f"sounds.json names a missing file: sounds/{n}.ogg" for n in sorted(referenced - on_disk)]
    problems += [f"file is not used by sounds.json: sounds/{n}.ogg" for n in sorted(on_disk - referenced)]

    generated = {out for name, (_, variants) in recipes().items() for out in outputs(name, variants)}
    problems += [f"recipe output has not been built: sounds/{n}.ogg" for n in sorted(generated - on_disk)]

    lang = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    for event, entry in sounds.items():
        key = entry.get("subtitle", "")
        if key.startswith("subtitles.elementalwands.") and key not in lang:
            problems.append(f"{event} subtitle has no en_us text: {key}")

    for problem in problems:
        print(problem)
    print(f"sound check: {len(sounds)} events, {len(on_disk)} files, {len(problems)} problems")
    return 1 if problems else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("words", nargs="*", help="only build sounds whose name contains one of these")
    parser.add_argument("--wav", type=Path, help="also write WAV copies into this folder")
    parser.add_argument("--check", action="store_true", help="verify the wiring instead of building")
    args = parser.parse_args()
    if args.check:
        return check()
    build(args.words, args.wav)
    return 0


if __name__ == "__main__":
    sys.exit(main())
