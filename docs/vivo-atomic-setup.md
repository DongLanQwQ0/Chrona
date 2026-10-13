# vivo 原子岛日程提醒

在已完成本机配置的 vivo 设备上，Chrona 会在日程开始前30分钟发送原子通知。提醒使用专属场景 `CHRONA_AGENDA`，包含标题、时间和地点，并保留查看详情、5分钟后提醒、隐藏本次的动作。是否读取系统日程由设置中的提醒选项独立控制。

2026-10-13 已在用户手机上完成专属场景注册，并取得原子岛实际显示截图。注册后的发送以普通应用身份进行，不需要持续连接 ADB。0.14.28 的最终提醒样式、动作交互和重启后的场景保留仍需后续真机复验。

## 个人开发设备的一次性配置

这是一项显式的本机开发配置，会持久增加仅包含 Chrona 的场景。生产 APK 不含场景注册代码，也不取得系统权限。配置工具借用已授权 ADB 的临时 `STATUS_BAR_SERVICE` 身份执行注册，立即撤掉身份，再以普通应用身份读回确认；不会修改已有的 `METTING` 或其它场景的应用名单。

先安装对应版本 Chrona，连接已授权 ADB 的手机，再解压发布附件 `Chrona-vivo-setup-0.14.28.zip`。使用 PowerShell 7：

```powershell
pwsh -File .\Configure-VivoAtomicScene.ps1 -Device '手机IP:连接端口' -Mode Enable -TestApk .\Chrona-device-checks.apk
```

ADB 未在 PATH 中时，使用 `-Adb '你的adb.exe绝对路径'`。工具只安装对应的测试 APK，运行专属场景配置并检查结果，不修改日程记录、不打开日历页面、不操作其它应用。

关闭该专属场景：

```powershell
pwsh -File .\Configure-VivoAtomicScene.ps1 -Device '手机IP:连接端口' -Mode Disable -TestApk .\Chrona-device-checks.apk
```

关闭会使自有场景和包开关变为 false，保留一条停用的自有场景配置。配置失败不能当成已启用，工具会报错。成功注册也不单独证明已显示原子岛，仍应观察设备实际通知。

此接口是 vivo 隐藏接口，具体固件可能不提供或拒绝调用。本机实验不等于官方准入，也不能推广为所有 vivo 设备都免审核。正式第三方接入要求以 [vivo 接入指导](https://dev.vivo.com.cn/documentCenter/doc/894) 为准。
