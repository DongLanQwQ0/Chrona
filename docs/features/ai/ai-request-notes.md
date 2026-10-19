# AI 服务请求协议

请求保留用户所选地址、模型和全部图片，使用版本化静态规则／schema 前缀及有界动态 JSON，语义时间在本地规范化。能力参数按已识别的服务和模型发送；格式、缓存或流选项被明确拒绝时，只允许使用原消息和时间参照降级一次。完整但非法或截断的响应抛出 `ResponseException`，不自动重新调用模型。缓存统计仅来自服务 usage。

动态内容上限：原文 24000 字符，超出拒绝；抓取正文 4000、附件元信息 6000，截断带标记。上限针对数据字符串，JSON 转义与参照字段另有传输开销。

`checks/AiRequestCheck.java` 检查固定前缀、schema、ISO 时间、输入编码、上限、服务能力、思考选项、降级分类、图片拒绝、usage 与 SSE 思考字段。使用 Java 17 及可运行的 org.json JAR 编译 AI 类和对应候选／时间依赖，执行 `com.donglan.chrona.ai.AiRequestCheck`。Android SDK 的 JSON 替身仅能编译，不能执行该检查；检查不联网。

各版本实际检查见 [验证记录](../../validation-history.md)，处理链路见 [AI 解析链路](ai-pipeline.md)。离线通过不证明供应商实际执行或返回了思考文本。

## 日程解析偏好

AI 服务设置中的工作时间默认 10:00–22:00；开始晚于结束表示跨午夜，起止相同不可保存。日程解析和课堂总结各有独立的自定义提示词，每项最多 8000 字符，语义偏好优先于各自默认规则；JSON schema、输出格式、安全边界与建议时间的待确认要求保持有效。

`SchedulePreferences` 独立保存这组偏好，不改变连接参数。`customPrompt` 仅进入日程请求，`classroomPrompt` 供课堂总结使用，两者互不混入。`ProcessingJobService` 在一轮请求开始时读取偏好，并让本轮重试使用同一份 system 内容。配置备份的 `schedulePreferences` 包含工作时间和两个完整自定义提示词；导入前验证类型、范围与长度，缺少该节时保留本机偏好。

`schedule-v11` 用 `execution_time_explicit` 和独立 `suggested_start` 区分来源与建议：原文执行时间仍写 `start/end`，模型自动选择写 `suggested_start`。响应缺少这两个字段即报格式错误。只有任务／作业有未来截止、原文无执行时间且 `start/end` 均为空时，转换器才补执行建议；原文明示时间和截止 `due` 均保持不变。

默认执行 30 分钟，原文或用户设置明确的时长优先。24 小时内到期固定以当前到截止的中点为目标，再选择最近可容纳的工作窗口，模型的 `suggested_start` 不改变该目标；超过 24 小时，模型按任务量在截止前 6–72 小时内选起点，默认靠近该范围中点。本地转换器把执行建议限制在实际当前时刻之后、截止之前及整个可用工作时段内；没有足够窗口时保留截止和空执行时段，等待确认。循环事项保留原始锚点，缺执行时间时等待确认。所有自动执行建议均为高存疑，用户确认后才写入日历。

自动安排只新增执行 `start/end`，不移动 `due` 或修改显式提醒。任务／作业的既有截止提醒仍以 `due` 为锚，生成较早执行时段不会直接提前通知。

相对词仍以输入创建时间解释，自动排期以响应完成时刻限制，避免队列等待后排进过去。工作时间按请求时区应用，即使原文截止使用其他时区。`checks/SchedulePreferencesCheck.java` 随 `python checks/config_backup_check.py` 检查两类提示词隔离、配置往返与校验；`checks/ScheduleExecutionCheck.java` 随 `python checks/ai_auto_publish_check.py` 检查排期、跨午夜、夏令时和响应解析。

## 已识别服务的参数

DeepSeek 官方接口：auto/low → low，medium/high → high，max → max，none 关闭思考。OpenAI 官方支持的文本模型使用严格 JSON Schema 与版本化 `prompt_cache_key`；auto → low，max → high，none 仅发送给已知支持的较新 GPT-5 家族，否则使用 low 及对应推理预算。

音频、实时、转写专用变体及未知端点仅使用保守 token 上限，保留模型与静态 schema 指令。格式、缓存和流选项只在明确兼容错误后移除一次；thinking／reasoning 参数被拒绝则显示错误，不静默丢弃用户设置。

## 吉吉 API 思考参数

`www.jiji.cc`、`api.jiji.cc`、`api-slb.jiji.cc` 三个精确域名使用独立能力分支，适配 `gpt-6`、`gpt-6.1` 的 luna/sol/astra 模型名称（实际格式为 `gpt-6-luna`、`gpt-6.1-sol` 等）。原模型和地址保持不变；不复制官方端点专用的 schema/cache 参数，不对任意代理域名猜测能力。

- Chat Completions：`reasoning_effort` 传入 low/medium/high；界面 auto 映射 low，max 映射 high，none 原样发送。具体模型若不接受所选值，显示错误，不自动改成别的强度。
- 使用文档指定的 `max_completion_tokens`：none 为8192，low/medium为16384，high/max为32768，包含模型内部推理预算。
- 已有 SSE/非流式通路读取服务实际返回的 `reasoning_content` 或 `reasoning` 文本；未返回时不伪造思考过程。启用推理与返回可见推理文本是两个独立结果。
- 离线测试覆盖三个域名、六种选项、特殊模型与伪造子域不匹配、拒绝参数不静默降级。未读取用户密钥、未发起付费调用；服务端是否执行和是否返回思考文本仍需有效调用验证。

依据：[Chat Completions](https://docs.jiji.cc/api/chat-completions)、[网络线路](https://docs.jiji.cc/environment/network)。服务推荐 GPT 推理模型采用 Responses，本轮保持 Chrona 现有 Chat Completions 协议，修复已证实的参数遗漏。
