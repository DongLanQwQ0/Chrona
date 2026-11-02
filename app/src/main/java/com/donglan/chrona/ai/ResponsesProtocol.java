package com.donglan.chrona.ai;

import com.donglan.chrona.net.RequestControl;
import com.donglan.chrona.ai.ChatCompletionClient.ResponseException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Native Responses wire format. No server-managed conversation, compaction or protocol fallback. */
public final class ResponsesProtocol {
    private static final int LIMIT = 2_000_000;
    private ResponsesProtocol() { }

    public static JSONObject request(JSONObject chat) throws JSONException {
        if (chat.has("input")) return new JSONObject(chat.toString());
        JSONObject result = new JSONObject(chat.toString());
        JSONArray input = new JSONArray(), messages = chat.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            String role = message.getString("role");
            Object content = message.get("content");
            if (content instanceof String) {
                input.put(new JSONObject().put("role", role).put("content", content));
                continue;
            }
            JSONArray parts = (JSONArray) content, converted = new JSONArray();
            for (int p = 0; p < parts.length(); p++) {
                JSONObject part = parts.getJSONObject(p);
                String type = part.getString("type");
                if ("text".equals(type)) converted.put(new JSONObject()
                        .put("type", "assistant".equals(role) ? "output_text" : "input_text")
                        .put("text", part.getString("text")));
                else if ("image_url".equals(type)) {
                    JSONObject image = part.getJSONObject("image_url");
                    JSONObject convertedImage = new JSONObject().put("type", "input_image")
                            .put("image_url", image.getString("url"));
                    if (image.has("detail")) convertedImage.put("detail", image.get("detail"));
                    converted.put(convertedImage);
                } else throw new JSONException("Unsupported Responses input content");
            }
            input.put(new JSONObject().put("role", role).put("content", converted));
        }
        result.remove("messages"); result.remove("stream_options");
        Object budget = chat.has("max_completion_tokens") ? chat.get("max_completion_tokens") : chat.opt("max_tokens");
        result.remove("max_completion_tokens"); result.remove("max_tokens");
        if (budget != null) result.put("max_output_tokens", budget);
        Object effort = chat.opt("reasoning_effort");
        JSONObject thinking = chat.optJSONObject("thinking");
        if (effort == null && thinking != null && "disabled".equals(thinking.optString("type"))) effort = "none";
        result.remove("reasoning_effort"); result.remove("thinking");
        if (effort != null) result.put("reasoning", new JSONObject().put("effort", effort));
        JSONObject format = chat.optJSONObject("response_format"); result.remove("response_format");
        if (format != null) {
            JSONObject nativeFormat;
            if ("json_schema".equals(format.optString("type"))) {
                nativeFormat = new JSONObject(format.getJSONObject("json_schema").toString()).put("type", "json_schema");
            } else nativeFormat = new JSONObject(format.toString());
            result.put("text", new JSONObject().put("format", nativeFormat));
        }
        return result.put("input", input).put("store", false).put("truncation", "disabled")
                .put("include", new JSONArray().put("reasoning.encrypted_content"));
    }

    /** Preserve the complete service response beside the common text/usage projection. */
    public static JSONObject normalize(JSONObject response) throws IOException {
        JSONObject evidence;
        try { evidence=failureEvidence(response); }
        catch(JSONException invalid){throw new ResponseException("Invalid Responses JSON",invalid);}
        try {
            if (response.has("error") && !response.isNull("error")) throw failure(response.optJSONObject("error"));
            String status = response.optString("status");
            if ("incomplete".equals(status)) {
                JSONObject details = response.optJSONObject("incomplete_details");
                if (details != null && "max_output_tokens".equals(details.optString("reason")))
                    throw new ResponseException("Responses输出达到上限，当前板块未提交，请从头重新生成本板块");
                throw new ResponseException("Responses未完整完成，当前板块未提交");
            }
            if (!"completed".equals(status)) throw new ResponseException("Responses未成功完成，当前板块未提交");
            JSONArray output = response.getJSONArray("output");
            StringBuilder text = new StringBuilder(), reasoning = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.getJSONObject(i);
                String type = item.getString("type");
                if ("message".equals(type)) {
                    if (!"assistant".equals(item.optString("role"))) throw new ResponseException("Responses输出角色无效");
                    if (item.has("status") && !"completed".equals(item.optString("status")))
                        throw new ResponseException("Responses消息未完整完成");
                    JSONArray parts = item.getJSONArray("content");
                    for (int p = 0; p < parts.length(); p++) {
                        JSONObject part = parts.getJSONObject(p);
                        if ("refusal".equals(part.optString("type"))) throw new ResponseException("AI服务未允许返回本板块内容");
                        if (!"output_text".equals(part.optString("type"))) throw new ResponseException("Responses正文类型不受支持");
                        append(text, part.getString("text"));
                    }
                } else if ("reasoning".equals(type)) {
                    JSONArray summary = item.optJSONArray("summary");
                    if (summary != null) for (int p = 0; p < summary.length(); p++) {
                        JSONObject part = summary.getJSONObject(p);
                        if ("summary_text".equals(part.optString("type"))) append(reasoning, part.getString("text"));
                    }
                } else throw new ResponseException("Responses包含尚未处理的输出类型");
            }
            if (text.toString().isBlank()) throw new ResponseException("Responses未返回正文，当前板块未提交");
            JSONObject message = new JSONObject().put("role", "assistant").put("content", text.toString());
            if (reasoning.length() > 0) message.put("reasoning_content", reasoning.toString());
            JSONObject envelope = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                    .put("finish_reason", "stop").put("message", message)))
                    .put("response", new JSONObject(response.toString()));
            JSONObject usage=evidence.optJSONObject("usage");
            if(usage!=null)envelope.put("usage",usage);
            return envelope;
        } catch(ResponseException failure){throw new ResponseException(failure.getMessage(),failure,evidence);}
        catch (JSONException invalid) { throw new ResponseException("Invalid Responses JSON", invalid,evidence); }
    }

    /** Failure responses remain auditable without becoming accepted assistant/history items. */
    private static JSONObject failureEvidence(JSONObject response)throws JSONException{
        JSONObject evidence=new JSONObject();
        if(response==null)return evidence;
        evidence.put("response",new JSONObject(response.toString()));
        JSONObject usage=response.optJSONObject("usage");
        if(usage!=null){
            JSONObject mapped=new JSONObject(usage.toString());
            if(usage.has("input_tokens"))mapped.put("prompt_tokens",usage.get("input_tokens"));
            if(usage.has("output_tokens"))mapped.put("completion_tokens",usage.get("output_tokens"));
            if(usage.has("input_tokens_details"))mapped.put("prompt_tokens_details",usage.get("input_tokens_details"));
            evidence.put("usage",mapped);
        }
        JSONObject incomplete=response.optJSONObject("incomplete_details");
        Object finish="completed".equals(response.optString("status"))?"stop":
                "incomplete".equals(response.optString("status"))&&incomplete!=null&&"max_output_tokens".equals(incomplete.optString("reason"))?"length":JSONObject.NULL;
        return evidence.put("finish_reason",finish);
    }
    private static ResponseException streamFailure(ResponseException failure,JSONObject terminal,JSONObject event,String type,int contentChars,int reasoningChars){
        try{
            JSONObject evidence=failure.evidence==null?failureEvidence(terminal):new JSONObject(failure.evidence.toString());
            if(event!=null)evidence.put("event",new JSONObject(event.toString()));
            if(type!=null)evidence.put("terminal_type",type);
            evidence.put("content_chars",contentChars).put("reasoning_chars",reasoningChars);
            return new ResponseException(failure.getMessage(),failure,evidence);
        }catch(JSONException invalid){return new ResponseException("Invalid Responses failure evidence",invalid);}
    }

    public static String read(InputStream stream, ChatCompletionClient.PreviewSink preview, RequestControl control) throws IOException {
        StringBuilder data = new StringBuilder(), text = new StringBuilder(), reasoning = new StringBuilder();
        JSONObject terminal=null,errorEvent=null;String terminalType=null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                RequestControl.check(control);
                line = reader.readLine();
                if (line == null && data.length() == 0) break;
                RequestControl.check(control);
                if (line == null || line.isEmpty()) {
                    if (data.length() == 0) continue;
                    String frame = data.toString(); data.setLength(0);
                    if ("[DONE]".equals(frame)) break; // Not a Responses completion event.
                    JSONObject event;
                    try { event = new JSONObject(frame); }
                    catch (JSONException invalid) { throw new ResponseException("Invalid Responses stream frame", invalid); }
                    String type = event.optString("type");
                    if ("error".equals(type) || "response.failed".equals(type)) {
                        JSONObject failed = event.optJSONObject("response");
                        terminal=failed;terminalType=type;if(failed==null)errorEvent=event;
                        throw failure(failed == null ? event : failed.optJSONObject("error"));
                    }
                    if ("response.incomplete".equals(type) || "response.completed".equals(type)) {
                        JSONObject response = event.optJSONObject("response");
                        terminal=response;terminalType=type;
                        if (response == null) throw new ResponseException("Responses完成事件缺少结果");
                        JSONObject envelope = normalize(response);
                        if (!"response.completed".equals(type)) throw new ResponseException("Responses未完整完成，当前板块未提交");
                        String full = envelope.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        if (!full.startsWith(text.toString())) throw new ResponseException("Responses流式正文与最终结果不一致");
                        if (preview != null && full.length() > text.length()) preview.append(full.substring(text.length()));
                        if (preview != null && reasoning.length() == 0) {
                            String thought = envelope.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("reasoning_content");
                            if (!thought.isEmpty()) preview.appendReasoning(thought);
                        }
                        return envelope.toString();
                    }
                    if ("response.output_text.delta".equals(type)) {
                        String delta = event.getString("delta"); append(text, delta);
                        if (preview != null) preview.append(delta);
                    } else if ("response.reasoning_summary_text.delta".equals(type) || "response.reasoning_text.delta".equals(type)) {
                        String delta = event.getString("delta"); append(reasoning, delta);
                        if (preview != null) preview.appendReasoning(delta);
                    }
                    if (line == null) break;
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    append(data, line.substring(5).trim());
                }
            }
        }catch(ResponseException failure){throw streamFailure(failure,terminal,errorEvent,terminalType,text.length(),reasoning.length());}
        catch (JSONException invalid) { throw streamFailure(new ResponseException("Invalid Responses stream content", invalid),terminal,errorEvent,terminalType,text.length(),reasoning.length()); }
        throw streamFailure(new ResponseException("Responses stream ended before completion (content_chars=" + text.length()
                + ", reasoning_chars=" + reasoning.length() + ")"),terminal,errorEvent,terminalType,text.length(),reasoning.length());
    }

    private static void append(StringBuilder target, String value) throws ResponseException {
        if (target.length() + value.length() > LIMIT) throw new ResponseException("Responses超过响应大小上限");
        target.append(value);
    }
    private static ResponseException failure(JSONObject error) {
        return new ResponseException("Responses provider error (code=" + ChatCompletionClient.safeStreamErrorLabel(error, "code")
                + ", type=" + ChatCompletionClient.safeStreamErrorLabel(error, "type") + ")");
    }
}
