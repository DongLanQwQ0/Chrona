# AI 服务请求协议

## 选择访问协议

AI服务在基础地址后提供「API协议」，复用原公共选择面板，可选Responses与Chat Completions，默认Responses。缺少协议的旧配置及旧备份也以Responses作为新请求默认值；选择只修改草稿，保存后生效，纳入十三项退出保护、旋转恢复、保留密钥保存及配置备份。图片拒绝标记按地址、模型、协议分别记录。

- Responses请求`/responses`：正文／图片为原生`input_text/input_image`，预算`max_output_tokens`、思考`reasoning.effort`、结构化输出`text.format`。不发送Chat专属字段；固定`store=false`、`truncation=disabled`，不托管或压缩历史。
- Chat Completions请求`/chat/completions`，保留原`messages`、参数和SSE。服务不支持所选协议时明确失败，不自动换协议／模型或重新付费请求；下文的一次参数降级仅适用于普通Chat请求。
- Responses只接受完整`completed`结果；流式必须收到`response.completed`，delta、`[DONE]`、EOF、failed或incomplete均不能提交。正文与可见推理按实际事件显示，完整原生响应保留。用量从`input_tokens/output_tokens/input_tokens_details.cached_tokens`映射到既有展示，原生usage也保留。
- 课堂每轮冻结协议。续跑不随全局选择改变，无协议字段的既有轮次继续原Chat协议，不转换旧前缀。新Responses轮次逐项保存原生input与服务完整output（含加密reasoning项），下一轮只追加，不改写旧项、不用previous_response_id替代本地历史；重新整理的新轮采用新协议。

实现依据：[Responses迁移](https://developers.openai.com/api/docs/guides/migrate-to-responses)、[typed流式事件](https://developers.openai.com/api/reference/resources/responses/streaming-events)。当前供应商／模型的支持情况需真实验证，协议名称不代表缓存命中。

请求保留用户所选地址、模型和全部图片，使用版本化静态规则／schema 前缀及有界动态 JSON，语义时间在本地规范化。能力参数按已识别的服务和模型发送；格式、缓存或流选项被明确拒绝时，只允许使用原消息和时间参照降级一次。完整但非法或截断的响应抛出 `ResponseException`，不自动重新调用模型。缓存统计仅来自服务 usage。

动态内容上限：原文 24000 字符，超出拒绝；抓取正文 4000、附件元信息 6000，截断带标记。上限针对数据字符串，JSON 转义与参照字段另有传输开销。

连接超时为 30 秒；文本、图片、SSE 和普通 JSON 响应统一允许连续 180 秒无新数据，容纳不返回可见思考文本的模型。日程解析仍受整轮 240 秒预算约束，取消立即断开当前连接；收到心跳或思考文本也不会延长整轮预算。不以思维链是否可见判断请求失败。

`checks/AiRequestCheck.java` 检查固定前缀、schema、ISO 时间、输入编码、上限、服务能力、思考选项、降级分类、图片拒绝、usage 与 SSE 思考字段。使用 Java 17 及可运行的 org.json JAR 编译 AI 类和对应候选／时间依赖，执行 `com.donglan.chrona.ai.AiRequestCheck`。Android SDK 的 JSON 替身仅能编译，不能执行该检查；检查不联网。

各版本实际检查见 [验证记录](../../validation-history.md)，处理链路见 [AI 解析链路](ai-pipeline.md)。离线通过不证明供应商实际执行或返回了思考文本。

## 日程解析偏好

AI 服务设置中的工作时间默认 10:00–22:00；开始晚于结束表示跨午夜，起止相同不可保存。日程解析和课堂总结各有独立的自定义提示词，每项最多 8000 字符，语义偏好优先于各自默认规则；JSON schema、输出格式、安全边界与自由建议时间的待确认要求保持有效。明确保存的时间偏好能唯一应用时，可按规则确定补全，并记录偏好依据。

AI服务设置比较进入页的实际表单基线：地址、协议、模型、密钥输入、思考强度、工作起止、两类提示词、增强整理、并发数、笔记整理强度和移动数据开关共十三项。标题返回与系统返回统一弹出「保存／取消／放弃」；改回原值直接退出。取消、弹窗返回或外部关闭保留草稿；放弃不写配置；保存沿用原校验，失败留页、成功退出。Activity重建保留全部草稿和原基线，不在离开前台时偷偷保存。图片输入重新允许仍是独立即时动作，不计作未保存表单修改。

`SchedulePreferences` 与地址、模型、密钥独立保存。`customPrompt` 仅进入日程请求，`classroomPrompt` 供课堂总结使用，两者互不混入。`ProcessingJobService` 在一轮请求开始时读取偏好，并让本轮重试使用同一份 system 内容。配置备份的 `schedulePreferences` 包含工作时间、两个完整自定义提示词、增强整理、并发数、笔记整理强度与`classroomCellular`；导入前验证类型、范围与长度，缺少该节时保留本机偏好。

## 课堂AI请求使用移动数据

「AI总结使用移动数据」默认开启，复用表单开关，保存后生效并纳入退出保护、旋转草稿与配置备份。每个新HTTP交换读取设置；切换不关闭已有连接，不改已发送的消息、冻结参数或缓存前缀。关闭时使用原系统默认网络。

开启时，单节、批量和监视自动总结共用按连接绑定的蜂窝网络，包含更正、规划、核图和整稿；学校素材下载、日程解析、局域网及其它应用不变。获取移动网络最多等待30秒，计入原任务总期限并响应取消；HTTP结束、失败或取消均释放网络请求。不启用移动数据、不更改默认SIM/Wi-Fi/VPN、不绑定整个进程；数据关闭、无覆盖或系统限制时明确失败，不偷偷切回Wi-Fi或重新付费请求。

连接使用所选蜂窝网络的DNS和系统代理，保留HTTPS证书校验与禁止重定向。若拾时自身仍受VPN接管，会提示先排除拾时或关闭此开关；已被VPN排除时不以系统存在VPN为由拦截。更换网络不能消除服务端502，断线仍按原检查点和手动授权恢复。

`schedule-v19` 的顶层仅含 `events` 数组，纯信息不生成候选。用 `execution_time_explicit` 和独立 `suggested_start` 区分来源与建议，并要求每项包含 `plan_group`、`plan_until`（无分组时均为 `null`）及七字段 `time_assessment`。缺少字段或应有依据、非法分组或无法解析的非空准备边界报格式错误，不静默退回临近截止排期。原文执行时间仍写 `start/end`，独立事项的模型建议写 `suggested_start`；分组子项的建议位置由程序计算。非空时间对象必须含布尔`date_exclusive`；仅日期截止保留原文日期和`due.time=null`，不含当日的截止由本地日历减一天再补23:59，含当日则补当天23:59。明确钟点不减一天；审计保留原始日期、边界、实际截止与所用规则，不把默认钟点冒充原文。详见[日期边界](ai-time-extraction.md#三类事项与独立截止)。

默认执行 30 分钟，原文或用户设置明确的时长优先；模型应按工作量估计较大任务的执行时长。单独事项默认以整个可执行起点区间的中点为目标，超过24小时可采用基于工作量的更早建议，并按剩余周期及执行时长留出修改提交缓冲，再映射到完整工作窗口。同一事项的多个子项按 `plan_group` 和输出顺序，利用实际当前时刻至 `plan_until` 的整个周期，尽早启动并留出产出反馈、返工和提交检查；固定执行时间分隔阶段、独立截止限制各自上界。为未来活动准备时可仅使用 `plan_until`，不新增 `due`。详见[多子项规则](ai-time-extraction.md#多个子项分散到整个准备周期)。

本地转换器把自由执行建议限制在实际当前时刻之后、准备边界及截止之前、完整工作时段内；没有足够窗口时保留原截止和空执行时段，等待确认。循环事项保留原始锚点，缺执行时间时等待确认。自由执行建议均为高存疑，普通收件解析需用户确认后才写入日历；下述浙大新作业开关可单独预先授权应用，模型存疑标记仍保留。明确保存的偏好唯一给出具体时刻及时长时保留确定取值，不用中点替换。偏好已过或违反截止时改为待确认替代建议。

时间依据区分 `explicit`、`rule_based`、`uncertain`，确定和存疑都必须给原文／偏好依据及短原因；本地转换后的实际字段另留校验和调整。每次解析的审计及关联随完整数据备份保存，重解析保留历史，已配对局域网 `GET /api/parse-audit?id=<收件ID>` 提供分页查询；按方案查询需同时指定 `runId` 和 `planGroup`。完整字段和删除／恢复边界见[时间审计](ai-time-extraction.md#确定与存疑的依据随解析留存)。

自动安排只新增执行 `start/end`，不移动 `due` 或修改显式提醒。任务／作业的既有截止提醒仍以 `due` 为锚，生成较早执行时段不会直接提前通知。

浙大新作业可通过独立、默认关闭的开关授权自动应用执行建议。该路径复用本页的请求、偏好与本地时间校验，但只更新同一官方作业的执行字段；不走整收件候选替换。模型存疑与应用授权分别留在审计，原截止和提醒保留。具体入队、竞态及恢复边界见[折姜星辰](../zju/zju-services.md)。

相对词仍以输入创建时间解释，自动排期以响应完成时刻限制，避免队列等待后排进过去。工作时间按请求时区应用，即使原文截止使用其他时区。`checks/SchedulePreferencesCheck.java` 随 `python checks/config_backup_check.py` 检查两类提示词隔离、配置往返与校验；`checks/ScheduleExecutionCheck.java` 随 `python checks/ai_auto_publish_check.py` 检查排期、跨午夜、夏令时和响应解析。

## 已识别服务的参数

DeepSeek 官方接口：auto/low → low，medium/high → high，max → max，none 关闭思考。OpenAI 官方支持的文本模型使用严格 JSON Schema 与版本化 `prompt_cache_key`；auto → low，max → high，none 仅发送给已知支持的较新 GPT-5 家族，否则使用 low 及对应推理预算。

音频、实时、转写专用变体及未知端点仅使用保守 token 上限，保留模型与静态 schema 指令。格式、缓存和流选项只在明确兼容错误后移除一次；thinking／reasoning 参数被拒绝则显示错误，不静默丢弃用户设置。

课堂新轮次输出预算提高至32768，沿用已识别的`max_completion_tokens`或`max_tokens`字段；已支持且上限较小的官方GPT-4o/mini保留原8192预算，不发送超限参数。不额外猜测未知模型的思考参数。核图与结构化笔记可能共用推理预算，不能普遍沿用短日程的8192上限。普通日程预算不变；已有课堂轮次的冻结选项原样保留，改变预算须重新整理并复用已提交的修正字幕，不改旧请求或拼接截断回复。服务不接受该预算时明确失败，不静默降级或自动重发。

## 吉吉 API 思考参数

`www.jiji.cc`、`api.jiji.cc`、`api-slb.jiji.cc` 三个精确域名使用独立能力分支，适配 `gpt-6`、`gpt-6.1` 的 luna/sol/astra 模型名称（实际格式为 `gpt-6-luna`、`gpt-6.1-sol` 等）。原模型和地址保持不变；不复制官方端点专用的 schema/cache 参数，不对任意代理域名猜测能力。

- Chat Completions：`reasoning_effort` 传入 low/medium/high；界面 auto 映射 low，max 映射 high，none 原样发送。具体模型若不接受所选值，显示错误，不自动改成别的强度。
- 使用文档指定的 `max_completion_tokens`：none 为8192，low/medium为16384，high/max为32768，包含模型内部推理预算。
- 已有 SSE/非流式通路读取服务实际返回的 `reasoning_content` 或 `reasoning` 文本；未返回时不伪造思考过程。启用推理与返回可见推理文本是两个独立结果。
- 离线测试覆盖三个域名、六种选项、特殊模型与伪造子域不匹配、拒绝参数不静默降级。未读取用户密钥、未发起付费调用；服务端是否执行和是否返回思考文本仍需有效调用验证。

依据：[Chat Completions](https://docs.jiji.cc/api/chat-completions)、[网络线路](https://docs.jiji.cc/environment/network)。以上为Chat参数；选择Responses时使用原生字段映射，不据协议名称推断服务支持所有模型或参数。
