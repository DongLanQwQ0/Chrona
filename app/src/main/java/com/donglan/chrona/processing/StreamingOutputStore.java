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
    /** Written before the answer when the model streamed reasoning first; readers split on it. */
    public static final String CONTENT_SECTION_MARKER = "[模型输出]";
    public static final String REASONING_SECTION_MARKER = "[思考内容]";
    public static final String THINKING_MESSAGE = "模型正在思考，输出尚未生成";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final File directory;
    private final java.util.Map<Long, PreviewState> previews = new java.util.HashMap<>();
    private long renderedTask = -1, renderedLength = -1, renderedModified = -1;
    private byte[] rendered = new byte[0];

    public StreamingOutputStore(Context context) {
        this(new File(context.getFilesDir(), "model-output"));
    }

    StreamingOutputStore(File directory) {
        this.directory = directory;
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

    /**
     * Removes preview files whose input no longer exists. A process killed mid-parse can leave one
     * behind, because the delete that normally accompanies a removal never ran.
     *
     * @return how many files were removed
     */
    public int deleteUnreferenced(java.util.Set<Long> liveTaskIds) {
        File[] files = directory.listFiles();
        if (files == null) return 0;
        int removed = 0;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("task-") || !name.endsWith(".txt")) continue;
            long id;
            try {
                id = Long.parseLong(name.substring("task-".length(), name.length() - 4));
            } catch (NumberFormatException exception) {
                continue;
            }
            if (!liveTaskIds.contains(id) && file.delete()) removed++;
        }
        return removed;
    }

    public long length(long taskId) { return file(taskId).length(); }

    public String tail(long taskId) throws IOException {
        return read(taskId, Math.max(0, length(taskId) - WINDOW_BYTES), WINDOW_BYTES);
    }

    public String page(long taskId, int index) throws IOException {
        return read(taskId, (long) index * PAGE_BYTES, PAGE_BYTES);
    }

    /** Only the answer is previewed. Scan newly flushed bytes, then read a bounded answer tail. */
    public String readPreview(long taskId) throws IOException {
        File source = file(taskId);
        if (!source.exists()) return "";
        PreviewState state = previews.computeIfAbsent(taskId, ignored -> new PreviewState());
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            long size = input.length();
            // Beginning can change on reparse, even if its new length equals the previous length.
            byte[] header = new byte[(int) Math.min(128, size)];
            input.readFully(header);
            if (size < state.scanned || !java.util.Arrays.equals(header, state.header)) {
                state = new PreviewState();
                previews.put(taskId, state);
            }
            state.header = header;
            scanSections(input, size, state);
            if (state.reasoning && state.answerStart < 0) return THINKING_MESSAGE;
            long end = state.reasoning ? state.answerEnd : size;
            long start = Math.max(Math.max(0, state.answerStart), end - WINDOW_BYTES);
            String answer = read(taskId, start, (int) Math.max(0, end - start));
            // A tail that begins midway through JSON must keep its original characters.
            return start > Math.max(0, state.answerStart) ? answer
                    : com.donglan.chrona.JsonFormat.format(answer);
        }
    }

    private static final byte[][] MARKERS = {
        (REASONING_SECTION_MARKER + "\n").getBytes(StandardCharsets.UTF_8),
        (CONTENT_SECTION_MARKER + "\n").getBytes(StandardCharsets.UTF_8)
    };

    private static void scanSections(RandomAccessFile input, long size, PreviewState state)
            throws IOException {
        long position = Math.max(0, state.scanned - 64);
        while (position < size) {
            input.seek(position);
            byte[] block = new byte[(int) Math.min(8192, size - position)];
            input.readFully(block);
            for (int i = 0; i < block.length; i++) {
                if (!(position + i == 0 || (i > 0 && block[i - 1] == '\n'))) continue;
                for (int section = 0; section < MARKERS.length; section++) {
                    byte[] marker = MARKERS[section];
                    if (i + marker.length > block.length) continue;
                    boolean matches = true;
                    for (int j = 0; j < marker.length; j++)
                        if (block[i + j] != marker[j]) { matches = false; break; }
                    if (!matches) continue;
                    if (section == 0) {
                        state.reasoning = true;
                        state.answerEnd = position + i;
                    } else {
                        state.reasoning = false;
                        state.answerStart = position + i + marker.length;
                    }
                }
            }
            if (position + block.length >= size) break;
            position += block.length - 64;
        }
        state.scanned = size;
    }

    private static final class PreviewState {
        long scanned, answerStart = -1, answerEnd;
        boolean reasoning;
        byte[] header;
    }

    /** Formatting precedes pagination, so middle pages are never interpreted as JSON documents. */
    public long renderedLength(long taskId) throws IOException {
        updateRendered(taskId);
        return rendered.length;
    }

    public String readPage(long taskId, int index) throws IOException {
        updateRendered(taskId);
        int start = (int) Math.min(rendered.length, Math.max(0L, (long) index * PAGE_BYTES));
        while (start < rendered.length && (rendered[start] & 0xC0) == 0x80) start++;
        int end = Math.min(rendered.length, (index + 1) * PAGE_BYTES);
        while (end < rendered.length && (rendered[end] & 0xC0) == 0x80) end++;
        return new String(rendered, start, Math.max(0, end - start), StandardCharsets.UTF_8);
    }

    private void updateRendered(long taskId) throws IOException {
        File source = file(taskId);
        long size = source.length(), modified = source.lastModified();
        if (renderedTask == taskId && renderedLength == size && renderedModified == modified) return;
        String raw = read(taskId, 0, (int) Math.min(MAX_BYTES + 1024L, size));
        rendered = com.donglan.chrona.JsonFormat.formatModelOutput(raw).getBytes(StandardCharsets.UTF_8);
        renderedTask = taskId;
        renderedLength = size;
        renderedModified = modified;
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
        /** Appended once the preview hits its cap, so a reader can tell why it stops. */
        private static final String TRUNCATION_NOTE = "\n…（输出过长，预览已截断；本次解析不受影响）\n";
        private final BufferedOutputStream output;
        private int written;
        private int pending;
        private boolean truncated;
        private boolean reasoningStarted;
        private boolean contentStarted;
        private int lastSection;
        private long lastFlush = System.currentTimeMillis();

        private Writer(File file) throws IOException {
            output = new BufferedOutputStream(new FileOutputStream(file, false), 8192);
        }

        /**
         * Appends one streamed chunk. The cap applies to the preview only: a very long answer is
         * still assembled and parsed in full, it just stops growing the file the reader pages.
         */
        public void append(String chunk) throws IOException {
            if (chunk == null || chunk.isEmpty()) return;
            if (reasoningStarted && (!contentStarted || lastSection != 2)) {
                appendRaw((written == 0 ? "" : "\n\n") + CONTENT_SECTION_MARKER + "\n");
                contentStarted = true;
            } else if (!contentStarted) {
                contentStarted = true;
            }
            lastSection = 2;
            appendRaw(chunk);
        }

        /** Stores optional model reasoning in the same backward-compatible output file. */
        public void appendReasoning(String chunk) throws IOException {
            if (chunk == null || chunk.isEmpty()) return;
            if (!reasoningStarted || lastSection != 1) {
                appendRaw((written == 0 ? "" : "\n\n") + REASONING_SECTION_MARKER + "\n");
                reasoningStarted = true;
            }
            lastSection = 1;
            appendRaw(chunk);
        }

        private void appendRaw(String chunk) throws IOException {
            if (truncated) return;
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            if (written + bytes.length > MAX_BYTES) {
                truncated = true;
                output.write(TRUNCATION_NOTE.getBytes(StandardCharsets.UTF_8));
                output.flush();
                return;
            }
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
