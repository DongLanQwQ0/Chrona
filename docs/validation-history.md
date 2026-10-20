# 验证记录

当前版本为 **0.15.29 / 191**。下面记录近期版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.29 — 正式发布与覆盖升级 — 2026-10-20

- 源码提交 `0ee3217` 与不可变标签 `v0.15.29` 已通过本地 Git 推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.29) 已发布并设为 Latest。Git 直连出现连接重置，使用当前系统已有的代理完成推送，未修改全局 Git 或网络设置。
- 公开 `/releases/latest` 返回 `v0.15.29`、非 Draft、非 Pre-release；`Chrona-0.15.29.apk` 已上传、3079870 字节。公开下载的 APK 实际包名 `com.donglan.chrona`、191／0.15.29、签名与原版一致；最终本地分发包来自该公开下载。API 记录 `build/release-0.15.29-public.json`，截图 `build/release-0.15.29-published.png`。
- 模拟器官方 0.15.27 的真实 `UpdateActivity` 读取公开 API，显示「发现新版本 v0.15.29」与下载按钮；覆盖安装公开 APK 后，官方 0.15.29 显示「已是最新版本」。日志 `build/release-0.15.29-{old-update,current-update}.log`，对应截图同名 PNG。下载按钮实际将正确 APK URL 交给外部浏览器；模拟器浏览器连接重置，宿主公开下载成功，二者不混为同一路由成功。
- 升级前先导出不含 API 密钥的完整备份并保存旧 APK；不卸载、不清数据，公开 Release APK 覆盖安装成功。升级后再导出备份，比较全部 15 张 SQLite 表结构和逐行数据、1 条收件、1 个候选、全部配置、学校／课表数据与课堂文档和图片内容，均一致；只排除本次备份操作生成的 `config.exportedAt`。日志 `build/release-0.15.29-upgrade-preservation.log`，恢复副本 `.local-backups/release-preparation-20261020/`。本轮没有普通收件附件或桌面小组件样本，不据此宣称其升级验收通过。
- 发布夹具补充只读完整备份入口；旧 IntentFilter-only 下载拦截未匹配实际外部启动，且浏览器留在任务栈上会使 `startActivitySync` 等待并未创建的 Activity（系统启动结果为 delivered-to-top）。检查改为新建测试页面、只断言更新结果与下载入口；外部交接使用上述实际浏览器证据。该修正仅影响 androidTest，生产 APK 和发布标签不变。
- 手机原子岛 OEM 宿主外观、手机非空数据升级、真实付费 AI 与真实远端 WebDAV 尚未现场验收，已在正式发布说明中列出。

## 0.15.29 — 作业通知最终验收续轮 — 2026-10-20

- `build/homework-notice-durable-lifecycle.log` 通过真实 SQLite 与 `TaskCompletion.set` 入口，验证部分完成仅移除对应项、剩余 ID／标题与原到期时间保留、静默更新、全部完成撤下、来源关闭、绑定替换及删除后的撤下。通知清理不删除或撤销已完成作业。
- 正常浅色与 320dp／字体 1.45 深色的真实系统通知栏折叠／展开和实际「×」关闭通过；关闭后完成字段未改变。折叠保留系统标题／摘要，展开标题与关闭键同首行，正文可读。日志 `build/homework-notice-system-shade-light.log`、`build/homework-notice-system-shade-narrow-dark.log`，有效画面在 `build/homework-notice-final-captures/notification-*.png`。这一组才是系统宿主证据，先前 Activity 预览只证明 RemoteViews 布局。
- 主页新刷新在「全部待办」与折叠箭头之间，共用操作组；同两组原生检查验证顺序、不重叠、48dp 目标、文字完整、原折叠偏好恢复、忙碌禁用／无障碍状态，以及未配置来源时实际打开学校设置。日志 `build/homework-refresh-home-{light,narrow-dark}.log`；已配置来源的仅作业请求／排队／回调沿用 24 项生产 owner 检查，本轮不发送真实学校请求。
- 进程结束后发现已有通知仍在而异步写入的通知身份尚未落盘。`HomeworkNotice` 改为先同步保存身份再投递，关闭同样同步清理；写入失败时不留下未跟踪的新通知。`build/homework-notice-durable-restart-status.log` 证明新进程仍能读取相同 expiry、真实未完成作业与系统通知。修正后的 Debug／Release、双 lint 通过，日志 `build/0.15.29-durable-final-build.log`。
- 最新真实十分钟检查通过，`build/homework-notice-durable-expiry.log` 证明真实 600000ms 后系统通知缺席、未完成作业仍保留；夹具保留到观察结束才清理，未通过删除源数据提前撤下。只有独立包受到测试；模拟器显示配置已恢复，记录 `build/homework-notice-display-{before,after}.txt`。恢复副本 `.local-backups/homework-notice-followup-20261020/`。
- 独立复审发现首项有截止时普通批量通知遗漏总数；已在系统折叠摘要与展开截止信息同时保留数量，不增加额外区域。`build/homework-notice-batch-shade-light.log` 验证两项真实 SQLite 作业、首项有截止、其它标题保留、真实系统折叠／展开数量与截止可见、实际关闭不完成作业。图片 `build/homework-notice-final-captures/notification-batch-{collapsed,expanded}-light.png`。单项空截止隐藏及原子岛布局不变；批量修正副本 `.local-backups/homework-notice-batch-count-20261020/`。
- 普通通知、主页刷新及批量数量的独立 UI 复审全部通过，无未修缺陷。最后候选 Debug／Release、双 lint 通过，日志 `build/0.15.29-batch-final-build.log`。原子岛图片仅支持 RemoteViews 判断，手机 OEM 宿主及手机十分钟现场效果仍未验收。
- `build/0.15.29-final-signing.log` 记录新 Debug、Release 与手机备份 0.15.26 APK 的签名证书一致；Release 实际包名 `com.donglan.chrona`、191／0.15.29、不可调试。最新候选已保存为 `build/candidates/0.15.29/Chrona-0.15.29.apk`（3079870 字节），未提交或发布；签名一致支持升级资格，实际旧版数据覆盖升级仍待发布阶段检查。

## 0.15.29 — 公共 UI 库与执行规范整理 — 2026-10-20

- `UiStyle` 从 1635 行／80571 字节收敛为 563 行／27136 字节；渲染与采样归 `UiSurfaces`，浮层归 `UiDialogs`，内容切换归已有 `UiMotion`。四处弹窗操作行合为一处，删除两项无调用的私有样式函数和始终为空的状态；生产与原生夹具直接迁移，不留旧转发别名。该数字仅说明原单文件规模变化，不表示整个库减少同等体积。
- `AGENTS.md` 固化工作收敛、失败止损、证据复用、反馈总结与无功能删减的整理门槛；当前工作只维护在精简后的 `HANDOFF.md`，旧交接原文归档。职责、接入及维护入口见[公共 UI 库](ui-library.md)。文档本地链接 124 项通过，日志 `build/ui-library-doc-links.log`。
- 统一离线集合现为 91 项：补跑 78 项全部通过，复用 12 项仍有效检查，再通过新增浮层缩放检查，未重复整轮。日志 `build/ui-library-regression.log` 与 `build/ui-library-*.log`；其中共享操作行 63 项、缩放反向／嵌套／减少动画／脱离清理 17 项。学校调度替身已匹配新候选 ID 接口，加入主页仅作业手动刷新后 24 项通过。
- 实际连续开关浮层发现背景缩放未归位，失败见 `build/ui-library-native-light-before-scale-fix.log`。修复为退出完成前保留最初基准，退出中重开继续使用同一基准，结束或脱离清理；原幅度与时长不变。最终原生正常深浅、320dp／字体 1.45 深浅四组各 32 项通过，覆盖确认／取消、多行编辑、日期／时间值与嵌套恢复。日志 `build/ui-library-native-{light,dark}.log`、`build/ui-library-native-narrow-{light,dark}.log`，实际图片 `build/ui-library-captures/`。
- 原生菜单深浅各 164 项通过；配置局部渲染的 27 组覆盖 320／360／640dp、字体 1／1.5／2、首尾长按与甩动，未更改系统设置；亚克力重排中间帧、祖先变换、重绘等价及静止停止刷新通过。日志 `build/ui-library-menu-{light,dark}.log`、`build/ui-library-motion-layout.log`、`build/ui-library-acrylic-motion.log`。窄屏整页浮层测试临时更改的模拟器显示配置已恢复，前后记录 `build/ui-library-emulator-display-{before,after}.txt`。
- 隔离 Debug／androidTest／lintDebug 通过，日志 `build/ui-library-validation-final-build.log`；正式 Release／lintRelease 通过，日志 `build/ui-library-release-build.log`。合成操作仅在 `com.donglan.chrona.followup.validation`，未重新操作用户手机。首轮截图被遗留的系统通知栏遮挡，未用作视觉证据；关闭该栏、增加前台焦点校验后重新取得实际画面。
- 专门 UI 审核代理对照原备份、调用者及有效日志并检查实际截图，确认职责与迁移完整、功能和参数保留、缩放生命周期修复正确，本轮审核通过，无待修缺陷。作业通知的系统宿主、完成收起与最终发布缺口继续见[当前工作单](../HANDOFF.md)。恢复副本 `.local-backups/ui-library-organize-20261020-160028/`。

## 0.15.29 — 新作业信息卡、独立同步频率与详情精简 — 2026-10-20

- 复用 `VivoAtomicNotification` 已注册 SuperX 通道，新增 `HomeworkNotice` 信息卡及独立通知 ID。正常／岛上 RemoteViews 复用原子卡壁纸／极光；标题、截止、正文与 48dp Lucide「×」，不包含地点、进度或完成动作。系统超时 600000ms，另设结束闹钟清理 OEM 身份；相同通知的关闭／过期按本次 expiry 校验，旧回调不关闭新提示。
- `ZjuHomeworkStore.Result.newHomework` 只包含事务中新增且未完成的作业；重复拉取、普通内容修改、已提交新导入均不发新作业提示。真实 SQLite upsert 检查扩至 45 项通过；原有日历关联、删除和个人完成状态保持。
- 作业 300000ms、课表 1800000ms 独立尝试时间；单次 Job 按最早到期来源重排，前台保留到期检查。手动全量刷新与后台单来源请求共用单个网络 owner，缺失 scope 排队，取消后新请求使用新 epoch；独立 fetch 共用一次内存认证会话，失败来源不应用空结果。`zju_sync_cadence_check.py` 22 项通过，覆盖两类开关、联网／持久 Job、单来源、部分失败、凭据失败防重试风暴、排队与取消、前后台生命周期。
- 学校作业详情整组附件／原文／重新解析区域不加入布局；普通收件保留。独立包原生检查覆盖两种详情、卡片两类宿主布局、真实通知 600000ms、提前／旧过期保护及真实「×」PendingIntent，关闭后本地作业完成字段和原文均未修改。正常浅色及 320dp／字体 1.45／深色日志 `build/homework-notice-{light,dark}.log`；图片 `build/homework-{detail,ordinary-retained,card-island,card-shade}-{light,dark}.png`。岛上截图是 RemoteViews 在模拟器中的实际布局；未获得本轮 vivo OEM 宿主的新样式画面。
- 通知权限／频道检查 650 项、原子协议生命周期 111 项及原卡动画资源合同通过。Debug、androidTest 与 lintDebug 首轮通过；最终正式包、独立 UI 复审及发布证据待补。源码备份 `.local-backups/homework-notice-sync-20261020/`。

## 0.15.28 — 菜单分隔行、空时间提示与窄屏筛选 — 2026-10-20

- 旧筛选栏标签固定保留 14sp、按完整字宽分配。正常宽度保持原 Dock；放不下时在原行显示当前值与 Lucide 下拉图标，公共面板完整列出选项和计数。第一版横向视口被独立审核以静态大段留白、滚动中间高光仍可能裁切两项 P2 否决，已撤销其全部视口与公共 Dock 改动；新方案复审关闭两项 P2，原 Dock 参数无改动。正常浅色与 320dp／字体 1.45／深色的两页切换、取消、中间与末项选择通过。
- 纵向浮动菜单改用公共 `UiStyle.addMenuItem`：透明文字行、相邻 1dp 分隔、左对齐、选中勾及整行涟漪，无独立边框和缩放；设置／筛选选择、真实课程关联预览与主来源子菜单、详情操作均覆盖。`build/menu-style-{light,dark}.log` 两组各 164 项通过，包含长标签、长列表底部可达、首项长按无缩放及取消不写课程规则。实际图为 `build/menu-{short,held,long-top,long-bottom,course-actions,course-source,detail}-{light,dark}.png`；用户同款筛选图为 `build/filter-menu-{1,2}-dark-open.png`。
- 空开始提示精确为「欸~这好像还没有时间」，空结束为「遥遥路途，没有终点~」。`build/time-hints-{light,dark}.log` 通过，核对空值与文字、正常／大字体渲染、实际点按日期选择器、取消、全天切换及填值不被提示替代；图为 `build/detail-time-hints-{light,dark}.png`。相关文档原空白规则已按用户新要求替换。
- 本轮两次 UI 独立复审均通过，菜单与提示本轮核对 18 张实际图。离线菜单合同更新为 88 项、40 个回归负例，覆盖 API 26 与 36；旧放大按钮包络检查随菜单不缩放的新合同替换。Dock 180、计数 15、详情编辑 23 及 LAN 7 项通过；其他未受影响检查沿用下节有效结果。首次正式 lint 发现 API 30 的 `setStateDescription` 调用未设门槛，已修正并重新通过 Debug／Release、lintDebug／lintRelease，日志 `build/0.15.28-release-build.log`。
- 新 debug／release APK 验签通过，证书与旧版一致。正式包 `build/distributions/0.15.28/Chrona-0.15.28.apk` 为 `com.donglan.chrona`、190 / 0.15.28、3072378 字节，无 debuggable；发布资产 SHA-256 为 `a663130bf9b49b1f47a409240fb74cbcee24ada72b005d79292a0b027ccff19a`。GitHub 已登录，待发布及旧版手动发现更新／模拟器覆盖安装；实体手机非空数据、真实付费 AI、真实远端 WebDAV 本轮仍未验收。
- 恢复副本 `.local-backups/filter-overflow-20261020/`、`.local-backups/menu-and-time-hints-20261020/`；下节 0.15.27 保留源码标签，全部功能合入本版，不另发包。

## 0.15.27 — 切周、课表一致性与静默推理 — 2026-10-20

- 课堂周表复用双轴滚动及边缘切周，旧／新原生视图用同一进度滑动，日期栏保持原实例。Android 36 独立模拟器在 420dpi／字体 1／浅色与 540dpi／320dp／字体 1.45／深色各 34 项通过，日志 `build/week-timetable-{light,dark}.log`。覆盖表内平移、两端切周、短拖、纵滑、取消、多指、连续反向、过渡层清理及关闭动画后保留横向位置。
- 学校课表复用导入的常规／特殊分类，单次调课保留实际日期和只读详情；离线检查覆盖真实重复周末课及短学期日期保留。设备检查还监听双向切学期的首次绘制：每个重建网格均直接在保留的 x=320 绘制，再开始入场；学校原账号数据未现场读取。
- 首页系统日程开关取消启用背景，仅改变图标颜色。深浅两组 `build/home-toggle-{light,dark}.log` 各 12 项通过，覆盖 off/on/off、偏好写入、原生无障碍状态、颜色末态和图标外透明像素。日历页原开关样式保留。
- UI 审核初次发现关闭动画时横向位置丢失，已补一次布局后的恢复；测试曾在过渡准备间隙抓到日期栏／网格不同周，现同时断言已显示周、无过渡、完成布局及实际网格日期后再截图。最新稳定画面与中途暂停帧为 `build/classroom-week-{light,dark}.png`、`build/classroom-week-mid-{light,dark}.png`；暂停帧只证明空间关系，不代表真实中点耗时。
- AI 所有内容类型统一 180 秒读等待，保留 30 秒连接超时和日程整轮 240 秒预算。真实 loopback HTTP 文本请求静默 31 秒后只返回内容 SSE，成功完成；取消及短总预算检查仍通过。`classroom_summary_check.py` 最终 140 项通过，未调用付费服务。
- 发布完整离线组首轮 81/88，通过部分见 `build/0.15.27-offline-full.log`。7 个失败均为随已改接口／布局过时的测试适配：轮播布局替身、选择框颜色入口、详情底部删除位置、周操作代理、首页图标开关构造、课堂共享文档编译依赖和同步课堂适配器。补齐后这 7 个入口逐项复跑通过：轮播 108、选择框 720、完成提醒 32、日历 139、周手势、图片 10、同步锁 3；未重复运行仍有效的另外 81 项。
- 本轮源码恢复点 `.local-backups/week-and-timetable-20261020/`，发布元数据和测试适配恢复点 `.local-backups/release-0.15.27/`。此前 Markdown、分享、同步／备份等证据见下列续修记录。实体手机当前未连接，未进行本轮真实账号、付费 AI 或真实 WebDAV 验证。
- 专门 UI 审核复审通过，本轮 P2 和稳定帧证据缺口均关闭；此前各续轮已通过的结论继续有效。正式 Debug／Release、lintDebug／lintRelease 通过，日志 `build/0.15.27-release-build.log`。源码及 `v0.15.27` 标签已推送，构建源码为 `d58538e`；本版本保留源码检查点，全部内容合入上节 0.15.28。
- 新 debug、release 与已发布 0.15.26 的 APK 签名一致。正式包 `build/distributions/0.15.27/Chrona-0.15.27.apk` 为 `com.donglan.chrona`、189 / 0.15.27、3070626 字节且无 debuggable 标志；Android 36 模拟器从正式 188 覆盖安装到 189、启动首页成功，未卸载或清数据。实体手机的非空真实数据升级仍未验收。

## 未发布续修 — 导出后系统分享与下载目录 — 2026-10-20

- 课堂总结 ZIP 与周日程 PNG 保存成功后自动打开 Android 系统分享面板，使用精确 MIME、内容 URI、ClipData 与临时只读授权。流写入及关闭成功后才排队分享；取消保存或写入失败不启动分享，取消分享保留文件。暂停期间等待页面恢复，已消费的分享不重复弹出。
- Android 10 及以上的周日程 PNG 复用现有 `TaskFileStore` 写入公共 `Download/Chrona`，发布完成后分享；Android 8–9 沿用文件保存器并默认定位下载目录。课堂 ZIP 保留选择保存位置的流程。
- Android 36 独立模拟器分别以 420dpi／字体 1／浅色和 540dpi／字体 1.45／深色完成 ZIP 与 PNG 四组检查，各 12 项通过。检查真实 ZIP 条目和 PNG 解码、系统分享 Intent 与读权限、实际 Downloads URI 及 `RELATIVE_PATH=Download/Chrona/`、`IS_PENDING=0`、取消后文件可读、返回不重复分享，以及 ZIP 取消／写入失败不分享。实际打开系统面板后仅返回，未选择接收方。日志与截图 `build/export-share-{zip,week}-{light,dark}.{log,png}`。
- 独立审核发现日程导出期间重建页面会丢失分享回调，已改由工作区保留的 `WeekExport` 接交进行中任务与结果。确定性检查阻塞导出队列、发起任务、真实 `Activity.recreate`、验证新页接管同一运行状态，再放行写入并自动分享；Downloads 与 SAF copy 两条路径在上述两组环境各 16 项通过，日志及图片 `build/export-share-week{,-saf}-recreate-{light,dark}.{log,png}`。SAF copy 在 Android 36 投递文件选择结果后验证。独立 UI 复审已关闭该 P2，本轮审核通过。
- Debug、androidTest 与正式 Debug／Release、双 lint 通过，正式日志 `build/export-share-formal-build.log`；APK 签名验证通过、关闭调试，仍为 188 / 0.15.26。产物 `build/distributions/export-share-20261020/Chrona-0.15.26-export-share.apk`；恢复副本 `.local-backups/export-share-20261020/`。Android 8–9 分支及实体手机未现场验证。

## 未发布续修 — 课堂正文、素材清理与可移植文档 — 2026-10-20

- 课堂正文采用本地打包 Markdown-it 14.1.0 与 MathJax 3.2.2，支持 Obsidian 风格行内与独立公式、行内图片。课堂安排改为蓝色正文点击提取；导出进入原生标题栏右侧。图片预算统一为 40 张，数学提示仅保留“中国大陆教材的常用风格”，不硬编码具体记法。
- 完成文档、完整字幕与正文引用 JPEG 参与 WebDAV 和完整备份。统一图片引用清单用于渲染、ZIP、同步和清理；七天清理与手动清理保护引用图片、未完成及运行中课堂。导出统一相对图片路径；来源弹窗跳过清理掉的非引用图，避免空图片占位。列表和历史采用每页 50 条真实翻页，课程目录仍完整读取后排序。
- 实际已登录智云页面读取到 5 个 PPT `img src` 均使用 `http://video.cmc.zju.edu.cn/ai3/ppt/...`，均加载为 1280×720；这解释了原 HTTPS 校验与浏览器显示之间的差异。相同图片改为 HTTPS 的 HEAD 返回 200、image/jpeg、104917 字节。应用只在确切媒体域名、标准 HTTP 端口下本地升级至 HTTPS，保留其他安全限制；同时接受 content 对象或 JSON 字符串并跳过空 PPT 项。未导出 Cookie 或账号凭据。
- 离线总结 139 项、客户端 73 项与网络诊断 44 项通过；真实回环 HTTP 同步交换 22 项通过，包含课堂文档、图片先上传及另一端下载。Android 原生同步／完整备份 17 项通过，覆盖晚到本地修改保护、同基线恢复缺失缓存、运行中拒绝覆盖、恢复前图片缺失与失败保留、恢复后 Markdown／图片／字幕／历史可达。日志 `build/classroom-assets-summary-check.log`、`build/classroom-assets-client-check.log`、`build/classroom-sync-exchange.log`、`build/classroom-portability-device.log`。
- Android 36 独立验证包：完整课堂浅色流程 38 项；420dpi／字体 1 浅色与 540dpi／字体 1.45 深色 Markdown 各 11 项，覆盖分式、极限、矩阵、积分、代码美元符号、图片去重、来源弹窗、蓝色安排原生确认。首次实际截图发现 MathJax 辅助 MathML 重复显示，已保留无障碍节点并以标准裁切隐藏视觉副本。截图 `build/classroom-current-screens/classroom-markdown-*.png` 与 `build/classroom-markdown-source-{light,dark}.png`。
- 学校设置保存／退出同行、同步圆形图标置顶；窄屏四字按钮初次换行后改为 12dp 水平内距，保持字号与点击宽度。两组设置各 4 项通过并实际执行合成素材清理，图片 `build/classroom-settings-{top,cleanup}-{light,dark}.png`。
- 公共胶囊端部按圆角单元可见面积居中，日历周导出槽为 45dp、分隔无额外横向留白。两组日历的收拢、反向恢复、日期及首尾持有检查通过，图片独立保存在 `build/classroom-calendar-{light,dark}/`。初次检查的 49dp 旧夹具断言已更新；批量拉取目录曾混入旧截图，已排除，深色公共页面有效图片在 `build/classroom-current-screens/files/`。独立 UI 审核通过本轮课堂、设置及胶囊／日历候选。
- 独立遗留 P2：旧 `DashboardActivity.filterDock` 在窄屏大字体下标签挤连、同组字号不同；本轮未修改该筛选栏，后续应独立修复并复审。保留已确认选择栏动效，极端甩动允许高光出屏。
- 正式 Debug／Release 和双 lint 通过，日志 `build/classroom-assets-formal-build.log`；APK 签名校验通过、关闭调试，仍为 188 / 0.15.26，未发布 GitHub Release。安装包 `build/distributions/classroom-20261020/Chrona-0.15.26-classroom.apk`。
- 未执行真实付费 AI 生成、真实远端 WebDAV 交换或实体手机安装；现有真实页面证据仅验证 PPT 地址与显示，不代表整条账号生成链路已在手机验收。源码恢复副本 `.local-backups/classroom-assets-20261020/`。

## 未发布续修 — 记录移除、标题栏动画与时间完整显示 — 2026-10-20

- 移除独立记录的 AI schema、解析工厂、原生／网页编辑转换和筛选入口；提示词升为 `schedule-v13`，仅输出 `events`。纯信息留在原文。旧记录保留存储，详情接口不返回且拒绝编辑；未增加数据迁移或删除。
- `WorkspacePageHeader` 两侧统一 8dp；导入来源下刷新按钮按当前进度渐隐、缩小并收起宽度及间距，退出开始即停止点击／无障碍入口，反向连续恢复。未改变选择栏内部留白或弹簧参数。
- 详情开始／结束／截止字段自然换行、不缩字不省略。`DetailRepeatUiCheck -scope values` 在 420dpi／字体 1／浅色和 540dpi（320dp）／字体 1.45／深色通过完整行宽、高度、字符及实际点按选择器和取消检查；独立旧记录卡片缺席而原文保留。首次用重复实例点按进入了重复页，改用普通事项 fixture 后通过，不属于选择器失败。截图 `build/detail-values-light.png`、`build/detail-values-narrow-dark.png`。
- `ClassroomDeviceCheck -scope header` 同样两组环境通过：课表与课堂 8dp、实际切来源、无残留空槽、动画中间帧及反向续接。完整课堂设备检查 37 项通过，见 `build/header-classroom-full-check.log`。截图 `build/header-*-light.png`、`build/header-*-narrow-dark.png`；模拟器恢复 420dpi／字体 1。
- `checks/ai_auto_publish_check.py` 通过请求协议、59 项时间规范化、363 项执行排期、16 项发布检查（`build/record-ai-check.log`）。收件查询 8 项 SQLite 与 4 项搜索检查通过。真实 Android SQLite／HTTP 集成检查通过旧记录拒绝编辑、详情过滤、原文与历史行保留，以及普通日程增删改和状态操作。
- 实际 Edge 网页专项、完整交互和来源／同步／附件检查通过，日志分别为 `build/record-removal-web-focused.log`、`build/record-removal-web-check.log`、`build/record-removal-web-features.log`。完整脚本的旧「今日安排」标题已更新为现行「首页」；动画测试显式设置 no-preference，仍单独覆盖 reduced-motion，避免依赖宿主系统偏好。已查看桌面与移动端截图 `build/lan-web-checks/record-removal-*.png`。
- 专门只读 UI 审核亲自检查 9 张指定截图与候选代码，确认时间截断关闭，本轮间距、刷新退出／恢复及记录 UI 移除通过，无需退回的实质缺陷。
- 缺课调查：真实手机刷新后本周 10/19 音响技术仍缺席，上周 10/12 有回放，教务课表正常。用户随后确认单双周安排中一周不录像；保持实际回放目录，不补造课堂条目。
- Debug、androidTest、lintDebug 与正式 Release、lintRelease 均通过（`build/record-header-validation-build.log`、`build/record-header-phone-build.log`）。11:26 无线 ADB `install -r` 成功，首页启动 `Status: ok`；手机导入页已显示无刷新空槽。实体机后续页面发生切换，停止追加自动操作，未做完整实体机回归；主要交互与窄屏验收使用独立模拟器。正式包保留 188 / 0.15.26，未发布 GitHub Release。恢复副本及安装前 APK：`.local-backups/remove-inbox-record-20261020/`。

## 未发布续修 — 详情操作、重复页与专门 UI 审核 — 2026-10-20

- 详情候选卡片的重复和删除同行置底，位于原始内容之前；空执行开始／结束字段不再显示占位，切换全天仍保持空白。重复页建立／停止放在公共标题栏，月份使用 `DateIntervalBar`；日程名称与规则合为一个可换行文本区，正常字号首实例由约 y=722px 提前至 y=590px（420dpi）。学期标题整卡居中、下划线，右侧保留「10 门」。
- 专门只读审核代理 `ui_contract_reviewer` 首轮发现重复页层级和间距仍偏高、实例点击重建整页；修正后复审关闭两项。实例点击现在仅更新选中态；原生回归滚动到后段实例、打开并取消操作，验证相同 ScrollView、scrollY 及日期栏实例。`AGENTS.md` 已规定每轮 UI 改动必须独立审核，修正后交回复审。
- 表单补测实际发现窄屏键盘遮住次数输入：视口仍有全屏高度，键盘仅作为其底部 padding，焦点滚动计算使用了错误可见高度。`SettingsPageLayout` 改由视口外安全区容器承接系统／键盘占位。失败证据 `build/detail-repeat-review-form-dark.log`；修复后 `build/detail-repeat-ime-dark.log`、`build/detail-repeat-ime-light.log` 均通过，覆盖每周、结束日期、次数输入完整滚到键盘上方、收起键盘后创建，以及删除确认、建立／停止和日期双向滑动。
- Android 36 独立验证包分别使用 420dpi／字体 1.0／浅色和 540dpi（320dp 宽）／字体 1.5／深色；深色使用长日程名称。学期标题正常与窄屏检查通过，日志 `build/term-heading-light.log`、`build/term-heading-narrow-dark.log`。已查看实际底部操作与原始内容、空时间、重复页、表单键盘和学期截图，位于 `build/detail-repeat-captures/`。
- 公共安全区调整后的 20 个原生页面仍保持返回按钮距安全区 16dp、左侧 20dp、点击区域 48dp，见 `build/detail-repeat-ime-headers.log`；设置布局 72 组替身及原错误反证通过，见 `build/detail-repeat-settings-layout.log`。Debug、androidTest 和 lintDebug 成功，见 `build/detail-repeat-ime-build.log`。
- 最终专门复审亲自检查深浅色键盘截图、最终原生断言和公共标题回归，确认键盘阻塞关闭，本轮改动审核通过，未发现新的实质缺陷。
- 审核另记录既有详情时间值在窄屏大字体下被单行截断，本轮未修复，不能将本次结果表述为详情页全部适配通过。未变动的选择栏动效沿用下一节同一候选的有效证据。模拟器测试结束恢复 420dpi／字体 1.0；实体手机页面效果尚未验收。恢复副本 `.local-backups/detail-repeat-20261020/`、`.local-backups/ui-review-gate-20261020-103524/`。
- 用户随后要求推到手机：正式包名 Release 构建与 lintRelease 通过（`build/detail-repeat-phone-build.log`），安装包无 debuggable 标记。2026-10-20 10:56 经无线 ADB 使用 `install -r` 覆盖现有应用，安装成功、首页启动 `Status: ok`，未卸载或清数据。此次本地续修保留版本 188 / 0.15.26，未发布新的 GitHub Release。

## 未发布续修 — 选择栏、日期手势与跨月子项排期 — 2026-10-20

- 标题槽与日历选择栏的选项等宽填满底板，文字居中；保留原样式、弹簧、放大和惯性。按用户最新决定，极端甩动允许出屏，不再挤窄内容轨道。`WorkspacePageHeader` 在保持左右按钮位置的情况下最后绘制标题槽，高光覆盖刷新／取消按钮。
- Android 36 独立包 `com.donglan.chrona.followup.validation`：420dpi／默认字体深色与 540dpi（320dp 宽）／1.5 倍字体浅色均通过课堂 35 项原生检查，含真实触摸注入的日期双向滑动、工作区不误翻页、来源切换、标题绘制顺序及日期文字居中；选择栏另检查均分、首尾长按、拖出、双向甩动。日志 `build/selector-layer-dark.log`、`build/selector-final-classroom-narrow.log`。
- 实际截图发现日期文字偏上，修正公共日期栏的确定高度测量；窄屏复查又捕获两侧 dp 分别取整导致的 1px 高度不足，已按实际 margin 合计计算。日历常规与窄屏检查通过，含日期文字完整显示／裁切、视图均分、动效、操作胶囊和 12 个月年历；日志 `build/selector-final-calendar-dark.log`、`build/selector-final-calendar-narrow.log`。已查看最终正常日期、窄屏日期／日历，以及深浅主题下高光与刷新按钮重叠的截图，目录 `build/selector-captures/`。测试结束恢复 420dpi、字体 1.0。
- 课堂夹具曾被自己生成的课程悬浮通知挡住触摸。测试现在仅允许独立包，在测试期间关闭自身提醒并清除自身通知，结束恢复提醒偏好且关闭合成学校来源；没有更改正式应用的通知设置。
- `schedule-v12` 的响应分组排期通过生产响应解析入口验证：30／60 天、不同工作量、固定时间、独立提前截止、无真实截止的活动准备、过期边界、独立组、时间不足、排队延迟、跨夜窗口及非法协议。`ScheduleExecutionCheck` 363 项、语义时间 60 项、自动写入 16 项及请求协议检查通过，日志 `build/subtask-spread-check.log`；全部自动排期仍待用户确认。未调用真实付费 AI 服务，未验收模型识别分组的真实准确率。
- Debug、androidTest 构建与 lintDebug 通过，日志 `build/selector-final-build.log`；Dock 离线 180 项、筛选布局 17 项通过。修改前恢复点 `.local-backups/selector-20261020/`、`.local-backups/subtask-spread-20261020/`。本轮未发布、未覆盖正式应用；实体手机效果尚未验收。

## 0.15.26 — 发布验证 — 2026-10-20

- 正式包 Debug／Release 构建与两套 lint 通过，日志 `build/0.15.26-release-build.log`；quick 7/7 通过，日志 `build/0.15.26-quick.log`。本轮布局、手势与权限回退证据见下节。
- APK 包名 `com.donglan.chrona`，版本 188 / 0.15.26，无 debuggable 标志；新 debug、release 与旧 0.15.25 证书一致。分发产物 `build/distributions/0.15.26/Chrona-0.15.26.apk`。
- Android 36 模拟器从正式 0.15.25 覆盖升级成功并启动首页，未卸载或清数据。升级前首页无日程／待办，实体手机非空数据升级与本版本长时后台未验收；真实账号验证应用未被覆盖。
- 发布准备恢复点 `.local-backups/release-0.15.26/`，原 UI 源码备份保留在 `.local-backups/compact-layout-20261019/`。
- `v0.15.26` 已发布为 Latest，正式 Release 非草稿、非预发布，APK 下载返回 HTTP 200；上传资产大小与摘要通过发布脚本核对。正式应用进入设置 → 检查更新，实际显示「已是最新版本」、`v0.15.26` 和本次更新说明，记录 `build/0.15.26-update.xml`；发布信息见 `build/release-0.15.26-artifact.json`。

## 0.15.26 — 紧凑布局与全局标题栏 — 2026-10-19 至 2026-10-20

- 公共标题栏与 Lucide 返回／确认资源已统一。Android 36 独立验证包中测量 20 个原生页面，420dpi／默认字体与 480dpi／1.3 倍字体两组均测得返回按钮距系统安全区下沿 16dp、左侧 20dp，点击区域 48dp。记录页仍走草稿退出，详情页仍走原保存入口；日志 `build/compact-ui.log`、`build/compact-ui-narrow.log`。
- 首页操作胶囊包含作业、系统日程与设置；学校开关仅在同步构建测试标题栏时临时启用，随后还原，未触发网络任务。下一件事使用原两行标题区域放置地点，正常与大字体都检查高度不增加、文字字形互不相交；正常字体补充日程／收件箱与记录页渲染见 `build/compact-lists.log`。
- 课表与课堂保留来源选择实例；学校学期标题可点击，导入空状态靠上，课堂日期栏切周保留实例。课堂合成数据专项 29 项通过；窄屏大字体覆盖来源与课堂导航首尾长按、拖出松手及双向快速甩动，动效水平包络未越屏。证据 `build/compact-classroom.log`、`build/compact-classroom-narrow.log`，截图 `build/compact-captures/`。
- 日历开关胶囊、周导出收拢／反向切换及日期栏通过。初次全宽选择栏甩动捕获高光右界 1080.5127px（屏宽 1080px）；改为底板内部预留轨道包络后，420dpi／默认字体与 480dpi／1.3 倍字体的首尾长按、拖出松手、双向快速甩动均通过，弹簧参数保持原值。日志 `build/compact-calendar-inset.log`、`build/compact-calendar-inset-narrow.log`。实际查看常规与窄屏静止／长按截图；窄屏年历的日期粘连另通过自适应月份列数修复，12 个月网格均检查实际宽度。
- Debug、androidTest 编译与 lintDebug 通过，最新收尾日志 `build/compact-calendar-inset-build.log`，此前全局标题栏日志 `build/compact-final-validation-build.log`。设置安全区 72 项、日期切页 221 项、周视图、筛选布局 17 项、首页筛选／重试与候选反馈离线检查通过；首页重试替身补齐现有偏好参数边界。未调用付费 AI，未操作真实账号数据。
- 公共 Dock 队列替身 180 项通过，日志 `build/compact-dock-check.log`；替身补齐 Android margin 字段及横向滚动边界 API，实际动画仍以模拟器证据为准。新增内部轨道留白仅由日历显式启用，原全宽表单调用维持现状。
- 收尾复查修复权限拒绝的开关状态：`CompoundButton` 监听器同步把勾选退回关闭时，动画现在跟随最终状态；首页和日历同时刷新关闭提示。新增回归在旧实现得到错误进度 1.0，修复后开关专项 39 项通过；Android 原生监听器重入拒绝及正常开启均通过。证据 `build/compact-toggle-before.log`、`build/compact-toggle-after.log`、`build/compact-toggle-native.log`。补充 Debug、androidTest、lintDebug 构建通过，`build/compact-recheck-build.log`；未变动选择栏几何与动效参数，沿用前述实际渲染证据。
- 源文件恢复点 `.local-backups/compact-layout-20261019/`。模拟器经用户授权重新启动；测试完成后密度恢复 420、字体恢复 1.0。功能验收使用独立验证包，未覆盖真实账号验证包。

## 0.15.25 — 发布验证 — 2026-10-19

- Debug、Release、两套 lint 与 androidTest 编译通过，日志 `build/0.15.25-release-build.log`；quick 7/7 通过，日志 `build/0.15.25-quick.log`。功能专项与原生交互证据复用下节同一实现的结果，未重复无关 full。
- APK 为 `com.donglan.chrona`、187 / 0.15.25，无 debuggable 标志；新 debug/release 与旧 0.15.24 签名证书一致。产物 `build/distributions/0.15.25/Chrona-0.15.25.apk`。
- Android 36 模拟器原正式包从 186 覆盖升级至 187 并启动成功，无卸载或清数据。升级前日程页为空；此项不代表实体手机非空用户数据升级。真实账号的独立验证包不受本次正式包升级影响。
- 真实付费 AI 生成、实体手机长时后台及本版本应用内手动检查更新尚未验收。后台进程被系统结束后由用户手动续跑检查点。发布准备文件备份 `.local-backups/release-0.15.25/`。

## 0.15.24 — 发布后工作区与课堂续轮 — 2026-10-19

- [需求对照与验收](followup-acceptance-20261019.md)汇总本轮结果。正式候选 `assembleRelease`、`lintRelease` 通过，日志 `build/followup-final-release-build.log`；已有账号验证包 debug/lint 通过，日志 `build/followup-overlay-build.log`。未更新远端发布。
- 离线专项：两轮课堂总结 124 项、课堂客户端 66 项、网络分类 44 项、图片缓存 10 项、学校恢复 17 项；执行时间 288 项、既有归一化 60 项、偏好 22 项、自动写入门槛 16 项通过。受影响的配置备份、设置、日历、按压、开关与筛选专项通过，未重复执行无关 full。
- Android 36 模拟器原生验证：三页状态保留、标题/正文边缘交接、快速反向、浮动底栏、周历边界、日期切换与操作组合并通过。课堂深浅主题及窄屏最终 23 项通过；设置中的独立提示词与自定义时间选择器已检查。真实页面上打开的日期选择器确认为既有自定义滚轮样式。
- 日期文字独立裁切在正常与 360dp/1.3 倍字体下通过。像素比较最初发现箭头抗锯齿差值 4，定位后仅忽略该量级的通道差异；没有用扩大文字视口掩盖越界。外层按压放大完整保留。
- 亚克力生产取样检查 12 项通过；原生动画检查验证 ChangeBounds 中途帧、父级缩放平移、实际完成卡片上移、最终正确背景和静止停止重绘。公共选择栏因共享矩阵变化补验 27 组通过，日志 `build/followup-interaction-layout.log`。
- 原生后台会话检查验证返回后继续、重开接回、多个课堂并行、最后任务结束停止服务；通知检查验证实际系统投递、权限拒绝与账号切换。进程死亡后的自动重启未实现，手动续跑使用检查点；本轮未执行真实付费 AI 生成。
- 合成夹具限于 `com.donglan.chrona.followup.validation`。真实账号所在 `com.donglan.chrona.validation` 的 APK 与私有数据先备份至 `.local-backups/logged-validation-before-overlay-20261019/`，再覆盖安装。未卸载或清空其数据；已启用原本关闭的学校课表，读取到 10 门课程，既有 1158 条字幕与 130→24 张候选图缓存正常。原正式包未被覆盖。
- 截图位于 `build/followup-captures/`、`build/followup-narrow-captures/`、`build/followup-narrow-final/`。模拟器恢复为 420dpi、字体 1.0，继续运行软件图形后端；模拟器结果不代表实体手机帧率或长时后台。

## 0.15.24 — 重复日程与网页一致性 — 2026-10-19

- 既有 full 81 项首轮通过 63 项，18 项因测试替身缺少新增字段/依赖而失败；修复测试替身后分组复跑通过。日志 `build/recurrence-offline-full.log`、`build/recurrence-fixture-rerun*.log`。新增检查已纳入统一入口，共 84 项；未重复执行整轮。
- 重复 AI 67 项、既有归一化 60 项、实例投影 33 项、Calendar 字段 20 项通过。投影检查发现并修复夏令时持续时间偏移，使用固定持续时长与系统日历 DURATION 对齐。去重键新增重复语义后合并组复跑通过，日志 `build/recurrence-fixture-merge-final.log`。
- 网页模拟 API 浏览器检查覆盖排序、日期/类型联动、跨页全选删除、单次身份与重复操作、390px 布局；截图位于 `build/web-parity-checks/`。随后内置浏览器经 ADB 转发连接真机隔离验证服务，实际验证 16 条记录倒序、每日系列五次展开、首个实例从 09:00 改为 11:30，以及停止后详情显示「已停止」。停止后的月份空列表在手机断连后转到 Android 36 模拟器补验通过，显示「本月没有安排」，停止操作不再显示。
- 独立验证应用 `com.donglan.chrona.validation` 的 debug/androidTest 构建及 lintDebug 通过，日志 `build/recurrence-validation-build.log`。vivo 真机 `RepeatDeviceCheck` 22 项通过：真实 Provider 与本地展开一致、跳过、双向跨日期改期、查询/小组件投影、原生按钮及确认框停止、历史保留和旧基线拒绝。报告 `build/recurrence-device/provider-ui-report.txt`；已查看系列与停止状态截图，内容及控件未见裁切。
- `LanAccessInstrumentation` 真实 Android SQLite/HTTP 检查通过，新增覆盖真实排序、创建重复、旧 revision 拒绝、实例查询、跳过、改期身份、停止和批量删除。浏览器联动单独使用验证包 18765 端口；正式版原有 8765 服务保持运行。浏览器夹具限时 10 分钟并在退出时删除自建收件；无线调试断连后的清理尚未现场复核。

- 正式 Debug/Release、两套 lint 与 androidTest 构建通过，日志 `build/0.15.24-release-build.log`。APK 包名 `com.donglan.chrona`、186 / 0.15.24，无 debuggable 标志；新 debug/release 及旧版签名证书一致。
- 模拟器从 0.15.23 覆盖升级到新 debug，再覆盖 release 并启动成功；数据库 13→14，旧列逐项比较通过。旧库收件与候选均为 0，此检查不代表真实用户非空库升级。恢复副本 `build/upgrade-0.15.24/before.tar`。浏览器模拟器夹具退出后实际收件数为 0，临时转发已移除；模拟器保持开启。

## 0.15.24 — 课堂总结 — 2026-10-19

- 既有离线 `full` 79/79 通过，日志 `build/classroom-full-regression.log`；课堂两项专项已加入统一入口。随后共享样式改动涉及的 Dock、亚克力、设置布局、弹窗、小组件偏好、周历及学校 UI 七项专项全部通过，未重复无关检查。
- 修复待导出 URI 恢复抢占本地目录/历史初始化；旋转复用运行状态，待导出操作只消费一次。生成确认框改用公共可滚动正文并适配键盘 resize。
- 按当前轻圆角要求统一普通按钮/输入框 8dp、卡片和小组件 10dp、弹窗 12dp、标签 6dp；选择栏保留独立 28dp 底板与原动态高光几何。卡片阴影不再依赖圆角数值判定。修改前副本位于 `.local-backups/classroom-summary-20261019-163102/ui-refinement/`。
- 上述修复后的 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 再次通过，日志 `build/classroom-ui-refinement-build.log`。真实窄屏、大字体、键盘及恢复交互仍待原生环境验收。
- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过，日志 `build/classroom-native-build-final.log`。首轮发现导出 URI 授权参数的 lint 错误，修复后通过。
- `checks/classroom_summary_check.py`：既有 AI 请求检查及课堂总结 77 项断言通过。覆盖真实循环回送 HTTP、SSE、取消与超时、分段续跑、引用校验、图片预算、账号隔离和离线 Markdown ZIP。Android 位图和原子文件使用 JVM 替身，尚不能代表 Android 运行结果。
- `checks/classroom_client_check.py`：42 项检查通过。生产 Java 客户端另成功读取 179 节课堂、49 节就绪；选中课堂返回 1158 条字幕、130 个截图地址，并成功下载一张 101988 字节的图片。验证仅进行只读访问，日志不保存登录会话或课程正文。
- 发现并修复：连续尾图突破每段 4 张；部分总结误将尚未完成分析的计划图片写入已分析清单。修复后仅导出已完成分段的图片，另保留计划图片数。
- Android 36 模拟器已在重启后使用 WHPX；图形由 SwiftShader 切换为 RTX 5060，SurfaceFlinger 确认 NVIDIA 渲染器。镜像、AVD、日志与启动脚本均在 F 盘。未操作手机。
- 原生课堂检查深浅主题各 11 项通过，覆盖合成课堂 50→72 分页、历史打开、Android 位图去重、AtomicFile/ZIP、账号停用退出与生成弹窗。320dp、1.5 倍字体的截图暴露日期与时间断行、导航字号不一致，修复后复拍确认。键盘可见状态经 WindowInsets 验证，滚动后取消操作完整可见。测试数据未调用外部 AI。
- 既有公共组件 `interaction_layout` 27 组原生布局通过，覆盖 320/360/640dp、1/1.5/2 倍字体、首尾长按/拖出/快速甩动；`interaction_acceptance` 日历初开、未缓存切换、缓存返回、长按拖出与快速反向通过。该结果不代表实体手机帧率。
- 证据位于 `build/classroom-native/`：`light-final-report.txt`、`keyboard-verified-report.txt`、`week-fixed.png`、`dialog-keyboard-verified.png`、`interaction-layout.txt`、`interaction-acceptance.txt`。窄屏修复后的 debug/androidTest/lint 通过，日志 `narrow-fix-build.log`。尚未正式发布。

## 0.15.23 — 2026-10-18

- 发布候选 debug/release、lintDebug/lintRelease 与 androidTest 编译通过，日志 `build/0.15.23-publish-build.log`。APK 185 / 0.15.23，无 debuggable 标记，debug/release 签名符合原证书要求。
- 集中 full 首轮 70/79；九个旧检查的替身接口、提取签名或旧分类断言未同步，修复后仅重跑失败项，全部通过。未为测试改变生产动效。
- 有效截止按 due/end/start 的模型、实际 SQLite 筛选/提醒、浏览器首页/列表/详情及编辑保留原始 due 检查通过；浏览器专项 `checks/deadline_consumers_check.cjs`。
- 浙大生产客户端的拦截 HTTP 与隔离密钥 fixture 17 项、真实 SQLite 作业更新和绑定隔离 41 项通过。学校公开入口从当前电脑访问出现 TLS 失败／超时，未执行真实登录、学校同步或 Android Keystore 验证；无真机覆盖升级验收。

- 网页补齐续轮：年历闰日、跨年导航、月日互跳、截断提示、周密度和 320/390/600px 布局专项通过；实际检查桌面浅色与手机深色年历截图，见 `build/lan-calendar-checks/`。日历原有 parity 检查通过。
- 新增自动同步开关与冲突字段展示后，同步 API 检查增至 67 项。浏览器新增开关失败回退、附件 409 后显式重试、纯附件占位原文、记录候选删除及托管收件操作限制检查通过。首次删除测试误选背景同名按钮，改为浮层内定位后通过；未改动用户记录。

- 最终 `assembleRelease`、`lintRelease`、`assembleDebugAndroidTest` 通过，日志 `build/0.15.23-final-build.log`。未安装手机或执行 instrumentation。
- Edge 浏览器核心、功能对齐及新增功能检查通过，使用模拟手机 API；覆盖日／周／月与手机自然日、筛选和批量写入、来源分页、附件失败重试、冲突版本预览与明确删除确认。深浅主题、移动宽度实际截图已检查；5 种主色在深浅主题的主按钮对比均至少 4.5。
- 真实 SQLite 快照 16 项、类型与持久化 66 项、共享审核状态 45 项、合并 99 项、批量写入 19 项通过；真实 HTTP 同步交换 16 项通过。
- 新生产适配器检查通过：来源 API 20 项、同步 API 17 项、附件限长流式上传及回滚；同步锁 3 个 latch 场景覆盖慢网络期间本地访问、编辑保留和恢复使旧结果失效。适配器检查不代替真实 Android Provider 或 WebDAV 多机验证。
- 截图与对比数据：`build/lan-feature-checks/`；浏览器核心与日历证据：`build/lan-web-checks/`、`build/lan-parity-checks/`。源码恢复副本：`build/backups/20261018-project-hardening/`。

## 0.15.22 — 2026-10-17

- 同步空状态沿用 14sp 文本，切为主题次要色；左右 16dp、上 12dp、下 16dp 的真实内容留白，填满卡片内容宽度。不改变按钮、同步逻辑或其他状态。
- release 构建与 lintRelease 通过，USB 覆盖安装 184 成功。导航复查未到达目标页，后续设备断开；尚无修复后实际截图，不标为原生视觉通过。没有再次执行真实同步、改变账号或处理冲突。
- 构建日志 `build/0.15.22-build.log`，源码备份 `build/backups/20261017-sync-empty-state/`。旧库标准备份快照问题仍保留在交接页，未在本次 UI 修复中处理。
