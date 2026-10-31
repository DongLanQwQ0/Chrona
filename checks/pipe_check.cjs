#!/usr/bin/env node
// 通信管道端到端检查：真实起 broker、真实收发、离线回退、文件投递、并发写入。
const { spawn, execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const WORK = path.join(ROOT, 'build', 'pipe-check');
const COMM = path.join(WORK, 'comm');
const PORT = 7811;
const BASE = 'http://127.0.0.1:' + PORT;
const lines = [];
let failures = 0;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function record(name, pass, detail) {
  const line = (pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '  ' + detail : '');
  lines.push(line);
  console.log(line);
  if (!pass) failures += 1;
}

async function waitHealth(ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    try {
      const r = await fetch(BASE + '/health', { signal: AbortSignal.timeout(800) });
      const d = await r.json();
      if (d.ok) return d;
    } catch { /* 还没起来 */ }
    await sleep(150);
  }
  return null;
}

function startBroker() {
  return spawn(process.execPath, ['--no-warnings', path.join(ROOT, 'tools', 'pipe', 'broker.mjs'), '--comm', COMM, '--port', String(PORT)], {
    stdio: 'ignore',
    windowsHide: true,
  });
}

async function post(url, body) {
  const r = await fetch(BASE + url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  return r.json();
}

async function readSSE(url, ms) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), ms);
  let text = '';
  try {
    const res = await fetch(url, { signal: ac.signal });
    const reader = res.body.getReader();
    const dec = new TextDecoder();
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      text += dec.decode(value, { stream: true });
      if (text.includes('event: message')) break;
    }
  } catch { /* 超时或中止 */ }
  clearTimeout(timer);
  try { ac.abort(); } catch { /* 忽略 */ }
  return text;
}

async function main() {
  fs.rmSync(WORK, { recursive: true, force: true });
  fs.mkdirSync(COMM, { recursive: true });

  let broker = startBroker();
  const health = await waitHealth(8000);
  record('broker 启动并可访问 /health', Boolean(health), health ? 'seq=' + health.seq : '超时');
  if (!health) throw new Error('broker 未就绪');

  const first = await post('/send', { from: 'core', to: 'ext', kind: 'request', thread: 't1', subject: '握手', body: '请确认收到' });
  record('在线发送返回 seq', first.ok && first.seq === 1, 'seq=' + first.seq);

  let inbox = await (await fetch(BASE + '/messages?to=ext&unread=1&since=0')).json();
  record('对端未读收件箱可见 1 条', inbox.messages.length === 1, JSON.stringify(inbox.messages.map((m) => m.subject)));

  await post('/ack', { peer: 'ext', ids: inbox.messages.map((m) => m.id) });
  inbox = await (await fetch(BASE + '/messages?to=ext&unread=1&since=0')).json();
  record('确认回执后未读清空', inbox.messages.length === 0);

  const t0 = Date.now();
  const waited = (async () => {
    const r = await fetch(BASE + '/wait?to=ext&since=1&timeout=6000');
    return r.json();
  })();
  await sleep(500);
  await post('/send', { from: 'core', to: 'ext', kind: 'answer', thread: 't1', subject: '长轮询', body: '推送测试' });
  const waitedResult = await waited;
  const waitMs = Date.now() - t0;
  record('长轮询在推送时立即返回', waitedResult.messages.length === 1 && waitMs < 2000, waitMs + 'ms');

  const ssePromise = readSSE(BASE + '/stream?to=ext&since=2', 6000);
  await sleep(400);
  await post('/send', { from: 'core', to: 'ext', kind: 'note', thread: 't1', subject: 'SSE', body: '事件流' });
  const sseText = await ssePromise;
  record('SSE 事件流收到消息', sseText.includes('event: message') && sseText.includes('SSE'));

  const dupPayload = { from: 'core', to: 'ext', kind: 'note', thread: 't1', subject: '同名 id', body: '重复' };
  const d1 = await post('/send', Object.assign({ id: 'fixed-dup-1' }, dupPayload));
  const d2 = await post('/send', Object.assign({ id: 'fixed-dup-1' }, dupPayload));
  const allAfterDup = await (await fetch(BASE + '/messages?all=1&since=0')).json();
  const dupCount = allAfterDup.messages.filter((m) => m.id === 'fixed-dup-1').length;
  record('同一 id 重复发送只落一条', d1.seq === d2.seq && dupCount === 1, 'seq=' + d1.seq);

  broker.kill();
  await sleep(700);
  let offlineOk = false;
  let offlineOut = '';
  try {
    offlineOut = execFileSync(process.execPath, [path.join(ROOT, 'tools', 'pipe', 'pipe.mjs'), 'send', '--comm', COMM, '--from', 'ext', '--to', 'core', '--kind', 'answer', '--subject', '离线投递', '--body', 'broker 不在时直接写日志'], { encoding: 'utf8' });
    offlineOk = offlineOut.includes('离线');
  } catch (err) {
    offlineOut = String(err.message);
  }
  record('broker 离线时 CLI 写入共享日志', offlineOk, offlineOut.trim().split('\n')[0]);

  broker = startBroker();
  const health2 = await waitHealth(8000);
  record('broker 重启成功', Boolean(health2));
  const afterRestart = await (await fetch(BASE + '/messages?all=1&since=0')).json();
  record('重启后离线消息被索引', afterRestart.messages.some((m) => m.subject === '离线投递'));

  fs.mkdirSync(path.join(COMM, 'inbox', 'core'), { recursive: true });
  fs.writeFileSync(path.join(COMM, 'inbox', 'core', 'drop.json'), JSON.stringify({ from: 'ext', to: 'core', kind: 'note', thread: 't1', subject: '文件投递', body: '只读文件也能进管道' }), 'utf8');
  await sleep(2600);
  const afterDrop = await (await fetch(BASE + '/messages?all=1&since=0')).json();
  const dropped = afterDrop.messages.some((m) => m.subject === '文件投递');
  const dropGone = !fs.existsSync(path.join(COMM, 'inbox', 'core', 'drop.json'));
  record('inbox 文件投递被导入并清理源文件', dropped && dropGone);

  const before = (await (await fetch(BASE + '/health')).json()).count;
  const many = [];
  for (let i = 0; i < 6; i += 1) many.push(post('/send', { from: 'core', to: 'ext', kind: 'status', thread: 't2', subject: '并发' + i, body: 'x' }));
  const results = await Promise.all(many);
  const seqs = results.map((r) => r.seq);
  const after = (await (await fetch(BASE + '/health')).json()).count;
  const uniqueSeq = new Set(seqs).size === seqs.length;
  const monotonic = seqs.every((s, i) => i === 0 || s > seqs[i - 1]);
  record('并发写入无重复 seq 且顺序单调', uniqueSeq && monotonic && after - before === 6, JSON.stringify(seqs));

  const board = await (await fetch(BASE + '/board?thread=t1')).text();
  record('board 渲染线程记录', board.includes('握手') && board.includes('SSE'));

  const peer = await post('/peer', { id: 'ext', role: '外部实现方' });
  record('peer 在线状态登记', peer.ok && peer.peer.role === '外部实现方');

  await post('/shutdown', {});
  await sleep(600);
  let stopped = false;
  try {
    await fetch(BASE + '/health', { signal: AbortSignal.timeout(600) });
  } catch { stopped = true; }
  record('shutdown 停止服务', stopped);

  lines.push('');
  lines.push(failures === 0 ? '结论：全部通过' : '结论：' + failures + ' 项失败');
  fs.mkdirSync(WORK, { recursive: true });
  fs.writeFileSync(path.join(WORK, 'result.txt'), lines.join('\n') + '\n', 'utf8');
  console.log('\n' + (failures === 0 ? '全部通过' : failures + ' 项失败') + ' → ' + path.join(WORK, 'result.txt'));
  process.exitCode = failures === 0 ? 0 : 1;
}

main().catch((err) => {
  lines.push('ERROR  ' + String((err && err.stack) || err));
  fs.mkdirSync(WORK, { recursive: true });
  fs.writeFileSync(path.join(WORK, 'result.txt'), lines.join('\n') + '\n', 'utf8');
  console.error(err);
  try { process.exit(1); } catch { /* 忽略 */ }
});
