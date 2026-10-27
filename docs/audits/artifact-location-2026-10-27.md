# 项目衍生产物归属与清理清单（2026-10-27）

## 已准备好，未执行清理

正式删除白名单：[artifact-cleanup-candidates-2026-10-27.csv](artifact-cleanup-candidates-2026-10-27.csv)。共 **105项**，每行均给出精确绝对路径、类型、字节、理由、目录链接及排除边界，供用户自行执行；不使用通配符或整个公共父目录。

- F盘公共目录：**16项，153968683字节（146.84 MiB）**。
- E盘项目内旧APK：**89个文件，221189936字节（210.94 MiB）**，单独分组，不计F盘收益。仅删列出的APK，保留同目录其余元数据。
- 旧A1–A7（含本次补查的C盘A6）目前均不存在，不再沿用旧132.1 MiB作为现有可回收量。

本次未删除、搬迁或清空任何已有文件。后续执行前须复查存在性、链接与占用，盘点快照不等于删除时可用性检查。

## F盘确认可删条目

以下目录树盘点时未发现junction/symlink/reparse point；不含当前运行数据、持久profile、签名和恢复材料。只认每行精确目标，不删除F:\Temp或F:\Android等父目录。

| 精确绝对路径 | 类型 | 字节 | 理由 |
| --- | --- | ---: | --- |
| `F:\Temp\chrona-island-drag` | 目录 | 98240390 | OEM APK/dex/smali/索引是历史灵动岛拖放静态分析材料；非安装运行目录，无当前HANDOFF路径依赖。 |
| `F:\Temp\chrona-reparse-validation` | 目录 | 36387504 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-vivo-setup-distribution-check` | 目录 | 9870261 | 0.15.42分发包展开验证夹具（含复制到夹具的adb工具）；不是公共SDK，不含签名；当前候选0.15.53不引用。 |
| `F:\Temp\chrona-classroom-workspace` | 目录 | 5393909 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-reparse-action-row-final` | 目录 | 1736142 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-reparse-short-copy-final` | 目录 | 1418478 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-no-accessibility` | 目录 | 244139 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-homework-filter` | 目录 | 131078 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-timetable` | 目录 | 105578 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-split` | 目录 | 47685 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-cap-close-now.jpg` | 文件 | 71343 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-cap-open-now.jpg` | 文件 | 69212 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-capsule-before.jpg` | 文件 | 93526 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-capsule-expanded.jpg` | 文件 | 72429 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-capsule-home.jpg` | 文件 | 33300 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |
| `F:\Temp\chrona-capsule-open.jpg` | 文件 | 53709 | 文件清单为历史渲染/验证/诊断产物；当前工作不依赖，无运行配置。 |

## E盘旧APK与保留版本

白名单的`E-old-apk`组逐个列出89个旧APK文件；不删除整个版本目录，不触及ZIP、工具脚本、元数据、密钥或备份。版本0.15.51、0.15.52、0.15.53的固定候选全部保留；手机已实测215／0.15.53，下一候选仍在开发。0.15.51／0.15.52使用旧数据库版本，保留它们用于历史与匹配备份，不承诺可直接覆盖降级；当前数据库版本18的恢复基线为0.15.53同源候选。

旧分发包中的18个`vivo-setup`辅助APK副本（1315334字节）已从删除白名单排除，逐项位于[待确认工具副本清单](artifact-cleanup-pending-tools-2026-10-27.csv)，避免将工具副本按主应用版本淘汰规则处理。

## 保留及待确认项

| 精确路径 | 字节 | 决定 |
| --- | ---: | --- |
| `F:\Temp\chrona-monitor-review` | 1503776 | 保留：现有验证记录直接引用的已签收UI证据，需先明确历史证据保留决定 |
| `F:\Temp\chrona-classroom-polish-input` | 391578 | 待确认，保留：三张JPEG尚未确认原始输入来源及项目内副本，未查看图片 |
| `F:\Android\ChronaBuildCheck\github-cli` | 42876281 | 保留：旧共享工具副本，不归Chrona构建垃圾；当前统一使用`F:\Programs\GitHubCLI\bin\gh.exe` |
| `F:\Android\user-home\emulator-logs` | 60799 | 保留：可能混有其它任务的共享日志 |
| `F:\Android\temp\AndroidEmulator` | 5019229 | 保留：共享AVD诊断，不能按模拟器名称认领全部文件 |
| `E:\Programs\拾时 · Chrona\build\lan-web-checks\browser-profile` | 58474641 | 保留：持久浏览器身份 |
| `E:\Programs\拾时 · Chrona\build\ai-checks\json.jar` | 82710 | 保留：实际检查依赖 |
| `E:\Programs\拾时 · Chrona\build\backups` | 89536308 | 保留：恢复材料 |
| `E:\Programs\拾时 · Chrona\.backups` | 742017 | 保留：用户已有恢复材料 |
| `E:\Programs\拾时 · Chrona\.local-backups` | 79905359 | 保留：用户已有恢复材料 |

公共SDK、Gradle/cache、统一`Chrona_Classroom_36` AVD/userdata、签名与登录状态保持原位；其它项目、原始输入和手机真实资料不纳入清理。目录及文件元数据详表在`build/artifact-path-repair-inventory.{csv,json}`及`build/artifact-path-repair-files.csv`。

## 已修正继续外写的入口

- `tools/Start-AndroidEmulator.ps1`：未来启动日志改写本项目`build/emulator-logs`，SDK、AVD与共享用户目录不搬；开发指南同步更新。未为验证启动或重置模拟器。
- `checks/recurrence_dependencies.py`：共享Java/javac入口默认使用项目`build/tmp`，保留调用者已有显式临时路径。
- `academic_terms_check.py`、`candidate_merge_check.py`、`classroom_images_check.py`、`lan_attachment_upload_check.py`、`sync_core_check.py`、`sync_lock_check.py`：编译器`-J-Djava.io.tmpdir`与运行时`-Djava.io.tmpdir`均指向各检查输出目录的`tmp`，不是仅改变cwd。
- Python `tempfile`的现有调用已显式使用项目`build`父目录，未改全局TEMP/TMP或其它项目环境。

有效证据：`build/artifact-path-repair-validation.json`。21条实际派发命令参数检查通过；7个临时根均用真实javac读取属性、真实JVM创建临时目录和文件验证实际落点。PowerShell语法、Python AST及diff检查通过。主代理新增图片引用定向测试亦经此共享Java入口通过33项，未额外重跑不相关业务。

直接手动调用仍需显式传临时路径：`ModelOutputCheck.java`没有受控runner或可更新执行命令，保留原入口，不凭空新增必填参数。开发指南已说明`java -Djava.io.tmpdir=...`／`javac -J-Djava.io.tmpdir=...`；绕过项目runner的任意手动调用不在自动强制范围内。

## 汇总与执行边界

本清单是用户手动清理的输入，未运行删除脚本。盘点时没有发现所列目录树链接；没有进行独占打开/占用锁检查，执行脚本必须重新检查，遇到新链接、文件占用或路径身份变化应停止。现有`Manage-ExternalArtifacts.ps1`只对应已不存在的旧A1–A7，不用于本次新白名单。
