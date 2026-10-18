# 验证记录

当前版本为 **0.15.23 / 185**。下面记录该版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

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
