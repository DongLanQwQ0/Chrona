# 当前工作单

## 当前交付（进行中）

当前主工作：①新浙大作业可选AI自动排程，用户已确认直接应用可修改；官方截止、提醒、手工安排及同一作业身份不改，复用现有全周期时间建议及审计。后端owner homework_ai_execute（独占TaskStore/ZjuHomeworkStore/ZjuSync/ZjuSettingsStore及新planner），主代理负责设置UI、集成与最终验收。专用队列表v18，不重建已有parse_runs。②课堂单节右上按导出／监视／删除组成公共操作组；删除本节本地总结、内容和引用图，保护学校原课、其它课堂和未来监视，活动任务先停止。总结图片复用同一全屏viewer及长按拖放。③公共操作胶囊在只剩刷新按钮时高光圆形。classroom_ui_execute持有该连贯UI／图片源／删除／公共高光实现；不得与TaskStore owner冲突。同一ui_review最后独立验收。

目录审计已交付`docs/audits/artifact-location-2026-10-27.md`。用户已选择删除A1–A7，已提供精确白名单PowerShell脚本供其自行执行，尚无执行回执，不宣称清理完成；主代理不代删。当前工具统一GitHubCLI为F:/Programs/GitHubCLI/bin/gh.exe，旧Android路径已由用户移除。所有新衍生产物留项目build，公共SDK/AVD/运行时/cache外置。

USB 10AG5S1SR4005X7正常覆盖安装215/0.15.53成功，首次安装时间保持。真实既有总结正文打开共用全屏viewer、1→2翻图、正文及大图长按系统拖影通过，主代理和同一ui_review亲看压缩图；单刷新按住图对称圆形。未删除真实课堂、未向其它应用投递资料，新作业开关保持默认关闭。并行手机控制与接收浮层曾干扰验收，用户已确认暂停另一侧测试；首次刷新采集不作为有效证据。

当前候选0.15.53／215，DB18。两位执行owner结果已集成；planner51、队列/备份/原作业回归、深浅原生自动排程各41、总结操作各21、总结拖放各129、单按钮高光各106通过。独立ui_review已消费精确拖放目标、四张压缩JPEG和原七张UI，整体签收本批受影响模拟器UI及交互，真机不在该证据范围。release与lintRelease通过，未改生产后重复构建。

当前微积分诊断：今天10:00课堂历史显示未完成、字幕草稿，1110条字幕、275→72截图，既往具体异常未持久化，不能归因。用户已明确允许继续并观察当前服务结果；正常UI点击继续整理后，已进入既有检查点的31张图片最终轮，ClassroomWorkService/2043实测运行，未重下载。主代理独占手机，禁止安装/重启中断该真实请求。

唯一下一步：观察本次微积分请求的实际结果，保留具体错误或完成证据；有应用缺陷则修复并补验，完成后提交推送发布。0.15.53正式包已固定并验证原签名/非调试/无测试入口；构建和USB验收无需重复。GitHub CLI登录正常，但Git直连443失败，复用系统代理的命令被执行策略拒绝，不绕过，不重新鉴权；尚未推送或发布。

## 上一已验收候选

**0.15.52／214已完成本地候选，尚未安装手机、未发布GitHub。** 固定安装包`build/candidates/0.15.52/Chrona-0.15.52.apk`，3208534字节、不可调试、原签名证书；release与lintRelease通过。

- 图片附件缩略图／大图、课堂大图与内嵌PPT统一长按拖放，保留缩放与跟手翻图。独立图片provider只精确授予所拖URI临时读取权限，不扩大syncblobs；课堂接收时重验账号。复用原图和真实MIME，不另存有损副本。
- 作业／待办执行时段结束即逾期，只有开始时按开始；明确截止更早时按较早者。严格超过才逾期，完成项、活动、记录排除。截止、提醒锚点与默认显示区间保持原值，列表、首页、日历、网页及通知状态一致。修复重复实例向原首发之前改期漏出的问题；重复identity仍使用实际投影时刻，未改精度契约。

## 已验收的候选与证据

- 图片真实触摸与跨UID：400dp浅色、320dp／150%深色各292项通过；标准`checks/classroom-monitor-validation.init.gradle`构建后的test APK runner同样292项通过。原缩放／跨边31项、Tile定向47项通过。权限前、写入、邻接URI、授权释放及账号停用均拒读；原字节与MIME正确，移动／多指、取消及拖后恢复通过。Tile证据限共享组件，不冒充完整Main／Task业务导航。
- 逾期模型／SQLite、4008行分页、重复编辑42项、默认区间和真实LAN消费者通过；真实TaskStore浅色与窄屏大字深色各3711，通知factory83、提醒槽位63及原子卡契约通过。
- 同一`ui_review`已亲看缩小JPEG并签收本轮受检UI，无未解决缺陷。回执与实际范围在`docs/validation-history.md`最新节，图在`build/image-drag-ui/`、`build/task-overdue-ui/`；正式包证据为`build/release-0.15.52-{build,badging,signature,manifest}.txt`。
- 既有0.15.51下课收尾41、自动派发51及完整准备249等证据仍用于未变范围；不能替代微积分的真实课堂诊断。

## 上一候选的手机依赖

此前用户将手机测试延后，现已重新提供USB并明确授权继续。手机最后确认**0.15.51／213**，既有安装回执为`build/release-0.15.51-phone-*`，不要把本地候选当作已安装。

下次用户允许测试时，先读取微积分监视注册、学校课表精确起止、官方课堂状态与自动任务日志，定位为何未结束／未总结，再完成0.15.52升级及图片拖放真机验证。先前36823、43579、41221正常连接及8765均超时，未读到真实日志，原因仍未确定；不将其归因于未经验证的课程处理延迟或时间匹配猜测。

GitHub认证已恢复：本轮通过统一路径`F:/Programs/GitHubCLI/bin/gh.exe auth status --hostname github.com`确认当前账号正常登录、授权可用（退出码0）。旧路径的失效结论已过期，不再作为阻塞，不重新登录或读取token。常规提交、推送、发布已有授权；当前主工作候选验收后直接复用当前登录发布。

## 所有权与保护

主代理持有生产、ADB／Gradle及最终交付；image_drag_route只写本轮测试，ui_review只读签收，均已消费结果。GlobalSend误发需求不属于本项目。不恢复胶囊无障碍服务或点击计时，不重跑用户历史AI总结。

FastCtx未提供工具，PowerShell7兜底。JAVA_HOME=`D:/Minecraft/java21`，SDK=`F:/Android/Sdk`，GRADLE_USER_HOME=`F:/Android/GradleCache`，ANDROID_USER_HOME=`F:/Android/user-home`。只复用`F:/Android/Avd/Chrona_Classroom_36`，本轮仅操作隔离包，无新建／重置AVD；系统旋转已回自动、user_rotation=0，无残留系统drag。

视觉只查看缩小压缩JPEG，PNG原件不直接查看／上传。保留独立修改`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`，不纳入本轮提交。
