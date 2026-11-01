# 当前交付：更正文稿原生展开已交付；数学重总结等待服务恢复

## 当前候选与已验结果

- 0.15.88/250 已于2026-11-01 15:19正常覆盖安装 V2527A，ADB包管理器确认版本和更新时间。APK：build/candidates/0.15.88/Chrona-0.15.88.apk；com.donglan.chrona、原升级证书、非debuggable。未发补丁Release，正式latest仍v0.15.81。
- 更正文稿移除WebView默认details/summary三角，复用日程详情既有紧凑按钮与高度/透明度开合；公共实现提升至UiStyle/UiMotion，TaskDetail私有重复删除、所有调用迁移。懒创建同款Markdown阅读器，后续开合复用；旋转保存开合，源文稿/代码/公式/来源编号/导出不变。
- debug/androidTest、release+lintRelease通过；公共职责1530、section生产控制器16、reader实际解析49通过。共享AVD常规竖浅/横深各41；font1.3长文4651公式、首屏优先、真实减少动画开合18通过，系统倍率恢复。长文逐帧终点稳定，独立UI最终审核无未解决P1/P2或关键证据缺口。
- 证据：build/correction-disclosure-ui/；首轮减少动画夹具固定200ms断言失败及JPEG保留为background-first*，后来补等真实点击送达后按原终态契约通过，未修改生产来迎合测试。全程图片先压缩JPEG，没有改DPI/分辨率。

## 数学19项仍未完成

- 仅微积分16、线性代数3；其它课程不重跑。最终须实际验证19项最新格式、有效来源与配图依据；另两项原“已完成”记录仍需核对新契约，不当作本轮成功。
- 最新格式是一篇精简完整笔记，affairs集中事务，assignments只明确行动；分段仅内部处理。复用已提交且原id/text与分块匹配的修正文稿，缺图走原下载/校验/去重。旧run精确留在本课审计，不另导出旧总结副本。
- 原19项首次选择见build/classroom-failure-20261101/math-selection.json。17项重跑曾5项JSON结构失败、1项HTTP413后有序停止：成功0/失败6/取消11，素材与文稿保留，无活动AI队列。详情见stopped-results.json及docs/validation-history.md。
- 86真机读到微积分10月29日第9节、线代10月27日第9节：顶层是字符串而非对象，finish_reason=stop；1–2节无图修复请求0.13MiB返回413。不归因于截断、图片或臆测服务阈值。
- 87/88新JSON初始指令明确对象及转义；repair/manual/network恢复仅引用原user的完整schema与资料，不再重复大prompt。原消息、模型选项、缓存前缀只追加不重构；每阶段唯一结构修复、引用检查、取消与原子回滚保持。
- 普通292、增强JSON719、独立只读检查通过；实际loopback请求102988→54596字节。Android真实JSON根类型/可选图片安全投影12项通过。这些不代替真实服务服从格式的验证。

## 唯一下一步与阻塞

- 用户要求测试服务后，一次237字节无课堂资料健康请求在1036ms也返回HTTP403（类别未明确）；87单课真实首请求同样403、无正文。证据build/classroom-regeneration-validation/phone-ai-health.txt；诊断辅助包com.donglan.chrona.classroom.probe已卸载。
- 暂停重复付费重跑，不更换密钥、模型、身份或网络绕过。服务恢复后先续单课，真实通过再扩至其余失败数学课堂，并核对两项旧完整记录。不能宣称数学总结已完成或服务故障已修复。

## 设备与文件边界

- GlobalSend已完成92版并明确交还真机；当前无已知同机控制冲突。仅实际冲突时协调，不常规跨聊天广播。所有设备操作归root。
- 统一共享AVD Chrona_Classroom_36 / emulator-5554，不克隆、清数据、改密度或中止其它任务；测试产物在项目build，设备只用本任务目录。
- 本次手机/data/local/tmp/chrona/classroom-failure-20261101/window.xml及空任务目录已清理；正式包已正常启动，不删真实收件或应用数据。
- 旧/data/local/tmp/chrona/classroom-failure-20261031/曾清理被策略拒绝，保持原记录、不绕过。
- 原生JSON12项报告在隔离包external files的classroom-json-projection-20261101/report.json；run-as读取被拒绝，保留准确路径待正常应用清理，不换身份绕过。旧fixture/failure.jpg不删。
- 新UI三个原生输出目录已完整转入build/correction-disclosure-ui；本次生成的JPEG、ZIP、报告及启动日志已按精确名单清理，前置fixture及旧failure.jpg保留。后台长文任务目录已清空删除。错误命名的JSON读取拒绝记录已改为read-denied.txt，不当作有效JSON报告。

## 非本批与讨论项

- 监视自动总结全局/课程三态仍在讨论，拟放标题下信息行右侧、后续课程左侧，未确认布局，不擅自实施。
- 不改Dock。外部core接口见docs/Communication/to-external-agent.md。
- 保护未属本批的AGENTS.md、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml及tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1，不提交或清理。
