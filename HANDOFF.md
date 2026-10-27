# 当前工作单

## 当前交付

**0.15.51 / 213** 已完成并正常覆盖安装手机，模拟器原生验收、独立UI复审、release和lintRelease均通过。固定安装包`build/candidates/0.15.51/Chrona-0.15.51.apk`，3205170字节、不可调试、原证书；源码提交`9139a6b`。

- 回到最新与右上开始／停止共用公共胶囊，按阅读位置动画显隐，不叠在正文。
- 到真实课程结束关闭实时字幕与增量观察；等待正式处理，完整字幕／PPT补全后共用原下载器自动总结，多次规则保留后续。结束时刻取账号课表同名、精确开始时间且无冲突的真实结束；缺失则保留官方资格检查，不猜时长。
- 普通附件与课堂共用同一查看布局和手势；放大平移跨边接续翻页，100%沿焦点连续缩放而不重置居中；横屏媒体延伸到摄像头区域，操作保留安全区。

## 验收

原生阅读400dp浅色与320dp/150%深色各80、图片焦点与跨边29、真实非零侧切口及邻图像素36、下课收尾41、自动总结派发51通过；正式准备249、客户端95+44、公共设置布局74及服务生命周期通过。ui_review已亲看压缩JPEG，关闭斜向跨边P2与邻图像素证据缺口。受检范围与回执见`docs/validation-history.md`；实图`build/ui-0.15.51-review/`。不替代真机OS分屏或真实学校整堂处理。

## 唯一下一步

手机`10.195.39.39:37561`已正常覆盖212→213，读回0.15.51，首次安装时间仍为2026-09-26 18:54:37。首次被系统拒绝后，用户明确同意重新发起；第二次正常安装成功，回执`build/release-0.15.51-phone-install-retry.txt`与`build/release-0.15.51-phone-version.txt`。未绕过拒绝、卸载或清数据，未继续导航用户页面；安装前后均无活动服务，无需恢复此前未运行的任务。

GitHub正常认证此前token无效，正常GCM非交互推送也缺凭据；常规提交、推送、发布已有授权，认证恢复后按CHANGELOG发布同一候选。未创建远端标签或Release，不把本地构建称作已发布。

## 所有权与保护

classroom_end_execute已交付后端，主代理已消费并完成原生41项；ui_review完成本轮只读独立签收。主代理持有安装／发布与最终交付。GlobalSend误发的需求不属于本项目。不恢复胶囊无障碍服务或点击计时，不重跑用户历史AI总结。

FastCtx未提供工具，PowerShell7兜底。JAVA_HOME=`D:/Minecraft/java21`，SDK=`F:/Android/Sdk`，GRADLE_USER_HOME=`F:/Android/GradleCache`，ANDROID_USER_HOME=`F:/Android/user-home`。只复用公共`F:/Android/Avd/Chrona_Classroom_36`；本轮wm尺寸与tall切口overlay已恢复，原density411保留。

视觉只查看更小压缩JPEG，PNG原件不直接查看／上传，产物留build。保留既有独立修改`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`和`tools/Clean-Project.ps1`，不纳入本轮提交。
