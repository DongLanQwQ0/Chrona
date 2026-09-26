package com.donglan.chrona.processing;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Private, bounded raw model output, separate from SQLite and the rendered task list. */
public final class StreamingOutputStore {
    public static final int WINDOW_BYTES = 12 * 1024;
    public static final int PAGE_BYTES = 16 * 1024;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final File directory;

    public StreamingOutputStore(Context context) {
        directory = new File(context.getFilesDir(), "model-output");
    }

    private File file(long taskId) {
        return new File(directory, "task-" + taskId + ".txt");
    }

    public Writer begin(long taskId) throws IOException {
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Could not create output directory");
        return new Writer(file(taskId));
    }

    public void delete(long taskId) {
        File file = file(taskId);
        if (file.exists() && !file.delete())
            android.util.Log.w("Chrona", "Could not remove model output for task " + taskId);
    }

    public long length(long taskId) { return file(taskId).length(); }

    public String tail(long taskId) throws IOException {
        return read(taskId, Math.max(0, length(taskId) - WINDOW_BYTES), WINDOW_BYTES);
    }

    public String page(long taskId, int index) throws IOException {
        return read(taskId, (long) index * PAGE_BYTES, PAGE_BYTES);
    }

    private String read(long taskId, long offset, int amount) throws IOException {
        File file = file(taskId);
        if (!file.exists()) return "";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long size = input.length();
            if (offset >= size) return "";
            // Skip a partial UTF-8 character at the beginning of each byte window.
            input.seek(offset);
            while (offset < size) {
                int next = input.read();
                if ((next & 0xC0) != 0x80) break;
                offset++;
            }
            input.seek(offset);
            int count = (int) Math.min(amount + 4L, size - offset);
            byte[] bytes = new byte[count];
            input.readFully(bytes);
            int end = (int) Math.min(amount, count);
            while (end < count && (bytes[end] & 0xC0) == 0x80) end++;
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        }
    }

    public static final class Writer implements Closeable {
        private final BufferedOutputStream output;
        private int written;
        private int pending;
        private long lastFlush = System.currentTimeMillis();

        private Writer(File file) throws IOException {
            output = new BufferedOutputStream(new FileOutputStream(file, false), 8192);
        }

        public void append(String chunk) throws IOException {
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            if (written + bytes.length > MAX_BYTES)
                throw new IOException("AI output exceeds preview size limit");
            output.write(bytes);
            written += bytes.length;
            pending += bytes.length;
            long now = System.currentTimeMillis();
            if (pending >= 1024 || now - lastFlush >= 350) {
                output.flush();
                pending = 0;
                lastFlush = now;
            }
        }

        @Override public void close() throws IOException { output.close(); }
    }
}
