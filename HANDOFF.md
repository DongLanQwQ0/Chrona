# 当前交付：0.15.80 已发布；下一阶段 iOS 风格 Dock 实验

分支 `main` 已包含 0.15.80；实验在独立分支进行，`v0.15.80` 是回滚点。

## 已发布

- `v0.15.80`：https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.80 （latest）
- 候选 `build/candidates/0.15.80/Chrona-0.15.80.apk`，3,299,025 字节，242／0.15.80，原签名，非 debuggable。
- 真机已覆盖升级（firstInstallTime 未变），Release 资产回读校验一致。
- 全量检查 120/121；失败项 `data_integrity_check` 为夹具问题。

## 下一阶段：iOS 风格 Dock 实验（需要可回滚）

- 用户要求：参照 Apple 的 Dock 重新做一版更灵动、贴合原样的交互；可不使用液态玻璃。
- 回滚方式：实验在独立分支提交，不打 tag、不发布；回滚即回到 `v0.15.80`。保留两版候选 APK 供对比。
- 约束：现有 `DockNavigationLayout`／`DockSpring` 参数被交互基线与多处检查锁定；实验必须放在独立类或用开关切换，不改动既有基线参数，避免污染发布线。

## 唯一下一步

确定实验落点（独立 Dock 实现 + 开关），先做出可对比的候选，再由用户决定是否替换。

## 边界

未改版本号（实验分支），未发布实验版本；真机仅安装已验证的 0.15.80。
