# 当前交付：Dock滚动缩小已删除；待真机更新及数学全量核对

## 当前优先交付

- 用户明确删除主页Dock随滚动缩小、靠边及仅保留图标的行为；首页、日程、收件箱的主导航滚动后保持完整尺寸、标签与位置。保留点击、长按、拖动高光、惯性、切页和独立「＋」的原切页动效，横屏侧栏几何不变。
- 0.15.90/252已于2026-11-01 19:08:11正常覆盖V2527A，首次安装时间仍2026-09-26 18:54:37。APK为build/candidates/0.15.90/Chrona-0.15.90.apk，正式包名、原升级签名、非debuggable；最终release/lint通过，不发Release。
- 三处滚动收起接线及唯一minimize状态已删除；公共弹簧、按压、惯性参数不变。竖浅187、横深189项真实触摸通过，build/dock-scroll/portrait/及landscape/的报告/JPEG保留原实例、三页>160dp/回顶/切页/系统返回/首尾拖出/＋打开返回证据。独立UI最终复审无未解决P1/P2及关键证据缺口；导航队列183、Chrome39通过。
- 同批回归发现并修正：＋同状态settle旁路禁用使缓存失配；presence/blur epsilon漏写精确终态；横屏已pin的侧栏将shell提升后遮住未pin的＋，给wide captureHost沿用同一pin。原失败及精确状态在portrait-first、portrait-second、portrait-diagnostics、landscape-first；未按失败候选交付。
- 真机10.197.184.141:39343；GlobalSend97已安装并明确交还。90已安装且已正常启动；数学快照确认21唯一课堂（微积分18、线代3，无重复key），4份完整成品全为旧affairs_schema=0/强度1，最新格式完成数为0。最新单课续跑仍403，现已定位GROUP_DISABLED／API Key所属分组已停用；唯一下一步由服务方恢复分组或用户提供有效Key后，root先续单课通过，再推进全21节，不再盲试批量。
- 数学只读诊断已正常安装/运行后卸载，phone-report/phone-all-runs/phone-provider.json为实际证据。仅mathAudit跳过Application并确认、已有缓存无symlink/.bak/.new才读；两个Provider初始化无副作用。按key/非空run UUID分离成品与轮次，覆盖无summary的失败run；无原消息/模型回复/凭据输出。显式providerFailure仅返回已保存403的320字符脱敏摘录，独立复审及真实内存脱敏检查通过，无新网络调用。portable/图片头不冒充语义质量。

## 本轮核查与纠正

- 真机快照微积分18、线代3共21唯一key；四条完整记录（10月20日、10月8日、9月24日、9月22日）均旧格式。原19只描述已选批次，漏选的两条旧记录也须纳入全量更新，不缩小用户范围。
- 四份旧成品结构/来源与引用图片头检查通过，但不是新格式或语义验收；其中两份已有较新失败run，不能把新run审计归到旧成品。完整快照已取得，不再把数量/重复目录列为未知。

## 已交付候选

- 0.15.89/251 于2026-11-01 15:56:05正常覆盖安装V2527A（10.197.184.141:38283），包管理器确认版本/更新时间并已正常启动。APK：build/candidates/0.15.89/Chrona-0.15.89.apk，com.donglan.chrona、原升级证书、非debuggable。未发Release；正式latest仍v0.15.81。
- 最新要求已取代88折叠方案：入口仅在课程名称标题行最右侧，纯文字公共紧凑按钮，无上下展开箭头；删除正文末尾入口及inline文稿/holder/开合状态。ClassroomCorrectedActivity复用SettingsPageLayout和同一Markdown/MathJax，首屏就绪入场、系统返回、整页滚动，旋转/图片返回保留偏移，新触摸优先。源文稿/公式/代码/来源编号/导出不改。
- publishedSummary只读解析已发布快照，允许新轮处理中阅读已有文稿；existingSummary保留原忙时guard，路径/身份/账号检查不变。页面不调用AI、学校或改检查点；账号撤销实际关闭并dispose。
- debug/androidTest、release+lintRelease通过。常规竖浅62、横深font1.3 62；长文font1.3 19；仅文稿checkpoint稳定竖浅/横深/减少动画各15；首次入场/恢复/系统返回/减少动画20通过。图片偏移1231→1231、1487→1487，同reader，真实旋转与返回parent不重建正文。
- 固定候选独立UI最终复审无未解决P1/P2或关键证据缺口。证据build/corrected-secondary-ui/及docs/validation-history.md；稳定JPEG在stable-portrait/stable-landscape/stable-reduced，各经两次硬件frame commit，完整返回/正文/公式/代码可见。
- 首批部分JPEG是空帧/缺箭头，未计通过；motion-first/second夹具失败原样保留。仅补等待实际onPresented消费，并修异步还原倍率后误快照0的问题；最终系统倍率1，生产动效未改，不重跑已有效矩阵。

## 全量数学总结仍未完成

- 范围是用户要求的所有微积分与线性代数，原已选批次19不代表全部；按实际lesson key去重确认总量，不把备份目录重复行算成新课堂。最终须实际验证全部最新格式、有效来源与配图依据，旧complete状态不算本轮成功。
- 一篇精简完整笔记，affairs集中事务，assignments只明确行动；分段仅内部处理。复用已提交且原id/text与分块匹配的修正文稿，缺图走原下载/校验/去重。旧run精确留同课审计，不另导出旧成品副本。
- 原19项首次选择见build/classroom-failure-20261101/math-selection.json。17项重跑前6项5个JSON结构失败、1个HTTP413后有序停止：成功0/失败6/取消11；素材/文稿保留，无活动AI队列。详情stopped-results.json及验证记录。
- 86实机：微积分10月29日第9节、线代10月27日第9节顶层字符串而非对象，finish_reason=stop；1–2节无图修复请求0.13MiB返回413。不归因于截断、图片或臆测服务阈值。
- 87以后首次JSON指令明确对象/转义，repair/manual/network恢复只引用原user完整schema/资料、不重复大prompt。历史与冻结模型/缓存选项只追加，不重构；每阶段唯一结构修复、引用检查、取消与原子回滚不变。
- 普通292、增强JSON719、独立只读检查；loopback实际请求102988→54596字节；Android真实JSON根类型/可选图片安全投影12项通过。不能代替真实模型格式与质量验收。

## AI服务依赖

- GROUP_DISABLED是保存的真实服务回复，明确为Key所属分组停用，不是推测；账号/分组权限由服务方处理，不能用更换模型/网络绕过。用户已知晓具体原因，未再要求其自行猜测。
- 19:12:48–19:12:49单课403已证实无图、0.05MiB、无正文/Token。此后只有已保存错误读取，原始历史/冻结配置保留，无新AI请求。证据resume-one-audit.json及phone-provider.json；只读诊断辅助包已卸载。
- 恢复后先续单课，真实通过再扩至失败数学课堂。禁止改密钥/模型/身份/网络绕过；不能宣称数学总结完成或服务恢复。

## 设备与文件边界

- GlobalSend97及拾时90均已安装，手机当前由root使用后留在拾时首页；只有实际冲突才协调，不常规跨聊天广播。
- 仅复用Chrona_Classroom_36 / emulator-5554，不克隆/清数据/改DPI或分辨率/中止其它任务。电脑产物在项目build；图片先压缩JPEG。
- 手机本次/data/local/tmp/chrona/classroom-failure-20261101/window.xml及空任务目录已清理。新包流式安装，无共享目录测试文件，不删用户收件或应用数据。
- 旧/data/local/tmp/chrona/classroom-failure-20261031/曾清理被策略拒绝，不绕过。
- 原生JSON报告仍在隔离包external files的classroom-json-projection-20261101/report.json，run-as读取被拒绝；待正常应用清理，不能换身份绕过。错误命名的读取拒绝输出已改read-denied.txt，不作有效JSON。
- 新UI原生JPEG/ZIP/报告已完整转入build/corrected-secondary-ui，本轮生成文件按精确名单清理；三个新stable/background任务目录已删除。前置fixture及旧failure.jpg/旧corrected-only-draft-expanded.jpg保留。PageMotion新图/报告已转入项目并清理，系统scale=1。
- 本轮6个AVD dock-scroll专用目录已转回项目并按精确名单清理，fixture任务已删除、偏好已恢复，原validation应用保留。实际记录见docs/validation-history.md。

## 未授权的讨论项与他人改动

- 监视自动总结全局/课程三态仍在讨论，拟置标题下信息行右侧、后续课程左侧，未确认布局，不擅自实施。
- 本轮仅按用户新授权删除Dock滚动缩小，不改弹簧/高光几何或核心手感；core历史接口见docs/Communication/to-external-agent.md。
- 保护AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml及tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1；不提交或清理这些他人改动。
