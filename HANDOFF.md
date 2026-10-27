# 当前工作单

## 最新验收范围（用户2026-10-27明确）

停止低分辨率、高DPI、大字体等极端场景测试；本轮仅正常屏幕、正常字号的竖屏与横屏。先保证常规布局和正文空间，再验关键操作。既有极端证据只存档，不再作为阻塞或继续测试理由。用户再次要求收紧网页纵向边距：标题操作同行、搜索筛选收拢、默认批量工具隐藏，工具间只有一份必要间隔；横屏工具可随上滑退出。不得靠缩字掩盖空间安排。

## 当前交付：Android 0.15.56／218（日期分组局部修复；217已安装）

包含216已完成的课堂批量、图片审计、地址复制；新增三页轻量Web及横屏工作区Dock间距。用户新反馈后合并一次更新，不先推216的旧间距包。216仅为内部固定快照，不发布。

- Web仅首页／日程／收件箱及普通详情编辑，移除高级前端实现；紧凑工具、明确选择模式、圆形状态操作、同Section详情返回和10秒刷新保持输入／选择／滚动。按手机分组、作业定位后单项已读、异步原作业刷新及精确外部活动确认；外部全天使用phone display日期，本地全天UTC。
- 共用横屏workspace侧栏120→96dp，72dp底板不变；安全区后外侧24→12dp，正文间隔44→32dp，正文左移24dp。按钮／竖屏／安全区／动效不变。

- 课堂列表／历史批量下载、导出、删除；按courseId筛选历次课堂，同名用完整ID消歧。复用抽取的`BatchSelectionUi`及原Dashboard批量语言；保留课堂长按监视。全选完整过滤结果跨页，筛选变更清选择、重建保留。
- 用户已确认总ZIP每节独立目录：有总结／草稿导笔记、引用图、sources.md；仅素材导原PPT字节和sources.md。删除只删本地对应资料及同节共享副本，保护活动任务、学校原课、其它课及后续监视。
- 下载复用原Session／prepare与同一FGS hold串行队列，不调用AI；取消／账号变更只停止本批拥有的任务，逐节统计。
- 图片引用：合法images/前缀与裸名使用同一合同，严格拒绝真正未分析图片；第二轮明确稀疏file白名单与附图次序，校验前保存原响应、请求及实际失败。重新进入同轮失败草稿在原status恢复错误。
- LAN设置地址文字点按复制当前普通URL，不带配对码；占位与已停会话不复制，不加按钮或高度。

## 唯一下一步

最终release/lint通过（`build/release-0.15.55-final.txt`），源提交32c3db9，固定`build/candidates/0.15.55/Chrona-0.15.55.apk`3231638字节、217、原签名、非debug、无instrumentation。已于22:21正常USB覆盖215→217，首次安装2026-09-26 18:54:37保持。Web与原生Dock由同一ui_review只读通过。

用户22:29确认另一侧已暂停，USB已恢复。已正常开启LAN，真实Wi-Fi地址10.195.39.39:8765的Edge检查完成：三页、实际数据36日程／13筛选作业／51收件、选择取消／详情返回／筛选／普通竖横屏滚动，全GET（仅正常授权request/poll为POST），`build/lan-live-217/report.json`及JPEG。browser已正常关闭，LAN保持开启给用户使用。

根亲看真实数据发现日期分组P2：全部类型的作业被误按截止11/6分组，后面又10/27。原生Dashboard2197-2205按**查询类别**，已返原owner只改groupingDate两行，query filter==='homework'才用截止，默认全天统一executionAllDay；21项反例通过，源freeze。218 assembleRelease/lintRelease已通过（14s，`build/release-0.15.56-build.txt`），等AI终态再安装，只补同手机这条截图，由原ui_review关闭P2；其它有效证据沿用。

**正在真实AI图片轮，禁止安装／重启**：22:40:53在既有微积分10/27 10:00草稿点继续并正常确认上传，1110字幕／275→72截图、实际31张附图，直接1/2“AI结合图片完成总结”，没重下载／重跑第一轮。ServiceRecord `6a68b3c`，ClassroomWorkService foreground2043；PID由下一次服务快照确认，单次RequestControl1小时，stream idle180s不等于整体失败。按该同轮服务/通知/页面观察，终态后读新具体错误/总结，勿盲重试付费。

主代码已本地提交，正常git push直连443失败（21059ms）；本轮只读诊断github.com:443不可达、api.github.com:443可达，Git未设proxy、Windows已有proxy保留未改。尚未推送／Release；不通过曾被工具拒绝的代理路由绕过，不重置鉴权。按当前218差异补最终构建和图片轮终态，再真实分组复验及发布。

内部216快照：`build/candidates/0.15.54/Chrona-0.15.54.apk`，3232238字节，源码60b7b61，release/lint通过；不是最终217。UiStyle公共多行变体的当前值P2已关闭，极端菜单补证已按用户要求取消。原生夹具三处失败经dispatch定位为抬手早于长按回调、Dock缝命中及持有脱离旧行，已修夹具；Dashboard正常400/font1真实三分支55通过，无生产手势改动。

## 已有有效证据与真实缺口

- 图片引用及第二轮传输／取消／审计39项：`build/classroom-image-references-audit-final.txt`。原未知引用继续拒绝；前缀结果、稀疏编号、ZIP与便携合同通过。真实微积分修复后尚未复验。
- LAN浅400／深320-150%各8项真实文字触摸→系统剪贴板；占位、新地址、停服竞态通过，同一ui_review已亲看两张JPEG并签收分项。证据`build/lan-address-ui-{light,dark}.txt`及`build/ui-0.15.54-review/lan-address-*.jpg`。没有打开网络Socket。
- 批量离线Export／Store51、失败恢复9、重名label5、队列15、FGS hold及header20通过。
- 真实后台缓存下载16项通过：3节在实际Activity.onPause后开始，逐节串行、同一OS前台服务，最终退出，意外网络计数0。`build/classroom-batch-native-background.txt`有效，不重复跑。
- 真实系统保存器与自动分享通过：正常UI在55节合成历史上全选、DocumentsUI保存、自动Chooser、取消分享。拉回ZIP447641字节，55目录／3总结／55字幕／52原PPT／113条目，0失败，无双前缀／路径穿越；返回显示成功55。见`build/ui-0.15.54-review/batch-system-export-report.json`及JPEG/XML。未发外部目标；仅本轮QA ZIP和新建batch隔离包已清理，项目证据副本保留。URI三常量分支最终lint已通过。
- 批量正常浅色15项通过，覆盖过滤、跨页、重建、active删除保护及终态；Dashboard正常三分支55项通过，`build/dashboard-batch-native-light-dispatch.txt`。旧夹具失败已准确定位并修复，极端分支不再追加验收。
- 旧Dashboard夹具失败已据真实事件修复，无生产猜测补丁；当前正常三分支55已通过。历史失败日志只用于追溯，不再作为当前待办。
- 同一ui_review已关闭多行当前值P2并通过正常Dashboard、Web及原生侧Dock分项。极端菜单补证取消。原生正常横屏真实触摸body视口369→603→904px，回顶恢复369；证据`build/ui-0.15.54-review/root-history-landscape-{before,after,collapsed,restored}.xml`，横向新间距`rail-landscape-normal.jpg/xml`实测正文x487→426。旋转恢复1/0，wm size/density从未修改。

## 真机与基线

USB `10AG5S1SR4005X7`：当前217／0.15.55已正常安装。215基线的总结共用全屏、1→2翻图、正文／大图系统拖影及单刷新圆形高光均已真机验证；未删除用户课堂、未投递到外部应用。0.15.53源码`d831a86`已推main；后续09e174d/60b7b61/32c3db9仍只本地，网络443失败。

今天10:00微积分1110字幕、275→72截图，旧历史仍未完成。用户明确授权继续后，实际31张图片轮约4分钟返回“AI引用了未分析的图片”；旧包未保存第二轮响应，无法判定真实文件名／是否只是前缀。回执`build/phone-20261027-analysis/calculus-resume-error.xml`及`calculus-failure-notification.txt`。已有确定的前缀误拒绝反例已修，不把候选机制当本次原始返回事实。新包后可在此次修复／诊断授权内续验一次图片轮，不重下载、不盲目反复付费。运行真实请求时禁止更新／重启应用。

手机曾被另一聊天及GlobalSend浮层干扰，用户已明确暂停另一侧测试后让本聊天继续，主代理独占；遇到前台切走先核对，不误点其它应用。

## Web三页已实现并审核，待真实手机链路

用户定位明确：其它设备快速查看与操作日程，只保留首页、日程、收件箱及其普通详情／编辑叶页；首页层级、筛选、多选、完成／编辑与App基本一致，不扩课堂或课表等高级管理。不能缩成只修截图按钮。

前端`web_core_execute`已完成并freeze，真实Edge＋模拟手机API的`checks/lan_daily_workspace_check.cjs`全通过，JPEG与请求记录在`build/lan-daily-workspace/`，同一ui_review亲看八图通过。414×896首卡y340，常规896×414由y397收紧至y270，上滑195后y75；没有极端DPI/字体矩阵。完成按钮原44×50已修48×48。checkbox原实测20×20/padding0，未按猜测打补丁；改为明确选择模式及圈勾。外部全天上海00:00反例、本地UTC日期、菜单尾勾、收件状态筛选与嵌套选择面板都已覆盖。

root后端真实Android SQLite＋HTTP检查通过：`build/web-core-android-http.txt`，使用`checks/lan-core-validation.init.gradle`与LanAccessInstrumentation的web_core模式，数据为隔离Context/SQLite，未访问学校或AI。覆盖原分页定位／单条已读／禁用刷新不联网／真实导入课的精确负long身份确认与撤销／禁用源拒绝／真实截止／独立ACK revision；不信客户端key/until。缺配置时session给真实设置动作，避免显示旧成功状态。API旧共享高级能力保留，重解析旧文档门槛已删除。

浏览器必须串行复用`build/lan-web-checks/browser-profile`，不新建随机profile、不清登录／锁、不强关别人浏览器。当前browser均正常关闭，profile无占用；共用CDP已移除Browser.close后的强杀。真实手机LAN前端链路待217安装后验，模拟API和设备HTTP不互相冒充。普通AI/附件/重复叶页原有效行为保留，本轮未重跑无关全矩阵。

## 外写入口及清理清单已独立交付

工具日志改项目build/emulator-logs，受控Java/javac runner实际tmp改到项目；21命令参数、7真实JVM落点验证通过。正式`docs/audits/artifact-location-2026-10-27.md`及`artifact-cleanup-candidates-2026-10-27.csv`共105项：F盘16项153968683字节，E盘旧APK89文件221189936字节。18个旧分发工具副本、monitor-review历史证据、polish-input来源疑义、共享工具／AVD／profile／签名／恢复资料排除。A1–A7当前均不存在，不再使用旧132.1MiB计数。

用户要求本阶段不删不搬，由用户运行最终脚本。已核实汇总聊天真实userMessage授权，并向`01a17d32-03fb-7743-b04b-043c81a1d8eb`回报正式路径与限制；已交付，不把发送当作已执行删除。源提交`09e174d`已本地完成，随后正常Git push遇到间歇443失败，尚未推送；不重置已有GitHub认证。任意绕过runner的手动Java调用仍须显式tmp，ModelOutputCheck没有受控runner，边界写在开发指南。

## 所有权／工具保护

主代理：整体判断、Summary/reader/LAN后端、HANDOFF、版本／CHANGELOG、ADB／Gradle、最终验收／发布。classroom_batch_execute与web_core_execute已完成各自源交接，范围内修复回原owner；ui_review只读独立审核，已通过当前Web和侧Dock范围。无需重新调查或重复已有效检查。

共享AVD只用`F:/Android/Avd/Chrona_Classroom_36`、serial emulator-5554，不新建／克隆／清空／重置。FastCtx未提供，pwsh7兜底。JAVA_HOME=D:/Minecraft/java21，SDK=F:/Android/Sdk，GRADLE_USER_HOME=F:/Android/GradleCache，ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP及新增日志在本项目build。

GH唯一`F:/Programs/GitHubCLI/bin/gh.exe`，当前登录正常，不读token、不重新鉴权。正常Git push曾成功也曾443失败；复用系统代理的命令曾被工具策略拒绝，不用其它工具绕过该拒绝。常规提交／推送／发布已授权，正式标签和资产只在候选验收后生成，不能传过时包。

所有图先压缩再查看；仅JPEG派生图，不查看／上传PNG原图。保护用户独立修改AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1，不纳入本轮提交。GlobalSend误发功能不属本项目。
