# 拾时 · Chrona

独立 Android 日程应用。首版闭环是：输入文字或通过系统分享文字 → 后台调用用户配置的 AI 服务 → 在应用内审核、修改日程草稿 → 写入专属的本地系统日历并由系统提醒。

当前版本：`0.1.0`。完整产品需求与历史决策见 [HANDOFF.md](HANDOFF.md)。

## 当前功能

- 应用内文字输入、Android `text/plain` 分享入口；每条输入先存入本地 SQLite 收件箱。
- `JobScheduler` 异步处理输入，网络可用时调用兼容 OpenAI Chat Completions 的自定义 HTTPS API；一次输入可返回多条草稿，记录服务返回的 token 用量。
- API 基础地址、模型名和密钥由用户填写。密钥以 Android Keystore 的 AES/GCM 加密后保存在本机；不要将真实密钥写入代码或文档。
- 日程草稿在应用内确认或修改后，写入应用专属的本地系统日历；支持继续在应用内更新已写入的日程。时间不完整的草稿不会自动写入。
- 解析结果通知与失败重试。日程提醒使用系统日历。

尚未实现：图片、语音、剪贴板、批量输入、按需联网检索、历史分类、完整的日程删除流程、全天日程编辑、模型能力检测及实际的提示词缓存统计。当前只统计 API 返回的 token 用量。

## 开发环境与构建

| 项目 | 当前配置 |
| --- | --- |
| Android Studio | `F:\Android\AndroidStudio` |
| Android SDK | `F:\Android\Sdk`，Android API 36 |
| Java | `D:\Minecraft\java21`，实际为 Java 22.0.2 |
| Gradle | Wrapper 9.6.0；缓存 `F:\Android\GradleCache` |
| Android Gradle Plugin | 9.4.1 |
| 应用 | Java 源码级别 17，`minSdk 26`，`targetSdk 36` |

用户已审核并批准上述 Gradle 与 Android Gradle Plugin 工具链。后续若需要**新增或更换构建工具链**，先提交具体方案供用户审核。项目路径含中文，`gradle.properties` 中的 `android.overridePathCheck=true` 已在当前环境中通过构建。

在 PowerShell 7 中构建：

```powershell
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Minecraft\java21'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:GRADLE_USER_HOME = 'F:\Android\GradleCache'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

产物位于 `app\build\outputs\apk\debug\app-debug.apk`，构建产物和本机配置已被 `.gitignore` 排除。Android Studio 可直接打开本目录。项目尚无远程 Git 仓库。

## 已验证

- 2026-09-26：`assembleDebug` 构建成功。
- Debug APK 已安装到一台实际 Android 手机，应用首页启动正常。
- 自定义 API 解析成功：测试输入 `2026-09-28 10:00-11:00 Chrona test meeting` 生成了对应的 10:00–11:00 草稿。该服务接受的测试模型名为 `deepseek-flash`；实际模型名必须以各服务的响应为准。
- 用户确认系统日历写入测试通过。测试输入可能仍留在应用收件箱中。

## 后续接力重点

1. 先读本文件、[HANDOFF.md](HANDOFF.md) 和 [CHANGELOG.md](CHANGELOG.md)，再查看对应模块代码；不要将历史需求当成已完成功能。
2. 先补齐日程删除与草稿生命周期，再逐项实现图片、剪贴板、语音、批量输入和按需联网检索。
3. 当前数据库版本为 1，应用已经安装到手机。任何表结构调整都必须编写迁移，不能直接删库或只改 `onCreate`。
4. 真机回归至少覆盖：多个草稿、时间不确定、API 错误与重试、日历权限拒绝、已写入日程的应用内更新。
