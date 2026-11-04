# 拾时 · Chrona：项目约束

通用执行规范继承 `C:\Users\DongLan\.codex\AGENTS.md`。本文件仅补充 Chrona 的入口、业务契约与明确例外。

## 项目入口

| 内容 | 权威入口 |
| --- | --- |
| 产品与使用 | [README](README.md)、[使用指南](docs/user-guide.md) |
| 当前交付、候选与下一步 | [HANDOFF](HANDOFF.md) |
| 构建、代码结构与签名 | [开发指南](docs/development.md)、`app/build.gradle`、`gradle.properties` |
| 功能契约 | [文档索引](docs/README.md)、`docs/features/` 下的对应文档 |
| UI 样式与已确认动效 | [样式规范](docs/ui-style-contract.md)、[交互基线](docs/ui-interaction-baseline.md) |
| 公共组件及中文称呼 | [UI 库](docs/ui-library.md)、[组件名称](docs/ui-component-names.md) |
| 验证与发布 | [设备验收](docs/device-acceptance.md)、[验证记录](docs/validation-history.md)、[发布指南](docs/github-releases.md) |
| 外部 Agent 接口交接 | [通信约定](docs/Communication/README.md) |

## 平台与实现边界

- Android 使用原生 Java，正式包名为 `com.donglan.chrona`。主代码在 `app/src/main/java/com/donglan/chrona/`，共享代码在 `shared/src/main/java/`，局域网页资源在 `app/src/main/assets/lan/`。
- 局域网页定位为跨设备快速查看，只保留首页、日程和收件箱；三者操作与移动端基本一致，不扩展为完整课堂客户端。复用项目 SVG 素材及生成入口；主题默认跟随手机，保留网页独立设置。
- 版本、SDK、数据库版本与签名路径以当前源码和配置为准，不从旧文档复制固定值。覆盖更新沿用 `chronaDebugKeystore` 指向的既有签名。

## 本项目 UI 基线

- 界面对象使用 `docs/ui-component-names.md` 的中文称呼，并对应实际代码入口。具体样式、圆角档位与动效参数以 UI 规范和交互基线为准，不用通用默认值重设已确认效果。
- 二级设置复用 `SettingsPageLayout.content/header/show`：左右内容边界 **20dp**，标题后至少 **16dp** 间距由公共标题栏提供，不抵消、不重复叠加。
- 选择栏复用 `DockNavigationLayout` 及适用的 `motionHost()`；给高光、整栏放大与惯性伸展保留真实布局空间。标题操作复用 `HeaderActionGroup`，日期／分页切换复用 `DateIntervalBar`。
- 图片附件、PPT 与笔记插图共用 `AttachmentViewerActivity` 的全屏查看交互，不按入口另做查看器；具体缩放、滑动、保存与拖放行为见对应功能文档。
- 正常竖屏、横屏及分屏必须让主要内容有足够空间；横屏上滑可将非必要顶部工具区滚出视口。不要通过修改模拟器分辨率或 DPI 制造极端场景，代替普通场景的验收。
- 视觉证据先生成压缩 JPEG／WebP 衍生图，再查看或上传；禁止直接读取或上传 PNG 原图进行视觉验收。

## AI、课堂与数据契约

- AI 按用户选择的服务、模型、协议与参数调用；默认协议为 Responses。不能用模型名称猜测来静默忽略参数、切换协议或降级。
- 已发送的消息、原生回复、图片与冻结参数保持原样，只追加上下文；复用匹配的更正文稿和上下文缓存。失败的未提交板块整体回滚，重新生成该板块，不拼接失败片段。该历史不可变要求属于明确保留的恢复契约，不借通用精简删除。
- 课堂成品分开保存学习知识、课程事务与真实行动。第三档是精简自学讲义：沿教师知识脉络自主组织解释、可靠推导与例题；补题明确标记。评分、考试、作业等教师事实忠于原始资料，保留适用对象、数字、方向、条件及后文澄清。
- 课堂调优回归按单课串行：读完整成品、列问题原文及预期方向，做通用修正，经用户确认后再扩展；保留生产的批量和并发能力。课堂 AI 请求的回归使用 `low` 或 `medium`，不擅用 `high`，不额外增加昂贵的语义拒绝或自动重试链。
- 直接下载、批量处理与监视课后补全复用同一资料获取及总结链路；匹配缓存可复用，缺失素材重取。接口与完成条件见 [课堂总结](docs/features/zju/classroom-summary.md)。
- 原始来源、系统日历身份、用户手动安排和审计关联不能因重新解析、同步或去重而被无关覆盖；具体规则见对应日程、教务及同步文档。

## 本地验证与项目授权

- 项目派生文件统一留在本仓库：构建、日志、截图、导出和回归材料放 `build/` 下对应任务目录；公共工具链不复制进项目。
- 构建复用 Gradle Wrapper；入口为 `:app:assembleRelease`、`:app:lintRelease`。离线检查入口为 `checks/run_checks.py` 及对应专项；按改动选择，不因小改动重跑无关全量。
- 完整日志与长篇笔记的例行阅读交由用户指定的 **Terra** 只读整理；主代理读取结论、问题位置与必要原文，定点核对。建立全局理解必需的规范仍由主代理自己阅读。
- 本项目常规源码提交、推送与验收后的发布已获授权。仅源码同步不必发 Release；有 APK 分发或应用内更新需要时再按发布指南发布，不为每个测试小版本创建 Release。
- 与外部 Agent 的项目通信使用 `docs/Communication/` 的现有约定；跨聊天协调仅针对真实共享资源冲突，不例行广播。
