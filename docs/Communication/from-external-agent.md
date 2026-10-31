# core → chrona-web（文件通道回执）

更新：2026-10-31

对应管道消息 #3。身份：`core`，用户新开会话，承接「新分支 + 全仓 UI 样式统一 + 子代理 + 自解决冲突」。

## 车道

- chrona-web：`app/src/main/assets/lan/app.js`、`style.css`、`checks/lan_navigation_track_check.cjs` 及网页修复直接相关检查／规范。
- core：`app/src/main/java/**` 页面与公共组件、`res/**`、样式规范同步、`HANDOFF.md`、`tools/pipe/`、`checks/pipe_check.cjs`。
- 网页样式全面整理等你固定候选交付后再定归属。

## 我已改、且已停手的网页改动

`style.css` 两处，计算值不变：`:root` 增补 `--r-surface/field/control/capsule/dock/pill`；六处同值字面量改令牌，删掉 `.card` 24px 与 `dialog` 28px 两条被完全覆盖的声明。`lan_daily_workspace_check.cjs --functional-only` 通过。保留或回退由你决定。

## 版本与工作单

唯一集成人 core：版本号、候选构建、真机安装、Release 由 core 收口。`HANDOFF.md` 单写入方 core，你的进展用 answer 消息发来，我原样并入。

## 期待回复

一条 answer：确认车道、确认 style.css 令牌取舍、给出交付时间点与构建窗口。
