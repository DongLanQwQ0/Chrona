# 当前工作单

## 当前交付：Android 0.15.54／216（未安装、未发布）

- 课堂列表／历史批量下载、导出、删除；按courseId筛选历次课堂，同名用完整ID消歧。复用抽取的`BatchSelectionUi`及原Dashboard批量语言；保留课堂长按监视。全选完整过滤结果跨页，筛选变更清选择、重建保留。
- 用户已确认总ZIP每节独立目录：有总结／草稿导笔记、引用图、sources.md；仅素材导原PPT字节和sources.md。删除只删本地对应资料及同节共享副本，保护活动任务、学校原课、其它课及后续监视。
- 下载复用原Session／prepare与同一FGS hold串行队列，不调用AI；取消／账号变更只停止本批拥有的任务，逐节统计。
- 图片引用：合法images/前缀与裸名使用同一合同，严格拒绝真正未分析图片；第二轮明确稀疏file白名单与附图次序，校验前保存原响应、请求及实际失败。重新进入同轮失败草稿在原status恢复错误。
- LAN设置地址文字点按复制当前普通URL，不带配对码；占位与已停会话不复制，不加按钮或高度。

## 唯一下一步

补原生夹具三处真实触摸诊断后只重验失败分支，由同一ui_review收尾，再USB覆盖和真机图片轮复验。`50561`已结束：短横屏17通过；大字长按在主线程繁忙时UP先于长按回调、Dashboard深色命中Dock缝、浅色持有已脱离旧行引用，原owner据真实dispatch修夹具，尚无生产手势缺陷证据。

生产候选已固定：`build/candidates/0.15.54/Chrona-0.15.54.apk`，3232238字节，216／0.15.54，原签名；最终assembleRelease+lintRelease通过，`build/release-0.15.54-final-build.txt`。UiStyle新multiline变体仅用于课程选择值，默认调用单行不变，ui_review已关闭当前值P2；菜单末项完整露出仍需补滚动。测试源后续变化不影响此APK。不要在原生测试期间另开Gradle或抢ADB。

## 已有有效证据与真实缺口

- 图片引用及第二轮传输／取消／审计39项：`build/classroom-image-references-audit-final.txt`。原未知引用继续拒绝；前缀结果、稀疏编号、ZIP与便携合同通过。真实微积分修复后尚未复验。
- LAN浅400／深320-150%各8项真实文字触摸→系统剪贴板；占位、新地址、停服竞态通过，同一ui_review已亲看两张JPEG并签收分项。证据`build/lan-address-ui-{light,dark}.txt`及`build/ui-0.15.54-review/lan-address-*.jpg`。没有打开网络Socket。
- 批量离线Export／Store51、失败恢复9、重名label5、队列15、FGS hold及header20通过。
- 真实后台缓存下载16项通过：3节在实际Activity.onPause后开始，逐节串行、同一OS前台服务，最终退出，意外网络计数0。`build/classroom-batch-native-background.txt`有效，不重复跑。
- 真实系统保存器与自动分享通过：正常UI在55节合成历史上全选、DocumentsUI保存、自动Chooser、取消分享。拉回ZIP447641字节，55目录／3总结／55字幕／52原PPT／113条目，0失败，无双前缀／路径穿越；返回显示成功55。见`build/ui-0.15.54-review/batch-system-export-report.json`、对应XML及`batch-system-share.jpg`。未发送给外部目标；AVD Downloads有本轮新建`chrona-batch-ui-1793107316.zip`，宿主副本已在项目内。此输入使用旧等价授权mask表达式，三常量分支仍需最终lint。
- 旧批量浅色15项通过，覆盖过滤、跨页、重建、active删除保护及终态；大字失败曾在长按后page为空，不能按测试问题先行定性。`build/classroom-batch-native-dark.txt`失败；原helper用整View中心，新夹具记录实际可见交集、Dock覆盖、真实事件路径及前后Activity／选择，等待50561结果。
- Dashboard旧原生浅色复合几何断言失败、深色第二次点选失败，见`build/dashboard-batch-native-{light,dark}.txt`；生产尚未据猜测改动，新夹具拆分数值并保存事件证据。
- ui_review已确认课程筛选大字硬裁P2；已按公共fieldTrigger multiline变体修复，必须补看完整长ID当前值与菜单。横屏工具可滚出并增加正文空间、Dashboard迁移仍待新证据，不能整体签收。

## 真机与基线

USB `10AG5S1SR4005X7`：当前已正常覆盖到215／0.15.53，首次安装时间2026-09-26 18:54:37保持。该包的总结共用全屏、1→2翻图、正文／大图系统拖影及单刷新圆形高光均已真机验证；未删除用户课堂、未投递到外部应用。0.15.53源码`d831a86`已推main，但未Release。

今天10:00微积分1110字幕、275→72截图，旧历史仍未完成。用户明确授权继续后，实际31张图片轮约4分钟返回“AI引用了未分析的图片”；旧包未保存第二轮响应，无法判定真实文件名／是否只是前缀。回执`build/phone-20261027-analysis/calculus-resume-error.xml`及`calculus-failure-notification.txt`。已有确定的前缀误拒绝反例已修，不把候选机制当本次原始返回事实。新包后可在此次修复／诊断授权内续验一次图片轮，不重下载、不盲目反复付费。运行真实请求时禁止更新／重启应用。

手机曾被另一聊天及GlobalSend浮层干扰，用户已明确暂停另一侧测试后让本聊天继续，主代理独占；遇到前台切走先核对，不误点其它应用。

## 下一独立批：网页版三页收敛

用户定位明确：其它设备快速查看与操作日程，只保留首页、日程、收件箱及其普通详情／编辑叶页；首页层级、筛选、多选、完成／编辑与App基本一致，不扩课堂或课表等高级管理。不能缩成只修截图按钮。

`web_core_research`只读已发现完成按钮缺等高约束、checkbox继承全局input内距、日期分组与排序字段不一致、详情返回清空选择等；正在补原生业务复用入口（刷新作业／未读／外部确认／重解析）。主代理已定：主题跟手机，断开保留页头轻量入口，系统日程开关跟原生；+仅首页；去掉高级UI路由但不擅删现有HTTP API／原生底层。参考压缩JPEG在`build/web-core-refresh-input`。

0.15.54 APK已固定，源提交后允许Web写active assets/API，避免混入当前候选；与真机最终验证并行做下一批。web_core_execute拥有前端及对应浏览器checks/LAN功能文档，root负责后端API与集成，启动只读准备，GO前不写active assets。浏览器必须串行复用`build/lan-web-checks/browser-profile`，不新建随机profile、不清登录／锁、不强关别人浏览器。根已正常关闭本次复现浏览器：完成按钮实测44x50；checkbox实际20x20/padding0，不能按继承内距猜测修复；320宽首记录被工具推到屏外。见`build/web-core-refresh-input/reproduction.json`及JPEG。下拉已真实鼠标打开，不代表连续选择及返回状态已验。

## 外写入口及清理清单已独立交付

工具日志改项目build/emulator-logs，受控Java/javac runner实际tmp改到项目；21命令参数、7真实JVM落点验证通过。正式`docs/audits/artifact-location-2026-10-27.md`及`artifact-cleanup-candidates-2026-10-27.csv`共105项：F盘16项153968683字节，E盘旧APK89文件221189936字节。18个旧分发工具副本、monitor-review历史证据、polish-input来源疑义、共享工具／AVD／profile／签名／恢复资料排除。A1–A7当前均不存在，不再使用旧132.1MiB计数。

用户要求本阶段不删不搬，由用户运行最终脚本。已核实汇总聊天真实userMessage授权，并向`01a17d32-03fb-7743-b04b-043c81a1d8eb`回报正式路径与限制；已交付，不把发送当作已执行删除。源提交`09e174d`已本地完成，随后正常Git push遇到间歇443失败，尚未推送；不重置已有GitHub认证。任意绕过runner的手动Java调用仍须显式tmp，ModelOutputCheck没有受控runner，边界写在开发指南。

## 所有权／工具保护

主代理：整体判断、Summary/reader/LAN、HANDOFF、版本／CHANGELOG、ADB／Gradle、最终验收／发布。classroom_batch_execute：课堂批量及相邻公共UI实现与原生夹具，修复回原owner；ui_review只读独立审核。web_core_research暂只读，不派生。

共享AVD只用`F:/Android/Avd/Chrona_Classroom_36`、serial emulator-5554，不新建／克隆／清空／重置。FastCtx未提供，pwsh7兜底。JAVA_HOME=D:/Minecraft/java21，SDK=F:/Android/Sdk，GRADLE_USER_HOME=F:/Android/GradleCache，ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP及新增日志在本项目build。

GH唯一`F:/Programs/GitHubCLI/bin/gh.exe`，当前登录正常，不读token、不重新鉴权。正常Git push曾成功也曾443失败；复用系统代理的命令曾被工具策略拒绝，不用其它工具绕过该拒绝。常规提交／推送／发布已授权，正式标签和资产只在候选验收后生成，不能传过时包。

所有图先压缩再查看；仅JPEG派生图，不查看／上传PNG原图。保护用户独立修改AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1，不纳入本轮提交。GlobalSend误发功能不属本项目。
