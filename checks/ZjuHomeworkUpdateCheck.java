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
    static void personal(Context c,long id,long execution,long event){try(TaskStore store=new TaskStore(c)){ContentValues v=new ContentValues();if(execution!=0){v.put("start_at_millis",execution);v.put("end_at_millis",execution+3_600_000L);}v.put("reminder_minutes_before",15);v.put("calendar_event_id",event);store.getWritableDatabase().update("event_candidates",v,"id=?",new String[]{Long.toString(id)});}}
    static void event(long id,long at,boolean own){CalendarStore.rows.put(id,new CalendarStore.EventRecord(new CalendarStore.EventInput("old",at,at+60000,"Asia/Shanghai",false,"old","",List.of(15)),own));}
    public static void main(String[] args)throws Exception{
        File directory=new File(args[0],"homework-"+System.nanoTime());Context c=new Context(directory);
        ZjuHomeworkStore managed=new ZjuHomeworkStore(c);managed.configure("account",true);long generation=managed.state().generation;
        Homework a=homework("a","A",DUE,false),b=homework("b","B",DUE,false);
        var initial=managed.apply("account",generation,List.of(a,b));check(initial.actionableAdded==2&&initial.actionableUpdated==0,"new unfinished actionable additions");
        check(managed.trackedCourses("account",generation).equals(java.util.Set.of("course")),"existing course is tracked for later terms");
        check(managed.trackedCourses("other-account",generation).isEmpty(),"other account course IDs never enter fetch");
        check(managed.trackedCourses("account",generation+1).isEmpty(),"stale generation cannot supply course IDs");
        List<EventCandidate> all;try(TaskStore store=new TaskStore(c)){all=store.getCandidates(managed.state().captureId);}
        long aid=all.get(0).id,bid=all.get(1).id;
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
            store.onUpgrade(store.getWritableDatabase(),12,13);
            check(row(c,aid).isHomework() && !row(c,ordinary).isHomework(), "existing managed homework upgrades without reclassifying ordinary tasks");
            check(row(c,aid).dueAtMillis.equals(DUE), "category upgrade preserves original deadline");
        }
        check(row(c,aid).calendarEventId!=null&&row(c,bid).calendarEventId!=null&&CalendarStore.rows.size()==2,"unfinished deadline homework automatically published");
        managed.apply("account",generation,List.of(a,b));check(CalendarStore.rows.size()==2,"unchanged refresh does not duplicate provider events");
        personal(c,aid,0,101);event(101,DUE,true);
        a=homework("a","A updated",DUE+100000,false);
        var changed=managed.apply("account",generation,List.of(a,b));
        check(changed.actionableAdded==0&&changed.actionableUpdated==1,"only actual changed unfinished row notifies");
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
        System.out.println("PASS: "+checks+" production homework upsert/actionable/calendar retry checks against real SQLite");
    }
}
