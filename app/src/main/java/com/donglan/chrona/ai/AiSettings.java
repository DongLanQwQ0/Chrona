package com.donglan.chrona.ai;

import java.net.URI;
import java.net.URISyntaxException;

/** Connection settings for the explicitly selected HTTPS API protocol. */
public final class AiSettings {
    public static final String RESPONSES = "responses";
    public static final String CHAT_COMPLETIONS = "chat_completions";
    public final String baseUrl;
    public final String model;
    public final String apiKey;
    public final String reasoningEffort;
    public final String protocol;
    public final String sessionHeader;

    public AiSettings(String baseUrl, String model, String apiKey) {
        this(baseUrl, model, apiKey, "auto");
    }

    public AiSettings(String baseUrl, String model, String apiKey, String reasoningEffort) {
        this(baseUrl, model, apiKey, reasoningEffort, RESPONSES);
    }

    public AiSettings(String baseUrl, String model, String apiKey, String reasoningEffort, String protocol) {
        this(baseUrl,model,apiKey,reasoningEffort,protocol,"");
    }

    public AiSettings(String baseUrl,String model,String apiKey,String reasoningEffort,String protocol,String sessionHeader){
        validateReasoningEffort(reasoningEffort);
        validateProtocol(protocol);
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
        this.protocol = protocol;
        this.sessionHeader=validateSessionHeader(sessionHeader);
    }

    public static String validateSessionHeader(String value){
        String name=value==null?"":value.trim();
        if(name.isEmpty())return "";
        if(!name.matches("[-!#$%&'*+.^_`|~0-9A-Za-z]{1,128}")
                ||java.util.Set.of("authorization","proxy-authorization","host","content-type","content-length",
                    "transfer-encoding","content-encoding","connection","expect","te","trailer","upgrade",
                    "keep-alive","proxy-connection","cookie","set-cookie","accept","accept-encoding","user-agent")
                    .contains(name.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("会话请求头名称无效，不能覆盖认证或HTTP传输字段");
        return name;
    }

    public static void validateProtocol(String value) {
        if (!RESPONSES.equals(value) && !CHAT_COMPLETIONS.equals(value))
            throw new IllegalArgumentException("Invalid AI protocol");
    }

    public static void validateReasoningEffort(String value) {
        if (!("auto".equals(value) || "none".equals(value) || "low".equals(value)
                || "medium".equals(value) || "high".equals(value) || "max".equals(value)))
            throw new IllegalArgumentException("Invalid reasoning effort");
    }
}
