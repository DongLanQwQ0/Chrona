# 当前交付：Dock滚动缩小已删除；待真机更新及数学全量核对

## 当前优先交付

- 用户明确删除主页Dock随滚动缩小、靠边及仅保留图标的行为；首页、日程、收件箱的主导航滚动后保持完整尺寸、标签与位置。保留点击、长按、拖动高光、惯性、切页和独立「＋」的原切页动效，横屏侧栏几何不变。
- 最新候选0.15.90/252：build/candidates/0.15.90/Chrona-0.15.90.apk，正式包名、原升级签名、非debuggable；最终release/lint通过，未更新真机，不发Release。
- 三处滚动收起接线及唯一minimize状态已删除；公共弹簧、按压、惯性参数不变。竖浅187、横深189项真实触摸通过，build/dock-scroll/portrait/及landscape/的报告/JPEG保留原实例、三页>160dp/回顶/切页/系统返回/首尾拖出/＋打开返回证据。独立UI最终复审无未解决P1/P2及关键证据缺口；导航队列183、Chrome39通过。
- 同批回归发现并修正：＋同状态settle旁路禁用使缓存失配；presence/blur epsilon漏写精确终态；横屏已pin的侧栏将shell提升后遮住未pin的＋，给wide captureHost沿用同一pin。原失败及精确状态在portrait-first、portrait-second、portrait-diagnostics、landscape-first；未按失败候选交付。
- 唯一下一步：真机恢复后先确认无活动总结/用户编辑，正常覆盖90并核对版本，再取得数学全量只读快照。原USB未连接，旧10.197.184.141:38283拒绝；双方mDNS无真机，GlobalSend已明确交还且未安装97；用户无人值守，不反复请求连接或重复旧端口。
- 数学只读诊断未安装：仅mathAudit跳过目标Application初始化并等待确认、构造Store前要求已有缓存且无symlink/.bak/.new；合并manifest两个Provider初始化无副作用，独立审查关闭P2。按实际key输出unique/duplicates，run_matches_summary要求非空UUID相等，run元数据均单列。修正版辅助包/日志在build/math-completion-audit-20261101/Chrona-Math-Audit.apk及probe-build-final.txt；尚未取得真机快照。portable只证明结构/来源/图片白名单，decodeBounds只证明图片头，不冒充内容/图片语义质量。

## 本轮核查与纠正

- 最新真机只读历史筛选显示微积分18节，其中4条已完成（10月20日、10月8日、9月24日、9月22日）；原工作单16节微积分/19节数学只描述原已选批次，不是完整数学库存。按用户原要求“微积分和线性代数”全量核对，不把漏入旧批次的两条旧完整记录排除。线代3节仍待当前快照确认，预计总21节。
- UI读记录时前台切到GlobalSend，已停止触控；GlobalSend96现已安装并明确交还。root准备复用既有签名诊断辅助程序增加mathAudit只读缓存投影：课程/课堂身份、格式版本/档位、完成状态、来源/图片校验及安全处理元数据，不输出消息历史/原始响应/凭据，不访问学校或AI，不写课堂数据。该程序测试后卸载；安装或权限拒绝不绕过。
- Dock交付后取得当前完整数学快照，按实际课堂key去重核对四条完整记录的新契约，纠正目标/剩余数量；服务403未恢复前不重跑批队列。

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

- 已准备独立审查的只读快照探针；恢复连接后核对四条完整数学记录的新契约，并纠正全部与剩余数量。
- 服务恢复前不重复批量调用：87真实单课首请求HTTP403、无正文；用户授权的一次237字节无课堂资料健康请求也在1036ms返回403（类别未明确）。证据build/classroom-regeneration-validation/phone-ai-health.txt，诊断辅助包com.donglan.chrona.classroom.probe已卸载。
- 恢复后先续单课，真实通过再扩至失败数学课堂。禁止改密钥/模型/身份/网络绕过；不能宣称数学总结完成或服务恢复。

## 设备与文件边界

- GlobalSend97因同一真机离线未安装，已明确交还；只有实际冲突才协调，不常规跨聊天广播。所有设备操作归root。
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
