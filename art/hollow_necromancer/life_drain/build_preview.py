#!/usr/bin/env python3
"""Build the local Life Drain concept page without touching mod resources."""

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SOURCE = Path(__file__).with_name("viewer.template.html")
OUTPUT = ROOT / ".local-previews/life-drain/index.html"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify the generated page")
    args = parser.parse_args()
    expected = SOURCE.read_text()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != expected:
            raise SystemExit("Life Drain preview is missing or stale; run build_preview.py")
        print(f"Verified {OUTPUT.relative_to(ROOT)}")
        return
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(expected)
    print(f"Built {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
