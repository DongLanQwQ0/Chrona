# 开发文档

[项目首页](../README.md) · [用户使用指南](user-guide.md)

本文面向构建与维护代码的开发者。下载安装和日常操作请看使用指南。

## 项目概况

拾时是原生 Android Java 应用，使用用户配置的 AI 服务生成日程草稿，用户审核后写入应用专属的系统日历。

| 项目 | 配置 |
| --- | --- |
| 应用 ID | `com.donglan.chrona` |
| 当前版本 | `0.14.6`，versionCode `113` |
| 最低 Android 版本 | API 26（Android 8.0） |
| 编译 / 目标 SDK | 36 / 36 |
| Java 源码级别 | 17 |
| 数据库版本 | 10；升级必须保留已有记录 |

网页滚动条用 `scrollbar-width:none` 与 `::-webkit-scrollbar` 隐藏轨道，禁止通过 `overflow:hidden` 禁止滚动。统一下拉保留原 select 的 name/value/input/change，触发按钮使用 combobox、选项使用 listbox/option，顶层 popover 避免 dialog 裁切；键盘方向/Home/End/Enter/Space/Escape/Tab 与焦点回归必须验证。课表按浏览周只读调用 `library.select("").current(day)`，沿用手机学期边界和最近规则，不持久化网页选择。

## 原生设置页布局规范

新增或修改设置类页面必须遵守以下规则，禁止页面主体、顶栏或卡片外缘贴屏幕左右边缘。此规则具体化既有外观页、设置主页和更新页的实现；此前只有历史修复记录，没有集中写明容器与调用顺序。

1. 层级为 `FrameLayout stage → GlassBackdropView + ScrollView viewport → 一个纵向 LinearLayout content → 顶栏/分组标题/卡片/操作控件`。背景可以铺满屏幕，内容容器左右必须各有 **20dp** 留白。全宽卡片、输入框和按钮的宽度仅填满内容区域，不能跨过这层留白。卡片内边距不替代页面留白。
2. 跨设备同步、局域网访问及以后同类二级设置页使用 `SettingsPageLayout.content/header/show`，不要另写安全区容器。`show(activity, root, animatePresentation)` 仅首次主动呈现传 true，旋转恢复和后台重绘传 false，避免重播。公共实现保留内容上下 8dp/28dp；设置主页已有 16dp/24dp、更新页已有 24dp/24dp 不在本轮改变。顶栏采用 48dp 返回触控区域、居中 Lucide 箭头和同一行标题；顶栏位于内容容器中，与卡片外缘对齐。
3. `UiStyle.page(activity, content)` 会设置背景、系统栏配色并调用 **`content.setFitsSystemWindows(true)`**，因此正确顺序是 `page → content.setFitsSystemWindows(false) → content.setPadding(20dp, top, 20dp, bottom) → transparent background`。遗漏关闭会使 Android 的系统 inset 分发覆盖内容 padding；只在调用 page 前设置 padding 无法保证留白。
4. 系统栏、屏幕挖孔和键盘安全区只由视口负责：`UiStyle.applyInsets(stage, viewport)`。API 35+ 会将初始视口 padding 与系统 inset 相加、底部取导航栏与键盘的较大值。不得把 content 作为安全区目标，或在 content 再开启 fitsSystemWindows；不得重复叠加 inset。背景仍可延伸到系统栏下面。API 26–34 保持系统默认窗口安全区。
5. 控件复用 `UiStyle.colors/title/muted/input/button/toggle/glass`，图标采用 Lucide；主题及深色状态使用现有 ThemeStore。`input` 和 `fieldTrigger` 会把内部 padding 设置为 16dp/14dp，`button` 会设置为 18dp/10dp；确需局部覆盖时在样式调用之后设置，不能依赖调用之前的值。`glass` 保留容器 padding，`toggle` 调整颜色及最小高度。控件 margin 只负责卡片内部布局，不承担页面 20dp 留白。
6. 不增加无必要的说明文字。布局改动保持原有分组间距、滚动能力、触控区域和功能。二维码承载 172dp、8dp 内距、主题描边和 RADIUS_FIELD 圆角；码图为不透明白底黑码、四模块静区，布局在已有内容区域内居中，不能撑破卡片。

发布前检查：运行 `python checks/settings_layout_check.py`，执行真实内容构建及 inset 方法，在多 SDK/密度与侧边挖孔/键盘变化下检查层级、左右像素值和不累加安全区；同时运行完整离线检查与 Android 构建/lint。该 JVM 检查使用 Android 平台替身，不构成 Android 渲染验收。具备设备时，在两页分别检查浅色/深色、纵横屏、首屏/滚动底部、打开键盘、大字体，截图确认顶栏/卡片外缘/全宽控件始终留有左右 20dp 内容留白（系统侧边安全区另加），安全区不遮挡；没有设备必须在验证记录中写明，不能用网页截图代替原生页面验收。

## 本地构建与签名

电脑通过手机内置的局域网网页访问，不设独立客户端或构建模块。离线资源位于 `app/src/main/assets/lan/`；二维码在本地由 [ZXing core 3.5.3](https://github.com/zxing/zxing) 编码，带临时配对 fragment 的二维码启停/变址/变码/到期与实际解码检查为 `python checks/lan_qr_check.py`；HTTP 使用 NanoHTTPD 2.3.1，服务/权限、认证与真实存储适配分别由 `LanAccessService`、`LanSecurity`、`LanWebServer` 管理，协议和验收边界见 [局域网访问](lan-access.md)。

同步核心保留在 `shared/src/main/java/` 并由 Android 源集直接编译。运行 `python checks/sync_core_check.py` 使用既有 Gradle 缓存中的 OkHttp/Okio/Kotlin 及 `build/ai-checks/json.jar`；可通过 `CHRONA_SYNC_CLASSPATH` 指定现有检查依赖。同步协议见 [sync-contract.md](sync-contract.md)。

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
$env:ANDROID_USER_HOME = 'F:\Android\user-home'
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
| 局域网访问 | `LanSettingsActivity`、`LanAccessService`、`LanSecurity`、`LanWebServer`、`assets/lan/` |
| 多设备同步 | `SyncSettingsActivity`、`AndroidSync`、`AndroidSyncData`、`SyncJobService`、`shared/` |

独立检查位于 `checks/`。不同检查的依赖与运行方式并不相同，应按修改范围选择，并阅读对应检查文件。

Dock 点击与高光回归检查：设置现有 `JAVA_HOME` 后运行 `python checks/dock_navigation_check.py`。它编译并执行真实 `DockNavigationLayout`，用最小 UI 队列替身模拟子 View 的点击入队、detach 取消回调和布局几何，检查点击、长按、取消及连续动画的绘制位置。无需新增依赖，生成文件位于忽略的 `build/`；这不等同于 Android 运行时或真机触摸验证。

## 技术文档

动效和启动优化见 [统一动效与启动测量](motion-startup.md)。`checks/lifecycle_motion_check.py` 使用替身运行实际返回保护和中断恢复方法；`checks/startup_metrics_check.py` 只校验测量数据解析。连接设备后使用 `checks/measure_startup.py` 测 TTID/TTFD，不能用离线检查代替 1 秒验收。

当前整合：[首页、课程与可信反馈](home-course-integration.md)。历史问题审查与修复记录：[软件审查与完善清单](quality-audit.md)。当前数据库版本为 v10；备份使用 SQLite ATTACH 事务复制快照，避免使用旧 Android SQLite 不支持的 VACUUM INTO。

统一离线验证使用 `python checks/run_checks.py full`，其中包含同步核心检查 `sync_core_check.py`；可用 `python checks/run_checks.py full --list` 查看清单。浏览器交互检查仍需单独运行，未纳入该离线清单。环境与正式构建、APK 验签说明见 [分发文档](github-releases.md)。设备流程单独按 [验收清单](device-acceptance.md) 记录。正式包使用 `:app:assembleRelease`，关闭调试并沿用既有签名；开发包仍使用 debug。

本轮离线回归：`python checks/detail_draft_check.py`、`python checks/job_execution_check.py`、`python checks/data_integrity_check.py`、`python checks/config_backup_check.py`。前三项分别执行实际 retained session/协调方法与真实 SQLite SQL；配置检查用内存偏好和密钥存储替身执行实际 ConfigBackup。Java 客户端检查 `checks/NetworkSafetyCheck.java` 使用离线 HTTPS 假连接，需要现有 org.json 检查运行库，不访问真实模型。检查生成物均在 `build/`，这些检查不能替代 Android 生命周期与设备测试。

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

网页有限动效使用原生 Web Animations：控件 120–180ms，页切换 240ms、卡片滚动呈现 260ms，反向从当前显示帧衔接；动态监听 `prefers-reduced-motion`，开启时完成正在进行的动画并清理。IntersectionObserver 只呈现尚未进入视口的卡片，十秒轮询不播放动画。输入所在弹窗不随内容刷新重建；dialogGeneration 隔离旧请求，sequence 保护路由内容与 revision，刷新搜索列表保留焦点/选区/滚动。浏览器检查单独运行 `node checks/lan_web_check.cjs`、`lan_feedback_check.cjs`、`lan_motion_check.cjs`，使用真实 Edge 与模拟 API，不能替代手机端到端。图片附件删除图标执行 `python checks/attachment_remove_check.py`：真实构造器/Android 替身及 Pillow 光栅化，6 种密度 × 3 字体环境检查图标中心和触控回调；该结果不等于 Android 截图。
