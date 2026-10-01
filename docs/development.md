# 开发文档

[项目首页](../README.md) · [用户使用指南](user-guide.md)

本文面向构建与维护代码的开发者。下载安装和日常操作请看使用指南。

## 项目概况

拾时是原生 Android Java 应用，使用用户配置的 AI 服务生成日程草稿，用户审核后写入应用专属的系统日历。

| 项目 | 配置 |
| --- | --- |
| 应用 ID | `com.donglan.chrona` |
| 当前版本 | `0.13.72`，versionCode `92` |
| 最低 Android 版本 | API 26（Android 8.0） |
| 编译 / 目标 SDK | 36 / 36 |
| Java 源码级别 | 17 |
| 数据库版本 | 9；升级必须保留已有记录 |

## 本地构建与签名

以下是项目已经验证的 Windows 环境。盘符路径属于本机配置，其他环境请替换成实际路径。私钥、本机配置与构建产物不应提交到仓库。

| 项目 | 当前配置 |
| --- | --- |
| Android Studio | `F:\Android\AndroidStudio` |
| Android SDK | `F:\Android\Sdk`，Android API 36 |
| Java | `D:\Minecraft\java21`，实际为 Java 22.0.2 |
| Gradle | Wrapper 9.6.0；缓存 `F:\Android\GradleCache` |
| Android Gradle Plugin | 9.4.1 |
| 应用 | Java 源码级别 17，`minSdk 26`，`targetSdk 36` |

维护本工作区时，新增或更换工具链须先经用户确认。项目路径含中文，`gradle.properties` 中的 `android.overridePathCheck=true` 已在当前环境中通过构建。

Debug 构建明确使用 `gradle.properties` 的 `chronaDebugKeystore=F\:/Android/user-home/debug.keystore`（属性文件内需转义盘符后的冒号）。这份密钥对应旧版签名证书 SHA-256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`；私钥不在仓库中。其他环境构建时须将该 Gradle 属性指向同一份密钥，否则构建会停止，避免静默生成无法覆盖安装的 APK。

在 PowerShell 7 中构建：

```powershell
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Minecraft\java21'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:GRADLE_USER_HOME = 'F:\Android\GradleCache'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

产物位于 `app\build\outputs\apk\debug\app-debug.apk`，构建产物和本机配置已被 `.gitignore` 排除。Android Studio 可直接打开本目录。源码与分发仓库为 https://github.com/DongLanQwQ0/Chrona，正式版 APK 见 [Releases](https://github.com/DongLanQwQ0/Chrona/releases)。

提交应用代码前至少执行一次构建；需要 Android 静态检查时运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --console=plain
```

纯文档修改检查内容、链接和 diff 即可，不需要重新打包 APK。开发验证要区分编译、静态检查、离线测试与真机操作，不能将构建通过写成真机验证通过。

## 代码入口

源码位于 `app/src/main/java/com/donglan/chrona/`。

| 范围 | 主要入口 |
| --- | --- |
| 主界面与记录 | `DashboardActivity`、`MainActivity` |
| 日程审核与使用引导 | `TaskDetailActivity`、`GuideActivity` |
| AI 与后台处理 | `ai/`、`processing/` |
| 本地数据与附件 | `data/`、`image/` |
| 日历与链接正文 | `calendar/`、`web/` |
| 外观与界面样式 | `ThemeStore`、`UiStyle` |
| 更新与桌面小组件 | `GitHubRelease`、`ReleaseUpdates`、`AgendaWidgetProvider` |

独立检查位于 `checks/`。不同检查的依赖与运行方式并不相同，应按修改范围选择，并阅读对应检查文件。

## 技术文档

- [AI 处理链路](ai-pipeline.md)、[请求说明](ai-request-notes.md)、[输出与设置](ai-settings-output-notes.md)、[日程规范化](ai-normalization-notes.md)
- [日程浏览与筛选](schedule-browser.md)、[首页系统日历](system-calendar-home.md)
- [日期时间选择器](date-time-picker.md)、[列表动画](list-entry-animation.md)、[紧凑选择菜单](compact-selection-menu.md)
- [附件选择与链接读取](selection-and-link-fetch.md)、[桌面小组件](desktop-widgets.md)
- [GitHub Release 分发](github-releases.md)

## 维护与历史记录

- [交接与需求](../HANDOFF.md)：产品边界、历史决策和继续开发的注意事项。
- [更新记录](../CHANGELOG.md)：各版本改动。
- [历史实现笔记](implementation-notes.md)：从旧 README 迁出的详细实现描述。
- [历史验证记录](validation-history.md)：各版本实际执行过的构建、安装与设备验证。

修改已有数据库格式时追加顺序迁移；发布新版本时同步构建版本、项目首页、交接与更新记录。历史文档中的版本和结论属于当时状态，当前实现以代码和本轮验证为准。
