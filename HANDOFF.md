# 当前交付：0.14.48 / 155

类型收敛为活动、待办，记录保留在收件箱。SQLite 11 升级保留原文、日历关联与合并来源；同步客户端均需更新。发布包 build/distribution/Chrona-0.14.48.apk。构建、lint 与 58/58 离线回归通过，最终真机验收因设备离线未执行。旧进度记录如下。

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

同步设置 16 个生产方法离线场景、同步核心定向检查、真实 Edge 模拟手机接口针对性检查和业务独立审查通过。`checks/run_checks.py full --list` 当前列出 32 项，本轮没有重新全量运行 32 项。最终 release、lint、签名、APK 内网页资源与发布产物证据见 [验证记录](docs/validation-history.md)。真实手机界面、真实 WebDAV 多机和覆盖安装仍未验收。发布准备备份 `build/backups/20261005-002106-release-0.14.5`；同步页修改前备份 `build/backups/20261005-001227-sync-settings`。下文保留历史记录。

# 本轮补充 · 2026-10-04 · 0.14.4

本轮按用户确认将导入课表限定在课表页/API，删除 HomeCourses 异步首页注入与小组件课程分支。系统其他日程统一读取 HomeTimelinePreferences，设置/首页/网页共用一个开关；开启保持系统原样及既有 calendarEventId 自身副本排除。

新增 CandidateMergeActivity 与网页设置入口，后台一致快照扫描、具体分组预览、revision 确认和一次 SQLite 事务合并，仅删除重复 candidate，保留 task/raw/attachments。完整字段比较、来源追溯与独立永久 merge_<UUID> 同步记录见 [合并日程说明](docs/candidate-merge.md)。DB10 保存审计/身份；恢复先固化已有 UUID 再轮换新实体命名空间。合并标记同事务仅过滤应用基线中的已删除 UUID，保留其他字段/clock 与真实编辑保护。所有同步设备需 >=0.14.4，旧客户端不能解析 merge 内容。

备份 `build/backups/20261004-155709-calendar-scope-merge`。核心 64 项使用生产 Java/SQL 与真实 SQLite（Android API 传输替身），Edge 模拟 API 37 组、离线 full 30/30 通过；release/lint/androidTest 集成成功，lint 0 错误/82 警告，原证书签名 0.14.4/code111 APK 1,937,378 字节、SHA256 `722cae8574ebe76d8139483dc59b24b15f09bc977f40320b4f70e9ce7173ac8f`。证据 `build/calendar-merge-{core,web,full-checks,final-build,signature,version}.log`、`build/calendar-merge-artifact.json`、`build/lan-web-checks/result.json`。这些检查不能替代 Android 真机渲染、系统日历/提醒、实际 WebDAV 多机、后台或覆盖安装验收。独立审查已通过，生产代码与 APK 已冻结；正式发布按 v0.14.4 核对远端、附件摘要和原生更新解析结果。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.3

网页根页面、dialog、textarea 和 week-wrapper 隐藏原生滚动条，保留 overflow；课表 API 以只读 Library.select("").current(浏览日期) 复用已确认边界/命中/最近规则，忽略手机历史选择且不写回。文件名选择器删除，保留周切换，标题使用真实学期标签。同类 select 保留原表单值/事件，由主题圆角按钮和顶层 popover 提供键盘/触摸入口，支持开合与减少动态偏好。记录按钮的 Lucide 加号移除文字基线偏移。此前扩大动效任务在用户澄清忘记刷新后取消，未产生改动；本轮下拉动效来自后续明确要求。备份 `build/backups/20261004-134026-hide-web-scrollbars`。真实 Edge 模拟 API 验证浅深短视口滚动、草稿、保存可达、无横移、下拉键盘/弹窗不裁切和加号中心；未做真机端到端验证。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.2

用户授权二维码包含配对凭据，当前二维码带六位码 fragment，网页立即清理地址再 POST 一次现有认证；保留手动配对和所有撤销/限流。172dp 圆角描边白底、8dp 内距/156dp 码图，不裁切静区。网页使用标准 Lucide 齿轮及有限可取消动效，减少动态模式关闭。附件删除改矢量 X，原图/删除回调保留。备份 `build/backups/20261004-130948-qr-auto-pair` 与 `build/backups/20261004-130914-attachment-remove-center`。原生截图、实扫/真实局域网、系统权限/日历、后台、多机同步及覆盖升级无设备验收。下文为历史记录。

# 本轮补充 · 2026-10-04 · 0.14.1

两页此前遗漏关闭 `UiStyle.page` 开启的内容层 fitsSystemWindows，系统安全区可能重置 20dp 留白。现由 SettingsPageLayout 共用内容/顶栏/视口，规则见 [原生设置页布局规范](docs/development.md#原生设置页布局规范)。六位安全随机配对码 10 分钟有效，独立会话/写入令牌仍为 256 位；本地二维码只含当前访问 URL，扫码后仍须配对。网页使用中性浅/深阅读表面，主题色仅关键操作/选中态，课表学期选择器和课程排版已调整。课表只聚合日期互斥的异地安排，同日同时异地冲突仍分列，保留每次 occurrence 地点，LAN 直接展示实际 occurrence，接口未改动。备份 `build/backups/20261004-124011-settings-gutters-pairing`、`build/backups/20261004-timetable-merge`。无真机时不能宣称原生渲染、实扫和局域网端到端验收成功。

# 拾时 · Chrona｜个人日程自动化交接文档

> 交接来源：[个人日程自动化](chatgpt-conversation://6ab74461-33a4-83e9-bdac-b15c3c8c0f83)。第 1–3 节保留原对话中**能直接确认的用户要求**；原对话里只写了选项字母、但没有选项原文的部分，不在此推断。[README.md](README.md) 面向用户介绍产品，操作教程见[使用指南](docs/user-guide.md)，构建与技术入口见[开发文档](docs/development.md)，历史验证见[验证记录](docs/validation-history.md)。实现与历史决策见下文第 4–6 节。

## 1. 目标与核心闭环

用户希望开发一款独立 Android 应用，暂定名 **拾时 · Chrona**。用户投入自然语言、聊天记录、链接、截图文字或模糊想法后，应用按需联网检索，判断待办、提醒、活动、截止、备忘等类型，补全时间、地点、参与人、备注和提醒规则，生成结构化日程项，写入手机系统日历，并由系统日历提醒。核心目的在于减少记忆与手工整理的负担，尤其处理需要等待、日后再行动的事情。

典型场景包括社团报名、抢票、班会、活动、课程、备忘和购物。Android 是主要平台；Windows 和 iPad 的优先级较低，用户没有提出强多端同步要求。

## 2. 已明确的产品要求

### 输入与处理

- 支持系统分享菜单、应用内文本输入、截图或图片、读取剪贴板、语音、批量输入。
- 快速收件也要保留上传图片和编辑文字的能力。
- 一次输入可能包含多个互不相关的活动；AI 可以一次生成多个日程或任务。
- 投入一项任务后，用户可能立即投入下一项。处理应异步进行；用户离开应用后也应能获知处理结果。
- 提供可手动启动继续检索或联网检查的入口。
- 对模糊任务可以进一步搜索，但应控制 token 消耗；需要详情时再读取具体任务，避免每次都带入完整历史。

### 日程与应用边界

- 应用内保存和分类历史日程；向系统日历写入时使用应用专属日历。原对话中的配置值为 `separate`。
- 时间不明确时，原对话中的配置值为 `pending`；具体待确认规则尚未形成可直接实施的文字规格。
- 用户要求应用自己具备修改能力，不设计在系统日历中人工修改应用所写条目的流程。需要改动时进入应用操作；这是用户明确提出的功能边界。
- 搜索或处理完成后通知并提醒用户；最终的日程提醒仍以系统日历为核心。
- 系统本身能提供的能力优先使用系统能力；如某项能力没有系统支持且需要复杂云端方案，则禁用并隐藏相应功能，不保留无效入口。

### AI 与成本

- AI 服务通过 API 提供；预设常用模型，统计 token 消耗，并尽量设计请求以命中缓存。
- 自动判断所选模型是否支持视觉；不支持时禁用图片功能。用户也提到可讨论低成本且高精度的 OCR，**尚未决定采用 OCR**。
- 图片可选择直接发送给支持视觉的模型。
- 语音转写要保持准确度，具体方案**待讨论**。

### 通用性

- 设计应保持通用，不以某一手机型号、Android 版本或日历应用为前提。原对话曾提供 Vivo、Android 16、手机自带日历作为当时设备信息；用户随后明确表示不希望用这三项锁定产品设计。

## 3. 已知但不能直接展开的选择

原对话经历了多轮选择题。用户多次以 `Q1: B` 这类形式回答，但当前交接读取到的记录中没有完整题目和各选项原文。**这些字母不能直接转写成需求或技术决策。**新项目如需使用这些结果，应打开原对话核对题目、选项以及后续修正，以用户明确的文字补充为准。

## 4. 历史进度（记录于 2026-10-04）

- 当时构建版本 `0.14.2`（versionCode 109）：取消独立 Windows 客户端；改为手机设置显式开启局域网访问，电脑浏览器配对后直接操作真实手机数据，网页离线随 APK 提供。服务绑定非 VPN 的私有 Wi-Fi/以太网 IPv4，前台通知可关闭；关闭/网络变化/进程退出撤销所有会话。协议与验证说明见 [局域网访问](docs/lan-access.md)。
- 保留多 Android 的坚果云 WebDAV 同步（日程、课表、原文、附件、冲突版本与删除墓碑），浏览器写入继续触发手机自动同步和小组件刷新。同步设置及局域网设置统一使用手机的玻璃背景、输入/按钮/开关与安全区。协议见 [sync-contract.md](docs/sync-contract.md)。
- 本轮可恢复备份 `build/backups/20261004-105512-lan-web` 含调整前全部有关已修改/未跟踪代码与文档；用户的 AGENTS 和角色配置未改动。既有离线回归 26/26、同步核心、生产认证 25 项和真实 Edge 上的模拟手机 API 网页交互已通过。隔离真实 Android SQLite/HTTP 的仪器检查包已准备，ADB 无设备时不宣称端到端、手机界面或后台行为验收通过。

- 0.13.85：课程按实际日期接入首页/两个小组件，点击定位课程；筛选状态可见、一键重置；详情显示真实时间依据与保存状态。相对时间按记录创建时刻固定，切换截止类型冻结明确截止时间。新增提醒诊断、小组件弹幕开关（含系统减少动画）及相关备份字段。正式 release 关闭调试并沿用原签名，统一验证入口 `checks/run_checks.py`；详见 [本轮整合说明](docs/home-course-integration.md) 与 [设备验收清单](docs/device-acceptance.md)。下列版本段落为历史进度。

- 0.13.84：课表增加按学年与四学期分组、导入日期确认、多学期保留、零散安排与可逆手动分类、课程数及总时长。当前用户确认所提供秋冬文件的冬学期自 2026-11-09 开始；通用导入采用可编辑建议，不把该日期写死在实现中。保留 0.13.83 视觉样式；详见 [课表开发说明](docs/course-timetable.md)。

- 0.13.83：新增首页标题课表入口、本地 ICS 导入、按实际时间汇总的七日课程网格及详情浮窗。原始文件和时区私有保存并纳入完整备份；实现边界与验证入口见 [课表开发说明](docs/course-timetable.md)。保留 0.13.82 小组件弹幕修复。

当前版本为 `0.13.82`（versionCode 102）：两个小组件共用的 WidgetDanmaku 在删除子项前选择第 0 个空白项，解除文字的共享入场动画，避免删除的动画文字进入 Android disappearing children 队列。清理也覆盖整个弹幕层隐藏及第二轨停用。新增 `checks/widget_danmaku_refresh_check.py` 执行实际 bind，Android 删除与动画行为为替身；502 项刷新检查和 68100 项位置检查通过。数据库仍为 v9，真实桌面前后台与动画视觉验收尚未完成。

当前版本为 `0.13.81`（versionCode 101）：分享与主页加号进入记录页统一为 48dp 单返回按钮；图片查看页使用居中的 Lucide 矢量返回图标。更新页内容关闭 fitsSystemWindows，系统安全区由外层滚动视口处理，保留左右 20dp、上下 24dp 的页面留白，与设置页一致。数据库仍为 v9；设备视觉验收尚未完成。下文保留历史记录。

当前版本为 `0.13.80`（versionCode 100）：更新页采用内容宽度操作按钮、自动检查开关和分层版本/说明卡片。外观自定义颜色卡片接通已有调色窗口，取消不提前切换配色；修复色值空格解析，滑杆区可滚动。公共浮窗不再对二级窗口增加近实色底板，保持统一玻璃表面，下层模糊遵循外观设置并在关闭后恢复。新增自定义颜色与弹窗层级的实际方法替身检查。数据库仍为 v9，真实视觉效果与软键盘适配待设备验收。下文保留历史记录。

当前版本为 `0.13.79`（versionCode 99）：背景图片操作按钮并排等宽，标题右侧增加统一风格开关。`ThemeStore.background` 保留原 URI 语义，用于选择管理和备份；`activeBackground` 供页面与小组件渲染，关闭时返回 null。既有图片默认启用，新选择自动启用，关闭保留 URI 与授权。完整备份存储开关状态，旧备份默认启用，恢复失败回滚原状态。数据库仍为 v9，真机布局待验收。下文保留历史记录。

当前版本为 `0.13.78`（versionCode 98）：根据实际使用反馈移除 Application 对 Activity 进出动画的全局覆盖及四个页面动画资源，恢复系统转场与预测返回；详情未保存/保存中返回拦截保留。公共选项弹窗隐藏内部滚动条。Dock 手指目标与视觉位置分开，单帧循环平滑追随，松手以手指位置选页、从当前高光接续，取消/多指/解绑/主体接管时撤销跟随。新增连续帧回归覆盖远处横拖、长按、转向、快速松手、取消与动画关闭。数据库无变更，真机观感待验收。下文保留历史记录。

当前版本为 `0.13.77`（versionCode 97）：自动更新按本地自然日每天一次，首页真实绘制后延迟 1.5 秒触发；偏好、包版本、网络、解析和持久化全部后台执行，自动请求防重。失败计入当天且静默，手动检查可立即重试；失去焦点时保留待提示版本，首页恢复后交付，同版本只提示一次。`checks/automatic_update_check.py` 使用 Android 与网络替身执行实际检查器，覆盖线程约束、延迟、防重、日界、失败、旋转与待提示恢复；不能替代设备帧率测量。数据库仍为 v9。下文保留历史记录。

当前版本为 `0.13.76`（versionCode 96）：统一引导和设置的玻璃控件、页面与弹窗进出动画；详情只在未保存/保存中拦截返回，普通浏览保留系统预测返回。首页首批本地数据后台准备，壁纸不再拦截首帧，图片与采样同步淡入；恢复核对仅查询处理中 ID，详情横滑合并帧并缓存玻璃控件，缩略图后台加载。新增真实内容绘制后的 `reportFullyDrawn()` 和 `checks/measure_startup.py`，1 秒为待设备验证的目标，不能写成已达标。数据库仍为 v9，工具链和依赖不变。范围、测量方法与边界见 [动效与启动优化](docs/motion-startup.md)。下文保留历史记录。

当前版本为 `0.13.75`（versionCode 95）：按 [软件审查与完善清单](docs/quality-audit.md) 处理 21 项编辑、异步目标、备份、后台取消/超时、小组件、图片与文档问题。详情草稿随旋转恢复，系统返回保护编辑，保存 session 随实例交接；已保存备注以数据库为准，独立同步系统日历 DESCRIPTION。备份从事务内复制的独立 v9 数据库生成，保留 revision、ID 高水位与共享事件关联；恢复时未关联的 READY 任务降为待确认。网络处理共用单调总预算，停止断开连接并取消等待者，预览 I/O 失败不引发付费重试。源码级协调/配置替身、真实网络客户端假连接、SQLite 并发快照与小组件窗口检查通过；数据库无变更。构建、lint 与原签名通过；ADB 无设备，Android 运行时、图片 EXIF 实际渲染、旋转/返回和覆盖安装仍待真机验证。相对时间参照规则保留为建议，未更改产品语义或新增依赖。下文为历史记录。

当前版本为 `0.13.74`（versionCode 94）：修复 0.13.73 普通子按钮在 UP 后 post 的点击回调被同步重建取消的问题，收尾刷新延后到点击执行之后。高光独立保存浮点显示位置，主体拖动插值、松手及点击共用页面动画进度，取消和中断从真实位置继续；Dock 拖动优先，间隙移出不误点。新增 checks/dock_navigation_check.py，用最小 UI 队列替身执行实际 Dock 类，旧版点击丢失已复现、修复版 33 项回归通过；该检查不等同于 Android 运行时或真机验证。构建、lint、独立复核、原签名与文档检查通过，数据库无变更；ADB 无设备，真机触摸与手感待验证，详见 docs/validation-history.md。下文保留历史记录。

当前版本为 `0.13.73`（versionCode 93）：手机底部 Dock 支持滑动与长按拖动共享高光，松手选中对应页面，取消恢复实际选中项；快速选择保留最终目标。页面主体横滑按显示帧合并 MOVE，亚克力 host 在手势开始/邻页加入后收集，每帧只刷新这些 host；去掉两页动画对同一页面树的重复刷新，复用壁纸采样坐标与裁剪对象。页面重建会重采集 host，新主体手势会取消旧队列目标。保留动态背景采样和端点回弹，教程及用户指南补充操作。数据库无变更，构建、lint、静态复核、文档链接与原签名检查通过；ADB 无连接设备，真机手感与帧率待验证，详见 docs/validation-history.md。下文保留历史记录。

当前版本为 `0.13.72`（versionCode 92）：使用引导升级为六步教程，每步给出具体操作和完成结果；底部固定上一步/下一步、支持跳过、旋转恢复，可复制练习示例并进入真实 AI 设置、记录与备份页面。引导版本标记升至 2，已看过旧引导的用户升级后仍会看到新教程，设置中可重看。教程不自动提交记录，解析由用户自行保存触发。数据库无变更；`assembleDebug`、`lintDebug` 和独立静态复核通过，真机交互未验证。下文 `0.13.71` 为上一版记录。

当前版本 `0.13.71`（versionCode 91）。新增首次启动使用引导和设置内重看入口；修复首页、日程、小组件仅传 capture ID 导致多个日程总跳第一项的问题，现按 candidate ID 定位并保留旋转恢复。设置可检查 `DongLanQwQ0/Chrona` 的 GitHub 正式 Release、查看说明与下载 APK；默认每天检查一次，后台发现的版本会等首页前台可见再提示，同版本只提示一次，可关闭。数据库未改变。构建、lint、`GuideReleaseCheck`、diff 和原签名检查通过，正式 Release 已发布并核对上传资产哈希。当前无连接设备，首次引导、多日程点击/旋转、更新下载及覆盖安装未做真机验证。源码与分发仓库为 https://github.com/DongLanQwQ0/Chrona；发布约定见 [docs/github-releases.md](docs/github-releases.md)。以下段落保留历史版本记录。

当前工作区版本为 `0.13.33`（versionCode 53）。本轮先修一个必崩：点击 Dock 栏切页时抛 `IllegalStateException: ScrollView can host only one direct child`。页面主体 `content` 原先直接挂在 `ScrollView` 下，而 `switchTo` 走 `UiStyle.swap` 交叉淡化，要把旧内容的快照作为**第二个**子 View 插进容器的父级；`ScrollView` 继承 `FrameLayout`，于是通过了原先 `snapshotOf.getParent() instanceof FrameLayout` 这一过宽的判断，`addView` 时才抛异常（滑动切页不经过 swap，所以只有点 Dock 会崩）。现在首页三页的页面主体都套一层 `FrameLayout` 宿主（`createUi` 的 `contentHost` 与 `buildAdjacentSection` 的 `targetHost`），并把 `UiStyle.swap` 的判断收紧为 `hostsSeveralChildren()`：`ScrollView`/`HorizontalScrollView` 一律退回直接重建。收件箱「选择收件类型」弹窗改用新的 `showDialog(..., widthPx)` 重载，宽度取屏幕 72% 与 300 dp 的较小值、行文字居中（短标签不再撑成整屏空条）。任务详情右上角菜单去掉「详情内容与操作」标题并精简为「输出预览 / 完整输出 / 解析用量 / 来源链接 / 删除任务」，「删除任务」用新增的 `UiStyle.danger()` 标红（浅色 `#B3261E`、深色 `#FFB4AB`）。新增 `JsonFormat` 统一模型输出的显示：完整 JSON 走 `org.json` 的 `toString(2)`，流式过程中未闭合的 JSON（以 `{`/`[` 开头）用状态机重新缩进（字符串内的逗号/冒号不误伤），纯文本原样返回；`[模型输出]` 分隔标记由新增的 `StreamingOutputStore.CONTENT_SECTION_MARKER` 提供，标记之上的思考内容不动；「模型完整输出」页与详情「输出预览」共用它（原先只有预览做了格式化，且解析不了的部分仍是原始长行），`TaskDetailActivity.prettyJson` 因此删除。确认删除任务对话框文案精简为一句（去掉公共存储副本那一段）。提示词新增：输入给出截止时刻时必须把该时刻用文字写进标题（如 `submit the report (due 5 March 18:00)`），且已知截止时间必须设置 `reminder_minutes_before`，按紧急程度在 10–360 分钟之间取值。图片缩略图右上角的移除按钮由 48 dp/20 sp 收到 36 dp/16 sp（`AttachmentImageTile`，记录页与详情页共用）。`assembleDebug` 与 `lintDebug` 已通过，ADB 覆盖安装成功（设备包信息 versionCode 53/versionName 0.13.33）；设备实测点 Dock「收件箱」正常切页且 `logcat -b crash` 无 FATAL、类型弹窗变窄且文字居中、详情菜单四项且删除项为红色、完整输出页 JSON 已缩进、删除确认对话框为新文案（点「取消」返回、未删数据）、图片 `×` 变小。提示词是纯文本改动，需真实 API 请求才能复验提醒分钟数。上一版 `0.13.32` 修掉端点方向的绘制问题：`DashboardActivity.onDrag` 在「该方向没有相邻页」的分支里提前 `return`，跳过了每帧的 `invalidateSectionSurfaces()`，卡片于是停在拖动开始时的模糊位图上（背景不动态模糊 + 闪烁）；现在该分支在 0.3 阻尼位移后同样重采样，端点拖动是跟手而不是不动。待确认与失败都计入提醒：Dock「收件箱」角标为两者之和并对齐到图标自身的右上角（按条目中心加半个 24dp 图标，不再贴条目边缘），收件箱筛选胶囊「待确认」「失败」用同一个 `countBadge()` 贴在**文字**右上角（贴胶囊容器角会被 chip row 与横滑容器裁掉），Dock「收件箱」的角标贴在图标右上角并带 6 dp `elevation`（否则会被带 elevation 的条目压在图标下面，数字被白色图标盖住）；筛选胶囊改用 `chipRow` 量出的统一宽度与新的 `RADIUS_CHIP`(12) 圆角。记录页空内容提交不再用 `EditText.setError()` 的系统白色弹框，改用应用自己的 `Feedback` 玻璃胶囊并聚焦输入框；外观页切换显示模式/配色原本是 `ThemeStore.publish()` 里的同步 `refreshAppearance()`，整页一帧闪切，现在改用 `UiStyle.swap(page, …)`；`UiStyle.swap` 本身也从「先淡到近透明→重建→再淡入」改成交叉淡化：快照加在容器自己的父级并按容器布局坐标落位（父级滚动偏移同时作用于新旧两层，避免非零滚动时对不齐），外观页因其页面背景透明改为 `UiStyle.swap(stage, page, …)` 取整屏快照（含 backdrop），否则背景会从 ghost 后面透出而显得仍在闪切。`assembleDebug` 已通过，ADB 覆盖安装成功（versionCode 52/versionName 0.13.32）；截图确认胶囊数量、Dock 角标与玻璃胶囊提示。端点拖动的手感与上一批 `0.13.31` 的分页/角标/标题对齐仍需手动确认。上一版 `0.13.31` 把首页/收件箱/日程改成真正的跟手分页：拖动过程中相邻页同时渲染并跟手，超过阈值切换、未达阈值回位；分页容器拦下横滑后子 View 会收到 `ACTION_CANCEL`，因此从卡片起手拖动再松手不会再误开二级页面；亚克力卡片按自身位置采样模糊壁纸，拖动时只翻译视图不会触发重新采样，所以在拖动与切换/回位动画的每一帧都调用了 `UiStyle.invalidateAcrylicSurfaces(pager)`，否则卡片表面会停在拖动开始时的位置。`TaskDetailPagerLayout` 更名为 `SwipePagerLayout`，同时服务详情页与首页三页，筛选胶囊行/类型筛选行/「下一件事」横滑条仍独占其内部起手的横滑。首页去掉「查看收件箱 N」整行，数量改到 Dock「收件箱」右上角的半透明角标；记录入口统一由 `UiStyle.recordEntry()` 构建（手机端悬浮按钮与宽屏入口同一个控件：只有 `+` 图标、套用 Dock 同一套亚克力 `glassPill` 表面并按主题色着色，既不加「记录」文字也不用纯色实心）；「记录一件事」页改为顶栏 ←/✓、标题右侧三个图标入口，并去掉「保存并解析」按钮与「按行拆分为多条」复选框（一次提交即一条输入，`MAX_BATCH_TASKS`/`splitLines` 一并删除）；收件箱与日程标题关闭字体额外边距并垂直居中，与搜索框和右侧控件对齐。`assembleDebug` 与 `lintDebug` 已通过，ADB 覆盖安装成功（设备包信息 versionCode 51/versionName 0.13.31）；截图与 `uiautomator` 树确认首页无「查看收件箱」行、Dock 收件箱带角标、记录页顶栏与图标入口、拖动中相邻页跟手且松手后当前页标记正确。松手是否会误开二级页面、回位手感、以及新一轮首页/收件箱标题对齐需要手动确认。上一版 `0.13.30` 把外观页配色格内容居中并调整「毛玻璃与层级」文案（`启用背景模糊`/`浓度`/`强度`，去掉「更透 / 更实」）。上一版 `0.13.29` 外观页「主题配色」改为两列网格（色点+名称+一行说明，选中时在名称尾部显示 ✓，奇数个预设时最后一格留空）；首页/收件箱/日程的页头搜索框缩到 36 dp、时间排序只用一个箭头（↑/↓）并与设置按钮合并为同一个胶囊控件，顶部留白由 14 dp 收到 2 dp；新增页面级左右快滑换页（`DashboardActivity.dispatchTouchEvent` 里跑 `GestureDetector`，落点先做命中测试，从筛选胶囊行、类型筛选行和「下一件事」横滑条内部起手的横滑不外传）；详情页未达阈值的回位动画由固定 190 ms 改为按剩余距离取 260–420 ms 并用 `DecelerateInterpolator(1.9f)`，候选卡片分页的回位也一并加长；处理结果通知因渠道重要性不可就地提升，改为新建 `chrona_results`（`IMPORTANCE_HIGH`、`CATEGORY_REMINDER`）并删除旧的 `chrona_processing`。`assembleDebug` 与 `lintDebug` 已通过；本轮全部是界面与手势改动，尚未真机回归，通知是否真的悬浮弹出还需一条新的解析结果验证。上一版 `0.13.28` 重画应用图标前景，奶白表盘由半径 36 缩到 25.9 并加 5 单位主题色描边，浅橙背景从「只在遮罩四角露出」变成一圈均匀边框，单色环同步到同一半径；去掉任务详情页右侧滚动条（详情页 `page` 与分页预建的 `targetScroll` 都关掉垂直滚动条）；并修掉详情页左右切换的跳变——`TaskDetailPagerLayout` 与 `CandidatePagerScrollView` 原先在越过 touch slop 后仍按「手指距初始按下点」计算偏移，跟手第一帧就带着整个死区距离跳出去，现在各自记录手势被识别时的 `dragOriginX` 把死区消耗掉，松手阈值仍按完整手指行程判定。`assembleDebug` 与 `lintDebug` 已通过，ADB 覆盖安装成功（设备包信息 versionCode 48/versionName 0.13.28）；图标外观已在本地按同一矢量渲染比对，并在设备上打开详情页滚动后截图确认右侧不再出现滚动条；分页跟手手感与全天复选框点击行为仍需真机手势确认。上一版 `0.13.27` 在收件箱与日程页的标题和设置按钮之间加入搜索框，按关键词过滤当前标签/筛选的列表（日程匹配标题、地点、备注与类型，收件匹配原始输入与链接正文），并加入紧邻的「时间 ↑ / 时间 ↓」排序开关（日程页默认最早在前，收件箱默认最近在前），搜索词与排序方向随页面状态保存；修复全天复选框选中后立即消失——勾选值原本从 `onStateChange(state)` 的状态集合推导，而点击时 `ViewGroup.dispatchSetPressed` 会把按压状态传给内部不可点击的 `ImageView`，`ImageView.drawableStateChanged()` 再用不含 `state_checked` 的状态集合回灌 drawable 清掉勾选，现在改由 `AllDayToggle` 显式持有并直接写入 drawable；应用图标背景由 `#006B60` 改为浅橙 `#FFB74D`。`assembleDebug` 与 `lintDebug` 已通过，ADB 覆盖安装成功（设备包信息 versionCode 47/versionName 0.13.27），截图确认日程页标题/搜索框/「时间 ↑」/设置齿轮同排且「即将到来」按时间升序；全天复选框点击与搜索输入的实际操作尚未在设备上复验。上一版 `0.13.26` 详情页进一步对齐字段列，全天改为自绘可访问控件，统一标题图标与页码图标/文字垂直居中，并移除原始内容展开入口；按 taskId 在详情会话中保留日程页、退出时清除；未保存修改弹窗改为“放弃/保存”，点弹窗外继续编辑。`assembleDebug` 与 `lintDebug` 已通过；ADB 安装尝试因无线设备 offline 失败，未能进行 0.13.26 真机视觉或手势回归。上一版 `0.13.25` 的 `assembleDebug`、`lintDebug` 和 ADB 安装已通过；截图验证详情顶部连体按钮、删除图标和全天方框视觉，任务横滑、首页时间线及 reasoning 响应仍待设备验证。更早版本记录如下：`0.13.24` 保留首页时间线同日竖线、日期点与事件轨道对齐，详情字段、图标、全天状态及紧凑按钮统一，外观主题卡缩高；该版 `assembleDebug`、`lintDebug` 与 ADB 安装均通过。更早的 `0.13.23` 记录如下：背景图片 ZIP 备份代码已完成，`assembleDebug`、`lintDebug` 与 ADB 安装通过；分身自定义背景导入及时间区视觉回归待确认。`0.13.22` 的分身恢复成功，用户确认收件箱、日程、图片和普通附件齐全，当时背景图片未纳入备份。修复 ZIP 恢复时 `PRAGMA wal_checkpoint` 错用 `execSQL` 导致的 SQLite 导入失败；收件箱状态筛选结果卡加入轻量错峰进入动画并保留滚动位置。`assembleDebug`、`lintDebug` 与 ADB 安装验证通过，设备包信息为 versionCode 41/versionName 0.13.21；ZIP 实际导入和筛选动画回归待用户操作。外观页新增多种预设与自定义主题色，面板混色浓度可调；记录页移除语音入口，支持多图选择和待提交文件列表，普通附件存入公共 `Downloads/Chrona`，删除附件前确认。详情页整理图标操作、备注浮层、日志滚动和弹窗；首页时间线展示近一月日程事件、隐藏空日期，可设 5–50 项（默认 20），下一事项可横滑查看 5 件；收件箱通过独立入口访问并显示待确认数量，无记录时显示空态。亚克力筛选切换复用现有背景，自定义壁纸准备期间使用带超时退路的首帧门控。新增独立完整 ZIP 备份/恢复入口：包括收件箱/日程草稿、原图 bytes、公共普通附件 bytes、模型输出及非敏感设置；API 密钥默认排除；自定义背景图片从 0.13.23 起纳入 ZIP 备份。跨设备恢复会在本机 Downloads/Chrona 重建普通附件并重建可验证的日历关联，缺少日历权限/无法恢复的事件转为待确认。导入前校验，失败时尝试恢复数据库、输出与设置并清理此次新增副作用。其他后台栈 Activity 返回时仍可能重建一次以更新已创建控件的主题；日历 provider 写入成功、数据库关联保存前若进程崩溃，仍可能造成重试重复写入；恢复期间 Job 被取消到 worker 完全退出的极短并发窗口尚未设备验证。

项目早期基线为 `0.10.0`；当前版本与验证状态见本节开头。应用以原生 Android Java 实现：文字、图片或语音进入 SQLite 收件箱；`JobScheduler` 异步调用用户自定义的 Chat Completions 兼容 HTTPS API；应用内审核、修改草稿后写入应用专属系统日历。支持结果通知、失败重试、API 密钥本机加密保存及返回 token 用量记录。主要模块为 `app/src/main/java/com/donglan/chrona/` 下的 `ai/`、`data/`、`image/`、`web/`、`debug/`、`processing/`、`calendar/` 和各原生 Activity。

`0.8.0` 完成历史分类与全天日程编辑：草稿存类型与全天标记，首页按类型和状态筛选；详情页用包含结束日的日期表单修改全天日程，日历写入采用 UTC 零点与排他结束日。新增 DeepSeek Flash/Pro 快速填充和服务报告的缓存命中 token 显示；整页滚动、卡片样式和动效改善小屏操作。数据库升级到版本 4，旧行保留。上述新增行为已完成离线检查和构建，尚待真机回归。

`0.9.0` 将主界面整理为「首页 / 收件箱 / 日程」底部导航：快速记录保留文字、图片、语音、剪贴板、批量及系统分享，成为二级页面；历史筛选进入收件箱，按时间的草稿清单进入日程。设置拆为外观、AI 服务与测试版诊断入口。外观支持系统/浅色/深色模式及青绿、晴蓝、暖珊瑚、Android 12+ 壁纸配色，无新增依赖或数据库迁移。构建与 lint 已通过，尚无设备验证界面、动画和系统动态配色。

`0.10.0` 根据旧版首页截图调整浏览流程：首页优先显示当前待确认或下一件日程，收件箱改用水平状态筛选与类型菜单，日程改用页内三分区，长列表每次展开 12 条。新增柔和光斑、半透明磨砂卡片和轻量动效，宽屏使用侧边导航，Android 15+ 显式处理系统栏安全区域。代码仍为原生 View，无新增依赖或数据库迁移。构建、lint 和原有签名需保持验证；设备视觉回归待进行。

`0.11.0` 接续上一段被中断的开发（工作区留有未提交的流式预览、弹窗改造与自定义背景图），按用户反馈完成：解析中可实时预览模型原始输出（详情页 12 KB 尾窗按 1.5 秒增量刷新，完整输出按 16 KB 分页；预览上限不再让解析失败）、外观改动经 `ChronaApp` 传播到所有存活页面（修「主题色恒为绿」）、选择器与弹窗样式统一并修正设置图标与模型预设的对齐、诊断页改为分区卡片、新增配置导入导出（API 密钥默认不导出）、补充列表与弹窗动效。数据库仍为版本 4；构建与 lint 通过，**无设备可回归**。

`0.11.1` 把所有系统 `Toast` 换成应用内的玻璃胶囊轻提示（`Feedback`），并修掉导入配置时连续重建两轮页面的问题。`0.11.2` 修掉「从系统分享进入记录页时按返回会退出应用」（改为回收件箱），并让收件箱里排队中/解析中的行显示已用时间、让轻提示避让输入法。数据库仍为版本 4，构建与 lint 通过，**均无设备可回归**。

`0.11.3` 复核配置导入导出与排队计时：导出始终包含当前表单中的 API 地址和模型，API Key 默认不导出；新设备可导入无 Key 配置后补填，已有本机 Key 会保留。配置文件增加大小、版本和字段校验。排队用时由独立轻量计时器更新文字，状态变化时才刷新列表。无数据库变更；构建、lint 和签名验证通过，已在设备验证启动、收件箱和状态筛选。

`0.11.4` 修复 SSE 流分片中 `content: null` 被 `optString()` 转成字面文本 `null` 并污染完整输出的问题；普通 JSON 响应的预览也仅追加字符串正文。针对性样例、构建、lint、签名和设备安装启动均已验证。未触发真实模型重解析，避免产生未确认的 API 费用；之前保存的错误输出不会自动改写，重新解析后会生成干净输出。

`0.12.0` 重排任务详情页：先显示任务状态、再显示可折叠原文和日程表单；已完成的模型输出、用量、链接正文默认收起，解析中仍可展开实时预览。单条日程将确认操作固定在页面底部，多条日程保留各自保存操作；删除和重新解析收进次要操作。卡片改为更实的主题表面，展开使用局部轻动效。数据库无变更。构建与 lint 通过；设备安装被系统拒绝两次，界面真机回归未完成。

`0.13.0` 按事件类型补齐缺少的结束时间；截止时刻保留为区间结束，开始点只提前一分钟。收件箱支持长按多选、当前筛选全选与批量删除，日历清理失败的任务会保留。任务详情可以左右切换收件，并修复模型输出预览的内层滚动冲突。数据库无变更，versionCode 20；构建与 lint 通过，设备安装和交互回归待完成。

`0.13.1` 将普通任务状态徽标并入详情页头，移除仅显示状态的空白整宽卡片；同一收件包含多条日程时使用横向吸附分页，页码与底部确认操作跟随当前日程。数据库无变更，versionCode 21；`assembleDebug`、`lintDebug` 与签名验证通过。设备当前 ADB offline，交互回归待完成。

`0.13.2` 修复多日程区域的横向手势被详情页外层任务分页抢截，日程卡片现在独占内部横滑；加入 270 毫秒减速缓动吸附动画，系统关闭动画时直接切换。数据库无变更，versionCode 22；待构建与设备交互回归。

`0.13.3` 在 `<application>` 显式启用 Android Predictive Back，所有原生 Activity 使用系统默认的边缘拖动预览和跨 Activity/返回桌面动画；代码没有注册会拦截系统动画的旧式返回回调。目标 SDK 为 36；无数据库变更，versionCode 23；`assembleDebug`、`lintDebug`、原签名校验通过，并在 Android 16 手势导航设备上从任务详情执行左边缘返回回到首页。

`0.13.4` 重做多日程切换为跟手横拖、松手后可见的 300 毫秒减速吸附动画，并限制详情页内外层分页不从系统返回边缘抢手势；页码/底部按钮仅在跨页时更新。无数据库变更，versionCode 24；待构建、lint 与含多条日程记录的设备专项验证。

`0.13.5` 修复收件箱任务详情左右切换只重绘、没有整页滑动动画的问题：旧页面沿手势方向滑出，新页面从另一侧进入，底部固定操作区一起过渡；单条日程保留外层收件分页，多条日程由内层分页接管。无数据库变更，versionCode 25；待构建安装，用户要求不再做设备手势测试。

`0.13.6` 任务详情页采用紧凑顶栏（返回、居中标题、粉色勾选保存），日程字段改为无边框标签行，时间合并为横向区间；原始内容单行折叠，模型输出/用量/任务操作收纳至底部更多信息区。保存成功提示“已更新”并返回；详情页切换改为手势跟随、松手滑出或回弹，不叠加透明度变化。无数据库变更，versionCode 26；`assembleDebug` 通过，设备安装时显示 offline，未启动应用或做手机交互测试（用户明确要求）。

`0.13.9` 一级浮层按各自区域采样实际壁纸模糊，并将采样背景放大 1.04 倍；支持普通模糊和高斯模糊两种方式，修复晴蓝主题的背景断层与换色闪烁。详情页将次要操作收进顶栏菜单，图片缩略图改为居中裁切；手机 dock 调整为胶囊圆角，记录按钮独立置于其上方。无数据库变更，versionCode 29；构建与 lint 通过，ADB offline，手机视觉效果尚未复验。

`0.13.8` 增加普通文件附件入口，可从记录页/详情页文件选择器添加 ZIP 等文件，也可通过 Android 系统分享进入。Android 10+ 写入公共 `Downloads/Chrona`，详情显示名称、类型、大小并支持打开、保存副本或解除关联；旧版 Android 使用公共下载目录兼容路径。普通文件仅以文件名、MIME 类型和大小元数据提供给 AI，文件内容不上传；删除任务或解除关联不会删除公共文件。数据库升级到版本 6；versionCode 28。构建与 lint 通过；系统分享、公共存储及详情操作尚未设备验证。

`0.13.7` 首页合并重复待确认统计，将“近期日程”和“最近收件”合并为今天/明天时间轴；手机底栏曾加入记录按钮，后在 `0.13.9` 调整到 dock 上方；收件箱类型选择折叠到漏斗弹窗，整行“查看全部”扩大点击范围。暗色背景图增加半透明黑色遮罩。详情页可编辑捕获文字、追加多张图片；图片支持手势放大并通过系统文件选择器保存，重新解析会将所有图片附件一并发送。数据库版本 5 将旧的单图记录迁移到 `task_attachments`；versionCode 27，`assembleDebug` 通过，未安装或做手机界面测试（用户要求）。

`0.2.0` 补齐了日程删除与草稿生命周期：任务详情可逐条删除草稿（已写入日历的同步删除系统日历条目），也可整条删除输入及其全部草稿，并取消该输入排队中或运行中的解析、清除对应通知。同一版本还补齐了剪贴板与批量输入：收件箱可一键读取剪贴板文字并追加到输入框，可勾选「按行拆分为多条」把整段粘贴拆成多条独立输入（单次上限 20 条，同一事务写入）。

`0.3.0` 补齐了图片输入：可接收系统分享的图片，也可选择相册图片或截图；图片复制进应用私有目录（`files/attachments`），缩放到长边 1568 px、JPEG 质量 90 后以多模态 `content` 数组（`text` + `image_url` data URL）发送；支持只发图片的输入。带图片的请求被服务拒绝时（415/422，或 400 且错误信息指向图片/内容格式），会把该「基础地址 + 模型名」记为不支持图片并停用图片入口，可在「AI 服务设置」中重新启用。这一版本把数据库提升到**版本 2**，通过 `ALTER TABLE tasks ADD COLUMN image_path TEXT` 保留版本 1 的全部存量数据。

`0.4.0` 补齐了语音输入，方案已由用户确认走**系统能力**：用 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`（自由说、语言跟随系统）调起系统识别界面，结果追加到输入框，不申请 `RECORD_AUDIO`、不调用云端转写接口、不产生 API 费用。设备没有识别服务时入口直接隐藏；清单已声明 `android.speech.RecognitionService` 的 `<queries>`，否则 Android 11+ 的包可见性会让识别服务不可见。

`0.5.0` 补齐了**按需联网检索的第一步**：解析前自动抓取输入里出现的链接正文，作为上下文一并发送。抓取在设备上完成，不需要任何新服务或密钥，并有硬上限（3 个链接、单页 512 KB、单页正文 2000 字、合计 4000 字、各 10 秒超时、最多 5 次重定向、仅 http/https）。抓取失败不影响解析，任务详情会显示抓取时间、字数与摘要；「重新联网检查并解析」可反复手动触发，没有轮次上限。正文提取自行实现（`web/LinkFetcher`），未引入新依赖。该版本把数据库提升到**版本 3**（`tasks.link_text`、`tasks.link_fetched_at`），版本 1、2 的数据全部保留。**通用联网搜索已由用户决定暂不接入**，理由与后续策略见第 5 节。

`0.6.0` 加入了面向测试期的诊断能力：收件箱里的「诊断信息（测试版）」（仅 debuggable 构建可见）汇总版本、机型、AI 配置（不含密钥）、数据库真实 `user_version` 与列名、记录数、附件占用、`JobScheduler` 待处理任务，以及进程内事件日志，并提供「复制全部」。`debug/DiagLog` 同时写 logcat 与 `files/diagnostics.log`（128 KB 轮转保留上一份）；关键路径（入队、job 生命周期、图片字节、链接抓取耗时、请求体量与生效超时、响应耗时与 token、HTTP 状态与异常类型、数据库建库/升级、日历写入、删除、重试、剪贴板/语音长度、图片导入与清理）均已埋点。数据库仍为版本 3。

用户已批准 Gradle 9.6.0 与 Android Gradle Plugin 9.4.1，缓存位于 F 盘；新构建工具链仍须先经用户审核。`assembleDebug` 已成功，真机安装、启动、API 解析通过；用户确认日历写入测试通过。构建命令与准确路径见[开发文档](docs/development.md)。手机上的 API 密钥、配对码和服务地址不写入仓库。

## 5. 尚未完成与已知边界

- **通用联网搜索**（对模糊任务做开放式检索）**已由用户确认暂不接入**：它需要第三方搜索服务，涉及额外费用与隐私，属于新增外部依赖。后续若要做，用户已确认的实现策略是——解析发现缺关键信息时自动检索、同时保留手动入口、并允许多轮检索。当前的链接抓取已满足「自动 + 手动 + 不限轮次」：每次解析都会重新抓取，手动「重新联网检查并解析」可反复执行。
- 链接抓取只做静态正文抽取，不执行 JavaScript，也识别不了需要登录或重脚本渲染的页面；导航等页面杂项会一起进上下文。抓不到时按原文解析，不会让任务失败。
- 历史分类与全天日程编辑已在 `0.8.0` 实现，尚未真机回归。
- **`0.11.0` 新增能力均未真机回归**（无可用设备）：流式预览（服务是否真的以 SSE 返回、长输出分页与截断提示的实际观感）、自定义背景图的显示与内存占用、配置导入导出在系统文件选择器中的实际读写、切换配色后各页面是否立即更新、新动效在不同刷新率上的手感。真机回归时应优先验证：解析中打开任务详情能否看到逐步增长的文字，以及切换主题色后返回首页是否立即变色。
- **自定义背景图的实现边界**：由 `GlassBackdropView` 在背景层绘制，按需降采样到长边 2048、居中裁切，并叠加主题色罩与光斑保证文字可读；图片是本机文档 URI（`ACTION_OPEN_DOCUMENT` + 持久化读权限），因此**不随配置导出**，用户在别的设备上需要重新选择。
- **配置管理（0.13.37）**：AI 服务页面不再提供独立配置导入导出，统一通过“备份与恢复”ZIP 管理。ZIP 中嵌入 ConfigBackup JSON，包含地址、模型、思考强度与外观设置；API Key 仍默认排除。保留无密钥恢复后的待补填设置和失败回滚。
- 语音转写完全依赖设备上的系统识别服务：没有服务的设备上入口隐藏。识别质量、是否离线可用、是否支持连续说话都由该系统服务决定，应用无法控制，也不做二次纠错。
- 语音入口用系统识别界面，会短暂离开本应用；返回后文字追加到输入框。若系统识别界面被用户取消，不写入任何内容。
- 图片支持与否只能靠「请求被拒」推断，没有权威的模型能力清单：400 只在错误信息出现 image/vision/multimodal/content/base64 等词时才判为不支持图片，因此判定可能漏报；415/422 一律判为不支持。误报时可让用户在设置里重新启用。
- 图片走的是 OpenAI 风格的 `image_url` + base64 data URL。若用户的自定义服务需要别的多模态格式，图片会失败而文字仍可用。
- 对时间不明确的任务目前只保留待确认草稿，缺少完整的澄清规则；日程不会在用户确认前自动写入。
- 可记录 API 报告的 token 数量与缓存命中数，但尚未用真实服务验证缓存命中率。
- 删除流程、剪贴板、批量提交、语音输入与链接抓取均**尚未完整真机回归**（图片输入已在 `0.6.2` 上成功过一次，见上）；删除已写入的日程依赖 `READ_CALENDAR`/`WRITE_CALENDAR` 权限，权限被拒时删除不会执行。抓取逻辑已在本机 JVM 上直接跑过样例（含真实 https/http 抓取）。
- **真机已验证的部分（2026-09-26，vivo V2527A / Android 16）**：文字输入的异步解析、草稿生成与 `needs_review` 状态正常（`今天晚上10点` 正确解析为当天 22:00，实测 token 4803 / 2856）；诊断页读到的 `user_version=3`、`tasks` 全部列名、附件占用、`JobScheduler` 待处理任务与事件日志均正确。同一次实测暴露了图片请求 30 秒读超时（`0.5.1` 修，图片读取超时放宽到 180 秒），以及网络抖动下必须手动重试、同一输入被并发解析两个问题（`0.6.1` 修：可恢复失败自动重试 3 次 / 间隔 3 秒 / 总预算 240 秒，进程内 in-flight 集合拦截并发重复运行）。
- **图片路径已有真机成功记录（`0.6.2`，2026-09-26）**：`20:20:58` 发起（152365 B JPEG + 15 字文字）→ `20:21:22` 返回，23.4 秒 / 6996 token / 1 条草稿，草稿描述里含只存在于图片中的内容（例17,18,19,20、`(1+1/n)^n` 的结论），**证明 `deepseek-flash` 接受图片且确实读懂了图片内容**。此前 `0.6.0` 上的三次失败全部是网络层错误（`UnknownHostException: Unable to resolve host "api.deepseek.com"`、`SocketException: Software caused connection abort`），一次都没到达服务器——**那些失败是手机网络/VPN 的问题，不是图片逻辑的问题**。已对照 [DeepSeek 图像理解文档](https://api-docs.deepseek.com/zh-cn/guides/vision/) 确认 `deepseek-flash` 支持图片、`content` 数组 + `image_url` base64 data URL 与官方示例一致，官方会把图片归一到约 1300×1300 等效像素、每张最多 1024 token，因此不需要再缩小 1568 px / JPEG 90 的现有设置。注意：真机成功只有一次、在单一网络环境下取得，**尚未在弱网/大图/多图等条件下回归**。
- **应用在后台被系统清理会让输入永久卡在「解析中」**（`0.6.1` 真机日志确认，`0.6.2` 修并已真机验证，`0.7.0` 起从根上降低了被杀概率）：解析跑在 `JobService` 的进程里，vivo 一类系统的后台清理会直接杀掉进程。进程一死，进行中的请求来不及记录任何日志，持久化 job 也一起消失，`processing` 状态无人收尾；而详情页的重试按钮在 `processing` 下是禁用的，用户因此彻底卡住。`0.6.2` 增加了 `ProcessingJobService.reconcile()`（收件箱 `onResume` 时把「解析中但没有 worker、也没有待处理 job」的输入标记为失败）、被中断时的日志与状态处理、收件箱解析中耗时计数，以及诊断页的「进程已运行」。**真机日志 `20:19:53 reconciled task=5 stuck parsing -> failed` 证实该路径在设备上生效。**
- **`0.7.0` 起解析改用「用户发起 job + 常驻通知」**：API 34+ 用 `JobInfo.Builder.setUserInitiated(true)`（需清单里的 `RUN_USER_INITIATED_JOBS`，normal 级权限）+ `setEstimatedNetworkBytes`，并在 `onStartJob` 内 10 秒期限里调用 `JobService.setNotification(...)` 挂常驻通知。注意 **`JobInfo.Builder.setNotification` 已从 API 36 移除**（用 `javap` 在 `android-36/android.jar` 上确认过），不要再走那条路；API 34+ 的用户发起 job 只能用于网络数据传输，必须声明网络约束。真机确认：`Priority: 500 [MAX]`、`userInitiatedApproved: true (started as UIJ: true)`、`Started with foreground flag: true`、`HAS_FOREGROUND_EXEMPTION`，入队到运行 2.2 秒；提交后 0.4 秒切后台仍能跑完（`task=8 ok in 7619ms`）。**但这不等于「后台一定跑得完」**：真机上仍出现过一次 `job stopped task=7 worker=true`，靠「线程不会被 interrupt 终止 + 自动重试」才成功（`attempt 1 failed after 76180ms` → `response ok in 92304ms`）。用户已开启系统「后台进程锁」；若仍不稳，下一步可考虑让用户把应用加入 vivo 的自启动/电池白名单。
- **过期 job 可能重复计费**（`0.7.0` 修）：真机上见到一个 `0.6.2` 时代 `setPersisted(true)` 留下的 job 在为一条已经解析成功的输入排队——它一旦运行就会重新上传图片并再次计费。现在 `processOnce` 只处理仍处于 `queued` / `processing` 的输入，其余直接跳过并记日志。**该跳过分支未能在真机上直接触发**（已完成的 job 会从调度器注销），只验证了正常解析不受影响。
- **后台存活是这类「提交后等结果」功能的固有风险**：`0.7.0` 的用户发起 job 与常驻通知把风险降到「偶尔被系统停止但仍能靠重试完成」，而不是零风险；真机测试时建议保持应用在前台、不要从最近任务里划掉，并开启系统的后台进程锁。用户侧的长效方案（vivo 自启动/电池白名单）尚未执行。
- **签名与升级安装**：`0.8.0` 的 Debug 构建已显式指向 `F:/Android/user-home/debug.keystore`（可用 `chronaDebugKeystore` Gradle 属性覆盖路径）。这份密钥的证书 SHA-256 为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，与之前交接记录一致；重建 APK 后已用 `apksigner` 核验。`%USERPROFILE%\.android\debug.keystore` 是另一份密钥，指纹以 `9968c5…` 开头，不可用于覆盖安装旧包。私钥不在仓库中，请勿删除或重生成；若丢失，应用 `allowBackup=false` 且数据在私有目录，卸载会清空收件箱。签名一致只证明升级安装的必要条件，真实设备上的升级与数据库迁移仍待回归。
- 剪贴板当前只接受纯文本与 HTML 文本；Android 10 起后台无法读剪贴板，读取入口只在应用处于前台时有效。
- 数据库版本已升到 4。改表必须继续在 `TaskStore.onUpgrade` 里追加顺序迁移并保留既有数据；手机上的版本 1/2/3 数据依赖已有迁移升级，升级安装必须专门回归。v3→v4 的新增列已在本机 SQLite 离线验证。
- 真机解析测试曾遇到服务拒绝模型名 `DeepSeek-V4.1-Flash` 的 HTTP 400；改为该服务实际支持的 `deepseek-flash` 后解析成功。不要将这个模型名当成所有自定义 API 的通用值。

## 6. 给下一位开发者的起始说明

> 请继续开发“拾时 · Chrona”。先阅读[开发文档](docs/development.md)、本文件和 [CHANGELOG.md](CHANGELOG.md)，再检查当前代码与 Git 状态。保留已在真机工作的文字输入、异步解析、审核、系统日历写入与删除闭环，以及剪贴板、按行批量提交、图片、语音与链接抓取；**通用联网搜索已由用户决定暂不接入**，除非用户主动改变主意，否则不要自行引入第三方搜索服务。优先真机回归 `0.8.0` 的历史筛选、全天日程、界面与升级安装。涉及新表结构时先写迁移。原对话中仅以 Q1/Q2 字母表达的选择仍需核对原题，不要猜测。若需新增构建工具链或新依赖，先给用户具体方案审核。

## 7. 提交与文档约定

- **一个功能一个提交。** 提交信息写清改了什么、为什么改，并注明是否改动数据库版本；不要把手头的多个不相关改动压进同一个提交。
- **应用代码提交前必须构建。** 至少运行一次 `:app:assembleDebug`；未能执行的验证（尤其是真机验证）必须如实写明。纯文档修改检查内容、链接和 diff 即可，无需重建 APK。
- **版本信息同步。** 版本号变更时，`app/build.gradle`、`CHANGELOG.md`、`README.md`、`HANDOFF.md` 和开发文档要一起改，避免文档与构建产物不一致。
- **改表必须先写迁移。** 调整表结构时同步提升 `TaskStore.DATABASE_VERSION`，在 `onUpgrade` 中按 `oldVersion` 顺序迁移，并在开发文档与 CHANGELOG 注明迁移内容。手机上的数据库是从版本 1 一路升级的真实数据，升级路径必须保留存量记录。
- **按读者组织文档。** README 保持用户产品首页，详细配置与操作放在 `docs/user-guide.md`，构建与技术入口放在 `docs/development.md`，验证记录放在 `docs/validation-history.md`；不要把开发日志重新堆回 README。
- **「已验证」只写真实发生过的验证。** 验证记录只记录确实跑过的构建、安装与真机结论；仅通过编译的功能只能写成「已实现、待真机回归」。
