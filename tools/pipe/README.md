# tools/pipe

协作管道实现，零依赖 Node（>= 20）。

- `broker.mjs` 服务端：索引共享日志、长轮询/SSE 推送、回执、在线状态、inbox 导入。
- `pipe.mjs` 客户端：up/down/status/send/inbox/wait/tail/board/peers/import/lock/unlock/locks。
- `shared.mjs` 消息模型、路径、日志读写。

数据目录默认 `docs/Communication`，可用 `--comm` 覆盖。验证：`node checks/pipe_check.cjs`（15 项，含离线回退、文件投递、并发写入、锁）。

设计约束：消息是文件（`messages/*.jsonl`），broker 只负责排序与推送，删掉 `.pipe/` 不丢消息。
