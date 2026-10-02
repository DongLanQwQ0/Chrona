package com.donglan.chrona;

import android.content.Context;
import android.util.AtomicFile;
import com.donglan.chrona.timetable.Timetable;
import com.donglan.chrona.timetable.TimetableParser;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

/** The original ICS is kept privately and replaced atomically only after a successful import. */
final class TimetableStore {
    private static final int FORMAT = 1;
    private static final int MAX_SNAPSHOT_BYTES = TimetableParser.MAX_BYTES * 3;
    private static final Object LOCK = new Object();
    private final AtomicFile file;

    static final class Document {
        final String name, source;
        final long importedAt;
        final Timetable table;
        Document(String name, String source, long importedAt, Timetable table) {
            this.name = name;
            this.source = source;
            this.importedAt = importedAt;
            this.table = table;
        }
    }

    TimetableStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "course-timetable.json"));
    }

    static Document prepare(String name, String source, ZoneId zone) throws IOException {
        Timetable table = TimetableParser.parse(source, zone);
        String label = name == null || name.trim().isEmpty() ? "导入的课表" : name.trim();
        if (label.length() > 160) label = label.substring(0, 160);
        return new Document(label, source, System.currentTimeMillis(), table);
    }

    Document load() throws Exception {
        String snapshot = snapshot();
        return snapshot == null ? null : inspect(snapshot);
    }

    String snapshot() throws IOException {
        synchronized (LOCK) {
            if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
            try (InputStream input = file.openRead()) { return readUtf8(input, MAX_SNAPSHOT_BYTES); }
        }
    }

    static Document inspect(String snapshot) throws Exception {
        if (snapshot == null) return null;
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES)
            throw new IOException("课表备份过大");
        JSONObject json = new JSONObject(snapshot);
        if (json.getInt("format") != FORMAT) throw new IOException("课表数据版本不受支持");
        String source = json.getString("ics");
        return new Document(json.getString("name"), source, json.getLong("importedAt"),
                TimetableParser.parse(source, ZoneId.of(json.getString("zone"))));
    }

    void save(Document document) throws Exception {
        JSONObject json = new JSONObject();
        json.put("format", FORMAT);
        json.put("name", document.name);
        json.put("ics", document.source);
        json.put("importedAt", document.importedAt);
        json.put("zone", document.table.zone.getId());
        write(json.toString());
    }

    /** Used by full backup restore, including rollback to the previous saved document. */
    void restore(String snapshot) throws Exception {
        if (snapshot != null) inspect(snapshot);
        write(snapshot);
    }

    private void write(String snapshot) throws IOException {
        synchronized (LOCK) {
            if (snapshot == null) { file.delete(); return; }
            byte[] bytes = snapshot.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IOException("课表数据过大");
            FileOutputStream stream = null;
            try {
                stream = file.startWrite();
                stream.write(bytes);
                file.finishWrite(stream);
            } catch (IOException exception) {
                if (stream != null) file.failWrite(stream);
                throw exception;
            }
        }
    }

    static String readIcs(InputStream input) throws IOException {
        return readUtf8(input, TimetableParser.MAX_BYTES);
    }

    private static String readUtf8(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > limit) throw new IOException("文件过大，请导出单个学期的 ICS（不超过 1 MB）");
            bytes.write(buffer, 0, count);
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            return value.startsWith("\uFEFF") ? value.substring(1) : value;
        } catch (CharacterCodingException exception) {
            throw new IOException("文件编码无法读取，请导出 UTF-8 格式的 ICS", exception);
        }
    }
}
