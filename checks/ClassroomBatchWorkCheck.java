package com.donglan.chrona;

import java.util.List;
import java.util.ArrayList;
import com.donglan.chrona.ai.SchedulePreferences;
import com.donglan.chrona.classroom.ClassroomLesson;

public final class ClassroomBatchWorkCheck {
    private static int checks;
    private static void require(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static ClassroomLesson l(String id){return new ClassroomLesson(id);}
    private static ClassroomLessonActivity.Session job(ClassroomBatchWork b,String id){return b.active.get("1/"+id);}
    private static void completeAll(ClassroomBatchWork b){for(var s:new ArrayList<>(b.active.values()))s.complete();}
    public static void main(String[] args)throws Exception{
        var app=new android.content.Context();var service=new ClassroomWorkService();
        var other=new ClassroomLessonActivity.Session("account","other");ClassroomWorkService.submit(other,()->{});service.onStartCommand(null,0,1);
        ClassroomLiveTranscripts.active.add("live");
        var batch=ClassroomBatchWork.start(app,"account",List.of(l("fail"),l("ok"),l("third"),l("fourth"),l("ok"),l("other"),l("live"),l("complete"),l("bad-dir")),java.util.Set.of());
        require(batch.lessons.size()==8,"same lesson deduplicated before queue");
        require(ClassroomBatchWork.start(app,"account",List.of(l("different")),java.util.Set.of())==batch,"running account batch is not replaced");
        service.onStartCommand(null,0,2);
        require(batch.active.size()==3&&batch.position==3,"three whole lessons start together, fourth stays queued");
        require(batch.active.values().stream().allMatch(s->s.noteStrength==3),"batch snapshots note strength for every active lesson");
        require(batch.text().contains("进行中 3")&&batch.text().contains("0/8"),"status reports all active work and terminal count");
        var first=job(batch,"fail");first.complete();
        require(!batch.active.containsValue(first)&&job(batch,"fourth")!=null&&batch.active.size()==3,"failed slot immediately fills without retry");
        require(ClassroomAutoSummary.calls==1,"failure invoked once and not automatically resent");
        completeAll(batch);
        require(!batch.running&&batch.success==3&&batch.skipped==3&&batch.failures.size()==5,"failure, complete, other owner, live and directory outcomes counted once");
        require(ClassroomAutoSummary.calls==4&&!other.cancelled,"only eligible lessons run and unrelated owner is preserved");
        require(batch.details().contains("素材失败")&&batch.details().contains("bad directory")&&batch.details().contains("失败 2"),"report retains individual causes and exact totals");
        ClassroomLiveTranscripts.active.clear();
        var cancelled=ClassroomBatchWork.start(app,"account",List.of(l("cancel1"),l("cancel2"),l("cancel3"),l("pending")),java.util.Set.of());service.onStartCommand(null,0,3);cancelled.cancel("已取消");
        require(cancelled.active.size()==3&&cancelled.active.values().stream().allMatch(s->s.cancelRequested)&&cancelled.running,"cancel signals every owned active Session and waits for cleanup");
        var cancellation=new ArrayList<>(cancelled.active.values());cancellation.get(0).complete();cancellation.get(1).complete();
        require(cancelled.running&&cancelled.active.size()==1,"batch foreground hold remains until last active Session finishes");
        cancellation.get(2).complete();
        require(!cancelled.running&&cancelled.cancelledCount==4&&cancelled.failures.size()==4,"all active and pending cancellations counted");require(!other.cancelled,"cancelled batch preserves unrelated foreground work");
        var changed=ClassroomBatchWork.start(app,"account",List.of(l("account1"),l("account2"),l("account3"),l("later")),java.util.Set.of());service.onStartCommand(null,0,4);ZjuSettingsStore.current="other-account";ZjuSettingsStore.shared.changed();
        require(changed.active.values().stream().allMatch(s->s.cancelRequested),"account switch cancels all owned active Sessions");completeAll(changed);require(!changed.running&&changed.cancelledCount==4,"account switch cancels pending queue");
        ZjuSettingsStore.current="account";
        for(int limit:new int[]{1,2}){
            SchedulePreferences.classroomConcurrency=limit;
            var bounded=ClassroomBatchWork.start(app,"account",List.of(l("bounded1"),l("bounded2"),l("bounded3")),java.util.Set.of());service.onStartCommand(null,0,5);
            require(bounded.active.size()==limit,"batch honors selected limit "+limit);
            SchedulePreferences.classroomConcurrency=3;
            bounded.active.values().iterator().next().complete();
            require(bounded.active.size()<=limit&&bounded.concurrency==limit,"running batch retains original concurrency snapshot");
            while(bounded.running)completeAll(bounded);
        }
        ClassroomWorkService.finished(other);service.onDestroy();
        var denied=new android.content.Context();denied.reject=true;var unavailable=ClassroomBatchWork.start(denied,"account",List.of(l("one"),l("two")),java.util.Set.of());
        require(!unavailable.running&&unavailable.cancelledCount==2&&unavailable.active.isEmpty(),"foreground denial finishes queue without starting work");
        require(ZjuSettingsStore.shared.listeners.isEmpty(),"all terminal paths unregister account observers");
        System.out.println("ClassroomBatchWorkCheck PASS: "+checks+" checks; parallel fill, dedupe, per-item failure, all-owned cancellation, account switch, foreground denial");
    }
}
