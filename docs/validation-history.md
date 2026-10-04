# 历史验证记录

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

[开发文档](development.md) · [项目首页](../README.md)

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

- 核查发现只有 HANDOFF 与历史记录描述了更新页 20dp 留白，原开发文档没有集中可执行页面规范。两页虽设 padding，却遗漏关闭 `UiStyle.page` 开启的 fitsSystemWindows，系统 inset 会重置内容留白；已由 `SettingsPageLayout` 统一内容/顶栏/视口。具体执行条款见 [开发文档](development.md#原生设置页布局规范)。
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

## 2026-10-03 · 0.13.86 设置分组

- 设置主页面改为四组紧凑列表，原有操作入口保留，版本和联系方式进入关于浮窗；复用现有主题表面和线性图标，选项值右对齐，触控行至少 54dp。
- `assembleRelease`、`lintRelease` 通过，0 errors、82 warnings；APK 元数据确认 0.13.86 / 106、未启用调试，原签名验证通过。
- 安装包 `build/distributions/Chrona-0.13.86.apk`，SHA-256 `7821f54e93ec01276b29f8ae6a020f4178dfc50230a3efca749985ba6b0b88f8`。备份 `build/backups/20261003-011045-settings-groups`；未做真机视觉验收。

## 2026-10-03 · 0.13.85 模块整合与可信反馈

- `python checks/run_checks.py full` 最终 24/24 通过（43.5 秒）。包括课程实际日期、启用学期、替换/删除/恢复缓存、全天/跨日、精确点击参数、解析锁期间版本读取、课表单源失败隔离、请求重试与重解析参照、筛选摘要、截止转换、详情草稿及现有数据完整性检查。
- 小组件偏好与实际备份偏好语句检查 28 项，弹幕重复刷新/关闭/系统禁用动画清理 544 项，布局边界 68100 项通过；模型替身无法验证真实启动器渲染、触摸或耗电。
- 独立审查发现并修复三项问题：版本读取与 ICS 解析共用锁造成主线程等待；课表读取失败误置小组件全局错误；普通日程转为截止后残留默认结束标记可能改写明确截止。相关针对性回归通过。
- 统一入口发现历史日程测试含产品未实现的 completed 字段/第五标签断言，已按实际四标签与历史保留语义修正；独立检查现在每次从当前查询源码重新编译并使用 JAVA_HOME。
- `assembleDebug`、`assembleRelease`、`lintDebug`、`lintRelease` 全部通过；两套 lint 均为 0 errors、89 warnings。差异格式检查通过（排除用户自有 AGENTS.md 的既存差异），AGENTS.md 和四份代理配置与备份哈希一致。
- 实际 release APK 包名 `com.donglan.chrona`、versionName `0.13.85`、versionCode `105`，`aapt` 验证未启用调试。新 debug、新 release 和 0.13.84 分发包均通过签名验证，证书 SHA-256 为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。
- 正式构建副本 `build/distributions/Chrona-0.13.85.apk`，1,216,481 字节，SHA-256 `16c4b9fe952124059ff7f401f2d4489772ffafa77d177057cf941c832be38073`；本轮尚未提交推送或发布到 GitHub。
- 修改前备份 `build/backups/20261003-001536-integration/tracked-baseline.zip`，同目录保留用户配置副本。ADB 实际查询无连接设备，未安装、强停或清除手机数据；完整备份恢复、覆盖升级、字体/读屏、桌面跨午夜、动画/启动耗时与提醒送达均待按 [设备清单](device-acceptance.md) 实测。未调用付费 AI。

## 2026-10-02 · 0.13.84 学期课表与特殊安排

- 学期边界、课程数/总时长、规律性判断与手动分类、同学期替换/其他学期保留、默认/记忆选择、删除、快照往返、原有 ICS 分组以及失败写入/恢复保留旧数据检查通过；文件 API 使用最小 Android 替身，业务逻辑与 JSON 库为实际实现。
- 用户提供的 ICS 仅在本机读取，未加入仓库：11 门、255 次安排完整分配。秋学期 10 门、113 次、特殊安排 9 次、网格 17 块、186 小时；冬学期 11 门、142 次、特殊安排 11 次、网格 19 块、234 小时。分界为用户确认的 2026-11-09。
- 原 15 组解析/布局回归和数据完整性回归通过；`assembleDebug`、`lintDebug` 通过，0 error、88 warning。本轮 diff 检查通过，保留用户的 AGENTS.md 和代理配置改动。
- 0.13.84 / versionCode 104，原签名证书验证通过；APK SHA-256 `83936d5157b90140e97c57396b14346e9db16fb791bd887b1ebf615d3cd238fc`。
- ADB 无连接设备；日期选择器、主题/大字体、旋转、实际视觉与触摸、完整备份恢复和覆盖安装未实机验收。修改前备份 `build/backups/20261002-terms`。

## 2026-10-02 · 0.13.83 本地 ICS 课表

- 首页共用字形边界对齐的下划线入口；课表页复用原生主题、安全区、玻璃卡片和浮窗。按真实起止时间汇总全部日期，长课连续绘制，冲突分列。
- `timetable_check.py` 15 组通过：重复与备注合并、折行转义、时长、重复规则、例外/修改/取消、日期列表、时区与夏令时、全天、跨日、版本覆盖、无限简单规则、错误文件及布局边界。`data_integrity_check.py` 通过。完整备份课表分支经代码审阅与 Android 编译，尚未实机往返恢复。
- `assembleDebug`、`lintDebug` 通过，0 error、85 warning；本轮 diff 检查通过（排除用户自行修改的 AGENTS.md）。版本 0.13.83 / 103；原签名证书验证通过，APK SHA-256 `4028d7135a78907d76abb2217f3b81f28eb9f4623d7bb486622b1d9341b90f3b`。
- 无连接设备，本机无完整可用模拟器镜像；文件选择、旋转、主题/大字体、浮窗、触摸与实际显示尚未验收。修改前备份 `build/backups/20261002-194935-timetable`。未发布到 GitHub。

## 2026-10-02 · 0.13.82 小组件弹幕刷新清理

- 两个小组件共用绑定逻辑在删除弹幕前选择空白项，解除旧文字动画；清理覆盖可见、单轨和全部隐藏状态。保留原有分轨、速度及间隔。
- `widget_danmaku_refresh_check.py` 编译并执行实际 WidgetDanmaku.bind，RemoteViews/ViewAnimator/ViewGroup 为按系统删除顺序实现的替身；旧顺序负例会残留动画文字，修复后 502 项通过。覆盖每个旧文字位置、无绘制帧的反复刷新、双轨转单轨、整体隐藏和恢复；原位置检查 68100 项通过。
- `assembleDebug`、`lintDebug` 和本轮 diff 检查通过；lint 为 0 error、82 warning。0.13.82 / versionCode 102，原签名验证通过；APK SHA-256 `63120364c521ea5afe69b333fb873b7daa72b033c5322395ce85ca872bda2b58`。
- 未进行真实桌面前后台、锁屏恢复及覆盖安装验收；替身回归不能替代厂商桌面动画测试。修改前备份位于 `build/backups/20261002-widget-danmaku`。

## 2026-10-02 · 0.13.81 分享入口、图片返回与更新页边距

- 分享入口移除按任务栈根节点显示“箭头＋收件箱”的布局分支，与主页加号入口共用单一 48dp 返回按钮；退出草稿保护及根任务返回收件箱逻辑保留。
- 图片查看页改用 24dp Lucide 矢量箭头，48dp 按钮内四边各 12dp，图形路径围绕视口中心对称。
- 更新页与设置页一致，关闭内容层 fitsSystemWindows，由滚动视口接收系统安全区，避免系统覆盖内容层左右 20dp、上下 24dp 留白。
- `assembleDebug` 与 `lintDebug` 通过：0 error、82 warning。本轮文件的 diff 检查通过；用户修改的 AGENTS.md 与代理配置保留。
- versionName 0.13.81 / versionCode 101，原签名验证通过；APK SHA-256 `f55f73418bafc56e6a026005efc9b1726ad4ad24ce60b08194922bcbc161c62c`。
- ADB 无连接设备；分享进入、图片按钮居中、更新页安全区/边距与覆盖安装尚未进行真机验收。修改前备份位于本地 `build/backups/20261002-1733-capture-viewer-update`。

## 2026-10-02 · 0.13.80 更新页、自定义颜色与嵌套浮窗

- 更新页采用按内容宽度排列的操作按钮、玻璃开关行和独立结果卡片，下载入口置于更新说明之前。自定义颜色接通实际调色窗口，输入去除空格后再解析，调色区可滚动。
- `custom_color_check.py` 执行实际入口/输入方法，覆盖自定义入口反复打开、不提前切换配色、普通配色选择、带空格/无 #/无效色值、滑杆同步。`dialog_layers_check.py` 执行实际表面/层级方法，覆盖一致透明度、三级模糊、逐层关闭恢复、深色与关闭模糊；均为替身测试，已通过。
- `assembleDebug` 与 `lintDebug` 通过：0 error、82 warning。文档链接检查通过。versionName 0.13.80 / versionCode 100，原签名验证通过；APK 1,315,670 字节，SHA-256 `98cb08b58bcba45716c4602a33ccc6ad0fbaea2a9c198c27fda550f089aef6f3`。
- 真机排版、大字体、软键盘、嵌套日期浮窗视觉效果及覆盖安装尚未验收。用户自行修改的 AGENTS.md 与代理配置文件不纳入本次提交。

## 2026-10-01 · 0.13.79 背景图片开关与布局

- `background_preference_check.py` 通过：执行实际偏好方法，覆盖旧图片默认启用、无图片不可启用、关闭保留 URI、再次启用、重新选择同一图片/新图片和恢复默认；SharedPreferences 与刷新入口为替身。
- `assembleDebug` 与 `lintDebug` 通过（0 error、83 warning）；文档链接检查通过。页面和小组件均通过 activeBackground 读取可显示图片，备份保持原 URI 并保存开关，失败回滚同时恢复两者。
- versionName 0.13.79 / versionCode 99，原签名验证通过。APK 1,315,330 字节，SHA-256 为 `34d8d27443aae86a168ee766219538deb47d1411c347df1bbb0b58ae1cd1f006`。
- 尚未进行真机布局、图片选择器、桌面小组件、备份恢复与覆盖安装验收。

## 2026-10-01 · 0.13.78 返回与 Dock 动画修复

- 根据用户设备反馈撤销 Activity 自定义进出动画；全仓核查无 Activity 转场覆盖残留，仅保留 Dialog 窗口动画。系统预测返回开关与详情条件返回保护保持不变。
- `dock_navigation_check.py` 48 项通过，新增可推进的帧队列验证远处起拖、长按、反向追随、快速松手的目标与接续、取消/多指/解绑/主体接管，以及关闭系统动画。`lifecycle_motion_check.py` 返回保护检查通过；独立复核未发现确定缺陷。
- `assembleDebug`、`lintDebug` 通过：0 error、83 warning。文档 11 文件/51 本地链接与锚点检查通过。versionName 0.13.78 / versionCode 98，数据库仍为 v9。
- APK 原签名 SHA-256 为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`；APK 1,314,678 字节，SHA-256 为 `0a94a1a2479502c37177b3ba0d082814dca719398fb67151c5fa955357c2e251`。
- 本轮未做设备安装、预测返回提交/取消观感、时间线弹窗滚动或 Dock 真机触摸验收。队列替身验证逻辑，不能证明硬件帧率或视觉效果。

## 2026-10-01 · 0.13.77 每日异步更新检查

- `automatic_update_check.py` 通过：使用 Android 与网络替身执行实际检查器，断言偏好读写、包版本和请求不在主线程；覆盖首屏后延迟、阻塞请求时主线程继续运行、自动请求合并、跨日/同日、失败静默、关闭自动检查、页面重建和待提示恢复。
- `GuideReleaseCheck` 通过：数字版本比较、正式 Release 元数据、同仓库 APK 地址验证及候选日程 ID 回归。独立只读复核未发现本轮新增线程或生命周期缺陷；手动检查仍独立于自动检查，允许用户立即重试。
- `:app:assembleDebug :app:lintDebug --no-daemon --console=plain` 通过，versionName 0.13.77 / versionCode 97。lint 为 0 error、83 warning；新增两项 ApplySharedPref 提醒来自明确在后台线程的同步持久化，避免将未完成的 apply 写入留给 Activity 生命周期等待。
- 原签名 SHA-256 仍为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。APK 为 1,316,658 字节，SHA-256 为 `42a87fb6dca0acce34f2b783545a2e475b74589e715363dd3dd719cdd0bdaaf9`。
- 本轮未进行真机启动耗时、帧率、下载或覆盖安装验证；离线线程约束检查不等同于实际零掉帧保证。
- 源码提交 `b79cc6f` 与 `v0.13.77` 标签已推送，[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.13.77) 已发布；复查 latest 返回 v0.13.77，稳定 APK 下载地址、大小和 SHA-256 与本地产物一致。

## 2026-10-01 · 0.13.76 统一动效与启动优化

- 完成 [全项目 UI 与启动巡检](motion-startup.md)，集中修改公共样式、转场和首屏关键路径；数据库 v9 不变，没有增加依赖或安装工具链。
- 最终 `:app:assembleDebug :app:lintDebug --no-daemon --console=plain` 通过，产物为 versionName 0.13.76 / versionCode 96。lint 无 error，81 项 warning，数量与上一版相同。
- Dock 33 项队列检查、详情草稿、后台协调和 SQLite 数据完整性检查通过。新增 `lifecycle_motion_check.py` 执行实际返回回调注册与中断恢复方法，Android/调度器/数据存储为替身；验证干净页面预测返回、编辑/保存保护、回调去重、活动或待调度任务保留及条件更新。
- `startup_metrics_check.py` 验证时间单位、P90、缺失样本不达标、启动 token 精确匹配、组件/初始显示关联及系统时间一致性。它只测脚本解析，**未测设备启动时间**；脚本先强停再设日志边界，防止上一轮日志被配对到当前启动。
- 独立复核发现并修正模糊失败后壁纸隐藏、暂停取消日历后的限流、动画中漏报启动完成、恢复任务与新 claim 竞态、旧日志误配等边界。统一窗口动画和图片异步生命周期经源码复核。
- APK 原签名验证通过，证书 SHA-256 为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。APK 为 1,615,050 字节，SHA-256 为 `bd7d7c37d5faf47e32a62a7505ba314122e93d1ff7306cb50257ad43173665f8`。文档检查覆盖 11 个文件与 51 项本地链接/锚点，SVG 解析通过。
- 四个实现/版本提交与 `v0.13.76` 标签已推送；构建源码提交 `eaa4201`。[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.13.76) 发布后复查 GitHub latest，稳定下载地址、版本、APK 大小与 SHA-256 均一致。
- ADB 无连接设备；没有执行安装、实际冷/热启动测量或手势视觉测试。**无法确认 1 秒启动已经达标**；正式说明保留这一限制。浅/深色、壁纸、嵌套弹窗、连续操作、旋转、系统动画关闭及覆盖安装仍需真机验收。

## 2026-10-01 · 0.13.75 软件审查与数据保护

- [完整清单](quality-audit.md) 先列出确认问题，再实施；最终 21 项，包括实施时发现的备份背景图说明矛盾。数据库无迁移，没有调用付费模型、增加依赖或修改工具链。
- `detail_draft_check.py` 提取实际 SaveSession，用最小 Activity/Bundle 替身验证旧 Bundle 在保存完成后的合并、原始标题保留、完成草稿移除和回调归属。实际编辑器的 Android 重建仍待设备验证。
- `data_integrity_check.py` 执行生产快照/状态/窗口 SQL：并发 SQLite writer 被快照事务隔离，附属库触发器、revision、删除 ID 高水位、外键、READY 降级及小组件历史溢出和全天边界检查通过。Android 自带 SQLite 的 ATTACH/WAL 行为未做设备验证。
- `config_backup_check.py` 执行实际 ConfigBackup，偏好和密钥存储使用内存替身；空 AI 配置在新旧设备配置下、密钥保留/覆盖、待配置草稿及半空配置拒绝均通过。未测试真实密钥加密层。
- `NetworkSafetyCheck` 运行实际 ChatCompletionClient、LinkFetcher、RequestControl 和 BestEffortPreview。HTTPS 连接全部为本地替身：预览 append/reasoning/close 失败不丢结果且仅一次请求，取消前不发 POST，阻塞链接/模型取消、慢速 SSE 总 deadline、剩余 timeout 和连接身份清理通过。`AiRequestCheck`、`LinkFetcherCheck` 回归通过。
- `job_execution_check.py` 提取实际 process/cancel/wasStopped 协调方法，work body 和 JobScheduler 为替身；验证取消标记早于 interrupt 的判断、串行重新调度、旧 finally 不移除新执行，以及延迟系统 stop、owner 在 cancel 内退出时仍先取消 waiter。两个实际 processOnce catch 的接入由源码复核确认；替身 work body 不直接覆盖它们，不能据此声称系统 JobScheduler 真机行为通过。
- 独立复核发现并修正旧 Bundle 覆盖已保存备注、类别提示恢复遗漏、取消误标失败和等待者漏取消；复核后的定向回归通过。
- 最终 `assembleDebug` 与 `lintDebug` 通过：0.13.75 / versionCode 95。lint 无 error，81 项 warning；新增一项建议使用 AndroidX EXIF 的提示，本次保留 minSdk 已支持的系统 API，未增加依赖。Dock 的 33 项队列回归、完整输出分页、43 项语义解析及 v1–v4、v6→v7、v8→v9 迁移范围检查通过；早期迁移检查已限制到原本测试的范围。
- APK 原签名校验通过，证书 SHA-256 保持 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`；构建包 1,323,922 字节，SHA-256 为 `896eab67688458a8117b819ac7048a2d74543e7ed15ec9026ad69b245dec89c5`。文档检查覆盖 10 个文件、47 项本地链接/锚点和 SVG，diff 检查通过。
- 8 个实现与准备提交、`v0.13.75` 标签已推送。[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.13.75) 发布后复查 latest 接口，版本、稳定 APK 地址、1,323,922 字节大小与远端 SHA-256 均一致。构建源码为 `8eaa5be36293da0fa39972ab97b48e0c621baf98`，发布结果以本条文档提交补记。
- ADB 检查无连接设备。未安装、不测试真实供应商或真实数据恢复；图片方向/缩放、旋转/返回、小组件桌面显示、系统调度、覆盖安装和整体流畅度仍待真机验收。

## 2026-10-01 · 0.13.74 Dock 点击与连续高光

- 用户反馈 0.13.73 底部按钮失效；此前的构建与静态复核未覆盖子按钮在 UP 后延迟执行点击、detach 取消回调的运行时顺序。
- `checks/dock_navigation_check.py` 编译并执行实际 Dock 类，使用最小 UI 队列替身，33 项通过。覆盖三按钮/角标/短按/连点、按钮内漂移、间隙点击与越界、长按拖动/取消/多指、高光绘制位置、布局刷新、中断与松手连续性。在同一队列模型中，0.13.73 源码复现了普通按钮点击丢失。该模型检查不等同于 Android 运行时测试。
- 独立复核确认点击先于按钮重建、高光随页面动画更新、中断保存真实起点、间隙越界不误触。
- `:app:assembleDebug :app:lintDebug --no-daemon --console=plain` 通过，APK 为 versionCode 94 / versionName 0.13.74。lint 为 80 项 warning，无 error。
- 原签名校验通过，证书 SHA-256 保持 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`；数据库无变更，diff 与文档本地链接检查通过。
- ADB 无连接设备，真实 Android 事件分发、点击反馈、动画手感、TalkBack 和覆盖安装仍待真机验证。

## 2026-10-01 · 0.13.73 Dock 手势与横滑优化

- `:app:assembleDebug :app:lintDebug --no-daemon --console=plain` 通过，APK 元数据为 versionCode 93 / versionName 0.13.73。
- `apksigner verify --print-certs` 通过，证书 SHA-256 为 `3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`，与已发布版本一致。
- 独立静态复核覆盖长按/横滑接管、子项与角标 CANCEL、多指取消、松手与越界、快速选择队列和背景采样缓存。端点拖动期间页面重建会重新收集亚克力控件，防止新卡片漏刷。
- `git diff --check` 和用户文档本地链接/锚点检查通过。数据库无变更。
- ADB 未发现连接设备，Dock 手感、TalkBack、旋转、动态壁纸效果、实际帧时间及覆盖安装未做真机验证。性能改动减少每帧重复工作，不代表已经测得帧率提升。

## 已验证

- 2026-10-01：`0.13.72`（versionCode 92）的六步使用引导通过 `assembleDebug`、`lintDebug` 与独立静态复核；引导页码边界、旋转恢复及旧版引导升级逻辑已核对。设备交互尚未真机回归。

- 2026-10-01：`0.13.71`（versionCode 91）的 `assembleDebug`、`lintDebug`、`GuideReleaseCheck` 和 `git diff --check` 通过；APK 签名 SHA-256 与旧版一致。GitHub 正式 Release 已发布，上传资产大小和 SHA-256 与本地产物一致，`releases/latest` 返回本版。当前无连接设备，首次引导、多日程点击/旋转、浏览器下载和覆盖安装尚未真机回归。

- 2026-09-27：`0.13.33` 修复点击 Dock 栏必崩（`IllegalStateException: ScrollView can host only one direct child`：页面主体原先直接挂在 `ScrollView` 下，而切页交叉淡化要把旧内容快照作为第二个子 View 插进容器父级，`ScrollView` 继承 `FrameLayout` 因而通过了原有判断）；页面主体改套一层 `FrameLayout` 宿主，`UiStyle.swap` 的子 View 容纳判断收紧为 `hostsSeveralChildren()`。收件箱「选择收件类型」弹窗收窄（屏幕 72% 与 300 dp 取小）并让行文字居中。任务详情右上角菜单去掉标题、精简为「输出预览 / 完整输出 / 解析用量 / 来源链接 / 删除任务」，「删除任务」用新增的 `UiStyle.danger()` 标红。新增 `JsonFormat`：模型完整输出与实时预览都按 JSON 缩进（完整值走 `toString(2)`，未闭合的流式 JSON 用状态机重新缩进，纯文本原样）。确认删除任务对话框文案精简为一句。提示词要求把截止时刻写进标题文字，并已知截止必须给出 10–360 分钟的提醒。图片右上角移除按钮由 48 dp/20 sp 收到 36 dp/16 sp。versionCode 53；`assembleDebug` 与 `lintDebug` 通过，ADB 覆盖安装成功（设备包信息 versionCode 53/versionName 0.13.33）。设备实测：点击 Dock「收件箱」切页成功且 `logcat -b crash` 无 FATAL（此前必崩，崩溃栈为 `UiStyle.swap` → `ScrollView.addView`）；「选择收件类型」弹窗宽度约为屏幕 72% 且各行为居中文字；详情右上角菜单为四项、无标题、「删除任务」显示为红色；「模型完整输出」页把该任务的回复渲染为两空格缩进的 JSON 树；删除确认对话框显示新文案（已点「取消」，未删除数据）；图片缩略图右上角 `×` 明显变小。提示词变更为纯文本，需真实 API 请求才能复验其是否按 10–360 分钟设置提醒。

- 2026-09-27：`0.13.32` 修复端点方向拖动时亚克力卡片不重新采样（提前 `return` 跳过了每帧重绘），端点恢复 0.3 阻尼跟手；待确认与失败之和显示在 Dock 收件箱角标，收件箱「待确认」「失败」筛选胶囊各带数量；筛选胶囊统一为同宽、圆角降为 12；数量角标统一走 `countBadge()`：胶囊上的计数贴**文字**右上角（贴容器角会被 chip row / 横滑容器裁掉），Dock 上的计数贴图标右上角并带 6 dp elevation（否则会被带 elevation 的条目压在图标下面、数字不可见）；记录页空内容提交改用应用内 `Feedback` 玻璃胶囊，不再出现 `EditText.setError()` 的系统白色弹框；`UiStyle.swap` 改为新旧内容同时淡出淡入的交叉淡化（220 ms）：快照加在容器**自己的父级**并按容器布局坐标落位，父级滚动偏移同样作用于新旧两层；外观页因为页面背景透明，快照取整屏（`UiStyle.swap(stage, page, …)`）以免 backdrop 从 ghost 后面透出。versionCode 52；`assembleDebug` 通过，ADB 覆盖安装成功。截图与 `uiautomator` 树确认：收件箱四枚筛选胶囊等宽且圆角变小、「待确认」右上角带角标、Dock 角标已落在收件箱图标右上角（item 中心 477，角标 477..536，图标 444..510）、记录页空提交弹出玻璃胶囊；切页途中截图可见新旧两页同时半透明叠加，确认是同时交叉淡化而非串行淡出淡入。角标位置与交叉淡化的手感仍需手动确认。

- 2026-09-27：`0.13.31` 三页改跟手分页（相邻页预览、子 View 收 `ACTION_CANCEL`、每帧重绘亚克力表面）、待确认数量移到 Dock 收件箱角标、记录入口统一为 `UiStyle.recordEntry()`、重排「记录一件事」页（顶栏 ✓ 保存、标题右侧图标入口、去掉拆分复选框）、标题与搜索框中线对齐。versionCode 51；`assembleDebug` 与 `lintDebug` 通过，ADB 覆盖安装成功（设备包信息 versionCode 51/versionName 0.13.31）。截图与 `uiautomator` 树确认：首页不再有「查看收件箱」整行、Dock 收件箱带角标、手机端记录入口已变成与宽屏一致的亚克力圆形 `+`（只有图标、非纯色）、记录页顶栏为 ←/✓ 且标题右侧为三个图标、无拆分复选框；拖动过程中相邻页确实跟手渲染、亚克力卡片随页面移动、松手后 `content-desc` 已变为「收件箱，当前页面」，且在本已是最左/最右的分页继续外拖时页面保持不动（不再露出未模糊的裸背景条）。拖动松手是否误开二级页面、以及未达阈值回位的手感仍需手动确认。
- 2026-09-27：`0.13.30` 外观页配色网格内容居中，并调整「毛玻璃与层级」的文案与滑杆提示。versionCode 50；`assembleDebug` 与 `lintDebug` 通过，ADB 覆盖安装成功。
- 2026-09-27：`0.13.29` 外观页配色改两列网格；页头搜索框缩到 36 dp、时间排序只留一个箭头并与设置按钮合并成同一个胶囊控件；页头顶部留白收到 2 dp；首页/收件箱/日程支持左右快滑换页；详情页左右拖拽未达阈值时的回位动画加长并改为更强缓出；处理结果通知改到新的 `chrona_results` 高重要性渠道以支持悬浮通知。versionCode 49；`assembleDebug` 与 `lintDebug` 通过，`apksigner` 证书与既有记录一致，ADB 覆盖安装成功（设备包信息 versionCode 49/versionName 0.13.29）。截图确认：页头「拾时 · Chrona」上移、搜索框变矮、↑ 与设置齿轮合成同一个胶囊，设置页显示 0.13.29，外观页「主题配色」为两列网格且选中项在名称尾部带 ✓。左右滑动换页、回位动画手感与通知是否真的悬浮弹出仍需真机操作确认。
- 2026-09-27：`0.13.28` 重画应用图标前景（表盘缩小并加主题色描边，浅橙背景成为均匀边框，单色环绕到同一半径）；任务详情页去掉右侧滚动条；`TaskDetailPagerLayout` 与 `CandidatePagerScrollView` 消耗 touch slop，左右切换不再在跟手第一帧跳出死区距离。versionCode 48；`assembleDebug` 与 `lintDebug` 通过，`apksigner` 证书与既有记录一致，ADB 覆盖安装成功（设备包信息 versionCode 48/versionName 0.13.28）。图标按同一矢量在本地渲染比对过；在设备上打开详情页并滚动后截图，右侧确认不再出现滚动条。分页跟手手感与全天复选框点击行为仍未在设备上复验。
- 2026-09-27：`0.13.27` 在日程页与收件箱加入搜索框与时间排序开关（位于标题与设置齿轮之间），修复全天复选框选中后立即消失（勾选值不再从 view 状态集合推导），应用图标背景改为浅橙 `#FFB74D`。versionCode 47；`assembleDebug` 与 `lintDebug` 通过，`apksigner` 证书 SHA-256 与既有记录一致，ADB 覆盖安装成功（设备包信息 versionCode 47/versionName 0.13.27）。截图确认日程页标题/搜索框/「时间 ↑」/设置齿轮同排，且「即将到来」按时间升序排列；全天复选框点击行为与收件箱搜索的实际输入未在设备上复验。
- 2026-09-27：`0.13.26` 详情页对齐字段列、自绘可访问全天控件、标题与页码布局，并移除原始内容展开入口；按 taskId 保留详情会话日程页，退出时清除；未保存修改弹窗改为“放弃/保存”，外部点击继续编辑。versionCode 46；`assembleDebug` 与 `lintDebug` 通过。ADB 安装尝试因无线设备 offline 失败，未能进行真机视觉或手势回归。
- 2026-09-27：`0.13.25` 优化首页时间线几何/字号、详情字段对齐与按钮/横滑、外观主题卡高度、分享进入记录页隐藏拆分选项（普通记录页保留）和模型思考输出查看；悬浮页弹出时背景窗口缩放动画时长加倍。versionCode 45；`assembleDebug`、`lintDebug` 和 ADB 安装通过，设备包信息为 versionCode 45/versionName 0.13.25。截图实测确认详情顶部连体按钮、删除图标和全天方框视觉；任务横滑、首页时间线及 reasoning 响应仍待设备验证。
- 2026-09-27：`0.13.24` 修正首页时间线同日竖线、日期点和事件轨道对齐；统一详情字段标签/图标/全天/紧凑按钮；收紧外观页主题色卡高度。versionCode 44；`assembleDebug`、`lintDebug` 和 ADB 安装通过。
- 2026-09-27：`0.13.23` ZIP 备份增加自定义背景图片，恢复时在公共 `Pictures/Chrona` 重建 URI 并校验内容；旧 ZIP 无背景字段时保留本机背景。versionCode 43；`assembleDebug`、`lintDebug` 与 ADB 安装验证通过（设备包信息为 versionCode 43/versionName 0.13.23）；分身自定义背景导出/导入及时间区视觉回归待用户确认。
- 2026-09-27：`0.13.22` 修复备份导入的主线程调用错误；恢复设置使用 application context，并在主线程刷新界面。换库前后校验清单、任务和候选数量，增强失败回滚。versionCode 42；`assembleDebug`、`lintDebug` 与 ADB 安装验证通过（设备包信息为 versionCode 42/versionName 0.13.22）；分身导入恢复成功；用户确认收件箱、日程、图片和普通附件齐全，背景图片未包含（符合备份范围）。
- 2026-09-27：`0.13.21` 修复 ZIP 恢复时 `PRAGMA wal_checkpoint` 调用方式导致的 SQLite 导入失败；收件箱状态筛选结果卡加入轻量错峰进入动画并保留滚动位置。versionCode 41；`assembleDebug`、`lintDebug` 与 ADB 安装验证通过，ZIP 实际导入和筛选动画回归待用户操作。
- 2026-09-27：`0.13.20` 新增完整 ZIP 备份/恢复入口与导入校验、回滚处理。versionCode 40；本次构建和真机操作尚未验证（ADB 当前离线）。壁纸图片不包含在备份中；API 密钥默认排除；日历恢复取决于权限和本机日历 provider。
- 2026-09-27：`0.13.19` 增加预设与自定义主题色，调整记录页附件选择/列表和详情交互，支持首页时间线数量设置与横滑浏览，并修复亚克力切换/首帧模糊闪变。versionCode 39；`assembleDebug` 与 `lintDebug` 已通过，真机视觉回归待确认。
- 2026-09-27：`0.13.18` 统一设置/外观返回按钮与详情页的位置，修正亚克力筛选控件在模糊/备用态的描边，并将共用信息气泡改为主题亚克力样式。versionCode 38；构建与真机验证待确认。
- 2026-09-27：`0.13.17` 修复外观页浅/深主题原位同步，扩大亚克力混色、二级透明度和模糊强度范围；选中态保留模糊，统一设置返回按钮。手机 Dock 改为悬浮并补足底部留白，详情入场采样更平滑且菜单标题居中。versionCode 37；`assembleDebug` 与 `lintDebug` 通过，真机视觉回归待确认。
- 2026-09-27：`0.13.16` 修复收件箱卡片亚克力背景，并微调暗/亮主题的混色；详情页增加图片右上角移除和横滑操作，统一菜单浮层、模型输出入口和顶部按钮，并在重新解析前确认。versionCode 36；`assembleDebug` 与 `lintDebug` 通过，真机回归待完成。
- 2026-09-27：`0.13.15` 外观增加模糊强度和面板纯色浓度调节；二级控件透明度支持即时预览，外观配置导入导出包含新增选项。versionCode 35；`assembleDebug` 与 `lintDebug` 通过，设备视觉回归待确认。
- 2026-09-27：`0.13.14` 横向拖动时刷新可见卡片的壁纸模糊采样，统一 Dock 子项圆角；外观页原位刷新并在绘制前恢复滚动位置。详情页仅壁纸延伸到状态栏下方，卡片及控件仍在安全区内；其他后台 Activity 恢复时仍可能重建一次。versionCode 34；构建与设备验收待主代理确认。`0.13.12` 的筛选留白和详情菜单方角也待本轮复验。
- 2026-09-27：`0.13.13` 滚动时重绘可见亚克力卡片以连续采样壁纸；详情附件按钮样式收紧并统一。versionCode 33；构建与设备验收待完成。`0.13.12` 的筛选末项留白与详情菜单方角修复仍待真机复验。
- 2026-09-27：`0.13.12` 为收件箱筛选列表末项增加滚动留白，并统一清除详情弹窗的矩形窗口背景。versionCode 32；构建与设备回归待验收。
- 2026-09-27：`0.13.11` 调整手机导航 Dock 为有间距的主题色圆角矩形，记录按钮独立放在 Dock 右侧并缩小底部占用；卡片/Dock 使用进程级缓存的静态壁纸模糊，API 31+ 弹窗使用页面实时模糊并以首帧门控避免背景闪烁。versionCode 31；本轮构建与真机视觉验证待结论。

- 2026-09-27：`0.13.10` 增加输入、解析候选和 Chrona 日历事件去重选择，并修复主题切换后一级浮层首帧背景模糊缺失；versionCode 30。构建与 lint 通过，未进行真机验证。日历插入成功到数据库关联保存之间仍有崩溃窗口，重试可能重复写入。
- 2026-09-27：`0.13.9` 优化一级浮层背景模糊、主题切换、详情菜单、图片裁切和手机 dock 圆角；versionCode 29。构建与 lint 通过，ADB offline，手机视觉效果未复验。
- 2026-09-27：`0.13.8` 增加公共普通文件附件，数据库版本 6、versionCode 28。构建与 lint 通过；系统分享导入、公共 Downloads 写入/打开和详情操作尚未设备验证。

- 2026-09-27：`0.13.5` 为收件箱任务详情切换加入方向对应的整页滑出/滑入，并理清单日程与多日程的内外层分页手势；已构建并安装，未做手机交互测试（按用户要求）。
- 2026-09-27：`0.13.6` 将任务详情页改为紧凑顶栏与无边框日程编辑行，折叠次要信息，并让收件分页跟随手指拖动；`assembleDebug` 通过，安装因设备 offline 未完成，未做手机交互测试（按用户要求）。
- 2026-09-27：`0.13.7` 精简首页并合并为两日时间轴，记录入口并入手机底栏，收件箱类型筛选折叠为漏斗按钮；加入暗色壁纸遮罩、捕获内容编辑、多图附件与图片查看保存。数据库版本 5；`assembleDebug` 通过，未安装或进行手机界面测试（按用户要求）。
- 2026-09-27：`0.13.4` 重做多日程分页为跟手拖动与松手吸附动画，分页容器避开系统返回手势边缘；待构建和多日程真机专项验证。
- 2026-09-27：`0.13.3` 为全部 Activity 启用 Android Predictive Back；`assembleDebug`、`lintDebug`、原签名指纹核对通过，并在 Android 16 手势导航设备上从任务详情左边缘返回首页成功。
- 2026-09-27：`0.13.2` 修复详情页外层分页抢截多日程横滑的问题，并增加 270 毫秒分页吸附动画；已随 `0.13.3` 通过构建与 lint，多日程横滑专项真机回归仍待补充。
- 2026-09-27：`0.13.1` 将普通任务状态移到紧凑页头，并为同一收件的多条日程增加吸附式横向分页和跟随当前页的固定确认按钮；`assembleDebug`、`lintDebug` 与签名验证通过，设备 ADB offline，交互回归待完成。
- 2026-09-27：`0.13.0` 的类型时间默认、多选批量删除、详情左右分页和预览内层滚动已通过 `assembleDebug` 与 `lintDebug`；设备交互回归待完成。
- 2026-09-26：`0.11.2` 的「分享进入后返回收件箱」「解析中行显示已用时间」「轻提示避开键盘」已通过 `assembleDebug` 和 `lintDebug`；**无可用 Android 设备，仍待真机回归**。
- 2026-09-27：`0.11.3` 修复无 Key 配置在新设备上导入失败、配置文件无大小/版本校验、导出只读取已保存字段，以及排队计时导致列表每 5 秒重建；构建、lint 和签名验证通过，设备安装、启动、收件箱及状态筛选已验证。
- 2026-09-27：`0.11.4` 修复流式响应中的 JSON `null` 被写成文本 `null`、污染模型完整输出的问题；针对性样例、`assembleDebug`、`lintDebug`、签名核对及设备安装启动通过。尚未触发真实模型重解析，以免未经确认消耗 API 额度。
- 2026-09-27：`0.12.0` 重排任务详情：状态与待确认日程优先，原文/解析预览/Token/链接改为摘要或展开区；单日程使用安全区内固定确认按钮，多日程保留各自保存入口，删除与重解析降为次要操作。构建与 lint 通过，设备拒绝新版安装，视觉回归待完成。
- 2026-09-26：`0.11.1` 的应用内轻提示（取代 39 处系统 Toast）与导入配置时的一次性外观发布已通过 `assembleDebug` 和 `lintDebug`；**无可用 Android 设备，提示的位置、动画与遮挡关系仍待真机回归**。
- 2026-09-26：`0.11.0` 的流式预览、外观传播、选择器与弹窗统一、诊断页分区卡片、配置导入导出与动效补充已通过 `assembleDebug` 和 `lintDebug`。**无可用 Android 设备（手机无线调试显示 offline）**，因此以下均属「已实现、待真机回归」：流式输出能否被真实服务以 SSE 返回、超长输出的分页与截断提示、自定义背景图的显示与内存占用、导入导出在系统文件选择器中的实际读写、切换配色后各页面是否立即更新、以及新增动效在不同刷新率上的手感。
- 2026-09-26：`0.10.0` 的首页概览、页内分区和分批浏览、半透明磨砂背景、侧边导航与系统栏适配已通过 `assembleDebug` 和 `lintDebug`；无可用 Android 设备，视觉、触控和不同机型上的系统栏仍待设备回归。
- 2026-09-26：`0.9.0` 的三板块导航、独立记录/设置/外观页面和浅色/深色/配色方案通过 `assembleDebug` 与 `lintDebug`；无可用 Android 设备，交互、系统壁纸配色和不同屏幕尺寸仍待设备回归。
- 2026-09-26：`0.8.0` 的 `assembleDebug` 与 `lintDebug` 通过；全天日期换算、链接正文实体替换的独立 JVM 检查和数据库 v1/v2/v3→v4 的 SQLite 迁移检查通过。新增历史筛选、全天编辑、界面及动画、预设与缓存统计**尚未真机回归**。
- 2026-09-26：指定旧版 Debug 密钥重建 APK；`apksigner verify --print-certs` 确认 APK 证书 SHA-256 与交接文档记录的旧证书完全一致。覆盖安装仍待设备验证。

- 2026-09-26：`assembleDebug` 构建成功。
- Debug APK 已安装到一台实际 Android 手机，应用首页启动正常。
- 自定义 API 解析成功：测试输入 `2026-09-28 10:00-11:00 Chrona test meeting` 生成了对应的 10:00–11:00 草稿。该服务接受的测试模型名为 `deepseek-flash`；实际模型名必须以各服务的响应为准。
- 用户确认系统日历写入测试通过。测试输入可能仍留在应用收件箱中。
- 2026-09-26：`0.2.0` 的日程删除、草稿生命周期、剪贴板读取与按行批量提交已通过 `assembleDebug` 构建；**真机回归尚未进行**。
- 2026-09-26：`0.3.0` 的图片输入与数据库 v1→v2 迁移已通过 `assembleDebug` 构建；**真机回归尚未进行**，含升级安装（保留手机上的既有数据）验证。
- 2026-09-26：`0.4.0` 的系统语音输入已通过 `assembleDebug` 构建；**真机回归尚未进行**，需要一台装有系统语音识别服务的设备。
- 2026-09-26：`0.5.0` 的按需联网检索（链接正文抓取）与数据库 v2→v3 迁移已通过 `assembleDebug` 构建；抓取与正文提取逻辑另外用本机 JVM 直接跑过样例（链接提取边界、实体解码、脚本剔除、https/http 真实抓取、失效域名返回空、多链接合并）。
- 2026-09-26：`0.5.1` 修复图片请求读超时（30 秒 → 图片 180 秒）并通过 `assembleDebug` 构建。
- 2026-09-26：`0.6.0` 的诊断页面与事件日志已通过 `assembleDebug` 构建，**并在真机上正常工作**（读取到 `user_version=3`、完整表列、`JobScheduler` 待处理任务与事件日志）。
- 2026-09-26：`0.6.1` 的瞬时网络失败自动重试与并发重复运行拦截已通过 `assembleDebug` 构建；**尚未真机回归**。
- 2026-09-26：`0.6.2` 的「解析中卡死」恢复（`reconcile()`）、被中断时的日志与状态处理、收件箱解析中耗时计数、诊断页「进程已运行」已通过 `assembleDebug` 构建。
- 2026-09-26：`0.7.0` 的用户发起 job（API 34+）、常驻通知与过期 job 跳过已通过 `assembleDebug` 构建，并**在真机上验证**：`dumpsys jobscheduler` 显示 `Priority: 500 [MAX]`、`userInitiatedApproved: true (started as UIJ: true)`、`Started with foreground flag: true`、`HAS_FOREGROUND_EXEMPTION`，入队到运行 2.2 秒；`dumpsys notification` 显示常驻通知（id 20006、channel `chrona_parsing`、`flags=ONGOING_EVENT|USER_INITIATED_JOB`）。A/B 验证：提交后 0.4 秒切到桌面，请求仍在后台跑完（`task=8 ok in 7619ms`）；留在应用内的对照同样成功（`task=9 ok in 9054ms`）。**过期 job 的跳过分支未能直接触发**（已完成的 job 会从调度器注销），只验证了正常解析不受影响（`task=10 ok in 6145ms`）。
- 2026-09-26：真机上仍观察到一次切后台后 `job stopped task=7 worker=true`。因为 `Thread.interrupt()` 不会终止阻塞中的请求，该请求继续执行并由自动重试接住（`attempt 1 failed after 76180ms` → `attempt 2/3` → `response ok in 92304ms`），任务没有卡住；用户随后开启了系统的「后台进程锁」。**因此「切后台解析是否一定完成」在真机上仍无保证，只是不再丢失。**
- 2026-09-26：**`0.6.2` 已在真机回归通过（vivo V2527A / Android 16，`api.deepseek.com` + `deepseek-flash`）**。两件事同时得到证实：
  1. `reconcile()` 在真机首次生效——`20:19:53` 日志出现 `reconciled task=5 stuck parsing -> failed`，把一条因进程被系统清理而永久卡在「解析中」的输入恢复成可重试状态，随后手动重试成功。
  2. **图片输入首次在真机上完整成功**——`20:20:58` 发起（152365 B 图片 + 15 字文字），`20:21:22` 返回，**23.4 秒 / 6996 token / 1 条草稿**。草稿为「完成微积分作业」，开始时间 `2026-09-28 12:00`（后天中午），描述为「书上例题自己完成：例17,18,19,20；记住结论：(1) {(1+1/n)^n}单调增有上界，以e为上确界。」——这段内容**只存在于图片中**（用户输入的文字只有「后天中午提醒我完成微积分作业。」），证明图片确实被模型读懂，而不是被忽略。
- 2026-09-26：同批真机数据（用 `adb exec-out run-as` 读出设备上的 `chrona.db` 直查）显示 `user_version=3`、4 条输入全部 `needs_review`，草稿时间换算正确：`今天晚上10点`→09-26 22:00、`明天早上7点`→09-27 07:00、`后天中午`→09-28 12:00；时间不明确的输入（「睡觉之前…我一般12点睡觉」）被正确标为 `needs_confirmation=1` 而不是硬猜。
- 2026-09-26：真机日志（`0.6.1`）确认了一个此前未识别的故障：图片输入会永久卡在「解析中」。证据链为——`19:20:37` 图片请求开始后 15 分钟内无任何日志（同时段文字请求 14.4 秒 / 12.8 秒成功）；诊断页「内存中日志: 8 行」恰好等于 `19:37:03` 之后的全部日志，说明**应用进程在请求进行中被系统杀掉**（vivo 后台清理），180 秒读超时因此从未有机会记录；进程死亡同时带走了持久化 job（`19:39:54`「待处理解析任务: 0 个」）。因详情页重试按钮在 `processing` 状态下禁用，该输入既拿不到结果也无法重试。`0.6.2` 针对此问题修复。
- 2026-09-26：真机实测（vivo V2527A / Android 16，`api.deepseek.com` + `deepseek-flash`）：文字输入的异步解析、草稿生成与 `needs_review` 状态正常（实测 token 2856 / 4803，`今天晚上10点` 正确解析为 22:00）；诊断页数据全部正确。
- 2026-09-26：**图片请求在真机上仍未成功过一次**（此条记录的是 `0.6.0`/`0.6.1` 的状态），三次失败全部是网络层错误（`UnknownHostException`、`SocketException: Software caused connection abort`），没有任何一次到达服务器，因此当时**「图片能否被该服务接受」没有结论**。已确认 `deepseek-flash` 官方支持图片且请求格式与官方示例一致（来源：[DeepSeek 图像理解文档](https://api-docs.deepseek.com/zh-cn/guides/vision/)）。**该问题已在 `0.6.2` 的真机回归中得到肯定答案：服务接受图片，且模型读懂了图片内容（见上）。**
- 2026-09-26：真机实测（一台 Android 手机，Vivo）：文字输入的异步解析、草稿生成与 `needs_review` 状态正常（实测 token 4803，`今天晚上10点` 正确解析为 22:00）。**图片请求在该版本上因 30 秒读超时失败**，即 0.5.1 所修问题；`0.5.1`/`0.6.0` 的图片路径与其余功能仍待回归。
- 2026-09-26：数据库 `v1 → v3` 迁移用 Python `sqlite3` 按真实 SQL 验证通过（版本 1 真实建表语句建库 + 插入数据 → 顺序执行三条 `ALTER TABLE` → 原有行、token 值与草稿全部保留，新列为 NULL，外键级联删除仍生效）。注意：这是对 SQL 本身的验证；Android `onUpgrade` 回调链路仍未经真机升级安装验证，因为手机上的旧版本已被卸载（签名不兼容）。
- 安装包为 `app\build\outputs\apk\debug\app-debug.apk`。**升级安装验证仍未完成**：手机当前是新装而非从 0.1.0/0.2.0 升级而来。
