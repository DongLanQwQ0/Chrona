package com.donglan.chrona;

import android.content.Context;
import android.util.AtomicFile;
import com.donglan.chrona.timetable.Timetable;
import com.donglan.chrona.timetable.TimetableParser;
import com.donglan.chrona.timetable.AcademicTerms;
import org.json.JSONArray;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The original ICS is kept privately and replaced atomically only after a successful import. */
final class TimetableStore {
    private static final int FORMAT = 1;
    private static final int MAX_SNAPSHOT_BYTES = TimetableParser.MAX_BYTES * 12;
    private static final int MAX_DOCUMENTS = 12;
    private static final Object LOCK = new Object();
    private final AtomicFile file;

    static final class Document {
        final String name, source;
        final long importedAt;
        final Timetable table;
        final AcademicTerms.Plan plan;
        final Set<Integer> enabled;
        final Map<String, Boolean> overrides;
        Document(String name, String source, long importedAt, Timetable table,
                AcademicTerms.Plan plan, Set<Integer> enabled, Map<String, Boolean> overrides) {
            this.name = name;
            this.source = source;
            this.importedAt = importedAt;
            this.table = table;
            this.plan = plan;
            this.enabled = Collections.unmodifiableSet(new HashSet<>(enabled));
            this.overrides = Collections.unmodifiableMap(new HashMap<>(overrides));
        }
        Document configured(AcademicTerms.Plan plan) {
            plan.validateCoverage(table);
            Set<Integer> active = new HashSet<>();
            for (int i = 0; i < plan.count(); i++) if (!plan.entries(table, i).isEmpty()) active.add(i);
            return new Document(name, source, importedAt, table, plan, active, Collections.emptyMap());
        }
        Document withState(Set<Integer> active, Map<String, Boolean> classified) {
            return new Document(name, source, importedAt, table, plan, active, classified);
        }
    }

    static final class Semester {
        final Document document;
        final int index;
        final AcademicTerms.View view;
        Semester(Document document, int index) {
            this.document = document; this.index = index;
            view = new AcademicTerms.View(document.plan.entries(document.table, index), document.table.zone, document.overrides);
        }
        String id() { return document.plan.id(index); }
    }

    static final class Library {
        final List<Document> documents;
        final String selected;
        final List<Semester> semesters;
        Library(List<Document> documents, String selected) {
            if (documents.size() > MAX_DOCUMENTS) throw new IllegalArgumentException("最多保留 12 份课表，请先删除不再需要的学期");
            this.documents = Collections.unmodifiableList(new ArrayList<>(documents));
            this.selected = selected;
            List<Semester> terms = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (Document document : documents) for (int index : document.enabled) {
                if (index < 0 || index >= document.plan.count() || !ids.add(document.plan.id(index)))
                    throw new IllegalArgumentException("课表包含重复或无效的学期");
                terms.add(new Semester(document, index));
            }
            terms.sort(java.util.Comparator.comparing(Semester::id).reversed());
            semesters = Collections.unmodifiableList(terms);
        }
        Semester current(LocalDate today) {
            for (Semester term : semesters) if (term.id().equals(selected)) return term;
            for (Semester term : semesters) if (!today.isBefore(term.document.plan.from(term.index))
                    && today.isBefore(term.document.plan.until(term.index))) return term;
            Semester nearest = null;
            long distance = Long.MAX_VALUE;
            for (Semester term : semesters) {
                AcademicTerms.Plan plan = term.document.plan;
                long days = today.isBefore(plan.from(term.index))
                        ? java.time.temporal.ChronoUnit.DAYS.between(today, plan.from(term.index))
                        : java.time.temporal.ChronoUnit.DAYS.between(plan.until(term.index).minusDays(1), today);
                if (days < distance) { distance = days; nearest = term; }
            }
            return nearest;
        }
        private Library(Library source, String selected) {
            documents = source.documents; semesters = source.semesters; this.selected = selected;
        }
        Library select(String id) { return new Library(this, id); }
        Library merge(Document incoming) {
            Set<String> replaced = new HashSet<>();
            for (int index : incoming.enabled) replaced.add(incoming.plan.id(index));
            List<Document> merged = new ArrayList<>();
            merged.add(incoming);
            for (Document existing : documents) {
                Set<Integer> retained = new HashSet<>(existing.enabled);
                retained.removeIf(index -> replaced.contains(existing.plan.id(index)));
                if (!retained.isEmpty()) merged.add(existing.withState(retained, existing.overrides));
            }
            return new Library(merged, selected);
        }
        Library remove(Semester removed) {
            List<Document> remaining = new ArrayList<>();
            for (Document document : documents) {
                Set<Integer> retained = new HashSet<>(document.enabled);
                if (document == removed.document) retained.remove(removed.index);
                if (!retained.isEmpty()) remaining.add(document.withState(retained, document.overrides));
            }
            return new Library(remaining, selected.equals(removed.id()) ? "" : selected);
        }
        Library classify(Semester term, List<Timetable.Occurrence> entries, Boolean regular) {
            List<Document> changed = new ArrayList<>();
            for (Document document : documents) {
                if (document != term.document) { changed.add(document); continue; }
                Map<String, Boolean> overrides = new HashMap<>(document.overrides);
                for (Timetable.Occurrence entry : entries) {
                    if (regular == null) overrides.remove(entry.key()); else overrides.put(entry.key(), regular);
                }
                changed.add(document.withState(document.enabled, overrides));
            }
            return new Library(changed, selected);
        }
    }

    TimetableStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "course-timetable.json"));
    }

    static Document prepare(String name, String source, ZoneId zone) throws IOException {
        Timetable table = TimetableParser.parse(source, zone);
        String label = name == null || name.trim().isEmpty() ? "导入的课表" : name.trim();
        if (label.length() > 160) label = label.substring(0, 160);
        AcademicTerms.Plan plan = AcademicTerms.suggest(table, source);
        return new Document(label, source, System.currentTimeMillis(), table, plan,
                Collections.emptySet(), Collections.emptyMap()).configured(plan);
    }

    Library load() throws Exception {
        String snapshot = snapshot();
        return snapshot == null ? null : inspect(snapshot);
    }

    String snapshot() throws IOException {
        synchronized (LOCK) {
            if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
            try (InputStream input = file.openRead()) { return readUtf8(input, MAX_SNAPSHOT_BYTES); }
        }
    }

    static Library inspect(String snapshot) throws Exception {
        if (snapshot == null) return null;
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES)
            throw new IOException("课表备份过大");
        JSONObject json = new JSONObject(snapshot);
        if (json.getInt("format") != FORMAT) throw new IOException("课表数据版本不受支持");
        List<Document> documents = new ArrayList<>();
        documents.add(readDocument(json));
        JSONArray archive = json.optJSONArray("archive");
        if (archive != null) {
            if (archive.length() >= MAX_DOCUMENTS) throw new IOException("保存的课表数量过多");
            for (int i = 0; i < archive.length(); i++) documents.add(readDocument(archive.getJSONObject(i)));
        }
        return new Library(documents, json.optString("selected", ""));
    }

    private static Document readDocument(JSONObject json) throws Exception {
        String source = json.getString("ics");
        Timetable table = TimetableParser.parse(source, ZoneId.of(json.getString("zone")));
        JSONObject savedPlan = json.optJSONObject("plan");
        AcademicTerms.Plan plan = savedPlan == null ? AcademicTerms.suggest(table, source)
                : new AcademicTerms.Plan(savedPlan.getInt("year"), savedPlan.getInt("mode"),
                        LocalDate.parse(savedPlan.getString("start")), LocalDate.parse(savedPlan.getString("split")),
                        LocalDate.parse(savedPlan.getString("end")));
        plan.validateCoverage(table);
        Set<Integer> enabled = new HashSet<>();
        JSONArray active = json.optJSONArray("enabled");
        if (active == null) {
            for (int i = 0; i < plan.count(); i++) if (!plan.entries(table, i).isEmpty()) enabled.add(i);
        } else for (int i = 0; i < active.length(); i++) enabled.add(active.getInt(i));
        Map<String, Boolean> overrides = new HashMap<>();
        JSONObject choices = json.optJSONObject("classification");
        if (choices != null) {
            java.util.Iterator<String> keys = choices.keys();
            while (keys.hasNext()) { String key = keys.next(); overrides.put(key, choices.getBoolean(key)); }
        }
        return new Document(json.getString("name"), source, json.getLong("importedAt"), table, plan, enabled, overrides);
    }

    private static JSONObject encodeDocument(Document document) throws Exception {
        JSONObject json = new JSONObject();
        json.put("format", FORMAT);
        json.put("name", document.name);
        json.put("ics", document.source);
        json.put("importedAt", document.importedAt);
        json.put("zone", document.table.zone.getId());
        JSONObject plan = new JSONObject();
        plan.put("year", document.plan.year); plan.put("mode", document.plan.mode);
        plan.put("start", document.plan.start.toString()); plan.put("split", document.plan.split.toString());
        plan.put("end", document.plan.end.toString());
        json.put("plan", plan);
        JSONArray enabled = new JSONArray();
        for (int index : document.enabled) enabled.put(index);
        json.put("enabled", enabled);
        json.put("classification", new JSONObject(document.overrides));
        return json;
    }

    static String encode(Library library) throws Exception {
        if (library.documents.isEmpty()) return null;
        JSONObject json = encodeDocument(library.documents.get(0));
        JSONArray archive = new JSONArray();
        for (int i = 1; i < library.documents.size(); i++) archive.put(encodeDocument(library.documents.get(i)));
        json.put("archive", archive); json.put("selected", library.selected);
        return json.toString();
    }

    void save(Library library) throws Exception {
        write(encode(library));
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
