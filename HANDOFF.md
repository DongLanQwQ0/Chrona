# 当前工作单

## 当前交付与唯一下一步

当前交付已完成：用户明确接受现固件约200ms系统过渡；正式30／60秒已恢复并覆盖安装手机，0.15.41／203已发布为Latest：[GitHub Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.41)。标签对应源码`e0dcedb28831e4d69c7546cd0ec759df659368a6`，资产`Chrona-0.15.41.apk`为3120586字节。正式构建、签名、升级及真实30／60秒核对通过。当前无待执行修复；完全断开ADB独立运行未单独验收，作为证据边界记录，不以无线连接冒充。

2026-10-23。用户确认临时5／10秒版的胶囊计时已恢复，随后要求修复“原生大卡片关闭后先变窄，再变详细”的闪动。**闪动未修复，已定位到当前OriginOS系统宿主固定的200ms中间动画；不宣称应用侧修好。**

手机版已于19:49:55保留数据覆盖安装正式0.15.41，无测试后缀；普通辅助服务Bound、Crashed空。系统设置确认在前台后返回桌面，＋24秒完整／＋33秒精简；原生点击后点外关闭，＋55秒完整／＋63秒精简，accepted=true。未用instrumentation／UiAutomation／手动tick，未创建新记录。第一组Dashboard重入的home25／33均短，未用作桌面边界证据；有效新组由系统设置前台确认起算。

## 当前候选与手机状态

- 源码203／0.15.41，远端标签及正式Release均已创建，GitHub Latest读回v0.15.41、draft=false、prerelease=false，APK资产uploaded；公开下载HEAD返回200及3120586字节。保留本地其他修改，禁止重置。
- `capsuleTest`仅作独立开发变体保留；正式debug／release均30／60秒，手机已退出短时测试。临时包不得作为正式发布资产。
- 最终正式构建`build/release-0.15.41-final-build.txt`：debug/release assemble及lint成功。`build/release-0.15.41-artifact-check.txt`含APK签名、包名版本、DEBUG=false及DEX内30/60常量；上版证书相同。正式APK为`build/candidates/0.15.41/Chrona-0.15.41.apk`。
- vivo V2527A／OriginOS16当前安装正式0.15.41，不含即时快投实验；设备唯一标识仅留本地证据，不公开提交。
- 用户关闭独立「日程后台更新」频道；读回importance=0，保留关闭，不能改成关闭整个应用或事项通知。
- 用户明确授权创建一条临时活动、验后删除；`capsule_live_fixture`的task116／candidate423已清理，读回task和候选不存在，记录`build/capsule-live-fixture-cleanup.txt`。**没有待删除测试活动。** 不再操作用户真实日程、账号、系统日历或同步配置。

## 闪动结论与证据

- 常规点击建立原点击时刻＋10秒（正式＋60秒）的期限。普通及秒级刷新不续时，不强制开关大卡，外部点空白仍由系统原生关闭。
- 绕过350ms合并刷新、用最新通知快照即时投影的实验通过离线检查，但实际`flash-after.mp4`仍经窄黑再扩宽；持有3秒后收起也一样。专门只读`/root/ui_review`已否决无闪回签收，该实验及其专用检查均撤回。
- `build/atomic-event-review/flash-before.mp4`、`flash-after.mp4`、`flash-held.mp4`及对应10fps接触表有效。录制期间无UiAutomation、instrumentation或手动tick；USB条件。临时活动由生产提醒流程正常发布。
- 只读宿主APK及派生源码在`F:/Android/ChronaBuildCheck/capsule-host/`。`DynamicCapsuleStateController`规定展开态4经中间态1转2／3；`ContainerAnimatorCommon`中间态使用系统资源固定宽度且duration=200；展开期间不重建收起用左右view。主代理已查看关键代码；该机制与录屏200ms窄黑阶段吻合。
- 已检查通知协议未发现跳过该中间态的app入口。没有修改系统APK、全局动画、强制展开或阻止关闭；不再无依据试参数。此限制只确认为当前固件，不推广其他OriginOS版本。
- 恢复后定向检查`build/capsule-final-restored-checks.txt`：正式45／短时18／vivo111通过，构建变体隔离通过。无闪回目标仍未达到。

## 本批其他已完成内容

- AI `schedule-v16`：三个请求时间锚点均附程序时区计算的星期，动态时间上下文在完整稳定system前缀末尾；user只保留输入。缺年采用明确上下文或本地当前年，不因仅缺年份存疑；JSON只剥外层escape／代码围栏，保留字符串正文与原始流展示。
- 通用执行建议取消截止前6小时至3天限制，利用整个准备周期并按工作量留缓冲；不针对征文特判，不自动重排已保存日程。请求493／归一化59／排期418／自动发布16通过。无付费在线解析。功能与规则见`docs/features/ai/ai-time-extraction.md`。
- 重复日程自动检测、默认不选、按组提交、取消去重、新组提示、前后台与原生／网页入口已验收，独立UI审核通过。数据336＋关联102／16／45，真实Edge专项和模拟器Activity／SQLite通过。全网页旧无关disclosure时序失败仍未称全通过。
- 紧凑原子卡、Lucide任务菜单、每个重复成员的标题／来源／地点、窄屏大字预览已验收，不因本轮重新设计。
- 备份恢复可靠性、共享日历引用、恢复并发、课堂入口隐藏等本批已验收项保留，具体实现和有效证据见`docs/validation-history.md`及各功能文档，不重复整轮。

## 仍保留的验收边界

- 物理断开ADB后的独立胶囊运行仍未正式验收；用户此前一直插着USB，不把辅助程序退出当拔线。当前故障定位不等于该项通过。
- 先前普通服务source=null点击问题已修，USB59／61、再次展开及保持卡片65秒有效证据仍适用。旧home29/31本轮均为精简，不签为桌面边界通过。
- 当前无需继续付费AI、真实WebDAV或应用内手动更新复验；手机LAN实链路仍缺实际开启访问，模拟API不替代它。
- 本轮已按授权发布同候选源码、版本、安装包及签名；保留数据升级已完成。旧含强制展开APK不属于当前正式资产，禁止分发。
- 本地`AGENTS.md`、`.backups/`、`.local-backups/`及角色toml不纳入业务提交。
- FastCtx未提供，使用PowerShell7；Java `D:/Minecraft/java21`，SDK `F:/Android/Sdk`，Gradle缓存`F:/Android/GradleCache`，Android用户目录`F:/Android/user-home`，下载与缓存仅F盘。
