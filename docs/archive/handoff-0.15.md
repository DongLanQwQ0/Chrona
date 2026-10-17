> 历史交接快照。旧方案、旧验证限制与旧待办仅适用于记录时的版本。

# 当前开发：0.15.19 / 181 · 已确认动效下的绘制性能优化

用户同意只优化绘制性能，0.15.18动效基线冻结。只修改UiStyle.AcrylicSurfaceDrawable：嵌套亚克力面板/弹窗内的玻璃底板及关闭亚克力时的底板，在像素输入不变时复用已有软件纹理；独立实时采样壁纸的栏仍每次重绘。bounds、调色、混合度、selected/outline、alpha、colorFilter与亚克力开关会使缓存失效。高光/文字仍独立硬件节点。DockSpring、DockNavigationLayout、DashboardDockChrome未修改。

新增只读原生acrylic_raster验收：RenderNode硬件Canvas录制触发生产背景路径，以Bitmap.getGenerationId统计实际纹理改写。安装前0.15.18相同100次重复绘制改写100次，CPU中位1.308334ms/P90=1.67901；0.15.19改写0次，中位0.006198ms/P90=0.007291。该量度仅包含背景软件栅格化及display-list录制，不含GPU显示、不是全应用帧率。首帧1280×168底板PNG逐像素完全一致，mix/alpha/bounds变化及退出嵌套恢复实时采样均通过。

release/androidTest构建及lint通过，壁纸采样10项、Dock协调36项通过。USB已安装181，7秒筛选栏首尾持有、远点短按与双向120ms甩动录屏，未见白线，高光溢出与尾部保留。证据build/device-0.15.19/，未改真实日程；备份build/backups/20261017-acrylic-raster-cache/。其他全应用卡顿热点未归因，不能宣称稳定120fps。

# 上轮开发：0.15.18 / 180 · 取消松手限速，保留部分越界

按最新明确要求取消点击速度上限。DockSpring.coast直接velocity=speed，删除clickPeakSpeed；releasedMotion正常时间、前后沿、放大、点击无抖动和405/27边缘回弹均不变。绘制层原有tanh允许部分越过轨道、限制最大视觉伸出，不改成框内裁切；检查没有恢复fullWidth内移限幅。

180项生产队列/物理检查通过：25/-18槽每秒实测速度原样传递，±100不限制，1/0.5配置松手同速，预测与实际一致，首尾长按及惯性可越过外框。原极端测试2槽数值上界在取消限速后不再成立（实测2.00124），改验证收敛及视觉tanh仍有界，避免为过旧测试重新限速。release/lint通过，USB安装180；10秒录屏首尾持有及双向快速甩动，可见高光超出外框后回弹，未恢复框内限幅，证据build/device-0.15.18/。

用户随后明确“现在舒服多了”，要求冻结已确认手感，已写ui-style-contract第0.1节；不得继续未经授权调参。本轮之后仅文档和只读帧率观察。设备当前120Hz，postOnAnimation跟随vsync，无固定60fps上限；近期全应用7149帧/256系统卡顿（3.58%），P90=20ms，包含录屏操作，不是选择栏独立统计。物理120子步不是fps封顶。卡顿归因未完成，不得擅自改效果或宣称已优化。

# 上轮开发：0.15.17 / 179 · 远距离点击抖动与松手正常速度

用户选择B/B/B：按下移动、快速放大、前后沿追随；所有栏松手后正常速度，并以最后明确要求保留点击速度上限。修复前生产模型复现：0.5倍栏按住83ms后释放，1槽目标峰值1.056433，越过5.6%后回摆。

DockSpring位置和尾部改临界阻尼解析积分，跟随转归位时只约束接近目标的速度（剩余距离×频率），不跳位置、不取消尾部；普通点击收敛与框外coasting回弹分离。点击频率sqrt180、跟随sqrt1800、尾部sqrt300；同模型点击峰值w/e用于速度上限。releasedMotion在点击/拖动松手后保持到完全收敛，归位、尾部和惯性均用真实dt；纯程序选择仍保留原movementSpeed。coast输入不减半，保留点击速度cap与405/27边缘回弹。放大参数、几何、主页230ms居中/＋460ms退出不改。

180项生产队列/物理检查通过：近/远、1/0.5倍点击不穿过目标，尾部仍拉伸；不同栏释放同速、点击释放同速、点击cap保留。release/lint通过，USB安装179成功，筛选浮层10秒录屏复查类型与日历状态首尾短按、双向甩动，未见目标附近反复回摆，放大与尾部效果保留；取消筛选草稿后返回。证据build/device-0.15.17/，未处理真实日程。

# 上轮开发：0.15.16 / 178 · 居中减半与旧版对比

用户授权仅加快主页选择日程/收件箱后的居中移动，要求高光松手/边缘点击先对比以便决策。本轮DockSpring和高光几何不变。DashboardDockChrome拆分homeFraction（＋）与dockHomeFraction（位置/宽度），非首页居中使用协调时长0.5倍，默认230ms；＋仍460ms。返回首页时长不变。beginDrag捕获Dock实际进度，避免＋未退出完时接管造成位置跳回。36项协调器检查通过，包含半程已居中、＋仍在退、拖动接管和中间帧。

对比基线2c51591 / v0.15.9（参考视频前），当前4343f6e / v0.15.15。生产Java隔离执行记录build/dock-version-comparison/result.txt：80ms按压释放，旧scale峰值1.200、回到1%内656ms；当前1.444、177ms（仅放大层，不含尾部）。同输入25槽/秒，旧25，当前主页11.867，其他半速栏5.934。当前尾部延伸、按下立即移动目标、快放大以及半速惯性共同改变手感；不能把用户感知的收尾拖沓说成放大层本身变慢。旧详情/筛选有框内限幅，本轮不能擅自恢复。待用户选择高光方案。

# 上轮开发：0.15.15 / 177 · 松手速度与即时放大

按最新要求取消松手动量统一乘0.5。DockSpring.clickPeakSpeed由实际点击弹簧180/20计算全轨道跨度的峰值，coast对实测速度做对称上限，预测共用同一路径；按压跟随、自动选中速度及透明模糊退出不变。边缘回弹180/18以1.5倍时间压缩为405/27，保留阻尼比。

所有DockNavigationLayout在ACTION_DOWN调用press(true)，删除previewPress，不再等待拖动或长按才开始完整放大；松手仍从当前尺寸回收。173项生产队列/物理检查与release/lint通过；速度上限对1/2/4槽跨度与实际点击积分峰值误差小于5%，中速不减半，回弹穿回边缘至少快20%。USB真机录屏证据见build/device-0.15.15/。

# 上轮开发：0.15.14 / 176 · 跟手、短按与退出节奏

用户否定0.15.13的强阻尼手感。恢复0.15.12的边缘参数（接近范围0.6槽、阻尼18、回拉180/18），仅松手速度乘0.5，预测使用同一模型。12槽/秒从边缘释放峰值0.17602槽。手指跟随时前沿/尾部使用真实dt；自动选中仍保留首页1、其它0.5倍速度。

按下previewPress使用1.16倍小预览和1.03倍轨道，beginDrag（含长按入口）才启用1.5/1.1完整放大。尾部最大延伸随强调比例连续从0.12宽度到0.75宽度变化，不加框内限幅。退出至少460ms smoothstep，setHome同目标时保留运行中的动画，避免240ms页面提交截断。

168项Dock及34项协调器检查通过，release/lint成功。USB vivo V2527A已安装176版，18.32秒实际录屏覆盖端点点击、900ms双向拖动、140ms双向甩动和800ms长按；另8秒录屏检查＋退出/返回，未见十字亮点或矩形模糊边。证据build/device-0.15.14/。临时取消诊断确认退出duration=460且无提前cancel，日志已移除。未修改真实日程。人工手感以用户使用反馈为准。

# 上轮开发：0.15.13 / 175 · 模糊采样与边缘大阻尼

用户反馈＋进入时四方向亮点、模糊黏在边界，以及高光越界回弹过大。DashboardDockChrome 最大模糊18dp保持不变，RenderEffect 的采样由 CLAMP 改为 DECAL。DashboardActivity 为移动/宽屏捕获按钮外包层增加四边36dp透明padding，并等量扩大外包层、反向补偿右/下margin，按钮仍为52/56dp且屏幕位置和点击区域不变。模糊作用于有透明余量的包装层，按钮自身按压仍独立。

DockSpring 仅调整coasting阶段：最后0.25槽位内增加70基础边缘阻尼及5*abs(velocity)速度阻力，越界回拉刚度600、阻尼80。普通滑行、按压、框外绘制几何与0.5倍速度不变，不引入框内限幅。测试12槽/秒甩动越界峰值0.06764槽；100槽/秒、两侧、两种移动速度均低于0.25槽并收敛。

离线Dock166项、协调器33项通过，release/debugAndroidTest构建与lint通过。用户重新授权USB后，vivo V2527A成功覆盖安装0.15.13，系统录屏16.32秒检查首页/日程/收件箱往返及两侧快速拖出松手。＋进出密集提帧未见四方向十字亮点或矩形模糊边缘；Dock居中与返回、高光放大溢出和边缘收敛可见。私有证据保存在build/device-0.15.13/，未上传；未操作真实日程完成或写入。

# 上轮开发：0.15.12 / 174 · 撤销框内限制，调整速度、模糊与居中

用户明确纠正：高光可溢出轨道，不能擅自改成向内挪、框内限幅。删除 DockNavigationLayout.updateHighlight 中 fullWidthMotion 分支的 span/left clamp，保留用户之前要求的惯性边界阻力。筛选 ScrollView 扩至原面板的侧边留白、内部padding抵消布局变化，关闭中间子裁切，保留真实视口边界。没有缩窄选择栏、改字号或增加字段占位。

DockSpring movementSpeed 只缩放位移与tail的积分时间，按压和收回仍用真实时间；公共选择栏默认0.5，主页 navigation 明确1.0，预测落点沿用该速度。DashboardDockChrome 的开合统一 UiMotion.SETTLE，按钮以平滑非线性alpha/scale配合Android31+ RenderEffect模糊，隐藏/完整恢复清除效果。Dock 中心和宽度分别连续过渡：非首页居中，仅增宽6%且最多16dp，不再固定左端拉长。

ui-style-contract 新增第0节，明确禁止未经授权的交互与视觉限制，并撤销此前「首尾向内扩展」规范。离线Dock162、筛选17、协调器28项通过；构建/lint记录见验证历史。本轮无在线设备，实际溢出、模糊与速度观感待真机验收。

# 上轮开发：0.15.11 / 173 · 首页快捷操作与 Dock 联动

用户明确「首页显示」：仅 HOME 显示底部记录＋，SCHEDULE/INBOX 隐藏。DashboardDockChrome 使用一个 homeFraction 协调捕获按钮包装层的缩放、透明度、可点击性及无障碍入口，独立于按钮自身的按压反馈。隐藏为 INVISIBLE，位置由 Dock 的真实宽度占用；不缩放导航文字。

手机导航保持左36dp，右边距从首页104dp连续变为36dp，隐藏后左右对称；底部和高度保持不变。每帧只局部测量/布局 navigation，不重建页面或改动内容安全区。底部父容器每次 onLayout 后恢复当前比例，支持旋转/inset 与非首页直达。宽屏不扩缩侧栏，仅控制同一记录按钮包装层。UiStyle 的 Dock 背景纹理按256px块增长并复用，使用实际绘制区域裁出1:1纹理，避免每个宽度帧新建位图；高光与文字独立节点不变。

DashboardActivity 的 body drag、commit/cancel、Back 历史与恢复使用同一协调器；动画取消先移除回调，反向从当前比例开始；完整页面提交后明确应用终态。系统动画关闭（包括动画中途关闭）立即到终态。销毁时取消协调动画。

离线协调器22项与历史27项、构建/lint结果见 docs/validation-history.md。手机不在线，新联动未真机验收。

# 上轮开发：0.15.10 / 172 · 视频参考 Dock 动效

用户参考录屏 Screenrecording_20261017_140740.mp4，逐帧拆解静止/按住/拖动/反向/松手。静止高光约125×98px，按住高度约140px，拖动宽度随前后沿距离变化。分析帧留在忽略的 build/dock-reference，未上传私有录屏。

DockSpring 新增 tailPosition/tailVelocity，前沿使用更快的连续跟随，尾部独立追赶。DockNavigationLayout 使用前后沿的实际像素距离有界延长高光，不再将按压和速度宽度相乘；cornerRadii 反向补偿 View scale，保持物理端帽圆润。文字及图标仅按覆盖程度连续着色，不拉伸。按下即预览但选择仍由原 click/drag 提交，取消回原选项，多指与父分页边界保持。

浮动轨道有界偏移6dp/4dp，全宽表单轨道不作水平偏移；motionHost 补足完整外沿与偏移包络，全宽轨道仍满宽且高光向内增长。按压约100ms即可明显放大，松手响应时长是按压的1.5倍，小点击脉冲与95%滑动高度保留。UiStyle 亚克力白线修复未变。

离线状态与几何回归、构建记录见 docs/validation-history.md。本轮没有在线设备；几何帧预览来自生产状态在测试替身上的输出，不能视为 Android 实际渲染验收。原生 interaction_layout 验收入口已更新为新尾部状态，仍需真机检查圆角、首尾长按、快速反向与白线。

# 上轮开发：0.15.9 / 171 · 作业时间线与详情收尾

作业有执行开始时按执行时间进入首页，仅截止时以截止里程碑进入；普通待办仅截止不改变原行为。首页卡片只压缩内部留白。独立 publication=3 表示未写入，筛选 UI 映射为 [0,3,1,2]，原待确认=1 含义不变；未写入同样可批量写入。

CourseSourceMatching.mergeConsecutive 仅合并同日、同来源、同标题地点且间隔不超过 30 分钟的课程，保留首节身份和合并备注。提醒读取保留当天已结束节次，先合并再按隐藏状态和提醒槽筛选，避免课间身份变化。系统课程详情通过快照显示合并备注，原 Provider 与课表数据不改动。

详情类型使用 fullWidthMotionHost。过期活动操作 GONE；作业开始清空同时清空结束，结束清空保留开始，空结束选择器只借用开始作为初值。清空全天执行后保存恢复本地时区且不改变同步截止毫秒值。学校截止只读，手动创建作业截止可清空。AI 提示词 schedule-v9 明确分类、执行/截止和单一时间主锚，未调用真实模型。

release、debugAndroidTest 和 lintRelease 构建通过；离线检查详见 docs/validation-history.md。手机已离线，本轮新增界面与课程课间原子岛尚未真机复验。0.15.8 真机勾选和选择栏证据仅适用于上轮候选。

# 上轮开发：0.15.8 / 170 · 作业分类、批量写入与局部动画

作业使用持久化 category=homework，沿用 isTask()/isTaskLike() 的截止、完成、提醒、日历和网页编辑行为。SQLite v13 将作业映射及 zju-homework 收件下的旧 task 转为 homework，ID、关联、截止、完成及历史不变；备份支持 v13，TaskCodec 接受该类型，同步设备一并升级。筛选直接按分类查询，首页待办统计同时包含 task/homework。

HomeworkLinks 从受信课程 URL 和 courseId 构造当前作业移动/桌面地址；缺少标识禁用，禁止退回首页或猜测 ID。按钮仍受折姜星辰总开关控制。批量写入只在收件箱待确认和日程待安排多选中提供，工作线程逐条重新读取/验证/提交，跳过不可写项，复用相同事件，仅清理本次新建但关联失败的事件。

卡片处理继续使用 DashboardCardReconciler 保留节点并 ChangeBounds 上移；补齐首页轮询和日历回调的局部更新路径。UiStyle 只将 Dock 背景板绘入复用位图再合成，高光及文字保持独立硬件节点。真机系统录屏证实原白线；改圆角 API、轮廓环和去掉描边均无效，整栏软件层消线但会限制溢出，因此未采用。最终仅背景软件画布在同手势录屏 252 帧中未见中线，高光放大/拖动保留。证据 build/device-white-line/。

设备 10.195.63.197:37889 已成功更新修复包及测试工具。详情页三次往返、临时作业按钮布局、原子岛临时提醒真实点击完成并消失均通过，fixture 均清理；不涉及真实日程完成或远程作业提交。

completion_card_motion 原生验收通过：两张下方卡片同一View各上移514px，同一ScrollView且scroll1145不变，完成已落库、无Provider关联；三条临时task均清理。检查入口在 DeviceUiChecks，新测试类只管理自己的fixture。

# 上轮开发：0.15.7 / 169 · 详情返回空白

TaskDetailActivity.addCandidateCarousel 的 post 回调原来引用可变 Activity.content/candidatePageIndex。buildAdjacentPage 会暂时切换这些字段再恢复，导致相邻页在稍后执行时误判 parent、跳过 scrollTo；按需构建的第二条存在，但实际偏移仍为零，显示空 slot。现在捕获 ownerContent/ownerTaskId/initialPageIndex；一次恢复等待实际页宽，在 onLayoutChange 和 post 共用入口。滚动监听和相邻预建仅在该 owner 当前激活时修改 Activity 状态，保留按需编辑器与草稿。

新增 detail_carousel_restore 原生验收，构建有两条日程、记住第二条的相邻页，先让 post 在原任务激活时运行，再提交页面，连续往返三次检查真实横向偏移和标题可见，不以额外触摸激活绘制。修复前真机复现 scrollX=0、应为1093；失败断言最初在主线程终止进程，已改为测试线程报告并仅清理本验收命名的临时数据。

离线生产恢复回调/所有权9项（含旧全局容器负例）、详情编辑器17+LAN7、草稿保持与release/androidTest构建、lint通过。设备拒绝修复包和测试工具安装，已异步询问是否允许重试，未绕过拒绝。当前手机仍旧代码，不能记为修复真机通过；获准后安装 app-release.apk 和 app-debug-androidTest.apk，运行 check=ui phase=detail_carousel_restore，再返回首页。该专属验收会清理此前断言终止遗留的 device_check/详情横滑验收0或1记录，绝不能泛删其他测试或真实任务。

# 上轮开发：0.15.6 / 168 · 作业编辑边界、课表导出与重复匹配

作业源字段（标题/正文/due/category/record）由 TaskStore.requireCandidateEditAllowed 与 updateCandidateDescription 拦截，本地个人执行/地点/提醒保留；平台 submitted=true 不能恢复为未完成。LAN 在触碰 Provider 前先校验，作业 capture 禁止 replaceCandidates 重新解析。详情源字段只读、备注可复制；源网页双按钮位于卡片底部，master 关闭隐藏。待办完成/恢复移入标题行 48dp 圆形图标，顺序新建/完成/删除。

ScheduleSourceSettingsActivity 手动课表导出使用 CourseCalendarExport，本地导入全部学期与当前学校账号缓存可分别选择，不进行联网。NFKC/空白规范化标题地点+实际起止+全天为重复身份；学校备注优先，每个有课程月份查询所有日历实例（不受可见/来源开关限制），相同跳过。写入无额外提醒，不更新/删除其他日历。部分失败保留已写入，重试去重；这是手动副本而非持续同步。无新持久开关，备份文档已补边界。

DuplicateCandidates 不再匹配 calendar ID、reminder、zone 名称、置信度、自动生成标志；有日期条目按业务字段与完成布尔值合并，未知日期和记录仍按备注保护。MergeLedger 保留来源快照与本机 Provider 关联 ID，原始系统事件不删除。实体合并页明确只扫描 Chrona 候选，独立课表和系统日历仍走来源展示去重。

# 上轮开发：0.15.5 / 167 · 作业自动写入系统日历

ZjuHomeworkCalendar.retryPending 在现有本地事务数据上标记当前启用账号/收件的未完成、有时间、未关联作业，串行补写系统日历。新建后绑定 candidate，持久 pending 位处理权限/provider 故障；用 Chrona 日历内相同标题起止/全天及备注地点检查恢复重试的重复事件；关联失败补偿删除本次新建。已完成未关联和无时间不新建，关闭来源不投递，已有链接继续按老师变更更新、个人执行时段不变。权限入口在折姜星辰页，授权回调本地重试，不重新调用学校或 AI。真实 SQLite 21 项通过。新增 auto_click 验收按当前可见按钮节点注入触摸，0.15.4 正式 APK 已验证秒数修复后点击完成并移除通知。

# 上轮开发：0.15.4 / 166 · 作业打开、来源与首页交互

TaskDetailActivity 原来为统一作业 capture 一次创建全部编辑表单，现仅创建当前页、首帧后预建相邻页，访问过的编辑器保留草稿和选择栏手势优先权。无合并记录时跳过逐候选来源查询，避免稳定 ID 的无效写入。真机 60 项合成收件（第 31 项）打开 1931ms/60 编辑器 → 781ms/3 编辑器，单次对照，不代表 P95。真实作业未删除或提交。

ZjuClient 标题为课程名 · 作业名，保留远端 key；旧数据下一次正常同步更新。ZjuAcademicCalendar 从实际字段生成课程 notes，ZjuTimetableStore、CourseAgenda 和 CalendarOccurrence.description 传递；旧 CalendarOccurrence 最后一参仍是 accountName。外部课程详情读取本地独立线程，不排在 ZjuSync 网络队列后。

DashboardCardReconciler 保留不变卡片，完成后 ChangeBounds/Fade 动画上移，ScrollView 和位置保留；写入失败不移除，期间门控轮询快照。展开箭头只改 RotateDrawable level，修复整按钮旋转破坏高光坐标。作业/系统日程/设置标题按钮独立 48dp、间距 8dp；master 关闭隐藏作业入口/分类并清理旧筛选。

筛选栏 fullWidthMotionHost 延伸内容区域，板 X 不缩放，首尾高光向内增长，长按仍 1.5 倍，标签按字宽。来源内容默认全部、每页 50，可切周；系统全部按 Events 每系列一次、周按 Instances，keyset 分页；导入包含非当前学期，不能跳转当前课表时只读详情。空来源不显示。

FeaturePreferenceBackup 为提醒外观/来源、首页展开、周图导出明确白名单，完整备份嵌入配置。SchoolDataBackup 保存匿名绑定、选项和课表缓存，不含凭据；恢复 master 关闭、失败恢复原设置与快照。详见 docs/backup-coverage.md。

真机发现 CourseSourceMatching 的 (?U) 正则在 Android ICU 崩溃，已改为显式 whitespace/separator 匹配。修复后只读源测试 imported=16/merged=18/calendar=48，interaction_acceptance 通过。构建 debug/release/androidTest 与 lintRelease 通过。设备勾选临时任务已实点通过并移除通知。秒数冻结原因是 vivo 的 getActiveNotifications 副本去掉 operation 等字段；AtomicCountdownUpdates 认可实际 VIVO_SUPERX_TAG，仍核对 when 防串槽。新增去字段/非原子/替换提醒用例，111 项通过；真机截图 102→96 秒持续更新。通话占用胶囊，未测主胶囊/锁屏/长时后台，不能写全形态通过。私有截图和设备报告留在忽略的 build/device-0.15.4，不提交。用户 AGENTS.md、角色 toml、备份不得提交。

# 上轮开发：0.15.3 / 165 · 吉吉思考参数

已定位旧RequestOptions对host仅官方白名单且模型无gpt-6-luna导致medium完全不发送。修复精确三个jiji域名的gpt-6/6.1 luna/sol/astra分支，传reasoning_effort（auto→low、max→high，其它原样）与max_completion_tokens预算，不复制官方schema/cache选项。请求端未换模型或协议。全服务thinking/reasoning_effort被拒绝不再静默降级；其它命名格式/流选项仍一次降级。既有reasoning_content/reasoning文本解析有回归，服务不返回时不伪造。checks/AiRequestCheck.java覆盖域名、六档、误匹配、拒绝和SSE。未读密钥/手机/付费调用，服务实际执行与思考文本是否可见尚未验证。备份build/backups/20261016-jiji-reasoning。docs/ai-request-notes.md有接口依据。

# 上轮开发：0.15.2 / 164 · UI风格与来源内容

强制先读docs/ui-style-contract.md及development.md原生布局章节。本轮修正ZjuHomeworkHistoryActivity的裸px/TextView胶囊/AlertDialog，历史变更用公共dialogPanel/dialogBody/showFloatingDialog，新增保持原值未知语义。课程关联改公共卡片+浮层，外部课程详情卡片化。来源页点击行打开ScheduleSourceContentsActivity（每周窗口），开关独立启停；zju详情 sourceIntent(Context,source,key,start,end)按稳定key读缓存，master开关前/后校验。CourseSourceProbe在master关时不读zju缓存，关联页过滤保存规则并在开关变化时重载。

完全相同来源项自动只合并展示，CourseSourceMatching.exact不删标题元信息；主源/稳定zju-imported-system排序、拒绝端点保护。ExternalCourseAgenda.merge新linkedIds参数先排自有已关联副本再合并并后置过滤，Dashboard/AgendaRepository/WidgetAgenda/LAN/Reminder接入。不要让代表选中linked副本再被过滤吞掉全组。原始candidate实体合并仍需已有确认流程。备份build/backups/20261016-ui-style-contract、20261016-source-contents、20261016-exact-occurrence。

集中检查与build/lint通过，详见validation-history 0.15.2。用户仍禁止手机调试，最新UI不宣称真机验收。用户AGENTS及角色toml不提交；仅docs/development链接新权威UI规范，未覆盖用户工作区规范。

# 上轮开发：0.15.1 / 163 · 折姜星辰

本轮完成校历设置页与注入、源关联设置/备份、历史分页及同账号本机身份复用。校历覆盖普通2026–2027四小学期及海宁连续秋冬，可维护未来学年/节次/停课/补课JSON；学校边界接口无授课截止和调课，未知配置提示维护，不猜。ZjuClient将absent映射false，只对实际发布作业判断未知，无作业跳过状态请求；坏非空截止跳过该项保留旧数据。真实状态取证已解释21项中的11 submitted+10 absent，先前未知结论属于0.15.0旧实现。

课程关联入口在来源页，保守同名/同课号匹配、预览/成对别名/主源/拒绝/撤销；只影响共享展示合并，不删除Provider。系统来源探测从Calendars识别自有ID再过滤Instances，后台有界查询。完整备份预验证校历和来源JSON，失败恢复旧配置；凭据不导出，跨设备calendarId不重映射，用户需重新检查源。

最新用户UI要求：活动确认是删除按钮左侧48dp图标，确认后图标/主题色变化，点击撤销；不占整行。详情类型motionHost(true)与字段54dp最小行高，允许高光跨相邻行；筛选五栏使用相同2/3最宽项包络统一外框，内部按文字权重且单行。原子岛本轮仅离线完成/撤销回归，无设备操作。

以下0.15.0原始记录为历史证据，范围限制以本段为准。本轮集中检查：校历32、匹配20、来源备份18、来源缓存14、作业更新16、完成32、筛选16、详情17+LAN7通过；生产SQLite删除/迁移检查通过。最终构建结果与发布见validation-history。备份build/backups/zju-complete-root、zju-calendar-complete及.local-backups/course-matching-20261016。不得提交用户AGENTS、角色toml、备份和私有响应。手机仍禁止使用，原生最终UI/后台投递待授权。

已接原生CAS与学在浙大作业读取、教务秋冬课表。凭据仅由用户提供的本地文件经脚本消费，未展示内容，不写日志或版本库；不要读取account.txt、.env、Cookie或把私有响应提交。仅参考E:/Programs/ZJU-live-better，用户已撤销早期GitHub参考。

入口和主开关严格叫「折姜星辰」。ZjuSettingsStore独立Keystore加密账号密码，Cookie仅内存；ZjuSync与JobService在后台拉取。作业一个capture，DB12五表保存绑定/代次/稳定远端映射/忽略墓碑/历史，删除trigger关闭capture推送，云apply防本机复活。个人执行安排和手动完成保留，官方提交可自动完成；截止变更通过pending_calendar持久重试同步Chrona自有系统事件。通知仅统计本次真正变化且仍未完成的作业。

ZjuSync网络不持AndroidSync.LOCK，提交与恢复互斥；关闭、换号、恢复先递增epoch取消旧响应。设置成功后立即同步，并有约30分钟JobScheduler/前台过期刷新。恢复后关闭学校自动同步。ZjuTimetableStore以明确completeTimetableTerms替换，包括成功空学期，部分失败保留旧项；不要以warnings数量判断删除。

ScheduleSources按系统calendarId过滤，原页面includeSystem保留；ExternalCourseAgenda负ID共享ICS和浙大缓存，不要求系统日历权限，详情只读且可确认/取消。首页/日历/周图/widget/LAN/reminder全部接入，cachekey包含来源revision；全天ICS转换回CalendarOccurrence约定的UTC日期。优先来源仅抑制确切标题/时间/全天重复，不删除原始数据。CalendarStore CRUD/链接恢复不受展示过滤。

真实原生OkHttp只读验证10课程/21作业/277节秋冬课表，11已提交、10未知保留null。校历只核实2026–2027秋冬普通本科校区；春夏/未知学年/海宁独立校历未接，实践课无日期不造提醒。官方PDF已由主代理实际渲染检查，国庆/学生节调课已入代码。19校历、16作业更新、14来源缓存、32完成动作、629提醒/时间轴断言和真实SQLite迁移/Provider过滤检查通过；通知79及详情3时区通过，release/lintRelease通过。构建目录保留测试适配器，不能以其代替Android实际界面/后台投递证据。

用户禁止继续手机调试，本轮未ADB/安装/截图。真机界面、系统后台投递及原子岛端到端仍待设备允许时验收。前轮活动确认/撤销、首页圆形动画、紧凑筛选已并入；其离线绘制32与布局14检查通过。不要提交用户AGENTS.md、角色toml或.backups目录。

文档：docs/zju-services.md（使用/限制）、docs/zju-integration-research.md（来源证据）、docs/validation-history.md。备份build/backups/zju-root-start、zju-homework、zju-sources及.backups/course-feed-consumers-*。APK输出build/distribution/Chrona-0.15.0.apk。
