# 当前交付：课堂增强整理代码候选

## 范围与已实现

**0.15.67 / 229**。用户授权实施默认关闭的「增强整理」：先完整更正文稿，再分块笔记／视觉缺口判断、按原权限核图及本地组装。单节、批量、监视沿用相同入口；开关纳入保存、退出保护、旋转草稿和备份。更正文稿可在原阅读器折叠查看并随原ZIP导出。

两种模式均冻结选项，保留已发送system、user、assistant及图片原位置，后续仅追加，不重构资料包、不摘要或裁剪历史。每次实际响应、usage、阶段及错误保存在run.json；未返回usage不声称缓存命中。增强模式每块一次格式修复，来源／图片校验失败需手动授权后追加校正；未知网络结果不自动重发。新增分段与检查点，强化改卷线索，公共段落修复保护公式及代码。业务合同见`docs/features/zju/classroom-summary.md`。

## 当前证据

- 离线回环初组277项，超长字幕／复用历史图片75项，非法引用／手动续跑116项，Markdown围栏／公式28项分别通过；原请求与合成响应在`build/classroom-enhanced-check/fixtures/`。默认CI已包含这些分组。
- 默认管线受影响图片／续跑39项、批量队列15项、偏好／备份含35项及reader.js语法检查通过；早期失败及修复范围见`docs/validation-history.md`，没有伪报整轮通过。
- 持续复用的只读ui_review审查源码；两项围栏保护P2已修复并复审闭合。尚无本候选实际UI渲染证据。
- 固定229源码的debug／AndroidTest编译、release与lintRelease通过，见`build/classroom-enhanced/candidate-229-build.txt`。安装包`build/candidates/0.15.67/Chrona-0.15.67.apk`为3274457字节，229／0.15.67、签名验证通过且与228一致；元数据及签名证据在同目录前缀`candidate-229-`。所有衍生输出留在项目内。

## 用户决定与唯一下一步

用户最新要求：**今晚只完成代码，明天上机测试**。今晚不再使用ADB、不安装或运行手机／模拟器测试、不调用真实AI、不发Release。源码同步不等于真机验收；手机最后确认仍为**0.15.66 / 228**，公共latest仍v0.15.61。

下一步在明天获得手机使用窗口后，安装固定229候选，在普通屏幕完成增强开关保存／取消／放弃／返回／旋转及深浅竖横检查，再按真实课程授权验证更正文稿、分段核图、失败续跑与服务usage。保留此前消息前缀不变的离线证据，不重复无变化的完整矩阵。只有真实服务返回缓存字段才记录命中。

## 独立依赖与边界

- 上一批网页SVG／跟随手机及浏览器独立主题已在228真机资源加载验证，证据`build/lan-theme/`和验证记录；无需重复修订成熟布局。
- GlobalSend对端为「设计跨设备传输与同步系统」`01a16e6f-f1c5-7081-adc4-1792a0a5d7db`，用户已授权协调。ADB／共享AVD已让出。完整手机→真实Windows联动仍需用户首次显式关联及在线目标，原Binder／Android接收证据保留；不代用户授权，不改对端组、凭据或软件。ABI唯一出处为对端`docs/INTEGRATION_ABI_V1.md`。
- FastCtx未提供时使用pwsh7；AVD仅复用`F:/Android/Avd/Chrona_Classroom_36`，不克隆、清空或重置。视觉图片先压缩JPEG，不读PNG原图，不调极端分辨率／DPI。GitHub CLI只用`F:/Programs/GitHubCLI/bin/gh.exe`，保留原鉴权。
- 用户的`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`不提交、不清理。
