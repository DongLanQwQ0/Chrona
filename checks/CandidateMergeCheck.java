package com.donglan.chrona;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.*;
import com.donglan.chrona.sync.*;
import org.json.*;
import java.io.File;
import java.util.*;

/** Actual production merge/service/persistence; Android API transport alone is replaced. */
public final class CandidateMergeCheck {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static EventCandidate candidate(long task,int difference){
        return new EventCandidate(0,task,difference==1?"different":"Meeting",difference==2?2000L:1000L,
            difference==3?4000L:3000L,difference==4?"UTC":"Asia/Shanghai",difference==5?"other":"Room",
            difference==6?"different":"Notes",difference==7?20:10,difference==8,
            difference==9?8L:7L,difference==10?EventCategory.TASK:EventCategory.EVENT,difference==11,difference==12,
            difference==13?EventCandidate.INFERRED:EventCandidate.CERTAIN);
    }
    static long task(TaskStore store,String raw,int difference){
        long id=store.insertTask(raw,"image.png","source",100L);
        store.updateStatus(id,"ready",null);
        store.replaceCandidates(id,Collections.singletonList(candidate(id,difference)));
        store.addFileAttachment(id,new TaskFileAttachment(0,id,"file.pdf","report.pdf","application/pdf",4));
        return id;
    }
    static int merge(TaskStore store,long revision)throws Exception{
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{int result=CandidateMerges.mergeRows(store,revision);db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
    }
    static JSONObject record(String source,String target)throws Exception{
        JSONObject snapshot=new JSONObject().put("rawText","origin").put("source","test").put("createdAt",100).put("linkText","")
            .put("candidate",TaskCodec.candidate(candidate(0,0)).put("id",source));
        return TaskCodec.validate(new JSONObject().put("kind","merge").put("sourceTask","task_00000000-0000-0000-0000-000000000001")
            .put("sourceCandidate",source).put("targetTask","task_00000000-0000-0000-0000-000000000002").put("targetCandidate",target).put("source",snapshot));
    }
    static void apply(TaskStore store,SyncState state)throws Exception{
        AndroidSyncData sync=new AndroidSyncData(store,new Context(new File(System.getProperty("java.io.tmpdir"))));sync.applyMergeRecords(state);
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            for(Map.Entry<String,List<SyncState.Version>> entry:state.records().entrySet())if(entry.getKey().startsWith("task_")){
                SyncState.Version version=entry.getValue().get(0);sync.stage(entry.getKey(),version);sync.applyTask(entry.getKey(),version);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static JSONObject payload(String stable)throws Exception{
        return new JSONObject().put("kind","task").put("rawText","raw original").put("source","source").put("createdAt",100L)
            .put("status","ready").put("linkText","").put("attachments",new JSONArray())
            .put("candidates",new JSONArray().put(TaskCodec.candidate(candidate(0,0)).put("id",stable)));
    }
    static AndroidSyncData.Snapshot snapshot(TaskStore store)throws Exception{
        AndroidSyncData.Snapshot result=new AndroidSyncData.Snapshot();result.revision=store.dataRevision();result.tasks=store.listTasks();
        for(TaskRecord task:result.tasks){JSONArray candidates=new JSONArray();for(EventCandidate value:store.getCandidates(task.id))candidates.put(TaskCodec.candidate(value).put("id",store.stableCandidateId(value.id)));
            JSONObject value=new JSONObject().put("kind","task").put("rawText",task.rawText).put("source",task.source).put("createdAt",task.createdAtMillis)
                .put("status",task.status).put("linkText",task.linkText==null?"":task.linkText).put("candidates",candidates).put("attachments",new JSONArray());result.payloads.put(task.id,value);}
        return result;
    }
    static void stagedApply(TaskStore store,Context context,SyncState state)throws Exception{
        AndroidSyncData sync=new AndroidSyncData(store,context);sync.applyMergeRecords(state);
        AndroidSyncData.Snapshot snapshot=snapshot(store);
        sync.prepareTaskApply(state,snapshot,null,null);
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            sync.requireRevision(snapshot);
            for(Map.Entry<String,List<SyncState.Version>> entry:state.records().entrySet())if(entry.getKey().startsWith("task_"))sync.applyTask(entry.getKey(),entry.getValue().get(0));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public static void main(String[] args)throws Exception{
        File base=new File(args[0],UUID.randomUUID().toString());base.mkdirs();
        List<EventCandidate> variants=new ArrayList<>();variants.add(candidate(1,0));variants.add(candidate(2,0));
        for(int i=1;i<=13;i++){check(DuplicateCandidates.groups(Arrays.asList(candidate(1,0),candidate(2,i))).isEmpty(),"field "+i+" differs");variants.add(candidate(3+i,i));}
        check(DuplicateCandidates.groups(variants).size()==1,"only exact pair grouped");
        Context context=new Context(new File(base,"local"));
        try(TaskStore store=new TaskStore(context)){
            long first=task(store,"first original",0),second=task(store,"second original",0);
            long unrelated=task(store,"different candidate",1);
            CandidateMerges.Preview preview=CandidateMerges.scan(store);
            check(preview.removed()==1,"cross-source preview");
            store.updateRawText(unrelated,"changed");
            try{merge(store,preview.revision);throw new AssertionError("stale preview accepted");}catch(java.io.IOException expected){checks++;}
            check(store.listCandidates().size()==3&&store.mergeLedger().records().isEmpty(),"stale leaves all rows");
            check(merge(store,store.dataRevision())==1,"one entity removed");
            check(store.listCandidates().size()==2,"one entity remains plus unrelated");
            check(store.getTask(first)!=null&&store.getTask(second)!=null,"source tasks survive");
            check(store.getImagePaths(first).size()==1&&store.getImagePaths(second).size()==1,"images survive");
            check(store.getFileAttachments(first).size()==1&&store.getFileAttachments(second).size()==1,"files survive");
            check(store.linkedCalendarIds().contains(7L),"provider association retained");
            JSONObject marker=store.mergeLedger().records().get(0);
            long keeper=store.localCandidateId(marker.getString("targetCandidate"));
            check(CandidateMerges.sources(store,keeper).length()==1,"merged source accessible");
            check(merge(store,store.dataRevision())==0,"repeat confirmation idempotent");
            long sourceTask=store.localTaskId(marker.getString("sourceTask"));
            store.deleteTask(sourceTask);
            check(CandidateMerges.sources(store,keeper).getJSONObject(0).getLong("taskId")==0,"deleted source snapshot fallback");
            check(store.mergeLedger().records().size()==1,"source deletion retains tombstone");
            store.getWritableDatabase().beginTransaction();try{store.acceptMerge(marker);store.getWritableDatabase().setTransactionSuccessful();}finally{store.getWritableDatabase().endTransaction();}
            check(store.mergeLedger().records().size()==1,"replay source tombstone idempotent");
            File snapshot=new File(base,"snapshot.db");store.createSnapshot(snapshot);
            try(TaskStore restored=TaskStore.openSnapshot(context,snapshot)){check(restored.mergeLedger().records().size()==1,"backup marker preserved");check(restored.stableCandidateId(keeper).equals(marker.getString("targetCandidate")),"backup stable identity preserved");}
            store.deleteTask(store.localTaskId(marker.getString("targetTask")));
            check(store.mergeLedger().records().size()==1,"keeper deletion retains marker");
        }
        try(TaskStore store=new TaskStore(new Context(new File(base,"rollback")))){
            task(store,"a",0);task(store,"b",0);task(store,"c",0);
            CandidateMerges.Preview preview=CandidateMerges.scan(store);
            long fail=preview.groups.get(0).get(2).id;
            store.getWritableDatabase().execSQL("CREATE TRIGGER reject_merge BEFORE DELETE ON event_candidates WHEN OLD.id="+fail+" BEGIN SELECT RAISE(ABORT,'forced rollback'); END");
            try{merge(store,preview.revision);throw new AssertionError("failure missing");}catch(IllegalStateException expected){checks++;}
            check(store.listCandidates().size()==3&&store.mergeLedger().records().isEmpty(),"whole multi-row transaction rolled back");
            check(store.dataRevision()==preview.revision,"revision rolled back");
        }
        String a="00000000-0000-0000-0000-000000000001",b="00000000-0000-0000-0000-000000000002",c="00000000-0000-0000-0000-000000000003";
        JSONObject cb=record(c,b),ca=record(c,a),ba=record(b,a);
        SyncState one=new SyncState("one"),two=new SyncState("two");one.put("merge_"+c,cb);two.put("merge_"+c,ca);two.put("merge_"+b,ba);
        one.merge(two);two.merge(one);
        check(SyncState.canonical(one.toJson().getJSONObject("records")).equals(SyncState.canonical(two.toJson().getJSONObject("records"))),"concurrent markers converge");
        MergeLedger ledger=new MergeLedger();ledger.add(cb);ledger.add(ba);check(ledger.target(c).equals(a),"chain resolves");ledger.add(ca);check(ledger.target(c).equals(a),"concurrent lower target wins");
        try{record(a,a);throw new AssertionError("self edge accepted");}catch(java.io.IOException expected){checks++;}
        try{record(a,c);throw new AssertionError("ascending edge accepted");}catch(java.io.IOException expected){checks++;}
        try{one.delete("merge_"+c);throw new AssertionError("marker deletion accepted");}catch(IllegalArgumentException expected){checks++;}
        SyncState old=new SyncState("old");old.put(cb.getString("sourceTask"),payload(c));old.put(cb.getString("targetTask"),payload(b));
        SyncState marked=old.copy();marked.put("merge_"+c,cb);
        for(boolean tasksFirst:new boolean[]{true,false})try(TaskStore store=new TaskStore(new Context(new File(base,"order-"+tasksFirst)))){
            if(tasksFirst)apply(store,old);
            apply(store,marked);
            check(store.listCandidates().size()==1,"marker/task arrival order "+tasksFirst);
            check(store.mergeLedger().removed(c),"marker imported on new device");
            apply(store,old);
            check(store.listCandidates().size()==1,"old task replay cannot revive merged candidate");
            store.deleteTask(store.localTaskId(cb.getString("sourceTask")));apply(store,old);
            check(store.listCandidates().size()==1&&store.mergeLedger().removed(c),"deleted source + old replay stays removed");
            store.deleteTask(store.localTaskId(cb.getString("targetTask")));apply(store,old);
            check(store.mergeLedger().removed(c),"deleted keeper + old replay retains removal");
        }
        for(boolean locallyEdited:new boolean[]{false,true}){
            Context device=new Context(new File(base,"staging-"+locallyEdited));try(TaskStore store=new TaskStore(device)){
                stagedApply(store,device,old);long source=store.localTaskId(cb.getString("sourceTask"));
                if(locallyEdited)store.updateRawText(source,"real local edit");
                SyncState remote=old.copy();byte[] bytes={1,2,3,4};String sha=SyncState.sha256(bytes);java.nio.file.Files.write(new File(device.root,sha).toPath(),bytes);
                JSONObject edited=payload(c).put("rawText","remote original updated").put("candidates",new JSONArray())
                    .put("attachments",new JSONArray().put(new JSONObject().put("sha",sha).put("name","updated.pdf").put("mime","application/pdf").put("size",4).put("image",false)));
                remote.put(cb.getString("sourceTask"),edited);remote.put("merge_"+c,cb);
                stagedApply(store,device,remote);
                check(store.getCandidates(source).isEmpty(),"staging removes duplicate candidate");
                check(store.getTask(source).rawText.equals(locallyEdited?"real local edit":"remote original updated"),"real staging preserves local edit or applies remote text");
                check(store.getFileAttachments(source).size()==(locallyEdited?0:1),"real staging protects local edit or imports remote attachment");
                AndroidSyncData sync=new AndroidSyncData(store,device);
                JSONObject baseline=new JSONObject(sync.value("SELECT baseline FROM sync_task_map WHERE sync_id=?",cb.getString("sourceTask")));
                check(baseline.getJSONArray("candidates").isEmpty(),"baseline removes only merged UUID");
                if(!locallyEdited){
                    check(baseline.getString("rawText").equals("remote original updated"),"remote baseline advances with content");
                    JSONObject current=snapshot(store).payloads.get(source);JSONArray files=new JSONArray();
                    for(TaskFileAttachment file:store.getFileAttachments(source))files.put(new JSONObject().put("sha",sha).put("name",file.displayName).put("mime",file.mimeType).put("size",file.sizeBytes).put("image",false));current.put("attachments",files);
                    String appliedClock=sync.value("SELECT baseline_clock FROM sync_task_map WHERE sync_id=?",cb.getString("sourceTask"));
                    java.lang.reflect.Method changed=AndroidSyncData.class.getDeclaredMethod("changed",SyncState.class,String.class,JSONObject.class,String.class,String.class);changed.setAccessible(true);
                    check(changed.invoke(sync,remote,cb.getString("sourceTask"),current,baseline.toString(),appliedClock).equals(appliedClock),"next capture keeps clock without false local branch");
                }
            }
        }
        Context restoredContext=new Context(new File(base,"identity"));try(TaskStore store=new TaskStore(restoredContext)){
            long first=task(store,"backup unmapped",0);long firstCandidate=store.getCandidates(first).get(0).id;
            String beforeTask=store.stableTaskId(first),beforeCandidate=store.stableCandidateId(firstCandidate);
            long unmapped=task(store,"unmapped existing",1),unmappedCandidate=store.getCandidates(unmapped).get(0).id;
            AndroidSyncData identityReader=new AndroidSyncData(store,restoredContext);String oldIdentity=identityReader.databaseIdentity;
            String expectedTask="task_"+UUID.nameUUIDFromBytes((oldIdentity+":task:"+unmapped).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String expectedCandidate=UUID.nameUUIDFromBytes((oldIdentity+":candidate:"+unmappedCandidate).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            File backup=new File(base,"identity-snapshot.db");store.createSnapshot(backup);
            long later=task(store,"after backup",0);String laterTask=store.stableTaskId(later),laterCandidate=store.stableCandidateId(store.getCandidates(later).get(0).id);
            try(TaskStore restored=TaskStore.openSnapshot(restoredContext,backup)){
                SQLiteDatabase db=restored.getWritableDatabase();db.beginTransaction();try{restored.rotateRestoredSyncIdentity();db.setTransactionSuccessful();}finally{db.endTransaction();}
                check(restored.stableTaskId(first).equals(beforeTask)&&restored.stableCandidateId(firstCandidate).equals(beforeCandidate),"restore retains mapped identity");
                check(restored.stableTaskId(unmapped).equals(expectedTask)&&restored.stableCandidateId(unmappedCandidate).equals(expectedCandidate),"restore freezes previously unmapped existing identities before rotating");
                long reused=task(restored,"unrelated after restore",0);check(reused==later,"exercise restored sequence reuse");
                check(!restored.stableTaskId(reused).equals(laterTask)&&!restored.stableCandidateId(restored.getCandidates(reused).get(0).id).equals(laterCandidate),"restored new entity never reuses former UUID");
            }
        }
        System.out.println("Candidate merge: "+checks+" checks passed (production Java + real SQLite; not Android rendering)");
    }
}
