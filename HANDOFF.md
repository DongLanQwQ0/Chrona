# 当前交付：课程事务（0.15.83）

## 已构建候选

- `build/candidates/0.15.83/Chrona-0.15.83.apk`：245／0.15.83，3398869字节，com.donglan.chrona，非debuggable，原签名。debug/release及两种lint通过；仅本地候选，未装手机、未发Release。包括下述0.15.82改动及本批课程事务。
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
- 本轮没有真实AI请求，没有改手机账号／偏好／安全设置。图片细微内容与软件遮挡的语义仍需真实AI核图；本地启发式不是100%语义识别。

## 课程事务已实现并离线验收

- 新批新增affairs_schema=1：notes只放学习内容，affairs集中非行动事务（与notes同结构），assignments保留具体行动及提取。overview不再散放事务。旧未完成轮次冻结旧契约和原消息；不改旧成品、不按关键词补分类。旧契约虽不要求affairs，一旦实际出现也检查其来源、结构与图片，不能绕过新消费者的校验。
- 成品与导出按“重点概览／学习笔记／课程事务”呈现，空区省略。专页复用SettingsPageLayout和同一Markdown阅读器，单课程选择、按课堂实际日期排列、每节来源返回原课；只投影已有affairs及明确assignments，不合并重复、不判断变化、不调用AI。单节详情操作行和学校设置提供入口，不改既有三图标次序或四视图。
- 默认332、AI传输538、Figure67、增强2505（含事务267）通过；后续补验旧轮额外事务10、概览图片限制39、合法分类示例的事务专项269。消费者52、reader41通过。独立契约审查无生产P1/P2，修复其发现的测试假阳性。
- `build/course-affairs-research/`：普通竖屏浅色、横屏深色、普通屏幕font1.3各17项原生检查通过。真实课程切换、来源原课身份、两课同名图片本地scope、旋转、账号停用及读写隔离已测。曾捕获返回偏移103→381px，现保存导航前位置并原生复验103→103px。无真实学校或AI调用，未将本地合成输入视为模型分类质量验收。
- 最终图见portrait-verified、landscape-accepted、large-text及large-text-render；最后仅补等实际可见后的大字初屏9项，旧空白帧不算通过。独立UI复审已关闭两项无障碍名称问题与缺图证据，无未解决P1/P2。原生截图均压缩JPEG，未改模拟器密度／分辨率。0.15.82稳定检查点为4b170e8。
- 最后补验`native-busy-choice.txt`19项：有正文／无文档错误态选择另一忙碌课程时，字段和数据身份一致；同课程忙刷新仍保留原投影。已修复并通过独立UI复审，不把前一课程的事务留在新的筛选状态。

## 尚未完成与唯一下一步

- 手机总结失败诊断明确延期：用户最新要求停止手机操作，留待下次。尚未查看失败项的具体记录，不宣称其已修复，不安装或续跑。
- 独立代码工作已完成；唯一下一步是在用户恢复手机操作后读取实际失败课堂的处理记录，再根据具体错误决定修复或续跑；同时验证0.15.83覆盖升级和真实课程分类／核图。当前没有批准重新操作手机，不能以无人值守默认授权绕过明确暂停。

## 手机、模拟器与临时文件

- 手机10.195.179.127:42381此前已连通，正式版仍0.15.81/243。正常Launcher进入拾时；内部Activity不允许外部直接启动，不改exported或权限。QQ通知与GlobalSend前台切换曾打断导航，最后已正常进入课堂历史（31节），尚未进入失败项；现已按用户要求停止所有真机操作（含清理），未重跑AI。
- 本次手机目录`/data/local/tmp/chrona/classroom-failure-20261031/`，含history.xml、window.xml及dashboard.xml（后者可能未生成）。电脑`build/classroom-current-failure/`的history.xml、current.jpg含偶发非Chrona窗口，不进Git或交付；删除命令被工具策略拒绝，未绕过，留待用户允许的执行通路清理。其它本次XML也仅属本任务。
- 共享模拟器emulator-5580复用Chrona_Classroom_36。所有原生夹具已结束，本批合成课堂与专用course-affairs目录已清理；已清理先前两个隔离包的classroom-export-layout及auto-summary/portrait-light中本批六个文件。后者原有10月28日failure.jpg保留，不删前置文件、其它应用数据或AVD。未终止模拟器。
- 用户跨任务偏好已按直接要求写到全局记忆更新便笺：询问操控手机后长时间无回复可按不在场继续；本次明确停止优先，不能以该默认授权绕过暂停或系统权限拒绝。

## 源码及既有发布边界

- 当前checkout为`experiment/glass-lens`，候选源码提交`6f074e5`已推送origin/main，安装包对应记录为`build/candidates/0.15.83/candidate.json`。保护AGENTS.md、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1与外部写入的docs/Communication/messages/2026-10-31.jsonl，不纳入本批提交或清理。
- 本批不动Dock；core交付见`docs/Communication/to-external-agent.md`。正式latest仍v0.15.81，不为补丁新增Release，下一次手机测试需实际验证0.15.83升级及失败记录。
- 历史真实C课整稿、v0.15.81升级与Release清理证据见`docs/validation-history.md`、`build/classroom-c-audit/`与`build/release-cleanup/20261031-v0.15.81/`，不重复进入当前工作单。
