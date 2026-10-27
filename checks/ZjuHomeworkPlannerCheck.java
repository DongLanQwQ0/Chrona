package com.donglan.chrona;

import android.content.Context;
import android.content.ContentValues;
import com.donglan.chrona.ai.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

/** Synthetic school data, real persistence/normalizer, no HTTP and no school credentials. */
public final class ZjuHomeworkPlannerCheck {
    static int checks, calls, serial;
    static final AiSettings AI = new AiSettings("https://invalid.example", "fake", "test-only");
    static final String ZONE = ZoneId.systemDefault().getId();
    static long due = LocalDate.now().plusDays(5).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static long count(Context context, String where) {
        try (TaskStore store=new TaskStore(context);var row=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM zju_homework_plans WHERE "+where,null)) {row.moveToFirst();return row.getLong(0);}
    }
    static EventCandidate get(Context context,long id) {try(TaskStore s=new TaskStore(context)){var rows=s.getCandidatesByIds(List.of(id));return rows.isEmpty()?null:rows.get(0);}}
    static ZjuSnapshot.Homework work(String key, Long deadline, Boolean done) {
        return new ZjuSnapshot.Homework(key,"course","课程 · "+key,"课程内容","https://school.example/homework","",deadline,done);
    }
    static long add(Context context) {
        var store=new ZjuHomeworkStore(context);var state=store.state();
        var result=store.apply(state.account,state.generation,List.of(work("new-"+(++serial),due,false)));
        return result.newHomeworkIds.get(0);
    }
    static JSONObject time(long millis) {
        LocalDateTime local=Instant.ofEpochMilli(millis).atZone(ZoneId.of(ZONE)).toLocalDateTime();
        return new JSONObject().put("date",local.toLocalDate().toString()).put("time",local.toLocalTime().withSecond(0).withNano(0).toString())
                .put("day_offset",JSONObject.NULL).put("weekday",JSONObject.NULL).put("week_offset",JSONObject.NULL).put("offset_minutes",JSONObject.NULL);
    }
    static ParseResult response(long task,long reference,String zone,boolean noWindow) throws Exception {
        long suggested=LocalDate.now().plusDays(1).atTime(12,0).atZone(ZoneId.of(zone)).toInstant().toEpochMilli();
        JSONObject event=new JSONObject().put("title","模型试图改标题").put("category","homework")
                .put("start",JSONObject.NULL).put("end",JSONObject.NULL).put("due",time(due))
                .put("suggested_start",time(suggested)).put("duration_minutes",noWindow?800L:90L)
                .put("execution_time_explicit",false).put("plan_group",JSONObject.NULL).put("plan_until",JSONObject.NULL)
                .put("recurrence",JSONObject.NULL).put("time_zone_id",zone).put("all_day",false)
                .put("location","模型地点").put("description","模型说明").put("reminder_specified",true)
                .put("reminder_minutes_before",99).put("uncertainty_level",2).put("needs_confirmation",true);
        JSONObject assessment=new JSONObject();
        for(String field:TimeAssessment.FIELDS)assessment.put(field,event.isNull(field)?JSONObject.NULL:
                new JSONObject().put("certainty",field.equals("due")?"explicit":"uncertain").put("basis","合成官方截止与工作量")
                        .put("rule",JSONObject.NULL).put("reason",field.equals("due")?"保留官方截止":"自由执行建议"));
        event.put("time_assessment",assessment);
        JSONObject envelope=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop")
                .put("message",new JSONObject().put("content",new JSONObject().put("events",new JSONArray().put(event)).toString()))));
        var method=ChatCompletionClient.class.getDeclaredMethod("parseResponse",long.class,String.class,long.class,String.class,long.class,int.class,int.class);
        method.setAccessible(true);
        return (ParseResult)method.invoke(null,task,envelope.toString(),reference,zone,System.currentTimeMillis(),600,1320);
    }
    static String process(Context context, Runnable during, boolean noWindow) {
        return ZjuHomeworkPlanner.processOne(context,(ai,prefs,control,task,input,reference,zone)->{
            calls++;check(!Thread.holdsLock(AndroidSync.LOCK),"network does not hold sync lock");
            check(!input.contains("account-a")&&!input.contains("password"),"no identity or credentials sent to AI");
            if(during!=null)during.run();return response(task,reference,zone,noWindow);
        },AI);
    }
    public static void main(String[] args) throws Exception {
        Context context=new Context(Files.createTempDirectory(new File(args[0]).toPath(),"planner-").toFile());
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        settings.prefs.edit().putString("account","account-a").putString("credentials","fixture-only-unusable").putBoolean("enabled",true).apply();
        ZjuHomeworkStore school=new ZjuHomeworkStore(context);school.configure("account-a",true);
        long first=add(context);check(count(context,"1=1")==0,"off by default queues nothing");
        settings.homeworkAutoPlan(true);var state=school.state();
        school.apply(state.account,state.generation,List.of(work("new-1",due,false)));
        check(count(context,"1=1")==0,"enabling does not retroactively queue old homework");
        school.apply(state.account,state.generation,List.of(work("no-due",null,false),work("past",1L,false),work("done",due,true)));
        check(count(context,"1=1")==0,"missing/past deadlines and submitted work excluded");
        long id=add(context);EventCandidate original=get(context,id);
        check(count(context,"state='queued'")==1,"new eligible homework queues atomically");
        school.apply(state.account,state.generation,List.of(work("new-2",due,false)));
        check(count(context,"1=1")==1,"repeated sync does not duplicate queue");
        int unconfigured=calls;
        check(ZjuHomeworkPlanner.processOne(context).isEmpty()&&calls==unconfigured&&count(context,"state='queued'")==1,
                "missing AI configuration does not submit or consume queued work");
        check(process(context,null,false).contains("已自动排程"),"fake response applies");
        EventCandidate planned=get(context,id);
        check(planned.id==original.id&&planned.taskId==original.taskId&&planned.title.equals(original.title)
                &&planned.description.equals(original.description)&&Objects.equals(planned.dueAtMillis,original.dueAtMillis)
                &&Objects.equals(planned.reminderMinutesBefore,original.reminderMinutesBefore)&&planned.location.equals(original.location),"official identity/content/deadline and personal reminder unchanged");
        check(planned.startAtMillis!=null&&planned.endAtMillis-planned.startAtMillis==90*60000L
                &&!planned.needsConfirmation&&planned.uncertaintyLevel==2,"auto authorization separate from genuine uncertainty");
        try(TaskStore store=new TaskStore(context)){
            JSONObject audit=store.getParseAudits(original.taskId,id,null,null,0,10).getJSONObject(0);
            JSONObject entry=audit.getJSONArray("entries").getJSONObject(0);
            check(entry.getLong("candidateId")==id&&entry.getString("candidateSyncId").equals(store.stableCandidateId(id))
                    &&entry.getJSONObject("evidence").getJSONObject("application").getString("policy").equals("auto_authorized"),"audit links original candidate and explicit authorized policy");
        }
        int before=calls;process(context,null,false);check(calls==before,"successful item never replans");
        long projection=add(context);String projectionResult=process(context,()->ZjuHomeworkCalendar.fail=true,false);ZjuHomeworkCalendar.fail=false;
        check(projectionResult.contains("已自动排程")&&get(context,projection).startAtMillis!=null
                &&count(context,"candidate_id="+projection+" AND state='succeeded'")==1,"calendar failure cannot rewrite committed schedule or successful audit");
        long edit=add(context);process(context,()->{try(TaskStore store=new TaskStore(context)){ContentValues v=new ContentValues();v.put("reminder_minutes_before",17);store.getWritableDatabase().update("event_candidates",v,"id=?",new String[]{""+edit});}},false);
        check(get(context,edit).startAtMillis==null&&get(context,edit).reminderMinutesBefore==17,"manual edit during provider request wins");
        long done=add(context);process(context,()->{try(TaskStore store=new TaskStore(context)){ContentValues v=new ContentValues();v.put("completed_at_millis",System.currentTimeMillis());store.getWritableDatabase().update("event_candidates",v,"id=?",new String[]{""+done});}},false);
        check(get(context,done).startAtMillis==null,"completion during provider request wins");
        long changed=add(context);process(context,()->{school.configure("account-b",true);settings.prefs.edit().putString("account","account-b").apply();},false);
        check(get(context,changed).startAtMillis==null,"account/generation change rejects old response");
        school.configure("account-a",true);settings.prefs.edit().putString("account","account-a").apply();
        long disabled=add(context);process(context,()->settings.homeworkAutoPlan(false),false);
        check(get(context,disabled).startAtMillis==null,"disable cancels in-flight application");settings.homeworkAutoPlan(true);
        long immediatelyRevoked=add(context);int beforeRevoke=calls;
        process(context,()->{
            settings.revokeHomeworkAutoPlan();
            check(!settings.homeworkAutoPlan(),"production immediate revoke withdraws authorization during provider work");
            check(count(context,"candidate_id="+immediatelyRevoked+" AND state='running'")==1,
                    "immediate revoke does not wait for worker queue cleanup");
        },false);
        check(get(context,immediatelyRevoked).startAtMillis==null&&calls==beforeRevoke+1,
                "in-flight paid response cannot apply after immediate revoke");
        settings.homeworkAutoPlan(true);process(context,null,false);
        check(calls==beforeRevoke+1&&count(context,"candidate_id="+immediatelyRevoked+" AND state='queued'")==0,
                "revoked started request never incurs another provider call after reauthorization");
        long noWindow=add(context);process(context,null,true);
        check(get(context,noWindow).startAtMillis==null&&get(context,noWindow).dueAtMillis==due,"no full window preserves deadline and unarranged state");
        long official=add(context);long originalDue=due;
        process(context,()->{try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().execSQL("UPDATE zju_homework_map SET snapshot='changed-official' WHERE candidate_id=?",new Object[]{official});
        }},false);
        check(get(context,official).startAtMillis==null&&get(context,official).dueAtMillis==originalDue,"official snapshot change rejects in-flight response");
        long removed=add(context);process(context,()->{try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().delete("event_candidates","id=?",new String[]{""+removed});
        }},false);
        check(get(context,removed)==null&&count(context,"candidate_id="+removed)==0,"deletion does not resurrect candidate or queue");
        long multiple=add(context);
        ZjuHomeworkPlanner.processOne(context,(ai,prefs,control,task,input,reference,zone)->{
            var one=response(task,reference,zone,false);
            return new ParseResult(List.of(one.candidates.get(0),one.candidates.get(0)),null,null,null,null,one.timeAuditJson);
        },AI);
        check(get(context,multiple).startAtMillis==null&&count(context,"candidate_id="+multiple+" AND state='failed'")==1,"multiple model events rejected without creating tasks");
        long interrupted=add(context);
        try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().execSQL("UPDATE zju_homework_plans SET state='running' WHERE candidate_id=?",new Object[]{interrupted});
        }
        before=calls;process(context,null,false);check(calls==before&&count(context,"candidate_id="+interrupted+" AND state='failed'")==1,"started/unknown outcome never auto-retries");
        long restore=add(context);
        try(TaskStore store=new TaskStore(context)){
            File snapshot=new File(context.getFilesDir(),"snapshot.db");store.createSnapshot(snapshot);
            try(TaskStore saved=TaskStore.openSnapshot(context,snapshot)){
                TaskStore.finishInterruptedParseRuns(saved.getWritableDatabase(),null,"InterruptedByRestore");
                try(var row=saved.getReadableDatabase().rawQuery("SELECT state FROM zju_homework_plans WHERE candidate_id=?",new String[]{""+restore})){
                    row.moveToFirst();check(row.getString(0).equals("cancelled"),"backup retains queue but restore cancels unstarted payment");
                }
                check(saved.getParseAudits(original.taskId,id,null,null,0,10).length()==1,"snapshot retains complete prior audit");
            }
        }
        SchedulePreferences overnight=new SchedulePreferences(22*60,2*60,"","");
        long night=LocalDate.now().plusDays(1).atTime(23,0).atZone(ZoneId.of(ZONE)).toInstant().toEpochMilli();
        EventCandidate overnightItem=new EventCandidate(0,1,"test",night,night+120*60000L,ZONE,"","",null,false,null,"homework",false);
        check(ZjuHomeworkPlanner.validWindow(overnightItem,due,System.currentTimeMillis(),ZONE,overnight),"complete overnight work window allowed");
        check(!ZjuHomeworkPlanner.validWindow(overnightItem,due,night+1,ZONE,overnight),"response-time lower bound enforced");
        try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().execSQL("DROP TABLE zju_homework_plans");store.getWritableDatabase().setVersion(17);
        }
        try(TaskStore store=new TaskStore(context)){
            check(store.getParseAudits(original.taskId,id,null,null,0,10).length()==1&&get(context,id).startAtMillis.equals(planned.startAtMillis),
                    "v17 to v18 migration preserves existing candidate and historical parse audit");
            check(count(context,"1=1")==0,"v18 migration creates no retroactive scheduling queue");
        }
        System.out.println("PASS: "+checks+" real SQLite, fake-provider scheduling/race/audit/snapshot/window checks");
    }
}
