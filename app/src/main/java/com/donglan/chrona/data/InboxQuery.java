package com.donglan.chrona.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared inbox predicates for the lightweight ID scan and current-page records. */
public final class InboxQuery {
    public static final int PAGE_SIZE = 12;
    public final String selection;
    public final String[] arguments;
    public final String keyword;

    public InboxQuery(int status, String category, String search) {
        List<String> clauses = new ArrayList<>(), values = new ArrayList<>();
        if (status == 1) {
            clauses.add("status=?"); values.add(TaskRecord.NEEDS_REVIEW);
        } else if (status == 2) {
            clauses.add("status IN (?,?)"); values.add(TaskRecord.QUEUED); values.add(TaskRecord.PROCESSING);
        } else if (status == 3) {
            clauses.add("status=?"); values.add(TaskRecord.FAILED);
        }
        if (category != null || status == 4) {
            String candidate = "EXISTS (SELECT 1 FROM event_candidates c WHERE c.task_id=tasks.id";
            if (category != null) {
                candidate += " AND c.category=?"; values.add(EventCategory.normalize(category));
            }
            if (status == 4) candidate += " AND c.calendar_event_id IS NOT NULL";
            clauses.add(candidate + ")");
        }
        selection = clauses.isEmpty() ? "1" : String.join(" AND ", clauses);
        arguments = values.toArray(new String[0]);
        keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    public boolean matchesSearch(String rawText, String linkText) {
        // Keep Java Unicode case handling and literal %, _ and backslashes; SQLite LOWER/LIKE differ.
        return keyword.isEmpty() || contains(rawText) || contains(linkText);
    }

    private boolean contains(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public String orderBy(boolean oldestFirst) {
        String direction = oldestFirst ? " ASC" : " DESC";
        return "created_at_millis" + direction + ", id" + direction;
    }
}
