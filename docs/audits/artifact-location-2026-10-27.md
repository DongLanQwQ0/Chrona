# 项目衍生产物归属审计（2026-10-27）

审计为只读；未移动、删除或清空任何文件。统计未追踪目录链接，未查看PNG原图；数字是审计时快照，构建过程中会变化。占用情况由执行脚本时重新检查。

后续决定：用户已选择删除A1–A7全部指定内容；已在聊天提供不依赖原处理脚本的精确白名单PowerShell命令，由用户执行。尚无执行回执，以下清单仍是审计时快照，原保留建议不再作为这些条目的处理要求。

## 建议先移回证据，再选择删除

已确认有 **138,516,169字节（约132.1 MiB）** 的Chrona材料遗留在项目外。下列ID可交给脚本逐项选择；默认只预览，建议优先`Move`到本项目的`build/imported-external-20261027/`。

| ID | 外部绝对路径 | 文件数 | 字节 | 归属与建议 |
| --- | --- | ---: | ---: | --- |
| A1 | `F:\Android\ChronaBuildCheck\capsule-host` | 20 | 132034322 | SystemUI APK及反编译审计，验证记录明确引用；建议移回保留 |
| A2 | `F:\Android\ChronaBuildCheck\com` | 14 | 80784 | Chrona历史class派生，可移回或删除 |
| A3 | `F:\Android\ChronaHistoricalBuilds` | 198 | 4761530 | 0.5.0历史源码导出／APK／构建；建议移回，是否删除由你决定 |
| A4 | `F:\Android\temp\reparse-web-evidence` | 4 | 57376 | 重新解析验收的四张压缩图，建议移回 |
| A5 | `F:\Android\temp\settings-exit-evidence` | 10 | 1549904 | 设置退出验收PNG/JPEG，建议移回 |
| A6 | `C:\Users\DongLan\AppData\Local\Temp\chrona-stores-review.txt` | 1 | 10973 | 存储代码检索审计，可移回或删除 |
| A7 | 下列8个精确日志 | 8 | 21280 | 设置退出构建／验收日志，建议移回；脚本不使用通配符 |

A7仅包含：

```text
F:\Android\temp\settings-exit-build.log
F:\Android\temp\settings-exit-dark-320-1.5.log
F:\Android\temp\settings-exit-dark-400-1.0.log
F:\Android\temp\settings-exit-emulator-err.log
F:\Android\temp\settings-exit-emulator-out.log
F:\Android\temp\settings-exit-light-320-1.5.log
F:\Android\temp\settings-exit-light.log
F:\Android\temp\settings-exit-test-build.log
```

证据出处：`docs/validation-history.md`中的胶囊宿主、重新解析、设置退出、0.5.0历史交付记录，以及文件内的Chrona源码路径。禁止把整个`F:\Android\ChronaBuildCheck`列删，其中`github-cli`是外部工具（2文件、42876281字节）。

## 项目内目录可另行选择，不在外部清理脚本中

以下均在`E:\Programs\拾时 · Chrona\`内，归属位置符合要求。

| 目录 | 文件数 | 字节 | 建议 |
| --- | ---: | ---: | --- |
| `build` | 12871 | 1789368985 | 混有证据、安装包、恢复材料及持久浏览器状态，不能整删 |
| `app/build/intermediates` | 2985 | 100937989 | 可重建缓存，待选择 |
| `app/build/tmp` | 41 | 982886 | 可重建临时文件，待选择 |
| `app/build/generated` | 4 | 1821 | 可重建生成物，待选择 |
| `app/build/outputs` | 23 | 19117912 | 各变体APK，需与固定候选分开选择 |
| `.gradle` | 13 | 8486609 | 项目缓存，可重建，待选择 |
| `.local-backups` | 1274 | 79905359 | 恢复备份，先保留 |
| `.backups` | 15 | 742017 | 恢复备份，先保留 |

`build`内占用较大：`device-ui-audit`382020328字节、`candidate-merge-checks`174471352、`distributions`162618017、`promo-20261014`143702400、`classroom-header-evidence`136710419、`candidates`91134669、`backups`89536308、`current-classroom-ui-evidence`81887104、`lan-web-checks`58476448、`backup-restore-check`50488551、`distribution`47571058。

必须保留：源码、`.git`、工程配置；`build/ai-checks/json.jar`仍被检查入口当作依赖，`build/lan-web-checks/browser-profile`是统一持久浏览器身份。未经迁移不能把它们当普通缓存清掉。`.backups/`和`.local-backups/`目前未被gitignore覆盖，但属于用户已有未跟踪文件，本轮不改。

## 来源未确定的外部文件：默认保留，不纳入脚本

| 路径 | 文件数／字节 | 原因 |
| --- | --- | --- |
| `F:\Android\user-home\emulator-logs` | 9／60799 | 本项目脚本会写此处，但共享模拟器日志可能属于其它任务 |
| `F:\Android\temp\AndroidEmulator` | 4／5019229 | 共享模拟器bugreport／崩溃状态，不按AVD名称认定项目垃圾 |
| `C:\Users\DongLan\Downloads\Chrona-demo-compatible.mp4` | 1／8164826 | 与项目视频同名同大小；用途待你确认，未计算hash |
| `C:\Users\DongLan\Downloads\Chrona-2026-10-12.png`及` (1)`、` (2)` | 3／133502 | 可能是原始输入，不查看原图、不清理 |

本聊天附加可视化工作区`C:\Users\DongLan\.codex\visualizations\2026\10\23\01a16705-4388-7f81-93e7-4064b46f727b`存在但为空。旧`F:\Android\GitHubCLI`已不存在。

## 继续产生外部文件的入口，待你选择后整改

1. `tools/Start-AndroidEmulator.ps1:71–76`将日志放入公共`ANDROID_USER_HOME/emulator-logs`；`docs/development.md:62`也记录该路径。建议改为项目`build/emulator-logs`，AVD／SDK仍外置。
2. Java临时目录未指定项目父目录：`ClassroomImagesCheck.java:27`、`CourseAgendaCheck.java:26,81`、`ModelOutputCheck.java:11`、`SyncCoreCheck.java:81`、`SyncExchangeCheck.java:163`、`TimetableLibraryCheck.java:61`；`lan_attachment_upload_check.py:111`、`sync_lock_check.py:108`生成的Java夹具亦有此问题。`CandidateMergeCheck.java:40`直接依赖`java.io.tmpdir`。
3. 对应runner未固定`-Djava.io.tmpdir`：`classroom_images_check.py`、`academic_terms_check.py`、`sync_core_check.py`、`candidate_merge_check.py`、`lan_attachment_upload_check.py`、`sync_lock_check.py`。仅设置cwd不改变JVM临时目录。建议统一到项目`build/tmp`，不搬公共运行库或Gradle缓存。

本轮新增检查已使用项目内临时目录；以上历史入口只列问题，未借审计擅自重构。

## 公共组件保留在外部

`F:\Programs\GitHubCLI\bin\gh.exe`（现用且认证正常）、`F:\Android\Sdk`、`Avd`、`Gradle`、`GradleCache`、`Jdk`、`npm-cache`及公共用户目录保留。`F:\Android\Downloads`中的NDK／SDK安装材料约3.77GB，为工具链而非项目垃圾。pip/Kotlin/JNI/sqlite公共运行库、其它项目、GlobalSend收到的原图、Codex全局会话与记忆、手机与AVD用户数据均不处理。

## 你选择后执行

使用PowerShell 7，默认只预览：

```powershell
& 'E:\Programs\拾时 · Chrona\tools\Manage-ExternalArtifacts.ps1'
# 建议：先把A1至A7移回本项目，保留证据
& 'E:\Programs\拾时 · Chrona\tools\Manage-ExternalArtifacts.ps1' -Id A1,A2,A3,A4,A5,A6,A7 -Action Move -Execute
# 若你只要删除旧class与存储审计文本，先看计划再加-Execute
& 'E:\Programs\拾时 · Chrona\tools\Manage-ExternalArtifacts.ps1' -Id A2,A6 -Action Remove
```

脚本只接受上述精确白名单；执行前检查源／目标边界、目录链接、文件占用及目标冲突。发现异常停止，不删除公共父目录。实际操作记录留在项目`build/external-artifact-actions/`。
