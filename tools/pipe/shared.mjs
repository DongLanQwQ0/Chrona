// 通信管道共享定义：路径、消息格式、日志读写。broker 与客户端都依赖本文件。
import fs from 'node:fs';
import path from 'node:path';

export const KINDS = ['request', 'answer', 'question', 'note', 'decision', 'status', 'blocker', 'patch', 'review'];

export function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === '--') continue;
    if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      let value;
      if (next === undefined || next.startsWith('--')) value = true;
      else { value = next; i += 1; }
      if (Object.prototype.hasOwnProperty.call(out, key)) out[key] = [].concat(out[key], value);
      else out[key] = value;
    } else {
      out._.push(a);
    }
  }
  return out;
}

export function commPaths(commDir) {
  const COMM = path.resolve(commDir);
  const PIPE = path.join(COMM, '.pipe');
  return {
    COMM,
    PIPE,
    MSG: path.join(COMM, 'messages'),
    INBOX: path.join(COMM, 'inbox'),
    OUTBOX: path.join(COMM, 'outbox'),
    ENDPOINT: path.join(PIPE, 'endpoint.json'),
    STATE: path.join(PIPE, 'state.json'),
    TOKEN: path.join(PIPE, 'token'),
    PID: path.join(PIPE, 'server.pid'),
    LOG: path.join(PIPE, 'server.log'),
  };
}

export function ensureDirs(commDir) {
  const P = commPaths(commDir);
  for (const d of [P.COMM, P.PIPE, P.MSG, P.INBOX, P.OUTBOX]) fs.mkdirSync(d, { recursive: true });
}

const pad = (n, w = 2) => String(Math.abs(n)).padStart(w, '0');

export function isoLocal(d = new Date()) {
  const off = -d.getTimezoneOffset();
  const sign = off >= 0 ? '+' : '-';
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
    'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()) + '.' + pad(d.getMilliseconds(), 3) +
    sign + pad(Math.trunc(off / 60)) + ':' + pad(off % 60);
}

export function dayOf(d = new Date()) {
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

export function messageId(d = new Date()) {
  const compact = dayOf(d).replace(/-/g, '') + 'T' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
  return compact + '-' + Math.random().toString(36).slice(2, 6);
}

export function normalizeTarget(to) {
  if (Array.isArray(to)) return to.map((s) => String(s).trim()).filter(Boolean);
  return String(to === undefined || to === null ? '' : to).trim();
}

export function newMessage(input, ctx = {}) {
  const now = ctx.now instanceof Date ? ctx.now : new Date();
  const msg = {
    id: String(input.id || messageId(now)),
    ts: String(input.ts || isoLocal(now)),
    from: String(input.from || ctx.from || '').trim(),
    to: normalizeTarget(input.to === undefined ? ctx.to : input.to),
    kind: String(input.kind || 'note').trim(),
    thread: String(input.thread || ctx.thread || 'general').trim() || 'general',
    subject: String(input.subject || '').trim(),
    body: String(input.body || ''),
    refs: Array.isArray(input.refs) ? input.refs.map(String) : (input.refs ? [String(input.refs)] : []),
  };
  if (input.replyTo) msg.replyTo = String(input.replyTo);
  if (input.requiresAck !== undefined) msg.requiresAck = Boolean(input.requiresAck);
  if (!msg.from) throw new Error('message.from 缺失');
  if (!msg.to || (Array.isArray(msg.to) && msg.to.length === 0)) throw new Error('message.to 缺失');
  if (!KINDS.includes(msg.kind)) throw new Error('message.kind 非法：' + msg.kind + '；可用 ' + KINDS.join(' / '));
  if (msg.subject.length > 300) throw new Error('message.subject 超过 300 字符');
  if (msg.body.length > 200000) throw new Error('message.body 超过 200000 字符');
  return msg;
}

export function matchesTarget(msg, peer) {
  if (!peer) return false;
  const to = msg.to;
  if (to === '*' || to === 'all') return true;
  if (Array.isArray(to)) return to.includes(peer) || to.includes('*');
  return to === peer;
}

function safeDate(ts) {
  const d = new Date(ts);
  return Number.isNaN(d.getTime()) ? new Date() : d;
}

export function readLog(commDir) {
  const P = commPaths(commDir);
  const out = [];
  const seen = new Set();
  if (!fs.existsSync(P.MSG)) return out;
  const files = fs.readdirSync(P.MSG).filter((f) => f.endsWith('.jsonl')).sort();
  for (const f of files) {
    const text = fs.readFileSync(path.join(P.MSG, f), 'utf8');
    for (const line of text.split('\n')) {
      const t = line.trim();
      if (!t) continue;
      let m = null;
      try { m = JSON.parse(t); } catch { m = null; }
      if (!m || !m.id || seen.has(m.id)) continue;
      seen.add(m.id);
      out.push(m);
    }
  }
  out.sort((a, b) => {
    if (a.ts !== b.ts) return a.ts < b.ts ? -1 : 1;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  });
  return out;
}

export function appendLog(commDir, msg) {
  const P = commPaths(commDir);
  fs.mkdirSync(P.MSG, { recursive: true });
  const file = path.join(P.MSG, dayOf(safeDate(msg.ts)) + '.jsonl');
  fs.appendFileSync(file, JSON.stringify(msg) + '\n', 'utf8');
  return file;
}

export function formatMessage(m, opts = {}) {
  const seq = m.seq === undefined ? '?' : String(m.seq);
  const to = Array.isArray(m.to) ? m.to.join(',') : m.to;
  const time = String(m.ts).replace('T', ' ').slice(0, 19);
  const head = '#' + seq + '  ' + time + '  ' + m.from + ' -> ' + to + '  [' + m.kind + ']  ' + m.thread + '  ' + (m.subject || '(无标题)');
  if (opts.headOnly) return head;
  const lines = [head];
  if (m.replyTo) lines.push('  reply-to: ' + m.replyTo);
  if (m.refs && m.refs.length) lines.push('  refs: ' + m.refs.join(' | '));
  const body = String(m.body || '').replace(/\r\n/g, '\n').split('\n');
  for (const line of body) lines.push('  ' + line);
  return lines.join('\n');
}
