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
    public static final int TODO_ANY = 0, TODO_TODAY_DUE = 1, TODO_OVERDUE = 2,
            TODO_OPEN = 3;
    public final String selection;
    public final String[] arguments;
    public final int todoScope;
    private final boolean homeworkOnly;
    public static final String DAY_SQL = "CASE WHEN all_day=1 THEN "
            + "strftime('%Y-%m-%d',COALESCE(start_at_millis,due_at_millis)/1000,'unixepoch') ELSE "
            + "strftime('%Y-%m-%d',COALESCE(start_at_millis,due_at_millis)/1000,'unixepoch','localtime') END";

    public ScheduleQuery(int tab, String category, int publication, String keyword,
            LocalDate from, LocalDate until, long now, ZoneId zone) {
        this(tab, category, publication, keyword, from, until, now, zone, TODO_ANY);
    }

    public ScheduleQuery(int tab, String category, int publication, String keyword,
            LocalDate from, LocalDate until, long now, ZoneId zone, int todoScope) {
        this.todoScope = todoScope;
        this.homeworkOnly = CATEGORY_HOMEWORK.equals(category);
        List<String> clauses = new ArrayList<>(), values = new ArrayList<>();
        clauses.add("is_record=0");
        String anchor = "COALESCE(start_at_millis,due_at_millis)";
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
                clauses.add("due_at_millis>=CAST(? AS INTEGER) AND due_at_millis<CAST(? AS INTEGER)");
                values.add(Long.toString(today.atStartOfDay(zone).toInstant().toEpochMilli()));
                values.add(Long.toString(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()));
            } else if (todoScope == TODO_OVERDUE) {
                clauses.add("due_at_millis<CAST(? AS INTEGER)");
                values.add(Long.toString(now));
            }
        }
        if (publication == 1) clauses.add("(needs_confirmation=1 OR (category='event' AND calendar_event_id IS NULL))");
        else if (publication == 2) clauses.add("calendar_event_id IS NOT NULL");
        else if (publication == 3) clauses.add("calendar_event_id IS NULL");
        if (from != null && until != null) {
            clauses.add("((all_day=0 AND COALESCE(start_at_millis,due_at_millis)<CAST(? AS INTEGER) AND COALESCE(end_at_millis,due_at_millis+1,start_at_millis+1)>CAST(? AS INTEGER))"
                    + " OR (all_day=1 AND COALESCE(start_at_millis,due_at_millis)<CAST(? AS INTEGER) AND COALESCE(end_at_millis,due_at_millis+1,start_at_millis+1)>CAST(? AS INTEGER)))");
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
            return "due_at_millis IS NULL ASC, due_at_millis" + direction + ", id" + direction;
        if (todoScope == TODO_TODAY_DUE || todoScope == TODO_OVERDUE)
            return "due_at_millis" + direction + ", id" + direction;
        return "COALESCE(start_at_millis,due_at_millis) IS NULL ASC, " + DAY_SQL + direction
                + ", all_day DESC, COALESCE(start_at_millis,due_at_millis)" + direction + ", id" + direction;
    }
}
