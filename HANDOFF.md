# 当前工作单

## 当前交付与候选

当前0.15.48/210：修复设置仅在「监视」视图出现及切视图刷新闪X；按用户明确批准增强AI主动请求图片，并精简时间戳、记录判断和选图依据；修复只给日期的跨日范围被默认时长缩成同日。最终release与lintRelease通过，固定包build/candidates/0.15.48/Chrona-0.15.48.apk为3200266字节、原签名、非调试、30秒、无无障碍服务；尚未覆盖手机或远端发布。上轮0.15.47/209已正常安装实体V2527A，原首次安装时间保留，课堂历史可见，临时classroom.probe已清理。

UI保持既有公共组件与动效：HeaderActionGroup条件设置及分隔连续收宽，退出即停触摸/无障碍，反向接续；WorkspacePageHeader自然包裹48/97dp操作组。Session.busy与实际refreshing分离，缓存/本地读取不显示取消X，真正远端刷新保留取消。

AI第一轮只提供去重截图总数、授权上限和最多12段紧凑时间范围，不逐张堆时间戳，字幕保留既有稀疏锚。image_assessment与逐请求reason必填，请求/无需/无权限/无图/未匹配分别审计；run.json与summary.image_audit保留选中/已分析计数，失败不冒充已分析。40张总上限、用户上传开关、两轮与续跑不变，管线升级9，未调用付费AI。

跨日补钟点保留原结束日期及高存疑；明确结束钟点、全天和不可唯一转换的DST钟点不覆盖。旧日程不自动改写，私有审计证据不提交。

## 验收和唯一下一步

- UI：400dp深浅、320dp/150%深浅各25项真实Activity/触摸/逐帧通过；320旧失败是测试误点同名不可点击弹窗标题，夹具已修。load20、公共库1530、UI结构规范通过。压缩图在build/classroom-header-evidence/previews，同一ui_review独立亲看四档签收。
- AI：build/classroom-image-policy-final.txt，生产管线+受控HTTP221、请求538通过。覆盖密集/稀疏目录压缩、最多12段、40图、依据必填/矛盾拒绝、未匹配、禁止上传、草稿/失败/两轮续跑与原始日志。
- 时间：build/cross-day-time-audit-final.txt，请求538、归一化59、排期418、时间审计43、自动写入17通过。含实际审计日期11/21–22的合成复现、明确钟点/全天/倒置日期/DST回归。

唯一下一步：正常设备连接恢复后安装上述固定APK并读回210；GitHub正常认证恢复后推送并发布同一候选。源代码本批本地提交，不再重建已验收内容。不能把模拟器验收称为真机新包验收；既有课堂总结不自动重跑，新策略对下一轮生成生效，原上传开关不变。

## 设备、发布与边界

- 本轮最后真机连接10.195.39.39:41913超时10060，ADB仅emulator-5554，mDNS为空；按无人值守要求不再找用户。GitHub正常gh认证仍token无效，不绕过、不宣称推送成功。
- 仅复用F:/Android/Avd/Chrona_Classroom_36，不新建/克隆/清数据，不终止其它任务。根持ADB与Gradle串行所有权。
- FastCtx无工具，PowerShell7兜底。JAVA_HOME D:/Minecraft/java21，SDK F:/Android/Sdk，GRADLE_USER_HOME F:/Android/GradleCache，ANDROID_USER_HOME F:/Android/user-home。
- 所有视觉工具只查看/上传更小压缩副本，严禁读PNG原图；报告/原图/预览留项目build，不提交私有数据。
- 保护既有独立修改AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1。主代理实现与整合，ui_review仅只读；工具无agent_type，不编造角色/模型。常规提交推送发布已授权，真实安装拒绝不绕过。
- 旧版及更早功能证据见docs/validation-history.md，不重跑无关整套或重设计。
