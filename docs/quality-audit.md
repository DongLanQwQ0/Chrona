# 2026-10-01 软件审查与完善清单

以 0.13.74 的当前源码、已实现功能和现有用户操作为基线。以下是本轮代码审查确认的问题；“未发现”不代表软件不存在其他缺陷。先完成清单，再按数据保护、后台正确性、交互与文档的顺序实施。不调用收费 AI，不更换工具链或引入外部服务。

## 已确认问题

| 编号 | 优先级 | 问题与影响 | 主要位置 | 处理状态 |
| --- | --- | --- | --- | --- |
| A01 | P1 | 详情页旋转后未保存的标题、时间、地点等编辑丢失 | TaskDetailActivity.onSaveInstanceState / addCandidateEditor | 已修复；草稿替身回归通过，真机待验 |
| A02 | P1 | 系统返回绕过顶部返回的未保存、正在保存保护 | TaskDetailActivity.addDetailHeader | 已修复；双版本返回接入复核，真机待验 |
| A03 | P1 | 附件异步导入读取可变 taskId，可能错绑记录；完成后重建可能擦掉编辑 | TaskDetailActivity.addCaptureImages / addOrdinaryFiles | 已修复；目标与回调复核，真机待验 |
| A04 | P1 | 确认日程期间仍能翻记录，后台保存和回调可能使用另一记录的 ID | TaskDetailActivity 的分页与确认流程 | 已修复；session / 分页复核，真机待验 |
| A05 | P2 | 已写入日程保存备注只改本地，系统日历备注不更新 | TaskDetailActivity.showNoteEditorDialog | 已修复；仅备注更新复核，provider 待验 |
| A06 | P2 | 图片缩回原倍率后可能偏离中心，且不能拖回 | AttachmentViewerActivity.onImageTouch / 缩放回调 | 已修复；构建通过，图片手势待真机 |
| A07 | P1 | 备份清单和原始数据库不是同一快照，并发写入可让自己导出的 ZIP 无法恢复 | ChronaDataBackup.createArchive | 已修复；并发 SQLite 快照回归通过 |
| A08 | P2 | 恢复时强制日历事件唯一占用，原本共享的关联被拆成重复事件 | ChronaDataBackup.restoreCalendarLinks | 已修复；共享映射独立复核通过 |
| A09 | P2 | 无日历权限恢复后候选已待确认，但任务仍显示已写入 | ChronaDataBackup.restore / restoreCalendarLinks | 已修复；恢复状态 SQL 回归通过 |
| A10 | P2 | ZIP 校验期间旋转或退出，旧 Activity 仍弹对话框，临时文件缺清理 | BackupRestoreActivity | 已修复；生命周期与操作互斥复核 |
| A11 | P2 | 未配置 AI 的合法备份，导入已配置设备时错误组合空地址、空模型和旧密钥 | ConfigBackup.apply | 已修复；实际配置逻辑替身回归通过 |
| A12 | P1 | 本地输出文件写入或关闭失败被当成网络异常重试，可能重复收费 | ProcessingJobService.requestWithRetries | 已修复；实际客户端预览失败回归通过 |
| A13 | P1 | 停止任务只中断线程，阻塞抓取返回后仍可能发出 AI 请求；旧执行与重新调度可能冲突 | ProcessingJobService / ChatCompletionClient | 已修复；假连接 / 实际协调方法回归通过 |
| A14 | P2 | 昨天或今天已结束的记录先耗尽 SQL 限额，小组件漏掉真实未来安排 | TaskStore.widgetCandidates / WidgetAgenda | 已修复；历史溢出 / 全天 SQL 回归通过 |
| A15 | P2 | 240 秒重试预算仅在失败后检查，下一次请求仍能获得完整超时额度 | ProcessingJobService / ChatCompletionClient | 已修复；慢速流 / 剩余预算回归通过 |
| A16 | P2 | 导入相机照片未应用 EXIF 方向，重新编码后可能横置或倒置 | ImageStore.decodeScaled | 已修复；构建通过，实际 EXIF 渲染待验 |
| A17 | P2 | 完整模型输出首次格式化在主线程执行，大输出会卡住页面 | ModelOutputActivity.showPage | 已修复；后台读取复核 / 输出存储回归通过 |
| A18 | P3 | AI 说明同时写 schedule-v2 和 schedule-v3，与当前常量矛盾 | docs/ai-pipeline.md | 已更新；与实际 Prompt 常量一致 |
| A19 | P3 | 旧开发计划将已经完成的功能列成待开发，容易误导继续维护 | docs/development-plan.md | 已更新；历史计划明确标记并链接本清单 |
| A20 | P2 | 备份入口声称不包含壁纸，与实际打包和恢复能力矛盾 | BackupRestoreActivity 的说明 | 已修复；界面说明与实际能力一致 |
| A21 | P3 | 早期 v1–v4 迁移检查误把后续迁移块纳入断言，当前源码下直接失败 | checks/verify_migration.py | 已修复；早期迁移范围回归通过 |

## 建议与现有边界

- **相对时间的参照点：** 本次历史审查时按开始解析时刻解释；已在 0.13.85 明确为记录创建时刻，首次、重试和重解析共用参照，并在详情显示依据。见 [AI 解析链路](ai-pipeline.md)。
- **真实设备验收：** 小屏、大字号、旋转、系统返回、日历权限拒绝、慢网取消、桌面小组件和覆盖安装值得做系统回归。离线模型与静态复核不能代替 Android 设备测试；当前是否有设备将在验证阶段检查。
- **继续保持的边界：** 原始记录仍须由用户逐项审核后写入日历；不新增自动发布日程或第三方开放式搜索。普通附件只提供元信息，公共 Downloads 副本由用户保留。

## 已核验并排除的疑点

- 图片孤儿清理已合并 capture 草稿中的图片引用，不能把草稿图片误报为未引用。
- 小组件旧版本使用了 notifyAppWidgetViewDataChanged，未发现缺少刷新调用的证据。
- 本轮未确认更新检查、密钥加密存储、首次教程步骤恢复存在新的同等级错误。

## 执行与验证记录

实施过程中逐项更新上表，并在这里记录针对性的验证、源码提交与分发结果。数据库和用户数据的格式兼容必须保留；每项不能执行的真机检查会明确注明。


### 本轮实施结果

- 已处理 A01–A21，发布目标为 0.13.75 / versionCode 95；数据库仍为 v9，既有数据格式没有迁移。相对时间参照点属于待制定的产品规则，本轮保留现有行为。
- 编辑和保存：raw UI 草稿、当前保存 owner 和固定目标 ID 分别保护旋转、生命周期和异步写入；独立保存过的备注以数据库最新值为准。保存中禁用编辑与收件分页，系统返回保留确认提示。
- 备份和配置：清单与 DB 同源快照，保留删除 ID 高水位，公共附件副本继续保留；共享系统事件仍共享，缺权限的关联转为待确认。导入、恢复和导出互斥，旧窗口的临时准备结果清理；空 AI 配置保留已有连接。
- 后台：预览文件异常仅影响可查看的输出；取消先停等待者，再释放原执行，避免旧任务退出时等待者发起请求。链接、AI、兼容降级和重试共用单调 deadline，持续慢速输出不能无限续期。
- 界面：小组件历史与未来分池，图片方向和复位修正，长输出后台格式化；文档和旧迁移检查与当前实现对齐。
- 具体离线检查、复核和设备限制见[验证记录](validation-history.md)。这些检查使用真实 Java/SQL 与明确标注的替身，不等同于 Android 真机验收。ADB 当前无设备，未安装或运行真实数据恢复，也没有付费 AI 请求。

### 源码与分发结果

- 修复分为 8 个独立提交，源代码构建基点 `8eaa5be36293da0fa39972ab97b48e0c621baf98`，标签 `v0.13.75` 已推送到 `DongLanQwQ0/Chrona`；本节为发布后的文档记录。
- [0.13.75 正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.13.75) 已发布并设为最新，官方 latest 接口复查一致。
- [Chrona-0.13.75.apk](https://github.com/DongLanQwQ0/Chrona/releases/download/v0.13.75/Chrona-0.13.75.apk) 为 1,323,922 字节；远程资产 digest 与本地 SHA-256 `896eab67688458a8117b819ac7048a2d74543e7ed15ec9026ad69b245dec89c5` 一致，原签名保持不变。
