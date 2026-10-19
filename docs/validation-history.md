# 验证记录

当前版本为 **0.15.24 / 186**。下面记录该版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.24 — 重复日程与网页一致性 — 2026-10-19

- 既有 full 81 项首轮通过 63 项，18 项因测试替身缺少新增字段/依赖而失败；修复测试替身后分组复跑通过。日志 `build/recurrence-offline-full.log`、`build/recurrence-fixture-rerun*.log`。新增检查已纳入统一入口，共 84 项；未重复执行整轮。
- 重复 AI 67 项、既有归一化 60 项、实例投影 33 项、Calendar 字段 20 项通过。投影检查发现并修复夏令时持续时间偏移，使用固定持续时长与系统日历 DURATION 对齐。去重键新增重复语义后合并组复跑通过，日志 `build/recurrence-fixture-merge-final.log`。
- 网页模拟 API 浏览器检查覆盖排序、日期/类型联动、跨页全选删除、单次身份与重复操作、390px 布局；截图位于 `build/web-parity-checks/`。随后内置浏览器经 ADB 转发连接真机隔离验证服务，实际验证 16 条记录倒序、每日系列五次展开、首个实例从 09:00 改为 11:30，以及停止后详情显示「已停止」。停止后的月份空列表在手机断连后转到 Android 36 模拟器补验通过，显示「本月没有安排」，停止操作不再显示。
- 独立验证应用 `com.donglan.chrona.validation` 的 debug/androidTest 构建及 lintDebug 通过，日志 `build/recurrence-validation-build.log`。vivo 真机 `RepeatDeviceCheck` 22 项通过：真实 Provider 与本地展开一致、跳过、双向跨日期改期、查询/小组件投影、原生按钮及确认框停止、历史保留和旧基线拒绝。报告 `build/recurrence-device/provider-ui-report.txt`；已查看系列与停止状态截图，内容及控件未见裁切。
- `LanAccessInstrumentation` 真实 Android SQLite/HTTP 检查通过，新增覆盖真实排序、创建重复、旧 revision 拒绝、实例查询、跳过、改期身份、停止和批量删除。浏览器联动单独使用验证包 18765 端口；正式版原有 8765 服务保持运行。浏览器夹具限时 10 分钟并在退出时删除自建收件；无线调试断连后的清理尚未现场复核。

- 正式 Debug/Release、两套 lint 与 androidTest 构建通过，日志 `build/0.15.24-release-build.log`。APK 包名 `com.donglan.chrona`、186 / 0.15.24，无 debuggable 标志；新 debug/release 及旧版签名证书一致。
- 模拟器从 0.15.23 覆盖升级到新 debug，再覆盖 release 并启动成功；数据库 13→14，旧列逐项比较通过。旧库收件与候选均为 0，此检查不代表真实用户非空库升级。恢复副本 `build/upgrade-0.15.24/before.tar`。浏览器模拟器夹具退出后实际收件数为 0，临时转发已移除；模拟器保持开启。

## 0.15.24 — 课堂总结 — 2026-10-19

- 既有离线 `full` 79/79 通过，日志 `build/classroom-full-regression.log`；课堂两项专项已加入统一入口。随后共享样式改动涉及的 Dock、亚克力、设置布局、弹窗、小组件偏好、周历及学校 UI 七项专项全部通过，未重复无关检查。
- 修复待导出 URI 恢复抢占本地目录/历史初始化；旋转复用运行状态，待导出操作只消费一次。生成确认框改用公共可滚动正文并适配键盘 resize。
- 按当前轻圆角要求统一普通按钮/输入框 8dp、卡片和小组件 10dp、弹窗 12dp、标签 6dp；选择栏保留独立 28dp 底板与原动态高光几何。卡片阴影不再依赖圆角数值判定。修改前副本位于 `.local-backups/classroom-summary-20261019-163102/ui-refinement/`。
- 上述修复后的 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 再次通过，日志 `build/classroom-ui-refinement-build.log`。真实窄屏、大字体、键盘及恢复交互仍待原生环境验收。
- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过，日志 `build/classroom-native-build-final.log`。首轮发现导出 URI 授权参数的 lint 错误，修复后通过。
- `checks/classroom_summary_check.py`：既有 AI 请求检查及课堂总结 77 项断言通过。覆盖真实循环回送 HTTP、SSE、取消与超时、分段续跑、引用校验、图片预算、账号隔离和离线 Markdown ZIP。Android 位图和原子文件使用 JVM 替身，尚不能代表 Android 运行结果。
- `checks/classroom_client_check.py`：42 项检查通过。生产 Java 客户端另成功读取 179 节课堂、49 节就绪；选中课堂返回 1158 条字幕、130 个截图地址，并成功下载一张 101988 字节的图片。验证仅进行只读访问，日志不保存登录会话或课程正文。
- 发现并修复：连续尾图突破每段 4 张；部分总结误将尚未完成分析的计划图片写入已分析清单。修复后仅导出已完成分段的图片，另保留计划图片数。
- Android 36 模拟器已在重启后使用 WHPX；图形由 SwiftShader 切换为 RTX 5060，SurfaceFlinger 确认 NVIDIA 渲染器。镜像、AVD、日志与启动脚本均在 F 盘。未操作手机。
- 原生课堂检查深浅主题各 11 项通过，覆盖合成课堂 50→72 分页、历史打开、Android 位图去重、AtomicFile/ZIP、账号停用退出与生成弹窗。320dp、1.5 倍字体的截图暴露日期与时间断行、导航字号不一致，修复后复拍确认。键盘可见状态经 WindowInsets 验证，滚动后取消操作完整可见。测试数据未调用外部 AI。
- 既有公共组件 `interaction_layout` 27 组原生布局通过，覆盖 320/360/640dp、1/1.5/2 倍字体、首尾长按/拖出/快速甩动；`interaction_acceptance` 日历初开、未缓存切换、缓存返回、长按拖出与快速反向通过。该结果不代表实体手机帧率。
- 证据位于 `build/classroom-native/`：`light-final-report.txt`、`keyboard-verified-report.txt`、`week-fixed.png`、`dialog-keyboard-verified.png`、`interaction-layout.txt`、`interaction-acceptance.txt`。窄屏修复后的 debug/androidTest/lint 通过，日志 `narrow-fix-build.log`。尚未正式发布。

## 0.15.23 — 2026-10-18

- 发布候选 debug/release、lintDebug/lintRelease 与 androidTest 编译通过，日志 `build/0.15.23-publish-build.log`。APK 185 / 0.15.23，无 debuggable 标记，debug/release 签名符合原证书要求。
- 集中 full 首轮 70/79；九个旧检查的替身接口、提取签名或旧分类断言未同步，修复后仅重跑失败项，全部通过。未为测试改变生产动效。
- 有效截止按 due/end/start 的模型、实际 SQLite 筛选/提醒、浏览器首页/列表/详情及编辑保留原始 due 检查通过；浏览器专项 `checks/deadline_consumers_check.cjs`。
- 浙大生产客户端的拦截 HTTP 与隔离密钥 fixture 17 项、真实 SQLite 作业更新和绑定隔离 41 项通过。学校公开入口从当前电脑访问出现 TLS 失败／超时，未执行真实登录、学校同步或 Android Keystore 验证；无真机覆盖升级验收。

- 网页补齐续轮：年历闰日、跨年导航、月日互跳、截断提示、周密度和 320/390/600px 布局专项通过；实际检查桌面浅色与手机深色年历截图，见 `build/lan-calendar-checks/`。日历原有 parity 检查通过。
- 新增自动同步开关与冲突字段展示后，同步 API 检查增至 67 项。浏览器新增开关失败回退、附件 409 后显式重试、纯附件占位原文、记录候选删除及托管收件操作限制检查通过。首次删除测试误选背景同名按钮，改为浮层内定位后通过；未改动用户记录。

- 最终 `assembleRelease`、`lintRelease`、`assembleDebugAndroidTest` 通过，日志 `build/0.15.23-final-build.log`。未安装手机或执行 instrumentation。
- Edge 浏览器核心、功能对齐及新增功能检查通过，使用模拟手机 API；覆盖日／周／月与手机自然日、筛选和批量写入、来源分页、附件失败重试、冲突版本预览与明确删除确认。深浅主题、移动宽度实际截图已检查；5 种主色在深浅主题的主按钮对比均至少 4.5。
- 真实 SQLite 快照 16 项、类型与持久化 66 项、共享审核状态 45 项、合并 99 项、批量写入 19 项通过；真实 HTTP 同步交换 16 项通过。
- 新生产适配器检查通过：来源 API 20 项、同步 API 17 项、附件限长流式上传及回滚；同步锁 3 个 latch 场景覆盖慢网络期间本地访问、编辑保留和恢复使旧结果失效。适配器检查不代替真实 Android Provider 或 WebDAV 多机验证。
- 截图与对比数据：`build/lan-feature-checks/`；浏览器核心与日历证据：`build/lan-web-checks/`、`build/lan-parity-checks/`。源码恢复副本：`build/backups/20261018-project-hardening/`。

## 0.15.22 — 2026-10-17

- 同步空状态沿用 14sp 文本，切为主题次要色；左右 16dp、上 12dp、下 16dp 的真实内容留白，填满卡片内容宽度。不改变按钮、同步逻辑或其他状态。
- release 构建与 lintRelease 通过，USB 覆盖安装 184 成功。导航复查未到达目标页，后续设备断开；尚无修复后实际截图，不标为原生视觉通过。没有再次执行真实同步、改变账号或处理冲突。
- 构建日志 `build/0.15.22-build.log`，源码备份 `build/backups/20261017-sync-empty-state/`。旧库标准备份快照问题仍保留在交接页，未在本次 UI 修复中处理。
