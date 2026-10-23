#!/usr/bin/env python3
"""Serves the workshop on loopback without caching, so rebuilt art shows on reload.

    python3 art/hollow_necromancer/workshop/serve.py   # http://127.0.0.1:8350/

`/before/` serves an optional local snapshot of the art from before a rework pass, kept in the
ignored `.local-previews/necromancer-workshop/before/`, for the workshop's "Before" version.
"""
import functools
import http.server
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BEFORE = HERE.parents[2] / '.local-previews/necromancer-workshop/before'


class NoCache(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.split('?', 1)[0] == '/before/available.json':
            body = json.dumps({'available': (BEFORE / 'hollow_necromancer.geo.json').exists()}).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def translate_path(self, path):
        clean = path.split('?', 1)[0].split('#', 1)[0]
        if clean.startswith('/before/'):
            return str(BEFORE / Path(clean).name)  # A flat folder: no subpaths, no way out of it.
        return super().translate_path(path)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8350
    handler = functools.partial(NoCache, directory=str(HERE))
    http.server.ThreadingHTTPServer(('127.0.0.1', port), handler).serve_forever()
