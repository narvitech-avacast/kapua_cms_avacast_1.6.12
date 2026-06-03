#!/usr/bin/env python3
"""
TCP probe: listens on multiple ports and logs every raw connection + first bytes.
Helps identify what port/data the TX device is actually sending.
Runs alongside the main proxy.
"""
import socket, threading, time, os, sys

# Ports to probe (anything the TX device might try)
PROBE_PORTS = [int(p) for p in os.environ.get("PROBE_PORTS", "8081,8082,8083,8444,8080").split(",")]

# Callback to record – will be set by main proxy
_record_fn = None

def set_recorder(fn):
    global _record_fn
    _record_fn = fn

def _handle(conn, addr, port):
    try:
        conn.settimeout(3)
        raw = b""
        try:
            raw = conn.recv(4096)
        except Exception:
            pass
        finally:
            conn.close()

        preview = raw[:512].decode("utf-8", errors="replace") if raw else ""
        entry = {
            "ts"         : time.strftime("%H:%M:%S"),
            "method"     : "TCP",
            "path"       : f":{port} ← {addr[0]}:{addr[1]}",
            "req_headers": {"Remote": f"{addr[0]}:{addr[1]}", "Port": str(port)},
            "req_body"   : preview or "(no data)",
            "res_status" : 0,
            "res_headers": {},
            "res_body"   : "(connection closed — port not routed through proxy)",
            "error"      : None,
            "duration_ms": 0,
        }
        if _record_fn:
            _record_fn(entry)
        print(f"[TCP probe] {addr[0]}:{addr[1]} → port {port}  {len(raw)} bytes", flush=True)
    except Exception as e:
        print(f"[TCP probe] handler error: {e}", flush=True)

def _listen(port):
    try:
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(("0.0.0.0", port))
        srv.listen(16)
        print(f"[TCP probe] listening on :{port}", flush=True)
        while True:
            conn, addr = srv.accept()
            threading.Thread(target=_handle, args=(conn, addr, port), daemon=True).start()
    except OSError as e:
        print(f"[TCP probe] port {port} unavailable: {e}", flush=True)

def start(record_fn):
    set_recorder(record_fn)
    for p in PROBE_PORTS:
        threading.Thread(target=_listen, args=(p,), daemon=True).start()
