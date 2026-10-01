package com.donglan.chrona.net;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One monotonic deadline and cancellation signal shared by links, model calls and retries. */
public final class RequestControl implements AutoCloseable {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            runnable -> { Thread thread = new Thread(runnable, "chrona-network-deadline");
                thread.setDaemon(true); return thread; });
    private final long deadline;
    private final ScheduledFuture<?> timeout;
    private volatile boolean cancelled;
    private volatile boolean expired;
    private HttpURLConnection active;

    public RequestControl(long budgetMillis) {
        if (budgetMillis <= 0) throw new IllegalArgumentException("Positive budget required");
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        timeout = TIMER.schedule(() -> {
            expired = true;
            disconnectActive();
        }, budgetMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isCancelled() { return cancelled; }

    public void check() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("任务已停止");
        if (expired || System.nanoTime() >= deadline)
            throw new SocketTimeoutException("解析超过总时间限制，请稍后重试");
    }

    public static void check(RequestControl control) throws IOException {
        if (control != null) control.check();
        else if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("任务已停止");
    }

    public int timeoutMillis(int maximum) throws IOException {
        check();
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        return (int) Math.max(1, Math.min(maximum, remaining));
    }

    public void register(HttpURLConnection connection) throws IOException {
        synchronized (this) { active = connection; }
        try { check(); }
        catch (IOException exception) { connection.disconnect(); throw exception; }
    }

    public synchronized void unregister(HttpURLConnection connection) {
        if (active == connection) active = null;
    }

    public void cancel() {
        cancelled = true;
        disconnectActive();
    }

    private void disconnectActive() {
        HttpURLConnection connection;
        synchronized (this) { connection = active; }
        if (connection != null) connection.disconnect();
    }

    @Override public void close() { timeout.cancel(false); }
}
