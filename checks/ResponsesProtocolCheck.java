package com.donglan.chrona.ai;

import com.donglan.chrona.net.RequestControl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Real protocol mapper/parser/client, deterministic native Responses fixtures, no external I/O. */
public final class ResponsesProtocolCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        mapping(); normalization(); streaming(); failureEvidence(); clientTransport();
        System.out.println("ResponsesProtocolCheck passed: " + checks + " checks (synthetic protocol fixtures)");
    }
    private static void mapping() throws Exception {
        check(new AiSettings("https://offline.invalid/v1", "model", "key").protocol.equals(AiSettings.RESPONSES), "three argument defaults Responses");
        check(new AiSettings("https://offline.invalid/v1", "model", "key", "high").protocol.equals(AiSettings.RESPONSES), "four argument defaults Responses");
        JSONObject schema = new JSONObject().put("type", "object").put("properties", new JSONObject().put("events", new JSONObject().put("type", "array")));
        JSONObject chat = new JSONObject().put("model", "gpt-5.1").put("stream", true)
                .put("stream_options", new JSONObject().put("include_usage", true)).put("max_completion_tokens", 32768)
                .put("reasoning_effort", "high").put("prompt_cache_key", "stable-key")
                .put("response_format", new JSONObject().put("type", "json_schema").put("json_schema",
                        new JSONObject().put("name", "schedule").put("strict", true).put("schema", schema)))
                .put("messages", new JSONArray().put(new JSONObject().put("role", "system").put("content", "trusted"))
                        .put(new JSONObject().put("role", "user").put("content", new JSONArray()
                                .put(new JSONObject().put("type", "text").put("text", "original user text"))
                                .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64,AQI=").put("detail", "high"))))));
        String original = chat.toString(); JSONObject nativeRequest = ResponsesProtocol.request(chat);
        check(chat.toString().equals(original), "mapping never mutates frozen canonical body");
        check(nativeRequest.getString("model").equals("gpt-5.1") && nativeRequest.getInt("max_output_tokens") == 32768, "native model and output budget");
        check(nativeRequest.getJSONObject("reasoning").getString("effort").equals("high"), "effort maps into native reasoning");
        JSONObject format = nativeRequest.getJSONObject("text").getJSONObject("format");
        check(format.getString("type").equals("json_schema") && format.getString("name").equals("schedule")
                && format.getBoolean("strict") && format.getJSONObject("schema").similar(schema), "schema flattens into text.format without losing strict/name");
        JSONArray input = nativeRequest.getJSONArray("input");
        check(input.length() == 2 && input.getJSONObject(0).toString().contains("trusted"), "system and user input preserved");
        JSONArray content = input.getJSONObject(1).getJSONArray("content");
        check(content.getJSONObject(0).getString("type").equals("input_text") && content.getJSONObject(0).getString("text").equals("original user text"), "native input text");
        check(content.getJSONObject(1).getString("type").equals("input_image")
                && content.getJSONObject(1).getString("image_url").equals("data:image/jpeg;base64,AQI="), "native image URL retains exact bytes");
        check(nativeRequest.getString("prompt_cache_key").equals("stable-key") && nativeRequest.getBoolean("stream"), "cache identity/stream choice retained");
        check(!nativeRequest.getBoolean("store") && nativeRequest.getString("truncation").equals("disabled"), "native requests prohibit server-managed history and automatic truncation");
        JSONObject replay=ResponsesProtocol.request(nativeRequest);
        check(replay!=nativeRequest&&replay.toString().equals(nativeRequest.toString()),"already-native frozen body is cloned without rewriting input/options");
        noChat(nativeRequest);
        JSONObject basic = new JSONObject().put("model", "custom").put("max_tokens", 8192).put("stream", false)
                .put("messages", chat.getJSONArray("messages")).put("response_format", new JSONObject().put("type", "json_object"));
        JSONObject mapped = ResponsesProtocol.request(basic);
        check(mapped.getInt("max_output_tokens") == 8192 && mapped.getJSONObject("text").getJSONObject("format").getString("type").equals("json_object"), "legacy budget and JSON object map natively");
        check(!mapped.has("reasoning"), "mapper does not invent unsupported effort"); noChat(mapped);
        ChatCompletionClient client = new ChatCompletionClient(new AiSettings("https://api.openai.com/v1", "gpt-5.1", "key", "high"));
        JSONObject options = client.classroomOptions();
        check(options.getInt("max_output_tokens") == 32768 && !options.has("messages"), "new classroom options are native Responses");
        check(options.optJSONArray("include") != null && options.getJSONArray("include").toString().contains("reasoning.encrypted_content"), "stateless classroom requests retain encrypted reasoning items");
        noChat(options);
    }
    private static JSONObject response(String content) throws Exception {
        return new JSONObject().put("id", "resp_fixture").put("object", "response").put("status", "completed")
                .put("output", new JSONArray()
                        .put(new JSONObject().put("id", "rs_fixture").put("type", "reasoning").put("encrypted_content", "opaque-encrypted-data")
                                .put("summary", new JSONArray().put(new JSONObject().put("type", "summary_text").put("text", "visible reasoning"))))
                        .put(new JSONObject().put("id", "msg_fixture").put("type", "message").put("role", "assistant").put("status", "completed")
                                .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", content).put("annotations", new JSONArray())))))
                .put("usage", new JSONObject().put("input_tokens", 101).put("output_tokens", 17).put("total_tokens", 118)
                        .put("input_tokens_details", new JSONObject().put("cached_tokens", 64)).put("output_tokens_details", new JSONObject().put("reasoning_tokens", 11)));
    }
    private static void normalization() throws Exception {
        JSONObject nativeResponse = response("{\"events\":[]}"); String snapshot = nativeResponse.toString();
        JSONObject envelope = ResponsesProtocol.normalize(nativeResponse);
        JSONObject message = envelope.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        check(message.getString("content").equals("{\"events\":[]}"), "output text maps to shared envelope");
        check(envelope.getJSONObject("response").toString().equals(snapshot) && nativeResponse.toString().equals(snapshot), "complete native response/encrypted reasoning retained unchanged");
        JSONObject usage = envelope.getJSONObject("usage");
        check(usage.getInt("prompt_tokens") == 101 && usage.getInt("completion_tokens") == 17 && usage.getInt("total_tokens") == 118, "native usage maps to shared counters");
        check(usage.getJSONObject("prompt_tokens_details").getInt("cached_tokens") == 64, "native cached tokens mapped");
        check(ChatCompletionClient.parseResponse(1, envelope.toString(), 0, "UTC").cachedTokens == 64, "existing scheduling parser consumes normalized response");
        JSONObject split = response("first");
        split.getJSONArray("output").getJSONObject(1).getJSONArray("content").put(new JSONObject().put("type", "output_text").put("text", " second"));
        check(ResponsesProtocol.normalize(split).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").equals("first second"), "ordered complete content parts are not discarded");
        for (String status : List.of("failed", "incomplete", "in_progress", "queued", "cancelled")) {
            JSONObject rejected = response("partial").put("status", status).put("incomplete_details", new JSONObject().put("reason", "max_output_tokens"));
            rejects(() -> ResponsesProtocol.normalize(rejected), "noncompleted response rejected: " + status);
        }
        JSONObject refused = response("ignored");
        refused.getJSONArray("output").getJSONObject(1).put("content", new JSONArray().put(new JSONObject().put("type", "refusal").put("refusal", "refused fixture")));
        rejects(() -> ResponsesProtocol.normalize(refused), "completed refusal is never usable output");
    }
    private static String event(String type, JSONObject value) throws Exception { return "event: " + type + "\ndata: " + new JSONObject(value.toString()).put("type", type) + "\n\n"; }
    private static ChatCompletionClient.ResponseException responseFailure(Throwing operation, String label) throws Exception {
        try { operation.run(); } catch (ChatCompletionClient.ResponseException expected) { check(true, label); return expected; }
        throw new AssertionError(label);
    }
    private static void retained(JSONObject raw, JSONObject evidence, String finish, String label) throws Exception {
        check(evidence != null && evidence.getJSONObject("response").toString().equals(raw.toString()), label + " retains complete raw response including encrypted reasoning");
        JSONObject usage=evidence.getJSONObject("usage"),nativeUsage=raw.getJSONObject("usage");
        for(String key:List.of("input_tokens","output_tokens","total_tokens","input_tokens_details","output_tokens_details"))
            check(usage.get(key).toString().equals(nativeUsage.get(key).toString()),label+" preserves native usage "+key);
        check(usage.getInt("prompt_tokens")==101&&usage.getInt("completion_tokens")==17
                &&usage.getJSONObject("prompt_tokens_details").getInt("cached_tokens")==64,label+" maps common usage even on failure");
        check(finish==null?evidence.isNull("finish_reason"):finish.equals(evidence.opt("finish_reason")),label+" truthful finish reason");
        check(!evidence.has("choices"),label+" never presents failure as usable completion");
    }
    private static void failureEvidence() throws Exception {
        ChatCompletionClient.ResponseException legacy=new ChatCompletionClient.ResponseException("old constructor");
        check(legacy.evidence==null,"original exception constructor remains usable without invented evidence");
        IOException cause=new IOException("fixture");
        check(new ChatCompletionClient.ResponseException("old cause",cause).getCause()==cause,"original cause constructor remains usable");
        JSONObject supplied=new JSONObject().put("fixture",true);
        check(new ChatCompletionClient.ResponseException("with evidence",cause,supplied).evidence.similar(supplied),"evidence exception constructor exposes metadata");
        for(String status:List.of("incomplete","failed")){
            JSONObject raw=response("PRIVATE-partial").put("status",status)
                    .put("incomplete_details",new JSONObject().put("reason","max_output_tokens"))
                    .put("error",new JSONObject().put("code","server_error").put("message","PRIVATE-provider-message"));
            String snapshot=raw.toString(),finish=status.equals("incomplete")?"length":null;
            JSONObject json=responseFailure(()->ResponsesProtocol.normalize(raw),"native JSON failure surfaces "+status).evidence;
            retained(raw,json,finish,"native JSON "+status);
            check(!json.has("content_chars")&&!json.has("reasoning_chars"),"JSON never invents delta counters "+status);
            for(String type:status.equals("incomplete")?List.of("response.incomplete"):List.of("response.failed","error")){
                String wire=event("response.output_text.delta",new JSONObject().put("delta","半😀"))
                        +event("response.reasoning_summary_text.delta",new JSONObject().put("delta","思考"))
                        +event("response.reasoning_text.delta",new JSONObject().put("delta","!"))
                        +event(type,new JSONObject().put("response",raw).put("error",raw.getJSONObject("error")));
                JSONObject streamed=responseFailure(()->ResponsesProtocol.read(bytes(wire),null,null),"SSE failure surfaces "+type).evidence;
                retained(raw,streamed,finish,"SSE "+type);
                check(streamed.getString("terminal_type").equals(type),"actual SSE terminal retained "+type);
                check(streamed.getInt("content_chars")=="半😀".length()&&streamed.getInt("reasoning_chars")==3,"actual typed delta counts retained, never final output size "+type);
            }
            check(raw.toString().equals(snapshot),"failure extraction never mutates native JSON "+status);
        }
        JSONObject filtered=response("partial").put("status","incomplete").put("incomplete_details",new JSONObject().put("reason","content_filter"));
        retained(filtered,responseFailure(()->ResponsesProtocol.normalize(filtered),"filtered native response rejected").evidence,null,"filtered native failure");
        JSONObject contradictory=response("complete-looking");
        JSONObject bad=responseFailure(()->ResponsesProtocol.read(bytes(event("response.failed",new JSONObject().put("response",contradictory))),null,null),"typed failure still rejects completed-looking body").evidence;
        retained(contradictory,bad,"stop","contradictory terminal");
        check(bad.getString("terminal_type").equals("response.failed"),"typed failure event cannot be relabelled as completed");
        String partial=event("response.output_text.delta",new JSONObject().put("delta","{\"events\":[]}"));
        JSONObject eof=responseFailure(()->ResponsesProtocol.read(bytes(partial),null,null),"EOF never commits valid-looking partial JSON").evidence;
        check(eof==null||(!eof.has("response")&&eof.isNull("terminal_type")&&eof.isNull("finish_reason")),"unknown EOF never invents native terminal or finish");
        JSONObject mismatch=response("different");
        JSONObject invalid=responseFailure(()->ResponsesProtocol.read(bytes(partial+event("response.completed",new JSONObject().put("response",mismatch))),null,null),"contradictory complete event never commits").evidence;
        retained(mismatch,invalid,"stop","invalid completed event");
        check(invalid.getString("terminal_type").equals("response.completed")&&invalid.getInt("content_chars")=="{\"events\":[]}".length(),"invalid completion retains actual terminal and observed delta count");
    }
    private static String completed(String text) throws Exception { return event("response.completed", new JSONObject().put("response", response(text))); }
    private static InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    private static void streaming() throws Exception {
        String text = "{\"events\":[]}";
        String wire = event("response.created", new JSONObject().put("response", new JSONObject().put("status", "in_progress")))
                + event("response.reasoning_summary_text.delta", new JSONObject().put("delta", "visible "))
                + event("response.reasoning_summary_text.delta", new JSONObject().put("delta", "reasoning"))
                + event("response.output_text.delta", new JSONObject().put("output_index", 1).put("content_index", 0).put("delta", "{\"events\":"))
                + event("response.output_text.delta", new JSONObject().put("output_index", 1).put("content_index", 0).put("delta", "[]}"))
                + completed(text);
        StringBuilder preview = new StringBuilder(), reasoning = new StringBuilder();
        ChatCompletionClient.PreviewSink sink = new ChatCompletionClient.PreviewSink() {
            public void append(String delta) { preview.append(delta); }
            public void appendReasoning(String delta) { reasoning.append(delta); }
        };
        JSONObject parsed = new JSONObject(ResponsesProtocol.read(bytes(wire), sink, null));
        check(preview.toString().equals(text) && reasoning.toString().equals("visible reasoning"), "typed text and reasoning previews remain separate and ordered");
        check(parsed.getJSONObject("response").getJSONArray("output").getJSONObject(0).getString("encrypted_content").equals("opaque-encrypted-data"), "final event keeps native encrypted reasoning");
        check(parsed.getJSONObject("usage").getInt("total_tokens") == 118, "completed event is usage authority");
        // A native completed response is sufficient; Chat's [DONE] is never a Responses completion signal.
        check(new JSONObject(ResponsesProtocol.read(bytes(completed(text).stripTrailing()), null, null)).has("choices"), "unterminated terminal SSE frame accepted");
        String delta = event("response.output_text.delta", new JSONObject().put("delta", text));
        rejects(() -> ResponsesProtocol.read(bytes(delta), null, null), "EOF after valid-looking text still fails");
        rejects(() -> ResponsesProtocol.read(bytes(delta + "data: [DONE]\n\n"), null, null), "Chat DONE cannot complete native stream");
        rejects(() -> ResponsesProtocol.read(bytes(event("response.output_text.done", new JSONObject().put("text", text))), null, null), "output_text.done alone cannot commit response");
        rejects(() -> ResponsesProtocol.read(bytes(event("response.output_text.delta",new JSONObject().put("delta","different"))+completed(text)),null,null),"final output cannot silently contradict streamed text");
        for (String type : List.of("response.failed", "response.incomplete", "error")) {
            JSONObject terminal = new JSONObject().put("response", response("partial").put("status", type.endsWith("failed") ? "failed" : "incomplete"))
                    .put("error", new JSONObject().put("code", "server_error").put("message", "synthetic failure"));
            rejects(() -> ResponsesProtocol.read(bytes(delta + event(type, terminal)), null, null), "failure event never commits partial output: " + type);
        }
        for (String type : List.of("response.failed", "response.incomplete"))
            rejects(() -> ResponsesProtocol.read(bytes(event(type, new JSONObject().put("response", response(text)))), null, null),
                    "typed failure cannot be overridden by contradictory completed status: " + type);
        JSONObject refusal = response("ignored");
        refusal.getJSONArray("output").getJSONObject(1).put("content", new JSONArray().put(new JSONObject().put("type", "refusal").put("refusal", "no")));
        rejects(() -> ResponsesProtocol.read(bytes(event("response.refusal.delta", new JSONObject().put("delta", "no"))
                + event("response.completed", new JSONObject().put("response", refusal))), null, null), "streamed refusal fails closed");
        try (RequestControl control = new RequestControl(2000)) {
            control.cancel(); rejects(() -> ResponsesProtocol.read(bytes(wire), null, control), "pre-cancel native stream");
        }
        try (RequestControl control = new RequestControl(2000)) {
            ChatCompletionClient.PreviewSink cancel = chunk -> control.cancel();
            rejects(() -> ResponsesProtocol.read(bytes(wire), cancel, control), "cancel during partial native stream");
        }
        // Byte-sized transport reads split UTF-8, SSE lines and JSON tokens independently.
        InputStream segmented = new FilterInputStream(bytes(completed("中文完整内容"))) {
            public int read(byte[] data, int offset, int count) throws IOException { return super.read(data, offset, Math.min(count, 1)); }
        };
        check(new JSONObject(ResponsesProtocol.read(segmented, null, null)).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").equals("中文完整内容"), "transport segmentation preserves UTF-8 and complete native output");
    }
    private static void clientTransport() throws Exception {
        for (String mode : List.of("success", "json", "http", "failed", "eof", "cancel", "chat-frozen", "responses-frozen")) {
            AtomicInteger opened = new AtomicInteger(), released = new AtomicInteger(), disconnected = new AtomicInteger();
            ByteArrayOutputStream body = new ByteArrayOutputStream(); String[] endpoint = {""};
            try (RequestControl control = new RequestControl(3000)) {
                ChatCompletionClient.ConnectionRoute route = (url, supplied) -> {
                    endpoint[0] = url.getPath(); opened.incrementAndGet();
                    HttpURLConnection connection = new HttpURLConnection(url) {
                        public void connect() { }
                        public void disconnect() { disconnected.incrementAndGet(); }
                        public boolean usingProxy() { return false; }
                        public OutputStream getOutputStream() { check(released.get() == 0, "lease held while writing " + mode); return body; }
                        public int getResponseCode() { return mode.equals("http") ? 422 : 200; }
                        public String getContentType() { return mode.equals("chat-frozen") || mode.equals("json") ? "application/json" : "text/event-stream"; }
                        public InputStream getErrorStream() { return bytes("{\"error\":{\"message\":\"unsupported max_output_tokens\"}}"); }
                        public InputStream getInputStream() throws IOException {
                            try {
                                if (mode.equals("cancel")) control.cancel();
                                String wire = mode.equals("chat-frozen") ? "{\"choices\":[{\"message\":{\"content\":\"done\"},\"finish_reason\":\"stop\"}]}"
                                        : mode.equals("json") ? response("{\"events\":[]}").toString()
                                        : mode.equals("failed") ? event("response.failed", new JSONObject().put("response", response("partial").put("status", "failed")))
                                        : mode.equals("eof") ? event("response.output_text.delta", new JSONObject().put("delta", "partial"))
                                        : completed("{\"events\":[]}");
                                return bytes(wire);
                            } catch (JSONException e) { throw new IOException(e); } catch (Exception e) { throw new IOException(e); }
                        }
                    };
                    return new ChatCompletionClient.OpenedConnection(connection, () -> {
                        check(disconnected.get() > 0, "disconnect before native lease release " + mode); released.incrementAndGet();
                    });
                };
                String selected = mode.equals("responses-frozen") ? AiSettings.CHAT_COMPLETIONS : AiSettings.RESPONSES;
                ChatCompletionClient client = new ChatCompletionClient(new AiSettings("https://offline.invalid/v1", "gpt-5.1", "key", "high", selected), control, SchedulePrompt.SYSTEM, route);
                boolean frozen = mode.endsWith("frozen");
                JSONObject request = new JSONObject().put("model", "gpt-5.1").put("stream", true)
                        .put(mode.equals("chat-frozen") ? "messages" : "input", new JSONArray().put(new JSONObject().put("role", "user").put("content", "frozen")));
                String snapshot = request.toString();
                Throwing exchange = () -> {
                    if (frozen) client.completeConversation(request);
                    else if(mode.equals("json"))client.parse(1, "raw", null, null, 0, "UTC");
                    else client.parse(1, "raw", null, null, 0, "UTC", chunk -> { });
                };
                if (List.of("http", "failed", "eof", "cancel").contains(mode)) rejects(exchange, "native client failure surfaces " + mode);
                else exchange.run();
                check(opened.get() == 1 && released.get() == 1, "native exchange releases exactly once and never retries/falls back " + mode);
                String path = mode.equals("chat-frozen") ? "/v1/chat/completions" : "/v1/responses";
                check(endpoint[0].equals(path), "selected/frozen protocol determines endpoint " + mode);
                JSONObject sent = new JSONObject(body.toString(StandardCharsets.UTF_8));
                if (frozen) check(body.toString(StandardCharsets.UTF_8).equals(snapshot) && request.toString().equals(snapshot), "frozen protocol request is sent unchanged " + mode);
                else { noChat(sent); check(sent.has("input") && sent.has("max_output_tokens"), "ordinary default request uses native Responses " + mode); }
            }
        }
    }
    private static void noChat(JSONObject body) {
        for (String key : List.of("messages", "max_tokens", "max_completion_tokens", "reasoning_effort", "response_format", "stream_options", "thinking"))
            check(!body.has(key), "native body excludes chat-only field " + key);
    }
    private interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing operation, String label) throws Exception {
        try { operation.run(); } catch (IOException | JSONException expected) { check(true, label); return; }
        throw new AssertionError(label);
    }
    private static void check(boolean result, String label) { checks++; if (!result) throw new AssertionError(label); }
}
