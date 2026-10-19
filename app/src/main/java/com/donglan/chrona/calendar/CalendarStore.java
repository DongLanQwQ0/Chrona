package com.donglan.chrona.calendar;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import com.donglan.chrona.ScheduleSources;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatRule;
import biweekly.component.VEvent;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.Date;
import java.text.Normalizer;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Direct access to Chrona's device-local system calendar. Call from a background thread. */
public final class CalendarStore {
    public static final String CALENDAR_NAME = "拾时 · Chrona";

    private static final int CALENDAR_COLOR = 0xFF486A85;
    private static final int MAX_REMINDERS = 5;
    private static final int MAX_HOME_INSTANCES = 1000;
    private static final int MAX_REMINDER_FUTURE_DAYS = 4;
    private static final String[] CALENDAR_ID_PROJECTION = {Calendars._ID};
    private static final String[] EVENT_PROJECTION = {
            Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND,
            Events.EVENT_TIMEZONE, Events.ALL_DAY, Events.DESCRIPTION, Events.EVENT_LOCATION,
            Events.RRULE, Events.DURATION, Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME,
            Events.ORIGINAL_ALL_DAY, Events.STATUS, Events._SYNC_ID, Events.ORIGINAL_SYNC_ID,
            Events.SYNC_DATA1, Events.SYNC_DATA2, Events.SYNC_DATA3
    };
    private static final String[] REMINDER_PROJECTION = {Reminders.MINUTES};

    private final Context context;
    private final ContentResolver resolver;
    private final String accountName;

    public CalendarStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.accountName = this.context.getPackageName() + ".calendar";
    }

    public boolean hasReadPermission() {
        return context.checkPermission(Manifest.permission.READ_CALENDAR,
                Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasWritePermission() {
        return context.checkPermission(Manifest.permission.WRITE_CALENDAR,
                Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Expands visible calendars' recurrences in a bounded window. No reminder queries or writes. */
    public List<CalendarOccurrence> listInstances(long begin, long end,
            android.os.CancellationSignal cancellation) {
        return listInstances(begin, end, cancellation, MAX_HOME_INSTANCES);
    }

    public List<CalendarOccurrence> listInstances(long begin, long end,
            android.os.CancellationSignal cancellation, int limit) {
        return listInstances(begin, end, cancellation, limit, false);
    }

    /** Export duplicate detection must include hidden/disabled calendar sources too. */
    public List<CalendarOccurrence> listAllInstances(long begin, long end) {
        return listInstances(begin, end, null, Integer.MAX_VALUE, true);
    }

    private List<CalendarOccurrence> listInstances(long begin, long end,
            android.os.CancellationSignal cancellation, int limit, boolean allSources) {
        requireRead();
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, begin);
        ContentUris.appendId(uri, end);
        String[] projection = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, Events.TITLE, Events.ALL_DAY,
                Events.EVENT_LOCATION, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR,
                Events.CALENDAR_ID, Calendars.ACCOUNT_NAME, Events.DESCRIPTION};
        List<CalendarOccurrence> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(uri.build(), projection, (allSources ? "(" : Calendars.VISIBLE + "=1 AND (")
                + Events.STATUS + " IS NULL OR " + Events.STATUS + "!=?)",
                new String[]{Integer.toString(Events.STATUS_CANCELED)},
                CalendarContract.Instances.BEGIN + " ASC", cancellation)) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext() && result.size() < limit) {
                if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2)) continue;
                if (!allSources && !ScheduleSources.allowsCalendar(context, cursor.getLong(8))) continue;
                long start = cursor.getLong(1), finish = cursor.getLong(2);
                if (finish <= start) continue;
                result.add(new CalendarOccurrence(cursor.getLong(0), start, finish,
                        cursor.getString(3), cursor.getInt(4) != 0, cursor.getString(5), cursor.getString(6),
                        cursor.isNull(7) ? null : cursor.getInt(7), cursor.getLong(8), cursor.getString(9),
                        cursor.getString(10)));
            }
        }
        return result;
    }

    /** Keeps complete local days so course segments can merge before reminder filtering. */
    public List<CalendarOccurrence> reminderInstances(long now, long horizon,
            java.util.Set<Long> linked) {
        requireRead();
        ZoneId zone = ZoneId.systemDefault();
        long dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, dayStart); ContentUris.appendId(uri, horizon);
        String[] projection = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, Events.TITLE, Events.ALL_DAY,
                Events.EVENT_LOCATION, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR,
                Events.CALENDAR_ID, Calendars.ACCOUNT_NAME, Events.DESCRIPTION};
        List<CalendarOccurrence> result = new ArrayList<>();
        LocalDate lastFutureDay = null;
        int futureDays = 0;
        try (Cursor cursor = resolver.query(uri.build(), projection,
                Calendars.VISIBLE + "=1 AND " + Events.ALL_DAY + "=0 AND (" + Events.STATUS
                        + " IS NULL OR " + Events.STATUS + "!=?)",
                new String[]{Integer.toString(Events.STATUS_CANCELED)}, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext()) {
                if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2)) continue;
                if (!ScheduleSources.allowsCalendar(context, cursor.getLong(8))) continue;
                long id = cursor.getLong(0), start = cursor.getLong(1), end = cursor.getLong(2);
                if (linked.contains(id) || cursor.getInt(4) != 0 || end <= start) continue;
                if (start > now) {
                    LocalDate day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
                    if (!day.equals(lastFutureDay)) {
                        if (futureDays == MAX_REMINDER_FUTURE_DAYS) break;
                        lastFutureDay = day;
                        futureDays++;
                    }
                }
                CalendarOccurrence entry = new CalendarOccurrence(id, start, end, cursor.getString(3), false,
                        cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getInt(7),
                        cursor.getLong(8), cursor.getString(9), cursor.getString(10));
                result.add(entry);
            }
        }
        return result;
    }

    /** Returns null when the calendar was removed from the provider. */
    public Long findCalendarId() {
        requireRead();
        try (Cursor cursor = resolver.query(Calendars.CONTENT_URI, CALENDAR_ID_PROJECTION,
                Calendars.ACCOUNT_NAME + "=? AND " + Calendars.ACCOUNT_TYPE + "=? AND "
                        + Calendars.NAME + "=?",
                new String[]{accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME},
                Calendars._ID + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    /** Finds or creates the local calendar. Caller should persist the returned ID only as a hint. */
    public long ensureCalendar() {
        requireReadWrite();
        Long existing = findCalendarId();
        if (existing != null) return existing;

        ContentValues values = new ContentValues();
        values.put(Calendars.ACCOUNT_NAME, accountName);
        values.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        values.put(Calendars.OWNER_ACCOUNT, accountName);
        values.put(Calendars.NAME, CALENDAR_NAME);
        values.put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME);
        values.put(Calendars.CALENDAR_COLOR, CALENDAR_COLOR);
        values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
        values.put(Calendars.VISIBLE, 1);
        values.put(Calendars.SYNC_EVENTS, 1);
        values.put(Calendars.MAX_REMINDERS, MAX_REMINDERS);
        values.put(Calendars.ALLOWED_REMINDERS, Integer.toString(Reminders.METHOD_ALERT));
        values.put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().getID());

        Uri inserted = resolver.insert(syncUri(Calendars.CONTENT_URI), values);
        if (inserted == null) {
            // A second writer may have created the same calendar in the meantime.
            Long raced = findCalendarId();
            if (raced != null) return raced;
            throw new IllegalStateException("Calendar provider did not create the calendar");
        }
        return ContentUris.parseId(inserted);
    }

    /** Creates the master, exceptions and alert reminders atomically. */
    public long insertEvent(EventInput input) {
        requireReadWrite();
        if (input == null) throw new IllegalArgumentException("input is required");
        long calendarId = ensureCalendar();
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(ContentProviderOperation.newInsert(syncUri(Events.CONTENT_URI))
                .withValues(eventValues(calendarId, input)).build());
        for (int minutes : input.reminderMinutes) {
            operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                    .withValueBackReference(Reminders.EVENT_ID, 0)
                    .withValue(Reminders.MINUTES, minutes)
                    .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                    .build());
        }
        appendExceptions(operations, calendarId, input, null);
        ContentProviderResult[] results = apply(operations);
        if (results[0].uri == null) throw new IllegalStateException("Event insert returned no URI");
        return ContentUris.parseId(results[0].uri);
    }

    /** Finds likely duplicates in Chrona's own calendar before asking the user to publish. */
    public List<Long> findMatchingEvents(EventInput input) {
        requireRead();
        if (input == null) throw new IllegalArgumentException("input is required");
        Long calendarId = findCalendarId();
        if (calendarId == null) return Collections.emptyList();
        ArrayList<Long> matches = new ArrayList<>();
        String[] projection = {Events._ID, Events.TITLE, Events.SYNC_DATA1, Events.SYNC_DATA2, Events.SYNC_DATA3};
        String selection = Events.CALENDAR_ID + "=? AND " + Events.DTSTART + "=? AND "
                + Events.ALL_DAY + "=? AND " + Events.ORIGINAL_ID + " IS NULL";
        String[] args = {Long.toString(calendarId), Long.toString(input.startMillis),
                input.allDay ? "1" : "0"};
        selection += input.rrule == null ? " AND " + Events.DTEND + "=? AND (" + Events.RRULE
                + " IS NULL OR " + Events.RRULE + "='')" : " AND " + Events.RRULE + "=? AND "
                + Events.DURATION + "=? AND " + Events.EVENT_TIMEZONE + "=?";
        ArrayList<String> arguments = new ArrayList<>(java.util.Arrays.asList(args));
        if (input.rrule == null) arguments.add(Long.toString(input.endMillis));
        else { arguments.add(input.rrule); arguments.add(duration(input)); arguments.add(input.timeZoneId); }
        String title = normalizeTitle(input.title);
        try (Cursor cursor = resolver.query(Events.CONTENT_URI, projection, selection, arguments.toArray(new String[0]),
                Events._ID + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext()) {
                if (input.rrule != null && (!java.util.Objects.equals(input.repeatRuleJson, cursor.getString(2))
                        || !java.util.Objects.equals(input.repeatExceptions, cursor.getString(3))
                        || !java.util.Objects.equals(input.repeatStoppedAtMillis, nullableLong(cursor, 4)))) continue;
                if (title.equals(normalizeTitle(cursor.getString(1)))) matches.add(cursor.getLong(0));
            }
        }
        return matches;
    }

    private static String normalizeTitle(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Returns false if the event ID is stale or belongs to another calendar. */
    public boolean updateDescription(long eventId, String description) {
        requireReadWrite();
        requireId(eventId);
        Long calendarId = findCalendarId();
        if (calendarId == null || getEvent(calendarId, eventId) == null) return false;
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(ContentProviderOperation.newUpdate(syncUri(Events.CONTENT_URI))
                .withSelection(Events._ID + "=? AND " + Events.CALENDAR_ID + "=?",
                        new String[]{Long.toString(eventId), Long.toString(calendarId)})
                .withValue(Events.DESCRIPTION, description).withExpectedCount(1).build());
        try { apply(operations); }
        catch (IllegalStateException exception) {
            if (getEvent(calendarId, eventId) == null) return false;
            throw exception;
        }
        return true;
    }

    /** Returns false if the event ID is stale or belongs to another calendar. */
    public boolean updateEvent(long eventId, EventInput input) {
        requireReadWrite();
        requireId(eventId);
        if (input == null) throw new IllegalArgumentException("input is required");
        Long calendarId = findCalendarId();
        if (calendarId == null || getEvent(calendarId, eventId) == null) return false;

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(ContentProviderOperation.newUpdate(syncUri(Events.CONTENT_URI))
                .withSelection(Events._ID + "=? AND " + Events.CALENDAR_ID + "=?",
                        new String[]{Long.toString(eventId), Long.toString(calendarId)})
                .withValues(eventValues(calendarId, input))
                .withExpectedCount(1).build());
        operations.add(ContentProviderOperation.newDelete(syncUri(Reminders.CONTENT_URI))
                .withSelection(Reminders.EVENT_ID + "=?", new String[]{Long.toString(eventId)})
                .build());
        operations.add(ContentProviderOperation.newDelete(syncUri(Events.CONTENT_URI))
                .withSelection(Events.ORIGINAL_ID + "=? AND " + Events.CALENDAR_ID + "=?",
                        new String[]{Long.toString(eventId), Long.toString(calendarId)}).build());
        for (int minutes : input.reminderMinutes) {
            operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                    .withValue(Reminders.EVENT_ID, eventId)
                    .withValue(Reminders.MINUTES, minutes)
                    .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                    .build());
        }
        appendExceptions(operations, calendarId, input, eventId);
        try {
            apply(operations);
        } catch (IllegalStateException exception) {
            if (getEvent(calendarId, eventId) == null) return false;
            throw exception;
        }
        return true;
    }

    /** Changes only alerts on an event owned by Chrona; event content and history remain intact. */
    public boolean replaceReminders(long eventId, List<Integer> minutes) {
        requireReadWrite();
        requireId(eventId);
        Long calendarId = findCalendarId();
        EventRecord master = calendarId == null ? null : getEvent(calendarId, eventId);
        if (master == null) return false;
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(ContentProviderOperation.newAssertQuery(Events.CONTENT_URI)
                .withSelection(Events._ID + "=? AND " + Events.CALENDAR_ID + "=?",
                        new String[]{Long.toString(eventId), Long.toString(calendarId)})
                .withExpectedCount(1).build());
        operations.add(ContentProviderOperation.newDelete(syncUri(Reminders.CONTENT_URI))
                .withSelection(Reminders.EVENT_ID + "=?", new String[]{Long.toString(eventId)}).build());
        for (int minute : minutes) operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                .withValue(Reminders.EVENT_ID, eventId).withValue(Reminders.MINUTES, minute)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build());
        if (master.rrule != null && !master.rrule.isEmpty()) {
            try {
                JSONObject changes = new JSONObject(master.repeatExceptions == null ? "{}" : master.repeatExceptions);
                try (Cursor cursor = resolver.query(Events.CONTENT_URI,
                        new String[]{Events._ID, Events.ORIGINAL_INSTANCE_TIME, Events.STATUS},
                        Events.ORIGINAL_ID + "=? AND " + Events.CALENDAR_ID + "=?",
                        new String[]{Long.toString(eventId), Long.toString(calendarId)}, null)) {
                    if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
                    while (cursor.moveToNext()) {
                        long exceptionId = cursor.getLong(0);
                        JSONObject change = changes.optJSONObject(Long.toString(cursor.getLong(1)));
                        boolean completed = change != null && !change.isNull("completed");
                        operations.add(ContentProviderOperation.newDelete(syncUri(Reminders.CONTENT_URI))
                                .withSelection(Reminders.EVENT_ID + "=?", new String[]{Long.toString(exceptionId)}).build());
                        if (completed || !cursor.isNull(2) && cursor.getInt(2) == Events.STATUS_CANCELED) continue;
                        for (int minute : minutes) operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                                .withValue(Reminders.EVENT_ID, exceptionId).withValue(Reminders.MINUTES, minute)
                                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build());
                    }
                }
            } catch (org.json.JSONException invalid) {
                throw new IllegalArgumentException("Invalid repeat exceptions", invalid);
            }
        }
        apply(operations);
        return true;
    }

    /** Returns false if the event ID is stale or belongs to another calendar. */
    public boolean deleteEvent(long eventId) {
        requireReadWrite();
        requireId(eventId);
        Long calendarId = findCalendarId();
        if (calendarId == null) return false;
        int count = resolver.delete(syncUri(Events.CONTENT_URI),
                Events._ID + "=? AND " + Events.CALENDAR_ID + "=?",
                new String[]{Long.toString(eventId), Long.toString(calendarId)});
        return count > 0;
    }

    /** Returns null when the provider no longer has this event in Chrona's calendar. */
    public EventRecord getEvent(long eventId) {
        requireRead();
        requireId(eventId);
        Long calendarId = findCalendarId();
        return calendarId == null ? null : getEvent(calendarId, eventId);
    }

    /** Verify linked IDs in bounded batches; a provider failure never means an event was deleted. */
    public java.util.Set<Long> existingEventIds(List<Long> ids) {
        requireRead();
        java.util.Set<Long> existing = new java.util.HashSet<>();
        for (int offset = 0; offset < ids.size(); offset += 200) {
            List<Long> batch = ids.subList(offset, Math.min(offset + 200, ids.size()));
            String[] arguments = new String[batch.size()];
            StringBuilder selection = new StringBuilder(Events.DELETED + "=0 AND " + Events._ID + " IN (");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) selection.append(',');
                selection.append('?');
                arguments[i] = Long.toString(batch.get(i));
            }
            selection.append(')');
            try (Cursor cursor = resolver.query(Events.CONTENT_URI, new String[]{Events._ID},
                    selection.toString(), arguments, null)) {
                if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
                while (cursor.moveToNext()) existing.add(cursor.getLong(0));
            }
        }
        return existing;
    }

    /** The calendar ID is checked against the current provider row before reading. */
    public EventRecord getEvent(long calendarId, long eventId) {
        requireRead();
        requireId(calendarId);
        requireId(eventId);
        if (!ownsCalendar(calendarId)) return null;
        try (Cursor cursor = resolver.query(Events.CONTENT_URI, EVENT_PROJECTION,
                Events._ID + "=? AND " + Events.CALENDAR_ID + "=?",
                new String[]{Long.toString(eventId), Long.toString(calendarId)}, null)) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            return cursor.moveToFirst() ? readEvent(cursor) : null;
        }
    }

    /** Queries the current provider state; an empty list also covers a removed calendar. */
    public List<EventRecord> listEvents() {
        requireRead();
        Long calendarId = findCalendarId();
        return calendarId == null ? Collections.emptyList() : listEvents(calendarId);
    }

    /** Returns an empty list if this saved calendar ID has become stale. */
    public List<EventRecord> listEvents(long calendarId) {
        requireRead();
        requireId(calendarId);
        if (!ownsCalendar(calendarId)) return Collections.emptyList();
        ArrayList<EventRecord> events = new ArrayList<>();
        try (Cursor cursor = resolver.query(Events.CONTENT_URI, EVENT_PROJECTION,
                Events.CALENDAR_ID + "=?", new String[]{Long.toString(calendarId)},
                Events.DTSTART + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext()) events.add(readEvent(cursor));
        }
        return events;
    }

    private boolean ownsCalendar(long calendarId) {
        Long current = findCalendarId();
        return current != null && current == calendarId;
    }

    private EventRecord readEvent(Cursor cursor) {
        long id = cursor.getLong(0);
        ArrayList<Integer> reminders = new ArrayList<>();
        try (Cursor reminderCursor = resolver.query(Reminders.CONTENT_URI, REMINDER_PROJECTION,
                Reminders.EVENT_ID + "=?", new String[]{Long.toString(id)},
                Reminders.MINUTES + " ASC")) {
            if (reminderCursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (reminderCursor.moveToNext()) reminders.add(reminderCursor.getInt(0));
        }
        return new EventRecord(id, cursor.getLong(1), cursor.getString(2), cursor.getLong(3),
                cursor.isNull(4) ? durationEnd(cursor.getLong(3), cursor.getString(10)) : cursor.getLong(4),
                cursor.getString(5), cursor.getInt(6) != 0,
                cursor.getString(7), cursor.getString(8), reminders,
                cursor.getString(9), cursor.getString(10), nullableLong(cursor, 11), nullableLong(cursor, 12),
                cursor.isNull(13) ? null : cursor.getInt(13), cursor.isNull(14) ? null : cursor.getInt(14),
                cursor.getString(15), cursor.getString(16), cursor.getString(17), cursor.getString(18),
                nullableLong(cursor, 19));
    }

    private static Long nullableLong(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static long durationEnd(long start, String duration) {
        if (duration == null || duration.isEmpty()) return start;
        try {
            // java.time supports ISO days but omits the RFC 5545 week-only form.
            if (duration.matches("[+-]?P[0-9]+W")) {
                int p = duration.indexOf('P');
                long weeks = Long.parseLong(duration.substring(p + 1, duration.length() - 1));
                long length = Math.multiplyExact(weeks, 7L * 86_400_000L);
                return Math.addExact(start, duration.startsWith("-") ? -length : length);
            }
            return Math.addExact(start, java.time.Duration.parse(duration).toMillis());
        }
        catch (RuntimeException invalid) { throw new IllegalStateException("Invalid calendar event duration", invalid); }
    }

    private static ContentValues eventValues(long calendarId, EventInput input) {
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.TITLE, input.title);
        values.put(Events.DTSTART, input.startMillis);
        if (input.rrule == null) {
            values.put(Events.DTEND, input.endMillis);
            values.putNull(Events.RRULE); values.putNull(Events.DURATION);
        } else {
            values.putNull(Events.DTEND);
            values.put(Events.RRULE, input.rrule); values.put(Events.DURATION, duration(input));
            // AOSP groups recurrence exceptions by sync ID, including local-only calendars.
            values.put(Events._SYNC_ID, input.syncId);
        }
        values.putNull(Events.RDATE); values.putNull(Events.EXDATE); values.putNull(Events.EXRULE);
        values.put(Events.SYNC_DATA1, input.repeatRuleJson);
        values.put(Events.SYNC_DATA2, input.repeatExceptions);
        values.put(Events.SYNC_DATA3, input.repeatStoppedAtMillis);
        values.put(Events.STATUS, input.cancelMaster ? Events.STATUS_CANCELED : Events.STATUS_CONFIRMED);
        values.put(Events.EVENT_TIMEZONE, input.timeZoneId);
        values.put(Events.ALL_DAY, input.allDay ? 1 : 0);
        values.put(Events.DESCRIPTION, input.description);
        values.put(Events.EVENT_LOCATION, input.location);
        return values;
    }

    private static String duration(EventInput input) {
        long length = input.endMillis - input.startMillis;
        return input.allDay ? "P" + (length / 86_400_000L) + "D" : "PT" + (length / 1000L) + "S";
    }

    private void appendExceptions(ArrayList<ContentProviderOperation> operations, long calendarId,
            EventInput input, Long eventId) {
        if (input.rrule == null) return;
        try {
            JSONObject exceptions = new JSONObject(input.repeatExceptions);
            java.util.Iterator<String> keys = exceptions.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long original = Long.parseLong(key);
                JSONObject change = exceptions.getJSONObject(key);
                long actual = change.has("start") ? change.getLong("start") : original;
                boolean canceled = change.optBoolean("skipped")
                        || input.repeatStoppedAtMillis != null && actual >= input.repeatStoppedAtMillis;
                // A completed occurrence stays visible, with an exception suppressing its alerts.
                boolean completed = !change.isNull("completed");
                if (!canceled && !completed && actual == original) continue;
                ContentValues values = eventValues(calendarId, input);
                values.putNull(Events._SYNC_ID);
                values.putNull(Events.SYNC_DATA1); values.putNull(Events.SYNC_DATA2); values.putNull(Events.SYNC_DATA3);
                values.putNull(Events.RRULE); values.putNull(Events.DURATION);
                values.put(Events.DTSTART, actual);
                values.put(Events.DTEND, Math.addExact(actual, input.endMillis - input.startMillis));
                values.put(Events.ORIGINAL_SYNC_ID, input.syncId);
                values.put(Events.ORIGINAL_INSTANCE_TIME, original);
                values.put(Events.ORIGINAL_ALL_DAY, input.allDay ? 1 : 0);
                values.put(Events.STATUS, canceled ? Events.STATUS_CANCELED : Events.STATUS_CONFIRMED);
                ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(syncUri(Events.CONTENT_URI))
                        .withValues(values);
                if (eventId == null) builder.withValueBackReference(Events.ORIGINAL_ID, 0);
                else builder.withValue(Events.ORIGINAL_ID, eventId);
                int exceptionIndex = operations.size();
                operations.add(builder.build());
                if (!canceled && !completed) for (int minutes : input.reminderMinutes) {
                    operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                            .withValueBackReference(Reminders.EVENT_ID, exceptionIndex)
                            .withValue(Reminders.MINUTES, minutes).withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build());
                }
            }
        } catch (org.json.JSONException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid repeat exceptions", invalid);
        }
    }

    private Uri syncUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();
    }

    private ContentProviderResult[] apply(ArrayList<ContentProviderOperation> operations) {
        try {
            return resolver.applyBatch(CalendarContract.AUTHORITY, operations);
        } catch (RemoteException | OperationApplicationException exception) {
            throw new IllegalStateException("Calendar provider rejected the event change", exception);
        }
    }

    private void requireRead() {
        if (!hasReadPermission()) throw new SecurityException("READ_CALENDAR permission is required");
    }

    private void requireReadWrite() {
        requireRead();
        if (!hasWritePermission()) throw new SecurityException("WRITE_CALENDAR permission is required");
    }

    private static void requireId(long id) {
        if (id <= 0) throw new IllegalArgumentException("Provider ID must be positive");
    }

    public static final class EventInput {
        public final String title;
        public final long startMillis;
        public final long endMillis;
        public final String timeZoneId;
        public final boolean allDay;
        public final String description;
        public final String location;
        public final List<Integer> reminderMinutes;
        public final String rrule;
        public final String repeatRuleJson;
        public final String repeatExceptions;
        public final Long repeatStoppedAtMillis;
        private final String syncId;
        private final boolean cancelMaster;

        /** All-day bounds must be UTC midnights, with an exclusive end. */
        public EventInput(String title, long startMillis, long endMillis, String timeZoneId,
                          boolean allDay, String description, String location,
                          List<Integer> reminderMinutes) {
            this(title, startMillis, endMillis, timeZoneId, allDay, description, location,
                    reminderMinutes, null, null, "{}", null, null, false);
        }

        private EventInput(String title, long startMillis, long endMillis, String timeZoneId,
                          boolean allDay, String description, String location, List<Integer> reminderMinutes,
                          String rrule, String repeatRuleJson, String repeatExceptions, Long stoppedAt,
                          String syncId, boolean cancelMaster) {
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("title is required");
            }
            if (endMillis <= startMillis) {
                throw new IllegalArgumentException("endMillis must be after startMillis");
            }
            if (timeZoneId == null || timeZoneId.trim().isEmpty()) {
                throw new IllegalArgumentException("timeZoneId is required");
            }
            if (allDay && (!"UTC".equals(timeZoneId)
                    || startMillis % 86_400_000L != 0 || endMillis % 86_400_000L != 0)) {
                throw new IllegalArgumentException("All-day bounds require UTC midnight values");
            }
            if (reminderMinutes == null || reminderMinutes.size() > MAX_REMINDERS) {
                throw new IllegalArgumentException("reminderMinutes must contain at most five values");
            }
            ArrayList<Integer> copy = new ArrayList<>(reminderMinutes.size());
            for (Integer minutes : reminderMinutes) {
                if (minutes == null || minutes < 0 || copy.contains(minutes)) {
                    throw new IllegalArgumentException("Reminder minutes must be unique and nonnegative");
                }
                copy.add(minutes);
            }
            this.title = title.trim();
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.timeZoneId = timeZoneId;
            this.allDay = allDay;
            this.description = description;
            this.location = location;
            this.reminderMinutes = Collections.unmodifiableList(copy);
            this.rrule = rrule;
            this.repeatRuleJson = repeatRuleJson;
            this.repeatExceptions = repeatExceptions;
            this.repeatStoppedAtMillis = stoppedAt;
            this.syncId = syncId;
            this.cancelMaster = cancelMaster;
        }

        /** The input bounds remain the stored series' first occurrence, never a projected occurrence. */
        public EventInput withRepeat(EventCandidate series) {
            if (series == null) throw new IllegalArgumentException("series is required");
            if (series.occurrenceAtMillis != null) throw new IllegalArgumentException("A repeat master is required");
            if (series.repeatRule == null) return new EventInput(title, startMillis, endMillis,
                    timeZoneId, allDay, description, location, reminderMinutes);
            series.repeatRule.validateAnchor(startMillis, allDay ? "UTC" : timeZoneId);
            String rule = stoppedRule(series.repeatRule, startMillis, timeZoneId, allDay, series.repeatStoppedAtMillis);
            return new EventInput(title, startMillis, endMillis, timeZoneId, allDay, description, location,
                    reminderMinutes, rule, series.repeatRule.toJson().toString(), series.repeatExceptions,
                    series.repeatStoppedAtMillis, java.util.UUID.randomUUID().toString(),
                    series.repeatStoppedAtMillis != null && series.repeatStoppedAtMillis <= startMillis);
        }

        /** Restores the provider master and regenerates its exception rows in the same insert batch. */
        public EventInput withRepeat(EventRecord record) {
            if (record == null) throw new IllegalArgumentException("record is required");
            if (record.originalId != null) throw new IllegalArgumentException("A repeat master is required");
            if (record.rrule == null || record.rrule.isEmpty()) return new EventInput(title, startMillis,
                    endMillis, timeZoneId, allDay, description, location, reminderMinutes);
            return new EventInput(title, startMillis, endMillis, timeZoneId, allDay, description, location,
                    reminderMinutes, record.rrule, record.repeatRuleJson,
                    record.repeatExceptions == null ? "{}" : record.repeatExceptions, record.repeatStoppedAtMillis,
                    java.util.UUID.randomUUID().toString(), record.status != null && record.status == Events.STATUS_CANCELED);
        }
    }

    /** Convert COUNT to its actual final occurrence before intersecting it with the stop boundary. */
    static String stoppedRule(RepeatRule rule, long start, String zone, boolean allDay, Long stop) {
        String rrule = rule.rrule(zone, allDay);
        if (stop == null || stop <= start) return rrule;
        long cutoff = stop - 1;
        if (rule.count != null) {
            Recurrence.Builder builder = new Recurrence.Builder(Frequency.valueOf(rule.frequency.toUpperCase(Locale.ROOT)))
                    .interval(rule.interval).count(rule.count).workweekStarts(biweekly.util.DayOfWeek.MONDAY);
            for (int day : rule.weekdays) builder.byDay(biweekly.util.DayOfWeek.valueOf(java.time.DayOfWeek.of(day).name()));
            VEvent event = new VEvent();
            event.setDateStart(new Date(start)); event.setRecurrenceRule(builder.build());
            DateIterator iterator = event.getDateIterator(TimeZone.getTimeZone(allDay ? "UTC" : zone));
            long last = start;
            while (iterator.hasNext()) {
                last = iterator.next().getTime();
                if (last >= cutoff) break;
            }
            cutoff = Math.min(cutoff, last);
        }
        if (rule.until != null) {
            long originalEnd = rule.until.plusDays(1).atStartOfDay(ZoneId.of(allDay ? "UTC" : zone))
                    .toInstant().toEpochMilli() - 1;
            cutoff = Math.min(cutoff, originalEnd);
        }
        rrule = rrule.replaceAll(";(COUNT|UNTIL)=[^;]+", "");
        java.time.format.DateTimeFormatter format = allDay
                ? java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").withZone(java.time.ZoneOffset.UTC)
                : java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC);
        return rrule + ";UNTIL=" + format.format(Instant.ofEpochMilli(cutoff));
    }

    public static final class EventRecord {
        public final long id;
        public final long calendarId;
        public final String title;
        public final long startMillis;
        public final long endMillis;
        public final String timeZoneId;
        public final boolean allDay;
        public final String description;
        public final String location;
        public final List<Integer> reminderMinutes;
        public final String rrule;
        public final String duration;
        public final Long originalId;
        public final Long originalInstanceTime;
        public final Integer originalAllDay;
        public final Integer status;
        public final String syncId;
        public final String originalSyncId;
        public final String repeatRuleJson;
        public final String repeatExceptions;
        public final Long repeatStoppedAtMillis;

        private EventRecord(long id, long calendarId, String title, long startMillis,
                            long endMillis, String timeZoneId, boolean allDay,
                            String description, String location, List<Integer> reminderMinutes,
                            String rrule, String duration, Long originalId, Long originalInstanceTime,
                            Integer originalAllDay, Integer status, String syncId, String originalSyncId,
                            String repeatRuleJson, String repeatExceptions, Long repeatStoppedAtMillis) {
            this.id = id;
            this.calendarId = calendarId;
            this.title = title;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.timeZoneId = timeZoneId;
            this.allDay = allDay;
            this.description = description;
            this.location = location;
            this.reminderMinutes = Collections.unmodifiableList(new ArrayList<>(reminderMinutes));
            this.rrule = rrule; this.duration = duration;
            this.originalId = originalId; this.originalInstanceTime = originalInstanceTime;
            this.originalAllDay = originalAllDay; this.status = status;
            this.syncId = syncId; this.originalSyncId = originalSyncId;
            this.repeatRuleJson = repeatRuleJson; this.repeatExceptions = repeatExceptions;
            this.repeatStoppedAtMillis = repeatStoppedAtMillis;
        }
    }
}
