# AI 解析链路（0.13.34）

## 时间与存疑提示（0.13.53）

固定前缀升级为 `schedule-v3`：能依据明确事实、相对时间或合理上下文推定具体时间时应填写；完全无法确定才留空，不凭空给无依据的事项指定钟点。优先明确事实，再使用上下文，最后标记未定；避免反复考虑已确定选择和假设边界，时间戳、默认时长和校验仍由程序完成。标题以具体动作和对象或真实活动名称概括，覆盖合并动作；邮箱及提交链接放备注，不作地点。

`uncertainty_level` 为 0 明确（勾）、1 推定（问号）、2 高存疑（警告）。推定保留具体时间并要求确认；程序发现时间缺失、非法字段或活动冲突时提升到高存疑。首页提示未写入的存疑事项，日程列表和详情显示分级；明确也仍需用户确认后写入。用户确认后清为明确，单独编辑备注保持原等级。数据库 v9 与备份恢复共用迁移，旧确认标志只能保守映射为高存疑。

按产品约定，某一天的 `24:00` / `24:00:00` 转为同日 `23:59`，截止事项留在当天；`24:01` 等非法钟点仍拒绝。已通过 52 项时间/分级检查、真实 SQLite v8→v9 迁移检查及 Gradle 编译/lint。未执行真机或真实模型语义验收，提示词不能保证模型的实际思考长度。

调用链：记录/分享 → ProcessingJobService（读图片、普通附件元信息、链接正文）→ ChatCompletionClient（固定 Prompt + 动态 JSON，多模态请求）→ SemanticEventNormalizer → CandidateRules → TaskStore → 用户确认 → CalendarStore。日历写入与数据库 events 接口保持不变。

## 已确认并修正

- 原模型负责毫秒换算、默认时长和截止提醒；现在仅提取语义时间。Java 负责时区、相对偏移、全天 UTC 边界、默认时长与截止映射。无法确定或冲突的时间标记确认，避免猜测。
- 原时间重叠硬合并会丢失独立事项；现在 Prompt 判断语义合并，程序仅删除全部业务字段相同的草稿。活动冲突保留两项并标确认。
- 网络重试原先更换时间锚点，结构错误也会重试；现在一次解析共用锚点，只对网络/限流/服务端暂时故障重试，完成后的非法/截断响应不自动重复请求。
- 模型输出预览原先读取思考尾部；现在预览只读答案。完整输出分段保留思考空格、换行，仅格式化答案 JSON，并在格式化后分页。

## 固定前缀与缓存

`SchedulePrompt.VERSION = schedule-v2` 管理静态 system/rules/schema。动态数据放后续 user 消息，包含 ISO 8601 本地时间、IANA 时区、原文、链接正文和附件元信息，图片随后附加。固定部分不含时间或任务 ID；没有缓存填充。OpenAI 官方接口传版本化缓存键，DeepSeek 使用自动前缀缓存；真实 cached_tokens 来自供应商 usage，缺失保留 null。

输入预算：原文最多 24000 字符，超出明确报错；链接 4000、普通附件元信息 6000，截断有标记。JSON 编码有额外开销；附件元信息不是附件正文。外部数据由系统规则标记为不可信，但这不能保证模型绝不受提示注入影响。

## 配置与限制

设置新增自动/关闭/低/中/高/最高思考强度，包含在备份中。详细参数映射见 ai-request-notes.md。minSdk 26 支持本次使用的 java.time，无需额外依赖或服务。保留模型选择、视觉限制、附件、链接、输出思考内容和用户确认流程。

Java 17 离线检查：AiRequestCheck、SemanticEventNormalizerCheck（43 项）、ModelOutputCheck、v6→v7 来源字段迁移检查已通过。Gradle assembleDebug / lintDebug 已通过。未执行手机验收或真实供应商调用；实际语义准确率、Token 节省量、缓存命中率尚未测量。历史文件若空格已被写入前丢失，无法自动还原；本次修正显示阶段造成的丢失。完整输出首次加载/变化后的格式化目前在 UI 线程，最大约 8 MB；预览增量读取不走此路径。

## 时间编辑

起止字段点击打开同一浮层内的日期/时间选择器，全天只选择日期。确定后一次更新字段，取消或点空白关闭不修改时间。切类型仅重算程序生成的默认结束；AI 明确结束/时长、用户选择的结束均保留。全天切换保留原钟点，避免覆盖明确时间。

数据库 v7 新增 end_auto_generated 来源标记，旧记录默认 false，不按时长猜测来源。旧 v6 备份在暂存数据库事务中升级，v7 备份保留标记；events 输出接口和旧 EventCandidate 构造器保持兼容。

官方参数依据：[DeepSeek thinking](https://api-docs.deepseek.com/guides/thinking_mode/)、[DeepSeek cache](https://api-docs.deepseek.com/guides/kv_cache/)、[OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create)、[OpenAI cache](https://developers.openai.com/api/docs/guides/prompt-caching)。
