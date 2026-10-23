# 开发指南

[文档索引](README.md) · [项目基本要求](project-requirements.md) · [UI 设计规范](ui-style-contract.md) · [当前状态](../HANDOFF.md)

按功能选择文档与代码入口，再阅读相关公共组件。本文只保留构建环境、结构和工作流，功能契约由对应文档维护。

## 技术基线

| 项目 | 配置来源与当前值 |
| --- | --- |
| 应用 | 原生 Android Java，`com.donglan.chrona` |
| 版本 | `app/build.gradle`：0.15.42 / 204 |
| Android | minSdk 26，compileSdk / targetSdk 36 |
| 语言与构建 | Java 源码 17，AGP 9.4.1，Gradle Wrapper 9.6.0 |
| 数据库 | `TaskStore.DATABASE_VERSION`：16 |

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
| UI 与动效 | `UiStyle`、`UiSurfaces`、`UiDialogs`、`UiMotion` 及公共布局组件 | [公共 UI 库](ui-library.md)、[UI 规范](ui-style-contract.md)、[交互基线](ui-interaction-baseline.md)、[性能测量](motion-startup.md) |
| 小组件与更新 | `AgendaWidgetProvider`、`ReleaseUpdates`、`GitHubRelease` | [小组件](features/widgets/desktop-widgets.md)、[发布](github-releases.md) |

## 本机构建

胶囊计时快速复验使用 `:app:assembleCapsuleTest`：独立构建变体将桌面窗口设为5秒、点击窗口设为10秒，版本名带 `-capsule-test-5s-10s`。同包同签名可保留数据覆盖安装；测试结束用正式release覆盖恢复。debug/release的BuildConfig始终为30／60秒，发布不得选用capsuleTest产物。离线 `python checks/atomic_capsule_check.py` 同时检查两种配置。

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

## 启动本机安卓虚拟机

双击桌面「启动安卓虚拟机」快捷方式，或在 PowerShell 7 中运行：

```powershell
& 'E:\Programs\拾时 · Chrona\tools\Start-AndroidEmulator.ps1'
```

脚本使用F盘现有 `Chrona_Classroom_36`，默认竖屏1080×2376并开启电脑键盘输入。没有实例时直接启动；已有窗口时复用；后台无窗口时先询问是否保留数据重启，不强制终止。修改硬件配置前在AVD原目录保存备份，启动日志位于 `F:\Android\user-home\emulator-logs`。屏幕像素与电脑上的窗口尺寸不同，窗口可缩放。

`-CheckOnly` 只读检查；`-RestartHeadless` 明确允许将已有后台实例重启为窗口；可用 `-Width 1080 -Height 2376` 调整像素。脚本不清数据、不重新安装Android、不操作真机。点击输入框后可使用电脑键盘；中文输入还需要模拟器内支持中文的输入法。

## 原生布局接入

通用视觉要求统一见 UI 规范。设置页采用 `SettingsPageLayout.content/header/show`，首次主动展示才播放呈现，恢复与数据更新保留稳定内容。

确需维护公共布局时，注意 `UiStyle.page` 会设置 fitsSystemWindows：内容容器随后关闭该标志并设置自己的留白，安全区只由 `UiStyle.applyInsets(stage, viewport)` 管理，避免重复 inset 或覆盖 padding。样式函数可能写入内部 padding，局部覆盖应在样式调用后。中间父容器与滚动视口的绘制职责分别处理，选择栏采用公共 motion host，保持完整动画和内容对齐。

## 开发、验证与交付

执行纪律见仓库 `AGENTS.md`；当前交付、候选、有效证据、缺口与下一步只维护在 `HANDOFF.md`。修改前先确认本批完成条件，尽早探明决定验收的设备／服务路径；实现后固定候选，集中验收，最后生成正式标签与发布资产。新增需求更新受影响项，不重启已完成且证据仍有效的工作。

1. 检查当前变更，读目标功能文档与直接依赖，备份将修改的文件。
2. 完成同一功能涉及的状态、数据、入口及恢复路径；保持用户授权范围和已确认交互。
3. 开发后集中执行相关检查、构建及必要的实际交互验证。`checks/` 中各脚本说明自身依赖；`python checks/run_checks.py full --list` 可列出统一离线集合，`full` 执行该集合。浏览器、设备和真实服务验证单独进行。
4. 实际结果写[验证记录](validation-history.md)，产品改动写对应功能文档及 `CHANGELOG.md`，当前限制写 `HANDOFF.md`。正式发布按发布指南核对有效 APK 和升级路径。

纯文档修改检查事实、链接和差异，无需重新打包 APK。仅相关输入或候选变化时补充验证，已有有效证据无需反复执行。归档保留历史证据，不当作当前要求或待办。

`RepeatDeviceCheck`、`BrowserDeviceCheck` 与 `BackupRestoreDeviceCheck` 的写入前置统一由 `FixtureIsolation` 检查：仅官方模拟器上的 `com.donglan.chrona.followup.validation.v33`，学校功能停用且无真实账号、无外部同步配置或自动同步、无运行中或正在启动的 LAN 会话。旧 `.validation` 包及实体手机均拒绝运行。离线 `fixture_isolation_check.py` 检查拒绝条件和入口顺序；重复系列删除专项使用 `check=ui / phase=repeat / scope=delete_only`。备份往返使用 `check=backup_restore`，数据库、文件与偏好另设隔离目录／命名空间，校验大课堂正文及提交后维护失败；仅使用合成数据。

胶囊设置行渲染使用同一隔离守卫，入口`check=capsule_settings`，参数`theme=light|dark`、`widthDp=320..600`、`font=1..2`。宽度与字体仅覆盖测试Activity资源，不更改模拟器全局设置。真实页面和行监听器参与测试，OEM能力及状态值使用展示夹具；该检查不能替代vivo权限启停、胶囊事件或原生大卡片验收。

自动重复提示隔离夹具入口为`check=compact_detail_atomic / scope=automatic_merge`，同样支持`theme/widthDp/font`的Activity级覆盖。实际进入前后台、插入合成来源、取消及选择提交，验证真实候选仅删除所选组；沿用`FixtureIsolation`，拒绝实体手机及有真实学校／同步配置的环境。截图存目标应用外部files目录，报告输出`compact_report`。浏览器专项`node checks/lan_web_check.cjs --merge-only`使用模拟API，数据选择安全性另由`python checks/candidate_merge_check.py`验证生产事务与SQLite。

反馈以结果、证据和影响下一步的缺口为主；交接明确源码／产物位置、适用候选、未完成项及负责动作。避免常规工具播报、重复状态、泛泛自证与无法执行的后续建议。遇到同一路径连续失败且没有新证据，先重新定位再尝试，独立工作继续推进。
