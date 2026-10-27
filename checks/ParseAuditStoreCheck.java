package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.*;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Production persistence and real SQLite, with isolated synthetic records only. */
public final class ParseAuditStoreCheck {
    static int checks;
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static EventCandidate candidate(long task, String title, int level) {
        return new EventCandidate(0, task, title, 123000L, 456000L, "Asia/Shanghai", "", "",
                null, level > 0, null, "task", false, false, level);
    }
    static String audit(List<EventCandidate> items, String group) throws Exception {
        JSONArray entries = new JSONArray();
        for (int i=0;i<items.size();i++) entries.put(new JSONObject().put("position",i)
                .put("plan_group", group == null ? JSONObject.NULL : group)
                .put("model",new JSONObject().put("basis","合成原文：11月3日截止").put("certainty",i==0?"rule_based":"uncertain"))
                .put("resolved",new JSONObject().put("start",items.get(i).startAtMillis).put("uncertainty_level",items.get(i).uncertaintyLevel)));
        return new JSONObject().put("prompt_version","schedule-v17").put("entries",entries).toString();
    }
    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory(new File(args[0]).toPath(),"parse-audit-").toFile();
        Context context = new Context(new File(base,"live"));
        long task;
        String first;
        long firstCandidate;
        File snapshot = new File(base,"snapshot.db");
        try (TaskStore store = new TaskStore(context)) {
            task = store.insertTask("11月3日截止",null,"test",1);
            check(store.getParseAudits(task,null,null,null,0,10).length()==0,"old/manual records have no invented evidence");
            first = store.beginParseRun(task,100,new JSONObject().put("preferences","工作时间10:00–22:00").put("attempt",1));
            EventCandidate a = candidate(task,"准备",0), b = candidate(task,"修改",2), duplicate = candidate(task,"准备",0);
            List<EventCandidate> original=List.of(a,b,duplicate), retained=CandidateRules.apply(original);
            store.replaceParsedCandidates(task,retained,original,first,audit(original,"方案A"));
            firstCandidate=store.getCandidates(task).get(0).id;
            JSONObject run=store.getParseAudits(task,null,first,null,0,10).getJSONObject(0);
            JSONArray entries=run.getJSONArray("entries");
            check(run.getString("status").equals("succeeded") && entries.length()==3,"every model item saved in one successful parse");
            check(entries.getJSONObject(0).getLong("candidateId")==firstCandidate
                    && entries.getJSONObject(2).getLong("candidateId")==firstCandidate,"identical model duplicates link to the retained real schedule");
            check(!entries.getJSONObject(0).getString("candidateSyncId").isEmpty(),"stable schedule identity saved");
            check(store.getParseAudits(task,firstCandidate,first,"方案A",0,10).getJSONObject(0).getJSONArray("entries").length()==2,
                    "candidate, plan and run filters combine with exact association");
            check(store.getParseAudits(task,firstCandidate,first,"方案B",0,10).length()==0,"different plans do not leak into results");
            long other=store.insertTask("other",null,"test",2);
            check(store.getParseAudits(other,firstCandidate,first,null,0,10).length()==0,"task scope enforced");
            String before=store.getCandidates(task).get(0).title;
            try { store.replaceParsedCandidates(task,List.of(candidate(task,"不得保存",0)),List.of(candidate(task,"不得保存",0)),
                    "missing-run",audit(List.of(candidate(task,"不得保存",0)),null)); throw new AssertionError("invalid audit accepted"); }
            catch (RuntimeException expected) { checks++; }
            check(store.getCandidates(task).get(0).id==firstCandidate && store.getCandidates(task).get(0).title.equals(before),
                    "audit failure rolls back candidate replacement, IDs and evidence");
            String second=store.beginParseRun(task,200,new JSONObject().put("attempt",1));
            EventCandidate newer=candidate(task,"重新规划",2);
            store.replaceParsedCandidates(task,List.of(newer),List.of(newer),second,audit(List.of(newer),"方案A"));
            check(store.getParseAudits(task,null,null,null,0,1).getJSONObject(0).getString("runId").equals(second),"runs paginated newest first");
            check(store.getParseAudits(task,null,null,null,1,1).getJSONObject(0).getString("runId").equals(first),"older parse remains retrievable");
            check(store.getParseAudits(task,firstCandidate,null,null,0,10).getJSONObject(0).getString("runId").equals(first),
                    "reparse keeps the old schedule-to-evidence association without pointing at a replacement ID");
            check(store.getParseAudits(task,null,first,"方案A",0,10).getJSONObject(0).getJSONArray("entries").length()==3,
                    "same plan label across runs never conflated");
            long newerId=store.getCandidates(task).get(0).id;
            store.getWritableDatabase().execSQL("UPDATE event_candidates SET start_at_millis=999999 WHERE id=?",new Object[]{newerId});
            check(store.getParseAudits(task,newerId,null,null,0,10).getJSONObject(0).getJSONArray("entries").getJSONObject(0)
                    .getJSONObject("evidence").getJSONObject("resolved").getLong("start")==123000,"editing schedule never rewrites original parse evidence");
            check(store.deleteCandidate(newerId,task),"delete synthetic schedule");
            check(store.getParseAudits(task,newerId,null,null,0,10).length()==1,"candidate deletion retains historical audit under source task");
            String failed=store.beginParseRun(task,300,new JSONObject().put("attempt",2));
            store.failParseRun(failed,false,"ResponseException");
            check(store.getParseAudits(task,null,failed,null,0,10).getJSONObject(0).getString("error").equals("ResponseException"),"failed attempt independently logged");
            String cancelled=store.beginParseRun(task,400,new JSONObject().put("attempt",3));
            store.failParseRun(cancelled,true,"cancelled");
            check(store.getParseAudits(task,null,cancelled,null,0,10).getJSONObject(0).getString("status").equals("cancelled"),"cancelled attempt independently logged");
            store.failParseRun(first,false,"later notification failure");
            check(store.getParseAudits(task,null,first,null,0,10).getJSONObject(0).getString("status").equals("succeeded"),"later failure cannot rewrite successful parse");
            try { store.getParseAudits(task,null,null,"方案A",0,10);throw new AssertionError("unscoped plan query accepted"); }
            catch(IllegalArgumentException expected){checks++;}
            check(store.getParseAudits(task,null,null,null,0,10).length()==4,"all parse attempts preserved");
            store.updateStatus(task,TaskRecord.PROCESSING,null);
            String interrupted=store.beginParseRun(task,500,new JSONObject().put("attempt",1));
            check(store.failInterruptedProcessing(task,"interrupted"),"dead processing task recovered");
            JSONObject recovered=store.getParseAudits(task,null,interrupted,null,0,10).getJSONObject(0);
            check(recovered.getString("status").equals("failed") && !recovered.isNull("finishedAt")
                    && recovered.getString("error").equals("InterruptedProcess"),"process reconciliation closes its unfinished parse log");
            String abandoned=store.beginParseRun(task,600,new JSONObject().put("attempt",1));
            String retry=store.beginParseRun(task,700,new JSONObject().put("attempt",2));
            check(store.getParseAudits(task,null,abandoned,null,0,10).getJSONObject(0).getString("error").equals("InterruptedBeforeNewAttempt"),
                    "new owned attempt closes stale running history even before reconciliation");
            TaskStore.finishInterruptedParseRuns(store.getWritableDatabase(),null,"InterruptedByRestore");
            check(store.getParseAudits(task,null,retry,null,0,10).getJSONObject(0).getString("status").equals("failed"),
                    "offline restore closes unfinished parse logs without changing successful history");
            store.createSnapshot(snapshot);
            try(TaskStore copied=TaskStore.openSnapshot(context,snapshot)) {
                check(copied.getParseAudits(task,null,null,null,0,10).toString().equals(store.getParseAudits(task,null,null,null,0,10).toString()),
                        "full snapshot preserves every evidence field and relationship");
            }
        }
        try(TaskStore reopened=new TaskStore(context)) {
            check(reopened.getParseAudits(task,firstCandidate,first,"方案A",0,10).length()==1,"audit survives process/storage reopen");
            check(reopened.deleteTask(task),"delete synthetic source");
            check(reopened.getParseAudits(task,null,null,null,0,10).length()==0,"source deletion cleans associated private audit");
            try(android.database.Cursor cursor=reopened.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM parse_time_entries",null)) {
                check(cursor.moveToFirst() && cursor.getLong(0)==0,"no orphan audit entries after source deletion");
            }
        }
        Context legacy = new Context(new File(base,"upgrade-v15"));
        long legacyTask;
        try (TaskStore store = new TaskStore(legacy)) {
            legacyTask = store.insertTask("升级前原文",null,"test",1);
            store.appendCandidate(candidate(legacyTask,"升级前日程",0));
            store.getWritableDatabase().execSQL("DROP TABLE zju_homework_plans");
            store.getWritableDatabase().execSQL("DROP TABLE parse_time_entries");
            store.getWritableDatabase().execSQL("DROP TABLE parse_runs");
            store.getWritableDatabase().execSQL("ALTER TABLE event_candidates DROP COLUMN occurrence_update");
            store.getWritableDatabase().setVersion(15);
        }
        try (TaskStore upgraded = new TaskStore(legacy)) {
            check(upgraded.getTask(legacyTask).rawText.equals("升级前原文")
                    && upgraded.getCandidates(legacyTask).get(0).title.equals("升级前日程"),
                    "actual v15 upgrade preserves existing source and schedule");
            check(upgraded.getParseAudits(legacyTask,null,null,null,0,10).length()==0,
                    "v15 upgrade creates audit storage without inventing historical evidence");
        }
        System.out.println("Parse audit persistence: "+checks+" checks passed; production TaskStore, actual SQLite, reparse, rollback, snapshot and queries");
    }
}
