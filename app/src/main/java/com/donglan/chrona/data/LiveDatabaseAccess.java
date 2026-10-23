package com.donglan.chrona.data;

/** Protects live database files from replacement while a handle or parsing execution owns them. */
public final class LiveDatabaseAccess {
    private static int users;
    private static Thread restoring;

    private LiveDatabaseAccess() { }

    /** Ordinary readers wait for replacement; the restoring thread may open the installed database. */
    public static synchronized Lease acquire() {
        boolean interrupted = false;
        while (restoring != null && restoring != Thread.currentThread()) {
            try { LiveDatabaseAccess.class.wait(); }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        users++;
        return new Lease();
    }

    /** Job admission must not block Android's main thread during a restore. */
    public static synchronized Lease tryAcquire() {
        if (restoring != null) return null;
        users++;
        return new Lease();
    }

    /** Never wait while holding business locks: an existing reader may need those same locks. */
    public static synchronized Restore beginRestore() {
        if (restoring != null || users != 0)
            throw new IllegalStateException("数据仍在使用中，请稍后再恢复备份");
        restoring = Thread.currentThread();
        return new Restore();
    }

    public static final class Lease implements AutoCloseable {
        private boolean closed;
        private Lease() { }
        @Override public void close() {
            synchronized (LiveDatabaseAccess.class) {
                if (!closed) { closed = true; users--; }
            }
        }
    }

    public static final class Restore implements AutoCloseable {
        private boolean closed;
        private Restore() { }
        @Override public void close() {
            synchronized (LiveDatabaseAccess.class) {
                if (closed) return;
                if (restoring != Thread.currentThread())
                    throw new IllegalStateException("Restore must finish on its owning thread");
                closed = true;
                restoring = null;
                LiveDatabaseAccess.class.notifyAll();
            }
        }
    }
}
