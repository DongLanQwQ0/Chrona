#!/usr/bin/env node
// 通信管道客户端：两边 agent 都只用这一个入口。
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { commPaths, parseArgs, ensureDirs, isoLocal, newMessage, readLog, appendLog, matchesTarget, formatMessage } from './shared.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROJECT = path.resolve(HERE, '..', '..');
const args = parseArgs(process.argv.slice(2));
const COMM = path.resolve(String(args.comm || process.env.PIPE_COMM || path.join(PROJECT, 'docs', 'Communication')));
const P = commPaths(COMM);
const ME = String(args.as || args.from || process.env.PIPE_PEER || 'core');
const cmd = args._[0] || 'help';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function readEndpoint() {
  try { return JSON.parse(fs.readFileSync(P.ENDPOINT, 'utf8')); } catch { return null; }
}

async function healthy(ep) {
  if (!ep || !ep.baseUrl) return false;
  try {
    const r = await fetch(ep.baseUrl + '/health', { signal: AbortSignal.timeout(1500) });
    if (!r.ok) return false;
    const d = await r.json();
    return Boolean(d && d.ok);
  } catch { return false; }
}

async function online() {
  const ep = readEndpoint();
  if (await healthy(ep)) return ep;
  return null;
}

async function api(ep, pathname, init = {}) {
  const headers = Object.assign({ 'content-type': 'application/json' }, init.headers || {});
  if (ep.token) headers['x-pipe-token'] = ep.token;
  const timeout = init.timeout || 20000;
  const r = await fetch(ep.baseUrl + pathname, Object.assign({}, init, { headers, signal: AbortSignal.timeout(timeout) }));
  const text = await r.text();
  let data = null;
  try { data = JSON.parse(text); } catch { data = { raw: text }; }
  if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + String((data && (data.error || data.raw)) || ''));
  return data;
}

function readBody() {
  if (typeof args['body-file'] === 'string') {
    const p = path.resolve(String(args['body-file']));
    return fs.readFileSync(p, 'utf8');
  }
  if (args.stdin) return fs.readFileSync(0, 'utf8');
  return String(args.body === undefined ? '' : args.body);
}

function printMessages(messages, opts = {}) {
  if (args.json) {
    process.stdout.write(JSON.stringify(messages, null, 2) + '\n');
    return;
  }
  if (!messages.length) {
    if (!opts.quiet) process.stdout.write('(没有新消息)\n');
    return;
  }
  for (const m of messages) process.stdout.write(formatMessage(m) + '\n\n');
}

async function ackMessages(ep, peer, messages) {
  if (!ep || !messages.length) return;
  await api(ep, '/ack', { method: 'POST', body: JSON.stringify({ peer, ids: messages.map((m) => m.id) }) });
}

async function cmdUp() {
  const current = readEndpoint();
  if (await healthy(current)) {
    process.stdout.write('管道已在运行：' + current.baseUrl + '\n');
    return 0;
  }
  ensureDirs(COMM);
  const host = String(args.host || '127.0.0.1');
  const port = String(args.port || 7801);
  const child = spawn(process.execPath, [path.join(HERE, 'broker.mjs'), '--host', host, '--port', port, '--comm', COMM], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
    cwd: PROJECT,
  });
  child.unref();
  for (let i = 0; i < 60; i += 1) {
    await sleep(200);
    const ep = readEndpoint();
    if (await healthy(ep)) {
      process.stdout.write('管道已启动：' + ep.baseUrl + '\n数据目录：' + COMM + '\n浏览器看板：' + ep.baseUrl + '/\n');
      return 0;
    }
  }
  process.stderr.write('启动超时，检查日志：' + P.LOG + '\n');
  return 1;
}

async function cmdDown() {
  const ep = readEndpoint();
  if (ep && await healthy(ep)) {
    try { await api(ep, '/shutdown', { method: 'POST' }); } catch { /* 进程可能已退出 */ }
    await sleep(400);
    process.stdout.write('管道已停止\n');
    return 0;
  }
  let pid = 0;
  try { pid = Number(fs.readFileSync(P.PID, 'utf8').trim()); } catch { pid = 0; }
  if (pid) {
    try { process.kill(pid); process.stdout.write('已结束进程 ' + pid + '\n'); } catch (err) { process.stderr.write('结束进程失败：' + err.message + '\n'); return 1; }
    return 0;
  }
  process.stdout.write('管道未运行\n');
  return 0;
}

async function cmdStatus() {
  const ep = readEndpoint();
  if (!ep) { process.stdout.write('未初始化：' + COMM + '\n'); return 1; }
  const ok = await healthy(ep);
  const line = ok ? '在线' : '离线（进程未响应）';
  process.stdout.write('状态：' + line + '\n地址：' + ep.baseUrl + '\n数据目录：' + ep.comm + '\n启动时间：' + ep.startedAt + '\n');
  if (ok) {
    const h = await api(ep, '/health');
    process.stdout.write('消息数：' + h.count + '　当前 seq：' + h.seq + '\n在线 peer：' + Object.keys(h.peers || {}).join(', ') + '\n');
  }
  return ok ? 0 : 1;
}

async function cmdSend() {
  const body = readBody();
  const msg = newMessage({
    from: String(args.from || ME),
    to: args.to,
    kind: args.kind,
    thread: args.thread,
    subject: args.subject,
    body,
    refs: args.ref,
    replyTo: args['reply-to'],
    requiresAck: args['requires-ack'],
  });
  const ep = await online();
  if (ep) {
    const d = await api(ep, '/send', { method: 'POST', body: JSON.stringify(msg) });
    process.stdout.write('已送达 #' + d.seq + '　' + msg.id + '\n');
    return 0;
  }
  appendLog(COMM, msg);
  process.stdout.write('管道离线，已写入共享日志：' + msg.id + '\nbroker 启动后会自动索引；对端可用 inbox 读取\n');
  return 0;
}

async function cmdInbox(all) {
  const ep = await online();
  const to = String(args.to || ME);
  const thread = String(args.thread || '');
  const since = args.since === undefined ? 0 : Number(args.since);
  let messages = [];
  if (ep) {
    const q = new URLSearchParams({ to, since: String(since), unread: all ? '0' : '1', limit: String(args.limit || 200) });
    if (thread) q.set('thread', thread);
    const d = await api(ep, '/messages?' + q.toString());
    messages = d.messages || [];
    if (args.ack) await ackMessages(ep, to, messages);
  } else {
    messages = readLog(COMM).filter((m) => matchesTarget(m, to) && m.from !== to && (!thread || m.thread === thread));
  }
  printMessages(messages);
  return 0;
}

async function cmdWait() {
  const ep = await online();
  const to = String(args.to || ME);
  const since = args.since === undefined ? 0 : Number(args.since);
  const timeout = Math.max(1, Number(args.timeout || 120)) * 1000;
  if (ep) {
    const q = new URLSearchParams({ to, since: String(since), timeout: String(timeout) });
    if (args.thread) q.set('thread', String(args.thread));
    const d = await api(ep, '/wait?' + q.toString(), { timeout: timeout + 20000 });
    printMessages(d.messages || []);
    if (args.ack && (d.messages || []).length) await ackMessages(ep, to, d.messages);
    return (d.messages || []).length ? 0 : 2;
  }
  const started = Date.now();
  const cutoff = new Date().toISOString();
  while (Date.now() - started < timeout) {
    const found = readLog(COMM).filter((m) => matchesTarget(m, to) && m.from !== to && String(m.ts) > cutoff);
    if (found.length) { printMessages(found); return 0; }
    await sleep(1500);
  }
  process.stdout.write('(等待超时，无新消息)\n');
  return 2;
}

async function cmdTail() {
  const ep = await online();
  const to = String(args.to || ME);
  let since = args.since === undefined ? 0 : Number(args.since);
  process.stdout.write('跟随 #' + since + ' 之后发给 ' + to + ' 的消息，Ctrl+C 结束\n');
  for (;;) {
    if (ep) {
      const q = new URLSearchParams({ to, since: String(since), timeout: '30000' });
      if (args.thread) q.set('thread', String(args.thread));
      const d = await api(ep, '/wait?' + q.toString(), { timeout: 50000 });
      const list = d.messages || [];
      if (list.length) {
        printMessages(list);
        since = Math.max(since, list[list.length - 1].seq);
        if (args.ack) await ackMessages(ep, to, list);
      }
    } else {
      await sleep(2000);
    }
  }
}

async function cmdBoard() {
  const ep = await online();
  const thread = String(args.thread || '');
  if (ep) {
    const q = thread ? '?thread=' + encodeURIComponent(thread) : '';
    const r = await fetch(ep.baseUrl + '/board' + q, { signal: AbortSignal.timeout(10000) });
    process.stdout.write(await r.text());
    return 0;
  }
  const list = readLog(COMM).filter((m) => !thread || m.thread === thread);
  process.stdout.write('# 通信记录（离线渲染）\n\n');
  for (const m of list) process.stdout.write(formatMessage(m) + '\n\n');
  return 0;
}

async function cmdPeers() {
  const ep = await online();
  if (ep) {
    const d = await api(ep, '/peers');
    process.stdout.write(JSON.stringify(d, null, 2) + '\n');
    return 0;
  }
  const file = path.join(COMM, 'peers.json');
  if (fs.existsSync(file)) process.stdout.write(fs.readFileSync(file, 'utf8') + '\n');
  else process.stdout.write('(没有 peers.json)\n');
  return 0;
}

function lockFileFor(target) {
  const rel = path.relative(PROJECT, path.resolve(target)).split(path.sep).join('/');
  const safe = rel.replace(/[\\/:*?"<>|]/g, '_').replace(/^_+/, '');
  return { rel, file: path.join(COMM, 'locks', safe + '.json') };
}

function lockConflicts(target, owner) {
  const dir = path.join(COMM, 'locks');
  if (!fs.existsSync(dir)) return [];
  const now = Date.now();
  const rel = path.relative(PROJECT, path.resolve(target)).split(path.sep).join('/');
  const out = [];
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith('.json')) continue;
    let rec = null;
    try { rec = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); } catch { continue; }
    if (!rec || rec.owner === owner) continue;
    if (rec.expiresAt && Date.parse(rec.expiresAt) < now) continue;
    const other = String(rec.path || '');
    const overlap = other === rel || rel.startsWith(other.replace(/\/$/, '') + '/') || other.startsWith(rel.replace(/\/$/, '') + '/');
    if (overlap) out.push(rec);
  }
  return out;
}

async function cmdLock() {
  const targets = [].concat(args.path || []);
  const owner = String(args.owner || ME);
  const ttl = Number(args.ttl || 3600);
  const note = String(args.note || '');
  if (!targets.length) { process.stderr.write('需要 --path 指定路径\n'); return 64; }
  const conflicts = [];
  for (const t of targets) conflicts.push(...lockConflicts(t, owner));
  if (conflicts.length && !args.force) {
    process.stderr.write('锁冲突：\n' + conflicts.map((c) => '  ' + c.path + ' 归 ' + c.owner + '（到期 ' + c.expiresAt + '）').join('\n') + '\n');
    return 3;
  }
  const acquired = [];
  for (const t of targets) {
    const { rel, file } = lockFileFor(t);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const rec = { path: rel, owner, acquiredAt: isoLocal(), expiresAt: isoLocal(new Date(Date.now() + ttl * 1000)), note };
    fs.writeFileSync(file, JSON.stringify(rec, null, 2), 'utf8');
    acquired.push(rec);
    process.stdout.write('已锁 ' + rel + ' → ' + owner + '（' + ttl + ' 秒）\n');
  }
  return 0;
}

async function cmdUnlock() {
  const targets = [].concat(args.path || []);
  const owner = String(args.owner || ME);
  if (!targets.length) { process.stderr.write('需要 --path 指定路径\n'); return 64; }
  for (const t of targets) {
    const { file, rel } = lockFileFor(t);
    if (!fs.existsSync(file)) { process.stdout.write('未加锁：' + rel + '\n'); continue; }
    let rec = null;
    try { rec = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { rec = null; }
    if (rec && rec.owner !== owner && !args.force) { process.stderr.write('锁属于 ' + rec.owner + '，拒绝释放：' + rel + '\n'); continue; }
    fs.rmSync(file, { force: true });
    process.stdout.write('已释放 ' + rel + '\n');
  }
  return 0;
}

async function cmdLocks() {
  const dir = path.join(COMM, 'locks');
  if (!fs.existsSync(dir)) { process.stdout.write('(没有任何锁)\n'); return 0; }
  const now = Date.now();
  const rows = fs.readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => {
    try { return JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); } catch { return null; }
  }).filter(Boolean);
  if (!rows.length) { process.stdout.write('(没有任何锁)\n'); return 0; }
  for (const r of rows) {
    const expired = r.expiresAt && Date.parse(r.expiresAt) < now;
    process.stdout.write((expired ? '[过期] ' : '[占用] ') + r.path + '  ← ' + r.owner + '（' + r.acquiredAt + ' → ' + r.expiresAt + '）' + (r.note ? ' ' + r.note : '') + '\n');
  }
  return 0;
}

async function cmdImport() {
  const ep = await online();
  if (!ep) { process.stderr.write('需要 broker 在线才能导入\n'); return 1; }
  const d = await api(ep, '/import', { method: 'POST' });
  process.stdout.write('已导入 ' + d.imported + ' 条，当前 seq ' + d.seq + '\n');
  return 0;
}

function help() {
  process.stdout.write([
    '用法：node tools/pipe/pipe.mjs <命令> [参数]',
    '',
    '  up        [--host H] [--port P]     启动 broker（后台）',
    '  down                                停止 broker',
    '  status                              查看状态',
    '  send      --to PEER --subject S [--kind K] [--thread T] [--body B|--body-file F|--stdin] [--ref R] [--reply-to ID]',
    '  inbox     [--as PEER] [--thread T] [--since N] [--ack] [--all] [--json]',
    '  wait      [--as PEER] [--since N] [--thread T] [--timeout 秒] [--ack]',
    '  tail      [--as PEER] [--since N] [--thread T] [--ack]',
    '  board     [--thread T]               渲染线程记录',
    '  peers                               查看参与方',
    '  import                              立即导入 inbox 文件投递',
    '  lock      --path P [--path P2] [--ttl 秒] [--note 说明] [--owner PEER]   申请路径租约',
    '  unlock    --path P [--owner PEER]    释放路径租约',
    '  locks                                列出所有租约',
    '',
    '公共参数：--comm DIR（数据目录，默认 docs/Communication）、--as PEER（默认 $env:PIPE_PEER 或 core）',
    '消息 kind：request / answer / question / note / decision / status / blocker / patch / review',
  ].join('\n') + '\n');
}

async function main() {
  switch (cmd) {
    case 'up': return cmdUp();
    case 'down': return cmdDown();
    case 'status': return cmdStatus();
    case 'send': return cmdSend();
    case 'inbox': return cmdInbox(false);
    case 'history': return cmdInbox(true);
    case 'wait': return cmdWait();
    case 'tail': return cmdTail();
    case 'board': return cmdBoard();
    case 'peers': return cmdPeers();
    case 'import': return cmdImport();
    case 'lock': return cmdLock();
    case 'unlock': return cmdUnlock();
    case 'locks': return cmdLocks();
    case 'help':
    case '--help':
    case '-h': help(); return 0;
    default:
      process.stderr.write('未知命令：' + cmd + '\n');
      help();
      return 64;
  }
}

main().then((code) => { process.exitCode = code; }).catch((err) => {
  process.stderr.write('失败：' + String((err && err.message) || err) + '\n');
  process.exitCode = 1;
});
