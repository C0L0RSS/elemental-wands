#!/usr/bin/env python3
"""Serve the repository root for the Life Drain design preview."""

import functools
import http.server
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    address = ("127.0.0.1", 8352)
    print("http://127.0.0.1:8352/.local-previews/life-drain/", flush=True)
    http.server.ThreadingHTTPServer(
        address, functools.partial(NoCache, directory=str(ROOT))
    ).serve_forever()
