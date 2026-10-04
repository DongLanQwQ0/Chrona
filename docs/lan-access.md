# 局域网浏览器访问

## 结构与数据边界

手机是数据和行为的权威。`LanSettingsActivity` 由用户开启会话，`LanAccessService` 使用 Android `connectedDevice` 前台服务监听当前非 VPN Wi-Fi/以太网的私有 IPv4，固定端口 8765；监听失败会如实显示。服务默认关闭，未持久化开启状态，不接收开机启动，也不使用粘性重启。网络地址变化自动关闭，需再次手动开启。

NanoHTTPD 2.3.1 负责 HTTP 解析与 Socket 生命周期，最多 8 个连接，读取超时 10 秒。`LanWebServer` 提供随 APK 打包的 HTML/CSS/JavaScript 和接口。网页复用手机主题色、玻璃卡片、中文页面层级与 Lucide 图标，宽屏呈两栏，移动宽度保留底部导航。网页只有临时界面状态，数据来自所连接手机。

记录/日程经 `TaskStore` 读写，SQLite 事务和 revision 检查拒绝旧页面覆盖；日历通过 `CalendarStore` 操作，课表通过 `TimetableStore` 和 `compareAndRestore` 更新。ICS 先预览会被替换的学期，由用户确认后导入。普通附件按真实记录关联的文件 ID 读取，图片通过 `ImageStore` 校验的存储名称读取，输入文件路径无法访问任意文件。普通文件作为下载返回，图片以 JPEG 显示。

已开启自动 WebDAV 同步时，浏览器写入请求 `AndroidSync` 和 `SyncJobService`；SQLite 关闭及课表提交刷新小组件。手机后台收到的 AI 请求使用普通持久化任务，避免从后台申请仅限前台用户发起的 UIDT；后台限制仍由 Android 决定。正在处理的记录禁止编辑，已写入日历的记录禁止直接重解析。

权限、通知、后台限制、AI/WebDAV 账号和同步冲突在手机设置管理。系统日历授权无法通过浏览器授予；API 不返回密钥或 WebDAV 凭据。

## 访问保护

- 每次开启生成 128 位临时配对码及 256 位会话/写入凭证，保存在内存；配对码通过 POST JSON 提交，会话使用 HttpOnly、SameSite=Strict 的会话 Cookie，写入凭证使用请求头。凭证不放 URL、日志或持久化网页存储。
- Host 必须等于正在监听的 IP:端口，Origin 必须同源；存在 Sec-Fetch-Site 时只允许 same-origin/none。所有非公开接口及附件在读取存储前验证会话。所有修改仅允许 POST，并要求 Origin 与写入凭证，拒绝跨站请求与域名重绑定。
- 配对每个来源一分钟最多 5 次、全局一分钟最多 20 次，最多 16 个会话；会话 12 小时后失效。关闭服务清空所有凭证、关闭 Socket。
- JSON 请求最大 1 MiB，配对请求最大 256 字节；拒绝未知方法、分块请求体、错误 MIME、无效 UTF-8、超范围/非整数数值和缺失必需字段。静态文件白名单、内容类型限制及 CSP 阻止用户文本成为可执行 HTML。

HTTP 仅适合可信局域网。Android 16/target36 默认允许 INTERNET 应用访问局域网，本实现同时声明/请求附近设备权限以覆盖 Android 16 可选限制。未来提升到 target37 时应按新的 ACCESS_LOCAL_NETWORK 规则调整；当前 target36 不声明该权限。官方说明：[本地网络权限](https://developer.android.com/privacy-and-security/local-network-permission)、[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)。

## 验证与复现

使用现有工具链，无需安装新运行时：

```powershell
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Minecraft\java21'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:ANDROID_USER_HOME = 'F:\Android\user-home'
$env:GRADLE_USER_HOME = 'F:\Android\GradleCache'
python checks/run_checks.py full
python checks/sync_core_check.py
node checks/lan_web_check.cjs
.\gradlew.bat :app:assembleRelease :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

`edit_conflict_check.py` 检查真实候选编辑基线与网络身份逻辑，以及手机保存的后台线程、事务/锁、Calendar 副作用前检查、提交后回调和草稿保留接线；`detail_draft_check.py` 检查旋转恢复后的已提交备注与基线。两者属于离线检查，真实手机/电脑并发仍须设备验证。

`lan_security_check.py` 执行真实生产认证类的 25 项离线检查，已纳入完整回归。`lan_web_check.cjs` 使用已安装 Edge、Node 原生 WebSocket 和 CDP，独立后台配置及模拟手机 API；检查配对、导航、原文/日程修改、XSS、手机时区、全天 UTC、手动建日程、ICS、退出、深浅色与移动溢出。结果与截图位于 `build/lan-web-checks/`。这些结果只证明浏览器界面与请求行为。

`app/src/androidTest/…/LanAccessInstrumentation.java` 使用隔离的临时 Context/SQLite 文件，运行真实 `LanWebServer` 与 `TaskStore`，覆盖未授权/跨站/CSRF、旧 revision、真实记录与候选保存/删除、无效时间不改变数据、退出/关闭；不读取用户数据库或发送真实 AI/WebDAV 请求。连接设备后执行：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am instrument -w com.donglan.chrona.test/com.donglan.chrona.LanAccessInstrumentation
```

检查成功必须返回 `LAN integration: PASS (real Android SQLite and HTTP)`。无在线设备时只确认该检查包编译通过，不能声称运行成功。仍须在手机/电脑真实局域网验证设置视觉、权限拒绝、前台通知、切后台/锁屏、网络变化、关闭撤销、日历权限/写入、附件读取、真实 WebDAV 多机同步、覆盖安装和提醒送达。
