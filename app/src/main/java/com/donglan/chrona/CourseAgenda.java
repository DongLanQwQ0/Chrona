package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import com.donglan.chrona.timetable.Timetable;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only dated courses shared by the home timeline and widgets. */
final class CourseAgenda {
    static final String EXTRA_TERM = "course_term";
    static final String EXTRA_OCCURRENCE = "course_occurrence";

    static final class Item {
        final String key, termId, title, location;
        final long start, end;
        final boolean allDay;

        Item(String termId, Timetable.Occurrence occurrence, ZoneId sourceZone, ZoneId displayZone) {
            key = occurrence.key();
            this.termId = termId;
            title = occurrence.title;
            location = occurrence.location;
            allDay = occurrence.allDay;
            start = (allDay ? occurrence.start.toLocalDate().atStartOfDay(displayZone)
                    : occurrence.start.atZone(sourceZone)).toInstant().toEpochMilli();
            end = (allDay ? occurrence.end.toLocalDate().atStartOfDay(displayZone)
                    : occurrence.end.atZone(sourceZone)).toInstant().toEpochMilli();
        }

        private String identity() {
            return title.length() + ":" + title + location.length() + ":" + location
                    + "/" + start + "/" + end + "/" + allDay;
        }
    }

    static long revision() { return TimetableStore.revision(); }

    static List<Item> load(Context context, long begin, long end, ZoneId displayZone) throws Exception {
        if (end <= begin) return java.util.Collections.emptyList();
        Map<String, Item> unique = new LinkedHashMap<>();
        TimetableStore.Library library = new TimetableStore(context).load();
        if (library != null) for (TimetableStore.Semester term : library.semesters) {
            for (Timetable.Occurrence occurrence : term.view.all) {
                Item item = new Item(term.id(), occurrence, term.document.table.zone, displayZone);
                if (item.start < end && item.end > begin) unique.putIfAbsent(item.identity(), item);
            }
        }
        List<Item> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong((Item item) -> item.start).thenComparing(item -> item.key));
        return result;
    }

    static Intent intent(Context context, Item item) {
        return new Intent(context, TimetableActivity.class)
                .putExtra(EXTRA_TERM, item.termId).putExtra(EXTRA_OCCURRENCE, item.key);
    }
}
