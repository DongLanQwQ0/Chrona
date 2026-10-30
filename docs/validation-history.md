# 验证记录

## 0.15.76 — 课堂只读处理记录 — 2026-10-30

- 用户明确批准详情现有操作行增加「处理记录」，与总结／重生成同行，右上原导出／监视／删除保持。新非导出Activity沿用公共二级页、入场、安全区和整页滚动；没有额外固定工具。只投影已保存阶段、请求状态／时间、实际Token与缓存字段、上下文附图数量、选中／已分析数量和各段图片依据，不展示或导出原消息、响应正文、图片二进制、远端地址或凭据。缺值显示未提供，实际0保留；新run不混旧摘要，读取与刷新不调用AI、不写run。存储锁内排除ACTIVE所有者，停用／换账号关闭。
- 同一原AVD、正常横竖屏及深浅主题原生22项各通过；最终portrait报告`build/enhanced-document/native-audit-238-final-portrait.txt`、landscape报告`native-audit-238-landscape.txt`。覆盖真实从详情DOWN／UP进入、首次动效、上滑标题离屏、刷新、recreate、原run字节不变、busy读取保护及账号关闭；投影覆盖13项隐私／缺值／两种模式／图片计数等边界。JPEG／report在`audit-238-evidence/classroom-audit-{portrait-light,landscape-dark}/`；横屏正文视口956px／屏1080px，上滑495px后标题离屏，未调分辨率或DPI。
- 原ui_review独立审核发现P2：持久化running被误显示为实时「请求中」。改为历史语义「未记录结果」，不改原状态；仅补无ACTIVE、无finished及UI／原字节4项，`native-audit-238-unfinished.txt`和`audit-238-evidence/unfinished.jpg`。复审关闭P2，受检UI／只读交互通过；不把合成数据当作真实provider命中证据。
- 正常生产release／lintRelease与隔离debug／AndroidTest编译通过`audit-238-final-build.txt`、`audit-238-unfinished-build.txt`。最终固定`build/candidates/0.15.76/Chrona-0.15.76.apk`3283725字节，238／0.15.76、非debug、原签名，签名／包信息见`candidate-238-{signature,package}.txt`。
- 首次覆盖被手机明确拒绝，已停止并询问用户，未绕过；用户允许后19:09覆盖成功，19:12:07补入最终状态修正版，首次安装仍2026-09-26 18:54:37。`phone-238-{install,retry-install,final-install,final-package}.txt`区分拒绝与最终结果。未调用AI。核对时仅导航至历史，C课控件被浮层覆盖后守卫拒绝触控；随后焦点切到QQ、进程20070仍活着，停止操作并请求用户空出两分钟。最终真实C课审计尚未读回，升级前开启的LAN还需从正常页面恢复；当前依赖见HANDOFF。
- 用户随后明确暂停手机操作，完成真实C课9/23处理记录只读核对与实际滚动：选中／已分析图片各40；第8轮输入762337、输出2752、缓存读取759808、总765089，第9轮缓存读取749952；第4轮失败及第5–7轮缺缓存字段仍显示未提供。`phone-238-audit-values.json`只保存当前原生页面白名单文本，没有读生产私有文件、重生成或调用AI。通用截图助手遮蔽六位数，因此`build/phone-enhanced-229/phone-238-audit.jpg`中的Token灰条由该数值记录补证，不当作服务缺字段。
- 已从正常设置恢复LAN，`phone-238-lan-restored.jpg`及`phone-238-lan-http.txt`确认开启与HTTP200；未代替用户配对或重新发送设备链接。ui_review亲看本轮真机JPEG与白名单数值后增量签收。窗口结束已明确归还手机给GlobalSend，不再触控／安装；课程语义质量与全课缓存命中率没有据此扩大结论。

## 0.15.75 — 长课堂首屏、按需公式与真机附图 — 2026-10-30

- 用户提供39137后，与GlobalSend串行使用同一原手机。真实C程序设计基础9/23已完成总结，等待20秒仍空白；236安全诊断证实HTML已生成、原生高度44559px，但全页4651公式尚在串行处理，425→1638→3201→4651后才visual ready。原因是全部隐藏文稿排版阻塞首次呈现，非AI未完成。`build/enhanced-document/phone-236-{phase,file-probe}.txt`及`build/phone-enhanced-229/phone-236-final-render.jpg`保留边界；系统日志输出关闭，临时安全计数仅写app-owned诊断文件，不读生产私有缓存或原始AI审计。
- 237改为首屏公式就绪后沿用原入场；屏外笔记16项一批让出事件循环，折叠文稿展开加入同一MathJax串行队列、已排节点复用。完整源文稿／AI历史／图片清单／导出均不改，本轮没有AI请求。临时计数桥接与文件写入已从最终包移除，并精确删除手机该诊断文件。
- 生产release／lintRelease通过`reader-237-final-build.txt`。固定237／0.15.75、3277629字节、非debug、原签名，证据`candidate-237-{package,signature}.txt`。最终手机17:19:01正常覆盖，首次安装时间仍9/26；`phone-237-final-install.txt`。真机JPEG `build/phone-enhanced-229/phone-237-{final-body,reading,picture,viewer}.jpg`亲见正文、实际滚动、两张真实引用图及点击后的共用全屏查看。采集包括dump与截图，不据此宣称精确3秒性能。
- 正常原AVD横竖屏、不改DPI：同一排版代码原生阅读、开合公式／代码／滚动、单节与批量ZIP、失败恢复29项各通过；最终代码后台覆盖／恢复与4651折叠公式8项通过。`native-reader-237-{portrait,landscape,long-hidden}.txt`，JPEG／report在`reader-237-final-evidence/`。长文检查最初断言误将JS字符串的JSON引号当作数字比较，改用数值返回后通过，未改生产行为。原ui_review独立亲看实际渲染与源码后签收本轮范围，未扩展为AI质量或真实服务usage。
- 恢复LAN至`http://10.195.59.196:8765`，4项静态资源200、SVG MIME正确，`phone-237-lan-http.json`。不代替用户配对、不重复设备发送。已明确向GlobalSend归还手机，后续不并行触控；不发布小版本Release。真实usage／缓存命中仍未读回，记录于当前HANDOFF，不声称已测实际节省。

## 0.15.73 — 真机恢复、完整图片规划schema与原模式续跑 — 2026-10-30

- 用户提供10.195.59.196:38711，正常ADB连接后核对原V2527A序列身份。与GlobalSend明确单一手机控制者后，从正常页面读取原C程序设计基础2026-09-23第7–8节：实际错误`No value for image_requests`，923条字幕、282→140截图及未完成入口保留；`build/global-send-link/phone-233-real-failure.txt`及同名压缩JPEG/XML。不把它描述为后台被杀或擅自断言已用几次修复。
- 将增强整理通过正常设置保存恢复false，并重进确认；233于14:36:59正常覆盖。核对提示词发现完整JSON示例漏列两个必需图片字段，改为默认、增强及手动/修复共用五字段完整示例；未授权/无图时示例显式保留空请求数组。234正常覆盖后再次确认原设置。初次默认检查仅原字符串断言失败，改为解析真实请求中的完整示例并保留主动核图条件后，默认管线292项（同次AI请求538项）通过`default-schema-234-final-check.txt`。
- 原全局模式改变会让未完成课堂换管线，补充共享总结入口的检查点模式选择：已有未完成conversation按实际blocks结构保持原模式，新任务及完成后重生成继续用当前偏好。真实生产helper与两向模式变更后的实际HTTP生成、原run身份、逐消息原样前缀、完整示例、有限修复及默认路径共341项通过`schema-resume-235-check.txt`。既有模型/图片预算改变产生独立任务规则未改，不修改已发送消息，也不扩大自动修复次数。
- 最终生产debug/AndroidTest编译/release/lintRelease通过`production-235-build.txt`。固定`build/candidates/0.15.73/Chrona-0.15.73.apk`3276349字节，235/0.15.73、非debug、原签名；`candidate-235-{package,signature}.txt`及JSON。手机14:52:25正常覆盖并读回235，首次安装仍2026-09-26 18:54:37；安装前无课堂任务，证据`phone-235-{pre-install-services,install,package}.txt`。
- 新包重进设置确认增强仍false；恢复用户原开启的LAN服务，实际地址http://10.195.59.196:8765。真实`/`、`/icons.js`、`/brand.svg`、`/app.js`均200，SVG MIME正确，`phone-235-lan-resources.json`。正常关联仍显示DongLan/Windows，不重发链接、不代替用户批准网页配对。配对信息、密码遮蔽后的JPEG/XML在`build/phone-enhanced-229/phone-235-*`，只生成/查看压缩JPEG，不写手机公共目录测试文件。
- 主代理与原只读ui_review亲看235真机增强关闭、LAN及AI运行3张JPEG，增量实际呈现签收；无生产UI改动，233的动态清单14×2、行可达2×2、原协议32项仍适用。不据此扩展为运行中AI质量或真实iPad收件通过。
- 按已授权原检查点启动一次剩余AI整理，原模型、服务与最多40张许可不变：全局增强false时真实直接进入核图计划0/5，未重做已完成的文稿5/5；ClassroomWorkService2043实际运行。随后仅仅读取该应用2043通知，核图计划已到1/5，越过原首块缺字段失败点，证据`phone-235-ai-{confirm,running,running-services,notification-1}.txt`。后续最终笔记、实际图片及真实usage尚待结果，不循环重试。
- 后续仅读取本应用2043，依次确认核图计划2/5、分段完成笔记0/5、3/5、4/5，证据`phone-235-ai-notification-{2..5}.txt`；之后`phone-235-ai-terminal-services.txt`确认ClassroomWorkService结束，2043已撤销。自然回到该课时只读结果页：显示“重新生成”、无失败状态，`phone-235-ai-result.{txt,jpg,xml}`。刚进入时该帧未见正文，不能充作最终正文、实际图片或缓存usage通过；随后前台离开应用，守卫拒绝再截图，未抢前台或重做生成。
- 对这个空白帧新增隔离原生后台/长文排查，不改生产渲染：普通竖屏、nativeScreen=true，真实其它Activity覆盖期间释放本地公式ready，返回后长文可见，visibility0/alpha1、高23160px、5个笔记块及更正文稿、20处公式无错、溢出0，6项通过。`native-background-ready.txt`和`background-ready-portrait-light/{report.json,background-resumed.jpg}`。仅操作共享AVD自己的合成课堂，paid_calls=0，清理自有数据并结束；不将此结果扩展为真机具体课堂已正常渲染，也不据单帧空白臆造生产修复。
- 用户另给38975端口后正常连接原设备，明确协调单一控制者。导航中前台离开到桌面，本应用事件是userLeaving/pauseBackTasks而非当前进程崩溃；随后实际看到顶部悬浮通知遮住切换栏，不能仅以mCurrentFocus属于本应用判断点击与截图安全。清理确切两张自有newlease JPEG，不保留通知内容或将它们充作页面证据。当前手机再次offline；对端转达用户主动断开，停止连接尝试，实际正文/图片/usage仍未补验。

## 0.15.71 — GlobalSend设备列表动态更新 — 2026-10-30

- 复用现有Messenger ABI：前台立即/每5秒刷新设备清单，后台、LAN关闭与销毁撤销定时器；上一请求未结束不并发。独立清单错误不清发送错误或原操作ID，清单刷新也不重启已结束的状态查询预算。设备行继续按稳定ID复用，增删沿用原UiMotion，没有新增样式或系统权限。
- 共享Chrona_Classroom_36隔离原生应用正常竖浅/横暗各14项通过：自动新增/删除/改名、离线与能力撤销、焦点和滚动保持、错误/8秒超时恢复、后台停止/恢复立即刷新、重复生命周期不重叠、查询预算及关闭/销毁。证据`build/global-send-link/dynamic-final-{portrait-light,landscape-dark}.txt`，每档15次本地假RPC，无真实发送。先前13项试跑后发现自动清单不应再次发起操作状态查询，修复并新增第14项后重验两档，旧试跑不代表最终候选。
- 新设备及离线行完全可滚达的补充原生检查各2项通过，`dynamic-visual-{portrait-light,landscape-dark}.txt`；同名目录的`dynamic-{joined,offline}-visible.jpg`为压缩、遮蔽配对信息的实际画面。原open/link合同回归32项通过`regression-233.txt`（42次本地假RPC），覆盖原ID、迟到回执、能力、授权、普通链接终态及不自动降级。不调分辨率/DPI/字体，不调用学校或AI。
- 主代理与持续复用的只读ui_review亲看竖浅/横暗5张JPEG；独立复审核对源码、14×2、可达性2×2及回归32项后签收233本轮受检范围，无新增明确缺陷。汇总`build/global-send-link/accepted-233.json`。此结论不扩大为实际iPad收件验收。
- 正常生产debug/AndroidTest编译/release/lintRelease通过`production-233-build.txt`。固定包`build/candidates/0.15.71/Chrona-0.15.71.apk`3276329字节，233/0.15.71、非debug、原签名，见`candidate-233-{package,signature}.txt`及JSON。后续仅追加隔离测试case，不影响生产源码或此包。
- 手机USB当前离线，未安装233；手机上次确认版本232。实际iPad收件由用户后续验证，未猜目标、没有重复发送Windows。真实C课AI剩余整理的具体错误与临时增强开关恢复仍依赖手机，不自动重发付费请求。GlobalSend原所有者已交付并推送，其HEAD为`34e7ca8`，工作区干净；Chrona未改对端代码。

## 0.15.70 — iPad普通链接与缺字段修复 — 2026-10-30

- GlobalSend所有者已确认并实现ABI v1可选send_link/sendLinkV1及status.deliveryMode=link。Chrona优先原send_open_url；普通链接不发expiresInMs，sent/failed终态停止查询，queued/未知只查询，用户显式重试保留原ID、模式、目标和URL。打开失败不自动降级。隔离包在共享AVD真实Messenger及真触摸下正常竖浅／横暗各32项通过，`build/global-send-link/native-{portrait-light,landscape-dark}-final.txt`与对应JPEG目录。首两轮因AVD原AndroidWifi没有连接而未开启LAN，实际连接后重测通过；没有改产品网络准入规则或克隆AVD。
- 主代理与同一只读ui_review亲看两档六张JPEG。横暗失败态第一张未滚到文字，补定向真实滚动及完整可见高度1项，`native-link-failed-visible.txt`和`landscape-dark-final/link-failed-visible.jpg`，增量复审通过；不把早期failure.jpg当成签收。二维码／配对码在截图中遮蔽，测试文件只在隔离应用目录及本项目build内。
- 真实AI获用户再次授权后由2/5继续，完整更正文稿5/5保存，随后首段笔记缺image_requests停止。发现结构缺字段此前被当作语义错误跳过有限修复；现必需字段及容器类型错误走一次格式修复，空依据、无效来源／图片与权限矛盾仍手动。修复预算按已发repair阶段识别，先前语义失败不耗格式预算；网络中断的repair只在显式继续时复用原pending。手动／修复消息保留本块完整字段要求，不改原消息前缀。新增production回环`--case schema-repair`254项（含语义／手动预算）和默认管线291项通过，日志`build/enhanced-document/{schema-repair-check,summary-validator-232-check}.txt`。初次缺JSONException导入的编译失败已修，不作通过证据。
- 正常生产debug／AndroidTest编译／release／lintRelease通过`build/global-send-link/production-232-build.txt`；固定`build/candidates/0.15.70/Chrona-0.15.70.apk`3275829字节，232／0.15.70、非debug、原签名，`candidate-232-{package,signature}.txt`及JSON。用户明确允许中断LAN后13:40:47正常覆盖，元数据读回232／0.15.70，首次安装仍9/26；证据`phone-232-{install,package}.txt`。首次立即导航因窗口未就绪被前台守卫拒绝，待真实Dashboard焦点后继续，没有盲点。
- 用户允许232继续剩余AI；原gpt-6-luna／接口／40张授权不变，实际直接进入分段整理与核图计划0/5，没有重做5段文稿，2043前台服务证据`phone-232-ai-{confirm,running,running-services}.*`。随后离页核对LAN时服务不再运行，历史仍未完成；具体停止原因尚未读回，USB随后断开，未自动重发。此次增强开关临时开启，待可用设备上恢复原false。完整最终笔记／核图及真实usage不报通过。
- fresh真实本机桥显示DongLan／Windows，未出现可明确识别的iPad卡，没有猜设备或重发Windows。现场图／脱敏XML在`build/phone-enhanced-229/phone-232-fresh-global-send.*`。GlobalSend手机1.0.55/66及Windows已由对端更新；对端本地中心合成iPad接收证据只支持其范围，不冒充真实iPad收到。用户新增列表动态更新要求，后续版本承接；同时明确两个项目无人值守、最终自行验证，实际iPad收件留用户验证，不阻塞代码交付。

## 0.15.69 — 增强文稿原生阅读、导出与失败恢复 — 2026-10-30

- **GlobalSend真实联动用户验收**：用户明确允许打开关联页并亲自确认；初次开启LAN时手机无Wi-Fi，服务如实拒绝。用户连接Wi-Fi后，手机正常开启LAN，fresh本机桥显示DongLan／Windows，当前安装身份已有有效关联，无需重复批准。脱敏JPEG／XML为`build/phone-enhanced-229/phone-231-lan-started.*`，配对码与二维码已遮蔽，不保存原始截图。随后用户直接确认「这一项经过检验可以正常工作，可以直接唤起远程浏览器」，按用户真实操作验收关闭远程浏览器唤起缺口；不冒充主代理亲见Windows界面，也不据此断言网页会话已配对。停止重复发送、保留用户当前LAN访问，并通知对端所有者消费结论。没有更改ABI、组、凭据或Windows客户端。

- 串行复用既有Chrona_Classroom_36，隔离monitor.validation应用，仅创建并清理自有合成课堂，不访问学校或AI。保持设备原分辨率／DPI／字体；nativeScreen=true实测竖1080×2376、横2376×1080。原生Activity和真实WebView覆盖竖浅／横暗各27项，折叠、实际触摸展开、公式、代码、滚动文末及再收起；两次重进失败原因均完整可见。回执`build/enhanced-document/native-{light,dark}-accepted.txt`。命令尾部400/1为未使用的旧夹具参数，非实际显示配置；实际尺寸见各报告。
- 发现折叠热区不足48dp、草稿空概览及错误图片阶段文案；在现有阅读器内修复，未另建样式。CSS约47.998783px为WebView取整，物理热区123px与48dp换算123px相符。最终压缩JPEG及报告冻结于`build/enhanced-document/review-231/{portrait-light-accepted,landscape-dark-accepted}/`；持续复用只读ui_review亲看并签收。早期冷启动ANR、夹具缺少images字段、CSS浮点比较和窗口过渡未结束的失败保留在原日志，不充作通过证据。
- 实际单节／批量ZIP均含课堂总结.md、sources.md、更正文稿.md和引用图，四项内容逐一相同；原字幕与更正文稿区分，段落／代码转义保护且空章节不导出，证据`accepted-evidence.json`及两档实际ZIP。Host批量导出51项、失败恢复9项、历史标签5项定向通过，`batch-export-check.txt`；未重跑不受影响的整套管线。
- 更正失败且尚无第一份笔记时，入口改为「继续整理」。真实Activity定向恢复8项通过，`native-recovery-accepted.txt`与`enhanced-document-portrait-light-failure-recovery/`；两次重进状态高度与可见高度均51px。主代理与ui_review均亲看failure-before-summary.jpg并增量签收。
- 上述为平台原生合成证据，paid_calls=0，不证明真实模型质量或缓存usage。真实C程序设计基础的2/5检查点和两次HTTP502边界沿用0.15.68记录；没有再次付费原样重试。手机部署及最终构建状态以当前HANDOFF为准。
- 最终正常生产debug／AndroidTest编译／release／lintRelease通过`release-231-final-build.txt`。固定`build/candidates/0.15.69/Chrona-0.15.69.apk`3274905字节，231／0.15.69、非debug，签名验证并与230一致，见同目录`candidate-231-{package,signature}.txt`及JSON。GlobalSend确认让出USB后双方均仅发现模拟器，手机未连接，未安装231；不将AVD结果写为真机升级成功。不发Release。
- 用户随后重新接入USB。与GlobalSend串行协调释放后，12:39正常覆盖安装成功；手机读回231／0.15.69，首次安装仍2026-09-26 18:54:37。安装前无课堂或LAN服务；证据`phone-231-{install,package,install-services}.txt`。后续页面检查遇到系统图片拖放，返回触控未导航，随后只读焦点转入相册，停止触控并协调，未强停其它应用。压缩JPEG／XML留在`build/phone-enhanced-229/phone-231-{home,dashboard}.*`，不作失败恢复入口通过证据；没有重发收费AI。手机入口复验仍有缺口，不影响已确认的签名覆盖与版本读回。
- 用户明确暂停手机操作后补齐231真机入口检查：从首页正常导航历史／未总结，9/23真实C课两次打开均为可点击的「继续整理」，提示与按钮完整可见；仅查看，没有请求AI。证据`build/phone-enhanced-229/phone-231-real-failure{,-reopen}.{xml,jpg}`。主代理及原ui_review亲看并签收。旧229执行记录未保存具体detail，仍显示通用提示，不能宣称恢复旧HTTP502原因。恢复总结时间筛选、返回首页并归还USB。
- 用户新增手机公共目录禁写测试文件规范，已写回项目基本要求。当前root及Download中按Chrona标识核查未见测试文件；Download/Chrona仅正常周日程导出和用户文档，不删除。精确清理本次`/data/local/tmp/chrona-ui-229.xml`，以及归档后清理旧`chrona-01560-window.xml`。本地设备助手改为专用临时子目录、finally清理及前台双重守卫；释放设备后一次截图因前台已非Chrona被拒绝，未产生新的无关截图。

## 0.15.68 — 真实USB设置验收与失败原因保留 — 2026-10-30

- 用户接入USB，GlobalSend所有者确认让出手机。V2527A从228正常覆盖固定229包，版本读回0.15.67；安装前无拾时运行服务。普通竖浅／横浅／竖暗／横暗实际检查新增开关、成本提示、保存操作及退出确认：取消留草稿、旋转保留、放弃重进关闭、保存重进开启、无新修改返回不提示。14项证据`build/phone-enhanced-229/settings-result.json`；ui_review亲看6张压缩JPEG独立通过受检范围。未改变分辨率、DPI或字体，不用模拟器替代手机。
- 用户明确允许最近一节完整素材未总结课堂的真实AI用量，选C程序设计基础2026-09-23 14:15第7-8节（课程87476／课堂1972361），923条字幕、282→140截图；确认框原图片授权勾选，沿用最多40张。gpt-6-luna实际完成前两块更正文稿后返回HTTP502。用户另行授权一次续跑，实际从2/5开始、未重做前两块，随后同阶段再次502，停止原样重试。未重做已完成课堂，未把上游失败解释为后台被杀。证据`enhanced-real-start`、`enhanced-progress-2`、`enhanced-checkpoint-resumed`、`enhanced-resume-progress-1`的JPEG／XML及`current-owned-notification.txt`；仅读取本应用通知，没有取得服务缓存usage。
- 发现真实失败重新进入时execution.detail被通用授权提示覆盖。230共用failureDetail处理当前显示和持久化，HTTP只留状态码及应用自有AI建议，取消优先，不显示服务原文、URL或凭据。提取生产格式化／记录／恢复方法并复用真实ClassroomStore、NetworkFailure和RequestException，49项定向离线检查通过，`failure-detail-check.txt`；不重新运行无变化的整条收费流程。ui_review源码复审无新增明确缺陷，状态TextView自然换行且可滚动；230未来错误状态的实际重进尚未验证，不将229截图冒充230文案证据。
- 完整更正文稿、分段核图、最终笔记及其折叠／公式／导出，被第三块两次502阻塞，未形成真实结果；实际缓存命中亦未验收。原检查点保留，服务恢复或取得新依据后再由用户授权继续；不自动重发。一次测试导航过退应用后改为每次先确认当前Chrona节点再触控，错误路线截图不作通过证据。
- 最终230固定源码debug／AndroidTest编译／release／lintRelease通过`candidate-230-final-build.txt`。`build/candidates/0.15.68/Chrona-0.15.68.apk`3274865字节、230／0.15.68、非debug、签名验证通过且身份与229相同。用户在vivo确认后正常覆盖成功，手机元数据读回230／0.15.68，主进程14676，首页正常；原增强开关关闭经新包重新进入确认，跟随系统主题与旋转1／0恢复，无课堂运行服务，ADB已释放并通知GlobalSend。证据同目录`candidate-230-{package,signature}.txt`、`install-230.txt`、`phone-230-package.txt`、`phone-230-enhanced-off.{jpg,xml}`、`phone-230-final-home.{jpg,xml,txt}`及`final-*-rotation.txt`。不发Release／标签，public latest仍v0.15.61。

## 0.15.67 — 增强整理代码候选，待上机验收 — 2026-10-29

- 新增默认关闭的增强整理，设置保存、退出保护、旋转草稿及配置备份接入同一偏好；单节、批量及监视总结在启动时快照。先完整更正文稿，再按原字幕来源分块整理／核图并本地组装；更正文稿共用阅读器和单节／批量导出入口。改卷／讲评提示保留题目条件、扣分点及细微线索的依据和不确定性。
- 两种模式改为冻结请求选项、原位保留system／user／assistant／图片，仅追加后续消息；恢复不重包装或裁剪历史。run.json逐次保留原响应、实际usage、阶段与错误；未进行真实供应商请求，不能把合成usage视为缓存命中或准确度证据。
- 离线回环管线初组277项通过；针对超长单行与历史图片复用75项、非法来源／图片与手动续跑116项、Markdown围栏／公式／代码保护28项分别通过。原始合成请求及回复保存在`build/classroom-enhanced-check/fixtures/*-http.json`，换行逐例报告为`markdown-fences.json`；默认CI入口已纳入全部组。素材及usage均为夹具，不访问学校或收费AI。
- 原默认管线首轮暴露非法回复缓存导致无法手动继续，修复为保留原回复并追加校正请求。随后291项报告中的两处失败为旧图片消息下标与旧版本号断言，更新后受影响图片／续跑39项通过，见`build/classroom-enhanced/default-references.txt`；不把旧失败报告改写成整轮通过。批量队列15项通过，见`batch.txt`。偏好／备份检查含35项；reader.js语法检查通过。
- 持续复用的只读ui_review进行了本轮源码审查，发现波浪线围栏、反引号伪关闭两项P2；公共规范化器修复后28项定向证据通过，复审无剩余明确源码缺陷。该审查不代替实际渲染。正常屏幕竖横／深浅、更正文稿折叠与公式、九字段保存／返回／旋转，以及真实AI的图文效果和缓存usage均留待明日上机。
- 用户明确今晚只完成代码，明天上机测试。本轮不安装手机／模拟器，不运行真机或真实AI验收，不发Release或标签。手机最后确认仍为0.15.66/228。
- 最终冻结源码构建debug、AndroidTest Java、release及lintRelease成功，`build/classroom-enhanced/candidate-229-build.txt`。固定`build/candidates/0.15.67/Chrona-0.15.67.apk`3274457字节，元数据229／0.15.67、签名校验通过且签名身份与228相同，见`candidate-229-{package,signature}.txt`；未安装，此包为待上机代码候选。

## 0.15.66 — 网页共享矢量与主题 — 2026-10-29

- 网页原先仅从手机palette推断深浅色并读取primary，背景／表面／正文／次要色／描边仍固定。现默认完整应用手机六色，轮询继续更新；右上「网页外观」复用既有浮层和选择器，独立深浅色／主题色仅存当前浏览器localStorage，恢复跟随清除覆盖。保持内容节点、输入和滚动，不向手机提交设置。
- 移除JS重复手绘ICONS，`:app:generateLanVectors`以原生VectorDrawable为唯一图标源，输出icons.js；品牌SVG直接复制docs/assets/chrona-icon.svg，替换配对页文字占位并用于页首／favicon。任务接入preBuild，无新工具依赖。原生已有filled图标保留填充，stroke图标保留透明；检查发现`@android:color/transparent`不能当作实心颜色，已修正并加入真实DOM断言。HTTP仅扩展两个精确静态白名单，保留Host／Origin／CSP边界。
- `checks/lan_daily_workspace_check.cjs`真实Edge生产资产＋模拟手机API通过：原三页操作与正常横竖屏、原生home路径及透明clock、独立深浅色、手机颜色变化不覆盖独立设置、主题色重载保留、恢复后完整surface/text和零手机写入。所有静态资源夹具补齐icons.js／brand.svg与SVG MIME；未恢复旧的高级网页功能。首次检查使用不存在的driver.reload且被浏览器退出错误掩盖，改用真实CDP Page.reload；最终两轮结果通过，以`build/lan-theme/browser.txt`为准。未终止浏览器或改profile。
- release及lintRelease通过`build/lan-theme/build.txt`，LAN安全83项通过`security.txt`。固定包`build/candidates/0.15.66/Chrona-0.15.66.apk`3268369字节、228／0.15.66、原签名，见package.txt／signature.txt。主代理和持续复用的只读ui_review亲看appearance-independent-dark／appearance-follow-phone／phone-schedule-dark／landscape-schedule-dark四张压缩JPEG，受检UI无具体缺陷。截图位于`build/lan-daily-workspace/`，未读取PNG原图，不测极端DPI／低分辨率。
- 本轮adb无设备，未覆盖手机，真实LAN的228资产加载待设备可达；手机最后确认227。只同步源码，不创建Release／标签，public latest仍v0.15.61。先前GlobalSend真实Binder与Android接收／浏览器证据仍有效，完整手机→Windows首次关联链继续保留。
- 随后用户提供`10.195.91.186:38241`，V2527A正常从227覆盖固定228，版本读回0.15.66并启动。验前仅LAN及提醒服务、无课堂处理；更新中断原LAN后经正常设置恢复开启，实际地址`http://10.195.91.186:8765`。真实HTTP首页／icons.js／brand.svg／app.js均200，SVG MIME正确，生成标记与新主题代码存在，见`build/lan-theme/phone-{install,package}.txt`、`phone-resources.json`及`phone-ui.xml`。没有为验收批准新浏览器或跨应用关联，旧浏览器会话随服务重启撤销，须重新配对；已释放ADB并通知GlobalSend任务，无操作对端软件或Windows。

## 0.15.65 — GlobalSend局域网地址联动 — 2026-10-28

- `GlobalSendLink`显式绑定真实包／服务，核对系统UID、每次RPC的arg1和意图UUID。首次关联使用GlobalSend提供的同UID不可变PendingIntent；页面恢复、刷新、未知结果和迟到发送回包均不重发，120秒后恢复设备操作。实际地址只从当前`LanAccessService.url()`取得，保持网页配对边界；正式ABI由GlobalSend的`docs/INTEGRATION_ABI_V1.md`维护。
- 设备组在精简授权提示下方，菜单行16dp内距；稳定deviceId复用节点，名字／状态刷新和增删其它设备保持原行及焦点。首轮原生检查发现刷新时禁用行会丢焦点，已移除该禁用；只在无授权、目标不可用或发送未决时禁用。客户端本地假Messenger检查正常竖浅／横暗各16项通过，`build/global-send/native-protocol-{light,dark}.txt`；之后最新布局单独7／8项通过，`native-visual-{light,dark}.txt`，含真实横屏上滑把标题和工具滚出屏幕。假提供者只证明客户端，不代表远端可用。
- 真实GlobalSend1.0.30/39 x64与普通Chrona包通过4项Binder／首次关联检查，`native-real.txt`的localFakeRequests为0。用户点击路径真实打开GlobalSend关联页并允许，回到原Chrona会话；没有读取组凭据或批准网页。最初普通包运行隔离协议模式被身份保护拒绝，随后改用隔离applicationId，不清空已有学校／同步配置。AVD冷启动时Wi-Fi未连接，经正常命令连接既有AndroidWifi后运行；未改屏幕分辨率／DPI。
- GlobalSend所有者提供隔离中心1507和独立协议发送器，AVD经正常GlobalSend界面连接测试组。真实Android接收端`73e9d333-e67a-4924-bd06-30e885aab256`收到请求`9f469f5d-c90f-4476-9ce7-854d43701f9e`，后台返回needs_tap并出现「拾时请求打开网页」通知；点击后返回launch_accepted，系统默认WebView Browser Tester实际加载`http://10.0.2.16:8765/`的Chrona手机确认页，未自动配对。证据`real-{send,needs-tap,launch-accepted}.txt`、`real-browser-ui.xml`和压缩`real-browser.jpg`。该发送端是隔离协议工具，目标浏览器访问AVD自身服务，不代替完整Chrona→真实Windows验收。
- 对端的正式ARM64包无法装入x64 AVD，使用同源码x64验证包，未改模拟器。首次关联截图暴露提供者原生安全区问题，对端修为1.0.31/40；已正常覆盖，经设置→本机应用联动观察标题／关闭及关联行均处于安全区，`provider-40-safearea.{jpg,xml}`。外部ABI未变，原Binder证据复用。真实Windows组、登录和配置未改。
- LAN二维码检查、LAN安全83项、设置布局74项、公共UI1530项通过，结果在`build/global-send/`；普通debug／release与双lint通过`build-final.txt`。最终包`build/candidates/0.15.65/Chrona-0.15.65.apk`3264338字节、227／0.15.65、非debug、原签名，见`package.txt`／`signature.txt`，已覆盖共享AVD。主代理和持续复用的只读ui_review亲看最新布局、实际关联／浏览器JPEG，签收受检UI范围；不发布小修Release，公共latest仍v0.15.61。
- 真机`10.196.4.142:36241`本轮10060超时，mDNS无设备；未更新手机。完整Chrona点击真实Windows设备、接收浏览器访问手机8765及网页批准仍待设备可达后验证。已有原生绑定、Android接收／浏览器与布局证据继续有效，不重复跑整组。所有输出均在项目内，模型只查看压缩JPEG。
- 随后用户接入USB，V2527A／`10AG5S1SR4005X7`由226正常覆盖固定227包，版本读回0.15.65，进程存活。按GlobalSend所有者的交付及既定覆盖授权，一并从其1.0.28/37正常更新正式ARM64至1.0.31/40并启动，组连接保留。Chrona LAN页实际hello提供关联入口，精简提示和下方设备组位置正常，`phone-lan-layout.{jpg,xml}`；未代用户点击首次关联或发送。证据`phone-{install,package}.txt`、`phone-globalsend-{install,package}.txt`及`phone-globalsend-ui.xml`。验前无Chrona活动服务，临时LAN已恢复关闭，`phone-{before,after}-services.txt`；未改手机旋转／分辨率／DPI，不操作Windows。ADB已释放并回传对端；完整手机→Windows链路的首次授权及在线目标验收保留。

## 0.15.64 — 下载后自动总结与批量总结 — 2026-10-28

- 历史工具行先扣48dp方向按钮和两份8dp间隔，课程与排序字段等分余宽，保留完整字号与多行字段；菜单新增「未总结」。课堂／历史长按多选的动作统一为「总结」，正常下载完成后直接进入AI整理，已有完整总结跳过。
- 单节、批量与监视使用同一生成入口及原素材准备、检查点。保存失败／中断状态，旧监视队列、恢复或刷新不重发收费请求；批量确认仅授权冻结的失败课堂一次。自动图片权限沿用既有开关并快照0／40，重试恢复原图片选择；没有将默认授权升级为图片上传。
- 共享Android36 AVD正常竖屏浅色／横屏深色各25项通过，`build/classroom-auto-work/native-{light,dark}.txt`。覆盖下载缓存→自动总结、失败继续下一节、完整跳过、无授权不重试、旧监视队列不重试、一次性确认、图片授权快照、真实前台服务持续至总结、停止当前与未开始项、等宽字段与完整文字、未总结筛选、两列表长按多选、启动后退出选择及单节失败确认。使用正式Session／Store／准备方法、隔离假账号与注入生成器，每档9次假生成、0次学校或AI网络请求；不替代真实账号下载或供应商调用验收。
- 重试确认框原先把取消按钮放进正文滚动区，横屏实际被截；改为复用`UiDialogs.actionRow`并置于正文外。两档断言操作同排、取消完整可见且真实点击能关闭，图片开关保持未勾。压缩JPEG在`build/classroom-auto-work/visual/{portrait-light,landscape-dark}/`。夹具初版过早读取菜单、弹窗进入缩放和课堂异步缓存更新中的旧行；修正为等待真实可见节点、完整终态几何及当前已呈现数据引用，未改产品时长或放宽断言。早期failure图不作通过证据，五组正式命名图对应最新25项结果。
- 原管线host291项、批量队列15项、公共弹窗操作1530项、课堂读取状态20项及前台通知检查通过，证据均在`build/classroom-auto-work/`。普通debug／release与两组lint通过，固定`build/candidates/0.15.64/Chrona-0.15.64.apk`3254438字节，226／0.15.64、非debug、原签名，见`build/release-0.15.64-{build,signature,package}.txt`。本批只同步源码，不发Release或标签，public latest仍v0.15.61。
- 主代理及持续复用的只读ui_review亲看最终10张JPEG并审查固定226候选，独立签收受检范围，无剩余明确缺陷。手机最后无线地址10.197.86.81:40627拒绝连接，mDNS未发现服务，最终ADB仅见共享模拟器；未覆盖真机0.15.63，也未把隔离包验收写成真机已更新。剩余仅在设备重新可达时安装上述固定包并做该机入口复验，不重复未变管线检查。

## 0.15.63 — 卡片裁切、批量交互与迟到内容 — 2026-10-28

- 用户三图中的课堂／历史／监视卡片在按压放大时被正文子边界平切。`motionViewport`复用20dp沟槽、8dp纵向空间，宽屏只取完整1.08峰值所需最小边距；关闭视口children／padding裁切，以独立clipBounds保护实际外边界。首次仅验证矩阵能落入外视口仍漏掉ScrollView对子内容的裁切；主代理与ui_review亲看失败图后定位此层并修正，旧22项不能作为视觉通过。最终正常竖浅／横暗真实DOWN峰值与逐层裁切共45×2项通过，课堂／历史／监视及全选按钮圆角完整，`build/page-audit-next/selection_navigation-{light,dark}.txt`和`visual/batch/`。未缩小动效、改字号或修改课表网格。
- 两列表长按即选中本条，无选择／完成入口；系统返回先退选择、清勾并保留页面、行实例和滚动，下一次返回才离页。启动完整缓存下载后退出选择，夹具断网验证零学校／AI请求。历史课程筛选严格半宽，排序居中、48dp方向在右、相邻8dp，真实几何通过；独立全宽计数／选择层避免退出横跳。
- 来源内容返回保留同一行与已加载100条；来源及作业更新历史重建恢复100条及可达阅读偏移。首次标记由公共onPresented真实布局启动帧消费，取消／脱离／接管前不消费，恢复不重播。正常原生竖浅／横暗各15项，`page_recovery-native-{light,dark}.txt`及`visual/monitor/page-recovery/`；稳态焦点、两次preDraw和650ms系统转场后再次断言，横屏来源saved/actual为15213/15213、历史13846/13846。早期重建截图和立即clamp断言不足，已由稳定实值与截图替换。
- 附件首图迟到700ms及呈现前重建不提前耗尽入场，实际图片就绪才入场；已显示恢复和系统减少动画不重播。追加／删除保留其它图片节点、Drawable和焦点，草稿同步保持；五档公共圆角与详情单层裁切通过。正常原生两档各17项，`attachment_page_motion-native-{light,dark}.txt`和`visual/monitor/attachment-page-motion/`。monitor新检查均显式`nativeScreen=true`，不改变分辨率／DPI；早期漏参形成的400dp横屏与临时实例失焦只记为夹具失败，不作为正常屏幕验收。
- 日程／收件箱分页复用持久DateIntervalBar，保留查询、顺序、焦点、跨页选择及正文原入退场。真机发现内部目标已到2/5而可见页码仍1/5：异步结果在110ms退出后的180ms进入期间到达，旧结束回调未发布最新desired。公共结束帧按原token发布最新文字，不更改时长；生产方法host11项通过，移除修复的对照在170ms断言确切失败。正常原生两档各68项，覆盖真实箭头／滑动／选页、边界、附着不重建、搜索与实际TextView文字／alpha／位移，含170ms迟到结果。`dashboard_pagination-native-{light,dark}.txt`、`pagination/date-interval-{motion,old-regression}-result.txt`及对应JSON。
- 手动／批量处理统一沿用WorkService前台2043，不再叠加逐课运行通知；单课保留详细进度与原入口，多课保留各课进度与目录入口，结果另发。完成结果显示课程、实际日期开始时间与课堂标题，未知时间不编造。实际系统Work检查一／多课、退出继续、剩余课入口与停止全部通过；独立通知检查投递、权限拒绝、账号切换、同名不同课堂及完成身份全部true，`classroom_work-light.txt`、`classroom_notifications-light.txt`。夹具仅按固定假账号／课ID清理自己的结果通知，未削弱无活动通知guard。
- 相关host检查通过：settings_layout、ui_library、classroom_header、classroom_batch_work、section_motion、classroom_foreground_notification、dashboard_pagination、calendar_slide；各owner的native_motion_core46、page_recovery16、attachment_page_motion27、attachment_remove及原查询检查有效。新增检查已纳入`checks/run_checks.py`。debug／release及两组lint通过，新增legacy返回lint按既有模式注明API33+原生pageBack回调与旧按键路径；不是关闭现代返回。最终`build/candidates/0.15.63/Chrona-0.15.63.apk`3252478字节、225／0.15.63、非debug、原签名，证据`build/release-0.15.63-{build,signature,package}.txt`。
- V2527A实际正常覆盖225；主代理真实按住三类列表及监视横屏，实际边缘返回手势先退选择且保留48条课堂；历史仍15条，收件仍52条。最终手机确认可见页码1/5→2/5→公共选页→1/5与内容一致，`build/phone-0.15.63-{classroom-held,history-held,monitor-held,monitor-landscape-held,history-landscape,selection,gesture-return,final-inbox-footer1,final-inbox-footer2,final-inbox-page-menu,final-inbox-restored}.jpg`及`selection-result.json`／`pagination.txt`。首个225测试候选暴露的旧页码问题不作完成证据，最终包已再次同签名正常覆盖。没有点击实际课堂下载／生成／删除，没有学校或AI用量；首页与原提醒恢复，旋转1／0、三项动画1.0。所有图先压缩JPEG，输出均在本项目build内。本批只推源码，不创建Release或标签，public latest仍v0.15.61。

## 0.15.62 本地候选与Release整理 — 2026-10-28

- 用户确认按版本节点保留Release。原110条中实际存在的节点为v0.14.0、v0.15.0；另保留latest v0.15.61及vivo-setup-0.15.42。其余106条的126个附件247405993字节已完整归档到`build/release-cleanup/`，逐项检查传输、字节长度及APK／ZIP可读性或视频头。元数据、正文、恢复输入及资产均在项目内；删除前重新比对远端ID、标签、资产ID／名称／大小，删除后四条保留记录与附件不变，latest仍v0.15.61。没有删除Git标签或源码。证据archive-manifest.json、restore-input.json、delete-results.jsonl、post-delete-{releases,latest}.json。旧下载链接按已确认范围失效。
- 本周日历实测范围10/26–11/1，无读取失败／截断提示。正常横屏展开全部七天，周六实际有06:30–07:00校运会方阵集合、07:30–08:00校运会入场式，以及06:30–09:00云峰阳光长跑方阵及校运会开幕式。任一日占用00–08即保留整周凌晨轴，符合既有规则；未修改压缩条件。证据`build/ui-review-20261028-week-{landscape,early-scroll2}.{jpg,xml}`。
- 展开卡片新增MM:SS／H:MM:SS；最初原生Chronometer试验经独立审核和Android原始实现确认存在过零负数，已撤回。最终普通TextView复用秒更新服务、固定baseline克隆，开启秒时覆盖完整未来窗口；compact仍只在最后两分钟显示秒。没有新增服务、权限、网络请求或唤醒锁，不重查日历、不累计RemoteViews动作、不重置30秒胶囊期限。逾期条目deadline=0，避免未来执行时间覆写逾期状态。
- 按压控制器保留90／160ms原始时长，由Android按每次播放时的当前动画比例控制。同一控件在关闭动画时创建，再开启后仍是原StateListAnimator；真实AVD触摸DOWN／UP记录按钮1.05、卡片1.08及操作组1.10的实际preDraw中间帧、目标与回到1，`build/press-feedback-device.txt`。手机新隔离包首轮Activity启动未完成，中止且清理该自有安装；该轮不作为通过证据，系统动画比例读回1.0。
- 真实AVD正常竖屏浅色／横屏深色，实际生产RemoteViews apply／reapply与固定模板tick验证03:05→03:04、小时文字完整、关闭秒静态、活动开始／结束、待完成／逾期、延迟跨零仍保留非负文字及late tick归零。证据`build/atomic-seconds-device-{light,dark}.txt`及`build/atomic-seconds-check/atomic-{seconds,hours}-{light,dark}.jpg`。夹具不访问真实学校或AI、不创建用户日程。host生产方法170项、vivo协议116项、原子卡资源及胶囊期限检查通过。
- debug／release及两组lint通过；正式构建签名与原安装一致，224／0.15.62、非debug，`build/release-0.15.62-{build,signature,package}.txt`。固定APK`build/candidates/0.15.62/Chrona-0.15.62.apk`3246046字节，正常覆盖手机223。版本读回224，主进程7121，原收件52条保留；没有触发AI、学校下载或日程删除，原后台提醒服务恢复。手机旋转1／0、动画比例1.0。
- 真机V2527A使用原有进行中C程序设计基础提醒，无临时日程／通知：通知中心展开卡实际显示1:29:31→1:28:58；从桌面真实胶囊点击展开，原子岛宿主显示1:28:14→1:27:54→1:27:51，更新期间保持展开、小时秒数不截断。证据`build/phone-0.15.62-oem-{1,2}.jpg`及`phone-0.15.62-island-expanded-{1,2,3}.jpg`，均先压缩并裁掉无关通知。两种宿主实际显示已由主代理与持续复用的只读ui_review亲看签收。跨零的刻意延迟仅在AVD受控验证，不等待真实课程结束。
- 本批仅本地安装与源码同步，不创建v0.15.62 Release或正式标签；公共latest保留v0.15.61。后续小修合并为有意义的一批再发布，保留节点＋latest＋仍使用的独立工具。

## 0.15.61 — 正文首次可见性交接与真机收尾 — 2026-10-28

- 对0.15.60补充具体源码反例：reader先写入HTML、等待公式，原生WebView默认可见，最终ready才将alpha压到0开始入场；公式处理跨帧时可能提前露出再淡入。ui_review独立确认P2，原静态签收不足以排除此窗口。
- 首次正文在加入前设为INVISIBLE；本地正文／公式完成后，通过WebView的postVisualStateCallback确认可绘制，再在该回调中转为VISIBLE并交给原240ms／8dp入场。恢复已呈现正文和更新同一内容不重复隐藏；销毁检查与实例身份保护保留。没有改笔记数据、Markdown解析、AI或下载流程。
- 受控真实WebView检查深浅／竖横各9项通过，`build/markdown-ready-native-{light,dark}.txt`。测试侧在子View加入时包装原WebViewClient，仅延迟真实MathJax startup promise：DOM已含正文后继续等待700ms，ready前仍INVISIBLE，释放后呈现；覆盖同实例更新、recreate不隐藏／不重播及系统关闭动画。初版减少动画截图早于OS窗口过渡结束，补充宽978高1028、shown／focus、真实DOM187字及650ms终态后确认可见；证据`build/markdown-ready-evidence/native-ready-report.json`与最终classroom-document-{held,entered,settled,reduced}.jpg。先前空白早帧不作为终态证据。
- 真实V2527A／10AG5S1SR4005X7检查：设置→外观→返回、学校→导入、课堂→历史、已有10/27微积分完成笔记、正常横屏上滑收起全部上部工具；真实52条课堂按1→2→1翻页。`build/phone-0.15.60-{settings.mp4,source-switch.mp4,pagination.mp4,pagination.txt}`、对应JPEG/XML及note-landscape-scrolled.jpg。源及分页在223未变，复用有效证据，不重跑成熟矩阵。
- 最终固定223包正常覆盖222，版本读回0.15.61，进程26640存活；实际重新打开已有微积分正文并系统返回原历史10条、正常选择模式。证据`build/phone-0.15.61-{install,package,final}.txt`及`phone-0.15.61-note-{initial,ready,return}.{jpg,xml}`。未点击素材下载、重新生成、导出或删除，未调用AI；临时测试批量选择已退出。原旋转1／0恢复，未改分辨率／DPI／通知设置，原LAN未运行无需恢复。
- 录屏证实微信横幅覆盖顶部按钮后曾误入微信；测试工具增加横幅等待及前台保护。另有GlobalSend由shell UID2000启动抢占前台，经用户明确授权已向「设计跨设备传输与同步系统」发送暂让出通知；其当前Web任务声明不动手机，完成后恢复通知已送达。未停止另一任务或其它应用。与Chrona无关的聊天视频段及其派生帧已清除，只保留本项目UI片段；手机端本次录屏／层级临时文件已移出到本项目并清理。
- 主代理与持续复用的只读ui_review亲看实际真机、受控延迟及减少动画终态，最终签收，无剩余明确动画缺陷。0.15.60的其他原生93项／Web及公共库证据继续适用于未改路径。
- release＋lintRelease成功，固定`build/candidates/0.15.61/Chrona-0.15.61.apk`3245950字节，223／0.15.61、非debug、原签名，见`build/release-0.15.61-{build,package,signature}.txt`。源码149e726已普通推送并正式发布[v0.15.61](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.61)，远端标签精确149e7264e7e1806cdc426a42d03468aa10ec674a；唯一资产uploaded、3245950字节、非草稿／非预发布，latest读回v0.15.61。证据`build/release-0.15.61-{push,create,tag,latest}.txt`及published.json。本轮交付与真机复验均已完成，不重复安装已验证包。

## 0.15.60 — 页面动效审计与补齐 — 2026-10-28

### 审计结论与修复范围

| 路径 | 缺口及处理 |
| --- | --- |
| 外观、设置总入口、备份恢复、引导、更新、日程对比 | 补用公共首次内容入场；引导原步骤切换保留 |
| 重复日程、课程／考试详情、考试列表、课程关联、来源内容、作业更新记录 | 加载壳结束后迟到的数据原本直接出现；首批有效内容单独入场，待呈现标记跨配置重建保留 |
| 模型完整输出 | 首批正文就绪入场；只动画实际正文，避免父子8dp叠加；分页由单次pop改为公共方向swap |
| 课堂总结正文 | 本地HTML与公式真正就绪后首次入场；后续生成更新不重播、不重建未变化正文 |
| 日程来源设置、跨设备同步待确认 | 后台计数／系统日历／待确认列表首次有效结果入场；普通状态轮询不重播 |
| 主动合并重复日程、校历编辑 | 保留窗口和标题，首次预览及分页／配置切换作用于正文，不再整页直接重建 |
| 课表来源、学校学期、来源内容模式／周 | 补正文方向切换，导航保持实例，暂停／快速反向清理快照 |
| 网页日程／收件箱分页 | 原local渲染忽略direction；结果区增加120ms退出、180ms进入，输入／导航与选择保留 |
| AI设置、系统日程详情 | 修正恢复时无条件重播首次入场 |
| Activity返回、工作区三页、课堂四视图／分页、字幕／PPT、全屏图片、日历日期／模式、网页主导航／详情 | 现有平台或公共机制已覆盖，保持原路径；不增加延迟finish或第二套退出机制 |

公共swap还会取消尚待首帧的旧入场回调，防止快速切换被迟到的透明度／位移覆盖。未改Dock尺寸／弹簧、权限、提醒期限、业务保存或下载协议。

### 固定候选与有效证据

- 正常Android36共享AVD隔离包，竖屏浅色与横屏深色分别93项通过：`build/page-motion-native-{portrait-light,landscape-dark}.txt`。覆盖16页实际首次入场／返回窗口资源／recreate、五条700ms迟到读取、真实Markdown ready、来源／学期／周／分页、快速反向、实际标题返回、减少动画、同步待确认／作业历史、校区配置及13组重复预览分页。测试仅修改自有记录和隔离配置，结束恢复；没有运行AI或学校认证。
- 首轮横屏测试器在NOSENSOR Launcher前冻结旋转、逐Activity强制方向引发测试自己的配置重建，之后又在点击后等待UI空闲耗尽被测动画。修正为普通父窗口出现后冻结正常显示方向、保留实际当前Activity并在动画期间采样。早期failure／混合方向帧不作通过证据；最终横屏925×420dp，未覆盖分辨率或DPI。最新ModelOutput仅正文入场的一行由最终竖屏93项补验，横屏已验证相同正文及分页轨道。
- 生产方法替身检查：`build/page-motion-native-core.txt`37项、`build/page-motion-ui-library.txt`1530项、`build/page-motion-settings-layout.txt`74项及`build/page-motion-lifecycle_motion_check.py.txt`通过；不以这些替身证明设备渲染。
- 真实Edge运行生产网页资产＋模拟手机API：`build/page-motion-web.txt`原日常操作、考试只读／混合全选、方向分页检查通过。JPEG为`build/lan-daily-workspace/desktop-pagination-{exit,enter}.jpg`及原日常页面；不代表当前手机LAN服务已升级。
- 原生压缩JPEG在`build/page-motion-evidence/page-motion-{portrait-light,landscape-dark}-*.jpg`，概览`portrait-contact.jpg`、`landscape-contact.jpg`。主代理和持续复用的只读`ui_review`亲看入场／中间帧／终态后签收受检范围，无剩余明确缺陷。所有图先下采样压缩，无直接读取PNG原图。
- release＋lintRelease成功；固定包`build/candidates/0.15.60/Chrona-0.15.60.apk`，3245674字节，222／0.15.60，非debug、原签名。证据`build/release-0.15.60-{build,package,signature}.txt`。
- 源码6ea8e5e已普通推送并正式发布[v0.15.60](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.60)；远端标签精确6ea8e5e5b77a5b52813912ef7f9fce3fab2bb1ed，资产3245674字节、uploaded、非草稿／非预发布，latest读回v0.15.60。证据`build/release-0.15.60-{push,create}.txt`、`build/release-0.15.60-published.json`与tag／latest读回记录。
- 发布时USB仅见模拟器；随后用户提供10.197.86.81:40627，硬件序号10AG5S1SR4005X7，固定222包正常覆盖安装成功，版本读回0.15.60，进程存活并实际打开首页。安装前无Chrona运行服务或活动Job，LAN原先未运行。证据`build/phone-0.15.60-{install,package}.txt`及压缩首页JPEG／XML。手机前台先后切到QQ／微信（第二次发生于用户允许继续之后），前台guard停止后续点击；导航／课堂正文与分页的该机复验仍待稳定前台，不把AVD与模拟API称为真机验收。

## 0.15.59 — 日程教务考试类型 — 2026-10-28

- 用户明确考试仅来自教务系统。日程增加只读考试投影，原生与Web共用`ScheduleListing`；全部类型也包含考试，手动与AI仍保留原三类。按官方时间／星期、考场、座位显示，复用只读详情；不创建候选或系统副本，不参与完成、确认或批量写入／删除。
- 正常Android36隔离包`build/exams-schedule-native-light.txt`46项通过：真实SQLite混合59行／3页正反序与跨页定位、只读全选ID隔离、四种范围、日期／搜索／日历状态、缓存刷新及账号／停用／损坏，真实LAN配对与只读API，原生实际触摸筛选、详情、旋转、取消草稿与清除。`build/exams-schedule-native-landscape.txt`6项通过，补拍等待旋转完成后的稳定横屏详情。未覆盖DPI或分辨率；临时测试数据只在隔离包内。
- `checks/schedule_query_check.py`通过，原三类SQL／逾期／重复／4008行分页合同不变，见`build/exams-schedule-query.txt`。生产Web资产在真实Edge＋模拟手机API的原日常回归和新增考试操作通过，见`build/exams-schedule-web.txt`；414竖屏通过公共筛选选择考试，桌面混合全选仅38条可编辑本地项；编辑类别仍为3。手机竖屏原本隐藏独立类型下拉框，新增检查使用实际筛选入口，未为测试改变产品布局。
- 压缩JPEG：`build/exam-schedule-{portrait-light,landscape-dark}-{filter,list,detail}.jpg`及`build/lan-daily-workspace/{phone-official-exam-dark,phone-official-exam-detail,landscape-official-exam-dark}.jpg`。主代理及持续复用的只读`ui_review`亲看签收本轮受检范围；最初横屏详情过渡帧已由稳定帧替换。
- release＋lintRelease成功，`build/release-0.15.59-build.txt`；固定APK`build/candidates/0.15.59/Chrona-0.15.59.apk`3241802字节、221／0.15.59、非debug、原签名，证书和包元信息见`build/release-0.15.59-{signature,package}.txt`。
- 当前USB未发现手机，最后无线地址10.195.39.39:46721连接超时，未覆盖安装；不把模拟器或模拟API证据称为新版本真机验收。0.15.58的真实官方查询证据仍适用于未改变的考试获取协议。
- 源提交6405dc5；首次普通推送因github.com:443无法连接于21107ms后失败。随后只读TCP探测确认恢复，普通推送成功并正式发布[v0.15.59](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.59)，未改代理／鉴权。远端标签目标精确6405dc5，APK资产3241802字节、uploaded、非草稿／非预发布，latest读回v0.15.59；证据`build/release-0.15.59-push-retry.txt`及`build/release-0.15.59-published.json`。仅剩手机升级与该机日程筛选验收。

## 0.15.58 — 折姜星辰考试查询 — 2026-10-27

- 普通Git推送因github.com:443连接超时未完成，随后该端口TCP=False、api.github.com:443=True；未修改代理或重置登录。0.15.58已在手机可用，尚未发布GitHub。更新前原LAN服务已通过正常设置恢复，最后确认服务运行，未中断课堂／AI工作。

- 考试安排复用已有教务CAS会话；普通及期中各自读取官方时间、考场与座位，不依据活跃学期删掉仍未进行的考试，不调用AI或写日历。完整分页后原子替换账号隔离缓存；异常时间保留原文、空成功清空旧列表、失败保留旧数据。缓存可由系统回收，退出账号清理。
- 正常Android36隔离包实际HTTP拦截／AtomicFile／原生触摸41项通过：`build/exams-native-light.txt`，覆盖103条跨页、缺字段、期中、时区星期、失败与分页停滞、账号、空成功、手动刷新及旋转。正常横屏深色15项通过：`build/exams-native-landscape-dark.txt`，整页上滑移走标题，正文视口不被固定工具挤占；未调整DPI／字号／分辨率。压缩JPEG在`build/ui-exams-review/`，同一ui_review独立通过。
- 连接构造时退出的具体竞态已收口：分配client后、首次HTTP前复验来源许可，失效即取消并关闭。定向真实Session检查`build/exams-native-final-lifecycle.txt`17项通过，含阻塞构造→退出→放行后零HTTP。纯错误文案只由Session按是否存在缓存追加一次保留状态。
- 既有客户端及账号回归：`build/exams-zju-recovery-regression.txt`21项、`build/exams-client-regression.txt`95＋44项、`build/exams-planner-adapter-regression.txt`51项通过。JVM适配器新增模型依赖与考试缓存清理桩，实际AtomicFile仍由原生检查验证。
- 源码7ebff9f，release＋lintRelease通过；最后三处纯错误文案收敛后定向assembleRelease通过，固定`build/candidates/0.15.58/Chrona-0.15.58.apk`3236982字节、220／0.15.58、原签名。USB正常覆盖于23:46:01成功，首次安装2026-09-26 18:54:37不变。回执`build/release-0.15.58-{final-build,fixed-assemble,signature,phone-package}.txt`。
- **官方真机成功**：通过应用UI与已保存账号正常查询，显示7条未来考试及已结束分组；微积分期中11/7周六14:00–16:00、西2-101／121，线代期中11/15周日14:00–16:00、西2-104／53，大英1/6周三08:00–10:00；另有4门普通考试。实际点击刷新后结果一致，无错误。原XML及压缩JPEG为`build/ui-exams-phone/phone-exams-{loaded,refreshed}.{xml,jpg}`，主代理与原reviewer亲看通过。未读取或输出凭据，不以合成响应冒充官方数据。

## 0.15.57验收与真机更新 — 2026-10-27

- 正式发布[v0.15.57](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.57)，标签目标0acf342与已安装候选一致，资产3231902字节且uploaded／非草稿，回执`build/release-0.15.57-published.json`。

- 配对地址居中；独立启用的可选文字控件恢复平台屏蔽的pressed drawable状态，继续复用公共PressFeedback及原生选字。正常Android36实际触摸、剪贴板、会话失效保护和精确「已复制」深浅各12项通过：`build/lan-centered-native-state.txt`、`build/lan-centered-native-dark.txt`。压缩JPEG在`build/ui-0.15.57-review/`，主代理与同一ui_review亲看通过。真机图只证明已安装布局及按压，短暂提示未捕获，不作真机提示证据。
- assembleRelease及lintRelease通过；源码0acf342，固定`build/candidates/0.15.57/Chrona-0.15.57.apk`3231902字节、219／0.15.57、非debug、原签名。USB于23:10:21覆盖成功，首次安装2026-09-26 18:54:37保持，见`build/release-0.15.57-phone-package.txt`。源码已正常推送，未更改代理或重新鉴权。
- 前序真实LAN已通过：`build/lan-live-217/report.json`覆盖实际三页及正常竖横屏、选择／筛选／返回；`build/lan-live-218/report.json`补验433条目全类型按执行10/27、作业查询按截止11/6，由同一reviewer关闭P2。219仅改变原生配对UI，不重跑已有效Web证据。
- 真实微积分草稿于22:40:53继续31附图第二轮，22:45明确显示「总结已完成」及真实正文，严格图片引用校验通过。证据`build/phone-20261027-analysis/217-calculus-result.xml`及`217-calculus-complete.jpg`。未重下载或重跑第一轮；旧包未保存具体非法引用，不能将其原始错误直接归因于前缀。
- 矩形遮挡仅完成只读审查，尚未改变去重：当前近似连续帧直接保留较晚帧，无质量评分或同组回选；讨论建议同页分组与内容完整度择优，不默认为像素补全或AI补画。

## 0.15.55候选 — 三页轻量Web、课堂批量与横屏间距 — 2026-10-27

- 用户最新限定只验正常屏幕字号竖横屏，不再运行极端分辨率／DPI／字体矩阵。生产前端真实Edge鼠标／键盘／触摸检查`node checks/lan_daily_workspace_check.cjs`通过，模拟手机API；414×896首卡y340，896×414收紧至y270，上滑后y75，三页与选择控件、返回状态、焦点、全天日期及精确外部确认通过。JPEG和请求在`build/lan-daily-workspace/`，同一只读`ui_review`独立通过。
- 新接口真实Android SQLite＋HTTP通过，`build/web-core-android-http.txt`：复用原作业同步、同排序跨页定位、单项已读、精确负long外部实例确认及撤销、禁用来源拒绝、独立确认revision、派生逾期边界。隔离Context/数据库，不请求真实学校或AI；缺配置的刷新不联网，并给设置动作而非旧成功状态。
- 课堂正常批量15、Dashboard三个选择调用路径55、批量Export/Store51、失败恢复9、课程消歧5、队列15、FGS20项通过。实际离页后3节缓存下载16项、同一个OS前台服务且意外联网0；系统保存器写55节总ZIP并自动打开分享，113条目、55字幕、52原PPT、3笔记，0失败。分别见`build/classroom-batch-native-background.txt`、`build/dashboard-batch-native-light-dispatch.txt`、`build/ui-0.15.54-review/batch-system-export-report.json`。未发送给外部目标，本轮临时QA导出及自建batch测试包已清，项目副本保留。
- 总结图片路径与第二轮响应／失败审计39项通过，`build/classroom-image-references-audit-final.txt`。真实旧微积分轮曾返回未分析图片，旧包未保存具体引用；已修复合法images/前缀误拒绝反例及稀疏文件名合同，真正未知文件继续拒绝。新包真实图片轮尚未续验，不将具体旧响应猜测为前缀。
- 原生横屏workspace侧栏120→96dp，72dp底板、按钮和动效不变；安全区后外侧12dp、正文间隔32dp。正常实屏正文x487→426，压缩图`build/ui-0.15.54-review/rail-landscape-normal.jpg`由主代理与同一reviewer通过。真实上滑正文视口369→603→904px、回顶恢复369；未修改wm size/density，最后旋转恢复原1/0。
- 源32c3db9的最终assembleRelease＋lintRelease通过，`build/release-0.15.55-final.txt`；固定`build/candidates/0.15.55/Chrona-0.15.55.apk`3231638字节、217／0.15.55、非debug、原证书、无instrumentation类。USB于22:21正常覆盖215→217成功，首次安装时间2026-09-26 18:54:37保持。
- 安装后前台连续被LocalSend切走，已停止点按；随后USB离线且mDNS无已发现手机。真实LAN浏览器及微积分续验待设备空闲恢复，未宣称通过。普通Git push直连github.com:443失败（21059ms），源码已本地提交但尚未推送／发布；不重置鉴权或绕过既有工具拒绝。

## 0.15.53候选 — 新作业自动排程与课堂总结媒体 — 2026-10-27

- 新作业授权路径复用现有AI请求、偏好和时间转换，仅应用原候选执行时段，不移动官方截止／提醒或覆写手工安排。DB18新增独立队列，原解析表不重建。`python checks/zju_homework_planner_check.py --planner-only`51项通过，包含生产`revokeHomeworkAutoPlan()`立即撤权、在途响应不应用、重新授权不重复请求；真实SQLite与受控提供方，不调用真实AI。解析审计32、备份16、同步节奏24、恢复20、作业更新68、双类型102项回归通过；旧夹具执行逾期预期更新为既定0.15.52行为，降级备份夹具先删除新增子表。
- `build/homework-planning-ui-{light,dark}.txt`各41项真实原生检查通过，覆盖取消／确认开关、停用同步／撤权、原截止、AI建议提示及实际时间轮手改保存后撤除提示。隔离包夹具恢复原设置，不访问学校或真实AI。
- 单节课堂头部导出／监视／删除按序合并；删除隔离课的总结、字幕和图片，同时保留未来监视规则。活动工作／字幕连接与删除共用锁。`build/summary-media-{light,dark}.txt`各21、`build/summary-delete-auto.txt`54项通过；正式准备／AI传输263／538与新增删除生命周期16项通过。未删除用户真实课堂，未重新生成微积分总结。
- 总结正文和大图共用图片查看／拖放入口，Provider只开放已发布且被总结引用的准确文件，不扩大原raw路由。`build/summary-drag-{light,dark}.txt`各129项真实触摸和跨UID检查通过；DOM和原生HitTest都命中image-0，接收URI与预期相同、JPEG1120字节完整，邻接、写入、授权前及释放后均拒绝。证据`build/ui-0.15.53-review/image-drag-{summary-targets,drop-summary-inline,report}.json`及四张压缩JPEG。先前失败来自夹具CSS坐标换算错误，修正测试而未改生产权限边界。
- 单刷新收宽后的高光`build/summary-header-{light,dark}.txt`各106项通过；公共操作／按压检查沿用本批有效1530／47项证据。深色为320dp／150%字体。主代理亲看缩小JPEG，同一只读`ui_review`复核七张UI与最后四张拖放图，整体签收本批受影响模拟器UI和图片交互，无剩余明确缺陷；不替代真机验收。
- `build/release-0.15.53-build.txt`的assembleRelease与lintRelease通过；固定`build/candidates/0.15.53/Chrona-0.15.53.apk`为3218650字节、215／0.15.53、不可调试、原证书，正式清单不含隔离测试入口。版本、签名与清单回执为`build/release-0.15.53-{badging,signature,manifest}.txt`。
- USB正常覆盖213→215成功，首次安装时间2026-09-26 18:54:37保持，安装前后均无课堂工作服务。回执`build/release-0.15.53-phone-*`。真实既有课堂总结头部按导出／监视／删除显示；正文图片打开`AttachmentViewerActivity`，1/35→2/35翻页正常，正文与全屏原生长按均出现系统拖影、松手取消。三张压缩图`build/phone-20261027-analysis/release-{viewer,drag-held,inline-drag-held}.jpg`由主代理及同一ui_review亲看，无新增确定视觉缺陷；首张带过渡叠影，不用于稳定标题栏。跨应用投递使用本批隔离模拟器证据，未向真机其它应用发送课堂图。
- 单刷新真机按住图`build/phone-20261027-analysis/release-refresh-held-2.jpg`显示对称圆形高光；首次采集被通知和并行控制干扰，`release-refresh-held.jpg`不作有效验收。用户已确认暂停另一侧手机测试后再续测，不改其它应用通知设置。
- 今天上午微积分的真实历史条目显示未完成，正文为字幕草稿，1110条字幕、275→72候选截图；无活动工作服务。源码确认草稿在最终图片轮之前先保存，既往具体异常未持久化，现有公开UI／正常ZIP不足以归因。用户随后明确授权用当前AI服务继续；已正常点击继续整理，服务2043运行，复用既有检查点并进入31张图片的最终分析，未重新下载学校资料。该次真实结果待补，不能将历史草稿当作修复完成。

## 0.15.52候选 — 图片长按拖放与执行逾期 — 2026-10-27

- 图片附件缩略图／大图与课堂大图／内嵌PPT复用`ImageDrag`及独立只读`ImageTransferProvider`。`build/image-drag-native.txt`400dp浅色与`build/image-drag-native-dark.txt`320dp／150%深色各292项真实触摸检查通过；跨UID接收端读回完整原字节和精确JPEG／PNG，授权前、写入、邻接图片及授权释放后均拒绝，拾起后停用课堂账号也拒读。移动／多指不提起、取消及完成后浏览恢复、UP不误打开或移除均通过。Tile证据限生产共享组件，不代表完整Main／Task业务导航。
- 压缩900px JPEG及结构化回执在`build/image-drag-ui/`与`dark/`。主代理和同一专门`ui_review`亲看，拖影比例、公共圆角、窄屏大字及当前受检交互通过，无剩余规范缺陷。共享Tile多指疑点经两指持续和抬回单指持续的47项定向检查未复现，关闭该源码推断，未新增无依据的闸门。
- 新测试沿用`checks/classroom-monitor-validation.init.gradle`，其androidTest源集合纳入既有测试目录以包含独立UID接收器；`build/image-drag-reproducible-build.txt`与`build/image-drag-standard-runner.txt`证明从受控源码构建后，标准test APK runner同样292项通过。命令为`./gradlew -I checks/classroom-monitor-validation.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest`，仅安装到共享AVD隔离包，再运行`adb -s emulator-5554 shell am instrument -w -e mode image_drag com.donglan.chrona.monitor.validation.test/com.donglan.chrona.ClassroomMonitorInstrumentation`。
- 前期失败均保留诊断：跨APK ResultReceiver先经框架Parcel重建；源窗口先拾起后跨应用，避免不同UID任务InputSink的正常穿透限制；恢复触摸等待系统窗口过渡，双击复用原固定事件时间流。未关闭系统输入安全策略、未用反射调用代替触摸。一次意外长按遗留的本fixture系统drag由原拖动流的CANCEL清除后再验，不把该次未开始的检查算通过。原缩放与跨边31项回归通过，见`build/image-drag-zoom-regression.txt`。
- 逾期使用执行结束／仅开始及明确截止的较早时刻，严格超过才计入；完成项排除，提醒与真实截止不改。`build/task-overdue-sql-final.txt`覆盖模型、69条SQLite隔离记录和4008行分页；重复编辑42项、默认执行区间回归及真实LAN消费者交互通过。真实TaskStore深浅各3711项通过，包含改期到原首发前、完成、重复实例、计数与清理，见`build/task-overdue-native-{light-final,dark}.txt`；图在`build/task-overdue-ui/`。初次重复夹具使用非投影的毫秒identity，修正为真实occurrence身份，未改变生产身份精度。逾期显示经独立UI复审通过。
- 通知factory83、真实refresh决策63和原子卡契约通过，见`build/task-overdue-{notification-check,reminder-slots,atomic-contract}.txt`；即使明确截止仍未来，已过执行时段的通知也显示已逾期，不继续倒计时；显式提醒时间与原过期／延后时长保留。
- release与lintRelease通过，固定`build/candidates/0.15.52/Chrona-0.15.52.apk`为3208534字节、214／0.15.52、不可调试、原证书；新provider不导出，只允许精确临时授权，测试接收器未进入正式APK。回执`build/release-0.15.52-{build,badging,signature,manifest}.txt`。
- 用户明确「下次测试手机」，本轮后续未再连接、安装或操作真机；手机最后确认0.15.51／213。微积分未停止／未总结的真实原因仍待下次设备日志，不能以合成课堂测试替代该诊断。GitHub认证已有阻塞，未发布远端资产。

## 0.15.51候选 — 共用图片查看、监视操作与下课收尾 — 2026-10-27

- 手机续交付：首次拒绝后用户明确授权重试，正常覆盖安装212→213成功，读回0.15.51，原首次安装时间2026-09-26 18:54:37保持。回执`build/release-0.15.51-phone-{install-retry,version,services-before,services-after}.txt`；未卸载、清数据或导航用户页面，安装前后均无活动服务。
- 「回到最新」与开始／停止复用公共操作组，公共设置标题栏仅对动作组采用自适应宽度。`build/reader-header-400-light.txt`与`build/reader-header-320-dark-final.txt`各80项真实原生检查通过：48/97dp、展开中间帧、触摸／无障碍停用、追加和修订保留Y、切PPT、重建及原图库保存。窄屏首轮动画观察器安装在追加之后，漏掉已结束的真实展开，改为在追加前观察后通过，未为此改变生产动效。
- 图片附件与课堂入口只按方向选择共用布局，来源仅负责账号／缓存权限和保存格式。放大图内先平移，边缘余量进入原翻页轨道；方向按完整手指位移仲裁，独立审核发现的斜向跨边P2已修。`build/viewer-zoom-edges-portrait-final.txt`29项及`build/viewer-edge-pixels-final.txt`36项通过，包含真实斜向同手势跨边、纯竖向不翻、原生连续双指收缩至1且保留非居中焦点、普通附件保存和替换清单等待。单次收缩受Android最小缩放跨度限制，夹具改为连续真实收缩到1，不用反射代替缩放验收。
- 横屏自然物理2376×1080窗口与非零侧切口下，媒体与Decor边界相同，普通附件同样隐藏状态栏／标题，角落操作仍按安全区留20dp。原自定义wm尺寸会令切口insets归零，改用自然物理尺寸后完成真实切口检查；未把零切口检查当成成功。`build/viewer-fullscreen-cutout-final.txt`34项及上述36项是有效证据，最终held图同时显示离开蓝图与进入红图像素。切口overlay和wm尺寸已恢复。
- `build/monitor-end-device.txt`41项通过：按账号真实课表同名／精确开始时间匹配结束，冲突不猜；下课关闭socket、保存当前句且不重连；处理中无字幕／PPT请求和新下载，在途观察跨界不发布增量；ready6但空字幕继续等待，正式资料完整后复用prepareComplete、补图并仅入队一次，后续规则保留。未知结束时间保留官方资格检查，不补默认时长。`build/monitor-auto-0.15.51.txt`51项验证原自动派发／owner／去重与取消，使用受控生成器，无付费AI或真实学校请求。
- 原客户端离线95+44项通过，正式准备管线249项及后台服务生命周期检查通过，见`build/classroom-0.15.51-{summary,service}-check.txt`；公共设置布局74项通过。专门ui_review亲看压缩JPEG、复核36项邻图实际像素交集断言后签收，本轮受检UI无剩余确定缺陷；证据在`build/ui-0.15.51-review/`，不代替真机OS分屏与真实学校整堂处理。
- `build/release-0.15.51-build.txt`的release与lintRelease通过；固定`build/candidates/0.15.51/Chrona-0.15.51.apk`为3205170字节、213/0.15.51、不可调试、原证书。正常ADB覆盖安装被系统拒绝，返回`INSTALL_FAILED_ABORTED: User rejected permissions`，见`build/release-0.15.51-phone-install.txt`；手机最后读回仍212/0.15.50，未绕过拒绝，已询问是否重新发起。

## 0.15.50候选 — 连续图片、公共高光与横屏高度 — 2026-10-27

- 真机续交付：通过用户新地址10.195.39.39:37561正常覆盖更新211→212，读回0.15.50，原首次安装时间2026-09-26 18:54:37不变。未卸载、清数据或导航用户页面；安装前后均无活动服务，不启动此前未运行的服务。安装、版本、服务回执为`build/release-0.15.50-phone-*.txt`。GitHub认证仍失效，远端分发未完成。
- 真实原生连续手势复现PPT第二次失效，修正图片轨道触摸接续、首个MOVE及过渡目的页计算。`build/ppt-burst-origin-final.txt`和`build/ui-final-rapid-dark.txt`各20项通过，包含24ms连续手势与双向翻页；正常与320dp/150%阅读检查各71项通过，见`build/ui-final-reader-light.txt`、`build/reader-compact-dark-narrow.txt`，覆盖追加／修订保持Y、重建、双击／双指缩放、保存、空白关闭和原附件入口。
- 公共胶囊高光按完整祖先矩阵与圆角裁切，图标居中不再平移高光。`build/highlight-final-{light,dark}.txt`各254项、`build/highlight-conditional-header.txt`25项通过，覆盖五档圆角、48/44dp、单至三单元及整组缩放。公共设置布局74项、弹窗操作1530项通过，见`build/ui-0.15.50-{settings,library}-check.txt`。
- 子代理盘点36个Manifest入口：32个有界面入口已在模拟器实际1920×768横屏窗口呈现；2个NoDisplay路由、1个debug入口及无待授权请求的LanPairing未作页面呈现。初轮亲看59张压缩JPEG，发现Guide工具残留状态栏，修复后补验；详情extra、测量主视口与触摸边界夹具错误均有定向重验，未绕过注入权限。入口记录为`build/all-pages-landscape-light-final/{review.md,report.json}`，后续有效补验为`build/all-pages-focused-recheck/report.json`。
- `build/all-pages-height-dark.txt`66项通过：900dp/150%深色、课堂四视图、日历五视图、模型输出、Guide正反联动滚动，以及日期／时间弹窗。课堂正文232→601px、Guide226→439px，工具退出后正文实际增高，底部步骤可达；真实OS分屏、真实学校满课表及未触发的弹窗业务状态不在该证据范围。`build/ui-week-gesture-final.txt`9项补验横向位置和日期保持、边缘取消、多指不误切周。
- 主导航横屏底板自然高度、四周内距与竖屏一致，侧栏104dp、正文20dp，实际间隔33dp。实图发现主入口仍保留旧居中留白后补齐，与切页副本一并用真实测量断言验证；`build/ui-final-dock-spacing.txt`17项通过，包含首尾按住越过底板。压缩实图在`build/ui-0.15.50-review/`。
- `build/ui-final-landscape-ppt.txt`22项通过：横屏隐藏系统状态栏、无重复标题、公共返回／保存在安全区左右上角、真实系统文档保存、返回继续沉浸和退出恢复正常系统栏。专门ui_review亲看压缩JPEG并复核原生回执，本轮受检UI范围最终通过，无剩余确定缺陷。
- `build/release-0.15.50-final-build.txt`的release和lintRelease通过。正式候选`build/candidates/0.15.50/Chrona-0.15.50.apk`为3202822字节、212/0.15.50、不可调试、原证书；badging与签名回执同版本前缀。手机地址10.195.39.39:39173连接超时10060，未覆盖安装，手机最后确认仍211/0.15.49；GitHub正常认证token无效，未推送或发布。按无人值守要求未要求用户重连，未调用付费模型或操作真实课程数据。

## 0.15.49 — PPT目录40段与真机覆盖更新 — 2026-10-27

- 用户指定目录上限40段；`MAX_IMAGE_TIME_RANGES`改40，管线10避免复用旧目录检查点。密集截图仍合并为少量范围，字幕稀疏时间锚、上传开关与40张图片上限不变。
- `build/classroom-40-range-final.txt`生产整理管线249项及请求538项通过，包括200张稀疏截图恰好40段且计数不丢、密集1000张仍仅一段、无逐帧路径/时间戳、原两轮/审计/续跑/授权与40图上限；仅本地HTTP合成响应，无付费AI请求。
- `build/release-0.15.49-build.txt`正式release与lintRelease通过；固定`build/candidates/0.15.49/Chrona-0.15.49.apk`为3200290字节、211/0.15.49、原签名且不可调试。经10.195.39.39:36009正常保留数据覆盖V2527A成功，读回211/0.15.49，原首次安装时间保留；未继续操纵用户页面。安装及版本回执为`build/release-0.15.49-phone-{install,version}.txt`。
- 前述0.15.48头部修复、图片判断审计及跨日修复一并随本版安装。未重新调用模型生成用户已有总结；GitHub正常认证仍token无效，尚未推送或发布远端。

## 0.15.48候选 — 监视头部、图片判断与跨日时间 — 2026-10-27

- 「监视」外仅刷新，设置与分隔连续收宽且隐藏起即停用。`build/classroom-header-{400-light,400-dark}.txt`、`build/classroom-header-final-320-{light,dark}.txt`四档各25项真实Activity/触摸/逐帧检查通过；包括48/97dp宽度、反向、阻塞本地读取无取消X和原运行设置入口。320初轮失败是夹具选择同名不可点击标题，修正为仅选可点击节点后通过，未据此改生产UI。压缩JPEG在`build/classroom-header-evidence/previews`，同一独立UI审核已亲看四档并签收。
- `build/classroom-header-load-check.txt`生产加载方法20项通过，区分cache/local与真实remote、空缓存fallback、失败和取消；公共库1530、UI结构规范检查通过。模拟器证据不能代替本版本真机更新后的表现。
- `build/classroom-image-policy-final.txt`真实生产整理管线、合成图片与本机HTTP响应221项及AI请求538项通过。覆盖最多12段时间范围、密集/稀疏图集、原文与时间锚保留、40张总上限、逐区间依据、请求/不请求/权限关闭/无图/无匹配、无效响应保留原始日志但不存成功草稿、两轮与失败续跑。没有调用真实收费模型，提示词语义效果尚无真实模型准确率证据。
- `build/cross-day-time-audit-final.txt`请求538、归一化59、排期418、时间审计43、自动写入17项通过。复现原审计的11/21–22日期范围；保留结束日期及高存疑补钟点，明确钟点/全天/矛盾日期/夏令时回归通过，未修改用户历史数据。
- 0.15.47已在V2527A正常覆盖升级至209，原首次安装时间保留、现有课堂历史可见，专用课堂探针已卸载。0.15.48最终`build/release-0.15.48-final-build.txt`的release及lintRelease通过；固定资产`build/candidates/0.15.48/Chrona-0.15.48.apk`为3200266字节、210/0.15.48、原签名、不可调试、30秒、无无障碍服务，检查在`build/release-0.15.48-artifact-check.json`。最后连接地址超时10060、ADB仅模拟器，gh认证仍token无效；尚未安装真机或发布远端，未宣称新包真机表现通过。

## 0.15.47候选 — 单次占位、课堂阅读与短窗口 — 2026-10-27

- 单次编辑与AI公告更新均使用稳定系列身份及原始日期。`build/occurrence-device-final-backend.txt`真实SQLite/CalendarProvider33项、`build/occurrence-merge-final.txt`合并340项通过，覆盖异常提案同事务持有、确认后更新、本地审计、模板与其它实例不变、重复确认和失败回滚；未调用付费AI。
- `build/occurrence-device-short-ime.txt`实际320dp/150%/500dp窗口及键盘15项；`build/occurrence-web-final.txt`网页18项及四组合渲染通过。单次编辑器继续复用普通表单，深浅主题对比和窄窗口操作已独立复审。
- `build/responsive-accepted-400-300-dark.txt`、`build/responsive-accepted-320-420-light.txt`、`build/responsive-accepted-landscape-dark.txt`和`build/responsive-medium-600.txt`验证首页/日历/课表的数据滚动、短窗口工具区、侧边导航、日期完整性及恢复。925dp为真实横屏，320/400/600dp为实际有界窗口，不能代替实体手机OS分屏验证；压缩图在`build/responsive-evidence/previews`。
- 课堂实际925dp横屏reader65项通过（`build/classroom-transition-diagnostic.txt`），横屏列表/搜索/双字段排序/分页/共享设置1130项通过（`build/classroom-accepted-landscape-browse.txt`）。400×420dp/150%浅色整套reader最终65项通过（`build/classroom-final-bounded-reader.txt`），包括追加和修订保持Y、切换和重建恢复、实际MOVE邻图跟手、双击/双指/放大平移、系统文档保存、空白关闭及UUID图片入口。
- 普通400dp深色和短窗口最初均完成读取/图库行为，但入场帧断言失败；公共`enterPage`改为首次布局绘制就绪后启动，时长、距离及曲线不变。两个尺寸的真实入场专项各6项通过（`build/classroom-first-frame-{bounded,portrait}-entry.txt`），短窗口随后完成上述整套65项。旧失败保留，不宣称旧报告通过。压缩静态证据为`build/current-classroom-ui-evidence/previews`，PNG原图未用于视觉工具或上传。
- 独立审核指出待首帧入场不能被`finishSwap`取消，已用统一pending撤销修正。真实附着窗口测试覆盖首帧前取消和PreDraw后取消已排队start；普通竖屏8项通过（`build/classroom-final-cancel-portrait-entry.txt`）。最终短窗口完整67项通过（`build/classroom-final-reader-accepted.txt`）；中途一次系统保存器仍持焦点的注入拒绝保留，夹具改为等待真实Viewer恢复焦点，不向其它窗口补发触摸。
- `build/ui-library-first-frame.txt`公共库1530项通过；专门UI审核亲看三档压缩JPEG，取消P2修复后复审签收，受检范围无剩余确定缺陷。Debug lint及初次Release lint通过；正式209/0.15.47构建与资产检查见后续记录。用户要求的全局图片压缩规则已写授权记忆更新，未改写历史PNG。
- `build/release-0.15.47-build.txt`正式release和最终lintRelease通过。固定资产`build/candidates/0.15.47/Chrona-0.15.47.apk`为3196266字节、com.donglan.chrona、209/0.15.47、不可调试、原证书，manifest不含无障碍服务，正式胶囊30秒；`build/release-0.15.47-artifact-check.json`记录资产检查。安装点ADB只有模拟器、没有手机；GitHub正常认证仍报告token无效，因此没有宣称真机更新或远端发布成功。

## 0.15.46 — 课后补全与自动总结恢复 — 2026-10-26

- 正式素材完整且仍在当前监视范围的旧完成记录，缺少自动总结任务时补入队；已有完整总结不再调用AI。停止范围、其它账号和未知请求结果保持原边界。
- 手动与监视共用`prepareComplete`，正式字幕/PPT清单替换临时观察，匹配缓存后执行同一校验及去重。离线整理186、隔离原生auto51/pipeline19通过，独立后端复审通过；未调用付费AI。
- release与lintRelease通过，固定`build/candidates/0.15.46/Chrona-0.15.46.apk`为3170426字节、208/0.15.46、原签名。19:31经正常安装覆盖实体手机并读回版本；已保留三条监视规则，两条已有总结读回complete。未声称验证整堂真实后台课流。
- 只读近期日程审计：49条收件、9轮解析、17候选、4次图片输入。原始私有数据只保存在build，不提交。两份课堂总结均未请求或选中图片，实际图片分析为零；没有因此更改上传授权。

## 0.15.45候选 — 课堂阅读、自动总结与移除胶囊无障碍 — 2026-10-26

- 当前为已验证的本地候选，源码已本地提交，尚未创建正式标签或Release，手机仍是中间版0.15.44/206。正常更新所需ADB重新失联，重连被拒绝且未发现mDNS端点；用户要求无人值守、不再询问连接。原生整堂学校流及新包的vivo退出/亮屏行为仍待真机验证。源码推送的凭据助手无响应，已停止本次推送进程；gh auth status确认现有GitHub token无效，未推送成功。
- `build/classroom-client-final.txt`：生产学校客户端95、网络错误44；`build/classroom-images-final.txt`：29项，包含完整回放替换临时字幕/图片清单、保留正确raw编号、匹配图片免重复下载。最终清单与直接下载一致，不把暂存直播片段混入完整字幕。
- `build/classroom-final-auto.txt`：真实持久队列与Session受控generator36项；完整材料后入队、默认文字、显式图片授权快照、去重、配置失败、未知结果不重发、范围/账号取消及timeout/凭据更新时的停止准入均通过。`build/classroom-final-pipeline.txt`：19项增量材料/PPT迟到/空观察/完整升级与自动队列；无真实付费AI调用。
- `build/classroom-final-live.txt`：10项真实Android控制器＋受控传输；陈旧stage2且材料请求阻塞时仍连接，熄屏34秒连续追加且PPT不重连，断线恢复、fresh非直播关闭、旧listener迟到消息不写入、再次恢复及停止后清理线程通过。学校真实短时WSS已验证可用，不扩大为本候选整堂后台证据。
- 实体旧版诊断找到 `ClassroomMonitorService.onDestroy → Slot.close → ZjuClient.close → TLS evictAll` 主线程网络崩溃，曾导致胶囊辅助服务enabled但未绑定。新候选的live客户端清理改独立线程；`RequestControl`取消同步置标记和摘连接、后台断连。离线 `AiRequestCheck`494、LinkFetcher、NetworkSafety线程/慢断连/身份及手动与monitor的WorkService合同通过。独立backend补审关闭原两项停止/取消缺陷。
- 课堂UI：400dp/1.0深浅、320dp/1.5浅完整各767项；最终图片可见边缘圆角修正后，这三组reader各补44项。320dp/1.5深最终完整768项。真实输入覆盖搜索/返回/重建、两字段双向排序/分页、字幕追加/修订保持Y、切换动画、PPT日期栏与图片横滑、底部Dock长按/甩动、既有全屏缩放/保存及UUID附件入口、图片上传授权默认关和持久化。中间WindowManager锁等待ANR保留在诊断，不作通过证据；恢复重跑成功。
- `build/classroom-polish-evidence/previews`为压缩渲染证据，原PNG保留未用于上传。主代理亲看窄屏大字，独立UI审核亲看四组合并签收本轮课堂范围。
- 按最新决定删除生产胶囊无障碍服务/身份编码/manifest声明/XML/字符串和设置入口；只移除点击60秒延长，保留新通知/亮屏30秒，并由自有Activity生命周期增加退出拾时30秒。不监听其它应用，不能保证其它应用回桌面的单独触发。控制器41、5秒变体16、生命周期6、提醒工厂80、vivo协议111、通知状态650和后台提醒检查通过。设置页四组合各31项真实Activity断言与压缩图独立UI复审通过。
- `build/release-0.15.45-build.txt`：assembleRelease、lintDebug、lintRelease通过。固定候选 `build/candidates/0.15.45/Chrona-0.15.45.apk`，3169902字节，包名com.donglan.chrona、207/0.15.45、不可调试、原签名；最终manifest无BIND_ACCESSIBILITY_SERVICE。该文件尚未安装/发布，不分发旧0.15.44产物。

## 0.15.43 — 连堂显示与课堂持续监视 — 2026-10-26

- 真实账号只读缓存证实线性代数与数字音视频的两段安排分别为13:25–14:10、14:15–15:50，同日同地且日期集合一致。展示合并不改Occurrence、学校缓存、源事件或统计。Timetable20场景与学校投影检查通过，新增覆盖短课间、不同周／地点、真实重叠、午休、缺课及课间其它课程的真实日期。
- 官方公开前端确认PPT使用page／per_page；真机大英回放为502条字幕、61条PPT记录，默认只返回10条。当天实时课的回放接口返回10002「未查询到语音数据」，实时地址由get-sub-info给出。安全WSS及Glue协议在真实课程20秒收到46／37帧；流中的字幕时钟为epoch毫秒，未将HTTP地址降级请求，未输出密码、Cookie或实际课流URL。
- `build/monitor-client-regression.txt`：实际客户端95项、网络错误44项通过；包含PPT完整分页及重复页拒绝、流地址与课堂身份边界、非直播不订阅教室后续课堂、单侧材料失败。图片缓存／实时帧24项通过；`build/monitor-summary-regression.txt`原整理管线166项继续通过。
- `build/monitor-native-final.txt`：隔离Android36原生41项通过。真实Activity／SharedPreferences／JobScheduler／FGS＋受控网络传输验证单次与后续范围、完整材料归档、稳定图片身份、空结果保留、上翻不跟随、触摸回最新、真实recreate恢复历史位置±3px，以及菜单实际选择两种监视并分别停止。持续socket在离页熄屏34秒期间追加字幕，PPT轮询不重建连接；主动断线后重连，停止关闭连接及前台服务。此条是受控传输证据，与上条学校真实短时流分开。
- 深浅主题400dp／1.0、320dp／1.5实际渲染与触摸通过；压缩图位于`F:/Temp/chrona-monitor-review`。独立专门UI审核发现的重建位置P2已修，菜单实际操作证据已补，复审签收。回最新操作使用公共固定绘制层，避免可选择正文焦点遮挡；公共show入口保留原背景和安全区，新增showFixed供独立滚动阅读页复用。
- `build/release-0.15.43-build.txt`：正式release及双lint通过；固定APK为3156422字节，已核对`com.donglan.chrona`、205／0.15.43、不可调试和原证书。
- 实体V2527A于13:02通过正常ADB保留数据覆盖升级成功，读回205／0.15.43、原首次安装日期仍为9月26日；首页原有待办和课程正常显示，AgendaReminderLiveService前台运行、AtomicCapsuleAccessibilityService已连接，安装后crash缓冲无新记录。已删除本轮15个公开静态临时文件并卸载探针。用户正在使用手机，未继续操作其界面；实体手机主包整堂持续流、后台存活和更新页手动检查尚未实测，学校短时真实连接与隔离原生受控后台证据见上文。
- 源码c1e6d37b0fa7950a12df24bb9f1ba9aa15a31ef3与标签v0.15.43已推送；正式Release为latest，公开元数据版本正确、非draft／prerelease，APK为3156422字节、下载HTTP200及Content-Length一致。`build/release-0.15.43-public-check.json`记录公开检查，固定资产与真机安装使用同一候选。

## 原子岛首次配置说明与一键工具 — 2026-10-24

- 主应用仍为0.15.42／204。本轮只增加文档、配置脚本及独立辅助APK；辅助包`com.donglan.chrona.vivo.setup`以正式包为目标，仅编译`app/src/vivoSetup`，不包含通用测试套件。操作固定为本包`CHRONA_AGENDA`，默认runner模式只读，写入时临时采用shell权限并立即释放，普通身份前后读回；失败尝试恢复原可用状态。
- `build/vivo-setup-offline.txt`：脚本25个模拟ADB场景通过，包含自动选机、未授权／离线／多机／非vivo／未安装目标前置、安装拒绝与无成功回执、三种操作、严格回执、辅助程序清理及WhatIf；实际CMD入口在含中文空格路径下成功／失败退出码两项通过。旧配置分支4项回归继续通过。
- `build/vivo-setup-helper-check.txt`：原辅助程序源码以框架替身编译执行16场景，覆盖默认只读、模式与包名／厂商边界、无权限／初读失败不写、幂等、启用／关闭、未确认及写入异常恢复、shell身份始终释放。模拟框架证据不作为其它固件通过证明。
- `build/vivo-setup-build.txt`：受限release AndroidTest最终构建通过；专用APK仅包含runner、Java集合回移与生成R／BuildConfig，不包含测试夹具。签名与既有正式版证书一致，目标包`com.donglan.chrona`，安装显示名“拾时原子岛配置”。
- 实体vivo V2527A／PD2527_A_16.0.19.3.W10：Status与最终Enable均普通身份读回`before=true, enabled=true, changed=false`，没有关闭既有场景；实际脚本完成辅助程序安装／运行／卸载。日志`build/vivo-setup-device-status.txt`和`build/vivo-setup-device-enable-final.txt`。本轮没有在未配置手机上首次注册，也没有关闭现有通知或新增日程；写入接口沿用此前初次配置的已验证调用，新增恢复路径以替身检查覆盖。
- README、用户指南与功能文档明确：逐机首次需ADB、不随APK分发、配置后日常无需电脑或保持USB调试；工具只配置通知，不启用拖放直达名单。不同固件仍可能拒绝隐藏接口，保留普通提醒。
- 工具源码9b4078dc818ba0f8b1f6da052bb437fef82080af及独立标签`vivo-setup-0.15.42`已推送，工具Release公开且非latest。ZIP4520720字节、11个必要文件，包含SDK的ADB37.0.1与NOTICE；解压后ADB实际运行、脚本WhatIf、辅助APK原证书检查通过，SOURCE.txt匹配工具提交。公开下载HTTP200、Content-Length4520720；应用latest仍为v0.15.42，原应用APK不替换，正式应用Release另补首次ADB要求与工具下载链接。

## 时间确定性审计、主动排程及设置／通知收尾 — 2026-10-23

- 同批新增重新解析自动清理：原生与网页统一 `CaptureReparser`，先校验再删除本收件独占的系统事件；共享／永久归档引用、源输入及历史审计保留。查询旧候选、状态和清空操作处于同一SQLite事务，提交后才调度；外部部分删除无法回滚，失败不排队并允许重试，调度失败另标FAILED。
- `build/reparse-web.txt`：实际Edge加载正式LAN页面，模拟手机传输；已关联日程可重新处理，四组深浅／1280px与320px实际150%弹窗文字渲染、取消零写入、确认请求、排队禁用及调度失败状态／版本刷新通过。压缩图 `F:/Android/temp/reparse-web-evidence/confirm-{light,dark}-{1280,320-large}-view.jpg`。不把网页mock结果当系统日历验收。
- `build/reparse-linked-calendar.txt`：生产关联清理器＋精确TaskStore引用SQL＋真实SQLite，29场景／211断言通过；含共享／归档保护、缺失事件、provider拒绝与异常及提交失败。LAN安全83、作业取消与生命周期专项继续通过，见 `build/reparse-{lan_security_check.py,job_execution_check.py,lifecycle_motion_check.py}.txt`。
- `build/reparse-device.txt`：隔离Android16实际SQLite／CalendarProvider／MediaStore共84断言通过；独占与重复引用清理、共享及归档保护、缺失事件、源文／图片与文件字节／历史审计保留，空输入／无配置／加密配置损坏／忙碌／学校托管／旧版本前置拒绝，以及provider／SQL／调度失败重试。日历权限拒绝用Context gate、provider拒绝与异常用transport注入；正向调度为no-op，无真实AI请求。
- 原生320dp／1.5初轮发现「重新解析」按钮截掉末字，已在公共 `UiDialogs.actionRow` 修复完整字宽测量及横／纵自适应，自然高度不缩字。`build/reparse-ui-library-check.txt`真实方法1530断言，含临界宽度、恢复及回调；`build/reparse-native-{light,dark}-{400-1.0,320-1.5}.txt`四组各22断言，实际Layout全文、无ellipsis、视口内、方向及触摸取消保留数据。最终压缩图 `F:/Temp/chrona-reparse-action-row-final/reparse-{light,dark}-{400-1.0,320-1.5}-preview.jpg`，主代理已查看窄大字浅色和正常深色。
- 用户随后将重新解析确认正文精简为「这将替换已有日程」，原生／网页统一且无附加标点。`build/reparse-short-native-{light,dark}-{400-1.0,320-1.5}.txt`四组各23断言及网页专项通过，压缩图 `F:/Temp/chrona-reparse-short-copy-final/reparse-{light,dark}-{400-1.0,320-1.5}-preview.jpg`；业务84项与公共1530项证据仍适用。
- 专门UI审核亲看最新原生／网页八图，原生重新解析与公共行整体审核、短文案补审均通过；设置退出与结果通知既有签收继续有效。

- 候选0.15.42／204、DB16、schedule-v17。确定与存疑都要求七字段时间依据，模型原始依据与本地实际字段、校验原因分开留存；每次请求尝试、日程及run＋plan关联持久化，并经已配对LAN分页查询。用户随后撤销截止日22:00–23:00默认，正式提示词与执行分支均已删除；单项仍利用整个可用周期，较大任务明确要求提前分阶段、预留修改提交。
- `build/time-audit-ai-final.txt`：实际请求契约494、归一化59、排期418、时间审计35、自动写入16通过。覆盖确定／规则补全／自由建议、日截止与执行独立、撤销专用晚间默认、日期缺钟点的具体建议、缺依据拒绝；本地补日期、冲突日期及结束日期继承不再残留错误explicit，明确日期仅钟点存疑不会误传。均为合成模型响应，不代表真实服务语义准确率；未付费调用或重解析用户记录。
- `build/time-audit-persistence-final.txt`：生产TaskStore＋真实SQLite32项通过，包含v15→v16保留原文／候选、无伪造旧依据、确定与存疑保存、去重原项关联、重解析历史、精确方案与日程查询、失败回滚、中断／重试／恢复收口及快照复制。`build/time-audit-store-checks.txt`关联合并336、双类型102、快照16、审核状态45通过；快照覆盖17表及非默认审计数据。`build/time-audit-api.txt`实际GET分支＋真实SQLite13项通过，HTTP传输边界不在该专项内。
- 配置偏好22项与配置备份通过（`build/time-audit-preferences.txt`）；备份恢复104、作业取消／串行重调度、生命周期恢复通过（`build/time-audit-{backup_restore_check.py,job_execution_check.py,lifecycle_motion_check.py}.txt`）。quick 8/8通过，沿用本批未改职责的有效基线，不重复整套。
- SettingsActivity的八字段脏表单保护，在隔离官方Android16模拟器400dp／1.0与320dp／1.5、深浅四组各63断言通过；涵盖标题／系统返回、改回原值、取消／外部关闭、放弃不写、校验失败、保存成功及Activity重建恢复。日志`F:/Android/temp/settings-exit-{light,dark-400-1.0,light-320-1.5,dark-320-1.5}.log`，专门UI审核及主代理查看压缩JPG后通过；旧Android、真实边缘手势与进程死亡未设备实测。图片即时动作保持原独立语义。
- `build/result-notifications-device.txt`：隔离Android16真实NotificationManager＋SQLite64断言通过。自动全确认不再发结果通知，手工确认后的TaskStore.close后台清理已有通知；混合剩余项、失败／处理中／排队／空结果与未关联活动草稿保留，孤儿和旧结果频道清理，其他四类频道同ID不同tag保留，重复清理幂等。原文、候选、成功解析审计及关联条目读回完整。无样式变化、无实体手机操作；剩余结果标题专项6项通过。
- 独立只读时间审计检查发现并促成修正本地字段确定性与中断running状态两项P2，补审均关闭；设置退出独立UI审核通过。所有设备写入只发生于隔离v33包与合成数据，未操作真实账号或手机。
- 加入重新解析与共享按钮修复后的正式debug／release assemble及lint成功，`build/reparse-final-build.txt`。实际APK签名与上版已验证证书一致，包名正确、204／0.15.42、DEBUG=false，DEX胶囊常量仍为30000／60000ms；`build/release-0.15.42-artifact-check.txt`。尚未覆盖实体手机或验证应用内更新。
- 最新短文案正式分发候选为 `build/candidates/0.15.42/Chrona-0.15.42.apk`，3134070字节；此前3132654字节旧候选已替换，不用于发布。
- 已提交并推送源码823f86d，正式标签v0.15.42固定同一提交；[0.15.42 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.42)已公开并成为latest。唯一资产Chrona-0.15.42.apk为uploaded／3134070字节，公开下载HTTP200且Content-Length一致；记录 `build/release-0.15.42-{publication.json,download-head.txt}`。未在实体手机覆盖安装，应用内更新交互未新增实测。

## 0.15.41正式发布与验收 — 2026-10-23

- 用户明确接受当前固件约200ms原生收起过渡，要求恢复30／60秒并Release。候选固定code203／0.15.41，使用release而非capsuleTest；已审核的业务源码不再改动。
- `build/release-0.15.41-final-build.txt`：assembleDebug／assembleRelease／lintDebug／lintRelease成功。实际APK元数据为官方包名、203／0.15.41、不可调试；反汇编APK内BuildConfig确认桌面30000ms、点击60000ms。新debug/release与已保存0.15.40安装包签名证书一致，见`build/release-0.15.41-artifact-check.txt`和上版签名记录。无卸载或清数据。
- 19:49:55同手机无线覆盖安装正式0.15.41，普通辅助服务Bound／Crashed空。实际系统设置前台确认后返回桌面，＋24秒完整／＋33秒短形；原生点击及点外关闭后＋55秒完整／＋63秒短形，点击accepted=true。有效图片`release-settings-home-{24,33}-capsule.png`、`release-click-{closed,55,63}-capsule.png`在`build/atomic-event-review/`。第一组Dashboard重入home25／33均短，不用作通过证据。测试未操作真实日程数据。
- 本轮沿用本批已验收的全套基线和后续受影响定向检查，不为发布重复有效整套。独立发布审查未发现生产代码阻断；新增公开文档已去除设备唯一标识、无线地址及真实活动标题，并补充无障碍启用要求和系统过渡限制。手机LAN实链路、付费模型与完全断开ADB的独立运行未新增验收，不把替身或无线验收扩大为这些项目。
- 独立UI审核亲看正式24／33秒及关闭后55／63秒稳定画面，通过本轮30／60秒范围验收；约200ms系统过渡按用户接受保留。正式源码`e0dcedb28831e4d69c7546cd0ec759df659368a6`及标签`v0.15.41`已推送，发布[0.15.41](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.41)。GitHub Latest读回同标签、draft=false、prerelease=false，资产`Chrona-0.15.41.apk`已uploaded，3120586字节，与本地正式产物长度一致。公开下载HEAD实际返回200、相同Content-Length及Android安装包类型。

## 无线恢复安装及真实活动点击窗口复验 — 2026-10-23

- 用户再次反馈点击收起后不保持完整；无线ADB （同一授权手机）核对同一V2527A，初查本服务配置Enabled但Bound空、Crashed含本服务。首次恢复安装被系统明确拒绝，得到用户“重新发起，我会允许安装”后才重试成功，未绕过拒绝。
- 19:18:38实际安装`0.15.41-capsule-test-5s-10s`恢复包，不含无效即时快投；普通辅助服务Bound、Crashed空。使用用户当前真实活动（用户当前真实活动）只做原生展开和点外关闭，未修改活动或创建新记录。
- 先等桌面5秒失效，再点击，约700ms点外关闭。点击＋8秒仍是完整倒计时和长标题，＋11.5秒为图标及“机器”；`ChronaDiag`记录`CAPSULE matched id=4409426 accepted=true`。截图`build/atomic-event-review/wireless-click-{8,11}-capsule.png`、记录`build/capsule-wireless-recovery-report.txt`。`wireless-click-closed-capsule.png`为模糊过渡帧，不用于认定即时稳定形态；不把本次检查说成系统收起200ms过渡已消除。
- 全过程无instrumentation、UiAutomation或手动tick，原后台更新频道关闭状态保留。当前继续保留用户要求的5／10秒测试包，正式30／60配置未变。

## 收起窄黑过渡定位及无效候选撤回 — 2026-10-23

- 用户确认5／10秒计时功能恢复，进一步反馈点击卡片收起后先窄再详细。实际`flash-before.mp4`、即时快投后的`flash-after.mp4`及各10fps接触表位于`build/atomic-event-review/`，均显示关闭先经窄黑形态再扩展；持有3秒后的`flash-held.mp4`同样出现。正式普通服务、无UiAutomation／instrumentation／手动tick参与录制，USB条件。
- 即时快投实验绕过350ms合并刷新／日历读取，保持最新秒文字和原提醒到期；离线控制器及协议检查通过，但真机未消除过渡。专门UI审核明确否决“无闪回”签收，主代理查看后撤回该生产改动及实验专用检查，保留已确认的5／10构建隔离。
- 只读拉取当前手机`/system/priv-app/SystemUIPlugin/SystemUIPlugin.apk`到`F:/Android/ChronaBuildCheck/capsule-host/`。派生`DynamicCapsuleStateController.java:20–33,73–77,243–252`表明展开态4收起必经中间态1；`ContainerAnimatorCommon.java:623–626`使用系统固定过渡宽度，`:1407`设置200ms；`DynamicCapsuleContainer.java:311–336`及`updateContentView.smali`交叉核对展开期不重建左右胶囊视图。主代理已抽读决定性代码。DEX事实为观察结果，它与录屏约200ms的对应关系为解释；不推广到其他固件。
- 已检查协议未见跳过中间态或改变其200ms的应用入口。没有修改系统APK、全局动画或以强制展开阻止用户关闭。用户要求的无闪回效果仍未达到，不再用未验证参数猜测继续试修。
- 用户明确授权创建一条临时活动以供复验，专属source/raw校验的夹具创建task116／candidate423，无AI或系统日历发布。`build/capsule-live-fixture-cleanup.txt`已确认该task与关联候选删除；不删除其他记录。最终恢复原点击刷新逻辑，正式45／短时18／vivo111检查通过，见`build/capsule-final-restored-checks.txt`。

## 活动胶囊复现与短计时测试候选 — 2026-10-23

- 用户确认作业卡片正常、活动卡片收起仍精简。16:23 USB普通运行时，系统服务配置Enabled但Bound空、Crashed含胶囊辅助服务；实时刷新服务仍在，未获得本包Java异常。原子岛截图不用于证明点击链路正常。单独后台更新频道已由用户关闭，本轮读回importance=0并保留。
- 按用户要求新增独立`capsuleTest`变体：桌面5秒、点击10秒，正式debug/release保持30／60秒。版本后缀明确标识测试，同包同签名保留数据覆盖。`build/short-capsule-build.txt`双变体构建通过，生成的BuildConfig已直接检查；`build/short-capsule-controller.txt`执行正式45及短时18断言通过，不以源码替换时长冒充变体。
- 手机16:39安装`0.15.41-capsule-test-5s-10s`成功，安装后普通辅助服务Bound恢复、Crashed空。用户活动16:11–16:34已结束，无活跃Chrona卡片，实际5／10边界仍缺；未将恢复连接称为活动故障已修复。没有修改真实日程或对其它无障碍服务执行开关。

## 通用全周期执行建议（未发布）— 2026-10-23

- `schedule-v16`取消单项截止前6小时至3天限制。提示词按工作量估计执行时长，必要的复杂任务沿用现有分组阶段，预留修改、检查和提交，不使用征文或具体日期特判。
- 本地`chooseExecutionStart`使用整个有效起点区间的中点作为默认目标；长期模型建议过晚时按剩余周期及执行时长提前目标。工作窗口仍搜索整个有效区间，缓冲偏好不导致本可执行的任务丢失。明确执行时间、原始截止、循环锚点与自动确认保护不变，不重排已有记录。
- 只读/受限执行分工的测试代理更新两份夹具并运行`python checks/ai_auto_publish_check.py`：请求493、归一化59、排期418、自动发布16通过。新增10／30／60天task和homework、早期模型建议、过晚建议、较大工作量、唯一临截止工作窗口、恰好可容纳时长及分组保护。主代理已审关键断言及生产逻辑。
- 当前ADB设备列表为空，本轮未安装手机、未付费在线解析；新行为的验证范围为真实Java请求组装、转换与响应解析夹具。正式构建记录`build/whole-period-formal-build.txt`。

## 自动重复日程提示与整轮 UI 收尾（未发布）— 2026-10-23

- 生产候选203／0.15.41新增`DuplicateScheduleReview`：应用级前台revision检查，变化后共用同步锁后台扫描；后台入库不拉起窗口，恢复前台再提示。按内容保存已展示组，取消／外部关闭／来源跳转后不因无关更新重复弹窗。弹窗与主动入口均默认不选，按组确认，未选组不动。网页可见且无其它浮层／上传时检测，旧路由结果丢弃；忽略随时间变化的确认可用态作为提示签名。
- 数据检查`build/automatic-merge-data.txt`：生产Java与真实SQLite336项通过，新增两组选一、空选择、未知组拒绝及未选组完整保留；关联双类型102、备份16、review45通过。保留原宽松匹配、稳定UUID保留项、revision校验和原子事务。
- `build/automatic-merge-browser.txt`：真实Edge＋模拟手机API专项通过，覆盖自动出现、默认不选、取消与无关revision去重、动态确认展示状态不重弹、追加新组只展示新组且计数正确、精确来源导航、过期拒绝／重扫及明确groups提交。稳定390px暗色帧等待公共150ms背景过渡后截取，启用动作computed对比均≥4.5；旧即时换主题截图为过渡帧，不作稳定对比依据。`build/automatic-merge-web-parity.txt`通过，未重跑原全网页已知无关动效失败路径。
- 隔离v33官方模拟器实际Activity与SQLite：`build/automatic-merge-native-light.txt`（400dp／1.0字体／浅色）、`automatic-merge-native-dark.txt`（320dp／1.5字体／深色）均PASS。覆盖前台新入库自动弹窗、每项来源、默认不选、取消与无关原文修改去重、后台新增组不弹、回前台显示新组、只合并选组且来源任务完整。宽度与字体只覆盖Activity，未改全局配置。`automatic-merge-native-scroll.txt`仅补320dp大字体滚到末条来源画面，不重复其它验收。截图`build/atomic-event-review/automatic-merge-{light,dark}.png`、`automatic-merge-dark-{selected,sources}.png`及`build/lan-web-checks/merge-{wide,narrow-dark}.png`已查看。初版“selected”截图取了旧帧；有效深色图已在idle＋200ms后补齐，不能将旧浅色selected图当选中态。
- 专门只读审核`/root/ui_review`发现并促成修正提交期复选框重启用、自动新组数量和手动入口选择缺失；最终源码／稳定暗色／选中态复审通过。随后补审既有紧凑原子卡、真实菜单触摸和成员预览，均签收；整轮UI审核缺口关闭。工具无`agent_type`字段，实际仅按只读check职责约束，未声明已设置角色模型。
- 正式构建与签名、产物元数据见`build/automatic-merge-formal-build.txt`及本轮当前工作单。未安装用户手机、未发布；物理断开ADB后的胶囊独立运行仍缺，保留到用户再次提供设备时验证。

## 前批紧凑卡片、菜单与 USB 胶囊有效证据补录 — 2026-10-23

- `notification_atomic_card.xml`外层垂直内距8dp、完整标题与48dp操作共行；摄像头基础安全带40dp，仍保留真实cutout＋4dp。RemoteViews实际apply短卡约155.56dp，320dp／1.5字体长标题完整；40种密度／cutout及相关合同通过。真机最终图`build/atomic-event-review/compact-native-phone-final.png`、`compact-native-closed-final.png`主代理已查看，关闭图为过渡帧，不证明计时。
- 任务详情Lucide竖向三点图标、48dp热区与整组1.10按压，实际DOWN／UP打开并回落通过`build/compact-click-light-report.txt`。成员预览400dp浅色正常字、320dp深色1.5字体分别通过`build/compact-merge-native-{light,dark}-report.txt`；截图与菜单按压／打开、短长卡图均在上述证据目录。现有有效证据仍适用于新自动检测候选中未改变的这些部位。
- USB正式服务证据：`usb-production-home-{29000,31500}.png`、`usb-production-click-{59000,61500}.png`、`usb-production-click-outside.png`、`usb-production-held-65s.png`及held-dismissed；`usb-auto-seconds-final.txt`／`usb-auto-final-*`为真实AgendaReminderLiveService自动秒级更新；`usb-settings-notifications-on.png`、`usb-settings-returned-{off,on}.png`为设置实际启停返回。均在`build/atomic-event-review/`。不再把这些项目列为待USB验证。
- 手机已交还用户；最后仅临时通知测试，无真实日程／TaskStore写入，通知与临时截图已清理，无障碍Bound且Crashed为空。本轮不操作手机；**仍缺物理断开ADB后独立运行**，辅助程序退出不等于拔线。旧通话胶囊／错误前台画面不能用于Chrona通过或失败判断。

## 胶囊辅助事件接入与离线续修（未发布）— 2026-10-22

- 用户明确授权安装、启用「原子岛胶囊计时」无障碍服务。正式实现只观察桌面窗口包名及`com.vivo.systemuiplugin`内Chrona标题标记，不执行手势／点击，不记录或上传正文；设置入口复用现有通知页面行及系统公开无障碍设置。专门UI源码初审无P0／P1，要求补实际设置页画面及完整边界。
- 真机证明跨包RemoteViews的非视觉Annotation标记可读取，但宿主按Chrona资源ID查询不可靠；已改为仅HOST内256节点／32层的有界遍历。`capsule-tree-build.log`记录release／lint通过，同候选已保留数据安装。`tree-real-service-device.txt`中真实服务将点击期限设为295194484，295194809时投影精简；轮询间隔1秒。`tree-click-open.png`／`tree-click-outside.png`实际查看，支持原生展开、外部立即关闭及关闭后长胶囊保持。本轮辅助循环发送投影，不单独证明正式提醒的边界调度。
- 实际桌面30秒局部证据为`actual-home-full.png`／`actual-home-compact.png`及`capsule-real-service-device.txt`。早先资源查找候选的点击失败不扩大为桌面失败；`tree-held-after-60.png`所在尝试已超过辅助运行期限，无效，不用于验收保持大卡片跨60秒。
- `seconds-production-device.txt`曾调用真实`AtomicCountdownUpdates.tick`，但点击续期断言失败。后续检查已保存的`seconds-home-29.png`／`seconds-home-32.png`发现当时为通话胶囊，前台为Chrona首页而非桌面；该轮不能判断Chrona胶囊点击或30秒行为，不把绑定时序当作已证实原因。探测器新增通信状态前置拒绝，且清理快照异常时仍结束临时通知／删除频道。
- instrumentation启动／结束会终止同包服务，enabled列表不能证明实际运行；验收驱动须在启动后核对服务Bound。最后测试已结束、临时通知已清理、正式服务重新Bound且Crashed为空，随后手机交还用户。离线续修不再自动操作真机。
- 新增`atomic_capsule_observer_check.py`，实际编译生产观察器／标记类，42项通过，覆盖桌面去重、锁屏／解锁、原始点击时刻、延迟出卡、一次消费、外部节点不读正文、超时／销毁、遍历上限和标记身份。空包事件现在忽略，避免假离开桌面后重复续期。Android事件传送／标记传输在此使用替身；通信前置仅验证接线。原控制器45项证据继续有效；新专项纳入统一集合，现109项。
- 新控制器／观察器候选的release、androidTest编译及lintRelease通过（`build/atomic-event-review/offline-final-build.log`）。内部包`Chrona-0.15.41-offline-candidate.apk`确认官方包名、203／0.15.41，APK签名校验通过，未安装真机或发布。随后仅新增／调整测试夹具，生产源码保持不变。
- 隔离v33模拟器的真实`NotificationSettingsActivity`设置行，400dp／浅色／字体1.0、320dp／深色／字体1.5最终各26项通过。使用Activity独立资源配置，模拟器全局仍1080×2376／字体1.0；OEM能力与「未开启／已开启」值为展示夹具。检查标签／值完整、无相交、20dp边界、54dp触控、整行可达，以及真实点按打开公开无障碍设置并返回同一行实例；未启用权限。日志`settings-light.txt`／`settings-dark-narrow.txt`，截图`capsule-settings-{light,dark}-{top,off,on}.png`均在`build/atomic-event-review/`。修正截图取旧帧及夹具未调用`updateControls`的问题，最终相邻开关与真实总开关一致，已查看light-top及dark-on等画面。专门UI代理局部复审通过，无新增缺陷；不扩大为OEM动态权限通过。
- 后续准备发现测试通知ID`0x4348f1`与生产`AgendaReminderLiveService`前台通知相同；已改为`0x4349f1`，保留运行时碰撞拒绝，并增加已知生产ID区分检查。通信检查从仅入口扩展到运行中每秒检查，通话期间退出并清理。观察器42项及两项源码接线通过，正式测试包构建成功，`device-helper-final-build.log`；旧通知ID的测试不作为生产服务并存证明。
- 此阶段的USB边界、跨期限及设置启停缺口已由上方2026-10-23补录证据关闭；物理断开ADB独立运行仍待验收，手机已交还，不再等待旧无线地址。0.15.41未发布，旧强制展开APK禁止分发。

## 模拟器启动脚本与插入交付 — 2026-10-22

- 用户手动将默认分辨率改为1080×2376，已保留。启动脚本损坏UTF-8及中文字符串丢失引号导致ParserError，和虚拟机是否已关机无关。损坏文件备份至F盘日志`script-repair/`，修复后保存UTF-8 BOM。
- `tools/Start-AndroidEmulator.ps1`语法／只读预检通过，实际从关机状态启动窗口成功；Android物理1080×2376、hw.keyboard=yes／lid=no、内建键盘识别及可见窗口句柄已确认，画面`build/atomic-event-review/emulator-started-2376.png`已查看。未模拟电脑键盘实际输入。桌面快捷方式「启动安卓虚拟机」可复用，日志与AVD在F盘。
- 手机端点已仅修改`chrona_ai_settings/base_url`为`https://api-slb.jiji.cc`并回读，证据`build/atomic-event-review/endpoint-update.txt`；密钥／模型未变，无付费或网络测试。
- 用户指定浙大体艺APK2.9.7／116，安装到当前虚拟机实际返回`INSTALL_FAILED_NO_MATCHING_ABIS`／-113：仅ARM库，当前x86_64 AVD无ARM转译。有限标准位置检查未找到现成转译模拟器；未改APK／AVD、未下载其它模拟器。完整诊断`F:/Android/ApkInstallDiagnostics/zju-sports/diagnosis.md`。此为安装尝试的明确负结果，需要其它运行环境才可继续安装。

## 共享日历关联与恢复并发（未发布）— 2026-10-22

- 抽取 `LinkedCalendarEvents` 供原生、网页、完成提醒、重复规则、学校更新与同步删除共用。修改共享事件时拆分当前候选，删除保留其它候选及永久合并引用；新事件仅在最外层 SQLite 提交后保留。已有独占事件的原地修改／删除仍跨两个存储顺序执行。
- 共享关联专项执行真实生产 helper、实际 TaskStore SQL 和 SQLite：29 场景／211 断言通过；完成提醒48项、学校更新68项通过。同步锁、LAN同步接口67项、重复投影33项、日历重复20项、编辑冲突及浏览器 parity 通过。独立只读审核发现同步错误透传仍匹配旧文案，已对齐为实际删除失败提示。
- 隔离模拟器 `check=linked_calendar` 实际操作 HTTP、SQLite 与 CalendarProvider：共享编辑、完成、删除、撤销时间、最后引用删除；用延迟外键触发真实 COMMIT 失败，确认新事件清理、本地关联回滚及同请求重试成功。证据 `build/linked-calendar-device-result.txt`，夹具入口 `LanAccessInstrumentation.checkLinkedCalendar`；不触及真实手机。
- 旧解析/恢复竞态已以真实处理控制流复现：扫描 queued 后 worker 开始，取消调度返回时 worker 仍持有旧数据库及输出。现 `LiveDatabaseAccess` 覆盖 TaskStore 与解析执行生命周期，恢复在业务锁内仅尝试独占，忙则立即拒绝；恢复期间解析延后，普通读者等待，独占覆盖回滚与身份重置。恢复仅取消解析作业，保留其它周期任务。
- 并发专项编译真实准入类并提取实际 job/restore/bridge 控制流，14 场景／124 断言通过；原 job_execution 的取消、重调度与身份清理通过。独立审查发现 `AndroidSyncData` 构造失败泄漏句柄，已在构造失败及附件清理异常时确保关闭，并通过局部复核。
- 最终隔离 Android `check=backup_restore` 通过：真实数据库持有时拒绝恢复；真实同步附件目录阻挡和数据库打开失败后可再取得独占；恢复读取失败后释放；并发读者等待直至新数据安装。大于4MiB课堂及真实ZIP/SQLite往返、提交后维护失败保留和自动同步关闭仍通过，日志 `build/restore-concurrency-final-device-result.txt`。首次故障注入改至 Android 实际调用的 getDatabasePath，未用未触发的旧钩子宣称通过。
- 统一入口纳入共享关联与恢复并发专项。整轮 `full` 为104/108通过（`build/project-review-full-checks.log`）；4项旧夹具缺少实际新增接口，补齐后逐项通过：按压47、通知80、首页切页46、日程刷新374。全部108项已有有效通过证据，保留原断言，未重复整轮运行。
- 最终生产候选 `assembleRelease`／`lintRelease` 通过（`build/project-review-final-build.log`）。内部 APK `build/project-review/Chrona-0.15.41-data-reliability.apk`，3109150字节，未发布或覆盖真机；胶囊进入桌面30秒／点击60秒的事件接入仍单独待完成。

## 课堂条件入口与学校说明（未发布）— 2026-10-22

- 折姜星辰关闭时，工作区移除课堂页与导航项，课表直接切到日程；稳定页面 ID 与可见位置分离，直接课堂入口及旧选中状态回课表，设置中的课堂入口隐藏。启停时重新检查条件并保留合法页面状态，停用课堂会话不再保留。学校设置显示「浙江大学专属功能」。
- 隔离 v33 模拟器浅色 1080×1920／density 411／font 1.0，与深色 900×1600／density 411／font 1.5 各 41 断言通过：真实导航点按与标题滑动、页面不创建、直接路由、启停恢复、真实学校开关及返回工作区、说明完整显示。证据 `build/classroom-visibility-supplement-{light,dark}.txt`，截图 `build/classroom-visibility/`。
- 专门只读 UI 审核通过静态实现、实际画面与设置路径；指出两项导航动效检查不应允许出屏，收紧后仅补跑首尾长按、拖出释放、双向快速甩动，所有采样帧高光在屏幕内，日志 `build/classroom-visibility-motion-{light,dark}.txt`。未修改公共动效参数；启用三项导航的选中截图等待动画结束后采集。
- `assembleRelease` 与 `lintRelease` 通过（`build/classroom-visibility-release.log`）；候选内部包 `build/project-review/Chrona-0.15.41-school-visibility.apk`，3104038 字节，未发布／未覆盖真机。新设备夹具受隔离守卫保护，模拟器尺寸、字体与偏好恢复。
- 离线统一入口显式补入 9 项专项；全部新增项目已运行通过。补齐过程中修正两处旧夹具：Dock 表面断言改读实际 `UiSurfaces`，页面历史替身补作业定位回调并验证进入日程触发。它们不更改生产行为。

## 第五版历史构建 — 2026-10-22

- 按用户要求从 `1ef2426` 独立导出原始源码，0.5.0／versionCode 5；`F:/Android/ChronaHistoricalBuilds/0.5.0-1ef2426/build.log` 为离线 debug 构建成功证据。APK 元信息及签名校验通过，交付桌面 `C:/Users/DongLan/Desktop/拾时-Chrona-0.5.0-第五版.apk`（934576 字节），未安装手机，不作为现版本升级包。

## 项目巡检首批 — 备份提交边界与容量一致性（未发布）— 2026-10-22

- 通览产品／开发规范及模块入口，对原生首页／详情、AI 与课堂后台、学校同步、数据保护、LAN 接口、共享同步和检查集合进行分区审阅。首批选择两个有控制流证据的备份缺陷；共享日历关联与单条发布补偿问题留在当前工作单，不将本轮当成全项目无缺陷证明。
- 旧代码故障注入复现 10 个失败场景：删掉旧数据库回滚点后，提醒状态／刷新／课堂调度异常进入回滚并删掉当前数据库；合法大于 4 MiB 的 JSON 被隐含读取上限拒绝，导出还允许超过声明容量。红测记录 `build/backup-restore-check/red-result.txt`。
- `ChronaDataBackup` 将提交后维护移出回滚区，各项失败保留恢复结果并报告具体警告；提交前仍执行回滚，缺少旧副本时保留幸存数据库。清单 40 MiB、课堂正文 16 MiB 均按 UTF-8 字节在导入与导出端检查。
- 新专项 `checks/backup_restore_check.py` 已纳入 `full`：21 个场景、104 项断言通过，执行生产恢复控制流／实际调用处和真实临时文件、ZIP；覆盖提交前后故障、缺回滚点、大正文、精确上限、超限 1 字节、多字节内容。Android／SQLite 在该专项中为替身，绿测 `build/backup-restore-check/green-result.txt`。
- 隔离官方模拟器 v33 包执行 `check=backup_restore`，实际调用 `write → prepare → restore`，使用真实 Android SQLite 与 ZIP。超过 4 MiB 的中文课堂正文、收件原文、解析输出均往返成功；故意使提交后课堂清理调度抛异常，恢复内容仍完整且有警告。目标隔离偏好的学校启用与 WebDAV 自动同步由 true 变为 false，未配置任何凭据。最终证据 `build/project-review-backup-device.txt`。夹具开发期间修正了测试 Context 对必备系统服务及绝对数据库路径的不完整模拟，最终使用真实系统服务，仅在指定清理调用处注入故障。
- 配置备份／22 项日程偏好、10 项学校备份、16 项隔离条件及三处写入入口前置通过。release 构建与 lintRelease 通过（`build/project-review-release-build.log`），隔离 debug/androidTest 构建通过。独立只读审查未发现本轮阻断缺陷，补齐了自动同步状态转换断言及功能文档证据范围。
- 无 UI 布局修改，不操作真实手机、账号、外部附件提供器或系统日历写入。未修改版本、未发布；原子岛任务按用户要求暂停，点击 60 秒仍未完成。旧原子岛候选不得作为本轮产物分发。

## 0.15.41候选修正 — 恢复原生大卡片关闭（未发布）— 2026-10-22

- 用户重申：点击起一分钟仅控制胶囊完整显示，绝不意味着强制展开或阻止大卡片点外部关闭。原候选已安装，在用户vivo上复现点击展开后约0.5秒点外部，卡片不收起；证据`build/atomic-capsule-30s/device/early-outside.png`。本机固件强制展开时普通外部关闭不重置点击状态，默认强制期限5秒；之前单次稍后关闭成功不足以验收。
- 试验点击后追加普通刷新清除forceShowCard，虽然能关闭，却在没有外部点击时也自动关闭，`fix-held.png`反证该方案，已撤回，未发布。
- 当前移除应用点击拦截与强制展开，恢复原生islandClick=0及原详情PendingIntent。release构建、lintRelease及33项生产controller检查通过，原签名覆盖安装成功并启动。当前包为`build/atomic-capsule-30s/Chrona-0.15.41-native.apk`，构建日志`native-dismiss-build.log`；早先固定候选不是此修正版。
- 真机`native-held.png`为原生点击后2秒保持展开，`native-outside.png`为点击外部后0.7秒已收起；未写入日历或合成作业。原生出卡分支不发送应用clickResp，受保护状态广播不可作为普通应用点击回调。点击60秒要求仍未实现，保留在当前工作单，不以恢复交互当作整个交付完成。

## 0.15.41候选 — 原子岛胶囊限时精简（未发布，待真机）— 2026-10-22

- 用户最终规则为亮屏后完整30秒、点击胶囊后从点击起完整60秒，随后仅显示标题前两个字。卡片保留完整信息，点击桥接以不可变URI身份接收，再由同一通知请求OEM展开；不使用需要系统签名权限的收起广播，也不以showTime移除整条通知代替精简。
- `AtomicCapsuleController`统一通知创建、普通刷新及秒级刷新投影，单调时间期限持久化并按开机身份隔离。主线程边界定时配系统闹钟；点击入口先捕获时间，WORKER排队不续期。过期／替换的旧点击拒绝；只在点击时请求展开，秒级更新清除出卡命令。作业有效期内复用已有服务观察亮屏，普通刷新保持原低频，不增加每秒数据库轮询。
- `python checks/atomic_capsule_check.py`执行生产controller，37项通过：精确边界、刷新不续期、双触点精简、全文与基线保留、点击单次出卡、URI无extras身份、旧／已关闭通知拒绝、进程恢复、恢复作业重绘、排队点击仍取接收时间、熄屏取消闹钟、权限撤销、开机隔离与Unicode字符。发布者/秒级/结束清理接线另作源码契约检查。该测试的时钟、Android传输及通知管理均为替身，不是OEM渲染验证。
- 受影响原适配111项、live服务生命周期/调度、原子卡资源与背景contract、作业存储检查均通过。独立check审查修复了仅作业每秒整链刷新、恢复只计时未重绘、点击extras剥离与排队延后计时，最终代码审查通过。未新建权限、唤醒锁、账号或系统配置。
- 最终debug/release/androidTest编译与双lint通过（`build/atomic-capsule-30s/final-build.log`）；固定待验APK `build/candidates/0.15.41/Chrona-0.15.41.apk`为3103022字节，203/0.15.41、原签名证书、不含application-debuggable，证据`signing.log`/`badging.log`。没有连接或操作手机。
- 按用户本轮要求不操作手机；0.15.41只准备代码与待验构建，不创建发布标签或公开Release。仍须在用户vivo验收30/60秒、两字后的实际宽度与状态栏避让、点击展开/详情、秒级更新、锁屏/进程恢复和原有十分钟/红点/完成关闭。无精确闹钟权限且进程退出时系统可能推迟精简；错过亮屏而恢复时，所有旧窗口已过期则从恢复补30秒，不声称还原未收到的历史亮屏时刻。

## 0.15.40 — 网页主从详情与编辑保护 — 2026-10-22

- 源码`18d974f700e48c6649a622b18b868db27acc07fd`与不可变标签`v0.15.40`已推送；[正式Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.40)于12:09发布为Latest，发布页确认同一提交、四条用户更新与固定APK（2.96MB），截图`build/release-0.15.40-published.png`。未追加先前叫停的公开APK下载或手动更新复验。
- 用户选择方案A，废弃0.15.39横向卡片：桌面30/70主从，窄屏列表/详情切换；紧迫优先、完成后置。全文随页面滚动，仅索引列表独立滚动；外部candidate_id选择、列表可见性、原位编辑及来源字段只读保留。现行规则已替换到LAN文档和交互基线。
- 生产资源＋隔离HTTP模拟API的`lan_detail_navigation_check.cjs`67项通过，覆盖45候选排序/深链、长文、键盘、原位编辑、脏稿取消/失败/重试/重复提交、浏览器返回与真实刷新取消、模态四关闭路径及删除取消、保存中拒绝关闭、390/320深浅主题、150%实际文字与Dock避让。截图及result.json在`build/lan-detail-navigation/`，模态150%为`320-font150-modal.png`。
- 独立UI审核发现并修复模态直接关闭丢稿、模态删除取消丢稿、窄屏返回列表目标不可见三项P2，最终复审通过。仅无编辑表单时跳过异步离开检查，保留Dock惯性预测即时切页；动效原回归和67项再次通过，独立复核该单行调整安全。
- 受影响浏览器反馈、parity、来源/同步/附件、motion、deadline、record-removal均通过，主页DOM复用87项和quick7/7通过。旧测试的首页标题、原文浮层和inline编辑选择器已跟随真实入口更新。CUA实际在隔离数据保存改标题并保留目标，再点击真正遮罩关闭原文弹窗，截图`cua-master-detail.png`。
- 正式debug/release构建及双lint通过（`formal-final-build.log`）。固定APK `build/candidates/0.15.40/Chrona-0.15.40.apk`为3100134字节，`com.donglan.chrona`、202/0.15.40、不可调试，原签名证书保持一致（`candidate-signing.log`/`candidate-badging.log`）。此版本只修改网页资源，原生首页课表修复沿用已验收候选。
- 用户随后通过USB连接vivo V2527A：install -r将201/0.15.39覆盖更新为202/0.15.40，返回Success，dumpsys确认新版，DashboardActivity启动Status: ok。未卸载或清数据，未写测试作业或日历。只读服务检查未见LanAccessService运行，真实LAN链路仍未验证，需用户开启局域网访问后使用；不以模拟API代替设备链路。

## 0.15.39 — 网页横向卡片与首页课表刷新竞态 — 2026-10-22

- 发布后用户明确要求安装：ADB在vivo上以install -r将200／0.15.38覆盖为201／0.15.39，系统返回Success；安装后dumpsys确认版本，DashboardActivity启动Status: ok。未卸载或清数据；此升级记录不代替尚未执行的真实网页链路／触摸验收。
- 已验收源码`671862e`与不可变标签`v0.15.39`已推送；[正式Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.39)于11:06发布为Latest。页面确认同一提交、四条用户更新及固定`Chrona-0.15.39.apk`（3097686字节／2.95 MB），截图`build/release-0.15.39-published.png`。手机未自动覆盖，需升级后刷新网页。
- 网页详情从纵向堆叠改为时间升序的多卡横向轨道；桌面250–340px卡宽、1280px下三张完整卡片，短内容自然高度、长内容卡内纵向滚动。底部保留切换和当前项操作；外部candidate_id按实际几何居中，当前ID写入历史，刷新／返回保留。主页最近作业入口携带筛选身份，刷新与历史不丢失；总列表仍筛选分页。原文／附件／记录操作收纳到浮层，托管作业隐藏该入口。
- `lan_detail_navigation_check.cjs`生产资源＋隔离HTTP合成45候选，33条通过：稳定时间排序、同时间ID、未定项末尾、目标居中与三完整卡、内部滚动、编辑与返回、旧回调不劫持新路由、真实计算字号150%的390/320深色布局、减少动画及空记录。大字体初验发现Dock变高遮挡10px，改为实际Dock高度决定详情底部安全距离后通过。专门只读UI复审通过。实际CUA滚轮从118横滚到122，URL／高亮／操作同步；实际主页最近作业显示作业／全部／未完成，`cua-horizontal-scroll.png`、`cua-homework-filter.png`。
- 受影响浏览器parity、deadline、原文保留record-removal专项通过；DOM复用87条与quick7/7通过。旧parity入口标题和隐藏候选操作、DOM片段边界及原文浮层选择器已同步，不通过改产品迁就夹具。CDP整页截图会错误偏移横向轨道，改用实际视口截图；CUA画面另行确认。
- 原生稳定复现：课表结果在`applyCompletionRows`完成动画期间到达，旧`refreshHomeContent`只置homeRefreshPending，结束回调却只消费completionRefreshPending；相同快照不触发poll刷新，课表继续缺失。旧候选受控实际Activity测试`home-race-red.log`失败，新代码将请求合入完成队列后通过。独立审查发现关联失败恢复缺口，补读取失败清双标志、保留dirty、既有轮询按dirty重试；无忙等或陈旧来源回退。
- `HomeCalendarRefreshDeviceCheck`限独立v33模拟器，真实首页／本地读取／动画／渲染，主动控制迟到课程回调顺序，关闭周期轮询以验证不需重进；再调用真实失败完成回调，断言立即解锁、dirty保留且没有立即重试，随后同快照的真实poll恢复。深浅主题`home-race-recovery-{light,dark}.log`均通过，`home-race-{light,dark}.png`干净实际截图并检查窗口焦点；无任务／Calendar写入，偏好已恢复。完成卡片复用9条及接线检查通过。未模拟实际存储I/O异常，用户历史现场无日志，所复现路径与历史原因不混为同一证据。专门只读UI最终复审通过。
- 正式双构建、androidTest编译及双lint日志`build/lan-detail-navigation/formal-final-build.log`；同目录保存候选签名与清单。固定APK为`build/candidates/0.15.39/Chrona-0.15.39.apk`，201／0.15.39、官方包名与原签名。原生修复加入后已重新构建，不分发先前仅含网页改动的产物。
- 本轮未覆盖真实手机；网页真实手机数据链路与真实手机触摸横滑未验收。公开Release发布状态见当前工作单；不追加此前叫停的公开下载／手动更新复验、付费AI或真实WebDAV。

## 0.15.38 — 新作业定位与首次亮屏计时 — 2026-10-21

- 源码`e96080f`与不可变标签`v0.15.38`已推送；正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.38)于21:12发布为Latest。页面确认同一提交、三条用户更新说明和固定`Chrona-0.15.38.apk`（2.95 MB），截图`build/release-0.15.38-published.png`。手机已安装同一200／0.15.38；未追加此前叫停的发布后公开下载或手动更新复验。
- 新入库的未完成学校作业在同一事务中保存未查看标记；数据库14→15为旧项默认0。重复同步、通知关闭／到期不恢复或消费标记。主页复用原作业按钮绘制8dp红点，沿实际列表排序与24项分页定位，补足首尾真实滚动空间，将目标居中并短暂主题强调。目标完成绘制且窗口有焦点、中心可见后才消费单项；其它未查看项保留。预建工作区完成激活后接续定位，HomeData复用已有TaskStore读事务，避免journal_mode锁。
- 隔离原生200／0.15.38：深色大字体1.3、66项跨第3页、两未读逐条查看14条通过；浅色单项首尾、真实通知栏遮挡不消费、恢复焦点后定位、关闭动画后恢复原foreground15条通过。截图与日志在`build/homework-first-screen/`，专门只读UI最终审核通过。夹具守卫拒绝正式包、手机、真实账号和外部同步，不写真实日历。
- 提醒服务生命周期与首次亮屏／服务启动竞态检查通过；作业存储49条、quick7/7、全表快照／隔离恢复16条通过。完整备份导入上限引用数据库唯一版本常量；原生当前版本快照校验与unread保留也通过。
- 第一轮真实十分钟暴露亮屏后350ms队列刷新前进程结束的撤下缺口；已改为亮屏广播内同步保存并立即交付系统结束闹钟，卡片后台刷新。收紧原生等待为实际十分钟TTL与needs_post=false，第二轮启动8条、跨进程同截止6条、实际600秒后系统通知撤下及原作业保留5条均通过（截止1792585778471，检查1792585780565）。首次亮屏同时更新旧关闭PendingIntent的截止身份，进程结束仍可关闭；服务在显式启动与亮屏竞争时先履行前台提升，独立于活动提醒频道是否开启。
- 追加结果通知第一行显示实际日程名称，优先已写入项、多项附数量，展开保留处理状态及原详情。生产名称选择6条、原生折叠／展开6条通过，截图`notification-{collapsed,expanded}-title-light.png`，专门只读UI审核通过。
- 后续只读确认手机离线，未重试系统拒绝的安装；在隔离包补充通知生命周期14条通过：分别熄屏拉取的两项合并、部分完成保留等待状态与身份、旧批次关闭无效、首次亮屏后旧PendingIntent仍能关闭、关闭不消费红点、全部完成及来源关闭撤下并保留未完成源数据。日志`notice-edges.log`，夹具已恢复；只新增测试，生产候选不变。
- 最终正式debug／release／androidTest及双lint通过：`formal-final-build.log`。修正minSdk26下stateDescription的API30条件；API36实测分支不变。候选`build/candidates/0.15.38/Chrona-0.15.38.apk`为3094850字节，官方包名200／0.15.38、release不可调试，签名与手机0.15.35一致；`candidate-{signing,badging}.log`。此固定候选已正式发布，见本节发布记录。
- 手机只读确认所查作业早于用户指出的时刻已入库，之后同步新增0；具体课程与时间保存在私有诊断目录。未保留历史投递日志，熄屏期间旧十分钟提示提前结束仅为合理解释。初次系统拒绝辅助包安装后未绕过；用户重新授权USB继续，已正常更新辅助包、保存完整数据恢复备份（不含API密钥，3630383字节／59项，ZIP CRC通过），并覆盖安装同签名200／0.15.38，原有作业与主页保留。
- vivo OEM实际收起胶囊、点击展开卡片及升级后主页经专门只读UI审核通过，标题位于摄像头安全头行下方，右侧关闭键完整。20:48:29熄屏投递waiting=true，20:48:31首次亮屏记录截止20:58:31；再次亮屏未延期，到期前15秒系统仍持有VIVO_SUPERX_TAG通知，20:58:31.824撤下。单一持续原生测试通过实际首次亮屏600秒验收，日志`usb-atomic-live.log`、`usb-atomic-before-expiry.log`及`usb-first-screen-events.log`，实际图`usb-phone-island-{resting,expanded}.png`、`usb-atomic-expired.png`。
- 另开ID0预览，真实点击OEM「×」在到期前关闭，`usb-oem-close.log`通过；最终只读逐项比较原有最近作业身份、完成和元数据一致，来源绑定不变，预览持久状态与系统通知均清空。未写入合成作业或真实日历。测试辅助包变动不改变正式生产候选。

## 0.15.37 — 通知详情与交互绘制层级 — 2026-10-21

- 源码 `7c2e0ad`、不可变标签 `v0.15.37` 已推送；正式 Release 已发布为 Latest，页面确认同一提交、三条用户更新及 `Chrona-0.15.37.apk`（2.95 MB）。发布页 https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.37 ，截图 `build/release-0.15.37-published.png`。未追加此前用户叫停的发布后公开下载、手动更新及覆盖安装核对；实体手机未覆盖。
- 处理结果通知将实际保存的事项详情同时用于 ContentText 与 BigText，保留权限检查、点击入口和查看操作；不改自动确认／日历写入策略。
- 新公共 InteractionLayers 按事件提升按压与焦点分支的 translationZ，不更改父容器子项顺序、静态尺寸或 Dock 弹簧参数。固定主页、宽屏及工作区导航分支优先。释放回落结束、失焦及脱离恢复原值；修正 Android 脱离回调期间 isAttachedToWindow 仍为真的时序。
- `NotificationLayersDeviceCheck` 仅允许独立 v33 官方模拟器，使用合成任务及已保存候选，不调用 AI、不写真实日历。通过真实服务通知入口与 NotificationManager 验证正文／展开内容、时间地点提醒备注及详情 PendingIntent；实际 SystemUI 展开和折叠截图。收件箱首尾长按、取消、拖出、键盘焦点、邻居分支 Z 与固定 Dock，附 30 次非累积、双状态、非零基线恢复及脱离检查。浅色 1080×1920 density411 font1 为原 Dock，137 条通过；深色 1080×2160 density480 font1.45 为原紧凑适配，136 条通过。日志 `build/notification-layers-device-{light,dark}.log`，9 张实际截图 `build/notification-layers-evidence/`。系统折叠高度仅容纳摘要，展开直接显示全部本通知内容。
- Dock 离线 180 条、公共 UI 63 条通过；前者仅替身逻辑检查，不代替上述真实触摸。初次原生层测试发现脱离 pin 未恢复 Z，已修复并双主题复验；通知 UI 查询改用实际可访问树遍历定位系统展开控件，未改产品语义。
- 独立 UI 初审发现以 scale 阈值恢复层级会早于惯性／尾部结束；改用实际 tracking、followPosted、coastSelection 状态，在安排下一帧后更新层级。新增真实末项双向甩出与每个 preDraw 断言，覆盖 scale<1.001 但仍运动的后段，最终恢复；浅色最终 138 条，新增 `inbox-late-inertia-light.png`。深色紧凑分支 136 条、关闭系统动画 136 条通过；关闭动画日志 `build/notification-layers-device-reduced.log`。专门只读 UI 复审通过，无剩余缺陷。
- 正式199／0.15.37双构建与双lint通过（`build/release-01537-formal.log`），quick7/7通过。正式候选 `build/candidates/0.15.37/Chrona-0.15.37.apk` 为3089194字节，比.36增加1784字节。原签名、官方包名／版本、release不可调试通过，`build/release-01537-{signing,debug-signing,badging}.log`。不更改工具链／混淆策略，不覆盖手机或正式模拟器包。修改前副本 `.local-backups/notification-and-interaction-layer-20261021/`；发布状态见当前 HANDOFF。不恢复此前叫停的发布后额外核对。

## 0.15.36 — 课表首帧高光与课堂加载布局 — 2026-10-21

- 课表首次render前先解析来源，避免null来源先显示导入、随后切到浙大的虚假高光动画；保留导入深链、学校停用默认来源及真实手动切换动画。课堂本地读取与远端刷新不再插入临时加载文字，使用原位刷新／取消图标；失败与取消消息路径保留。
- 实际原生启动还发现布局期重建网格后，滚动恢复的preDraw等待只重绘而不安排新布局，可能阻止首帧。现安排body重新布局，并只恢复／等待实际挂载的横向视口；独立UI初审指出的仅特殊安排分支未挂载控件等待P2已关闭。
- 独立v33模拟器198两主题各29项通过：学校两次新打开、导入深链及学校停用的创建后／首个preDraw精确高光位置；真实来源触摸的中间动画、归位和同实例；单次特殊安排首帧与窗口焦点；阻塞实际worker的CACHE／REFRESH加载、无临时状态行、日期栏顶部起止不变、真实取消及错误反馈。浅1080×1920／density411／字体1，深1080×2160／density480／字体1.45（360dp）；日期栏顶部309／355px保持不变。`build/workspace-first-frame-{light,dark}.log`；八张最终实际画面 `build/workspace-first-frame-evidence/{timetable-initial,timetable-special-only,classroom-loading-cache,classroom-loading-refresh}-{light,dark}.png`。专门只读UI复审通过，无剩余缺陷或关键证据缺口。
- 初轮夹具误用Activity.onCreate中的生命周期回调、过早触摸与空闲等待；已改用postCreated／首个preDraw／窗口焦点、真实条件等待与异步点击结果等待。未通过改产品门禁规避输入限制。布局期真实等待问题和特殊安排问题在产品修正，未以夹具绕过。首轮lint的连续单行if缩进误报用等价花括号修复。
- 正式198／0.15.36双构建、androidTest与双lint通过（`build/release-01536-formal.log`），quick7/7通过。候选 `build/candidates/0.15.36/Chrona-0.15.36.apk` 为3087410字节，官方包名、版本、不可调试与原签名通过；`build/release-01536-{signing,badging}.log`。R8试验未混入本版。修改前副本 `.local-backups/workspace-first-frame-20261021/`，未覆盖实体手机或正式模拟器。
- 源码 `713db77` 与不可变标签 `v0.15.36` 已推送，正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.36) 已发布为Latest，页面显示同版APK与简洁用户说明；`build/release-0.15.36-published.png`。按用户此前叫停要求，未追加公开下载复验、应用内更新或覆盖升级核对。用户新提出的自动确认通知详情及收件箱聚焦层级／固定Dock顶层要求，明确留到下一版，本版未实现。

## 代码裁剪体积试验（未接入正式构建）— 2026-10-21

- 同197／0.15.35源码，临时R8入口使用平台默认规则、`-dontobfuscate`和`-dontoptimize`，不裁剪资源。APK 3087526→1940720字节，减少1146806字节／37.14%；压缩DEX 2142181→995460字节，全部assets清单及内容逐字节相同。官方包名、版本和原签名通过。试验APK、result.json、签名／清单／usage／configuration均在 `build/r8-size-trial/`；入口 `build/r8-size-trial.init.gradle`、`build/r8-size-trial.pro`。
- 相同裁剪配置的独立v33 Release在模拟器实际通过8项检查：ICS重复／排除日期／时区／时长、二维码、OkHttp TLS初始化、Activity启动、离线MathJax SVG及JavascriptInterface回调。日志 `build/r8-size-trial-runtime.log`；临时夹具源 `build/r8-smoke-source/`，入口 `build/r8-smoke.init.gradle`。测试只进入单独androidTest包，未添加影响产品裁剪的测试保留规则。
- 初轮测试runner注册与异步回调等待已修正。loopback明文HTTP受原策略拒绝，未放宽策略，最终仅验证TLS初始化，不声称外部网络请求通过。biweekly包含反射构造和字段枚举，正式接入前仍需检查动态边界；有限路径通过不代替完整发布验收。
- 正式配置与公开资产未变。独立v33包及测试runner已恢复，正常Release经原构建入口恢复至3087526字节；未覆盖正式模拟器或实体手机。恢复材料 `.local-backups/r8-size-trial-20261021/`。

## 0.15.35 — 课堂下载历史与学生重要事项 — 2026-10-21

- 完整素材下载后按课程／课堂身份归入历史并从课堂列表排除；下载中断不归档，草稿／完成总结优先并去重，保留共享历史与账号隔离。新下载完成时将课堂身份与完成时间一同原子写入候选素材清单，已有下载按当前保存目录索引。历史不受远端回放状态变化阻止，仍执行学校账号门禁；缓存先呈现，远端刷新失败不遮住本地内容。
- 用户确认两轮共用提示词加入全部十类学生重要事项，前八类优先，区分明确要求、参考建议与暗示；仅明确行动安排进入原有提取流程。流水线版本8使新请求采用新规则，已有总结保留。真实Java／循环HTTP166项通过，覆盖完整／中断素材、实际prepare保存身份、目录移除后历史可达、去重／账号隔离，以及实际两轮请求完整十类、优先级和不确定性要求；`build/classroom-history-and-attention-check.log`。未调用真实付费AI，不以请求断言代替模型输出质量判断。
- 正式原生隔离包197两组各17项通过：浅色1080×1920／density411／字体1；深色1080×2160／density480／字体1.45（360dp）。覆盖课堂排除、历史状态、远端未就绪但缓存可打开、真实触摸与返回、过滤后50＋4分页、非零滚动位置本地刷新与详情返回保留、未变条目及选择栏实例。后台完成后真实等待定时刷新，浅色29982ms／深色30072ms进入历史。日志 `build/classroom-history-native-{light,dark}.log`，八张本轮图 `build/classroom-history-evidence/files/classroom-{list,history,downloaded-detail,history-refreshed}-{light,dark}.png`。
- 初轮夹具误读隐藏周表、空contentDescription，以及重复ActivityMonitor返回旧Activity；修正可见范围、空值处理与每次启动前独立monitor后通过，未为这些夹具问题修改产品。合成目录和所建课堂在测试后恢复／删除，不写入手机账号或日历。
- 197／0.15.35正式debug／release、androidTest与双lint通过，`build/release-01535-formal.log`；quick7/7通过。候选 `build/candidates/0.15.35/Chrona-0.15.35.apk` 为3087526字节，官方包名、197版本、关闭调试及debug／release原签名通过，`build/release-01535-{candidate.json,signing.log,debug-signing.log,badging.log}`。专门只读UI审核及非零滚动补充复审均通过，无剩余缺陷或验收缺口。派发工具不支持角色参数，按只读check职责约束，未覆盖或声称固定角色模型；未安装实体手机。

- 源码 `a17b88d` 与标签 `v0.15.35` 已推送；首次GitHub上传失败并超时，后续成功上传同一候选并正式发布为[Latest](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.35)，实际页面显示APK资产及修订后的用户版更新说明；截图 `build/release-0.15.35-published.png`。用户明确要求直接发布并停止追加核对，因此未追加公开下载复验、应用内检查与正式196→197非空覆盖升级测试。前置备份 `.local-backups/release-01535-20261021/upgrade-before.zip` 已保存，正式模拟器及实体手机未被覆盖。

## 0.15.34 — 重复系列残留清理 — 2026-10-20

- 用户发现三个 `重复验收 <时间戳>` 系列未清干净，并明确允许删除这三个系列的全部实例。真实手机Provider确认9条例外仍引用已缺失的三个主ID；按已确认标题、所属日历、主ID和实际行ID联合限制删除。全量433→424条，剩余424条完整行逐项一致，三个标题及原主ID／例外查询均为空。私有原始快照和修改前副本仅保存在 `.local-backups/calendar-fixture-cleanup-20261020/`。
- 根因是 `CalendarStore.deleteEvent` 仅删除主ID，依赖Provider级联清理；此手机Provider留下改期／取消例外。源码改为在同一自有日历内单次删除主ID及其 `original_id` 例外，主记录缺失仍能清理其例外，保留跨日历隔离。隔离官方模拟器实际创建、跳过、改期再删整系列，15项通过，确认主行和全部例外为零、二次删除返回false；`build/calendar-cleanup-native.log`。既有重复边界20项及实际生产SQL的系列、孤儿、陈旧ID、普通事件和跨日历隔离回归通过，`build/calendar-cleanup-regression.log`。
- 重复与浏览器写入夹具首行调用隔离守卫：拒绝实体手机、正式／旧validation包、真实学校账号、外部同步及已有LAN会话。实际Java守卫16个场景和写入前调用顺序检查通过，`build/fixture-isolation-check.log`，已纳入full。
- 正式debug／release、androidTest编译和双lint通过，`build/calendar-cleanup-formal-build.log`；日历链接原有SQL专项通过。手机正式195未替换，最后成功观察8756仍处于前台；随后USB设备断开。真实手机网页经手动刷新确认仍连接、三个测试标题为零、本周23条正常日程保留，`build/phone-calendar-cleanup-browser.json`，实际截图仅保存在上述私有恢复目录。手机暂存的 `/sdcard/chrona-cleanup-window.xml` 与 `/sdcard/chrona-cleanup.png` 因USB断开尚未移除，重连后仅清理这两个验收文件。
- 196／0.15.34正式debug、release、androidTest及双lint通过，`build/release-01534-formal.log`；quick 7/7通过。候选 `build/candidates/0.15.34/Chrona-0.15.34.apk` 为3085598字节，官方包名、196／0.15.34、关闭调试及debug／release原签名一致通过，记录 `build/release-01534-{candidate.json,signing.log,badging.log}`。隔离Provider和SQL证据对应同一删除实现，仅版本元数据变化。
- 源码 `d3c0dc5` 与不可变标签 `v0.15.34` 已推送，正式[Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.34) 为Latest。公开API非Draft／非Pre-release，下载资产3085598字节；公开APK实际196／0.15.34、不可调试和原签名通过。分发包 `build/distributions/0.15.34/Chrona-0.15.34.apk`，证据 `build/release-0.15.34-{public.json,public-apk.log,public-signing.log,published.png}`。CLI直连GitHub连接重置／超时后，沿现有系统代理作本次命令路由完成推送，未改全局Git／代理配置。
- 正式模拟器195实际显示发现0.15.34；公开196覆盖成功后显示已是最新。覆盖前后完整备份比较15张表结构与全部行、1条收件／1个候选、设置、课堂文档／图片字节一致，仅导出时间变化；日志 `build/release-0.15.34-{old-update,current-update,upgrade-preservation}.log`，副本 `.local-backups/release-01534-20261020/`。实体手机未覆盖新版，现有LAN未被重启。

当前源码版本为 **0.15.36 / 198**。下面记录近期版本的实际验证；历史记录按版本分卷保留。

- [0.15 系列](archive/validation-0.15.md)
- [0.14 系列](archive/validation-0.14.md)
- [更早版本](archive/validation-early.md)
- [设备验收模板](device-acceptance.md)

构建、离线检查、实际设备操作分别记录。旧版本某项通过，只支持对应候选和条件。

## 0.15.33 — 缺省执行区间、手机授权与课堂阅读 — 2026-10-20

- 普通 `task` 原始起止均空且 due 非空时派生 due−1800000ms 至 due，原始字段、编辑器、同步、Provider 写入与重复锚不变；学校作业仍为截止点。真实模型／首页／SQL、已有重复、Agenda、Widget 与 TaskStore 检查通过；新检查 `todo_due_default_check.py` 已纳入 full。主代理日志 `build/todo-due-default-root.log`，其余受影响检查有效结果沿用本轮执行记录。
- 普通待办原生浅色 67、360dp／系统字体 1.45 深色 69 项通过：三类首页卡片、列表完整区间、实际触摸进入详情和返回、读取不回填、真实 Agenda／Widget 模型；launcher 宿主未测。日志 `build/todo-due-native-{light,dark-narrow}.log`，截图 `build/01533-ui-evidence/`。
- 课堂总结阅读移除计时与来源导航，保持来源 JSON 和导出完整。两组原生各 29 项通过：八个公式 SVG、x-height 压力、长公式真实横向触摸、引用图真实点击／放大、无重复图片及 ZIP 原始链接保留。日志 `build/classroom-reader-native-{light,dark-narrow}.log`；深色 360dp／系统字体 1.45。图片链离线 146／10 项及五个补充探针通过，`build/classroom-image-reference-audit.log`；四份实际 ZIP 零张分析图片不足以判断原因，AI 图片选择策略保持不变。
- 授权安全离线 83 项、独立 21 项及并发 30 次仅一次 session 通过。独立审查发现无效 poll/cancel 触发通知回调的 P2 后，改为真实消费／取消才通知；真实 Android HTTP 回归包括十轮无效调用／重放不增加回调，`build/lan-01533-http-integration.log` PASS，局部安全复审确认关闭。
- 授权浅色和窄屏大字体深色原生主流程通过：真实生产服务／HTTP、前台允许与拒绝、网页取消自动关闭、后台8757通知不抢前台、系统通知真实触摸进入确认、单次 Cookie、停服断连和重启后旧凭证撤销。日志 `build/lan-approval-native-{light,dark-narrow}.log`。最初失败为模拟器 Wi-Fi 未连接、旧夹具节点／坐标／通知检索问题；保留旧包数据，另建 `.followup.validation.v33` 后完成实际验收。
- 浏览器实际 CUA 加载生产资源与本地合成 API，验证取消、重试、拒绝、批准和手输码，列表／详情显示跨日 23:40—00:10，编辑原始起止仍空。桌面深浅、360px 列表及 360px／body22.5px 深色授权图已目检，无横向溢出；合成 CSS 字号不冒充系统字体。实际主题按钮深色断连重试的核对码 #00b1ad 对 #252629 对比 5.686:1。受控 API／计时器执行真实授权函数 14 项、主页节点复用 87 项通过；整套旧 CLI 浏览器矩阵未重跑。
- 正式 Debug／Release 与双 lint 通过，`build/release-01533-formal.log`。正式候选 3085590 字节，官方包名、195／0.15.33、原签名通过，包内 LAN 三项与课堂两项资源逐字节一致；记录 `build/release-01533-{candidate.json,signing.log}`。121147ms 真实等待后的请求过期、原生页关闭和通知撤下通过，`build/lan-approval-native-expiry.log`；专门独立 UI 审核通过。
- quick 7/7 通过；Widget 和数据完整性检查从生产拼接表达式解析共享 SQL 常量，保持 quick 只需 Python，`build/release-01533-quick.log`。
- 源码 `5b33193` 与不可变 `v0.15.33` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.33) 为 Latest，公开 API 非 Draft／非 Pre-release，APK 3085590 字节。公开下载实际官方包名、195／0.15.33、原签名通过；分发包 `build/distributions/0.15.33/Chrona-0.15.33.apk`，证据 `build/release-0.15.33-{public.json,public-apk.log,public-signing.log,published.png}`。
- 正式模拟器194实际发现新版；公开195覆盖成功后显示已是最新版本。前后完整备份比较保留全部15张表结构与全部行、1条收件／1个候选、设置、课堂文档与图片字节，仅导出时间变化。日志 `build/release-0.15.33-{old-update,current-update,upgrade-preservation}.log`，副本 `.local-backups/release-01533-20261020/`。
- 用户确认手机升级并重开LAN。USB调试打开后重新插拔并选文件传输即正常识别，未改驱动。正常备份界面确认密钥未勾选后导出当前ZIP，3586147字节、39条收件、81个候选、56个归档项；原APK也保留于 `.local-backups/release-01533-20261020/phone/`。正常系统确认后公开195覆盖成功，`build/release-0.15.33-phone-install.log`，手机实际版本195／0.15.33。
- 手机从正常设置入口重开LAN，8756前台服务保持运行。新网页自动申请并实际弹出原生授权页，两端核对一致后由用户在手机允许。真实首页在可见状态下间隔79423ms，时间线50条、明日7条及总58个独立展示键完整保留；随后实际手动刷新仍保持相同计数，无横向溢出。报告 `build/release-0.15.33-phone-home.json`；实际画面保存在上述私有phone恢复目录。服务与已授权网页继续可用，用户数据未上传到公开发布页。

## 0.15.32 — 局域网主页重复节点 — 2026-10-20

- 旧版全局键在时间线和最近记录间重复；下一轮 `replaceWith` 搬移同一个节点，使时间线只剩日期标题。相同输入最小复现为 3 条变 0 条。`entry` 按主页区域区分显示键，复用循环用集合保证每个旧节点只消费一次；原数据、接口、排序、十秒刷新与动效不变。
- `checks/lan_dom_reuse_check.py` 生产片段回归 87 项通过，覆盖连续五轮、两区重复显示、节点身份、标题更新、点击目标、合法空状态及重复键保护，日志 `build/lan-home-reuse-core.log`。检查已纳入 full；quick 7/7 通过，日志 `build/lan-home-reuse-quick.log`。
- 实际浏览器加载生产 HTML／JS／CSS，合成 API 同时提供两区相同记录：时间线 4、最近记录 4、待办 1，9 个独立显示键。多轮真实十秒轮询与手动刷新后数量及日期保留；更新标题在两处出现，学校只读详情与本地候选详情入口正确。桌面深浅、360px 浅色与 360px／22.5px 页面字号深色实际图已检查，后者仅是相对 15px 的页面字号 1.5 倍。报告 `build/lan-home-reuse-browser-report.json`；原有整套 CLI 浏览器矩阵未重跑。
- 正式 Debug／Release、lintDebug／lintRelease 通过，日志 `build/lan-home-reuse-final-formal.log`。候选 `build/candidates/0.15.32/Chrona-0.15.32.apk` 为 3081046 字节，官方包名、194／0.15.32、不可调试及原签名通过；包内 LAN 三项资源与所检查源码逐字节一致。签名记录 `build/release-0.15.32-signing.log`。
- 专门 UI 独立审核已核对代码、回归及实际浏览器图，无剩余代码／界面缺陷。用户明确同意覆盖手机并重开局域网，系统确认后安装成功，实际为 194／0.15.32；通过正常设置界面重新开启，服务处于前台运行。手机真实网页配对在十分钟期限后返回失败，尚未进入主页；随用户新增手机确认授权流程补验真实连续刷新。恢复副本 `.local-backups/lan-home-node-reuse-20261020/`。
- 发布源码 `8457f4f`、不可变标签 `v0.15.32` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.32) 为 Latest，公开 API 为非 Draft／非 Pre-release，APK 3081046 字节。公开下载实际官方包名、194／0.15.32、不可调试及原签名通过，分发包 `build/distributions/0.15.32/Chrona-0.15.32.apk`；记录 `build/release-0.15.32-{public.json,public-apk.log,public-signing.log,published.png}`。
- 正式模拟器 193 实际发现 0.15.32，覆盖安装公开 194 后显示已是最新版本。前后完整备份比较通过：15 张表结构与全部行、1 条收件／1 个候选、设置、课堂文档和图片字节保留，仅备份导出时间变化。日志 `build/release-0.15.32-{old-update,current-update,upgrade-preservation}.log`，恢复副本 `.local-backups/release-01532-20261020/`。

## 0.15.31 — 操作胶囊系统日程高光 — 2026-10-20

- `CalendarVisibilityToggle` 原来先安装专用圆形涟漪，`UiStyle.pressable` 检测已有 RippleDrawable 后保留它，造成同组高光不同。删除该圆形遮罩及圆形选中底色，直接复用现有 `ControlHighlightMask`，图标颜色继续表达开关状态。主页调用者同步移除废弃的布尔配置；公共高光、胶囊与按压动效参数未改。
- 核心 35 项通过，覆盖连续 tint／图标 pulse、快速反向、重复值、减少动画、脱离及权限同步拒绝，日志 `build/header-toggle-core.log`。周日程资源／路由及实际双轴手势替身检查通过，日志 `build/header-toggle-week.log`。
- 正常浅色和 320dp／字体 1.45 深色两组原生检查通过，日志 `build/header-toggle-native-{light,narrow-dark}.log`：每组实际按压主页 3、周日程 3、日程列表 2 个动作，验证公共填充遮罩、真实 DOWN／CANCEL、取消不切换来源，以及 6 次开关状态／偏好／无障碍／颜色终态与无圆形选中底色。34 张原始截图及按压对照图在 `build/header-toggle-captures/`。启用学校的主页仅临时调用实际标题构建方法，未运行学校生命周期或真实网络；测试数据仅在隔离包创建并清理。
- 检查夹具按压捕获改为等待原生延迟 pressed 后直接截图，避开长按 tooltip；开关验证等待实际颜色终态而非固定延时。生产实现未因这些夹具修正而改变。最终 androidTest 构建通过，日志 `build/header-toggle-final-test-build.log`；模拟器显示设置已恢复。
- 正式 Debug／Release 与双 lint 通过，日志 `build/header-toggle-final-formal.log`；实际 APK 官方包名、193／0.15.31、不可调试及与旧版一致的签名证书通过，日志 `build/release-0.15.31-signing.log`。候选 `build/candidates/0.15.31/Chrona-0.15.31.apk` 为 3080998 字节；手机已成功覆盖安装并确认实际版本，未卸载或清除数据。
- 独立 UI 审核已核对公共遮罩、两组按压对照和完整截图、开关状态以及相关检查，未见旧圆形高光、新增裁切或剩余缺陷，本轮 UI 验收通过。
- 发布源码 `94cfe38`、不可变标签 `v0.15.31` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.31) 已设为 Latest。公开 API 为非 Draft／非 Pre-release，APK 3080998 字节；公开下载的官方包名、193／0.15.31、不可调试和原签名通过，保存在 `build/distributions/0.15.31/Chrona-0.15.31.apk`。记录 `build/release-0.15.31-{public.json,public-apk.log,published.png}`。
- 正式模拟器 0.15.30 实际发现 v0.15.31 及下载入口，覆盖安装公开包后显示已是最新版本。前后不含 API 密钥的完整备份比较通过：全部 15 张表结构与逐行数据、1 条收件／1 个候选、全部设置、课堂文档和图片字节保留，仅备份导出时间变化。日志 `build/release-0.15.31-{old-update,current-update,upgrade-preservation}.log`；恢复副本 `.local-backups/release-01531-20261020/`。普通收件附件与桌面小组件没有本轮升级样本。

## 0.15.30 — 课堂滚动、自动状态、重新生成与公式 — 2026-10-20

- 发布源码 `80f2362`、不可变标签 `v0.15.30` 已推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.30) 已设为 Latest。公开 API 为非 Draft／非 Pre-release，正式 APK 3081266 字节；公开下载的实际包名／192／0.15.30、不可调试和原签名验证通过，保存在 `build/distributions/0.15.30/Chrona-0.15.30.apk`。记录 `build/release-0.15.30-{public.json,public-apk.log,published.png}`。
- 正式模拟器旧 0.15.29 通过真实更新页发现 v0.15.30 及下载入口；覆盖安装公开 APK 后显示已是最新版本。升级前后不含 API 密钥的完整备份比较通过：全部 15 张表的结构与逐行数据、1 条收件／1 个候选、设置、课堂文档和图片内容保留，仅备份导出时间变化。日志 `build/release-0.15.30-{old-update,current-update,upgrade-preservation}.log`；恢复副本 `.local-backups/release-01530-20261020/`。普通收件附件与桌面小组件本轮没有升级样本。

- 手机现场复现课堂表格纵向拖动抽搐：外层纵向视口移动了处理双轴手势的横向容器，局部触点坐标形成反向反馈。改为直接复用周日程的层级：固定 `FreeCalendarScrollView`，内部原生 `ScrollView` 承载表格。公共手势实现未改；标题与日期固定，切周使用固定视口横向过渡。
- 正常浅色与 320dp／字体 1.45 深色各 104 项通过，日志 `build/classroom-viewport-reuse-{light,narrow-dark}.log`。覆盖每个 MOVE 的静止容器坐标与单调纵向位移、横向浏览／边缘切周、取消／多指、半途反向、减少动画位置、末行到达 Dock 上方、短周无额外滚动，以及真实时间边界、原位状态重绑、缓存年龄、五分钟失败冷却、不重叠和生命周期清理。截图在 `build/classroom-final-captures/`。
- 用户新授权 USB 调试后，先通过应用导出不含 API 密钥的完整备份并保留原 APK，再以 `-r` 覆盖安装候选，不卸载、不清数据、不调整手机密度／字体。手机原有课堂的公式尺寸和基线已实际对照，课堂表格进行了多次慢速上下拖动；视频 `build/classroom-phone-scroll-final.mp4`，截图 `build/classroom-phone-week-reuse-{top,scrolled}.png`。恢复副本 `.local-backups/classroom-viewport-status-20261020/phone/`。没有向手机注入合成课堂或调用付费 AI。
- 公式专项在相同两组显示条件下各 19 项通过，日志 `build/classroom-math-final-{light,narrow-dark}.log`：8 个公式均成功，TeX x-height 转为 em；模拟异常系统 ex 后公式高度不变，大字体仍自然增大。长行内／独立公式真实横向手势首尾可达，正文不被挤宽，源码／图片／课堂安排入口保留。手机原有线性代数和多项式正文实际显示正常，图像 `build/classroom-phone-summary-final.png` 与 `build/classroom-phone-polynomial-final.png`。
- 重新生成核心 146 项通过，日志 `build/classroom-regenerate-regression.log`；原生 9 项通过，日志 `build/classroom-regenerate-native.log`。已完成轮次新建 run 并实际尝试本地 HTTP；未完成同配置续跑，首轮连接失败时原总结恢复可读。正式 AI 服务的付费生成与内容准确率未实测。
- 快速离线检查 7／7 通过，日志 `build/classroom-release-quick.log`。旧版完整检查仅复用未改变的对应前提，不当作新增 UI 的渲染证据。
- 独立审核指出空周提示跟随宽表被裁掉；改为按视口宽度布局并抵消横向偏移，不重置浏览位置。正常浅色及 320dp／字体 1.45 深色各 7 项通过：X=200 保留、提示完整可见、无纵向范围、最右边缘仍可见。日志 `build/classroom-empty-final-{light,narrow-dark}.log`，截图 `build/classroom-final-captures/classroom-viewport-empty-fixed-{light,dark}.png`。原审核代理局部复审通过，本轮整体 UI 无剩余缺陷或关键证据缺口。
- 最终正式 Debug／Release 与双 lint 通过，日志 `build/classroom-empty-final-formal.log`；隔离 Debug／androidTest 构建通过，日志 `build/classroom-empty-isolated-build.log`。最终 Release `build/candidates/0.15.30/Chrona-0.15.30.apk` 为 3081266 字节、官方包名、192／0.15.30、不可调试；新 Debug／Release 与旧 0.15.29 的签名证书一致，日志 `build/release-0.15.30-signing.log`。
- 手机首次安装最终包被系统拒绝后停止重试；用户亮屏明确允许继续，覆盖安装成功，确认实际安装版本为 192／0.15.30，原有数据可正常打开。手机密度／字体未调整；模拟器检查后恢复 density override 411、font_scale 1.0。

## 0.15.29 — 正式发布与覆盖升级 — 2026-10-20

- 源码提交 `0ee3217` 与不可变标签 `v0.15.29` 已通过本地 Git 推送；[正式 Release](https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.15.29) 已发布并设为 Latest。Git 直连出现连接重置，使用当前系统已有的代理完成推送，未修改全局 Git 或网络设置。
- 公开 `/releases/latest` 返回 `v0.15.29`、非 Draft、非 Pre-release；`Chrona-0.15.29.apk` 已上传、3079870 字节。公开下载的 APK 实际包名 `com.donglan.chrona`、191／0.15.29、签名与原版一致；最终本地分发包来自该公开下载。API 记录 `build/release-0.15.29-public.json`，截图 `build/release-0.15.29-published.png`。
- 模拟器官方 0.15.27 的真实 `UpdateActivity` 读取公开 API，显示「发现新版本 v0.15.29」与下载按钮；覆盖安装公开 APK 后，官方 0.15.29 显示「已是最新版本」。日志 `build/release-0.15.29-{old-update,current-update}.log`，对应截图同名 PNG。下载按钮实际将正确 APK URL 交给外部浏览器；模拟器浏览器连接重置，宿主公开下载成功，二者不混为同一路由成功。
- 升级前先导出不含 API 密钥的完整备份并保存旧 APK；不卸载、不清数据，公开 Release APK 覆盖安装成功。升级后再导出备份，比较全部 15 张 SQLite 表结构和逐行数据、1 条收件、1 个候选、全部配置、学校／课表数据与课堂文档和图片内容，均一致；只排除本次备份操作生成的 `config.exportedAt`。日志 `build/release-0.15.29-upgrade-preservation.log`，恢复副本 `.local-backups/release-preparation-20261020/`。本轮没有普通收件附件或桌面小组件样本，不据此宣称其升级验收通过。
- 发布夹具补充只读完整备份入口；旧 IntentFilter-only 下载拦截未匹配实际外部启动，且浏览器留在任务栈上会使 `startActivitySync` 等待并未创建的 Activity（系统启动结果为 delivered-to-top）。检查改为新建测试页面、只断言更新结果与下载入口；外部交接使用上述实际浏览器证据。该修正仅影响 androidTest，生产 APK 和发布标签不变。
- 手机原子岛 OEM 宿主外观、手机非空数据升级、真实付费 AI 与真实远端 WebDAV 尚未现场验收，已在正式发布说明中列出。

## 0.15.29 — 作业通知最终验收续轮 — 2026-10-20

- `build/homework-notice-durable-lifecycle.log` 通过真实 SQLite 与 `TaskCompletion.set` 入口，验证部分完成仅移除对应项、剩余 ID／标题与原到期时间保留、静默更新、全部完成撤下、来源关闭、绑定替换及删除后的撤下。通知清理不删除或撤销已完成作业。
- 正常浅色与 320dp／字体 1.45 深色的真实系统通知栏折叠／展开和实际「×」关闭通过；关闭后完成字段未改变。折叠保留系统标题／摘要，展开标题与关闭键同首行，正文可读。日志 `build/homework-notice-system-shade-light.log`、`build/homework-notice-system-shade-narrow-dark.log`，有效画面在 `build/homework-notice-final-captures/notification-*.png`。这一组才是系统宿主证据，先前 Activity 预览只证明 RemoteViews 布局。
- 主页新刷新在「全部待办」与折叠箭头之间，共用操作组；同两组原生检查验证顺序、不重叠、48dp 目标、文字完整、原折叠偏好恢复、忙碌禁用／无障碍状态，以及未配置来源时实际打开学校设置。日志 `build/homework-refresh-home-{light,narrow-dark}.log`；已配置来源的仅作业请求／排队／回调沿用 24 项生产 owner 检查，本轮不发送真实学校请求。
- 进程结束后发现已有通知仍在而异步写入的通知身份尚未落盘。`HomeworkNotice` 改为先同步保存身份再投递，关闭同样同步清理；写入失败时不留下未跟踪的新通知。`build/homework-notice-durable-restart-status.log` 证明新进程仍能读取相同 expiry、真实未完成作业与系统通知。修正后的 Debug／Release、双 lint 通过，日志 `build/0.15.29-durable-final-build.log`。
- 最新真实十分钟检查通过，`build/homework-notice-durable-expiry.log` 证明真实 600000ms 后系统通知缺席、未完成作业仍保留；夹具保留到观察结束才清理，未通过删除源数据提前撤下。只有独立包受到测试；模拟器显示配置已恢复，记录 `build/homework-notice-display-{before,after}.txt`。恢复副本 `.local-backups/homework-notice-followup-20261020/`。
- 独立复审发现首项有截止时普通批量通知遗漏总数；已在系统折叠摘要与展开截止信息同时保留数量，不增加额外区域。`build/homework-notice-batch-shade-light.log` 验证两项真实 SQLite 作业、首项有截止、其它标题保留、真实系统折叠／展开数量与截止可见、实际关闭不完成作业。图片 `build/homework-notice-final-captures/notification-batch-{collapsed,expanded}-light.png`。单项空截止隐藏及原子岛布局不变；批量修正副本 `.local-backups/homework-notice-batch-count-20261020/`。
- 普通通知、主页刷新及批量数量的独立 UI 复审全部通过，无未修缺陷。最后候选 Debug／Release、双 lint 通过，日志 `build/0.15.29-batch-final-build.log`。原子岛图片仅支持 RemoteViews 判断，手机 OEM 宿主及手机十分钟现场效果仍未验收。
- `build/0.15.29-final-signing.log` 记录新 Debug、Release 与手机备份 0.15.26 APK 的签名证书一致；Release 实际包名 `com.donglan.chrona`、191／0.15.29、不可调试。最新候选已保存为 `build/candidates/0.15.29/Chrona-0.15.29.apk`（3079870 字节），未提交或发布；签名一致支持升级资格，实际旧版数据覆盖升级仍待发布阶段检查。

## 0.15.29 — 公共 UI 库与执行规范整理 — 2026-10-20

- `UiStyle` 从 1635 行／80571 字节收敛为 563 行／27136 字节；渲染与采样归 `UiSurfaces`，浮层归 `UiDialogs`，内容切换归已有 `UiMotion`。四处弹窗操作行合为一处，删除两项无调用的私有样式函数和始终为空的状态；生产与原生夹具直接迁移，不留旧转发别名。该数字仅说明原单文件规模变化，不表示整个库减少同等体积。
- `AGENTS.md` 固化工作收敛、失败止损、证据复用、反馈总结与无功能删减的整理门槛；当前工作只维护在精简后的 `HANDOFF.md`，旧交接原文归档。职责、接入及维护入口见[公共 UI 库](ui-library.md)。文档本地链接 124 项通过，日志 `build/ui-library-doc-links.log`。
- 统一离线集合现为 91 项：补跑 78 项全部通过，复用 12 项仍有效检查，再通过新增浮层缩放检查，未重复整轮。日志 `build/ui-library-regression.log` 与 `build/ui-library-*.log`；其中共享操作行 63 项、缩放反向／嵌套／减少动画／脱离清理 17 项。学校调度替身已匹配新候选 ID 接口，加入主页仅作业手动刷新后 24 项通过。
- 实际连续开关浮层发现背景缩放未归位，失败见 `build/ui-library-native-light-before-scale-fix.log`。修复为退出完成前保留最初基准，退出中重开继续使用同一基准，结束或脱离清理；原幅度与时长不变。最终原生正常深浅、320dp／字体 1.45 深浅四组各 32 项通过，覆盖确认／取消、多行编辑、日期／时间值与嵌套恢复。日志 `build/ui-library-native-{light,dark}.log`、`build/ui-library-native-narrow-{light,dark}.log`，实际图片 `build/ui-library-captures/`。
- 原生菜单深浅各 164 项通过；配置局部渲染的 27 组覆盖 320／360／640dp、字体 1／1.5／2、首尾长按与甩动，未更改系统设置；亚克力重排中间帧、祖先变换、重绘等价及静止停止刷新通过。日志 `build/ui-library-menu-{light,dark}.log`、`build/ui-library-motion-layout.log`、`build/ui-library-acrylic-motion.log`。窄屏整页浮层测试临时更改的模拟器显示配置已恢复，前后记录 `build/ui-library-emulator-display-{before,after}.txt`。
- 隔离 Debug／androidTest／lintDebug 通过，日志 `build/ui-library-validation-final-build.log`；正式 Release／lintRelease 通过，日志 `build/ui-library-release-build.log`。合成操作仅在 `com.donglan.chrona.followup.validation`，未重新操作用户手机。首轮截图被遗留的系统通知栏遮挡，未用作视觉证据；关闭该栏、增加前台焦点校验后重新取得实际画面。
- 专门 UI 审核代理对照原备份、调用者及有效日志并检查实际截图，确认职责与迁移完整、功能和参数保留、缩放生命周期修复正确，本轮审核通过，无待修缺陷。作业通知的系统宿主、完成收起与最终发布缺口继续见[当前工作单](../HANDOFF.md)。恢复副本 `.local-backups/ui-library-organize-20261020-160028/`。

## 0.15.29 — 新作业信息卡、独立同步频率与详情精简 — 2026-10-20

- 复用 `VivoAtomicNotification` 已注册 SuperX 通道，新增 `HomeworkNotice` 信息卡及独立通知 ID。正常／岛上 RemoteViews 复用原子卡壁纸／极光；标题、截止、正文与 48dp Lucide「×」，不包含地点、进度或完成动作。系统超时 600000ms，另设结束闹钟清理 OEM 身份；相同通知的关闭／过期按本次 expiry 校验，旧回调不关闭新提示。
- `ZjuHomeworkStore.Result.newHomework` 只包含事务中新增且未完成的作业；重复拉取、普通内容修改、已提交新导入均不发新作业提示。真实 SQLite upsert 检查扩至 45 项通过；原有日历关联、删除和个人完成状态保持。
- 作业 300000ms、课表 1800000ms 独立尝试时间；单次 Job 按最早到期来源重排，前台保留到期检查。手动全量刷新与后台单来源请求共用单个网络 owner，缺失 scope 排队，取消后新请求使用新 epoch；独立 fetch 共用一次内存认证会话，失败来源不应用空结果。`zju_sync_cadence_check.py` 22 项通过，覆盖两类开关、联网／持久 Job、单来源、部分失败、凭据失败防重试风暴、排队与取消、前后台生命周期。
- 学校作业详情整组附件／原文／重新解析区域不加入布局；普通收件保留。独立包原生检查覆盖两种详情、卡片两类宿主布局、真实通知 600000ms、提前／旧过期保护及真实「×」PendingIntent，关闭后本地作业完成字段和原文均未修改。正常浅色及 320dp／字体 1.45／深色日志 `build/homework-notice-{light,dark}.log`；图片 `build/homework-{detail,ordinary-retained,card-island,card-shade}-{light,dark}.png`。岛上截图是 RemoteViews 在模拟器中的实际布局；未获得本轮 vivo OEM 宿主的新样式画面。
- 通知权限／频道检查 650 项、原子协议生命周期 111 项及原卡动画资源合同通过。Debug、androidTest 与 lintDebug 首轮通过；最终正式包、独立 UI 复审及发布证据待补。源码备份 `.local-backups/homework-notice-sync-20261020/`。

## 0.15.28 — 菜单分隔行、空时间提示与窄屏筛选 — 2026-10-20

- 旧筛选栏标签固定保留 14sp、按完整字宽分配。正常宽度保持原 Dock；放不下时在原行显示当前值与 Lucide 下拉图标，公共面板完整列出选项和计数。第一版横向视口被独立审核以静态大段留白、滚动中间高光仍可能裁切两项 P2 否决，已撤销其全部视口与公共 Dock 改动；新方案复审关闭两项 P2，原 Dock 参数无改动。正常浅色与 320dp／字体 1.45／深色的两页切换、取消、中间与末项选择通过。
- 纵向浮动菜单改用公共 `UiStyle.addMenuItem`：透明文字行、相邻 1dp 分隔、左对齐、选中勾及整行涟漪，无独立边框和缩放；设置／筛选选择、真实课程关联预览与主来源子菜单、详情操作均覆盖。`build/menu-style-{light,dark}.log` 两组各 164 项通过，包含长标签、长列表底部可达、首项长按无缩放及取消不写课程规则。实际图为 `build/menu-{short,held,long-top,long-bottom,course-actions,course-source,detail}-{light,dark}.png`；用户同款筛选图为 `build/filter-menu-{1,2}-dark-open.png`。
- 空开始提示精确为「欸~这好像还没有时间」，空结束为「遥遥路途，没有终点~」。`build/time-hints-{light,dark}.log` 通过，核对空值与文字、正常／大字体渲染、实际点按日期选择器、取消、全天切换及填值不被提示替代；图为 `build/detail-time-hints-{light,dark}.png`。相关文档原空白规则已按用户新要求替换。
- 本轮两次 UI 独立复审均通过，菜单与提示本轮核对 18 张实际图。离线菜单合同更新为 88 项、40 个回归负例，覆盖 API 26 与 36；旧放大按钮包络检查随菜单不缩放的新合同替换。Dock 180、计数 15、详情编辑 23 及 LAN 7 项通过；其他未受影响检查沿用下节有效结果。首次正式 lint 发现 API 30 的 `setStateDescription` 调用未设门槛，已修正并重新通过 Debug／Release、lintDebug／lintRelease，日志 `build/0.15.28-release-build.log`。
- 新 debug／release APK 验签通过，证书与旧版一致。正式包 `build/distributions/0.15.28/Chrona-0.15.28.apk` 为 `com.donglan.chrona`、190 / 0.15.28、3072378 字节，无 debuggable；发布资产 SHA-256 为 `a663130bf9b49b1f47a409240fb74cbcee24ada72b005d79292a0b027ccff19a`。GitHub 已登录，待发布及旧版手动发现更新／模拟器覆盖安装；实体手机非空数据、真实付费 AI、真实远端 WebDAV 本轮仍未验收。
- 恢复副本 `.local-backups/filter-overflow-20261020/`、`.local-backups/menu-and-time-hints-20261020/`；下节 0.15.27 保留源码标签，全部功能合入本版，不另发包。

## 0.15.27 — 切周、课表一致性与静默推理 — 2026-10-20

- 课堂周表复用双轴滚动及边缘切周，旧／新原生视图用同一进度滑动，日期栏保持原实例。Android 36 独立模拟器在 420dpi／字体 1／浅色与 540dpi／320dp／字体 1.45／深色各 34 项通过，日志 `build/week-timetable-{light,dark}.log`。覆盖表内平移、两端切周、短拖、纵滑、取消、多指、连续反向、过渡层清理及关闭动画后保留横向位置。
- 学校课表复用导入的常规／特殊分类，单次调课保留实际日期和只读详情；离线检查覆盖真实重复周末课及短学期日期保留。设备检查还监听双向切学期的首次绘制：每个重建网格均直接在保留的 x=320 绘制，再开始入场；学校原账号数据未现场读取。
- 首页系统日程开关取消启用背景，仅改变图标颜色。深浅两组 `build/home-toggle-{light,dark}.log` 各 12 项通过，覆盖 off/on/off、偏好写入、原生无障碍状态、颜色末态和图标外透明像素。日历页原开关样式保留。
- UI 审核初次发现关闭动画时横向位置丢失，已补一次布局后的恢复；测试曾在过渡准备间隙抓到日期栏／网格不同周，现同时断言已显示周、无过渡、完成布局及实际网格日期后再截图。最新稳定画面与中途暂停帧为 `build/classroom-week-{light,dark}.png`、`build/classroom-week-mid-{light,dark}.png`；暂停帧只证明空间关系，不代表真实中点耗时。
- AI 所有内容类型统一 180 秒读等待，保留 30 秒连接超时和日程整轮 240 秒预算。真实 loopback HTTP 文本请求静默 31 秒后只返回内容 SSE，成功完成；取消及短总预算检查仍通过。`classroom_summary_check.py` 最终 140 项通过，未调用付费服务。
- 发布完整离线组首轮 81/88，通过部分见 `build/0.15.27-offline-full.log`。7 个失败均为随已改接口／布局过时的测试适配：轮播布局替身、选择框颜色入口、详情底部删除位置、周操作代理、首页图标开关构造、课堂共享文档编译依赖和同步课堂适配器。补齐后这 7 个入口逐项复跑通过：轮播 108、选择框 720、完成提醒 32、日历 139、周手势、图片 10、同步锁 3；未重复运行仍有效的另外 81 项。
- 本轮源码恢复点 `.local-backups/week-and-timetable-20261020/`，发布元数据和测试适配恢复点 `.local-backups/release-0.15.27/`。此前 Markdown、分享、同步／备份等证据见下列续修记录。实体手机当前未连接，未进行本轮真实账号、付费 AI 或真实 WebDAV 验证。
- 专门 UI 审核复审通过，本轮 P2 和稳定帧证据缺口均关闭；此前各续轮已通过的结论继续有效。正式 Debug／Release、lintDebug／lintRelease 通过，日志 `build/0.15.27-release-build.log`。源码及 `v0.15.27` 标签已推送，构建源码为 `d58538e`；本版本保留源码检查点，全部内容合入上节 0.15.28。
- 新 debug、release 与已发布 0.15.26 的 APK 签名一致。正式包 `build/distributions/0.15.27/Chrona-0.15.27.apk` 为 `com.donglan.chrona`、189 / 0.15.27、3070626 字节且无 debuggable 标志；Android 36 模拟器从正式 188 覆盖安装到 189、启动首页成功，未卸载或清数据。实体手机的非空真实数据升级仍未验收。

## 未发布续修 — 导出后系统分享与下载目录 — 2026-10-20

- 课堂总结 ZIP 与周日程 PNG 保存成功后自动打开 Android 系统分享面板，使用精确 MIME、内容 URI、ClipData 与临时只读授权。流写入及关闭成功后才排队分享；取消保存或写入失败不启动分享，取消分享保留文件。暂停期间等待页面恢复，已消费的分享不重复弹出。
- Android 10 及以上的周日程 PNG 复用现有 `TaskFileStore` 写入公共 `Download/Chrona`，发布完成后分享；Android 8–9 沿用文件保存器并默认定位下载目录。课堂 ZIP 保留选择保存位置的流程。
- Android 36 独立模拟器分别以 420dpi／字体 1／浅色和 540dpi／字体 1.45／深色完成 ZIP 与 PNG 四组检查，各 12 项通过。检查真实 ZIP 条目和 PNG 解码、系统分享 Intent 与读权限、实际 Downloads URI 及 `RELATIVE_PATH=Download/Chrona/`、`IS_PENDING=0`、取消后文件可读、返回不重复分享，以及 ZIP 取消／写入失败不分享。实际打开系统面板后仅返回，未选择接收方。日志与截图 `build/export-share-{zip,week}-{light,dark}.{log,png}`。
- 独立审核发现日程导出期间重建页面会丢失分享回调，已改由工作区保留的 `WeekExport` 接交进行中任务与结果。确定性检查阻塞导出队列、发起任务、真实 `Activity.recreate`、验证新页接管同一运行状态，再放行写入并自动分享；Downloads 与 SAF copy 两条路径在上述两组环境各 16 项通过，日志及图片 `build/export-share-week{,-saf}-recreate-{light,dark}.{log,png}`。SAF copy 在 Android 36 投递文件选择结果后验证。独立 UI 复审已关闭该 P2，本轮审核通过。
- Debug、androidTest 与正式 Debug／Release、双 lint 通过，正式日志 `build/export-share-formal-build.log`；APK 签名验证通过、关闭调试，仍为 188 / 0.15.26。产物 `build/distributions/export-share-20261020/Chrona-0.15.26-export-share.apk`；恢复副本 `.local-backups/export-share-20261020/`。Android 8–9 分支及实体手机未现场验证。

## 未发布续修 — 课堂正文、素材清理与可移植文档 — 2026-10-20

- 课堂正文采用本地打包 Markdown-it 14.1.0 与 MathJax 3.2.2，支持 Obsidian 风格行内与独立公式、行内图片。课堂安排改为蓝色正文点击提取；导出进入原生标题栏右侧。图片预算统一为 40 张，数学提示仅保留“中国大陆教材的常用风格”，不硬编码具体记法。
- 完成文档、完整字幕与正文引用 JPEG 参与 WebDAV 和完整备份。统一图片引用清单用于渲染、ZIP、同步和清理；七天清理与手动清理保护引用图片、未完成及运行中课堂。导出统一相对图片路径；来源弹窗跳过清理掉的非引用图，避免空图片占位。列表和历史采用每页 50 条真实翻页，课程目录仍完整读取后排序。
- 实际已登录智云页面读取到 5 个 PPT `img src` 均使用 `http://video.cmc.zju.edu.cn/ai3/ppt/...`，均加载为 1280×720；这解释了原 HTTPS 校验与浏览器显示之间的差异。相同图片改为 HTTPS 的 HEAD 返回 200、image/jpeg、104917 字节。应用只在确切媒体域名、标准 HTTP 端口下本地升级至 HTTPS，保留其他安全限制；同时接受 content 对象或 JSON 字符串并跳过空 PPT 项。未导出 Cookie 或账号凭据。
- 离线总结 139 项、客户端 73 项与网络诊断 44 项通过；真实回环 HTTP 同步交换 22 项通过，包含课堂文档、图片先上传及另一端下载。Android 原生同步／完整备份 17 项通过，覆盖晚到本地修改保护、同基线恢复缺失缓存、运行中拒绝覆盖、恢复前图片缺失与失败保留、恢复后 Markdown／图片／字幕／历史可达。日志 `build/classroom-assets-summary-check.log`、`build/classroom-assets-client-check.log`、`build/classroom-sync-exchange.log`、`build/classroom-portability-device.log`。
- Android 36 独立验证包：完整课堂浅色流程 38 项；420dpi／字体 1 浅色与 540dpi／字体 1.45 深色 Markdown 各 11 项，覆盖分式、极限、矩阵、积分、代码美元符号、图片去重、来源弹窗、蓝色安排原生确认。首次实际截图发现 MathJax 辅助 MathML 重复显示，已保留无障碍节点并以标准裁切隐藏视觉副本。截图 `build/classroom-current-screens/classroom-markdown-*.png` 与 `build/classroom-markdown-source-{light,dark}.png`。
- 学校设置保存／退出同行、同步圆形图标置顶；窄屏四字按钮初次换行后改为 12dp 水平内距，保持字号与点击宽度。两组设置各 4 项通过并实际执行合成素材清理，图片 `build/classroom-settings-{top,cleanup}-{light,dark}.png`。
- 公共胶囊端部按圆角单元可见面积居中，日历周导出槽为 45dp、分隔无额外横向留白。两组日历的收拢、反向恢复、日期及首尾持有检查通过，图片独立保存在 `build/classroom-calendar-{light,dark}/`。初次检查的 49dp 旧夹具断言已更新；批量拉取目录曾混入旧截图，已排除，深色公共页面有效图片在 `build/classroom-current-screens/files/`。独立 UI 审核通过本轮课堂、设置及胶囊／日历候选。
- 独立遗留 P2：旧 `DashboardActivity.filterDock` 在窄屏大字体下标签挤连、同组字号不同；本轮未修改该筛选栏，后续应独立修复并复审。保留已确认选择栏动效，极端甩动允许高光出屏。
- 正式 Debug／Release 和双 lint 通过，日志 `build/classroom-assets-formal-build.log`；APK 签名校验通过、关闭调试，仍为 188 / 0.15.26，未发布 GitHub Release。安装包 `build/distributions/classroom-20261020/Chrona-0.15.26-classroom.apk`。
- 未执行真实付费 AI 生成、真实远端 WebDAV 交换或实体手机安装；现有真实页面证据仅验证 PPT 地址与显示，不代表整条账号生成链路已在手机验收。源码恢复副本 `.local-backups/classroom-assets-20261020/`。

## 未发布续修 — 记录移除、标题栏动画与时间完整显示 — 2026-10-20

- 移除独立记录的 AI schema、解析工厂、原生／网页编辑转换和筛选入口；提示词升为 `schedule-v13`，仅输出 `events`。纯信息留在原文。旧记录保留存储，详情接口不返回且拒绝编辑；未增加数据迁移或删除。
- `WorkspacePageHeader` 两侧统一 8dp；导入来源下刷新按钮按当前进度渐隐、缩小并收起宽度及间距，退出开始即停止点击／无障碍入口，反向连续恢复。未改变选择栏内部留白或弹簧参数。
- 详情开始／结束／截止字段自然换行、不缩字不省略。`DetailRepeatUiCheck -scope values` 在 420dpi／字体 1／浅色和 540dpi（320dp）／字体 1.45／深色通过完整行宽、高度、字符及实际点按选择器和取消检查；独立旧记录卡片缺席而原文保留。首次用重复实例点按进入了重复页，改用普通事项 fixture 后通过，不属于选择器失败。截图 `build/detail-values-light.png`、`build/detail-values-narrow-dark.png`。
- `ClassroomDeviceCheck -scope header` 同样两组环境通过：课表与课堂 8dp、实际切来源、无残留空槽、动画中间帧及反向续接。完整课堂设备检查 37 项通过，见 `build/header-classroom-full-check.log`。截图 `build/header-*-light.png`、`build/header-*-narrow-dark.png`；模拟器恢复 420dpi／字体 1。
- `checks/ai_auto_publish_check.py` 通过请求协议、59 项时间规范化、363 项执行排期、16 项发布检查（`build/record-ai-check.log`）。收件查询 8 项 SQLite 与 4 项搜索检查通过。真实 Android SQLite／HTTP 集成检查通过旧记录拒绝编辑、详情过滤、原文与历史行保留，以及普通日程增删改和状态操作。
- 实际 Edge 网页专项、完整交互和来源／同步／附件检查通过，日志分别为 `build/record-removal-web-focused.log`、`build/record-removal-web-check.log`、`build/record-removal-web-features.log`。完整脚本的旧「今日安排」标题已更新为现行「首页」；动画测试显式设置 no-preference，仍单独覆盖 reduced-motion，避免依赖宿主系统偏好。已查看桌面与移动端截图 `build/lan-web-checks/record-removal-*.png`。
- 专门只读 UI 审核亲自检查 9 张指定截图与候选代码，确认时间截断关闭，本轮间距、刷新退出／恢复及记录 UI 移除通过，无需退回的实质缺陷。
- 缺课调查：真实手机刷新后本周 10/19 音响技术仍缺席，上周 10/12 有回放，教务课表正常。用户随后确认单双周安排中一周不录像；保持实际回放目录，不补造课堂条目。
- Debug、androidTest、lintDebug 与正式 Release、lintRelease 均通过（`build/record-header-validation-build.log`、`build/record-header-phone-build.log`）。11:26 无线 ADB `install -r` 成功，首页启动 `Status: ok`；手机导入页已显示无刷新空槽。实体机后续页面发生切换，停止追加自动操作，未做完整实体机回归；主要交互与窄屏验收使用独立模拟器。正式包保留 188 / 0.15.26，未发布 GitHub Release。恢复副本及安装前 APK：`.local-backups/remove-inbox-record-20261020/`。

## 未发布续修 — 详情操作、重复页与专门 UI 审核 — 2026-10-20

- 详情候选卡片的重复和删除同行置底，位于原始内容之前；空执行开始／结束字段不再显示占位，切换全天仍保持空白。重复页建立／停止放在公共标题栏，月份使用 `DateIntervalBar`；日程名称与规则合为一个可换行文本区，正常字号首实例由约 y=722px 提前至 y=590px（420dpi）。学期标题整卡居中、下划线，右侧保留「10 门」。
- 专门只读审核代理 `ui_contract_reviewer` 首轮发现重复页层级和间距仍偏高、实例点击重建整页；修正后复审关闭两项。实例点击现在仅更新选中态；原生回归滚动到后段实例、打开并取消操作，验证相同 ScrollView、scrollY 及日期栏实例。`AGENTS.md` 已规定每轮 UI 改动必须独立审核，修正后交回复审。
- 表单补测实际发现窄屏键盘遮住次数输入：视口仍有全屏高度，键盘仅作为其底部 padding，焦点滚动计算使用了错误可见高度。`SettingsPageLayout` 改由视口外安全区容器承接系统／键盘占位。失败证据 `build/detail-repeat-review-form-dark.log`；修复后 `build/detail-repeat-ime-dark.log`、`build/detail-repeat-ime-light.log` 均通过，覆盖每周、结束日期、次数输入完整滚到键盘上方、收起键盘后创建，以及删除确认、建立／停止和日期双向滑动。
- Android 36 独立验证包分别使用 420dpi／字体 1.0／浅色和 540dpi（320dp 宽）／字体 1.5／深色；深色使用长日程名称。学期标题正常与窄屏检查通过，日志 `build/term-heading-light.log`、`build/term-heading-narrow-dark.log`。已查看实际底部操作与原始内容、空时间、重复页、表单键盘和学期截图，位于 `build/detail-repeat-captures/`。
- 公共安全区调整后的 20 个原生页面仍保持返回按钮距安全区 16dp、左侧 20dp、点击区域 48dp，见 `build/detail-repeat-ime-headers.log`；设置布局 72 组替身及原错误反证通过，见 `build/detail-repeat-settings-layout.log`。Debug、androidTest 和 lintDebug 成功，见 `build/detail-repeat-ime-build.log`。
- 最终专门复审亲自检查深浅色键盘截图、最终原生断言和公共标题回归，确认键盘阻塞关闭，本轮改动审核通过，未发现新的实质缺陷。
- 审核另记录既有详情时间值在窄屏大字体下被单行截断，本轮未修复，不能将本次结果表述为详情页全部适配通过。未变动的选择栏动效沿用下一节同一候选的有效证据。模拟器测试结束恢复 420dpi／字体 1.0；实体手机页面效果尚未验收。恢复副本 `.local-backups/detail-repeat-20261020/`、`.local-backups/ui-review-gate-20261020-103524/`。
- 用户随后要求推到手机：正式包名 Release 构建与 lintRelease 通过（`build/detail-repeat-phone-build.log`），安装包无 debuggable 标记。2026-10-20 10:56 经无线 ADB 使用 `install -r` 覆盖现有应用，安装成功、首页启动 `Status: ok`，未卸载或清数据。此次本地续修保留版本 188 / 0.15.26，未发布新的 GitHub Release。

## 未发布续修 — 选择栏、日期手势与跨月子项排期 — 2026-10-20

- 标题槽与日历选择栏的选项等宽填满底板，文字居中；保留原样式、弹簧、放大和惯性。按用户最新决定，极端甩动允许出屏，不再挤窄内容轨道。`WorkspacePageHeader` 在保持左右按钮位置的情况下最后绘制标题槽，高光覆盖刷新／取消按钮。
- Android 36 独立包 `com.donglan.chrona.followup.validation`：420dpi／默认字体深色与 540dpi（320dp 宽）／1.5 倍字体浅色均通过课堂 35 项原生检查，含真实触摸注入的日期双向滑动、工作区不误翻页、来源切换、标题绘制顺序及日期文字居中；选择栏另检查均分、首尾长按、拖出、双向甩动。日志 `build/selector-layer-dark.log`、`build/selector-final-classroom-narrow.log`。
- 实际截图发现日期文字偏上，修正公共日期栏的确定高度测量；窄屏复查又捕获两侧 dp 分别取整导致的 1px 高度不足，已按实际 margin 合计计算。日历常规与窄屏检查通过，含日期文字完整显示／裁切、视图均分、动效、操作胶囊和 12 个月年历；日志 `build/selector-final-calendar-dark.log`、`build/selector-final-calendar-narrow.log`。已查看最终正常日期、窄屏日期／日历，以及深浅主题下高光与刷新按钮重叠的截图，目录 `build/selector-captures/`。测试结束恢复 420dpi、字体 1.0。
- 课堂夹具曾被自己生成的课程悬浮通知挡住触摸。测试现在仅允许独立包，在测试期间关闭自身提醒并清除自身通知，结束恢复提醒偏好且关闭合成学校来源；没有更改正式应用的通知设置。
- `schedule-v12` 的响应分组排期通过生产响应解析入口验证：30／60 天、不同工作量、固定时间、独立提前截止、无真实截止的活动准备、过期边界、独立组、时间不足、排队延迟、跨夜窗口及非法协议。`ScheduleExecutionCheck` 363 项、语义时间 60 项、自动写入 16 项及请求协议检查通过，日志 `build/subtask-spread-check.log`；全部自动排期仍待用户确认。未调用真实付费 AI 服务，未验收模型识别分组的真实准确率。
- Debug、androidTest 构建与 lintDebug 通过，日志 `build/selector-final-build.log`；Dock 离线 180 项、筛选布局 17 项通过。修改前恢复点 `.local-backups/selector-20261020/`、`.local-backups/subtask-spread-20261020/`。本轮未发布、未覆盖正式应用；实体手机效果尚未验收。

## 0.15.26 — 发布验证 — 2026-10-20

- 正式包 Debug／Release 构建与两套 lint 通过，日志 `build/0.15.26-release-build.log`；quick 7/7 通过，日志 `build/0.15.26-quick.log`。本轮布局、手势与权限回退证据见下节。
- APK 包名 `com.donglan.chrona`，版本 188 / 0.15.26，无 debuggable 标志；新 debug、release 与旧 0.15.25 证书一致。分发产物 `build/distributions/0.15.26/Chrona-0.15.26.apk`。
- Android 36 模拟器从正式 0.15.25 覆盖升级成功并启动首页，未卸载或清数据。升级前首页无日程／待办，实体手机非空数据升级与本版本长时后台未验收；真实账号验证应用未被覆盖。
- 发布准备恢复点 `.local-backups/release-0.15.26/`，原 UI 源码备份保留在 `.local-backups/compact-layout-20261019/`。
- `v0.15.26` 已发布为 Latest，正式 Release 非草稿、非预发布，APK 下载返回 HTTP 200；上传资产大小与摘要通过发布脚本核对。正式应用进入设置 → 检查更新，实际显示「已是最新版本」、`v0.15.26` 和本次更新说明，记录 `build/0.15.26-update.xml`；发布信息见 `build/release-0.15.26-artifact.json`。

## 0.15.26 — 紧凑布局与全局标题栏 — 2026-10-19 至 2026-10-20

- 公共标题栏与 Lucide 返回／确认资源已统一。Android 36 独立验证包中测量 20 个原生页面，420dpi／默认字体与 480dpi／1.3 倍字体两组均测得返回按钮距系统安全区下沿 16dp、左侧 20dp，点击区域 48dp。记录页仍走草稿退出，详情页仍走原保存入口；日志 `build/compact-ui.log`、`build/compact-ui-narrow.log`。
- 首页操作胶囊包含作业、系统日程与设置；学校开关仅在同步构建测试标题栏时临时启用，随后还原，未触发网络任务。下一件事使用原两行标题区域放置地点，正常与大字体都检查高度不增加、文字字形互不相交；正常字体补充日程／收件箱与记录页渲染见 `build/compact-lists.log`。
- 课表与课堂保留来源选择实例；学校学期标题可点击，导入空状态靠上，课堂日期栏切周保留实例。课堂合成数据专项 29 项通过；窄屏大字体覆盖来源与课堂导航首尾长按、拖出松手及双向快速甩动，动效水平包络未越屏。证据 `build/compact-classroom.log`、`build/compact-classroom-narrow.log`，截图 `build/compact-captures/`。
- 日历开关胶囊、周导出收拢／反向切换及日期栏通过。初次全宽选择栏甩动捕获高光右界 1080.5127px（屏宽 1080px）；改为底板内部预留轨道包络后，420dpi／默认字体与 480dpi／1.3 倍字体的首尾长按、拖出松手、双向快速甩动均通过，弹簧参数保持原值。日志 `build/compact-calendar-inset.log`、`build/compact-calendar-inset-narrow.log`。实际查看常规与窄屏静止／长按截图；窄屏年历的日期粘连另通过自适应月份列数修复，12 个月网格均检查实际宽度。
- Debug、androidTest 编译与 lintDebug 通过，最新收尾日志 `build/compact-calendar-inset-build.log`，此前全局标题栏日志 `build/compact-final-validation-build.log`。设置安全区 72 项、日期切页 221 项、周视图、筛选布局 17 项、首页筛选／重试与候选反馈离线检查通过；首页重试替身补齐现有偏好参数边界。未调用付费 AI，未操作真实账号数据。
- 公共 Dock 队列替身 180 项通过，日志 `build/compact-dock-check.log`；替身补齐 Android margin 字段及横向滚动边界 API，实际动画仍以模拟器证据为准。新增内部轨道留白仅由日历显式启用，原全宽表单调用维持现状。
- 收尾复查修复权限拒绝的开关状态：`CompoundButton` 监听器同步把勾选退回关闭时，动画现在跟随最终状态；首页和日历同时刷新关闭提示。新增回归在旧实现得到错误进度 1.0，修复后开关专项 39 项通过；Android 原生监听器重入拒绝及正常开启均通过。证据 `build/compact-toggle-before.log`、`build/compact-toggle-after.log`、`build/compact-toggle-native.log`。补充 Debug、androidTest、lintDebug 构建通过，`build/compact-recheck-build.log`；未变动选择栏几何与动效参数，沿用前述实际渲染证据。
- 源文件恢复点 `.local-backups/compact-layout-20261019/`。模拟器经用户授权重新启动；测试完成后密度恢复 420、字体恢复 1.0。功能验收使用独立验证包，未覆盖真实账号验证包。

## 0.15.25 — 发布验证 — 2026-10-19

- Debug、Release、两套 lint 与 androidTest 编译通过，日志 `build/0.15.25-release-build.log`；quick 7/7 通过，日志 `build/0.15.25-quick.log`。功能专项与原生交互证据复用下节同一实现的结果，未重复无关 full。
- APK 为 `com.donglan.chrona`、187 / 0.15.25，无 debuggable 标志；新 debug/release 与旧 0.15.24 签名证书一致。产物 `build/distributions/0.15.25/Chrona-0.15.25.apk`。
- Android 36 模拟器原正式包从 186 覆盖升级至 187 并启动成功，无卸载或清数据。升级前日程页为空；此项不代表实体手机非空用户数据升级。真实账号的独立验证包不受本次正式包升级影响。
- 真实付费 AI 生成、实体手机长时后台及本版本应用内手动检查更新尚未验收。后台进程被系统结束后由用户手动续跑检查点。发布准备文件备份 `.local-backups/release-0.15.25/`。

## 0.15.24 — 发布后工作区与课堂续轮 — 2026-10-19

- [需求对照与验收](followup-acceptance-20261019.md)汇总本轮结果。正式候选 `assembleRelease`、`lintRelease` 通过，日志 `build/followup-final-release-build.log`；已有账号验证包 debug/lint 通过，日志 `build/followup-overlay-build.log`。未更新远端发布。
- 离线专项：两轮课堂总结 124 项、课堂客户端 66 项、网络分类 44 项、图片缓存 10 项、学校恢复 17 项；执行时间 288 项、既有归一化 60 项、偏好 22 项、自动写入门槛 16 项通过。受影响的配置备份、设置、日历、按压、开关与筛选专项通过，未重复执行无关 full。
- Android 36 模拟器原生验证：三页状态保留、标题/正文边缘交接、快速反向、浮动底栏、周历边界、日期切换与操作组合并通过。课堂深浅主题及窄屏最终 23 项通过；设置中的独立提示词与自定义时间选择器已检查。真实页面上打开的日期选择器确认为既有自定义滚轮样式。
- 日期文字独立裁切在正常与 360dp/1.3 倍字体下通过。像素比较最初发现箭头抗锯齿差值 4，定位后仅忽略该量级的通道差异；没有用扩大文字视口掩盖越界。外层按压放大完整保留。
- 亚克力生产取样检查 12 项通过；原生动画检查验证 ChangeBounds 中途帧、父级缩放平移、实际完成卡片上移、最终正确背景和静止停止重绘。公共选择栏因共享矩阵变化补验 27 组通过，日志 `build/followup-interaction-layout.log`。
- 原生后台会话检查验证返回后继续、重开接回、多个课堂并行、最后任务结束停止服务；通知检查验证实际系统投递、权限拒绝与账号切换。进程死亡后的自动重启未实现，手动续跑使用检查点；本轮未执行真实付费 AI 生成。
- 合成夹具限于 `com.donglan.chrona.followup.validation`。真实账号所在 `com.donglan.chrona.validation` 的 APK 与私有数据先备份至 `.local-backups/logged-validation-before-overlay-20261019/`，再覆盖安装。未卸载或清空其数据；已启用原本关闭的学校课表，读取到 10 门课程，既有 1158 条字幕与 130→24 张候选图缓存正常。原正式包未被覆盖。
- 截图位于 `build/followup-captures/`、`build/followup-narrow-captures/`、`build/followup-narrow-final/`。模拟器恢复为 420dpi、字体 1.0，继续运行软件图形后端；模拟器结果不代表实体手机帧率或长时后台。

## 0.15.24 — 重复日程与网页一致性 — 2026-10-19

- 既有 full 81 项首轮通过 63 项，18 项因测试替身缺少新增字段/依赖而失败；修复测试替身后分组复跑通过。日志 `build/recurrence-offline-full.log`、`build/recurrence-fixture-rerun*.log`。新增检查已纳入统一入口，共 84 项；未重复执行整轮。
- 重复 AI 67 项、既有归一化 60 项、实例投影 33 项、Calendar 字段 20 项通过。投影检查发现并修复夏令时持续时间偏移，使用固定持续时长与系统日历 DURATION 对齐。去重键新增重复语义后合并组复跑通过，日志 `build/recurrence-fixture-merge-final.log`。
- 网页模拟 API 浏览器检查覆盖排序、日期/类型联动、跨页全选删除、单次身份与重复操作、390px 布局；截图位于 `build/web-parity-checks/`。随后内置浏览器经 ADB 转发连接真机隔离验证服务，实际验证 16 条记录倒序、每日系列五次展开、首个实例从 09:00 改为 11:30，以及停止后详情显示「已停止」。停止后的月份空列表在手机断连后转到 Android 36 模拟器补验通过，显示「本月没有安排」，停止操作不再显示。
- 独立验证应用 `com.donglan.chrona.validation` 的 debug/androidTest 构建及 lintDebug 通过，日志 `build/recurrence-validation-build.log`。vivo 真机 `RepeatDeviceCheck` 22 项通过：真实 Provider 与本地展开一致、跳过、双向跨日期改期、查询/小组件投影、原生按钮及确认框停止、历史保留和旧基线拒绝。报告 `build/recurrence-device/provider-ui-report.txt`；已查看系列与停止状态截图，内容及控件未见裁切。
- `LanAccessInstrumentation` 真实 Android SQLite/HTTP 检查通过，新增覆盖真实排序、创建重复、旧 revision 拒绝、实例查询、跳过、改期身份、停止和批量删除。浏览器联动单独使用验证包 18765 端口；正式版原有 8765 服务保持运行。浏览器夹具限时 10 分钟并在退出时删除自建收件；无线调试断连后的清理尚未现场复核。

- 正式 Debug/Release、两套 lint 与 androidTest 构建通过，日志 `build/0.15.24-release-build.log`。APK 包名 `com.donglan.chrona`、186 / 0.15.24，无 debuggable 标志；新 debug/release 及旧版签名证书一致。
- 模拟器从 0.15.23 覆盖升级到新 debug，再覆盖 release 并启动成功；数据库 13→14，旧列逐项比较通过。旧库收件与候选均为 0，此检查不代表真实用户非空库升级。恢复副本 `build/upgrade-0.15.24/before.tar`。浏览器模拟器夹具退出后实际收件数为 0，临时转发已移除；模拟器保持开启。

## 0.15.24 — 课堂总结 — 2026-10-19

- 既有离线 `full` 79/79 通过，日志 `build/classroom-full-regression.log`；课堂两项专项已加入统一入口。随后共享样式改动涉及的 Dock、亚克力、设置布局、弹窗、小组件偏好、周历及学校 UI 七项专项全部通过，未重复无关检查。
- 修复待导出 URI 恢复抢占本地目录/历史初始化；旋转复用运行状态，待导出操作只消费一次。生成确认框改用公共可滚动正文并适配键盘 resize。
- 按当前轻圆角要求统一普通按钮/输入框 8dp、卡片和小组件 10dp、弹窗 12dp、标签 6dp；选择栏保留独立 28dp 底板与原动态高光几何。卡片阴影不再依赖圆角数值判定。修改前副本位于 `.local-backups/classroom-summary-20261019-163102/ui-refinement/`。
- 上述修复后的 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 再次通过，日志 `build/classroom-ui-refinement-build.log`。真实窄屏、大字体、键盘及恢复交互仍待原生环境验收。
- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过，日志 `build/classroom-native-build-final.log`。首轮发现导出 URI 授权参数的 lint 错误，修复后通过。
- `checks/classroom_summary_check.py`：既有 AI 请求检查及课堂总结 77 项断言通过。覆盖真实循环回送 HTTP、SSE、取消与超时、分段续跑、引用校验、图片预算、账号隔离和离线 Markdown ZIP。Android 位图和原子文件使用 JVM 替身，尚不能代表 Android 运行结果。
- `checks/classroom_client_check.py`：42 项检查通过。生产 Java 客户端另成功读取 179 节课堂、49 节就绪；选中课堂返回 1158 条字幕、130 个截图地址，并成功下载一张 101988 字节的图片。验证仅进行只读访问，日志不保存登录会话或课程正文。
- 发现并修复：连续尾图突破每段 4 张；部分总结误将尚未完成分析的计划图片写入已分析清单。修复后仅导出已完成分段的图片，另保留计划图片数。
- Android 36 模拟器已在重启后使用 WHPX；图形由 SwiftShader 切换为 RTX 5060，SurfaceFlinger 确认 NVIDIA 渲染器。镜像、AVD、日志与启动脚本均在 F 盘。未操作手机。
- 原生课堂检查深浅主题各 11 项通过，覆盖合成课堂 50→72 分页、历史打开、Android 位图去重、AtomicFile/ZIP、账号停用退出与生成弹窗。320dp、1.5 倍字体的截图暴露日期与时间断行、导航字号不一致，修复后复拍确认。键盘可见状态经 WindowInsets 验证，滚动后取消操作完整可见。测试数据未调用外部 AI。
- 既有公共组件 `interaction_layout` 27 组原生布局通过，覆盖 320/360/640dp、1/1.5/2 倍字体、首尾长按/拖出/快速甩动；`interaction_acceptance` 日历初开、未缓存切换、缓存返回、长按拖出与快速反向通过。该结果不代表实体手机帧率。
- 证据位于 `build/classroom-native/`：`light-final-report.txt`、`keyboard-verified-report.txt`、`week-fixed.png`、`dialog-keyboard-verified.png`、`interaction-layout.txt`、`interaction-acceptance.txt`。窄屏修复后的 debug/androidTest/lint 通过，日志 `narrow-fix-build.log`。尚未正式发布。

## 0.15.23 — 2026-10-18

- 发布候选 debug/release、lintDebug/lintRelease 与 androidTest 编译通过，日志 `build/0.15.23-publish-build.log`。APK 185 / 0.15.23，无 debuggable 标记，debug/release 签名符合原证书要求。
- 集中 full 首轮 70/79；九个旧检查的替身接口、提取签名或旧分类断言未同步，修复后仅重跑失败项，全部通过。未为测试改变生产动效。
- 有效截止按 due/end/start 的模型、实际 SQLite 筛选/提醒、浏览器首页/列表/详情及编辑保留原始 due 检查通过；浏览器专项 `checks/deadline_consumers_check.cjs`。
- 浙大生产客户端的拦截 HTTP 与隔离密钥 fixture 17 项、真实 SQLite 作业更新和绑定隔离 41 项通过。学校公开入口从当前电脑访问出现 TLS 失败／超时，未执行真实登录、学校同步或 Android Keystore 验证；无真机覆盖升级验收。

- 网页补齐续轮：年历闰日、跨年导航、月日互跳、截断提示、周密度和 320/390/600px 布局专项通过；实际检查桌面浅色与手机深色年历截图，见 `build/lan-calendar-checks/`。日历原有 parity 检查通过。
- 新增自动同步开关与冲突字段展示后，同步 API 检查增至 67 项。浏览器新增开关失败回退、附件 409 后显式重试、纯附件占位原文、记录候选删除及托管收件操作限制检查通过。首次删除测试误选背景同名按钮，改为浮层内定位后通过；未改动用户记录。

- 最终 `assembleRelease`、`lintRelease`、`assembleDebugAndroidTest` 通过，日志 `build/0.15.23-final-build.log`。未安装手机或执行 instrumentation。
- Edge 浏览器核心、功能对齐及新增功能检查通过，使用模拟手机 API；覆盖日／周／月与手机自然日、筛选和批量写入、来源分页、附件失败重试、冲突版本预览与明确删除确认。深浅主题、移动宽度实际截图已检查；5 种主色在深浅主题的主按钮对比均至少 4.5。
- 真实 SQLite 快照 16 项、类型与持久化 66 项、共享审核状态 45 项、合并 99 项、批量写入 19 项通过；真实 HTTP 同步交换 16 项通过。
- 新生产适配器检查通过：来源 API 20 项、同步 API 17 项、附件限长流式上传及回滚；同步锁 3 个 latch 场景覆盖慢网络期间本地访问、编辑保留和恢复使旧结果失效。适配器检查不代替真实 Android Provider 或 WebDAV 多机验证。
- 截图与对比数据：`build/lan-feature-checks/`；浏览器核心与日历证据：`build/lan-web-checks/`、`build/lan-parity-checks/`。源码恢复副本：`build/backups/20261018-project-hardening/`。

## 0.15.22 — 2026-10-17

- 同步空状态沿用 14sp 文本，切为主题次要色；左右 16dp、上 12dp、下 16dp 的真实内容留白，填满卡片内容宽度。不改变按钮、同步逻辑或其他状态。
- release 构建与 lintRelease 通过，USB 覆盖安装 184 成功。导航复查未到达目标页，后续设备断开；尚无修复后实际截图，不标为原生视觉通过。没有再次执行真实同步、改变账号或处理冲突。
- 构建日志 `build/0.15.22-build.log`，源码备份 `build/backups/20261017-sync-empty-state/`。旧库标准备份快照问题仍保留在交接页，未在本次 UI 修复中处理。
