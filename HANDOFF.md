# 当前工作单

## 当前交付

0.15.49/211已正常覆盖安装实体V2527A，连接10.195.39.39:36009，读回版本和原首次安装时间，AgendaReminderLiveService已恢复前台运行。固定包build/candidates/0.15.49/Chrona-0.15.49.apk为3200290字节、原签名、非调试。用户要求PPT目录最多40段已落实，密集截图仍合并范围，字幕稀疏时间锚、40张上传上限与用户授权开关不变，管线版本10，不重跑已有总结。

本版包含前批全部修复：设置仅「监视」视图出现，切页/本地读取不闪取消X；主动判断视觉缺口，记录请求/不请求/无权限/无图/无匹配的依据及实际选图数量；跨日补钟点保留结束日期并待确认，明确钟点、全天、DST语义不变，不自动改用户历史数据。

## 有效证据

- build/classroom-40-range-final.txt：生产管线249、请求538通过；稀疏200图恰好40范围、密集1000图仅一范围、原两轮/40图/审计/失败续跑不变，无付费AI调用。
- build/release-0.15.49-build.txt：release/lintRelease通过；badging/signature回执同前缀，安装/版本/前台服务回执为build/release-0.15.49-phone-*。
- 前批UI四档各25、load20、公共1530已通过且ui_review独立亲看压缩图签收；跨日请求538/转换59/排期418/时间审计43/自动17通过。完整范围见docs/validation-history.md，不重复无关验收。

## 唯一下一步

用户本轮40段及手机更新已完成；仅GitHub正常认证恢复后，推送本地业务提交并发布与固定APK一致的版本。最近gh auth status仍报告token无效，未推送/未远端发布，不绕过凭据，不反复构建或刷手机。常规提交、推送、发布已授权。UI和真实模型语义效果不扩大宣称：本轮未新做真机整堂监视、OS分屏或收费模型准确率评估。

## 工具与保护

FastCtx当前无工具，PowerShell7兜底。JAVA_HOME D:/Minecraft/java21，SDK F:/Android/Sdk，GRADLE_USER_HOME F:/Android/GradleCache，ANDROID_USER_HOME F:/Android/user-home。仅复用公共AVD F:/Android/Avd/Chrona_Classroom_36，根持Gradle/ADB串行所有权，不新建/克隆/清数据、不停其它任务。

视觉查看/上传只用更小压缩副本，严禁读取PNG原图；测试/图片/私有审计留build，不提交。保护既有独立修改AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1。用户真实数据、通知频道、上传授权保留；不恢复无障碍服务，不绕过安装拒绝。classroom.probe临时辅助包已清理。
