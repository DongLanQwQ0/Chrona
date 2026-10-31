# 当前交付：0.15.81 / 243 已正式发布

## 范围与所有者

- 本聊天接续 core 的构建交付，负责课堂 AI 修复、集成候选、手机更新及结果收口；不再并行改 Dock。core 交付见 `docs/Communication/to-external-agent.md`，既有 Dock 决定见 `docs/liquid-glass-dock.md`。
- 正式Release：[v0.15.81](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.81)，非draft、非prerelease，已设latest。资产`Chrona-0.15.81.apk`为3302645字节，与已安装及验证候选完全相同，原签名，非debuggable。
- 当前 checkout 为 `experiment/glass-lens`，含 main 的 Dock 合并及独立预览标签。保护 AGENTS.md、备份目录、角色 toml 和用户清理脚本，不纳入提交或清理。

## 课堂候选与有效证据

- 9月23日 C 程序设计基础第7–8节：真实成品为1篇、7主题、2条不同安排；39张不同正文配图，40张实际分析图片。证据：`build/classroom-c-audit/real-final-report-deduplicated.json`、`real-final-summary-readable.md`。
- 最后只新增一次安排分组请求，原25轮审计不变；已验证原消息前缀、冻结options、文稿、7主题及全局偏好原样不变。最后一轮服务返回input270436、cached269824，保留真实usage，不推算缓存命中率。
- 2200项增强检查、quick8/8通过；debug/release构建与lint均通过，原签名及release非debuggable已核对。修复了原快照检查夹具漏建作业计划表及异常退出时工作线程未收尾的问题，未改生产数据库。
- 0.15.81正式包已实际覆盖安装，拉回APK与候选逐字节相同，首次安装时间仍为原值。vivo同版本安装可能同时回报拒绝与成功，不应只据ADB退出码判断用户拒绝；正常版本升级成功。候选及过程证据位于 `build/classroom-c-audit/atomic-candidate/`。

## 已交还设备

- 没有运行中的AI、安装或手机控制任务；临时验证包已卸载，本任务手机临时文件及空目录已清理。
- 未改账号、全局AI偏好或手机安全设置；未导出旧总结备份。正式包不是preview，已有独立预览包不发布也不擅自删除。

## 唯一下一步

发布任务已完成，无待处理安装或AI任务。GitHub latest及公开APK下载已回读，公开下载HTTP200且字节与真机候选相同；证据见`build/classroom-c-audit/atomic-candidate/release-latest.json`及`release-readback-public/`。

按既定策略，v0.15.80和v0.15.61的元数据、全部附件已归档到项目`build/release-cleanup/20261031-v0.15.81/`后删除Release记录；Git标签和源码保留，旧附件链接已失效。现保留v0.14.0、v0.15.0、v0.15.81及原子岛配置包。后续视觉和日常体验由用户自行检查，不追加截图审核或极端DPI场景。
