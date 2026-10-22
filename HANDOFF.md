# 当前工作单

## 当前交付：0.15.40 网页主从详情

- 已实现并验收方案A：桌面30/70列表/完整详情，窄屏列表/详情切换；取消横向卡片、卡内长文滚动和底部事项选择器。紧迫优先，完成后置；外部目标直接选中，返回列表目标可见。
- 原位分组编辑、Dock上方可达操作、模态遮罩关闭、四关闭路径及删除取消草稿保护、保存中锁定、失败保留与浏览器返回/刷新保护已通过。受影响源码、规范、功能文档与回归同步。
- 固定候选为202/0.15.40，APK：build/candidates/0.15.40/Chrona-0.15.40.apk（3100134字节，原签名，不可调试）。正式debug/release及双lint、67项主从专项、反馈/parity/features/motion/deadline/record-removal、DOM87与quick7/7通过，专门UI复审通过；证据见docs/validation-history.md和build/lan-detail-navigation/。
- 已发布Latest：[0.15.40](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.40)，源码18d974f与标签v0.15.40已推送，页面确认同一提交、四条更新说明及固定APK（2.96MB），截图build/release-0.15.40-published.png。
- 唯一下一步：待用户重新连接vivo，按既有安装要求覆盖更新此固定候选并检查真实LAN只读链路，不卸载或清数据。
- 手机安装受连接阻塞：当前只有emulator-5580，旧vivo 10.196.4.142:39681连接10060超时。已请求用户重连/提供新地址，恢复后继续安装及真实LAN只读检查。模拟API验收不替代真实手机数据链路。
- 0.15.39原生首页课表修复已在vivo安装，本轮不改动。不得重启此前叫停的公开下载复验、手动更新核对、付费AI或真实WebDAV。历史见验证记录。
