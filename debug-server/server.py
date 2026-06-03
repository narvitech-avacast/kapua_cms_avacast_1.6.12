#!/usr/bin/env python3
"""Nginx access log → SSE debug server (pure stdlib)."""
import http.server, threading, time, os, re, json

LOG_FILE = os.environ.get("LOG_FILE", "/logs/access.log")
PORT = int(os.environ.get("PORT", "9999"))

LOG_RE = re.compile(
    r'(\S+) \S+ \S+ \[([^\]]+)\] "(\S+) ([^"]*?) HTTP/[\d\.]+" (\d+) (\d+) "([^"]*)" "([^"]*)"'
)

_clients: list[list] = []
_lock = threading.Lock()


def parse(line: str):
    m = LOG_RE.match(line.strip())
    if not m:
        return None
    ip, ts, method, path, status, size, ref, ua = m.groups()
    return dict(ip=ip, ts=ts.split()[0], method=method, path=path,
                status=int(status), size=int(size),
                ref="" if ref == "-" else ref)


def broadcast(obj):
    with _lock:
        for q in _clients:
            q.append(obj)


def tailer():
    while not os.path.exists(LOG_FILE):
        time.sleep(0.5)
    with open(LOG_FILE, errors="replace") as f:
        f.seek(0, 2)
        while True:
            line = f.readline()
            if line and line.strip():
                entry = parse(line)
                if entry:
                    broadcast(entry)
            else:
                time.sleep(0.1)


HTML = r"""<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Server Request Log</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0d1117;color:#e6edf3;height:100vh;display:flex;flex-direction:column;overflow:hidden}

/* ── Toolbar ── */
#toolbar{background:#161b22;border-bottom:1px solid #30363d;padding:10px 14px;display:flex;align-items:center;gap:10px;flex-shrink:0;flex-wrap:wrap}
#toolbar h1{font-size:.95rem;font-weight:600;color:#58a6ff;white-space:nowrap}
#dot{width:9px;height:9px;border-radius:50%;background:#f85149;flex-shrink:0;transition:background .3s}
#dot.ok{background:#3fb950}
#filter{flex:1;min-width:120px;background:#21262d;border:1px solid #30363d;border-radius:6px;color:#e6edf3;padding:5px 10px;font-size:.82rem;outline:none}
#filter:focus{border-color:#58a6ff}
#methodBtns{display:flex;gap:4px;flex-wrap:wrap}
.mbtn{border:1px solid #30363d;background:#21262d;color:#8b949e;border-radius:5px;padding:3px 9px;font-size:.75rem;cursor:pointer;transition:all .15s}
.mbtn.active{border-color:currentColor;color:#e6edf3;background:#30363d}
#count{font-size:.75rem;color:#8b949e;white-space:nowrap}
#clearBtn{background:#21262d;border:1px solid #30363d;color:#f85149;border-radius:5px;padding:3px 10px;font-size:.78rem;cursor:pointer}
#clearBtn:hover{background:#2d1f1f}
label{font-size:.78rem;color:#8b949e;display:flex;align-items:center;gap:4px;cursor:pointer;white-space:nowrap}

/* ── Table ── */
#wrap{flex:1;overflow-y:auto;overflow-x:auto}
table{width:100%;border-collapse:collapse;font-size:.8rem}
thead th{position:sticky;top:0;background:#161b22;border-bottom:1px solid #30363d;padding:7px 10px;text-align:left;color:#8b949e;font-weight:500;white-space:nowrap}
tbody tr{border-bottom:1px solid #21262d;transition:background .1s}
tbody tr:hover{background:#21262d}
tbody tr.hidden{display:none}
td{padding:5px 10px;vertical-align:top;word-break:break-all}
td.num{color:#484f58;width:44px;text-align:right}
td.ts{color:#8b949e;white-space:nowrap;width:90px;font-size:.75rem}
td.path{color:#e6edf3;font-family:'Courier New',monospace;max-width:340px}
td.ref{color:#8b949e;font-size:.75rem;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
td.sz{color:#8b949e;white-space:nowrap;text-align:right;width:70px}

/* ── Method badge ── */
.m{display:inline-block;padding:1px 7px;border-radius:4px;font-weight:600;font-size:.72rem;width:52px;text-align:center}
.m-GET{background:#1f3a5f;color:#58a6ff}
.m-POST{background:#1a3a28;color:#3fb950}
.m-PUT{background:#3a2b10;color:#e3b341}
.m-DELETE{background:#3a1515;color:#f85149}
.m-PATCH{background:#2d1f4a;color:#bc8cff}
.m-X{background:#21262d;color:#8b949e}

/* ── Status badge ── */
.s{display:inline-block;padding:1px 7px;border-radius:4px;font-weight:600;font-size:.78rem;min-width:36px;text-align:center}
.s2{background:#1a3a28;color:#3fb950}
.s3{background:#2e2c16;color:#e3b341}
.s4{background:#3a2010;color:#f0883e}
.s5{background:#3a1515;color:#f85149}

/* ── Empty state ── */
#empty{text-align:center;padding:60px 20px;color:#484f58;font-size:.9rem;display:none}
#empty.show{display:block}
</style>
</head>
<body>

<div id="toolbar">
  <h1>📡 Server Request Log</h1>
  <div id="dot" title="Disconnected"></div>
  <input id="filter" type="text" placeholder="Filter URL / method / status…">
  <div id="methodBtns">
    <button class="mbtn active" data-m="ALL">ALL</button>
    <button class="mbtn" data-m="GET" style="color:#58a6ff">GET</button>
    <button class="mbtn" data-m="POST" style="color:#3fb950">POST</button>
    <button class="mbtn" data-m="PUT" style="color:#e3b341">PUT</button>
    <button class="mbtn" data-m="DELETE" style="color:#f85149">DEL</button>
  </div>
  <span id="count">0 requests</span>
  <label><input type="checkbox" id="autoScroll" checked> Auto-scroll</label>
  <button id="clearBtn">Clear</button>
</div>

<div id="wrap">
  <table>
    <thead>
      <tr>
        <th>#</th>
        <th>Time</th>
        <th>Method</th>
        <th>Path</th>
        <th>Status</th>
        <th>Size</th>
        <th>Referer</th>
      </tr>
    </thead>
    <tbody id="tbody"></tbody>
  </table>
  <div id="empty">Waiting for requests…</div>
</div>

<script>
const tbody  = document.getElementById('tbody');
const filter = document.getElementById('filter');
const dot    = document.getElementById('dot');
const countEl= document.getElementById('count');
const wrap   = document.getElementById('wrap');
const empty  = document.getElementById('empty');
const autoSc = document.getElementById('autoScroll');

let total = 0;
let activeMethod = 'ALL';
const MAX_ROWS = 800;

/* ── SSE ── */
let es;
function connect() {
  es = new EventSource('/events');
  es.onopen = () => { dot.className = 'ok'; dot.title = 'Connected'; };
  es.onerror = () => {
    dot.className = '';
    dot.title = 'Reconnecting…';
    setTimeout(connect, 3000);
  };
  es.onmessage = e => addRow(JSON.parse(e.data));
}
connect();

/* ── Add row ── */
function addRow(d) {
  total++;
  updateCount();

  const m = d.method;
  const mClass = ['GET','POST','PUT','DELETE','PATCH'].includes(m) ? `m-${m}` : 'm-X';
  const sc = Math.floor(d.status / 100);
  const sClass = sc >= 2 && sc <= 5 ? `s${sc}` : 's5';
  const sz = d.size >= 1024 ? (d.size/1024).toFixed(1)+'k' : d.size+'b';
  const ref = d.ref ? new URL(d.ref, location.href).pathname + (new URL(d.ref, location.href).search || '') : '';

  const tr = document.createElement('tr');
  tr.dataset.method = m;
  tr.dataset.raw = `${m} ${d.path} ${d.status} ${d.ref}`.toLowerCase();
  tr.innerHTML = `
    <td class="num">${total}</td>
    <td class="ts">${d.ts.replace('T',' ')}</td>
    <td><span class="m ${mClass}">${m}</span></td>
    <td class="path">${esc(d.path)}</td>
    <td><span class="s ${sClass}">${d.status}</span></td>
    <td class="sz">${sz}</td>
    <td class="ref" title="${esc(d.ref)}">${esc(ref || d.ref)}</td>
  `;
  applyFilter(tr);
  tbody.appendChild(tr);
  pruneRows();

  if (autoSc.checked && !tr.classList.contains('hidden')) {
    wrap.scrollTop = wrap.scrollHeight;
  }
  updateEmpty();
}

/* ── Filter ── */
function applyFilter(tr) {
  const q = filter.value.toLowerCase();
  const mOk = activeMethod === 'ALL' || tr.dataset.method === activeMethod;
  const qOk = !q || tr.dataset.raw.includes(q);
  tr.classList.toggle('hidden', !mOk || !qOk);
}

function refilter() {
  for (const tr of tbody.rows) applyFilter(tr);
  updateEmpty();
  if (autoSc.checked) wrap.scrollTop = wrap.scrollHeight;
}

filter.addEventListener('input', refilter);

document.getElementById('methodBtns').addEventListener('click', e => {
  const btn = e.target.closest('.mbtn');
  if (!btn) return;
  activeMethod = btn.dataset.m;
  document.querySelectorAll('.mbtn').forEach(b => b.classList.toggle('active', b === btn));
  refilter();
});

/* ── Clear ── */
document.getElementById('clearBtn').addEventListener('click', () => {
  tbody.innerHTML = '';
  total = 0;
  updateCount();
  updateEmpty();
});

/* ── Prune old rows ── */
function pruneRows() {
  while (tbody.rows.length > MAX_ROWS) tbody.deleteRow(0);
}

/* ── Helpers ── */
function updateCount() {
  const vis = [...tbody.rows].filter(r => !r.classList.contains('hidden')).length;
  countEl.textContent = `${vis} / ${total} requests`;
}
function updateEmpty() {
  empty.classList.toggle('show', tbody.rows.length === 0);
}
function esc(s) {
  return (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
updateEmpty();
</script>
</body>
</html>"""


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/events":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            q: list = []
            with _lock:
                _clients.append(q)
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
        else:
            body = HTML.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def log_message(self, *args):
        pass


threading.Thread(target=tailer, daemon=True).start()
print(f"Debug server → http://0.0.0.0:{PORT}", flush=True)
http.server.HTTPServer(("", PORT), Handler).serve_forever()
