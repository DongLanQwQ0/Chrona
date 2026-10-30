# 当前交付：0.15.78已更新手机，视觉由用户验收

## 本轮已实现

- 网页筛选高光在挂载、复用、尺寸变化后按当前选中节点重新定位；Dock高光沿用原运动参数，改为真实宽高／顶部位置放大，保持圆端，不再scaleY拉伸。
- 「＋」移出导航宿主独立固定，导航伸缩不再挤压按钮；原位渐隐、缩小与模糊退出，隐藏即停止交互。
- 日程页右上日历图标进入年／月／周／日／日程五视图。日期条选择／前后／滑动、今天、搜索、年进入月、月选日明细、周／日全天及定时区、原生冲突分列／凌晨空段压缩／缩放、原详情与重复实例、周PNG导出均已接线。数据与分列直接调用手机仓库和CalendarTimelineLayout，导出复用WeekAgendaImage；来源开关与手机日历共用且不改首页开关，不调用AI。

## 代码与构建证据

- build/lan-calendar-code/final-checks.txt：40项Java真实投影／分列、102项纯JS日期／坐标／活节点高光／接线通过。dates.txt为21项既有日期规则。Node语法、release及lintRelease通过final-build.txt。
- 固定build/candidates/0.15.78/Chrona-0.15.78.apk，3294581字节，240／0.15.78、非debug、原签名。candidate.json记录六项资产与源码直接逐字节一致；candidate-package.txt／signature.txt提供元信息。最后仅补入日历子页面方向及导出按钮初始隐藏态，资产打包见final-assets-build.txt。
- 首次安装被明确拒绝后已停止；用户允许重试后，经已连接无线ADB核对原手机10AG5S1SR4005X7并正常覆盖成功。最终23:37:24更新，原首次安装仍9月26日；phone-retry-install.txt／phone-final-install.txt／phone-package.txt。

## 验收边界与唯一下一步

用户明确自行做视觉验收。本轮没有运行浏览器、截图、触控页面或委派UI审查；不宣称动画、像素与真实浏览器五视图已验收，也不挪用0.15.77截图。可选旧浏览器用例已迁移资源与新日历接口，但未运行。

更新后原LAN地址暂不可达（phone-static-assets.json记录URLError）。唯一下一步：用户在手机重新开启局域网访问，浏览器刷新并按正常流程授权，然后检查两处高光、独立「＋」退出与五种日历视图。没有代配对或自动继续视觉检查。手机已归还GlobalSend，源码常规提交推送，不创建小版本Release。

全部产物在本项目build；安装流式传输，无手机公共目录暂存。用户AGENTS.md、角色toml、.backups/、.local-backups/、tools/Clean-Project.ps1不提交、不清理。历史证据见docs/validation-history.md。
