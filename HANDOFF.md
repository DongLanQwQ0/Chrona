# 当前交付：0.15.69已更新手机，保留真实AI续跑检查点

## 候选与范围

新增交付：iPad普通链接投递。用户在GlobalSend明确授权双方协调；对端已确认ABI v1可选扩展send_link、sendLinkV1与status.deliveryMode=link。Chrona负责设备能力分流、原ID手动重试与状态恢复，GlobalSend负责本机桥及普通文本投递。已有Windows/Android自动打开不变，不因失败自动改投链接。sent仅表示中心接受普通链接。现有UI结构不变；复用同一只读ui_review验收。

本次新增约束已写入`docs/project-requirements.md`：手机主目录及公共目录禁写测试文件，必要临时项用专属目录并finally清理。已清理两条确切自有层级XML；公共目录只见正常用户导出，未删除。真实失败入口复验已完成。

源码与USB手机均为 **0.15.69 / 231**。本批修复更正文稿折叠热区与焦点、草稿空概览／空安排、错误的图片阶段文案，以及更正失败但尚无笔记时的「继续整理」入口。单节／批量共享导出逻辑。默认关闭的增强整理及原消息历史仅追加、真实检查点合同不变。详见`docs/features/zju/classroom-summary.md`。

## 有效证据

- 231隔离原生应用在既有共享AVD按正常竖浅／横暗各27项通过，实际WebView触摸展开／收起、公式和代码、文末、ZIP及两次失败重进；最后入口标签定向8项通过。主代理与持续复用只读ui_review亲看压缩JPEG并签收。无学校／AI调用，未改分辨率、DPI或字体。
- 单节／批量ZIP四个条目逐项内容相同。证据`build/enhanced-document/`内`native-{light,dark}-accepted.txt`、`native-recovery-accepted.txt`、`accepted-evidence.json`、`review-231/`最终两档及`enhanced-document-portrait-light-failure-recovery/`。早期夹具／窗口过渡失败保留，不作通过证据；详见`docs/validation-history.md`。
- 原增强管线277／75／116／28项、默认图片39项、队列15项、偏好35项、230失败格式化49项与手机设置14项证据继续适用，未重跑无变化路径。
- 最终231正常生产debug／AndroidTest编译／release／lintRelease通过，日志`build/enhanced-document/release-231-final-build.txt`。固定包`build/candidates/0.15.69/Chrona-0.15.69.apk`3274905字节，231／0.15.69、非debug，签名验证且与230一致；元数据与签名见`candidate-231-{package,signature}.txt`及`candidate-231.json`。用户恢复USB后正常覆盖成功，手机读回231／0.15.69，原首次安装时间保留。

## 真实AI结果与缺口

用户授权的 **C程序设计基础2026-09-23 14:15第7-8节，course87476／lesson1972361**：923条字幕、282→140截图，沿用原图片授权最多40。gpt-6-luna完成2/5更正文稿后HTTP502；用户再授权一次续跑，实际从2/5恢复，同阶段再次502。停止原样付费重试，原检查点保留。真实完整文稿／核图／最终笔记及缓存usage尚未验收，平台合成检查不替代这些结果。

用户确认「服务已恢复，允许续跑」后，真实从2/5继续，越过旧502点并完成更正文稿5/5，ClassroomWorkService2043运行证据完整。进入首段笔记／核图计划后因AI缺少image_requests停止，未重复付费请求；原文稿和检查点保留，实际阅读页可见默认折叠「更正文稿」。证据`build/enhanced-document/phone-ai-{resumed-after-service-recovery,recovery-status-2,recovery-status-3,recovery-status-4}.txt`及`build/phone-enhanced-229/phone-ai-corrected-draft-settled.jpg`。没有形成最终笔记／真实核图结果，真实usage仍未取证。

定位到结构缺字段被IOException当成语义错误，跳过有限格式修复。232源码已改为必需字段／类型JSON错误走唯一修复，事实／来源／权限错误仍手动授权；重试和修复重新附本块字段要求，原消息前缀不改。唯一修复预算按已持久化repair阶段识别，先前语义失败没有消耗预算，已有网络中断的repair可在显式授权后继续原请求。`checks/classroom_enhanced_check.py --case schema-repair` 254项通过（含既有语义／手动预算）；初次编译缺JSONException导入已补，失败日志不作通过证据。

手机增强开关已保存恢复原false，重新打开AI设置读回false；主题跟随系统／旋转未改。长表单返回按钮滚出视口时未盲点，使用系统返回离开。GlobalSend已释放其25秒安装窗口，现手机GlobalSend为1.0.55/66，Windows也已更新；Chrona仍231。最后只读服务清单没有课堂或LAN服务，未擅自重启LAN或再次调用AI。232正式构建和安装尚未完成，不得拿隔离包覆盖手机。

232普通链接分支现已在共享AVD真实Messenger＋触摸下正常竖浅／横暗各30项通过：原open、iPad能力分流、sent语义、原ID手动重试、重建查询及不自动降级。`build/global-send-link/native-{portrait-light,landscape-dark}-accepted.txt`及对应目录。后来对端确认link failed也是终态，源码及两项专项已添加，尚需补验。早期两次LAN启动失败因AVD AndroidWifi未连接；连接其原有开放虚拟网络后通过，无克隆／清空，现AndroidWifi已连接，原wifi开启状态不变。待完成专项、UI独立审核、正常生产包构建后再交付。

## 当前依赖与唯一下一步

2026-10-30已与GlobalSend线程`01a16e6f-f1c5-7081-adc4-1792a0a5d7db`协调，12:39正常覆盖231成功，安装前无课堂或LAN服务。初验遇拖放／聊天切前台已停止触控；无关聊天误采文件已清理。用户明确暂停手机操作后，从正常首页进入历史／未总结，真实9/23课堂两次重进均显示启用的「继续整理」，提示及操作完整可见。未点击续跑、不调用AI，恢复原总结时间筛选并返回首页后已释放USB。证据`build/phone-enhanced-229/phone-231-real-failure{,-reopen}.{xml,jpg}`，主代理与同一ui_review亲看签收；旧229执行记录当时没有具体detail，仍是通用失败提示，不声称恢复了旧HTTP原因。下一步在服务恢复或有新依据且用户授权后续跑原课堂。不发Release，公共latest仍v0.15.61。

Chrona→真实Windows的GlobalSend联动已由用户实测验收：2026-10-30用户明确确认「可以正常工作，可以直接唤起远程浏览器」。本轮手机连接Wi-Fi后，真实设置页显示当前访问地址及DongLan／Windows，现安装身份已有关联，无需重做批准；脱敏证据`build/phone-enhanced-229/phone-231-lan-started.{xml,jpg}`。不重复发送测试链接，不代点网页授权；保留当前用户正在使用的LAN会话。已通知GlobalSend所有者关闭该验收依赖并释放USB。此项不再阻塞交付，ABI唯一出处仍为对端`docs/INTEGRATION_ABI_V1.md`。

## 操作约束

FastCtx未提供时使用pwsh7；产物只放本项目build。AVD仅复用`F:/Android/Avd/Chrona_Classroom_36`，先协调，不克隆／清空／重置。视觉先压缩JPEG，不读PNG原图，不调极端显示配置。不读凭据／生产私有缓存，不绕过安装拒绝。GitHub CLI只用`F:/Programs/GitHubCLI/bin/gh.exe`。

用户的`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`不提交、不清理。
