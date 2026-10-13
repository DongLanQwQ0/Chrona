# vivo 原子通知实现与调研 — 2026-10-13

## 官方协议

已通过公开页面加载的文档接口取得《原子通知技术规范》正文，来源doc896。确认本地Notification extras中operation为创建/更新/结束；template、baseInfos和infos是大卡数据，island是左右岛Bundle。会议日程scene为METTING（官方拼写），dismissWhenKill=false表示进程终止时不清除，此值也是文档默认值。

## Chrona 0.14.26

VivoAtomicNotification以Build.MANUFACTURER等于vivo（不区分大小写）限定适配范围；非vivo设备不添加厂商字段。AgendaReminder沿用原提醒通知ID和渠道，将标题、起止时间、地点及详情PendingIntent放入模板1和左右岛；左岛展示图标及开始时间，右岛展示标题并支持详情跳转。已有通知更新使用operation1；切换日程、隐藏、延后、关闭提醒或结束时，已有原子通知先发送operation2再取消。系统没有已有通知时使用创建。

保留原通知的普通内容、RemoteViews及查看/延后/隐藏动作，showNotify=true用于厂商不能展示时的普通通知呈现；没有重复发第二条通知。没有增加VPush SDK、网络推送或前台保活服务。原有提醒设置默认开启，日程开始前30分钟触发。

## 限制与验证

本地接口不等于免接入权限。vivo/阿里云指南仍要求申请场景接入权限；个人自用是否可测试需以设备实际接受及vivo答复为准，不能以代码绕过系统授权。不同系统/机型未必展示原子岛。

生产适配器26项离线验证覆盖厂商过滤、字段类型、必填图标和模板、创建/更新/结束、普通通知保留及结束异常仍取消。尚无本版真机原子岛截图，不能宣称出岛成功。子代理调研服务容量不足，正文取得及核验由主代理完成。

## 官方来源

- https://dev.vivo.com.cn/documentCenter/doc/896
- https://dev.vivo.com.cn/webapi/doc/info?id=896 （页面脚本实际使用的公开正文接口）
- https://dev.vivo.com.cn/documentCenter/doc/894
- https://help.aliyun.com/zh/document_detail/3030718.html