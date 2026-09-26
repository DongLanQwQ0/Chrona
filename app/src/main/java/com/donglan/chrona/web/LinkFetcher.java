package com.donglan.chrona.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the pages behind links found in an input, so parsing can use details that only live
 * online. Every request is bounded by link count, bytes and time; a link that cannot be read
 * simply contributes no text instead of failing the parse. Call from a worker thread.
 */
public final class LinkFetcher {
    /** Token control: only the first few links are read, and only an excerpt of each page. */
    public static final int MAX_LINKS = 3;
    public static final int MAX_TEXT_CHARS = 4000;

    private static final int MAX_BYTES = 512 * 1024;
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_PAGE_CHARS = 2000;
    private static final String USER_AGENT = "Chrona/0.5 (Android)";

    private static final Pattern LINK = Pattern.compile(
            "(?:https?://|www\\.)[^\\s<>\"'\\\\，。；：！？（）【】《》、]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?\"']+$");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern DESCRIPTION = Pattern.compile(
            "(?is)<meta[^>]+name=[\"']?description[\"']?[^>]*content=[\"']([^\"']*)[\"']");
    private static final Pattern DROP_BLOCK = Pattern.compile(
            "(?is)<(script|style|noscript|svg|iframe|template|head)\\b.*?</\\1\\s*>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>|</"
            + "(p|div|li|tr|td|h[1-6]|section|article|header|footer|ul|ol|table)\\s*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([\\w-]+)");
    private static final Pattern INLINE_SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern LINE_PADDING = Pattern.compile("(?m)^[ \\t]+|[ \\t]+$");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    /** Returns up to {@link #MAX_LINKS} absolute HTTP(S) links, in the order they appear. */
    public static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null) return urls;
        Matcher matcher = LINK.matcher(text);
        while (matcher.find() && urls.size() < MAX_LINKS) {
            String candidate = TRAILING_PUNCTUATION.matcher(matcher.group()).replaceAll("");
            if (candidate.toLowerCase(Locale.ROOT).startsWith("www.")) {
                candidate = "https://" + candidate;
            }
            if (isHttpUrl(candidate) && !urls.contains(candidate)) urls.add(candidate);
        }
        return urls;
    }

    /** Reads every link and returns a labelled excerpt; empty when nothing could be read. */
    public static String fetch(List<String> urls) {
        if (urls == null || urls.isEmpty()) return "";
        StringBuilder combined = new StringBuilder();
        for (String url : urls) {
            if (combined.length() >= MAX_TEXT_CHARS) break;
            String page = truncate(readPage(url), MAX_PAGE_CHARS);
            if (page.isEmpty()) continue;
            if (combined.length() > 0) combined.append("\n\n");
            combined.append("来源：").append(url).append('\n').append(page);
        }
        return truncate(combined.toString(), MAX_TEXT_CHARS);
    }

    private static String readPage(String url) {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(current).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MILLIS);
                connection.setReadTimeout(TIMEOUT_MILLIS);
                // Redirects are followed by hand so an http link may still land on https.
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "text/html,text/plain;q=0.9,*/*;q=0.1");
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String next = resolve(current, connection.getHeaderField("Location"));
                    if (next == null) return "";
                    current = next;
                    continue;
                }
                if (status < 200 || status >= 300) return "";
                byte[] body = readLimited(connection.getInputStream());
                String charset = charsetOf(connection.getContentType(), body);
                return toText(decode(body, charset));
            } catch (IOException | RuntimeException exception) {
                return "";
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return "";
    }

    private static String resolve(String base, String location) {
        if (location == null || location.isEmpty()) return null;
        try {
            String next = new URI(base).resolve(location).toString();
            return isHttpUrl(next) ? next : null;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /** Prefers the header charset, then a meta tag in the first bytes, then UTF-8. */
    private static String charsetOf(String contentType, byte[] body) {
        if (contentType != null) {
            Matcher header = CHARSET.matcher(contentType);
            if (header.find()) return header.group(1);
        }
        String head = new String(body, 0, Math.min(body.length, 2048), StandardCharsets.ISO_8859_1);
        Matcher inPage = CHARSET.matcher(head);
        return inPage.find() ? inPage.group(1) : "UTF-8";
    }

    private static String decode(byte[] body, String charsetName) {
        try {
            return new String(body, Charset.forName(charsetName));
        } catch (RuntimeException exception) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readLimited(InputStream stream) throws IOException {
        try (InputStream input = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BYTES) {
                    buffer.write(chunk, 0, MAX_BYTES - buffer.size());
                    break;
                }
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        }
    }

    /** Turns one HTML page into the title, description and visible text, in that order. */
    static String toText(String html) {
        if (html == null || html.isEmpty()) return "";
        String title = clean(match(TITLE, html));
        String description = clean(match(DESCRIPTION, html));
        String body = COMMENT.matcher(html).replaceAll(" ");
        body = DROP_BLOCK.matcher(body).replaceAll(" ");
        body = LINE_BREAK.matcher(body).replaceAll("\n");
        body = TAG.matcher(body).replaceAll(" ");
        body = decodeEntities(body);
        body = INLINE_SPACE.matcher(body).replaceAll(" ");
        body = BLANK_LINES.matcher(body).replaceAll("\n").trim();
        body = LINE_PADDING.matcher(body).replaceAll("");

        StringBuilder text = new StringBuilder();
        appendPart(text, title);
        appendPart(text, description);
        appendPart(text, body);
        return text.toString().trim();
    }

    private static void appendPart(StringBuilder text, String part) {
        if (part.isEmpty()) return;
        if (text.length() > 0) text.append('\n');
        text.append(part);
    }

    private static String match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String clean(String value) {
        if (value == null || value.isEmpty()) return "";
        return INLINE_SPACE.matcher(decodeEntities(value)).replaceAll(" ").trim();
    }

    private static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(entityValue(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String entityValue(String entity) {
        if (entity.startsWith("#")) {
            try {
                int code = entity.startsWith("#x") || entity.startsWith("#X")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                if (code <= 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) return "";
                return new String(Character.toChars(code));
            } catch (NumberFormatException exception) {
                return "";
            }
        }
        switch (entity.toLowerCase(Locale.ROOT)) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "mdash": return "—";
            case "ndash": return "–";
            case "hellip": return "…";
            default: return " ";
        }
    }

    private static String truncate(String text, int limit) {
        if (text.length() <= limit) return text;
        int end = limit;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
