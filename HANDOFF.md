# 当前工作单

## 当前交付：日程教务考试类型，0.15.59／221

用户明确「考试只有教务系统」。日程类型增加全部、活动、待办、作业、考试；仅教务考试缓存投影到全部与考试列表，手动／AI类别保持三类，不按标题分类。不复制为本地候选、不写系统日历，不参与编辑、确认、完成、批量写入或删除。原生和Web共用ScheduleListing，日期／搜索／状态、全局排序和24项分页一致，本地全选ID及新作业定位保持准确。

卡片显示官方时间／星期、考场及座位，点击复用ExternalCourseDetailActivity只读详情。未知时间保留原文并进入待安排，已结束进入历史；考试不满足待办范围，日历状态仅匹配全部／未写入。日程不额外联网，尚无缓存时提供考试安排入口；缓存刷新使原生快照失效，停用／退出／换账号隐藏旧数据，损坏缓存不阻塞本地日程。

## 已验收的固定候选

- APK：build/candidates/0.15.59/Chrona-0.15.59.apk，3241802字节，221／0.15.59，非debug，原签名。release＋lintRelease成功，build/release-0.15.59-{build,package,signature}.txt。源码未在构建后改变。
- 正常Android36隔离包46项及横屏深色6项：build/exams-schedule-native-{light,landscape}.txt。SQLite混合59行3页正反序、跨页定位、只读ID、筛选和账号／缓存、真实LAN HTTP，原生实际筛选／详情／旋转／取消／清除。没有DPI或分辨率覆盖。
- 原SQL／逾期／重复／4008行分页回归：build/exams-schedule-query.txt。真实Edge运行生产资产＋模拟手机API的日常及考试检查：build/exams-schedule-web.txt，混合全选只含38个本地可编辑ID。
- 主代理与持续复用的ui_review亲看压缩JPEG，受检范围签收。build/exam-schedule-{portrait-light,landscape-dark}-{filter,list,detail}.jpg及build/lan-daily-workspace/中的official-exam图。最初横屏过渡帧已替换为稳定截图。
- 功能合同和验证范围已更新docs/features/schedules/schedule-browser.md、docs/features/zju/zju-services.md、docs/validation-history.md。前序工作不重跑；重复截图遮挡仍仅讨论，未授权实现。

## 当前依赖与唯一下一步

手机仍是已验证的0.15.58／220；USB无手机，最后无线地址10.195.39.39:46721超时。下一次设备可用时，确认无课堂／AI工作后正常覆盖上述固定包，实际打开日程筛选考试，检查官方缓存行／详情；原LAN服务若因更新停止须正常恢复。不将模拟器／模拟API视作本轮真机验收，不反复要求无人值守用户处理ADB。

0.15.58真实官方查询已成功，7条未来考试与期中地点座位，见验证记录；未改变的获取协议不重复付费／联网验证。当前分发还须普通Git推送后发布0.15.59，目标精确本轮源码，不能发布旧0.15.58包。正式Release状态以build/release-0.15.59-push.txt及远端读回为准；发布文案build/release-0.15.59-notes.md。旧github.com:443网络阻塞不通过代理改写、重新鉴权或另造上传平台规避。

## 工具与保护

FastCtx不可用，PowerShell7兜底。JAVA_HOME=D:/Minecraft/java21，SDK=F:/Android/Sdk，GRADLE_USER_HOME=F:/Android/GradleCache，ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP和全部衍生物留项目build。公共gh仅F:/Programs/GitHubCLI/bin/gh.exe，保留现有登录。

共享AVD仅F:/Android/Avd/Chrona_Classroom_36／emulator-5554，串行使用、不克隆／重置／清数据。旋转已恢复accelerometer1／user0。真机USB序号10AG5S1SR4005X7，操作前校验Chrona前台，其他应用抢前台立即停后续点击。所有图先压缩，仅查看JPEG派生图。

用户AGENTS.md、角色toml、.backups、.local-backups和tools/Clean-Project.ps1不纳入本轮提交。外部衍生物清理已独立交付、由用户执行；不清公共工具／SDK／AVD／profile／签名。GlobalSend误发需求不属本项目。