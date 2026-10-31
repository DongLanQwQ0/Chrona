# 当前交付：0.15.80 已发布；Dock 实验已上真机预览，待验收

## 已发布

- `v0.15.80`：https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.80 （latest），资产 `Chrona-0.15.80.apk` 3,299,025 字节，242／0.15.80，原签名，非 debuggable。
- 真机 V2527A 已覆盖升级；全量检查 120/121，唯一失败为 `data_integrity_check`（数据层夹具，与功能无关）。

## 实验分支 `experiment/glass-lens`

11 个提交，参数与决策见 `docs/liquid-glass-dock.md` 与 `docs/ui-interaction-baseline.md`：

玻璃镜片（渐变＋顶部高光＋边缘亮线）、弹簧 response 0.38s／damping 0.93、按压局部响应（1.06 倍，平台动画器）、滚动最小化（图标式，「＋」同步淡出且不可点）、引导页卡片内距与复制示例按钮、横向越界放开（首尾外 0.6 格）。

- 回归：`full` 120/121，与发布线一致。
- 真机预览已装：`com.donglan.chrona.preview`，0.15.80-preview，14:24:40。预览包数据独立且为空，滚动最小化需先记录几条内容。
- 正式版未动：`com.donglan.chrona` 0.15.80／242。

## 唯一下一步

等用户对预览的试用结论。满意则该分支并入 `main`，版本递增到 0.15.81，走 `docs/github-releases.md` 的发布流程（构建 debug＋release＋lint、核对签名与版本、打 tag、发布并校验 `/releases/latest`）。

## 边界

实验未合并、未改正式版本号；预览包只用于验收，不与正式版共享数据。安装时 vivo 会要求勾选风险确认再点继续安装。
