# 当前工作单

## 当前交付：安装包代码裁剪试验已完成

- 用户同意试用未使用代码裁剪。相同197／0.15.35源码与签名，保留类名、关闭代码优化、未启用资源裁剪；APK从3087526降至1940720字节，减少1146806字节／37.14%。DEX压缩占用2142181→995460字节，全部assets清单与内容逐字节相同。
- 试验包 `build/r8-size-trial/Chrona-0.15.35-shrink-only.apk`；体积报告 `build/r8-size-trial/result.json`，签名／清单和裁剪清单同目录。构建入口 `build/r8-size-trial.init.gradle` 与 `build/r8-size-trial.pro`，原始配置副本 `.local-backups/r8-size-trial-20261021/`。
- 真正裁剪后的Release在独立v33模拟器包通过8项运行检查：ICS时区／重复／排除日期／时长、二维码、OkHttp TLS初始化、Activity启动、离线MathJax SVG及JavascriptInterface回调；`build/r8-size-trial-runtime.log`。仅覆盖这些路径，未运行完整功能矩阵或实际外部网络请求，不能作为完整发布验收。
- 结果支持继续采用仅代码裁剪方案；当前实验未改变 `app/build.gradle`，未发布或替换正式资产。下一次若接入正式构建，应保留此对照并检查biweekly动态构造／常量枚举等反射入口；无需重跑无关UI设计验收。

## 状态与边界

- 正常Release输出已通过原构建入口恢复至3087526字节；独立v33包恢复试验前APK及正常测试runner，不清数据。正式模拟器与实体手机均未覆盖。
- 0.15.35已发布为Latest，源码标签 `v0.15.35`／`a17b88d`，用户版说明已上线。原包 `build/candidates/0.15.35/Chrona-0.15.35.apk`；历史证据见验证记录。
- 不恢复上一轮用户叫停的公开下载与升级核对。用户AGENTS、角色文件及其他未跟踪文件保持原样；不操作真实账号、日历或现有LAN服务。
