package com.donglan.chrona.ai;

import com.donglan.chrona.net.RequestControl;
import com.donglan.chrona.processing.BestEffortPreview;
import com.donglan.chrona.web.LinkFetcher;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real client/control code against offline HTTPS test connections; no network or API charges. */
public final class NetworkSafetyCheck {
    private static final String ANSWER = "{\"choices\":[{\"message\":{\"reasoning_content\":\"thought\",\"content\":\"{\\\"events\\\":[]}\"}}]}";
    private static final String SSE = "data: {\"choices\":[{\"delta\":{\"content\":\"{\\\"events\\\":[]}\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
    private static final AiSettings SETTINGS = new AiSettings("https://offline.test/v1", "test-model", "test-key");
    private static final AtomicInteger posts = new AtomicInteger();
    private static volatile String mode = "success";
    private static volatile FakeConnection last;
    private static volatile CountDownLatch reading;

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                last = new FakeConnection(url, mode);
                return last;
            }
        } : null);
        previewFailures();
        cancelBeforePost();
        cancelWhileLinkReading();
        cancelWhileModelReading();
        totalDeadlineStopsDrip();
        remainingTimeoutAndConnectionIdentity();
        System.out.println("NetworkSafetyCheck passed: preview append/reasoning/close, pre-send cancellation, blocked links/model, drip deadline, remaining timeout and connection identity");
    }

    private static ParseResult parse(RequestControl control, ChatCompletionClient.PreviewSink sink,
            boolean stream) throws IOException {
        return new ChatCompletionClient(SETTINGS, control).parseImages(1, "明天会议",
                Collections.emptyList(), null, 0, "UTC", sink, stream);
    }

    private static void previewFailures() throws Exception {
        mode = "success";
        for (String failAt : new String[]{"append", "reasoning", "close"}) {
            posts.set(0);
            AtomicInteger failures = new AtomicInteger();
            ChatCompletionClient.PreviewSink sink = new ChatCompletionClient.PreviewSink() {
                public void append(String chunk) throws IOException {
                    if (failAt.equals("append")) throw new IOException("disk full");
                }
                public void appendReasoning(String chunk) throws IOException {
                    if (failAt.equals("reasoning")) throw new IOException("disk full");
                }
            };
            ParseResult result;
            try (RequestControl control = new RequestControl(2000);
                    BestEffortPreview preview = new BestEffortPreview(sink,
                            () -> { if (failAt.equals("close")) throw new IOException("close failed"); },
                            exception -> failures.incrementAndGet())) {
                result = parse(control, preview, false);
            }
            check(result.candidates.isEmpty() && posts.get() == 1 && failures.get() == 1, failAt);
        }
    }

    private static void cancelBeforePost() throws Exception {
        posts.set(0);
        try (RequestControl control = new RequestControl(2000)) {
            control.cancel();
            try { parse(control, null, false); throw new AssertionError("cancel ignored"); }
            catch (InterruptedIOException expected) { }
            check(posts.get() == 0, "no cancelled POST");
        }
    }

    private static void cancelWhileLinkReading() throws Exception {
        mode = "block"; reading = new CountDownLatch(1); posts.set(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (RequestControl control = new RequestControl(2000)) {
            Thread worker = new Thread(() -> {
                try {
                    LinkFetcher.fetchResult(Collections.singletonList("https://offline.test/article"), control);
                    parse(control, null, false);
                } catch (Throwable exception) { failure.set(exception); }
            });
            worker.start();
            check(reading.await(1, TimeUnit.SECONDS), "link started");
            FakeConnection connection = last;
            control.cancel();
            worker.join(1000);
            check(!worker.isAlive() && connection.disconnected && posts.get() == 0
                    && failure.get() instanceof InterruptedIOException, "link cancellation prevents AI");
        }
    }

    private static void cancelWhileModelReading() throws Exception {
        mode = "block"; reading = new CountDownLatch(1); posts.set(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (RequestControl control = new RequestControl(2000)) {
            Thread worker = new Thread(() -> {
                try { parse(control, null, false); }
                catch (Throwable exception) { failure.set(exception); }
            });
            worker.start();
            check(reading.await(1, TimeUnit.SECONDS), "model started");
            FakeConnection connection = last;
            control.cancel(); worker.join(1000);
            check(!worker.isAlive() && connection.disconnected && posts.get() == 1
                    && failure.get() instanceof InterruptedIOException, "model cancellation");
        }
    }

    private static void totalDeadlineStopsDrip() throws Exception {
        mode = "drip";
        long begin = System.nanoTime();
        try (RequestControl control = new RequestControl(180)) {
            try { parse(control, null, true); throw new AssertionError("deadline ignored"); }
            catch (SocketTimeoutException expected) { }
            check(last.disconnected, "deadline disconnects");
        }
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin) < 1000, "drip cannot extend deadline");
    }

    private static void remainingTimeoutAndConnectionIdentity() throws Exception {
        mode = "success";
        try (RequestControl control = new RequestControl(1000)) {
            parse(control, null, false);
            int first = last.getReadTimeout();
            Thread.sleep(80);
            parse(control, null, false);
            check(last.getReadTimeout() < first && last.getConnectTimeout() < first, "remaining budget");
            FakeConnection old = new FakeConnection(new URL("https://offline.test/old"), "success");
            FakeConnection fresh = new FakeConnection(new URL("https://offline.test/new"), "success");
            control.register(old); control.register(fresh); control.unregister(old); control.cancel();
            check(fresh.disconnected && !old.disconnected, "old cleanup preserves new connection");
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final String behavior;
        volatile boolean disconnected;
        final CountDownLatch release = new CountDownLatch(1);
        FakeConnection(URL url, String behavior) { super(url); this.behavior = behavior; }
        public void connect() { }
        public boolean usingProxy() { return false; }
        public void disconnect() { disconnected = true; release.countDown(); }
        public OutputStream getOutputStream() { posts.incrementAndGet(); return new ByteArrayOutputStream(); }
        public int getResponseCode() { return 200; }
        public String getContentType() {
            return behavior.equals("drip") ? "text/event-stream" : "application/json";
        }
        public InputStream getInputStream() {
            if (behavior.equals("success")) return new ByteArrayInputStream(ANSWER.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = SSE.getBytes(StandardCharsets.UTF_8);
            return new InputStream() {
                int offset;
                public int read() throws IOException {
                    if (reading != null) reading.countDown();
                    try {
                        if (behavior.equals("block")) release.await();
                        else Thread.sleep(20);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt(); throw new InterruptedIOException();
                    }
                    if (disconnected) throw new IOException("disconnected");
                    return offset < bytes.length ? bytes[offset++] & 255 : -1;
                }
                public int read(byte[] buffer, int start, int count) throws IOException {
                    int value = read(); if (value < 0) return -1;
                    buffer[start] = (byte) value; return 1;
                }
            };
        }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
