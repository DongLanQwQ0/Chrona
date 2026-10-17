# 当前状态与接续

## 从这里开始

先读[文档索引](docs/README.md)、[项目基本要求](docs/project-requirements.md)和[UI 设计规范](docs/ui-style-contract.md)，再按所改功能阅读对应文档。先检查工作区改动，保留其他人的文件。

## 当前版本

- **0.15.21 / versionCode 183**，数据库版本 **13**；版本以 `app/build.gradle`、`TaskStore.DATABASE_VERSION` 为准。
- 源码与安装包：[GitHub Releases](https://github.com/DongLanQwQ0/Chrona/releases)。
- 最近修复 WebDAV 列表及 GET 缺少有效强 ETag 时的同步失败；只更新本设备串行单写者 head，有强标签时继续条件写入。合并标记通过交换层校验，HTTP 错误与网络故障返回固定安全提示。
- 0.15.18 的选择栏手感已获用户确认，见[已确认交互基线](docs/ui-interaction-baseline.md)。后续修复和性能优化保持该效果。

## 最近证据与实际限制

0.15.21 release / androidTest 构建与 lintRelease 通过；USB 安装 183。真机通过应用已有 Keystore 配置检查 WebDAV：本地 46 个寄存器有效；服务器列表和 GET 的版本标签不符合原强 ETag 要求。修复后实际运行完整 AndroidSync，返回“上次同步：2026/10/17 18:23 · 成功”。本轮测试未显示、导出或修改凭据，未清空本地或云端，未替用户处理冲突／删除日历。

传输回归通过；新增真实 HTTP 交换 16 项通过，覆盖无强 ETag／强 ETag、重复同步、双设备、永久合并标记、拒绝未知记录及不覆盖其他设备 head；合并 99、类型与 SQLite 66 项通过。证据位于 `build/device-0.15.21/`，构建与检查日志在 `build/0.15.21-*.log`、`build/webdav-*-check.log`。

实际同步前已在应用私有 files/sync-recovery-<时间戳>/ 中保留数据库及 WAL、同步状态和课表文件。准备标准快照时发现历史升级库 `createSnapshot` 复制失败（uncertainty_level CHECK）；`INSERT ... SELECT *` 未显式列名，旧库与新建表列序可能不同。此独立备份问题尚待核对并修复，禁止通过修改真实行或关闭约束掩盖。原失败快照未用于恢复；本次使用写事务内的原库与 WAL 恢复副本完成同步前保护。

仍需区分以下限制：

- 全应用其余卡顿热点尚未归因，不能宣称稳定 120fps。
- 原子岛已有部分真机操作与秒级刷新证据；锁屏、胶囊、长时后台等形态必须按具体版本和场景判断，不能据一次通过泛称全部通过。
- 模型实际返回的思考文本、缓存命中和语义准确率受服务影响；离线协议检查不能替代真实调用。
- 备份合成数据检查不代表已对用户真实数据完成覆盖恢复。恢复、升级和破坏性验证须使用获准且可恢复的测试环境。

具体证据见[验证记录](docs/validation-history.md)，历史差异见[归档索引](docs/archive/README.md)。历史中的“尚未完成”或设备地址不作为当前任务清单。

## 文档维护

产品变化更新对应功能文档；通用要求进入基本要求或 UI 规范；已确认的具体交互进入交互基线。发布摘要写 `CHANGELOG.md`，实际验证写验证记录。本页只保留当前状态、未解决限制和接续入口，不再累积逐版日志。
