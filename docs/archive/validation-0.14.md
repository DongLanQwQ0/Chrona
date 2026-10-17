# 验证记录 · 0.14

以下为当时实际执行的检查与限制，不作为当前全量验收结论。

## 0.14.54 — 2026-10-16

- 离线集中检查：选择栏141、按压41、筛选布局12、主页切换38、历史27、vivo通知108、通知工厂78项通过。筛选最终采用实测文本宽度权重、14sp单行；曾尝试的等宽/换行及减小高光方案已撤销。
- 真机实际展开原子卡片并点击勾选，结果realClickCompleted=true、notificationRemoved=true；只操作新建临时测试待办，finally清理。原始结果build/atomic-real-fixture-result.txt，点击前后画面保留本地，不发布。
- 原子通知结束回归覆盖OEM剥离operation并改写VIVO_SUPERX_TAG的情形，不以重复发送结束通知代替实际tag/id取消；动作URI保留发生项身份。
- 真机查看筛选最终单行排版、来源首项长按1.5倍完整包络、待办展开后的壁纸模糊采样，以及三主页返回上一页。用户要求不再使用手机后停止所有设备调试；窄屏/大字体与其余控件未逐一真机验证。
- release及lintRelease通过，日志build/20261016-01454-release.log；0.14.54安装成功，发布包build/distribution/Chrona-0.14.54.apk。

## 0.14.53 — 2026-10-16

- 选择栏128项通过，更新旧的拖动滞后预期为输入帧直接跟随，并覆盖短按脉冲峰值1.1–1.3、恢复1倍、长按1.5倍、惯性与取消边界。
- 00–08时间轴17320断言通过；周手势与小组件布局契约通过，检查图文整体居中约束、首页图标对称边界和开关状态色。
- 真机短暂连接后断线，尚未执行原子岛勾选。按用户要求停止连接并先发布其它修改；新图标与选择栏手感尚未真机复验。
- release及lintRelease通过，日志build/20261016-01453-release.log；发布包build/distribution/Chrona-0.14.53.apk。

## 0.14.52 — 2026-10-16

- 通知载荷105项通过，使用可推进时钟连续验证119→115秒，卡片、原子岛、胶囊与简卡一致；覆盖秒数关闭、显示结束、旧快照及屏幕非交互状态。服务验证连续1秒重排程、闲时分钟探测与亮屏立即刷新。
- 时间映射17320断言通过，覆盖01/08边界、跨夜重叠、三档尺寸的单调与正反换算；卡片构建/缩放642项、周双轴手势、按压控制器40项通过。仅界面周视图压缩，完整导出不变。
- 此轮离线检查与构建，不连接手机；新UI及vivo显示策略未真机复验，不以替身检查声称实际系统锁屏秒刷新已验收。
- release及lintRelease通过，日志build/20261016-01452-release.log，安装包build/distribution/Chrona-0.14.52.apk。

## 0.14.51 — 2026-10-15

- 请求契约检查通过；SemanticEventNormalizer 59项、自动写入协调16项通过，覆盖凌晨05:00边界、本地星期、跨年、只有钟点的存疑草稿、准点提醒不虚构截止。样例是实际程序转换及模型输出夹具，不是线上模型准确率测试。
- release及lintRelease通过，日志build/ai-time-01451-release.log。真实旧输出仅只读观察；本版按用户要求不连接手机，不重新提交用户日程，未测量新提示词的服务端耗时。

## 0.14.50 — 2026-10-15

- 角标生产方法回归 15 项通过，覆盖确认后 1→0 隐藏、0→1 原位重现、失败/导航同步、缓存页面及 revision 记账顺序。日程刷新生产控制器 374 项通过，包含旧 worker 与旧动画不能覆盖新计数。
- 共享选择栏 126 项通过，新增角标 wrapper 的主标签自动字号回归；角标自己的字体不受影响。
- 周手势生产控件回归通过，包含双轴滚动、同次拖到边缘后再外拖、仅抵边不切、回撤取消和捏合隔离；卡片构建/缩放 642 项与导出组合 7 项通过。导出替身核对关闭索引时不绘制附录、不保留编号、不受附录高度限制，不代替实际 PNG 视觉。
- release / AndroidTest / lintRelease 构建通过。用户重新提供连接并要求重推后，实际安装成功，设备版本157 / 0.14.50。原生week_image通过：仅周表2240×2790、附索引2240×3492，两种预览均已查看；原生ScaleGestureDetector累计缩放1.7143且未切周，连续外拖上一周与右边缘下一周通过。首次600px手势fixture指距不足，改为正常屏宽1200px后通过，生产实现未改。证据 `build/device-workflow-01449/week-image-01450.txt`；测试是局部原生控件，整页捏合焦点和手感仍待人工检查。

## 0.14.49 — 2026-10-15

- 完整离线回归 60/60 通过（`build/workflow-round1-checks.log`）；后续选择栏、时间编辑与筛选增量仅复查受影响入口。真实 SQLite 覆盖首页待办统计/范围查询及记录标题、正文搜索。
- vivo V2527A / SDK 36 的真实 Android SQLite 与 HTTP 集成通过；测试使用隔离数据库，临时日历事件在结束后清理。已覆盖记录修改、独立原文、完成/恢复和过期 revision 拒绝。
- 日历缓存返回证实复用同一快照，前后缓存键和 Provider revision 一致。六次快速反向输入全部收到，最终高光归位；长按 1100ms、拖出取消、松手复位及下一次点击通过。
- 本机未缓存切换 P95 / 最大帧耗时 11.86 / 15.70ms，缓存返回 14.01 / 21.79ms，快速反向 16.21 / 22.42ms；全部样本未丢失测量回调。Activity 空实例打开为 32.51 / 34.18ms，不是进程冷启动；worker 时间含仓库和缓存工作，不代表独立 SQL 耗时。
- 初次组件布局回归覆盖 320×568、360×640、640×360dp 与字体倍率 1 / 1.5 / 2，共 9 组；这属于局部原生组件检查，未据此宣称全应用横屏、大字体或零掉帧。
- 最终增量检查：选择栏/嵌套手势 126 项、详情时间 watcher 17 项和网页时间规则 7 项通过；首页筛选、切页控制器及日程逐帧刷新检查通过，网页语法检查通过。最终 release、AndroidTest 和 lintRelease 构建成功，日志 `build/workflow-publish-final-build.log`。
- 最终组件包络复验 27 组通过：2 / 3 / 5 项真实标签，三种屏宽 × 三种字体倍率；每组首尾长按 240 个积分帧、快甩 480 个积分帧及极限伸展均容纳于宿主/滚动视口。此前失败抓到自动字号后的基线错位（末项下移 26px）和大字体下“自定义”文字超宽，分别通过关闭基线对齐、简洁标签及文字权重布局修复。320dp / 2 倍字体的 2 项和 5 项预览已实际查看。
- 最终 APK 与匹配测试包覆盖安装成功；最终布局报告 `build/device-workflow-01449/layout-publish.txt`，报告校验 `acceptance-final.txt` 通过。当前用户正在使用手机，未抢占操作；整页横屏/大字体及最新筛选摘要收起动画仍待人工复验。

## 0.14.48 — 2026-10-15

- assembleRelease、lintRelease 通过；离线回归 58/58 通过，网页脚本语法检查通过。
- 实际 SQLite 覆盖待办完成/恢复、精确截止、升级、快照字段顺序和合并来源转换；AI、查询、提醒、备份与同步模型回归通过。
- 手机离线，最终原生界面、升级安装与原子岛显示尚未真机验收。

# 历史验证记录

## 0.14.26 — 2026-10-13

- 完整47/47通过，build/calendar-list-vivo-checks.log。日历生产dayList/时间轴工厂631项验证创建延后，立即创建负例复现集中构建；scheduler15项验证2ms/4项上限及追加/取消。时间轴当前线最后入队保持在卡片上方。均为平台替身，不是FPS测量。
- 日程controller306项含异步数据完成后退出旧列表、共享进入及过期出口拒绝；原生动效36项含取消复位、低动效立即替换；vivo生产适配器26项含字段、厂商、三阶段及结束异常取消。
- debug/release/lint通过，0 errors /84 warnings。最终日志build/calendar-list-vivo-final-build.log，签名保持；0.14.26 /133，1,995,626字节，SHA256 `521ad70df9a41002bfa1b4219ecb2a122b2dcb6c79fee9bff3ed93791ef5849d`。备份build/backups/20261013-calendar-list-motion。设备离线，日历/列表视觉和原子岛均未真机验证。

## 0.14.25 — 2026-10-13

- 录屏解码381帧，定位约50ms高光冻结后相邻8ms跳138px；结果55→31同时更新。用户以减少日程数消除卡顿补强列表规模相关性。尚无运行时各阶段耗时分解。
- 日程刷新生产控制器和renderer最终245项：worker查询、每回调单条构建、旧内容保留、过期请求丢弃、暂停/身份/销毁保护、末尾分页、revision缓存、滚动恢复、读取与行创建失败重试。替身不代替Android帧率或触控。完整45/45，build/schedule-refresh-checks.log；最后新增行创建失败案例单独复验通过。
- debug/release/lint通过，0 errors /84 warnings，build/schedule-refresh-build.log；原签名保持。0.14.25 /132，1,992,758字节，SHA256 `81089865327c3941da84f74f6d71e114286eddf18bd934d18e89026770ee2c22`。备份build/backups/20261013-schedule-frame-refresh。本版未真机复验。

完整44项通过：初跑42/44，学期持久化原子移动和同步回环连接受沙箱限制；正常权限单独复验两项通过。日志build/choice-output-checks.log、choice-output-terms-recheck.log、choice-output-sync-recheck.log。

## 0.14.24 — 2026-10-13

- 生产选择弹窗布局回归720项，覆盖密度1/2/3、窄宽窗口、短长列表和首尾持续放大；去掉内容padding的负例144次均复现裁剪。替身验证几何，不能代替Android绘制与触控。
- 子代理核对现代AOSP硬件裁剪实现和0.14.23最终DEX，确认固定clipBounds不加滚动偏移且应用生命周期覆盖ModelOutput。手机最后已知安装的是中间包，无法离线确认截图实际安装内容。
- debug/release/lint通过，0 errors /84 warnings；日志build/choice-output-build.log。0.14.24 /131，APK 1,989,530字节，SHA256 `ea4fa7ae6e599030c057c9ef01b584e351a82f2bc35a27ac32bc0351c1fb4f02`，签名保持；备份build/backups/20261013-choice-output。本版未真机复验。

## 0.14.23 — 2026-10-13

- 已连接手机并以持续按住2秒、1秒截图测试。用户现场下一件事首卡长按的左边框裁切有直接证据；修复后同控件长按轮廓完整。日历右端Dock、首页标题、设置、记录“+”、课表学年按钮及课程块也有稳定长按截图，位于`build/device-ui-audit/`。
- 实际carousel工厂108项；移回strip padding的错误变体9/9被拒绝。PressFeedback40项含自身clipBounds及尺寸更新；完整43/43，`build/device-ui-audit/full-checks.log`。源码集成审查通过，前次发现的日/周内层Scroll与长备注余量遗漏已补齐。
- 最终版本debug/release/lint通过，0 errors /84 warnings，`build/device-ui-audit/final-build.log`。0.14.23 /130，1,989,554字节，SHA256 `278c5dec0d32ce3c3e9cb859d2437b75e53dd38f36bfba56652a40ba8db08dd7`，原签名保持；备份`build/backups/20261013-device-long-press`。
- ADB在最后自身clipBounds修复覆盖安装时离线。用户明确继续离线审查，子代理独立复核clipBounds滚动语义、网格对齐及长备注布局通过。手机目前为中间测试包；最终包的视口边界、备注输入框与日/周边缘事件未真机复验，按授权发布并披露。已测个别控件不等于整个应用视觉验收或FPS测试。

## 0.14.22 — 2026-10-13

- PressFeedback+生产 pressable 27项：动态挂载后放开普通父级，滚动/切页/outline边界保留，Dock排除，设置父板接收子按压并防叠乘，1.20倍率不被全局扫描覆盖，已有波纹mask保留。View/动画替身，不能代替Android视觉验证。
- 课表20场景，新增冲突列与大字体下四个11sp字的宽度余量；完整42/42，`build/press-overflow-full-checks.log`。子代理截图与集成审查通过。
- debug/release/lint通过，0 errors / 84 warnings，`build/press-overflow-final-build.log`。0.14.22 /129，1,989,238字节，SHA256 `b67796280219ec3bd94319ada8ebbe3dc7cc2054e68741491973da69a7a7c0e2`，签名保持一致；备份 `build/backups/20261013-press-overflow`、`build/backups/20261013-output-spacing`。ADB无设备，最终按压视觉与滚动未实测。

## 0.14.21 — 2026-10-13

- 日历 220 项，加入布局未稳定保持隐藏、滚动恢复触发第二轮布局仍不显示、稳定后才启动，以及旧页移除前先隐藏；禁止页面 setAlpha。生产方法配合布局/动画替身，不能证明真机残影已消失。
- PressFeedback 14 项覆盖按下放大/松手恢复、enabled+pressed 状态、动态控件、幂等安装、Dock 排除、滚动容器排除、detach 清理与关闭动效。完整 42/42，`build/calendar-feedback-full-checks.log`；子代理独立集成审查通过。
- debug/release/lint 通过，0 errors / 84 warnings，`build/calendar-feedback-final-build.log`。0.14.21 / 128，1,988,834 字节，SHA256 `b872b7714790fff208f6fbd23e7c64630ab795156c1a087b633bba95aea42416`，保持原签名。备份 `build/backups/20261013-calendar-first-frame`、`build/backups/20261013-home-guide`。真机视觉、残影与触控未验证。

## 0.14.20 — 2026-10-13

- Dock 111 项，完整 41/41；`build/dock-container-full-checks.log`。实际生产筛选工厂、页面切换容器、Dock 和溢出方法，配合 Android 父子布局/事件/队列替身，覆盖中间父级不裁剪、页面视口仍裁剪、栏内手势优先、栏外松手、栏外横滑及缺失登记的负例。
- debug/release/lint 通过，0 errors / 84 warnings，`build/dock-container-final-build.log`。0.14.20 / 127，1,988,490 字节，SHA256 `b9b7f97771043ea9c4921c7e490a0fd6a8265370add560b6eba2dfeb4905785c`；签名保持一致。备份 `build/backups/20261013-dock-container-ownership`。ADB 无设备，实际手机裁剪和手势未验证。

## 0.14.19 — 2026-10-13

- 截图指出真实布局错位。生产高光平移修正父布局原点重复计算；测试替身开始模拟实际内边距及高光布局原点，不再把 setPadding 当空操作。
- Dock 104 项、完整回归 41/41 通过，`build/dock-padding-full-checks.log`。同一测试对修改前代码失败：期望中心 149、实际 157；`build/dock-padding-original-rejection.log`。覆盖主导航、四/五项筛选、竖向、修改 padding 与父布局重排；仍为 Android 布局/队列替身。
- debug/release/lint 通过，0 errors / 84 warnings，`build/dock-padding-final-build.log`。0.14.19 / 126，1,988,310 字节，SHA256 `18388fdac757a5459e7c4e36acfbedca05deb34b1d40e430ddb4e2a2abef8b7c`，保持原安装签名；备份 `build/backups/20261013-dock-padding-regression`。ADB 无设备，实际手机视觉、手感和帧率尚未实测。

## 0.14.18 — 2026-10-13

- 完整离线 41/41，`build/filter-dock-full-checks.log`。原生 Dock 与生产 filterDock 工厂补充 92 项：状态徽章、末项点击、四/五按钮复用、接近区提前减速及连续性、稳定的高光布局基准；采用 Android 队列和布局替身。
- 新增生产日历过渡方法 212 项：等待预绘制、测量后恢复滚动先于动画、两页共享进度且边缘无重叠、取消/过期代际/关闭动效收尾。日志对应 `checks/calendar_slide_check.py`，不能替代实际 Android GPU 和帧率测试。
- 真实 Edge 动效与交互通过，pageErrors 为空；日志 `build/filter-dock-web-motion.log`、`build/filter-dock-web-check.log`。debug/release/lint 通过，0 errors / 84 warnings，`build/filter-dock-final-build.log`。0.14.18 / 125，1,988,166 字节，SHA256 `ef52fd87185eba6ce65044c877ee262c8adfdd2a48699ebae32cbff1e788ebe3`，保持原安装签名。备份 `build/backups/20261013-filter-dock-motion`。ADB 无设备，实际残影、FPS 和覆盖安装未实测。

## 0.14.17 — 2026-10-13

- 完整离线回归 40/40，`build/shared-dock-full-checks.log`。原生 Dock 83 项包含横竖手势、Y 速度、竖向栏外释放、渲染属性更新期间无整栏 invalidate 与无重复布局；采用 Android 队列/布局替身。
- 新增生产日历分帧构建调度 12 项，验证工作预算让出帧、动态追加日期行、完成仅执行一次、取消回调并使不完整缓存失效。采用时钟/帧回调替身，不能替代 Android 帧率测量。最终 Dashboard 切页控制器 12 项复验通过。
- debug/release/lint 通过，0 errors / 84 warnings，日志 `build/shared-dock-final-build.log`。0.14.17 / 124，1,987,778 字节，SHA256 `a5fe3f47405b13f10d8a541ee17b6b086d42996132d08a41565320ae6972cffd`，保持原安装签名；备份 `build/backups/20261013-shared-dock`。ADB 无设备，横屏视觉、真实 FPS 与覆盖安装未实测。

## 0.14.16 — 2026-10-12

- 完整回归 39/39，原生 Dock 77 项覆盖渐进上限、放大时间倍率、额外阻力、预测不修改显示状态、立即选页与惯性结束不重复选页；生产方法使用 Android 队列替身。日志 `build/dock-resistance-full-checks.log`。
- 真实 Edge 动效与完整网页交互通过，验证预测路由在惯性仍进行时已启动，结束后路由不变、外端距离有上限，pageErrors 为空。日志 `build/dock-resistance-web-motion.log`、`build/dock-resistance-web-check.log`。
- debug/release/lint 通过，0 errors / 84 warnings，构建日志 `build/dock-resistance-final-build.log`。0.14.16 / 123，1,986,526 字节，SHA256 `d831925a61b01f9fad6192e57c35f1dfb43153cbee8b2f9f6f8a9c487b7fdb49`，保持原安装签名。备份 `build/backups/20261012-dock-resistance`。ADB 无设备，Android 实际帧率、触控手感和覆盖安装未实测。

## 0.14.15 — 2026-10-12

- 完整离线回归 39/39，通过日志 `build/inertial-landing-full-checks.log`。新增执行生产模糊绘制方法的 10 项检查，覆盖移动坐标、离屏边缘延拓、着色器复用/替换和未就绪路径。原生 Dock 70 项包含同一释放位置不同速度得到不同落点、越界回弹和延迟单次提交。Android 图形与队列均为替身。
- 真实 Edge 动效检查通过：正反向惯性落点、标准半宽内端锚定、外端继续延伸、栏外释放、缩放及减少动态效果；网页完整交互检查通过，pageErrors 为空。网页证据 `build/lan-motion-checks/evidence.json`，完整交互日志 `build/inertial-landing-web-check.log`。
- debug/release 与 lint 通过，0 errors / 84 warnings，`build/inertial-landing-final-build.log`。APK 0.14.15 / 122，1,986,222 字节，SHA256 `35f64478361722d90db1d018df4039b6f7a4212d6aeea3324574357f3cf84ea8`，保持原安装签名。
- 备份 `build/backups/20261012-inertial-landing`。ADB 无设备；Android 真机帧率、动态模糊、边缘拉伸手感及覆盖安装尚未实测。

## 0.14.14 — 2026-10-12

- 审查发现松手切页同步读取与重复导航构建、每帧玻璃采样。新增 DashboardSectionData 后台一致性事务读取；缓存页与导航按钮保留，动效期间延后刷新，松手页平移临时 withLayer 并结束恢复玻璃采样。首页宿主不裁剪且底部新增 12dp 放大余量。
- 完整离线 38/38 通过，`build/dock-release-performance-full.log`。新增真实读取/切页控制器 12 项核对工作线程约束、分页/捕获输入、缓存无读取、快速反选、动画期间延后检查、失败保持旧页/事务释放、生命周期过期及系统源隔离，`dock-release-performance-switch-check.log`。
- 后续 1∶1 松手速度补充通过 Dock 63 项检查：屏幕 VelocityTracker 速度转换、正反向保速、目标不改变、100ms 快缩放和约 32% alpha，加上既有栏外释放/多指/暂停逻辑。平台速度采样器为替身，真实设备手感待验收；日志 `dock-release-performance-dock-check.log`。
- 实际 Edge 动效与速度函数验证通过，包含 1∶1 屏幕像素/秒计算及反向符号、快缩放、视口底边、栏外 UP、回缩、减少动态效果和已有页面/弹窗动作；日志 `dock-release-performance-web-motion.log`。Android 真机 FPS 与下半部裁切未实测，ADB 无设备。
- 最终 debug/release 与 lint 通过，0 errors / 84 warnings，`build/dock-release-performance-final-build.log`。APK 0.14.14 / 121，1,984,898 字节，SHA256 `eb29d4172ebd93ad89e977a28cc9ab25936a88ded5d947e38958a0157b945cd8`，原安装签名、非调试。
- 备份 `build/backups/20261012-dock-release-performance`；分发 `build/distributions/0.14.14/Chrona-0.14.14.apk`；发布核对 `build/release-0.14.14-artifact.json`、`build/dock-release-performance-public-verification.log`。尚未进行 Android 帧率、实际裁切/速度手感或覆盖安装验收。

## 0.14.13 — 2026-10-12

- Chrona 本地日程与系统日程统一开启周/日/全天卡片边框。共享原生 Dock 和网页 Dock 的水平拖动支持栏外松手确认；阻尼弹簧保留重定向速度，按压目标高光 1.5、整栏 1.1，移动宽度拉伸回弹，释放/取消/多指/减少动态效果收尾。
- 完整离线回归 37/37，通过日志 `build/dock-spring-full-checks.log`；原生 Dock 57 项覆盖普通 tap/badge、队列 click、栏外/越界 UP、取消、多指、惯性连续性、尺寸目标/回归、宽度弹性、脱离/页面手势接管和关闭动画。原生为 Android 队列替身，不能替代真机。
- 实际 Edge 动效与指针捕获通过，验证栏外松手切换、移动惯性、宽度拉伸、按压比例、释放恢复、关闭动画和原有导航/弹窗/滚动呈现。网页完整交互检查也通过，pageErrors 为空。证据 `build/dock-spring-web-motion.log`、`dock-spring-web-check.log`、`build/lan-motion-checks/evidence.json` 及 `dock-{pressed,moving}-mobile.png`。截图发现底边裁切后改为向上放大锚点并复验通过。
- 最终 debug/release 构建、lint 成功，0 errors / 84 warnings，日志 `build/dock-spring-final-build.log`；APK 0.14.13 / 120，1,981,514 字节，SHA256 `a1f2a1fe3c4c88b0798e4eed31afb302e6cdaed33a5470bd0f921b4ff9881604`；原安装签名，无调试标记。
- 备份 `build/backups/20261012-dock-spring`；分发 `build/distributions/0.14.13/Chrona-0.14.13.apk`，发布核对 `build/release-0.14.13-artifact.json`、`build/dock-spring-public-verification.log`。ADB 无连接设备，Android 真机视觉、触控手感和覆盖安装仍未验收。

## 0.14.12 — 2026-10-12

- 来源颜色使用 CALENDAR_COLOR，经生产 Instances 查询传递；实际查询方法的平台替身检查验证列投影、空值、黑色和全天项。日历专项 134 项验证颜色传递、仅源色改变时刷新、65/35 RGB 混色及主题改变。日志 `build/calendar-source-color-core.log`、`calendar-source-color-provider.log`。
- 提醒 37 项、实际 SQLite eligibility 扫描、组件来源失败/隔离、动效核心 30 项与自定义主题检查通过；日志 `build/calendar-source-color-{provider,widget,motion,theme}.log`。真实 Android 提供器与视觉未验收。
- debug/release 与 lint 通过，0 errors / 84 warnings，日志 `build/calendar-source-color-build.log`。APK 0.14.12 / 119，1,978,986 字节，SHA256 `5ddeb77d48e82299efe52c59a455b1c2f3fdfe90096f279bdb0053c3dcd7636a`；原安装证书，无调试标记，分发 `build/distributions/0.14.12/Chrona-0.14.12.apk`。
- 备份 `build/backups/20261012-calendar-source-color`；ADB 无连接设备。发布/公共下载核对证据 `build/release-0.14.12-artifact.json`、`build/calendar-source-color-public-verification.log`；尚未验收真实提供器颜色、原生视觉与覆盖安装。

## 0.14.11 — 2026-10-12

- 月内选日采用原位更新，专项 127 项通过；新增检查执行真实 selectMonthDay/navigateDate，核对月历/列表宿主/滚动宿主身份保留、无整页重建或读取、只更新前后选中格与当天列表、重复选择无操作、后台刷新时局部选择及跨月导航。Android UI 为替身，未验收真机呈现。日志 `build/month-day-selection-check.log`。
- debug/release 与 lint 通过，0 errors / 84 warnings，日志 `build/month-day-selection-build.log`。APK 0.14.11 / 118，1,978,154 字节，SHA256 `df83b84c9d345c08e71a0f63f8723828bb78950e94111127433648ec4f80dbff`，原安装证书、无调试标记；分发 `build/distributions/0.14.11/Chrona-0.14.11.apk`。
- 备份 `build/backups/20261012-month-day-selection`；公共发布核对 `build/release-0.14.11-artifact.json`、`build/month-day-selection-public-verification.log`。未进行 Android 真机视觉、手势或覆盖安装验收。

## 0.14.10 — 2026-10-12

- 根据用户实际 Android 截图检查透明度叠加与壁纸底板：数量取消二次 alpha，非本月整格仅弱化一次；日历局部底板提高混色下限，选中日期描边，系统/已结束标题恢复正常文字对比度。备份 `build/backups/20261012-calendar-contrast-gestures`。
- 日历生产逻辑专项 121 项（新增左右滑动、抖动、纵向/嵌套滚动优先、短滑动、多指/加载取消等）、动效核心 30 项、Dock 49 项及自定义主题检查通过。证据 `build/calendar-contrast-{gesture,motion,dock,theme}-check.log`；手势状态机是真实代码，Android 事件派发与视觉仍需真机验收。
- debug/release 构建与 lint 通过，0 errors / 84 warnings，日志 `build/calendar-contrast-build.log`。release APK 0.14.10 / 117，1,977,850 字节，SHA256 `5c77e29ca27c1e3cfe6fb3b3352cfdb7dc00f84107570c18d0eceabadf8eb93b`；原证书 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，无调试标记。
- ADB 无连接设备，真机视觉、手势手感及覆盖安装未验收。分发 `build/distributions/0.14.10/Chrona-0.14.10.apk`；发布与匿名下载/应用更新解析核对证据 `build/release-0.14.10-artifact.json`、`build/calendar-contrast-public-verification.log`。

## 0.14.9 — 2026-10-12

- 先完成九项问题审查，再实施修复，见 `docs/archive/notes/calendar-review-20261012.md`。修改前备份 `build/backups/20261012-calendar-review`；用户 AGENTS 与角色 TOML 未纳入版本提交。
- 完整离线回归 37 项通过：普通沙箱 35/37，课表原子文件移动和同步本机回环连接受权限限制，使用现有工具链在允许的环境单独重跑两项通过。证据 `build/calendar-review-full.log`、`calendar-review-academic-retry.log`、`calendar-review-sync-retry.log`。
- 新日历专项 110 项执行真实日期索引、提供器封装、取消/队列、滚动恢复和失败恢复方法；动效核心 30 项覆盖稳定视口连续合成与暂停释放。Android 提供器/UI 为替身，不能代替真机。证据 `build/calendar-review-core.log`、`calendar-review-motion.log`。
- 最终 debug/release 构建与 lint 通过，0 errors / 84 warnings；新增警告涉及恢复用户原有无障碍焦点及列表状态文字拼接。证据 `build/calendar-review-final-build.log`、`app/build/reports/lint-results-debug.txt`。
- release APK 0.14.9 / 116，1,976,630 字节，SHA256 `d2410ba73765b24a665fdc450a09de9ac64a9ed0b401948b198f7e103f72f8c6`；debug/release 原证书 SHA256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，release 无调试标记。分发 `build/distributions/0.14.9/Chrona-0.14.9.apk`。
- ADB 无连接设备，未验收 Android 真机视觉、触控、帧率或覆盖安装。发布与公共下载核对证据保存在 `build/release-0.14.9-artifact.json`、`build/calendar-review-public-verification.log`。

## 0.14.8 — 2026-10-12

- 定向检查通过：Dock 49 项、原生动效核心 28 项、生命周期动效、日历/提醒核心 37 项与实际 SQL/提供器扫描回归。检查使用 Android 平台替身，不能代替真机视觉或触摸验收。本轮不涉及数据逻辑，未重复执行整套 36 项检查。
- 最终 `assembleDebug/assembleRelease/lintDebug/lintRelease` 成功，日志 `build/calendar-polish-final-build.log`，lint 0 错误/83 警告。新增 ClickableViewAccessibility 提示对应主动吞掉旧页触摸的非交互转场层，该层隐藏无障碍树。
- release APK 0.14.8 / 115，1,971,842 字节，SHA256 `0ebde9b651523c3268e0b19f9860826f03c32b339bc8dd294ace533755807df2`；debug/release 均为原证书 SHA256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，release 无调试标记。证据 `build/calendar-polish-{debug,release}-signature.log`、`build/calendar-polish-version.log`；分发 `build/distributions/0.14.8/Chrona-0.14.8.apk`。
- 修改前备份 `build/backups/20261012-calendar-polish`。ADB 无连接设备，未验收 Android 日历玻璃效果、动画手感、帧率或覆盖安装；发布说明保留限制。

## 0.14.7 — 2026-10-12

- `checks/run_checks.py full`：36/36，通过日志 `build/agenda-final-checks.log`。初轮沙箱阻止临时文件移动和本机回环连接，及新增提醒刷新需要补测试替身；修正适配后完整执行通过。
- `agenda_features_check.py` 执行 37 项生产 Java 提醒/布局/偏好场景、实际 TaskStore 构造的 SQL（真实 SQLite）、CalendarStore 提醒扫描方法（1199 个自身关联副本、进行中/下一项、隐藏周期实例）；`widget_preferences_check.py` 32 项，执行真实偏好及备份恢复/回滚语句。日历提供器、通知及 Android 存储使用替身。
- `ai_auto_publish_check.py`：真实 AiRequestCheck 请求契约、54 项 SemanticEventNormalizer 时间语义、16 项 AutoCalendarPublisher 自动写入协调检查；覆盖混合批次、缺权限、重复调用、写入失败、关联失败补偿、取消补偿与清理失败提示。未调用真实模型。
- 最终 `assembleDebug/assembleRelease/lintDebug/lintRelease` 成功（`build/agenda-final-build.log`），lint 0 错误/82 警告。APK 0.14.7 / 114，1,967,858 字节，SHA256 `22254f380c491114422f02d19430aacd572a0b40a0a2e841f1c0399d0747f570`；原证书 SHA256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，release 不可调试。签名和 manifest 证据 `build/agenda-{release,debug}-signature.log`、`build/agenda-apk-version.log`；分发产物 `build/distributions/0.14.7/Chrona-0.14.7.apk`。
- ADB 无连接设备；未进行 Android 真机日历/通知呈现、精确闹钟与锁屏送达、后台电池策略、实际模型 Token 节省量或覆盖安装验收。修改前备份 `build/backups/20261012-agenda-features`。发布说明显式保留这些验收限制。

## 0.14.6 — 2026-10-05

- `checks/run_checks.py full`：34/34，通过日志 `build/motion-final-checks.log`；包含新增原生核心 28 项、详情折叠 13 项，Dock 49 场景与设置布局 72 次生产方法/平台替身执行。设置恢复明确静默，动画中新取得的无障碍焦点立即收敛。
- 实际 Edge 加载生产网页与模拟手机接口：`lan_motion_check.cjs` 检查动画中间帧、Dock/弹窗/下拉/展开面板连续反向、运行中减少动效清理、滚动呈现不重播、实际十秒轮询静止、过期路由 revision、焦点及 390px 布局；`lan_feedback_check.cjs` 追加旧成功/失败请求不影响新弹窗；`lan_web_check.cjs` 37 组通过，pageErrors 为空。日志 `build/lan-{motion,feedback-motion,web-motion}-final.log`；截图 `build/lan-motion-checks/*.png`、`build/lan-feedback-checks/modal-error-mobile.png`。
- 最终 `assembleRelease/lintRelease/assembleDebugAndroidTest` 成功，94 tasks / 29 秒，Lint 0 错误/82 警告。Android instrumentation 仅编译，未执行设备测试。
- APK 0.14.6 / 113，1,945,118 字节，SHA256 `5be0578c083c2063bd10985ae06eeefcbb1579783e20280d67254fd31ac8c438`；原证书 SHA256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，不可调试，包内四项 LAN 资源逐字节匹配工作区。证据 `build/release-0.14.6-{build,signature,version}.log` 和 `release-0.14.6-artifact.json`。
- 原生独立审查发现的设置恢复重播与中途无障碍焦点保护已修复并纳入回归，最终核对源码、签名和包内资源。未执行 Android 真机动画、帧率、系统返回手感、真实 WebDAV 多机、手机 LAN 或覆盖安装验收。

## 0.14.5 — 2026-10-05

- `checks/sync_settings_check.py`：16 个离线场景通过，执行生产设置/完成回调/冲突选择方法，UI、存储、网络使用替身；覆盖测试不保存、不安排同步、密码保留/成功清空、忙碌反馈、处理后刷新及销毁保护。
- `checks/sync_core_check.py` 定向通过。`checks/lan_feedback_check.cjs` 在真实 Edge 与模拟手机 API 上通过，覆盖弹窗错误、单次提交、失败后重试以及关闭/重开时清理已有提示；独立代码审查通过。
- `checks/run_checks.py full --list` 共 32 项，同步核心与同步设置各出现一次；本轮未重跑全部 32 项。最终 `assembleRelease/lintRelease` 成功，46 tasks、9 秒，lint 0 错误/82 警告。
- 正式 APK `build/distributions/Chrona-0.14.5.apk`，versionCode 112，1,938,098 字节，SHA-256 `2f2acf940d61fc198625f79fd74f6f2c32d1eb4d35c0e3dc022a2cf7e1cb51f9`；实际 APK 未启用调试。新包与上一版 APK 的签名证书 SHA-256 均为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，包内 LAN 全部资源逐字节匹配当前源码。
- 构建、签名、版本与产物证据：`build/release-0.14.5-build.log`、`release-0.14.5-signature.log`、`release-0.14.5-previous-signature.log`、`release-0.14.5-version.log`、`release-0.14.5-artifact.json`；网页截图 `build/lan-feedback-checks/modal-error-mobile.png`。
- 未进行真实 Android 界面、实际 WebDAV、手机 LAN 端到端或覆盖安装验收。备份 `build/backups/20261005-002106-release-0.14.5`。

## 0.14.4 — 2026-10-04

- `checks/candidate_merge_check.py`：64 项，生产 TaskStore/CandidateMerges、共享同步模型，以及实际 AndroidSyncData staging/apply/changed 方法；Android SQLite API 传输替身接真实 SQLite。新增恢复序列回退后的 UUID 不重用（含未映射实体先固化），远端合并同时修改原文/附件正常落地且下一次 capture 不造假冲突，真正本地修改仍保留。
- `checks/widget_source_failure_check.py`：真实 loader 不读取课表，系统开关关闭不列系统实例、开启保留同内容不同外部事件，仅排除自身关联 ID。
- `checks/run_checks.py full`：30/30；`checks/sync_core_check.py`：通过；Edge `checks/lan_web_check.cjs`：37 组，模拟 API 检查预览/过期拒绝重扫/实体删除与来源导航/统一显示开关，截图 `build/lan-web-checks/desktop-merge-preview.png`。
- 最终 `assembleRelease/lintRelease/assembleDebugAndroidTest`：94 tasks，7 秒；lint 0 错误/82 警告。APK 0.14.4/code111、原证书 SHA256 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，1,937,378 字节，SHA256 `722cae8574ebe76d8139483dc59b24b15f09bc977f40320b4f70e9ce7173ac8f`。
- 日志：`build/calendar-merge-core.log`、`calendar-merge-web.log`、`calendar-merge-full-checks.log`、`calendar-merge-final-build.log`、`calendar-merge-signature.log`、`calendar-merge-version.log`。未执行真机原生渲染、实际日历提醒、WebDAV 两机/覆盖安装或 LAN 端到端验收。

[开发文档](../development.md) · [项目首页](../../README.md)

以下记录从原 README 完整迁出，保留各版本实际发生过的验证及当时限制。较早条目中的安装包位置、数据库版本与未完成项属于当时状态，不应据此判断最新版本。新的验证记录可按日期追加在本页，用户可见的版本说明发布在 GitHub Releases。

## 2026-10-04 · 0.14.3 网页滚动、学期与统一控件

- 真实 Edge 加载生产静态资源与模拟手机 API；1280/390 × 500px、浅深主题四种长编辑表单均隐藏弹窗/textarea 原生滚动条，dialog 仍为 overflow:auto。弹窗滚动超过 590/690px、textarea 1456px，实际滚轮/键盘滚动、草稿保持、保存按钮可达、无横移；无网页异常。日志 `build/scrollbar-browser-check.log`，截图 `build/lan-web-checks/scrollbar-*.png`。临时滚动验证脚本在忽略的 build 目录，未新增镜像测试。
- 原有浏览器回归追加统一下拉浅深主题、top-layer 菜单、方向/Escape、实际 Enter 更新原生 form 值、移动弹窗不裁切和焦点、实际触摸选择、外点关闭；33 组通过。加号 SVG 与可见路径中心均和按钮一致（dx/dy=0，路径中心12/12）。截图 `dropdown-light.png`、`dropdown-dark.png`、`dropdown-mobile-modal.png`，同目录。
- 实际 TimetableStore/AcademicTerms JVM 检查追加只读忽略旧选择、春夏秋冬边界、跨年、无当期最近、空库及多源，保留手机持久化选择；`build/scrollbar-term-check.log`。网页 API 使用同一选择表达式，不重新推断日期。
- 增量浏览器检查验证上一周/本周/下一周保持课程、请求不携带旧学期，以及下拉减少动态模式、Home/End/Tab；同一浏览器日志记录 PASS。
- Firefox 隐藏规则为标准 scrollbar-width:none；本轮实际运行 Edge，未运行 Firefox 或手机端到端、真机覆盖安装。动效扩大任务未产生任何改动。
- 完整离线组 29/29；release/lint/Android检查包构建 94 tasks 成功，lint 0 errors / 82 warnings，签名保持原证书。日志 `build/scrollbar-final-checks.log`、`scrollbar-final-build.log`、`scrollbar-final-signature.log`、`scrollbar-final-metadata.log`。APK `build/distributions/Chrona-0.14.3.apk`，1,930,598 字节，SHA-256 `a8f9211a877858d76da1d34da2c8c351be2cc9d40d989213d7c02101eeff3d5a`，versionCode 110；未运行真机检查包。

## 2026-10-04 · 0.14.2 扫码自动配对、网页动效与附件按钮

- 按用户新要求，二维码包含临时六位配对码 fragment；网页首段脚本读取后立即清理地址，再只 POST 一次现有认证。保留普通干净地址和手动配对，失败/过期回到手动入口，不持久化或自动重试；独立高熵会话、限流及关闭/重配/网络变化撤销保持。
- 二维码改为 172dp 圆角主题描边白底、8dp 内距，156dp 码图保留四模块静区；到期/关闭移除图像，地址/码变化重绘。真实生成位图经 ZXing 解码，fake 前导零配对 fragment、变址/变码/到期/停服通过，`build/qr-auto-pair-decode.log`。
- 官方 Lucide 设置齿轮与主题图标统一 21px/2px 笔画；导航、弹窗、展开收起与提示采用 120–180ms 可取消有限动画，普通刷新不重播，减少动态模式关闭。真实 Edge 模拟接口 28 组通过，涵盖自动配对/URL 先清理/失效无重试/手输、图标中心、动画触发结束/快速操作/减少动态、输入草稿/焦点及浅深/桌面移动；无网页异常，`build/qr-motion-web-check.log`。截图在 `build/lan-web-checks/`，新增浅深齿轮特写已查看。
- 图片附件删除改为 Lucide 矢量 X；真实构造器/JVM 平台替身 18 次执行与离线可见笔画光栅化通过，非对称 padding 故障变体被拒绝，原图与删除回调保持。该检查已纳入完整离线组，不代表 Android 渲染。
- 完整离线回归 29/29，`build/qr-motion-final-checks.log`。`assembleRelease/lintRelease/assembleDebugAndroidTest` 成功，`build/qr-motion-final-build.log`，lint 0 errors / 82 warnings。APK `build/distributions/Chrona-0.14.2.apk`，0.14.2 / 109，未启用调试，1,928,918 字节，SHA-256 `fe20d0c4a78576ca9f6859914cf41765c41ea7f0342cdd246c1c07521b23791b`；原证书保持，签名/元数据证据 `build/qr-motion-final-signature.log`、`qr-motion-final-metadata.log`。
- 修改前备份 `build/backups/20261004-130948-qr-auto-pair`、`build/backups/20261004-130914-attachment-remove-center`；用户 AGENTS 和角色 TOML 未改动。ADB 无设备，未验证原生二维码/附件实际渲染、实扫/真实 LAN、系统权限/日历、后台/真实 WebDAV 多机及覆盖升级；网页截图与 JVM 检查不替代设备验收。

## 2026-10-04 · 0.14.1 设置留白、配对与网页/课表修正

- 核查发现只有 HANDOFF 与历史记录描述了更新页 20dp 留白，原开发文档没有集中可执行页面规范。两页虽设 padding，却遗漏关闭 `UiStyle.page` 开启的 fitsSystemWindows，系统 inset 会重置内容留白；已由 `SettingsPageLayout` 统一内容/顶栏/视口。具体执行条款见 [开发文档](../development.md#原生设置页布局规范)。
- 备份 `build/backups/20261004-124011-settings-gutters-pairing` 与 `build/backups/20261004-timetable-merge`；未修改用户 AGENTS 或角色 TOML。
- 安全随机六位数字码保留前导零，10 分钟有效；独立 256 位会话/写入凭证及限流/退出/停服撤销保持。认证检查 29 项通过。ZXing core 在本地生成纯 URL 二维码，真实解码验证两 URL、变址重绘、缓存及停服清图通过，日志 `build/settings-qr-check.log`。
- 布局检查执行真实内容/视口方法及真实 UiStyle.page/applyInsets，36 组 SDK/密度/系统栏/键盘下的层级和留白数值通过；隔离故障变体重新开启 fitsSystemWindows 后确实被断言拒绝，日志 `build/settings-layout-check.log`。该检查使用 Android 平台替身，不代表原生渲染。
- 网页中性浅/深背景、正文/次文字与卡片对比 ≥4.5、前导零码输入、完整学期名及现有操作共 16 组 Edge 检查通过；模拟手机接口、无网页异常。浅/深课表对照截图 `build/lan-web-checks/desktop-light-timetable.png`、`desktop-dark-timetable.png` 已查看；日志 `build/settings-web-check.log`。
- 手机课表不同地点仅在实际日期互斥时聚合；同日同时间异地保持独立色块/冲突列，各 occurrence 地点和定位 key 保留。课表模型 19 场景及学期/CourseAgenda 检查通过，LAN 直接使用实际 occurrence 地点，无接口迁移。
- 完整离线回归 28/28 通过，日志 `build/settings-reviewed-final-checks.log`；同步核心检查通过，日志 `build/settings-sync-core.log`。
- 最终 `assembleRelease/lintRelease/assembleDebugAndroidTest` 成功，日志 `build/settings-reviewed-final-build.log`，lint 0 errors / 82 warnings；正式包 `build/distributions/Chrona-0.14.1.apk`，版本 0.14.1 / 108，未启用调试，1,927,130 字节，SHA-256 `0823cecb1cafaf595c1f3cc06e55938f30ea2f6655fd847545be98994e3d0db3`，原证书保持。签名与实际包元数据：`build/settings-final-signature.log`、`settings-final-metadata.log`。
- ADB 无在线设备：尚无两页 Android 渲染截图、实际扫码/后台/LAN 端到端、系统日历/权限、课表触控/大字体/旋转、覆盖升级和真实 WebDAV 多机验证；网页截图不能替代原生验收。

## 2026-10-04 · 0.14.0 局域网网页与多设备同步

- 取消独立 Windows 客户端后，以 Android 内置局域网 HTTP 服务与离线网页替代；同步核心保留。修改前备份 `build/backups/20261004-105512-lan-web` 含本轮调整前代码/文档，用户 AGENTS 与角色配置未改动。
- `:app:assembleRelease :app:lintRelease :app:assembleDebugAndroidTest` 成功，日志 `build/lan-review-fixes-final-build.log`；lint 0 errors / 83 warnings。APK 为 0.14.0 / 107，未启用调试，证书 SHA-256 保持 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。
- 统一完整离线回归 26/26 通过，日志 `build/lan-review-fixes-checks.log`；同步核心检查通过。生产 `LanSecurity` 的 25 项认证、来源/地址、限流、会话到期/关闭验证通过。
- 使用已安装 Edge 和隔离的后台浏览器配置验证网页，手机接口为明确的模拟数据。配对、今日/日程/收件箱/课表、原文 XSS 与修改、日程修改、手机时区转换、全天 UTC、手动建日程、ICS 预览/导入、退出、深浅主题和 390px 宽度无页面溢出通过。生产 CSP 限制下检查通过，深色玻璃背景检查通过，页面异常 0。结果与四张截图位于 `build/lan-web-checks/`。
- `LanAccessInstrumentation` 使用真实 HTTP 服务和 Android SQLite 的隔离数据库，检查包编译通过；ADB 无在线设备，仪器检查未实际运行。手机设置视觉、系统授权/日历写入、真实附件权限、锁屏/后台服务、网络变化/关闭撤销及手机/电脑局域网端到端仍待真机验证。未使用真实坚果云账号或 AI 密钥，真实多机同步和提醒送达也未验收。
- 审查修复后产物 `build/distributions/Chrona-0.14.0.apk`，1,704,614 字节；SHA-256 `1ddd7084a1e707ad7a52783937d0d4894acc8574110714074ecc094c344cd026`。签名与元数据证据分别为 `build/lan-final-signature.log`、`build/lan-final-apk-metadata.log`。
- 独立审查修复：手机原文/候选/备注保存先原子比对打开时基线，Calendar 副作用前验证；数据库提交后反馈，原文后台保存，失败保留输入，旋转与重复日历确认保留基线。网络身份与 IPv4 任一变化撤销会话，同 IPv4 不同网络也关闭。生产逻辑与事务接线离线检查 `build/lan-edit-conflict.log`、草稿检查 `build/lan-detail-draft.log` 通过；未将这些检查等同于真机并发/网络测试。
- 发布前只读查询确认远端最新 `v0.13.84`，发布于 2026-10-02；不存在 `v0.14.0`，证据 `build/lan-remote-version.json`。此记录为发布前实现验证，发布另须独立审查与远端结果确认。
