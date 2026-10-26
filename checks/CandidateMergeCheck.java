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
    static void appendChecks(File base)throws Exception{
        try(TaskStore store=new TaskStore(new Context(new File(base,"append")))){
            long id=task(store,"append original",0);
            EventCandidate original=store.getCandidates(id).get(0);
            String stable=store.stableCandidateId(original.id);
            long revision=store.dataRevision();
            EventCandidate added=new EventCandidate(0,id,"Second",5000L,6000L,"UTC","New room",
                    "New notes",25,true,null,EventCategory.TASK,true,true,EventCandidate.INFERRED);
            long second=store.appendCandidate(added);
            List<EventCandidate> rows=store.getCandidates(id);
            check(rows.size()==2&&rows.get(0).id==original.id&&rows.get(1).id==second,"append keeps original identity and order");
            check(rows.get(0).calendarEventId.equals(7L)&&store.stableCandidateId(original.id).equals(stable),"append preserves provider and sync identity");
            check(rows.get(1).title.equals(added.title)&&rows.get(1).startAtMillis.equals(added.startAtMillis)
                    &&rows.get(1).endAtMillis.equals(added.endAtMillis)&&rows.get(1).timeZoneId.equals(added.timeZoneId)
                    &&rows.get(1).location.equals(added.location)&&rows.get(1).description.equals(added.description)
                    &&rows.get(1).reminderMinutesBefore.equals(25)&&rows.get(1).needsConfirmation
                    &&rows.get(1).calendarEventId==null&&rows.get(1).category.equals(EventCategory.TASK)
                    &&rows.get(1).allDay&&rows.get(1).endAutoGenerated&&rows.get(1).uncertaintyLevel==EventCandidate.INFERRED,
                    "append persists every candidate field");
            check(store.dataRevision()>revision,"append advances data revision");
            store.getWritableDatabase().execSQL("UPDATE event_candidates SET position=4 WHERE id="+second);
            long third=store.appendCandidate(new EventCandidate(0,id,"Third",null,null,"UTC","","",null,true));
            try(android.database.Cursor cursor=store.getReadableDatabase().rawQuery("SELECT position FROM event_candidates WHERE id=?",new String[]{Long.toString(third)})){
                check(cursor.moveToFirst()&&cursor.getInt(0)==5,"append uses maximum position after gaps");
            }
            EventCandidate[] invalid={null,original,new EventCandidate(0,0,"Bad",null,null,"UTC","","",null,true),
                    new EventCandidate(0,id," ",null,null,"UTC","","",null,true),
                    new EventCandidate(0,id,"Bad",null,null,"UTC","","",-1,true),
                    new EventCandidate(0,Long.MAX_VALUE,"Missing",null,null,"UTC","","",null,true)};
            for(EventCandidate value:invalid){
                try{store.appendCandidate(value);throw new AssertionError("invalid append accepted");}catch(IllegalArgumentException expected){checks++;}
            }
            check(store.getCandidates(id).size()==3,"invalid append leaves existing rows unchanged");
            revision=store.dataRevision();
            store.getWritableDatabase().execSQL("CREATE TRIGGER reject_append BEFORE INSERT ON event_candidates WHEN NEW.title='Rejected' BEGIN SELECT RAISE(ABORT,'forced rollback'); END");
            try{store.appendCandidate(new EventCandidate(0,id,"Rejected",null,null,"UTC","","",null,true));throw new AssertionError("append failure missing");}catch(IllegalStateException expected){checks++;}
            check(store.getCandidates(id).size()==3&&store.dataRevision()==revision,"failed append rolls back candidate and revision");
            long empty=store.insertTask("empty",null,"test",200L);
            long first=store.appendCandidate(new EventCandidate(0,empty,"First",null,null,"UTC","","",null,true));
            try(android.database.Cursor cursor=store.getReadableDatabase().rawQuery("SELECT position FROM event_candidates WHERE id=?",new String[]{Long.toString(first)})){
                check(cursor.moveToFirst()&&cursor.getInt(0)==0,"append into empty task starts at zero");
            }
        }
    }
    static EventCandidate identity(String title,Long start,Long end,Long due,String location,
            String description,Long completed,boolean record){
        return new EventCandidate(0,99,title,start,end,"UTC",location,description,30,true,88L,
                EventCategory.TASK,false,true,EventCandidate.INFERRED,due,completed,record);
    }
    static boolean same(EventCandidate first,EventCandidate second){
        return DuplicateCandidates.key(first).equals(DuplicateCandidates.key(second));
    }
    static void identityChecks(){
        EventCandidate single = candidate(1, 0);
        EventCandidate series = single.withRepeat(new RepeatRule("daily", 1, List.of(), null, 3));
        check(!same(single, series), "single event and repeated series cannot merge");
        check(!same(series, series.withRepeatState("{\"1000\":{\"deleted\":true}}", null)),
                "series with different occurrence exceptions cannot merge");
        List<EventCandidate> variants=new ArrayList<>();variants.add(candidate(1,0));variants.add(candidate(2,0));
        Set<Integer> significant=new HashSet<>(Arrays.asList(1,2,3,5,10,11));
        for(int i=1;i<=13;i++){
            check(same(candidate(1,0),candidate(2,i))!=significant.contains(i),"business field classification "+i);
            variants.add(candidate(3+i,i));
        }
        check(DuplicateCandidates.groups(variants).size()==1,"metadata variants share one business group");
        EventCandidate dated=identity("Meeting",1000L,3000L,4000L,"Room","Notes",null,false);
        check(same(dated,identity(" ｍＥＥＴ\u00a0ＩＮＧ \n",1000L,3000L,4000L," Ｒｏ Ｏｍ ","Other source notes",null,false)),
                "NFKC, whitespace and root case normalization match across sources");
        check(!same(dated,identity("Meeting",1001L,3000L,4000L,"Room","Notes",null,false)),"actual start stays exact");
        check(!same(dated,identity("Meeting",1000L,3001L,4000L,"Room","Notes",null,false)),"actual end stays exact");
        check(!same(dated,identity("Meeting",1000L,3000L,4001L,"Room","Notes",null,false)),"deadline stays exact");
        check(!same(dated,identity("Meeting",1000L,3000L,4000L,"Other room","Notes",null,false)),"different location stays separate");
        check(!same(dated,identity("Meeting",1000L,3000L,4000L,"Room","Notes",5000L,false)),"pending and completed stay separate");
        check(same(identity("Meeting",1000L,3000L,4000L,"Room","Notes",5000L,false),
                identity("Meeting",1000L,3000L,4000L,"Room","Notes",6000L,false)),"completion timestamp is metadata");
        check(!same(dated,identity("Meeting",1000L,3000L,4000L,"Room","Notes",null,true)),"record and schedule stay separate");
        check(!same(identity("Meeting",null,null,null,"Room","Notes",null,false),
                identity("Meeting",null,null,null,"Room","Other notes",null,false)),"undated descriptions protect distinct items");
        check(same(identity("Meeting",null,null,4000L,"Room","Notes",null,false),
                identity("Meeting",null,null,4000L,"Room","Other notes",null,false)),"deadline gives dated business identity");
        check(!same(identity("Meeting",1000L,3000L,null,"Room","Notes",null,true),
                identity("Meeting",1000L,3000L,null,"Room","Other notes",null,true)),"record description remains significant");
        check(same(identity("Meeting",1000L,3000L,null,null,"Notes",null,false),
                identity("Meeting",1000L,3000L,null," \u3000 ","Notes",null,false)),"empty location normalization");
    }
    static EventCandidate named(long id,String title,String location){
        return new EventCandidate(id,id,title,1000L,3000L,"UTC",location,"Notes",10,false,
                null,EventCategory.TASK,false,false,EventCandidate.CERTAIN,4000L,null,false);
    }
    static boolean grouped(EventCandidate first,EventCandidate second){
        return DuplicateCandidates.groups(List.of(first,second)).size()==1;
    }
    static void boundedDistanceChecks()throws Exception{
        var method=DuplicateCandidates.class.getDeclaredMethod("distanceAtMost",int[].class,int[].class,int.class);
        method.setAccessible(true);
        Random random=new Random(20261023);
        for(int sample=0;sample<180;sample++){
            int length=8+random.nextInt(40),otherLength=length-2+random.nextInt(5);
            int[] a=new int[length],b=new int[otherLength];
            for(int i=0;i<a.length;i++)a[i]=random.nextInt(5);
            for(int i=0;i<b.length;i++)b[i]=random.nextBoolean()&&i<a.length?a[i]:random.nextInt(5);
            int[][] full=new int[a.length+1][b.length+1];
            for(int i=0;i<=a.length;i++)full[i][0]=i;
            for(int j=0;j<=b.length;j++)full[0][j]=j;
            for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)
                full[i][j]=Math.min(Math.min(full[i-1][j]+1,full[i][j-1]+1),full[i-1][j-1]+(a[i-1]==b[j-1]?0:1));
            int budget=Math.max(Math.abs(a.length-b.length),sample%14);
            check((Boolean)method.invoke(null,a,b,budget)==(full[a.length][b.length]<=budget),"banded distance equals full DP "+sample);
        }
    }
    static void broadMatchingChecks(){
        EventCandidate meeting=named(1,"项目进度汇报","教学楼-101");
        check(grouped(meeting,named(2,"项目进度汇报（会议）","教学楼 101")),"Chinese suffix and punctuation-normalized location");
        check(!same(meeting,named(2,"项目进度汇报（会议）","教学楼 101")),"exact key contract is unchanged");
        check(grouped(named(1,"Annual Review","Room-A"),named(2,"ＡＮＮＵＡＬ， ＲＥＶＩＥＷ!","room a")),"English fullwidth case and punctuation");
        check(grouped(meeting,named(2,"项目进度汇报",null)),"missing location may match known location");
        check(grouped(named(1,"项目进度汇报",null),named(2,"项目进度汇报（会议）","教学楼101")),"missing location is symmetric");
        check(!grouped(meeting,named(2,"项目进度汇报","教学楼102")),"explicit conflicting locations remain independent");
        check(!grouped(meeting,named(2,"完全不同事项","教学楼101")),"location and notes cannot replace title similarity");
        check(grouped(named(1,"项目汇报第１次",""),named(2,"项目汇报第1次（会议）","")),"fullwidth numeric sequence matches");
        check(!grouped(named(1,"项目汇报第1次",""),named(2,"项目汇报第2次","")),"different occurrence numbers remain independent");
        check(!grouped(named(1,"项目进度汇报",""),named(2,"项目进度汇报1","")),"missing number is not equivalent");
        check(!grouped(named(1,"项目进度汇报1-2",""),named(2,"项目进度汇报12","")),"number sequence boundaries survive punctuation removal");
        check(!grouped(named(1,"项目进度汇报1 2",""),named(2,"项目进度汇报12","")),"number boundaries also protect normalized exact titles");
        check(!grouped(named(1,"跨学期项目进度汇报第三次会议",""),named(2,"跨学期项目进度汇报第四次会议","")),"Chinese occurrence numbers remain independent");
        check(!grouped(named(1,"大学物理阅读笔记第二章讨论",""),named(2,"大学物理阅读笔记第三章讨论","")),"Chinese chapter numbers remain independent");
        check(!grouped(named(1,"项目计划第壹次说明会议",""),named(2,"项目计划第贰次说明会议","")),"financial Chinese numbers remain independent");
        check(!grouped(named(1,"物理实验",""),named(2,"物理","")),"short contained titles are protected");
        check(!grouped(meeting,named(2,"项目进度汇报及下学期规划会议","教学楼101")),"containment requires sufficient coverage");
        check(grouped(named(1,"abcdefghi",""),named(2,"abcxefghi","")),"Levenshtein above threshold");
        check(!grouped(named(1,"abcdefgh",""),named(2,"abcxefgh","")),"Levenshtein below threshold");
        check(grouped(named(1,"abcdefghijklmnopqrstuvwxy",""),named(2,"abcxefghijkymnopqrstuvwxz","")),"exactly 88 percent similarity is accepted");
        check(!grouped(named(1,"abcdefghijklmnopqrstuvwxy",""),named(2,"abcxefghijkymnopqzstuvwxz","")),"four edits at length 25 fail similarity");
        String longTitle="a".repeat(500);
        check(grouped(named(1,longTitle,""),named(2,"b".repeat(60)+"a".repeat(440),"")),"long title matches at exact edit budget");
        check(!grouped(named(1,longTitle,""),named(2,"b".repeat(61)+"a".repeat(439),"")),"long title fails beyond edit budget");
        check(!grouped(named(1,"𠮷野家",""),named(2,"𠮷野家店","")),"containment counts code points instead of UTF16 units");
        check(grouped(named(1,"𠮷野家店",""),named(2,"𠮷野家店铺","")),"four-code-point containment is allowed");
        EventCandidate a=named(1,"abcdefghi",""),b=named(2,"abcxefghi",""),c=named(3,"abcxefghj","");
        check(grouped(a,b)&&grouped(b,c)&&!grouped(a,c),"synthetic non-transitive similarity chain");
        List<List<EventCandidate>> chain=DuplicateCandidates.groups(List.of(c,b,a));
        check(chain.size()==1&&chain.get(0).size()==2&&chain.get(0).get(0).id==1&&chain.get(0).get(1).id==2,"pairwise group rejects chain bridge and orders IDs");
        check(DuplicateCandidates.groups(List.of(a,b,c)).get(0).equals(chain.get(0)),"input order cannot change similarity grouping");
        List<List<EventCandidate>> locations=DuplicateCandidates.groups(List.of(named(3,"项目进度汇报","B楼"),named(2,"项目进度汇报",null),named(1,"项目进度汇报","A楼")));
        check(locations.size()==1&&locations.get(0).size()==2,"missing location cannot bridge conflicting known locations");
        for(int difference:new int[]{2,3,10,11})
            check(!grouped(candidate(1,0),candidate(2,difference)),"broad matcher preserves business conflict "+difference);
        EventCandidate dated=identity("项目进度汇报",1000L,3000L,4000L,"","Notes",null,false);
        check(!grouped(dated,identity("项目进度汇报会议",1000L,3000L,4001L,"","Notes",null,false)),"broad matcher preserves deadline");
        check(!grouped(dated,identity("项目进度汇报会议",1000L,3000L,4000L,"","Notes",5000L,false)),"broad matcher preserves completion state");
        check(!grouped(dated,dated.withRepeat(new RepeatRule("daily",1,List.of(),null,3))),"broad matcher preserves repeat rule");
        EventCandidate repeated=dated.withRepeat(new RepeatRule("daily",1,List.of(),null,3));
        check(!grouped(repeated,repeated.withRepeatState("{\"1000\":{\"deleted\":true}}",null)),"broad matcher preserves repeat exceptions");
        check(!grouped(repeated,repeated.withRepeatState("{}",2000L)),"broad matcher preserves repeat stop");
        EventCandidate otherZone=new EventCandidate(0,99,"项目进度汇报",1000L,3000L,"Asia/Shanghai","","Notes",30,true,88L,
                EventCategory.TASK,false,true,EventCandidate.INFERRED,4000L,null,false)
                .withRepeat(new RepeatRule("daily",1,List.of(),null,3));
        check(!grouped(repeated,otherZone),"broad matcher preserves repeated local timezone");
        check(grouped(identity("项目进度汇报",1000L,3000L,4000L,"","Notes",5000L,false),
                identity("项目进度汇报会议",1000L,3000L,4000L,"","Other",6000L,false)),"completion timestamps do not block broad dated match");
        check(grouped(identity("项目进度汇报",null,null,4000L,"","Notes",null,false),
                identity("项目进度汇报会议",null,null,4000L,"","Other",null,false)),"deadline alone anchors broad match");
        check(!grouped(identity("项目进度汇报",null,null,null,"","Notes",null,false),identity("项目进度汇报会议",null,null,null,"","Notes",null,false)),"undated items do not use fuzzy titles");
        check(!grouped(identity("项目进度汇报",1000L,3000L,null,"","Notes",null,true),identity("项目进度汇报会议",1000L,3000L,null,"","Notes",null,true)),"records do not use fuzzy titles");
        check(!grouped(identity("项目进度汇报",null,null,null,"","Notes",null,false),identity("项目进度汇报",null,null,null,"","Other",null,false)),"undated description protection remains");
    }
    public static void main(String[] args)throws Exception{
        File base=new File(args[0],UUID.randomUUID().toString());base.mkdirs();
        appendChecks(base);
        identityChecks();
        broadMatchingChecks();
        boundedDistanceChecks();
        try(TaskStore store=new TaskStore(new Context(new File(base,"occurrence-hold")))){
            long first=task(store,"announcement",0);task(store,"ordinary one",0);task(store,"ordinary two",0);
            long candidate=store.getCandidates(first).get(0).id;
            for(String state:List.of("pending","invalid","applied")){
                store.getWritableDatabase().execSQL("UPDATE event_candidates SET occurrence_update=? WHERE id=?",new Object[]{new JSONObject().put("state",state).toString(),candidate});
                CandidateMerges.Preview preview=CandidateMerges.scan(store);
                check(preview.removed()==1&&preview.groups.stream().flatMap(List::stream).noneMatch(item->item.id==candidate),"held announcement excluded from generic merge: "+state);
            }
            store.getWritableDatabase().execSQL("UPDATE event_candidates SET occurrence_update=? WHERE id=?",new Object[]{new JSONObject().put("state","new").toString(),candidate});
            check(CandidateMerges.scan(store).removed()==2,"explicit new returns to ordinary merge contract");
        }
        try(TaskStore store=new TaskStore(new Context(new File(base,"selected-groups")))){
            task(store,"first source",0);task(store,"second source",0);
            task(store,"other first",1);task(store,"other second",1);
            CandidateMerges.Preview preview=CandidateMerges.scan(store);
            check(preview.groups.size()==2,"two independent groups for explicit choice");
            Set<Long> selected=Collections.singleton(preview.groups.get(0).get(0).id);
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try{check(CandidateMerges.mergeRows(store,preview.revision,selected)==1,"only selected group merged");db.setTransactionSuccessful();}finally{db.endTransaction();}
            CandidateMerges.Preview remaining=CandidateMerges.scan(store);
            check(remaining.groups.size()==1&&remaining.groups.get(0).size()==2,"unselected group remains intact");
            db.beginTransaction();try{
                check(CandidateMerges.mergeRows(store,remaining.revision,Collections.emptySet())==0,"empty selection never deletes");
                try{CandidateMerges.mergeRows(store,remaining.revision,Collections.singleton(Long.MAX_VALUE));throw new AssertionError("unknown group accepted");}catch(java.io.IOException expected){checks++;}
            }finally{db.endTransaction();}
            check(CandidateMerges.scan(store).groups.size()==1,"invalid choice leaves group untouched");
        }
        try(TaskStore store=new TaskStore(new Context(new File(base,"broad-match")))){
            long first=task(store,"short title source",0),second=task(store,"detailed title source",0);
            store.replaceCandidates(second,List.of(new EventCandidate(0,second,"Meeting (review)",1000L,3000L,
                    "Asia/Shanghai","Room","Notes",10,false,7L,EventCategory.EVENT,false,false,EventCandidate.CERTAIN)));
            // Short title must cover at least 65% of the longer one; this suffix is intentionally too long.
            check(CandidateMerges.scan(store).removed()==0,"long suffix below coverage is absent from real preview");
            store.replaceCandidates(second,List.of(new EventCandidate(0,second,"Meeting!",1000L,3000L,
                    "Asia/Shanghai","","Preserved second-source notes",10,false,7L,EventCategory.EVENT,false,false,EventCandidate.CERTAIN)));
            CandidateMerges.Preview preview=CandidateMerges.scan(store);
            check(preview.removed()==1,"normalized title and missing location appear in real preview");
            check(store.listCandidates().size()==2&&store.mergeLedger().records().isEmpty(),"scan never automatically merges broad matches");
            check(merge(store,preview.revision)==1&&store.listCandidates().size()==1,"broad match uses existing confirmation transaction");
            check(store.getTask(first)!=null&&store.getTask(second)!=null&&store.mergeLedger().records().size()==1,"broad merge retains sources and permanent ledger");
        }
        try(TaskStore store=new TaskStore(new Context(new File(base,"metadata")))){
            long first=task(store,"capture source",0),second=task(store,"calendar source",9);
            EventCandidate source=store.getCandidates(second).get(0);
            store.replaceCandidates(second,Collections.singletonList(new EventCandidate(0,second,source.title,
                    source.startAtMillis,source.endAtMillis,"UTC",source.location,"Second source notes",30,true,
                    8L,source.category,source.allDay,true,EventCandidate.INFERRED)));
            check(CandidateMerges.scan(store).removed()==1,"different IDs and metadata appear in preview");
            check(merge(store,store.dataRevision())==1,"cross-provider metadata duplicate merges");
            JSONObject marker=store.mergeLedger().records().get(0);
            EventCandidate archived=TaskCodec.event(marker.getJSONObject("source").getJSONObject("candidate"));
            boolean secondArchived=marker.getString("sourceTask").equals(store.stableTaskId(second));
            check(archived.description.equals(secondArchived?"Second source notes":"Notes")
                    &&archived.uncertaintyLevel==(secondArchived?EventCandidate.INFERRED:EventCandidate.CERTAIN)
                    &&archived.endAutoGenerated==secondArchived,
                    "merge ledger preserves original notes and portable provenance");
            check(archived.calendarEventId==null&&archived.reminderMinutesBefore==null,
                    "portable ledger excludes device-local provider IDs and reminder settings");
            check(store.linkedCalendarIds().containsAll(Arrays.asList(7L,8L)),"both local provider associations retained");
            MergeLedger copied=new MergeLedger();copied.add(marker);
            check(copied.records().get(0).getJSONObject("source").getJSONObject("candidate")
                    .toString().equals(marker.getJSONObject("source").getJSONObject("candidate").toString()),
                    "ledger validation retains metadata for sync");
            check(store.getTask(first)!=null&&store.getTask(second)!=null,"both metadata source tasks retained");
        }
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
            long keeperTask=store.localTaskId(marker.getString("targetTask"));
            store.appendCandidate(new EventCandidate(0,keeperTask,"Added after merge",null,null,"UTC","","",null,true));
            check(store.localCandidateId(marker.getString("targetCandidate"))==keeper
                    &&CandidateMerges.sources(store,keeper).length()==1
                    &&store.getCandidates(keeperTask).get(0).calendarEventId.equals(7L),
                    "append preserves merged candidate reference and published association");
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
