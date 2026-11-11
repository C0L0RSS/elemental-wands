#!/usr/bin/env python3
"""Serve art with a /blocknature/ alias on 127.0.0.1 with caching off, so a reload always shows the latest scene files.

py -3 art/nature/blocknature/serve.py [port]   # then open http://127.0.0.1:8351/blocknature/
"""
import functools
import http.server
import sys
from pathlib import Path


class NoCache(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        if path.startswith("/blocknature/"):
            path = "/nature" + path
        return super().translate_path(path)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, *args):
        pass


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8351
handler = functools.partial(NoCache, directory=str(Path(__file__).resolve().parents[2]))
http.server.ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()
