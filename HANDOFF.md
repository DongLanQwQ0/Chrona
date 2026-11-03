# 当前交付：单课课堂总结内容回归

## 当前状态与唯一下一步

- 用户要求修复可迁移，一次只总结一节，完整后按“问题原文—预期写法—通用修正”审核并修改，同课复验；用户批准前不换课、不批量。目标是3档精简完整、自学可用的讲义，最终不展示内部流水线。
- 固定课堂：微积分（甲）Ⅰ，2026-10-29第1–2节，86985-1988487。使用唯一Chrona_Classroom_36／emulator-5580，不占手机。
- 用户重新粘贴Go Key后，112的Responses单图及完整生成已成功，先前401已解除。saved配置deepseek-v4.1-flash／Responses／可选x-opencode-session；未读取或输出密钥。4段更正文稿复用，11次实际请求含1次JSON修复，生成6知识、6事务、0行动、18张引用图；流程成功但全文内容不批准。
- 112成品仍有评分指令反转、存疑术语造加分条件、事务重复、行动漏项、标题层级及例题配图错位。问题原文、原字幕与预期详见docs/audits/classroom-notebook-regression-20261103.md；证据build/editorial117/export/、completed-audit.txt、image-review.jpg。
- 当前候选113（275）：通用否定词作用对象／可靠事实规则；核图保留有原话支持行动；主题编排索引附完整正文；区分候选值与存在证明；内部标题降级；整稿仅追加本主题引用图片的已有visible，数组前缀引用规范化，不重传图片或增加AI阶段／硬校验。旧原话、回复和缓存前缀不改。
- 113检查：build/editorial118/batches.txt 2140项、coverage.txt1351项、summary.txt594项0失败。独立check两项及prefix-only定向case已复核通过；build-final.log构建／lint通过，113已安装同一模拟器。113同课runner71081已失败终态：draft-0成功，draft-1及一次repair缺一个对象闭合符；服务均completed/stop，未达输出上限，失败组未提交。证据audit.txt、shape-audit.txt。114只追加通用JSON完整闭合提示，coverage1389、resume220通过，build114.log构建／lint及独立复核通过，114已安装统一模拟器。configured续跑runner51386已失败终态：draft-1-user-retry-1仍completed/stop但正文6053字符少一个对象闭合，3493输出Token；第一组和4段更正稿未重做。加强提示未解决，不继续同路径重试。已询问是否采用服务原生JSON模式，仅JSON阶段生效、更正文稿普通文本；新轮复用4段更正稿，旧轮冻结参数／消息不改。唯一下一步：取得用户决定；采用后先小请求验证服务真实支持，再实现通用阶段输出模式，不按域名／模型分支，不增加内容硬校验。

## 验收与原范围

- 单篇知识主线完整，条件、必要推导、代表例题可直接自学；评分／考试及其他有用事务集中，真实指令和例外不反转，同义事实一次，具体行动保留。原题不明可使用标明的同类补充题，不冒充教师原题。图片必须实际可见、可用且匹配所在内容。
- 每轮完整后审读概览、全部知识、事务、行动、原字幕及引用图；问题在聊天给出原文、预期、修正，规则应跨课程／模型迁移。无内容缺陷且用户批准后才能转原21节数学（18微积分＋3线代），不擅自加入11/2新课。原身份build/math-completion-audit-20261101/phone-before-batch92.json；旧完成数不代表当前内容合格。
- 分段仅内部使用；已发送历史只能追加，失败未提交块完整回滚，不拼失败输出。已成功且源块匹配的更正字幕复用；配置改变及完整成品重生成使用新轮归档，不混服务历史，不另导旧成品副本。未知usage不记0；缓存命中仅据服务实际usage。
- 原21节及手机端旧10/20试点仍未全部内容验收，不宣称完成。108/109/110旧成品不批准；历史详见上述审查和docs/validation-history.md。此前工作单可由Git a549653:HANDOFF.md检索，不把旧停止条件当当前指令。

## 其他已决定／交付

- 用户取消外部日历订阅，交由系统接管；未实施，不纳入待办。
- 112通用服务会话配置已推送：公共HTTP按可选sessionHeader，不按域名或模型猜测；默认Responses、不静默切协议。保存／备份／旋转草稿／密钥保留已审查；协议752、偏好121及课堂594检查通过。UI独立审核通过。未安装手机或发Release。
- 111网页选择栏物理对齐Android、原生来源选择栏全宽及AVD旧验证应用清理已验收；110 PTA同步、来源搜索及校园设置分组已验收。证据build/ui-parity-20261103/、build/lan-dock-repair/、build/pta-research-20261103/，不重新设计。
- 99作业定位／通用思考、96连堂字幕与PPT分页、100监视自动总结、108实际补课保留原验收。C课10/10下午、10/17上午已恢复，未凭空写11/7，不改学校原始数据。

## 运行与边界

- 未收到新手机测试授权不占手机；模拟器串行复用、不新建／克隆／清空／改变显示参数。当前课堂runner76432已succeeded=true终态；安装前确认无活动AI。等待超时不当失败或重启依据。
- 测试输出集中项目build/editorial117和editorial118；设备仅/data/local/tmp/chrona/editorial118或应用缓存。已清设备UI XML目录；112审查导出ZIP已清理；113诊断生成的external cache/chrona-notebook-review108/review.zip及空目录已清理，诊断包已卸载。不得删真实素材／应用数据。
- 图片先压缩再查看，禁止直接查看PNG原图。112图审用已压缩image-review.jpg。
- 代理：editorial_batch_check只读代码复审；pta_ui_check专门UI复审。实际工具无agent_type字段，不虚称角色／模型选择，不跨聊天例行通信。
- ADB F:\Android\Sdk\platform-tools\adb.exe；JDK D:\Minecraft\java21；gh F:\Programs\GitHubCLI\bin\gh.exe。FastCtx未暴露时用pwsh7，保留账号、不输出token。
- 保护其他改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1。只提交本轮owned文件；不为小版本自动发Release。
