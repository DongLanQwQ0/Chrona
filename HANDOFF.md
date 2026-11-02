# 当前交付：99修复更新与数学21节重整

## 当前候选与唯一下一步

- 当前源码候选 **0.15.99/261**，release/lint及只读probe已构建；最终增量构建再次确认源文稿边界与包一致（`build/release99/final-build.txt`）。候选目录 `build/candidates/0.15.99/`。USB **10AG5S1SR4005X7 / V2527A** 实际仍是98/260：99覆盖返回INSTALL_FAILED_ABORTED，已读回版本未升级，前台NotificationShade；不把错误归因用户点击。已停止重试并通过request_user_input_async询问是否重发。用户允许后仅重发一次正常安装，读回99，再仅续10/20微积分，确认无图草稿真实结果，然后完成其余16节。无课堂工作/监视服务，不以安装受阻取消数学范围。
- 用户最新明确要求：新增命名文档不能取代其它工作，之前要求悉数完成；每次新需求先简要说明理解的范围、保留行为和预期结果再改。命名按 `docs/ui-component-names.md`，区分主导航栏、视图选择栏、状态筛选栏、操作组及定位强调。本地AGENTS已加这条，保留其余owner原有修改，不整文件提交。
- **目标仍active、未完成。** 数学原21节仅4节最新成品验收；10/20试点目前未完成，不据UI、协议或格式测试通过宣称21节完成。禁止再次整批盲重启、拼接半稿、改写冻结历史或放宽来源/图片校验。

## 99已实现与有效证据

- **新作业定位三问题已修复并原生验收。** 98基线强调仅399–434ms，末页出现scroll5808→144→5808及卡片重建；99目的页首个可见绘制前完成必要padding/居中，稳定后开始定位强调，本地自然刷新复用现有行协调器，独立Overlay不被按压foreground替换。原Dock、按压及ENTER+2000ms参数不变。
- `build/homework-focus99/` 固定candidate99 APK及逐帧JSON：首条、单条、末页单条/多条、本地刷新、HOME/resume、普通横屏、深色全部通过；2231–2242ms连续渐隐，首个目的内容及强调帧中心误差0、卡片实例1、无早缺失。减少动画真实比例0：2000ms全部alpha255、2017ms清理，原值1已恢复。横屏DOWN→CANCEL、返回和无红点入口保持。root已看JPEG，专门ui_check独立复审无P1/P2及关键缺口；UI证据适用于不变的Dashboard代码，不冒充实机OEM验证。
- **通用思考配置已落地。** 任意host/model发送显式none/low/medium/high/max，不再由正向白名单丢弃，不静默改档；auto→low，仅原已识别普通OpenAI模型auto保留原省略合同。DeepSeek none用原生disabled。原预算/schema/cache、旧轮协议与options保留；混合拒绝涉及思考字段也不降级重发。AiRequest917、ResponsesProtocol705通过，真实本地HTTP覆盖拒绝不重试、协议不切换与冻结请求原样发送；产物 `build/protocol-effort99/`。
- **无图草稿边界已修正，待真实续跑。** 98完整JSON的notes[0/3/5/6/7].text存在非规范JPEG Markdown链接（`build/image-ref99/before/audit.json`）。普通/增强草稿及显式恢复、唯一格式修复的新消息均明确未附实际图，images=[]，文本禁图片/占位链接，只经image_requests请求时间范围；不引入旧schema没有的字段，不更改旧消息。非法图片仍拒绝提交、不新增自动语义重试。最终定向普通51、增强含Responses330、JSON修复299、事务/legacy295通过，旧legacy !affairs/!collection断言保持；图片引用39既有证据复用。产物 `build/draft-images99/`。
- **命名文档已交付并推送 c62d89b**：`docs/ui-component-names.md`，12项名称、2张压缩原生截图、3种高光区分，`docs/README.md`已索引。root看图且ui_check独立审查通过；截图只用于命名，不作为定位修复证据。
- 99其它代码与验证记录尚未提交；当前HEAD为c62d89b（98源码7428a09已在main），正常授权 `git push origin HEAD:main`，不force、不为小修复创建Release。

## 数学21节：保留身份、字幕与审计

- 原21身份清单：`build/math-completion-audit-20261101/phone-before-batch92.json`，18微积分+3线代。当前历史28含数学22（新增11/2线代），原批次不无意纳入新课或6条C课。
- 已验4节：86985-1984869、86985-1988487、86985-1984868、86985-1988486。均schema1、强度3、单篇、来源通过、精确重复正文0，引用图18/26/9/23全部可读，更正文稿复用。不要重新生成这4节。
- 其余17仍需完成。原17批次已正常停止：成功0、失败7、取消10；`build/monitor96/selection-final.json`确认17/27及排除6C/4有效成品，旧selection-progress不是真正最终值，选择脚本已防重复。保持3并发、图片授权true、缺图走原下载器、复用已提交且源块匹配的更正字幕。
- 当前试点 **86985-1982954 / 10月20日微积分**：5/5更正字幕已复用。97显式续跑output32768撞冻限，原生思考68076字符/正文6905；配置low当时漏传。98新轮真实low完整stop：59秒，input14558/output7337/total21895，思考6278/正文7934，随后被上述5处图片引用校验拦截；并非继续输出超限。
- 试点完整98元数据 `build/glm-effort98/phone/after-one.json`。旧97原生response仍在应用runs归档；原本地快照曾被固定输出脚本覆盖，当前 `build/usb97/after-one.json`不能当97原快照。按实测stdout提取的97元数据投影为 `build/usb97/failure97-projection.json`。今后用 `build/image-ref99/read_audit.py <唯一输出目录>`，禁止覆盖旧证据。
- 继续本轮只追加新恢复要求，保留旧input/output items、加密reasoning及冻结low/Responses。重新整理才建独立新轮并精确归档旧run；只复用已提交更正文稿，不另导出旧成品副本、不拼接截断回复。完成试点后只启动其余16节，不重做试点/4有效成品；最后按原21身份逐项验证最终格式、单篇精简、独立事务区、来源及图片。
- 活动AI期间不安装、不运行instrumentation（会重启进程）；仅普通UI、自有通知和只读服务。观察超时不能证明任务停止，须核对同一服务/句柄。旧HTTP502/413/403与当前语义校验分别记录，不能把旧服务故障当作今天所有失败原因。

## 已交付且不重开设计

- 96已实机验证PPT分页完整圆端及连堂字幕后台增长：线代13:25–15:50原被raw首段14:10提前结束，现复用ZjuTimetableView连堂块，监视与导出共用；同一服务在离开专用页后字幕继续增长。源bf920d3、证据 `build/monitor96/`。95心跳/握手恢复39项、96结束边界58项及正常原生PPT竖横视觉通过。
- 日期专属截止通用date_exclusive已实现；原贺信“11月3日前”已正常编辑保存为11月2日23:59并重开读回。小Responses健康请求已通过，但不替代整节长流程验证。历史详见 `docs/validation-history.md`。
- 监视全局自动总结/上传图片、课程覆盖三态的控件位置原处讨论，未借本次故障修复擅自拼布局；需求与后续授权需依原上下文核对，不默默取消。

## 设备、文件与owner边界

- USB当前无观察到的课堂工作/监视服务，最近只读探针已卸载；手机任务XML及 `/data/local/tmp/chrona/monitor95/` 已精确清理。99 probe只读检查也已卸载。下一次安装前必须重验，尤其晚间可能自动开始课堂监视。拒绝安装不可绕过或自动反复尝试。
- GlobalSend111已安装且明确归还USB；无新真实冲突不跨聊天例行发消息。用户正在使用手机切走时停止触控；已有授权下无人回复可继续，但真实权限拒绝/设备所有权冲突仍约束。
- 共享AVD `F:\Android\Avd\Chrona_Classroom_36` / emulator-5554 保持运行（PID4948/3736），当前已交还root；不新建/克隆/清空/改DPI分辨率。本轮测试cache精确清理、checkpoint为空、旋转free。JPEG先压缩再查看，禁止直接看PNG原图；电脑所有衍生文件留项目build。
- 子代理：协议实现、定位测试、无图边界测试、命名文档均完成交回；专门 `/root/ui_check` 可复用，不能再新建同名。实际工具无agent_type，不编造字段或模型角色声明。root负责集成/接受/手机更新和数学最终验收。
- 公共工具：ADB `F:\Android\Sdk\platform-tools\adb.exe`，JDK `D:\Minecraft\java21`，gh `F:\Programs\GitHubCLI\bin\gh.exe`；保留登录，不取token。FastCtx未暴露时用pwsh7，错误立即中止。
- 保护他人既有改动：AGENTS.md（只额外加本轮沟通规则）、docs/Communication/messages/2026-10-31.jsonl、.backups/、.local-backups/、角色toml、tools/Clean-Project.ps1、tools/Seed-PreviewData.ps1。只提交本轮owned路径，不整树add。外部core接口仍在docs/Communication/to-external-agent.md。
