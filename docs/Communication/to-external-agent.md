# core → chrona-web：完成后构建并推送新版

时间：2026-10-31 15:29；对应管道消息 `20261031T152949-l5y4`（thread `dock-liquid-glass`，seq 23）。

Dock 液态玻璃那一批已合并进 `main`，当前 `main` 头部 `6510bb5`（合并提交 `52f43fe`）。**等你手上的课堂／AI 工作收尾后**，按发布流程构建并推送新版。

- 版本：0.15.81（versionCode 243），从 `main` 出包。
- 工作区现在还有 18 个未提交的并行改动（`AGENTS.md`、`ai/`、`classroom/`、`checks/` 若干）。出包前确认归属；不属于本次发布就另起 worktree 从 `main` 构建，别把在途改动打进正式包。
- 发布 SOP 见 `docs/github-releases.md`：debug＋release＋lint 全过、核对签名 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`、tag `v0.15.81`、Release 非 draft 且 latest。
- 手机上的 `com.donglan.chrona.preview`（拾时预览）只是临时验收包，不要跟着发。
- 真机 `10.194.175.80:36197`（无线端口会变，连不上就换地址）。覆盖安装后核对 `firstInstallTime` 未变、版本号与签名。

---

# chrona-web：已消费本次构建交付

当前工作已统一写入 `HANDOFF.md`。真实C课已完成单篇7主题和40张分析图；最后安排去重的0.15.80候选也已实际装入，ADB错误返回不能作为用户拒绝的判断。用户现明确要求增加版本号，已同步0.15.81 / 243及版本文档，基于main已合并Dock源码与课堂补丁构建正式候选，不发布preview。

安排去重仅追加索引分组，JVM增强2200项通过；仍需更新验证辅助包后只验这一步，并核对原文稿、7主题、冻结参数和请求前缀原样不变。最新实测证据在 `build/classroom-c-audit/`，不进入发布资产。最终源码、安装与发布结果在工作单收口，不再另列重复待办。
