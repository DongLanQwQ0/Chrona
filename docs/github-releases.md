# GitHub Release 分发

应用更新源固定为公开仓库 https://github.com/DongLanQwQ0/Chrona。
设置 → 检查更新可以查看正式版说明并通过浏览器下载 APK。默认每天后台检查一次；只有发现比安装版本更新的版本才提示，同一版本只提示一次，可在更新页关闭自动检查。后台失败保持安静，手动检查会显示失败原因。尚无 Release（HTTP 404）不会显示为「已是最新版本」。

## 发布约定

1. 同步 `app/build.gradle`、README、HANDOFF 和 CHANGELOG。每次递增 `versionCode`，`versionName` 使用三个数字段，如 `0.13.71`。
2. 使用已批准的工具链运行 `:app:assembleDebug :app:lintDebug --no-daemon --console=plain`。现有用户安装的是原 debug 签名，必须继续使用 `F:/Android/user-home/debug.keystore`，不能更换密钥后要求用户卸载应用。
3. 检查 APK 签名 SHA-256：`3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。APK 来源为 `app/build/outputs/apk/debug/app-debug.apk`。
4. 将审核后的源码提交推送到上述仓库，为对应构建源码创建 `v0.13.71` 形式的标签。首次仓库不能为空，需要先推送源码再发布。
5. 在 GitHub Releases 创建正式 Release，填写更新说明、上传 `Chrona-0.13.71.apk`。不要勾选 Draft 或 Pre-release；正式发布后设为 Latest。
6. 核对发布页、APK 下载、应用手动检查与旧版本覆盖安装。未进行的设备验证应在更新说明里明确写出。

检查使用 GitHub 官方 `GET /repos/DongLanQwQ0/Chrona/releases/latest`，不会上传收件箱、图片或 API 密钥。只接受三个数字段的正式版本标签，按数字逐段比较。下载链接必须属于同一仓库的 HTTPS Release 资产，且 APK 已上传完成；缺少 APK 时显示发布页入口。

发布可使用 GitHub 网页或官方 REST API。凭据只用于 GitHub 认证，不写入脚本、仓库或日志。先创建 Draft、上传并核对 APK 的大小与 SHA-256，再正式发布并核对 `/releases/latest`。
