package com.donglan.chrona;

import com.donglan.chrona.net.RequestControl;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.function.IntSupplier;

/** FIFO process-wide AI admission; downloads and exports do not acquire a slot. */
final class ClassroomSummarySlots {
    private final ArrayDeque<Object> waiting = new ArrayDeque<>();
    private int active;

    Lease acquire(RequestControl control, IntSupplier maximum, Runnable queued) throws IOException {
        Object ticket = new Object();
        synchronized (this) { waiting.addLast(ticket); }
        boolean acquired = false, announced = false;
        try {
            while (true) {
                synchronized (this) {
                    RequestControl.check(control);
                    int limit = maximum.getAsInt();
                    if (limit < 1 || limit > 3) throw new IllegalArgumentException("课堂总结并发数必须为 1、2 或 3");
                    if (waiting.peekFirst() == ticket && active < limit) {
                        waiting.removeFirst();
                        active++;
                        acquired = true;
                        notifyAll();
                        return new Lease();
                    }
                    if (announced) {
                        try { wait(control == null ? 100 : control.timeoutMillis(100)); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException("任务已停止");
                        }
                        continue;
                    }
                }
                // Progress may touch disk or post to UI; never hold the admission lock for it.
                queued.run();
                announced = true;
            }
        } finally {
            if (!acquired) synchronized (this) { waiting.remove(ticket); notifyAll(); }
        }
    }

    synchronized int activeCount() { return active; }
    synchronized int queuedCount() { return waiting.size(); }

    final class Lease implements AutoCloseable {
        private boolean closed;
        @Override public void close() {
            synchronized (ClassroomSummarySlots.this) {
                if (closed) return;
                closed = true;
                active--;
                ClassroomSummarySlots.this.notifyAll();
            }
        }
    }
}
