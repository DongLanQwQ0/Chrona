# GitHub Release 分发

应用更新源固定为公开仓库 https://github.com/DongLanQwQ0/Chrona。
设置 → 检查更新可以查看正式版说明并通过浏览器下载 APK。默认按本地自然日每天自动检查一次：首页实际绘制后延迟 1.5 秒触发，偏好读取、安装版本查询、网络请求、解析和持久化均在后台低优先级线程执行，不等待检查完成才显示首页。失败也计入当天，避免反复启动重试；手动检查不受每日限制，并会显示失败原因。只有发现比安装版本更新的版本才提示，同一版本只提示一次，可在更新页关闭自动检查。首页失去焦点时保留待提示版本，回到首页后再提示。尚无 Release（HTTP 404）不会显示为「已是最新版本」。

## 发布约定

1. 同步 `app/build.gradle`、README、HANDOFF 和 CHANGELOG。每次递增 `versionCode`，`versionName` 使用三个数字段，如 `0.13.71`。
2. 使用已批准的工具链运行 `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon --console=plain`，再执行下述离线检查。正式分发使用 release 构建；它关闭调试、保持不混淆，并复用 `chronaDebugKeystore` 指向的原有密钥。保留 debug 构建用于开发。现有用户必须能覆盖升级，不生成或更换密钥，也不卸载或清除用户数据。
3. 使用现有 Android SDK 的 `apksigner verify --verbose --print-certs` 分别检查新 debug、release 和可取得的上一版 APK。证书 SHA-256 均须为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。正式产物为 `app/build/outputs/apk/release/app-release.apk`。使用 SDK 的 `apkanalyzer manifest debuggable` 检查 release 必须输出 `false`；或使用 `aapt dump badging` 确认无 `application-debuggable`，并核对包名、versionCode 与 versionName。只查看 Gradle 配置不算 APK 验证。
4. 将审核后的源码提交推送到上述仓库，为对应构建源码创建 `v0.13.71` 形式的标签。首次仓库不能为空，需要先推送源码再发布。
5. 在 GitHub Releases 创建正式 Release，填写更新说明、上传 `Chrona-0.13.71.apk`。不要勾选 Draft 或 Pre-release；正式发布后设为 Latest。
6. 核对发布页、APK 下载、应用手动检查与旧版本覆盖安装，按 [设备验收清单](device-acceptance.md) 记录旧版到新版的数据保留结果。未进行或失败的设备验证应在更新说明里明确写出；构建成功不能代替覆盖升级验收。

## 统一离线检查

使用现有 Python 运行 `python checks/run_checks.py quick`，覆盖无需 Java 的迁移、SQLite 和启动报告解析检查。`python checks/run_checks.py full --list` 列出显式纳入的完整离线检查；完整执行命令为 `python checks/run_checks.py full`。入口汇总失败并返回非零退出码，不自动扫描未完成的脚本或直接运行所有 Java 文件。

完整组要求设置已有 `JAVA_HOME` 和 `GRADLE_USER_HOME`；课表检查使用已解析的 biweekly/vinnie 缓存，配置与学期检查使用已有 `build/ai-checks/json.jar`。不自动下载依赖。查询检查每次编译当前源码，避免读取旧 class；所有纳入的新检查使用上述工具链变量。独立 Java 检查中尚未纳入 harness 的项目、Gradle 构建与 lint、签名验证、设备操作和真实网络服务均不属于 `full` 的范围。

```powershell
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Minecraft\java21'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:ANDROID_USER_HOME = 'F:\Android\user-home'
$env:GRADLE_USER_HOME = 'F:\Android\GradleCache'
python checks/run_checks.py full
if ($LASTEXITCODE -ne 0) { throw 'Offline checks failed' }
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or lint failed' }
```

检查使用 GitHub 官方 `GET /repos/DongLanQwQ0/Chrona/releases/latest`，不会上传收件箱、图片或 API 密钥。只接受三个数字段的正式版本标签，按数字逐段比较。下载链接必须属于同一仓库的 HTTPS Release 资产，且 APK 已上传完成；缺少 APK 时显示发布页入口。

发布可使用 GitHub 网页或官方 REST API。凭据只用于 GitHub 认证，不写入脚本、仓库或日志。先创建 Draft、上传并核对 APK 的大小与 SHA-256，再正式发布并核对 `/releases/latest`。
