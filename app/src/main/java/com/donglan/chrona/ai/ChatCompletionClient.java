package com.donglan.chrona.ai;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.net.RequestControl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Synchronous network client. Call parse from a worker thread, never the UI thread. */
public final class ChatCompletionClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    /** Hidden reasoning can stay silent for minutes, with or without images or SSE. */
    private static final int RESPONSE_READ_TIMEOUT_MILLIS = 180_000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    private final AiSettings settings;
    private final RequestControl control;
    private final String scheduleSystem;
    private final int workStartMinutes;
    private final int workEndMinutes;

    public ChatCompletionClient(AiSettings settings) {
        this(settings, null);
    }

    public ChatCompletionClient(AiSettings settings, RequestControl control) {
        this(settings, control, SchedulePrompt.SYSTEM);
    }

    public ChatCompletionClient(AiSettings settings, RequestControl control, String scheduleSystem) {
        this(settings, control, scheduleSystem,
                SemanticEventNormalizer.DEFAULT_WORK_START, SemanticEventNormalizer.DEFAULT_WORK_END);
    }

    public ChatCompletionClient(AiSettings settings, RequestControl control, String scheduleSystem,
            int workStartMinutes, int workEndMinutes) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.settings = settings;
        this.control = control;
        this.scheduleSystem = scheduleSystem;
        this.workStartMinutes = workStartMinutes;
        this.workEndMinutes = workEndMinutes;
    }

    /** The read timeout this client applies; exposed so diagnostics can report the real value. */
    public static int readTimeoutMillis(boolean withImage) {
        return RESPONSE_READ_TIMEOUT_MILLIS;
    }

    /** Parses one submitted input and returns all proposed entries plus reported token usage. */
    public ParseResult parse(long taskId, String rawText, byte[] imageJpeg, String linkText,
            long nowMillis, String timeZoneId) throws IOException {
        return parseInternal(taskId, rawText, imageJpeg == null
                        ? Collections.emptyList() : Collections.singletonList(imageJpeg),
                linkText, null, nowMillis, timeZoneId,
                null, false);
    }

    public interface PreviewSink {
        void append(String chunk) throws IOException;

        default void appendReasoning(String chunk) throws IOException { }
    }

    public ParseResult parse(long taskId, String rawText, byte[] imageJpeg, String linkText,
            long nowMillis, String timeZoneId, PreviewSink preview) throws IOException {
        return parseInternal(taskId, rawText, imageJpeg == null
                        ? Collections.emptyList() : Collections.singletonList(imageJpeg),
                linkText, null, nowMillis, timeZoneId,
                preview, true);
    }

    public ParseResult parseWithoutStreaming(long taskId, String rawText, byte[] imageJpeg,
            String linkText, long nowMillis, String timeZoneId, PreviewSink preview)
            throws IOException {
        return parseInternal(taskId, rawText, imageJpeg == null
                        ? Collections.emptyList() : Collections.singletonList(imageJpeg),
                linkText, null, nowMillis, timeZoneId,
                preview, false);
    }

    public ParseResult parseImages(long taskId, String rawText, List<byte[]> imagesJpeg,
            String linkText, long nowMillis, String timeZoneId, PreviewSink preview,
            boolean streaming) throws IOException {
        return parseImages(taskId, rawText, imagesJpeg, linkText, null, nowMillis, timeZoneId,
                preview, streaming);
    }

    public ParseResult parseImages(long taskId, String rawText, List<byte[]> imagesJpeg,
            String linkText, String attachmentMetadata, long nowMillis, String timeZoneId,
            PreviewSink preview, boolean streaming) throws IOException {
        return parseInternal(taskId, rawText, imagesJpeg, linkText, attachmentMetadata,
                nowMillis, timeZoneId, preview, streaming);
    }

    private ParseResult parseInternal(long taskId, String rawText, List<byte[]> imagesJpeg,
            String linkText, String attachmentMetadata, long nowMillis, String timeZoneId,
            PreviewSink preview, boolean streaming) throws IOException {
        RequestControl.check(control);
        boolean hasText = rawText != null && !rawText.trim().isEmpty();
        boolean hasImages = imagesJpeg != null && !imagesJpeg.isEmpty();
        boolean hasFileMetadata = attachmentMetadata != null && !attachmentMetadata.trim().isEmpty();
        if ((!hasText && !hasImages && !hasFileMetadata)
                || timeZoneId == null || timeZoneId.trim().isEmpty()) {
            throw new IllegalArgumentException("Text or image, and a timezone, are required");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("model", settings.model);
            long schedulingNowMillis = System.currentTimeMillis();
            JSONArray messages = SchedulePrompt.requestMessages(scheduleSystem, rawText, linkText,
                    attachmentMetadata, nowMillis, schedulingNowMillis, timeZoneId);
            JSONArray content = messages.getJSONObject(1).getJSONArray("content");
            if (hasImages) {
                for (byte[] imageJpeg : imagesJpeg) {
                    if (imageJpeg == null || imageJpeg.length == 0) continue;
                    content.put(new JSONObject().put("type", "image_url").put("image_url",
                            new JSONObject().put("url", "data:image/jpeg;base64,"
                                    + Base64.getEncoder().encodeToString(imageJpeg))));
                }
            }
            body.put("messages", messages);
            RequestOptions.apply(body, settings);
            body.put("stream", streaming);
            if (streaming) body.put("stream_options",
                    new JSONObject().put("include_usage", true));
        } catch (JSONException e) {
            throw new IOException("Could not encode AI request", e);
        }

        return execute(taskId, body, hasImages, preview, nowMillis, timeZoneId, true);
    }

    private ParseResult execute(long taskId, JSONObject body, boolean hasImages, PreviewSink preview,
            long nowMillis, String timeZoneId, boolean canDowngrade) throws IOException {
        String response = executeEnvelope(body, hasImages, preview, canDowngrade);
        ParseResult parsed = parseResponse(taskId, response, nowMillis, timeZoneId, System.currentTimeMillis(),
                workStartMinutes, workEndMinutes);
        try {
            JSONObject audit = new JSONObject(parsed.timeAuditJson).put("request", new JSONObject()
                    .put("system", body.getJSONArray("messages").getJSONObject(0).getString("content"))
                    .put("input", new JSONObject(body.getJSONArray("messages").getJSONObject(1)
                            .getJSONArray("content").getJSONObject(0).getString("text")))
                    .put("model", settings.model).put("reasoning_effort", settings.reasoningEffort));
            return new ParseResult(parsed.candidates, parsed.promptTokens, parsed.completionTokens,
                    parsed.totalTokens, parsed.cachedTokens, audit.toString());
        } catch (JSONException invalidAudit) { throw new IOException("Could not retain parse time evidence", invalidAudit); }
    }

    /** General completion uses the same cancellation, options and HTTP transport as capture parsing. */
    public String complete(String system, String prompt, List<byte[]> imagesJpeg) throws IOException {
        boolean hasImages = imagesJpeg != null && !imagesJpeg.isEmpty();
        try {
            JSONArray content = new JSONArray().put(new JSONObject().put("type", "text").put("text", prompt));
            if (hasImages) for (byte[] bytes : imagesJpeg) content.put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes))));
            JSONObject body = new JSONObject().put("model", settings.model).put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", system))
                    .put(new JSONObject().put("role", "user").put("content", content)));
            RequestOptions.apply(body, settings);
            body.remove("response_format"); body.remove("prompt_cache_key");
            body.put("stream", true).put("stream_options", new JSONObject().put("include_usage", true));
            JSONObject choice = new JSONObject(executeEnvelope(body, hasImages, null, true))
                    .getJSONArray("choices").getJSONObject(0);
            String finish = choice.optString("finish_reason");
            if ("length".equals(finish) || "content_filter".equals(finish)) throw new ResponseException("总结未完整返回，请重试当前分段");
            String result = StreamChunkContent.fromJsonValue(choice.getJSONObject("message").opt("content"));
            if (result.isBlank()) throw new ResponseException("AI 未返回总结内容");
            return result;
        } catch (JSONException e) { throw new ResponseException("无法读取 AI 总结响应", e); }
    }

    private String executeEnvelope(JSONObject body, boolean hasImages, PreviewSink preview,
            boolean canDowngrade) throws IOException {
        boolean streaming = body.optBoolean("stream");
        RequestControl.check(control);
        int readTimeout = control == null ? readTimeoutMillis(hasImages)
                : control.timeoutMillis(readTimeoutMillis(hasImages));
        HttpURLConnection connection = (HttpURLConnection) new URL(
                settings.baseUrl + "/chat/completions").openConnection();
        try {
            if (control != null) control.register(connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(control == null ? CONNECT_TIMEOUT_MILLIS
                    : control.timeoutMillis(CONNECT_TIMEOUT_MILLIS));
            connection.setReadTimeout(readTimeout);
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + settings.apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", !streaming
                    ? "application/json" : "text/event-stream, application/json");
            RequestControl.check(control);
            try (OutputStream output = connection.getOutputStream()) {
                RequestControl.check(control);
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (status < 200 || status >= 300) {
                String response = stream == null ? "" : readLimited(stream, control);
                String detail = errorMessage(response);
                if (canDowngrade && RequestOptions.downgrade(body, status, detail)) {
                    connection.disconnect();
                    return executeEnvelope(body, hasImages, preview, false);
                }
                throw new RequestException(status, "AI request failed (HTTP " + status + "): " + detail);
            }
            if (streaming && stream != null && connection.getContentType() != null
                    && connection.getContentType().toLowerCase(java.util.Locale.ROOT)
                    .contains("text/event-stream")) {
                return streamEnvelope(stream, preview, control);
            }
            String response = stream == null ? "" : readLimited(stream, control);
            if (preview != null) {
                try {
                    JSONObject message = new JSONObject(response).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message");
                    String reasoning = reasoningFrom(message);
                    if (!reasoning.isEmpty()) preview.appendReasoning(reasoning);
                    String content = StreamChunkContent.fromJsonValue(message.opt("content"));
                    if (!content.isEmpty()) preview.append(content);
                } catch (JSONException ignored) {
                    // The ordinary parser below reports the malformed response.
                }
            }
            return response;
        } catch (SocketTimeoutException exception) {
            RequestControl.check(control);
            throw new IOException("AI 服务在 " + (readTimeout / 1000) + " 秒内没有返回结果"
                    + (!hasImages ? "，请稍后重试"
                            : "（图片请求较慢，可重试或先改用文字描述）"), exception);
        } catch (IOException exception) {
            RequestControl.check(control);
            throw exception;
        } finally {
            if (control != null) control.unregister(connection);
            connection.disconnect();
        }
    }

    static ParseResult parseStream(long taskId, InputStream stream, PreviewSink preview,
            long nowMillis, String timeZoneId)
            throws IOException {
        return parseStream(taskId, stream, preview, nowMillis, timeZoneId, null);
    }

    private static ParseResult parseStream(long taskId, InputStream stream, PreviewSink preview,
            long nowMillis, String timeZoneId, RequestControl control) throws IOException {
        return parseResponse(taskId, streamEnvelope(stream, preview, control), nowMillis, timeZoneId);
    }

    private static String streamEnvelope(InputStream stream, PreviewSink preview, RequestControl control) throws IOException {
        StringBuilder completion = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        JSONObject usage = null;
        String finishReason = null;
        boolean done = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder event = new StringBuilder();
            while (true) {
                RequestControl.check(control);
                line = reader.readLine();
                // EOF terminates the final SSE frame even when the server omits its blank line.
                if (line == null) {
                    if (event.length() == 0) break;
                    line = "";
                }
                RequestControl.check(control);
                if (line.isEmpty()) {
                    if (event.length() == 0) continue;
                    String data = event.toString();
                    event.setLength(0);
                    if ("[DONE]".equals(data)) { done = true; break; }
                    try {
                        JSONObject frame = new JSONObject(data);
                        JSONObject frameUsage = frame.optJSONObject("usage");
                        if (frameUsage != null) usage = frameUsage;
                        JSONArray choices = frame.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) continue;
                        JSONObject choice = choices.getJSONObject(0);
                        if (!choice.isNull("finish_reason"))
                            finishReason = choice.optString("finish_reason", finishReason);
                        JSONObject delta = choice.optJSONObject("delta");
                        String reasoningChunk = delta == null ? "" : reasoningFrom(delta);
                        if (!reasoningChunk.isEmpty()) {
                            if (completion.length() + reasoning.length() + reasoningChunk.length()
                                    > MAX_RESPONSE_CHARS)
                                throw new ResponseException("AI response exceeds size limit");
                            reasoning.append(reasoningChunk);
                            if (preview != null) preview.appendReasoning(reasoningChunk);
                        }
                        String chunk = delta == null ? ""
                                : StreamChunkContent.fromJsonValue(delta.opt("content"));
                        if (!chunk.isEmpty()) {
                            if (completion.length() + reasoning.length() + chunk.length()
                                    > MAX_RESPONSE_CHARS)
                                throw new ResponseException("AI response exceeds size limit");
                            completion.append(chunk);
                            if (preview != null) preview.append(chunk);
                        }
                    } catch (JSONException exception) {
                        throw new ResponseException("Invalid AI stream frame", exception);
                    }
                } else if (line.startsWith("data:")) {
                    if (event.length() > 0) event.append('\n');
                    event.append(line.substring(5).trim());
                    if (event.length() > MAX_RESPONSE_CHARS)
                        throw new ResponseException("AI stream frame exceeds size limit");
                }
            }
            if ("[DONE]".equals(event.toString())) done = true;
        }
        // Some OpenAI-compatible servers close the stream without the [DONE] sentinel. A reported
        // finish reason still proves the answer is complete; a connection cut mid-answer does not.
        if (!done && finishReason == null)
            throw new ResponseException("AI stream ended before completion");
        if (completion.length() == 0) throw new ResponseException("AI stream returned no content");
        JSONObject envelope = new JSONObject();
        try {
            envelope.put("choices", new JSONArray().put(new JSONObject()
                    .put("finish_reason", finishReason)
                    .put("message", new JSONObject().put("content", completion.toString()))));
            if (usage != null) envelope.put("usage", usage);
        } catch (JSONException exception) {
            throw new IOException("Could not assemble AI response", exception);
        }
        return envelope.toString();
    }

    private static String reasoningFrom(JSONObject message) {
        String reasoning = StreamChunkContent.fromJsonValue(message.opt("reasoning_content"));
        return reasoning.isEmpty()
                ? StreamChunkContent.fromJsonValue(message.opt("reasoning")) : reasoning;
    }

    static ParseResult parseResponse(long taskId, String response) throws IOException {
        return parseResponse(taskId, response, System.currentTimeMillis(), java.util.TimeZone.getDefault().getID());
    }

    static ParseResult parseResponse(long taskId, String response, long nowMillis, String timeZoneId) throws IOException {
        return parseResponse(taskId, response, nowMillis, timeZoneId, nowMillis,
                SemanticEventNormalizer.DEFAULT_WORK_START, SemanticEventNormalizer.DEFAULT_WORK_END);
    }

    static ParseResult parseResponse(long taskId, String response, long nowMillis, String timeZoneId,
            long schedulingNowMillis, int workStartMinutes, int workEndMinutes) throws IOException {
        try {
            JSONObject root = new JSONObject(response);
            JSONArray choices = root.getJSONArray("choices");
            if (choices.length() == 0) {
                throw new JSONException("No completion choice");
            }
            JSONObject choice = choices.getJSONObject(0);
            if ("length".equals(choice.optString("finish_reason"))) {
                throw new JSONException("Completion was truncated");
            }
            String content = choice.getJSONObject("message").getString("content");
            JSONObject parsed = contentObject(content);
            JSONArray events = parsed.getJSONArray("events");
            TimeAssessment.validate(events);
            List<EventCandidate> candidates = SemanticEventNormalizer.fromJsonBatch(taskId, events,
                    nowMillis, timeZoneId, schedulingNowMillis, workStartMinutes, workEndMinutes);
            JSONObject usage = root.optJSONObject("usage");
            JSONObject promptDetails = usage == null ? null
                    : usage.optJSONObject("prompt_tokens_details");
            Integer cachedTokens = promptDetails == null || !promptDetails.has("cached_tokens") ? null
                    : optionalInteger(promptDetails, "cached_tokens");
            if (cachedTokens == null && usage != null
                    && usage.has("prompt_cache_hit_tokens")) {
                cachedTokens = optionalInteger(usage, "prompt_cache_hit_tokens");
            }
            return new ParseResult(candidates,
                    usage == null ? null : optionalInteger(usage, "prompt_tokens"),
                    usage == null ? null : optionalInteger(usage, "completion_tokens"),
                    usage == null ? null : optionalInteger(usage, "total_tokens"),
                    cachedTokens, new JSONObject().put("prompt_version", SchedulePrompt.VERSION)
                            .put("reference_epoch_millis", nowMillis).put("scheduling_epoch_millis", schedulingNowMillis)
                            .put("time_zone_id", timeZoneId).put("work_start_minutes", workStartMinutes)
                            .put("work_end_minutes", workEndMinutes).put("usage", usage == null ? JSONObject.NULL : usage)
                            .put("entries", TimeAssessment.audit(events, candidates, nowMillis, timeZoneId)).toString());
        } catch (JSONException e) {
            throw new ResponseException("Invalid AI response JSON: " + e.getMessage(), e);
        }
    }

    /** Strip recognized outer presentation wrappers without editing JSON string contents. */
    private static JSONObject contentObject(String content) throws JSONException {
        String json = content.trim();
        while (true) {
            if (json.startsWith("<escape>")) {
                json = json.substring("<escape>".length()).trim();
                // Some compatible services emit only the opening marker.
                if (json.endsWith("</escape>"))
                    json = json.substring(0, json.length() - "</escape>".length()).trim();
            } else if (json.startsWith("```")) {
                int lineEnd = json.indexOf('\n');
                String opening = lineEnd < 0 ? "" : json.substring(0, lineEnd).trim();
                if ((!opening.equals("```") && !opening.equalsIgnoreCase("```json"))
                        || !json.endsWith("```"))
                    throw new JSONException("Invalid JSON code fence");
                json = json.substring(lineEnd + 1, json.length() - 3).trim();
            } else break;
        }
        JSONTokener tokener = new JSONTokener(json);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0)
            throw new JSONException("Expected one complete JSON object");
        return (JSONObject) value;
    }

    private static Long optionalLong(JSONObject object, String key) throws JSONException {
        if (object.isNull(key)) {
            return null;
        }
        Object value = object.get(key);
        if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
            throw new JSONException(key + " must be an integer or null");
        }
        return ((Number) value).longValue();
    }

    private static Integer optionalInteger(JSONObject object, String key) throws JSONException {
        Long value = optionalLong(object, key);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new JSONException(key + " is outside the integer range");
        }
        return value.intValue();
    }

    private static String optionalString(JSONObject object, String key) throws JSONException {
        if (object.isNull(key)) {
            return null;
        }
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new JSONException(key + " must be a string or null");
        }
        return (String) value;
    }

    private static String errorMessage(String response) {
        try {
            JSONObject error = new JSONObject(response).optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (JSONException ignored) {
            // Non-JSON HTTP errors are still reported with their status code.
        }
        return response.isEmpty() ? "Empty error response" : response.substring(0,
                Math.min(response.length(), 500));
    }

    private static String readLimited(InputStream stream, RequestControl control) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                RequestControl.check(control);
                if (text.length() + count > MAX_RESPONSE_CHARS) {
                    throw new ResponseException("AI response exceeds size limit");
                }
                text.append(buffer, 0, count);
            }
            return text.toString();
        }
    }

    /** A completed model response that cannot become safe candidates; never retry automatically. */
    public static final class ResponseException extends IOException {
        public ResponseException(String message) { super(message); }
        public ResponseException(String message, Throwable cause) { super(message, cause); }
    }

    /** A non-2xx Chat Completions response, with enough detail to classify the failure. */
    public static final class RequestException extends IOException {
        private static final Pattern IMAGE_HINT = Pattern.compile(
                "image|vision|multimodal|modalit|base64", Pattern.CASE_INSENSITIVE);

        public final int statusCode;

        RequestException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        /**
         * Heuristic: the endpoint rejected an image-bearing request because of the image.
         * 415 is treated that way; 400/422 require an explicit image hint, so an
         * unrelated bad request never disables the image entry.
         */
        public boolean rejectsImage() {
            if (statusCode == 415) return true;
            return (statusCode == 400 || statusCode == 422)
                    && !String.valueOf(getMessage()).matches("(?is).*(response_format|json_schema|reasoning_effort|max_tokens|max_completion_tokens|thinking|stream_options).*" )
                    && IMAGE_HINT.matcher(String.valueOf(getMessage())).find();
        }
    }
}
