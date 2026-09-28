package com.donglan.chrona.ai;

import org.json.JSONObject;
import org.json.JSONException;
import java.net.URI;
import java.util.Locale;

/** Conservative options for official endpoints; unknown services keep their chosen model. */
public final class RequestOptions {
    private RequestOptions() { }
    public static void apply(JSONObject body, AiSettings settings) throws JSONException {
        String host = URI.create(settings.baseUrl).getHost().toLowerCase(Locale.ROOT);
        String model = settings.model.toLowerCase(Locale.ROOT);
        String effort = settings.reasoningEffort;
        boolean deep = host.equals("api.deepseek.com")
                && (model.equals("deepseek-flash") || model.equals("deepseek-v4-pro"));
        boolean open = host.equals("api.openai.com") && model.matches(
                "(?:gpt-4o(?:-mini)?|gpt-4\\.1(?:-mini|-nano)?|gpt-5(?:\\.[124])?(?:-mini|-nano|-pro)?|o3(?:-mini)?|o4-mini)(?:-\\d{4}-\\d{2}-\\d{2})?");
        boolean reasoning = deep || (open && (model.startsWith("gpt-5") || model.startsWith("o3") || model.startsWith("o4")));
        String resolved = effort.equals("auto") ? "low" : effort;
        boolean supportsNone = open && model.matches("gpt-5\\.[124](?:-mini|-nano)?(?:-\\d{4}-\\d{2}-\\d{2})?");
        // Older reasoning models cannot disable thinking; keep a reasoning-sized budget.
        if (reasoning && !deep && resolved.equals("none") && !supportsNone) resolved = "low";
        int budget = reasoning && !resolved.equals("none")
                ? (resolved.equals("high") || resolved.equals("max") ? 32768 : 16384) : 8192;
        body.put(open ? "max_completion_tokens" : "max_tokens", budget);
        if (deep) {
            body.put("response_format", new JSONObject().put("type", "json_object"));
            body.put("thinking", new JSONObject().put("type", resolved.equals("none") ? "disabled" : "enabled"));
            if (!resolved.equals("none")) body.put("reasoning_effort",
                    resolved.equals("medium") ? "high" : resolved);
        } else if (open) {
            body.put("response_format", new JSONObject().put("type", "json_schema").put("json_schema",
                    new JSONObject().put("name", "chrona_schedule_v2").put("strict", true).put("schema", SchedulePrompt.schema())));
            body.put("prompt_cache_key", "chrona-" + SchedulePrompt.VERSION);
            // Avoid sending 'none' to reasoning families that may not accept it.
            if (reasoning) body.put("reasoning_effort", resolved.equals("max") ? "high" : resolved);
        }
    }

    /** Remove only an explicitly named rejected option; at most one retry is made by the caller. */
    public static boolean downgrade(JSONObject body, int status, String detail) {
        if (status != 400 && status != 422) return false;
        String text = detail.toLowerCase(Locale.ROOT);
        if (!(text.contains("unsupported") || text.contains("not supported") || text.contains("does not support") || text.contains("unknown")
                || text.contains("unrecognized") || text.contains("not allowed") || text.contains("invalid parameter"))) return false;
        boolean changed = false;
        for (String key : new String[]{"response_format", "json_schema", "thinking", "reasoning_effort",
                "max_completion_tokens", "max_tokens", "prompt_cache_key", "stream_options", "stream"}) {
            if (!java.util.regex.Pattern.compile("(?<![a-z0-9_])" + key + "(?![a-z0-9_])")
                    .matcher(text).find()) continue;
            if (key.equals("json_schema")) { changed |= body.has("response_format"); body.remove("response_format"); }
            else if (key.equals("stream")) {
                if (body.optBoolean("stream")) { changed = true; try { body.put("stream", false); } catch (JSONException e) { throw new IllegalStateException(e); } }
                body.remove("stream_options");
            } else { changed |= body.has(key); body.remove(key); }
        }
        return changed;
    }
}
