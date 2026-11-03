#!/usr/bin/env python3
"""Serve art/wind on 127.0.0.1 with caching off, so a reload always shows the latest scene files.

python3 art/wind/blockwind/serve.py [port]   # then open http://127.0.0.1:8348/blockwind/
"""
import functools
import http.server
import sys
from pathlib import Path


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, *args):
        pass


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8348
handler = functools.partial(NoCache, directory=str(Path(__file__).resolve().parent.parent))
http.server.ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()
