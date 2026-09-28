package com.donglan.chrona.ai;

import java.net.URI;
import java.net.URISyntaxException;

/** Connection settings for a Chat Completions-compatible HTTPS service. */
public final class AiSettings {
    public final String baseUrl;
    public final String model;
    public final String apiKey;
    public final String reasoningEffort;

    public AiSettings(String baseUrl, String model, String apiKey) {
        this(baseUrl, model, apiKey, "auto");
    }

    public AiSettings(String baseUrl, String model, String apiKey, String reasoningEffort) {
        validateReasoningEffort(reasoningEffort);
        if (baseUrl == null || model == null || model.trim().isEmpty()
                || apiKey == null || apiKey.trim().isEmpty()
                || apiKey.indexOf('\n') >= 0 || apiKey.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Base URL, model and API key are required");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("Base URL must be an HTTPS origin or path");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid base URL", e);
        }
        this.baseUrl = normalized;
        this.model = model;
        this.apiKey = apiKey;
        this.reasoningEffort = reasoningEffort;
    }

    public static void validateReasoningEffort(String value) {
        if (!("auto".equals(value) || "none".equals(value) || "low".equals(value)
                || "medium".equals(value) || "high".equals(value) || "max".equals(value)))
            throw new IllegalArgumentException("Invalid reasoning effort");
    }
}
