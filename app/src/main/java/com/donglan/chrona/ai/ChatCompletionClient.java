package com.donglan.chrona.ai;

import com.donglan.chrona.data.EventCandidate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/** Synchronous network client. Call parse from a worker thread, never the UI thread. */
public final class ChatCompletionClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    /** A text-only reply comes back quickly, so waiting longer only delays a real failure. */
    private static final int TEXT_READ_TIMEOUT_MILLIS = 30_000;
    /** An image request uploads far more and the provider needs longer to look at it. */
    private static final int IMAGE_READ_TIMEOUT_MILLIS = 180_000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    // Keep this prefix identical across requests so providers can cache it.
    private static final String SYSTEM_PROMPT = "Extract all independent calendar events and "
            + "reminders from the user input. Return only one JSON object with an events array. "
            + "Each event has title (nonempty string), start_at_millis (Unix milliseconds or null), "
            + "end_at_millis (Unix milliseconds or null), time_zone_id (IANA timezone or null), "
            + "location (string or null), description (string or null), "
            + "reminder_minutes_before (nonnegative integer or null), and needs_confirmation "
            + "(boolean). Preserve separate events as separate array entries. Use null and set "
            + "needs_confirmation to true when timing or another essential detail is uncertain. "
            + "Do not invent dates or facts. Return {\"events\":[]} if there are none.";

    private final AiSettings settings;

    public ChatCompletionClient(AiSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.settings = settings;
    }

    /** The read timeout this client applies; exposed so diagnostics can report the real value. */
    public static int readTimeoutMillis(boolean withImage) {
        return withImage ? IMAGE_READ_TIMEOUT_MILLIS : TEXT_READ_TIMEOUT_MILLIS;
    }

    /** Parses one submitted input and returns all proposed entries plus reported token usage. */
    public ParseResult parse(long taskId, String rawText, byte[] imageJpeg, String linkText,
            long nowMillis, String timeZoneId) throws IOException {
        boolean hasText = rawText != null && !rawText.trim().isEmpty();
        if ((!hasText && imageJpeg == null) || timeZoneId == null || timeZoneId.trim().isEmpty()) {
            throw new IllegalArgumentException("Text or image, and a timezone, are required");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("model", settings.model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
            StringBuilder prompt = new StringBuilder();
            prompt.append("Current Unix time in milliseconds: ").append(nowMillis)
                    .append("\nTimezone: ").append(timeZoneId)
                    .append("\nInput:\n").append(hasText ? rawText : "(见随附图片)");
            if (linkText != null && !linkText.trim().isEmpty()) {
                // Fetched on the device only because the input carried a link; may be partial.
                prompt.append("\n\nText fetched from links in the input (may be incomplete):\n")
                        .append(linkText.trim());
            }
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", prompt.toString()));
            if (imageJpeg != null) {
                content.put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", "data:image/jpeg;base64,"
                                + Base64.getEncoder().encodeToString(imageJpeg))));
            }
            messages.put(new JSONObject().put("role", "user").put("content", content));
            body.put("messages", messages);
            body.put("stream", false);
        } catch (JSONException e) {
            throw new IOException("Could not encode AI request", e);
        }

        int readTimeout = readTimeoutMillis(imageJpeg != null);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                settings.baseUrl + "/chat/completions").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(readTimeout);
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + settings.apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : readLimited(stream);
            if (status < 200 || status >= 300) {
                throw new RequestException(status, "AI request failed (HTTP " + status + "): "
                        + errorMessage(response));
            }
            return parseResponse(taskId, response);
        } catch (SocketTimeoutException exception) {
            throw new IOException("AI 服务在 " + (readTimeout / 1000) + " 秒内没有返回结果"
                    + (imageJpeg == null ? "，请稍后重试"
                            : "（图片请求较慢，可重试或先改用文字描述）"), exception);
        } finally {
            connection.disconnect();
        }
    }

    static ParseResult parseResponse(long taskId, String response) throws IOException {
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
            JSONObject parsed = new JSONObject(content);
            JSONArray events = parsed.getJSONArray("events");
            List<EventCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                Object rawTitle = event.get("title");
                if (!(rawTitle instanceof String)) {
                    throw new JSONException("Event title must be a string");
                }
                String title = (String) rawTitle;
                if (title.trim().isEmpty()) {
                    throw new JSONException("Event title is empty");
                }
                Object confirmation = event.get("needs_confirmation");
                if (!(confirmation instanceof Boolean)) {
                    throw new JSONException("needs_confirmation must be a boolean");
                }
                Integer reminder = optionalInteger(event, "reminder_minutes_before");
                if (reminder != null && reminder < 0) {
                    throw new JSONException("Reminder minutes must be nonnegative");
                }
                candidates.add(new EventCandidate(0, taskId, title,
                        optionalLong(event, "start_at_millis"),
                        optionalLong(event, "end_at_millis"),
                        optionalString(event, "time_zone_id"),
                        optionalString(event, "location"),
                        optionalString(event, "description"), reminder,
                        (Boolean) confirmation));
            }
            JSONObject usage = root.optJSONObject("usage");
            return new ParseResult(candidates,
                    usage == null ? null : optionalInteger(usage, "prompt_tokens"),
                    usage == null ? null : optionalInteger(usage, "completion_tokens"),
                    usage == null ? null : optionalInteger(usage, "total_tokens"));
        } catch (JSONException e) {
            throw new IOException("Invalid AI response JSON: " + e.getMessage(), e);
        }
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

    private static String readLimited(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (text.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("AI response exceeds size limit");
                }
                text.append(buffer, 0, count);
            }
            return text.toString();
        }
    }

    /** A non-2xx Chat Completions response, with enough detail to classify the failure. */
    public static final class RequestException extends IOException {
        private static final Pattern IMAGE_HINT = Pattern.compile(
                "image|vision|multimodal|modalit|content|base64", Pattern.CASE_INSENSITIVE);

        public final int statusCode;

        RequestException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        /**
         * Heuristic: the endpoint rejected an image-bearing request because of the image.
         * 415 and 422 are always treated that way; a 400 only when the provider says so, so an
         * unrelated bad request never disables the image entry.
         */
        public boolean rejectsImage() {
            if (statusCode == 415 || statusCode == 422) return true;
            return statusCode == 400
                    && IMAGE_HINT.matcher(String.valueOf(getMessage())).find();
        }
    }
}
