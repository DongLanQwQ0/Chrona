package com.donglan.chrona;

public final class ClassroomWorkServiceCheck {
    private static void require(boolean condition,String name){if(!condition)throw new AssertionError(name);}
    public static void main(String[] args){
        var a=new ClassroomLessonActivity.Session("account","a");
        var b=new ClassroomLessonActivity.Session("account","b");
        var service=new ClassroomWorkService();
        int[] runs={0};
        ClassroomWorkService.submit(a,()->{require(service.promoted,"foreground precedes work");runs[0]++;});
        ClassroomWorkService.submit(b,()->runs[0]++);
        require(runs[0]==0,"queued until foreground promotion");
        require(ClassroomWorkService.find("account",a.selected)==a,"reopen same session");
        require(ClassroomWorkService.find("other",a.selected)==null,"account isolation");
        require(service.onStartCommand(null,0,1)==android.app.Service.START_NOT_STICKY,"no paid-call redelivery");
        require(runs[0]==2,"multiple lessons start");
        ClassroomWorkService.finished(a);
        require(!service.stopped,"one lesson cannot stop another");
        ClassroomWorkService.finished(b);
        require(!service.stopped,"pending start callback remains protected");
        service.onStartCommand(null,0,2);
        require(runs[0]==2&&service.stopped&&!service.promoted,"last completion stops, no duplicate execution");
        service.onDestroy();
        var rejected=new ClassroomLessonActivity.Session("account","reject");rejected.app.reject=true;
        ClassroomWorkService.submit(rejected,()->{throw new AssertionError("rejected work ran");});
        require(rejected.failed&&ClassroomWorkService.find("account",rejected.selected)==null,"start rejection clears registry");
        var timed=new ClassroomLessonActivity.Session("account","timeout");var timedService=new ClassroomWorkService();
        ClassroomWorkService.submit(timed,()->{});timedService.onStartCommand(null,0,3);timedService.onTimeout(3,1);
        require(timed.cancelled&&timedService.stopped&&!timedService.promoted,"timeout cancels and stops promptly");
        ClassroomWorkService.finished(timed);timedService.onDestroy();
        var failure=new ClassroomLessonActivity.Session("account","promotion");var failedService=new ClassroomWorkService();failedService.rejectPromotion=true;
        ClassroomWorkService.submit(failure,()->{throw new AssertionError("unprotected work ran");});failedService.onStartCommand(null,0,4);
        require(failure.failed&&failedService.stopped,"promotion failure prevents work");failedService.onDestroy();
        monitorOwnership();
        batchOwnership();
        System.out.println("ClassroomWorkServiceCheck PASS: manual promotion/reopen/isolation/rejection/timeout/no redelivery; monitor owner gate, no extra FGS, duplicate guard, selective scope/account cancellation");
    }
    private static void batchOwnership(){
        Object batch=new Object();var app=new android.content.Context();var service=new ClassroomWorkService();int[] state={0,0};
        ClassroomWorkService.holdBatch(batch,app,()->{require(service.promoted,"batch ready after foreground");state[0]++;},()->state[1]++);
        require(state[0]==0&&app.starts==1,"one foreground start for batch");service.onStartCommand(null,0,8);
        var first=new ClassroomLessonActivity.Session("account","batch-a");ClassroomWorkService.submitBatch(batch,first,()->state[0]++);
        require(state[0]==2&&first.app.starts==0,"batch Session reuses existing foreground lifecycle");ClassroomWorkService.finished(first);
        require(!service.stopped&&service.promoted,"foreground remains owned between sequential lessons");
        var second=new ClassroomLessonActivity.Session("account","batch-b");ClassroomWorkService.submitBatch(batch,second,()->state[0]++);ClassroomWorkService.finished(second);
        require(state[0]==3&&!service.stopped,"second lesson protected without new foreground start");ClassroomWorkService.releaseBatch(batch);
        require(service.stopped&&state[1]==0,"release finishes batch without reporting loss");service.onDestroy();
        var rejected=new android.content.Context();rejected.reject=true;ClassroomWorkService.holdBatch(new Object(),rejected,()->{throw new AssertionError("unpromoted batch ran");},()->state[1]++);
        require(state[1]==1,"batch start denial invokes loss exactly once");
        Object timed=new Object();var timedService=new ClassroomWorkService();ClassroomWorkService.holdBatch(timed,app,()->{},()->state[1]++);timedService.onStartCommand(null,0,9);
        var own=new ClassroomLessonActivity.Session("account","batch-timeout");ClassroomWorkService.submitBatch(timed,own,()->{});timedService.onTimeout(9,1);
        require(state[1]==2&&own.cancelled,"timeout cancels held batch and its Session");timedService.onDestroy();require(state[1]==2,"destroy after timeout does not redeliver cancellation");ClassroomWorkService.finished(own);
        var denied=new ClassroomLessonActivity.Session("account","batch-after-release");ClassroomWorkService.submitBatch(timed,denied,()->{throw new AssertionError("released batch ran");});require(denied.failed,"released or destroyed foreground owner cannot run a batch Session");
    }
    private static void monitorOwnership(){
        int[] runs={0};var denied=new ClassroomLessonActivity.Session("account","no-owner");denied.monitorOwned=true;
        ClassroomAutoSummary.foreground=false;ClassroomWorkService.submitMonitor(denied,()->runs[0]++);
        require(denied.failed&&runs[0]==0&&denied.app.starts==0,"monitor cannot bypass missing foreground owner");
        var manual=new ClassroomLessonActivity.Session("account","manual");var service=new ClassroomWorkService();
        ClassroomWorkService.submit(manual,()->runs[0]++);service.onStartCommand(null,0,5);
        var monitored=new ClassroomLessonActivity.Session("account","monitored");monitored.monitorOwned=true;
        ClassroomAutoSummary.foreground=true;ClassroomWorkService.submitMonitor(monitored,()->runs[0]++);
        require(runs[0]==2&&monitored.app.starts==0,"monitor uses existing FGS without another start");
        var duplicate=new ClassroomLessonActivity.Session("account","monitored");duplicate.monitorOwned=true;
        ClassroomWorkService.submitMonitor(duplicate,()->runs[0]++);
        require(duplicate.failed&&runs[0]==2&&ClassroomWorkService.find("account",monitored.selected)==monitored,"duplicate automatic submit retains original Session");
        ClassroomWorkService.cancelMonitored();require(monitored.cancelled&&!manual.cancelled,"monitor owner stop does not cancel manual work");
        monitored.cancelled=false;ClassroomMonitorStore.stopped.add("monitored");ClassroomWorkService.reconcileMonitored(manual.app);
        require(monitored.cancelled&&!manual.cancelled,"scope stop only cancels its monitored Session");
        ClassroomMonitorStore.stopped.clear();monitored.cancelled=false;monitored.allowed=false;ClassroomWorkService.reconcileMonitored(manual.app);
        require(monitored.cancelled&&!manual.cancelled,"account gate cancels automatic but preserves independent manual work");
        ClassroomWorkService.finished(monitored);ClassroomWorkService.finished(manual);service.onDestroy();
        require(service.stopped,"mixed registry stops when all work completes");
        ClassroomAutoSummary.foreground=false;
    }
}
