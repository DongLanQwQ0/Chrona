# 验证记录

当前发布版本为 **0.15.21 / 183**。下面记录该版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.21 — 2026-10-17

- 真机修复前只读预检：本地 46 个寄存器、0 个合并标记有效；列表 ETag 校验失败，放开列表后 GET 仍缺少可用强 ETag。未读取或输出账号、应用密码、云端正文或实体 ID。
- release / androidTest 构建、lintRelease 通过；USB 安装 183 后真实调用完整 AndroidSync，结果为“上次同步：2026/10/17 18:23 · 成功”。同步前在应用私有目录保留原库及 WAL、同步状态和课表恢复副本，未清空或删除原数据。
- 传输检查通过，新增缺失、未加引号与弱 ETag；有强标签仍条件写入，HTTP 401/429 原因明确且不包含服务器正文。真实 HTTP 交换 16 项、合并 99 项、类型与真实 SQLite 66 项通过。检查使用本地虚构账号和记录，无外网服务调用。
- `sync_preflight` 只检查本地与远端；`sync_roundtrip` 显式执行真实同步并先准备恢复副本。设备报告 `build/device-0.15.21/roundtrip.txt`；离线日志 `build/webdav-core-check.log`、`build/webdav-exchange-check.log`；构建／lint 日志 `build/0.15.21-*.log`。
- 新发现的独立限制：标准 createSnapshot 在该历史升级库触发 CHECK 失败，未对真实数据作修正或放宽约束；本次采用事务内原库／WAL 副本保护。该备份缺陷单独留在交接页，不将本次同步成功当作完整备份恢复通过。
