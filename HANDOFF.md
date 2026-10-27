# 当前工作单

## 当前交付

**0.15.52／214已完成本地候选，尚未安装手机、未发布GitHub。** 固定安装包`build/candidates/0.15.52/Chrona-0.15.52.apk`，3208534字节、不可调试、原签名证书；release与lintRelease通过。

- 图片附件缩略图／大图、课堂大图与内嵌PPT统一长按拖放，保留缩放与跟手翻图。独立图片provider只精确授予所拖URI临时读取权限，不扩大syncblobs；课堂接收时重验账号。复用原图和真实MIME，不另存有损副本。
- 作业／待办执行时段结束即逾期，只有开始时按开始；明确截止更早时按较早者。严格超过才逾期，完成项、活动、记录排除。截止、提醒锚点与默认显示区间保持原值，列表、首页、日历、网页及通知状态一致。修复重复实例向原首发之前改期漏出的问题；重复identity仍使用实际投影时刻，未改精度契约。

## 已验收的候选与证据

- 图片真实触摸与跨UID：400dp浅色、320dp／150%深色各292项通过；标准`checks/classroom-monitor-validation.init.gradle`构建后的test APK runner同样292项通过。原缩放／跨边31项、Tile定向47项通过。权限前、写入、邻接URI、授权释放及账号停用均拒读；原字节与MIME正确，移动／多指、取消及拖后恢复通过。Tile证据限共享组件，不冒充完整Main／Task业务导航。
- 逾期模型／SQLite、4008行分页、重复编辑42项、默认区间和真实LAN消费者通过；真实TaskStore浅色与窄屏大字深色各3711，通知factory83、提醒槽位63及原子卡契约通过。
- 同一`ui_review`已亲看缩小JPEG并签收本轮受检UI，无未解决缺陷。回执与实际范围在`docs/validation-history.md`最新节，图在`build/image-drag-ui/`、`build/task-overdue-ui/`；正式包证据为`build/release-0.15.52-{build,badging,signature,manifest}.txt`。
- 既有0.15.51下课收尾41、自动派发51及完整准备249等证据仍用于未变范围；不能替代微积分的真实课堂诊断。

## 唯一下一步：下次手机测试

用户明确「下次测试手机」，本轮后续不再连接、安装或操作真机。手机最后确认**0.15.51／213**，既有安装回执为`build/release-0.15.51-phone-*`，不要把本地候选当作已安装。

下次用户允许测试时，先读取微积分监视注册、学校课表精确起止、官方课堂状态与自动任务日志，定位为何未结束／未总结，再完成0.15.52升级及图片拖放真机验证。先前36823、43579、41221正常连接及8765均超时，未读到真实日志，原因仍未确定；不将其归因于未经验证的课程处理延迟或时间匹配猜测。

GitHub正常认证此前token无效，GCM非交互推送也缺凭据。常规提交、推送、发布已有授权，认证恢复后发布同一候选；未创建远端标签或Release，不绕过认证。

## 所有权与保护

主代理持有生产、ADB／Gradle及最终交付；image_drag_route只写本轮测试，ui_review只读签收，均已消费结果。GlobalSend误发需求不属于本项目。不恢复胶囊无障碍服务或点击计时，不重跑用户历史AI总结。

FastCtx未提供工具，PowerShell7兜底。JAVA_HOME=`D:/Minecraft/java21`，SDK=`F:/Android/Sdk`，GRADLE_USER_HOME=`F:/Android/GradleCache`，ANDROID_USER_HOME=`F:/Android/user-home`。只复用`F:/Android/Avd/Chrona_Classroom_36`，本轮仅操作隔离包，无新建／重置AVD；系统旋转已回自动、user_rotation=0，无残留系统drag。

视觉只查看缩小压缩JPEG，PNG原件不直接查看／上传。保留独立修改`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`，不纳入本轮提交。
