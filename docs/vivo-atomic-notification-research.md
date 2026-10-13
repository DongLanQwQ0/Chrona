# vivo 原子通知实现与调研 — 2026-10-13

## 最终进展：0.14.28（覆盖下方早期实验状态）

在用户明确授权后，测试组件借用ADB临时STATUS_BAR_SERVICE身份，仅注册包含Chrona的自有场景CHRONA_AGENDA，随后撤回身份；场景读回为true。未修改系统METTING或其它应用名单，生产APK不执行注册。用户要求成功保留、失败关闭，本机成功后保留此场景。

仅注册还不足以显示原子岛。本机通知模板额外校验shortInfos.image、shortInfos.describeShort及shortInfos.coreInfoShort；补齐后，普通应用身份发送的测试通知已在状态栏显示原子岛。私有截图build/device-ui-audit/atomic-short-status.png和日志atomic-short-template-device.txt记录显示与创建/更新测试；通知记录带VIVO_SUPERX_TAG。此证据只适用于已测固件，不证明所有vivo系统接受同一路径。

最终生产提醒使用原生BigText卡片和应用图标，保留详情、5分钟后提醒、隐藏本次；仅vivo添加厂商字段。设备已交还用户，最终样式、动作交互、重启持久性和完整提醒生命周期待后续真机复验。适配器36项、通知工厂22项以及配置脚本6种离线情况验证通过，完整检查50/50；这些结果不替代实际通知视觉和交互验证。

个人开发设备工具与关闭方法见[vivo原子岛设置](vivo-atomic-setup.md)。隐藏接口的本机实验不改变官方准入要求，也未代用户发送审核邮件。

## 官方协议

已通过公开页面加载的文档接口取得《原子通知技术规范》正文，来源doc896。确认本地Notification extras中operation为创建/更新/结束；template、baseInfos和infos是大卡数据，island是左右岛Bundle。会议日程scene为METTING（官方拼写），dismissWhenKill=false表示进程终止时不清除，此值也是文档默认值。

## Chrona 0.14.26

VivoAtomicNotification以Build.MANUFACTURER等于vivo（不区分大小写）限定适配范围；非vivo设备不添加厂商字段。AgendaReminder沿用原提醒通知ID和渠道，将标题、起止时间、地点及详情PendingIntent放入模板1和左右岛；左岛展示图标及开始时间，右岛展示标题并支持详情跳转。已有通知更新使用operation1；切换日程、隐藏、延后、关闭提醒或结束时，已有原子通知先发送operation2再取消。系统没有已有通知时使用创建。

保留原通知的普通内容、RemoteViews及查看/延后/隐藏动作，showNotify=true用于厂商不能展示时的普通通知呈现；没有重复发第二条通知。没有增加VPush SDK、网络推送或前台保活服务。原有提醒设置默认开启，日程开始前30分钟触发。

## 限制与验证

本地接口不等于免接入权限。vivo/阿里云指南仍要求申请场景接入权限；个人自用是否可测试需以设备实际接受及vivo答复为准，不能以代码绕过系统授权。不同系统/机型未必展示原子岛。

生产适配器26项离线验证覆盖厂商过滤、字段类型、必填图标和模板、创建/更新/结束、普通通知保留及结束异常仍取消。尚无本版真机原子岛截图，不能宣称出岛成功。子代理调研服务容量不足，正文取得及核验由主代理完成。

## 2026-10-13 真机验证补充（0.14.27 开发包）

vivo 真机执行生产适配器创建、间隔12秒更新、结束测试，通知存在检查通过，但 `getSceneStatus(实际应用包名, METTING)` 返回false。因此本轮只能确认普通通知发送成功，不能确认原子岛已经显示。补齐官方要求的普通胶囊Bundle与原子岛Bundle同时发送；适配器32项协议/生命周期替身检查通过。

通过公开正文接口取得doc894，指南明确要求应用先在vivo应用商店上架，向oosyztz@vivo.com申请接入权限，审核准入后由vivo开通权限并联调。这是文档给出的正式开通路径，未发现自用应用的豁免路径。sceneEnabled=false也可能表示对应场景开关关闭，尚未通过手机设置界面区分关闭与未准入。未发送任何对外申请邮件。

## 官方来源

- https://dev.vivo.com.cn/documentCenter/doc/896
- https://dev.vivo.com.cn/webapi/doc/info?id=896 （页面脚本实际使用的公开正文接口）
- https://dev.vivo.com.cn/documentCenter/doc/894
- https://help.aliyun.com/zh/document_detail/3030718.html
