package com.donglan.chrona.debug;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Debug-build event log kept in a rotating file inside app storage, so a failure on the phone
 * can be read back and copied without a connected PC. Every line also goes to logcat.
 */
public final class DiagLog {
    private static final String TAG = "ChronaDiag";
    private static final String FILE_NAME = "diagnostics.log";
    private static final String PREVIOUS_NAME = "diagnostics.log.1";
    private static final long MAX_BYTES = 128 * 1024;
    private static final int MEMORY_LINES = 300;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private DiagLog() {
    }

    /** Appends one timestamped line to memory, logcat and the log file. Never throws. */
    public static void add(Context context, String message) {
        String line = STAMP.format(LocalTime.now()) + " " + message;
        Log.d(TAG, line);
        synchronized (RECENT) {
            RECENT.addLast(line);
            while (RECENT.size() > MEMORY_LINES) RECENT.removeFirst();
        }
        if (context == null) return;
        try {
            File file = file(context);
            if (file.length() > MAX_BYTES) rotate(file);
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException exception) {
            // Diagnostics must never break the feature being diagnosed.
            Log.w(TAG, "could not write diagnostics: " + exception);
        }
    }

    /** The last lines kept in memory, oldest first. */
    public static List<String> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }

    /** Reads the whole log file, oldest first; empty when nothing was logged yet. */
    public static String read(Context context) {
        File file = file(context);
        if (!file.isFile()) return "";
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            return "读取日志失败：" + exception;
        }
    }

    public static long sizeBytes(Context context) {
        return file(context).length();
    }

    /** Removes both the current and the rotated log. */
    public static void clear(Context context) {
        File file = file(context);
        new File(file.getParentFile(), PREVIOUS_NAME).delete();
        file.delete();
    }

    private static void rotate(File file) {
        File previous = new File(file.getParentFile(), PREVIOUS_NAME);
        if (previous.exists()) previous.delete();
        if (!file.renameTo(previous)) file.delete();
    }

    private static File file(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }
}
