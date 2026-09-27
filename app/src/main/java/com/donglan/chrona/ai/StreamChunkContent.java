package com.donglan.chrona.ai;

/** Keeps JSON null and non-text stream fields out of the model's text response. */
final class StreamChunkContent {
    private StreamChunkContent() { }

    static String fromJsonValue(Object value) {
        return value instanceof String ? (String) value : "";
    }
}
