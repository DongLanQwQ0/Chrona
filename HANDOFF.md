# 当前工作单

## 当前交付：折姜星辰考试安排，0.15.58／220已实现并更新手机

用户要求参考CrazySpottedDove/fiz查看考试时间。入口为「设置 → 折姜星辰 → 考试安排」。复用原学校账号、CAS／教务会话、SettingsPageLayout、圆形刷新与卡片；显示普通及期中考试的课程、官方时间／星期、考场和座位。当前与未来考试按时间排列，已结束放后面。不以学在浙大活跃学期删考试，不调用AI，不自动创建日程或写系统日历。

进入时先显示本机账号缓存并更新，手动可刷新；完整分页后AtomicFile替换，失败保留，空成功清空。异常时间留原文、缺考场座位明确待公布。缓存为可重新获取的cacheDir文件，不含凭据。账号更换／关闭不显示旧数据，退出清缓存；旋转保留单个请求，离开取消，构造client期间失效也在首次HTTP前复验并关闭。

源提交：`7ebff9f1cb89ae689c286380173b6c6732f7f5cc`。固定APK：`build/candidates/0.15.58/Chrona-0.15.58.apk`，3236982字节、220／0.15.58、非debug、原签名。release＋lintRelease通过，最后三条纯错误文案由Session统一追加保留状态后定向assembleRelease通过；源与安装包相同，见`build/release-0.15.58-{final-build,fixed-assemble,signature}.txt`。

USB正常覆盖于2026-10-27 23:46:01完成，首次安装2026-09-26 18:54:37保持；`build/release-0.15.58-phone-package.txt`。通过原App UI和已保存账号真实获取并刷新成功：7条未来考试及已结束分组。微积分期中11/7周六14–16，西2-101／121；线代期中11/15周日14–16，西2-104／53；大英1/6周三08–10等。实图及原XML：`build/ui-exams-phone/phone-exams-{loaded,refreshed}.{jpg,xml}`。未读取或输出凭据，不以合成数据冒充官方数据。当前手机停在考试页。

更新前LAN原本运行，安装使会话停止；已通过正常设置恢复，并确认`http://10.195.39.39:8765`和LanAccessService ee254de运行，见`build/release-0.15.58-phone-final-services.txt`。中途微信切前台即停止后续盲点；正常返回本应用后确认状态，未操作微信内容。

## 验收已闭环

- 正常Android36隔离包41项：`build/exams-native-light.txt`。103条完整分页、普通／期中／缺字段／异常时间／星期时区、空成功、后页失败、停滞、缓存账号隔离、真实触摸刷新及旋转。
- 正常横屏深色15项：`build/exams-native-landscape-dark.txt`。整页上滑移走标题、正文不被固定工具挤占；JPEG在`build/ui-exams-review/`。没有DPI／字号／分辨率覆盖，不恢复极端矩阵。
- 构造连接时退出的定向Session检查17项：`build/exams-native-final-lifecycle.txt`，阻塞构造→退出→放行后零HTTP，旧UI证据仍有效。
- 账号／原客户端回归21项、95＋44项、原作业排程51项：`build/exams-zju-recovery-regression.txt`、`build/exams-client-regression.txt`、`build/exams-planner-adapter-regression.txt`。
- 原专门ui_review已独立读源、亲看压缩深浅竖横图及真实手机官方结果，关闭本轮UI与生命周期缺口。详细记录已加入`docs/validation-history.md`；功能合同在`docs/features/zju/zju-services.md`。
- 0.15.57及更早工作均已闭环，旧证据见验证记录，不重跑。重复截图遮挡仍仅讨论，尚未授权实现；不得把它并入本次发布。

## 唯一下一步：恢复GitHub发布

代码已本地提交；普通`git push origin main`失败：github.com:443连接21050ms超时。随后只读TCP复核github.com:443=False、api.github.com:443=True；未重新鉴权或改代理，不用其它工具绕过历史代理路由拒绝。远端main仍eeca5ca，**v0.15.58尚未发布**；最后正式发布是v0.15.57。

当正常Git连接恢复后，主代理直接推送main，再用F:/Programs/GitHubCLI/bin/gh.exe发布v0.15.58，目标精确7ebff9f，上传上述3236982字节固定APK。发布文案已保存`build/release-0.15.58-notes.md`。不得上传旧包、重复安装、重跑有效矩阵或付费AI；只补远端资产／标签读回。当前需求已在手机可用，向用户说明发布缺口即可。

## 工具与保护

- FastCtx不可用，PowerShell7兜底。JAVA_HOME=D:/Minecraft/java21；SDK=F:/Android/Sdk；GRADLE_USER_HOME=F:/Android/GradleCache；ANDROID_USER_HOME=F:/Android/user-home；TEMP/TMP及所有衍生物留项目build。工具留公共位置。
- USB 10AG5S1SR4005X7；共享AVD仅Chrona_Classroom_36／emulator-5554，不克隆、重置或清数据。AVD旋转已恢复accelerometer1／user0，尺寸和密度未修改。原生fixture恢复自己的偏好及缓存。
- 所有图先压缩，视觉工具只查看JPEG派生图，不查看／上传PNG原图。
- 保护用户AGENTS.md、角色toml、.backups、.local-backups、tools/Clean-Project.ps1，未纳入提交。禁止broad add/reset。
- 外部衍生物审计／清理列表已独立交付，用户自行清理；共享工具、SDK、AVD、profile、签名和恢复资料保留，A1–A7当前不存在。GlobalSend误发需求不属本项目。
