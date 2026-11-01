# 当前交付：数学课堂失败修复与全21节重新总结

## 当前候选与唯一下一步

- 正式包0.15.91/253已于2026-11-01 20:27:08覆盖V2527A，首次安装仍9月26日；原升级证书、非debuggable。APK：build/candidates/0.15.91/Chrona-0.15.91.apk。最终release/lint通过，不发小补丁Release，正式latest仍v0.15.81。
- 已经原UI在历史按课程时间排序，长按最新10月29日微积分、仅选1/27，确认「重新总结1节课堂」，启动新run验证32768预算与新提示。不能点旧run「继续」冒充预算更新。先观察这节完整通过，再恢复其余20节；运行时不安装、重启或instrument目标应用。
- 被动观察：build/classroom-failure-20261101/watch_work.py，当前session84619；candidate91-single-watch-next.jsonl保存通知。20:45已全部完成分段笔记，进入「AI全课主题编排」，越过原length失败点；尚未整篇发布。观察掉线不等于任务停止，不据此重启请求。

## 全21节的交付条件

- 微积分18+线代3，21唯一lesson key；不能缩成原19节。最新affairs_schema=1、强度3，一篇精简完整的可自学笔记；事务集中、assignments只明确行动，不把内部段落拼成重复长文。
- 原6000分块的id/text匹配复用已提交修正文稿，不改分块；缺图走原完整下载/校验/去重。已发messages/options只追加、不裁历史；失败片段回滚、不拼接。旧轮精确归档同课审计，不另导出旧成品副本。
- 21节均须新格式成品、真实来源/图片有效，抽查正文精简、过程解释与考核线索。四份旧schema0/强度1的complete不计成功；当前最新成功仍0/21。

## 本轮失败与修复

- 用户恢复服务后模型glm-5.3-flash、api-slb.jiji.cc；真实冻参max_tokens=8192，未向未知模型猜测发送low。10/29第9节复用3/3修正文稿、3段draft均stop；19图final-0 length，正文1080、reasoning18099字符、usage输出8192，结果未提交。不是旧403。
- 其它20节准确排除六节C及单课，用户允许后正常停止：成功0/失败6/取消14。六失败：漏image_requests、图片判断/请求不一致、四次stream ended before completion。通知2043及ClassroomWorkService退出，未强杀/清理资料。证据phone-model-budget.json、stop-batch-confirm.json。
- 91课堂新run预算32768，普通日程不变；官方GPT-4o/mini及日期快照保留原8192。沿原token字段、不猜GLM effort；旧run冻参不改。完整字段自检、目录有图与尚未附图明确区分；逐图证据精炼但不省应核图片。
- SSE顶层error不再因无choices被忽略；仅白名单code/type，EOF附正文/思考长度。原DONE/finish规则保持，不接纳部分JSON、不自动重试。四次历史EOF原因仍未知。
- 检查通过：AiRequest586、日程418/时间43/自动应用17；schema725、增强2830、修正复用25、图片引用39。合成/loopback不冒充真实成品。独立只读复审关闭GPT-4o预算P2，无其余必须修项。新预算实际整课仍待完成。

## 已验收内容不重做

- 90删除三页Dock滚动缩小、靠边、仅图标；保留弹簧/高光/惯性/切页/独立＋。竖浅187、横深189、导航183/Chrome39及独立UI复审通过，build/dock-scroll/。
- 89更正文稿独立二级页：标题行最右入口、同Markdown/MathJax、整页滚动/返回/偏移恢复，source/公式/代码不改。原生/独立UI通过，build/corrected-secondary-ui/。本轮无UI改版，不重做视觉验收。
- 历史验证在docs/validation-history.md；先前GROUP_DISABLED是真实旧拒绝，用户恢复后已有成功请求，不再作为当前阻塞。安全诊断证据build/math-completion-audit-20261101/，不输出原始会话/凭据。

## 设备及文件边界

- 真机10.197.184.141:39343；GlobalSend99已于20:36:54安装并明确交还，root持有手机。仅实际冲突时协调。AVD只Chrona_Classroom_36/emulator-5554，不克隆/重置/改DPI或分辨率。
- /data/local/tmp/chrona/classroom-failure-20261101/window.xml本轮UI又已生成，交还前只删此文件与空任务目录。诊断辅助包未安装；91签名probe已构建，仅任务结束后可运行mathAudit，结束卸载。
- 旧/data/local/tmp/chrona/classroom-failure-20261031/清理曾被策略拒绝；隔离包external files/classroom-json-projection-20261101/report.json读取被run-as拒绝，均不绕过。
- 电脑派生文件全部项目build内；图片先压缩JPEG。89/90本轮AVD产物已转回清理，保留既有fixture/用户资料。

## 其它边界

- 监视自动总结全局/课程三态仍在讨论，拟置标题下信息行右侧、后续课程左侧，未确认布局，不擅自实施。
- core接口见docs/Communication/to-external-agent.md；保护AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1及tools/Seed-PreviewData.ps1，不提交或清理他人改动。
