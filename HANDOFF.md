# 当前交付：日历截图修复与单课总结回归

## 当前状态与唯一下一步

- 当前安装候选123（285），唯一AVD Chrona_Classroom_36／emulator-5580，不占手机。low新轮70863曾在edit-plan空正文失败，output760/上限32768，非截断；沿原检查点19926已完整成功。4/4更正稿复用、40附图标签0失配，855原字幕最终逐字一致，最后input204003/cache184960，含一次failed和一次restart。证据build/editorial127/complete-audit.txt、export/、两张压缩image-review已读，设备review.zip及空目录已清。
- 固定课程：微积分（甲）Ⅰ，2026-10-29第1–2节，86985-1988487。同123 high首轮66850在final-0将PPT35任务误引为字幕，既有诊断续跑67962已完整成功；4修正段复用，855原字一致，最终input206525/cache187520，model/effort全局不变。全文8知识/4事务/4行动/23图已读，三张压缩图集已查，独立high_note_review不批准：仍否定合法相除、反转保留答案，新增把求导后的倒数商误说代数变形错，评分细节漏失。不能视作high已改善或开启21节批量；不再原样生成。完整证据build/editorial127/high/。下一轮通用修正须先对照原句/预期，当前先收尾用户新截图。
- 123 low全文9知识／10事务／2行动／18图已读，内容不批准：仍否定合法约分、反转保留答案指令、辛苦分触发条件改写、模糊词扩成具体加分条件、无用人数说明。原更正文稿确有反向指令，但最终事务已供全部原字幕，不能归咎漏传。image113可见2+2/3板书，与分步评分举例相关，不把它误报为无关图片。最近122证据保留build/editorial126/。
- 模拟器保存服务为OpenCode Go／deepseek-v4.1-flash／Responses／low，可选x-opencode-session；密钥不读不输出。原模型、协议及全局设置未变。
- 用户已批准临时high同课对照，已按上述66850启动。仅本次AiSettings、不保存全局配置；终态记录全局model/effort不变、实际effort、usage和内容差异。使用已保存匹配辅助包build/editorial127/model-trial.apk；不得先安装124再声称同候选对照。
- 123政策仅system一次，四类Schema不重复，policy1002/全量24904/构建/独立复审通过。冻结与当前自定义提示长度均0，排除覆盖。危险skip事务优化已撤回，不能丢原事务中误分类的知识。
- 124（286）独立图片窗口覆盖修复已构建未安装：已有基线覆盖则原样保留；预算足够时先最少区间锚点再补原质量采样，预算不可行保留原基线；40上限、来源编号、原图对象不变。实际122全部15窗已覆盖，不归因本课缺式。独立代码check通过，600组/2511预算穷举对照、专项23994/完整24566/Responses535/grounded317项0失败；build/editorial128/。测试diff保留原完整用例，仅新增覆盖检查。完成文档和源码提交，不安装到运行中的AVD。

## 验收与原范围

- 新截图：USB 10AG5S1SR4005X7已核查，旧系统课表副本17与学校同选课号/起止、旧东1A-301 vs现东2-303；用户明确批准后原地更新未结束的9条副本位置/课表说明，已结束不改、其余日程不动。事务中断言原账号/事件身份并删除测试日历18内6条精确“重复验收”记录和空测试日历；正式日历361条及其它日历/未目标C课字段前后相同。证据build/ui-calendar-header-20261104/phone-{calendar-details,repair}.txt、repair-verification.json；专用辅助包已卸载。没有给生产加按包名隐藏，手动写出仍是静态副本，不改成擅自持续同步。
- 图三125（287）已验：HeaderActionGroup统一按相邻实际可见进度计算分隔，先隐藏再添加首项及中项均无残留，48dp/24dp/1.10/原圆角动效不变。JVM2257项；隔离v33真实TaskDetail深浅竖横屏、DOWN/UP菜单、反向中间帧通过，全部9轮（含失败诊断轮）草稿已删，压缩JPEG在device-evidence/。横屏旧回执无效，最终landscape-{light,dark}-final.txt实际orientation=2/rotation=1。主代理和ui_review亲看最终深浅横屏、竖屏，复审签收该范围；不扩称真机视觉验收。根因为测试在Launcher先冻结方向、开页又恢复竖屏；复用既有测试的先开真实页再旋转一次路径，未改生产方向/布局或放宽断言。
- 125 release/lint、包名/版本/签名验证通过，build/ui-calendar-header-20261104/Chrona-v0.15.125.apk；USB手机已从105升级到125，phone-version.txt实读287/0.15.125。未创建Release，未抢占手机聊天前台做视觉检查。正式AVD保留123及其真实课堂/账号。当前任务的v33测试包和辅助包已卸载，私有JPEG随包清除；手机日历修复辅助包已卸载。AVD 411dpi未改，原user_rotation=1/accelerometer_rotation=1已恢复。
- 当前唯一下一步回到单课内容回归：low/high均未批准，先整理高思考成品的具体原句与预期，并以可泛化的事实来源/数学操作修正方案推进；不原样盲重跑，不先批量21节。截图修复已完成，等待用户实际手机反馈不阻塞代码工作。

- 用户要求每轮先在聊天列具体问题、原文／预期及通用修正，实际修改一致；一次只整理一节。最终3档精简讲义，可直接自学，保留必要条件、过程、解释和结论；非学习事务集中，评分／考查细节、真实方向与适用条件不丢，不混入知识；明确行动独立。
- 同一例题可覆盖多个机制，不按题数／标题数机械扩写。原题确实不明时可用标明的同类补题；原题确认后不再并列同机制替代题。无题干叙述和生产过程不作笔记内容。
- 完整历史只追加；失败未提交块整体回滚，不拼失败输出。成功且原块匹配的更正稿复用，配置改变／成品重生开新轮正常归档，不另导旧成品副本。真实usage缺失为未知，不记0或伪称缓存。
- 内容无关键缺陷且用户批准后，才继续原21节数学（18微积分＋3线代）。不擅加11/2新课。原身份build/math-completion-audit-20261101/phone-before-batch92.json；此前完成数不等于本轮内容合格。
- 原21节与手机旧试点尚未全部内容验收，目标保持未完成。历史过程见docs/validation-history.md及回归审查，不把旧失败或旧等待当当前状态。

## 已完成机制与边界

- 117原生阶段JSON Schema只约束既有结构，首次保存后恢复／repair复用；旧text/json_object轮不追改，更正仍文本。
- 119取消整稿数量上限误拒绝，知识/事务分类拆分完整保留；来源、图片及行动分离检查不减。真实原edit-note-0完整回复本地重验，不重付。
- 120新增强轮unified_affairs快照，唯一edit-affairs结果editor_affairs替换各分支派生事务；过滤图片后提交前检查原结构，失败不留坏检查点。121改最终事务直接扫描全课原文，无候选也扫描、可返回[]，去派生标题。
- 121全量ClassroomEnhancedCheck23902断言0失败；release/lint/trial及独立代码check通过。证据build/editorial125/check-owner/full-fixed.log和build.log。合成usage不当真实缓存证据。没有新增内容语义硬拒绝。
- PTA Cookie新路径F:\Programs\Server\Cookies\PTACookies.txt，仅查元数据确认存在，未读内容；项目未硬编码旧电脑路径，不复制Cookie或重复登录。
- 外部日历订阅已取消，由系统接管。110 PTA/来源搜索/校园设置、111选择栏物理对齐与来源全宽、112会话配置等已验收，不重新设计。其他既有交付见功能文档及验证记录。

## 工作约束

- 不自动发小版本Release；源码普通提交并push origin HEAD:main，不force。123/124包与历史证据保留；当前125包在build/ui-calendar-header-20261104/，已安装USB手机。trial和只读probe分别保存，不拿被后续构建覆盖的默认AndroidTest包代替。
- 复用单一AVD，串行使用，不新建/复制/清空/改显示参数。120安装前模拟器已退出且无进程，已正常重开原AVD，账号及数据保留。观察超时不当失败或重启依据。
- 测试输出仅项目build/内；设备仅专用临时目录或应用缓存，不向共享存储根目录散放。图片先压缩再查看，禁止读取PNG原图。清理只删本次创建的确切路径。
- ADB F:\Android\Sdk\platform-tools\adb.exe；JDK D:\Minecraft\java21；gh F:\Programs\GitHubCLI\bin\gh.exe，保留登录，不读token。FastCtx当前未暴露时用pwsh7。
- 本聊天editorial_batch_check只读代码审查，pta_ui_check独立UI审查；affairs_stage_checks受限execute只改测试已完成，regression125_content只读内容审查。实际工具无agent_type字段，不虚称角色／模型，不向无冲突的其他聊天例行发消息。
- 保护其他所有者改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1；仅提交当前owned文件。
