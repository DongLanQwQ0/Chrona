#!/usr/bin/env node
// 本地通信管道 broker：零依赖，仅用 Node 内置模块。
// 职责：把共享日志索引成有序消息、推送（长轮询 / SSE / 浏览器看板）、记录回执与在线状态、导入文件投递。
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { commPaths, parseArgs, ensureDirs, isoLocal, newMessage, readLog, appendLog, matchesTarget } from './shared.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROJECT = path.resolve(HERE, '..', '..');
const args = parseArgs(process.argv.slice(2));
const HOST = String(args.host || process.env.PIPE_HOST || '127.0.0.1');
const PORT = Number(args.port || process.env.PIPE_PORT || 7801);
const COMM = path.resolve(String(args.comm || process.env.PIPE_COMM || path.join(PROJECT, 'docs', 'Communication')));

const P = commPaths(COMM);
ensureDirs(COMM);

const LOOPBACK = HOST === '127.0.0.1' || HOST === 'localhost' || HOST === '::1';

function ensureToken() {
  if (fs.existsSync(P.TOKEN)) {
    const t = fs.readFileSync(P.TOKEN, 'utf8').trim();
    if (t) return t;
  }
  const t = crypto.randomBytes(16).toString('hex');
  fs.writeFileSync(P.TOKEN, t + '\n', 'utf8');
  return t;
}

const TOKEN = LOOPBACK ? '' : ensureToken();

function log(tag, text) {
  const line = isoLocal() + ' [' + tag + '] ' + text;
  try { fs.appendFileSync(P.LOG, line + '\n', 'utf8'); } catch { /* 日志失败不影响服务 */ }
  process.stdout.write(line + '\n');
}

// ---------- 状态 ----------
let state = { seq: 0, msgSeq: {}, peers: {}, acked: {} };
if (fs.existsSync(P.STATE)) {
  try {
    const loaded = JSON.parse(fs.readFileSync(P.STATE, 'utf8'));
    state = Object.assign(state, loaded);
    state.peers = state.peers || {};
    state.acked = state.acked || {};
    state.msgSeq = state.msgSeq || {};
  } catch (err) {
    log('warn', 'state 读取失败，从空状态开始：' + err.message);
  }
}

let saveTimer = null;
function saveStateSoon() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    saveState();
  }, 200);
  saveTimer.unref();
}

function saveState() {
  try {
    fs.mkdirSync(P.PIPE, { recursive: true });
    fs.writeFileSync(P.STATE, JSON.stringify(state), 'utf8');
  } catch (err) {
    log('warn', 'state 写入失败：' + err.message);
  }
}

const index = [];
const byId = new Map();

function buildIndex() {
  const messages = readLog(COMM);
  for (const m of messages) {
    let s = state.msgSeq[m.id];
    if (!s) {
      state.seq += 1;
      s = state.seq;
      state.msgSeq[m.id] = s;
    }
    const entry = { seq: s, msg: m };
    index.push(entry);
    byId.set(m.id, entry);
  }
  index.sort((a, b) => a.seq - b.seq);
  if (index.length) state.seq = Math.max(state.seq, index[index.length - 1].seq);
  saveState();
  log('index', '载入 ' + index.length + ' 条消息，当前 seq=' + state.seq);
}

function mirror(entry) {
  const to = entry.msg.to;
  if (typeof to !== 'string' || to === '*' || to === 'all') return;
  const dir = path.join(P.OUTBOX, to);
  try {
    fs.mkdirSync(dir, { recursive: true });
    const name = String(entry.seq).padStart(5, '0') + '-' + entry.msg.id + '.md';
    const lines = [
      '---',
      'seq: ' + entry.seq,
      'id: ' + entry.msg.id,
      'ts: ' + entry.msg.ts,
      'from: ' + entry.msg.from,
      'to: ' + to,
      'kind: ' + entry.msg.kind,
      'thread: ' + entry.msg.thread,
      'subject: ' + entry.msg.subject,
      '---',
      '',
      entry.msg.body,
      '',
    ];
    fs.writeFileSync(path.join(dir, name), lines.join('\n'), 'utf8');
  } catch (err) {
    log('warn', '镜像写入失败：' + err.message);
  }
}

function isAcked(peer, id) {
  const list = state.acked[peer];
  return Array.isArray(list) && list.includes(id);
}

function touchPeer(id, role) {
  if (!id) return;
  const now = isoLocal();
  const rec = state.peers[id] || { id, firstSeen: now, requests: 0 };
  rec.lastSeen = now;
  rec.requests = (rec.requests || 0) + 1;
  if (role) rec.role = String(role);
  state.peers[id] = rec;
  saveStateSoon();
}

function ingest(raw) {
  const msg = newMessage(raw);
  const existing = byId.get(msg.id);
  if (existing) return existing;
  appendLog(COMM, msg);
  state.seq += 1;
  state.msgSeq[msg.id] = state.seq;
  const entry = { seq: state.seq, msg };
  index.push(entry);
  byId.set(msg.id, entry);
  mirror(entry);
  saveStateSoon();
  notify(entry);
  return entry;
}

function selectMessages(opts) {
  const { to, since = 0, thread, all = false, unread = false, limit = 0 } = opts;
  const out = [];
  for (const entry of index) {
    if (entry.seq <= since) continue;
    if (!all) {
      if (!to) continue;
      if (!matchesTarget(entry.msg, to)) continue;
      if (entry.msg.from === to) continue;
    }
    if (thread && entry.msg.thread !== thread) continue;
    if (unread && to && isAcked(to, entry.msg.id)) continue;
    out.push(Object.assign({}, entry.msg, { seq: entry.seq, acked: to ? isAcked(to, entry.msg.id) : false }));
  }
  if (limit > 0 && out.length > limit) return out.slice(out.length - limit);
  return out;
}

// ---------- 推送 ----------
const waiters = new Set();
const streams = new Set();

function notify(entry) {
  const payload = Object.assign({}, entry.msg, { seq: entry.seq });
  for (const w of Array.from(waiters)) {
    if (entry.seq <= w.since) continue;
    if (w.thread && payload.thread !== w.thread) continue;
    if (w.to && !matchesTarget(payload, w.to)) continue;
    if (w.to && payload.from === w.to) continue;
    w.batch.push(payload);
    flushWaiter(w);
  }
  for (const s of Array.from(streams)) {
    if (entry.seq <= s.since) continue;
    if (s.thread && payload.thread !== s.thread) continue;
    if (s.to && !matchesTarget(payload, s.to)) continue;
    if (s.to && payload.from === s.to) continue;
    try {
      s.res.write('event: message\nid: ' + entry.seq + '\ndata: ' + JSON.stringify(payload) + '\n\n');
    } catch {
      streams.delete(s);
    }
  }
}

function flushWaiter(w) {
  if (w.done) return;
  w.done = true;
  clearTimeout(w.timer);
  waiters.delete(w);
  const body = JSON.stringify({ ok: true, messages: w.batch, seq: state.seq, timeout: w.batch.length === 0 });
  w.res.writeHead(200, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
  });
  w.res.end(body);
}

// ---------- HTTP 工具 ----------
function sendJson(res, code, data) {
  const body = JSON.stringify(data);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
  });
  res.end(body);
}

function readJson(req, limit = 4 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(new Error('请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch (err) { reject(new Error('JSON 解析失败：' + err.message)); }
    });
    req.on('error', reject);
  });
}

function authorized(req, url) {
  if (LOOPBACK) return true;
  const t = req.headers['x-pipe-token'] || url.searchParams.get('token');
  return t === TOKEN;
}

function scanInbox() {
  if (!fs.existsSync(P.INBOX)) return 0;
  let count = 0;
  for (const d of fs.readdirSync(P.INBOX, { withFileTypes: true })) {
    if (!d.isDirectory() || d.name.startsWith('.')) continue;
    const dir = path.join(P.INBOX, d.name);
    for (const f of fs.readdirSync(dir)) {
      if (!f.toLowerCase().endsWith('.json')) continue;
      const full = path.join(dir, f);
      try {
        const raw = JSON.parse(fs.readFileSync(full, 'utf8'));
        if (!raw.to) raw.to = d.name;
        const entry = ingest(raw);
        fs.unlinkSync(full);
        count += 1;
        log('import', '文件投递 ' + d.name + '/' + f + ' -> seq ' + entry.seq);
      } catch (err) {
        log('warn', '导入失败 ' + d.name + '/' + f + '：' + err.message);
      }
    }
  }
  return count;
}

function renderBoard(thread) {
  const list = selectMessages({ all: true, thread });
  const lines = ['# 通信记录' + (thread ? ' · ' + thread : ''), ''];
  lines.push('消息数：' + list.length + '　当前 seq：' + state.seq + '　生成时间：' + isoLocal(), '');
  for (const m of list) {
    const to = Array.isArray(m.to) ? m.to.join(',') : m.to;
    lines.push('## #' + m.seq + '　' + m.from + ' → ' + to + '　[' + m.kind + ']');
    lines.push('');
    if (m.subject) lines.push('**' + m.subject + '**', '');
    lines.push('- 时间：' + m.ts);
    lines.push('- 线程：' + m.thread);
    if (m.replyTo) lines.push('- 回复：' + m.replyTo);
    if (m.refs && m.refs.length) lines.push('- 引用：' + m.refs.join(' · '));
    lines.push('');
    const body = String(m.body || '').trim();
    lines.push(body ? body : '（空正文）', '');
  }
  return lines.join('\n');
}

const HTML = [
  '<!doctype html>',
  '<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
  '<title>Chrona 通信管道</title>',
  '<style>',
  ':root{color-scheme:dark}',
  'body{margin:0;font:14px/1.6 ui-sans-serif,system-ui,"Segoe UI",sans-serif;background:#0f1115;color:#e6e8ee}',
  'header{padding:16px 20px;border-bottom:1px solid #23262e;display:flex;gap:16px;align-items:baseline}',
  'header h1{font-size:15px;margin:0;font-weight:600}',
  'header span{color:#79808d;font-size:12px}',
  'main{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:0;height:calc(100vh - 57px)}',
  '#list{overflow:auto;padding:12px 20px}',
  '.msg{border:1px solid #23262e;border-radius:6px;padding:10px 12px;margin:0 0 10px}',
  '.meta{color:#79808d;font-size:12px;margin-bottom:4px;display:flex;gap:8px;flex-wrap:wrap}',
  '.subj{font-weight:600}',
  '.body{white-space:pre-wrap;margin-top:6px}',
  'form{border-left:1px solid #23262e;padding:14px;display:flex;flex-direction:column;gap:8px;overflow:auto}',
  'input,select,textarea,button{font:inherit;background:#151922;color:#e6e8ee;border:1px solid #2b303b;border-radius:4px;padding:6px 8px}',
  'textarea{min-height:120px;resize:vertical}',
  'button{background:#2f6fed;border-color:#2f6fed;cursor:pointer}',
  'label{font-size:12px;color:#79808d}',
  '</style></head><body>',
  '<header><h1>Chrona 通信管道</h1><span id="stat">连接中</span></header>',
  '<main><div id="list"></div>',
  '<form id="compose">',
  '<label>from<input name="from" value="core"></label>',
  '<label>to<input name="to" value="ext"></label>',
  '<label>kind<select name="kind"><option>note</option><option>request</option><option>answer</option><option>question</option><option>decision</option><option>status</option><option>blocker</option><option>review</option><option>patch</option></select></label>',
  '<label>thread<input name="thread" value="general"></label>',
  '<label>subject<input name="subject"></label>',
  '<label>body<textarea name="body"></textarea></label>',
  '<button type="submit">发送</button>',
  '</form></main>',
  '<script>',
  'var last=0;',
  'function esc(t){var d=document.createElement("div");d.textContent=t;return d.innerHTML;}',
  'function add(m){var el=document.createElement("div");el.className="msg";',
  'var to=Array.isArray(m.to)?m.to.join(","):m.to;',
  'el.innerHTML="<div class=\\"meta\\"><span>#"+m.seq+"</span><span>"+esc(m.ts)+"</span><span>"+esc(m.from)+" -> "+esc(to)+"</span><span>"+esc(m.kind)+"</span><span>"+esc(m.thread)+"</span></div>"+',
  '("<div class=\\"subj\\">"+esc(m.subject||"(无标题)")+"</div>")+("<div class=\\"body\\">"+esc(m.body||"")+"</div>");',
  'document.getElementById("list").appendChild(el);}',
  'async function pull(){try{',
  'var r=await fetch("/messages?all=1&since="+last);var d=await r.json();',
  'if(d.messages&&d.messages.length){last=d.messages[d.messages.length-1].seq;d.messages.forEach(add);}',
  'document.getElementById("stat").textContent="seq "+d.seq+" · 在线 "+Object.keys(d.peers||{}).length;',
  '}catch(e){document.getElementById("stat").textContent="管道未响应";}}',
  'document.getElementById("compose").addEventListener("submit",async function(ev){ev.preventDefault();',
  'var f=new FormData(ev.target);var body={from:f.get("from"),to:f.get("to"),kind:f.get("kind"),thread:f.get("thread"),subject:f.get("subject"),body:f.get("body")};',
  'await fetch("/send",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(body)});',
  'ev.target.body.value="";pull();});',
  'pull();setInterval(pull,1500);',
  '</script></body></html>',
].join('\n');

async function handle(req, res) {
  const url = new URL(req.url, 'http://' + (req.headers.host || 'localhost'));
  const method = (req.method || 'GET').toUpperCase();
  const pathname = url.pathname.replace(/\/+$/, '') || '/';

  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-headers': 'content-type,x-pipe-token',
      'access-control-allow-methods': 'GET,POST,OPTIONS',
    });
    res.end();
    return;
  }

  if (pathname !== '/' && pathname !== '/health' && !authorized(req, url)) {
    sendJson(res, 401, { ok: false, error: '缺少或错误的令牌' });
    return;
  }

  if (pathname === '/' && method === 'GET') {
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
    res.end(HTML);
    return;
  }

  if (pathname === '/health') {
    sendJson(res, 200, {
      ok: true,
      pid: process.pid,
      host: HOST,
      port: PORT,
      comm: COMM,
      seq: state.seq,
      count: index.length,
      peers: state.peers,
      startedAt: STARTED_AT,
    });
    return;
  }

  const as = url.searchParams.get('as') || url.searchParams.get('peer');
  if (as) touchPeer(as);

  if (pathname === '/send' && method === 'POST') {
    const body = await readJson(req);
    const entry = ingest(body);
    log('send', '#' + entry.seq + ' ' + entry.msg.from + ' -> ' + (Array.isArray(entry.msg.to) ? entry.msg.to.join(',') : entry.msg.to) + ' [' + entry.msg.kind + '] ' + entry.msg.subject);
    sendJson(res, 200, { ok: true, id: entry.msg.id, seq: entry.seq, message: Object.assign({}, entry.msg, { seq: entry.seq }) });
    return;
  }

  if (pathname === '/messages' && method === 'GET') {
    const to = url.searchParams.get('to') || '';
    const since = Number(url.searchParams.get('since') || 0) || 0;
    const thread = url.searchParams.get('thread') || '';
    const all = url.searchParams.get('all') === '1';
    const unread = url.searchParams.get('unread') === '1';
    const limit = Number(url.searchParams.get('limit') || 0) || 0;
    const messages = selectMessages({ to, since, thread, all, unread, limit });
    sendJson(res, 200, { ok: true, seq: state.seq, count: messages.length, messages, peers: state.peers });
    return;
  }

  if (pathname === '/wait' && method === 'GET') {
    const to = url.searchParams.get('to') || '';
    const since = Number(url.searchParams.get('since') || 0) || 0;
    const thread = url.searchParams.get('thread') || '';
    const timeout = Math.max(0, Math.min(Number(url.searchParams.get('timeout') || 30000) || 0, 120000));
    if (to) touchPeer(to);
    const ready = selectMessages({ to, since, thread });
    if (ready.length || timeout === 0) {
      sendJson(res, 200, { ok: true, messages: ready, seq: state.seq, timeout: ready.length === 0 });
      return;
    }
    const waiter = { res, to, since, thread, batch: [], done: false, timer: null };
    waiter.timer = setTimeout(() => flushWaiter(waiter), timeout);
    waiters.add(waiter);
    req.on('close', () => {
      if (!waiter.done) {
        waiter.done = true;
        clearTimeout(waiter.timer);
        waiters.delete(waiter);
      }
    });
    return;
  }

  if (pathname === '/stream' && method === 'GET') {
    const to = url.searchParams.get('to') || '';
    const since = Number(url.searchParams.get('since') || 0) || 0;
    const thread = url.searchParams.get('thread') || '';
    if (to) touchPeer(to);
    res.writeHead(200, {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-store',
      connection: 'keep-alive',
      'access-control-allow-origin': '*',
    });
    res.write('retry: 2000\n\n');
    const stream = { res, to, since, thread };
    streams.add(stream);
    const beat = setInterval(() => {
      try { res.write(': ping ' + Date.now() + '\n\n'); } catch { /* 连接已断 */ }
    }, 25000);
    beat.unref();
    req.on('close', () => {
      clearInterval(beat);
      streams.delete(stream);
    });
    return;
  }

  if (pathname === '/ack' && method === 'POST') {
    const body = await readJson(req);
    const peer = String(body.peer || body.as || '');
    const ids = Array.isArray(body.ids) ? body.ids.map(String) : [].concat(body.id || []);
    if (!peer) throw new Error('ack.peer 缺失');
    const list = Array.isArray(state.acked[peer]) ? state.acked[peer] : [];
    for (const id of ids) if (id && !list.includes(id)) list.push(id);
    state.acked[peer] = list;
    saveStateSoon();
    sendJson(res, 200, { ok: true, peer, acked: list.length });
    return;
  }

  if (pathname === '/peers' && method === 'GET') {
    let declared = [];
    const file = path.join(COMM, 'peers.json');
    if (fs.existsSync(file)) {
      try { declared = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { declared = []; }
    }
    sendJson(res, 200, { ok: true, declared, live: state.peers });
    return;
  }

  if (pathname === '/peer' && method === 'POST') {
    const body = await readJson(req);
    if (!body.id) throw new Error('peer.id 缺失');
    touchPeer(String(body.id), body.role);
    sendJson(res, 200, { ok: true, peer: state.peers[String(body.id)] });
    return;
  }

  if (pathname === '/board' && method === 'GET') {
    const thread = url.searchParams.get('thread') || '';
    const text = renderBoard(thread);
    res.writeHead(200, { 'content-type': 'text/markdown; charset=utf-8', 'cache-control': 'no-store' });
    res.end(text);
    return;
  }

  if (pathname === '/import' && method === 'POST') {
    const count = scanInbox();
    sendJson(res, 200, { ok: true, imported: count, seq: state.seq });
    return;
  }

  if (pathname === '/shutdown' && method === 'POST') {
    saveState();
    sendJson(res, 200, { ok: true, stopping: true });
    log('stop', '收到停止指令');
    setTimeout(() => process.exit(0), 60);
    return;
  }

  sendJson(res, 404, { ok: false, error: '未知路由 ' + method + ' ' + pathname });
}

const STARTED_AT = isoLocal();
buildIndex();

const server = http.createServer((req, res) => {
  handle(req, res).catch((err) => {
    log('error', (err && err.stack) || String(err));
    if (!res.headersSent) sendJson(res, 400, { ok: false, error: String((err && err.message) || err) });
    else res.end();
  });
});

server.on('error', (err) => {
  log('error', '监听失败：' + err.message);
  process.exit(1);
});

server.listen(PORT, HOST, () => {
  const baseUrl = 'http://' + (HOST === '0.0.0.0' ? '127.0.0.1' : HOST) + ':' + PORT;
  fs.writeFileSync(P.ENDPOINT, JSON.stringify({
    baseUrl,
    bind: HOST,
    port: PORT,
    token: TOKEN,
    pid: process.pid,
    comm: COMM,
    startedAt: STARTED_AT,
  }, null, 2), 'utf8');
  fs.writeFileSync(P.PID, String(process.pid), 'utf8');
  log('start', '管道就绪 ' + baseUrl + '，数据目录 ' + COMM);
});

const importTimer = setInterval(() => {
  try { scanInbox(); } catch (err) { log('warn', '扫描投递箱失败：' + err.message); }
}, 1500);
importTimer.unref();

function shutdown(signal) {
  log('stop', '收到 ' + signal + '，保存状态后退出');
  saveState();
  try { fs.rmSync(P.PID, { force: true }); } catch { /* 忽略 */ }
  process.exit(0);
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('uncaughtException', (err) => log('error', '未捕获异常：' + ((err && err.stack) || err)));
