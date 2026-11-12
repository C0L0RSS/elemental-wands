#!/usr/bin/env python3
"""Serve the generated review workshop on loopback port 8410."""
import functools
import http.server
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'.local-previews/pyre-king'
class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path=='/':
            self.send_response(302);self.send_header('Location','/workshop/');self.end_headers();return
        super().do_GET()
    def end_headers(self):
        self.send_header('Cache-Control','no-store');super().end_headers()
if __name__=='__main__':
    if not (OUT/'workshop/index.html').exists():raise SystemExit('Run python3 art/pyre_king/build_art.py first.')
    print('Pyre King workshop: http://127.0.0.1:8410/',flush=True)
    http.server.ThreadingHTTPServer(('127.0.0.1',8410),functools.partial(Handler,directory=str(OUT))).serve_forever()
