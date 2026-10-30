# 当前交付：0.15.69已更新手机，保留真实AI续跑检查点

## 候选与范围

源码与USB手机均为 **0.15.69 / 231**。本批修复更正文稿折叠热区与焦点、草稿空概览／空安排、错误的图片阶段文案，以及更正失败但尚无笔记时的「继续整理」入口。单节／批量共享导出逻辑。默认关闭的增强整理及原消息历史仅追加、真实检查点合同不变。详见`docs/features/zju/classroom-summary.md`。

## 有效证据

- 231隔离原生应用在既有共享AVD按正常竖浅／横暗各27项通过，实际WebView触摸展开／收起、公式和代码、文末、ZIP及两次失败重进；最后入口标签定向8项通过。主代理与持续复用只读ui_review亲看压缩JPEG并签收。无学校／AI调用，未改分辨率、DPI或字体。
- 单节／批量ZIP四个条目逐项内容相同。证据`build/enhanced-document/`内`native-{light,dark}-accepted.txt`、`native-recovery-accepted.txt`、`accepted-evidence.json`、`review-231/`最终两档及`enhanced-document-portrait-light-failure-recovery/`。早期夹具／窗口过渡失败保留，不作通过证据；详见`docs/validation-history.md`。
- 原增强管线277／75／116／28项、默认图片39项、队列15项、偏好35项、230失败格式化49项与手机设置14项证据继续适用，未重跑无变化路径。
- 最终231正常生产debug／AndroidTest编译／release／lintRelease通过，日志`build/enhanced-document/release-231-final-build.txt`。固定包`build/candidates/0.15.69/Chrona-0.15.69.apk`3274905字节，231／0.15.69、非debug，签名验证且与230一致；元数据与签名见`candidate-231-{package,signature}.txt`及`candidate-231.json`。用户恢复USB后正常覆盖成功，手机读回231／0.15.69，原首次安装时间保留。

## 真实AI结果与缺口

用户授权的 **C程序设计基础2026-09-23 14:15第7-8节，course87476／lesson1972361**：923条字幕、282→140截图，沿用原图片授权最多40。gpt-6-luna完成2/5更正文稿后HTTP502；用户再授权一次续跑，实际从2/5恢复，同阶段再次502。停止原样付费重试，原检查点保留。真实完整文稿／核图／最终笔记及缓存usage尚未验收，平台合成检查不替代这些结果。

继续原轮次需服务恢复或新依据及用户再授权，保持增强开启、原模型／提示词／图片选项以复用历史。手机原增强关闭、主题跟随系统、旋转1／0此前已恢复。

## 当前依赖与唯一下一步

2026-10-30用户再次要求「继续usb」，USB上线；GlobalSend线程`01a16e6f-f1c5-7081-adc4-1792a0a5d7db`确认释放后，12:39正常覆盖231成功。安装前无课堂或LAN服务，未打断实时字幕。证据`build/enhanced-document/phone-231-{install,package,install-services}.txt`。打开采集页后截图显示系统图片拖放仍在进行，返回触控没有产生导航；后续只读焦点已转相册，因此立即停止触控并协调，未继续误点或强停其它应用。`build/phone-enhanced-229/phone-231-{home,dashboard}.{xml,jpg}`仅证明当时页面与拖放，不作失败入口复验成功的证据。231真实失败入口检查仍待独占手机窗口，既有原生合成证据有效。下一步在服务恢复或有新依据且用户授权后续跑原课堂，不自动产生AI用量。不发Release，公共latest仍v0.15.61。

Chrona→真实Windows的GlobalSend联动另需用户首次显式「关联GlobalSend」及在线目标，不代用户授权。原Binder／Android接收证据保留，ABI唯一出处为对端`docs/INTEGRATION_ABI_V1.md`。

## 操作约束

FastCtx未提供时使用pwsh7；产物只放本项目build。AVD仅复用`F:/Android/Avd/Chrona_Classroom_36`，先协调，不克隆／清空／重置。视觉先压缩JPEG，不读PNG原图，不调极端显示配置。不读凭据／生产私有缓存，不绕过安装拒绝。GitHub CLI只用`F:/Programs/GitHubCLI/bin/gh.exe`。

用户的`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`不提交、不清理。
