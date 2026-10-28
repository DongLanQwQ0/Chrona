# 当前交付：Chrona × GlobalSend 局域网地址联动（协商）

用户要求与正在工作的「设计跨设备传输与同步系统」协商：开启局域网访问后，若已安装GlobalSend，先获取设备列表，在Chrona选定设备直接发送访问地址，目标设备自动唤起浏览器。双向协商已完成、方案已相互采用；尚未开始生产实现或修改UI，不能称为已接通。

## 所有权与已核实条件

- 对端任务`01a16e6f-f1c5-7081-adc4-1792a0a5d7db`，项目`F:/Codes/Java/GlobalSend`。用户明确授权协商；对端已调查、回传并根据本聊天决定更新`F:/Codes/Java/GlobalSend/docs/CHRONA_INTEGRATION_DRAFT.md`，本聊天已完整读取并采用。这是唯一接口草案，不复制第二份协议。
- Chrona拥有LAN会话、设备选择入口及本机调用；GlobalSend任务拥有设备发现、可信身份、传输和接收端浏览器处理。互不编辑对方项目。当前没有手机租约，不打断对端构建或测试。
- `LanSettingsActivity`已有开启、居中普通链接复制、二维码及配对码；没有GlobalSend入口。`LanAccessService.url()`仅提供一个实际绑定的非VPN Wi-Fi／以太网私有IPv4地址，端口8765；Host强校验该IP:端口，网络变化关闭。不能用GlobalSend其它网卡IP拼接网页地址，也不能把传输成功当作浏览器可达。
- 首期传普通地址，不发送配对码、会话Cookie、CSRF或账号凭据；目标首次仍走手机确认。显式远端打开意图应与普通文本收件分开，复用可信设备校验，并按requestId去重。Windows／Android受系统限制无法打开时须如实返回待点击或失败，不能报告虚假已打开。

## 已对齐的契约与实施验收

- 现有GlobalSend只有分享入队、内部组设备列表和用户点击打开浏览器，不能直接复用为定向联动。需要新增Android显式绑定的版本化`IntegrationService`，逻辑方法为`hello/list_devices/send_open_url/status`。设备使用稳定deviceId，带在线与`openUrlIntentV1`能力；组内未撤销是现有信任范围，不虚构逐设备批准。
- 本期仅Android Chrona发送，包`com.donglan.chrona`，min26/target36；接收端覆盖GlobalSend Windows／Android。无Chrona Windows调用方，不新增Windows本机IPC，不承诺Web/iOS自动打开。
- 首次关联在GlobalSend批准“拾时：读取本组设备／发送所选网页”，Binder用系统UID→精确包名和当前签名识别；hello未授权只返回版本／能力与authorization_required，不泄露列表。一次性关联动作由Chrona前台显式打开GlobalSend确认页，结果返回原绑定会话；后续免重复确认，撤销／重装／签名变化需重关联。候选签名已提供，Debug证书不单独构成授权，不共享组凭据。
- 有类型的open_url与普通文本分开，120秒期限、requestId持久去重、同ID异内容conflict；状态区分queued/delivered/launch_accepted/needs_tap/failed/expired，超时未知用原ID查询不重发。Windows使用现有Shell入口，系统接受不等于加载／配对成功；Android前台ACTION_VIEW，后台无法确认时needs_tap＋点击通知。LAN关闭／网络改变不重发旧链接。
- 实施验收覆盖正确目标、其它设备及普通文本不被唤起、重复只开一次、过期／离线／撤销／后台限制状态真实，再独立验证真实浏览器访问8765和原手机确认。GlobalSend可传输不证明网页可达。UI继续复用现有规范与公共控件；此次协商未调用AI、修改日程或操作手机。

## 唯一下一步

进入实现阶段后，由GlobalSend所有者提供本机桥的实际Binder接口／关联动作与能力、定向意图和回执；Chrona所有者消费该接口接入LAN设置页。沿用同一草案，不把协议提案或模拟列表当成已实现。当前协商任务已交付，生产接入尚待启动。

## 前批可用候选

源码`a14376b`已推送，候选 **0.15.64 / 226**：`build/candidates/0.15.64/Chrona-0.15.64.apk`。课堂自动／批量总结实现、正常竖浅／横暗25×2、独立UI审核、debug／release与双lint、原签名构建完成；完整证据见`docs/validation-history.md`的0.15.64节。尚未更新真机，手机最后确认0.15.63/225；用户新给`10.196.4.142:36241`，尚未操作，先协调设备占用。既有证据继续有效，不重建候选或重跑成熟矩阵。本批不发Release／标签，公共latest仍v0.15.61。

FastCtx未提供，使用pwsh7；共享AVD仅复用`Chrona_Classroom_36`，不克隆／重置。所有输出均在本项目build，查看图片先压缩JPEG。用户修改的AGENTS.md、角色toml、.backups、.local-backups及tools/Clean-Project.ps1保留且不提交。gh仅使用F:/Programs/GitHubCLI/bin/gh.exe，保持既有鉴权。
