# 当前交付：0.15.75已更新，长课堂正文与引用图片已复核

## 当前状态

- 手机`10.195.59.196:39137`，V2527A／原序列`10AG5S1SR4005X7`。最终237／0.15.75于2026-10-30 17:19:01正常覆盖，首次安装仍2026-09-26 18:54:37；固定包`build/candidates/0.15.75/Chrona-0.15.75.apk`3277629字节、非debug、原签名。Release／lintRelease通过，不发小版本Release。
- 真实C程序设计基础2026-09-23 14:15第7–8节（course87476／lesson1972361）AI已完成；本轮未调用AI、未重生成、未读生产私有缓存。原文稿、总结、40张图片许可、消息历史和检查点保留。全局增强偏好仍沿用此前保存的false。
- 空白原因已查明：旧阅读器先串行排完全页4651个公式才显示；安全计数425→1638→3201→4651及最终visual ready证明持续运行，先前等待20秒并不足以区分长排版与永久失败。237首屏公式就绪即呈现，屏外笔记每16项让出事件循环，折叠文稿展开才排版；不改变字体、图片、导出和原入场参数。
- 237真机已亲见正文、真实滚动、两张实际引用图，并点击进入共用全屏大图。`build/phone-enhanced-229/phone-237-{final-body,reading,picture,viewer}.jpg`均为先压缩JPEG，外部输入窗口区域已遮蔽。采集耗时含UI dump／截图，不宣称精确3秒。
- 临时阶段计数文件仅在应用专属files/diagnostics写入，现已精确删除；临时文件写入和逐公式桥接代码已移除，最终包不再生成它。手机公共根目录没有测试写入。

## 验收证据

- `build/enhanced-document/reader-237-final-build.txt`：正常生产release及lintRelease通过；`candidate-237-{package,signature}.txt`、`phone-237-final-install.txt`及固定APK对应同一候选。
- 同一原AVD正常横竖屏、未调DPI：原生阅读／折叠／公式／代码／滚动／单节与批量ZIP／失败恢复29项各通过；长文后台Activity覆盖与恢复、4651隐藏公式按需排版8项通过。`native-reader-237-{portrait,landscape,long-hidden}.txt`，实际JPEG及report在`reader-237-final-evidence/`。长文检查最初失败是测试把JS字符串的JSON引号当数字比较，改为数字返回后通过，未修改生产实现掩盖失败。
- 原只读`/root/ui_review`亲看真机正文／图片／共用查看页及AVD长文／横屏证据，本轮受检阅读器范围通过，无新增明确缺陷；结论不扩大到模型内容质量或服务缓存usage。
- 既有341项原模式恢复与schema检查、292项默认管线、538项同次请求前缀、GlobalSend清单动态更新14×2及协议32项不受本轮影响，继续复用；历史入口见`docs/validation-history.md`。

## 设备归还与下一步

已恢复升级前开启的局域网访问`http://10.195.59.196:8765`；`/`、`/app.js`、`/icons.js`、`/brand.svg`均200且SVG MIME正确（`phone-237-lan-http.json`）。浏览器按正常流程重新授权，不代替用户确认，不重复发送链接。

GlobalSend原所有者已先完成1.0.57/code68及真实系统图片拖入待发区检查。Chrona本轮已明确归还手机，之后不触控／安装；对方可继续自己的定向实收等剩余测试，不改Chrona／网络／LAN。Windows远程浏览器联动沿用用户亲自确认成功，不重复发送；真实iPad实收仍由用户后续验证。

真实服务usage／缓存命中未读回：原始审计保存在本机run.json，现无已批准的外部只读入口，不绕过私有缓存权限，也不声称实际缓存节省。后续如继续该项，走应用正常审计／导出能力；无需重新生成已完成课堂。

项目衍生产物留本项目build；GitHub CLI使用F盘公共安装。用户的AGENTS.md、角色toml、.backups/、.local-backups/、tools/Clean-Project.ps1不提交、不清理。
