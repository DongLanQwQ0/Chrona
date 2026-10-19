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
        System.out.println("ClassroomWorkServiceCheck PASS: promotion, multiple lessons, reopen, account isolation, rejection, timeout, no redelivery");
    }
}
