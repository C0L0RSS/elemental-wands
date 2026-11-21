#!/usr/bin/env python3
"""Installs each element's Storm Roc landing into the game: its track and both actors' clips.

Each <element>_landing.py bakes the whole intro with that element's landing after the throw
(build_intro.py's scene up to there). The game plays a player's own element's version
(summit.StormRocIntroTrack.get(landing)); a player with no element gets build_intro.py's plain crash.
For each element this writes, under src/main/resources/assets/elementalwands/:
  cinematics/storm_roc_intro_<element>.json                   the track, with the landing's columns and facts
  geckolib/animations/storm_roc_intro_<element>.animation.json         the Roc's clip
  geckolib/animations/storm_roc_intro_player_<element>.animation.json  the player's clip
The models and the cloud texture are build_intro.py's, shared by every version.

Run from the repository root:
    python3 art/storm_roc/intro/install_landings.py            # every element
    python3 art/storm_roc/intro/install_landings.py nature     # just these
    python3 art/storm_roc/intro/install_landings.py --check    # fail if any installed copy has drifted
Each element bakes in its own process: the landing scripts reshape build_intro's module as they import it.
"""
import argparse
import importlib
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ELEMENTS = ['fire', 'wind', 'stone', 'nature', 'space']
# What the browser review needs and the game doesn't.
PREVIEW_ONLY = {'script', 'shots', 'shot_names', 'atlas'}


def compact(data): return json.dumps(data, separators=(',', ':')) + '\n'


def outputs(element):
    """The installed files for one element, baked in this process."""
    sys.path.insert(0, str(HERE))
    landing = importlib.import_module(f'{element}_landing')
    B = landing.B
    scene, roc_clip, player_clip, track = landing.build()
    facts = track.pop('landing', None) or track.pop('nature', None) or {}
    track['landing'] = {'element': element, **{k: v for k, v in facts.items() if k not in PREVIEW_ONLY | {'element'}}}
    # The game's beats are whole ticks.
    track['beats'] = {k: int(round(v)) for k, v in track['beats'].items()}
    anims = B.ASSETS/'geckolib/animations'
    return {
        B.ASSETS/f'cinematics/storm_roc_intro_{element}.json': compact(track),
        anims/f'storm_roc_intro_{element}.animation.json': compact({'format_version': '1.8.0', 'animations': {'animation.storm_roc.intro': roc_clip}}),
        anims/f'storm_roc_intro_player_{element}.animation.json': compact({'format_version': '1.8.0', 'animations': {'animation.storm_roc_intro_player.intro': player_clip}}),
    }


def one(element, check):
    files = outputs(element)
    if check:
        stale = [p for p, text in files.items() if not p.exists() or p.read_text(encoding='utf-8') != text]
        for p in stale: print(f'stale: {p}')
        return 1 if stale else 0
    for path, text in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8', newline='\n')
    print(f'{element}: installed ({", ".join(f"{p.name} {len(t)//1024} KB" for p, t in files.items())})')
    return 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('elements', nargs='*', help='which landings: ' + ', '.join(ELEMENTS) + ' (default: all)')
    parser.add_argument('--check', action='store_true', help='fail if any installed copy differs from a fresh bake')
    parser.add_argument('--one', choices=ELEMENTS, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.one: sys.exit(one(args.one, args.check))
    unknown = set(args.elements) - set(ELEMENTS)
    if unknown: parser.error('no landing for ' + ', '.join(sorted(unknown)))
    failed = 0
    for element in args.elements or ELEMENTS:
        failed |= subprocess.run([sys.executable, __file__, '--one', element] + (['--check'] if args.check else [])).returncode
    sys.exit(failed)
