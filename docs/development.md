# 开发指南

[文档索引](README.md) · [项目基本要求](project-requirements.md) · [UI 设计规范](ui-style-contract.md) · [当前状态](../HANDOFF.md)

按功能选择文档与代码入口，再阅读相关公共组件。本文只保留构建环境、结构和工作流，功能契约由对应文档维护。

## 技术基线

| 项目 | 配置来源与当前值 |
| --- | --- |
| 应用 | 原生 Android Java，`com.donglan.chrona` |
| 版本 | `app/build.gradle`：0.15.27 / 189 |
| Android | minSdk 26，compileSdk / targetSdk 36 |
| 语言与构建 | Java 源码 17，AGP 9.4.1，Gradle Wrapper 9.6.0 |
| 数据库 | `TaskStore.DATABASE_VERSION`：14 |

版本和数据库格式以源码为准，发布后更新本表和交接页；历史文档的旧值不作为配置依据。

## 按功能进入代码

源码根为 `app/src/main/java/com/donglan/chrona/`，共享同步源码在 `shared/src/main/java/`，网页资源在 `app/src/main/assets/lan/`。

| 功能 | 主要入口 | 文档 |
| --- | --- | --- |
| 记录、首页与详情 | `MainActivity`、`DashboardActivity`、`TaskDetailActivity` | [日程与浏览](features/schedules/schedule-browser.md) |
| AI 提取与处理 | `ai/`、`processing/` | [解析链路](features/ai/ai-pipeline.md)、[时间规则](features/ai/ai-time-extraction.md)、[请求协议](features/ai/ai-request-notes.md) |
| 日历、课表与来源 | `calendar/`、`AgendaRepository`、`ScheduleSourceRepository`、`CalendarTimelineLayout` | [本地课表](features/calendar/course-timetable.md)、[来源](features/calendar/system-calendar-home.md)、[候选合并](features/schedules/candidate-merge.md) |
| 提醒与原子岛 | `AgendaReminder`、`AgendaReminderJobService` | [提醒](features/reminders/agenda-reminder-settings.md)、[vivo 配置](features/reminders/vivo-atomic-setup.md) |
| 学校作业与课表 | `ZjuClient`、`ZjuSync`、`ZjuHomeworkCalendar` | [折姜星辰](features/zju/zju-services.md) |
| 数据、附件与备份 | `data/`、`image/`、`ConfigBackup`、`SchoolDataBackup` | [备份范围](features/data/backup-coverage.md)、[附件与链接](features/schedules/selection-and-link-fetch.md) |
| 局域网与同步 | `LanAccessService`、`LanSecurity`、`LanWebServer`、`AndroidSync`、`shared/` | [局域网](features/connectivity/lan-access.md)、[WebDAV 同步](features/connectivity/sync-contract.md) |
| UI 与动效 | `UiStyle`、`SettingsPageLayout`、`DockNavigationLayout`、`DockSpring` | [UI 规范](ui-style-contract.md)、[交互基线](ui-interaction-baseline.md)、[性能测量](motion-startup.md) |
| 小组件与更新 | `AgendaWidgetProvider`、`ReleaseUpdates`、`GitHubRelease` | [小组件](features/widgets/desktop-widgets.md)、[发布](github-releases.md) |

## 本机构建

本机使用 PowerShell 7。SDK、用户数据和 Gradle 缓存在 F 盘；新增或更换工具链先经用户确认，不修改全局 Java 配置。

```powershell
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Minecraft\java21'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:ANDROID_USER_HOME = 'F:\Android\user-home'
$env:GRADLE_USER_HOME = 'F:\Android\GradleCache'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleRelease :app:lintRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or lint failed' }
```

目录名含中文，保留 `android.overridePathCheck=true`。debug / release 都沿用 `chronaDebugKeystore` 指向的既有签名，不能生成另一份密钥替代。正式 APK 为 `app/build/outputs/apk/release/app-release.apk`；debug 和 androidTest 按任务需要构建。签名与发布验证见[发布指南](github-releases.md)。

## 原生布局接入

通用视觉要求统一见 UI 规范。设置页采用 `SettingsPageLayout.content/header/show`，首次主动展示才播放呈现，恢复与数据更新保留稳定内容。

确需维护公共布局时，注意 `UiStyle.page` 会设置 fitsSystemWindows：内容容器随后关闭该标志并设置自己的留白，安全区只由 `UiStyle.applyInsets(stage, viewport)` 管理，避免重复 inset 或覆盖 padding。样式函数可能写入内部 padding，局部覆盖应在样式调用后。中间父容器与滚动视口的绘制职责分别处理，选择栏采用公共 motion host，保持完整动画和内容对齐。

## 开发、验证与交付

1. 检查当前变更，读目标功能文档与直接依赖，备份将修改的文件。
2. 完成同一功能涉及的状态、数据、入口及恢复路径；保持用户授权范围和已确认交互。
3. 开发后集中执行相关检查、构建及必要的实际交互验证。`checks/` 中各脚本说明自身依赖；`python checks/run_checks.py full --list` 可列出统一离线集合，`full` 执行该集合。浏览器、设备和真实服务验证单独进行。
4. 实际结果写[验证记录](validation-history.md)，产品改动写对应功能文档及 `CHANGELOG.md`，当前限制写 `HANDOFF.md`。正式发布按发布指南核对有效 APK 和升级路径。

纯文档修改检查事实、链接和差异，无需重新打包 APK。仅相关输入或候选变化时补充验证，已有有效证据无需反复执行。归档保留历史证据，不当作当前要求或待办。
