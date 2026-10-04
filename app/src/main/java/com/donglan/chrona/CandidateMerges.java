package com.donglan.chrona;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.*;
import com.donglan.chrona.sync.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Preview and atomic entity merge; never calls the calendar provider or file deletion. */
final class CandidateMerges {
    static final class Preview {
        final long revision;
        final List<List<EventCandidate>> groups;
        final Map<Long,TaskRecord> tasks;
        Preview(long revision,List<List<EventCandidate>> groups,Map<Long,TaskRecord> tasks){this.revision=revision;this.groups=groups;this.tasks=tasks;}
        int removed(){int count=0;for(List<EventCandidate> group:groups)count+=group.size()-1;return count;}
    }
    static Preview scan(TaskStore store) {
        List<EventCandidate> all=new ArrayList<>();Map<Long,TaskRecord> tasks=new LinkedHashMap<>();
        for(TaskRecord task:store.listTasks())if(!AndroidSyncData.busy(task)){
            tasks.put(task.id,task);store.stableTaskId(task.id);
            for(EventCandidate candidate:store.getCandidates(task.id)){store.stableCandidateId(candidate.id);all.add(candidate);}
        }
        List<List<EventCandidate>> groups=DuplicateCandidates.groups(all);
        for(List<EventCandidate> group:groups)group.sort(Comparator.comparing(candidate->store.stableCandidateId(candidate.id)));
        return new Preview(store.dataRevision(),groups,tasks);
    }
    static Preview scan(Context context) {
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context)){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{Preview result=scan(store);db.setTransactionSuccessful();return result;}finally{db.endTransaction();}}}
    }
    static JSONObject record(TaskStore store,EventCandidate source,EventCandidate target,TaskRecord task)throws Exception{
        String candidate=store.stableCandidateId(source.id);
        JSONObject snapshot=new JSONObject().put("rawText",task.rawText).put("source",task.source).put("createdAt",task.createdAtMillis)
                .put("linkText",task.linkText==null?"":task.linkText).put("candidate",TaskCodec.candidate(source).put("id",candidate));
        return TaskCodec.validate(new JSONObject().put("kind","merge").put("sourceTask",store.stableTaskId(source.taskId)).put("sourceCandidate",candidate)
                .put("targetTask",store.stableTaskId(target.taskId)).put("targetCandidate",store.stableCandidateId(target.id)).put("source",snapshot));
    }
    static int merge(Context context,long expectedRevision)throws Exception{
        int removed;
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context)){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            removed=mergeRows(store,expectedRevision);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}}}
        if(removed>0){AgendaWidgetProvider.requestRefresh(context);if(new WebDavSettingsStore(context).automatic()){SyncJobService.schedule(context);AndroidSync.request(context,null);}}
        return removed;
    }
    static int mergeRows(TaskStore store,long expectedRevision)throws Exception{
        if(!store.getWritableDatabase().inTransaction())throw new IllegalStateException("合并必须在事务中确认");
        if(store.dataRevision()!=expectedRevision)throw new IOException("日程已变化，请重新扫描");
        Preview preview=scan(store);List<JSONObject> records=new ArrayList<>();
        for(List<EventCandidate> group:preview.groups)for(int i=1;i<group.size();i++)records.add(record(store,group.get(i),group.get(0),preview.tasks.get(group.get(i).taskId)));
        for(JSONObject record:records)store.acceptMerge(record);
        return records.size();
    }
    static JSONArray sources(TaskStore store,long candidate)throws Exception{
        JSONArray result=new JSONArray();String stable=store.stableCandidateId(candidate);
        for(JSONObject record:store.mergeLedger().sources(stable)){
            long local=store.localTaskId(record.getString("sourceTask"));TaskRecord task=local==0?null:store.getTask(local);
            JSONObject snapshot=record.getJSONObject("source");
            result.put(new JSONObject().put("taskId",task==null?0:local).put("source",task==null?snapshot.getString("source"):task.source)
                    .put("text",task==null?snapshot.getString("rawText"):task.rawText).put("createdAt",snapshot.getLong("createdAt")));
        }
        return result;
    }
    static JSONArray targets(TaskStore store,long task)throws Exception{
        JSONArray result=new JSONArray();String stable=store.stableTaskId(task);MergeLedger ledger=store.mergeLedger();Set<String> added=new HashSet<>();
        for(JSONObject record:ledger.records())if(record.getString("sourceTask").equals(stable)){
            String target=ledger.target(record.getString("sourceCandidate"));if(!added.add(target))continue;
            long local=store.localCandidateId(target);List<EventCandidate> candidates=local==0?Collections.emptyList():store.getCandidatesByIds(Collections.singletonList(local));
            EventCandidate candidate=candidates.isEmpty()?null:candidates.get(0);
            result.put(new JSONObject().put("taskId",candidate==null?0:candidate.taskId).put("candidateId",candidate==null?0:candidate.id).put("title",candidate==null?record.getJSONObject("source").getJSONObject("candidate").getString("title"):candidate.title));
        }return result;
    }
    private CandidateMerges() { }
}
