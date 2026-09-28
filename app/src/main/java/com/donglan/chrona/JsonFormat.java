package com.donglan.chrona;

import com.donglan.chrona.processing.StreamingOutputStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Readable form for the JSON the model is asked to return. A finished reply pretty-prints through
 * the parser; a reply that is still arriving does not parse yet, so the text is re-indented
 * instead of staying one endless line.
 */
public final class JsonFormat {
    private static final int INDENT = 2;

    private JsonFormat() { }

    /** Pretty-prints {@code value} when it is JSON; returns it unchanged when it is prose. */
    public static String format(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return value;
        String parsed = parsedJson(trimmed);
        if (parsed != null) return parsed;
        if (!looksLikeJson(trimmed)) return value;
        return indent(trimmed);
    }

    /** Formats the answer part of a stored model output, leaving any reasoning text above it. */
    public static String formatModelOutput(String value) {
        if (value == null) return "";
        java.util.regex.Matcher sections = java.util.regex.Pattern.compile(
                "(?m)^\\[(思考内容|模型输出)\\](?:\\r?\\n|$)").matcher(value);
        StringBuilder result = new StringBuilder();
        int start = 0;
        boolean reasoning = false;
        boolean found = false;
        while (sections.find()) {
            result.append(reasoning ? value.substring(start, sections.start())
                    : formatPreservingEdges(value.substring(start, sections.start())));
            result.append(sections.group());
            reasoning = "思考内容".equals(sections.group(1));
            start = sections.end();
            found = true;
        }
        if (!found) return format(value);
        result.append(reasoning ? value.substring(start)
                : formatPreservingEdges(value.substring(start)));
        return result.toString();
    }

    private static String formatPreservingEdges(String value) {
        int first = 0, last = value.length();
        while (first < last && Character.isWhitespace(value.charAt(first))) first++;
        while (last > first && Character.isWhitespace(value.charAt(last - 1))) last--;
        return value.substring(0, first) + format(value.substring(first, last))
                + value.substring(last);
    }

    /** The canonical rendering when the whole value is one complete JSON document, else null. */
    private static String parsedJson(String value) {
        try {
            JSONTokener tokener = new JSONTokener(value);
            Object parsed = tokener.nextValue();
            if (tokener.nextClean() != 0) return null;
            if (parsed instanceof JSONObject) return ((JSONObject) parsed).toString(INDENT);
            if (parsed instanceof JSONArray) return ((JSONArray) parsed).toString(INDENT);
        } catch (Exception ignored) {
            // Streaming output is often incomplete JSON; the original text stands until it parses.
        }
        return null;
    }

    /**
     * Re-indenting is only safe on JSON: prose can contain the same commas and colons. The opening
     * brace is what tells them apart, so plain sentences are handed back untouched.
     */
    private static boolean looksLikeJson(String value) {
        char first = value.charAt(0);
        int next = 1;
        while (next < value.length() && Character.isWhitespace(value.charAt(next))) next++;
        if (next == value.length()) return true;
        char token = value.charAt(next);
        if (first == '{') return token == '"' || token == '}';
        return first == '[' && (token == '"' || token == '{' || token == '['
                || token == ']' || token == '-' || Character.isDigit(token)
                || value.startsWith("true", next) || value.startsWith("false", next)
                || value.startsWith("null", next));
    }

    /** Re-indents JSON without parsing it, so a half-received object still reads as a tree. */
    static String indent(String value) {
        StringBuilder out = new StringBuilder(value.length() + 64);
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"':
                    inString = true;
                    out.append(c);
                    break;
                case '{': case '[':
                    out.append(c);
                    if (closesImmediately(value, i)) break;
                    newLine(out, ++depth);
                    break;
                case '}': case ']':
                    depth = Math.max(0, depth - 1);
                    newLine(out, depth);
                    out.append(c);
                    break;
                case ',':
                    out.append(c);
                    newLine(out, depth);
                    break;
                case ':':
                    out.append(": ");
                    break;
                default:
                    if (!Character.isWhitespace(c)) out.append(c);
                    break;
            }
        }
        return out.toString();
    }

    /** True when the next meaningful character closes the container that just opened. */
    private static boolean closesImmediately(String value, int openIndex) {
        char close = value.charAt(openIndex) == '{' ? '}' : ']';
        for (int i = openIndex + 1; i < value.length(); i++) {
            char next = value.charAt(i);
            if (Character.isWhitespace(next)) continue;
            return next == close;
        }
        return false;
    }

    private static void newLine(StringBuilder out, int depth) {
        out.append('\n');
        for (int i = 0; i < depth; i++) out.append("  ");
    }
}
