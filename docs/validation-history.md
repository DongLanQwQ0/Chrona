# 验证记录

当前发布版本为 **0.15.20 / 182**。下面记录该版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.20 — 2026-10-17

- `assembleRelease`、`lintRelease` 成功；构建日志 `build/0.15.20-build.log`。数据库及动效公共组件未改动。
- USB 覆盖安装 182 成功。六步真实页面逐页点击、读取节点和截图，实际检查内容、底部导航及卡片布局；旧用户不自动重播引导，设置入口仍可重看。
- 实点 AI 设置、通知与提醒、备份与恢复三个入口，核对目标 Activity，返回保留原步骤；最后使用稍后再看退出。未读取设置中的密钥，未复制示例、提交记录、调用 AI 或修改真实日程。
- 证据位于本机 `build/device-0.15.20/`：六页 PNG/XML、`guide-contact.png`、`navigation-results.json`。本次未覆盖旋转、最大字号及新装权限弹窗，不据此声称全场景通过。
