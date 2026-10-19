# 文档索引

## 使用与项目要求

- [使用指南](user-guide.md)：下载安装、操作和常见问题。
- [项目基本要求](project-requirements.md)：数据、工程、权限、验证与文档职责。
- [开发指南](development.md)：技术基线、本机构建和代码入口。
- [UI 设计规范](ui-style-contract.md)：任何 UI 操作前必读；全局视觉、布局、公共组件与验收约束。
- [已确认交互基线](ui-interaction-baseline.md)：保留具体用户决定，不与通用设计原则混写。

## 按功能开发

| 功能 | 文档 |
| --- | --- |
| AI 提取 | [处理链路](features/ai/ai-pipeline.md) · [分类与时间](features/ai/ai-time-extraction.md) · [服务请求](features/ai/ai-request-notes.md) |
| 日程与收件 | [浏览、筛选与批量操作](features/schedules/schedule-browser.md) · [重复日程](features/schedules/repeat-schedules.md) · [候选合并](features/schedules/candidate-merge.md) · [时间选择](features/schedules/date-time-picker.md) · [附件与链接](features/schedules/selection-and-link-fetch.md) |
| 日历与课表 | [本地课表](features/calendar/course-timetable.md) · [系统日历与来源](features/calendar/system-calendar-home.md) |
| 提醒 | [日程提醒](features/reminders/agenda-reminder-settings.md) · [vivo 原子岛配置](features/reminders/vivo-atomic-setup.md) |
| 桌面小组件 | [小组件与草稿](features/widgets/desktop-widgets.md) |
| 折姜星辰 | [作业、学校课表与账号边界](features/zju/zju-services.md) · [课堂总结](features/zju/classroom-summary.md) |
| 连接与同步 | [局域网访问](features/connectivity/lan-access.md) · [WebDAV 协议](features/connectivity/sync-contract.md) |
| 数据保护 | [备份范围与恢复](features/data/backup-coverage.md) |

## 验证、发布与历史

- [性能测量](motion-startup.md) · [设备验收模板](device-acceptance.md) · [发布指南](github-releases.md)
- [当前状态与接续](../HANDOFF.md) · [版本更新](../CHANGELOG.md) · [实际验证记录](validation-history.md)
- [历史归档](archive/README.md)：旧设计、调研、交接与验证原始记录，仅供追溯。

新增功能优先更新对应功能文档。通用规范不积累逐页特例，交接页不积累开发流水账，当前文档不引用旧计划作为待完成工作。
