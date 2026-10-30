# 当前交付：0.15.79网页功能修复候选，待真机更新

## 本轮结果

- 保留0.15.78的五视图日历、高光和独立新增按钮，不重新设计样式。修复日历搜索草稿在详情返回后被清空；查询与草稿独立，仅显式提交改变结果。
- 修复周日历缩放把固定40px时间栏一起缩放计算而导致的焦点漂移，保持日期列及分钟焦点；不改样式和运动参数。
- 继续使用首页、日程、收件箱三个主导航，日历是日程子页；不扩展课堂等高级网页管理。

## 固定候选与有效证据

- build/candidates/0.15.79/Chrona-0.15.79.apk：0.15.79／241、3294641字节、非debug、原签名。build/lan-web-functional/candidate.json记录六项打包资产与源码直接逐字节一致；package.txt／signature.txt保留检查。
- build/lan-web-functional/build.txt：release及lintRelease通过，2m10s；calendar-code.txt：生产Java投影／分列40项、日期／坐标／接线102项通过；Node语法通过。
- daily-workspace.txt：生产资源在原Edge的三大主页面功能回归通过，覆盖鼠标／键盘／触摸导航、搜索／筛选、跨页选择、详情返回、草稿、旧请求隔离、作业定位／刷新及考试只读。请求证据另存build/lan-daily-workspace/functional-evidence.json。
- calendar-final.txt：五视图、手机时区、原重复实例进入详情、返回草稿与横向阅读位置、缩放焦点、来源权限拒绝及独立开关、快速切月的迟到请求隔离、导出503恢复通过。两项失败复现见calendar-regression-before.txt／calendar-zoom-before.txt，修复后已通过。

## 验收边界与唯一下一步

本轮是真实Edge＋模拟手机API的功能验证，没有截图、视觉几何验收或UI审查；用户明确自行验收视觉，不重用0.15.77截图来证明新候选。没有调用AI、修改真实日程、访问凭据或触控手机。

ADB设备清单为空，未安装0.15.79；手机最后有效安装证据仍为0.15.78／240。下一步是设备重新在线后协调单一手机所有者，正常流式覆盖这个固定候选，核对版本与真实LAN资源；若系统拒绝立即停止，不自动重试或代确认。真实Android新日历接口／导出仍需补验，浏览器模拟检查不冒充它。未重新配对，也不以新手机数据重跑测试。

常规源码提交推送，不创建小版本Release。项目产物均在本项目build；用户AGENTS.md、角色toml、.backups/、.local-backups/、tools/Clean-Project.ps1不提交、不清理。历史交付与安装证据见docs/validation-history.md。
