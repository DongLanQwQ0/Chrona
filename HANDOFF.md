# 当前工作单

## 当前交付与唯一下一步

已交付：README、用户指南、原子岛文档及Release说明明确逐机首次需ADB配置、安装APK不会自动完成、日常无需连接电脑。Windows一键配置包已发布，源码提交9b4078dc818ba0f8b1f6da052bb437fef82080af，独立标签／Release为`vivo-setup-0.15.42`；主应用功能、版本及原APK不变，应用latest保持`v0.15.42`。

脚本25场景、双击入口2场景、受限辅助源码16场景及旧配置4项检查通过，最终release AndroidTest构建成功；手机普通身份Status／Enable读回true且changed=false，没有关闭既有场景。ZIP共11个必要文件、4520720字节，原签名辅助APK仅含专用runner与生成支持类，内置ADB可运行，脚本WhatIf正常；公开下载HTTP200，SOURCE.txt关联同一工具提交。证据及范围见docs/validation-history.md。当前无待实施事项；使用者安装PowerShell7、连接授权手机后双击Enable-VivoAtomic.cmd。未在未配置手机上新增注册，不承诺其它固件兼容。

下载：https://github.com/DongLanQwQ0/Chrona/releases/download/vivo-setup-0.15.42/Chrona-vivo-setup-0.15.42.zip

此前拖放调查已按用户要求仅说明结论：手机拖放直达4.0.9.1由服务配置管理目标名单，未找到普通ADB新增接口；相关签名权限不属于shell，模拟更新广播仅重读厂商配置。不继续改拖放入口。

独立已交付项：`tools/Clean-Project.ps1`默认预览、`-Apply`才删除，实际预览61624文件／约5.01 GiB，由用户自行执行；本聊天未执行删除。本地日志与截图经用户清理后不再保证历史路径存在，安装包、依赖、备份及源码保留。

0.15.42／204已正式发布为GitHub latest，DB16、schedule-v17，源码提交823f86d，标签v0.15.42固定同一提交。原生／网页重新解析确认正文统一为「这将替换已有日程」。正式包已核验原签名、正确版本及30／60秒计时；公开APK3134070字节、下载HTTP200。实体手机已通过ADB保留数据覆盖安装成功，按用户要求未继续启动或验证交互。

发布：https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.42

## 已交付行为

- AI时间审计：确定和不确定均返回七字段的依据／规则／短原因；模型原依据与实际归一化结果、字段校验、存疑及调整分别保留。每次请求尝试独立runId，日程历史ID＋稳定身份、run＋plan关联落库；重解析不覆盖旧记录。TaskStore与已认证LAN /api/parse-audit支持分页查询，完整备份包含审计；旧日程不补造依据，删除源收件才级联清理。
- 用户已撤销截止日22:00–23:00专用默认，提示词和执行分支均已删除。单项及较大任务利用整个可用周期；更主动给具体建议和时长，较大工作分阶段并预留修改提交。自由建议保留高存疑；只有实际保存且能唯一应用的明确时间偏好可确定补全。不改用户模型思考强度，不自动重排已保存事项。
- AI服务退出保护：八字段脏表单统一保存／取消／放弃；取消与外部关闭保留输入、放弃不写、校验失败留页，Activity重建保留草稿及原基线。图片重新允许仍是独立即时动作。
- 已确认后只清通知栏解析结果：READY且非空批次全部settled时移除，自动全确认不再发无须处理的通知；mixed、失败／处理中／排队／空结果及未关联草稿保留。不删除原文／日程／审计，不影响其它通知频道，复用后台刷新。
- 重新解析统一CaptureReparser：状态、版本、旧候选读取和替换在同一SQLite事务；先验证再清独占旧系统事件，共享及永久合并引用保留，提交排队后才启动请求。清理失败不解析，部分已删外部事件不回滚；调度失败标FAILED可重试。原生后台执行绑定SaveSession，网页不再因已关联而禁用。公共actionRow在窄屏／大字放不下完整按钮文字时纵排，保留字号与点击语义。

## 决定验收的证据

- build/time-audit-ai-final.txt：请求494、归一化59、排期418、时间审计35、自动写入16通过。合成模型响应，未付费调用，不宣称测得真实模型准确率。
- build/time-audit-persistence-final.txt：生产TaskStore＋真实SQLite32项，含v15→v16保留数据、历史关联、去重、回滚、查询、中断／重试／恢复及快照。关联合并336、双类型102、快照16、审核45继续有效；实际GET分支13项。
- 配置偏好22、备份恢复104、作业取消与生命周期恢复、quick 8/8通过；不重复未变职责整套。具体日志见docs/validation-history.md。
- 设置退出：隔离Android16的400dp／1.0、320dp／1.5深浅四组各63断言，专门UI审核通过；主代理已查看窄屏大字压缩图。
- build/result-notifications-device.txt：实际NotificationManager＋SQLite64项，通知清理及原文／候选／审计保留通过；标题专项6项。通知无样式改动，独立交互补审已通过。
- 时间审计独立审查发现的两项P2（本地字段依据、中断日志状态）已修并补审关闭，包括继承日期存疑不误传仅钟点存疑。
- 重新解析：build/reparse-device.txt真实SQLite／CalendarProvider／MediaStore84断言；build/reparse-ui-library-check.txt公共方法1530断言。最终短文案四组原生各23断言及网页四组通过，取消／确认／失败刷新有效。生产清理器＋SQLite211、LAN安全83及job／lifecycle通过。专门UI审核已亲看最新原生／网页八图，业务、公共行与短文案均通过。
- build/reparse-final-build.txt：最终assembleDebug／assembleRelease／lintDebug／lintRelease成功。build/release-0.15.42-artifact-check.txt：正确包名204／0.15.42、不可调试、原证书、正式30／60常量；资产build/candidates/0.15.42/Chrona-0.15.42.apk为3134070字节。

## 边界与保留状态

- 功能验收在隔离v33包、合成记录中进行，无真实账号／付费模型请求；实体手机随后仅完成正式0.15.42保留数据覆盖安装。旧Android、真实边缘手势及进程死亡恢复未设备实测，Activity重建和中断持久化分别已有有效证据。
- 30／60秒正式胶囊窗口不变；当前OriginOS约200ms系统收起过渡按用户接受保留，不修改宿主或全局动画。无待清理临时活动，用户关闭的后台更新通知频道保留。
- 本地AGENTS.md、角色toml、.backups及.local-backups不纳入业务提交。FastCtx未提供时使用PowerShell7；Java D:/Minecraft/java21、SDK F:/Android/Sdk、Gradle缓存F:/Android/GradleCache、Android用户目录F:/Android/user-home。
