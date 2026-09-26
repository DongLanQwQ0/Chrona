package com.donglan.chrona.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Copies picked or shared images into app-private storage, downscaled for vision requests.
 * Stored names are relative file names; callers persist those names, never absolute paths.
 */
public final class ImageStore {
    /** Vision models downscale long edges to roughly this size; sending more only costs tokens. */
    private static final int MAX_DIMENSION = 1568;
    private static final int JPEG_QUALITY = 90;
    /** A freshly imported image may still be in flight, so only old orphans are collected. */
    private static final long ORPHAN_AGE_MILLIS = 3_600_000L;
    private static final String DIRECTORY_NAME = "attachments";
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f\\-]{36}\\.jpg");

    private final Context context;

    public ImageStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        this.context = context.getApplicationContext();
    }

    /** Copies a provider URI into app storage and returns the stored file name. */
    public String importImage(Uri source) throws IOException {
        if (source == null) throw new IllegalArgumentException("source is required");
        Bitmap bitmap = decodeScaled(source);
        try {
            File directory = directory();
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("无法创建图片目录");
            }
            File target = new File(directory, UUID.randomUUID() + ".jpg");
            try (OutputStream output = new FileOutputStream(target)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw new IOException("无法编码图片");
                }
            } catch (IOException exception) {
                target.delete();
                throw exception;
            }
            return target.getName();
        } finally {
            bitmap.recycle();
        }
    }

    /** Returns the file for a stored name; the name is validated before it touches the path. */
    public File fileFor(String name) {
        if (!isStoredName(name)) throw new IllegalArgumentException("Unknown stored image name");
        return new File(directory(), name);
    }

    /** Reads a stored image for an API request. */
    public byte[] read(String name) throws IOException {
        File file = fileFor(name);
        if (!file.isFile()) throw new IOException("图片文件已不存在");
        long length = file.length();
        if (length <= 0 || length > Integer.MAX_VALUE) throw new IOException("图片文件大小异常");
        byte[] bytes = new byte[(int) length];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("图片文件读取不完整");
                offset += count;
            }
        }
        return bytes;
    }

    /** Returns false when the file was already gone. */
    public boolean delete(String name) {
        return isStoredName(name) && fileFor(name).delete();
    }

    /** Deletes stored images that no task references and that are past the in-flight window. */
    public int deleteUnreferenced(Collection<String> referenced) {
        File[] files = directory().listFiles();
        if (files == null) return 0;
        Set<String> keep = new HashSet<>(referenced == null ? Collections.<String>emptySet()
                : referenced);
        long cutoff = System.currentTimeMillis() - ORPHAN_AGE_MILLIS;
        int removed = 0;
        for (File file : files) {
            String name = file.getName();
            if (keep.contains(name) || file.lastModified() > cutoff) continue;
            if (isStoredName(name) && file.delete()) removed++;
        }
        return removed;
    }

    /** File count and total size of app storage; read by the debug screen. */
    public String describe() {
        File[] files = directory().listFiles();
        if (files == null) return "0 个文件, 0 KB";
        int count = 0;
        long bytes = 0;
        for (File file : files) {
            if (!isStoredName(file.getName())) continue;
            count++;
            bytes += file.length();
        }
        return count + " 个文件, " + (bytes / 1024) + " KB";
    }

    /** True when the name has the shape produced by {@link #importImage}. */
    public static boolean isStoredName(String name) {
        return name != null && STORED_NAME.matcher(name).matches();
    }

    private File directory() {
        return new File(context.getFilesDir(), DIRECTORY_NAME);
    }

    private Bitmap decodeScaled(Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(source)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法读取所选图片");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap decoded;
        try (InputStream input = open(source)) {
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("无法读取所选图片");
        return scaleDown(decoded);
    }

    private InputStream open(Uri source) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(source);
        if (input == null) throw new IOException("无法打开所选图片");
        return input;
    }

    /** Halves while either edge would still decode above the target, then fine-scales. */
    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= MAX_DIMENSION || height / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleDown(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_DIMENSION) return bitmap;
        float scale = (float) MAX_DIMENSION / longest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)),
                true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }
}
