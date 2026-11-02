# 当前交付：协议选择与数学全21节整理

## 当前候选与唯一下一步

- 用户新增：AI服务加入协议选择，**默认Responses**，可选Chat Completions；按原公共选择控件处理，不另拼样式。候选 **0.15.93/255** 已实现并构建：`build/candidates/0.15.93/Chrona-0.15.93.apk`，正式包名、原升级证书、release/lint通过。未发Release、尚未安装真机。
- 正式UI、协议代码已冻结。默认／缺协议旧配置为Responses；设置纳入十三项草稿、保存／取消／放弃、旋转及保留密钥保存与备份。根负责协议／课堂接线和集成，原设置执行owner、测试执行owner已完成各自代码；ui_check独立复审已通过，无P1/P2及关键UI缺口。
- **唯一下一步：真机重新可达后先查实际AI队列，再决定安全安装93及真实服务验证。** 最后新增审计投影104项通过，93源码准备提交／推送；不能因观察进程退出而重启、取消或假定手机任务已停。当前供应商／GLM模型的Responses支持尚未实测。

## 协议验收与有效证据

- 请求原生映射input_text/input_image、max_output_tokens、reasoning.effort、text.format；store=false、truncation=disabled。Responses只接受completed及response.completed，failed／incomplete／EOF／单独DONE不提交。HTTP、取消及网络lease共用原实现，不跨协议回退或自动付费重试。
- 课堂新轮冻结protocol及options，直接保存完整原生input和原output items（含加密reasoning），只在末尾追加。既有无protocol的轮次继续Chat，不转写旧前缀；重新整理的新轮才使用当前选择。原始响应和usage完整保留，显示使用字段映射，不伪造缓存。
- 有效检查：协议152、增强3511（Responses专项505）、蜂窝64／HTTP676、普通课堂526、备份及偏好121。包含真实loopback多块／附图／整稿、HTTP与incomplete回滚、改设置后冻结续跑、旧Chat不变；这是本地合成证据，不冒充实际供应商能力。
- 同一AVD正常屏幕竖浅190、竖深190通过；外部user_rotation未改变协议截图方向，明确不计横屏。protocolOnly实际Activity横屏29通过，含可达、真实选择／旋转草稿、标题滚出／保存再开。真实压缩JPEG在`build/protocol93/ui-light/`、`ui-landscape/`；独立UI已消费证据并通过。26张设备任务JPEG转回`device-exports/`后清理，原偏好及旋转设置恢复。未改DPI／分辨率、未新建AVD。

## 数学全21节范围不变

- 微积分18+线代3，共21唯一lesson key，清单`build/math-completion-audit-20261101/phone-before-batch92.json`。最新affairs_schema1、强度3、单篇精简完整可自学笔记；事务集中，来源及引用图有效。旧schema0／强度1成品不计本轮完成。
- 复用原6000分块id/text匹配的已提交更正文稿；缺图走原完整下载／校验／去重。messages/options只追加，失败片段回滚，不拼接半稿。
- 已实际成品验收首节 **86985-1988486，微积分10/29第9节**：8知识主题／4事务、单篇、精确重复主题正文0、23引用图全部可读、来源有效、更正3/3复用，真实阅读页可访问。证据`phone-completed91.json`。
- 其余20节在92于11/1 23:34原历史多选启动，观察20/27、排除六节C与上述首节，进行中3；首次选择未排除齐时安全停止，小步反向补齐后才确认。证据`build/classroom-failure-20261101/batch92-*`。
- **队列最终状态未知。** 最后有效观察11/2 00:17：2个新完成通知（10/29上午、10/22下午微积分）、7个失败通知、3节活动，之后ADB丢失；不能把通知或观察超时当成最终成品验收。watch session64341已exit0（观察时限结束，连接错误），不可再等待旧handle；恢复后读实际服务/批次结果，不重启整批。
- 七失败：两次服务SSE非空error（10/27微积分／线代）、10/26线代本块来源缺失、10/19线代来源缺失、10/20及10/15上午微积分原话依据不匹配、10/15下午微积分缺images字段。检查点均保留，不放松来源／完整JSON验证；恢复实机后按实际保存的阶段决定逐项修复或续跑。

## 手机、协调与边界

- 真机最后版本 **0.15.92/254**，11/1 23:10:23正常覆盖，原签名及首次安装时间保持。移动数据默认开启的真实连接曾成功：204字节固定OK、1次4635ms、CELLULAR且拾时自身VPN=false，默认Wi-Fi及进程绑定不变、结束释放。学校下载／LAN／普通日程AI不变。证据`cellular-health92.txt`及CM快照，不保证服务端502消失。
- 旧无线地址`10.196.131.180:36653`已从设备表消失；一次重连拒绝、mDNS无服务，仅剩共享AVD。不扫描旧地址、不反复重连、不改安全设置；按无人值守要求不为ADB断线找用户。没有当前可用的真机serial。
- GlobalSend待交接候选已由104替换为**105**，只安装105；此前Chrona仍有活动队列，已暂不交还，现实机离线的变化也已通知该owner。尚未授予105手机操作权。恢复后先核对队列再明确交接；无真实资源冲突不跨聊天发消息，103旧授权不沿用。
- 诊断辅助包均已卸载，本轮真机`/data/local/tmp/chrona/classroom-failure-20261101/window.xml`及空任务目录已删除。旧20261031任务目录清理／隔离包external report读取曾被策略拒绝，未绕过。派生输出仅项目build，图片先压缩JPEG。
- AVD统一`F:\Android\Avd\Chrona_Classroom_36`，root本轮复用，未克隆／清空，不终止其它任务资源。公共工具与签名保持外部原位置。

## 源码与后续

- 92源码已提交并推送`fe6c5c1`／工作记录`ffb7e2e`；93仅提交所属代码／测试／功能文档／验证记录／HANDOFF，不发补丁Release。
- 保护他人改动：AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1。
- 历史91预算／SSE错误修复、89更正文稿二级页、90取消Dock滚动缩小均保留，证据见`docs/validation-history.md`，不重做无变化验收。监视全局／课程自动总结三态的布局仍在讨论，未确认，不擅自实施。外部core接口见docs/Communication/to-external-agent.md。
