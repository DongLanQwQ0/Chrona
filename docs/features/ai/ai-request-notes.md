# AI 服务请求协议

请求保留用户所选地址、模型和全部图片，使用版本化静态规则／schema 前缀及有界动态 JSON，语义时间在本地规范化。能力参数按已识别的服务和模型发送；格式、缓存或流选项被明确拒绝时，只允许使用原消息和时间参照降级一次。完整但非法或截断的响应抛出 `ResponseException`，不自动重新调用模型。缓存统计仅来自服务 usage。

动态内容上限：原文 24000 字符，超出拒绝；抓取正文 4000、附件元信息 6000，截断带标记。上限针对数据字符串，JSON 转义与参照字段另有传输开销。

`checks/AiRequestCheck.java` 检查固定前缀、schema、ISO 时间、输入编码、上限、服务能力、思考选项、降级分类、图片拒绝、usage 与 SSE 思考字段。使用 Java 17 及可运行的 org.json JAR 编译 AI 类和对应候选／时间依赖，执行 `com.donglan.chrona.ai.AiRequestCheck`。Android SDK 的 JSON 替身仅能编译，不能执行该检查；检查不联网。

各版本实际检查见 [验证记录](../../validation-history.md)，处理链路见 [AI 解析链路](ai-pipeline.md)。离线通过不证明供应商实际执行或返回了思考文本。

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
