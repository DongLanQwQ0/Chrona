# 当前状态与接续

## 从这里开始

先读[文档索引](docs/README.md)、[项目基本要求](docs/project-requirements.md)和[UI 设计规范](docs/ui-style-contract.md)，再按所改功能阅读对应文档。先检查工作区改动，保留其他人的文件。

## 0.15.26 UI 续轮

2026-10-19 晚间要求已纳入 `docs/ui-style-contract.md`，项目 `AGENTS.md` 强制 UI 操作前完整阅读该规范、交互基线及功能文档。紧凑工作区、首页胶囊与地点行、公共顶部位置和 Lucide 返回／确认已实现，并于 2026-10-20 发布为 `v0.15.26` Latest；验证条件与产物见[验证记录](docs/validation-history.md)。

日历采用「底板铺满、内部轨道留动效空间」，未修改弹簧参数；常规尺寸与窄屏大字体的首尾长按、拖出松手、双向快速甩动均已通过。实际截图另发现窄屏大字体年历日期粘连，已改为按字宽选择一列或两列月份。其余有效证据复用，不重跑全套。2026-10-20 已获发布授权，正式产物与验证见验证记录。

收尾复查另修复系统日程开关被权限监听器退回关闭时的动画与状态提示；旧实现的失败回归、修复后 39 项专项及原生监听器检查已记录，最新构建日志 `build/compact-recheck-build.log`。

## 当前版本

- 当前 **0.15.26 / versionCode 188** ，数据库版本 **14**；版本以 `app/build.gradle`、`TaskStore.DATABASE_VERSION` 为准。
- 源码与安装包：[GitHub Releases](https://github.com/DongLanQwQ0/Chrona/releases)。
- 本轮补齐网页日历、批量处理、来源管理、附件采集和同步冲突处理，统一审核与来源查询；修复备份列顺序、网页日期边界及同步网络占锁。详见[局域网访问](docs/features/connectivity/lan-access.md)。
- 网页续轮增加年历与月日跳转、周密度和自动同步开关；修复附件冲突重试、文件名原文、记录单独删除及冲突版本字段展示。续轮备份 `build/backups/20261018-web-followup/`，构建日志 `build/0.15.23-web-followup-build.log`；同步 API 当前为 67 项通过。
- 0.15.18 的选择栏手感已获用户确认，见[已确认交互基线](docs/ui-interaction-baseline.md)。后续修复和性能优化保持该效果。

## 最近证据与实际限制

0.15.26 正式 Debug／Release、双 lint 与 quick 7/7 通过；签名沿用旧版且 release 关闭调试。模拟器从正式 0.15.25 覆盖升级并启动成功；升级前为空首页，实体手机非空数据升级未验收。产物 `build/distributions/0.15.26/Chrona-0.15.26.apk`，日志 `build/0.15.26-release-build.log`。

0.15.25 收录工作区与课堂总结续轮：三页浮动导航、浙大课表直读、两轮图片分析、后台服务与通知、执行时间建议及独立自定义提示词。圆角五档、日期文字裁切与外层按压空间、移动亚克力取样已统一。完整[需求对照与验收](docs/followup-acceptance-20261019.md)列明条件和差异。Debug/Release、双 lint、androidTest 编译及 quick 7/7 通过；正式包 `build/distributions/0.15.25/Chrona-0.15.25.apk` 沿用旧签名且关闭调试，模拟器从正式 0.15.24 覆盖安装并启动成功。真实账号验证应用此前已备份后覆盖安装并读取 10 门学校课程及旧课堂素材；本次未在实体手机验收非空数据升级，真实付费 AI 生成仍未执行。

重复日程与网页体验统一已完成。规则、单次跳过/改期/完成及停止历史语义见[重复日程](docs/features/schedules/repeat-schedules.md)；网页增加独立排序、日期预设、跨页全选与删除，并共用重复操作。既有 81 项检查在首轮与失败项修复复跑后全部通过；新增 AI、投影及 Provider 检查通过，统一入口现为 84 项。独立验证包 debug/androidTest 与 lintDebug 通过；真机 Provider/原生停止交互 22 项及真实 SQLite/HTTP 检查通过；浏览器已验证真实排序、改期及停止状态。模拟器补验停止后空月份视图通过，验证服务关闭后数据库收件数为 0；手机断连后的验证夹具清理未现场复核。源文件备份 `.local-backups/recurrence-20261019/source-before.zip`，构建日志 `build/recurrence-validation-build.log`。验证包名 `com.donglan.chrona.validation`，不覆盖正式应用。

课堂总结已完成。新入口、周课表/倒序分页、字幕截图分段整理和含图 Markdown ZIP 已实现；开发说明见[课堂总结](docs/features/zju/classroom-summary.md)。debug/androidTest 构建与 lintDebug、既有 full 79/79、课堂总结 77 项及客户端 42 项检查通过，并完成生产 Java 智云只读访问。续轮修复导出恢复初始化、确认框滚动，公共普通控件收敛为轻圆角；选择栏保持已确认几何。相关七项专项及续轮构建/lint 通过。模拟器已修复并使用 WHPX + RTX 5060 宿主图形加速；课堂深浅主题、320dp/1.5倍字体、键盘滚动与原生图片/ZIP 检查通过，实际截图发现的日期/时间列断行与导航字号问题已修复。公共选择栏原生 27 组布局及日历交互检查通过。0.15.24 Debug/Release、双 lint 和 androidTest 构建通过，正式 APK 关闭调试，证书与旧版一致；模拟器从 0.15.23 覆盖升级并启动成功，数据库 13→14。旧库无收件，不能据此声称真实用户数据升级已验收。源码备份：`.local-backups/classroom-summary-20261019-163102/tracked-before.zip`。最新实际证据见[验证记录](docs/validation-history.md)。

0.15.23 发布候选 debug/release 构建、两套 lint、androidTest 编译通过，日志 `build/0.15.23-publish-build.log`。集中 full 首轮 70/79，通过修复九项过期测试替身及断言后，失败项分别复跑通过，未更改已确认动效。新浙大同步恢复 17 项、作业 SQLite 更新 41 项和有效截止浏览器专项通过。APK 为 185 / 0.15.23、关闭调试，签名与既有发布证书一致。当前电脑访问学校公开入口遇到 TLS 失败／超时，未进行真实学校登录与更新；未使用手机。截止规则与学校恢复修复备份 `build/backups/20261018-zju-deadline/`。

0.15.23 最终 release、lintRelease 与 androidTest 编译通过，日志 `build/0.15.23-final-build.log`。浏览器模拟 API 交互与深浅主题截图已检查；真实 SQLite 快照 16 项、共享审核状态 45 项、批量写入 19 项、来源 API 20 项和同步 API 17 项通过。同步并发 latch、附件流式上传及失败恢复专项通过。未使用手机，未运行 Android instrumentation 或再次连接真实 WebDAV；不可将上述结果表述为真机验收。源码恢复副本位于 `build/backups/20261018-project-hardening/`。

0.15.22 release 构建、lintRelease 成功，USB 覆盖安装 184。尝试导航复查时手机随后断开，未获得修复后空状态截图；该项视觉复查仍待设备可用。构建日志 `build/0.15.22-build.log`。下列同步证据属于 0.15.21，本轮未重复执行真实同步。

0.15.21 release / androidTest 构建与 lintRelease 通过；USB 安装 183。真机通过应用已有 Keystore 配置检查 WebDAV：本地 46 个寄存器有效；服务器列表和 GET 的版本标签不符合原强 ETag 要求。修复后实际运行完整 AndroidSync，返回“上次同步：2026/10/17 18:23 · 成功”。本轮测试未显示、导出或修改凭据，未清空本地或云端，未替用户处理冲突／删除日历。

传输回归通过；新增真实 HTTP 交换 16 项通过，覆盖无强 ETag／强 ETag、重复同步、双设备、永久合并标记、拒绝未知记录及不覆盖其他设备 head；合并 99、类型与 SQLite 66 项通过。证据位于 `build/device-0.15.21/`，构建与检查日志在 `build/0.15.21-*.log`、`build/webdav-*-check.log`。

0.15.21 实际同步前已在应用私有 files/sync-recovery-<时间戳>/ 中保留数据库及 WAL、同步状态和课表文件。当时发现历史升级库快照列顺序错误；0.15.23 已改为显式列名复制，并通过真实 SQLite 升级列序专项。原失败快照未用于恢复；真实用户备份覆盖恢复仍未执行。

仍需区分以下限制：

- 全应用其余卡顿热点尚未归因，不能宣称稳定 120fps。
- 原子岛已有部分真机操作与秒级刷新证据；锁屏、胶囊、长时后台等形态必须按具体版本和场景判断，不能据一次通过泛称全部通过。
- 模型实际返回的思考文本、缓存命中和语义准确率受服务影响；离线协议检查不能替代真实调用。
- 备份合成数据检查不代表已对用户真实数据完成覆盖恢复。恢复、升级和破坏性验证须使用获准且可恢复的测试环境。

具体证据见[验证记录](docs/validation-history.md)，历史差异见[归档索引](docs/archive/README.md)。历史中的“尚未完成”或设备地址不作为当前任务清单。

## 文档维护

产品变化更新对应功能文档；通用要求进入基本要求或 UI 规范；已确认的具体交互进入交互基线。发布摘要写 `CHANGELOG.md`，实际验证写验证记录。本页只保留当前状态、未解决限制和接续入口，不再累积逐版日志。
