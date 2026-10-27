# 当前工作单

## 当前交付：0.15.57／219，已验收、安装并发布

最新需求：浏览器配对链接居中，点击沿用公共按压高光，成功明确显示「已复制」。源码0acf342已推送origin/main；固定APK为 `build/candidates/0.15.57/Chrona-0.15.57.apk`，3231902字节、219、非debug、原签名。assembleRelease及lintRelease通过，`build/release-0.15.57-build.txt`。USB于23:10:21正常覆盖，原首次安装2026-09-26 18:54:37保持，读回见`build/release-0.15.57-phone-package.txt`。

链接复用UiStyle.selectableActionText（独立启用），恢复平台可选TextView屏蔽的pressed drawable状态，保留原生选字与原PressFeedback参数；不是页面手势补丁。正常Android36实际触摸、系统剪贴板及明确反馈深浅各12项通过：`build/lan-centered-native-state.txt`、`build/lan-centered-native-dark.txt`，JPEG在`build/ui-0.15.57-review/lan-address-{pressed,copied}-{light,dark}.jpg`。同一ui_review已独立通过，主代理亲看。真机`phone-address-copied-dark.jpg`只证明安装后居中与按压，未捕获短暂提示，不冒充真机提示证据。手机当前QQ使用中，不继续抢前台。LAN保持开启。

## 唯一下一步

已发布 https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.57 ，标签精确指向0acf342，远端资产Chrona-0.15.57.apk为3231902字节、uploaded、非草稿，读回见`build/release-0.15.57-published.json`。当前实现批次闭环，向用户交付配对修复与下述遮挡方案；遮挡实现等待明确授权。不得上传旧216／217／218，不重复付费AI、整套网页检查或极端屏幕测试。当前仅正常屏幕、正常字号竖横屏验收，以用户最新限定为准。

## 同批已闭环的功能及有效证据

- 三页轻量Web：仅首页／日程／收件箱和普通详情编辑，紧凑工具、圆形操作、明确选择模式，保留筛选／选择／输入／滚动。前端真实Edge受控API检查`build/lan-daily-workspace/`、实际Android SQLite/HTTP `build/web-core-android-http.txt`通过。复用原作业同步、精确系统实例身份和单项已读；高级前端移除但原共享API保留。
- 真实手机LAN：`build/lan-live-217/report.json`及JPEG，实际36日程／13筛选作业／51收件，正常竖横屏、选择取消、详情返回与筛选通过；仅正常授权request/poll有POST，其余GET，未更改用户数据。正常关闭浏览器。
- 真实发现并修复日期分组P2：全部类型按执行日期；作业查询才按截止。候选433全类型10/27、作业11/6，`build/lan-live-218/report.json`及两张JPEG，由原ui_review关闭。此后只变配对原生UI，Web证据仍有效。
- 真实微积分1110字幕／275→72候选、31附图第二轮于22:40:53继续，22:45明确「总结已完成」，真实正文已显示。未重下载或重跑第一轮。证据`build/phone-20261027-analysis/217-calculus-result.xml`、`217-calculus-complete.jpg`。旧错误缺少原响应，不能断言当时具体文件名；新轮严格实际附图白名单通过。
- 图片引用和审计：合法images/前缀统一；真正未分析文件继续拒绝；校验前保存第二轮请求、原响应及具体失败，39项见`build/classroom-image-references-audit-final.txt`。源60b7b61后续已集成。
- 课堂批量：历史按courseId筛选、批量下载／完整总ZIP导出／本地删除，复用BatchSelectionUi。离页3节缓存下载16项、同一个真实FGS、意外联网0：`build/classroom-batch-native-background.txt`。系统保存55节总ZIP并自动打开分享，55目录／55字幕／52PPT／3笔记／113条目0失败：`build/ui-0.15.54-review/batch-system-export-report.json`。未发送外部目标，临时QA包和手机QA导出已清，项目证据保留。
- 横屏workspace侧栏120→96dp，72dp底板不变；安全区后外侧12dp、正文间距32dp，正文左移24dp。正常截图`build/ui-0.15.54-review/rail-landscape-normal.jpg`；真实上滑正文视口369→603→904px，回顶恢复。原review通过，未修改wm size/density，AVD旋转恢复1/0。
- 前序详细证据、夹具失败原因与版本沿革见`docs/validation-history.md`，不再作为当前待办重复执行。

## 新讨论：重复截图遇到矩形遮挡（尚未授权实现）

只读研究完成，未改去重业务：ClassroomImages把相邻图缩48×48、平均差<3即保留较晚帧；无裁剪、像素拼接、遮挡评分或组成员索引。小菜单可能替换清晰图，大菜单也可能占候选预算；新增小公式同样可能被平均差淹没。原raw暂存但不在候选内便不能回选，完整课堂七天清理可能删除raw。

建议同页分组并择优保留可见内容，保存成员与时间范围；有新增板书时可保留互补双帧。软件演示可能本身是教学，不能矩形即删除。默认不拼图；真正像素补全需同版对齐及真实未遮挡来源，不用AI猜补公式。先向用户说明现状与方案，等明确实现授权。

## 工具、所有权与保护

- Root负责整体判断、源码整合、ADB／Gradle及发布；web_core_execute已完成，ui_review复用只读审核，classroom_occlusion_research只读交接完成。不要重启已交付代理确认有效证据。
- FastCtx当前未提供，PowerShell7兜底。JAVA_HOME=D:/Minecraft/java21；SDK=F:/Android/Sdk；GRADLE_USER_HOME=F:/Android/GradleCache；ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP及项目输出留本项目build。
- USB 10AG5S1SR4005X7；AVD只复用Chrona_Classroom_36／emulator-5554，串行，不克隆、重置或清数据。手机前台切到其它App时停止盲点，不误操作QQ／LocalSend等。
- GitHub CLI仅F:/Programs/GitHubCLI/bin/gh.exe，保留登录，不读token。先前github.com443失败，本轮恢复可达后正常git push已成功；未改代理或重新鉴权。
- 所有图先压缩，仅查看JPEG派生图，严禁查看／上传PNG原图。
- 用户独立AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1保持未纳入本轮提交。禁止broad add/reset。
- 外部衍生物审计与清理列表已交付：`docs/audits/artifact-location-2026-10-27.md`及同目录CSV105项；用户执行清理，不擅自删移。A1–A7当前不存在。共享工具、SDK、AVD、profile、签名及恢复资料排除。
