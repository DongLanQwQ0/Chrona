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
    public final String selection;
    public final String[] arguments;
    public static final String DAY_SQL = "CASE WHEN all_day=1 THEN "
            + "strftime('%Y-%m-%d',start_at_millis/1000,'unixepoch') ELSE "
            + "strftime('%Y-%m-%d',start_at_millis/1000,'unixepoch','localtime') END";

    public ScheduleQuery(int tab, String category, int publication, String keyword,
            LocalDate from, LocalDate until, long now, ZoneId zone) {
        List<String> clauses = new ArrayList<>(), values = new ArrayList<>();
        String complete = "(start_at_millis IS NOT NULL AND end_at_millis IS NOT NULL)";
        String future = "((all_day=0 AND end_at_millis>?) OR (all_day=1 AND end_at_millis>?))";
        if (tab == 1) clauses.add("NOT " + complete);
        else if (tab == 0 || tab == 2) {
            clauses.add(complete);
            clauses.add(tab == 0 ? future : "NOT " + future);
            values.add(Long.toString(now));
            values.add(Long.toString(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        if (category != null) { clauses.add("category=?"); values.add(category); }
        if (publication == 1) clauses.add("calendar_event_id IS NULL");
        else if (publication == 2) clauses.add("calendar_event_id IS NOT NULL");
        if (from != null && until != null) {
            clauses.add("((all_day=0 AND start_at_millis<? AND COALESCE(end_at_millis,start_at_millis+1)>CAST(? AS INTEGER))"
                    + " OR (all_day=1 AND start_at_millis<? AND COALESCE(end_at_millis,start_at_millis+1)>CAST(? AS INTEGER)))");
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
        return "start_at_millis IS NULL ASC, " + DAY_SQL + direction
                + ", all_day DESC, start_at_millis" + direction + ", id" + direction;
    }
}
