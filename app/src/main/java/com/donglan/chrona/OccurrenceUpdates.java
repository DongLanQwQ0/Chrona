package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.LinkedCalendarEvents;
import com.donglan.chrona.data.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Announcement proposals remain drafts until an explicit, audited single-occurrence confirmation. */
public final class OccurrenceUpdates {
    private OccurrenceUpdates() { }
    private static final String[] FIELDS={"title","start","end","due","time_zone_id","location","description",
            "reminder_minutes_before","category","all_day","end_auto_generated","needs_confirmation","uncertainty_level"};

    public static JSONArray context(Context context)throws Exception{
        JSONArray result=new JSONArray();
        try(TaskStore store=new TaskStore(context)){
            for(EventCandidate item:store.repeatingCandidates()){
                if(item.repeatStoppedAtMillis!=null||!item.settled()||store.isManagedHomework(item.id,item.taskId))continue;
                TaskRecord source=store.getTask(item.taskId);if(source==null||TaskRecord.PROCESSING.equals(source.status)||TaskRecord.QUEUED.equals(source.status))continue;
                ZoneId zone=ZoneId.of(item.allDay?"UTC":item.timeZoneId);
                result.put(new JSONObject().put("series_id",store.stableCandidateId(item.id)).put("title",item.title)
                        .put("start",item.startAtMillis==null?JSONObject.NULL:item.startAtMillis)
                        .put("end",item.endAtMillis==null?JSONObject.NULL:item.endAtMillis)
                        .put("due",item.dueAtMillis==null?JSONObject.NULL:item.dueAtMillis)
                        .put("time_zone_id",item.timeZoneId).put("all_day",item.allDay).put("category",item.category)
                        .put("location",item.location==null?JSONObject.NULL:item.location).put("recurrence",item.repeatRule.toJson())
                        .put("start_local",local(item.startAtMillis,zone)).put("end_local",local(item.endAtMillis,zone))
                        .put("due_local",local(item.dueAtMillis,zone)).put("weekday",Instant.ofEpochMilli(item.anchor()).atZone(zone).getDayOfWeek().getValue()));
            }
        }return result;
    }

    /** Must run before automatic publication, after the original parse evidence is committed. */
    public static void stage(Context context,long taskId)throws Exception{
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().beginTransaction();
            try{
                for(EventCandidate source:store.getCandidates(taskId)){
                    JSONObject audit=store.latestParseEvidence(source.id),saved=store.occurrenceProposal(source.id);
                    if(audit==null||saved!=null&&!"pending_projection".equals(saved.optString("validation")))continue;
                    JSONObject model=audit.getJSONObject("evidence").getJSONObject("model"),match=model.optJSONObject("occurrence_update");
                    if(!model.has("occurrence_update")||model.isNull("occurrence_update"))continue;
                    if(match==null){store.recordOccurrenceProposal(source.id,new JSONObject().put("state","invalid").put("validation","invalid_match_type").put("reason","占位匹配格式无效，请重新解析或明确选择新建"));continue;}
                    JSONObject proposal=new JSONObject().put("state","invalid").put("reason","占位匹配已失效，请重新解析或明确选择新建");
                    EventCandidate preview=copy(source,source.id,source.taskId,source.calendarEventId,true,EventCandidate.DOUBTFUL,false,null);
                    try{
                        if(!store.occurrenceMatchesAgree(source.id,audit.getString("runId")))throw new IllegalArgumentException("合并条目的占位目标不一致");
                        String stable=match.getString("series_id");JSONArray sent=audit.optJSONArray("placeholders");boolean offered=false;
                        if(sent!=null)for(int i=0;i<sent.length();i++)if(stable.equals(sent.getJSONObject(i).optString("series_id")))offered=true;
                        if(!offered)throw new IllegalArgumentException("目标未包含在本次请求中");
                        EventCandidate series=series(store,stable);long original=original(series,LocalDate.parse(match.getString("original_date")));
                        EventCandidate before=RepeatOccurrences.at(series,original);if(before==null)throw new IllegalArgumentException("这次占位已停止");
                        JSONArray supplied=match.getJSONArray("provided_fields");JSONObject patch=new JSONObject(),incoming=snapshot(source);
                        for(int i=0;i<supplied.length();i++){
                            String field=supplied.getString(i);if(!Arrays.asList(FIELDS).contains(field))throw new IllegalArgumentException("更新字段无效");
                            // A parser's certainty/default flags never replace the existing occurrence's state.
                            if(field.equals("needs_confirmation")||field.equals("uncertainty_level")||field.equals("end_auto_generated"))continue;
                            if(field.equals("reminder_minutes_before")&&!model.optBoolean("reminder_specified"))continue;
                            patch.put(field,incoming.get(field));
                        }
                        if(patch.has("start")&&!patch.isNull("start")&&!patch.has("end")&&before.startAtMillis!=null&&before.endAtMillis!=null)
                            patch.put("end",Math.addExact(patch.getLong("start"),before.endAtMillis-before.startAtMillis));
                        if(patch.has("end"))patch.put("end_auto_generated",false);
                        EventCandidate projected=RepeatOccurrences.at(RepeatOccurrences.edit(series,original,patch),original);
                        preview=copy(projected,source.id,source.taskId,null,true,Math.max(source.uncertaintyLevel,projected.uncertaintyLevel),false,null);
                        proposal=new JSONObject().put("state","pending").put("series_id",stable).put("original",original)
                                .put("original_date",match.getString("original_date")).put("target_snapshot",targetSnapshot(series))
                                .put("fields",patch).put("before",snapshot(before)).put("preview",snapshot(preview))
                                .put("reason",match.getString("reason")).put("applications",new JSONArray());
                    }catch(Exception invalid){proposal.put("validation",invalid.getClass().getSimpleName());}
                    store.recordOccurrenceProposal(source.id,proposal);
                    if(!store.updateCandidate(preview))throw new IllegalStateException("公告草稿已不存在");
                }
                store.updateReviewStatus(taskId);store.getWritableDatabase().setTransactionSuccessful();
            }finally{store.getWritableDatabase().endTransaction();}
        }}
    }

    public static boolean held(TaskStore store,long candidateId)throws Exception{
        JSONObject proposal=store.occurrenceProposal(candidateId);return proposal!=null&&!"new".equals(proposal.optString("state"));
    }

    static EventCandidate apply(Context context,EventCandidate edited,String sourceBaseline)throws Exception{
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context);LinkedCalendarEvents writes=new LinkedCalendarEvents(store,new CalendarStore(context))){
            store.getWritableDatabase().beginTransaction();
            EventCandidate changed;
            try{
                changed=applyLocked(store,writes,edited,sourceBaseline);
                store.getWritableDatabase().setTransactionSuccessful();
            }finally{store.getWritableDatabase().endTransaction();}
            writes.committed();return changed;
        }}
    }

    static EventCandidate applyLocked(TaskStore store,LinkedCalendarEvents writes,EventCandidate edited,String sourceBaseline)throws Exception{
                EventCandidate source=store.getCandidatesByIds(List.of(edited.id)).stream().filter(item->item.taskId==edited.taskId).findFirst().orElse(null);
                JSONObject proposal=store.occurrenceProposal(edited.id);if(proposal==null)throw new IllegalStateException("占位匹配不存在");
                if("applied".equals(proposal.optString("state")))return series(store,proposal.getString("series_id"));
                CandidateEditBaseline.require(source,sourceBaseline);
                if(source.calendarEventId!=null)throw new IllegalStateException("公告已关联独立日程，请先重新解析以移除旧关联");
                if(!"pending".equals(proposal.optString("state")))throw new IllegalStateException(proposal.optString("reason","请重新解析占位匹配"));
                EventCandidate target=series(store,proposal.getString("series_id"));
                if(!com.donglan.chrona.sync.SyncState.canonical(targetSnapshot(target)).equals(com.donglan.chrona.sync.SyncState.canonical(proposal.getJSONObject("target_snapshot"))))
                    throw new IllegalStateException("占位已更新，请重新解析；当前草稿已保留");
                long original=proposal.getLong("original");EventCandidate before=RepeatOccurrences.at(target,original);
                JSONObject patch=new JSONObject(proposal.getJSONObject("fields").toString()),userChanges=diff(source,edited);
                for(Iterator<String> keys=userChanges.keys();keys.hasNext();){String key=keys.next();patch.put(key,userChanges.get(key));}
                patch.put("needs_confirmation",false).put("uncertainty_level",EventCandidate.CERTAIN);
                JSONObject application=new JSONObject().put("action","applied").put("at",System.currentTimeMillis())
                        .put("source_task",store.stableTaskId(source.taskId)).put("source_candidate",store.stableCandidateId(source.id))
                        .put("runId",proposal.getString("runId")).put("position",proposal.getInt("position"))
                        .put("series_id",proposal.getString("series_id")).put("original",original).put("before",snapshot(before));
                application.put("after",snapshot(RepeatOccurrences.at(RepeatOccurrences.edit(target,original,patch),original)));
                store.appendOccurrenceApplication(source.id,application);
                // This is an announcement outcome, never another calendar event or an alias of the master.
                if(!store.updateCandidate(copy(edited,source.id,source.taskId,null,false,EventCandidate.CERTAIN,true,null)))throw new IllegalStateException("公告已不存在");
                store.updateReviewStatus(source.taskId);
                return RepeatActions.editLocked(store,writes,target,original,patch,application);
    }

    static void chooseNew(Context context,long candidateId,String expected)throws Exception{
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context)){
            store.getWritableDatabase().beginTransaction();try{
                chooseNewLocked(store,candidateId,expected);
                store.getWritableDatabase().setTransactionSuccessful();
            }finally{store.getWritableDatabase().endTransaction();}
        }}
    }

    static void chooseNewLocked(TaskStore store,long candidateId,String expected)throws Exception{
        EventCandidate source=store.getCandidatesByIds(List.of(candidateId)).stream().findFirst().orElse(null);CandidateEditBaseline.require(source,expected);
        JSONObject proposal=store.occurrenceProposal(candidateId);if(proposal==null||"applied".equals(proposal.optString("state")))throw new IllegalStateException("此公告已应用");
        store.appendOccurrenceApplication(candidateId,new JSONObject().put("action","new").put("at",System.currentTimeMillis()));
    }

    static EventCandidate series(TaskStore store,String stable){
        List<EventCandidate> found=store.getCandidatesByIds(List.of(store.localCandidateId(stable)));
        if(found.size()!=1||found.get(0).repeatRule==null||found.get(0).isRecord)throw new IllegalArgumentException("原占位已不存在");
        if(store.isManagedHomework(found.get(0).id,found.get(0).taskId))throw new IllegalArgumentException("学校作业不作为可编辑占位");
        return found.get(0);
    }
    private static long original(EventCandidate series,LocalDate date){
        ZoneId zone=ZoneId.of(series.allDay?"UTC":series.timeZoneId);
        long begin=date.atStartOfDay(zone).toInstant().toEpochMilli(),end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<EventCandidate> options=RepeatOccurrences.between(series.withRepeatState("{}",null),begin,end,8).stream()
                .filter(item->Instant.ofEpochMilli(item.occurrenceAtMillis).atZone(zone).toLocalDate().equals(date)).collect(java.util.stream.Collectors.toList());
        if(options.size()!=1)throw new IllegalArgumentException("无法唯一确定这一次占位");return options.get(0).occurrenceAtMillis;
    }
    static JSONObject snapshot(EventCandidate item)throws JSONException{
        return new JSONObject().put("title",item.title).put("start",nullable(item.startAtMillis)).put("end",nullable(item.endAtMillis))
                .put("due",nullable(item.dueAtMillis)).put("time_zone_id",item.timeZoneId).put("location",nullable(item.location))
                .put("description",nullable(item.description)).put("reminder_minutes_before",nullable(item.reminderMinutesBefore))
                .put("category",item.category).put("all_day",item.allDay).put("end_auto_generated",item.endAutoGenerated)
                .put("needs_confirmation",item.needsConfirmation).put("uncertainty_level",item.uncertaintyLevel);
    }
    static JSONObject diff(EventCandidate before,EventCandidate after)throws JSONException{
        JSONObject left=snapshot(before),right=snapshot(after),patch=new JSONObject();
        for(String key:FIELDS)if(!Objects.equals(left.get(key),right.get(key)))patch.put(key,right.get(key));return patch;
    }
    private static JSONObject targetSnapshot(EventCandidate item)throws Exception{
        JSONObject value=snapshot(item).put("recurrence",item.repeatRule.toJson()).put("exceptions",new JSONObject(item.repeatExceptions))
                .put("stopped",nullable(item.repeatStoppedAtMillis)).put("completed",nullable(item.completedAtMillis));
        return com.donglan.chrona.sync.TaskCodec.portableOccurrenceUpdate(value);
    }
    private static Object local(Long at,ZoneId zone){return at==null?JSONObject.NULL:Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime().toString();}
    static EventCandidate copy(EventCandidate item,long id,long task,Long calendar,boolean confirmation,int uncertainty,boolean record,Long original){
        return new EventCandidate(id,task,item.title,item.startAtMillis,item.endAtMillis,item.timeZoneId,item.location,item.description,item.reminderMinutesBefore,
                confirmation,calendar,item.category,item.allDay,item.endAutoGenerated,uncertainty,record?null:item.dueAtMillis,record?null:item.completedAtMillis,record,null,"{}",null,original);
    }
    private static Object nullable(Object value){return value==null?JSONObject.NULL:value;}
}
