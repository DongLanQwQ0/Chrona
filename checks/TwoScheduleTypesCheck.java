package com.donglan.chrona;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.*;
import com.donglan.chrona.sync.TaskCodec;
import org.json.JSONObject;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Production persistence/sync codec over the real SQLite bridge; never user data. */
public final class TwoScheduleTypesCheck {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static EventCandidate value(long task,Long start,Long end,Long due,Long done,boolean record){
        return new EventCandidate(0,task,"原始标题",start,end,"Asia/Shanghai","原始地点","原始备注",10,false,null,
                record?EventCategory.EVENT:EventCategory.TASK,false,false,0,due,done,record);
    }
    public static void main(String[] args)throws Exception{
        Context context=new Context(new File(args[0],"types-"+System.nanoTime()));long now=System.currentTimeMillis();
        long task,id,record;
        try(TaskStore store=new TaskStore(context)){
            task=store.insertTask("完整原文",null,"source",now);store.updateStatus(task,TaskRecord.READY,null);
            id=store.appendCandidate(value(task,null,null,now-1000,null,false));
            record=store.appendCandidate(value(task,null,null,null,null,true));
            EventCandidate original=store.getCandidatesByIds(List.of(id)).get(0);
            check(original.dueAtMillis==now-1000&&original.startAtMillis==null&&original.point(),"deadline remains exact and does not invent execution time");
            check(original.taskState(now).equals("已逾期"),"overdue pending task");
            ScheduleQuery pending=new ScheduleQuery(0,null,0,"",null,null,now,ZoneId.systemDefault());
            check(store.queryScheduleIds(pending).equals(List.of(id)),"overdue task remains pending, record excluded");
            check(store.setCandidateCompleted(id,task,true),"complete task");
            EventCandidate done=store.getCandidatesByIds(List.of(id)).get(0);
            check(done.completed()&&done.completedAtMillis>0&&done.dueAtMillis.equals(original.dueAtMillis),"completion persists separately from deadline");
            check(store.queryScheduleIds(pending).isEmpty(),"completed task leaves pending list");
            check(store.queryScheduleIds(new ScheduleQuery(2,null,0,"",null,null,now,ZoneId.systemDefault())).equals(List.of(id)),"completed task enters history");
            check(!store.setCandidateCompleted(record,task,true),"records are not tasks");
            EventCandidate portable=TaskCodec.event(TaskCodec.candidate(done));
            check(portable.completedAtMillis.equals(done.completedAtMillis)&&portable.dueAtMillis.equals(done.dueAtMillis)&&portable.startAtMillis==null,"new state survives sync");
            check(TaskCodec.event(TaskCodec.candidate(store.getCandidatesByIds(List.of(record)).get(0))).isRecord,"record survives sync");
            check(store.calendarCandidates(now-2000,now+2000,now-2000,now+2000).isEmpty(),"completed tasks and records do not occupy calendar");
        }
        try(TaskStore store=new TaskStore(context)){
            check(store.getCandidatesByIds(List.of(id)).get(0).completed(),"completion survives reopening");
            check(store.setCandidateCompleted(id,task,false),"restore pending task");
            check(store.getCandidatesByIds(List.of(id)).get(0).taskState(now).equals("已逾期"),"restore keeps deadline");
            long planned=store.appendCandidate(value(task,now-3600000,now-1800000,now+3600000,null,false));
            check(store.nextReminderCandidates(now,Set.of()).stream().anyMatch(e->e.id==planned&&e.reminderAnchor()==now+3600000),"deadline reminder is not lost behind past execution slot");
        }
        Context legacy=new Context(new File(args[0],"legacy-types-"+System.nanoTime()));
        try(TaskStore store=new TaskStore(legacy)){
            long source=store.insertTask("升级前完整原文",null,"source",now);store.updateStatus(source,TaskRecord.NEEDS_REVIEW,null);
            long deadline=store.appendCandidate(value(source,now-60000,now,null,null,false));
            long note=store.appendCandidate(value(source,now,now+900000,null,null,true));
            SQLiteDatabase db=store.getWritableDatabase();
            JSONObject old=TaskCodec.candidate(store.getCandidatesByIds(List.of(deadline)).get(0));
            old.put("category","deadline");old.remove("dueAt");old.remove("completedAt");old.remove("record");
            JSONObject archive=new JSONObject().put("source",new JSONObject().put("candidate",old));
            db.execSQL("INSERT INTO candidate_merges(source_candidate,record) VALUES(?,?)",new Object[]{"legacy-archive",archive.toString()});
            db.execSQL("UPDATE event_candidates SET category='deadline',calendar_event_id=71 WHERE id=?",new Object[]{deadline});
            db.execSQL("UPDATE event_candidates SET category='note',calendar_event_id=72 WHERE id=?",new Object[]{note});
            db.execSQL("DROP INDEX candidates_by_due");db.execSQL("DROP INDEX candidates_by_anchor");
            for(String column:new String[]{"due_at_millis","completed_at_millis","is_record"})db.execSQL("ALTER TABLE event_candidates DROP COLUMN "+column);
            db.beginTransaction();try{store.onUpgrade(db,10,11);db.setTransactionSuccessful();}finally{db.endTransaction();}
            EventCandidate upgraded=store.getCandidatesByIds(List.of(deadline)).get(0),memo=store.getCandidatesByIds(List.of(note)).get(0);
            check(upgraded.isTask()&&upgraded.startAtMillis==null&&upgraded.dueAtMillis==now&&upgraded.calendarEventId==71,"old deadline converted without shifting due time or association");
            check(memo.isRecord&&memo.calendarEventId==72&&memo.title.equals("原始标题")&&memo.description.equals("原始备注"),"old memo retained as inbox record with identity and content");
            check(store.getTask(source).rawText.equals("升级前完整原文"),"raw source retained");
            try(Cursor archived=db.rawQuery("SELECT record FROM candidate_merges WHERE source_candidate='legacy-archive'",null)){
                check(archived.moveToNext(),"archived merge source retained");
                EventCandidate converted=TaskCodec.event(new JSONObject(archived.getString(0)).getJSONObject("source").getJSONObject("candidate"));
                check(converted.isTask()&&converted.dueAtMillis==now&&converted.title.equals("原始标题"),"archived merge source remains valid for sync");
            }
            File snapshot=new File(legacy.getFilesDir(),"snapshot.db");store.createSnapshot(snapshot);
            try(TaskStore saved=TaskStore.openSnapshot(legacy,snapshot)){
                EventCandidate restored=saved.getCandidatesByIds(List.of(deadline)).get(0);
                check(restored.dueAtMillis==now&&restored.startAtMillis==null&&!restored.isRecord,"snapshot column order matches upgraded database");
                check(saved.getCandidatesByIds(List.of(note)).get(0).isRecord,"snapshot retains record flag");
            }
        }
        System.out.println("Two schedule types: "+checks+" checks passed (actual TaskStore/SQLite, completion/reopen, precise deadline, sync and upgraded backup)");
    }
}
