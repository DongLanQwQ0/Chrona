package com.donglan.chrona.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.os.Build;
import android.os.Environment;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

/** Places picked files in public Downloads/Chrona on Android 10+ and exports copies through SAF. */
public final class TaskFileStore {
    private static final Pattern CONTENT_URI = Pattern.compile("content://.+");
    private final Context context;

    public TaskFileStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public TaskFileAttachment importFile(Uri source) throws IOException {
        if (source == null) throw new IllegalArgumentException("source is required");
        ContentResolver resolver = context.getContentResolver();
        String displayName = displayName(resolver, source);
        String mimeType = resolver.getType(source);
        if (mimeType == null || mimeType.trim().isEmpty()) {
            String extension = extension(displayName);
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (mimeType == null || mimeType.trim().isEmpty()) mimeType = "application/octet-stream";

        if (Build.VERSION.SDK_INT < 29) {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new IOException("需要存储权限才能保存到公共 Downloads/Chrona");
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File directory = new File(downloads, "Chrona");
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("无法创建公共 Downloads/Chrona");
            File target = uniqueFile(directory, displayName);
            long size;
            try {
                size = copy(resolver.openInputStream(source), new FileOutputStream(target));
            } catch (IOException | RuntimeException exception) {
                target.delete();
                throw exception;
            }
            ContentValues indexed = new ContentValues();
            indexed.put(MediaStore.MediaColumns.DATA, target.getAbsolutePath());
            indexed.put(MediaStore.MediaColumns.DISPLAY_NAME, target.getName());
            indexed.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            indexed.put(MediaStore.MediaColumns.SIZE, size);
            Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), indexed);
            if (uri == null) throw new IOException("文件已保存，但无法登记到公共媒体索引");
            return new TaskFileAttachment(0, 0, uri.toString(), target.getName(), mimeType, size);
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Chrona");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (destination == null) throw new IOException("无法在 Downloads/Chrona 创建文件");
        long size;
        try {
            size = copy(resolver.openInputStream(source), resolver.openOutputStream(destination, "w"));
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(destination, ready, null, null);
        } catch (IOException | RuntimeException exception) {
            resolver.delete(destination, null, null);
            throw exception;
        }
        return new TaskFileAttachment(0, 0, destination.toString(), displayName, mimeType, size);
    }

    /** Removes a task association only. The copy in the public Downloads folder belongs to user. */
    public void exportTo(String sourceUri, Uri destination) throws IOException {
        if (destination == null) throw new IllegalArgumentException("destination is required");
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(requireUri(sourceUri)));
                OutputStream output = context.getContentResolver().openOutputStream(destination)) {
            if (input == null || output == null) throw new IOException("无法打开文件或保存位置");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    public static boolean isStoredName(String name) {
        return name != null && CONTENT_URI.matcher(name).matches();
    }

    private static String displayName(ContentResolver resolver, Uri source) {
        String name = null;
        try (Cursor cursor = resolver.query(source,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (RuntimeException ignored) { }
        if (name == null || name.trim().isEmpty()) name = "附件";
        name = name.replaceAll("[\\p{Cntrl}/\\\\]", "_").trim();
        if (name.length() > 160) name = name.substring(name.length() - 160);
        return name.isEmpty() ? "附件" : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? ""
                : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireUri(String uri) throws IOException {
        if (!isStoredName(uri)) throw new IOException("附件 URI 无效");
        return uri;
    }

    private static long copy(InputStream input, OutputStream output) throws IOException {
        try (InputStream source = input; OutputStream target = output) {
            if (source == null || target == null)
                throw new IOException("无法打开文件或目标位置");
            byte[] buffer = new byte[32 * 1024];
            long size = 0;
            int count;
            while ((count = source.read(buffer)) != -1) {
                target.write(buffer, 0, count);
                size += count;
            }
            return size;
        }
    }

    private static File uniqueFile(File directory, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String suffix = dot <= 0 ? "" : name.substring(dot);
        for (int index = 0; index < 1000; index++) {
            String candidate = index == 0 ? name : stem + " (" + index + ")" + suffix;
            File file = new File(directory, candidate);
            if (file.createNewFile()) return file;
        }
        throw new IOException("Downloads/Chrona 中同名文件过多");
    }

    private static long querySize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.SIZE },
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                return Math.max(0, cursor.getLong(0));
        } catch (RuntimeException ignored) {
            // Some document providers omit size; count from a stream below.
        }
        return streamSize(resolver, uri);
    }

    private static long streamSize(ContentResolver resolver, Uri uri) {
        long size = 0;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return 0;
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) size += count;
        } catch (IOException | RuntimeException ignored) { }
        return size;
    }
}
