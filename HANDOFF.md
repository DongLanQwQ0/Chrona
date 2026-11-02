# 当前交付：课堂监视恢复与数学21节重整

## 当前候选和唯一下一步

- **当前代码候选97/259已构建，但手机离线仍停96。** `build/candidates/0.15.97/`包含正式包与专用probe；release/lint、协议249、审计145、原生增强定向650/完整3656通过，无UI实现变化，沿用95/96有效视觉。97修复失败response/usage被丢，不代表已解决输出上限；未改32768预算、模型、冻结参数或请求历史。
- ADB于本回合读处理记录后消失，设备表仅emulator-5554、mDNS为空；不重复连接旧地址、不为无人值守ADB找用户。恢复后root先查实际服务，尤其11/2线代15:50后可能自动总结：若有ClassroomWorkService，先等任务结束，不能运行探针或覆盖；再安装97，只续一节失败板块记录真实usage/思考Token/冻结上限，据证据修复。旧7个失败的usage当时未保存，无法补回，不能臆断模型思考耗尽。

- 实机 `10.195.25.59:35621` 已于11/2 14:51:56覆盖为 **0.15.96/258**。候选 `build/candidates/0.15.96/Chrona-0.15.96.apk`；release/lint及实机后台增长已通过，不发小补丁Release。95/96源码已推送 `2aac4e9` / `bf920d3`。
- **17节批次已正常停止：成功0、失败7、取消10，不重复发起。** 原17/27及排除6C/4有效成品证据在`build/monitor96/selection-final.json`，正式批次确实3并发、图片true、复用更正/补齐图/Responses独立新轮。至少10/20及10/15下午真实失败为“Responses输出达到上限”；两次相同终态后从原批量状态正常停止，保留检查点，不让余队列重复失败。`response96-stopped.json`是终态UI，当前4/21有效成品不变。
- 97已补ResponseException.evidence→原turn证据通道：完整native response/usage、实际terminal/delta计数留本地；失败仍回滚，raw output不加到可重放输入。审计增加白名单native状态/原因、思考Token、冻结上限；测试owner仅3个既有Java检查，全部通过。缺口仍是实际新失败的usage，不能以248B健康成功充当长课堂已兼容。
- 当前21身份无重复；4节最新schema1/强度3已完整且来源、单篇、图片检查通过，另3节完整仍旧schema0/强度1，不计本轮完成。完整审计 `build/monitor95/math_audit.json`，21清单沿用 `build/math-completion-audit-20261101/phone-before-batch92.json`。

## 本轮已完成和决定性证据

- PPT切换栏改用公共 `SettingsPageLayout.motionViewport`，复用20dp页面gutter，过渡容器不裁剪；不改变圆角、图片比例、零内距或公共动效。正常原生竖浅reader18项、横深PPT9项通过，独立ui_check复审无未解决P1/P2及关键UI缺口。JPEG在 `build/monitor95/native-*`。96实机按住图 `build/monitor96/phone-pager-held.jpg` 已查看，整个圆端和上边完整。
- Glue新增30秒初始化、30秒心跳和15秒确认期限，发送失败沿原5–60秒退避恢复；保存节流窗口内当前句并异步清理TLS。真实Android合成断流39项通过，不把它冒充现场根因复现。
- **已查清现场提前停字幕原因**：11/2线代1989268仍官方status1，旧监视按raw首段14:10结束，学校Grid整堂13:25–15:50。96复用 `ZjuTimetableView.terms` 的连堂块，只取目标实际日期末段；保留精确起点、NFKC名称、冲突end返回0，按账号/revision缓存。监视和导出共用结果，不复制20分钟/教室/课间冲突规则。
- 96定向Android结束边界58项与已有学校Grid合并检查通过；含跨周/教室/午休/冲突/一次课、首段已过但末段未到仍保留同一Socket，整堂结束保存后关闭。实机只读 `build/monitor96/phone-end96.json` 已读回本节 **15:50**。
- 96仅从Dashboard启动服务、离开到桌面后才首次进入专用页，已由旧106恢复到115条字幕/126张PPT；14:55实际截图126条/128PPT。转去历史选择后监视卡片已180条/137PPT，同一服务实例87fc5c8持续存活（`background-service-before/first-read/later.txt`），不依赖专用页停留。当前上传图片开关经正常UI读回true，未改设置；探针旧optBoolean缺省误报false已修。
- 当前AI服务真实Responses小请求已通过：248B、一次3161ms、21输入/77输出Token；蜂窝默认开、拾时自身VPN=false、默认Wi-Fi及进程绑定不变，无课堂资料。证据 `build/monitor95/response-health95.txt`。未更改模型、密钥或设置。
- 原贺信“11月3日前”已在正常编辑器改为 **11月2日23:59**，保存后重新打开日期器读回一致，原文保留。未来解析通用 `date_exclusive` 已在94实现，95/96包含；不按备注批量改旧记录。

## 数学21节的剩余工作

- 已验四节：`86985-1984869`（10/22）、`86985-1988487`（10/29上午）、`86985-1984868`（10/22）、`86985-1988486`（10/29下午）。均单篇、schema1、强度3、来源通过、精确重复正文0，引用图18/26/9/23全部可读，更正块已复用。
- 其余17需完成；旧失败含HTTP502/413、服务SSEerror、当前块来源缺失、原话依据非精确子串、缺images。旧413估算仅261–273KB且0图，不能臆断图片太大；这些失败发生在旧网络/旧轮次，不放松来源或JSON完整性校验。
- 历史批量重新总结按原行为创建独立新轮并精确归档旧run，只复用已提交且源块匹配的更正文稿，不拼接半稿、不另导出旧成品副本。既有未完成轮次若点“继续”，必须按原冻结Chat协议和历史只追加；新批次用当前Responses，不转换旧前缀。
- 完成后再只读审计21身份、最新格式、完整成品、来源和图片，区分真实服务失败与客户端检查。活动AI期间不得安装或运行instrumentation探针；仅普通UI查看和只读服务/通知观察。

## 设备、工具和收尾

- 最后root持有手机触控，当前无通道；GlobalSend106已安装，其15分钟检查已明确交还，不改其数据/网络，无冲突不跨聊天发消息。手机切至用户正在使用的应用时停触控，现有授权内无人回复可继续；实际权限拒绝不能绕过。临时选择脚本的旧selection-progress不是最终状态；一次被底部Dock挡住目标行后已停止，改为向上滚出Dock并逐项取消剩余三条C，最终17/27及六条C全部未选证据成立，不重跑旧脚本。
- 课堂诊断probe已卸载。手机XML在离开桌面前曾清理，但随后失败记录UI检查又重建了 `/data/local/tmp/chrona/monitor95/window.xml`；断连时该精确路径及空任务目录尚待清理。不得在内部存储根、Download/DCIM/Pictures散放测试文件。
- 公共AVD统一 `F:\Android\Avd\Chrona_Classroom_36` / emulator-5554，串行复用，不克隆/清空/改DPI或分辨率。本轮源fixture早期默认Activity覆盖配置不计正常验收，已改用 `nativeScreen=true`；本轮22张精确任务截图已压缩保存到`build/monitor96/avd-exports/`后从设备删除，保留1张旧同名匹配文件及其它旧材料，清单cleanup.json。
- 所有电脑派生内容留项目build；图片先压缩JPEG再查看，禁止查看PNG原图。公共JDK `D:\Minecraft\java21`，ADB `F:\Android\Sdk\platform-tools\adb.exe`，gh `F:\Programs\GitHubCLI\bin\gh.exe`，保留现有鉴权，不输出token。
- 保护其它owner改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1。只提交本轮所属文件；授权 `git push origin HEAD:main`，不force。
- 业务/动效规范已按当前功能更新，旧过程证据见 `docs/validation-history.md`。监视全局/课程自动总结三态的控件位置仍处于讨论，未以本次后台修复擅自改布局。外部core接口仍见docs/Communication/to-external-agent.md。
