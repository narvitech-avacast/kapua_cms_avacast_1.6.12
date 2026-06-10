#!/usr/bin/env python3
"""
Kapua API Debug Proxy
- Port 7080 : transparent reverse proxy → kapua-api:8080 (logs everything)
- Port 9999 : debug UI + SSE stream
"""
import gzip, http.client, http.server, json, os, threading, time, traceback
from collections import deque
from datetime import datetime, timezone

BACKEND      = os.environ.get("BACKEND", "kapua-api:8080")
PROXY_PORT   = int(os.environ.get("PROXY_PORT", "7080"))
UI_PORT      = int(os.environ.get("UI_PORT",    "9999"))
MAX_BODY     = 128 * 1024   # 128 KB body capture limit
MAX_HISTORY  = 300

_history: deque = deque(maxlen=MAX_HISTORY)
_clients: list  = []
_lock           = threading.Lock()
_seq            = 0

TEXT_CTS = {
    "application/json", "application/xml", "text/xml",
    "text/plain", "text/html", "application/x-www-form-urlencoded",
}

def is_text(ct: str) -> bool:
    return (ct or "").split(";")[0].strip().lower() in TEXT_CTS

def decode_body(raw: bytes, ct: str, enc: str) -> str:
    if not raw:
        return ""
    data = raw
    if enc and "gzip" in enc:
        try:
            data = gzip.decompress(raw)
        except Exception:
            pass
    if not is_text(ct):
        return f"[{len(raw)} bytes binary]"
    text = data[:MAX_BODY].decode("utf-8", errors="replace")
    if "json" in ct:
        try:
            text = json.dumps(json.loads(text), ensure_ascii=False, indent=2)
        except Exception:
            pass
    return text

def broadcast(obj):
    with _lock:
        for q in _clients:
            q.append(obj)

def record(entry):
    global _seq
    with _lock:
        _seq += 1
        entry["id"] = _seq
    _history.append(entry)
    broadcast(entry)


# ── Token → ScopeId enrichment store ─────────────────────────────────────────
import re as _re

_token_scope: dict = {}   # { token_value: scope_id }
_token_lock  = threading.Lock()

def _store_token(path: str, res_body: str):
    """After registrationToken succeeds, remember token → scopeId."""
    m = _re.search(r'/v1/([^/]+)/devices/registrationToken', path)
    if not m:
        return
    scope_id = m.group(1)
    try:
        token = json.loads(res_body).get("value", "")
        if token and not _re.match(r'^\d{3,4}:', token):
            with _token_lock:
                _token_scope[token] = scope_id
            print(f"[enrich] stored token→scope: ...{token[-8:]} → {scope_id}", flush=True)
    except Exception:
        pass

def _enrich_no_auth(req_raw: bytes) -> tuple[bytes, dict]:
    """Inject missing scopeId / clientId into devicesNoAuth request body."""
    injected = {}
    try:
        body = json.loads(req_raw)
    except Exception:
        return req_raw, injected

    if not body.get("scopeId") and body.get("accessToken"):
        with _token_lock:
            scope_id = _token_scope.get(body["accessToken"])
        if scope_id:
            body["scopeId"] = scope_id
            injected["scopeId"] = scope_id

    if not body.get("clientId") and body.get("accessToken"):
        token = body["accessToken"]
        cid = "tx-" + token.replace("-", "").replace("_", "")[-12:]
        body["clientId"] = cid
        injected["clientId"] = cid

    if injected:
        print(f"[enrich] injected {injected} into devicesNoAuth request", flush=True)
        return json.dumps(body, ensure_ascii=False).encode(), injected
    return req_raw, injected


# ── Proxy handler ────────────────────────────────────────────────────────────

class ProxyHandler(http.server.BaseHTTPRequestHandler):

    def handle_any(self):
        entry = {
            "ts"          : datetime.now(timezone.utc).strftime("%H:%M:%S.%f")[:-3],
            "method"      : self.command,
            "path"        : self.path,
            "req_headers" : dict(self.headers),
            "req_body"    : "",
            "res_status"  : None,
            "res_headers" : {},
            "res_body"    : "",
            "error"       : None,
            "duration_ms" : 0,
        }

        # ── read request body ────────────────────────────────
        cl = int(self.headers.get("Content-Length", 0) or 0)
        req_raw = b""
        if cl > 0:
            req_raw = self.rfile.read(min(cl, MAX_BODY))

        # ── enrich devicesNoAuth request if fields missing ───
        _injected = {}
        if "devicesNoAuth" in self.path and req_raw:
            req_raw, _injected = _enrich_no_auth(req_raw)

        entry["req_body"] = decode_body(
            req_raw,
            self.headers.get("Content-Type", ""),
            self.headers.get("Content-Encoding", ""),
        )
        if _injected:
            entry["req_body"] += f"\n\n⚡ Auto-injected: {_injected}"

        # ── forward to backend ───────────────────────────────
        t0 = time.time()
        try:
            host, port = (BACKEND.split(":") + ["80"])[:2]
            conn = http.client.HTTPConnection(host, int(port), timeout=30)

            fwd = {}
            for k, v in self.headers.items():
                if k.lower() not in ("connection", "transfer-encoding", "content-length"):
                    fwd[k] = v
            # Keep nginx's Host header (e.g. localhost:8443) so Jetty builds the
            # correct SSO callback URI. Only fall back if nginx sent nothing.
            fwd.setdefault("Host", BACKEND)
            fwd.setdefault("X-Forwarded-Proto", "http")
            if _injected:
                fwd["Content-Length"] = str(len(req_raw))

            conn.request(self.command, self.path, body=req_raw or None, headers=fwd)
            resp = conn.getresponse()
            res_raw = resp.read()
            entry["duration_ms"] = int((time.time() - t0) * 1000)

            entry["res_status"]  = resp.status
            entry["res_headers"] = dict(resp.getheaders())
            res_ct  = resp.getheader("Content-Type", "")
            res_enc = resp.getheader("Content-Encoding", "")
            entry["res_body"] = decode_body(res_raw, res_ct, res_enc)

            # ── store token→scope after registrationToken ────
            if "registrationToken" in self.path and self.command == "POST" and resp.status == 200:
                _store_token(self.path, entry["res_body"])

            # send response to client
            self.send_response(resp.status)
            for k, v in resp.getheaders():
                if k.lower() not in ("connection", "transfer-encoding", "content-length"):
                    self.send_header(k, v)
            self.send_header("Content-Length", str(len(res_raw)))
            self.end_headers()
            self.wfile.write(res_raw)
            conn.close()

        except Exception:
            entry["error"]        = traceback.format_exc()
            entry["duration_ms"]  = int((time.time() - t0) * 1000)
            entry["res_status"]   = 502
            self.send_response(502)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(entry["error"].encode())

        record(entry)

    do_GET     = handle_any
    do_POST    = handle_any
    do_PUT     = handle_any
    do_DELETE  = handle_any
    do_PATCH   = handle_any
    do_OPTIONS = handle_any
    do_HEAD    = handle_any

    def log_message(self, *args):
        pass


# ── UI / SSE handler ─────────────────────────────────────────────────────────

class UIHandler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == "/events":
            self._sse()
        elif self.path == "/history":
            self._json(list(_history))
        else:
            body = HTML.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def _sse(self):
        self.send_response(200)
        self.send_header("Content-Type",  "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Connection",    "keep-alive")
        self.end_headers()
        q: list = []
        with _lock:
            _clients.append(q)
            for h in list(_history):   # replay history on connect
                q.append(h)
        try:
            while True:
                with _lock:
                    items, q[:] = q[:], []
                for it in items:
                    self.wfile.write(f"data: {json.dumps(it)}\n\n".encode())
                self.wfile.flush()
                time.sleep(0.2)
        except Exception:
            with _lock:
                if q in _clients:
                    _clients.remove(q)

    def _json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


# ── HTML ─────────────────────────────────────────────────────────────────────

HTML = r"""<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<title>API Debug Proxy</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
:root{--bg:#0d1117;--bg2:#161b22;--bg3:#21262d;--bd:#30363d;--tx:#e6edf3;--tx2:#8b949e;
      --blue:#58a6ff;--green:#3fb950;--yellow:#e3b341;--orange:#f0883e;--red:#f85149;--purple:#bc8cff}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:var(--bg);color:var(--tx);
     height:100vh;display:flex;flex-direction:column;overflow:hidden;font-size:13px}

/* toolbar */
#tb{background:var(--bg2);border-bottom:1px solid var(--bd);padding:8px 12px;
    display:flex;align-items:center;gap:8px;flex-shrink:0;flex-wrap:wrap}
#tb h1{font-size:.9rem;font-weight:600;color:var(--blue);white-space:nowrap}
#dot{width:8px;height:8px;border-radius:50%;background:var(--red);transition:background .3s;flex-shrink:0}
#dot.ok{background:var(--green)}
#fi{flex:1;min-width:100px;background:var(--bg3);border:1px solid var(--bd);
    border-radius:5px;color:var(--tx);padding:4px 9px;font-size:.8rem;outline:none}
#fi:focus{border-color:var(--blue)}
.mbtn{border:1px solid var(--bd);background:var(--bg3);color:var(--tx2);border-radius:4px;
      padding:2px 8px;font-size:.72rem;cursor:pointer;transition:all .15s}
.mbtn.on{background:var(--bd);color:var(--tx)}
#cnt{font-size:.72rem;color:var(--tx2);white-space:nowrap}
#clr{background:var(--bg3);border:1px solid var(--bd);color:var(--red);
     border-radius:4px;padding:2px 9px;font-size:.75rem;cursor:pointer}

/* layout */
#layout{flex:1;display:flex;overflow:hidden}

/* left list */
#list{width:38%;min-width:220px;border-right:1px solid var(--bd);
      overflow-y:auto;overflow-x:hidden;flex-shrink:0}
.row{display:grid;grid-template-columns:32px 56px 48px 1fr 40px;
     gap:4px;align-items:center;padding:5px 8px;cursor:pointer;
     border-bottom:1px solid #1a1f27;transition:background .1s}
.row:hover{background:var(--bg3)}
.row.sel{background:#1c2330;border-left:2px solid var(--blue)}
.row.hidden{display:none}
.rn{color:var(--tx2);font-size:.7rem;text-align:right}
.rts{color:var(--tx2);font-size:.7rem;font-family:monospace}
.rpath{color:var(--tx);font-size:.75rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rdur{color:var(--tx2);font-size:.7rem;text-align:right}

/* badges */
.m,.s{display:inline-block;padding:1px 5px;border-radius:3px;font-weight:600;font-size:.68rem;text-align:center}
.mGET{background:#1f3a5f;color:var(--blue)} .mPOST{background:#1a3a28;color:var(--green)}
.mPUT{background:#3a2b10;color:var(--yellow)} .mDELETE{background:#3a1515;color:var(--red)}
.mPATCH{background:#2d1f4a;color:var(--purple)} .mX{background:var(--bg3);color:var(--tx2)}
.mMQTT{background:#0d2e35;color:#56d4e8} .smqtt{background:#0d2e35;color:#56d4e8}
.s2{background:#1a3a28;color:var(--green)} .s3{background:#2e2c16;color:var(--yellow)}
.s4{background:#3a2010;color:var(--orange)} .s5{background:#3a1515;color:var(--red)}

/* right detail */
#detail{flex:1;overflow:hidden;display:flex;flex-direction:column}
#empty-msg{flex:1;display:flex;align-items:center;justify-content:center;
           color:var(--tx2);font-size:.85rem}
#pane{flex:1;overflow:hidden;display:none;flex-direction:column}
#overview{padding:10px 14px;border-bottom:1px solid var(--bd);flex-shrink:0}
#ov-line1{font-size:.9rem;font-weight:600;color:var(--tx);margin-bottom:5px;word-break:break-all}
#ov-line2{font-size:.78rem;color:var(--tx2);display:flex;gap:14px;flex-wrap:wrap}
#device-result{display:none;margin-top:8px;padding:7px 9px;border-radius:5px;font-size:.78rem;font-weight:600}
#device-result.ok{display:block;background:#1a3a28;color:var(--green);border:1px solid #286b3b}
#device-result.warn{display:block;background:#3a2b10;color:var(--yellow);border:1px solid #765c1d}
#device-result.err{display:block;background:#3a1515;color:var(--red);border:1px solid #7d2b2b}
#tabs{display:flex;gap:0;border-bottom:1px solid var(--bd);flex-shrink:0}
.tab{padding:7px 16px;font-size:.8rem;cursor:pointer;color:var(--tx2);border-bottom:2px solid transparent;transition:all .15s}
.tab.on{color:var(--blue);border-bottom-color:var(--blue)}
#tabcontent{flex:1;overflow:auto}

/* section layout inside tab */
.section{padding:10px 14px}
.section+.section{border-top:1px solid var(--bd)}
.sec-title{font-size:.72rem;font-weight:600;color:var(--tx2);text-transform:uppercase;
           letter-spacing:.05em;margin-bottom:8px}
table.hdrs{width:100%;border-collapse:collapse;font-size:.78rem}
table.hdrs td{padding:3px 6px;vertical-align:top}
table.hdrs td:first-child{color:var(--tx2);width:38%;white-space:nowrap}
table.hdrs td:last-child{color:var(--tx);font-family:monospace;word-break:break-all}
table.hdrs tr:nth-child(even) td{background:#0f131a}

/* code block */
.code{background:#0d1117;border:1px solid var(--bd);border-radius:6px;
      padding:10px 12px;font-family:'Courier New',monospace;font-size:.78rem;
      white-space:pre-wrap;word-break:break-all;max-height:480px;overflow-y:auto;line-height:1.5}
.err-block{background:#1c0f0f;border:1px solid var(--red);border-radius:6px;
           padding:10px 12px;font-size:.75rem;color:var(--red);white-space:pre-wrap;
           word-break:break-all;max-height:200px;overflow-y:auto}

/* json syntax */
.jk{color:#79c0ff} .jstr{color:#a5d6ff} .jnum{color:#ffa657} .jbool{color:#ff7b72} .jnull{color:#ff7b72}

/* scrollbar */
::-webkit-scrollbar{width:6px;height:6px}
::-webkit-scrollbar-track{background:var(--bg)}
::-webkit-scrollbar-thumb{background:var(--bd);border-radius:3px}
</style>
</head>
<body>
<div id="tb">
  <h1>🔍 API Debug Proxy</h1>
  <div id="dot" title="Disconnected"></div>
  <input id="fi" type="text" placeholder="Filter path / status / method…">
  <button class="mbtn on" data-m="ALL">ALL</button>
  <button class="mbtn" data-m="GET">GET</button>
  <button class="mbtn" data-m="POST">POST</button>
  <button class="mbtn" data-m="PUT">PUT</button>
  <button class="mbtn" data-m="DELETE">DEL</button>
  <button class="mbtn" data-m="MQTT">MQTT</button>
  <span id="cnt">0</span>
  <button id="clr">Clear</button>
</div>

<div id="layout">
  <div id="list"></div>
  <div id="detail">
    <div id="empty-msg">← 選擇左側請求查看詳細內容</div>
    <div id="pane">
      <div id="overview">
        <div id="ov-line1"></div>
        <div id="ov-line2"></div>
        <div id="device-result"></div>
      </div>
      <div id="tabs">
        <div class="tab on" data-t="req">Request</div>
        <div class="tab"    data-t="res">Response</div>
      </div>
      <div id="tabcontent"></div>
    </div>
  </div>
</div>

<script>
const $ = id => document.getElementById(id);
const list = $('list'), dot = $('dot'), cnt = $('cnt'), fi = $('fi');
const pane = $('pane'), emptyMsg = $('empty-msg');

let rows = [], selId = null, activeM = 'ALL', activeTab = 'req';
const data = {};   // id → entry

/* ── SSE ────────────────────────────────────────────────── */
let es;
function connect() {
  es = new EventSource('/events');
  es.onopen  = () => { dot.className = 'ok'; dot.title = 'Connected'; };
  es.onerror = () => { dot.className = ''; dot.title = 'Reconnecting…'; setTimeout(connect, 2000); };
  es.onmessage = e => addEntry(JSON.parse(e.data));
}
connect();

/* ── Add entry ──────────────────────────────────────────── */
function addEntry(d) {
  data[d.id] = d;
  const m = d.method || '?';
  const mc = ['GET','POST','PUT','DELETE','PATCH','MQTT'].includes(m) ? m : 'X';
  const sc = d.res_status ? Math.floor(d.res_status / 100) : 0;
  const statusText = m === 'MQTT' ? 'MSG' : (d.res_status || '…');
  const statusCls  = m === 'MQTT' ? 'mqtt' : (sc || 'X');

  const row = document.createElement('div');
  row.className = 'row';
  row.dataset.id = d.id;
  row.dataset.raw = `${m} ${d.path} ${d.res_status || ''}`.toLowerCase();
  row.dataset.method = m;
  row.innerHTML = `
    <span class="rn">${d.id}</span>
    <span class="rts">${d.ts}</span>
    <span class="m m${mc}">${m}</span>
    <span class="rpath" title="${esc(d.path)}">${esc(d.path)}</span>
    <span class="s s${statusCls}">${statusText}</span>`;
  row.addEventListener('click', () => select(d.id));

  applyFilter(row);
  list.appendChild(row);
  rows.push(row);
  pruneRows();
  updateCnt();

  if (selId === null && !row.classList.contains('hidden')) {
    select(d.id);
  }
  // auto-scroll list if near bottom
  if (list.scrollTop + list.clientHeight > list.scrollHeight - 60) {
    list.scrollTop = list.scrollHeight;
  }
}

/* ── Select entry ───────────────────────────────────────── */
function select(id) {
  selId = id;
  rows.forEach(r => r.classList.toggle('sel', +r.dataset.id === id));
  const d = data[id];
  if (!d) return;

  emptyMsg.style.display = 'none';
  pane.style.display = 'flex';

  const m = d.method;
  const mc = ['GET','POST','PUT','DELETE','PATCH','MQTT'].includes(m) ? m : 'X';
  const sc = d.res_status ? Math.floor(d.res_status/100) : 0;

  $('ov-line1').innerHTML =
    `<span class="m m${mc}">${m}</span> <span style="font-family:monospace">${esc(d.path)}</span>`;
  $('ov-line2').innerHTML = [
    m === 'MQTT' ? `<b class="s smqtt" style="font-size:.8rem">MQTT MSG</b>` : (d.res_status ? `<b class="s s${sc}" style="font-size:.8rem">${d.res_status}</b>` : ''),
    d.duration_ms ? `⏱ ${d.duration_ms} ms` : '',
    d.req_headers?.['x-real-ip'] || d.req_headers?.['X-Real-IP'] ? `IP: ${d.req_headers['x-real-ip']||d.req_headers['X-Real-IP']}` : '',
  ].filter(Boolean).join('&ensp;·&ensp;');

  renderDeviceResult(d);
  renderTab(d);
}

function renderDeviceResult(d) {
  const box = $('device-result');
  box.className = '';
  box.textContent = '';
  if (!d.path?.includes('/devicesNoAuth/getAndroidGatewayConfigByAccessToken')) return;

  const response = d.res_body || '';
  if (response.includes('"value": "GatewayConfig"') ||
      response.includes('"value":"GatewayConfig"') ||
      response.includes('value="GatewayConfig"')) {
    box.className = 'ok';
    box.textContent = 'CMS 已收到：裝置新增成功，Gateway Config 已回傳';
  } else if (response.includes('5200:USER_NOT_FIND')) {
    box.className = 'warn';
    box.textContent = 'CMS 已收到：裝置請求已處理，但找不到 Broker User，Gateway Config 產生失敗';
  } else if (response.includes('401:INVALID_OR_EXPIRED_DEVICE_TOKEN')) {
    box.className = 'err';
    box.textContent = 'CMS 已收到：Registration Token 無效、過期或已使用';
  } else if (d.res_status) {
    box.className = d.res_status < 400 ? 'warn' : 'err';
    box.textContent = `CMS 已收到：請查看 Response Body（HTTP ${d.res_status}）`;
  }
}

/* ── Tabs ───────────────────────────────────────────────── */
document.getElementById('tabs').addEventListener('click', e => {
  const t = e.target.closest('.tab');
  if (!t) return;
  activeTab = t.dataset.t;
  document.querySelectorAll('.tab').forEach(x => x.classList.toggle('on', x === t));
  if (selId) renderTab(data[selId]);
});

function renderTab(d) {
  const tc = $('tabcontent');
  if (activeTab === 'req') {
    tc.innerHTML = `
      <div class="section">
        <div class="sec-title">Headers</div>
        ${hdrsTable(d.req_headers)}
      </div>
      <div class="section">
        <div class="sec-title">Body</div>
        ${bodyBlock(d.req_body)}
      </div>`;
  } else {
    tc.innerHTML = `
      <div class="section">
        <div class="sec-title">Status &amp; Headers</div>
        ${hdrsTable(d.res_headers)}
      </div>
      <div class="section">
        <div class="sec-title">Body</div>
        ${bodyBlock(d.res_body)}
        ${d.error ? `<div class="err-block" style="margin-top:8px">${esc(d.error)}</div>` : ''}
      </div>`;
  }
}

/* ── Helpers ────────────────────────────────────────────── */
function hdrsTable(hdrs) {
  if (!hdrs || !Object.keys(hdrs).length) return '<span style="color:var(--tx2);font-size:.78rem">（無）</span>';
  const rows = Object.entries(hdrs).map(([k,v]) =>
    `<tr><td>${esc(k)}</td><td>${esc(Array.isArray(v)?v.join(', '):v)}</td></tr>`).join('');
  return `<table class="hdrs"><tbody>${rows}</tbody></table>`;
}

function bodyBlock(body) {
  if (!body) return '<span style="color:var(--tx2);font-size:.78rem">（空）</span>';
  return `<div class="code">${syntaxHighlight(body)}</div>`;
}

function syntaxHighlight(str) {
  const s = esc(str);
  try {
    // quick json highlight
    return s.replace(/"(\\u[0-9a-fA-F]{4}|\\[^u]|[^"\\])*"(\s*:)?|true|false|null|-?\d+(\.\d+)?([eE][+-]?\d+)?/g, m => {
      if (/^"/.test(m)) return m.endsWith(':') ? `<span class="jk">${m}</span>` : `<span class="jstr">${m}</span>`;
      if (/true|false/.test(m)) return `<span class="jbool">${m}</span>`;
      if (/null/.test(m)) return `<span class="jnull">${m}</span>`;
      return `<span class="jnum">${m}</span>`;
    });
  } catch { return s; }
}

function esc(s) {
  return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

/* ── Filter ─────────────────────────────────────────────── */
function applyFilter(row) {
  const q = fi.value.toLowerCase();
  const mOk = activeM === 'ALL' || row.dataset.method === activeM;
  const qOk = !q || row.dataset.raw.includes(q);
  row.classList.toggle('hidden', !mOk || !qOk);
}
function refilter() {
  rows.forEach(applyFilter);
  updateCnt();
}
fi.addEventListener('input', refilter);
document.querySelector('#tb').addEventListener('click', e => {
  const b = e.target.closest('.mbtn');
  if (!b) return;
  activeM = b.dataset.m;
  document.querySelectorAll('.mbtn').forEach(x => x.classList.toggle('on', x === b));
  refilter();
});

/* ── Clear ──────────────────────────────────────────────── */
$('clr').addEventListener('click', () => {
  list.innerHTML = ''; rows = []; selId = null;
  pane.style.display = 'none'; emptyMsg.style.display = '';
  updateCnt();
});

/* ── Prune / Count ──────────────────────────────────────── */
function pruneRows() {
  while (rows.length > 600) {
    const old = rows.shift();
    old.remove();
  }
}
function updateCnt() {
  const vis = rows.filter(r => !r.classList.contains('hidden')).length;
  cnt.textContent = `${vis} / ${rows.length}`;
}
</script>
</body>
</html>"""


# ── Start servers ─────────────────────────────────────────────────────────────

class ThreadedHTTP(http.server.ThreadingHTTPServer):
    daemon_threads = True

def start(handler, port, name):
    srv = ThreadedHTTP(("", port), handler)
    t = threading.Thread(target=srv.serve_forever, name=name, daemon=True)
    t.start()
    print(f"{name} → http://0.0.0.0:{port}", flush=True)


start(ProxyHandler, PROXY_PORT, "Proxy")
start(UIHandler,    UI_PORT,    "UI")

import tcp_probe
tcp_probe.start(record)

import mqtt_subscriber
mqtt_subscriber.start(record)

print("Ready.", flush=True)
threading.Event().wait()
