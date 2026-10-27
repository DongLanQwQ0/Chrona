# 当前工作单

## 当前交付

**0.15.50 / 212** 已实现、构建，通过本轮模拟器UI独立复审并正常覆盖安装手机，尚未发布GitHub。正式安装包：`build/candidates/0.15.50/Chrona-0.15.50.apk`，3202822字节、非调试、原签名。

- PPT连续快滑、箭头带动图片动画修复；监视阅读页课程名标题、紧凑信息、底部选择栏基线统一。
- 横屏PPT隐藏状态栏及重复标题，角落返回／保存不扣图片高度，保留图库交互。
- 公共胶囊高光修复；横屏主导航自然高度、对称内距，104dp侧栏与20dp正文内距，首页与切页副本实际间隔33dp，不缩小按钮或收紧原高光动效。
- 横屏／短窗口顶部工具随正文上滑退出，正文增高，回顶下拉恢复；该项已写成所有新页强制验收。完整入口审查、受检状态和缺口见`docs/validation-history.md`。

## 验收与证据

原生图片快滑20×2、阅读71×2、胶囊254×2、条件头部25、高度66、周网格9、侧Dock17、横屏图片保存22通过。独立ui_review已亲看压缩JPEG并核对最终周网格补验，本轮受检范围签收。release与lintRelease通过，资产badging／签名回执为`build/release-0.15.50-*`；压缩实图为`build/ui-0.15.50-review/`。

36入口中32个界面已实际呈现；2个NoDisplay、1个debug和无待授权请求的LanPairing未呈现。实际模拟器窗口不代替手机OS分屏、真实学校满课表或全部业务弹窗状态。历史过程及详细回执仅在验证记录，不扩为新的待办。

## 唯一下一步

手机更新已完成：新地址`10.195.39.39:37561`正常连接，`adb install -r`成功，读回212/0.15.50，首次安装时间仍为2026-09-26 18:54:37。安装前后该包均无活动服务，无需启动此前未运行的监视或提醒；未额外导航用户页面。回执`build/release-0.15.50-phone-{before,install,version,services-before,services-after}.txt`。

唯一待交付：GitHub正常`gh auth status`仍token无效，恢复正常认证后推送已验收源码并按CHANGELOG发布同一APK；不绕过凭据或上传旧产物。常规提交、推送及发布已有授权。

源码已本地提交`eb0362c`。正常Git Credential Manager非交互推送也因缺少有效凭据失败，回执`build/release-0.15.50-source-push.txt`。现仅待恢复GitHub认证，未创建远端标签或Release。

GlobalSend文件框及设备路径需求已明确为误发，本聊天不修改其代码。不恢复胶囊无障碍服务或点击计时，不重新生成用户已有总结。

## 工具与保护

FastCtx当前未提供工具，使用PowerShell7。JAVA_HOME=`D:/Minecraft/java21`，SDK=`F:/Android/Sdk`，GRADLE_USER_HOME=`F:/Android/GradleCache`，ANDROID_USER_HOME=`F:/Android/user-home`。

仅复用`F:/Android/Avd/Chrona_Classroom_36`，本轮测试已恢复wm size，保留原density411；不新建、克隆、清空或终止他人工作。视觉只查看压缩JPEG，PNG原件保留但不直接查看／上传。测试产物留build。

保护既有独立修改`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`；不纳入本轮提交。真实数据、通知频道及上传授权不改。
