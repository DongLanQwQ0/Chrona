# 拾时原子岛通知：首次配置

**目前每台支持的 vivo / iQOO 手机首次启用拾时原子岛通知，都需要用电脑通过 ADB 配置一次。只安装拾时 APK 不会自动完成这一步；配置成功后，日常使用不需要连接电脑或保持 USB 调试。**

此工具只配置拾时的 `CHRONA_AGENDA` 通知场景。它不启用原子岛的“拖放直达”应用列表。

## Windows 一键配置

1. 手机先安装 GitHub 正式版拾时；等待正在进行的解析／同步结束，退出拾时。
2. 电脑安装 [PowerShell 7](https://aka.ms/powershell-release)，下载并完整解压 Release 中的 `Chrona-vivo-setup-0.15.42.zip`。配置包已包含 ADB，无需 Android Studio。
3. 手机开启“开发者选项 → USB 调试”，用可传输数据的数据线连接电脑。解锁手机，在 USB 调试提示中选择“允许”。只连接这一台手机。
4. 双击 `Enable-VivoAtomic.cmd`；若手机询问是否安装辅助程序，请允许。看到“配置已读回确认”后即可断开电脑。
5. 回到拾时的“通知与提醒”开启原子岛提醒，并允许系统通知。“胶囊计时”仍需由用户在系统无障碍设置中单独开启。

脚本临时安装专用辅助 APK，普通应用身份读回配置后移除本次新装的辅助程序；已存在的辅助程序保留。不重装主应用、不清数据、不创建测试日程、不修改其他应用的通知场景。辅助程序安装或系统接口被拒绝时停止，不会自动重试安装。

更换手机或恢复出厂设置后需重新配置。一般应用覆盖升级无需重复；系统升级后若失效，可查询状态并重新运行。

## 查询或关闭

在解压后的文件夹中打开 PowerShell 7：

```powershell
# 只查询；同样会临时安装辅助程序，但不写通知场景
.\Configure-VivoAtomicScene.ps1 -Mode Status

# 关闭拾时专属场景，保留日程数据
.\Configure-VivoAtomicScene.ps1 -Mode Disable

# 多台设备时明确指定，脚本不会自行选错手机
.\Configure-VivoAtomicScene.ps1 -Device '手机序列号' -Mode Enable
```

已有 ADB 可用 `-Adb '完整的adb.exe路径'` 指定。无线 ADB 请先自行配对和连接，再用 `-Device 'IP:端口'`；脚本不自动开启无线调试或修改网络配置。

## 无法完成时

- **未找到手机／未授权／离线**：检查数据线与 USB 调试，解锁手机并允许授权；Windows 未识别手机时先安装厂商 USB 驱动。
- **安装被拒绝**：在手机安装提示中允许辅助程序，再手动重试；签名不一致时请使用本仓库正式版，不要为运行工具卸载已有拾时。
- **未找到 ADB**：确认完整解压；也可从 [Google 官方](https://developer.android.com/tools/releases/platform-tools)下载 Windows 平台工具，把 `platform-tools` 文件夹放在脚本旁。
- **系统未确认／接口不存在或权限不足**：该固件可能不支持这条配置路径。保留普通通知提醒，并附机型、系统版本与错误内容反馈；工具不会尝试 Root、系统签名伪装或更改系统安全设置。
- **PowerShell 阻止脚本运行**：右键下载的 ZIP → 属性 → 解除锁定，再解压；受组织策略管理的电脑请联系管理员。

已在一台 vivo 设备验证的结果不代表全部型号与固件兼容。配置读回成功也不等同于实际通知显示成功：还需原子岛能力、通知权限、提醒开关和正常后台运行。此配置属于本机隐藏接口配置，不能替代 vivo 官方准入。

## 开发者构建

辅助 APK 只编译 `app/src/vivoSetup/java/`，不包含通用设备测试套件，也不改变主应用包名、签名或版本：

```powershell
.\gradlew.bat -I checks/vivo-setup.init.gradle :app:assembleReleaseAndroidTest --console=plain
.\tools\vivo\Build-VivoSetupBundle.ps1 -PlatformTools '你的SDK\platform-tools'
```

配置包沿用仓库已有签名，辅助 APK 仅能针对同签名的正式拾时执行。第三方重新签名版本需自行构建配套辅助程序。
