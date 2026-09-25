#!/usr/bin/env python3
"""Serve the repository root for the local Soul Bolt preview on port 8351."""
import functools
import http.server
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()


if __name__ == '__main__':
    handler = functools.partial(NoCache, directory=str(ROOT))
    print('http://127.0.0.1:8351/.local-previews/soul-bolt/', flush=True)
    http.server.ThreadingHTTPServer(('127.0.0.1', 8351), handler).serve_forever()
