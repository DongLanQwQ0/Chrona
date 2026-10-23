package com.donglan.chrona;

import android.content.Context;
import android.content.ContentValues;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot.Homework;
import java.io.File;
import java.util.List;

public final class ZjuHomeworkUpdateCheck {
    static int checks;
    static final long DUE=2_000_000_000_000L;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static Homework homework(String key,String title,long due,Boolean submitted){return new Homework(key,"course",title,"description","https://example.test/item","version",due==0?null:due,submitted);}
    static EventCandidate row(Context c,long id){try(TaskStore store=new TaskStore(c)){return store.getCandidatesByIds(List.of(id)).get(0);}}
    static boolean unread(Context c,long id){try(TaskStore store=new TaskStore(c);var rows=store.getReadableDatabase().rawQuery("SELECT unread FROM zju_homework_map WHERE candidate_id=?",new String[]{Long.toString(id)})){return rows.moveToFirst()&&rows.getInt(0)==1;}}
    static void personal(Context c,long id,long execution,long event){try(TaskStore store=new TaskStore(c)){ContentValues v=new ContentValues();if(execution!=0){v.put("start_at_millis",execution);v.put("end_at_millis",execution+3_600_000L);}v.put("reminder_minutes_before",15);v.put("calendar_event_id",event);store.getWritableDatabase().update("event_candidates",v,"id=?",new String[]{Long.toString(id)});}}
    static void event(long id,long at,boolean own){CalendarStore.rows.put(id,new CalendarStore.EventRecord(new CalendarStore.EventInput("old",at,at+60000,"Asia/Shanghai",false,"old","",List.of(15)),own));}
    static long otherOwner(Context context,long event) {
        try(TaskStore store=new TaskStore(context)) {
            long task=store.insertTask("independent owner",null,"text",DUE);
            long id=store.appendCandidate(new EventCandidate(0,task,"independent owner",DUE,DUE+60000,
                    "Asia/Shanghai","other location","other description",15,false,null,"task",false));
            check(store.setCalendarEventId(id,task,event),"fixture second owner linked");
            return id;
        }
    }
    static void sharedOwners(File parent) {
        Context context=new Context(new File(parent,"shared-homework-"+System.nanoTime()));
        ZjuHomeworkStore managed=new ZjuHomeworkStore(context);managed.configure("shared-account",true);
        long generation=managed.state().generation;
        Homework source=homework("shared","Shared homework",DUE,false);
        managed.apply("shared-account",generation,List.of(source));
        EventCandidate a;try(TaskStore store=new TaskStore(context)){a=store.getCandidates(managed.state().captureId).get(0);}
        long old=a.calendarEventId,bid=otherOwner(context,old);
        personal(context,a.id,0,old);CalendarStore.rows.get(old).reminderMinutes=new java.util.ArrayList<>(List.of(15));
        CalendarStore.EventRecord before=CalendarStore.rows.get(old);
        source=homework("shared","Shared homework updated",DUE+600000,false);
        managed.apply("shared-account",generation,List.of(source));
        EventCandidate changed=row(context,a.id);long split=changed.calendarEventId;
        check(split!=old&&row(context,bid).calendarEventId==old,"school content update splits only school owner");
        check(CalendarStore.rows.get(old)==before&&before.input.title.equals("Shared homework")
                &&before.reminderMinutes.equals(List.of(15)),"school content update preserves second owner's old provider fields and reminders");
        check(CalendarStore.rows.get(split).input.title.equals("Shared homework updated")
                &&CalendarStore.rows.get(split).startMillis==DUE+600000,"school update writes latest content to split provider");
        check(ZjuHomeworkCalendar.pendingCount(context)==0,"shared school update commits and clears repair");

        long cid=otherOwner(context,split);CalendarStore.EventRecord splitBefore=CalendarStore.rows.get(split);
        source=homework("shared","Shared homework deadline removed",0,false);
        managed.apply("shared-account",generation,List.of(source));
        long detachedAlerts=row(context,a.id).calendarEventId;
        check(detachedAlerts!=split,"removed deadline suppresses only school owner's reminders via copy-on-write");
        check(CalendarStore.rows.get(detachedAlerts).reminderMinutes.isEmpty(),"removed deadline new provider has no obsolete alerts");
        check(row(context,cid).calendarEventId==split&&CalendarStore.rows.get(split)==splitBefore
                &&splitBefore.reminderMinutes.equals(List.of(15)),"removed deadline never reuses stale event ID to clear second owner's alerts");
        check(row(context,bid).calendarEventId==old&&before.reminderMinutes.equals(List.of(15)),"earlier independent owner also remains unchanged");
        check(ZjuHomeworkCalendar.pendingCount(context)==1,"removed deadline remains pending without valid interval");
        source=homework("shared","Shared homework restored",DUE+900000,false);
        managed.apply("shared-account",generation,List.of(source));
        check(row(context,a.id).calendarEventId==detachedAlerts&&CalendarStore.rows.get(detachedAlerts).reminderMinutes.equals(List.of(15)),"restored deadline updates A's own event and restores configured reminders");
        check(splitBefore.reminderMinutes.equals(List.of(15))&&row(context,cid).calendarEventId==split,"restored deadline leaves second owner intact");

        long did=otherOwner(context,detachedAlerts);
        try(TaskStore store=new TaskStore(context)) {
            var db=store.getWritableDatabase();
            db.execSQL("CREATE TABLE linked_commit_parent(id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TABLE linked_commit_child(parent_id INTEGER REFERENCES linked_commit_parent(id) DEFERRABLE INITIALLY DEFERRED)");
            db.execSQL("CREATE TRIGGER fail_shared_commit AFTER UPDATE OF calendar_event_id ON event_candidates "
                    +"WHEN NEW.id="+a.id+" AND NEW.calendar_event_id!=OLD.calendar_event_id BEGIN INSERT INTO linked_commit_child VALUES(1); END");
        }
        int count=CalendarStore.rows.size();CalendarStore.EventRecord original=CalendarStore.rows.get(detachedAlerts);
        source=homework("shared","Shared failed commit",DUE+1200000,false);
        managed.apply("shared-account",generation,List.of(source));
        check(ZjuHomeworkCalendar.pendingCount(context)==1,"actual SQLite COMMIT failure retains durable school repair");
        check(row(context,a.id).calendarEventId==detachedAlerts&&row(context,did).calendarEventId==detachedAlerts,"failed school relink commit retains both old associations");
        check(CalendarStore.rows.size()==count&&CalendarStore.rows.get(detachedAlerts)==original,"failed school relink commit cleans new provider, keeps existing provider");
        try(TaskStore store=new TaskStore(context)){store.getWritableDatabase().execSQL("DROP TRIGGER fail_shared_commit");}
        ZjuHomeworkCalendar.retryPending(context);
        long recovered=row(context,a.id).calendarEventId;
        check(recovered!=detachedAlerts&&ZjuHomeworkCalendar.pendingCount(context)==0,"school relink retries successfully after commit failure removed");
        check(CalendarStore.rows.get(recovered).title.equals("Shared failed commit")&&row(context,did).calendarEventId==detachedAlerts
                &&CalendarStore.rows.get(detachedAlerts)==original,"recovered school update preserves second owner");
    }
    public static void main(String[] args)throws Exception{
        File directory=new File(args[0],"homework-"+System.nanoTime());Context c=new Context(directory);
        ZjuHomeworkStore managed=new ZjuHomeworkStore(c);managed.configure("account",true);long generation=managed.state().generation;
        Homework a=homework("a","A",DUE,false),b=homework("b","B",DUE,false);
        var initial=managed.apply("account",generation,List.of(a,b));check(initial.actionableAdded==2&&initial.actionableUpdated==0,"new unfinished actionable additions");
        check(initial.newHomework.equals(List.of(a,b)),"new-work notice carries the actual committed titles, body and deadlines");
        check(managed.trackedCourses("account",generation).equals(java.util.Set.of("course")),"existing course is tracked for later terms");
        check(managed.trackedCourses("other-account",generation).isEmpty(),"other account course IDs never enter fetch");
        check(managed.trackedCourses("account",generation+1).isEmpty(),"stale generation cannot supply course IDs");
        List<EventCandidate> all;try(TaskStore store=new TaskStore(c)){all=store.getCandidates(managed.state().captureId);}
        long aid=all.get(0).id,bid=all.get(1).id;
        check(unread(c,aid)&&unread(c,bid),"new homework and unread marker commit together");
        managed.markSeen(aid);
        check(!unread(c,aid)&&unread(c,bid),"opening one new homework leaves other attention intact");
        managed.apply("account",generation,List.of(a,b));
        check(!unread(c,aid)&&unread(c,bid),"duplicate fetch never reopens read attention");
        check(row(c,aid).isHomework() && row(c,aid).isTask(), "homework is distinct but keeps task behavior");
        try(TaskStore store=new TaskStore(c)) {
            long now=DUE-86_400_000L;
            check(store.queryScheduleIds(new com.donglan.chrona.data.ScheduleQuery(3,"homework",0,"",null,null,now,java.time.ZoneId.of("Asia/Shanghai"))).size()==2,
                    "homework filter selects the distinct persisted category");
            check(store.queryScheduleIds(new com.donglan.chrona.data.ScheduleQuery(3,"task",0,"",null,null,now,java.time.ZoneId.of("Asia/Shanghai"))).isEmpty(),
                    "ordinary task category remains separate from homework");
            check(store.homeTodoSummary(now,java.time.ZoneId.of("Asia/Shanghai"),5).total==2,
                    "home todo summary includes homework");
            check(com.donglan.chrona.sync.TaskCodec.event(com.donglan.chrona.sync.TaskCodec.candidate(row(c,aid))).isHomework(),
                    "homework category and deadline survive sync codec");
        }
        try(TaskStore store=new TaskStore(c)) {
            long ordinaryCapture=store.insertTask("personal",null,"text",DUE);
            long ordinary=store.appendCandidate(new EventCandidate(0,ordinaryCapture,"personal",null,null,"Asia/Shanghai","","",null,false,null,"task",false));
            store.getWritableDatabase().execSQL("UPDATE event_candidates SET category='task' WHERE id=?",new Object[]{aid});
            // Reconstruct the v12 schema before exercising every subsequent migration.
            for (String column : new String[]{"repeat_rule", "repeat_exceptions", "repeat_stopped_at"})
                store.getWritableDatabase().execSQL("ALTER TABLE event_candidates DROP COLUMN " + column);
            store.getWritableDatabase().execSQL("ALTER TABLE zju_homework_map DROP COLUMN unread");
            store.onUpgrade(store.getWritableDatabase(),12,15);
            check(row(c,aid).isHomework() && !row(c,ordinary).isHomework(), "existing managed homework upgrades without reclassifying ordinary tasks");
            check(row(c,aid).dueAtMillis.equals(DUE), "category upgrade preserves original deadline");
            check(!unread(c,aid)&&!unread(c,bid),"upgrading existing work does not fabricate new-work badges");
        }
        check(row(c,aid).calendarEventId!=null&&row(c,bid).calendarEventId!=null&&CalendarStore.rows.size()==2,"unfinished deadline homework automatically published");
        check(managed.apply("account",generation,List.of(a,b)).newHomework.isEmpty(),"unchanged refresh never reopens or extends a new-work notice");check(CalendarStore.rows.size()==2,"unchanged refresh does not duplicate provider events");
        personal(c,aid,0,101);event(101,DUE,true);
        a=homework("a","A updated",DUE+100000,false);
        var changed=managed.apply("account",generation,List.of(a,b));
        check(changed.actionableAdded==0&&changed.actionableUpdated==1,"only actual changed unfinished row notifies");
        check(changed.newHomework.isEmpty(),"edited existing work is not announced as new homework");
        check(CalendarStore.rows.get(101L).input.startMillis==DUE+100000&&CalendarStore.rows.get(101L).input.endMillis==DUE+160000,"deadline-only provider point follows new deadline");
        check(CalendarStore.rows.get(101L).input.title.equals("A updated")&&CalendarStore.rows.get(101L).reminderMinutes.equals(List.of(15)),"provider title and personal reminder updated");
        check(row(c,aid).id==aid&&row(c,aid).calendarEventId==101,"candidate identity and calendar link retained");
        personal(c,bid,DUE-3_600_000L,102);event(102,DUE-3_600_000L,true);
        b=homework("b","B updated",DUE+500000,false);managed.apply("account",generation,List.of(a,b));
        check(CalendarStore.rows.get(102L).input.startMillis==DUE-3_600_000L&&CalendarStore.rows.get(102L).input.endMillis==DUE,"personal execution interval is preserved");
        CalendarStore.fail=true;a=homework("a","A retry",DUE+200000,false);managed.apply("account",generation,List.of(a,b));
        check(ZjuHomeworkCalendar.pendingCount(c)==1,"provider failure remains persisted pending");
        CalendarStore.fail=false;ZjuHomeworkCalendar.retryPending(c);check(ZjuHomeworkCalendar.pendingCount(c)==0&&CalendarStore.rows.get(101L).input.title.equals("A retry"),"later provider recovery repairs latest content");
        CalendarStore.permission=false;a=homework("a","A permission",DUE+300000,false);managed.apply("account",generation,List.of(a,b));
        check(ZjuHomeworkCalendar.pendingCount(c)==1,"permission denial retains repair");CalendarStore.permission=true;ZjuHomeworkCalendar.retryPending(c);check(ZjuHomeworkCalendar.pendingCount(c)==0,"permission recovery repairs");
        try(TaskStore store=new TaskStore(c)){store.setCandidateCompleted(aid,managed.state().captureId,true);}
        a=homework("a","A completed update",DUE+400000,false);var doneChange=managed.apply("account",generation,List.of(a,b));
        check(doneChange.updated==1&&doneChange.actionableUpdated==0,"completed A change plus unchanged open B never notifies");
        check(row(c,aid).completedAtMillis!=null&&CalendarStore.rows.get(101L).reminderMinutes.isEmpty(),"manual completion retained with provider alerts suppressed");
        var submitted=managed.apply("account",generation,List.of(a,b,homework("c","C",DUE,true)));
        check(submitted.added==1&&submitted.completed==1&&submitted.actionableAdded==0,"new submitted work never actionable");
        check(submitted.newHomework.isEmpty(),"already-submitted imports do not produce a new-work notice");
        a=homework("a","A no deadline",0,false);managed.apply("account",generation,List.of(a,b));
        check(ZjuHomeworkCalendar.pendingCount(c)==1&&CalendarStore.rows.get(101L).reminderMinutes.isEmpty(),"removed deadline preserves history without obsolete alarms and stays pending");
        try(TaskStore store=new TaskStore(c)){check(!store.allowsReminder(aid),"completion retry cannot restore reminder on removed deadline");}
        int updates=CalendarStore.updates;personal(c,aid,0,103);event(103,DUE,false);a=homework("a","A foreign",DUE+600000,false);managed.apply("account",generation,List.of(a,b));
        check(CalendarStore.updates==updates&&CalendarStore.rows.get(103L).input.title.equals("old"),"non-Chrona event never overwritten");
        CalendarStore.permission=false;
        managed.apply("account",generation,List.of(homework("permission","Permission",DUE,false),homework("undated","Undated",0,false)));
        check(ZjuHomeworkCalendar.pendingCount(c)==1,"new homework without permission is durably queued, undated homework is not");
        int count=CalendarStore.rows.size();CalendarStore.permission=true;ZjuHomeworkCalendar.retryPending(c);
        check(CalendarStore.rows.size()==count+1&&ZjuHomeworkCalendar.pendingCount(c)==0,"permission grant publishes cached homework without fetching again");
        CalendarStore.permission=false;managed.apply("account",generation,List.of(homework("disabled","Disabled",DUE,false)));
        managed.configure("account",false);CalendarStore.permission=true;count=CalendarStore.rows.size();ZjuHomeworkCalendar.retryPending(c);
        check(managed.trackedCourses("account",managed.state().generation).isEmpty(),"disabled source supplies no tracked courses");
        check(CalendarStore.rows.size()==count,"disabled homework source cannot publish pending events");
        try (TaskStore store = new TaskStore(c)) {
            EventCandidate current = row(c, bid);
            EventCandidate personalEdit = new EventCandidate(current.id, current.taskId, current.title,
                    DUE-7_200_000L, DUE-3_600_000L, current.timeZoneId, "自习室", current.description,
                    20, current.needsConfirmation, current.calendarEventId, current.category,
                    false, false, current.uncertaintyLevel, current.dueAtMillis, current.completedAtMillis, false);
            check(store.updateCandidate(personalEdit), "managed homework allows personal execution/location/reminder edits");
            for (int field = 0; field < 4; field++) {
                EventCandidate forbidden = new EventCandidate(current.id, current.taskId,
                        field==0 ? "renamed" : current.title, current.startAtMillis, current.endAtMillis,
                        current.timeZoneId, current.location, field==1 ? "changed" : current.description,
                        current.reminderMinutesBefore, current.needsConfirmation, current.calendarEventId,
                        field==2 ? "event" : current.category, false, false, current.uncertaintyLevel,
                        field==3 ? DUE+9 : current.dueAtMillis, current.completedAtMillis, false);
                boolean rejected = false;
                try { store.updateCandidate(forbidden); } catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "source-owned field cannot be modified: " + field);
            }
            boolean rejected = false;
            try { store.updateCandidateDescription(current.id, current.taskId, "overwrite"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "notes-only route cannot bypass source ownership");
            long submittedId = store.getCandidates(managed.state().captureId).stream()
                    .filter(item -> item.title.equals("C")).findFirst().get().id;
            rejected = false;
            try { store.setCandidateCompleted(submittedId, managed.state().captureId, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "platform submission cannot be reset by local completion");
            check(store.setCandidateCompleted(bid, current.taskId, true)
                    && store.setCandidateCompleted(bid, current.taskId, false), "local completion remains reversible for unsubmitted homework");
            rejected = false;
            try { store.replaceCandidates(current.taskId, List.of()); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected && row(c, bid) != null, "reparse cannot wipe a managed homework capture");
        }
        sharedOwners(new File(args[0]));
        System.out.println("PASS: "+checks+" production homework upsert/actionable/calendar retry checks against real SQLite");
    }
}
