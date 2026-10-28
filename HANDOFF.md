# 当前交付：Chrona × GlobalSend 局域网地址联动

## 当前候选与验收范围

**0.15.65 / 227**：`build/candidates/0.15.65/Chrona-0.15.65.apk`，3264338字节，原签名、非debug。普通debug／release与双lint通过，已正常覆盖共享AVD；不创建Release／标签，公共latest仍v0.15.61。手机最后确认0.15.63/225，本轮未更新真机。

- 已实现GlobalSend精确安装查询、Messenger ABI v1、本机首次显式关联、真实组设备列表、点击指定设备发送当前普通LAN地址和原请求状态查询。刷新／恢复不发送；120秒UUID意图不因迟到回复或未知结果重复打开；不传配对码、Cookie或账号凭据，保留Chrona网页授权。
- 用户最新布局已落地：授权短句为「可通过手机确认/扫码/配对码授权」，「发送到设备」整组位于其下方，设备行左右16dp内距，复用公共设置、菜单和动效。正常竖浅／横暗渲染及整页滚动通过，持续复用的只读ui_review已审核，无剩余具体UI缺陷。
- 客户端本地假Messenger协议16×2项、最新布局7／8项通过；它们只证明客户端。真实GlobalSend1.0.30/39跨应用Binder和首次关联另有4项通过。真实Android后台接收返回needs_tap，点击系统通知后返回launch_accepted；默认浏览器实际加载Chrona的手机确认页，没有自动配对。发送端为对端的隔离协议工具，不冒充完整Chrona→Windows链路。
- GlobalSend所有者交付1.0.31/40修复原生关联页安全区；AVD已覆盖其x64验证包，经正常设置进入管理页观察正常。正式ARM64包不适用于x64 AVD，未改AVD架构或复制模拟器。协议／Binder既有证据继续有效。

实现入口：`GlobalSendLink`、`LanSettingsActivity`。业务合同见`docs/features/connectivity/lan-access.md`；有效证据、复现方法及局限见`docs/validation-history.md`的0.15.65节。此前0.15.64课堂自动／批量总结候选及证据继续有效，不重复成熟管线检查。

## 唯一下一步

设备重新可达时直接将上述固定227包覆盖手机，验证真实同组Windows设备列表→点击发送→默认浏览器访问手机8765→原手机确认。当前手机`10.196.4.142:36241`连接10060超时、mDNS无设备，仅共享AVD在线；AVD NAT地址10.0.2.16不能替代手机→Windows真实可达性验收。不索要重连、不原样反复尝试，不把离线范围称为完成。

## 所有权与执行边界

- GlobalSend对端：`01a16e6f-f1c5-7081-adc4-1792a0a5d7db`「设计跨设备传输与同步系统」，项目`F:/Codes/Java/GlobalSend`。用户已授权协商／消息。本聊天拥有Chrona与本轮ADB；对端拥有身份、传输、服务端及Windows／Android接收。唯一ABI文档是其`docs/INTEGRATION_ABI_V1.md`，不复制协议。
- 根代理已回传真实接收、浏览器及40安全区证据的实际消费结果，测试中心由对端关闭。真实Windows组、登录及应用数据未改；不终止对端进程。临时reverse1507只由本聊天清除。
- FastCtx本轮未提供，使用pwsh7。共享AVD仅`F:/Android/Avd/Chrona_Classroom_36`，不克隆／清空／重置。所有测试输出在本项目`build/global-send/`，图片先缩小压缩JPEG，不查看PNG原图；不调分辨率／DPI。
- 用户修改的`AGENTS.md`、角色toml、`.backups/`、`.local-backups/`及`tools/Clean-Project.ps1`保留，不提交。GitHub CLI只使用`F:/Programs/GitHubCLI/bin/gh.exe`，不重复鉴权。
