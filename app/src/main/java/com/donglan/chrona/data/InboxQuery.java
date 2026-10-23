package com.donglan.chrona.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared inbox predicates for the lightweight ID scan and current-page records. */
public final class InboxQuery {
    public static final int PAGE_SIZE = 12;
    public final String selection;
    public final String[] arguments;
    public final String keyword;
    private final String[] terms;

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
                candidate += " AND c.is_record=0 AND c.category=?"; values.add(EventCategory.normalize(category));
            }
            if (status == 4) candidate += " AND c.is_record=0 AND c.calendar_event_id IS NOT NULL";
            clauses.add(candidate + ")");
        }
        selection = clauses.isEmpty() ? "1" : String.join(" AND ", clauses);
        arguments = values.toArray(new String[0]);
        keyword = normalized(search).trim();
        terms = keyword.isEmpty() ? new String[0] : keyword.split(" ");
    }

    public boolean matchesSearch(String rawText, String linkText) {
        return matchesSearch(rawText, linkText, null, null);
    }

    public boolean matchesSearch(String rawText, String linkText,
            String candidateTitles, String candidateDescriptions) {
        // Each literal term may occur in any field; every term must be present in this capture.
        if (terms.length == 0) return true;
        String[] fields = {normalized(rawText), normalized(linkText),
                normalized(candidateTitles), normalized(candidateDescriptions)};
        for (String term : terms) {
            boolean found = false;
            for (String field : fields) if (field.contains(term)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    private static String normalized(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{javaWhitespace}\\p{Z}]+", " ");
    }

    public String orderBy(boolean oldestFirst) {
        String direction = oldestFirst ? " ASC" : " DESC";
        return "created_at_millis" + direction + ", id" + direction;
    }
}
