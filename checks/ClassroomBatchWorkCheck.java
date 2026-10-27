package com.donglan.chrona;

import java.util.List;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomSummary;

public final class ClassroomBatchWorkCheck {
    private static int checks;
    private static void require(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static ClassroomLesson l(String id){return new ClassroomLesson(id);}
    public static void main(String[] args){
        var app=new android.content.Context();var service=new ClassroomWorkService();
        var other=new ClassroomLessonActivity.Session("account","other");ClassroomWorkService.submit(other,()->{});service.onStartCommand(null,0,1);
        ClassroomLiveTranscripts.active.add("live");
        var batch=ClassroomBatchWork.start(app,"account",List.of(l("fail"),l("ok"),l("ok"),l("other"),l("live"),l("bad-dir")));
        require(batch.lessons.size()==5,"same lesson deduplicated before queue");
        require(ClassroomBatchWork.start(app,"account",List.of(l("different")))==batch,"running account batch is not replaced");
        service.onStartCommand(null,0,2);var first=batch.current;require(first.selected.id.equals("fail")&&first.busy,"only first lesson starts");
        first.complete();require(batch.current!=first&&batch.current.selected.id.equals("ok"),"failed item advances sequentially");
        batch.current.complete();require(!batch.running&&batch.success==1&&batch.skipped==2&&batch.failures.size()==4,"success failure active skips and directory failure counted per lesson");
        require(ClassroomSummary.calls==2,"only unowned eligible lessons prepare materials, no AI calls");require(!other.cancelled,"batch never cancels another Session");
        require(batch.details().contains("素材失败")&&batch.details().contains("bad directory")&&batch.details().contains("跳过 2"),"report retains individual causes and totals");
        ClassroomLiveTranscripts.active.clear();
        var cancelled=ClassroomBatchWork.start(app,"account",List.of(l("cancel"),l("pending")));service.onStartCommand(null,0,3);cancelled.cancel("已取消");
        require(cancelled.current.cancelRequested&&cancelled.running,"cancel owns only current Session and waits for cleanup");cancelled.current.complete();
        require(!cancelled.running&&cancelled.cancelledCount==2&&cancelled.failures.size()==2,"current and pending cancellation both counted");require(!other.cancelled,"cancelled batch preserves unrelated foreground work");
        var changed=ClassroomBatchWork.start(app,"account",List.of(l("account"),l("later")));service.onStartCommand(null,0,4);ZjuSettingsStore.current="other-account";ZjuSettingsStore.shared.changed();
        require(changed.current.cancelRequested,"account switch cancels owned current Session");changed.current.complete();require(!changed.running&&changed.cancelledCount==2,"account switch cancels pending queue");
        ZjuSettingsStore.current="account";ClassroomWorkService.finished(other);service.onDestroy();
        var denied=new android.content.Context();denied.reject=true;var unavailable=ClassroomBatchWork.start(denied,"account",List.of(l("one"),l("two")));
        require(!unavailable.running&&unavailable.cancelledCount==2&&unavailable.current==null,"foreground denial finishes complete queue without losing callback");
        require(ZjuSettingsStore.shared.listeners.isEmpty(),"all terminal paths unregister account observers");
        System.out.println("ClassroomBatchWorkCheck PASS: "+checks+" checks; sequential dedupe, per-item failure, owned cancellation, account switch, foreground denial");
    }
}
