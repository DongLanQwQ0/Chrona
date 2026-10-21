package com.donglan.chrona;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Actual onUpgrade/createSnapshot over real SQLite; all data and restore destinations are isolated. */
public final class SnapshotBackupCheck {
    static int checks;
    static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    static List<String> columns(SQLiteDatabase db, String table) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(1));
        }
        return result;
    }
    static Map<String,List<List<String>>> contents(SQLiteDatabase db) {
        Map<String,List<List<String>>> result = new TreeMap<>();
        try (Cursor tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null)) {
            while (tables.moveToNext()) {
                String table = tables.getString(0);
                List<String> names = columns(db, table);
                Collections.sort(names);
                String selection = String.join(",", names);
                List<List<String>> rows = new ArrayList<>();
                try (Cursor cursor = db.rawQuery("SELECT " + selection + " FROM " + table + " ORDER BY " + selection, null)) {
                    while (cursor.moveToNext()) {
                        List<String> row = new ArrayList<>();
                        for (int i=0;i<names.size();i++) row.add(cursor.getString(i));
                        rows.add(row);
                    }
                }
                result.put(table, rows);
            }
        }
        return result;
    }
    static void integrity(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
            check(cursor.moveToFirst() && "ok".equals(cursor.getString(0)), "SQLite integrity");
        }
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_key_check", null)) {
            check(!cursor.moveToFirst(), "foreign keys preserved");
        }
    }
    static void fixture(Context context) {
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("chrona.db"), null)) {
            db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,raw_text TEXT NOT NULL,source TEXT NOT NULL,created_at_millis INTEGER NOT NULL,status TEXT NOT NULL,error_message TEXT,prompt_tokens INTEGER,completion_tokens INTEGER,total_tokens INTEGER)");
            db.execSQL("CREATE TABLE event_candidates(id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,position INTEGER NOT NULL,title TEXT NOT NULL,start_at_millis INTEGER,end_at_millis INTEGER,time_zone_id TEXT,location TEXT,description TEXT,reminder_minutes_before INTEGER,needs_confirmation INTEGER NOT NULL,calendar_event_id INTEGER,UNIQUE(task_id,position))");
            db.execSQL("INSERT INTO tasks VALUES(7,'升级前原文','text',1000,'needs_review',NULL,11,22,33)");
            db.execSQL("INSERT INTO event_candidates VALUES(9,7,0,'升级前日程',123000,456000,'Asia/Shanghai','教室','备注',17,1,987654)");
            db.setVersion(1);
        }
    }
    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory(new File(args[0]).toPath(), "snapshot-").toFile();
        Context liveContext = new Context(new File(base,"live"));
        fixture(liveContext);
        File snapshot = new File(base,"snapshot.db");
        Map<String,List<List<String>>> expected;
        try (TaskStore store = new TaskStore(liveContext)) {
            SQLiteDatabase db = store.getWritableDatabase(); // Production v1 -> current migration.
            try (Cursor cursor = db.rawQuery("SELECT uncertainty_level,calendar_event_id FROM event_candidates WHERE id=9", null)) {
                check(cursor.moveToFirst() && cursor.getInt(0)==2 && cursor.getLong(1)==987654, "actual upgrade retains calendar and derives uncertainty");
            }
            db.execSQL("UPDATE tasks SET image_path='image.png',cached_tokens=44,link_text='链接原文',link_fetched_at=5678,error_message='保留消息'");
            db.execSQL("UPDATE event_candidates SET category='task',all_day=1,end_auto_generated=1,due_at_millis=789000,completed_at_millis=890000,is_record=1,uncertainty_level=1");
            db.execSQL("INSERT INTO task_attachments(task_id,image_path,position) VALUES(7,'attachment.png',3)");
            db.execSQL("INSERT INTO task_files(task_id,stored_name,display_name,mime_type,size_bytes,position) VALUES(7,'file.pdf','附件.pdf','application/pdf',1234,2)");
            db.execSQL("INSERT INTO candidate_merges VALUES('merged-candidate','{}',7654321)");
            db.execSQL("INSERT INTO sync_task_map VALUES('stable-task',7,'baseline','clock')");
            db.execSQL("INSERT INTO sync_candidate_map VALUES(9,'stable-candidate')");
            db.execSQL("UPDATE sync_meta SET identity='original-namespace'");
            db.execSQL("UPDATE managed_zju_state SET enabled=1,generation=8,account='synthetic-account',capture_id=7");
            db.execSQL("INSERT INTO zju_homework_captures VALUES(7,'synthetic-account')");
            db.execSQL("INSERT INTO zju_homework_map VALUES('synthetic-account','remote-key',7,9,'stable-candidate',1,'{\"submitted\":true}',1,1)");
            db.execSQL("INSERT INTO zju_homework_history VALUES(3,'synthetic-account','历史','变更',12345,'{}','{}','remote-time')");
            db.execSQL("INSERT INTO zju_homework_deleted_tasks VALUES('deleted-task')");
            // Deleted IDs must remain reserved even after snapshot and restore.
            db.execSQL("INSERT INTO tasks(id,raw_text,source,created_at_millis,status) VALUES(999,'deleted','test',0,'ready')");
            db.execSQL("DELETE FROM tasks WHERE id=999");
            db.execSQL("INSERT INTO event_candidates(id,task_id,position,title,needs_confirmation) VALUES(888,7,1,'deleted',0)");
            db.execSQL("DELETE FROM event_candidates WHERE id=888");
            db.execSQL("INSERT INTO zju_homework_history VALUES(777,'synthetic-account','deleted','deleted',0,NULL,NULL,NULL)");
            db.execSQL("DELETE FROM zju_homework_history WHERE id=777");
            expected = contents(db);
            check(expected.size()==15, "all production tables including sequence covered");
            integrity(db);
            store.createSnapshot(snapshot);
            try (TaskStore snap = TaskStore.openSnapshot(liveContext,snapshot)) {
                check(!columns(db,"tasks").equals(columns(snap.getReadableDatabase(),"tasks")), "fixture exercises different task column order");
                check(!columns(db,"event_candidates").equals(columns(snap.getReadableDatabase(),"event_candidates")), "fixture exercises different candidate column order");
                check(expected.equals(contents(snap.getReadableDatabase())), "snapshot preserves every table and named field");
                integrity(snap.getReadableDatabase());
            }
            db.execSQL("UPDATE tasks SET raw_text='snapshot must stay independent'");
        }
        Context restoredContext = new Context(new File(base,"isolated-restore"));
        Files.copy(snapshot.toPath(),restoredContext.getDatabasePath("chrona.db").toPath());
        try (TaskStore restored = new TaskStore(restoredContext)) {
            SQLiteDatabase db = restored.getWritableDatabase();
            check(expected.equals(contents(db)), "isolated restored database equals pre-backup contents");
            integrity(db);
            boolean rejected = false;
            try { db.execSQL("UPDATE event_candidates SET uncertainty_level=3 WHERE id=9"); }
            catch (IllegalStateException invalid) { rejected = invalid.getMessage().contains("CHECK constraint failed"); }
            check(rejected && expected.equals(contents(db)), "restored CHECK constraint rejects invalid values without changes");
            check(restored.insertTask("new",null,"test",1)==1000, "task deleted-ID high water restored");
            db.execSQL("INSERT INTO event_candidates(task_id,position,title,needs_confirmation) VALUES(1000,0,'new',0)");
            try (Cursor cursor = db.rawQuery("SELECT id FROM event_candidates WHERE task_id=1000", null)) {
                check(cursor.moveToFirst() && cursor.getLong(0)==889, "candidate deleted-ID high water restored");
            }
            db.execSQL("INSERT INTO zju_homework_history(account,title,change,at_millis) VALUES('synthetic-account','new','new',1)");
            try (Cursor cursor = db.rawQuery("SELECT MAX(id) FROM zju_homework_history", null)) {
                check(cursor.moveToFirst() && cursor.getLong(0)==778, "history deleted-ID high water restored");
            }
        }
        System.out.println("Snapshot backup: "+checks+" checks passed; actual v1 upgrade, non-default fields, all tables, isolated restore, ID high water");
    }
}
