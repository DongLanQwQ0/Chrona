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

    static long time(LocalDate date,int hour,int minute,ZoneId zone) {
        return date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
    }

    static long add(TaskStore store,long task,String title,String category,Long start,Long end,
            Long due,Long completed,boolean record,boolean allDay) {
        return store.appendCandidate(new EventCandidate(0,task,title,start,end,
                allDay?"UTC":"Asia/Shanghai","","",10,false,null,category,allDay,false,
                EventCandidate.CERTAIN,due,completed,record));
    }

    static List<Long> ids(List<EventCandidate> values) {
        return values.stream().map(candidate->candidate.id).toList();
    }

    static void homeTaskChecks(File base) throws Exception {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalDate today=LocalDate.of(2026,10,15);
        long now=time(today,12,0,zone);
        Context context=new Context(new File(base,"home-tasks-"+System.nanoTime()));
        try(TaskStore store=new TaskStore(context)) {
            HomeTodoSummary empty=store.homeTodoSummary(now,zone,3);
            check(empty.total==0&&empty.todayDue==0&&empty.overdue==0&&empty.unscheduled==0
                    &&empty.focus.isEmpty(),"empty task summary has zero counts and no invented focus");
            long source=store.insertTask("首页待办的完整来源原文",null,"source",now);
            store.updateStatus(source,TaskRecord.READY,null);
            List<Long> old=new ArrayList<>();
            for(int day=5;day>=1;day--) {
                old.add(add(store,source,"旧逾期待办 "+day,EventCategory.TASK,null,null,
                        time(today.minusDays(day),18,0,zone),null,false,false));
            }
            long dueToday=add(store,source,"仅今日截止",EventCategory.TASK,null,null,
                    time(today,19,0,zone),null,false,false);
            long overdueToday=add(store,source,"今日已过截止",EventCategory.TASK,null,null,
                    time(today,9,0,zone),null,false,false);
            long unscheduled=add(store,source,"完全无时间待办",EventCategory.TASK,null,null,
                    null,null,false,false);
            long futureDue=add(store,source,"未来仅截止",EventCategory.TASK,null,null,
                    time(today.plusDays(1),18,0,zone),null,false,false);
            long planned=add(store,source,"明确执行时段",EventCategory.TASK,
                    time(today,13,0,zone),time(today,14,0,zone),null,null,false,false);
            long point=add(store,source,"明确执行时点",EventCategory.TASK,
                    time(today,15,0,zone),null,null,null,false,false);
            long finishedExecution=add(store,source,"执行结束仍未截止",EventCategory.TASK,
                    time(today,8,0,zone),time(today,9,0,zone),time(today.plusDays(1),17,0,zone),null,false,false);
            long ongoing=add(store,source,"进行中活动",EventCategory.EVENT,
                    time(today,11,0,zone),time(today,12,30,zone),null,null,false,false);
            long nextEvent=add(store,source,"未来活动",EventCategory.EVENT,
                    time(today,17,0,zone),time(today,18,0,zone),null,null,false,false);
            long allDay=add(store,source,"今日全天活动",EventCategory.EVENT,
                    today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                    today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                    null,null,false,true);
            long ended=add(store,source,"已结束活动",EventCategory.EVENT,
                    time(today,7,0,zone),time(today,8,0,zone),null,null,false,false);
            long completed=add(store,source,"已完成今日待办",EventCategory.TASK,
                    time(today,16,0,zone),null,time(today,17,0,zone),now-1,false,false);
            long memo=add(store,source,"记录不占安排",EventCategory.EVENT,
                    time(today,16,0,zone),time(today,17,0,zone),null,null,true,false);

            List<Long> upcoming=ids(store.queryHomeUpcoming(now,zone,5));
            check(upcoming.equals(List.of(allDay,ongoing,planned,point,nextEvent)),
                    "five older overdue tasks cannot displace real upcoming or ongoing arrangements");
            check(Collections.disjoint(upcoming,old)&&!upcoming.contains(dueToday)
                    &&!upcoming.contains(overdueToday)&&!upcoming.contains(futureDue),
                    "bounded first five arrangements exclude expired tasks and later default intervals");
            check(ids(store.queryHomeUpcoming(now,zone,100)).containsAll(List.of(dueToday,futureDue)),
                    "ordinary due-only tasks enter home through the final half-hour default");
            check(!upcoming.contains(finishedExecution)&&!upcoming.contains(ended)
                    &&!upcoming.contains(completed)&&!upcoming.contains(memo),
                    "ended execution, ended events, completed tasks and records stay outside next events");
            check(store.queryHomeUpcoming(now,zone,2).size()==2,"upcoming query is bounded at the database");
            EventCandidate deadline=store.getCandidatesByIds(List.of(dueToday)).get(0);
            check(HomeTimelineEntry.hasUpcomingExecution(deadline,now,zone)
                    &&new HomeTimelineEntry(deadline).timestampMillis==deadline.dueAtMillis-1800000,
                    "home presentation uses the derived half-hour interval");
            check(deadline.startAtMillis==null&&deadline.endAtMillis==null,
                    "persisted due-only source fields remain empty");
            EventCandidate pointCandidate=store.getCandidatesByIds(List.of(point)).get(0);
            check(HomeTimelineEntry.hasUpcomingExecution(pointCandidate,now,zone)
                    &&HomeTimelineEntry.executionEnd(pointCandidate)==pointCandidate.startAtMillis,
                    "explicit task point stays exact instead of borrowing its deadline or inventing an interval");
            EventCandidate allDayCandidate=store.getCandidatesByIds(List.of(allDay)).get(0);
            check(new HomeTimelineEntry(allDayCandidate).timestampMillis==today.atStartOfDay(zone).toInstant().toEpochMilli(),
                    "all-day UTC storage maps to the displayed local day");

            HomeTodoSummary summary=store.homeTodoSummary(now,zone,3);
            check(summary.total==12&&summary.todayDue==4&&summary.overdue==6&&summary.unscheduled==1,
                    "summary counts all unfinished tasks, today's deadlines, overdue work and completely undated tasks");
            check(ids(summary.focus).equals(List.of(overdueToday,planned,point)),
                    "focus is bounded and prioritizes today's deadlines before older overdue work");
            HomeTodoSummary smaller=store.homeTodoSummary(now,zone,1);
            check(smaller.total==12&&smaller.focus.size()==1&&smaller.unscheduled==1,
                    "focus limit never truncates aggregate counts or the undated entry");
            check(new HashSet<>(store.queryScheduleIds(new ScheduleQuery(3,EventCategory.TASK,0,"",
                    null,null,now,zone,ScheduleQuery.TODO_TODAY_DUE))).equals(Set.of(dueToday,overdueToday,planned,point)),
                    "today scope uses explicit deadlines before execution end or start, excluding completed tasks");
            List<Long> overdue=store.queryScheduleIds(new ScheduleQuery(3,EventCategory.TASK,0,"",
                    null,null,now,zone,ScheduleQuery.TODO_OVERDUE));
            Set<Long> expectedOverdue=new HashSet<>(old);expectedOverdue.add(overdueToday);
            check(new HashSet<>(overdue).equals(expectedOverdue),"overdue scope includes old and same-day expired deadlines");
            check(store.queryScheduleIds(new ScheduleQuery(1,EventCategory.TASK,0,"",null,null,now,zone))
                    .equals(List.of(unscheduled)),"unscheduled summary routes to the existing undated task predicate");
            Set<Long> open=new HashSet<>(old);
            open.addAll(List.of(dueToday,overdueToday,unscheduled,futureDue,planned,point,finishedExecution));
            check(new HashSet<>(store.queryScheduleIds(new ScheduleQuery(3,EventCategory.TASK,0,"",
                    null,null,now,zone,ScheduleQuery.TODO_OPEN))).equals(open),
                    "all unfinished task route includes undated work and excludes history and records");
            List<EventCandidate> deadlinePage=store.querySchedulePage(new ScheduleQuery(3,EventCategory.TASK,0,"",
                    null,null,now,zone,ScheduleQuery.TODO_TODAY_DUE),true,0);
            check(ids(deadlinePage).equals(List.of(overdueToday,planned,point,dueToday)),"deadline scope sorts by effective deadline rather than execution start");
            check(store.setCandidateCompleted(dueToday,source,true),"complete task from summary");
            HomeTodoSummary after=store.homeTodoSummary(now,zone,3);
            check(after.total==11&&after.todayDue==3&&after.overdue==6&&after.unscheduled==1,
                    "completion removes only the finished task from the relevant aggregate counts");
            long earlyDeadline=add(store,source,"明日执行但今日早截止",EventCategory.TASK,
                    time(today.plusDays(1),9,0,zone),time(today.plusDays(1),10,0,zone),
                    time(today,8,0,zone),null,false,false);
            long lateDeadline=add(store,source,"昨日执行但今日晚截止",EventCategory.TASK,
                    time(today.minusDays(1),9,0,zone),time(today.minusDays(1),10,0,zone),
                    time(today,20,0,zone),null,false,false);
            List<EventCandidate> differingExecutionDates=store.querySchedulePage(new ScheduleQuery(3,
                    EventCategory.TASK,0,"",null,null,now,zone,ScheduleQuery.TODO_TODAY_DUE),true,0);
            check(ids(differingExecutionDates).equals(List.of(earlyDeadline,overdueToday,planned,point,lateDeadline)),
                    "deadline filtering and sort remain due-based when execution dates disagree or run in reverse order");
            long homework=add(store,source,"课程作业",EventCategory.HOMEWORK,null,null,time(today,12,30,zone),null,false,false);
            long plannedHomework=add(store,source,"已安排作业",EventCategory.HOMEWORK,time(today,14,0,zone),time(today,15,0,zone),time(today,23,0,zone),null,false,false);
            long undatedHomework=add(store,source,"无日期作业",EventCategory.HOMEWORK,null,null,null,null,false,false);
            long overdueHomework=add(store,source,"过期作业",EventCategory.HOMEWORK,null,null,now-1,null,false,false);
            List<Long> homeworkUpcoming=ids(store.queryHomeUpcoming(now,zone,30));
            EventCandidate hw=store.getCandidatesByIds(List.of(homework)).get(0),plannedHw=store.getCandidatesByIds(List.of(plannedHomework)).get(0);
            check(homeworkUpcoming.contains(homework)&&homeworkUpcoming.contains(plannedHomework),"dated homework appears in home query");
            check(!homeworkUpcoming.contains(undatedHomework)&&!homeworkUpcoming.contains(overdueHomework),"undated and expired deadlines stay in task management");
            check(new HomeTimelineEntry(hw).timestampMillis==hw.dueAtMillis&&HomeTimelineEntry.deadlineOnly(hw),"deadline milestone uses due timestamp without inventing execution");
            check(HomeTimelineEntry.hasUpcomingTime(hw,now,zone)&&!HomeTimelineEntry.hasUpcomingExecution(hw,now,zone),"deadline milestone remains distinct from execution");
            check(new HomeTimelineEntry(plannedHw).timestampMillis==plannedHw.startAtMillis&&!HomeTimelineEntry.deadlineOnly(plannedHw),"execution wins when homework has both times");
            check(Collections.frequency(homeworkUpcoming,plannedHomework)==1&&hw.startAtMillis==null,"one entry per homework and no persisted execution mutation");
            store.setCandidateCompleted(homework,source,true);
            check(!ids(store.queryHomeUpcoming(now,zone,30)).contains(homework),"completed homework leaves home timeline");
            Set<Long> unwritten=new HashSet<>(store.queryScheduleIds(new ScheduleQuery(3,null,3,"",null,null,now,zone)));
            check(unwritten.contains(plannedHomework)&&unwritten.contains(futureDue)&&unwritten.contains(undatedHomework),
                    "unwritten partition includes certain homework, dated and undated tasks without requiring AI confirmation");
            store.setCalendarEventIdIfUnlinked(plannedHomework,source,9001L);
            check(!store.queryScheduleIds(new ScheduleQuery(3,null,3,"",null,null,now,zone)).contains(plannedHomework)
                    &&store.queryScheduleIds(new ScheduleQuery(3,null,2,"",null,null,now,zone)).contains(plannedHomework),
                    "successful publication leaves unwritten and enters written partition");
        }
    }

    static void recordSearchChecks(File base) throws Exception {
        Context context=new Context(new File(base,"record-search-"+System.nanoTime()));
        long source,record;
        String raw="完整来源原文始终独立保留";
        String title="编辑后标题 ÄÜБ %_\\";
        String body="只在记录正文里的检索词";
        try(TaskStore store=new TaskStore(context)) {
            source=store.insertTask(raw,null,"source",1000L);
            store.updateStatus(source,TaskRecord.READY,null);
            record=store.appendCandidate(value(source,null,null,null,null,true));
            long event=store.appendCandidate(new EventCandidate(0,source,"活动原名",null,null,"UTC","",
                    "活动原备注",null,true));
            long other=store.insertTask("百分号通配陷阱",null,"source",2000L);
            store.updateStatus(other,TaskRecord.READY,null);
            store.appendCandidate(new EventCandidate(0,other,"不含相同文字",null,null,"UTC","","普通内容",null,true));
            EventCandidate edited=new EventCandidate(record,source,title,null,null,"UTC","",body,null,false,
                    null,EventCategory.EVENT,false,false,EventCandidate.CERTAIN,null,null,true);
            check(store.updateCandidate(edited),"record editor persists through production updateCandidate");
            check(store.getTask(source).rawText.equals(raw),"editing record title and body never replaces captured source text");
            EventCandidate saved=store.getCandidatesByIds(List.of(record)).get(0);
            check(saved.id==record&&saved.taskId==source&&saved.isRecord&&saved.title.equals(title)
                    &&saved.description.equals(body),"record edit preserves stable identity, record type and independent content");
            check(store.queryInboxIds(new InboxQuery(0,"record","编辑后标题"),true).equals(List.of(source)),
                    "actual inbox query finds a record by edited candidate title");
            check(store.queryInboxIds(new InboxQuery(0,"record","记录正文里的"),true).equals(List.of(source)),
                    "actual inbox query finds a record by edited candidate body");
            check(store.queryInboxIds(new InboxQuery(0,null,"äüб"),true).equals(List.of(source)),
                    "candidate search keeps Java Unicode case folding");
            check(store.queryInboxIds(new InboxQuery(0,null,"%_\\"),true).equals(List.of(source)),
                    "candidate search treats percent, underscore and backslash literally");
            check(store.queryInboxIds(new InboxQuery(0,null,"原始标题"),true).isEmpty(),
                    "old record title is no longer searchable when absent from the source");
            check(store.queryInboxIds(new InboxQuery(0,null,raw),true).equals(List.of(source)),
                    "captured source remains independently searchable after record editing");
            EventCandidate editedEvent=new EventCandidate(event,source,"活动编辑后标题",null,null,"UTC","",
                    "活动备注被编辑后也能搜到",null,true);
            check(store.updateCandidate(editedEvent),"non-record candidate editing uses the same persistence contract");
            check(store.queryInboxIds(new InboxQuery(0,null,"活动备注被编辑"),true).equals(List.of(source)),
                    "candidate descriptions from non-record schedules also feed inbox search");
            store.appendCandidate(new EventCandidate(0,source,title,null,null,"UTC","",body,null,true));
            check(store.queryInboxIds(new InboxQuery(0,null,"编辑后标题"),true).equals(List.of(source)),
                    "multiple matching candidates return their capture once, without join duplicates");
            check(store.getTask(source).rawText.equals(raw),"all candidate saves preserve the original capture");
        }
        try(TaskStore store=new TaskStore(context)) {
            check(store.getTask(source).rawText.equals(raw)
                    &&store.getCandidatesByIds(List.of(record)).get(0).description.equals(body),
                    "reopening retains independent source and record body");
            check(store.queryInboxIds(new InboxQuery(0,"record",body),true).equals(List.of(source)),
                    "edited record search survives database reopening");
        }
    }

    static void effectiveDeadlineChecks(File base) throws Exception {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalDate today=LocalDate.of(2026,10,18);
        long now=time(today,12,0,zone), start=now-3_600_000L, end=now+600_000L, due=now+1_200_000L;
        for(String category:List.of(EventCategory.TASK,EventCategory.HOMEWORK)) {
            for(Long[] times:new Long[][]{{start,end,due},{start,end,null},{start,null,null},{null,null,null}}) {
                EventCandidate item=new EventCandidate(0,1,"截止优先级",times[0],times[1],"Asia/Shanghai","","",0,false,
                        null,category,false,false,0,times[2],null,false);
                Long expected=times[2]!=null?times[2]:times[1]!=null?times[1]:times[0];
                check(Objects.equals(item.effectiveDueAtMillis(),expected)&&Objects.equals(item.reminderAnchor(),expected),"task/homework deadline and reminder priorities agree");
                check(item.taskState(now).equals(expected!=null&&expected<now?"已逾期":"待完成"),"derived deadline determines pending state");
                check(Objects.equals(item.dueAtMillis,times[2])&&Objects.equals(item.anchor(),times[0]!=null?times[0]:times[2]),"derived deadline never mutates official due or execution anchor");
            }
        }
        EventCandidate event=new EventCandidate(0,1,"活动",start,end,"Asia/Shanghai","","",0,false,
                null,EventCategory.EVENT,false,false,0,due,null,false);
        check(event.effectiveDueAtMillis()==null&&event.reminderAnchor().equals(start)&&event.calendarStart().equals(start),"event execution and reminder anchors remain unchanged");
        check(value(1,start,end,null,now,false).taskState(now).equals("已完成"),"completion wins over derived deadline");
        Context context=new Context(new File(base,"effective-deadline-"+System.nanoTime()));
        try(TaskStore store=new TaskStore(context)) {
            long source=store.insertTask("学校截止留空",null,"source",now);
            store.updateStatus(source,TaskRecord.READY,null);
            long official=add(store,source,"官方截止",EventCategory.HOMEWORK,start,end,due,null,false,false);
            long fallbackEnd=add(store,source,"个人安排结束",EventCategory.HOMEWORK,start,end,null,null,false,false);
            long fallbackStart=add(store,source,"个人安排时点",EventCategory.HOMEWORK,now+300_000L,null,null,null,false,false);
            long expired=add(store,source,"已过安排结束",EventCategory.TASK,start,now-600_000L,null,null,false,false);
            long undated=add(store,source,"无日期",EventCategory.HOMEWORK,null,null,null,null,false,false);
            long done=add(store,source,"已完成",EventCategory.HOMEWORK,start,end,null,now,false,false);
            long activity=add(store,source,"活动不算待办",EventCategory.EVENT,start,end,null,null,false,false);
            check(ids(store.querySchedulePage(new ScheduleQuery(3,EventCategory.HOMEWORK,0,"",null,null,now,zone,ScheduleQuery.TODO_TODAY_DUE),true,0))
                    .equals(List.of(fallbackStart,fallbackEnd,official)),"SQLite today filter and order use all three deadline priorities");
            check(store.queryScheduleIds(new ScheduleQuery(3,null,0,"",null,null,now,zone,ScheduleQuery.TODO_OVERDUE)).equals(List.of(expired)),"SQLite overdue filter excludes completed, undated and event rows");
            check(ids(store.querySchedulePage(new ScheduleQuery(3,EventCategory.HOMEWORK,0,"",null,null,now,zone),true,0))
                    .equals(List.of(fallbackStart,fallbackEnd,done,official,undated)),"homework sorting uses effective deadline, stable IDs and undated last");
            HomeTodoSummary summary=store.homeTodoSummary(now,zone,10);
            check(summary.total==5&&summary.todayDue==4&&summary.overdue==1&&summary.unscheduled==1,"summary counts fallback dates without counting events or completed tasks");
            check(ids(summary.focus).equals(List.of(expired,fallbackStart,fallbackEnd,official,undated)),"summary focus follows effective deadlines");
            List<Long> reminders=ids(store.nextReminderCandidates(now,Set.of()));
            check(reminders.containsAll(List.of(fallbackStart,fallbackEnd,official,activity))&&!reminders.contains(done)&&!reminders.contains(undated)&&!reminders.contains(expired),"reminder SQL selects fallback anchors without resurrecting expired execution starts");
            String hidden="chrona:"+fallbackEnd+":"+end;
            check(!ids(store.nextReminderCandidates(now,Set.of(hidden))).contains(fallbackEnd),"hidden reminder keys use effective deadline");
            EventCandidate saved=store.getCandidatesByIds(List.of(fallbackEnd)).get(0);
            check(saved.dueAtMillis==null&&saved.effectiveDueAtMillis().equals(end),"official absent deadline remains null in persistence");
            check(TaskCodec.event(TaskCodec.candidate(saved)).dueAtMillis==null,"sync keeps official absent deadline null");
        }
    }

    public static void main(String[] args)throws Exception{
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        effectiveDeadlineChecks(new File(args[0]));
        homeTaskChecks(new File(args[0]));
        recordSearchChecks(new File(args[0]));
        Context context=new Context(new File(args[0],"types-"+System.nanoTime()));long now=System.currentTimeMillis();
        long task,id,record;
        try(TaskStore store=new TaskStore(context)){
            task=store.insertTask("完整原文",null,"source",now);store.updateStatus(task,TaskRecord.READY,null);
            id=store.appendCandidate(value(task,null,null,now-1000,null,false));
            record=store.appendCandidate(value(task,null,null,null,null,true));
            EventCandidate original=store.getCandidatesByIds(List.of(id)).get(0);
            check(original.dueAtMillis==now-1000&&original.startAtMillis==null&&original.endAtMillis==null
                    &&!original.point()&&original.executionStartAtMillis()==now-1000-1800000,
                    "deadline and source fields remain exact while display derives a half-hour interval");
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
            for(String column:new String[]{"due_at_millis","completed_at_millis","is_record","repeat_rule","repeat_exceptions","repeat_stopped_at","occurrence_update"})db.execSQL("ALTER TABLE event_candidates DROP COLUMN "+column);
            // Reconstruct the v10 schema, including absence of the later school tables/triggers.
            db.execSQL("DROP TABLE parse_time_entries");
            db.execSQL("DROP TABLE parse_runs");
            db.execSQL("DROP TRIGGER zju_capture_deleted");
            db.execSQL("DROP TRIGGER zju_candidate_deleted");
            for(String table:new String[]{"managed_zju_state","zju_homework_captures","zju_homework_map","zju_homework_history","zju_homework_deleted_tasks"})
                db.execSQL("DROP TABLE "+table);
            db.beginTransaction();try{store.onUpgrade(db,10,TaskStore.DATABASE_VERSION);db.setTransactionSuccessful();}finally{db.endTransaction();}
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
        System.out.println("Two schedule types: "+checks+" checks passed (actual TaskStore/SQLite, home execution/deadline split, complete summaries, candidate search/edit/reopen, completion, sync and upgraded backup)");
    }
}
