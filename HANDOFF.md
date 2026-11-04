# 当前交付：单课讲义与课程事务回归

## 状态与验收

- 固定课堂：微积分（甲）Ⅰ，2026-10-29第1–2节，`86985-1988487`。第三档为精简自学讲义，教师提供知识方向，AI自主组织可靠过程及代表例题；知识、事务、真实行动分区。
- 单课完整成品需经用户认可，再继续原21节数学（18微积分＋3线代）；尚未启动批量，不扩入新课。原身份清单：`build/math-completion-audit-20261101/phone-before-batch92.json`。
- 134已通过来源边界24589项、本地完整回执重验630项及release/lint/trial构建，并装入原AVD（296／0.15.134）。133完整回复沿原检查点成功恢复，未新增AI调用；4更正段复用，原2次请求、22附图、18成品引用可读。
- Terra已读完整日志、成品、来源及5张压缩拼图。纯图新增事务由image-6实际作业页支持；知识主线可跟算，往届误列、行动题设收窄和图注过度本轮未重现。成品仍未批准。

## 当前具体缺口与唯一下一步

- 事务仍漏：s639“一分辛苦分”后找分即失去的条件；s657–658先猜A=1再按定义证明的给分场景；s586–588满分6分不累计成8分的例子；s126使用对数不等式前须证明的要求。
- 下一步先判别这些原句是否被现有来源ID导航覆盖，再决定通用事务提取修正；不在未知原因下继续堆提示，不加本课原句特判、昂贵语义拒绝或审校轮。Terra正在只读核对导航覆盖，主代理负责方案及修改。
- 证据：`build/editorial137/{run,complete-audit}.txt`、`export/`、`image-review-{1..5}.jpg`；134代码与测试固定，未启动下一轮AI。来源与回执恢复验收见`docs/validation-history.md`。

## 历史与恢复契约

- 原常规增强流程：更正稿后两次实质请求（事务／取图、全课知识），low或medium；不使用high。全局服务、模型、协议、参数不擅改。匹配的4段更正稿继续复用。
- 已发送历史、原生回复、实际附图与冻结参数原样保留，只追加；失败未提交块整体回滚。完整已收到回复可本地重验，不为校验修正主动重生成。
- 图片按时间窗实际count选取，不填满40授权；文字事务先生成并本地原字保留，仅去掉严格内容相同且来源已覆盖的回显副本。知识／事务允许实际图片独立来源，行动仍需真实字幕和教师原话。
- 127–133过程与证据快照：`build/editorial137/handoff-through133.md`；较早逐项内容审查见`docs/audits/classroom-notebook-regression-20261103.md`。当前工作单不再复制历史状态。

## 已完成项与设备边界

- 全局新AGENTS已继承；项目AGENTS已按用户要求删除后重写并提交推送（6db2806），只补本项目契约。
- 128账号／PTA界面修复已在原AVD实际渲染并经UI独立审核；129刷新／启动恢复低开销审计已实现。偶发卡顿根因尚无实际异常证据，不声称已修复卡顿。
- 手机旧课程副本修正及精确测试日历清理已完成，证据在`build/ui-calendar-header-20261104/`；本轮不占手机。PTA Cookie现为`F:\Programs\Server\Cookies\PTACookies.txt`，不读取输出凭据；外部日历订阅已取消。
- 共享AVD仅`Chrona_Classroom_36`／`emulator-5580`，不克隆、清空、改显示参数。134前离线已核实，现已恢复原AVD；readonly helper在位，无活跃AI。设备临时ZIP与空目录已清。
- 全量长输出由`terra_output_review`只读审查，主代理读结论及必要原句。工具无agent_type字段，不编造角色配置；FastCtx未暴露时使用pwsh7。
- ADB：`F:\Android\Sdk\platform-tools\adb.exe`；JDK：`D:\Minecraft\java21`。项目产物仅仓库build，图先压缩，禁止PNG原图。常规源码可提交并`push origin HEAD:main`，不为本轮测试发Release。
- 保护其它所有者改动：`docs/Communication/messages/2026-10-31.jsonl`、`.backups/`、`.local-backups/`、`tools/Clean-Project.ps1`、`tools/Seed-PreviewData.ps1`；角色配置的外部删除不恢复。
