#!/usr/bin/env python3
"""Serves the workshop on loopback without caching, so rebuilt art shows on reload.

    python3 art/hollow_necromancer/workshop/serve.py   # http://127.0.0.1:8350/
"""
import functools
import http.server
import sys
from pathlib import Path


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8350
    handler = functools.partial(NoCache, directory=str(Path(__file__).resolve().parent))
    http.server.ThreadingHTTPServer(('127.0.0.1', port), handler).serve_forever()
