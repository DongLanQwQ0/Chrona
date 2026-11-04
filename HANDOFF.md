# 当前交付：压缩课堂总结流程，知识与事务独立

## 当前候选与唯一下一步

- 唯一AVD：Chrona_Classroom_36／emulator-5580，已安装127（289）。固定课堂：微积分（甲）Ⅰ，2026-10-29第1–2节，86985-1988487。
- low新轮会话15212正在运行，build/editorial130/run.txt。使用保存的OpenCode Go／deepseek-v4.1-flash／Responses，临时显式low，不改全局设置。用户明确后续仅low或medium，不再high。
- 唯一下一步：等待15212同一会话终态；之后才把trial辅助包换为只读probe，读取实际阶段、思考强度、缓存与usage并导出全文及配图审核。运行中不覆盖helper、不重启、不并发另一节。
- 当前仅代码／构建验收通过，实际笔记尚未批准。内容无关键缺陷且用户批准后，才继续原21节数学（18微积分＋3线代）；不擅加11月2日新课。原身份清单build/math-completion-audit-20261101/phone-before-batch92.json。此前完成数不等于本轮合格。

## 127行为与有效证据

- 普通新增强轮原字幕<=60000 UTF16：更正文稿→仅图片需求计划→一次完整成稿。删该路径独立提取、编排、分批整稿、事务整合和行动去重请求。鲜课一次更正；合法已提交新旧完整／部分更正缓存按原块复用，只补未完成块。
- 无图或未授权时本地记录可用性原因、origin=local_availability，省去计划API；有图记录模型请求／不请求依据。完整缓存时，有图正常2次API、无图1次。知识notes、事务affairs、行动assignments保持独立，事务汇总页继续使用同一事务集合。
- 超过边界及旧缺／false compact_pipeline快照保留原分段契约。60000是输入字符边界，不保证输出Token完整；真实返回需审查，不因此静默裁历史、改预算或回退协议。
- compact专项1657/0，全量27488/0；build/editorial130/check-owner/compact-fixed.log、full-initial.log。旧检查显式冻结原expanded快照，原覆盖保留。首轮compact-initial.log保留恢复缺陷证据。
- 独立editorial_batch_check复审通过：失效whole缓存吞合法旧缓存、拒图删空坏提交，以及成功有图checkpoint被空inventory重写均已闭环；未新增语义硬拒绝。
- release/lint/trial终版构建成功：build/editorial130/build-final.log、Chrona-v0.15.127.apk、model-trial.apk。只读probe需以终版源码重新构建，不拿旧初版只读包声称包含新增字段。
- 126会话12435已完整成功，实测low、4段更正缓存复用、实际编排／整稿原文逐字及覆盖／去重审计全部true；但成品反转保留作答、擅加再次触发、换元漏平方及对数界分子，未批准。build/editorial129/complete-audit.txt、export/。其设备测试ZIP和空目录已清。126源码752640c已推送；127尚待源码提交。

## 内容与历史不可变要求

- 每轮先在聊天列具体问题、问题原句／原文、预期表达和通用修正，修改与说明一致。单课回归，不作一题一补丁。
- 最终3档为精简完整讲义，保留自学所需条件、关键过程、解释和结论；不按主题／题目数机械扩写，不重复原题与同机制补题。原题确实不明可给标明的同类型补题；不留空壳题干或生产说明。
- 教师事务集中，考试／评分细微线索、对象、动作方向、触发条件不丢，不混进知识；只有真实具体行动进入待办。不增加昂贵语义拒绝门槛。
- 完整历史只追加；失败未提交块整体回滚，不拼失败片段。已提交匹配更正复用，参数变化／重生成新轮归档旧run，不另导旧成品副本。原生Schema及冻结选项不追改，真实usage未知不填0。
- low/high旧对照与124选图覆盖、126投影历史见docs/audits/classroom-notebook-regression-20261103.md、docs/validation-history.md，不再原样重跑123或恢复high。

## 近期已完成与设备边界

- 手机最近验证125（287）；用户批准后已原地更新9条尚未结束C课旧系统副本的地点／课表说明，已结束及其它日程不改；删除精确6条“重复验收”及其空测试日历18。正式日历17仍361条。证据build/ui-calendar-header-20261104/phone-{calendar-details,repair}.txt、repair-verification.json；手机辅助包已卸载。静态副本不改为擅自持续同步。
- 125 HeaderActionGroup分隔修复已提交bd210ee、构建并更新手机。JVM2257及正常AVD深浅竖横屏实际触摸／反向帧通过，ui_review独立签收该范围；不扩称真机视觉。9轮草稿及v33测试包已清，压缩JPEG在build/ui-calendar-header-20261104/device-evidence/。
- 不为本轮回归占用手机。共享AVD不新建、克隆、清空或改显示参数。原411dpi、user_rotation=1／accelerometer_rotation=1保持。
- PTA Cookie现为F:\Programs\Server\Cookies\PTACookies.txt，仅查存在性，不读输出。外部日历订阅已取消，由系统接管。

## 工作与工具

- 只有HANDOFF.md是当前工作单；历史过程与验收进入已有文档，不复制长篇待办。公共UI变化须读完整规范并由ui_review独立检查；本轮只改AI管线，无新UI。
- ADB：F:\Android\Sdk\platform-tools\adb.exe；JDK：D:\Minecraft\java21；gh：F:\Programs\GitHubCLI\bin\gh.exe。FastCtx未暴露时pwsh7、ErrorActionPreference=Stop、非交互。不读token／密钥。
- 图片先压缩再查看，禁止PNG原图。输出只在项目build/；设备临时文件只用项目专用缓存／临时目录，清理仅本次确切文件，不能散放内部存储根目录。
- affairs_stage_checks受限执行仅拥有checks两个文件；editorial_batch_check只读生产审查；不向无冲突的其它聊天例行发消息。工具没有agent_type字段，不虚称角色／模型。
- 常规源码可提交并push origin HEAD:main，不force、不自动发小版本Release。保护他人改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1；仅提交本轮owned文件。
