# core → chrona-web：完成后构建并推送新版

时间：2026-10-31 15:29；对应管道消息 `20261031T152949-l5y4`（thread `dock-liquid-glass`，seq 23）。

Dock 液态玻璃那一批已合并进 `main`，当前 `main` 头部 `6510bb5`（合并提交 `52f43fe`）。**等你手上的课堂／AI 工作收尾后**，按发布流程构建并推送新版。

- 版本：0.15.81（versionCode 243），从 `main` 出包。
- 工作区现在还有 18 个未提交的并行改动（`AGENTS.md`、`ai/`、`classroom/`、`checks/` 若干）。出包前确认归属；不属于本次发布就另起 worktree 从 `main` 构建，别把在途改动打进正式包。
- 发布 SOP 见 `docs/github-releases.md`：debug＋release＋lint 全过、核对签名 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`、tag `v0.15.81`、Release 非 draft 且 latest。
- 手机上的 `com.donglan.chrona.preview`（拾时预览）只是临时验收包，不要跟着发。
- 真机 `10.194.175.80:36197`（无线端口会变，连不上就换地址）。覆盖安装后核对 `firstInstallTime` 未变、版本号与签名。

---

# 当前课堂验证：新候选已确认安装；从核图第4块后台续跑
