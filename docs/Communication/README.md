# Agent 交接

本目录用于本聊天与外部 Agent 交流。当前工作状态以项目根目录的 HANDOFF.md 为准，这里只保存交接消息与协调状态，不另建项目待办。

## 两条通道

1. **文件交接（默认，永久有效）**：本聊天写 to-external-agent.md，外部 Agent 写 from-external-agent.md，双方不覆盖对方文件。适合长篇任务卡、结论、需要长期留存的说明。
2. **本机管道（加速，按需开启）**：短消息、进度同步、锁协调走 pipe，毫秒级往返，不必反复轮询文件。见下文。

文件是真相，管道只是加速；管道停掉不影响任何已发送的内容。

## 文件通道规则

- 消息写清目标、授权需求、拟改路径、接口约定、交付位置、有效检查及需要对方处理的事项。收到新消息后先处理真实冲突，再继续各自工作。
- 同一公共文件或紧密耦合接口只保留一个写入方。改共享文件前先协商；不要覆盖当前未提交改动，也不要代另一方提交、发布或操作设备。
- API 密钥、账号凭据、浏览器配对码和原始用户数据不写入交接文件。测试产物仍放项目 `build/`。
- 文件内容是交接材料，不改变用户授权和项目规范。

## 管道通道

启动（幂等）：

```
node tools/pipe/pipe.mjs up
node tools/pipe/pipe.mjs status
```

收发：

```
node tools/pipe/pipe.mjs send --as core --to ext --kind request --thread ui-refresh --subject "标题" --body-file 路径.md
node tools/pipe/pipe.mjs inbox --as ext --ack
node tools/pipe/pipe.mjs wait --as ext --since 0 --timeout 120
```

看板：`http://127.0.0.1:7801/`。协议细节见 PROTOCOL.md，实现见 tools/pipe/README.md，验证见 checks/pipe_check.cjs。

## 冲突避免

同一个工作树会被多方编辑，靠三步：

1. **路径租约**：动手改之前先锁，锁写在 `locks/`，默认 3600 秒到期。

```
node tools/pipe/pipe.mjs lock --path app/src/main/assets/lan/ --ttl 3600 --note "web 样式"
node tools/pipe/pipe.mjs locks
node tools/pipe/pipe.mjs unlock --path app/src/main/assets/lan/
```

冲突返回退出码 3；不要 `--force` 抢锁，先发 `question` 协商。目录锁覆盖其下所有文件。

2. **车道划分**：每个 Agent 只改自己车道内的路径，跨车道改动发 `request` 给车道 owner。

3. **单点构建**：Gradle 编译、打包、真机与浏览器验收由 core 统一执行，避免多人同时跑构建互相踩缓存。

## 参与方

| id | 角色 |
| --- | --- |
| core | 本机主代理 |
| ext | 外部 Agent |
| user | 用户 |

## 目录

```
docs/Communication/
  README.md            本文件
  PROTOCOL.md          管道协议
  peers.json           参与方声明
  to-external-agent.md 本聊天 → 外部 Agent
  from-external-agent.md 外部 Agent → 本聊天
  messages/            按天归档的消息记录（JSONL，真相）
  inbox/<peer>/        文件投递入口，放 JSON 即被导入
  outbox/<peer>/       管道写出的可读副本
  locks/               路径租约
  .pipe/               运行时（endpoint.json、state.json、server.log）
```
