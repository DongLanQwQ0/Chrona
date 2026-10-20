package com.donglan.chrona.data;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parameterized schedule predicates, shared by page, count, and selection queries. */
public final class ScheduleQuery {
    public static final int PAGE_SIZE = 24;
    public static final String CATEGORY_HOMEWORK = "homework";
    public static final String TASK_DUE_SQL = "COALESCE(due_at_millis,end_at_millis,start_at_millis)";
    public static final String DEFAULT_EXECUTION_SQL = "(is_record=0 AND category='task' AND start_at_millis IS NULL AND end_at_millis IS NULL AND due_at_millis IS NOT NULL)";
    public static final String EXECUTION_START_SQL = "(CASE WHEN " + DEFAULT_EXECUTION_SQL
            + " THEN due_at_millis-" + EventCandidate.DEFAULT_EXECUTION_DURATION_MILLIS + " ELSE COALESCE(start_at_millis,due_at_millis) END)";
    public static final String EXECUTION_END_SQL = "(CASE WHEN " + DEFAULT_EXECUTION_SQL
            + " THEN due_at_millis ELSE end_at_millis END)";
    public static final String EXECUTION_ALL_DAY_SQL = "(all_day=1 AND NOT " + DEFAULT_EXECUTION_SQL + ")";
    public static final String CALENDAR_END_SQL = "(CASE WHEN " + EXECUTION_END_SQL + ">" + EXECUTION_START_SQL
            + " THEN " + EXECUTION_END_SQL + " ELSE " + EXECUTION_START_SQL + "+60000 END)";
    public static final int TODO_ANY = 0, TODO_TODAY_DUE = 1, TODO_OVERDUE = 2,
            TODO_OPEN = 3;
    public final String selection;
    public final String[] arguments;
    public final int todoScope;
    private final boolean homeworkOnly;
    private final int tab, publication;
    private final String category, keyword;
    private final LocalDate from, until;
    private final long now;
    private final ZoneId zone;
    public static final String DAY_SQL = "CASE WHEN " + EXECUTION_ALL_DAY_SQL + " THEN "
            + "strftime('%Y-%m-%d'," + EXECUTION_START_SQL + "/1000,'unixepoch') ELSE "
            + "strftime('%Y-%m-%d'," + EXECUTION_START_SQL + "/1000,'unixepoch','localtime') END";

    public ScheduleQuery(int tab, String category, int publication, String keyword,
            LocalDate from, LocalDate until, long now, ZoneId zone) {
        this(tab, category, publication, keyword, from, until, now, zone, TODO_ANY);
    }

    public ScheduleQuery(int tab, String category, int publication, String keyword,
            LocalDate from, LocalDate until, long now, ZoneId zone, int todoScope) {
        this.todoScope = todoScope;
        this.tab = tab; this.category = category; this.publication = publication;
        this.keyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        this.from = from; this.until = until; this.now = now; this.zone = zone;
        this.homeworkOnly = CATEGORY_HOMEWORK.equals(category);
        List<String> clauses = new ArrayList<>(), values = new ArrayList<>();
        clauses.add("is_record=0");
        String anchor = EXECUTION_START_SQL;
        String finish = "COALESCE(end_at_millis,due_at_millis,start_at_millis)";
        String dated = "((category IN ('task','homework') AND " + anchor + " IS NOT NULL) OR (category='event' AND start_at_millis IS NOT NULL AND end_at_millis IS NOT NULL))";
        String futureTime = "((all_day=0 AND " + finish + ">CAST(? AS INTEGER)) OR (all_day=1 AND " + finish + ">CAST(? AS INTEGER)))";
        if (tab == 1) { clauses.add("NOT " + dated); clauses.add("completed_at_millis IS NULL"); }
        else if (tab == 0 || tab == 2) {
            if (tab == 0) { clauses.add(dated); clauses.add("completed_at_millis IS NULL AND (category IN ('task','homework') OR " + futureTime + ")"); }
            else clauses.add("(completed_at_millis IS NOT NULL OR (category='event' AND " + dated + " AND NOT " + futureTime + "))");
            values.add(Long.toString(now));
            values.add(Long.toString(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        if (CATEGORY_HOMEWORK.equals(category)) {
            clauses.add("category='homework'");
        } else if (category != null) { clauses.add("category=?"); values.add(category); }
        if (todoScope != TODO_ANY) {
            clauses.add("category IN ('task','homework') AND completed_at_millis IS NULL");
            if (todoScope == TODO_TODAY_DUE) {
                LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
                clauses.add(TASK_DUE_SQL + ">=CAST(? AS INTEGER) AND " + TASK_DUE_SQL + "<CAST(? AS INTEGER)");
                values.add(Long.toString(today.atStartOfDay(zone).toInstant().toEpochMilli()));
                values.add(Long.toString(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()));
            } else if (todoScope == TODO_OVERDUE) {
                clauses.add(TASK_DUE_SQL + "<CAST(? AS INTEGER)");
                values.add(Long.toString(now));
            }
        }
        if (publication == 1) clauses.add("(needs_confirmation=1 OR (category='event' AND calendar_event_id IS NULL))");
        else if (publication == 2) clauses.add("calendar_event_id IS NOT NULL");
        else if (publication == 3) clauses.add("calendar_event_id IS NULL");
        if (from != null && until != null) {
            String overlap = anchor + "<CAST(? AS INTEGER) AND COALESCE(" + EXECUTION_END_SQL
                    + ",due_at_millis+1,start_at_millis+1)>CAST(? AS INTEGER)";
            clauses.add("((NOT " + EXECUTION_ALL_DAY_SQL + " AND " + overlap + ") OR ("
                    + EXECUTION_ALL_DAY_SQL + " AND " + overlap + "))");
            values.add(Long.toString(until.atStartOfDay(zone).toInstant().toEpochMilli()));
            values.add(Long.toString(from.atStartOfDay(zone).toInstant().toEpochMilli()));
            values.add(Long.toString(until.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
            values.add(Long.toString(from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            StringBuilder search = new StringBuilder("(LOWER(title) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(location,'')) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(description,'')) LIKE ? ESCAPE '\\'");
            values.add(like); values.add(like); values.add(like);
            for (int i = 0; i < EventCategory.VALUES.length; i++) {
                if (EventCategory.LABELS[i].contains(query)) {
                    search.append(" OR category=?"); values.add(EventCategory.VALUES[i]);
                }
            }
            clauses.add(search.append(')').toString());
        }
        selection = clauses.isEmpty() ? "1=1" : String.join(" AND ", clauses);
        arguments = values.toArray(new String[0]);
    }

    public String orderBy(boolean oldestFirst) {
        String direction = oldestFirst ? " ASC" : " DESC";
        if (homeworkOnly)
            return TASK_DUE_SQL + " IS NULL ASC, " + TASK_DUE_SQL + direction + ", id" + direction;
        if (todoScope == TODO_TODAY_DUE || todoScope == TODO_OVERDUE)
            return TASK_DUE_SQL + direction + ", id" + direction;
        return EXECUTION_START_SQL + " IS NULL ASC, " + DAY_SQL + direction
                + ", " + EXECUTION_ALL_DAY_SQL + " DESC, " + EXECUTION_START_SQL + direction + ", id" + direction;
    }

    /** Lists retain one selectable row per series; its occurrence opens the complete series history. */
    public EventCandidate seriesRow(EventCandidate series) {
        if (tab == 1 || category != null && !category.equals(series.category)) return null;
        if (publication == 1 && !(series.needsConfirmation || !series.isTask() && series.calendarEventId == null)
                || publication == 2 && series.calendarEventId == null || publication == 3 && series.calendarEventId != null) return null;
        String searchable = (series.title + " " + series.location + " " + series.description + " "
                + EventCategory.label(series.category)).toLowerCase(Locale.ROOT);
        if (!searchable.contains(keyword)) return null;
        ZoneId boundsZone = series.executionAllDay() ? ZoneOffset.UTC : zone;
        long current = series.executionAllDay() ? java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() : now;
        long begin = from == null ? executionBegin(series) : from.atStartOfDay(boundsZone).toInstant().toEpochMilli();
        long end = until == null ? 253_402_300_799_999L : until.atStartOfDay(boundsZone).toInstant().toEpochMilli();
        if (tab == 0 && !series.isTask()) begin = Math.max(begin, current - Math.max(60000, series.calendarEnd() - series.anchor()));
        if (tab == 2 && !series.isTask()) end = Math.min(end, current);
        if (todoScope != TODO_ANY && !series.isTask()) return null;
        if (todoScope == TODO_TODAY_DUE) {
            LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            long dueOffset = series.effectiveDueAtMillis() - series.anchor();
            long lead = series.hasDefaultExecutionInterval() ? EventCandidate.DEFAULT_EXECUTION_DURATION_MILLIS : 0;
            begin = Math.max(begin, today.atStartOfDay(zone).toInstant().toEpochMilli() - dueOffset - lead);
            end = Math.min(end, today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - dueOffset);
        } else if (todoScope == TODO_OVERDUE) end = Math.min(end, now - (series.effectiveDueAtMillis() - series.anchor()));
        if (tab == 2 && series.isTask()) {
            org.json.JSONObject changes = RepeatOccurrences.exceptions(series);
            java.util.Iterator<String> keys = changes.keys();
            List<EventCandidate> completed = new ArrayList<>();
            while (keys.hasNext()) {
                String key = keys.next();
                org.json.JSONObject change = changes.optJSONObject(key);
                if (change == null || change.isNull("completed")) continue;
                EventCandidate occurrence = RepeatOccurrences.at(series, Long.parseLong(key));
                if (occurrence != null && executionBegin(occurrence) < end
                        && (occurrence.hasDefaultExecutionInterval() ? occurrence.executionEndAtMillis() : occurrence.calendarEnd()) > begin)
                    completed.add(occurrence);
            }
            completed.sort(java.util.Comparator.comparingLong(e -> e.anchor()));
            return completed.isEmpty() || todoScope != TODO_ANY ? null : completed.get(0);
        }
        int changed = RepeatOccurrences.exceptions(series).length();
        for (EventCandidate occurrence : RepeatOccurrences.between(series, begin, end, changed + 2)) {
            if (tab == 0 && occurrence.completed() || todoScope != TODO_ANY && occurrence.completed()) continue;
            if (todoScope == TODO_OVERDUE && occurrence.effectiveDueAtMillis() >= now) continue;
            if (todoScope == TODO_TODAY_DUE && !java.time.Instant.ofEpochMilli(occurrence.effectiveDueAtMillis())
                    .atZone(zone).toLocalDate().equals(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate())) continue;
            if (tab == 2 && series.isTask() && !occurrence.completed()) continue;
            if (tab == 2 && !series.isTask() && occurrence.calendarEnd() > current) continue;
            if (tab == 0 && !series.isTask() && occurrence.calendarEnd() <= current) continue;
            return occurrence;
        }
        return null;
    }

    public java.util.Comparator<EventCandidate> comparator(boolean oldestFirst) {
        java.util.Comparator<Long> times = oldestFirst ? java.util.Comparator.naturalOrder() : java.util.Comparator.reverseOrder();
        java.util.Comparator<LocalDate> dates = oldestFirst ? java.util.Comparator.naturalOrder() : java.util.Comparator.reverseOrder();
        java.util.Comparator<EventCandidate> result;
        if (homeworkOnly || todoScope == TODO_TODAY_DUE || todoScope == TODO_OVERDUE)
            result = java.util.Comparator.comparing(EventCandidate::effectiveDueAtMillis,
                    java.util.Comparator.nullsLast(times));
        else result = java.util.Comparator.comparing((EventCandidate e) -> executionBegin(e) == null ? null
                    : java.time.Instant.ofEpochMilli(executionBegin(e)).atZone(e.executionAllDay() ? ZoneOffset.UTC : zone).toLocalDate(),
                    java.util.Comparator.nullsLast(dates))
                .thenComparing(e -> !e.executionAllDay())
                .thenComparing(ScheduleQuery::executionBegin, java.util.Comparator.nullsLast(times));
        return result.thenComparing(e -> e.id, times);
    }
    static Long executionBegin(EventCandidate event) {
        return event.hasDefaultExecutionInterval() ? event.executionStartAtMillis() : event.anchor();
    }
}
