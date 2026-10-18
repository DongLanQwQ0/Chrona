package com.donglan.chrona;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskFileStore;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.image.ImageStore;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.Map;

/** Raw, bounded uploads; authentication and CSRF validation belong to the HTTP router. */
final class LanAttachmentUpload {
    static final long MAX_BYTES = 20L * 1024 * 1024;

    static JSONObject receive(Context context, NanoHTTPD.IHTTPSession request) throws Exception {
        Map<String, String> headers = request.getHeaders();
        if (headers.containsKey("transfer-encoding")
                || !"application/octet-stream".equals(headers.get("content-type")))
            throw new IllegalArgumentException("上传格式无效");
        long size = number(headers.get("content-length"), 1, MAX_BYTES);
        long id = number(headers.get("x-chrona-task-id"), 1, Long.MAX_VALUE);
        long revision = number(headers.get("x-chrona-revision"), 0, Long.MAX_VALUE);
        String name = filename(headers.get("x-chrona-filename"));
        String kind = headers.get("x-chrona-image");
        if (!"true".equals(kind) && !"false".equals(kind))
            throw new IllegalArgumentException("附件类型无效");
        File temporary = File.createTempFile("lan-upload-", ".tmp", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                copy(request.getInputStream(), output, size);
            }
            synchronized (AndroidSync.LOCK) {
                try (TaskStore store = new TaskStore(context)) {
                    SQLiteDatabase db = store.getWritableDatabase();
                    ImageStore images = new ImageStore(context);
                    String image = null;
                    TaskFileAttachment file = null;
                    boolean committed = false;
                    try {
                        db.beginTransaction();
                        try {
                            if (store.dataRevision() != revision) throw new ConcurrentModificationException();
                            TaskRecord task = store.getTask(id);
                            if (task == null || TaskRecord.QUEUED.equals(task.status)
                                    || TaskRecord.PROCESSING.equals(task.status))
                                throw new ConcurrentModificationException();
                            if (store.isHomeworkCapture(id))
                                throw new IllegalArgumentException("作业收件由同步管理，无法追加附件");
                            if ("false".equals(kind) && android.os.Build.VERSION.SDK_INT < 29
                                    && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                throw new SecurityException("需要存储权限才能保存附件");
                            if ("true".equals(kind)) {
                                image = images.importImage(Uri.fromFile(temporary));
                                if (!store.addImageAttachment(id, image)) throw new ConcurrentModificationException();
                            } else {
                                int dot = name.lastIndexOf('.');
                                String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
                                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                                file = new TaskFileStore(context).importFile(Uri.fromFile(temporary), name,
                                        mime == null ? "application/octet-stream" : mime);
                                if (!store.addFileAttachment(id, file)) throw new ConcurrentModificationException();
                            }
                            db.setTransactionSuccessful();
                        } finally { db.endTransaction(); }
                        committed = true;
                    } catch (Exception error) {
                        if (!committed) {
                            if (image != null) images.delete(image);
                            if (file != null) {
                                try { context.getContentResolver().delete(Uri.parse(file.storedName), null, null); }
                                catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
                            }
                        }
                        throw error;
                    }
                    return new JSONObject().put("ok", true).put("id", Long.toString(id))
                            .put("revision", store.dataRevision());
                }
            }
        } finally { temporary.delete(); }
    }

    static long number(String text, long minimum, long maximum) {
        if (text == null || !text.matches("[0-9]{1,19}")) throw new IllegalArgumentException("上传参数无效");
        long value;
        try { value = Long.parseLong(text); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("上传参数无效", error); }
        if (value < minimum || value > maximum) throw new IllegalArgumentException("附件大小或参数超出范围");
        return value;
    }

    static String filename(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > 8192)
            throw new IllegalArgumentException("附件名称无效");
        // '+' is literal in encodeURIComponent; URLDecoder otherwise treats it as a space.
        String name;
        try { name = URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
        if (name.indexOf('\uFFFD') >= 0) throw new IllegalArgumentException("附件名称编码无效");
        name = name.replaceAll("[\\p{Cntrl}/\\\\]", "_").trim();
        if (name.length() > 160) name = name.substring(name.length() - 160);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("附件名称无效");
        return name;
    }

    static void copy(InputStream input, java.io.OutputStream output, long size) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count < 0) throw new IOException("附件上传未完成，请重试");
            if (count == 0) throw new IOException("附件上传读取失败，请重试");
            output.write(buffer, 0, count);
            remaining -= count;
        }
    }

    private LanAttachmentUpload() { }
}
