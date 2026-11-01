# 当前交付：数学课堂失败修复与全21节重新总结

## 当前候选与唯一下一步

- 正式包0.15.91/253已于2026-11-01 20:27:08覆盖V2527A，首次安装仍9月26日；原升级证书、非debuggable。APK：build/candidates/0.15.91/Chrona-0.15.91.apk。最终release/lint通过，不发小补丁Release，正式latest仍v0.15.81。
- 已经原UI按课程时间排序、长按最新10月29日微积分、仅选1/27并确认重新总结，新run实测冻参max_tokens32768、更正复用3/3。3个draft及3个final全stop，20→40张实际附图，全课编排完成；前2/12主题整稿成功，edit-note-2于20:56:31 HTTP502（请求3959069字节、302秒）。新预算截断路径已越过，整课尚未完成，不启动其余20节。
- 确认服务/通知退出后读取phone-candidate91.json。21:00经用户允许后沿2/12恢复，21:06:09同阶段再次HTTP502，队列已退出；不再原样重试，不启动其它20节。只读保存错误为「Upstream service temporarily unavailable」，无更细服务原因；不能断言具体网关配置、上下文上限或模型内部路由。
- 同配置独立短健康请求仅204字节，无课堂数据，1次请求60,001毫秒SocketTimeoutException；未改设置。当前不足以归因于长输入，实际阻塞为AI接口未稳定响应。证据phone-candidate91-502.json、phone-candidate91-health.txt。唯一下一步：待接口恢复可响应后，先沿edit-note-2检查点完成本课，再继续其它20节；不改key/model或裁历史绕开。
- Probe明确诊断模式构建/实测通过：默认mathAudit无网络，providerFailure只扩展保存502限长脱敏摘录；aiPing独立跳过Application初始化且等待确认，不意外恢复其它业务。诊断辅助包、手机本轮window.xml与空任务目录均已正常删除；没有活动AI队列。

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

- 真机10.197.184.141:39343；GlobalSend100已于21:02:10安装并明确交还，本轮手机操作已结束。AVD只Chrona_Classroom_36/emulator-5554，不克隆/重置/改DPI或分辨率。
- /data/local/tmp/chrona/classroom-failure-20261101/window.xml与空任务目录已删除，诊断辅助包已卸载。91签名probe只在任务结束后运行，未中断活动总结。
- 旧/data/local/tmp/chrona/classroom-failure-20261031/清理曾被策略拒绝；隔离包external files/classroom-json-projection-20261101/report.json读取被run-as拒绝，均不绕过。
- 电脑派生文件全部项目build内；图片先压缩JPEG。89/90本轮AVD产物已转回清理，保留既有fixture/用户资料。

## 其它边界

- 监视自动总结全局/课程三态仍在讨论，拟置标题下信息行右侧、后续课程左侧，未确认布局，不擅自实施。
- core接口见docs/Communication/to-external-agent.md；保护AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1及tools/Seed-PreviewData.ps1，不提交或清理他人改动。
