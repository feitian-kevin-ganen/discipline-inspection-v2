#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本机统一入口网关：三端静态资源 + /api 反向代理。

目标机该角色由 InforSuiteLB 承担（6003 单入口）；本机没有 InforSuiteLB，
用本脚本等价实现，保证本机与目标机的访问路径完全一致：

    /portal/        -> web/portal（本机模拟数字国联门户）
    /mgmt/         -> web/admin
    /learning-web/  -> web/user
    /learning-h5/   -> web/h5
    /api/          -> 127.0.0.1:8088（exam-server）
    /              -> 302 /portal/

用法：python3 scripts/local_gateway.py [--port 6003] [--root /home/cloud/projects/AIExamK3/deploy/local-deploy]
"""
import argparse
import http.client
import os
import posixpath
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 路径前缀 -> (web 子目录, 前缀)
APPS = [
    ("/portal/", "portal"),
    ("/mgmt/", "admin"),
    ("/learning-web/", "user"),
    ("/learning-h5/", "h5"),
]

NO_CACHE_SUFFIXES = (".html",)


def _client_ip(handler):
    return handler.client_address[0] if handler.client_address else "-"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"
    server_version = "exam-local-gateway"

    # ---------- 基础 ----------
    def log_message(self, fmt, *args):
        sys.stderr.write("%s [%s] %s\n" % (self.log_date_time_string(), _client_ip(self), fmt % args))

    def _send_body(self, code, body, content_type="text/plain; charset=utf-8", extra=None, head_only=False):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if not head_only:
            self.wfile.write(body)

    # ---------- 路由 ----------
    def do_GET(self):
        self._handle("GET")

    def do_HEAD(self):
        self._handle("HEAD")

    def do_POST(self):
        self._handle("POST")

    def do_PUT(self):
        self._handle("PUT")

    def do_DELETE(self):
        self._handle("DELETE")

    def do_PATCH(self):
        self._handle("PATCH")

    def do_OPTIONS(self):
        self._handle("OPTIONS")

    def _handle(self, method):
        parsed = urllib.parse.urlsplit(self.path)
        path = urllib.parse.unquote(parsed.path)
        if path.startswith("/api/"):
            self._proxy(method, parsed)
            return
        if method not in ("GET", "HEAD"):
            self._send_body(405, b"method not allowed\n")
            return

        if path == "/":
            self.send_response(302)
            self.send_header("Location", "/portal/")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        if path in ("/guolian-jijian/login", "/guolian-jijian/login/"):
            self._serve_sso(head_only=(method == "HEAD"))
            return

        for prefix, sub in APPS:
            bare = prefix.rstrip("/")
            if path == bare:
                self.send_response(301)
                self.send_header("Location", prefix)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            if path.startswith(prefix):
                self._serve_static(prefix, sub, parsed.path[len(prefix):], head_only=(method == "HEAD"))
                return

        self._send_body(404, b"not found\n")

    # ---------- 静态资源 ----------
    def _serve_sso(self, head_only=False):
        f = os.path.join(self.server.app_root, "web", "sso", "index.html")
        if not os.path.isfile(f):
            self._send_body(404, "未部署门户单点登录页：web/sso/index.html\n".encode())
            return
        self._send_file(f, head_only=head_only)

    def _serve_static(self, prefix, sub, rel, head_only=False):
        base_dir = os.path.join(self.server.app_root, "web", sub)
        rel = posixpath.normpath("/" + rel).lstrip("/")
        target = os.path.join(base_dir, rel) if rel else base_dir
        if not os.path.abspath(target).startswith(os.path.abspath(base_dir)):
            self._send_body(403, b"forbidden\n")
            return
        if os.path.isdir(target):
            target = os.path.join(target, "index.html")
        if os.path.isfile(target):
            self._send_file(target, head_only=head_only)
            return
        # SPA 回退：无扩展名的前端路由交回 index.html
        if not posixpath.splitext(rel)[1]:
            index = os.path.join(base_dir, "index.html")
            if os.path.isfile(index):
                self._send_file(index, head_only=head_only)
                return
        self._send_body(404, b"not found\n")

    def _send_file(self, path, head_only=False):
        try:
            with open(path, "rb") as fp:
                data = fp.read()
        except OSError:
            self._send_body(404, b"not found\n")
            return
        ctype = self.guess_type(path)
        extra = {}
        if path.endswith(NO_CACHE_SUFFIXES):
            extra["Cache-Control"] = "no-store"
        else:
            extra["Cache-Control"] = "public, max-age=31536000, immutable"
        self._send_body(200, data, ctype, extra, head_only=head_only)

    @staticmethod
    def guess_type(path):
        ext = posixpath.splitext(path)[1].lower()
        return {
            ".html": "text/html; charset=utf-8",
            ".js": "application/javascript; charset=utf-8",
            ".mjs": "application/javascript; charset=utf-8",
            ".css": "text/css; charset=utf-8",
            ".json": "application/json; charset=utf-8",
            ".svg": "image/svg+xml",
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".gif": "image/gif",
            ".ico": "image/x-icon",
            ".woff": "font/woff",
            ".woff2": "font/woff2",
            ".ttf": "font/ttf",
            ".map": "application/json; charset=utf-8",
            ".txt": "text/plain; charset=utf-8",
            ".pdf": "application/pdf",
            ".mp4": "video/mp4",
        }.get(ext, "application/octet-stream")

    # ---------- 反向代理 ----------
    def _proxy(self, method, parsed):
        upstream = self.server.upstream
        path = parsed.path + ("?" + parsed.query if parsed.query else "")

        headers = {}
        for k, v in self.headers.items():
            lk = k.lower()
            if lk in ("host", "connection", "keep-alive", "proxy-connection",
                      "transfer-encoding", "upgrade", "content-length"):
                continue
            headers[k] = v
        headers["Host"] = upstream[1]
        headers["X-Real-IP"] = _client_ip(self)
        prior = self.headers.get("X-Forwarded-For")
        headers["X-Forwarded-For"] = (prior + ", " + _client_ip(self)) if prior else _client_ip(self)
        headers["X-Forwarded-Proto"] = "http"

        length = self.headers.get("Content-Length")
        body = None
        if length:
            body = _LimitedReader(self.rfile, int(length))

        conn = http.client.HTTPConnection(upstream[0], upstream[1], timeout=300)
        try:
            conn.putrequest(method, path, skip_host=True, skip_accept_encoding=True)
            for k, v in headers.items():
                conn.putheader(k, v)
            if length:
                conn.putheader("Content-Length", length)
            conn.endheaders(body)
            resp = conn.getresponse()

            self.send_response(resp.status, resp.reason)
            for k, v in resp.getheaders():
                lk = k.lower()
                if lk in ("connection", "transfer-encoding", "keep-alive"):
                    continue
                self.send_header(k, v)
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if method != "HEAD":
                while True:
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    self.wfile.flush()
        except Exception as e:  # noqa: BLE001 - 网关需对所有上游异常兜底
            msg = ("upstream error: %s\n" % e).encode()
            try:
                self._send_body(502, msg)
            except Exception:
                pass
        finally:
            conn.close()


class _LimitedReader:
    """按 Content-Length 精确读取请求体，供 http.client 流式转发。"""

    def __init__(self, rfile, length):
        self._rfile = rfile
        self._remaining = length

    def read(self, size=-1):
        if self._remaining <= 0:
            return b""
        if size is None or size < 0:
            size = self._remaining
        size = min(size, self._remaining)
        data = self._rfile.read(size)
        self._remaining -= len(data)
        return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=int(os.environ.get("GATEWAY_PORT", "6003")))
    ap.add_argument("--bind", default=os.environ.get("GATEWAY_BIND", "0.0.0.0"))
    ap.add_argument("--root", default=os.environ.get("APP_DIR", os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    ap.add_argument("--upstream", default=os.environ.get("UPSTREAM", "127.0.0.1:8088"))
    args = ap.parse_args()

    host, _, port = args.upstream.partition(":")
    srv = ThreadingHTTPServer((args.bind, args.port), Handler)
    srv.app_root = os.path.abspath(args.root)
    srv.upstream = (host or "127.0.0.1", int(port or 8088))
    srv.daemon_threads = True
    print("gateway %s:%s root=%s upstream=%s:%s" % (args.bind, args.port, srv.app_root, srv.upstream[0], srv.upstream[1]), flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


if __name__ == "__main__":
    main()
