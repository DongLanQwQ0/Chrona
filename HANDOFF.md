# 当前交付：0.15.80 已发布；iOS 风格 Dock 实验待验收

## 已发布

- `v0.15.80`：https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.80 （latest），资产 `Chrona-0.15.80.apk` 3,299,025 字节，242／0.15.80，原签名，非 debuggable。
- 真机 V2527A 已覆盖升级（firstInstallTime 未变），全量检查 120/121（唯一失败 `data_integrity_check`，夹具问题）。

## 实验分支 `experiment/glass-lens`

七次提交，见 `docs/liquid-glass-dock.md`：玻璃镜片、弹簧 response/damping 模型、按压平台动画器、滚动最小化（图标式、「＋」同步）、引导页边距修正。

- 回归：full 120/121，与发布线一致。
- 候选：`app/build/outputs/apk/preview/app-preview.apk`（3,300,777 字节，14:00:05），包名 `com.donglan.chrona.preview`，应用名「拾时预览」。
- 真机上的 `拾时预览` 仍是 13:40 那版（无最小化、无引导页修正）；安装需在手机上勾选 vivo 风险确认再点继续安装。

## 唯一下一步

等用户确认手感后推一次预览包；若要并入发布，则合并分支、递增版本并走发布流程。

## 边界

实验未合并、未改版本号；真机只装了 preview 包，正式版未动。
