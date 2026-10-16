# AI request v2

Implementation plan: preserve the selected endpoint/model and all image parts; keep a versioned static system/schema prefix, encode bounded dynamic data as JSON, and normalize semantic times locally. Official endpoint options are selected conservatively. A single explicit unsupported-option downgrade reuses the original messages and time anchor. Completed malformed/truncated responses raise ResponseException and must not trigger automatic model retries. Cache statistics come only from provider usage.

Dynamic content limits: raw text 24000 characters (reject excess), fetched text 4000, attachment metadata 6000 (truncate with flags). These limits cover data strings; JSON escaping and reference fields add transport overhead.

`checks/AiRequestCheck.java` exercises prefix/schema stability, ISO time, encoded input boundaries, limits, endpoint/effort options, downgrade classification, image rejection classification, usage and SSE reasoning. It requires a runnable org.json jar. Android SDK android.jar can compile these sources but its JSON stubs cannot execute the checks. Compile with Java 17, the selected JSON jar on the classpath, all ai classes, EventCandidate/EventCategory/EventTimeDefaults and SemanticEventNormalizer's dependencies; run `com.donglan.chrona.ai.AiRequestCheck`. No network requests are made by this check.

Validation performed: Java 17 compilation and `AiRequestCheck` execution passed with a desktop org.json runtime. This check sends no API requests. Provider and device validation have not been performed; build results are tracked in ai-pipeline.md.

Official DeepSeek: auto/low -> low; medium/high -> high; max -> max; none disables thinking. Official OpenAI supported text model families use strict JSON Schema and a versioned prompt_cache_key. Auto -> low; max -> high; none is passed only to known supported newer GPT-5 families, otherwise low with a reasoning-sized token budget. Specialized audio/realtime/transcription variants and unknown endpoints receive only a conservative token cap. They retain the selected model and static schema instructions. Optional format/cache/stream parameters are removed only after a named compatibility error, with one retry of the same messages. Rejected thinking/reasoning parameters surface the service error rather than silently dropping the selected setting.

## 吉吉 API 思考参数

`www.jiji.cc`、`api.jiji.cc`、`api-slb.jiji.cc` 三个精确域名使用独立能力分支，适配 `gpt-6`、`gpt-6.1` 的 luna/sol/astra 模型名称（实际格式为 `gpt-6-luna`、`gpt-6.1-sol` 等）。原模型和地址保持不变；不复制官方端点专用的 schema/cache 参数，不对任意代理域名猜测能力。

- Chat Completions：`reasoning_effort` 传入 low/medium/high；界面 auto 映射 low，max 映射 high，none 原样发送。具体模型若不接受所选值，显示错误，不自动改成别的强度。
- 使用文档指定的 `max_completion_tokens`：none 为8192，low/medium为16384，high/max为32768，包含模型内部推理预算。
- 已有 SSE/非流式通路读取服务实际返回的 `reasoning_content` 或 `reasoning` 文本；未返回时不伪造思考过程。启用推理与返回可见推理文本是两个独立结果。
- 离线测试覆盖三个域名、六种选项、特殊模型与伪造子域不匹配、拒绝参数不静默降级。未读取用户密钥、未发起付费调用；服务端是否执行和是否返回思考文本仍需有效调用验证。

依据：[Chat Completions](https://docs.jiji.cc/api/chat-completions)、[网络线路](https://docs.jiji.cc/environment/network)。服务推荐 GPT 推理模型采用 Responses，本轮保持 Chrona 现有 Chat Completions 协议，修复已证实的参数遗漏。
