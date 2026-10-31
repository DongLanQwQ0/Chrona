# 管道协议

零依赖 Node 服务，默认监听 `127.0.0.1:7801`，数据目录本目录。

## 启动与发现

```
node tools/pipe/pipe.mjs up [--host 127.0.0.1] [--port 7801]
```

broker 启动后写 `.pipe/endpoint.json`，客户端只读这个文件，不猜端口：

```json
{ "baseUrl": "http://127.0.0.1:7801", "bind": "127.0.0.1", "port": 7801, "token": "", "pid": 12345, "comm": "...", "startedAt": "..." }
```

绑定非回环地址时自动生成 `.pipe/token`，请求需带 `x-pipe-token` 头或 `?token=`。

## 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /health | 进程、seq、消息数、在线 peer |
| POST | /send | 投递消息，返回 id 与 seq |
| GET | /messages | `to=&since=&thread=&all=1&unread=1&limit=` |
| GET | /wait | 长轮询，`timeout` 毫秒，上限 120000 |
| GET | /stream | SSE，`event: message` |
| POST | /ack | `{peer, ids:[]}` 标记已读 |
| GET | /peers | 声明表 + 在线状态 |
| POST | /peer | 登记 `{id, role}` |
| GET | /board | 线程 markdown |
| POST | /import | 立即扫描 inbox |
| POST | /shutdown | 保存状态后退出 |

`/messages` 默认排除自己发的消息；`unread=1` 只返回未 ack 的。

## 消息

```json
{
  "id": "20261031T104500-a1b2",
  "ts": "2026-10-31T10:45:00.000+08:00",
  "from": "core",
  "to": "ext",
  "kind": "request",
  "thread": "ui-refresh",
  "subject": "一句话标题",
  "body": "markdown 正文",
  "refs": ["app/src/main/java/com/donglan/chrona/UiStyle.java"],
  "replyTo": "20261031T104000-9x8y",
  "requiresAck": true
}
```

`to` 可以是单个 id、id 数组或 `*`（广播）。kind：request / answer / question / decision / note / status / blocker / patch / review。

## 语义

- **seq** 由 broker 单调分配并持久化在 `.pipe/state.json`；重启后同一 id 保持原 seq。
- **id** 缺省按时间戳生成；显式传入可去重，重复投递只落一条。
- **顺序** 取 seq，不取文件写入顺序。
- **失败** 参数非法 400 + `error`；未授权 401；未知路由 404。

## 租约

`locks/<rel-path>.json`：

```json
{ "path": "app/src/main/assets/lan/", "owner": "ext", "acquiredAt": "...", "expiresAt": "...", "note": "" }
```

- 冲突判定：路径相同、或一方是另一方的目录前缀、owner 不同、未过期。
- 冲突时 CLI 退出码 3；`--force` 只用于处理崩溃残留，使用后必须在消息里说明。
- 过期锁不阻塞，但会继续列在 `locks` 里，由 owner 清理。

## 客户端退出码

| 码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 1 | 一般失败（启动超时、网络错误） |
| 2 | `wait` 超时无消息 |
| 3 | 锁冲突 |
| 64 | 参数错误 |
