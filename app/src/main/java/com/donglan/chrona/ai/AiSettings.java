package com.donglan.chrona.ai;

import java.net.URI;
import java.net.URISyntaxException;

/** Connection settings for a Chat Completions-compatible HTTPS service. */
public final class AiSettings {
    public final String baseUrl;
    public final String model;
    public final String apiKey;

    public AiSettings(String baseUrl, String model, String apiKey) {
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
    }
}
