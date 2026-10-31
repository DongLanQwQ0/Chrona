# 当前交付：课堂导出、并发、笔记整理强度与配图质量

## 已构建候选

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

## 尚未完成与唯一下一步

- 手机总结失败诊断明确延期：用户最新要求停止手机操作，留待下次。尚未查看失败项的具体记录，不宣称其已修复，不安装或续跑。
- 课程事务仍未落地：成品分“学习笔记／课程事务”；事务集中评分、考试、作业、期限、出勤和规则变更。新页面只按真实课程身份汇总各节已有事务、按日期分组，保留必要来源；不合并重复、不判断新旧规则、不再调用AI分析、不加额外说明、不改旧总结。用户最后要求无人在场继续剩余任务；本批提交收口后的下一步是实现该独立功能，保持手机暂停，不把其当手机故障的依赖。

## 手机、模拟器与临时文件

- 手机10.195.179.127:42381此前已连通，正式版仍0.15.81/243。正常Launcher进入拾时；内部Activity不允许外部直接启动，不改exported或权限。QQ通知与GlobalSend前台切换曾打断导航，最后已正常进入课堂历史（31节），尚未进入失败项；现已按用户要求停止所有真机操作（含清理），未重跑AI。
- 本次手机目录`/data/local/tmp/chrona/classroom-failure-20261031/`，含history.xml、window.xml及dashboard.xml（后者可能未生成）。电脑`build/classroom-current-failure/`的history.xml、current.jpg含偶发非Chrona窗口，不进Git或交付；删除命令被工具策略拒绝，未绕过，留待用户允许的执行通路清理。其它本次XML也仅属本任务。
- 共享模拟器emulator-5580复用Chrona_Classroom_36。所有原生夹具已结束，恢复自身偏好／目录；figure-native自己创建的图片及空目录已清理。尚需清理本次两个隔离包外部files/classroom-export-layout及batch包auto-summary/portrait-light（证据已拉回；不清用户数据／AVD）。
- 用户跨任务偏好已按直接要求写到全局记忆更新便笺：询问操控手机后长时间无回复可按不在场继续；本次明确停止优先，不能以该默认授权绕过暂停或系统权限拒绝。

## 源码及既有发布边界

- 当前checkout为`experiment/glass-lens`。保护AGENTS.md、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1与外部写入的docs/Communication/messages/2026-10-31.jsonl，不纳入本批提交或清理。
- 本批不动Dock；core交付见`docs/Communication/to-external-agent.md`。正式latest仍v0.15.81，不为补丁新增Release，下一次手机测试需实际验证0.15.82升级及失败记录。
- 历史真实C课整稿、v0.15.81升级与Release清理证据见`docs/validation-history.md`、`build/classroom-c-audit/`与`build/release-cleanup/20261031-v0.15.81/`，不重复进入当前工作单。
