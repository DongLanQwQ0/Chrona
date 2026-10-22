# 验证记录

## 0.15.39 — 网页横向卡片与首页课表刷新竞态 — 2026-10-22

- 发布后用户明确要求安装：ADB在vivo上以install -r将200／0.15.38覆盖为201／0.15.39，系统返回Success；安装后dumpsys确认版本，DashboardActivity启动Status: ok。未卸载或清数据；此升级记录不代替尚未执行的真实网页链路／触摸验收。
- 已验收源码`671862e`与不可变标签`v0.15.39`已推送；[正式Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.39)于11:06发布为Latest。页面确认同一提交、四条用户更新及固定`Chrona-0.15.39.apk`（3097686字节／2.95 MB），截图`build/release-0.15.39-published.png`。手机未自动覆盖，需升级后刷新网页。
- 网页详情从纵向堆叠改为时间升序的多卡横向轨道；桌面250–340px卡宽、1280px下三张完整卡片，短内容自然高度、长内容卡内纵向滚动。底部保留切换和当前项操作；外部candidate_id按实际几何居中，当前ID写入历史，刷新／返回保留。主页最近作业入口携带筛选身份，刷新与历史不丢失；总列表仍筛选分页。原文／附件／记录操作收纳到浮层，托管作业隐藏该入口。
- `lan_detail_navigation_check.cjs`生产资源＋隔离HTTP合成45候选，33条通过：稳定时间排序、同时间ID、未定项末尾、目标居中与三完整卡、内部滚动、编辑与返回、旧回调不劫持新路由、真实计算字号150%的390/320深色布局、减少动画及空记录。大字体初验发现Dock变高遮挡10px，改为实际Dock高度决定详情底部安全距离后通过。专门只读UI复审通过。实际CUA滚轮从118横滚到122，URL／高亮／操作同步；实际主页最近作业显示作业／全部／未完成，`cua-horizontal-scroll.png`、`cua-homework-filter.png`。
- 受影响浏览器parity、deadline、原文保留record-removal专项通过；DOM复用87条与quick7/7通过。旧parity入口标题和隐藏候选操作、DOM片段边界及原文浮层选择器已同步，不通过改产品迁就夹具。CDP整页截图会错误偏移横向轨道，改用实际视口截图；CUA画面另行确认。
- 原生稳定复现：课表结果在`applyCompletionRows`完成动画期间到达，旧`refreshHomeContent`只置homeRefreshPending，结束回调却只消费completionRefreshPending；相同快照不触发poll刷新，课表继续缺失。旧候选受控实际Activity测试`home-race-red.log`失败，新代码将请求合入完成队列后通过。独立审查发现关联失败恢复缺口，补读取失败清双标志、保留dirty、既有轮询按dirty重试；无忙等或陈旧来源回退。
- `HomeCalendarRefreshDeviceCheck`限独立v33模拟器，真实首页／本地读取／动画／渲染，主动控制迟到课程回调顺序，关闭周期轮询以验证不需重进；再调用真实失败完成回调，断言立即解锁、dirty保留且没有立即重试，随后同快照的真实poll恢复。深浅主题`home-race-recovery-{light,dark}.log`均通过，`home-race-{light,dark}.png`干净实际截图并检查窗口焦点；无任务／Calendar写入，偏好已恢复。完成卡片复用9条及接线检查通过。未模拟实际存储I/O异常，用户历史现场无日志，所复现路径与历史原因不混为同一证据。专门只读UI最终复审通过。
- 正式双构建、androidTest编译及双lint日志`build/lan-detail-navigation/formal-final-build.log`；同目录保存候选签名与清单。固定APK为`build/candidates/0.15.39/Chrona-0.15.39.apk`，201／0.15.39、官方包名与原签名。原生修复加入后已重新构建，不分发先前仅含网页改动的产物。
- 本轮未覆盖真实手机；网页真实手机数据链路与真实手机触摸横滑未验收。公开Release发布状态见当前工作单；不追加此前叫停的公开下载／手动更新复验、付费AI或真实WebDAV。

## 0.15.38 — 新作业定位与首次亮屏计时 — 2026-10-21

- 源码`e96080f`与不可变标签`v0.15.38`已推送；正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.38)于21:12发布为Latest。页面确认同一提交、三条用户更新说明和固定`Chrona-0.15.38.apk`（2.95 MB），截图`build/release-0.15.38-published.png`。手机已安装同一200／0.15.38；未追加此前叫停的发布后公开下载或手动更新复验。
- 新入库的未完成学校作业在同一事务中保存未查看标记；数据库14→15为旧项默认0。重复同步、通知关闭／到期不恢复或消费标记。主页复用原作业按钮绘制8dp红点，沿实际列表排序与24项分页定位，补足首尾真实滚动空间，将目标居中并短暂主题强调。目标完成绘制且窗口有焦点、中心可见后才消费单项；其它未查看项保留。预建工作区完成激活后接续定位，HomeData复用已有TaskStore读事务，避免journal_mode锁。
- 隔离原生200／0.15.38：深色大字体1.3、66项跨第3页、两未读逐条查看14条通过；浅色单项首尾、真实通知栏遮挡不消费、恢复焦点后定位、关闭动画后恢复原foreground15条通过。截图与日志在`build/homework-first-screen/`，专门只读UI最终审核通过。夹具守卫拒绝正式包、手机、真实账号和外部同步，不写真实日历。
- 提醒服务生命周期与首次亮屏／服务启动竞态检查通过；作业存储49条、quick7/7、全表快照／隔离恢复16条通过。完整备份导入上限引用数据库唯一版本常量；原生当前版本快照校验与unread保留也通过。
- 第一轮真实十分钟暴露亮屏后350ms队列刷新前进程结束的撤下缺口；已改为亮屏广播内同步保存并立即交付系统结束闹钟，卡片后台刷新。收紧原生等待为实际十分钟TTL与needs_post=false，第二轮启动8条、跨进程同截止6条、实际600秒后系统通知撤下及原作业保留5条均通过（截止1792585778471，检查1792585780565）。首次亮屏同时更新旧关闭PendingIntent的截止身份，进程结束仍可关闭；服务在显式启动与亮屏竞争时先履行前台提升，独立于活动提醒频道是否开启。
- 追加结果通知第一行显示实际日程名称，优先已写入项、多项附数量，展开保留处理状态及原详情。生产名称选择6条、原生折叠／展开6条通过，截图`notification-{collapsed,expanded}-title-light.png`，专门只读UI审核通过。
- 后续只读确认手机离线，未重试系统拒绝的安装；在隔离包补充通知生命周期14条通过：分别熄屏拉取的两项合并、部分完成保留等待状态与身份、旧批次关闭无效、首次亮屏后旧PendingIntent仍能关闭、关闭不消费红点、全部完成及来源关闭撤下并保留未完成源数据。日志`notice-edges.log`，夹具已恢复；只新增测试，生产候选不变。
- 最终正式debug／release／androidTest及双lint通过：`formal-final-build.log`。修正minSdk26下stateDescription的API30条件；API36实测分支不变。候选`build/candidates/0.15.38/Chrona-0.15.38.apk`为3094850字节，官方包名200／0.15.38、release不可调试，签名与手机0.15.35一致；`candidate-{signing,badging}.log`。此固定候选已正式发布，见本节发布记录。
- 手机只读确认所查作业早于用户指出的时刻已入库，之后同步新增0；具体课程与时间保存在私有诊断目录。未保留历史投递日志，熄屏期间旧十分钟提示提前结束仅为合理解释。初次系统拒绝辅助包安装后未绕过；用户重新授权USB继续，已正常更新辅助包、保存完整数据恢复备份（不含API密钥，3630383字节／59项，ZIP CRC通过），并覆盖安装同签名200／0.15.38，原有作业与主页保留。
- vivo OEM实际收起胶囊、点击展开卡片及升级后主页经专门只读UI审核通过，标题位于摄像头安全头行下方，右侧关闭键完整。20:48:29熄屏投递waiting=true，20:48:31首次亮屏记录截止20:58:31；再次亮屏未延期，到期前15秒系统仍持有VIVO_SUPERX_TAG通知，20:58:31.824撤下。单一持续原生测试通过实际首次亮屏600秒验收，日志`usb-atomic-live.log`、`usb-atomic-before-expiry.log`及`usb-first-screen-events.log`，实际图`usb-phone-island-{resting,expanded}.png`、`usb-atomic-expired.png`。
- 另开ID0预览，真实点击OEM「×」在到期前关闭，`usb-oem-close.log`通过；最终只读逐项比较原有最近作业身份、完成和元数据一致，来源绑定不变，预览持久状态与系统通知均清空。未写入合成作业或真实日历。测试辅助包变动不改变正式生产候选。

## 0.15.37 — 通知详情与交互绘制层级 — 2026-10-21

- 源码 `7c2e0ad`、不可变标签 `v0.15.37` 已推送；正式 Release 已发布为 Latest，页面确认同一提交、三条用户更新及 `Chrona-0.15.37.apk`（2.95 MB）。发布页 https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.37 ，截图 `build/release-0.15.37-published.png`。未追加此前用户叫停的发布后公开下载、手动更新及覆盖安装核对；实体手机未覆盖。
- 处理结果通知将实际保存的事项详情同时用于 ContentText 与 BigText，保留权限检查、点击入口和查看操作；不改自动确认／日历写入策略。
- 新公共 InteractionLayers 按事件提升按压与焦点分支的 translationZ，不更改父容器子项顺序、静态尺寸或 Dock 弹簧参数。固定主页、宽屏及工作区导航分支优先。释放回落结束、失焦及脱离恢复原值；修正 Android 脱离回调期间 isAttachedToWindow 仍为真的时序。
- `NotificationLayersDeviceCheck` 仅允许独立 v33 官方模拟器，使用合成任务及已保存候选，不调用 AI、不写真实日历。通过真实服务通知入口与 NotificationManager 验证正文／展开内容、时间地点提醒备注及详情 PendingIntent；实际 SystemUI 展开和折叠截图。收件箱首尾长按、取消、拖出、键盘焦点、邻居分支 Z 与固定 Dock，附 30 次非累积、双状态、非零基线恢复及脱离检查。浅色 1080×1920 density411 font1 为原 Dock，137 条通过；深色 1080×2160 density480 font1.45 为原紧凑适配，136 条通过。日志 `build/notification-layers-device-{light,dark}.log`，9 张实际截图 `build/notification-layers-evidence/`。系统折叠高度仅容纳摘要，展开直接显示全部本通知内容。
- Dock 离线 180 条、公共 UI 63 条通过；前者仅替身逻辑检查，不代替上述真实触摸。初次原生层测试发现脱离 pin 未恢复 Z，已修复并双主题复验；通知 UI 查询改用实际可访问树遍历定位系统展开控件，未改产品语义。
- 独立 UI 初审发现以 scale 阈值恢复层级会早于惯性／尾部结束；改用实际 tracking、followPosted、coastSelection 状态，在安排下一帧后更新层级。新增真实末项双向甩出与每个 preDraw 断言，覆盖 scale<1.001 但仍运动的后段，最终恢复；浅色最终 138 条，新增 `inbox-late-inertia-light.png`。深色紧凑分支 136 条、关闭系统动画 136 条通过；关闭动画日志 `build/notification-layers-device-reduced.log`。专门只读 UI 复审通过，无剩余缺陷。
- 正式199／0.15.37双构建与双lint通过（`build/release-01537-formal.log`），quick7/7通过。正式候选 `build/candidates/0.15.37/Chrona-0.15.37.apk` 为3089194字节，比.36增加1784字节。原签名、官方包名／版本、release不可调试通过，`build/release-01537-{signing,debug-signing,badging}.log`。不更改工具链／混淆策略，不覆盖手机或正式模拟器包。修改前副本 `.local-backups/notification-and-interaction-layer-20261021/`；发布状态见当前 HANDOFF。不恢复此前叫停的发布后额外核对。

## 0.15.36 — 课表首帧高光与课堂加载布局 — 2026-10-21

- 课表首次render前先解析来源，避免null来源先显示导入、随后切到浙大的虚假高光动画；保留导入深链、学校停用默认来源及真实手动切换动画。课堂本地读取与远端刷新不再插入临时加载文字，使用原位刷新／取消图标；失败与取消消息路径保留。
- 实际原生启动还发现布局期重建网格后，滚动恢复的preDraw等待只重绘而不安排新布局，可能阻止首帧。现安排body重新布局，并只恢复／等待实际挂载的横向视口；独立UI初审指出的仅特殊安排分支未挂载控件等待P2已关闭。
- 独立v33模拟器198两主题各29项通过：学校两次新打开、导入深链及学校停用的创建后／首个preDraw精确高光位置；真实来源触摸的中间动画、归位和同实例；单次特殊安排首帧与窗口焦点；阻塞实际worker的CACHE／REFRESH加载、无临时状态行、日期栏顶部起止不变、真实取消及错误反馈。浅1080×1920／density411／字体1，深1080×2160／density480／字体1.45（360dp）；日期栏顶部309／355px保持不变。`build/workspace-first-frame-{light,dark}.log`；八张最终实际画面 `build/workspace-first-frame-evidence/{timetable-initial,timetable-special-only,classroom-loading-cache,classroom-loading-refresh}-{light,dark}.png`。专门只读UI复审通过，无剩余缺陷或关键证据缺口。
- 初轮夹具误用Activity.onCreate中的生命周期回调、过早触摸与空闲等待；已改用postCreated／首个preDraw／窗口焦点、真实条件等待与异步点击结果等待。未通过改产品门禁规避输入限制。布局期真实等待问题和特殊安排问题在产品修正，未以夹具绕过。首轮lint的连续单行if缩进误报用等价花括号修复。
- 正式198／0.15.36双构建、androidTest与双lint通过（`build/release-01536-formal.log`），quick7/7通过。候选 `build/candidates/0.15.36/Chrona-0.15.36.apk` 为3087410字节，官方包名、版本、不可调试与原签名通过；`build/release-01536-{signing,badging}.log`。R8试验未混入本版。修改前副本 `.local-backups/workspace-first-frame-20261021/`，未覆盖实体手机或正式模拟器。
- 源码 `713db77` 与不可变标签 `v0.15.36` 已推送，正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.36) 已发布为Latest，页面显示同版APK与简洁用户说明；`build/release-0.15.36-published.png`。按用户此前叫停要求，未追加公开下载复验、应用内更新或覆盖升级核对。用户新提出的自动确认通知详情及收件箱聚焦层级／固定Dock顶层要求，明确留到下一版，本版未实现。

## 代码裁剪体积试验（未接入正式构建）— 2026-10-21

- 同197／0.15.35源码，临时R8入口使用平台默认规则、`-dontobfuscate`和`-dontoptimize`，不裁剪资源。APK 3087526→1940720字节，减少1146806字节／37.14%；压缩DEX 2142181→995460字节，全部assets清单及内容逐字节相同。官方包名、版本和原签名通过。试验APK、result.json、签名／清单／usage／configuration均在 `build/r8-size-trial/`；入口 `build/r8-size-trial.init.gradle`、`build/r8-size-trial.pro`。
- 相同裁剪配置的独立v33 Release在模拟器实际通过8项检查：ICS重复／排除日期／时区／时长、二维码、OkHttp TLS初始化、Activity启动、离线MathJax SVG及JavascriptInterface回调。日志 `build/r8-size-trial-runtime.log`；临时夹具源 `build/r8-smoke-source/`，入口 `build/r8-smoke.init.gradle`。测试只进入单独androidTest包，未添加影响产品裁剪的测试保留规则。
- 初轮测试runner注册与异步回调等待已修正。loopback明文HTTP受原策略拒绝，未放宽策略，最终仅验证TLS初始化，不声称外部网络请求通过。biweekly包含反射构造和字段枚举，正式接入前仍需检查动态边界；有限路径通过不代替完整发布验收。
- 正式配置与公开资产未变。独立v33包及测试runner已恢复，正常Release经原构建入口恢复至3087526字节；未覆盖正式模拟器或实体手机。恢复材料 `.local-backups/r8-size-trial-20261021/`。

## 0.15.35 — 课堂下载历史与学生重要事项 — 2026-10-21

- 完整素材下载后按课程／课堂身份归入历史并从课堂列表排除；下载中断不归档，草稿／完成总结优先并去重，保留共享历史与账号隔离。新下载完成时将课堂身份与完成时间一同原子写入候选素材清单，已有下载按当前保存目录索引。历史不受远端回放状态变化阻止，仍执行学校账号门禁；缓存先呈现，远端刷新失败不遮住本地内容。
- 用户确认两轮共用提示词加入全部十类学生重要事项，前八类优先，区分明确要求、参考建议与暗示；仅明确行动安排进入原有提取流程。流水线版本8使新请求采用新规则，已有总结保留。真实Java／循环HTTP166项通过，覆盖完整／中断素材、实际prepare保存身份、目录移除后历史可达、去重／账号隔离，以及实际两轮请求完整十类、优先级和不确定性要求；`build/classroom-history-and-attention-check.log`。未调用真实付费AI，不以请求断言代替模型输出质量判断。
- 正式原生隔离包197两组各17项通过：浅色1080×1920／density411／字体1；深色1080×2160／density480／字体1.45（360dp）。覆盖课堂排除、历史状态、远端未就绪但缓存可打开、真实触摸与返回、过滤后50＋4分页、非零滚动位置本地刷新与详情返回保留、未变条目及选择栏实例。后台完成后真实等待定时刷新，浅色29982ms／深色30072ms进入历史。日志 `build/classroom-history-native-{light,dark}.log`，八张本轮图 `build/classroom-history-evidence/files/classroom-{list,history,downloaded-detail,history-refreshed}-{light,dark}.png`。
- 初轮夹具误读隐藏周表、空contentDescription，以及重复ActivityMonitor返回旧Activity；修正可见范围、空值处理与每次启动前独立monitor后通过，未为这些夹具问题修改产品。合成目录和所建课堂在测试后恢复／删除，不写入手机账号或日历。
- 197／0.15.35正式debug／release、androidTest与双lint通过，`build/release-01535-formal.log`；quick7/7通过。候选 `build/candidates/0.15.35/Chrona-0.15.35.apk` 为3087526字节，官方包名、197版本、关闭调试及debug／release原签名通过，`build/release-01535-{candidate.json,signing.log,debug-signing.log,badging.log}`。专门只读UI审核及非零滚动补充复审均通过，无剩余缺陷或验收缺口。派发工具不支持角色参数，按只读check职责约束，未覆盖或声称固定角色模型；未安装实体手机。

- 源码 `a17b88d` 与标签 `v0.15.35` 已推送；首次GitHub上传失败并超时，后续成功上传同一候选并正式发布为[Latest](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.35)，实际页面显示APK资产及修订后的用户版更新说明；截图 `build/release-0.15.35-published.png`。用户明确要求直接发布并停止追加核对，因此未追加公开下载复验、应用内检查与正式196→197非空覆盖升级测试。前置备份 `.local-backups/release-01535-20261021/upgrade-before.zip` 已保存，正式模拟器及实体手机未被覆盖。

## 0.15.34 — 重复系列残留清理 — 2026-10-20

- 用户发现三个 `重复验收 <时间戳>` 系列未清干净，并明确允许删除这三个系列的全部实例。真实手机Provider确认9条例外仍引用已缺失的三个主ID；按已确认标题、所属日历、主ID和实际行ID联合限制删除。全量433→424条，剩余424条完整行逐项一致，三个标题及原主ID／例外查询均为空。私有原始快照和修改前副本仅保存在 `.local-backups/calendar-fixture-cleanup-20261020/`。
- 根因是 `CalendarStore.deleteEvent` 仅删除主ID，依赖Provider级联清理；此手机Provider留下改期／取消例外。源码改为在同一自有日历内单次删除主ID及其 `original_id` 例外，主记录缺失仍能清理其例外，保留跨日历隔离。隔离官方模拟器实际创建、跳过、改期再删整系列，15项通过，确认主行和全部例外为零、二次删除返回false；`build/calendar-cleanup-native.log`。既有重复边界20项及实际生产SQL的系列、孤儿、陈旧ID、普通事件和跨日历隔离回归通过，`build/calendar-cleanup-regression.log`。
- 重复与浏览器写入夹具首行调用隔离守卫：拒绝实体手机、正式／旧validation包、真实学校账号、外部同步及已有LAN会话。实际Java守卫16个场景和写入前调用顺序检查通过，`build/fixture-isolation-check.log`，已纳入full。
- 正式debug／release、androidTest编译和双lint通过，`build/calendar-cleanup-formal-build.log`；日历链接原有SQL专项通过。手机正式195未替换，最后成功观察8756仍处于前台；随后USB设备断开。真实手机网页经手动刷新确认仍连接、三个测试标题为零、本周23条正常日程保留，`build/phone-calendar-cleanup-browser.json`，实际截图仅保存在上述私有恢复目录。手机暂存的 `/sdcard/chrona-cleanup-window.xml` 与 `/sdcard/chrona-cleanup.png` 因USB断开尚未移除，重连后仅清理这两个验收文件。
- 196／0.15.34正式debug、release、androidTest及双lint通过，`build/release-01534-formal.log`；quick 7/7通过。候选 `build/candidates/0.15.34/Chrona-0.15.34.apk` 为3085598字节，官方包名、196／0.15.34、关闭调试及debug／release原签名一致通过，记录 `build/release-01534-{candidate.json,signing.log,badging.log}`。隔离Provider和SQL证据对应同一删除实现，仅版本元数据变化。
- 源码 `d3c0dc5` 与不可变标签 `v0.15.34` 已推送，正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.34) 为Latest。公开API非Draft／非Pre-release，下载资产3085598字节；公开APK实际196／0.15.34、不可调试和原签名通过。分发包 `build/distributions/0.15.34/Chrona-0.15.34.apk`，证据 `build/release-0.15.34-{public.json,public-apk.log,public-signing.log,published.png}`。CLI直连GitHub连接重置／超时后，沿现有系统代理作本次命令路由完成推送，未改全局Git／代理配置。
- 正式模拟器195实际显示发现0.15.34；公开196覆盖成功后显示已是最新。覆盖前后完整备份比较15张表结构与全部行、1条收件／1个候选、设置、课堂文档／图片字节一致，仅导出时间变化；日志 `build/release-0.15.34-{old-update,current-update,upgrade-preservation}.log`，副本 `.local-backups/release-01534-20261020/`。实体手机未覆盖新版，现有LAN未被重启。

当前源码版本为 **0.15.36 / 198**。下面记录近期版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.33 — 缺省执行区间、手机授权与课堂阅读 — 2026-10-20

- 普通 `task` 原始起止均空且 due 非空时派生 due−1800000ms 至 due，原始字段、编辑器、同步、Provider 写入与重复锚不变；学校作业仍为截止点。真实模型／首页／SQL、已有重复、Agenda、Widget 与 TaskStore 检查通过；新检查 `todo_due_default_check.py` 已纳入 full。主代理日志 `build/todo-due-default-root.log`，其余受影响检查有效结果沿用本轮执行记录。
- 普通待办原生浅色 67、360dp／系统字体 1.45 深色 69 项通过：三类首页卡片、列表完整区间、实际触摸进入详情和返回、读取不回填、真实 Agenda／Widget 模型；launcher 宿主未测。日志 `build/todo-due-native-{light,dark-narrow}.log`，截图 `build/01533-ui-evidence/`。
- 课堂总结阅读移除计时与来源导航，保持来源 JSON 和导出完整。两组原生各 29 项通过：八个公式 SVG、x-height 压力、长公式真实横向触摸、引用图真实点击／放大、无重复图片及 ZIP 原始链接保留。日志 `build/classroom-reader-native-{light,dark-narrow}.log`；深色 360dp／系统字体 1.45。图片链离线 146／10 项及五个补充探针通过，`build/classroom-image-reference-audit.log`；四份实际 ZIP 零张分析图片不足以判断原因，AI 图片选择策略保持不变。
- 授权安全离线 83 项、独立 21 项及并发 30 次仅一次 session 通过。独立审查发现无效 poll/cancel 触发通知回调的 P2 后，改为真实消费／取消才通知；真实 Android HTTP 回归包括十轮无效调用／重放不增加回调，`build/lan-01533-http-integration.log` PASS，局部安全复审确认关闭。
- 授权浅色和窄屏大字体深色原生主流程通过：真实生产服务／HTTP、前台允许与拒绝、网页取消自动关闭、后台8757通知不抢前台、系统通知真实触摸进入确认、单次 Cookie、停服断连和重启后旧凭证撤销。日志 `build/lan-approval-native-{light,dark-narrow}.log`。最初失败为模拟器 Wi-Fi 未连接、旧夹具节点／坐标／通知检索问题；保留旧包数据，另建 `.followup.validation.v33` 后完成实际验收。
- 浏览器实际 CUA 加载生产资源与本地合成 API，验证取消、重试、拒绝、批准和手输码，列表／详情显示跨日 23:40—00:10，编辑原始起止仍空。桌面深浅、360px 列表及 360px／body22.5px 深色授权图已目检，无横向溢出；合成 CSS 字号不冒充系统字体。实际主题按钮深色断连重试的核对码 #00b1ad 对 #252629 对比 5.686:1。受控 API／计时器执行真实授权函数 14 项、主页节点复用 87 项通过；整套旧 CLI 浏览器矩阵未重跑。
- 正式 Debug／Release 与双 lint 通过，`build/release-01533-formal.log`。正式候选 3085590 字节，官方包名、195／0.15.33、原签名通过，包内 LAN 三项与课堂两项资源逐字节一致；记录 `build/release-01533-{candidate.json,signing.log}`。121147ms 真实等待后的请求过期、原生页关闭和通知撤下通过，`build/lan-approval-native-expiry.log`；专门独立 UI 审核通过。
- quick 7/7 通过；Widget 和数据完整性检查从生产拼接表达式解析共享 SQL 常量，保持 quick 只需 Python，`build/release-01533-quick.log`。
- 源码 `5b33193` 与不可变 `v0.15.33` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.33) 为 Latest，公开 API 非 Draft／非 Pre-release，APK 3085590 字节。公开下载实际官方包名、195／0.15.33、原签名通过；分发包 `build/distributions/0.15.33/Chrona-0.15.33.apk`，证据 `build/release-0.15.33-{public.json,public-apk.log,public-signing.log,published.png}`。
- 正式模拟器194实际发现新版；公开195覆盖成功后显示已是最新版本。前后完整备份比较保留全部15张表结构与全部行、1条收件／1个候选、设置、课堂文档与图片字节，仅导出时间变化。日志 `build/release-0.15.33-{old-update,current-update,upgrade-preservation}.log`，副本 `.local-backups/release-01533-20261020/`。
- 用户确认手机升级并重开LAN。USB调试打开后重新插拔并选文件传输即正常识别，未改驱动。正常备份界面确认密钥未勾选后导出当前ZIP，3586147字节、39条收件、81个候选、56个归档项；原APK也保留于 `.local-backups/release-01533-20261020/phone/`。正常系统确认后公开195覆盖成功，`build/release-0.15.33-phone-install.log`，手机实际版本195／0.15.33。
- 手机从正常设置入口重开LAN，8756前台服务保持运行。新网页自动申请并实际弹出原生授权页，两端核对一致后由用户在手机允许。真实首页在可见状态下间隔79423ms，时间线50条、明日7条及总58个独立展示键完整保留；随后实际手动刷新仍保持相同计数，无横向溢出。报告 `build/release-0.15.33-phone-home.json`；实际画面保存在上述私有phone恢复目录。服务与已授权网页继续可用，用户数据未上传到公开发布页。

## 0.15.32 — 局域网主页重复节点 — 2026-10-20

- 旧版全局键在时间线和最近记录间重复；下一轮 `replaceWith` 搬移同一个节点，使时间线只剩日期标题。相同输入最小复现为 3 条变 0 条。`entry` 按主页区域区分显示键，复用循环用集合保证每个旧节点只消费一次；原数据、接口、排序、十秒刷新与动效不变。
- `checks/lan_dom_reuse_check.py` 生产片段回归 87 项通过，覆盖连续五轮、两区重复显示、节点身份、标题更新、点击目标、合法空状态及重复键保护，日志 `build/lan-home-reuse-core.log`。检查已纳入 full；quick 7/7 通过，日志 `build/lan-home-reuse-quick.log`。
- 实际浏览器加载生产 HTML／JS／CSS，合成 API 同时提供两区相同记录：时间线 4、最近记录 4、待办 1，9 个独立显示键。多轮真实十秒轮询与手动刷新后数量及日期保留；更新标题在两处出现，学校只读详情与本地候选详情入口正确。桌面深浅、360px 浅色与 360px／22.5px 页面字号深色实际图已检查，后者仅是相对 15px 的页面字号 1.5 倍。报告 `build/lan-home-reuse-browser-report.json`；原有整套 CLI 浏览器矩阵未重跑。
- 正式 Debug／Release、lintDebug／lintRelease 通过，日志 `build/lan-home-reuse-final-formal.log`。候选 `build/candidates/0.15.32/Chrona-0.15.32.apk` 为 3081046 字节，官方包名、194／0.15.32、不可调试及原签名通过；包内 LAN 三项资源与所检查源码逐字节一致。签名记录 `build/release-0.15.32-signing.log`。
- 专门 UI 独立审核已核对代码、回归及实际浏览器图，无剩余代码／界面缺陷。用户明确同意覆盖手机并重开局域网，系统确认后安装成功，实际为 194／0.15.32；通过正常设置界面重新开启，服务处于前台运行。手机真实网页配对在十分钟期限后返回失败，尚未进入主页；随用户新增手机确认授权流程补验真实连续刷新。恢复副本 `.local-backups/lan-home-node-reuse-20261020/`。
- 发布源码 `8457f4f`、不可变标签 `v0.15.32` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.32) 为 Latest，公开 API 为非 Draft／非 Pre-release，APK 3081046 字节。公开下载实际官方包名、194／0.15.32、不可调试及原签名通过，分发包 `build/distributions/0.15.32/Chrona-0.15.32.apk`；记录 `build/release-0.15.32-{public.json,public-apk.log,public-signing.log,published.png}`。
- 正式模拟器 193 实际发现 0.15.32，覆盖安装公开 194 后显示已是最新版本。前后完整备份比较通过：15 张表结构与全部行、1 条收件／1 个候选、设置、课堂文档和图片字节保留，仅备份导出时间变化。日志 `build/release-0.15.32-{old-update,current-update,upgrade-preservation}.log`，恢复副本 `.local-backups/release-01532-20261020/`。

## 0.15.31 — 操作胶囊系统日程高光 — 2026-10-20

- `CalendarVisibilityToggle` 原来先安装专用圆形涟漪，`UiStyle.pressable` 检测已有 RippleDrawable 后保留它，造成同组高光不同。删除该圆形遮罩及圆形选中底色，直接复用现有 `ControlHighlightMask`，图标颜色继续表达开关状态。主页调用者同步移除废弃的布尔配置；公共高光、胶囊与按压动效参数未改。
- 核心 35 项通过，覆盖连续 tint／图标 pulse、快速反向、重复值、减少动画、脱离及权限同步拒绝，日志 `build/header-toggle-core.log`。周日程资源／路由及实际双轴手势替身检查通过，日志 `build/header-toggle-week.log`。
- 正常浅色和 320dp／字体 1.45 深色两组原生检查通过，日志 `build/header-toggle-native-{light,narrow-dark}.log`：每组实际按压主页 3、周日程 3、日程列表 2 个动作，验证公共填充遮罩、真实 DOWN／CANCEL、取消不切换来源，以及 6 次开关状态／偏好／无障碍／颜色终态与无圆形选中底色。34 张原始截图及按压对照图在 `build/header-toggle-captures/`。启用学校的主页仅临时调用实际标题构建方法，未运行学校生命周期或真实网络；测试数据仅在隔离包创建并清理。
- 检查夹具按压捕获改为等待原生延迟 pressed 后直接截图，避开长按 tooltip；开关验证等待实际颜色终态而非固定延时。生产实现未因这些夹具修正而改变。最终 androidTest 构建通过，日志 `build/header-toggle-final-test-build.log`；模拟器显示设置已恢复。
- 正式 Debug／Release 与双 lint 通过，日志 `build/header-toggle-final-formal.log`；实际 APK 官方包名、193／0.15.31、不可调试及与旧版一致的签名证书通过，日志 `build/release-0.15.31-signing.log`。候选 `build/candidates/0.15.31/Chrona-0.15.31.apk` 为 3080998 字节；手机已成功覆盖安装并确认实际版本，未卸载或清除数据。
- 独立 UI 审核已核对公共遮罩、两组按压对照和完整截图、开关状态以及相关检查，未见旧圆形高光、新增裁切或剩余缺陷，本轮 UI 验收通过。
- 发布源码 `94cfe38`、不可变标签 `v0.15.31` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.31) 已设为 Latest。公开 API 为非 Draft／非 Pre-release，APK 3080998 字节；公开下载的官方包名、193／0.15.31、不可调试和原签名通过，保存在 `build/distributions/0.15.31/Chrona-0.15.31.apk`。记录 `build/release-0.15.31-{public.json,public-apk.log,published.png}`。
- 正式模拟器 0.15.30 实际发现 v0.15.31 及下载入口，覆盖安装公开包后显示已是最新版本。前后不含 API 密钥的完整备份比较通过：全部 15 张表结构与逐行数据、1 条收件／1 个候选、全部设置、课堂文档和图片字节保留，仅备份导出时间变化。日志 `build/release-0.15.31-{old-update,current-update,upgrade-preservation}.log`；恢复副本 `.local-backups/release-01531-20261020/`。普通收件附件与桌面小组件没有本轮升级样本。

## 0.15.30 — 课堂滚动、自动状态、重新生成与公式 — 2026-10-20

- 发布源码 `80f2362`、不可变标签 `v0.15.30` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.30) 已设为 Latest。公开 API 为非 Draft／非 Pre-release，正式 APK 3081266 字节；公开下载的实际包名／192／0.15.30、不可调试和原签名验证通过，保存在 `build/distributions/0.15.30/Chrona-0.15.30.apk`。记录 `build/release-0.15.30-{public.json,public-apk.log,published.png}`。
- 正式模拟器旧 0.15.29 通过真实更新页发现 v0.15.30 及下载入口；覆盖安装公开 APK 后显示已是最新版本。升级前后不含 API 密钥的完整备份比较通过：全部 15 张表的结构与逐行数据、1 条收件／1 个候选、设置、课堂文档和图片内容保留，仅备份导出时间变化。日志 `build/release-0.15.30-{old-update,current-update,upgrade-preservation}.log`；恢复副本 `.local-backups/release-01530-20261020/`。普通收件附件与桌面小组件本轮没有升级样本。

- 手机现场复现课堂表格纵向拖动抽搐：外层纵向视口移动了处理双轴手势的横向容器，局部触点坐标形成反向反馈。改为直接复用周日程的层级：固定 `FreeCalendarScrollView`，内部原生 `ScrollView` 承载表格。公共手势实现未改；标题与日期固定，切周使用固定视口横向过渡。
- 正常浅色与 320dp／字体 1.45 深色各 104 项通过，日志 `build/classroom-viewport-reuse-{light,narrow-dark}.log`。覆盖每个 MOVE 的静止容器坐标与单调纵向位移、横向浏览／边缘切周、取消／多指、半途反向、减少动画位置、末行到达 Dock 上方、短周无额外滚动，以及真实时间边界、原位状态重绑、缓存年龄、五分钟失败冷却、不重叠和生命周期清理。截图在 `build/classroom-final-captures/`。
- 用户新授权 USB 调试后，先通过应用导出不含 API 密钥的完整备份并保留原 APK，再以 `-r` 覆盖安装候选，不卸载、不清数据、不调整手机密度／字体。手机原有课堂的公式尺寸和基线已实际对照，课堂表格进行了多次慢速上下拖动；视频 `build/classroom-phone-scroll-final.mp4`，截图 `build/classroom-phone-week-reuse-{top,scrolled}.png`。恢复副本 `.local-backups/classroom-viewport-status-20261020/phone/`。没有向手机注入合成课堂或调用付费 AI。
- 公式专项在相同两组显示条件下各 19 项通过，日志 `build/classroom-math-final-{light,narrow-dark}.log`：8 个公式均成功，TeX x-height 转为 em；模拟异常系统 ex 后公式高度不变，大字体仍自然增大。长行内／独立公式真实横向手势首尾可达，正文不被挤宽，源码／图片／课堂安排入口保留。手机原有线性代数和多项式正文实际显示正常，图像 `build/classroom-phone-summary-final.png` 与 `build/classroom-phone-polynomial-final.png`。
- 重新生成核心 146 项通过，日志 `build/classroom-regenerate-regression.log`；原生 9 项通过，日志 `build/classroom-regenerate-native.log`。已完成轮次新建 run 并实际尝试本地 HTTP；未完成同配置续跑，首轮连接失败时原总结恢复可读。正式 AI 服务的付费生成与内容准确率未实测。
- 快速离线检查 7／7 通过，日志 `build/classroom-release-quick.log`。旧版完整检查仅复用未改变的对应前提，不当作新增 UI 的渲染证据。
- 独立审核指出空周提示跟随宽表被裁掉；改为按视口宽度布局并抵消横向偏移，不重置浏览位置。正常浅色及 320dp／字体 1.45 深色各 7 项通过：X=200 保留、提示完整可见、无纵向范围、最右边缘仍可见。日志 `build/classroom-empty-final-{light,narrow-dark}.log`，截图 `build/classroom-final-captures/classroom-viewport-empty-fixed-{light,dark}.png`。原审核代理局部复审通过，本轮整体 UI 无剩余缺陷或关键证据缺口。
- 最终正式 Debug／Release 与双 lint 通过，日志 `build/classroom-empty-final-formal.log`；隔离 Debug／androidTest 构建通过，日志 `build/classroom-empty-isolated-build.log`。最终 Release `build/candidates/0.15.30/Chrona-0.15.30.apk` 为 3081266 字节、官方包名、192／0.15.30、不可调试；新 Debug／Release 与旧 0.15.29 的签名证书一致，日志 `build/release-0.15.30-signing.log`。
- 手机首次安装最终包被系统拒绝后停止重试；用户亮屏明确允许继续，覆盖安装成功，确认实际安装版本为 192／0.15.30，原有数据可正常打开。手机密度／字体未调整；模拟器检查后恢复 density override 411、font_scale 1.0。

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
