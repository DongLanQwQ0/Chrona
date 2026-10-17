> 历史交接快照。旧方案、旧验证限制与旧待办仅适用于记录时的版本。

# 上一轮交付：0.14.54 / 161

共用按压遮罩改ControlHighlightMask，跟随控件自身及最近父层亚克力圆角；选择栏保留1.5倍高光与1.1倍板，motionHost预留长按包络，中间父层不裁切。筛选按实际measureText加padding分配宽度，14sp单行、44dp高，不再等宽或强行换行。高光follow改连续900/60弹簧，普通移动720/40改180/20以延长两倍；放大时间乘数3，缩小4.5。跟随位移时trackingHeight平滑到.95，跟随到位恢复1；边缘.6槽距离内渐增阻力，保留越界回弹。

UiStyle的亚克力观察器同时监听globalLayout，重录可见表面的壁纸采样，不重建卡片树；修复待办展开/折叠后下方卡片移动但模糊纹理留在旧位置。SectionHistory仅在真实页面提交时记录最多10条，返回请求原因贯穿异步加载/排队；返回成功才pop，多选优先，旋转保存恢复。

原子岛发现vivo实际通知tag为VIVO_SUPERX_TAG，同时operation被剥离。active识别实际tag，end取消实际tag/id且不重发一个可能复活通知的未标记结束通知；动作key从immutable PendingIntent data URI读取，避免OEM剥离extras。原生系统卡片实际展开并点勾，临时本地待办completed=true、通知移除通过，测试创建项在finally清理，未完成原有用户待办。不能使用不在数据库中的旧synthetic probe判定真实完成（自动refresh会移走其槽位）。

集中离线检查：Dock141、Press41、筛选12、切换38、历史27、vivo108、通知78项通过。release/lintRelease通过。真机检查了筛选文字单行、来源首项长按放大未裁切、待办展开后背景位置与日程→收件箱→返回日程。未完成窄屏/大字体/全部控件逐一真机覆盖；用户随后要求停止手机调试，已停止，后续只离线发布。证据仅在build/，不提交私人设备画面。备份build/backups/20261016-highlight-containment及子任务记录。

# 上一轮交付：0.14.53 / 160

横向本周日历小组件的标题改wrap_content，与图标整体居中；横竖版关闭includeFontPadding。首页系统日程CheckBox保留原生勾选语义，自绘24dp图标至控件正中心，checked=主题色+淡底、unchecked=muted；tooltip/stateDescription同步，权限未授予先恢复关闭。

WeekTimelineScale压缩范围改00–08，正反坐标、绘制标签与边界测试同步。DockSpring直接follow手指位置，保留速度伸展/松手惯性；选中位置弹簧改720/40，页面settle不再把高光目标限制在页面动画的中间值。短按releaseWithPulse至少到1.16再缩回，长按1.5；动画关闭和取消不播放脉冲。128项选择栏、17320坐标断言和周手势/小组件契约通过。

原子岛勾按钮仍未解决。真机短暂连接时读取了当前界面，尚未完成一次勾选及对应日志采集就断线；用户要求先发布，其后恢复调试再处理。不得把旧离线绑定检查当成本次点击已恢复的证据。不提交私人设备界面/原始固件。备份build/backups/20261016-icons-selection。

# 上一轮交付：0.14.52 / 159

最后两分钟的缓存更新覆盖card/island/baseInfos/shortInfos/capsule，沿用串行WORKER，不逐秒查数据库；不再按PowerManager.isInteractive跳过，屏幕唤醒立即探测。105项通知载荷检查含连续119→115秒、非交互状态及各表面一致；服务检查含连续一秒重排程和亮屏立即刷新。无唤醒锁，系统深度休眠仍受系统调度约束。

WeekTimelineScale在整周01–08无非全天日程时压缩，来源缺失/截断保留原轴；预计算各天block，所有绘制与捏合使用同一正反映射。坐标17320断言、卡片/缩放642项、周手势及按压40项通过。首页待办各子组只传递pressed状态，外层卡片1.05倍并动态预留上下margin。日视图、导出图片不压缩。

按用户要求不连接手机，真机秒刷显示与新UI未复验。源码及元数据备份build/backups/20261016-countdown-week-home；新增检查已注册集中回归。

# 上一轮交付：0.14.51 / 158

AI 提取提示词升级为 schedule-v7：TIME_RULES 管理口语时间，DECISION_RULES 明确类别、起始/截止、准点提醒、角色及日期校验职责。猜测时间保持level2待确认，完全明确的事项沿用自动写入；转换器为只有钟点的模型结果保留最近一次的存疑草稿。完整约定及真实输出观察见 docs/ai-time-extraction.md。保留用户显式的思考强度，不调整供应商参数。请求契约、59项时间转换、16项自动写入检查及release/lintRelease通过，安装包build/distribution/Chrona-0.14.51.apk。

只读查看了手机既有模型输出，发现裸钟点提醒反复比较类别和时间字段，考试通知手算星期并预写JSON；不提交私人输出、截图或设备信息。此次按用户要求仅离线构建与GitHub发布，不连接手机、不重提真实日程；没有新版本线上耗时改善测量。源码及元数据备份在build/backups/20261015-colloquial-time、20261015-reasoning、20261015-ai-release。

# 上一轮交付：0.14.50 / 157

本轮新增确认后角标的原位同步、首页系统日程开关移至标题右侧、周边缘切换/捏合缩放及可选导出索引。角标统计仍有未确认项的收件记录数，多候选记录只有全部确认后才移除。周内滚动到边缘后，可在同一次按住中继续外拖切周，仅额外距离计阈值；缩放逐帧合并重排现有视图，不重查数据库。导出索引默认关闭并记住选择。

角标 15 项、日程刷新 374 项、选择栏 126 项、周手势、卡片/缩放 642 项和导出组合 7 项通过；release、AndroidTest、lintRelease 构建通过。安装包 build/distribution/Chrona-0.14.50.apk 已在用户重新提供端口并要求重推后覆盖安装成功，实际版本 157 / 0.14.50。week_image 原生图片及手势 fixture 通过：仅周表 2240×2790，附索引 2240×3492，两图预览已查看；原生 ScaleGestureDetector 累计缩放 1.7143 且未切周，连续外拖上一周和右边缘下一周均通过。首次手势fixture因固定600px视口下的指距不足未触发缩放，已按本机正常屏宽改为1200px并复验；未改生产实现。证据 build/device-workflow-01449/week-image-01450.txt；整页缩放焦点和实际手感仍待人工检查。GitHub已发布v0.14.50，APK已核验为latest且资产uploaded。

# 上一轮已验收：0.14.49 / 156

本轮补齐首页待办分流、记录编辑搜索、完成后的系统提醒同步、稍后提醒生命周期及频道状态诊断。数据库仍为 11。安装包 build/distribution/Chrona-0.14.49.apk 已覆盖安装到 vivo V2527A。

筛选与任务类型复用“选择栏”；弹窗/详情通过 motionHost 容纳放大与惯性包络，选项禁用基线对齐，避免自动字号将末项和高光向下推离。不同长度选项按文字长度分配宽度，高光宽度与速度连续过渡。日期范围两侧箭头分别切换区间，中间打开日期选择；清除筛选以高度和透明度收起摘要，下方选择栏随布局回位。

完整离线 60/60、后续受影响检查、最终 release/AndroidTest/lintRelease 构建通过。真实 Android SQLite/HTTP 集成、日历快照缓存复用及快速反向交互通过。最终原生布局 27 组覆盖 2/3/5 项、三种屏宽与字体倍率，逐帧核对首尾长按、快甩及越界上限；320dp/2 倍字体的两张代表图已查看。布局证据属于局部组件，整页横屏/大字体与最新筛选摘要收起动画尚未人工复验，手机正在由用户使用；不宣称全应用零裁切或零掉帧。证据 build/device-workflow-01449/；源备份 build/backups/20261015-workflow-reliability/source-before.zip。旧进度记录如下。

# 剩余进度、多提醒及手动追加 · 2026-10-13 · 0.14.31 / 138

用户提供新ADB地址恢复测试，安装0.14.29成功，实际桌面展开图位于build/device-ui-audit/atomic-01429-expanded.png；用户明确确认交互已经完善，随后要求剩余时间进度条、新建第二个日程入口、进度图标与文字简化、最多3条原子岛。用户最后要求继续无人值守，并未限制仅离线。设备随后离线，同一地址连接超时、devices与mDNS均为空，最终整合包未覆盖安装，不再以中间0.14.30包视觉推断最终结果。

VivoAtomicNotification改模板2原生进度条，开始前以30分钟为总时长、开始后以finish-start为总时长，剩余比例100→0。模板要求两个节点Icon，使用共享透明1px图像消除用户指出的黑色/重叠装饰；进度色为主题primary，轨道为其淡色。大卡仅剩余短文案与时间范围，不重复地点来源；完整信息仍在通知详情和日程详情。三通知用原ID+i稳定槽位，未确认/全天/隐藏/结束排除，每个key独立延期、旧动作拒绝；本地和系统各方向至多读3项，延期key先排除以填补空位。原子岛可见容量最终由系统决定，保存固件证实同scene不同ID独立，但没有三条并发真机显示证据。普通设备保持一条。

TaskDetail新建按钮在删除左侧，使用已有compactActionButton；appendCandidate事务验证任务与输入并按MAX(position)+1追加，保留已有ID及calendar_event_id/合并关联。新ID驱动选中新页，render/snapshotDrafts保留草稿。真实SQLite80项和详情草稿检查通过。完整51/51通过，日志build/atomic-three-01430-checks.log；46项协议、35项通知工厂、592项日程特征、22项生产refresh状态机均有回归。最终版本号从中间测试137提升138，确保已安装0.14.30用户也能更新。

新增DeviceUiChecks progress_cards用三个真实通知槽临时展示生产工厂卡片，串行Reminder WORKER防定时更新干扰；不写任务数据，finally结束测试卡并同步恢复真实提醒，created/updated=3和afterEnd=0为断言。最终手机离线未执行此phase；下轮需安装正式APK及匹配test APK，先测此phase和实际进度UI，再测日历帧率/残影/年视图速度及手动新增草稿。备份build/backups/20261013-atomic-remaining-progress、20261013-detail-add-schedule及three-agenda-20261013-184835。不提交用户AGENTS.md、角色toml、私有手机截图/固件或实际设备地址。

# 原子岛展示调整 · 2026-10-13 · 0.14.29 / 136

用户18:12截图证明正式原子通知在锁屏和岛上实际显示，指出大卡重复时间和状态，并要求桌面首次点击展开、再次点击进应用。当前协议islandClick=1且未声明island.template；依据实时读取官方doc896，0为出卡、1为跳转，无template不支持大卡。改为islandClick=0、island.template=1，保留island.clickResp供卡片空白区域详情跳转，删除rightInfo.clickResp避免绕过展开。协议说明https://dev.vivo.com.cn/documentCenter/doc/896。

baseInfos保留标题与一次时间范围；infos.describe显示地点，缺地点时显示来源，来源缺失时使用日程提醒；coreInfo只显示即将开始/进行中，不重复开始时间。同日范围仅首端有日期，跨日保留两端日期。原始日程标题未改写。44项适配器回归及25项生产提醒工厂回归通过，release/androidTest/lint成功；无手机连接，首次展开及最终视觉仍未真机复验。备份build/backups/20261013-atomic-presentation。用户AGENTS.md及角色toml不纳入提交。

# 最终离线收尾 · 2026-10-13 · 0.14.28 / 135

本段覆盖下方早期0.14.27实验结论。用户已离开，明确仅允许离线开发，后续不得操作或读取手机。0.14.27仅为中间测试包；最终0.14.28版本号135用于让已安装中间包也能收到更新。

日历切换改为保留旧body的原挂载并创建新body，消除整批子控件解绑/重挂。实际六次左右Dock滑动的最大帧耗时为14.89、27.95、26.53、22.90、22.27、24.69ms，六次均无超过32ms的帧，仍有少量超过本机帧截止时间的帧，不能宣称零掉帧。证据build/device-ui-audit/calendar-page-identity-metrics.txt。年视图队列仍有2ms预算，上限从4增为16是最后离线改动，尚未真机测量加载时间；相同模式和主题色的不可变色板缓存通过独立验证。

经用户明确授权持久注册仅包含Chrona的CHRONA_AGENDA场景，成功后保留。补齐本机强制校验的shortInfos三字段后，普通应用身份发送的测试通知实际进入原子岛；截图atomic-short-status.png与atomic-short-template-device.txt为私有证据，不发布用户页面或厂商框架文件。生产APK不含注册和提权代码。最终生产提醒使用原生BigText样式、应用图标并保留三个动作；这些最后修改、动作实际交互、完整结束生命周期和重启后的场景保留尚未真机复验。

完整离线50/50通过；debug、release、androidTest及lint构建成功，日志final-01428-checks.log和final-01428-build.log。发布附件仅正式APK与包含配置脚本/测试APK/说明的ZIP，避免自动更新误选测试APK。用户AGENTS.md及四个角色toml不得混入提交。工具说明docs/vivo-atomic-setup.md。目标仍active，真机待验项目不能以离线检查替代。

# 本轮真机补充 · 2026-10-13 · 0.14.27 开发包，尚未发布

用户重新授权手机自动测试并要求重新安装；应用包和instrumentation包均重新安装Success。新DeviceUiChecks测量真实Window.FrameMetrics，10次模式选择及4次松手在Dock外的滑动，保留原模式且不写日程。恢复测试后抓到切换停住：loading=false、transition=true、steps=null、preDraw仍存在、resumed=true、layout=false。根因是隐藏body.invalidate不再驱动绘制；prepareBodySlide改使可见bodyViewport.postInvalidateOnAnimation驱动预绘制，等待布局/滚动恢复时也请求下一帧。完成14项真机测试，未再发生25秒不收敛；calendar_slide_check 221项通过。原文件备份build/backups/20261013-device-goal-verification。

私有真机证据build/device-ui-audit/calendar-0.14.25-baseline-2.txt及calendar-0.14.27-redraw.txt。新四次滑动maxMs为20.899/24.392/45.119/27.918，p95为12.311/11.595/13.766/12.123；旧四次为68.497/24.879/98.355/25.668。新仍有一次45ms长帧和年转月37.660ms，不能宣称零掉帧；最后测量时间附近运行过桌面离线编译检查，后续复测应避免竞争。原年度同步构建变逐帧后368事件视图完成需约2.9秒，应关注延迟体验。用户手机已经交还，后续操作需避免抢占用户。

vivo生产适配器真机创建/更新/结束成功，sceneEnabled=false，ownNotificationActive=true只证明普通通知存在。补齐capsule与island同时发送及官方反射场景查询；离线32项通过。doc894公开正文明确正式路径需要上架及邮件申请权限；未代用户发邮件。尚未区分手机场景开关关闭和未准入；目标仍active，不能标完整接入完成。app/build.gradle暂为0.14.27/134，未提交发布；用户AGENTS.md与角色toml不可混入提交。

# 本轮补充 · 2026-10-13 · 0.14.26

用户确认日程大部分卡顿已解决，剩日历，并要求日程/收件箱统一进入、退出及切换；随后明确要求仅vivo原子岛实现。Calendar原队列遗漏dayList及timeline卡片，monthGrid还以七天整行为一个任务；现改逐日期/逐事件入队，预算2ms且每回调至多4项（单项可超过预算），全天事件也入队。时间轴提前按统一12/14sp度量保留边缘余量，缓存小时标签和网格Palette；nowLine最终入队，使其在所有事件上方。初始无动画构建仍同步，小月日选择仍保留原月网格，未做虚拟列表或改变Dock惯性。

UiMotion新增resetList/exitList/enterList，列表容器退出180ms、进入240ms，位移±8dp，共享SETTLE；不截图、不复制旧卡片。日程worker快照完成后才退出旧列表，首条构建后开始进入，后续逐帧；收件箱同套出口/入口，原查询方式保持。generation、results identity、切页/暂停取消及dirty缓存保护防止旧动画重建或留下透明缓存。删旧的animateResultRows/animateResultCard。

vivo官方doc896通过页面公开webapi/doc/info?id=896取得正文，验证island左右区具体字段、模板1infos必填图标及METTING。新VivoAtomicNotification仅MANUFACTURER=vivo生成大卡/左右岛Bundle；原AgendaReminder通知ID/渠道/RemoteViews/动作保留，operation012管理创建/更新/结束，结束异常仍cancel。已存在通知才更新，切换事件先结束旧通知。没有VPush/保活/后台网络，授权和机型仍决定是否出岛；不能保证自用免授权。调研子代理持续容量不足，主代理补查官方正文。

完整47/47，build/calendar-list-vivo-checks.log。Calendar工厂631项及立即创建负例复现；scheduler15项、日程controller306项、原生motion36项、vivo26项。最终nowLine顺序与active异常处理后重跑相关检查；debug/release/lint通过，0 errors /84 warnings，build/calendar-list-vivo-final-build.log。备份build/backups/20261013-calendar-list-motion。0.14.26 /133，APK 1,995,626字节，SHA256 521ad70df9a41002bfa1b4219ecb2a122b2dcb6c79fee9bff3ed93791ef5849d，原签名保持。无ADB，日历FPS、统一列表视觉与vivo出岛均未真机复验。
# 本轮补充 · 2026-10-13 · 0.14.25

录屏逐原始帧定位：2.671756至2.721711秒高光中心约482.5px不动，2.729733秒跳至344.5px（相邻约8ms跳138px），同时55条变31条。用户进一步确认底部Dock与收件箱正常，日程减少数量后正常。源码松手select回调同步queryScheduleIds/querySchedulePage并创建全页卡片，与停顿后spring一次补算elapsed一致。录屏不是FrameMetrics，SQL/布局/玻璃的各自耗时仍未真机测量。

修复仅限日程刷新：复用startupReader和DashboardSectionData读取事务快照；request token、目标results身份与Activity状态阻止过期回调；当前列表留到快照到达；每动画回调最多生成一个日程条目（日程日期标题随同条目），分页footer最后。系统日程同样逐条生成，已加载系统数据筛选仍在UI执行。连续筛选、切离、pause/destroy和行创建失败取消队列并标dirty；完成后用worker revision更新缓存、恢复当前滚动位置。初次整页准备与收件箱/底部导航逻辑不改，DockSpring/惯性未改。

完整45/45通过，日志build/schedule-refresh-checks.log。新增生产控制器/renderer回归最终245项（末次新增行创建异常取消及重试通过）；worker/frame替身验证调度，不等于Android FPS。debug/release/lint通过，0 errors /84 warnings，build/schedule-refresh-build.log。备份build/backups/20261013-schedule-frame-refresh。0.14.25 /132，APK 1,992,758字节，SHA256 81089865327c3941da84f74f6d71e114286eddf18bd934d18e89026770ee2c22，保留原签名。设备离线，尚未真机复验。

用户同时要求子代理调研仅vivo原子岛。子代理多次因服务模型容量不足未执行，主代理核验阿里云官方指南，记录docs/vivo-atomic-notification-research.md。本地通知路径得到证实，但接入授权仍是文档要求，自用免授权没有证据；vivo参数正文动态页不可读，dismissWhenKill/island完整契约未证实。本版仅调研，没有厂商SDK/extras/保活或对外邮件。
# 本轮补充 · 2026-10-13 · 0.14.24

完整44项通过：初跑42/44，学期持久化原子移动和同步回环连接受沙箱限制；正常权限单独复验两项通过。日志build/choice-output-checks.log、choice-output-terms-recheck.log、choice-output-sync-recheck.log。

用户新截图：日程类型首行稳定放大时左右边框被Scroll裁剪；模型完整输出长文字覆盖标题和底部。共享choiceDialog确实没有直接choices内容padding，现按sheetWidth-40dp计算edgeSpace并给上下3dp，Scroll高度加6dp，保护首尾行。ModelOutput和choiceDialog创建Scroll时显式protectViewport，不依赖全局注册顺序。未修改按压倍率或监听。

设备离线前安装的是0.14.23中间包，最终clipBounds未覆盖安装；不能仅凭截图/版本号确认当前手机二进制。子代理核对现代AOSP Skia RenderNode与最终0.14.23 DEX，固定Rect不加scroll偏移正确；最终APK包含保护调用，应用创建/恢复回调和globalLayout覆盖ModelOutput。该解释仍未在设备验证。本版以0.14.24 /131区分旧中间包。

构建debug/release/lint通过，0 errors /84 warnings；日志build/choice-output-build.log。备份build/backups/20261013-choice-output。最终APK 1,989,530字节，SHA256 ea4fa7ae6e599030c057c9ef01b584e351a82f2bc35a27ac32bc0351c1fb4f02，原签名保持。设备未连接，不宣称最终视觉/帧率通过。
# 本轮补充 · 2026-10-13 · 0.14.23（离线审查完成）

用户连接 V2527A 后要求 UI 检测，强调持续长按，并现场按住首页“下一件事”指出左边框截断。真机原版本0.14.22：普通首页时间线按压完整；日历AGENDADock最右项长按高光越过屏幕；AGENDA单条卡片在Scroll视口边界被截；用户现场carousel稳定长按截图确实左边框缺一段。证据保存在build/device-ui-audit/，不发布用户页面截图。

关键修复：carousel余量由strip迁入直接cards内容容器，保持负8dp左margin和正文对齐；card放大不再超出Scroll对子内容的边界。共享PressFeedback.edgeSpace按1.05峰值计算像素余量。Calendar列表左右内容留白，Dock居中限280dp/窗口70%；单项dayList采用group，让玻璃边框与动作子项一同缩放。Calendar日/周grid新增外包留白，保留绘图坐标，负margin补偿日期/网格横向对齐，初始scroll加verticalEdge。Timetable学年chip内容6dp余量、课程grid外包留白；备注editor外包并按实际高度动态设置上下余量。

进一步真机检查发现：普通祖先clipChildren=false时Scroll自身clipToBounds失效，零paddingScroll的clipToPadding也不能保证自身窗口，日视图网格会盖到日期栏上。最终PressFeedback.protectViewport为所有Scroll/HScroll/SwipePager设置自身clipBounds，尺寸变化才更新；CalendarbodyViewport同样显式保护。最后这一修改构建通过，但ADB恰好离线，尚未覆盖安装/真机复验。用户明确要求停止等待ADB、继续离线审查。设备地址不存入项目文档。

真机已通过2秒持续按压、1秒截图的案例：title-long-after、timetable-term-long-after、timetable-course-long-after、calendar-long-held-after（右端高光完整）、upcoming-long-final（用户指出的首卡整段左边框完整）、settings-long-final、plus-long-final。日历单项表面和最后clipBounds尚需最终包复验；备注长编辑框及边缘日/周事件也需实际检查。只代表已测控件，不宣称整个App无裁剪或已量测FPS。手机当前是0.14.23中间测试包；最终0.14.23 /130已构建，未覆盖安装；按用户授权发布。

离线43/43通过：PressFeedback40项，实际carousel工厂108项，9/9错误padding变体被direct cards boundary clipping拒绝。debug/release/lint通过，0 errors /84 warnings。日志build/device-ui-audit/full-checks.log、final-build.log；备份build/backups/20261013-device-long-press。最终APK build/distributions/0.14.23/Chrona-0.14.23.apk，1,989,554字节，SHA256 278c5dec0d32ce3c3e9cb859d2437b75e53dd38f36bfba56652a40ba8db08dd7，原安装签名保持。子代理独立离线复审通过：自身clipBounds忽略滚动偏移，日期与网格横向坐标一致，备注动态余量布局稳定。用户AGENTS.md/toml未动。最终包真机边界与帧率复验仍未完成，发布说明明确这一限制。

# 本轮补充 · 2026-10-13 · 0.14.22

用户用五张截图指出普遍放大裁切，补充设置无明显放大、“+”应更明显、完整输出页按钮无间隔。上一版 PressFeedback 只装缩放，遗漏父级绘制边界。现在每次安装/布局复查都放开普通祖先的 clipChildren/clipToPadding，遇 ScrollView、HorizontalScrollView、SwipePagerLayout 或 clipToOutline 视口即停止；不取消滚动/切页/媒体边界。保留 Float 自定义倍率，NaN 表示动作子项只驱动父板、不重复缩放。设置底板 addStatesFromChildren + 1.10，子图标和排序保留点击但无独立缩放；记录按钮 1.20。

carousel 首项左侧需 6dp 溢出余量：strip 左8dp、上下6dp padding及左margin -8dp，保持正文对齐，cards关闭裁剪。PressFeedback 覆盖标题与时间线中间层。ModelOutput 三按钮等权，前两项末端8dp间距，行上下8dp padding。课表 MIN_COLUMN_WIDTH 56，运行时根据四个中文字实际测宽+12dp间隙/内边距增大，已有横向滚动承接超出宽度；课程 radius6、outline和minimumMix54。UiStyle.pressable 保留已有 RippleDrawable，不覆写按具体表面半径构建的mask。

子代理只读截图审查与集成审查通过，发现的波纹圆角问题已修正。PressFeedback+生产 pressable 27项，课表20场景，完整42/42；debug/release/lint通过，0 errors / 84 warnings。证据 build/press-overflow-full-checks.log、build/press-overflow-final-build.log。备份 build/backups/20261013-press-overflow、build/backups/20261013-output-spacing。0.14.22 /129，1,989,238字节，SHA256 b67796280219ec3bd94319ada8ebbe3dc7cc2054e68741491973da69a7a7c0e2，原签名保持。ADB无设备，实际截图复验/按压触控仍未实测。

# 本轮补充 · 2026-10-13 · 0.14.21

用户报告日历松手约一帧残影、要求移除首页课表重复入口、更新引导、所有交互控件按下放大，以及任务详情菜单横线分隔。本次日历页面不再 setAlpha：INVISIBLE 准备新树，预绘制时检查 layoutRequested，滚动恢复仅一次且恢复后仍有布局请求则继续等待；再定位新旧两页并 VISIBLE 开始共享进度的平移，旧页 INVISIBLE 后移除。并无实际设备逐帧证据能确认残影根因或最终消失；旧子树 reparent 仍保留，后续有设备证据再评估。

PressFeedback 统一 StateListAnimator：按下 1.05、90ms，松手 1、160ms；不设置触摸/点击监听。Activity decor、Dialog 内容与原生日期弹窗接入；全局布局覆盖新增控件，弱键与 detach 清理。Dock 子树保持自身动效，滚动容器不整体放大。UiStyle.button 由缩小改放大，pressable 接入同控制器。菜单删逐项 pill，行间插 1dp 横线，保留主题色与删除强调色。Dashboard 删除 courses 和专用 sources 行；Guide version 3 说明下划线标题三个入口。

子代理独立源码集成审查通过。日历生产方法/布局动画替身 220 项，PressFeedback 生产控制器/View 动画替身 14 项，完整 42/42；debug/release/lint 通过，0 errors / 84 warnings。日志 build/calendar-feedback-full-checks.log、build/calendar-feedback-final-build.log。备份 build/backups/20261013-calendar-first-frame、build/backups/20261013-home-guide。0.14.21 / 128，APK 1,988,834 字节，SHA256 b872b7714790fff208f6fbd23e7c64630ab795156c1a087b633bba95aea42416；签名保持一致。真机残影、FPS 和全控件按压视觉未实测。

# 本轮补充 · 2026-10-13 · 0.14.20

用户截图显示日程与收件箱筛选 Dock 放大后被行容器截断，横向手势被页面切换抢走。初始及缓存页面的 content LinearLayout 和 contentHost FrameLayout 统一关闭中间层 clipChildren/clipToPadding，保留 ScrollView 页面视口裁剪；filterDock 工厂将共享控件加入 strips，由 SwipePagerLayout 优先保护。遗漏登记会使 pager 的 MOVE 主动解除 requestDisallowInterceptTouchEvent，进而取消 Dock 手势。

生产 filterDock、溢出设置、SwipePagerLayout 和 Dock 共同纳入测试：111 项通过，包含嵌套事件链、栏外松手只提交一次、栏外横滑仍切页、移除登记复现父级抢夺与取消。完整 41/41，debug/release/lint 通过，0 errors / 84 warnings。日志 build/dock-container-full-checks.log、build/dock-container-final-build.log。备份 build/backups/20261013-dock-container-ownership。0.14.20 / 127，APK 1,988,490 字节，SHA256 b9b7f97771043ea9c4921c7e490a0fd6a8265370add560b6eba2dfeb4905785c；保留安装签名。ADB 无设备，真实 Android 裁剪效果和触控未实测。

# 本轮补充 · 2026-10-13 · 0.14.19

用户截图显示共享 Dock 高光下沉和横向错位。根因是 updateHighlight 已算出 Dock 坐标，而 FrameLayout 又按 padding 布置 highlightView；之前直接将 Dock 坐标赋给 translation，重复叠加了实际布局原点。改为 translationX/Y = 目标 Dock 坐标 - highlightView.getLeft()/getTop()。

原测试替身 setPadding 是空方法且 FrameLayout 不布置高光原点，漏掉回归。替身现在保存四侧 padding 并布置所有子视图；测量使用 layout origin + translation + scaled size。新增底部导航、四/五项筛选、竖向导航、padding 变化及父重排案例。生产 Dock 104 项和完整 41/41 通过；用修改前源文件执行同一测试得到明确失败：期望中心 149，实际 157，原缺陷被拒绝。证据 build/dock-padding-original-rejection.log、build/dock-padding-full-checks.log。

debug/release/lint 通过；备份 build/backups/20261013-dock-padding-regression。0.14.19 / 126。ADB 无设备，手机最终视觉和帧率仍未实测。

# 本轮补充 · 2026-10-13 · 0.14.18

用户要求日程与收件箱筛选栏共享高光 Dock、内端在触及锚点前减速，并报告日历松手残影/跳帧。Dashboard 的 filterChips 和缓存 SectionPage 使用 DockNavigationLayout；filterDock 工厂统一四/五按钮与角落计数，删除旧 chipRow/横向滚动布局及逐按钮亚克力选中背景，保留原有筛选、排序、分页回调。日程与收件箱使用一致的 48dp 栏及尾部筛选按钮间距。

DockSpring.softUpperBoundary 以 tanh 在半个标准宽度的接近区平滑减速，去掉内端硬 Math.min/max 截断，网页同步。highlightView 基准宽高写入 LayoutParams，避免父布局将基准重置为 1px。CalendarOverview 去除旧/新内容的 alpha 交叉淡化与 withLayer 双动画；等新网格完整构建，注册一次性 preDraw，完整测量后直接恢复实际滚动偏移，再用一个 ValueAnimator 与 CalendarSlideMotion 同步平移两页，保持文字不叠加。取消清理监听与动画，过期 generation 不启动。

完整 41/41；原生 Dock 与实际 filterDock 工厂 92 项（Android 队列/布局替身），日历过渡 212 项（生产方法和测量/动画替身），真实 Edge 动效/交互通过。debug/release/lint 通过。备份 build/backups/20261013-filter-dock-motion。0.14.18 / 125。Android 实际残影、FPS、触控手感仍未真机实测；ADB 无设备。

# 本轮补充 · 2026-10-13 · 0.14.17

用户报告横屏首页仍旧样式、日历 Dock 掉帧，并要求严格统一控件框架。DockNavigationLayout 改为共享 FrameLayout 组合：玻璃底板、独立 highlightView、items LinearLayout。高光通过独立渲染节点的 translation/scale 更新，不再在 dispatchDraw 中绘制或每帧 invalidate 整栏；布局只在项目尺寸改变时更新。主题变化在选中语义更新时刷新高光色。显式 itemCount/itemAt/addItem/clearItems API 隔离内部结构。

Dashboard 只保留 navigation 一个共享实例，横屏使用同一控件的 VERTICAL 布局、居中浮动玻璃栏；主轴位置、Y 速度、惯性预测、越界上限、缩放和栏外松手复用。移除旧宽屏 pill 样式。CalendarOverview 移除 tabMotion 和额外 tab 按压动画，复用同一控制器。月、年网格在旧内容仍显示时按帧构建，4ms 工作预算，每行七天，年视图先分批建立月份容器；完成后开始内容淡化。取消清理 frame callback/queue/done 并清空 renderedKey，避免恢复时把未完成内容当完整缓存。

完整 40/40、原生 Dock 83 项、日历构建调度 12 项、Dashboard 生产切页控制器 12 项通过；debug/release/lint 通过。备份 build/backups/20261013-shared-dock。0.14.17 / 124。ADB 无设备，Android 横屏视觉、真实帧率及手感未实测。

# 本轮补充 · 2026-10-12 · 0.14.16

用户追加最大伸出距离及渐进阻力、增大普通滑行阻力、放大动画延长至 1.5 倍，并明确要求提前计算落点、松手立即切页。DockSpring 新增 predictLanding，克隆当前位置后执行同一生产 step 预演自由滑行和边缘回弹，实际速度保持 1∶1；DockNavigationLayout 立即 select.accept，beginPageSettle 在惯性中不接管，惯性完成仅吸附，不二次选页。CalendarOverview 移除等待 Dock 稳定后的构建门槛。网页复用 coastDockStep 预测并立即路由。

普通 COAST_DAMPING 5→7.5。放大阶段时间乘 1.5（刚度 900→400，阻尼 50→33.333），释放缩回仍 900/50。外端 tanh 映射上限为标准高光宽度 0.75，内端半宽锚定保留。原生 77 项、完整 39/39、真实 Edge 动效与完整交互、debug/release/lint 均通过。备份 build/backups/20261012-dock-resistance。0.14.16 / 123；ADB 无设备，Android 实际手感和帧率未验证。

# 本轮补充 · 2026-10-12 · 0.14.15

用户真机报告上一版模糊和日历掉帧未解决，并明确要求惯性决定最终落点、主导航收窄居中、选中主题色，以及标准宽度的一半越界后内端停住、外端继续拉伸。取消 Dashboard 整页 withLayer 并恢复每帧重采样；GlassBackdropView 改用缓存 CLAMP BitmapShader 与移动坐标矩阵，避免离屏采样变实色。CalendarOverview 不再同步截图，保留旧原生子树过渡，合并过渡内请求并等待 Dock 运动结束后构建视图。

DockSpring 原生与网页实现自由指数阻尼阶段，边界外弹簧回拉，随后按实际减速落点选择并吸附。保持速度 1∶1，不再以松手位置决定项目。绘制高光内端到半个标准宽度越界位置后固定，外端允许继续延伸；主 Dock 左/右留白 36/104dp，选中按钮主题色。

完整离线 39/39、原生 Dock 70、生产采样方法 10、实际 Edge 动效及完整交互通过；debug/release/lint 成功，0 errors / 84 warnings。备份 build/backups/20261012-inertial-landing。0.14.15 / 122，SHA256 35f64478361722d90db1d018df4039b6f7a4212d6aeea3324574357f3cf84ea8。ADB 无设备，Android 真机 FPS、动态模糊及实际拉伸手感未验证，不能声称手机掉帧已经实测消失。

# 本轮补充 · 2026-10-12 · 0.14.14

用户报告松手切页掉帧、首页 Dock 底部裁切、放大响应慢，并要求高光半透明、松手速度 1∶1。首次 Dock 切页通过 DashboardSectionData 后台事务读取有界 Home/Inbox/Schedule 数据，主线程仅创建 View。cached buildAdjacentSection 不再检查数据库或刷新；页面完成后的版本/待处理刷新延至 Dock 和页面都静止，poll 和系统来源刷新也避开动效。相同注意数时 drawNavigation 保留按钮/包装/徽章并原位改选中语义。读失败保留旧页，pending 最后请求与 pause generation 隔离过期回调。

松手页动画 withLayer 暂存页面，移除每帧模糊卡片采样，完成恢复采样；正文拖动仍保留实时采样。首页 stage 禁裁剪并增加 12dp 按压余量。DockSpring 仅缩放提高刚度/阻尼（900/50），保留位置/宽度弹性；native alpha 82/255、web 32%。VelocityTracker 使用屏幕坐标样本，实际速度除以屏幕槽位间距后 1∶1 传给 spring；Dock 发起切页避免 beginBodyDrag 清零。网页最近 100ms 屏幕样本线性拟合，同样无衰减转换，选择目标不因速度跳到其他页。

备份 build/backups/20261012-dock-release-performance；完整离线 38/38，速度/快缩放补充 Dock 63 项、生产后台与切页控制器 12 项、实际 Edge 动效验证通过。0.14.14 / 121，数据库 v10；真实 Android 帧率、裁切与速度手感尚未验证。

# 本轮补充 · 2026-10-12 · 0.14.13

用户要求 Chrona 日程同样有边框，以及全部 Dock 栏外释放确认、惯性、宽度弹性和按压缩放。CalendarOverviewActivity.eventSurface 对本地/系统统一 outline=true。原生 DockNavigationLayout 使用单一帧循环和 DockSpring（位置/高光缩放/宽度/整栏缩放阻尼弹簧），重定向保留速度；放大目标 1.5/1.1，释放回 1。水平拖动不再以内栏 UP 限制提交，ACTION_CANCEL 与多指仍取消，普通 child queued click 顺序保留。日历父级允许扩展绘制；主导航原宿主已允许。

网页 Dock 使用 Pointer Capture，栏外释放以横向最近项目确定路由，阻止重复鼠标 click，键盘点击保留；同样采用单一 RAF 阻尼弹簧，支持宽度与缩放、减少动态效果、失焦/取消。Edge 实际指针与中间帧验收通过（含 requested scales 与外栏 UP），截图 build/lan-motion-checks/dock-{pressed,moving}-mobile.png。完整离线 37/37、原生 Dock 57 项通过；备份 build/backups/20261012-dock-spring。0.14.13 / 120，数据库 v10，Android 真机视觉/手感未验收。

# 本轮补充 · 2026-10-12 · 0.14.12

用户选择所属日历 CALENDAR_COLOR 并要求主题混色，同时要求搜索移到标题栏。CalendarStore.listInstances 追加颜色列，nullable Integer 经 CalendarOccurrence/AgendaRepository 传到 AgendaEntry；值相等与 hash 纳入颜色，保证源色变化更新。CalendarSourceColors 使用 65% 来源、35% 主题、不依赖平台的 RGB 混合，忽略源 alpha 输出不透明颜色；null 才视为缺失，0 黑色有效。

日历列表来源色点、周/日/全天 acrylic 卡片应用混色。UiStyle AcrylicSurfaceDrawable 保存原始 calendarColor，绘制时按当前主题计算强调颜色与底色，关闭 acrylic 的不透明底板也保留来源着色；文字保持主题文字色。搜索替代原「日历」标题，返回/新建保留。备份 build/backups/20261012-calendar-source-color；0.14.12 / 119，数据库 v10；验证见 docs/validation-history.md，真机观感尚未验收。

# 本轮补充 · 2026-10-12 · 0.14.11

用户要求月视图选择 day 不重建月历。monthDates 与 monthDetails 独立保存，navigateDate 首先尝试 selectMonthDay：当前渲染同月时更新旧/新日期颜色、描边、语义和当天列表，保留月历、日期格与双轴滚动宿主，不调用 load/renderBody。重复点同一天无操作，今天/日期选择器复用；跨月仍正常导航。后台同上下文读取时允许局部选择，结果相同不会重建。备份 build/backups/20261012-month-day-selection；专项真实选择控制器检查保留宿主身份、无读取/无重建，127 项通过。0.14.11 / 118，数据库 v10；真机呈现仍未验收。

# 本轮补充 · 2026-10-12 · 0.14.10

截图反馈日历整体弱化。对比度修复限定 CalendarOverviewActivity：日历亚克力底板设混色下限（深色 70%、浅色 80%），保留背景模糊和全局外观设置；日期数量取消额外 alpha、增为 11sp/3dp 色点，非本月只在整格弱化一次。选中日期增加主题描边，已结束与系统事件标题保持正常文字对比度。

CalendarSwipe 单指方向判定，日历内容区 dispatch 在明确水平意图时给子元素发送 CANCEL，并在足够距离的抬手后 shift 日期。纵向滚动、未达阈值、多指、加载/转场期间不导航；嵌套周视图优先横向滚动，边缘新手势才切周。备份 build/backups/20261012-calendar-contrast-gestures；0.14.10 / 117，数据库 v10。逻辑与 Android 构建检查不能代替真机视觉和手势手感验收。

# 本轮补充 · 2026-10-12 · 0.14.9

先完成九项问题审查，记录在 docs/calendar-review-20261012.md。CalendarOverviewActivity 使用可取消任务与提供器信号，旧上下文隔离输入，内容相同跳过重建，手势/滚动/全天弹窗期间延后替换。十二窗口的双轴位置缓存与旋转状态保持位置；周视图初次聚焦选中列。收件箱日历页的系统来源改为直接可见开关，仍独立存储。

动画复用 UiStyle 的有限可见快照，稳定视口捕获当前合成画面；连续切换、暂停和焦点收敛及时释放。CalendarDayCounts 差分索引计算每日数量；边界计时更新列表状态，日期计数和短事件适配大字体，全天项最多显示三项再展开。备份 build/backups/20261012-calendar-review；0.14.9 / 116，数据库 v10。验证证据见 docs/validation-history.md；尚无 Android 真机视觉与触控验收。

# 本轮补充 · 2026-10-12 · 0.14.8

课表主体 horizontal 与空课表占位清零 elevation。CalendarOverviewActivity 顶部复用 DockNavigationLayout 连续高光和触摸跟随；独立页内 animator 管理实际高光位置，异步读取保留内容，日期/视图切换以淡入和方向位移呈现。月日期使用 acrylicChoice 底板，非本月减弱，今天描边、选中日强调，计数改为轻量色点数字，三级阈值为 2/5。系统减少动画、暂停和焦点收敛均处理；旧内容阻止操作且隐藏无障碍树，转场最多保留两层。

修改前备份 `build/backups/20261012-calendar-polish`。验证与分发记录见 docs/validation-history.md；版本 0.14.8 / 115，数据库 v10。真机视觉、动画手感与锁屏行为仍未验收。

# 本轮补充 · 2026-10-12 · 0.14.7

新增提前 30 分钟详细常驻通知及独立系统来源，支持详情、延后与多项隐藏；边界闹钟及周期只读任务覆盖后台，重启/时间/数据变化重算。首页、小组件、提醒和新日历页的来源各自保存。日程四预设分别持久排序；收件箱标题下划线打开年/月/周/日/日程日历。新设置与排序纳入完整备份。

schedule-v4 放宽模糊时间，模型直接建议并标高存疑待确认。完全明确且确定的项逐项自动写入，保留缺权限/失败/存疑草稿，通过详细通知报告；写入关联失败或取消进行补偿清理。独立事件重叠不降低确定性。无真实模型 Token 节省测量。

备份 `build/backups/20261012-agenda-features`；36/36 离线检查及 debug/release 构建/lint 已通过，数据库 v10、版本 0.14.7 / 114。ADB 无连接设备，未验收 Android 真机呈现、锁屏送达、系统权限行为及覆盖安装。详见 docs/agenda-features-plan.md、docs/ai-pipeline.md、docs/validation-history.md。

# 本轮补充 · 2026-10-05 · 0.14.6

本轮完成原生与 LAN 页内动效优化：UiMotion/ScrollRevealObserver 缓存可见卡片并合帧，主动呈现与 settleScroll 静默重绘分开；设置页 show 的 animatePresentation 参数由调用者明确区分首次与恢复。输入与无障碍焦点保护覆盖动画过程。详情 SectionMotion 高度/透明度可反转，Dock 继续单帧跟手，课表主动选学期只过渡课程主体并保持双轴滚动。UiStyle 切换快照裁可见区域，上限 1024px/524288像素，释放旧图层；Activity 系统预测返回未覆盖。

LAN 使用方向性页入场、连续 Dock 高光、IntersectionObserver 一次性呈现和动态 reduced-motion 收敛；dialogGeneration 隔离旧请求与旧关闭回调，sequence 同时保护页面内容与 revision。后台十秒轮询静止，编辑焦点和选区保留。没有新增依赖、数据库或同步协议变化。

离线 full 34/34 通过；原生核心 28、详情折叠 13、Dock 49 场景和设置布局 72 次平台替身执行通过；实际 Edge 动效专项、反馈与完整交互检查通过。它们不等于 Android 真机视觉或帧率验收。收尾修复设置恢复重播及动画中途无障碍焦点保护。修改前备份位于 build/backups/20261005-003315-native-motion-pages、003323-native-motion-core、003446-web-motion-upgrade 和 20261005-main-motion-finish-075521。版本 0.14.6 / 113，最终构建与签名记录见验证历史。下文保留历史记录。

# 本轮补充 · 2026-10-05 · 0.14.5

本轮修复同步设置的测试/保存分离、冲突处理刷新与忙碌反馈，网页弹窗错误可见与提交防重，并将同步核心/设置检查纳入统一入口、校正当前开发信息。版本为 0.14.5 / versionCode 112，数据库仍为 v10；同步协议和数据模型未变。

同步设置 16 个生产方法离线场景、同步核心定向检查、真实 Edge 模拟手机接口针对性检查和业务独立审查通过。`checks/run_checks.py full --list` 当前列出 32 项，本轮没有重新全量运行 32 项。最终 release、lint、签名、APK 内网页资源与发布产物证据见 [验证记录](../validation-history.md)。真实手机界面、真实 WebDAV 多机和覆盖安装仍未验收。发布准备备份 `build/backups/20261005-002106-release-0.14.5`；同步页修改前备份 `build/backups/20261005-001227-sync-settings`。下文保留历史记录。

# 本轮补充 · 2026-10-04 · 0.14.4

本轮按用户确认将导入课表限定在课表页/API，删除 HomeCourses 异步首页注入与小组件课程分支。系统其他日程统一读取 HomeTimelinePreferences，设置/首页/网页共用一个开关；开启保持系统原样及既有 calendarEventId 自身副本排除。

新增 CandidateMergeActivity 与网页设置入口，后台一致快照扫描、具体分组预览、revision 确认和一次 SQLite 事务合并，仅删除重复 candidate，保留 task/raw/attachments。完整字段比较、来源追溯与独立永久 merge_<UUID> 同步记录见 [合并日程说明](../features/schedules/candidate-merge.md)。DB10 保存审计/身份；恢复先固化已有 UUID 再轮换新实体命名空间。合并标记同事务仅过滤应用基线中的已删除 UUID，保留其他字段/clock 与真实编辑保护。所有同步设备需 >=0.14.4，旧客户端不能解析 merge 内容。

备份 `build/backups/20261004-155709-calendar-scope-merge`。核心 64 项使用生产 Java/SQL 与真实 SQLite（Android API 传输替身），Edge 模拟 API 37 组、离线 full 30/30 通过；release/lint/androidTest 集成成功，lint 0 错误/82 警告，原证书签名 0.14.4/code111 APK 1,937,378 字节、SHA256 `722cae8574ebe76d8139483dc59b24b15f09bc977f40320b4f70e9ce7173ac8f`。证据 `build/calendar-merge-{core,web,full-checks,final-build,signature,version}.log`、`build/calendar-merge-artifact.json`、`build/lan-web-checks/result.json`。这些检查不能替代 Android 真机渲染、系统日历/提醒、实际 WebDAV 多机、后台或覆盖安装验收。独立审查已通过，生产代码与 APK 已冻结；正式发布按 v0.14.4 核对远端、附件摘要和原生更新解析结果。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.3

网页根页面、dialog、textarea 和 week-wrapper 隐藏原生滚动条，保留 overflow；课表 API 以只读 Library.select("").current(浏览日期) 复用已确认边界/命中/最近规则，忽略手机历史选择且不写回。文件名选择器删除，保留周切换，标题使用真实学期标签。同类 select 保留原表单值/事件，由主题圆角按钮和顶层 popover 提供键盘/触摸入口，支持开合与减少动态偏好。记录按钮的 Lucide 加号移除文字基线偏移。此前扩大动效任务在用户澄清忘记刷新后取消，未产生改动；本轮下拉动效来自后续明确要求。备份 `build/backups/20261004-134026-hide-web-scrollbars`。真实 Edge 模拟 API 验证浅深短视口滚动、草稿、保存可达、无横移、下拉键盘/弹窗不裁切和加号中心；未做真机端到端验证。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.2

用户授权二维码包含配对凭据，当前二维码带六位码 fragment，网页立即清理地址再 POST 一次现有认证；保留手动配对和所有撤销/限流。172dp 圆角描边白底、8dp 内距/156dp 码图，不裁切静区。网页使用标准 Lucide 齿轮及有限可取消动效，减少动态模式关闭。附件删除改矢量 X，原图/删除回调保留。备份 `build/backups/20261004-130948-qr-auto-pair` 与 `build/backups/20261004-130914-attachment-remove-center`。原生截图、实扫/真实局域网、系统权限/日历、后台、多机同步及覆盖升级无设备验收。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.1

两页此前遗漏关闭 `UiStyle.page` 开启的内容层 fitsSystemWindows，系统安全区可能重置 20dp 留白。现由 SettingsPageLayout 共用内容/顶栏/视口，规则见 [原生设置页布局规范](../development.md#原生设置页布局规范)。六位安全随机配对码 10 分钟有效，独立会话/写入令牌仍为 256 位；本地二维码只含当前访问 URL，扫码后仍须配对。网页使用中性浅/深阅读表面，主题色仅关键操作/选中态，课表学期选择器和课程排版已调整。课表只聚合日期互斥的异地安排，同日同时异地冲突仍分列，保留每次 occurrence 地点，LAN 直接展示实际 occurrence，接口未改动。备份 `build/backups/20261004-124011-settings-gutters-pairing`、`build/backups/20261004-timetable-merge`。无真机时不能宣称原生渲染、实扫和局域网端到端验收成功。
