# 局域网浏览器访问

网页使用手机已有数据与业务规则。首页和日历包含启用的本地课表来源；系统日历额外受「包含系统其他日程」与手机读取权限控制。日程列表管理 Chrona 自身的活动、待办与作业，课表页负责 ICS 学期管理。合并结果保留原来源记录与附件，详见 [合并日程](../schedules/candidate-merge.md)。

## 日常操作

| 页面 | 操作 |
| --- | --- |
| 首页 | 未来时间线及近期记录、作业截止标识、待办总数、今日截止／逾期／待安排统计；快速打开最近作业或日历 |
| 日程 | 待处理／待安排／历史／全部；类型、日历状态、待办范围和日期预设筛选；分组选序、搜索、分页、选择本页或全部筛选结果、批量写入与删除 |
| 收件箱 | 状态与类型筛选、排序、搜索、分页、跨页多选、批量写入与删除 |
| 详情 | 原文、图片与文件读取；AI 重处理；追加事项；编辑、删除与写入；待办／作业完成和恢复；有效活动确认及撤销 |
| 作业详情 | 来源管理的标题、内容、截止时间与类型只读；执行时间和提醒可调整；可清空执行时间；来源提供完整链接且折姜星辰启用时，显示两个直达作业按钮 |
| 日历 | 日／周／月／年、前后切换、日期跳转、启用来源汇总、原文详情跳转；周内容导出 PNG 与三档密度 |
| 导入课表 | ICS 预览与导入、按周查看、原始 ICS 下载及学期删除 |
| 日程来源 | 查看全部内容并分页、启用来源、选择主来源；隐藏空来源及未启用的学校功能 |
| 跨设备同步 | 查看状态、切换自动同步、立即同步、分页查看冲突、选择保留版本；涉及本机日历删除时明确确认 |
| 采集与附件 | 文字、图片和文件；新建先保存原文再上传，全部成功后按用户选择调用 AI；已有记录可追加附件 |

筛选选择栏共用网页组件与主题色，选中状态具有无障碍语义；同组宽度由内容区域统一分配，高光位置与宽度连续过渡。返回列表保留其筛选、页码和滚动位置。局部刷新复用内容未变的时间线节点，并用位置过渡呈现布局变化。编辑浮层打开时暂停自动刷新。
首页的时间线、最近事项和待办分别使用独立的展示节点标识；同一日程在多处出现时，各区域保留自己的节点。每轮刷新每个旧节点最多复用一次，不跨区域搬走记录；十秒自动刷新与手动刷新遵循同一规则。

批量写入调用手机的 `BulkCalendarPublisher`：收件箱提交 capture ID，日程页提交 candidate ID；每条独立提交，信息不完整、已完成、已关联或正在处理的事项跳过，并返回写入／跳过／失败数量。旧 revision 在执行前拒绝。选中范围可跨分页保留，每次最多 500 条；更改筛选会清空所选。

## 与手机端的范围差异

日程排序按标签分别保留，筛选支持今日、本周、本月及前后一段；待办范围与类型联动。重复日程共用手机规则与操作入口，可查看月份实例、设置新系列、跳过、改期、单次完成/恢复和停止；停止保留历史，详见[重复日程](../schedules/repeat-schedules.md)。

浏览器通过在线手机工作，没有独立云端数据库。AI 与 WebDAV 凭据、折姜星辰登录与刷新、备份恢复及课程关联在手机设置管理；网页日历实时使用共享来源设置。时间刻度缩放与系统级提醒配置保留在手机端。

附件每个最多 20 MiB，逐个流式上传；失败时保留已保存原文与成功附件，重试只发送未成功项目；版本冲突后的下一次重试先读取最新记录版本，不自动重传。纯附件记录使用类型占位原文，不将文件名作为正文。图片复用手机图片导入和 AI 处理；普通文件沿用元信息提取，不把文件名视为正文。学校管理的作业收件及正在处理的记录不能追加附件。上传、来源修改和同步冲突操作同样要求已配对会话与 CSRF 凭证。

日／周／月／年及周图片按手机时区的自然日划分，覆盖夏令时的非 24 小时日。来源列表与原生页面共用 `ScheduleSourceRepository`；收件审核状态共用 `TaskStore.reviewStatus`，明确但尚未写入的活动仍进入待确认。

## 结构与数据边界

手机是数据和行为的权威。`LanSettingsActivity` 由用户开启会话，`LanAccessService` 使用 Android `connectedDevice` 前台服务监听当前非 VPN Wi-Fi/以太网的私有 IPv4，固定端口 8765；监听失败会如实显示。服务默认关闭，未持久化开启状态，不接收开机启动，也不使用粘性重启。网络地址变化自动关闭，需再次手动开启。

NanoHTTPD 2.3.1 负责 HTTP 解析与 Socket 生命周期，最多 8 个连接，读取超时 10 秒。`LanWebServer` 提供随 APK 打包的 HTML/CSS/JavaScript 和接口。网页复用手机主题色、玻璃卡片、中文页面层级与 Lucide 图标，宽屏呈两栏，移动宽度保留底部导航。网页只有临时界面状态，数据来自所连接手机。

记录/日程经 `TaskStore` 读写，SQLite 事务和 revision 检查拒绝旧页面覆盖；日历通过 `CalendarStore` 操作，课表通过 `TimetableStore` 和 `compareAndRestore` 更新。ICS 先预览会被替换的学期，由用户确认后导入。普通附件按真实记录关联的文件 ID 读取，图片通过 `ImageStore` 校验的存储名称读取，输入文件路径无法访问任意文件。普通文件作为下载返回，图片以 JPEG 显示。

已开启自动 WebDAV 同步时，浏览器写入请求 `AndroidSync` 和 `SyncJobService`；SQLite 关闭及课表提交刷新小组件。手机后台收到的 AI 请求使用普通持久化任务，避免从后台申请仅限前台用户发起的 UIDT；后台限制仍由 Android 决定。正在处理的记录禁止编辑，已写入日历的记录禁止直接重解析。

权限、通知、后台限制与 AI/WebDAV 账号在手机设置管理；同步冲突也可在已配对浏览器处理。系统日历授权无法通过浏览器授予；API 不返回密钥或 WebDAV 凭据。

## 访问保护

- 每次开启生成安全随机六位十进制配对码（保留前导零、10 分钟有效）及独立 256 位会话/写入凭证，保存在内存；配对码通过 POST JSON 提交，会话使用 HttpOnly、SameSite=Strict 的会话 Cookie，写入凭证使用请求头。凭证不放 URL、日志或持久化网页存储。
- Host 必须等于正在监听的 IP:端口，Origin 必须同源；存在 Sec-Fetch-Site 时只允许 same-origin/none。所有非公开接口及附件在读取存储前验证会话。所有修改仅允许 POST，并要求 Origin 与写入凭证，拒绝跨站请求与域名重绑定。
- 配对每个来源一分钟最多 5 次、全局一分钟最多 20 次，最多 16 个会话；会话 12 小时后失效，配对码过期不影响已建立会话；新浏览器配对需在手机重新配对。关闭服务清空所有凭证、关闭 Socket。
- JSON 请求最大 1 MiB，配对请求最大 256 字节；二进制附件仅通过专用接口限长流式接收。拒绝未知方法、分块请求体、错误 MIME、无效 UTF-8、超范围/非整数数值和缺失必需字段。静态文件白名单、内容类型限制及 CSP 阻止用户文本成为可执行 HTML。

HTTP 仅适合可信局域网。Android 16/target36 默认允许 INTERNET 应用访问局域网，本实现同时声明/请求附近设备权限以覆盖 Android 16 可选限制。未来提升到 target37 时应按新的 ACCESS_LOCAL_NETWORK 规则调整；当前 target36 不声明该权限。官方说明：[本地网络权限](https://developer.android.com/privacy-and-security/local-network-permission)、[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)。

二维码由本地 ZXing core 3.5.3 生成，访问 URL 的 **fragment** 携带当前六位配对码（形如 `/#pair=000007`，此处为测试码）。扫码打开后网页最先读取并立即用 `history.replaceState` 清除 fragment，再仅尝试一次现有 `/api/pair` POST；片段不会随 HTTP 请求或 Referer 发送。成功仍取得独立高熵会话，错误/过期/无效二维码回到手动入口，不自动重试、不写持久存储。普通可复制地址不带配对码。二维码须视作临时配对凭据，仅向可信设备展示；10 分钟过期、关闭/重新配对/网络变化的既有撤销机制保留。地址或码变化重绘，过期/关闭清除图像。

二维码承载为 172dp 主题描边圆角白底，8dp 内距，实际码图 156dp；保持黑白对比与四模块静区，不裁切有效码。真实解码、前导零 fragment、码/地址变化、过期及停服通过 `python checks/lan_qr_check.py`；浏览器自动配对、URL 先清理、失败/过期回退、刷新不重用、手动配对通过 `node checks/lan_web_check.cjs`。

网页图标使用标准 Lucide 设置齿轮，与主题图标同尺寸/笔画。主要路由切换、弹窗进出、链接正文展开收起和反馈使用 120–180ms 可取消动画；列表/按钮保留轻量交互反馈。普通刷新不重播页面动画，快速重复操作以最后一次为准；`prefers-reduced-motion` 关闭这些动画。浏览器检查覆盖触发/结束、快速路由/弹窗/展开切换、输入焦点和草稿保持、桌面/移动与浅/深主题。

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

`lan_security_check.py` 执行真实生产认证类的 25 项离线检查，已纳入完整回归。`lan_web_check.cjs` 使用已安装 Edge、Node 原生 WebSocket 和 CDP，独立后台配置及模拟手机 API；检查配对、导航、原文/日程修改、XSS、手机时区、全天 UTC、手动建日程、ICS、退出、深浅色与移动溢出。`lan_parity_check.cjs` 补充作业快捷筛选、多选写入、活动确认撤销、追加事项、来源字段只读、作业链接、执行时间清空、月视图与窄屏选择栏检查。结果与截图分别位于 `build/lan-web-checks/`、`build/lan-parity-checks/`。这些结果证明浏览器界面与请求行为，真实 Android SQLite 和 Calendar Provider 另由设备集成检查验证。

`app/src/androidTest/…/LanAccessInstrumentation.java` 使用隔离的临时 Context/SQLite 文件，运行真实 `LanWebServer` 与 `TaskStore`，覆盖未授权/跨站/CSRF、旧 revision、真实记录与候选保存/删除、无效时间不改变数据、退出/关闭；不读取用户数据库或发送真实 AI/WebDAV 请求。连接设备后执行：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am instrument -w com.donglan.chrona.test/com.donglan.chrona.LanAccessInstrumentation
```

检查成功必须返回 `LAN integration: PASS (real Android SQLite and HTTP)`。无在线设备时只确认该检查包编译通过，不能声称运行成功。仍须在手机/电脑真实局域网验证设置视觉、权限拒绝、前台通知、切后台/锁屏、网络变化、关闭撤销、日历权限/写入、附件读取、真实 WebDAV 多机同步、覆盖安装和提醒送达。
## 网页课表与控件

页面、弹窗、备注输入框和课表横向区域隐藏原生滚动条，仍可用滚轮、触摸或键盘滚动；长表单底部保存按钮保留。状态筛选和日程类型统一主题下拉，方向键选择，Enter/Space 确认，Escape 关闭，支持外点关闭和减少动态偏好。

课表不显示文件名列表框；按浏览周日期自动命中手机已有春夏秋冬学期边界，无当期时沿用最近学期规则，多源同距沿用 ID 倒序，不改变手机手动选择。上一周、本周、下一周可自然跨学期；标题显示所选实际学期。导入建议仍来自已有 AcademicTerms，网页不另按月份划分。

年历按月份组织，日期可进入日视图，月份可进入月视图；月视图日期与日视图返回月形成浏览闭环。全年结果达到服务器上限时，明确提示仅显示部分日程，日期计数标为已加载内容。周视图密度只调整内容间距，不改变日期、日程范围或导出结果。自动同步开关复用手机设置和调度，开启前须已配置账号，切换不隐式执行立即同步。

记录候选可单独删除，确认文案区分候选与整条收件；托管作业收件禁用 AI 重处理和手动新增入口。同步冲突预览包括实际同步的类型、起止、截止、完成、备注等字段及完整原文；提醒值属于设备本地设置，不伪装为远端版本字段。
