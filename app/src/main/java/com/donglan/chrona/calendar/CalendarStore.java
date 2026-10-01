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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.text.Normalizer;
import java.util.Locale;

/** Direct access to Chrona's device-local system calendar. Call from a background thread. */
public final class CalendarStore {
    public static final String CALENDAR_NAME = "拾时 · Chrona";

    private static final int CALENDAR_COLOR = 0xFF486A85;
    private static final int MAX_REMINDERS = 5;
    private static final int MAX_HOME_INSTANCES = 1000;
    private static final String[] CALENDAR_ID_PROJECTION = {Calendars._ID};
    private static final String[] EVENT_PROJECTION = {
            Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND,
            Events.EVENT_TIMEZONE, Events.ALL_DAY, Events.DESCRIPTION, Events.EVENT_LOCATION
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
        requireRead();
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, begin);
        ContentUris.appendId(uri, end);
        String[] projection = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, Events.TITLE, Events.ALL_DAY,
                Events.EVENT_LOCATION, Calendars.CALENDAR_DISPLAY_NAME};
        List<CalendarOccurrence> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(uri.build(), projection, Calendars.VISIBLE + "=1 AND ("
                + Events.STATUS + " IS NULL OR " + Events.STATUS + "!=?)",
                new String[]{Integer.toString(Events.STATUS_CANCELED)},
                CalendarContract.Instances.BEGIN + " ASC", cancellation)) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext() && result.size() < MAX_HOME_INSTANCES) {
                if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2)) continue;
                long start = cursor.getLong(1), finish = cursor.getLong(2);
                if (finish <= start) continue;
                result.add(new CalendarOccurrence(cursor.getLong(0), start, finish,
                        cursor.getString(3), cursor.getInt(4) != 0, cursor.getString(5), cursor.getString(6)));
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

    /** Creates a non-recurring event and its alert reminders in one provider batch. */
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
        String[] projection = {Events._ID, Events.TITLE};
        String selection = Events.CALENDAR_ID + "=? AND " + Events.DTSTART + "=? AND "
                + Events.DTEND + "=? AND " + Events.ALL_DAY + "=?";
        String[] args = {Long.toString(calendarId), Long.toString(input.startMillis),
                Long.toString(input.endMillis), input.allDay ? "1" : "0"};
        String title = normalizeTitle(input.title);
        try (Cursor cursor = resolver.query(Events.CONTENT_URI, projection, selection, args,
                Events._ID + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
            while (cursor.moveToNext()) {
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
        for (int minutes : input.reminderMinutes) {
            operations.add(ContentProviderOperation.newInsert(syncUri(Reminders.CONTENT_URI))
                    .withValue(Reminders.EVENT_ID, eventId)
                    .withValue(Reminders.MINUTES, minutes)
                    .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                    .build());
        }
        try {
            apply(operations);
        } catch (IllegalStateException exception) {
            if (getEvent(calendarId, eventId) == null) return false;
            throw exception;
        }
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
                cursor.getLong(4), cursor.getString(5), cursor.getInt(6) != 0,
                cursor.getString(7), cursor.getString(8), reminders);
    }

    private static ContentValues eventValues(long calendarId, EventInput input) {
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.TITLE, input.title);
        values.put(Events.DTSTART, input.startMillis);
        values.put(Events.DTEND, input.endMillis);
        values.put(Events.EVENT_TIMEZONE, input.timeZoneId);
        values.put(Events.ALL_DAY, input.allDay ? 1 : 0);
        values.put(Events.DESCRIPTION, input.description);
        values.put(Events.EVENT_LOCATION, input.location);
        return values;
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

        /** All-day bounds must be UTC midnights, with an exclusive end. */
        public EventInput(String title, long startMillis, long endMillis, String timeZoneId,
                          boolean allDay, String description, String location,
                          List<Integer> reminderMinutes) {
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
        }
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

        private EventRecord(long id, long calendarId, String title, long startMillis,
                            long endMillis, String timeZoneId, boolean allDay,
                            String description, String location, List<Integer> reminderMinutes) {
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
        }
    }
}
