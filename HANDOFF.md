# 当前交付：单课内容回归，随后完成原数学21节

## 当前状态与唯一下一步

- 用户已选择**修复原服务后继续**，不是换服务，也尚未确认服务已恢复。
- 固定课堂：`86985-1988487`，微积分（甲）Ⅰ，2026-10-29 08:00，第1–2节。
- 模拟器原111同轮：更正文稿4块复用，2组初稿已提交，核图0组；final-0及其恢复均在4–5秒返回server_is_overloaded，无正文／usage。没有活动课堂AI服务，observer51259已终态。
- 对照：同配置／协议／课堂路由，274B纯文成功（568+5Token，10.2秒）；1张32×32合成JPEG、1404B请求在2.6秒同样过载，无usage。支持当前图片路径失败，不推断提供商内部根因或永久不支持图片。
- 证据：`build/editorial112/after/audit.json`、`vision-after/audit.json`、`health/text.txt`、`health/image.txt`。探针、专用导出ZIP及缓存空目录、UI XML已清理。
- **唯一下一步：原服务图片通道恢复后先确认可用，再从原检查点继续核图。** 不原样反复付费重试、不擅自换模型／协议／密钥、不关图、不裁历史、不重做更正或已提交初稿。

## 当前候选与已有证据

- 模拟器安装工作候选：`build/editorial112/candidate/Chrona-evidence-recovery.apk`，仍109/271；最新源码已推送main，未发小版本Release。最终release/lint日志`build/editorial112/build-final.log`。
- 减轮：原更正块仍6000 UTF16；新轮提取／核图每2块一组，整稿最多3主题／18000 UTF16。旧轮快照与成功内容保持。110曾15→8请求，但因漏内容未通过，不能以减轮代替内容验收。
- 覆盖：逐段明确边界，原更正文本逐字保留；已有Markdown标题仅作软提醒，忽略跨段／跨组代码围栏，不强制成品目录。行动区分过去叙事与本班仍有效任务提醒。实际HTTP1321项及独立复审通过；派生稿互相重复不作独立证据。
- 原话恢复：仅draft-N／final-N原重试／repair末尾追加错误引文与真实原字幕，保留ASR原字；不改validator、冻结历史或预算，不增加AI阶段。838／728／2543项及stage反例188项通过，独立复审通过；原111同轮两组初稿已真实提交，最终成稿待审。
- 规范：`docs/features/zju/classroom-summary.md`。问题原文／预期／通用修正：`docs/audits/classroom-notebook-regression-20261103.md`。历史及证据：`docs/validation-history.md`；清理前交接全文可用Git读取`6b51ca6:HANDOFF.md`，过期句柄不再列为当前运行。

## 必须完成的验收与原范围

- 一次只整理一节，完整后审读概览、知识、事务、行动及来源／引用图。3档精简完整、可直接自学，保留必要条件、推导、例题和具体评分／考核细节；同义事实一次，非学习内容集中，不用删除问题对象冒充修复。全文通过后交用户确认，再换课验证，不先批量。
- 原题不明可用标明的完整同类补充题；明确课堂题不统一标成补充题，不编造教师安排。同课跨块真实来源只记提示，不增加昂贵硬拒绝；不存在来源、未提供图片、虚假原话仍保留必要边界。
- 108／109／110完整成品均未通过。110漏柯西定理、夹逼求和及多例题；111/112初稿标题已恢复主线，但完整正文、评分方向、考点／行动覆盖仍待验证。
- 原21身份：`build/math-completion-audit-20261101/phone-before-batch92.json`（18微积分＋3线代），不擅自纳入11/2新线代或C课。原技术通过4节也未获内容豁免。基线审查：`docs/audits/classroom-notebook-content-review-20261102.md`、`build/notebook-review101-20261102/`；旧完成数不是实时统计。
- 旧10/20试点86985-1982954及手机端任务终态仍须保留／核对，不与当前并行。旧17批次已停止（0成功、7失败、10取消）；本次单课，原3并发能力保留。旧服务错误不作当前失败原因。
- 原始消息、Responses items／reasoning、冻结选项只能追加，不重写／摘要／裁剪。失败未提交块完整回滚；只复用成功且源块匹配的更正文稿。新轮归档旧run，不拼旧稿、不另导出旧成品副本；未知用量不记0。

## 设备与边界

- 普通回归只用已有Chrona_Classroom_36／emulator-5580，保留账号、素材、显示参数。手机仅留OEM专属验收，不长期占用。模拟器Windows触屏仍未修复／验证；用户已解决密钥粘贴，不等同触屏通过。
- 安装／instrumentation会重启应用，先查真实服务，禁止中断活动AI；观察超时不等于终态。拒绝安装不绕过。测试文件集中项目build及设备专用目录，交还前清理本次文件；图片先压缩，禁止直接看PNG原图。
- 已验收99作业定位／通用思考、96连堂字幕及PPT分页、100监视自动总结、108实际补课保留，不重开设计；证据在验证记录。C课10/10下午、10/17上午已恢复，未凭空写11/7，不修改学校数据。
- 代理复用：editorial_batch_execute受限执行，editorial_batch_check只读代码审查，notebook108_content_check全文审查；主代理最终验收。实际工具无agent_type，不编造角色字段。
- 工具：ADB `F:\Android\Sdk\platform-tools\adb.exe`，JDK `D:\Minecraft\java21`，gh `F:\Programs\GitHubCLI\bin\gh.exe`。FastCtx未暴露时用pwsh7；保留登录，不输出token。
- 保护他人改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1。只提交owned路径。无实际并行冲突不跨聊天例行发消息；core接口在`docs/Communication/to-external-agent.md`。