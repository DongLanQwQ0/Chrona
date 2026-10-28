# 当前交付：页面动效审计与补齐，0.15.60／222

用户要求审核新页面缺失的进入、退出及切换动画并全部修复。已完成审计与修复：首次迟到数据／Markdown实际就绪入场，模型输出、课表来源与学校学期、来源模式／周、合并预览、校历配置及Web分页过渡；恢复／轮询不重播，快速接管清理旧回调。返回退出保留Android系统路径，不延迟finish。完整路径清单及证据见docs/validation-history.md的0.15.60节，不另存待办。

## 已验收候选

- 固定APK：build/candidates/0.15.60/Chrona-0.15.60.apk，3245674字节，222／0.15.60，非debug、原签名；release＋lintRelease成功。生产源码在最终构建后未变。
- 正常共享AVD原生竖屏浅色／横屏深色各93项通过，真实Edge生产网页＋模拟手机API回归通过；公共方法替身37／1530／74及生命周期检查通过。详见build/page-motion-*及build/release-0.15.60-*。
- 主代理及持续复用ui_review亲看压缩JPEG，受检范围签收。build/page-motion-evidence/的最终portrait-light、landscape-dark证据有效，历史failure帧不是通过证据。未改DPI或分辨率，旋转恢复accelerometer1／user0，动画比例恢复1。
- 0.15.60已提交、推送并正式发布：https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.60 。标签精确指向源码6ea8e5e5b77a5b52813912ef7f9fce3fab2bb1ed，APK资产3245674字节、uploaded、非草稿／非预发布，latest已读回v0.15.60。证据build/release-0.15.60-{push,create,published.json,tag,latest}；不重复上传。手机已完成222升级；唯一下一步为获得稳定前台后的该机页面复验。

## 真机复验

2026-10-28用户提供10.197.86.81:40627，ADB已连接，硬件序号10AG5S1SR4005X7。覆盖固定包成功，安装版本从220／0.15.58升为222／0.15.60；日志build/phone-0.15.60-{install,package}.txt。安装前Chrona无运行服务，JobScheduler无活动任务，LAN此前亦未运行，没有中断中的课堂／AI工作或需恢复的原LAN服务。

升级后已实际启动首页，应用进程29504存活，压缩JPEG及页面层级在build/phone-0.15.60-home.{jpg,xml}。尚未完成导航／课堂正文／分页的真机复验，不能以模拟器证据替代该机操作。

测试首次被QQ切前台打断；用户明确答复「可以，现在继续」后重新打开Chrona，读取首页成功，但进入设置时前台又变为微信ChattingMainUI。前台校验阻止了后续点击，没有操作QQ或微信内容。旧ADB离线阻塞已解除；当前只阻塞需要稳定前台的手机交互验收，不能反复抢占其它应用。用户／其它控制方让出手机后，主代理继续正常界面检查；不再覆盖已成功安装的包，不重建／重发／重复有效离线验收。实际UI工具是build/phone_01560_check.py，所有动作前检查当前焦点，只在Chrona内点击，图片直接压缩为JPEG。

## 工具与保护

FastCtx本轮不可用，PowerShell7兜底。JAVA_HOME=D:/Minecraft/java21，SDK=F:/Android/Sdk，GRADLE_USER_HOME=F:/Android/GradleCache，ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP和全部衍生物留项目build。公共gh仅F:/Programs/GitHubCLI/bin/gh.exe，保留登录。

共享AVD仅F:/Android/Avd/Chrona_Classroom_36／emulator-5554，不克隆／重置／清数据，当前保留运行。真机USB序号10AG5S1SR4005X7；有其他应用抢前台时暂停手机测试。所有图先压缩，仅查看JPEG派生图。

用户AGENTS.md、角色toml、.backups、.local-backups和tools/Clean-Project.ps1不纳入本轮提交。不清公共工具／SDK／AVD／profile／签名。GlobalSend误发需求不属本项目；重复截图遮挡仍仅讨论，未授权实现。
