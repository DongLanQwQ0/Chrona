package com.donglan.chrona.processing;

import com.donglan.chrona.ai.ChatCompletionClient;
import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/** Local preview failures never discard a valid answer or cause another paid request. */
public final class BestEffortPreview implements ChatCompletionClient.PreviewSink, Closeable {
    private final ChatCompletionClient.PreviewSink sink;
    private final Closeable resource;
    private final Consumer<IOException> failure;
    private boolean disabled;

    public BestEffortPreview(ChatCompletionClient.PreviewSink sink, Closeable resource,
            Consumer<IOException> failure) {
        this.sink = sink; this.resource = resource; this.failure = failure;
    }

    @Override public void append(String chunk) {
        if (disabled || sink == null) return;
        try { sink.append(chunk); } catch (IOException exception) { failed(exception); }
    }

    @Override public void appendReasoning(String chunk) {
        if (disabled || sink == null) return;
        try { sink.appendReasoning(chunk); } catch (IOException exception) { failed(exception); }
    }

    private void failed(IOException exception) {
        if (!disabled) { disabled = true; failure.accept(exception); }
    }

    @Override public void close() {
        if (resource == null) return;
        try { resource.close(); } catch (IOException exception) { failed(exception); }
    }
}
