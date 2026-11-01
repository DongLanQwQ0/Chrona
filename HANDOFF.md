# 当前交付：历史课堂按最新格式重总结及 JSON 失败修复

## 已构建候选

- `build/candidates/0.15.84/Chrona-0.15.84.apk`：246／0.15.84，原签名、非debuggable。debug/release及lintDebug/lintRelease通过；2026-11-01 11:57正常覆盖安装并dumpsys确认。实际旧失败详情已成为短错误，处理记录确认两次服务均finish_reason=stop，故原失败为收到非法JSON，并非length或图片未请求。
- 0.15.85/247：正式签名release产物在build/candidates/0.15.85；debug/release及两种lint完整退出成功（production-01585-confirmed.txt）。13:45手机dumpsys确认已升级85。包含公共首次呈现窗口时机修复、安全JSON具体诊断与请求MiB只读审计。数学旧批在升级前已无工作服务，未中断在途总结。
- 0.15.86/248：build/candidates/0.15.86/Chrona-0.15.86.apk，正式签名、非debuggable，release+lintRelease成功（6m11s），debug及测试包构建成功。尚未安装手机：14:07前台为钉钉TeleVideoConfActivity，主停止触控/安装，不切走用户会议。
- `build/candidates/0.15.83/Chrona-0.15.83.apk`：245／0.15.83，3398869字节，com.donglan.chrona，非debuggable，原签名。debug/release及两种lint通过；2026-11-01 11:31已正常覆盖安装手机，dumpsys确认245／0.15.83。未发Release。包括下述0.15.82改动及本批课程事务。
- `build/candidates/0.15.82/Chrona-0.15.82.apk`：244／0.15.82，3389781字节，非debuggable，原签名证书`3f64d76960de8f2ee9705c2abd44f0d321851fda7edf9c6bc85d02f75b876770`。debug/release构建与两种lint通过；正式包未安装、未发Release。
- 导出：多节先用公共面板选平铺／课程分层，名称为“月-日-节次-年”，重名后缀不暴露内部ID。单节／批量均写每节课表下课时间（创建＋修改，FileTime及NTFS扩展），优先账号课表匹配，否则按标题最后节次读取当前校历配置。
- 总结：1–3并发默认3，整节批队列与跨入口FIFO生成门分别限额；取消只作用本批／所属任务，失败不擅自重发。
- 整理强度：1原有完整总结、2顺畅重组、3精简讲义（默认），保留自学过程、考核细节和不确定性。滑块右侧介绍页含同一长原文和三档示例；保存、11字段草稿、旋转及备份已接线，原未完成历史冻结原档位及消息。
- 引导页复制按钮保留12dp底部空间，去掉双重上间距，复用原组件。
- 配图：96×96局部比较与固定近似组锚点，质量优选而非一律较晚帧；预算在时间覆盖内优选。空白置信度降低，逐图实际可见依据与排除理由入image_audit.figures，拒绝图不能跨块或整稿复活。新轮figure_policy版本固定，旧历史不重写。CommonMark AST统一引用／过滤／导出，实际reader图片token补图；代码与引用式图片保持一致。

## 有效证据与边界

- `build/classroom-export-layout/`：导出406＋恢复9＋历史标签5；批队列22、FIFO19、偏好96；原生设置145、Android ZIP及深色横屏入口50、受控并发25。UI独立审核通过，普通横屏介绍页可将标题滚出，示例和阅读位置可达；没有将实际TalkBack朗读或极端DPI场景计作已验。
- `build/classroom-image-dedup/`：质量87、门禁及缓存恢复67、默认管线332、增强2238、reader19；AST与reader独立反例复审通过。Android实际位图／CommonMark运行8项通过，曾发现混色黑边导致空白误判，已修统计过渡带并原生复验；不裁改原图。
- 实样微积分10月29日第1–2节image-48近空白却被旧成品标为错误证明。最终质量为low／0.04784，完整image-61为normal／0.77251；实样导出的本地门禁64项通过，派生预览`build/classroom-image-dedup/filtered-export-preview/`。用户原导出不改。这是本地门禁验证，不是重新调用真实AI的语义验收；原始候选未从导出提供，不能断言旧去重是唯一根因。
- 上述离线候选阶段没有真实AI请求；本次真机重总结见当前状态。没有改手机账号／偏好／安全设置；本地启发式不是100%语义识别。

## 课程事务已实现并离线验收

- 新批新增affairs_schema=1：notes只放学习内容，affairs集中非行动事务（与notes同结构），assignments保留具体行动及提取。overview不再散放事务。旧未完成轮次冻结旧契约和原消息；不改旧成品、不按关键词补分类。旧契约虽不要求affairs，一旦实际出现也检查其来源、结构与图片，不能绕过新消费者的校验。
- 成品与导出按“重点概览／学习笔记／课程事务”呈现，空区省略。专页复用SettingsPageLayout和同一Markdown阅读器，单课程选择、按课堂实际日期排列、每节来源返回原课；只投影已有affairs及明确assignments，不合并重复、不判断变化、不调用AI。单节详情操作行和学校设置提供入口，不改既有三图标次序或四视图。
- 默认332、AI传输538、Figure67、增强2505（含事务267）通过；后续补验旧轮额外事务10、概览图片限制39、合法分类示例的事务专项269。消费者52、reader41通过。独立契约审查无生产P1/P2，修复其发现的测试假阳性。
- `build/course-affairs-research/`：普通竖屏浅色、横屏深色、普通屏幕font1.3各17项原生检查通过。真实课程切换、来源原课身份、两课同名图片本地scope、旋转、账号停用及读写隔离已测。曾捕获返回偏移103→381px，现保存导航前位置并原生复验103→103px。无真实学校或AI调用，未将本地合成输入视为模型分类质量验收。
- 最终图见portrait-verified、landscape-accepted、large-text及large-text-render；最后仅补等实际可见后的大字初屏9项，旧空白帧不算通过。独立UI复审已关闭两项无障碍名称问题与缺图证据，无未解决P1/P2。原生截图均压缩JPEG，未改模拟器密度／分辨率。0.15.82稳定检查点为4b170e8。
- 最后补验`native-busy-choice.txt`19项：有正文／无文档错误态选择另一忙碌课程时，字段和数据身份一致；同课程忙刷新仍保留原投影。已修复并通过独立UI复审，不把前一课程的事务留在新的筛选状态。

## 当前证据、交付条件与唯一下一步

- GlobalSend线程已完成其1.0.74升级并明确让出手机；本聊天接管，未绕过安装权限。当前手机10.197.184.141:40137，已从243升级至245／0.15.83。
- 用户最新明确收紧重总结范围：仅微积分、线性代数，其余课程不重跑。现场全部历史31节，其中未总结筛选只有微积分（甲）Ⅰ2026-09-29第3–5节；需要从完整历史筛选两门课的实际条目数，不凭31推断数学课数。复用已提交的修正字幕和现有PPT，缺失图片按原下载逻辑补齐；不另做旧总结副本。新的重总结启动独立轮次，采用当前偏好及affairs_schema=1，不续接旧五字段失败轮；旧run精确字节作为本机处理记录保存到各课runs子目录，不当作新轮上下文。
- 实际失败已定位：该微积分课有1097条字幕，原始263图／旧候选75图；页面泄露JSONException的完整9210字符响应。严格JSON解析在末尾失败，image_requests错误嵌套在image_assessment，根对象缺失闭合。不能靠追加括号或抽取字段冒充正确结果，需完整对象的有界结构修复。
- 只读处理记录有两次笔记与图片规划响应：10-31 21:44:51输入35924／输出6797／缓存4992；22:06:34手动续跑输入49871／输出5782／缓存35712；均上下文图片0。旧页面未展示finish_reason，故不能把“已响应”判为JSON有效或直接认定输出截断。证据在build/classroom-failure-20261101/failure-audit.json和unfinished-lesson.json；实际压缩图lesson-current.jpg。
- 已确认主题滑块、右侧介绍按钮、增强整理开启及并发3，未改全局设置。手机当前已在84通过历史多选确认启动19节重总结：微积分16、线性代数3，包含9月29日失败项；C课未选。当前实际历史27条，不能再用此前31条作当前分母。最后观察UI“总结0/19 · 进行中3”，ClassroomWorkService前台2043存活，任务已启动但尚无完成数量，不把入队当作模型成功。
- 精确UI选择与启动证据：build/classroom-failure-20261101/math-selection.json、math-selection-complete-progress.txt、regen-final-confirm.json、regen-started.json；相同日期多节均分别选择。曾因测试脚本把滚动计数行误作固定头而漏选两项，已用实际ScrollView边界重新完整核对并补齐到19，不使用17项作为完整范围。
- 0.15.84/246代码已完成：共享有界JSON阶段及安全错误、只读finish_reason及执行状态、历史多选确认重总结、修正文稿缓存复用、缺图补齐。JSON owner普通418/专项87、增强2606、AI538/Figure67；审计53；主代理批队列25、修正缓存/精确审计/缺图原下载链20通过。实渲染和集成构建尚在进行，不宣称真机修复或真实AI重总结完成。
- 主代理修正缓存检查已补真实cleanUnused路径：25项通过。独立check发现“run清理后仅按块数复用会接受已变化字幕”P2，现按原文id/text直接比较，新更正文件及清理前均保留blocks；反例复验已关闭。
- 审计原生竖屏浅色26项通过，实际frame-ready记录2个OnDraw、布局完成、alpha1及入场2帧；横屏诊断发现公共UiMotion在旧无焦点portrait窗口提前消费firstPresentation，新landscape实例0入场帧。85最小修公共时机，独立UI check指出不可阻塞无焦点分屏，已在canPresent显式允许可见multiWindow；需要增量原生横屏/大字/分屏和独立复审，不改既定参数。测试等待曾误读中途View树，现主线程完整边界及实际绘制取证，不删除动效断言。
- 13:41恢复后宿主原进程句柄均已消失，旧无线端口拒绝连接；用户提供10.197.184.141:38283，实机确认84、无ClassroomWorkService。历史27条，未总结17条。用户明确说明网络变化导致多项失败并授权重新总结。只重跑当前失败的数学课，已成功两项不重做；先读取85具体诊断区分网络失败、HTTP413与JSON结构问题。新的重跑选择与结果尚未启动，不能计作完成。
- 13:51在85启动失败17项重跑；13:59前6项仍失败（5项JSON、1项HTTP413），已通过批量停止按钮有序停止：成功0、失败6、取消11，不继续反复发送同一路径。两次实际JSON响应均stop；10月29日1–2节第二请求0.13MiB且上下文图片0，413不能未经核对就归因于图片体积。素材/修正字幕保留。证据retry17-started.json、stopped-results.json、retry-json9-audit.json。
- 86/248只补只读具体诊断：Android对根数组/字符串的JSONObject构造错误不同于host jar，85泛化了该形态；当前无法确认真实响应根类型。修复投影并补可选assignments.images类型检查，专项194通过，已构建，待上机读取现有失败；不改生成/历史、不盲补JSON。
- 公共UiMotion真实分屏/无焦点、等待中取消/脱离/重新附着、减少动画14项已通过：native-visibility-split-two-stage.txt、visibility-accepted/report.json及压缩JPEG。SystemUI真实分屏，task1382不变，Settings持焦点、target windowFocus=false仍onPresented一次且alpha1/y0；取消和脱离不迟到，animator scale1.0→1.0恢复。首次instrument杀进程使旧可见筛选为0，改为同一AppTask等待root手动系统分屏后才实测，不冒充此前失败为通过；独立UI最终复审通过，无未解决P1/P2或关键UI证据缺口，范围不含真实AI生成质量。
- 唯一下一步：手机会议结束且可安全操作后升级86，只读查看10月29日第9节/线代10月27日原失败的具体根类型；据真实诊断修复生成链，再恢复17项数学总结，并核对另外两项完整记录的最新契约。所有19项最新格式、既有修正复用、缺图补齐与有效图片依据须以实际结果验收，不能只凭入队、离线检查或旧完整总结判定。不发小版本Release。
- 课堂监视自动总结偏好是新讨论需求：全局自动总结/上传图片，按courseId不自动/自动/跟随全局覆盖。已解释控件拟放现有信息行右侧、后续课程左边，公共菜单；用户仍在确认布局，本批没有实现此讨论项，不据“完成所有”越过讨论阶段。

## 手机、模拟器与临时文件

- 上次手机10.195.179.127:42381已连通，正式版为0.15.81/243。正常Launcher进入拾时；内部Activity不允许外部直接启动，不改exported或权限。QQ通知与GlobalSend前台切换曾打断导航，最后进入课堂历史（31节）但尚未进入失败项；该次已按用户要求停止操作，未重跑AI。本次恢复及交接状态以上节为准。
- 本次恢复使用10.197.184.141:40137，临时文件仅放手机`/data/local/tmp/chrona/classroom-failure-20261101/`和项目`build/classroom-failure-20261101/`。原有20261031目录另行保留其已记录的清理边界，不混入本次文件。
- 本次手机目录`/data/local/tmp/chrona/classroom-failure-20261031/`，含history.xml、window.xml及dashboard.xml（后者可能未生成）。电脑`build/classroom-current-failure/`的history.xml、current.jpg含偶发非Chrona窗口，不进Git或交付；删除命令被工具策略拒绝，未绕过，留待用户允许的执行通路清理。其它本次XML也仅属本任务。
- 共享模拟器emulator-5580复用Chrona_Classroom_36。所有原生夹具已结束，本批合成课堂与专用course-affairs目录已清理；已清理先前两个隔离包的classroom-export-layout及auto-summary/portrait-light中本批六个文件。后者原有10月28日failure.jpg保留，不删前置文件、其它应用数据或AVD。未终止模拟器。
- 用户跨任务偏好已按直接要求写到全局记忆更新便笺：询问操控手机后长时间无回复可按不在场继续；本次明确停止优先，不能以该默认授权绕过暂停或系统权限拒绝。

## 源码及既有发布边界

- 当前checkout为`experiment/glass-lens`，候选源码提交`6f074e5`已推送origin/main，安装包对应记录为`build/candidates/0.15.83/candidate.json`。保护AGENTS.md、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1与外部写入的docs/Communication/messages/2026-10-31.jsonl，不纳入本批提交或清理。
- 本批不动Dock；core交付见`docs/Communication/to-external-agent.md`。正式latest仍v0.15.81，不为补丁新增Release；0.15.83真机覆盖升级及滑块可见已验证，剩余为上述JSON修复和全量重总结。
- 历史真实C课整稿、v0.15.81升级与Release清理证据见`docs/validation-history.md`、`build/classroom-c-audit/`与`build/release-cleanup/20261031-v0.15.81/`，不重复进入当前工作单。
