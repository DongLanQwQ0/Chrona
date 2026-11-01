# 当前交付：更正文稿独立页已更新；数学19项等待服务恢复

## 已交付候选

- 0.15.89/251 于2026-11-01 15:56:05正常覆盖安装V2527A（10.197.184.141:38283），包管理器确认版本/更新时间并已正常启动。APK：build/candidates/0.15.89/Chrona-0.15.89.apk，com.donglan.chrona、原升级证书、非debuggable。未发Release；正式latest仍v0.15.81。
- 最新要求已取代88折叠方案：入口仅在课程名称标题行最右侧，纯文字公共紧凑按钮，无上下展开箭头；删除正文末尾入口及inline文稿/holder/开合状态。ClassroomCorrectedActivity复用SettingsPageLayout和同一Markdown/MathJax，首屏就绪入场、系统返回、整页滚动，旋转/图片返回保留偏移，新触摸优先。源文稿/公式/代码/来源编号/导出不改。
- publishedSummary只读解析已发布快照，允许新轮处理中阅读已有文稿；existingSummary保留原忙时guard，路径/身份/账号检查不变。页面不调用AI、学校或改检查点；账号撤销实际关闭并dispose。
- debug/androidTest、release+lintRelease通过。常规竖浅62、横深font1.3 62；长文font1.3 19；仅文稿checkpoint稳定竖浅/横深/减少动画各15；首次入场/恢复/系统返回/减少动画20通过。图片偏移1231→1231、1487→1487，同reader，真实旋转与返回parent不重建正文。
- 固定候选独立UI最终复审无未解决P1/P2或关键证据缺口。证据build/corrected-secondary-ui/及docs/validation-history.md；稳定JPEG在stable-portrait/stable-landscape/stable-reduced，各经两次硬件frame commit，完整返回/正文/公式/代码可见。
- 首批部分JPEG是空帧/缺箭头，未计通过；motion-first/second夹具失败原样保留。仅补等待实际onPresented消费，并修异步还原倍率后误快照0的问题；最终系统倍率1，生产动效未改，不重跑已有效矩阵。

## 数学19项仍未完成

- 仅微积分16、线性代数3，其它课不重跑；最终须实际验证19项最新格式、有效来源与配图依据。另两项原“已完成”记录尚未核对新契约，不算本轮成功。
- 一篇精简完整笔记，affairs集中事务，assignments只明确行动；分段仅内部处理。复用已提交且原id/text与分块匹配的修正文稿，缺图走原下载/校验/去重。旧run精确留同课审计，不另导出旧成品副本。
- 原19项首次选择见build/classroom-failure-20261101/math-selection.json。17项重跑前6项5个JSON结构失败、1个HTTP413后有序停止：成功0/失败6/取消11；素材/文稿保留，无活动AI队列。详情stopped-results.json及验证记录。
- 86实机：微积分10月29日第9节、线代10月27日第9节顶层字符串而非对象，finish_reason=stop；1–2节无图修复请求0.13MiB返回413。不归因于截断、图片或臆测服务阈值。
- 87以后首次JSON指令明确对象/转义，repair/manual/network恢复只引用原user完整schema/资料、不重复大prompt。历史与冻结模型/缓存选项只追加，不重构；每阶段唯一结构修复、引用检查、取消与原子回滚不变。
- 普通292、增强JSON719、独立只读检查；loopback实际请求102988→54596字节；Android真实JSON根类型/可选图片安全投影12项通过。不能代替真实模型格式与质量验收。

## 唯一下一步与阻塞

- 先只读核对那两项旧完整数学记录的最新契约，尚未完成（期间被用户新UI要求中断）。19项范围不缩小。
- 服务恢复前不重复批量调用：87真实单课首请求HTTP403、无正文；用户授权的一次237字节无课堂资料健康请求也在1036ms返回403（类别未明确）。证据build/classroom-regeneration-validation/phone-ai-health.txt，诊断辅助包com.donglan.chrona.classroom.probe已卸载。
- 恢复后先续单课，真实通过再扩至失败数学课堂。禁止改密钥/模型/身份/网络绕过；不能宣称数学总结完成或服务恢复。

## 设备与文件边界

- GlobalSend 93已明确交还真机，无已知同机冲突；只有实际冲突才协调，不常规跨聊天广播。所有设备操作归root。
- 仅复用Chrona_Classroom_36 / emulator-5554，不克隆/清数据/改DPI或分辨率/中止其它任务。电脑产物在项目build；图片先压缩JPEG。
- 手机本次/data/local/tmp/chrona/classroom-failure-20261101/window.xml及空任务目录已清理。新包流式安装，无共享目录测试文件，不删用户收件或应用数据。
- 旧/data/local/tmp/chrona/classroom-failure-20261031/曾清理被策略拒绝，不绕过。
- 原生JSON报告仍在隔离包external files的classroom-json-projection-20261101/report.json，run-as读取被拒绝；待正常应用清理，不能换身份绕过。错误命名的读取拒绝输出已改read-denied.txt，不作有效JSON。
- 新UI原生JPEG/ZIP/报告已完整转入build/corrected-secondary-ui，本轮生成文件按精确名单清理；三个新stable/background任务目录已删除。前置fixture及旧failure.jpg/旧corrected-only-draft-expanded.jpg保留。PageMotion新图/报告已转入项目并清理，系统scale=1。

## 未授权的讨论项与他人改动

- 监视自动总结全局/课程三态仍在讨论，拟置标题下信息行右侧、后续课程左侧，未确认布局，不擅自实施。
- 本批不改Dock；core接口见docs/Communication/to-external-agent.md。
- 保护AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml及tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1；不提交或清理这些他人改动。
