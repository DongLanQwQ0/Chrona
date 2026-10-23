package com.donglan.chrona.calendar;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatRule;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Real SQLite, production helper and extracted production persistence/input members. */
public final class LinkedCalendarEventsCheck {
    private static int assertions, scenarios;
    private static File directory;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(Objects.equals(actual, expected), message + ": expected " + expected + ", got " + actual);
    }

    private static RuntimeException fails(Runnable work, String message) {
        try { work.run(); }
        catch (RuntimeException error) { assertions++; return error; }
        throw new AssertionError(message);
    }

    private static CalendarStore.EventInput input(String title) {
        return new CalendarStore.EventInput(title, 1_800_000_000_000L, 1_800_003_600_000L,
                "Asia/Shanghai", false, "provider description", "provider location", List.of(5, 30));
    }

    private static EventCandidate candidate(long id, long task, Long event) {
        return new EventCandidate(id, task, "stale local title", 1000L, 2000L, "Europe/Paris",
                "stale local location", "stale local description", 99, false, event);
    }

    private static final class Fixture implements AutoCloseable {
        final File file = new File(directory, UUID.randomUUID() + ".db");
        final SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        final TaskStore store = new TaskStore(db);
        final CalendarStore calendar = new CalendarStore();
        Fixture() {
            // Deliberately reduced compatible schema: every SQL predicate/bind is production code.
            db.execSQL("CREATE TABLE event_candidates(id INTEGER PRIMARY KEY, task_id INTEGER NOT NULL, "
                    + "calendar_event_id INTEGER, needs_confirmation INTEGER, uncertainty_level INTEGER)");
            db.execSQL("CREATE TABLE candidate_merges(id INTEGER PRIMARY KEY, calendar_event_id INTEGER)");
            db.setForeignKeyConstraintsEnabled(true);
            calendar.seed(10, input("original provider title"));
        }
        EventCandidate row(long id, long task, Long event) {
            db.execSQL("INSERT INTO event_candidates VALUES(?,?,?,1,2)", new Object[]{id, task, event});
            return candidate(id, task, event);
        }
        Long link(long id) {
            try (Cursor cursor = db.rawQuery("SELECT calendar_event_id FROM event_candidates WHERE id=?",
                    new String[]{Long.toString(id)})) {
                if (!cursor.moveToFirst()) throw new AssertionError("missing fixture row " + id);
                return cursor.isNull(0) ? null : cursor.getLong(0);
            }
        }
        void audit() { db.execSQL("INSERT INTO candidate_merges VALUES(1,10)"); }
        LinkedCalendarEvents writes() { return new LinkedCalendarEvents(store, calendar); }
        void commitFailure() {
            db.execSQL("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TABLE child(parent_id INTEGER REFERENCES parent(id) DEFERRABLE INITIALLY DEFERRED)");
        }
        void transact(java.util.function.Consumer<LinkedCalendarEvents> action, boolean failCommit) {
            try (LinkedCalendarEvents writes = writes()) {
                db.beginTransaction();
                try {
                    action.accept(writes);
                    if (failCommit) db.execSQL("INSERT INTO child VALUES(123)");
                    db.setTransactionSuccessful();
                } finally {
                    try { db.endTransaction(); }
                    catch (RuntimeException commitError) {
                        // SQLite leaves a failed deferred-FK COMMIT transaction open.
                        db.execSQL("ROLLBACK");
                        throw commitError;
                    }
                }
                writes.committed();
            }
        }
        public void close() { db.close(); check(file.delete(), "remove isolated SQLite fixture"); }
    }

    private static void sqlReferencesAndCompareAndSwap() {
        try (Fixture f = new Fixture()) {
            f.row(1, 7, 10L); f.row(2, 8, 10L); f.row(3, 9, 20L); f.row(4, 9, null);
            check(f.store.hasOtherCalendarReferences(10, List.of(1L)), "other candidate protects event");
            check(!f.store.hasOtherCalendarReferences(10, List.of(1L, 2L, 2L)), "exclude whole selection");
            check(f.store.hasOtherCalendarReferences(10, List.of()), "empty exclusions retain live rows");
            check(!f.store.hasOtherCalendarReferences(77, List.of()), "unreferenced event");
            f.audit();
            check(f.store.hasOtherCalendarReferences(10, List.of(1L, 2L)), "audit survives selected candidates");
            f.db.execSQL("DELETE FROM event_candidates WHERE calendar_event_id=10");
            check(f.store.hasOtherCalendarReferences(10, List.of()), "audit protects with no source candidates");
            check(!f.store.replaceCalendarEventId(3, 8, 20, 21L), "wrong task cannot relink");
            check(!f.store.replaceCalendarEventId(99, 9, 20, 21L), "wrong candidate cannot relink");
            check(!f.store.replaceCalendarEventId(3, 9, 19, 21L), "stale expected ID cannot relink");
            check(!f.store.replaceCalendarEventId(4, 9, 20, 21L), "NULL does not match expected ID");
            fails(() -> f.store.replaceCalendarEventId(3, 9, 20, 0L), "reject zero replacement");
            fails(() -> f.store.replaceCalendarEventId(3, 9, 20, -1L), "reject negative replacement");
            equal(f.link(3), 20L, "failed CAS never changes association");
            check(f.store.replaceCalendarEventId(3, 9, 20, 21L), "valid CAS updates one row");
            equal(f.link(3), 21L, "replacement stored");
            equal(f.link(4), null, "unrelated candidate untouched");
            check(f.store.replaceCalendarEventId(3, 9, 21, null), "valid CAS supports SQL NULL");
            equal(f.link(3), null, "detach stores SQL NULL");
        }
        scenarios++;
    }

    private static void updates() {
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.row(2, 8, 10L);
            CalendarStore.EventRecord old = f.calendar.getEvent(10);
            f.transact(w -> check(w.update(a, input("edited A")), "shared edit succeeds"), false);
            equal(f.link(1), 100L, "A gets independent event");
            equal(f.link(2), 10L, "B keeps original event");
            check(f.calendar.getEvent(10) == old, "shared original provider record untouched");
            equal(f.calendar.getEvent(100).title, "edited A", "new provider fields saved");
            equal(f.calendar.updated.size(), 0, "no update of shared provider record");
            equal(f.calendar.deleted.size(), 0, "commit/close retain both events");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L);
            f.transact(w -> check(w.update(a, input("exclusive")), "exclusive update succeeds"), false);
            equal(f.link(1), 10L, "exclusive association unchanged");
            equal(f.calendar.updated, List.of(10L), "exclusive update is in place");
            equal(f.calendar.records.size(), 1, "exclusive edit creates no duplicate");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.audit();
            f.transact(w -> check(w.update(a, input("audit protected")), "audit causes split"), false);
            equal(f.link(1), 100L, "audit reference gets independent event");
            equal(f.calendar.getEvent(10).title, "original provider title", "audit provider unchanged");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.row(2, 8, 10L);
            f.calendar.records.clear();
            try (LinkedCalendarEvents w = f.writes()) {
                check(!w.update(a, input("missing")), "missing shared event returns false");
                check(!w.description(a, "missing"), "missing description target returns false");
                check(!w.reminders(a, List.of()), "missing reminder target returns false");
            }
            equal(f.link(1), 10L, "missing provider leaves link unchanged");
            equal(f.calendar.records.size(), 0, "no replacement for missing provider");
        }
        scenarios++;
    }

    private static void fieldCopies(boolean allDay, boolean description) {
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.row(2, 8, 10L);
            long start = allDay ? 1_800_057_600_000L : 1_800_000_000_000L;
            if (allDay) start -= start % 86_400_000L;
            long end = start + (allDay ? 86_400_000L : 3_600_000L);
            EventCandidate series = new EventCandidate(80, 90, "provider title", start, end,
                    allDay ? "UTC" : "Asia/Shanghai", "provider place", "provider notes", 15, false, 10L)
                    .withRepeat(new RepeatRule("weekly", 2, List.of(), null, 6))
                    .withRepeatState("{\"1800000000000\":{\"cancelled\":true}}", start);
            CalendarStore.EventInput original = new CalendarStore.EventInput("provider title", start, end,
                    allDay ? "UTC" : "Asia/Shanghai", allDay, "provider notes", "provider place", List.of(15, 60))
                    .withRepeat(series);
            f.calendar.seed(10, original);
            CalendarStore.EventRecord before = f.calendar.getEvent(10);
            f.transact(w -> check(description ? w.description(a, "new notes") : w.reminders(a, List.of(2, 9)),
                    "shared field update succeeds"), false);
            CalendarStore.EventRecord copied = f.calendar.getEvent(f.link(1));
            equal(copied.title, before.title, "copy title from provider, not stale candidate");
            equal(copied.startMillis, before.startMillis, "copy provider start");
            equal(copied.endMillis, before.endMillis, "copy provider end");
            equal(copied.timeZoneId, before.timeZoneId, "copy provider zone");
            equal(copied.allDay, before.allDay, "copy provider all-day flag");
            equal(copied.location, before.location, "copy provider location");
            equal(copied.description, description ? "new notes" : before.description, "only requested description change");
            equal(copied.reminderMinutes, description ? before.reminderMinutes : List.of(2, 9), "only requested reminders change");
            equal(copied.rrule, before.rrule, "copy provider recurrence");
            equal(copied.repeatRuleJson, before.repeatRuleJson, "copy recurrence metadata");
            equal(copied.repeatExceptions, before.repeatExceptions, "copy recurrence exceptions");
            equal(copied.repeatStoppedAtMillis, before.repeatStoppedAtMillis, "copy recurrence stop");
            equal(copied.status, before.status, "copy cancelled-master state");
            check(!Objects.equals(copied.syncId, before.syncId), "independent repeat master has new sync identity");
            check(f.calendar.getEvent(10) == before, "B's provider object unchanged");
            equal(f.link(2), 10L, "B's association unchanged");
        }
        scenarios++;
    }

    private static void deletionAndDetach() {
        for (boolean all : List.of(false, true)) for (boolean audit : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                EventCandidate a = f.row(1, 7, 10L), b = f.row(2, 8, 10L);
                EventCandidate unlinked = f.row(3, 7, null);
                f.row(4, 7, 20L); f.calendar.seed(20, input("unrelated"));
                if (audit) f.audit();
                try (LinkedCalendarEvents w = f.writes()) {
                    w.delete(all ? List.of(a, b, a, unlinked) : List.of(a, unlinked));
                }
                equal(f.calendar.deleted, all && !audit ? List.of(10L) : List.of(), "selection and audit deletion protection");
                check(f.calendar.getEvent(20) != null, "unrelated event survives selection");
                equal(f.link(1), 10L, "delete helper leaves local deletion to transaction owner");
            }
            scenarios++;
        }
        for (boolean shared : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                EventCandidate a = f.row(1, 7, 10L), unlinked = f.row(3, 7, null);
                if (shared) f.row(2, 8, 10L);
                f.transact(w -> { w.detach(a); w.detach(unlinked); }, false);
                equal(f.link(1), null, "detach clears only A");
                equal(f.link(3), null, "unlinked detach is no-op");
                equal(f.calendar.deleted, shared ? List.of() : List.of(10L), "detach protects other references");
                if (shared) equal(f.link(2), 10L, "detach keeps B link");
            }
            scenarios++;
        }
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.calendar.failDelete.add(10L);
            fails(() -> f.transact(w -> w.detach(a), false), "provider deletion error visible");
            equal(f.link(1), 10L, "failed provider deletion preserves local association");
            check(f.calendar.getEvent(10) != null, "failed deletion retains provider");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.calendar.refuseDelete.add(10L);
            fails(() -> f.transact(w -> {
                w.delete(List.of(a));
                f.db.execSQL("DELETE FROM event_candidates WHERE id=1");
            }, false), "provider false with existing record stops local deletion");
            equal(f.link(1), 10L, "provider refusal retains candidate and link");
            check(f.calendar.getEvent(10) != null, "refused provider record remains");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.calendar.records.clear();
            f.transact(w -> w.detach(a), false);
            equal(f.link(1), null, "already missing provider permits local detach");
            equal(f.calendar.deleted, List.of(10L), "stale deletion was attempted once");
        }
        scenarios++;
    }

    private static void conflictsAndFailures() {
        try (Fixture f = new Fixture()) {
            EventCandidate stale = f.row(1, 7, 10L);
            fails(() -> f.transact(w -> w.insert(stale, input("conflict")), false), "insert association conflict visible");
            equal(f.link(1), 10L, "insert conflict preserves reused link");
            equal(f.calendar.deleted, List.of(100L), "insert conflict cleans only newly inserted event");
            check(f.calendar.getEvent(10) != null, "insert conflict never deletes reused event");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.row(2, 8, 10L);
            f.db.execSQL("CREATE TRIGGER conflict BEFORE UPDATE OF calendar_event_id ON event_candidates "
                    + "WHEN OLD.id=1 BEGIN SELECT RAISE(IGNORE); END");
            fails(() -> f.transact(w -> w.update(a, input("CAS conflict")), false), "update association conflict visible");
            equal(f.link(1), 10L, "CAS conflict preserves A");
            equal(f.link(2), 10L, "CAS conflict preserves B");
            equal(f.calendar.deleted, List.of(100L), "CAS conflict cleans only new event");
            equal(f.calendar.getEvent(10).title, "original provider title", "CAS conflict leaves original provider untouched");
        }
        scenarios++;
        for (boolean shared : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                EventCandidate a = f.row(1, 7, shared ? 10L : null);
                if (shared) f.row(2, 8, 10L);
                f.commitFailure();
                RuntimeException error = fails(() -> f.transact(w -> {
                    if (shared) w.update(a, input("failed commit")); else w.insert(a, input("failed commit"));
                }, true), "real SQLite COMMIT failure visible");
                check(error.getMessage().contains("FOREIGN KEY constraint failed"), "failure occurs in SQLite COMMIT");
                equal(f.link(1), shared ? 10L : null, "failed commit rolls back association");
                equal(f.calendar.deleted, List.of(100L), "failed commit cleans newly created event");
                equal(f.calendar.getEvent(10).title, "original provider title", "failed commit never deletes/edits old shared event");
                check(f.calendar.getEvent(100) == null, "failed commit leaves no new orphan");
            }
            scenarios++;
        }
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.commitFailure();
            fails(() -> f.transact(w -> w.update(a, input("in place before commit failure")), true), "exclusive commit fails");
            equal(f.link(1), 10L, "exclusive association intact after commit failure");
            equal(f.calendar.deleted.size(), 0, "compensation never deletes reused exclusive event");
            check(f.calendar.getEvent(10) != null, "exclusive provider still exists");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, null); f.calendar.failInsert = true;
            fails(() -> f.transact(w -> w.insert(a, input("provider failure")), false), "provider insert failure visible");
            equal(f.link(1), null, "provider insert failure leaves candidate unlinked");
            equal(f.calendar.deleted.size(), 0, "failed insertion has nothing to compensate");
        }
        scenarios++;
    }

    private static void cleanupAndCommitLifecycle() {
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, null); f.calendar.refuseDelete.add(100L);
            LinkedCalendarEvents w = f.writes();
            w.insert(a, input("refused cleanup"));
            RuntimeException error = fails(w::close, "false-returning cleanup with surviving event must be visible");
            check(error.getMessage().contains("清理失败"), "refused cleanup reports cleanup failure");
            check(f.calendar.getEvent(100) != null, "refused orphan still requires manual recovery");
            equal(f.calendar.deleted, List.of(100L), "only orphan cleanup attempted");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, null);
            LinkedCalendarEvents w = f.writes();
            w.insert(a, input("already removed cleanup"));
            f.calendar.records.remove(100L);
            w.close();
            equal(f.calendar.deleted, List.of(100L), "already missing orphan does not report cleanup failure");
            check(f.calendar.getEvent(10) != null, "missing orphan cleanup never targets pre-existing event");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, null), b = f.row(2, 8, null);
            f.calendar.failDelete.addAll(List.of(100L, 101L));
            LinkedCalendarEvents w = f.writes();
            w.insert(a, input("orphan A")); w.insert(b, input("orphan B"));
            RuntimeException error = fails(w::close, "cleanup error visible");
            check(error.getMessage().contains("清理失败"), "cleanup has actionable message");
            equal(error.getCause().getMessage(), "provider delete failed: 100", "first cleanup cause preserved");
            equal(error.getSuppressed().length, 1, "later cleanup failure suppressed");
            equal(f.calendar.deleted, List.of(100L, 101L), "cleanup attempts all new events");
            check(f.calendar.getEvent(10) != null, "cleanup failures never target old events");
            w.close();
            equal(f.calendar.deleted.size(), 2, "close is idempotent after failed cleanup");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, 10L); f.calendar.failDelete.add(100L);
            RuntimeException error = fails(() -> f.transact(w -> w.insert(a, input("conflict")), false),
                    "primary failure plus cleanup failure visible");
            check(error.getMessage().contains("关联已变化"), "association conflict stays primary");
            equal(error.getSuppressed().length, 1, "cleanup failure retained on primary exception");
            check(error.getSuppressed()[0].getMessage().contains("清理失败"), "suppressed cleanup is identifiable");
            equal(f.link(1), 10L, "failed cleanup still rolls back SQL");
        }
        scenarios++;
        try (Fixture f = new Fixture()) {
            EventCandidate a = f.row(1, 7, null), b = f.row(2, 8, null);
            f.transact(w -> { w.insert(a, input("committed A")); w.insert(b, input("committed B")); }, false);
            equal(f.link(1), 100L, "committed A linked"); equal(f.link(2), 101L, "committed B linked");
            equal(f.calendar.deleted.size(), 0, "committed before close disables compensation");
            check(f.calendar.getEvent(100) != null && f.calendar.getEvent(101) != null, "committed provider events survive close");
        }
        scenarios++;
    }

    public static void main(String[] args) {
        directory = new File(args[0]); directory.mkdirs();
        sqlReferencesAndCompareAndSwap(); updates();
        for (boolean allDay : List.of(false, true)) for (boolean description : List.of(false, true))
            fieldCopies(allDay, description);
        deletionAndDetach(); conflictsAndFailures(); cleanupAndCommitLifecycle();
        System.out.println("LinkedCalendarEvents: " + scenarios + " scenarios / " + assertions
                + " assertions passed (production helper + exact TaskStore SQL + real SQLite COMMIT failures)");
    }
}
